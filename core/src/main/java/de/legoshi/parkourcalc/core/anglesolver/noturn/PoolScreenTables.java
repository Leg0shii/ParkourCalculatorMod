package de.legoshi.parkourcalc.core.anglesolver.noturn;

import java.util.Arrays;

public final class PoolScreenTables {

    public static final int SLOTS = 36;

    public final int n;
    public final int walls;
    public final int groups;
    public final int mainGroup;
    public final int[] freeGroups;
    public final int[] groupOf;
    public final float[] tab;
    public final float[] coefW;
    public final float[] shift0;
    public final int[] wallAxisX;
    public final int[] wallKind;
    public final float[] objCoef;
    public final float objConst;
    public final boolean objAxisX;
    public final boolean maximize;
    public final float loX;
    public final float hiX;
    public final float loZ;
    public final float hiZ;
    public final int diskGrid;
    public final int diskCoarseStride;
    public final int diskMidStride;
    public final float diskEarlyOut;
    public final float diskKeep;
    private final String key;

    public PoolScreenTables(int n, int walls, int groups, int mainGroup, int[] freeGroups, int[] groupOf, float[] tab,
                     float[] coefW, float[] shift0, int[] wallAxisX, int[] wallKind, float[] objCoef, float objConst,
                     boolean objAxisX, boolean maximize, float loX, float hiX, float loZ, float hiZ, int diskGrid,
                     int diskCoarseStride, int diskMidStride, float diskEarlyOut, float diskKeep) {
        this.n = n;
        this.walls = walls;
        this.groups = groups;
        this.mainGroup = mainGroup;
        this.freeGroups = freeGroups;
        this.groupOf = groupOf;
        this.tab = tab;
        this.coefW = coefW;
        this.shift0 = shift0;
        this.wallAxisX = wallAxisX;
        this.wallKind = wallKind;
        this.objCoef = objCoef;
        this.objConst = objConst;
        this.objAxisX = objAxisX;
        this.maximize = maximize;
        this.loX = loX;
        this.hiX = hiX;
        this.loZ = loZ;
        this.hiZ = hiZ;
        this.diskGrid = diskGrid;
        this.diskCoarseStride = diskCoarseStride;
        this.diskMidStride = diskMidStride;
        this.diskEarlyOut = diskEarlyOut;
        this.diskKeep = diskKeep;
        int h = 31 * n + walls;
        h = 31 * h + groups;
        h = 31 * h + mainGroup;
        h = 31 * h + Arrays.hashCode(freeGroups);
        h = 31 * h + Arrays.hashCode(groupOf);
        h = 31 * h + Arrays.hashCode(tab);
        h = 31 * h + Arrays.hashCode(coefW);
        h = 31 * h + Arrays.hashCode(shift0);
        h = 31 * h + Arrays.hashCode(wallAxisX);
        h = 31 * h + Arrays.hashCode(wallKind);
        h = 31 * h + Arrays.hashCode(objCoef);
        h = 31 * h + Float.floatToIntBits(objConst);
        h = 31 * h + Float.floatToIntBits(loX + 3.0f * hiX + 7.0f * loZ + 11.0f * hiZ);
        h = 31 * h + diskGrid * 1000 + diskCoarseStride * 10 + diskMidStride;
        this.key = "n" + n + "w" + walls + "g" + groups + "m" + mainGroup + "h" + Integer.toHexString(h)
                + (objAxisX ? "x" : "z") + (maximize ? "+" : "-");
    }

    public String key() {
        return key;
    }

    public String programKey() {
        return "n" + n + "w" + walls + "g" + groups + "m" + mainGroup + "f" + Arrays.toString(freeGroups)
                + "d" + diskGrid + "/" + diskCoarseStride + "/" + diskMidStride;
    }
}
