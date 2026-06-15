package com.izimi.eagent.simulation;

import java.util.List;
import com.izimi.eagent.brainstem.scheduler.Perspective;
import com.izimi.eagent.cortex.api.HormonalPreset;

public final class Scenarios {

    public static List<Scenario> all() {
        return List.of(s1_survivalEarly(), s2_taskMidGame(), s3_nightLowHealth(), s4_nightFullGear(),
                s5_timeDivergence(), s6a_riskAversion(), s6b_neDrivenRisk(), s7a_resourceTool(), s7b_hungerFood(),
                s8_riskDiamond(), s9_legacyProduct());
    }

    /**
     * S1: 生存前期, 急需木头, 无工具.
     * 两种公式都应选择 punch_tree (直接撸树).
     */
    static Scenario s1_survivalEarly() {
        return new Scenario(
            "S1", "生存前期-撸树",
            Perspective.SURVIVAL,
            new HormonalPreset(0.4, 0.2, 0.2, 0.5, 0.3, 0.2, 0.5),
            0.7,
            List.of(
                new ReflexCandidate("punch_tree",      "用手撸树",  5, 0.50, 0.40, 0.70, 1.0, true),
                new ReflexCandidate("craft_stone_axe",  "做石斧",   12, 0.30, 0.15, 0.30, 1.0, true),
                new ReflexCandidate("gather_berries",   "采浆果",    2, 0.20, 0.30, 0.40, 1.0, true)
            ),
            "punch_tree", List.of("punch_tree")
        );
    }

    /**
     * S2: 生存中期, 有石斧, 需大量木头.
     * 两种公式都应选择 chop_with_axe (用斧伐木).
     */
    static Scenario s2_taskMidGame() {
        return new Scenario(
            "S2", "高效伐木",
            Perspective.TASK,
            new HormonalPreset(0.1, 0.5, 0.1, 0.2, 0.6, 0.1, 0.7),
            0.8,
            List.of(
                new ReflexCandidate("chop_with_axe", "用斧伐木", 3, 0.80, 0.90, 0.95, 1.0, true),
                new ReflexCandidate("punch_tree",    "用手撸树", 5, 0.40, 0.50, 0.60, 1.0, true)
            ),
            "chop_with_axe", List.of("chop_with_axe")
        );
    }

    /**
     * S3: 夜间, 无装备, 低血量.
     * 两种公式都应选择 flee_to_bed.
     * SURVIVAL 领域权重让 flee 的 margin 扩大 (wRisk 基线加成).
     */
    static Scenario s3_nightLowHealth() {
        return new Scenario(
            "S3", "夜间低血避险",
            Perspective.SURVIVAL,
            new HormonalPreset(0.7, 0.1, 0.5, 0.8, 0.2, 0.6, 0.3),
            0.5,
            List.of(
                new ReflexCandidate("flee_to_bed",     "回床睡觉",   3, 0.60, 0.80, 0.90, 1.0, true),
                new ReflexCandidate("continue_mining", "继续挖矿",   5, 0.50, 0.70, 0.70, 1.0, true),
                new ReflexCandidate("light_torches",   "插火把",     2, 0.40, 0.60, 0.70, 1.0, true)
            ),
            "flee_to_bed", List.of("flee_to_bed", "light_torches")
        );
    }

    /**
     * S4: 夜间, 钻石套满血.
     * 两种公式都应选择 continue_mining.
     * 即使 TASK 领域权重将成功因子拉至 0.5, mining 的后验优势仍保持排名.
     */
    static Scenario s4_nightFullGear() {
        return new Scenario(
            "S4", "夜战神装挖矿",
            Perspective.TASK,
            new HormonalPreset(0.0, 0.8, 0.0, 0.1, 0.8, 0.1, 0.8),
            0.9,
            List.of(
                new ReflexCandidate("continue_mining", "继续挖矿", 5, 0.80, 0.90, 0.95, 1.0, true),
                new ReflexCandidate("sleep_in_bed",    "回床睡觉", 3, 0.30, 0.30, 0.30, 1.0, true)
            ),
            "continue_mining", List.of("continue_mining")
        );
    }

