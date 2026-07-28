package de.legoshi.parkourcalc.anglesolver.metriclab;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

public final class MeasurementEngine {

    public static final double COARSE_STEP_DEG = 0.25;
    public static final double WINDOW_CAP_DEG = 180.0;
    public static final double BISECT_TOL_DEG = 0.001;
    public static final int JITTER_SAMPLES = 128;
    public static final double TURN_EPS_DEG = 1.0e-9;
    public static final int CENTER_PASSES = 6;
    public static final double CENTER_STOP_DEG = 0.05;
    public static final int SHIFT_CAP_TICKS = 5;
    public static final int JUMP_HOLD_COOLDOWN_TICKS = 10;
    public static final float KEY_INPUT_SCALE = 0.98F;
    public static final float SNEAK_INPUT_SCALE = 0.3F;

    private static final Gson GSON = new Gson();

    private MeasurementEngine() {
    }

    public static JumpMeasurements measure(HpkHumanSet.Sample sample) {
        JumpMeasurements m = measure(sample.save, sample.name);
        m.dLevel = sample.dLevel;
        m.meta = sample.meta;
        return m;
    }

    public static JumpMeasurements measure(SaveFile file, String name) {
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        JumpSpec spec = buildSpec(file, model);
        if (spec == null) {
            throw new IllegalStateException(name + ": engine built no spec");
        }
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        SaveFile.Result recorded = file.angleSolver.result;
        if (recorded != null && !recorded.success) {
            throw new BadSampleException(name + ": recorded result is marked success=false (met "
                    + recorded.met + "/" + recorded.total + "), re-solve and re-save before measuring");
        }
        if (file.angleSolver.ticks != null) {
            for (SaveFile.Tick tick : file.angleSolver.ticks) {
                if (tick == null || tick.constraints == null) {
                    continue;
                }
                for (SaveFile.Constraint c : tick.constraints) {
                    if (c != null && !c.disabled
                            && (tick.tick < file.angleSolver.startTick || tick.tick > file.angleSolver.landingTick)) {
                        throw new BadSampleException(name + ": enabled constraint at tick " + tick.tick
                                + " lies outside the solve segment [" + file.angleSolver.startTick + ", "
                                + file.angleSolver.landingTick + "] and is silently dropped on replay, "
                                + "fix the constraint tick or landingTick and re-save");
                    }
                }
            }
        }
        JumpConstraintCompiler.Compiled oracle = landingOracle(compiled);
        double[] yawVec = recordedYaws(file, name, sc.numTicks);

        double viol0 = violation(model, sc, compiled, yawVec);
        if (viol0 > 0.0) {
            throw new IllegalStateException(name + ": recorded solve does not replay feasible, viol=" + viol0);
        }

        double[] anchor = centeredAnchor(model, sc, oracle, yawVec);

        int n = sc.numTicks;
        double[] lo = new double[n];
        double[] hi = new double[n];
        for (int k = 0; k < n; k++) {
            hi[k] = window(model, sc, oracle, anchor, k, 1.0);
            lo[k] = window(model, sc, oracle, anchor, k, -1.0);
        }

        JumpMeasurements m = new JumpMeasurements();
        m.name = name;
        m.mcVersion = file.mcVersion;
        m.numTicks = n;
        m.facingConstraints = (compiled.ineq.size() + compiled.eq.size())
                - (oracle.ineq.size() + oracle.eq.size());
        m.minMargin = minMargin(model, sc, oracle, anchor);
        m.centerDriftDeg = maxDrift(anchor, yawVec);
        m.windowLo = lo;
        m.windowHi = hi;

        int takeoff = 0;
        int jumps = 0;
        for (int k = 0; k < n; k++) {
            if (sc.jumpAt(k)) {
                jumps++;
                takeoff = k;
            }
        }
        m.jumps = jumps;
        m.takeoffTick = takeoff;

        m.jitterDeg = jitterRadius(model, sc, oracle, anchor, minNarrowSide(lo, hi), name);

        m.winMinDeg = minWidth(lo, hi, 0, n);
        m.winGeoDeg = geoWidth(lo, hi, 0, n);
        m.winMinMomentumDeg = minWidth(lo, hi, 0, takeoff);
        m.winGeoMomentumDeg = geoWidth(lo, hi, 0, takeoff);
        m.winMinJumpDeg = minWidth(lo, hi, takeoff, n);
        m.winGeoJumpDeg = geoWidth(lo, hi, takeoff, n);

        double prev = sc.startYaw;
        for (int k = 0; k < n; k++) {
            boolean turned = Math.abs(Angles.wrapDelta(yawVec[k] - prev)) > TURN_EPS_DEG;
            if (turned) {
                if (k < takeoff) {
                    m.turnTicksMomentum++;
                } else {
                    m.turnTicksJump++;
                }
                if (k == takeoff) {
                    m.jumpAngle = true;
                }
            }
            prev = yawVec[k];
        }

        countInputEdges(file, m, file.angleSolver.startTick + takeoff + 1, file.angleSolver.startTick + n);

        long tShift = System.nanoTime();
        measureShiftWindows(file, name, model, sc, m, yawVec);
        m.shiftMs = (System.nanoTime() - tShift) / 1_000_000L;

        SolveRunRecord.Outcome smooth = new SolveRunRecord.Outcome();
        SolveRunRecord.smoothnessOf(smooth, yawVec);
        m.yawTravelDeg = smooth.yawTravelDeg != null ? smooth.yawTravelDeg : 0.0;
        m.yawReversals = smooth.yawDirChanges != null ? smooth.yawDirChanges : 0;
        m.yawJerkDeg = smooth.yawJerkDeg != null ? smooth.yawJerkDeg : 0.0;
        return m;
    }

