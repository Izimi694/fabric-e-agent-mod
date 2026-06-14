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
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
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
        tickCounter++;

        boolean hasInput = storedForward != 0f || storedStrafe != 0f || storedJump;

        // 1. Read current velocity
        Vec3d vel = playerEntity.getVelocity();

        // 2. Apply gravity (0.08 blocks/tick²)
        if (!playerEntity.isOnGround()) {
            vel = vel.subtract(0, 0.08, 0);
        }

        // 3. Apply friction
        if (playerEntity.isOnGround()) {
            vel = new Vec3d(vel.x * 0.6, vel.y * 0.98, vel.z * 0.6);
        } else {
            vel = new Vec3d(vel.x * 0.91, vel.y * 0.98, vel.z * 0.91);
        }

        // 4. Apply input-based velocity
        if (hasInput) {
            float yawRad = playerEntity.getYaw() * 0.017453292f;
            float speed = playerEntity.getMovementSpeed();

            double vx = (-MathHelper.sin(yawRad) * storedForward + MathHelper.cos(yawRad) * storedStrafe) * speed;
            double vz = (MathHelper.cos(yawRad) * storedForward + MathHelper.sin(yawRad) * storedStrafe) * speed;
            vel = vel.add(vx, 0, vz);

            if (storedJump && playerEntity.isOnGround()) {
                vel = vel.add(0, 0.42, 0);
            }
        }

        playerEntity.setVelocity(vel);
        playerEntity.velocityModified = true;

        // 5. Move entity (collision-aware)
        double oldX = playerEntity.getX();
        double oldY = playerEntity.getY();
        double oldZ = playerEntity.getZ();

        playerEntity.move(MovementType.SELF, vel);

        double moved = Math.sqrt(
                Math.pow(playerEntity.getX() - oldX, 2) +
                Math.pow(playerEntity.getY() - oldY, 2) +
                Math.pow(playerEntity.getZ() - oldZ, 2));

        // 6. Zero out downward velocity when on ground
        if (playerEntity.isOnGround() && playerEntity.getVelocity().y < 0) {
            Vec3d v = playerEntity.getVelocity();
            playerEntity.setVelocity(v.x, 0, v.z);
            playerEntity.velocityModified = true;
        }

        // 7. Log every 60 ticks (or if input but no movement)
        if (tickCounter % LOG_TICK_INTERVAL == 0 || (hasInput && moved < 0.001)) {
            LOGGER.info("[BotPlayer] tick={}, pos=({},{},{}), onGround={}, yaw={}, vel=({},{},{}), input=({},{},{}), moved={}",
                    tickCounter,
                    String.format("%.1f", playerEntity.getX()),
                    String.format("%.1f", playerEntity.getY()),
                    String.format("%.1f", playerEntity.getZ()),
                    playerEntity.isOnGround(),
                    String.format("%.1f", playerEntity.getYaw()),
                    String.format("%.3f", playerEntity.getVelocity().x),
                    String.format("%.3f", playerEntity.getVelocity().y),
                    String.format("%.3f", playerEntity.getVelocity().z),
                    storedForward, storedStrafe, storedJump ? 1 : 0,
                    String.format("%.4f", moved));
        }

        // 8. Reset stored input for next tick
        storedForward = 0;
        storedStrafe = 0;
        storedJump = false;
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
