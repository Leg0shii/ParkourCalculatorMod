package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProblemDeriverTest {

    private static StratProblem.Area start() {
        return new StratProblem.Area(601.0, 602.0, 6.0, 7.0, 416.0, 417.0, "601,6,416");
    }

    private static ProblemDeriver.Footprint fp(int tick, double xLo, double xHi, double zLo,
                                               double zHi, Double surfaceY) {
        return new ProblemDeriver.Footprint(tick, xLo, xHi, zLo, zHi, surfaceY);
    }

    @Test
    public void bfNeoShapeDerivesTwoSegments() {
        List<ProblemDeriver.Footprint> fps = new ArrayList<ProblemDeriver.Footprint>();
        fps.add(fp(12, 600.7, 602.3, 415.7, 417.3, 7.0));
        fps.add(fp(25, 600.7, 602.3, 413.7, 414.7, 7.0));
        ProblemDeriver.Result r = ProblemDeriver.derive(new int[]{0, 13}, fps, start(),
                null, null, "1.8.9", null);
        assertNull(r.error);
        assertEquals(2, r.problem.segments.size());
        StratProblem.Segment s1 = r.problem.segments.get(0);
        assertEquals(1, s1.groundLo);
        assertEquals(2, s1.groundHi);
        assertEquals(12, s1.airTicks);
        StratProblem.Segment s2 = r.problem.segments.get(1);
        assertEquals(1, s2.groundLo);
        assertEquals(3, s2.groundHi);
        assertEquals(12, s2.airTicks);
        StratProblem.Area land1 = s1.landings.get(0);
        assertEquals(601.0, land1.xLo, 1e-6);
        assertEquals(602.0, land1.xHi, 1e-6);
        assertEquals(7.0, land1.top(), 0.0);
        assertEquals("T12", land1.label);
        StratProblem.Area land2 = s2.landings.get(0);
        assertEquals(414.0, land2.zLo, 1e-6);
        assertEquals("T25", land2.label);
        ProblemCompiler.Compilation comp = ProblemCompiler.compile(r.problem);
        assertTrue(comp.specs.size() > 0);
    }

    @Test
    public void markedTickIsTheTimingAuthority() {
        List<ProblemDeriver.Footprint> fps = new ArrayList<ProblemDeriver.Footprint>();
        fps.add(fp(11, 600.7, 602.3, 415.7, 417.3, 7.0));
        ProblemDeriver.Result r = ProblemDeriver.derive(new int[]{0}, fps, start(),
                null, null, "1.8.9", null);
        assertNull(r.error);
        assertEquals(11, r.problem.segments.get(0).airTicks);
    }

    @Test
    public void observedArcIsCapturedFromTheRecording() {
        List<ProblemDeriver.Footprint> fps = new ArrayList<ProblemDeriver.Footprint>();
        fps.add(fp(12, 600.7, 602.3, 415.7, 417.3, 7.0));
        double[] recordedY = new double[26];
        Arrays.fill(recordedY, 7.0);
        for (int i = 1; i < 12; i++) {
            recordedY[i] = 7.0 + 0.1 * i;
        }
        ProblemDeriver.Result r = ProblemDeriver.derive(new int[]{0}, fps, start(),
                recordedY, null, "1.8.9", null);
        assertNull(r.error);
        double[] arc = r.problem.segments.get(0).arcRel;
        assertNotNull(arc);
        assertEquals(11, arc.length);
        assertEquals(0.1, arc[0], 1e-9);
        assertEquals(1.1, arc[10], 1e-9);
    }

    @Test
    public void earlyLandingOnTheWrongRouteDiscardsTheObservedArc() {
        List<ProblemDeriver.Footprint> fps = new ArrayList<ProblemDeriver.Footprint>();
        fps.add(fp(12, 600.7, 602.3, 415.7, 417.3, 7.0));
        double[] recordedY = new double[26];
        Arrays.fill(recordedY, 7.0);
        for (int i = 1; i < 12; i++) {
            recordedY[i] = 7.0 + 0.1 * i;
        }
        boolean[] grounded = new boolean[26];
        grounded[0] = true;
        grounded[8] = true;
        ProblemDeriver.Result r = ProblemDeriver.derive(new int[]{0}, fps, start(),
                recordedY, grounded, "1.8.9", null);
        assertNull(r.error);
        assertNull(r.problem.segments.get(0).arcRel);
        assertEquals(12, r.problem.segments.get(0).airTicks);
    }

    @Test
    public void missingLandingConstraintNamesTheJump() {
        List<ProblemDeriver.Footprint> fps = new ArrayList<ProblemDeriver.Footprint>();
        fps.add(fp(12, 600.7, 602.3, 415.7, 417.3, 7.0));
        ProblemDeriver.Result r = ProblemDeriver.derive(new int[]{0, 13}, fps, start(),
                null, null, "1.8.9", null);
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("Jump 2"));
    }

    @Test
    public void footprintOfALaterJumpIsNotStolen() {
        List<ProblemDeriver.Footprint> fps = new ArrayList<ProblemDeriver.Footprint>();
        fps.add(fp(25, 600.7, 602.3, 413.7, 414.7, 7.0));
        ProblemDeriver.Result r = ProblemDeriver.derive(new int[]{0, 13}, fps, start(),
                null, null, "1.8.9", null);
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("Jump 1"));
    }

    @Test
    public void recordedHeightIsTheFallbackOnlyWhenGroundedThere() {
        List<ProblemDeriver.Footprint> fps = new ArrayList<ProblemDeriver.Footprint>();
        fps.add(fp(12, 600.7, 602.3, 415.7, 417.3, null));
        double[] recordedY = new double[26];
        Arrays.fill(recordedY, 7.0);
        boolean[] grounded = new boolean[26];
        grounded[12] = true;
        ProblemDeriver.Result r = ProblemDeriver.derive(new int[]{0}, fps, start(),
                recordedY, grounded, "1.8.9", null);
        assertNull(r.error);
        assertEquals(7.0, r.problem.segments.get(0).landings.get(0).top(), 0.0);

        ProblemDeriver.Result airborne = ProblemDeriver.derive(new int[]{0}, fps, start(),
                recordedY, new boolean[26], "1.8.9", null);
        assertNotNull(airborne.error);
        assertTrue(airborne.error, airborne.error.contains("not standing"));
    }

    @Test
    public void pairingPrefersTheGroundedFootprintOverEarlierMidAirOnes() {
        List<ProblemDeriver.Footprint> fps = new ArrayList<ProblemDeriver.Footprint>();
        fps.add(fp(4, 605.7, 607.3, 415.7, 417.3, null));
        fps.add(fp(14, 605.7, 607.3, 411.7, 413.7, null));
        fps.add(fp(15, 605.7, 607.3, 411.7, 413.3, null));
        double[] recordedY = new double[16];
        Arrays.fill(recordedY, 7.0);
        recordedY[4] = 7.42;
        recordedY[14] = 7.2;
        boolean[] grounded = new boolean[16];
        for (int t = 0; t <= 3; t++) {
            grounded[t] = true;
        }
        grounded[15] = true;
        ProblemDeriver.Result r = ProblemDeriver.derive(new int[]{3}, fps, start(),
                recordedY, grounded, "1.8.9", null);
        assertNull(r.error);
        StratProblem.Segment seg = r.problem.segments.get(0);
        assertEquals("T15", seg.landings.get(0).label);
        assertEquals(12, seg.airTicks);
        assertEquals(7.0, seg.landings.get(0).top(), 0.0);
    }

    @Test
    public void unknownHeightFailsLoud() {
        List<ProblemDeriver.Footprint> fps = new ArrayList<ProblemDeriver.Footprint>();
        fps.add(fp(12, 600.7, 602.3, 415.7, 417.3, null));
        ProblemDeriver.Result r = ProblemDeriver.derive(new int[]{0}, fps, start(),
                null, null, "1.8.9", null);
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("height"));
    }

    @Test
    public void knobsSurviveTheResync() {
        List<ProblemDeriver.Footprint> fps = new ArrayList<ProblemDeriver.Footprint>();
        fps.add(fp(12, 600.7, 602.3, 415.7, 417.3, 7.0));
        StratProblem.Segment prev = new StratProblem.Segment();
        prev.ja = true;
        prev.maxChanges = 3;
        prev.alphabet = new int[]{1, 2};
        ProblemDeriver.Result r = ProblemDeriver.derive(new int[]{0}, fps, start(),
                null, null, "1.8.9", java.util.Collections.singletonList(prev));
        assertNull(r.error);
        StratProblem.Segment seg = r.problem.segments.get(0);
        assertTrue(seg.ja);
        assertEquals(3, seg.maxChanges);
        assertTrue(Arrays.equals(new int[]{1, 2}, seg.alphabet));
    }
}
