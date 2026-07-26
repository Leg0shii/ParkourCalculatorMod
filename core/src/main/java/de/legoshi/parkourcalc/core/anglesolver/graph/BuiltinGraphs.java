package de.legoshi.parkourcalc.core.anglesolver.graph;

public final class BuiltinGraphs {

    public static final int IMPROVE_TICK_CAP = 256;

    private BuiltinGraphs() {
    }

    public static SolverGraph fast() {
        return build("Fast", 16, 4500, 2, "LIGHT", true, false, true, 10, 3, 0);
    }

    public static SolverGraph optimize(int optimizeSeconds) {
        return build("Optimize", 16, 4500, 4, "EXHAUSTIVE", false, true, true, 10, 3, optimizeSeconds);
    }

    public static SolverGraph explore() {
        return explore(10, 40, 10);
    }

    public static SolverGraph explore(int seedSec, int bnbSec, int ilsSec) {
        GraphBuilder g = new GraphBuilder("Explore", true);
        g.add("entry", "entry");
        g.add("emit", "emit");
        g.add("seed", "dualChain").set("seed", "keepBetter", true).set("seed", "budgetSec", seedSec);
        router(g, "rFeas", "CANDIDATE_FEASIBLE_RAW");
        g.add("bnbOpt", "bnb").set("bnbOpt", "mode", "OPTIMIZE")
                .set("bnbOpt", "budgetSec", bnbSec).set("bnbOpt", "minBudgetMs", 0);
        g.add("bnbFF", "bnb").set("bnbFF", "mode", "FIRST_FEASIBLE")
                .set("bnbFF", "budgetSec", bnbSec).set("bnbFF", "minBudgetMs", 0);
        router(g, "rIls", "CANDIDATE_FEASIBLE_RAW");
        g.add("ils", "ilsPolish").set("ils", "budgetSec", ilsSec).set("ils", "roundCap", 400);
        g.add("smooth", "smoothing").set("smooth", "countEvals", true);
        g.edge("entry", Guarantee.DONE, "seed");
        g.edge("seed", Guarantee.FOUND, "rFeas");
        g.edge("seed", Guarantee.NONE, "bnbFF");
        g.edge("rFeas", Guarantee.TRUE, "bnbOpt");
        g.edge("rFeas", Guarantee.FALSE, "bnbFF");
        bnbOut(g, "bnbOpt", "rIls");
        bnbOut(g, "bnbFF", "rIls");
        g.edge("rIls", Guarantee.TRUE, "ils");
        g.edge("rIls", Guarantee.FALSE, "emit");
        g.edge("ils", Guarantee.IMPROVED, "smooth");
        g.edge("ils", Guarantee.UNCHANGED, "smooth");
        g.edge("smooth", Guarantee.DONE, "emit");
        return g.build();
    }

    public static SolverGraph fromBudget(int restarts, int maxEval, int polishCount, boolean exhaustiveDepth,
                                         boolean stopOnFeasible, boolean ilsExhaustive, boolean useWindowSolver,
                                         int window, int commit, int timeBudgetSeconds) {
        return build("Custom", restarts, maxEval, polishCount, exhaustiveDepth ? "EXHAUSTIVE" : "LIGHT",
                stopOnFeasible, ilsExhaustive, useWindowSolver, window, commit, timeBudgetSeconds);
    }

