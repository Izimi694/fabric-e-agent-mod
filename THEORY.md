# CognitiveBrain — 理论背景与从零推导

---

## 第一原则

**以降低成本作为主要战略方向，以确定模块边界作为主要降低成本的方法。**

```
边界模糊 → 互相调用 → 本应本地做的事泄露到 LLM → 成本爆炸。
边界清晰 → 每个模块只做自己"最便宜能做到"的事 → LLM 只在边界终端出现一次。
```

---

## 一、核心矛盾

**运行时间 ∞ vs 预算有限。**

在 Minecraft 中控制一个可以长期在线的 AI 玩家，要求：
- **不无限烧钱** → 少调大模型 API
- **能长期在线** → 本地自动学习、自动固化
- **个体差异** → 不同 bot 性格不同
- **行为可解释** → 什么情况做什么，能追溯

这不是研究"意识本质"，而是在有限、可控、可重复的环境里，建一个经济且可演化的行为控制系统。

---

## 二、从零推导：五个递进洞察

### 洞察 1：记忆 ≠ 技能

| 维度 | 记忆（陈述性） | 反射（程序性） |
|------|-------------|-------------|
| 存储内容 | 事实、观察、偏好 | 参数化的步骤链 |
| 精确性要求 | 低（模糊更有价值） | 高（步骤错全错） |
| 执行成本 | 仍需 LLM 理解 | 命中即零成本 |
| 泛化能力 | 低 | 高（参数自动适配） |

**核心论断**：记忆告诉 AI"上次做了什么"，反射告诉 AI"下次怎么做"。

**工程边界**：MemoryManager（陈述性）vs ConditionedReflex（程序性）— 彻底分离。

### 洞察 2：反射链 = 参数化步骤序列

反射不是单一动作，是一组可参数化的原子步骤。泛化的方式不是复制整条链，而是替换链中的参数（目标位置、物品类型等）。

**工程边界**：ConditionedReflex 的 atoms 数组 + stw/ltb 双权重，每条链独立演化。

### 洞察 3：贝叶斯 = 错误预测与拦截

#### 三层存储

| 存储区 | 内容 | 更新频率 | 对应公式项 |
|--------|------|---------|---------|
| 先验条件表 | P(成功) | 极慢 | 先验概率 |
| 后验观测链 | P(环境特征 \| 成功/失败) | 快（每次错误） | 似然度 |
| 主题锚定区 | 当前任务锚点 | 中 | 条件过滤器 |

#### 计算公式

```
P(成功 | 环境) ∝ P(环境 | 成功) × P(成功)
```

#### 错误蒸馏

预测错了不是终点。失败信息被拆解为环境特征，更新条件概率表，下次预测更准。

**工程边界**：BayesianModule（事后统计）vs ConditionedReflex（执行）。贝叶斯模块不输出显式后验概率，不参与实时决策门控。大脑没有"后验概率输出神经元"——贝叶斯公式是描述工具，不是神经运行机制。

### 洞察 4：激素调度 = 相对优先级

激素只输出相对值（stress vs curiosity vs aggression），不输出绝对值。玻尔兹曼选择只看相对大小，temperature 控制随机性，交叉抑制防止视角振荡。

**工程边界**：HormonalSystem（激素浓度）vs MotivationEngine（视角选择）— 激素不直接决定动作。

### 洞察 5：e = 自然切割边界

| 切割对象 | e 的角色 | 具体数值 |
|---------|---------|---------|
| 探索/利用 | 最优停止边界 | 37% 探索，63% 利用 |
| 任务切换 | 执行/切换比例 | 63.2% 执行，36.8% 缓冲 |
| 记忆衰减 | 遗忘曲线 | e^(-λt) |
| 概率收敛 | KL 散度边界 | 1/e 阈值 |

**工程落点**：探索阈值（MotivationEngine）、切换缓冲（MetaScheduler）、收敛判断（BayesianModule）。

### 洞察 6：共享池 = 有限资源下的动态调配

共享池的本质不是"无限容量的数据库"，而是用有限资源 + 动态调配实现效率最大化。

