package com.izimi.eagent.brainstem.perception;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class WorldScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    public static final int FINE_INTERVAL = 2;
    public static final int CORE_INTERVAL = 6;
    public static final int BROAD_INTERVAL = 300;
    public static final int SCAN_RADIUS = 16;
    public static final int SCAN_HEIGHT = 8;
    public static final int WINDOW_SIZE = 50;
    public static final int COMPACT_MAX_CHARS = 400;

    private int tickCounter = 0;
    private int tpsDownshift = 1;

    private final Map<String, SlidingWindow> windows = new HashMap<>();
    private String biomeCache = "plains";
    private long timeOfDayCache = 0;

    private static class SlidingWindow {
        final int[] buffer;
        int index = 0;
        int count = 0;

        SlidingWindow(int size) { this.buffer = new int[size]; }

        void push(int value) {
            buffer[index % buffer.length] = value;
            index++;
            if (count < buffer.length) count++;
        }

        int average() {
            if (count == 0) return 0;
            long sum = 0;
            for (int i = 0; i < count; i++) sum += buffer[i];
            return (int) (sum / count);
        }

        int current() {
            return count > 0 ? buffer[(index - 1) % buffer.length] : 0;
        }

        int delta() { return current() - average(); }
    }

    public PerceptionSnapshot scan(ServerPlayerEntity bot, long tick, double tps) {
        if (bot == null) return PerceptionSnapshot.empty(tick);

        boolean isFine = (tickCounter % (FINE_INTERVAL * tpsDownshift)) == 0;
        boolean isCore = (tickCounter % (CORE_INTERVAL * tpsDownshift)) == 0;
        boolean isBroad = (tickCounter % (BROAD_INTERVAL * tpsDownshift)) == 0;
        tickCounter++;

        tpsDownshift = tps < 18 ? 2 : 1;

        ServerWorld world = (ServerWorld) bot.getWorld();
        BlockPos pos = bot.getBlockPos();

        int oreVeins = 0, woodBlocks = 0, mobCount = 0, projectiles = 0;
        double health = bot.getHealth() / bot.getMaxHealth();
        double hunger = bot.getHungerManager().getFoodLevel() / 20.0;
        double armor = bot.getArmor() / 20.0;
        boolean underAttack = bot.hurtTime > 0;
        boolean hasShelter = world.isSkyVisible(pos) || world.isSkyVisible(pos.up());

        if (isFine || isCore) {
            var entities = world.getEntitiesByClass(LivingEntity.class,
                bot.getBoundingBox().expand(SCAN_RADIUS), e -> e != bot);
            mobCount = (int) entities.stream().filter(e -> e instanceof Monster).count();

            projectiles = world.getEntitiesByClass(ProjectileEntity.class,
                bot.getBoundingBox().expand(SCAN_RADIUS), p -> true).size();
        }

        if (isCore) {
            oreVeins = countBlocks(world, pos, "_ore", SCAN_RADIUS, SCAN_HEIGHT);
            woodBlocks = countBlocksBySet(world, pos, LOG_BLOCKS, SCAN_RADIUS, SCAN_HEIGHT);

            recordResource("iron_ore", oreVeins);
            recordResource("wood", woodBlocks);
            recordResource("mob_count", mobCount);
        }

        if (isBroad) {
            biomeCache = world.getBiome(pos).getKey()
                .map(k -> k.getValue().getPath())
                .orElse("unknown");
            timeOfDayCache = world.getTimeOfDay() % 24000;
        }

        double controllable = computeControllable(health, mobCount, underAttack);

        var dense = new PerceptionSnapshot.DenseView(
            oreVeins, woodBlocks, mobCount, projectiles,
            health, hunger, armor, underAttack, hasShelter,
            timeOfDayCache, controllable
        );

        String compact = buildCompact(bot, dense);
        return new PerceptionSnapshot(tick, dense, new PerceptionSnapshot.CompactView(compact));
    }

    public Map<String, Float> getVisibleBlocksWithDistance(ServerPlayerEntity bot) {
        if (bot == null) return Map.of();
        ServerWorld world = (ServerWorld) bot.getWorld();
        BlockPos center = bot.getBlockPos();
        Map<String, Float> result = new HashMap<>();

        BlockPos.iterateOutwards(center, SCAN_RADIUS, SCAN_HEIGHT, SCAN_RADIUS).forEach(bp -> {
            BlockState state = world.getBlockState(bp);
            if (!state.isAir()) {
                String id = Registries.BLOCK.getId(state.getBlock()).getPath();
                double dist = Math.sqrt(bp.getSquaredDistance(center));
                result.merge(id, (float) dist, Math::min);
            }
        });

        if (LOGGER.isInfoEnabled() && !result.isEmpty()) {
            var top5 = result.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(5)
                .map(e -> e.getKey() + "@" + String.format("%.1f", e.getValue()))
                .collect(Collectors.joining(", "));
            LOGGER.info("[WS] visible {} blocks: {}", result.size(), top5);
        }
        return result;
    }

    public List<String> getVisibleBlockIds(ServerPlayerEntity bot) {
        return new ArrayList<>(getVisibleBlocksWithDistance(bot).keySet());
    }

    public int getResourceDelta(String key) {
        var w = windows.get(key);
        return w != null ? w.delta() : 0;
    }

    public int getResourceCount(String key) {
        var w = windows.get(key);
        return w != null ? w.current() : 0;
    }

    // ── private ──

    private void recordResource(String key, int value) {
        windows.computeIfAbsent(key, k -> new SlidingWindow(WINDOW_SIZE)).push(value);
    }

    private static int countBlocks(ServerWorld world, BlockPos center, String nameContains, int r, int h) {
        int count = 0;
        for (var bp : BlockPos.iterateOutwards(center, r, h, r)) {
            String id = Registries.BLOCK.getId(world.getBlockState(bp).getBlock()).getPath();
            if (id.contains(nameContains)) count++;
        }
        return count;
    }

    private static int countBlocksBySet(ServerWorld world, BlockPos center, Set<String> names, int r, int h) {
        int count = 0;
        for (var bp : BlockPos.iterateOutwards(center, r, h, r)) {
            String id = Registries.BLOCK.getId(world.getBlockState(bp).getBlock()).getPath();
            if (names.contains(id)) count++;
        }
        return count;
    }

    private static final Set<String> LOG_BLOCKS = Set.of(
        "oak_log", "birch_log", "spruce_log", "jungle_log",
        "acacia_log", "dark_oak_log", "mangrove_log", "cherry_log");

    private static double computeControllable(double health, int mobCount, boolean underAttack) {
        double variance = (1 - health) * health;
        double envPenalty = underAttack ? 0.5 : 1.0;
        double mobPenalty = Math.max(0.3, 1.0 - mobCount * 0.05);
        return Math.max(0, Math.min(1, (1 / (1 + variance)) * envPenalty * mobPenalty));
    }

    private String buildCompact(ServerPlayerEntity bot, PerceptionSnapshot.DenseView dense) {
        List<String> parts = new ArrayList<>();
        parts.add("HP " + (int)(dense.health() * 20) + "/" + (int)(dense.hunger() * 20));
        if (dense.oreVeinsNearby() > 0) parts.add("ore:" + dense.oreVeinsNearby());
        if (dense.woodBlocksNearby() > 0) parts.add("wood:" + dense.woodBlocksNearby());
        if (dense.mobCount() > 0) parts.add("mobs:" + dense.mobCount());
        if (dense.isUnderAttack()) parts.add("ATTACKED");
        parts.add("biome:" + biomeCache);
        if (dense.controllableIndex() < 0.3) parts.add("LOW_CONTROL");

        String raw = String.join(" ", parts);
        return raw.length() > COMPACT_MAX_CHARS ? raw.substring(0, COMPACT_MAX_CHARS) : raw;
    }
}
