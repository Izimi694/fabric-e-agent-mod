# E-Agent — 架构详细说明

> 本文件是 E-Agent 技术架构的完整参考。设计原理见 [AGENTS.md](./AGENTS.md)，当前状态见 [DEVELOPMENT.md](./DEVELOPMENT.md)，理论背景见 [THEORY.md](./THEORY.md)。

---

## 1. 六层拦截器

每一层存在的意义，都是让下一层**不需要被调用**。

| 层 | 模组组件 | 触发条件 | 成本 |
|----|---------|---------|:---:|
| **L0** 生存本能 | `InnateReflexRegistry` | 熔岩/虚空/HP<2 | 0 |
>
> **L0 触发机制**：L0 反射不是事件驱动的中断，而是在 MetaScheduler.tick() 的 `executeHabitLayerWithGating()` 阶段被逐项扫描调用（poll 模式）。这意味着即使 bot 掉入虚空，也要等下一个 tick（≤50ms）才能触发逃生反应。在 50ms 尺度上这个延迟对生存场景可接受。未来如需要亚 tick 响应，需引入 Fabric 事件监听器。
| **L1** 先天预警 | `OneShotAlarmSystem` | 玩家说过"坏" | 0 |
| **L2** 条件反射 | `ConditionedReflex` | 匹配已有反射 | 0 |
| **L3** 模仿学习 | `SocialObserver` + 贝叶斯 | 附近有人在做 | 0 |
| **L4** 自组织 | `CorrelationDetector` | 无任何匹配 | 0 |
| **L5** 本地规划 | `LocalTaskDecomposer` | 规则匹配 | 0 |
| **L6** LLM 兜底 | `TemplateManager` → LLM | 以上全不命中 | 高 |

同一场景在不同层级下的不同拦截行为：

```
残血 + creeper + 附近玩家在打怪
L0  → flee (硬编码)
L1  → flee (玩家说过"creeper危险")
L2  → 习惯性打creeper (过去成功过)
L3  → 跟玩家一起打 (观察学习)
L4  → 随机试探 (无反射时)
L5  → 试探性攻击 (规则匹配)
L6  → 复杂语义分析 (以上全不命中)
```

---

## 2. MetaScheduler — 元调度器

每 tick 的决策分五阶段（由 MotivationEngine 和 MetaScheduler 协作完成）：

```
MetaScheduler.tick()
  1. MotivationEngine.computeDrives()  ← 5通道驱力计算 (BotContext + WorldContext + 环境)
                                    激素状态 + BotParams + 环境 → 5维 DriveState
  2. MotivationEngine.select()         ← 玻尔兹曼选择 + 交叉抑制 (BotContext)
  3. labelProblem(perspective)   ← 贴标签: SURVIVAL/LEARNED_THREAT/TASK_ACTIVE/ROUTINE/FAMILIAR/NOVEL/TRIVIAL
  4. getFlowAdjustment(bot, state) ← 升降级: AUTOPILOT/NORMAL/OVERRIDE (BotContext + MetaState)
  5. fallbackDispatch(label, flow) ← 分派到对应执行层（inline 在内层循环）
```

> 注：阶段 1 (`computeDrives`) 和阶段 2 (`select`) 实际实现在 `MotivationEngine` 中，MetaScheduler 的 `tick()` 调用 `MotivationEngine` 的对应方法。详见 `MotivationEngine.java`。

### 2.1 五大视角 (Perspective)

```java
enum Perspective { SURVIVAL, TASK, SOCIAL, CURIOUS, CAUTIOUS }

SURVIVAL:  alarms.hasUrgentThreat()
TASK:      task.hasActiveTask()
SOCIAL:    hasGroupActivity()
CURIOUS:   hasRecentNovelty()
CAUTIOUS:  params.beta > 0.02
```

### 2.2 升降级条件

| 降级 → AUTOPILOT | 升级 → OVERRIDE |
|---------|---------|
| 熟练度 ≥ 0.8 且环境无异常 | 连续失败 ≥ 2 次 |
| 同一动作成功 > 10 次 | 检测到新实体 (novelEntity) |
| 玩家 5 分钟无指令 | 环境突变 (suddenEnvironmentChange) |
| | 玩家说"小心/停" (urgentPlayerMessage) |
| | 当前状态卡住 > 600 ticks (FLOW_STUCK_THRESHOLD) |
| | 卡住 > 2000 ticks 且紧迫度 > 0.5 (TIME_ESCALATION_TICKS) |

### 2.3 时间片分配（e 切割）

任务切换使用 63.2% 执行 + 36.8% 缓冲：

```java
public long computeTimeSlice(long totalLatencyBound, int taskCount) {
    long available = totalLatencyBound - (switchOverheadMs * taskCount);
    return (long) (available / taskCount * 0.632);  // 63.2% = 1 - 1/e
}
```

抢占阈值使用 1/e 增量：

```java
if (newTask.priority > currentTask.priority * (1 + (1.0 / Math.E))) {
    preempt();
}
```

### 2.4 决策场统一驱力 (MotivationEngine refinements)

**资源压力模型**（替代散落的 W_FEAR + health/hunger 因子）：

所有生存资源采用同一公式：
```
resourceStress(fillRatio, depletionRate, replenishDifficulty)
  = effectiveDeficit × (0.5 + 0.5 × replenishDifficulty)

effectiveDeficit = (1 − fillRatio) + depletionRate × DEPLETION_SENSITIVITY(2.0)
```

- **health**：fillRatio = current/max, depletionRate 受威胁类型加权（creeper=1.0, skeleton=0.7, zombie=0.5, spider=0.5, default=0.3）
- **hunger**：fillRatio = saturation/20, depletionRate 由消耗活动频率估算
- **oxygen**（水下）：fillRatio = air/300, depletionRate 固定 0.02，缺水时难度=1.0

补给难度由 `countFoodItems()` 扫描背包 (i=0..35) 中 `ComponentTypes.FOOD` 的物品数量决定。

**动态温度**：

```
T = baseTemp × (1 − pressure × 0.85)
     clamped [0.05, 0.8]
```

`DriveState.pressure()` = survivalUrgency (单一合成信号)。压力高 → 温度低 → 抉择更确定性（生存优先）。压力低 → 温度高 → 容许探索随机性。

**任务惯性 (computeTaskDrive)**：

连续失败降低任务驱动力，成功放大驱动力。防止单次失败导致任务放弃。

**谨慎驱力 (computeCautiousDrive)**：

威胁距离因子 `W_THREAT_CAUTIOUS=0.6`，敌方越近谨慎驱力越高。

**激素调制 select()**：4 维向量 (NE/DA/5-HT/ACh) 通过余弦匹配 + 5-HT 情境分支调制候选权重，GABA/Glu 分别注入攻/逃行为（详见 §4.2 CognitiveControl）。

---

## 3. 三大信息传递系统

| 传递类型 | 时间尺度 | 载体 | 工程实现 |
|---------|---------|------|---------|
| **基因层** | 代际 | α, β, ltb | `BotParams` + 三规则继承 |
| **神经调质层** | 秒~分钟 | 4 维向量 | `HormonalSystem` (NE/DA/5-HT/ACh + 独立 intimacy) |
| **反射层** | 分钟~小时 | stw/ltb 固化 | `ConditionedReflex` + reinforce + solidify |

三者形成闭环：

```
执行反射 → 成功/失败 → 神经递质浓度变化 → 前额叶抑制调制
    ↑                                              ↓
    └──── 反复成功 → ltb ↑ (固化) ←─────────────────┘
                                              ↓
                                       死亡 → 三规则继承给后代
```

### 3.1 状态向量系统 (HormonalSystem + NeuroState)

当前状态：4 维状态向量 (NE/DA/5-HT/ACh) + 向下兼容的 stress/aggression/curiosity/intimacy 旧别名。
4 维名称源于神经科学命名约定，工程上视为独立状态通道，不做生物模拟。

| 变量 | 触发方式 | 时间尺度 | 调制角色 |
|------|---------|:-------:|---------|
| NE | 受伤/威胁/突发事件 | 快 (秒) | 情境门控：NE < 0.5 低威胁 / ≥ 0.5 高威胁 |
| DA | 战斗胜利/新奇发现/任务完成 | 中 (秒~分) | 攻击前提：DA < 0.4 时否决攻击候选 |
| 5-HT | 连续失败/威胁持续 | 慢 (分) | 低NE→全局抑制；高NE→促逃抑攻 |
| ACh | 专注任务/目标切换 | 中 (秒~分) | 高→攻击/挖矿（专注），低→探索（扫视） |
| intimacy | 玩家评价/互动 | 极慢 (时~天) | 社交候选门控乘数，不参与余弦匹配 |

GABA (攻击刹车) 与 Glu (逃跑油门) 作为独立导出量，由 `NeuroDynamics` 工具类实时计算，不持久化存储。详见 §4.3。

**迁移记录**：Phase 2 完成 4 维向量扩展（NE/DA/5-HT/ACh），旧字段 `stress/aggression/curiosity/intimacy` 保留为向下兼容别名。
旧字段标 `@Deprecated`，新代码应使用 `getNE()/getDA()/getSerotonin()/getACh()` 原生接口。
详见 `HormonalSystem.java` 和 `NeuroState.java`。

激素对视角选择的影响：

| 激素状态 | 视角偏向 | 聊天语气 |
|---------|---------|---------|
| stress > 0.7 | SURVIVAL | 简短/警惕 |
| curiosity > 0.6 | CURIOUS | 主动提问 |
| aggression > 0.7 | TASK | 战斗意愿强 |
| intimacy > 0.6 | SOCIAL | 热情回复 |

### 3.2 探索/利用比例（e 切割）

基于 37% 法则：

```java
public boolean shouldExplore() {
    double exploreBias = curiosity / maxCuriosity;
    return exploreBias > (1.0 / Math.E);
}
```

探索倾向高于 1/e ≈ 36.8% 时探索，否则利用。

---

## 4. 四模块代码分包

