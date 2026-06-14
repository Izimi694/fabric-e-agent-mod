package com.izimi.eagent.brainstem.navigation;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class LandmarkCalibrator {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    private static final double CALIBRATION_RANGE = 5.0;

    private final Map<String, BlockPos> landmarks = new HashMap<>();

    public void registerLandmark(String name, BlockPos pos) {
        landmarks.put(name, pos);
    }

    public BlockPos getNearestLandmark(BlockPos current) {
        if (current == null || landmarks.isEmpty()) return null;
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (var entry : landmarks.entrySet()) {
            double dist = current.getSquaredDistance(entry.getValue());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = entry.getValue();
            }
        }
        return nearest;
    }

    public BlockPos getNearestLandmarkWithinRange(BlockPos current, double range) {
        if (current == null || landmarks.isEmpty()) return null;
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (var entry : landmarks.entrySet()) {
            double dist = current.getSquaredDistance(entry.getValue());
            if (dist < nearestDist && dist <= range * range) {
                nearestDist = dist;
                nearest = entry.getValue();
            }
        }
        return nearest;
    }

    public void calibrate(ServerPlayerEntity bot, BlockPos target) {
        if (bot == null || target == null) return;
        Vec3d targetCenter = Vec3d.ofCenter(target);
        double dx = targetCenter.x - bot.getX();
        double dz = targetCenter.z - bot.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1.5) {
            resetPosition(bot, target);
            LOGGER.info("[LandmarkCalibrator] 已校准: {}", target.toShortString());
        }
    }

    private void resetPosition(ServerPlayerEntity bot, BlockPos target) {
        Vec3d center = Vec3d.ofCenter(target);
        bot.setPos(center.x, target.getY(), center.z);
        bot.setVelocity(0, 0, 0);
        bot.velocityModified = true;
    }

    public boolean reportConfusion(ServerPlayerEntity bot) {
        if (bot == null) return false;
        bot.sendMessage(Text.literal("§b[E-Agent] §e我有点迷茫了，请帮我确认一下位置。"));
        LOGGER.warn("[LandmarkCalibrator] 向玩家求助: bot={}", bot.getName().getString());
        return true;
    }
}
