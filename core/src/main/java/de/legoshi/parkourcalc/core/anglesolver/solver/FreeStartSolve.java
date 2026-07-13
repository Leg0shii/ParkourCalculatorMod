package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FreeStartSolve {

    private FreeStartSolve() {
    }

    private static final double EMPTY_TOL = 1.0e-9;

    public static final class Config {
        public int maxIters = 12;
        public double intervalMargin = 1.0e-3;
        public double invariantTol = 1.0e-6;
        public double stepTol = 1.0e-9;
        public int slpPhase1Calls = 40;
        public int slpTotalCalls = 60;
        public double[] jointMargins = {0.0, 1.0e-4, 3.0e-4, 6.0e-4, 1.2e-3, 2.5e-3, 5.0e-3, 1.0e-2};
    }

    public static final class Result {
        public final double[] yaws;
        public final double startX;
        public final double startZ;
        public final boolean feasible;

        Result(double[] yaws, double startX, double startZ, boolean feasible) {
            this.yaws = yaws;
            this.startX = startX;
            this.startZ = startZ;
            this.feasible = feasible;
        }
    }

    public static Result solve(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        return solve(exact, spec, feasTol, cancel, new Config());
    }

    public static Result solve(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel, Config cfg) {
        JumpPhysicsInputs base = spec.asScenario();
        StartBox box = base.startBox;
        if (box == null || !box.startFree()) return null;

        double p0x = clamp(box.px, box.pxLo, box.pxHi);
        double p0z = clamp(box.pz, box.pzLo, box.pzHi);

        if (SolverTrace.on()) {
            SolverTrace.log("FREE", "start box=%s seedStart=(%.4f,%.4f) center=(%.4f,%.4f)",
                    box.label(), base.startPos.x, base.startPos.z, p0x, p0z);
        }

        for (int iter = 0; iter < cfg.maxIters; iter++) {
            if (cancel != null && cancel.get()) return null;
            JumpSpec at = specAtStart(base, spec, p0x, p0z);

            double[] yaws = ClosedFormSolve.optimize(exact, at, feasTol, cancel);
            if (yaws == null) yaws = SlpSolve.optimize(exact, at, feasTol, cancel);
            if (yaws != null) {
                if (SolverTrace.on()) SolverTrace.log("FREE", "iter=%d feasible at start=(%.4f,%.4f)", iter, p0x, p0z);
                return new Result(yaws, p0x, p0z, true);
            }

            double[] shape = bestEffortShape(exact, at, feasTol, cancel, cfg);
            if (shape == null) break;
            JumpPhysicsInputs atSc = at.asScenario();
            double[] gf = atSc.toGameFacings(Angles.wrapAll(shape));
            ForwardPath path = exact.forward(atSc, gf);

            double[] delta = pinTranslate(spec, gf, path, box, p0x, p0z, cfg);
            if (delta == null) {
                if (SolverTrace.on()) SolverTrace.log("FREE", "iter=%d no feasible translation of the current shape", iter);
                break;
            }
            double nx = clamp(p0x + delta[0], box.pxLo, box.pxHi);
            double nz = clamp(p0z + delta[1], box.pzLo, box.pzHi);
            if (SolverTrace.on()) {
                SolverTrace.log("FREE", "iter=%d translate (%.4f,%.4f) -> (%.4f,%.4f)", iter, p0x, p0z, nx, nz);
            }
            if (Math.abs(nx - p0x) < cfg.stepTol && Math.abs(nz - p0z) < cfg.stepTol) break;
            p0x = nx;
            p0z = nz;
        }
        return null;
    }

    public static Result solveJoint(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        return solveJoint(exact, spec, feasTol, cancel, new Config());
    }

    public static Result solveJoint(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                    Config cfg) {
        JumpPhysicsInputs base = spec.asScenario();
        StartBox box = base.startBox;
        if (box == null || !box.startFree()) return null;
        if (JumpLinearModel.hasFacingWall(spec.constraints)) return solve(exact, spec, feasTol, cancel, cfg);

        double refX = 0.5 * (box.pxLo + box.pxHi);
        double refZ = 0.5 * (box.pzLo + box.pzHi);
        StartBox cbox = new StartBox(refX, refZ, box.vx, box.vz, box.pxLo, box.pxHi, box.pzLo, box.pzHi,
                box.vxLo, box.vxHi, box.vzLo, box.vzHi);
        JumpPhysicsInputs refSc = copyWithStart(base, refX, refZ);
        JumpLinearModel lin = new JumpLinearModel(refSc);
        double[] cx = new double[lin.n];
        double[] cz = new double[lin.n];
        lin.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivial = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivial);
        if (trivial[0]) { lastJointDebug = "trivial-infeasible"; return null; }
        CostateDualSolver solver = new CostateDualSolver(lin.n, cx, cz, lin.mMagAll(), walls, buildFreeP0(cbox, spec.objective));

        double bestViol = Double.POSITIVE_INFINITY;
        double[] bestYaws = null;
        double[] warm = null;
        for (double margin : cfg.jointMargins) {
            if (cancel != null && cancel.get()) return null;
            CostateDualSolver.Result r = solver.solve(margin, warm);
            if (r == null) { lastJointDebug = "dual-null margin=" + margin; return null; }
            warm = r.lambda;
            double[] yaws = recover(lin, spec.objective, r);
            if (bestYaws == null) bestYaws = yaws;
            double[] gf = refSc.toGameFacings(Angles.wrapAll(yaws));
            ForwardPath path = exact.forward(refSc, gf);
            double[] delta = pinTranslate(spec, gf, path, cbox, refX, refZ, cfg);
            double p0x = refX;
            double p0z = refZ;
            if (delta != null) {
                p0x = clamp(refX + delta[0], box.pxLo, box.pxHi);
                p0z = clamp(refZ + delta[1], box.pzLo, box.pzHi);
            }
            JumpSpec atSpec = specAtStart(base, spec, p0x, p0z);
            JumpPhysicsInputs atSc = atSpec.asScenario();
            double[] atGf = atSc.toGameFacings(Angles.wrapAll(yaws));
            ForwardPath atPath = exact.forward(atSc, atGf);
            double viol = JumpConstraintCompiler.compile(atSpec).maxViolation(atGf, atPath);
            bestViol = Math.min(bestViol, viol);
            if (viol <= feasTol) {
                if (SolverTrace.on()) {
                    SolverTrace.log("FREE", "joint solved margin=%.2e start=(%.4f,%.4f)", margin, p0x, p0z);
                }
                return new Result(yaws, p0x, p0z, true);
            }
        }
        double recViol = -1.0;
        if (bestYaws != null) {
            double[] rs2 = recoverStart(exact, spec, bestYaws, cfg);
            if (rs2 != null) recViol = violationAt(exact, spec, bestYaws, rs2[0], rs2[1]);
        }
        lastJointDebug = String.format("no-certify pinTranslateViol=%.3e recoverStartViol=%.3e", bestViol, recViol);
        return null;
    }

    public static volatile String lastJointDebug = "";

    public static double[] bestTranslate(JumpSpec spec, double[] gf, ForwardPath path, StartBox box) {
        return bestTranslate(spec, gf, path, box, 0.0);
    }

    public static double[] bestTranslate(JumpSpec spec, double[] gf, ForwardPath path, StartBox box, double margin) {
        double loX = Double.NEGATIVE_INFINITY, hiX = Double.POSITIVE_INFINITY;
        double loZ = Double.NEGATIVE_INFINITY, hiZ = Double.POSITIVE_INFINITY;
        for (JumpConstraint c : spec.constraints) {
            int axis = c.mode == JumpConstraint.Mode.X ? 0 : c.mode == JumpConstraint.Mode.Z ? 1 : -1;
            if (axis < 0) continue;
            int tc = (c.t2 == null) ? 1 : (c.op == JumpConstraint.Op.PLUS ? 2 : 0);
            if (tc == 0) continue;
            double e0 = JumpConstraintCompiler.evaluate(c, gf, path);
            if (c.cmp == JumpConstraint.Cmp.LE) {
                double b = (-e0 - margin) / tc;
                if (axis == 0) hiX = Math.min(hiX, b); else hiZ = Math.min(hiZ, b);
            } else if (c.cmp == JumpConstraint.Cmp.GE) {
                double b = (-e0 + margin) / tc;
                if (axis == 0) loX = Math.max(loX, b); else loZ = Math.max(loZ, b);
            } else {
                double b = -e0 / tc;
                if (axis == 0) { loX = Math.max(loX, b); hiX = Math.min(hiX, b); }
                else { loZ = Math.max(loZ, b); hiZ = Math.min(hiZ, b); }
            }
        }
        Objective obj = spec.objective;
        int objAxis = obj.axis == JumpPhysicsInputs.Axis.X ? 0 : 1;
        boolean max = obj.sense == Objective.Sense.MAX;
        double dx = pickBest(loX, hiX, box.pxLo - box.px, box.pxHi - box.px, objAxis == 0, max);
        double dz = pickBest(loZ, hiZ, box.pzLo - box.pz, box.pzHi - box.pz, objAxis == 1, max);
        return new double[] {dx, dz};
    }

    private static double pickBest(double lo, double hi, double bLo, double bHi, boolean objectiveAxis, boolean max) {
        double flo = Math.max(lo, bLo);
        double fhi = Math.min(hi, bHi);
        if (flo <= fhi) return 0.5 * (flo + fhi);
        if (bHi < lo) return bHi;
        if (bLo > hi) return bLo;
        return clamp(0.0, bLo, bHi);
    }

    public static double violationAt(ExactJumpModel exact, JumpSpec spec, double[] yaws, double p0x, double p0z) {
        JumpSpec at = specAtStart(spec.asScenario(), spec, p0x, p0z);
        JumpPhysicsInputs atSc = at.asScenario();
        double[] gf = atSc.toGameFacings(Angles.wrapAll(yaws));
        return JumpConstraintCompiler.compile(at).maxViolation(gf, exact.forward(atSc, gf));
    }

    public static double[] recoverStart(ExactJumpModel exact, JumpSpec spec, double[] yaws) {
        return recoverStart(exact, spec, yaws, new Config());
    }

    public static double[] recoverStart(ExactJumpModel exact, JumpSpec spec, double[] yaws, Config cfg) {
        JumpPhysicsInputs base = spec.asScenario();
        StartBox box = base.startBox;
        if (box == null || !box.startFree() || yaws == null) return null;
        JumpPhysicsInputs refSc = copyWithStart(base, box.px, box.pz);
        double[] wrapped = Angles.wrapAll(yaws);
        double[] gf = refSc.toGameFacings(wrapped);
        ForwardPath path = exact.forward(refSc, gf);
        double[] best = null;
        for (double margin : cfg.jointMargins) {
            double[] d = bestTranslate(spec, gf, path, box, margin);
            double p0x = clamp(box.px + d[0], box.pxLo, box.pxHi);
            double p0z = clamp(box.pz + d[1], box.pzLo, box.pzHi);
            if (best == null) best = new double[] {p0x, p0z};
            JumpSpec at = specAtStart(base, spec, p0x, p0z);
            JumpPhysicsInputs atSc = at.asScenario();
            double[] agf = atSc.toGameFacings(wrapped);
            if (JumpConstraintCompiler.compile(at).maxViolation(agf, exact.forward(atSc, agf)) <= 0.0) {
                return new double[] {p0x, p0z};
            }
        }
        return best;
    }

    private static CostateDualSolver.FreeP0 buildFreeP0(StartBox box, Objective obj) {
        boolean max = obj.sense == Objective.Sense.MAX;
        double objDevX = obj.axis == JumpPhysicsInputs.Axis.X ? (max ? 1.0 : -1.0) : 0.0;
        double objDevZ = obj.axis == JumpPhysicsInputs.Axis.X ? 0.0 : (max ? 1.0 : -1.0);
        return new CostateDualSolver.FreeP0(box.pxLo - box.px, box.pxHi - box.px,
                box.pzLo - box.pz, box.pzHi - box.pz, objDevX, objDevZ);
    }

    private static double[] pinTranslate(JumpSpec spec, double[] gf, ForwardPath path, StartBox box,
                                         double p0x, double p0z, Config cfg) {
        double loX = Double.NEGATIVE_INFINITY, hiX = Double.POSITIVE_INFINITY;
        double loZ = Double.NEGATIVE_INFINITY, hiZ = Double.POSITIVE_INFINITY;
        for (JumpConstraint c : spec.constraints) {
            int axis = c.mode == JumpConstraint.Mode.X ? 0 : c.mode == JumpConstraint.Mode.Z ? 1 : -1;
            double e0 = JumpConstraintCompiler.evaluate(c, gf, path);
            if (axis < 0) {
                double slack = c.cmp == JumpConstraint.Cmp.GE ? Math.max(0.0, -e0)
                        : c.cmp == JumpConstraint.Cmp.LE ? Math.max(0.0, e0) : Math.abs(e0);
                if (slack > cfg.invariantTol) return null;
                continue;
            }
            int tc = (c.t2 == null) ? 1 : (c.op == JumpConstraint.Op.PLUS ? 2 : 0);
            if (tc == 0) {
                double slack = c.cmp == JumpConstraint.Cmp.GE ? Math.max(0.0, -e0)
                        : c.cmp == JumpConstraint.Cmp.LE ? Math.max(0.0, e0) : Math.abs(e0);
                if (slack > cfg.invariantTol) return null;
                continue;
            }
            if (c.cmp == JumpConstraint.Cmp.LE) {
                double b = -e0 / tc - cfg.intervalMargin;
                if (axis == 0) hiX = Math.min(hiX, b); else hiZ = Math.min(hiZ, b);
            } else if (c.cmp == JumpConstraint.Cmp.GE) {
                double b = -e0 / tc + cfg.intervalMargin;
                if (axis == 0) loX = Math.max(loX, b); else loZ = Math.max(loZ, b);
            } else {
                double b = -e0 / tc;
                if (axis == 0) { loX = Math.max(loX, b); hiX = Math.min(hiX, b); }
                else { loZ = Math.max(loZ, b); hiZ = Math.min(hiZ, b); }
            }
        }
        loX = Math.max(loX, box.pxLo - p0x);
        hiX = Math.min(hiX, box.pxHi - p0x);
        loZ = Math.max(loZ, box.pzLo - p0z);
        hiZ = Math.min(hiZ, box.pzHi - p0z);
        if (loX > hiX + EMPTY_TOL || loZ > hiZ + EMPTY_TOL) return null;

        Objective obj = spec.objective;
        int objAxis = obj.axis == JumpPhysicsInputs.Axis.X ? 0 : 1;
        boolean max = obj.sense == Objective.Sense.MAX;
        double dx = pickDelta(loX, hiX, objAxis == 0, max);
        double dz = pickDelta(loZ, hiZ, objAxis == 1, max);
        return new double[] {dx, dz};
    }

    private static double pickDelta(double lo, double hi, boolean objectiveAxis, boolean max) {
        if (lo > hi) return 0.0;
        if (objectiveAxis) return max ? hi : lo;
        return clamp(0.0, lo, hi);
    }

    private static double[] bestEffortShape(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                            Config cfg) {
        JumpPhysicsInputs sc = spec.asScenario();
        if (!JumpLinearModel.hasFacingWall(spec.constraints)) {
            JumpLinearModel lin = new JumpLinearModel(sc);
            double[] cx = new double[lin.n];
            double[] cz = new double[lin.n];
            lin.objectiveVectors(spec.objective, cx, cz);
            boolean[] trivial = {false};
            List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivial);
            if (!trivial[0]) {
                CostateDualSolver.Result r = new CostateDualSolver(lin.n, cx, cz, lin.mMagAll(), walls).solve(0.0, null);
                if (r != null) return recover(lin, spec.objective, r);
            }
        }
        double[] seed = objectiveSeed(sc, spec.objective);
        return SlpSolve.optimizeBestEffort(exact, spec, feasTol, cancel, seed, cfg.slpPhase1Calls, cfg.slpTotalCalls);
    }

    private static double[] objectiveSeed(JumpPhysicsInputs sc, Objective obj) {
        JumpLinearModel lin = new JumpLinearModel(sc);
        boolean max = obj.sense == Objective.Sense.MAX;
        double gx = obj.axis == JumpPhysicsInputs.Axis.X ? (max ? 1.0 : -1.0) : 0.0;
        double gz = obj.axis == JumpPhysicsInputs.Axis.X ? 0.0 : (max ? 1.0 : -1.0);
        double[] yaws = new double[lin.n];
        for (int t = 0; t < lin.n; t++) yaws[t] = lin.recoverYawDeg(t, gx, gz);
        return yaws;
    }

    private static double[] recover(JumpLinearModel lin, Objective obj, CostateDualSolver.Result r) {
        int n = lin.n;
        double[] yaws = new double[n];
        boolean max = obj.sense == Objective.Sense.MAX;
        boolean axisX = obj.axis == JumpPhysicsInputs.Axis.X;
        for (int t = 0; t < n; t++) {
            double gx = r.gx[t], gz = r.gz[t];
            if (gx * gx + gz * gz < 1.0e-18) {
                gx = axisX ? (max ? 1.0 : -1.0) : 0.0;
                gz = axisX ? 0.0 : (max ? 1.0 : -1.0);
            }
            yaws[t] = lin.recoverYawDeg(t, gx, gz);
        }
        return yaws;
    }

    private static JumpSpec specAtStart(JumpPhysicsInputs base, JumpSpec spec, double p0x, double p0z) {
        return new JumpSpec(copyWithStart(base, p0x, p0z), spec.constraints, spec.objective);
    }

    private static JumpPhysicsInputs copyWithStart(JumpPhysicsInputs b, double p0x, double p0z) {
        JumpPhysicsInputs a = new JumpPhysicsInputs(b.numTicks);
        a.startPos = new Vec3dCore(p0x, b.startPos.y, p0z);
        a.startYaw = b.startYaw;
        a.initialVelocity = b.initialVelocity;
        a.startBox = StartBox.pinned(p0x, p0z, b.initialVelocity.x, b.initialVelocity.z);
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

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
