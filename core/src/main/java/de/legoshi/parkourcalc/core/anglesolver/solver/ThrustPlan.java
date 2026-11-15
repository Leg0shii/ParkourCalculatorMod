package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;

public final class ThrustPlan {

    public static final double TOLERANCE = 1.0e-9;
    public static final int MAX_ITERATIONS = 400;

    private static final int LINE_STEPS = 6;
    private static final double ARMIJO = 1.0e-4;
    private static final double MIN_DAMPING = 1.0e-10;
    private static final double START_DAMPING = 1.0e-6;
    private static final double DAMPING_UP = 10.0;
    private static final double DAMPING_DOWN = 3.0;
    private static final int DAMPING_TRIES = 16;

    public static final class Row {
        final double[] ax;
        final double[] az;
        final double rhs;
        final boolean eq;

        public Row(double[] ax, double[] az, double rhs, boolean eq) {
            this.ax = ax;
            this.az = az;
            this.rhs = rhs;
            this.eq = eq;
        }
    }

    public static final class Result {
        public final double[] vx;
        public final double[] vz;
        public final double[] qx;
        public final double[] qz;
        public final double[] multipliers;
        public final double[] residual;
        public final double violation;
        public final boolean converged;
        public final int iterations;

        Result(double[] vx, double[] vz, double[] qx, double[] qz, double[] multipliers, double[] residual,
               double violation, boolean converged, int iterations) {
            this.vx = vx;
            this.vz = vz;
            this.qx = qx;
            this.qz = qz;
            this.multipliers = multipliers;
            this.residual = residual;
            this.violation = violation;
            this.converged = converged;
            this.iterations = iterations;
        }

        public double share(int t) {
            return StrictMath.hypot(vx[t], vz[t]);
        }
    }

    private final int n;
    private final double[] cost;
    private final List<Row> rows;
    private final double[] qx;
    private final double[] qz;
    private final double[] vx;
    private final double[] vz;
    private final double cap;

    private ThrustPlan(int n, double[] cost, List<Row> rows, double cap) {
        this.n = n;
        this.cost = cost;
        this.rows = rows;
        this.cap = cap;
        this.qx = new double[n];
        this.qz = new double[n];
        this.vx = new double[n];
        this.vz = new double[n];
    }

    public static Result solve(int n, double[] cost, List<Row> rows, double softness, double[] warm, long deadlineNanos) {
        return solve(n, cost, rows, softness, warm, MAX_ITERATIONS, Double.POSITIVE_INFINITY, deadlineNanos);
    }

    public static Result solve(int n, double[] cost, List<Row> rows, double softness, double[] warm, int maxIterations,
                               double multiplierCap, long deadlineNanos) {
        ThrustPlan plan = new ThrustPlan(n, cost, rows, multiplierCap);
        double[] lam = new double[rows.size()];
        if (warm != null && warm.length == lam.length) {
            for (int i = 0; i < lam.length; i++) {
                lam[i] = plan.clamp(i, warm[i]);
            }
        }
        return plan.run(lam, softness, maxIterations, deadlineNanos);
    }

    private double clamp(int i, double value) {
        double low = rows.get(i).eq ? -cap : 0.0;
        return Math.max(low, Math.min(cap, value));
    }

    private boolean blocked(int i, double lam, double g) {
        if (lam >= cap && g > 0.0) {
            return true;
        }
        return rows.get(i).eq && lam <= -cap && g < 0.0;
    }

