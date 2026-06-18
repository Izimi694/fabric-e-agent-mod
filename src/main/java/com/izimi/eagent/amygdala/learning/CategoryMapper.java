package com.izimi.eagent.amygdala.learning;

import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;
import com.izimi.eagent.util.TagResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

public class CategoryMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private static Map<String, String> displayNameCache = null;

    private static void reloadDisplayNames() {
        try {
            Path p = FileUtil.getConfigDir().resolve("category_display.json");
            Map<String, Object> data = JsonUtil.readMapFromFileSafe(p);
            if (data != null) {
                Object names = data.get("display_names");
                if (names instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> map = (Map<String, String>) names;
                    displayNameCache = new HashMap<>(map);
                    return;
                }
            }
        } catch (Exception e) {
            // 测试环境可能无 Fabric
        }
        displayNameCache = new HashMap<>();
    }

    public static String getCategory(String action, String target) {
        if (target == null) return action;
        String category = TagResolver.findCategory(target.toLowerCase());
        if (category != null) {
            return action + "_" + category;
        }
        return action;
    }

    public static String getCategoryDisplayName(String categoryId) {
        if (displayNameCache == null) reloadDisplayNames();
        String name = displayNameCache.get(categoryId);
        if (name != null) return name;

        return switch (categoryId) {
            case "dig_tree_log" -> "砍树";
            case "dig_ore" -> "挖矿";
            case "dig_crop" -> "收割作物";
            case "dig_common_block" -> "挖掘方块";
            case "attack_hostile" -> "打怪";
            case "attack_passive" -> "攻击动物";
            case "use_item" -> "使用物品";
            case "place_block" -> "放置方块";
            case "dig" -> "挖掘";
            case "attack" -> "攻击";
            default -> categoryId;
        };
    }

    public static String getCategoryName(String categoryId) {
        String display = getCategoryDisplayName(categoryId);
        return display.equals(categoryId) ? "其他行为" : display;
    }

    public static Map<String, List<String>> getCategoryRules() {
        return TagResolver.getCategoryPatterns();
    }
}
