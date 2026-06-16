# E-Agent — 开发 & 状态

> 本文件记录当前实施状态、路线图、已知问题。架构说明见 [ARCHITECTURE.md](./ARCHITECTURE.md)，设计原理见 [AGENTS.md](./AGENTS.md)。

---

## 1. 当前状态

```
项目阶段: ✅ Stage 1-3 domain executors + Stage 5 failure escalation + resource stress + dynamic temperature
```

### 完成情况

| Phase | 内容 | 状态 |
|:----:|------|:----:|
| P0-P6 | 基础系统 (IdleBrain/观察/条件反射/记忆/任务/计划) | ✅ |
| Phase 1 | MetaScheduler + BotContext | ✅ |
| Phase 2 | OneShotAlarmSystem | ✅ |
| Phase 2.5 | HormonalSystem | ✅ |
| Phase 3 | LocalTaskDecomposer | ✅ |
| Phase 4 | LocalChatHandler | ✅ |
| Phase 4.5 | MotivationEngine + LLM Gate | ✅ |
| Phase 5 | Urgency + Temporal scaling + CorrelationDetector | ✅ |
| Phase 6 | Multi-bot (BotManager + BotInstance) | ✅ |
| **Phase A** | **BayesianModule 三层存储 + 收敛判断(e)** | ✅ |
| **Phase B** | **错误蒸馏闭环 (ConditionedReflex ↔ BayesianModule)** | ✅ |
| **Phase C** | **三阶段记忆检索 + shouldExplore(e)** | ✅ |
| **Phase D** | **ChatSessionManager + loop 刷新** | ✅ |
| **Phase F** | **激素解耦 hormonal/ 包 + MetaScheduler 时间片(e)** | ✅ |
| **Phase E** | **TemplateManager 模板填空统一入口 + 全局冷却** | ✅ |
| **Phase RP** | **ReflexPackManager 反射包导入/导出/合并** | ✅ |
| **Phase 7** | **繁衍模块 (三规则继承 + trial-first 脚手架)** | ✅ |
| **Phase 1 (Decoupling)** | **api/ 接口定义 (WorldContext/BotContext/MetaState/BrainstemAPI/AmygdalaAPI/CortexAPI)** | ✅ |
| **Phase 2 (Decoupling)** | **MetaScheduler 重构 (BotContext+WorldContext+MetaState 注入), CorrelationDetector 解耦** | ✅ |
| **Phase 2.5 (Decoupling)** | **TemplateManagerTest (16 个测试), BayesianModuleTest (16 个测试)** | ✅ |
| **Phase G-M** | **ReflexChain DAG/死路检测/环境可控性/双向推理/共享池/参数绑定/L1/L3 门控/前置条件门控 (99 tests)** | ✅ |
| **Phase LLM (P0-P5)** | **四模板LLM调用重构: TemplateMatcher + CLARIFICATION/CHAT_RESPONSE + PersonaManager + MetaScheduler接入** | ✅ |
| **Phase MG** | **MemoryGraph 记忆关系图 (inferEdges/遍历/持久化/贝叶斯排重)** | ✅ |
| **Decoupling #7/#11/#12/#13** | **BotController/EAgent.LOGGER 全局替换/ConditionedReflex/MinecraftReflexEvaluator 解耦** | ✅ |
| **Phase W2** | **底层修正: 贝叶斯角色分离 + MemoryGraph 结构性缺陷** | ✅ |
| **Phase 2** | **神经递质向量系统: NeuroState + 4维 HormonalSystem + NeuroDynamics** | ✅ |
| **Phase 3** | **CognitiveControl 集成: 余弦匹配/阈值参数化/四门决策流水线** | ✅ |
| **Phase Playstyle Pack** | **Layer 1 玩法包: PlaystylePack V2 数据模型 + 5 预设包 + CLI** | ✅ |
| **Gating stateless** | **GatingArbiter 纯静态重构 + SocialObserver 清理 + OneShotAlarmSystem 直连 BayesianModule** | ✅ |
| **Memory RLU + Reflex decay** | **MemoryEntry.lastAccessedAt + MemoryManager.touchMemory()/flushDirtyTimestamps() + ConditionedReflex.computeDecayFactor()/updateLastAccessed()** | ✅ |
| **Persona formatHint/lock** | **PersonaProfile.formatHint 字段 + PersonaManager.personaLocked 锁 + /ai persona 设置锁 + playstyle 自动切换人格** | ✅ |
| **Playstyle Pack 扩展** | **jar→filesystem 内置包首次复制 + ReflexPackManager 子目录递归扫描** | ✅ |
| **死代码清理** | **移除 ~30 个未使用 import/field/method/annotation/参数，消除所有编译器警告** | ✅ |
| **Phase S0-S3** | **S0: 导航 bug 修复 + S1: ReflexSatisfaction 满意度评分 + S2: TemporalScaler 连续时间缩放 + S3: 领域自适应权重** | ✅ |
| **死代码清理 II** | **移除 ~20 个未使用 method (ConditionedReflex/MotivationEngine/LowLevelDispatcher/MetaState/ReflexSatisfaction)** | ✅ |
| **Phase S4-S5** | **riskScore/resourceScore 实现 + scanAndTrigger 集成** | ✅ |
| **Phase 3 (Challenge)** | **SurvivalChallengeMonitor + /ai challenge 命令** | ✅ |
| **Stage 1** | **domain/ 包接口: DomainCommand sealed + DomainExecutor + DomainRouter + FailureContext** | ✅ |
| **Stage 2** | **BreakCommand + DigExecutor 提取 (MinecraftActionAdapter.dig 委派)** | ✅ |
| **Stage 3** | **MotionCommand + MotionExecutor 提取 (moveTo/lookAt/jump/sprint/sneak)** | ✅ |
| **Stage 5** | **TASK_REPLAN 失败升级链 + TaskExecutor MAX_UNABLE_RETRIES (200→5) + onUnableExhausted** | ✅ |
| **Stage 5b** | **资源压力模型 resourceStress() + 动态温度 + 任务惯性 + ThreatInfo/getThreatsNearby** | ✅ |
| **TemporalScaler** | **computeTimeScale(h,pressure) + update(h,bot,ticks,pressure) + @Deprecated 旧 overload** | ✅ |