    private static JumpConstraintCompiler.Compiled landingOracle(JumpConstraintCompiler.Compiled compiled) {
        java.util.List<JumpConstraint> ineq = new java.util.ArrayList<JumpConstraint>();
        java.util.List<JumpConstraint> eq = new java.util.ArrayList<JumpConstraint>();
        for (JumpConstraint c : compiled.ineq) {
            if (c.mode != JumpConstraint.Mode.F) {
                ineq.add(c);
            }
        }
        for (JumpConstraint c : compiled.eq) {
            if (c.mode != JumpConstraint.Mode.F) {
                eq.add(c);
            }
        }
        return new JumpConstraintCompiler.Compiled(ineq, eq);
    }

    private static double[] centeredAnchor(ExactJumpModel model, JumpPhysicsInputs sc,
                                           JumpConstraintCompiler.Compiled compiled, double[] recorded) {
        double[] anchor = Arrays.copyOf(recorded, recorded.length);
        for (int pass = 0; pass < CENTER_PASSES; pass++) {
            double maxShift = 0.0;
            for (int k = 0; k < anchor.length; k++) {
                double hi = window(model, sc, compiled, anchor, k, 1.0);
                double lo = window(model, sc, compiled, anchor, k, -1.0);
                double shift = 0.5 * (hi - lo);
                anchor[k] += shift;
                maxShift = Math.max(maxShift, Math.abs(shift));
            }
            if (maxShift < CENTER_STOP_DEG) {
                break;
            }
        }
        return anchor;
    }

    private static double maxDrift(double[] anchor, double[] recorded) {
        double max = 0.0;
        for (int k = 0; k < anchor.length; k++) {
            max = Math.max(max, Math.abs(Angles.wrapDelta(anchor[k] - recorded[k])));
        }
        return max;
    }

    public static double minNarrowSide(double[] lo, double[] hi) {
        double min = WINDOW_CAP_DEG;
        for (int k = 0; k < lo.length; k++) {
            min = Math.min(min, Math.min(lo[k], hi[k]));
        }
        return min;
    }

    private static void countInputEdges(SaveFile file, JumpMeasurements m, int takeoffRow, int lastRow) {
        int end = Math.min(lastRow, file.rows.size() - 1);
        TreeSet<String> prev = keySet(file.rows.get(0));
        for (int t = 1; t <= end; t++) {
            TreeSet<String> cur = keySet(file.rows.get(t));
            if (!cur.equals(prev)) {
                if (t < takeoffRow) {
                    m.inputEdgesMomentum++;
                } else {
                    m.inputEdgesJump++;
                }
            }
            prev = cur;
        }
    }

