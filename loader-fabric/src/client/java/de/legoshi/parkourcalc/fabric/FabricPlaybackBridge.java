package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.fabric.mixin.LocalPlayerAccessor;
import de.legoshi.parkourcalc.fabric.mixin.KeyMappingAccessor;
import de.legoshi.parkourcalc.fabric.mixin.PlayerAccessor;
import de.legoshi.parkourcalc.fabric.sim.GhostPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Options;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.UUID;

public final class FabricPlaybackBridge implements PlaybackBridge {

    @Override
    public boolean isGamePaused() {
        return Minecraft.getInstance().isPaused();
    }

    private static final int EFFECT_DURATION_TICKS = 20000;
    private static final int GHOST_ENTITY_ID = -2100000000;

    private final InputRow currentRow = new InputRow();
    private ClientInput originalInput;
    private boolean ghostMode;
    private GhostPlayerEntity ghost;
    private final java.util.List<ReplaySample> replaySamples = new java.util.ArrayList<>();

    private record ReplaySample(int tick, Vec3 pos, Vec3 vel, boolean onGround, double fallDistance, float health,
                                Vec3 serverVel, String serverDesc) {
    }

    InputRow getCurrentRow() {
        return currentRow;
    }

    GhostPlayerEntity ghostEntity() {
        return ghost;
    }

    void installPlaybackInput(LocalPlayer player) {
        if (originalInput != null) return;
        originalInput = player.input;
        player.input = ghostMode ? new FrozenClientInput() : new PlaybackInput(this);
    }

    void restorePlaybackInput(LocalPlayer player) {
        if (originalInput == null) return;
        if (player.input instanceof PlaybackInput || player.input instanceof FrozenClientInput) {
            player.input = originalInput;
        }
        originalInput = null;
    }

    void resetInputOverride() {
        originalInput = null;
    }