命名来自神经科学中对应脑区的功能启发，但代码包结构 = 模块边界，不是生物模拟。

| 包 | 职责 |
|---|------|
| `cortex/` | 规划、复杂决策、语义理解 |
| `hippocampus/` | 记忆存储、高光回忆、记忆关系图 (MemoryGraph) |
| `amygdala/` | 评价、条件反射、学习 |
| `brainstem/` | 先天反射、基础动作、生存本能 |

**基础设施 = 骨架** (`command/`, `config/`, `util/`, `log/`, `state/`, `bayesian/`, `hormonal/`)：跨领域支撑层，不属于四个模块分包。

### 4.1 brainstem（执行层） vs amygdala（判断层）

| | brainstem/ | amygdala/ |
|---|---|---|
| 职责 | "怎么做" — 动作执行 | "什么时候做" — 评价判断 |
| 内容 | 调度器、12 原子动作、寻路、Idle 动画 | 条件反射、社会镜像、评价 |
| 不含 | 任何"是否该做"的判断（除 MetaScheduler 外） | 任何"如何执行"的实现 |
| 类比 | 伺服电机和机械臂（含一个 PLC） | 条件反射回路 |

> **实现注记**：MetaScheduler 为满足每 tick 的性能要求，直接引用了底层模块而非通过 API 门面（BrainstemAPI/AmygdalaAPI/CortexAPI）。这是一个有意的性能权衡，不视为架构偏离。此外，InhibitoryControl 历史位于 `cortex/inhibitor/` 路径下，逻辑归属 L2 调度层，该路径视为历史遗留，不改变功能。

**骨架** 在 §4 表格的基础上扩展为：
- `brainstem/scheduler/` 包含 MetaScheduler（系统唯一决策调度器）——它不执行动作，只做"是否做、做什么"的选择。这是整套系统中**唯一**进行决策的模块，其余执行模块不参与决策。
- `brainstem/scheduler/` 还包含反射链、参数绑定、紧急分类、驱力计算（MotivationEngine），全部为决策调度服务。
- `brainstem/scheduler/` 还包含 `ReflexSatisfaction`（四维满意度评分：timeScore + riskScore + resourceScore + 贝叶斯后验×熟练度）和 `SurvivalChallengeMonitor`（挑战监控），组成三件套：时空缩放(TemporalScaler in `brainstem/adapter/`) + 领域自适应权重(Perspective → DomainWeights) + 满意度评分(ReflexSatisfaction — timeScore + riskScore + resourceScore)。

### 4.1a 领域执行器 (brainstem/domain/)

Stage 1-3 引入的领域执行架构，将原子动作按领域分组封装：

| 文件 | 职责 |
|------|------|
| `DomainCommand.java` | sealed 接口，按领域细分 (Break/Motion/Place/Combat/Craft/Inventory) |
| `DomainExecutor.java` | 基类接口：`canHandle()`, `execute()`, `getDomainType()` |
| `DomainRouter.java` | 路由 + tickAll + 失败收集；dispacher 使用 raw-type cast (类型安全 by canHandle) |
| `FailureContext.java` | 结构化失败报告 (reason, exception, recoveryHint) |
| `DigExecutor.java` | Break 领域：挖矿 (MIN_BREAK_TICKS=15, SWING_INTERVAL=7, SCAN_RANGE=8) |
| `MotionExecutor.java` | Motion 领域：moveTo/lookAt/jump/sprint/sneak |
| `PlaceCommand.java` | 占位 stub |
| `CraftCommand.java` | 占位 stub |
| `CombatCommand.java` | CombatCommand 记录 |
| `InventoryCommand.java` | 占位 stub |
| `BreakCommand.java` | BreakCommand 记录 |
| `MotionCommand.java` | MotionCommand 记录 |
| `CombatExecutor.java` | Combat 领域：攻击目标选择、武器切换（剑/弓）、撤退逻辑、per-bot 状态 |
| `SurvivalEquipmentManager.java` | 装备领域：自动装备最优武器/护甲/图腾 (brainstem/equipment) |

Adapter 委派：`MinecraftActionAdapter.dig()` → `DomainRouter.dispatch(BreakCommand(...))` → `DigExecutor.execute()`。同域动作走同一 executor，失败统计按 router 聚合。

**注**：PlaceCommand/CraftCommand/InventoryCommand 为 Stage 4 占位。CombatCommand 已在 Phase A 实现。
- **当前行为**：通过 DomainRouter.dispatch() 抛 UnsupportedOperationException（明确失败，非静默 null）
- **Stage 4 计划**：为每个 Command 实现对应的 Executor
- **各类型状态**：

  | Command | Executor | 状态 |
  |:-------:|:--------:|:----:|
  | BreakCommand | DigExecutor | ✅ Stage 2 完成 |
  | MotionCommand | MotionExecutor | ✅ Stage 3 完成 |
  | CombatCommand | CombatExecutor | ✅ Phase A 完成 (stateful, weapon selection, bow physics, retreat) |
  | CraftCommand | ❌ 无 | ⏳ Stage 4 待实现 |
  | PlaceCommand | ❌ 无 | ⏳ Stage 4 待实现 |
  | InventoryCommand | ❌ 无 | ⏳ Stage 4 待实现 |

### 4.2 抑制控制 (InhibitoryControl + CognitiveControl)

当前实现（InhibitoryControl）：硬否决 → 二进制 veto，静态阈值。
已实现（Phase 3）：扩展为两层抑制模型（InhibitoryControl 硬门 + CognitiveControl 连续调制）。

#### 当前结构 (InhibitoryControl)

二进制否决硬规则：
- 否决不必要的安全反射（误判危险不致命时抑制逃跑）
- 否决不恰当的习惯反射
- 否决不合适的模仿行为（如群体跳崖）
- 否决 IdleBrain 泛词误判

#### 已实现结构 (Phase 3: 四门决策流水线)

```
MetaScheduler.executeLoop():
  ┌─────────────────────────────────────────────────────┐
  │ 第一道门：InhibitoryControl 硬否决 (二进制)          │
  │   if (shouldVetoJump(from lava)): return REJECT     │
  │   if (shouldVetoAttack(villager)): return REJECT    │
  │   → 安全关键约束，不做连续调制妥协                    │
  └───────────────────┬─────────────────────────────────┘
                      ↓ pass
  ┌─────────────────────────────────────────────────────┐
  │ 第二道门：候选生成 (原有逻辑)                         │
  │   candidates = generateCandidates()                  │
  │   → 激素粗筛 + 贝叶斯候选集 (≤5)                     │
  └───────────────────┬─────────────────────────────────┘
                      ↓
  ┌─────────────────────────────────────────────────────┐
  │ 第三道门：CognitiveControl 连续调制 (新)             │
  │   inhibitedCandidates = apply(candidates, neuroState) │
  │   │ ① 合取条件检查 (require da≥0.4, 5-HT≤0.3)      │
  │   │    → 不满足条件 → 排除候选                        │
  │   │ ② 5-HT 情境分支                                  │
  │   │    → NE < 0.5: 高5-HT全局抑制                     │
  │   │    → NE ≥ 0.5: 高5-HT促逃跑、抑攻击               │
  │   │ ③ 余弦匹配反射配方 → 动态抑制权重                  │
  │   │ ④ GABA/Glu 分别注入:                             │
  │   │    → GABA → attack 刹车 (5-HT×0.5 + 失败×0.05)   │
  │   │    → Glu  → flee 油门 (DA×0.3 + NE×0.5)          │
  └───────────────────┬─────────────────────────────────┘
                      ↓
  ┌─────────────────────────────────────────────────────┐
  │ 第四道门：玻尔兹曼 / 贝叶斯精筛                       │
  │   selected = selectByBoltzmann(inhibitedCandidates)   │
  │   → MotivationEngine 最终选择                         │
  └─────────────────────────────────────────────────────┘
```

#### 阈值参数化规则 (InhibitoryControl → CognitiveControl)

- 现有静态阈值（`SAFETY_DISTANCE_THRESHOLD` 等）改为从 CognitiveControl 接收调制
- 调制方向约束：**只允许向安全方向调整**（更保守）
- 公式：`effectiveThreshold = baseThreshold + |modulation|`
- 不支持负数调制（不降低安全阈值）

| 阈值 | 基值 | 调制来源 | 说明 |
|------|:----:|---------|------|
| 坠落高度 | 3 blocks | NE↑ 时 ↑ | 警觉时更保守 |
| 熔岩距离 | 2 blocks | NE↑ 时 ↑ | 危险感知时更远 |
| 村民攻击 | 禁止 | 永不调制 | 硬安全不妥协 |

#### CognitiveControl 类设计 (已实现，见 `cortex/prefrontal/CognitiveControl.java`)

```
class CognitiveControl {
  // 4 维向量余弦匹配 + 情境分支
  computeInhibition(NeuroState state, String candidateType, String reflexId) → double

  // 候选集批量调制 (List<CandidateWeight>)
  modulateCandidates(List<CandidateWeight> candidates, NeuroState state,
                     int failureCount, double confidence, double novelty) → List<CandidateWeight>
    ├─ ① 合取条件检查 (recipe.meetsRequirements)
    ├─ ② 5-HT 情境分支
    │    if (state.ne() < THREAT_THRESHOLD):
    │        ↓ state.serotonin() × INHIBIT_STRENGTH  // 全局抑制
    │    else:
    │        ↓ state.serotonin() → flee↑, attack↓
    ├─ ③ 余弦匹配 (state.cosineSimilarity)
    └─ ④ GABA/Glu 分别注入 (NeuroDynamics)
            attackInhibition = NeuroDynamics.computeAttackInhibition(state, failureCount, confidence)
            flightExcitation = NeuroDynamics.computeFlightExcitation(state, novelty)

  // 单反射否决检查 (返回 null 表示通过)
  checkReflex(String reflexId, NeuroState state) → String

  // 阈值调制参数
  getModulation(String thresholdId, NeuroState state) → double

  // 有效阈值 = base + |getModulation|
  getEffectiveThreshold(double baseThreshold, String thresholdId, NeuroState state) → double
}

// ── 常量 ──
THREAT_THRESHOLD = 0.5    // NE 阈值，区分低威胁/高威胁
INHIBIT_STRENGTH  = 0.6   // 低威胁下 5-HT 全局抑制系数
FLEE_BOOST        = 0.4   // 高威胁下 5-HT 促进逃跑系数
ATTACK_SUPPRESS   = 0.5   // 高威胁下 5-HT 抑制攻击系数
```

