package com.izimi.eagent.api;

import com.izimi.eagent.amygdala.character.BehaviorStats;
import com.izimi.eagent.brainstem.bot.BotManager;
import com.izimi.eagent.brainstem.skill.SkillManager;
import com.izimi.eagent.config.ModConfig;
import com.izimi.eagent.log.ExecutionLogger;

public interface WorldContext {
    BrainstemAPI brainstem();
    AmygdalaAPI amygdala();
    CortexAPI cortex();
    SkillManager skillManager();
    BehaviorStats behaviorStats();
    ModConfig modConfig();
    ExecutionLogger executionLogger();
    BotManager botManager();
    void setBotManager(BotManager botManager);
}
