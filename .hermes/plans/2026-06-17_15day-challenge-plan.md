# 15 日生存挑战 & 抽象概念能力 — 实施计划

> **For Hermes:** Direct execution — user is working in VSCode.

**目标：** 重写挑战系统，支持 15 天递进式目标（Day 1 木制工作台→食物→床；Day 2-3 铁装备→盾牌；Day 4+ 建房子、耕地、钻石），同时解决「光源足够=怪物少」等抽象概念在系统中如何编码的问题。

**架构：** 三层扩展 — (1) 挑战里程碑追踪系统加入 `SurvivalChallengeMonitor`，(2) `KnowledgeBase` 新增游戏规则段（game_rules），(3) 新测试类 `ChallengeMilestoneTest` 验证每日达标逻辑。

**技术栈：** Java 21, JUnit 5, 现有 EAgent 代码库

---

## 总体路线图

```
Phase A ─── 挑战数据结构 + KnowledgeBase 游戏规则（2 Tasks）
  ├── A.1: ChallengeMilestone 定义 + 15天目标JSON
  ├── A.2: KnowledgeBase 新增 game_rules 段（光源/怪物刷新/工具效率）
  └── A.3: GameConceptDetector — 抽象概念→可测量检查器

Phase B ─── SurvivalChallengeMonitor 扩展（3 Tasks）
  ├── B.1: 背包跟踪增加 15+ 物品类型（工作台/床/盾牌/火把/铜矿等）
  ├── B.2: 每日里程碑检查 + 达标日志
  └── B.3: 新评分系统（存活天数 × 10 + 里程碑分数 − 死亡 × 200）

Phase C ─── 测试 & 文档（3 Tasks）
  ├── C.1: ChallengeMilestoneTest — 里程碑检查单元测试
  ├── C.2: ChallengeScenarios — 15个递进式决策场景
  └── C.3: ARCHITECTURE.md 文档更新
```

---

## 关于「抽象概念怎么办」的设计决策

### 问题：光源足够会减少怪物刷新 — 如何编码？

**Minecraft 事实：** 光级 0-15，怪物在光级 ≤ 7 处生成。这是**物理规则**，不是抽象概念。

**系统中的三层解决方案：**

| 层 | 作用 | 成本 |
|:--:|------|:---:|
| **KnowledgeBase game_rules** | 硬编码游戏知识（光级阈值/工具效率/食物饱食度） | 0 |
| **BayesianModule + CorrelationDetector** | 观察学习 — P(被攻击\|光级<7) 逐渐升高 | 0 |
| **OneShotAlarmSystem + 先验** | 玩家说「暗处危险」→ 标记暗处 | 0 |

**具体实现：**

```
KnowledgeBase.game_rules.json:
{
  "monster_spawn_light_max": 7,
  "safe_light_level": 8,
  "torch_light_level": 14,
  "shelter_enclosed_walls_min": 4,
  "shelter_roof_required": true,
  "tool_efficiency": {
    "wooden_pickaxe": {"base_speed": 2.0, "can_mine": ["stone", "coal", "copper"]},
    "stone_pickaxe": {"base_speed": 4.0, "can_mine": ["stone", "coal", "copper", "iron"]},
    "iron_pickaxe": {"base_speed": 6.0, "can_mine": ["stone", "coal", "copper", "iron", "diamond"]},
    "diamond_pickaxe": {"base_speed": 8.0, "can_mine": ["stone", "coal", "copper", "iron", "diamond", "obsidian"]}
  }
}
```

**应用方式：**

```java
// GameConceptDetector — 把抽象概念翻译成可检查的断言
public class GameConceptDetector {
    // "这个地方足够亮吗？" → 扫描 8格范围内的光级
    public static boolean isWellLit(ServerPlayerEntity bot, int radius) {
        int safeLevel = KnowledgeBase.getGameRule("safe_light_level", 8);
        for (BlockPos pos : scanRadius(bot.getBlockPos(), radius)) {
            if (bot.getServerWorld().getLightLevel(pos) < safeLevel) return false;
        }
        return true;
    }

    // "有庇护所吗？" → 头顶有非空气方块 + 周围有墙
    public static boolean hasShelter(ServerPlayerEntity bot) {
        BlockPos pos = bot.getBlockPos();
        return !bot.getServerWorld().getBlockState(pos.up()).isAir();
    }
}
```

