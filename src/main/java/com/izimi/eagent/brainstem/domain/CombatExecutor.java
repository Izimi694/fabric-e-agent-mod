package com.izimi.eagent.brainstem.domain;

import com.izimi.eagent.brainstem.adapter.ActionResult;
import com.izimi.eagent.brainstem.equipment.SurvivalEquipmentManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CombatExecutor implements DomainExecutor<CombatCommand, ActionResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    private static final int SCAN_RANGE = 8;
    private static final int RETREAT_HEALTH = 6;

    private final Map<UUID, UUID> currentTargets = new HashMap<>();
    private final Map<UUID, Integer> attackCooldown = new HashMap<>();
    private FailureContext lastFailure;

    @Override
    public boolean canHandle(String commandType) {
        return "attack".equals(commandType);
    }

    @Override
    public CompletableFuture<ActionResult> submit(CombatCommand command) {
        ServerPlayerEntity bot = command.bot();
        if (bot == null) {
            return CompletableFuture.completedFuture(ActionResult.unable("attack: bot为null"));
        }

        ServerWorld world = bot.getServerWorld();
        LivingEntity target = resolveTarget(world, bot, command);

        if (target == null || !target.isAlive()) {
            currentTargets.remove(bot.getUuid());
            String msg = "附近没有" + (command.entityName() != null ? command.entityName() : "攻击目标");
            lastFailure = FailureContext.of("attack", msg);
            return CompletableFuture.completedFuture(ActionResult.unable(msg));
        }

        currentTargets.put(bot.getUuid(), target.getUuid());

        if (bot.getHealth() <= RETREAT_HEALTH) {
            Vec3d away = bot.getPos().subtract(target.getPos()).normalize().multiply(0.2);
            bot.setVelocity(new Vec3d(away.x, 0.08, away.z));
            bot.velocityModified = true;
            return CompletableFuture.completedFuture(ActionResult.partial(0.3, "血量过低，撤退"));
        }

        lookAtEntity(bot, target);
        double distSq = bot.squaredDistanceTo(target);

        if (distSq > 225.0) {
            return CompletableFuture.completedFuture(ActionResult.unable("目标太远"));
        }

        if (distSq > 25.0) {
            return CompletableFuture.completedFuture(handleRanged(bot, target, distSq));
        }

        return CompletableFuture.completedFuture(handleMelee(bot, target));
    }

    private ActionResult handleMelee(ServerPlayerEntity bot, LivingEntity target) {
        SurvivalEquipmentManager.equipBestWeapon(bot);

        int cd = attackCooldown.getOrDefault(bot.getUuid(), 0);
        if (cd > 0) {
            attackCooldown.put(bot.getUuid(), cd - 1);
            return ActionResult.partial(0.5, "攻击冷却中");
        }

        bot.swingHand(Hand.MAIN_HAND);
        bot.attack(target);
        attackCooldown.put(bot.getUuid(), 10);

        if (!target.isAlive()) {
            currentTargets.remove(bot.getUuid());
            return ActionResult.success("击杀目标");
        }
        return ActionResult.partial(0.7, "攻击");
    }

    private ActionResult handleRanged(ServerPlayerEntity bot, LivingEntity target, double distSq) {
        if (!SurvivalEquipmentManager.hasBowInHotbar(bot) || !SurvivalEquipmentManager.hasArrows(bot)) {
            Vec3d dir = target.getPos().subtract(bot.getPos()).normalize().multiply(0.15);
            bot.setVelocity(new Vec3d(dir.x, 0.08, dir.z));
            bot.velocityModified = true;
            return ActionResult.partial(0.4, "追击中");
        }

        int cd = attackCooldown.getOrDefault(bot.getUuid(), 0);
        if (cd > 0) {
            attackCooldown.put(bot.getUuid(), cd - 1);
            return ActionResult.partial(0.4, "射击冷却中");
        }

        selectBowHotbar(bot);
        float pitch = calculateBowPitch(bot, target);
        bot.setPitch(pitch);
        bot.swingHand(Hand.MAIN_HAND);
        attackCooldown.put(bot.getUuid(), 20);
        return ActionResult.partial(0.6, "射击");
    }

    private float calculateBowPitch(ServerPlayerEntity bot, LivingEntity target) {
        Vec3d delta = target.getPos().subtract(bot.getPos());
        double hDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (hDist < 0.01) return bot.getPitch();

        double v0 = 3.0;
        double g = 0.05;
        double vSq = v0 * v0;
        double sqrt = vSq * vSq - g * (g * hDist * hDist + 2 * delta.y * vSq);
        if (sqrt < 0) {
            return (float) -Math.toDegrees(Math.atan2(delta.y, hDist));
        }
        double angle = Math.atan2(vSq - Math.sqrt(sqrt), g * hDist);
        return (float) -Math.toDegrees(angle);
    }

    private void selectBowHotbar(ServerPlayerEntity bot) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (!stack.isEmpty() && (stack.isOf(Items.BOW) || stack.isOf(Items.CROSSBOW))) {
                bot.getInventory().selectedSlot = i;
                return;
            }
        }
    }

    private LivingEntity resolveTarget(ServerWorld world, ServerPlayerEntity bot, CombatCommand cmd) {
        if (cmd.entityName() != null && !cmd.entityName().isEmpty()) {
            LivingEntity named = findEntityByName(world, bot, cmd.entityName());
            if (named != null) currentTargets.put(bot.getUuid(), named.getUuid());
            return named;
        }

        UUID targetUuid = currentTargets.get(bot.getUuid());
        if (targetUuid != null) {
            var entity = world.getEntity(targetUuid);
            if (entity instanceof LivingEntity le && le.isAlive()) return le;
        }

        LivingEntity nearest = findNearestHostile(world, bot);
        if (nearest != null) currentTargets.put(bot.getUuid(), nearest.getUuid());
        return nearest;
    }

    private LivingEntity findEntityByName(ServerWorld world, ServerPlayerEntity bot, String name) {
        List<? extends LivingEntity> all = world.getEntitiesByClass(
                LivingEntity.class,
                bot.getBoundingBox().expand(SCAN_RANGE),
                e -> e.isAlive() && e != bot);
        for (var e : all) {
            String id = Registries.ENTITY_TYPE.getId(e.getType()).toString();
            if (id.toLowerCase().contains(name.toLowerCase())) return e;
        }
        return null;
    }

    private LivingEntity findNearestHostile(ServerWorld world, ServerPlayerEntity bot) {
        return world.getEntitiesByClass(
                        LivingEntity.class,
                        bot.getBoundingBox().expand(SCAN_RANGE),
                        e -> e.isAlive() && e != bot)
                .stream()
                .min((a, b) -> Double.compare(a.squaredDistanceTo(bot), b.squaredDistanceTo(bot)))
                .orElse(null);
    }

    private void lookAtEntity(ServerPlayerEntity bot, LivingEntity target) {
        double dx = target.getX() - bot.getX();
        double dy = target.getEyeY() - (bot.getY() + bot.getStandingEyeHeight());
        double dz = target.getZ() - bot.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(yaw);
        bot.setHeadYaw(yaw);
        bot.setPitch(pitch);
    }

    public void stopCombat(UUID botId) {
        currentTargets.remove(botId);
        attackCooldown.remove(botId);
    }

    @Override
    public void tick() {}

    @Override
    public FailureContext getFailureContext() { return lastFailure; }
}
