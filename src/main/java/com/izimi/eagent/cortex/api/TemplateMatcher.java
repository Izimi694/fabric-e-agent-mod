package com.izimi.eagent.cortex.api;

import com.izimi.eagent.api.BotContext;
import com.izimi.eagent.api.WorldContext;
import com.izimi.eagent.cortex.api.TemplateManager.TemplateType;
import com.izimi.eagent.cortex.chat.LocalChatHandler;
import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;
import com.izimi.eagent.util.TagResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TemplateMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private static List<Pattern> clarificationPatterns = null;
    private static List<Pattern> taskPatterns = null;
    private static List<Pattern> reflexPatterns = null;
    private static List<Pattern> socialPatterns = null;
    private static boolean loaded = false;

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        Map<String, Object> data = null;
        try {
            data = JsonUtil.readMapFromFileSafe(
                    FileUtil.getConfigDir().resolve("template_patterns.json"));
        } catch (Exception e) {
        }
        if (data != null && data.get("patterns") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, List<String>> patterns = (Map<String, List<String>>) data.get("patterns");
            clarificationPatterns = compilePatterns(patterns.get("CLARIFICATION"));
            taskPatterns = compilePatterns(patterns.get("TASK_PLAN"));
            reflexPatterns = compilePatterns(patterns.get("REFLEX_CREATE"));
            socialPatterns = compilePatterns(patterns.get("CHATTING"));
        } else {
            loadDefaults();
            try {
                JsonUtil.writeToFileSafe(
                        FileUtil.getConfigDir().resolve("template_patterns.json"),
                        Map.of("version", 1, "patterns", defaultPatternsMap()));
            } catch (RuntimeException e) {
            }
        }
        LOGGER.debug("[TemplateMatcher] patterns: clarification={}, task={}, reflex={}, social={}",
                clarificationPatterns.size(), taskPatterns.size(), reflexPatterns.size(), socialPatterns.size());
    }

    private static List<Pattern> compilePatterns(List<String> regexes) {
        if (regexes == null || regexes.isEmpty()) return List.of();
        return regexes.stream().map(Pattern::compile).collect(Collectors.toList());
    }

    private static Map<String, List<String>> defaultPatternsMap() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("TASK_PLAN", List.of(
            "^(挖|打|建|造|种|收集|找|去|杀|做|生产|获取|采|制作|建造|盖|放置|合成|拿)(.{0,10}(个|的|些|点)?)",
            "\\d+个"
        ));
        m.put("REFLEX_CREATE", List.of(
            "(学|教|记住|反射|习惯|自动)(.{0,5}(挖|打|建|种|做|合成))",
            "(如果|当|遇到).*(就|则|可以|应该)",
            "(行为|反应|响应|动作|任务).*(规则|模式|方式)"
        ));
        m.put("CHATTING", List.of(
            "^(你好|嗨|hi|hello|在吗|你在吗)",
            "(谢谢|多谢|thx|thanks)",
            "(再见|拜拜|bye|拜)",
            "(厉害|牛逼|真棒)",
            "(傻|笨|蠢)",
            "(喵|~)$",
            "^(在吗|在不在)"
        ));
        m.put("CLARIFICATION", List.of(
            "(.?怎么|如何|怎样|能不能|可以.?(吗|么)|说明|解释)(?!办)(?!样)(?!回事)",
            "(我想|我要|帮我|给我|能不能)",
            "^(你|你帮我).{0,3}(做|弄|搞|建|造|打|挖|种|喂)",
            "[?？]$",
            "^(调|改|变成|设置为|改成)"
        ));
        return m;
    }

    private static void loadDefaults() {
        clarificationPatterns = compilePatterns(defaultPatternsMap().get("CLARIFICATION"));
        taskPatterns = compilePatterns(defaultPatternsMap().get("TASK_PLAN"));
        reflexPatterns = compilePatterns(defaultPatternsMap().get("REFLEX_CREATE"));
        socialPatterns = compilePatterns(defaultPatternsMap().get("CHATTING"));
    }

    public TemplateType match(String message, BotContext botCtx, WorldContext worldCtx) {
        ensureLoaded();
        if (message == null || message.isBlank()) return null;

        LocalChatHandler chatHandler = worldCtx.cortex().chatHandler();
        if (chatHandler != null && chatHandler.canHandle(message)) return null;

        String lower = message.toLowerCase();

        for (Pattern p : taskPatterns) {
            if (p.matcher(lower).find()) return TemplateType.TASK_PLAN;
        }

        for (Pattern p : reflexPatterns) {
            if (p.matcher(lower).find()) return TemplateType.REFLEX_CREATE;
        }

        for (Pattern p : socialPatterns) {
            if (p.matcher(lower).find()) return TemplateType.CHAT_RESPONSE;
        }

        for (Pattern p : clarificationPatterns) {
            if (p.matcher(lower).find()) return TemplateType.CLARIFICATION;
        }

        if (TagResolver.hasConcreteNoun(lower)) return TemplateType.TASK_PLAN;
        return TemplateType.CHAT_RESPONSE;
    }
}
