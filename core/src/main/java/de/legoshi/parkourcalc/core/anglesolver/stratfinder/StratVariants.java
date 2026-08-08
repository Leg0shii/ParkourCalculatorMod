package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class StratVariants {

    public static final float KEY_INPUT_SCALE = 0.98F;
    public static final float SNEAK_INPUT_SCALE = 0.3F;

    private static final Gson GSON = new Gson();
    private static final List<String> KEYS = Arrays.asList("W", "SPRINT", "A", "D", "S");
    private static final int WINDOW = 12;
    private static final int MAX_VARIANTS = 40;

    public static final class Variant {
        public final String label;
        public final SaveFile save;
        public final int edits;

        Variant(String label, SaveFile save, int edits) {
            this.label = label;
            this.save = save;
            this.edits = edits;
        }
    }

    private StratVariants() {
    }

    public static List<Variant> variants(SaveFile witness, ExactJumpModel model) {
        List<Variant> out = new ArrayList<Variant>();
        JumpPhysicsInputs sc;
        try {
            sc = scenario(witness, model);
        } catch (RuntimeException ex) {
            return out;
        }
        if (sc == null) {
            return out;
        }
        Set<String> seen = new HashSet<String>();
        List<Variant> bases = new ArrayList<Variant>();
        Variant self = prepare("self", witness, witness, sc, 0);
        seen.add(signature(self.save));
        bases.add(self);
        collectTimingVariants(bases, seen, witness, sc);
        for (Variant base : bases) {
            out.add(base);
            addShapeVariants(out, seen, witness, sc, base);
        }
        return out;
    }

    private static void collectTimingVariants(List<Variant> bases, Set<String> seen,
                                              SaveFile witness, JumpPhysicsInputs sc) {
        int startTick = witness.angleSolver.startTick;
        int landing = witness.angleSolver.landingTick;
        for (int j = startTick; j <= landing && j < witness.rows.size(); j++) {
            if (!hasKey(witness.rows.get(j), "JUMP")) {
                continue;
            }
            int from = Math.max(Math.max(startTick, 0) + 1, j - WINDOW);
            int end = Math.min(Math.min(j + WINDOW, landing), witness.rows.size() - 1);
            for (String key : KEYS) {
                for (int r = from; r <= end; r++) {
                    boolean here = hasKey(witness.rows.get(r), key);
                    boolean before = hasKey(witness.rows.get(r - 1), key);
                    boolean after = r + 1 < witness.rows.size() && hasKey(witness.rows.get(r + 1), key);
                    boolean earlierAllowed = r - 1 > startTick || !hasKey(witness.rows.get(r - 1), "JUMP");
                    if (here && !before && after) {
                        addVariant(bases, seen, witness, sc, key + "@" + r + "later", r, key, false);
                        if (earlierAllowed) {
                            addVariant(bases, seen, witness, sc, key + "@" + r + "earlier", r - 1, key, true);
                        }
                    }
                    if (here && before && !after && r < end) {
                        addVariant(bases, seen, witness, sc, key + "@" + r + "holdlonger", r + 1, key, true);
                        addVariant(bases, seen, witness, sc, key + "@" + r + "releaseearlier", r, key, false);
                    }
                    if (here && !before && !after) {
                        if (r + 1 <= end) {
                            moveVariant(bases, seen, witness, sc, key + "@" + r + "taplater", r, r + 1, key);
                        }
                        if (r - 1 >= from && earlierAllowed) {
                            moveVariant(bases, seen, witness, sc, key + "@" + r + "tapearlier", r, r - 1, key);
                        }
                    }
                    if (bases.size() > MAX_VARIANTS) {
                        return;
                    }
                }
            }
        }
    }

    private static void moveVariant(List<Variant> out, Set<String> seen, SaveFile witness,
                                    JumpPhysicsInputs sc, String label, int rowFrom, int rowTo, String key) {
        if (rowFrom < 0 || rowFrom >= witness.rows.size() || rowTo < 0 || rowTo >= witness.rows.size()) {
            return;
        }
        SaveFile s = copy(witness);
        TreeSet<String> from = new TreeSet<String>(s.rows.get(rowFrom).keys);
        TreeSet<String> to = new TreeSet<String>(s.rows.get(rowTo).keys);
        if (!from.remove(key) || !to.add(key)) {
            return;
        }
        s.rows.get(rowFrom).keys = new ArrayList<String>(from);
        s.rows.get(rowTo).keys = new ArrayList<String>(to);
        String sig = signature(s);
        if (!seen.add(sig)) {
            return;
        }
        out.add(prepare(label, s, witness, sc, 1));
    }

    private static void addShapeVariants(List<Variant> out, Set<String> seen, SaveFile witness,
                                         JumpPhysicsInputs sc, Variant base) {
        int startTick = witness.angleSolver.startTick;
        int landing = witness.angleSolver.landingTick;
        if (landing <= startTick) {
            return;
        }
        int lastFire = lastJumpRow(base.save, startTick, landing);
        if (lastFire <= startTick) {
            return;
        }
        SaveFile nt = copy(base.save);
        for (int t = startTick + 1; t <= lastFire; t++) {
            addDfZero(nt, t);
        }
        addShaped(out, seen, shapeLabel(base.label, "nt"), nt, sc, base.edits);
        if (lastFire > startTick + 1) {
            SaveFile ja = copy(base.save);
            for (int t = startTick + 1; t < lastFire; t++) {
                addDfZero(ja, t);
            }
            addShaped(out, seen, shapeLabel(base.label, "ja"), ja, sc, base.edits);
        }
        if ("self".equals(base.label)) {
            SaveFile nt45 = copy(base.save);
            nt45.angleSolver.defaultInputs = "FORCE_45";
            stripYawPins(nt45);
            for (int t = startTick + 1; t <= lastFire; t++) {
                addDfZero(nt45, t);
            }
            addShaped(out, seen, "nt45", nt45, sc, base.edits);
            addStrafePatterns(out, seen, witness, sc, base, startTick, lastFire, landing);
        }
    }

    private static final String[] STRAFES = {"", "A", "D"};

    private static void addStrafePatterns(List<Variant> out, Set<String> seen, SaveFile witness,
                                          JumpPhysicsInputs sc, Variant base,
                                          int startTick, int lastFire, int landing) {
        for (String momentum : STRAFES) {
            for (String air : STRAFES) {
                SaveFile s = copy(base.save);
                int end = Math.min(landing, s.rows.size() - 1);
                for (int r = Math.max(0, startTick); r <= end; r++) {
                    SaveFile.Row row = s.rows.get(r);
                    TreeSet<String> ks = new TreeSet<String>();
                    ks.add("W");
                    ks.add("SPRINT");
                    if (row.keys != null && row.keys.contains("JUMP")) {
                        ks.add("JUMP");
                    }
                    String strafe = r < lastFire ? momentum : r > lastFire ? air : "";
                    if (!strafe.isEmpty()) {
                        ks.add(strafe);
                    }
                    row.keys = new ArrayList<String>(ks);
                }
                for (int t = startTick + 1; t <= lastFire; t++) {
                    addDfZero(s, t);
                }
                String label = "nt[" + (momentum.isEmpty() ? "-" : momentum)
                        + "|" + (air.isEmpty() ? "-" : air) + "]";
                addShaped(out, seen, label, s, sc, changedRows(witness, s));
            }
        }
    }

    private static int changedRows(SaveFile witness, SaveFile variant) {
        int changed = 0;
        for (int r = 0; r < witness.rows.size() && r < variant.rows.size(); r++) {
            TreeSet<String> a = keySetOf(witness.rows.get(r));
            TreeSet<String> b = keySetOf(variant.rows.get(r));
            if (!a.equals(b)) {
                changed++;
            }
        }
        return changed;
    }

    private static void stripYawPins(SaveFile save) {
        if (save.angleSolver.ticks == null) {
            return;
        }
        for (SaveFile.Tick tick : save.angleSolver.ticks) {
            if (tick == null) {
                continue;
            }
            if (tick.constraints != null) {
                Iterator<SaveFile.Constraint> it = tick.constraints.iterator();
                while (it.hasNext()) {
                    SaveFile.Constraint c = it.next();
                    if (c == null || "F".equals(c.field) || "DF".equals(c.field)) {
                        it.remove();
                    }
                }
            }
            if (tick.override != null) {
                tick.override.inputs = null;
            }
        }
    }

    private static String shapeLabel(String baseLabel, String shape) {
        return "self".equals(baseLabel) ? shape : baseLabel + "/" + shape;
    }

    private static void addShaped(List<Variant> out, Set<String> seen, String label,
                                  SaveFile shaped, JumpPhysicsInputs sc, int edits) {
        if (!seen.add(signature(shaped))) {
            return;
        }
        out.add(prepare(label, shaped, null, sc, edits));
    }

    private static boolean hasDfConstraint(SaveFile save, int absTick) {
        if (save.angleSolver.ticks == null) {
            return false;
        }
        for (SaveFile.Tick tick : save.angleSolver.ticks) {
            if (tick == null || tick.tick != absTick || tick.constraints == null) {
                continue;
            }
            for (SaveFile.Constraint c : tick.constraints) {
                if (c != null && !c.disabled && "DF".equals(c.field)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void addDfZero(SaveFile save, int absTick) {
        if (hasDfConstraint(save, absTick)) {
            return;
        }
        SaveFile.Constraint c = new SaveFile.Constraint();
        c.range = false;
        c.field = "DF";
        c.op = "EQ";
        c.value = 0.0;
        if (save.angleSolver.ticks == null) {
            save.angleSolver.ticks = new ArrayList<SaveFile.Tick>();
        }
        for (SaveFile.Tick tick : save.angleSolver.ticks) {
            if (tick != null && tick.tick == absTick) {
                if (tick.constraints == null) {
                    tick.constraints = new ArrayList<SaveFile.Constraint>();
                }
                tick.constraints.add(c);
                return;
            }
        }
        SaveFile.Tick tick = new SaveFile.Tick();
        tick.tick = absTick;
        tick.constraints.add(c);
        save.angleSolver.ticks.add(tick);
    }

    private static int lastJumpRow(SaveFile s, int startTick, int landing) {
        int last = -1;
        for (int t = Math.max(0, startTick); t <= landing && t < s.rows.size(); t++) {
            if (hasKey(s.rows.get(t), "JUMP")) {
                last = t;
            }
        }
        return last;
    }

    public static JumpPhysicsInputs scenario(SaveFile file, ExactJumpModel model) {
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, SaveBoxes.buildBoxes(file), inputs, t -> {
        }, model);
        JumpSpec spec = engine.debugBuildSpec();
        return spec == null ? null : spec.asScenario();
    }

    private static void addVariant(List<Variant> out, Set<String> seen, SaveFile witness,
                                   JumpPhysicsInputs sc, String label, int row, String key, boolean add) {
        if (row < 0 || row >= witness.rows.size()) {
            return;
        }
        SaveFile s = copy(witness);
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
        out.add(prepare(label, s, witness, sc, 1));
    }

    private static Variant prepare(String label, SaveFile s, SaveFile witness, JumpPhysicsInputs sc, int edits) {
        SaveFile v = s == witness ? copy(witness) : s;
        deriveDebugSamples(v, sc);
        v.angleSolver.result = null;
        v.angleSolver.effort = "FAST";
        v.angleSolver.stopOnFeasible = Boolean.TRUE;
        return new Variant(label, v, edits);
    }

    public static void deriveDebugSamples(SaveFile save, JumpPhysicsInputs sc0) {
        if (save.debug == null) {
            return;
        }
        int startTick = save.angleSolver.startTick;
        boolean sprint = startTick < save.debug.size() && save.debug.get(startTick).sprinting;
        int end = Math.min(startTick + sc0.numTicks - 1, save.rows.size() - 1);
        for (int r = startTick; r <= end; r++) {
            TreeSet<String> ks = keySetOf(save.rows.get(r));
            boolean canRun = ks.contains("W") && !ks.contains("S") && !ks.contains("SNEAK");
            if (!canRun) {
                sprint = false;
            } else if (!sprint && ks.contains("SPRINT")) {
                sprint = true;
            }
            if (r + 1 >= save.debug.size()) {
                break;
            }
            SaveFile.DebugTick d = save.debug.get(r + 1);
            float scale = KEY_INPUT_SCALE * (ks.contains("SNEAK") ? SNEAK_INPUT_SCALE : 1.0F);
            d.moveForward = scale * ((ks.contains("W") ? 1 : 0) - (ks.contains("S") ? 1 : 0));
            d.moveStrafe = scale * ((ks.contains("A") ? 1 : 0) - (ks.contains("D") ? 1 : 0));
            d.sprinting = sprint;
        }
    }

    private static String signature(SaveFile s) {
        StringBuilder sb = new StringBuilder();
        for (SaveFile.Row r : s.rows) {
            sb.append(r.keys).append(';');
        }
        sb.append('|').append(s.angleSolver != null ? s.angleSolver.defaultInputs : null).append('|');
        List<Integer> dfTicks = new ArrayList<Integer>();
        List<Integer> fTicks = new ArrayList<Integer>();
        if (s.angleSolver != null && s.angleSolver.ticks != null) {
            for (SaveFile.Tick t : s.angleSolver.ticks) {
                if (t == null || t.constraints == null) {
                    continue;
                }
                boolean df = false;
                boolean f = false;
                for (SaveFile.Constraint c : t.constraints) {
                    if (c == null || c.disabled) {
                        continue;
                    }
                    df = df || "DF".equals(c.field);
                    f = f || "F".equals(c.field);
                }
                if (df) dfTicks.add(t.tick);
                if (f) fTicks.add(t.tick);
            }
        }
        Collections.sort(dfTicks);
        Collections.sort(fTicks);
        sb.append(dfTicks).append('|').append(fTicks);
        return sb.toString();
    }

    private static boolean hasKey(SaveFile.Row row, String key) {
        return row != null && row.keys != null && row.keys.contains(key);
    }

    private static TreeSet<String> keySetOf(SaveFile.Row row) {
        TreeSet<String> set = new TreeSet<String>();
        if (row.keys != null) {
            set.addAll(row.keys);
        }
        return set;
    }

    private static SaveFile copy(SaveFile s) {
        return GSON.fromJson(GSON.toJson(s), SaveFile.class);
    }
}
