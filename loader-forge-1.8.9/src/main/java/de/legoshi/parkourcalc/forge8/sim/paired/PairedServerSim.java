package de.legoshi.parkourcalc.forge8.sim.paired;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.core.PlaybackController;
import de.legoshi.parkourcalc.core.sim.ServerSimEvent;
import de.legoshi.parkourcalc.core.sim.StartResumeState;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.forge8.sim.SimulatorEntity;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings({"unchecked", "rawtypes"})
public final class PairedServerSim {

    private static final UUID PROFILE_UUID = UUID.nameUUIDFromBytes("pkc-paired-sim".getBytes(StandardCharsets.UTF_8));
    private static final String PROFILE_NAME = "PKCPairedSim";
    private static final int RIGHT_CLICK_DELAY_TICKS = 4;
    private static final int WARMUP_SERVER_TICKS = 2;
    private static final double CLIENT_REACH = 4.5;

    private final WorldServer level;
    private final PairedServerPlayer sp;
    private final PairedGameHandler handler;
    private final WorldJournal journal;

    private final List<Packet> pendingClientbound = new ArrayList<Packet>();
    private final List<Packet> serverbound = new ArrayList<Packet>();
    private final List<ServerSimEvent> events = new ArrayList<ServerSimEvent>();

    private int tickIndex;
    private float startPitch = PlaybackController.DEFAULT_PITCH;
    private float pitch = PlaybackController.DEFAULT_PITCH;

    private double lastReportedPosX;
    private double lastReportedPosY;
    private double lastReportedPosZ;
    private float lastReportedYaw;
    private float lastReportedPitch;
    private int positionUpdateTicks;
    private boolean lastSprinting;
    private boolean lastSneaking;
    private boolean prevRightClick;
    private int rightClickDelay;
    private int journalSizeAtTickStart;
    private boolean shutdownDone;
    private boolean damageEnabled = true;

    private PairedServerSim(WorldServer level, PairedServerPlayer sp, PairedGameHandler handler) {
        this.level = level;
        this.sp = sp;
        this.handler = handler;
        this.journal = new WorldJournal(level);
    }

    public static PairedServerSim create(SimulatorEntity clientEntity) {
        World world = clientEntity.worldObj;
        if (!(world instanceof WorldServer)) return null;
        WorldServer serverWorld = (WorldServer) world;
        MinecraftServer server = serverWorld.getMinecraftServer();
        GameProfile profile = new GameProfile(PROFILE_UUID, PROFILE_NAME);
        PairedServerPlayer sp = new PairedServerPlayer(server, serverWorld, profile);
        PairedGameHandler handler = new PairedGameHandler(server, sp);
        final PairedServerSim sim = new PairedServerSim(serverWorld, sp, handler);
        handler.setPacketSink(new PairedGameHandler.PacketSink() {
            @Override
            public void onClientbound(Packet packet) {
                sim.pendingClientbound.add(packet);
            }
        });
        sp.setDamageSink(new PairedServerPlayer.DamageSink() {
            @Override
            public void onDamageRuled(DamageSource source, float amount) {
                sim.onDamageRuled(source, amount);
            }
        });
        sp.setFallRulingSink(new PairedServerPlayer.FallRulingSink() {
            @Override
            public void onLandingRuled(float fallDistance, Block sampledBlock, BlockPos sampledPos) {
                sim.onLandingRuled(fallDistance, sampledBlock, sampledPos);
            }
        });
        sp.setDamageGate(new PairedServerPlayer.DamageGate() {
            @Override
            public boolean isDamageEnabled() {
                return sim.damageEnabled;
            }
        });
        return sim;
    }

    public WorldServer level() {
        return level;
    }

    public void setStartPitch(float startPitch) {
        this.startPitch = startPitch;
    }

    public void setDamageEnabled(boolean enabled) {
        this.damageEnabled = enabled;
    }

