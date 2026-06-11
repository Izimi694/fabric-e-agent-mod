# 内化检查清单 — 抽象概念的工程落地指南

## 一句话定义

**抽象 → 内化成最小单元 = 把"概念"翻译成"带信号源、归属层、物质载体、成本模型的工程结构"。**

## 检查清单（5 问）

当你遇到一个抽象概念时，逐条回答这 5 个问题。全部通过则内化成功。

| # | 问题 | 通过标准 |
|:--:|------|---------|
| 1 | **成本**：固化后是 0 吗？会无限调用 LLM 吗？ | **成本的问前提。** 成本不能收敛，再漂亮的设计也不准入 |
| 2 | **归属层**：属于 L0~L6 哪一层，或骨架？ | 唯一定位，不能悬浮 |
| 3 | **信号源**：来自玩家/生物，还是环境？ | 只能属于两类，不能是"我认为" |
| 4 | **物质载体**：JSON 文件？内存数值？硬编码？ | 必须落在已有数据结构上 |
| 5 | **更新方式**：谁改它？衰减规则？触发条件？ | 必须有明确的更新路径 |

---

## 实例：把"好奇心"内化

### ❌ 错误内化（仍是抽象）

> "在性格标签里加一个 curiosity 字段，数值高了就好奇。"

| 问题 | 回答 | 评判 |
|------|------|:----:|
| 成本 | 未评估 | ❌ |
| 归属层 | 未指定 | ❌ |
| 信号源 | 未指定 | ❌ |
| 物质载体 | 概念中的"字段" | ⚠️ |
| 更新方式 | 未指定 | ❌ |

### ✅ 正确内化

```java
// HormonalSystem.java
class HormonalSystem {
    double curiosity;  // 物质载体：内存 double

    void onNoveltyDiscovered() {           // 更新方式
        curiosity = Math.min(1.0, curiosity + 0.1);
    }
    void tick() {
        curiosity = Math.max(0, curiosity - CURIOSITY_DECAY); // 衰减规则
    }
}
```

| 问题 | 答案 |
|:----:|------|
| 成本 | 0（纯本地 tick 运算）✅ |
| 归属层 | L1（激素层）✅ |
| 信号源 | 环境（发现新实体/方块触发 `onNoveltyDiscovered()`）✅ |
| 物质载体 | `double curiosity` 内存变量 ✅ |
| 更新方式 | `onNoveltyDiscovered()` +10%，`tick()` 按 `CURIOSITY_DECAY` 衰减 ✅ |

---

## 新概念内化示例

### 环境可控性指数

```java
// BayesianModule.java
class BayesianModule {
    public double computeControllability(String reflexId, BotContext ctx) {
        Posterior posterior = getPosterior(reflexId, ctx);
        double variance = posterior.variance;
        double controllability = 1.0 / (1.0 + variance / varianceScale);
        if (envChangeRate > threshold) controllability *= 0.5;
        return clamp(controllability, 0, 1);
    }
}
```

| 问题 | 答案 |
|:----:|------|
| 成本 | 0（纯本地查表 + 运算）✅ |
| 归属层 | 骨架 (bayesian/) ✅ |
| 信号源 | 贝叶斯后验分布自身（不来自外部事件）✅ |
| 物质载体 | `double` 内存数值，随 tick 刷新 ✅ |
| 更新方式 | 每次贝叶斯更新后重算，环境变化率每 tick 衰减 ✅ |

### Loop 刷新循环

| 问题 | 答案 |
|:----:|------|
| 成本 | 0（事件驱动，无独立 tick）✅ |
| 归属层 | MetaScheduler (L2) ✅ |
| 信号源 | 反射执行结果（成功/失败事件）✅ |
| 物质载体 | 循环状态机（内存，非持久化）✅ |
| 更新方式 | 反射完成后触发，最大 4 轮 ✅ |

### 前置条件提前返回

| 问题 | 答案 |
|:----:|------|
| 成本 | 0（本地背包/环境/激素状态查询）✅ |
| 归属层 | MetaScheduler 门控 (L2) + 反射 JSON 声明 (L4) ✅ |
| 信号源 | 背包/环境/激素三类来源 ✅ |
| 物质载体 | `preconditions[]` 数组（reflex JSON）+ `fail_strategy` 枚举（skip/wait/defer）✅ |
| 更新方式 | 反射固化时声明，执行前每轮检查 ✅ |

### ParameterBinding

| 问题 | 答案 |
|:----:|------|
| 成本 | 0（纯查表 + 赋值运算）✅ |
| 归属层 | 骨架 (ReflexChain / TaskDAG) ✅ |
| 信号源 | DAG 上游节点输出 ✅ |
| 物质载体 | `bindings[{from, to, transform}]`（TaskDAG）+ `input_slots`（reflex JSON）✅ |
| 更新方式 | 绑定失败→依赖回退（不单独更新）✅ |

## 常见内化物对照表

| 抽象概念 | 信号源 | 归属层 | 物质载体 |
|---------|--------|:------:|---------|
| 习惯 | 自身执行结果 | L4（反射） | `conditioned/reflex_xxx.json` |
| 情绪 | 环境+生物交互 | L1（激素） | `HormonalSystem` 内存数值 |
| 记忆 | 环境事件 | 骨架 / 海马体 | `memory/` day_xxx.mem |
| 性格 | 反射权重的统计分布 | L4（反射）× N | `conditioned/*.json` 中的 stw/ltb |
| 智慧 | 调度结果 | L2（调度） | `DispatchReflex` 权重 |
| 紧急程度 | 环境+生物+激素 | L2（调度） | `UrgencyClassifier` 双阈值比较 |
| 贝叶斯先验 | 所有反射的执行结果 | 骨架 (bayesian/) | `bayesian/shared_prior.json` |
| 模板填空 | LLM 响应 | 骨架 (cortex/api) | `TemplateManager` + JSON 模板 |
| DAG 依赖 | LLM 粗分解 | 骨架 (cortex/planner) | `TaskDAG` 内存对象 |
| 反射链 | DAG 构建 | L4 (ReflexChain) | `dag/` 索引 + 反射节点关系 |
| 环境可控性 | 贝叶斯后验方差 | 骨架 (bayesian/) | `double` 内存值 |
| 瓶颈节点 | 入度检测 + LLM 标记 | L4 (ReflexChain) | `boolean isBottleneck` |
| 参数绑定 | DAG 上游输出 | 骨架 (ReflexChain) | `bindings[{from, to, transform}]` |
| 边界门控 | 背包/环境/激素 | L2 (MetaScheduler) | `preconditions[]` in reflex JSON |
| 共享池 | 配置参数 | 骨架 (config/) | `SharedPoolConfig` + 4 约束值 |

## 与准入三原则的关系

| 内化检查 | 对应 AGENTS.md 准入原则 |
|---------|------------------------|
| 成本 → 0 | **第一原则 — 成本可收敛** |
| 归属层 → 唯一定位 | 有层归属 |
| 物质载体 → 已有数据结构 | 可物质化 |
| 信号源 → 两类之一 | 架构约束的实现 |
| 更新方式 → 明确规则 | 工程完整性要求 |
