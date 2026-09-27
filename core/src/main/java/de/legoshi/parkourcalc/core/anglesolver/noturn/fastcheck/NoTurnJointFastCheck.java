package de.legoshi.parkourcalc.core.anglesolver.noturn.fastcheck;

import de.legoshi.parkourcalc.core.anglesolver.noturn.FastCheck;
import de.legoshi.parkourcalc.core.anglesolver.noturn.FastCheckVerdict;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.McSineTable;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.anglesolver.solver.SurfaceKind;
import de.legoshi.parkourcalc.core.anglesolver.solver.WorkDeadline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NoTurnJointFastCheck implements FastCheck {

    private static final double BUCKET_RAD = 2.0 * Math.PI / McSineTable.SIZE;
    private static final double PAIR_DEV = 2.0 * BUCKET_RAD;
    private static final double EPS_TRIG = 2.5e-4;
    private static final double DBL_EPS = 1.0e-9;
    private static final double FLOAT_REL = 64.0 / 16777216.0;
    private static final double R_SIGNIF = 1.0e-3;
    private static final double TIE_EPS = 1.0e-3;
    private static final double LP_FEAS_TOL = 1.0e-7;
    private static final int KGON = 12;
    private static final double COARSE_DEG = 3.0;
    private static final double MIN_DEG = 0.02;

    private static final class WallLin {
        int axis;
        int tick;
        int cmp;
        double rhs;
        double cst;
        double aCoef;
        double bCoef;
        int[] freeVar;
        double[] freeCoef;
        double[] absCoef;
        double baseEps;
    }

    private static final class Model {
        int lastTick;
        double thr;
        boolean perAxis;
        double[] f4;
        double[] fPre;
        double[] mmag;
        double[] errR;
        double[] freeR;
        double[][] va = new double[2][];
        double[][] vb = new double[2][];
        double[] v0 = new double[2];
        double[][] rem = new double[2][];
        int[] freeVarOfTick;
        int F;
        int nVar;
        boolean[] signif;
        double sxLo, sxHi, szLo, szHi;
        double[] kcos = new double[KGON];
        double[] ksin = new double[KGON];
        double apo;
        List<WallLin> wl = new ArrayList<>();
        double[] epsFull;
        double[] epsBase;
        double[] epsCase;
    }

    @Override
    public FastCheckVerdict check(NoTurnProblem problem, JumpSpec spec, ExactJumpModel model, long budgetNanos,
                                  AtomicBoolean cancel) {
        WorkDeadline deadline = WorkDeadline.in(WorkDeadline.Clock.THREAD_CPU, budgetNanos);
        JumpPhysicsInputs sc = spec.asScenario();
        int n = sc.numTicks;

        for (JumpConstraint c : spec.constraints) {
            if (c.mode == JumpConstraint.Mode.DXZ || c.mode == JumpConstraint.Mode.DZX) {
                return FastCheckVerdict.unknown("cross-axis wall unsupported");
            }
        }

        List<JumpConstraint> walls = new ArrayList<>();
        int lastTick = 0;
        for (JumpConstraint c : spec.constraints) {
            if (NoTurnProblem.isFlat(c)) {
                if (c.t1 < 0 || c.t1 > n) return FastCheckVerdict.unknown("wall tick out of range");
                walls.add(c);
                lastTick = Math.max(lastTick, c.t1);
            }
        }
        if (walls.isEmpty()) return FastCheckVerdict.unknown("no axis walls");
        for (int t = 0; t < lastTick; t++) {
            if (sc.surfaceAt(t) != SurfaceKind.NORMAL) return FastCheckVerdict.unknown("surface unsupported");
        }
        StartBox box = sc.startBox;
        if (box != null && box.velocityFree()) return FastCheckVerdict.unknown("free start velocity unsupported");

        Map<Long, double[]> linkBounds = new HashMap<>();
        for (JumpConstraint c : spec.constraints) {
            if (c.mode != JumpConstraint.Mode.F || c.t2 == null || c.op != JumpConstraint.Op.MINUS) continue;
            if (c.t1 < 0 || c.t1 >= n || c.t2 < 0 || c.t2 >= n || c.t1 == c.t2) continue;
            int lo = Math.min(c.t1, c.t2), hi = Math.max(c.t1, c.t2);
            double sign = c.t1 == hi ? 1.0 : -1.0;
            double[] b = linkBounds.computeIfAbsent((long) lo * n + hi,
                    k -> new double[]{Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY});
            double v = sign * c.rhs;
            boolean upper = c.cmp == JumpConstraint.Cmp.EQ || (c.cmp == JumpConstraint.Cmp.LE) == (sign > 0);
            boolean lower = c.cmp == JumpConstraint.Cmp.EQ || (c.cmp == JumpConstraint.Cmp.GE) == (sign > 0);
            if (upper) b[1] = Math.min(b[1], v);
            if (lower) b[0] = Math.max(b[0], v);
        }
        int[] parent = new int[n];
        for (int t = 0; t < n; t++) parent[t] = t;
        List<int[]> links = new ArrayList<>();
        List<Double> bands = new ArrayList<>();
        for (Map.Entry<Long, double[]> e : linkBounds.entrySet()) {
            double[] b = e.getValue();
            if (!(b[0] >= -TIE_EPS && b[1] <= TIE_EPS && b[0] <= b[1])) continue;
            int hi = (int) (e.getKey() % n);
            int lo = (int) (e.getKey() / n);
            union(parent, lo, hi);
            links.add(new int[]{lo, hi});
            bands.add(Math.max(Math.abs(b[0]), Math.abs(b[1])));
        }
        int[] size = new int[n];
        for (int t = 0; t < n; t++) size[find(parent, t)]++;
        int gRoot = -1;
        for (int t = 0; t < n; t++) {
            int r = find(parent, t);
            if (gRoot < 0 || size[r] > size[gRoot]) gRoot = r;
        }
        if (gRoot < 0 || size[gRoot] < 2) return FastCheckVerdict.unknown("no tied run-up group");
        boolean[] inG = new boolean[n];
        for (int t = 0; t < n; t++) inG[t] = find(parent, t) == gRoot;
        double bandDeg = 0.0;
        for (int i = 0; i < links.size(); i++) if (inG[links.get(i)[0]]) bandDeg += bands.get(i);
        double tiedDev = bandDeg > 0.0 ? Angles.rad(bandDeg) + PAIR_DEV : 0.0;

        JumpLinearModel lm = new JumpLinearModel(sc, null, null, true);
        double[] q = new double[n];
        double[] p = new double[n];
        double[] mmag = new double[n];
        double[] errIn = new double[n];
        for (int t = 0; t < n; t++) {
            q[t] = lm.strafeMag(t);
            p[t] = lm.forwardMag(t) + lm.boostAt(t);
            mmag[t] = lm.mMag(t);
            double e = mmag[t] * FLOAT_REL;
            if (lm.boostAt(t) > 0.0) e += lm.boostAt(t) * PAIR_DEV;
            if (inG[t]) e += (Math.abs(q[t]) + Math.abs(p[t])) * tiedDev;
            errIn[t] = e;
        }

        Model m = new Model();
        m.lastTick = lastTick;
        m.perAxis = model.perAxisInertia();
        m.thr = m.perAxis ? model.inertiaThreshold() : model.combinedInertiaBound();
        m.mmag = mmag;
        m.f4 = new double[lastTick];
        m.fPre = new double[lastTick + 1];
        m.errR = new double[lastTick + 1];
        m.freeR = new double[lastTick + 1];
        for (int a = 0; a < 2; a++) {
            m.va[a] = new double[lastTick + 1];
            m.vb[a] = new double[lastTick + 1];
            m.rem[a] = new double[lastTick];
        }
        m.v0[0] = sc.initialVelocity.x;
        m.v0[1] = sc.initialVelocity.z;
        m.fPre[0] = 1.0;
        for (int t = 0; t < lastTick; t++) {
            double f = lm.friction(t);
            m.f4[t] = f;
            m.fPre[t + 1] = m.fPre[t] * f;
            boolean g = inG[t];
            m.va[0][t + 1] = f * (m.va[0][t] + (g ? q[t] : 0.0));
            m.vb[0][t + 1] = f * (m.vb[0][t] + (g ? -p[t] : 0.0));
            m.va[1][t + 1] = f * (m.va[1][t] + (g ? p[t] : 0.0));
            m.vb[1][t + 1] = f * (m.vb[1][t] + (g ? q[t] : 0.0));
            m.freeR[t + 1] = f * (m.freeR[t] + (g ? 0.0 : mmag[t]));
            m.errR[t + 1] = f * (m.errR[t] + errIn[t]);
        }

        m.freeVarOfTick = new int[n];
        double[] freeMaxReach = new double[n];
        for (int t = 0; t < n; t++) m.freeVarOfTick[t] = -1;
        int F = 0;
        for (int s = 0; s < lastTick; s++) {
            if (inG[s]) continue;
            for (JumpConstraint c : walls) {
                double reach = Math.abs(lm.coef(s, c.t1)) * mmag[s];
                if (reach > freeMaxReach[s]) freeMaxReach[s] = reach;
            }
            if (freeMaxReach[s] > 0.0) m.freeVarOfTick[s] = F++;
        }
        m.F = F;
        m.nVar = 4 + 2 * F;
        m.signif = new boolean[F];
        for (int s = 0; s < lastTick; s++) {
            if (m.freeVarOfTick[s] >= 0) m.signif[m.freeVarOfTick[s]] = freeMaxReach[s] > R_SIGNIF;
        }

        double p0x = box != null ? box.px : sc.startPos.x;
        double p0z = box != null ? box.pz : sc.startPos.z;
        if (box != null) {
            m.sxLo = box.pxLo - p0x;
            m.sxHi = box.pxHi - p0x;
            m.szLo = box.pzLo - p0z;
            m.szHi = box.pzHi - p0z;
        }

        for (JumpConstraint c : walls) {
            int axis = c.mode == JumpConstraint.Mode.X ? 0 : 1;
            int tick = c.t1;
            WallLin w = new WallLin();
            w.axis = axis;
            w.tick = tick;
            w.cmp = c.cmp == JumpConstraint.Cmp.LE ? 1 : c.cmp == JumpConstraint.Cmp.GE ? -1 : 0;
            w.rhs = c.rhs;
            w.cst = (axis == 0 ? p0x : p0z) + m.v0[axis] * lm.coef(0, tick);
            double aC = 0.0, bC = 0.0;
            double base = DBL_EPS;
            w.absCoef = new double[tick];
            List<Integer> fv = new ArrayList<>();
            List<Double> fc = new ArrayList<>();
            for (int s = 0; s < tick; s++) {
                double cf = lm.coef(s, tick);
                w.absCoef[s] = Math.abs(cf);
                base += Math.abs(cf) * errIn[s];
                if (inG[s]) {
                    if (axis == 0) {
                        aC += cf * q[s];
                        bC += cf * (-p[s]);
                    } else {
                        aC += cf * p[s];
                        bC += cf * q[s];
                    }
                } else if (m.freeVarOfTick[s] >= 0 && cf * mmag[s] != 0.0) {
                    fv.add(m.freeVarOfTick[s]);
                    fc.add(cf * mmag[s]);
                }
            }
            w.aCoef = aC;
            w.bCoef = bC;
            w.baseEps = base;
            w.freeVar = new int[fv.size()];
            w.freeCoef = new double[fv.size()];
            for (int i = 0; i < fv.size(); i++) {
                w.freeVar[i] = fv.get(i);
                w.freeCoef[i] = fc.get(i);
            }
            m.wl.add(w);
        }
        int W = m.wl.size();
        m.epsFull = new double[W];
        m.epsBase = new double[W];
        m.epsCase = new double[W];
        for (int i = 0; i < W; i++) m.epsBase[i] = m.wl.get(i).baseEps;

        m.apo = 1.0 / StrictMath.cos(Math.PI / KGON);
        for (int j = 0; j < KGON; j++) {
            double al = j * (2.0 * Math.PI / KGON);
            m.kcos[j] = StrictMath.cos(al);
            m.ksin[j] = StrictMath.sin(al);
        }

        double[] stack = new double[4096];
        int sp = 0;
        int coarse = (int) Math.ceil(360.0 / COARSE_DEG);
        int prefilterPass = 0;
        for (int i = coarse - 1; i >= 0; i--) {
            double lo = -180.0 + i * COARSE_DEG;
            double hi = lo + COARSE_DEG;
            double[] ab = inflatedArcBox(lo, hi);
            clampSlack(m, ab);
            if (prefilterBox(m, ab)) {
                stack[sp++] = lo;
                stack[sp++] = hi;
                prefilterPass++;
            }
        }

        int lpCalls = 0;
        int splits = 0;
        boolean feasibleRemains = false;
        while (sp > 0) {
            if ((lpCalls & 3) == 0 && deadline.over()) {
                return FastCheckVerdict.unknown("budget: lp=" + lpCalls + " pref=" + prefilterPass);
            }
            double hi = stack[--sp];
            double lo = stack[--sp];
            lpCalls++;
            double[] ab = inflatedArcBox(lo, hi);
            clampSlack(m, ab);
            if (!lpSolve(m, ab, lo, hi, m.epsFull, -1, -1)) continue;
            if (hasClampSlack(m)) {
                lpCalls++;
                if (!lpSolve(m, ab, lo, hi, m.epsBase, -1, -1)) {
                    splits++;
                    int[] calls = new int[1];
                    boolean empty = firstFiringCasesEmpty(m, ab, lo, hi, calls, deadline);
                    lpCalls += calls[0];
                    if (empty) continue;
                    if (deadline.over()) return FastCheckVerdict.unknown("budget: lp=" + lpCalls + " pref=" + prefilterPass);
                }
            }
            if (hi - lo <= MIN_DEG) {
                feasibleRemains = true;
                break;
            }
            double mid = 0.5 * (lo + hi);
            if (sp + 4 <= stack.length) {
                stack[sp++] = lo;
                stack[sp++] = mid;
                stack[sp++] = mid;
                stack[sp++] = hi;
            } else {
                feasibleRemains = true;
                break;
            }
        }

        if (feasibleRemains) {
            return FastCheckVerdict.unknown("relaxation feasible: lp=" + lpCalls + " pref=" + prefilterPass
                    + " splits=" + splits);
        }
        return FastCheckVerdict.infeasible("joint relaxation empty: lp=" + lpCalls + " pref=" + prefilterPass
                + " splits=" + splits + " F=" + F + " walls=" + W);
    }

    private static boolean hasClampSlack(Model m) {
        for (int i = 0; i < m.epsFull.length; i++) if (m.epsFull[i] > m.epsBase[i]) return true;
        return false;
    }

    private static boolean firstFiringCasesEmpty(Model m, double[] ab, double lo, double hi, int[] calls,
                                                 WorkDeadline deadline) {
        if (!m.perAxis) {
            for (int t = -1; t < m.lastTick; t++) {
                if (t >= 0 && !(m.rem[0][t] > 0.0)) continue;
                caseEps(m, 0, t);
                caseEps(m, 1, t);
                calls[0]++;
                if (lpSolve(m, ab, lo, hi, m.epsCase, t, t)) return false;
                if ((calls[0] & 3) == 0 && deadline.over()) return false;
            }
            return true;
        }
        for (int a = 0; a < 2; a++) {
            boolean alive = false;
            for (int t = -1; t < m.lastTick && !alive; t++) {
                if (t >= 0 && !(m.rem[a][t] > 0.0)) continue;
                caseEps(m, a, t);
                for (int i = 0; i < m.wl.size(); i++) if (m.wl.get(i).axis != a) m.epsCase[i] = m.epsFull[i];
                calls[0]++;
                alive = lpSolve(m, ab, lo, hi, m.epsCase, a == 0 ? t : -1, a == 0 ? -1 : t);
                if ((calls[0] & 3) == 0 && deadline.over()) return false;
            }
            if (!alive) return true;
        }
        return false;
    }

    private static void caseEps(Model m, int axis, int t) {
        for (int i = 0; i < m.wl.size(); i++) {
            WallLin w = m.wl.get(i);
            if (w.axis != axis) continue;
            double s = 0.0;
            if (t >= 0) {
                double[] rem = m.rem[axis];
                for (int u = t; u < w.tick; u++) s += w.absCoef[u] * rem[u];
            }
            m.epsCase[i] = w.baseEps + s;
        }
    }

    private static void clampSlack(Model m, double[] ab) {
        double aLo = ab[0], aHi = ab[1], bLo = ab[2], bHi = ab[3];
        double thr = m.thr;
        double cx = 0.0, cz = 0.0;
        double lbx = 0.0, ubx = 0.0, lbz = 0.0, ubz = 0.0;
        for (int t = 0; t < m.lastTick; t++) {
            for (int a = 0; a < 2; a++) {
                double va = m.va[a][t], vb = m.vb[a][t];
                double base = m.v0[a] * m.fPre[t];
                double rad = m.freeR[t] + m.errR[t] + (a == 0 ? cx : cz);
                double lo = base + va * (va > 0 ? aLo : aHi) + vb * (vb > 0 ? bLo : bHi) - rad;
                double hi = base + va * (va > 0 ? aHi : aLo) + vb * (vb > 0 ? bHi : bLo) + rad;
                double lb = (lo <= 0.0 && hi >= 0.0) ? 0.0 : Math.min(Math.abs(lo), Math.abs(hi));
                double ub = Math.max(Math.abs(lo), Math.abs(hi));
                if (a == 0) {
                    lbx = lb;
                    ubx = ub;
                } else {
                    lbz = lb;
                    ubz = ub;
                }
            }
            boolean fireX, fireZ;
            if (m.perAxis) {
                fireX = lbx < thr;
                fireZ = lbz < thr;
            } else {
                boolean fire = lbx < thr && lbz < thr;
                fireX = fire;
                fireZ = fire;
            }
            double rx = fireX ? Math.min(thr, ubx) : 0.0;
            double rz = fireZ ? Math.min(thr, ubz) : 0.0;
            m.rem[0][t] = rx;
            m.rem[1][t] = rz;
            cx = m.f4[t] * (cx + rx);
            cz = m.f4[t] * (cz + rz);
        }
        for (int i = 0; i < m.wl.size(); i++) {
            WallLin w = m.wl.get(i);
            double[] rem = m.rem[w.axis];
            double s = 0.0;
            for (int t = 0; t < w.tick; t++) s += w.absCoef[t] * rem[t];
            m.epsFull[i] = w.baseEps + s;
        }
    }

    private static boolean prefilterBox(Model m, double[] ab) {
        double aLo = ab[0], aHi = ab[1], bLo = ab[2], bHi = ab[3];
        double needLoX = m.sxLo, needHiX = m.sxHi, needLoZ = m.szLo, needHiZ = m.szHi;
        for (int i = 0; i < m.wl.size(); i++) {
            WallLin w = m.wl.get(i);
            double eps = m.epsFull[i];
            double setMin = w.aCoef * (w.aCoef > 0 ? aLo : aHi) + w.bCoef * (w.bCoef > 0 ? bLo : bHi);
            double setMax = w.aCoef * (w.aCoef > 0 ? aHi : aLo) + w.bCoef * (w.bCoef > 0 ? bHi : bLo);
            double R = 0.0;
            for (int k = 0; k < w.freeVar.length; k++) R += Math.abs(w.freeCoef[k]);
            R *= 1.0 + EPS_TRIG;
            if (w.cmp >= 0) {
                double shiftHi = w.rhs + eps - w.cst - setMin + R;
                if (w.axis == 0) needHiX = Math.min(needHiX, shiftHi);
                else needHiZ = Math.min(needHiZ, shiftHi);
            }
            if (w.cmp <= 0) {
                double shiftLo = w.rhs - eps - w.cst - setMax - R;
                if (w.axis == 0) needLoX = Math.max(needLoX, shiftLo);
                else needLoZ = Math.max(needLoZ, shiftLo);
            }
        }
        return needLoX <= needHiX && needLoZ <= needHiZ;
    }

    private static boolean lpSolve(Model m, double[] ab, double loDeg, double hiDeg, double[] eps,
                                   int velTickX, int velTickZ) {
        int nVar = m.nVar;
        int wallRows = 0;
        for (WallLin w : m.wl) wallRows += (w.cmp == 0 ? 2 : 1);
        int kgonRows = 0;
        for (int k = 0; k < m.F; k++) if (m.signif[k]) kgonRows += KGON;
        int maxRows = wallRows + kgonRows + 8;
        LpFeas lp = new LpFeas(nVar, maxRows);

        double loRad = Angles.rad(loDeg);
        double hiRad = Angles.rad(hiDeg);
        lp.setBound(0, ab[0], ab[1]);
        lp.setBound(1, ab[2], ab[3]);
        lp.setBound(2, m.sxLo, m.sxHi);
        lp.setBound(3, m.szLo, m.szHi);
        double eb = 1.0 + EPS_TRIG;
        for (int k = 0; k < m.F; k++) {
            lp.setBound(4 + 2 * k, -eb, eb);
            lp.setBound(5 + 2 * k, -eb, eb);
        }

        double thm = 0.5 * (loRad + hiRad);
        double h = 0.5 * (hiRad - loRad);
        double cm = StrictMath.cos(thm), sm = StrictMath.sin(thm);
        double cosh = StrictMath.cos(h);
        double sinh = StrictMath.sin(h);
        double[] r1 = new double[nVar];
        r1[0] = cm; r1[1] = sm;
        lp.addRow(r1, 1.0 + EPS_TRIG);
        double[] r2 = new double[nVar];
        r2[0] = -cm; r2[1] = -sm;
        lp.addRow(r2, -(cosh - EPS_TRIG));
        double[] r3 = new double[nVar];
        r3[0] = -sm; r3[1] = cm;
        lp.addRow(r3, sinh + EPS_TRIG);
        double[] r4 = new double[nVar];
        r4[0] = sm; r4[1] = -cm;
        lp.addRow(r4, sinh + EPS_TRIG);

        for (int i = 0; i < m.wl.size(); i++) {
            WallLin w = m.wl.get(i);
            double e = eps[i];
            int shiftVar = w.axis == 0 ? 2 : 3;
            if (w.cmp >= 0) {
                double[] a = new double[nVar];
                a[0] = w.aCoef;
                a[1] = w.bCoef;
                a[shiftVar] += 1.0;
                for (int k = 0; k < w.freeVar.length; k++) a[4 + 2 * w.freeVar[k] + w.axis] += w.freeCoef[k];
                lp.addRow(a, w.rhs + e - w.cst);
            }
            if (w.cmp <= 0) {
                double[] a = new double[nVar];
                a[0] = -w.aCoef;
                a[1] = -w.bCoef;
                a[shiftVar] += -1.0;
                for (int k = 0; k < w.freeVar.length; k++) a[4 + 2 * w.freeVar[k] + w.axis] += -w.freeCoef[k];
                lp.addRow(a, -(w.rhs - e - w.cst));
            }
        }

        for (int axis = 0; axis < 2; axis++) {
            int velTick = axis == 0 ? velTickX : velTickZ;
            if (velTick < 0) continue;
            double[] a = new double[nVar];
            a[0] = m.va[axis][velTick];
            a[1] = m.vb[axis][velTick];
            for (int s = 0; s < velTick; s++) {
                int v = m.freeVarOfTick[s];
                if (v < 0) continue;
                a[4 + 2 * v + axis] += m.fPre[velTick] / m.fPre[s] * m.mmag[s];
            }
            double c0 = m.v0[axis] * m.fPre[velTick];
            double bound = m.thr + m.errR[velTick] + DBL_EPS;
            lp.addRow(a, bound - c0);
            double[] neg = new double[nVar];
            for (int v = 0; v < nVar; v++) neg[v] = -a[v];
            lp.addRow(neg, bound + c0);
        }

        for (int k = 0; k < m.F; k++) {
            if (!m.signif[k]) continue;
            int ex = 4 + 2 * k, ez = 5 + 2 * k;
            for (int j = 0; j < KGON; j++) {
                double[] a = new double[nVar];
                a[ex] = m.kcos[j];
                a[ez] = m.ksin[j];
                lp.addRow(a, m.apo + EPS_TRIG);
            }
        }

        return lp.feasible(LP_FEAS_TOL);
    }

    private static double[] inflatedArcBox(double loDeg, double hiDeg) {
        double lo = Angles.rad(loDeg);
        double hi = Angles.rad(hiDeg);
        double aMin = Math.min(StrictMath.cos(lo), StrictMath.cos(hi));
        double aMax = Math.max(StrictMath.cos(lo), StrictMath.cos(hi));
        double bMin = Math.min(StrictMath.sin(lo), StrictMath.sin(hi));
        double bMax = Math.max(StrictMath.sin(lo), StrictMath.sin(hi));
        for (int k = -4; k <= 4; k++) {
            double crit = k * (Math.PI / 2.0);
            if (crit > lo - 1e-12 && crit < hi + 1e-12) {
                double c = StrictMath.cos(crit), s = StrictMath.sin(crit);
                if (c < aMin) aMin = c;
                if (c > aMax) aMax = c;
                if (s < bMin) bMin = s;
                if (s > bMax) bMax = s;
            }
        }
        return new double[]{aMin - EPS_TRIG, aMax + EPS_TRIG, bMin - EPS_TRIG, bMax + EPS_TRIG};
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }

    @Override
    public String describe() {
        return "NoTurnJointFastCheck";
    }
}
