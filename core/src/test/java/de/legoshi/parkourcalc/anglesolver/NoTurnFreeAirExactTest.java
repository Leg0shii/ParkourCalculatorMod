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
}
