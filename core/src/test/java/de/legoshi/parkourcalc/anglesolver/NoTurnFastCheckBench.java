package de.legoshi.parkourcalc.anglesolver;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.Constraint;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.graph.Scoring;
import de.legoshi.parkourcalc.core.anglesolver.noturn.FastCheck;
import de.legoshi.parkourcalc.core.anglesolver.noturn.FastCheckVerdict;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class NoTurnFastCheckBench {

    private static final class Case {
        String id;
        String capture;
        int startTick;
        double freeBox;
        String keys;
        int engage;
        boolean ja;
        String expect;
    }

    private static final class Loaded {
        NoTurnProblem problem;
        ExactJumpModel model;
    }

    private final StringBuilder report = new StringBuilder();

    private void line(String s) {
        System.out.println(s);
        report.append(s).append('\n');
    }

    @Test
    public void bench() throws Exception {
        String casesPath = System.getProperty("pkc.fc.cases");
        Assume.assumeTrue("set -Dpkc.fc.cases=<jsonl>", casesPath != null && !casesPath.isEmpty());
        String implName = System.getProperty("pkc.fc.impl",
                "de.legoshi.parkourcalc.core.anglesolver.noturn.SearchGraphCheck");
        long budgetMs = Long.getLong("pkc.fc.budgetMs", 3000L);
        String out = System.getProperty("pkc.fc.out", "build/reports/fastcheck.txt");
        String only = System.getProperty("pkc.fc.only", "");
        int repeat = Integer.getInteger("pkc.fc.repeat", 1);

        FastCheck impl = (FastCheck) Class.forName(implName).getDeclaredConstructor().newInstance();
        List<Case> cases = new ArrayList<>();
        for (String raw : Files.readAllLines(new File(casesPath).toPath(), StandardCharsets.UTF_8)) {
            String t = raw.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            JsonObject o = new JsonParser().parse(t).getAsJsonObject();
            Case c = new Case();
            c.id = o.get("id").getAsString();
            c.capture = o.get("capture").getAsString();
            c.startTick = o.has("startTick") ? o.get("startTick").getAsInt() : -1;
            c.freeBox = o.has("freeBox") ? o.get("freeBox").getAsDouble() : 0.0;
            c.keys = o.get("keys").getAsString();
            c.engage = o.has("engage") ? o.get("engage").getAsInt() : -1;
            c.ja = o.has("ja") && o.get("ja").getAsBoolean();
            c.expect = o.has("expect") ? o.get("expect").getAsString() : "unknown";
            if (only.isEmpty() || c.id.contains(only) || c.capture.contains(only)) cases.add(c);
        }
        line("impl=" + implName + " (" + impl.describe() + ") budgetMs=" + budgetMs + " cases=" + cases.size()
                + " repeat=" + repeat + " cpus=" + Runtime.getRuntime().availableProcessors());

        Map<String, Loaded> cache = new HashMap<>();
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        AtomicBoolean cancel = new AtomicBoolean(false);
        int hit = 0, bogus = 0, falseReject = 0, miss = 0, reject = 0, undecided = 0, newFeasible = 0, errors = 0;
        List<Double> wallFeasible = new ArrayList<>();
        List<Double> wallInfeasible = new ArrayList<>();
        double cpuSum = 0.0;
        for (Case c : cases) {
            String key = c.capture + "|" + c.startTick + "|" + c.freeBox;
            Loaded L = cache.get(key);
            if (L == null) {
                L = load(c);
                cache.put(key, L);
                impl.prepare(L.problem);
            }
            NoTurnProblem p = L.problem;
            int[] combos;
            try {
                combos = parseKeys(c.keys, p.setupEnd + 1);
            } catch (RuntimeException e) {
                line("CASE " + c.id + " ERROR " + e.getMessage());
                errors++;
                continue;
            }
            boolean[] sprint = NoTurnKeys.latchSprint(combos, c.engage < 0 ? p.setupEnd + 5 : c.engage);
            JumpSpec spec = p.buildSpec(combos, sprint, NoTurnKeys.WA, c.ja);
            for (int r = 0; r < repeat; r++) {
                long cpu0 = mx.getCurrentThreadCpuTime();
                long t0 = System.nanoTime();
                FastCheckVerdict v;
                try {
                    v = impl.check(p, spec, L.model, budgetMs * 1_000_000L, cancel);
                } catch (RuntimeException e) {
                    v = FastCheckVerdict.unknown("exception " + e);
                }
                double wall = (System.nanoTime() - t0) / 1e6;
                double cpu = (mx.getCurrentThreadCpuTime() - cpu0) / 1e6;
                cpuSum += cpu;
                boolean verified = false;
                double viol = Double.NaN;
                if (v.kind == FastCheckVerdict.Kind.FEASIBLE && v.yaws != null) {
                    JumpPhysicsInputs sc = spec.asScenario();
                    double px = Double.isNaN(v.px) ? p.refStart().x : v.px;
                    double pz = Double.isNaN(v.pz) ? p.refStart().z : v.pz;
                    JumpPhysicsInputs scPin = Scoring.pinnedScenario(sc, px, pz);
                    double[] gf = scPin.toGameFacings(Angles.wrapAll(v.yaws));
                    ForwardPath fp = L.model.forward(scPin, gf);
                    viol = JumpConstraintCompiler.compile(spec).maxViolation(gf, fp);
                    verified = viol <= 0.0;
                }
                String outcome;
                boolean expectFeasible = "feasible".equals(c.expect);
                if (v.kind == FastCheckVerdict.Kind.FEASIBLE) {
                    if (!verified) {
                        outcome = "BOGUS";
                        bogus++;
                    } else if (expectFeasible) {
                        outcome = "HIT";
                        hit++;
                    } else {
                        outcome = "NEW_FEASIBLE";
                        newFeasible++;
                    }
                } else if (v.kind == FastCheckVerdict.Kind.INFEASIBLE) {
                    if (expectFeasible) {
                        outcome = "FALSE_REJECT";
                        falseReject++;
                    } else {
                        outcome = "REJECT";
                        reject++;
                    }
                } else {
                    if (expectFeasible) {
                        outcome = "MISS";
                        miss++;
                    } else {
                        outcome = "UNDECIDED";
                        undecided++;
                    }
                }
                if (r == 0) (expectFeasible ? wallFeasible : wallInfeasible).add(wall);
                line(String.format(Locale.ROOT, "CASE %-28s expect=%-10s verdict=%-10s %-12s wall=%8.1fms cpu=%8.1fms viol=%s note=%s",
                        c.id, c.expect, v.kind, outcome, wall, cpu,
                        Double.isNaN(viol) ? "-" : String.format(Locale.ROOT, "%.3g", viol), v.note));
            }
        }
        line(String.format(Locale.ROOT,
                "SUMMARY impl=%s cases=%d feasibleCases=%d hit=%d miss=%d falseReject=%d bogus=%d | infeasibleCases=%d reject=%d undecided=%d newFeasible=%d | errors=%d",
                impl.describe(), cases.size(), wallFeasible.size(), hit, miss, falseReject, bogus,
                wallInfeasible.size(), reject, undecided, newFeasible, errors));
        line(String.format(Locale.ROOT, "TIMING feasible: %s | infeasible: %s | cpuTotal=%.0fms",
                stats(wallFeasible), stats(wallInfeasible), cpuSum));
        File f = new File(out);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        Files.write(f.toPath(), report.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String stats(List<Double> v) {
        if (v.isEmpty()) return "n=0";
        double[] a = new double[v.size()];
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            a[i] = v.get(i);
            sum += a[i];
        }
        Arrays.sort(a);
        return String.format(Locale.ROOT, "n=%d mean=%.0fms median=%.0fms max=%.0fms", a.length, sum / a.length,
                a[a.length / 2], a[a.length - 1]);
    }

    private Loaded load(Case c) throws Exception {
        String raw;
        File direct = new File(c.capture);
        if (direct.isFile()) raw = new String(Files.readAllBytes(direct.toPath()), StandardCharsets.UTF_8);
        else raw = Fixtures.rawPool(c.capture);
        SaveFile file = SaveIO.parseSafe(raw);
        if (file == null) throw new IllegalStateException(c.capture + ": failed to parse");
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        if (c.startTick >= 0) state.setStartTick(c.startTick);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        JumpSpec spec = engine.debugBuildSpec();
        if (spec == null) throw new IllegalStateException(c.capture + ": no spec");
        JumpPhysicsInputs sc0 = spec.asScenario();
        boolean hasFree = sc0.startBox != null && sc0.startBox.startFree();
        if (c.freeBox > 0 && !hasFree) {
            TickConstraints tc = state.tickConstraints(state.getStartTick());
            tc.getConstraints().add(Constraint.range(Constraint.Field.X, sc0.startPos.x - c.freeBox,
                    sc0.startPos.x + c.freeBox, true, true));
            tc.getConstraints().add(Constraint.range(Constraint.Field.Z, sc0.startPos.z - c.freeBox,
                    sc0.startPos.z + c.freeBox, true, true));
            engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
            spec = engine.debugBuildSpec();
        }
        Loaded L = new Loaded();
        L.problem = NoTurnProblem.from(spec, model);
        L.model = model;
        if (L.problem.issue != null) throw new IllegalStateException(c.capture + ": " + L.problem.issue);
        return L;
    }

    static int[] parseKeys(String text, int len) {
        String[] labels = {"-", "W", "WA", "WD", "A", "D", "S", "SA", "SD"};
        int[] combos = new int[len];
        int t = 0;
        for (String tok : text.trim().split(" +")) {
            if (tok.isEmpty()) continue;
            int x = tok.indexOf('x');
            String label = x > 0 ? tok.substring(0, x) : tok;
            int count = x > 0 ? Integer.parseInt(tok.substring(x + 1)) : 1;
            int combo = Arrays.asList(labels).indexOf(label);
            if (combo < 0) throw new IllegalArgumentException("bad key token " + tok);
            for (int i = 0; i < count && t < len; i++) combos[t++] = combo;
        }
        if (t != len) throw new IllegalArgumentException("keys cover " + t + " ticks, need " + len);
        return combos;
    }
}
