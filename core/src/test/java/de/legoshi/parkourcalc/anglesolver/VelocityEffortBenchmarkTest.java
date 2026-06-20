package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.BlockSelection;
import de.legoshi.parkourcalc.core.anglesolver.TickConstraints;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveCore;
import de.legoshi.parkourcalc.core.anglesolver.velocity.LandingPad;
import de.legoshi.parkourcalc.core.anglesolver.velocity.VelocityFinder;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.sim.TickState;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Assume;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class VelocityEffortBenchmarkTest {

    private static final String SAVE =
            "C:/Users/benja/Desktop/Coding/10 Minecraft/Mods/ParkourCalculatorMod/loader-forge-1.8.9/run/client/parkourcalculator/j008-bfneo-original.json";
    private static final String OUT =
            "C:/Users/benja/Desktop/Coding/10 Minecraft/Mods/ParkourCalculatorMod/velbench-out.txt";

    private static final double VLO = -0.1, VHI = 0.1;
    private static final double EPS = 1.0e-4;

    private enum Setting {
        FAST(VelocityFinder.Accuracy.FAST, false),
        ACCURATE(VelocityFinder.Accuracy.ACCURATE, false),
        HYPER_WINDOW(VelocityFinder.Accuracy.HYPER, false),
        HYPER_GLOBAL(VelocityFinder.Accuracy.HYPER, true);

        final VelocityFinder.Accuracy accuracy;
        final boolean cmaOnMulti;

        Setting(VelocityFinder.Accuracy a, boolean cma) {
            this.accuracy = a;
            this.cmaOnMulti = cma;
        }
    }

    @Test
    public void benchmark() throws Exception {
        Assume.assumeTrue("set VELBENCH=1 to run the velocity-effort benchmark", System.getenv("VELBENCH") != null);

        SaveFile file = SaveIO.parseSafe(Fixtures.read(new File(SAVE)));
        if (file == null) throw new IllegalStateException("could not parse " + SAVE);
        List<TickState> states = reconstructStates(file);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        int landingTick = file.angleSolver.landingTick;
        int res = envInt("VELBENCH_RES", 32);
        long perSolve = envLong("VELBENCH_PERSOLVE", 4000);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "save=%s mc=%s rows=%d debugTicks=%d landingTick=%d res=%dx%d vx/vz[%.3f,%.3f] perSolveMs=%d%n%n",
                new File(SAVE).getName(), file.mcVersion, file.rows.size(), states.size(), landingTick, res, res, VLO, VHI, perSolve));

        if (System.getenv("VELBENCH_CLOSEDONLY") != null) {
            closedFormOnly(sb, "SINGLE JUMP", file, states, model, envInt("VELBENCH_SINGLE", 14), landingTick, res, perSolve);
            closedFormOnly(sb, "MULTI JUMP", file, states, model, envInt("VELBENCH_MULTI", 0), landingTick, res, perSolve);
            Files.write(Paths.get(OUT), sb.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println(sb);
            return;
        }

        if (System.getenv("VELBENCH_PARAMSWEEP") != null) {
            paramSweep(sb, "SINGLE JUMP", file, states, model, envInt("VELBENCH_SINGLE", 14), landingTick, res, perSolve);
            paramSweep(sb, "MULTI JUMP", file, states, model, envInt("VELBENCH_MULTI", 0), landingTick, res, perSolve);
            VelocityFinder.FAST_FEAS_TOL = 1.0e-6;
            VelocityFinder.MULTI_FALLBACK_VIOL = 0.6;
            Files.write(Paths.get(OUT), sb.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println(sb);
            return;
        }

        if (System.getenv("VELBENCH_FEASSWEEP") != null) {
            feasSweep(sb, "SINGLE JUMP", file, states, model, envInt("VELBENCH_SINGLE", 14), landingTick, res, perSolve);
            feasSweep(sb, "MULTI JUMP", file, states, model, envInt("VELBENCH_MULTI", 0), landingTick, res, perSolve);
            VelocityFinder.FAST_FEAS_TOL = 1.0e-6;
            Files.write(Paths.get(OUT), sb.toString().getBytes(StandardCharsets.UTF_8));
            System.out.println(sb);
            return;
        }

        Setting[] gridSettings = System.getenv("VELBENCH_GLOBAL") != null
                ? new Setting[]{Setting.FAST, Setting.ACCURATE, Setting.HYPER_WINDOW, Setting.HYPER_GLOBAL}
                : new Setting[]{Setting.FAST, Setting.ACCURATE, Setting.HYPER_WINDOW};
        runGrid(sb, "SINGLE JUMP", file, states, model, envInt("VELBENCH_SINGLE", 14), landingTick, res, perSolve, gridSettings);
        runGrid(sb, "MULTI JUMP", file, states, model, envInt("VELBENCH_MULTI", 0), landingTick, res, perSolve, gridSettings);

        if (System.getenv("VELBENCH_TRACE") != null) {
            traceWhy(sb, "MULTI JUMP", file, states, model, envInt("VELBENCH_MULTI", 0), landingTick);
        }

        Files.write(Paths.get(OUT), sb.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println(sb);
    }

    private void runGrid(StringBuilder sb, String title, SaveFile file, List<TickState> states, ExactJumpModel model,
                         int startTick, int landingTick, int res, long perSolve, Setting[] settings) {
        int jumps = countJumpKeys(file, startTick, landingTick);
        TickState a = states.get(startTick);
        double[] pb = derivePad(file, landingTick);
        VelocityFinder.Anchor anchor = new VelocityFinder.Anchor(startTick, a.position, a.yaw, a.velocity.y, file.rows.size());
        VelocityFinder.Pad pad = new VelocityFinder.Pad(pb[0], pb[1], pb[2], pb[3]);
        double step = VHI > VLO ? (VHI - VLO) / (res - 1) : 0.0;
        VelocityFinder.Grid grid = new VelocityFinder.Grid(VLO, VHI, step, VLO, VHI, step);

        sb.append(String.format(Locale.ROOT,
                "=== %s === startTick=%d landingTick=%d jumpKeys=%d onGround=%s anchor=(%.3f,%.3f,%.3f) keepVy=%.4f pad x[%.3f,%.3f] z[%.3f,%.3f]%n",
                title, startTick, landingTick, jumps, a.onGround, a.position.x, a.position.y, a.position.z, a.velocity.y,
                pb[0], pb[1], pb[2], pb[3]));

        VelocityFinder.Candidate[][] grids = new VelocityFinder.Candidate[settings.length][];
        long[] times = new long[settings.length];
        for (int s = 0; s < settings.length; s++) {
            VelocityFinder finder = buildFinder(settings[s], file, model, anchor, landingTick, pad, states, startTick, perSolve);
            finder.setAccuracy(settings[s].accuracy);
            long t0 = System.nanoTime();
            grids[s] = sweep(finder, grid, res);
            times[s] = (System.nanoTime() - t0) / 1_000_000L;
        }

        int n = res * res;
        double[] best = new double[n];
        for (int i = 0; i < n; i++) {
            double b = Double.NEGATIVE_INFINITY;
            for (int s = 0; s < settings.length; s++) {
                VelocityFinder.Candidate c = grids[s][i];
                if (c != null && c.lands && c.support > b) b = c.support;
            }
            best[i] = b;
        }

        sb.append(String.format(Locale.ROOT, "%-14s %8s %11s %9s %8s %10s %9s %8s%n",
                "setting", "landers", "sumSupport", "maxSup", "missVsB", "deficit", "optimal%", "time"));
        for (int s = 0; s < settings.length; s++) {
            VelocityFinder.Candidate[] g = grids[s];
            int landers = 0, missed = 0, optimal = 0;
            double sum = 0, max = 0, deficit = 0;
            for (int i = 0; i < n; i++) {
                VelocityFinder.Candidate c = g[i];
                boolean lands = c != null && c.lands;
                boolean anyLands = best[i] > Double.NEGATIVE_INFINITY;
                if (lands) {
                    landers++;
                    sum += c.support;
                    if (c.support > max) max = c.support;
                    deficit += Math.max(0.0, best[i] - c.support);
                    if (best[i] - c.support <= EPS) optimal++;
                } else if (anyLands) {
                    missed++;
                }
            }
            double optPct = landers > 0 ? 100.0 * optimal / landers : 0.0;
            sb.append(String.format(Locale.ROOT, "%-14s %8d %11.4f %9.4f %8d %10.4f %8.1f%% %7dms%n",
                    settings[s].name(), landers, sum, max, missed, deficit, optPct, times[s]));
        }
        sb.append("\n");
    }

    private void closedFormOnly(StringBuilder sb, String title, SaveFile file, List<TickState> states, ExactJumpModel model,
                                int startTick, int landingTick, int res, long perSolve) {
        int jumps = countJumpKeys(file, startTick, landingTick);
        TickState a = states.get(startTick);
        double[] pb = derivePad(file, landingTick);
        VelocityFinder.Anchor anchor = new VelocityFinder.Anchor(startTick, a.position, a.yaw, a.velocity.y, file.rows.size());
        VelocityFinder.Pad pad = new VelocityFinder.Pad(pb[0], pb[1], pb[2], pb[3]);
        VelocityFinder finder = buildFinder(Setting.FAST, file, model, anchor, landingTick, pad, states, startTick, perSolve);
        VelocityFinder.FAST_FEAS_TOL = 1.0e-6;

        int n = res * res;
        double step = VHI > VLO ? (VHI - VLO) / (res - 1) : 0.0;
        int hasResult = 0, certified = 0, bestAimLands = 0;
        double sum = 0, maxSup = 0;
        double[] sups = new double[n];
        int si = 0;
        for (int r = 0; r < res; r++) {
            double vz = VLO + r * step;
            for (int c = 0; c < res; c++) {
                double vx = VLO + c * step;
                VelocityFinder.Candidate cd = finder.evaluateClosedFormOnly(vx, vz);
                if (!Double.isNaN(cd.support)) {
                    hasResult++;
                    sups[si++] = cd.support;
                }
                if (cd.constraintsMet) certified++;
                if (cd.lands) {
                    bestAimLands++;
                    sum += cd.support;
                    if (cd.support > maxSup) maxSup = cd.support;
                }
            }
        }
        java.util.Arrays.sort(sups, 0, si);
        double sMin = si > 0 ? sups[0] : Double.NaN;
        double sMed = si > 0 ? sups[si / 2] : Double.NaN;
        double sMax = si > 0 ? sups[si - 1] : Double.NaN;
        double mean = bestAimLands > 0 ? sum / bestAimLands : 0.0;

        sb.append(String.format(Locale.ROOT, "=== CLOSED-FORM-ONLY %s === startTick=%d landingTick=%d jumpKeys=%d cells=%d pad x[%.3f,%.3f] z[%.3f,%.3f]%n",
                title, startTick, landingTick, jumps, n, pb[0], pb[1], pb[2], pb[3]));
        sb.append(String.format(Locale.ROOT, "  closed form returned a result on:   %d / %d cells%n", hasResult, n));
        sb.append(String.format(Locale.ROOT, "  CERTIFIED feasible (viol<=1e-6):    %d / %d cells%n", certified, n));
        sb.append(String.format(Locale.ROOT, "  best-aim ACTUALLY lands on pad:      %d / %d cells%n", bestAimLands, n));
        sb.append(String.format(Locale.ROOT, "  support of landers: sum=%.4f mean=%.4f max=%.4f%n", sum, mean, maxSup));
        sb.append(String.format(Locale.ROOT, "  best-aim pad support over all results (neg = off-pad): min=%.4f median=%.4f max=%.4f%n%n",
                sMin, sMed, sMax));
    }

    private void paramSweep(StringBuilder sb, String title, SaveFile file, List<TickState> states, ExactJumpModel model,
                            int startTick, int landingTick, int res, long perSolve) {
        int jumps = countJumpKeys(file, startTick, landingTick);
        TickState a = states.get(startTick);
        double[] pb = derivePad(file, landingTick);
        VelocityFinder.Anchor anchor = new VelocityFinder.Anchor(startTick, a.position, a.yaw, a.velocity.y, file.rows.size());
        VelocityFinder.Pad pad = new VelocityFinder.Pad(pb[0], pb[1], pb[2], pb[3]);
        double step = VHI > VLO ? (VHI - VLO) / (res - 1) : 0.0;
        VelocityFinder.Grid grid = new VelocityFinder.Grid(VLO, VHI, step, VLO, VHI, step);

        sb.append(String.format(Locale.ROOT, "=== PARAM SWEEP %s === startTick=%d landingTick=%d jumpKeys=%d cells=%d pad x[%.3f,%.3f] z[%.3f,%.3f]%n",
                title, startTick, landingTick, jumps, res * res, pb[0], pb[1], pb[2], pb[3]));

        double[] fastTols = {1.0e-6, 1.0e-3, 1.0e-2, 0.05, 0.1, 0.2};
        sb.append("FAST (closed form; vary feasTol):\n");
        sb.append(String.format(Locale.ROOT, "%-10s %8s %8s %11s %9s %9s %8s%n", "feasTol", "landers", "offPad", "sumSup", "meanSup", "maxSup", "time"));
        for (double tol : fastTols) {
            VelocityFinder.FAST_FEAS_TOL = tol;
            VelocityFinder.MULTI_FALLBACK_VIOL = 0.6;
            sb.append(String.format(Locale.ROOT, "%-10.1e ", tol)).append(runOne(Setting.FAST, file, model, anchor, landingTick, pad, states, startTick, perSolve, grid, res)).append("\n");
        }

        double[] accTols = {1.0e-6, 1.0e-2};
        double[] gates = {0.6, 1.0, 2.0, 5.0, 100.0};
        sb.append("ACCURATE (closed form + window; vary feasTol x fallbackViol):\n");
        sb.append(String.format(Locale.ROOT, "%-10s %-9s %8s %8s %11s %9s %9s %8s%n", "feasTol", "fallback", "landers", "offPad", "sumSup", "meanSup", "maxSup", "time"));
        for (double tol : accTols) {
            for (double gate : gates) {
                VelocityFinder.FAST_FEAS_TOL = tol;
                VelocityFinder.MULTI_FALLBACK_VIOL = gate;
                sb.append(String.format(Locale.ROOT, "%-10.1e %-9.1f ", tol, gate))
                        .append(runOne(Setting.ACCURATE, file, model, anchor, landingTick, pad, states, startTick, perSolve, grid, res)).append("\n");
            }
        }
        sb.append("\n");
    }

    private String runOne(Setting setting, SaveFile file, ExactJumpModel model, VelocityFinder.Anchor anchor,
                          int landingTick, VelocityFinder.Pad pad, List<TickState> states, int startTick, long perSolve,
                          VelocityFinder.Grid grid, int res) {
        VelocityFinder finder = buildFinder(setting, file, model, anchor, landingTick, pad, states, startTick, perSolve);
        finder.setAccuracy(setting.accuracy);
        long t0 = System.nanoTime();
        VelocityFinder.Candidate[] g = sweep(finder, grid, res);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        int landers = 0, offPad = 0;
        double sum = 0, max = 0;
        for (VelocityFinder.Candidate c : g) {
            if (c == null) continue;
            if (c.lands) {
                landers++;
                sum += c.support;
                if (c.support > max) max = c.support;
            } else if (c.constraintsMet) {
                offPad++;
            }
        }
        double mean = landers > 0 ? sum / landers : 0.0;
        return String.format(Locale.ROOT, "%8d %8d %11.4f %9.4f %9.4f %6dms", landers, offPad, sum, mean, max, ms);
    }

    private void feasSweep(StringBuilder sb, String title, SaveFile file, List<TickState> states, ExactJumpModel model,
                           int startTick, int landingTick, int res, long perSolve) {
        int jumps = countJumpKeys(file, startTick, landingTick);
        TickState a = states.get(startTick);
        double[] pb = derivePad(file, landingTick);
        VelocityFinder.Anchor anchor = new VelocityFinder.Anchor(startTick, a.position, a.yaw, a.velocity.y, file.rows.size());
        VelocityFinder.Pad pad = new VelocityFinder.Pad(pb[0], pb[1], pb[2], pb[3]);
        double step = VHI > VLO ? (VHI - VLO) / (res - 1) : 0.0;
        VelocityFinder.Grid grid = new VelocityFinder.Grid(VLO, VHI, step, VLO, VHI, step);

        sb.append(String.format(Locale.ROOT, "=== FEAS SWEEP %s === startTick=%d landingTick=%d jumpKeys=%d cells=%d pad x[%.3f,%.3f] z[%.3f,%.3f]%n",
                title, startTick, landingTick, jumps, res * res, pb[0], pb[1], pb[2], pb[3]));
        sb.append(String.format(Locale.ROOT, "%-10s %-9s %8s %8s %11s %9s %9s%n",
                "feasTol", "setting", "landers", "offPad", "sumSupport", "meanSup", "maxSup"));

        double[] tols = {1.0e-6, 1.0e-4, 1.0e-3, 1.0e-2, 0.05, 0.1, 0.2, 0.4, 0.6};
        Setting[] cf = {Setting.FAST, Setting.ACCURATE};
        for (double tol : tols) {
            VelocityFinder.FAST_FEAS_TOL = tol;
            for (Setting setting : cf) {
                VelocityFinder finder = buildFinder(setting, file, model, anchor, landingTick, pad, states, startTick, perSolve);
                finder.setAccuracy(setting.accuracy);
                VelocityFinder.Candidate[] g = sweep(finder, grid, res);
                int landers = 0, offPad = 0;
                double sum = 0, max = 0;
                for (VelocityFinder.Candidate c : g) {
                    if (c == null) continue;
                    if (c.lands) {
                        landers++;
                        sum += c.support;
                        if (c.support > max) max = c.support;
                    } else if (c.constraintsMet) {
                        offPad++;
                    }
                }
                double mean = landers > 0 ? sum / landers : 0.0;
                sb.append(String.format(Locale.ROOT, "%-10.1e %-9s %8d %8d %11.4f %9.4f %9.4f%n",
                        tol, setting.name(), landers, offPad, sum, mean, max));
            }
        }
        sb.append("\n");
    }

    private void traceWhy(StringBuilder sb, String title, SaveFile file, List<TickState> states, ExactJumpModel model,
                          int startTick, int landingTick) throws Exception {
        TickState a = states.get(startTick);
        double[] pb = derivePad(file, landingTick);
        VelocityFinder.Anchor anchor = new VelocityFinder.Anchor(startTick, a.position, a.yaw, a.velocity.y, file.rows.size());
        VelocityFinder.Pad pad = new VelocityFinder.Pad(pb[0], pb[1], pb[2], pb[3]);
        long tracePerSolve = envLong("VELBENCH_TRACE_PERSOLVE", 30000);

        VelocityFinder windowF = buildFinder(Setting.HYPER_WINDOW, file, model, anchor, landingTick, pad, states, startTick, 6000);
        windowF.setAccuracy(VelocityFinder.Accuracy.HYPER);
        double fevx = 0.0, fevz = 0.0;
        boolean found = false;
        for (double vz = VLO; vz <= VHI && !found; vz += 0.025) {
            for (double vx = VLO; vx <= VHI; vx += 0.025) {
                VelocityFinder.Candidate c = windowF.evaluate(vx, vz);
                if (c.lands) { fevx = vx; fevz = vz; found = true; break; }
            }
        }

        PrintStream o = System.out, e = System.err;
        ByteArrayOutputStream cap = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(cap, true, "UTF-8");
        double[][] cells = {{fevx, fevz}, {0.0, 0.0}, {0.02, -0.02}};
        try {
            System.setOut(ps);
            System.setErr(ps);
            ClosedFormSolve.DEBUG = true;
            LongRunSolver.DEBUG = true;
            SolveCore.TRACE = true;
            VelocityFinder.TRACE = true;
            for (double[] cell : cells) {
                System.out.printf(Locale.ROOT, "%n##### TRACE %s CELL v=(%.4f,%.4f) feasibleProbe=%s pad x[%.3f,%.3f] z[%.3f,%.3f] #####%n",
                        title, cell[0], cell[1], found, pb[0], pb[1], pb[2], pb[3]);

                System.out.println("--- FAST: closed-form only ---");
                report("FAST", buildFinder(Setting.FAST, file, model, anchor, landingTick, pad, states, startTick, 6000),
                        VelocityFinder.Accuracy.FAST, cell);

                System.out.println("--- HYPER_WINDOW: receding-horizon window solver ---");
                report("WINDOW", buildFinder(Setting.HYPER_WINDOW, file, model, anchor, landingTick, pad, states, startTick, 6000),
                        VelocityFinder.Accuracy.HYPER, cell);

                System.out.println("--- HYPER_GLOBAL: CMA-ES from scratch (" + tracePerSolve + "ms budget) ---");
                report("CMA", buildFinder(Setting.HYPER_GLOBAL, file, model, anchor, landingTick, pad, states, startTick, tracePerSolve),
                        VelocityFinder.Accuracy.HYPER, cell);
            }
        } finally {
            ClosedFormSolve.DEBUG = false;
            LongRunSolver.DEBUG = false;
            SolveCore.TRACE = false;
            VelocityFinder.TRACE = false;
            System.setOut(o);
            System.setErr(e);
        }
        sb.append("===== ").append(title).append(" WHY-TRACE =====\n");
        sb.append(cap.toString("UTF-8"));
        sb.append("\n");
    }

    private void report(String tag, VelocityFinder finder, VelocityFinder.Accuracy acc, double[] cell) {
        finder.setAccuracy(acc);
        VelocityFinder.Candidate c = finder.evaluate(cell[0], cell[1]);
        System.out.printf(Locale.ROOT, "%s RESULT -> constraintsMet=%s lands=%s support=%.5f landX=%.4f landZ=%.4f%n",
                tag, c.constraintsMet, c.lands, c.support, c.landX, c.landZ);
    }

    private VelocityFinder buildFinder(Setting setting, SaveFile file, ExactJumpModel model,
                                       VelocityFinder.Anchor anchor, int landingTick, VelocityFinder.Pad pad,
                                       List<TickState> states, int startTick, long perSolveMs) {
        final boolean cma = setting.cmaOnMulti;
        final int restarts = envInt("VELBENCH_RESTARTS", 24);
        final int maxEval = envInt("VELBENCH_EVALS", 6000);
        VelocityFinder.ProblemFactory problem = new VelocityFinder.ProblemFactory() {
            public AngleSolverState newState() {
                AngleSolverState s = new AngleSolverState();
                SaveIO.applyAngleSolverTo(file, s);
                s.setStartTick(startTick);
                s.setLandingTick(landingTick);
                if (cma) {
                    s.setEffort(AngleSolverState.Effort.CUSTOM);
                    AngleSolverState.SolveBudget b = s.getSolveBudget();
                    b.setUseWindowSolver(false);
                    b.setRestarts(restarts);
                    b.setMaxEval(maxEval);
                    b.setPolishCount(envInt("VELBENCH_POLISH", 4));
                    b.setPolishDepth(AngleSolverState.PolishDepth.EXHAUSTIVE);
                }
                return s;
            }

            public InputData newInputs() {
                InputData in = new InputData();
                SaveIO.applyRowsTo(file, in);
                return in;
            }
        };
        return new VelocityFinder(problem, model, anchor, landingTick, pad, states, perSolveMs);
    }

    private VelocityFinder.Candidate[] sweep(VelocityFinder finder, VelocityFinder.Grid grid, int res) {
        int threads = envInt("VELBENCH_SWEEP_THREADS", Math.max(2, Runtime.getRuntime().availableProcessors()));
        List<VelocityFinder.Candidate> list = finder.sweepParallel(grid, threads, null);
        return list.toArray(new VelocityFinder.Candidate[0]);
    }

    private double[] derivePad(SaveFile file, int landingTick) {
        AngleSolverState probe = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, probe);
        BlockSelection land = probe.getLandBlock();
        double[] baseBox = land != null
                ? new double[]{land.box.min.x, land.box.max.x, land.box.min.z, land.box.max.z}
                : new double[]{-0.5, 0.5, -0.5, 0.5};
        TickConstraints tc = probe.tickConstraintsOrNull(landingTick);
        return LandingPad.derive(tc == null ? null : tc.getConstraints(), baseBox);
    }

    private int countJumpKeys(SaveFile file, int startTick, int landingTick) {
        int jumps = 0;
        int hi = Math.min(landingTick, file.rows.size() - 1);
        for (int t = Math.max(0, startTick); t <= hi; t++) {
            if (file.rows.get(t).keys.contains("JUMP")) jumps++;
        }
        return jumps;
    }

    private List<TickState> reconstructStates(SaveFile file) {
        List<TickState> out = new ArrayList<>();
        if (file.debug == null) return out;
        for (SaveFile.DebugTick d : file.debug) {
            Vec3dCore pos = new Vec3dCore(d.pos[0], d.pos[1], d.pos[2]);
            Vec3dCore vel = new Vec3dCore(d.vel[0], d.vel[1], d.vel[2]);
            double colAngle = d.collisionAngle == null ? Double.NaN : d.collisionAngle;
            float mf = d.moveForward == null ? Float.NaN : d.moveForward;
            float ms = d.moveStrafe == null ? Float.NaN : d.moveStrafe;
            out.add(new TickState(pos, d.onGround, d.sneaking, d.wallCollision, d.yaw,
                    Collections.<Vec3dCore>emptyList(), vel, d.softCollision, colAngle, d.sprinting, mf, ms));
        }
        return out;
    }

    private static int envInt(String key, int def) {
        String v = System.getenv(key);
        try {
            return v == null ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static long envLong(String key, long def) {
        String v = System.getenv(key);
        try {
            return v == null ? def : Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
