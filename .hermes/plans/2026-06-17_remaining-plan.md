# 剩余计划：配方测试 + 运行时里程碑加载 + 里程碑日志可视化

**目标：** 补全概念集成计划中未实现的测试和集成项，让版本功能真正可用。

**技术栈：** Java 21, Fabric Loom, JUnit 5

---

## 剩余工作

```
Phase H ─── 补全测试（1 Task）
  ├── H.1: 自动配方生成测试 (E.2)

Phase I ─── 运行时里程碑加载（2 Tasks）
  ├── I.1: JsonUtil 添加 readListFromFileSafe 方法
  └── I.2: AICommand 从 challenge_milestones.json 读取而非硬编码

Phase J ─── 里程碑日志可视化（1 Task）
  └── J.1: SurvivalChallengeMonitor compactLine 显示里程碑状态

Phase K ─── 挖矿知识连接（1 Task）
  └── K.1: 添加 mining_patterns 查询 + 知识注入反射
```

---

## Phase H: 补全测试

### Task H.1: 自动配方生成测试

测试 `generateDefaultRecipeForAction` 的各动作分支。

**文件：**
- Create: `src/test/java/com/izimi/eagent/brainstem/scheduler/MetaSchedulerRecipeTest.java`

```java
package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.cortex.prefrontal.ReflexRecipe;
import com.izimi.eagent.hormonal.NeuroState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 MetaScheduler.generateDefaultRecipeForAction 的配方生成逻辑。
 * 使用反射调用 private 方法。
 */
class MetaSchedulerRecipeTest {

    @Test
    void attackRecipe() throws Exception {
        var recipe = invokeGenerate("attack_reflex", "attack");
        assertEquals("attack_reflex", recipe.reflexId());
        assertEquals(0.7, recipe.targetVector().ne(), 0.01);
        assertEquals(0.6, recipe.targetVector().da(), 0.01);
        assertEquals(0.2, recipe.targetVector().serotonin(), 0.01);
        assertEquals(0.8, recipe.targetVector().ach(), 0.01);
    }

    @Test
    void digRecipe() throws Exception {
        var recipe = invokeGenerate("dig_reflex", "dig");
        assertEquals(0.4, recipe.targetVector().ne(), 0.01);
        assertEquals(0.5, recipe.targetVector().da(), 0.01);
    }

    @Test
    void fleeRecipe() throws Exception {
        var recipe = invokeGenerate("flee_reflex", "flee");
        assertEquals(0.8, recipe.targetVector().ne(), 0.01);
        assertEquals(5.0, recipe.safetyDistance(), 0.01);
    }

    @Test
    void eatRecipe() throws Exception {
        var recipe = invokeGenerate("eat_reflex", "eat");
        assertEquals(0.3, recipe.targetVector().ne(), 0.01);
        assertEquals(0.7, recipe.targetVector().serotonin(), 0.01);
    }

    @Test
    void unknownActionDefaultsToNeutral() throws Exception {
        var recipe = invokeGenerate("unknown_reflex", "dance");
        assertEquals(0.5, recipe.targetVector().ne(), 0.01);
        assertEquals(0.5, recipe.targetVector().da(), 0.01);
        assertEquals(0.5, recipe.targetVector().serotonin(), 0.01);
        assertEquals(0.5, recipe.targetVector().ach(), 0.01);
    }

    private ReflexRecipe invokeGenerate(String reflexId, String action) throws Exception {
        var method = MetaScheduler.class.getDeclaredMethod(
                "generateDefaultRecipeForAction", String.class, String.class);
        method.setAccessible(true);
        return (ReflexRecipe) method.invoke(null, reflexId, action);
    }
}
```

---

## Phase I: 运行时里程碑加载

### Task I.1: JsonUtil 添加 readListFromFileSafe

**文件：**
- Modify: `src/main/java/com/izimi/eagent/util/JsonUtil.java`

```java
/**
 * 从文件安全读取 JSON 数组。文件不存在或解析失败返回 null。
 */
public static List<Map<String, Object>> readListFromFileSafe(Path path) {
    if (!Files.exists(path)) return null;
    try {
        String content = Files.readString(path);
        var type = new TypeToken<List<Map<String, Object>>>(){}.getType();
        return GSON.fromJson(content, type);
    } catch (Exception e) {
        return null;
    }
}
```

