package de.legoshi.parkourcalc.forge8.sim;

import com.mojang.authlib.GameProfile;
import de.legoshi.parkourcalc.forge.core.sim.PlayerSprintMachine;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/** 1.8.9 API surface uses net.minecraft.util.Vec3 and has no moveVertical field. */
public class SimulatorEntity extends EntityPlayer {

    public Vec3 startPosition;
    public Vec3 startVelocity = new Vec3(0.0, 0.0, 0.0);
    public float startYaw;

    private InputRow currentInput = new InputRow();
    private PlayerSprintMachine.State sprintState = PlayerSprintMachine.State.initial();

    private final ArrayList<Vec3dCore> subtickBuf = new ArrayList<Vec3dCore>(8);
    private boolean capturing = false;

    public void beginSubtickCapture() {
        subtickBuf.clear();
        capturing = true;
    }

    public List<Vec3dCore> endSubtickCapture() {
        capturing = false;
        List<Vec3dCore> result = new ArrayList<Vec3dCore>(subtickBuf);
        subtickBuf.clear();
        return result;
    }

    @Override
    public void moveEntity(double x, double y, double z) {
        if (!capturing) {
            super.moveEntity(x, y, z);
            return;
        }
        double bx = this.posX, by = this.posY, bz = this.posZ;
        super.moveEntity(x, y, z);
        double cx = this.posX - bx;
        double cy = this.posY - by;
        double cz = this.posZ - bz;

        if (subtickBuf.isEmpty()) {
            subtickBuf.add(new Vec3dCore(bx, by, bz));
        }
        subtickBuf.add(new Vec3dCore(bx, by + cy, bz));
        subtickBuf.add(new Vec3dCore(bx + cx, by + cy, bz));
        subtickBuf.add(new Vec3dCore(bx + cx, by + cy, bz + cz));
    }

    public SimulatorEntity(World world, GameProfile profile, Vec3 startPosition, Vec3 startVelocity, float startYaw) {
        super(world, profile);
        this.startPosition = startPosition;
        this.startVelocity = startVelocity;
        this.startYaw = startYaw;
        resetPlayer();
    }

    public void setInput(InputRow row) {
        this.currentInput = row;
    }

    public void resetPlayer() {
        this.noClip = true;
        this.motionX = startVelocity.xCoord;
        this.motionY = startVelocity.yCoord;
        this.motionZ = startVelocity.zCoord;
        this.setPosition(startPosition.xCoord, startPosition.yCoord, startPosition.zCoord);
        this.rotationYaw = startYaw;
        this.rotationPitch = 0.0F;

        this.currentInput = new InputRow();
        this.sprintState = PlayerSprintMachine.State.initial();
        this.onUpdate();
        this.onUpdate();

        this.setPosition(startPosition.xCoord, startPosition.yCoord, startPosition.zCoord);
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

    /** Prevent the simulator from pushing the real player or any other world entity. */
    @Override
    protected void collideWithNearbyEntities() {
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    /** Required: EntityLivingBase.moveEntityWithHeading is gated by isServerWorld. */
    @Override
    public boolean isServerWorld() {
        return true;
    }
}