这些检查器作为 `preconditions` 挂载到 `ConditionedReflex` 的 `preconditions` JSON 段：

```json
{
  "reflex_id": "place_torch_when_dark",
  "preconditions": [
    {"type": "concept", "key": "is_well_lit", "operator": "==", "value": false}
  ],
  "steps": [
    {"action": "equipItem", "target": "torch"},
    {"action": "placeBlock", "target": "wall"}
  ]
}
```

---

## Phase A: 数据结构 + 游戏规则

### Task A.1: ChallengeMilestone 定义 + 15天目标JSON

**目标：** 创建 15 天递进式里程碑的数据结构

**文件：**
- Create: `src/main/java/com/izimi/eagent/brainstem/scheduler/ChallengeMilestone.java`（数据模型）
- Create: `src/main/resources/challenge_milestones.json`（15天目标定义）

**ChallengeMilestone.java：**

```java
package com.izimi.eagent.brainstem.scheduler;

import java.util.List;
import java.util.Map;

public record ChallengeMilestone(
    int fromDay,           // 起始日（含）
    int toDay,             // 结束日（含）
    String tierName,       // "石器时代" / "铁器时代" / "钻石时代"
    boolean obligatory,    // true=必修（不过即死），false=选修（bonus）
    String description,    // "制作工作台并合成石镐"
    List<InventoryCheck> requiredItems,  // 背包中必须有
    List<BlockCheck> requiredNearby,     // 附近必须存在
    List<ReflexCheck> requiredReflexes,  // 反射必须已固化
    List<AbstractConceptCheck> conceptChecks, // 抽象概念条件
    Map<String, Integer> bonusItems,     // bonus 物品及其分值
    int scoreOnComplete                   // 达标得分
) {
    public record InventoryCheck(String itemId, int minCount) {}
    public record BlockCheck(String blockId, int maxDistance) {}
    public record ReflexCheck(String reflexPrefix, int minProficiency) {}
    public record AbstractConceptCheck(String conceptName, boolean expectedValue) {}
}
```

**15 天目标 (challenge_milestones.json)：**

```json
[
  {
    "fromDay": 1, "toDay": 1,
    "tierName": "石器时代",
    "obligatory": true,
    "description": "制作工作台，获取食物，制作床",
    "requiredItems": [
      {"itemId": "crafting_table", "minCount": 1},
      {"itemId": "bed", "minCount": 1}
    ],
    "requiredNearby": [],
    "requiredReflexes": [
      {"reflexPrefix": "craft_crafting_table", "minProficiency": 30},
      {"reflexPrefix": "craft_bed", "minProficiency": 30}
    ],
    "conceptChecks": [
      {"conceptName": "is_well_lit", "expectedValue": true},
      {"conceptName": "has_shelter", "expectedValue": true}
    ],
    "bonusItems": {"torch": 2, "coal": 5, "copper_ingot": 3, "copper_pickaxe": 10},
    "scoreOnComplete": 100
  },
  {
    "fromDay": 2, "toDay": 3,
    "tierName": "铁器时代",
    "obligatory": true,
    "description": "挖铁矿石，制作铁镐/铁剑/盾牌",
    "requiredItems": [
      {"itemId": "iron_pickaxe", "minCount": 1},
      {"itemId": "iron_sword", "minCount": 1},
      {"itemId": "shield", "minCount": 1}
    ],
    "requiredNearby": [],
    "requiredReflexes": [
      {"reflexPrefix": "mine_iron", "minProficiency": 40},
      {"reflexPrefix": "smelt_iron", "minProficiency": 40},
      {"reflexPrefix": "craft_iron_pickaxe", "minProficiency": 30}
    ],
    "conceptChecks": [],
    "bonusItems": {"iron_ingot": 10, "iron_block": 5, "coal": 20},
    "scoreOnComplete": 200
  },
  {
    "fromDay": 4, "toDay": 15,
    "tierName": "建造与繁荣",
    "obligatory": false,
    "description": "建造房屋，开垦农田，采集钻石",
    "requiredItems": [
      {"itemId": "diamond_pickaxe", "minCount": 1}
    ],
    "requiredNearby": [
      {"blockId": "farmland", "maxDistance": 20},
      {"blockId": "chest", "maxDistance": 10}
    ],
    "requiredReflexes": [
      {"reflexPrefix": "build_house", "minProficiency": 30},
      {"reflexPrefix": "plant_wheat", "minProficiency": 30}
    ],
    "conceptChecks": [
      {"conceptName": "has_shelter", "expectedValue": true},
      {"conceptName": "has_food_supply", "expectedValue": true}
    ],
    "bonusItems": {
      "diamond": 20, "diamond_pickaxe": 5, "emerald": 30,
      "bread": 3, "cooked_beef": 5, "iron_block": 2, "golden_apple": 50
    },
    "scoreOnComplete": 300
  }
]
```

