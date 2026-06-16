package com.izimi.eagent.brainstem.domain;

import com.izimi.eagent.brainstem.adapter.ActionResult;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class PlaceExecutor implements DomainExecutor<PlaceCommand, ActionResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private FailureContext failureContext;

    @Override
    public boolean canHandle(String commandType) {
        return "placeBlock".equals(commandType);
    }

    @Override
    public CompletableFuture<ActionResult> submit(PlaceCommand command) {
        ServerPlayerEntity bot = command.bot();
        BlockPos pos = command.pos();
        String faceStr = command.face();

        if (bot == null || pos == null) {
            return CompletableFuture.completedFuture(ActionResult.unable("placeBlock: 参数无效"));
        }

        ServerWorld world = bot.getServerWorld();
        BlockPos placePos = pos.offset(parseFace(faceStr));

        if (placePos.getSquaredDistance(bot.getBlockPos()) > 25.0) {
            return CompletableFuture.completedFuture(ActionResult.partial(0.3, "距离太远"));
        }

        ItemStack mainHand = bot.getMainHandStack();
        if (mainHand.isEmpty()) {
            failureContext = FailureContext.of("placeBlock", "主手没有物品");
            return CompletableFuture.completedFuture(ActionResult.unable("主手没有物品"));
        }

        world.setBlockState(placePos, Blocks.STONE.getDefaultState());
        bot.swingHand(Hand.MAIN_HAND);

        failureContext = null;
        return CompletableFuture.completedFuture(ActionResult.success("放置完成"));
    }

    private static Direction parseFace(String face) {
        if (face == null) return Direction.UP;
        return switch (face.toLowerCase()) {
            case "up", "top" -> Direction.UP;
            case "down", "bottom" -> Direction.DOWN;
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            default -> Direction.UP;
        };
    }

    @Override
    public void tick() {}

    @Override
    public FailureContext getFailureContext() { return failureContext; }
}
