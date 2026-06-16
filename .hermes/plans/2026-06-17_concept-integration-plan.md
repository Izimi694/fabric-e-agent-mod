# 抽象概念运行时集成 & 反射配方自动生成 实施计划

**目标：** (1) 将 GameConceptDetector 接入 ConditionedReflex 的前置条件系统，
(2) REFLEX_CREATE 钩子自动生成 CognitiveControl 配方向量，
(3) 新增高效挖矿知识到 KnowledgeBase

**技术栈：** Java 21, Fabric Loom, JUnit 5

---

## 总体路线图

```
Phase D ─── 抽象概念前提前置（Runtime 集成）
  ├── D.1: ConditionedReflex 增加 concept 前置条件类型
  ├── D.2: GameConceptDetector 注入 ConditionedReflex
  └── D.3: 测试——验证 concept precondition 在新/旧反射中工作

Phase E ─── 反射配方自动生成（CognitiveControl 集成）
  ├── E.1: REFLEX_CREATE 钩子中调用 generateDefaultRecipe()
  └── E.2: 测试——验证新反射有可用的 recipe

Phase F ─── 高效挖矿知识（KnowledgeBase 扩展）
  ├── F.1: KnowledgeBase 新增 "strip_mining" 和 "branch_mining" 模板
  └── F.2: 测试——验证模板匹配

Phase G ─── 阈值参数文档
  └── G.1: 创建 THRESHOLDS.md
```

---

## Phase D: 抽象概念前提前置

### Task D.1: ConditionedReflex 增加 concept 前置条件类型

**文件：**
- Modify: `src/main/java/com/izimi/eagent/amygdala/ConditionedReflex.java`

**开关 —— 在 `checkPreconditions()` 的 switch 中增加 `case "concept"`：**

```java
// 当前 (行 149-153):
boolean passed = switch (type) {
    case "item" -> checkItemPrecondition(bot, key, match);
    case "state" -> checkStatePrecondition(bot, key, operator, value, hormonalSystem);
    default -> true;
};

// 修改后:
boolean passed = switch (type) {
    case "item" -> checkItemPrecondition(bot, key, match);
    case "state" -> checkStatePrecondition(bot, key, operator, value, hormonalSystem);
    case "concept" -> checkConceptPrecondition(bot, key, value);
    default -> true;
};
```

**新增方法：**

```java
private boolean checkConceptPrecondition(ServerPlayerEntity bot, String conceptName, double expectedValue) {
    if (conceptDetector == null) return true;  // 无检测器时宽松通过
    boolean actual = conceptDetector.checkConcept(conceptName, bot);
    boolean expected = expectedValue != 0.0;  // value=1.0 → true, value=0.0 → false
    return actual == expected;
}
```

### Task D.2: GameConceptDetector 注入 ConditionedReflex

**文件：**
- Modify: `src/main/java/com/izimi/eagent/amygdala/ConditionedReflex.java`
- Modify: `src/main/java/com/izimi/eagent/EAgent.java`（初始化处注入）

**ConditionedReflex 增加字段 + setter：**

```java
// 新增字段
private GameConceptDetector conceptDetector;

// 新增 setter
public void setConceptDetector(GameConceptDetector detector) {
    this.conceptDetector = detector;
}
```

**需要新增 import：**
```java
import com.izimi.eagent.brainstem.domain.GameConceptDetector;
```

**EAgent.java 初始化处注入：**

```java
// 在 KnowledgeBase 加载之后，找到原有的：
knowledgeBase = KnowledgeBase.load();
conditionedReflex.setBayesianModule(bayesianModule);
// 新增：
conditionedReflex.setConceptDetector(new GameConceptDetector(knowledgeBase));
```

### Task D.3: 测试 concept precondition

**文件：**
- Create: `src/test/java/com/izimi/eagent/amygdala/ConditionedReflexPhaseDTest.java`
- Modify: `src/main/java/com/izimi/eagent/amygdala/ConditionedReflex.java`（使 test 可注入 mock detector）

**测试方案（依赖 mock 的 GameConceptDetector）：**

```java
class ConditionedReflexPhaseDTest {
    // 测试 concept precondition 通过
    //   给定: 反射JSON有 {"type":"concept","key":"is_well_lit","value":1.0}
    //          GameConceptDetector.isWellLit() → true
    //   预期: checkPreconditions → passed=true

    // 测试 concept precondition 不通过
    //   给定: 反射JSON有 {"type":"concept","key":"is_well_lit","value":1.0}
    //          GameConceptDetector.isWellLit() → false
    //   预期: checkPreconditions → passed=false, failStrategy="skip"

    // 测试无 conceptDetector 时宽松通过
    //   给定: conceptDetector=null
    //   预期: checkConceptPrecondition → true (宽松降级)
}
```

---

## Phase E: 反射配方自动生成

### Task E.1: REFLEX_CREATE 钩子中生成默认配方

**文件：**
- Modify: `src/main/java/com/izimi/eagent/brainstem/scheduler/MetaScheduler.java`
- Modify: `src/main/java/com/izimi/eagent/cortex/prefrontal/CognitiveControl.java`（可能需要注册方法）

MetaScheduler 的 `processTemplateResult()` 中 REFLEX_CREATE 分支，在固化反射后追加配方生成：

```java
// 在 processTemplateResult() 中，找到 REFLEX_CREATE 分支的末尾
// 当前已有：
// ConditionedReflex.solidifySequence(sequence, category) → trial 状态写入

// 追加：
// 1. 从第一步动作推断默认配方向量
ReflexRecipe recipe = generateDefaultRecipeForAction(reflexId, firstStep.action);
// 2. 写入 reflex_recipes.json（持久化）
cognitiveControl.registerRecipe(recipe);
// 3. 日志
LOGGER.info("[ReflexRecipe] 自动生成配方: {} → {}", reflexId, firstStep.action);
```

