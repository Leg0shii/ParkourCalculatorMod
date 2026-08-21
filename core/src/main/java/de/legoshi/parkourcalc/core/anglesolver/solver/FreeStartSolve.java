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
        public int jointPatternCap = 8;
        public double jointPatternViolGate = 0.25;
        public double jointMarginMax = 0.25;
        public int jointBisectIters = 8;
        public double jointWrapCloseGate = 0.01;
        public boolean jointWrapClose = true;
    }

    private static final double[] JOINT_RECOVERY_FRACTIONS = {0.5, 0.25, 0.75, 0.0};
    private static final long JOINT_WRAP_CLOSE_NANOS = 6_000_000_000L;
    private static final double JOINT_WRAP_REPAIR_GATE = 1.0e-3;

    private static final class JointBest {
        double viol = Double.POSITIVE_INFINITY;
        double[] yaws;
        double theta = Double.NaN;
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
        return slpAnchorGrid(exact, spec, base, box, feasTol, cancel);
    }

    private static final double[] ANCHOR_GRID_FRACTIONS = {0.5, 0.25, 0.75, 0.0, 1.0};

    private static Result slpAnchorGrid(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs base, StartBox box,
                                        double feasTol, AtomicBoolean cancel) {
        double seedX = clamp(box.px, box.pxLo, box.pxHi);
        double seedZ = clamp(box.pz, box.pzLo, box.pzHi);
        for (double fx : ANCHOR_GRID_FRACTIONS) {
            for (double fz : ANCHOR_GRID_FRACTIONS) {
                if (cancel != null && cancel.get()) return null;
                double px = box.pxLo + (box.pxHi - box.pxLo) * fx;
                double pz = box.pzLo + (box.pzHi - box.pzLo) * fz;
                if (Math.abs(px - seedX) < 1.0e-9 && Math.abs(pz - seedZ) < 1.0e-9) continue;
                JumpSpec at = specAtStart(base, spec, px, pz);
                double[] yaws = SlpSolve.optimize(exact, at, feasTol, cancel);
                if (yaws != null && violationAt(exact, spec, yaws, px, pz) <= feasTol) {
                    if (SolverTrace.on()) {
                        SolverTrace.log("FREE", "anchor grid solved at (%.4f,%.4f)", px, pz);
                    }
                    return new Result(Angles.wrapAll(yaws.clone()), px, pz, true);
                }
            }
        }
        if (SolverTrace.on()) SolverTrace.log("FREE", "anchor grid miss");
        return null;
    }

    public static Result solveJoint(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel) {
        return solveJoint(exact, spec, feasTol, cancel, new Config());
    }

    public static Result solveJoint(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                    Config cfg) {
        Result r = solveJointBest(exact, spec, feasTol, cancel, cfg);
        return r != null && r.feasible ? r : null;
    }

    public static Result solveJointBest(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel,
                                        Config cfg) {
        JumpPhysicsInputs base = spec.asScenario();
        StartBox box = base.startBox;
        if (box == null || !box.startFree()) return null;

        double refX = 0.5 * (box.pxLo + box.pxHi);
        double refZ = 0.5 * (box.pzLo + box.pzHi);
        StartBox cbox = new StartBox(refX, refZ, box.vx, box.vz, box.pxLo, box.pxHi, box.pzLo, box.pzHi,
                box.vxLo, box.vxHi, box.vzLo, box.vzHi);
        JumpPhysicsInputs refSc = copyWithStart(base, refX, refZ);
        double[] arcThetas = null;
        FacingPrefold.ChainScan scan0 = FacingPrefold.scannable(spec.constraints, new JumpLinearModel(refSc));
        if (scan0 != null) arcThetas = prefixArcThetas(exact, spec, refSc, box, scan0);
        JointBest freeBest = new JointBest();
        Result r0 = jointDispatch(exact, spec, base, box, cbox, refSc, new JumpLinearModel(refSc),
                refX, refZ, feasTol, cancel, cfg, freeBest, arcThetas);
        if (r0 != null && r0.feasible) return r0;
        if (freeBest.yaws == null || freeBest.viol > cfg.jointPatternViolGate) {
            return solve(exact, spec, feasTol, cancel, cfg);
        }

        JointBest overall = new JointBest();
        if (freeBest.viol < overall.viol) {
            overall.viol = freeBest.viol;
            overall.yaws = freeBest.yaws;
        }
        java.util.ArrayDeque<boolean[][]> queue = new java.util.ArrayDeque<boolean[][]>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        enqueuePattern(queue, seen, refSc, exact, freeBest.yaws);
        int tried = 0;
        while (!queue.isEmpty() && tried < cfg.jointPatternCap) {
            if (cancel != null && cancel.get()) return null;
            boolean[][] pat = queue.poll();
            tried++;
            JumpLinearModel lin = new JumpLinearModel(refSc, pat[0], pat[1]);
            JointBest best = new JointBest();
            Result r = jointDispatch(exact, spec, base, box, cbox, refSc, lin, refX, refZ, feasTol, cancel,
                    cfg, best, arcThetas);
            if (SolverTrace.on()) {
                SolverTrace.log("FREE", "joint pattern %s bestViol=%s%s", SolverTrace.patternLabel(pat[0], pat[1]),
                        best.yaws == null ? "none" : String.format(java.util.Locale.ROOT, "%.3e", best.viol),
                        r != null && r.feasible ? " SOLVED" : "");
            }
            if (r != null && r.feasible) return r;
            if (best.yaws != null) {
                if (best.viol < overall.viol) {
                    overall.viol = best.viol;
                    overall.yaws = best.yaws;
                }
                if (best.viol <= cfg.jointPatternViolGate) {
                    enqueuePattern(queue, seen, refSc, exact, best.yaws);
                }
            }
        }
        Result fb = solve(exact, spec, feasTol, cancel, cfg);
        if (fb != null) return fb;
        if (overall.yaws != null) {
            double[] rs = recoverStart(exact, spec, overall.yaws, cfg);
            if (rs != null) {
                double[] repaired = LatticeRepair.repair(exact, specAtStart(base, spec, rs[0], rs[1]),
                        Angles.wrapAll(overall.yaws.clone()), feasTol, cancel);
                if (repaired != null && violationAt(exact, spec, repaired, rs[0], rs[1]) <= feasTol) {
                    if (SolverTrace.on()) {
                        SolverTrace.log("FREE", "joint best certified by lattice repair (%.5f,%.5f)", rs[0], rs[1]);
                    }
                    return new Result(repaired, rs[0], rs[1], true);
                }
                if (cfg.jointWrapClose && overall.viol <= cfg.jointWrapCloseGate) {
                    Result wr = jointWrapClose(exact, base, spec, box, overall.yaws, rs, feasTol, cancel);
                    if (wr != null) return wr;
                }
                return new Result(Angles.wrapAll(overall.yaws.clone()), rs[0], rs[1], false);
            }
        }
        return null;
    }

    private static Result jointWrapClose(ExactJumpModel exact, JumpPhysicsInputs base, JumpSpec spec, StartBox box,
                                         double[] yaws, double[] rs, double feasTol, AtomicBoolean cancel) {
        JumpSpec atSpec = specAtStart(base, spec, rs[0], rs[1]);
        JumpPhysicsInputs atSc = atSpec.asScenario();
        double[] gf = atSc.toGameFacings(Angles.wrapAll(yaws.clone()));
        double[] dom = {box.pxLo - rs[0], box.pxHi - rs[0], box.pzLo - rs[1], box.pzHi - rs[1]};
        WrapWindowIls.Config wcfg = new WrapWindowIls.Config();
        wcfg.roundCap = 1;
        wcfg.evalCap = 4_000_000;
        WrapWindowIls.Result w = WrapWindowIls.polish(exact, atSpec, gf, dom, wcfg,
                System.nanoTime() + JOINT_WRAP_CLOSE_NANOS, cancel);
        if (w == null || w.viol > JOINT_WRAP_REPAIR_GATE) {
            if (SolverTrace.on()) {
                SolverTrace.log("FREE", "joint wrap close miss viol=%s",
                        w == null ? "-" : String.format(java.util.Locale.ROOT, "%.3e", w.viol));
            }
            return null;
        }
        double[] d = bestTranslate(atSpec, w.gf, exact.forward(atSc, w.gf), box);
        double px = clamp(rs[0] + d[0], box.pxLo, box.pxHi);
        double pz = clamp(rs[1] + d[1], box.pzLo, box.pzHi);
        double v = violationAt(exact, spec, w.gf, px, pz);
        if (SolverTrace.on()) {
            SolverTrace.log("FREE", "joint wrap close ils=%.3e reaccum=%.3e start=(%.5f,%.5f)", w.viol, v, px, pz);
        }
        if (v <= feasTol) return new Result(Angles.wrapAll(w.gf.clone()), px, pz, true);
        double[] repaired = LatticeRepair.repair(exact, specAtStart(base, spec, px, pz),
                Angles.wrapAll(w.gf.clone()), feasTol, cancel);
        if (repaired != null && violationAt(exact, spec, repaired, px, pz) <= feasTol) {
            if (SolverTrace.on()) {
                SolverTrace.log("FREE", "joint wrap close certified by lattice repair (%.5f,%.5f)", px, pz);
            }
            return new Result(repaired, px, pz, true);
        }
        return null;
    }

    private static Result jointDispatch(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs base, StartBox box,
                                        StartBox cbox, JumpPhysicsInputs refSc, JumpLinearModel lin,
                                        double refX, double refZ, double feasTol, AtomicBoolean cancel, Config cfg,
                                        JointBest best, double[] arcThetas) {
        FacingPrefold pre = FacingPrefold.analyze(spec.constraints, lin);
        if (pre != null) {
            return jointLadder(exact, spec, base, box, cbox, refSc, lin, refX, refZ, feasTol, cancel, cfg, pre, best);
        }
        FacingPrefold.ChainScan scan = FacingPrefold.scannable(spec.constraints, lin);
        if (scan == null) return null;
        double[] thetas = arcThetas != null ? arcThetas : ClosedFormSolve.candidateThetas(spec, lin, scan, cbox);
        if (thetas == null) return null;
        if (SolverTrace.on()) {
            StringBuilder sb = new StringBuilder();
            for (double th : thetas) sb.append(String.format(java.util.Locale.ROOT, "%.2f ", th));
            SolverTrace.log("FREE", "joint thetas: %s", sb.toString());
        }
        for (double th : thetas) {
            if (cancel != null && cancel.get()) return null;
            double before = best.viol;
            Result r = jointLadder(exact, spec, base, box, cbox, refSc, lin, refX, refZ, feasTol, cancel,
                    cfg, scan.at(th), best);
            if (best.viol < before) best.theta = th;
            if (r != null && r.feasible) {
                if (SolverTrace.on()) SolverTrace.log("FREE", "joint chain scan solved theta=%.4f", th);
                return r;
            }
        }
        Result mp = thetaMicroPolish(exact, spec, refSc, scan, best, feasTol, cancel, cfg);
        if (mp != null && mp.feasible) return mp;
        JointBest local = new JointBest();
        local.viol = best.viol;
        local.yaws = best.yaws;
        local.theta = best.theta;
        for (int it = 0; it < 6 && local.yaws != null && !Double.isNaN(local.theta) && local.viol <= 0.05; it++) {
            if (cancel != null && cancel.get()) return null;
            double before = local.viol;
            boolean[] zx = new boolean[refSc.numTicks];
            boolean[] zz = new boolean[refSc.numTicks];
            new JumpLinearModel(refSc).zeroingPattern(Angles.wrapAll(local.yaws.clone()),
                    exact.inertiaThreshold(), exact.perAxisInertia(), zx, zz);
            JumpLinearModel lin2 = new JumpLinearModel(refSc, zx, zz);
            FacingPrefold.ChainScan scan2 = FacingPrefold.scannable(spec.constraints, lin2);
            JumpLinearModel useLin = scan2 != null ? lin2 : lin;
            FacingPrefold.ChainScan useScan = scan2 != null ? scan2 : scan;
            Result rl = jointLadder(exact, spec, base, box, cbox, refSc, useLin, refX, refZ, feasTol, cancel,
                    cfg, useScan.at(local.theta), local);
            if (rl != null && rl.feasible) return rl;
            Result mp2 = thetaMicroPolish(exact, spec, refSc, useScan, local, feasTol, cancel, cfg);
            if (mp2 != null && mp2.feasible) return mp2;
            if (local.viol >= before - 1.0e-9) break;
        }
        if (SolverTrace.on()) SolverTrace.log("FREE", "joint chain scan miss cands=%d", thetas.length);
        return null;
    }

    private static Result thetaMicroPolish(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs refSc,
                                           FacingPrefold.ChainScan scan, JointBest best, double feasTol,
                                           AtomicBoolean cancel, Config cfg) {
        if (best.yaws == null || Double.isNaN(best.theta)) return null;
        if (best.viol > 0.15) return null;
        int n = refSc.numTicks;
        boolean[] open = new boolean[n];
        for (int t = 0; t < n; t++) open[t] = scan.openMember(t);
        double[] baseYaws = best.yaws.clone();
        double theta0 = best.theta;
        double dBest = 0.0;
        double violBest = best.viol;
        double radius = 0.16;
        for (int round = 0; round < 4; round++) {
            double center = dBest;
            boolean improved = false;
            for (int i = 0; i <= 16; i++) {
                if (cancel != null && cancel.get()) return null;
                double d = center - radius + 2.0 * radius * i / 16.0;
                double[] y2 = baseYaws.clone();
                for (int t = 0; t < n; t++) if (open[t]) y2[t] += d;
                double[] rs = recoverStart(exact, spec, y2, cfg);
                if (rs == null) continue;
                double v = violationAt(exact, spec, y2, rs[0], rs[1]);
                if (v < violBest) {
                    violBest = v;
                    dBest = d;
                    best.viol = v;
                    best.yaws = y2;
                    best.theta = theta0 + d;
                    improved = true;
                }
                if (v <= feasTol) {
                    if (SolverTrace.on()) {
                        SolverTrace.log("FREE", "joint theta micro-polish solved d=%.5f start=(%.4f,%.4f)",
                                d, rs[0], rs[1]);
                    }
                    return new Result(Angles.wrapAll(y2), rs[0], rs[1], true);
                }
            }
            if (!improved && round > 0) break;
            radius /= 8.0;
        }
        if (SolverTrace.on() && dBest != 0.0) {
            SolverTrace.log("FREE", "joint theta micro-polish best d=%.5f viol=%.3e", dBest, violBest);
        }
        return null;
    }

    private static double[] prefixArcThetas(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs refSc,
                                            StartBox box, FacingPrefold.ChainScan scan) {
        int n = refSc.numTicks;
        int lastOpen = -1;
        for (int t = 0; t < n; t++) if (scan.openMember(t)) lastOpen = t;
        if (lastOpen < 2) return null;
        for (int t = 0; t <= lastOpen; t++) if (!scan.openMember(t)) return null;

        double step = 0.1;
        java.util.List<double[]> arcs = new java.util.ArrayList<double[]>();
        boolean inArc = false;
        double arcStart = 0.0;
        double[] gf = new double[n];
        for (double f = -180.0; f < 180.0 + step; f += step) {
            boolean ok = false;
            if (f < 180.0) {
                for (int k = 0; k < n; k++) gf[k] = f;
                ForwardPath p = exact.forward(refSc, gf);
                double xLo = box.pxLo;
                double xHi = box.pxHi;
                double zLo = box.pzLo;
                double zHi = box.pzHi;
                ok = true;
                for (JumpConstraint c : spec.constraints) {
                    if (c.t2 != null || c.t1 > lastOpen) continue;
                    boolean isX = c.mode == JumpConstraint.Mode.X;
                    boolean isZ = c.mode == JumpConstraint.Mode.Z;
                    if (!isX && !isZ) continue;
                    double off = isX ? p.posX[c.t1] - refSc.startPos.x : p.posZ[c.t1] - refSc.startPos.z;
                    if (c.cmp != JumpConstraint.Cmp.LE) {
                        if (isX) xLo = Math.max(xLo, c.rhs - off); else zLo = Math.max(zLo, c.rhs - off);
                    }
                    if (c.cmp != JumpConstraint.Cmp.GE) {
                        if (isX) xHi = Math.min(xHi, c.rhs - off); else zHi = Math.min(zHi, c.rhs - off);
                    }
                    if (xLo > xHi || zLo > zHi) {
                        ok = false;
                        break;
                    }
                }
            }
            if (ok != inArc) {
                if (ok) {
                    arcStart = f;
                } else {
                    arcs.add(new double[] {arcStart - step, f});
                }
                inArc = ok;
            }
        }
        if (arcs.isEmpty()) {
            if (SolverTrace.on()) SolverTrace.log("FREE", "joint prefix arcs empty (chain<=%d)", lastOpen);
            return new double[0];
        }

        double total = 0.0;
        for (double[] arc : arcs) total += arc[1] - arc[0];
        double spacing = Math.max(4.0 / (lastOpen + 1), total / 140.0);
        java.util.List<Double> out = new java.util.ArrayList<Double>();
        for (double[] arc : arcs) {
            double width = arc[1] - arc[0];
            int samples = Math.max(3, (int) Math.ceil(width / spacing));
            for (int i = 0; i <= samples; i++) out.add(arc[0] + width * i / samples);
        }
        double[] thetas = new double[out.size()];
        for (int i = 0; i < thetas.length; i++) thetas[i] = out.get(i);
        if (SolverTrace.on()) {
            StringBuilder sb = new StringBuilder();
            for (double[] arc : arcs) sb.append(String.format(java.util.Locale.ROOT, "[%.2f,%.2f] ", arc[0], arc[1]));
            SolverTrace.log("FREE", "joint prefix arcs (chain<=%d): %ssamples=%d", lastOpen, sb, thetas.length);
        }
        return thetas;
    }

    private static void enqueuePattern(java.util.ArrayDeque<boolean[][]> queue, java.util.Set<String> seen,
                                       JumpPhysicsInputs refSc, ExactJumpModel exact, double[] yaws) {
        int n = refSc.numTicks;
        boolean[] nzx = new boolean[n];
        boolean[] nzz = new boolean[n];
        new JumpLinearModel(refSc).zeroingPattern(Angles.wrapAll(yaws.clone()),
                exact.inertiaThreshold(), exact.perAxisInertia(), nzx, nzz);
        offerPattern(queue, seen, refSc, nzx, nzz);
        for (int axis = 0; axis < 2; axis++) {
            boolean[] base = axis == 0 ? nzx : nzz;
            int last = -1;
            for (int t = 0; t < n; t++) if (base[t]) last = t;
            if (last < 0) continue;
            boolean[] shorter = base.clone();
            shorter[last] = false;
            offerPattern(queue, seen, refSc, axis == 0 ? shorter : nzx.clone(), axis == 0 ? nzz.clone() : shorter);
            if (last + 1 < n && !base[last + 1]) {
                boolean[] longer = base.clone();
                longer[last + 1] = true;
                offerPattern(queue, seen, refSc, axis == 0 ? longer : nzx.clone(), axis == 0 ? nzz.clone() : longer);
            }
        }
    }

    private static void offerPattern(java.util.ArrayDeque<boolean[][]> queue, java.util.Set<String> seen,
                                     JumpPhysicsInputs refSc, boolean[] zeroX, boolean[] zeroZ) {
        if (!patternEffective(refSc, zeroX, zeroZ)) return;
        if (!seen.add(SolverTrace.patternLabel(zeroX, zeroZ))) return;
        queue.add(new boolean[][] {zeroX, zeroZ});
    }

    private static boolean patternEffective(JumpPhysicsInputs sc, boolean[] zeroX, boolean[] zeroZ) {
        for (int t = 0; t < zeroX.length; t++) {
            if (zeroX[t] && (t > 0 || sc.initialVelocity.x != 0.0)) return true;
            if (zeroZ[t] && (t > 0 || sc.initialVelocity.z != 0.0)) return true;
        }
        return false;
    }

    private static Result jointLadder(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs base, StartBox box,
                                      StartBox cbox, JumpPhysicsInputs refSc, JumpLinearModel lin,
                                      double refX, double refZ, double feasTol, AtomicBoolean cancel, Config cfg,
                                      FacingPrefold pre, JointBest passBest) {
        double[] cx = new double[lin.n];
        double[] cz = new double[lin.n];
        lin.objectiveVectors(spec.objective, cx, cz);
        boolean[] trivial = {false};
        List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivial);
        if (trivial[0]) { lastJointDebug = "trivial-infeasible"; return null; }
        walls.addAll(lin.velocityWalls(exact.inertiaThreshold()));
        FacingPrefold.Reduced red = pre.reduce(cx, cz, lin.mMagAll(), walls);
        CostateDualSolver solver = new CostateDualSolver(red.n, red.cx, red.cz, red.mMag, red.walls,
                buildFreeP0(cbox, spec.objective));

        CostateDualSolver.Result probe = solver.solve(0.0, null);
        if (probe == null || solver.lastStalled) {
            lastJointDebug = probe == null ? "branch-infeasible@0" : "branch-stalled@0";
            return null;
        }
        double[] warm = probe.lambda;
        double lo = 0.0;
        double hi = cfg.jointMarginMax;
        CostateDualSolver.Result rHi = solver.solve(hi, warm);
        if (rHi != null && !solver.lastStalled) {
            lo = hi;
            warm = rHi.lambda;
        } else {
            for (int i = 0; i < cfg.jointBisectIters; i++) {
                if (cancel != null && cancel.get()) return null;
                double mid = 0.5 * (lo + hi);
                CostateDualSolver.Result rm = solver.solve(mid, warm);
                if (rm != null && !solver.lastStalled) {
                    lo = mid;
                    warm = rm.lambda;
                } else {
                    hi = mid;
                }
            }
        }
        double tStar = lo;
        if (SolverTrace.on()) SolverTrace.log("FREE", "joint maxMargin=%.5f", tStar);

        double bestViol = Double.POSITIVE_INFINITY;
        double[] bestYaws = null;
        double[] margins = new double[JOINT_RECOVERY_FRACTIONS.length + cfg.jointMargins.length];
        int marginCount = 0;
        for (double frac : JOINT_RECOVERY_FRACTIONS) margins[marginCount++] = tStar * frac;
        for (double m : cfg.jointMargins) if (m > 0.0 && m <= tStar) margins[marginCount++] = m;
        for (int mi = 0; mi < marginCount; mi++) {
            double margin = margins[mi];
            if (cancel != null && cancel.get()) return null;
            CostateDualSolver.Result r = solver.solve(margin, warm);
            if (r == null || solver.lastStalled) continue;
            warm = r.lambda;
            double[] yaws = pre.expand(lin, spec.objective, r);
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
            if (viol <= feasTol) {
                if (SolverTrace.on()) {
                    SolverTrace.log("FREE", "joint solved margin=%.2e start=(%.4f,%.4f)", margin, p0x, p0z);
                }
                return new Result(yaws, p0x, p0z, true);
            }
            if (viol <= 0.5) {
                double[] rs = recoverStart(exact, spec, yaws, cfg);
                if (rs != null) {
                    double rv = violationAt(exact, spec, yaws, rs[0], rs[1]);
                    if (rv <= feasTol) {
                        if (SolverTrace.on()) {
                            SolverTrace.log("FREE", "joint solved via recovered start (%.4f,%.4f)", rs[0], rs[1]);
                        }
                        return new Result(Angles.wrapAll(yaws.clone()), rs[0], rs[1], true);
                    }
                    viol = Math.min(viol, rv);
                }
            }
            if (viol < bestViol) {
                bestViol = viol;
                bestYaws = yaws;
            }
            if (passBest != null && viol < passBest.viol) {
                passBest.viol = viol;
                passBest.yaws = yaws;
            }
        }
        double recViol = -1.0;
        if (bestYaws != null) {
            double[] rs2 = recoverStart(exact, spec, bestYaws, cfg);
            if (rs2 != null) {
                recViol = violationAt(exact, spec, bestYaws, rs2[0], rs2[1]);
                double[] cfYaws = null;
                if (recViol <= 0.02) {
                    JumpSpec rsSpec = specAtStart(base, spec, rs2[0], rs2[1]);
                    cfYaws = ClosedFormSolve.optimize(exact, rsSpec, feasTol, cancel);
                }
                if (cfYaws != null) {
                    if (SolverTrace.on()) {
                        SolverTrace.log("FREE", "joint recovered start certified by pinned closed form (%.5f,%.5f)",
                                rs2[0], rs2[1]);
                    }
                    return new Result(cfYaws, rs2[0], rs2[1], true);
                }
            }
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
        double dx = pickBest(loX, hiX, box.pxLo - path.posX[0], box.pxHi - path.posX[0], objAxis == 0, max);
        double dz = pickBest(loZ, hiZ, box.pzLo - path.posZ[0], box.pzHi - path.posZ[0], objAxis == 1, max);
        return new double[] {dx, dz};
    }

    private static double pickBest(double lo, double hi, double bLo, double bHi, boolean objectiveAxis, boolean max) {
        double flo = Math.max(lo, bLo);
        double fhi = Math.min(hi, bHi);
        if (flo <= fhi) return 0.5 * (flo + fhi);
        if (bHi < lo) return bHi;
        if (bLo > hi) return bLo;
        return clamp(0.5 * (lo + hi), bLo, bHi);
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
        double[] d = bestTranslate(spec, gf, path, box, cfg.jointMargins.length > 0 ? cfg.jointMargins[0] : 0.0);
        double p0x = clamp(box.px + d[0], box.pxLo, box.pxHi);
        double p0z = clamp(box.pz + d[1], box.pzLo, box.pzHi);
        return new double[] {p0x, p0z};
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
        JumpLinearModel lin = new JumpLinearModel(sc);
        FacingPrefold pre = FacingPrefold.analyze(spec.constraints, lin);
        if (pre != null) {
            double[] cx = new double[lin.n];
            double[] cz = new double[lin.n];
            lin.objectiveVectors(spec.objective, cx, cz);
            boolean[] trivial = {false};
            List<JumpLinearModel.Wall> walls = lin.compileWalls(spec.constraints, 0.0, trivial);
            if (!trivial[0]) {
                FacingPrefold.Reduced red = pre.reduce(cx, cz, lin.mMagAll(), walls);
                CostateDualSolver.Result r = new CostateDualSolver(red.n, red.cx, red.cz, red.mMag, red.walls)
                        .solve(0.0, null);
                if (r != null) return pre.expand(lin, spec.objective, r);
            }
        } else {
            ClosedFormSolve.Result graded = ClosedFormSolve.optimizeRobustGraded(exact, spec, feasTol, cancel);
            if (graded != null) return graded.yaws;
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

    private static JumpSpec specAtStart(JumpPhysicsInputs base, JumpSpec spec, double p0x, double p0z) {
        return new JumpSpec(copyWithStart(base, p0x, p0z), spec.constraints, spec.objective);
    }

    static JumpPhysicsInputs copyWithStart(JumpPhysicsInputs b, double p0x, double p0z) {
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
        a.surfacePerTick = b.surfacePerTick;
        a.soulsandCellsPerTick = b.soulsandCellsPerTick;
        a.sneakPerTick = b.sneakPerTick;
        a.yawLockedPerTick = b.yawLockedPerTick;
        a.sprintPerTick = b.sprintPerTick;
        a.incomingSprint = b.incomingSprint;
        a.incomingAmp = b.incomingAmp;
        a.liveAirSprintFactor = b.liveAirSprintFactor;
        a.forwardInputPerTick = b.forwardInputPerTick;
        a.strafeInputPerTick = b.strafeInputPerTick;
        return a;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
