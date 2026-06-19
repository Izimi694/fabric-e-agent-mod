package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.brainstem.perception.PerceptionSnapshot;

import java.util.Map;

public class ConditionEvaluator {

    public enum ConditionType {
        HEALTH_BELOW,
        HUNGER_BELOW,
        ARMOR_BELOW,
        UNDER_ATTACK,
        HAS_SHELTER,
        MOB_THREAT,
        RESOURCE_ABUNDANT
    }

    public record Condition(ConditionType type, double threshold, String target) {}

    public boolean evaluate(Condition condition, PerceptionSnapshot snapshot, Map<String, Integer> resourceCounts) {
        if (condition == null || snapshot == null) return false;
        return switch (condition.type()) {
            case HEALTH_BELOW -> snapshot.dense().health() < condition.threshold();
            case HUNGER_BELOW -> snapshot.dense().hunger() < condition.threshold();
            case ARMOR_BELOW -> snapshot.dense().armor() < condition.threshold();
            case UNDER_ATTACK -> snapshot.dense().isUnderAttack();
            case HAS_SHELTER -> snapshot.dense().hasShelterNearby();
            case MOB_THREAT -> {
                if (condition.target() != null) {
                    yield snapshot.dense().mobCount() > condition.threshold();
                }
                yield false;
            }
            case RESOURCE_ABUNDANT -> {
                if (condition.target() != null && resourceCounts != null) {
                    int count = resourceCounts.getOrDefault(condition.target().toLowerCase(), 0);
                    yield count > condition.threshold();
                }
                yield false;
            }
        };
    }
}
