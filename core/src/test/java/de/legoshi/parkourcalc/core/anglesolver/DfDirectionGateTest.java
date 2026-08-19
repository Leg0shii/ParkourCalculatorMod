package de.legoshi.parkourcalc.core.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LevelSetAscent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The dF gate: {@link LevelSetAscent} is skipped (returns null) exactly when a delta-facing constraint is
 *  present, which is the condition {@link AngleSolverEngine#DF_DIRECTION_NOTICE} is shown for. Position
 *  keep-out walls (the common case) are NOT facing walls, so the ladder runs on them. */
public class DfDirectionGateTest {

    @Test
    public void positionWallSpecHasNoFacingWallSoLadderApplies() {
        JumpSpec spec = ProblemFixture.load("solve", "j024-bfly-goal-direction")
                .specFor(AngleSolverState.Axis.X, AngleSolverState.Goal.MAX);
        assertFalse("bfly uses position keep-out walls, not facing walls",
                JumpLinearModel.hasFacingWall(spec.constraints));
    }

    @Test
    public void deltaFacingConstraintSkipsTheLadder() {
        ProblemFixture pf = ProblemFixture.load("solve", "j024-bfly-goal-direction");
        JumpSpec spec = pf.specFor(AngleSolverState.Axis.X, AngleSolverState.Goal.MAX);
        List<JumpConstraint> withDf = new ArrayList<>(spec.constraints);
        withDf.add(new JumpConstraint(JumpConstraint.Mode.F, 1, 0, JumpConstraint.Op.MINUS,
                JumpConstraint.Cmp.GE, 0.0, "df"));
        JumpSpec dfSpec = new JumpSpec(spec.asScenario(), withDf, spec.objective);
        assertTrue(JumpLinearModel.hasFacingWall(dfSpec.constraints));

        double[] witness = new double[spec.asScenario().numTicks];
        assertNull("dF constraint => no dual bound => ladder must not run",
                LevelSetAscent.improve(pf.model, dfSpec, witness, 0.0, new AtomicBoolean(false)));
    }

    @Test
    public void noticeConstantNamesDeltaFacing() {
        assertNotNull(AngleSolverEngine.DF_DIRECTION_NOTICE);
        assertTrue(AngleSolverEngine.DF_DIRECTION_NOTICE.contains("dF"));
    }
}