**5-HT 情境分支伪代码：**

```java
private double computeSerotoninModulation(NeuroState state, String candidateType) {
    if (state.ne() < THREAT_THRESHOLD) {
        // 低威胁：5-HT 高 → 全局抑制（僵住/回避）
        return -state.serotonin() * INHIBIT_STRENGTH;
    } else {
        // 高威胁：5-HT 高 → 促进逃跑，抑制攻击
        if ("flee".equals(candidateType)) {
            return +state.serotonin() * FLEE_BOOST;
        } else if ("attack".equals(candidateType)) {
            return -state.serotonin() * ATTACK_SUPPRESS;
        }
        return 0;
    }
}
```

#### 反射配方档案

每个反射的行为配方存储在一个目标向量中：

```json
{
  "reflexId": "attack_zombie",
  "targetVector": {"ne": 0.7, "da": 0.6, "serotonin": 0.2, "ach": 0.8},
  "require": {
    "da": {"min": 0.4},
    "serotonin": {"max": 0.3}
  },
  "safetyDistance": 3,
  "neModulation": 1.5
}
```

**余弦匹配** = 当前 `neuroState` 与目标向量的相似度，用于抑制/促进候选权重。
3 维 (NE/DA/5-HT) 下 ATTACK vs EXPLORE 余弦 ≈ 0.76（难区分）；
4 维 (+ACh) 下 ≈ 0.58（可区分）。

**合取条件检查**：候选即使余弦匹配度高，也必须满足 `require` 字段的所有约束：
```java
private boolean meetsRequirements(NeuroState state, ReflexRecipe recipe) {
    for (var req : recipe.require().entrySet()) {
        double value = state.getValue(req.getKey());
        if (req.getValue().hasMin() && value < req.getValue().getMin()) return false;
        if (req.getValue().hasMax() && value > req.getValue().getMax()) return false;
    }
    return true;
}
```
例如 `attack_zombie` 要求 `DA ≥ 0.4` 且 `serotonin ≤ 0.3`，即使余弦距离很低也不选中。(DA=0.8, 5-HT=0.8) 会被 `serotonin.max` 排除——防止"高 DA + 高 5-HT"错误激活攻击。

---

### 4.3 NeuroDynamics — 攻击抑制/逃跑激励推导

GABA（攻击抑制）与 Glu（逃跑激励）是 CognitiveControl 调制流程中两个独立的推导值，分别注入候选权重，不合并为单比值。

> 注：GABA/Glu 命名借用自神经科学中 LH 双向开关的发现，工程上是两个独立的数值通道，不做生物模拟。

| 信号 | 功能 | 工程角色 |
|------|------|---------|
| GABA | 抑制攻击（随 5-HT/失败次数/低置信度递增） | `computeAttackInhibition()` |
| Glu  | 激励逃跑（随 DA/NE/新奇度递增） | `computeFlightExcitation()` |

#### 攻击刹车 (computeAttackInhibition)

由 5-HT 水平、连续失败次数、收敛置信度共同决定：

```java
public static double computeAttackInhibition(double serotonin, int failureCount, double confidence) {
    double inhibition = serotonin * 0.5;                      // 5-HT 刹车
    inhibition += Math.min(0.3, failureCount * 0.05);         // 失败累积刹车
    inhibition += (1.0 - confidence) * 0.2;                   // 低置信度刹车
    return Math.min(0.9, inhibition);
}
```

**输入**：serotonin (0-1), failureCount, convergence confidence (0-1)
**输出范围**：[0, 0.9]

CognitiveControl 中应用：`candidate.weight *= (1.0 - attackInhibition)`

#### 逃跑油门 (computeFlightExcitation)

由 DA（奖赏驱动）、NE（警觉激活）、新奇度共同决定：

```java
public static double computeFlightExcitation(double dopamine, double ne, double novelty) {
    double excitation = dopamine * 0.3 + ne * 0.5;            // 警觉驱动
    excitation += novelty * 0.2;                              // 新奇贡献
    return Math.min(0.9, excitation);
}
```

**输入**：dopamine (0-1), ne (0-1), novelty (0-1)
**输出范围**：[0, 0.9]

CognitiveControl 中应用：`candidate.type == FLEE ? candidate.weight += flightExcitation`

#### CognitiveControl 调制流程整合

```
对于每个候选:
  1. meetsRequirements(state, recipe) → 不满足则排除
  2. 余弦匹配度 = cos(state.targetVector, recipe.targetVector)
  3. serotoninModulation = computeSerotoninModulation(state, candidateType)
  4. attackInhibition = NeuroDynamics.computeAttackInhibition(...)
  5. flightExcitation = NeuroDynamics.computeFlightExcitation(...)
  6. 最终权重 = 基线权重 × 余弦匹配度 × (1+serotoninModulation) × (1-attackInhibition) + flightExcitation
```

---

## 5. 条件反射系统

### 5.1 双权重学习 (stw/ltb)

| 权重 | 性质 | 更新频率 | 说明 |
|------|------|---------|------|
| stw (short-term) | 快变 | 每次执行 +5% 成功/-3% 失败 | 快速适应短期经验变化 |
| ltb (long-term) | 慢变 | 反复成功才更新 | 慢速固化长期经验 |

### 5.2 生命周期

```
试错执行/观察学习
  ↓
累积 ≥3 次成功 → 固化 (conditioned/reflex_{skill}_{target}.json)
  ↓
每次执行 → 检查成功率
  ├─ > 40% → 正常
  ├─ 20~40% → watching（继续观察）
  └─ ≤ 20% 且 executionCount > 20 → 休眠 (dormant)，不删除，可复活
```

### 5.3 反射满意度评分 (ReflexSatisfaction)

`ConditionedReflex.scanAndTrigger()` 在候选排序中使用 `ReflexSatisfaction` 替代原始乘积公式（或 `legacy` 分支保留乘积），评分由五部分组成：

| 维度 | 计算方式 | 来源 |
|------|---------|------|
| **时间满意度** | S 形曲线：`estimatedTime < T1 → 1.0`，`T1~T2 → 线性递减`，`> T2 → 0` | `timeScore()` 三段折线 |
| **后验置信度** | 贝叶斯均值 + 熟练度加权 | `bayesianPosterior * atomProficiency` |
| **风险评分 riskScore** | `clamp(1 - dangerLevel × (1 - posterior))` | `scanAndTrigger()` 预计算，dangerLevel 由 health/NE/5-HT/night/consecutiveFails 合成 |
| **资源评分 resourceScore** | `clamp(0.3 + hungerRatio × 0.3 + posterior × 0.4)` | `scanAndTrigger()` 预计算，hungerRatio 映射饥饿度 |
| **时间缩放 timeScale** | NE/DA/5-HT → `[0.5, 2.0]` 连续缩放 | `TemporalScaler.computeTimeScale()` |

最终评分 = `wTime × timeScore + wRisk × riskScore + wSuccess × (reflexWeight × posterior × proficiency × decayFactor) + wResource × resourceScore`。

**领域自适应权重**：各 `Perspective`（SURVIVAL/TASK/SOCIAL/CURIOUS/CAUTIOUS）有独立的 `[wTime, wRisk, wSuccess, wResource]` 权重配置，通过 `computeForDomainWithScale()` 完成评分。

**向后兼容**：`compute()` 保留 9 参数重载（默认 risk=1.0, resource=1.0），旧调用无需修改。`legacy` 分支在 `BotInstance.useLegacyScoring` 为 true 时使用原始乘积公式，用于挑战系统对比。

**运行中调优**：`updateWeights(Perspective, DomainWeights)` 支持运行时更新视角权重，无需重启。


## 5a. 降级执行层 (DegradedExecutor)

当 `ConditionedReflex.scanAndTrigger()` 经过四层回退（精确→类别→相似度→图）全部返回空时，`DegradedExecutor` 作为最终"永不卡死"兜底。

### 评估函数

```
evaluate(bot) → UrgencyResult
  1. healthUrgency > 0.6 → flee
  2. hungerUrgency > 0.7 + hasFood → eat
  3. nightUrgency > 0.5 + !hasShelter → seekShelter
  4. healthUrgency > 0.4 → retreat
  5. hungerUrgency > 0.5 → collectFood
  6. needWood → digWood
```

冷却 100 tick 防刷。集成在 `BotController.onTick()` 的 P4 层，scanAndTrigger 无候选时自动执行。

## 5b. 执行后反思 (ReflectionEngine)

每次行动成败自动记录结构化 `ReflectionRecord`，永久存入 `reflections/<reflexId>.json`。

| 字段 | 含义 |
|------|------|
| `timestamp` | Unix 毫秒 |
| `success` | 是否成功 |
| `category` | 反射类别 (tree_log/hostile 等) |
| `action` | 原子动作 (dig/attack/moveTo) |
| `target` | 目标 ID |
| `posterior` | 贝叶斯后验概率 |
| `severity` | 失败级别 (RETRY/PROBABILISTIC/WATCH/IMPOSSIBLE_ATOM/DORMANT) |

### PatternSummary 动态降权

```
PatternSummary(reflexId, totalAttempts, successes, failures, successRate,
               dominantFailureCategory, deterministicSkip, consecutiveFailures)

shouldDegrade(): consecutiveFailures >= 5 || (totalAttempts >= 10 && successRate < 0.3)
```

降权：`shortTermWeight -= min(0.3, consecutiveFailures × 0.05)`，写入 reflex JSON。

### 集成进故障分类

