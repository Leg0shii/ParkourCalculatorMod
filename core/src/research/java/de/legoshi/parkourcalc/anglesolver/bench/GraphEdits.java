package de.legoshi.parkourcalc.anglesolver.bench;

import de.legoshi.parkourcalc.core.anglesolver.graph.GraphEdge;
import de.legoshi.parkourcalc.core.anglesolver.graph.GraphNode;
import de.legoshi.parkourcalc.core.anglesolver.graph.Guarantee;
import de.legoshi.parkourcalc.core.anglesolver.graph.ParamSpec;
import de.legoshi.parkourcalc.core.anglesolver.graph.SolverGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GraphEdits {

    private GraphEdits() {
    }

    public static SolverGraph apply(SolverGraph base, String edits) {
        if (edits == null || edits.trim().isEmpty()) return base;
        List<GraphNode> nodes = new ArrayList<GraphNode>();
        for (GraphNode n : base.nodes) nodes.add(new GraphNode(n.id, n.type, n.params.copy()));
        List<GraphEdge> edges = new ArrayList<GraphEdge>(base.edges);
        for (String raw : edits.split(";")) {
            String edit = raw.trim();
            if (edit.isEmpty()) continue;
            if (edit.startsWith("drop:")) {
                edges = drop(nodes, edges, edit.substring(5).trim());
            } else if (edit.startsWith("set:")) {
                set(nodes, edit.substring(4).trim());
            } else {
                throw new IllegalArgumentException("unknown graph edit '" + edit + "', use drop:<node> or set:<node>.<param>=<value>");
            }
        }
        return new SolverGraph(base.name, base.builtin, nodes, edges);
    }

    private static List<GraphEdge> drop(List<GraphNode> nodes, List<GraphEdge> edges, String spec) {
        String id = spec;
        Guarantee via = null;
        int at = spec.indexOf('@');
        if (at >= 0) {
            id = spec.substring(0, at);
            via = Guarantee.valueOf(spec.substring(at + 1));
        }
        GraphNode node = find(nodes, id);
        Map<String, Integer> targets = new HashMap<String, Integer>();
        String successor = null;
        for (GraphEdge e : edges) {
            if (!e.fromNode.equals(id)) continue;
            if (via != null && e.branch == via) successor = e.toNode;
            Integer c = targets.get(e.toNode);
            targets.put(e.toNode, c == null ? 1 : c + 1);
        }
        if (successor == null) {
            if (via != null) throw new IllegalArgumentException("node " + id + " has no " + via + " edge");
            if (targets.size() != 1) {
                throw new IllegalArgumentException("node " + id + " has " + targets.size() + " successors, name one with drop:" + id + "@<branch>");
            }
            successor = targets.keySet().iterator().next();
        }
        List<GraphEdge> out = new ArrayList<GraphEdge>();
        for (GraphEdge e : edges) {
            if (e.fromNode.equals(id)) continue;
            if (e.toNode.equals(id)) out.add(new GraphEdge(e.fromNode, e.branch, successor));
            else out.add(e);
        }
        nodes.remove(node);
        return out;
    }

    private static void set(List<GraphNode> nodes, String spec) {
        int eq = spec.indexOf('=');
        int dot = spec.indexOf('.');
        if (eq < 0 || dot < 0 || dot > eq) throw new IllegalArgumentException("set edit needs <node>.<param>=<value>: " + spec);
        GraphNode node = find(nodes, spec.substring(0, dot));
        String key = spec.substring(dot + 1, eq);
        String value = spec.substring(eq + 1);
        for (ParamSpec p : node.type.params) {
            if (!p.key.equals(key)) continue;
            switch (p.kind) {
                case INT:
                    node.params.set(key, Integer.valueOf(value));
                    if (node.params.getInt(key) != Integer.parseInt(value)) throw clamped(spec, String.valueOf(node.params.getInt(key)));
                    return;
                case DOUBLE:
                    node.params.set(key, Double.valueOf(value));
                    if (node.params.getDouble(key) != Double.parseDouble(value)) throw clamped(spec, String.valueOf(node.params.getDouble(key)));
                    return;
                case BOOL:
                    node.params.set(key, Boolean.valueOf(value));
                    return;
                default:
                    node.params.set(key, value);
                    if (!value.equals(node.params.getString(key))) throw clamped(spec, node.params.getString(key));
                    return;
            }
        }
        throw new IllegalArgumentException("node " + node.id + " (" + node.type.id + ") has no param " + key);
    }

    private static IllegalArgumentException clamped(String spec, String kept) {
        return new IllegalArgumentException("graph edit " + spec + " is outside the param's range, the node kept " + kept);
    }

    private static GraphNode find(List<GraphNode> nodes, String id) {
        for (GraphNode n : nodes) {
            if (n.id.equals(id)) return n;
        }
        throw new IllegalArgumentException("unknown node: " + id);
    }
}
