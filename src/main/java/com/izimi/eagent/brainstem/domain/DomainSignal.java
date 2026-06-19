package com.izimi.eagent.brainstem.domain;

public record DomainSignal(
    double movementIntensity,
    boolean isTurningSharp,
    boolean isInCombatRange,
    float bodyYaw,
    Integer combatTargetId
) {
    public static final DomainSignal NEUTRAL = new DomainSignal(0, false, false, 0, null);

    public DomainSignal withMovementIntensity(double v) {
        return new DomainSignal(Math.max(0, Math.min(1, v)), isTurningSharp, isInCombatRange, bodyYaw, combatTargetId);
    }

    public DomainSignal withCombatRange(boolean inRange) {
        return new DomainSignal(movementIntensity, isTurningSharp, inRange, bodyYaw, combatTargetId);
    }

    public DomainSignal withBodyYaw(float yaw) {
        return new DomainSignal(movementIntensity, isTurningSharp, isInCombatRange, yaw, combatTargetId);
    }
}
