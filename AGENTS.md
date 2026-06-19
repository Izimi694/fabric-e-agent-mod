# E-Agent — Agent 指南

> **第一原则**：以降低成本作为主要战略方向，以确定模块边界作为主要降低成本的方法。
> **实现形式**：LLM 只填空，系统只出题。loop 微分无限问题，刷新消除上下文污染。

---

## 1. 核心矛盾

**运行时间 ∞ vs 成本有限。**

所有设计决策都是在这个矛盾下的权衡。边界模糊 → 互相调用 → 本应本地的事泄露到 LLM → 成本爆炸。边界清晰 → 每个模块只做自己最便宜能做到的事 → LLM 只在边界终端出现一次。

---

## 2. 核心抽象 (OS 内核视角)

| 概念 | 工程实现 |
|------|---------|
| 进程/线程 | 反射 (ConditionedReflex) |
| 应用程序 | LLM |
| 系统调用 | 12 原子动作 (BasicActionAdapter) |
| 调度器 | MetaScheduler |
| 文件系统 | 反射库 (`conditioned/*.json`) |
| 软件包管理 | 反射包 V1 (`reflex_packs/*.json`) + 玩法包 V2 (`reflex_packs/*.json` v2) |
| 热/冷缓存 | stw/ltb 双权重 |
| 动态优先级 | 激素系统 (HormonalSystem) |
| 进程 fork | 繁衍 / 三规则继承 |
| 进程依赖图 | DAG 任务依赖图 (TaskDAG) |
| 进程执行链 | 时序/执行链表 (ReflexChain) |
| 参数传递 | 参数绑定 (ParameterBinder) |
| 进程门控 | 边界条件检查 (precondition guard) |
| 认知网图/记忆关系 | 记忆关系图 (MemoryGraph) |
| 认知地图 | 图式压缩 (Schema Compression) — WorkingMemoryPool 轨迹聚类 |
| 抑制控制 | 抑制控制 (InhibitoryControl + CognitiveControl) |
| 调质调谐 | 4 维状态向量 (NE/DA/5-HT/ACh + 导出 GABA/Glu) |

---

## 3. 五层洞察 (从零推导)

