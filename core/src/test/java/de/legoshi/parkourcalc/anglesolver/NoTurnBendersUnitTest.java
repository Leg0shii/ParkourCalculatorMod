package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.anglesolver.harness.Fixtures;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverEngine;
import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.anglesolver.noturn.IisExtractor;
import de.legoshi.parkourcalc.core.anglesolver.noturn.MinTvMaster;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoGoodCut;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnKeys;
import de.legoshi.parkourcalc.core.anglesolver.noturn.NoTurnProblem;
import de.legoshi.parkourcalc.core.anglesolver.noturn.StructurePoolDriver;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import de.legoshi.parkourcalc.core.ui.InputData;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NoTurnBendersUnitTest {

    private NoTurnProblem load(String capture) throws Exception {
        String raw = Fixtures.rawPool(capture);
        SaveFile file = SaveIO.parseSafe(raw);
        ExactJumpModel model = ExactJumpModel.forMcVersion(file.mcVersion);
        InputData inputs = new InputData();
        SaveIO.applyRowsTo(file, inputs);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(file, state);
        AngleSolverEngine engine = new AngleSolverEngine(state, Fixtures.buildBoxes(file), inputs, t -> { }, model);
        return NoTurnProblem.from(engine.debugBuildSpec(), model);
    }

    private boolean contains(int[] a, int v) {
        for (int x : a) if (x == v) return true;
        return false;
    }

    @Test
    public void iisExtractorFlagsJumpPhaseRegion() throws Exception {
        NoTurnProblem p = load("hpk_precise/j154-noturn-ja-inner");
        int[] anc = new int[29];
        for (int t = 0; t <= 5; t++) anc[t] = NoTurnKeys.SD;
        for (int t = 6; t <= 14; t++) anc[t] = NoTurnKeys.S;
        for (int t = 15; t <= 27; t++) anc[t] = NoTurnKeys.WA;
        anc[28] = NoTurnKeys.W;
        boolean[] spr = NoTurnKeys.latchSprint(anc, 0);

        IisExtractor iis = new IisExtractor(p, p.model, new IisExtractor.Config());
        NoGoodCut cut = iis.extract(anc, spr, null, p.refStart().x, p.refStart().z);
        System.out.println("IIS (no yaws) -> " + cut.describe());

        assertTrue("cut includes jump-center tick 15", contains(cut.ticks, 15));
        assertTrue("cut includes tick 14", contains(cut.ticks, 14));
        assertTrue("cut includes tick 16", contains(cut.ticks, 16));
        assertTrue("cut is a proper sub-assignment (smaller than full schedule)", cut.size() < 29);
        for (int i = 0; i < cut.ticks.length; i++) {
            assertEquals("cut combo matches schedule at tick " + cut.ticks[i],
                    anc[cut.ticks[i]], cut.combos[i]);
        }
    }

    @Test
    public void masterReproducesStructurePoolStreamInEdgeOrder() {
        int setupEnd = 14;
        boolean takeoffW = true;
        int minDwell = 3;
        int maxEdges = 2;
        int[] alphabet = {NoTurnKeys.W, NoTurnKeys.WA, NoTurnKeys.WD, NoTurnKeys.S};

        List<int[]> reference = StructurePoolDriver.enumerateRaw(setupEnd, takeoffW, minDwell, maxEdges, alphabet);
        MinTvMaster master = new MinTvMaster(setupEnd, takeoffW, minDwell, maxEdges, alphabet);
        List<int[]> stream = master.collectAll();

        assertEquals("master yields the same count as StructurePoolDriver raw enumeration",
                reference.size(), stream.size());

        int prevEdges = -1;
        java.util.Set<String> streamKeys = new java.util.HashSet<>();
        for (int[] s : stream) {
            int e = NoTurnKeys.countEdges(s);
            assertTrue("non-decreasing edge order (prev=" + prevEdges + " cur=" + e + ")", e >= prevEdges);
            prevEdges = e;
            streamKeys.add(key(s));
        }
        java.util.Set<String> refKeys = new java.util.HashSet<>();
        for (int[] s : reference) refKeys.add(key(s));
        assertEquals("master stream is the same set as StructurePoolDriver's", refKeys, streamKeys);

        MinTvMaster cutMaster = new MinTvMaster(setupEnd, takeoffW, minDwell, maxEdges, alphabet);
        int[] cutTicks = {0, 1, 2};
        int[] cutCombos = {NoTurnKeys.W, NoTurnKeys.W, NoTurnKeys.W};
        NoGoodCut cut = new NoGoodCut(cutTicks, cutCombos, setupEnd + 1, 1.0);
        cutMaster.addCut(cut);
        List<int[]> cutStream = cutMaster.collectAll();

        int expectedRemoved = 0;
        for (int[] s : reference) if (cut.matches(s)) expectedRemoved++;
        assertTrue("hand cut actually forbids some schedules", expectedRemoved > 0);
        assertEquals("cut removes exactly the matching sub-assignments",
                reference.size() - expectedRemoved, cutStream.size());
        for (int[] s : cutStream) {
            assertFalse("no yielded schedule matches the cut", cut.matches(s));
        }
        int prev2 = -1;
        for (int[] s : cutStream) {
            int e = NoTurnKeys.countEdges(s);
            assertTrue("cut stream still non-decreasing edge order", e >= prev2);
            prev2 = e;
        }
        System.out.println("master ordering: total=" + reference.size()
                + " afterCut=" + cutStream.size() + " removed=" + expectedRemoved);
    }

    private static String key(int[] c) {
        StringBuilder sb = new StringBuilder();
        for (int x : c) sb.append((char) ('a' + x));
        return sb.toString();
    }
}
