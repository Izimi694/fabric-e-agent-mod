package com.izimi.eagent.brainstem.scheduler;

import java.util.List;
import java.util.Map;

/**
 * 挑战里程碑 — 定义 Bot 在特定游戏日内必须达成的目标。
 * 加载自 challenge_milestones.json。
 *
 * @param fromDay        起始日（含）
 * @param toDay          结束日（含）
 * @param tierName       时代名称，如"石器时代""铁器时代"
 * @param obligatory     true=必修（到期未过即判定挑战失败罚分），false=选修（额外加分）
 * @param description    人类可读描述
 * @param requiredItems  背包中必须持有的物品及最少数量
 * @param requiredNearby 附近必须存在的方块
 * @param requiredReflexes 必须已固化的反射（前缀匹配）
 * @param conceptChecks  抽象概念检查（is_well_lit 等）
 * @param bonusItems     Bonus 物品及其额外分值
 * @param scoreOnComplete 达标得分
 */
public record ChallengeMilestone(
        int fromDay,
        int toDay,
        String tierName,
        boolean obligatory,
        String description,
        List<InventoryCheck> requiredItems,
        List<BlockCheck> requiredNearby,
        List<ReflexCheck> requiredReflexes,
        List<AbstractConceptCheck> conceptChecks,
        Map<String, Integer> bonusItems,
        int scoreOnComplete
) {

    public record InventoryCheck(String itemId, int minCount) {}

    public record BlockCheck(String blockId, int maxDistance) {}

    public record ReflexCheck(String reflexPrefix, int minProficiency) {}

    public record AbstractConceptCheck(String conceptName, boolean expectedValue) {}
}
