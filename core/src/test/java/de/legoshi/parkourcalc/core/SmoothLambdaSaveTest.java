package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SmoothLambdaSaveTest {

    @Test
    public void absentLambdaLoadsAsZero() {
        SaveFile f = SaveIO.parseSafe("{\"version\":1,\"angleSolver\":{}}");
        assertNotNull(f);
        AngleSolverState state = new AngleSolverState();
        state.setSmoothLambda(AngleSolverState.TASER_SMOOTH_LAMBDA);
        SaveIO.applyAngleSolverTo(f, state);
        assertEquals(0.0, state.getSmoothLambda(), 0.0);
    }

    @Test
    public void savedLambdaLoads() {
        SaveFile f = SaveIO.parseSafe(
                "{\"version\":1,\"angleSolver\":{\"smoothLambda\":" + AngleSolverState.TASER_SMOOTH_LAMBDA + "}}");
        assertNotNull(f);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(f, state);
        assertEquals(AngleSolverState.TASER_SMOOTH_LAMBDA, state.getSmoothLambda(), 0.0);
    }
}
