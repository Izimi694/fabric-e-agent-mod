package com.izimi.aiplayermod.amygdala;

import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

import java.util.Random;

public class BotParams {
    private double alpha;
    private double beta;
    private long generatedAt;

    public BotParams() {}

    public BotParams(double alpha, double beta) {
        this.alpha = alpha;
        this.beta = beta;
        this.generatedAt = System.currentTimeMillis();
    }

    public static BotParams load() {
        BotParams params = JsonUtil.readFromFileSafe(FileUtil.getBotParamsPath(), BotParams.class);
        if (params == null || params.alpha <= 0) {
            params = generate();
            params.save();
        }
        return params;
    }

    public static BotParams generate() {
        Random rng = new Random();
        double alpha = clampNormal(0.3, 0.1, rng, 0.1, 0.6);
        double beta = clampNormal(0.01, 0.005, rng, 0.002, 0.03);
        return new BotParams(alpha, beta);
    }

    private static double clampNormal(double mean, double std, Random rng, double min, double max) {
        double val = mean + rng.nextGaussian() * std;
        return Math.max(min, Math.min(max, val));
    }

    public void save() {
        JsonUtil.writeToFileSafeAtomic(FileUtil.getBotParamsPath(), this);
    }

    public double getAlpha() { return alpha; }
    public double getBeta() { return beta; }
    public long getGeneratedAt() { return generatedAt; }
}
