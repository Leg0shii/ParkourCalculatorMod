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
                .param(ParamSpec.decimal("sigmaDeg", "Sigma (deg)", 1.0, 360.0, 90.0))
                .param(ParamSpec.bool("warmStart", "Warm start from candidate", false))
                .param(ParamSpec.integer("budgetSec", "Budget (s)", 0, 600, 0))
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
