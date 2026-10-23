package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.SolveResult;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolveRunRecord;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolverTrace;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FreeStartSweepBench {

    private static final double SHIFT_X = 2.3;
    private static final double SHIFT_Z = 1.7;
    private static final double BOX_HALF = 0.5;

    private final StringBuilder report = new StringBuilder();

    private void line(String format, Object... args) {
        String s = String.format(Locale.ROOT, format, args);
        System.out.println(s);
        report.append(s).append(System.lineSeparator());
    }

    private static String prop(String key, String dflt) {
        String v = System.getProperty(key);
        if (v == null || v.isEmpty()) v = System.getenv(key.replace('.', '_').toUpperCase(Locale.ROOT));
        return v != null && !v.isEmpty() ? v : dflt;
    }

    @Test
    public void sweep() throws Exception {
        org.junit.Assume.assumeTrue("set -Dpkc.sweep=1 to run", prop("pkc.sweep", null) != null);
        String tag = prop("pkc.sweep.tag", "run");
        long timeoutMs = Long.parseLong(prop("pkc.sweep.timeoutMs", "45000"));
        String variants = prop("pkc.sweep.variants", "base,shift");
        String filter = prop("pkc.sweep.filter", null);
        boolean trace = prop("pkc.sweep.trace", null) != null;

        List<File> files = new ArrayList<>();
        collect(resolve("/captures/hpk"), files);

        line("%-52s %-6s %8s %7s %8s  %s", "capture", "var", "success", "met", "ms", "chain | top nodes");
        for (File f : files) {
            String stem = f.getName().substring(0, f.getName().length() - ".json".length());
            if (filter != null && !matches(filter, stem)) continue;
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            for (String variant : variants.split(",")) {
                variant = variant.trim();
                if (variant.isEmpty()) continue;
                try {
                    benchOne(stem, json, variant, timeoutMs, trace);
                } catch (Throwable t) {
                    line("%-52s %-6s EXC %s", stem, variant, t);
                }
            }
        }
        File dst = new File("build/reports/sweep-" + tag + ".txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), report.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(new File("build/reports/sweep-" + tag + "-nodes.tsv").toPath(),
                stats.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void benchOne(String stem, String json, String variant, long timeoutMs, boolean trace) {
        SaveFile file = SaveIO.parseSafe(json);
        if (file == null || file.angleSolver == null || file.angleSolver.seed == null
                || file.rows == null || file.rows.isEmpty()) {
            line("%-52s %-6s (skipped: unparseable)", stem, variant);
            return;
        }
        if (file.angleSolver.startTick != 0) {
            line("%-52s %-6s (skipped: startTick=%d)", stem, variant, file.angleSolver.startTick);
            return;
        }
        double sx = file.angleSolver.seed.pos[0];
        double sz = file.angleSolver.seed.pos[2];
        SaveFile.Tick boxTick = new SaveFile.Tick();
        boxTick.tick = file.angleSolver.startTick;
        boxTick.constraints.add(range("X", sx - BOX_HALF, sx + BOX_HALF));
        boxTick.constraints.add(range("Z", sz - BOX_HALF, sz + BOX_HALF));
        file.angleSolver.ticks.add(boxTick);
        file.angleSolver.result = null;
        if ("shift".equals(variant)) {
            file.angleSolver.seed.pos[0] += SHIFT_X;
            file.angleSolver.seed.pos[2] += SHIFT_Z;
            if (file.debug != null) {
                for (SaveFile.DebugTick d : file.debug) {
                    if (d != null && d.pos != null && d.pos.length >= 3) {
                        d.pos[0] += SHIFT_X;
                        d.pos[2] += SHIFT_Z;
                    }
                }
            }
        } else if (variant.startsWith("frac")) {
            String[] f = variant.split("~");
            double dx = (Double.parseDouble(f[1]) - 0.5) * 2.0 * BOX_HALF;
            double dz = (Double.parseDouble(f[2]) - 0.5) * 2.0 * BOX_HALF;
            file.angleSolver.seed.pos[0] += dx;
            file.angleSolver.seed.pos[2] += dz;
            if (file.debug != null) {
                for (SaveFile.DebugTick d : file.debug) {
                    if (d != null && d.pos != null && d.pos.length >= 3) {
                        d.pos[0] += dx;
                        d.pos[2] += dz;
                    }
                }
            }
        }

        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        state.setEffort(AngleSolverState.Effort.FAST);
        state.clearResult();
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);

        if (trace) SolverTrace.enable(stem + "-" + variant);
        long t0 = System.nanoTime();
        engine.solve();
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (engine.isSolving() && System.currentTimeMillis() < deadline) {
            engine.poll();
            sleep(2);
        }
        engine.poll();
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        if (trace) SolverTrace.disable();

        SolveResult r = state.getResult();
        if (r == null) {
            line("%-52s %-6s %8s %7s %8d  TIMEOUT", stem, variant, "none", "-", ms);
            return;
        }
        line("%-52s %-6s %8s %4d/%-2d %8d  %s | %s",
                stem, variant, r.isSuccess(), r.getMet(), r.getTotal(), ms,
                r.getSolver(), topNodes(engine.lastRunRecord()));
        appendStats(stem, variant, ms, r, engine.lastRunRecord());
    }

    private final StringBuilder stats = new StringBuilder();

    private void appendStats(String stem, String variant, long ms, SolveResult r, SolveRunRecord rec) {
        if (rec == null) return;
        stats.append(String.format(Locale.ROOT, "RUN\t%s\t%s\t%d\t%s\t%s%n",
                stem, variant, ms, r.isSuccess(), r.getSolver()));
        if (rec.nodes != null) {
            for (SolveRunRecord.NodeRun n : rec.nodes) {
                stats.append(String.format(Locale.ROOT, "NODE\t%s\t%s\t%s\t%d\t%d\t%s\t%d%n",
                        stem, variant, n.id, n.visits, n.elapsedNanos / 1_000_000L, n.taken, n.evals));
            }
        }
        if (rec.race != null && rec.race.exploreNodes != null) {
            for (SolveRunRecord.NodeRun n : rec.race.exploreNodes) {
                stats.append(String.format(Locale.ROOT, "NODE\t%s\t%s\texplore:%s\t%d\t%d\t%s\t%d%n",
                        stem, variant, n.id, n.visits, n.elapsedNanos / 1_000_000L, n.taken, n.evals));
            }
        }
    }

    private static String topNodes(SolveRunRecord rec) {
        if (rec == null) return "-";
        List<SolveRunRecord.NodeRun> nodes = new ArrayList<>();
        if (rec.nodes != null) nodes.addAll(rec.nodes);
        if (rec.race != null && rec.race.exploreNodes != null) {
            for (SolveRunRecord.NodeRun n : rec.race.exploreNodes) {
                SolveRunRecord.NodeRun c = new SolveRunRecord.NodeRun();
                c.id = "explore:" + n.id;
                c.elapsedNanos = n.elapsedNanos;
                c.visits = n.visits;
                nodes.add(c);
            }
        }
        nodes.sort(Comparator.comparingLong((SolveRunRecord.NodeRun n) -> n.elapsedNanos).reversed());
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (SolveRunRecord.NodeRun n : nodes) {
            long nodeMs = n.elapsedNanos / 1_000_000L;
            if (nodeMs < 50 || shown == 5) break;
            if (shown > 0) sb.append(", ");
            sb.append(n.id).append('=').append(nodeMs).append("ms");
            if (n.visits > 1) sb.append("(x").append(n.visits).append(')');
            shown++;
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    private static SaveFile.Constraint range(String field, double lo, double hi) {
        SaveFile.Constraint c = new SaveFile.Constraint();
        c.range = true;
        c.field = field;
        c.lo = lo;
        c.hi = hi;
        c.loInclusive = true;
        c.hiInclusive = true;
        return c;
    }

    private static boolean matches(String filter, String stem) {
        for (String tok : filter.split(",")) {
            if (stem.equals(tok.trim())) return true;
        }
        return false;
    }

    private static File resolve(String path) throws Exception {
        URL url = FreeStartSweepBench.class.getResource(path);
        if (url == null) throw new IllegalStateException("missing " + path);
        return new File(url.toURI());
    }

    private static void collect(File dir, List<File> out) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        Arrays.sort(entries);
        for (File e : entries) {
            if (e.isDirectory()) collect(e, out);
            else if (e.getName().endsWith(".json")) out.add(e);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
