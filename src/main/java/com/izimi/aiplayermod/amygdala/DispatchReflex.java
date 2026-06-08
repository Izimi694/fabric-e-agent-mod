package com.izimi.aiplayermod.amygdala;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.brainstem.scheduler.FlowLevel;
import com.izimi.aiplayermod.brainstem.scheduler.ProblemLabel;
import com.izimi.aiplayermod.util.FileUtil;
import com.izimi.aiplayermod.util.JsonUtil;

import java.nio.file.Path;
import java.util.*;

public class DispatchReflex {

    private static final double DEFAULT_WEIGHT = 0.5;
    private static final double WEIGHT_MIN = -1.0;
    private static final double WEIGHT_MAX = 1.0;
    private static final double EW_STW_RATIO = 0.7;
    private static final double EW_LTB_RATIO = 0.3;
    private static final double SUCCESS_DELTA = 0.05;
    private static final double FAILURE_DELTA = -0.05;
    private static final int MIN_SAMPLES_FOR_CONFIDENT = 5;

    private final BotParams botParams;
    private final UUID botId;
    private final Map<String, Map<String, DispatchWeight>> dispatchWeights = new LinkedHashMap<>();

    public DispatchReflex(BotParams botParams, UUID botId) {
        this.botParams = botParams;
        this.botId = botId;
        load();
    }

    public DispatchAction match(ProblemLabel label, FlowLevel flow) {
        String key = dispatchKey(label, flow);
        var actions = dispatchWeights.get(key);
        if (actions == null || actions.isEmpty()) return null;

        return actions.entrySet().stream()
                .filter(e -> e.getValue().totalCalls() >= MIN_SAMPLES_FOR_CONFIDENT)
                .max((a, b) -> Double.compare(
                        a.getValue().effectiveWeight(),
                        b.getValue().effectiveWeight()))
                .filter(e -> e.getValue().effectiveWeight() > 0.3)
                .map(e -> toDispatchAction(e.getKey()))
                .orElse(null);
    }

    public void recordOutcome(ProblemLabel label, FlowLevel flow,
                              DispatchAction action, boolean success) {
        String key = dispatchKey(label, flow);
        String actionKey = dispatchActionKey(action);
        var actions = dispatchWeights.computeIfAbsent(key, k -> new LinkedHashMap<>());
        var weight = actions.computeIfAbsent(actionKey,
                k -> new DispatchWeight(DEFAULT_WEIGHT, DEFAULT_WEIGHT, 0));

        weight.stw = weight.stw * (1 - botParams.getAlpha()) + botParams.getAlpha() * (success ? SUCCESS_DELTA : FAILURE_DELTA);
        weight.ltb = weight.ltb * (1 - botParams.getBeta()) + botParams.getBeta() * weight.stw;
        weight.stw = Math.max(WEIGHT_MIN, Math.min(WEIGHT_MAX, weight.stw));
        weight.ltb = Math.max(WEIGHT_MIN, Math.min(WEIGHT_MAX, weight.ltb));
        weight.totalCalls++;

        save();
    }

    public List<DispatchEntry> getHistory() {
        List<DispatchEntry> result = new ArrayList<>();
        for (var entry : dispatchWeights.entrySet()) {
            for (var action : entry.getValue().entrySet()) {
                var dw = action.getValue();
                var da = toDispatchAction(action.getKey());
                result.add(new DispatchEntry(entry.getKey(), da,
                        dw.stw, dw.ltb, dw.totalCalls, dw.effectiveWeight()));
            }
        }
        return result;
    }

    private void save() {
        try {
            Map<String, Map<String, Map<String, Double>>> data = new LinkedHashMap<>();
            for (var entry : dispatchWeights.entrySet()) {
                Map<String, Map<String, Double>> actions = new LinkedHashMap<>();
                for (var action : entry.getValue().entrySet()) {
                    Map<String, Double> vals = new LinkedHashMap<>();
                    vals.put("stw", action.getValue().stw);
                    vals.put("ltb", action.getValue().ltb);
                    vals.put("calls", (double) action.getValue().totalCalls);
                    actions.put(action.getKey(), vals);
                }
                data.put(entry.getKey(), actions);
            }
            JsonUtil.writeToFileSafeAtomic(getPath(), data);
        } catch (Exception e) {
            AIPlayerMod.LOGGER.debug("[DispatchReflex] save skipped: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        try {
            Map<String, Map<String, Map<String, Double>>> data =
                    JsonUtil.readFromFileSafe(getPath(), Map.class);
            if (data != null) {
                for (var entry : data.entrySet()) {
                    Map<String, DispatchWeight> actions = new LinkedHashMap<>();
                    for (var action : entry.getValue().entrySet()) {
                        var vals = action.getValue();
                        double stw = vals.getOrDefault("stw", DEFAULT_WEIGHT);
                        double ltb = vals.getOrDefault("ltb", DEFAULT_WEIGHT);
                        int calls = vals.containsKey("calls") ? vals.get("calls").intValue() : 0;
                        actions.put(action.getKey(), new DispatchWeight(stw, ltb, calls));
                    }
                    dispatchWeights.put(entry.getKey(), actions);
                }
            }
        } catch (Exception e) {
            AIPlayerMod.LOGGER.debug("[DispatchReflex] load skipped: {}", e.getMessage());
        }
    }

    private Path getPath() {
        return FileUtil.getBotDir(botId).resolve("dispatch_weights.json");
    }

    private static String dispatchKey(ProblemLabel label, FlowLevel flow) {
        return label.name() + "_" + flow.name();
    }

    private static String dispatchActionKey(DispatchAction action) {
        return action.layer() + (action.reason() != null ? ":" + action.reason() : "");
    }

    private static DispatchAction toDispatchAction(String key) {
        int idx = key.indexOf(':');
        if (idx > 0) {
            return new DispatchAction(key.substring(0, idx), key.substring(idx + 1));
        }
        return new DispatchAction(key, "");
    }

    public static class DispatchWeight {
        double stw;
        double ltb;
        int totalCalls;

        DispatchWeight(double stw, double ltb, int totalCalls) {
            this.stw = stw;
            this.ltb = ltb;
            this.totalCalls = totalCalls;
        }

        double effectiveWeight() {
            return Math.max(0, stw * EW_STW_RATIO + ltb * EW_LTB_RATIO);
        }

        int totalCalls() { return totalCalls; }
    }

    public record DispatchAction(String layer, String reason) {
        public DispatchAction { if (reason == null) reason = ""; }
    }

    public record DispatchEntry(String contextKey, DispatchAction action,
                                double stw, double ltb, int calls, double effectiveWeight) {}
}