    @Override
    public boolean isSingleplayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) return false;
        IntegratedServer s = mc.getSingleplayerServer();
        if (s == null) return false;
        return !s.isPublished();
    }

    @Override
    public boolean supportsMultiplayerPlayback() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null;
    }

    private void beginGhostPlayback(Vec3dCore pos, Vec3dCore vel, float yaw, de.legoshi.parkourcalc.core.sim.Checkpoint carry) {
        ghostMode = true;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer client = mc.player;
        if (level == null || client == null) return;
        removeGhostEntity();
        GhostPlayerEntity g = new GhostPlayerEntity(level, client.getGameProfile());
        g.setId(GHOST_ENTITY_ID);
        g.setInput(currentRow);
        g.absSnapTo(pos.x, pos.y, pos.z, yaw, client.getXRot());
        g.setYBodyRot(yaw);
        g.yBodyRotO = yaw;
        g.setYHeadRot(yaw);
        g.yHeadRotO = yaw;
        g.setDeltaMovement(vel.x, vel.y, vel.z);
        if (carry != null) {
            g.applyCarry(carry);
        } else {
            g.setOnGround(true);
            g.fallDistance = 0.0;
        }
        if (!FabricParkourCalculator.getSettings().pairedSimulation) {
            g.fallDistance = 0.0;
        }
        g.setOldPosAndRot();
        g.copyModelCustomisationFrom(client);
        level.addEntity(g);
        ghost = g;
        client.xxa = 0.0F;
        client.zza = 0.0F;
        client.setJumping(false);
        mc.setCameraEntity(g);
    }

    private ServerPlayer serverSideSelf() {
        if (ghostMode) return null;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer client = mc.player;
        IntegratedServer server = mc.getSingleplayerServer();
        if (client == null || server == null) return null;
        return server.getPlayerList().getPlayer(client.getUUID());
    }

    @Override
    public void beginReplayLockstep() {
        if (!FabricParkourCalculator.getSettings().lockstepReplay) return;
        if (!isSingleplayer()) return;
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        ReplayLockstep.engageForStart(server);
    }

    @Override
    public void releaseReplayLockstep() {
        ReplayLockstep.releaseStartArm();
    }

    @Override
    public void beginPlaybackCapture() {
        replaySamples.clear();
    }

    @Override
    public void capturePlaybackSample(int tickIndex) {
        net.minecraft.world.entity.player.Player subject = ghostMode ? ghost : Minecraft.getInstance().player;
        if (subject == null) return;
        ServerPlayer sp = serverSideSelf();
        replaySamples.add(new ReplaySample(tickIndex, subject.position(), subject.getDeltaMovement(),
                subject.onGround(), subject.fallDistance, subject.getHealth(),
                sp == null ? Vec3.ZERO : sp.getDeltaMovement(),
                sp == null ? "realSrv=unavailable" : describeRealServer(sp)));
        if (de.legoshi.parkourcalc.core.DebugFlags.PAIRED_DIAGNOSTICS && tickIndex <= 4) {
            System.out.println("[PC-NET] tick T" + (tickIndex + 1)
                    + " pos=" + subject.position() + " vel=" + subject.getDeltaMovement()
                    + " fall=" + subject.fallDistance + " hp=" + subject.getHealth());
        }
    }

    private static String describeRealServer(ServerPlayer sp) {
        Vec3 v = sp.getDeltaMovement();
        net.minecraft.server.network.ServerGamePacketListenerImpl conn = sp.connection;
        return "realSrv=" + v.x + "," + v.y + "," + v.z
                + " fall=" + sp.fallDistance
                + " g=" + sp.onGround()
                + " vCollB=" + sp.verticalCollisionBelow
                + " spr=" + sp.isSprinting()
                + " sneak=" + sp.isShiftKeyDown()
                + " hurtMarked=" + sp.hurtMarked
                + " invuln=" + sp.invulnerableTime
                + " hurtTime=" + sp.hurtTime
                + " fire=" + sp.getRemainingFireTicks()
                + " in=" + de.legoshi.parkourcalc.fabric.sim.paired.PairedServerSim.describeInput(sp.getLastClientInput())
                + " tp=" + (conn.awaitingPositionFromClient != null)
                + " tpId=" + conn.awaitingTeleport
                + " recv=" + conn.receivedMovePacketCount + "/" + conn.knownMovePacketCount
                + " aboveG=" + conn.aboveGroundTickCount
                + " lastGood=" + conn.lastGoodX + "," + conn.lastGoodY + "," + conn.lastGoodZ;
    }

    private static void printAccumulatorLine(ReplaySample sample) {
        System.out.println("[PC-ACC] T" + (sample.tick() + 1)
                + " " + sample.serverDesc()
                + " | " + de.legoshi.parkourcalc.fabric.sim.paired.PairedCheckpoint.describeServer(
                        FabricParkourCalculator.simCheckpoint(sample.tick() + 1)));
    }

    private static boolean matchesPair(Vec3 real, int checkpointIndex) {
        Vec3 pair = de.legoshi.parkourcalc.fabric.sim.paired.PairedCheckpoint.serverVelocity(
                FabricParkourCalculator.simCheckpoint(checkpointIndex));
        return pair == null || real.distanceToSqr(pair) <= 1.0e-18;
    }

    private void reportAccumulator() {
        int first = -1;
        for (ReplaySample sample : replaySamples) {
            if (matchesPair(sample.serverVel(), sample.tick() + 1)
                    || matchesPair(sample.serverVel(), sample.tick())) {
                continue;
            }
            first = sample.tick();
            break;
        }
        if (first < 0) {
            System.out.println("[PC-ACC] server accumulator matches the pair across " + replaySamples.size()
                    + " ticks (phase-tolerant)");
            return;
        }
        System.out.println("[PC-ACC] server accumulator first differs at T" + (first + 1));
        for (ReplaySample sample : replaySamples) {
            if (sample.tick() < first - 2 || sample.tick() > first + 6) continue;
            printAccumulatorLine(sample);
        }
    }

    @Override
    public void finishPlaybackCapture() {
        if (replaySamples.isEmpty()) return;
        java.util.List<de.legoshi.parkourcalc.core.sim.TickState> states = FabricParkourCalculator.simStates();
        int firstDiverged = -1;
        for (ReplaySample sample : replaySamples) {
            int idx = sample.tick() + 1;
            if (idx < 0 || idx >= states.size()) continue;
            de.legoshi.parkourcalc.core.sim.TickState sim = states.get(idx);
            double d = Math.max(Math.abs(sample.pos().x - sim.position.x),
                    Math.max(Math.abs(sample.pos().y - sim.position.y), Math.abs(sample.pos().z - sim.position.z)));
            if (d > 1.0e-6) {
                firstDiverged = sample.tick();
                break;
            }
        }
        reportAccumulator();
        if (firstDiverged < 0) {
            System.out.println("[PC-REPLAY] no divergence across " + replaySamples.size() + " replayed ticks (eps 1e-6)");
            replaySamples.clear();
            return;
        }
        System.out.println("[PC-REPLAY] first divergence at T" + (firstDiverged + 1));
        for (ReplaySample sample : replaySamples) {
            if (sample.tick() < firstDiverged - 3 || sample.tick() > firstDiverged + 8) continue;
            int idx = sample.tick() + 1;
            String simPart;
            if (idx >= 0 && idx < states.size()) {
                de.legoshi.parkourcalc.core.sim.TickState sim = states.get(idx);
                simPart = "sim=" + sim.position.x + "," + sim.position.y + "," + sim.position.z
                        + " v=" + sim.velocity.x + "," + sim.velocity.y + "," + sim.velocity.z
                        + " g=" + sim.onGround;
            } else {
                simPart = "sim=out-of-range";
            }
            System.out.println("[PC-REPLAY] T" + (sample.tick() + 1)
                    + " real=" + sample.pos().x + "," + sample.pos().y + "," + sample.pos().z
                    + " v=" + sample.vel().x + "," + sample.vel().y + "," + sample.vel().z
                    + " g=" + sample.onGround()
                    + " fall=" + sample.fallDistance()
                    + " hp=" + sample.health()
                    + " | " + simPart);
            printAccumulatorLine(sample);
        }
        replaySamples.clear();
    }

    void syncFrozenPlayerToServer() {
        if (!ghostMode) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.isPassenger()) return;
        LocalPlayerAccessor acc = (LocalPlayerAccessor) p;
        double dx = p.getX() - acc.pkc$getXLast();
        double dy = p.getY() - acc.pkc$getYLast();
        double dz = p.getZ() - acc.pkc$getZLast();
        acc.pkc$setPositionReminder(acc.pkc$getPositionReminder() + 1);
        boolean moved = Mth.lengthSquared(dx, dy, dz) > Mth.square(2.0E-4) || acc.pkc$getPositionReminder() >= 20;
        if (moved) {
            p.connection.send(new ServerboundMovePlayerPacket.Pos(p.position(), p.onGround(), p.horizontalCollision));
            acc.pkc$setXLast(p.getX());
            acc.pkc$setYLast(p.getY());
            acc.pkc$setZLast(p.getZ());
            acc.pkc$setPositionReminder(0);
        } else if (acc.pkc$getLastOnGround() != p.onGround() || acc.pkc$getLastHorizontalCollision() != p.horizontalCollision) {
            p.connection.send(new ServerboundMovePlayerPacket.StatusOnly(p.onGround(), p.horizontalCollision));
        }
        acc.pkc$setLastOnGround(p.onGround());
        acc.pkc$setLastHorizontalCollision(p.horizontalCollision);
    }

    void endGhostPlayback() {
        Minecraft mc = Minecraft.getInstance();
        if (ghost != null && mc.getCameraEntity() == ghost && mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        removeGhostEntity();
        ghostMode = false;
    }

    private void removeGhostEntity() {
        GhostPlayerEntity g = ghost;
        ghost = null;
        if (g == null) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && g.level() == level) {
            level.removeEntity(GHOST_ENTITY_ID, Entity.RemovalReason.DISCARDED);
        }
    }

    @Override
    public void teleport(Vec3dCore pos, Vec3dCore vel, float yaw, de.legoshi.parkourcalc.core.sim.Checkpoint carry) {
        if (!isSingleplayer()) {
            beginGhostPlayback(pos, vel, yaw, carry);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer client = mc.player;
        if (client == null) return;
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        UUID uuid = client.getUUID();
        Vec3 pendingStomp = de.legoshi.parkourcalc.fabric.sim.paired.PairedCheckpoint.pendingVelocity(carry);
        Vec3 startVel = pendingStomp != null ? pendingStomp : new Vec3(vel.x, vel.y, vel.z);
        if (pendingStomp != null && de.legoshi.parkourcalc.core.DebugFlags.PAIRED_DIAGNOSTICS) {
            System.out.println("[PC-NET] restart applied pending velocity flush " + pendingStomp);
        }
        server.execute(() -> {
            try {
                ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
                if (sp == null) return;
                // EntityPosition overload carries velocity in the teleport packet itself.
                // The 5-arg overload zeroes velocity and the followup setVelocity+velocityModified
                // path fires a SetEntityMotion packet that arrives ~1 tick late and stomps the
                // player mid-playback.
                sp.connection.teleport(
                    new PositionMoveRotation(new Vec3(pos.x, pos.y, pos.z), startVel, yaw, sp.getXRot()),
                    Collections.emptySet()
                );
                de.legoshi.parkourcalc.fabric.sim.paired.PairedCheckpoint.applyRestartState(sp, carry);
                if (carry instanceof de.legoshi.parkourcalc.fabric.sim.paired.PairedCheckpoint) {
                    de.legoshi.parkourcalc.fabric.sim.paired.RestartSettle.arm(uuid, carry);
                }
            } finally {
                ReplayLockstep.onRestartHopComplete();
            }
        });
        client.absSnapTo(pos.x, pos.y, pos.z, yaw, client.getXRot());
        client.setYBodyRot(yaw);
        client.yBodyRotO = yaw;
        client.setYHeadRot(yaw);
        client.yHeadRotO = yaw;
        client.setDeltaMovement(startVel);
        if (carry != null) {
            de.legoshi.parkourcalc.fabric.sim.SimulatorEntity.applyCheckpoint(client, carry);
        } else {
            client.setOnGround(true);
            client.fallDistance = 0.0;
        }
        if (!FabricParkourCalculator.getSettings().pairedSimulation) {
            client.fallDistance = 0.0;
        }
        // Suppress the player tick's position packet until the server's requestTeleport
        // arms its teleport-pending state, otherwise the client races and trips moved-wrongly.
        LocalPlayerAccessor acc = (LocalPlayerAccessor) client;
        acc.pkc$setXLast(pos.x);
        acc.pkc$setYLast(pos.y);
        acc.pkc$setZLast(pos.z);
        acc.pkc$setYRotLast(yaw);
        acc.pkc$setXRotLast(client.getXRot());
        acc.pkc$setPositionReminder(0);
        client.yBob = yaw;
        client.yBobO = yaw;
        client.xBob = client.getXRot();
        client.xBobO = client.getXRot();
        if (de.legoshi.parkourcalc.core.DebugFlags.PAIRED_DIAGNOSTICS) {
            System.out.println("[PC-NET] teleport snap pos=" + pos.x + "," + pos.y + "," + pos.z
                    + " vel=" + vel.x + "," + vel.y + "," + vel.z
                    + " carry=" + (carry == null ? "none" : carry.getClass().getSimpleName())
                    + " clientFall=" + client.fallDistance + " clientOnGround=" + client.onGround()
                    + " clientFire=" + client.getRemainingFireTicks() + " sharedFlagFire=" + client.isOnFire());
        }
    }

    @Override
    public void setKey(InputRow.Key key, boolean pressed) {
        currentRow.setKeyActive(key, pressed);
        if (ghostMode) return;
        KeyMapping kb = bindFor(key);
        if (kb == null) return;
        kb.setDown(pressed);
        if (pressed && isClickKey(key)) {
            KeyMappingAccessor acc = (KeyMappingAccessor) kb;
            acc.pkc$setClickCount(acc.pkc$getClickCount() + 1);
        }
    }

    private static boolean isClickKey(InputRow.Key key) {
        return key == InputRow.Key.LEFT_CLICK || key == InputRow.Key.RIGHT_CLICK;
    }

    @Override
    public void setYaw(float absoluteYaw) {
        if (ghostMode) {
            if (ghost == null) return;
            ghost.setYRot(absoluteYaw);
            ghost.yRotO = absoluteYaw;
            return;
        }
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        p.setYRot(absoluteYaw);
        p.yRotO = absoluteYaw;
        p.yBob = absoluteYaw;
        p.yBobO = absoluteYaw;
    }

    @Override
    public void setHeadYaw(float absoluteYaw) {
        if (ghostMode) {
            if (ghost == null) return;
            ghost.setYHeadRot(absoluteYaw);
            return;
        }
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        p.setYHeadRot(absoluteYaw);
    }

    @Override
    public void setPitch(float absolutePitch) {
        if (ghostMode) {
            if (ghost == null) return;
            ghost.setXRot(absolutePitch);
            ghost.xRotO = absolutePitch;
            return;
        }
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        p.setXRot(absolutePitch);
        p.xRotO = absolutePitch;
        p.xBob = absolutePitch;
        p.xBobO = absolutePitch;
    }

    @Override
    public void releaseAllKeys() {
        for (InputRow.Key k : InputRow.Key.values()) {
            setKey(k, false);
        }
    }

    @Override
    public void suppressFlight() {
        if (ghostMode) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        ((PlayerAccessor) p).pkc$setJumpTriggerTime(0);
        Abilities abilities = p.getAbilities();
        if (abilities.flying) {
            abilities.flying = false;
            p.onUpdateAbilities();
        }
    }

    @Override
    public void closeUI() {
        FabricParkourCalculator.closeOverlay();
    }

    @Override
    public void applyEffects(int speedAmplifier, int jumpBoostAmplifier) {
        if (ghostMode) {
            if (ghost == null) return;
            ghost.removeAllEffects();
            if (speedAmplifier > 0) {
                ghost.addEffect(new MobEffectInstance(MobEffects.SPEED, EFFECT_DURATION_TICKS, speedAmplifier - 1, false, false, true));
            }
            if (jumpBoostAmplifier > 0) {
                ghost.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, EFFECT_DURATION_TICKS, jumpBoostAmplifier - 1, false, false, true));
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer client = mc.player;
        if (client == null) return;
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        UUID uuid = client.getUUID();
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(uuid);
            if (sp == null) return;
            sp.removeEffect(MobEffects.SPEED);
            sp.removeEffect(MobEffects.JUMP_BOOST);
            if (speedAmplifier > 0) {
                sp.addEffect(new MobEffectInstance(MobEffects.SPEED, EFFECT_DURATION_TICKS, speedAmplifier - 1, false, false, true));
            }
            if (jumpBoostAmplifier > 0) {
                sp.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, EFFECT_DURATION_TICKS, jumpBoostAmplifier - 1, false, false, true));
            }
        });
    }

    @Override
    public void setHotbarSlot(int slotZeroBased) {
        if (ghostMode) return;
        if (slotZeroBased < 0 || slotZeroBased > 8) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        p.getInventory().setSelectedSlot(slotZeroBased);
    }

    @Override
    public void dumpPlayerState(int tickIndex) {
        net.minecraft.world.entity.player.Player p = ghost != null ? ghost : Minecraft.getInstance().player;
        if (p == null) return;
        MobEffectInstance spd = p.getEffect(MobEffects.SPEED);
        MobEffectInstance jmp = p.getEffect(MobEffects.JUMP_BOOST);
        double mvSp = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        System.out.println("[PC-STATE play] t=" + tickIndex
                + " pos=" + p.getX() + "," + p.getY() + "," + p.getZ()
                + " mot=" + p.getDeltaMovement().x + "," + p.getDeltaMovement().y + "," + p.getDeltaMovement().z
                + " yaw=" + p.getYRot()
                + " onG=" + p.onGround()
                + " spr=" + p.isSprinting()
                + " sne=" + p.isShiftKeyDown()
                + " colH=" + p.horizontalCollision
                + " mvF=" + p.zza
                + " mvS=" + p.xxa
                + " spdAmp=" + (spd == null ? -1 : spd.getAmplifier())
                + " jmpAmp=" + (jmp == null ? -1 : jmp.getAmplifier())
                + " mvSpeed=" + mvSp);
    }

    private static KeyMapping bindFor(InputRow.Key key) {
        Options o = Minecraft.getInstance().options;
        return switch (key) {
            case W -> o.keyUp;
            case S -> o.keyDown;
            case A -> o.keyLeft;
            case D -> o.keyRight;
            case JUMP -> o.keyJump;
            case SNEAK -> o.keyShift;
            case SPRINT -> o.keySprint;
            case LEFT_CLICK -> o.keyAttack;
            case RIGHT_CLICK -> o.keyUse;
        };
    }
}