    private static TreeSet<String> keySet(SaveFile.Row row) {
        TreeSet<String> set = new TreeSet<String>();
        if (row.keys != null) {
            set.addAll(row.keys);
        }
        return set;
    }

    private static void measureShiftWindows(SaveFile file, String name, ExactJumpModel model,
                                            JumpPhysicsInputs sc0, JumpMeasurements m, double[] yawVec) {
        int n = sc0.numTicks;
        int startTick = file.angleSolver.startTick;
        int takeoffRow = startTick + m.takeoffTick + 1;
        int end = Math.min(startTick + n, file.rows.size() - 1);
        List<Integer> edges = new ArrayList<Integer>();
        TreeSet<String> prev = keySet(file.rows.get(0));
        for (int t = 1; t <= end; t++) {
            TreeSet<String> cur = keySet(file.rows.get(t));
            if (!cur.equals(prev)) {
                edges.add(t);
            }
            prev = cur;
        }
        String baseJson = GSON.toJson(file);
        if (!probeShift(baseJson, model, sc0, yawVec, 1, 0, startTick)) {
            throw new IllegalStateException(name + ": zero-shift probe does not replay feasible, copy machinery broken");
        }

        int e = edges.size();
        m.shiftEdgeRow = new int[e];
        m.shiftEdgeMomentum = new boolean[e];
        m.shiftEdgeKeys = new String[e];
        m.shiftLo = new int[e];
        m.shiftHi = new int[e];
        m.shiftLoCensored = new boolean[e];
        m.shiftHiCensored = new boolean[e];
        m.shiftLoFree = new boolean[e];
        m.shiftHiFree = new boolean[e];
        for (int i = 0; i < e; i++) {
            int t = edges.get(i);
            m.shiftEdgeRow[i] = t;
            m.shiftEdgeMomentum[i] = t < takeoffRow;
            m.shiftEdgeKeys[i] = flippedKeysLabel(file.rows.get(t - 1), file.rows.get(t));
            boolean pureRelease = isPureRelease(file, t);
            int spanStart = pureRelease ? releasedSpanStart(file, t) : -1;
            for (int side = -1; side <= 1; side += 2) {
                int count = 0;
                boolean censored = false;
                boolean failed = false;
                for (int step = 1; step <= SHIFT_CAP_TICKS; step++) {
                    int dest = t + side * step;
                    if (dest < 0 || dest > end) {
                        censored = true;
                        break;
                    }
                    if (!probeShift(baseJson, model, sc0, yawVec, t, side * step, startTick)) {
                        failed = !(side < 0 && pureRelease && dest <= spanStart);
                        break;
                    }
                    count++;
                }
                if (side < 0) {
                    m.shiftLo[i] = count;
                    m.shiftLoCensored[i] = censored;
                    m.shiftLoFree[i] = !failed;
                } else {
                    m.shiftHi[i] = count;
                    m.shiftHiCensored[i] = censored;
                    m.shiftHiFree[i] = !failed;
                }
            }
        }
        m.shiftMinMomentumTicks = shiftMin(m, true);
        m.shiftGeoMomentumTicks = shiftGeo(m, true);
        m.shiftMinJumpTicks = shiftMin(m, false);
        m.shiftGeoJumpTicks = shiftGeo(m, false);
        m.shiftGeoMomentumEffTicks = shiftGeoEff(m, true);
        m.shiftGeoJumpEffTicks = shiftGeoEff(m, false);
    }

    private static boolean isPureRelease(SaveFile file, int t) {
        TreeSet<String> pre = keySet(file.rows.get(t - 1));
        TreeSet<String> post = keySet(file.rows.get(t));
        boolean any = false;
        for (String k : pre) {
            if (!post.contains(k)) {
                any = true;
            }
        }
        for (String k : post) {
            if (!pre.contains(k)) {
                return false;
            }
        }
        return any;
    }

