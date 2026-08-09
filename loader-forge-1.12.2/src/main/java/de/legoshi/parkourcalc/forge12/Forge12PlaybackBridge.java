package de.legoshi.parkourcalc.forge12;

import de.legoshi.parkourcalc.core.ports.PlaybackBridge;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.forge12.sim.GhostPlayerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.MouseHelper;
import net.minecraft.util.MovementInput;

import java.util.UUID;

@SuppressWarnings("DuplicatedCode")
public final class Forge12PlaybackBridge implements PlaybackBridge {

    @Override
    public boolean isGamePaused() {
        return Minecraft.getMinecraft().isGamePaused();
    }

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
        return mc.player != null && mc.world != null;
    }

    private void beginGhostPlayback(Vec3dCore pos, Vec3dCore vel, float yaw, de.legoshi.parkourcalc.core.sim.Checkpoint carry) {
        ghostMode = true;
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.world;
        EntityPlayerSP client = mc.player;
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
        }
        g.fallDistance = 0.0F;
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
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p == null || p.connection == null || p.isRiding()) return;
        double minY = p.getEntityBoundingBox().minY;
        double d0 = p.posX - p.lastReportedPosX;
        double d1 = minY - p.lastReportedPosY;
        double d2 = p.posZ - p.lastReportedPosZ;
        p.positionUpdateTicks++;
        if (d0 * d0 + d1 * d1 + d2 * d2 > 9.0E-4D || p.positionUpdateTicks >= 20) {
            p.connection.sendPacket(new CPacketPlayer.Position(p.posX, minY, p.posZ, p.onGround));
            p.lastReportedPosX = p.posX;
            p.lastReportedPosY = minY;
            p.lastReportedPosZ = p.posZ;
            p.positionUpdateTicks = 0;
        } else {
            p.connection.sendPacket(new CPacketPlayer(p.onGround));
        }
    }

    void endGhostPlayback() {
        Minecraft mc = Minecraft.getMinecraft();
        if (originalMouseHelper != null) {
            if (mc.mouseHelper instanceof FrozenMouseHelper) {
                mc.mouseHelper = originalMouseHelper;
            }
            originalMouseHelper = null;
        }
        if (ghost != null && mc.getRenderViewEntity() == ghost && mc.player != null) {
            mc.setRenderViewEntity(mc.player);
        }
        removeGhostEntity();
        ghostMode = false;
    }

    private void removeGhostEntity() {
        GhostPlayerEntity g = ghost;
        ghost = null;
        if (g == null) return;
        WorldClient world = Minecraft.getMinecraft().world;
        if (world != null && g.world == world) {
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
        EntityPlayerSP client = mc.player;
        if (client == null) return;
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return;
        UUID uuid = client.getUniqueID();
        server.addScheduledTask(() -> {
            EntityPlayerMP sp = server.getPlayerList().getPlayerByUUID(uuid);
            if (sp == null) return;
            sp.setPositionAndRotation(pos.x, pos.y, pos.z, yaw, sp.rotationPitch);
            sp.motionX = vel.x;
            sp.motionY = vel.y;
            sp.motionZ = vel.z;
            if (carry != null) {
                de.legoshi.parkourcalc.forge12.sim.SimulatorEntity.applyCheckpoint(sp, carry);
            } else {
                sp.setSprinting(false);
                sp.setSneaking(false);
                sp.onGround = true;
            }
            sp.velocityChanged = false;
        });
        client.setPositionAndRotation(pos.x, pos.y, pos.z, yaw, client.rotationPitch);
        client.renderYawOffset = yaw;
        client.prevRenderYawOffset = yaw;
        client.rotationYawHead = yaw;
        client.prevRotationYawHead = yaw;
        client.motionX = vel.x;
        client.motionY = vel.y;
        client.motionZ = vel.z;
        if (carry != null) {
            de.legoshi.parkourcalc.forge12.sim.SimulatorEntity.applyCheckpoint(client, carry);
        } else {
            client.onGround = true;
        }
        client.fallDistance = 0.0F;
        // Suppress onUpdateWalkingPlayer's position packet until the server's scheduled
        // setPlayerLocation arms targetPos, otherwise the client races and trips moved-wrongly.
        client.lastReportedPosX = pos.x;
        client.lastReportedPosY = client.getEntityBoundingBox().minY;
        client.lastReportedPosZ = pos.z;
        client.lastReportedYaw = yaw;
        client.lastReportedPitch = client.rotationPitch;
        client.positionUpdateTicks = 0;
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
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p == null) return;
        p.rotationYaw = absoluteYaw;
        p.prevRotationYaw = absoluteYaw;
    }

    @Override
    public void setHeadYaw(float absoluteYaw) {
        if (ghostMode) {
            if (ghost == null) return;
            ghost.rotationYawHead = absoluteYaw;
            return;
        }
        EntityPlayerSP p = Minecraft.getMinecraft().player;
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
        EntityPlayerSP p = Minecraft.getMinecraft().player;
        if (p == null) return;
        p.rotationPitch = absolutePitch;
        p.prevRotationPitch = absolutePitch;
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
        EntityPlayerSP p = Minecraft.getMinecraft().player;
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

    private static final int EFFECT_DURATION_TICKS = 20000;

    @Override
    public void applyEffects(int speedAmplifier, int jumpBoostAmplifier) {
        if (ghostMode) {
            if (ghost == null) return;
            ghost.removePotionEffect(MobEffects.SPEED);
            ghost.removePotionEffect(MobEffects.JUMP_BOOST);
            if (speedAmplifier > 0) {
                ghost.addPotionEffect(new PotionEffect(MobEffects.SPEED, EFFECT_DURATION_TICKS, speedAmplifier - 1, false, false));
            }
            if (jumpBoostAmplifier > 0) {
                ghost.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, EFFECT_DURATION_TICKS, jumpBoostAmplifier - 1, false, false));
            }
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP client = mc.player;
        if (client == null) return;
        IntegratedServer server = mc.getIntegratedServer();
        if (server == null) return;
        UUID uuid = client.getUniqueID();
        server.addScheduledTask(() -> {
            EntityPlayerMP sp = server.getPlayerList().getPlayerByUUID(uuid);
            if (sp == null) return;
            sp.removePotionEffect(MobEffects.SPEED);
            sp.removePotionEffect(MobEffects.JUMP_BOOST);
            if (speedAmplifier > 0) {
                sp.addPotionEffect(new PotionEffect(MobEffects.SPEED, EFFECT_DURATION_TICKS, speedAmplifier - 1, false, false));
            }
            if (jumpBoostAmplifier > 0) {
                sp.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, EFFECT_DURATION_TICKS, jumpBoostAmplifier - 1, false, false));
            }
        });
        applyClientEffects(client, speedAmplifier, jumpBoostAmplifier);
    }

    private static void applyClientEffects(EntityPlayerSP client, int speedAmplifier, int jumpBoostAmplifier) {
        client.removePotionEffect(MobEffects.SPEED);
        client.removePotionEffect(MobEffects.JUMP_BOOST);
        MobEffects.SPEED.removeAttributesModifiersFromEntity(client, client.getAttributeMap(), 0);
        if (speedAmplifier > 0) {
            client.addPotionEffect(new PotionEffect(MobEffects.SPEED, EFFECT_DURATION_TICKS, speedAmplifier - 1, false, false));
            MobEffects.SPEED.applyAttributesModifiersToEntity(client, client.getAttributeMap(), speedAmplifier - 1);
        }
        if (jumpBoostAmplifier > 0) {
            client.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, EFFECT_DURATION_TICKS, jumpBoostAmplifier - 1, false, false));
        }
    }

    @Override
    public void dumpPlayerState(int tickIndex) {
        net.minecraft.entity.player.EntityPlayer p = ghost != null ? ghost : Minecraft.getMinecraft().player;
        if (p == null) return;
        PotionEffect spd = p.getActivePotionEffect(MobEffects.SPEED);
        PotionEffect jmp = p.getActivePotionEffect(MobEffects.JUMP_BOOST);
        double mvSp = p.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue();
        System.out.println("[PC-STATE play] t=" + tickIndex
                + " pos=" + p.posX + "," + p.posY + "," + p.posZ
                + " mot=" + p.motionX + "," + p.motionY + "," + p.motionZ
                + " yaw=" + p.rotationYaw
                + " onG=" + p.onGround
                + " spr=" + p.isSprinting()
                + " sne=" + p.isSneaking()
                + " colH=" + p.collidedHorizontally
                + " mvF=" + p.moveForward
                + " mvS=" + p.moveStrafing
                + " spdAmp=" + (spd == null ? -1 : spd.getAmplifier())
                + " jmpAmp=" + (jmp == null ? -1 : jmp.getAmplifier())
                + " mvSpeed=" + mvSp);
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
        }
        return null;
    }
}
