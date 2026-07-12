package de.legoshi.parkourcalc.core.anglesolver.graph;

public final class GraphEdge {

    public final String fromNode;
    public final Guarantee branch;
    public final String toNode;

    public GraphEdge(String fromNode, Guarantee branch, String toNode) {
        this.fromNode = fromNode;
        this.branch = branch;
        this.toNode = toNode;
    }
}
