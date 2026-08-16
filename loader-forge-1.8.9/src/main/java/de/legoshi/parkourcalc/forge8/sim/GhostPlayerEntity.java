package de.legoshi.parkourcalc.forge8.sim;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.core.ui.InputRow;
import de.legoshi.parkourcalc.forge.core.sim.PlayerSprintMachine;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
        SimulatorEntity.Checkpoint c = de.legoshi.parkourcalc.forge8.sim.paired.PairedCheckpoint.clientPart(carry);
        SimulatorEntity.applyCheckpoint(this, carry);
        if (c != null) {
            this.sprintState = c.sprintState;
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
                this.isUsingItem(),
                this.isRiding(),
                this.isCollidedHorizontally,
                this.isPotionActive(Potion.blindness),
                this.capabilities.allowFlying,
                (float) this.getFoodStats().getFoodLevel()
        );

        PlayerSprintMachine.State seed = sprintState.withIsSprinting(this.isSprinting());
        PlayerSprintMachine.Outputs out = PlayerSprintMachine.tick(in, seed);
        sprintState = out.next;

        this.setSprinting(out.next.isSprinting);
        this.setSneaking(in.sneak);
        this.moveForward = out.moveForward;
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
    protected void kill() {
    }

    @Override
    public void clearActivePotions() {
        Collection<PotionEffect> effects = this.getActivePotionEffects();
        if (effects.isEmpty()) return;
        List<PotionEffect> all = new ArrayList<PotionEffect>(effects);
        for (PotionEffect e : all) {
            this.removePotionEffect(e.getPotionID());
        }
    }

    @Override
    protected void onNewPotionEffect(PotionEffect effect) {
        Potion.potionTypes[effect.getPotionID()].applyAttributesModifiersToEntity(this, this.getAttributeMap(), effect.getAmplifier());
    }

    @Override
    protected void onChangedPotionEffect(PotionEffect effect, boolean reapply) {
        if (reapply) {
            Potion.potionTypes[effect.getPotionID()].removeAttributesModifiersFromEntity(this, this.getAttributeMap(), effect.getAmplifier());
            Potion.potionTypes[effect.getPotionID()].applyAttributesModifiersToEntity(this, this.getAttributeMap(), effect.getAmplifier());
        }
    }

    @Override
    protected void onFinishedPotionEffect(PotionEffect effect) {
        Potion.potionTypes[effect.getPotionID()].removeAttributesModifiersFromEntity(this, this.getAttributeMap(), effect.getAmplifier());
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isServerWorld() {
        return true;
    }
}
