package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.FlowVector;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClosedFormBoundScopeTest {

    private static final String PLAIN_CAPTURE = "j016-X2jmmp2p";
    private static final String[] DAMAGE_CAPTURES = {"j344-fall-damage-26.3-test1", "synth-damage-reset"};
    private static final int FLOW_TICK = 3;

    private static JumpSpec spec(String capture) {
        ProblemFixture.Rig rig = ProblemFixture.load("solve", capture).rig();
        rig.state.setEffort(AngleSolverState.Effort.THOROUGH);
        return rig.spec();
    }

    private static JumpSpec withFlow(JumpSpec spec, boolean atTickEnd) {
        JumpPhysicsInputs sc = spec.asScenario().copy();
        FlowVector[] flow = new FlowVector[sc.numTicks];
        flow[FLOW_TICK] = new FlowVector(1.0, 0.0);
        if (atTickEnd) sc.endFlowPerTick = flow;
        else sc.flowPerTick = flow;
        return new JumpSpec(sc, spec.constraints, spec.objective);
    }

    @Test
    public void aPlainJumpHasABound() {
        JumpSpec spec = spec(PLAIN_CAPTURE);
        assertFalse(Double.isNaN(ClosedFormSolve.dualBound(spec)));
        assertFalse(Double.isNaN(ClosedFormSolve.dualBoundIgnoringFacing(spec)));
    }

    @Test
    public void aJumpWithADamageTickHasNoBound() {
        for (String capture : DAMAGE_CAPTURES) {
            JumpSpec spec = spec(capture);
            assertTrue(capture + " must carry a damage tick", spec.asScenario().lastDamageTick() >= 0);
            assertTrue(capture, Double.isNaN(ClosedFormSolve.dualBound(spec)));
            assertTrue(capture, Double.isNaN(ClosedFormSolve.dualBoundIgnoringFacing(spec)));
        }
    }

    @Test
    public void aJumpWithAFlowPushHasNoBound() {
        JumpSpec plain = spec(PLAIN_CAPTURE);
        assertTrue(Double.isNaN(ClosedFormSolve.dualBound(withFlow(plain, false))));
        assertTrue(Double.isNaN(ClosedFormSolve.dualBound(withFlow(plain, true))));
        assertTrue(Double.isNaN(ClosedFormSolve.dualBoundIgnoringFacing(withFlow(plain, false))));
    }
}
