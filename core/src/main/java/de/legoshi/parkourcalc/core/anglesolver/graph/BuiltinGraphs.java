package de.legoshi.parkourcalc.core.anglesolver.graph;

public final class BuiltinGraphs {

    public static final int IMPROVE_TICK_CAP = 256;

    public static final int FAST_SEED_CAP_MS = 500;

    public static final String FAST_PRESET = "Fast";
    public static final String MULTI_START_PRESET = "Fast (multi-start)";
    public static final String OPTIMIZE_PRESET = "Optimize";
    public static final String SMOOTH_PRESET = "Smooth";
    public static final String SMOOTH_INPUTS_PRESET = "Smooth (inputs)";

    public static final double WRAP_RESERVE_FRACTION = 0.25;
    public static final int WRAP_RESERVE_CAP_SEC = 3;
    public static final double SMOOTH_RESERVE_FRACTION = 0.125;
    public static final int SMOOTH_RESERVE_CAP_SEC = 6;

    public static final int SWEEP_SEEDS = 64;
    public static final int MULTI_START_SWEEP_SEC = 20;
    public static final int MIN_SWEEP_SEC = 3;

    public static final int SWEEP_ILS_FROM_SEC = 20;
    public static final int BOTH_STARTS_BELOW_SEC = 2;
    public static final int COLD_START_CAP_MS = 500;

    public static final int TREE_BATCH = 8;

    public static final int POLISH_FACINGS = 64;
    public static final int POLISH_SEARCHES = 21;
    public static final int POLISH_TABLE = 30000;
    public static final int POLISH_WINDOW = 64;
    public static final int POLISH_WORK = 4000;
    public static final double POLISH_RESERVE_FRACTION = 0.05;
    public static final int POLISH_RESERVE_CAP_SEC = 2;

    private BuiltinGraphs() {
    }

    public static boolean isBuiltinPreset(String name) {
        return FAST_PRESET.equals(name) || MULTI_START_PRESET.equals(name) || OPTIMIZE_PRESET.equals(name);
    }

    public static SolverGraph smooth(double giveBack) {
        GraphBuilder g = new GraphBuilder(SMOOTH_PRESET, true);
        g.add("entry", "entry");
        g.add("smooth", "smooth")
                .set("smooth", "giveBack", giveBack)
                .set("smooth", GraphRunner.RESERVE_FRACTION_PARAM, 1.0)
                .set("smooth", GraphRunner.RESERVE_CAP_PARAM, 0);
        g.add("emit", "emit");
        chain(g, "entry", "smooth");
        chain(g, "smooth", "emit");
        return g.build();
    }

    public static boolean isSmoothPreset(String name) {
        return SMOOTH_PRESET.equals(name) || SMOOTH_INPUTS_PRESET.equals(name);
    }

    public static SolverGraph smoothInputs(double giveBack, boolean allowA, boolean allowD, boolean keepSprint) {
        GraphBuilder g = new GraphBuilder(SMOOTH_INPUTS_PRESET, true);
        g.add("entry", "entry");
        g.add("smooth", "inputSmooth")
                .set("smooth", "giveBack", giveBack)
                .set("smooth", "allowA", allowA)
                .set("smooth", "allowD", allowD)
                .set("smooth", "keepSprint", keepSprint)
                .set("smooth", GraphRunner.RESERVE_FRACTION_PARAM, 1.0)
                .set("smooth", GraphRunner.RESERVE_CAP_PARAM, 0);
        g.add("emit", "emit");
        chain(g, "entry", "smooth");
        chain(g, "smooth", "emit");
        return g.build();
    }

    public static SolverGraph fast() {
        return build(FAST_PRESET, 10, 3, 0, true, false, FAST_SEED_CAP_MS, 0);
    }

    public static SolverGraph fast(int budgetMs) {
        int seedCapMs = budgetMs > 0 && budgetMs < 1000 ? budgetMs : FAST_SEED_CAP_MS;
        return build(FAST_PRESET, 10, 3, 0, true, false, seedCapMs, 0);
    }

    public static SolverGraph fastMultiStart() {
        return build(MULTI_START_PRESET, 10, 3, 0, true, false, FAST_SEED_CAP_MS, SWEEP_SEEDS);
    }

    public static SolverGraph fastRunTicks() {
        return build("Fast (run ticks)", 10, 3, 0, true, false, 0, 0);
    }

    public static SolverGraph optimize(int optimizeSeconds) {
        return build(OPTIMIZE_PRESET, 10, 3, optimizeSeconds > 0 ? optimizeSeconds : 120, false, true, 0, SWEEP_SEEDS);
    }

    public static SolverGraph fromBudget(boolean stopOnFeasible, boolean ilsExhaustive,
                                         boolean useWindowSolver, int window, int commit, int timeBudgetSeconds) {
        return build("Custom", window, commit, timeBudgetSeconds, false, true, 0, SWEEP_SEEDS);
    }

