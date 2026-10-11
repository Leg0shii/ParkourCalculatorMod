package de.legoshi.parkourcalc.core;

import de.legoshi.parkourcalc.core.anglesolver.AngleSolverState;
import de.legoshi.parkourcalc.core.save.SaveFile;
import de.legoshi.parkourcalc.core.save.SaveIO;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class SmoothGiveBackSaveTest {

    @Test
    public void absentGiveBackLoadsAsTheWholeOffset() {
        SaveFile f = SaveIO.parseSafe("{\"version\":1,\"angleSolver\":{\"smoothLambda\":0.01}}");
        assertNotNull(f);
        AngleSolverState state = new AngleSolverState();
        state.setSmoothGiveBack(0.3);
        SaveIO.applyAngleSolverTo(f, state);
        assertEquals(1.0, state.getSmoothGiveBack(), 0.0);
    }

    @Test
    public void savedGiveBackLoadsAndClamps() {
        SaveFile f = SaveIO.parseSafe("{\"version\":1,\"angleSolver\":{\"smoothGiveBack\":0.25}}");
        assertNotNull(f);
        AngleSolverState state = new AngleSolverState();
        SaveIO.applyAngleSolverTo(f, state);
        assertEquals(0.25, state.getSmoothGiveBack(), 0.0);
        state.setSmoothGiveBack(7.0);
        assertEquals(1.0, state.getSmoothGiveBack(), 0.0);
        state.setSmoothGiveBack(-1.0);
        assertEquals(0.0, state.getSmoothGiveBack(), 0.0);
    }
}
