package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemCatalog;
import de.legoshi.parkourcalc.anglesolver.harness.ProblemFixture;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertTrue;

/** Every FAST capture that solves is re-solved with one-way dF rows the solved path already satisfies, so a
 *  solution exists by construction and a miss is a solver regression: random ticks, every turning tick, the
 *  longest one-way run, the first (seam) and last tick, zero-turn ticks in both directions, a minimum turn
 *  just under the real one, a dF = 0 next to one-way rows, and the same rows on a short Optimize. */
@RunWith(Parameterized.class)
@Category(SlowSolverTests.class)
public class YawDirectionCrossCheckTest {

    private static final double TURNING = 0.05;
    private static final double STILL = 1.0e-7;
    private static final long SKIP_BUDGET_MS = 60_000L;
    private static final int OPTIMIZE_TICK_CAP = 60;
    private static final int OPTIMIZE_SECONDS_MIN = 6;

    @Parameters(name = "{0}")
    public static Collection<Object[]> captures() {
        Collection<Object[]> out = new ArrayList<>();
        for (String name : ProblemCatalog.problemNames("solve")) {
            ProblemFixture pf = ProblemFixture.load("solve", name);
            if (pf.expect.effort() != AngleSolverState.Effort.FAST || !pf.expect.shouldSolve(pf.file)) continue;
            if (pf.expect.maxSolveMs != null && pf.expect.maxSolveMs >= SKIP_BUDGET_MS) continue;
            out.add(new Object[]{name});
        }
        return out;
    }

    @Parameter
    public String name;

    private static final class Row {
        final int tick;
        final String op;
        final double rhs;

        Row(int tick, String op, double rhs) {
            this.tick = tick;
            this.op = op;
            this.rhs = rhs;
        }

        @Override
        public String toString() {
            return "T" + (tick + 1) + (op.equals("GE") ? ">=" : op.equals("LE") ? "<=" : "=") + (rhs == 0.0 ? "0" : String.format(java.util.Locale.ROOT, "%.4f", rhs));
        }
    }

    private static final class Turn {
        final int tick;
        final double turn;

        Turn(int tick, double turn) {
            this.tick = tick;
            this.turn = turn;
        }

        Row oneWay() {
            return new Row(tick, turn >= 0.0 ? "GE" : "LE", 0.0);
        }

        Row atLeast(double margin) {
            return turn >= 0.0 ? new Row(tick, "GE", turn - margin) : new Row(tick, "LE", turn + margin);
        }
    }

    private static final class Run {
        final SolveResult result;
        final long ms;

        Run(SolveResult result, long ms) {
            this.result = result;
            this.ms = ms;
        }

        boolean ok() {
            return result != null && result.isSuccess();
        }
    }

