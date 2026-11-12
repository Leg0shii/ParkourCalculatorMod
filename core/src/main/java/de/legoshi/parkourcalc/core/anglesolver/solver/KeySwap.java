package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.anglesolver.FlowVector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class KeySwap {

    public static final double SWAP_DEG = 2.0;
    public static final double EDGE_DEG = 20.0;
    public static final double DRIVE_DEG = 90.0 * InputSmooth.DRIVE_CHANGE_COST;
    public static final int LOOK_TICKS = 8;
    public static final double LOOK_MIN_BLOCKS = 0.1;
    public static final double LOOK_DEAD_DEG = 45.0;
    public static final double LOOK_WEIGHT = 0.5;

    private static final ShapeSmooth.Weights REPAIR_METRIC =
            new ShapeSmooth.Weights(ShapeSmooth.Norm.L2, 1.0, 1.0, 0.0, 0.0);

    private static final TickInputs[] COMBOS = {
            new TickInputs(true, false, false, false, true),
            new TickInputs(true, true, false, false, true),
            new TickInputs(true, false, false, true, true),
            new TickInputs(false, true, false, false, false),
            new TickInputs(false, false, false, true, false),
            new TickInputs(false, false, true, false, false),
            new TickInputs(false, true, true, false, false),
            new TickInputs(false, false, true, true, false),
    };

    public static final class Result {
        public final JumpPhysicsInputs scenario;
        public final double[] yaws;
        public final int changed;

        Result(JumpPhysicsInputs scenario, double[] yaws, int changed) {
            this.scenario = scenario;
            this.yaws = yaws;
            this.changed = changed;
        }
    }

    private KeySwap() {
    }

    public static Result plan(ExactJumpModel exact, JumpSpec spec, double[] yawsAbsWrapped, boolean allowA, boolean allowD,
                              boolean keepSprint) {
        JumpPhysicsInputs base = spec.asScenario();
        int n = base.numTicks;
        if (yawsAbsWrapped == null || yawsAbsWrapped.length != n) {
            return null;
        }
        double[] yaws = Angles.wrapAll(yawsAbsWrapped.clone());
        double[] look = look(exact.forward(base, base.toGameFacings(yaws)), n);
        JumpPhysicsInputs editable = TickInputs.editable(base);
        boolean[] held = DeWiggle.heldByFacing(n, spec);
        TickInputs[] current = new TickInputs[n];
        boolean[] swappable = new boolean[n];
        boolean any = false;
        for (int t = 0; t < n; t++) {
            current[t] = TickInputs.of(editable, t);
            swappable[t] = current[t] != null && thrusts(current[t]) && !held[t] && !base.sneakAt(t) && !base.damageAt(t)
                    && base.surfaceAt(t) == SurfaceKind.NORMAL && base.flowAt(t) == FlowVector.NONE
                    && !(base.jumpAt(t) && base.contactAt(t));
            any |= swappable[t];
        }
        if (!any) {
            return null;
        }
        JumpLinearModel baseModel = new JumpLinearModel(editable);
        List<TickInputs> combos = new ArrayList<>();
        List<JumpLinearModel> models = new ArrayList<>();
        for (TickInputs combo : COMBOS) {
            if ((combo.a && !allowA) || (combo.d && !allowD) || (keepSprint && !combo.sprint)) {
                continue;
            }
            JumpPhysicsInputs sc = TickInputs.cloneInputs(editable);
            for (int t = 0; t < n; t++) {
                if (swappable[t]) {
                    combo.applyTo(sc, t);
                }
            }
            combos.add(combo);
            models.add(new JumpLinearModel(sc));
        }

        TickInputs[][] keys = new TickInputs[n][];
        double[][] face = new double[n][];
        double[][] own = new double[n][];
        int[][] drive = new int[n][];
        List<Integer> chain = new ArrayList<>();
        for (int t = 0; t < n; t++) {
            boolean boosted = base.jumpAt(t) && base.contactAt(t) && base.sprintAt(t);
            if (current[t] != null && !thrusts(current[t]) && !boosted && !held[t]) {
                continue;
            }
            chain.add(t);
            if (!swappable[t]) {
                keys[t] = new TickInputs[]{current[t]};
                face[t] = new double[]{yaws[t]};
                own[t] = new double[]{0.0};
                drive[t] = new int[]{InputSmooth.drive(editable, t)};
                continue;
            }
            List<TickInputs> k = new ArrayList<>();
            List<Double> f = new ArrayList<>();
            k.add(current[t]);
            f.add(yaws[t]);
            for (int c = 0; c < combos.size(); c++) {
                TickInputs combo = combos.get(c);
                if (combo.equals(current[t]) || models.get(c).mMag(t) <= 0.0) {
                    continue;
                }
                k.add(combo);
                f.add(Angles.wrap(yaws[t] + Angles.deg(baseModel.baseArg(t) - models.get(c).baseArg(t))));
            }
            keys[t] = k.toArray(new TickInputs[0]);
            face[t] = new double[k.size()];
            own[t] = new double[k.size()];
            drive[t] = new int[k.size()];
            for (int o = 0; o < k.size(); o++) {
                face[t][o] = f.get(o);
                own[t][o] = (o == 0 ? 0.0 : SWAP_DEG) + away(face[t][o], look[t]);
                drive[t][o] = driveOf(keys[t][o]);
            }
        }

        int m = chain.size();
        double[][] best = new double[m][];
        int[][] from = new int[m][];
        for (int i = 0; i < m; i++) {
            int t = chain.get(i);
            int options = keys[t].length;
            best[i] = new double[options];
            from[i] = new int[options];
            for (int o = 0; o < options; o++) {
                if (i == 0) {
                    best[i][o] = own[t][o] + Math.abs(Angles.wrapDelta(face[t][o] - base.startYaw));
                    from[i][o] = -1;
                    continue;
                }
                int p = chain.get(i - 1);
                double low = Double.POSITIVE_INFINITY;
                int arg = 0;
                for (int q = 0; q < keys[p].length; q++) {
                    double step = best[i - 1][q] + Math.abs(Angles.wrapDelta(face[t][o] - face[p][q]));
                    if (p == t - 1) {
                        if (!same(keys[p][q], keys[t][o])) {
                            step += EDGE_DEG;
                        }
                        if (drive[p][q] != drive[t][o]) {
                            step += DRIVE_DEG;
                        }
                    }
                    if (step < low) {
                        low = step;
                        arg = q;
                    }
                }
                best[i][o] = own[t][o] + low;
                from[i][o] = arg;
            }
        }
        int pick = 0;
        for (int o = 1; o < best[m - 1].length; o++) {
            if (best[m - 1][o] < best[m - 1][pick]) {
                pick = o;
            }
        }
        JumpPhysicsInputs out = TickInputs.cloneInputs(editable);
        double[] turned = yaws.clone();
        boolean[] chained = new boolean[n];
        int changed = 0;
        for (int i = m - 1; i >= 0; i--) {
            int t = chain.get(i);
            chained[t] = true;
            turned[t] = face[t][pick];
            if (pick != 0) {
                keys[t][pick].applyTo(out, t);
                changed++;
            }
            pick = from[i][pick];
        }
        if (changed == 0) {
            return null;
        }
        double last = base.startYaw;
        for (int t = 0; t < n; t++) {
            if (chained[t]) {
                last = turned[t];
            } else {
                turned[t] = last;
            }
        }
        int kept = settle(out, editable, current, turned, yaws);
        if (SolverTrace.on()) {
            StringBuilder sb = new StringBuilder();
            for (int t = 0; t < n; t++) {
                TickInputs now = TickInputs.of(out, t);
                sb.append(t).append(':').append(now == null ? "?" : now.keys().replace(" ", "")).append('@')
                        .append(Math.round(turned[t])).append('/').append(Math.round(yaws[t])).append(' ');
            }
            SolverTrace.log("ENGINE", "key swap picked %d ticks, %d after the sprint rules: %s", changed, kept, sb);
        }
        return kept == 0 ? null : new Result(out, Angles.wrapAll(turned), kept);
    }

    private static int settle(JumpPhysicsInputs out, JumpPhysicsInputs editable, TickInputs[] current, double[] turned,
                              double[] yaws) {
        int n = out.numTicks;
        for (int round = 0; round <= n; round++) {
            boolean reverted = false;
            for (int t = 0; t < n; t++) {
                TickInputs now = TickInputs.of(out, t);
                if (now == null || realizable(out, editable, t, now)) {
                    continue;
                }
                int back = differs(out, current, t) ? t : t > 0 && differs(out, current, t - 1) ? t - 1
                        : t + 1 < n && differs(out, current, t + 1) ? t + 1 : -1;
                if (back < 0) {
                    continue;
                }
                current[back].applyTo(out, back);
                turned[back] = yaws[back];
                reverted = true;
            }
            if (!reverted) {
                break;
            }
        }
        int kept = 0;
        for (int t = 0; t < n; t++) {
            if (differs(out, current, t)) {
                kept++;
            }
        }
        return kept;
    }

    private static boolean differs(JumpPhysicsInputs out, TickInputs[] current, int t) {
        TickInputs now = TickInputs.of(out, t);
        return now != null && current[t] != null && !now.equals(current[t]);
    }

    private static boolean realizable(JumpPhysicsInputs out, JumpPhysicsInputs editable, int t, TickInputs now) {
        if (InputSmooth.realizable(out, t, now)) {
            return true;
        }
        TickInputs was = TickInputs.of(editable, t);
        return was != null && !InputSmooth.realizable(editable, t, was) && same(now, was)
                && sameAround(out, editable, t);
    }

    private static boolean sameAround(JumpPhysicsInputs out, JumpPhysicsInputs editable, int t) {
        for (int s = Math.max(0, t - 1); s <= Math.min(out.numTicks - 1, t + 1); s++) {
            if (!same(TickInputs.of(out, s), TickInputs.of(editable, s))) {
                return false;
            }
        }
        return true;
    }

    public static Result realise(ExactJumpModel exact, JumpSpec source, double[] sourceYaws, Result swap,
                                 List<JumpConstraint> guarded, long deadlineNanos, AtomicBoolean cancel) {
        JumpPhysicsInputs from = TickInputs.editable(source.asScenario());
        int n = from.numTicks;
        double[] start = Angles.wrapAll(sourceYaws.clone());
        JumpSpec all = new JumpSpec(swap.scenario, new ArrayList<>(guarded), source.objective);
        double[] whole = restored(exact, all, swap.yaws);
        if (whole != null) {
            return new Result(swap.scenario, whole, swap.changed);
        }
        JumpPhysicsInputs scenario = TickInputs.cloneInputs(from);
        double[] yaws = start.clone();
        int kept = 0;
        for (int t = 0; t < n; t++) {
            TickInputs was = TickInputs.of(from, t);
            TickInputs want = TickInputs.of(swap.scenario, t);
            if (was == null || want == null || was.equals(want)) {
                continue;
            }
            if ((cancel != null && cancel.get()) || (deadlineNanos != 0L && System.nanoTime() >= deadlineNanos)) {
                break;
            }
            JumpPhysicsInputs next = TickInputs.cloneInputs(scenario);
            want.applyTo(next, t);
            double[] moved = yaws.clone();
            moved[t] = Angles.wrap(yaws[t] + Angles.wrapDelta(swap.yaws[t] - start[t]));
            double[] landed = restored(exact, new JumpSpec(next, new ArrayList<>(guarded), source.objective), moved);
            if (landed == null) {
                continue;
            }
            scenario = next;
            yaws = landed;
            kept++;
        }
        return kept == 0 ? null : new Result(scenario, yaws, kept);
    }

    private static double[] restored(ExactJumpModel exact, JumpSpec spec, double[] start) {
        JumpPhysicsInputs sc = spec.asScenario();
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(spec);
        if (lands(exact, sc, compiled, start)) {
            return start;
        }
        double[] out = new ShapeSmooth(exact, spec).restore(start, REPAIR_METRIC);
        return out != null && lands(exact, sc, compiled, out) ? out : null;
    }

    private static boolean lands(ExactJumpModel exact, JumpPhysicsInputs sc, JumpConstraintCompiler.Compiled compiled,
                                 double[] yaws) {
        double[] gf = sc.toGameFacings(yaws);
        return compiled.maxViolation(gf, exact.forward(sc, gf)) <= 0.0;
    }

    private static double[] look(ForwardPath path, int n) {
        double[] look = new double[n];
        double last = Double.NaN;
        for (int t = n - 1; t >= 0; t--) {
            for (int to = Math.min(n, t + LOOK_TICKS); to <= n; to++) {
                double dx = path.posX[to] - path.posX[t];
                double dz = path.posZ[to] - path.posZ[t];
                if (StrictMath.hypot(dx, dz) >= LOOK_MIN_BLOCKS) {
                    last = Angles.deg(StrictMath.atan2(-dx, dz));
                    break;
                }
            }
            look[t] = last;
        }
        return look;
    }

    private static double away(double facing, double look) {
        if (Double.isNaN(look)) {
            return 0.0;
        }
        return LOOK_WEIGHT * Math.max(0.0, Math.abs(Angles.wrapDelta(facing - look)) - LOOK_DEAD_DEG);
    }

    private static boolean thrusts(TickInputs keys) {
        return keys.w || keys.a || keys.s || keys.d;
    }

    private static boolean same(TickInputs a, TickInputs b) {
        return a == null ? b == null : a.equals(b);
    }

    private static int driveOf(TickInputs keys) {
        if (keys.forward()) {
            return keys.sprint ? 2 : 1;
        }
        return keys.s && !keys.w ? -1 : 0;
    }
}
