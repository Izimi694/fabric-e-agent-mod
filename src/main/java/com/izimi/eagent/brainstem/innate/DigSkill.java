package com.izimi.eagent.brainstem.innate;

import com.izimi.eagent.brainstem.skill.Skill;
import com.izimi.eagent.util.TagResolver;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class DigSkill extends Skill {
    private BlockPos currentTarget = null;
    private int breakingTicks = 0;
    private static final int BREAK_TIME_TICKS = 40;

    public DigSkill() {
        super("dig", "挖掘", "innate");
    }

    @Override
    public boolean canExecute(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
        if (bot == null || world == null) return false;
        String filter = extractFilter(context);
        return findDigTarget(world, bot, filter) != null;
    }

    @Override
    public SkillResult execute(ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
        String filter = extractFilter(context);
        BlockPos target = findDigTarget(world, bot, filter);
        if (target == null) {
            return SkillResult.unable("附近没有可挖掘的目标方块");
        }

        double distance = bot.getPos().squaredDistanceTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        if (distance > 25.0) {
            return SkillResult.partial(0.3, "距离目标太远，需要移动");
        }

        equipBestTool(world, bot, target);

        breakingTicks++;

        if (breakingTicks >= BREAK_TIME_TICKS) {
            breakingTicks = 0;
            world.breakBlock(target, true, bot);
            currentTarget = null;
            return SkillResult.success("成功挖掘方块");
        }

        if (Math.random() < 0.1) {
            world.setBlockBreakingInfo(bot.getId(), target, (int)(breakingTicks * 10.0 / BREAK_TIME_TICKS));
        }

        return SkillResult.partial(0.6, "正在挖掘");
    }

    private String extractFilter(Map<String, Object> context) {
        if (context == null) return null;
        String goal = (String) context.get("goal");
        if (goal == null) return null;

        String resolved = TagResolver.findTargetByAlias(goal);
        if (resolved != null && !resolved.equals(goal.toLowerCase())) return resolved;

        String lower = goal.toLowerCase();
        for (String part : lower.split("[ _]")) {
            if (part.length() > 2 && !part.equals("dig") && !part.equals("alex")) {
                return part;
            }
        }
        return null;
    }

    private BlockPos findDigTarget(ServerWorld world, ServerPlayerEntity bot, String filter) {
        if (currentTarget != null) {
            BlockState state = world.getBlockState(currentTarget);
            if (!state.isAir() && matchesFilter(state, filter)) {
                return currentTarget;
            }
        }

        BlockPos botPos = bot.getBlockPos();
        for (int dy = 4; dy >= -1; dy--) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = botPos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (matchesFilter(state, filter)) {
                        currentTarget = pos;
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private boolean matchesFilter(BlockState state, String filter) {
        if (filter == null || filter.isEmpty()) return !state.isAir();
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        return blockId.toLowerCase().contains(filter.toLowerCase());
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
}
