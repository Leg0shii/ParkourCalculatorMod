package de.legoshi;

import de.legoshi.ui.InputTick;
import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;

public class SimulatorInput extends Input {

    private InputTick tick;

    public SimulatorInput(InputTick tick) {
        this.tick = tick;
    }

    public void setTick(InputTick tick) {
        this.tick = tick;
    }

    private static float getMovementMultiplier(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        } else {
            return positive ? 1.0F : -1.0F;
        }
    }

    public void tick() {
        this.playerInput = new PlayerInput(
                this.tick.w(),
                this.tick.s(),
                this.tick.a(),
                this.tick.d(),
                this.tick.j(),
                this.tick.n(),
                this.tick.p()
        );
        float f = getMovementMultiplier(this.playerInput.forward(), this.playerInput.backward());
        float g = getMovementMultiplier(this.playerInput.left(), this.playerInput.right());
        this.movementVector = (new Vec2f(g, f)).normalize();
    }

}
