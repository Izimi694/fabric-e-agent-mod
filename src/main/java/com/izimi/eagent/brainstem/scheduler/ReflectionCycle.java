package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.amygdala.ConditionedReflex;
import com.izimi.eagent.bayesian.BayesianModule;
import com.izimi.eagent.brainstem.navigation.LandmarkCalibrator;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReflectionCycle {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    private static final int MAX_REFLECTIONS = 3;
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    public enum Result { RESOLVED, CALIBRATED, UNRESOLVED }

    private int consecutiveReflections = 0;

    public Result evaluate(ConditionedReflex conditionedReflex, BayesianModule bayesian,
                           LandmarkCalibrator calibrator, ServerPlayerEntity bot) {
        if (conditionedReflex == null) return Result.UNRESOLVED;

        DeviationCounter counter = conditionedReflex.getDeviationCounter();
        if (counter == null) return Result.UNRESOLVED;

        if (consecutiveReflections >= MAX_REFLECTIONS) {
            LOGGER.warn("[ReflectionCycle] 连续反思超过{}次，不再尝试", MAX_REFLECTIONS);
            consecutiveReflections = 0;
            return Result.UNRESOLVED;
        }
        consecutiveReflections++;

        if (counter.needsCalibration()) {
            if (calibrator != null && bot != null) {
                var nearest = calibrator.getNearestLandmark(bot.getBlockPos());
                if (nearest != null) {
                    calibrator.calibrate(bot, nearest);
                    counter.reset();
                    LOGGER.info("[ReflectionCycle] 标志校准完成，偏差已重置");
                    consecutiveReflections = 0;
                    return Result.CALIBRATED;
                } else {
                    calibrator.reportConfusion(bot);
                }
            }
        }

        int failures = conditionedReflex.getConsecutiveFailures();
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            conditionedReflex.resetConsecutiveFailures();
            LOGGER.info("[ReflectionCycle] 连续失败{}次已重置，LLM将重新评估", failures);
        }

        if (counter.needsCalibration()) {
            counter.reset();
            consecutiveReflections = 0;
            return Result.RESOLVED;
        }

        return Result.UNRESOLVED;
    }

    public void reset() {
        consecutiveReflections = 0;
    }
}
