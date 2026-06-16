package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.brainstem.domain.GameConceptDetector;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 挑战里程碑追踪器 — 在每个游戏日结束时检查 Bot 是否达标。
 * 里程碑数据从 challenge_milestones.json 加载。
 * <p>
 * 设计原则：检查只在 day 边界执行（非每 tick），成本可忽略。
 */
public class ChallengeMilestoneTracker {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private final List<ChallengeMilestone> milestones;
    private final GameConceptDetector conceptDetector;
    private final Map<UUID, Set<String>> completedMilestones;
    private int totalMilestoneScore;

    public ChallengeMilestoneTracker(List<ChallengeMilestone> milestones,
                                     GameConceptDetector conceptDetector) {
        this.milestones = milestones;
        this.conceptDetector = conceptDetector;
        this.completedMilestones = new HashMap<>();
        this.totalMilestoneScore = 0;
    }

    /**
     * 在每日快照时调用，检查当天可达的里程碑。
     *
     * @param day     当前游戏日
     * @param botId  Bot 的 UUID
     * @param entity  Bot 的玩家实体
     * @param inv     当天的背包摘要
     * @return 检查结果（当天新获分数 + 达标/失败列表）
     */
    public MilestoneCheckResult checkDay(int day, UUID botId,
                                          ServerPlayerEntity entity,
                                          SurvivalChallengeMonitor.InvSummary inv) {
        Set<String> completed = completedMilestones.computeIfAbsent(botId, k -> new HashSet<>());
        int newScore = 0;
        List<String> passed = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (ChallengeMilestone m : milestones) {
            // 不在本日范围或已达标
            if (day < m.fromDay() || day > m.toDay()) continue;
            String key = m.tierName() + "_" + m.fromDay() + "-" + m.toDay();
            if (completed.contains(key)) continue;

            boolean ok = checkSingle(m, entity, inv);
            if (ok) {
                completed.add(key);
                newScore += m.scoreOnComplete();
                // + bonus items
                int bonus = computeBonus(m, inv);
                newScore += bonus;
                totalMilestoneScore += m.scoreOnComplete() + bonus;
                passed.add(m.tierName() + " (" + m.description() + ") +" + m.scoreOnComplete()
                        + (bonus > 0 ? " +" + bonus + " bonus" : ""));
            } else if (m.obligatory() && day >= m.toDay()) {
                failed.add("⚠ " + m.tierName() + " (" + m.description() + ") — 必修未达标");
            }
        }

        return new MilestoneCheckResult(newScore, passed, failed);
    }

    /** 检查单个里程碑 */
    private boolean checkSingle(ChallengeMilestone m, ServerPlayerEntity entity,
                                 SurvivalChallengeMonitor.InvSummary inv) {
        // 1. 背包物品检查
        for (var req : m.requiredItems()) {
            int count = inv.count(req.itemId());
            if (count < req.minCount()) return false;
        }

        // 2. 抽象概念检查
        for (var ck : m.conceptChecks()) {
            if (conceptDetector.checkConcept(ck.conceptName(), entity) != ck.expectedValue()) {
                return false;
            }
        }

        return true;
    }

    /** 计算 Bonus 物品得分（非强制，有就给分） */
    private int computeBonus(ChallengeMilestone m, SurvivalChallengeMonitor.InvSummary inv) {
        int bonus = 0;
        for (var entry : m.bonusItems().entrySet()) {
            String itemId = entry.getKey();
            int pointsPerUnit = entry.getValue();
            int count = inv.count(itemId);
            bonus += count * pointsPerUnit;
        }
        return bonus;
    }

    public int getTotalMilestoneScore() {
        return totalMilestoneScore;
    }

    /** 查询指定 Bot 已达标里程碑数量 */
    public int getPassedCount(UUID botId) {
        return completedMilestones.getOrDefault(botId, java.util.Collections.emptySet()).size();
    }

    /** 重置追踪器（挑战开始时调用） */
    public void reset() {
        completedMilestones.clear();
        totalMilestoneScore = 0;
    }

    public record MilestoneCheckResult(int newScore, List<String> passed, List<String> failed) {}
}
