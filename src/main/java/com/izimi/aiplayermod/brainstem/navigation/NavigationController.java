package com.izimi.aiplayermod.brainstem.navigation;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class NavigationController {
    private static final double ARRIVAL_THRESHOLD = 1.5;

    public boolean navigateTo(ServerPlayerEntity bot, BlockPos target) {
        if (bot == null || target == null) return false;

        double distance = bot.getPos().distanceTo(Vec3d.ofCenter(target));
        if (distance < ARRIVAL_THRESHOLD) {
            return true;
        }

        GreedyNavigator navigator = new GreedyNavigator(bot.getWorld());
        BlockPos bestStep = navigator.getBestStep(bot.getBlockPos(), target);

        if (bestStep != null) {
            moveToward(bot, bestStep);
        }

        return false;
    }

    private void moveToward(ServerPlayerEntity bot, BlockPos waypoint) {
        Vec3d direction = Vec3d.ofCenter(waypoint).subtract(bot.getPos());
        double horiDist = Math.sqrt(direction.x * direction.x + direction.z * direction.z);

        if (horiDist > 0.01) {
            float yaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90;
            bot.setYaw(yaw);
            bot.setHeadYaw(yaw);

            double speed = 0.3;

            Vec3d velocity = new Vec3d(
                    direction.x / horiDist * speed,
                    direction.y * 0.2,
                    direction.z / horiDist * speed
            );
            bot.setVelocity(velocity);
            bot.velocityModified = true;

            if (bot.isOnGround()) {
                if (waypoint.getY() > bot.getBlockY() || direction.y > 0.5) {
                    bot.jump();
                }
            }
        }
    }

    public void stopNavigation() {
    }

    public boolean isNavigating() {
        return false;
    }
}
