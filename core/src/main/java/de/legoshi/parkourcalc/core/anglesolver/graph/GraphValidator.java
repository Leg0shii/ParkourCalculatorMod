package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GraphValidator {

    private GraphValidator() {
    }

    public static boolean hasErrors(List<ValidationIssue> issues) {
        for (ValidationIssue i : issues) {
            if (i.severity == ValidationIssue.Severity.ERROR) return true;
        }
        return false;
    }

    public static List<ValidationIssue> validate(SolverGraph g) {
        List<ValidationIssue> out = new ArrayList<>();
        checkMarkers(g, out);
        checkDuplicateIds(g, out);
        checkEdges(g, out);
        checkReachability(g, out);
        checkUnwiredBranches(g, out);
        checkFeasibleRequires(g, out);
        checkUnguardedCycles(g, out);
        return out;
    }

    private static void checkMarkers(SolverGraph g, List<ValidationIssue> out) {
        int entries = 0;
        int emits = 0;
        for (GraphNode n : g.nodes) {
            if (n.type.entryMarker) entries++;
            if (n.type.emitMarker) emits++;
        }
        if (entries != 1) {
            out.add(new ValidationIssue(ValidationIssue.Severity.ERROR, null, -1,
                    "graph must have exactly one Entry node, found " + entries));
        }
        if (emits != 1) {
            out.add(new ValidationIssue(ValidationIssue.Severity.ERROR, null, -1,
                    "graph must have exactly one Emit node, found " + emits));
        }
    }

    private static void checkDuplicateIds(SolverGraph g, List<ValidationIssue> out) {
        Set<String> seen = new HashSet<>();
        for (GraphNode n : g.nodes) {
            if (!seen.add(n.id)) {
                out.add(new ValidationIssue(ValidationIssue.Severity.ERROR, n.id, -1,
                        "duplicate node id"));
            }
        }
    }

    private static void checkEdges(SolverGraph g, List<ValidationIssue> out) {
        Set<String> wired = new HashSet<>();
        for (int i = 0; i < g.edges.size(); i++) {
            GraphEdge e = g.edges.get(i);
            GraphNode from = g.node(e.fromNode);
            GraphNode to = g.node(e.toNode);
            if (from == null) {
                out.add(new ValidationIssue(ValidationIssue.Severity.ERROR, e.fromNode, i,
                        "edge from unknown node"));
                continue;
            }
            if (to == null) {
                out.add(new ValidationIssue(ValidationIssue.Severity.ERROR, e.toNode, i,
                        "edge to unknown node"));
                continue;
            }
            if (from.type.branch(e.branch) == null) {
                out.add(new ValidationIssue(ValidationIssue.Severity.ERROR, from.id, i,
                        "node type " + from.type.id + " has no branch " + e.branch));
            }
            if (to.type.entryMarker) {
                out.add(new ValidationIssue(ValidationIssue.Severity.ERROR, to.id, i,
                        "edge into Entry is not allowed"));
            }
            if (!wired.add(e.fromNode + "#" + e.branch)) {
                out.add(new ValidationIssue(ValidationIssue.Severity.ERROR, from.id, i,
                        "branch " + e.branch + " is wired more than once"));
            }
        }
    }

    private static void checkReachability(SolverGraph g, List<ValidationIssue> out) {
        if (g.entry == null) return;
        Set<String> reached = new HashSet<>();
        Deque<String> work = new ArrayDeque<>();
        reached.add(g.entry.id);
        work.push(g.entry.id);
        while (!work.isEmpty()) {
            String id = work.pop();
            for (GraphEdge e : g.edges) {
                if (!e.fromNode.equals(id)) continue;
                if (g.node(e.toNode) != null && reached.add(e.toNode)) work.push(e.toNode);
            }
        }
        for (GraphNode n : g.nodes) {
            if (n.type.emitMarker) continue;
            if (!reached.contains(n.id)) {
                out.add(new ValidationIssue(ValidationIssue.Severity.WARN, n.id, -1,
                        "node is unreachable from Entry"));
            }
        }
    }

    private static void checkUnwiredBranches(SolverGraph g, List<ValidationIssue> out) {
        for (GraphNode n : g.nodes) {
            if (n.type.emitMarker) continue;
            for (Branch b : n.type.branches) {
                if (g.edgeFor(n.id, b.label) == null) {
                    out.add(new ValidationIssue(ValidationIssue.Severity.WARN, n.id, -1,
                            "branch " + b.label + " is unwired and falls through to Emit"));
                }
            }
        }
    }

    private static void checkFeasibleRequires(SolverGraph g, List<ValidationIssue> out) {
        Map<String, Boolean> feasIn = new HashMap<>();
        for (GraphNode n : g.nodes) feasIn.put(n.id, Boolean.FALSE);
        boolean changed = true;
        int guard = g.nodes.size() + 2;
        while (changed && guard-- > 0) {
            changed = false;
            for (GraphNode n : g.nodes) {
                if (n.type.entryMarker) continue;
                boolean any = false;
                boolean all = true;
                for (GraphEdge e : g.edges) {
                    if (!e.toNode.equals(n.id)) continue;
                    any = true;
                    all &= edgeFeasible(g, e, feasIn);
                }
                boolean v = any && all;
                if (v != feasIn.get(n.id)) {
                    feasIn.put(n.id, v);
                    changed = true;
                }
            }
        }
        for (int i = 0; i < g.edges.size(); i++) {
            GraphEdge e = g.edges.get(i);
            GraphNode to = g.node(e.toNode);
            if (to == null || to.type.requires != InputRequirement.FEASIBLE) continue;
            if (!edgeFeasible(g, e, feasIn)) {
                out.add(new ValidationIssue(ValidationIssue.Severity.ERROR, to.id, i,
                        "node requires a FEASIBLE candidate but branch " + e.branch + " of "
                                + e.fromNode + " does not guarantee one"));
            }
        }
    }

    private static boolean edgeFeasible(SolverGraph g, GraphEdge e, Map<String, Boolean> feasIn) {
        GraphNode from = g.node(e.fromNode);
        if (from == null) return false;
        Branch br = from.type.branch(e.branch);
        if (br == null) return false;
        switch (br.feas) {
            case FEASIBLE:
                return true;
            case UNKNOWN:
                return false;
            default:
                break;
        }
        if (isFeasibilityRouter(from)) {
            if (e.branch == Guarantee.TRUE) return true;
            if (e.branch == Guarantee.FALSE) return false;
        }
        Boolean in = feasIn.get(from.id);
        return in != null && in;
    }

    private static boolean isFeasibilityRouter(GraphNode n) {
        if (!"router".equals(n.type.id)) return false;
        String p = n.params.getString("predicate");
        if (p == null) return false;
        try {
            return RouterPredicate.valueOf(p).feasibilityRefining();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void checkUnguardedCycles(SolverGraph g, List<ValidationIssue> out) {
        Set<String> unguarded = new HashSet<>();
        for (GraphNode n : g.nodes) {
            if (n.type.emitMarker || n.type.entryMarker) continue;
            if (!n.type.budgetGuarded(n.params)) unguarded.add(n.id);
        }
        Map<String, Integer> color = new HashMap<>();
        for (String id : unguarded) color.put(id, 0);
        for (String id : unguarded) {
            if (color.get(id) == 0) {
                List<String> cycle = dfsCycle(g, id, unguarded, color, new ArrayList<String>());
                if (cycle != null) {
                    out.add(new ValidationIssue(ValidationIssue.Severity.ERROR, cycle.get(0), -1,
                            "unguarded loop through nodes " + cycle
                                    + "; every cycle must pass a node with a positive time budget"));
                    return;
                }
            }
        }
    }

    private static List<String> dfsCycle(SolverGraph g, String id, Set<String> scope,
                                         Map<String, Integer> color, List<String> stack) {
        color.put(id, 1);
        stack.add(id);
        for (GraphEdge e : g.edges) {
            if (!e.fromNode.equals(id) || !scope.contains(e.toNode)) continue;
            Integer c = color.get(e.toNode);
            if (c != null && c == 1) {
                List<String> cycle = new ArrayList<>(stack.subList(stack.indexOf(e.toNode), stack.size()));
                return cycle;
            }
            if (c != null && c == 0) {
                List<String> found = dfsCycle(g, e.toNode, scope, color, stack);
                if (found != null) return found;
            }
        }
        color.put(id, 2);
        stack.remove(stack.size() - 1);
        return null;
    }
}