---

## 2. 实施路线图

```
Phase A (Bayesian地基) ── ✅
  ├── Phase B (错误蒸馏)    ── ConditionedReflex ↔ BayesianModule
  ├── Phase C (记忆检索)    ── MemoryManager 三阶段 + shouldExplore(e)
  ├── Phase D (ChatSession) ── 6条窗口 + 贝叶斯方向 + loop刷新
  └── Phase F (激素解耦)    ── hormonal/ 独立包 + MetaScheduler 时间片(e)
        ├── Phase E (模板填空) ── TemplateManager 统一 LLM 入口
        └── Phase RP (反射包)  ── ReflexPackManager 导出/导入/合并
Phase 7 (繁衍) ── 三规则继承 (平均+脚手架 trial-first + 突变) + 基因组存档

Phase G-M (全部已完成): ReflexChain/TaskDAG/Loop/死路/可控性/双向推理/共享池/门控/绑定

Phase LLM (四模板LLM调用重构):
  ├── P0 — MetaScheduler.executeCortexLLM() 接入 TemplateManager
  ├── P1 — CLARIFICATION 模板 + TemplateMatcher 路由
  ├── P2 — CHAT_RESPONSE 模板 + 独立预算
  ├── P3 — PersonaManager + 注入系统提示
  ├── P4 — DAG_TASK_PLAN 合并到 TASK_PLAN
  └── P5 — 删除 CHAT_DIRECTION 模板

Phase MG (MemoryGraph 记忆关系图):
  ├── MG1 — MemoryNode record + MemoryEdge mutable class + MemoryGraph 核心 (CRUD/save/load)
  ├── MG2 — inferEdges 显著性门控(computeSalience) + TEMPORAL/CAUSAL/SIMILARITY/CONTRAST 边推断
  ├── MG3 — 图遍历API (traverse/traceCausalChain/findSimilar/getTimeline/rankEdges)
  ├── MG4 — 持久化 memory_graph.json (+ version/no MissingNode)
  ├── MG5 — MemoryManager 集成(setMemoryGraph) + rebuildReflexToNodesIndex
  ├── MG6 — Hebbian 强化: reflexToNodes 索引 + findNodeIdsByReflex(前缀匹配) + reinforcePath
  │   └── ConditionedReflex 集成 (贝叶斯更新后调用)
  ├── MG7 — 扩散激活: traverse 重载(maxDepth/minWeight) + MetaScheduler 三路候选合并
  └── MG8 — 骨骼导出/导入: exportSkeleton + importSkeleton + ReflexPackManager 集成

Phase W2 (底层修正 — 数据层正确性):
  ├── W2.1 — BayesianModule 角色分离: `computeControllability()` 迁至新建 `GatingArbiter`
  └── W2.2 — MemoryGraph 结构性缺陷修复: 禁止自环、重复边去重、deinstanceLabel 哈希ID

Phase 2 (神经递质向量系统 — 架构升级):
  ├── P2.1 — NeuroState record {ne, da, serotonin, ach} + 余弦距离方法
  ├── P2.2 — HormonalSystem 扩展 4 维字段 + 事件触发更新 (保留旧别名)
  ├── P2.3 — NeuroDynamics 工具类: computeAttackInhibition/computeFlightExcitation (对应 GABA 刹车/Glu 油门)
  ├── P2.4 — 4 维各自衰减方程 (NE快/DA中/5-HT慢/ACh中) + 事件更新逻辑
  └── P2.5 — 测试适配 (兼容旧 API + 新增向量测试)

Phase 3 (CognitiveControl 集成 — 决策升级):
  ├── P3.1 — CognitiveControl 类: computeInhibition/modulateCandidates 余弦匹配 ✅
  ├── P3.2 — 反射配方档案 reflex_recipes.json (每个反射的目标4维向量) ✅
  ├── P3.3 — InhibitoryControl 阈值参数化 (仅向安全方向: effectiveThreshold = base + |modulation|) ✅
  ├── P3.4 — MetaScheduler 四门决策流水线集成 (硬门→候选→调制→玻尔兹曼) ✅
  └── P3.5 — MotivationEngine 弃用旧 curiosity, 改用 NE/DA 驱动 shouldExplore() ✅

Phase Playstyle Pack (Layer 1 玩法包 — 行为预设初始化):
  ├── PP.1 — PlaystylePack/HormonalPreset/IPlaystylePlugin 数据模型
  ├── PP.2 — BotParams.override() + HormonalSystem.applyPreset() + KnowledgeBase 分区
  ├── PP.3 — MotivationEngine.setPerspectiveWeights() 视角权重覆盖
  ├── PP.4 — ReflexPackManager V2 export/parseProfile/describePack
  ├── PP.5 — BotInstance.applyPlaystylePack() 统一入口链式调用
  ├── PP.6 — AICommand /ai playstyle load/list/export/current
  ├── PP.7 — 5 预设包 JSON: aggressive/explorer/social/cautious/builder
  └── PP.8 — 326 测试全部通过
```

