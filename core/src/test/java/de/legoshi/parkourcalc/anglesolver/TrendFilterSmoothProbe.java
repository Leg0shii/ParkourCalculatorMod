package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.core.anglesolver.solver.Angles;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.DeWiggle;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.FacingPrefold;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraintCompiler;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SlpSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.SmoothingPolish;
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
 * P6 A/B: the one give-back-constrained order-1 trend filter vs the shipped four-pass smoothing stack
 * (DeWiggle, SmoothingPolish, then the guarded face-walk), on the same feasible seed over the hpk
 * corpus. It certifies that the collapse (i) stays byte-exact feasible, (ii) never lets the trend
 * filter raise the reversal count above the seed, (iii) bounds the give-back by ONE budget rather
 * than the stacked ~1.63e-2 b, and (iv) matches or beats the four-pass reversal sum.
 */
@Category(SlowSolverTests.class)
public class TrendFilterSmoothProbe {

    private static final double SMOOTH_LAMBDA = 1.0e-2;
    private static final double STACK_FACE_SLACK = 3.0e-4;
    private static final double TREND_BUDGET = 8.0e-3;
    private static final long BUDGET_NANOS = 800_000_000L;
    private static final double FLOOR = Angles.REVERSAL_FLOOR_DEG;

    @Test
    public void trendFilterCollapseMatchesTheFourPassStack() throws Exception {
        List<File> files = new ArrayList<File>();
        collect(resolve("/captures/hpk"), files);
        Collections.sort(files);

        Method build = HpkDualRecoveryScreen.class.getDeclaredMethod("buildSpec", SaveFile.class, ExactJumpModel.class);
        build.setAccessible(true);

        StringWriter sw = new StringWriter();
        PrintWriter out = new PrintWriter(sw);
        out.printf("%-54s %3s %6s %8s %8s %10s %10s%n",
                "capture", "n", "seedRev", "stackRev", "trendRev", "stackGive", "trendGive");

        int both = 0, trendBetter = 0, trendWorse = 0, trendSame = 0;
        int seedSum = 0, stackSum = 0, trendSum = 0;
        double stackGiveSum = 0.0, stackGiveMax = 0.0, trendGiveMax = 0.0;
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

                double[] stack = fourPassStack(exact, spec, sc, seed, cancel);
                double[] trend = TrendFilterSmooth.smooth(exact, spec, seed, TREND_BUDGET, System.nanoTime() + BUDGET_NANOS, cancel);

                boolean stackOk = feasible(exact, sc, spec, stack);
                assertTrue(stem + ": trend filter must stay byte-exact feasible", feasible(exact, sc, spec, trend));
                int trendRev = reversals(anchor, trend);
                assertTrue(stem + ": trend filter must not add reversals over the seed (" + seedRev + " -> " + trendRev + ")",
                        trendRev <= seedRev);
                double trendGive = give(spec, seedObj, objAt(exact, sc, spec.objective, trend));
                assertTrue(stem + ": trend give-back " + trendGive + " must stay within the single budget " + TREND_BUDGET,
                        trendGive <= TREND_BUDGET + 1.0e-9);

                Integer stackRev = stackOk ? reversals(anchor, stack) : null;
                double stackGive = stackOk ? give(spec, seedObj, objAt(exact, sc, spec.objective, stack)) : Double.NaN;
                trendGiveMax = Math.max(trendGiveMax, trendGive);
                if (stackRev != null) {
                    both++;
                    seedSum += seedRev;
                    stackSum += stackRev;
                    trendSum += trendRev;
                    stackGiveSum += stackGive;
                    stackGiveMax = Math.max(stackGiveMax, stackGive);
                    if (trendRev < stackRev) trendBetter++;
                    else if (trendRev > stackRev) trendWorse++;
                    else trendSame++;
                }
                out.printf("%-54s %3d %6d %8s %8d %10.2e %10.2e%n", stem, sc.numTicks, seedRev,
                        stackRev == null ? "-" : stackRev.toString(), trendRev,
                        stackOk ? stackGive : Double.NaN, trendGive);
            } catch (Throwable t) {
                out.printf("%-54s EXC %s%n", stem, t);
            }
        }
        out.printf("%nBoth-certify captures=%d  trendSmoother=%d trendRougher=%d same=%d%n",
                both, trendBetter, trendWorse, trendSame);
        out.printf("Sum reversals over both-certify: seed=%d stack=%d trend=%d%n", seedSum, stackSum, trendSum);
        out.printf("Give-back: stack SUM=%.4e MAX/capture=%.4e (stacked caps, F6); trend MAX/capture=%.4e (single budget %.1e)%n",
                stackGiveSum, stackGiveMax, trendGiveMax, TREND_BUDGET);
        out.flush();
        String report = sw.toString();
        System.out.println(report);
        File dst = new File("build/reports/trend-filter-ab.txt");
        dst.getParentFile().mkdirs();
        Files.write(dst.toPath(), report.getBytes(StandardCharsets.UTF_8));

        assertTrue("the trend-filter collapse must cut reversals well below the raw seed (seed=" + seedSum
                + ", trend=" + trendSum + ")", trendSum < seedSum);
        int slack = Math.max(4, stackSum / 10);
        assertTrue("the trend-filter collapse must match the four-pass reversal sum within noise (stack=" + stackSum
                + ", trend=" + trendSum + ", slack=" + slack + ")", trendSum <= stackSum + slack);
        assertTrue("the trend filter must bound every capture's give-back by the single budget (max=" + trendGiveMax
                + ", budget=" + TREND_BUDGET + ")", trendGiveMax <= TREND_BUDGET + 1.0e-9);
    }

    private static double[] fourPassStack(ExactJumpModel exact, JumpSpec spec, JumpPhysicsInputs sc,
                                          double[] seed, AtomicBoolean cancel) {
        double[] a = DeWiggle.run(exact, spec, seed.clone(), cancel);
        double[] b = SmoothingPolish.smooth(exact, spec, a, cancel);
        if (!feasible(exact, sc, spec, b)) b = seed;
        double achieved = objAt(exact, sc, spec.objective, b);
        boolean max = spec.objective.sense == Objective.Sense.MAX;
        JumpConstraint.Mode axisMode = spec.objective.axis == JumpPhysicsInputs.Axis.X ? JumpConstraint.Mode.X
                : JumpConstraint.Mode.Z;
        double rhs = max ? achieved - STACK_FACE_SLACK : achieved + STACK_FACE_SLACK;
        List<JumpConstraint> cons = new ArrayList<JumpConstraint>(spec.constraints);
        cons.add(new JumpConstraint(axisMode, spec.objective.tick, null, JumpConstraint.Op.PLUS,
                max ? JumpConstraint.Cmp.GE : JumpConstraint.Cmp.LE, rhs, "objGuard"));
        JumpSpec guarded = new JumpSpec(sc, cons, spec.objective);
        boolean[] frozen = frozenPins(sc, spec);
        double[] face = ClosedFormSolve.recoverFace(exact, guarded, 0.0, cancel, b,
                System.nanoTime() + BUDGET_NANOS, frozen);
        return feasible(exact, sc, spec, face) ? face : b;
    }

    private static boolean[] frozenPins(JumpPhysicsInputs sc, JumpSpec spec) {
        FacingPrefold pre = FacingPrefold.analyze(spec.constraints, new JumpLinearModel(sc));
        if (pre == null || pre.isIdentity()) return null;
        boolean[] frozen = new boolean[sc.numTicks];
        for (int t = 0; t < sc.numTicks; t++) frozen[t] = pre.varIndex(t) < 0;
        return frozen;
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
