package com.izimi.eagent.brainstem.action;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class FindBlockAction implements IAction {
    private final String targetMatch;
    private final int scanRange;
    private boolean found = false;
    private BlockPos foundPos = null;

    public FindBlockAction(String targetMatch, int scanRange) {
        this.targetMatch = targetMatch;
        this.scanRange = scanRange;
    }

    @Override
    public ActionState tick(ServerWorld world, ServerPlayerEntity bot) {
        if (found && foundPos != null) {
            if (!world.getBlockState(foundPos).isAir()) {
                return ActionState.SUCCESS;
            }
            found = false;
            foundPos = null;
        }

        BlockPos botPos = bot.getBlockPos();
        String search = targetMatch.toLowerCase();

        for (int dx = -scanRange; dx <= scanRange; dx++) {
            for (int dy = -scanRange; dy <= scanRange; dy++) {
                for (int dz = -scanRange; dz <= scanRange; dz++) {
                    BlockPos pos = botPos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) continue;
                    String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                    if (blockId.contains(search)) {
                        foundPos = pos;
                        found = true;
                        return ActionState.SUCCESS;
                    }
                }
            }
        }
        return ActionState.FAILED;
    }

    public BlockPos getFoundPos() {
        return foundPos;
    }

    @Override
    public void reset() {
        found = false;
        foundPos = null;
    }
}