### 本轮新增 (2026-06-15)

```
Phase S0-S3 (三件套决策系统 + 导航修复):
  ├── S0.1 — GreedyNavigator.isSingleBlockJump() 条件反转修复
  ├── S0.2 — NavigationController FORWARD_SPEED = 0.5f 常量 + 日志修复
  ├── S0.3 — 删除 stopNavigation() 无参空方法 和 isNavigating() 存根
  ├── S1.1 — ReflexSatisfaction 类: timeScore() 三段折线 S 形 / compute() 加权和
  ├── S1.2 — scanAndTrigger() 改用 ReflexSatisfaction 替代原始乘积
  ├── S2.1 — TemporalScaler.computeTimeScale(HormonalSystem) 连续 [0.5, 2.0] 缩放
  ├── S2.2 — timeScale 注入 scanAndTrigger() 满意度计算
  ├── S3.1 — DomainWeights record + 全视图权重配置 (SURVIVAL/TASK/SOCIAL/CURIOUS/CAUTIOUS)
  └── S3.2 — Perspective 管道: MetaScheduler → executeHabitLayerWithGating → scanAndTrigger

Phase Cleanup II (死代码清理):
  ├── CL.1 — ConditionedReflex: delete getRecentSuccessCount/getStoragePath/resetReflexWeights/getLastExecutedReflexId
  ├── CL.2 — MotivationEngine: delete shouldExplore() (被 wheatEarExplore 替代)
  ├── CL.3 — LowLevelDispatcher: delete executeHabitLayer() + CorrelationDetector 导入
  ├── CL.4 — MetaState: delete ~15 个未使用 getter/setter (biome/entity/block/chat/p3 等)
  ├── CL.5 — ReflexSatisfaction: delete 过载 compute(wTime,wSuccess) 简化 API
  └── CL.6 — NavigationController: delete stopNavigation() 无参空方法
```

### 本轮新增 (2026-06-16)

```
Phase S4-S5 (riskScore/resourceScore + 挑战系统):
  ├── S4.1 — ReflexSatisfaction.compute() 11 参数: 新增 riskScore, resourceScore
  ├── S4.2 — 9 参数向后兼容重载 (risk=1.0, resource=1.0)
  ├── S4.3 — computeWithScale() / computeForDomainWithScale() 8/9 参数重载
  ├── S4.4 — ConditionedReflex.scanAndTrigger() riskScore = clamp(1 - dangerLevel×(1-posterior))
  ├── S4.5 — scanAndTrigger() resourceScore = clamp(0.3 + hungerRatio×0.3 + posterior×0.4)
  ├── S4.6 — BotInstance.useLegacyScoring 字段 + getter/setter
  ├── S4.7 — clampScore() 工具方法 + legacy 分支 (乘积公式)
  ├── S5.1 — SurvivalChallengeMonitor 静态计数器 (llmCounters/deathCounters)
  ├── S5.2 — recordLLMCall/recordDeath/reset/startChallenge/stopChallenge
  ├── S5.3 — 每日快照 (compact 行 + detailed 行) + 最终报告 (计分规则)
  ├── S5.4 — BotInstance.tick 钩子: recordDeath + day 边界每日快照
  ├── S5.5 — MetaScheduler.executeCortexLLM 调 recordLLMCall
  ├── S5.6 — AICommand: /ai challenge start/stop/status
  └── S5.7 — 337 测试全部通过 (新增 Scenarios S8/S9 + DecisionQualityTest risk/resource)
```

