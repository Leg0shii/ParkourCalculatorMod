package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InputRowForce45Test {

    @Test
    public void force45ReleasesBackward() {
        InputRow row = new InputRow();
        row.setKeyActive(InputRow.Key.S, true);
        row.setKeyActive(InputRow.Key.D, true);

        row.applyForce45(true, 1);

        assertTrue(row.isKeyActive(InputRow.Key.W));
        assertTrue(row.isKeyActive(InputRow.Key.SPRINT));
        assertTrue(row.isKeyActive(InputRow.Key.A));
        assertFalse(row.isKeyActive(InputRow.Key.D));
        assertFalse(row.isKeyActive(InputRow.Key.S));
    }

    @Test
    public void force45JumpTickHoldsForwardOnly() {
        InputRow row = new InputRow();
        row.setKeyActive(InputRow.Key.S, true);
        row.setKeyActive(InputRow.Key.A, true);

        row.applyForce45(false, 1);

        assertTrue(row.isKeyActive(InputRow.Key.W));
        assertTrue(row.isKeyActive(InputRow.Key.SPRINT));
        assertFalse(row.isKeyActive(InputRow.Key.A));
        assertFalse(row.isKeyActive(InputRow.Key.D));
        assertFalse(row.isKeyActive(InputRow.Key.S));
    }
}
