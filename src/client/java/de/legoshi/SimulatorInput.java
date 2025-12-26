package de.legoshi;

import de.legoshi.ui.InputRow;
import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;

/**
 * Input handler for the movement simulator.
 * Converts InputRow data into Minecraft's input format.
 */
public class SimulatorInput extends Input {

    private InputRow data = new InputRow();

    public void setData(InputRow data) {
        this.data = data;
    }

    @Override
    public void tick() {
        this.playerInput = new PlayerInput(
                data.isKeyActive(InputRow.Key.W),
                data.isKeyActive(InputRow.Key.S),
                data.isKeyActive(InputRow.Key.A),
                data.isKeyActive(InputRow.Key.D),
                data.isKeyActive(InputRow.Key.JUMP),
                data.isKeyActive(InputRow.Key.SNEAK),
                data.isKeyActive(InputRow.Key.SPRINT)
        );

        float forward = axisValue(playerInput.forward(), playerInput.backward());
        float strafe = axisValue(playerInput.left(), playerInput.right());
        this.movementVector = new Vec2f(strafe, forward).normalize();
    }

    private static float axisValue(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        }
        return positive ? 1.0F : -1.0F;
    }
}