### Task A.2: KnowledgeBase 新增 game_rules 段

**目标：** 让 KnowledgeBase 能加载和查询游戏规则（光级阈值、工具效率等）

**文件：**
- Modify: `src/main/java/com/izimi/eagent/cortex/planner/KnowledgeBase.java`
- Create: `src/main/resources/game_rules.json`

**KnowledgeBase 新增方法：**

```java
// 新增字段
private final Map<String, Object> gameRules;

// 构造函数中增加 gameRules 加载
private KnowledgeBase(..., Map<String, Object> gameRules) {
    ...
    this.gameRules = gameRules;
}

// 新增查询方法
public int getGameRuleInt(String key, int fallback) {
    Object v = gameRules.get(key);
    if (v instanceof Number n) return n.intValue();
    return fallback;
}

public double getGameRuleDouble(String key, double fallback) {
    Object v = gameRules.get(key);
    if (v instanceof Number n) return n.doubleValue();
    return fallback;
}

public boolean getGameRuleBool(String key, boolean fallback) {
    Object v = gameRules.get(key);
    if (v instanceof Boolean b) return b;
    return fallback;
}

public Map<String, Object> getGameRuleMap(String key) {
    return (Map<String, Object>) gameRules.getOrDefault(key, Map.of());
}
```

**game_rules.json：**

```json
{
  "monster_spawn_light_max": 7,
  "safe_light_level": 8,
  "torch_light_level": 14,
  "shelter_enclosed_walls_min": 4,
  "shelter_roof_required": true,
  "food_tracker": {
    "bread": 5,
    "cooked_beef": 8,
    "apple": 4,
    "golden_apple": 20
  },
  "tool_efficiency": {
    "wooden_pickaxe": {"base_speed": 2.0, "tier": 0},
    "stone_pickaxe": {"base_speed": 4.0, "tier": 1},
    "iron_pickaxe": {"base_speed": 6.0, "tier": 2},
    "diamond_pickaxe": {"base_speed": 8.0, "tier": 3}
  },
  "ore_mining_levels": {
    "coal_ore": 0,
    "copper_ore": 0,
    "iron_ore": 1,
    "diamond_ore": 2,
    "obsidian": 3
  }
}
```

### Task A.3: GameConceptDetector — 抽象概念→可测量检查器

**目标：** 创建工具类，把"光源充足""有庇护所""有食物储备"等概念翻译成可测量检查

**文件：**
- Create: `src/main/java/com/izimi/eagent/brainstem/domain/GameConceptDetector.java`

