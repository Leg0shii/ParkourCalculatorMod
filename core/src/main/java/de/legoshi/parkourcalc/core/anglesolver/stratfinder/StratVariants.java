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
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class StratVariants {

    public static final float KEY_INPUT_SCALE = 0.98F;
    public static final float SNEAK_INPUT_SCALE = 0.3F;

    private static final Gson GSON = new Gson();
    private static final List<String> KEYS = Arrays.asList("W", "SPRINT", "A", "D", "S");
    private static final int WINDOW = 12;
    private static final int MAX_VARIANTS = 40;
    private static final Set<String> PRODUCT_FAMILIES = allNarrowPlanLabels();

    private static Set<String> allNarrowPlanLabels() {
        Set<String> out = new HashSet<String>();
        for (StratPlans.Plan p : StratPlans.plans(false)) {
            out.add(p.label);
        }
        return out;
    }
    private static final Set<String> PATTERN_FAMILIES = patternFamilyLabels();

    private static Set<String> patternFamilyLabels() {
        Set<String> out = new HashSet<String>();
        for (StratPlans.Plan p : StratPlans.plans(false)) {
            if (p.label.startsWith("fmm") || p.label.startsWith("pessi") || p.label.startsWith("mark")) {
                out.add(p.label);
            }
        }
        return out;
    }

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

    public static final class Filter {
        public static final String FAMILY_SHAPES = "shapes";
        public static final String FAMILY_SHIFTS = "shifts";
        public static final String FAMILY_FMM = "fmm";
        public static final String FAMILY_PESSI = "pessi";
        public static final String FAMILY_RUN_JAM = "run+jam";
        public static final String FAMILY_MARK = "mark";
        public static final String FAMILY_BWMM = "bwmm";
        public static final List<String> FAMILIES = Collections.unmodifiableList(Arrays.asList(
                FAMILY_SHAPES, FAMILY_SHIFTS, FAMILY_FMM, FAMILY_PESSI,
                FAMILY_RUN_JAM, FAMILY_MARK, FAMILY_BWMM));

        public enum Shape {ANY, NT, JA}

        public static final Filter ALL = new Filter(null, Shape.ANY);

        private final Set<String> families;
        public final Shape shape;

        public Filter(Set<String> families, Shape shape) {
            this.families = families == null ? null : new HashSet<String>(families);
            this.shape = shape == null ? Shape.ANY : shape;
        }

        public boolean allowsFamily(String family) {
            return families == null || families.contains(family);
        }

        public boolean allowsUnshaped() {
            return shape == Shape.ANY;
        }

        public boolean allowsNt() {
            return shape != Shape.JA;
        }

        public boolean allowsJa() {
            return shape != Shape.NT;
        }
    }

    public static String familyOfPlan(String planLabel) {
        if (planLabel.startsWith("bwmm")) {
            return Filter.FAMILY_BWMM;
        }
        if (planLabel.startsWith("fmm")) {
            return Filter.FAMILY_FMM;
        }
        if (planLabel.startsWith("pessi")) {
            return Filter.FAMILY_PESSI;
        }
        if (planLabel.startsWith("mark")) {
            return Filter.FAMILY_MARK;
        }
        return Filter.FAMILY_RUN_JAM;
    }

    private StratVariants() {
    }

    public static List<Variant> variants(SaveFile witness, ExactJumpModel model) {
        return variants(witness, model, Filter.ALL);
    }

    public static List<Variant> variants(SaveFile witness, ExactJumpModel model, Filter filter) {
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
        if (filter.allowsFamily(Filter.FAMILY_SHIFTS)) {
            collectTimingVariants(bases, seen, witness, sc);
        }
        collectFamilyVariants(bases, seen, witness, sc, filter);
        for (Variant base : bases) {
            if ("self".equals(base.label) || filter.allowsUnshaped()) {
                out.add(base);
            }
            addShapeVariants(out, seen, witness, sc, base, filter);
        }
        addFamilyPatternVariants(out, seen, witness, sc, filter);
        return out;
    }

    private static void collectFamilyVariants(List<Variant> out, Set<String> seen,
                                              SaveFile witness, JumpPhysicsInputs sc, Filter filter) {
        int fire = firstGroundedJumpTick(witness, sc);
        if (fire < 0) {
            return;
        }
        for (StratPlans.Plan plan : StratPlans.plans(false)) {
            if (!PRODUCT_FAMILIES.contains(plan.label)) {
                continue;
            }
            if (!filter.allowsFamily(familyOfPlan(plan.label))) {
                continue;
            }
            SaveFile s = applyFamily(witness, sc, plan, fire);
            if (s == null) {
                continue;
            }
            if (!seen.add(signature(s))) {
                continue;
            }
            out.add(prepare(plan.label, s, null, sc, changedRows(witness, s)));
        }
    }

    private static int firstGroundedJumpTick(SaveFile witness, JumpPhysicsInputs sc) {
        int startTick = witness.angleSolver.startTick;
        int landing = witness.angleSolver.landingTick;
        for (int k = 0; k < sc.numTicks; k++) {
            int t = startTick + k;
            if (t > landing || t >= witness.rows.size()) {
                break;
            }
            if (!hasKey(witness.rows.get(t), "JUMP")) {
                continue;
            }
            double slip = sc.slipAt(k);
            if (!Double.isNaN(slip) && slip < 1.0) {
                return t;
            }
        }
        return -1;
    }

    private static SaveFile applyFamily(SaveFile witness, JumpPhysicsInputs sc,
                                        StratPlans.Plan plan, int fire) {
        SaveFile s = copy(witness);
        if (!patchFamilyRows(s, plan, fire)) {
            return null;
        }
        stampKeepDerive(s);
        deriveDebugSamples(s, sc);
        if (!sprintAtFireMatches(s, fire, plan)) {
            return null;
        }
        return s;
    }

    private static boolean patchFamilyRows(SaveFile s, StratPlans.Plan plan, int fire) {
        int startTick = s.angleSolver.startTick;
        int landing = s.angleSolver.landingTick;
        int approachStart = fire - plan.fire;
        if (approachStart < startTick) {
            return false;
        }
        int maxRel = plan.lastPatchRel();
        if (fire + maxRel > Math.min(landing, s.rows.size() - 1)) {
            return false;
        }
        for (int rel = 1; rel <= maxRel; rel++) {
            if (hasKey(s.rows.get(fire + rel), "JUMP")) {
                return false;
            }
        }
        for (int t = approachStart; t < fire; t++) {
            TreeSet<String> planKeys = plan.preRows.get(t - approachStart);
            boolean planJump = planKeys != null && planKeys.contains("JUMP");
            if (planJump != hasKey(s.rows.get(t), "JUMP")) {
                return false;
            }
        }
        boolean unsprintedWFire = plan.fireKeys.contains("W") && !plan.fireKeys.contains("SPRINT");
        if (unsprintedWFire) {
            for (int t = startTick; t < approachStart; t++) {
                TreeSet<String> ks = keySetOf(s.rows.get(t));
                if (ks.remove("SPRINT")) {
                    s.rows.get(t).keys = new ArrayList<String>(ks);
                }
            }
        }
        for (int t = approachStart; t < fire; t++) {
            TreeSet<String> planKeys = plan.preRows.get(t - approachStart);
            TreeSet<String> ks = planKeys != null
                    ? new TreeSet<String>(planKeys) : new TreeSet<String>();
            preserveStrafe(s.rows.get(t), ks);
            s.rows.get(t).keys = new ArrayList<String>(ks);
        }
        TreeSet<String> fk = new TreeSet<String>(plan.fireKeys);
        preserveStrafe(s.rows.get(fire), fk);
        s.rows.get(fire).keys = new ArrayList<String>(fk);
        for (Map.Entry<Integer, String[][]> e : plan.post.entrySet()) {
            int r = fire + e.getKey();
            TreeSet<String> ks = keySetOf(s.rows.get(r));
            Collections.addAll(ks, e.getValue()[0]);
            for (String off : e.getValue()[1]) {
                ks.remove(off);
            }
            s.rows.get(r).keys = new ArrayList<String>(ks);
        }
        String side = familySide(plan);
        if (side != null) {
            String other = "A".equals(side) ? "D" : "A";
            int end = Math.min(landing, s.rows.size() - 1);
            for (int t = fire + 1; t <= end; t++) {
                if (hasKey(s.rows.get(t), "JUMP")) {
                    break;
                }
                TreeSet<String> ks = keySetOf(s.rows.get(t));
                ks.add(side);
                ks.remove(other);
                s.rows.get(t).keys = new ArrayList<String>(ks);
            }
        }
        return true;
    }

    private static String familySide(StratPlans.Plan plan) {
        if (plan.fireKeys.contains("A")) {
            return "A";
        }
        if (plan.fireKeys.contains("D")) {
            return "D";
        }
        return null;
    }

    private static void preserveStrafe(SaveFile.Row row, TreeSet<String> ks) {
        if (ks.contains("A") || ks.contains("D")) {
            return;
        }
        if (hasKey(row, "A")) {
            ks.add("A");
        }
        if (hasKey(row, "D")) {
            ks.add("D");
        }
    }

    private static void stampKeepDerive(SaveFile s) {
        s.angleSolver.defaultInputs = "KEEP";
        s.angleSolver.defaultSprint = "DERIVE";
        if (s.angleSolver.ticks == null) {
            return;
        }
        for (SaveFile.Tick tick : s.angleSolver.ticks) {
            if (tick != null && tick.override != null) {
                tick.override.inputs = null;
                tick.override.sprint = null;
            }
        }
    }

    private static boolean sprintAtFireMatches(SaveFile s, int fire, StratPlans.Plan plan) {
        if (s.debug == null || fire + 1 >= s.debug.size()) {
            return false;
        }
        return s.debug.get(fire + 1).sprinting == plan.fireKeys.contains("SPRINT");
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
                                         JumpPhysicsInputs sc, Variant base, Filter filter) {
        boolean self = "self".equals(base.label);
        if (self && !filter.allowsFamily(Filter.FAMILY_SHAPES)) {
            return;
        }
        int startTick = witness.angleSolver.startTick;
        int landing = witness.angleSolver.landingTick;
        if (landing <= startTick) {
            return;
        }
        int lastFire = lastJumpRow(base.save, startTick, landing);
        if (lastFire <= startTick) {
            return;
        }
        if (filter.allowsNt()) {
            SaveFile nt = copy(base.save);
            addMomentumChain(nt, startTick, lastFire, false);
            addShaped(out, seen, shapeLabel(base.label, "nt"), nt, sc, base.edits);
        }
        if (lastFire > startTick + 1 && filter.allowsJa()) {
            SaveFile ja = copy(base.save);
            addMomentumChain(ja, startTick, lastFire, true);
            addShaped(out, seen, shapeLabel(base.label, "ja"), ja, sc, base.edits);
        }
        if (self && filter.allowsNt()) {
            SaveFile nt45 = copy(base.save);
            nt45.angleSolver.defaultInputs = "FORCE_45";
            stripYawPins(nt45);
            addMomentumChain(nt45, startTick, lastFire, false);
            addShaped(out, seen, "nt45", nt45, sc, base.edits);
            addStrafePatterns(out, seen, witness, sc, base, startTick, lastFire, landing);
        }
    }

    private static void addMomentumChain(SaveFile s, int startTick, int lastFire, boolean exemptLastFire) {
        for (int t = startTick + 1; t <= lastFire; t++) {
            if (exemptLastFire && t == lastFire) {
                continue;
            }
            addDfZero(s, t);
        }
    }

    private static final String[] STRAFES = {"", "A", "D"};

    private static void addStrafePatterns(List<Variant> out, Set<String> seen, SaveFile witness,
                                          JumpPhysicsInputs sc, Variant base,
                                          int startTick, int lastFire, int landing) {
        for (String momentum : STRAFES) {
            for (String air : STRAFES) {
                SaveFile s = canonicalPattern(base.save, momentum, air, false, startTick, lastFire, landing);
                stampKeepDerive(s);
                addShaped(out, seen, patternLabel("", momentum, air, false), s, sc, changedRows(witness, s));
                if (!air.isEmpty()) {
                    SaveFile p = canonicalPattern(base.save, momentum, air, true, startTick, lastFire, landing);
                    stampKeepDerive(p);
                    addShaped(out, seen, patternLabel("", momentum, air, true), p, sc, changedRows(witness, p));
                }
            }
        }
    }

    private static SaveFile canonicalPattern(SaveFile base, String momentum, String air, boolean airOnPress,
                                             int startTick, int lastFire, int landing) {
        SaveFile s = copy(base);
        int end = Math.min(landing, s.rows.size() - 1);
        for (int r = Math.max(0, startTick); r <= end; r++) {
            SaveFile.Row row = s.rows.get(r);
            TreeSet<String> ks = new TreeSet<String>();
            ks.add("W");
            ks.add("SPRINT");
            if (row.keys != null && row.keys.contains("JUMP")) {
                ks.add("JUMP");
            }
            String strafe = r < lastFire ? momentum
                    : r > lastFire ? air
                    : airOnPress ? air : "";
            if (!strafe.isEmpty()) {
                ks.add(strafe);
            }
            row.keys = new ArrayList<String>(ks);
        }
        addMomentumChain(s, startTick, lastFire, false);
        return s;
    }

    private static String patternLabel(String familyPrefix, String momentum, String air, boolean airOnPress) {
        return familyPrefix + "nt[" + (momentum.isEmpty() ? "-" : momentum)
                + "|" + (air.isEmpty() ? "-" : air) + (airOnPress ? "*" : "") + "]";
    }

    private static void addFamilyPatternVariants(List<Variant> out, Set<String> seen,
                                                 SaveFile witness, JumpPhysicsInputs sc, Filter filter) {
        if (!filter.allowsNt()) {
            return;
        }
        int startTick = witness.angleSolver.startTick;
        int landing = witness.angleSolver.landingTick;
        if (landing <= startTick) {
            return;
        }
        int lastFire = lastJumpRow(witness, startTick, landing);
        int fire = firstGroundedJumpTick(witness, sc);
        if (fire < 0 || lastFire <= startTick) {
            return;
        }
        for (StratPlans.Plan plan : StratPlans.plans(false)) {
            if (!PATTERN_FAMILIES.contains(plan.label)) {
                continue;
            }
            if (!filter.allowsFamily(familyOfPlan(plan.label))) {
                continue;
            }
            String side = familySide(plan);
            for (String momentum : STRAFES) {
                if (side != null && !momentum.equals(side)) {
                    continue;
                }
                for (String air : STRAFES) {
                    addFamilyPattern(out, seen, witness, sc, plan, fire,
                            canonicalPattern(witness, momentum, air, false, startTick, lastFire, landing),
                            patternLabel(plan.label + "/", momentum, air, false));
                    if (!air.isEmpty()) {
                        addFamilyPattern(out, seen, witness, sc, plan, fire,
                                canonicalPattern(witness, momentum, air, true, startTick, lastFire, landing),
                                patternLabel(plan.label + "/", momentum, air, true));
                    }
                }
            }
        }
    }

    private static void addFamilyPattern(List<Variant> out, Set<String> seen, SaveFile witness,
                                         JumpPhysicsInputs sc, StratPlans.Plan plan, int fire,
                                         SaveFile canon, String label) {
        if (!patchFamilyRows(canon, plan, fire)) {
            return;
        }
        stampKeepDerive(canon);
        deriveDebugSamples(canon, sc);
        if (!sprintAtFireMatches(canon, fire, plan)) {
            return;
        }
        addShaped(out, seen, label, canon, sc, changedRows(witness, canon));
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
            sb.append(keySetOf(r)).append(';');
        }
        sb.append('|').append(s.angleSolver != null ? s.angleSolver.defaultInputs : null)
                .append('|').append(s.angleSolver != null ? s.angleSolver.defaultSprint : null).append('|');
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