| 池 | 生物对应 | 约束 | 工程实现 |
|----|---------|:----:|---------|
| 囊泡超级池 | 相邻突触共享囊泡 | 反射链长度 ≤ 5 | chain_max_length |
| 工作记忆绑定池 | 有限特征绑定 | 贝叶斯候选集 ≤ 5 | bayesian_candidate_limit |
| 跨脑共享子空间 | dmPFC 社交对齐 | 共享先验比例 10-30% | shared_prior_ratio |
| 归一化网络池 | 总活动恒定 | 总驱力 = 1.0 | 硬编码归一化 |

四池对应不同生物结构，约束独立，不交叉合并。

### 洞察 7：连续信号 + 离散决策

- 神经元速率编码是连续的（放电频率），门控阈值是离散的（钠离子通道阈值）
- **信号存储和计算**用连续值（0-1），**决策边界**用离散阈值
- 阈值由激素动态调节：stress↑ → 执行阈值↑（更谨慎），curiosity↑ → 探索阈值↓（更爱试错）

| 连续信号 | 离散决策点 | 阈值调节 |
|---------|-----------|---------|
| 贝叶斯后验均值 > ? | 执行该反射 | stress 高时阈值↑ |
| 置信度方差 < ? | 标记为 deterministic | curiosity 高时↓ |
| 环境可控性 > ? | L1/L3 需验证 | stress 高时阈值↓ |

### 洞察 8：Simon IDC 三阶段决策框架

决策分三个阶段：I — 认识问题（情境识别），D — 设计对策（发散，≤5 候选），C — 选择对策（收敛，贝叶斯精筛）。价值计算（贝叶斯）和价值选择（MetaScheduler）分离。

**前额叶三层次 (Grabenhorst & Rolls 2011)**：Tier1 识别特征 → Tier2 计算价值 → Tier3 做出选择（ACC 冲突仲裁）。

### 洞察 9：有限步骤 = 微分 + 竞争 + 关系 + 刷新 + 锚定 + 适应

在承认外部环境动态无限、内部有限步骤的条件下，想要以最低成本对外部拟合，需要六步：

| # | 定义 | 工程落点 | 数据结构 |
|:--:|------|---------|---------|
| ① | 对外部微分，使其变为有限步骤 | TaskDAG + ReflexChain + Template TASK_PLAN | `TaskDAG.SubtaskNode`, `ReflexChain.ReflexNode` |
| ② | 接收环境，贝叶斯/激素在竞争池抉择动作 | MetaScheduler 玻尔兹曼 + IDC 三阶段 | `DriveState`, `Posterior`, `BotParams` |
| ③ | 记忆只记关系与连续，非切片 | MemoryGraph 关系图 (`memory_graph.json`) | `MemoryNode`, `MemoryEdge`, `RelationType` enum |
| ④ | 每次读上次记忆，接收-竞争-执行后更新记忆刷新 | Loop 事件驱动刷新 (§17) + MemoryGraph.inferEdges() | `MetaState`, `inferEdges()` |
| ⑤ | 找稳定标识防动态积分偏离 | 贝叶斯收敛(e) + 瓶颈节点 + 固化阈值 | `PosteriorSnapshot.isConverged()`, `isBottleneck`, `reflex.status=healthy` |
| ⑥ | 以是否适应外部环境决定成败 | 环境裁决原则 + 错误蒸馏 + stw/ltb 更新 | `successRate`, `consecutiveFailures`, `stw/ltb` |

**统一公式**：
> 微分是压缩，竞争是选择，关系是结构，刷新是去噪，锚定是稳定，适应是标准。

#### 附：高光记忆的生物启发

在神经科学中，"高光记忆"的设计有坚实的生物学基础：

