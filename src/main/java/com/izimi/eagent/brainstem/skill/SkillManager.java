package com.izimi.eagent.brainstem.skill;

import com.izimi.eagent.amygdala.ConditionedReflex;
import static com.izimi.eagent.amygdala.ReflexConstants.*;
import com.izimi.eagent.brainstem.innate.AttackSkill;
import com.izimi.eagent.brainstem.innate.CraftSkill;
import com.izimi.eagent.brainstem.innate.DigSkill;
import com.izimi.eagent.brainstem.innate.JumpSkill;
import com.izimi.eagent.brainstem.innate.MoveSkill;
import com.izimi.eagent.brainstem.innate.SneakSkill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;

public class SkillManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    private final Map<String, Skill> skills = new HashMap<>();

    public SkillManager() {
        registerInnateSkills();
        loadConditionedSkills();
    }

    private void registerInnateSkills() {
        registerSkill(new MoveSkill());
        registerSkill(new DigSkill());
        registerSkill(new AttackSkill());
        registerSkill(new CraftSkill());
        registerSkill(new JumpSkill());
        registerSkill(new SneakSkill());

        saveInnateSkillFiles();
    }

    private void registerSkill(Skill skill) {
        skills.put(skill.getSkillId(), skill);
    }

    public Skill getSkill(String skillId) {
        return skills.get(skillId);
    }

    public Map<String, Skill> getSkills() {
        return skills;
    }

    public void registerConditionedSkill(Skill skill) {
        skills.put(skill.getSkillId(), skill);
        saveConditionedSkillFile(skill);
    }

    private void saveInnateSkillFiles() {
        for (Skill skill : skills.values()) {
            if ("innate".equals(skill.getType())) {
                saveSkillToFile(FileUtil.getInnateSkillsDir().resolve(skill.getSkillId() + ".skill"), skill);
            }
        }
    }

    private void saveConditionedSkillFile(Skill skill) {
        saveSkillToFile(FileUtil.getConditionedSkillsDir().resolve(skill.getSkillId() + ".skill"), skill);
    }

    private void saveSkillToFile(Path path, Skill skill) {
        Map<String, Object> skillData = new HashMap<>();
skillData.put(KEY_SKILL_ID, skill.getSkillId());
skillData.put("type", skill.getType());
skillData.put("name", skill.getName());
        JsonUtil.writeToFileSafeAtomic(path, skillData);
    }

    @SuppressWarnings("unchecked")
    private void loadConditionedSkills() {
        Path dir = FileUtil.getConditionedSkillsDir();
        if (!Files.exists(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".skill")).forEach(p -> {
                try {
                    Map<String, Object> data = JsonUtil.readFromFile(p, Map.class);
                    if (data != null) {
String skillId = (String) data.get(KEY_SKILL_ID);
String name = (String) data.getOrDefault("name", skillId);
                        ConditionedReflex.ConditionedSkill skill = new ConditionedReflex.ConditionedSkill(skillId, name);
                        registerSkill(skill);
                    }
                } catch (Exception e) {
                    LOGGER.warn("加载条件反射技能文件失败: {}", e.getMessage());
                }
            });
        } catch (IOException e) {
            LOGGER.warn("读取条件反射技能目录失败: {}", e.getMessage());
        }
    }
}
