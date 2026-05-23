package de.legoshi.parkourcalc.fabric.sim;

import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.LazyEntitySimulator;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.List;

public final class FabricSimulator extends LazyEntitySimulator<SimulatorEntity> {

    @Override
    protected SimulatorEntity createEntity(Vec3dCore pendingStart, Vec3dCore pendingVelocity, Float pendingYaw) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld clientWorld = client.world;
        PlayerEntity player = client.player;
        if (player == null || clientWorld == null) {
            throw new IllegalStateException("Cannot create simulator: player or world is null");
        }
        // On the server thread (SP): bind to ServerWorld so block reads hit it natively and
        // chunks can page in from disk. Off-thread (MP, or pre-sim setup): stay on ClientWorld.
        World simWorld = clientWorld;
        MinecraftServer server = client.getServer();
        if (server != null && server.isOnThread()) {
            ServerWorld serverWorld = server.getWorld(clientWorld.getRegistryKey());
            if (serverWorld != null) {
                simWorld = serverWorld;
            }
        }
        Vec3d start = pendingStart != null
                ? new Vec3d(pendingStart.x, pendingStart.y, pendingStart.z)
                : player.getEntityPos();
        Vec3d vel = pendingVelocity != null
                ? new Vec3d(pendingVelocity.x, pendingVelocity.y, pendingVelocity.z)
                : Vec3d.ZERO;
        float yaw = pendingYaw != null ? pendingYaw : 0.0F;
        return new SimulatorEntity(simWorld, player.getGameProfile(), start, vel, yaw);
    }

    @Override protected void resetEntity(SimulatorEntity e) { e.resetPlayer(); }
    @Override protected void setInput(SimulatorEntity e, InputRow row) { e.input.setData(row); }
    @Override protected void applyYaw(SimulatorEntity e, float yaw) { e.setYaw(e.getYaw() + yaw); }

    @Override
    protected void applyTickEffects(SimulatorEntity e, int speedAmplifier, int jumpBoostAmplifier) {
        e.clearStatusEffects();
        if (speedAmplifier > 0) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 2, speedAmplifier - 1));
        }
        if (jumpBoostAmplifier > 0) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 2, jumpBoostAmplifier - 1));
        }
    }

    @Override
    protected void tickEntity(SimulatorEntity e) {
        preloadChunksAround(e);
        e.beginSubtickCapture();
        e.tick();
    }

    /** isChunkLoaded short-circuits the common case; getChunk(FULL, true) only fires on miss. */
    private static void preloadChunksAround(SimulatorEntity e) {
        World world = e.getEntityWorld();
        if (!(world instanceof ServerWorld)) return;
        ServerWorld serverWorld = (ServerWorld) world;
        Vec3d pos = e.getEntityPos();
        int cx1 = ((int) Math.floor(pos.x - 1.0)) >> 4;
        int cz1 = ((int) Math.floor(pos.z - 1.0)) >> 4;
        int cx2 = ((int) Math.floor(pos.x + 1.0)) >> 4;
        int cz2 = ((int) Math.floor(pos.z + 1.0)) >> 4;
        for (int cx = cx1; cx <= cx2; cx++) {
            for (int cz = cz1; cz <= cz2; cz++) {
                if (!serverWorld.isChunkLoaded(cx, cz)) {
                    serverWorld.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, true);
                }
            }
        }
    }

    @Override
    protected List<Vec3dCore> getSubtickPath(SimulatorEntity e) {
        return e.endSubtickCapture();
    }

    @Override
    protected Vec3dCore getPos(SimulatorEntity e) {
        Vec3d p = e.getEntityPos();
        return new Vec3dCore(p.x, p.y, p.z);
    }

    @Override
    protected boolean isOnGround(SimulatorEntity e) {
        return e.isOnGround();
    }

    @Override
    protected boolean isSneaking(SimulatorEntity e) {
        return e.isSneaking();
    }

    @Override
    protected boolean isWallCollision(SimulatorEntity e) {
        return e.horizontalCollision;
    }

    @Override
    protected Vec3dCore getVelocity(SimulatorEntity e) {
        Vec3d v = e.getVelocity();
        return new Vec3dCore(v.x, v.y, v.z);
    }

    @Override
    protected boolean isSoftCollision(SimulatorEntity e) {
        return e.collidedSoftly;
    }

    @Override
    protected float getYaw(SimulatorEntity e) {
        return e.getYaw();
    }

    @Override
    protected Vec3dCore getStart(SimulatorEntity e) {
        Vec3d p = e.startPosition;
        return new Vec3dCore(p.x, p.y, p.z);
    }

    @Override
    protected void setStart(SimulatorEntity e, Vec3dCore pos) {
        e.startPosition = new Vec3d(pos.x, pos.y, pos.z);
    }

    @Override
    protected Vec3dCore getStartVel(SimulatorEntity e) {
        Vec3d v = e.startVelocity;
        return new Vec3dCore(v.x, v.y, v.z);
    }

    @Override
    protected void setStartVel(SimulatorEntity e, Vec3dCore vel) {
        e.startVelocity = new Vec3d(vel.x, vel.y, vel.z);
    }

    @Override
    protected float getStartYawValue(SimulatorEntity e) {
        return e.startYaw;
    }

    @Override
    protected void setStartYawValue(SimulatorEntity e, float yaw) {
        e.startYaw = yaw;
    }

    @Override
    protected Checkpoint saveCheckpoint(SimulatorEntity e) {
        return e.saveCheckpoint();
    }

    @Override
    protected void restoreCheckpoint(SimulatorEntity e, Checkpoint checkpoint) {
        e.restoreCheckpoint((SimulatorEntity.Checkpoint) checkpoint);
    }
}
