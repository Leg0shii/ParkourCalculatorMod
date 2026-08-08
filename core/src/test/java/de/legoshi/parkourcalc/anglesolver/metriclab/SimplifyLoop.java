package de.legoshi.parkourcalc.anglesolver.metriclab;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

public final class SimplifyLoop {

    public static final int MAX_STEPS = 15;
    public static final long CANDIDATE_SOLVE_MS = 250L;
    public static final long COLD_VERIFY_MS = 1_000L;
    public static final double NO_TURN_MIN_WINDOW_DEG = 10.0;
    public static final int NO_TURN_MIN_SPAN = 4;

    private static final Gson GSON = new Gson();

    public static final class Step {
        public final String operator;
        public final double scoreBefore;
        public final double scoreAfter;

        Step(String operator, double scoreBefore, double scoreAfter) {
            this.operator = operator;
            this.scoreBefore = scoreBefore;
            this.scoreAfter = scoreAfter;
        }
    }

    public static final class Outcome {
        public SaveFile finalSave;
        public JumpMeasurements finalMeasurement;
        public double startScore;
        public double finalScore;
        public final List<Step> steps = new ArrayList<Step>();
        public boolean coldVerified;
        public long coldVerifyMs;
        public String keepifyNote;
    }

    private static final class Candidate {
        final String label;
        final SaveFile save;
        final boolean needsSolve;
        final boolean repair;
        final boolean deepen;
        final List<double[]> support;

        Candidate(String label, SaveFile save, boolean needsSolve) {
            this(label, save, needsSolve, false, false, new ArrayList<double[]>());
        }

        Candidate(String label, SaveFile save, boolean needsSolve, List<double[]> support) {
            this(label, save, needsSolve, false, false, support);
        }

        Candidate(String label, SaveFile save, boolean needsSolve, boolean repair, List<double[]> support) {
            this(label, save, needsSolve, repair, false, support);
        }

        Candidate(String label, SaveFile save, boolean needsSolve, boolean repair, boolean deepen,
                  List<double[]> support) {
            this.label = label;
            this.save = save;
            this.needsSolve = needsSolve;
            this.repair = repair;
            this.deepen = deepen;
            this.support = support;
        }

        double[] repairSeed;
    }

    private SimplifyLoop() {
    }

    public static Outcome run(SaveFile start, String name, ScoringMetric metric) {
        Outcome best = greedy(start, name, metric, false, false);
        if ("FORCE_45".equals(start.angleSolver.defaultInputs)) {
            best = better(best, greedy(start, name, metric, true, false));
            best = better(best, greedy(start, name, metric, false, true));
        }
        return best;
    }

    private static Outcome better(Outcome a, Outcome b) {
        if (a.coldVerified != b.coldVerified) {
            return a.coldVerified ? a : b;
        }
        return b.finalScore < a.finalScore - 1.0e-9 ? b : a;
    }

