package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RunMatrixSweepTest {

    @Test
    public void taserSweepGeneratesLambdaGrid() {
        List<RunMatrixScreen.Preset> ps = RunMatrixScreen.sweepPresets("taser60:l=1e-5,1e-4,1e-3");
        assertEquals(3, ps.size());
        assertEquals("taser60-l1e-5", ps.get(0).id);
        assertEquals("taser60-l1e-4", ps.get(1).id);
        assertEquals("taser60-l1e-3", ps.get(2).id);
        for (RunMatrixScreen.Preset p : ps) {
            assertNull(p.alm);
            assertNotNull(p.apply);
        }
        AngleSolverState state = new AngleSolverState();
        ps.get(1).apply.accept(state);
        assertEquals(AngleSolverState.Effort.THOROUGH, state.getEffort());
        assertEquals(60, state.getOptimizeSeconds());
        assertEquals(1.0e-4, state.getSmoothLambda(), 0.0);
    }

    @Test
    public void almSweepCrossProduct() {
        List<RunMatrixScreen.Preset> ps = RunMatrixScreen.sweepPresets("alm60:l=1e-3;seeds=16,32;cooking=0");
        assertEquals(2, ps.size());
        assertEquals("alm60-l1e-3-seeds16-cooking0", ps.get(0).id);
        assertEquals("alm60-l1e-3-seeds32-cooking0", ps.get(1).id);
        for (RunMatrixScreen.Preset p : ps) {
            assertNotNull(p.alm);
            assertNull(p.apply);
            assertEquals(60, p.alm.budgetSec);
            assertEquals(1.0e-3, p.alm.lambda, 0.0);
            assertFalse(p.alm.cooking);
            assertEquals(32, p.alm.topK);
            assertEquals(1.0, p.alm.gateWiden, 0.0);
        }
        assertEquals(16, ps.get(0).alm.seeds);
        assertEquals(32, ps.get(1).alm.seeds);
    }

    @Test
    public void bareAlmBaseUsesDefaults() {
        List<RunMatrixScreen.Preset> ps = RunMatrixScreen.sweepPresets("alm45");
        assertEquals(1, ps.size());
        assertEquals("alm45", ps.get(0).id);
        assertEquals(45, ps.get(0).alm.budgetSec);
        assertEquals(0.0, ps.get(0).alm.lambda, 0.0);
        assertEquals(16, ps.get(0).alm.seeds);
        assertTrue(ps.get(0).alm.cooking);
    }

    @Test
    public void staticPresetReuseAndMixedSpec() {
        List<RunMatrixScreen.Preset> ps = RunMatrixScreen.sweepPresets("fast|taser60-l1e3|alm60:l=1e-3");
        assertEquals(3, ps.size());
        assertEquals("fast", ps.get(0).id);
        assertEquals("taser60-l1e3", ps.get(1).id);
        assertEquals("alm60-l1e-3", ps.get(2).id);
        assertNull(ps.get(0).alm);
        assertNull(ps.get(1).alm);
        assertNotNull(ps.get(2).alm);
    }

    @Test
    public void unknownBaseAndParamsThrow() {
        try {
            RunMatrixScreen.sweepPresets("warp60:l=1");
            fail("unknown base accepted");
        } catch (IllegalArgumentException expected) {
        }
        try {
            RunMatrixScreen.sweepPresets("taser60:seeds=4");
            fail("unknown taser param accepted");
        } catch (IllegalArgumentException expected) {
        }
        try {
            RunMatrixScreen.sweepPresets("alm60:bogus=1");
            fail("unknown alm param accepted");
        } catch (IllegalArgumentException expected) {
        }
    }
}