```java
/** 基于动作类型推断初始神经递质配方向量 */
private ReflexRecipe generateDefaultRecipeForAction(String reflexId, String action) {
    double ne = 0.5, da = 0.5, serotonin = 0.5, ach = 0.5;
    int safetyDistance = 2;

    switch (action) {
        case "attack" -> {
            ne = 0.7; da = 0.6; serotonin = 0.2; ach = 0.8;
            safetyDistance = 3;
        }
        case "dig", "mine" -> {
            ne = 0.4; da = 0.5; serotonin = 0.4; ach = 0.7;
        }
        case "moveTo", "flee" -> {
            ne = 0.8; da = 0.3; serotonin = 0.3; ach = 0.4;
            safetyDistance = 5;
        }
        case "eat" -> {
            ne = 0.3; da = 0.5; serotonin = 0.7; ach = 0.3;
        }
        case "craft", "placeBlock" -> {
            ne = 0.3; da = 0.6; serotonin = 0.6; ach = 0.8;
        }
        case "equipItem" -> {
            ne = 0.4; da = 0.5; serotonin = 0.5; ach = 0.6;
        }
        default -> { /* 保持 0.5 中性 */ }
    }
    return new ReflexRecipe(reflexId, new NeuroState(ne, da, serotonin, ach),
            Map.of(), safetyDistance, 1.0);
}
```

需要的方法签名在 `CognitiveControl` 中补全：`registerRecipe(ReflexRecipe)`。

### Task E.2: 测试配方自动生成

**文件：**
- Modify: `src/test/java/com/izimi/eagent/cortex/prefrontal/CognitiveControlTest.java`（追加 3 测试）

```java
@Test void reflexRecipe_autoGenerateAttack() (...)
@Test void reflexRecipe_autoGenerateDig() (...)
@Test void reflexRecipe_unknownAction_defaultsToNeutral() (...)
```

---

## Phase F: 高效挖矿知识

### Task F.1: KnowledgeBase 挖矿知识模板

**文件：**
- Modify: `src/main/java/com/izimi/eagent/cortex/planner/KnowledgeBase.java`
- Modify: `src/main/resources/challenge_milestones.json`（可选追加高效挖矿作为 bonus）

在 `DEFAULT_JSON` 的 `game_rules` 段中追加：

```json
"mining_patterns": {
  "strip_mining": {
    "description": "在 Y=11 或 Y=-59 水平直线挖 2x1 隧道，每隔 3 格分岔",
    "optimal_y_levels": [-59, 11],
    "tunnel_spacing": 3,
    "tunnel_height": 2,
    "efficiency_factor": 0.8
  },
  "branch_mining": {
    "description": "主隧道两侧每隔 3 格挖分支隧道",
    "branch_spacing": 3,
    "branch_length": 20,
    "efficiency_factor": 0.9
  },
  "cave_mining": {
    "description": "探索天然洞穴，优先检查 exposed ores",
    "efficiency_factor": 1.5
  }
}
```

同时增加对应的 getter：
```java
public Map<String, Object> getMiningPattern(String name) {
    Map<String, Object> patterns = getGameRuleMap("mining_patterns");
    Object v = patterns.get(name);
    if (v instanceof Map) return (Map<String, Object>) v;
    return Map.of();
}
```

---

## Phase G: 阈值参数文档

### Task G.1: 创建 THRESHOLDS.md

**文件：**
- Create: `THRESHOLDS.md`

```markdown
# E-Agent 核心阈值参数文档

## 分类

| 类型 | 来源 | 例子 |
|:----:|:----:|:----:|
| e-based | 最优停止理论 | 探索/利用 37%, 执行比 63.2% |
| 启发式 | 工程直觉 | NE 威胁阈值 0.5, 显著性门控 0.6 |
| 可调 | 运行时性能可感知 | SAFETY_DISTANCE_THRESHOLD |

## 完整阈值表

| 阈值 | 值 | 类型 | 文件 | 影响 |
|:----:|:--:|:----:|:----:|:----:|
| 探索/利用切割 | 1/e ≈ 0.3679 | e-based | MotivationEngine | shouldExplore() |
| 执行比 | 63.2% | e-based | MetaScheduler | computeTimeSlice() |
| 抢占阈值 | 1/e | e-based | MetaScheduler | shouldPreempt() |
| 收敛判断 | 1/e | e-based | BayesianModule | isConverged() |
| NE 威胁阈值 | 0.5 | 启发式 | CognitiveControl | 低/高威胁切换 |
| 显著性门控 | 0.6 | 启发式 | MemoryGraph | inferEdges() |
| DA 攻击前提 | ≥ 0.4 | 启发式 | CognitiveControl | attack 候选 |
| 5-HT 抑制强度 | 0.6 | 启发式 | CognitiveControl | 全局抑制系数 |
| 攻击刹车上限 | 0.9 | 工程约束 | NeuroDynamics | attackInhibition |
| LLM 冷却 | 400 ticks | 工程约束 | MetaScheduler | 每次 LLM 后冷却 |
| ...

## 如何调优
详见 /ai challenge 15 天对比测试
```

---

## 验证策略

```bash
# 每次改动后
./gradlew.bat compileJava && ./gradlew.bat test

# Phase D 专用
./gradlew.bat test --tests "*ConditionedReflexPhaseD*"

# Phase E 专用
./gradlew.bat test --tests "*CognitiveControlTest*"

# Phase F 验证挖矿知识加载
echo "在 Minecraft 服务器中验证: /ai status 显示挖矿知识"
```
