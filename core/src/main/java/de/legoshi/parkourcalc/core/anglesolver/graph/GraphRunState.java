package de.legoshi.parkourcalc.core.anglesolver.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GraphRunState {

    public static final class Step {

        public final String nodeId;
        public final String label;
        public Guarantee taken;

        Step(String nodeId, String label) {
            this.nodeId = nodeId;
            this.label = label;
        }

        public Step copy() {
            Step s = new Step(nodeId, label);
            s.taken = taken;
            return s;
        }
    }

    private final Map<String, NodeStatus> statuses = new LinkedHashMap<>();
    private final List<Step> steps = new ArrayList<>();
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
        steps.add(new Step(nodeId, label));
        version++;
    }

    public synchronized void end(String nodeId, Guarantee taken, long evalsDelta) {
        NodeStatus s = statuses.get(nodeId);
        if (s != null) {
            s.phase = NodeStatus.Phase.DONE;
            s.elapsedNanos = System.nanoTime() - s.startNanos;
            s.taken = taken;
            s.evals += evalsDelta;
        }
        if (!steps.isEmpty()) {
            Step last = steps.get(steps.size() - 1);
            if (last.nodeId.equals(nodeId)) last.taken = taken;
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

    public synchronized List<Step> steps() {
        List<Step> out = new ArrayList<>(steps.size());
        for (Step s : steps) out.add(s.copy());
        return out;
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
