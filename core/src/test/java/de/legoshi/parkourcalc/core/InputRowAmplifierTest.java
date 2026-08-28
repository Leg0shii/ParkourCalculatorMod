package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.PotionDose;
import de.legoshi.parkourcalc.core.ui.InputRow;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class InputRowAmplifierTest {

    @Test
    public void amplifiersAcceptUpTo255() {
        assertEquals(255, InputRow.MAX_AMPLIFIER);

        InputRow row = new InputRow();
        row.setSpeedAmplifier(255);
        assertEquals(255, row.getSpeedAmplifier());
        row.setJumpBoostAmplifier(255);
        assertEquals(255, row.getJumpBoostAmplifier());
    }

    @Test
    public void amplifiersClampToRange() {
        InputRow row = new InputRow();

        row.setSpeedAmplifier(256);
        assertEquals(255, row.getSpeedAmplifier());
        row.setSpeedAmplifier(-1);
        assertEquals(0, row.getSpeedAmplifier());

        row.setJumpBoostAmplifier(1000);
        assertEquals(255, row.getJumpBoostAmplifier());
        row.setJumpBoostAmplifier(-5);
        assertEquals(0, row.getJumpBoostAmplifier());
    }

    @Test
    public void potionDoseCapIs255() {
        assertEquals(1, PotionDose.MIN_LEVEL);
        assertEquals(255, PotionDose.MAX_LEVEL);
    }
}
