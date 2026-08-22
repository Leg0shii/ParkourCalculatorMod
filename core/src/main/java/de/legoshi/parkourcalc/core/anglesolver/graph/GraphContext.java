package de.legoshi.parkourcalc.core.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveProgress;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class GraphContext {

    public final JumpSpec spec;
    public final JumpPhysicsInputs scenario;
    public final ForwardModel model;
    public final ExactJumpModel exactModel;
    public final StartBox freeBox;
    public final boolean freeStart;
    public final JumpConstraint legalGoal;
    public final double feasTol;
    public final AtomicBoolean cancel;
    public final SolveProgress progress;
    public final GraphRunState runState = new GraphRunState();
    public final boolean sequential;
    public final LongRunSolver.LongRunConfig longRun;
    public final AtomicLong smoothingEvals = new AtomicLong();

    private final BudgetWatchdog watchdog;
    private String chain;
    private volatile boolean stageLocked;
    private volatile boolean settled;
    private volatile double dualGap = Double.NaN;
    private volatile GraphNode currentNode;
    private volatile AtomicBoolean currentToken;
    private long evalsAtNodeStart;
    private int jumpCount = -1;
    private boolean reachBoundSet;
    private double reachBound = Double.NaN;
    private boolean headroomBoundSet;
    private double headroomBound = Double.NaN;
    private volatile long overallDeadlineNanos;

    public GraphContext(JumpSpec spec, ForwardModel model, StartBox freeBox, JumpConstraint legalGoal,
                        double feasTol, AtomicBoolean cancel, SolveProgress progress, boolean sequential,
                        LongRunSolver.LongRunConfig longRun) {
        this.spec = spec;
        this.scenario = spec.asScenario();
        this.model = model;
        this.exactModel = model instanceof ExactJumpModel ? (ExactJumpModel) model : null;
        this.freeBox = freeBox;
        this.freeStart = freeBox != null;
        this.legalGoal = legalGoal;
        this.feasTol = feasTol;
        this.cancel = cancel;
        this.progress = progress;
        this.sequential = sequential;
        this.longRun = longRun;
        this.watchdog = new BudgetWatchdog(cancel);
        if (progress != null) progress.setActiveNodeSource(runState::activeNodeId);
    }

    public boolean exact() {
        return exactModel != null;
    }

    public void setOverallDeadline(long absoluteNanos) {
        this.overallDeadlineNanos = absoluteNanos;
    }

    public long overallDeadline() {
        return overallDeadlineNanos;
    }

    public boolean maximize() {
        return spec.objective.sense == Objective.Sense.MAX;
    }

    public double violationOf(double[] yaws) {
        return Scoring.violationOf(model, scenario, spec, yaws);
    }

    public double exactObjective(double[] yaws) {
        return Scoring.exactObjective(model, scenario, spec, yaws);
    }

    public double scoredViol(double[] yaws) {
        return Scoring.scoredViol(model, scenario, spec, freeBox, yaws);
    }

    public double scoredObjective(double[] yaws) {
        return Scoring.scoredObjective(model, scenario, spec, freeBox, yaws);
    }

    public double scoredExactObjective(double[] yaws) {
        return spec.objective.scored(Scoring.exactObjective(model, scenario, spec, yaws), scenario.startYaw, yaws);
    }

    public synchronized String chain() {
        return chain;
    }

    public synchronized void chainAppend(String name) {
        chain = Scoring.chain(chain, name);
    }

    public synchronized void chainSuffix(String suffix) {
        chain = chain == null ? suffix.trim() : chain + suffix;
    }

    public synchronized String chainWith(String name) {
        return Scoring.chain(chain, name);
    }

    public boolean stageLocked() {
        return stageLocked;
    }

    public void setStageLocked(boolean locked) {
        this.stageLocked = locked;
    }

    public boolean settled() {
        return settled;
    }

    public void setSettled(boolean settled) {
        this.settled = settled;
    }

    public double dualGap() {
        return dualGap;
    }

    public void setDualGap(double gap) {
        this.dualGap = gap;
    }

    public synchronized int jumpCount() {
        if (jumpCount < 0) jumpCount = Scoring.countJumps(scenario);
        return jumpCount;
    }

    public synchronized double reachBound() {
        if (!reachBoundSet) {
            reachBound = exact() ? ClosedFormSolve.dualBound(spec) : Double.NaN;
            reachBoundSet = true;
        }
        return reachBound;
    }

    public synchronized double headroomBound() {
        if (!headroomBoundSet) {
            headroomBound = exact() ? ClosedFormSolve.dualBoundIgnoringFacing(spec) : Double.NaN;
            headroomBoundSet = true;
        }
        return headroomBound;
    }

    public AtomicBoolean beginNode(GraphNode node, long deadlineNanos, long budgetNanos) {
        currentNode = node;
        AtomicBoolean token = watchdog.arm(deadlineNanos);
        currentToken = token;
        evalsAtNodeStart = smoothingEvals.get();
        runState.begin(node.id, node.type.label, budgetNanos);
        return token;
    }

    public void endNode(GraphNode node, Guarantee taken) {
        watchdog.disarm();
        currentToken = null;
        currentNode = null;
        runState.end(node.id, taken, smoothingEvals.get() - evalsAtNodeStart);
    }

    void shutdown() {
        watchdog.shutdown();
    }
}
