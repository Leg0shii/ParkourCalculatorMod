package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SolverGraph {

    public final String name;
    public final boolean builtin;
    public final List<GraphNode> nodes;
    public final List<GraphEdge> edges;
    public final GraphNode entry;
    public final GraphNode emit;

    private final Map<String, GraphNode> byId = new HashMap<>();
    private final Map<String, Map<Guarantee, GraphEdge>> out = new HashMap<>();

    public SolverGraph(String name, boolean builtin, List<GraphNode> nodes, List<GraphEdge> edges) {
        this.name = name;
        this.builtin = builtin;
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.edges = Collections.unmodifiableList(new ArrayList<>(edges));
        GraphNode en = null;
        GraphNode em = null;
        for (GraphNode n : this.nodes) {
            if (!byId.containsKey(n.id)) byId.put(n.id, n);
            if (n.type.entryMarker && en == null) en = n;
            if (n.type.emitMarker && em == null) em = n;
        }
        this.entry = en;
        this.emit = em;
        for (GraphEdge e : this.edges) {
            Map<Guarantee, GraphEdge> m = out.get(e.fromNode);
            if (m == null) {
                m = new EnumMap<>(Guarantee.class);
                out.put(e.fromNode, m);
            }
            if (!m.containsKey(e.branch)) m.put(e.branch, e);
        }
    }

    public GraphNode node(String id) {
        return byId.get(id);
    }

    public GraphEdge edgeFor(String nodeId, Guarantee branch) {
        Map<Guarantee, GraphEdge> m = out.get(nodeId);
        return m == null ? null : m.get(branch);
    }
}
