package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.CostateDualSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.DiskSocpKernel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Stage P5 probe: the free-start p0 columns folded into the P3 {@link DiskSocpKernel}. The free-start disk
 *  relaxation adds two box-bounded start-translation variables whose objective coefficient is +-1 and whose
 *  wall coefficient is the compiled {@code p0coef = +-tc}; the dual absorbs them as one smoothed box-support
 *  term per axis (h_a = objDev_a + sum_j lambda_j p0coef_j, delta clamped to the box), exactly the
 *  {@link CostateDualSolver.FreeP0} mechanism the shipped joint dual already carries. This asserts the IPM
 *  converges the free-start bound, recovers a feasible disk primal and the optimal in-box translation, matches
 *  the {@link CostateDualSolver.FreeP0} reference, and beats the fixed-reference bound (the F4 rigid-translation
 *  separability / the COPT "feasible once p0 is free" flip). Facing (dF) walls drop out of {@code compileWalls}
 *  so the dF-chain capture runs as its position-only relaxation, the model COPT solves in 0.13 s. */
@Category(SlowSolverTests.class)
public class FreeStartKernelProbe {

    private static final double SMOOTH = 5.0e-4;
    private static final double BOUND_MATCH = 1.0e-5;
    private static final double DELTA_MATCH = 1.0e-3;

    @Test
    public void synthFreeTranslateReachesTheBoxEdge() {
        Case c = build("synth-free-translate", SMOOTH);
        report(c);
        assertTrue("free box must be derived", c.startFree);
        assertNotNull("free-start kernel returned null", c.free);
        assertTrue("free-start kernel must converge, gap=" + c.freeGap, c.freeConverged);
        assertTrue("free-start disk primal must be feasible, max|u|-m=" + c.freeDiskViol,
                c.freeDiskViol <= 1.0e-9);
        assertTrue("free-start bound must match the CostateDualSolver.FreeP0 reference " + c.dualBound
                + ", was " + c.freeBound, Math.abs(c.freeBound - c.dualBound) <= BOUND_MATCH);
        assertTrue("recovered X translation must match the reference, disk=" + c.freeDvx + " dual=" + c.dualDvx,
                Math.abs(c.freeDvx - c.dualDvx) <= DELTA_MATCH);
        double edge = c.box.pxHi - c.box.px;
        assertTrue("MAX-X free start must recover the +X box edge (delta " + edge + "), was " + c.freeDvx,
                Math.abs(c.freeDvx - edge) <= DELTA_MATCH);
        assertTrue("free-start bound must beat the fixed-reference bound " + c.fixedBound + " by ~the box width, was "
                + c.freeBound, c.freeBound > c.fixedBound + 0.4);
        assertTrue("byte-exact objective at the recovered free start must beat the fixed-reference objective ("
                + c.byteObjFixed + " -> " + c.byteObjFree + ")", c.byteObjFree > c.byteObjFixed);
        assertTrue("byte-exact free-start trajectory must be feasible, viol=" + c.byteViolFree,
                c.byteViolFree <= 0.0);
    }

    @Test
    public void dfChainFreeStartFeasibleOnceP0IsFree() {
        Case c = build("df-chain-free-start", SMOOTH);
        report(c);
        assertTrue("free box must be derived", c.startFree);
        assertNotNull("free-start kernel returned null", c.free);
        assertTrue("free-start kernel must converge, gap=" + c.freeGap, c.freeConverged);
        assertTrue("free-start disk primal must be feasible, max|u|-m=" + c.freeDiskViol,
                c.freeDiskViol <= 1.0e-9);
        assertTrue("df-chain position-only relaxation must carry position walls, m=" + c.m, c.m > 0);
        assertTrue("free-start bound must match the CostateDualSolver.FreeP0 reference " + c.dualBound
                + ", was " + c.freeBound, Math.abs(c.freeBound - c.dualBound) <= BOUND_MATCH);
        assertTrue("recovered translation must match the reference (X " + c.freeDvx + "/" + c.dualDvx + ", Z "
                + c.freeDvz + "/" + c.dualDvz + ")",
                Math.abs(c.freeDvx - c.dualDvx) <= DELTA_MATCH && Math.abs(c.freeDvz - c.dualDvz) <= DELTA_MATCH);
        assertTrue("free-start must be feasible where the fixed reference is infeasible or worse (fixedBound="
                + c.fixedBound + ", freeBound=" + c.freeBound + ")",
                c.fixedNull || c.freeBound >= c.fixedBound - BOUND_MATCH);
    }

    @Test
    public void determinismHolds() {
        Case a = build("df-chain-free-start", SMOOTH);
        Case b = build("df-chain-free-start", SMOOTH);
        assertTrue("free-start bound must be deterministic", a.freeBound == b.freeBound);
        assertTrue("free-start translation must be deterministic",
                a.freeDvx == b.freeDvx && a.freeDvz == b.freeDvz);
    }

    private static final class Case {
        String name;
        int n;
        int m;
        boolean startFree;
        StartBox box;
        DiskSocpKernel.Result free;
        boolean freeConverged;
        double freeBound;
        double freeGap;
        double freeDiskViol;
        double freeDvx;
        double freeDvz;
        double dualBound;
        double dualDvx;
        double dualDvz;
        boolean fixedNull;
        double fixedBound;
        double byteObjFree;
        double byteObjFixed;
        double byteViolFree;
    }

