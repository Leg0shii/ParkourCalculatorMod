package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.anglesolver.harness.NoTurnCapture;
import de.legoshi.parkourcalc.core.anglesolver.noturn.FreeAirExact;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NoTurnFreeAirExactTest {

    private static final double EXACT = 0.002;

    private static double violation(NoTurnProblem p, int[] combos, boolean[] sprint, StructurePoolDriver.Config cfg) {
        return FreeAirExact.solveWithInertia(p.buildSpec(combos, sprint, cfg.turnCombo, cfg.allowJa),
                p.model.inertiaThreshold(), p.model.perAxisInertia(), cfg.familyKeep, cfg.familyReach, 3).dual;
    }

    @Test
    public void humanStratsPassAndStandingStillFails() throws Exception {
        StructurePoolDriver.Config cfg = new StructurePoolDriver.Config();
        File root = new File(Fixtures.class.getResource("/captures/hpk").toURI());
        List<String> notExact = new ArrayList<>();
        List<String> dropped = new ArrayList<>();
        int total = 0;
        File[] folders = root.listFiles();
        Arrays.sort(folders);
        for (File folder : folders) {
            File[] files = folder.listFiles();
            Arrays.sort(files);
            for (File file : files) {
                String name = file.getName().replace(".json", "");
                NoTurnProblem p = NoTurnCapture.problem("hpk/" + folder.getName() + "/" + name);
                double human = violation(p, p.baseCombos(), p.baseSprint(), cfg);
                double still = violation(p, new int[p.n], new boolean[p.n], cfg);
                total++;
                if (human > EXACT) notExact.add(name + " " + human);
                if (human > cfg.familyKeep) dropped.add(name + " " + human);
                assertTrue(name + " standing still scores " + still, still > cfg.familyKeep);
            }
        }
        assertEquals(59, total);
        assertTrue("human Strats outside the exact tier: " + notExact, notExact.size() <= 3);
        assertTrue("human Strats the screen drops: " + dropped, dropped.size() <= 1);
    }

    @Test
    public void theTableScreenScoresLikeTheModelBuiltPerSchedule() throws Exception {
        StructurePoolDriver.Config cfg = new StructurePoolDriver.Config();
        String[] captures = {"hpk/d11/j716-1bm_Cobblewall_to_Cobblewall_Winged_Neo", "hpk/d12/j154_1bm_Head_Butterfly_Neo",
                "hpk/d10/j424_1.4375bm_Double_Winged_Neo__0.25", "hpk/d11/j1099-1.1875bm_Head_Butterfly_Neo_fail"};
        java.util.Random random = new java.util.Random(671L);
        for (String capture : captures) {
            NoTurnProblem p = NoTurnCapture.problem(capture);
            FreeAirExact.Tables tables = FreeAirExact.Tables.of(p, cfg.turnCombo, cfg.allowJa);
            assertTrue(capture + " has no tables", tables != null);
            for (int trial = 0; trial < 60; trial++) {
                int[] combos = new int[p.n];
                boolean[] sprint = new boolean[p.n];
                int t = 0;
                while (t < p.n) {
                    int combo = random.nextInt(NoTurnKeys.COUNT);
                    boolean run = random.nextBoolean();
                    int end = Math.min(p.n, t + 1 + random.nextInt(12));
                    for (; t < end; t++) {
                        combos[t] = combo;
                        sprint[t] = run && random.nextInt(8) != 0;
                    }
                }
                double perSchedule = FreeAirExact.solve(p.buildSpec(combos, sprint, cfg.turnCombo, cfg.allowJa)).dual;
                double fromTables = new FreeAirExact.Screen(tables).solve(combos, sprint, Double.POSITIVE_INFINITY).dual;
                assertEquals(capture + " " + NoTurnKeys.describe(combos), Double.doubleToLongBits(perSchedule),
                        Double.doubleToLongBits(fromTables));
            }
        }
    }

    @Test
    public void theScoreIsTheSameOnEveryCall() throws Exception {
        StructurePoolDriver.Config cfg = new StructurePoolDriver.Config();
        NoTurnProblem p = NoTurnCapture.problem("hpk/d11/j716-1bm_Cobblewall_to_Cobblewall_Winged_Neo");
        int[] combos = p.baseCombos();
        boolean[] sprint = NoTurnKeys.latchSprint(combos, 0);
        double first = violation(p, combos, sprint, cfg);
        double second = violation(p, combos, sprint, cfg);
        assertEquals(Double.doubleToLongBits(first), Double.doubleToLongBits(second));
    }

    @Test
    public void storedRejectsOnlyDropSchedulesTheFullSolveDrops() throws Exception {
        StructurePoolDriver.Config cfg = new StructurePoolDriver.Config();
        String[] captures = {"hpk/d12/j154_1bm_Head_Butterfly_Neo", "hpk/d10/j424_1.4375bm_Double_Winged_Neo__0.25"};
        java.util.Random random = new java.util.Random(680L);
        for (String capture : captures) {
            NoTurnProblem p = NoTurnCapture.problem(capture);
            FreeAirExact.Tables tables = FreeAirExact.Tables.of(p, cfg.turnCombo, cfg.allowJa);
            FreeAirExact.Screen pooled = new FreeAirExact.Screen(tables, 32);
            FreeAirExact.Screen full = new FreeAirExact.Screen(tables);
            int[] starts = {0, p.n / 3, 2 * p.n / 3};
            int[] ends = {starts[1], starts[2], p.n};
            int[] options = {6, 6, 6};
            int[][][] keys = new int[3][6][];
            int[] human = p.baseCombos();
            for (int segment = 0; segment < 3; segment++) {
                for (int option = 0; option < 6; option++) {
                    int[] block = java.util.Arrays.copyOfRange(human, starts[segment], ends[segment]);
                    int t = option == 0 ? block.length : random.nextInt(block.length);
                    while (t < block.length) {
                        int combo = random.nextInt(NoTurnKeys.COUNT);
                        int end = Math.min(block.length, t + 1 + random.nextInt(6));
                        for (; t < end; t++) block[t] = combo;
                    }
                    keys[segment][option] = block;
                }
            }
            int[] combos = new int[p.n];
            boolean[] sprint = new boolean[p.n];
            boolean[] variant = new boolean[p.n];
            for (int trial = 0; trial < 150; trial++) {
                for (int segment = 0; segment < 3; segment++) {
                    System.arraycopy(keys[segment][random.nextInt(6)], 0, combos, starts[segment], ends[segment] - starts[segment]);
                }
                for (int t = 0; t < p.n; t++) sprint[t] = NoTurnKeys.isRun(combos[t]);
                FreeAirExact.Result fast = pooled.solve(combos, sprint, cfg.familyKeep);
                if (fast.dual > cfg.familyKeep) {
                    double reached = full.solve(combos, sprint, Double.POSITIVE_INFINITY).primal;
                    assertTrue(capture + " " + NoTurnKeys.describe(combos) + " dropped at " + fast.dual + ", reaches "
                            + reached, reached >= fast.dual - 1.0e-9);
                }
            }
            assertTrue(capture + " never used a stored reject", pooled.poolRejects() > 0);
            long dropped = 0;
            for (int a = 0; a < 6; a++) {
                for (int b = 0; b < 6; b++) {
                    for (int c = 0; c < 6; c++) {
                        int[] pick = {a, b, c};
                        pooled.layout(starts, options);
                        for (int segment = 0; segment < 3; segment++) {
                            System.arraycopy(keys[segment][pick[segment]], 0, combos, starts[segment],
                                    ends[segment] - starts[segment]);
                            pooled.choose(segment, pick[segment]);
                        }
                        int firstRun = -1;
                        for (int t = 0; t < p.n; t++) {
                            sprint[t] = NoTurnKeys.isRun(combos[t]);
                            if (sprint[t] && firstRun < 0) firstRun = t;
                        }
                        int stored = pooled.survivors(combos, sprint, firstRun, cfg.familyKeep);
                        pooled.layout(starts, options);
                        int direct = pooled.survivors(combos, sprint, firstRun, cfg.familyKeep);
                        String label = capture + " " + NoTurnKeys.describe(combos);
                        assertEquals(label, direct, stored);
                        for (int kind = 0; kind <= 2; kind++) {
                            if (firstRun < 0 && kind > 0) break;
                            if (kind > 0 && firstRun + 2 >= p.n && kind == 2) break;
                            if ((stored & (1 << kind)) != 0) continue;
                            dropped++;
                            for (int t = 0; t < p.n; t++) variant[t] = sprint[t] && t >= firstRun + kind;
                            double reached = full.solve(combos, kind == 0 ? sprint : variant, Double.POSITIVE_INFINITY).primal;
                            assertTrue(label + " sprint variant " + kind + " dropped, reaches " + reached,
                                    reached > cfg.familyKeep);
                        }
                    }
                }
            }
            assertTrue(capture + " dropped nothing", dropped > 0);
        }
    }
}
