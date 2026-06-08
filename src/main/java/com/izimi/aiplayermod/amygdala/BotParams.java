package com.izimi.aiplayermod.amygdala;

import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

import java.util.Random;

public class BotParams {
    private double alpha;
    private double beta;
    private double temperature;
    private long generatedAt;

    public BotParams() {}

    public BotParams(double alpha, double beta, double temperature) {
        this.alpha = alpha;
        this.beta = beta;
        this.temperature = temperature;
        this.generatedAt = System.currentTimeMillis();
    }

    public static BotParams load() {
        BotParams params = JsonUtil.readFromFileSafe(FileUtil.getBotParamsPath(), BotParams.class);
        if (params == null || params.alpha <= 0) {
            params = generate();
            params.save();
        }
        if (params.temperature <= 0) {
            params.temperature = 0.4;
        }
        return params;
    }

    public static BotParams generate() {
        Random rng = new Random();
        double alpha = clampNormal(0.3, 0.1, rng, 0.1, 0.6);
        double beta = clampNormal(0.01, 0.005, rng, 0.002, 0.03);
        double baseTemp = clampNormal(0.4, 0.15, rng, 0.2, 0.7);
        double betaFactor = 1.0 - (beta * 5);
        double temperature = Math.max(0.15, Math.min(0.8, baseTemp * betaFactor));
        return new BotParams(alpha, beta, temperature);
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
    public double getTemperature() { return temperature; }
    public long getGeneratedAt() { return generatedAt; }
}
