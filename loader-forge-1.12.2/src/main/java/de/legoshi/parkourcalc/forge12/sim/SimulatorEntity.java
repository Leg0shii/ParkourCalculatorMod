package de.legoshi.parkourcalc.forge12.sim;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.forge.core.sim.PlayerSprintMachine;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** MCP 39: moveForward is the forward input, moveVertical is the swim/fly axis. */
public class SimulatorEntity extends EntityPlayer {

    public Vec3d startPosition;

    private InputRow currentInput = new InputRow();
    private PlayerSprintMachine.State sprintState = PlayerSprintMachine.State.initial();

    public SimulatorEntity(World world, GameProfile profile, Vec3d startPosition) {
        super(world, profile);
        this.startPosition = startPosition;
        resetPlayer();
    }

    public void setInput(InputRow row) {
        this.currentInput = row;
    }

    public void resetPlayer() {
        this.noClip = true;
        this.motionX = 0.0;
        this.motionY = 0.0;
        this.motionZ = 0.0;
        this.setPosition(startPosition.x, startPosition.y, startPosition.z);
        this.rotationYaw = 0.0F;
        this.rotationPitch = 0.0F;

        this.currentInput = new InputRow();
        this.sprintState = PlayerSprintMachine.State.initial();
        this.onUpdate();
        this.onUpdate();

        this.setPosition(startPosition.x, startPosition.y, startPosition.z);
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

    /** Prevent the simulator from pushing the real player or any other world entity. */
    @Override
    protected void collideWithNearbyEntities() {
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    /** Required: EntityLivingBase.func_191986_a (travel) is gated by isServerWorld. */
    @Override
    public boolean isServerWorld() {
        return true;
    }
}
