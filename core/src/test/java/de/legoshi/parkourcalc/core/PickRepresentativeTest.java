package de.legoshi.parkourcalc.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PickRepresentativeTest {

    @Test
    public void measuredMemberBeatsEasierUnmeasuredOne() {
        boolean[] measured = {false, true, true};
        double[] difficulty = {-9.0, 1.5, 2.0};
        assertEquals(1, ColdStratController.pickRepresentative(measured, difficulty));
    }

    @Test
    public void lowestDifficultyWinsAmongMeasured() {
        boolean[] measured = {true, true, true};
        double[] difficulty = {2.0, -1.0, 0.5};
        assertEquals(1, ColdStratController.pickRepresentative(measured, difficulty));
    }

    @Test
    public void nanDifficultyLosesToAFiniteOne() {
        boolean[] measured = {true, true};
        double[] difficulty = {Double.NaN, 3.0};
        assertEquals(1, ColdStratController.pickRepresentative(measured, difficulty));
    }

    @Test
    public void allUnmeasuredFallsBackToTheEasiest() {
        boolean[] measured = {false, false, false};
        double[] difficulty = {2.0, 0.5, Double.NaN};
        assertEquals(1, ColdStratController.pickRepresentative(measured, difficulty));
    }
}
