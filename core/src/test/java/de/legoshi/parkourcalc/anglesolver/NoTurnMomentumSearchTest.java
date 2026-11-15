package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.SlowSolverTests;
import de.legoshi.parkourcalc.anglesolver.harness.NoTurnCapture;
import de.legoshi.parkourcalc.core.anglesolver.graph.BuiltinGraphs;
import de.legoshi.parkourcalc.core.anglesolver.noturn.FreeAirExact;
import de.legoshi.parkourcalc.core.anglesolver.noturn.MomentumRules;
import de.legoshi.parkourcalc.core.anglesolver.noturn.MomentumSearch;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnResult;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(SlowSolverTests.class)
public class NoTurnMomentumSearchTest {

    private static final String J315 = "hpk/d9/j315_Egg_to_Block_Butterfly_Neo";
    private static final String J154 = "hpk/d12/j154_1bm_Head_Butterfly_Neo";
    private static final String J135 = "hpk/d9/j135_Waza_to_Cobblewall_-0.5";
    private static final String J344 = "hpk/d10/j344_Butterfly_Neo_to_Cobblewall_-0.5";
    private static final String J342 = "hpk/d10/j342_3bc_Trapdoor_to_Trapdoor_Cross_Neo";
    private static final String J152 = "hpk/d10/j152_Slab_to_Fence_3x3";
    private static final String J828 = "hpk/d11/j828-1bm_5.3125-1.5";
    private static final String J718 = "hpk/d11/j718-2bm__Z__Pane_to_Pane_Neo";
    private static final String J246 = "hpk/d9/j246__X__1.9375bm_Triple_Neo";
    private static final String J134 = "hpk/d9/j134_T_3mm_4jmm_4_1";
    private static final double KEEP = 0.0005;
    private static final double EDGE = 2e-5;

    private static final class Jump {
        final NoTurnProblem problem;
        final StructurePoolDriver.Config cfg = new StructurePoolDriver.Config();
        final FreeAirExact.Tables tables;
        final FreeAirExact.Screen screen;

        Jump(String capture) throws Exception {
            problem = NoTurnCapture.problem(capture);
            tables = FreeAirExact.Tables.of(problem, cfg.turnCombo, cfg.allowJa);
            screen = new FreeAirExact.Screen(tables);
        }

        double value(int[] combos, int variant) {
            boolean[] sprint = new boolean[combos.length];
            MomentumRules.sprintOf(combos, variant, sprint);
            return screen.solve(combos, sprint, KEEP).dual;
        }

        Set<String> searched(int changes, int dwell) {
            final Set<String> kept = ConcurrentHashMap.newKeySet();
            Set<String> sent = ConcurrentHashMap.newKeySet();
            List<MomentumSearch.Cut> pool = new ArrayList<>();
            for (int level = 0; level <= changes; level++) {
                for (int variant = 0; variant < MomentumRules.SPRINT_VARIANTS; variant++) {
                    MomentumSearch.Settings settings = new MomentumSearch.Settings();
                    settings.keep = KEEP;
                    settings.threads = 4;
                    settings.keptCap = Long.MAX_VALUE;
                    MomentumSearch search = new MomentumSearch(tables, new MomentumRules(problem, cfg, level, dwell), null,
                            problem.jumpTicks, variant, settings, pool, sent, () -> false,
                            (combos, v, value) -> kept.add(key(combos, v)));
                    assertEquals(MomentumSearch.Outcome.COMPLETE, search.run());
                    pool = search.pool();
                }
            }
            return kept;
        }

        void walk(MomentumRules rules, int t, int state, int[] combos, Set<String> sure, Set<String> possible) {
            if (t == problem.n) {
                for (int variant = 0; variant < MomentumRules.SPRINT_VARIANTS; variant++) {
                    double value = value(combos, variant);
                    if (value <= KEEP - EDGE) sure.add(key(combos, variant));
                    if (value <= KEEP + EDGE) possible.add(key(combos, variant));
                }
                return;
            }
            int[] key = new int[1];
            for (int combo : cfg.alphabet) {
                int next = rules.next(t, state, combo, 0, key);
                if (next < 0) continue;
                combos[t] = combo;
                walk(rules, t + 1, next, combos, sure, possible);
            }
        }

