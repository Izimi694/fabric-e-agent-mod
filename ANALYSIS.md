# E-Agent 项目可行性分析报告

> 分析日期: 2026-06-17
> 基准: AGENTS.md / ARCHITECTURE.md / DEVELOPMENT.md / THEORY.md + 源代码

---

## 一、总体可行性判断：✅ 高可行

项目采用"反射固化 + LLM 仅填空"的核心策略，在 **运行时间 ∞ vs 预算有限** 这一核心矛盾下给出了自洽且工程上可行的解决方案。337 个单元测试全部通过，~100 个 Java 源文件覆盖了从 6 层拦截器到繁衍系统的完整架构，代码质量和文档成熟度远超一般 Minecraft 模组。

**可行性的三个支柱：**

| 支柱 | 说明 | 证据 |
|:---:|------|------|
| 成本收敛 | LLM 只填 7 种 JSON 模板，反射命中后成本为 0 | 挂机 1h=0 API，活跃 1h=~4-8 次 |
| 行为分层 | L0-L6 逐层拦截，每层拦截自己能力内的刺激 | MetaScheduler.tick() 完整实现了五阶段决策 + 升降级 |
| 本地学习 | 观察学习 → 条件反射 → 贝叶斯蒸馏，闭环自进化 | CorrelationDetector + ConditionedReflex + BayesianModule |

---

## 二、当前缺陷（按严重性排序）

### 🔴 P0: 域执行器架构只有 2/6 实现（Stage 1-3 实际上只完成了 1/3）

**现象：** `DomainCommand` sealed 接口声明了 6 种子类型（Break / Motion / Place / Craft / Combat / Inventory），但只有 **DigExecutor** 和 **MotionExecutor** 被实现。其余 4 种 Command 记录（PlaceCommand、CraftCommand、CombatCommand、InventoryCommand）通过 `DomainRouter.dispatch()` 分发时，因找不到对应的 Executor，**静默返回 null**（走 `LOGGER.warn` → `return CompletableFuture.completedFuture(null)`）。

**风险：**
- 不是 `UnsupportedOperationException`（文档说应该抛，实际没有），而是静默失败——调用方不知道命令没被执行
- `sealed` 关键字制造了"接口已封闭完整"的假象，实际 66% 的 subtype 没有对应实现
- 未来添加新命令时，如果没有注意注册 Executor，会得到不可见的空结果

**修复方向：**
1. 在 `DomainRouter.dispatch()` 中，对找不到 Executor 的命令返回 `CompletableFuture.failedFuture(new UnsupportedOperationException(...))` 而非静默 null
2. 在 Stage 4 完成前，确保所有未实现的 Executor 抛出清晰异常，**不要静默失败**
3. 或者将未实现的 Command 从 `sealed` 中暂时移除，直到有对应的 Executor

### 🟠 P1: MinecraftActionAdapter 绕过域执行器架构

**现象：** `MinecraftActionAdapter` 声称已委派 `dig()` → DomainRouter → DigExecutor、`moveTo()` → MotionExecutor，但以下动作 **仍直接在 Adapter 中实现，不经过 DomainRouter**：

| 动作 | 位置 | 路由方式 |
|:----:|:----:|:--------:|
| `attack()` | MinecraftActionAdapter 行 95 | 直接实现 |
| `placeBlock()` | MinecraftActionAdapter 行 121 | 直接实现 |
| `craft()` | MinecraftActionAdapter 行 373 | 直接实现（含完整的 6 子方法） |
| `useItem()` | MinecraftActionAdapter 行 143 | 直接实现 |
| `equipItem()` | MinecraftActionAdapter 行 156 | 直接实现 |
| `openBlock( )` | MinecraftActionAdapter 行 178 | 直接实现 |
| `closeWindow()` | MinecraftActionAdapter 行 190 | 直接实现 |
| `clickSlot()` | MinecraftActionAdapter 行 202 | 直接实现 |
| `flee()` / `retreat()` | MinecraftActionAdapter 行 257 / 287 | 直接 velocity 操作 |

**问题：** `BasicActionAdapter` 接口甚至暴露了 `getDigExecutor()` / `getMotionExecutor()` —— 接口不该暴露内部组件实例。域架构应当统一所有动作的执行路由，而不是挖矿/移动走新路、其余动作走旧路。

**修复方向：**
1. 制定明确的迁移路线：哪些动作在 Stage 4 从 Adapter 剥离到 DomainExecutor
2. 至少为 CombatCommand / CraftCommand / PlaceCommand 实现 Executor 的基本骨架（哪怕是调用 Adapter 的现有方法）
3. 从 `BasicActionAdapter` 接口移除 `getDigExecutor()` / `getMotionExecutor()` 暴露

