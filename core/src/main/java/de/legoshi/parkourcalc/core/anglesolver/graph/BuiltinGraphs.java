package de.legoshi.parkourcalc.core.anglesolver.graph;

public final class BuiltinGraphs {

    public static final int IMPROVE_TICK_CAP = 256;

    public static final String FAST_PRESET = "Fast";
    public static final String OPTIMIZE_PRESET = "Optimize";

    private BuiltinGraphs() {
    }

    public static boolean isBuiltinPreset(String name) {
        return FAST_PRESET.equals(name) || OPTIMIZE_PRESET.equals(name);
    }

    public static SolverGraph fast() {
        return build(FAST_PRESET, 10, 3, 0, false);
    }

    public static SolverGraph fastRunTicks() {
        return build("Fast (run ticks)", 10, 3, 0, true);
    }

    public static SolverGraph optimize(int optimizeSeconds) {
        return build(OPTIMIZE_PRESET, 10, 3, optimizeSeconds > 0 ? optimizeSeconds : 120, false);
    }

    public static SolverGraph fromBudget(boolean stopOnFeasible, boolean ilsExhaustive,
                                         boolean useWindowSolver, int window, int commit, int timeBudgetSeconds) {
        return build("Custom", window, commit, timeBudgetSeconds, false);
    }

    private static SolverGraph build(String name, int window, int commit, int t, boolean runTicks) {
        boolean leafSnap = !runTicks;
        boolean fastTier = t <= 0;
        int tp = fastTier ? 120 : t;
        long reserveNanos = fastTier ? 0L : GraphRunner.wrapReserveNanos(t * 1_000_000_000L);
        int stageSec = Math.max(1, tp - (int) ((reserveNanos + 999_999_999L) / 1_000_000_000L));
        int improveSec = Math.max(1, Math.min(60, stageSec / 3));
        int ffSec = fastTier ? 3 : Math.max(1, Math.min(30, stageSec / 2));

        GraphBuilder g = new GraphBuilder(name, true);
        g.add("entry", "entry");
        g.add("horizon", "recedingHorizon")
                .set("horizon", "window", window)
                .set("horizon", "commit", commit);
        g.add("wrap0", "wrapYaws");
        g.add("seed", "dualChain")
                .set("seed", "keepBetter", true)
                .set("seed", "budgetSec", fastTier ? 0 : t)
                .set("seed", "warmSec", fastTier ? 0 : 1);
        g.add("cap1", "capCertify")
                .set("cap1", "computeDualGap", true)
                .set("cap1", "markSettled", true);
        g.add("freeRescue", "freeStartImprove")
                .set("freeRescue", "jointOnly", true)
                .set("freeRescue", "budgetSec", 2);
        g.add("peel", "setupPeel")
                .set("peel", "budgetSec", fastTier ? 12 : Math.max(1, Math.min(12, t)))
                .set("peel", "candidateMs", 600)
                .set("peel", "stepDeg", 15.0)
                .set("peel", "window", window)
                .set("peel", "commit", commit);
        g.add("freeImprove", "freeStartImprove")
                .set("freeImprove", "budgetSec", fastTier ? 20 : Math.min(20, t))
                .set("freeImprove", "warmSec", fastTier ? 0 : 1);
        g.add("fold", "foldDriver")
                .set("fold", "objectiveRounds", fastTier ? 0 : 16)
                .set("fold", "multiStart", fastTier ? 0 : 2)
                .set("fold", "budgetSec", fastTier ? 0 : Math.max(2, stageSec / 2))
                .set("fold", "ascentMs", fastTier ? 0 : 60000)
                .set("fold", "tickCap", IMPROVE_TICK_CAP);
        g.add("ladder", "homotopyLadder");
        g.add("cert", "certBnb")
                .set("cert", "budgetSec", fastTier ? 0 : Math.max(ffSec, improveSec))
                .set("cert", "ffSec", ffSec)
                .set("cert", "ffNodeCap", fastTier ? 32 : 256)
                .set("cert", "tickCap", IMPROVE_TICK_CAP);
        g.add("bnb", "bnb")
                .set("bnb", "budgetSec", fastTier ? 0 : Math.max(3, stageSec / 2))
                .set("bnb", "ffSec", fastTier ? 40 : Math.max(3, stageSec / 3))
                .set("bnb", "tickCap", IMPROVE_TICK_CAP);
        g.add("ils", "ilsPolish")
                .set("ils", "budgetSec", fastTier ? 0 : Math.max(1, stageSec - 4));
        g.add("cap2", "capCertify")
                .set("cap2", "computeDualGap", false)
                .set("cap2", "skipIfSettled", true);
        g.add("wrap", "wrapIls")
                .set("wrap", "budgetSec", fastTier ? 0 : tp)
                .set("wrap", "minRemainingSec", 1);
        g.add("translate", "translatedStart");
        if (leafSnap) {
            g.add("snap", "leafSnap")
                    .set("snap", "pairPass", fastTier ? 0 : 1);
        }
        g.add("emit", "emit");

        if (runTicks) {
            chain(g, "entry", "seed");
            chain(g, "seed", "horizon");
            chain(g, "horizon", "wrap0");
            chain(g, "wrap0", "cap1");
        } else {
            chain(g, "entry", "horizon");
            chain(g, "horizon", "wrap0");
            chain(g, "wrap0", "seed");
            chain(g, "seed", "cap1");
        }
        chain(g, "cap1", "freeRescue");
        chain(g, "freeRescue", "peel");
        chain(g, "peel", "freeImprove");
        chain(g, "freeImprove", "fold");
        chain(g, "fold", "ladder");
        chain(g, "ladder", "cert");
        chain(g, "cert", "bnb");
        chain(g, "bnb", "ils");
        chain(g, "ils", "cap2");
        chain(g, "cap2", "wrap");
        chain(g, "wrap", "translate");
        if (leafSnap) {
            chain(g, "translate", "snap");
            chain(g, "snap", "emit");
        } else {
            chain(g, "translate", "emit");
        }
        return g.build();
    }

    private static void chain(GraphBuilder g, String from, String to) {
        g.chainAll(from, to);
    }
}
