package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.FlowDirection;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FlowDirectionTest {

    @Test
    public void nearestPicksTheAxisOrDiagonalClosestToTheVector() {
        assertEquals(FlowDirection.NONE, FlowDirection.nearest(0.0, 0.0));
        assertEquals(FlowDirection.NONE, FlowDirection.nearest(1.0e-9, -1.0e-9));
        assertEquals(FlowDirection.POS_X, FlowDirection.nearest(0.5, 0.1));
        assertEquals(FlowDirection.NEG_X, FlowDirection.nearest(-0.5, 0.1));
        assertEquals(FlowDirection.POS_Z, FlowDirection.nearest(-0.1, 0.5));
        assertEquals(FlowDirection.NEG_Z, FlowDirection.nearest(0.1, -0.5));
        assertEquals(FlowDirection.POS_X_POS_Z, FlowDirection.nearest(0.4, 0.5));
        assertEquals(FlowDirection.POS_X_NEG_Z, FlowDirection.nearest(0.5, -0.4));
        assertEquals(FlowDirection.NEG_X_POS_Z, FlowDirection.nearest(-0.5, 0.4));
        assertEquals(FlowDirection.NEG_X_NEG_Z, FlowDirection.nearest(-0.4, -0.5));
    }
}
