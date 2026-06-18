package com.izimi.eagent.brainstem.domain;

import com.izimi.eagent.brainstem.adapter.ActionResult;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
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
            failureContext = FailureContext.of("placeBlock", "参数无效");
            return CompletableFuture.completedFuture(com.izimi.eagent.brainstem.adapter.ActionResult.unable("placeBlock: 参数无效"));
        }

        Direction face = parseFace(faceStr);
        BlockPos placePos = pos.offset(face);

        if (placePos.getSquaredDistance(bot.getBlockPos()) > 25.0) {
            return CompletableFuture.completedFuture(com.izimi.eagent.brainstem.adapter.ActionResult.partial(0.3, "距离太远"));
        }

        if (!bot.getServerWorld().getBlockState(placePos).isReplaceable()) {
            failureContext = FailureContext.of("placeBlock", "目标位置不可替换");
            return CompletableFuture.completedFuture(com.izimi.eagent.brainstem.adapter.ActionResult.unable("目标位置不可替换"));
        }

        ItemStack mainHand = bot.getMainHandStack();
        if (mainHand.isEmpty()) {
            failureContext = FailureContext.of("placeBlock", "主手没有物品");
            return CompletableFuture.completedFuture(com.izimi.eagent.brainstem.adapter.ActionResult.unable("主手没有物品"));
        }

        Vec3d hitVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitVec, face.getOpposite(), pos, false);
        ItemUsageContext ctx = new ItemUsageContext(bot, Hand.MAIN_HAND, hit);
        net.minecraft.util.ActionResult placeResult = mainHand.useOnBlock(ctx);

        if (placeResult.isAccepted()) {
            failureContext = null;
            return CompletableFuture.completedFuture(com.izimi.eagent.brainstem.adapter.ActionResult.success("放置完成"));
        }

        failureContext = FailureContext.of("placeBlock", "放置失败");
        return CompletableFuture.completedFuture(com.izimi.eagent.brainstem.adapter.ActionResult.fail("放置失败"));
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
