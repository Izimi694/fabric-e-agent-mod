package com.izimi.aiplayermod.character;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.config.ModConfig;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

import java.util.*;

public class BehaviorObserver {
    private final CharacterManager characterManager;
    private final ModConfig config;

    private final Map<String, Integer> blockBreakCounts = new HashMap<>();
    private final Map<String, Integer> entityAttackCounts = new HashMap<>();
    private final List<String> chatKeywords = new ArrayList<>();
    private final Map<String, Double> pendingBehaviorUpdates = new HashMap<>();
    private final Map<String, Double> pendingChatUpdates = new HashMap<>();
    private int observationCount = 0;
    private static final int EVOLVE_THRESHOLD = 10;

    public BehaviorObserver(CharacterManager characterManager, ModConfig config) {
        this.characterManager = characterManager;
        this.config = config;
    }

    public void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayerEntity) {
                onBlockBreak(state);
            }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayerEntity && entity instanceof LivingEntity) {
                onEntityAttack((LivingEntity) entity);
            }
            return ActionResult.PASS;
        });

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            onChatMessage(message.getSignedContent());
        });

        AIPlayerMod.LOGGER.info("[BehaviorObserver] 行为观察器已注册");
    }

    private void onBlockBreak(BlockState state) {
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        blockBreakCounts.merge(blockId, 1, Integer::sum);
        pendingBehaviorUpdates.merge(blockId, 0.05, Double::sum);
        observationCount++;
        checkEvolution();
    }

    private void onEntityAttack(LivingEntity entity) {
        String entityType = entity.getType().getName().getString();
        entityAttackCounts.merge(entityType, 1, Integer::sum);
        pendingBehaviorUpdates.merge(entityType, -0.03, Double::sum);
        observationCount++;
        checkEvolution();
    }

    private void onChatMessage(String message) {
        if (message == null) return;
        String lower = message.toLowerCase();
        List<String> keywords = extractItemKeywords(lower);
        chatKeywords.addAll(keywords);

        for (String keyword : keywords) {
            double delta = 0.03;
            if (lower.contains("喜欢") || lower.contains("想要") || lower.contains("优先") || lower.contains("like")
                    || lower.contains("want") || lower.contains("love")) {
                delta = 0.07;
            } else if (lower.contains("讨厌") || lower.contains("烦") || lower.contains("hate")
                    || lower.contains("dislike")) {
                delta = -0.07;
            }
            pendingChatUpdates.merge(keyword, delta, Double::sum);
        }
        observationCount++;
        checkEvolution();
    }

    private List<String> extractItemKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        Set<String> knownItems = Set.of(
                "钻石", "铁矿", "金矿", "煤矿", "红石", "青金石", "绿宝石",
                "diamond", "iron", "gold", "coal", "redstone", "lapis", "emerald",
                "橡木", "桦木", "石头", "沙", "玻璃", "雪", "冰",
                "oak", "birch", "stone", "sand", "glass", "snow", "ice",
                "小麦", "胡萝卜", "土豆", "苹果", "肉", "鱼",
                "wheat", "carrot", "potato", "apple", "meat", "fish",
                "剑", "斧", "镐", "铲", "锄",
                "sword", "axe", "pickaxe", "shovel", "hoe",
                "僵尸", "骷髅", "蜘蛛", "苦力怕", "末影人",
                "zombie", "skeleton", "spider", "creeper", "enderman"
        );

        for (String item : knownItems) {
            if (text.contains(item)) {
                keywords.add(item);
            }
        }
        return keywords;
    }

    private void checkEvolution() {
        if (observationCount >= EVOLVE_THRESHOLD) {
            triggerEvolution();
            observationCount = 0;
        }
    }

    public void triggerEvolution() {
        if (pendingBehaviorUpdates.isEmpty() && pendingChatUpdates.isEmpty()) return;

        Map<String, Double> commandUpdates = new HashMap<>();
        Map<String, Double> behaviorUpdates = new HashMap<>(pendingBehaviorUpdates);
        Map<String, Double> chatUpdates = new HashMap<>(pendingChatUpdates);

        characterManager.evolvePreferences(behaviorUpdates, commandUpdates, chatUpdates);

        pendingBehaviorUpdates.clear();
        pendingChatUpdates.clear();
        AIPlayerMod.LOGGER.info("[BehaviorObserver] 性格演化已触发");
    }

    public String getBehaviorSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("已观察 ").append(observationCount).append(" 次行为 (阈值用完后触发演化)\n");
        if (!blockBreakCounts.isEmpty()) {
            sb.append("挖掘统计:\n");
            blockBreakCounts.entrySet().stream()
                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                    .limit(5)
                    .forEach(e -> sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append("次\n"));
        }
        return sb.toString();
    }
}