    private static SolverGraph build(String name, int restarts, int maxEval, int polishCount, String polishDepth,
                                     boolean sof, boolean ilx, boolean win, int window, int commit, int t) {
        int tp = t > 0 ? t : 120;
        boolean exh = ilx && !sof;
        boolean throttle = exh && t > 0;
        int raceThrottledSec = Math.max(1, t * 2 / 5);
        int raceWarmSec = throttle ? raceThrottledSec : t;
        int peelSec = t > 0 ? Math.max(1, Math.min(12, t)) : 12;
        int momentumSec = t > 0 ? Math.min(240, t) : 240;
        int freeSec = t > 0 ? Math.min(20, t) : 20;
        int rescueSec = t > 0 ? Math.max(1, Math.min(3, t)) : 3;
        int sweepSec = Math.max(1, Math.min(60, tp / 5));
        int bnbSec = Math.max(1, (tp - sweepSec) * 3 / 4);
        int ilsSec = Math.max(1, tp - sweepSec - bnbSec);
        int nearBnbSec = Math.max(1, Math.min(60, tp / 2));
        String coldEntry = throttle ? "rRaceTicks" : "raceColdFull";
        String afterFree = sof ? "rRescueTicks" : "rHave";
        String afterCap = exh ? "rExhTicks" : (ilx ? "rWrapEps" : "rTrans");

        GraphBuilder g = new GraphBuilder(name, true);
        g.add("entry", "entry");
        g.add("emit", "emit");
        router(g, "rJumps", "JUMPS_LE_ONE");
        g.add("seedSingle", "dualChain")
                .set("seedSingle", "keepBetter", false)
                .set("seedSingle", "budgetSec", t);
        g.add("cap1", "capCertify")
                .set("cap1", "computeDualGap", true)
                .set("cap1", "markSettled", true);
        g.add("seedMulti", "dualChain")
                .set("seedMulti", "keepBetter", true)
                .set("seedMulti", "budgetSec", t);
        g.add("wrap0", "wrapYaws");
        router(g, "rWarmTicks", "TICKS_LE_CAP");
        g.add("smoothWarm", "smoothing").set("smoothWarm", "countEvals", false);
        g.add("settledMark", "markSettled");
        g.add("repA", "report");
        g.add("repWarm", "report");
        g.add("repSkip", "report");
        race(g, "raceWarm", 16, 4500, 2, "LIGHT", true, raceWarmSec);
        race(g, "raceColdFull", restarts, maxEval, polishCount, polishDepth, false, t);
        if (throttle) {
            race(g, "raceColdThrottled", restarts, maxEval, polishCount, polishDepth, false, raceThrottledSec);
            router(g, "rRaceTicks", "TICKS_LE_CAP");
            router(g, "rRaceJumps", "JUMPS_LE_ONE");
            router(g, "rRaceHead", "HAS_REACH_HEADROOM");
        }
        if (ilx) {
            router(g, "rMomGate", "VIOLATION_AT_MOST");
            g.set("rMomGate", "epsilon", 5.0e-2);
        } else {
            router(g, "rMomGate", "CANDIDATE_FEASIBLE_SCORED");
        }
        g.add("momentum", "momentumAssembly")
                .set("momentum", "budgetSec", momentumSec)
                .set("momentum", "minBudgetSec", 2);
        router(g, "rFree", "HAS_FREE_START");
        g.add("freeImprove", "freeStartImprove")
                .set("freeImprove", "budgetSec", freeSec)
                .set("freeImprove", "iters", 3);
        router(g, "rEarlyFree", "HAS_FREE_START");
        g.add("freeRescue", "freeStartImprove")
                .set("freeRescue", "jointOnly", true)
                .set("freeRescue", "budgetSec", 2)
                .set("freeRescue", "iters", 1);
        if (!sof) {
            router(g, "rEarlyFeas", "CANDIDATE_FEASIBLE_RAW");
        }
        if (sof) {
            router(g, "rFeasFastCold", "CANDIDATE_FEASIBLE_RAW");
            router(g, "rFeasFastWarm", "CANDIDATE_FEASIBLE_RAW");
            g.add("lblFF", "label").set("lblFF", "text", " (first feasible)");
            router(g, "rRescueTicks", "TICKS_LE_CAP");
            router(g, "rRescueFeas", "CANDIDATE_FEASIBLE_SCORED");
            g.add("rescueBnb", "bnb")
                    .set("rescueBnb", "mode", "FIRST_FEASIBLE")
                    .set("rescueBnb", "budgetSec", rescueSec)
                    .set("rescueBnb", "minBudgetMs", 0)
                    .set("rescueBnb", "labelSuffix", " (first feasible)");
        }
        router(g, "rHave", "HAS_CANDIDATE");
        g.add("cap2", "capCertify")
                .set("cap2", "computeDualGap", false)
                .set("cap2", "skipIfSettled", true);
        if (exh) {
            router(g, "rExhTicks", "TICKS_LE_CAP");
            router(g, "rExhFeas", "CANDIDATE_FEASIBLE_RAW");
            router(g, "rExhJumps", "JUMPS_LE_ONE");
            router(g, "rExhHead", "HAS_REACH_HEADROOM");
            g.add("sweep", "seamSweep").set("sweep", "budgetSec", sweepSec);
            g.add("bnbOpt", "bnb")
                    .set("bnbOpt", "mode", "OPTIMIZE")
                    .set("bnbOpt", "budgetSec", bnbSec)
                    .set("bnbOpt", "minBudgetMs", 0);
            g.add("ils", "ilsPolish")
                    .set("ils", "budgetSec", ilsSec)
                    .set("ils", "roundCap", 400);
            router(g, "rNearFeas", "CANDIDATE_FEASIBLE_SCORED");
            router(g, "rNearEps", "VIOLATION_AT_MOST");
            g.set("rNearEps", "epsilon", 5.0e-2);
            g.add("nearBnb", "bnb")
                    .set("nearBnb", "mode", "FIRST_FEASIBLE")
                    .set("nearBnb", "budgetSec", nearBnbSec)
                    .set("nearBnb", "minBudgetMs", 1000)
                    .set("nearBnb", "labelSuffix", " (near miss)");
        }
        if (ilx) {
            router(g, "rWrapEps", "VIOLATION_AT_MOST");
            g.set("rWrapEps", "epsilon", 1.0e-2);
            router(g, "rWrapFeas", "CANDIDATE_FEASIBLE_SCORED");
            router(g, "rWrapLegal", "LEGAL_PUSH");
            g.add("wrap", "wrapIls")
                    .set("wrap", "budgetSec", tp)
                    .set("wrap", "minRemainingSec", 1);
        }
        router(g, "rTrans", "HAS_FREE_START");
        g.add("translate", "translatedStart");
        g.add("smoothFinal", "smoothing").set("smoothFinal", "countEvals", true);
        if (win) {
            g.add("horizon", "recedingHorizon")
                    .set("horizon", "window", window)
                    .set("horizon", "commit", commit);
            router(g, "rChainTicks", "TICKS_LE_CAP");
            g.add("peel", "setupPeel")
                    .set("peel", "budgetSec", peelSec)
                    .set("peel", "candidateMs", 600)
                    .set("peel", "stepDeg", 15.0)
                    .set("peel", "window", window)
                    .set("peel", "commit", commit);
        }

        g.edge("entry", Guarantee.DONE, "rJumps");
        g.edge("rJumps", Guarantee.TRUE, "seedSingle");
        g.edge("rJumps", Guarantee.FALSE, win ? "horizon" : "seedMulti");
        g.edge("seedSingle", Guarantee.FOUND, "cap1");
        g.edge("seedSingle", Guarantee.NONE, "repA");
        g.edge("cap1", Guarantee.AT_CAP, "repSkip");
        g.edge("cap1", Guarantee.FALSE, "repA");
        if (win) {
            g.edge("horizon", Guarantee.FOUND, "rChainTicks");
            g.edge("horizon", Guarantee.NONE, "seedMulti");
            g.edge("rChainTicks", Guarantee.TRUE, "seedMulti");
            g.edge("rChainTicks", Guarantee.FALSE, "wrap0");
            g.edge("peel", Guarantee.FOUND, "wrap0");
            g.edge("peel", Guarantee.NONE, "repA");
        }
        g.edge("seedMulti", Guarantee.FOUND, "wrap0");
        g.edge("seedMulti", Guarantee.NONE, win ? "peel" : "repA");
        g.edge("wrap0", Guarantee.DONE, "rWarmTicks");
        g.edge("rWarmTicks", Guarantee.TRUE, "smoothWarm");
        g.edge("rWarmTicks", Guarantee.FALSE, "settledMark");
        g.edge("smoothWarm", Guarantee.DONE, "repWarm");
        g.edge("settledMark", Guarantee.DONE, "repSkip");
        g.edge("repSkip", Guarantee.DONE, "rMomGate");
        g.edge("repA", Guarantee.DONE, sof ? "rFeasFastCold" : "rEarlyFeas");
        g.edge("repWarm", Guarantee.DONE, sof ? "rFeasFastWarm" : "raceWarm");
        if (sof) {
            g.edge("rFeasFastCold", Guarantee.TRUE, "lblFF");
            g.edge("rFeasFastCold", Guarantee.FALSE, "rEarlyFree");
            g.edge("rFeasFastWarm", Guarantee.TRUE, "lblFF");
            g.edge("rFeasFastWarm", Guarantee.FALSE, "raceWarm");
            g.edge("lblFF", Guarantee.DONE, "rMomGate");
            g.edge("freeRescue", Guarantee.IMPROVED, "lblFF");
        } else {
            g.edge("rEarlyFeas", Guarantee.TRUE, coldEntry);
            g.edge("rEarlyFeas", Guarantee.FALSE, "rEarlyFree");
            g.edge("freeRescue", Guarantee.IMPROVED, coldEntry);
        }
        g.edge("rEarlyFree", Guarantee.TRUE, "freeRescue");
        g.edge("rEarlyFree", Guarantee.FALSE, coldEntry);
        g.edge("freeRescue", Guarantee.UNCHANGED, coldEntry);
        if (throttle) {
            g.edge("rRaceTicks", Guarantee.TRUE, "rRaceJumps");
            g.edge("rRaceTicks", Guarantee.FALSE, "raceColdFull");
            g.edge("rRaceJumps", Guarantee.FALSE, "raceColdThrottled");
            g.edge("rRaceJumps", Guarantee.TRUE, "rRaceHead");
            g.edge("rRaceHead", Guarantee.TRUE, "raceColdThrottled");
            g.edge("rRaceHead", Guarantee.FALSE, "raceColdFull");
            raceOut(g, "raceColdThrottled", "rMomGate");
        }
        raceOut(g, "raceColdFull", "rMomGate");
        raceOut(g, "raceWarm", "rMomGate");
        g.edge("rMomGate", Guarantee.TRUE, "rFree");
        g.edge("rMomGate", Guarantee.FALSE, "momentum");
        g.edge("momentum", Guarantee.FOUND, "rFree");
        g.edge("momentum", Guarantee.NONE, "rFree");
        g.edge("rFree", Guarantee.TRUE, "freeImprove");
        g.edge("rFree", Guarantee.FALSE, afterFree);
        g.edge("freeImprove", Guarantee.IMPROVED, afterFree);
        g.edge("freeImprove", Guarantee.UNCHANGED, afterFree);
        if (sof) {
            g.edge("rRescueTicks", Guarantee.TRUE, "rRescueFeas");
            g.edge("rRescueTicks", Guarantee.FALSE, "rHave");
            g.edge("rRescueFeas", Guarantee.TRUE, "rHave");
            g.edge("rRescueFeas", Guarantee.FALSE, "rescueBnb");
            bnbOut(g, "rescueBnb", "rHave");
        }
        g.edge("rHave", Guarantee.TRUE, "cap2");
        g.edge("rHave", Guarantee.FALSE, "emit");
        g.edge("cap2", Guarantee.AT_CAP, afterCap);
        g.edge("cap2", Guarantee.FALSE, afterCap);
        if (exh) {
            g.edge("rExhTicks", Guarantee.TRUE, "rExhFeas");
            g.edge("rExhTicks", Guarantee.FALSE, "rNearFeas");
            g.edge("rExhFeas", Guarantee.TRUE, "rExhJumps");
            g.edge("rExhFeas", Guarantee.FALSE, "rNearFeas");
            g.edge("rExhJumps", Guarantee.FALSE, "sweep");
            g.edge("rExhJumps", Guarantee.TRUE, "rExhHead");
            g.edge("rExhHead", Guarantee.TRUE, "sweep");
            g.edge("rExhHead", Guarantee.FALSE, "rNearFeas");
            g.edge("sweep", Guarantee.IMPROVED, "bnbOpt");
            g.edge("sweep", Guarantee.UNCHANGED, "bnbOpt");
            bnbOut(g, "bnbOpt", "ils");
            g.edge("ils", Guarantee.IMPROVED, "rNearFeas");
            g.edge("ils", Guarantee.UNCHANGED, "rNearFeas");
            g.edge("rNearFeas", Guarantee.TRUE, "rWrapEps");
            g.edge("rNearFeas", Guarantee.FALSE, "rNearEps");
            g.edge("rNearEps", Guarantee.TRUE, "nearBnb");
            g.edge("rNearEps", Guarantee.FALSE, "rWrapEps");
            bnbOut(g, "nearBnb", "rWrapEps");
        }
        if (ilx) {
            g.edge("rWrapEps", Guarantee.TRUE, "rWrapFeas");
            g.edge("rWrapEps", Guarantee.FALSE, "rTrans");
            g.edge("rWrapFeas", Guarantee.FALSE, "wrap");
            g.edge("rWrapFeas", Guarantee.TRUE, "rWrapLegal");
            g.edge("rWrapLegal", Guarantee.TRUE, "wrap");
            g.edge("rWrapLegal", Guarantee.FALSE, "rTrans");
            g.edge("wrap", Guarantee.ADOPTED, "emit");
            g.edge("wrap", Guarantee.REJECTED, "rTrans");
        }
        g.edge("rTrans", Guarantee.TRUE, "translate");
        g.edge("rTrans", Guarantee.FALSE, "smoothFinal");
        g.edge("translate", Guarantee.DONE, "smoothFinal");
        g.edge("smoothFinal", Guarantee.DONE, "emit");
        return g.build();
    }

