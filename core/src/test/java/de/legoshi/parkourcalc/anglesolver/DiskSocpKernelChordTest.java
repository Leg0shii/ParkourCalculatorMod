package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.DiskSocpKernel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpLinearModel;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DiskSocpKernelChordTest {

    @Test
    public void chordRowForcesDirectionWedge() {
        int n = 1;
        double[] cx = {1.0};
        double[] cz = {0.0};
        double[] mMag = {1.0};
        List<DiskSocpKernel.ChordRow> chords = new ArrayList<>();
        chords.add(new DiskSocpKernel.ChordRow(0, 0.0, 1.0, Math.cos(Math.PI / 6.0), "chord@0"));
        DiskSocpKernel.Outcome oc = DiskSocpKernel.solveChords(n, cx, cz, mMag,
                Collections.<JumpLinearModel.Wall>emptyList(), null, chords, null);
        assertNotNull(oc.result);
        assertTrue("chord solve must converge, gap " + oc.result.gap, oc.result.converged);
        assertEquals(0, oc.failCode);
        double ux = oc.result.ux[0];
        double uz = oc.result.uz[0];
        assertEquals("uz must sit on the chord", Math.cos(Math.PI / 6.0), uz, 1.0e-5);
        assertEquals("ux must reach the disk boundary inside the wedge", 0.5, ux, 1.0e-5);
        assertTrue("modulus must be full", Math.hypot(ux, uz) >= 1.0 - 1.0e-5);
    }

    @Test
    public void emptyChordListMatchesPlainSolveBitExact() {
        int n = 3;
        double[] cx = {1.0, 0.5, -0.25};
        double[] cz = {0.25, -1.0, 0.75};
        double[] mMag = {0.1, 0.026, 0.3274};
        DiskSocpKernel.Result plain = DiskSocpKernel.solve(n, cx, cz, mMag,
                Collections.<JumpLinearModel.Wall>emptyList());
        DiskSocpKernel.Outcome oc = DiskSocpKernel.solveChords(n, cx, cz, mMag,
                Collections.<JumpLinearModel.Wall>emptyList(), null, null, null);
        assertNotNull(plain);
        assertNotNull(oc.result);
        assertEquals(Double.doubleToRawLongBits(plain.value), Double.doubleToRawLongBits(oc.result.value));
        for (int t = 0; t < n; t++) {
            assertEquals(Double.doubleToRawLongBits(plain.ux[t]), Double.doubleToRawLongBits(oc.result.ux[t]));
            assertEquals(Double.doubleToRawLongBits(plain.uz[t]), Double.doubleToRawLongBits(oc.result.uz[t]));
        }
    }
}
