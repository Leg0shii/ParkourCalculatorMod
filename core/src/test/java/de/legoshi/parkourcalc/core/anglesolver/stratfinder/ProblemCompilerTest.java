package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import de.legoshi.parkourcalc.core.anglesolver.coldsearch.ColdProblem;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProblemCompilerTest {

    @Test
    public void arcDurationsMatchTheWikiTable() {
        assertEquals(12, JumpArcs.duration(0.0, true));
        assertEquals(9, JumpArcs.duration(1.0, true));
        assertEquals(10, JumpArcs.duration(0.5, true));
        assertEquals(14, JumpArcs.duration(-1.0, true));
        assertEquals(12, JumpArcs.duration(0.0, false));
        assertEquals(9, JumpArcs.duration(1.0, false));
        assertEquals(-1, JumpArcs.duration(2.0, true));
        assertTrue(JumpArcs.maxRise(true) > 1.24 && JumpArcs.maxRise(true) < 1.26);
    }

    @Test
    public void ceilingDurationsMatchTheWikiTable() {
        assertEquals(11, JumpArcs.duration(0.0, 3.0, true));
        assertEquals(6, JumpArcs.duration(0.0, 2.5, true));
        assertEquals(12, JumpArcs.duration(0.0, Double.NaN, true));
        assertEquals(-1, JumpArcs.duration(0.0, 1.8, true));
    }

    @Test
    public void arcHeightsEndOnTheLandingSurface() {
        int d = JumpArcs.duration(1.0, true);
        double[] h = JumpArcs.heights(d, 1.0, true);
        assertEquals(d + 1, h.length);
        assertEquals(1.0, h[d], 0.0);
        assertTrue(h[1] > 0.41 && h[1] < 0.43);
    }

    @Test
    public void singleFlatJumpCompilesToAColdProblem() {
        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(0, 64, 0);
        StratProblem.Segment seg = new StratProblem.Segment();
        seg.groundLo = 1;
        seg.groundHi = 3;
        seg.landings.add(StratProblem.Area.block(4, 64, 0));
        p.segments.add(seg);

        ProblemCompiler.Compilation comp = ProblemCompiler.compile(p);
        assertFalse(comp.truncated);
        assertEquals(3, comp.specs.size());

        ProblemCompiler.Compiled c = comp.specs.get(1);
        assertEquals(2, c.groundCounts[0]);
        assertEquals(1, c.fireTicks[0]);
        assertEquals(13, c.landTicks[0]);
        assertEquals(13, c.save.angleSolver.landingTick);
        assertEquals(14, c.yPerTick.length);
        assertEquals(65.0, c.yPerTick[0], 0.0);
        assertEquals(65.0, c.yPerTick[13], 0.0);
        assertTrue(c.yPerTick[5] > 65.0);

        ColdProblem cold = ColdProblem.fromSave(c.save);
        assertEquals(0, cold.startTick);
        assertEquals(13, cold.landingTick);
        assertTrue(Arrays.equals(new int[]{1}, cold.pressSegTicks));
        assertTrue(cold.lastPressYawTied);
        assertTrue(cold.singleHeld);
        assertFalse(cold.tailYawsFree);
        assertEquals(-StratProblem.HALF_WIDTH, cold.rectXLo, 1e-12);
        assertEquals(1.0 + StratProblem.HALF_WIDTH, cold.rectXHi, 1e-12);
        assertFalse(cold.momentumWalls.isEmpty());
        assertFalse(cold.tailWalls.isEmpty());
        boolean landingWall = false;
        for (ColdProblem.Wall w : cold.tailWalls) {
            if (w.segTick == 13 && w.axisX) {
                assertEquals(4.0 - StratProblem.HALF_WIDTH, w.lo, 1e-12);
                assertEquals(5.0 + StratProblem.HALF_WIDTH, w.hi, 1e-12);
                landingWall = true;
            }
        }
        assertTrue(landingWall);
    }

    @Test
    public void twoSegmentScheduleChainsTicks() {
        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(0, 64, 0);
        StratProblem.Segment s1 = new StratProblem.Segment();
        s1.groundLo = 2;
        s1.groundHi = 2;
        s1.landings.add(StratProblem.Area.block(4, 64, 0));
        StratProblem.Segment s2 = new StratProblem.Segment();
        s2.groundLo = 1;
        s2.groundHi = 1;
        s2.landings.add(StratProblem.Area.block(8, 64, 0));
        p.segments.add(s1);
        p.segments.add(s2);

        ProblemCompiler.Compilation comp = ProblemCompiler.compile(p);
        assertEquals(1, comp.specs.size());
        ProblemCompiler.Compiled c = comp.specs.get(0);
        assertTrue(Arrays.equals(new int[]{1, 13}, c.fireTicks));
        assertTrue(Arrays.equals(new int[]{13, 25}, c.landTicks));

        ColdProblem cold = ColdProblem.fromSave(c.save);
        assertTrue(Arrays.equals(new int[]{1, 13}, cold.pressSegTicks));
        assertTrue(cold.singleHeld);
    }

    @Test
    public void jaFreesTheTail() {
        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(0, 64, 0);
        StratProblem.Segment seg = new StratProblem.Segment();
        seg.groundLo = 2;
        seg.groundHi = 2;
        seg.ja = true;
        seg.landings.add(StratProblem.Area.block(4, 64, 0));
        p.segments.add(seg);

        ProblemCompiler.Compilation comp = ProblemCompiler.compile(p);
        assertEquals(1, comp.specs.size());
        ColdProblem cold = ColdProblem.fromSave(comp.specs.get(0).save);
        assertFalse(cold.lastPressYawTied);
        assertTrue(cold.tailYawsFree);
        assertFalse(cold.singleHeld);
    }

    @Test
    public void areaSlipperinessLandsOnTheGroundedTicks() {
        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(0, 64, 0);
        p.start.slipperiness = "SLIME";
        StratProblem.Segment seg = new StratProblem.Segment();
        seg.groundLo = 2;
        seg.groundHi = 2;
        seg.landings.add(StratProblem.Area.block(4, 64, 0));
        p.segments.add(seg);

        ProblemCompiler.Compiled c = ProblemCompiler.compile(p).specs.get(0);
        ColdProblem cold = ColdProblem.fromSave(c.save);
        assertEquals(0.8, cold.slip[0], 0.0);
        assertEquals(0.8, cold.slip[1], 0.0);
        assertEquals(1.0, cold.slip[2], 0.0);
        assertEquals(1.0, cold.slip[12], 0.0);
    }

    @Test
    public void unreachableRiseIsSkippedWithANote() {
        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(0, 64, 0);
        StratProblem.Segment seg = new StratProblem.Segment();
        seg.groundLo = 1;
        seg.groundHi = 1;
        seg.landings.add(StratProblem.Area.block(4, 66, 0));
        p.segments.add(seg);

        ProblemCompiler.Compilation comp = ProblemCompiler.compile(p);
        assertTrue(comp.specs.isEmpty());
        assertFalse(comp.notes.isEmpty());
    }

    @Test
    public void observedTimingOverridesPhysics() {
        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(0, 64, 0);
        StratProblem.Segment seg = new StratProblem.Segment();
        seg.groundLo = 2;
        seg.groundHi = 2;
        seg.airTicks = 11;
        seg.arcRel = new double[10];
        for (int i = 0; i < 10; i++) {
            seg.arcRel[i] = 0.5;
        }
        seg.landings.add(StratProblem.Area.block(2, 64, 0));
        p.segments.add(seg);

        ProblemCompiler.Compiled c = ProblemCompiler.compile(p).specs.get(0);
        assertTrue(Arrays.equals(new int[]{1}, c.fireTicks));
        assertTrue(Arrays.equals(new int[]{12}, c.landTicks));
        assertEquals(13, c.yPerTick.length);
        assertEquals(65.5, c.yPerTick[5], 0.0);
        assertEquals(65.0, c.yPerTick[12], 0.0);
    }

    @Test
    public void userWallsTranslateIntoEveryTiming() {
        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(0, 64, 0);
        StratProblem.Segment seg = new StratProblem.Segment();
        seg.groundLo = 1;
        seg.groundHi = 3;
        seg.airTicks = 12;
        seg.refFire = 1;
        seg.refLand = 13;
        seg.landings.add(StratProblem.Area.block(4, 64, 0));
        p.segments.add(seg);
        p.userWalls.add(new StratProblem.Wall(5, "X", "GE", 602.8));

        ProblemCompiler.Compilation comp = ProblemCompiler.compile(p);
        assertEquals(3, comp.specs.size());
        int[] expectedTick = {4, 5, 6};
        for (int i = 0; i < 3; i++) {
            ProblemCompiler.Compiled c = comp.specs.get(i);
            boolean found = false;
            for (de.legoshi.parkourcalc.core.save.SaveFile.Tick tk : c.save.angleSolver.ticks) {
                if (tk.tick != expectedTick[i]) {
                    continue;
                }
                for (de.legoshi.parkourcalc.core.save.SaveFile.Constraint sc : tk.constraints) {
                    if (!sc.range && "X".equals(sc.field) && "GE".equals(sc.op)
                            && sc.value == 602.8) {
                        found = true;
                    }
                }
            }
            assertTrue("wall missing on tick " + expectedTick[i] + " of timing " + i, found);
        }
    }

    @Test
    public void machineConstraintsAreFlaggedDerivedExceptTheLanding() {
        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(0, 64, 0);
        StratProblem.Segment seg = new StratProblem.Segment();
        seg.groundLo = 2;
        seg.groundHi = 2;
        seg.landings.add(StratProblem.Area.block(4, 64, 0));
        p.segments.add(seg);

        ProblemCompiler.Compiled c = ProblemCompiler.compile(p).specs.get(0);
        for (de.legoshi.parkourcalc.core.save.SaveFile.Tick tk : c.save.angleSolver.ticks) {
            for (de.legoshi.parkourcalc.core.save.SaveFile.Constraint sc : tk.constraints) {
                boolean landingFootprint = tk.tick == c.landTicks[0] && sc.range;
                assertEquals("tick " + tk.tick + " " + sc.field, !landingFootprint, sc.derived);
            }
        }
    }

    @Test
    public void problemCopySurvivesNaNCeiling() {
        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(0, 64, 0);
        StratProblem.Segment seg = new StratProblem.Segment();
        seg.landings.add(StratProblem.Area.block(4, 64, 0));
        p.segments.add(seg);

        StratProblem c = p.copy();
        assertTrue(Double.isNaN(c.segments.get(0).ceilingY));
        p.segments.get(0).ceilingY = 68.0;
        assertEquals(68.0, p.copy().segments.get(0).ceilingY, 0.0);
    }

    @Test
    public void invalidProblemsReportInsteadOfThrowing() {
        assertNotNull(ProblemCompiler.compile(null).notes);
        StratProblem p = new StratProblem();
        p.mcVersion = "1.8.9";
        p.start = StratProblem.Area.block(0, 64, 0);
        ProblemCompiler.Compilation comp = ProblemCompiler.compile(p);
        assertTrue(comp.specs.isEmpty());
        assertNull(null);
    }
}