    /**
     * S5: 长耗时任务 — 时间曲线产生翻转.
     * strip_mine(200原子,400s) product=0.536, quick_prospect(20原子,40s) product=0.416.
     * 旧公式(纯乘积): strip(0.536) > quick(0.416) → 选 strip(错).
     * 新公式(TASK): tScore(strip)=0.793, tScore(quick)=1.0, timeScale=0.655.
     *   strip = 0.4*0.793 + 0.5*0.536 + 0.1 = 0.685
     *   quick = 0.4*1.000 + 0.5*0.416 + 0.1 = 0.708
     *   新公式选 quick(对).
     */
    static Scenario s5_timeDivergence() {
        return new Scenario(
            "S5", "时间曲线翻转",
            Perspective.TASK,
            new HormonalPreset(0.05, 0.6, 0.1, 0.1, 0.8, 0.1, 0.7),
            0.85,
            List.of(
                new ReflexCandidate("strip_mine_thorough", "精细条带采矿", 200, 0.90, 0.85, 0.70, 1.0, true),
                new ReflexCandidate("quick_prospect",      "快速勘探",     20, 0.70, 0.70, 0.85, 1.0, true)
            ),
            "quick_prospect", List.of("quick_prospect")
        );
    }

    // ════════════════════════════════════════════════════════════════════
    //  S6: 风险权重测试 (SURVIVAL, wRisk=0.4)
    // ════════════════════════════════════════════════════════════════════

    /**
     * S6a: 低血量 + 夜间 → 风险规避.
     * flee_to_bed 高 riskScore(1.0) vs continue_mining 低 riskScore(0.3).
     * wRisk=0.4 使 riskScore 成为决胜因子:
     *   旧公式(纯乘积): mining(0.315) > flee(0.288) → 选 mining(错).
     *   新公式: flee = 0.2*tScore + 0.4*1.0 + 0.4*product
     *          mining = 0.2*tScore + 0.4*0.3 + 0.4*product
     *          risk 惩罚 (-0.28) 使 flee 胜出.
     */
    static Scenario s6a_riskAversion() {
        return new Scenario(
            "S6a", "低血夜间风险规避",
            Perspective.SURVIVAL,
            new HormonalPreset(0.7, 0.1, 0.5, 0.8, 0.2, 0.6, 0.3),
            0.5,
            List.of(
                new ReflexCandidate("flee_to_bed",     "回床睡觉",    3, 0.60, 0.80, 0.60, 1.0, true, 1.0, 1.0),
                new ReflexCandidate("continue_mining", "继续挖矿",    5, 0.50, 0.70, 0.90, 1.0, true, 0.3, 0.8)
            ),
            "flee_to_bed", List.of("flee_to_bed")
        );
    }

    /**
     * S6b: 高 NE → 风险厌恶 (即使血量正常).
     * mine_iron 更安全(riskScore=0.8) vs explore_cave 更冒险(riskScore=0.4).
     * 新公式惩罚探索倾向, 偏好安全采矿.
     */
    static Scenario s6b_neDrivenRisk() {
        return new Scenario(
            "S6b", "NE驱动风险厌恶",
            Perspective.SURVIVAL,
            new HormonalPreset(0.4, 0.2, 0.2, 0.5, 0.3, 0.2, 0.5),
            0.7,
            List.of(
                new ReflexCandidate("mine_iron",    "开采铁矿",  3, 0.70, 0.75, 0.85, 1.0, true, 0.8, 0.9),
                new ReflexCandidate("explore_cave", "探索洞穴",  5, 0.60, 0.70, 0.70, 1.0, true, 0.4, 0.7)
            ),
            "mine_iron", List.of("mine_iron")
        );
    }

    // ════════════════════════════════════════════════════════════════════
    //  S7: 资源效率测试 (SOCIAL 和 TASK 中 wResource>0)
    // ════════════════════════════════════════════════════════════════════

    /**
     * S7a: 钻石镐 vs 石镐 — 资源效率.
     * SOCIAL 领域 wResource=0.2.
     * craft_diamond_pick: 高耗时, 低 posterior, 低 resourceScore(昂贵).
     * craft_stone_pick:   短耗时, 高 posterior, 高 resourceScore(廉价).
     * 新公式中 resourceScore 放大了 stone_pick 的优势.
     */
    static Scenario s7a_resourceTool() {
        return new Scenario(
            "S7a", "资源效率-工具选择",
            Perspective.SOCIAL,
            new HormonalPreset(0.2, 0.3, 0.3, 0.4, 0.2, 0.4, 0.3),
            0.6,
            List.of(
                new ReflexCandidate("craft_diamond_pick", "做钻石镐", 15, 0.40, 0.30, 0.40, 1.0, true, 1.0, 0.3),
                new ReflexCandidate("craft_stone_pick",   "做石镐",    5, 0.70, 0.80, 0.80, 1.0, true, 1.0, 0.9)
            ),
            "craft_stone_pick", List.of("craft_stone_pick")
        );
    }