    public void resetForFullRun(SimulatorEntity e, StartResumeState resume) {
        journal.revertTo(0);
        pendingClientbound.clear();
        serverbound.clear();
        tickIndex = 0;
        pitch = startPitch;
        journal.beginWindow(0);
        try {
            e.resetPlayer(resume);
        } finally {
            journal.endWindow();
        }
        resetServerSide(e);
    }

    private void resetServerSide(SimulatorEntity e) {
        sp.setPositionAndRotation(e.posX, e.posY, e.posZ, e.rotationYaw, 0.0F);
        sp.motionX = e.motionX;
        sp.motionY = e.motionY;
        sp.motionZ = e.motionZ;
        sp.fallDistance = 0.0F;
        sp.onGround = false;
        sp.hurtResistantTime = 0;
        sp.hurtTime = 0;
        sp.setLastDamage(0.0F);
        sp.velocityChanged = false;
        sp.setSneaking(false);
        sp.setSprinting(false);
        sp.respawnInvulnerabilityTicks = 0;
        sp.managedPosX = sp.posX;
        sp.managedPosZ = sp.posZ;
        handler.hasMoved = true;
        handler.lastPosX = e.posX;
        handler.lastPosY = e.posY;
        handler.lastPosZ = e.posZ;
        journal.beginWindow(0);
        try {
            for (int i = 0; i < WARMUP_SERVER_TICKS; i++) {
                handler.processPlayer(new C03PacketPlayer(false));
                sp.onUpdate();
                handler.update();
            }
        } finally {
            journal.endWindow();
        }
        sp.velocityChanged = false;
        sp.respawnInvulnerabilityTicks = 0;
        handler.networkTickCount = 0;
        handler.field_175090_f = 0;
        handler.floatingTickCount = 0;
        handler.lastSentPingPacket = 0;
        pendingClientbound.clear();
        serverbound.clear();
        lastReportedPosX = e.posX;
        lastReportedPosY = e.getEntityBoundingBox().minY;
        lastReportedPosZ = e.posZ;
        lastReportedYaw = e.rotationYaw;
        lastReportedPitch = e.rotationPitch;
        positionUpdateTicks = 0;
        lastSprinting = false;
        lastSneaking = false;
        prevRightClick = false;
        rightClickDelay = 0;
    }

    public void abortTick() {
        serverbound.clear();
        journal.endWindow();
    }

    public void beginTick(SimulatorEntity e, InputRow row) {
        journal.beginWindow(tickIndex);
        journalSizeAtTickStart = journal.size();
        if (!pendingClientbound.isEmpty()) {
            List<Packet> toApply = new ArrayList<Packet>(pendingClientbound);
            pendingClientbound.clear();
            for (Packet packet : toApply) {
                if (packet instanceof S12PacketEntityVelocity) {
                    S12PacketEntityVelocity motion = (S12PacketEntityVelocity) packet;
                    if (motion.getEntityID() != sp.getEntityId()) continue;
                    e.motionX = motion.getMotionX() / 8000.0;
                    e.motionY = motion.getMotionY() / 8000.0;
                    e.motionZ = motion.getMotionZ() / 8000.0;
                    addEvent(ServerSimEvent.Kind.VELOCITY_SET, formatVec(e.motionX, e.motionY, e.motionZ));
                    System.out.println("[PC-PAIR] T" + (tickIndex + 1) + " velocity applied "
                            + formatVec(e.motionX, e.motionY, e.motionZ));
                } else if (packet instanceof S08PacketPlayerPosLook) {
                    applyPositionCorrection(e, (S08PacketPlayerPosLook) packet);
                } else if (packet instanceof S00PacketKeepAlive) {
                    serverbound.add(new C00PacketKeepAlive(((S00PacketKeepAlive) packet).func_149134_c()));
                }
            }
        }
        if (row != null) {
            pitch = PlaybackController.applyPitch(pitch, row);
        }
        synthesizeUseItem(e, row);
    }

