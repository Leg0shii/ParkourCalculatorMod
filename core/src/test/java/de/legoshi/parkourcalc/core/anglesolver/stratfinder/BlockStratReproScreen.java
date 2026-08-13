package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdBeamSolver;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdProblem;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdStratFinder;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.KeyLine;
import de.legoshi.parkourcalc.core.anglesolver.coldsearch.LineSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardPath;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import org.junit.Assume;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockStratReproScreen {

    private static void handLine(ProblemCompiler.Compiled c) {
        ColdProblem p = ColdProblem.fromSave(c.save);
        int n = p.lastPressSeg + 1;
        int[] mk = new int[n];
        boolean[] hold = new boolean[n];
        for (int k = 12; k <= 13 && k < n; k++) {
            mk[k] = KeyLine.W;
            hold[k] = true;
        }
        KeyLine line = new KeyLine(p, mk, hold, KeyLine.W);
        double bestViol = Double.POSITIVE_INFINITY;
        double bestFacing = 0;
        for (double f = -180.0; f <= 180.0; f += 0.25) {
            JumpSpec spec = LineSpec.build(line, f, 601.5, 416.5);
            JumpPhysicsInputs sc = spec.asScenario();
            double[] yaws = new double[sc.numTicks];
            Arrays.fill(yaws, f);
            double[] gf = sc.toGameFacings(yaws);
            ForwardPath path = p.model.forward(sc, gf);
            double v = worstWall(p, path);
            if (v < bestViol) {
                bestViol = v;
                bestFacing = f;
            }
        }
        System.out.println(String.format(Locale.ROOT,
                "hand line: best facing %.2f viol %.6f (negative means inside every wall)",
                bestFacing, bestViol));
        JumpSpec spec = LineSpec.build(line, bestFacing, 601.5, 416.5);
        JumpPhysicsInputs sc = spec.asScenario();
        double[] yaws = new double[sc.numTicks];
        Arrays.fill(yaws, bestFacing);
        ForwardPath path = p.model.forward(sc, sc.toGameFacings(yaws));
        for (ColdProblem.Wall w : p.momentumWalls) {
            double v = w.axisX ? path.posX[w.segTick] : path.posZ[w.segTick];
            System.out.println(String.format(Locale.ROOT, "  mwall t%d %s [%.3f, %.3f] pos %.4f",
                    w.segTick, w.axisX ? "X" : "Z", w.lo, w.hi, v));
        }
        for (ColdProblem.Wall w : p.tailWalls) {
            double v = w.axisX ? path.posX[w.segTick] : path.posZ[w.segTick];
            System.out.println(String.format(Locale.ROOT, "  twall t%d %s [%.3f, %.3f] pos %.4f",
                    w.segTick, w.axisX ? "X" : "Z", w.lo, w.hi, v));
        }
    }

    private static double worstWall(ColdProblem p, ForwardPath path) {
        double worst = Double.NEGATIVE_INFINITY;
        for (ColdProblem.Wall w : p.momentumWalls) {
            double v = w.axisX ? path.posX[w.segTick] : path.posZ[w.segTick];
            worst = Math.max(worst, Math.max(w.lo - v, v - w.hi));
        }
        for (ColdProblem.Wall w : p.tailWalls) {
            double v = w.axisX ? path.posX[w.segTick] : path.posZ[w.segTick];
            worst = Math.max(worst, Math.max(w.lo - v, v - w.hi));
        }
        return worst;
    }

    @Test
    public void screen() {
        Assume.assumeTrue("set PKC_BLOCKSTRAT_REPRO=1 to run", System.getenv("PKC_BLOCKSTRAT_REPRO") != null);

        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(601, 7, 416);
        StratProblem.Segment s1 = new StratProblem.Segment();
        s1.groundLo = 1;
        s1.groundHi = 1;
        s1.landings.add(StratProblem.Area.block(601, 7, 416));
        StratProblem.Segment s2 = new StratProblem.Segment();
        s2.groundLo = 2;
        s2.groundHi = 2;
        s2.landings.add(StratProblem.Area.block(601, 7, 414));
        p.segments.add(s1);
        p.segments.add(s2);

        ProblemCompiler.Compilation comp = ProblemCompiler.compile(p);
        System.out.println("specs=" + comp.specs.size() + " notes=" + comp.notes);
        handLine(comp.specs.get(0));
        for (ProblemCompiler.Compiled c : comp.specs) {
            System.out.println("spec " + c.label + " fires=" + Arrays.toString(c.fireTicks)
                    + " lands=" + Arrays.toString(c.landTicks)
                    + " landingTick=" + c.save.angleSolver.landingTick);
            try {
                ColdProblem cold = ColdProblem.fromSave(c.save);
                System.out.println("  cold ok: presses=" + Arrays.toString(cold.pressSegTicks)
                        + " singleHeld=" + cold.singleHeld + " lastTied=" + cold.lastPressYawTied
                        + " momentumWalls=" + cold.momentumWalls.size()
                        + " tailWalls=" + cold.tailWalls.size());
            } catch (RuntimeException ex) {
                System.out.println("  cold FAILED: " + ex.getMessage());
                continue;
            }
            ColdStratFinder.Request req = new ColdStratFinder.Request();
            req.segments = new ArrayList<ColdStratFinder.SegmentConfig>();
            for (StratProblem.Segment seg : p.segments) {
                ColdStratFinder.SegmentConfig sc = new ColdStratFinder.SegmentConfig();
                sc.maxChanges = seg.maxChanges;
                req.segments.add(sc);
            }
            req.beam.budgetMs = 60_000L;
            req.beam.threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
            ColdStratFinder.Result res = ColdStratFinder.find(c.save, req, new ColdBeamSolver.ProgressSink() {
                @Override
                public void onBuildCycle(int cycleIndex, int cycleCount, long extensions, int survivors, long tailCut) {
                    System.out.println(String.format(Locale.ROOT,
                            "  build cycle %d/%d ext=%d survivors=%d tailCut=%d",
                            cycleIndex, cycleCount, extensions, survivors, tailCut));
                }

                @Override
                public void onBuilt(int candidates, long tailCut) {
                    System.out.println("  built candidates=" + candidates + " tailCut=" + tailCut);
                }

                @Override
                public void onCertify(int done, int total, int certified, long elapsedMs) {
                    if (done == total || done % 2000 == 0) {
                        System.out.println("  certify " + done + "/" + total + " ok=" + certified
                                + " " + elapsedMs + "ms");
                    }
                }

                @Override
                public void onSolved(String sig, int idx, int certified, long elapsedMs) {
                }
            }, new AtomicBoolean(false));
            System.out.println("  candidatesBuilt=" + res.candidatesBuilt + " certified=" + res.certified
                    + " feasible=" + res.feasible + " truncated=" + res.truncated
                    + " strats=" + res.strats.size());
            for (ColdStratFinder.Strat s : res.strats) {
                System.out.println("    strat " + BlockStratFinder.displayLabel(s));
            }
        }
    }
}
