package de.legoshi.parkourcalc.anglesolver.harness;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class WitnessProblem {

    public static final double[] ALL_MARGINS = {0.0, 1.0e-12, 1.0e-9, 1.0e-6, 1.0e-3, 0.05};
    public static final double[] LOOSE_MARGINS = {1.0e-6, 1.0e-4, 1.0e-3, 0.05};

    private static final String[] KINDS = {"X", "Z", "X", "Z", "XR", "ZR", "XREF", "ZREF", "DX", "DZ", "DXV", "DZV",
            "F", "F", "FEQ", "DF", "DF", "DFEQ"};
    private static final double FACING_MARGIN_SCALE = 100.0;
    private static final int MAX_CONSTRAINTS = 4;
    private static final int CLIMB_STEPS = 400;
    private static final double[] CLIMB_TURNS = {45.0, 10.0, 1.0, 0.1, 0.01, 360.0 / 65536.0};

    public final ProblemFixture fixture;
    public final ProblemFixture.Rig rig;
    public final JumpSpec spec;
    public final JumpPhysicsInputs start;
    public final double[] yaws;
    public final double objective;
    public final String label;

    private WitnessProblem(ProblemFixture fixture, ProblemFixture.Rig rig, JumpSpec spec, JumpPhysicsInputs start,
                           double[] yaws, double objective, String label) {
        this.fixture = fixture;
        this.rig = rig;
        this.spec = spec;
        this.start = start;
        this.yaws = yaws;
        this.objective = objective;
        this.label = label;
    }

    public boolean maximizes() {
        return spec.objective.sense == Objective.Sense.MAX;
    }

    public boolean pinnedStart() {
        StartBox box = spec.asScenario().startBox;
        return box == null || box.isPinned();
    }

    public double violation(JumpPhysicsInputs at, double[] yawsDeg) {
        return violation(fixture.model, at, spec, yawsDeg);
    }

    public static WitnessProblem build(String name, long seed, int trial, double[] margins) {
        ProblemFixture pf = ProblemFixture.load("solve", name);
        SaveFile.AngleSolver as = pf.file.angleSolver;
        Random r = new Random(seed * 1000003L + name.hashCode() * 31L + trial);
        int quarterTurns = r.nextInt(4);
        as.result = null;
        keepOnlyTheStartBox(as);
        rotateLaunch(as.seed, quarterTurns);
        JumpSpec bare = fastRig(pf).spec();
        if (bare == null) return null;
        JumpPhysicsInputs sc = bare.asScenario();
        int n = sc.numTicks;
        JumpPhysicsInputs start = startInside(r, sc);
        double[] yaws = randomYaws(r, n, sc.startYaw);
        double[] facings = start.toGameFacings(yaws);
        ForwardPath path = pf.model.forward(start, facings);
        if (!(violation(pf.model, start, bare, yaws) <= 0.0)) return null;
        StringBuilder rows = new StringBuilder();
        int count = 1 + r.nextInt(MAX_CONSTRAINTS);
        for (int i = 0; i < count; i++) {
            String row = addConstraint(r, pf, n, facings, path, margins);
            if (row != null) rows.append(' ').append(row);
        }
        ProblemFixture.Rig rig = fastRig(pf);
        JumpSpec spec = rig.spec();
        if (spec == null) return null;
        double violation = violation(pf.model, start, spec, yaws);
        String label = name + "#" + trial + " n=" + n + " turn=" + (90 * quarterTurns) + " start="
                + (sc.startBox == null || sc.startBox.isPinned() ? "pinned" : sc.startBox.label())
                + " goal=" + spec.objective.sense + spec.objective.axis + rows;
        if (!(violation <= 0.0)) {
            throw new AssertionError(label + ": the generated path misses its own constraints by " + violation);
        }
        double[] climbed = climb(r, pf.model, start, spec, yaws);
        double objective = spec.objective.evaluate(pf.model.forward(start, start.toGameFacings(climbed)));
        return new WitnessProblem(pf, rig, spec, start, climbed, objective, label);
    }

    private static double violation(ExactJumpModel model, JumpPhysicsInputs at, JumpSpec spec, double[] yaws) {
        double[] facings = at.toGameFacings(yaws);
        return JumpConstraintCompiler.compile(new JumpSpec(at, spec.constraints, spec.objective))
                .maxViolation(facings, model.forward(at, facings));
    }

    private static ProblemFixture.Rig fastRig(ProblemFixture pf) {
        ProblemFixture.Rig rig = pf.rig();
        rig.state.setEffort(AngleSolverState.Effort.FAST);
        return rig;
    }

    private static void keepOnlyTheStartBox(SaveFile.AngleSolver as) {
        for (SaveFile.Tick t : as.ticks) {
            if (t.tick != as.startTick) {
                t.constraints.clear();
                continue;
            }
            for (Iterator<SaveFile.Constraint> it = t.constraints.iterator(); it.hasNext(); ) {
                SaveFile.Constraint c = it.next();
                boolean position = "X".equalsIgnoreCase(c.field) || "Z".equalsIgnoreCase(c.field);
                if (!position || c.refTick != null) it.remove();
            }
        }
    }

    private static void rotateLaunch(SaveFile.Start seed, int quarterTurns) {
        if (seed.vel == null || seed.vel.length < 3) return;
        for (int i = 0; i < quarterTurns; i++) {
            double vx = seed.vel[0];
            seed.vel[0] = -seed.vel[2];
            seed.vel[2] = vx;
            seed.yaw = (float) Angles.wrap(seed.yaw + 90.0);
        }
    }

    private static JumpPhysicsInputs startInside(Random r, JumpPhysicsInputs sc) {
        StartBox box = sc.startBox;
        if (box == null || box.isPinned()) return sc;
        double x = box.pxLo + r.nextDouble() * (box.pxHi - box.pxLo);
        double z = box.pzLo + r.nextDouble() * (box.pzHi - box.pzLo);
        double vx = box.vxLo + r.nextDouble() * (box.vxHi - box.vxLo);
        double vz = box.vzLo + r.nextDouble() * (box.vzHi - box.vzLo);
        JumpPhysicsInputs c = sc.copy();
        c.startPos = new Vec3dCore(x, sc.startPos.y, z);
        c.initialVelocity = new Vec3dCore(vx, sc.initialVelocity.y, vz);
        c.startBox = StartBox.pinned(x, z, vx, vz);
        return c;
    }

    private static double[] randomYaws(Random r, int n, double startYaw) {
        double[] y = new double[n];
        int shape = r.nextInt(5);
        double base = shape == 0 ? startYaw : r.nextDouble() * 360.0 - 180.0;
        double drift = (r.nextDouble() - 0.5) * (shape == 2 ? 20.0 : 2.0);
        int flip = 1 + r.nextInt(Math.max(1, n));
        double second = r.nextDouble() * 360.0 - 180.0;
        for (int t = 0; t < n; t++) {
            if (shape <= 1) y[t] = base;
            else if (shape == 2) y[t] = base + drift * t;
            else if (shape == 3) y[t] = r.nextDouble() * 360.0 - 180.0;
            else y[t] = t < flip ? base : second;
        }
        return y;
    }

    private static String addConstraint(Random r, ProblemFixture pf, int n, double[] facings, ForwardPath path,
                                        double[] margins) {
        SaveFile.AngleSolver as = pf.file.angleSolver;
        JumpSpec before = fastRig(pf).spec();
        String kind = KINDS[r.nextInt(KINDS.length)];
        boolean facing = kind.startsWith("F") || kind.startsWith("DF");
        int tick = facing ? r.nextInt(n + 1) : 1 + r.nextInt(n);
        String field = kind.startsWith("DX") ? "DX" : kind.startsWith("DZ") ? "DZ" : kind.startsWith("DF") ? "DF"
                : kind.substring(0, 1);
        boolean atLeast = r.nextBoolean();
        double margin = margins[r.nextInt(margins.length)] * (facing ? FACING_MARGIN_SCALE : 1.0);
        Integer refTick = null;
        if (kind.endsWith("REF")) {
            int other = r.nextInt(n + 1);
            if (other == tick) return null;
            refTick = as.startTick + other;
        }
        boolean versusOtherAxis = kind.equals("DXV") || kind.equals("DZV");
        SaveFile.Tick entry = tickEntry(as, as.startTick + tick);
        String op = atLeast ? "GE" : "LE";
        JumpConstraint atZero = compiled(pf, before, entry, scalar(field, op, 0.0, refTick, versusOtherAxis));
        JumpConstraint atOne = compiled(pf, before, entry, scalar(field, op, 1.0, refTick, versusOtherAxis));
        if (atZero == null || atOne == null) return null;
        double slope = atOne.rhs - atZero.rhs;
        if (Math.abs(Math.abs(slope) - 1.0) > 1.0e-9) return null;
        double slack;
        try {
            slack = JumpConstraintCompiler.evaluate(atZero, facings, path);
        } catch (RuntimeException e) {
            return null;
        }
        double value = slack / slope;
        SaveFile.Constraint c;
        if (kind.equals("XR") || kind.equals("ZR")) {
            c = new SaveFile.Constraint();
            c.range = true;
            c.field = field;
            c.op = "IN";
            c.lo = value - margin;
            c.hi = value + margins[r.nextInt(margins.length)];
            c.loInclusive = true;
            c.hiInclusive = true;
        } else if (kind.equals("FEQ")) {
            c = scalar(field, "EQ", value, refTick, versusOtherAxis);
        } else if (kind.equals("DFEQ")) {
            if (slack != 0.0) return null;
            c = scalar(field, "EQ", 0.0, refTick, versusOtherAxis);
        } else {
            double rhs = atZero.cmp == JumpConstraint.Cmp.GE ? slack - margin : slack + margin;
            c = scalar(field, op, rhs / slope, refTick, versusOtherAxis);
        }
        entry.constraints.add(c);
        return kind + "[" + tick + (refTick != null ? "~" + (refTick - as.startTick) : "") + "]" + c.op
                + (c.range ? c.lo + ".." + c.hi : String.valueOf(c.value));
    }

    private static SaveFile.Tick tickEntry(SaveFile.AngleSolver as, int tick) {
        for (SaveFile.Tick t : as.ticks) if (t.tick == tick) return t;
        SaveFile.Tick entry = new SaveFile.Tick();
        entry.tick = tick;
        as.ticks.add(entry);
        return entry;
    }

    private static SaveFile.Constraint scalar(String field, String op, double value, Integer refTick, boolean vsDz) {
        SaveFile.Constraint c = new SaveFile.Constraint();
        c.range = false;
        c.field = field;
        c.op = op;
        c.value = value;
        c.refTick = refTick;
        c.vsDz = vsDz;
        return c;
    }

    private static JumpConstraint compiled(ProblemFixture pf, JumpSpec before, SaveFile.Tick entry, SaveFile.Constraint c) {
        entry.constraints.add(c);
        try {
            JumpSpec after = fastRig(pf).spec();
            if (after == null) return null;
            List<JumpConstraint> added = added(before, after);
            return added.size() == 1 ? added.get(0) : null;
        } finally {
            entry.constraints.remove(c);
        }
    }

    private static List<JumpConstraint> added(JumpSpec before, JumpSpec after) {
        Map<String, Integer> seen = new HashMap<>();
        for (JumpConstraint c : before.constraints) seen.merge(key(c), 1, Integer::sum);
        List<JumpConstraint> out = new ArrayList<>();
        for (JumpConstraint c : after.constraints) {
            Integer left = seen.get(key(c));
            if (left != null && left > 0) seen.put(key(c), left - 1);
            else out.add(c);
        }
        return out;
    }

    private static String key(JumpConstraint c) {
        return c.mode + ":" + c.t1 + ":" + c.t2 + ":" + c.op + ":" + c.cmp + ":" + c.name + ":" + c.rhs;
    }

    private static double[] climb(Random r, ExactJumpModel model, JumpPhysicsInputs start, JumpSpec spec, double[] yaws) {
        JumpConstraintCompiler.Compiled compiled = JumpConstraintCompiler.compile(new JumpSpec(start, spec.constraints, spec.objective));
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        double[] best = yaws.clone();
        double bestObjective = spec.objective.evaluate(model.forward(start, start.toGameFacings(best)));
        for (int i = 0; i < CLIMB_STEPS; i++) {
            double[] y = best.clone();
            int t = r.nextInt(y.length);
            double turn = CLIMB_TURNS[r.nextInt(CLIMB_TURNS.length)] * (r.nextBoolean() ? 1 : -1);
            y[t] = Angles.wrap(y[t] + turn);
            double[] facings = start.toGameFacings(y);
            ForwardPath path = model.forward(start, facings);
            if (!(compiled.maxViolation(facings, path) <= 0.0)) continue;
            double objective = spec.objective.evaluate(path);
            if (max ? objective > bestObjective : objective < bestObjective) {
                best = y;
                bestObjective = objective;
            }
        }
        return best;
    }
}