### 🟡 P2: flee/retreat 使用了 velocity 直接操作而非 NavigationController

**现象：** `flee()` 和 `retreat()` 使用 `bot.setVelocity()` + `bot.jump()` 进行回避，完全绕过了 `GreedyNavigator` + `NavigationController` 的寻路栈。结果是：
- 逃跑路径会上墙、卡障碍物
- 不与 `motionExecutor.moveTo()` 的路径规划集成
- 不受 `TemporalScaler` 的速度调制影响

**影响：** 生存场景（L0/L1 触发逃跑）是最高优先级行为，但它们的运动实现反而是最粗糙的。

**修复方向：** `flee()` 应该计算一个远离威胁的目标点，然后调用 `moveTo(target)` 走 NavigationController。直接 velocity 操作只应作为紧急帧回退（flee 的第一步击退，后续还是要寻路）。

### 🟡 P3: 文档与代码不同步 — "UnsupportedOperationException" 不存在

**现象：** `ARCHITECTURE.md` 多处提到未实现的 Executor 会抛出 `UnsupportedOperationException`，但 `grep` 搜索整个项目代码库 **返回 0 个结果**。

**影响：** 文档是读者的第一参考来源，文档与代码的偏差会浪费大量调试时间。

| 文档声明 | 实际行为 |
|---------|---------|
| "PlaceCommand/CraftCommand/CombatCommand/InventoryCommand 为 Stage 4 占位，当前仅抛出 UnsupportedOperationException" | 不抛异常，Dispather 返回静默 null |
| "adapter.dig() → DomainRouter.dispatch(BreakCommand) → DigExecutor.execute()" | 只有 dig 和 moveTo/lookAt/jump 走这条路 |
| "cost model: 挂机1h=0 API" | EVALUATION_BATCH 每 30 分钟触发一次 LLM，即使是挂机状态 |

### 🟢 P4: L0 触发模型与 tick 驱动循环的关系未文档化

**现象：** `InnateReflexRegistry`（L0）被描述为"熔岩/虚空/HP<2"触发，但 `MetaScheduler.tick()` 是唯一的主循环。L0 是如何在 tick 之前或之外被触发的？是 poll 在 tick 内检查，还是事件驱动中断？从代码看 L0 反射在 `executeHabitLayerWithGating()` 中被扫描，这意味着 **即使掉落虚空也要等到下一个 tick 才能响应**，这与 L0 "生存本能"的实时性要求可能冲突（一个 tick=50ms，虚空掉落期间可能致命）。

### 🟢 P5: 结构性问题 — 囊泡超级池硬编码约束与反射配方档案的维护

**现象：** 囊泡超级池约束 `chain_max_length=5` 是硬编码在 `SharedPoolConfig` 中的。但反射配方档案（`reflex_recipes.json`）是人工维护的 JSON 文件，当新的反射被 LLM 创建（REFLEX_CREATE 钩子）时，系统不会自动为其生成目标向量和合取条件字段。这意味着：

- LLM 创建的反射缺乏 `targetVector` / `require` 字段 → CognitiveControl 的余弦匹配降级（无配方时只能使用默认权重）
- `neuroDynamics.computeAttackInhibition()` / `computeFlightExcitation()` 依赖 `serotonin`、`failureCount`、`confidence` 等参数——但这些参数对新创建的 trial 反射都处于初始值，导致 CognitiveControl 对新生反射的调制效果很弱

**修复方向：** REFLEX_CREATE 钩子弹化时，应自动为新建反射生成默认配方向量（基于其 action 类型推断）。

---

## 三、边界模糊问题

### 🔷 B1: ConditionedReflex 的双重层归属

`§1（运行时）` 把 ConditionedReflex 放在 **L2（条件反射层）**，但 `§12/§14（硬编码分级）` 把它放在 **L4（可学习可遗传的反射）**。同一个组件在两个分类体系中有不同层级归属。

**影响：** 当讨论"L2 拦截"时，指的到底是 ConditionedReflex 还是 MetaScheduler 调度层？在实际代码中，`ConditionedReflex.scanAndTrigger()` 的调用位置在 `executeHabitLayerWithGating()` 中（L2-L4 聚合拦截），但反射本身的持久化文件却是"可遗传的 L4"。团队内讨论设计时需要指定是"运行时拦截层"还是"硬编码分级层"。

### 🔷 B2: BasicActionAdapter 的"原子动作"vs"复合动作"边界

`BasicActionAdapter` 有 14 个原子动作 + 8 个复合动作。但边界不明确：

