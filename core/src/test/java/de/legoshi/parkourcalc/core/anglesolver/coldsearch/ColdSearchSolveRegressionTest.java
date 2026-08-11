package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.core.save.SaveFile;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Search-level regression: runs the actual cold beam (not a stored sig) and asserts it rediscovers and
 * byte-exact certifies the line cold. Distinct from ColdSearchRegressionTest, which only re-certifies a
 * known sig. Slow (tens of seconds to a couple of minutes per jump).
 */
@Category(SlowSolverTests.class)
public class ColdSearchSolveRegressionTest {

    private static SaveFile load(String stem) {
        return ColdTestHarness.loadSave(stem);
    }

    private static ColdBeamSolver.CycleConfig cycle(int lo, int hi) {
        ColdBeamSolver.CycleConfig c = new ColdBeamSolver.CycleConfig();
        c.glideLo = lo;
        c.glideHi = hi;
        return c;
    }

    private static void assertSolvesCold(String stem, ColdBeamSolver.Config cfg) {
        cfg.threads = Math.max(2, Runtime.getRuntime().availableProcessors());
        cfg.budgetMs = 900_000L;
        ColdResult r = ColdBeamSolver.solve(load(stem), cfg, ColdBeamSolver.NO_PROGRESS, new AtomicBoolean(false));
        assertNotNull(stem + ": beam did not solve cold", r);
        assertTrue(stem + ": beam solve not byte-exact: " + r.summary(), r.solved() && r.maxViolation <= 0.0);
    }

    @Test
    public void j154SolvesColdViaBeam() {
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
        cfg.bucketBudget = 3;
        assertSolvesCold("hpk_human/d12/j154_1bm_Head_Butterfly_Neo", cfg);
    }

    @Test
    public void j925SolvesColdViaBeam() {
        ColdBeamSolver.Config cfg = new ColdBeamSolver.Config();
        cfg.alphabet = new int[] {KeyLine.NONE, KeyLine.WA, KeyLine.WD, KeyLine.W};
        cfg.maxChanges = 3;
        cfg.engageTicks = new int[] {0, 1, 2, 3};
        cfg.beamCap = 2_000_000;
        cfg.certifyCap = 500_000;
        cfg.bucketBudget = 30;
        assertSolvesCold("hpk_human/d11/j925-Sidewalled_Single_Piston_Butterfly_Neo_1bl", cfg);
    }

    @Test
    public void j1150SolvesColdViaIncremental() {
        ColdBeamSolver.Config cfg = new ColdBeamSolver.Config();
        cfg.incremental = true;
        cfg.alphabet = new int[] {KeyLine.NONE, KeyLine.SA, KeyLine.WD, KeyLine.W};
        cfg.maxChanges = 1;
        cfg.certifyCap = 500_000;
        cfg.bucketBudget = 30;
        assertSolvesCold("hpk_human/d11/j1150-2x2bm_Nix_Neo", cfg);
    }

    @Test
    public void j1150SolvesViaMitm() {
        ColdBeamSolver.Config cfg = new ColdBeamSolver.Config();
        cfg.mitm = true;
        cfg.alphabet = new int[] {KeyLine.NONE, KeyLine.W, KeyLine.WA, KeyLine.WD, KeyLine.SA, KeyLine.SD};
        cfg.maxChanges = 2;
        cfg.mitmFrontCap = 1;
        cfg.mitmBackCap = 1;
        cfg.mitmSeam = 19;
        assertSolvesCold("hpk_human/d11/j1150-2x2bm_Nix_Neo", cfg);
    }

    @Test
    public void j925SolvesViaMitm() {
        ColdBeamSolver.Config cfg = new ColdBeamSolver.Config();
        cfg.mitm = true;
        cfg.alphabet = new int[] {KeyLine.NONE, KeyLine.W, KeyLine.WA, KeyLine.WD, KeyLine.SA, KeyLine.SD};
        cfg.maxChanges = 3;
        cfg.mitmFrontCap = 2;
        cfg.mitmBackCap = 1;
        cfg.mitmSeam = 7;
        assertSolvesCold("hpk_human/d11/j925-Sidewalled_Single_Piston_Butterfly_Neo_1bl", cfg);
    }

    @Test
    public void j716SolvesColdViaBeam() {
        ColdBeamSolver.Config cfg = new ColdBeamSolver.Config();
        cfg.coasts = new int[] {KeyLine.D, KeyLine.A, KeyLine.WD};
        cfg.glides = new int[] {KeyLine.SA, KeyLine.WD};
        cfg.presses = new int[] {KeyLine.A, KeyLine.WD, KeyLine.W};
        cfg.engages = new int[] {KeyLine.W, KeyLine.WD};
        cfg.brakes = new int[] {KeyLine.NONE};
        cfg.glideMax = 13;
        cfg.cycles = new ArrayList<ColdBeamSolver.CycleConfig>();
        cfg.cycles.add(cycle(1, 2));
        cfg.cycles.add(cycle(8, 12));
        cfg.cycles.add(cycle(8, 12));
        cfg.engageTicks = new int[] {19};
        cfg.beamCap = 5_000_000;
        cfg.certifyCap = 500_000;
        cfg.bucketBudget = 30;
        assertSolvesCold("hpk_human/d11/j716-1bm_Cobblewall_to_Cobblewall_Winged_Neo", cfg);
    }
}
