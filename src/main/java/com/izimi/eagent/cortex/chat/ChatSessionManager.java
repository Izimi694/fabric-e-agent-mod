package com.izimi.eagent.cortex.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

import com.izimi.eagent.bayesian.BayesianModule;

public class ChatSessionManager {

    public record ChatSlot(String role, String intent, List<String> entities, String rawPreview, long timestamp) {
        public ChatSlot(String role, String intent, List<String> entities, String rawPreview) {
            this(role, intent, entities, rawPreview, System.currentTimeMillis());
        }
    }

    private static final int MAX_WINDOW_SIZE = 6;
    private final Deque<ChatSlot> window = new ArrayDeque<>(MAX_WINDOW_SIZE);
    private final BayesianModule bayesianModule;
    private String currentGoal = "";

    public ChatSessionManager(BayesianModule bayesianModule, UUID botId) {
        this.bayesianModule = bayesianModule;
    }

    public void addMessage(ChatSlot slot) {
        window.addLast(slot);
        if (window.size() > MAX_WINDOW_SIZE) {
            window.removeFirst();
        }
    }

    public void setCurrentGoal(String goal) {
        this.currentGoal = goal != null ? goal : "";
    }

    public String buildPrompt() {
        String direction = bayesianModule != null
                ? bayesianModule.getCurrentDirection()
                : "暂无经验数据";

        StringBuilder sb = new StringBuilder();
        sb.append("[方向] ").append(direction).append("\n");
        sb.append("[当前目标] ").append(currentGoal).append("\n");

        if (!window.isEmpty()) {
            sb.append("[最近对话]\n");
            for (ChatSlot slot : window) {
                sb.append(slot.role()).append(": ").append(slot.rawPreview()).append("\n");
            }
        }

        sb.append("请基于以上信息填空：");
        return sb.toString();
    }

    public void refresh() {
        window.clear();
    }

    public int getWindowSize() {
        return window.size();
    }

    public Deque<ChatSlot> getWindow() {
        return new ArrayDeque<>(window);
    }
}
