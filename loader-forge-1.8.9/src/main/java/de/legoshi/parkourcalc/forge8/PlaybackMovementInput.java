package de.legoshi.parkourcalc.forge8;

import de.legoshi.parkourcalc.core.ui.InputRow;
import net.minecraft.util.MovementInput;

final class PlaybackMovementInput extends MovementInput {

    private final Forge8PlaybackBridge bridge;

    public PlaybackMovementInput(Forge8PlaybackBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void updatePlayerMoveState() {
        InputRow row = bridge.getCurrentRow();
        this.moveStrafe = 0.0F;
        this.moveForward = 0.0F;
        this.jump = false;
        this.sneak = false;
        if (row == null) return;
        if (row.isKeyActive(InputRow.Key.W)) this.moveForward += 1.0F;
        if (row.isKeyActive(InputRow.Key.S)) this.moveForward -= 1.0F;
        if (row.isKeyActive(InputRow.Key.A)) this.moveStrafe += 1.0F;
        if (row.isKeyActive(InputRow.Key.D)) this.moveStrafe -= 1.0F;
        this.jump = row.isKeyActive(InputRow.Key.JUMP);
        this.sneak = row.isKeyActive(InputRow.Key.SNEAK);
        if (this.sneak) {
            this.moveStrafe *= 0.3F;
            this.moveForward *= 0.3F;
        }
    }
}
