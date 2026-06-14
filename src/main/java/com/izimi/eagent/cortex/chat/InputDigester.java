package com.izimi.eagent.cortex.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InputDigester {

    public record Digested(String intent, List<String> entities, int count, String rawPreview) {
        public static final int MAX_PREVIEW_CHARS = 80;
    }

    private static final Pattern COUNT_PATTERN = Pattern.compile("(\\d+)\\s*(?:个|块|只|根|组|次)");

    private static final Map<String, String> INTENT_MAP = Map.ofEntries(
        Map.entry("挖", "mine"),       Map.entry("采", "mine"),       Map.entry("收集", "collect"),
        Map.entry("打", "attack"),     Map.entry("杀", "attack"),     Map.entry("攻击", "attack"),
        Map.entry("合成", "craft"),    Map.entry("做", "craft"),      Map.entry("建造", "build"),
        Map.entry("去", "move"),       Map.entry("走到", "move"),     Map.entry("来", "move"),
        Map.entry("拿", "retrieve"),   Map.entry("给我", "retrieve"),
        Map.entry("放", "place"),      Map.entry("放下", "place"),
        Map.entry("找", "explore"),    Map.entry("探索", "explore"),
        Map.entry("种", "plant"),      Map.entry("种植", "plant"),
        Map.entry("修理", "repair"),   Map.entry("修复", "repair"),
        Map.entry("附魔", "enchant"),
        Map.entry("烧", "smelt"),      Map.entry("冶炼", "smelt"),
        Map.entry("吃", "eat"),        Map.entry("喝", "drink"),
        Map.entry("扔", "discard"),    Map.entry("丢", "discard"),
        Map.entry("交易", "trade"),    Map.entry("卖", "trade"),
        Map.entry("看", "look"),       Map.entry("检查", "inspect"),
        Map.entry("帮忙", "help"),     Map.entry("帮助", "help"),
        Map.entry("跟随", "follow"),   Map.entry("跟", "follow"),
        Map.entry("停止", "stop"),     Map.entry("停", "stop"),
        Map.entry("回来", "return"),   Map.entry("回家", "return"),
        Map.entry("完成", "done"),     Map.entry("做好了", "done"),
        Map.entry("继续", "continue"), Map.entry("接着", "continue"),
        Map.entry("等待", "wait"),     Map.entry("等", "wait"),
        Map.entry("汇报", "report"),   Map.entry("报告", "report"),
        Map.entry("保存", "save"),     Map.entry("存档", "save"),
        Map.entry("退出", "quit"),     Map.entry("离开", "quit")
    );

    private static final Pattern ENTITY_PATTERN = Pattern.compile(
        "[\\w\u4e00-\u9fff]{2,8}"
    );

    public Digested digest(String raw) {
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
        for (var entry : INTENT_MAP.entrySet()) {
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
        return switch (word) {
            case "的", "了", "吗", "呢", "吧", "啊", "在", "是",
                 "我", "你", "他", "她", "它", "我们", "你们", "他们",
                 "这个", "那个", "什么", "怎么", "为什么", "多少",
                 "可以", "能够", "想", "要", "有", "没", "不", "很",
                 "the", "is", "are", "was", "were", "do", "does", "did",
                 "has", "have", "had", "can", "could", "will", "would",
                 "this", "that", "these", "those", "what", "who", "how" -> true;
            default -> false;
        };
    }
}
