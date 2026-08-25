package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockSolveProbe {

    private static final double RAD = Math.PI / 180.0;
    private static final double FLOOR = 0.01;
    private static java.io.PrintWriter OUT;

    @Test
    public void probe() throws Exception {
        String path = System.getenv("PKC_BLOCK_FILE");
        Assume.assumeTrue("set PKC_BLOCK_FILE", path != null && !path.isEmpty());
        java.io.StringWriter sw = new java.io.StringWriter();
        OUT = new java.io.PrintWriter(sw);
        try {
            run(path);
        } finally {
            OUT.flush();
            File dst = new File("build/reports/block-solve.txt");
            dst.getParentFile().mkdirs();
            Files.write(dst.toPath(), sw.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println(sw);
        }
    }

    private void run(String path) throws Exception {
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
        ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);
        Method build = HpkDualRecoveryScreen.class.getDeclaredMethod("buildSpec", SaveFile.class, ExactJumpModel.class);
        build.setAccessible(true);
        JumpSpec spec = (JumpSpec) build.invoke(null, file, exact);
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(spec);
        int n = sc.numTicks;
        double anchor = sc.startYaw;
        AtomicBoolean cancel = new AtomicBoolean(false);

        TreeSet<Integer> bounds = new TreeSet<Integer>();
        for (JumpConstraint c : spec.constraints) {
            if (c.mode != JumpConstraint.Mode.X && c.mode != JumpConstraint.Mode.Z) continue;
            if (c.t1 > 0 && c.t1 <= n) bounds.add(c.t1 - 1);
            if (c.t2 != null && c.t2 > 0 && c.t2 <= n) bounds.add(c.t2 - 1);
        }

        double[] seed = ClosedFormSolve.optimize(exact, spec, 0.0, cancel);
        if (seed == null) seed = SlpSolve.optimize(exact, spec, 0.0, cancel);
        if (seed == null && file.angleSolver != null && file.angleSolver.result != null
                && file.angleSolver.result.yaws != null && !file.angleSolver.result.yaws.isEmpty()) {
            java.util.Map<Integer, Double> ym = new java.util.HashMap<Integer, Double>();
            for (SaveFile.Yaw yy : file.angleSolver.result.yaws) ym.put(yy.tick, yy.yaw);
            int startTick = file.angleSolver.startTick;
            double[] rec = new double[n];
            boolean full = true;
            for (int k = 0; k < n; k++) {
                Double v = ym.get(startTick + 1 + k);
                if (v == null) { full = false; v = (double) sc.startYaw; }
                rec[k] = v;
            }
            if (full) { seed = rec; OUT.println("(seed = the save's recorded solve result)"); }
        }
        OUT.printf("mopus block probe: n=%d constraints=%d boundaryTicks=%d objTick=%d%n",
                n, spec.constraints.size(), bounds.size(), spec.objective.tick);
        OUT.println("boundary ticks (heading may change here): " + bounds);
        if (seed == null) {
            OUT.println("no feasible seed; abort");
            return;
        }
        seed = Angles.wrapAll(seed);
        report("raw solve (seed)", exact, sc, spec, comp, anchor, seed);

        int[] segByBounds = segments(n, bounds);
        double[] byBounds = blockSolve(exact, sc, spec, comp, seed, segByBounds, count(segByBounds), cancel);
        report("constant between constraints", exact, sc, spec, comp, anchor, byBounds);

        for (int k : new int[]{5, 6, 8}) {
            int[] segFixed = fixedBlocks(n, k);
            double[] fixed = blockSolve(exact, sc, spec, comp, seed, segFixed, count(segFixed), cancel);
            report("fixed " + k + "-tick blocks", exact, sc, spec, comp, anchor, fixed);
        }

        OUT.println("\n=== per-tick yaw: raw vs constant-between-constraints ===");
        double[] gRaw = sc.toGameFacings(seed);
        double[] gBlk = sc.toGameFacings(byBounds);
        for (int t = 0; t < n; t++) {
            String mark = bounds.contains(t) ? " <boundary>" : "";
            OUT.printf("t=%2d  raw=%9.3f  block=%9.3f%s%n", t, gRaw[t], gBlk[t], mark);
        }
    }

    private static int[] segments(int n, TreeSet<Integer> bounds) {
        int[] seg = new int[n];
        int s = 0;
        for (int t = 0; t < n; t++) {
            seg[t] = s;
            if (bounds.contains(t)) s++;
        }
        return seg;
    }

    private static int[] fixedBlocks(int n, int k) {
        int[] seg = new int[n];
        for (int t = 0; t < n; t++) seg[t] = t / k;
        return seg;
    }

    private static int count(int[] seg) {
        int m = 0;
        for (int s : seg) m = Math.max(m, s + 1);
        return m;
    }

    /** Reparametrize to one heading per segment, seed each segment from the raw solve's first tick in it,
     *  then damped Gauss-Newton over the segment headings until the byte-exact model is feasible. */
    private static double[] blockSolve(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec,
                                       JumpConstraintCompiler.Compiled comp, double[] seed, int[] segOf, int nSeg,
                                       AtomicBoolean cancel) {
        int n = sc.numTicks;
        double[] head = new double[nSeg];
        boolean[] set = new boolean[nSeg];
        for (int t = 0; t < n; t++) {
            if (!set[segOf[t]]) { head[segOf[t]] = seed[t]; set[segOf[t]] = true; }
        }
        double[] y = new double[n];
        for (int t = 0; t < n; t++) y[t] = head[segOf[t]];

        boolean[] zx = new boolean[n];
        boolean[] zz = new boolean[n];
        double[] addX = new double[n];
        double[] addZ = new double[n];
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>(comp.ineq);
        cons.addAll(comp.eq);

        for (int iter = 0; iter < 200; iter++) {
            if (cancel.get()) break;
            double[] gf = sc.toGameFacings(y);
            ForwardPath path = exact.forward(sc, gf);
            double viol = comp.maxViolation(gf, path);
            if (viol <= 0.0) return y;

            new JumpLinearModel(sc).zeroingPattern(y, exact.inertiaThreshold(), exact.perAxisInertia(), zx, zz);
            JumpLinearModel lin = new JumpLinearModel(sc, zx, zz);
            for (int t = 0; t < n; t++) {
                double phi = lin.baseArg(t) + gf[t] * RAD;
                addX[t] = lin.mMag(t) * Math.cos(phi);
                addZ[t] = lin.mMag(t) * Math.sin(phi);
            }

            List<double[]> J = new ArrayList<double[]>();
            List<Double> res = new ArrayList<Double>();
            for (JumpConstraint c : cons) {
                if (c.mode != JumpConstraint.Mode.X && c.mode != JumpConstraint.Mode.Z) continue;
                double e = JumpConstraintCompiler.evaluate(c, gf, path);
                double sign, r;
                boolean eq = c.cmp == JumpConstraint.Cmp.EQ;
                if (c.cmp == JumpConstraint.Cmp.GE) { sign = -1.0; r = -e; }
                else { sign = 1.0; r = e; }
                if (!eq && r <= 0.0) continue;
                int axis = c.mode == JumpConstraint.Mode.X ? 0 : 1;
                double opSign = c.op == JumpConstraint.Op.PLUS ? 1.0 : -1.0;
                double[] js = new double[nSeg];
                for (int t = 0; t < n; t++) {
                    double k = lin.coefAxis(axis, t, c.t1);
                    if (c.t2 != null) k += opSign * lin.coefAxis(axis, t, c.t2);
                    if (k == 0.0) continue;
                    double jt = sign * k * (axis == 0 ? -addZ[t] : addX[t]) * RAD;
                    js[segOf[t]] += jt;
                }
                J.add(js);
                res.add(r);
            }
            if (J.isEmpty()) return y;

            double[] d = dampedLeastSquares(J, res, nSeg);
            if (d == null) return y;
            double dInf = 0.0;
            for (double v : d) dInf = Math.max(dInf, Math.abs(v));
            if (dInf == 0.0) return y;
            double scale = dInf > 0.2 ? 0.2 / dInf : 1.0;

            boolean moved = false;
            for (double alpha = scale; alpha >= scale / 64.0; alpha *= 0.5) {
                double[] c2 = new double[n];
                for (int t = 0; t < n; t++) c2[t] = y[t] + alpha * d[segOf[t]] * (180.0 / Math.PI);
                double[] cg = sc.toGameFacings(c2);
                double cv = comp.maxViolation(cg, exact.forward(sc, cg));
                if (cv < viol) { System.arraycopy(c2, 0, y, 0, n); moved = true; break; }
            }
            if (!moved) break;
        }
        return y;
    }

    private static double[] dampedLeastSquares(List<double[]> J, List<Double> res, int nSeg) {
        int m = J.size();
        double[][] jtj = new double[nSeg][nSeg];
        double[] jtr = new double[nSeg];
        for (int i = 0; i < m; i++) {
            double[] ji = J.get(i);
            double ri = -res.get(i);
            for (int a = 0; a < nSeg; a++) {
                jtr[a] += ji[a] * ri;
                for (int b = 0; b < nSeg; b++) jtj[a][b] += ji[a] * ji[b];
            }
        }
        double maxDiag = 0.0;
        for (int a = 0; a < nSeg; a++) maxDiag = Math.max(maxDiag, jtj[a][a]);
        if (maxDiag <= 0.0) return null;
        for (int a = 0; a < nSeg; a++) jtj[a][a] += 1.0e-9 * maxDiag + 1.0e-18;
        return solve(jtj, jtr);
    }

    private static double[] solve(double[][] a, double[] b) {
        int n = b.length;
        double[][] w = new double[n][n + 1];
        for (int i = 0; i < n; i++) { System.arraycopy(a[i], 0, w[i], 0, n); w[i][n] = b[i]; }
        for (int c = 0; c < n; c++) {
            int piv = c;
            for (int r = c + 1; r < n; r++) if (Math.abs(w[r][c]) > Math.abs(w[piv][c])) piv = r;
            double[] tmp = w[c]; w[c] = w[piv]; w[piv] = tmp;
            if (Math.abs(w[c][c]) < 1.0e-18) continue;
            for (int r = 0; r < n; r++) {
                if (r == c) continue;
                double f = w[r][c] / w[c][c];
                for (int k = c; k <= n; k++) w[r][k] -= f * w[c][k];
            }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = Math.abs(w[i][i]) < 1.0e-18 ? 0.0 : w[i][n] / w[i][i];
        return x;
    }

    private static void report(String label, ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec,
                               JumpConstraintCompiler.Compiled comp, double anchor, double[] yaws) {
        double[] gf = sc.toGameFacings(yaws);
        ForwardPath path = exact.forward(sc, gf);
        double viol = comp.maxViolation(gf, path);
        double obj = path.getPos(spec.objective.tick, spec.objective.axis);
        int rev = reversals(anchor, yaws);
        int changes = headingChanges(anchor, yaws);
        OUT.printf("%-32s feasible=%-5s viol=%9.2e obj(X)=%.4f reversals=%d headingChanges=%d%n",
                label, viol <= 0.0, viol, obj, rev, changes);
    }

    private static int reversals(double anchor, double[] y) {
        int c = 0, last = 0;
        double prev = anchor;
        for (double v : y) {
            double d = Angles.wrapDelta(v - prev);
            prev = v;
            if (Math.abs(d) <= FLOOR) continue;
            int s = d > 0 ? 1 : -1;
            if (last != 0 && s != last) c++;
            last = s;
        }
        return c;
    }

    private static int headingChanges(double anchor, double[] y) {
        int c = 0;
        double prev = anchor;
        for (double v : y) {
            if (Math.abs(Angles.wrapDelta(v - prev)) > FLOOR) c++;
            prev = v;
        }
        return c;
    }
}
