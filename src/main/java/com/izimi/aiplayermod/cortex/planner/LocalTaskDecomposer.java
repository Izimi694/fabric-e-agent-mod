package com.izimi.aiplayermod.cortex.planner;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.brainstem.scheduler.ILocalPlanner;
import com.izimi.aiplayermod.cortex.api.AIResponse;

import java.util.regex.Matcher;

public class LocalTaskDecomposer implements ILocalPlanner {

    private final KnowledgeBase kb;
    private final PlanManager planManager;

    public LocalTaskDecomposer(KnowledgeBase kb, PlanManager planManager) {
        this.kb = kb;
        this.planManager = planManager;
    }

    @Override
    public boolean canHandle(String message) {
        if (message == null || message.isEmpty()) return false;
        return kb.matchTemplate(message).isPresent();
    }

    @Override
    public AIResponse decompose(String message) {
        var matched = kb.matchTemplate(message);
        if (matched.isEmpty()) return null;

        var tmpl = matched.get();
        Matcher m = tmpl.pattern().matcher(message);
        if (!m.find()) return null;

        String target = m.groupCount() >= 1 ? (m.group(m.groupCount()) != null ? m.group(m.groupCount()) : "") : "";
        String countStr = m.groupCount() >= 2 ? m.group(1) : "1";
        int count = 1;
        try {
            if (countStr != null && !countStr.isEmpty()) count = Integer.parseInt(countStr);
        } catch (NumberFormatException ignored) {}

        String taskId = "local_" + System.currentTimeMillis();
        Plan plan = planManager.createPlan(taskId, message);
        plan.subSteps.clear();

        for (int i = 0; i < count; i++) {
            for (var step : tmpl.steps()) {
                String resolvedTarget = resolveTarget(step.target(), target);
                String resolvedAction = step.action();
                if ("{tool}".equals(step.target())) {
                    String tool = resolveTool(target);
                    if (tool != null) resolvedTarget = tool;
                }
                if ("{weapon}".equals(step.target())) {
                    resolvedTarget = "sword";
                }
                plan.subSteps.add(new Plan.PlanStep(
                        step.skillId(), resolvedAction, resolvedTarget));
            }
        }

        plan.status = "active";
        plan.source = "local";

        AIPlayerMod.LOGGER.info("[LocalTaskDecomposer] 模板匹配: {} → {}×{}步",
                tmpl.name(), count, plan.subSteps.size());

        AIResponse response = new AIResponse();
        response.action = "execute_task";
        response.skill = tmpl.name();
        if (response.params == null) response.params = new AIResponse.AIResponseParams();
        response.params.target = target;
        response.params.amount = count;
        return response;
    }

    private String resolveTarget(String pattern, String extracted) {
        return pattern.replace("{target}", extracted)
                .replace("{material}", extracted);
    }

    private String resolveTool(String target) {
        if (target == null) return "wooden_pickaxe";
        var tool = kb.getTool(target);
        if (tool.isPresent()) return tool.get();
        String withoutOre = target.replace("_ore", "");
        var tool2 = kb.getTool(withoutOre);
        return tool2.orElse("wooden_pickaxe");
    }
}
