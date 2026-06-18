package com.izimi.eagent.amygdala;

import com.izimi.eagent.amygdala.learning.BehaviorEvent;
import com.izimi.eagent.bayesian.BayesianModule;
import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class NaiveBayesClassifier {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private static Map<String, String> actionDisplayMap = null;
    private static boolean loaded = false;

    static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        Map<String, Object> data = null;
        try {
            data = JsonUtil.readMapFromFileSafe(
                    FileUtil.getConfigDir().resolve("category_display.json"));
        } catch (Exception e) {
        }
        if (data != null && data.get("display_names") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> map = (Map<String, String>) data.get("display_names");
            actionDisplayMap = new HashMap<>(map);
        } else {
            actionDisplayMap = new HashMap<>();
        }
    }

    public record Classification(
            String bestAction,
            double confidence,
            boolean meetsThreshold
    ) {
        public String toTaskGoal() {
            NaiveBayesClassifier.ensureLoaded();
            String name = actionDisplayMap.get(bestAction);
            if (name != null) return name;
            return switch (bestAction) {
                case "dig" -> "挖矿";
                case "attack" -> "打怪";
                case "use_item" -> "使用物品";
                case "place_block" -> "建造";
                default -> bestAction;
            };
        }
    }

    private final ThresholdConfig thresholdConfig;
    private final BayesianModule bayesianModule;

    public NaiveBayesClassifier(ThresholdConfig thresholdConfig) {
        this(thresholdConfig, null);
    }

    public NaiveBayesClassifier(ThresholdConfig thresholdConfig, BayesianModule bayesianModule) {
        this.thresholdConfig = thresholdConfig;
        this.bayesianModule = bayesianModule;
    }

    public Classification classify(Map<String, Deque<BehaviorEvent>> playerWindows,
                                    FamiliarityTracker familiarity) {
        if (playerWindows.isEmpty()) return null;

        Map<String, Double> actionScores = new HashMap<>();
        double totalWeight = 0.0;
        int contributingPlayers = 0;

        for (var entry : playerWindows.entrySet()) {
            String playerName = entry.getKey();
            Deque<BehaviorEvent> events = entry.getValue();

            if (events.isEmpty()) continue;

            double playerWeight = familiarity.getWeight(playerName);
            contributingPlayers++;

            for (BehaviorEvent event : events) {
                double bayesianBoost = 0.0;
                if (bayesianModule != null) {
                    bayesianBoost = bayesianModule.predictSuccess("reflex_" + event.action(),
                            Collections.emptyList()) - 0.5;
                }
                actionScores.merge(event.action(), playerWeight * (1.0 + bayesianBoost), Double::sum);
                totalWeight += playerWeight * (1.0 + bayesianBoost);
            }
        }

        if (totalWeight == 0 || actionScores.isEmpty()) return null;

        String bestAction = null;
        double bestScore = 0.0;
        for (var entry : actionScores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                bestAction = entry.getKey();
            }
        }

        double confidence = bestScore / totalWeight;
        boolean meets = contributingPlayers >= thresholdConfig.minObservations
                && confidence >= thresholdConfig.minConfidence;

        return new Classification(bestAction, confidence, meets);
    }
}
