package de.legoshi.parkourcalc.forge8.sim;

import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.LazyEntitySimulator;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

public final class Forge8Simulator extends LazyEntitySimulator<SimulatorEntity> {

    @Override
    protected SimulatorEntity createEntity(Vec3dCore pendingStart, Vec3dCore pendingVelocity, Float pendingYaw) {
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient clientWorld = mc.theWorld;
        EntityPlayerSP player = mc.thePlayer;
        if (player == null || clientWorld == null) {
            throw new IllegalStateException("Cannot create simulator: player or world is null");
        }
        // SP (integrated server running): bind to WorldServer so chunks can page in from disk.
        // We tick from the client thread against it; ChunkProviderServer is plain map access
        // without thread routing on 1.8.9, so reads stay cheap and races are unlikely in practice.
        // MP: no integrated server, stay on WorldClient.
        World simWorld = clientWorld;
        IntegratedServer server = mc.getIntegratedServer();
        if (server != null) {
            WorldServer serverWorld = server.worldServerForDimension(clientWorld.provider.getDimensionId());
            if (serverWorld != null) {
                simWorld = serverWorld;
            }
        }
        Vec3 start = pendingStart != null
                ? new Vec3(pendingStart.x, pendingStart.y, pendingStart.z)
                : new Vec3(player.posX, player.posY, player.posZ);
        Vec3 vel = pendingVelocity != null
                ? new Vec3(pendingVelocity.x, pendingVelocity.y, pendingVelocity.z)
                : new Vec3(0.0, 0.0, 0.0);
        float yaw = pendingYaw != null ? pendingYaw : 0.0F;
        return new SimulatorEntity(simWorld, player.getGameProfile(), start, vel, yaw);
    }

    @Override protected void resetEntity(SimulatorEntity e) { e.resetPlayer(); }
    @Override protected void setInput(SimulatorEntity e, InputRow row) { e.setInput(row); }
    @Override protected void applyYaw(SimulatorEntity e, float yaw) { e.rotationYaw += yaw; }

    @Override
    protected void applyTickEffects(SimulatorEntity e, int speedAmplifier, int jumpBoostAmplifier) {
        e.clearActivePotions();
        if (speedAmplifier > 0) {
            e.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 2, speedAmplifier - 1));
        }
        if (jumpBoostAmplifier > 0) {
            e.addPotionEffect(new PotionEffect(Potion.jump.id, 2, jumpBoostAmplifier - 1));
        }
    }

    @Override
    protected void tickEntity(SimulatorEntity e) {
        preloadChunksAround(e);
        e.beginSubtickCapture();
        e.onUpdate();
    }

    /** chunkExists short-circuits the common case; provideChunk only fires on miss. */
    private static void preloadChunksAround(SimulatorEntity e) {
        if (!(e.worldObj instanceof WorldServer)) return;
        WorldServer serverWorld = (WorldServer) e.worldObj;
        int cx1 = ((int) Math.floor(e.posX - 1.0)) >> 4;
        int cz1 = ((int) Math.floor(e.posZ - 1.0)) >> 4;
        int cx2 = ((int) Math.floor(e.posX + 1.0)) >> 4;
        int cz2 = ((int) Math.floor(e.posZ + 1.0)) >> 4;
        for (int cx = cx1; cx <= cx2; cx++) {
            for (int cz = cz1; cz <= cz2; cz++) {
                if (!serverWorld.theChunkProviderServer.chunkExists(cx, cz)) {
                    serverWorld.theChunkProviderServer.provideChunk(cx, cz);
                }
            }
        }
    }

    @Override
    protected String formatDebugState(SimulatorEntity e, int tickIndex) {
        PotionEffect spd = e.getActivePotionEffect(Potion.moveSpeed);
        PotionEffect jmp = e.getActivePotionEffect(Potion.jump);
        double mvSp = e.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.movementSpeed).getAttributeValue();
        return "[PC-STATE sim ] t=" + tickIndex
                + " pos=" + e.posX + "," + e.posY + "," + e.posZ
                + " mot=" + e.motionX + "," + e.motionY + "," + e.motionZ
                + " yaw=" + e.rotationYaw
                + " onG=" + e.onGround
                + " spr=" + e.isSprinting()
                + " sne=" + e.isSneaking()
                + " colH=" + e.isCollidedHorizontally
                + " mvF=" + e.moveForward
                + " mvS=" + e.moveStrafing
                + " spdAmp=" + (spd == null ? -1 : spd.getAmplifier())
                + " jmpAmp=" + (jmp == null ? -1 : jmp.getAmplifier())
                + " mvSpeed=" + mvSp;
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
        return e.isCollidedHorizontally;
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
        Vec3 p = e.startPosition;
        return new Vec3dCore(p.xCoord, p.yCoord, p.zCoord);
    }

    @Override
    protected void setStart(SimulatorEntity e, Vec3dCore pos) {
        e.startPosition = new Vec3(pos.x, pos.y, pos.z);
    }

    @Override
    protected Vec3dCore getStartVel(SimulatorEntity e) {
        Vec3 v = e.startVelocity;
        return new Vec3dCore(v.xCoord, v.yCoord, v.zCoord);
    }

    @Override
    protected void setStartVel(SimulatorEntity e, Vec3dCore vel) {
        e.startVelocity = new Vec3(vel.x, vel.y, vel.z);
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