`classifyAndApplyFailure()` 在更新贝叶斯后调用 `reflectionEngine.record()` + `adjustWeights()`，形成闭环：

```
executeReflex → fail → classifyAndApplyFailure → record reflection → adjustWeights
    ↑                                                                      ↓
    └──────────── 下⼀次 scanAndTrigger 权重更低, 倾向其他反射 ←─────────────┘
```

## 6. 观察学习系统

### 6.1 学习流程

```
Fabric 事件触发
  ↓ (60s 窗口缓冲)
行为序列缓冲
  ↓ (模式检测，≥3 次相同"触发→步骤→结果")
自动固化为条件反射 → conditioned/reflex_{skill}_{target}.json
```

### 6.2 观察数据模型

```json
{
  "id": "seq_xxx",
  "occurrences": 5,
  "proficiency": 0.85,
  "source": "OBSERVED",
  "trigger": {"nearbyBlocks": ["iron_ore"], "inventory": ["stone_pickaxe"]},
  "steps": [
    {"action": "equipItem", "target": "stone_pickaxe"},
    {"action": "moveTo", "target": "nearest iron_ore"},
    {"action": "dig", "target": "iron_ore"}
  ]
}
```

### 6.3 社会学习（两阶段）

**阶段 1 — 学习期（cortex/ 驱动）**：观察 + KNN 筛选 + 朴素贝叶斯价值评估 + 抑制控制

**阶段 2 — 反射期（amygdala/ 驱动）**：同一模式成功 ≥3 次 → 固化 → 零成本自动执行

---

## 7. 模板填空架构 (Template Fill-in)

> **当前状态**：TemplateManager 已接入。MetaScheduler.executeCortexLLM() 通过 `TemplateMatcher.match()` 路由 → `TemplateManager.fill()`。

LLM 的唯⼀工作：**看到 JSON 模板，填空**。

```
伺候层 (TemplateManager)          LLM (老爷)
  ┌─────────────────┐              ┌──────────┐
  │ 1. 选模板 (题)   │ ────JSON──→ │ 2. 填空   │
  │ 3. 解析结果      │ ←───JSON─── │          │
  │ 4. 路由执行      │              └──────────┘
  └─────────────────┘
```

### 7.1 六种模板 (四种用户交互 + 两种后台)

| 模板 | 用途 | LLM 填入内容 | 下游消费者 | 是否固化 |
|------|------|-------------|-----------|:--------:|
| CLARIFICATION | 用户输入模糊时生成澄清问题 | `{ambiguity_detected, possible_interpretations, missing_info, suggested_question}` | 直接返回用户 | 否 |
| TASK_PLAN | 生成带依赖关系的任务DAG | `{task_id, subtasks[{id, name, action, target, depends_on[{id, type, weight, bindings}]}], bottleneck_nodes}` | TaskDAG → ReflexChain | 是 |
| TASK_REPLAN | 任务执行卡死时重新规划 | 同 TASK_PLAN 格式 | 替换旧 plan, 重置 failureEscalation | 否 |
| REFLEX_CREATE | 生成新的条件反射 | `{reflex_id, display_name, steps[{action, target}]}` | ConditionedReflex | 是 |
| CHAT_RESPONSE | 生成对玩家的回复文本 | `{reply_text, suggested_emote, tone}` | 直接返回用户 | 否 |
| EVALUATION_BATCH | 批量评价反射效果 | `[{reflexId, delta}]` | EvaluationCycle | 否 (内部) |
| FAILURE_CLASSIFY | 分析失败原因 | `{featureKey, outcome}` | BayesianModule | 否 (内部) |

### 7.2 TemplateManager 核心接口

```java
public class TemplateManager {
    public CompletableFuture<JsonObject> fill(TemplateType type, Map<String, Object> context);
    void registerPostFillHook(TemplateType type, Consumer<JsonObject> hook);
    void setActivePersona(String persona);
    String injectPersona(String basePrompt, String persona);
    enum TemplateType { CLARIFICATION, TASK_PLAN, TASK_REPLAN, REFLEX_CREATE, CHAT_RESPONSE, EVALUATION_BATCH, FAILURE_CLASSIFY }
}
```

### 7.3 入口压缩 (InputDigester)

在路由之前，所有用户输入经过 `InputDigester.digest()` 压缩：正则抽取 `intent/entities/count`（模式从 `config/intent_map.json` 加载），原文截断至 80 字符为 `rawPreview` 后丢弃。LLM 只看到结构化槽位，原始长文本不会进入上下文。

成本 = 0（纯本地正则 + JSON 模式匹配）。

### 7.4 TemplateMatcher 路由

`TemplateMatcher.match(message, botCtx, worldCtx)` 按以下顺序路由用户输入（模式从 `config/template_patterns.json` 加载，首次运行自动从 `/defaults/` 生成）：

1. **LocalChatHandler** 正则匹配 → `null` (0 成本)
2. **关键词分类**（基于 `template_patterns.json` 中的意图模板 → templateType 映射）：
   - 明确任务请求 (挖/打/建+N) → `TASK_PLAN`
   - 明确学习请求 (学/记住/如果...就) → `REFLEX_CREATE`
   - 纯社交 (你好/谢谢/喵~) → `CHAT_RESPONSE`
   - 模糊输入 (怎么/如何/能不能/?) → `CLARIFICATION`
   - 含具体名词 → `TASK_PLAN`, 否则 `CHAT_RESPONSE`

### 7.5 CLARIFICATION 调用限制

- 同一对话轮次最多调用一次 CLARIFICATION
- 用户二次模糊 → 返回预设 "请更具体地描述你的需求"

### 7.6 CHAT_RESPONSE 预算隔离

- 独立预算配额 (最多 50 次), 与核心循环完全隔离
- 预算耗尽 → 回退到 LocalChatHandler 预设回复

### 7.7 PersonaManager

PersonaManager 管理角色设定注入:

- `setPersona(description)` → 覆盖 `TemplateManager.activePersona`
- 只影响 `CHAT_RESPONSE` 和 `REFLEX_CREATE` 的系统提示
- 不影响 `TASK_PLAN`、`CLARIFICATION`、贝叶斯统计、反射权重、激素系统
- 持久化到 `eagent/skills/character/active_persona.txt`

---

## 8. 基本动作池 (14 原子动作)

| 动作 | 说明 | 返回 |
|------|------|------|
| `moveTo(x,y,z)` | 移动到坐标 | boolean |
| `lookAt(x,y,z)` | 看向坐标 | void |
| `dig(x,y,z)` | 挖掘方块 | boolean |
| `attack(entity)` | 攻击实体 | boolean |
| `placeBlock(block, face)` | 放置方块 | boolean |
| `useItem()` | 使用手中物品 | boolean |
| `equipItem(name)` | 装备物品 | boolean |
| `openBlock(x,y,z)` | 打开容器 | boolean |
| `closeWindow()` | 关闭容器 | void |
| `clickSlot(slot, button)` | 点击容器格子 | boolean |
| `chat(msg)` | 发送聊天 | void |
| `jump()` | 跳跃 | boolean |
| `sprint(enable)` | 冲刺开关 | void |
| `dropItem(slot)` | 丢弃物品 | boolean |

> **注**：上表为 14 个核心原子动作。`BasicActionAdapter` 实际有 22 个方法（14 原子 + 8 复合）：原子动作同左表，复合动作包括 `craft`、`flee`、`eat`、`retreat`、`avoidLava`、`seekShelter`、`collectItem`、`sneak`。
>
> **注意**：目前只有 moveTo/lookAt/dig/jump/sprint/sneak 通过 DomainRouter → Executor 执行链（见 §4.1a）。attack/placeBlock/craft 等其余动作仍在 MinecraftActionAdapter 中直接实现，**计划在 Stage 4 迁移到 DomainRouter 统一路由**。

---

## 9. 贝叶斯模块 (BayesianModule)

> **注意**：本系统中的贝叶斯模块是被动数据仓库，不参与实时决策门控。它提供成功率统计、记忆相关性排序、社交镜像筛选等服务。决策由 Subsumption Architecture (L0-L5) + 玻尔兹曼驱力竞争完成。理论依据见 [THEORY.md](./THEORY.md)。

### 9.1 三层存储

| 存储区 | 内容 | 更新频率 | 公式对应 |
|--------|------|---------|---------|
| 先验条件表 | `P(success)` | 极慢 (平滑更新) | 先验概率 |
| 后验观测链 | `P(feature\|outcome)` | 快 (每次错误) | 似然度 |
| 主题锚定区 | 当前任务上下文 | 中 | 条件过滤器 |

### 9.2 核心公式

```
P(成功 | 特征) ∝ P(特征 | 成功) × P(成功)
```

### 9.3 收敛判断（e 切割）

```java
public boolean isConverged(Posterior posterior) {
    double changeRate = Math.abs(posterior.newProb - posterior.oldProb) / posterior.initialVariance;
    return changeRate < (1.0 / Math.E);  // 1/e ≈ 0.3679
}
```

---

## 10. 成本模型

| 操作 | 执行者 | LLM 调用 | 频率 |
|------|--------|:-------:|:----:|
| 安全反射 (flee/eat) | 脑干 | 0 | 每 tick |
| 条件反射执行 | 脑干 | 0 | 每次匹配 |
| 试错执行 | 脑干 | 0 | 每次执行 |
| 自动固化 | 脑干 | 0 | ≥3 成功 |
| 观察学习 | 脑干 | 0 | 每次 Fabric 事件 |
| Idle 主动建议 | 脑干 | 0 | idle > 30s |
| 本地聊天 (LocalChatHandler) | 脑干 | 0 | 正则匹配 |
| **────** | | **────** | **────** |
| 澄清问题 (CLARIFICATION) | LLM | 1 | 模糊输入 (同一轮次只一次) |
| 任务分解 (TASK_PLAN) | LLM | 1 | 每个新任务 |
| 重规划 (TASK_REPLAN) | LLM | 1 | 任务失败 esculation (上限 3 次/任务) |
| 反射创建 (REFLEX_CREATE) | LLM | 1 | 每个新行为 |
| 闲聊回复 (CHAT_RESPONSE) | LLM | 1 | 按需 (独立预算 50次) |
| 批量评价 (EVALUATION_BATCH) | LLM | 1 | 每 30min (内部) |
| 失败分类 (FAILURE_CLASSIFY) | LLM | 1 | 每次未覆盖的失败 (内部) |

