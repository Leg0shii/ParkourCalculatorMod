package de.legoshi.parkourcalc.forge.core.sim;

import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackMoveStateTest {

    private static InputRow row(InputRow.Key... keys) {
        InputRow row = new InputRow();
        for (InputRow.Key k : keys) row.setKeyActive(k, true);
        return row;
    }

    @Test
    public void theAxesFollowTheRowKeys() {
        PlaybackMoveState state = new PlaybackMoveState();
        state.update(row(InputRow.Key.W, InputRow.Key.A, InputRow.Key.JUMP));
        assertEquals(1.0F, state.moveForward, 0f);
        assertEquals(1.0F, state.moveStrafe, 0f);
        assertTrue(state.jump);
        assertFalse(state.sneak);
        state.update(row(InputRow.Key.S, InputRow.Key.D));
        assertEquals(-1.0F, state.moveForward, 0f);
        assertEquals(-1.0F, state.moveStrafe, 0f);
        assertFalse(state.jump);
    }

    @Test
    public void opposingKeysCancel() {
        PlaybackMoveState state = new PlaybackMoveState();
        state.update(row(InputRow.Key.W, InputRow.Key.S, InputRow.Key.A, InputRow.Key.D));
        assertEquals(0.0F, state.moveForward, 0f);
        assertEquals(0.0F, state.moveStrafe, 0f);
    }

    @Test
    public void sneakScalesTheAxes() {
        PlaybackMoveState state = new PlaybackMoveState();
        state.update(row(InputRow.Key.W, InputRow.Key.D, InputRow.Key.SNEAK));
        assertEquals(0.3F, state.moveForward, 0f);
        assertEquals(-0.3F, state.moveStrafe, 0f);
        assertTrue(state.sneak);
    }

    @Test
    public void aMissingRowClearsEverything() {
        PlaybackMoveState state = new PlaybackMoveState();
        state.update(row(InputRow.Key.W, InputRow.Key.A, InputRow.Key.JUMP, InputRow.Key.SNEAK));
        state.update(null);
        assertEquals(0.0F, state.moveForward, 0f);
        assertEquals(0.0F, state.moveStrafe, 0f);
        assertFalse(state.jump);
        assertFalse(state.sneak);
    }

    @Test
    public void sprintAndMouseKeysDoNotMoveTheAxes() {
        PlaybackMoveState state = new PlaybackMoveState();
        state.update(row(InputRow.Key.SPRINT, InputRow.Key.LEFT_CLICK, InputRow.Key.RIGHT_CLICK));
        assertEquals(0.0F, state.moveForward, 0f);
        assertEquals(0.0F, state.moveStrafe, 0f);
        assertFalse(state.jump);
        assertFalse(state.sneak);
    }
}
