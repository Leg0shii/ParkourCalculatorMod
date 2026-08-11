package de.legoshi.parkourcalc.fabric;

import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;

final class PlaybackInput extends ClientInput {

    private final FabricPlaybackBridge bridge;

    PlaybackInput(FabricPlaybackBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void tick(boolean isMovingSlowly, float slowFactor) {
        InputRow row = bridge.getCurrentRow();
        boolean fwd = false, back = false, left = false, right = false, jump = false, sneak = false, sprint = false;
        if (row != null) {
            fwd = row.isKeyActive(InputRow.Key.W);
            back = row.isKeyActive(InputRow.Key.S);
            left = row.isKeyActive(InputRow.Key.A);
            right = row.isKeyActive(InputRow.Key.D);
            jump = row.isKeyActive(InputRow.Key.JUMP);
            sneak = row.isKeyActive(InputRow.Key.SNEAK);
            sprint = row.isKeyActive(InputRow.Key.SPRINT);
        }
        this.keyPresses = new Input(fwd, back, left, right, jump, sneak, sprint);
        this.forwardImpulse = axis(fwd, back);
        this.leftImpulse = axis(left, right);
        if (isMovingSlowly) {
            this.leftImpulse *= slowFactor;
            this.forwardImpulse *= slowFactor;
        }
    }

    private static float axis(boolean positive, boolean negative) {
        if (positive == negative) return 0.0F;
        return positive ? 1.0F : -1.0F;
    }
}