**挂机 1 小时: 0 次 API。活跃 1 小时: ~4-8 次。**

### 10.1 反射包 V1 (ReflexPack) — 零成本的批量移植

V1 包 = JSON 文件，打包所有 `conditioned/*.json` + 可选贝叶斯先验。纯本地文件操作，不触发 LLM。

| 操作 | 成本 | 场景 |
|------|:----:|------|
| `export bot packname` | 0 | 备份/分享当前反射经验 |
| `export bot packname noprior` | 0 | 只分享步骤，不含成功率 |
| `import bot packname` | 0 | 合并（保留本地新经验） |
| `import bot packname reset` | 0 | 冷启动（完全覆盖） |

**核心原则**：导入后不自闭，Bot 继续用自己的贝叶斯模块去适应新环境。包只是初始信念。

### 10.2 玩法包 V2 (PlaystylePack) — 一次性初始化整个行为配置

V2 玩法包在 V1 反射包基础上扩展了 profile/knowledge/config 三个额外段，用于一次性初始化 Bot 的完整行为配置。

| 段 | 内容 | 说明 |
|:----:|------|------|
| `profile` | BotParams (α/β/temperature) + HormonalPreset (7 维) + PerspectiveWeights (5 维) | 覆盖 Bot 的行为参数和初始激素状态 |
| `reflexes` | 同 V1 反射列表 | 同 V1 |
| `knowledge` | 玩法专属知识 (recipes/entities/item_uses/tool_map) | 隔离到 `KnowledgeBase` 的 playstyle 分区，查询优先于全局知识 |
| `config` | 共享池约束覆盖 (chain_max_length 等) | 预留，当前仅记录日志 |

**应用链路**：

```
BotInstance.applyPlaystylePack(pack)
  1. BotParams.override(alpha, beta, temp)     ← 热替换
  2. HormonalSystem.applyPreset(preset)         ← 批设 7 维
  3. MotivationEngine.setPerspectiveWeights()    ← 覆盖 5 视角权重
  4. KnowledgeBase.switchPlaystyle(id)           ← 切换知识分区
  5. Reflexes → 逐文件写入 conditioned/          ← 同 V1
```

**5 预设包**（`resources/packs/`）：

| 包名 | α | β | 温度 | 主导视角 | 适用场景 |
|:----:|::|::|:---:|:--------:|---------|
| aggressive | 0.25 | 0.008 | 0.55 | SURVIVAL + DA↑ | 好战型，低恐惧高攻击性 |
| explorer | 0.45 | 0.005 | 0.50 | CURIOUS + ACh↑ | 好奇心驱动，地图遍历 |
| social | 0.35 | 0.012 | 0.45 | SOCIAL + 5-HT↑ | 高亲密度，爱陪伴玩家 |
| cautious | 0.18 | 0.028 | 0.25 | CAUTIOUS + NE↑ | 高规避风险，保守行动 |
| builder | 0.30 | 0.015 | 0.35 | TASK + 5-HT/DA中 | 偏好建设与收集 |

这些包可通过 `/ai playstyle load <name> [botName]` 运行时加载，不影响其他 Bot，支持 `/ai playstyle list` 预览。

**命令**：

| 命令 | 说明 |
|------|------|
| `/ai playstyle list` | 列出可用玩法包 (v2 含 profile 标注) |
| `/ai playstyle load <包名> [机器人]` | 加载 V2 玩法包 (兼容 V1 自动回退) |
| `/ai playstyle export <机器人> <包名>` | 导出 Bot 当前状态为 V2 玩法包 |
| `/ai playstyle current [机器人]` | 查看 Bot 当前参数/激素/反射数 |

---

## 11. 聪明与智慧的工程定义

- **聪明 = 大量高质量的条件反射（反射池 × 强化质量）**。知道"怎么做"，反应快，执行准。
- **智慧 = `selectPerspective()` 的精度**。知道"现在是什么情况"，从而选择对的反射链。

```
低智慧 ─────────────────────────────→ 高智慧
僵化（永远用本能）←────→ 灵活（自如升降级）←────→ 混乱（永远用LLM）
```

**智慧 = f(标签精准度, 调控灵活性, 调度经济性)**

---

## 12. 硬编码分级

| 层 | 名称 | 硬编码 | 可配置 | 可学习 | 可遗传 | 存储位置 |
|:--:|------|:------:|:------:|:------:|:------:|---------|
| L0 | Kernel | ✅ 永久 | ❌ | ❌ | ❌ | 代码 |
| L1 | Config | ❌ | ✅ | ✅ | ✅ | `config.json` + `BotParams` |
| L2 | Scheduler | ⚠️ 临时 | ❌ | ✅ | ❌ | `DispatchReflex` 权重 |
| L3 | Knowledge | ❌ | ✅ (人工) | ✅ (LLM) | ❌ (共享) | `knowledge_base.json` + `category_display.json` (CategoryMapper 已外部化) |
| L4 | Reflexes | ❌ | ❌ | ✅ | ✅ | `conditioned/*.json` |

---

## 13. 繁衍系统 (三规则继承)

Bot 死亡或 despawn 时，其参数 (α/β/temperature) + 谱系数据被存入 `bots/genomes/` 基因组存档。新 Bot spawn 时自动从存档继承：

| 规则 | 应用于 | 实现 |
|------|--------|------|
| **1. 平均 (Intersection Average)** | BotParams | 双亲参数取均值 |
| **2. 减半 (Random Halving)** | BotParams | α/β/temperature 各 50% 概率减半 |
| **3. 突变 (Mutation)** | BotParams | α/β/temperature + gaussian(σ=0.05/0.003/0.08) |

> 脚手架式反射继承是独立机制，见 §13.2。与三规则并列：三规则操作 BotParams（遗传参数），脚手架操作 ConditionedReflex（继承反射先验后自试验证）。

中心极限定理保证：多次继承后种群参数趋于正态分布。

### 13.1 遗传流程

```
Bot死亡/despawn
  ↓
saveDeathGenome(cause)
  ├── botParams → bots/{uuid}/bot_params.json    (per-bot参数归档)
  └── GenomeArchivist → bots/genomes/{uuid}.json  (种群基因组存档)
        ↓
新Bot spawn
  ├── inheritGenome=true? → GenomeArchivist.getLatestGenomeParams()
  ├── 有祖先 → BotParams.inherit(parent[, p2])   (平均+突变)
  ├── 无祖先 → BotParams.generate()               (随机初始化)
  └── copyReflexesFromMentor()                    (脚手架 trial-first)
        └── bot自试3次 → healthy/dormant
```

### 13.2 脚手架式反射继承 (Trial-First)

继承的反射不直接加入——bot 必须先试 3 次，通过了才算自己的：

```
copyReflexesFromMentor()
  │
  ├── 复制完整模板 (不截断、不减半)
  ├── status = "trial"
  ├── trialSuccesses = 0, trialFailures = 0
  └── 中性权重 (stw/ltb = 0.5, proficiency = 0.3)
        │
        ▼ bot 触发反射
  ConditionedReflex.executeReflex()
        │
        ├── 成功 → trialSuccesses++
        │        ├── ≥ 3 → status = "healthy", proficiency = 0.5  → 正式加入
        │        └── < 3 → 继续试炼
        │
        └── 失败 → trialFailures++
                 ├── ≥ 3 → status = "dormant", 移入 archived → "不适合我"
                 └── < 3 → 继续试炼
```

试炼期内不更新 `stw/ltb`（权重中性），不触发 `handleReflexFailure` 的 watching/dormant 降级链。bot 在"安全区"内自己试，成不成都由自己的经历决定。

### 13.3 基因组存档格式

```json
{
  "botId": "uuid",
  "name": "miner_bot",
  "alpha": 0.35, "beta": 0.012, "temperature": 0.4,
  "generation": 3,
  "parentId": "parent_uuid",
  "cause": "killed/despawned/removed",
  "diedAt": 1718000000000
}
```

存档仅保留 BotParams（遗传特质），不保留反射文件（反射通过 trial-first 脚手架继承）。

---


## 14. 组件归属表

> ⚠️ **重要：双分类体系注意**
> 本文档使用两套互不兼容的分类维度：
> - **§1 运行时拦截层 (L0-L6)：** 按执行时机和触发条件分类。L0=生存本能, L1=先天预警, L2=条件反射, L3=模仿学习, L4=自组织, L5=本地规划, L6=LLM。
> - **§12/§14 硬编码分级：** 按可配置性/可学习性/可遗传性分类。L0=Kernel(永硬编码), L1=Config(可配置), L2=Scheduler, L3=Knowledge, L4=Reflexes。
> - **ConditionedReflex 在两种分类中位置不同：** §1 运行时 → L2（条件反射执行时机），§12/§14 分级 → L4（可学习可遗传）。
> - **讨论归属时请标注分类体系**，例如"在 §1 运行时视角下，ConditionedReflex 属 L2"。