    private static Outcome greedy(SaveFile start, String name, ScoringMetric metric, boolean deferKeepify,
                                  boolean crossFirst) {
        ExactJumpModel model = ExactJumpModel.forMcVersion(start.mcVersion);
        Outcome out = new Outcome();
        SaveFile cur = copy(start);
        freeStartify(cur);
        JumpMeasurements m = MeasurementEngine.measure(cur, name);
        double score = metric.score(m);
        out.startScore = score;

        boolean keepifyAllowed = !deferKeepify;
        List<double[]> acceptedSupport = new ArrayList<double[]>();
        for (int step = 0; step < MAX_STEPS; step++) {
            out.keepifyNote = null;
            boolean crossing = crossFirst
                    && ("FORCE_45".equals(cur.angleSolver.defaultInputs) || firstF45Override(cur) >= 0);
            List<Candidate> candidates = new ArrayList<Candidate>();
            if (crossing) {
                addKeepifyCandidate(candidates, cur, model);
            } else {
                if (keyOpsAllowed(cur)) {
                    addJumpHoldCandidates(candidates, cur);
                    addRecenterCandidates(candidates, cur, m, model);
                    addTapDeletionCandidates(candidates, cur, m);
                    addHoldDeletionCandidates(candidates, cur);
                }
                addNoTurnCandidate(candidates, cur, m);
                if (keepifyAllowed) {
                    addKeepifyCandidate(candidates, cur, model);
                }
                addResolveCandidates(candidates, cur);
                addDeepenCandidate(candidates, cur);
                addAimLineCandidate(candidates, cur);
            }

            SaveFile bestSave = null;
            JumpMeasurements bestM = null;
            double bestScore = crossing ? Double.POSITIVE_INFINITY : score;
            String bestLabel = null;
            List<double[]> bestSupport = null;
            for (Candidate c : candidates) {
                SaveFile cand = c.save;
                boolean keepify = c.label.startsWith("keepify");
                List<double[]> effectiveSupport = new ArrayList<double[]>(acceptedSupport);
                effectiveSupport.addAll(c.support);
                if (c.deepen) {
                    if (!deepenAttach(cand, model) || !replayFeasible(cand, model, effectiveSupport)) {
                        continue;
                    }
                } else if (c.repair) {
                    if (!repairAttach(cand, cur, model, c.repairSeed)) {
                        if (keepify) {
                            note(out, c.label, "repair did not converge");
                        }
                        continue;
                    }
                    if (!replayFeasible(cand, model, effectiveSupport)) {
                        if (keepify) {
                            note(out, c.label, "repaired but replay infeasible");
                        }
                        continue;
                    }
                } else if (c.needsSolve) {
                    HeadlessSolve.Run run = HeadlessSolve.solve(cand, model, CANDIDATE_SOLVE_MS);
                    if (run.result == null || !run.result.isSuccess()) {
                        if (keepify) {
                            note(out, c.label, run.result == null
                                    ? "no result in " + CANDIDATE_SOLVE_MS + " ms"
                                    : "infeasible best " + run.result.getMet() + "/" + run.result.getTotal()
                                            + " in " + run.elapsedMs + " ms");
                        }
                        continue;
                    }
                    Variant45.attachResult(cand, run.result);
                    if (run.movedStart != null) {
                        applyMovedStart(cand, run.movedStart);
                    }
                    if (!replayFeasible(cand, model, effectiveSupport)) {
                        if (keepify) {
                            note(out, c.label, "solved but replay infeasible");
                        }
                        continue;
                    }
                } else if (!replayFeasible(cand, model, effectiveSupport)) {
                    continue;
                }
                JumpMeasurements cm;
                try {
                    cm = MeasurementEngine.measure(cand, name);
                } catch (RuntimeException ex) {
                    if (keepify) {
                        note(out, c.label, "measure failed: " + ex.getMessage());
                    }
                    continue;
                }
                double cs = metric.score(cm);
                if (keepify && cs >= bestScore - 1.0e-9) {
                    note(out, c.label, String.format(java.util.Locale.ROOT,
                            "score %.3f not below %.3f", cs, bestScore));
                }
                if (cs < bestScore - 1.0e-9) {
                    bestScore = cs;
                    bestSave = cand;
                    bestM = cm;
                    bestLabel = c.label;
                    bestSupport = effectiveSupport;
                }
            }
            if (bestSave == null) {
                if (!keepifyAllowed) {
                    keepifyAllowed = true;
                    continue;
                }
                break;
            }
            if (bestLabel.startsWith("keepify")) {
                out.keepifyNote = null;
            }
            out.steps.add(new Step(bestLabel, score, bestScore));
            cur = bestSave;
            m = bestM;
            score = bestScore;
            acceptedSupport = bestSupport;
        }

        out.finalSave = cur;
        out.finalMeasurement = m;
        out.finalScore = score;

        SaveFile verify = copy(cur);
        verify.angleSolver.result = null;
        HeadlessSolve.Run run = HeadlessSolve.solve(verify, model, COLD_VERIFY_MS);
        out.coldVerified = run.result != null && run.result.isSuccess();
        out.coldVerifyMs = run.elapsedMs;
        bakeYawRows(cur, model);
        return out;
    }

    public static void bakeYawRows(SaveFile save, ExactJumpModel model) {
        if (save.angleSolver.result == null) {
            return;
        }
        JumpSpec spec;
        try {
            spec = MeasurementEngine.buildSpec(save, model);
        } catch (RuntimeException ex) {
            return;
        }
        if (spec == null) {
            return;
        }
        JumpPhysicsInputs sc = spec.asScenario();
        double[] yaws;
        try {
            yaws = MeasurementEngine.recordedYaws(save, "bake", sc.numTicks);
        } catch (RuntimeException ex) {
            return;
        }
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        int startTick = save.angleSolver.startTick;
        for (int k = 0; k < gf.length && startTick + k < save.rows.size(); k++) {
            SaveFile.Row row = save.rows.get(startTick + k);
            if (force45At(save, startTick + k)) {
                TreeSet<String> ks = keySetOf(row);
                ks.add("W");
                ks.add("SPRINT");
                ks.remove("S");
                ks.remove("SNEAK");
                boolean strafe = sc.strafeAt(k);
                if (strafe && sc.strafeSign > 0) {
                    ks.add("A");
                } else {
                    ks.remove("A");
                }
                if (strafe && sc.strafeSign < 0) {
                    ks.add("D");
                } else {
                    ks.remove("D");
                }
                row.keys = new ArrayList<String>(ks);
            }
            row.yawLocked = true;
            row.yaw = (float) gf[k];
        }
    }

    private static boolean force45At(SaveFile save, int absTick) {
        String mode = save.angleSolver.defaultInputs;
        if (save.angleSolver.ticks != null) {
            for (SaveFile.Tick tick : save.angleSolver.ticks) {
                if (tick != null && tick.tick == absTick && tick.override != null && tick.override.inputs != null) {
                    mode = tick.override.inputs;
                }
            }
        }
        return "FORCE_45".equals(mode);
    }

