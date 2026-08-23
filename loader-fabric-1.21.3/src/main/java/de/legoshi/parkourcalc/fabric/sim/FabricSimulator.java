package de.legoshi.parkourcalc.fabric.sim;

import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.anglesolver.Medium;
import de.legoshi.parkourcalc.core.sim.ChunkRange;
import de.legoshi.parkourcalc.core.sim.Checkpoint;
import de.legoshi.parkourcalc.core.sim.LazyEntitySimulator;
import de.legoshi.parkourcalc.core.sim.ServerSimEvent;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.fabric.sim.paired.PairedCheckpoint;
import de.legoshi.parkourcalc.fabric.sim.paired.PairedServerSim;
import net.minecraft.world.entity.player.Input;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.List;

public final class FabricSimulator extends LazyEntitySimulator<SimulatorEntity> {

    private boolean pairedEnabled;
    private boolean pairedDamage = true;
    private PairedServerSim pair;
    private InputRow lastRow;
    private float startPitch = PlaybackController.DEFAULT_PITCH;

    @Override
    public boolean supportsPairedSimulation() {
        return true;
    }

    @Override
    public void setPairedSimulation(boolean enabled) {
        if (!enabled && pair != null) {
            pair.shutdown();
            pair = null;
        }
        pairedEnabled = enabled;
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
        return pair == null ? List.of() : pair.drainEvents();
    }

    @Override
    public void onPassEnd() {
        if (pair != null) pair.endPass();
    }

    @Override
    public void onReplayStart(int startTick) {
        PairedServerSim p = pair;
        if (p == null) return;
        p.level().getServer().execute(() -> p.onReplayStart(startTick));
    }

    @Override
    public void onReplayEnd() {
        PairedServerSim p = pair;
        if (p == null) return;
        p.level().getServer().execute(p::onReplayEnd);
    }

    @Override
    protected void onInvalidate() {
        PairedServerSim p = pair;
        pair = null;
        if (p != null) {
            p.level().getServer().execute(p::shutdown);
        }
    }

    public void onServerStopping(MinecraftServer server) {
        PairedServerSim p = pair;
        if (p == null || p.level().getServer() != server) return;
        pair = null;
        p.shutdown();
    }

    private void ensurePair(SimulatorEntity e) {
        if (!pairedEnabled) return;
        if (pair != null && pair.level() != e.level()) {
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
    }

    @Override
    protected SimulatorEntity createEntity(Vec3dCore pendingStart, Vec3dCore pendingVelocity, Float pendingYaw) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel clientWorld = client.level;
        Player player = client.player;
        if (player == null || clientWorld == null) {
            throw new IllegalStateException("Cannot create simulator: player or world is null");
        }
        // On the server thread (SP): bind to ServerWorld so block reads hit it natively and
        // chunks can page in from disk. Off-thread (MP, or pre-sim setup): stay on ClientWorld.
        Level simWorld = clientWorld;
        MinecraftServer server = client.getSingleplayerServer();
        if (server != null && server.isSameThread()) {
            ServerLevel serverWorld = server.getLevel(clientWorld.dimension());
            if (serverWorld != null) {
                simWorld = serverWorld;
            }
        }
        Vec3 start = pendingStart != null ? new Vec3(pendingStart.x, pendingStart.y, pendingStart.z) : player.position();
        Vec3dCore vel0 = pendingVelocity != null ? pendingVelocity : Vec3dCore.GROUND_REST_VELOCITY;
        Vec3 vel = new Vec3(vel0.x, vel0.y, vel0.z);
        float yaw = pendingYaw != null ? pendingYaw : 0.0F;
        return new SimulatorEntity(simWorld, player.getGameProfile(), start, vel, yaw);
    }

    @Override protected void resetEntity(SimulatorEntity e, StartResumeState resume) {
        Player player = Minecraft.getInstance().player;
        if (player != null) e.getAttributes().assignBaseValues(player.getAttributes());
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
        r.wallContact = c.horizontalCollision;
        r.softWallContact = c.collidedSoftly;
        r.sprinting = c.sprinting;
        r.sprintWindow = c.ticksLeftToDoubleTapSprint;
        r.jumpCooldown = c.jumpingCooldown;
        if (c.movementMultiplier != null && c.movementMultiplier.lengthSqr() > 1.0E-7) {
            r.stuckMultiplier = new Vec3dCore(c.movementMultiplier.x, c.movementMultiplier.y, c.movementMultiplier.z);
        }
        Input held = c.playerInput;
        if (held != null) {
            if (held.forward()) r.heldLastTick.add(InputRow.Key.W);
            if (held.backward()) r.heldLastTick.add(InputRow.Key.S);
            if (held.left()) r.heldLastTick.add(InputRow.Key.A);
            if (held.right()) r.heldLastTick.add(InputRow.Key.D);
            if (held.jump()) r.heldLastTick.add(InputRow.Key.JUMP);
            if (held.shift()) r.heldLastTick.add(InputRow.Key.SNEAK);
            if (held.sprint()) r.heldLastTick.add(InputRow.Key.SPRINT);
        }
        return r;
    }

