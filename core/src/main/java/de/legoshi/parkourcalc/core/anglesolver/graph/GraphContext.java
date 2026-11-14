package de.legoshi.parkourcalc.core.anglesolver.graph;

import de.legoshi.parkourcalc.core.anglesolver.solver.CertifiedBnb;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosedFormSolve;
import de.legoshi.parkourcalc.core.anglesolver.solver.ClosestMiss;
import de.legoshi.parkourcalc.core.anglesolver.solver.ExactJumpModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.ForwardModel;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpConstraint;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpPhysicsInputs;
import de.legoshi.parkourcalc.core.anglesolver.solver.JumpSpec;
import de.legoshi.parkourcalc.core.anglesolver.solver.LongRunSolver;
import de.legoshi.parkourcalc.core.anglesolver.solver.Objective;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveClock;
import de.legoshi.parkourcalc.core.anglesolver.solver.SolveProgress;
import de.legoshi.parkourcalc.core.anglesolver.solver.StartBox;
import de.legoshi.parkourcalc.core.sim.Vec3dCore;

import java.util.ArrayList;
import java.util.List;
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
    final SolveProgress laneProgress;
    public final GraphRunState runState = new GraphRunState();
    public final boolean sequential;
    public final LongRunSolver.LongRunConfig longRun;
    public final AtomicLong smoothingEvals = new AtomicLong();
    private final ClosestMiss closestMiss = new ClosestMiss();

    private final BudgetWatchdog watchdog;
    private String chain;
    private volatile boolean stageLocked;
    private volatile boolean settled;
    private volatile boolean optimumCertified;
    private volatile GraphNode currentNode;
    private volatile AtomicBoolean currentToken;
    private long evalsAtNodeStart;
    private int jumpCount = -1;
    private int foldableDf = -1;
    private boolean reachBoundSet;
    private double reachBound = Double.NaN;
    private double provenBound = Double.NaN;
    private boolean headroomBoundSet;
    private double headroomBound = Double.NaN;
    private volatile long overallDeadlineNanos;
    private volatile SmoothOutcome smoothOutcome;
    private volatile InputOutcome inputOutcome;
    private volatile String infeasibilityProof;
    private final List<GraphLane> lanes = new ArrayList<>();
    private Candidate heldStart;
    private Vec3dCore heldStartPos;
    private StartBox heldStartBox;

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
        this.laneProgress = null;
        this.sequential = sequential;
        this.longRun = longRun;
        this.watchdog = new BudgetWatchdog(cancel);
        if (progress != null) progress.setActiveNodeSource(runState::activeNodeId);
    }

    private GraphContext(GraphContext parent) {
        this.spec = parent.spec;
        this.scenario = parent.scenario.copy();
        this.model = parent.model;
        this.exactModel = parent.exactModel;
        this.freeBox = parent.freeBox;
        this.freeStart = parent.freeStart;
        this.legalGoal = parent.legalGoal;
        this.feasTol = parent.feasTol;
        this.cancel = parent.cancel;
        this.progress = null;
        this.laneProgress = parent.progress != null ? parent.progress : parent.laneProgress;
        this.sequential = parent.sequential;
        this.longRun = parent.longRun;
        this.watchdog = new BudgetWatchdog(parent.cancel);
        this.chain = parent.chain();
        this.stageLocked = parent.stageLocked;
        this.settled = parent.settled;
        this.optimumCertified = parent.optimumCertified;
        this.overallDeadlineNanos = parent.overallDeadlineNanos;
        this.dualGapRequested = parent.dualGapRequested;
    }

    public boolean exact() {
        return exactModel != null;
    }

    public ClosestMiss closestMiss() {
        return closestMiss;
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

    public String infeasibilityProof() {
        return infeasibilityProof;
    }

    public void certifyInfeasible(String proof) {
        if (infeasibilityProof == null) infeasibilityProof = proof;
    }

    public SmoothOutcome smoothOutcome() {
        return smoothOutcome;
    }

    public void setSmoothOutcome(SmoothOutcome outcome) {
        this.smoothOutcome = outcome;
    }

    public InputOutcome inputOutcome() {
        return inputOutcome;
    }

    public void setInputOutcome(InputOutcome outcome) {
        this.inputOutcome = outcome;
    }

    public boolean stageLocked() {
        return stageLocked;
    }

    public void setStageLocked(boolean locked) {
        this.stageLocked = locked;
    }

    private volatile boolean dualGapRequested;

    public boolean dualGapRequested() {
        return dualGapRequested;
    }

    public void requestDualGap() {
        dualGapRequested = true;
    }

    public synchronized void holdStart(Candidate candidate) {
        heldStart = candidate;
        heldStartPos = scenario.startPos;
        heldStartBox = scenario.startBox;
    }

    public synchronized Candidate heldStart() {
        return heldStart;
    }

    public synchronized void dropHeldStart() {
        heldStart = null;
        heldStartPos = null;
        heldStartBox = null;
    }

    public synchronized Candidate restoreHeldStart() {
        Candidate held = heldStart;
        if (held == null) return null;
        scenario.startPos = heldStartPos;
        scenario.startBox = heldStartBox;
        dropHeldStart();
        return held;
    }

    public boolean settled() {
        return settled;
    }

    public boolean optimumCertified() {
        return optimumCertified;
    }

    public void certifyOptimum() {
        optimumCertified = true;
    }

    public synchronized void offerProvenBound(double bound) {
        if (Double.isNaN(bound) || Double.isInfinite(bound)) return;
        if (Double.isNaN(provenBound) || (maximize() ? bound < provenBound : bound > provenBound)) {
            provenBound = bound;
        }
    }

    public synchronized double provenBound() {
        return provenBound;
    }

    public void setSettled(boolean settled) {
        this.settled = settled;
    }

    public synchronized int jumpCount() {
        if (jumpCount < 0) jumpCount = Scoring.countJumps(scenario);
        return jumpCount;
    }

    public synchronized boolean foldableDf() {
        if (foldableDf < 0) foldableDf = exact() && CertifiedBnb.foldable(spec) ? 1 : 0;
        return foldableDf == 1;
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

    void startLane(GraphRunner.Walk walk, GraphNode start, Candidate in) {
        GraphContext laneCtx = new GraphContext(this);
        SolveClock clock = SolveClock.current();
        SolveClock fork = clock == null ? null : clock.forkLane();
        GraphLane lane = new GraphLane(walk, start, laneCtx, in, fork, runState.stepCount());
        lanes.add(lane);
        if (sequential) lane.run();
        else lane.start();
    }

    volatile boolean laneAborted;

    void abortCurrentNode() {
        laneAborted = true;
        AtomicBoolean token = currentToken;
        if (token != null) token.set(true);
    }

    boolean hasLane(String nodeId) {
        for (GraphLane lane : lanes) {
            if (lane.start.id.equals(nodeId)) return true;
        }
        return false;
    }

    public Candidate joinLanes(Candidate current) {
        return joinLanes(current, null);
    }

    public Candidate joinLanes(Candidate current, String startedAt) {
        Candidate cur = current;
        int at = 0;
        while (at < lanes.size()) {
            GraphLane lane = lanes.get(at);
            if (startedAt != null && !startedAt.isEmpty() && !startedAt.equals(lane.start.id)) {
                at++;
                continue;
            }
            lanes.remove(at);
            lane.await();
            SolveClock clock = SolveClock.current();
            if (clock != null && lane.clock != null) clock.joinLane(lane.clock);
            GraphContext lc = lane.ctx;
            absorbSteps(lane, at);
            if (lane.error() != null) throw lane.error();
            if (lc.optimumCertified) certifyOptimum();
            offerProvenBound(lc.provenBound());
            if (!lc.closestMiss.isEmpty()) closestMiss.offer(lc.closestMiss.yaws(), lc.closestMiss.violation());
            if (lc.infeasibilityProof != null) certifyInfeasible(lc.infeasibilityProof);
            cur = mergeLane(cur, lane);
        }
        return cur;
    }

    private void absorbSteps(GraphLane lane, int at) {
        int added = runState.absorb(lane.ctx.runState, lane.stepIndex);
        for (int i = 0; i < lanes.size(); i++) {
            GraphLane other = lanes.get(i);
            if (other.stepIndex > lane.stepIndex || (other.stepIndex == lane.stepIndex && i >= at)) {
                other.stepIndex += added;
            }
        }
    }

    void closeLanes() {
        while (!lanes.isEmpty()) {
            GraphLane lane = lanes.remove(0);
            lane.abort();
            lane.await();
            absorbSteps(lane, 0);
        }
    }

    private Candidate mergeLane(Candidate cur, GraphLane lane) {
        Candidate found = lane.result();
        if (found == null || found.yaws == null) return cur;
        JumpPhysicsInputs laneScenario = lane.ctx.scenario;
        boolean adopt;
        if (cur == null || cur.yaws == null) {
            adopt = true;
        } else if (!cur.feasible) {
            adopt = found.feasible || found.violation < cur.violation;
        } else if (!found.feasible) {
            adopt = false;
        } else {
            double mine = objectiveAfterStartMove(scenario, cur.yaws);
            double theirs = objectiveAfterStartMove(laneScenario, found.yaws);
            adopt = maximize() ? theirs > mine : theirs < mine;
        }
        if (!adopt) return cur;
        scenario.startPos = laneScenario.startPos;
        scenario.startBox = laneScenario.startBox;
        String laneChain = lane.ctx.chain();
        String from = lane.chainAtStart;
        if (laneChain != null && !laneChain.equals(from)) {
            String added = from != null && laneChain.startsWith(from) ? laneChain.substring(from.length()) : laneChain;
            if (!added.isEmpty()) chainSuffix(added.startsWith(" ") || chain() == null ? added : " " + added);
        }
        Candidate out = Candidate.of(this, found.yaws);
        if (!Double.isNaN(found.dualGap)) out = out.withDualGap(found.dualGap);
        return out;
    }

    private double objectiveAfterStartMove(JumpPhysicsInputs at, double[] yaws) {
        if (!freeStart) return Scoring.exactObjective(model, at, spec, yaws);
        JumpPhysicsInputs moved = at.copy();
        Scoring.adoptPinnedStart(moved, moved.startPos.x, moved.startPos.z);
        Scoring.adoptWinningTranslation(model, moved, spec, freeBox, yaws, feasTol);
        return Scoring.exactObjective(model, moved, spec, yaws);
    }

    void shutdown() {
        for (GraphLane lane : lanes) lane.abort();
        lanes.clear();
        watchdog.shutdown();
    }
}
