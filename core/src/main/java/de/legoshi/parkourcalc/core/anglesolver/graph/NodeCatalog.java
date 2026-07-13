package de.legoshi.parkourcalc.core.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.BnbNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.CapCertifyNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.CmaesRaceNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.DualChainNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.FreeStartImproveNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.IlsPolishNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.LabelNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.MarkSettledNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.MomentumAssemblyNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.ReportNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.RecedingHorizonNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.RouterNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.SeamSweepNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.SetupPeelNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.SmoothingNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.TranslatedStartNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.WrapIlsNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.WrapYawsNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NodeCatalog {

    private static final Map<String, NodeType> TYPES = new LinkedHashMap<>();

    private NodeCatalog() {
    }

    static {
        register(NodeType.builder("entry", "Entry", NodeCategory.CONTROL)
                .requires(InputRequirement.NONE)
                .branch(Branch.preserves(Guarantee.DONE))
                .entry()
                .factory(p -> (ctx, in, tok, dl) -> NodeOutcome.of(Guarantee.DONE, in))
                .build());
        register(NodeType.builder("emit", "Emit", NodeCategory.CONTROL)
                .requires(InputRequirement.ANY)
                .emit()
                .factory(p -> (ctx, in, tok, dl) -> NodeOutcome.of(Guarantee.DONE, in))
                .build());
        register(NodeType.builder("router", "Router", NodeCategory.CONTROL)
                .requires(InputRequirement.ANY)
                .branch(Branch.preserves(Guarantee.TRUE))
                .branch(Branch.preserves(Guarantee.FALSE))
                .param(ParamSpec.choice("predicate", "Predicate", predicateNames(), "HAS_CANDIDATE"))
                .param(ParamSpec.decimal("epsilon", "Epsilon", 0.0, 1.0, 0.0))
                .param(ParamSpec.integer("cap", "Tick cap", 0, 100000, 64))
                .fallback(Guarantee.FALSE)
                .factory(RouterNode::new)
                .build());
        register(NodeType.builder("label", "Label", NodeCategory.CONTROL)
                .requires(InputRequirement.ANY)
                .branch(Branch.preserves(Guarantee.DONE))
                .param(ParamSpec.text("text", "Chain suffix", ""))
                .fallback(Guarantee.DONE)
                .factory(LabelNode::new)
                .build());
        register(NodeType.builder("capCertify", "Cap certify", NodeCategory.CONTROL)
                .requires(InputRequirement.ANY)
                .branch(Branch.preserves(Guarantee.AT_CAP))
                .branch(Branch.preserves(Guarantee.FALSE))
                .param(ParamSpec.bool("computeDualGap", "Compute dual gap", false))
                .param(ParamSpec.bool("markSettled", "Mark settled at cap", false))
                .param(ParamSpec.bool("skipIfSettled", "Skip when settled", false))
                .fallback(Guarantee.FALSE)
                .factory(CapCertifyNode::new)
                .build());
        register(NodeType.builder("report", "Report progress", NodeCategory.CONTROL)
                .requires(InputRequirement.ANY)
                .branch(Branch.preserves(Guarantee.DONE))
                .fallback(Guarantee.DONE)
                .factory(ReportNode::new)
                .build());
        register(NodeType.builder("markSettled", "Mark settled", NodeCategory.CONTROL)
                .requires(InputRequirement.ANY)
                .branch(Branch.preserves(Guarantee.DONE))
                .fallback(Guarantee.DONE)
                .factory(MarkSettledNode::new)
                .build());
        register(NodeType.builder("wrapYaws", "Wrap yaws", NodeCategory.CONTROL)
                .requires(InputRequirement.ANY)
                .branch(Branch.preserves(Guarantee.DONE))
                .fallback(Guarantee.DONE)
                .factory(WrapYawsNode::new)
                .build());
        register(NodeType.builder("dualChain", "Dual chain", NodeCategory.SEED)
                .requires(InputRequirement.ANY)
                .branch(Branch.feasible(Guarantee.FOUND))
                .branch(Branch.preserves(Guarantee.NONE))
                .param(ParamSpec.bool("keepBetter", "Keep better vs incumbent", false))
                .param(ParamSpec.integer("budgetSec", "Budget (s)", 0, 600, 0))
                .param(ParamSpec.integer("slpPhase1Calls", "SLP phase-1 calls", 1, 10000, 40))
                .param(ParamSpec.integer("slpTotalCalls", "SLP total calls", 1, 10000, 60))
                .param(ParamSpec.decimal("slpTrStartDeg", "SLP trust region start (deg)", 0.001, 360.0, 30.0))
                .param(ParamSpec.decimal("slpTrMaxDeg", "SLP trust region max (deg)", 0.001, 360.0, 45.0))
                .param(ParamSpec.decimal("slpTrMinDeg", "SLP trust region floor (deg)", 0.0, 10.0, 1.0e-7))
                .param(ParamSpec.integer("slpLpMaxIter", "SLP LP max iterations", 100, 1000000, 2000))
                .param(ParamSpec.text("cfMargins", "Closed form margin ladder",
                        "0.0,1.0e-4,3.0e-4,6.0e-4,1.2e-3,2.5e-3,5.0e-3,1.0e-2"))
                .param(ParamSpec.integer("cfMaxInertiaPasses", "Closed form inertia passes", 1, 16, 4))
                .param(ParamSpec.integer("cfRungStallLimit", "Closed form rung stall limit", 1, 16, 2))
                .param(ParamSpec.integer("rrOuterIters", "Relaxation outer iterations", 1, 1000, 30))
                .param(ParamSpec.integer("rrInnerIters", "Relaxation inner iterations", 1, 100000, 500))
                .param(ParamSpec.decimal("rrRhoStart", "Relaxation rho start", 1.0e-6, 1.0e12, 100.0))
                .param(ParamSpec.decimal("rrRhoGrow", "Relaxation rho growth", 1.0, 100.0, 3.0))
                .param(ParamSpec.decimal("rrRhoMax", "Relaxation rho cap", 1.0, 1.0e18, 1.0e6))
                .param(ParamSpec.text("rrPinWidths", "Relaxation pin widths", "0.4,0.15"))
                .param(ParamSpec.text("rrSeedMargins", "Relaxation seed margins",
                        "0.0,3.0e-4,1.2e-3,5.0e-3,1.0e-2,2.0e-2,5.0e-2"))
                .param(ParamSpec.integer("rrDualRestarts", "Relaxation dual restarts", 0, 100, 5))
                .param(ParamSpec.integer("rrSlpPhase1Calls", "Relaxation SLP phase-1 calls", 1, 10000, 160))
                .param(ParamSpec.integer("rrSlpTotalCalls", "Relaxation SLP total calls", 1, 10000, 220))
                .param(ParamSpec.text("lrSchedule", "Lattice repair schedule",
                        "0.5:0.05,0.12:0.012,0.03:0.003,0.008:0.0008,0.002:0.0001"))
                .param(ParamSpec.integer("lrMaxRounds", "Lattice repair rounds", 1, 100, 8))
                .param(ParamSpec.decimal("lrStartViolCap", "Lattice repair start cap", 0.0, 10.0, 0.08))
                .param(ParamSpec.integer("lrPairSteps", "Lattice repair pair steps", 1, 32, 4))
                .budgetParam("budgetSec")
                .fallback(Guarantee.NONE)
                .factory(DualChainNode::new)
                .build());
        register(NodeType.builder("recedingHorizon", "Receding horizon", NodeCategory.WINDOWING)
                .requires(InputRequirement.NONE)
                .branch(Branch.feasible(Guarantee.FOUND))
                .branch(Branch.preserves(Guarantee.NONE))
                .param(ParamSpec.integer("window", "Window", 2, 14, 10))
                .param(ParamSpec.integer("commit", "Commit", 1, 13, 3))
                .param(ParamSpec.text("windowLadder", "Window ladder (blank = auto)", ""))
                .param(ParamSpec.text("commitLadder", "Commit ladder (blank = auto)", ""))
                .param(ParamSpec.integer("budgetSec", "Budget (s)", 0, 600, 0))
                .budgetParam("budgetSec")
                .fallback(Guarantee.NONE)
                .factory(RecedingHorizonNode::new)
                .build());
        register(NodeType.builder("setupPeel", "Setup peel", NodeCategory.SEED)
                .requires(InputRequirement.NONE)
                .branch(Branch.unknown(Guarantee.FOUND))
                .branch(Branch.preserves(Guarantee.NONE))
                .param(ParamSpec.integer("budgetSec", "Budget (s)", 1, 600, 12))
                .param(ParamSpec.integer("candidateMs", "Per-candidate (ms)", 50, 60000, 600))
                .param(ParamSpec.decimal("stepDeg", "Sweep step (deg)", 1.0, 90.0, 15.0))
                .param(ParamSpec.integer("window", "Window", 2, 14, 10))
                .param(ParamSpec.integer("commit", "Commit", 1, 13, 3))
                .param(ParamSpec.text("windowLadder", "Window ladder (blank = auto)", ""))
                .param(ParamSpec.text("commitLadder", "Commit ladder (blank = auto)", ""))
                .budgetParam("budgetSec")
                .fallback(Guarantee.NONE)
                .factory(SetupPeelNode::new)
                .build());
        register(NodeType.builder("cmaesRace", "CMA-ES race", NodeCategory.GLOBAL)
                .requires(InputRequirement.ANY)
                .branch(Branch.feasible(Guarantee.FEASIBLE))
                .branch(Branch.unknown(Guarantee.INFEASIBLE))
                .branch(Branch.preserves(Guarantee.NONE))
                .param(ParamSpec.integer("restarts", "Restarts", 1, 256, 16))
                .param(ParamSpec.integer("maxEval", "Max evals", 500, 100000, 4500))
                .param(ParamSpec.integer("polishCount", "Polish basins", 1, 64, 2))
                .param(ParamSpec.choice("polishDepth", "Polish depth", new String[] {"LIGHT", "EXHAUSTIVE"}, "LIGHT"))
                .param(ParamSpec.text("polishB1", "Polish B1 schedule (blank = depth)", ""))
                .param(ParamSpec.text("polishB2", "Polish B2 schedule (blank = depth)", ""))
                .param(ParamSpec.integer("polishMaxRounds", "Polish rounds (-1 = depth)", -1, 1000, -1))
                .param(ParamSpec.integer("polishRestarts", "Polish restarts (-1 = depth)", -1, 256, -1))
                .param(ParamSpec.integer("polishPairSpan", "Polish pair span (-1 = depth)", -1, 64, -1))
                .param(ParamSpec.decimal("sigmaDeg", "Sigma (deg)", 1.0, 360.0, 90.0))
                .param(ParamSpec.bool("warmStart", "Warm start from candidate", false))
                .param(ParamSpec.integer("budgetSec", "Budget (s)", 0, 600, 0))
                .param(ParamSpec.decimal("cmaMuIneq", "Penalty mu (inequality)", 1.0, 1.0e12, 1.0e7))
                .param(ParamSpec.decimal("cmaMuEq", "Penalty mu (equality)", 1.0, 1.0e12, 1.0e7))
                .param(ParamSpec.integer("cmaLambda", "Population (0 = auto)", 0, 4096, 0))
                .param(ParamSpec.decimal("cmaPolishStepDeg", "Polish start step (deg)", 1.0e-6, 360.0, 45.0))
                .param(ParamSpec.integer("cmaPolishIters", "Polish iterations", 0, 100000, 500))
                .param(ParamSpec.decimal("cmaPolishFloorDeg", "Polish step floor (deg)", 0.0, 360.0, 1.0e-7))
                .advance()
                .budgetParam("budgetSec")
                .fallback(Guarantee.NONE)
                .factory(CmaesRaceNode::new)
                .build());
        register(NodeType.builder("momentumAssembly", "Momentum assembly", NodeCategory.RECOVERY)
                .requires(InputRequirement.ANY)
                .branch(Branch.feasible(Guarantee.FOUND))
                .branch(Branch.preserves(Guarantee.NONE))
                .param(ParamSpec.integer("budgetSec", "Budget (s)", 0, 600, 240))
                .param(ParamSpec.integer("minBudgetSec", "Minimum budget (s)", 0, 60, 2))
                .param(ParamSpec.text("seamTrims", "Seam trims", "2,0,4"))
                .param(ParamSpec.integer("templateTries", "Template tries", 1, 256, 6))
                .param(ParamSpec.integer("frontierTries", "Frontier tries", 1, 256, 6))
                .param(ParamSpec.integer("frontierCap", "Frontier grid cap", 1000, 10000000, 400000))
                .param(ParamSpec.decimal("frontierSlack", "Frontier slack", 0.0, 10.0, 0.35))
                .param(ParamSpec.decimal("vxCap", "Cross-axis velocity cap", 0.0, 10.0, 0.13))
                .param(ParamSpec.decimal("closerEps0", "Closer epsilon start", 1.0e-6, 1.0, 1.0e-3))
                .param(ParamSpec.integer("perCandidateSec", "Per-candidate budget (s)", 1, 600, 120))
                .param(ParamSpec.text("closerRepairSigmas", "Closer repair sigmas", "0.3,1.0,3.0"))
                .param(ParamSpec.integer("closerRepairMaxEval", "Closer repair max evals", 100, 10000000, 60000))
                .param(ParamSpec.integer("closerMaxRungs", "Closer max rungs", 1, 1000, 90))
                .param(ParamSpec.integer("closerMaxRefines", "Closer max refines", 0, 100, 6))
                .param(ParamSpec.decimal("closerEpsFloor", "Closer epsilon floor", 0.0, 1.0, 2.0e-6))
                .param(ParamSpec.integer("closerDescentRounds", "Closer descent rounds", 1, 1000, 24))
                .param(ParamSpec.integer("closerDescentPairSpan", "Closer descent pair span", 1, 64, 3))
                .param(ParamSpec.integer("closerEntryRestarts", "Closer entry restarts", 1, 4096, 192))
                .param(ParamSpec.integer("closerEntryMaxEval", "Closer entry max evals", 500, 10000000, 100000))
                .param(ParamSpec.integer("closerEntryPolishCount", "Closer entry polish basins", 1, 64, 8))
                .advance()
                .budgetParam("budgetSec")
                .fallback(Guarantee.NONE)
                .factory(MomentumAssemblyNode::new)
                .build());
        register(NodeType.builder("freeStartImprove", "Free start improve", NodeCategory.RECOVERY)
                .requires(InputRequirement.ANY)
                .branch(Branch.unknown(Guarantee.IMPROVED))
                .branch(Branch.preserves(Guarantee.UNCHANGED))
                .param(ParamSpec.integer("budgetSec", "Budget (s)", 0, 600, 20))
                .param(ParamSpec.integer("iters", "Iterations", 1, 10, 3))
                .param(ParamSpec.decimal("sigmaDeg", "Sigma (deg)", 1.0, 360.0, 90.0))
                .param(ParamSpec.integer("fsMaxIters", "Translate iterations", 1, 100, 12))
                .param(ParamSpec.decimal("fsIntervalMargin", "Pin interval margin", 0.0, 1.0, 1.0e-3))
                .param(ParamSpec.decimal("fsInvariantTol", "Invariant slack tolerance", 0.0, 1.0, 1.0e-6))
                .param(ParamSpec.decimal("fsStepTol", "Fixed-point step tolerance", 0.0, 1.0, 1.0e-9))
                .param(ParamSpec.integer("fsSlpPhase1Calls", "Shape SLP phase-1 calls", 1, 10000, 40))
                .param(ParamSpec.integer("fsSlpTotalCalls", "Shape SLP total calls", 1, 10000, 60))
                .param(ParamSpec.text("fsJointMargins", "Joint margin ladder",
                        "0.0,1.0e-4,3.0e-4,6.0e-4,1.2e-3,2.5e-3,5.0e-3,1.0e-2"))
                .advance()
                .budgetParam("budgetSec")
                .fallback(Guarantee.UNCHANGED)
                .factory(FreeStartImproveNode::new)
                .build());
        register(NodeType.builder("bnb", "Pattern B&B", NodeCategory.RECOVERY)
                .requires(InputRequirement.ANY)
                .branch(Branch.feasible(Guarantee.FOUND))
                .branch(Branch.feasible(Guarantee.IMPROVED))
                .branch(Branch.preserves(Guarantee.UNCHANGED))
                .branch(Branch.preserves(Guarantee.NONE))
                .param(ParamSpec.choice("mode", "Mode", new String[] {"FIRST_FEASIBLE", "OPTIMIZE"}, "FIRST_FEASIBLE"))
                .param(ParamSpec.integer("budgetSec", "Budget (s)", 0, 600, 3))
                .param(ParamSpec.integer("minBudgetMs", "Minimum budget (ms)", 0, 600000, 0))
                .param(ParamSpec.text("labelSuffix", "Chain suffix", ""))
                .param(ParamSpec.decimal("searchShare", "Search share of budget", 0.1, 1.0, 0.8))
                .param(ParamSpec.decimal("pruneTol", "Prune tolerance", 0.0, 1.0, 1.0e-6))
                .param(ParamSpec.decimal("slpViolTrigger", "SLP trigger violation", 0.0, 10.0, 0.02))
                .param(ParamSpec.text("seedMargins", "Seed margin ladder", "3.0e-4,1.2e-3,5.0e-3,2.0e-2"))
                .param(ParamSpec.integer("maxPatterns", "Max patterns", 1, 256, 8))
                .param(ParamSpec.decimal("minSeamWidth", "Min seam width", 1.0e-4, 10.0, 0.04))
                .param(ParamSpec.integer("restoreIters", "Restore iterations", 1, 1000, 45))
                .param(ParamSpec.integer("treeSlpPhase1Calls", "Tree SLP phase-1 calls", 1, 10000, 40))
                .param(ParamSpec.integer("treeSlpTotalCalls", "Tree SLP total calls", 1, 10000, 60))
                .param(ParamSpec.decimal("treeSlpTrMinDeg", "Tree SLP trust floor (deg)", 0.0, 10.0, 1.0e-3))
                .param(ParamSpec.integer("polishSlpPhase1Calls", "Polish SLP phase-1 calls", 1, 10000, 160))
                .param(ParamSpec.integer("polishSlpTotalCalls", "Polish SLP total calls", 1, 10000, 220))
                .advance()
                .budgetParam("budgetSec")
                .fallback(Guarantee.UNCHANGED)
                .factory(BnbNode::new)
                .build());
        register(NodeType.builder("seamSweep", "Seam sweep", NodeCategory.RECOVERY)
                .requires(InputRequirement.FEASIBLE)
                .branch(Branch.feasible(Guarantee.IMPROVED))
                .branch(Branch.preserves(Guarantee.UNCHANGED))
                .param(ParamSpec.integer("budgetSec", "Budget (s)", 0, 600, 12))
                .param(ParamSpec.decimal("sweepPinHalf", "Sweep pin half-width", 1.0e-4, 10.0, 0.06))
                .param(ParamSpec.decimal("narrowPinHalf", "Narrow pin half-width", 1.0e-4, 10.0, 0.03))
                .param(ParamSpec.decimal("finePinHalf", "Fine pin half-width", 1.0e-4, 10.0, 0.015))
                .param(ParamSpec.decimal("beamPinHalf", "Beam pin half-width", 1.0e-4, 10.0, 0.1))
                .param(ParamSpec.decimal("holdPinHalf", "Hold pin half-width", 1.0e-4, 10.0, 0.10))
                .param(ParamSpec.integer("maxSeams", "Max seams", 1, 64, 5))
                .param(ParamSpec.integer("maxCells1d", "Cells (1 band)", 1, 256, 20))
                .param(ParamSpec.integer("maxCells2d", "Cells (2 bands)", 1, 256, 10))
                .param(ParamSpec.integer("narrowCells1d", "Narrow cells (1 band)", 1, 256, 28))
                .param(ParamSpec.integer("narrowCells2d", "Narrow cells (2 bands)", 1, 256, 14))
                .param(ParamSpec.integer("slpRescueCap", "SLP rescue cap", 0, 256, 6))
                .param(ParamSpec.integer("narrowSlpRescueCap", "Narrow SLP rescue cap", 0, 256, 8))
                .param(ParamSpec.integer("beamWidth", "Beam width", 1, 64, 3))
                .param(ParamSpec.integer("beamMaxCells", "Beam max cells", 1, 256, 8))
                .param(ParamSpec.integer("wideBeamWidth", "Wide beam width", 1, 64, 4))
                .param(ParamSpec.integer("wideBeamMaxCells", "Wide beam max cells", 1, 256, 12))
                .param(ParamSpec.integer("beamMaxSeams", "Beam max seams", 1, 64, 4))
                .param(ParamSpec.integer("beamSlpCap", "Beam SLP cap", 1, 256, 8))
                .param(ParamSpec.decimal("polishReserveFraction", "Polish reserve fraction", 0.0, 0.9, 0.2))
                .param(ParamSpec.decimal("longRunFraction", "Long-run slice fraction", 0.0, 1.0, 0.45))
                .advance()
                .budgetParam("budgetSec")
                .fallback(Guarantee.UNCHANGED)
                .factory(SeamSweepNode::new)
                .build());
        register(NodeType.builder("ilsPolish", "ILS polish", NodeCategory.POLISH)
                .requires(InputRequirement.FEASIBLE)
                .branch(Branch.feasible(Guarantee.IMPROVED))
                .branch(Branch.preserves(Guarantee.UNCHANGED))
                .param(ParamSpec.integer("budgetSec", "Budget (s)", 0, 600, 120))
                .param(ParamSpec.integer("roundCap", "Round cap", 1, 10000, 400))
                .param(ParamSpec.integer("perturbTicksMin", "Kick ticks min", 1, 100, 3))
                .param(ParamSpec.integer("perturbTicksSpan", "Kick ticks span", 1, 100, 13))
                .param(ParamSpec.decimal("perturbMagMin", "Kick magnitude min (deg)", 0.0, 360.0, 3.0))
                .param(ParamSpec.decimal("perturbMagSpan", "Kick magnitude span (deg)", 0.0, 360.0, 50.0))
                .param(ParamSpec.decimal("climbMuIneq", "Climb penalty mu (inequality)", 1.0, 1.0e12, 1.0e6))
                .param(ParamSpec.decimal("climbMuEq", "Climb penalty mu (equality)", 1.0, 1.0e12, 1.0e6))
                .param(ParamSpec.decimal("climbSigmaDeg", "Climb sigma (deg)", 0.1, 360.0, 4.0))
                .param(ParamSpec.integer("climbMaxEval", "Climb max evals", 100, 10000000, 80000))
                .advance()
                .budgetParam("budgetSec")
                .fallback(Guarantee.UNCHANGED)
                .factory(IlsPolishNode::new)
                .build());
        register(NodeType.builder("wrapIls", "Wrap ILS", NodeCategory.POLISH)
                .requires(InputRequirement.ANY)
                .branch(Branch.feasible(Guarantee.ADOPTED))
                .branch(Branch.preserves(Guarantee.REJECTED))
                .param(ParamSpec.integer("budgetSec", "Budget (s)", 0, 600, 120))
                .param(ParamSpec.integer("minRemainingSec", "Minimum budget (s)", 0, 60, 1))
                .param(ParamSpec.integer("span", "Cell span", 1, 512, 16))
                .param(ParamSpec.integer("maxSpan", "Max cell span", 1, 4096, 512))
                .param(ParamSpec.integer("candHighTarget", "High-norm candidates", 1, 64, 5))
                .param(ParamSpec.bool("kicks", "Random kicks", true))
                .param(ParamSpec.integer("evalCap", "Eval cap", 0, 1000000000, 0))
                .param(ParamSpec.integer("roundCap", "Round cap", 0, 100000, 0))
                .param(ParamSpec.bool("gateFlipMoves", "Gate flip moves", false))
                .advance()
                .budgetParam("budgetSec")
                .fallback(Guarantee.REJECTED)
                .factory(WrapIlsNode::new)
                .build());
        register(NodeType.builder("translatedStart", "Translated start", NodeCategory.RECOVERY)
                .requires(InputRequirement.ANY)
                .branch(Branch.preserves(Guarantee.DONE))
                .fallback(Guarantee.DONE)
                .factory(TranslatedStartNode::new)
                .build());
        register(NodeType.builder("smoothing", "Smoothing", NodeCategory.POLISH)
                .requires(InputRequirement.ANY)
                .branch(Branch.preserves(Guarantee.DONE))
                .param(ParamSpec.bool("countEvals", "Count evals", false))
                .param(ParamSpec.integer("maxRounds", "Max rounds", 1, 1000, 24))
                .param(ParamSpec.integer("maxEvals", "Max evals", 100, 10000000, 24000))
                .param(ParamSpec.integer("pairSpan", "Pair span", 1, 64, 3))
                .param(ParamSpec.text("fractions", "Pull fractions", "1.0,0.5,0.25,0.125"))
                .fallback(Guarantee.DONE)
                .factory(SmoothingNode::new)
                .build());
    }

    private static String[] predicateNames() {
        RouterPredicate[] all = RouterPredicate.values();
        String[] names = new String[all.length];
        for (int i = 0; i < all.length; i++) names[i] = all[i].name();
        return names;
    }

    private static void register(NodeType t) {
        TYPES.put(t.id, t);
    }

    public static NodeType byId(String id) {
        return TYPES.get(id);
    }

    public static List<NodeType> all() {
        return new ArrayList<>(TYPES.values());
    }
}
