package de.legoshi.parkourcalc.core.anglesolver;

import java.util.ArrayList;
import java.util.List;

public final class ConstraintConflict {

    private ConstraintConflict() {
    }

    public static String describe(int absTick, List<Constraint> constraints) {
        String at = "T" + (absTick + 1) + ": ";
        for (Constraint.Field field : Constraint.Field.values()) {
            if (field == Constraint.Field.RT || field == Constraint.Field.DF) continue;
            List<Constraint> same = new ArrayList<>();
            for (Constraint c : constraints) {
                if (!c.isEnabled() || c.getField() != field || c.isRelative() || c.isVsOther() || c.isVsDz()) continue;
                if (field == Constraint.Field.F && c.isRange()) continue;
                if (c.isRange() && empty(c.getLo(), c.isLoInclusive(), c.getHi(), c.isHiInclusive())) {
                    return at + text(c) + " is an empty range. Fix its bounds or delete it.";
                }
                same.add(c);
            }
            if (same.size() < 2) continue;
            Constraint[] pair = conflictingPair(same);
            if (pair != null) {
                return at + text(pair[0]) + " and " + text(pair[1])
                        + " cannot both hold. Change or delete one of them.";
            }
        }
        return null;
    }

    private static Constraint[] conflictingPair(List<Constraint> same) {
        double lo = Double.NEGATIVE_INFINITY;
        double hi = Double.POSITIVE_INFINITY;
        boolean loIncl = true;
        boolean hiIncl = true;
        Constraint loOwner = null;
        Constraint hiOwner = null;
        for (Constraint c : same) {
            double cLo = Double.NEGATIVE_INFINITY;
            double cHi = Double.POSITIVE_INFINITY;
            boolean cLoIncl = true;
            boolean cHiIncl = true;
            switch (c.getOp()) {
                case GT: cLo = c.getValue(); cLoIncl = false; break;
                case GE: cLo = c.getValue(); break;
                case LT: cHi = c.getValue(); cHiIncl = false; break;
                case LE: cHi = c.getValue(); break;
                case EQ: cLo = c.getValue(); cHi = c.getValue(); break;
                case IN: cLo = c.getLo(); cHi = c.getHi(); cLoIncl = c.isLoInclusive(); cHiIncl = c.isHiInclusive(); break;
                default: break;
            }
            if (hiOwner != null && empty(cLo, cLoIncl, hi, hiIncl)) return new Constraint[] {hiOwner, c};
            if (loOwner != null && empty(lo, loIncl, cHi, cHiIncl)) return new Constraint[] {loOwner, c};
            if (cLo > lo || (cLo == lo && !cLoIncl && loIncl)) {
                lo = cLo;
                loIncl = cLoIncl;
                loOwner = c;
            }
            if (cHi < hi || (cHi == hi && !cHiIncl && hiIncl)) {
                hi = cHi;
                hiIncl = cHiIncl;
                hiOwner = c;
            }
        }
        return null;
    }

    private static boolean empty(double lo, boolean loIncl, double hi, boolean hiIncl) {
        if (lo > hi) return true;
        return lo == hi && !(loIncl && hiIncl);
    }

    private static String text(Constraint c) {
        return c.getField().label + " " + c.getOp().glyph + " " + ConstraintText.chip(c);
    }
}