| 原子动作 | 复合动作 |
|:--------:|:--------:|
| moveTo, lookAt, dig, attack, placeBlock, useItem, equipItem, openBlock, closeWindow, clickSlot, chat, jump, sprint, dropItem | craft, flee, eat, retreat, avoidLava, seekShelter, collectItem, sneak |

为什么 `sneak` 是复合动作而 `jump` 是原子动作？为什么 `craft`（调用了 findCraftingRecipe + openCraftingInterface + placeItemsInGrid + collectCraftResult + cleanupCraft 共 5 个子方法）被视为一个"复合动作"而不是域命令 `CraftCommand` 的执行体？这种分类是随意而非系统的。

### 🔷 B3: 域架构的分层归属 — DomainCommand 属于哪一层？

文档组件归属表（§14）中没有列出 `DomainCommand`、`DomainExecutor`、`DomainRouter` 属于哪一层。它们被放在 `brainstem/domain/` 包下，但：

- `DigExecutor` 对应 Break 领域 — 是 L4（条件反射执行）还是 L2（调度层）？
- `MotionExecutor` 对应 Motion 领域 — 是 L3（导航）还是 L2（调度）？

**效果：** 域架构目前处于"不知道属于哪一层"的灰色地带，既没有进入架构分层表，也没有明确的升级/降级路径。

### 🔷 B4: 激素系统状态（连续）与认知控制阈值（离散）的转换不确定性

文档正确地指出了"连续信号存储 vs 离散决策边界"的原则（§10 / THEORY 洞察 7），但在实际实现中，以下转换路径没有文档化：

- `HormonalSystem` 的 NE 连续值 (0-1) → `CognitiveControl` 的 NE 阈值 0.5（区分低/高威胁）— 为什么是 0.5？
- `CognitiveControl` 的 GABA 推导 `serotonin×0.5 + failures×0.05 + ...` → 此连续值如何转换为候选权重的离散乘法因子（`candidate.weight *= (1.0 - attackInhibition)`）？

这些常数缺乏理论推导或实验依据，目前是"硬编码直觉值"。

### 🔷 B5: 记忆关系图（MemoryGraph）的显著性门控与贝叶斯收敛边界

`MemoryGraph.inferEdges()` 的显著性门控 threshold=0.6。同时 `BayesianModule.isConverged()` 使用 1/e ≈ 0.3679。这两个阈值的差异没有理论解释：

| 阈值 | 值 | 用途 |
|:----:|:--:|:----:|
| 显著性门控 | 0.6 | 边推断过滤 |
| 收敛阈值 | 0.3679 (1/e) | 贝叶斯后验收敛判断 |
| 探索阈值 | 0.37 | 探索/利用切割 |
| 安全距离调制 | base + \|modulation\| | NE 调制 |

三个 e-based 阈值（0.37/0.368/0.632）与一个任意值（0.6）共存，0.6 的来源是什么？

---

## 四、综合建议

### 短期（下一轮开发）

1. **修复 DomainRouter 静默失败**：对未注册 Executor 的 Command 抛 `UnsupportedOperationException`，将静默 null 改为明确失败
2. **文档同步**：更新 `ARCHITECTURE.md` 中域执行器的实际实现状态，标注哪些 Executor 已完成、哪些是占位
3. **为 Place/Craft/Combat 实现 Executor 骨架**：当前 Adapter 已有完整实现，将其简单包装到对应 Executor 中即可（委派调用）

### 中期

4. **完成域架构迁移**：将 Adapter 中的所有原子动作统一经过 `DomainRouter` → 对应 Executor
5. **定义 DomainExecutor 的架构分层归属**：在 §14 组件归属表中增加 domain/ 包的层级
6. **REFLEX_CREATE 钩子后自动生成反射配方**：确保 CognitiveControl 对新反射有基线调制能力
7. **flee/retreat 接入 NavigationController**

### 长期

8. **制定阈值参数实验方法论**：0.6 显著性门控、0.5 NE 威胁阈值等核心常数需要更系统的推导或实验支持
9. **消除 BasicActionAdapter 的"原子/复合"随意分类**：将所有动作统一为 DomainCommand → Executor 模型，删除旧的分类

---

## 五、结论

E-Agent 是一个在技术深度、架构完整性和工程实现上都极为出色的 Minecraft AI Mod 项目。其"反射固化 + 成本收敛"的核心策略是解决"无限运行时间 vs 有限预算"这一核心矛盾的 **正确路径**。

当前最大的问题是 **域执行器架构（2/6 完成）与 MinecraftActionAdapter（6/6 直接实现）的双轨并行**。这造成了两套执行路径共存，使模块边界模糊、失败模式不确定。先解决 P0（静默失败）+ P3（文档同步），再逐步推进完整迁移，项目会进入一个非常健康的状态。
