package com.izimi.eagent.brainstem.domain;

import com.izimi.eagent.brainstem.adapter.ActionResult;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CombatExecutor implements DomainExecutor<CombatCommand, ActionResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    private static final int SCAN_RANGE = 8;

    private FailureContext failureContext;

    @Override
    public boolean canHandle(String commandType) {
        return "attack".equals(commandType);
    }

    @Override
    public CompletableFuture<ActionResult> submit(CombatCommand command) {
        ServerPlayerEntity bot = command.bot();
        String entityName = command.entityName();

        if (bot == null) {
            return CompletableFuture.completedFuture(ActionResult.unable("attack: bot为null"));
        }

        ServerWorld world = bot.getServerWorld();
        LivingEntity target = findTarget(world, bot, entityName);

        if (target == null) {
            String msg = "附近没有" + (entityName != null ? entityName : "攻击目标");
            failureContext = FailureContext.of("attack", msg);
            return CompletableFuture.completedFuture(ActionResult.unable(msg));
        }

        // 锁定视角到目标
        double px = target.getX();
        double py = target.getEyeY();
        double pz = target.getZ();
        double dx = px - bot.getX();
        double dy = py - (bot.getY() + bot.getStandingEyeHeight());
        double dz = pz - bot.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        bot.setYaw(yaw);
        bot.setHeadYaw(yaw);
        bot.setPitch(pitch);

        double distSq = bot.squaredDistanceTo(target);
        if (distSq > 25.0) {
            // 距离太远 → 追击
            Vec3d dir = target.getPos().subtract(bot.getPos()).normalize().multiply(0.15);
            bot.setVelocity(new Vec3d(dir.x, 0.08, dir.z));
            bot.velocityModified = true;
            failureContext = null;
            return CompletableFuture.completedFuture(ActionResult.partial(0.4, "追击中"));
        }

        bot.swingHand(Hand.MAIN_HAND);
        bot.attack(target);
        failureContext = null;
        return CompletableFuture.completedFuture(ActionResult.partial(0.7, "攻击"));
    }

    private LivingEntity findTarget(ServerWorld world, ServerPlayerEntity bot, String entityName) {
        List<? extends LivingEntity> all = world.getEntitiesByClass(
                LivingEntity.class,
                bot.getBoundingBox().expand(SCAN_RANGE),
                e -> e.isAlive() && e != bot);

        if (entityName != null && !entityName.isEmpty()) {
            for (var e : all) {
                String id = Registries.ENTITY_TYPE.getId(e.getType()).toString();
                if (id.toLowerCase().contains(entityName.toLowerCase())) return e;
            }
            return null;
        }

        if (all.isEmpty()) return null;
        return all.stream()
                .min((a, b) -> Double.compare(a.squaredDistanceTo(bot), b.squaredDistanceTo(bot)))
                .orElse(null);
    }

    @Override
    public void tick() {}

    @Override
    public FailureContext getFailureContext() { return failureContext; }
}
