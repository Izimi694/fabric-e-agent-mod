package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.api.BotContext;
import com.izimi.eagent.api.WorldContext;
import com.izimi.eagent.amygdala.BotParams;
import com.izimi.eagent.amygdala.OneShotAlarmSystem;
import com.izimi.eagent.brainstem.innate.InnateReflexRegistry;
import com.izimi.eagent.cortex.task.Task;
import com.izimi.eagent.hormonal.HormonalSystem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MotivationEngine {

    private static final double W_SURVIVAL = 1.0;
    private static final double W_STRESS = 0.8;
    // W_FEAR replaced by unified resource stress model
    private static final double W_TASK_PROG = 0.7;
    private static final double W_TASK_BASE = 0.3;
    private static final double W_SOCIAL = 0.5;
    private static final double W_LONELY = 0.4;
    private static final double W_CAUTIOUS = 0.5;
    private static final double W_RECENT_FAIL = 0.6;
    private static final double W_STRESS_CAUTIOUS = 0.3;
    private static final double W_TASK_INERTIA = 0.3;
    private static final double W_THREAT_CAUTIOUS = 0.6;

    // ── 统一资源压力参数 ──
    private static final double DEPLETION_SENSITIVITY = 2.0;
    private static final double HEALTH_BASELINE_DEPLETION = 0.02;
    private static final double HUNGER_BASELINE_DEPLETION = 0.02;
    private static final double HUNGER_ACTIVE_DEPLETION = 0.05;
    private static final double OXYGEN_SUBMERGED_DEPLETION = 0.1;

    private static final double CROSS_INHIBITION_RATIO = 0.7;
    private static final int INHIBITION_WINDOW = 5;
    /**
     * 麦穗策略: 基于置信度计算探索剩余窗口.
     * exploreProb = max(0, 0.37 - confidence).
     * 探索倾向额外受 DA 驱动.
     */
    public static double wheatEarExplore(double confidence, HormonalSystem hormones) {
        return wheatEarExplore(confidence, hormones, null, null);
    }

    public static double wheatEarExplore(double confidence, HormonalSystem hormones,
                                          String dimensionBias, Integer yLevelTarget) {
        double base = Math.max(0, (1.0 / Math.E) - confidence);
        if (hormones != null) {
            double daMod = hormones.getDA() * 0.3;
            base = Math.max(0, base + daMod);
        }
        if (dimensionBias != null) base = Math.min(1.0, base + 0.2);
        return Math.min(1.0, base);
    }

    private final Random rng = new Random();
    private Perspective lastWinner = null;
    private int inhibitionTicks = 0;

    private Map<Perspective, Double> perspectiveWeightOverrides;

    /** Replace default hardcoded perspective weights with playstyle-specific values */
    public void setPerspectiveWeights(Map<Perspective, Double> weights) {
        this.perspectiveWeightOverrides = weights != null && !weights.isEmpty()
            ? new HashMap<>(weights) : null;
    }

    /** Export current perspective weight overrides (null if not set) */
    public Map<String, Double> getPerspectiveWeights() {
        if (perspectiveWeightOverrides == null) return null;
        Map<String, Double> out = new HashMap<>();
        for (var e : perspectiveWeightOverrides.entrySet()) {
            out.put(e.getKey().name(), e.getValue());
        }
        return out;
    }

    private double weight(Perspective p) {
        if (perspectiveWeightOverrides != null) {
            Double w = perspectiveWeightOverrides.get(p);
            if (w != null) return w;
        }
        return switch (p) {
            case SURVIVAL -> W_SURVIVAL;
            case TASK -> W_TASK_BASE;
            case SOCIAL -> W_SOCIAL;
            case CURIOUS -> 0.0; // computed dynamically in computeCuriosityDrive
            case CAUTIOUS -> W_CAUTIOUS;
        };
    }

    public DriveState computeDrives(BotContext ctx, WorldContext world, ServerPlayerEntity bot) {
        HormonalSystem h = ctx.hormonalSystem();
        BotParams params = ctx.botParams();

        double survivalUrgency = computeSurvivalDrive(ctx, world, bot, h);
        double taskUrgency = computeTaskDrive(ctx, bot);
        double socialUrgency = computeSocialDrive(ctx, world, h);
        double curiosityUrgency = computeCuriosityDrive(ctx, h);
        double cautiousUrgency = computeCautiousDrive(ctx, h, params, bot);

        if (perspectiveWeightOverrides != null) {
            survivalUrgency *= weight(Perspective.SURVIVAL);
            taskUrgency *= weight(Perspective.TASK);
            socialUrgency *= weight(Perspective.SOCIAL);
            curiosityUrgency *= weight(Perspective.CURIOUS);
            cautiousUrgency *= weight(Perspective.CAUTIOUS);
        }

        return new DriveState(survivalUrgency, taskUrgency, socialUrgency, curiosityUrgency, cautiousUrgency);
    }

    public Perspective select(BotContext ctx, DriveState drives) {
        Perspective[] values = Perspective.values();
        double[] activations = new double[values.length];

        for (int i = 0; i < values.length; i++) {
            activations[i] = drives.get(values[i]);
            if (lastWinner != null && inhibitionTicks > 0 && values[i] != lastWinner) {
                activations[i] *= CROSS_INHIBITION_RATIO;
            }
        }

        double temperature = Math.max(0.05, ctx.botParams().getTemperature());
        double pressure = drives.pressure();
        temperature = temperature * (1.0 - pressure * 0.85);
        temperature = Math.max(0.05, Math.min(0.8, temperature));
        Perspective winner = boltzmannSample(activations, values, temperature);

        if (lastWinner == winner) {
            inhibitionTicks = inhibitionTicks > 0 ? inhibitionTicks - 1 : 0;
        } else {
            lastWinner = winner;
            inhibitionTicks = INHIBITION_WINDOW;
        }

        return winner;
    }

    private Perspective boltzmannSample(double[] activations, Perspective[] values, double temperature) {
        double maxActivation = 0;
        for (double a : activations) {
            if (a > maxActivation) maxActivation = a;
        }

        double[] probs = new double[activations.length];
        double sum = 0;
        for (int i = 0; i < activations.length; i++) {
            probs[i] = Math.exp((activations[i] - maxActivation) / temperature);
            sum += probs[i];
        }

        double rand = rng.nextDouble() * sum;
        double cumulative = 0;
        for (int i = 0; i < probs.length; i++) {
            cumulative += probs[i];
            if (rand <= cumulative) {
                return values[i];
            }
        }
        return values[values.length - 1];
    }

    private double computeSurvivalDrive(BotContext ctx, WorldContext world, ServerPlayerEntity bot, HormonalSystem h) {
        double drive = 0;
        if (bot != null) {
            // ── 收集上下文 ──
            var alarmSys = ctx.alarmSystem();
            List<OneShotAlarmSystem.ThreatInfo> threats = alarmSys != null
                    ? alarmSys.getThreatsNearby(bot) : List.of();
            int foodCount = countFoodItems(bot);

            // ── 生命值资源压力 ──
            double healthRatio = bot.getHealth() / bot.getMaxHealth();
            double healthDepletion = estimateHealthDepletionRate(threats);
            double healthReplenish = estimateHealthReplenishDifficulty(foodCount, threats);
            double healthStress = resourceStress(healthRatio, healthDepletion, healthReplenish);

            // ── 饥饿值资源压力 ──
            double hungerRatio = bot.getHungerManager().getFoodLevel() / 20.0;
            double hungerDepletion = estimateHungerDepletionRate(bot);
            double hungerReplenish = estimateHungerReplenishDifficulty(foodCount, threats);
            double hungerStress = resourceStress(hungerRatio, hungerDepletion, hungerReplenish);

            // ── 氧气资源压力 (仅水下) ──
            double oxygenStress = 0;
            if (bot.isSubmergedInWater()) {
                double airRatio = bot.getAir() / (double) bot.getMaxAir();
                oxygenStress = resourceStress(airRatio, OXYGEN_SUBMERGED_DEPLETION, 0.1);
            }

            // ── 取最大压力 + 激素调制 ──
            drive = Math.max(healthStress, Math.max(hungerStress, oxygenStress));

            // NE 放大整体生存压力，serotonin 压低
            if (h.getNE() > 0.3) drive += W_STRESS * h.getNE();
            if (h.getSerotonin() > 0.7) drive *= 0.7;

            // ── 先天反射额外加权 ──
            InnateReflexRegistry innate = world.brainstem().innateReflexes();
            if (innate != null && innate.highest(bot, 0) != null) drive += 0.4;
        }
        if (h.getNE() > 0.7) drive += 0.3;
        return Math.min(1.0, drive);
    }

    private double computeTaskDrive(BotContext ctx, ServerPlayerEntity bot) {
        double drive = 0;
        var tm = ctx.taskManager();
        Task task = tm != null ? tm.getActiveTask() : null;
        if (task != null && "running".equals(task.getStatus())) {
            drive += W_TASK_BASE;
            double prog = (double) task.progress.completedCount / Math.max(1, task.progress.targetCount);
            drive += W_TASK_PROG * prog;

            // ── 任务惯性: 连续成功→保持, 连续失败→松动 ──
            int fails = ctx.conditionedReflex() != null
                    ? ctx.conditionedReflex().getConsecutiveFailures() : 0;
            if (fails == 0) drive *= (1.0 + W_TASK_INERTIA);
            else if (fails <= 2) drive *= 1.0;
            else drive *= (1.0 - W_TASK_INERTIA * 0.5);
        }
        if (ctx.conditionedReflex() != null && ctx.conditionedReflex().getHighestProficiency() >= 0.8) {
            drive += 0.2;
        }
        return Math.min(1.0, drive);
    }

    private double computeSocialDrive(BotContext ctx, WorldContext world, HormonalSystem h) {
        double drive = 0;
        var social = world.amygdala().socialObserver();
        if (social != null && social.getNearbyPlayerCount() > 1) {
            drive += W_SOCIAL;
        }
        double avgIntimacy = 0;
        int cnt = 0;
        for (double v : h.getIntimacyMap().values()) {
            avgIntimacy += v;
            cnt++;
        }
        if (cnt > 0) avgIntimacy /= cnt;
        double loneliness = 1.0 - Math.min(1.0, avgIntimacy);
        if (loneliness > 0.3) drive += W_LONELY * loneliness;
        return Math.min(1.0, drive);
    }

    private double computeCuriosityDrive(BotContext ctx, HormonalSystem h) {
        double da = h.getDA();
        double ne = h.getNE();
        double ser = h.getSerotonin();
        double ach = h.getACh();

        double exploreScore = da * 0.4 + (1.0 - ach) * 0.3 + (1.0 - ser) * 0.3;
        double threshold = 0.5;
        if (ne > 0.5) exploreScore *= 0.5;

        return Math.min(1.0, exploreScore);
    }

    private double computeCautiousDrive(BotContext ctx, HormonalSystem h, BotParams params, ServerPlayerEntity bot) {
        double drive = 0;
        drive += W_CAUTIOUS * params.getBeta() * 10;
        var conditioned = ctx.conditionedReflex();
        if (conditioned != null) {
            int fails = conditioned.getConsecutiveFailures();
            if (fails >= 2) drive += W_RECENT_FAIL * Math.min(1.0, fails * 0.2);
        }
        drive += W_STRESS_CAUTIOUS * h.getNE();

        // ── 威胁距离 → 谨慎 ──
        if (bot != null) {
            var alarmSys = ctx.alarmSystem();
            if (alarmSys != null) {
                var threats = alarmSys.getThreatsNearby(bot);
                for (var t : threats) {
                    double distFactor = Math.max(0, 1.0 - t.distance() / 10.0);
                    drive += W_THREAT_CAUTIOUS * distFactor;
                }
            }
        }
        return Math.min(1.0, drive);
    }

    // ── 统一资源压力辅助方法 ──

    /** 统计背包中所有可食用物品总数 */
    private static int countFoodItems(ServerPlayerEntity bot) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = bot.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.contains(DataComponentTypes.FOOD)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 估算生命值消耗速率 (0-1) */
    private static double estimateHealthDepletionRate(List<OneShotAlarmSystem.ThreatInfo> threats) {
        double severity = 0;
        for (var t : threats) {
            double typeMul = switch (t.type()) {
                case "minecraft:creeper" -> 1.0;
                case "minecraft:skeleton" -> 0.7;
                case "minecraft:zombie" -> 0.5;
                case "minecraft:spider" -> 0.5;
                default -> 0.3;
            };
            double distFactor = Math.max(0, 1.0 - t.distance() / 10.0);
            severity += typeMul * distFactor;
        }
        return Math.min(1.0, HEALTH_BASELINE_DEPLETION + severity);
    }

    /** 估算饥饿值消耗速率 (0-1) */
    private static double estimateHungerDepletionRate(ServerPlayerEntity bot) {
        if (bot.isSprinting()) return HUNGER_ACTIVE_DEPLETION;
        return HUNGER_BASELINE_DEPLETION;
    }

    /** 估算生命值补给难度 (0=easy, 1=impossible) */
    private static double estimateHealthReplenishDifficulty(int foodCount, List<OneShotAlarmSystem.ThreatInfo> threats) {
        if (foodCount <= 0) return 1.0;
        boolean hasThreat = !threats.isEmpty();
        boolean lowFood = foodCount <= 3;
        double base = lowFood ? 0.6 : 0.2;
        return hasThreat ? Math.min(1.0, base + 0.3) : base;
    }

    /** 估算饥饿值补给难度 (0=easy, 1=impossible) */
    private static double estimateHungerReplenishDifficulty(int foodCount, List<OneShotAlarmSystem.ThreatInfo> threats) {
        if (foodCount <= 0) return 1.0;
        boolean hasThreat = !threats.isEmpty();
        if (foodCount <= 3) return hasThreat ? 0.8 : 0.6;
        return hasThreat ? 0.3 : 0.1;
    }

    /** 统一资源压力公式 */
    private static double resourceStress(double fillRatio, double depletionRate, double replenishDifficulty) {
        double effectiveDeficit = (1.0 - fillRatio) + depletionRate * DEPLETION_SENSITIVITY;
        effectiveDeficit = Math.max(0, Math.min(1.0, effectiveDeficit));
        return effectiveDeficit * (0.5 + 0.5 * replenishDifficulty);
    }
}
