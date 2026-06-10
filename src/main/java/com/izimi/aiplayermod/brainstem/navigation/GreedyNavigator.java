package com.izimi.aiplayermod.brainstem.navigation;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class GreedyNavigator {
    private final World world;

    private static final Set<net.minecraft.block.Block> DANGEROUS_BLOCKS = Set.of(
            Blocks.LAVA, Blocks.FIRE, Blocks.CACTUS, Blocks.MAGMA_BLOCK,
            Blocks.SWEET_BERRY_BUSH, Blocks.COBWEB
    );

    private static final int[][] HORIZONTAL_DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1}
    };

    private static final int[][] JUMP_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public GreedyNavigator(World world) {
        this.world = world;
    }

    public BlockPos getBestStep(BlockPos current, BlockPos target) {
        List<Candidate> candidates = new ArrayList<>();

        for (int[] dir : HORIZONTAL_DIRS) {
            addWalkCandidates(candidates, current, dir, target);
        }

        addVerticalCandidates(candidates, current, target);
        addJumpCandidates(candidates, current, target);

        candidates.removeIf(c -> isDangerous(c.pos));

        if (candidates.isEmpty()) return null;

        candidates.sort(Comparator.comparingDouble(c -> c.cost));
        return candidates.get(0).pos;
    }

    private void addWalkCandidates(List<Candidate> candidates, BlockPos current, int[] dir, BlockPos target) {
        BlockPos neighbor = current.add(dir[0], 0, dir[1]);
        if (isWalkableLike(neighbor)) {
            candidates.add(new Candidate(neighbor, heuristic(neighbor, target)));
        }

        BlockPos jumpUp = current.add(dir[0], 1, dir[1]);
        if (isSingleBlockJump(current, jumpUp) && isWalkableLike(jumpUp)) {
            candidates.add(new Candidate(jumpUp, heuristic(jumpUp, target)));
        }
    }

    private boolean isSingleBlockJump(BlockPos from, BlockPos to) {
        if (to.getY() - from.getY() != 1) return false;
        BlockPos wall = new BlockPos(to.getX(), from.getY(), to.getZ());
        if (!isPassableForJump(wall) || !isPassableForJump(wall.up())) return false;
        BlockPos wallAbove = wall.up(2);
        return isPassableForJump(wallAbove);
    }

    private void addVerticalCandidates(List<Candidate> candidates, BlockPos current, BlockPos target) {
        for (int dy = -2; dy <= 2; dy++) {
            if (dy == 0) continue;
            BlockPos neighbor = current.add(0, dy, 0);
            if (isWalkableLike(neighbor)) {
                double cost = heuristic(neighbor, target);
                if (dy < 0) cost *= 1.15;
                candidates.add(new Candidate(neighbor, cost));
            }
        }
    }

    private void addJumpCandidates(List<Candidate> candidates, BlockPos current, BlockPos target) {
        for (int[] dir : JUMP_DIRS) {
            for (int dist = 2; dist <= 3; dist++) {
                BlockPos landing = current.add(dir[0] * dist, 0, dir[1] * dist);
                if (canJumpPath(current, landing, dir)) {
                    candidates.add(new Candidate(landing, heuristic(landing, target) * 1.2));
                }
            }

            BlockPos upLanding2 = current.add(dir[0] * 2, 1, dir[1] * 2);
            if (canJumpPath(current, upLanding2, dir)) {
                candidates.add(new Candidate(upLanding2, heuristic(upLanding2, target) * 1.3));
            }
        }
    }

    private boolean canJumpPath(BlockPos from, BlockPos landing, int[] dir) {
        if (Math.abs(landing.getY() - from.getY()) > 2) return false;
        if (!isWalkableLike(landing)) return false;

        int dx = landing.getX() - from.getX();
        int dz = landing.getZ() - from.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));

        for (int i = 1; i < steps; i++) {
            int x = from.getX() + (int) Math.round((double) dx * i / steps);
            int z = from.getZ() + (int) Math.round((double) dz * i / steps);
            BlockPos feet = new BlockPos(x, from.getY(), z);
            if (!isPassableForJump(feet) || !isPassableForJump(feet.up())) return false;
        }

        return true;
    }

    private boolean isWalkableLike(BlockPos pos) {
        if (pos.getY() < world.getBottomY() || pos.getY() >= world.getTopY()) return false;

        var feetState = world.getBlockState(pos);
        var headState = world.getBlockState(pos.up());
        boolean feetPassable = !feetState.isSolidBlock(world, pos);
        boolean headPassable = !headState.isSolidBlock(world, pos.up());

        if (!feetPassable || !headPassable) return false;

        var groundState = world.getBlockState(pos.down());
        return groundState.isSolidBlock(world, pos.down()) || groundState.isOf(Blocks.WATER);
    }

    private boolean isPassableForJump(BlockPos pos) {
        var state = world.getBlockState(pos);
        return state.getCollisionShape(world, pos) == VoxelShapes.empty();
    }

    private boolean isDangerous(BlockPos pos) {
        var feetState = world.getBlockState(pos);
        var headState = world.getBlockState(pos.up());
        var groundState = world.getBlockState(pos.down());
        return DANGEROUS_BLOCKS.contains(feetState.getBlock())
                || DANGEROUS_BLOCKS.contains(headState.getBlock())
                || DANGEROUS_BLOCKS.contains(groundState.getBlock());
    }

    private double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy * 2.0 + dz * dz;
    }

    private static class Candidate {
        final BlockPos pos;
        final double cost;

        Candidate(BlockPos pos, double cost) {
            this.pos = pos;
            this.cost = cost;
        }
    }
}
