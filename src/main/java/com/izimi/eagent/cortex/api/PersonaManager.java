package com.izimi.eagent.cortex.api;

import com.izimi.eagent.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PersonaManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private volatile String activePersona = "";
    private static final String DEFAULT_PERSONA = "你是一个Minecraft AI助手。";
    private static final String PERSONA_FILE = "active_persona.txt";

    private final TemplateManager templateManager;

    public PersonaManager(TemplateManager templateManager) {
        this.templateManager = templateManager;
        loadPersona();
    }

    public void setPersona(String personaDescription) {
        this.activePersona = personaDescription != null ? personaDescription : "";
        templateManager.setActivePersona(activePersona);
        savePersona();
        LOGGER.info("[Persona] 角色设定已更新: {}", activePersona.substring(0, Math.min(activePersona.length(), 50)));
    }

    public String getPersona() { return activePersona; }

    public void clearPersona() {
        setPersona("");
    }

    public boolean hasPersona() { return !activePersona.isEmpty(); }

    public String getEffectivePrompt() {
        return hasPersona() ? activePersona : DEFAULT_PERSONA;
    }

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
