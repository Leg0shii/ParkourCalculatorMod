package de.legoshi.parkourcalc.forge;

import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.forge.sim.GhostPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
//? if >=1.12 {
import net.minecraft.entity.SharedMonsterAttributes;
//?}
import net.minecraft.entity.player.EntityPlayerMP;
//? if >=1.12 {
import net.minecraft.init.MobEffects;
import net.minecraft.network.play.client.CPacketPlayer;
//?}
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.MouseHelper;
import net.minecraft.util.MovementInput;
//? if <1.12 {
/*import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.potion.Potion;
*///?}

import java.util.UUID;

@SuppressWarnings("DuplicatedCode")
public final class ForgePlaybackBridge implements PlaybackBridge {

    @Override
    public boolean isGamePaused() {
        return Minecraft.getMinecraft().isGamePaused();
    }

    private static final int EFFECT_DURATION_TICKS = 20000;
    private static final int GHOST_ENTITY_ID = -2100000000;

    private final InputRow currentRow = new InputRow();
    private MovementInput originalInput;
    private boolean ghostMode;
    private GhostPlayerEntity ghost;
    private MouseHelper originalMouseHelper;

    InputRow getCurrentRow() {
        return currentRow;
    }

    GhostPlayerEntity ghostEntity() {
        return ghost;
    }

    void installPlaybackInput(EntityPlayerSP player) {
        if (originalInput != null) return;
        originalInput = player.movementInput;
        player.movementInput = ghostMode ? new FrozenMovementInput() : new PlaybackMovementInput(this);
    }

    void restorePlaybackInput(EntityPlayerSP player) {
        if (originalInput == null) return;
        if (player.movementInput instanceof PlaybackMovementInput || player.movementInput instanceof FrozenMovementInput) {
            player.movementInput = originalInput;
        }
        originalInput = null;
    }

    void resetInputOverride() {
        originalInput = null;
    }

