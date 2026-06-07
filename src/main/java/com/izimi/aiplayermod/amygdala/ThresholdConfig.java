package com.izimi.aiplayermod.amygdala;

import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

import java.nio.file.Path;

public class ThresholdConfig {

    public double minConfidence = 0.6;
    public int minObservations = 3;

    public static ThresholdConfig load() {
        ThresholdConfig config = JsonUtil.readFromFileSafe(getPath(), ThresholdConfig.class);
        if (config == null) {
            config = new ThresholdConfig();
            config.save();
        }
        return config;
    }

    public void save() {
        JsonUtil.writeToFileSafeAtomic(getPath(), this);
    }

    public static Path getPath() {
        return FileUtil.getThresholdsDir().resolve("thresholds.json");
    }
}
