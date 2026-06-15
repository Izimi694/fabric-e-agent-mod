package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.brainstem.bot.BotInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private SurvivalChallengeMonitor() {}

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

        return String.format("%s[HP:%d/%d 饿:%d 🪓:%d ⛏:%d 💎:%d 💀:%d 🤖:%d]",
                bot.getBotName(), hp, (int) entity.getMaxHealth(), food,
                inv.pickaxe, inv.iron, inv.diamond, deaths, llm);
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

        return String.format("pos=[%d,%d,%d] HP=%d Hung=%d legacy=%s 物品:木镐=%d 石镐=%d 铁镐=%d 钻石镐=%d 铁锭=%d 钻石=%d 死亡=%d LLM=%d",
                pos.getX(), pos.getY(), pos.getZ(),
                hp, food, legacy,
                inv.woodPick, inv.stonePick, inv.ironPick, inv.diamondPick,
                inv.iron, inv.diamond, deaths, llm);
    }

    // ════════════════════════════════════════════════════════════════════
    //  背包摘要
    // ════════════════════════════════════════════════════════════════════

    private static InvSummary summarizeInventory(ServerPlayerEntity entity) {
        InvSummary s = new InvSummary();
        for (ItemStack stack : entity.getInventory().main) {
            if (stack.isEmpty()) continue;
            int cnt = stack.getCount();
            if (stack.isOf(Items.WOODEN_PICKAXE))  s.woodPick += cnt;
            if (stack.isOf(Items.STONE_PICKAXE))   s.stonePick += cnt;
            if (stack.isOf(Items.IRON_PICKAXE))    s.ironPick += cnt;
            if (stack.isOf(Items.DIAMOND_PICKAXE)) s.diamondPick += cnt;
            if (stack.isOf(Items.IRON_INGOT))      s.iron += cnt;
            if (stack.isOf(Items.DIAMOND))         s.diamond += cnt;
        }
        s.pickaxe = (s.woodPick + s.stonePick + s.ironPick + s.diamondPick) > 0 ? 1 : 0;
        return s;
    }

    private static class InvSummary {
        int woodPick, stonePick, ironPick, diamondPick;
        int iron, diamond;
        int pickaxe; // 有无任何镐
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
            int score = inv.iron * 10 + inv.diamond * 50 - deaths * 100;
            score = Math.max(0, score);

            LOGGER.info("  {} (legacy={})", b.getBotName(), b.isLegacyScoring());
            LOGGER.info("    HP={}/{} 饿={}", (int) entity.getHealth(), (int) entity.getMaxHealth(),
                    entity.getHungerManager().getFoodLevel());
            LOGGER.info("    工具: 木={} 石={} 铁={} 钻={}",
                    inv.woodPick, inv.stonePick, inv.ironPick, inv.diamondPick);
            LOGGER.info("    资源: 铁={} 钻={}", inv.iron, inv.diamond);
            LOGGER.info("    死亡={} LLM调用={}", deaths, llm);
            LOGGER.info("    §b评分: {} {}", score, score >= 100 ? "★" : "");
        }
        LOGGER.info("═══════════════════════════════════════════════");
        LOGGER.info("");
    }
}
