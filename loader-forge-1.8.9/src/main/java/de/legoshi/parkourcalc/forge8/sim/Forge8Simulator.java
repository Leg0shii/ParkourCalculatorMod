package de.legoshi.parkourcalc.forge8.sim;

import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.anglesolver.Medium;
import de.legoshi.parkourcalc.core.sim.ChunkRange;
import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.LazyEntitySimulator;
import de.legoshi.parkourcalc.core.sim.ServerSimEvent;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.forge8.sim.paired.PairedCheckpoint;
import de.legoshi.parkourcalc.forge8.sim.paired.PairedServerSim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.List;

@SuppressWarnings("DuplicatedCode")
public final class Forge8Simulator extends LazyEntitySimulator<SimulatorEntity> {

    private static volatile boolean serverThreadRequired;

    private boolean pairedEnabled;
    private boolean pairedDamage = true;
    private PairedServerSim pair;
    private InputRow lastRow;
    private float startPitch = PlaybackController.DEFAULT_PITCH;

    public static boolean needsServerThread() {
        return serverThreadRequired;
    }

    private void updateServerThreadRequirement() {
        serverThreadRequired = pairedEnabled || pair != null;
    }

    @Override
    public boolean supportsPairedSimulation() {
        return true;
    }

    @Override
    public void setPairedSimulation(boolean enabled) {
        if (!enabled && pair != null) {
            final PairedServerSim p = pair;
            pair = null;
            p.level().getMinecraftServer().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    p.shutdown();
                }
            });
        }
        pairedEnabled = enabled;
        updateServerThreadRequirement();
    }

    @Override
    public void setPairedDamage(boolean enabled) {
        pairedDamage = enabled;
        if (pair != null) pair.setDamageEnabled(enabled);
    }

    @Override
    public void setStartPitch(float pitch) {
        startPitch = pitch;
        if (pair != null) pair.setStartPitch(pitch);
    }

    @Override
    public List<ServerSimEvent> takeServerSimEvents() {
        return pair == null ? java.util.Collections.<ServerSimEvent>emptyList() : pair.drainEvents();
    }

    @Override
    public void onPassEnd() {
        if (pair != null) pair.endPass();
    }

    @Override
    public void onReplayStart(final int startTick) {
        final PairedServerSim p = pair;
        if (p == null) return;
        p.level().getMinecraftServer().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                p.onReplayStart(startTick);
            }
        });
    }

    @Override
    public void onReplayEnd() {
        final PairedServerSim p = pair;
        if (p == null) return;
        p.level().getMinecraftServer().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                p.onReplayEnd();
            }
        });
    }

    @Override
    protected void onInvalidate() {
        final PairedServerSim p = pair;
        pair = null;
        updateServerThreadRequirement();
        if (p != null) {
            p.level().getMinecraftServer().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    p.shutdown();
                }
            });
        }
    }

    public void onServerStopping(MinecraftServer server) {
        PairedServerSim p = pair;
        if (p == null || p.level().getMinecraftServer() != server) return;
        pair = null;
        updateServerThreadRequirement();
        p.shutdown();
    }

    private void ensurePair(SimulatorEntity e) {
        if (!pairedEnabled) return;
        if (pair != null && pair.level() != e.worldObj) {
            pair.shutdown();
            pair = null;
        }
        if (pair == null) {
            pair = PairedServerSim.create(e);
            if (pair != null) {
                pair.setStartPitch(startPitch);
                pair.setDamageEnabled(pairedDamage);
            }
        }
        updateServerThreadRequirement();
    }

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
        Vec3 start = pendingStart != null ? new Vec3(pendingStart.x, pendingStart.y, pendingStart.z) : new Vec3(player.posX, player.posY, player.posZ);
        Vec3dCore vel0 = pendingVelocity != null ? pendingVelocity : Vec3dCore.GROUND_REST_VELOCITY;
        Vec3 vel = new Vec3(vel0.x, vel0.y, vel0.z);
        float yaw = pendingYaw != null ? pendingYaw : 0.0F;
        return new SimulatorEntity(simWorld, player.getGameProfile(), start, vel, yaw);
    }

    @Override protected void resetEntity(SimulatorEntity e, StartResumeState resume) {
        ensurePair(e);
        if (pair != null) {
            pair.resetForFullRun(e, resume);
        } else {
            e.resetPlayer(resume);
        }
    }

    @Override
    protected StartResumeState describeResume(Checkpoint checkpoint) {
        SimulatorEntity.Checkpoint c = PairedCheckpoint.clientPart(checkpoint);
        if (c == null) return null;
        StartResumeState r = new StartResumeState();
        r.onGround = c.onGround;
        r.wallContact = c.isCollidedHorizontally;
        r.sprinting = c.sprinting;
        r.jumpCooldown = c.jumpTicks;
        r.airSprintFactor = c.jumpMovementFactor;
        if (c.isInWeb) {
            r.stuckMultiplier = new Vec3dCore(0.25, 0.05, 0.25);
        }
        if (c.sprintState != null) {
            r.sprintWindow = c.sprintState.sprintToggleTimer;
            r.sprintTicksLeft = c.sprintState.sprintingTicksLeft;
            if (c.sprintState.prevSneak) r.heldLastTick.add(InputRow.Key.SNEAK);
            if (c.sprintState.prevMoveForward > 0.0F) r.heldLastTick.add(InputRow.Key.W);
            if (c.sprintState.prevMoveForward < 0.0F) r.heldLastTick.add(InputRow.Key.S);
        }
        return r;
    }

    @Override protected void setInput(SimulatorEntity e, InputRow row) {
        lastRow = row;
        e.setInput(row);
    }

    @Override protected void teleportEntity(SimulatorEntity e, Vec3dCore pos, Vec3dCore velocity) {
        e.teleportRest(pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z);
    }

    @Override protected void applyYaw(SimulatorEntity e, float yaw) {
        e.rotationYaw += yaw;
    }

    @Override protected void setYawAbsolute(SimulatorEntity e, float yaw) {
        e.rotationYaw = yaw;
    }

    @Override
    protected void applyTickEffects(SimulatorEntity e, int speedAmplifier, int jumpBoostAmplifier) {
        e.clearActivePotions();
        if (speedAmplifier > 0) {
            e.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 2, clientAmplifier(speedAmplifier)));
        }
        if (jumpBoostAmplifier > 0) {
            e.addPotionEffect(new PotionEffect(Potion.jump.id, 2, clientAmplifier(jumpBoostAmplifier)));
        }
    }

    static int clientAmplifier(int level) {
        return (byte) (level - 1);
    }

    @Override
    protected void tickEntity(SimulatorEntity e) {
        ensurePair(e);
        if (pair == null) {
            preloadChunksAround(e);
            e.beginSubtickCapture();
            e.onUpdate();
            return;
        }
        pair.beginTick(e, lastRow);
        boolean ticked = false;
        try {
            preloadChunksAround(e);
            e.beginSubtickCapture();
            e.onUpdate();
            ticked = true;
        } finally {
            if (ticked) {
                pair.afterClientTick(e);
            } else {
                pair.abortTick();
            }
        }
    }

    /** chunkExists short-circuits the common case; provideChunk only fires on miss. */
    private static void preloadChunksAround(SimulatorEntity e) {
        if (!(e.worldObj instanceof WorldServer)) return;
        WorldServer serverWorld = (WorldServer) e.worldObj;
        int[] r = ChunkRange.around(e.posX, e.posZ);
        for (int cx = r[0]; cx <= r[2]; cx++) {
            for (int cz = r[1]; cz <= r[3]; cz++) {
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
    protected boolean isSprinting(SimulatorEntity e) {
        return e.isSprinting();
    }

    @Override
    protected float getMoveForward(SimulatorEntity e) {
        return e.moveForward;
    }

    @Override
    protected float getMoveStrafe(SimulatorEntity e) {
        return e.moveStrafing;
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
    protected double getCollisionAngleDegrees(SimulatorEntity e) {
        return Double.NaN;
    }

    @Override
    protected Medium getTickMedium(SimulatorEntity e) {
        return e.capturedTickMedium();
    }

    @Override
    protected double getTickGroundFriction(SimulatorEntity e) {
        return e.capturedTickGroundFriction();
    }

    @Override
    protected int getTickSoulsandCells(SimulatorEntity e) {
        return e.capturedTickSoulsandCells();
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
        SimulatorEntity.Checkpoint client = e.saveCheckpoint();
        return pair != null ? new PairedCheckpoint(client, pair.saveCheckpoint()) : client;
    }

    @Override
    protected void restoreCheckpoint(SimulatorEntity e, Checkpoint checkpoint) {
        if (checkpoint instanceof PairedCheckpoint && pair != null) {
            pair.restore(e, (PairedCheckpoint) checkpoint);
            return;
        }
        SimulatorEntity.Checkpoint client = PairedCheckpoint.clientPart(checkpoint);
        if (client != null) {
            e.restoreCheckpoint(client);
        }
    }
}
