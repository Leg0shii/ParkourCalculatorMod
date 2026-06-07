package de.legoshi.parkourcalc.anglesolver.derive;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Validates the harness itself (both sides of the oracle) before any DERIVE runs:
 *  the known-good recorded solution is swept-clean + landed (no false positive), and nr3's recorded
 *  failing yaws are flagged as colliding (no false negative). This is what lets a VALID verdict on a
 *  derived solution stand in for an in-game run. */
public class DeriveOracleTest {

    @Test
    public void recordedGoodSolutionIsValid() {
        DeriveFixtures.Loaded f = DeriveFixtures.load("j154-fails-nr3.json");
        // The known-good in-game facings (from j154.json) over the SAME segment/geometry/seed.
        double[] good = DeriveFixtures.recordedFacings("j154.json", f.startTick, f.landingTick - f.startTick);
        Validation v = f.problem.oracle.validateGameFacings(good);
        System.out.println("[oracle] recorded GOOD -> " + v.describe());
        assertTrue("known-good recorded solution must be swept-clean (no false positive)", v.clean);
        assertTrue("known-good recorded solution must land", v.landed);
    }

    @Test
    public void recordedFailingSolutionCollides() {
        DeriveFixtures.Loaded f = DeriveFixtures.load("j154-fails-nr3.json");
        // nr3's own recorded yaws: in-game this clipped, so the swept model must flag a collision.
        Validation v = f.problem.oracle.validateGameFacings(f.recordedGameFacings);
        System.out.println("[oracle] recorded nr3 FAIL -> " + v.describe());
        assertFalse("nr3 recorded solution must be flagged as colliding (no false negative)", v.clean);
    }
}