| 洞察 | 核心公式 | 工程落点 |
|------|---------|---------|
| 记忆 ≠ 技能 | 陈述性 vs 程序性 | MemoryManager vs ConditionedReflex |
| 反射链 = 参数化步骤 | 有限状态机 | ConditionedReflex atoms |
| 贝叶斯 = 被动先验编码 | P(s\|f) ∝ P(f\|s)×P(s) ([事后统计，非实时门控](#2-核心抽象-os-内核视角)) | BayesianModule |
| 激素 = 相对优先级 | 竞争性调度 | MotivationEngine boltzmann |
| 神经递质 = 组合调制 | 情境开关 + 合取条件 + 余弦匹配 (NE/DA/5-HT/ACh) | HormonalSystem → CognitiveControl |
| 图式压缩 = 离线抽象 | 连续轨迹 → 离散聚类 → 持久图式 | WorkingMemoryPool 网格聚类 + MemoryGraph 持久化 |
| e = 自然切割 | 37% 探索 / 63% 利用 | 探索阈值/切换缓冲/收敛阈值 |

**统一表述**：并发不是并行，是极速串行。复杂不是高维，是压扁后用 e 切割。轨迹不是记忆，是压缩后才有意义。

---

## 4. 工程约定

### 矛盾 A：响应速度 vs 行为质量

| 策略 | 说明 |
|------|------|
| 分层原则 | 每层拦截自己能力内的刺激 |
| 优先级原则 | 成本越低、响应越快，优先级越高 |
| 降级优先 | API 挂了降级本地，不停摆 |
| 原子性原则 | 每反射只做一件事 |
| 环境裁决原则 | 不做预判 — `jump()` 不查 `isOnGround`，`attack()` 不查 `instanceof`。执行动作，让环境反馈裁决成败 |

### 矛盾 B：个体差异 vs 群体稳定

| 策略 | 说明 |
|------|------|
| 随机初始化 | α/β 从正态分布采样 |
| 三规则继承 | 平均+减半+突变 → 中心极限定理→正态 |

### 矛盾 C：学习速度 vs 遗忘成本

| 策略 | 说明 |
|------|------|
| 双权重 (stw/ltb) | 快变+慢变 |
| 休眠不删除 | successRate < 30% → dormant |
| 失败分类 | 确定性永久跳过 vs 概率性重试 |

---

## 5. 一句话总结

> **安全优先于任务，任务优先于习惯，习惯优先于社交，社交优先于记忆。**
> **成本越低越快，越快越优先。**
> **反射是核心，记忆是参考。**
> **泛化共享，特例独占。**
> **挂了要降级，不能停摆。**

---

## 6. 准入三原则

新功能必须同时满足：
1. **成本可收敛** — 不能无限调 LLM
2. **有层归属** — 必须落在 L0-L6 某一层或骨架
3. **可物质化** — 必须落在已有数据结构上

---

## 7. 贝叶斯唯一仲裁者与环境可控性

贝叶斯模块是系统与环境的唯一统计接口。绕过贝叶斯 = 破坏学习能力。

| 概念 | 定义 | 工程实现 |
|------|------|---------|
| 环境可控性指数 | `1 / (1 + variance/varianceScale)` × envChangePenalty，variance = confidence×(1-confidence) | GatingArbiter.computeControllability() |
| 方差低 | 后验集中 → 行动→结果确定 | L1/L3 需贝叶斯验证后固化 |
| 方差高 | 后验分散 → 行动→结果随机 | L1/L3 允许直接生效 |
| 阈值动态 | stress↑ → 执行阈值↑; curiosity↑ → 探索阈值↓ | HormonalSystem 调节 |

**分层仲裁 (Go/NoGo 模型)**：L1/L3 不默认生效 → 先查 GatingArbiter（封装可空性指数计算的门控决策器）→ 低于阈值才允许直接固化。门控逻辑已从 BayesianModule 分离，保持统计与决策的模块边界。

---

## 8. 麦穗策略

| 维度 | 规则 |
|------|------|
| 探索概率 | `max(0, 0.37 - reflex.confidence)` — 置信度越高窗口越短 |
| 停止阈值 | 由 curiosity 激素动态调节 |
| 计数单位 | 每反射独立，非全局常量 |
| 统计依据 | 贝叶斯后验置信度，非执行次数 |

---

## 9. 四类共享池

| 池 | 约束 | 工程实现 |
|----|:----:|---------|
| 囊泡超级池 | 反射链长度 ≤ 5 | chain_max_length |
| 工作记忆绑定池 | 贝叶斯候选集 ≤ 5 | bayesian_candidate_limit |
| 跨脑共享子空间 | 共享先验比例 10-30% | shared_prior_ratio |
| 归一化网络池 | 总驱力 = 1.0 | 硬编码归一化 |

四池约束独立，不交叉合并。生物对应关系见 [THEORY.md](./THEORY.md)。

---

## 10. 连续信号 + 离散决策

- **信号存储和计算**用连续值 (0-1)
- **决策边界**用离散阈值，阈值由激素动态调节
- （生物类比：神经元速率编码连续，门控阈值离散）

| 连续信号 | 离散决策点 | 阈值调节 |
|---------|-----------|---------|
| 贝叶斯后验均值 > ? | 执行该反射 | stress 高时阈值↑ |
| 置信度方差 < ? | 标记为 deterministic | curiosity 高时↓ |
| 探索进度 > ? | 停止探索 | curiosity 调低阈值 |
| 环境可控性 > ? | L1/L3 需验证 | stress 高时阈值↓ |
| 轨迹热力 > ? | 压缩为图式 | buffer 满/心跳自动触发 |

---

## 11. Simon IDC 三阶段决策框架

| 阶段 | 认知需求 | 你的系统对应 |
|:----:|---------|-------------|
| I — 认识问题 | "我面对的是什么问题？" | 情境识别 + 记忆加载 |
| D — 设计对策 | "我有哪些选择？" | 激素粗筛 + 候选集生成 (≤5) |
| C — 选择对策 | "选哪一个？" | 贝叶斯精筛 + 玻尔兹曼抉择 |
| 填空 | "这一步的具体参数？" | ParameterBinding 填入参数槽位 |

**价值-选择分离**：价值计算 (贝叶斯后验) 和价值选择 (ACC 冲突仲裁) 在两个独立阶段完成，参见 Grabenhorst & Rolls 2011。

---

## 12. Related Work

| 维度 | 已有工作 | 本系统做法 |
|------|---------|-----------|
| 行为分层 | Brooks Subsumption Architecture | 6 层拦截器 + 按成本分层 |
| 双权重学习 | TD(λ) / Dual-process RL | 简化工程实现 + 休眠 |
| 激素调节 | EM-BDI / Homeostatic regulation | 绑定 Perspective 选择 |
| LLM + 技能复用 | Voyager / Generative Agents | 反射固化，成本收敛到 0 |
| 神经调质组合 | AES / Emotion-Modulated Architectures | 4 维向量余弦匹配 + 反射配方 |
| 认知控制 | MATE (2026) / EvoEmo (2025) | InhibitoryControl 硬门 + CognitiveControl 连续调制 |
| 客户端 Bot 架构 | [Mineflayer](https://github.com/PrismarineJS/mineflayer) (MIT) | 服务端 mod，controlState 意图分离、挖掘协议三态、复活延迟 1500ms |
| 上下文预算 + 结构化失败 | [AutoSci](https://github.com/skyllwt/AutoSci) (北京大学 DAIR Lab, MIT) | ContextBudget token 预算 + failure_reasons JSON 链 |
| 离线重放 + 图式形成 | 海马体 SWR 20x 快放 (Wilson & McNaughton 1994) | WorkingMemoryPool 轨迹缓冲 → 网格聚类 → MemoryGraph 持久化 → LLM 读取产出 LandscapePatch |

---

## 13. 参照文档

| 文件 | 内容 |
|------|------|
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 完整技术架构 (拦截器/调度器/组件/模板填空) |
| [DEVELOPMENT.md](./DEVELOPMENT.md) | 开发状态/路线图/bug/构建/指令/测试 |
| [THEORY.md](./THEORY.md) | 理论背景、从零推导、论文引用 |
| [INTERNALIZATION.md](./INTERNALIZATION.md) | 抽象概念内化检查清单 |

PS：记得提前边界条件和前卫语句，不会擅自修改不符合条件的参数，快速失败，快速报错。

---

## 14. 高管-下属协议 (E-Agent 2.0)

> LLM 是「景观雕塑家」，不是「司令官」。ActionSorter 是「最终决策者」，不是「指令执行器」。

### 通信边界

| 方向 | 内容 | 格式 | 举例 |
|------|------|------|------|
| 下属→高管 | 统计量 + 异常标志 | `PerformanceReport` (数字/布尔) | `{"oreVeinsNearby": 2, "isUnderAttack": true}` |
| 高管→下属 | 标量乘数 | `LandscapePatch` (targetType + salienceBoost, no coords) | `{"attractor": {"type": "IRON_ORE", "boost": 0.3}}` |
| 下属↔下属 | 物理信号 | `DomainRouter` 局部总线 (scalar only) | `movementIntensity=0.8, isInCombatRange=true` |

### 绝对禁止的操作

| 禁止事项 | 谁禁止 | 为什么 |
|----------|--------|--------|
| LLM 输出坐标或 `moveTo(x,z)` | LLM | 坐标属 Executor 的能力圈，LLM 不该知道 |
| LLM 指定动作序列 `["dig","move","look"]` | LLM | ActionSorter 和 Executor CPG 负责节奏 |
| Executor 调用 LLM | Executor | Executor 不该知道 LLM 存在 |
| Executor 绕过 ActionSorter 自行决定优先级 | Executor | 优先级是 AffordanceRouter 的职责 |
| ActionSorter 输出精确速度/方向值 | ActionSorter | 精确值是 Executor CPG 内部细节 |
| PerformanceReport 包含行动记录日志 | 感知层 | 报告只含统计量和标志，不含历史动作 |

### 错误追溯模式

当出现异常行为时，按以下顺序检查数据契约边界：

```
1. PerformanceReport → ActionSorter    : 感知层是否输出了错误 flag？
2. ActionSorter → BlendedAction        : softmax 权重是否正确？
3. AffordanceRouter → 排序             : 优先级的 offset 是否正确？
4. DomainRouter 总线 → Executor        : 域间信号是否被错误路由？
5. Executor CPG → 动作输出             : 节奏/相位是否正确？
```

### 成本层级 (越低越快，越快越优先)

| 层级 | 组件 | 每 tick 成本 | 触发频率 |
|------|------|:----------:|:--------:|
| L0 | DomainRouter 局部总线 | 纳秒级 | 每 tick |
| L1 | Executor CPG (Motion/Dig/Combat) | 微秒级 | 每 tick |
| L2 | ActionSorter + SalienceMap | 微秒级 | 每 tick |
| L3 | WorkingMemoryPool (惯性 + 图式压缩) | 微秒级 | 每 tick |
| L4 | AffordanceRouter 门控 | 微秒级 | 按需 |
| L5 | 旧 L0-L5 反射层 | 毫秒级 | 按需 |
| L6 | LLM | ~秒级 | ~10 分钟/次 |

### 14a. 数值-标识 物理隔离铁律

> E-Agent = 微积分（连续流）+ 标识（离散锚点）
> 当前动作 = ∫[ 物理惯性 C + 激素梯度 f(t) + 标识激励 g(label) ] dt

| 组件 | 只能处理 | 绝对不能处理 |
|------|---------|-------------|
| ActionSorter / 激素系统 / WorkingMemoryPool (轨迹寄存器) / DomainRouter / Executor CPG | 连续数值 (float 权重, double 坐标偏移, int Tick) | 不能解析 String 标签, 不能判断 category 含义 |
| LLM / SalienceMap / MemoryGraph / Schema / PersonaProfile / ConditionEvaluator | 离散标识 (String 标签, Enum 类别, JSON Schema, int 优先级等级) | 不能输出 float 动作权重, 不能生成 BlockPos |

违反后果：
- `ActionSorter` 中出现 `if (label.equals("DIAMOND"))` → 中层读标识，越界
- `LLM` 输出 `{"moveTo": [x,y,z]}` → 高管写微积分边界值，越界
- `SalienceMap` 直接修改 `float` 动作权重 → 标识层写连续值，越界
- **`WorkingMemoryPool` 解析 Schema.label → 标识层写连续值，越界** (trajectory buffer 属于微积分层, Schema 标签属于标识层, 两者交通过 MemoryGraph 间接传递)