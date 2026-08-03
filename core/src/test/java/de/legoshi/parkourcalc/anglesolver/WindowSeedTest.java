package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WindowSeedTest {

    private static JumpSpec fullSpec() {
        JumpPhysicsInputs sc = new JumpPhysicsInputs(3);
        sc.startPos = new Vec3dCore(0.5, 64.0, 0.5);
        sc.sprintPerTick = new boolean[]{false, false, false};
        sc.speedAmplifier = new int[]{0, 0, 0};
        sc.incomingSprint = Boolean.TRUE;
        sc.incomingAmp = 2;
        return new JumpSpec(sc, Collections.emptyList(),
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, 3));
    }

    @Test
    public void aSuffixAtTickZeroKeepsTheScenarioSeed() {
        JumpPhysicsInputs win = LongRunSolver.suffixSpec(fullSpec(), 0,
                new Vec3dCore(0.5, 64.0, 0.5), Vec3dCore.ZERO, 0.0F).asScenario();

        assertEquals(Boolean.TRUE, win.incomingSprint);
        assertEquals(Integer.valueOf(2), win.incomingAmp);
        assertTrue("the seed drives tick 0's movement factor", win.factorSprintAt(0));
        assertEquals(2, win.factorAmpAt(0));
    }

    @Test
    public void aSuffixPastTickZeroStillReadsThePrecedingTick() {
        JumpSpec full = fullSpec();
        full.asScenario().sprintPerTick = new boolean[]{true, true, false};
        full.asScenario().speedAmplifier = new int[]{1, 1, 0};

        JumpPhysicsInputs win = LongRunSolver.suffixSpec(full, 1,
                new Vec3dCore(0.5, 64.0, 0.5), Vec3dCore.ZERO, 0.0F).asScenario();

        assertEquals(Boolean.TRUE, win.incomingSprint);
        assertEquals(Integer.valueOf(1), win.incomingAmp);
    }
}
