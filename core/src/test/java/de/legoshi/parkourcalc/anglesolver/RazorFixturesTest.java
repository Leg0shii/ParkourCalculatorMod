package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.RazorFixtures;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RazorFixturesTest {

    @Test
    public void proofReplayPrecheck() {
        RazorFixtures.Loaded proof = RazorFixtures.loadProofSpec();
        assertEquals(49, proof.n);
        assertEquals(49, proof.objTick);
        RazorFixtures.Precheck r = RazorFixtures.proofPrecheck(proof);
        assertTrue("proof viol feasible, got " + r.viol, r.viol <= 0.0);
        assertEquals(212.7001641, r.objX, 1e-6);
        assertTrue("proof posDiff, got " + r.posDiff, r.posDiff < 1e-12);
    }

    @Test
    public void weirdpaneReplayPrecheck() {
        RazorFixtures.Loaded wp = RazorFixtures.loadWeirdpaneSpec();
        assertEquals(50, wp.n);
        assertEquals(50, wp.objTick);
        RazorFixtures.Precheck r = RazorFixtures.weirdpanePrecheck(wp);
        assertEquals(-8.864771846396799, r.objX, 1e-6);
        assertEquals(2.271846e-3, r.viol, 1e-6);
        assertTrue("weirdpane posDiff, got " + r.posDiff, r.posDiff < 1e-12);
    }

    @Test
    public void rung5375PatchRaisesThreeLeadWallsAndBindsAtT12() {
        RazorFixtures.Loaded proof = RazorFixtures.loadProofSpec();
        RazorFixtures.RungPatch patch = RazorFixtures.applyRung5375Patch(proof.spec);

        assertEquals(3, patch.raised.size());
        Set<Integer> ticks = new HashSet<Integer>();
        for (RazorFixtures.RaisedWall w : patch.raised) {
            ticks.add(w.tick);
            assertEquals(-1.487500011921, w.oldRhs, 1e-12);
            assertEquals(-1.425000011921, w.newRhs, 1e-12);
        }
        Set<Integer> expected = new HashSet<Integer>();
        expected.add(12);
        expected.add(24);
        expected.add(37);
        assertEquals(expected, ticks);

        double[] gf = RazorFixtures.warmGameFacings(proof);
        ForwardPath p = RazorFixtures.warmPath(proof);
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(patch.spec);

        double viol = compiled.maxViolation(gf, p);
        assertEquals(6.248650866e-2, viol, 1e-9);

        double maxSlack = -1.0;
        int bindTick = Integer.MIN_VALUE;
        for (JumpConstraint jc : patch.spec.constraints) {
            double s = JumpConstraintCompiler.slack(jc, gf, p);
            if (s > maxSlack) {
                maxSlack = s;
                bindTick = jc.t1;
            }
        }
        assertEquals(12, bindTick);
        assertEquals(viol, maxSlack, 1e-12);

        boolean sawTail = false;
        for (JumpConstraint jc : patch.spec.constraints) {
            boolean tail = jc.t1 == 48 || jc.t1 == 49
                    || (jc.t2 != null && (jc.t2 == 48 || jc.t2 == 49));
            if (tail) {
                sawTail = true;
                double s = JumpConstraintCompiler.slack(jc, gf, p);
                assertTrue("tail constraint " + jc.name + " t" + jc.t1 + " slack " + s, s <= 0.0);
            }
        }
        assertTrue("expected at least one tail constraint at ticks 48/49", sawTail);
    }
}