    private static SolverGraph build(String name, int window, int commit, int t, boolean seedFirst,
                                     boolean leafSnap, int seedCapMs, int sweepSeeds) {
        boolean fastTier = t <= 0;
        int tp = fastTier ? 120 : t;
        long reserveNanos = fastTier ? 0L
                : GraphRunner.reserveNanos(WRAP_RESERVE_FRACTION, WRAP_RESERVE_CAP_SEC, t * 1_000_000_000L);
        int stageSec = Math.max(1, tp - (int) ((reserveNanos + 999_999_999L) / 1_000_000_000L));
        int ffSec = fastTier ? 3 : Math.max(1, Math.min(30, stageSec / 2));
        int horizonSec = fastTier ? 0 : Math.max(2, Math.min(12, stageSec / 3));

        GraphBuilder g = new GraphBuilder(name, true);
        g.add("entry", "entry");
        int sweepSec = fastTier ? MULTI_START_SWEEP_SEC : Math.min(20, stageSec / 4);
        boolean sweep = sweepSeeds > 0 && sweepSec >= MIN_SWEEP_SEC;
        if (sweep) {
            g.add("seeds", "seedSweep")
                    .set("seeds", "seeds", sweepSeeds)
                    .set("seeds", "budgetSec", sweepSec);
        }
        boolean bothStarts = !fastTier && !seedFirst && t < BOTH_STARTS_BELOW_SEC;
        if (bothStarts) {
            g.add("cold", "dualChain")
                    .set("cold", "keepBetter", true)
                    .set("cold", "budgetMs", COLD_START_CAP_MS);
        }
        g.add("horizon", "recedingHorizon")
                .set("horizon", "window", window)
                .set("horizon", "commit", commit)
                .set("horizon", "budgetSec", horizonSec)
                .set("horizon", "keepIncoming", bothStarts);
        g.add("wrap0", "wrapYaws");
        g.add("seed", "dualChain")
                .set("seed", "keepBetter", true)
                .set("seed", "budgetSec", fastTier ? 0 : t)
                .set("seed", "budgetMs", fastTier ? seedCapMs : 0)
                .set("seed", "warmSec", fastTier ? 0 : 1);
        if (bothStarts) g.add("pick", "pickStart");
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
        g.add("sweep", "facingStep")
                .set("sweep", "budgetSec", fastTier ? 0 : Math.max(2, Math.min(20, stageSec / 3)));
        boolean sweepIls = !fastTier && t >= SWEEP_ILS_FROM_SEC;
        if (sweepIls) {
            g.add("ils2", "ilsPolish")
                    .set("ils2", "budgetSec", 3)
                    .set("ils2", "perturbMagMin", 0.0055)
                    .set("ils2", "perturbMagSpan", 10.0)
                    .set("ils2", "perturbTicksMin", 15)
                    .set("ils2", "perturbTicksSpan", 15);
        }
        g.add("translate2", "translatedStart");
        g.add("fold", "foldDriver")
                .set("fold", "objectiveRounds", fastTier ? 0 : 16)
                .set("fold", "multiStart", fastTier ? 0 : 2)
                .set("fold", "budgetSec", fastTier ? 0 : Math.max(2, stageSec / 2))
                .set("fold", "ascentMs", fastTier ? 0 : 60000)
                .set("fold", "tickCap", IMPROVE_TICK_CAP);
        if (!fastTier) {
            g.add("polish0", "latticePolish")
                    .set("polish0", GraphRunner.LANE_PARAM, true);
            if (leafSnap) {
                g.add("snap0", "leafSnap")
                        .set("snap0", "pairPass", 1)
                        .set("snap0", GraphRunner.LANE_PARAM, true);
            }
        }
        g.add("ladder", "homotopyLadder");
        g.add("cert", "certBnb")
                .set("cert", "budgetSec", fastTier ? 0 : tp)
                .set("cert", "ffSec", ffSec)
                .set("cert", "ffNodeCap", fastTier ? 32 : 256)
                .set("cert", "tickCap", IMPROVE_TICK_CAP);
        if (!fastTier) {
            g.set("cert", "batch", TREE_BATCH).set("cert", GraphRunner.LANE_PARAM, true);
        }
        g.add("bnb", "bnb")
                .set("bnb", "budgetSec", fastTier ? 0 : Math.max(3, stageSec / 2))
                .set("bnb", "ffSec", fastTier ? 40 : Math.max(3, stageSec / 3))
                .set("bnb", "tickCap", IMPROVE_TICK_CAP);
        g.add("ils", "ilsPolish")
                .set("ils", "budgetSec", fastTier ? 0 : Math.max(1, stageSec - 4));
        if (!fastTier) g.add("join", "joinLanes").set("join", "of", "cert");
        g.add("cap2", "capCertify")
                .set("cap2", "computeDualGap", false)
                .set("cap2", "skipIfSettled", true);
        if (!fastTier) {
            g.add("polish", "latticePolish")
                    .set("polish", "facings", POLISH_FACINGS)
                    .set("polish", "searches", POLISH_SEARCHES)
                    .set("polish", "tableSize", POLISH_TABLE)
                    .set("polish", "window", POLISH_WINDOW)
                    .set("polish", "work", POLISH_WORK);
        }
        g.add("wrap", "wrapIls")
                .set("wrap", "budgetSec", fastTier ? 0 : tp)
                .set("wrap", "minRemainingSec", 1);
        g.add("translate", "translatedStart");
        if (leafSnap) {
            g.add("snap", "leafSnap")
                    .set("snap", "pairPass", fastTier ? 0 : 1);
        }
        if (!fastTier) {
            g.add("join0", "joinLanes").set("join0", "of", "polish0");
            g.add("dive", "certBnb")
                    .set("dive", "budgetSec", t)
                    .set("dive", "ffSec", 0)
                    .set("dive", "ffNodeCap", 0)
                    .set("dive", "tickCap", IMPROVE_TICK_CAP)
                    .set("dive", "dive", true)
                    .set("dive", "batch", TREE_BATCH)
                    .set("dive", "labelSuffix", " (dive order)");
            g.add("translate3", "translatedStart");
            if (leafSnap) {
                g.add("snap2", "leafSnap")
                        .set("snap2", "pairPass", 1)
                        .set("snap2", "repeat", true);
            }
        }
        g.add("certify", "certBnb")
                .set("certify", "budgetSec", fastTier ? 0 : t)
                .set("certify", "ffSec", 0)
                .set("certify", "ffNodeCap", 0)
                .set("certify", "tickCap", IMPROVE_TICK_CAP)
                .set("certify", "exhaust", true)
                .set("certify", "batch", fastTier ? 1 : TREE_BATCH);
        g.add("emit", "emit");

        String first = sweep ? "seeds" : "entry";
        if (sweep) chain(g, "entry", "seeds");
        if (seedFirst) {
            chain(g, first, "seed");
            chain(g, "seed", "horizon");
            chain(g, "horizon", "wrap0");
            chain(g, "wrap0", "cap1");
        } else if (bothStarts) {
            chain(g, first, "cold");
            chain(g, "cold", "horizon");
            chain(g, "horizon", "wrap0");
            chain(g, "wrap0", "seed");
            chain(g, "seed", "pick");
            chain(g, "pick", "cap1");
        } else {
            chain(g, first, "horizon");
            chain(g, "horizon", "wrap0");
            chain(g, "wrap0", "seed");
            chain(g, "seed", "cap1");
        }
        chain(g, "cap1", "freeRescue");
        chain(g, "freeRescue", "peel");
        chain(g, "peel", "freeImprove");
        chain(g, "freeImprove", "sweep");
        g.edge("sweep", Guarantee.TRUE, sweepIls ? "ils2" : "translate2");
        g.edge("sweep", Guarantee.FOUND, "fold");
        g.edge("sweep", Guarantee.IMPROVED, "fold");
        g.edge("sweep", Guarantee.UNCHANGED, "fold");
        g.edge("sweep", Guarantee.NONE, "fold");
        if (sweepIls) chain(g, "ils2", "translate2");
        if (fastTier) {
            g.edge("translate2", Guarantee.DONE, "fold");
        } else {
            g.edge("translate2", Guarantee.DONE, "sweep");
        }
        if (fastTier) {
            chain(g, "fold", "ladder");
        } else if (leafSnap) {
            chain(g, "fold", "polish0");
            chain(g, "polish0", "snap0");
            chain(g, "snap0", "ladder");
        } else {
            chain(g, "fold", "polish0");
            chain(g, "polish0", "ladder");
        }
        chain(g, "ladder", "cert");
        chain(g, "cert", "bnb");
        chain(g, "bnb", "ils");
        if (fastTier) {
            chain(g, "ils", "cap2");
        } else {
            chain(g, "ils", "join");
            chain(g, "join", "cap2");
        }
        if (fastTier) {
            chain(g, "cap2", "wrap");
        } else {
            chain(g, "cap2", "polish");
            chain(g, "polish", "wrap");
        }
        chain(g, "wrap", "translate");
        String last = "translate";
        if (leafSnap) {
            chain(g, last, "snap");
            last = "snap";
        }
        if (!fastTier) {
            chain(g, last, "join0");
            chain(g, "join0", "dive");
            chain(g, "dive", "translate3");
            last = "translate3";
            if (leafSnap) {
                chain(g, last, "snap2");
                last = "snap2";
            }
        }
        chain(g, last, "certify");
        chain(g, "certify", "emit");
        return g.build();
    }

    private static void chain(GraphBuilder g, String from, String to) {
        g.chainAll(from, to);
    }
}
