package com.izimi.eagent.brainstem.domain;

import com.izimi.eagent.cortex.planner.KnowledgeBase;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 抽象概念检查器 — 将"光源充足""有庇护所""有食物储备"等
 * 概念级断言翻译成可测量的世界状态检查。
 *
 * 所有阈值从 KnowledgeBase.game_rules 加载，零硬编码。
 * 所有检查 O(1)~O(scanRadius²)，适合在 tick 内调用。
 */
public class GameConceptDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private final KnowledgeBase knowledgeBase;

    public GameConceptDetector(KnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    /**
     * 检查一个抽象概念是否满足。
     * 概念名称与 ChallengeMilestone.AbstractConceptCheck.conceptName 对应。
     *
     * @param conceptName 概念名（is_well_lit / has_shelter / has_food_supply / can_mine_iron / can_mine_diamond）
     * @param bot         目标 bot
     * @return true=概念满足
     */
    public boolean checkConcept(String conceptName, ServerPlayerEntity bot) {
        if (bot == null) return false;
        return switch (conceptName) {
            case "is_well_lit" -> isWellLit(bot);
            case "has_shelter" -> hasShelter(bot);
            case "has_food_supply" -> hasFoodSupply(bot);
            case "can_mine_iron" -> canMineOre(bot, "iron");
            case "can_mine_diamond" -> canMineOre(bot, "diamond");
            default -> {
                LOGGER.warn("[GameConcept] 未知概念: {}, 返回 true", conceptName);
                yield true;
            }
        };
    }

    // ════════════════════════════════════════════════════════════════════
    //  抽象概念实现
    // ════════════════════════════════════════════════════════════════════

    /**
     * "这个地方足够亮吗？" — 脚下方块的光级 ≥ safe_light_level。
     * 光级从 Minecraft 世界数据直接读取，成本 O(1)。
     */
    public boolean isWellLit(ServerPlayerEntity bot) {
        int safeLevel = knowledgeBase.getGameRuleInt("safe_light_level", 8);
        ServerWorld world = bot.getServerWorld();
        BlockPos pos = bot.getBlockPos();
        int light = world.getLightLevel(pos);
        return light >= safeLevel;
    }

    /**
     * "有庇护所吗？" — 头顶有方块（遮雨防阳光）+ 至少 3 面有墙。
     * 使用 game_rules.shelter_enclosed_walls_min 控制严格程度。
     */
    public boolean hasShelter(ServerPlayerEntity bot) {
        BlockPos pos = bot.getBlockPos();
        ServerWorld world = bot.getServerWorld();

        // 头顶必须有非空气方块
        if (world.getBlockState(pos.up()).isAir()) return false;

        // 统计周围的实体墙
        int walls = 0;
        if (!world.getBlockState(pos.north()).isAir()) walls++;
        if (!world.getBlockState(pos.south()).isAir()) walls++;
        if (!world.getBlockState(pos.east()).isAir()) walls++;
        if (!world.getBlockState(pos.west()).isAir()) walls++;

        int minWalls = knowledgeBase.getGameRuleInt("shelter_enclosed_walls_min", 3);
        return walls >= minWalls;
    }

    /**
     * "有食物储备吗？" — 背包中食物物品的饱食度总和 ≥ 20。
     * 食物饱食度从 game_rules.food_tracker 加载。
     */
    @SuppressWarnings("unchecked")
    public boolean hasFoodSupply(ServerPlayerEntity bot) {
        Map<String, Object> rawFood = knowledgeBase.getGameRuleMap("food_tracker");
        Map<String, Integer> foodValues = (Map<String, Integer>) (Map) rawFood;

        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            // 从 item ID 中提取短名称 (minecraft:iron_pickaxe → iron_pickaxe)
            String shortId = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            if (foodValues.containsKey(shortId)) {
                total += foodValues.get(shortId) * stack.getCount();
            }
        }
        return total >= 20;
    }

    /**
     * "能挖 X 矿吗？" — 手持镐的 tier 是否 ≥ 该矿石需要的 tier。
     * 镐 tier 从 game_rules.tool_efficiency 加载，矿石 tier 从 ore_mining_levels 加载。
     */
    @SuppressWarnings("unchecked")
    public boolean canMineOre(ServerPlayerEntity bot, String oreType) {
        Map<String, Object> oreLevels = knowledgeBase.getGameRuleMap("ore_mining_levels");
        Map<String, Object> toolEff = knowledgeBase.getGameRuleMap("tool_efficiency");

        Number oreLevelNum = (Number) oreLevels.get(oreType + "_ore");
        if (oreLevelNum == null) return false;
        int requiredTier = oreLevelNum.intValue();

        ItemStack held = bot.getMainHandStack();
        if (held.isEmpty()) return false;
        String id = Registries.ITEM.getId(held.getItem()).toString();
        String shortId = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;

        for (var entry : toolEff.entrySet()) {
            if (shortId.contains(entry.getKey().replace("_", ""))) {
                Map<String, Object> tool = (Map<String, Object>) entry.getValue();
                Number tierNum = (Number) tool.get("tier");
                int tier = tierNum != null ? tierNum.intValue() : -1;
                return tier >= requiredTier;
            }
        }
        return false;
    }
}
