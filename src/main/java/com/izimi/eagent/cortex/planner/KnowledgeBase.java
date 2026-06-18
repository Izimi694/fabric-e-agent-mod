package com.izimi.eagent.cortex.planner;

import com.izimi.eagent.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.izimi.eagent.util.JsonUtil;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class KnowledgeBase {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private final Map<String, Map<String, Object>> recipes;
    private final Map<String, Map<String, Object>> entities;
    private final Map<String, List<String>> itemUses;
    private final Map<String, Template> templates;
    private final Map<String, String> toolMap;
    private final Map<String, Object> gameRules;

    private final Map<String, Map<String, Object>> playstyleKnowledge;
    private String activePlaystyleId;

    public record Template(String name, Pattern pattern, List<TemplateStep> steps) {}

    public record TemplateStep(String skillId, String action, String target, int amount) {}

    private KnowledgeBase(Map<String, Map<String, Object>> recipes,
                          Map<String, Map<String, Object>> entities,
                          Map<String, List<String>> itemUses,
                          Map<String, Template> templates,
                          Map<String, String> toolMap,
                          Map<String, Object> gameRules) {
        this.recipes = recipes;
        this.entities = entities;
        this.itemUses = itemUses;
        this.templates = templates;
        this.toolMap = toolMap;
        this.gameRules = gameRules;
        this.playstyleKnowledge = new HashMap<>();
        this.activePlaystyleId = null;
    }

    /** Store per-playstyle knowledge data (loaded from pack) */
    public void setPlaystyleKnowledge(String playstyleId, Map<String, Object> data) {
        playstyleKnowledge.put(playstyleId, data != null ? data : Collections.emptyMap());
    }

    /** Switch active playstyle context; subsequent queries check playstyle knowledge first */
    public void switchPlaystyle(String playstyleId) {
        this.activePlaystyleId = playstyleId;
    }

    /** Clear active playstyle context, fall back to default knowledge */
    public void clearPlaystyle() {
        this.activePlaystyleId = null;
    }

    private Map<String, Object> activePlaystyleKnowledge() {
        if (activePlaystyleId != null) {
            Map<String, Object> pk = playstyleKnowledge.get(activePlaystyleId);
            if (pk != null && !pk.isEmpty()) return pk;
        }
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> getFromPlaystyleOrGlobal(String section, String key, Map<String, T> globalMap) {
        Map<String, Object> pk = activePlaystyleKnowledge();
        if (!pk.isEmpty() && pk.containsKey(section)) {
            Map<String, T> psMap = (Map<String, T>) pk.get(section);
            if (psMap != null && psMap.containsKey(key))
                return Optional.ofNullable(psMap.get(key));
        }
        return Optional.ofNullable(globalMap.get(key));
    }

    public Optional<Map<String, Object>> getRecipe(String itemId) {
        return getFromPlaystyleOrGlobal("recipes", itemId, recipes);
    }

    public Optional<Map<String, Object>> getEntity(String name) {
        return getFromPlaystyleOrGlobal("entities", name, entities);
    }

    public Optional<List<String>> getItemUses(String itemId) {
        return getFromPlaystyleOrGlobal("item_uses", itemId, itemUses);
    }

    public Optional<String> getTool(String blockId) {
        return getFromPlaystyleOrGlobal("tool_map", blockId, toolMap);
    }

    // ════════════════════════════════════════════════════════════════════
    //  Game Rules — 硬编码游戏规则查询
    // ════════════════════════════════════════════════════════════════════

    public int getGameRuleInt(String key, int fallback) {
        Object v = gameRules.get(key);
        if (v instanceof Number n) return n.intValue();
        return fallback;
    }

    public double getGameRuleDouble(String key, double fallback) {
        Object v = gameRules.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return fallback;
    }

    public boolean getGameRuleBool(String key, boolean fallback) {
        Object v = gameRules.get(key);
        if (v instanceof Boolean b) return b;
        return fallback;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getGameRuleMap(String key) {
        Object v = gameRules.get(key);
        if (v instanceof Map) return (Map<String, Object>) v;
        return Map.of();
    }

    public Collection<String> getGameRuleKeys() {
        return gameRules.keySet();
    }

    /** 获取高效挖矿模式定义（由 game_rules.mining_patterns 加载） */
    public Map<String, Object> getMiningPattern(String name) {
        Map<String, Object> patterns = getGameRuleMap("mining_patterns");
        Object v = patterns.get(name);
        if (v instanceof Map) return (Map<String, Object>) v;
        return Map.of();
    }

    public Optional<Template> matchTemplate(String input) {
        for (Template t : templates.values()) {
            if (t.pattern().matcher(input).find()) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    public Collection<Template> allTemplates() {
        return templates.values();
    }

    @SuppressWarnings("unchecked")
    public static KnowledgeBase load() {
        Path path = FileUtil.getConfigDir().resolve("knowledge_base.json");
        Map<String, Object> data = null;
        if (Files.exists(path)) {
            data = JsonUtil.readMapFromFileSafe(path);
        }
        if (data == null) {
            data = loadDefaultFromResource();
            JsonUtil.writeToFileSafeAtomic(path, data);
            LOGGER.info("[KnowledgeBase] 已从默认资源创建知识库: {}", path);
        }
        return fromMap(data);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadDefaultFromResource() {
        try (InputStream is = KnowledgeBase.class.getResourceAsStream("/defaults/knowledge_base.json")) {
            if (is != null) {
                String json = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                return JsonUtil.fromJson(json, Map.class);
            }
        } catch (Exception e) {
            LOGGER.warn("[KnowledgeBase] 加载默认资源失败，使用内置兜底", e);
        }
        return JsonUtil.fromJson(DEFAULT_JSON_FALLBACK, Map.class);
    }

    @SuppressWarnings("unchecked")
    private static KnowledgeBase fromMap(Map<String, Object> data) {
        Map<String, Map<String, Object>> recipes = new LinkedHashMap<>();
        Map<String, List<String>> itemUses = new LinkedHashMap<>();
        Map<String, Map<String, Object>> entities = new LinkedHashMap<>();
        Map<String, String> toolMap = new LinkedHashMap<>();
        Map<String, Object> gameRules = new LinkedHashMap<>();

        if (data.containsKey("recipes")) {
            recipes.putAll((Map<String, Map<String, Object>>) data.get("recipes"));
        }
        if (data.containsKey("entities")) {
            entities.putAll((Map<String, Map<String, Object>>) data.get("entities"));
        }
        if (data.containsKey("item_uses")) {
            Map<String, List<String>> raw = (Map<String, List<String>>) data.get("item_uses");
            itemUses.putAll(raw);
        }
        if (data.containsKey("tool_map")) {
            Map<String, String> raw = (Map<String, String>) data.get("tool_map");
            toolMap.putAll(raw);
        }
        if (data.containsKey("game_rules")) {
            gameRules.putAll((Map<String, Object>) data.get("game_rules"));
        }
        Map<String, Template> templates = parseTemplates(data);

        return new KnowledgeBase(recipes, entities, itemUses, templates, toolMap, gameRules);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Template> parseTemplates(Map<String, Object> data) {
        Map<String, Template> templates = new LinkedHashMap<>();
        if (!data.containsKey("templates")) return templates;
        Map<String, Map<String, Object>> raw = (Map<String, Map<String, Object>>) data.get("templates");
        for (var e : raw.entrySet()) {
            String name = e.getKey();
            Map<String, Object> tm = e.getValue();
            String pattern = (String) tm.get("pattern");
            List<Map<String, Object>> stepsRaw = (List<Map<String, Object>>) tm.get("steps");
            List<TemplateStep> steps = new ArrayList<>();
            if (stepsRaw != null) {
                for (Map<String, Object> sm : stepsRaw) {
                    steps.add(new TemplateStep(
                            (String) sm.get("skillId"),
                            (String) sm.get("action"),
                            (String) sm.getOrDefault("target", ""),
                            sm.containsKey("amount") ? ((Number) sm.get("amount")).intValue() : 1
                    ));
                }
            }
            templates.put(name, new Template(name, Pattern.compile(pattern), steps));
        }
        return templates;
    }

    public List<String> allKeys() {
        List<String> keys = new ArrayList<>();
        keys.addAll(recipes.keySet());
        keys.addAll(entities.keySet());
        keys.addAll(itemUses.keySet());
        keys.addAll(templates.keySet());
        return keys;
    }

    private static final String DEFAULT_JSON_FALLBACK = "{\"templates\":{\"mine\":{\"pattern\":\"挖(\\\\\\\\d+)个(.+)\",\"steps\":[{\"skillId\":\"equip\",\"action\":\"equipItem\",\"target\":\"{tool}\"},{\"skillId\":\"move\",\"action\":\"moveTo\",\"target\":\"{target}\"},{\"skillId\":\"dig\",\"action\":\"dig\",\"target\":\"{target}\"},{\"skillId\":\"collect\",\"action\":\"collectItem\",\"target\":\"{target}\"}]},\"craft\":{\"pattern\":\"合成(.+)\",\"steps\":[{\"skillId\":\"open_craft\",\"action\":\"openBlock\",\"target\":\"crafting_table\"},{\"skillId\":\"craft\",\"action\":\"craft\",\"target\":\"{target}\"}]},\"smelt\":{\"pattern\":\"(?:烧|冶炼)(.+)\",\"steps\":[{\"skillId\":\"open_furnace\",\"action\":\"openBlock\",\"target\":\"furnace\"},{\"skillId\":\"smelt\",\"action\":\"clickSlot\",\"target\":\"{target}\"}]},\"attack\":{\"pattern\":\"(?:打|杀|攻击)(.+)\",\"steps\":[{\"skillId\":\"equip\",\"action\":\"equipItem\",\"target\":\"{weapon}\"},{\"skillId\":\"move\",\"action\":\"moveTo\",\"target\":\"{target}\"},{\"skillId\":\"attack\",\"action\":\"attack\",\"target\":\"{target}\"}]},\"get\":{\"pattern\":\"(?:拿|取|弄|搞)(\\\\\\\\d+)?个?(.+)\",\"steps\":[{\"skillId\":\"move\",\"action\":\"moveTo\",\"target\":\"{target}\"},{\"skillId\":\"collect\",\"action\":\"collectItem\",\"target\":\"{target}\"}]},\"build\":{\"pattern\":\"(?:建|盖|搭|造)(.+)\",\"steps\":[{\"skillId\":\"get_blocks\",\"action\":\"collectItem\",\"target\":\"{material}\"},{\"skillId\":\"place\",\"action\":\"placeBlock\",\"target\":\"{target}\"}]}},\"recipes\":{\"crafting_table\":{\"ingredients\":\"4 planks\",\"tool\":\"none\"},\"stick\":{\"ingredients\":\"2 planks\",\"tool\":\"none\"},\"planks\":{\"ingredients\":\"1 log\",\"tool\":\"none\"},\"wooden_pickaxe\":{\"ingredients\":\"3 planks + 2 sticks\",\"tool\":\"crafting_table\"},\"stone_pickaxe\":{\"ingredients\":\"3 cobblestone + 2 sticks\",\"tool\":\"crafting_table\"},\"iron_pickaxe\":{\"ingredients\":\"3 iron_ingot + 2 sticks\",\"tool\":\"crafting_table\"},\"diamond_pickaxe\":{\"ingredients\":\"3 diamond + 2 sticks\",\"tool\":\"crafting_table\"},\"wooden_sword\":{\"ingredients\":\"2 planks + 1 stick\",\"tool\":\"crafting_table\"},\"stone_sword\":{\"ingredients\":\"2 cobblestone + 1 stick\",\"tool\":\"crafting_table\"},\"iron_sword\":{\"ingredients\":\"2 iron_ingot + 1 stick\",\"tool\":\"crafting_table\"},\"wooden_axe\":{\"ingredients\":\"3 planks + 2 sticks\",\"tool\":\"crafting_table\"},\"stone_axe\":{\"ingredients\":\"3 cobblestone + 2 sticks\",\"tool\":\"crafting_table\"},\"wooden_shovel\":{\"ingredients\":\"1 planks + 2 sticks\",\"tool\":\"crafting_table\"},\"stone_shovel\":{\"ingredients\":\"1 cobblestone + 2 sticks\",\"tool\":\"crafting_table\"},\"furnace\":{\"ingredients\":\"8 cobblestone\",\"tool\":\"crafting_table\"},\"chest\":{\"ingredients\":\"8 planks\",\"tool\":\"crafting_table\"},\"bed\":{\"ingredients\":\"3 wool + 3 planks\",\"tool\":\"crafting_table\"},\"torch\":{\"ingredients\":\"1 coal + 1 stick\",\"tool\":\"none\"}},\"entities\":{\"zombie\":{\"hostile\":true,\"attackable\":true},\"skeleton\":{\"hostile\":true,\"attackable\":true},\"spider\":{\"hostile\":true,\"attackable\":true},\"creeper\":{\"hostile\":true,\"attackable\":false,\"warning\":\"会爆炸，保持距离\"},\"enderman\":{\"hostile\":true,\"attackable\":true,\"warning\":\"不要看它的眼睛\"},\"witch\":{\"hostile\":true,\"attackable\":true},\"slime\":{\"hostile\":true,\"attackable\":true}},\"item_uses\":{\"diamond\":[\"diamond_pickaxe\",\"diamond_sword\",\"diamond_axe\",\"diamond_shovel\",\"enchantment_table\"],\"iron_ingot\":[\"iron_pickaxe\",\"iron_sword\",\"iron_axe\",\"iron_shovel\",\"iron_helmet\",\"iron_chestplate\",\"iron_leggings\",\"iron_boots\",\"flint_and_steel\",\"bucket\",\"shears\"],\"gold_ingot\":[\"golden_pickaxe\",\"golden_sword\",\"golden_apple\",\"powered_rail\"],\"coal\":[\"torch\",\"campfire\"],\"redstone\":[\"redstone_dust\",\"piston\",\"observer\",\"repeater\",\"comparator\"],\"stick\":[\"torch\",\"tool_handle\",\"fence\",\"ladder\",\"sign\"],\"planks\":[\"crafting_table\",\"stick\",\"chest\",\"door\",\"trapdoor\",\"boat\"],\"cobblestone\":[\"stone_pickaxe\",\"stone_sword\",\"furnace\",\"stone_bricks\"],\"log\":[\"planks\",\"charcoal\"]},\"tool_map\":{\"iron_ore\":\"stone_pickaxe\",\"diamond_ore\":\"iron_pickaxe\",\"gold_ore\":\"iron_pickaxe\",\"stone\":\"wooden_pickaxe\",\"log\":\"wooden_axe\",\"coal_ore\":\"wooden_pickaxe\"},\"game_rules\":{\"monster_spawn_light_max\":7,\"safe_light_level\":8,\"torch_light_level\":14,\"shelter_enclosed_walls_min\":3,\"shelter_roof_required\":true,\"food_tracker\":{\"bread\":5,\"cooked_beef\":8,\"apple\":4,\"golden_apple\":20},\"tool_efficiency\":{\"wooden_pickaxe\":{\"base_speed\":2.0,\"tier\":0},\"stone_pickaxe\":{\"base_speed\":4.0,\"tier\":1},\"iron_pickaxe\":{\"base_speed\":6.0,\"tier\":2},\"diamond_pickaxe\":{\"base_speed\":8.0,\"tier\":3}},\"ore_mining_levels\":{\"coal_ore\":0,\"copper_ore\":0,\"iron_ore\":1,\"diamond_ore\":2,\"obsidian\":3},\"mining_patterns\":{\"strip_mining\":{\"description\":\"在 Y=-59 水平直线挖 2x1 隧道，每 3 格分岔\",\"optimal_y_levels\":[-59,11],\"tunnel_spacing\":3,\"tunnel_height\":2,\"efficiency_factor\":0.8},\"branch_mining\":{\"description\":\"主隧道两侧每 3 格挖分支隧道 20 格深\",\"branch_spacing\":3,\"branch_length\":20,\"efficiency_factor\":0.9},\"cave_mining\":{\"description\":\"探索天然洞穴，优先检查裸露矿石\",\"efficiency_factor\":1.5}}}}";
}
