package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.brainstem.bot.BotInstance;
import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChallengeTaskOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private static final int SHELTER_SCAN_RADIUS = 4;

    private final int deadlineDay;
    private final List<PhaseDef> phases;
    private final Map<UUID, BotProgress> progressMap = new ConcurrentHashMap<>();
    private boolean terminationTriggered = false;

    public record PhaseDef(int fromDay, int toDay, String name,
                           List<TaskDef> mainTasks, List<TaskDef> bonusTasks,
                           Map<String, Integer> requiredItemsForNextPhase) {}
    public record TaskDef(String id, String goal, List<String> dependsOn,
                          Map<String, Integer> itemChecks) {}
    public static class BotProgress {
        public final Set<String> completedTasks;
        public int mainPointer;
        public int bonusPointer;
        public boolean freeExplore;
        public boolean done;

        public BotProgress(Set<String> completedTasks, int mainPointer, int bonusPointer, boolean freeExplore, boolean done) {
            this.completedTasks = completedTasks;
            this.mainPointer = mainPointer;
            this.bonusPointer = bonusPointer;
            this.freeExplore = freeExplore;
            this.done = done;
        }
    }

    public ChallengeTaskOrchestrator(int deadlineDay, List<PhaseDef> phases) {
        this.deadlineDay = deadlineDay;
        this.phases = phases;
        this.terminationTriggered = false;
    }

    public static ChallengeTaskOrchestrator loadDefault() {
        Path path = FileUtil.getConfigDir().resolve("challenge_days.json");
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(path);
        if (data != null) return parse(data);
        LOGGER.warn("[ChallengeTaskOrchestrator] challenge_days.json 未找到，使用硬编码默认值");
        return createDefault();
    }

    @SuppressWarnings("unchecked")
    private static ChallengeTaskOrchestrator parse(Map<String, Object> data) {
        int deadline = ((Number) data.getOrDefault("deadlineDay", 15)).intValue();
        List<Map<String, Object>> rawPhases = (List<Map<String, Object>>) data.get("phases");
        if (rawPhases == null || rawPhases.isEmpty()) return createDefault();

        List<PhaseDef> phases = new ArrayList<>();
        for (Map<String, Object> p : rawPhases) {
            int from = ((Number) p.getOrDefault("fromDay", 1)).intValue();
            int to = ((Number) p.getOrDefault("toDay", 1)).intValue();
            String name = (String) p.getOrDefault("name", "未知阶段");

            List<TaskDef> main = parseTasks((List<Map<String, Object>>) p.get("mainTasks"));
            List<TaskDef> bonus = parseTasks((List<Map<String, Object>>) p.get("bonusTasks"));

            Map<String, Object> rawReq = (Map<String, Object>) p.get("requiredItemsForNextPhase");
            Map<String, Integer> req = new HashMap<>();
            if (rawReq != null) {
                for (var e : rawReq.entrySet())
                    req.put(e.getKey(), ((Number) e.getValue()).intValue());
            }

            phases.add(new PhaseDef(from, to, name, main, bonus, req));
        }

        return new ChallengeTaskOrchestrator(deadline, phases);
    }

    @SuppressWarnings("unchecked")
    private static List<TaskDef> parseTasks(List<Map<String, Object>> raw) {
        if (raw == null) return List.of();
        List<TaskDef> result = new ArrayList<>();
        for (Map<String, Object> t : raw) {
            String id = (String) t.get("id");
            String goal = (String) t.get("goal");
            List<String> deps = (List<String>) t.get("dependsOn");
            if (deps == null) deps = List.of();
            Map<String, Object> rawChecks = (Map<String, Object>) t.get("itemChecks");
            Map<String, Integer> checks = new HashMap<>();
            if (rawChecks != null) {
                for (var e : rawChecks.entrySet())
                    checks.put(e.getKey(), ((Number) e.getValue()).intValue());
            }
            result.add(new TaskDef(id != null ? id : "task_" + result.size(),
                    goal != null ? goal : "", deps, checks));
        }
        return result;
    }

    private static ChallengeTaskOrchestrator createDefault() {
        List<PhaseDef> phases = new ArrayList<>();

        Map<String, Integer> emptyMap = Map.of();

        // Phase 1: 石器时代 Day 1
        phases.add(new PhaseDef(1, 1, "石器时代", List.of(
                new TaskDef("gather_wood", "挖5个原木", List.of(), Map.of("logs", 5)),
                new TaskDef("craft_planks", "合成木板", List.of("gather_wood"), Map.of("planks", 4)),
                new TaskDef("craft_table", "合成工作台", List.of("craft_planks"), Map.of("crafting_table", 1)),
                new TaskDef("kill_sheep", "杀3只羊", List.of(), Map.of("wool", 3)),
                new TaskDef("craft_bed", "合成床", List.of("craft_planks", "kill_sheep"), Map.of("bed", 1)),
                new TaskDef("craft_chest", "合成箱子", List.of("craft_planks"), Map.of("chest", 1))
        ), List.of(
                new TaskDef("mine_coal", "挖10个煤矿", List.of(), Map.of("coal", 10)),
                new TaskDef("mine_copper", "挖5个铜矿", List.of(), Map.of("raw_copper", 5)),
                new TaskDef("craft_copper", "合成铜镐和铜剑", List.of("mine_copper"), Map.of("copper_ingot", 3))
        ), Map.of()));

        // Phase 2: 铁器时代 Day 2-3
        phases.add(new PhaseDef(2, 3, "铁器时代", List.of(
                new TaskDef("mine_cobble", "挖20个圆石", List.of(), Map.of("cobblestone", 20)),
                new TaskDef("craft_furnace", "合成熔炉", List.of("mine_cobble"), Map.of("furnace", 1)),
                new TaskDef("mine_coal2", "挖10个煤矿", List.of(), Map.of("coal", 10)),
                new TaskDef("craft_torches", "合成16个火把", List.of("mine_coal2"), Map.of("torch", 16)),
                new TaskDef("mine_iron", "挖10个铁矿", List.of(), Map.of("raw_iron", 10)),
                new TaskDef("smelt_iron", "烧铁锭", List.of("mine_iron", "craft_furnace"), Map.of("iron_ingot", 3)),
                new TaskDef("craft_iron_pick", "合成铁镐", List.of("smelt_iron"), Map.of("iron_pickaxe", 1)),
                new TaskDef("craft_iron_sword", "合成铁剑", List.of("smelt_iron"), Map.of("iron_sword", 1)),
                new TaskDef("craft_shield", "合成盾牌", List.of("smelt_iron"), Map.of("shield", 1))
        ), List.of(
                new TaskDef("farm_land", "耕地", List.of(), Map.of()),
                new TaskDef("plant_wheat", "种植小麦", List.of(), Map.of("wheat_seeds", 3))
        ), Map.of("iron_pickaxe", 1, "shield", 1)));

        // Phase 3: 建造与繁荣 Day 4-15
        phases.add(new PhaseDef(4, 15, "建造与繁荣", List.of(
                new TaskDef("clear_area", "清理20x20区域", List.of(), emptyMap),
                new TaskDef("build_house", "建造木屋", List.of("clear_area"), emptyMap),
                new TaskDef("light_up", "放置16个火把", List.of("build_house"), Map.of("torch", 16))
        ), List.of(
                new TaskDef("mine_diamond", "挖钻石", List.of(), Map.of("diamond", 3)),
                new TaskDef("craft_diamond_pick", "合成钻石镐", List.of("mine_diamond"), Map.of("diamond_pickaxe", 1)),
                new TaskDef("find_village", "找到村庄并交易", List.of(), Map.of("emerald", 1))
        ), emptyMap));

        return new ChallengeTaskOrchestrator(15, phases);
    }

    public void tick(BotInstance bot, int currentDay) {
        if (bot == null) return;
        UUID botId = bot.getBotId();
        ServerPlayerEntity entity = bot.asEntity();
        if (entity == null) return;

        BotProgress bp = progressMap.computeIfAbsent(botId, k -> new BotProgress(
                ConcurrentHashMap.newKeySet(), 0, 0, false, false));

        // ── Deadline check ──
        if (currentDay >= deadlineDay && !bp.done) {
            if (!terminationTriggered) {
                terminationTriggered = true;
                LOGGER.info("[ChallengeTaskOrchestrator] {} Day{} 截止——挑战结束", bot.getBotName(), currentDay);
                bp.completedTasks.clear();
                bp.done = true;
            }
            return;
        }

        // ── Don't interrupt active task ──
        if (bot.getTaskManager().getActiveTask() != null) return;

        // ── Night check — Day 1, no bed, nighttime → shelter override ──
        if (currentDay == 1 && entity.getWorld().isNight() && !hasItem(entity, Items.WHITE_BED, 1)) {
            if (!hasShelterNearby(entity)) {
                LOGGER.info("[ChallengeTaskOrchestrator] {} 第一晚没有床，注入避难任务", bot.getBotName());
                bot.getTaskManager().createTask("挖一个2x2x2避难洞并封口");
                return;
            }
        }

        // ── Find current phase ──
        PhaseDef phase = null;
        int phaseIndex = 0;
        for (int i = 0; i < phases.size(); i++) {
            PhaseDef p = phases.get(i);
            if (currentDay >= p.fromDay() && currentDay <= p.toDay()) {
                phase = p;
                phaseIndex = i;
                break;
            }
        }
        if (phase == null) return;

        // ── Free explore mode ──
        if (bp.freeExplore) {
            tickFreeExplore(bot, entity);
            return;
        }

        // ── Advance main tasks ──
        boolean assigned = advanceTasks(bot, entity, bp, phase.mainTasks(), true);
        if (assigned) return;

        // ── All main done → advance bonus ──
        boolean allMainDone = bp.mainPointer >= phase.mainTasks().size();
        if (allMainDone) {
            assigned = advanceTasks(bot, entity, bp, phase.bonusTasks(), false);
            if (assigned) return;

            boolean allBonusDone = bp.bonusPointer >= phase.bonusTasks().size();
            if (allBonusDone) {
                bp.freeExplore = true;
                LOGGER.info("[ChallengeTaskOrchestrator] {} 所有任务完成，进入自由探索模式", bot.getBotName());
                tickFreeExplore(bot, entity);
            } else {
                // Bonus tasks exist but can't advance (dependencies not met) → idle
                LOGGER.debug("[ChallengeTaskOrchestrator] {} 附加任务依赖未满足，等待", bot.getBotName());
            }
        }
    }

    private boolean advanceTasks(BotInstance bot, ServerPlayerEntity entity,
                                  BotProgress bp, List<TaskDef> tasks, boolean isMain) {
        int pointer = isMain ? bp.mainPointer : bp.bonusPointer;
        // Advance pointer past completed tasks
        while (pointer < tasks.size() && bp.completedTasks.contains(tasks.get(pointer).id())) {
            pointer++;
        }
        if (isMain) bp.mainPointer = pointer;
        else bp.bonusPointer = pointer;

        if (pointer >= tasks.size()) return false;

        TaskDef task = tasks.get(pointer);
        if (!allDepsSatisfied(task, bp)) return false;

        if (checkItems(entity, task.itemChecks())) {
            bp.completedTasks.add(task.id());
            LOGGER.info("[ChallengeTaskOrchestrator] {} 任务已完成(背包检出): {}", bot.getBotName(), task.goal());
            return false;
        }

        if (bot.getTaskManager().getActiveTask() == null) {
            LOGGER.info("[ChallengeTaskOrchestrator] {} 分配任务: {}", bot.getBotName(), task.goal());
            bot.getTaskManager().createTask(task.goal());
            return true;
        }
        return false;
    }

    private boolean allDepsSatisfied(TaskDef task, BotProgress bp) {
        for (String dep : task.dependsOn()) {
            if (!bp.completedTasks.contains(dep)) return false;
        }
        return true;
    }

    private boolean checkItems(ServerPlayerEntity entity, Map<String, Integer> checks) {
        if (checks == null || checks.isEmpty()) return false;
        for (var entry : checks.entrySet()) {
            String key = entry.getKey();
            int required = entry.getValue();
            if (required <= 0) continue;
            int count = countItemByKey(entity, key);
            if (count < required) return false;
        }
        return true;
    }

    private boolean hasItem(ServerPlayerEntity entity, Item item, int minCount) {
        int count = 0;
        for (ItemStack s : entity.getInventory().main) {
            if (!s.isEmpty() && s.isOf(item)) count += s.getCount();
            if (count >= minCount) return true;
        }
        return false;
    }

    private int countItemByKey(ServerPlayerEntity entity, String key) {
        return switch (key) {
            case "logs" -> countByTag(entity, ItemTags.LOGS);
            case "planks" -> countByTag(entity, ItemTags.PLANKS);
            case "wool" -> countByTag(entity, ItemTags.WOOL);
            case "cobblestone" -> countItem(entity, Items.COBBLESTONE);
            case "crafting_table" -> countItem(entity, Items.CRAFTING_TABLE);
            case "bed" -> countItem(entity, Items.WHITE_BED);
            case "chest" -> countItem(entity, Items.CHEST);
            case "coal" -> countItem(entity, Items.COAL);
            case "raw_copper" -> countItem(entity, Items.RAW_COPPER);
            case "raw_iron" -> countItem(entity, Items.RAW_IRON);
            case "copper_ingot" -> countItem(entity, Items.COPPER_INGOT);
            case "iron_ingot" -> countItem(entity, Items.IRON_INGOT);
            case "iron_pickaxe" -> countItem(entity, Items.IRON_PICKAXE);
            case "iron_sword" -> countItem(entity, Items.IRON_SWORD);
            case "shield" -> countItem(entity, Items.SHIELD);
            case "furnace" -> countItem(entity, Items.FURNACE);
            case "torch" -> countItem(entity, Items.TORCH);
            case "diamond" -> countItem(entity, Items.DIAMOND);
            case "diamond_pickaxe" -> countItem(entity, Items.DIAMOND_PICKAXE);
            case "emerald" -> countItem(entity, Items.EMERALD);
            case "wheat_seeds" -> countItem(entity, Items.WHEAT_SEEDS);
            default -> 0;
        };
    }

    private static int countItem(ServerPlayerEntity entity, Item item) {
        int count = 0;
        for (ItemStack s : entity.getInventory().main) {
            if (!s.isEmpty() && s.isOf(item)) count += s.getCount();
        }
        return count;
    }

    private static int countByTag(ServerPlayerEntity entity, TagKey<Item> tag) {
        int count = 0;
        for (ItemStack s : entity.getInventory().main) {
            if (!s.isEmpty() && s.isIn(tag)) count += s.getCount();
        }
        return count;
    }

    private void tickFreeExplore(BotInstance bot, ServerPlayerEntity entity) {
        if (bot.getTaskManager().getActiveTask() != null) return;
        LOGGER.debug("[ChallengeTaskOrchestrator] {} 自由探索模式", bot.getBotName());
        bot.getTaskManager().createExploreTask();
    }

    private boolean hasShelterNearby(ServerPlayerEntity entity) {
        BlockPos pos = entity.getBlockPos();
        for (int dx = -SHELTER_SCAN_RADIUS; dx <= SHELTER_SCAN_RADIUS; dx++) {
            for (int dz = -SHELTER_SCAN_RADIUS; dz <= SHELTER_SCAN_RADIUS; dz++) {
                BlockPos check = pos.add(dx, 0, dz);
                if (entity.getWorld().getBlockState(check).isFullCube(entity.getWorld(), check)
                        && entity.getWorld().getBlockState(check.up()).isFullCube(entity.getWorld(), check.up())) {
                    return true;
                }
            }
        }
        return false;
    }

    public void reset() {
        progressMap.clear();
        terminationTriggered = false;
    }

    public boolean isDone(UUID botId) {
        BotProgress bp = progressMap.get(botId);
        return bp != null && bp.done;
    }

    public boolean isFreeExplore(UUID botId) {
        BotProgress bp = progressMap.get(botId);
        return bp != null && bp.freeExplore;
    }

    public int getCompletedTaskCount(UUID botId) {
        BotProgress bp = progressMap.get(botId);
        return bp != null ? bp.completedTasks.size() : 0;
    }
}
