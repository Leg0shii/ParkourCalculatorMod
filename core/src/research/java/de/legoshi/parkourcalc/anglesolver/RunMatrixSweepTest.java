package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
            assertNotNull(p.apply);
        }
        AngleSolverState state = new AngleSolverState();
        ps.get(1).apply.accept(state);
        assertEquals(AngleSolverState.Effort.THOROUGH, state.getEffort());
        assertEquals(60, state.getOptimizeSeconds());
        assertEquals(1.0e-4, state.getSmoothLambda(), 0.0);
    }

    @Test
    public void staticPresetReuseAndMixedSpec() {
        List<RunMatrixScreen.Preset> ps = RunMatrixScreen.sweepPresets("fast|taser60-l1e3|taser60:l=1e-4");
        assertEquals(3, ps.size());
        assertEquals("fast", ps.get(0).id);
        assertEquals("taser60-l1e3", ps.get(1).id);
        assertEquals("taser60-l1e-4", ps.get(2).id);
        for (RunMatrixScreen.Preset p : ps) {
            assertNotNull(p.apply);
        }
    }

    @Test
    public void captaserSweepRaisesRouterCaps() {
        List<RunMatrixScreen.Preset> ps = RunMatrixScreen.sweepPresets("captaser60:l=1e-3;cap=128,256");
        assertEquals(2, ps.size());
        assertEquals("captaser60-l1e-3-cap128", ps.get(0).id);
        assertEquals("captaser60-l1e-3-cap256", ps.get(1).id);
        AngleSolverState state = new AngleSolverState();
        ps.get(0).apply.accept(state);
        assertEquals(AngleSolverState.Effort.CUSTOM, state.getEffort());
        assertNotNull(state.getCustomGraph());
        assertEquals(1.0e-3, state.getSmoothLambda(), 0.0);
    }

    @Test
    public void fastcapSweepParses() {
        List<RunMatrixScreen.Preset> ps = RunMatrixScreen.sweepPresets("fastcap:cap=256");
        assertEquals(1, ps.size());
        assertEquals("fastcap-cap256", ps.get(0).id);
        AngleSolverState state = new AngleSolverState();
        ps.get(0).apply.accept(state);
        assertEquals(AngleSolverState.Effort.CUSTOM, state.getEffort());
        assertNotNull(state.getCustomGraph());
        assertEquals(0.0, state.getSmoothLambda(), 0.0);
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
            RunMatrixScreen.sweepPresets("alm60:l=1e-3");
            fail("retired alm base accepted");
        } catch (IllegalArgumentException expected) {
        }
    }
}