| 组件 | 归属层 | 存储位置 |
|------|:------:|---------|
| InnateReflexRegistry | L0 | `innate_reflexes.json` |
| ConditionedReflex | L4 | `conditioned/*.json` |
| BotParams | L1 | `bot_params.json` |
| HormonalSystem | L1 | 内存 |
| OneShotAlarmSystem | L2 | `alarms/*.json` |
| DispatchReflex | L2 | `dispatch_weights.json` |
| InhibitoryControl | L2 (brainstem/scheduler) | 内存 |
| KnowledgeBase | L3 | `knowledge_base.json` |
| ThresholdConfig | L1 | `thresholds.json` |
| BayesianModule | 骨架 | `bayesian/shared_prior.json` + per-bot posterior |
| TagResolver | L3 (util) | `config/entity_aliases.json` + Minecraft Tag 系统 |
| TemplateManager | 骨架 (cortex/api) | 模板定义 |
| SocialObserver | 骨架 | 内存 |
| BehaviorStats | 骨架 | 内存 |
| ChatSessionManager | 骨架 (cortex/chat) | 内存窗口 |
| ReflexPackManager | 骨架 (brainstem/bot) | 实例注入（botId + BayesianModule + BotInstance） `reflex_packs/*.json` |
| PlaystylePack | L3 (cortex/api) | `reflex_packs/*.json` — V2 pack 数据模型 |
| HormonalPreset | L1 (cortex/api) | 7 维激素预设记录 |
| IPlaystylePlugin | 预留 (cortex/api) | 未来 L2 专精模块接口 |
| WorldContext | 骨架 (api) | `WorldContext` 接口 / `WorldContextImpl` |
| BotContext | 骨架 (api) | `BotContext` 接口 / `BotContextImpl` |
| MetaState | 骨架 (api) — 具体类，非接口 | `MetaState` 类（每 tick 新建，状态容器，不定义行为） |
| BrainstemAPI | 骨架 (api) | 脑干门面接口（innateReflexes/basicActions/inhibitor） |
| AmygdalaAPI | 骨架 (api) | 杏仁核门面接口（socialObserver/familiarityTracker） |
| CognitiveBrainAPI | 骨架 (api) | 顶层脑门面（world/bot/botCount/config/executionLogger） |
| CognitiveBrain | 骨架 (api/impl) | CognitiveBrainAPI 实现 |
| CortexAPI | 骨架 (api) | 前额叶门面接口（localPlanner/chatHandler/templateManager/aiClient/chatAI） |
| ReflexChain | L4 (时序/执行) | `dag/` 索引 + 反射节点间关系 |
| TaskDAG | 骨架 | 内存 (cortex/planner) |
| ParameterBinder | 骨架 | 内存 |
| SharedPoolConfig | 骨架 (config) | `config/` |
| MemoryGraph | L4 (hippocampus) | `memory_graph.json` |
| MemoryNode / MemoryEdge | L4 (hippocampus) | 内存 + `memory_graph.json` |
| MemoryQuery | L4 (hippocampus) | 内存 |
| LearningSystem | L4 (amygdala/learning) | `memory/trials/observed_*.json` |
| TrialStorage | L4 (hippocampus/storage) | `memory/trials/` |
| HighlightStorage | L4 (hippocampus/storage) | `memory/highlights/` |
| BehaviorEventHandler | L2 (amygdala/character) | 内存 |
| EvaluationCycle | L2 (amygdala/character) | 内存 |
| CategoryMapper | L3 (amygdala/learning) | `config/category_display.json` (委托 TagResolver) |
| NaiveBayesClassifier | L3 (amygdala) | 内存 |
| PlanManager | L5 (cortex/planner) | `active_plan.json` |
| Plan | L5 (cortex/planner) | `active_plan.json` |
| AITaskPlanner | L6 (cortex/api) | 内存 |
| AIClient / AIConfig / AIResponse | L6 (cortex/api) | 内存 + `config/api_key.json` |
| DeepSeekClient | L6 (cortex/api) | 内存 |
| AIChatHandler | L6 (cortex/api) | 内存 |
| TemplateMatcher | L6 (cortex/api) | 内存 |
| AIMemoryGenerator | L6 (cortex/api) | 内存 |
| LocalChatHandler | L5 (cortex/chat) | 内存 |
| ChatSessionManager | 骨架 (cortex/chat) | 内存窗口, ChatSlot {intent, entities, rawPreview} |
| InputDigester | L5 (cortex/chat) | 内存 — 入口压缩: 抽取 intent/entities/count 后丢弃原文 |
| KnowledgeBase | L3 (cortex/planner) | `config/knowledge_base.json` |
| UrgencyClassifier | L2 (brainstem/scheduler) | 内存 |
| ReflexChain | L4 (brainstem/scheduler) | `dag/` 索引 + 反射节点间关系 |
| TaskDAG | 骨架 (cortex/planner) | 内存 |
| ParameterBinder | 骨架 (brainstem/scheduler) | 内存 |
| BotSpawner | L2 (brainstem/bot) | 内存 |
| BotPlayer | L2 (brainstem/bot) | 内存 (Minecraft 假人) |
| GreedyNavigator | L3 (brainstem/navigation) | 内存 |
| NavigationController | L3 (brainstem/navigation) | 内存 |
| Skill / SkillManager | L2 (brainstem/skill) | 内存 |
| DomainRouter | 待定 (brainstem/domain) | 内存 — 命令分发路由 |
| DigExecutor | 待定 (brainstem/domain) | 内存 — Break 域挖掘 |
| MotionExecutor | 待定 (brainstem/domain) | 内存 — Motion 域移动 |
| CombatExecutor | ✅ Phase A (brainstem/domain) | 内存 — Combat 域攻击 (stateful per-bot, weapon selection) |
| SurvivalEquipmentManager | L0 (brainstem/equipment) | 静态工具 — equipBestWeapon/equipBestArmor/equipTotem |
| DegradedExecutor | P4 兜底 (brainstem/scheduler) | 内存 — 硬编码安全优先级行动 |
| ReflectionEngine | L2 (amygdala) | `reflections/<reflexId>.json` — 结构化失败历史 |
| ReflexSimilarityScorer | 骨架 (brainstem/reflex) | 内存 — 13 维特征向量 cosine similarity |
| CraftExecutor | ⏳ Stage 4 (brainstem/domain) | 内存 — Craft 域合成 |
| PlaceExecutor | ⏳ Stage 4 (brainstem/domain) | 内存 — Place 域放置 |
| InventoryExecutor | ⏳ Stage 4 (brainstem/domain) | 内存 — Inventory 域物品操作 |
| InnateReflex | L0 (brainstem/innate) | `innate_reflexes.json` (含 Phase A 新增: equip_armor/equip_totem/ranged_attack) + `innate_reflex_weights.json` |
| TriggerType | L0 (brainstem/innate) | 内存 — 12 枚举值 (含 5 Phase A 新增: ARMOR_SLOT_EMPTY/OFFHAND_EMPTY/HAS_TOTEM/BOW_IN_HOTBAR/ARROW_IN_INVENTORY) |
| ReflexTrigger | L0 (brainstem/innate) | 内存 — 12 种 TriggerType |

---

## 15. DAG 任务依赖图 (数据/逻辑层)

DAG 描述任务子步骤之间的**数据/逻辑依赖关系**，与 ReflexChain 的时序/执行链分层独立。

### 15.1 TaskDAG 数据模型

```json
{
  "task_id": "mine_10_iron",
  "subtasks": [
    {
      "id": "a1", "name": "找到铁矿", "action": "moveTo", "target": "iron_ore",
      "depends_on": []
    },
    {
      "id": "a2", "name": "挖掘铁矿", "action": "dig", "target": "iron_ore",
      "count": 10,
      "depends_on": [{
        "id": "a1", "type": "hard", "weight": 0.95,
        "bindings": [
          {"from": "output.position", "to": "target_position"},
          {"from": "output.block_type", "to": "block_type"}
        ]
      }]
    }
  ],
  "bottleneck_nodes": ["a2"]
}
```

### 15.2 依赖类型

| 类型 | 失败后果 | 回退策略 |
|------|---------|---------|
| `hard` | 下游不可执行 | 重试/回溯上游 |
| `soft` | 可尝试替代方案 | 查 weight 最高替代 |

### 15.3 瓶颈节点识别

- LLM 粗分解时主动标记语义瓶颈（一次性）
- 运行时入度检测自动标记结构瓶颈（入度 ≥ 3 → `isBottleneck = true`）

---

## 16. ReflexChain 执行链表 (时序/执行层)

ReflexChain 描述反射的**时序执行顺序**，与 DAG 的依赖关系分层独立。

### 16.1 树形多指针

```java
class ReflexNode {
    String id;
    String reflexId;
    Set<String> prev;  // 前置节点 (多指针)
    Set<String> next;  // 后续节点 (多指针)
    boolean isBottleneck;
    double baseWeight;
    double getSharedWeight(String taskId) {
        return baseWeight * getTaskConfidence(taskId);
    }
}
```

### 16.2 共享节点权重动态调整

- 多任务均成功 → 增加共享权重
- 仅一任务成功、其他失败 → 降低该节点在失败任务中的置信度，保留基础权重

### 16.3 Alternative 选择

`selectAlternative(failedNodeId)` 在节点失败时查询 ReflexGraph 的 **ALTERNATIVE** 边：

```java
public String selectAlternative(String failedNodeId) {
    // 查询 ReflexGraph 中失败节点的 ALTERNATIVE 后继
    // 动态权重 >= 0.5 → 创建替代节点, 桥接前后依赖
    // 返回替代节点 ID
}
```

无需 LLM，纯本地图查询。

### 16.4 Skip 传播

`getSkippableNodes(completedNodeId)` 检测已完成节点的后继中哪些有 ALTERNATIVE 边，支持调度层跳过。

---

## 17. MetaScheduler 主循环 (tick 驱动)

实际的决策循环由 `MetaScheduler.tick()` 每个 server tick 驱动，并非事件驱动刷新：

