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
        public long workNanos = -1L;
        public long[] workUnits;
        public double objective = Double.NaN;
        public double violation = Double.NaN;
        public double dualGap = Double.NaN;
        public boolean feasible;

        Step(String nodeId, String label) {
            this.nodeId = nodeId;
            this.label = label;
        }

        public Step copy() {
            Step s = new Step(nodeId, label);
            s.taken = taken;
            s.workNanos = workNanos;
            s.workUnits = workUnits;
            s.objective = objective;
            s.violation = violation;
            s.dualGap = dualGap;
            s.feasible = feasible;
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

    public synchronized void noteResult(String nodeId, long workNanos, long[] workUnits, Candidate cand) {
        if (steps.isEmpty()) return;
        Step last = steps.get(steps.size() - 1);
        if (!last.nodeId.equals(nodeId)) return;
        last.workNanos = workNanos;
        last.workUnits = workUnits;
        if (cand != null && cand.yaws != null) {
            last.objective = cand.objective;
            last.violation = cand.violation;
            last.dualGap = cand.dualGap;
            last.feasible = cand.feasible;
        }
    }

    public synchronized int stepCount() {
        return steps.size();
    }

    public int absorb(GraphRunState other, int at) {
        List<Step> otherSteps = other.steps();
        List<NodeStatus> otherStatuses = other.statuses();
        synchronized (this) {
            steps.addAll(Math.max(0, Math.min(at, steps.size())), otherSteps);
            for (NodeStatus st : otherStatuses) {
                if (!statuses.containsKey(st.nodeId)) statuses.put(st.nodeId, st);
            }
            version++;
        }
        return otherSteps.size();
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
