package de.legoshi.parkourcalc.core.anglesolver.runticks;

import de.legoshi.parkourcalc.core.anglesolver.Constraint;

import java.util.List;

public final class RunTicksFilter {

    private RunTicksFilter() {
    }

    public static boolean allows(List<Constraint> atJumpTick, int extraTicks) {
        if (atJumpTick == null) return true;
        for (Constraint c : atJumpTick) {
            if (c.getField() != Constraint.Field.RT || !c.isEnabled()) continue;
            if (!satisfies(c, extraTicks)) return false;
        }
        return true;
    }

    private static boolean satisfies(Constraint c, double found) {
        if (c.isRange()) {
            boolean lo = c.isLoInclusive() ? found >= c.getLo() : found > c.getLo();
            boolean hi = c.isHiInclusive() ? found <= c.getHi() : found < c.getHi();
            return lo && hi;
        }
        double target = c.getValue();
        switch (c.getOp()) {
            case GT: return found > target;
            case GE: return found >= target;
            case LT: return found < target;
            case LE: return found <= target;
            case EQ: return found == target;
            default: return true;
        }
    }
}
