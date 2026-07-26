package de.legoshi.parkourcalc.core.anglesolver.graph;

public final class ValidationIssue {

    public enum Severity {
        ERROR,
        WARN
    }

    public final Severity severity;
    public final String nodeId;
    public final int edgeIndex;
    public final String message;

    public ValidationIssue(Severity severity, String nodeId, int edgeIndex, String message) {
        this.severity = severity;
        this.nodeId = nodeId;
        this.edgeIndex = edgeIndex;
        this.message = message;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(severity.name());
        if (nodeId != null) sb.append(" [").append(nodeId).append("]");
        if (edgeIndex >= 0) sb.append(" edge#").append(edgeIndex);
        return sb.append(": ").append(message).toString();
    }
}
