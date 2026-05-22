package de.legoshi.parkourcalc.forge12.sim;

import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.LazyEntitySimulator;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

public final class Forge12Simulator extends LazyEntitySimulator<SimulatorEntity> {

    @Override
    protected SimulatorEntity createEntity(Vec3dCore pendingStart, Vec3dCore pendingVelocity, Float pendingYaw) {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient clientWorld = mc.world;
        EntityPlayerSP player = mc.player;
        if (player == null || clientWorld == null) {
            throw new IllegalStateException("Cannot create simulator: player or world is null");
        }
        // SP (integrated server running): bind to WorldServer so chunks can page in from disk.
        // We tick from the client thread against it; ChunkProviderServer is plain map access
        // without thread routing on 1.12.2, so reads stay cheap and races are unlikely in practice.
        // MP: no integrated server, stay on WorldClient.
        World simWorld = clientWorld;
        IntegratedServer server = mc.getIntegratedServer();
        if (server != null) {
            WorldServer serverWorld = server.getWorld(clientWorld.provider.getDimension());
            if (serverWorld != null) {
                simWorld = serverWorld;
            }
        }
        Vec3d start = pendingStart != null
                ? new Vec3d(pendingStart.x, pendingStart.y, pendingStart.z)
                : new Vec3d(player.posX, player.posY, player.posZ);
        Vec3d vel = pendingVelocity != null
                ? new Vec3d(pendingVelocity.x, pendingVelocity.y, pendingVelocity.z)
                : Vec3d.ZERO;
        float yaw = pendingYaw != null ? pendingYaw : 0.0F;
        return new SimulatorEntity(simWorld, player.getGameProfile(), start, vel, yaw);
    }

    @Override protected void resetEntity(SimulatorEntity e) { e.resetPlayer(); }
    @Override protected void setInput(SimulatorEntity e, InputRow row) { e.setInput(row); }
    @Override protected void applyYaw(SimulatorEntity e, float yaw) { e.rotationYaw += yaw; }

    @Override
    protected void tickEntity(SimulatorEntity e) {
        preloadChunksAround(e);
        e.beginSubtickCapture();
        e.onUpdate();
    }

    /** getLoadedChunk short-circuits the common case; provideChunk only fires on miss. */
    private static void preloadChunksAround(SimulatorEntity e) {
        if (!(e.world instanceof WorldServer)) return;
        WorldServer serverWorld = (WorldServer) e.world;
        int cx1 = ((int) Math.floor(e.posX - 1.0)) >> 4;
        int cz1 = ((int) Math.floor(e.posZ - 1.0)) >> 4;
        int cx2 = ((int) Math.floor(e.posX + 1.0)) >> 4;
        int cz2 = ((int) Math.floor(e.posZ + 1.0)) >> 4;
        for (int cx = cx1; cx <= cx2; cx++) {
            for (int cz = cz1; cz <= cz2; cz++) {
                if (serverWorld.getChunkProvider().getLoadedChunk(cx, cz) == null) {
                    serverWorld.getChunkProvider().provideChunk(cx, cz);
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
        return new Vec3dCore(e.posX, e.posY, e.posZ);
    }

    @Override
    protected boolean isOnGround(SimulatorEntity e) {
        return e.onGround;
    }

    @Override
    protected boolean isSneaking(SimulatorEntity e) {
        return e.isSneaking();
    }

    @Override
    protected boolean isWallCollision(SimulatorEntity e) {
        return e.collidedHorizontally;
    }

    @Override
    protected Vec3dCore getVelocity(SimulatorEntity e) {
        return new Vec3dCore(e.motionX, e.motionY, e.motionZ);
    }

    @Override
    protected boolean isSoftCollision(SimulatorEntity e) {
        return false;
    }

    @Override
    protected float getYaw(SimulatorEntity e) {
        return e.rotationYaw;
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
