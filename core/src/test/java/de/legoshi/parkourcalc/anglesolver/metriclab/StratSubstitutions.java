package de.legoshi.parkourcalc.anglesolver.metriclab;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class StratSubstitutions {

    private static final Gson GSON = new Gson();
    private static final List<String> KEYS = Arrays.asList("W", "SPRINT", "A", "D", "S");
    private static final int WINDOW = 12;
    private static final int MAX_VARIANTS = 40;

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
        List<Variant> out = new ArrayList<Variant>();
        JumpSpec spec;
        try {
            spec = MeasurementEngine.buildSpec(human, model);
        } catch (RuntimeException ex) {
            return out;
        }
        if (spec == null) {
            return out;
        }
        JumpPhysicsInputs sc = spec.asScenario();
        out.add(prepare("self", human, human, sc));

        int startTick = human.angleSolver.startTick;
        int landing = human.angleSolver.landingTick;
        Set<String> seen = new HashSet<String>();
        for (int j = startTick; j <= landing && j < human.rows.size(); j++) {
            if (!hasKey(human.rows.get(j), "JUMP")) {
                continue;
            }
            int end = Math.min(Math.min(j + WINDOW, landing), human.rows.size() - 1);
            for (String key : KEYS) {
                for (int r = j + 1; r <= end; r++) {
                    boolean here = hasKey(human.rows.get(r), key);
                    boolean before = hasKey(human.rows.get(r - 1), key);
                    boolean after = r + 1 < human.rows.size() && hasKey(human.rows.get(r + 1), key);
                    if (here && !before && after) {
                        addVariant(out, seen, human, sc, key + "@" + r + "later", r, key, false);
                        if (r - 1 > startTick || !hasKey(human.rows.get(r - 1), "JUMP")) {
                            addVariant(out, seen, human, sc, key + "@" + r + "earlier", r - 1, key, true);
                        }
                    }
                    if (here && before && !after && r < end) {
                        addVariant(out, seen, human, sc, key + "@" + r + "holdlonger", r + 1, key, true);
                        addVariant(out, seen, human, sc, key + "@" + r + "releaseearlier", r, key, false);
                    }
                    if (out.size() > MAX_VARIANTS) {
                        return out;
                    }
                }
            }
        }
        return out;
    }

    private static void addVariant(List<Variant> out, Set<String> seen, SaveFile human,
                                   JumpPhysicsInputs sc, String label, int row, String key, boolean add) {
        if (row < 0 || row >= human.rows.size()) {
            return;
        }
        SaveFile s = copy(human);
        TreeSet<String> ks = new TreeSet<String>(s.rows.get(row).keys);
        boolean changed = add ? ks.add(key) : ks.remove(key);
        if (!changed) {
            return;
        }
        s.rows.get(row).keys = new ArrayList<String>(ks);
        String sig = signature(s);
        if (!seen.add(sig)) {
            return;
        }
        out.add(prepare(label, s, human, sc));
    }

    private static Variant prepare(String label, SaveFile s, SaveFile human, JumpPhysicsInputs sc) {
        SaveFile v = s == human ? copy(human) : s;
        SimplifyLoop.deriveDebugSamples(v, sc);
        v.angleSolver.result = null;
        v.angleSolver.effort = "FAST";
        v.angleSolver.stopOnFeasible = Boolean.TRUE;
        return new Variant(label, v);
    }

    private static String signature(SaveFile s) {
        StringBuilder sb = new StringBuilder();
        for (SaveFile.Row r : s.rows) {
            sb.append(r.keys).append(';');
        }
        return sb.toString();
    }

    private static boolean hasKey(SaveFile.Row row, String key) {
        return row != null && row.keys != null && row.keys.contains(key);
    }

    private static SaveFile copy(SaveFile s) {
        return GSON.fromJson(GSON.toJson(s), SaveFile.class);
    }
}