### 本轮新增 (2026-06-17)

```
Stage 1-3 (领域执行器架构):
  ├── S1.1 — DomainCommand sealed interface + 3 子类型 (Break/Motion/Place)
  ├── S1.2 — DomainExecutor 基类 + DomainRouter dispatch/tickAll/failure collection
  ├── S1.3 — FailureContext record (reason/time/domain/recoveryHint)
  ├── S2.1 — BreakCommand + DigExecutor 提取 (MinecraftActionAdapter.dig 委派)
  ├── S2.2 — 删除 MinecraftActionAdapter 中的 dig/breakBlock 逻辑
  ├── S3.1 — MotionCommand (moveTo/lookAt/jump/sprint/sneak) + MotionExecutor
  └── S3.2 — MinecraftActionAdapter.moveTo/lookAt/jump/sprint/sneak 委派 MotionExecutor

Stage 5 (失败升级 → LLM 重规划):
  ├── S5.1 — MetaState failure escalation fields (failureEscalation/replanCount)
  ├── S5.2 — MetaScheduler.tick() hasFailureEscalation → executeReplan() → TASK_REPLAN
  ├── S5.3 — TemplateManager.TemplateType.TASK_REPLAN + executeReplan()
  ├── S5.4 — TaskExecutor MAX_UNABLE_RETRIES 200→5 + onUnableExhausted callback
  ├── S5.5 — EAgent 注册 onUnableExhausted → MetaScheduler.requestTaskFailureEscalation
  ├── S5.6 — BrainstemAPI.domainRouter() + WorldContext.setDomainRouter()
  └── S5.7 — replan limit=3, LLM冷却/预算 gate 共用

Resource Stress + 动态温度 + TemporalScaler 统一:
  ├── RS.1 — OneShotAlarmSystem.getThreatsNearby() → ThreatInfo[] (替换 boolean hasThreatMatchNearby)
  ├── RS.2 — computeSurvivalDrive() 统一 resourceStress(fillRatio, depletionRate, replenishDifficulty)
  ├── RS.3 — countFoodItems() 背包扫描 DataComponentTypes.FOOD
  ├── RS.4 — estimateHealthDepletionRate() 威胁类型加权 (creeper=1.0/skeleton=0.7/zombie=0.5/spider=0.5)
  ├── RS.5 — estimateHungerDepletionRate() + replenish difficulty estimators
  ├── RS.6 — DriveState.pressure() = survivalUrgency (单一环境压力信号)
  ├── RS.7 — motivationEngine.select() 动态温度 T = baseTemp × (1 − pressure × 0.85)
  ├── RS.8 — computeTaskDrive() 任务惯性 (成功放大/失败衰减)
  ├── RS.9 — computeCautiousDrive() + W_THREAT_CAUTIOUS=0.6 威胁距离因子
  ├── RS.10 — TemporalScaler.computeTimeScale(h, pressure) 统一过载
  ├── RS.11 — TemporalScaler.update(h, bot, ticks, pressure) 新过载
  ├── RS.12 — MetaScheduler.tick() 传 drives.pressure() → temporalScaler.update
  └── RS.13 — 删除 W_FEAR 常量 (被资源压力模型替代)
```

### 本轮新增 (2026-06-14)

