package de.legoshi.parkourcalc.core.anglesolver.noturn;

import java.util.ArrayList;
import java.util.List;

public final class MinTvMaster {

    private final int[][] ordered;
    private final List<NoGoodCut> cuts = new ArrayList<>();
    private int cursor;

    public MinTvMaster(int setupEnd, boolean takeoffW, int minDwell, int maxEdges, int[] alphabet) {
        this(StructurePoolDriver.enumerateRaw(setupEnd, takeoffW, minDwell, maxEdges, alphabet));
    }

    public MinTvMaster(List<int[]> rawSchedulesWithinLevelOrder) {
        this(rawSchedulesWithinLevelOrder, true);
    }

    public MinTvMaster(List<int[]> schedules, boolean edgeSorted) {
        List<int[]> src = schedules;
        int nItems = src.size();
        Integer[] idx = new Integer[nItems];
        int[] edges = new int[nItems];
        for (int i = 0; i < nItems; i++) {
            idx[i] = i;
            edges[i] = NoTurnKeys.countEdges(src.get(i));
        }
        if (edgeSorted) {
            java.util.Arrays.sort(idx, (a, b) -> {
                if (edges[a] != edges[b]) return Integer.compare(edges[a], edges[b]);
                return Integer.compare(a, b);
            });
        }
        this.ordered = new int[nItems][];
        for (int i = 0; i < nItems; i++) {
            ordered[i] = src.get(idx[i]);
        }
        this.cursor = 0;
    }

    public void addCut(NoGoodCut cut) {
        if (cut != null) cuts.add(cut);
    }

    private boolean cut(int[] schedule) {
        for (int i = 0; i < cuts.size(); i++) {
            if (cuts.get(i).matches(schedule)) return true;
        }
        return false;
    }

    public int[] next() {
        while (cursor < ordered.length) {
            int[] s = ordered[cursor++];
            if (!cut(s)) return s;
        }
        return null;
    }

    public List<int[]> collectAll() {
        List<int[]> out = new ArrayList<>();
        int[] s;
        while ((s = next()) != null) out.add(s);
        return out;
    }
}
