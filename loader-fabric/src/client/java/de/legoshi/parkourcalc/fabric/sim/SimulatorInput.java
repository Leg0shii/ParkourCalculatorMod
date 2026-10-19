package de.legoshi.parkourcalc.fabric.sim;

import de.legoshi.parkourcalc.core.ui.InputRow;
//? if >=1.21.2 {
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
//?}
//? if >=26.1
import net.minecraft.world.phys.Vec2;

/**
 * Input handler for the movement simulator.
 * Converts InputRow data into Minecraft's input format.
 */
//? if >=1.21.2 {
public class SimulatorInput extends ClientInput {
//?} else {
/*public class SimulatorInput extends net.minecraft.client.player.Input {

    public Input keyPresses = Input.EMPTY;
*///?}

    private InputRow data = new InputRow();

    public void setData(InputRow data) {
        this.data = data;
    }

    //? if >=26.1 {
    @Override
    public void tick() {
        seedKeyPresses(new Input(
                data.isKeyActive(InputRow.Key.W),
                data.isKeyActive(InputRow.Key.S),
                data.isKeyActive(InputRow.Key.A),
                data.isKeyActive(InputRow.Key.D),
                data.isKeyActive(InputRow.Key.JUMP),
                data.isKeyActive(InputRow.Key.SNEAK),
                data.isKeyActive(InputRow.Key.SPRINT)
        ));
    }

    public void seedKeyPresses(Input presses) {
        this.keyPresses = presses != null ? presses : Input.EMPTY;
        float forward = axisValue(this.keyPresses.forward(), this.keyPresses.backward());
        float strafe = axisValue(this.keyPresses.left(), this.keyPresses.right());
        this.moveVector = new Vec2(strafe, forward).normalized();
    }
    //?} else {
    /*@Override
    public void tick(boolean isMovingSlowly, float slowFactor) {
        this.keyPresses = new Input(
                data.isKeyActive(InputRow.Key.W),
                data.isKeyActive(InputRow.Key.S),
                data.isKeyActive(InputRow.Key.A),
                data.isKeyActive(InputRow.Key.D),
                data.isKeyActive(InputRow.Key.JUMP),
                data.isKeyActive(InputRow.Key.SNEAK),
                data.isKeyActive(InputRow.Key.SPRINT)
        );
        applyKeyPresses();
        if (isMovingSlowly) {
            this.leftImpulse *= slowFactor;
            this.forwardImpulse *= slowFactor;
        }
    }

    public void seedKeyPresses(Input presses) {
        this.keyPresses = presses != null ? presses : Input.EMPTY;
        applyKeyPresses();
    }

    private void applyKeyPresses() {
        //? if <1.21.2 {
        /^this.up = this.keyPresses.forward();
        this.down = this.keyPresses.backward();
        this.left = this.keyPresses.left();
        this.right = this.keyPresses.right();
        this.jumping = this.keyPresses.jump();
        this.shiftKeyDown = this.keyPresses.shift();
        ^///?}
        this.forwardImpulse = axisValue(this.keyPresses.forward(), this.keyPresses.backward());
        this.leftImpulse = axisValue(this.keyPresses.left(), this.keyPresses.right());
    }
    *///?}

    private static float axisValue(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        }
        return positive ? 1.0F : -1.0F;
    }
}
