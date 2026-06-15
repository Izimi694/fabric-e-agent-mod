package com.izimi.eagent.brainstem.action;

import com.izimi.eagent.brainstem.navigation.GreedyNavigator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class MoveToBlockAction implements IAction {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    private static final double ARRIVAL_THRESHOLD = 2.0;
    private static final int STUCK_TIMEOUT = 40;

    private final BlockPos target;
    private int stuckTicks = 0;
    private BlockPos lastPos = null;

    public MoveToBlockAction(BlockPos target) {
        this.target = target;
    }

    @Override
    public ActionState tick(ServerWorld world, ServerPlayerEntity bot) {
        double dist = bot.getPos().distanceTo(Vec3d.ofCenter(target));
        if (dist < ARRIVAL_THRESHOLD) {
            bot.updateInput(0, 0, false, false);
            return ActionState.SUCCESS;
        }

        GreedyNavigator nav = new GreedyNavigator(world);
        BlockPos bestStep = nav.getBestStep(bot.getBlockPos(), target);

        if (bestStep == null) {
            moveDirect(bot);
            stuckTicks++;
            if (stuckTicks > STUCK_TIMEOUT * 2) {
                LOGGER.warn("[MoveToBlockAction] 无路可走 target={}", target);
                return ActionState.FAILED;
            }
            return ActionState.RUNNING;
        }

        Vec3d dir = Vec3d.ofCenter(bestStep).subtract(bot.getPos());
        double horiDist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);

        if (horiDist > 0.01) {
            float yaw = (float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90;
            bot.setYaw(yaw);
            bot.setHeadYaw(yaw);

            boolean shouldJump = (bot.isOnGround() || bot.isTouchingWater())
                    && (bestStep.getY() > bot.getBlockY() || dir.y > 0.5);
            bot.updateInput(0.5f, 0, shouldJump, false);
        }

        // Stuck detection: force jump if stuck
        BlockPos curPos = bot.getBlockPos();
        if (curPos.equals(lastPos)) {
            stuckTicks++;
            if (stuckTicks > STUCK_TIMEOUT) {
                if (bot.isOnGround() || bot.isTouchingWater()) {
                    bot.updateInput(0.5f, 0, true, false);
                    bot.jump();
                    LOGGER.info("[MoveToBlockAction] 卡死强制跳 stuck={}", stuckTicks);
                }
                if (stuckTicks > STUCK_TIMEOUT * 3) {
                    LOGGER.warn("[MoveToBlockAction] 卡死超时放弃 target={}", target);
                    return ActionState.FAILED;
                }
            }
        } else {
            stuckTicks = 0;
        }
        lastPos = curPos;

        return ActionState.RUNNING;
    }

    private void moveDirect(ServerPlayerEntity bot) {
        Vec3d dir = Vec3d.ofCenter(target).subtract(bot.getPos());
        double horiDist = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (horiDist > 0.01) {
            float yaw = (float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90;
            bot.setYaw(yaw);
            bot.setHeadYaw(yaw);
            bot.updateInput(0.5f, 0, false, false);
        }
    }

    @Override
    public void reset() {
        stuckTicks = 0;
        lastPos = null;
    }
}