```
Phase Gating stateless (GatingArbiter 纯静态):
  ├── GS.1 — GatingArbiter 移出 BayesianModule，改为纯 static 工具类
  ├── GS.2 — SocialObserver 删除 gatingArbiter 字段/setGatingArbiter/shouldDirectConsolidate/CONTROLLABILITY_GATE
  └── GS.3 — OneShotAlarmSystem 直连 BayesianModule，替换 gatingArbiter 字段

Phase RLU+Decay (记忆刷新 + 反射衰减):
  ├── RD.1 — MemoryEntry.lastAccessedAt + MemoryManager.touchMemory/flushDirtyTimestamps
  ├── RD.2 — getRecentMemories 改用 Math.max(timestamp, lastAccessedAt) 过滤
  ├── RD.3 — ConditionedReflex.computeDecayFactor(方案A): max(0.3, 1 - unusedHours×0.0003)
  ├── RD.4 — getTopCandidates 乘 decayFactor + executeReflex 调 updateLastAccessed
  └── RD.5 — 创建时设 lastAccessedAt (4处) + ReflexIO 反激活

Phase Persona Extension (角色系统扩展):
  ├── PE.1 — PersonaProfile.formatHint 字段 + PersonaManager 存储
  ├── PE.2 — MetaScheduler/buildTemplateContext 注入 CHAT_RESPONSE
  ├── PE.3 — PersonaManager.personaLocked 锁 + AICommand 设置
  └── PE.4 — PlaystylePack.persona 字段 + load 时自动切换（未锁时）

Phase Cleanup (死代码清理):
  ├── CL.1 — 移除 ~30 个未使用 import/field/method/local
  ├── CL.2 — 移除 3 个不必要的 @SuppressWarnings("unchecked")
   └── CL.3 — 移除 MemoryQuery 字段/引用/EAgent/LinkedHashSet/EXPLORE_THRESHOLD 等
```

### 本轮新增 (2026-06-16)

**Phase RF (重构 — 降低复杂度 & 错误处理)**

```
Phase RF (重构 — 降低复杂度 & 错误处理):
  ├── RF.0 — CraftSkill: 提取 findFirstCraftable(), 嵌套 5→2
  ├── RF.1 — KnowledgeBase: 提取泛型 getFromPlaystyleOrGlobal(), 4 重复模式消除
  ├── RF.1b — KnowledgeBase: fromMap 提取 parseTemplates(), 降低 CogC 18→10
  ├── RF.2 — BotController: 构造函数 12→8 参数, 加入 WorldContext 替换 4 个分离字段
  ├── RF.3 — MemoryGraph: 统一两个 traverse BFS, 提取私有 traverseBFS()
  ├── RF.4 — TaskExecutor: executeTask 拆为 7 方法 (checkTimeout/handleNoSubTask/executeOneSubTask/handleSuccess/handleAccepted/handleFailure)
  ├── RF.5 — ReflexPackManager: importPack 提取 resolvePackFile() + applyPriorAndGraph(); instanceof 检查替代 3 处 raw cast
  ├── RF.6 — MetaScheduler: tick 拆三阶段 (tickPhaseEscalation/tickPhaseTemplate/tickPhaseRoutine); tryExecuteReflex 拆 checkAllGates + handleDeadEnd
  ├── RF.7 — MinecraftActionAdapter: craft 拆 5 方法 (findCraftingRecipe/openCraftingInterface/placeItemsInGrid/collectCraftResult/cleanupCraft)
  ├── RF.8 — ConditionedReflex: scanAndTrigger 拆 computeTimeScale + computeDangerLevel + scoreReflex; executeReflex 拆 executeAtomAction + recordReflexOutcome; handleReflexFailure 拆 classifyAndApplyFailure
  └── RF.9 — AICommand: loadPlaystylePack 拆 parseV2Pack + autoSwitchPersona; exportPlaystylePack 拆 buildExportEnvelope/buildExportProfile/buildExportReflexes; despawnBot 拆 despawnByName + despawnNearest
```

**Phase RFX (REFLEX_CREATE 钩子修复 — 降低 LLM 成本)**

```
Phase RFX (REFLEX_CREATE 钩子修复):
  └── RFX.0 — MetaScheduler.processTemplateResult(): REFLEX_CREATE 分支替换为完整固化流水线
      LLM 返回 JSON → 提取 steps → CategoryMapper 分类 → 构造 ObservedSequence(source=LLM_TEMPLATE)
      → ConditionedReflex.solidifySequence(sequence, category) → 以 trial 状态写入 conditioned/*.json
      → 下个 tick 自动参与 scanAndTrigger → 环境裁决晋升/休眠
       效果: 玩家说"学挖矿" → LLM 填坑 1 次 → 永久反射 → 0 次额外 LLM
```

**Phase RFA (反射寿命周期 & 记��支撑 — 提升拦截率)**

```
Phase RFA (反射寿命周期 & 记忆支撑):
  ├── RFA.1 — MetaScheduler.checkDormantArchives(): 每 100 tick 扫描 archived/ 下 dormant 反射
  │   条件: 后验 > 0.5 + preconditions 通过 → conditionedReflex.tryReactivate() 自动复活
  │   效果: 环境变化后反射自动恢复，不依赖手动 /ai import
  └── RFA.2 — ConditionedReflex.computeMemoryBoost(): scoreReflex 乘记忆系数 1.0 + min(0.3, nodeCount×0.03)
       MemoryGraph.findNodeIdsByReflex() 查询关联记忆 → 有经验的反射得分更高 → 更容易被选中
       效果: 相同情境下成功历史越多的反射优先，不用 LLM
```