    private static boolean replayFeasible(SaveFile save, ExactJumpModel model, List<double[]> support) {
        JumpSpec spec;
        try {
            spec = MeasurementEngine.buildSpec(save, model);
        } catch (RuntimeException ex) {
            return false;
        }
        if (spec == null) {
            return false;
        }
        JumpPhysicsInputs sc = spec.asScenario();
        double[] yaws;
        try {
            yaws = MeasurementEngine.recordedYaws(save, "candidate", sc.numTicks);
        } catch (RuntimeException ex) {
            return false;
        }
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath path = model.forward(sc, gf);
        if (compiled.maxViolation(gf, path) > 0.0) {
            return false;
        }
        return MeasurementEngine.supported(path, support);
    }

    private static void note(Outcome out, String label, String msg) {
        String entry = label + ": " + msg;
        out.keepifyNote = out.keepifyNote == null ? entry : out.keepifyNote + "; " + entry;
    }

    private static void freeStartify(SaveFile save) {
        if (save.angleSolver.startTick != 0) {
            return;
        }
        SaveFile.Start seed = save.angleSolver.seed;
        if (seed == null || seed.pos == null || seed.pos.length < 3) {
            return;
        }
        if (hasTickRange(save, 0, "X") || hasTickRange(save, 0, "Z")) {
            return;
        }
        double bx = Math.floor(seed.pos[0]);
        double bz = Math.floor(seed.pos[2]);
        addRange(save, 0, "X", bx - 0.3, bx + 1.3);
        addRange(save, 0, "Z", bz - 0.3, bz + 1.3);
    }

