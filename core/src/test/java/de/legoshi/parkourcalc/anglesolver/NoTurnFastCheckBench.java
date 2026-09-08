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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

    private static final class Prepared {
        Case c;
        Loaded L;
        JumpSpec spec;
        FastCheck impl;
        FastCheck audit;
        String error;
    }

    private static final class CaseOut {
        final List<String> lines = new ArrayList<>();
        int hit, bogus, falseReject, miss, reject, undecided, newFeasible, errors;
        int auditReject, auditUnknown, auditContradiction, auditRejectUnverified;
        double cpuSum;
        double auditCpuSum;
        double auditCpuMax;
        Double timing;
        boolean timingFeasible;
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
        String auditName = System.getProperty("pkc.fc.audit", "");
        long auditBudgetNanos = Long.getLong("pkc.fc.auditBudgetMs", 100L) * 1_000_000L;

        FastCheck impl = (FastCheck) Class.forName(implName).getDeclaredConstructor().newInstance();
        FastCheck audit = auditName.isEmpty() ? null
                : (FastCheck) Class.forName(auditName).getDeclaredConstructor().newInstance();
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

        int parallel = Integer.getInteger("pkc.fc.parallel", 1);
        long budgetNanos = budgetMs * 1_000_000L;
        Map<String, Loaded> cache = new HashMap<>();
        ThreadMXBean mx = ManagementFactory.getThreadMXBean();
        AtomicBoolean cancel = new AtomicBoolean(false);
        int hit = 0, bogus = 0, falseReject = 0, miss = 0, reject = 0, undecided = 0, newFeasible = 0, errors = 0;
        List<Double> wallFeasible = new ArrayList<>();
        List<Double> wallInfeasible = new ArrayList<>();
        double cpuSum = 0.0;

        List<Prepared> prepared = new ArrayList<>();
        for (Case c : cases) {
            String key = c.capture + "|" + c.startTick + "|" + c.freeBox;
            Loaded L = cache.get(key);
            boolean fresh = L == null;
            if (fresh) {
                L = load(c);
                cache.put(key, L);
            }
            Prepared pc = new Prepared();
            pc.c = c;
            pc.L = L;
            try {
                int[] combos = parseKeys(c.keys, L.problem.setupEnd + 1);
                boolean[] sprint = NoTurnKeys.latchSprint(combos, c.engage < 0 ? L.problem.setupEnd + 5 : c.engage);
                pc.spec = L.problem.buildSpec(combos, sprint, NoTurnKeys.WA, c.ja);
            } catch (RuntimeException e) {
                pc.error = e.getMessage();
            }
            if (parallel <= 1) {
                pc.impl = impl;
                pc.audit = audit;
                if (fresh) {
                    impl.prepare(L.problem);
                    if (audit != null) audit.prepare(L.problem);
                }
            } else {
                FastCheck own = (FastCheck) Class.forName(implName).getDeclaredConstructor().newInstance();
                if (pc.spec != null) own.prepare(L.problem);
                pc.impl = own;
                if (audit != null) {
                    FastCheck ownAudit = (FastCheck) Class.forName(auditName).getDeclaredConstructor().newInstance();
                    if (pc.spec != null) ownAudit.prepare(L.problem);
                    pc.audit = ownAudit;
                }
            }
            prepared.add(pc);
        }
        if (audit != null) line("audit=" + auditName + " (" + audit.describe() + ") auditBudgetMs=" + auditBudgetNanos / 1_000_000L);

        line("parallel=" + parallel);
        List<CaseOut> results = new ArrayList<>(Collections.nCopies(prepared.size(), (CaseOut) null));
        if (parallel <= 1) {
            for (int i = 0; i < prepared.size(); i++) {
                results.set(i, runCase(prepared.get(i), budgetNanos, auditBudgetNanos, repeat, cancel, mx));
            }
        } else {
            ExecutorService pool = Executors.newFixedThreadPool(parallel);
            List<Future<CaseOut>> futures = new ArrayList<>();
            for (Prepared pc : prepared) {
                final Prepared fpc = pc;
                futures.add(pool.submit(() -> runCase(fpc, budgetNanos, auditBudgetNanos, repeat, cancel, mx)));
            }
            for (int i = 0; i < futures.size(); i++) results.set(i, futures.get(i).get());
            pool.shutdown();
        }

        int auditReject = 0, auditUnknown = 0, auditContradiction = 0, auditRejectUnverified = 0;
        double auditCpuSum = 0.0, auditCpuMax = 0.0;
        for (CaseOut co : results) {
            for (String s : co.lines) line(s);
            hit += co.hit;
            bogus += co.bogus;
            falseReject += co.falseReject;
            miss += co.miss;
            reject += co.reject;
            undecided += co.undecided;
            newFeasible += co.newFeasible;
            errors += co.errors;
            cpuSum += co.cpuSum;
            auditReject += co.auditReject;
            auditUnknown += co.auditUnknown;
            auditContradiction += co.auditContradiction;
            auditRejectUnverified += co.auditRejectUnverified;
            auditCpuSum += co.auditCpuSum;
            auditCpuMax = Math.max(auditCpuMax, co.auditCpuMax);
            if (co.timing != null) (co.timingFeasible ? wallFeasible : wallInfeasible).add(co.timing);
        }
        line(String.format(Locale.ROOT,
                "SUMMARY impl=%s cases=%d feasibleCases=%d hit=%d miss=%d falseReject=%d bogus=%d | infeasibleCases=%d reject=%d undecided=%d newFeasible=%d | errors=%d",
                impl.describe(), cases.size(), wallFeasible.size(), hit, miss, falseReject, bogus,
                wallInfeasible.size(), reject, undecided, newFeasible, errors));
        line(String.format(Locale.ROOT, "TIMING feasible: %s | infeasible: %s | cpuTotal=%.0fms",
                stats(wallFeasible), stats(wallInfeasible), cpuSum));
        if (audit != null) {
            int checks = cases.size() * repeat - errors * repeat;
            line(String.format(Locale.ROOT,
                    "AUDIT impl=%s checks=%d reject=%d (contradictions=%d rejectOfUnverified=%d) unknown=%d cpuMean=%.1fms cpuMax=%.1fms",
                    audit.describe(), checks, auditReject, auditContradiction, auditRejectUnverified, auditUnknown,
                    checks > 0 ? auditCpuSum / checks : 0.0, auditCpuMax));
        }
        File f = new File(out);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        Files.write(f.toPath(), report.toString().getBytes(StandardCharsets.UTF_8));
    }

    private CaseOut runCase(Prepared pc, long budgetNanos, long auditBudgetNanos, int repeat, AtomicBoolean cancel,
                            ThreadMXBean mx) {
        CaseOut out = new CaseOut();
        Case c = pc.c;
        if (pc.error != null) {
            out.lines.add("CASE " + c.id + " ERROR " + pc.error);
            out.errors = 1;
            return out;
        }
        NoTurnProblem p = pc.L.problem;
        JumpSpec spec = pc.spec;
        boolean expectFeasible = "feasible".equals(c.expect);
        for (int r = 0; r < repeat; r++) {
            long cpu0 = mx.getCurrentThreadCpuTime();
            long t0 = System.nanoTime();
            FastCheckVerdict v;
            try {
                v = pc.impl.check(p, spec, pc.L.model, budgetNanos, cancel);
            } catch (RuntimeException e) {
                v = FastCheckVerdict.unknown("exception " + e);
            }
            double wall = (System.nanoTime() - t0) / 1e6;
            double cpu = (mx.getCurrentThreadCpuTime() - cpu0) / 1e6;
            out.cpuSum += cpu;
            boolean verified = false;
            double viol = Double.NaN;
            if (v.kind == FastCheckVerdict.Kind.FEASIBLE && v.yaws != null) {
                JumpPhysicsInputs sc = spec.asScenario();
                double px = Double.isNaN(v.px) ? p.refStart().x : v.px;
                double pz = Double.isNaN(v.pz) ? p.refStart().z : v.pz;
                JumpPhysicsInputs scPin = Scoring.pinnedScenario(sc, px, pz);
                double[] gf = scPin.toGameFacings(Angles.wrapAll(v.yaws));
                ForwardPath fp = pc.L.model.forward(scPin, gf);
                viol = JumpConstraintCompiler.compile(spec).maxViolation(gf, fp);
                verified = viol <= 0.0;
            }
            String outcome;
            if (v.kind == FastCheckVerdict.Kind.FEASIBLE) {
                if (!verified) {
                    outcome = "BOGUS";
                    out.bogus++;
                } else if (expectFeasible) {
                    outcome = "HIT";
                    out.hit++;
                } else {
                    outcome = "NEW_FEASIBLE";
                    out.newFeasible++;
                }
            } else if (v.kind == FastCheckVerdict.Kind.INFEASIBLE) {
                if (expectFeasible) {
                    outcome = "FALSE_REJECT";
                    out.falseReject++;
                } else {
                    outcome = "REJECT";
                    out.reject++;
                }
            } else {
                if (expectFeasible) {
                    outcome = "MISS";
                    out.miss++;
                } else {
                    outcome = "UNDECIDED";
                    out.undecided++;
                }
            }
            if (r == 0) {
                out.timing = wall;
                out.timingFeasible = expectFeasible;
            }
            String auditText = "";
            if (pc.audit != null) {
                long a0 = mx.getCurrentThreadCpuTime();
                FastCheckVerdict av;
                try {
                    av = pc.audit.check(p, spec, pc.L.model, auditBudgetNanos, cancel);
                } catch (RuntimeException e) {
                    av = FastCheckVerdict.unknown("exception " + e);
                }
                double acpu = (mx.getCurrentThreadCpuTime() - a0) / 1e6;
                out.auditCpuSum += acpu;
                out.auditCpuMax = Math.max(out.auditCpuMax, acpu);
                boolean contradiction = av.kind == FastCheckVerdict.Kind.INFEASIBLE && verified;
                if (av.kind == FastCheckVerdict.Kind.INFEASIBLE) out.auditReject++;
                else out.auditUnknown++;
                if (contradiction) out.auditContradiction++;
                if (av.kind == FastCheckVerdict.Kind.INFEASIBLE && !verified) out.auditRejectUnverified++;
                auditText = String.format(Locale.ROOT, " | audit=%s cpu=%.1fms%s note=%s", av.kind, acpu,
                        contradiction ? " CONTRADICTION" : "", av.note);
            }
            out.lines.add(String.format(Locale.ROOT, "CASE %-28s expect=%-10s verdict=%-10s %-12s wall=%8.1fms cpu=%8.1fms viol=%s note=%s%s",
                    c.id, c.expect, v.kind, outcome, wall, cpu,
                    Double.isNaN(viol) ? "-" : String.format(Locale.ROOT, "%.3g", viol), v.note, auditText));
        }
        return out;
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