```java
package com.izimi.eagent.brainstem.domain;

import com.izimi.eagent.cortex.planner.KnowledgeBase;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Map;

public class GameConceptDetector {

    private final KnowledgeBase knowledgeBase;

    public GameConceptDetector(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    /** 检查一个抽象概念是否满足 */
    public boolean checkConcept(String conceptName, ServerPlayerEntity bot) {
        return switch (conceptName) {
            case "is_well_lit" -> isWellLit(bot);
            case "has_shelter" -> hasShelter(bot);
            case "has_food_supply" -> hasFoodSupply(bot);
            case "can_mine_iron" -> canMineOre(bot, "iron");
            case "can_mine_diamond" -> canMineOre(bot, "diamond");
            default -> true; // 未知概念 → 宽松通过
        };
    }

    /** "这个地方足够亮吗？" — 脚下光级 ≥ safe_light_level */
    public boolean isWellLit(ServerPlayerEntity bot) {
        int safeLevel = knowledgeBase.getGameRuleInt("safe_light_level", 8);
        ServerWorld world = bot.getServerWorld();
        BlockPos pos = bot.getBlockPos();
        return world.getLightLevel(pos) >= safeLevel;
    }

    /** "有庇护所吗？" — 头顶有方块（遮雨） + 附近有墙 */
    public boolean hasShelter(ServerPlayerEntity bot) {
        BlockPos pos = bot.getBlockPos();
        ServerWorld world = bot.getServerWorld();
        // 头顶必须有非空气方块
        if (world.getBlockState(pos.up()).isAir()) return false;
        // 至少 3 面有墙（简化版）
        int walls = 0;
        if (!world.getBlockState(pos.north()).isAir()) walls++;
        if (!world.getBlockState(pos.south()).isAir()) walls++;
        if (!world.getBlockState(pos.east()).isAir()) walls++;
        if (!world.getBlockState(pos.west()).isAir()) walls++;
        return walls >= knowledgeBase.getGameRuleInt("shelter_enclosed_walls_min", 3);
    }

    /** "有食物储备吗？" — 背包中有食物物品 */
    public boolean hasFoodSupply(ServerPlayerEntity bot) {
        Map<String, Integer> foodValues = (Map) knowledgeBase.getGameRuleMap("food_tracker");
        int totalFood = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
            if (foodValues.containsKey(id)) {
                totalFood += foodValues.get(id) * stack.getCount();
            }
        }
        return totalFood >= 20; // 至少 20 点饱食度储备
    }

    /** "能挖铁/钻石吗？" — 手持镐的 tier 足够 */
    private boolean canMineOre(ServerPlayerEntity bot, String oreType) {
        Map<String, Object> oreLevels = knowledgeBase.getGameRuleMap("ore_mining_levels");
        Map<String, Object> toolEfficiency = knowledgeBase.getGameRuleMap("tool_efficiency");
        int requiredTier = (int) oreLevels.getOrDefault(oreType + "_ore", 0);

        ItemStack held = bot.getMainHandStack();
        if (held.isEmpty()) return false;
        String itemId = net.minecraft.registry.Registries.ITEM.getId(held.getItem()).toString();

        for (var entry : toolEfficiency.entrySet()) {
            if (itemId.contains(entry.getKey())) {
                Map<String, Object> tool = (Map) entry.getValue();
                int tier = (int) tool.getOrDefault("tier", -1);
                return tier >= requiredTier;
            }
        }
        return false;
    }
}
```

---

## Phase B: SurvivalChallengeMonitor 扩展

### Task B.1: 背包跟踪增加 15+ 物品类型

**目标：** `InvSummary` 从 6 种物品 → 扩展到 20+ 种（覆盖整个技术树）

**文件：**
- Modify: `src/main/java/com/izimi/eagent/brainstem/scheduler/SurvivalChallengeMonitor.java`

**InvSummary 扩展：**

```java
private static class InvSummary {
    // 工具 (6)
    int woodPick, stonePick, ironPick, diamondPick;
    int woodAxe, stoneAxe, ironAxe;
    int woodSword, stoneSword, ironSword, diamondSword;
    int shield, bow;

    // 矿物 (7)
    int coal, copperRaw, ironRaw, ironIngot, goldRaw, diamond, emerald;

    // 建筑/生存 (5)
    int craftingTable, bed, furnace, chest, torch;

    // 食物 (3)
    int bread, cookedBeef, apple;

    // 辅计算字段
    int totalPickaxes() {
        return woodPick + stonePick + ironPick + diamondPick;
    }
    boolean hasAnyPickaxe() { return totalPickaxes() > 0; }
    int totalIronSet() { return Math.min(ironPick, Math.min(ironSword, shield)); }
}
```

**summarizeInventory 扩展：** 添加对应的 `stack.isOf(Items.XXX)` 检查（约 40 行映射）。

