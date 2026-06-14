package com.izimi.eagent.cortex.api;

import com.google.gson.annotations.SerializedName;

public class AIResponse {
    public String action;
    public String skill;
    public AIResponseParams params;
    public String message;
    @SerializedName("memory_save")
    public String memorySave;
    @SerializedName("personality_delta")
    public java.util.Map<String, Double> personalityDelta;

    public TokenUsage usage;

    public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
        public double estimatedCostYuan(String model) {
            double promptPrice = 1.0;
            double completionPrice = 2.0;
            if (model != null && model.contains("flash")) {
                promptPrice = 0.5;
                completionPrice = 1.0;
            }
            return (promptTokens * promptPrice + completionTokens * completionPrice) / 1_000_000.0;
        }
    }

    public static class AIResponseParams {
        public String target;
        public int amount;
        public int[] position;
        public String direction;
        public String item;
    }

    public boolean isAction() {
        return "execute_task".equals(action) || "dig".equals(action) || "move".equals(action)
                || "attack".equals(action) || "craft".equals(action) || "explore".equals(action);
    }

    public boolean isChat() {
        return "chat".equals(action);
    }

    public boolean isEmpty() {
        return action == null;
    }

    public String getAction() { return action != null ? action : "chat"; }
    public String getSkill() { return skill != null ? skill : "move"; }
    public String getMessage() { return message != null ? message : ""; }
    public String getMemoryNote() { return memorySave != null ? memorySave : ""; }

    public static AIResponse empty() {
        AIResponse r = new AIResponse();
        r.action = null;
        r.message = "";
        return r;
    }
}