## 3. 已知问题

### ~~P-1: MemoryManager.memoriesDir() 无限递归 (line 36)~~ ✅ 已修复
### ~~P-2: TaskManager.activeTaskPath() 无限递归 (line 32-33)~~ ✅ 已修复

### ~~P-3: 寻路未接入主循环~~ ✅ 已修复
`GreedyNavigator` (贪心一步导航) 取代 `AStarPathfinder`，接入 `NavigationController` → `MinecraftActionAdapter.moveTo()`。

### ~~P-4: 导航使用 velocity-based 移动~~ ✅ 已修复
`moveTo()` 每 tick 通过 `GreedyNavigator.getBestStep()` 选最优下一步方向，velocity 层执行。

### ~~P-6: "我替环境判你能不能" — 预判反模式~~ ✅ 已修复
12 处预判逻辑改为"先行动，让环境反馈裁决"：
- `jump()` 不再检查 `isOnGround()`，直接跳
- `flee()`/`retreat()` 不再要求 `HostileEntity` 存在才跑
- `eat()` 找不到食物 → `partial` 而非 `unable`
- `seekShelter()`/`collectItem()` 无目标时继续探索而非放弃
- `dig()`/`attack()` 简化目标过滤 (删 `hardness≥0`/`BEDROCK`/`instanceof` 白名单)
- `isWalkableLike()` 从 `isAir||WATER` 白名单 → `!isSolidBlock()` 碰撞检测
- `Skill.canExecute()` 从 `abstract` → `default true`，消除双重扫描
- `AttackSkill` 删 `instanceof HostileEntity||AnimalEntity` 白名单

### ~~P-5: TemplateManager.fill() 未接入主循环~~ ✅ 已修复
`MetaScheduler.executeCortexLLM()` 现在通过 `TemplateMatcher.match()` 路由到对应的模板，再通过 `TemplateManager.fill()` 调用 LLM。不再直接调用 `AIChatHandler.handleChat()`。

### P-6: MetaContext 已物理删除
Phase 2 已将 MetaContext 物理删除（268 行代码 + 271 行测试移除）。`pendingChatMessage` 状态已合入 `BotInstance` 直管。`BotController.setMetaScheduler()` 的 `MetaContext` 参数也已移除。

### ~~P-7: LLM熔断器永久锁死 + 并发安全 + 环境可控性公式 + 激素死结~~ ✅ 已修复 (Week 1)

| 缺陷 | 文件 | 修复内容 |
|------|------|---------|
| LLM熔断器永久锁死 | `DeepSeekClient.java` | 指数退避重试(1s/2s/4s...最多5次)，Retry-After头处理，`resetCircuitBreaker()` 手动重置 |
| `memoryCache` 并发读写 | `MemoryManager.java` | `ArrayList`→`CopyOnWriteArrayList`，所有 stream 加 `Objects::nonNull` |
| `consecutiveFailures` 全局共享 | `ConditionedReflex.java` | `int`→`AtomicInteger`，`HashMap`→`ConcurrentHashMap`，`HashSet`→`ConcurrentHashMap.newKeySet()` |
| 环境可控性方差硬编码 | `BayesianModule.java` | 移除 `Math.max(variance, 0.1)` floor，改用 `p*(1-p)` 计算后验方差，VARIANCE_SCALE=0.25 |
| intimacy 永不衰减 | `HormonalSystem.java` | 每次tick乘0.999，低于0时移除 |
| curiosity 可被压到0 | `HormonalSystem.java` | 增加 `CURIOSITY_FLOOR=0.1` 下界 |
| stress>0.6 强制打断任务 | `HormonalSystem.java` | 改为作为候选加入，由玻尔兹曼竞争决定 |

---

## 4. 构建

```bash
# 环境: JDK 21, Gradle 8+
cd EAgentMod-1.21.1-Fabric
.\gradlew.bat build
# 输出: build/libs/e-agent-1.21.1.jar
```

### 部署

1. 将 jar 放入 `mods/` 目录
2. 启动 Fabric 服务端 (Loader 0.19.3+)
3. 配置 API 密钥: `/ai setkey <your-api-key>`
4. 自然语言指令: "帮我挖 10 个铁矿"

---

## 5. 游戏内指令

