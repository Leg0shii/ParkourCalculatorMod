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
            double[][] jjt = new double[m][m];
            for (int a = 0; a < m; a++) {
                for (int b = 0; b < m; b++) {
                    double s = 0.0;
                    for (int t = 0; t < n; t++) s += J[a][t] * J[b][t];
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
                    for (int t = 0; t < n; t++) c[t] += damp * lam[a] * J[a][t];
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
        int rounds = Integer.getInteger("pkc.sr.rounds", 400);
        double step = Double.parseDouble(System.getProperty("pkc.sr.step", "4"));
        int patternPasses = Integer.getInteger("pkc.sr.passes", 6);

        double[] gf = sc.toGameFacings(seed);
        double[] bestGf = gf.clone();
        double bestExact = comp.maxViolation(gf, model.forward(sc, gf));
        System.out.printf("SR seed exactViol=%.4e (kept as the floor; recovery may only improve on it)%n", bestExact);
        if (bestExact <= 0.0) {
            System.out.println("SR seed already certifies, nothing to recover");
        }

        for (int pass = 0; pass < patternPasses && bestExact > 0.0; pass++) {
            double[] yNow = fromGf(sc, gf, seed);
            boolean[] zx = new boolean[n];
            boolean[] zz = new boolean[n];
            new JumpLinearModel(sc).zeroingPattern(Angles.wrapAll(yNow), model.inertiaThreshold(),
                    model.perAxisInertia(), zx, zz);
            L = build(sc, spec, zx, zz);

            boolean ok = restore(L, gf, margin, false);
            double ev0 = comp.maxViolation(gf, model.forward(sc, gf));
            System.out.printf("SR pass=%d restore=%s linViol=%11.4e exactViol=%11.4e rev=%d runs=%d%n",
                    pass, ok, linViol(L, gf), ev0, reversals(anchor, fromGf(sc, gf, seed)),
                    runsOf(anchor, fromGf(sc, gf, seed)));
            if (ev0 < bestExact) {
                bestExact = ev0;
                bestGf = gf.clone();
            }

            double curStep = step;
            int acc = 0;
            for (int it = 0; it < rounds; it++) {
                double[] y = fromGf(sc, gf, seed);
                double[] g = smoothGrad(anchor, y);
                List<Integer> act = new ArrayList<Integer>();
                for (int j = 0; j < L.walls.size(); j++) {
                    if (wallValue(L, j, gf) - L.walls.get(j).bPrime > -margin * 4.0) act.add(j);
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
                double cost = smoothCost(anchor, y);
                for (double a = curStep; a >= 1.0e-4; a *= 0.5) {
                    double[] c = gf.clone();
                    double k = a / gn;
                    for (int t = 0; t < n; t++) c[t] -= k * d[t];
                    if (!restore(L, c, margin, false)) continue;
                    double[] cy = fromGf(sc, c, seed);
                    if (smoothCost(anchor, cy) >= cost) continue;
                    gf = c;
                    moved = true;
                    acc++;
                    break;
                }
                if (!moved) {
                    curStep *= 0.6;
                    if (curStep < 1.0e-4) break;
                }
            }
            double ev = comp.maxViolation(gf, model.forward(sc, gf));
            System.out.printf("SR   smoothed accepted=%d exactViol=%11.4e rev=%d runs=%d%n",
                    acc, ev, reversals(anchor, fromGf(sc, gf, seed)), runsOf(anchor, fromGf(sc, gf, seed)));
            if (ev < bestExact) {
                bestExact = ev;
                bestGf = gf.clone();
            }
            if (ev <= 0.0) break;
        }
        gf = bestGf;

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
