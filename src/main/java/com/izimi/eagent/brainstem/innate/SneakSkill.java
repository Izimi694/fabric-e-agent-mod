package com.izimi.eagent.brainstem.innate;

import com.izimi.eagent.brainstem.skill.Skill;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Map;

public class SneakSkill extends Skill {
    private boolean isSneaking = false;

    public SneakSkill() {
        super("sneak", "潜行", "innate");
    }

    @Override
    public boolean canExecute(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
        return bot != null;
    }

    @Override
    public SkillResult execute(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
        isSneaking = !isSneaking;
        bot.setSneaking(isSneaking);
        return isSneaking
                ? SkillResult.success("开始潜行")
                : SkillResult.success("取消潜行");
    }
}