| 指令 | 功能 |
|------|------|
| `/ai spawn <name>` | 生成指定名字的假人 |
| `/ai despawn [name]` | 移除假人 |
| `/ai list` | 列出所有假人 |
| `/ai bot <name> <cmd>` | 指定假人执行 |
| `/ai status` | 当前任务状态 |
| `/ai model [name]` | 查看/设置模型 |
| `/ai reflexes` | 已学习反射 |
| `/ai setkey <key>` | 设置 API 密钥 |
| `/ai reflexpack export <bot> <name> [noprior]` | 导出反射包 (默认含先验) |
| `/ai reflexpack import <bot> <name> [reset]` | 导入反射包 (合并/冷启动) |
| `/ai reflexpack list` | 列出已导入反射包 |
| `/ai reflexpack delete <name>` | 删除反射包 |
| `/ai playstyle list` | 列出可用玩法包 (V2 含 profile 标注) |
| `/ai playstyle load <包名> [机器人]` | 加载 V2 玩法包 (兼容 V1) |
| `/ai playstyle export <机器人> <包名>` | 导出 Bot 状态为 V2 玩法包 |
| `/ai playstyle current [机器人]` | 查看 Bot 当前参数/激素/反射数 |
| `/ai challenge start [days]` | 启动生存挑战：生成 LegacyBot + NewBot，开启监控 |
| `/ai challenge stop` | 停止挑战：杀死两 Bot，打印最终计分报告 |
| `/ai challenge status` | 实时查看两 Bot 对比统计 |
| `/ai help` | 全部指令 |

`@bot_name <消息>` — 精确路由。无 `@` 时路由最近假人。

---

## 6. 源代码结构

```
src/main/java/com/izimi/eagent/
├── EAgent.java                  DI 入口
├── api/                              跨脑区接口定义 (Phase 1 Decoupling)
│   ├── WorldContext.java             世界级共享组件门面
│   ├── BotContext.java               per-bot 组件门面
│   ├── MetaState.java                每 tick 可变状态
│   ├── BrainstemAPI.java             脑干门面
│   ├── AmygdalaAPI.java              杏仁核门面
│   ├── CortexAPI.java                前额叶门面
│   └── CognitiveBrainAPI.java        顶层脑门面
├── api/impl/                         实现类
│   ├── WorldContextImpl.java
│   ├── BotContextImpl.java
│   └── CognitiveBrain.java           CognitiveBrainAPI 实现
├── bayesian/                         BayesianModule + value objects
├── hormonal/                         HormonalSystem (Phase F 移入)
├── cortex/                           前额叶
│   ├── api/                          LLM 接口 + TemplateManager
│   ├── planner/                      Plan/KnowledgeBase/LocalTaskDecomposer/KnowledgeBase
│   ├── prefrontal/                   CognitiveControl + ReflexRecipe
│   ├── chat/                         LocalChatHandler + ChatSessionManager
│   └── task/                         Task/TaskManager/TaskExecutor
├── hippocampus/                      海马体
│   ├── MemoryEntry/MemoryManager/MemoryQuery
│   ├── MemoryGraph/MemoryNode/MemoryEdge  记忆关系图 (§25)
│   └── storage/                      HighlightStorage/TrialStorage
├── amygdala/                         杏仁核
│   ├── ConditionedReflex/DispatchReflex/OneShotAlarmSystem
│   ├── SocialObserver/NaiveBayesClassifier/FamiliarityTracker
│   ├── character/                    BehaviorEventHandler/BehaviorStats/EvaluationCycle
│   └── learning/                     BehaviorEvent/ObservedSequence/CategoryMapper/CorrelationDetector
├── brainstem/                        脑干
│   ├── adapter/                      12 原子动作 (MinecraftActionAdapter) + TemporalScaler
│   ├── bot/                          BotManager/BotInstance/BotSpawner/BotController
│   ├── domain/                       Stage 1-3 领域执行器
│   │   ├── DomainCommand.java        sealed 接口 (Break/Motion/Place/Combat/Craft/Inventory)
│   │   ├── DomainExecutor.java       执行器基类
│   │   ├── DomainRouter.java         路由 + tickAll + 失败收集
│   │   ├── FailureContext.java       结构化失败报告
│   │   ├── DigExecutor.java          Break 领域挖矿
│   │   ├── MotionExecutor.java       Motion 领域移动/跳跃/潜行
│   │   ├── BreakCommand.java
│   │   ├── MotionCommand.java
│   │   ├── PlaceCommand.java         (stub)
│   │   ├── CraftCommand.java         (stub)
│   │   ├── CombatCommand.java        (stub)
│   │   └── InventoryCommand.java     (stub)
│   ├── innate/                       InnateReflexRegistry/9 先天技能 (含 sneak)
│   ├── scheduler/                    MetaScheduler/MotivationEngine/ReflexSatisfaction/DriveState/UrgencyClassifier/InhibitoryControl/SurvivalChallengeMonitor
│   ├── skill/                        Skill + SkillManager
│   ├── navigation/                   GreedyNavigator + NavigationController
│   └── IdleBrain.java
├── command/                          AICommand
├── config/                           ModConfig
├── util/                             FileUtil/JsonUtil
├── log/                              ExecutionLogger
└── state/                            PlayerState/StateManager
```

