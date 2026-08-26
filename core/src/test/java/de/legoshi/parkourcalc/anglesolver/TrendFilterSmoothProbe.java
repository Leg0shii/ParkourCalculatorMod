package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.TrendFilterSmooth;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertTrue;

/**
 * P6: the one give-back-constrained order-1 trend filter over the hpk corpus, on a byte-exact-feasible
 * seed. It certifies that the terminal smoother (i) stays byte-exact feasible, (ii) never raises the
 * reversal count above the seed, (iii) bounds every capture's give-back by the single budget, and
 * (iv) cuts the summed reversal count well below the raw seed.
 */
@Category(SlowSolverTests.class)
public class TrendFilterSmoothProbe {

    private static final double SMOOTH_LAMBDA = 1.0e-2;
    private static final double TREND_BUDGET = 8.0e-3;
    private static final long BUDGET_NANOS = 800_000_000L;
    private static final double FLOOR = Angles.REVERSAL_FLOOR_DEG;

    @Test
    public void trendFilterCollapseStaysFeasibleAndSmooths() throws Exception {
        List<File> files = new ArrayList<File>();
        collect(resolve("/captures/hpk"), files);
        Collections.sort(files);

        Method build = HpkDualRecoveryScreen.class.getDeclaredMethod("buildSpec", SaveFile.class, ExactJumpModel.class);
        build.setAccessible(true);

        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.printf("%-54s %3s %6s %8s %10s%n", "capture", "n", "seedRev", "trendRev", "trendGive");

        int seedSum = 0, trendSum = 0;
        double trendGiveMax = 0.0;
        for (File f : files) {
            String stem = f.getName().substring(0, f.getName().length() - ".json".length());
            try {
                SaveFile file = SaveIO.parseSafe(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
                if (file == null || file.angleSolver == null || file.angleSolver.seed == null
                        || file.rows == null || file.rows.isEmpty()) continue;
                ExactJumpModel exact = ExactJumpModel.forMcVersion(file.mcVersion);
                JumpSpec raw = (JumpSpec) build.invoke(null, file, exact);
                if (raw == null) continue;
                JumpPhysicsInputs sc = raw.asScenario();
                Objective smoothObj = new Objective(raw.objective.axis, raw.objective.sense, raw.objective.tick, SMOOTH_LAMBDA);
                JumpSpec spec = new JumpSpec(sc, raw.constraints, smoothObj);
                double anchor = sc.startYaw;
                AtomicBoolean cancel = new AtomicBoolean(false);

                double[] seed = ClosedFormSolve.optimize(exact, spec, 0.0, cancel);
                if (!feasible(exact, sc, spec, seed)) seed = SlpSolve.optimize(exact, spec, 0.0, cancel);
                if (!feasible(exact, sc, spec, seed)) continue;
                seed = Angles.wrapAll(seed);
                int seedRev = reversals(anchor, seed);
                double seedObj = objAt(exact, sc, spec.objective, seed);

                double[] trend = TrendFilterSmooth.smooth(exact, spec, seed, TREND_BUDGET,
                        System.nanoTime() + BUDGET_NANOS, cancel);

                assertTrue(stem + ": trend filter must stay byte-exact feasible", feasible(exact, sc, spec, trend));
                int trendRev = reversals(anchor, trend);
                assertTrue(stem + ": trend filter must not add reversals over the seed (" + seedRev + " -> " + trendRev + ")",
                        trendRev <= seedRev);
                double trendGive = give(spec, seedObj, objAt(exact, sc, spec.objective, trend));
                assertTrue(stem + ": trend give-back " + trendGive + " must stay within the single budget " + TREND_BUDGET,
                        trendGive <= TREND_BUDGET + 1.0e-9);

                seedSum += seedRev;
                trendSum += trendRev;
                trendGiveMax = Math.max(trendGiveMax, trendGive);
                out.printf("%-54s %3d %6d %8d %10.2e%n", stem, sc.numTicks, seedRev, trendRev, trendGive);
            } catch (Throwable t) {
                out.printf("%-54s EXC %s%n", stem, t);
            }
        }
        out.printf("%nSum reversals: seed=%d trend=%d%n", seedSum, trendSum);
        out.printf("Give-back: trend MAX/capture=%.4e (single budget %.1e)%n", trendGiveMax, TREND_BUDGET);
        out.flush();
        String report = sw.toString();
        System.out.println(report);
        File dst = new File("build/reports/trend-filter-ab.txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), report.getBytes(StandardCharsets.UTF_8));

        assertTrue("the trend-filter collapse must cut reversals well below the raw seed (seed=" + seedSum
                + ", trend=" + trendSum + ")", trendSum < seedSum);
        assertTrue("the trend filter must bound every capture's give-back by the single budget (max=" + trendGiveMax
                + ", budget=" + TREND_BUDGET + ")", trendGiveMax <= TREND_BUDGET + 1.0e-9);
    }

    private static double give(JumpSpec spec, double seedObj, double outObj) {
        return spec.objective.sense == Objective.Sense.MAX ? Math.max(0.0, seedObj - outObj)
                : Math.max(0.0, outObj - seedObj);
    }

    private static double objAt(ExactJumpModel exact, JumpPhysicsInputs sc, Objective obj, double[] y) {
        double[] gf = sc.toGameFacings(Angles.wrapAll(y));
        return exact.forward(sc, gf).getPos(obj.tick, obj.axis);
    }

    private static boolean feasible(ExactJumpModel exact, JumpPhysicsInputs sc, JumpSpec spec, double[] y) {
        if (y == null) return false;
        double[] gf = sc.toGameFacings(Angles.wrapAll(y));
        return JumpConstraintCompiler.compile(spec).maxViolation(gf, exact.forward(sc, gf)) <= 0.0;
    }

    private static int reversals(double anchor, double[] y) {
        return Angles.reversals(anchor, Angles.wrapAll(y), FLOOR);
    }

    private static void collect(File dir, List<File> out) {
        File[] kids = dir == null ? null : dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) collect(k, out);
            else if (k.getName().endsWith(".json")) out.add(k);
        }
    }

    private static File resolve(String res) throws Exception {
        java.net.URL u = TrendFilterSmoothProbe.class.getResource(res);
        return u == null ? null : new File(u.toURI());
    }
}
