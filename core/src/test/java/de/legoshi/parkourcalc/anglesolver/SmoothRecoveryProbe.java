package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The dual gives a tight bound and a correct active set but no directions, because at the optimum
 * every direction maximises the Lagrangian equally. So recover the primal directly: satisfy the
 * linear walls while minimising sign changes in the per-tick yaw deltas, then check byte-exact.
 */
public class SmoothRecoveryProbe {

    static final double RAD = Math.PI / 180.0;

    static final class Lin {
        JumpLinearModel lin;
        List<JumpLinearModel.Wall> walls;
        List<JumpConstraint> cons;
        int n;
    }

    static Lin build(JumpPhysicsInputs sc, JumpSpec spec) {
        return build(sc, spec, null, null);
    }

    static Lin build(JumpPhysicsInputs sc, JumpSpec spec, boolean[] zx, boolean[] zz) {
        Lin L = new Lin();
        L.lin = zx == null ? new JumpLinearModel(sc) : new JumpLinearModel(sc, zx, zz);
        L.n = sc.numTicks;
        L.walls = new ArrayList<JumpLinearModel.Wall>();
        L.cons = new ArrayList<JumpConstraint>();
        for (JumpConstraint c : spec.constraints) {
            if (c.mode == JumpConstraint.Mode.F) continue;
            JumpLinearModel.Wall w = L.lin.compileWall(c, 0.0, null);
            if (w != null) {
                L.walls.add(w);
                L.cons.add(c);
            }
        }
        return L;
    }

    /** Wall value A_j.u at the given absolute yaws, and its gradient in yaw degrees. */
    static double wallValue(Lin L, int j, double[] gf) {
        JumpLinearModel.Wall w = L.walls.get(j);
        double s = 0.0;
        for (int t = 0; t < L.n; t++) {
            if (w.coef[t] == 0.0) continue;
            double phi = L.lin.baseArg(t) + gf[t] * RAD;
            double u = w.axis == 0 ? Math.cos(phi) : Math.sin(phi);
            s += w.coef[t] * L.lin.mMag(t) * u;
        }
        return s;
    }

    static void wallGrad(Lin L, int j, double[] gf, double[] out) {
        JumpLinearModel.Wall w = L.walls.get(j);
        for (int t = 0; t < L.n; t++) {
            if (w.coef[t] == 0.0) {
                out[t] = 0.0;
                continue;
            }
            double phi = L.lin.baseArg(t) + gf[t] * RAD;
            double d = w.axis == 0 ? -Math.sin(phi) : Math.cos(phi);
            out[t] = w.coef[t] * L.lin.mMag(t) * d * RAD;
        }
    }

    /** Linear-model violation: max over walls of (A_j.u - b'_j). */
    static double linViol(Lin L, double[] gf) {
        double worst = Double.NEGATIVE_INFINITY;
        for (int j = 0; j < L.walls.size(); j++) {
            worst = Math.max(worst, wallValue(L, j, gf) - L.walls.get(j).bPrime);
        }
        return worst;
    }

    static double[] deltas(double anchor, double[] y) {
        double[] d = new double[y.length];
        double prev = anchor;
        for (int i = 0; i < y.length; i++) {
            d[i] = Angles.wrapDelta(y[i] - prev);
            prev = y[i];
        }
        return d;
    }

    static final double FLOOR = 0.01;

    static int reversals(double anchor, double[] y) {
        int c = 0;
        int last = 0;
        for (double v : deltas(anchor, y)) {
            if (Math.abs(v) <= FLOOR) continue;
            int s = v > 0 ? 1 : -1;
            if (last != 0 && s != last) c++;
            last = s;
        }
        return c;
    }

    static int runsOf(double anchor, double[] y) {
        int runs = 0;
        int last = 0;
        for (double v : deltas(anchor, y)) {
            if (Math.abs(v) <= FLOOR) continue;
            int s = v > 0 ? 1 : -1;
            if (s != last) runs++;
            last = s;
        }
        return runs;
    }

