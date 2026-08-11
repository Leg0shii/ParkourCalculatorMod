package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.core.save.SaveFile;
import org.junit.Assume;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Exercises the PRODUCTION ColdBeamSolver end to end on j154 with the winning family config,
 * confirming the promoted beam still rediscovers and byte-exact certifies the cold line.
 * Inert unless PKC_COLD_SMOKE is set (a ~40s solve); run it manually to validate the port.
 */
public class ColdBeamSolverSmokeScreen {

    @Test
    public void j154SolvesColdViaProductionBeam() {
        Assume.assumeTrue("set PKC_COLD_SMOKE=1", "1".equals(System.getenv("PKC_COLD_SMOKE")));
        SaveFile file = ColdTestHarness.loadSave("hpk_human/d12/j154_1bm_Head_Butterfly_Neo");

        ColdBeamSolver.Config cfg = new ColdBeamSolver.Config();
        cfg.coasts = new int[] {KeyLine.A, KeyLine.SD};
        cfg.glides = new int[] {KeyLine.SD, KeyLine.S, KeyLine.WA};
        cfg.presses = new int[] {KeyLine.SD, KeyLine.WA, KeyLine.W};
        cfg.engages = new int[] {KeyLine.W, KeyLine.WA};
        cfg.brakes = new int[0];
        cfg.glideMax = 13;
        cfg.cycles = new ArrayList<ColdBeamSolver.CycleConfig>();
        cfg.cycles.add(cycle(1, 2));
        cfg.cycles.add(cycle(1, 3));
        cfg.cycles.add(cycle(8, 12));
        cfg.sprintEngage = ColdBeamSolver.SprintEngage.ALWAYS;
        cfg.beamCap = 5_000_000;
        cfg.certifyCap = 100_000;
        cfg.probeGate = 999.0;
        cfg.bucketBudget = 3;
        cfg.threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        cfg.budgetMs = 900_000L;

        final long[] built = {0};
        ColdBeamSolver.ProgressSink sink = new ColdBeamSolver.ProgressSink() {
            @Override
            public void onBuildCycle(int cycleIndex, int cycleCount, long extensions, int survivors, long tailCut) {
                System.out.printf(Locale.ROOT, "cycle %d/%d ext=%d survivors=%d tailCut=%d%n",
                        cycleIndex + 1, cycleCount, extensions, survivors, tailCut);
            }

            @Override
            public void onBuilt(int candidates, long tailCut) {
                built[0] = candidates;
                System.out.printf(Locale.ROOT, "built %d candidates (tailCut=%d)%n", candidates, tailCut);
            }

            @Override
            public void onCertify(int done, int total, int certified, long elapsedMs) {
                System.out.printf(Locale.ROOT, "  certify %d/%d certified=%d ms=%d%n",
                        done, total, certified, elapsedMs);
            }

            @Override
            public void onSolved(String sig, int idx, int certified, long elapsedMs) {
                System.out.printf(Locale.ROOT, "SOLVED sig=%s idx=%d certified=%d ms=%d%n",
                        sig, idx, certified, elapsedMs);
            }
        };

        ColdResult r = ColdBeamSolver.solve(file, cfg, sink, new AtomicBoolean(false));
        assertNotNull("no result", r);
        System.out.println(r.summary());
        assertTrue("cold beam did not solve j154: " + r.summary(), r.solved());
    }

    private static ColdBeamSolver.CycleConfig cycle(int lo, int hi) {
        ColdBeamSolver.CycleConfig c = new ColdBeamSolver.CycleConfig();
        c.glideLo = lo;
        c.glideHi = hi;
        return c;
    }
}
