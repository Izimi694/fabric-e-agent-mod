package com.izimi.eagent.brainstem.scheduler;

public record DriveState(
        double survivalUrgency,
        double taskUrgency,
        double socialUrgency,
        double curiosityUrgency,
        double cautiousUrgency
) {
    public static final DriveState ZERO = new DriveState(0, 0, 0, 0, 0);

    public double get(Perspective p) {
        return switch (p) {
            case SURVIVAL -> survivalUrgency;
            case TASK -> taskUrgency;
            case SOCIAL -> socialUrgency;
            case CURIOUS -> curiosityUrgency;
            case CAUTIOUS -> cautiousUrgency;
        };
    }

    /** 决策场整体压力：用于动态温度计算，濒死→冷，安逸→温 */
    public double pressure() {
        return survivalUrgency;
    }
}
