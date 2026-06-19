package com.izimi.eagent.brainstem.domain;

import com.izimi.eagent.brainstem.adapter.ActionResult;
import com.izimi.eagent.brainstem.bot.BotPlayer;
import com.izimi.eagent.brainstem.navigation.NavigationController;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class MotionExecutor implements DomainExecutor<MotionCommand, ActionResult> {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    public static final double ARRIVAL_THRESHOLD_DEFAULT_SQ = 4.0;

    private final Map<UUID, NavigationController> navigationControllers = new ConcurrentHashMap<>();
    private final Map<UUID, BlockPos> currentMotionTargets = new ConcurrentHashMap<>();
    private FailureContext lastFailure;

    private static final Set<String> HANDLED_TYPES = Set.of("moveTo", "lookAt", "jump", "sprint", "sneak");

    @Override
    public boolean canHandle(String commandType) {
        return HANDLED_TYPES.contains(commandType);
    }

    @Override
    public CompletableFuture<ActionResult> submit(MotionCommand cmd) {
        if (cmd == null || cmd.bot() == null) {
            return CompletableFuture.completedFuture(ActionResult.unable("motion: bot为null"));
        }
        return CompletableFuture.completedFuture(switch (cmd.motionType()) {
            case "moveTo" -> moveTo(cmd.bot(), cmd.targetPos());
            case "jump" -> jump(cmd.bot());
            case "sprint" -> sprint(cmd.bot(), cmd.flag());
            case "sneak" -> sneak(cmd.bot(), cmd.flag());
            case "lookAt" -> lookAt(cmd.bot(), cmd.x(), cmd.y(), cmd.z());
            default -> ActionResult.unable("unknown motion: " + cmd.motionType());
        });
    }

    @Override
    public void tick() {
        // Push all active motion targets forward each tick
        for (var entry : currentMotionTargets.entrySet()) {
            // Future extension: truly async navigation per bot
        }
    }

    @Override
    public FailureContext getFailureContext() {
        return lastFailure;
    }

    public ActionResult moveTo(ServerPlayerEntity bot, BlockPos target) {
        if (bot == null || target == null) {
            lastFailure = FailureContext.of("moveTo", "bot或target为null");
            return ActionResult.unable("moveTo: bot或target为null");
        }

        NavigationController nav = navigationControllers.computeIfAbsent(
                bot.getUuid(), k -> new NavigationController());

        Vec3d botPos = bot.getPos();
        double dist = botPos.squaredDistanceTo(target.toCenterPos());

        LOGGER.info("[MOVE] target={} bot={} dist={} threshold={}",
            target, bot.getBlockPos(),
            String.format("%.1f", Math.sqrt(dist)),
            String.format("%.1f", Math.sqrt(ARRIVAL_THRESHOLD_DEFAULT_SQ)));

        if (dist < ARRIVAL_THRESHOLD_DEFAULT_SQ) {
            nav.stopNavigation(bot);
            currentMotionTargets.remove(bot.getUuid());
            LOGGER.info("[MOVE] arrived at {}", target);
            return ActionResult.success("已到达");
        }

        boolean navigating = nav.navigateTo(bot, target);
        if (navigating) {
            currentMotionTargets.put(bot.getUuid(), target);
            LOGGER.info("[MOVE] navigating to {} (dist={})", target, String.format("%.1f", Math.sqrt(dist)));
            return ActionResult.success("已到达");
        } else {
            currentMotionTargets.put(bot.getUuid(), target);
            LOGGER.info("[MOVE] in progress toward {} (dist={})", target, String.format("%.1f", Math.sqrt(dist)));
            return ActionResult.partial(Math.max(0, 1.0 - dist / 100.0), "移动中");
        }
    }

    public void stopNavigation(UUID botId) {
        navigationControllers.remove(botId);
        currentMotionTargets.remove(botId);
    }

    public ActionResult lookAt(ServerPlayerEntity bot, double x, double y, double z) {
        if (bot == null) return ActionResult.unable("lookAt: bot为null");

        Vec3d botPos = bot.getPos();
        double dx = x - botPos.x;
        double dy = y - (botPos.y + bot.getStandingEyeHeight());
        double dz = z - botPos.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, hDist));

        bot.setYaw(yaw);
        bot.setHeadYaw(yaw);
        bot.setPitch(pitch);

        return ActionResult.success("lookAt");
    }

    public ActionResult jump(ServerPlayerEntity bot) {
        if (bot == null) return ActionResult.unable("jump: bot为null");
        BotPlayer bp = BotPlayer.getByUUID(bot.getUuid());
        if (bp != null) {
            bp.setMoveInput(bp.getForward(), bp.getStrafe(), true);
        } else {
            bot.jump();
        }
        return ActionResult.success("跳跃");
    }

    public ActionResult sprint(ServerPlayerEntity bot, boolean sprinting) {
        if (bot == null) return ActionResult.unable("sprint: bot为null");
        bot.setSprinting(sprinting);
        return ActionResult.success("sprint: " + sprinting);
    }

    public ActionResult sneak(ServerPlayerEntity bot, boolean sneaking) {
        if (bot == null) return ActionResult.unable("sneak: bot为null");
        bot.setSneaking(sneaking);
        return ActionResult.success("sneak: " + sneaking);
    }
}
