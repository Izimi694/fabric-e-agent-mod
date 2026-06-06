package com.izimi.aiplayermod.skill;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.config.ModConfig;
import com.izimi.aiplayermod.task.Task;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.*;

public class ConditionedReflex {
    private final SkillManager skillManager;
    private final ModConfig config;

    private final Map<String, List<Double>> actionHistory = new HashMap<>();
    private int actionCount = 0;

    public ConditionedReflex(SkillManager skillManager, ModConfig config) {
        this.skillManager = skillManager;
        this.config = config;
    }

    public Skill match(Task task) {
        if (task == null) return null;
        String goal = task.getGoal().toLowerCase();

        for (Map.Entry<String, Skill> entry : skillManager.getSkills().entrySet()) {
            Skill skill = entry.getValue();
            if ("conditioned".equals(skill.getType())) {
                String skillId = skill.getSkillId().toLowerCase();
                if (goal.contains(skillId.replace("reflex_", ""))) {
                    return skill;
                }
            }
        }
        return null;
    }

    public void recordAction(String skillId, double effectiveness) {
        actionHistory.computeIfAbsent(skillId, k -> new ArrayList<>()).add(effectiveness);
        actionCount++;

        if (actionCount >= 20) {
            analyzeAndGenerate();
            actionCount = 0;
        }
    }

    public void executeReflex(Skill reflex, ServerPlayerEntity bot) {
        if (reflex instanceof ConditionedSkill conditioned) {
            Map<String, Object> context = new HashMap<>();
            Skill.SkillResult result = conditioned.execute(bot.getServerWorld(), bot, context);
            AIPlayerMod.LOGGER.info("[ConditionedReflex] 条件反射执行: {} -> {}", reflex.getSkillId(), result.success());
        }
    }

    private void analyzeAndGenerate() {
        for (Map.Entry<String, List<Double>> entry : actionHistory.entrySet()) {
            String skillId = entry.getKey();
            List<Double> scores = entry.getValue();

            if (scores.size() < config.reflexMinSuccesses) continue;

            long successes = scores.stream().filter(s -> s >= config.reflexThreshold).count();
            double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

            if (successes >= config.reflexMinSuccesses && avg >= config.reflexThreshold) {
                ConditionedSkill newSkill = new ConditionedSkill("reflex_" + skillId, "条件反射_" + skillId);
                skillManager.registerConditionedSkill(newSkill);
                AIPlayerMod.LOGGER.info("[ConditionedReflex] 生成条件反射: {}", skillId);
            }
        }
        actionHistory.clear();
    }

    public static class ConditionedSkill extends Skill {
        public ConditionedSkill(String skillId, String name) {
            super(skillId, name, "conditioned");
        }

        @Override
        public boolean canExecute(net.minecraft.server.world.ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
            return bot != null && world != null;
        }

        @Override
        public SkillResult execute(net.minecraft.server.world.ServerWorld world, ServerPlayerEntity bot, Map<String, Object> context) {
            return SkillResult.success("条件反射已触发: " + getName());
        }
    }
}
