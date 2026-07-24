package de.legoshi.parkourcalc.core.anglesolver.graph;

public final class NodeOutcome {

    public final Guarantee branch;
    public final Candidate candidate;

    private NodeOutcome(Guarantee branch, Candidate candidate) {
        this.branch = branch;
        this.candidate = candidate;
    }

    public static NodeOutcome of(Guarantee branch, Candidate candidate) {
        return new NodeOutcome(branch, candidate);
    }
}
