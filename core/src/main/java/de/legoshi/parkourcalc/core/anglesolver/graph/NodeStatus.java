package de.legoshi.parkourcalc.core.anglesolver.graph;

public final class NodeStatus {

    public enum Phase {
        PENDING,
        RUNNING,
        DONE
    }

    public final String nodeId;
    public final String label;
    public Phase phase = Phase.PENDING;
    public Guarantee taken;
    public long startNanos;
    public long elapsedNanos;
    public long budgetNanos;
    public long evals;
    public int visits;

    NodeStatus(String nodeId, String label) {
        this.nodeId = nodeId;
        this.label = label;
    }

    public NodeStatus copy() {
        NodeStatus s = new NodeStatus(nodeId, label);
        s.phase = phase;
        s.taken = taken;
        s.startNanos = startNanos;
        s.elapsedNanos = elapsedNanos;
        s.budgetNanos = budgetNanos;
        s.evals = evals;
        s.visits = visits;
        return s;
    }
}
