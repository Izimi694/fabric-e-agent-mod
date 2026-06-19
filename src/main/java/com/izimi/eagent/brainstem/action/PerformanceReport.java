package com.izimi.eagent.brainstem.action;

import java.util.List;

public record PerformanceReport(
    List<ResourceTrend> resourceTrends,
    List<FailureTrend> failureTrends,
    VitalSigns vitalSigns,
    List<String> anomalies,
    EnvironmentStats environment,
    List<GoalAdoption> adoptions,
    long ticksSinceLastLLM
) {
    public record ResourceTrend(String key, int count, int delta, int windowTicks) {
        public ResourceTrend {
            windowTicks = Math.max(1, windowTicks);
            count = Math.max(0, count);
        }
    }

    public record FailureTrend(String domain, double rate, double deltaRate) {
        public FailureTrend {
            rate = clamp01(rate);
            deltaRate = clamp01(deltaRate);
        }
    }

    public record VitalSigns(double health, double hunger, double armor) {
        public VitalSigns {
            health = clamp01(health);
            hunger = clamp01(hunger);
            armor = clamp01(armor);
        }
    }

    public record EnvironmentStats(double controllableIndex, double tps) {
        public EnvironmentStats {
            controllableIndex = clamp01(controllableIndex);
            tps = Math.max(0, tps);
        }
    }

    /** 早期预警阈值: 资源 delta < 此值 且 elapsed > 120s → 提前唤醒 LLM */
    public static final int EARLY_WARN_RESOURCE_DELTA = -3;
    /** 早期预警阈值: 失败率 > 此值 且连续失败 > 此值 → 提前唤醒 LLM */
    public static final double EARLY_WARN_FAILURE_RATE = 0.6;
    public static final int EARLY_WARN_CONSECUTIVE_FAILURES = 3;
    /** LLM 调用的最小间隔 (ticks), 防止网络风暴 */
    public static final int LLM_COOLDOWN_TICKS = 2400; // 120s

    public boolean requiresEarlyLLM() {
        boolean resourceCrash = resourceTrends.stream()
            .anyMatch(t -> t.delta() <= EARLY_WARN_RESOURCE_DELTA
                && ticksSinceLastLLM >= LLM_COOLDOWN_TICKS);
        boolean failureSpike = failureTrends.stream()
            .anyMatch(t -> t.rate() >= EARLY_WARN_FAILURE_RATE
                && t.deltaRate() > 0
                && ticksSinceLastLLM >= LLM_COOLDOWN_TICKS);
        return resourceCrash || failureSpike;
    }

    public static PerformanceReport empty() {
        return new PerformanceReport(
            List.of(), List.of(),
            new VitalSigns(1.0, 1.0, 1.0),
            List.of(),
            new EnvironmentStats(0, 20),
            List.of(),
            0
        );
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
