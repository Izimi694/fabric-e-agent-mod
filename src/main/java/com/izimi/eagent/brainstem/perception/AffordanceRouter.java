package com.izimi.eagent.brainstem.perception;

import com.izimi.eagent.brainstem.action.BlendedAction;
import com.izimi.eagent.brainstem.perception.PerceptionSnapshot.DenseView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class AffordanceRouter {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    public static final double OFFSET_HIGH = 0.5;
    public static final double OFFSET_CRITICAL = 1.0;
    public static final double OFFSET_NORMAL = 0.0;
    public static final double OFFSET_LOW = -0.3;

    public static final double LOCK_BASE_TICKS = 10.0;
    public static final double LOCK_DISTANCE_DIVISOR = 2.0;
    public static final int LOCK_MAX_TICKS = 60;
    public static final double LOCK_BREAK_MULTIPLIER = 1.3;

    public static final double PRECISION_CRITICAL = 0.5;
    public static final double PRECISION_HIGH = 1.0;
    public static final double PRECISION_NORMAL = 2.0;
    public static final double PRECISION_LOW = 3.0;

    public static double precisionForTier(String tier) {
        return switch (tier) {
            case "CRITICAL" -> PRECISION_CRITICAL;
            case "HIGH" -> PRECISION_HIGH;
            case "NORMAL" -> PRECISION_NORMAL;
            case "LOW" -> PRECISION_LOW;
            default -> PRECISION_NORMAL;
        };
    }

    public record SortedCandidate(String targetType, double adjustedSalience, double urgencyOffset, String tier, String category) {
        public SortedCandidate(String targetType, double adjustedSalience, double urgencyOffset, String tier) {
            this(targetType, adjustedSalience, urgencyOffset, tier, null);
        }
    }

    private final Map<String, CommitLock> activeLocks = new HashMap<>();
    private int tickCounter = 0;

    static class CommitLock {
        final String category;
        final double urgency;
        final int expiryTick;
        CommitLock(String category, double urgency, int expiryTick) {
            this.category = category;
            this.urgency = urgency;
            this.expiryTick = expiryTick;
        }
        boolean isExpired(int currentTick) { return currentTick >= expiryTick; }
    }

    public List<SortedCandidate> route(List<SalienceMap.Candidate> candidates, DenseView denseView,
                                        Map<String, Integer> resourceCounts, boolean hasShelter) {
        tickCounter++;
        cleanupLocks();
        if (candidates == null || candidates.isEmpty()) return List.of();

        List<SortedCandidate> sorted = new ArrayList<>();
        for (SalienceMap.Candidate c : candidates) {
            double urgency = computeUrgency(c, denseView, resourceCounts, hasShelter);
            String tier = classifyTier(urgency, c, denseView);
            double offset = offsetForTier(tier);
            String category = c.category() != null ? c.category() : categoryFromTarget(c.targetType());

            if (tier.equals("CRITICAL")) {
                sorted.add(new SortedCandidate(c.targetType(), c.salience() + offset, offset, tier, category));
                continue;
            }

            if (tier.equals("LOW") && hasHigherTierCandidate(candidates, denseView, resourceCounts, hasShelter)) {
                continue;
            }

            CommitLock lock = activeLocks.get(category);
            if (lock != null && !lock.isExpired(tickCounter)) {
                if (urgency <= lock.urgency * LOCK_BREAK_MULTIPLIER) {
                    continue;
                }
            }

            int lockDuration = computeLockDuration(10.0);
            activeLocks.put(category, new CommitLock(category, urgency, tickCounter + lockDuration));

            double adjustedSalience = Math.max(0, Math.min(1, c.salience() + offset));
            sorted.add(new SortedCandidate(c.targetType(), adjustedSalience, offset, tier, category));
        }

        sorted.sort((a, b) -> Double.compare(b.adjustedSalience(), a.adjustedSalience()));
        if (LOGGER.isInfoEnabled() && !sorted.isEmpty()) {
            var top3 = sorted.stream().limit(3)
                .map(c -> c.targetType() + "[" + c.tier() + "]=" + String.format("%.3f", c.adjustedSalience()))
                .collect(Collectors.joining(", "));
            LOGGER.info("[AR] sorted {} candidates: {}", sorted.size(), top3);
        }
        return sorted;
    }

    double computeUrgency(SalienceMap.Candidate candidate, DenseView denseView,
                           Map<String, Integer> resourceCounts, boolean hasShelter) {
        double urgency = 0;
        if (denseView != null) {
            double healthThreat = (1 - denseView.health()) * 0.6;
            double hungerThreat = (1 - denseView.hunger()) * 0.3;
            urgency += healthThreat + hungerThreat;
            if (denseView.isUnderAttack()) urgency += 0.3;
        }
        double salienceFactor = 0;
        if (candidate != null) {
            salienceFactor = candidate.salience() * 0.2;
        }
        urgency = Math.min(1.0, urgency + salienceFactor);
        return Math.max(0, urgency);
    }

    String classifyTier(double urgency, SalienceMap.Candidate candidate, DenseView denseView) {
        if (denseView != null) {
            if (denseView.isUnderAttack() && "food".equals(candidate.targetType())) return "CRITICAL";
            if (denseView.health() < 0.3 && "food".equals(candidate.targetType())) return "CRITICAL";
        }
        if (urgency > 0.75) return "HIGH";
        if (urgency > 0.35) return "NORMAL";
        return "LOW";
    }

    static double offsetForTier(String tier) {
        return switch (tier) {
            case "CRITICAL" -> OFFSET_CRITICAL;
            case "HIGH" -> OFFSET_HIGH;
            case "NORMAL" -> OFFSET_NORMAL;
            case "LOW" -> OFFSET_LOW;
            default -> 0;
        };
    }

    boolean hasHigherTierCandidate(List<SalienceMap.Candidate> candidates, DenseView denseView,
                                    Map<String, Integer> resourceCounts, boolean hasShelter) {
        for (SalienceMap.Candidate c : candidates) {
            double u = computeUrgency(c, denseView, resourceCounts, hasShelter);
            String t = classifyTier(u, c, denseView);
            if ("NORMAL".equals(t) || "HIGH".equals(t) || "CRITICAL".equals(t)) return true;
        }
        return false;
    }

    void cleanupLocks() {
        activeLocks.entrySet().removeIf(e -> e.getValue().isExpired(tickCounter));
    }

    static int computeLockDuration(double distance) {
        return Math.min(LOCK_MAX_TICKS, (int)(LOCK_BASE_TICKS + distance / LOCK_DISTANCE_DIVISOR));
    }

    static String categoryFromTarget(String targetType) {
        if (targetType == null) return "unknown";
        String lower = targetType.toLowerCase();
        if (lower.contains("log") || lower.contains("wood")) return "wood";
        if (lower.contains("ore") || lower.contains("diamond") || lower.contains("gold")) return "ore";
        if (lower.contains("food") || lower.contains("apple") || lower.contains("bread")) return "food";
        if (lower.contains("stone") || lower.contains("cobblestone")) return "stone";
        if (lower.contains("dirt") || lower.contains("grass")) return "dirt";
        return "misc";
    }

    public void reset() {
        activeLocks.clear();
        tickCounter = 0;
    }

    public int activeLockCount() { return activeLocks.size(); }
}
