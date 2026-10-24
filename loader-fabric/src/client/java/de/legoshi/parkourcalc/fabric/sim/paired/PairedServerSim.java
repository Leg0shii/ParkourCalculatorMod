package de.legoshi.parkourcalc.fabric.sim.paired;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.core.DebugFlags;
import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.sim.ServerSimEvent;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.fabric.sim.FabricSimulator;
//? if <1.21.2
//import de.legoshi.parkourcalc.fabric.sim.Input;
import de.legoshi.parkourcalc.fabric.sim.SimulatorEntity;
//? if >=26.1 {
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
//?}
import net.minecraft.core.BlockPos;
//? if >=26.1
import net.minecraft.network.LpVec3;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
//? if >=1.21.2
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
//? if >=1.21.2
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
//? if >=1.21.4
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
//? if >=1.21.2 {
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Input;
//?} else {
/*import net.minecraft.world.entity.RelativeMovement;
*///?}
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
//? if <1.21.2
//import java.util.Set;
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
    //? if <26.1
    //private boolean lastShiftKeyDown;
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
        //? if >=1.21.4
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
        if (real != null) FabricSimulator.copyBaseAttributes(sp.getAttributes(), real.getAttributes());
        sp.setAirSupply(real != null ? real.getAirSupply() : sp.getMaxAirSupply());
        snapServerPlayer(e.getX(), e.getY(), e.getZ(), e.getYRot(), 0.0F);
        sp.setDeltaMovement(e.getDeltaMovement());
        sp.fallDistance = 0;
        sp.setOnGround(false);
        sp.verticalCollisionBelow = false;
        //? if >=26.1 {
        sp.damageCooldownTime = 0;
        sp.hurtTime = 0;
        sp.syncVelocity = false;
        //?} else {
        /*sp.invulnerableTime = 0;
        sp.spawnInvulnerableTime = 0;
        sp.hurtTime = 0;
        sp.hurtMarked = false;
        *///?}
        sp.clearFire();
        sp.setShiftKeyDown(false);
        sp.setSprinting(false);
        handler.resetPosition();
        handler.handleMovePlayer(new ServerboundMovePlayerPacket.PosRot(
        //? if >=1.21.2 {
                e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot(), false, false));
        handler.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
        //?} else {
                /*e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot(), false));
        *///?}
        for (int i = 0; i < WARMUP_SERVER_TICKS; i++) {
            sp.tick();
            tickHandler();
        }
        //? if >=26.1 {
        sp.syncVelocity = false;
        handler.resetPosition();
        handler.resetFlyingTicks();
        //?} else {
        /*sp.hurtMarked = false;
        handler.resetPosition();
        handler.aboveGroundTickCount = 0;
        handler.aboveGroundVehicleTickCount = 0;
        *///?}
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
        //? if <26.1
        //lastShiftKeyDown = false;
        lastSprinting = false;
        prevRightClick = false;
        rightClickDelay = 0;
        useSequence = 0;
    }

    private void snapServerPlayer(double x, double y, double z, float yRot, float xRot) {
        //? if >=26.1 {
        sp.absSnapTo(x, y, z, yRot, xRot);
        //?} else {
        /*sp.absMoveTo(x, y, z, yRot, xRot);
        *///?}
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
                    if (motionEntityId(motion) != sp.getId()) continue;
                    Vec3 applied = motionVelocity(motion);
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

    static int motionEntityId(ClientboundSetEntityMotionPacket motion) {
        //? if >=26.1 {
        return motion.id();
        //?} else {
        /*return motion.getId();
        *///?}
    }

    static Vec3 motionVelocity(ClientboundSetEntityMotionPacket motion) {
        //? if >=26.1 {
        return quantizeVelocity(motion.movement());
        //?} elif >=1.21 {
        /*return new Vec3(motion.getXa(), motion.getYa(), motion.getZa());
        *///?} else {
        /*return new Vec3(motion.getXa() / 8000.0, motion.getYa() / 8000.0, motion.getZa() / 8000.0);
        *///?}
    }

    private void applyPositionCorrection(SimulatorEntity e, ClientboundPlayerPositionPacket packet) {
        //? if >=1.21.2 {
        PositionMoveRotation current = PositionMoveRotation.of(e);
        PositionMoveRotation next = PositionMoveRotation.calculateAbsolute(current, packet.change(), packet.relatives());
        Vec3 currentPosition = current.position();
        Vec3 currentVelocity = current.deltaMovement();
        Vec3 nextPosition = next.position();
        Vec3 nextVelocity = next.deltaMovement();
        //?} else {
        /*Set<RelativeMovement> relatives = packet.getRelativeArguments();
        Vec3 currentPosition = e.position();
        Vec3 currentVelocity = e.getDeltaMovement();
        Vec3 nextPosition = new Vec3(
                relatives.contains(RelativeMovement.X) ? currentPosition.x + packet.getX() : packet.getX(),
                relatives.contains(RelativeMovement.Y) ? currentPosition.y + packet.getY() : packet.getY(),
                relatives.contains(RelativeMovement.Z) ? currentPosition.z + packet.getZ() : packet.getZ());
        Vec3 nextVelocity = new Vec3(
                relatives.contains(RelativeMovement.X) ? currentVelocity.x : 0.0,
                relatives.contains(RelativeMovement.Y) ? currentVelocity.y : 0.0,
                relatives.contains(RelativeMovement.Z) ? currentVelocity.z : 0.0);
        *///?}
        if (DebugFlags.PAIRED_DIAGNOSTICS) {
            System.out.println("[PC-PAIR] T" + (tickIndex + 1) + " lagback from " + formatVec(currentPosition)
                    + " vel " + formatVec(currentVelocity) + " onGround=" + e.onGround()
                    + " sneaking=" + e.isShiftKeyDown() + " sprinting=" + e.isSprinting());
        }
        e.setPos(nextPosition);
        e.setDeltaMovement(nextVelocity);
        e.setOldPosAndRot();
        addEvent(ServerSimEvent.Kind.POSITION_CORRECTION, "to " + formatVec(nextPosition));
        if (DebugFlags.PAIRED_DIAGNOSTICS) {
            System.out.println("[PC-PAIR] T" + (tickIndex + 1) + " lagback applied to " + formatVec(nextPosition));
        }
        //? if >=26.1 {
        serverbound.add(new ServerboundAcceptTeleportationPacket(
                packet.id(), e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot()));
        //?} elif >=1.21.2 {
        /*serverbound.add(new ServerboundAcceptTeleportationPacket(packet.id()));
        serverbound.add(new ServerboundMovePlayerPacket.PosRot(
                e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot(), false, false));
        *///?} else {
        /*serverbound.add(new ServerboundAcceptTeleportationPacket(packet.getId()));
        serverbound.add(new ServerboundMovePlayerPacket.PosRot(
                e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot(), false));
        *///?}
    }

    public void afterClientTick(SimulatorEntity e) {
        try {
            //? if <26.1
            //synthesizeShiftCommand(e);
            //? if >=1.21.2
            synthesizeInputPacket(e);
            synthesizeSprintCommand(e);
            synthesizeMovePacket(e);
            //? if >=1.21.2
            serverbound.add(ServerboundClientTickEndPacket.INSTANCE);
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

    //? if <26.1 {
    /*private void synthesizeShiftCommand(SimulatorEntity e) {
        boolean shift = e.isShiftKeyDown();
        if (shift != lastShiftKeyDown) {
            serverbound.add(new ServerboundPlayerCommandPacket(sp, shift
                    ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY
                    : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
            lastShiftKeyDown = shift;
        }
    }
    *///?}

    //? if >=1.21.2 {
    private void synthesizeInputPacket(SimulatorEntity e) {
        Input current = e.input.keyPresses;
        if (!current.equals(lastSentInput)) {
            serverbound.add(new ServerboundPlayerInputPacket(current));
            lastSentInput = current;
        }
    }
    //?}

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
        //? if >=26.1 {
                    e.position(), e.getYRot(), e.getXRot(), onGround, horizontalCollision));
        } else if (move) {
            serverbound.add(new ServerboundMovePlayerPacket.Pos(e.position(), onGround, horizontalCollision));
        } else if (rot) {
            serverbound.add(new ServerboundMovePlayerPacket.Rot(
                    e.getYRot(), e.getXRot(), onGround, horizontalCollision));
        } else if (lastOnGround != onGround || lastHorizontalCollision != horizontalCollision) {
            serverbound.add(new ServerboundMovePlayerPacket.StatusOnly(onGround, horizontalCollision));
        }
        //?} elif >=1.21.2 {
                    /*e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot(), onGround, horizontalCollision));
        } else if (move) {
            serverbound.add(new ServerboundMovePlayerPacket.Pos(
                    e.getX(), e.getY(), e.getZ(), onGround, horizontalCollision));
        } else if (rot) {
            serverbound.add(new ServerboundMovePlayerPacket.Rot(
                    e.getYRot(), e.getXRot(), onGround, horizontalCollision));
        } else if (lastOnGround != onGround || lastHorizontalCollision != horizontalCollision) {
            serverbound.add(new ServerboundMovePlayerPacket.StatusOnly(onGround, horizontalCollision));
        }
        *///?} else {
                    /*e.getX(), e.getY(), e.getZ(), e.getYRot(), e.getXRot(), onGround));
        } else if (move) {
            serverbound.add(new ServerboundMovePlayerPacket.Pos(e.getX(), e.getY(), e.getZ(), onGround));
        } else if (rot) {
            serverbound.add(new ServerboundMovePlayerPacket.Rot(e.getYRot(), e.getXRot(), onGround));
        } else if (lastOnGround != onGround) {
            serverbound.add(new ServerboundMovePlayerPacket.StatusOnly(onGround));
        }
        *///?}
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
        Vec3 dir = pickViewVector(pitch, e.getYRot());
        //? if >=1.20.5 {
        double range = e.blockInteractionRange();
        //?} else {
        /*double range = 4.5;
        *///?}
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

    private static Vec3 pickViewVector(float pitch, float yaw) {
        float f = pitch * ((float) Math.PI / 180F);
        float f1 = -yaw * ((float) Math.PI / 180F);
        float f2 = Mth.cos(f1);
        float f3 = Mth.sin(f1);
        float f4 = Mth.cos(f);
        float f5 = Mth.sin(f);
        return new Vec3(f3 * f4, -f5, f2 * f4);
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
            snapServerPlayer(handler.firstGoodX, handler.firstGoodY, handler.firstGoodZ, sp.getYRot(), sp.getXRot());
            handler.tickCount = before + 1;
            handler.knownMovePacketCount = handler.receivedMovePacketCount;
        }
    }

    public static String describeInput(Input in) {
        if (in == null) return "null";
        return (in.forward() ? "W" : "-") + (in.backward() ? "S" : "-")
                + (in.left() ? "A" : "-") + (in.right() ? "D" : "-")
                + (in.jump() ? "J" : "-") + (in.shift() ? "N" : "-")
                + (in.sprint() ? "R" : "-");
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
        //? if >=26.1 {
        boolean pendingVelocity = sp.syncVelocity;
        sp.syncVelocity = false;
        //?} else {
        /*boolean pendingVelocity = sp.hurtMarked;
        sp.hurtMarked = false;
        *///?}
        if (pendingVelocity) {
            pendingClientbound.add(new ClientboundSetEntityMotionPacket(sp));
            if (DebugFlags.PAIRED_DIAGNOSTICS) {
                Vec3 raw = sp.getDeltaMovement();
                System.out.println("[PC-PAIR] T" + (tickIndex + 1) + " velocity queued " + formatVec(raw)
                        + " raw=" + raw.x + "," + raw.y + "," + raw.z
                        + " quantized=" + quantizeVelocity(raw));
            }
        }
    }

    public static Vec3 quantizeVelocity(Vec3 velocity) {
        //? if >=26.1 {
        ByteBuf buf = Unpooled.buffer();
        try {
            LpVec3.write(buf, velocity);
            return LpVec3.read(buf);
        } finally {
            buf.release();
        }
        //?} else {
        /*return new Vec3(quantizeAxis(velocity.x), quantizeAxis(velocity.y), quantizeAxis(velocity.z));
        *///?}
    }

    //? if <26.1 {
    /*private static double quantizeAxis(double value) {
        return (double) (int) (Mth.clamp(value, -3.9, 3.9) * 8000.0) / 8000.0;
    }
    *///?}

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

    public Vec3dCore serverVelocity() {
        Vec3 v = sp.getDeltaMovement();
        return new Vec3dCore(v.x, v.y, v.z);
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
        //? if >=26.1 {
        c.damageCooldownTime = sp.damageCooldownTime;
        c.syncVelocity = sp.syncVelocity;
        //?} else {
        /*c.damageCooldownTime = sp.invulnerableTime;
        c.syncVelocity = sp.hurtMarked;
        *///?}
        c.hurtTime = sp.hurtTime;
        c.remainingFireTicks = sp.getRemainingFireTicks();
        c.airSupply = sp.getAirSupply();
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
        //? if >=1.21.2
        c.serverLastClientInput = sp.getLastClientInput();
        //? if <26.1
        //c.lastShiftKeyDown = lastShiftKeyDown;
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
        snapServerPlayer(c.posX, c.posY, c.posZ, c.yRot, c.xRot);
        sp.setDeltaMovement(c.velX, c.velY, c.velZ);
        sp.fallDistance = c.fallDistance;
        sp.setOnGround(c.onGround);
        sp.verticalCollisionBelow = c.verticalCollisionBelow;
        //? if >=26.1 {
        sp.damageCooldownTime = c.damageCooldownTime;
        sp.syncVelocity = c.syncVelocity;
        //?} else {
        /*sp.invulnerableTime = c.damageCooldownTime;
        sp.hurtMarked = c.syncVelocity;
        *///?}
        sp.hurtTime = c.hurtTime;
        sp.setRemainingFireTicks(c.remainingFireTicks);
        sp.setAirSupply(c.airSupply);
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
        //? if >=1.21.2
        sp.setLastClientInput(c.serverLastClientInput);
        //? if <26.1
        //lastShiftKeyDown = c.lastShiftKeyDown;
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
        //? if >=26.1 {
        double fallDistance;
        //?} else {
        /*float fallDistance;
        *///?}
        boolean onGround;
        boolean verticalCollisionBelow;
        int damageCooldownTime;
        int hurtTime;
        boolean syncVelocity;
        int remainingFireTicks;
        int airSupply;
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
        //? if >=1.21.2
        Input serverLastClientInput;
        //? if <26.1
        //boolean lastShiftKeyDown;
        boolean lastSprinting;
        boolean prevRightClick;
        int rightClickDelay;
        int useSequence;
        float pitch;
        int tickIndex;
        int journalSize;
    }
}