    private static int releasedSpanStart(SaveFile file, int t) {
        TreeSet<String> pre = keySet(file.rows.get(t - 1));
        TreeSet<String> post = keySet(file.rows.get(t));
        int min = t;
        for (String k : pre) {
            if (post.contains(k)) {
                continue;
            }
            int p = 0;
            for (int r = t - 1; r >= 1; r--) {
                boolean cur = keySet(file.rows.get(r)).contains(k);
                boolean before = keySet(file.rows.get(r - 1)).contains(k);
                if (cur != before) {
                    p = r;
                    break;
                }
            }
            min = Math.min(min, p);
        }
        return min;
    }

    public static double effWidth(JumpMeasurements m, int i) {
        if (m.shiftLoFree[i] || m.shiftHiFree[i]) {
            return 2.0 * SHIFT_CAP_TICKS;
        }
        return m.shiftLo[i] + m.shiftHi[i];
    }

    private static double shiftGeoEff(JumpMeasurements m, boolean momentum) {
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < m.shiftEdgeRow.length; i++) {
            if (m.shiftEdgeMomentum[i] != momentum) {
                continue;
            }
            sum += Math.log1p(effWidth(m, i));
            count++;
        }
        if (count == 0) {
            return Double.NaN;
        }
        return Math.expm1(sum / count);
    }

    private static double shiftMin(JumpMeasurements m, boolean momentum) {
        double min = Double.NaN;
        for (int i = 0; i < m.shiftEdgeRow.length; i++) {
            if (m.shiftEdgeMomentum[i] != momentum) {
                continue;
            }
            double w = m.shiftLo[i] + m.shiftHi[i];
            if (Double.isNaN(min) || w < min) {
                min = w;
            }
        }
        return min;
    }

    private static double shiftGeo(JumpMeasurements m, boolean momentum) {
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < m.shiftEdgeRow.length; i++) {
            if (m.shiftEdgeMomentum[i] != momentum) {
                continue;
            }
            sum += Math.log1p(m.shiftLo[i] + m.shiftHi[i]);
            count++;
        }
        if (count == 0) {
            return Double.NaN;
        }
        return Math.expm1(sum / count);
    }

    private static String flippedKeysLabel(SaveFile.Row before, SaveFile.Row after) {
        TreeSet<String> pre = keySet(before);
        TreeSet<String> post = keySet(after);
        StringBuilder sb = new StringBuilder();
        for (String k : post) {
            if (!pre.contains(k)) {
                sb.append('+').append(k);
            }
        }
        for (String k : pre) {
            if (!post.contains(k)) {
                sb.append('-').append(k);
            }
        }
        return sb.toString();
    }

    private static boolean probeShift(String baseJson, ExactJumpModel model, JumpPhysicsInputs sc0,
                                      double[] yawVec, int edgeRow, int shift, int startTick) {
        SaveFile copy = GSON.fromJson(baseJson, SaveFile.class);
        if (copy == null) {
            return false;
        }
        List<double[]> support = new ArrayList<double[]>();
        if (!applyShift(copy, edgeRow, shift, startTick, sc0, support)) {
            return false;
        }
        JumpSpec spec;
        try {
            spec = buildSpec(copy, model);
        } catch (RuntimeException ex) {
            return false;
        }
        if (spec == null) {
            return false;
        }
        JumpPhysicsInputs sc = spec.asScenario();
        if (sc.numTicks != yawVec.length) {
            return false;
        }
        JumpConstraintCompiler.Compiled oracle = landingOracle(JumpConstraintCompiler.compile(spec));
        double[] gf = sc.toGameFacings(Angles.wrapAll(yawVec));
        ForwardPath path = model.forward(sc, gf);
        if (oracle.maxViolation(gf, path) > 0.0) {
            return false;
        }
        return supported(path, support);
    }

    static boolean applyShift(SaveFile copy, int t, int shift, int startTick, JumpPhysicsInputs sc0,
                              List<double[]> supportOut) {
        if (shift == 0) {
            return true;
        }
        List<SaveFile.Row> rows = copy.rows;
        TreeSet<String> pre = keySet(rows.get(t - 1));
        TreeSet<String> post = keySet(rows.get(t));
        TreeSet<String> flipped = new TreeSet<String>();
        for (String k : pre) {
            if (!post.contains(k)) {
                flipped.add(k);
            }
        }
        for (String k : post) {
            if (!pre.contains(k)) {
                flipped.add(k);
            }
        }
        boolean sprintFlip = flipped.contains("SPRINT");
        int prevJumpChange = flipped.contains("JUMP") ? previousKeyChangeRow(copy, t, "JUMP") : -1;
        int from = shift > 0 ? t : t + shift;
        int to = shift > 0 ? t + shift : t;
        TreeSet<String> srcState = shift > 0 ? pre : post;
        int srcRow = shift > 0 ? t - 1 : t;
        for (int r = from; r < to; r++) {
            SaveFile.Row row = rows.get(r);
            TreeSet<String> ks = keySet(row);
            for (String k : flipped) {
                if (srcState.contains(k)) {
                    ks.add(k);
                } else {
                    ks.remove(k);
                }
            }
            row.keys = new ArrayList<String>(ks);
            syncMovementSample(copy, r, srcRow, !sprintFlip);
        }
        if (sprintFlip && !shiftSprintFlag(copy, t, shift)) {
            return false;
        }
        if (flipped.contains("JUMP")) {
            if (post.contains("JUMP")) {
                return shiftJumpArc(copy, t, shift, startTick, sc0, supportOut, prevJumpChange);
            }
            if (shift > 0 && !heldJumpExtensionAllowed(sc0, startTick, t, t + shift)) {
                return false;
            }
        }
        return true;
    }

    private static int previousKeyChangeRow(SaveFile copy, int t, String key) {
        for (int r = t - 1; r >= 1; r--) {
            boolean cur = keySet(copy.rows.get(r)).contains(key);
            boolean before = keySet(copy.rows.get(r - 1)).contains(key);
            if (cur != before) {
                return r;
            }
        }
        return -1;
    }

    private static boolean shiftSprintFlag(SaveFile copy, int t, int shift) {
        if (copy.debug == null) {
            return true;
        }
        int trans = t + 1;
        if (trans >= copy.debug.size()) {
            return true;
        }
        boolean preFlag = copy.debug.get(trans - 1).sprinting;
        boolean postFlag = copy.debug.get(trans).sprinting;
        if (preFlag == postFlag) {
            return true;
        }
        int from = shift > 0 ? trans : trans + shift;
        int to = shift > 0 ? trans + shift : trans;
        boolean value = shift > 0 ? preFlag : postFlag;
        if (from < 1 || to > copy.debug.size()) {
            return false;
        }
        for (int i = from; i < to; i++) {
            if (value) {
                TreeSet<String> ks = keySet(copy.rows.get(i - 1));
                if (!ks.contains("W") || ks.contains("S") || ks.contains("SNEAK")) {
                    return false;
                }
            }
            copy.debug.get(i).sprinting = value;
        }
        return true;
    }

    private static boolean heldJumpExtensionAllowed(JumpPhysicsInputs sc0, int startTick, int fromRow, int toRow) {
        int prevFire = -1;
        for (int r = fromRow; r < toRow; r++) {
            int k = r - startTick;
            if (k < 0 || k >= sc0.numTicks || !groundedAt(sc0, k)) {
                continue;
            }
            int last = prevFire >= 0 ? prevFire : lastFireBefore(sc0, k);
            if (last >= 0 && k - last < JUMP_HOLD_COOLDOWN_TICKS) {
                return false;
            }
            prevFire = k;
        }
        return true;
    }

    private static int lastFireBefore(JumpPhysicsInputs sc0, int kExclusive) {
        for (int k = Math.min(kExclusive, sc0.numTicks) - 1; k >= 0; k--) {
            if (sc0.jumpAt(k) && groundedAt(sc0, k)) {
                return k;
            }
        }
        return -1;
    }

    static void syncMovementSample(SaveFile copy, int row, int srcRow) {
        syncMovementSample(copy, row, srcRow, true);
    }

    static void syncMovementSample(SaveFile copy, int row, int srcRow, boolean syncSprint) {
        if (copy.debug == null || row + 1 >= copy.debug.size()) {
            return;
        }
        SaveFile.DebugTick d = copy.debug.get(row + 1);
        TreeSet<String> ks = keySet(copy.rows.get(row));
        float scale = KEY_INPUT_SCALE * (ks.contains("SNEAK") ? SNEAK_INPUT_SCALE : 1.0F);
        d.moveForward = scale * ((ks.contains("W") ? 1 : 0) - (ks.contains("S") ? 1 : 0));
        d.moveStrafe = scale * ((ks.contains("A") ? 1 : 0) - (ks.contains("D") ? 1 : 0));
        if (!syncSprint) {
            return;
        }
        boolean srcSprint = true;
        if (srcRow + 1 < copy.debug.size()) {
            srcSprint = copy.debug.get(srcRow + 1).sprinting;
        }
        d.sprinting = srcSprint && ks.contains("W") && !ks.contains("S");
    }

    private static boolean shiftJumpArc(SaveFile copy, int t, int shift, int startTick, JumpPhysicsInputs sc0,
                                        List<double[]> supportOut, int prevJumpChange) {
        int n = sc0.numTicks;
        int kPress = t - startTick;
        if (kPress < 0 || kPress >= n || !groundedAt(sc0, kPress)) {
            return true;
        }
        int fire0 = kPress;
        int airStart = fire0 + 1;
        while (airStart < n && groundedAt(sc0, airStart)) {
            airStart++;
        }
        int arcEnd = airStart;
        while (arcEnd < n && !groundedAt(sc0, arcEnd)) {
            arcEnd++;
        }
        int fireNew;
        if (shift > 0) {
            List<String> destKeys = copy.rows.get(t + shift).keys;
            if (destKeys == null || !destKeys.contains("JUMP")) {
                return true;
            }
            fireNew = fire0 + shift;
        } else {
            fireNew = fire0 + shift;
            while (fireNew < fire0 && !groundedAt(sc0, fireNew)) {
                fireNew++;
            }
            if (prevJumpChange >= 0 && t + shift <= prevJumpChange) {
                int prevFire = lastFireBefore(sc0, fire0);
                if (prevFire >= 0 && fireNew - prevFire < JUMP_HOLD_COOLDOWN_TICKS) {
                    return false;
                }
            }
        }
        int d = fireNew - fire0;
        if (d == 0) {
            return true;
        }
        if (d > 0) {
            double[] takeoffSupport = blockCheckpoint(copy, startTick + Math.max(fire0 - 1, 0), startTick + fire0, true);
            if (takeoffSupport == null) {
                return false;
            }
            String takeoffSlip = slipNameAt(copy, startTick + fire0);
            for (int k = airStart; k < airStart + d; k++) {
                if (k >= n || groundedAt(sc0, k)) {
                    return false;
                }
                setSlipOverride(copy, startTick + k, takeoffSlip);
                supportOut.add(new double[]{k, takeoffSupport[0], takeoffSupport[1], takeoffSupport[2], takeoffSupport[3]});
            }
            for (int k = arcEnd; k < Math.min(arcEnd + d, n); k++) {
                if (!groundedAt(sc0, k)) {
                    return false;
                }
                setSlipOverride(copy, startTick + k, "AIR");
            }
        } else {
            for (int k = airStart + d; k < airStart; k++) {
                if (k < 0 || !groundedAt(sc0, k)) {
                    return false;
                }
                setSlipOverride(copy, startTick + k, "AIR");
            }
            int landFrom = Math.max(arcEnd + d, 0);
            if (landFrom < arcEnd) {
                double[] landSupport = blockCheckpoint(copy, startTick + arcEnd - 1, startTick + n, false);
                if (landSupport == null) {
                    return false;
                }
                String landSlip = arcEnd < n ? slipNameAt(copy, startTick + arcEnd) : slipNameAt(copy, startTick + fire0);
                for (int k = landFrom; k < arcEnd; k++) {
                    if (arcEnd < n && groundedAt(sc0, k)) {
                        return false;
                    }
                    setSlipOverride(copy, startTick + k, landSlip);
                    supportOut.add(new double[]{k, landSupport[0], landSupport[1], landSupport[2], landSupport[3]});
                }
            }
        }
        return true;
    }

    private static double[] blockCheckpoint(SaveFile copy, int fromAbsTick, int toAbsTick, boolean pickLatest) {
        double[] best = null;
        int bestTick = -1;
        if (copy.angleSolver.ticks == null) {
            return null;
        }
        for (SaveFile.Tick tick : copy.angleSolver.ticks) {
            if (tick == null || tick.constraints == null || tick.tick < fromAbsTick || tick.tick > toAbsTick) {
                continue;
            }
            double[] x = intervalOf(tick, "X");
            double[] z = intervalOf(tick, "Z");
            if (x == null || z == null) {
                continue;
            }
            boolean better = best == null || (pickLatest ? tick.tick > bestTick : tick.tick < bestTick);
            if (better) {
                best = new double[]{x[0], x[1], z[0], z[1]};
                bestTick = tick.tick;
            }
        }
        return best;
    }

    private static double[] intervalOf(SaveFile.Tick tick, String field) {
        for (SaveFile.Constraint c : tick.constraints) {
            if (c != null && !c.disabled && c.range && c.refTick == null && field.equals(c.field)) {
                return new double[]{c.lo, c.hi};
            }
        }
        return null;
    }

    static boolean supported(ForwardPath path, List<double[]> support) {
        for (double[] req : support) {
            int k = (int) req[0];
            double x = path.posX[k];
            double z = path.posZ[k];
            if (x < req[1] || x > req[2] || z < req[3] || z > req[4]) {
                return false;
            }
        }
        return true;
    }

    static boolean groundedAt(JumpPhysicsInputs sc, int k) {
        if (k < 0 || k >= sc.numTicks) {
            return false;
        }
        double slip = sc.slipAt(k);
        return !Double.isNaN(slip) && slip < 1.0;
    }

    private static String slipNameAt(SaveFile copy, int absTick) {
        if (copy.angleSolver.ticks != null) {
            for (SaveFile.Tick tk : copy.angleSolver.ticks) {
                if (tk != null && tk.tick == absTick && tk.override != null && tk.override.slipperiness != null) {
                    return tk.override.slipperiness;
                }
            }
        }
        return copy.angleSolver.defaultSlipperiness != null ? copy.angleSolver.defaultSlipperiness : "AIR";
    }

    private static void setSlipOverride(SaveFile copy, int absTick, String slipName) {
        if (copy.angleSolver.ticks == null) {
            copy.angleSolver.ticks = new ArrayList<SaveFile.Tick>();
        }
        for (SaveFile.Tick tk : copy.angleSolver.ticks) {
            if (tk != null && tk.tick == absTick) {
                if (tk.override == null) {
                    tk.override = new SaveFile.Override();
                }
                tk.override.slipperiness = slipName;
                return;
            }
        }
        SaveFile.Tick tk = new SaveFile.Tick();
        tk.tick = absTick;
        tk.override = new SaveFile.Override();
        tk.override.slipperiness = slipName;
        copy.angleSolver.ticks.add(tk);
    }

    private static double window(ExactJumpModel model, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled compiled,
                                 double[] base, int k, double sign) {
        int steps = (int) Math.round(WINDOW_CAP_DEG / COARSE_STEP_DEG);
        double lastGood = 0.0;
        double firstBad = Double.NaN;
        for (int i = 1; i <= steps; i++) {
            double d = i * COARSE_STEP_DEG;
            if (feasibleAt(model, sc, compiled, base, k, sign * d)) {
                lastGood = d;
            } else {
                firstBad = d;
                break;
            }
        }
        if (Double.isNaN(firstBad)) {
            return WINDOW_CAP_DEG;
        }
        while (firstBad - lastGood > BISECT_TOL_DEG) {
            double mid = 0.5 * (lastGood + firstBad);
            if (feasibleAt(model, sc, compiled, base, k, sign * mid)) {
                lastGood = mid;
            } else {
                firstBad = mid;
            }
        }
        return lastGood;
    }

    private static double jitterRadius(ExactJumpModel model, JumpPhysicsInputs sc,
                                       JumpConstraintCompiler.Compiled compiled, double[] base, double cap,
                                       String seedName) {
        if (cap <= 0.0) {
            return 0.0;
        }
        int n = base.length;
        Random rnd = new Random(seedName.hashCode() * 1_000_003L + n);
        double[][] units = new double[JITTER_SAMPLES][n];
        for (int s = 0; s < JITTER_SAMPLES; s++) {
            for (int k = 0; k < n; k++) {
                units[s][k] = 2.0 * rnd.nextDouble() - 1.0;
            }
        }
        if (allLand(model, sc, compiled, base, units, cap)) {
            return cap;
        }
        double good = 0.0;
        double bad = cap;
        while (bad - good > BISECT_TOL_DEG) {
            double mid = 0.5 * (good + bad);
            if (allLand(model, sc, compiled, base, units, mid)) {
                good = mid;
            } else {
                bad = mid;
            }
        }
        return good;
    }

    private static boolean allLand(ExactJumpModel model, JumpPhysicsInputs sc,
                                   JumpConstraintCompiler.Compiled compiled, double[] base, double[][] units,
                                   double e) {
        int n = base.length;
        double[] probe = new double[n];
        for (int s = 0; s < units.length; s++) {
            for (int k = 0; k < n; k++) {
                probe[k] = base[k] + e * units[s][k];
            }
            if (violation(model, sc, compiled, probe) > 0.0) {
                return false;
            }
        }
        return true;
    }

    private static boolean feasibleAt(ExactJumpModel model, JumpPhysicsInputs sc,
                                      JumpConstraintCompiler.Compiled compiled, double[] base, int k, double delta) {
        double[] probe = Arrays.copyOf(base, base.length);
        probe[k] += delta;
        return violation(model, sc, compiled, probe) <= 0.0;
    }

    static double violation(ExactJumpModel model, JumpPhysicsInputs sc,
                            JumpConstraintCompiler.Compiled compiled, double[] yaws) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath path = model.forward(sc, gf);
        return compiled.maxViolation(gf, path);
    }

    private static double minMargin(ExactJumpModel model, JumpPhysicsInputs sc,
                                    JumpConstraintCompiler.Compiled compiled, double[] yaws) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(yaws));
        ForwardPath path = model.forward(sc, gf);
        double min = Double.NaN;
        for (JumpConstraint c : compiled.ineq) {
            double e = JumpConstraintCompiler.evaluate(c, gf, path);
            double margin = c.cmp == JumpConstraint.Cmp.GE ? e : -e;
            if (Double.isNaN(min) || margin < min) {
                min = margin;
            }
        }
        return min;
    }

    private static double minWidth(double[] lo, double[] hi, int from, int to) {
        if (from >= to) {
            return Double.NaN;
        }
        double min = Double.MAX_VALUE;
        for (int k = from; k < to; k++) {
            min = Math.min(min, lo[k] + hi[k]);
        }
        return min;
    }

    private static double geoWidth(double[] lo, double[] hi, int from, int to) {
        if (from >= to) {
            return Double.NaN;
        }
        double sum = 0.0;
        for (int k = from; k < to; k++) {
            sum += Math.log(Math.max(lo[k] + hi[k], 1.0e-6));
        }
        return Math.exp(sum / (to - from));
    }

    static double[] recordedYaws(SaveFile file, String name, int n) {
        if (file.angleSolver.result == null || file.angleSolver.result.yaws == null
                || file.angleSolver.result.yaws.isEmpty()) {
            throw new BadSampleException(name + ": capture carries no result.yaws, re-solve and re-save");
        }
        int startTick = file.angleSolver.startTick;
        double[] out = new double[n];
        boolean[] seen = new boolean[n];
        for (SaveFile.Yaw y : file.angleSolver.result.yaws) {
            int k = y.tick - startTick - 1;
            if (k < 0 || k >= n) {
                continue;
            }
            out[k] = y.yaw;
            seen[k] = true;
        }
        for (int k = 0; k < n; k++) {
            if (!seen[k]) {
                throw new IllegalStateException(name + ": result.yaws has no entry for tick " + (startTick + k + 1)
                        + " (" + file.angleSolver.result.yaws.size() + " entries, scenario needs " + n + ")");
            }
        }
        return out;
    }

    static JumpSpec buildSpec(SaveFile file, ExactJumpModel model) {
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> {
        }, model);
        return engine.debugBuildSpec();
    }
}
