package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.util.ArrayList;
import java.util.List;

public final class GraphBuilder {

    private final String name;
    private final boolean builtin;
    private final List<GraphNode> nodes = new ArrayList<>();
    private final List<GraphEdge> edges = new ArrayList<>();

    public GraphBuilder(String name, boolean builtin) {
        this.name = name;
        this.builtin = builtin;
    }

    public GraphBuilder add(String id, String typeId) {
        NodeType type = NodeCatalog.byId(typeId);
        if (type == null) throw new IllegalArgumentException("unknown node type: " + typeId);
        nodes.add(new GraphNode(id, type, type.defaultParams()));
        return this;
    }

    public GraphBuilder set(String nodeId, String paramKey, Object value) {
        node(nodeId).params.set(paramKey, value);
        return this;
    }

    public GraphBuilder edge(String from, Guarantee branch, String to) {
        edges.add(new GraphEdge(from, branch, to));
        return this;
    }

    public GraphBuilder chainAll(String from, String to) {
        GraphNode n = node(from);
        for (Branch b : n.type.branches) {
            edges.add(new GraphEdge(from, b.label, to));
        }
        return this;
    }

    private GraphNode node(String id) {
        for (GraphNode n : nodes) {
            if (n.id.equals(id)) return n;
        }
        throw new IllegalArgumentException("unknown node: " + id);
    }

    public SolverGraph build() {
        return new SolverGraph(name, builtin, nodes, edges);
    }
}
