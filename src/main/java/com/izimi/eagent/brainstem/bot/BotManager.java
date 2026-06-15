package com.izimi.eagent.brainstem.bot;

import com.izimi.eagent.api.WorldContext;
import static com.izimi.eagent.amygdala.ReflexConstants.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.izimi.eagent.amygdala.BotParams;
import com.izimi.eagent.brainstem.adapter.TemporalScaler;
import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class BotManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private final Map<UUID, BotInstance> bots = new LinkedHashMap<>();
    private WorldContext worldContext;

    public void setWorldContext(WorldContext ctx) {
        this.worldContext = ctx;
    }

    public BotInstance spawn(String name, MinecraftServer server, ServerWorld world, Vec3d position) {
        return spawn(name, server, world, position, true);
    }

    public BotInstance spawn(String name, MinecraftServer server, ServerWorld world, Vec3d position, boolean inheritGenome) {
        UUID botId = UUID.randomUUID();
        GameProfile profile = new GameProfile(botId, name);
        BotPlayer botPlayer = BotPlayer.create(server, world, profile);

        ServerPlayerEntity entity = botPlayer.asEntity();
        entity.setPos(position.x, position.y, position.z);
        entity.setPitch(0);
        entity.setYaw(0);

        // Force-load chunk at spawn so entity enters world tick list
        net.minecraft.util.math.BlockPos bp = net.minecraft.util.math.BlockPos.ofFloored(position);
        world.getChunk(bp.getX() >> 4, bp.getZ() >> 4);
        LOGGER.info("[BotManager] 强制加载区块: ({}, {}) 于 ({:.1f}, {:.1f}, {:.1f})",
                bp.getX() >> 4, bp.getZ() >> 4, position.x, position.y, position.z);

        entity.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);

        PlayerManager playerManager = server.getPlayerManager();
        playerManager.getPlayerList().add(entity);
        server.getPlayerManager().sendToAll(new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, entity));
        world.onPlayerConnected(entity);
        LOGGER.info("[BotManager] Bot游戏模式: {}, abilities.allowFlying={}, abilities.flying={}, noClip={}",
                entity.interactionManager.getGameMode(),
                entity.getAbilities().allowFlying,
                entity.getAbilities().flying,
                entity.isSpectator() || entity.isCreative());

        entity.setHealth(20.0f);
        entity.getHungerManager().setFoodLevel(20);

        // onPlayerConnected 已处理 world.player 注册，spawnEntity 会重复注册导致 UUID 碰撞
        // world.spawnEntity(entity);

        entity.getAbilities().flying = false;
        entity.getAbilities().allowFlying = false;
        entity.getAbilities().invulnerable = false;
        entity.sendAbilitiesUpdate();

        // Phase 7: Inherit params from genome archive if available
        BotParams childParams = null;
        if (inheritGenome && GenomeArchivist.hasGenomes()) {
            BotParams parent = GenomeArchivist.getLatestGenomeParams();
            if (parent != null) {
                BotParams p2 = getSecondParent(parent);
                childParams = p2 != null ? BotParams.inherit(parent, p2) : BotParams.inherit(parent);
                LOGGER.info("[BotManager] 继承参数: {} (gen {}) -> {} (gen {})",
                        parent.getGeneration(), parent.getGeneration() + 1, name, childParams.getGeneration());
            }
        }

        BotInstance instance = new BotInstance(botId, name, botPlayer, childParams, worldContext);

        // Register in BotPlayer's active bots map for navigation communication
        BotPlayer.registerBot(botId, botPlayer);

        FileUtil.getBotDir(botId).toFile().mkdirs();

        copyReflexesFromMentor(instance);

        bots.put(botId, instance);
        LOGGER.info("[BotManager] Bot已生成: {} ({})", name, botId);
        return instance;
    }

    private BotParams getSecondParent(BotParams primary) {
        List<GenomeArchivist.GenomeRecord> records = GenomeArchivist.listGenomes();
        if (records.size() < 2) return null;
        // Pick a genome with different parentId if possible
        for (var r : records) {
            if (r.parentId() == null || !r.parentId().equals(primary.getBotId())) {
                return GenomeArchivist.loadGenomeParams(r.botId());
            }
        }
        return null;
    }

    public boolean despawn(UUID botId) {
        BotInstance instance = bots.get(botId);
        if (instance == null) return false;

        try {
            worldContext.brainstem().basicActions().stopNavigation(botId);
            // Save genome before removing (Phase 7)
            instance.saveDeathGenome("despawned");

            instance.getBotPlayer().unregisterBot();

            MinecraftServer server = instance.getBotPlayer().getServer();
            if (server != null) {
                server.getPlayerManager().remove(instance.asEntity());
            }
            instance.getBotPlayer().setRemoved(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            bots.remove(botId);
            LOGGER.info("[BotManager] Bot已移除: {} ({})", instance.getBotName(), botId);
            return true;
        } catch (Exception e) {
            LOGGER.error("[BotManager] Bot移除失败: {}", botId, e);
            return false;
        }
    }

    public void despawnAll() {
        List<UUID> ids = new ArrayList<>(bots.keySet());
        for (UUID id : ids) {
            despawn(id);
        }
    }

    public BotInstance getById(UUID botId) {
        return bots.get(botId);
    }

    public BotInstance getByName(String name) {
        String lower = name.toLowerCase();
        // Exact match on botName or nickname
        for (BotInstance instance : bots.values()) {
            if (instance.getBotName().toLowerCase().equals(lower)) return instance;
            String nick = instance.getNickname();
            if (nick != null && nick.toLowerCase().equals(lower)) return instance;
        }
        // Partial match fallback
        for (BotInstance instance : bots.values()) {
            if (instance.getBotName().toLowerCase().contains(lower)) return instance;
            String nick = instance.getNickname();
            if (nick != null && nick.toLowerCase().contains(lower)) return instance;
        }
        return null;
    }

    public BotInstance getNearest(ServerPlayerEntity player) {
        if (player == null || bots.isEmpty()) return null;
        ServerWorld world = player.getServerWorld();

        BotInstance nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (BotInstance instance : bots.values()) {
            ServerPlayerEntity bot = instance.asEntity();
            if (bot == null || bot.isRemoved()) continue;
            if (bot.getServerWorld() != world) continue;

            double dist = bot.squaredDistanceTo(player);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = instance;
            }
        }

        return nearest;
    }

    public List<BotInstance> getAll() {
        return new ArrayList<>(bots.values());
    }

    public List<BotInstance> getNearby(ServerPlayerEntity player, double radius) {
        if (player == null) return List.of();
        ServerWorld world = player.getServerWorld();
        double radiusSq = radius * radius;

        return bots.values().stream()
                .filter(i -> {
                    ServerPlayerEntity bot = i.asEntity();
                    return bot != null && !bot.isRemoved()
                            && bot.getServerWorld() == world
                            && bot.squaredDistanceTo(player) <= radiusSq;
                })
                .collect(Collectors.toList());
    }

    public boolean isEmpty() {
        return bots.isEmpty();
    }

    public int getCount() {
        return bots.size();
    }

    public void tickAll(MinecraftServer server) {
        List<BotInstance> active = new ArrayList<>(bots.values());
        for (BotInstance instance : active) {
            if (!instance.isSpawned()) {
                instance.saveDeathGenome("removed");
                bots.remove(instance.getBotId());
                continue;
            }

            TemporalScaler scaler = instance.getTemporalScaler();
            if (scaler != null && scaler.getSpeed() < 0.01f) {
                continue;
            }

            instance.tick(server);
        }
    }

    // Phase 7: Scaffold-style reflex inheritance (trial-first)
    private void copyReflexesFromMentor(BotInstance newBot) {
        if (bots.isEmpty()) return;

        BotInstance mentor = bots.values().iterator().next();
        Path mentorDir = FileUtil.getBotConditionedDir(mentor.getBotId());
        Path newBotDir = FileUtil.getBotConditionedDir(newBot.getBotId());

        if (!Files.exists(mentorDir)) return;

        try {
            Files.createDirectories(newBotDir);
            try (var stream = Files.list(mentorDir)) {
                stream.filter(p -> p.toString().endsWith(".json"))
                        .forEach(src -> copyReflexFile(src, mentorDir, newBotDir));
            }
            LOGGER.info("[BotManager] 从 {} 继承反射到 {} (trial-first)",
                    mentor.getBotName(), newBot.getBotName());
        } catch (IOException e) {
            LOGGER.warn("[BotManager] 冷启动反射复制失败", e);
        }
    }

    private void copyReflexFile(Path src, Path mentorDir, Path newBotDir) {
        try {
            Path rel = mentorDir.relativize(src);
            Path dest = newBotDir.resolve(rel);

            Map<String, Object> data = JsonUtil.readMapFromFileSafe(src);
            if (data == null) return;

            data.put(KEY_STATUS, STATUS_TRIAL);
data.put(KEY_TRIAL_SUCCESSES, 0);
data.put(KEY_TRIAL_FAILURES, 0);
data.put(KEY_SHORT_TERM_WEIGHT, 0.5);
data.put(KEY_LONG_TERM_BASELINE, 0.5);
data.put(KEY_PROFICIENCY, 0.3);
data.put(KEY_EXECUTION_COUNT, 0);
data.put(KEY_SUCCESS_RATE, 0.0);

            resetAtomsToTrial(data);

            JsonUtil.writeToFileSafeAtomic(dest, data);
        } catch (Exception e) {
            LOGGER.warn("[BotManager] 复制反射失败: {}", src, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void resetAtomsToTrial(Map<String, Object> data) {
        List<Map<String, Object>> atoms = (List<Map<String, Object>>) data.get(KEY_ATOMS);
        if (atoms == null) return;
        for (Map<String, Object> atom : atoms) {
            atom.put(KEY_EXECUTION_COUNT, 0);
            atom.put(KEY_SUCCESS_RATE, 0.0);
            atom.put(KEY_PROFICIENCY, 0.1);
        }
    }

    public void notifyReflexSuccess(ServerPlayerEntity executor, String category) {
        if (executor == null || category == null) return;
        ServerWorld world = executor.getServerWorld();
        for (BotInstance observer : bots.values()) {
            if (!observer.isSpawned()) continue;
            ServerPlayerEntity obsEntity = observer.asEntity();
            if (obsEntity == null || obsEntity == executor) continue;
            if (obsEntity.getServerWorld() != world) continue;
            if (obsEntity.squaredDistanceTo(executor) > 900) continue; // 30 blocks
            observer.getConditionedReflex().observePeerSuccess(category);
        }
    }

    public boolean isSpawned() {
        return !bots.isEmpty();
    }
}
