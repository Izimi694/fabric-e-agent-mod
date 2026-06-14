package com.izimi.eagent.cortex.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TemplateManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    public enum TemplateType {
        // 用户交互模板
        CLARIFICATION,
        TASK_PLAN,
        REFLEX_CREATE,
        CHAT_RESPONSE,
        // 后台内部模板
        EVALUATION_BATCH,
        FAILURE_CLASSIFY
    }

    private static final Gson GSON = new Gson();
    private static final long GLOBAL_COOLDOWN_MS = 5000;
    private static volatile long lastGlobalCall = 0;

    private final AIClient aiClient;
    private final Map<TemplateType, List<Consumer<JsonObject>>> postFillHooks = new HashMap<>();
    private volatile String activePersona = "";

    public TemplateManager(AIClient aiClient) {
        this.aiClient = aiClient;
    }

    public void registerPostFillHook(TemplateType type, Consumer<JsonObject> hook) {
        postFillHooks.computeIfAbsent(type, k -> new ArrayList<>()).add(hook);
    }

    public CompletableFuture<JsonObject> fill(TemplateType type, Map<String, Object> context) {
        if (!tryAcquireLLMSlot()) {
            return CompletableFuture.completedFuture(null);
        }

        String template = buildTemplate(type, context);

        List<AIMessage> messages = List.of(
                new AIMessage("system", buildSystemPrompt(type)),
                new AIMessage("user", template)
        );

        return aiClient.sendMessage(messages).thenApply(response -> {
            if (response == null || response.getMessage() == null || response.getMessage().isEmpty()) {
                return null;
            }
            try {
                JsonObject result = GSON.fromJson(response.getMessage(), JsonObject.class);
                if (result != null) {
                    List<Consumer<JsonObject>> hooks = postFillHooks.get(type);
                    if (hooks != null) {
                        for (Consumer<JsonObject> hook : hooks) {
                            hook.accept(result);
                        }
                    }
                }
                return result;
            } catch (JsonSyntaxException | NullPointerException e) {
                LOGGER.warn("[TemplateManager] 填空解析失败: {} - {}", type, e.getMessage());
                return null;
            }
        });
    }

    private boolean tryAcquireLLMSlot() {
        long now = System.currentTimeMillis();
        if (now - lastGlobalCall < GLOBAL_COOLDOWN_MS) {
            return false;
        }
        lastGlobalCall = now;
        return true;
    }

    /** 注入当前角色设定到系统提示 */
    public String injectPersona(String basePrompt, String persona) {
        if (persona == null || persona.isBlank()) return basePrompt;
        return persona + "\n\n" + basePrompt;
    }

    private String buildSystemPrompt(TemplateType type) {
        String base = switch (type) {
            case CLARIFICATION -> "你是Minecraft AI助手。用户输入可能不明确，请分析用户意图并输出澄清问题。";
            case REFLEX_CREATE -> "你是一个Minecraft AI助手。请填空以下JSON模板，生成一个新的反射技能。";
            case TASK_PLAN -> "你是一个Minecraft AI助手。请填空以下JSON模板，生成带依赖关系的任务DAG。输出DAG结构，包含subtasks数组，每个子任务有id、name、action、target、depends_on[{id, type, weight, bindings[{from, to, transform}]}]，以及bottleneck_nodes列表。";
            case CHAT_RESPONSE -> "你是一个Minecraft AI助手。请填空以下JSON模板，生成对玩家的回复。保持语气友好、简洁。";
            case EVALUATION_BATCH -> "你是一个行为评价系统。请填空以下JSON模板，输出评价结果。";
            case FAILURE_CLASSIFY -> "你是一个错误分析系统。请填空以下JSON模板，分析失败原因。";
        };
        return injectPersona(base, activePersona);
    }

    /** 设置当前角色设定（供 PersonaManager 调用） */
    public void setActivePersona(String persona) {
        this.activePersona = persona != null ? persona : "";
    }

    public String getActivePersona() { return activePersona; }

    private String buildTemplate(TemplateType type, Map<String, Object> context) {
        return switch (type) {
            case CLARIFICATION -> {
                String userInput = (String) context.getOrDefault("userInput", "");
                String availableTemplates = (String) context.getOrDefault("availableTemplates", "TASK_PLAN, REFLEX_CREATE, CHAT_RESPONSE");
                yield String.format("""
                        用户输入: "%s"
                        可用模板: %s
                        填空以下JSON，分析用户意图:
                        {
                          "ambiguity_detected": true,
                          "possible_interpretations": ["{解释1}", "{解释2}"],
                          "missing_info": ["{缺失信息}"],
                          "suggested_question": "{澄清问题}"
                        }
                        注意: 如果用户输入明确(如"挖10个铁矿")则 ambiguity_detected=false。
                        """, userInput, availableTemplates);
            }
            case REFLEX_CREATE -> {
                String skill = (String) context.getOrDefault("skill", "unknown");
                String target = (String) context.getOrDefault("target", "unknown");
                yield String.format("""
                        填空以下JSON:
                        {
                          "reflex_id": "reflex_%s_%s",
                          "display_name": "{填显示名称}",
                          "steps": [
                            {"action": "{填动作1}", "target": "{填目标1}"},
                            {"action": "{填动作2}", "target": "{填目标2}"}
                          ]
                        }""", skill, target);
            }
            case TASK_PLAN -> {
                String goal = (String) context.getOrDefault("goal", "");
                String availableActions = (String) context.getOrDefault("availableActions", "moveTo, dig, attack, placeBlock, useItem, equipItem, craft, chat, jump, lookAt, openBlock, closeWindow, clickSlot");
                yield String.format("""
                        任务: %s
                        可用原子动作: %s
                        填空以下JSON (输出DAG依赖图):
                        {
                          "task_id": "%s",
                          "subtasks": [
                            {
                              "id": "a1",
                              "name": "{子任务名称}",
                              "action": "{原子动作}",
                              "target": "{目标}",
                              "count": 1,
                              "depends_on": [
                                {"id": "{上游id}", "type": "hard|soft", "weight": 0.95, "bindings": [
                                  {"from": "output.{字段}", "to": "{参数字段}"}
                                ]}
                              ],
                              "is_bottleneck": false
                            }
                          ],
                          "bottleneck_nodes": ["{瓶颈节点id}"]
                        }""", goal, availableActions, goal.replaceAll("\\s+", "_").toLowerCase());
            }
            case CHAT_RESPONSE -> {
                String playerMsg = (String) context.getOrDefault("playerMessage", "");
                String contextInfo = (String) context.getOrDefault("contextInfo", "");
                String formatHint = (String) context.getOrDefault("formatHint", "");
                String formatSection = formatHint.isEmpty() ? "" : "格式提示: " + formatHint + "\n";
                yield String.format("""
                        玩家消息: "%s"
                        上下文: %s
                        %s填空以下JSON:
                        {
                          "reply_text": "{回复文本}",
                          "suggested_emote": "{可选动作}",
                          "tone": "warm|neutral|cold"
                        }""", playerMsg, contextInfo, formatSection);
            }
            case EVALUATION_BATCH -> {
                String evaluations = (String) context.getOrDefault("evaluations", "");
                String reflexContext = (String) context.getOrDefault("reflexContext", "");
                yield String.format("""
                        评价:
                        %s
                        最近执行:
                        %s
                        填空以下JSON:
                        [
                          {"reflex_id": "reflex_{填技能}_{填目标}", "delta": 0.0}
                        ]""", evaluations, reflexContext);
            }
            case FAILURE_CLASSIFY -> {
                String failureInfo = (String) context.getOrDefault("failureInfo", "");
                yield String.format("""
                        失败信息:
                        %s
                        填空以下JSON:
                        {
                          "reflex_id": "reflex_{填技能}_{填目标}",
                          "feature_key": "{填环境特征}",
                          "feature_value": true,
                          "outcome": "failure"
                        }""", failureInfo);
            }
        };
    }
}
