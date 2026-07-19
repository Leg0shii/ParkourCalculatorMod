package de.legoshi.parkourcalc.core.anglesolver.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class SolveProgress {

    public static final class Sample {

        public final long elapsedNanos;
        public final double objective;
        public final double violation;
        public final boolean feasible;
        public final String stage;
        public final String node;

        Sample(long elapsedNanos, double objective, double violation, boolean feasible, String stage, String node) {
            this.elapsedNanos = elapsedNanos;
            this.objective = objective;
            this.violation = violation;
            this.feasible = feasible;
            this.stage = stage;
            this.node = node;
        }
    }

    private final boolean maximize;
    private final boolean stopOnFeasible;
    private final long startNanos = System.nanoTime();
    private final List<Sample> samples = new ArrayList<>();

    private double[] bestYaws;
    private double bestObjective;
    private double bestViolation;
    private boolean bestFeasible;
    private boolean haveBest;
    private int version;
    private String stage;
    private String bestSolver;
    private Supplier<String> activeNodeSource;
    private SolveProgress forwardTarget;
    private String forwardNode;

    public SolveProgress(boolean maximize, boolean stopOnFeasible) {
        this.maximize = maximize;
        this.stopOnFeasible = stopOnFeasible;
    }

    public synchronized void setActiveNodeSource(Supplier<String> source) {
        this.activeNodeSource = source;
    }

    public synchronized void forwardTo(SolveProgress target, String nodeLabel) {
        this.forwardTarget = target;
        this.forwardNode = nodeLabel;
    }

    public boolean stopOnFeasible() {
        return stopOnFeasible;
    }

    public synchronized void setStage(String stage) {
        this.stage = stage;
    }

    public synchronized String bestSolver() {
        return bestSolver;
    }

    public synchronized void report(double[] absWrappedYaws, double objective, double violation, boolean feasible) {
        if (absWrappedYaws == null) return;
        String node = activeNodeSource != null ? activeNodeSource.get() : null;
        boolean accepted = accept(absWrappedYaws, objective, violation, feasible, stage, node);
        if (accepted && forwardTarget != null) {
            forwardTarget.reportForwarded(absWrappedYaws, objective, violation, feasible, stage, forwardNode);
        }
    }

    synchronized void reportForwarded(double[] yaws, double objective, double violation, boolean feasible,
                                      String fromStage, String node) {
        if (yaws == null) return;
        accept(yaws, objective, violation, feasible, fromStage, node);
    }

    private boolean accept(double[] yaws, double objective, double violation, boolean feasible,
                           String fromStage, String node) {
        if (haveBest && !isBetter(feasible, objective, violation)) return false;
        bestYaws = yaws.clone();
        bestObjective = objective;
        bestViolation = violation;
        bestFeasible = feasible;
        bestSolver = fromStage;
        haveBest = true;
        version++;
        samples.add(new Sample(System.nanoTime() - startNanos, objective, violation, feasible, fromStage, node));
        return true;
    }

    public synchronized List<Sample> samples() {
        return new ArrayList<>(samples);
    }

    public synchronized int version() {
        return version;
    }

    private boolean isBetter(boolean feasible, double objective, double violation) {
        if (feasible != bestFeasible) return feasible;
        return maximize ? objective > bestObjective : objective < bestObjective;
    }

    public synchronized boolean haveBest() {
        return haveBest;
    }

    public synchronized boolean isBestFeasible() {
        return bestFeasible;
    }

    public synchronized double bestObjective() {
        return bestObjective;
    }

    public synchronized double bestViolation() {
        return bestViolation;
    }

    public synchronized double[] bestYaws() {
        return bestYaws == null ? null : bestYaws.clone();
    }
}
