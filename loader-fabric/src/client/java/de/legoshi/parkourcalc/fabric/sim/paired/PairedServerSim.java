package de.legoshi.parkourcalc.fabric.sim.paired;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import de.legoshi.parkourcalc.core.DebugFlags;
import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.sim.ServerSimEvent;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.fabric.sim.SimulatorEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.LpVec3;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class PairedServerSim {

    private static final UUID PROFILE_UUID = UUID.nameUUIDFromBytes("pkc-paired-sim".getBytes(StandardCharsets.UTF_8));
    private static final String PROFILE_NAME = "PKCPairedSim";
    private static final int RIGHT_CLICK_DELAY_TICKS = 4;
    private static final int WARMUP_SERVER_TICKS = 2;

    private final ServerLevel level;
    private final PairedServerPlayer sp;
    private final PairedGameHandler handler;
    private final WorldJournal journal;

    private final List<Packet<?>> pendingClientbound = new ArrayList<>();
    private final List<Packet<?>> serverbound = new ArrayList<>();
    private final List<ServerSimEvent> events = new ArrayList<>();

    private int tickIndex;
    private float startPitch = PlaybackController.DEFAULT_PITCH;
    private float pitch = PlaybackController.DEFAULT_PITCH;

    private double xLast;
    private double yLast;
    private double zLast;
    private float yRotLast;
    private float xRotLast;
    private int positionReminder;
    private boolean lastOnGround;
    private boolean lastHorizontalCollision;
    private Input lastSentInput = Input.EMPTY;
    private boolean lastSprinting;
    private boolean prevRightClick;
    private int rightClickDelay;
    private int useSequence;
    private int journalSizeAtTickStart;
    private boolean shutdownDone;
    private boolean damageEnabled = true;

    private PairedServerSim(ServerLevel level, PairedServerPlayer sp, PairedGameHandler handler) {
        this.level = level;
        this.sp = sp;
        this.handler = handler;
        this.journal = new WorldJournal(level);
    }

    public static PairedServerSim create(SimulatorEntity clientEntity) {
        Level lvl = clientEntity.level();
        if (!(lvl instanceof ServerLevel serverLevel)) return null;
        MinecraftServer server = serverLevel.getServer();
        GameProfile profile = new GameProfile(PROFILE_UUID, PROFILE_NAME);
        PairedServerPlayer sp = new PairedServerPlayer(server, serverLevel, profile);
        PairedGameHandler handler = new PairedGameHandler(server, sp);
        PairedServerSim sim = new PairedServerSim(serverLevel, sp, handler);
        handler.setPacketSink(sim.pendingClientbound::add);
        sp.setDamageSink(sim::onDamageRuled);
        sp.setFallRulingSink(sim::onLandingRuled);
        sp.setDamageGate(sim::isDamageEnabled);
        handler.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return sim;
    }

    public ServerLevel level() {
        return level;
    }

    public void setStartPitch(float startPitch) {
        this.startPitch = startPitch;
    }

    public void setDamageEnabled(boolean enabled) {
        this.damageEnabled = enabled;
    }

    public boolean isDamageEnabled() {
        return damageEnabled;
    }

    public void resetForFullRun(SimulatorEntity e, StartResumeState resume) {
        journal.revertTo(0);
        pendingClientbound.clear();
        serverbound.clear();
        tickIndex = 0;
        pitch = startPitch;
        journal.beginWindow(0, true);
        try {
            e.resetPlayer(resume);
        } finally {
            journal.endWindow();
        }
        resetServerSide(e);
    }

    private void resetServerSide(SimulatorEntity e) {
        ServerPlayer real = level.getServer().getPlayerList().getPlayer(e.getUUID());
        if (real != null) sp.getAttributes().assignBaseValues(real.getAttributes());
        sp.absSnapTo(e.getX(), e.getY(), e.getZ(), e.getYRot(), 0.0F);
        sp.setDeltaMovement(e.getDeltaMovement());
        sp.fallDistance = 0.0;
        sp.setOnGround(false);
        sp.verticalCollisionBelow = false;
        sp.invulnerableTime = 0;
        sp.hurtTime = 0;
        sp.hurtMarked = false;
        sp.clearFire();
        sp.setShiftKeyDown(false);
        sp.setSprinting(false);
        handler.resetPosition();
        handler.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(
                e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot(), false, false));
        for (int i = 0; i < WARMUP_SERVER_TICKS; i++) {
            sp.tick();
            tickHandler();
        }
        sp.hurtMarked = false;
        handler.resetPosition();
        handler.resetFlyingTicks();
        handler.awaitingPositionFromClient = null;
        handler.awaitingTeleport = 0;
        handler.awaitingTeleportTime = 0;
        handler.clientIsFloating = false;
        handler.receivedMovePacketCount = 0;
        handler.knownMovePacketCount = 0;
        handler.tickCount = 0;
        xLast = e.getX();
        yLast = e.getY();
        zLast = e.getZ();
        yRotLast = e.getYRot();
        xRotLast = e.getXRot();
        positionReminder = 0;
        lastOnGround = e.onGround();
        lastHorizontalCollision = false;
        lastSentInput = Input.EMPTY;
        lastSprinting = false;
        prevRightClick = false;
        rightClickDelay = 0;
        useSequence = 0;
    }

    public void abortTick() {
        serverbound.clear();
        journal.endWindow();
    }

    public void beginTick(SimulatorEntity e, InputRow row) {
        journal.beginWindow(tickIndex, true);
        journalSizeAtTickStart = journal.size();
        if (!pendingClientbound.isEmpty()) {
            List<Packet<?>> toApply = new ArrayList<>(pendingClientbound);
            pendingClientbound.clear();
            for (Packet<?> packet : toApply) {
                if (packet instanceof ClientboundSetEntityMotionPacket motion) {
                    if (motion.id() != sp.getId()) continue;
                    Vec3 applied = quantizeVelocity(motion.movement());
                    e.setDeltaMovement(applied);
                    addEvent(ServerSimEvent.Kind.VELOCITY_SET, formatVec(applied));
                    if (DebugFlags.PAIRED_DIAGNOSTICS) {
                        System.out.println("[PC-PAIR] T" + (tickIndex + 1) + " velocity applied " + formatVec(applied));
                    }
                } else if (packet instanceof ClientboundPlayerPositionPacket position) {
                    applyPositionCorrection(e, position);
                } else if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
                    serverbound.add(new ServerboundKeepAlivePacket(keepAlive.getId()));
                }
            }
        }
        if (row != null) {
            pitch = PlaybackController.applyPitch(pitch, row);
        }
        synthesizeUseItem(e, row);
    }

    private void applyPositionCorrection(SimulatorEntity e, ClientboundPlayerPositionPacket packet) {
        PositionMoveRotation current = PositionMoveRotation.of(e);
        PositionMoveRotation next = PositionMoveRotation.calculateAbsolute(current, packet.change(), packet.relatives());
        e.setPos(next.position());
        e.setDeltaMovement(next.deltaMovement());
        e.setYRot(next.yRot());
        e.setXRot(next.xRot());
        e.setOldPosAndRot();
        addEvent(ServerSimEvent.Kind.POSITION_CORRECTION, "to " + formatVec(next.position()));
        if (DebugFlags.PAIRED_DIAGNOSTICS) {
            System.out.println("[PC-PAIR] T" + (tickIndex + 1) + " lagback applied to " + formatVec(next.position()));
        }
        serverbound.add(new ServerboundAcceptTeleportationPacket(packet.id()));
        serverbound.add(new ServerboundMovePlayerPacket.PosRot(
                e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot(), false, false));
    }

    public void afterClientTick(SimulatorEntity e) {
        try {
            synthesizeInputPacket(e);
            synthesizeSprintCommand(e);
            synthesizeMovePacket(e);
            drainServerbound();
            reportReplayMismatch(e);
            sp.tick();
            flushHurtMarked();
            tickHandler();
            emitBlockChangeEvents();
        } finally {
            journal.endWindow();
            tickIndex++;
        }
    }

    private void synthesizeInputPacket(SimulatorEntity e) {
        Input current = e.input.keyPresses;
        if (!current.equals(lastSentInput)) {
            serverbound.add(new ServerboundPlayerInputPacket(current));
            lastSentInput = current;
        }
    }

    private void synthesizeSprintCommand(SimulatorEntity e) {
        boolean sprinting = e.isSprinting();
        if (sprinting != lastSprinting) {
            serverbound.add(new ServerboundPlayerCommandPacket(sp, sprinting
                    ? ServerboundPlayerCommandPacket.Action.START_SPRINTING
                    : ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
            lastSprinting = sprinting;
        }
    }

    private void synthesizeMovePacket(SimulatorEntity e) {
        double dx = e.getX() - xLast;
        double dy = e.getY() - yLast;
        double dz = e.getZ() - zLast;
        double dYRot = e.getYRot() - yRotLast;
        double dXRot = e.getXRot() - xRotLast;
        positionReminder++;
        boolean move = Mth.lengthSquared(dx, dy, dz) > Mth.square(2.0E-4) || positionReminder >= 20;
        boolean rot = dYRot != 0.0 || dXRot != 0.0;
        boolean onGround = e.onGround();
        boolean horizontalCollision = e.horizontalCollision;
        if (move && rot) {
            serverbound.add(new ServerboundMovePlayerPacket.PosRot(
                    e.position(), e.getYRot(), e.getXRot(), onGround, horizontalCollision));
        } else if (move) {
            serverbound.add(new ServerboundMovePlayerPacket.Pos(e.position(), onGround, horizontalCollision));
        } else if (rot) {
            serverbound.add(new ServerboundMovePlayerPacket.Rot(
                    e.getYRot(), e.getXRot(), onGround, horizontalCollision));
        } else if (lastOnGround != onGround || lastHorizontalCollision != horizontalCollision) {
            serverbound.add(new ServerboundMovePlayerPacket.StatusOnly(onGround, horizontalCollision));
        }
        if (move) {
            xLast = e.getX();
            yLast = e.getY();
            zLast = e.getZ();
            positionReminder = 0;
        }
        if (rot) {
            yRotLast = e.getYRot();
            xRotLast = e.getXRot();
        }
        lastOnGround = onGround;
        lastHorizontalCollision = horizontalCollision;
    }

    private void synthesizeUseItem(SimulatorEntity e, InputRow row) {
        boolean rightClick = row != null && row.isKeyActive(InputRow.Key.RIGHT_CLICK);
        if (rightClickDelay > 0) rightClickDelay--;
        boolean fire = rightClick && (!prevRightClick || rightClickDelay == 0);
        prevRightClick = rightClick;
        if (!fire) return;
        rightClickDelay = RIGHT_CLICK_DELAY_TICKS;
        Vec3 eye = new Vec3(e.getX(), e.getEyeY(), e.getZ());
        Vec3 dir = Vec3.directionFromRotation(pitch, e.getYRot());
        double range = e.blockInteractionRange();
        HitResult hit = level.clip(new ClipContext(eye, eye.add(dir.scale(range)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, e));
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            addEvent(ServerSimEvent.Kind.INTERACTION_REJECTED, "no block in reach");
            return;
        }
        drainServerbound();
        int sizeBefore = journal.size();
        handler.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, blockHit, ++useSequence));
        if (journal.size() == sizeBefore) {
            addEvent(ServerSimEvent.Kind.INTERACTION_REJECTED, "use had no effect");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void drainServerbound() {
        for (Packet<?> packet : serverbound) {
            ((Packet) packet).handle(handler);
        }
        serverbound.clear();
    }

    private void tickHandler() {
        int before = handler.tickCount;
        handler.tick();
        if (handler.tickCount == before) {
            handler.resetPosition();
            sp.xo = sp.getX();
            sp.yo = sp.getY();
            sp.zo = sp.getZ();
            sp.doTick();
            sp.absSnapTo(handler.firstGoodX, handler.firstGoodY, handler.firstGoodZ, sp.getYRot(), sp.getXRot());
            handler.tickCount = before + 1;
            handler.knownMovePacketCount = handler.receivedMovePacketCount;
        }
    }

    private void reportReplayMismatch(SimulatorEntity e) {
        if (!DebugFlags.PAIRED_DIAGNOSTICS) return;
        double dx = sp.getX() - e.getX();
        double dy = sp.getY() - e.getY();
        double dz = sp.getZ() - e.getZ();
        if (Math.abs(dx) < 1.0e-7 && Math.abs(dy) < 1.0e-7 && Math.abs(dz) < 1.0e-7) return;
        System.out.println("[PC-PAIR] T" + (tickIndex + 1) + " replay mismatch"
                + " server=" + sp.getX() + "," + sp.getY() + "," + sp.getZ()
                + " client=" + e.getX() + "," + e.getY() + "," + e.getZ()
                + " serverOnGround=" + sp.onGround()
                + " serverFall=" + sp.fallDistance
                + " serverMotion=" + formatVec(sp.getDeltaMovement())
                + " pose=" + sp.getPose());
    }

    private void flushHurtMarked() {
        if (sp.hurtMarked) {
            sp.hurtMarked = false;
            pendingClientbound.add(new ClientboundSetEntityMotionPacket(sp));
            if (DebugFlags.PAIRED_DIAGNOSTICS) {
                System.out.println("[PC-PAIR] T" + (tickIndex + 1) + " velocity queued " + formatVec(sp.getDeltaMovement()));
            }
        }
    }

    private static Vec3 quantizeVelocity(Vec3 velocity) {
        ByteBuf buf = Unpooled.buffer();
        try {
            LpVec3.write(buf, velocity);
            return LpVec3.read(buf);
        } finally {
            buf.release();
        }
    }

    private void onLandingRuled(double fallDistance, BlockState sampledState, BlockPos sampledPos) {
        if (!DebugFlags.PAIRED_DIAGNOSTICS) return;
        if (fallDistance < 1.0) return;
        System.out.println("[PC-PAIR] T" + (tickIndex + 1) + " landing fallDistance=" + fallDistance
                + " sampled=" + sampledState.getBlock().getName().getString()
                + " at " + sampledPos.getX() + "," + sampledPos.getY() + "," + sampledPos.getZ()
                + " sneak=" + sp.isShiftKeyDown()
                + " serverMotion=" + formatVec(sp.getDeltaMovement())
                + " unloadedChunks=" + sp.touchingUnloadedChunk());
    }

    private void emitBlockChangeEvents() {
        for (int i = journalSizeAtTickStart; i < journal.size(); i++) {
            WorldJournal.Entry entry = journal.get(i);
            addEvent(ServerSimEvent.Kind.BLOCK_CHANGED,
                    entry.pos.getX() + ", " + entry.pos.getY() + ", " + entry.pos.getZ()
                            + ": " + entry.before.getBlock().getName().getString()
                            + " to " + entry.after.getBlock().getName().getString());
        }
    }

    private void onDamageRuled(DamageSource source, float amount) {
        addEvent(ServerSimEvent.Kind.DAMAGE_RULED,
                source.type().msgId() + " for " + String.format(Locale.ROOT, "%.2f", amount));
        if (DebugFlags.PAIRED_DIAGNOSTICS) {
            System.out.println("[PC-PAIR] T" + (tickIndex + 1) + " damage-ruled " + source.type().msgId()
                    + " for " + String.format(Locale.ROOT, "%.2f", amount)
                    + " serverMotion=" + formatVec(sp.getDeltaMovement())
                    + " fallDistance=" + sp.fallDistance
                    + " onGround=" + sp.onGround()
                    + " pos=" + sp.getX() + "," + sp.getY() + "," + sp.getZ()
                    + " unloadedChunks=" + sp.touchingUnloadedChunk());
        }
    }

    private void addEvent(ServerSimEvent.Kind kind, String detail) {
        events.add(new ServerSimEvent(tickIndex, kind, detail));
    }

    public List<ServerSimEvent> drainEvents() {
        if (events.isEmpty()) return List.of();
        List<ServerSimEvent> drained = new ArrayList<>(events);
        events.clear();
        return drained;
    }

    public void endPass() {
        journal.notifyNetChanges();
    }

    public void onReplayStart(int startTick) {
        journal.rewindForReplay(startTick);
    }

    public void onReplayEnd() {
        journal.reapplyAfterReplay();
    }

    public void shutdown() {
        if (shutdownDone) return;
        shutdownDone = true;
        journal.shutdown();
        pendingClientbound.clear();
        serverbound.clear();
        events.clear();
        sp.discard();
    }

    public ServerCheckpoint saveCheckpoint() {
        ServerCheckpoint c = new ServerCheckpoint();
        c.posX = sp.getX();
        c.posY = sp.getY();
        c.posZ = sp.getZ();
        c.yRot = sp.getYRot();
        c.xRot = sp.getXRot();
        Vec3 vel = sp.getDeltaMovement();
        c.velX = vel.x;
        c.velY = vel.y;
        c.velZ = vel.z;
        c.fallDistance = sp.fallDistance;
        c.onGround = sp.onGround();
        c.verticalCollisionBelow = sp.verticalCollisionBelow;
        c.invulnerableTime = sp.invulnerableTime;
        c.hurtTime = sp.hurtTime;
        c.hurtMarked = sp.hurtMarked;
        c.remainingFireTicks = sp.getRemainingFireTicks();
        c.shiftKeyDown = sp.isShiftKeyDown();
        c.sprinting = sp.isSprinting();
        c.awaitingPositionFromClient = handler.awaitingPositionFromClient;
        c.awaitingTeleport = handler.awaitingTeleport;
        c.awaitingTeleportTime = handler.awaitingTeleportTime;
        c.clientIsFloating = handler.clientIsFloating;
        c.aboveGroundTickCount = handler.aboveGroundTickCount;
        c.receivedMovePacketCount = handler.receivedMovePacketCount;
        c.knownMovePacketCount = handler.knownMovePacketCount;
        c.handlerTickCount = handler.tickCount;
        c.firstGoodX = handler.firstGoodX;
        c.firstGoodY = handler.firstGoodY;
        c.firstGoodZ = handler.firstGoodZ;
        c.lastGoodX = handler.lastGoodX;
        c.lastGoodY = handler.lastGoodY;
        c.lastGoodZ = handler.lastGoodZ;
        c.pendingClientbound = new ArrayList<>(pendingClientbound);
        c.xLast = xLast;
        c.yLast = yLast;
        c.zLast = zLast;
        c.yRotLast = yRotLast;
        c.xRotLast = xRotLast;
        c.positionReminder = positionReminder;
        c.lastOnGround = lastOnGround;
        c.lastHorizontalCollision = lastHorizontalCollision;
        c.lastSentInput = lastSentInput;
        c.lastSprinting = lastSprinting;
        c.prevRightClick = prevRightClick;
        c.rightClickDelay = rightClickDelay;
        c.useSequence = useSequence;
        c.pitch = pitch;
        c.tickIndex = tickIndex;
        c.journalSize = journal.size();
        return c;
    }

    public void restore(SimulatorEntity e, PairedCheckpoint checkpoint) {
        ServerCheckpoint c = checkpoint.server;
        journal.revertTo(c.journalSize);
        journal.beginWindow(c.tickIndex, true);
        try {
            e.restoreCheckpoint(checkpoint.client);
        } finally {
            journal.endWindow();
        }
        sp.absSnapTo(c.posX, c.posY, c.posZ, c.yRot, c.xRot);
        sp.setDeltaMovement(c.velX, c.velY, c.velZ);
        sp.fallDistance = c.fallDistance;
        sp.setOnGround(c.onGround);
        sp.verticalCollisionBelow = c.verticalCollisionBelow;
        sp.invulnerableTime = c.invulnerableTime;
        sp.hurtTime = c.hurtTime;
        sp.hurtMarked = c.hurtMarked;
        sp.setRemainingFireTicks(c.remainingFireTicks);
        sp.setShiftKeyDown(c.shiftKeyDown);
        sp.setSprinting(c.sprinting);
        handler.awaitingPositionFromClient = c.awaitingPositionFromClient;
        handler.awaitingTeleport = c.awaitingTeleport;
        handler.awaitingTeleportTime = c.awaitingTeleportTime;
        handler.clientIsFloating = c.clientIsFloating;
        handler.aboveGroundTickCount = c.aboveGroundTickCount;
        handler.receivedMovePacketCount = c.receivedMovePacketCount;
        handler.knownMovePacketCount = c.knownMovePacketCount;
        handler.tickCount = c.handlerTickCount;
        handler.firstGoodX = c.firstGoodX;
        handler.firstGoodY = c.firstGoodY;
        handler.firstGoodZ = c.firstGoodZ;
        handler.lastGoodX = c.lastGoodX;
        handler.lastGoodY = c.lastGoodY;
        handler.lastGoodZ = c.lastGoodZ;
        pendingClientbound.clear();
        pendingClientbound.addAll(c.pendingClientbound);
        serverbound.clear();
        xLast = c.xLast;
        yLast = c.yLast;
        zLast = c.zLast;
        yRotLast = c.yRotLast;
        xRotLast = c.xRotLast;
        positionReminder = c.positionReminder;
        lastOnGround = c.lastOnGround;
        lastHorizontalCollision = c.lastHorizontalCollision;
        lastSentInput = c.lastSentInput;
        lastSprinting = c.lastSprinting;
        prevRightClick = c.prevRightClick;
        rightClickDelay = c.rightClickDelay;
        useSequence = c.useSequence;
        pitch = c.pitch;
        tickIndex = c.tickIndex;
    }

    private static String formatVec(Vec3 v) {
        return String.format(Locale.ROOT, "%.4f, %.4f, %.4f", v.x, v.y, v.z);
    }

    public static final class ServerCheckpoint {
        double posX;
        double posY;
        double posZ;
        float yRot;
        float xRot;
        double velX;
        double velY;
        double velZ;
        double fallDistance;
        boolean onGround;
        boolean verticalCollisionBelow;
        int invulnerableTime;
        int hurtTime;
        boolean hurtMarked;
        int remainingFireTicks;
        boolean shiftKeyDown;
        boolean sprinting;
        Vec3 awaitingPositionFromClient;
        int awaitingTeleport;
        int awaitingTeleportTime;
        boolean clientIsFloating;
        int aboveGroundTickCount;
        int receivedMovePacketCount;
        int knownMovePacketCount;
        int handlerTickCount;
        double firstGoodX;
        double firstGoodY;
        double firstGoodZ;
        double lastGoodX;
        double lastGoodY;
        double lastGoodZ;
        List<Packet<?>> pendingClientbound;
        double xLast;
        double yLast;
        double zLast;
        float yRotLast;
        float xRotLast;
        int positionReminder;
        boolean lastOnGround;
        boolean lastHorizontalCollision;
        Input lastSentInput;
        boolean lastSprinting;
        boolean prevRightClick;
        int rightClickDelay;
        int useSequence;
        float pitch;
        int tickIndex;
        int journalSize;
    }
}
