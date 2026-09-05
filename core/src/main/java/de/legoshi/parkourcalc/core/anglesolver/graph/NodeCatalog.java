package de.legoshi.parkourcalc.core.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.BnbNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.CapCertifyNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.CertifiedBnbNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.DualChainNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.FoldDriverNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.FreeStartImproveNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.HomotopyLadderNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.IlsPolishNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.LabelNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.LeafSnapNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.MarkSettledNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.ReportNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.RecedingHorizonNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.RouterNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.nodes.SetupPeelNode;
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
                .param(ParamSpec.integer("cap", "Tick cap", 0, 100000, BuiltinGraphs.IMPROVE_TICK_CAP))
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
                .param(ParamSpec.integer("budgetSec", "Max time (s)", 0, 600, 0))
                .param(ParamSpec.integer("budgetMs", "Max time (ms)", 0, 600000, 0))
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
                .param(ParamSpec.integer("warmSec", "Warm improve (s)", 0, 600, 0))
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
                .param(ParamSpec.integer("budgetSec", "Max time (s)", 1, 600, 12))
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
        register(NodeType.builder("freeStartImprove", "Free start improve", NodeCategory.RECOVERY)
                .requires(InputRequirement.ANY)
                .branch(Branch.unknown(Guarantee.IMPROVED))
                .branch(Branch.preserves(Guarantee.UNCHANGED))
                .param(ParamSpec.integer("budgetSec", "Max time (s)", 0, 600, 20))
                .param(ParamSpec.bool("jointOnly", "Joint rescue only", false))
                .param(ParamSpec.decimal("fsIntervalMargin", "Pin interval margin", 0.0, 1.0, 1.0e-3))
                .param(ParamSpec.decimal("fsInvariantTol", "Invariant slack tolerance", 0.0, 1.0, 1.0e-6))
                .param(ParamSpec.text("fsJointMargins", "Joint margin ladder",
                        "0.0,1.0e-4,3.0e-4,6.0e-4,1.2e-3,2.5e-3,5.0e-3,1.0e-2"))
                .param(ParamSpec.integer("warmSec", "Warm improve (s)", 0, 600, 0))
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
                .param(ParamSpec.integer("budgetSec", "Budget (s)", 0, 600, 0))
                .param(ParamSpec.integer("ffSec", "First-feasible budget (s)", 0, 600, 3))
                .param(ParamSpec.integer("tickCap", "Tick cap", 0, 100000, BuiltinGraphs.IMPROVE_TICK_CAP))
                .param(ParamSpec.text("labelSuffix", "Chain suffix", ""))
                .budgetParam("budgetSec")
                .fallback(Guarantee.UNCHANGED)
                .factory(BnbNode::new)
                .build());
        register(NodeType.builder("certBnb", "Certified B&B", NodeCategory.RECOVERY)
                .requires(InputRequirement.ANY)
                .branch(Branch.feasible(Guarantee.FOUND))
                .branch(Branch.feasible(Guarantee.IMPROVED))
                .branch(Branch.preserves(Guarantee.UNCHANGED))
                .branch(Branch.preserves(Guarantee.NONE))
                .param(ParamSpec.integer("budgetSec", "Budget (s)", 0, 600, 0))
                .param(ParamSpec.integer("ffSec", "First-feasible budget (s)", 0, 600, 3))
                .param(ParamSpec.integer("ffNodeCap", "First-feasible node cap", 0, 1000000, 32))
                .param(ParamSpec.integer("tickCap", "Tick cap", 0, 100000, BuiltinGraphs.IMPROVE_TICK_CAP))
                .param(ParamSpec.text("labelSuffix", "Chain suffix", ""))
                .budgetParam("budgetSec")
                .fallback(Guarantee.UNCHANGED)
                .factory(CertifiedBnbNode::new)
                .build());
        register(NodeType.builder("foldDriver", "Fold driver", NodeCategory.RECOVERY)
                .requires(InputRequirement.ANY)
                .branch(Branch.feasible(Guarantee.FOUND))
                .branch(Branch.feasible(Guarantee.IMPROVED))
                .branch(Branch.preserves(Guarantee.UNCHANGED))
                .branch(Branch.preserves(Guarantee.NONE))
                .param(ParamSpec.integer("budgetSec", "Max time (s)", 0, 600, 0))
                .param(ParamSpec.integer("objectiveRounds", "Objective rounds", 0, 64, 0))
                .param(ParamSpec.integer("multiStart", "Multi-start count", 0, 5, 0))
                .param(ParamSpec.integer("ascentMs", "Ascent budget (ms)", 0, 600000, 0))
                .param(ParamSpec.integer("tickCap", "Tick cap", 0, 100000, BuiltinGraphs.IMPROVE_TICK_CAP))
                .param(ParamSpec.text("labelSuffix", "Chain suffix", ""))
                .budgetParam("budgetSec")
                .fallback(Guarantee.NONE)
                .factory(FoldDriverNode::new)
                .build());
        register(NodeType.builder("homotopyLadder", "Homotopy ladder", NodeCategory.RECOVERY)
                .requires(InputRequirement.ANY)
                .branch(Branch.feasible(Guarantee.FOUND))
                .branch(Branch.preserves(Guarantee.NONE))
                .param(ParamSpec.integer("budgetSec", "Max time (s)", 0, 600, 0))
                .param(ParamSpec.integer("cap", "Tick cap", 0, 100000, BuiltinGraphs.IMPROVE_TICK_CAP))
                .budgetParam("budgetSec")
                .fallback(Guarantee.NONE)
                .factory(HomotopyLadderNode::new)
                .build());
        register(NodeType.builder("ilsPolish", "ILS polish", NodeCategory.POLISH)
                .requires(InputRequirement.ANY)
                .branch(Branch.feasible(Guarantee.IMPROVED))
                .branch(Branch.preserves(Guarantee.UNCHANGED))
                .param(ParamSpec.integer("budgetSec", "Budget (s)", 0, 600, 120))
                .param(ParamSpec.integer("perturbTicksMin", "Kick ticks min", 1, 100, 3))
                .param(ParamSpec.integer("perturbTicksSpan", "Kick ticks span", 1, 100, 13))
                .param(ParamSpec.decimal("perturbMagMin", "Kick magnitude min (deg)", 0.0, 360.0, 3.0))
                .param(ParamSpec.decimal("perturbMagSpan", "Kick magnitude span (deg)", 0.0, 360.0, 50.0))
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
                .param(ParamSpec.integer("maxAbsGf", "Max |facing| (deg)", 360, 100000, 12000))
                .budgetParam("budgetSec")
                .fallback(Guarantee.REJECTED)
                .factory(WrapIlsNode::new)
                .build());
        register(NodeType.builder("leafSnap", "Leaf snap", NodeCategory.POLISH)
                .requires(InputRequirement.ANY)
                .branch(Branch.feasible(Guarantee.ADOPTED))
                .branch(Branch.preserves(Guarantee.REJECTED))
                .param(ParamSpec.integer("pairPass", "Pair pass", 0, 1, 0))
                .fallback(Guarantee.REJECTED)
                .factory(LeafSnapNode::new)
                .build());
        register(NodeType.builder("translatedStart", "Translated start", NodeCategory.RECOVERY)
                .requires(InputRequirement.ANY)
                .branch(Branch.preserves(Guarantee.DONE))
                .fallback(Guarantee.DONE)
                .factory(TranslatedStartNode::new)
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