    private static Run solve(ProblemFixture pf, List<Row> rows, int optimizeSeconds) throws InterruptedException {
        boolean optimize = optimizeSeconds > 0;
        SaveFile file = pf.file;
        for (Row r : rows) addDf(file, r);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(optimize ? AngleSolverState.Effort.THOROUGH : AngleSolverState.Effort.FAST);
        if (optimize) state.setOptimizeSeconds(optimizeSeconds);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, pf.model);
        long t0 = System.nanoTime();
        engine.solve();
        long deadline = System.currentTimeMillis() + 120_000L;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            Thread.sleep(5L);
        }
        engine.poll();
        return new Run(state.getResult(), (System.nanoTime() - t0) / 1_000_000L);
    }

    private static void addDf(SaveFile file, Row r) {
        SaveFile.Tick entry = null;
        for (SaveFile.Tick t : file.angleSolver.ticks) if (t.tick == r.tick) entry = t;
        if (entry == null) {
            entry = new SaveFile.Tick();
            entry.tick = r.tick;
            file.angleSolver.ticks.add(entry);
        }
        SaveFile.Constraint c = new SaveFile.Constraint();
        c.range = false;
        c.field = "DF";
        c.op = r.op;
        c.value = r.rhs;
        entry.constraints.add(c);
    }

    private static boolean hasDf(SaveFile file, int tick) {
        for (SaveFile.Tick t : file.angleSolver.ticks) {
            if (t.tick != tick) continue;
            for (SaveFile.Constraint c : t.constraints) if ("DF".equals(c.field)) return true;
        }
        return false;
    }

    private static List<Row> rows(List<Turn> turns) {
        List<Row> out = new ArrayList<>();
        for (Turn t : turns) out.add(t.oneWay());
        return out;
    }

    private static List<Turn> pick(List<Turn> from, int k, Random rnd) {
        List<Turn> shuffled = new ArrayList<>(from);
        Collections.shuffle(shuffled, rnd);
        return new ArrayList<>(shuffled.subList(0, Math.min(k, shuffled.size())));
    }

    private static List<Turn> longestRun(List<Turn> turning) {
        List<Turn> best = new ArrayList<>();
        List<Turn> cur = new ArrayList<>();
        for (Turn t : turning) {
            if (!cur.isEmpty() && (cur.get(cur.size() - 1).tick != t.tick - 1
                    || Math.signum(cur.get(cur.size() - 1).turn) != Math.signum(t.turn))) {
                cur = new ArrayList<>();
            }
            cur.add(t);
            if (cur.size() > best.size()) best = cur;
        }
        return best.size() >= 2 ? best : new ArrayList<>();
    }

    @Test
    public void holdsTheSolvedPathsTurns() throws InterruptedException {
        ProblemFixture pf = ProblemFixture.load("solve", name);
        Run base = solve(pf, Collections.emptyList(), 0);
        assertTrue(name + ": the baseline must solve", base.ok());
        List<SolveResult.YawEntry> ys = base.result.getYaws();
        double prev = pf.file.angleSolver.seed.yaw;
        List<Turn> turning = new ArrayList<>();
        List<Turn> still = new ArrayList<>();
        for (int i = 0; i < ys.size(); i++) {
            int tick = ys.get(i).tick - 1;
            double turn = Angles.wrapDelta(ys.get(i).yaw - prev);
            prev = ys.get(i).yaw;
            if (hasDf(pf.file, tick)) continue;
            if (Math.abs(turn) >= TURNING) turning.add(new Turn(tick, turn));
            else if (Math.abs(turn) < STILL) still.add(new Turn(tick, 0.0));
        }
        StringBuilder turnsText = new StringBuilder();
        for (Turn t : turning) turnsText.append('T').append(t.tick + 1).append(':').append(String.format(java.util.Locale.ROOT, "%.2f", t.turn)).append(' ');
        System.out.printf("XCHK %-26s baseline ok %d ms, %d ticks, %d turning, %d still: %s%n", name, base.ms, ys.size(),
                turning.size(), still.size(), turnsText);
        if (turning.isEmpty() && still.isEmpty()) return;

        Random rnd = new Random(name.hashCode());
        List<String> labels = new ArrayList<>();
        List<List<Row>> variants = new ArrayList<>();
        List<Integer> optimize = new ArrayList<>();
        List<Turn> random3 = pick(turning, 3, rnd);
        add(labels, variants, optimize, "random3#0", rows(random3), 0);
        add(labels, variants, optimize, "random3#1", rows(pick(turning, 3, rnd)), 0);
        add(labels, variants, optimize, "all", rows(turning), 0);
        add(labels, variants, optimize, "longestRun", rows(longestRun(turning)), 0);
        if (!turning.isEmpty() && turning.get(0).tick == ys.get(0).tick - 1) {
            add(labels, variants, optimize, "seam", rows(turning.subList(0, 1)), 0);
        }
        if (!turning.isEmpty() && turning.get(turning.size() - 1).tick == ys.get(ys.size() - 1).tick - 1) {
            add(labels, variants, optimize, "last", rows(turning.subList(turning.size() - 1, turning.size())), 0);
        }
        List<Turn> still3 = pick(still, 3, rnd);
        List<Row> stillGe = new ArrayList<>();
        List<Row> stillLe = new ArrayList<>();
        for (Turn t : still3) {
            stillGe.add(new Row(t.tick, "GE", 0.0));
            stillLe.add(new Row(t.tick, "LE", 0.0));
        }
        add(labels, variants, optimize, "stillGe", stillGe, 0);
        add(labels, variants, optimize, "stillLe", stillLe, 0);
        List<Row> margin = new ArrayList<>();
        List<Row> tight = new ArrayList<>();
        for (Turn t : random3) {
            margin.add(t.atLeast(0.01));
            tight.add(t.atLeast(1.0e-3));
        }
        add(labels, variants, optimize, "minTurn0.01", margin, 0);
        add(labels, variants, optimize, "minTurn1e-3", tight, 0);
        if (!still3.isEmpty()) {
            List<Row> mixed = new ArrayList<>(rows(random3));
            mixed.add(new Row(still3.get(0).tick, "EQ", 0.0));
            add(labels, variants, optimize, "withDfZero", mixed, 0);
        }
        boolean withOptimize = ys.size() <= OPTIMIZE_TICK_CAP;

        List<String> failures = new ArrayList<>();
        for (int v = 0; v < variants.size(); v++) {
            if (variants.get(v).isEmpty()) continue;
            Run r = solve(ProblemFixture.load("solve", name), variants.get(v), optimize.get(v));
            if (v == 0 && withOptimize) {
                int seconds = (int) Math.max(OPTIMIZE_SECONDS_MIN, 3L * ((r.ms + 999L) / 1000L));
                add(labels, variants, optimize, "optimize" + seconds + "s", rows(random3), seconds);
            }
            System.out.printf("XCHK %-26s %-12s %s %6d ms met=%s solver=%s rows=%s%n", name, labels.get(v),
                    r.ok() ? "OK  " : "FAIL", r.ms,
                    r.result == null ? "-" : r.result.getMet() + "/" + r.result.getTotal(),
                    r.result == null ? "-" : r.result.getSolver(), variants.get(v));
            if (!r.ok()) failures.add(labels.get(v) + " " + variants.get(v));
        }
        assertTrue(name + ": one-way dF rows the solved path already satisfies must still solve: " + failures,
                failures.isEmpty());
    }

    private static void add(List<String> labels, List<List<Row>> variants, List<Integer> optimize, String label,
                            List<Row> rows, int opt) {
        labels.add(label);
        variants.add(rows);
        optimize.add(opt);
    }
}
