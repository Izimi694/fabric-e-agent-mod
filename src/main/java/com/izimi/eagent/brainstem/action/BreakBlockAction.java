package com.izimi.eagent.brainstem.action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class BreakBlockAction implements IAction {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    private static final int BREAK_TIME_TICKS = 40;

    private final BlockPos target;
    private int breakingTicks = 0;

    public BreakBlockAction(BlockPos target) {
        this.target = target;
    }

    @Override
    public ActionState tick(ServerWorld world, ServerPlayerEntity bot) {
        BlockState state = world.getBlockState(target);
        if (state.isAir()) {
            return ActionState.SUCCESS;
        }

        double dist = bot.getPos().squaredDistanceTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        if (dist > 36.0) {
            return ActionState.FAILED;
        }

        // Face target
        double dx = target.getX() + 0.5 - bot.getX();
        double dz = target.getZ() + 0.5 - bot.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        bot.setYaw(yaw);
        bot.setHeadYaw(yaw);

        equipBestTool(world, bot, target);

        breakingTicks++;
        if (breakingTicks % 4 == 0) {
            world.setBlockBreakingInfo(bot.getId(), target, (int) (breakingTicks * 10.0 / BREAK_TIME_TICKS));
        }

        if (breakingTicks >= BREAK_TIME_TICKS) {
            breakingTicks = 0;
            world.breakBlock(target, true, bot);
            world.setBlockBreakingInfo(bot.getId(), target, -1);
            LOGGER.info("[BreakBlockAction] 方块已破坏 {}", target);
            return ActionState.SUCCESS;
        }

        return ActionState.RUNNING;
    }

    private void equipBestTool(ServerWorld world, ServerPlayerEntity bot, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        ItemStack bestTool = ItemStack.EMPTY;
        float bestSpeed = 0;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestTool = stack;
            }
        }

        if (!bestTool.isEmpty()) {
            for (int i = 0; i < 9; i++) {
                if (bot.getInventory().getStack(i) == bestTool) {
                    bot.getInventory().selectedSlot = i;
                    break;
                }
            }
        }
    }

    @Override
    public void reset() {
        breakingTicks = 0;
    }
}
