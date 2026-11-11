package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ShapeSmooth {

    public enum Norm {
        L1(1.0),
        L2(2.0),
        LOG(0.0);

        final double power;

        Norm(double power) {
            this.power = power;
        }
    }

    public static final class Weights {
        public final Norm norm;
        public final double travel;
        public final double accel;
        public final double jerk;
        public final double heading;

        public Weights(Norm norm, double travel, double accel, double jerk, double heading) {
            this.norm = norm;
            this.travel = travel;
            this.accel = accel;
            this.jerk = jerk;
            this.heading = heading;
        }
    }

    public static final int MAX_ITERATIONS = 60;
    public static final double WALL_MARGIN = 1.0e-4;

    private static final double EPS_DEG = 0.5;
    private static final double HEADING_SCALE = 100.0;
    private static final double MULTIPLIER_CAP = 1.0e7;
    private static final double REPAIR_DAMPING = 1.0e-3;
    private static final double REPAIR_MIN_SCALE = 1.0 / 16.0;
    private static final int REPAIR_ROUNDS = 6;
    private static final double REPAIR_SHRINK = 0.5;
    private static final int STEP_TRIES = 5;
    private static final int LINE_STEPS = 5;
    private static final double LINE_SHRINK = 0.4;
    private static final double MIN_GAIN = 1.0e-4;
    private static final double[] D1 = {1.0, -1.0};
    private static final double[] D2 = {1.0, -2.0, 1.0};
    private static final double[] D3 = {1.0, -3.0, 3.0, -1.0};

    private final ExactJumpModel exact;
    private final JumpPhysicsInputs sc;
    private final JumpConstraintCompiler.Compiled compiled;
    private final JumpLinearModel lin;
    private final List<JumpConstraint> rows = new ArrayList<>();
    private final List<JumpLinearModel.Wall> walls = new ArrayList<>();
    private final int n;
    private final double anchor;
    private final int[] free;
    private final int[] slot;

    public ShapeSmooth(ExactJumpModel exact, JumpSpec spec) {
        this.exact = exact;
        this.sc = spec.asScenario();
        this.compiled = JumpConstraintCompiler.compile(spec);
        this.lin = new JumpLinearModel(sc);
        this.n = sc.numTicks;
        this.anchor = sc.startYaw;
        for (JumpConstraint c : spec.constraints) {
            if (c.mode != JumpConstraint.Mode.X && c.mode != JumpConstraint.Mode.Z) {
                continue;
            }
            JumpLinearModel.Wall wall = lin.compileWall(c, 0.0, null);
            if (wall == null) {
                continue;
            }
            rows.add(c);
            walls.add(wall);
        }
        boolean[] held = DeWiggle.heldByFacing(n, spec);
        FacingPrefold pre = FacingPrefold.analyze(spec.constraints, lin);
        if (pre != null && !pre.isIdentity()) {
            for (int t = 0; t < n; t++) {
                held[t] |= pre.varIndex(t) < 0;
            }
        }
        int count = 0;
        int[] idx = new int[n];
        slot = new int[n];
        Arrays.fill(slot, -1);
        for (int t = 0; t < n; t++) {
            if (!held[t]) {
                slot[t] = count;
                idx[count++] = t;
            }
        }
        free = Arrays.copyOf(idx, count);
    }

    public double[] unwrap(double[] absWrapped) {
        double[] u = new double[n];
        double prev = anchor;
        for (int t = 0; t < n; t++) {
            prev += Angles.wrapDelta(absWrapped[t] - prev);
            u[t] = prev;
        }
        return u;
    }

    public ForwardPath path(double[] unwrapped) {
        return exact.forward(sc, sc.toGameFacings(Angles.wrapAll(unwrapped.clone())));
    }

    public boolean feasible(double[] absWrapped) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(absWrapped.clone()));
        return compiled.maxViolation(gf, exact.forward(sc, gf)) <= 0.0;
    }

    public double[] run(double[] seedAbsWrapped, Weights w, long deadlineNanos, AtomicBoolean cancel) {
        double[] seed = Angles.wrapAll(seedAbsWrapped.clone());
        if (free.length == 0 || seed.length != n) {
            return seed;
        }
        double[] u = unwrap(seed);
        double[] gf0 = sc.toGameFacings(Angles.wrapAll(u.clone()));
        ForwardPath p = exact.forward(sc, gf0);
        if (compiled.maxViolation(gf0, p) > 0.0) {
            return seed;
        }
        HeadingBand band = w.heading > 0.0 ? HeadingBand.of(p, u) : null;
        double cur = cost(u, w, band);
        double damping = 1.0e-3;
        for (int it = 0; it < MAX_ITERATIONS; it++) {
            if (stopped(deadlineNanos, cancel)) {
                break;
            }
            double[] r = residuals(p);
            double[][] jac = jacobian(u);
            double[][] h = new double[free.length][BandQp.BAND + 1];
            double[] g = new double[free.length];
            assemble(u, w, band, h, g);
            BandQp.Factor repairFactor = null;
            List<double[]> a = new ArrayList<>();
            List<Double> b = new ArrayList<>();
            wallRows(jac, r, a, b, false);
            double[] accepted = null;
            ForwardPath acceptedPath = null;
            HeadingBand acceptedBand = null;
            double acceptedCost = Double.NaN;
            for (int tries = 0; tries < STEP_TRIES && accepted == null; tries++) {
                BandQp.Factor factor = BandQp.factor(h, damping);
                if (damping >= REPAIR_DAMPING) {
                    repairFactor = factor;
                } else if (repairFactor == null) {
                    repairFactor = BandQp.factor(h, REPAIR_DAMPING);
                }
                double[] step = BandQp.solve(factor, g, a, b, MULTIPLIER_CAP);
                double alpha = 1.0;
                for (int ls = 0; ls < LINE_STEPS && accepted == null; ls++) {
                    if (stopped(deadlineNanos, cancel)) {
                        break;
                    }
                    double[] cand = u.clone();
                    for (int i = 0; i < free.length; i++) {
                        cand[free[i]] += alpha * step[i];
                    }
                    alpha *= LINE_SHRINK;
                    cand = repair(cand, repairFactor);
                    if (cand == null) {
                        continue;
                    }
                    ForwardPath cp = path(cand);
                    HeadingBand cb = w.heading > 0.0 ? HeadingBand.of(cp, cand) : null;
                    double cc = cost(cand, w, cb);
                    if (cc < cur - 1.0e-9) {
                        accepted = cand;
                        acceptedPath = cp;
                        acceptedBand = cb;
                        acceptedCost = cc;
                    }
                }
                if (accepted == null) {
                    damping *= 6.0;
                }
            }
            if (accepted == null) {
                break;
            }
            double gain = cur - acceptedCost;
            u = accepted;
            p = acceptedPath;
            band = acceptedBand;
            cur = acceptedCost;
            damping = Math.max(1.0e-6, damping / 3.0);
            if (gain < MIN_GAIN * Math.max(1.0, cur)) {
                break;
            }
        }
        return Angles.wrapAll(u.clone());
    }

    private static boolean stopped(long deadlineNanos, AtomicBoolean cancel) {
        if (cancel != null && cancel.get()) {
            return true;
        }
        return deadlineNanos != 0L && System.nanoTime() >= deadlineNanos;
    }

    private double violation(double[] unwrapped, ForwardPath[] pathOut) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(unwrapped.clone()));
        ForwardPath p = exact.forward(sc, gf);
        if (pathOut != null) {
            pathOut[0] = p;
        }
        return compiled.maxViolation(gf, p);
    }

    private double[] repair(double[] candidate, BandQp.Factor factor) {
        double[] cand = candidate;
        ForwardPath[] holder = new ForwardPath[1];
        double viol = violation(cand, holder);
        double[] zero = new double[free.length];
        for (int round = 0; round < REPAIR_ROUNDS && viol > 0.0; round++) {
            double[] r = residuals(holder[0]);
            double[][] jac = jacobian(cand);
            List<double[]> a = new ArrayList<>();
            List<Double> b = new ArrayList<>();
            wallRows(jac, r, a, b, true);
            double[] step = BandQp.solve(factor, zero, a, b, MULTIPLIER_CAP);
            boolean stepped = false;
            for (double scale = 1.0; scale >= REPAIR_MIN_SCALE; scale *= 0.5) {
                double[] next = cand.clone();
                for (int i = 0; i < free.length; i++) {
                    next[free[i]] += scale * step[i];
                }
                double nv = violation(next, holder);
                if (nv <= 0.0 || nv < REPAIR_SHRINK * viol) {
                    cand = next;
                    viol = nv;
                    stepped = true;
                    break;
                }
            }
            if (!stepped) {
                break;
            }
        }
        return viol <= 0.0 ? cand : null;
    }

    private void wallRows(double[][] jac, double[] r, List<double[]> a, List<Double> b, boolean push) {
        for (int i = 0; i < rows.size(); i++) {
            double[] row = new double[free.length];
            boolean any = false;
            for (int k = 0; k < free.length; k++) {
                row[k] = jac[i][free[k]];
                any |= row[k] != 0.0;
            }
            if (!any) {
                continue;
            }
            if (rows.get(i).cmp == JumpConstraint.Cmp.EQ) {
                a.add(row);
                b.add(-r[i]);
                double[] neg = new double[free.length];
                for (int k = 0; k < free.length; k++) {
                    neg[k] = -row[k];
                }
                a.add(neg);
                b.add(r[i]);
            } else {
                a.add(row);
                b.add(push ? WALL_MARGIN - r[i] : Math.min(0.0, WALL_MARGIN - r[i]));
            }
        }
    }

    private double value(ForwardPath p, JumpConstraint c) {
        JumpPhysicsInputs.Axis axis = c.mode == JumpConstraint.Mode.X ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z;
        double v = p.getPos(c.t1, axis);
        if (c.t2 != null) {
            double other = p.getPos(c.t2, axis);
            v += c.op == JumpConstraint.Op.PLUS ? other : -other;
        }
        return v;
    }

    private double[] residuals(ForwardPath p) {
        double[] out = new double[rows.size()];
        for (int i = 0; i < out.length; i++) {
            JumpConstraint c = rows.get(i);
            double got = value(p, c);
            out[i] = c.cmp == JumpConstraint.Cmp.LE ? c.rhs - got : got - c.rhs;
        }
        return out;
    }

    private double[][] jacobian(double[] unwrapped) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(unwrapped.clone()));
        double[] ux = new double[n];
        double[] uz = new double[n];
        for (int t = 0; t < n; t++) {
            double phi = lin.baseArg(t) + Angles.rad(gf[t]);
            ux[t] = lin.mMag(t) * StrictMath.cos(phi);
            uz[t] = lin.mMag(t) * StrictMath.sin(phi);
        }
        double radPerDeg = Angles.rad(1.0);
        double[][] j = new double[rows.size()][n];
        for (int i = 0; i < rows.size(); i++) {
            JumpLinearModel.Wall wall = walls.get(i);
            double sign = rows.get(i).cmp == JumpConstraint.Cmp.EQ ? -1.0 : 1.0;
            for (int t = 0; t < n; t++) {
                double du = wall.axis == 0 ? -uz[t] : ux[t];
                j[i][t] = sign * -wall.coef[t] * du * radPerDeg;
            }
        }
        return j;
    }

    private static double psi(double v, Norm norm) {
        double a = Math.abs(v);
        return norm == Norm.LOG ? EPS_DEG * StrictMath.log1p(a / EPS_DEG) : a;
    }

    private double cost(double[] u, Weights w, HeadingBand band) {
        double sum = 0.0;
        double d1 = 0.0;
        double d2 = 0.0;
        for (int t = 0; t < n; t++) {
            double d = u[t] - (t == 0 ? anchor : u[t - 1]);
            if (w.travel > 0.0) {
                sum += w.travel * psi(d, w.norm);
            }
            if (t >= 1) {
                sum += w.accel * psi(d - d1, w.norm);
            }
            if (t >= 2) {
                sum += w.jerk * psi(d - 2.0 * d1 + d2, w.norm);
            }
            if (band != null) {
                double m = band.excess(t, u[t]);
                sum += w.heading * band.weight(t) * m * m / HEADING_SCALE;
            }
            d2 = d1;
            d1 = d;
        }
        return sum;
    }

    private void assemble(double[] u, Weights w, HeadingBand band, double[][] h, double[] g) {
        double d1 = 0.0;
        double d2 = 0.0;
        for (int t = 0; t < n; t++) {
            double d = u[t] - (t == 0 ? anchor : u[t - 1]);
            if (w.travel > 0.0) {
                term(h, g, t, D1, d, w.travel, w.norm);
            }
            if (t >= 1) {
                term(h, g, t, D2, d - d1, w.accel, w.norm);
            }
            if (t >= 2) {
                term(h, g, t, D3, d - 2.0 * d1 + d2, w.jerk, w.norm);
            }
            if (band != null && slot[t] >= 0) {
                double m = band.excess(t, u[t]);
                if (m != 0.0) {
                    double q = 2.0 * w.heading * band.weight(t) / HEADING_SCALE;
                    h[slot[t]][0] += q;
                    g[slot[t]] += q * m;
                }
            }
            d2 = d1;
            d1 = d;
        }
    }

    private void term(double[][] h, double[] g, int t, double[] stencil, double v, double weight, Norm norm) {
        if (weight <= 0.0) {
            return;
        }
        double q = weight / StrictMath.pow(Math.abs(v) + EPS_DEG, 2.0 - norm.power);
        for (int x = 0; x < stencil.length; x++) {
            int tx = t - x;
            if (tx < 0 || slot[tx] < 0) {
                continue;
            }
            int px = slot[tx];
            g[px] += q * v * stencil[x];
            for (int y = 0; y < stencil.length; y++) {
                int ty = t - y;
                if (ty < 0 || slot[ty] < 0) {
                    continue;
                }
                int py = slot[ty];
                if (py <= px) {
                    h[px][px - py] += q * stencil[x] * stencil[y];
                }
            }
        }
    }
}
