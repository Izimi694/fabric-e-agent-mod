package com.izimi.eagent.brainstem.domain;

import com.izimi.eagent.brainstem.adapter.ActionResult;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DigExecutor implements DomainExecutor<BreakCommand, ActionResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private static final int MIN_BREAK_TICKS = 15;
    private static final int SWING_INTERVAL = 7;
    private static final int SCAN_RANGE = 8;
    private static final double DEFAULT_DIG_DISTANCE_SQ = 4.0;

    private BlockPos currentDigTarget;
    private int digBreakingTicks;
    private int digBreakTimeTicks = 40;
    private int digSwingTicks;
    private FailureContext lastFailure;

    @Override
    public boolean canHandle(String commandType) {
        return "dig".equals(commandType);
    }

    @Override
    public CompletableFuture<ActionResult> submit(BreakCommand command) {
        return CompletableFuture.completedFuture(dig(command.bot(), command.target()));
    }

    @Override
    public void tick() {
        if (currentDigTarget == null) return;
    }

    @Override
    public FailureContext getFailureContext() {
        return lastFailure;
    }

    public ActionResult dig(ServerPlayerEntity bot, BlockPos target) {
        if (bot == null) {
            lastFailure = FailureContext.of("dig", "bot为null");
            return ActionResult.unable("dig: bot为null");
        }

        ServerWorld world = bot.getServerWorld();

        if (target != null) {
            currentDigTarget = target;
            LOGGER.info("[DIG] received target: {}", target);
        }

        if (currentDigTarget == null) {
            currentDigTarget = findNearbyBlock(world, bot);
            if (currentDigTarget == null) {
                List<String> nearbyIds = getNearbyBlockIds(world, bot);
                String mainHandItem = getMainHandItemId(bot);
                Map<String, Object> diag = new LinkedHashMap<>();
                diag.put("nearbyBlocks", nearbyIds);
                diag.put("inventory", mainHandItem);
                diag.put("position", bot.getBlockPos());
                lastFailure = FailureContext.of("dig", "\u9644\u8fd1\u6ca1\u6709\u53ef\u6316\u6398\u7684\u65b9\u5757", diag);
                LOGGER.info("[DIG] no reachable block, nearby={}", nearbyIds);
                return ActionResult.unable("\u9644\u8fd1\u6ca1\u6709\u53ef\u6316\u6398\u7684\u65b9\u5757");
            }
            LOGGER.info("[DIG] self-selected target: {} at {}", Registries.BLOCK.getId(world.getBlockState(currentDigTarget).getBlock()).getPath(), currentDigTarget);
        }

        BlockState currentState = world.getBlockState(currentDigTarget);
        String blockName = Registries.BLOCK.getId(currentState.getBlock()).getPath();

        if (currentState.isAir() || currentState.isOf(Blocks.BEDROCK)) {
            resetDigState();
            LOGGER.info("[DIG] target gone (air/bedrock)");
            lastFailure = FailureContext.of("dig", "\u76ee\u6807\u65b9\u5757\u5df2\u4e0d\u5b58\u5728");
            return ActionResult.unable("\u76ee\u6807\u65b9\u5757\u5df2\u4e0d\u5b58\u5728");
        }

        double maxDistSq = DEFAULT_DIG_DISTANCE_SQ;
        double distance = bot.getPos().squaredDistanceTo(
                currentDigTarget.getX() + 0.5, currentDigTarget.getY(), currentDigTarget.getZ() + 0.5);
        if (distance > maxDistSq) {
            resetDigState();
            LOGGER.info("[DIG] too far: dist={} > max={}",
                String.format("%.1f", Math.sqrt(distance)), String.format("%.1f", Math.sqrt(maxDistSq)));
            lastFailure = FailureContext.of("dig", "\u8ddd\u79bb\u592a\u8fdc\uff0c\u53d6\u6d88\u6316\u6398",
                    Map.of("distance", String.format("%.1f", Math.sqrt(distance)), "position", bot.getBlockPos()));
            return ActionResult.unable("\u8ddd\u79bb\u592a\u8fdc\uff0c\u53d6\u6d88\u6316\u6398");
        }

        if (digBreakingTicks == 0) {
            equipBestTool(bot, currentState);
            digBreakTimeTicks = calculateBreakTime(currentState, bot);
            LOGGER.info("[DIG] start breaking {} (ticks={})", blockName, digBreakTimeTicks);
        }

        digBreakingTicks++;
        digSwingTicks++;

        if (digSwingTicks >= SWING_INTERVAL) {
            digSwingTicks = 0;
            bot.swingHand(Hand.MAIN_HAND);
        }

        world.setBlockBreakingInfo(bot.getId(), currentDigTarget,
                Math.min(10, (int) (digBreakingTicks * 10.0 / digBreakTimeTicks)));

        if (digBreakingTicks >= digBreakTimeTicks) {
            BlockPos completed = currentDigTarget;
            resetDigState();
            world.breakBlock(completed, true, bot);
            lastFailure = null;
            LOGGER.info("[DIG] completed! broke {}", blockName);
            return ActionResult.success("\u6316\u6398\u5b8c\u6210");
        }

        if (digBreakingTicks % 20 == 0) {
            LOGGER.info("[DIG] breaking {} progress={}/{}", blockName, digBreakingTicks, digBreakTimeTicks);
        }
        return ActionResult.partial(0.6, "\u6316\u6398\u4e2d");
    }

    public void stopDigging() {
        resetDigState();
    }

    private void resetDigState() {
        currentDigTarget = null;
        digBreakingTicks = 0;
        digBreakTimeTicks = 40;
        digSwingTicks = 0;
    }

    private int calculateBreakTime(BlockState state, ServerPlayerEntity bot) {
        float hardness = state.getHardness(bot.getServerWorld(), bot.getBlockPos());
        if (hardness < 0) return 72000;
        if (hardness >= 50) return MIN_BREAK_TICKS * 10;

        float toolSpeed = 1.0f;
        ItemStack held = bot.getMainHandStack();
        if (!held.isEmpty()) {
            toolSpeed = Math.max(1.0f, held.getMiningSpeedMultiplier(state));
        }

        int ticks = Math.max(MIN_BREAK_TICKS, Math.round(hardness * 30.0f / toolSpeed));
        return ticks;
    }

    private BlockPos findNearbyBlock(ServerWorld world, ServerPlayerEntity bot) {
        BlockPos botPos = bot.getBlockPos();
        for (int dy = 4; dy >= -1; dy--) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = botPos.add(dx, dy, dz);
                    if (!world.getBlockState(pos).isAir()) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private void equipBestTool(ServerPlayerEntity bot, BlockState state) {
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

    private String getMainHandItemId(ServerPlayerEntity bot) {
        ItemStack held = bot.getMainHandStack();
        if (held.isEmpty()) return "empty";
        return Registries.ITEM.getId(held.getItem()).toString();
    }

    private List<String> getNearbyBlockIds(ServerWorld world, ServerPlayerEntity bot) {
        List<String> ids = new ArrayList<>();
        BlockPos botPos = bot.getBlockPos();
        for (int dy = 4; dy >= -1; dy--) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    BlockPos pos = botPos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir()) {
                        ids.add(Registries.BLOCK.getId(state.getBlock()).toString());
                    }
                }
            }
        }
        return ids;
    }
}
