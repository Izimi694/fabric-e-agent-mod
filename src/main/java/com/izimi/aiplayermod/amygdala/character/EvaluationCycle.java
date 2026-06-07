package com.izimi.aiplayermod.amygdala.character;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.amygdala.ConditionedReflex;
import com.izimi.aiplayermod.cortex.api.AIClient;
import com.izimi.aiplayermod.cortex.api.AIMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class EvaluationCycle {

    private static final Pattern EVAL_PATTERN = Pattern.compile(
            "(?:你[很太真假好笨]|你真|你有点|你是个|你是)[^，。！？\n]{1,15}"
    );

    private static final int BATCH_THRESHOLD = 10;
    private static final long BATCH_INTERVAL_MS = 30L * 60 * 1000;

    private final ConditionedReflex conditionedReflex;
    private final List<String> pendingEvaluations = new ArrayList<>();
    private long lastBatchTime = 0;

    public EvaluationCycle(ConditionedReflex conditionedReflex) {
        this.conditionedReflex = conditionedReflex;
    }

    public String checkMessage(String message) {
        if (message == null || message.isEmpty()) return null;
        if (!EVAL_PATTERN.matcher(message).find()) return null;

        pendingEvaluations.add(message);
        return "evaluation";
    }

    public void onTick() {
        long now = System.currentTimeMillis();
        if (pendingEvaluations.size() >= BATCH_THRESHOLD ||
                (!pendingEvaluations.isEmpty() && now - lastBatchTime >= BATCH_INTERVAL_MS)) {
            batchReinforce();
        }
    }

    private void batchReinforce() {
        var aiClient = AIPlayerMod.getAIClient();
        if (aiClient == null || !aiClient.isConfigured()) {
            pendingEvaluations.clear();
            return;
        }

        List<String> batch = new ArrayList<>(pendingEvaluations);
        pendingEvaluations.clear();
        lastBatchTime = System.currentTimeMillis();

        String joined = String.join("\n", batch);
        String lastReflex = conditionedReflex.getLastExecutedReflexId();
        String reflexContext = lastReflex != null
                ? "Bot最近执行的反射: " + lastReflex
                : "Bot最近没有执行特定反射";

        AIMessage systemMsg = new AIMessage("system", """
                你是一个游戏角色的行为强化系统。以下是玩家对bot最近的评价列表。
                判断每条评价是鼓励还是批评哪种行为。
                输出JSON数组格式: [{"reflexHint":"dig","delta":0.15}, ...]
                delta范围[-0.2, 0.2]。正面评价用正delta，负面用负delta。
                如果无明确指向的行为，输出{"reflexHint":"","delta":0}。
                只输出JSON数组，不要其他内容。""");
        AIMessage userMsg = new AIMessage("user",
                "评价:\n" + joined + "\n\n" + reflexContext);

        try {
            aiClient.sendMessage(List.of(systemMsg, userMsg)).thenAccept(response -> {
                if (response == null || response.getMessage() == null || response.getMessage().isEmpty()) return;

                try {
                    var gson = new com.google.gson.Gson();
                    var array = gson.fromJson(response.getMessage(), com.google.gson.JsonArray.class);
                    if (array == null) return;

                    for (int i = 0; i < array.size(); i++) {
                        var obj = array.get(i).getAsJsonObject();
                        String hint = obj.has("reflexHint") && !obj.get("reflexHint").isJsonNull()
                                ? obj.get("reflexHint").getAsString() : "";
                        double delta = obj.has("delta") ? obj.get("delta").getAsDouble() : 0;
                        if (hint.isEmpty() || delta == 0) continue;

                        String matchedId = conditionedReflex.matchReflexIdByHint(hint);
                        if (matchedId != null) {
                            conditionedReflex.reinforce(matchedId, delta);
                            AIPlayerMod.LOGGER.info("[EvaluationCycle] 反射强化: {} delta={}", matchedId, delta);
                        }
                    }
                } catch (Exception e) {
                    AIPlayerMod.LOGGER.warn("[EvaluationCycle] JSON解析失败: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            AIPlayerMod.LOGGER.warn("[EvaluationCycle] 批量强化失败: {}", e.getMessage());
        }
    }
}
