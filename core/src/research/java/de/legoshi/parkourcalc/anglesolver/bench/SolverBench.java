package de.legoshi.parkourcalc.anglesolver.bench;

import com.google.gson.Gson;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveClock;
import de.legoshi.parkourcalc.core.anglesolver.solver.WatchScore;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class SolverBench {

    public static final String CATEGORY_HUMAN = "human";
    public static final String CATEGORY_THEORETICAL = "theoretical";
    public static final String CATEGORY_ASIS = "asis";
    public static final String MODE_START = "start";
    public static final String MODE_LAST_JUMP = "lastjump";
    public static final String TIER_FAST = "FAST";
    public static final String TIER_OPTIMIZE = "OPTIMIZE";

    private static final Gson GSON = new Gson();

    static final class Task {
        final BenchJumps.Entry entry;
        final String category;
        final String mode;
        final int budgetMs;
        final long seed;

        Task(BenchJumps.Entry entry, String category, String mode, int budgetMs, long seed) {
            this.entry = entry;
            this.category = category;
            this.mode = mode;
            this.budgetMs = budgetMs;
            this.seed = seed;
        }
    }

    @Test
    public void bench() throws Exception {
        String out = System.getProperty("pkc.bench.out");
        Assume.assumeTrue("set -Dpkc.bench.out=<file.jsonl> to run", out != null);
        run(new File(out));
    }

    public static void main(String[] args) throws Exception {
        String out = System.getProperty("pkc.bench.out");
        if (out == null) throw new IllegalArgumentException("set -Dpkc.bench.out=<file.jsonl>");
        run(new File(out));
        System.exit(0);
    }

    static void run(File out) throws Exception {
        String variant = System.getProperty("pkc.bench.variant", "base");
        String edits = System.getProperty("pkc.bench.graph", "");
        String build = System.getProperty("pkc.bench.build", "");
        boolean repeatable = !Boolean.getBoolean("pkc.bench.wall");
        long timeoutMs = Long.getLong("pkc.bench.timeoutMs", 1_800_000L);
        int verifyEvery = Integer.getInteger("pkc.bench.verify", 0);
        List<Task> tasks = shard(tasks(), System.getProperty("pkc.bench.shard"));
        Set<String> done = recorded(out);
        if (out.getParentFile() != null) out.getParentFile().mkdirs();
        int ran = 0;
        int identical = 0;
        int verified = 0;
        try (PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out, true), StandardCharsets.UTF_8), true)) {
            for (int i = 0; i < tasks.size(); i++) {
                Task t = tasks.get(i);
                if (done.contains(header(t, variant, edits, build, repeatable).runKey())) continue;
                BenchRecord r = solve(t, variant, edits, build, repeatable, timeoutMs);
                w.println(GSON.toJson(r));
                ran++;
                System.out.println(line(r));
                if (repeatable && verifyEvery > 0 && i % verifyEvery == 0 && r.error == null) {
                    BenchRecord again = solve(t, variant, edits, build, true, timeoutMs);
                    again.kind = "verify";
                    boolean same = r.identity().equals(again.identity());
                    again.error = same ? null : "not identical to the first run: " + r.identity() + " against " + again.identity();
                    w.println(GSON.toJson(again));
                    verified++;
                    if (same) identical++;
                    else System.out.println("IDENTITY BROKEN " + r.runKey());
                }
            }
        }
        System.out.println(String.format(Locale.ROOT, "bench done: %d solves written to %s, identity check %d of %d identical",
                ran, out.getPath(), identical, verified));
        if (verified > identical) throw new AssertionError("repeatability broke on " + (verified - identical) + " of " + verified + " checked solves");
    }

    static List<Task> tasks() {
        List<BenchJumps.Entry> entries = BenchJumps.load(System.getProperty("pkc.bench.sets", BenchJumps.SUITE_HPK));
        String filter = System.getProperty("pkc.bench.filter");
        Pattern pattern = filter == null ? null : Pattern.compile(filter);
        String categories = System.getProperty("pkc.bench.categories");
        String[] modes = System.getProperty("pkc.bench.modes", MODE_START).split(",");
        int[] budgets = budgets(System.getProperty("pkc.bench.strengths", "fast"));
        String[] seeds = System.getProperty("pkc.bench.seeds", "0").split(",");
        List<Task> tasks = new ArrayList<Task>();
        for (BenchJumps.Entry e : entries) {
            if (pattern != null && !pattern.matcher(e.jump.key).find()) continue;
            String[] cats = categories != null ? categories.split(",")
                    : !BenchJumps.SUITE_DEV.equals(e.jump.suite) ? new String[] {CATEGORY_HUMAN, CATEGORY_THEORETICAL}
                    : new String[] {CATEGORY_ASIS};
            for (String category : cats) {
                for (String mode : modes) {
                    for (int budget : budgets) {
                        for (String seed : seeds) {
                            tasks.add(new Task(e, category.trim(), mode.trim(), budget, Long.parseLong(seed.trim())));
                        }
                    }
                }
            }
        }
        return tasks;
    }

    private static int[] budgets(String spec) {
        String[] parts = spec.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim().toLowerCase(Locale.ROOT);
            if (p.equals("fast")) out[i] = 0;
            else if (p.endsWith("ms")) out[i] = Integer.parseInt(p.substring(0, p.length() - 2));
            else if (p.endsWith("s")) out[i] = Integer.parseInt(p.substring(0, p.length() - 1)) * 1000;
            else out[i] = Integer.parseInt(p) * 1000;
            if (out[i] != 0 && out[i] < AngleSolverState.OPTIMIZE_TIER_MS) {
                throw new IllegalArgumentException("an Optimize budget is at least " + AngleSolverState.OPTIMIZE_TIER_MS + " ms: " + parts[i]);
            }
        }
        return out;
    }

    private static List<Task> shard(List<Task> tasks, String shard) {
        if (shard == null) return tasks;
        String[] p = shard.split("/");
        int index = Integer.parseInt(p[0].trim());
        int count = Integer.parseInt(p[1].trim());
        List<Task> out = new ArrayList<Task>();
        for (int i = 0; i < tasks.size(); i++) {
            if (i % count == index) out.add(tasks.get(i));
        }
        return out;
    }

    private static Set<String> recorded(File out) throws IOException {
        Set<String> keys = new HashSet<String>();
        if (!out.isFile()) return keys;
        try (BufferedReader r = Files.newBufferedReader(out.toPath(), StandardCharsets.UTF_8)) {
            String l;
            while ((l = r.readLine()) != null) {
                if (l.trim().isEmpty()) continue;
                BenchRecord rec = GSON.fromJson(l, BenchRecord.class);
                if (rec != null && "solve".equals(rec.kind) && rec.jump != null) keys.add(rec.runKey());
            }
        }
        return keys;
    }

    static BenchRecord solve(Task t, String variant, String edits, String build, boolean repeatable, long timeoutMs) {
        BenchRecord rec = header(t, variant, edits, build, repeatable);
        try {
            solveInto(rec, t, edits, repeatable, timeoutMs);
        } catch (Throwable e) {
            rec.error = e.toString();
        }
        return rec;
    }

    private static BenchRecord header(Task t, String variant, String edits, String build, boolean repeatable) {
        BenchRecord rec = new BenchRecord();
        rec.build = build;
        rec.variant = variant;
        rec.graphEdits = edits;
        rec.jump = t.entry.jump;
        rec.category = t.category;
        rec.mode = t.mode;
        rec.strength = new BenchRecord.Strength();
        rec.strength.tier = t.budgetMs == 0 ? TIER_FAST : TIER_OPTIMIZE;
        rec.strength.budgetMs = t.budgetMs;
        rec.seed = t.seed;
        rec.repeatable = repeatable;
        rec.machine = Runtime.getRuntime().availableProcessors() + " cpus, java " + System.getProperty("java.version");
        return rec;
    }

    private static void solveInto(BenchRecord rec, Task t, String edits, boolean repeatable, long timeoutMs) {
        SaveFile file = t.entry.file;
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        if (t.entry.expect != null && t.entry.expect.strafeSide != null) {
            state.setStrafeSide(AngleSolverState.StrafeSide.valueOf(t.entry.expect.strafeSide));
        }
        applyCategory(state, t.category);
        applyMode(state, file, t.mode);
        AngleSolverState.Effort effort;
        if (t.budgetMs == 0) {
            state.setEffort(AngleSolverState.Effort.FAST);
            state.setBudgetMs(0);
            effort = AngleSolverState.Effort.FAST;
        } else {
            state.setBudgetMs(t.budgetMs);
            state.setEffort(AngleSolverState.Effort.THOROUGH);
            effort = AngleSolverState.Effort.THOROUGH;
        }
        state.setRepeatable(repeatable);
        state.setSeed(t.seed);
        state.clearResult();
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, x -> { }, model);
        if (Boolean.getBoolean("pkc.bench.sequential")) engine.setSequentialSolve(true);
        JumpSpec spec = engine.debugBuildSpec();
        SolverGraph graph = null;
        if (edits != null && !edits.trim().isEmpty()) {
            SolverGraph base = t.budgetMs == 0 ? BuiltinGraphs.fast(state.getBudgetMs()) : BuiltinGraphs.optimize(state.getOptimizeSeconds());
            graph = GraphEdits.apply(base, edits);
        }
        long t0 = System.nanoTime();
        engine.solve(effort, graph);
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            sleep(2);
        }
        if (engine.isSolving()) {
            engine.cancel();
            rec.error = "timeout after " + timeoutMs + " ms";
        }
        engine.poll();
        long wallMs = (System.nanoTime() - t0) / 1_000_000L;
        fill(rec, state, engine, spec, wallMs, repeatable);
    }

    static void applyCategory(AngleSolverState state, String category) {
        if (CATEGORY_HUMAN.equals(category) || CATEGORY_ASIS.equals(category)) return;
        if (!CATEGORY_THEORETICAL.equals(category)) throw new IllegalArgumentException("unknown category: " + category);
        state.setDefaultInputs(AngleSolverState.InputMode.FORCE_45);
        for (Integer tick : state.populatedTicks()) {
            TickConstraints tc = state.tickConstraintsOrNull(tick);
            if (tc == null) continue;
            if (tc.getOverride() != null) tc.getOverride().clearInputs();
            Iterator<Constraint> it = tc.getConstraints().iterator();
            while (it.hasNext()) {
                Constraint.Field f = it.next().getField();
                if (f == Constraint.Field.DF || f == Constraint.Field.F) it.remove();
            }
        }
    }

    static void applyMode(AngleSolverState state, SaveFile file, String mode) {
        if (MODE_START.equals(mode)) return;
        if (!MODE_LAST_JUMP.equals(mode)) throw new IllegalArgumentException("unknown mode: " + mode);
        int last = -1;
        int from = state.getStartTick();
        int to = Math.min(state.getLandingTick(), file.rows.size());
        for (int i = from; i < to; i++) {
            SaveFile.Row row = file.rows.get(i);
            SaveFile.DebugTick d = file.debug != null && i < file.debug.size() ? file.debug.get(i) : null;
            if (row.keys != null && row.keys.contains("JUMP") && d != null && d.onGround) last = i;
        }
        if (last < 0) throw new IllegalStateException("no grounded jump tick with a recorded state");
        state.setStartTick(last);
    }

    private static void fill(BenchRecord rec, AngleSolverState state, AngleSolverEngine engine, JumpSpec spec, long wallMs, boolean repeatable) {
        SolveResult r = state.getResult();
        SolveRunRecord run = engine.lastRunRecord();
        rec.problem = new BenchRecord.Problem();
        rec.problem.startTick = state.getStartTick();
        rec.problem.landingTick = state.getLandingTick();
        if (run != null && run.problem != null) {
            rec.problem.hash = run.problem.hash;
            rec.problem.numTicks = run.problem.numTicks;
            rec.problem.jumps = run.problem.jumps;
            rec.problem.constraints = run.problem.constraints;
            rec.problem.axis = run.problem.axis;
            rec.problem.sense = run.problem.sense;
            rec.problem.freeStart = run.problem.freeStart;
        }
        if (run != null && run.config != null) rec.graphHash = run.config.graphHash;
        rec.result = new BenchRecord.Result();
        rec.time = new BenchRecord.Time();
        rec.time.wallMs = wallMs;
        if (repeatable) {
            rec.time.workMs = engine.lastSolveWorkNanos() / 1e6;
            rec.time.overran = engine.lastSolveOverran();
        }
        if (run != null) {
            long[] lastUnits = null;
            for (SolveRunRecord.StepRun s : run.steps) {
                BenchRecord.Step step = new BenchRecord.Step();
                step.id = s.id;
                step.taken = s.taken;
                step.workMs = s.workNanos < 0 ? -1.0 : s.workNanos / 1e6;
                step.objective = s.objective;
                step.gap = s.dualGap;
                step.feasible = s.feasible;
                rec.steps.add(step);
                if (s.workUnits != null) lastUnits = s.workUnits;
            }
            if (lastUnits != null) {
                for (int k = 0; k < lastUnits.length; k++) rec.time.work.put(SolveClock.kindName(k), lastUnits[k]);
            }
            if (run.outcome != null) {
                rec.smoothness = new BenchRecord.Smoothness();
                rec.smoothness.travelDeg = run.outcome.yawTravelDeg;
                rec.smoothness.reversals = run.outcome.yawDirChanges;
                rec.smoothness.maxStepDeg = run.outcome.yawMaxStepDeg;
                rec.smoothness.jerkDeg = run.outcome.yawJerkDeg;
            }
        }
        if (repeatable && engine.lastSolveWork() != null) {
            rec.time.work.clear();
            for (String part : engine.lastSolveWork().split(" ")) {
                int eq = part.indexOf('=');
                if (eq > 0) rec.time.work.put(part.substring(0, eq), Long.parseLong(part.substring(eq + 1)));
            }
        }
        if (r == null) {
            rec.result.verdict = "NONE";
            return;
        }
        rec.result.lands = r.isSuccess();
        rec.result.verdict = r.getVerdict().name();
        rec.result.solver = r.getSolver();
        for (SolveResult.Detail d : r.getDetails()) rec.result.details.put(d.label, d.value);
        if (r.hasObjective()) {
            rec.result.objective = r.getObjectiveValue();
            double offset = offset(spec, r.getObjectiveValue());
            rec.result.offset = Double.isNaN(offset) ? null : offset;
        }
        List<SolveResult.YawEntry> entries = r.getYaws();
        if (entries != null && !entries.isEmpty()) {
            double[] yaws = new double[entries.size()];
            for (int i = 0; i < yaws.length; i++) yaws[i] = entries.get(i).yaw;
            rec.result.yawHash = String.format(Locale.ROOT, "%08x", Arrays.hashCode(yaws));
            rec.turn = new BenchRecord.Turn();
            rec.turn.yaws = yaws;
            Vec3dCore start = engine.lastPlanStart();
            if (start != null) rec.turn.start = new double[] {start.x, start.y, start.z};
            rec.turn.startYaw = engine.lastPlanStartYaw();
            double anchor = rec.turn.startYaw != null ? rec.turn.startYaw : yaws[0];
            WatchScore watch = WatchScore.of(anchor, Angles.wrapAll(yaws), null);
            if (rec.smoothness == null) rec.smoothness = new BenchRecord.Smoothness();
            rec.smoothness.watchCost = watch.cost;
            rec.smoothness.watchTurns = watch.turns;
            rec.smoothness.watchWobbles = watch.wobbles;
            rec.smoothness.watchMicroTurns = watch.microTurns;
            rec.smoothness.watchReversalCost = watch.reversalCost;
        }
    }

    static double offset(JumpSpec spec, double objective) {
        if (spec == null || Double.isNaN(objective)) return Double.NaN;
        double margin = Double.NaN;
        boolean axisX = spec.objective.axis == JumpPhysicsInputs.Axis.X;
        for (JumpConstraint c : spec.constraints) {
            if (c.t1 != spec.objective.tick || c.t2 != null) continue;
            if (c.mode != (axisX ? JumpConstraint.Mode.X : JumpConstraint.Mode.Z)) continue;
            double m;
            if (c.cmp == JumpConstraint.Cmp.GE) m = objective - c.rhs;
            else if (c.cmp == JumpConstraint.Cmp.LE) m = c.rhs - objective;
            else m = -Math.abs(objective - c.rhs);
            if (Double.isNaN(margin) || m < margin) margin = m;
        }
        return margin;
    }

    private static String line(BenchRecord r) {
        if (r.error != null && r.result == null) {
            return String.format(Locale.ROOT, "BENCH %s %s/%s %s seed=%d ERROR %s", r.jump.key, r.category, r.mode, strength(r), r.seed, r.error);
        }
        return String.format(Locale.ROOT, "BENCH %s %s/%s %s seed=%d lands=%s obj=%s offset=%s wallMs=%d workMs=%.1f%s%s",
                r.jump.key, r.category, r.mode, strength(r), r.seed, r.result.lands,
                r.result.objective == null ? "-" : String.format(Locale.ROOT, "%.9f", r.result.objective),
                r.result.offset == null ? "-" : String.format(Locale.ROOT, "%.6f", r.result.offset),
                r.time.wallMs, r.time.workMs, r.time.overran ? " OVERRAN" : "", r.error == null ? "" : " " + r.error);
    }

    static String strength(BenchRecord r) {
        return TIER_FAST.equals(r.strength.tier) ? "fast" : (r.strength.budgetMs % 1000 == 0 ? r.strength.budgetMs / 1000 + "s" : r.strength.budgetMs + "ms");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
