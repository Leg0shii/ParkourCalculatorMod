package de.legoshi.parkourcalc.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.BucketAscentPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SmoothingPolish;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveProgress;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SmoothLambdaScoringTest {

    @Test
    public void travelDegNormalizesWrapSeams() {
        assertEquals(3.5, Angles.travelDeg(new double[] {0.0, 1.0, 2.0, 3.5}), 0.0);
        assertEquals(7.0, Angles.travelDeg(new double[] {10.0, 12.0, 12.0, 9.0, 11.0}), 0.0);
        assertEquals(4.0, Angles.travelDeg(new double[] {179.0, -179.0, -177.0}), 1.0e-12);
        assertEquals(0.0, Angles.travelDeg(new double[] {45.0}), 0.0);
        assertEquals(Angles.travelDeg(new double[] {350.0, 370.0}),
                Angles.travelDeg(new double[] {-10.0, 10.0}), 1.0e-12);
    }

    @Test
    public void scoredAppliesLambdaBySense() {
        double[] yaws = {0.0, 10.0, 0.0};
        Objective maxObj = new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, 3, 0.01);
        assertEquals(5.0 - 0.3, maxObj.scored(5.0, 0.0, yaws), 1.0e-12);
        Objective minObj = new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MIN, 3, 0.01);
        assertEquals(5.0 + 0.3, minObj.scored(5.0, 0.0, yaws), 1.0e-12);
        Objective zero = new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, 3);
        assertEquals(0.0, zero.smoothLambda, 0.0);
        assertEquals(5.0, zero.scored(5.0, 0.0, yaws), 0.0);
        assertEquals(0.0, zero.smoothPenalty(0.0, yaws), 0.0);
    }

    @Test
    public void anchoredJerkCostsTheSeamTurn() {
        double[] flat = {10.0, 10.0, 10.0};
        assertEquals(0.0, Angles.wiggleDeg(10.0, flat), 0.0);
        assertEquals(30.0, Angles.wiggleDeg(40.0, flat), 1.0e-12);
        double[] flick = {40.0, 40.0, 40.0};
        double[] ramp = {20.0, 30.0, 40.0};
        assertTrue(Angles.wiggleDeg(10.0, flick) > Angles.wiggleDeg(10.0, ramp));
        assertEquals(Angles.wiggleDeg(new double[] {10.0, 20.0, 5.0, 25.0}) + 10.0,
                Angles.wiggleDeg(10.0, new double[] {10.0, 20.0, 5.0, 25.0}), 1.0e-12);
    }

    @Test
    public void microWigglesCostMoreThanOneCleanSweep() {
        double[] jitter = {0.0, 1.0, 0.0, 1.0, 0.0};
        double[] sweep = {0.0, 6.0, 12.0, 18.0};
        assertTrue(Angles.travelDeg(jitter) < Angles.travelDeg(sweep));
        assertTrue(Angles.wiggleDeg(jitter) > Angles.wiggleDeg(sweep));
    }

    @Test
    public void staircaseCostsMoreThanConstantRateSweep() {
        double[] staircase = {0.0, 0.0, 0.0, 18.0, 18.0, 18.0};
        double[] ramp = {0.0, 3.6, 7.2, 10.8, 14.4, 18.0};
        assertTrue(Angles.wiggleDeg(staircase) > Angles.wiggleDeg(ramp));
        assertEquals(0.0, Angles.wiggleDeg(ramp), 1.0e-9);
        assertEquals(0.0, Angles.wiggleDeg(new double[] {0.0, 6.0, 12.0, 18.0}), 1.0e-9);
    }

    @Test
    public void subFloorJitterCountsNoReversals() {
        double[] noise = {0.0, 0.005, 0.0, 0.005, 0.0};
        assertEquals(0, Angles.reversals(noise, Angles.REVERSAL_FLOOR_DEG));
        assertEquals(3, Angles.reversals(new double[] {0.0, 1.0, 0.0, 1.0, 0.0}, Angles.REVERSAL_FLOOR_DEG));
        assertEquals(1, Angles.reversals(new double[] {179.0, -179.0, 178.0}, Angles.REVERSAL_FLOOR_DEG));
        assertEquals(0, Angles.reversals(new double[] {0.0, 10.0, 10.0, 20.0}, Angles.REVERSAL_FLOOR_DEG));
    }

    @Test
    public void progressPrefersSmootherWithinFeasibility() {
        double[] wiggly = {0.0, 40.0, 0.0, 40.0, 0.0};
        double[] smooth = {0.0, 0.0, 0.0, 0.0, 0.0};

        SolveProgress p = new SolveProgress(true, false, 0.01);
        p.report(wiggly, 5.0, 0.0, true);
        p.report(smooth, 4.0, 0.0, true);
        assertEquals(4.0, p.bestObjective(), 0.0);
        p.report(smooth, 6.0, 1.0, false);
        assertTrue(p.isBestFeasible());
        assertEquals(4.0, p.bestObjective(), 0.0);

        SolveProgress plain = new SolveProgress(true, false);
        plain.report(wiggly, 5.0, 0.0, true);
        plain.report(smooth, 4.0, 0.0, true);
        assertEquals(5.0, plain.bestObjective(), 0.0);
    }

    @Test
    public void metricRecordsLambda() {
        Objective objective = new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, 12, 2.5e-4);
        SolveRunRecord.Config c = SolveRunRecord.configOf(BuiltinGraphs.fast(), null, "FAST", 0.0, objective);
        assertEquals(2.5e-4, c.metric.smoothLambda, 0.0);
        assertEquals(0.0, SolveRunRecord.configOf(BuiltinGraphs.fast(), null, "FAST", 0.0,
                new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MIN, 12)).metric.smoothLambda, 0.0);
    }

    @Test
    public void smoothingSpendsScoredMarginWithLambda() {
        JumpPhysicsInputs phys = TestScenarios.phys(8, null);
        double[] wiggly = {0.0, 8.0, -8.0, 8.0, -8.0, 8.0, -8.0, 0.0};
        JumpSpec spec = new JumpSpec(phys, Collections.<JumpConstraint>emptyList(),
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MIN, 8, 1.0));
        double[] out = SmoothingPolish.smooth(ExactJumpModel.forMcVersion("1.8.9"), spec, wiggly.clone(),
                new AtomicBoolean(false));
        assertTrue(Angles.wiggleDeg(out) < Angles.wiggleDeg(wiggly));
        assertTrue(Angles.reversals(out, Angles.REVERSAL_FLOOR_DEG)
                < Angles.reversals(wiggly, Angles.REVERSAL_FLOOR_DEG));
    }

    @Test
    public void bucketAscentWithDominantLambdaNeverAddsTravel() {
        JumpPhysicsInputs phys = TestScenarios.phys(8, null);
        JumpSpec spec = new JumpSpec(phys, Collections.<JumpConstraint>emptyList(),
                new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, 8, 1.0e6));
        double[] start = {0.0, 30.0, -30.0, 30.0, -30.0, 30.0, -30.0, 0.0};
        double[] out = BucketAscentPolish.polish(ExactJumpModel.forMcVersion("1.8.9"), spec, start.clone(),
                BucketAscentPolish.FAST, new AtomicBoolean(false));
        assertTrue(Angles.wiggleDeg(out) <= Angles.wiggleDeg(start) + 1.0e-9);
    }
}