    private static void router(GraphBuilder g, String id, String predicate) {
        g.add(id, "router").set(id, "predicate", predicate);
        if ("TICKS_LE_CAP".equals(predicate)) {
            g.set(id, "cap", IMPROVE_TICK_CAP);
        }
    }

    private static void race(GraphBuilder g, String id, int restarts, int maxEval, int polishCount,
                             String polishDepth, boolean warm, int budgetSec) {
        g.add(id, "cmaesRace")
                .set(id, "restarts", restarts)
                .set(id, "maxEval", maxEval)
                .set(id, "polishCount", polishCount)
                .set(id, "polishDepth", polishDepth)
                .set(id, "sigmaDeg", 90.0)
                .set(id, "warmStart", warm)
                .set(id, "budgetSec", budgetSec);
    }

    private static void raceOut(GraphBuilder g, String id, String to) {
        g.edge(id, Guarantee.FEASIBLE, to);
        g.edge(id, Guarantee.INFEASIBLE, to);
        g.edge(id, Guarantee.NONE, to);
    }

    private static void bnbOut(GraphBuilder g, String id, String to) {
        g.edge(id, Guarantee.FOUND, to);
        g.edge(id, Guarantee.IMPROVED, to);
        g.edge(id, Guarantee.UNCHANGED, to);
        g.edge(id, Guarantee.NONE, to);
    }
}
