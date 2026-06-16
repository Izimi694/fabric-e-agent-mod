package com.izimi.eagent.simulation;

import com.izimi.eagent.brainstem.scheduler.ChallengeMilestone;
import com.izimi.eagent.brainstem.scheduler.SurvivalChallengeMonitor.InvSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 挑战里程碑测试 — 验证里程碑检查逻辑和 InvSummary 计数。
 * <p>
 * 不依赖 Minecraft 实体，纯模拟数据。
 */
class ChallengeMilestoneTest {

    // ════════════════════════════════════════════════════════════════════
    //  InvSummary 计数测试
    // ════════════════════════════════════════════════════════════════════

    @Test
    void invSummary_countsItems() {
        InvSummary inv = new InvSummary();
        inv.woodPick = 1;
        inv.ironPick = 2;
        inv.shield = 1;
        inv.ironIngot = 10;
        inv.diamond = 3;
        inv.torch = 32;
        inv.bed = 1;
        inv.craftingTable = 1;
        inv.bread = 5;

        assertEquals(1, inv.count("wooden_pickaxe"));
        assertEquals(0, inv.count("stone_pickaxe"));
        assertEquals(2, inv.count("iron_pickaxe"));
        assertEquals(1, inv.count("shield"));
        assertEquals(10, inv.count("iron_ingot"));
        assertEquals(3, inv.count("diamond"));
        assertEquals(32, inv.count("torch"));
        assertEquals(1, inv.count("bed"));
        assertEquals(1, inv.count("crafting_table"));
        assertEquals(5, inv.count("bread"));
        assertEquals(0, inv.count("nonexistent_item"));
    }

    @Test
    void invSummary_unknownItem_returnsZero() {
        InvSummary inv = new InvSummary();
        assertEquals(0, inv.count("diamond_pickaxe"));
        assertEquals(0, inv.count(""));
        assertEquals(0, inv.count("bedrock"));
    }

    // ════════════════════════════════════════════════════════════════════
    //  InvSummary 辅助方法测试
    // ════════════════════════════════════════════════════════════════════

    @Test
    void invSummary_totalPickaxes() {
        InvSummary inv = new InvSummary();
        assertEquals(0, inv.totalPickaxes());
        inv.woodPick = 1;
        assertEquals(1, inv.totalPickaxes());
        inv.ironPick = 2;
        assertEquals(3, inv.totalPickaxes());
    }

    @Test
    void invSummary_totalIronSet() {
        InvSummary inv = new InvSummary();
        assertEquals(0, inv.totalIronSet());
        inv.ironPick = 1;
        assertEquals(1, inv.totalIronSet());
        inv.ironSword = 1;
        inv.shield = 0;
        assertEquals(2, inv.totalIronSet());
    }

    @Test
    void invSummary_hasFullIronSet() {
        InvSummary inv = new InvSummary();
        assertFalse(inv.hasFullIronSet());
        inv.ironPick = 1;
        inv.ironSword = 1;
        inv.shield = 1;
        assertTrue(inv.hasFullIronSet());
    }

    @Test
    void invSummary_totalFood() {
        InvSummary inv = new InvSummary();
        assertEquals(0, inv.totalFood());
        inv.bread = 3;
        inv.cookedBeef = 2;
        assertEquals(5, inv.totalFood());
        inv.goldenApple = 1;
        assertEquals(6, inv.totalFood());
    }

    // ════════════════════════════════════════════════════════════════════
    //  ChallengeMilestone 数据模型测试
    // ════════════════════════════════════════════════════════════════════

    @Test
    void milestone_dataModel() {
        var itemCheck = new ChallengeMilestone.InventoryCheck("wooden_pickaxe", 1);
        assertEquals("wooden_pickaxe", itemCheck.itemId());
        assertEquals(1, itemCheck.minCount());

        var blockCheck = new ChallengeMilestone.BlockCheck("crafting_table", 10);
        assertEquals("crafting_table", blockCheck.blockId());
        assertEquals(10, blockCheck.maxDistance());

        var reflexCheck = new ChallengeMilestone.ReflexCheck("mine_iron", 40);
        assertEquals("mine_iron", reflexCheck.reflexPrefix());
        assertEquals(40, reflexCheck.minProficiency());

        var conceptCheck = new ChallengeMilestone.AbstractConceptCheck("is_well_lit", true);
        assertEquals("is_well_lit", conceptCheck.conceptName());
        assertTrue(conceptCheck.expectedValue());
    }

    @Test
    void milestone_fullRecord() {
        var milestone = new ChallengeMilestone(
                1, 1, "石器时代", true,
                "制作工作台，获取食物，制作床",
                List.of(
                        new ChallengeMilestone.InventoryCheck("crafting_table", 1),
                        new ChallengeMilestone.InventoryCheck("bed", 1)
                ),
                List.of(),
                List.of(
                        new ChallengeMilestone.ReflexCheck("craft_crafting_table", 30)
                ),
                List.of(
                        new ChallengeMilestone.AbstractConceptCheck("is_well_lit", true)
                ),
                Map.of("torch", 2, "coal", 5),
                100
        );

        assertEquals(1, milestone.fromDay());
        assertEquals(1, milestone.toDay());
        assertEquals("石器时代", milestone.tierName());
        assertTrue(milestone.obligatory());
        assertEquals(2, milestone.requiredItems().size());
        assertEquals(1, milestone.requiredReflexes().size());
        assertEquals(1, milestone.conceptChecks().size());
        assertEquals(2, milestone.bonusItems().size());
        assertEquals(100, milestone.scoreOnComplete());
    }
}