    private void applyPositionCorrection(SimulatorEntity e, S08PacketPlayerPosLook packet) {
        double x = packet.getX();
        double y = packet.getY();
        double z = packet.getZ();
        float yaw = packet.getYaw();
        float pit = packet.getPitch();
        Set<S08PacketPlayerPosLook.EnumFlags> flags = packet.func_179834_f();
        if (flags.contains(S08PacketPlayerPosLook.EnumFlags.X)) {
            x += e.posX;
        } else {
            e.motionX = 0.0;
        }
        if (flags.contains(S08PacketPlayerPosLook.EnumFlags.Y)) {
            y += e.posY;
        } else {
            e.motionY = 0.0;
        }
        if (flags.contains(S08PacketPlayerPosLook.EnumFlags.Z)) {
            z += e.posZ;
        } else {
            e.motionZ = 0.0;
        }
        if (flags.contains(S08PacketPlayerPosLook.EnumFlags.X_ROT)) {
            pit += e.rotationPitch;
        }
        if (flags.contains(S08PacketPlayerPosLook.EnumFlags.Y_ROT)) {
            yaw += e.rotationYaw;
        }
        e.setPositionAndRotation(x, y, z, yaw, pit);
        addEvent(ServerSimEvent.Kind.POSITION_CORRECTION, "to " + formatVec(e.posX, e.posY, e.posZ));
        System.out.println("[PC-PAIR] T" + (tickIndex + 1) + " lagback applied to "
                + formatVec(e.posX, e.posY, e.posZ));
        serverbound.add(new C03PacketPlayer.C06PacketPlayerPosLook(
                e.posX, e.getEntityBoundingBox().minY, e.posZ, e.rotationYaw, e.rotationPitch, false));
    }

    public void afterClientTick(SimulatorEntity e) {
        try {
            synthesizeSprintCommand(e);
            synthesizeSneakCommand(e);
            synthesizeMovePacket(e);
            sp.managedPosX = sp.posX;
            sp.managedPosZ = sp.posZ;
            drainServerbound();
            reportReplayMismatch(e);
            sp.onUpdate();
            flushVelocityChanged();
            handler.update();
            emitBlockChangeEvents();
        } finally {
            journal.endWindow();
            tickIndex++;
        }
    }

    private void synthesizeSprintCommand(SimulatorEntity e) {
        boolean sprinting = e.isSprinting();
        if (sprinting != lastSprinting) {
            serverbound.add(new C0BPacketEntityAction(sp, sprinting
                    ? C0BPacketEntityAction.Action.START_SPRINTING
                    : C0BPacketEntityAction.Action.STOP_SPRINTING));
            lastSprinting = sprinting;
        }
    }

    private void synthesizeSneakCommand(SimulatorEntity e) {
        boolean sneaking = e.isSneaking();
        if (sneaking != lastSneaking) {
            serverbound.add(new C0BPacketEntityAction(sp, sneaking
                    ? C0BPacketEntityAction.Action.START_SNEAKING
                    : C0BPacketEntityAction.Action.STOP_SNEAKING));
            lastSneaking = sneaking;
        }
    }

    private void synthesizeMovePacket(SimulatorEntity e) {
        double minY = e.getEntityBoundingBox().minY;
        double d0 = e.posX - lastReportedPosX;
        double d1 = minY - lastReportedPosY;
        double d2 = e.posZ - lastReportedPosZ;
        double d3 = e.rotationYaw - lastReportedYaw;
        double d4 = e.rotationPitch - lastReportedPitch;
        boolean moving = d0 * d0 + d1 * d1 + d2 * d2 > 9.0E-4 || positionUpdateTicks >= 20;
        boolean rotating = d3 != 0.0 || d4 != 0.0;
        boolean onGround = e.onGround;
        if (moving && rotating) {
            serverbound.add(new C03PacketPlayer.C06PacketPlayerPosLook(
                    e.posX, minY, e.posZ, e.rotationYaw, e.rotationPitch, onGround));
        } else if (moving) {
            serverbound.add(new C03PacketPlayer.C04PacketPlayerPosition(e.posX, minY, e.posZ, onGround));
        } else if (rotating) {
            serverbound.add(new C03PacketPlayer.C05PacketPlayerLook(e.rotationYaw, e.rotationPitch, onGround));
        } else {
            serverbound.add(new C03PacketPlayer(onGround));
        }
        positionUpdateTicks++;
        if (moving) {
            lastReportedPosX = e.posX;
            lastReportedPosY = minY;
            lastReportedPosZ = e.posZ;
            positionUpdateTicks = 0;
        }
        if (rotating) {
            lastReportedYaw = e.rotationYaw;
            lastReportedPitch = e.rotationPitch;
        }
    }