| 项目概念 | 生物学对应 | 核心相似点 | 支持文献 |
|---------|-----------|-----------|---------|
| 高光记忆 | 显著性 (Salience) | 大脑自动标记重要信息，使其更容易被记住 | Saliency / Salience |
| 记忆固化 | 记忆印记 (Engram) | 一次学习经历在特定神经元网络中留下物理痕迹 | Josselyn & Tonegawa, *Science* (2015) |
| 记忆动态 | 记忆印记稳定性与灵活性 | 记忆痕迹会根据新经验更新和调整 | Memory Engram Stability and Flexibility |
| 注意力状态 | 泛光灯 vs 聚光灯 | 高光记忆 ≈ 聚光灯高度聚焦的注意力状态 | Sustained Attention as a Floodlight |

判定"高光"的多维依据在工程中的对应：

| 显著性维度 | 生物学定义 | 工程判定条件 |
|-----------|-----------|-------------|
| 感知显著性 | 视觉突出、声音独特 | 稀有实体/方块检测、异常事件 |
| 语义/情境显著性 | 违反预期、与核心目标强相关 | Task 目标相关、贝叶斯 posterior 突变 |
| 结果显著性 | 巨大收益或损失 | 死亡/击杀 BOSS / 获得稀有物品 |
| 社交显著性 | 其他智能体普遍关注 | SocialObserver 群体注意力检测 |

---

## 三、统一表述

> 并发不是并行，是极速串行。
> 无限不是无界，是有限段的递推。
> 复杂不是高维，是压扁后用 e 切割。

反射链线性化任务，激素线性化优先级，贝叶斯线性化不确定性——三个线性化叠加，并发的复杂度被压成一根线，e 告诉你线该切在哪里。

---

## 四、论文引用

### 第一组：贝叶斯大脑与自由能原理

1. **Knill, D. C., & Pouget, A. (2004).** The Bayesian brain. *Trends in Neurosciences*.
   → 支撑"大脑通过概率分布编码不确定性"，对应先验/后验分离存储。

2. **Friston, K. (2010).** The free-energy principle. *Nature Reviews Neuroscience*.
   → 贝叶斯更新 = 变分自由能最小化，对应后验更新机制。

### 第二组：模型误设与收敛边界

3. **Fudenberg, D. et al. (2022).** Bayesian learning with misspecified models. *arXiv*.
   → 后验收敛到 KL 散度最小的近似解，对应不追求"绝对准确"。

### 第三组：最优停止与 37% 法则

4. **Ferguson, T. S. (1989).** Who solved the secretary problem? *Statistical Science*.
   → 37% 法则的数学史依据，对应探索/利用切割。

5. **Gilbert, J. P. & Mosteller, F. (1966).** Recognizing the maximum of a sequence. *JASA*.
   → 最优停止策略的经典证明，对应任务切换窗口。

### 第四组：梯度下降与认知架构

6. **Rosenbloom, P. S. et al. (2013).** Learning via Gradient Descent in Sigma. *CogSci*.
   → 梯度下降可作为认知架构核心学习机制，对应贝叶斯更新工程实现。

7. **Baltieri, M. & Buckley, C. L. (2021).** Kalman filters as gradient descent on variational free energy. *arXiv*.
   → 贝叶斯滤波 = 梯度下降的稳态解。

### 第五组：人脑的无限处理机制

8. **Tozzi, A. & Peters, J. F. (2019).** The brain deals with infinity by means of the finite. *Cognitive Neurodynamics*.
   → 建立观测边界替代处理"无限"，对应探索窗口切割。

### 第六组：Go/NoGo 分层仲裁

9. **Guitart-Masip, M. et al. (2012).** Go and no-go learning in the basal ganglia. *Nature Reviews Neuroscience*.
   → Pavlovian vs Instrumental 系统竞争，对应环境可控性分层仲裁。

10. **Collins, A. G. E. & Cockburn, J. (2021).** A framework for the development of decision-making. *PLOS Computational Biology*.
    → Go/NoGo 实验验证了环境可控性对决策系统选择的动态调节。

### 第七组：探索/利用平衡

11. **Lee, M. D. & Zhang, N. (2024).** Individual differences in exploration-exploitation. *Journal of Cognitive Neuroscience*.
    → 探索倾向跨任务一致，支持每反射独立置信度制导而非全局常量。

