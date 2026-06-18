package com.izimi.eagent.cortex.chat;

import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InputDigester {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    public record Digested(String intent, List<String> entities, int count, String rawPreview) {
        public static final int MAX_PREVIEW_CHARS = 80;
    }

    private static final Pattern COUNT_PATTERN = Pattern.compile("(\\d+)\\s*(?:个|块|只|根|组|次)");

    private static Map<String, String> intentMap = null;
    private static Set<String> stopWords = null;
    private static boolean loaded = false;

    private static final Pattern ENTITY_PATTERN = Pattern.compile(
        "[\\w\u4e00-\u9fff]{2,8}"
    );

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        try {
            Map<String, Object> intentData = JsonUtil.readMapFromFileSafe(
                    FileUtil.getConfigDir().resolve("intent_map.json"));
            if (intentData != null && intentData.get("intents") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> map = (Map<String, String>) intentData.get("intents");
                intentMap = new HashMap<>(map);
            } else {
                intentMap = initDefaultIntentMap();
            }
        } catch (Exception e) {
            intentMap = initDefaultIntentMap();
        }

        try {
            Map<String, Object> stopData = JsonUtil.readMapFromFileSafe(
                    FileUtil.getConfigDir().resolve("stop_words.json"));
            if (stopData != null && stopData.get("words") instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) stopData.get("words");
                stopWords = new HashSet<>(list);
            } else {
                stopWords = initDefaultStopWords();
            }
        } catch (Exception e) {
            stopWords = initDefaultStopWords();
        }

        LOGGER.debug("[InputDigester] intentMap={}, stopWords={}", intentMap.size(), stopWords.size());
    }

    private static Map<String, String> initDefaultIntentMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("挖", "mine"); m.put("采", "mine"); m.put("收集", "collect");
        m.put("打", "attack"); m.put("杀", "attack"); m.put("攻击", "attack");
        m.put("合成", "craft"); m.put("做", "craft"); m.put("建造", "build");
        m.put("去", "move"); m.put("走到", "move"); m.put("来", "move");
        m.put("拿", "retrieve"); m.put("给我", "retrieve");
        m.put("放", "place"); m.put("放下", "place");
        m.put("找", "explore"); m.put("探索", "explore");
        m.put("种", "plant"); m.put("种植", "plant");
        m.put("修理", "repair"); m.put("修复", "repair");
        m.put("附魔", "enchant");
        m.put("烧", "smelt"); m.put("冶炼", "smelt");
        m.put("吃", "eat"); m.put("喝", "drink");
        m.put("扔", "discard"); m.put("丢", "discard");
        m.put("交易", "trade"); m.put("卖", "trade");
        m.put("看", "look"); m.put("检查", "inspect");
        m.put("帮忙", "help"); m.put("帮助", "help");
        m.put("跟随", "follow"); m.put("跟", "follow");
        m.put("停止", "stop"); m.put("停", "stop");
        m.put("回来", "return"); m.put("回家", "return");
        m.put("完成", "done"); m.put("做好了", "done");
        m.put("继续", "continue"); m.put("接着", "continue");
        m.put("等待", "wait"); m.put("等", "wait");
        m.put("汇报", "report"); m.put("报告", "report");
        m.put("保存", "save"); m.put("存档", "save");
        m.put("退出", "quit"); m.put("离开", "quit");
        return m;
    }

    private static Set<String> initDefaultStopWords() {
        return new HashSet<>(List.of(
            "的", "了", "吗", "呢", "吧", "啊", "在", "是",
            "我", "你", "他", "她", "它", "我们", "你们", "他们",
            "这个", "那个", "什么", "怎么", "为什么", "多少",
            "可以", "能够", "想", "要", "有", "没", "不", "很",
            "the", "is", "are", "was", "were", "do", "does", "did",
            "has", "have", "had", "can", "could", "will", "would",
            "this", "that", "these", "those", "what", "who", "how"
        ));
    }

    public Digested digest(String raw) {
        ensureLoaded();
        if (raw == null || raw.isBlank()) {
            return new Digested("chat", List.of(), 1, "");
        }
        int count = extractCount(raw);
        String intent = extractIntent(raw);
        List<String> entities = extractEntities(raw);
        String preview = raw.length() > Digested.MAX_PREVIEW_CHARS
                ? raw.substring(0, Digested.MAX_PREVIEW_CHARS) + "…"
                : raw;
        return new Digested(intent, entities, count, preview);
    }

    private int extractCount(String raw) {
        Matcher m = COUNT_PATTERN.matcher(raw);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
            }
        }
        return 1;
    }

    private String extractIntent(String raw) {
        for (var entry : intentMap.entrySet()) {
            if (raw.contains(entry.getKey())) return entry.getValue();
        }
        return "chat";
    }

    private List<String> extractEntities(String raw) {
        List<String> entities = new ArrayList<>();
        Matcher m = ENTITY_PATTERN.matcher(raw);
        while (m.find()) {
            String word = m.group();
            if (word.length() >= 2 && !isFilterWord(word)) {
                entities.add(word);
            }
        }
        return entities;
    }

    private boolean isFilterWord(String word) {
        return stopWords.contains(word);
    }
}
