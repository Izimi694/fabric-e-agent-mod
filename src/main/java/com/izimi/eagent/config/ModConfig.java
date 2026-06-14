package com.izimi.eagent.config;

import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;

public class ModConfig {
    public int defaultTaskTimeout = 300;
    public int explorationRange = 500;
    public double reflexThreshold = 0.8;
    public int reflexMinSuccesses = 3;
    public int memoryWindowDays = 3;
    public int stateSaveIntervalTicks = 200;
    public int logAnalysisInterval = 2000;
    public double behaviorWeight = 0.6;
    public double commandWeight = 0.3;
    public double chatWeight = 0.1;
    public int preferenceEvolutionThreshold = 10;
    public int maxMemorySummaryChars = 120;
    public int maxChatSlotChars = 80;

    public static ModConfig load() {
        ModConfig config = JsonUtil.readFromFileSafe(FileUtil.getConfigPath(), ModConfig.class);
        if (config == null) {
            config = new ModConfig();
            config.save();
        }
        return config;
    }

    public void save() {
        JsonUtil.writeToFileSafeAtomic(FileUtil.getConfigPath(), this);
    }
}