    @Override protected void setInput(SimulatorEntity e, InputRow row) {
        lastRow = row;
        e.input.setData(row);
    }

    @Override protected void applyYaw(SimulatorEntity e, float yaw) {
        e.setYRot(e.getYRot() + yaw);
    }

    @Override protected void setYawAbsolute(SimulatorEntity e, float yaw) {
        e.setYRot(yaw);
    }

    @Override
    protected void applyTickEffects(SimulatorEntity e, int speedAmplifier, int jumpBoostAmplifier) {
        e.removeAllEffects();
        if (speedAmplifier > 0) {
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 2, speedAmplifier - 1));
        }
        if (jumpBoostAmplifier > 0) {
            e.addEffect(new MobEffectInstance(MobEffects.JUMP, 2, jumpBoostAmplifier - 1));
        }
    }

    @Override
    protected void tickEntity(SimulatorEntity e) {
        ensurePair(e);
        if (pair == null) {
            preloadChunksAround(e);
            e.beginSubtickCapture();
            e.tick();
            return;
        }
        pair.beginTick(e, lastRow);
        boolean ticked = false;
        try {
            preloadChunksAround(e);
            e.beginSubtickCapture();
            e.tick();
            ticked = true;
        } finally {
            if (ticked) {
                pair.afterClientTick(e);
            } else {
                pair.abortTick();
            }
        }
    }

    /** isChunkLoaded short-circuits the common case; getChunk(FULL, true) only fires on miss. */
    private static void preloadChunksAround(SimulatorEntity e) {
        Level world = e.level();
        if (!(world instanceof ServerLevel serverWorld)) return;
        Vec3 pos = e.position();
        int[] r = ChunkRange.around(pos.x, pos.z);
        for (int cx = r[0]; cx <= r[2]; cx++) {
            for (int cz = r[1]; cz <= r[3]; cz++) {
                if (!serverWorld.hasChunk(cx, cz)) {
                    serverWorld.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, true);
                }
            }
        }
    }

    @Override
    protected String formatDebugState(SimulatorEntity e, int tickIndex) {
        MobEffectInstance spd = e.getEffect(MobEffects.MOVEMENT_SPEED);
        MobEffectInstance jmp = e.getEffect(MobEffects.JUMP);
        double mvSp = e.getAttributeValue(Attributes.MOVEMENT_SPEED);
        Vec3 pos = e.position();
        Vec3 vel = e.getDeltaMovement();
        return "[PC-STATE sim ] t=" + tickIndex
                + " pos=" + pos.x + "," + pos.y + "," + pos.z
                + " mot=" + vel.x + "," + vel.y + "," + vel.z
                + " yaw=" + e.getYRot()
                + " onG=" + e.onGround()
                + " spr=" + e.isSprinting()
                + " sne=" + e.isShiftKeyDown()
                + " colH=" + e.horizontalCollision
                + " mvF=" + e.zza
                + " mvS=" + e.xxa
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
        Vec3 p = e.position();
        return new Vec3dCore(p.x, p.y, p.z);
    }

    @Override
    protected boolean isOnGround(SimulatorEntity e) {
        return e.onGround();
    }

    @Override
    protected boolean isSneaking(SimulatorEntity e) {
        return e.isShiftKeyDown();
    }

    @Override
    protected boolean isSprinting(SimulatorEntity e) {
        return e.isSprinting();
    }

    @Override
    protected float getMoveForward(SimulatorEntity e) {
        return e.zza;
    }

    @Override
    protected float getMoveStrafe(SimulatorEntity e) {
        return e.xxa;
    }

    @Override
    protected boolean isWallCollision(SimulatorEntity e) {
        return e.horizontalCollision;
    }

    @Override
    protected Vec3dCore getVelocity(SimulatorEntity e) {
        Vec3 v = e.getDeltaMovement();
        return new Vec3dCore(v.x, v.y, v.z);
    }

    @Override
    protected boolean isSoftCollision(SimulatorEntity e) {
        return e.minorHorizontalCollision;
    }

    @Override
    protected double getCollisionAngleDegrees(SimulatorEntity e) {
        return e.getLastCollisionAngleDegrees();
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
        return e.getYRot();
    }

    @Override
    protected Vec3dCore getStart(SimulatorEntity e) {
        Vec3 p = e.startPosition;
        return new Vec3dCore(p.x, p.y, p.z);
    }

    @Override
    protected void setStart(SimulatorEntity e, Vec3dCore pos) {
        e.startPosition = new Vec3(pos.x, pos.y, pos.z);
    }

    @Override
    protected Vec3dCore getStartVel(SimulatorEntity e) {
        Vec3 v = e.startVelocity;
        return new Vec3dCore(v.x, v.y, v.z);
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
        if (checkpoint instanceof PairedCheckpoint paired && pair != null) {
            pair.restore(e, paired);
            return;
        }
        SimulatorEntity.Checkpoint client = PairedCheckpoint.clientPart(checkpoint);
        if (client != null) {
            e.restoreCheckpoint(client);
        }
    }
}