        int sameAsAFullWalk(int changes, int dwell) {
            Set<String> sure = new TreeSet<>();
            Set<String> possible = new TreeSet<>();
            MomentumRules rules = new MomentumRules(problem, cfg, changes, dwell);
            walk(rules, 0, rules.startState(), new int[problem.n], sure, possible);
            Set<String> kept = searched(changes, dwell);
            Set<String> missed = new TreeSet<>(sure);
            missed.removeAll(kept);
            Set<String> extra = new TreeSet<>(kept);
            extra.removeAll(possible);
            assertTrue("schedules under the limit the search dropped: " + missed, missed.isEmpty());
            assertTrue("schedules over the limit the search kept: " + extra, extra.isEmpty());
            System.out.println("full walk changes=" + changes + " dwell=" + dwell + ": " + sure.size() + " under the limit, "
                    + kept.size() + " kept");
            return sure.size();
        }
    }

    private static String key(int[] combos, int variant) {
        return variant + NoTurnKeys.key(combos);
    }

    private static int[] combosOf(String key) {
        int[] combos = new int[key.length() - 1];
        for (int t = 0; t < combos.length; t++) combos[t] = key.charAt(t + 1) - 'a';
        return combos;
    }

    @Test
    public void theSearchKeepsWhatAFullWalkKeepsOnAShortJump() throws Exception {
        assertTrue(new Jump(J135).sameAsAFullWalk(1, 3) > 0);
        assertTrue(new Jump(J135).sameAsAFullWalk(1, 1) > 0);
    }

    @Test
    public void theSearchKeepsWhatAFullWalkKeepsAcrossAJoin() throws Exception {
        assertTrue(new Jump(J344).sameAsAFullWalk(0, 3) > 0);
        assertTrue(new Jump(J342).sameAsAFullWalk(1, 3) > 0);
    }

    @Test
    public void theSearchKeepsWhatAFullWalkKeepsWithSeveralFacingParts() throws Exception {
        assertTrue(new Jump(J152).sameAsAFullWalk(1, 3) > 0);
        assertTrue(new Jump(J828).sameAsAFullWalk(0, 3) > 0);
        assertTrue(new Jump(J246).sameAsAFullWalk(1, 3) > 0);
        assertTrue(new Jump(J718).sameAsAFullWalk(1, 3) > 0);
    }

    @Test
    public void theSearchKeepsWhatAFullWalkKeepsWithATurnOnEveryJump() throws Exception {
        assertTrue(new Jump(J134).sameAsAFullWalk(0, 3) > 0);
    }

    @Test
    public void noNeighbourOfAKeptScheduleIsMissing() throws Exception {
        Jump jump = new Jump(J315);
        int changes = 2;
        Set<String> kept = jump.searched(changes, 3);
        assertTrue("the recorded Strat is kept", kept.contains(key(jump.problem.baseCombos(), 0))
                || kept.contains(key(jump.problem.baseCombos(), 1)) || kept.contains(key(jump.problem.baseCombos(), 2)));
        MomentumRules rules = new MomentumRules(jump.problem, jump.cfg, changes, 3);
        List<String> order = new ArrayList<>(kept);
        Collections.sort(order);
        int n = jump.problem.n;
        Set<String> tried = new TreeSet<>();
        List<String> missed = new ArrayList<>();
        int step = Math.max(1, order.size() / 150);
        for (int index = 0; index < order.size(); index += step) {
            String schedule = order.get(index);
            int variant = schedule.charAt(0) - '0';
            int[] base = combosOf(schedule);
            assertTrue(rules.accepts(base));
            assertTrue(jump.value(base, variant) <= KEEP + EDGE);
            List<int[]> neighbours = new ArrayList<>();
            for (int t = 1; t < n; t++) {
                if (base[t] == base[t - 1]) continue;
                int[] earlier = base.clone();
                earlier[t - 1] = base[t];
                neighbours.add(earlier);
                int[] later = base.clone();
                later[t] = base[t - 1];
                neighbours.add(later);
            }
            int from = 0;
            while (from < n) {
                int to = from;
                while (to + 1 < n && base[to + 1] == base[from]) to++;
                for (int combo : jump.cfg.alphabet) {
                    if (combo == base[from]) continue;
                    int[] swapped = base.clone();
                    for (int t = from; t <= to; t++) swapped[t] = combo;
                    neighbours.add(swapped);
                }
                from = to + 1;
            }
            for (int[] neighbour : neighbours) {
                String k = key(neighbour, variant);
                if (kept.contains(k) || !tried.add(k) || !rules.accepts(neighbour)) continue;
                if (jump.value(neighbour, variant) <= KEEP - EDGE) missed.add(NoTurnKeys.describe(neighbour));
            }
        }
        assertTrue("schedules under the limit next to a kept one: " + missed, missed.isEmpty());
    }

