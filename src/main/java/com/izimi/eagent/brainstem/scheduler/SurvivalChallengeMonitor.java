package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.brainstem.bot.BotInstance;
import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 生存挑战监控器 — 每日快照输出.
 * <p>
 * 控制台: 每游戏日一行紧凑对比 (logCompactToConsole).
 * 日志文件: 同一行 + 详细数据 (logDetailedToFile, 通过 LOGGER.info).
 * <p>
 * 钩子: BotInstance.tick() 中记死亡, MetaScheduler.executeCortexLLM() 中记 LLM 调用.
 */
public class SurvivalChallengeMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    // ── 静态计数器 ──
    private static final Map<UUID, AtomicInteger> llmCounters = new ConcurrentHashMap<>();
    private static final Map<UUID, AtomicInteger> deathCounters = new ConcurrentHashMap<>();

    // ── 每日一次锁 ──
    private static int lastDayPrinted = -1;
    private static final Object printLock = new Object();

    // ── 挑战生命周期 ──
    private static int challengeEndDay = -1;  // -1 = 无限

    // ── 里程碑追踪器 ──
    private static ChallengeMilestoneTracker milestoneTracker;

    // ── 任务管线编排器 ──
    private static ChallengeTaskOrchestrator taskOrchestrator;

    private SurvivalChallengeMonitor() {}

    /** 注入里程碑追踪器（由 AICommand 在挑战开始时调用） */
    public static void setMilestoneTracker(ChallengeMilestoneTracker tracker) {
        milestoneTracker = tracker;
    }

    /** 注入任务管线编排器（由 AICommand 在挑战开始时调用） */
    public static void setTaskOrchestrator(ChallengeTaskOrchestrator orchestrator) {
        taskOrchestrator = orchestrator;
    }

    public static ChallengeTaskOrchestrator getTaskOrchestrator() {
        return taskOrchestrator;
    }

    // ════════════════════════════════════════════════════════════════════
    //  钩子 API
    // ════════════════════════════════════════════════════════════════════

    public static void recordLLMCall(UUID botId) {
        llmCounters.computeIfAbsent(botId, k -> new AtomicInteger()).incrementAndGet();
    }

    public static void recordDeath(UUID botId) {
        deathCounters.computeIfAbsent(botId, k -> new AtomicInteger()).incrementAndGet();
    }

    /** reset 计数器 (挑战开始时调用) */
    public static void reset() {
        llmCounters.clear();
        deathCounters.clear();
        lastDayPrinted = -1;
        challengeEndDay = -1;
        if (milestoneTracker != null) milestoneTracker.reset();
        if (taskOrchestrator != null) taskOrchestrator.reset();
    }

    /** 启动挑战 (days <= 0 = 无限) */
    public static void startChallenge(int days) {
        reset();
        challengeEndDay = days > 0 ? days : -1;
        LOGGER.info("[挑战] 挑战已启动{}", days > 0 ? " (" + days + "天)" : "");
    }

    /** 挑战是否活跃 */
    public static boolean isActive() {
        return lastDayPrinted >= 0 || challengeEndDay != -1;
    }

    public static int getDeathCount(UUID botId) {
        return deathCounters.getOrDefault(botId, new AtomicInteger()).get();
    }

    public static int getLLMCount(UUID botId) {
        return llmCounters.getOrDefault(botId, new AtomicInteger()).get();
    }

    // ════════════════════════════════════════════════════════════════════
    //  每日快照
    // ════════════════════════════════════════════════════════════════════

    /**
     * 在 bot 的 tickCounter 到达游戏日边界时调用.
     * 线程安全: 同步锁保证每 day 只打印一次.
     */
    public static void printDailySnapshot(int day, List<BotInstance> bots) {
        int endDay;
        boolean shouldEnd;
        synchronized (printLock) {
            if (day <= lastDayPrinted) return;
            lastDayPrinted = day;
            endDay = challengeEndDay;
        }
        shouldEnd = endDay > 0 && day >= endDay;

        // Tick orchestrator on day boundary for all bots
        if (taskOrchestrator != null) {
            for (BotInstance b : bots) {
                if (b != null) taskOrchestrator.tick(b, day);
            }
        }

        // ── 紧凑对比行 (控制台可见) ──
        String compact = bots.stream()
                .filter(Objects::nonNull)
                .map(b -> compactLine(day, b))
                .collect(Collectors.joining("  "));
        LOGGER.info("[挑战] Day {}: {}", day, compact);

        // ── 详细行 (日志文件) ──
        for (BotInstance b : bots) {
            if (b == null) continue;
            LOGGER.info("[挑战] Day {} | {} 详细: {}",
                    day, b.getBotName(), detailedLine(b));
        }

        // ── 里程碑检查 ──
        if (milestoneTracker != null) {
            for (BotInstance b : bots) {
                if (b == null) continue;
                ServerPlayerEntity entity = b.asEntity();
                if (entity == null) continue;
                InvSummary inv = summarizeInventory(entity);
                var result = milestoneTracker.checkDay(day, b.getBotId(), entity, inv);
                if (!result.passed().isEmpty()) {
                    LOGGER.info("[挑战] {} Day{} ✅ 达标: {} (+{}分)",
                            b.getBotName(), day,
                            String.join(", ", result.passed()), result.newScore());
                }
                if (!result.failed().isEmpty()) {
                    LOGGER.warn("[挑战] {} Day{} ❌ 未达标: {}",
                            b.getBotName(), day,
                            String.join(", ", result.failed()));
                }
            }
        }

        // ── 挑战结束自动报告 ──
        if (shouldEnd) {
            printFinalReport(bots);
            challengeEndDay = -1;  // 防止重复触发
            LOGGER.info("[挑战] 挑战已结束 (Day {})", day);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    //  格式化
    // ════════════════════════════════════════════════════════════════════

    private static String compactLine(int day, BotInstance bot) {
        ServerPlayerEntity entity = bot.asEntity();
        if (entity == null) return bot.getBotName() + ":[离线]";

        int hp = (int) entity.getHealth();
        int food = entity.getHungerManager().getFoodLevel();
        int deaths = deathCounters.getOrDefault(bot.getBotId(), new AtomicInteger()).get();
        int llm = llmCounters.getOrDefault(bot.getBotId(), new AtomicInteger()).get();
        InvSummary inv = summarizeInventory(entity);

        // 里程碑达标符号
        int milestonesPassed = 0;
        if (milestoneTracker != null) {
            milestonesPassed = milestoneTracker.getPassedCount(bot.getBotId());
        }
        String star = milestonesPassed >= 3 ? "★" : milestonesPassed >= 1 ? "☆" : "";

        return String.format("%s[HP:%d 饿:%d 🪓:%d⚔:%d🛡:%d 🔥:%d 🛏:%d ⛏:%d 💎:%d 💀:%d 🤖:%d]%s",
                bot.getBotName(), hp, food,
                inv.totalPickaxes(), inv.totalSwords(), inv.shield,
                inv.torch, inv.bed, inv.ironIngot, inv.diamond, deaths, llm, star);
    }

    private static String detailedLine(BotInstance bot) {
        ServerPlayerEntity entity = bot.asEntity();
        if (entity == null) return "离线";

        BlockPos pos = entity.getBlockPos();
        int hp = (int) entity.getHealth();
        int food = entity.getHungerManager().getFoodLevel();
        int deaths = deathCounters.getOrDefault(bot.getBotId(), new AtomicInteger()).get();
        int llm = llmCounters.getOrDefault(bot.getBotId(), new AtomicInteger()).get();
        boolean legacy = bot.isLegacyScoring();
        InvSummary inv = summarizeInventory(entity);

        return String.format("pos=[%d,%d,%d] HP=%d Hung=%d legacy=%s " +
                        "镐:木=%d石=%d铁=%d钻=%d 剑:木=%d石=%d铁=%d钻=%d 盾=%d弓=%d " +
                        "矿:铁锭=%d钻石=%d铜=%d煤=%d 建筑:工作台=%d熔炉=%d箱子=%d床=%d " +
                        "食物:%d 死亡=%d LLM=%d",
                pos.getX(), pos.getY(), pos.getZ(), hp, food, legacy,
                inv.woodPick, inv.stonePick, inv.ironPick, inv.diamondPick,
                inv.woodSword, inv.stoneSword, inv.ironSword, inv.diamondSword,
                inv.shield, inv.bow,
                inv.ironIngot, inv.diamond, inv.copperIngot, inv.coal,
                inv.craftingTable, inv.furnace, inv.chest, inv.bed,
                inv.totalFood(), deaths, llm);
    }

    // ════════════════════════════════════════════════════════════════════
    //  背包摘要
    // ════════════════════════════════════════════════════════════════════

    private static InvSummary summarizeInventory(ServerPlayerEntity entity) {
        InvSummary s = new InvSummary();
        for (ItemStack stack : entity.getInventory().main) {
            if (stack.isEmpty()) continue;
            int cnt = stack.getCount();

            // 工具
            if (stack.isOf(Items.WOODEN_PICKAXE))   s.woodPick += cnt;
            if (stack.isOf(Items.STONE_PICKAXE))    s.stonePick += cnt;
            if (stack.isOf(Items.IRON_PICKAXE))     s.ironPick += cnt;
            if (stack.isOf(Items.DIAMOND_PICKAXE))  s.diamondPick += cnt;
            if (stack.isOf(Items.WOODEN_SWORD))     s.woodSword += cnt;
            if (stack.isOf(Items.STONE_SWORD))      s.stoneSword += cnt;
            if (stack.isOf(Items.IRON_SWORD))       s.ironSword += cnt;
            if (stack.isOf(Items.DIAMOND_SWORD))    s.diamondSword += cnt;
            if (stack.isOf(Items.SHIELD))           s.shield += cnt;
            if (stack.isOf(Items.BOW))              s.bow += cnt;

            // 矿物
            if (stack.isOf(Items.COAL))             s.coal += cnt;
            if (stack.isOf(Items.RAW_COPPER))       s.copperRaw += cnt;
            if (stack.isOf(Items.COPPER_INGOT))     s.copperIngot += cnt;
            if (stack.isOf(Items.RAW_IRON))         s.ironRaw += cnt;
            if (stack.isOf(Items.IRON_INGOT))       s.ironIngot += cnt;
            if (stack.isOf(Items.DIAMOND))          s.diamond += cnt;
            if (stack.isOf(Items.EMERALD))          s.emerald += cnt;

            // 建筑/生存
            if (stack.isOf(Items.CRAFTING_TABLE))   s.craftingTable += cnt;
            if (stack.isOf(Items.WHITE_BED))         s.bed += cnt;
            if (stack.isOf(Items.FURNACE))          s.furnace += cnt;
            if (stack.isOf(Items.CHEST))            s.chest += cnt;
            if (stack.isOf(Items.TORCH))            s.torch += cnt;
            if (stack.isOf(Items.CAMPFIRE))         s.campfire += cnt;

            // 食物
            if (stack.isOf(Items.BREAD))            s.bread += cnt;
            if (stack.isOf(Items.COOKED_BEEF))      s.cookedBeef += cnt;
            if (stack.isOf(Items.APPLE))            s.apple += cnt;
            if (stack.isOf(Items.GOLDEN_APPLE))     s.goldenApple += cnt;
        }
        return s;
    }

    public static class InvSummary {
        // 工具 (7)
        public int woodPick, stonePick, ironPick, diamondPick;
        public int woodSword, stoneSword, ironSword, diamondSword;
        public int shield, bow;

        // 矿物 (7)
        public int coal, copperRaw, copperIngot, ironRaw, ironIngot, diamond, emerald;

        // 建筑/生存 (6)
        public int craftingTable, bed, furnace, chest, torch, campfire;

        // 食物 (4)
        public int bread, cookedBeef, apple, goldenApple;

        // ── 配置文件字段映射（延迟加载，避免测试时依赖 Fabric）──
        private static Map<String, String> fieldMap = null;
        private static boolean fieldMapLoaded = false;

        private static void ensureFieldMap() {
            if (fieldMapLoaded) return;
            fieldMapLoaded = true;
            try {
                Path p = FileUtil.getConfigDir().resolve("challenge_items.json");
                Map<String, Object> data = JsonUtil.readMapFromFileSafe(p);
                if (data != null && data.get("item_fields") instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> map = (Map<String, String>) data.get("item_fields");
                    fieldMap = new HashMap<>(map);
                    return;
                }
            } catch (Exception e) {
                // 测试环境可能无 Fabric，忽略
            }
            fieldMap = new HashMap<>();
        }

        /** 按 itemId 查询数量（来自外部 JSON 字段映射） */
        public int count(String itemId) {
            ensureFieldMap();
            if (fieldMap != null && fieldMap.containsKey(itemId)) {
                return getFieldValue(fieldMap.get(itemId));
            }
            return countLegacy(itemId);
        }

        private int getFieldValue(String fieldName) {
            return switch (fieldName) {
                case "woodPick" -> woodPick;
                case "stonePick" -> stonePick;
                case "ironPick" -> ironPick;
                case "diamondPick" -> diamondPick;
                case "woodSword" -> woodSword;
                case "stoneSword" -> stoneSword;
                case "ironSword" -> ironSword;
                case "diamondSword" -> diamondSword;
                case "shield" -> shield;
                case "bow" -> bow;
                case "coal" -> coal;
                case "copperIngot" -> copperIngot;
                case "ironIngot" -> ironIngot;
                case "diamond" -> diamond;
                case "emerald" -> emerald;
                case "craftingTable" -> craftingTable;
                case "bed" -> bed;
                case "furnace" -> furnace;
                case "chest" -> chest;
                case "torch" -> torch;
                case "campfire" -> campfire;
                case "bread" -> bread;
                case "cookedBeef" -> cookedBeef;
                case "apple" -> apple;
                case "goldenApple" -> goldenApple;
                default -> 0;
            };
        }

        private int countLegacy(String itemId) {
            return switch (itemId) {
                case "wooden_pickaxe" -> woodPick;
                case "stone_pickaxe" -> stonePick;
                case "iron_pickaxe" -> ironPick;
                case "diamond_pickaxe" -> diamondPick;
                case "wooden_sword" -> woodSword;
                case "stone_sword" -> stoneSword;
                case "iron_sword" -> ironSword;
                case "diamond_sword" -> diamondSword;
                case "shield" -> shield;
                case "bow" -> bow;
                case "coal" -> coal;
                case "copper_ingot" -> copperIngot;
                case "raw_iron" -> ironRaw;
                case "iron_ingot" -> ironIngot;
                case "diamond" -> diamond;
                case "emerald" -> emerald;
                case "crafting_table" -> craftingTable;
                case "bed" -> bed;
                case "furnace" -> furnace;
                case "chest" -> chest;
                case "torch" -> torch;
                case "campfire" -> campfire;
                case "bread" -> bread;
                case "cooked_beef" -> cookedBeef;
                case "apple" -> apple;
                case "golden_apple" -> goldenApple;
                default -> 0;
            };
        }

        // ── 辅助方法 ──
        public int totalPickaxes() { return woodPick + stonePick + ironPick + diamondPick; }
        public boolean hasAnyPickaxe() { return totalPickaxes() > 0; }
        public int totalSwords() { return woodSword + stoneSword + ironSword + diamondSword; }
        public int totalIronSet() { return ironPick + ironSword + shield; }
        public boolean hasFullIronSet() { return ironPick >= 1 && ironSword >= 1 && shield >= 1; }
        public int totalFood() { return bread + cookedBeef + apple + goldenApple; }
    }

    // ════════════════════════════════════════════════════════════════════
    //  最终报告
    // ════════════════════════════════════════════════════════════════════

    /** 手动终止挑战 (由 /ai challenge stop 调用) */
    public static void stopChallenge(List<BotInstance> bots) {
        printFinalReport(bots);
        reset();
        LOGGER.info("[挑战] 挑战已手动终止");
    }

    /** 打印最终对比报告 */
    private static void printFinalReport(List<BotInstance> bots) {
        LOGGER.info("");
        LOGGER.info("═══════════════════════════════════════════════");
        LOGGER.info("  [挑战] 最终报告");
        LOGGER.info("═══════════════════════════════════════════════");
        for (BotInstance b : bots) {
            if (b == null) continue;
            ServerPlayerEntity entity = b.asEntity();
            if (entity == null) {
                LOGGER.info("  {}: [离线]", b.getBotName());
                continue;
            }
            InvSummary inv = summarizeInventory(entity);
            int deaths = deathCounters.getOrDefault(b.getBotId(), new AtomicInteger()).get();
            int llm = llmCounters.getOrDefault(b.getBotId(), new AtomicInteger()).get();
            int resourceScore = inv.ironIngot * 2 + inv.diamond * 20 +
                                inv.copperIngot * 1 + inv.coal * 1 + inv.emerald * 30;
            int deathPenalty = deaths * 200;
            int llmBonus = Math.max(0, 50 - llm);
            int score = Math.max(0, resourceScore + llmBonus - deathPenalty);

            LOGGER.info("  {} (legacy={})", b.getBotName(), b.isLegacyScoring());
            LOGGER.info("    HP={}/{} 饿={}", (int) entity.getHealth(), (int) entity.getMaxHealth(),
                    entity.getHungerManager().getFoodLevel());
            LOGGER.info("    工具: 木镐={} 石镐={} 铁镐={} 钻镐={} 盾={} 弓={}",
                    inv.woodPick, inv.stonePick, inv.ironPick, inv.diamondPick, inv.shield, inv.bow);
            LOGGER.info("    资源: 铁={} 钻={} 铜={} 煤={} 绿宝石={}",
                    inv.ironIngot, inv.diamond, inv.copperIngot, inv.coal, inv.emerald);
            LOGGER.info("    建筑: 工作台={} 熔炉={} 箱子={} 床={} 火把={}",
                    inv.craftingTable, inv.furnace, inv.chest, inv.bed, inv.torch);
            LOGGER.info("    食物储备: {} 总份", inv.totalFood());
            LOGGER.info("    死亡={} LLM调用={}", deaths, llm);
            LOGGER.info("    §b评分: {} (资源{} + LLM效率{} - 死亡{}) {}",
                    score, resourceScore, llmBonus, deathPenalty,
                    score >= 500 ? "★" : score >= 200 ? "☆" : "");
        }
        LOGGER.info("═══════════════════════════════════════════════");
        LOGGER.info("");
    }
}
