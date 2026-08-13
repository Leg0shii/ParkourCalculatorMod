package de.legoshi.parkourcalc.core.anglesolver.stratfinder;

import java.util.Locale;

public final class DeriveChain {

    public final int[] fires;
    public final int[] landings;
    public final double[] yPerTick;
    public final String error;

    private DeriveChain(int[] fires, int[] landings, double[] yPerTick, String error) {
        this.fires = fires;
        this.landings = landings;
        this.yPerTick = yPerTick;
        this.error = error;
    }

    private static DeriveChain fail(String message) {
        return new DeriveChain(null, null, null, message);
    }

    public static DeriveChain fromPresses(int startTick, int[] presses, double startTop,
                                          double[] landTops, boolean legacy) {
        if (presses.length == 0 || presses.length != landTops.length) {
            return fail("need one landing block per recorded jump press");
        }
        int[] landings = new int[presses.length];
        int[] durations = new int[presses.length];
        double ground = startTop;
        int prevLanding = startTick;
        for (int i = 0; i < presses.length; i++) {
            if (presses[i] < prevLanding) {
                return fail("jump " + (i + 1) + " is pressed on T" + (presses[i] + 1)
                        + " but the previous jump only lands on tick " + prevLanding
                        + "; shift the press later");
            }
            double dy = landTops[i] - ground;
            int d = JumpArcs.duration(dy, legacy);
            if (d < 1) {
                return fail(String.format(Locale.ROOT, "jump %d: rise %.2f is not jumpable", i + 1, dy));
            }
            durations[i] = d;
            landings[i] = presses[i] + d;
            prevLanding = landings[i];
            ground = landTops[i];
        }
        int last = landings[landings.length - 1];
        double[] y = new double[last + 1];
        ground = startTop;
        for (int t = 0; t <= presses[0]; t++) {
            y[t] = ground;
        }
        for (int i = 0; i < presses.length; i++) {
            double[] arc = JumpArcs.heights(durations[i], landTops[i] - ground, legacy);
            for (int a = 1; a <= durations[i]; a++) {
                y[presses[i] + a] = ground + arc[a];
            }
            ground = landTops[i];
            int until = i + 1 < presses.length ? presses[i + 1] : landings[i];
            for (int t = landings[i]; t <= until; t++) {
                y[t] = ground;
            }
        }
        return new DeriveChain(presses.clone(), landings, y, null);
    }
}