    /**
     * S7b: 饥饿食物选择 — 资源效率.
     * TASK 领域 wResource=0.1.
     * eat_cooked_beef:   高效率食物, resourceScore=0.8.
     * eat_cooked_chicken:低效率食物, resourceScore=0.5.
     * 新公式中 resourceScore 提供小幅但稳定的偏好.
     */
    static Scenario s7b_hungerFood() {
        return new Scenario(
            "S7b", "饥饿食物选择",
            Perspective.TASK,
            new HormonalPreset(0.1, 0.5, 0.1, 0.2, 0.6, 0.1, 0.7),
            0.8,
            List.of(
                new ReflexCandidate("eat_cooked_beef",   "吃牛排",    2, 0.80, 0.90, 0.90, 1.0, true, 1.0, 0.8),
                new ReflexCandidate("eat_cooked_chicken", "吃鸡肉",   2, 0.50, 0.50, 0.50, 1.0, true, 1.0, 0.5)
            ),
            "eat_cooked_beef", List.of("eat_cooked_beef")
        );
    }

    // ════════════════════════════════════════════════════════════════════
    //  S8: 风险校准 — 低血量钻石 vs 稳妥铁 (SURVIVAL wRisk=0.4)
    // ════════════════════════════════════════════════════════════════════

    /**
     * S8: 25% HP, 夜间, 钻石矿附近.
     * mine_diamond: posterior=0.75, riskScore=0.5 (中等风险)
     * mine_iron_safe: posterior=0.80, riskScore=0.85 (低风险)
     * SURVIVAL wRisk=0.4 — riskScore gap 0.35 → 0.14 penalty 使 iron 胜出.
     * 验证: 当风险可控时安全选择压倒高回报, 是"安全优先于任务"原则。
     * 若未来需要更进取, 可降低 wRisk 系数.
     */
    static Scenario s8_riskDiamond() {
        return new Scenario(
            "S8", "低血钻石vs稳妥铁",
            Perspective.SURVIVAL,
            new HormonalPreset(0.6, 0.2, 0.4, 0.7, 0.3, 0.5, 0.4),
            0.6,
            List.of(
                new ReflexCandidate("mine_diamond_risky", "挖钻石", 12, 0.75, 0.80, 0.75, 1.0, true, 0.5, 0.9),
                new ReflexCandidate("mine_iron_safe",     "挖铁矿",  8, 0.70, 0.80, 0.80, 1.0, true, 0.85, 0.7)
            ),
            "mine_iron_safe", List.of("mine_iron_safe")
        );
    }

    // ════════════════════════════════════════════════════════════════════
    //  S9: Legacy 模式验证 — 确保旧公式=纯乘积
    // ════════════════════════════════════════════════════════════════════

    /**
     * S9: Legacy 模式 = 纯乘积公式.
     * 所有候选者的 posterior 故意不同以产生唯一排名.
     * 旧系统 (OldScoringFormula) 选 product 最高者.
     * 新系统有 risk/resource 加成, 但在这个场景中 riskScore ≈ 1.0 (假怀高HP,无Danger).
     * 验证: 即使使用新系统, 当 risk/resource 无差异时排名仍然由 product 主导.
     */
    static Scenario s9_legacyProduct() {
        return new Scenario(
            "S9", "Legacy模式-纯乘积",
            Perspective.SURVIVAL,
            new HormonalPreset(0.1, 0.1, 0.1, 0.2, 0.1, 0.1, 0.9),
            0.8,
            List.of(
                new ReflexCandidate("high_product", "高乘积值", 5, 0.90, 0.95, 0.95, 1.0, true, 1.0, 1.0),
                new ReflexCandidate("low_product",  "低乘积值", 3, 0.30, 0.40, 0.50, 1.0, true, 1.0, 1.0)
            ),
            "high_product", List.of("high_product")
        );
    }
}
