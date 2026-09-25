package de.legoshi.parkourcalc.forge8.sim;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

public final class ReplayPlayerEntity extends AbstractClientPlayer {

    private NetworkPlayerInfo skinInfo;

    public ReplayPlayerEntity(World world, GameProfile profile) {
        super(world, profile);
        this.noClip = true;
    }

    public void setSkinProfile(GameProfile filledProfile) {
        skinInfo = new NetworkPlayerInfo(filledProfile);
    }

    public boolean hasSkinProfile() {
        return skinInfo != null;
    }

    @Override
    protected NetworkPlayerInfo getPlayerInfo() {
        return skinInfo;
    }

    public void pose(double x, double y, double z, float headYaw, float bodyYaw, float pitch,
                     float limbAmount, float limbPhase, float partialTicks, boolean sneaking, float swing) {
        setSneaking(sneaking);
        prevSwingProgress = swing;
        swingProgress = swing;
        isSwingInProgress = swing > 0f;
        setPosition(x, y, z);
        prevPosX = x;
        prevPosY = y;
        prevPosZ = z;
        lastTickPosX = x;
        lastTickPosY = y;
        lastTickPosZ = z;
        rotationYaw = headYaw;
        prevRotationYaw = headYaw;
        rotationYawHead = headYaw;
        prevRotationYawHead = headYaw;
        renderYawOffset = bodyYaw;
        prevRenderYawOffset = bodyYaw;
        rotationPitch = pitch;
        prevRotationPitch = pitch;
        prevLimbSwingAmount = limbAmount;
        limbSwingAmount = limbAmount;
        limbSwing = limbPhase + limbAmount * (1f - partialTicks);
    }

    @Override
    public void onUpdate() {
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected void collideWithNearbyEntities() {
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isSpectator() {
        return false;
    }
}
