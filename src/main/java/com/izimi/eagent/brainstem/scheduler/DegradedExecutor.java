package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.brainstem.adapter.BasicActionAdapter;
import com.izimi.eagent.brainstem.adapter.TemporalScaler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public final class DegradedExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    private static final long COOLDOWN_TICKS = 100;
    private long lastActionTick = 0;

    public record UrgencyResult(String action, double urgency, Map<String, Object> params) {}

    public UrgencyResult evaluate(ServerPlayerEntity bot) {
        float healthRatio = bot.getHealth() / bot.getMaxHealth();
        int foodLevel = bot.getHungerManager().getFoodLevel();
        boolean isNight = !bot.getServerWorld().isDay();

        double healthUrgency = Math.max(0, 1.0 - healthRatio * 1.5);
        double hungerUrgency = Math.max(0, 1.0 - foodLevel / 20.0 * 1.2);
        double nightUrgency = isNight ? 0.7 : 0.0;

        if (healthUrgency > 0.6) {
            return new UrgencyResult("flee", healthUrgency, Map.of("speed", 0.3));
        }
        if (hungerUrgency > 0.7 && hasFood(bot)) {
            return new UrgencyResult("eat", hungerUrgency, Map.of());
        }
        if (nightUrgency > 0.5 && !hasShelterNearby(bot)) {
            return new UrgencyResult("seekShelter", nightUrgency, Map.of("speed", 0.15));
        }
        if (healthUrgency > 0.4) {
            return new UrgencyResult("retreat", healthUrgency, Map.of("speed", 0.25));
        }
        if (hungerUrgency > 0.5) {
            return new UrgencyResult("collectFood", hungerUrgency, Map.of());
        }

        if (needWood(bot)) {
            return new UrgencyResult("digWood", 0.3, Map.of());
        }

        return null;
    }

    public void execute(ServerPlayerEntity bot, BasicActionAdapter adapter, TemporalScaler temporalScaler) {
        if (adapter == null) return;
        long currentTick = bot.age;
        if (currentTick - lastActionTick < COOLDOWN_TICKS) return;

        UrgencyResult result = evaluate(bot);
        if (result == null) return;

        lastActionTick = currentTick;
        float speedMul = temporalScaler != null ? temporalScaler.getSpeed() : 1.0f;

        LOGGER.debug("[DegradedExecutor] action={} urgency={:.2f}", result.action(), result.urgency());

        switch (result.action()) {
            case "flee" -> {
                double speed = (double) result.params().getOrDefault("speed", 0.3);
                adapter.flee(bot, speed * speedMul);
            }
            case "eat" -> adapter.eat(bot);
            case "retreat" -> {
                double speed = (double) result.params().getOrDefault("speed", 0.25);
                adapter.retreat(bot, speed * speedMul);
            }
            case "seekShelter" -> {
                double speed = (double) result.params().getOrDefault("speed", 0.15);
                adapter.seekShelter(bot, speed * speedMul);
            }
            case "collectFood" -> findAndCollectFood(bot, adapter);
            case "digWood" -> digNearestWood(bot, adapter);
        }
    }

    private boolean hasFood(ServerPlayerEntity bot) {
        for (int i = 0; i < bot.getInventory().size(); i++) {
            var stack = bot.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.contains(DataComponentTypes.FOOD)) return true;
        }
        return false;
    }

    private boolean hasShelterNearby(ServerPlayerEntity bot) {
        BlockPos pos = bot.getBlockPos();
        var world = bot.getServerWorld();
        for (int dx = -8; dx <= 8; dx += 4) {
            for (int dz = -8; dz <= 8; dz += 4) {
                BlockPos check = pos.add(dx, 0, dz);
                if (world.isSkyVisible(check) || world.isSkyVisible(check.up())) continue;
                return true;
            }
        }
        return false;
    }

    private boolean needWood(ServerPlayerEntity bot) {
        var inv = bot.getInventory();
        int woodCount = 0;
        for (int i = 0; i < inv.size(); i++) {
            var stack = inv.getStack(i);
            if (!stack.isEmpty()) {
                String id = Registries.ITEM.getId(stack.getItem()).toString();
                if (id.contains("log") || id.contains("plank")) woodCount += stack.getCount();
            }
        }
        return woodCount < 4;
    }

    private void findAndCollectFood(ServerPlayerEntity bot, BasicActionAdapter adapter) {
        var world = bot.getServerWorld();
        var entities = world.getEntitiesByClass(ItemEntity.class,
                bot.getBoundingBox().expand(8), e -> {
                    var stack = e.getStack();
                    return !stack.isEmpty() && stack.contains(DataComponentTypes.FOOD);
                });
        if (!entities.isEmpty()) {
            var target = entities.get(0);
            adapter.moveTo(bot, target.getBlockPos());
        }
    }

    private void digNearestWood(ServerPlayerEntity bot, BasicActionAdapter adapter) {
        BlockPos pos = bot.getBlockPos();
        var world = bot.getServerWorld();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    BlockPos target = pos.add(dx, dy, dz);
                    var state = world.getBlockState(target);
                    String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                    if (blockId.contains("log")) {
                        adapter.moveTo(bot, target);
                        return;
                    }
                }
            }
        }
    }
}
