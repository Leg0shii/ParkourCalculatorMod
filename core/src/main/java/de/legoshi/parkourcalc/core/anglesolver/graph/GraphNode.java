package de.legoshi.parkourcalc.core.anglesolver.graph;

public final class GraphNode {

    public final String id;
    public final NodeType type;
    public final ParamValues params;
    public float x;
    public float y;

    public GraphNode(String id, NodeType type, ParamValues params) {
        this.id = id;
        this.type = type;
        this.params = params;
    }
}
