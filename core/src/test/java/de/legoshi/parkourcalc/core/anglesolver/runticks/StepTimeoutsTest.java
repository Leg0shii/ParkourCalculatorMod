package de.legoshi.parkourcalc.core.anglesolver.runticks;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StepTimeoutsTest {

    private static RunTicksSettings adaptive() {
        RunTicksSettings cfg = new RunTicksSettings();
        cfg.setAdaptiveTimeout(true);
        cfg.setAddPerJumpMs(50);
        cfg.setSafetyMult(2.0);
        cfg.setSafetyMarginMs(0);
        return cfg;
    }

    @Test
    public void fixedTimeoutIgnoresMeasurements() {
        RunTicksSettings cfg = new RunTicksSettings();
        cfg.setTimeoutMs(400);
        StepTimeouts timeouts = new StepTimeouts(cfg, 3);
        timeouts.recordSuccess(1, 10);
        assertEquals(400, timeouts.forDepth(1));
        assertEquals(400, timeouts.forDepth(3));
    }

    @Test
    public void firstDepthStartsAtTheBaseBudget() {
        StepTimeouts timeouts = new StepTimeouts(adaptive(), 3);
        assertEquals(RunTicksSettings.DEFAULT_TIMEOUT_MS, timeouts.forDepth(1));
    }

    @Test
    public void unmeasuredDepthsGrowFromTheDeepestMeasuredOne() {
        StepTimeouts timeouts = new StepTimeouts(adaptive(), 3);
        timeouts.recordSuccess(1, 100);
        assertEquals(200, timeouts.forDepth(1));
        assertEquals(250, timeouts.forDepth(2));
        assertEquals(300, timeouts.forDepth(3));
    }

    @Test
    public void aMeasuredDepthIsNotOverwrittenByItsPredecessor() {
        StepTimeouts timeouts = new StepTimeouts(adaptive(), 3);
        timeouts.recordSuccess(2, 500);
        assertEquals(1000, timeouts.forDepth(2));
        timeouts.recordSuccess(1, 100);
        assertEquals(1000, timeouts.forDepth(2));
    }

    @Test
    public void shortSolvesStillGetAFloor() {
        StepTimeouts timeouts = new StepTimeouts(adaptive(), 2);
        timeouts.recordSuccess(1, 1);
        assertEquals(RunTicksSettings.MIN_STEP_TIMEOUT_MS, timeouts.forDepth(1));
    }

    @Test
    public void depthIsClampedIntoTheTable() {
        StepTimeouts timeouts = new StepTimeouts(adaptive(), 2);
        assertEquals(timeouts.forDepth(2), timeouts.forDepth(9));
        assertEquals(timeouts.forDepth(1), timeouts.forDepth(0));
    }
}
