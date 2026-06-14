package com.izimi.eagent.brainstem.innate;

import com.izimi.eagent.brainstem.skill.Skill;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.Map;

public class JumpSkill extends Skill {
    private int cooldown = 0;
    private static final int COOLDOWN_TICKS = 5;

    public JumpSkill() {
        super("jump", "跳跃", "innate");
    }

    @Override
    public boolean canExecute(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
        return bot != null && bot.isOnGround();
    }

    @Override
    public SkillResult execute(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
        if (!bot.isOnGround()) {
            return SkillResult.fail("不在站立状态");
        }
        if (cooldown > 0) {
            cooldown--;
            return SkillResult.partial(0.5, "跳跃冷却中");
        }
        bot.jump();
        cooldown = COOLDOWN_TICKS;
        return SkillResult.success("跳跃");
    }
}