    @Override
    public boolean isSingleplayer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getCurrentServerData() != null) return false;
        IntegratedServer s = mc.getIntegratedServer();
        if (s == null) return false;
        return !s.getPublic();
    }

    @Override
    public boolean supportsMultiplayerPlayback() {
        Minecraft mc = Minecraft.getMinecraft();
        return McClient.player() != null && McClient.world() != null;
    }

    @Override
    public void beginReplayLockstep() {
        de.legoshi.parkourcalc.core.ui.Settings s = ForgeParkourCalculator.settings();
        if (s == null || !s.lockstepReplay) return;
        if (!isSingleplayer()) return;
        ReplayLockstep.engageForStart();
    }

    @Override
    public void releaseReplayLockstep() {
        ReplayLockstep.releaseStartArm();
    }

    private void beginGhostPlayback(Vec3dCore pos, Vec3dCore vel, float yaw, de.legoshi.parkourcalc.core.sim.Checkpoint carry) {
        ghostMode = true;
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = McClient.world();
        EntityPlayerSP client = McClient.player();
        if (world == null || client == null) return;
        removeGhostEntity();
        GhostPlayerEntity g = new GhostPlayerEntity(world, client.getGameProfile());
        g.setInput(currentRow);
        g.setLocationAndAngles(pos.x, pos.y, pos.z, yaw, client.rotationPitch);
        g.renderYawOffset = yaw;
        g.prevRenderYawOffset = yaw;
        g.rotationYawHead = yaw;
        g.prevRotationYawHead = yaw;
        g.motionX = vel.x;
        g.motionY = vel.y;
        g.motionZ = vel.z;
        if (carry != null) {
            g.applyCarry(carry);
        } else {
            g.onGround = true;
            g.fallDistance = 0.0F;
        }
        if (!isPairedSimulationOn()) {
            g.fallDistance = 0.0F;
        }
        //? if <1.12 {
        /*g.getDataWatcher().updateObject(10, client.getDataWatcher().getWatchableObjectByte(10));
        *///?}
        world.addEntityToWorld(GHOST_ENTITY_ID, g);
        ghost = g;
        client.moveStrafing = 0.0F;
        client.moveForward = 0.0F;
        client.isJumping = false;
        if (originalMouseHelper == null) {
            originalMouseHelper = mc.mouseHelper;
            mc.mouseHelper = new FrozenMouseHelper();
        }
        mc.setRenderViewEntity(g);
    }

    void syncFrozenPlayerToServer() {
        if (!ghostMode) return;
        EntityPlayerSP p = McClient.player();
        //? if >=1.12 {
        if (p == null || p.connection == null || p.isRiding()) return;
        //?} else {
        /*if (p == null || p.sendQueue == null || p.isRiding()) return;
        *///?}
        double minY = p.getEntityBoundingBox().minY;
        double d0 = p.posX - p.lastReportedPosX;
        double d1 = minY - p.lastReportedPosY;
        double d2 = p.posZ - p.lastReportedPosZ;
        p.positionUpdateTicks++;
        if (d0 * d0 + d1 * d1 + d2 * d2 > 9.0E-4D || p.positionUpdateTicks >= 20) {
            //? if >=1.12 {
            p.connection.sendPacket(new CPacketPlayer.Position(p.posX, minY, p.posZ, p.onGround));
            //?} else {
            /*p.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(p.posX, minY, p.posZ, p.onGround));
            *///?}
            p.lastReportedPosX = p.posX;
            p.lastReportedPosY = minY;
            p.lastReportedPosZ = p.posZ;
            p.positionUpdateTicks = 0;
        } else {
            //? if >=1.12 {
            p.connection.sendPacket(new CPacketPlayer(p.onGround));
            //?} else {
            /*p.sendQueue.addToSendQueue(new C03PacketPlayer(p.onGround));
            *///?}
        }
    }

    void endGhostPlayback() {
        Minecraft mc = Minecraft.getMinecraft();
        if (originalMouseHelper != null) {
            if (mc.mouseHelper instanceof FrozenMouseHelper || mc.mouseHelper instanceof ReplicaMouseHelper) {
                mc.mouseHelper = originalMouseHelper;
            }
            originalMouseHelper = null;
        }
        if (ghost != null && mc.getRenderViewEntity() == ghost && McClient.player() != null) {
            mc.setRenderViewEntity(McClient.player());
        }
        removeGhostEntity();
        ghostMode = false;
    }

    private void removeGhostEntity() {
        GhostPlayerEntity g = ghost;
        ghost = null;
        if (g == null) return;
        WorldClient world = McClient.world();
        //? if >=1.12 {
        if (world != null && g.world == world) {
        //?} else {
        /*if (world != null && g.worldObj == world) {
        *///?}
            world.removeEntityFromWorld(GHOST_ENTITY_ID);
        }
    }

    @Override
    public void teleport(Vec3dCore pos, Vec3dCore vel, float yaw, de.legoshi.parkourcalc.core.sim.Checkpoint carry) {
        if (!isSingleplayer()) {
            beginGhostPlayback(pos, vel, yaw, carry);
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP client = McClient.player();
        if (client == null) return;
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return;
        UUID uuid = client.getUniqueID();
        server.addScheduledTask(() -> {
            EntityPlayerMP sp = serverPlayer(server, uuid);
            if (sp == null) return;
            sp.setPositionAndRotation(pos.x, pos.y, pos.z, yaw, sp.rotationPitch);
            sp.motionX = vel.x;
            sp.motionY = vel.y;
            sp.motionZ = vel.z;
            if (carry != null) {
                de.legoshi.parkourcalc.forge.sim.SimulatorEntity.applyCheckpoint(sp, carry);
            } else {
                sp.setSprinting(false);
                sp.setSneaking(false);
                sp.onGround = true;
            }
            de.legoshi.parkourcalc.forge.sim.paired.PairedCheckpoint.applyRestartState(sp, carry);
        });
        client.setPositionAndRotation(pos.x, pos.y, pos.z, yaw, client.rotationPitch);
        client.renderYawOffset = yaw;
        client.prevRenderYawOffset = yaw;
        client.rotationYawHead = yaw;
        client.prevRotationYawHead = yaw;
        client.motionX = vel.x;
        client.motionY = vel.y;
        client.motionZ = vel.z;
        double[] pendingStomp = de.legoshi.parkourcalc.forge.sim.paired.PairedCheckpoint.pendingVelocity(carry);
        if (pendingStomp != null) {
            client.motionX = pendingStomp[0];
            client.motionY = pendingStomp[1];
            client.motionZ = pendingStomp[2];
            if (de.legoshi.parkourcalc.core.DebugFlags.PAIRED_DIAGNOSTICS) {
                System.out.println("[PC-NET] restart applied pending velocity flush "
                        + pendingStomp[0] + "," + pendingStomp[1] + "," + pendingStomp[2]);
            }
        }
        if (carry != null) {
            de.legoshi.parkourcalc.forge.sim.SimulatorEntity.applyCheckpoint(client, carry);
        } else {
            client.onGround = true;
            client.fallDistance = 0.0F;
        }
        if (!isPairedSimulationOn()) {
            client.fallDistance = 0.0F;
        }
        // Suppress onUpdateWalkingPlayer's position packet until the server's scheduled
        // setPlayerLocation arms targetPos, otherwise the client races and trips moved-wrongly.
        client.lastReportedPosX = pos.x;
        client.lastReportedPosY = client.getEntityBoundingBox().minY;
        client.lastReportedPosZ = pos.z;
        client.lastReportedYaw = yaw;
        client.lastReportedPitch = client.rotationPitch;
        client.positionUpdateTicks = 0;
        client.renderArmYaw = yaw;
        client.prevRenderArmYaw = yaw;
        client.renderArmPitch = client.rotationPitch;
        client.prevRenderArmPitch = client.rotationPitch;
        if (de.legoshi.parkourcalc.core.DebugFlags.PAIRED_DIAGNOSTICS) {
            System.out.println("[PC-NET] teleport snap pos=" + pos.x + "," + pos.y + "," + pos.z
                    + " vel=" + vel.x + "," + vel.y + "," + vel.z
                    + " carry=" + (carry == null ? "none" : carry.getClass().getSimpleName())
                    + " clientFall=" + client.fallDistance + " clientOnGround=" + client.onGround);
        }
    }

    private static EntityPlayerMP serverPlayer(IntegratedServer server, UUID uuid) {
        //? if >=1.12 {
        return server.getPlayerList().getPlayerByUUID(uuid);
        //?} else {
        /*return server.getConfigurationManager().getPlayerByUUID(uuid);
        *///?}
    }

    @Override
    public void setKey(InputRow.Key key, boolean pressed) {
        currentRow.setKeyActive(key, pressed);
        if (ghostMode) return;
        KeyBinding kb = bindFor(key);
        if (kb == null) return;
        KeyBinding.setKeyBindState(kb.getKeyCode(), pressed);
        if (pressed && isClickKey(key)) {
            KeyBinding.onTick(kb.getKeyCode());
        }
    }

    private static boolean isClickKey(InputRow.Key key) {
        return key == InputRow.Key.LEFT_CLICK || key == InputRow.Key.RIGHT_CLICK;
    }

    @Override
    public void setYaw(float absoluteYaw) {
        if (ghostMode) {
            if (ghost == null) return;
            ghost.rotationYaw = absoluteYaw;
            ghost.prevRotationYaw = absoluteYaw;
            return;
        }
        EntityPlayerSP p = McClient.player();
        if (p == null) return;
        p.rotationYaw = absoluteYaw;
        p.prevRotationYaw = absoluteYaw;
        p.renderArmYaw = absoluteYaw;
        p.prevRenderArmYaw = absoluteYaw;
    }

    @Override
    public void setHeadYaw(float absoluteYaw) {
        if (ghostMode) {
            if (ghost == null) return;
            ghost.rotationYawHead = absoluteYaw;
            return;
        }
        EntityPlayerSP p = McClient.player();
        if (p == null) return;
        p.rotationYawHead = absoluteYaw;
    }

    @Override
    public void setPitch(float absolutePitch) {
        if (ghostMode) {
            if (ghost == null) return;
            ghost.rotationPitch = absolutePitch;
            ghost.prevRotationPitch = absolutePitch;
            return;
        }
        EntityPlayerSP p = McClient.player();
        if (p == null) return;
        p.rotationPitch = absolutePitch;
        p.prevRotationPitch = absolutePitch;
        p.renderArmPitch = absolutePitch;
        p.prevRenderArmPitch = absolutePitch;
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
        EntityPlayerSP p = McClient.player();
        if (p == null) return;
        p.flyToggleTimer = 0;
        if (p.capabilities.isFlying) {
            p.capabilities.isFlying = false;
            p.sendPlayerAbilities();
        }
    }

    @Override
    public void closeUI() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null) {
            mc.displayGuiScreen(null);
            if (mc.currentScreen == null) {
                mc.setIngameFocus();
            }
        }
    }

    @Override
    public void applyEffects(int speedAmplifier, int jumpBoostAmplifier) {
        if (ghostMode) {
            if (ghost == null) return;
            //? if >=1.12 {
            ghost.removePotionEffect(MobEffects.SPEED);
            ghost.removePotionEffect(MobEffects.JUMP_BOOST);
            if (speedAmplifier > 0) {
                ghost.addPotionEffect(new PotionEffect(MobEffects.SPEED, EFFECT_DURATION_TICKS, clientAmplifier(speedAmplifier), false, false));
            }
            if (jumpBoostAmplifier > 0) {
                ghost.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, EFFECT_DURATION_TICKS, clientAmplifier(jumpBoostAmplifier), false, false));
            //?} else {
            /*ghost.removePotionEffect(Potion.moveSpeed.id);
            ghost.removePotionEffect(Potion.jump.id);
            if (speedAmplifier > 0) {
                ghost.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, EFFECT_DURATION_TICKS, clientAmplifier(speedAmplifier), false, false));
            }
            if (jumpBoostAmplifier > 0) {
                ghost.addPotionEffect(new PotionEffect(Potion.jump.id, EFFECT_DURATION_TICKS, clientAmplifier(jumpBoostAmplifier), false, false));
            *///?}
            }
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP client = McClient.player();
        if (client == null) return;
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return;
        UUID uuid = client.getUniqueID();
        server.addScheduledTask(() -> {
            EntityPlayerMP sp = serverPlayer(server, uuid);
            if (sp == null) return;
            //? if >=1.12 {
            sp.removePotionEffect(MobEffects.SPEED);
            sp.removePotionEffect(MobEffects.JUMP_BOOST);
            if (speedAmplifier > 0) {
                sp.addPotionEffect(new PotionEffect(MobEffects.SPEED, EFFECT_DURATION_TICKS, clientAmplifier(speedAmplifier), false, false));
            }
            if (jumpBoostAmplifier > 0) {
                sp.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, EFFECT_DURATION_TICKS, clientAmplifier(jumpBoostAmplifier), false, false));
            //?} else {
            /*sp.removePotionEffect(Potion.moveSpeed.id);
            sp.removePotionEffect(Potion.jump.id);
            if (speedAmplifier > 0) {
                sp.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, EFFECT_DURATION_TICKS, clientAmplifier(speedAmplifier), false, false));
            }
            if (jumpBoostAmplifier > 0) {
                sp.addPotionEffect(new PotionEffect(Potion.jump.id, EFFECT_DURATION_TICKS, clientAmplifier(jumpBoostAmplifier), false, false));
            *///?}
            }
        });
        applyClientEffects(client, speedAmplifier, jumpBoostAmplifier);
    }

    private static void applyClientEffects(EntityPlayerSP client, int speedAmplifier, int jumpBoostAmplifier) {
        //? if >=1.12 {
        client.removePotionEffect(MobEffects.SPEED);
        client.removePotionEffect(MobEffects.JUMP_BOOST);
        MobEffects.SPEED.removeAttributesModifiersFromEntity(client, client.getAttributeMap(), 0);
        if (speedAmplifier > 0) {
            client.addPotionEffect(new PotionEffect(MobEffects.SPEED, EFFECT_DURATION_TICKS, clientAmplifier(speedAmplifier), false, false));
            MobEffects.SPEED.applyAttributesModifiersToEntity(client, client.getAttributeMap(), clientAmplifier(speedAmplifier));
        }
        if (jumpBoostAmplifier > 0) {
            client.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, EFFECT_DURATION_TICKS, clientAmplifier(jumpBoostAmplifier), false, false));
        //?} else {
        /*client.removePotionEffect(Potion.moveSpeed.id);
        client.removePotionEffect(Potion.jump.id);
        Potion.moveSpeed.removeAttributesModifiersFromEntity(client, client.getAttributeMap(), 0);
        if (speedAmplifier > 0) {
            client.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, EFFECT_DURATION_TICKS, clientAmplifier(speedAmplifier), false, false));
            Potion.moveSpeed.applyAttributesModifiersToEntity(client, client.getAttributeMap(), clientAmplifier(speedAmplifier));
        }
        if (jumpBoostAmplifier > 0) {
            client.addPotionEffect(new PotionEffect(Potion.jump.id, EFFECT_DURATION_TICKS, clientAmplifier(jumpBoostAmplifier), false, false));
        *///?}
        }
    }

    private static int clientAmplifier(int level) {
        return (byte) (level - 1);
    }

    @Override
    public void setHotbarSlot(int slotZeroBased) {
        if (ghostMode) return;
        if (slotZeroBased < 0 || slotZeroBased > 8) return;
        EntityPlayerSP p = McClient.player();
        if (p == null) return;
        p.inventory.currentItem = slotZeroBased;
    }

    private static boolean isPairedSimulationOn() {
        de.legoshi.parkourcalc.core.ui.Settings s = ForgeParkourCalculator.settings();
        return s != null && s.pairedSimulation;
    }

    private static final class ReplaySample {
        final int tick;
        final double x;
        final double y;
        final double z;
        final double vx;
        final double vy;
        final double vz;
        final boolean onGround;
        final float fallDistance;
        final float health;
        final double[] serverVel;
        final String serverDesc;

        ReplaySample(int tick, double x, double y, double z, double vx, double vy, double vz, boolean onGround,
                     float fallDistance, float health, double[] serverVel, String serverDesc) {
            this.tick = tick;
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.onGround = onGround;
            this.fallDistance = fallDistance;
            this.health = health;
            this.serverVel = serverVel;
            this.serverDesc = serverDesc;
        }
    }

    private final java.util.List<ReplaySample> replaySamples = new java.util.ArrayList<ReplaySample>();

    private EntityPlayerMP serverSideSelf() {
        if (ghostMode) return null;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP client = McClient.player();
        IntegratedServer server = mc.getIntegratedServer();
        if (client == null || server == null) return null;
        return serverPlayer(server, client.getUniqueID());
    }

    private static String describeRealServer(EntityPlayerMP sp) {
        String handlerPart = " handler=unavailable";
        //? if >=1.12 {
        if (sp.connection != null) {
            handlerPart = " awaitingTeleport=" + (sp.connection.targetPos != null)
                    + " floating=" + sp.connection.floatingTickCount
                    + " lastGood=" + sp.connection.lastGoodX
                    + "," + sp.connection.lastGoodY
                    + "," + sp.connection.lastGoodZ;
        }
        //?} else {
        /*if (sp.playerNetServerHandler != null) {
            handlerPart = " hasMoved=" + sp.playerNetServerHandler.hasMoved
                    + " floating=" + sp.playerNetServerHandler.floatingTickCount
                    + " lastPos=" + sp.playerNetServerHandler.lastPosX
                    + "," + sp.playerNetServerHandler.lastPosY
                    + "," + sp.playerNetServerHandler.lastPosZ;
        }
        *///?}
        return "realSrv=" + sp.motionX + "," + sp.motionY + "," + sp.motionZ
                + " fall=" + sp.fallDistance
                + " g=" + sp.onGround
                + " spr=" + sp.isSprinting()
                + " sneak=" + sp.isSneaking()
                + " velChanged=" + sp.velocityChanged
                + " hurtResist=" + sp.hurtResistantTime
                + " hurtTime=" + sp.hurtTime
                + " lastDmg=" + sp.lastDamage
                + " fire=" + sp.fire
                + handlerPart;
    }

    @Override
    public void beginPlaybackCapture() {
        replaySamples.clear();
    }

    @Override
    public void capturePlaybackSample(int tickIndex) {
        net.minecraft.entity.player.EntityPlayer subject = ghostMode ? ghost : McClient.player();
        if (subject == null) return;
        EntityPlayerMP sp = serverSideSelf();
        replaySamples.add(new ReplaySample(tickIndex, subject.posX, subject.posY, subject.posZ,
                subject.motionX, subject.motionY, subject.motionZ, subject.onGround,
                subject.fallDistance, subject.getHealth(),
                sp == null ? null : new double[]{sp.motionX, sp.motionY, sp.motionZ},
                sp == null ? "realSrv=unavailable" : describeRealServer(sp)));
    }

    private static void printAccumulatorLine(ReplaySample sample) {
        System.out.println("[PC-ACC] T" + (sample.tick + 1)
                + " " + sample.serverDesc
                + " | " + de.legoshi.parkourcalc.forge.sim.paired.PairedCheckpoint.describeServer(
                        ForgeParkourCalculator.simCheckpoint(sample.tick + 1)));
    }

    private static boolean velMatches(double[] real, de.legoshi.parkourcalc.core.sim.Checkpoint pair) {
        double[] p = de.legoshi.parkourcalc.forge.sim.paired.PairedCheckpoint.serverVelocity(pair);
        if (p == null) return true;
        double dx = real[0] - p[0];
        double dy = real[1] - p[1];
        double dz = real[2] - p[2];
        return dx * dx + dy * dy + dz * dz <= 1.0e-18;
    }

    private void reportAccumulator() {
        int first = -1;
        for (ReplaySample sample : replaySamples) {
            if (sample.serverVel == null) continue;
            if (velMatches(sample.serverVel, ForgeParkourCalculator.simCheckpoint(sample.tick + 1))
                    || velMatches(sample.serverVel, ForgeParkourCalculator.simCheckpoint(sample.tick))) {
                continue;
            }
            first = sample.tick;
            break;
        }
        if (first < 0) {
            System.out.println("[PC-ACC] server accumulator matches the pair across " + replaySamples.size()
                    + " ticks (phase-tolerant)");
            return;
        }
        System.out.println("[PC-ACC] server accumulator first differs at T" + (first + 1));
        for (ReplaySample sample : replaySamples) {
            if (sample.tick < first - 2 || sample.tick > first + 6) continue;
            printAccumulatorLine(sample);
        }
    }

    @Override
    public void finishPlaybackCapture() {
        if (replaySamples.isEmpty()) return;
        java.util.List<de.legoshi.parkourcalc.core.sim.TickState> states = ForgeParkourCalculator.simStates();
        int firstDiverged = -1;
        for (ReplaySample sample : replaySamples) {
            int idx = sample.tick + 1;
            if (idx < 0 || idx >= states.size()) continue;
            de.legoshi.parkourcalc.core.sim.TickState sim = states.get(idx);
            double d = Math.max(Math.abs(sample.x - sim.position.x),
                    Math.max(Math.abs(sample.y - sim.position.y), Math.abs(sample.z - sim.position.z)));
            if (d > 1.0e-6) {
                firstDiverged = sample.tick;
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
            if (sample.tick < firstDiverged - 3 || sample.tick > firstDiverged + 8) continue;
            int idx = sample.tick + 1;
            String simPart;
            if (idx >= 0 && idx < states.size()) {
                de.legoshi.parkourcalc.core.sim.TickState sim = states.get(idx);
                simPart = "sim=" + sim.position.x + "," + sim.position.y + "," + sim.position.z
                        + " v=" + sim.velocity.x + "," + sim.velocity.y + "," + sim.velocity.z
                        + " g=" + sim.onGround;
            } else {
                simPart = "sim=out-of-range";
            }
            System.out.println("[PC-REPLAY] T" + (sample.tick + 1)
                    + " real=" + sample.x + "," + sample.y + "," + sample.z
                    + " v=" + sample.vx + "," + sample.vy + "," + sample.vz
                    + " g=" + sample.onGround
                    + " fall=" + sample.fallDistance
                    + " hp=" + sample.health
                    + " | " + simPart);
            printAccumulatorLine(sample);
        }
        replaySamples.clear();
    }

    @Override
    public void dumpPlayerState(int tickIndex) {
        net.minecraft.entity.player.EntityPlayer p = ghost != null ? ghost : McClient.player();
        if (p == null) return;
        //? if >=1.12 {
        PotionEffect spd = p.getActivePotionEffect(MobEffects.SPEED);
        PotionEffect jmp = p.getActivePotionEffect(MobEffects.JUMP_BOOST);
        double mvSp = p.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue();
        //?} else {
        /*PotionEffect spd = p.getActivePotionEffect(Potion.moveSpeed);
        PotionEffect jmp = p.getActivePotionEffect(Potion.jump);
        double mvSp = p.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.movementSpeed).getAttributeValue();
        *///?}
        System.out.println("[PC-STATE play] t=" + tickIndex
                + " pos=" + p.posX + "," + p.posY + "," + p.posZ
                + " mot=" + p.motionX + "," + p.motionY + "," + p.motionZ
                + " yaw=" + p.rotationYaw
                + " onG=" + p.onGround
                + " spr=" + p.isSprinting()
                + " sne=" + p.isSneaking()
                //? if >=1.12 {
                + " colH=" + p.collidedHorizontally
                //?} else {
                /*+ " colH=" + p.isCollidedHorizontally
                *///?}
                + " mvF=" + p.moveForward
                + " mvS=" + p.moveStrafing
                + " spdAmp=" + (spd == null ? -1 : spd.getAmplifier())
                + " jmpAmp=" + (jmp == null ? -1 : jmp.getAmplifier())
                + " mvSpeed=" + mvSp);
    }


    private boolean replica;

    @Override
    public boolean beginReplica(boolean freezeMouse) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP client = McClient.player();
        if (client == null || McClient.world() == null) return false;
        if (replica) endReplica();
        for (InputRow.Key k : InputRow.Key.values()) currentRow.setKeyActive(k, false);
        beginGhostPlayback(new Vec3dCore(client.posX, client.posY, client.posZ), Vec3dCore.ZERO, client.rotationYaw, null);
        if (ghost == null) {
            ghostMode = false;
            return false;
        }
        ghost.rotationPitch = client.rotationPitch;
        ghost.prevRotationPitch = client.rotationPitch;
        installPlaybackInput(client);
        if (!freezeMouse) mc.mouseHelper = new ReplicaMouseHelper(this);
        replica = true;
        return true;
    }

    @Override
    public void endReplica() {
        if (!replica) return;
        replica = false;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP client = McClient.player();
        if (client != null) restorePlaybackInput(client);
        else resetInputOverride();
        endGhostPlayback();
        mc.objectMouseOver = null;
    }

    @Override
    public boolean replicaActive() {
        return replica && ghost != null;
    }

    @Override
    public int playerKeyMask() {
        GameSettings o = Minecraft.getMinecraft().gameSettings;
        return de.legoshi.parkourcalc.core.TurnReference.mask(o.keyBindForward.isKeyDown(), o.keyBindLeft.isKeyDown(),
                o.keyBindBack.isKeyDown(), o.keyBindRight.isKeyDown(), o.keyBindJump.isKeyDown(),
                o.keyBindSneak.isKeyDown(), o.keyBindSprint.isKeyDown());
    }

    private static KeyBinding bindFor(InputRow.Key key) {
        GameSettings o = Minecraft.getMinecraft().gameSettings;
        switch (key) {
            case W: return o.keyBindForward;
            case S: return o.keyBindBack;
            case A: return o.keyBindLeft;
            case D: return o.keyBindRight;
            case JUMP: return o.keyBindJump;
            case SNEAK: return o.keyBindSneak;
            case SPRINT: return o.keyBindSprint;
            case LEFT_CLICK: return o.keyBindAttack;
            case RIGHT_CLICK: return o.keyBindUseItem;
            default: return null;
        }
    }

    @Override
    public void closeInventory() {
        if (ghostMode) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP p = McClient.player();
        if (p == null) return;
        if (mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer) {
            p.closeScreen();
        }
    }
}
