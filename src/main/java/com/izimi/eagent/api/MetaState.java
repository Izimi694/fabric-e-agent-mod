package com.izimi.eagent.api;

import com.google.gson.JsonObject;
import com.izimi.eagent.brainstem.scheduler.ProblemLabel;
import com.izimi.eagent.cortex.api.TemplateManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MetaState {

    private int p3Cooldown = 0;
    private int tickSinceLastLLM = 0;
    private boolean recentLLMFailure = false;

    // ── 模板填空结果暂存 (异步 LLM) ──
    private CompletableFuture<JsonObject> pendingTemplateResult = null;
    private TemplateManager.TemplateType pendingTemplateType = null;
    private String pendingTemplateMessage = null;

    // ── CHAT_RESPONSE 独立预算 ──
    private static final int CHAT_BUDGET_MAX = 50;
    private int chatBudgetRemaining = CHAT_BUDGET_MAX;

    private String pendingChatMessage;
    private String lastPlayerMessage = "";
    private long lastPlayerMessageTime = 0;

    private String currentBiomeId = "";
    private int lastTickEntityCount = 0;
    private int thisTickEntityCount = 0;
    private int lastActionSuccessCount = 0;

    private ProblemLabel currentProblemLabel = ProblemLabel.TRIVIAL;
    private int ticksInCurrentLabel = 0;

    private final Map<String, Integer> novelEntityTicks = new HashMap<>();
    private String lastBlockFingerprint = "";

    private static final int NOVEL_ENTITY_WINDOW = 6000;

    // ── 模板结果暂存 ──
    public void setPendingTemplateResult(CompletableFuture<JsonObject> future, TemplateManager.TemplateType type, String originalMsg) {
        this.pendingTemplateResult = future;
        this.pendingTemplateType = type;
        this.pendingTemplateMessage = originalMsg;
    }

    public boolean hasPendingTemplateResult() { return pendingTemplateResult != null; }

    public TemplateManager.TemplateType getPendingTemplateType() { return pendingTemplateType; }

    public String getPendingTemplateMessage() { return pendingTemplateMessage; }

    public CompletableFuture<JsonObject> consumePendingTemplateResult() {
        CompletableFuture<JsonObject> f = pendingTemplateResult;
        pendingTemplateResult = null;
        pendingTemplateType = null;
        pendingTemplateMessage = null;
        return f;
    }

    // ── 聊天预算 ──
    public int getChatBudgetRemaining() { return chatBudgetRemaining; }

    public void consumeChatBudget() { if (chatBudgetRemaining > 0) chatBudgetRemaining--; }

    public void rechargeChatBudget() { chatBudgetRemaining = CHAT_BUDGET_MAX; }

    public boolean isChatBudgetExhausted() { return chatBudgetRemaining <= 0; }

    public int getP3Cooldown() { return p3Cooldown; }
    public void setP3Cooldown(int v) { this.p3Cooldown = v; }
    public void tickP3Cooldown() { if (p3Cooldown > 0) p3Cooldown--; }

    public int getTickSinceLastLLM() { return tickSinceLastLLM; }
    public void incrementTickSinceLastLLM() { tickSinceLastLLM++; }
    public void resetTickSinceLastLLM() { tickSinceLastLLM = 0; }

    public boolean hasRecentLLMFailure() { return recentLLMFailure; }
    public void setRecentLLMFailure(boolean v) { this.recentLLMFailure = v; }

    public String getPendingChatMessage() { return pendingChatMessage; }
    public void setPendingChat(String msg) { this.pendingChatMessage = msg; }
    public String consumePendingChat() {
        String msg = pendingChatMessage;
        pendingChatMessage = null;
        return msg;
    }
    public boolean hasPendingChat() { return pendingChatMessage != null; }

    public String getLastPlayerMessage() { return lastPlayerMessage; }
    public void setLastPlayerMessage(String msg) {
        this.lastPlayerMessage = msg;
        if (msg != null && !msg.isEmpty()) {
            lastPlayerMessageTime = System.currentTimeMillis();
        }
    }

    public String getCurrentBiomeId() { return currentBiomeId; }
    public void setCurrentBiomeId(String id) { this.currentBiomeId = id; }

    public int getPlayerInactiveMinutes() {
        if (lastPlayerMessageTime == 0) return 60;
        return (int) ((System.currentTimeMillis() - lastPlayerMessageTime) / 60_000);
    }

    public int getLastTickEntityCount() { return lastTickEntityCount; }
    public void setLastTickEntityCount(int c) { this.lastTickEntityCount = c; }
    public int getThisTickEntityCount() { return thisTickEntityCount; }
    public void setThisTickEntityCount(int c) { thisTickEntityCount = c; }
    public void cycleEntityCount() {
        lastTickEntityCount = thisTickEntityCount;
        thisTickEntityCount = 0;
    }

    public int getLastActionSuccessCount() { return lastActionSuccessCount; }
    public void setLastActionSuccessCount(int c) { this.lastActionSuccessCount = c; }

    public ProblemLabel getCurrentProblemLabel() { return currentProblemLabel; }
    public void setCurrentProblemLabel(ProblemLabel label) {
        if (label == currentProblemLabel) {
            ticksInCurrentLabel++;
        } else {
            currentProblemLabel = label;
            ticksInCurrentLabel = 0;
        }
    }
    public int getTicksInCurrentLabel() { return ticksInCurrentLabel; }

    public void recordNovelEntity(String entityType) {
        novelEntityTicks.put(entityType, NOVEL_ENTITY_WINDOW);
    }
    public void tickNovelEntities() {
        novelEntityTicks.entrySet().removeIf(e -> {
            e.setValue(e.getValue() - 1);
            return e.getValue() <= 0;
        });
    }
    public boolean hasNovelEntity() { return !novelEntityTicks.isEmpty(); }

    public String getBlockFingerprint() { return lastBlockFingerprint; }
    public void setBlockFingerprint(String fp) { this.lastBlockFingerprint = fp; }
    public boolean hasBlockChange(String newFingerprint) {
        if (lastBlockFingerprint.isEmpty()) return false;
        return !lastBlockFingerprint.equals(newFingerprint);
    }

    public boolean hasSuddenEnvironmentChange() {
        if (lastTickEntityCount == 0) return false;
        if (thisTickEntityCount == 0) return false;
        double change = Math.abs(thisTickEntityCount - lastTickEntityCount) / (double) lastTickEntityCount;
        return change > 0.3 && Math.abs(thisTickEntityCount - lastTickEntityCount) >= 3;
    }

    public boolean hasUrgentPlayerMessage() {
        if (lastPlayerMessage == null || lastPlayerMessage.isEmpty()) return false;
        String lower = lastPlayerMessage.toLowerCase();
        return lower.contains("小心") || lower.contains("停") || lower.contains("想清楚再做")
                || lower.contains("等等") || lower.contains("别");
    }
}