    /** Sum of squared second differences of the yaw deltas: the smooth direction to descend. */
    static double[] smoothGrad(double anchor, double[] y) {
        int n = y.length;
        double[] u = new double[n];
        double prev = anchor;
        for (int i = 0; i < n; i++) {
            u[i] = (i == 0 ? anchor : u[i - 1]) + Angles.wrapDelta(y[i] - prev);
            prev = y[i];
        }
        double[] a = new double[n];
        for (int i = 1; i < n; i++) {
            a[i] = i == 1 ? u[1] - 2.0 * u[0] + anchor : u[i] - 2.0 * u[i - 1] + u[i - 2];
        }
        double[] g = new double[n];
        for (int k = 0; k < n; k++) {
            double s = 0.0;
            if (k >= 1) s += a[k];
            if (k + 1 <= n - 1) s += -2.0 * a[k + 1];
            if (k + 2 <= n - 1) s += a[k + 2];
            g[k] = 2.0 * s;
        }
        return g;
    }

    static double[] solveSym(double[][] a, double[] b, double reg) {
        int m = b.length;
        double[][] w = new double[m][m + 1];
        for (int i = 0; i < m; i++) {
            System.arraycopy(a[i], 0, w[i], 0, m);
            w[i][i] += reg;
            w[i][m] = b[i];
        }
        for (int c = 0; c < m; c++) {
            int piv = c;
            for (int r = c + 1; r < m; r++) if (Math.abs(w[r][c]) > Math.abs(w[piv][c])) piv = r;
            double[] tmp = w[c];
            w[c] = w[piv];
            w[piv] = tmp;
            if (Math.abs(w[c][c]) < 1.0e-16) continue;
            for (int r = 0; r < m; r++) {
                if (r == c) continue;
                double f = w[r][c] / w[c][c];
                if (f == 0.0) continue;
                for (int k = c; k <= m; k++) w[r][k] -= f * w[c][k];
            }
        }
        double[] x = new double[m];
        for (int i = 0; i < m; i++) x[i] = Math.abs(w[i][i]) < 1.0e-16 ? 0.0 : w[i][m] / w[i][i];
        return x;
    }

    /** One Gauss-Newton pass pushing every wall to at most bPrime - margin, in the linear model. */
    static boolean restore(Lin L, double[] gf, double margin, boolean trace) {
        int n = L.n;
        for (int it = 0; it < 200; it++) {
            List<Integer> bad = new ArrayList<Integer>();
            for (int j = 0; j < L.walls.size(); j++) {
                if (wallValue(L, j, gf) - L.walls.get(j).bPrime > -margin) bad.add(j);
            }
            if (bad.isEmpty()) return true;
            int m = bad.size();
            double[][] J = new double[m][n];
            double[] r = new double[m];
            for (int i = 0; i < m; i++) {
                int j = bad.get(i);
                wallGrad(L, j, gf, J[i]);
                r[i] = (L.walls.get(j).bPrime - margin) - wallValue(L, j, gf);
            }
            double[][] W = smoothMetric(n);
            double[][] JW = new double[m][n];
            for (int a = 0; a < m; a++) {
                for (int x = 0; x < n; x++) {
                    double s = 0.0;
                    for (int z = 0; z < n; z++) s += J[a][z] * W[z][x];
                    JW[a][x] = s;
                }
            }
            double[][] jjt = new double[m][m];
            for (int a = 0; a < m; a++) {
                for (int b = 0; b < m; b++) {
                    double s = 0.0;
                    for (int x = 0; x < n; x++) s += JW[a][x] * J[b][x];
                    jjt[a][b] = s;
                }
            }
            double scale = 0.0;
            for (int a = 0; a < m; a++) scale = Math.max(scale, Math.abs(jjt[a][a]));
            double[] lam = solveSym(jjt, r, scale * 1.0e-10 + 1.0e-20);
            double best = linViol(L, gf);
            double[] cur = gf.clone();
            boolean moved = false;
            for (double damp = 1.0; damp >= 1.0 / 512.0; damp *= 0.5) {
                double[] c = cur.clone();
                for (int a = 0; a < m; a++) {
                    if (lam[a] == 0.0) continue;
                    for (int t = 0; t < n; t++) c[t] += damp * lam[a] * JW[a][t];
                }
                double v = linViol(L, c);
                if (v < best - 1.0e-15) {
                    System.arraycopy(c, 0, gf, 0, n);
                    best = v;
                    moved = true;
                    break;
                }
            }
            if (!moved) {
                if (trace) System.out.printf("SR     restore stalled at linViol=%.4e after %d its%n", best, it);
                return linViol(L, gf) <= -margin * 0.5;
            }
        }
        return linViol(L, gf) <= 0.0;
    }

