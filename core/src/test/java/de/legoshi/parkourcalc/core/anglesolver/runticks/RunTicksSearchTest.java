package de.legoshi.parkourcalc.core.anglesolver.runticks;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RunTicksSearchTest {

    private static final RunTicksSearch.JumpOptions ANY = (jumpIndex, extraTicks) -> true;

    /** Drives the search to exhaustion with every step succeeding, collecting the full-depth combos. */
    private static List<String> exploreAll(RunTicksSearch<Void> search, boolean climbLadder) {
        List<String> leaves = new ArrayList<String>();
        while (true) {
            while (!search.hasNext()) {
                if (!climbLadder || !search.nextRung()) return leaves;
            }
            RunTicksSearch.Node<Void> node = search.take();
            if (node.depth() == search.jumpCount()) leaves.add(Arrays.toString(node.combo()));
            search.recordSuccess(null);
        }
    }

    @Test
    public void enumeratesEveryCombinationUpToTheBudget() {
        RunTicksSearch<Void> search = new RunTicksSearch<Void>(2, 2, false, ANY);
        List<String> leaves = exploreAll(search, false);
        assertEquals(Arrays.asList("[0, 0]", "[0, 1]", "[0, 2]", "[1, 0]", "[1, 1]", "[2, 0]"), leaves);
        assertEquals(6, search.fullSolutions());
        assertEquals(1.0, search.progress(), 1.0e-9);
    }

    @Test
    public void zeroTicksFirstSoTheCheapestPathIsTriedFirst() {
        RunTicksSearch<Void> search = new RunTicksSearch<Void>(1, 3, false, ANY);
        assertEquals(0, search.take().combo()[0]);
    }

    @Test
    public void minimizeVisitsEachSumExactlyOnceAcrossRungs() {
        RunTicksSearch<Void> search = new RunTicksSearch<Void>(2, 0, true, ANY);
        assertEquals(0, search.target());

        assertEquals(Arrays.asList("[0, 0]"), exploreRung(search));
        assertTrue(search.nextRung());
        assertEquals(1, search.target());
        assertEquals(Arrays.asList("[0, 1]", "[1, 0]"), exploreRung(search));
        assertTrue(search.nextRung());
        assertEquals(2, search.target());
        assertEquals(Arrays.asList("[0, 2]", "[1, 1]", "[2, 0]"), exploreRung(search));
        assertTrue(search.nextRung());
        assertEquals(3, search.target());
        assertEquals(Arrays.asList("[0, 3]", "[1, 2]", "[2, 1]", "[3, 0]"), exploreRung(search));
    }

    @Test
    public void minimizeStartsFromConfiguredRunTicks() {
        RunTicksSearch<Void> search = new RunTicksSearch<Void>(2, 3, true, ANY);
        assertEquals(3, search.target());
        assertEquals(Arrays.asList("[0, 3]", "[1, 2]", "[2, 1]", "[3, 0]"), exploreRung(search));
        assertTrue(search.nextRung());
        assertEquals(4, search.target());
    }

    @Test
    public void theLadderCoversExactlyTheSweepWithoutRepeats() {
        List<String> ladder = new ArrayList<String>();
        RunTicksSearch<Void> search = new RunTicksSearch<Void>(3, 0, true, ANY);
        while (search.target() <= 3) {
            ladder.addAll(exploreRung(search));
            if (search.target() == 3 || !search.nextRung()) break;
        }
        List<String> sweep = exploreAll(new RunTicksSearch<Void>(3, 3, false, ANY), false);
        assertEquals(sweep.size(), ladder.size());
        assertEquals(new TreeSet<String>(sweep), new TreeSet<String>(ladder));
    }

    @Test
    public void aRungCostsOnlyItsOwnCombinations() {
        RunTicksSearch<Void> ladder = new RunTicksSearch<Void>(3, 0, true, ANY);
        exploreRung(ladder);
        assertEquals("the all-zero rung is one chain of solves", 3, ladder.steps());
        assertEquals(1, ladder.fullSolutions());
    }

    @Test
    public void constraintsPruneOptionsPerJump() {
        RunTicksSearch<Void> search = new RunTicksSearch<Void>(2, 2, false,
                (jumpIndex, extraTicks) -> jumpIndex != 0 || extraTicks >= 1);
        assertEquals(Arrays.asList("[1, 0]", "[1, 1]", "[2, 0]"), exploreAll(search, false));
    }

    @Test
    public void minimizeClimbsPastRungsTheConstraintsPruneAway() {
        RunTicksSearch<Void> search = new RunTicksSearch<Void>(1, 0, true,
                (jumpIndex, extraTicks) -> extraTicks >= 2);
        assertFalse("rung 0 and rung 1 are both impossible", search.hasNext());
        assertTrue(search.nextRung());
        assertEquals(2, search.target());
        assertEquals(Arrays.asList("[2]"), exploreRung(search));
    }

    @Test
    public void aFullyPrunedRootReportsCompleteImmediately() {
        RunTicksSearch<Void> search = new RunTicksSearch<Void>(2, 2, false, (jumpIndex, extraTicks) -> false);
        assertFalse(search.hasNext());
        assertEquals(1.0, search.progress(), 1.0e-9);
    }

    @Test
    public void failedBranchesStillAccountForTheirShareOfProgress() {
        RunTicksSearch<Void> search = new RunTicksSearch<Void>(2, 1, false, ANY);
        while (search.hasNext()) {
            search.take();
            search.recordFailure();
        }
        assertEquals(1.0, search.progress(), 1.0e-9);
        assertEquals(0, search.successes());
        assertEquals(2, search.steps());
    }

    private static List<String> exploreRung(RunTicksSearch<Void> search) {
        List<String> leaves = new ArrayList<String>();
        while (search.hasNext()) {
            RunTicksSearch.Node<Void> node = search.take();
            if (node.depth() == search.jumpCount()) leaves.add(Arrays.toString(node.combo()));
            search.recordSuccess(null);
        }
        return leaves;
    }
}
