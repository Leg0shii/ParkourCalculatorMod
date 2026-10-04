package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.TurnReference;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.ui.InputData;
import de.legoshi.parkourcalc.core.ui.InputRow;

import java.util.List;

final class OnejumpRigs {

    private OnejumpRigs() {
    }

    static void flag(InputData inputs, int first, int last) {
        for (int t = first; t <= last; t++) {
            InputRow r = inputs.getRows().get(t);
            r.setOnejumpKeys(true);
            r.setOnejumpFace(InputRow.ONEJUMP_FACE_CHECK);
        }
    }

    static void landing(AngleSolverState state, int tasFirst, TurnReference.Landing l) {
        List<Constraint> list = state.tickConstraints(tasFirst + l.tick).getConstraints();
        list.removeIf(c -> c.getField() == Constraint.Field.X || c.getField() == Constraint.Field.Z);
        axis(list, Constraint.Field.X, l.xLo, l.xHi);
        axis(list, Constraint.Field.Z, l.zLo, l.zHi);
    }

    static void clearLandings(AngleSolverState state, int from, int to) {
        for (int tick : state.populatedTicks()) {
            if (tick < from || tick > to) continue;
            state.tickConstraints(tick).getConstraints()
                    .removeIf(c -> c.getField() == Constraint.Field.X || c.getField() == Constraint.Field.Z);
        }
    }

    private static void axis(List<Constraint> list, Constraint.Field field, double lo, double hi) {
        boolean hasLo = !Double.isNaN(lo);
        boolean hasHi = !Double.isNaN(hi);
        if (hasLo && hasHi) list.add(Constraint.range(field, lo, hi, true, true));
        else if (hasLo) list.add(Constraint.scalar(field, Constraint.Op.GE, lo));
        else if (hasHi) list.add(Constraint.scalar(field, Constraint.Op.LE, hi));
    }
}
