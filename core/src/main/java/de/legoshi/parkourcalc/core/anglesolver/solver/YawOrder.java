package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class YawOrder {

    public static final double PIN_HALF_WIDTH = 1.0e-4;
    public static final double FLOAT_MARGIN_DEG = 3.0e-5;
    public static final double TIE_TOL = 1.5 * YawOrderDp.H;

    public static final class Link {
        public final int hiTick;
        public final int loTick;
        public final double minTurnDeg;
        public final JumpConstraint source;

        Link(int hiTick, int loTick, double minTurnDeg, JumpConstraint source) {
            this.hiTick = hiTick;
            this.loTick = loTick;
            this.minTurnDeg = minTurnDeg;
            this.source = source;
        }

        public int laterTick() {
            return Math.max(hiTick, loTick);
        }
    }

    public static final class Bound {
        public final int tick;
        public final double loDeg;
        public final double hiDeg;
        public final JumpConstraint source;

        Bound(int tick, double loDeg, double hiDeg, JumpConstraint source) {
            this.tick = tick;
            this.loDeg = loDeg;
            this.hiDeg = hiDeg;
            this.source = source;
        }
    }

    private final List<Link> links;
    private final List<Bound> bounds;
    private final List<JumpConstraint> carried;

    private YawOrder(List<Link> links, List<Bound> bounds, List<JumpConstraint> carried) {
        this.links = Collections.unmodifiableList(links);
        this.bounds = Collections.unmodifiableList(bounds);
        this.carried = Collections.unmodifiableList(carried);
    }

    public List<Link> links() {
        return links;
    }

    public List<Bound> bounds() {
        return bounds;
    }

    public List<JumpConstraint> carried() {
        return carried;
    }

    public boolean isEmpty() {
        return carried.isEmpty();
    }

    public static boolean absorbed(JumpConstraint c, YawTies ties) {
        if (ties == null) return false;
        if (c.t2 == null) return ties.varOf(c.t1) < 0;
        return c.op == JumpConstraint.Op.MINUS && c.t2 >= 0 && c.t2 < c.t1
                && ties.groupOf(c.t1) == ties.groupOf(c.t2);
    }

    public static YawOrder carry(List<JumpConstraint> constraints, YawTies ties, int n) {
        List<Link> links = new ArrayList<>();
        List<Bound> bounds = new ArrayList<>();
        List<JumpConstraint> carried = new ArrayList<>();
        for (JumpConstraint c : constraints) {
            if (c.mode != JumpConstraint.Mode.F) continue;
            if (c.t1 < 0 || c.t1 >= n) continue;
            if (absorbed(c, ties)) continue;
            if (c.cmp == JumpConstraint.Cmp.EQ) return null;
            if (c.t2 == null) {
                double lo = c.cmp == JumpConstraint.Cmp.GE ? c.rhs : c.rhs - 180.0;
                double hi = c.cmp == JumpConstraint.Cmp.GE ? c.rhs + 180.0 : c.rhs;
                bounds.add(new Bound(c.t1, lo, hi, c));
                carried.add(c);
                continue;
            }
            if (c.op != JumpConstraint.Op.MINUS || c.t2 < 0 || c.t2 >= n || Math.abs(c.t1 - c.t2) != 1) return null;
            Link link = c.cmp == JumpConstraint.Cmp.GE ? new Link(c.t1, c.t2, c.rhs, c) : new Link(c.t2, c.t1, -c.rhs, c);
            for (Link other : links) {
                if (other.hiTick == link.loTick && other.loTick == link.hiTick) return null;
            }
            links.add(link);
            carried.add(c);
        }
        return new YawOrder(links, bounds, carried);
    }

    public static boolean accepts(List<JumpConstraint> constraints, YawTies ties, int n) {
        return carry(constraints, ties, n) != null;
    }

    public static double[] carryFreeTicks(JumpLinearModel lin, double startYaw, double[] yawsAbs) {
        double[] y = yawsAbs.clone();
        double prev = Angles.wrap(startYaw);
        for (int t = 0; t < y.length && t < lin.n; t++) {
            if (lin.mMag(t) <= 0.0) y[t] = prev;
            prev = y[t];
        }
        return y;
    }

    public List<JumpConstraint> stripped(List<JumpConstraint> constraints) {
        List<JumpConstraint> base = new ArrayList<JumpConstraint>(constraints.size());
        for (JumpConstraint c : constraints) {
            boolean isCarried = false;
            for (JumpConstraint k : carried) {
                if (k == c) {
                    isCarried = true;
                    break;
                }
            }
            if (!isCarried) base.add(c);
        }
        return base;
    }

    public List<JumpConstraint> withTies(List<JumpConstraint> base, boolean[] tie, boolean[] pinLo, boolean[] pinHi) {
        List<JumpConstraint> active = new ArrayList<JumpConstraint>(base);
        for (int l = 0; l < tie.length; l++) {
            if (!tie[l]) continue;
            JumpConstraint s = links.get(l).source;
            double at = s.rhs == 0.0 ? 0.0
                    : s.rhs + (s.cmp == JumpConstraint.Cmp.GE ? PIN_HALF_WIDTH : -PIN_HALF_WIDTH);
            active.add(new JumpConstraint(JumpConstraint.Mode.F, s.t1, s.t2, JumpConstraint.Op.MINUS,
                    JumpConstraint.Cmp.GE, at - PIN_HALF_WIDTH, s.name + "eqLo"));
            active.add(new JumpConstraint(JumpConstraint.Mode.F, s.t1, s.t2, JumpConstraint.Op.MINUS,
                    JumpConstraint.Cmp.LE, at + PIN_HALF_WIDTH, s.name + "eqHi"));
        }
        for (int b = 0; b < pinLo.length; b++) {
            if (!pinLo[b] && !pinHi[b]) continue;
            Bound bd = bounds.get(b);
            double edge = pinLo[b] ? bd.loDeg + PIN_HALF_WIDTH : bd.hiDeg - PIN_HALF_WIDTH;
            active.add(new JumpConstraint(JumpConstraint.Mode.F, bd.tick, null, JumpConstraint.Op.PLUS,
                    JumpConstraint.Cmp.GE, edge - PIN_HALF_WIDTH, bd.source.name + "eqLo"));
            active.add(new JumpConstraint(JumpConstraint.Mode.F, bd.tick, null, JumpConstraint.Op.PLUS,
                    JumpConstraint.Cmp.LE, edge + PIN_HALF_WIDTH, bd.source.name + "eqHi"));
        }
        return active;
    }

    public void structureOf(double[] yaws, boolean[] tie, boolean[] pinLo, boolean[] pinHi) {
        for (int l = 0; l < tie.length; l++) {
            Link link = links.get(l);
            if (link.laterTick() >= yaws.length) continue;
            double turn = Angles.wrapDelta(yaws[link.hiTick] - yaws[link.loTick]);
            tie[l] = turn <= link.minTurnDeg + TIE_TOL || turn >= 180.0 - TIE_TOL;
        }
        for (int b = 0; b < pinLo.length; b++) {
            Bound bd = bounds.get(b);
            if (bd.tick >= yaws.length) continue;
            double mid = 0.5 * (bd.loDeg + bd.hiDeg);
            double rel = mid + Angles.wrapDelta(yaws[bd.tick] - mid);
            pinLo[b] = rel <= bd.loDeg + TIE_TOL;
            pinHi[b] = !pinLo[b] && rel >= bd.hiDeg - TIE_TOL;
        }
    }

    public double[] project(double[] yawsAbs) {
        double[] y = yawsAbs.clone();
        for (Bound b : bounds) {
            if (b.tick >= y.length) continue;
            double mid = 0.5 * (b.loDeg + b.hiDeg);
            double rel = mid + Angles.wrapDelta(y[b.tick] - mid);
            if (rel < b.loDeg) rel = b.loDeg;
            if (rel > b.hiDeg) rel = b.hiDeg;
            y[b.tick] = Angles.wrap(rel);
        }
        for (int t = 1; t < y.length; t++) {
            for (Link l : links) {
                if (l.laterTick() != t) continue;
                double turn = Angles.wrapDelta(y[l.hiTick] - y[l.loTick]);
                double need = l.minTurnDeg == 0.0 ? 0.0 : l.minTurnDeg + FLOAT_MARGIN_DEG;
                if (turn >= need) continue;
                if (l.hiTick == t) y[t] = Angles.wrap(y[l.loTick] + need);
                else y[t] = Angles.wrap(y[l.hiTick] - need);
            }
        }
        return y;
    }

    public boolean satisfied(double[] yawsAbs, double tolDeg) {
        for (Bound b : bounds) {
            double mid = 0.5 * (b.loDeg + b.hiDeg);
            double rel = mid + Angles.wrapDelta(yawsAbs[b.tick] - mid);
            if (rel < b.loDeg - tolDeg || rel > b.hiDeg + tolDeg) return false;
        }
        for (Link l : links) {
            if (Angles.wrapDelta(yawsAbs[l.hiTick] - yawsAbs[l.loTick]) < l.minTurnDeg - tolDeg) return false;
        }
        return true;
    }
}
