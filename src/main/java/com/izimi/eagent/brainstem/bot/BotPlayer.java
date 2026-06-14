package com.izimi.eagent.brainstem.bot;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.authlib.GameProfile;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BotPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    private static final int LOG_TICK_INTERVAL = 60;
    private static final Map<UUID, BotPlayer> ACTIVE_BOTS = new ConcurrentHashMap<>();

    private final ServerPlayerEntity playerEntity;
    private long tickCounter = 0;
    private float storedForward = 0f;
    private float storedStrafe = 0f;
    private boolean storedJump = false;

    private BotPlayer(ServerPlayerEntity playerEntity) {
        this.playerEntity = playerEntity;
    }

    public static void registerBot(UUID uuid, BotPlayer bot) {
        if (uuid != null && bot != null) {
            ACTIVE_BOTS.put(uuid, bot);
        }
    }

    public static BotPlayer getByUUID(UUID uuid) {
        return uuid != null ? ACTIVE_BOTS.get(uuid) : null;
    }

    public void unregisterBot() {
        ACTIVE_BOTS.remove(playerEntity.getUuid());
    }

    public void setMoveInput(float forward, float strafe, boolean jump) {
        this.storedForward = forward;
        this.storedStrafe = strafe;
        this.storedJump = jump;
    }

    public static BotPlayer create(MinecraftServer server, ServerWorld world, GameProfile profile) {
        SyncedClientOptions options = SyncedClientOptions.createDefault();
        ServerPlayerEntity entity = server.getPlayerManager().createPlayer(profile, options);

        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND) {
            @Override
            public void send(Packet<?> packet, @Nullable PacketCallbacks callbacks, boolean flush) {}

            @Override
            public boolean isOpen() { return true; }

            @Override
            public void disconnect(Text disconnectReason) {}

            @Override
            public void flush() {}

            @Override
            public void tick() {}
        };
        ConnectedClientData clientData = ConnectedClientData.createDefault(profile, false);
        ServerPlayNetworkHandler handler = new ServerPlayNetworkHandler(server, connection, entity, clientData);
        entity.networkHandler = handler;

        return new BotPlayer(entity);
    }

    public ServerPlayerEntity asEntity() {
        return playerEntity;
    }

    public boolean isRemoved() {
        return playerEntity.isRemoved();
    }

    public void setRemoved(net.minecraft.entity.Entity.RemovalReason reason) {
        playerEntity.setRemoved(reason);
    }

    public void setPos(double x, double y, double z) {
        playerEntity.setPos(x, y, z);
    }

    public void setPitch(float pitch) {
        playerEntity.setPitch(pitch);
    }

    public void setYaw(float yaw) {
        playerEntity.setYaw(yaw);
    }

    public void setHeadYaw(float headYaw) {
        playerEntity.setHeadYaw(headYaw);
    }

    public void setHealth(float health) {
        playerEntity.setHealth(health);
    }

    public void tick() {
        // Inject stored navigation input right before entity tick
        // (counters potential internal reset in ServerPlayerEntity.tick())
        if (storedForward != 0f || storedStrafe != 0f || storedJump) {
            playerEntity.updateInput(storedForward, storedStrafe, storedJump, false);
        }

        Vec3d oldPos = playerEntity.getPos();
        playerEntity.tick();

        // Log position changes
        if (!oldPos.equals(playerEntity.getPos())) {
            LOGGER.info("[BotPlayer] moved: ({:.2f},{:.2f},{:.2f}) -> ({:.2f},{:.2f},{:.2f}), delta=({:.4f},{:.4f},{:.4f})",
                    oldPos.x, oldPos.y, oldPos.z,
                    playerEntity.getX(), playerEntity.getY(), playerEntity.getZ(),
                    playerEntity.getX() - oldPos.x,
                    playerEntity.getY() - oldPos.y,
                    playerEntity.getZ() - oldPos.z);
        }

        tickCounter++;
        if (tickCounter % LOG_TICK_INTERVAL == 0) {
            LOGGER.info("[BotPlayer] tick={}, pos=({:.1f},{:.1f},{:.1f}), isOnGround={}, yaw={}",
                    tickCounter,
                    playerEntity.getX(), playerEntity.getY(), playerEntity.getZ(),
                    playerEntity.isOnGround(),
                    playerEntity.getYaw());
        }
    }

    public MinecraftServer getServer() {
        return playerEntity.getServer();
    }

    public BlockPos getBlockPos() {
        return playerEntity.getBlockPos();
    }

    public Vec3d getPos() {
        return playerEntity.getPos();
    }

    public ServerWorld getServerWorld() {
        return playerEntity.getServerWorld();
    }

    public boolean isOnGround() {
        return playerEntity.isOnGround();
    }

    public void jump() {
        playerEntity.jump();
    }

    public net.minecraft.entity.player.PlayerInventory getInventory() {
        return playerEntity.getInventory();
    }

    public net.minecraft.entity.player.HungerManager getHungerManager() {
        return playerEntity.getHungerManager();
    }

    public float getHealth() {
        return playerEntity.getHealth();
    }

    public void setVelocity(Vec3d velocity) {
        playerEntity.setVelocity(velocity);
        playerEntity.velocityModified = true;
    }

    public void attack(net.minecraft.entity.Entity target) {
        playerEntity.attack(target);
    }

    public void swingHand(net.minecraft.util.Hand hand) {
        playerEntity.swingHand(hand);
    }

    public void lookAtEntity(net.minecraft.entity.Entity target) {
        double dx = target.getX() - playerEntity.getX();
        double dy = target.getEyeY() - playerEntity.getEyeY();
        double dz = target.getZ() - playerEntity.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance)));
        playerEntity.setYaw(yaw);
        playerEntity.setHeadYaw(yaw);
        playerEntity.setPitch(pitch);
    }

    public ServerPlayNetworkHandler getNetworkHandler() {
        return playerEntity.networkHandler;
    }
}