```
MetaScheduler.tick():
  ┌─ LLM 冷却/预算检查 (shouldInvokeLLM / isChatBudgetExhausted)
  │    冷却期 → 跳过 LLM 调用，仅执行 L0-L4 反射
  ├─ tickNovelEntities()
  ├─ 消费 pendingTaskFailure (来自 TaskExecutor 的 unable exhausted 回调)
  ├─ computeDrives(botCtx, worldCtx, bot)          [MotivationEngine]
  │   ├─ resourceStress(fillRatio, depletionRate, replenishDifficulty)
  │   ├─ computeSurvivalDrive() → survivalUrgency
  │   ├─ computeTaskDrive() → taskInertia (成功放大/失败衰减)
  │   ├─ computeCautiousDrive() → threat distance factor
  │   ├─ computeSocialDrive()
  │   └─ computeCuriosityDrive()
  ├─ perspective = select(botCtx, drives)           [MotivationEngine]
  │     T = baseTemp × (1 − pressure × 0.85)
  │     Boltzmann 抉择 → Perspective (SURVIVAL/TASK/SOCIAL/CURIOUS/CAUTIOUS)
  ├─ ProblemLabel label = labelProblem(botCtx, worldCtx, bot, perspective)
  ├─ FlowLevel flow = getFlowAdjustment(botCtx, bot, state)
  │   ├─ AUTOPILOT (降级): proficient & stable / success>10 / inactive>5min
  │   ├─ OVERRIDE (升级): consecutiveFail≥2 / novelEntity / suddenChange /
  │   │   urgentMsg / stuck>600 ticks / stuck>2000+urgency>0.5
  │   └─ NORMAL
  ├─ temporalScaler.update(hormones, bot, ticks, pressure)
  ├─ executeHabitLayerWithGating(..., state, flow)  [L0-L4 反射]
  │   ├─ CognitiveControl 否决检查
  │   └─ GatingArbiter 环境可控性仲裁
  └─ processTemplateResult(...)                     [L5-L6 LLM 模板]
      ├─ TASK_PLAN → TaskDAG.fromLLMJson → ReflexChain.buildFromDAG
      ├─ TASK_REPLAN → 新计划替换旧 plan (失败升级链)
      ├─ 其他模板 → 按类型分配
      └─ failure escalation → onUnableExhausted → requestTaskFailureEscalation
           → MetaState.setFailureEscalation(true) → executeReplan()
```

### 17.1 失败升级链

TaskExecutor 内部无法完成时触发回调：

```
TaskExecutor.retryOnce() 连续失败 ≥ MAX_UNABLE_RETRIES(5)
  → onUnableExhausted.run() [EAgent 注册]
    → metaScheduler.requestTaskFailureEscalation(state)
      → state.setFailureEscalation(true); state.incrementReplanCount()
        → MetaScheduler.tick() 检测 hasFailureEscalation()
          → 尝试 TASK_REPLAN (LLM 生成新 plan)
            → 成功 → 重置 failureEscalation, 执行新 plan
            → 失败或 replanCount ≥ 3 → 跳过 (防止 LLM 死循环)
```

### 17.2 约束

- LLM 冷却期 `LLM_COOLDOWN_TICKS=400`，不触发模板填空
- 冷却期也跳过 TASK_REPLAN（不影响纯反射执行）
- replan 上限 3 次，防止无限重复规划
- MetaScheduler 是**唯一决策节点**，所有执行模块不参与决策

---

## 18. 环境可控性指数 (GatingArbiter)

### 18.1 计算公式

```java
// GatingArbiter 封装此计算，与 BayesianModule 纯统计角色分离
public double computeControllability(String reflexId, List<BayesianFeature> features) {
    double confidence = bayesianModule.getConfidence(reflexId);
    double variance = confidence * (1.0 - confidence); // Beta分布方差
    double controllability = 1.0 / (1.0 + variance / varianceScale);
    if (features contains "environment_change") controllability *= 0.5;
    return clamp(controllability, 0, 1);
}
```

方差使用 `p*(1-p)` 公式，max=0.25 (p=0.5 最不确定)，min→0 (p→0或1 最确定)。对应 `VARIANCE_SCALE=0.25`。

### 18.2 分层仲裁

| 方差 | 后验分布 | 可控性 | L1/L3 行为 |
|:----:|---------|:------:|-----------|
| 低 | 集中尖锐 | 高 | 需贝叶斯验证后固化 |
| 高 | 分散平坦 | 低 | 允许直接固化 |

---

## 19. 死路三条件

按优先级排序，任一满足即触发回退或 LLM 兜底：

```java
public DeadEndResult isDeadEnd(Reflex reflex, BotContext context) {
    if (reflex.consecutiveFailures >= 5)
        return new DeadEndResult(true, "CONSECUTIVE_FAILURES");
    if (bayesian.posteriorSuccessRate(reflex, context) < 0.1)
        return new DeadEndResult(true, "LOW_POSTERIOR");
    if (reflex.attempts > 37 && !reflex.isStable())
        return new DeadEndResult(true, "EXPLORATION_EXHAUSTED");
    return new DeadEndResult(false, null);
}
```

---

## 20. 贝叶斯双向推理

### 20.1 顺推 (inferForward)

从当前状态预测最大可能下一步，贝叶斯原生能力：

```java
public List<Candidate> inferForward(BotState state, Evidence evidence) {
    return getPosteriorSorted(state, evidence);
}
```

### 20.2 倒推 (inferBackward)

从目标反推前置条件，贝叶斯仅做概率排序，因果关系来自外部：

```java
public List<Precondition> inferBackward(Goal goal, Evidence evidence) {
    // 1. 历史归纳: getFrequentPredecessors(goal)
    // 2. 反射元数据: goal.prev
    // 3. LLM 按需生成
    return sortByPrior(getPreconditions(goal, evidence));
}
```

---

## 21. 回退五阶段

```java
public void rollback(Node node, Failure failure) {
    // Stage 1: 本地重试
    if (node.retryCount < MAX_RETRY) { retry(node); return; }
    // Stage 2: 替代方案
    Reflex alt = findAlternative(node);
    if (alt != null && bayesian.posterior(alt) > MIN_ALT_THRESHOLD) { tryAlternative(alt); return; }
    // Stage 3: 回溯上游
    Node upstream = traceToUpstream(node);
    if (upstream != null && bayesian.hasDegradedOutput(upstream)) { rollback(upstream, failure); return; }
    // Stage 4: 麦穗探索
    if (wheatEarExplore(node)) { markExploration(node); return; }
    // Stage 5: LLM 重新规划
    llmReplan(memento);
}
```

---

## 22. 四类共享池形式化参数

| 池 | 约束 | 配置键 |
|----|:----:|--------|
| 囊泡超级池 | 反射链长度 ≤ 5 | `chain_max_length` |
| 工作记忆绑定池 | 贝叶斯候选集 ≤ 5 | `bayesian_candidate_limit` |
| 跨脑共享子空间 | 共享先验比例 10-30% | `shared_prior_ratio` |
| 归一化网络池 | 总驱力 = 1.0 | 硬编码归一化 |

四池约束独立，不交叉合并。生物对应关系见 [THEORY.md](./THEORY.md)。

---

## 23. 边界条件门控与提前返回

### 23.1 门控位置

在贝叶斯精筛后、反射执行前插入：

```
贝叶斯精筛 → 依赖检查 → 参数绑定 → 前置条件门控 → 反射执行
                                          ↓ 不通过
                                      提前返回
```

### 23.2 三类前置条件

| 类型 | 例子 | 检查方式 | 成本 |
|------|------|---------|:----:|
| 物品条件 | `requires_item: "wooden_pickaxe"` | 查背包 | 0 |
| 环境条件 | `block_reachable(x,y,z)` | 调导航 | 0 |
| 状态条件 | `stress < 0.8` | 读激素 | 0 |

### 23.3 依赖检查 ≠ 前置条件检查

| 检查 | 归属 | 失败后果 |
|------|------|---------|
| 依赖检查 (DAG) | depends_on | 回退五阶段 (§21) |
| 前置条件 (反射) | preconditions[] | skip/wait/defer |

### 23.4 贝叶斯预判提前返回

```java
if (bayesian.posteriorMean(reflex, context) < 0.05) {
    return EarlyReturn(reason: "posterior_too_low", updateBayesian: false);
}
```

### 23.5 fail_strategy 三种策略

| 策略 | 行为 | 适用场景 |
|:----:|------|---------|
| `skip` | 尝试下一个候选反射 | 同类候选多 (多种攻击方式) |
| `wait` | 挂起当前任务，等待条件满足 | 临时条件 (等天亮) |
| `defer` | 标记阻塞，推进不相关子任务 | DAG 并行场景 |

默认值: `skip`

### 23.6 反射 JSON 新字段

```json
{
  "reflex_id": "reflex_dig_iron_ore",
  "input_slots": [
    {"name": "target_position", "type": "BlockPos", "required": true},
    {"name": "block_type", "type": "string", "optional": true}
  ],
  "capabilities": ["break_block", "collect_item"],
  "preconditions": [
    {"type": "item", "key": "main_hand", "match": "pickaxe", "fail_strategy": "skip"},
    {"type": "state", "key": "stress", "operator": "<", "value": 0.8, "fail_strategy": "defer"}
  ]
}
```

---

## 24. 参数绑定 (ParameterBinding) — 填空题显式化

### 24.1 存储方案 (方案 B)

- **反射 JSON**: 声明 `input_slots`（参数槽位定义）
- **TaskDAG**: `depends_on` 中 `bindings` 字段声明从上游输出到下游输入的映射

### 24.2 支持变换类型

| 变换 | 格式 | 例子 |
|------|------|------|
| 直接传递 | `from → to` | `output.position → target_position` |
| 变换 | `transform` 字段 | `"transform": "offset(x, y+2, z)"` |

### 24.3 绑定时机

```
依赖检查 → 参数绑定 → 前置条件检查 → 反射执行
```

绑定失败 → 视为依赖不满足 → 回退五阶段

### 24.4 绑定执行逻辑

```java
public Map<String, Object> bindParameters(SubtaskNode node, Map<String, Object> upstreamOutputs) {
    Map<String, Object> params = new HashMap<>();
    for (Binding binding : node.getBindings()) {
        Object value = upstreamOutputs.get(binding.from);
        if (value == null) throw new BindingError("Missing upstream output: " + binding.from);
        if (binding.transform != null) value = applyTransform(value, binding.transform);
        params.put(binding.to, value);
    }
    return params;
}
```

---