---

## 7. 运行时数据目录

```
minecraft/eagent/
├── config/                全局配置/先天反射 JSON
├── thresholds/            自适应阈值
├── bayesian/              全局共享先验 (shared_prior.json)
├── reflex_packs/          导入/导出的反射包 (*.json)
├── bots/genomes/          死亡Bot基因组存档 (*.json)
└── bots/{bot_uuid}/
    ├── conditioned/       条件反射库 (reflex_*.json)
    ├── conditioned/archived/  休眠反射归档
    ├── alarms/            L1 一次预警
    ├── memory/            高光记忆 (day_*.mem)
    │   ├── latest.mem     最新记忆快照
    │   ├── memory_graph.json  记忆关系图 (MemoryGraph)
    │   ├── trials/        观察学习试验 (ObservedSequence)
    │   └── highlights/    高光记忆 (独立文件)
    ├── bayesian/          per-bot 后验 (posterior.json)
    ├── bot_params.json    per-bot 参数 (α/β/temperature/gen)
    ├── dispatch_weights.json  DispatchReflex 权重
    ├── plans/             任务计划 (active_plan.json)
    ├── evaluations/       玩家评价缓存
    └── execution_logs/    执行日志 (log_*.json)
```

---

## 8. 测试

| 测试文件 | 数量 | 内容 |
|---------|:---:|------|
| `CategoryMapperTest.java` | 18 | 分类映射 |
| `TaskTest.java` | 15 | 任务拆解 |
| `IdleBrainTest.java` | 10 | 状态机 |
| `ReflexRegistryTest.java` | 15 | 先天反射注册表 |
| `MotivationEngineTest.java` | 19 | 驱力/玻尔兹曼/交叉抑制 |
| `BotParamsTest.java` | 7 | 三规则继承/参数范围/变异 |
| `BayesianModuleTest.java` | 16 | 三层存储/概率预测/收敛判断/FileSystem注入 |
| `TemplateManagerTest.java` | 15 | 模板填空/解析/钩子/速率限制/异常传播/CLARIFICATION/CHAT_RESPONSE |
| `MemoryGraphTest.java` | 38 | 节点/边 CRUD、显著性门控、边推断、图遍历 BFS、因果链、持久化、贝叶斯重排、Hebbian 强化、扩散激活、骨骼导出/导入 |
| `ChatSessionManagerTest.java` | 7 | 窗口限制/方向回退/null安全/防御拷贝 |
| `TemplateMatcherTest.java` | 14 | 路由: CLARIFICATION/TASK_PLAN/REFLEX_CREATE/CHAT_RESPONSE/拦截 |
| **合计 (含新增)** | **337** | **全部通过** |

**新增测试计划：**

| Phase | 测试文件 | 数量 | 内容 |
|:-----:|---------|:---:|------|
| G | `ReflexChainTest.java` | 17 | DAG 构建/遍历/瓶颈检测/共享权重/链约束 |
| H | `MetaSchedulerPhaseHTest.java` | 15 | 时间片/抢占/死路三条件/麦穗连续置信度 |
| I | `BayesianModulePhaseITest.java` | 12 | 环境可控性计算/置信度/后验稳定性/BotState |
| J | `SharedPoolConfigTest.java` + `BayesianModulePhaseJTest.java` | 7+8 | 共享池常量/inferForward/inferBackward/回退阶段 |
| K | `TaskDAGTest.java` | 13 | LLM JSON 解析/DAG 遍历/瓶颈检测/格式校验 |
| L | `ConditionedReflexPhaseLTest.java` | 4 | precondition guard skip/wait/defer |
| M | `ParameterBinderTest.java` | 12 | bindings 绑定/transform/绑定失败→回退 |
| | `AlarmGatingTest.java` | 11 | L1/L3 门控 (OneShotAlarmSystem/SocialObserver/HormonalSystem) |
| 2 | `NeuroStateTest.java` | 11 | 4维向量/余弦距离/数值范围/with*方法 |
| 2 | `HormonalSystemNeuroTest.java` | 17 | 4维字段/衰减/事件更新/旧别名兼容 |
| 2 | `NeuroDynamicsTest.java` | 7 | GABA/Glu 推导/抑制兴奋比/NeuroState重载 |
| 3 | `CognitiveControlTest.java` | 10 | 余弦匹配/候选调制/5-HT情境分支/require合取/阈值参数化 |
| 3 | `MetaSchedulerCognitiveControlTest.java` | 7 | setCognitiveControl 安全性/checkReflex 全路径 (无配方/通过/否决/余弦过低/精确匹配) |
| | **合计** | **337** | **全部通过** |
