package de.legoshi.parkourcalc.forge;

import de.legoshi.parkourcalc.forge.core.sim.PlaybackMoveState;
import net.minecraft.util.MovementInput;

@SuppressWarnings("DuplicatedCode")
final class PlaybackMovementInput extends MovementInput {

    private final ForgePlaybackBridge bridge;
    private final PlaybackMoveState state = new PlaybackMoveState();

    PlaybackMovementInput(ForgePlaybackBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void updatePlayerMoveState() {
        state.update(bridge.getCurrentRow(), de.legoshi.parkourcalc.forge.sim.BpkModBridge.rules().normaliseInput());
        this.moveStrafe = state.moveStrafe;
        this.moveForward = state.moveForward;
        this.jump = state.jump;
        this.sneak = state.sneak;
    }
}
