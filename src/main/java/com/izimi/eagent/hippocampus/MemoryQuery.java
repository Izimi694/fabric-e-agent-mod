package com.izimi.eagent.hippocampus;

import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MemoryQuery {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private final MemoryManager memoryManager;

    private static List<String> triggerPatterns = null;
    private static Set<String> timeKeywords = null;
    private static Set<String> stopWords = null;
    private static boolean loaded = false;

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        Map<String, Object> data = null;
        try {
            data = JsonUtil.readMapFromFileSafe(
                    FileUtil.getConfigDir().resolve("memory_triggers.json"));
        } catch (Exception e) {
        }
        if (data != null) {
            if (data.get("triggers") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> triggers = (Map<String, String>) data.get("triggers");
                triggerPatterns = new ArrayList<>(triggers.values());
            }
            if (data.get("time_keywords") instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) data.get("time_keywords");
                timeKeywords = new HashSet<>(list);
            }
            if (data.get("stop_words") instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> list = (List<String>) data.get("stop_words");
                stopWords = new HashSet<>(list);
            }
        }

        if (triggerPatterns == null || triggerPatterns.isEmpty()) {
            triggerPatterns = initDefaultTriggers();
        }
        if (timeKeywords == null || timeKeywords.isEmpty()) {
            timeKeywords = initDefaultTimeKeywords();
        }
        if (stopWords == null || stopWords.isEmpty()) {
            stopWords = initDefaultStopWords();
        }

        LOGGER.debug("[MemoryQuery] triggers={}, timeKeywords={}, stopWords={}",
                triggerPatterns.size(), timeKeywords.size(), stopWords.size());
    }

    private static List<String> initDefaultTriggers() {
        return new ArrayList<>(List.of(
            "还记得", "你之前", "你几天前", "你刚才", "你昨天",
            "记得", "之前做了", "上次", "你做过", "你的记忆",
            "remember", "before", "last time", "you did", "your memory"
        ));
    }

    private static Set<String> initDefaultTimeKeywords() {
        return new HashSet<>(Set.of(
            "昨天", "今天", "前天", "之前", "几天前", "上周",
            "yesterday", "today", "ago", "before", "last week"
        ));
    }

    private static Set<String> initDefaultStopWords() {
        return new HashSet<>(Set.of(
            "的", "了", "吗", "呢", "吧", "啊", "在", "是",
            "the", "a", "an", "is", "are", "was", "were", "did", "do", "does"
        ));
    }

    public MemoryQuery(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
    }

    public boolean isMemoryQuery(String message) {
        ensureLoaded();
        if (message == null) return false;
        String lower = message.toLowerCase();
        for (String pattern : triggerPatterns) {
            if (lower.contains(pattern)) return true;
        }
        return false;
    }

    public List<String> extractKeywords(String message) {
        ensureLoaded();
        if (message == null) return Collections.emptyList();
        List<String> keywords = new ArrayList<>();

        String lower = message.toLowerCase();
        for (String trigger : triggerPatterns) {
            lower = lower.replace(trigger, " ");
        }
        for (String time : timeKeywords) {
            lower = lower.replace(time, " ");
        }

        String[] words = lower.split("[\\s，。！？,.!?]+");
        for (String word : words) {
            word = word.trim();
            if (word.length() >= 2 && !isStopWord(word)) {
                keywords.add(word);
            }
        }

        return keywords;
    }

    public List<MemoryEntry> query(String message) {
        if (!isMemoryQuery(message)) {
            return memoryManager.getRecentMemories();
        }

        List<String> keywords = extractKeywords(message);
        if (keywords.isEmpty()) {
            return memoryManager.getRecentMemories();
        }

        return memoryManager.searchByKeywords(keywords);
    }

    public String formatMemories(List<MemoryEntry> memories) {
        if (memories.isEmpty()) return "没有找到相关记忆";

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(memories.size()).append(" 条相关记忆:\n");
        for (int i = 0; i < Math.min(memories.size(), 5); i++) {
            MemoryEntry m = memories.get(i);
            sb.append("● [").append(m.getId()).append("] ").append(m.getSummary());
            if (m.gameDay > 0) {
                sb.append(" (第").append(m.gameDay).append("天)");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private boolean isStopWord(String word) {
        return stopWords.contains(word) || word.length() < 2;
    }
}
