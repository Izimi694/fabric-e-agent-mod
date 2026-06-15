package com.izimi.eagent.brainstem.navigation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.izimi.eagent.brainstem.bot.BotPlayer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class NavigationController {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    private static final double ARRIVAL_THRESHOLD = 1.5;
    private static final float FORWARD_SPEED = 0.5f;

    public boolean navigateTo(ServerPlayerEntity bot, BlockPos target) {
        if (bot == null || target == null) return false;

        double distance = bot.getPos().distanceTo(Vec3d.ofCenter(target));
        if (distance < ARRIVAL_THRESHOLD) {
            BotPlayer bp = BotPlayer.getByUUID(bot.getUuid());
            if (bp != null) bp.clearMoveInput();
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

            boolean shouldJump = bot.isOnGround()
                    && (waypoint.getY() > bot.getBlockY() || direction.y > 0.5);

            // Store input in BotPlayer for physics loop consumption
            BotPlayer bp = BotPlayer.getByUUID(bot.getUuid());
            if (bp != null) {
                bp.setMoveInput(FORWARD_SPEED, 0, shouldJump);
            }

            LOGGER.info("[Navigation] moveToward -> forward={}, jump={}, pos={}, onGround={}, y={}, waypoint={}",
                    FORWARD_SPEED, shouldJump, bot.getBlockPos(), bot.isOnGround(), String.format("%.1f", bot.getY()), waypoint);
        }
    }

    public void stopNavigation(ServerPlayerEntity bot) {
        if (bot != null) {
            BotPlayer bp = BotPlayer.getByUUID(bot.getUuid());
            if (bp != null) bp.clearMoveInput();
        }
    }

}