### Task B.2: 每日里程碑检查

**目标：** 在每日快照中增加里程碑达标检查

**文件：**
- Modify: `src/main/java/com/izimi/eagent/brainstem/scheduler/SurvivalChallengeMonitor.java`
- Create: `src/main/java/com/izimi/eagent/brainstem/scheduler/ChallengeMilestoneTracker.java`

**ChallengeMilestoneTracker.java：**

```java
package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.brainstem.domain.GameConceptDetector;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;

public class ChallengeMilestoneTracker {

    private final List<ChallengeMilestone> milestones;
    private final GameConceptDetector conceptDetector;
    private final Map<UUID, Set<String>> completedMilestones = new HashMap<>();

    public ChallengeMilestoneTracker(List<ChallengeMilestone> milestones,
                                     GameConceptDetector conceptDetector) {
        this.milestones = milestones;
        this.conceptDetector = conceptDetector;
    }

    /** 在每天结束时调用，检查里程碑达标情况 */
    public MilestoneCheckResult checkDay(int day, UUID botId,
                                          ServerPlayerEntity entity,
                                          InvSummary inv,
                                          List<ConditionedReflex> reflexes) {
        Set<String> completed = completedMilestones.computeIfAbsent(botId, k -> new HashSet<>());
        int score = 0;
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (ChallengeMilestone m : milestones) {
            if (day < m.fromDay() || day > m.toDay()) continue;
            String key = m.tierName();
            if (completed.contains(key)) continue;

            boolean ok = checkMilestone(m, entity, inv, reflexes);
            if (ok) {
                completed.add(key);
                score += m.scoreOnComplete();
                passed.add(m.tierName() + " (" + m.description() + ")");
            } else if (m.obligatory() && day == m.toDay()) {
                failed.add(m.tierName() + " (" + m.description() + ") — 必修未达标");
            }
        }

        int bonusScore = computeBonus(milestones, inv);
        return new MilestoneCheckResult(score + bonusScore, passed, failed);
    }

    private boolean checkMilestone(ChallengeMilestone m, ServerPlayerEntity e,
                                    InvSummary inv, List<ConditionedReflex> reflexes) {
        // 1. 背包物品检查
        for (var req : m.requiredItems()) {
            if (inv.count(req.itemId()) < req.minCount()) return false;
        }

        // 2. 抽象概念检查
        for (var ck : m.conceptChecks()) {
            if (conceptDetector.checkConcept(ck.conceptName(), e) != ck.expectedValue())
                return false;
        }

        // 3. 反射检查
        for (var rc : m.requiredReflexes()) {
            boolean found = reflexes.stream().anyMatch(r ->
                r.getId().startsWith(rc.reflexPrefix()) &&
                r.getProficiency() >= rc.minProficiency());
            if (!found) return false;
        }

        return true;
    }

    // ... bonus 计算等

    public record MilestoneCheckResult(int score, List<String> passed, List<String> failed) {}
}
```

### Task B.3: 新评分系统

**目标：** 替换当前的 `iron×10 + diamond×50 - deaths×100` 为多层次评分

**文件：**
- Modify: `src/main/java/com/izimi/eagent/brainstem/scheduler/SurvivalChallengeMonitor.java`

**新评分公式：**

```java
// 挑战评分 = 存活分 + 里程碑分 + 额外资源分 − 死亡惩罚
public static int computeScore(int daysSurvived, int milestoneScore,
                                InvSummary inv, int deaths, int llmCalls) {
    int survivalScore = daysSurvived * 10;                     // 存活天数加权
    int resourceScore = inv.ironIngot * 2 + inv.diamond * 20 +
                        inv.emerald * 30 + inv.copperRaw * 1;  // 资源细化
    int deathPenalty = deaths * 200;                            // 死亡严重扣分
    int llmEfficiencyBonus = Math.max(0, 50 - llmCalls);       // LLM 少调奖分

    return Math.max(0, survivalScore + milestoneScore + resourceScore +
                       llmEfficiencyBonus - deathPenalty);
}
```

**每日快照格式更新：**

