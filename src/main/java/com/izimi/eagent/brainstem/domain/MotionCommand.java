package com.izimi.eagent.brainstem.domain;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public record MotionCommand(
        ServerPlayerEntity bot,
        String motionType,
        BlockPos targetPos,
        double x,
        double y,
        double z,
        boolean flag,
        double speed,
        String reason
) implements DomainCommand {
    @Override
    public String commandType() { return motionType; }

    public static MotionCommand moveTo(ServerPlayerEntity bot, BlockPos target, String reason) {
        return new MotionCommand(bot, "moveTo", target, 0, 0, 0, false, 0, reason);
    }

    public static MotionCommand jump(ServerPlayerEntity bot, String reason) {
        return new MotionCommand(bot, "jump", null, 0, 0, 0, false, 0, reason);
    }

    public static MotionCommand sprint(ServerPlayerEntity bot, boolean flag, String reason) {
        return new MotionCommand(bot, "sprint", null, 0, 0, 0, flag, 0, reason);
    }

    public static MotionCommand sneak(ServerPlayerEntity bot, boolean flag, String reason) {
        return new MotionCommand(bot, "sneak", null, 0, 0, 0, flag, 0, reason);
    }

    public static MotionCommand lookAt(ServerPlayerEntity bot, double x, double y, double z, String reason) {
        return new MotionCommand(bot, "lookAt", null, x, y, z, false, 0, reason);
    }
}
