package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.FreeStartSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FreeStartSolveTest {

    @Test
    public void pinsStartAgainstBindingConstraintWithinFootprint() {
        runFreeStart(0.3, 0.6, 0.25);
    }

    @Test
    public void pinsStartAtFootprintCorner() {
        runFreeStart(0.5, 0.55, 0.45);
    }

    @Test
    public void jointSolveImprovesObjectiveOverSeed() {
        ProblemFixture pf = ProblemFixture.load("dualrecovery", "j320_1bm_Gapped_Head_Butterfly_Neo");
        ExactJumpModel exact = pf.model;
        JumpSpec base = pf.specFor(null, null);
        JumpPhysicsInputs sc = base.asScenario();
        Objective obj = base.objective;
        AtomicBoolean cancel = new AtomicBoolean(false);

        boolean axisX = obj.axis == JumpPhysicsInputs.Axis.X;
        boolean max = obj.sense == Objective.Sense.MAX;
        double sign = max ? 1.0 : -1.0;

        JumpSpec reachSpec = new JumpSpec(sc, Collections.<JumpConstraint>emptyList(), obj);
        double[] seedYaws = ClosedFormSolve.optimize(exact, reachSpec, 0.0, cancel);
        assertNotNull("seed reach solve must certify", seedYaws);
        double seedObj = objectivePos(exact, sc, obj, seedYaws);

        double seedAxis = axisX ? sc.startPos.x : sc.startPos.z;
        double a0 = seedAxis;
        double a1 = seedAxis + sign * 0.6;
        double lo = Math.min(a0, a1);
        double hi = Math.max(a0, a1);
        double vx = sc.initialVelocity.x, vz = sc.initialVelocity.z;
        StartBox freeBox = axisX
                ? new StartBox(sc.startPos.x, sc.startPos.z, vx, vz, lo, hi, sc.startPos.z, sc.startPos.z, vx, vx, vz, vz)
                : new StartBox(sc.startPos.x, sc.startPos.z, vx, vz, sc.startPos.x, sc.startPos.x, lo, hi, vx, vx, vz, vz);
        JumpPhysicsInputs freePhys = withStart(sc, sc.startPos.x, sc.startPos.z, freeBox);
        JumpSpec freeSpec = new JumpSpec(freePhys, Collections.<JumpConstraint>emptyList(), obj);

        FreeStartSolve.Result res = FreeStartSolve.solveJoint(exact, freeSpec, 0.0, cancel);
        assertNotNull("joint solve found nothing", res);
        assertTrue("joint result not feasible", res.feasible);

        JumpPhysicsInputs atRes = withStart(freePhys, res.startX, res.startZ,
                StartBox.pinned(res.startX, res.startZ, vx, vz));
        double jointObj = objectivePos(exact, atRes, obj, res.yaws);
        double gain = max ? jointObj - seedObj : seedObj - jointObj;
        System.out.printf("JOINT seedObj=%.4f jointObj=%.4f gain=%.4f start=%.4f->%.4f%n",
                seedObj, jointObj, gain, seedAxis, axisX ? res.startX : res.startZ);
        assertTrue("joint free-start must strictly improve the objective (gain=" + gain + ")", gain > 0.5);
    }

    @Test
    public void jointSolveSolvesConvexFreeBoxFromFarCornerReference() {
        ProblemFixture pf = ProblemFixture.load("dualrecovery", "j320_1bm_Gapped_Head_Butterfly_Neo");
        ExactJumpModel exact = pf.model;
        JumpSpec base = pf.specFor(null, null);
        JumpPhysicsInputs sc = base.asScenario();
        AtomicBoolean cancel = new AtomicBoolean(false);
        int n = sc.numTicks;
        double seedX = sc.startPos.x, seedZ = sc.startPos.z;
        double vx = sc.initialVelocity.x, vz = sc.initialVelocity.z;
        Objective objX = new Objective(JumpPhysicsInputs.Axis.X, Objective.Sense.MAX, n);

        double[] curveShape = ClosedFormSolve.optimize(exact,
                new JumpSpec(sc, Collections.<JumpConstraint>emptyList(),
                        new Objective(JumpPhysicsInputs.Axis.Z, Objective.Sense.MAX, n)), 0.0, cancel);
        assertNotNull("max-Z reach solve must certify", curveShape);

        double tgtX = seedX - 0.3, tgtZ = seedZ - 0.3, slack = 0.03;
        JumpPhysicsInputs tgt = withStart(sc, tgtX, tgtZ, StartBox.pinned(tgtX, tgtZ, vx, vz));
        double[] tgtGf = tgt.toGameFacings(Angles.wrapAll(curveShape));
        ForwardPath tgtPath = exact.forward(tgt, tgtGf);
        double landX = tgtPath.getPos(n, JumpPhysicsInputs.Axis.X);
        double landZ = tgtPath.getPos(n, JumpPhysicsInputs.Axis.Z);

        List<JumpConstraint> cons = new ArrayList<JumpConstraint>();
        cons.add(pad(JumpConstraint.Mode.X, n, JumpConstraint.Cmp.GE, landX - slack));
        cons.add(pad(JumpConstraint.Mode.X, n, JumpConstraint.Cmp.LE, landX + slack));
        cons.add(pad(JumpConstraint.Mode.Z, n, JumpConstraint.Cmp.GE, landZ - slack));
        cons.add(pad(JumpConstraint.Mode.Z, n, JumpConstraint.Cmp.LE, landZ + slack));

        double pxLo = seedX - 1.0, pxHi = seedX + 0.2, pzLo = seedZ - 0.8, pzHi = seedZ + 0.8;
        double refX = pxLo, refZ = pzHi;
        StartBox freeBox = new StartBox(refX, refZ, vx, vz, pxLo, pxHi, pzLo, pzHi, vx, vx, vz, vz);
        JumpSpec freeSpec = new JumpSpec(withStart(sc, refX, refZ, freeBox), cons, objX);

        FreeStartSolve.Result res = FreeStartSolve.solveJoint(exact, freeSpec, 0.0, cancel);
        assertNotNull("joint solve found no feasible free start on a convex box that contains one", res);
        assertTrue("joint result not marked feasible", res.feasible);
        assertTrue("recovered start X outside footprint", res.startX >= pxLo - 1.0e-9 && res.startX <= pxHi + 1.0e-9);
        assertTrue("recovered start Z outside footprint", res.startZ >= pzLo - 1.0e-9 && res.startZ <= pzHi + 1.0e-9);

        double viol = FreeStartSolve.violationAt(exact, freeSpec, res.yaws, res.startX, res.startZ);
        assertTrue("free-start solution not byte-exact feasible (viol=" + viol + ")", viol <= 0.0);
    }

    private static JumpConstraint pad(JumpConstraint.Mode mode, int tick, JumpConstraint.Cmp cmp, double rhs) {
        return new JumpConstraint(mode, tick, null, JumpConstraint.Op.PLUS, cmp, rhs, "pad");
    }

    private void runFreeStart(double beyondReach, double footprint, double minMove) {
        ProblemFixture pf = ProblemFixture.load("dualrecovery", "j320_1bm_Gapped_Head_Butterfly_Neo");
        ExactJumpModel exact = pf.model;
        JumpSpec base = pf.specFor(null, null);
        JumpPhysicsInputs sc = base.asScenario();
        Objective obj = base.objective;
        AtomicBoolean cancel = new AtomicBoolean(false);

        boolean axisX = obj.axis == JumpPhysicsInputs.Axis.X;
        boolean max = obj.sense == Objective.Sense.MAX;
        double sign = max ? 1.0 : -1.0;

        JumpSpec reachSpec = new JumpSpec(sc, Collections.<JumpConstraint>emptyList(), obj);
        double[] reachYaws = ClosedFormSolve.optimize(exact, reachSpec, 0.0, cancel);
        assertNotNull("unconstrained reach solve must certify", reachYaws);
        double reach = objectivePos(exact, sc, obj, reachYaws);

        double seedAxis = axisX ? sc.startPos.x : sc.startPos.z;
        double wallRhs = reach + sign * beyondReach;
        JumpConstraint wall = new JumpConstraint(
                axisX ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z,
                obj.tick, null, JumpConstraint.Op.PLUS,
                max ? JumpConstraint.Cmp.GE : JumpConstraint.Cmp.LE, wallRhs, "freestart-reach");
        List<JumpConstraint> cons = new ArrayList<>();
        cons.add(wall);

        double a0 = seedAxis;
        double a1 = seedAxis + sign * footprint;
        double lo = Math.min(a0, a1);
        double hi = Math.max(a0, a1);
        double vx = sc.initialVelocity.x, vz = sc.initialVelocity.z;
        StartBox freeBox = axisX
                ? new StartBox(sc.startPos.x, sc.startPos.z, vx, vz, lo, hi, sc.startPos.z, sc.startPos.z, vx, vx, vz, vz)
                : new StartBox(sc.startPos.x, sc.startPos.z, vx, vz, sc.startPos.x, sc.startPos.x, lo, hi, vx, vx, vz, vz);

        JumpPhysicsInputs freePhys = withStart(sc, sc.startPos.x, sc.startPos.z, freeBox);
        JumpSpec freeSpec = new JumpSpec(freePhys, cons, obj);

        assertNull("jump must be infeasible at the pinned seed start",
                ClosedFormSolve.optimize(exact, freeSpec, 0.0, cancel));

        FreeStartSolve.Result res = FreeStartSolve.solve(exact, freeSpec, 0.0, cancel);
        assertNotNull("free-start solve found no feasible start", res);
        assertTrue("free-start result not marked feasible", res.feasible);

        double resAxis = axisX ? res.startX : res.startZ;
        assertTrue("pinned start " + resAxis + " outside footprint [" + lo + "," + hi + "]",
                resAxis >= lo - 1.0e-9 && resAxis <= hi + 1.0e-9);
        assertTrue("free start did not translate away from the infeasible seed (" + resAxis + " vs " + seedAxis + ")",
                Math.abs(resAxis - seedAxis) >= minMove);

        JumpPhysicsInputs atRes = withStart(freePhys, res.startX, res.startZ,
                StartBox.pinned(res.startX, res.startZ, vx, vz));
        double[] gf = atRes.toGameFacings(Angles.wrapAll(res.yaws));
        ForwardPath path = exact.forward(atRes, gf);
        double viol = JumpConstraintCompiler.compile(new JumpSpec(atRes, cons, obj)).maxViolation(gf, path);
        assertTrue("free-start solution not byte-exact feasible at the pinned start (viol=" + viol + ")", viol <= 0.0);

        System.out.printf("FREESTART beyond=%.2f foot=%.2f  reach=%.4f wall=%.4f seed=%.4f -> start=%.4f (moved %.4f)%n",
                beyondReach, footprint, reach, wallRhs, seedAxis, resAxis, resAxis - seedAxis);
    }

    private static double objectivePos(ExactJumpModel exact, JumpPhysicsInputs sc, Objective obj, double[] yaws) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        return exact.forward(sc, gf).getPos(obj.tick, obj.axis);
    }

    private static JumpPhysicsInputs withStart(JumpPhysicsInputs b, double p0x, double p0z, StartBox box) {
        JumpPhysicsInputs a = new JumpPhysicsInputs(b.numTicks);
        a.startPos = new Vec3dCore(p0x, b.startPos.y, p0z);
        a.startYaw = b.startYaw;
        a.initialVelocity = b.initialVelocity;
        a.startBox = box;
        a.jumpTick = b.jumpTick;
        a.jumpPerTick = b.jumpPerTick;
        a.strafeSign = b.strafeSign;
        a.strafePerTick = b.strafePerTick;
        a.speedAmplifier = b.speedAmplifier;
        a.slipPerTick = b.slipPerTick;
        a.yawLockedPerTick = b.yawLockedPerTick;
        a.sprintPerTick = b.sprintPerTick;
        a.incomingSprint = b.incomingSprint;
        a.incomingAmp = b.incomingAmp;
        a.forwardInputPerTick = b.forwardInputPerTick;
        a.strafeInputPerTick = b.strafeInputPerTick;
        return a;
    }
}
