package de.legoshi.parkourcalc.forge12.sim;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.forge.core.sim.PlayerSprintMachine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("DuplicatedCode")
public class GhostPlayerEntity extends AbstractClientPlayer {

    private InputRow currentInput = new InputRow();
    private PlayerSprintMachine.State sprintState = PlayerSprintMachine.State.initial();

    public GhostPlayerEntity(World world, GameProfile profile) {
        super(world, profile);
    }

    public void setInput(InputRow row) {
        this.currentInput = row;
    }

    public void applyCarry(de.legoshi.parkourcalc.core.sim.Checkpoint carry) {
        SimulatorEntity.applyCheckpoint(this, carry);
        if (carry instanceof SimulatorEntity.Checkpoint) {
            this.sprintState = ((SimulatorEntity.Checkpoint) carry).sprintState;
        }
    }

    @Override
    public void onLivingUpdate() {
        applyMovementInput();
        super.onLivingUpdate();
    }

    private void applyMovementInput() {
        PlayerSprintMachine.Inputs in = new PlayerSprintMachine.Inputs(
                currentInput.isKeyActive(InputRow.Key.W),
                currentInput.isKeyActive(InputRow.Key.S),
                currentInput.isKeyActive(InputRow.Key.A),
                currentInput.isKeyActive(InputRow.Key.D),
                currentInput.isKeyActive(InputRow.Key.JUMP),
                currentInput.isKeyActive(InputRow.Key.SNEAK),
                currentInput.isKeyActive(InputRow.Key.SPRINT),
                this.onGround,
                this.isHandActive(),
                this.isRiding(),
                this.collidedHorizontally,
                this.isPotionActive(MobEffects.BLINDNESS),
                this.capabilities.allowFlying,
                (float) this.getFoodStats().getFoodLevel()
        );

        PlayerSprintMachine.State seed = sprintState.withIsSprinting(this.isSprinting());
        PlayerSprintMachine.Outputs out = PlayerSprintMachine.tick(in, seed);
        sprintState = out.next;

        this.setSprinting(out.next.isSprinting);
        this.setSneaking(in.sneak);
        this.moveForward = out.moveForward;
        this.moveVertical = 0.0F;
        this.moveStrafing = out.moveStrafe;
        this.isJumping = out.isJumping;
    }

    @Override
    protected void collideWithNearbyEntities() {
    }

    @Override
    public void addExhaustion(float amount) {
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void outOfWorld() {
    }

    @Override
    public boolean isWearing(EnumPlayerModelParts part) {
        return Minecraft.getMinecraft().gameSettings.getModelParts().contains(part);
    }

    @Override
    public void clearActivePotions() {
        Map<Potion, PotionEffect> map = this.getActivePotionMap();
        if (map.isEmpty()) return;
        List<PotionEffect> all = new ArrayList<PotionEffect>(map.values());
        map.clear();
        for (PotionEffect e : all) {
            e.getPotion().removeAttributesModifiersFromEntity(this, this.getAttributeMap(), e.getAmplifier());
        }
    }

    @Override
    protected void onNewPotionEffect(PotionEffect effect) {
        effect.getPotion().applyAttributesModifiersToEntity(this, this.getAttributeMap(), effect.getAmplifier());
    }

    @Override
    protected void onChangedPotionEffect(PotionEffect effect, boolean reapply) {
        if (reapply) {
            effect.getPotion().removeAttributesModifiersFromEntity(this, this.getAttributeMap(), effect.getAmplifier());
            effect.getPotion().applyAttributesModifiersToEntity(this, this.getAttributeMap(), effect.getAmplifier());
        }
    }

    @Override
    protected void onFinishedPotionEffect(PotionEffect effect) {
        effect.getPotion().removeAttributesModifiersFromEntity(this, this.getAttributeMap(), effect.getAmplifier());
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    @Override
    public boolean isServerWorld() {
        return true;
    }
}
