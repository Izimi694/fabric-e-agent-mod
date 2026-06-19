package com.izimi.eagent.util;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

public class TagResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private static Map<String, String> aliasToId = new HashMap<>();
    private static boolean loaded = false;

    private static final Map<String, List<String>> CATEGORY_PATTERNS = new LinkedHashMap<>();

    static {
        CATEGORY_PATTERNS.put("tree_log",
                List.of("_log", "_wood", "stem", "hyphae", "bamboo_block"));
        CATEGORY_PATTERNS.put("ore",
                List.of("_ore", "ancient_debris", "nether_gold"));
        CATEGORY_PATTERNS.put("crop",
                List.of("wheat", "carrot", "potato", "beetroot", "melon", "pumpkin",
                        "sugar_cane", "cocoa", "nether_wart", "kelp"));
        CATEGORY_PATTERNS.put("common_block",
                List.of("_planks", "stone", "cobblestone", "dirt", "sand", "gravel",
                        "netherrack", "end_stone", "granite", "diorite", "andesite",
                        "deepslate", "tuff", "calcite"));
        CATEGORY_PATTERNS.put("hostile",
                List.of("zombie", "skeleton", "creeper", "spider", "enderman",
                        "witch", "blaze", "ghast", "wither", "slime", "magma_cube",
                        "piglin", "hoglin", "pillager", "vindicator", "drowned"));
        CATEGORY_PATTERNS.put("passive",
                List.of("villager", "sheep", "cow", "pig", "chicken", "horse",
                        "donkey", "llama", "rabbit", "fox", "wolf", "cat",
                        "panda", "turtle", "dolphin"));
    }

    public static Map<String, List<String>> getCategoryPatterns() {
        return Collections.unmodifiableMap(CATEGORY_PATTERNS);
    }

    public static void reload() {
        try {
            Path p = FileUtil.getConfigDir().resolve("entity_aliases.json");
            Map<String, Object> data = JsonUtil.readMapFromFileSafe(p);
            if (data != null) {
                Object aliases = data.get("aliases");
                if (aliases instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> map = (Map<String, String>) aliases;
                    aliasToId = new HashMap<>(map);
                    loaded = true;
                    LOGGER.debug("[TagResolver] 已加载 {} 个别名", aliasToId.size());
                    return;
                }
            }
        } catch (Exception e) {
            // 测试环境可能无 Fabric
        }
        aliasToId = new HashMap<>();
        loaded = true;
    }

    public static String resolveId(String alias) {
        if (!loaded) reload();
        if (alias == null) return null;
        String lower = alias.toLowerCase().trim();
        String direct = aliasToId.get(lower);
        if (direct != null) return direct;

        for (var entry : aliasToId.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    public static boolean isInCategory(String blockId, String category) {
        String lower = blockId.toLowerCase();
        var patterns = CATEGORY_PATTERNS.get(category);
        if (patterns == null) return false;
        for (String pattern : patterns) {
            if (lower.contains(pattern.toLowerCase())) return true;
        }
        return false;
    }

    public static String findCategory(String itemOrBlockId) {
        if (itemOrBlockId == null) return null;
        String lower = itemOrBlockId.toLowerCase();
        for (var entry : CATEGORY_PATTERNS.entrySet()) {
            for (String pattern : entry.getValue()) {
                if (lower.contains(pattern.toLowerCase())) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    public static List<String> expandCategory(String categoryName) {
        if (categoryName == null) return List.of();
        List<String> patterns = CATEGORY_PATTERNS.get(categoryName.toLowerCase());
        return patterns != null ? List.copyOf(patterns) : List.of();
    }

    public static boolean sharesCategory(String idA, String idB) {
        if (idA == null || idB == null) return false;
        String catA = findCategory(idA);
        String catB = findCategory(idB);
        return catA != null && catA.equals(catB);
    }

    public static boolean isInItemTag(Identifier itemId, TagKey<Item> tag) {
        if (itemId == null || tag == null) return false;
        var item = Registries.ITEM.get(itemId);
        if (item == null) return false;
        return item.getRegistryEntry().isIn(tag);
    }

    public static boolean isInBlockTag(Identifier blockId, TagKey<Block> tag) {
        if (blockId == null || tag == null) return false;
        var block = Registries.BLOCK.get(blockId);
        if (block == null) return false;
        return block.getRegistryEntry().isIn(tag);
    }

    public static boolean isInEntityTag(Identifier entityId, TagKey<EntityType<?>> tag) {
        if (entityId == null || tag == null) return false;
        var type = Registries.ENTITY_TYPE.get(entityId);
        if (type == null) return false;
        return type.getRegistryEntry().isIn(tag);
    }

    public static boolean hasConcreteNoun(String text) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase();

        if (!loaded) reload();
        for (String alias : aliasToId.keySet()) {
            if (lower.contains(alias)) return true;
        }

        String[] knownNouns = {"矿", "石", "铁", "金", "钻", "木", "剑", "镐", "斧",
            "锹", "锄", "弓", "箭", "床", "箱", "炉", "门", "栅", "药", "食", "肉",
            "草", "花", "树", "怪", "猪", "牛", "羊", "鸡", "鱼", "龙", "人", "家",
            "火", "水", "地", "天", "空", "房", "桌", "灯", "梯", "墙"};
        for (String noun : knownNouns) {
            if (lower.contains(noun)) return true;
        }

        return false;
    }

    public static String findTargetByAlias(String alias) {
        String resolved = resolveId(alias);
        if (resolved != null) return resolved;
        if (alias != null && alias.contains("_")) {
            return alias.toLowerCase();
        }
        return alias;
    }
}
