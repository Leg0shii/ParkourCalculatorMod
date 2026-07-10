package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.LinkedHashMap;

public final class FacingLattice {

    private static final double DEG_PER_BUCKET = 180.0 / (Math.PI * (double) McSineTable.INDEX_FROM_RAD);

    private FacingLattice() {
    }

    static float radOf(float gfDeg, boolean modern, boolean boostCast) {
        if (!modern && !boostCast) {
            return gfDeg * (float) Math.PI / 180.0F;
        }
        return gfDeg * (float) (Math.PI / 180.0);
    }

    static int rawSinIndex(float gfDeg, boolean modern, boolean boostCast) {
        return (int) (radOf(gfDeg, modern, boostCast) * McSineTable.INDEX_FROM_RAD);
    }

    public static int sinIndex(float gfDeg, boolean modern, boolean boostCast) {
        return rawSinIndex(gfDeg, modern, boostCast) & McSineTable.MASK;
    }

    public static int cosIndex(float gfDeg, boolean modern, boolean boostCast) {
        float rad = radOf(gfDeg, modern, boostCast);
        return (int) (rad * McSineTable.INDEX_FROM_RAD + McSineTable.COS_INDEX_OFFSET) & McSineTable.MASK;
    }

    public static long jointCellId(float gfDeg, boolean modern, boolean jumpBoostTick) {
        long msin = sinIndex(gfDeg, modern, false);
        long mcos = cosIndex(gfDeg, modern, false);
        long id = (msin << 48) | (mcos << 32);
        if (jumpBoostTick && !modern) {
            long bsin = sinIndex(gfDeg, modern, true);
            long bcos = cosIndex(gfDeg, modern, true);
            id |= (bsin << 16) | bcos;
        }
        return id;
    }

    static int movementSinOf(long id) {
        return (int) ((id >>> 48) & 0xffffL);
    }

    private static int signed16(int diff) {
        return ((diff & 0xffff) << 16) >> 16;
    }

    public static float stepToSinBucket(float gfDeg, int targetIndex, boolean modern) {
        targetIndex &= McSineTable.MASK;
        int curRaw = rawSinIndex(gfDeg, modern, false);
        if ((curRaw & McSineTable.MASK) == targetIndex) {
            return gfDeg;
        }
        int sd = signed16(targetIndex - (curRaw & McSineTable.MASK));
        int targetRaw = curRaw + sd;
        double estD = (double) gfDeg + (double) sd * DEG_PER_BUCKET;

        float a;
        float b;
        if (targetRaw > curRaw) {
            a = gfDeg;
            b = (float) (estD + 4.0 * DEG_PER_BUCKET);
            int guard = 0;
            while (rawSinIndex(b, modern, false) < targetRaw && guard++ < 256) {
                b = (float) ((double) b + 8.0 * DEG_PER_BUCKET);
            }
            if (rawSinIndex(b, modern, false) < targetRaw) {
                return Float.NaN;
            }
        } else {
            b = gfDeg;
            a = (float) (estD - 4.0 * DEG_PER_BUCKET);
            int guard = 0;
            while (rawSinIndex(a, modern, false) >= targetRaw && guard++ < 256) {
                a = (float) ((double) a - 8.0 * DEG_PER_BUCKET);
            }
            if (rawSinIndex(a, modern, false) >= targetRaw) {
                return Float.NaN;
            }
        }

        while (Math.nextUp(a) < b) {
            float m = midFloat(a, b);
            if (rawSinIndex(m, modern, false) >= targetRaw) {
                b = m;
            } else {
                a = m;
            }
        }
        return rawSinIndex(b, modern, false) == targetRaw ? b : Float.NaN;
    }

    public static float[] cellRepresentatives(float gfDeg, int sinDeltaLo, int sinDeltaHi,
                                              boolean modern, boolean jumpBoostTick) {
        int curSin = sinIndex(gfDeg, modern, false);
        double loD = (double) gfDeg + ((double) sinDeltaLo - 0.5) * DEG_PER_BUCKET;
        double hiD = (double) gfDeg + ((double) sinDeltaHi + 0.5) * DEG_PER_BUCKET;
        float fLo = (float) loD;
        float fHi = (float) hiD;
        if (fLo > fHi) {
            float t = fLo;
            fLo = fHi;
            fHi = t;
        }
        LinkedHashMap<Long, Float> cells = new LinkedHashMap<Long, Float>();
        record(gfDeg, jointCellId(gfDeg, modern, jumpBoostTick), curSin, sinDeltaLo, sinDeltaHi, cells);
        collectCells(fLo, fHi, curSin, sinDeltaLo, sinDeltaHi, modern, jumpBoostTick, cells, 0);
        float[] out = new float[cells.size()];
        int i = 0;
        for (Float f : cells.values()) {
            out[i++] = f.floatValue();
        }
        return out;
    }

    private static void collectCells(float a, float b, int curSin, int lo, int hi, boolean modern,
                                     boolean jumpBoostTick, LinkedHashMap<Long, Float> cells, int depth) {
        long idA = jointCellId(a, modern, jumpBoostTick);
        long idB = jointCellId(b, modern, jumpBoostTick);
        record(a, idA, curSin, lo, hi, cells);
        record(b, idB, curSin, lo, hi, cells);
        if (idA == idB) {
            return;
        }
        if (Math.nextUp(a) >= b) {
            return;
        }
        if (depth >= 120) {
            return;
        }
        float m = midFloat(a, b);
        collectCells(a, m, curSin, lo, hi, modern, jumpBoostTick, cells, depth + 1);
        collectCells(m, b, curSin, lo, hi, modern, jumpBoostTick, cells, depth + 1);
    }

    private static void record(float f, long id, int curSin, int lo, int hi, LinkedHashMap<Long, Float> cells) {
        int sinDelta = signed16(movementSinOf(id) - curSin);
        if (sinDelta < lo || sinDelta > hi) {
            return;
        }
        Long key = Long.valueOf(id);
        if (!cells.containsKey(key)) {
            cells.put(key, Float.valueOf(f));
        }
    }

    private static float midFloat(float a, float b) {
        float m = (float) (((double) a + (double) b) * 0.5);
        if (m <= a || m >= b) {
            m = Math.nextUp(a);
        }
        return m;
    }
}