## 25. 记忆关系图 (MemoryGraph)

MemoryGraph 是陈述性记忆的关系层，与 MemoryManager 的切片存储分层独立。它只记录记忆之间的**关系**，不记录记忆内容本身。

### 25.1 数据模型

```java
// 节点：轻量引用，不污染 MemoryEntry
record MemoryNode(String memoryId, String summary, long timestamp, int gameDay) {}

// 边：带类型的加权有向边 (mutable class, weight ∈ [0.1, 1.0])
class MemoryEdge {
    String fromId(), String toId(), RelationType type();  // 不可变
    double weight();                                       // 可变, updateWeight(delta) 夹具 [0.1,1.0]
    long createdAt(), long lastReinforcedAt();             // 创建/最后强化时间戳
}

enum RelationType { CAUSAL, TEMPORAL, SIMILARITY, CONTRAST }
```

### 25.2 存储格式

独立文件 `eagent/bots/{uuid}/memory/memory_graph.json`：

```json
{
  "version": "1.0",
  "lastSavedDay": 42,
  "nodes": [
    {"memoryId": "mem_001", "summary": "挖了5个铁矿", "timestamp": 1718300000, "gameDay": 1}
  ],
  "edges": [
    {"fromId": "mem_001", "toId": "mem_002", "type": "CAUSAL", "weight": 0.85, "createdAt": 1718300000000, "lastReinforcedAt": 1718300050000}
  ]
}
```

### 25.3 边推断 (inferEdges)

每次创建新记忆时同步推断，但受显著性门控：

| 推断条件 | 结果类型 | 权重公式 |
|---------|---------|---------|
| 同 gameDay，时间相邻 | TEMPORAL | `1.0 - gap / avgInterval` |
| 新 learning 含已有 skill | CAUSAL | `0.5 + overlap * 0.15` |
| 贝叶斯相似度 > 0.7 | SIMILARITY | `bayes.predictRelevance(a, b)` |
| 含"成功"/"失败"冲突词 | CONTRAST | `0.3`（固定） |

新记忆必须先过显著性门控（`computeSalience() >= 0.6`）：

| 显著性来源 | 加分 | 条件 |
|-----------|:---:|------|
| keyLearnings 非空 | +0.3 | 有学习内容 = 重要性信号 |
| relatedSkills 数量 | +0.1/个，上限 +0.2 | 技能跨越多类别 |
| preferencesUpdated 非空 | +0.2 | 偏好变更 = 结果显著性 |
| 时间间隔 > 2× 平均间隔 | +0.3 | 长时间无记忆后突发 = 重大事件 |

### 25.4 图遍历 API

| 方法 | 说明 | 用途 |
|------|------|------|
| `traverse(startId, type, maxDepth)` | BFS 沿指定关系类型游走 | 记忆穿梭 |
| `traverse(startId, type, maxDepth, minWeight)` | BFS 低权重剪枝游走 | 扩散激活 |
| `traceCausalChain(memoryId, upstream)` | 因果链追溯（前因/后果） | 归因分析 |
| `findSimilar(memoryId, topK)` | 按相似边权重排序 | 相关推荐 |
| `getTimeline(gameDay)` | 某天时间线（按时间戳排序） | 日回顾 |
| `rankEdges(edges, queryContext, bayes)` | 贝叶斯对边关联节点重排 | 上下文排序 |

### 25.5 与 MemoryManager 的关系

```
MemoryManager              MemoryGraph
  ├── day_*.mem (切片)      ├── memory_graph.json (关系)
  ├── 高光片段存储           ├── 零侵入（不修改 MemoryEntry）
  └── 负责读写内容           └── 只记录节点间关系
```

MemoryGraph 通过 `setMemoryGraph()` 注入 MemoryManager，`enabled=false` 时完全降级。

### 25.6 Hebbian 强化 (Phase 1)

每次反射执行成功后，`ConditionedReflex.executeReflex()` 在贝叶斯更新后调用 `memoryGraph.reinforcePath(nodeIds, delta)`，强化反射相关记忆节点间的边权重。

```
反射执行成功 → Bayesian 更新
                ↓
    findNodeIdsByReflex(reflexId)  ← reflexToNodes 前缀索引
                ↓
      reinforcePath(nodeIds, delta)
                ↓
      edgeDelta = delta × 0.1
      ├── 已有边 → updateWeight(+0.005 on success / -0.003 on failure)
      └── 无此边 → addEdge(initWeight = 0.3 + delta × 0.05)
```

关键设计：

| 机制 | 说明 |
|------|------|
| `reflexToNodes` 索引 | `rebuildReflexToNodesIndex(MemoryManager)` 在 `setMemoryGraph()` 后自动重建 |
| 前缀匹配 | `"mine_iron"` 前缀匹配 `"mine"` — 支持一对多反射→记忆映射 |
| `updateWeight` | 夹具 `[0.1, 1.0]`，下限防止边因频繁失败消失 |
| 衰减因子 | `edgeDelta = delta × 0.1` — 每条边增量为主增量 1/10，大量重复才能显著变化 (Heisenberg 原则) |

### 25.7 扩散激活 (Phase 2)

当 MetaScheduler 无法从已有反射路径生成候选时，回退到记忆图的扩散激活，通过 BFS 遍历低权重剪枝的相邻节点，提取关联反射作为备选。

```
MetaScheduler.executeLoop() 候选生成:
  路径 1: 优先级精确匹配当前反射 (weight 1.0)
  路径 2: 最近执行反射 (weight 0.8)        ← 三路并行
  路径 3: 扩散激活 (weight × 0.6)         ←
```

扩散参数：

| 参数 | 默认值 | 说明 |
|------|:------:|------|
| `maxDepth` | 2 | 激活传播深度 |
| `minWeight` | 0.5 | 只遍历权重大于该阈值的边 |
| 衰减因子 | 0.6 | 每层激活值 × 0.6 |
| 种子节点 | 最近 3 条记忆 | 或全部（少于 3 条时） |

所有候选路径经玻尔兹曼选择竞争，扩散激活路径因 0.6 衰减系数天然处于较低优先级。

### 25.8 骨骼导出/导入 (Phase 3)

骨骼 (Skeleton) 是 MemoryGraph 的轻量摘要，仅保留高频连接节点，用于跨 bot 迁移记忆关系：

```
exportSkeleton()
  ├── incident edge count ≥ 2 (weight ≥ 0.5) → 高频节点
  ├── deinstanceLabel() 擦除坐标/时间戳
  └── 输出: skeleton_nodes + skeleton_edges

importSkeleton(skeleton)
  ├── 非重置合并: max(existingWeight, importedWeight)
  └── 标签冲突: 以 label.hashCode() 为稳定性 ID (sid = "skel_" + hashCode)
```

集成 ReflexPackManager：

| 方法 | 行为 |
|------|------|
| `exportPack()` | 写入 `memory_graph` 键 |
| `importPack()` | 调 `memoryGraph.importSkeleton(skeleton)` 合并 |

骨骼操作不调用 LLM，纯本地 JSON 合并。`deinstanceLabel` 擦除 ` at (x,y,z)`、`HH:mm:ss`、`YYYY-MM-DD`、`[tags]` 等实例化后缀。

---

## 26. SurvivalChallengeMonitor — 生存挑战监控

SurvivalChallengeMonitor 是挑战系统的核心监控器，用于盲测对比新旧评分系统（ReflexSatisfaction 五维评分 vs 原始乘积公式）。

### 26.1 数据模型

```java
class SurvivalChallengeMonitor {
    static final ConcurrentHashMap<UUID, AtomicInteger> llmCounters;   // LLM 调用计数器
    static final ConcurrentHashMap<UUID, AtomicInteger> deathCounters; // 死亡计数器
    static int endDay;                                                  // 挑战结束天数
}
```

### 26.2 生命周期

```
/ai challenge start [days]
  ├── 重置 llmCounters/deathCounters
  ├── 设置 endDay = currentDay + days
  ├── 生成 LegacyBot (useLegacyScoring=true, 800格远)
  └── 生成 NewBot (useLegacyScoring=false, 600格远)

BotInstance.tick() 内钩子:
  ├── 检测死亡 → recordDeath(botId)
  └── day 边界变化 → printDailySnapshot(day, bots)

MetaScheduler.executeCortexLLM() 内钩子:
  └── recordLLMCall(botId)

/ai challenge stop
  ├── 杀死两 Bot
  ├── printFinalReport(bots) — 计分对比
  └── 重置监控状态
```

### 26.3 每日快照格式 (printDailySnapshot)

**Compact 行**（控制台，单行）：
```
LegacyBot[HP:12/20 饿:8 🪓:0 ⛏:0 💎:0 💀:1 🤖:0]
NewBot[HP:18/20 饿:6 🪓:1 ⛏:5 💎:1 💀:0 🤖:2]
```

**Detailed 行**（日志文件，含细分维度）：
```
[Challenge] Day 3 — LegacyBot: HP=12/20, hunger=8, wood=0, iron=0, diamond=0, deaths=1, llmCalls=0
[Challenge] Day 3 — NewBot:     HP=18/20, hunger=6, wood=1, iron=5, diamond=1, deaths=0, llmCalls=2
```

### 26.4 最终报告与计分规则 (printFinalReport)

```java
score = ironCount × 10 + diamondCount × 50 - deathCount × 100
```

最终报告格式：
```
=== Survival Challenge Report ===
              LegacyBot     NewBot
HP             12/20         18/20
Hunger         8              6
Wood           0              1
Iron           0              5
Diamond        0              1
Deaths         1              0
LLM Calls      0              2
-----------------------------------
Score          -100           100
Winner: NewBot
```

计分规则设计：
| 指标 | 单位分值 | 理由 |
|------|:-------:|------|
| 铁锭 | +10 | 基础资源产出，反映日常效率 |
| 钻石 | +50 | 高价值资源，反映高阶能力 |
| 死亡 | -100 | 严重扣分，反映生存能力 |
| 其他 | 0 | 不计分，仅展示 |