    private boolean stuck(double[] g) {
        if (cap < Double.POSITIVE_INFINITY) {
            return false;
        }
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if ((r.eq ? Math.abs(g[i]) : g[i]) <= TOLERANCE) {
                continue;
            }
            boolean coupled = false;
            for (int t = 0; t < n && !coupled; t++) {
                coupled = r.ax[t] != 0.0 || r.az[t] != 0.0;
            }
            if (!coupled) {
                return true;
            }
        }
        return false;
    }

    private Result run(double[] lam, double eps, int maxIterations, long deadlineNanos) {
        int m = rows.size();
        double[] g = new double[m];
        double value = evaluate(lam, eps, g);
        boolean converged = false;
        int it = 0;
        boolean stuck = stuck(g);
        double damping = START_DAMPING;
        double[] reach = new double[m];
        for (int i = 0; i < m; i++) {
            Row r = rows.get(i);
            for (int t = 0; t < n; t++) {
                reach[i] += r.ax[t] * r.ax[t] + r.az[t] * r.az[t];
            }
        }
        for (; it < maxIterations && !stuck; it++) {
            if (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos) {
                break;
            }
            List<Integer> active = new ArrayList<>();
            double worst = 0.0;
            for (int i = 0; i < m; i++) {
                if (blocked(i, lam[i], g[i])) {
                    continue;
                }
                boolean held = rows.get(i).eq || lam[i] > 0.0;
                if (held || g[i] > TOLERANCE) {
                    active.add(i);
                    worst = Math.max(worst, held ? Math.abs(g[i]) : g[i]);
                }
            }
            if (worst <= TOLERANCE) {
                converged = true;
                break;
            }
            double[][] h = hessian(active, eps);
            double scale = 1.0e-12;
            for (int i : active) {
                scale = Math.max(scale, reach[i] / eps);
            }
            double[] next = new double[m];
            double[] gNext = new double[m];
            boolean moved = false;
            for (int attempt = 0; attempt < DAMPING_TRIES && !moved; attempt++) {
                double[] step = attempt + 1 < DAMPING_TRIES ? newton(active, h, g, damping * scale)
                        : ascent(lam, g, scale);
                double t = 1.0;
                for (int ls = 0; ls < LINE_STEPS; ls++) {
                    double predicted = 0.0;
                    for (int i = 0; i < m; i++) {
                        next[i] = clamp(i, lam[i] + t * step[i]);
                        predicted += g[i] * (next[i] - lam[i]);
                    }
                    if (predicted > 0.0) {
                        double trial = evaluate(next, eps, gNext);
                        if (trial >= value + ARMIJO * predicted) {
                            System.arraycopy(next, 0, lam, 0, m);
                            System.arraycopy(gNext, 0, g, 0, m);
                            value = trial;
                            moved = true;
                            if (ls == 0 && attempt == 0) {
                                damping = Math.max(MIN_DAMPING, damping / DAMPING_DOWN);
                            }
                            break;
                        }
                    }
                    t *= 0.5;
                }
                if (!moved) {
                    damping *= DAMPING_UP;
                }
            }
            if (!moved) {
                break;
            }
        }
        evaluate(lam, eps, g);
        double violation = 0.0;
        for (int i = 0; i < m; i++) {
            violation = Math.max(violation, rows.get(i).eq ? Math.abs(g[i]) : g[i]);
        }
        return new Result(vx.clone(), vz.clone(), qx.clone(), qz.clone(), lam, g, violation, converged, it);
    }

    private double evaluate(double[] lam, double eps, double[] g) {
        java.util.Arrays.fill(qx, 0.0);
        java.util.Arrays.fill(qz, 0.0);
        int m = rows.size();
        double value = 0.0;
        for (int i = 0; i < m; i++) {
            double l = lam[i];
            if (l == 0.0) {
                continue;
            }
            Row r = rows.get(i);
            for (int t = 0; t < n; t++) {
                qx[t] -= l * r.ax[t];
                qz[t] -= l * r.az[t];
            }
            value -= l * r.rhs;
        }
        for (int t = 0; t < n; t++) {
            double q = StrictMath.hypot(qx[t], qz[t]);
            double s = share(q, cost[t], eps);
            if (s <= 0.0) {
                vx[t] = 0.0;
                vz[t] = 0.0;
                continue;
            }
            vx[t] = s * qx[t] / q;
            vz[t] = s * qz[t] / q;
            value += cost[t] * s + 0.5 * eps * s * s - q * s;
        }
        for (int i = 0; i < m; i++) {
            Row r = rows.get(i);
            double sum = -r.rhs;
            for (int t = 0; t < n; t++) {
                sum += r.ax[t] * vx[t] + r.az[t] * vz[t];
            }
            g[i] = sum;
        }
        return value;
    }

    private static double share(double q, double cost, double eps) {
        if (q <= 0.0) {
            return 0.0;
        }
        double s = (q - cost) / eps;
        return s <= 0.0 ? 0.0 : Math.min(1.0, s);
    }

    private double[] ascent(double[] lam, double[] g, double scale) {
        int m = rows.size();
        double[] step = new double[m];
        for (int i = 0; i < m; i++) {
            boolean free = rows.get(i).eq || lam[i] > 0.0 || g[i] > 0.0;
            step[i] = free && !blocked(i, lam[i], g[i]) ? g[i] / scale : 0.0;
        }
        return step;
    }

    private double[][] hessian(List<Integer> active, double eps) {
        int k = active.size();
        double[] wxx = new double[n];
        double[] wxz = new double[n];
        double[] wzz = new double[n];
        for (int t = 0; t < n; t++) {
            double q = StrictMath.hypot(qx[t], qz[t]);
            double s = share(q, cost[t], eps);
            if (s <= 0.0) {
                continue;
            }
            double cx = qx[t] / q;
            double cz = qz[t] / q;
            double along = s < 1.0 ? 1.0 / eps : 0.0;
            double across = s / q;
            wxx[t] = along * cx * cx + across * cz * cz;
            wxz[t] = (along - across) * cx * cz;
            wzz[t] = along * cz * cz + across * cx * cx;
        }
        double[][] h = new double[k][k];
        for (int a = 0; a < k; a++) {
            Row ra = rows.get(active.get(a));
            double[] wx = new double[n];
            double[] wz = new double[n];
            for (int t = 0; t < n; t++) {
                wx[t] = wxx[t] * ra.ax[t] + wxz[t] * ra.az[t];
                wz[t] = wxz[t] * ra.ax[t] + wzz[t] * ra.az[t];
            }
            for (int b = a; b < k; b++) {
                Row rb = rows.get(active.get(b));
                double sum = 0.0;
                for (int t = 0; t < n; t++) {
                    sum += wx[t] * rb.ax[t] + wz[t] * rb.az[t];
                }
                h[a][b] = sum;
                h[b][a] = sum;
            }
        }
        return h;
    }

    private double[] newton(List<Integer> active, double[][] h, double[] g, double ridge) {
        int k = active.size();
        double[] rhs = new double[k];
        for (int a = 0; a < k; a++) {
            rhs[a] = g[active.get(a)];
        }
        double[] solved = null;
        for (int tries = 0; tries < 12 && solved == null; tries++) {
            solved = cholesky(h, rhs, ridge);
            ridge *= 10.0;
        }
        double[] step = new double[rows.size()];
        for (int a = 0; a < k; a++) {
            step[active.get(a)] = solved == null ? rhs[a] : solved[a];
        }
        return step;
    }

    private static double[] cholesky(double[][] h, double[] rhs, double ridge) {
        int k = rhs.length;
        double[][] l = new double[k][k];
        for (int i = 0; i < k; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = h[i][j] + (i == j ? ridge : 0.0);
                for (int x = 0; x < j; x++) {
                    sum -= l[i][x] * l[j][x];
                }
                if (i == j) {
                    if (!(sum > 0.0)) {
                        return null;
                    }
                    l[i][i] = StrictMath.sqrt(sum);
                } else {
                    l[i][j] = sum / l[j][j];
                }
            }
        }
        double[] y = new double[k];
        for (int i = 0; i < k; i++) {
            double sum = rhs[i];
            for (int x = 0; x < i; x++) {
                sum -= l[i][x] * y[x];
            }
            y[i] = sum / l[i][i];
        }
        double[] out = new double[k];
        for (int i = k - 1; i >= 0; i--) {
            double sum = y[i];
            for (int x = i + 1; x < k; x++) {
                sum -= l[x][i] * out[x];
            }
            out[i] = sum / l[i][i];
        }
        return out;
    }
}
