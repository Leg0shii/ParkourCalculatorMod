package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GraphRunState {

    private final Map<String, NodeStatus> statuses = new LinkedHashMap<>();
    private final List<String> breadcrumb = new ArrayList<>();
    private String activeNodeId;
    private int version;

    public synchronized void begin(String nodeId, String label, long budgetNanos) {
        NodeStatus s = statuses.get(nodeId);
        if (s == null) {
            s = new NodeStatus(nodeId, label);
            statuses.put(nodeId, s);
        }
        s.phase = NodeStatus.Phase.RUNNING;
        s.startNanos = System.nanoTime();
        s.elapsedNanos = 0L;
        s.budgetNanos = budgetNanos;
        s.visits++;
        activeNodeId = nodeId;
        breadcrumb.add(label);
        version++;
    }

    public synchronized void end(String nodeId, Guarantee taken) {
        NodeStatus s = statuses.get(nodeId);
        if (s != null) {
            s.phase = NodeStatus.Phase.DONE;
            s.elapsedNanos = System.nanoTime() - s.startNanos;
            s.taken = taken;
        }
        if (nodeId.equals(activeNodeId)) activeNodeId = null;
        version++;
    }

    public synchronized int version() {
        return version;
    }

    public synchronized String activeNodeId() {
        return activeNodeId;
    }

    public synchronized List<String> breadcrumb() {
        return new ArrayList<>(breadcrumb);
    }

    public synchronized List<NodeStatus> statuses() {
        List<NodeStatus> out = new ArrayList<>(statuses.size());
        for (NodeStatus s : statuses.values()) out.add(s.copy());
        return out;
    }

    public synchronized NodeStatus status(String nodeId) {
        NodeStatus s = statuses.get(nodeId);
        return s == null ? null : s.copy();
    }
}
