package de.legoshi.parkourcalc.core.anglesolver.coldsearch;

import java.util.ArrayList;
import java.util.List;

final class StratPrefixes {

    static final class Seed {
        final int startSeg;
        final int[] moveKey;
        final boolean[] hold;
        final boolean preSprint;
        final String label;

        Seed(int startSeg, int[] moveKey, boolean[] hold, boolean preSprint, String label) {
            this.startSeg = startSeg;
            this.moveKey = moveKey;
            this.hold = hold;
            this.preSprint = preSprint;
            this.label = label;
        }

        int endSeg() {
            return startSeg + moveKey.length - 1;
        }
    }

    private static final int[] ENGAGE = {KeyLine.W, KeyLine.WA, KeyLine.WD};
    private static final int[] PESSI_COAST = {KeyLine.NONE, KeyLine.S, KeyLine.A, KeyLine.D, KeyLine.SA, KeyLine.SD};
    private static final int[] MARK_SIDE = {KeyLine.A, KeyLine.D};

    private StratPrefixes() {
    }

    static List<Seed> generate(ColdProblem p) {
        List<Seed> out = new ArrayList<Seed>();
        int f0 = p.pressSegTicks[0];
        int nextPress = p.pressSegTicks.length > 1 ? p.pressSegTicks[1] : p.lastPressSeg;
        int maxK = Math.min(10, Math.max(1, nextPress - f0 - 1));

        for (int k = 1; k <= maxK; k++) {
            for (int coast : PESSI_COAST) {
                for (int engage : ENGAGE) {
                    int[] mk = new int[k + 1];
                    boolean[] hd = new boolean[k + 1];
                    for (int i = 0; i < k; i++) mk[i] = coast;
                    mk[k] = engage;
                    hd[k] = true;
                    out.add(new Seed(f0, mk, hd, false,
                            "pessi" + k + "/" + KeyLine.COMBO_LABEL[coast] + ">" + KeyLine.COMBO_LABEL[engage]));
                }
            }
        }
        for (int k = 1; k <= maxK; k++) {
            for (int engage : ENGAGE) {
                int[] mk = new int[k + 1];
                boolean[] hd = new boolean[k + 1];
                for (int i = 0; i <= k; i++) mk[i] = engage;
                hd[k] = true;
                out.add(new Seed(f0, mk, hd, false, "fmm" + k + "/" + KeyLine.COMBO_LABEL[engage]));
            }
        }
        for (int k = 1; k <= Math.min(6, maxK); k++) {
            for (int side : MARK_SIDE) {
                for (int engage : ENGAGE) {
                    int[] mk = new int[k + 1];
                    boolean[] hd = new boolean[k + 1];
                    for (int i = 0; i < k; i++) mk[i] = side;
                    mk[k] = engage;
                    hd[k] = true;
                    out.add(new Seed(f0, mk, hd, false,
                            "mark" + KeyLine.COMBO_LABEL[side] + k + ">" + KeyLine.COMBO_LABEL[engage]));
                }
            }
        }
        int[] mk = new int[] {KeyLine.W};
        boolean[] hd = new boolean[] {true};
        out.add(new Seed(f0, mk, hd, true, "run+jam"));
        return out;
    }
}
