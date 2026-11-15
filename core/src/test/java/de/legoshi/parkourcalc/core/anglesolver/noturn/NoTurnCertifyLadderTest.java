package de.legoshi.parkourcalc.core.anglesolver.noturn;

import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver.Candidate;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NoTurnCertifyLadderTest {

    private static final long FLASH = 16_000_000L;
    private static final long SHORT = 100_000_000L;
    private static final long RETRY = 500_000_000L;
    private static final int TICKS = 8;

    private final StructurePoolDriver.Config cfg = new StructurePoolDriver.Config();
    private final List<Candidate> change = new ArrayList<>();
    private final List<Candidate> family = new ArrayList<>();
    private final List<Candidate> value = new ArrayList<>();
    private final List<Candidate> momentum = new ArrayList<>();
    private final CertifyLadder ladder = new CertifyLadder(cfg, new long[]{FLASH, SHORT, RETRY}, new double[]{4.0, 2.0, 1.0},
            true, change, family, value, momentum);
    private final Set<String> closed = new HashSet<>();

    private static int[] schedule(int id) {
        int[] combos = new int[TICKS];
        int rest = id;
        for (int t = 0; t < TICKS; t++) {
            combos[t] = rest % NoTurnKeys.COUNT;
            rest /= NoTurnKeys.COUNT;
        }
        return combos;
    }

    private static Candidate candidate(int id, double viol, int cost, boolean family, boolean momentum) {
        Candidate c = new Candidate(schedule(id), new boolean[TICKS], 0, 0, 0, 0.0, 0.0, viol);
        c.family = family;
        c.momentum = momentum;
        c.familyCost = cost;
        return c;
    }

    private CertifyLadder.Ticket next() {
        ladder.sort();
        return ladder.next(closed::contains, true);
    }

    @Test
    public void aMissClimbsEveryPassAndThenLeavesTheLadder() {
        ladder.offer(candidate(1, 0.0, 10, true, false));
        long[] budgets = {FLASH, SHORT, RETRY};
        for (int pass = 0; pass < budgets.length; pass++) {
            CertifyLadder.Ticket t = next();
            assertNotNull("pass " + pass, t);
            assertEquals(pass, t.pass);
            assertEquals(budgets[pass], t.budget);
            assertEquals(pass + 1 < budgets.length, ladder.missed(t));
        }
        assertNull(next());
    }

    @Test
    public void aFloodFromTheMomentumLaneDoesNotHoldBackAFamilyCandidate() {
        for (int i = 0; i < 2000; i++) ladder.offer(candidate(i, 0.0, 6, true, true));
        for (int i = 0; i < 40; i++) ladder.missed(next());
        Candidate late = candidate(5000, 0.0004, 60, true, false);
        ladder.offer(late);
        int waited = 0;
        while (true) {
            CertifyLadder.Ticket t = next();
            assertNotNull(t);
            if (t.candidate == late) break;
            ladder.missed(t);
            waited++;
            assertTrue("the family candidate waits behind " + waited + " momentum candidates", waited <= CertifyLadder.SOURCES);
        }
    }

    @Test
    public void aBetterScreenValueOvertakesInTheLaterPasses() {
        for (int i = 0; i < 300; i++) ladder.offer(candidate(i, 0.005, 20, true, false));
        for (int i = 0; i < 200; i++) ladder.missed(next());
        Candidate late = candidate(5000, 0.0, 60, true, false);
        ladder.offer(late);
        int shortPassesBefore = 0;
        boolean flashed = false;
        while (true) {
            CertifyLadder.Ticket t = next();
            assertNotNull(t);
            if (t.candidate == late && t.pass == 1) break;
            if (t.candidate == late) flashed = true;
            else if (flashed && t.pass == 1) shortPassesBefore++;
            ladder.missed(t);
        }
        assertTrue("short passes of worse candidates first: " + shortPassesBefore, shortPassesBefore <= CertifyLadder.SOURCES);
    }

    @Test
    public void theFirstPassKeepsMostOfTheCertifyTime() {
        for (int i = 0; i < 20000; i++) ladder.offer(candidate(i, 0.001 + i * 1e-7, 20, true, false));
        long[] spent = new long[3];
        for (int i = 0; i < 6000; i++) {
            CertifyLadder.Ticket t = next();
            spent[t.pass] += t.budget;
            ladder.missed(t);
        }
        double total = spent[0] + spent[1] + spent[2];
        assertEquals(4.0 / 7.0, spent[0] / total, 0.03);
        assertEquals(2.0 / 7.0, spent[1] / total, 0.03);
        assertEquals(1.0 / 7.0, spent[2] / total, 0.03);
    }

    @Test
    public void aSecondCopyOfAScheduleDoesNotStartTheLadderAgain() {
        ladder.offer(candidate(7, 0.0, 60, true, false));
        CertifyLadder.Ticket first = next();
        assertEquals(0, first.pass);
        assertTrue(ladder.missed(first));
        ladder.offer(candidate(7, 0.0, 24, true, true));
        int passes = 1;
        CertifyLadder.Ticket t;
        while ((t = next()) != null) {
            assertEquals(passes, t.pass);
            passes++;
            ladder.missed(t);
        }
        assertEquals(3, passes);
    }

    @Test
    public void aScheduleInFlightOrDoneIsNotHandedOut() {
        Candidate c = candidate(9, 0.0, 10, true, false);
        ladder.offer(c);
        closed.add(PoolCertifier.scheduleKey(c.combos, c.engage, false));
        assertNull(next());
    }

    @Test
    public void theChangeLevelsWaitWhileTheirTurnsAreUsedUp() {
        ladder.offer(candidate(3, 0.0, 0, false, false));
        ladder.sort();
        assertNull(ladder.next(closed::contains, false));
        CertifyLadder.Ticket t = ladder.next(closed::contains, true);
        assertNotNull(t);
        assertEquals(CertifyLadder.SOURCE_CHANGE, t.source);
    }

    @Test
    public void aRepairCandidateKeepsItsOwnFirstBudget() {
        cfg.repairFlashNanos = 5_000_000L;
        Candidate c = candidate(4, 0.0, 0, false, false);
        c.flash = true;
        ladder.offer(c);
        CertifyLadder.Ticket t = next();
        assertEquals(5_000_000L, t.budget);
        assertTrue(ladder.missed(t));
        assertEquals(SHORT, next().budget);
    }

    @Test
    public void whatIsLeftGoesBackToTheBatchQueues() {
        List<Candidate> all = new ArrayList<>();
        for (int i = 0; i < 50; i++) all.add(candidate(i, 0.001 * (i % 7), 10 + i % 3, true, false));
        for (int i = 50; i < 60; i++) all.add(candidate(i, 0.0, 6, true, true));
        for (Candidate c : all) ladder.offer(c);
        java.util.Map<Candidate, Integer> misses = new java.util.IdentityHashMap<>();
        for (int i = 0; i < 45; i++) {
            CertifyLadder.Ticket t = next();
            misses.merge(t.candidate, 1, Integer::sum);
            ladder.missed(t);
        }
        List<Candidate> retry = new ArrayList<>();
        ladder.settle(SHORT, change, family, retry);
        assertTrue(momentum.isEmpty());
        int waitingForRetry = 0;
        for (Candidate c : all) {
            int done = misses.getOrDefault(c, 0);
            if (done == 2) waitingForRetry++;
            assertEquals("a candidate with " + done + " passes, in the retry queue", done == 2, retry.contains(c));
            if (done < 2) assertTrue("a candidate with " + done + " passes, in the family queue", family.contains(c));
        }
        assertEquals(waitingForRetry, retry.size());
        assertTrue("some candidate had reached the last pass", waitingForRetry > 0);
        assertEquals("no candidate twice", new java.util.HashSet<>(family).size(), family.size());
    }
}
