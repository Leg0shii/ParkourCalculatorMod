package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.TurnShape;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TurnShapeTest {

    @Test
    public void shakyRampHasFlipsAndTheSteadyRampHasNone() {
        TurnShape shaky = TurnShape.ofDeltas(new double[] {27, 16, 24, 24, 26}, 0.0);
        TurnShape steady = TurnShape.ofDeltas(new double[] {14, 16, 18, 20, 22}, 0.0);
        TurnShape bell = TurnShape.ofDeltas(new double[] {5, 10, 15, 10, 5}, 0.0);
        assertEquals(0, shaky.reversals);
        assertEquals(1, shaky.accelFlips);
        assertEquals(0, steady.accelFlips);
        assertEquals(0.0, steady.snapDeg, 1.0e-12);
        assertEquals(1, bell.accelFlips);
        assertTrue(steady.betterThan(shaky));
        assertTrue(bell.betterThan(shaky));
        assertFalse(shaky.betterThan(steady));
    }

    @Test
    public void reversalsOutrankFlipsAndTheCapOutranksEverything() {
        TurnShape reversing = TurnShape.ofDeltas(new double[] {10, -5, 10, 12, 14}, 0.0);
        TurnShape shaky = TurnShape.ofDeltas(new double[] {30, 2, 6, 6, 3}, 0.0);
        assertEquals(2, reversing.reversals);
        assertEquals(2, shaky.accelFlips);
        assertTrue(shaky.betterThan(reversing));
        TurnShape capped = TurnShape.ofDeltas(new double[] {30, 2, 6, 6, 3}, 25.0);
        TurnShape under = TurnShape.ofDeltas(new double[] {10, -5, 10, 12, 14}, 25.0);
        assertEquals(1, capped.capTicks);
        assertEquals(5.0, capped.capExcessDeg, 1.0e-12);
        assertTrue(under.betterThan(capped));
        TurnShape lessExcess = TurnShape.ofDeltas(new double[] {29.9, -2, 6, 6, 3}, 25.0);
        assertFalse("a hair less excess never buys a reversal", lessExcess.betterThan(capped));
    }

    @Test
    public void tinyWobblesBelowTheFloorsAreIgnored() {
        TurnShape s = TurnShape.ofDeltas(new double[] {10.00, 10.02, 9.99, 10.01, 0.005, -0.004}, 0.0);
        assertEquals(0, s.reversals);
        assertEquals(0, s.accelFlips);
    }
}