### Task I.2: AICommand 从文件读取里程碑

**文件：**
- Modify: `src/main/java/com/izimi/eagent/command/AICommand.java`

在 `startChallenge()` 中，先尝试从 `eagent/config/challenge_milestones.json` 读取：
- 文件存在且解析成功 → 使用文件定义的里程碑
- 文件不存在 → 使用硬编码默认（fallback）

```java
// 尝试从文件加载里程碑
List<ChallengeMilestone> milestones = loadMilestonesFromFile();
if (milestones == null) {
    milestones = defaultMilestones();
    LOGGER.info("[挑战] 使用默认3-tier里程碑");
}

private static List<ChallengeMilestone> loadMilestonesFromFile() {
    Path path = FileUtil.getConfigDir().resolve("challenge_milestones.json");
    List<Map<String, Object>> raw = JsonUtil.readListFromFileSafe(path);
    if (raw == null) return null;
    // 逐项解析为 ChallengeMilestone
    List<ChallengeMilestone> result = new ArrayList<>();
    for (Map<String, Object> item : raw) {
        // 解析字段...(用 GSON 或手动)
    }
    return result;
}
```

---

## Phase J: 里程碑日志可视化

### Task J.1: compactLine 显示里程碑达标状态

**文件：**
- Modify: `src/main/java/com/izimi/eagent/brainstem/scheduler/SurvivalChallengeMonitor.java`

在 `compactLine()` 中添加里程碑状态符号：

```java
private static String compactLine(int day, BotInstance bot) {
    ...
    // 里程碑达标数
    int milestonesPassed = 0;
    if (milestoneTracker != null) {
        milestonesPassed = milestoneTracker.getPassedCount(bot.getBotId());
    }
    String star = milestonesPassed >= 3 ? "★" : milestonesPassed >= 1 ? "☆" : "";

    return String.format("%s[HP:%d 饿:%d 🪓:%d⚔:%d🛡:%d 🔥:%d 🛏:%d ⛏:%d 💎:%d 💀:%d 🤖:%d]%s",
            bot.getBotName(), hp, food,
            inv.totalPickaxes(), inv.totalSwords(), inv.shield,
            inv.torch, inv.bed, inv.ironIngot, inv.diamond, deaths, llm, star);
}
```

需要在 `ChallengeMilestoneTracker` 中添加：

```java
public int getPassedCount(UUID botId) {
    return completedMilestones.getOrDefault(botId, Set.of()).size();
}
```

---

## Phase K: 挖矿知识连接

### Task K.1: 添加 mining_patterns 查询 + 条件触发

**文件：**
- Modify: `src/main/java/com/izimi/eagent/cortex/planner/KnowledgeBase.java`

```java
/**
 * 获取高效挖矿模式定义。
 * 返回形如 { "optimal_y_levels": [-59,11], "branch_spacing": 3, ... } 的 map。
 */
public Map<String, Object> getMiningPattern(String name) {
    Map<String, Object> patterns = getGameRuleMap("mining_patterns");
    Object v = patterns.get(name);
    if (v instanceof Map) return (Map<String, Object>) v;
    return Map.of();
}
```

**反射 JSON 示例（人工预置或 REFLEX_CREATE 生成）：**

```json
{
  "reflex_id": "strip_mine_at_y11",
  "preconditions": [
    {"type": "concept", "key": "is_well_lit", "value": 1.0, "operator": "=="},
    {"type": "item", "key": "main_hand", "match": "pickaxe"}
  ],
  "steps": [
    {"action": "moveTo", "target": "y11_area"},
    {"action": "dig", "target": "forward"},
    {"action": "placeBlock", "target": "torch"}
  ]
}
```

---

## 验证

```bash
# H.1: 配方测试
./gradlew.bat test --tests "*MetaSchedulerRecipeTest*" -i

# 全量
./gradlew.bat test

# I: 运行时加载验证
# 启动 Minecraft 服务器，修改 eagent/config/challenge_milestones.json
# /ai challenge start 15 → 使用新配置

# J: 里程碑日志验证
# /ai challenge start 3 → 查看控制台输出含 ☆★ 符号
```
