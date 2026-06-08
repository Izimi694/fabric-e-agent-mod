package com.izimi.aiplayermod.brainstem.scheduler;

import com.izimi.aiplayermod.brainstem.HormonalSystem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;

import java.util.List;

public class UrgencyClassifier {

    public enum UrgencyLabel {
        CRITICAL,
        HIGH,
        NORMAL,
        LOW,
        OBSERVE
    }

    private static final double HIGH_THRESHOLD = 0.5;
    private static final double LOW_THRESHOLD = 0.2;

    public UrgencyLabel classify(HormonalSystem hormones, ServerPlayerEntity bot) {
        double stimulus = computeStimulus(hormones, bot);
        double inhibition = computeInhibition(hormones, bot);
        double delta = stimulus - inhibition;

        if (delta > HIGH_THRESHOLD)  return UrgencyLabel.CRITICAL;
        if (delta < -HIGH_THRESHOLD) return UrgencyLabel.CRITICAL;
        if (delta > LOW_THRESHOLD)   return UrgencyLabel.HIGH;
        if (delta < -LOW_THRESHOLD)  return UrgencyLabel.LOW;
        return UrgencyLabel.OBSERVE;
    }

    private double computeStimulus(HormonalSystem hormones, ServerPlayerEntity bot) {
        double aggression = hormones != null ? hormones.getAggression() : 0;
        double curiosity = hormones != null ? hormones.getCuriosity() : 0;
        double intimacy = hormones != null ? hormones.getIntimacy(bot != null ? bot.getUuid() : null) : 0;

        double score = 0;
        score += aggression * 0.4;
        score += curiosity * 0.3;
        score += intimacy * 0.1;

        if (bot != null) {
            ServerWorld world = bot.getServerWorld();
            if (world != null) {
                boolean hasLava = findBlockNearby(world, bot.getBlockPos(), Blocks.LAVA, 3);
                if (hasLava) score += 0.8;
            }
        }

        return score;
    }

    private double computeInhibition(HormonalSystem hormones, ServerPlayerEntity bot) {
        double stress = hormones != null ? hormones.getStress() : 0;

        double score = 0;
        score += stress * 0.5;

        if (bot != null) {
            double health = bot.getHealth();
            if (health < 4) score += 0.8;
            else if (health < 8) score += 0.4;

            ServerWorld world = bot.getServerWorld();
            if (world != null) {
                List<HostileEntity> hostiles = world.getEntitiesByClass(
                        HostileEntity.class,
                        bot.getBoundingBox().expand(8),
                        e -> e.isAlive()
                );
                if (!hostiles.isEmpty()) score += 0.3;

                boolean hasVoid = bot.getY() < -10;
                if (hasVoid) score += 0.9;
            }
        }

        return score;
    }

    private boolean findBlockNearby(ServerWorld world, BlockPos origin, net.minecraft.block.Block block, int range) {
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    if (world.getBlockState(origin.add(dx, dy, dz)).isOf(block)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