12. **The Brain Bandit Team (2025).** Brain Bandit: uncertainty-driven exploration. *ICLR*.
    → 探索偏向由网络内在不确定性编码，支持置信度制导探索窗口。

### 第八组：共享子空间与任务分解

13. **Nature (2025).** GABAergic neurons dominate shared subspaces in social behavior. *Nature*.
    → 抑制性神经元主导共享子空间，共享编码的是行为/互动而非孤立动作。破坏共享子空间显著降低行为适应性。

14. **Tomov, M. S. et al. (2023).** Task decomposition via bottleneck states. *arXiv*.
    → 规划成本与任务性能的权衡决定最佳分解点，瓶颈节点即多条路径必须经过的状态。

### 第九组：工作记忆与特征绑定

15. **Treisman, A. (1996).** The binding problem. *Current Opinion in Neurobiology*.
    → 注意是绑定的关键，对应 ParameterBinding 需要贝叶斯选择"注意"激活。

16. **Grabenhorst, F. & Rolls, E. T. (2011).** Value, pleasure and choice in the orbitofrontal cortex. *Trends in Cognitive Sciences*.
    → 前额叶三层次：识别特征→计算价值→做出选择。价值计算与价值选择分离。

### 论文章节建议

| 章节 | 引用论文 |
|------|---------|
| 绪论/理论背景 | Knill & Pouget (2004), Friston (2010) |
| 方法：贝叶斯模块 | Fudenberg et al. (2022), Baltieri & Buckley (2021) |
| 方法：并发与调度 | Ferguson (1989), Gilbert & Mosteller (1966) |
| 方法：认知架构对比 | Rosenbloom et al. (2013) |
| 方法：决策仲裁 | Guitart-Masip et al. (2012), Collins & Cockburn (2021) |
| 方法：探索/利用 | Lee & Zhang (2024), Brain Bandit (2025) |
| 方法：任务分解 | Tomov et al. (2023), Treisman (1996) |
| 方法：决策层次 | Grabenhorst & Rolls (2011) |
| 讨论：认知根源 | Tozzi & Peters (2019) |
| 讨论：共享子空间 | Nature (2025) |

---

## 五、Related Work

| 维度 | 已有工作 | 本系统做法 |
|------|---------|-----------|
| 行为分层 | Brooks Subsumption Architecture | 6 层拦截器 + 按成本分层 |
| 双权重学习 | TD(λ) / Dual-process RL | 简化工程实现 + 休眠机制 |
| 情绪/激素调节 | EM-BDI / Homeostatic regulation | 绑定 Perspective 选择 |
| LLM + 技能复用 | Voyager / Generative Agents | 反射固化，成本收敛到 0 |

### 这是组合，不是发明

- 双权重（stw/ltb）≈ Q-learning 的简化变体
- 繁衍 ≈ 遗传算法的特例
- MetaScheduler ≈ Behavior Tree 的变种
- 六层分层 ≈ Subsumption Architecture 的分层继承

### 独特性的可能方向

**"本地优先 + 反射固化 + 休眠不删除"的组合机制**——不同于现有 LLM 缓存方案。

### 生物学启发的声明

本系统引用生物学概念（条件反射、激素、突触可塑性）作为**设计启发**，不是声称模拟神经系统。以下对应关系是**工程简化**：

- 双权重 (stw/ltb) ⇢ 类似突触可塑性的快/慢成分，但不模拟生物机制
- 激素系统 ⇢ 类似神经调节的行为倾向调节，不是内分泌系统模型
- 休眠/复活 ⇢ 类似记忆再巩固，但纯工程决策

这些不是学术贡献，是工程上的简化。

---

> 工程架构详细说明见 [ARCHITECTURE.md](./ARCHITECTURE.md) (含 §15-24: DAG/ReflexChain/Loop/可控性/死路/双向推理/回退/共享池/门控/参数绑定)
> 开发状态与路线图见 [DEVELOPMENT.md](./DEVELOPMENT.md) (含 Phase G-M)
> Agent 设计指南见 [AGENTS.md](./AGENTS.md) (含 §7-11: 贝叶斯仲裁/麦穗/共享池/连续信号/IDC)
