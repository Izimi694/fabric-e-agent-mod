package com.izimi.aiplayermod.character;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.config.ModConfig;

import java.util.HashMap;
import java.util.Map;

public class PersonalityEvolution {
    private final ModConfig config;

    public PersonalityEvolution(ModConfig config) {
        this.config = config;
    }

    public void processInteraction(String target, String interactionType, String content) {
        double delta = calculateDelta(interactionType, content);
        double weightedDelta = applyWeight(delta, interactionType);

        var characterManager = AIPlayerMod.getCharacterManager();
        if (characterManager != null) {
            characterManager.updatePreference(target, weightedDelta, interactionType);
        }
    }

    private double calculateDelta(String interactionType, String content) {
        return switch (interactionType) {
            case "behavior" -> calculateBehaviorDelta(content);
            case "command" -> calculateCommandDelta(content);
            case "chat" -> calculateChatDelta(content);
            default -> 0.0;
        };
    }

    private double calculateBehaviorDelta(String content) {
        if (content == null) return 0.05;

        String lower = content.toLowerCase();
        if (lower.contains("钻石") || lower.contains("diamond")) return 0.08;
        if (lower.contains("铁") || lower.contains("iron")) return 0.05;
        if (lower.contains("金") || lower.contains("gold")) return 0.04;
        if (lower.contains("煤") || lower.contains("coal")) return 0.02;
        if (lower.contains("红石") || lower.contains("redstone")) return 0.04;
        if (lower.contains("绿宝石") || lower.contains("emerald")) return 0.09;

        return 0.03;
    }

    private double calculateCommandDelta(String content) {
        if (content == null) return 0.0;

        return 0.1;
    }

    private double calculateChatDelta(String content) {
        if (content == null) return 0.0;

        String lower = content.toLowerCase();
        if (lower.contains("喜欢") || lower.contains("爱") || lower.contains("like") || lower.contains("love")) {
            return 0.07;
        }
        if (lower.contains("讨厌") || lower.contains("恨") || lower.contains("hate") || lower.contains("dislike")) {
            return -0.07;
        }
        if (lower.contains("想要") || lower.contains("需要") || lower.contains("want") || lower.contains("need")) {
            return 0.05;
        }

        return 0.02;
    }

    private double applyWeight(double delta, String interactionType) {
        return switch (interactionType) {
            case "behavior" -> delta * config.behaviorWeight;
            case "command" -> delta * config.commandWeight;
            case "chat" -> delta * config.chatWeight;
            default -> delta;
        };
    }
}
