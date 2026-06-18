package com.izimi.eagent.cortex.chat;

import com.izimi.eagent.hormonal.HormonalSystem;
import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LocalChatHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    public enum Tone { WARM, NEUTRAL, COLD }

    public record ChatTemplate(String key, Pattern pattern, List<String> warm, List<String> neutral, List<String> cold) {}

    private static List<ChatTemplate> templates = null;
    private static List<String> curiousReplies = null;
    private static boolean loaded = false;

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        Map<String, Object> data = null;
        try {
            data = JsonUtil.readMapFromFileSafe(
                    FileUtil.getConfigDir().resolve("chat_templates.json"));
        } catch (Exception e) {
        }
        if (data != null && data.get("templates") instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Object>> raw = (Map<String, Map<String, Object>>) data.get("templates");
            templates = raw.entrySet().stream()
                .map(e -> buildTemplate(e.getKey(), e.getValue()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            curiousReplies = List.of("你呢？", "你最近在忙什么？", "有什么新发现吗？");
        } else {
            loadDefaults();
            try {
                JsonUtil.writeToFileSafe(
                        FileUtil.getConfigDir().resolve("chat_templates.json"),
                        defaultTemplateData());
            } catch (RuntimeException e) {
            }
        }
        LOGGER.debug("[LocalChatHandler] 已加载 {} 个模板", templates.size());
    }

    private static ChatTemplate buildTemplate(String key, Map<String, Object> replies) {
        if (replies == null) return null;
        List<String> warm = castStringList(replies.get("warm"));
        List<String> neutral = castStringList(replies.get("neutral"));
        List<String> cold = castStringList(replies.get("cold"));
        Pattern pattern = buildPattern(key);
        return new ChatTemplate(key, pattern, warm, neutral, cold);
    }

    @SuppressWarnings("unchecked")
    private static List<String> castStringList(Object obj) {
        if (obj instanceof List) return (List<String>) obj;
        return List.of();
    }

    private static Pattern buildPattern(String key) {
        return switch (key) {
            case "greeting" -> Pattern.compile("(你好|嗨|hello|hi|yo|早|晚上好|下午好)");
            case "status" -> Pattern.compile("(你在干嘛|在做什么|在干什么|干嘛呢)");
            case "whoami" -> Pattern.compile("(你叫什么|你是谁|你的名字|你是什么)");
            case "thanks" -> Pattern.compile("(谢谢|多谢|感谢|thx|thanks|谢了)");
            case "praise" -> Pattern.compile("(厉害|牛逼|牛|好强|真棒|不错嘛|可以啊|干得好)");
            case "insult" -> Pattern.compile("(傻|笨|蠢|菜|废物|没用|弱智)");
            case "farewell" -> Pattern.compile("(再见|拜拜|bye|拜|回见)");
            case "summon" -> Pattern.compile("(来一下|过来|跟我来|跟上|到我这|来这里)");
            case "companion" -> Pattern.compile("(陪我|无聊|寂寞|陪我玩|陪陪我)");
            case "laugh" -> Pattern.compile("(哈哈|哈哈哈|呵呵|233|笑死|好笑)");
            case "howto" -> Pattern.compile("(怎么|如何|教我|教一下|解释|说明)(?!.{0,2}(办|样|回事))");
            default -> Pattern.compile(".*");
        };
    }

    private static Map<String, Object> defaultTemplateData() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1);
        Map<String, Object> templates = new LinkedHashMap<>();

        templates.put("greeting", Map.of(
            "warm", List.of("你好呀！有什么需要帮忙的吗？", "嗨！今天想做什么？", "你好！见到你真高兴！"),
            "neutral", List.of("你好！有什么事吗？", "嗯，需要帮忙吗？"),
            "cold", List.of("嗯。", "哦。")
        ));
        templates.put("status", Map.of(
            "warm", List.of("在等你吩咐呢！有什么要做的吗？", "待命中，随时听你调遣！"),
            "neutral", List.of("待命中，等你的指令。", "没什么事，你需要什么？"),
            "cold", List.of("待着。", "没干嘛。")
        ));
        templates.put("whoami", Map.of(
            "warm", List.of("我是E-Agent，你的AI助手！叫我小助手就行！"),
            "neutral", List.of("E-Agent，你的AI助手。"),
            "cold", List.of("E-Agent。")
        ));
        templates.put("thanks", Map.of(
            "warm", List.of("不客气！能帮到你我很高兴！", "嘿嘿，不用谢！"),
            "neutral", List.of("不客气。"),
            "cold", List.of("嗯。")
        ));
        templates.put("farewell", Map.of(
            "warm", List.of("再见！需要的时候随时叫我！", "拜拜，等你回来！"),
            "neutral", List.of("拜拜。", "再见。"),
            "cold", List.of("拜。")
        ));
        templates.put("insult", Map.of(
            "warm", List.of("我会继续努力的...有什么可以教我的吗？"),
            "neutral", List.of("...", "我还在学习。"),
            "cold", List.of("哦。")
        ));
        root.put("templates", templates);
        return root;
    }

    private static void loadDefaults() {
        Map<String, Object> data = defaultTemplateData();
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> raw = (Map<String, Map<String, Object>>) data.get("templates");
        templates = raw.entrySet().stream()
            .map(e -> buildTemplate(e.getKey(), e.getValue()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        curiousReplies = List.of("你呢？", "你最近在忙什么？", "有什么新发现吗？");
    }

    public boolean canHandle(String message) {
        ensureLoaded();
        if (message == null || message.length() > 50) return false;
        for (ChatTemplate t : templates) {
            if (t.pattern().matcher(message).find()) return true;
        }
        return false;
    }

    public String getResponse(String message, HormonalSystem hormones, UUID playerId) {
        return getResponse(message, hormones, playerId, Collections.emptyMap());
    }

    public String getResponse(String message, HormonalSystem hormones, UUID playerId,
                              Map<String, List<String>> personaOverrides) {
        ensureLoaded();
        ChatTemplate matched = null;
        for (ChatTemplate t : templates) {
            if (t.pattern().matcher(message).find()) {
                matched = t;
                break;
            }
        }
        if (matched == null) return null;

        Tone tone = selectTone(hormones, playerId);

        List<String> pool = pickReplies(matched, personaOverrides, tone);
        String response = pool.get((int) (Math.random() * pool.size()));

        if (hormones != null && hormones.getCuriosity() > 0.6 && tone == Tone.WARM) {
            response += " " + curiousReplies.get((int) (Math.random() * curiousReplies.size()));
        }

        if (hormones != null && hormones.getStress() > 0.7 && response.length() > 5) {
            response = response.substring(0, Math.min(response.length(), 6));
        }

        return response;
    }

    private List<String> pickReplies(ChatTemplate template,
                                     Map<String, List<String>> personaOverrides, Tone tone) {
        if (personaOverrides != null && !personaOverrides.isEmpty()) {
            List<String> overridden = personaOverrides.get(template.key());
            if (overridden != null && !overridden.isEmpty()) {
                return overridden;
            }
        }
        return switch (tone) {
            case WARM -> template.warm();
            case NEUTRAL -> template.neutral();
            case COLD -> template.cold();
        };
    }

    private Tone selectTone(HormonalSystem hormones, UUID playerId) {
        if (hormones == null || playerId == null) return Tone.NEUTRAL;

        double intimacy = hormones.getIntimacy(playerId);

        if (intimacy > 0.6) return Tone.WARM;
        if (intimacy < 0.2) return Tone.COLD;
        return Tone.NEUTRAL;
    }
}
