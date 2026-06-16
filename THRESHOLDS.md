# E-Agent 核心阈值参数文档

> 所有阈值的分类与来源说明。
> 调优方法：运行 `/ai challenge start 15` 对比 LegacyBot vs NewBot 得分变化。

---

## 一、分类体系

| 类型 | 来源 | 可调性 | 例子 |
|:----:|:----:|:------:|------|
| **e-based** | 最优停止理论 / 37% 法则 | 不建议调 | 探索/利用切割 37%, 执行比 63.2% |
| **启发式** | 工程直觉 + 实验观察 | 可调，需 A/B 验证 | NE 威胁阈值 0.5, 显著性门控 0.6 |
| **工程约束** | 运行时开销/安全考虑 | 可调 | LLM 冷却 400 ticks, 攻击刹车 0.9 |

---

## 二、完整阈值表

### 2.1 e-based（理论基础坚实）

| 阈值 | 值 | 公式 | 文件 | 用途 |
|:----:|:--:|:----:|:----:|:----:|
| 探索/利用切割 | 0.3679 | 1/e | `MotivationEngine` | `shouldExplore()` 边界 |
| 执行比 | 0.6321 | 1 - 1/e | `MetaScheduler` | `computeTimeSlice()` 任务时间片 |
| 抢占阈值 | 0.3679 | 1/e | `MetaScheduler` | `shouldPreempt()` 优先级增量 |
| 收敛判断 | 0.3679 | 1/e | `BayesianModule` | `isConverged()` 后验变化率 |
| 记忆衰减 | e^(-λt) | e 指数 | `MemoryManager`/`ConditionedReflex` | 遗忘曲线 |

### 2.2 启发式（工程直觉）

| 阈值 | 值 | 文件 | 影响 | 调优方向 |
|:----:|:--:|:----:|:----:|:--------:|
| NE 威胁阈值 | 0.5 | `CognitiveControl` | 区分低/高威胁情境 | ↑更保守，↓更激进 |
| 显著性门控 | 0.6 | `MemoryGraph.inferEdges()` | 边推断过滤 | ↑更少边，↓更多关系 |
| DA 攻击前提 | ≥ 0.4 | `CognitiveControl` | 攻击候选的合取条件 | ↑更难触发攻击 |
| 5-HT 全局抑制 | 0.6 | `CognitiveControl` | 低威胁下全局抑制强度 | ↑更易僵住 |
| 攻击刹车系数 | 0.5 | `NeuroDynamics` | 5-HT 对 attack 的抑制权重 | ↑更难攻击 |
| 逃跑油门系数 | 0.4 | `NeuroDynamics` | 5-HT 对 flee 的促进权重 | ↑更易逃跑 |
| 低置信度刹车 | 0.2 | `NeuroDynamics` | (1-confidence) 的对 attack 抑制 | ↑更谨慎 |
| 失败累积刹车 | 0.05/次 | `NeuroDynamics` | 每次失败 × 0.05 | ↑失败后更保守 |
| 疲劳刹车上限 | 0.3 | `NeuroDynamics` | 失败累积上限 | ↑可承受更多失败 |
| DA 警觉驱动 | 0.3 | `NeuroDynamics` | DA → flightExcitation | ↑奖赏驱动逃跑 |
| NE 警觉驱动 | 0.5 | `NeuroDynamics` | NE → flightExcitation | ↑恐惧驱动逃跑 |
| 新奇度贡献 | 0.2 | `NeuroDynamics` | novelty → flightExcitation | ↑新奇→探索 |
| 刹车/油门上界 | 0.9 | `NeuroDynamics` | 钳位上限 | 保护性约束 |

### 2.3 游戏规则（从 KnowledgeBase.game_rules 加载）

| 阈值 | 值 | 来源 | 说明 |
|:----:|:--:|:----:|------|
| safe_light_level | 8 | `game_rules.json` | 光级 ≥ 8 视为"光源充足" |
| monster_spawn_light_max | 7 | `game_rules.json` | 怪物在光级 ≤ 7 处生成 |
| torch_light_level | 14 | `game_rules.json` | 火把提供的光级 |
| shelter_walls_min | 3 | `game_rules.json` | 至少 3 面墙算庇护所 |
| 食物储备阈值 | 20 | `game_rules` food_tracker | 至少 20 点饱食度算"有食物" |
| LLM 冷却 | 400 ticks (20s) | `MetaScheduler` | 每次 LLM 调用后冷却期 |

### 2.4 工程约束

| 阈值 | 值 | 文件 | 原因 |
|:----:|:--:|:----:|:----:|
| 囊泡链长上限 | 5 | `SharedPoolConfig` | 反射链长度 ≤ 5 |
| 贝叶斯候选上限 | 5 | `SharedPoolConfig` | 候选集 ≤ 5 |
| 共享先验比例 | 10-30% | `SharedPoolConfig` | 跨 Bot 共享 |
| 总驱力归一化 | 1.0 | `MotivationEngine` | 硬编码 |
| replan 上限 | 3 | `MetaScheduler` | 防止 LLM 死循环 |
| MAX_UNABLE_RETRIES | 5 | `TaskExecutor` | 任务失败后重试上限 |
| 冷却期跳过 LLM | 400 ticks | `MetaScheduler` | 熔断保护 |
| 死亡扣分 | 200 | `SurvivalChallengeMonitor` | 评分系统 |

---

## 三、如何调优

### 3.1 推荐流程

1. 运行基线挑战：`/ai challenge start 15`
2. 记录 LegacyBot vs NewBot 的最终报告
3. 修改单一阈值（如 NE 威胁阈值 0.5 → 0.6）
4. 重新运行 `/ai challenge start 15`
5. 对比两次报告的得分变化
6. 记录实验结果到 `eagent/threshold_experiments/`

### 3.2 安全调优规则

- **e-based 阈值不改** — 有最优停止理论支撑
- **启发式阈值只往"安全方向"调** — `effectiveThreshold = base + |modulation|`
- **每次只改一个参数** — 否则无法归因
- **实验记录必须保留** — 不记录就等于没调