    private static NoTurnProblem cold(String capture) throws Exception {
        NoTurnCapture c = NoTurnCapture.load(capture);
        JumpSpec spec = c.buildSpec();
        JumpPhysicsInputs wiped = spec.asScenario().copy();
        wiped.forwardInputPerTick = new float[wiped.numTicks];
        wiped.strafeInputPerTick = new float[wiped.numTicks];
        wiped.sprintPerTick = new boolean[wiped.numTicks];
        wiped.strafePerTick = null;
        return NoTurnProblem.from(new JumpSpec(wiped, spec.constraints, spec.objective), c.model);
    }

    private static StructurePoolDriver.Config laneAlone() {
        StructurePoolDriver.Config cfg = new StructurePoolDriver.Config();
        cfg.momentumStage = true;
        cfg.changeLevels = false;
        cfg.certifyBudgetNanos = 0L;
        cfg.extraCertify = 0;
        cfg.totalBudgetNanos = 90_000_000_000L;
        return cfg;
    }

    @Test
    public void theLaneAloneFindsAStratWithoutTheLibrary() throws Exception {
        NoTurnProblem p = cold(J315);
        StructurePoolDriver.Config cfg = laneAlone();
        StructurePoolDriver driver = new StructurePoolDriver(p.model, cfg, new AtomicBoolean(false), (stage, frac) -> { });
        long t0 = System.nanoTime();
        NoTurnResult best = driver.run(p, BuiltinGraphs.optimize(6));
        System.out.println(J315 + " momentum lane: " + (best == null ? "nothing" : NoTurnKeys.describe(best.combos))
                + " scored=" + driver.scoredCount() + " wall=" + (System.nanoTime() - t0) / 1e9 + "s");
        assertNotNull("a Strat from the momentum lane", best);
        assertTrue("byte-exact", best.violation <= 0.0);
    }

    @Test
    public void theLaneAloneFindsAStratWithTwoFacingParts() throws Exception {
        NoTurnProblem p = cold(J152);
        StructurePoolDriver.Config cfg = laneAlone();
        StructurePoolDriver driver = new StructurePoolDriver(p.model, cfg, new AtomicBoolean(false), (stage, frac) -> { });
        long t0 = System.nanoTime();
        NoTurnResult best = driver.run(p, BuiltinGraphs.optimize(6));
        System.out.println(J152 + " momentum lane: " + (best == null ? "nothing" : NoTurnKeys.describe(best.combos))
                + " scored=" + driver.scoredCount() + " wall=" + (System.nanoTime() - t0) / 1e9 + "s");
        assertNotNull("a Strat from the momentum lane", best);
        assertTrue("byte-exact", best.violation <= 0.0);
    }

    @Test
    public void aCancelStopsTheLane() throws Exception {
        final NoTurnProblem p = cold(J154);
        StructurePoolDriver.Config cfg = laneAlone();
        final AtomicBoolean cancel = new AtomicBoolean(false);
        final StructurePoolDriver driver = new StructurePoolDriver(p.model, cfg, cancel, (stage, frac) -> { });
        Thread run = new Thread(() -> driver.run(p, BuiltinGraphs.optimize(6)));
        run.start();
        Thread.sleep(1_500L);
        cancel.set(true);
        long t0 = System.nanoTime();
        run.join(20_000L);
        assertTrue("the run ends after a cancel", !run.isAlive());
        assertTrue("within two seconds, took " + (System.nanoTime() - t0) / 1e9, System.nanoTime() - t0 < 2_000_000_000L);
    }
}
