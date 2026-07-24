package de.legoshi.parkourcalc.anglesolver.harness;

import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class CaptureMutationsTest {

    private static SaveFile load(String pool) {
        return SaveIO.parseSafe(Fixtures.rawPool(pool));
    }

    @Test
    public void copyIsDeep() {
        SaveFile a = load("j017-postwalllightningneo");
        SaveFile b = CaptureMutations.copy(a);
        assertNotSame(a, b);
        assertNotSame(a.angleSolver, b.angleSolver);
        b.angleSolver.seed.vel[0] += 1.0;
        assertEquals(a.angleSolver.seed.vel[0] + 1.0, b.angleSolver.seed.vel[0], 0.0);
    }

    @Test
    public void goalShiftMovesLandingConstraintsHarder() {
        SaveFile file = load("nix-full-t1");
        SaveFile.AngleSolver a = file.angleSolver;
        boolean max = a.goal == null || "MAX".equalsIgnoreCase(a.goal);
        String axisField = "Z".equalsIgnoreCase(a.axis) ? "Z" : "X";
        SaveFile.Constraint before = landingAxisConstraint(a, axisField);
        assertTrue("nix-full-t1 should be pad-style on the objective axis", before != null);
        double ref = before.range ? before.lo : before.value;

        assertTrue(CaptureMutations.apply(file, new CaptureMutations.Mutation(2, 0, 1.0)));
        SaveFile.Constraint after = landingAxisConstraint(file.angleSolver, axisField);
        double expected = ref + (max ? 1 : -1) * 2.0 / 16.0;
        assertEquals(expected, after.range ? after.lo : after.value, 1.0e-12);
    }

    private static SaveFile.Constraint landingAxisConstraint(SaveFile.AngleSolver a, String axisField) {
        for (SaveFile.Tick t : a.ticks) {
            if (t.tick != a.landingTick) continue;
            for (SaveFile.Constraint c : t.constraints) {
                if (axisField.equalsIgnoreCase(c.field)) return c;
            }
        }
        return null;
    }

    @Test
    public void tightenShrinksEveryCorridor() {
        SaveFile file = load("j017-postwalllightningneo");
        double leBefore = Double.NaN;
        double geBefore = Double.NaN;
        for (SaveFile.Tick t : file.angleSolver.ticks) {
            for (SaveFile.Constraint c : t.constraints) {
                if (!"Z".equalsIgnoreCase(c.field) || c.range) continue;
                if ("LE".equalsIgnoreCase(c.op) && Double.isNaN(leBefore)) leBefore = c.value;
                if ("GE".equalsIgnoreCase(c.op) && Double.isNaN(geBefore)) geBefore = c.value;
            }
        }
        assertFalse(Double.isNaN(leBefore));
        assertFalse(Double.isNaN(geBefore));

        assertTrue(CaptureMutations.apply(file, new CaptureMutations.Mutation(0, 2, 1.0)));
        double leAfter = Double.NaN;
        double geAfter = Double.NaN;
        for (SaveFile.Tick t : file.angleSolver.ticks) {
            for (SaveFile.Constraint c : t.constraints) {
                if (!"Z".equalsIgnoreCase(c.field) || c.range) continue;
                if ("LE".equalsIgnoreCase(c.op) && Double.isNaN(leAfter)) leAfter = c.value;
                if ("GE".equalsIgnoreCase(c.op) && Double.isNaN(geAfter)) geAfter = c.value;
            }
        }
        assertEquals(leBefore - 2.0 / 16.0, leAfter, 1.0e-12);
        assertEquals(geBefore + 2.0 / 16.0, geAfter, 1.0e-12);
    }

    @Test
    public void momentumScaleAndNoOps() {
        SaveFile file = load("j017-postwalllightningneo");
        double vx = file.angleSolver.seed.vel[0];
        double vz = file.angleSolver.seed.vel[2];
        boolean hasMomentum = vx != 0.0 || vz != 0.0;
        SaveFile scaled = CaptureMutations.copy(file);
        boolean applied = CaptureMutations.apply(scaled, new CaptureMutations.Mutation(0, 0, 0.5));
        assertEquals(hasMomentum, applied);
        if (applied) {
            assertEquals(vx * 0.5, scaled.angleSolver.seed.vel[0], 0.0);
            assertEquals(vz * 0.5, scaled.angleSolver.seed.vel[2], 0.0);
        }

        SaveFile noGoal = CaptureMutations.copy(file);
        noGoal.angleSolver.ticks.clear();
        assertFalse(CaptureMutations.apply(noGoal, new CaptureMutations.Mutation(2, 0, 1.0)));

        SaveFile identity = CaptureMutations.copy(file);
        assertFalse(CaptureMutations.apply(identity, new CaptureMutations.Mutation(0, 0, 1.0)));
    }
}
