package com.izimi.eagent.cortex.api;

import com.google.gson.Gson;
import com.izimi.eagent.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PersonaManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private static final Gson GSON = new Gson();

    private volatile String activePersona = "";
    private volatile Map<String, List<String>> activeOverrides = Collections.emptyMap();
    private volatile String activeFormatHint = "";
    private volatile boolean personaLocked = false;
    private static final String DEFAULT_PERSONA = "你是一个Minecraft AI助手。";
    private static final String PERSONA_FILE = "active_persona.txt";
    private static final String PERSONAS_DIR = "personas";

    private final TemplateManager templateManager;

    public PersonaManager(TemplateManager templateManager) {
        this.templateManager = templateManager;
        loadPersona();
    }

    public void setPersona(String personaDescription) {
        this.activeOverrides = Collections.emptyMap();
        this.activeFormatHint = "";
        this.activePersona = personaDescription != null ? personaDescription : "";
        templateManager.setActivePersona(activePersona);
        savePersona();
        LOGGER.info("[Persona] 角色设定已更新: {}", activePersona.substring(0, Math.min(activePersona.length(), 50)));
    }

    public void loadProfile(String name) {
        Path personasDir = FileUtil.getCharacterDir().resolve(PERSONAS_DIR);
        Path file = personasDir.resolve(name + ".json");
        if (!Files.exists(file)) {
            LOGGER.warn("[Persona] 找不到角色档案: {}", file);
            setPersona(name);
            return;
        }
        try {
            String json = Files.readString(file);
            PersonaProfile profile = GSON.fromJson(json, PersonaProfile.class);
            if (profile == null || profile.systemPrompt() == null || profile.systemPrompt().isBlank()) {
                LOGGER.warn("[Persona] 角色档案格式错误: {}", name);
                return;
            }
            this.activePersona = profile.systemPrompt();
            this.activeOverrides = profile.localOverrides() != null
                ? profile.localOverrides() : Collections.emptyMap();
            this.activeFormatHint = profile.formatHint() != null ? profile.formatHint() : "";
            templateManager.setActivePersona(activePersona);
            savePersona();
            LOGGER.info("[Persona] 已加载角色档案: {} ({} 条覆盖)", name, activeOverrides.size());
        } catch (IOException e) {
            LOGGER.warn("[Persona] 读取角色档案失败: {} - {}", name, e.getMessage());
        }
    }

    public Map<String, List<String>> getActiveOverrides() {
        return activeOverrides;
    }

    public String getFormatHint() { return activeFormatHint; }

    public String getPersona() { return activePersona; }

    public void clearPersona() {
        this.activeOverrides = Collections.emptyMap();
        this.activeFormatHint = "";
        setPersona("");
    }

    public boolean hasPersona() { return !activePersona.isEmpty(); }

    public String getEffectivePrompt() {
        return hasPersona() ? activePersona : DEFAULT_PERSONA;
    }

    public boolean isPersonaLocked() { return personaLocked; }
    public void setPersonaLocked(boolean locked) { this.personaLocked = locked; }

    private void savePersona() {
        try {
            Path dir = FileUtil.getCharacterDir();
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(PERSONA_FILE), activePersona);
        } catch (IOException e) {
            LOGGER.warn("[Persona] 保存失败: {}", e.getMessage());
        }
    }

    private void loadPersona() {
        try {
            Path path = FileUtil.getCharacterDir().resolve(PERSONA_FILE);
            if (Files.exists(path)) {
                activePersona = Files.readString(path).trim();
                templateManager.setActivePersona(activePersona);
                if (!activePersona.isEmpty()) {
                    LOGGER.info("[Persona] 已加载角色设定");
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[Persona] 加载失败: {}", e.getMessage());
        }
    }
}
