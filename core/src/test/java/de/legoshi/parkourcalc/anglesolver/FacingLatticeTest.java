package de.legoshi.parkourcalc.anglesolver;

import de.legoshi.parkourcalc.core.anglesolver.solver.FacingLattice;
import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FacingLatticeTest {

    private static int signed16(int diff) {
        return ((diff & 0xffff) << 16) >> 16;
    }

    private static void runStepBand(Random rnd, float lo, float hi, boolean modern, long[] counters) {
        for (int i = 0; i < 100_000; i++) {
            float f = lo + (float) (rnd.nextDouble() * (hi - lo));
            int delta = rnd.nextInt(13) - 6;
            if (delta == 0) {
                delta = 1;
            }
            int cur = FacingLattice.sinIndex(f, modern, false);
            int target = (cur + delta) & 0xffff;
            float stepped = FacingLattice.stepToSinBucket(f, target, modern);
            counters[0]++;
            if (Float.isNaN(stepped)) {
                counters[1]++;
                continue;
            }
            assertEquals("stepToSinBucket must land on the exact requested sin bucket",
                    target, FacingLattice.sinIndex(stepped, modern, false));
        }
    }

    @Test
    public void stepToSinBucketHitsTargetExactly() {
        Random rnd = new Random(0xC0FFEEL);
        long[] counters = new long[2];
        runStepBand(rnd, -360.0f, 360.0f, false, counters);
        runStepBand(rnd, -360.0f, 360.0f, true, counters);
        runStepBand(rnd, -20000.0f, 20000.0f, false, counters);
        runStepBand(rnd, -20000.0f, 20000.0f, true, counters);
        System.out.println("[stepToSinBucket] calls=" + counters[0] + " no-representative=" + counters[1]
                + " (" + ((double) counters[1] / counters[0]) + ")");
        assertTrue("stepToSinBucket exercised", counters[0] == 400_000L);
    }

    @Test
    public void cellRepresentativesReproduceIdsAndDedup() {
        Random rnd = new Random(0x5EEDL);
        int lo = -2;
        int hi = 2;
        for (int i = 0; i < 30_000; i++) {
            float f = -360.0f + (float) (rnd.nextDouble() * 720.0);
            boolean modern = rnd.nextBoolean();
            boolean boost = rnd.nextBoolean();
            float[] reps = FacingLattice.cellRepresentatives(f, lo, hi, modern, boost);
            int curSin = FacingLattice.sinIndex(f, modern, false);
            Set<Long> ids = new HashSet<Long>();
            for (float r : reps) {
                long id = FacingLattice.jointCellId(r, modern, boost);
                assertTrue("joint cells must be deduped", ids.add(Long.valueOf(id)));
                int sd = signed16(FacingLattice.sinIndex(r, modern, false) - curSin);
                assertTrue("representative sin delta must be inside the requested range",
                        sd >= lo && sd <= hi);
            }
        }

        int rlo = -3;
        int rhi = 3;
        long requested = 0;
        long missing = 0;
        for (int i = 0; i < 30_000; i++) {
            float f = -20000.0f + (float) (rnd.nextDouble() * 40000.0);
            boolean modern = rnd.nextBoolean();
            boolean boost = rnd.nextBoolean();
            float[] reps = FacingLattice.cellRepresentatives(f, rlo, rhi, modern, boost);
            int curSin = FacingLattice.sinIndex(f, modern, false);
            boolean[] present = new boolean[rhi - rlo + 1];
            for (float r : reps) {
                int sd = signed16(FacingLattice.sinIndex(r, modern, false) - curSin);
                if (sd >= rlo && sd <= rhi) {
                    present[sd - rlo] = true;
                }
            }
            for (int d = rlo; d <= rhi; d++) {
                requested++;
                if (!present[d - rlo]) {
                    missing++;
                }
            }
        }
        double frac = (double) missing / (double) requested;
        System.out.println("[cellRepresentatives] no-representative fraction (|gf| up to 20000): " + frac
                + " (requested=" + requested + " missing=" + missing + ")");
        assertTrue("fraction is a valid probability", frac >= 0.0 && frac <= 1.0);

        for (double mag : new double[] {1.0e4, 1.0e5, 1.0e6}) {
            long req = 0;
            long miss = 0;
            for (int i = 0; i < 5_000; i++) {
                float f = (float) ((rnd.nextDouble() * 2.0 - 1.0) * mag);
                boolean modern = rnd.nextBoolean();
                float[] reps = FacingLattice.cellRepresentatives(f, rlo, rhi, modern, false);
                int curSin = FacingLattice.sinIndex(f, modern, false);
                boolean[] present = new boolean[rhi - rlo + 1];
                for (float r : reps) {
                    int sd = signed16(FacingLattice.sinIndex(r, modern, false) - curSin);
                    if (sd >= rlo && sd <= rhi) {
                        present[sd - rlo] = true;
                    }
                }
                for (int d = rlo; d <= rhi; d++) {
                    req++;
                    if (!present[d - rlo]) {
                        miss++;
                    }
                }
            }
            System.out.println("[cellRepresentatives] no-representative fraction at |gf|~" + mag + ": "
                    + ((double) miss / (double) req));
        }
    }

    @Test
    public void adjacencyIncludesOwnCell() {
        Random rnd = new Random(0xADAC7L);
        for (int i = 0; i < 10_000; i++) {
            float f;
            int band = rnd.nextInt(3);
            if (band == 0) {
                f = -360.0f + (float) (rnd.nextDouble() * 720.0);
            } else if (band == 1) {
                f = -20000.0f + (float) (rnd.nextDouble() * 40000.0);
            } else {
                f = (float) ((rnd.nextDouble() * 2.0 - 1.0) * 5000.0);
            }
            boolean modern = rnd.nextBoolean();
            boolean boost = rnd.nextBoolean();
            float[] reps = FacingLattice.cellRepresentatives(f, -1, 1, modern, boost);
            long ownId = FacingLattice.jointCellId(f, modern, boost);
            boolean found = false;
            for (float r : reps) {
                if (FacingLattice.jointCellId(r, modern, boost) == ownId) {
                    found = true;
                    break;
                }
            }
            assertTrue("delta [-1,+1] cells must include the input float's own joint cell", found);
        }
    }
}