    private Case build(String cap, double smooth) {
        String raw = Fixtures.rawPool(cap);
        SaveFile file = SaveIO.parseSafe(raw);
        assertNotNull(cap + ": parse", file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        assertNotNull(cap + ": spec", spec);
        JumpPhysicsInputs sc = spec.asScenario();

        Case c = new Case();
        c.name = cap;
        c.box = sc.startBox;
        c.startFree = c.box != null && c.box.startFree();
        if (!c.startFree) return c;

        JumpLinearModel lin = new JumpLinearModel(sc);
        int n = lin.n;
        double[] cx = new double[n];
        double[] cz = new double[n];
        lin.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivial = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivial);
        double[] mMag = lin.mMagAll();
        int axis = spec.objective.axis == JumpPhysicsInputs.Axis.X ? 0 : 1;
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        double constPos = lin.constPos(spec.objective.tick, axis);
        c.n = n;
        c.m = walls.size();

        CostateDualSolver.FreeP0 freeP0 = buildFreeP0(c.box, spec.objective, smooth);

        DiskSocpKernel.Result free = DiskSocpKernel.solve(n, cx, cz, mMag, walls, freeP0);
        c.free = free;
        if (free != null) {
            c.freeConverged = free.converged;
            c.freeBound = max ? constPos + free.value : constPos - free.value;
            c.freeGap = free.gap;
            c.freeDvx = free.dvx;
            c.freeDvz = free.dvz;
            double mv = 0.0;
            for (int t = 0; t < n; t++) mv = Math.max(mv, Math.hypot(free.ux[t], free.uz[t]) - mMag[t]);
            c.freeDiskViol = mv;
        }

        CostateDualSolver.Result dual = new CostateDualSolver(n, cx, cz, mMag, walls, freeP0).solve(0.0, null);
        if (dual != null) {
            c.dualBound = max ? constPos + dual.value : constPos - dual.value;
            c.dualDvx = dual.dvx;
            c.dualDvz = dual.dvz;
        } else {
            c.dualBound = Double.NaN;
        }

        DiskSocpKernel.Result fixed = DiskSocpKernel.solve(n, cx, cz, mMag, walls);
        if (fixed == null || !fixed.converged) {
            c.fixedNull = true;
            c.fixedBound = Double.NEGATIVE_INFINITY;
        } else {
            c.fixedBound = max ? constPos + fixed.value : constPos - fixed.value;
        }

        c.byteObjFixed = byteObjective(model, sc, spec, lin, c.box.px, c.box.pz, new double[]{Double.NaN});
        double p0x = clamp(c.box.px + c.freeDvx, c.box.pxLo, c.box.pxHi);
        double p0z = clamp(c.box.pz + c.freeDvz, c.box.pzLo, c.box.pzHi);
        double[] viol = {Double.NaN};
        c.byteObjFree = byteObjective(model, sc, spec, lin, p0x, p0z, viol);
        c.byteViolFree = viol[0];
        return c;
    }

    private static double byteObjective(ExactJumpModel model, JumpPhysicsInputs sc, JumpSpec spec,
                                        JumpLinearModel lin, double p0x, double p0z, double[] violOut) {
        int n = lin.n;
        double[] yaws = new double[n];
        for (int t = 0; t < n; t++) {
            double gx = spec.objective.axis == JumpPhysicsInputs.Axis.X
                    ? (spec.objective.sense == Objective.Sense.MAX ? 1.0 : -1.0) : 0.0;
            double gz = spec.objective.axis == JumpPhysicsInputs.Axis.X ? 0.0
                    : (spec.objective.sense == Objective.Sense.MAX ? 1.0 : -1.0);
            yaws[t] = lin.recoverYawDeg(t, gx, gz);
        }
        JumpPhysicsInputs at = sc.copy();
        at.startPos = new Vec3dCore(p0x, sc.startPos.y, p0z);
        at.startBox = StartBox.pinned(p0x, p0z, sc.initialVelocity.x, sc.initialVelocity.z);
        double[] wrapped = Angles.wrapAll(yaws);
        double[] gf = at.toGameFacings(wrapped);
        ForwardPath path = model.forward(at, gf);
        List<JumpConstraint> pos = new ArrayList<>();
        for (JumpConstraint jc : spec.constraints) {
            if (jc.mode == JumpConstraint.Mode.X || jc.mode == JumpConstraint.Mode.Z) pos.add(jc);
        }
        JumpSpec posSpec = new JumpSpec(at, pos, spec.objective);
        violOut[0] = JumpConstraintCompiler.compile(posSpec).maxViolation(gf, path);
        return path.getPos(spec.objective.tick, spec.objective.axis);
    }

    private static CostateDualSolver.FreeP0 buildFreeP0(StartBox box, Objective obj, double smooth) {
        boolean max = obj.sense == Objective.Sense.MAX;
        double objDevX = obj.axis == JumpPhysicsInputs.Axis.X ? (max ? 1.0 : -1.0) : 0.0;
        double objDevZ = obj.axis == JumpPhysicsInputs.Axis.X ? 0.0 : (max ? 1.0 : -1.0);
        return new CostateDualSolver.FreeP0(box.pxLo - box.px, box.pxHi - box.px,
                box.pzLo - box.pz, box.pzHi - box.pz, objDevX, objDevZ, smooth);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static void report(Case c) {
        System.out.printf(
                "FREE %-22s n=%d m=%d startFree=%b conv=%b%n"
                + "    freeBound=%.9f dualBound=%.9f fixedBound=%.9f fixedNull=%b%n"
                + "    dv(disk)=(%.6f,%.6f) dv(dual)=(%.6f,%.6f) diskViol=%.3e gap=%.3e%n"
                + "    byteObj fixed=%.9f free=%.9f violFree=%.3e%n",
                c.name, c.n, c.m, c.startFree, c.freeConverged,
                c.freeBound, c.dualBound, c.fixedBound, c.fixedNull,
                c.freeDvx, c.freeDvz, c.dualDvx, c.dualDvz, c.freeDiskViol, c.freeGap,
                c.byteObjFixed, c.byteObjFree, c.byteViolFree);
    }
}
