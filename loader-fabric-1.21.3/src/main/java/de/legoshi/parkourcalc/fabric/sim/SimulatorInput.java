package de.legoshi.parkourcalc.fabric.sim;

import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;

/**
 * Input handler for the movement simulator.
 * Converts InputRow data into Minecraft's input format.
 */
public class SimulatorInput extends ClientInput {

    private InputRow data = new InputRow();

    public void setData(InputRow data) {
        this.data = data;
    }

    @Override
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

        this.forwardImpulse = axisValue(keyPresses.forward(), keyPresses.backward());
        this.leftImpulse = axisValue(keyPresses.left(), keyPresses.right());
        if (isMovingSlowly) {
            this.leftImpulse *= slowFactor;
            this.forwardImpulse *= slowFactor;
        }
    }

    private static float axisValue(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        }
        return positive ? 1.0F : -1.0F;
    }
}
