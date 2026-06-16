package com.izimi.eagent.brainstem.adapter;

import com.izimi.eagent.hormonal.HormonalSystem;
import com.izimi.eagent.brainstem.scheduler.UrgencyClassifier;
import net.minecraft.server.network.ServerPlayerEntity;

public class TemporalScaler {

    private float globalSpeed = 1.0f;
    private UrgencyClassifier classifier;
    private UrgencyClassifier.UrgencyLabel lastLabel = UrgencyClassifier.UrgencyLabel.NORMAL;

    public TemporalScaler() {
        this.classifier = new UrgencyClassifier();
    }

    public TemporalScaler(UrgencyClassifier classifier) {
        this.classifier = classifier;
    }

    public void update(HormonalSystem hormones, ServerPlayerEntity bot, int ticksInState) {
        update(hormones, bot, ticksInState, 0.0);
    }

    /**
     * 统一 update: 连续公式(激素+压力)设定 globalSpeed, 保留离散 label 供 getter.
     * @param pressure 决策场压力, 来自 DriveState.pressure()
     */
    public void update(HormonalSystem hormones, ServerPlayerEntity bot, int ticksInState, double pressure) {
        globalSpeed = (float) computeTimeScale(hormones, pressure);
        UrgencyClassifier.UrgencyLabel label = classifier.classify(hormones, bot, ticksInState);
        lastLabel = label;
    }

/**
 * 直接从激素 4 维向量计算时间缩放 (连续值, 替代 UrgencyClassifier 的离散 5 档).
 *
 * NE↑ (警觉/恐惧) → scale 增大 → 主观时间变慢 → 紧迫动作更有价值
 * DA↑ (奖赏/活力) → scale 减小 → 主观时间变快 → 容忍更长任务
 * 5-HT↑ (情境钝化) → scale 小幅增大 → 行为抑制趋强
 *
 * @return 时间缩放因子, 范围 [0.5, 2.0]
 */
@Deprecated
public static double computeTimeScale(HormonalSystem h) {
    return computeTimeScale(h, 0.0);
}

/**
 * 统一时间缩放：激素 + 环境压力.
 *
 * @param pressure 决策场整体压力 (0=安逸, 1=濒死), 来自 DriveState.pressure()
 * @return 时间缩放因子, 范围 [0.5, 2.0]
 */
public static double computeTimeScale(HormonalSystem h, double pressure) {
    double ne = (h != null) ? h.getNE() : 0.1;
    double da = (h != null) ? h.getDA() : 0.2;
    double ser = (h != null) ? h.getSerotonin() : 0.3;
    double hormonalScale = 0.5 + ne * 0.8 + (1.0 - da) * 0.3 + ser * 0.15;

    double p = Math.max(0, Math.min(1.0, pressure));
    double pressureMod = 1.0 + p * 0.5;

    return Math.max(0.5, Math.min(2.0, hormonalScale * pressureMod));
}

public int scaleDuration(int baseTicks) {
        return Math.max(1, Math.round(baseTicks / globalSpeed));
    }

    public double scaleVelocity(double baseVelocity) {
        return baseVelocity * globalSpeed;
    }

    public float getSpeed() {
        return globalSpeed;
    }

    public UrgencyClassifier.UrgencyLabel getLastLabel() {
        return lastLabel;
    }
}
