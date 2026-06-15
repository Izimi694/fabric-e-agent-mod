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
        UrgencyClassifier.UrgencyLabel label = classifier.classify(hormones, bot, ticksInState);
        lastLabel = label;

        switch (label) {
            case CRITICAL -> globalSpeed = 2.0f;
            case HIGH     -> globalSpeed = 1.5f;
            case NORMAL   -> globalSpeed = 1.0f;
            case LOW      -> globalSpeed = 0.8f;
            case OBSERVE  -> globalSpeed = 0.5f;
        }
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
    public static double computeTimeScale(HormonalSystem h) {
        if (h == null) return 1.0;
        double ne = h.getNE();
        double da = h.getDA();
        double ser = h.getSerotonin();
        double scale = 0.5 + ne * 0.8 + (1.0 - da) * 0.3 + ser * 0.15;
        return Math.max(0.5, Math.min(2.0, scale));
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