    private void synthesizeUseItem(SimulatorEntity e, InputRow row) {
        boolean rightClick = row != null && row.isKeyActive(InputRow.Key.RIGHT_CLICK);
        if (rightClickDelay > 0) rightClickDelay--;
        boolean fire = rightClick && (!prevRightClick || rightClickDelay == 0);
        prevRightClick = rightClick;
        if (!fire) return;
        rightClickDelay = RIGHT_CLICK_DELAY_TICKS;
        Vec3 eye = new Vec3(e.posX, e.posY + e.getEyeHeight(), e.posZ);
        Vec3 look = vectorForRotation(pitch, e.rotationYaw);
        Vec3 end = eye.addVector(look.xCoord * CLIENT_REACH, look.yCoord * CLIENT_REACH, look.zCoord * CLIENT_REACH);
        MovingObjectPosition hit = level.rayTraceBlocks(eye, end, false, false, true);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            addEvent(ServerSimEvent.Kind.INTERACTION_REJECTED, "no block in reach");
            return;
        }
        drainServerbound();
        BlockPos pos = hit.getBlockPos();
        float fx = quantizeFacing((float) (hit.hitVec.xCoord - pos.getX()));
        float fy = quantizeFacing((float) (hit.hitVec.yCoord - pos.getY()));
        float fz = quantizeFacing((float) (hit.hitVec.zCoord - pos.getZ()));
        int sizeBefore = journal.size();
        BlockPos[] cube = new BlockPos[27];
        IBlockState[] before = new IBlockState[27];
        int n = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = pos.add(dx, dy, dz);
                    cube[n] = p;
                    before[n] = level.getBlockState(p);
                    n++;
                }
            }
        }
        handler.processPlayerBlockPlacement(new C08PacketPlayerBlockPlacement(
                pos, hit.sideHit.getIndex(), null, fx, fy, fz));
        for (int i = 0; i < 27; i++) {
            IBlockState after = level.getBlockState(cube[i]);
            if (after != before[i]) {
                journal.record(cube[i], before[i], after);
            }
        }
        if (journal.size() == sizeBefore) {
            addEvent(ServerSimEvent.Kind.INTERACTION_REJECTED, "use had no effect");
        }
    }

    private static float quantizeFacing(float f) {
        return ((int) (f * 16.0F) & 255) / 16.0F;
    }

    private static Vec3 vectorForRotation(float pitch, float yaw) {
        float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(f1 * f2, f3, f * f2);
    }

    private void drainServerbound() {
        if (serverbound.isEmpty()) return;
        List<Packet> toProcess = new ArrayList<Packet>(serverbound);
        serverbound.clear();
        for (Packet packet : toProcess) {
            packet.processPacket(handler);
        }
    }

    private void reportReplayMismatch(SimulatorEntity e) {
        double dx = sp.posX - e.posX;
        double dy = sp.posY - e.posY;
        double dz = sp.posZ - e.posZ;
        if (Math.abs(dx) < 1.0e-7 && Math.abs(dy) < 1.0e-7 && Math.abs(dz) < 1.0e-7) return;
        System.out.println("[PC-PAIR] T" + (tickIndex + 1) + " replay mismatch"
                + " server=" + sp.posX + "," + sp.posY + "," + sp.posZ
                + " client=" + e.posX + "," + e.posY + "," + e.posZ
                + " serverOnGround=" + sp.onGround
                + " serverFall=" + sp.fallDistance
                + " serverMotion=" + formatVec(sp.motionX, sp.motionY, sp.motionZ));
    }

    private void flushVelocityChanged() {
        if (sp.velocityChanged) {
            sp.velocityChanged = false;
            pendingClientbound.add(new S12PacketEntityVelocity(sp));
            System.out.println("[PC-PAIR] T" + (tickIndex + 1) + " velocity queued "
                    + formatVec(sp.motionX, sp.motionY, sp.motionZ));
        }
    }

    private void onLandingRuled(float fallDistance, Block sampledBlock, BlockPos sampledPos) {
        if (fallDistance < 1.0F) return;
        System.out.println("[PC-PAIR] T" + (tickIndex + 1) + " landing fallDistance=" + fallDistance
                + " sampled=" + sampledBlock.getLocalizedName()
                + " at " + sampledPos.getX() + "," + sampledPos.getY() + "," + sampledPos.getZ()
                + " sneak=" + sp.isSneaking()
                + " serverMotion=" + formatVec(sp.motionX, sp.motionY, sp.motionZ));
    }

    private void emitBlockChangeEvents() {
        for (int i = journalSizeAtTickStart; i < journal.size(); i++) {
            WorldJournal.Entry entry = journal.get(i);
            addEvent(ServerSimEvent.Kind.BLOCK_CHANGED,
                    entry.pos.getX() + ", " + entry.pos.getY() + ", " + entry.pos.getZ()
                            + ": " + entry.before.getBlock().getLocalizedName()
                            + " to " + entry.after.getBlock().getLocalizedName());
        }
    }

    private void onDamageRuled(DamageSource source, float amount) {
        addEvent(ServerSimEvent.Kind.DAMAGE_RULED,
                source.getDamageType() + " for " + String.format(Locale.ROOT, "%.2f", amount));
        System.out.println("[PC-PAIR] T" + (tickIndex + 1) + " damage-ruled " + source.getDamageType()
                + " for " + String.format(Locale.ROOT, "%.2f", amount)
                + " serverMotion=" + formatVec(sp.motionX, sp.motionY, sp.motionZ)
                + " fallDistance=" + sp.fallDistance
                + " onGround=" + sp.onGround
                + " pos=" + sp.posX + "," + sp.posY + "," + sp.posZ);
    }

    private void addEvent(ServerSimEvent.Kind kind, String detail) {
        events.add(new ServerSimEvent(tickIndex, kind, detail));
    }

    public List<ServerSimEvent> drainEvents() {
        if (events.isEmpty()) return Collections.emptyList();
        List<ServerSimEvent> drained = new ArrayList<ServerSimEvent>(events);
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
        sp.setDead();
    }

    public ServerCheckpoint saveCheckpoint() {
        ServerCheckpoint c = new ServerCheckpoint();
        c.posX = sp.posX;
        c.posY = sp.posY;
        c.posZ = sp.posZ;
        c.yaw = sp.rotationYaw;
        c.pitchRot = sp.rotationPitch;
        c.velX = sp.motionX;
        c.velY = sp.motionY;
        c.velZ = sp.motionZ;
        c.fallDistance = sp.fallDistance;
        c.onGround = sp.onGround;
        c.hurtResistantTime = sp.hurtResistantTime;
        c.hurtTime = sp.hurtTime;
        c.lastDamage = sp.getLastDamage();
        c.velocityChanged = sp.velocityChanged;
        c.sneaking = sp.isSneaking();
        c.sprinting = sp.isSprinting();
        c.hasMoved = handler.hasMoved;
        c.lastPosX = handler.lastPosX;
        c.lastPosY = handler.lastPosY;
        c.lastPosZ = handler.lastPosZ;
        c.floatingTickCount = handler.floatingTickCount;
        c.networkTickCount = handler.networkTickCount;
        c.lastMoveStallTick = handler.field_175090_f;
        c.lastSentPingPacket = handler.lastSentPingPacket;
        c.pendingClientbound = new ArrayList<Packet>(pendingClientbound);
        c.lastReportedPosX = lastReportedPosX;
        c.lastReportedPosY = lastReportedPosY;
        c.lastReportedPosZ = lastReportedPosZ;
        c.lastReportedYaw = lastReportedYaw;
        c.lastReportedPitch = lastReportedPitch;
        c.positionUpdateTicks = positionUpdateTicks;
        c.lastSprinting = lastSprinting;
        c.lastSneaking = lastSneaking;
        c.prevRightClick = prevRightClick;
        c.rightClickDelay = rightClickDelay;
        c.pitch = pitch;
        c.tickIndex = tickIndex;
        c.journalSize = journal.size();
        return c;
    }

    public void restore(SimulatorEntity e, PairedCheckpoint checkpoint) {
        ServerCheckpoint c = checkpoint.server;
        journal.revertTo(c.journalSize);
        journal.beginWindow(c.tickIndex);
        try {
            e.restoreCheckpoint(checkpoint.client);
        } finally {
            journal.endWindow();
        }
        sp.setPositionAndRotation(c.posX, c.posY, c.posZ, c.yaw, c.pitchRot);
        sp.motionX = c.velX;
        sp.motionY = c.velY;
        sp.motionZ = c.velZ;
        sp.fallDistance = c.fallDistance;
        sp.onGround = c.onGround;
        sp.hurtResistantTime = c.hurtResistantTime;
        sp.hurtTime = c.hurtTime;
        sp.setLastDamage(c.lastDamage);
        sp.velocityChanged = c.velocityChanged;
        sp.setSneaking(c.sneaking);
        sp.setSprinting(c.sprinting);
        sp.respawnInvulnerabilityTicks = 0;
        sp.managedPosX = sp.posX;
        sp.managedPosZ = sp.posZ;
        handler.hasMoved = c.hasMoved;
        handler.lastPosX = c.lastPosX;
        handler.lastPosY = c.lastPosY;
        handler.lastPosZ = c.lastPosZ;
        handler.floatingTickCount = c.floatingTickCount;
        handler.networkTickCount = c.networkTickCount;
        handler.field_175090_f = c.lastMoveStallTick;
        handler.lastSentPingPacket = c.lastSentPingPacket;
        pendingClientbound.clear();
        pendingClientbound.addAll(c.pendingClientbound);
        serverbound.clear();
        lastReportedPosX = c.lastReportedPosX;
        lastReportedPosY = c.lastReportedPosY;
        lastReportedPosZ = c.lastReportedPosZ;
        lastReportedYaw = c.lastReportedYaw;
        lastReportedPitch = c.lastReportedPitch;
        positionUpdateTicks = c.positionUpdateTicks;
        lastSprinting = c.lastSprinting;
        lastSneaking = c.lastSneaking;
        prevRightClick = c.prevRightClick;
        rightClickDelay = c.rightClickDelay;
        pitch = c.pitch;
        tickIndex = c.tickIndex;
    }

    private static String formatVec(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.4f, %.4f, %.4f", x, y, z);
    }

    public static final class ServerCheckpoint {
        double posX;
        double posY;
        double posZ;
        float yaw;
        float pitchRot;
        double velX;
        double velY;
        double velZ;
        float fallDistance;
        boolean onGround;
        int hurtResistantTime;
        int hurtTime;
        float lastDamage;
        boolean velocityChanged;
        boolean sneaking;
        boolean sprinting;
        boolean hasMoved;
        double lastPosX;
        double lastPosY;
        double lastPosZ;
        int floatingTickCount;
        int networkTickCount;
        int lastMoveStallTick;
        long lastSentPingPacket;
        List<Packet> pendingClientbound;
        double lastReportedPosX;
        double lastReportedPosY;
        double lastReportedPosZ;
        float lastReportedYaw;
        float lastReportedPitch;
        int positionUpdateTicks;
        boolean lastSprinting;
        boolean lastSneaking;
        boolean prevRightClick;
        int rightClickDelay;
        float pitch;
        int tickIndex;
        int journalSize;
    }
}
