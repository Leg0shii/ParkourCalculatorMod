package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RangeMetTest {

    private static final double LO = -35.699999988;
    private static final double HI = -35.699999888;

    @Test
    public void nearMissBelowANarrowRangeIsNotMet() {
        Constraint range = Constraint.range(Constraint.Field.Z, LO, HI, true, true);
        assertFalse(AngleSolverEngine.satisfied(range, -35.700000869));
        assertFalse(AngleSolverEngine.satisfied(range, Math.nextDown(LO)));
        assertFalse(AngleSolverEngine.satisfied(range, Math.nextUp(HI)));
    }

    @Test
    public void rangeEndsAreGatedLikeTheWalls() {
        Constraint range = Constraint.range(Constraint.Field.Z, LO, HI, true, true);
        Constraint ge = Constraint.scalar(Constraint.Field.Z, Constraint.Op.GE, LO);
        Constraint le = Constraint.scalar(Constraint.Field.Z, Constraint.Op.LE, HI);
        double[] probes = {LO - 1.0e-4, LO - 1.0e-7, Math.nextDown(LO), LO, (LO + HI) / 2.0,
                HI, Math.nextUp(HI), HI + 1.0e-7, HI + 1.0e-4};
        for (double v : probes) {
            boolean walls = AngleSolverEngine.satisfied(ge, v) && AngleSolverEngine.satisfied(le, v);
            assertEquals("Z " + v, walls, AngleSolverEngine.satisfied(range, v));
        }
    }

    @Test
    public void exclusiveEndsRejectTheBoundItself() {
        Constraint range = Constraint.range(Constraint.Field.X, 1.0, 2.0, false, false);
        assertFalse(AngleSolverEngine.satisfied(range, 1.0));
        assertFalse(AngleSolverEngine.satisfied(range, 2.0));
        assertTrue(AngleSolverEngine.satisfied(range, 1.5));
    }
}
