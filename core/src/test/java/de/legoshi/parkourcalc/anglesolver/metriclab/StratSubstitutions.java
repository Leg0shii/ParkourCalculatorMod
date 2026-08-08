package de.legoshi.parkourcalc.anglesolver.metriclab;

import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratVariants;
import de.legoshi.parkourcalc.core.save.SaveFile;

import java.util.ArrayList;
import java.util.List;

public final class StratSubstitutions {

    public static final class Variant {
        public final String label;
        public final SaveFile save;

        Variant(String label, SaveFile save) {
            this.label = label;
            this.save = save;
        }
    }

    private StratSubstitutions() {
    }

    public static List<Variant> variants(SaveFile human, ExactJumpModel model) {
        List<StratVariants.Variant> vs = StratVariants.variants(human, model);
        List<Variant> out = new ArrayList<Variant>(vs.size());
        for (StratVariants.Variant v : vs) {
            out.add(new Variant(v.label, v.save));
        }
        return out;
    }
}
