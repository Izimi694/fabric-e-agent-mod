package com.izimi.eagent.brainstem.innate;

import com.google.gson.reflect.TypeToken;
import com.izimi.eagent.amygdala.BotParams;
import static com.izimi.eagent.amygdala.ReflexConstants.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;
import net.minecraft.server.network.ServerPlayerEntity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class InnateReflexRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private static final double DEFAULT_WEIGHT = 0.5;
    private static final double WEIGHT_MIN = -1.0;
    private static final double WEIGHT_MAX = 1.0;
    private static final double EW_STW_RATIO = 0.7;
    private static final double EW_LTB_RATIO = 0.3;
    private static final double SELF_REINFORCE_DELTA = 0.05;

    private final List<InnateReflex> reflexes = new ArrayList<>();
    private final MinecraftReflexEvaluator evaluator;
    private final Map<String, double[]> reflexWeights = new LinkedHashMap<>();

    public InnateReflexRegistry(MinecraftReflexEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public void register(InnateReflex reflex) {
        reflexes.add(reflex);
        reflexWeights.putIfAbsent(reflex.id(), new double[]{DEFAULT_WEIGHT, DEFAULT_WEIGHT});
    }

    public void loadFromJson(Path path) {
        List<InnateReflex> loaded = JsonUtil.readFromFileSafe(path,
                new TypeToken<List<InnateReflex>>(){}.getType());
        if (loaded != null && !loaded.isEmpty()) {
            reflexes.clear();
            reflexes.addAll(loaded);
            for (InnateReflex r : loaded) {
                reflexWeights.putIfAbsent(r.id(), new double[]{DEFAULT_WEIGHT, DEFAULT_WEIGHT});
            }
        }
        loadWeights();
    }

    public void saveToJson(Path path) {
        JsonUtil.writeToFileSafeAtomic(path, reflexes);
    }

    public void loadDefaults() {
        reflexes.clear();
        reflexWeights.clear();
        register(InnateReflex.create("critical", 0, true,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.HEALTH_BELOW, 2.0, 0),
                        new ReflexTrigger(ReflexTrigger.TriggerType.MONSTER_NEARBY, 0.0, 3)),
                new ReflexAction("flee", Map.of("speed", 0.3))));
        register(InnateReflex.create("flee", 0, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.HEALTH_BELOW, 10.0, 0),
                        new ReflexTrigger(ReflexTrigger.TriggerType.MONSTER_NEARBY, 0.0, 10)),
                new ReflexAction("flee", Map.of("speed", 0.3))));
        register(InnateReflex.create("eat", 0, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.HUNGER_BELOW, 6.0, 0)),
                new ReflexAction("eat", Map.of())));
        register(InnateReflex.create("retreat", 1, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.HEALTH_BELOW, 6.0, 0),
                        new ReflexTrigger(ReflexTrigger.TriggerType.MONSTER_NEARBY, 0.0, 20)),
                new ReflexAction("retreat", Map.of("speed", 0.25))));
        register(InnateReflex.create("avoid_lava", 1, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.LAVA_NEARBY, 0.0, 3)),
                new ReflexAction("avoidLava", Map.of("speed", 0.2))));
        register(InnateReflex.create("seek_shelter", 1, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.TIME_OF_DAY, 0.0, 5)),
                new ReflexAction("seekShelter", Map.of("speed", 0.1))));
        register(InnateReflex.create("collect_item", 1, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.ITEM_NEARBY, 0.0, 5)),
                new ReflexAction("collectItem", Map.of("speed", 0.15))));
        register(InnateReflex.create("sneak", 1, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.MONSTER_NEARBY, 0.0, 12)),
                new ReflexAction("sneak", Map.of())));
        register(InnateReflex.create("vocal_response", 5, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.CHAT_PRESENCE, 0.0, 30)),
                new ReflexAction("invokeLLM", Map.of())));
        register(InnateReflex.create("equip_armor", 1, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.ARMOR_SLOT_EMPTY, 0.0, 3)),
                new ReflexAction("equipArmor", Map.of())));
        register(InnateReflex.create("equip_totem", 1, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.OFFHAND_EMPTY, 0.0, 0),
                        new ReflexTrigger(ReflexTrigger.TriggerType.HAS_TOTEM, 0.0, 0)),
                new ReflexAction("equipTotem", Map.of())));
        register(InnateReflex.create("ranged_attack", 1, false,
                List.of(new ReflexTrigger(ReflexTrigger.TriggerType.BOW_IN_HOTBAR, 0.0, 0),
                        new ReflexTrigger(ReflexTrigger.TriggerType.ARROW_IN_INVENTORY, 0.0, 0),
                        new ReflexTrigger(ReflexTrigger.TriggerType.MONSTER_NEARBY, 0.0, 15)),
                new ReflexAction("rangedAttack", Map.of())));
        saveWeights();
    }

    public List<InnateReflex> match(ServerPlayerEntity bot) {
        if (bot == null) return List.of();
        return reflexes.stream()
                .filter(InnateReflex::enabled)
                .filter(r -> evaluator.matchesAll(r.triggers(), bot))
                .sorted(Comparator.comparingInt(InnateReflex::priority))
                .collect(Collectors.toList());
    }

    public List<InnateReflex> matchWeighted(ServerPlayerEntity bot) {
        return match(bot).stream()
                .filter(r -> r.priority() == 0 || effectiveWeight(r.id()) >= 0.3)
                .collect(Collectors.toList());
    }

    public InnateReflex highest(ServerPlayerEntity bot) {
        return match(bot).stream().findFirst().orElse(null);
    }

    public InnateReflex highest(ServerPlayerEntity bot, int maxPriority) {
        return match(bot).stream()
                .filter(r -> r.priority() <= maxPriority)
                .findFirst().orElse(null);
    }

    public int size() {
        return reflexes.size();
    }

    public List<InnateReflex> all() {
        return new ArrayList<>(reflexes);
    }

    public double effectiveWeight(String reflexId) {
        double[] w = reflexWeights.getOrDefault(reflexId, new double[]{DEFAULT_WEIGHT, DEFAULT_WEIGHT});
        return Math.max(0, w[0] * EW_STW_RATIO + w[1] * EW_LTB_RATIO);
    }

    public void reinforce(String reflexId, double delta) {
        double[] w = reflexWeights.computeIfAbsent(reflexId, k -> new double[]{DEFAULT_WEIGHT, DEFAULT_WEIGHT});
        BotParams bp = BotParams.load();
        w[0] = w[0] * (1 - bp.getAlpha()) + bp.getAlpha() * delta;
        w[1] = w[1] * (1 - bp.getBeta()) + bp.getBeta() * w[0];
        w[0] = Math.max(WEIGHT_MIN, Math.min(WEIGHT_MAX, w[0]));
        w[1] = Math.max(WEIGHT_MIN, Math.min(WEIGHT_MAX, w[1]));
        saveWeights();
    }

    public void reinforceOnDispatch(String reflexId, boolean success) {
        double delta = success ? SELF_REINFORCE_DELTA : -SELF_REINFORCE_DELTA * 0.5;
        reinforce(reflexId, delta);
        LOGGER.debug("[InnateReflex] 强化: {} delta={:.3f} stw={:.2f} ltb={:.2f}",
                reflexId, delta, getStw(reflexId), getLtb(reflexId));
    }

    public double getStw(String reflexId) {
        double[] w = reflexWeights.getOrDefault(reflexId, new double[]{DEFAULT_WEIGHT, DEFAULT_WEIGHT});
        return w[0];
    }

    public double getLtb(String reflexId) {
        double[] w = reflexWeights.getOrDefault(reflexId, new double[]{DEFAULT_WEIGHT, DEFAULT_WEIGHT});
        return w[1];
    }

    public Map<String, double[]> getWeights() {
        return new LinkedHashMap<>(reflexWeights);
    }

    private void loadWeights() {
        try {
            Path path = FileUtil.getInnateReflexWeightsPath();
            Map<String, Map<String, Double>> saved = JsonUtil.readFromFileSafe(path,
                    new TypeToken<Map<String, Map<String, Double>>>(){}.getType());
            if (saved != null) {
                for (var entry : saved.entrySet()) {
                    Map<String, Double> vals = entry.getValue();
                    double stw = vals.getOrDefault(KEY_SHORT_TERM_WEIGHT, DEFAULT_WEIGHT);
                    double ltb = vals.getOrDefault(KEY_LONG_TERM_BASELINE, DEFAULT_WEIGHT);
                    reflexWeights.put(entry.getKey(), new double[]{stw, ltb});
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[InnateReflexRegistry] 权重加载跳过 (测试环境): {}", e.getMessage());
        }
    }

    private void saveWeights() {
        try {
            Map<String, Map<String, Double>> data = new LinkedHashMap<>();
            for (var entry : reflexWeights.entrySet()) {
                Map<String, Double> vals = new LinkedHashMap<>();
                vals.put(KEY_SHORT_TERM_WEIGHT, entry.getValue()[0]);
                vals.put(KEY_LONG_TERM_BASELINE, entry.getValue()[1]);
                data.put(entry.getKey(), vals);
            }
            JsonUtil.writeToFileSafeAtomic(FileUtil.getInnateReflexWeightsPath(), data);
        } catch (Exception e) {
            LOGGER.debug("[InnateReflexRegistry] 权重保存跳过 (测试环境): {}", e.getMessage());
        }
    }
}
