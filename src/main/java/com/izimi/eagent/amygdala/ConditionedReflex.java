package com.izimi.eagent.amygdala;

import com.izimi.eagent.bayesian.BayesianFeature;
import com.izimi.eagent.bayesian.BayesianModule;
import com.izimi.eagent.brainstem.adapter.ActionResult;
import com.izimi.eagent.brainstem.adapter.BasicActionAdapter;
import com.izimi.eagent.brainstem.adapter.TemporalScaler;
import com.izimi.eagent.brainstem.scheduler.DeviationCounter;
import com.izimi.eagent.brainstem.scheduler.Perspective;
import com.izimi.eagent.brainstem.scheduler.ReflexSatisfaction;
import com.izimi.eagent.brainstem.skill.Skill;
import com.izimi.eagent.brainstem.skill.SkillManager;
import com.izimi.eagent.brainstem.bot.BotInstance;
import com.izimi.eagent.config.ModConfig;
import com.izimi.eagent.hippocampus.MemoryGraph;
import com.izimi.eagent.hippocampus.MemoryManager;
import com.izimi.eagent.amygdala.learning.CategoryMapper;
import com.izimi.eagent.amygdala.learning.ObservedSequence;
import static com.izimi.eagent.amygdala.ReflexConstants.*;
import com.izimi.eagent.cortex.task.Task;
import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;
import com.izimi.eagent.amygdala.ReflexIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConditionedReflex {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    private static final double SELF_REINFORCE_SUCCESS = 0.05;
    private static final double SELF_REINFORCE_FAIL = -0.03;
    private static final double DEFAULT_WEIGHT = 0.5;
    private static final double WEIGHT_MIN = -1.0;
    private static final double WEIGHT_MAX = 1.0;
    private static final double EW_STW_RATIO = 0.7;
    private static final double EW_LTB_RATIO = 0.3;
    private static final double SPILL_RATIO = 0.3;

    private final SkillManager skillManager;
    private final ModConfig config;
    private final BasicActionAdapter actionAdapter;
    private final BotParams botParams;
    private final UUID botId;
    private BayesianModule bayesianModule;
    private BotInstance botInstance;

    private final Map<String, List<Double>> actionHistory = new ConcurrentHashMap<>();
    private int actionCount = 0;
    private String lastExecutedReflexId;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private int recentSuccessCount = 0;
    private final DeviationCounter deviationCounter = new DeviationCounter();

    private final Map<String, Set<String>> prevPointers = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> nextPointers = new ConcurrentHashMap<>();
    private final Map<String, Boolean> bottleneckCache = new ConcurrentHashMap<>();

    public void setPrev(String reflexId, String prevId) {
        prevPointers.computeIfAbsent(reflexId, k -> ConcurrentHashMap.newKeySet()).add(prevId);
    }

    public void setNext(String reflexId, String nextId) {
        nextPointers.computeIfAbsent(reflexId, k -> ConcurrentHashMap.newKeySet()).add(nextId);
    }

    public Set<String> getPrev(String reflexId) {
        return prevPointers.getOrDefault(reflexId, Collections.emptySet());
    }

    public Set<String> getNext(String reflexId) {
        return nextPointers.getOrDefault(reflexId, Collections.emptySet());
    }

    public void markBottleneck(String reflexId, boolean isBottleneck) {
        bottleneckCache.put(reflexId, isBottleneck);
        Path reflexPath = reflexPath(reflexId);
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(reflexPath);
        if (data != null) {
            data.put("isBottleneck", isBottleneck);
            JsonUtil.writeToFileSafeAtomic(reflexPath, data);
        }
    }

    public boolean isBottleneck(String reflexId) {
        return bottleneckCache.getOrDefault(reflexId, false);
    }

    public double getConfidence(String reflexId) {
        return ReflexIO.getConfidence(reflexId, botId);
    }

    public double getSharedWeight(String reflexId, String taskId) {
        double base = getConfidence(reflexId);
        double taskConf = 0.5;
        if (bayesianModule != null) {
            taskConf = bayesianModule.predictSuccess(reflexId, Collections.emptyList());
        }
        return base * taskConf;
    }

    public boolean isReflexStable(String reflexId) {
        Path reflexPath = reflexPath(reflexId);
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(reflexPath);
        if (data == null) return false;
        int execCount = ((Number) data.getOrDefault("executionCount", 0)).intValue();
        boolean healthy = STATUS_HEALTHY.equals(data.getOrDefault(KEY_STATUS, STATUS_HEALTHY));
        return execCount >= 10 && healthy && consecutiveFailures.get() == 0;
    }

    public record PreconditionResult(boolean passed, String reason, String failStrategy) {}

    public PreconditionResult checkPreconditions(String reflexId, net.minecraft.server.network.ServerPlayerEntity bot) {
        return checkPreconditions(reflexId, bot, null);
    }

    public PreconditionResult checkPreconditions(String reflexId, net.minecraft.server.network.ServerPlayerEntity bot,
                                                 com.izimi.eagent.hormonal.HormonalSystem hormonalSystem) {
        if (bot == null) return new PreconditionResult(true, null, "skip");
        Path reflexPath = reflexPath(reflexId);
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(reflexPath);
        if (data == null) return new PreconditionResult(true, null, "skip");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> preconditions = (List<Map<String, Object>>) data.get("preconditions");
        if (preconditions == null || preconditions.isEmpty()) {
            return new PreconditionResult(true, null, "skip");
        }

        for (Map<String, Object> pc : preconditions) {
            String type = (String) pc.get("type");
            String key = (String) pc.get("key");
            String match = (String) pc.get("match");
            String operator = (String) pc.get("operator");
            double value = ((Number) pc.getOrDefault("value", 0.0)).doubleValue();
            String failStrategy = (String) pc.getOrDefault("fail_strategy", "skip");

            boolean passed = switch (type) {
                case "item" -> checkItemPrecondition(bot, key, match);
                case "state" -> checkStatePrecondition(bot, key, operator, value, hormonalSystem);
                default -> true;
            };

            if (!passed) {
                return new PreconditionResult(false, "precondition_failed:" + type + "." + key, failStrategy);
            }
        }
        return new PreconditionResult(true, null, "skip");
    }

    private boolean checkItemPrecondition(net.minecraft.server.network.ServerPlayerEntity bot, String key, String match) {
        var inventory = bot.getInventory();
        if ("main_hand".equals(key)) {
            var stack = inventory.getMainHandStack();
            return stack.isEmpty() || match == null || stack.getItem().toString().toLowerCase().contains(match.toLowerCase());
        }
        for (int i = 0; i < inventory.size(); i++) {
            var stack = inventory.getStack(i);
            if (!stack.isEmpty()) {
                String name = stack.getItem().toString().toLowerCase();
                if (match == null || name.contains(match.toLowerCase())) return true;
            }
        }
        return false;
    }

    private boolean checkStatePrecondition(net.minecraft.server.network.ServerPlayerEntity bot, String key,
                                            String operator, double value,
                                            com.izimi.eagent.hormonal.HormonalSystem hormonalSystem) {
        double current = switch (key) {
            case "stress" -> hormonalSystem != null ? hormonalSystem.getStress() : 0.0;
            case "health" -> bot.getHealth() / bot.getMaxHealth();
            default -> 0.0;
        };
        return switch (operator) {
            case "<" -> current < value;
            case ">" -> current > value;
            case "<=" -> current <= value;
            case ">=" -> current >= value;
            default -> true;
        };
    }

    public ConditionedReflex(SkillManager skillManager, ModConfig config, BasicActionAdapter actionAdapter) {
        this(skillManager, config, actionAdapter, null);
    }

    public void setBayesianModule(BayesianModule bayesianModule) {
        this.bayesianModule = bayesianModule;
    }

    public void setBotInstance(BotInstance botInstance) {
        this.botInstance = botInstance;
    }

    public ConditionedReflex(SkillManager skillManager, ModConfig config, BasicActionAdapter actionAdapter, UUID botId) {
        this.skillManager = skillManager;
        this.config = config;
        this.actionAdapter = actionAdapter;
        this.botId = botId;
        this.botParams = botId != null ? BotParams.generate() : BotParams.load();
    }

    private Path conditionedDir() {
        return ReflexIO.conditionedDir(botId);
    }

    private Path reflexPath(String reflexId) {
        return ReflexIO.reflexPath(reflexId, botId);
    }

    public Skill match(Task task) {
        if (task == null) return null;
        String goal = task.getGoal().toLowerCase();

        for (Map.Entry<String, Skill> entry : skillManager.getSkills().entrySet()) {
            Skill skill = entry.getValue();
            if ("conditioned".equals(skill.getType())) {
                String skillId = skill.getSkillId().toLowerCase();
                String displayName = skill.getName().toLowerCase();

                if (goal.contains(displayName)) return skill;

                String shortId = skillId.replace("reflex_", "").replace("_", " ");
                if (goal.contains(shortId)) return skill;
            }
        }
        return null;
    }

    public Skill scanAndTrigger(ServerPlayerEntity bot) {
        return scanAndTrigger(bot, null);
    }

    public Skill scanAndTrigger(ServerPlayerEntity bot, Perspective domain) {
        if (bot == null) return null;

        record Candidate(Skill skill, double score, int atomIndex) {}
        List<Candidate> candidates = new ArrayList<>();
        List<BayesianFeature> contextFeatures = extractContextFeatures(bot);
        double timeScale = computeTimeScale();
        double hungerRatio = bot.getHungerManager().getFoodLevel() / 20.0;
        double dangerLevel = computeDangerLevel(bot);
        boolean legacy = botInstance != null && botInstance.isLegacyScoring();

        for (Map.Entry<String, Skill> entry : skillManager.getSkills().entrySet()) {
            Skill skill = entry.getValue();
            if (!"conditioned".equals(skill.getType())) continue;

            Path reflexPath = conditionedDir().resolve(skill.getSkillId() + ".json");
            Map<String, Object> data = JsonUtil.readMapFromFileSafe(reflexPath);
            if (data == null) continue;

            String status = (String) data.getOrDefault(KEY_STATUS, STATUS_HEALTHY);
            if (STATUS_DEPRECATED.equals(status) || STATUS_DORMANT.equals(status)) continue;

            double reflexWeight = effectiveWeight(data);
            double decayFactor = computeDecayFactor(data);
            double bayesianMultiplier = bayesianModule != null
                    ? Math.max(0.1, bayesianModule.predictSuccess(skill.getSkillId(), contextFeatures))
                    : 1.0;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> atoms = (List<Map<String, Object>>) data.get(KEY_ATOMS);

            if (atoms != null && !atoms.isEmpty()) {
                for (int i = 0; i < atoms.size(); i++) {
                    Map<String, Object> atom = atoms.get(i);
                    String atomStatus = (String) atom.getOrDefault(KEY_STATUS, STATUS_HEALTHY);
                    if (STATUS_DEPRECATED.equals(atomStatus) || STATUS_IMPOSSIBLE.equals(atomStatus)) continue;
                    String atomTarget = (String) atom.get("atomTarget");
                    if (atomTarget == null) continue;
                    double atomProficiency = ((Number) atom.getOrDefault(KEY_PROFICIENCY, 0.0)).doubleValue();
                    if (isAtomTargetNearby(bot, (String) atom.get("action"), atomTarget)) {
                        double memoryBoost = computeMemoryBoost(skill.getSkillId());
                        double score = scoreReflex(reflexWeight, atomProficiency, bayesianMultiplier, memoryBoost,
                                decayFactor, timeScale, dangerLevel, hungerRatio, legacy, domain, atoms.size());
                        candidates.add(new Candidate(skill, score, i));
                    }
                }
            } else {
                String category = (String) data.get("category");
                if (category != null && isTargetNearby(bot, category, data)) {
                    double compoundProficiency = ((Number) data.getOrDefault(KEY_PROFICIENCY, 0.0)).doubleValue();
                    double memoryBoost = computeMemoryBoost(skill.getSkillId());
                    double score = scoreReflex(reflexWeight, compoundProficiency, bayesianMultiplier, memoryBoost,
                            decayFactor, timeScale, dangerLevel, hungerRatio, legacy, domain, 1);
                    candidates.add(new Candidate(skill, score, -1));
                }
            }
        }

        if (candidates.isEmpty()) return null;
        candidates.sort((a, b) -> Double.compare(b.score(), a.score()));
        Candidate best = candidates.get(0);

        LOGGER.info("[ConditionedReflex] scanAndTrigger: selected={} score={} ({} candidates)",
                best.skill().getSkillId(), String.format("%.4f", best.score()), candidates.size());

        if (best.atomIndex() >= 0) {
            Path reflexPath = conditionedDir().resolve(best.skill().getSkillId() + ".json");
            Map<String, Object> data = JsonUtil.readMapFromFileSafe(reflexPath);
            if (data != null) {
                data.put("currentAtomIndex", best.atomIndex());
                JsonUtil.writeToFileSafeAtomic(reflexPath, data);
            }
        }
        return best.skill();
    }

    private double computeTimeScale() {
        if (botInstance == null || botInstance.getBotContext() == null) return 1.0;
        var h = botInstance.getBotContext().hormonalSystem();
        return h != null ? TemporalScaler.computeTimeScale(h) : 1.0;
    }

    private double computeDangerLevel(ServerPlayerEntity bot) {
        double healthRatio = Math.max(0, bot.getHealth() / bot.getMaxHealth());
        boolean isNight = !bot.getWorld().isDay();
        int consecFails = consecutiveFailures.get();
        double dangerLevel = (1.0 - healthRatio) * 0.5;
        if (botInstance != null && botInstance.getBotContext() != null) {
            var h = botInstance.getBotContext().hormonalSystem();
            if (h != null) {
                dangerLevel = Math.max(dangerLevel, h.getNE() * 0.3);
                if (h.getNE() < 0.5) dangerLevel += h.getSerotonin() * 0.15;
            }
        }
        if (isNight) dangerLevel += 0.1;
        dangerLevel += Math.min(0.15, consecFails * 0.04);
        return Math.max(0, Math.min(1, dangerLevel));
    }

    private double scoreReflex(double reflexWeight, double proficiency, double bayesianMultiplier,
                               double memoryBoost, double decayFactor, double timeScale, double dangerLevel,
                               double hungerRatio, boolean legacy, Perspective domain, int atomCount) {
        if (legacy) return reflexWeight * proficiency * bayesianMultiplier * decayFactor * memoryBoost;
        double estimatedSec = ReflexSatisfaction.estimateSeconds(atomCount);
        double riskScore = clampScore(1.0 - dangerLevel * (1.0 - bayesianMultiplier));
        double resourceScore = clampScore(0.3 + hungerRatio * 0.3 + bayesianMultiplier * 0.4);
        double base = domain != null
                ? ReflexSatisfaction.computeForDomainWithScale(estimatedSec, timeScale,
                        reflexWeight, proficiency, bayesianMultiplier, decayFactor,
                        riskScore, resourceScore, domain)
                : ReflexSatisfaction.computeWithScale(estimatedSec, timeScale,
                        reflexWeight, proficiency, bayesianMultiplier, decayFactor,
                        riskScore, resourceScore);
        return base * memoryBoost;
    }

    private double computeMemoryBoost(String reflexId) {
        if (botInstance == null) return 1.0;
        var ctx = botInstance.getBotContext();
        if (ctx == null) return 1.0;
        MemoryManager mem = ctx.memoryManager();
        if (mem == null) return 1.0;
        MemoryGraph mg = mem.getMemoryGraph();
        if (mg == null) return 1.0;

        List<String> nodeIds = mg.findNodeIdsByReflex(reflexId);
        if (nodeIds == null || nodeIds.isEmpty()) return 1.0;
        return 1.0 + Math.min(0.3, nodeIds.size() * 0.03);
    }

    private boolean isTargetNearby(ServerPlayerEntity bot, String category, Map<String, Object> reflexData) {
        if (category.startsWith("dig_")) {
            return isDigTargetNearby(bot, reflexData);
        }
        if (category.startsWith("attack_")) {
            return isAttackTargetNearby(bot, reflexData);
        }
        return false;
    }

    private boolean isDigTargetNearby(ServerPlayerEntity bot, Map<String, Object> reflexData) {
        BlockPos botPos = bot.getBlockPos();
        List<String> contributedTargets = (List<String>) reflexData.get("contributedTargets");
        List<String> searchTargets = contributedTargets != null ? contributedTargets : List.of();

        if (searchTargets.isEmpty()) {
            String target = (String) reflexData.get("target");
            if (target != null) searchTargets = List.of(target);
        }
        if (searchTargets.isEmpty()) return false;

        return scanBlocksForTargets(bot.getServerWorld(), botPos, 8, searchTargets);
    }

    private boolean isAttackTargetNearby(ServerPlayerEntity bot, Map<String, Object> reflexData) {
        var world = bot.getServerWorld();
        String target = (String) reflexData.get("target");
        if (target == null) return false;

        var entities = world.getEntitiesByClass(LivingEntity.class,
                bot.getBoundingBox().expand(8),
                e -> e.isAlive() && e != bot);
        for (var entity : entities) {
            String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            if (entityId.toLowerCase().contains(target.toLowerCase())) return true;
        }
        return false;
    }

    private boolean scanBlocksForTargets(ServerWorld world, BlockPos botPos, int scanRange, List<String> targets) {
        for (int dx = -scanRange; dx <= scanRange; dx++) {
            for (int dy = -scanRange; dy <= scanRange; dy++) {
                for (int dz = -scanRange; dz <= scanRange; dz++) {
                    BlockPos pos = botPos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() || state.isOf(Blocks.BEDROCK)) continue;
                    String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                    for (String target : targets) {
                        if (blockId.toLowerCase().contains(target.toLowerCase())) return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isAtomTargetNearby(ServerPlayerEntity bot, String action, String atomTarget) {
        if ("equipItem".equals(action) || atomTarget.startsWith("equip_")) return true;

        String search = stripPrefix(atomTarget);

        if ("dig".equals(action) || atomTarget.startsWith("dig_")) {
            return scanBlocksForAtomTarget(bot.getServerWorld(), bot.getBlockPos(), search, atomTarget);
        }

        if ("attack".equals(action) || atomTarget.startsWith("attack_")) {
            return scanEntitiesForTarget(bot, search);
        }

        if ("moveTo".equals(action) || atomTarget.startsWith("moveTo_")) {
            return scanBlocksForAtomTarget(bot.getServerWorld(), bot.getBlockPos(), search, null);
        }

        return false;
    }

    private static String stripPrefix(String atomTarget) {
        if (atomTarget.contains("_")) {
            return atomTarget.substring(atomTarget.indexOf('_') + 1);
        }
        return atomTarget;
    }

    private boolean scanBlocksForAtomTarget(ServerWorld world, BlockPos botPos, String search, String categoryCheck) {
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -8; dy <= 8; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos pos = botPos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() || state.isOf(Blocks.BEDROCK)) continue;
                    String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                    if (matchCategoryTarget(blockId, search, categoryCheck)) return true;
                    if (blockId.toLowerCase().contains(search.toLowerCase())) return true;
                }
            }
        }
        return false;
    }

    private boolean scanEntitiesForTarget(ServerPlayerEntity bot, String search) {
        var world = bot.getServerWorld();
        var entities = world.getEntitiesByClass(LivingEntity.class,
                bot.getBoundingBox().expand(8),
                e -> e.isAlive() && e != bot);
        for (var entity : entities) {
            String entityId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            if (entityId.toLowerCase().contains(search.toLowerCase())) return true;
        }
        return false;
    }

    private static boolean matchCategoryTarget(String blockId, String search, String categoryCheck) {
        for (var rule : CategoryMapper.getCategoryRules().entrySet()) {
            if (!rule.getKey().equals(search) && !rule.getKey().equals(categoryCheck)) continue;
            for (String pattern : rule.getValue()) {
                if (blockId.toLowerCase().contains(pattern.toLowerCase())) return true;
            }
        }
        return false;
    }

    private List<String> extractTargets(ObservedSequence sequence) {
        List<String> targets = new ArrayList<>();
        for (ObservedSequence.Step step : sequence.steps()) {
            String t = step.target();
            if (!targets.contains(t)) {
                targets.add(t);
            }
        }
        return targets;
    }

    private List<Map<String, Object>> buildAtomEntries(List<Map<String, String>> steps) {
        List<Map<String, Object>> atoms = new ArrayList<>();
        for (Map<String, String> step : steps) {
            Map<String, Object> atom = new LinkedHashMap<>();
            atom.put("action", step.get("action"));
            atom.put("target", step.get("target"));
            atom.put("atomTarget", buildAtomTarget(step.get("action"), step.get("target")));
            atom.put(KEY_PROFICIENCY, 0.1);
            atom.put(KEY_STATUS, STATUS_HEALTHY);
            atoms.add(atom);
        }
        return atoms;
    }

    private String buildAtomTarget(String action, String target) {
        if ("equipItem".equals(action)) {
            return "equip_" + target.toLowerCase().replace(" ", "_");
        }
        if ("moveTo".equals(action)) {
            String cat = CategoryMapper.getCategory("dig", target);
            return "moveTo_" + (cat.contains("_") ? cat.substring(cat.indexOf('_') + 1) : target.toLowerCase());
        }
        return CategoryMapper.getCategory(action, target);
    }

    public static void resetReflexWeights(Path reflexPath) {
        ReflexIO.resetReflexWeights(reflexPath);
    }

    public List<BayesianFeature> extractContextFeatures(ServerPlayerEntity bot) {
        if (bot == null) return Collections.emptyList();
        List<BayesianFeature> features = new ArrayList<>();

        float healthRatio = bot.getHealth() / bot.getMaxHealth();
        features.add(new BayesianFeature("low_health", healthRatio < 0.3));
        features.add(new BayesianFeature("injured", healthRatio < 0.6));

        int foodLevel = bot.getHungerManager().getFoodLevel();
        features.add(new BayesianFeature("hungry", foodLevel < 10));

        if (bot.getServerWorld() != null) {
            features.add(new BayesianFeature("night_time", !bot.getServerWorld().isDay()));
            features.add(new BayesianFeature("underwater", bot.isSubmergedInWater()));
        }

        boolean hasWeapon = false;
        for (int i = 0; i < 9; i++) {
            var stack = bot.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.item.SwordItem) {
                hasWeapon = true;
                break;
            }
        }
        features.add(new BayesianFeature("holding_weapon", hasWeapon));

        return features;
    }

    public void recordAction(String skillId, double effectiveness) {
        actionHistory.computeIfAbsent(skillId, k -> new ArrayList<>()).add(effectiveness);
        actionCount++;

        if (actionCount >= 20) {
            analyzeAndGenerate();
            actionCount = 0;
        }
    }

    public void executeReflex(Skill reflex, ServerPlayerEntity bot) {
        if (!(reflex instanceof ConditionedSkill)) return;

        String skillId = reflex.getSkillId();
        Path reflexPath = reflexPath(skillId);
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(reflexPath);
        if (data == null) return;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> atoms = (List<Map<String, Object>>) data.get(KEY_ATOMS);
        int atomIdx = ((Number) data.getOrDefault("currentAtomIndex", 0)).intValue();

        Skill.SkillResult result = executeAtomAction(reflex, skillId, data, atoms, atomIdx, bot);
        if (result == null) return;

        if (result.executed()) {
            lastExecutedReflexId = skillId;
            double delta = result.success() ? SELF_REINFORCE_SUCCESS : SELF_REINFORCE_FAIL;
            reinforce(data, delta);
        }

        recordReflexOutcome(skillId, bot, result, atoms, atomIdx);
        updateLastAccessed(skillId);
    }

    private Skill.SkillResult executeAtomAction(Skill reflex, String skillId, Map<String, Object> data,
                                                  List<Map<String, Object>> atoms, int atomIdx, ServerPlayerEntity bot) {
        if (atoms != null && atomIdx >= 0 && atomIdx < atoms.size()) {
            Map<String, Object> atom = atoms.get(atomIdx);
            if (STATUS_IMPOSSIBLE.equals(atom.getOrDefault(KEY_STATUS, STATUS_HEALTHY))) {
                LOGGER.debug("[ConditionedReflex] 原子已标记不可能，跳过: {} [{}]", skillId, atom.get("action"));
                return null;
            }
            ActionResult adapterResult = dispatchAction((String) atom.get("action"), (String) atom.get("target"), atom, bot);
            Skill.SkillResult result;
            if (adapterResult != null) {
                result = new Skill.SkillResult(adapterResult.executed(), adapterResult.success(),
                        adapterResult.effectiveness(), adapterResult.message());
            } else {
                Skill innate = skillManager.getSkill((String) atom.get("action"));
                if (innate != null) {
                    result = innate.execute(bot.getServerWorld(), bot, new HashMap<>());
                } else {
                    result = ((ConditionedSkill) reflex).execute(bot.getServerWorld(), bot, new HashMap<>());
                }
            }
            LOGGER.debug("[ConditionedReflex] 原子{}/{}: {} {} → executed={} success={}",
                    atomIdx + 1, atoms.size(), atom.get("action"), atom.get("target"),
                    result.executed(), result.success());
            return result;
        }
        return ((ConditionedSkill) reflex).execute(bot.getServerWorld(), bot, new HashMap<>());
    }

    private void recordReflexOutcome(String skillId, ServerPlayerEntity bot, Skill.SkillResult result,
                                      List<Map<String, Object>> atoms, int atomIdx) {
        updateReflexStats(skillId, result.success(), result.effectiveness());
        if (bayesianModule != null) {
            bayesianModule.update(skillId, extractContextFeatures(bot), result.success());
        }
        reinforceMemoryGraph(skillId, result.success());
        if (result.success()) {
            LOGGER.info("[ConditionedReflex] 成功: {}", skillId);
            if (botInstance != null) {
                String category = getReflexCategoryPublic(skillId);
                if (category != null) botInstance.getBotContext().world().botManager().notifyReflexSuccess(bot, category);
            }
        } else {
            handleReflexFailure(skillId, bot, atoms, atomIdx);
        }
    }

    public enum ReflexHealth { HEALTHY, WATCHING, DORMANT }

    public record ReflexState(int executionCount, double successRate, ReflexHealth health) {}

    private void updateReflexStats(String skillId, boolean success, double effectiveness) {
        Path path = reflexPath(skillId);
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(path);
        if (data == null) return;

        String status = (String) data.getOrDefault(KEY_STATUS, STATUS_HEALTHY);
        if (STATUS_TRIAL.equals(status)) {
            handleTrialResult(data, path, skillId, success);
            return;
        }

        if (success) {
            consecutiveFailures.set(0);
            recentSuccessCount++;
        } else {
            consecutiveFailures.incrementAndGet();
        }

        data.put("lastEffectiveness", effectiveness);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> atoms = (List<Map<String, Object>>) data.get(KEY_ATOMS);
        if (atoms != null && !atoms.isEmpty()) {
            int atomIdx = ((Number) data.getOrDefault("currentAtomIndex", 0)).intValue();
            if (atomIdx >= 0 && atomIdx < atoms.size()) {
                Map<String, Object> atom = atoms.get(atomIdx);
                atom.put(KEY_PROFICIENCY, Math.min(1.0,
                        ((Number) atom.getOrDefault(KEY_PROFICIENCY, 0.1)).doubleValue() + (success ? 0.05 : 0.0)));

                if (success && atomIdx + 1 < atoms.size()) {
                    data.put("currentAtomIndex", atomIdx + 1);
                }
            }
        }

        JsonUtil.writeToFileSafeAtomic(path, data);
    }

    private void handleTrialResult(Map<String, Object> data, Path path, String skillId, boolean success) {
        int trialSuccesses = ((Number) data.getOrDefault("trialSuccesses", 0)).intValue();
        int trialFailures = ((Number) data.getOrDefault("trialFailures", 0)).intValue();

        if (success) {
            trialSuccesses++;
            data.put("trialSuccesses", trialSuccesses);
        } else {
            trialFailures++;
            data.put("trialFailures", trialFailures);
        }

        boolean converged = bayesianModule != null && bayesianModule.isConverged(skillId);
        double posterior = bayesianModule != null ? bayesianModule.predictSuccess(skillId, Collections.emptyList()) : 0.5;

        if (converged && posterior > 0.5) {
            data.put(KEY_STATUS, STATUS_HEALTHY);
            data.put(KEY_PROFICIENCY, 0.5);
            data.put("trialSuccesses", 0);
            data.put("trialFailures", 0);
            JsonUtil.writeToFileSafeAtomic(path, data);
            LOGGER.info("[ConditionedReflex] 试炼通过: {} ({}成功/{}失败), 正式加入",
                    skillId, trialSuccesses, trialFailures);
            return;
        }

        if (converged && posterior < 0.3) {
            data.put(KEY_STATUS, STATUS_DORMANT);
            data.put(KEY_PROFICIENCY, 0.1);
            JsonUtil.writeToFileSafeAtomic(path, data);
            moveToArchived(skillId, data);
            LOGGER.info("[ConditionedReflex] 试炼失败: {} ({}成功/{}失败), 休眠",
                    skillId, trialSuccesses, trialFailures);
            return;
        }

        JsonUtil.writeToFileSafeAtomic(path, data);
    }

    private void handleReflexFailure(String skillId, ServerPlayerEntity bot,
                                      List<Map<String, Object>> atoms, int atomIdx) {
        Path path = reflexPath(skillId);
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(path);
        if (data == null) return;
        if (STATUS_TRIAL.equals(data.getOrDefault(KEY_STATUS, STATUS_HEALTHY))) return;
        if (bayesianModule == null) return;

        List<BayesianFeature> features = bot != null ? extractContextFeatures(bot) : Collections.emptyList();
        double posterior = bayesianModule.predictSuccess(skillId, features);
        boolean converged = bayesianModule.isConverged(skillId);

        classifyAndApplyFailure(skillId, bot, atoms, atomIdx, data, path, posterior, converged);
    }

    private enum FailureSeverity { RETRY, PROBABILISTIC, WATCH, IMPOSSIBLE_ATOM, DORMANT }

    private void classifyAndApplyFailure(String skillId, ServerPlayerEntity bot,
                                          List<Map<String, Object>> atoms, int atomIdx,
                                          Map<String, Object> data, Path path,
                                          double posterior, boolean converged) {
        String currentStatus = (String) data.getOrDefault(KEY_STATUS, STATUS_HEALTHY);
        FailureSeverity severity;
        if (!converged) severity = FailureSeverity.RETRY;
        else if (posterior > 0.4) severity = FailureSeverity.PROBABILISTIC;
        else if (posterior > 0.2) severity = !STATUS_WATCHING.equals(currentStatus) ? FailureSeverity.WATCH : FailureSeverity.RETRY;
        else if (atoms != null && atomIdx >= 0 && atomIdx < atoms.size()) severity = FailureSeverity.IMPOSSIBLE_ATOM;
        else severity = FailureSeverity.DORMANT;

        switch (severity) {
            case RETRY -> LOGGER.debug("[ConditionedReflex] 样本不足(未收敛)，重试: {}", skillId);
            case PROBABILISTIC -> LOGGER.debug("[ConditionedReflex] 概率性失败: {} (posterior={:.2f})", skillId, posterior);
            case WATCH -> {
                data.put(KEY_STATUS, STATUS_WATCHING);
                JsonUtil.writeToFileSafeAtomic(path, data);
                LOGGER.warn("[ConditionedReflex] 标记观察: {} (posterior={:.2f})", skillId, posterior);
            }
            case IMPOSSIBLE_ATOM -> {
                Map<String, Object> atom = atoms.get(atomIdx);
                String aTarget = (String) atom.getOrDefault("target", atom.get("action"));
                atom.put(KEY_STATUS, STATUS_IMPOSSIBLE);
                data.put(KEY_ATOMS, atoms);
                JsonUtil.writeToFileSafeAtomic(path, data);
                LOGGER.warn("[ConditionedReflex] 原子永久跳过: {} [{}] (posterior={:.2f})", skillId, aTarget, posterior);
                if (bot != null) bot.sendMessage(Text.literal("§c[E-Agent] §7" + aTarget + " 这个我做不了，跳过"));
            }
            case DORMANT -> {
                if (!STATUS_DORMANT.equals(currentStatus)) {
                    data.put(KEY_STATUS, STATUS_DORMANT);
                    JsonUtil.writeToFileSafeAtomic(path, data);
                    moveToArchived(skillId, data);
                    LOGGER.warn("[ConditionedReflex] 标记休眠并归档: {} (posterior={:.2f})", skillId, posterior);
                    if (bot != null) bot.sendMessage(Text.literal("§c[E-Agent] §7这个" +
                            data.getOrDefault("displayName", skillId) + "好像不太对，我先放一放..."));
                }
            }
        }
    }

    public ReflexState getReflexState(String skillId) {
        Path path = reflexPath(skillId);
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(path);
        if (data == null) return new ReflexState(0, 0.0, ReflexHealth.HEALTHY);

        String status = (String) data.getOrDefault(KEY_STATUS, STATUS_HEALTHY);

        ReflexHealth health = switch (status) {
            case STATUS_DORMANT -> ReflexHealth.DORMANT;
            case STATUS_WATCHING -> ReflexHealth.WATCHING;
            default -> ReflexHealth.HEALTHY;
        };

        boolean converged = bayesianModule != null && bayesianModule.isConverged(skillId);
        double posterior = bayesianModule != null ? bayesianModule.predictSuccess(skillId, Collections.emptyList()) : 0.5;
        int sampleEstimate = converged ? 5 : 0;

        return new ReflexState(sampleEstimate, posterior, health);
    }

    public void solidifySequence(ObservedSequence sequence) {
        String category = CategoryMapper.getCategory(
                sequence.steps().get(0).action(), sequence.target());
        solidifySequence(sequence, category);
    }

    public void solidifySequence(ObservedSequence sequence, String category) {
        String skillId = "reflex_" + category;
        String name = CategoryMapper.getCategoryDisplayName(category);

        if (skillManager.getSkill(skillId) != null) {
            incrementProficiency(skillId);
            LOGGER.info("[ConditionedReflex] 分类已有反射，强化熟练度: {} -> {}", category, skillId);
            return;
        }

        ConditionedSkill skill = new ConditionedSkill(skillId, name);
        skillManager.registerConditionedSkill(skill);

        Map<String, Object> reflexData = new LinkedHashMap<>();
        reflexData.put("skillId", skillId);
        reflexData.put("type", "conditioned");
        reflexData.put("displayName", name);
        reflexData.put("category", category);
        reflexData.put("source", sequence.source());
        reflexData.put("occurrences", sequence.occurrences());
        reflexData.put(KEY_PROFICIENCY, sequence.proficiency());
        reflexData.put(KEY_SHORT_TERM_WEIGHT, DEFAULT_WEIGHT);
        reflexData.put(KEY_LONG_TERM_BASELINE, DEFAULT_WEIGHT);
        reflexData.put("target", sequence.target());
        reflexData.put("contributedTargets", extractTargets(sequence));
        reflexData.put("solidifiedAt", System.currentTimeMillis());
        reflexData.put(KEY_LAST_ACCESSED, System.currentTimeMillis());

        List<Map<String, String>> steps = new ArrayList<>();
        for (ObservedSequence.Step step : sequence.steps()) {
            steps.add(Map.of("action", step.action(), "target", step.target()));
        }
        reflexData.put("steps", steps);

        List<Map<String, String>> atomSteps = new ArrayList<>();
        for (ObservedSequence.Step step : sequence.steps()) {
            atomSteps.add(Map.of("action", step.action(), "target", step.target()));
        }
        reflexData.put(KEY_ATOMS, buildAtomEntries(atomSteps));
        reflexData.put("currentAtomIndex", 0);

        reflexData.put("trigger", Map.of(
                "type", "subtask",
                "target", category
        ));

        Path path = reflexPath(skillId);
        JsonUtil.writeToFileSafeAtomic(path, reflexData);

        LOGGER.info("[ConditionedReflex] 条件反射已固化: {} (category={}, 观察{}次, proficiency={})",
                skillId, category, sequence.occurrences(), String.format("%.2f", sequence.proficiency()));
    }

    public void incrementProficiency(String skillId) {
        Path path = reflexPath(skillId);
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(path);
        if (data == null) return;

        double proficiency = ((Number) data.getOrDefault(KEY_PROFICIENCY, 0.3)).doubleValue();
        proficiency = Math.min(1.0, proficiency + 0.05);
        data.put(KEY_PROFICIENCY, proficiency);

        int observed = ((Number) data.getOrDefault("occurrences", 0)).intValue();
        data.put("occurrences", observed + 1);

        JsonUtil.writeToFileSafeAtomic(path, data);

        LOGGER.info("[ConditionedReflex] 熟练度提升: {} -> proficiency={} (观察{}次)",
                skillId, String.format("%.2f", proficiency), observed + 1);

        if (bayesianModule != null && bayesianModule.isConverged(skillId) && botInstance != null) {
            String name = (String) data.getOrDefault("displayName", skillId);
            botInstance.sendMessage("我现在" + name + "已经很熟练了，需要我承包吗？");
        }
    }

    public String buildSkillId(ObservedSequence sequence) {
        String action = sequence.steps().get(0).action();
        String target = sequence.target();
        String category = CategoryMapper.getCategory(action, target);
        return "reflex_" + category;
    }

    public void reinforce(String skillId, double delta) {
        Path path = reflexPath(skillId);
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(path);
        if (data == null) return;
        reinforce(data, delta);
        JsonUtil.writeToFileSafeAtomic(path, data);
    }

    private void reinforce(Map<String, Object> data, double delta) {
        double stw = ((Number) data.getOrDefault(KEY_SHORT_TERM_WEIGHT, DEFAULT_WEIGHT)).doubleValue();
        double ltb = ((Number) data.getOrDefault(KEY_LONG_TERM_BASELINE, DEFAULT_WEIGHT)).doubleValue();

        stw = stw * (1 - botParams.getAlpha()) + botParams.getAlpha() * delta;
        ltb = ltb * (1 - botParams.getBeta()) + botParams.getBeta() * stw;

        data.put(KEY_SHORT_TERM_WEIGHT, Math.max(WEIGHT_MIN, Math.min(WEIGHT_MAX, stw)));
        data.put(KEY_LONG_TERM_BASELINE, Math.max(WEIGHT_MIN, Math.min(WEIGHT_MAX, ltb)));
    }

    public double effectiveWeight(Map<String, Object> data) {
        double stw = ((Number) data.getOrDefault(KEY_SHORT_TERM_WEIGHT, DEFAULT_WEIGHT)).doubleValue();
        double ltb = ((Number) data.getOrDefault(KEY_LONG_TERM_BASELINE, DEFAULT_WEIGHT)).doubleValue();
        return Math.max(0, stw * EW_STW_RATIO + ltb * EW_LTB_RATIO);
    }

    static double computeDecayFactor(Map<String, Object> data) {
        long lastAccessed = ((Number) data.getOrDefault(KEY_LAST_ACCESSED, 0L)).longValue();
        if (lastAccessed == 0L) return 1.0;
        double hours = (System.currentTimeMillis() - lastAccessed) / 3600000.0;
        return Math.max(DECAY_MIN, 1.0 - hours * DECAY_RATE_PER_HOUR);
    }

    public void updateLastAccessed(String skillId) {
        Path path = reflexPath(skillId);
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(path);
        if (data == null) return;
        data.put(KEY_LAST_ACCESSED, System.currentTimeMillis());
        JsonUtil.writeToFileSafeAtomic(path, data);
    }

    public String getLastExecutedReflexId() {
        return lastExecutedReflexId;
    }

    public String getReflexContext(String skillId) {
        if (skillId == null) return null;
        Path path = reflexPath(skillId);
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(path);
        if (data == null) return null;
        String category = (String) data.getOrDefault("category", "");
        String target = (String) data.getOrDefault("target", "");
        String displayName = (String) data.getOrDefault("displayName", skillId);
        double stw = ((Number) data.getOrDefault(KEY_SHORT_TERM_WEIGHT, DEFAULT_WEIGHT)).doubleValue();
        double ltb = ((Number) data.getOrDefault(KEY_LONG_TERM_BASELINE, DEFAULT_WEIGHT)).doubleValue();
        return String.format("reflexId=%s, category=%s, target=%s, displayName=%s, stw=%.2f, ltb=%.2f",
                skillId, category, target, displayName, stw, ltb);
    }

    public String getLastReflexSummary() {
        if (lastExecutedReflexId == null) return "最近没有执行特定反射";
        String ctx = getReflexContext(lastExecutedReflexId);
        return "Bot最近执行的反射: " + (ctx != null ? ctx : lastExecutedReflexId);
    }

    public double getHighestProficiency() {
        double max = 0;
        try {
            var dir = conditionedDir();
            if (!java.nio.file.Files.exists(dir)) return 0;
            try (var stream = java.nio.file.Files.list(dir)) {
                for (var path : stream.toList()) {
                    if (!path.toString().endsWith(".json")) continue;
                    Map<String, Object> data = JsonUtil.readMapFromFileSafe(path);
                    if (data != null) {
                        double ew = effectiveWeight(data);
                        if (ew > max) max = ew;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[ConditionedReflex] getHighestProficiency: {}", e.getMessage());
        }
        return max;
    }

    public Path getStoragePath() {
        return conditionedDir();
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    public void resetConsecutiveFailures() {
        consecutiveFailures.set(0);
    }

    public DeviationCounter getDeviationCounter() {
        return deviationCounter;
    }

    public Skill getSkill(String skillId) {
        return skillManager.getSkill(skillId);
    }

    public int getRecentSuccessCount() {
        return recentSuccessCount;
    }

    public void moveToArchived(String skillId, Map<String, Object> data) {
        Path src = reflexPath(skillId);
        Path dst = ReflexIO.archivedDir(botId).resolve(skillId + FileUtil.JSON_EXT);
        JsonUtil.writeToFileSafeAtomic(dst, data);
        try {
            java.nio.file.Files.deleteIfExists(src);
        } catch (java.io.IOException e) {
            LOGGER.warn("[ConditionedReflex] 删除原反射文件失败: {}", skillId);
        }
    }

    public boolean tryReactivate(String skillId) {
        return ReflexIO.tryReactivate(skillId, botId);
    }

    public String matchReflexIdByHint(String hint) {
        if (hint == null || hint.isEmpty()) return null;

        if (skillManager.getSkill(hint) != null) {
            LOGGER.debug("[ConditionedReflex] 精确匹配: {}", hint);
            return hint;
        }

        String lower = hint.toLowerCase();

        if (lastExecutedReflexId != null && lastExecutedReflexId.toLowerCase().contains(lower)) {
            LOGGER.debug("[ConditionedReflex] 最近执行匹配: {} <- {}", lastExecutedReflexId, hint);
            return lastExecutedReflexId;
        }

        if (lastExecutedReflexId != null) {
            String lastCategory = getReflexCategory(lastExecutedReflexId);
            if (lastCategory != null) {
                for (String id : skillManager.getSkills().keySet()) {
                    if (id.equals(lastExecutedReflexId)) continue;
                    String cat = getReflexCategory(id);
                    if (lastCategory.equals(cat) && id.toLowerCase().contains(lower)) {
                        LOGGER.debug("[ConditionedReflex] 同category匹配: {} <- {} (cat={})", id, hint, lastCategory);
                        return id;
                    }
                }
            }
        }

        for (String id : skillManager.getSkills().keySet()) {
            if (id.toLowerCase().contains(lower)) {
                LOGGER.warn("[ConditionedReflex] 兜底子串匹配(LLM输出可能不够精确): {} <- {}", id, hint);
                return id;
            }
        }

        LOGGER.warn("[ConditionedReflex] 无匹配反射: hint={}", hint);
        return null;
    }

    private String getReflexCategory(String skillId) {
        return ReflexIO.getCategory(skillId, botId);
    }

    public String getReflexCategoryPublic(String skillId) {
        return getReflexCategory(skillId);
    }

    public void observePeerSuccess(String category) {
        if (category == null) return;
        int count = 0;
        for (String id : skillManager.getSkills().keySet()) {
            String cat = getReflexCategory(id);
            if (category.equals(cat)) {
                Path path = reflexPath(id);
                Map<String, Object> data = JsonUtil.readMapFromFileSafe(path);
                if (data == null) continue;
                double stw = ((Number) data.getOrDefault(KEY_SHORT_TERM_WEIGHT, DEFAULT_WEIGHT)).doubleValue();
                stw = Math.min(WEIGHT_MAX, stw + 0.03);
                data.put(KEY_SHORT_TERM_WEIGHT, stw);
                JsonUtil.writeToFileSafeAtomic(path, data);
                count++;
            }
        }
        if (count > 0) {
            LOGGER.debug("[ConditionedReflex] 社交观察学习: category={} 提升 {} 个反射", category, count);
        }
    }

    private void reinforceMemoryGraph(String skillId, boolean success) {
        if (botInstance == null) return;
        var ctx = botInstance.getBotContext();
        if (ctx == null) return;
        MemoryManager mem = ctx.memoryManager();
        if (mem == null) return;
        MemoryGraph mg = mem.getMemoryGraph();
        if (mg == null) return;
        List<String> nodeIds = mg.findNodeIdsByReflex(skillId);
        if (nodeIds.isEmpty()) return;
        mg.reinforcePath(nodeIds, success ? 0.05 : -0.03);
    }

    public void reinforceWithSpill(String skillId, double delta) {
        reinforce(skillId, delta);
        LOGGER.info("[ConditionedReflex] 反射强化: {} delta={}", skillId, delta);

        String category = getReflexCategory(skillId);
        if (category == null) return;

        double spill = delta * SPILL_RATIO;
        int count = 0;
        for (String id : skillManager.getSkills().keySet()) {
            if (id.equals(skillId)) continue;
            String cat = getReflexCategory(id);
            if (category.equals(cat)) {
                reinforce(id, spill);
                count++;
            }
        }
        if (count > 0) {
            LOGGER.debug("[ConditionedReflex] 溢出强化: {}个同category反射 x{:.3f} (cat={})", count, spill, category);
        }
    }

    private void analyzeAndGenerate() {
        for (Map.Entry<String, List<Double>> entry : actionHistory.entrySet()) {
            String skillId = entry.getKey();
            List<Double> scores = entry.getValue();

            if (scores.size() < config.reflexMinSuccesses) continue;

            long successes = scores.stream().filter(s -> s >= config.reflexThreshold).count();
            double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            if (successes >= config.reflexMinSuccesses && avg >= config.reflexThreshold) {
                ConditionedSkill newSkill = new ConditionedSkill("reflex_" + skillId, "条件反射_" + skillId);
                skillManager.registerConditionedSkill(newSkill);
                LOGGER.info("[ConditionedReflex] 生成条件反射: {}", skillId);
            }
        }
        actionHistory.clear();
    }

    private ActionResult dispatchAction(String action, String target, Map<String, Object> atom, ServerPlayerEntity bot) {
        if (actionAdapter == null || bot == null) return null;

        var world = bot.getServerWorld();

        return switch (action) {
            case "moveTo" -> {
                BlockPos targetPos = findBlockPosByName(world, bot, target);
                yield actionAdapter.moveTo(bot, targetPos != null ? targetPos : bot.getBlockPos());
            }
            case "lookAt" -> actionAdapter.lookAt(bot, bot.getX(), bot.getY() + 1.6, bot.getZ());
            case "dig" -> {
                BlockPos digTarget = findBlockPosByName(world, bot, target);
                yield actionAdapter.dig(bot, digTarget);
            }
            case "attack" -> actionAdapter.attack(bot, target);
            case "placeBlock" -> {
                BlockPos placePos = bot.getBlockPos().offset(net.minecraft.util.math.Direction.UP);
                yield actionAdapter.placeBlock(bot, placePos, "up");
            }
            case "useItem" -> actionAdapter.useItem(bot);
            case "equipItem" -> actionAdapter.equipItem(bot, target);
            case "openBlock" -> {
                BlockPos openPos = findBlockPosByName(world, bot, target);
                yield actionAdapter.openBlock(bot, openPos != null ? openPos : bot.getBlockPos());
            }
            case "closeWindow" -> actionAdapter.closeWindow(bot);
            case "clickSlot" -> {
                int slot = atom != null && atom.containsKey("slot")
                        ? ((Number) atom.getOrDefault("slot", 0)).intValue() : 0;
                int button = atom != null && atom.containsKey("button")
                        ? ((Number) atom.getOrDefault("button", 0)).intValue() : 0;
                yield actionAdapter.clickSlot(bot, slot, button);
            }
            case "craft" -> actionAdapter.craft(bot, target != null ? target : "");
            case "chat" -> actionAdapter.chat(bot, target != null ? target : "");
            case "jump" -> actionAdapter.jump(bot);
            default -> null;
        };
    }

    private BlockPos findBlockPosByName(net.minecraft.server.world.ServerWorld world,
                                         ServerPlayerEntity bot, String name) {
        if (world == null || bot == null) return null;
        BlockPos botPos = bot.getBlockPos();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    BlockPos pos = botPos.add(dx, dy, dz);
                    var state = world.getBlockState(pos);
                    if (state.isAir()) continue;
                    String blockId = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString();
                    if (name != null && blockId.toLowerCase().contains(name.toLowerCase())) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /** 将值钳制在 [0,1] 区间 */
    private static double clampScore(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    public static class ConditionedSkill extends Skill {
        public ConditionedSkill(String skillId, String name) {
            super(skillId, name, "conditioned");
        }

        @Override
        public boolean canExecute(net.minecraft.server.world.ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
            return bot != null && world != null;
        }

        @Override
        public SkillResult execute(net.minecraft.server.world.ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
            return SkillResult.success("条件反射已触发: " + getName());
        }
    }
}