    private static boolean hasTickRange(SaveFile save, int absTick, String field) {
        if (save.angleSolver.ticks == null) {
            return false;
        }
        for (SaveFile.Tick tick : save.angleSolver.ticks) {
            if (tick == null || tick.tick != absTick || tick.constraints == null) {
                continue;
            }
            for (SaveFile.Constraint c : tick.constraints) {
                if (c != null && !c.disabled && c.range && c.refTick == null && field.equals(c.field)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void addRange(SaveFile save, int absTick, String field, double lo, double hi) {
        SaveFile.Constraint c = new SaveFile.Constraint();
        c.range = true;
        c.field = field;
        c.lo = lo;
        c.hi = hi;
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

    private static void applyMovedStart(SaveFile save, Vec3dCore p) {
        if (save.angleSolver.seed != null && save.angleSolver.seed.pos != null
                && save.angleSolver.seed.pos.length >= 3) {
            save.angleSolver.seed.pos[0] = p.x;
            save.angleSolver.seed.pos[2] = p.z;
        }
        if (save.start != null && save.start.pos != null && save.start.pos.length >= 3) {
            save.start.pos[0] = p.x;
            save.start.pos[2] = p.z;
        }
    }

    private static boolean keyOpsAllowed(SaveFile save) {
        if (!"KEEP".equals(save.angleSolver.defaultInputs)) {
            return false;
        }
        if (save.angleSolver.ticks != null) {
            for (SaveFile.Tick tick : save.angleSolver.ticks) {
                if (tick != null && tick.override != null && tick.override.inputs != null
                        && !"KEEP".equals(tick.override.inputs)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void addJumpHoldCandidates(List<Candidate> out, SaveFile cur) {
        List<Integer> presses = jumpPressRows(cur);
        if (presses.size() < 2) {
            return;
        }
        boolean allSpaced = true;
        for (int i = 0; i + 1 < presses.size(); i++) {
            if (presses.get(i + 1) - presses.get(i) < MeasurementEngine.JUMP_HOLD_COOLDOWN_TICKS) {
                allSpaced = false;
            }
        }
        if (allSpaced) {
            SaveFile full = copy(cur);
            holdJump(full, presses.get(0), presses.get(presses.size() - 1));
            out.add(new Candidate("holdJump[all]", full, false));
        }
        for (int i = 0; i + 1 < presses.size(); i++) {
            if (presses.get(i + 1) - presses.get(i) < MeasurementEngine.JUMP_HOLD_COOLDOWN_TICKS) {
                continue;
            }
            SaveFile pair = copy(cur);
            holdJump(pair, presses.get(i), presses.get(i + 1));
            out.add(new Candidate("holdJump[" + presses.get(i) + ".." + presses.get(i + 1) + "]", pair, false));
        }
    }

    private static List<Integer> jumpPressRows(SaveFile save) {
        List<Integer> presses = new ArrayList<Integer>();
        int end = lastEditableRow(save);
        boolean prev = rowHasKey(save.rows.get(0), "JUMP");
        for (int t = 1; t <= end; t++) {
            boolean curJump = rowHasKey(save.rows.get(t), "JUMP");
            if (curJump && !prev) {
                presses.add(t);
            }
            prev = curJump;
        }
        return presses;
    }

    private static void holdJump(SaveFile save, int fromRow, int toRow) {
        for (int r = fromRow; r <= toRow; r++) {
            SaveFile.Row row = save.rows.get(r);
            if (!rowHasKey(row, "JUMP")) {
                row.keys.add("JUMP");
            }
        }
    }

    private static void addRecenterCandidates(List<Candidate> out, SaveFile cur, JumpMeasurements m,
                                              ExactJumpModel model) {
        if (m.shiftEdgeRow == null) {
            return;
        }
        JumpSpec spec = MeasurementEngine.buildSpec(cur, model);
        if (spec == null) {
            return;
        }
        JumpPhysicsInputs sc0 = spec.asScenario();
        for (int i = 0; i < m.shiftEdgeRow.length; i++) {
            if (m.shiftEdgeKeys[i].contains("SPRINT")) {
                continue;
            }
            int half = (m.shiftHi[i] - m.shiftLo[i]) / 2;
            int full = m.shiftHi[i] - m.shiftLo[i];
            for (int shift : half == full ? new int[]{half} : new int[]{half, full}) {
                if (shift == 0) {
                    continue;
                }
                SaveFile cand = copy(cur);
                List<double[]> support = new ArrayList<double[]>();
                if (!MeasurementEngine.applyShift(cand, m.shiftEdgeRow[i], shift, cur.angleSolver.startTick, sc0,
                        support)) {
                    continue;
                }
                out.add(new Candidate("recenter[row" + m.shiftEdgeRow[i] + (shift > 0 ? "+" : "") + shift + "]",
                        cand, false, support));
            }
        }
    }

    private static void addTapDeletionCandidates(List<Candidate> out, SaveFile cur, JumpMeasurements m) {
        if (m.shiftEdgeRow == null) {
            return;
        }
        for (int i = 0; i + 1 < m.shiftEdgeRow.length; i++) {
            String pressKeys = m.shiftEdgeKeys[i];
            String releaseKeys = m.shiftEdgeKeys[i + 1];
            if (pressKeys.contains("JUMP") || releaseKeys.contains("JUMP")
                    || pressKeys.contains("SPRINT") || releaseKeys.contains("SPRINT")) {
                continue;
            }
            if (!isOppositePair(pressKeys, releaseKeys)) {
                continue;
            }
            int t1 = m.shiftEdgeRow[i];
            int t2 = m.shiftEdgeRow[i + 1];
            SaveFile cand = copy(cur);
            TreeSet<String> preState = keySetOf(cand.rows.get(t1 - 1));
            for (int r = t1; r < t2; r++) {
                SaveFile.Row row = cand.rows.get(r);
                TreeSet<String> ks = keySetOf(row);
                for (String flip : flippedKeys(pressKeys)) {
                    if (preState.contains(flip)) {
                        ks.add(flip);
                    } else {
                        ks.remove(flip);
                    }
                }
                row.keys = new ArrayList<String>(ks);
                MeasurementEngine.syncMovementSample(cand, r, t1 - 1);
            }
            out.add(new Candidate("deleteTap[" + pressKeys + "@" + t1 + ".." + t2 + "]", cand, false));
        }
    }

    private static final String[] HOLD_DELETE_KEYS = {"A", "D", "S"};

    private static void addHoldDeletionCandidates(List<Candidate> out, SaveFile cur) {
        int end = lastEditableRow(cur);
        for (String key : HOLD_DELETE_KEYS) {
            int spanStart = -1;
            for (int t = cur.angleSolver.startTick; t <= end + 1; t++) {
                boolean held = t <= end && rowHasKey(cur.rows.get(t), key);
                if (held && spanStart < 0) {
                    spanStart = t;
                }
                if (!held && spanStart >= 0) {
                    if (t - spanStart >= 2) {
                        SaveFile cand = copy(cur);
                        for (int r = spanStart; r < t; r++) {
                            SaveFile.Row row = cand.rows.get(r);
                            TreeSet<String> ks = keySetOf(row);
                            ks.remove(key);
                            row.keys = new ArrayList<String>(ks);
                            MeasurementEngine.syncMovementSample(cand, r, r);
                        }
                        out.add(new Candidate("deleteHold[" + key + "@" + spanStart + ".." + (t - 1) + "]",
                                cand, true, true, new ArrayList<double[]>()));
                    }
                    spanStart = -1;
                }
            }
        }
    }

    private static boolean isOppositePair(String a, String b) {
        List<String> flipsA = new ArrayList<String>();
        List<String> flipsB = new ArrayList<String>();
        for (String k : flippedKeys(a)) {
            flipsA.add((a.contains("+" + k) ? "+" : "-") + k);
        }
        for (String k : flippedKeys(b)) {
            flipsB.add((b.contains("+" + k) ? "+" : "-") + k);
        }
        if (flipsA.size() != flipsB.size()) {
            return false;
        }
        for (String fa : flipsA) {
            String inverted = (fa.startsWith("+") ? "-" : "+") + fa.substring(1);
            if (!flipsB.contains(inverted)) {
                return false;
            }
        }
        return true;
    }

    private static List<String> flippedKeys(String label) {
        List<String> keys = new ArrayList<String>();
        for (String part : label.split("[+-]")) {
            if (!part.isEmpty()) {
                keys.add(part);
            }
        }
        return keys;
    }

    private static void addNoTurnCandidate(List<Candidate> out, SaveFile cur, JumpMeasurements m) {
        int bestStart = -1;
        int bestLen = 0;
        int runStart = -1;
        for (int k = 0; k <= m.numTicks; k++) {
            boolean wide = k < m.numTicks && m.windowLo[k] + m.windowHi[k] >= NO_TURN_MIN_WINDOW_DEG
                    && !hasDfConstraint(cur, cur.angleSolver.startTick + k);
            if (wide && runStart < 0) {
                runStart = k;
            }
            if (!wide && runStart >= 0) {
                if (k - runStart > bestLen) {
                    bestLen = k - runStart;
                    bestStart = runStart;
                }
                runStart = -1;
            }
        }
        if (bestLen < NO_TURN_MIN_SPAN) {
            return;
        }
        SaveFile cand = copy(cur);
        for (int k = bestStart + 1; k < bestStart + bestLen; k++) {
            addDfZero(cand, cur.angleSolver.startTick + k);
        }
        cand.angleSolver.result = null;
        String span = bestStart + ".." + (bestStart + bestLen - 1);
        out.add(new Candidate("noTurn[" + span + "]~repair", copy(cand), true, true, new ArrayList<double[]>()));
        out.add(new Candidate("noTurn[" + span + "]", cand, true));
    }

    public static final int SOLVE_ATTEMPTS = 1;

    private static void addKeepifyCandidate(List<Candidate> out, SaveFile cur, ExactJumpModel model) {
        boolean f45Default = "FORCE_45".equals(cur.angleSolver.defaultInputs);
        int ovStart = firstF45Override(cur);
        if (!f45Default && ovStart < 0) {
            return;
        }
        JumpSpec spec;
        try {
            spec = MeasurementEngine.buildSpec(cur, model);
        } catch (RuntimeException ex) {
            return;
        }
        if (spec == null) {
            return;
        }
        JumpPhysicsInputs sc0 = spec.asScenario();
        int startTick = cur.angleSolver.startTick;
        int landingTick = cur.angleSolver.landingTick;
        List<Integer> fires = fireTicksAbs(sc0, startTick);
        if (f45Default) {
            SaveFile base = copy(cur);
            base.angleSolver.defaultInputs = "KEEP";
            base.angleSolver.result = null;
            deriveDebugSamples(base, sc0);
            out.add(new Candidate("keepify~repair", copy(base), true, true, new ArrayList<double[]>()));
            double[] aim = aimSeed(base);
            if (aim != null) {
                Candidate c = new Candidate("keepify~aim", copy(base), true, true, new ArrayList<double[]>());
                c.repairSeed = aim;
                out.add(c);
            }
            double[] rot = rotSeedRange(cur, sc0, 0, sc0.numTicks);
            if (rot != null) {
                Candidate c = new Candidate("keepify~rot", copy(base), true, true, new ArrayList<double[]>());
                c.repairSeed = rot;
                out.add(c);
            }
            for (int i = 0; i < SOLVE_ATTEMPTS; i++) {
                out.add(new Candidate(i == 0 ? "keepify" : "keepify#" + (i + 1), copy(base), true));
            }
            if (fires.size() >= 2) {
                int crossEnd = fires.get(1);
                SaveFile arc = copy(cur);
                arc.angleSolver.defaultInputs = "KEEP";
                arc.angleSolver.result = null;
                for (int t = crossEnd; t <= landingTick && t < arc.rows.size(); t++) {
                    setInputOverride(arc, t, "FORCE_45");
                }
                deriveDebugSamples(arc, sc0);
                double[] seed = rotSeedRange(cur, sc0, 0, crossEnd - startTick);
                if (seed != null) {
                    Candidate c = new Candidate("keepifyArc[.." + crossEnd + ")", arc, true, true,
                            new ArrayList<double[]>());
                    c.repairSeed = seed;
                    out.add(c);
                }
            }
        } else {
            int crossEnd = landingTick + 1;
            for (int f : fires) {
                if (f > ovStart) {
                    crossEnd = f;
                    break;
                }
            }
            SaveFile arc = copy(cur);
            arc.angleSolver.result = null;
            for (int t = ovStart; t < crossEnd; t++) {
                clearInputOverride(arc, t);
            }
            deriveDebugSamples(arc, sc0);
            int kFrom = ovStart - startTick;
            int kTo = Math.min(crossEnd, landingTick) - startTick;
            double[] seed = rotSeedRange(cur, sc0, kFrom, kTo);
            if (seed != null) {
                Candidate c = new Candidate("keepifyArc[" + ovStart + ".." + crossEnd + ")", copy(arc), true, true,
                        new ArrayList<double[]>());
                c.repairSeed = seed;
                out.add(c);
            }
            double[] aim = aimSeed(arc);
            if (aim != null && seed != null) {
                double[] spliced = Arrays.copyOf(seed, seed.length);
                for (int k = Math.max(0, kFrom); k < Math.min(spliced.length, kTo); k++) {
                    spliced[k] = aim[k];
                }
                Candidate c = new Candidate("keepifyArc~aim[" + ovStart + ".." + crossEnd + ")", copy(arc), true,
                        true, new ArrayList<double[]>());
                c.repairSeed = spliced;
                out.add(c);
            }
            out.add(new Candidate("keepifyArc!solve", copy(arc), true));
        }
    }

    private static List<Integer> fireTicksAbs(JumpPhysicsInputs sc, int startTick) {
        List<Integer> fires = new ArrayList<Integer>();
        for (int k = 0; k < sc.numTicks; k++) {
            if (sc.jumpAt(k) && MeasurementEngine.groundedAt(sc, k)) {
                fires.add(startTick + k);
            }
        }
        return fires;
    }

    private static int firstF45Override(SaveFile save) {
        int min = -1;
        if (save.angleSolver.ticks != null) {
            for (SaveFile.Tick tick : save.angleSolver.ticks) {
                if (tick != null && tick.override != null && "FORCE_45".equals(tick.override.inputs)
                        && (min < 0 || tick.tick < min)) {
                    min = tick.tick;
                }
            }
        }
        return min;
    }

    private static void setInputOverride(SaveFile save, int absTick, String mode) {
        if (save.angleSolver.ticks == null) {
            save.angleSolver.ticks = new ArrayList<SaveFile.Tick>();
        }
        for (SaveFile.Tick tick : save.angleSolver.ticks) {
            if (tick != null && tick.tick == absTick) {
                if (tick.override == null) {
                    tick.override = new SaveFile.Override();
                }
                tick.override.inputs = mode;
                return;
            }
        }
        SaveFile.Tick tick = new SaveFile.Tick();
        tick.tick = absTick;
        tick.override = new SaveFile.Override();
        tick.override.inputs = mode;
        save.angleSolver.ticks.add(tick);
    }

    private static void clearInputOverride(SaveFile save, int absTick) {
        if (save.angleSolver.ticks == null) {
            return;
        }
        for (SaveFile.Tick tick : save.angleSolver.ticks) {
            if (tick != null && tick.tick == absTick && tick.override != null) {
                tick.override.inputs = null;
            }
        }
    }

    private static double[] rotSeedRange(SaveFile cur, JumpPhysicsInputs sc, int kFrom, int kTo) {
        double[] yaws;
        try {
            yaws = MeasurementEngine.recordedYaws(cur, "rot-seed", sc.numTicks);
        } catch (RuntimeException ex) {
            return null;
        }
        double[] out = Arrays.copyOf(yaws, yaws.length);
        for (int k = Math.max(0, kFrom); k < Math.min(out.length, kTo); k++) {
            if (sc.strafeAt(k)) {
                out[k] -= 45.0 * sc.strafeSign;
            }
        }
        return out;
    }

    private static void addResolveCandidates(List<Candidate> out, SaveFile cur) {
        if (!"KEEP".equals(cur.angleSolver.defaultInputs)) {
            return;
        }
        SaveFile cand = copy(cur);
        cand.angleSolver.result = null;
        out.add(new Candidate("resolve", cand, true));
    }

    private static boolean repairAttach(SaveFile cand, SaveFile cur, ExactJumpModel model, double[] seed) {
        JumpSpec spec;
        try {
            spec = MeasurementEngine.buildSpec(cand, model);
        } catch (RuntimeException ex) {
            return false;
        }
        if (spec == null) {
            return false;
        }
        JumpPhysicsInputs sc = spec.asScenario();
        double[] yaws0;
        if (seed != null) {
            if (seed.length != sc.numTicks) {
                return false;
            }
            yaws0 = seed;
        } else {
            try {
                yaws0 = MeasurementEngine.recordedYaws(cur, "repair-seed", sc.numTicks);
            } catch (RuntimeException ex) {
                return false;
            }
        }
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double[] repaired = repairYaws(model, sc, compiled, yaws0, CANDIDATE_SOLVE_MS);
        if (repaired == null) {
            return false;
        }
        attachYaws(cand, repaired, compiled);
        return true;
    }

    private static void attachYaws(SaveFile cand, double[] yaws, JumpConstraintCompiler.Compiled compiled) {
        SaveFile.Result res = new SaveFile.Result();
        res.success = true;
        res.met = compiled.ineq.size() + compiled.eq.size();
        res.total = res.met;
        res.startTick = cand.angleSolver.startTick + 1;
        res.landingTick = cand.angleSolver.landingTick + 1;
        res.yaws = new ArrayList<SaveFile.Yaw>();
        double[] wrapped = Angles.wrapAll(yaws);
        for (int k = 0; k < wrapped.length; k++) {
            SaveFile.Yaw y = new SaveFile.Yaw();
            y.tick = cand.angleSolver.startTick + k + 1;
            y.yaw = wrapped[k];
            res.yaws.add(y);
        }
        cand.angleSolver.result = res;
    }

    private static double[] aimSeed(SaveFile save) {
        int startTick = save.angleSolver.startTick;
        int n = save.angleSolver.landingTick - startTick;
        SaveFile.Start seed = save.angleSolver.seed;
        if (n <= 0 || seed == null || seed.pos == null || seed.pos.length < 3) {
            return null;
        }
        List<double[]> waypoints = new ArrayList<double[]>();
        waypoints.add(new double[]{0, seed.pos[0], seed.pos[2]});
        if (save.angleSolver.ticks != null) {
            List<SaveFile.Tick> sorted = new ArrayList<SaveFile.Tick>(save.angleSolver.ticks);
            sorted.sort((a, b) -> Integer.compare(a != null ? a.tick : 0, b != null ? b.tick : 0));
            for (SaveFile.Tick tick : sorted) {
                if (tick == null || tick.constraints == null || tick.tick <= startTick
                        || tick.tick > save.angleSolver.landingTick) {
                    continue;
                }
                double[] x = rangeOf(tick, "X");
                double[] z = rangeOf(tick, "Z");
                if (x == null || z == null) {
                    continue;
                }
                waypoints.add(new double[]{tick.tick - startTick,
                        0.5 * (x[0] + x[1]), 0.5 * (z[0] + z[1])});
            }
        }
        if (waypoints.size() < 2) {
            return null;
        }
        double[] yaws = new double[n];
        int wp = 0;
        for (int k = 0; k < n; k++) {
            while (wp + 1 < waypoints.size() - 1 && waypoints.get(wp + 1)[0] <= k) {
                wp++;
            }
            double[] from = waypoints.get(wp);
            double[] to = waypoints.get(Math.min(wp + 1, waypoints.size() - 1));
            double dx = to[1] - from[1];
            double dz = to[2] - from[2];
            if (Math.abs(dx) < 1.0e-12 && Math.abs(dz) < 1.0e-12) {
                yaws[k] = k > 0 ? yaws[k - 1] : seed.yaw;
            } else {
                yaws[k] = Math.toDegrees(Math.atan2(-dx, dz));
            }
        }
        return yaws;
    }

    private static double[] rangeOf(SaveFile.Tick tick, String field) {
        for (SaveFile.Constraint c : tick.constraints) {
            if (c != null && !c.disabled && c.range && c.refTick == null && field.equals(c.field)) {
                return new double[]{c.lo, c.hi};
            }
        }
        return null;
    }

    private static void addAimLineCandidate(List<Candidate> out, SaveFile cur) {
        if (!"KEEP".equals(cur.angleSolver.defaultInputs) || cur.angleSolver.result == null
                || firstF45Override(cur) >= 0) {
            return;
        }
        double[] seed = aimSeed(cur);
        if (seed == null) {
            return;
        }
        Candidate c = new Candidate("aimLine", copy(cur), true, true, new ArrayList<double[]>());
        c.repairSeed = seed;
        out.add(c);
    }

    private static void addDeepenCandidate(List<Candidate> out, SaveFile cur) {
        if (cur.angleSolver.result == null) {
            return;
        }
        out.add(new Candidate("deepen", copy(cur), false, false, true, new ArrayList<double[]>()));
    }

    private static boolean deepenAttach(SaveFile cand, ExactJumpModel model) {
        JumpSpec spec;
        try {
            spec = MeasurementEngine.buildSpec(cand, model);
        } catch (RuntimeException ex) {
            return false;
        }
        if (spec == null) {
            return false;
        }
        JumpPhysicsInputs sc = spec.asScenario();
        double[] y0;
        try {
            y0 = MeasurementEngine.recordedYaws(cand, "deepen-seed", sc.numTicks);
        } catch (RuntimeException ex) {
            return false;
        }
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        double[] deepened = deepenYaws(model, sc, compiled, y0, CANDIDATE_SOLVE_MS);
        if (deepened == null) {
            return false;
        }
        attachYaws(cand, deepened, compiled);
        return true;
    }

    private static double[] deepenYaws(ExactJumpModel model, JumpPhysicsInputs sc,
                                       JumpConstraintCompiler.Compiled compiled, double[] y0, long budgetMs) {
        double[] y = Arrays.copyOf(y0, y0.length);
        double start = interiorMargin(model, sc, compiled, y);
        if (Double.isNaN(start) || start < 0.0) {
            return null;
        }
        double best = start;
        long deadline = System.nanoTime() + budgetMs * 1_000_000L;
        boolean improved = true;
        while (improved && System.nanoTime() < deadline) {
            improved = false;
            for (int k = 0; k < y.length; k++) {
                boolean tickImproved = true;
                while (tickImproved && System.nanoTime() < deadline) {
                    tickImproved = false;
                    for (double step : REPAIR_STEPS) {
                        for (int sign = -1; sign <= 1; sign += 2) {
                            double old = y[k];
                            y[k] = old + sign * step;
                            double nm = interiorMargin(model, sc, compiled, y);
                            if (!Double.isNaN(nm) && nm > best + 1.0e-12) {
                                best = nm;
                                tickImproved = true;
                                improved = true;
                            } else {
                                y[k] = old;
                            }
                        }
                        if (tickImproved) {
                            break;
                        }
                    }
                }
            }
        }
        return best > start + 1.0e-9 ? y : null;
    }

    private static double interiorMargin(ExactJumpModel model, JumpPhysicsInputs sc,
                                         JumpConstraintCompiler.Compiled compiled, double[] yaws) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath path = model.forward(sc, gf);
        for (JumpConstraint c : compiled.eq) {
            if (Math.abs(JumpConstraintCompiler.evaluate(c, gf, path)) > 1.0e-9) {
                return Double.NaN;
            }
        }
        double min = Double.POSITIVE_INFINITY;
        for (JumpConstraint c : compiled.ineq) {
            double e = JumpConstraintCompiler.evaluate(c, gf, path);
            double margin = c.cmp == JumpConstraint.Cmp.GE ? e : -e;
            min = Math.min(min, margin);
        }
        return min;
    }

    private static final double[] REPAIR_STEPS = {8.0, 4.0, 2.0, 1.0, 0.5, 0.25, 0.1, 0.05, 0.02, 0.01,
            0.005, 0.002, 0.001};

    private static double[] repairYaws(ExactJumpModel model, JumpPhysicsInputs sc,
                                       JumpConstraintCompiler.Compiled compiled, double[] yaws0, long budgetMs) {
        double[] y = Arrays.copyOf(yaws0, yaws0.length);
        double v = MeasurementEngine.violation(model, sc, compiled, y);
        if (v <= 0.0) {
            return y;
        }
        long deadline = System.nanoTime() + budgetMs * 1_000_000L;
        boolean improved = true;
        while (improved && v > 0.0 && System.nanoTime() < deadline) {
            improved = false;
            for (int k = 0; k < y.length && v > 0.0; k++) {
                boolean tickImproved = true;
                while (tickImproved && v > 0.0 && System.nanoTime() < deadline) {
                    tickImproved = false;
                    for (double step : REPAIR_STEPS) {
                        for (int sign = -1; sign <= 1; sign += 2) {
                            double old = y[k];
                            y[k] = old + sign * step;
                            double nv = MeasurementEngine.violation(model, sc, compiled, y);
                            if (nv < v) {
                                v = nv;
                                tickImproved = true;
                                improved = true;
                            } else {
                                y[k] = old;
                            }
                        }
                        if (tickImproved) {
                            break;
                        }
                    }
                }
            }
            for (int k = 0; k < y.length && v > 0.0 && System.nanoTime() < deadline; k++) {
                boolean tailImproved = true;
                while (tailImproved && v > 0.0 && System.nanoTime() < deadline) {
                    tailImproved = false;
                    for (double step : REPAIR_STEPS) {
                        for (int sign = -1; sign <= 1; sign += 2) {
                            double delta = sign * step;
                            for (int j = k; j < y.length; j++) {
                                y[j] += delta;
                            }
                            double nv = MeasurementEngine.violation(model, sc, compiled, y);
                            if (nv < v) {
                                v = nv;
                                tailImproved = true;
                                improved = true;
                            } else {
                                for (int j = k; j < y.length; j++) {
                                    y[j] -= delta;
                                }
                            }
                        }
                        if (tailImproved) {
                            break;
                        }
                    }
                }
            }
        }
        return v <= 0.0 ? y : null;
    }

    public static void deriveDebugSamples(SaveFile save, JumpPhysicsInputs sc0) {
        de.legoshi.parkourcalc.core.anglesolver.stratfinder.StratVariants.deriveDebugSamples(save, sc0);
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

    private static int lastEditableRow(SaveFile save) {
        int n = save.angleSolver.landingTick - save.angleSolver.startTick;
        return Math.min(save.angleSolver.startTick + n, save.rows.size() - 1);
    }

    private static boolean rowHasKey(SaveFile.Row row, String key) {
        return row.keys != null && row.keys.contains(key);
    }

    private static TreeSet<String> keySetOf(SaveFile.Row row) {
        TreeSet<String> set = new TreeSet<String>();
        if (row.keys != null) {
            set.addAll(row.keys);
        }
        return set;
    }

    private static SaveFile copy(SaveFile file) {
        return GSON.fromJson(GSON.toJson(file), SaveFile.class);
    }
}
