package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AlmBfgsCore {

    private static final boolean DEBUG = "1".equals(System.getenv("PKC_ALM_DEBUG"));
    private static final double COMBINED_INERTIA_SQ = 9.0E-6;
    private static final int MAX_REFRESH = 3;

    public static final class Config {
        public int maxOuter = 25;
        public int maxInner = 80;
        public double feasTol = 1.0e-6;
        public double gradTol = 1.0e-6;
        public double penInit = 1.0;
        public boolean patternRefresh = true;
        public boolean binaryZoom = false;
    }

    public static final class Counters {
        public int lsZoomExhausted;
        public int curvSkip;
        public int sdFallback;
        public int hReset;
        public int gradCheckFail;
        public int patternFlips;
        public int fRebase;
        public int almStall;
        public double smoothExactGap = Double.NaN;
    }

    public static final class Result {
        public final double[] thetaRad;
        public final double smoothViol;
        public final double smoothObjective;
        public final int outerIters;
        public final int patternFlips;
        public final Counters counters;
        public final double tx;
        public final double tz;

        Result(double[] thetaRad, double smoothViol, double smoothObjective, int outerIters,
               int patternFlips, Counters counters, double tx, double tz) {
            this.thetaRad = thetaRad;
            this.smoothViol = smoothViol;
            this.smoothObjective = smoothObjective;
            this.outerIters = outerIters;
            this.patternFlips = patternFlips;
            this.counters = counters;
            this.tx = tx;
            this.tz = tz;
        }
    }

    private AlmBfgsCore() {
    }

    public static Result solve(ExactJumpModel model, JumpSpec spec, double[] seedThetaRad, Config cfg,
                               long deadlineNanos, AtomicBoolean cancel) {
        return solve(model, spec, seedThetaRad, cfg, deadlineNanos, cancel, null);
    }

    public static Result solve(ExactJumpModel model, JumpSpec spec, double[] seedThetaRad, Config cfg,
                               long deadlineNanos, AtomicBoolean cancel, double[] transDomain) {
        return solve(model, spec, seedThetaRad, cfg, deadlineNanos, cancel, transDomain, null, null);
    }

    public static Result solve(ExactJumpModel model, JumpSpec spec, double[] seedThetaRad, Config cfg,
                               long deadlineNanos, AtomicBoolean cancel, double[] transDomain,
                               boolean[] pinnedZeroX, boolean[] pinnedZeroZ) {
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;
        Counters counters = new Counters();
        double velBound = model.inertiaThreshold();
        boolean translate = !isPinnedDomain(transDomain);
        int dim = translate ? n + 2 : n;
        boolean pinned = pinnedZeroX != null && pinnedZeroZ != null;

        double[] x = new double[dim];
        System.arraycopy(seedThetaRad, 0, x, 0, n);
        wrapX(x, n, translate, transDomain);
        boolean[] curX = new boolean[n];
        boolean[] curZ = new boolean[n];
        if (pinned) {
            System.arraycopy(pinnedZeroX, 0, curX, 0, n);
            System.arraycopy(pinnedZeroZ, 0, curZ, 0, n);
        } else {
            derivePattern(model, sc, firstN(x, n), curX, curZ);
        }
        SmoothJumpProblem problem = compileFor(spec, curX, curZ, model, velBound, translate, transDomain);

        AlmRun best = null;
        int refreshes = 0;
        while (true) {
            AlmRun run = runAlm(problem, x, cfg, deadlineNanos, cancel, counters, translate, transDomain);
            best = better(best, run, cfg.feasTol);
            if (pinned) break;
            boolean[] nX = new boolean[n];
            boolean[] nZ = new boolean[n];
            derivePattern(model, sc, firstN(run.x, n), nX, nZ);
            if (!cfg.patternRefresh || samePattern(curX, curZ, nX, nZ)
                    || refreshes >= MAX_REFRESH || out(deadlineNanos, cancel)) {
                break;
            }
            counters.patternFlips++;
            refreshes++;
            curX = nX;
            curZ = nZ;
            problem = compileFor(spec, curX, curZ, model, velBound, translate, transDomain);
            x = run.x.clone();
        }

        double[] theta = firstN(best.x, n);
        double[] gf = sc.toGameFacings(Angles.wrapAll(toDeg(theta)));
        ForwardPath path = model.forward(sc, gf);
        JumpConstraintCompiler.Compiled cc = JumpConstraintCompiler.compile(spec);
        double exactViol;
        if (translate) {
            exactViol = 0.0;
            for (JumpConstraint c : cc.ineq) {
                exactViol = Math.max(exactViol, JumpConstraintCompiler.translatedSlack(c, gf, path, best.tx, best.tz));
            }
            for (JumpConstraint c : cc.eq) {
                exactViol = Math.max(exactViol, Math.abs(JumpConstraintCompiler.translatedEvaluate(c, gf, path, best.tx, best.tz)));
            }
        } else {
            exactViol = cc.maxViolation(gf, path);
        }
        counters.smoothExactGap = Math.abs(exactViol - best.smoothViol);
        if (DEBUG) {
            System.out.println("[DBG-alm1] done outers=" + best.outerIters + " smoothViol=" + best.smoothViol
                    + " smoothObj=" + best.smoothObjective + " exactViol=" + exactViol
                    + " smooth_exact_gap=" + counters.smoothExactGap + " patternFlips=" + counters.patternFlips
                    + " tx=" + best.tx + " tz=" + best.tz
                    + " sdFallback=" + counters.sdFallback + " curvSkip=" + counters.curvSkip
                    + " lsZoomExhausted=" + counters.lsZoomExhausted + " almStall=" + counters.almStall);
        }
        return new Result(theta, best.smoothViol, best.smoothObjective, best.outerIters,
                counters.patternFlips, counters, best.tx, best.tz);
    }

    static boolean isPinnedDomain(double[] d) {
        if (d == null) return true;
        return d[0] == 0.0 && d[1] == 0.0 && d[2] == 0.0 && d[3] == 0.0;
    }

    private static SmoothJumpProblem compileFor(JumpSpec spec, boolean[] zx, boolean[] zz, ExactJumpModel model,
                                                double velBound, boolean translate, double[] dom) {
        SmoothJumpProblem p = SmoothJumpProblem.compile(spec, zx, zz, model.modern(), velBound);
        if (translate) p = p.withTranslationBox(dom[0], dom[1], dom[2], dom[3]);
        return p;
    }

    private static double[] firstN(double[] x, int n) {
        if (x.length == n) return x;
        double[] out = new double[n];
        System.arraycopy(x, 0, out, 0, n);
        return out;
    }

    private static final class AlmRun {
        final double[] x;
        final double smoothViol;
        final double smoothObjective;
        final double scoredSigned;
        final int outerIters;
        final double tx;
        final double tz;

        AlmRun(double[] x, double smoothViol, double smoothObjective, double scoredSigned,
               int outerIters, double tx, double tz) {
            this.x = x;
            this.smoothViol = smoothViol;
            this.smoothObjective = smoothObjective;
            this.scoredSigned = scoredSigned;
            this.outerIters = outerIters;
            this.tx = tx;
            this.tz = tz;
        }
    }

    private static AlmRun better(AlmRun best, AlmRun run, double feasTol) {
        if (best == null) return run;
        boolean bFeas = best.smoothViol <= feasTol;
        boolean rFeas = run.smoothViol <= feasTol;
        if (rFeas != bFeas) return rFeas ? run : best;
        if (!rFeas) return run.smoothViol < best.smoothViol ? run : best;
        return run.scoredSigned < best.scoredSigned ? run : best;
    }

    private static AlmRun runAlm(SmoothJumpProblem p, double[] seed, Config cfg, long deadlineNanos,
                                 AtomicBoolean cancel, Counters counters, boolean translate, double[] dom) {
        int n = p.n();
        double[] x = seed.clone();
        List<SmoothJumpProblem.Term> ineq = p.ineq();
        List<SmoothJumpProblem.Term> eqs = p.eq();
        double[] lamb = new double[ineq.size()];
        double[] nu = new double[eqs.size()];
        Holder pen = new Holder(cfg.penInit);
        ValueGradient oracle = translate ? new AugOracleT(p, lamb, nu, pen, n) : new AugOracle(p, lamb, nu, pen);

        double prevMaxVio = Double.POSITIVE_INFINITY;
        int outer = 0;
        for (; outer < cfg.maxOuter; outer++) {
            wrapX(x, n, translate, dom);
            double tx = translate ? x[n] : 0.0;
            double tz = translate ? x[n + 1] : 0.0;
            if (DEBUG && outer == 0) {
                if (translate) gradCheckT(p, x, tx, tz, lamb, nu, pen.v, counters);
                else gradCheck(p, x, lamb, nu, pen.v, counters);
            }
            BfgsResult br = bfgs(oracle, x, cfg.maxInner, cfg.gradTol, cfg.binaryZoom,
                    deadlineNanos, cancel, counters);
            x = br.x;
            tx = translate ? x[n] : 0.0;
            tz = translate ? x[n + 1] : 0.0;

            double maxGi = 0.0;
            String worst = "-";
            double worstVal = 0.0;
            for (int i = 0; i < ineq.size(); i++) {
                double gi = translate ? p.smoothValue(ineq.get(i), x, tx, tz) : p.smoothValue(ineq.get(i), x);
                lamb[i] = Math.max(0.0, lamb[i] + pen.v * gi);
                if (Math.max(0.0, gi) > maxGi) {
                    maxGi = Math.max(0.0, gi);
                    worst = ineq.get(i).name;
                    worstVal = gi;
                }
            }
            double maxHj = 0.0;
            for (int j = 0; j < eqs.size(); j++) {
                double hj = translate ? p.smoothValue(eqs.get(j), x, tx, tz) : p.smoothValue(eqs.get(j), x);
                nu[j] += pen.v * hj;
                if (Math.abs(hj) > maxHj) {
                    maxHj = Math.abs(hj);
                    if (maxHj > maxGi) {
                        worst = eqs.get(j).name;
                        worstVal = hj;
                    }
                }
            }
            double maxVio = Math.max(maxGi, maxHj);
            if (DEBUG) {
                System.out.println("[DBG-alm1] outer=" + outer + " pen=" + pen.v + " maxVio=" + maxVio
                        + " worst=" + worst + " worstVal=" + worstVal + " innerExit=" + br.exit
                        + " innerIters=" + br.iters + " gradNormSq=" + br.gradNormSq + " patternFlips=" + counters.patternFlips);
            }
            if (maxVio < cfg.feasTol) {
                outer++;
                break;
            }
            if (maxVio > 0.5 * prevMaxVio) pen.v *= 2.0;
            prevMaxVio = maxVio;
            if (pen.v > 1.0e12 && maxVio > 10.0 * cfg.feasTol) {
                counters.almStall++;
                if (DEBUG) System.out.println("[DBG-alm1] stall pen=" + pen.v + " maxVio=" + maxVio);
                break;
            }
            if (out(deadlineNanos, cancel)) break;
        }
        wrapX(x, n, translate, dom);
        double tx = translate ? x[n] : 0.0;
        double tz = translate ? x[n + 1] : 0.0;
        double finalVio = smoothViolation(p, x, tx, tz);
        double obj = translate ? p.smoothValue(p.objective(), x, tx, tz) : p.smoothValue(p.objective(), x);
        double scoredSigned = p.objectiveSign() * obj + p.travelPenalty(x);
        return new AlmRun(x, finalVio, obj, scoredSigned, outer, tx, tz);
    }

    private static double smoothViolation(SmoothJumpProblem p, double[] theta, double tx, double tz) {
        double v = 0.0;
        for (SmoothJumpProblem.Term t : p.ineq()) v = Math.max(v, Math.max(0.0, p.smoothValue(t, theta, tx, tz)));
        for (SmoothJumpProblem.Term t : p.eq()) v = Math.max(v, Math.abs(p.smoothValue(t, theta, tx, tz)));
        return v;
    }

    private static final class Holder {
        double v;

        Holder(double v) {
            this.v = v;
        }
    }

    private static final class AugOracle implements ValueGradient {
        final SmoothJumpProblem p;
        final double[] lamb;
        final double[] nu;
        final Holder pen;

        AugOracle(SmoothJumpProblem p, double[] lamb, double[] nu, Holder pen) {
            this.p = p;
            this.lamb = lamb;
            this.nu = nu;
            this.pen = pen;
        }

        @Override
        public double eval(double[] x, double[] gradOut) {
            return p.augLagrangian(x, lamb, nu, pen.v, gradOut);
        }
    }

    private static final class AugOracleT implements ValueGradient {
        final SmoothJumpProblem p;
        final double[] lamb;
        final double[] nu;
        final Holder pen;
        final int n;

        AugOracleT(SmoothJumpProblem p, double[] lamb, double[] nu, Holder pen, int n) {
            this.p = p;
            this.lamb = lamb;
            this.nu = nu;
            this.pen = pen;
            this.n = n;
        }

        @Override
        public double eval(double[] x, double[] gradOut) {
            return p.augLagrangianT(x, x[n], x[n + 1], lamb, nu, pen.v, gradOut);
        }
    }

    public interface ValueGradient {
        double eval(double[] x, double[] gradOut);
    }

    public static final class BfgsResult {
        public final double[] x;
        public final double value;
        public final double gradNormSq;
        public final int iters;
        public final String exit;

        BfgsResult(double[] x, double value, double gradNormSq, int iters, String exit) {
            this.x = x;
            this.value = value;
            this.gradNormSq = gradNormSq;
            this.iters = iters;
            this.exit = exit;
        }
    }

    public static BfgsResult bfgs(ValueGradient f, double[] x0, int maxInner, double gradTol,
                                  boolean binaryZoom, long deadlineNanos, AtomicBoolean cancel) {
        return bfgs(f, x0, maxInner, gradTol, binaryZoom, deadlineNanos, cancel, new Counters());
    }

    static BfgsResult bfgs(ValueGradient f, double[] x0, int maxInner, double gradTol,
                           boolean binaryZoom, long deadlineNanos, AtomicBoolean cancel, Counters counters) {
        int n = x0.length;
        double[] x = x0.clone();
        double[][] h = identity(n);
        double[] grad = new double[n];
        double val = f.eval(x, grad);
        double gt2 = gradTol * gradTol;
        String exit = "maxInner";
        int sdStreak = 0;
        int curvThisRun = 0;
        int iter = 0;
        for (; iter < maxInner; iter++) {
            if (dot(grad, grad) < gt2) {
                exit = "gradTol";
                break;
            }
            if (out(deadlineNanos, cancel)) {
                exit = "cancel";
                break;
            }
            double[] step = matVec(h, grad);
            for (int i = 0; i < n; i++) step[i] = -step[i];
            double deri = dot(grad, step);
            if (deri >= 0.0) {
                for (int i = 0; i < n; i++) step[i] = -grad[i];
                deri = dot(grad, step);
                counters.sdFallback++;
                sdStreak++;
                if (sdStreak > 10) {
                    h = identity(n);
                    counters.hReset++;
                    sdStreak = 0;
                }
            } else {
                sdStreak = 0;
            }
            double alpha = lineSearch(f, x, step, val, deri, binaryZoom, counters);
            double[] s = new double[n];
            for (int i = 0; i < n; i++) {
                s[i] = alpha * step[i];
                x[i] += s[i];
            }
            double[] gradNew = new double[n];
            double valNew = f.eval(x, gradNew);
            double[] y = new double[n];
            for (int i = 0; i < n; i++) y[i] = gradNew[i] - grad[i];
            double a = dot(s, y);
            double ss = dot(s, s);
            double yy = dot(y, y);
            double eps = 1.0e-12;
            if (a * a <= eps * eps * ss * yy) {
                counters.curvSkip++;
                curvThisRun++;
                System.arraycopy(gradNew, 0, grad, 0, n);
                val = valNew;
                continue;
            }
            double ainv = 1.0 / a;
            double[] hy = matVec(h, y);
            addSymOuter(h, s, hy, -ainv);
            double b = ainv * (1.0 + ainv * dot(hy, y));
            addOuter(h, s, s, b);
            System.arraycopy(gradNew, 0, grad, 0, n);
            val = valNew;
        }
        if (DEBUG) {
            System.out.println("[DBG-bfgs1] exit=" + exit + " iters=" + iter + " gradNormSq=" + dot(grad, grad)
                    + " curvSkip=" + curvThisRun + " sdFallback=" + counters.sdFallback
                    + " hReset=" + counters.hReset + " lsZoomExhausted=" + counters.lsZoomExhausted);
            if (curvThisRun > maxInner / 2) {
                System.out.println("[DBG-bfgs1] warn curv_skip=" + curvThisRun + " > maxInner/2");
            }
        }
        return new BfgsResult(x, val, dot(grad, grad), iter, exit);
    }

    private static double lineSearch(ValueGradient f, double[] x, double[] step, double val0, double deri0,
                                     boolean binaryZoom, Counters counters) {
        double c1 = 1.0e-4;
        double c2 = 0.9;
        int n = x.length;
        double base = 0.0;
        double alpha = 1.0;
        double valPrev = val0;
        double[] tmpX = new double[n];
        double[] tmpG = new double[n];
        int maxBracket = 20;
        for (int i = 0; i < maxBracket; i++) {
            double valA = phi(f, x, step, alpha, tmpX, tmpG);
            if (valA > val0 + c1 * alpha * deri0) {
                return zoom(f, x, step, val0, deri0, base, alpha, binaryZoom, c1, c2, counters);
            }
            if (base > 0.0 && valA >= valPrev) {
                return zoom(f, x, step, val0, deri0, base, alpha, binaryZoom, c1, c2, counters);
            }
            double deriA = dot(tmpG, step);
            if (Math.abs(deriA) <= -c2 * deri0) return alpha;
            if (deriA >= 0.0) {
                return zoom(f, x, step, val0, deri0, base, alpha, binaryZoom, c1, c2, counters);
            }
            valPrev = valA;
            base = alpha;
            alpha *= 2.0;
        }
        return alpha;
    }

    private static double zoom(ValueGradient f, double[] x, double[] step, double val0, double deri0,
                               double lo, double hi, boolean binaryZoom, double c1, double c2, Counters counters) {
        int n = x.length;
        double[] tmpX = new double[n];
        double[] tmpG = new double[n];
        double valLo = phi(f, x, step, lo, tmpX, tmpG);
        double deriLo = dot(tmpG, step);
        double valHi = binaryZoom ? 0.0 : phi(f, x, step, hi, tmpX, tmpG);
        int maxZoom = 20;
        for (int i = 0; i < maxZoom; i++) {
            double mid;
            if (binaryZoom) {
                mid = 0.5 * (lo + hi);
            } else {
                mid = safeInterp(lo, valLo, deriLo, hi, valHi);
            }
            double valMid = phi(f, x, step, mid, tmpX, tmpG);
            if (valMid > val0 + c1 * mid * deri0 || valMid >= valLo) {
                hi = mid;
                valHi = valMid;
            } else {
                double deriMid = dot(tmpG, step);
                if (Math.abs(deriMid) <= -c2 * deri0) return mid;
                lo = mid;
                valLo = valMid;
                deriLo = deriMid;
            }
        }
        counters.lsZoomExhausted++;
        return 0.5 * (lo + hi);
    }

    private static double safeInterp(double lo, double valLo, double deriLo, double hi, double valHi) {
        double width = hi - lo;
        double mid;
        if (width == 0.0) return lo;
        double cc = (valHi - valLo - deriLo * width) / (width * width);
        if (cc > 0.0) {
            mid = lo - deriLo / (2.0 * cc);
        } else {
            mid = 0.5 * (lo + hi);
        }
        double a = Math.min(lo, hi);
        double b = Math.max(lo, hi);
        double margin = 0.1 * (b - a);
        double loB = a + margin;
        double hiB = b - margin;
        if (Double.isNaN(mid) || Double.isInfinite(mid) || !(mid > loB && mid < hiB)) {
            mid = 0.5 * (lo + hi);
        }
        return mid;
    }

    private static double phi(ValueGradient f, double[] x, double[] step, double alpha, double[] tmpX, double[] tmpG) {
        for (int i = 0; i < x.length; i++) tmpX[i] = x[i] + alpha * step[i];
        return f.eval(tmpX, tmpG);
    }

    private static void derivePattern(ExactJumpModel model, JumpPhysicsInputs sc, double[] thetaRad,
                                      boolean[] outX, boolean[] outZ) {
        int n = sc.numTicks;
        double[] gf = sc.toGameFacings(Angles.wrapAll(toDeg(thetaRad)));
        ForwardPath path = model.forward(sc, gf);
        boolean perAxis = model.perAxisInertia();
        double thr = model.inertiaThreshold();
        for (int t = 0; t < n; t++) {
            if (perAxis) {
                outX[t] = Math.abs(path.velX[t]) < thr;
                outZ[t] = Math.abs(path.velZ[t]) < thr;
            } else {
                double vx = path.velX[t];
                double vz = path.velZ[t];
                boolean z = vx * vx + vz * vz < COMBINED_INERTIA_SQ;
                outX[t] = z;
                outZ[t] = z;
            }
        }
    }

    private static boolean samePattern(boolean[] ax, boolean[] az, boolean[] bx, boolean[] bz) {
        for (int i = 0; i < ax.length; i++) {
            if (ax[i] != bx[i] || az[i] != bz[i]) return false;
        }
        return true;
    }

    private static void gradCheck(SmoothJumpProblem p, double[] theta, double[] lamb, double[] nu, double pen,
                                  Counters counters) {
        int n = theta.length;
        double[] analytic = new double[n];
        p.smoothGradient(theta, lamb, nu, pen, analytic);
        double h = 1.0e-6;
        double[] tmp = new double[n];
        double worst = 0.0;
        for (int k = 0; k < n; k++) {
            double save = theta[k];
            theta[k] = save + h;
            double vp = p.augLagrangian(theta, lamb, nu, pen, tmp);
            theta[k] = save - h;
            double vm = p.augLagrangian(theta, lamb, nu, pen, tmp);
            theta[k] = save;
            double num = (vp - vm) / (2.0 * h);
            double denom = Math.max(1.0, Math.abs(num));
            worst = Math.max(worst, Math.abs(num - analytic[k]) / denom);
        }
        if (worst > 1.0e-6) {
            counters.gradCheckFail++;
            System.out.println("[DBG-bfgs1] grad_check_fail relErr=" + worst);
        }
    }

    private static void gradCheckT(SmoothJumpProblem p, double[] x, double tx, double tz, double[] lamb,
                                   double[] nu, double pen, Counters counters) {
        int dim = x.length;
        int n = dim - 2;
        double[] analytic = new double[dim];
        p.smoothGradientT(x, tx, tz, lamb, nu, pen, analytic);
        double h = 1.0e-6;
        double[] tmp = new double[dim];
        double worst = 0.0;
        for (int k = 0; k < dim; k++) {
            double save = x[k];
            x[k] = save + h;
            double vp = p.augLagrangianT(x, x[n], x[n + 1], lamb, nu, pen, tmp);
            x[k] = save - h;
            double vm = p.augLagrangianT(x, x[n], x[n + 1], lamb, nu, pen, tmp);
            x[k] = save;
            double num = (vp - vm) / (2.0 * h);
            double denom = Math.max(1.0, Math.abs(num));
            worst = Math.max(worst, Math.abs(num - analytic[k]) / denom);
        }
        if (worst > 1.0e-6) {
            counters.gradCheckFail++;
            System.out.println("[DBG-bfgs1] grad_check_fail(T) relErr=" + worst);
        }
    }

    private static double[] toDeg(double[] thetaRad) {
        double[] d = new double[thetaRad.length];
        for (int i = 0; i < thetaRad.length; i++) d[i] = Math.toDegrees(thetaRad[i]);
        return d;
    }

    private static void wrapInPlace(double[] theta) {
        double twoPi = 2.0 * Math.PI;
        for (int i = 0; i < theta.length; i++) {
            double d = theta[i] % twoPi;
            if (d > Math.PI) d -= twoPi;
            if (d <= -Math.PI) d += twoPi;
            theta[i] = d;
        }
    }

    private static void wrapX(double[] x, int n, boolean translate, double[] dom) {
        if (!translate) {
            wrapInPlace(x);
            return;
        }
        double twoPi = 2.0 * Math.PI;
        for (int i = 0; i < n; i++) {
            double d = x[i] % twoPi;
            if (d > Math.PI) d -= twoPi;
            if (d <= -Math.PI) d += twoPi;
            x[i] = d;
        }
        x[n] = clampDom(x[n], dom[0], dom[1]);
        x[n + 1] = clampDom(x[n + 1], dom[2], dom[3]);
    }

    private static double clampDom(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static boolean out(long deadlineNanos, AtomicBoolean cancel) {
        if (cancel != null && cancel.get()) return true;
        return deadlineNanos > 0 && System.nanoTime() >= deadlineNanos;
    }

    private static double[][] identity(int n) {
        double[][] m = new double[n][n];
        for (int i = 0; i < n; i++) m[i][i] = 1.0;
        return m;
    }

    private static double[] matVec(double[][] m, double[] v) {
        int n = v.length;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double s = 0.0;
            double[] row = m[i];
            for (int j = 0; j < n; j++) s += row[j] * v[j];
            out[i] = s;
        }
        return out;
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    private static void addSymOuter(double[][] m, double[] a, double[] b, double scale) {
        int n = a.length;
        for (int i = 0; i < n; i++) {
            double ai = a[i];
            double bi = b[i];
            double[] row = m[i];
            for (int j = 0; j < n; j++) {
                row[j] += scale * (ai * b[j] + a[j] * bi);
            }
        }
    }

    private static void addOuter(double[][] m, double[] a, double[] b, double scale) {
        int n = a.length;
        for (int i = 0; i < n; i++) {
            double ai = a[i];
            double[] row = m[i];
            for (int j = 0; j < n; j++) {
                row[j] += scale * ai * b[j];
            }
        }
    }
}
