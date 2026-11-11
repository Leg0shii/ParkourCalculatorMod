package de.legoshi.parkourcalc.core.anglesolver.solver;

import de.legoshi.parkourcalc.core.anglesolver.FlowVector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ReleasePlan {

    public static final double WALL_MARGIN = 0.01;
    public static final double SNAP_DEG = 15.0;
    private static final double SAME_FACING_DEG = 1.0e-6;
    private static final int GROUP_LEVELS = 3;

    private static final double[] SOFTNESS = {0.5, 0.25, 0.125, 0.0625, 0.03125, 0.02};
    private static final double FINAL_SOFTNESS = 0.02;
    private static final int DIVE_ITERATIONS = 400;
    private static final double EMPTY_SHARE = 1.0e-7;
    private static final double EASE_RATIO = 0.5;
    private static final double PLAN_TOLERANCE = 1.0e-6;
    private static final double LOOSE_TOLERANCE = 0.01;
    private static final double MULTIPLIER_CAP = 1000.0;
    private static final ShapeSmooth.Weights REPAIR_METRIC =
            new ShapeSmooth.Weights(ShapeSmooth.Norm.L2, 1.0, 1.0, 0.0, 0.0);

    private static final ShapeSmooth.Weights[] SPARSE_TURNS = {
            new ShapeSmooth.Weights(ShapeSmooth.Norm.L1, 1.0, 0.0, 0.0, 0.0),
            new ShapeSmooth.Weights(ShapeSmooth.Norm.LOG, 1.0, 0.0, 0.0, 0.0),
    };
    private static final int MAX_EASE_TICKS = 4;
    private static final double PLACED_EASE_RATIO = 0.25;
    private static final double PLACE_TIE = 1.0e-9;

    private static final TickInputs SPRINT = new TickInputs(true, false, false, false, true);
    private static final TickInputs WALK = new TickInputs(true, false, false, false, false);
    private static final TickInputs[] COMBOS = {
            SPRINT,
            WALK,
            new TickInputs(true, true, false, false, true),
            new TickInputs(true, false, false, true, true),
            new TickInputs(true, true, false, false, false),
            new TickInputs(true, false, false, true, false),
            new TickInputs(false, true, false, false, false),
            new TickInputs(false, false, false, true, false),
            new TickInputs(false, false, true, false, false),
            new TickInputs(false, true, true, false, false),
            new TickInputs(false, false, true, true, false),
    };
    private static final double STRONG_SHARE = 0.5;
    private static final double TURN_BASE = 0.25;
    private static final double TURN_SLOPE = 0.35;
    private static final int MAX_CHOICES = 8;

    public static final class Result {
        public final JumpPhysicsInputs scenario;
        public final double[] yaws;
        public final TickInputs[] inputs;
        public final int changed;
        public final boolean resolved;

        Result(JumpPhysicsInputs scenario, double[] yaws, TickInputs[] inputs, int changed, boolean resolved) {
            this.scenario = scenario;
            this.yaws = yaws;
            this.inputs = inputs;
            this.changed = changed;
            this.resolved = resolved;
        }
    }

    private static final class Snapshot {
        final boolean[] decided;
        final boolean[] walking;
        final boolean[] faced;
        final TickInputs[] keys;
        final double[] fixX;
        final double[] fixZ;
        final double[] face;

        Snapshot(ReleasePlan p) {
            this.decided = p.decided.clone();
            this.walking = p.walking.clone();
            this.faced = p.faced.clone();
            this.keys = p.keys.clone();
            this.fixX = p.fixX.clone();
            this.fixZ = p.fixZ.clone();
            this.face = p.face.clone();
        }

        void restore(ReleasePlan p) {
            int n = decided.length;
            System.arraycopy(decided, 0, p.decided, 0, n);
            System.arraycopy(walking, 0, p.walking, 0, n);
            System.arraycopy(faced, 0, p.faced, 0, n);
            System.arraycopy(keys, 0, p.keys, 0, n);
            System.arraycopy(fixX, 0, p.fixX, 0, n);
            System.arraycopy(fixZ, 0, p.fixZ, 0, n);
            System.arraycopy(face, 0, p.face, 0, n);
        }
    }

    private final ExactJumpModel exact;
    private final JumpSpec spec;
    private final JumpPhysicsInputs base;
    private final JumpPhysicsInputs editable;
    private final int n;
    private final boolean[] eligible;
    private final boolean[] held;
    private final boolean[] contact;
    private final TickInputs[] original;
    private final List<JumpConstraint> guarded;
    private final List<JumpLinearModel.Wall> walls = new ArrayList<>();
    private final List<Double> bounds = new ArrayList<>();
    private final double[] sprintRadius;
    private final double[] walkRadius;
    private final double[][] comboRadius;
    private final double[] seedX;
    private final double[] seedZ;
    private final double[] planArg;
    private final double[][] comboArg;
    private final boolean[] allowed;
    private final boolean airTap;
    private final boolean[] sprintJump;
    private int blockedPress = -1;

    private final boolean[] decided;
    private final boolean[] walking;
    private final boolean[] faced;
    private final TickInputs[] keys;
    private final double[] fixX;
    private final double[] fixZ;
    private final double[] face;
    private double[] debt;
    private ThrustPlan.Result current;
    private int loose;
    private int solves;

    private ReleasePlan(ExactJumpModel exact, JumpSpec spec, double[] seed, ForwardPath seedPath, double giveBack,
                        boolean allowA, boolean allowD) {
        this.exact = exact;
        this.spec = spec;
        this.base = spec.asScenario();
        this.editable = TickInputs.editable(base);
        this.n = base.numTicks;
        this.airTap = exact.airTapSprint();
        this.sprintJump = new boolean[n];
        this.guarded = SmoothPortfolio.guard(base, spec, spec.objective.evaluate(seedPath), giveBack).constraints;
        this.held = DeWiggle.heldByFacing(n, spec);
        this.eligible = new boolean[n];
        this.contact = new boolean[n];
        this.original = new TickInputs[n];
        for (int t = 0; t < n; t++) {
            original[t] = TickInputs.of(editable, t);
            contact[t] = base.contactAt(t);
            eligible[t] = original[t] != null && !held[t] && !base.sneakAt(t) && !base.damageAt(t)
                    && base.surfaceAt(t) == SurfaceKind.NORMAL && base.flowAt(t) == FlowVector.NONE;
        }
        JumpLinearModel sprintModel = model(SPRINT);
        JumpLinearModel walkModel = model(WALK);
        this.comboRadius = new double[COMBOS.length][n];
        this.comboArg = new double[COMBOS.length][n];
        this.allowed = new boolean[COMBOS.length];
        for (int combo = 0; combo < COMBOS.length; combo++) {
            allowed[combo] = (allowA || !COMBOS[combo].a) && (allowD || !COMBOS[combo].d);
            JumpLinearModel m = combo == 0 ? sprintModel : combo == 1 ? walkModel : model(COMBOS[combo]);
            for (int t = 0; t < n; t++) {
                comboRadius[combo][t] = eligible[t] ? m.mMag(t) : 0.0;
                comboArg[combo][t] = m.baseArg(t);
            }
        }
        JumpLinearModel baseModel = new JumpLinearModel(base);
        this.sprintRadius = new double[n];
        this.walkRadius = new double[n];
        this.seedX = new double[n];
        this.seedZ = new double[n];
        this.planArg = new double[n];
        this.decided = new boolean[n];
        this.walking = new boolean[n];
        this.faced = new boolean[n];
        this.keys = new TickInputs[n];
        this.fixX = new double[n];
        this.fixZ = new double[n];
        this.face = new double[n];
        double[] gf = base.toGameFacings(seed);
        for (int t = 0; t < n; t++) {
            sprintRadius[t] = sprintModel.mMag(t);
            walkRadius[t] = walkModel.mMag(t);
            planArg[t] = sprintModel.baseArg(t);
            double phi = baseModel.baseArg(t) + Angles.rad(gf[t]);
            seedX[t] = baseModel.mMag(t) * StrictMath.cos(phi);
            seedZ[t] = baseModel.mMag(t) * StrictMath.sin(phi);
            if (held[t]) {
                face[t] = seed[t];
                faced[t] = true;
            }
        }
        for (JumpConstraint c : guarded) {
            JumpLinearModel.Wall wall = sprintModel.compileWall(c, 0.0, null);
            if (wall == null || wall.axis > 1) {
                continue;
            }
            double margin = wall.eq ? 0.0 : Math.max(0.0, Math.min(WALL_MARGIN, 0.5 * slack(c, seedPath)));
            double bound = wall.bPrime - margin;
            for (int t = 0; t < n; t++) {
                if (held[t]) {
                    bound -= wall.coef[t] * (wall.axis == 0 ? seedX[t] : seedZ[t]);
                }
            }
            walls.add(wall);
            bounds.add(bound);
        }
    }

    public static Result plan(ExactJumpModel exact, JumpSpec spec, double[] seedAbsWrapped, double giveBack,
                              boolean allowA, boolean allowD, InputSmooth.Resolver resolver, long deadlineNanos,
                              AtomicBoolean cancel) {
        JumpPhysicsInputs sc = spec.asScenario();
        if (seedAbsWrapped == null || seedAbsWrapped.length != sc.numTicks) {
            return null;
        }
        double[] seed = Angles.wrapAll(seedAbsWrapped.clone());
        double[] gf = sc.toGameFacings(seed);
        ForwardPath seedPath = exact.forward(sc, gf);
        if (JumpConstraintCompiler.compile(spec).maxViolation(gf, seedPath) > 0.0) {
            return null;
        }
        ReleasePlan plan = new ReleasePlan(exact, spec, seed, seedPath, giveBack, allowA, allowD);
        return plan.run(resolver, deadlineNanos, cancel);
    }

    private JumpLinearModel model(TickInputs everywhere) {
        JumpPhysicsInputs sc = TickInputs.cloneInputs(editable);
        for (int t = 0; t < n; t++) {
            if (eligible[t]) {
                everywhere.applyTo(sc, t);
            }
        }
        return new JumpLinearModel(sc);
    }

    private static double slack(JumpConstraint c, ForwardPath path) {
        JumpPhysicsInputs.Axis axis = c.mode == JumpConstraint.Mode.X ? JumpPhysicsInputs.Axis.X : JumpPhysicsInputs.Axis.Z;
        double got = path.getPos(c.t1, axis);
        if (c.t2 != null) {
            double other = path.getPos(c.t2, axis);
            got += c.op == JumpConstraint.Op.PLUS ? other : -other;
        }
        if (c.cmp == JumpConstraint.Cmp.LE) {
            return c.rhs - got;
        }
        return c.cmp == JumpConstraint.Cmp.GE ? got - c.rhs : 0.0;
    }

    private static boolean stopped(long deadlineNanos, AtomicBoolean cancel) {
        if (cancel != null && cancel.get()) {
            return true;
        }
        return deadlineNanos != 0L && System.nanoTime() >= deadlineNanos;
    }

    private Result run(InputSmooth.Resolver resolver, long deadlineNanos, AtomicBoolean cancel) {
        boolean any = false;
        for (boolean e : eligible) {
            any |= e;
        }
        if (!any || walls.isEmpty()) {
            return null;
        }
        debt = new double[walls.size()];
        double[] warm = null;
        for (double softness : SOFTNESS) {
            current = relax(softness, warm, ThrustPlan.MAX_ITERATIONS, deadlineNanos);
            warm = current.multipliers;
        }
        if (SolverTrace.on()) {
            SolverTrace.log("ENGINE", "release plan relax rows=%d converged=%s violation=%.3e", walls.size(),
                    current.converged, current.violation);
        }
        if (current.violation > PLAN_TOLERANCE) {
            return null;
        }
        Snapshot fresh = new Snapshot(this);
        ThrustPlan.Result relaxed = current;
        for (int round = 0; round <= n; round++) {
            int stuck = dive(deadlineNanos, cancel);
            if (stuck < 0) {
                return realise(resolver, deadlineNanos, cancel);
            }
            int jump = stuck == n ? -1 : walkJumpBehind(blockedPress);
            if (SolverTrace.on()) {
                SolverTrace.log("ENGINE", "release plan dive stuck at tick %d, walk jump in the way %d", stuck, jump);
            }
            if (jump < 0) {
                return null;
            }
            sprintJump[jump] = true;
            fresh.restore(this);
            current = relaxed;
            debt = new double[walls.size()];
            loose = 0;
            blockedPress = -1;
        }
        return null;
    }

    private int dive(long deadlineNanos, AtomicBoolean cancel) {
        for (int pass = 0; pass < 2; pass++) {
            for (int t = 0; t < n; t++) {
                if (decided[t] || held[t] || contact[t] != (pass == 0)) {
                    continue;
                }
                if (stopped(deadlineNanos, cancel)) {
                    return n;
                }
                if (!decide(t, deadlineNanos)) {
                    return t;
                }
            }
        }
        return -1;
    }

    private int walkJumpBehind(int press) {
        if (press < 0) {
            return -1;
        }
        int jump = press + 1 < n && contact[press + 1] ? press + 1 : press;
        while (jump >= 0 && !contact[jump]) {
            jump--;
        }
        boolean walks = jump >= 0 && keys[jump] != null && keys[jump].forward() && !keys[jump].sprint;
        return walks && !sprintJump[jump] ? jump : -1;
    }

    private ThrustPlan.Result relax(double softness, double[] warm, int maxIterations, long deadlineNanos) {
        double[] cost = new double[n];
        double[] radius = new double[n];
        for (int t = 0; t < n; t++) {
            if (decided[t] || held[t]) {
                continue;
            }
            radius[t] = walking[t] ? walkRadius[t] : sprintRadius[t];
            cost[t] = eligible[t] ? 1.0 : 0.0;
        }
        List<ThrustPlan.Row> rows = new ArrayList<>();
        for (int i = 0; i < walls.size(); i++) {
            JumpLinearModel.Wall wall = walls.get(i);
            double[] ax = new double[n];
            double[] az = new double[n];
            double rhs = bounds.get(i) + debt[i];
            for (int t = 0; t < n; t++) {
                if (decided[t]) {
                    rhs -= wall.coef[t] * (wall.axis == 0 ? fixX[t] : fixZ[t]);
                } else if (wall.axis == 0) {
                    ax[t] = wall.coef[t] * radius[t];
                } else {
                    az[t] = wall.coef[t] * radius[t];
                }
            }
            rows.add(new ThrustPlan.Row(ax, az, rhs, wall.eq));
        }
        solves++;
        return ThrustPlan.solve(n, cost, rows, softness, warm, maxIterations, MULTIPLIER_CAP, deadlineNanos);
    }

    private double[] direction(int t) {
        double share = current.share(t);
        if (share > 1.0e-9) {
            return new double[]{current.vx[t] / share, current.vz[t] / share};
        }
        double q = StrictMath.hypot(current.qx[t], current.qz[t]);
        if (q > 1.0e-12) {
            return new double[]{current.qx[t] / q, current.qz[t] / q};
        }
        double m = StrictMath.hypot(seedX[t], seedZ[t]);
        if (m > 0.0) {
            return new double[]{seedX[t] / m, seedZ[t] / m};
        }
        int anchor = anchor(t);
        double phi = planArg[t] + Angles.rad(anchor < 0 ? base.startYaw : face[anchor]);
        return new double[]{StrictMath.cos(phi), StrictMath.sin(phi)};
    }

    private int anchor(int t) {
        for (int s = t - 1; s >= 0; s--) {
            if (faced[s]) {
                return s;
            }
        }
        return -1;
    }

    private boolean canWalk(int t) {
        return t >= 2 && !sprintJump[t] && eligible[t - 1] && eligible[t - 2] && !contact[t - 1] && !contact[t - 2]
                && !decided[t - 1] && !decided[t - 2];
    }

    private static final class Choice {
        final TickInputs keys;
        final double heading;
        final double radius;
        final double facing;
        final double rank;

        Choice(TickInputs keys, double heading, double radius, double facing, double rank) {
            this.keys = keys;
            this.heading = heading;
            this.radius = radius;
            this.facing = facing;
            this.rank = rank;
        }

        boolean release() {
            return radius <= 0.0;
        }
    }

    private void still(List<Choice> out, int t, int combo, double facing, double wantX, double wantZ, double unit) {
        double radius = comboRadius[combo][t];
        if (radius <= 0.0) {
            return;
        }
        double heading = comboArg[combo][t] + Angles.rad(facing);
        double dx = radius * StrictMath.cos(heading) - wantX;
        double dz = radius * StrictMath.sin(heading) - wantZ;
        out.add(new Choice(COMBOS[combo], heading, radius, facing, StrictMath.hypot(dx, dz) / unit));
    }

    private void turned(List<Choice> out, int t, int combo, double facing, double free, double want, double unit) {
        double radius = comboRadius[combo][t];
        if (radius <= 0.0) {
            return;
        }
        double to = Angles.wrap(Angles.deg(free - comboArg[combo][t]));
        double turn = Math.abs(Angles.wrapDelta(to - facing));
        double rank = Math.abs(radius - want) / unit + TURN_BASE + TURN_SLOPE * turn / 180.0;
        out.add(new Choice(COMBOS[combo], free, radius, to, rank));
    }

    private List<Choice> choices(int t, double share, double free) {
        List<Choice> out = new ArrayList<>();
        int anchor = anchor(t);
        double facing = anchor < 0 ? base.startYaw : face[anchor];
        if (!eligible[t]) {
            double to = Angles.wrap(Angles.deg(free - planArg[t]));
            if (Math.abs(Angles.wrapDelta(to - facing)) <= SNAP_DEG) {
                out.add(new Choice(null, planArg[t] + Angles.rad(facing), sprintRadius[t], facing, 0.0));
            }
            out.add(new Choice(null, free, sprintRadius[t], to, 1.0));
            return out;
        }
        double unit = walking[t] ? walkRadius[t] : sprintRadius[t];
        double want = share * unit;
        double wantX = want * StrictMath.cos(free);
        double wantZ = want * StrictMath.sin(free);
        out.add(new Choice(TickInputs.NONE, free, 0.0, facing, share));
        if (unit <= 0.0) {
            return out;
        }
        boolean walk = contact[t] ? canWalk(t) : walking[t];
        boolean sprint = contact[t] || !walking[t];
        boolean taps = !contact[t] || share < STRONG_SHARE;
        for (int combo = 0; combo < COMBOS.length; combo++) {
            TickInputs keys = COMBOS[combo];
            if (!allowed[combo]) {
                continue;
            }
            if (keys.forward() ? (keys.sprint ? !sprint : !walk) : !taps) {
                continue;
            }
            if (contact[t] && keys.forward() && !keys.sprint && keys.strafes()) {
                continue;
            }
            still(out, t, combo, facing, wantX, wantZ, unit);
            if (keys.forward() && !keys.strafes()) {
                turned(out, t, combo, facing, free, want, unit);
            }
        }
        out.sort((a, b) -> Double.compare(a.rank, b.rank));
        return out.size() > MAX_CHOICES ? new ArrayList<>(out.subList(0, MAX_CHOICES)) : out;
    }

    private boolean decide(int t, long deadlineNanos) {
        double share = current.share(t);
        double[] dir = direction(t);
        List<Choice> choices = choices(t, share, StrictMath.atan2(dir[1], dir[0]));
        if (choices.get(0).release() && share <= EMPTY_SHARE) {
            fix(t, choices.get(0));
            return true;
        }
        Snapshot before = new Snapshot(this);
        choices = untapped(t, choices, before);
        ThrustPlan.Result from = current;
        ThrustPlan.Result best = null;
        Snapshot bestState = null;
        boolean bestOver = false;
        double want = share * (walking[t] ? walkRadius[t] : sprintRadius[t]);
        for (Choice choice : choices) {
            fix(t, choice);
            ThrustPlan.Result r = relax(FINAL_SOFTNESS, from.multipliers, DIVE_ITERATIONS, deadlineNanos);
            if (SolverTrace.on()) {
                SolverTrace.log("ENGINE", "release plan tick %d%s share=%.3f keys=%s facing=%.1f violation=%.3e iterations=%d",
                        t, contact[t] ? " contact" : "", share, keys[t] == null ? "kept" : keys[t].keys(), face[t],
                        r.violation, r.iterations);
            }
            if (r.violation <= PLAN_TOLERANCE) {
                current = r;
                return true;
            }
            boolean over = choice.radius * (StrictMath.cos(choice.heading) * dir[0]
                    + StrictMath.sin(choice.heading) * dir[1]) >= want;
            boolean loose = r.violation <= LOOSE_TOLERANCE;
            if (best == null || (loose && over && !bestOver) || (over == bestOver && r.violation < best.violation)) {
                best = r;
                bestState = new Snapshot(this);
                bestOver = loose && over;
            }
            before.restore(this);
        }
        if (best == null || best.violation > LOOSE_TOLERANCE) {
            return false;
        }
        bestState.restore(this);
        current = best;
        loose++;
        for (int i = 0; i < debt.length; i++) {
            if (!walls.get(i).eq && best.residual[i] > 0.0) {
                debt[i] += best.residual[i] + PLAN_TOLERANCE;
            }
        }
        return true;
    }

    private List<Choice> untapped(int t, List<Choice> choices, Snapshot before) {
        if (!airTap) {
            return choices;
        }
        boolean[] was = SprintTap.conflicts(scenario(false));
        List<Choice> out = new ArrayList<>();
        for (Choice choice : choices) {
            fix(t, choice);
            int press = SprintTap.added(was, SprintTap.conflicts(scenario(false)));
            before.restore(this);
            if (press < 0) {
                out.add(choice);
            } else {
                blockedPress = press;
            }
        }
        return out.isEmpty() ? choices : out;
    }

    private JumpPhysicsInputs scenario(boolean settled) {
        JumpPhysicsInputs sc = TickInputs.cloneInputs(editable);
        for (int t = 0; t < n; t++) {
            if (keys[t] != null) {
                keys[t].applyTo(sc, t);
            } else if (!settled && eligible[t] && !decided[t]) {
                TickInputs.NONE.applyTo(sc, t);
            }
        }
        return sc;
    }

    private void fix(int t, Choice choice) {
        decided[t] = true;
        keys[t] = choice.keys;
        fixX[t] = 0.0;
        fixZ[t] = 0.0;
        faced[t] = false;
        if (choice.release()) {
            return;
        }
        double dx = StrictMath.cos(choice.heading);
        double dz = StrictMath.sin(choice.heading);
        face[t] = choice.facing;
        faced[t] = true;
        fixX[t] = choice.radius * dx;
        fixZ[t] = choice.radius * dz;
        boolean walks = choice.keys != null && choice.keys.forward() && !choice.keys.sprint;
        if (walks && contact[t]) {
            decided[t - 1] = true;
            keys[t - 1] = choice.keys;
            face[t - 1] = choice.facing;
            faced[t - 1] = true;
            fixX[t - 1] = walkRadius[t - 1] * dx;
            fixZ[t - 1] = walkRadius[t - 1] * dz;
            decided[t - 2] = true;
            keys[t - 2] = TickInputs.NONE;
            fixX[t - 2] = 0.0;
            fixZ[t - 2] = 0.0;
            faced[t - 2] = false;
            for (int k = t + 1; k < n && !contact[k]; k++) {
                if (eligible[k] && !decided[k]) {
                    walking[k] = true;
                }
            }
        }
    }

    private Result realise(InputSmooth.Resolver resolver, long deadlineNanos, AtomicBoolean cancel) {
        JumpPhysicsInputs scenario = settle();
        if (scenario == null) {
            if (SolverTrace.on()) {
                SolverTrace.log("ENGINE", "release plan keys do not settle");
            }
            return null;
        }
        TickInputs[] inputs = new TickInputs[n];
        int changed = 0;
        for (int t = 0; t < n; t++) {
            if (keys[t] != null && !keys[t].equals(original[t])) {
                inputs[t] = keys[t];
                changed++;
            }
        }
        if (changed == 0) {
            return null;
        }
        JumpSpec planned = new JumpSpec(scenario, new ArrayList<>(guarded), spec.objective);
        double[] start = facings(scenario);
        ShapeSmooth shape = new ShapeSmooth(exact, planned);
        double[] yaws = null;
        for (int level = 0; level < GROUP_LEVELS && yaws == null; level++) {
            yaws = rigid(shape, scenario, planned, start, level);
        }
        boolean rigid = yaws != null;
        if (yaws == null) {
            yaws = shape.restore(start, REPAIR_METRIC);
        }
        boolean resolved = false;
        if (yaws == null && resolver != null && !stopped(deadlineNanos, cancel)) {
            double[] solved = resolver.resolve(planned, start, deadlineNanos, cancel);
            if (solved != null) {
                solved = Angles.wrapAll(solved.clone());
                double[] gf = scenario.toGameFacings(solved);
                if (JumpConstraintCompiler.compile(planned).maxViolation(gf, exact.forward(scenario, gf)) <= 0.0) {
                    yaws = solved;
                    resolved = true;
                }
            }
        }
        if (yaws != null) {
            yaws = sharpen(shape, scenario, yaws, deadlineNanos, cancel);
        }
        if (SolverTrace.on()) {
            double[] gf = scenario.toGameFacings(start);
            SolverTrace.log("ENGINE", "release plan changed=%d solves=%d loose=%d start violation=%.4f %s", changed,
                    solves, loose, JumpConstraintCompiler.compile(planned).maxViolation(gf, exact.forward(scenario, gf)),
                    yaws == null ? "not repaired" : resolved ? "re-solved" : rigid ? "turned as groups" : "repaired");
        }
        return yaws == null ? null : new Result(scenario, yaws, inputs, changed, resolved);
    }

    private double[] rigid(ShapeSmooth shape, JumpPhysicsInputs scenario, JumpSpec planned, double[] start, int level) {
        int[] group = new int[n];
        int groups = 0;
        int last = -1;
        for (int t = 0; t < n; t++) {
            group[t] = -1;
            if (!faced[t] || held[t]) {
                continue;
            }
            boolean split = level >= 2 || (level == 1 && contact[t]);
            if (last < 0 || split || Math.abs(Angles.wrapDelta(face[t] - face[last])) > SAME_FACING_DEG) {
                groups++;
            }
            group[t] = groups - 1;
            last = t;
        }
        double[] turned = shape.restoreRigid(start, group, groups);
        if (turned == null) {
            return null;
        }
        for (int t = 0; t < n; t++) {
            if (group[t] >= 0) {
                face[t] = turned[t];
            }
        }
        double[] yaws = facings(scenario);
        double[] gf = scenario.toGameFacings(yaws);
        boolean lands = JumpConstraintCompiler.compile(planned).maxViolation(gf, exact.forward(scenario, gf)) <= 0.0;
        return lands ? yaws : turned;
    }

    private double[] sharpen(ShapeSmooth shape, JumpPhysicsInputs scenario, double[] yaws, long deadlineNanos,
                             AtomicBoolean cancel) {
        double[] best = tidy(shape, scenario, place(shape, scenario, yaws));
        WatchScore bestScore = score(scenario, best);
        for (ShapeSmooth.Weights w : SPARSE_TURNS) {
            if (stopped(deadlineNanos, cancel)) {
                break;
            }
            double[] out = shape.run(yaws, w, deadlineNanos, cancel);
            if (out == null || !shape.feasible(out)) {
                continue;
            }
            double[] placed = tidy(shape, scenario, place(shape, scenario, out));
            WatchScore score = score(scenario, placed);
            if (score.betterThan(bestScore)) {
                best = placed;
                bestScore = score;
            }
        }
        return best;
    }

    private WatchScore score(JumpPhysicsInputs scenario, double[] yaws) {
        return WatchScore.of(scenario.startYaw, yaws, exact.forward(scenario, scenario.toGameFacings(yaws)));
    }

    private double[] tidy(ShapeSmooth shape, JumpPhysicsInputs scenario, double[] yaws) {
        double[] u = shape.unwrap(yaws);
        double cost = score(scenario, yaws).cost;
        boolean improved = true;
        for (int round = 0; round < n && improved; round++) {
            improved = false;
            int t = 0;
            while (t < n && !improved) {
                double step = u[t] - (t == 0 ? scenario.startYaw : u[t - 1]);
                if (Math.abs(step) <= WatchScore.VISIBLE_DEG) {
                    t++;
                    continue;
                }
                int from = t;
                double mass = 0.0;
                while (t < n) {
                    double d = u[t] - (t == 0 ? scenario.startYaw : u[t - 1]);
                    if (Math.abs(d) <= WatchScore.VISIBLE_DEG || d > 0.0 != step > 0.0) {
                        break;
                    }
                    mass += d;
                    t++;
                }
                if (Math.abs(mass) >= WatchScore.MICRO_DEG) {
                    continue;
                }
                for (double[] cand : merges(scenario, u, from, t, mass)) {
                    double[] wrapped = Angles.wrapAll(cand.clone());
                    if (!shape.feasible(wrapped)) {
                        continue;
                    }
                    double next = score(scenario, wrapped).cost;
                    if (next < cost - PLACE_TIE) {
                        u = cand;
                        cost = next;
                        improved = true;
                        break;
                    }
                }
            }
        }
        return Angles.wrapAll(u);
    }

    private List<double[]> merges(JumpPhysicsInputs scenario, double[] u, int from, int to, double mass) {
        List<double[]> out = new ArrayList<>();
        double before = from == 0 ? scenario.startYaw : u[from - 1];
        double[] dropped = u.clone();
        for (int k = from; k < n; k++) {
            dropped[k] = k < to ? before : u[k] - mass;
        }
        out.add(dropped);
        int next = to;
        while (next < n && Math.abs(u[next] - u[next - 1]) <= WatchScore.VISIBLE_DEG) {
            next++;
        }
        if (next < n) {
            double[] later = u.clone();
            for (int k = from; k < next; k++) {
                later[k] = before;
            }
            out.add(later);
        }
        int prev = from - 1;
        while (prev >= 0 && Math.abs(u[prev] - (prev == 0 ? scenario.startYaw : u[prev - 1])) <= WatchScore.VISIBLE_DEG) {
            prev--;
        }
        if (prev >= 0) {
            double[] earlier = u.clone();
            for (int k = prev; k < to; k++) {
                earlier[k] = u[to - 1];
            }
            out.add(earlier);
        }
        return out;
    }

    private double[] place(ShapeSmooth shape, JumpPhysicsInputs scenario, double[] yaws) {
        ForwardPath path = exact.forward(scenario, scenario.toGameFacings(yaws));
        double[] u = shape.unwrap(yaws);
        boolean[] free = new boolean[n];
        for (int t = 0; t < n; t++) {
            free[t] = !held[t] && !scenario.strafeAt(t) && scenario.forwardAt(t) == 0.0F
                    && scenario.strafeInputAt(t) == 0.0F;
        }
        int t = 0;
        while (t < n) {
            if (!free[t]) {
                t++;
                continue;
            }
            int a = t;
            while (t < n && free[t]) {
                t++;
            }
            int b = t;
            double from = a == 0 ? scenario.startYaw : u[a - 1];
            double to = b < n ? u[b] : from;
            for (int k = a; k < b; k++) {
                u[k] = from;
            }
            if (Math.abs(to - from) <= WatchScore.VISIBLE_DEG) {
                continue;
            }
            double[] best = null;
            double bestCost = Double.POSITIVE_INFINITY;
            int last = b < n ? b : b - 1;
            for (int e = a; e <= last; e++) {
                for (int ease = 1; ease <= MAX_EASE_TICKS && e - ease + 1 >= a; ease++) {
                    double[] cand = u.clone();
                    double floor = StrictMath.pow(PLACED_EASE_RATIO, ease);
                    for (int k = 1; k <= ease; k++) {
                        double share = (StrictMath.pow(PLACED_EASE_RATIO, ease - k) - floor) / (1.0 - floor);
                        cand[e - ease + k] = from + (to - from) * share;
                    }
                    for (int k = e + 1; k < b; k++) {
                        cand[k] = to;
                    }
                    double cost = WatchScore.of(scenario.startYaw, Angles.wrapAll(cand.clone()), path).cost;
                    if (cost <= bestCost + PLACE_TIE) {
                        best = cand;
                        bestCost = Math.min(bestCost, cost);
                    }
                }
            }
            if (best != null) {
                u = best;
            }
        }
        double[] placed = Angles.wrapAll(u);
        return shape.feasible(placed) ? placed : yaws;
    }

    private JumpPhysicsInputs settle() {
        for (int round = 0; round <= n; round++) {
            JumpPhysicsInputs sc = scenario(true);
            int bad = -1;
            for (int t = 0; t < n && bad < 0; t++) {
                if (keys[t] != null && !InputSmooth.realizable(sc, t, keys[t])) {
                    bad = t;
                }
            }
            int tapped = airTap ? SprintTap.firstNew(editable, sc) : -1;
            if (tapped >= 0 && (bad < 0 || tapped < bad)) {
                if (!untap(sc, tapped)) {
                    return null;
                }
                continue;
            }
            if (bad < 0) {
                return sc;
            }
            boolean walks = keys[bad].forward() && !keys[bad].sprint;
            if (walks && bad >= 1 && keys[bad - 1] != null && keys[bad - 1].sprint) {
                release(bad - 1);
            } else if (keys[bad] != TickInputs.NONE) {
                release(bad);
            } else {
                return null;
            }
        }
        return null;
    }

    private boolean untap(JumpPhysicsInputs sc, int tapped) {
        int armer = SprintTap.armer(sc, tapped);
        boolean lone = armer >= 0 && keys[armer] != null && keys[armer] != TickInputs.NONE && !contact[armer]
                && !SprintTap.forward(sc, armer + 1);
        if (lone) {
            release(armer);
            return true;
        }
        if (eligible[tapped] && keys[tapped] != TickInputs.NONE) {
            release(tapped);
            return true;
        }
        return false;
    }

    private void release(int t) {
        keys[t] = TickInputs.NONE;
        faced[t] = false;
    }

    private double[] facings(JumpPhysicsInputs scenario) {
        double[] out = new double[n];
        double current = scenario.startYaw;
        int last = -1;
        for (int t = 0; t < n; t++) {
            if (!faced[t]) {
                continue;
            }
            double next = current + Angles.wrapDelta(face[t] - current);
            int span = t - last;
            double floor = StrictMath.pow(EASE_RATIO, span);
            for (int k = 1; k <= span; k++) {
                double eased = (StrictMath.pow(EASE_RATIO, span - k) - floor) / (1.0 - floor);
                out[last + k] = current + (next - current) * eased;
            }
            current = next;
            last = t;
        }
        for (int t = last + 1; t < n; t++) {
            out[t] = current;
        }
        return Angles.wrapAll(out);
    }
}
