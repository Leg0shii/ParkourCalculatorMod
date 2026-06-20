package de.legoshi.parkourcalc.core.anglesolver.velocity;

import de.legoshi.parkourcalc.core.anglesolver.Constraint;

import java.util.List;

public final class LandingPad {

    private LandingPad() {
    }

    public static double[] derive(List<Constraint> landingTickConstraints, double[] baseBox) {
        double[] box = baseBox.clone();
        if (landingTickConstraints == null) return box;
        double[] xb = {Double.NaN, Double.NaN};
        double[] zb = {Double.NaN, Double.NaN};
        boolean any = false;
        for (Constraint c : landingTickConstraints) {
            if (!c.isEnabled()) continue;
            if (c.getField() == Constraint.Field.X) {
                accumulate(xb, c);
                any = true;
            } else if (c.getField() == Constraint.Field.Z) {
                accumulate(zb, c);
                any = true;
            }
        }
        if (!any) return box;
        if (!Double.isNaN(xb[0])) box[0] = xb[0];
        if (!Double.isNaN(xb[1])) box[1] = xb[1];
        if (!Double.isNaN(zb[0])) box[2] = zb[0];
        if (!Double.isNaN(zb[1])) box[3] = zb[1];
        return box;
    }

    private static void accumulate(double[] b, Constraint c) {
        if (c.isRange()) {
            b[0] = Double.isNaN(b[0]) ? c.getLo() : Math.max(b[0], c.getLo());
            b[1] = Double.isNaN(b[1]) ? c.getHi() : Math.min(b[1], c.getHi());
            return;
        }
        double v = c.getValue();
        switch (c.getOp()) {
            case EQ:
                b[0] = Double.isNaN(b[0]) ? v : Math.max(b[0], v);
                b[1] = Double.isNaN(b[1]) ? v : Math.min(b[1], v);
                break;
            case GT:
            case GE:
                b[0] = Double.isNaN(b[0]) ? v : Math.max(b[0], v);
                break;
            case LT:
            case LE:
                b[1] = Double.isNaN(b[1]) ? v : Math.min(b[1], v);
                break;
            default:
                break;
        }
    }
}