```java
// compactLine 增强版
String.format("%s[HP:%d/%d 饿:%d 工作台:%d 床:%d 盾:%d 💎:%d 💀:%d 🤖:%d ⭐:%d]",
    name, hp, maxHp, food, inv.craftingTable, inv.bed, inv.shield,
    inv.diamond, deaths, llm, score);

// 里程碑达标日志
LOGGER.info("[挑战] {} Day {} 里程碑达标: {}", botName, day, passed);
```

---

## Phase C: 测试 & 文档

### Task C.1: ChallengeMilestoneTest

**目标：** 验证里程碑检查逻辑

**文件：**
- Create: `src/test/java/com/izimi/eagent/simulation/ChallengeMilestoneTest.java`

**测试用例：**

| 测试 | 输入 | 预期 |
|:----:|------|:----:|
| Day1 背包无工作台 | inv.craftingTable=0 | 石器时代里程碑 FAIL |
| Day1 背包有工作台+床 | inv.craftingTable=1, inv.bed=1 | 石器时代里程碑 PASS |
| Day2 缺盾牌 | inv.ironPick=1, inv.ironSword=1, inv.shield=0 | 铁器时代里程碑 FAIL |
| Day3 全铁装备 | inv.ironPick=1, inv.ironSword=1, inv.shield=1 | 铁器时代里程碑 PASS |
| 光源检查 | lightLevel=3 | is_well_lit → false |
| 光源检查 | lightLevel=12 | is_well_lit → true |
| 庇护所检查 | 头顶空气 | has_shelter → false |
| 庇护所检查 | 头顶方块+3面墙 | has_shelter → true |

### Task C.2: ChallengeScenarios — 15个递进式决策场景

**目标：** 在现有 9 个 Scenarios 基础上增加到 15 个，覆盖每日挑战的决策点

**文件：**
- Modify: `src/test/java/com/izimi/eagent/simulation/Scenarios.java`

**新增场景：**

| 编号 | 场景 | 验证 |
|:----:|------|------|
| S10 | Day1: 木剑vs徒手撸树 | punch_tree 胜（无工具时徒手效率最高） |
| S11 | Day1: 先做工作台vs先做床 | crafting_table 胜（工作台是前置） |
| S12 | Day2: 挖煤vs下矿 | mine_coal 胜（先确保光源） |
| S13 | Day2: 石镐挖铁vs徒手挖土 | iron_pick 优先（资源升级） |
| S14 | Day3: 做盾牌vs做更多工具 | shield 胜（防御优先） |
| S15 | Day5: 挖钻石vs建房子 | build_house 胜（安全>财富） |
| S16 | Day7: 种田vs打猎 | plant_wheat 胜（可持续） |
| S17 | 光源检查：插火把vs盲目前进 | light_torch 胜（抽象概念决策） |
| S18 | 庇护所检查：天黑了vs继续挖矿 | flee_to_shelter 胜（抽象概念决策） |

### Task C.3: 文档更新

**目标：** 在 `ARCHITECTURE.md` 和 `DEVELOPMENT.md` 中反映新系统

**修改内容：**

1. `ARCHITECTURE.md` §26 SurvivalChallengeMonitor → 新评分公式 + 里程碑表
2. `DEVELOPMENT.md` 新增 Phase Challenge（15天挑战系统）
3. 新建 `CHALLENGE.md` — 挑战系统的完整设计文档（JSON 格式说明、评分规则、抽象概念列表）

---

## 验证策略

```bash
# 每轮
./gradlew.bat compileJava && ./gradlew.bat test

# 新增测试
./gradlew.bat test --tests "*ChallengeMilestoneTest*"
./gradlew.bat test --tests "*DecisionQualityTest*"
```

---

## 风险与缓解

| 风险 | 缓解 |
|:----:|------|
| InvSummary 扩展导致现有代码反序列化失败 | 使用新字段初始化为 0，向后兼容 |
| GameConceptDetector 依赖 Minecraft API | 测试中用 mock，生产中仅在 tick 内调用 |
| 里程碑检查开销（每 tick 扫描反射列表） | 里程碑检查只在 day 边界执行，不是每 tick |
| 抽象概念"has_food_supply"定义模糊 | 使用明确的阈值（≥20 饱食度）并文档化 |
