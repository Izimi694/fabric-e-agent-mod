package com.izimi.eagent.amygdala;

import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.izimi.eagent.amygdala.ReflexConstants.*;

public final class ReflexIO {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private ReflexIO() {}

    public static Path conditionedDir(UUID botId) {
        return botId != null ? FileUtil.getBotConditionedDir(botId) : FileUtil.getConditionedDir();
    }

    public static Path archivedDir(UUID botId) {
        return conditionedDir(botId).resolve(STATUS_ARCHIVED);
    }

    public static Path reflexPath(String reflexId, UUID botId) {
        return conditionedDir(botId).resolve(reflexId + FileUtil.JSON_EXT);
    }

    public static Map<String, Object> loadData(String reflexId, UUID botId) {
        return JsonUtil.readMapFromFileSafe(reflexPath(reflexId, botId));
    }

    public static void saveData(String reflexId, UUID botId, Map<String, Object> data) {
        JsonUtil.writeToFileSafeAtomic(reflexPath(reflexId, botId), data);
    }

    public static double getDouble(Map<String, Object> data, String key, double defaultVal) {
        return ((Number) data.getOrDefault(key, defaultVal)).doubleValue();
    }

    public static int getInt(Map<String, Object> data, String key, int defaultVal) {
        return ((Number) data.getOrDefault(key, defaultVal)).intValue();
    }

    public static String getString(Map<String, Object> data, String key, String defaultVal) {
        return (String) data.getOrDefault(key, defaultVal);
    }

    public static String getCategory(String reflexId, UUID botId) {
        Map<String, Object> data = loadData(reflexId, botId);
        return data != null ? (String) data.get("category") : null;
    }

    public static String getDisplayName(String reflexId, UUID botId) {
        Map<String, Object> data = loadData(reflexId, botId);
        return data != null ? (String) data.getOrDefault("displayName", reflexId) : reflexId;
    }

    public static double getProficiency(String reflexId, UUID botId) {
        Map<String, Object> data = loadData(reflexId, botId);
        return data != null ? getDouble(data, KEY_PROFICIENCY, 0.1) : 0.1;
    }

    public static boolean hasStatus(String reflexId, UUID botId, String status) {
        Map<String, Object> data = loadData(reflexId, botId);
        return data != null && status.equals(data.get(KEY_STATUS));
    }

    public static void setStatus(String reflexId, UUID botId, String status) {
        Map<String, Object> data = loadData(reflexId, botId);
        if (data == null) return;
        data.put(KEY_STATUS, status);
        saveData(reflexId, botId, data);
    }

    public static void setStatusAndSave(String reflexId, UUID botId, String status, Path path) {
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(path);
        if (data == null) return;
        data.put(KEY_STATUS, status);
        JsonUtil.writeToFileSafeAtomic(path, data);
    }

    public static double getConfidence(String reflexId, UUID botId) {
        Map<String, Object> data = loadData(reflexId, botId);
        if (data == null) return 0.5;
        double stw = getDouble(data, KEY_SHORT_TERM_WEIGHT, 0.5);
        double ltb = getDouble(data, KEY_LONG_TERM_BASELINE, 0.5);
        return Math.max(0, Math.min(1, stw * 0.7 + ltb * 0.3));
    }

    public static void resetReflexWeights(Path reflexPath) {
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(reflexPath);
        if (data == null) return;
        data.put(KEY_SHORT_TERM_WEIGHT, 0.5);
        data.put(KEY_LONG_TERM_BASELINE, 0.5);
        data.put(KEY_PROFICIENCY, 0.1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> atoms = (List<Map<String, Object>>) data.get(KEY_ATOMS);
        if (atoms != null) {
            for (Map<String, Object> atom : atoms) {
                atom.put(KEY_PROFICIENCY, 0.1);
            }
        }
        JsonUtil.writeToFileSafeAtomic(reflexPath, data);
    }

    public static void moveToArchived(String reflexId, UUID botId) {
        Path src = reflexPath(reflexId, botId);
        Path dest = archivedDir(botId).resolve(reflexId + FileUtil.JSON_EXT);
        try {
            java.nio.file.Files.createDirectories(dest.getParent());
            Map<String, Object> data = loadData(reflexId, botId);
            if (data != null) {
                data.put(KEY_STATUS, STATUS_DORMANT);
                data.put(KEY_PROFICIENCY, 0.1);
                JsonUtil.writeToFileSafeAtomic(dest, data);
                java.nio.file.Files.deleteIfExists(src);
                LOGGER.info("[ReflexIO] 归档反射: {} -> archived/", reflexId);
            }
        } catch (java.io.IOException e) {
            LOGGER.warn("[ReflexIO] 归档反射失败: {} — {}", reflexId, e.getMessage());
        }
    }

    public static boolean tryReactivate(String reflexId, UUID botId) {
        Path archivePath = archivedDir(botId).resolve(reflexId + FileUtil.JSON_EXT);
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(archivePath);
        if (data == null) return false;
        data.put(KEY_STATUS, STATUS_HEALTHY);
        data.put(KEY_PROFICIENCY, 0.1);
        data.put(KEY_SHORT_TERM_WEIGHT, 0.5);
        data.put(KEY_LONG_TERM_BASELINE, 0.5);
        saveData(reflexId, botId, data);
        try {
            java.nio.file.Files.deleteIfExists(archivePath);
        } catch (java.io.IOException e) {
            LOGGER.warn("[ReflexIO] 激活后清理归档文件失败: {}", e.getMessage());
        }
        LOGGER.info("[ReflexIO] 反射复活: {}", reflexId);
        return true;
    }
}