    @Test
    public void probe() throws Exception {
        String path = System.getenv("PKC_SR_FILE");
        Assume.assumeTrue("set PKC_SR_FILE", path != null && !path.isEmpty());
        SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(new File(path).toPath()),
                StandardCharsets.UTF_8));
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        double anchor = sc.startYaw;
        Lin L = build(sc, spec);
        JumpConstraintCompiler.Compiled comp = JumpConstraintCompiler.compile(spec);
        AtomicBoolean cancel = new AtomicBoolean(false);

        System.out.printf("SR %s n=%d walls=%d maxiter=%s%n", new File(path).getName(), n, L.walls.size(),
                System.getProperty("pkc.dual.maxiter", "100"));

        ClosedFormSolve.Result dr = ClosedFormSolve.optimizeRobustGraded(model, spec, 0.0, cancel);
        double[] seed;
        if (dr != null && dr.yaws != null) {
            seed = Angles.wrapAll(dr.yaws);
            System.out.printf("SR dual seed: linViol=%.4e exactViol=%.4e rev=%d runs=%d%n",
                    linViol(L, sc.toGameFacings(seed)),
                    comp.maxViolation(sc.toGameFacings(seed), model.forward(sc, sc.toGameFacings(seed))),
                    reversals(anchor, seed), runsOf(anchor, seed));
        } else {
            seed = new double[n];
            System.out.println("SR dual seed: null, starting flat");
        }

        double margin = Double.parseDouble(System.getProperty("pkc.sr.margin", "5e-3"));
        int rounds = Integer.getInteger("pkc.sr.rounds", 600);
        double step = Double.parseDouble(System.getProperty("pkc.sr.step", "6"));
        int patternPasses = Integer.getInteger("pkc.sr.passes", 6);

        double[] gf = sc.toGameFacings(seed);
        double[] bestGf = gf.clone();
        double bestExact = comp.maxViolation(gf, model.forward(sc, gf));
        int bestRev = reversals(anchor, fromGf(sc, gf, seed));
        System.out.printf("SR seed exactViol=%.4e rev=%d runs=%d%n", bestExact, bestRev,
                runsOf(anchor, fromGf(sc, gf, seed)));

        // Phase 0: the Gauss-Newton restore is local, so a seed far from any feasible point stalls in
        // a basin. Multi-start it: the dual answer first, then the saved solve if the file has one,
        // then coherent random perturbations. Cheap, and it is the difference between reaching
        // feasibility at all on the harder windows.
        int starts = Integer.getInteger("pkc.sr.starts", 24);
        if (bestExact > 0.0 && starts > 0) {
            java.util.Random rng = new java.util.Random(20260823L);
            List<double[]> seeds = new ArrayList<double[]>();
            seeds.add(seed.clone());
            if (!"0".equals(System.getProperty("pkc.sr.usesaved", "1")) && file.angleSolver.result != null && !file.angleSolver.result.yaws.isEmpty()) {
                java.util.Map<Integer, Double> ym = new java.util.HashMap<Integer, Double>();
                for (SaveFile.Yaw yy : file.angleSolver.result.yaws) ym.put(yy.tick, yy.yaw);
                double[] sv = new double[n];
                boolean full = true;
                for (int k = 0; k < n; k++) {
                    Double v = ym.get(state.getStartTick() + k + 1);
                    if (v == null) full = false;
                    sv[k] = v == null ? 0.0 : v;
                }
                if (full) seeds.add(sv);
            }
            int base = seeds.size();
            for (int k = base; k < starts; k++) {
                double[] c = seeds.get(rng.nextInt(base)).clone();
                int nk = 1 + rng.nextInt(3);
                for (int q = 0; q < nk; q++) {
                    int i = rng.nextInt(n);
                    int len = 1 + rng.nextInt(n - i);
                    double mag = (rng.nextDouble() * 2.0 - 1.0) * 60.0;
                    boolean ramp = rng.nextBoolean();
                    for (int t = i; t < i + len; t++) {
                        c[t] = Angles.wrap(c[t] + (ramp ? mag * (t - i + 1.0) / len : mag));
                    }
                }
                seeds.add(c);
            }
            int feasFound = 0;
            for (int si = 0; si < seeds.size(); si++) {
                double[] cand = sc.toGameFacings(Angles.wrapAll(seeds.get(si)));
                for (int pass = 0; pass < 3; pass++) {
                    double[] yy = fromGf(sc, cand, seed);
                    boolean[] zx = new boolean[n];
                    boolean[] zz = new boolean[n];
                    new JumpLinearModel(sc).zeroingPattern(Angles.wrapAll(yy), model.inertiaThreshold(),
                            model.perAxisInertia(), zx, zz);
                    Lin Ls = build(sc, spec, zx, zz);
                    restore(Ls, cand, margin, false);
                    if (comp.maxViolation(cand, model.forward(sc, cand)) <= 0.0) break;
                }
                double ev = comp.maxViolation(cand, model.forward(sc, cand));
                int rv = reversals(anchor, fromGf(sc, cand, seed));
                if (ev <= 0.0) feasFound++;
                boolean better;
                if (ev <= 0.0 && bestExact > 0.0) better = true;
                else if (ev <= 0.0) better = rv < bestRev;
                else better = ev < bestExact && rv <= bestRev;
                if (better) {
                    bestExact = ev;
                    bestRev = rv;
                    bestGf = cand.clone();
                }
            }
            System.out.printf("SR multistart seeds=%d reachedFeasible=%d best exactViol=%.4e rev=%d%n",
                    seeds.size(), feasFound, bestExact, bestRev);
            gf = bestGf.clone();
        }

        // Phase 1: reach byte-exact feasibility. The incumbent is floored on BOTH axes so a pass can
        // never hand back something worse than the seed: feasibility outranks shape, but between two
        // infeasible candidates a violation gain that costs reversals is refused, since an infeasible
        // result goes to the downstream repair anyway and only its shape survives being a seed.
        for (int pass = 0; pass < patternPasses && bestExact > 0.0; pass++) {
            double[] yNow = fromGf(sc, gf, seed);
            boolean[] zx = new boolean[n];
            boolean[] zz = new boolean[n];
            new JumpLinearModel(sc).zeroingPattern(Angles.wrapAll(yNow), model.inertiaThreshold(),
                    model.perAxisInertia(), zx, zz);
            L = build(sc, spec, zx, zz);
            restore(L, gf, margin, false);
            double ev = comp.maxViolation(gf, model.forward(sc, gf));
            int rv = reversals(anchor, fromGf(sc, gf, seed));
            boolean better;
            if (ev <= 0.0 && bestExact > 0.0) better = true;
            else if (ev <= 0.0) better = rv < bestRev;
            else better = ev < bestExact && rv <= bestRev;
            System.out.printf("SR pass=%d linViol=%11.4e exactViol=%11.4e rev=%d %s%n",
                    pass, linViol(L, gf), ev, rv, better ? "TAKE" : "refused");
            if (better) {
                bestExact = ev;
                bestRev = rv;
                bestGf = gf.clone();
            }
        }
        gf = bestGf.clone();
        if (bestExact > 0.0) {
            System.out.printf("SR could not reach byte-exact feasibility, best=%.4e%n", bestExact);
        }

        // Phase 2: descend turn cost while holding byte-exact feasibility.
        int acc = 0;
        int rejFeas = 0;
        int rejCost = 0;
        if (bestExact <= 0.0) {
            double[] y0 = fromGf(sc, gf, seed);
            double cost = turnCost(anchor, y0);
            System.out.printf("SR descent start turnCost=%.2f rev=%d runs=%d%n",
                    cost, reversals(anchor, y0), runsOf(anchor, y0));
            double curStep = step;
            for (int it = 0; it < rounds; it++) {
                double[] y = fromGf(sc, gf, seed);
                boolean[] zx = new boolean[n];
                boolean[] zz = new boolean[n];
                new JumpLinearModel(sc).zeroingPattern(Angles.wrapAll(y), model.inertiaThreshold(),
                        model.perAxisInertia(), zx, zz);
                L = build(sc, spec, zx, zz);

                double[] g = smoothGrad(anchor, y);
                List<Integer> act = new ArrayList<Integer>();
                for (int j = 0; j < L.walls.size(); j++) {
                    if (wallValue(L, j, gf) - L.walls.get(j).bPrime > -margin * 6.0) act.add(j);
                }
                double[] d = g.clone();
                if (!act.isEmpty()) {
                    int m = act.size();
                    double[][] J = new double[m][n];
                    for (int i = 0; i < m; i++) wallGrad(L, act.get(i), gf, J[i]);
                    double[][] jjt = new double[m][m];
                    for (int a = 0; a < m; a++) {
                        for (int b = 0; b < m; b++) {
                            double sm = 0.0;
                            for (int t = 0; t < n; t++) sm += J[a][t] * J[b][t];
                            jjt[a][b] = sm;
                        }
                    }
                    double[] jg = new double[m];
                    for (int a = 0; a < m; a++) {
                        double sm = 0.0;
                        for (int t = 0; t < n; t++) sm += J[a][t] * g[t];
                        jg[a] = sm;
                    }
                    double scale = 0.0;
                    for (int a = 0; a < m; a++) scale = Math.max(scale, Math.abs(jjt[a][a]));
                    double[] lam = solveSym(jjt, jg, scale * 1.0e-10 + 1.0e-20);
                    for (int a = 0; a < m; a++) {
                        if (lam[a] == 0.0) continue;
                        for (int t = 0; t < n; t++) d[t] -= lam[a] * J[a][t];
                    }
                }
                double gn = 0.0;
                for (double v : d) gn = Math.max(gn, Math.abs(v));
                if (gn < 1.0e-12) break;

                boolean moved = false;
                for (double a = curStep; a >= 1.0e-3; a *= 0.5) {
                    double[] c = gf.clone();
                    double k = a / gn;
                    for (int t = 0; t < n; t++) c[t] -= k * d[t];
                    if (!restoreExact(L, model, sc, comp, c, margin)) {
                        rejFeas++;
                        continue;
                    }
                    double cc = turnCost(anchor, fromGf(sc, c, seed));
                    if (cc >= cost - 1.0e-12) {
                        rejCost++;
                        continue;
                    }
                    gf = c;
                    cost = cc;
                    moved = true;
                    acc++;
                    if (a > curStep * 0.6) curStep = Math.min(curStep * 1.4, 40.0);
                    break;
                }
                if (!moved) {
                    curStep *= 0.6;
                    if (curStep < 1.0e-3) break;
                }
            }
            bestGf = gf.clone();
        }
        gf = bestGf;
        System.out.printf("SR descent accepted=%d rejectedInfeasible=%d rejectedNotSmoother=%d%n",
                acc, rejFeas, rejCost);

        // Phase 3: walk toward the flattened shape along the feasible tangent space. Jumping to the
        // flattened path and repairing afterwards makes the two steps fight: the flatten breaks
        // feasibility by more than a least-norm correction can absorb without re-adding reversals.
        // Projecting the flatten direction onto the null space of the active walls keeps the step
        // feasible to first order, so the repair only has second-order drift to remove.
        if (comp.maxViolation(gf, model.forward(sc, gf)) <= 0.0) {
            int killed = 0;
            int tries = 0;
            java.util.Set<String> stuck = new java.util.HashSet<String>();
            java.util.Map<String, Integer> touched = new java.util.HashMap<String, Integer>();
            while (true) {
                double[] y = fromGf(sc, gf, seed);
                int curRev = reversals(anchor, y);
                double curJerk = jerkOf(anchor, y);
                double[] dd = deltas(anchor, y);
                List<int[]> rs = runSpans(anchor, y);
                int[] pick = null;
                double pickMass = 0.0;
                for (int[] r : rs) {
                    double mass = 0.0;
                    for (int t = r[0]; t <= r[1]; t++) mass += dd[t];
                    if (stuck.contains(r[0] + ":" + r[1])) continue;
                    if (pick == null || Math.abs(mass) < Math.abs(pickMass)) {
                        pick = r;
                        pickMass = mass;
                    }
                }
                if (pick == null) break;
                String key = pick[0] + ":" + pick[1];
                Integer seenN = touched.get(key);
                int nSeen = seenN == null ? 0 : seenN;
                if (nSeen >= 3) {
                    stuck.add(key);
                    continue;
                }
                touched.put(key, nSeen + 1);

                boolean[] zx = new boolean[n];
                boolean[] zz = new boolean[n];
                new JumpLinearModel(sc).zeroingPattern(Angles.wrapAll(y), model.inertiaThreshold(),
                        model.perAxisInertia(), zx, zz);
                L = build(sc, spec, zx, zz);
                double[][] P = tangentProjector(L, gf, margin, n);

                boolean done = false;
                for (int grow = 0; grow <= 16 && !done; grow++) {
                    int lo = Math.max(0, pick[0] - grow);
                    int hi = Math.min(n - 1, pick[1] + grow);
                    double[] target = flattenYaw(anchor, y, lo, hi);
                    double[] tgf = sc.toGameFacings(Angles.wrapAll(target));
                    double[] dir = new double[n];
                    for (int t = 0; t < n; t++) dir[t] = Angles.wrapDelta(tgf[t] - gf[t]);
                    double[] pdir = apply(P, dir);
                    double pn = 0.0;
                    for (double v : pdir) pn = Math.max(pn, Math.abs(v));
                    if (pn < 1.0e-12) continue;

                    for (double frac = 1.0; frac >= 1.0 / 64.0 && !done; frac *= 0.5) {
                        double[] c = gf.clone();
                        for (int t = 0; t < n; t++) c[t] += frac * pdir[t];
                        tries++;
                        if (!restoreExact(L, model, sc, comp, c, margin)) continue;
                        double[] cy = fromGf(sc, c, seed);
                        int rv = reversals(anchor, cy);
                        double jk = jerkOf(anchor, cy);
                        if (rv > curRev) continue;
                        if (rv == curRev && jk >= curJerk - 1.0e-9) continue;
                        gf = c;
                        done = true;
                        if (rv < curRev) {
                            killed++;
                            touched.clear();
                        }
                        System.out.printf("SR   step run T%d..T%d mass=%+.2f span=%d frac=%.3f -> rev=%d jerk=%.1f%n",
                                pick[0], pick[1], pickMass, grow, frac, rv, jk);
                    }
                }
                if (!done) stuck.add(pick[0] + ":" + pick[1]);
            }
            System.out.printf("SR face-walk reversalsRemoved=%d tries=%d%n", killed, tries);
        }

        double[] fy = fromGf(sc, gf, seed);
        double ev = comp.maxViolation(gf, model.forward(sc, gf));
        System.out.printf("SR FINAL linViol=%.4e exactViol=%.4e rev=%d runs=%d %s%n",
                linViol(L, gf), ev, reversals(anchor, fy), runsOf(anchor, fy),
                ev <= 0.0 ? "EXACT-FEASIBLE" : "");
        StringBuilder sb = new StringBuilder("SR runs ");
        double prev = anchor;
        double mass = 0.0;
        int last = 0;
        for (double v : fy) {
            double dd = Angles.wrapDelta(v - prev);
            prev = v;
            if (Math.abs(dd) <= FLOOR) continue;
            int s = dd > 0 ? 1 : -1;
            if (s != last && last != 0) {
                sb.append(String.format("%+.1f ", mass));
                mass = 0.0;
            }
            last = s;
            mass += dd;
        }
        if (last != 0) sb.append(String.format("%+.1f", mass));
        System.out.println(sb);
    }

    /** Gauss-Newton on the linear walls, but stop the moment the BYTE-EXACT model is satisfied. */
    static boolean restoreExact(Lin L, ExactJumpModel model, JumpPhysicsInputs sc,
                                JumpConstraintCompiler.Compiled comp, double[] gf, double margin) {
        if (comp.maxViolation(gf, model.forward(sc, gf)) <= 0.0) return true;
        for (double m = margin; m <= margin * 16.0; m *= 2.0) {
            double[] c = gf.clone();
            restore(L, c, m, false);
            if (comp.maxViolation(c, model.forward(sc, c)) <= 0.0) {
                System.arraycopy(c, 0, gf, 0, gf.length);
                return true;
            }
        }
        return false;
    }

    private static double[][] METRIC_CACHE;
    private static int METRIC_N = -1;

    /** (L^T L + eps I)^-1 with L the second-difference operator: makes the least-norm correction the
     *  SMOOTHEST correction rather than the smallest, so a repair cannot re-introduce a flick. */
    static double[][] smoothMetric(int n) {
        double eps = Double.parseDouble(System.getProperty("pkc.sr.weps", "5e-3"));
        if (METRIC_N == n && METRIC_CACHE != null) return METRIC_CACHE;
        double[][] a = new double[n][n];
        double[] cf = {1.0, -2.0, 1.0};
        for (int t = 1; t < n - 1; t++) {
            int[] idx = {t - 1, t, t + 1};
            for (int i = 0; i < 3; i++) {
                for (int k = 0; k < 3; k++) a[idx[i]][idx[k]] += cf[i] * cf[k];
            }
        }
        for (int i = 0; i < n; i++) a[i][i] += eps;
        METRIC_CACHE = invert(a);
        METRIC_N = n;
        return METRIC_CACHE;
    }

    static double[][] invert(double[][] a) {
        int n = a.length;
        double[][] w = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(a[i], 0, w[i], 0, n);
            w[i][n + i] = 1.0;
        }
        for (int c = 0; c < n; c++) {
            int piv = c;
            for (int r = c + 1; r < n; r++) if (Math.abs(w[r][c]) > Math.abs(w[piv][c])) piv = r;
            double[] tmp = w[c];
            w[c] = w[piv];
            w[piv] = tmp;
            double d = w[c][c];
            if (Math.abs(d) < 1.0e-16) d = d >= 0 ? 1.0e-16 : -1.0e-16;
            for (int k = 0; k < 2 * n; k++) w[c][k] /= d;
            for (int r = 0; r < n; r++) {
                if (r == c) continue;
                double f = w[r][c];
                if (f == 0.0) continue;
                for (int k = 0; k < 2 * n; k++) w[r][k] -= f * w[c][k];
            }
        }
        double[][] out = new double[n][n];
        for (int i = 0; i < n; i++) System.arraycopy(w[i], n, out[i], 0, n);
        return out;
    }

    /** Projector onto the null space of the active wall gradients: I - J^T (J J^T)^-1 J. */
    static double[][] tangentProjector(Lin L, double[] gf, double margin, int n) {
        List<Integer> act = new ArrayList<Integer>();
        for (int j = 0; j < L.walls.size(); j++) {
            if (wallValue(L, j, gf) - L.walls.get(j).bPrime > -margin * 6.0) act.add(j);
        }
        double[][] P = new double[n][n];
        for (int i = 0; i < n; i++) P[i][i] = 1.0;
        if (act.isEmpty()) return P;
        int m = act.size();
        double[][] J = new double[m][n];
        for (int i = 0; i < m; i++) wallGrad(L, act.get(i), gf, J[i]);
        double[][] jjt = new double[m][m];
        for (int a = 0; a < m; a++) {
            for (int b = 0; b < m; b++) {
                double sm = 0.0;
                for (int t = 0; t < n; t++) sm += J[a][t] * J[b][t];
                jjt[a][b] = sm;
            }
        }
        double scale = 0.0;
        for (int a = 0; a < m; a++) scale = Math.max(scale, Math.abs(jjt[a][a]));
        double[][] inv = invert(addReg(jjt, scale * 1.0e-10 + 1.0e-20));
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                double sm = 0.0;
                for (int a = 0; a < m; a++) {
                    for (int b = 0; b < m; b++) sm += J[a][r] * inv[a][b] * J[b][c];
                }
                P[r][c] -= sm;
            }
        }
        return P;
    }

    static double[][] addReg(double[][] a, double reg) {
        double[][] o = new double[a.length][a.length];
        for (int i = 0; i < a.length; i++) {
            System.arraycopy(a[i], 0, o[i], 0, a.length);
            o[i][i] += reg;
        }
        return o;
    }

    static double[] apply(double[][] P, double[] v) {
        double[] o = new double[v.length];
        for (int i = 0; i < v.length; i++) {
            double s = 0.0;
            for (int k = 0; k < v.length; k++) s += P[i][k] * v[k];
            o[i] = s;
        }
        return o;
    }

    static double jerkOf(double anchor, double[] y) {
        double[] d = deltas(anchor, y);
        double s = 0.0;
        for (int i = 1; i < d.length; i++) s += Math.abs(d[i] - d[i - 1]);
        return s;
    }

    static List<int[]> runSpans(double anchor, double[] y) {
        List<int[]> out = new ArrayList<int[]>();
        double[] d = deltas(anchor, y);
        int last = 0;
        int start = 0;
        for (int t = 0; t < d.length; t++) {
            if (Math.abs(d[t]) <= FLOOR) continue;
            int sg = d[t] > 0 ? 1 : -1;
            if (last == 0) {
                last = sg;
                start = t;
                continue;
            }
            if (sg != last) {
                out.add(new int[] {start, t - 1});
                last = sg;
                start = t;
            }
        }
        if (last != 0) out.add(new int[] {start, d.length - 1});
        return out;
    }

    /** Replace ticks [lo..hi] with a constant turn rate between their neighbours. */
    static double[] flattenYaw(double anchor, double[] y, int lo, int hi) {
        int n = y.length;
        double left = lo == 0 ? anchor : y[lo - 1];
        double right = hi + 1 < n ? y[hi + 1] : y[hi];
        int steps = hi + 1 < n ? hi - lo + 2 : hi - lo + 1;
        double span = Angles.wrapDelta(right - left);
        double[] c = y.clone();
        for (int k = lo; k <= hi; k++) c[k] = Angles.wrap(left + span * (k - lo + 1.0) / steps);
        return c;
    }

    static double turnCost(double anchor, double[] y) {
        double[] d = deltas(anchor, y);
        double j2 = 0.0;
        for (int i = 1; i < d.length; i++) {
            double v = d[i] - d[i - 1];
            j2 += v * v;
        }
        return 90.0 * reversals(anchor, y) + 0.02 * Math.sqrt(j2);
    }

    static double smoothCost(double anchor, double[] y) {
        double[] d = deltas(anchor, y);
        double s = 0.0;
        for (int i = 1; i < d.length; i++) {
            double v = d[i] - d[i - 1];
            s += v * v;
        }
        return s + 1000.0 * reversals(anchor, y);
    }

    /** Recover absolute yaws from game facings, undoing whatever offset toGameFacings applied. */
    static double[] fromGf(JumpPhysicsInputs sc, double[] gf, double[] refYaw) {
        double[] refGf = sc.toGameFacings(refYaw);
        double[] y = new double[gf.length];
        for (int t = 0; t < gf.length; t++) y[t] = Angles.wrap(refYaw[t] + (gf[t] - refGf[t]));
        return y;
    }
}
