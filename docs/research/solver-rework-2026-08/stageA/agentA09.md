# Agent A09 shard: orchestrator + node graph

Territory: `AngleSolverEngine`, `AngleSolverState`, and the whole `graph/` package (GraphRunner,
BuiltinGraphs, GraphFactory, GraphContext, Scoring, BudgetWatchdog, RouterPredicate, NodeCatalog,
SolverGraph, all `graph/nodes/`), plus the run-ticks entry (`RunTicksController`).

Files inspected (all under `core/src/main/java/de/legoshi/parkourcalc/core/`):
`anglesolver/AngleSolverEngine.java`, `anglesolver/AngleSolverState.java`,
`anglesolver/graph/{GraphRunner,BuiltinGraphs,GraphFactory,GraphContext,Scoring,BudgetWatchdog,RouterPredicate,NodeCatalog,SolverGraph,Candidate,Guarantee,GraphNode,NodeType,SolverGraph}.java`,
`anglesolver/graph/nodes/{DualChainNode,SmoothingNode,CapCertifyNode,MarkSettledNode,WrapYawsNode,ReportNode,LabelNode,TranslatedStartNode,RecedingHorizonNode,SetupPeelNode,FreeStartImproveNode,BnbNode,WrapIlsNode,IlsPolishNode,SeamSweepNode,RouterNode}.java`,
`anglesolver/solver/{JumpSpec,SmoothingPolish}.java`, `RunTicksController.java`.

Measured probes (compiled main classes already on disk at `core/build/classes/java/main`, no Gradle):
- `GraphDump.java`: dumped node/edge lists for `BuiltinGraphs.fast()`, `.optimize(10)`, `.explore()`.
  Result: FAST = 32 nodes / 54 edges; OPTIMIZE = 43 nodes / 81 edges; EXPLORE = 9 nodes / 18 edges.
- `BudgetDump.java`: dumped `budgetSec` for every budget-guarded node in each preset.
Both compiled with `javac -cp core/build/classes/java/main` and run with `java -cp "<main>;<scratch>"`.

---

## A09-1  FAST preset actual DAG (measured connectivity map)

LOCATION: `BuiltinGraphs.build("Fast", sof=true, ilx=false, win=true, 10, 3, 0)` (BuiltinGraphs.java:10,55-278); routed by `GraphFactory.forState` default branch (GraphFactory.java:22).

CLAIM: FAST is a 32-node graph whose spine is seed -> capCertify -> (warm smoothing) -> free-start
ladder -> first-feasible rescue -> capCertify -> translate -> final smoothing -> emit; single vs
multi is an internal router (`rJumps` = JUMPS_LE_ONE), not a separate entry.

EVIDENCE (GraphDump, verbatim spine):
- `entry -> rJumps`. `rJumps -TRUE-> seedSingle` (dualChain); `rJumps -FALSE-> horizon` (recedingHorizon).
- Single: `seedSingle -FOUND-> cap1`(capCertify, computeDualGap=true, markSettled) `-AT_CAP-> repSkip`, `-FALSE-> repA`; `seedSingle -NONE-> repA`.
- Multi: `horizon -FOUND-> wrap0`(wrapYaws), `-NONE-> seedMulti`; `seedMulti -FOUND-> wrap0`, `-NONE-> rSeedHave`; `rSeedHave -TRUE-> wrap0`, `-FALSE-> peel`(setupPeel); `peel -FOUND-> wrap0`, `-NONE-> repA`.
- Warm: `wrap0 -> rWarmTicks`(TICKS_LE_CAP 256) `-TRUE-> smoothWarm`(smoothing, deWiggle=false) `-> repWarm`; `-FALSE-> settledMark -> repSkip`.
- Cold: reports funnel into `rImproveFeas`(CANDIDATE_FEASIBLE_SCORED); `rFeasFastCold/rFeasFastWarm`(CANDIDATE_FEASIBLE_RAW) short-circuit to `lblFF`(label " (first feasible)"); free ladder `rEarlyFree -> freeRescue`(jointOnly), then `rFree -> freeImprove`, then `rRescueTicks -> rRescueFeas -> rescueBnb`(bnb FIRST_FEASIBLE).
- Tail: `rHave -TRUE-> cap2`(capCertify skipIfSettled) `-> rTrans`(HAS_FREE_START) `-TRUE-> translate -> smoothFinal`(smoothing, deWiggle=true) `-> emit`; `-FALSE-> smoothFinal`; `rHave -FALSE-> emit` (no candidate bypasses smoothFinal).

IMPACT: simplicity/correctness. This is the STAGE CONNECTIVITY MAP baseline for FAST; it shows FAST is
already a large graph (7 routers on the cold path alone) despite being the "first feasible" preset.

PROPOSAL: use this as the reference DAG for the spec; collapse candidates in A09-10.

CONFIDENCE: 0.98 (dumped from the compiled builder).

DEPENDS-ON: none.

---

## A09-2  OPTIMIZE preset actual DAG (measured connectivity map)

LOCATION: `BuiltinGraphs.build("Optimize", sof=false, ilx=true, win=true, 10, 3, optimizeSeconds)` (BuiltinGraphs.java:14,55-278); `GraphFactory.forState` THOROUGH branch (GraphFactory.java:16).

CLAIM: OPTIMIZE is FAST's spine plus two extra blocks the FAST flags disable: the exhaustive
recovery block (seamSweep -> bnb OPTIMIZE -> ILS -> near-miss bnb) and the wrap-ILS block; it has 43
nodes / 81 edges.

EVIDENCE (GraphDump): the `exh = ilx && !sof = true` block adds `rExhTicks/rExhFeas/rExhJumps/rExhHead
-> sweep -> bnbOpt -> ils -> rNearFeas/rNearEps -> nearBnb`; the `ilx` block adds `rWrapEps/rWrapFeas/
rWrapLegal -> wrap`(wrapIls) with `wrap -ADOPTED-> emit` (locks stage, bypasses smoothFinal) and
`-REJECTED-> rTrans`. Multi seed differs from FAST by a `rChainTicks` router before `seedMulti`
(`horizon -FOUND-> rChainTicks -TRUE-> seedMulti / -FALSE-> wrap0`). Tail identical to FAST
(`rTrans -> translate -> smoothFinal -> emit`).

IMPACT: simplicity. OPTIMIZE and FAST are ONE builder differentiated by 3 booleans (`sof/ilx/win`);
they are not independent pipelines. The spec can describe them as one graph with feature gates.

PROPOSAL: document the 3-flag superset; the "presets" are configurations, not distinct programs.

CONFIDENCE: 0.98 (dumped).

DEPENDS-ON: A09-1.

---

## A09-3  EXPLORE is a thin second race arm, missing most FAST capabilities

LOCATION: `BuiltinGraphs.explore()` (BuiltinGraphs.java:18-48); spawned only as FAST's second arm in `AngleSolverEngine.runStagedRace` (AngleSolverEngine.java:886-916), gated by `job.raceExplore = (effort==FAST)` (AngleSolverEngine.java:413).

CLAIM: The explore graph is 9 nodes (seed -> bnb -> ILS -> smoothing) and contains NO capCertify, NO
free-start improve/rescue, NO translatedStart, NO recedingHorizon, NO setupPeel; when FAST's primary
arm fails and explore wins, all of those capabilities are silently skipped for that solve.

EVIDENCE (GraphDump EXPLORE): nodes = `entry,emit,seed,rFeas,bnbOpt,bnbFF,rIls,ils,smooth`. Spine:
`seed -FOUND-> rFeas -TRUE-> bnbOpt / -FALSE-> bnbFF`; `seed -NONE-> bnbFF`; both bnb `-> rIls -TRUE-> ils
-> smooth -> emit`, `rIls -FALSE-> emit`. The race adopts explore when `explore.cand != null && (!primaryOk || explore.feasible)` (AngleSolverEngine.java:902-903), and returns `exploreSpec.asScenario()` (line 914). No free-start node exists on that arm even though `exploreCtx` carries `freeBox`.

IMPACT: correctness/robustness. A free-start or multi-jump instance that only the explore arm can seed
loses free-start optimization and translated-start entirely; the winning start is whatever `seed`
happened to pin. This is a real capability gap between the two FAST arms.

PROPOSAL: either give explore the free-start/translate tail, or fold explore into the primary FAST
graph as an alternate seed branch so the shared tail (free ladder, translate, smoothing) always runs.

CONFIDENCE: 0.95.

DEPENDS-ON: A09-1.

---

## A09-4  CONSISTENCY-GAP MATRIX: capability x path (mission-critical)

LOCATION: cross-cutting; anchors in cells.

CLAIM: Nine capabilities are present on some paths and absent on others. Table below (P=present,
`-`=absent), columns = FAST-primary / FAST-explore(race) / OPTIMIZE / run-ticks-candidate /
run-ticks-final. CUSTOM varies by its 3 flags (see A09-2).

| Capability | anchor | FAST-prim | FAST-explore | OPTIMIZE | RT-candidate | RT-final |
| --- | --- | --- | --- | --- | --- | --- |
| dualChain seed | DualChainNode.java:60 | P | P | P | P | P |
| capCertify + dual-gap report | CapCertifyNode.java; BuiltinGraphs:78,124 | P | - | P | P | P |
| free-start improve (full) | FreeStartImproveNode.java:48 | P | - | P | P | P |
| free-start joint rescue | FreeStartImproveNode.java:39 | P | - | P | P | P |
| translated start | TranslatedStartNode.java:19 | P | - | P | P | P |
| receding horizon (multi) | RecedingHorizonNode.java:28 | P | - | P | P | P |
| setup peel | SetupPeelNode.java:40 | P | - | P | P | P |
| seam sweep | BuiltinGraphs:132 (exh only) | - | - | P | - | - |
| bnb OPTIMIZE (obj) | BnbNode.java:61 | - | P | P | - | - |
| ILS polish | IlsPolishNode.java:28 | - | P | P | - | - |
| wrap ILS + legal push | WrapIlsNode.java:42; BuiltinGraphs:149-157 | - | - | P | - | - |
| graph smoothing (SmoothingPolish, roughness) | SmoothingNode.java:45-52 | P | P(smooth only) | P | P | P |
| DeWiggle (needs smoothLambda>0) | SmoothingNode.java:37 | P(smoothFinal) | P | P | P | P |
| engine SmoothFaceRecovery | AngleSolverEngine.java:1020 | P | P | P | - | P |

EVIDENCE: presence/absence read directly from the three DAG dumps (A09-1..A09-3) plus the two
run-ticks solve calls (`engine.solve(FAST,false)` per candidate, RunTicksController.java:207;
`engine.solve(FAST)` for the winner, line 226). The only per-path difference between RT-candidate and
RT-final is the `smoothFinalResult` flag that gates the engine SmoothFaceRecovery (AngleSolverEngine.java:481-483,1020).

IMPACT: simplicity/robustness, high. The two most user-visible gaps: (1) FAST-explore lacks free-start
and capCertify (A09-3); (2) wrap-ILS + legal-push exist only in the `ilx` graph (OPTIMIZE / exhaustive
CUSTOM), so LEGAL MODE under FAST effort gets no legal push at all (the legalGoal is removed from the
constraint set in `buildJob` (AngleSolverEngine.java:404) but no FAST node consumes it; only
`rWrapLegal`/`wrap` do, and FAST has neither).

PROPOSAL: define one capability set that every terminal path shares (free-start, translate, capCertify,
smoothing), and gate only the SEARCH intensity (sweep/ils/wrap) by effort. Route legal push into a
shared tail so it is not OPTIMIZE-only.

CONFIDENCE: 0.9.

DEPENDS-ON: A09-1, A09-2, A09-3, A09-5.

---

## A09-5  Smoothing lives at three independent sites; only DeWiggle needs the checkbox

LOCATION: graph `SmoothingNode` (SmoothingNode.java:33-56), engine `smoothFacing` (AngleSolverEngine.java:1019-1085), objective `smoothPenalty`/scored (SmoothingPolish.java:62).

CLAIM: "Smooth (TAS)" is not one switch. `SmoothingPolish.smooth` (roughness minimization) runs on the
graph path REGARDLESS of the smoothLambda checkbox and can move free ticks even at lambda=0; only
`DeWiggle` and the scored (turnCost) objective are gated on `smoothLambda>0`; and the engine-level
`SmoothFaceRecovery` is a separate post-graph pass gated on `(smoothLambda>0 || PKC_SMOOTHFACING) &&
smoothFinalResult`.

EVIDENCE: `SmoothingNode.execute` always calls `SmoothingPolish.smooth` (line 45-52); DeWiggle guarded
by `deWiggle && ctx.spec.objective.smoothLambda > 0.0` (line 37). `SmoothingPolish` header states "at
lambda 0 the objective never drops ... the pass is simply a no-op" only "where every tick is
load-bearing" (SmoothingPolish.java:11-16); underdetermined ticks are still de-wiggled at lambda 0.
Engine gate: `smoothRequested = spec.objective.smoothLambda > 0.0 || SMOOTH_FINAL_FACING` and
`smoothRequested && smoothFinalResult && model instanceof ExactJumpModel` (AngleSolverEngine.java:1019-1020).
`smoothWarm` node has `deWiggle=false, countEvals=false` (BuiltinGraphs.java:86); `smoothFinal`/explore
`smooth` have `deWiggle=true, countEvals=true` (BuiltinGraphs.java:160-161,34).

IMPACT: smoothness/simplicity. The pack's "four smoothing stages" map to: turnCost (Objective scored),
DeWiggle (SmoothingNode), SmoothingPolish (SmoothingNode, TWO instances smoothWarm+smoothFinal), and
SmoothFaceRecovery (engine). Two of the four (base roughness min) fire even with the checkbox OFF, so
"smoothing" is not cleanly gated and is spread across graph + engine.

PROPOSAL: collapse to a single smoothing stage expressed as a constraint/objective term (target
capability 3); if kept as a pass, make its gate uniform (one flag decides all four) and run it in one
place, not split graph/engine.

CONFIDENCE: 0.9.

DEPENDS-ON: none.

---

## A09-6  Run-ticks candidates skip engine SmoothFaceRecovery; final does not

LOCATION: `RunTicksController.advance` (RunTicksController.java:207), `.finishSearch` (line 226), engine gate (AngleSolverEngine.java:481-483,1020).

CLAIM: During the run-ticks search each candidate is solved with `engine.solve(FAST, smoothFinal=false)`
so the engine SmoothFaceRecovery pass is off; the final winning combo is re-solved with
`engine.solve(FAST)` (smoothFinal defaults true), so the applied TAS is smoothed but the objective the
search RANKED candidates by was measured on unsmoothed facings.

EVIDENCE: line 207 passes `false`; line 226 uses the 1-arg overload (smoothFinal=true, AngleSolverEngine.java:477-479).
Candidate objective compared via `result.getObjectiveValue()` (RunTicksController.java:176-180). Graph
smoothing (SmoothingPolish) still runs on candidates (it is inside the FAST graph, unaffected by the
flag), so the gap is specifically the engine face-recovery pass, which only matters when smoothLambda>0.

IMPACT: correctness/smoothness, small. With Smooth (TAS) on, the run-ticks winner is chosen on a
slightly different (unsmoothed) objective than the one finally shown; the SmoothFaceRecovery objGuard
(A09-9) bounds the drift to <= 3e-4 b, so ranking flips are rare but possible on near-ties.

PROPOSAL: either run the same smoothing on candidates, or (cheaper) rank candidates on the pre-smoothing
objective consistently and note it. Fold this into the single-smoothing-stage decision (A09-5).

CONFIDENCE: 0.85.

DEPENDS-ON: A09-5.

---

## A09-7  Result selection is a decentralized threaded incumbent over shared mutable scenario

LOCATION: `GraphRunner.walk` threads one `Candidate` (GraphRunner.java:23-83); each node's own adopt
rule; `JumpSpec.asScenario()` returns the SHARED mutable `JumpPhysicsInputs` (JumpSpec.java:21-23).

CLAIM: There is no central "best-feasible" selector. The graph carries a single incumbent candidate
and a single mutable scenario; feasibility-first-then-objective is re-implemented independently in at
least six places, and `startPos` is mutated in-place by several nodes. A feasible incumbent can be
dropped if any startPos mutation is not matched by the yaws that produced it.

EVIDENCE: the incumbent is `outcome.candidate` reassigned each node (GraphRunner.java:71-72). Adopt
rules with their own feasibility/objective logic: `DualChainNode` (scoredExactObjective only, line 74-81),
`FreeStartImproveNode.improve` (feasibility-first, lines 108-166), `BnbNode` (objective + re-verify,
lines 71-79), `SeamSweepNode` (lines 52-60), `WrapIlsNode` (adopt gate lines 76-88), race arm selection
(AngleSolverEngine.java:902-916). Mutations to `ctx.scenario.startPos`: FreeStartImproveNode.java:140-141,
160-161,164-165,88-89,95-96; RecedingHorizonNode.java:41-42; Scoring.adoptWinningTranslation/adoptStageResult
(Scoring.java:156-160,179-180). Because `asScenario()` returns the shared instance, the final forward in
`runJob` (AngleSolverEngine.java:1031-1032) reads whatever startPos the last mutating node left; every
mutation currently re-verifies feasibility at the new start, which is the ONLY thing preventing a
dropped-feasible result.

IMPACT: correctness, high recurrence risk. This is the architectural surface behind the handoff's
"Optimize dropped-feasible" class: incumbent yaws and scenario startPos are two separate mutable states
kept consistent only by per-node re-verification convention, not by construction.

PROPOSAL: make the candidate carry its own (start, yaws) pair immutably; a central selector keeps the
best FEASIBLE-then-objective across all node outputs (target capability 5). Never mutate a shared
scenario mid-graph.

CONFIDENCE: 0.85 (mechanism verified in code; no reproduction attempted this session).

DEPENDS-ON: A09-1.

---

## A09-8  Success flag uses per-UI-constraint met==total, divergent from the recorded compiled violation

LOCATION: `buildResult` (AngleSolverEngine.java:1524-1548), `satisfied` (line 1583-1603), record path
`finishRecord` with `finalViolation` (line 1042-1048).

CLAIM: `SolveResult.isSuccess()` = `feasible(hardcoded true) && met==total` where met/total count only
UI constraints judged by `satisfied()` (walls at FEAS_TOL=0, EQ/range at MET_TOL=1e-4). The run record's
`feasible` is `JumpConstraintCompiler.maxViolation(...) <= FEAS_TOL` over the FULL compiled wall set.
These two judgments are computed independently and can disagree.

EVIDENCE: `assembleResult -> buildResultWithObjective(...)` passes the default `feasible=true`
(AngleSolverEngine.java:1153,1138-1139); the only real gate is `met==total` (line 1541). Separately
`finalViolation = JumpConstraintCompiler.compile(spec).maxViolation(gameFacings, path)` (line 1043) feeds
`finishRecord(..., finalViolation <= FEAS_TOL, ...)` (line 1047-1048). The compiled set includes solver
walls that are not UI constraints (objGuard from smoothing, `eqLo/eqHi` corridors, derived seam dF), so a
result can show all UI rows met (isSuccess true) while a compiled wall is violated, or vice-versa.

IMPACT: correctness, low-moderate. A false "Solved" is possible when UI rows pass their MET_TOL band but a
tighter compiled corridor is missed; the panel and the log would then disagree.

PROPOSAL: derive isSuccess from the same compiled `maxViolation` used for the record, and report per-UI
margins as presentation only.

CONFIDENCE: 0.7 (divergence proven structurally; magnitude not measured on a capture this session).

DEPENDS-ON: A09-7.

---

## A09-9  Budget/deadline model: FAST seeds are wall-clock unbounded; per-node budgets are soft ceilings under the overall deadline

LOCATION: `deadlineNanosFor` (AngleSolverEngine.java:69-82), `GraphRunner.walk` deadline math (GraphRunner.java:58-66), `BudgetWatchdog` (BudgetWatchdog.java), build() budget arithmetic (BuiltinGraphs.java:56-66).

CLAIM: FAST has NO overall deadline (0) and its seed nodes have budgetSec=0, so a FAST solve is bounded
only by the solvers' internal iteration caps plus fixed-second recovery-node budgets; OPTIMIZE per-node
budgets sum far above the overall budget and are clamped by it, with a wrap reserve carved at the end.

EVIDENCE (BudgetDump): FAST budgetSec: `seedSingle=0, seedMulti=0, horizon=0, peel=12, freeImprove=20,
freeRescue=2, rescueBnb=3`. OPTIMIZE(10): `seedSingle=10, seedMulti=10, freeImprove=10, freeRescue=2,
coldBnb=4, sweep=1, bnbOpt=4, ils=7, nearBnb=5, wrap=10, peel=10` (sum ~73s clamped by the overall
`System.nanoTime()+10s` set in AngleSolverEngine.java:983). OPTIMIZE(120): ils=117, wrap=120, etc.
`GraphRunner` computes each node deadline as `min(now+budgetNanos, overall)` and, when a wrapIls node is
reachable, further caps pre-wrap nodes at `overall - wrapReserve` where `wrapReserve = min(3s,
totalNanos/4)` (GraphRunner.java:58-66,85-88). FAST leaves `overall=0` (deadlineNanosFor FAST=0, line 80),
so only the nonzero per-node budgets (peel 12, freeImprove 20) bound wall-clock; the seed can run to its
internal cap. During run-ticks, FAST is externally bounded by `stepTimeoutMs` via poll+cancel
(RunTicksController.java:163-166), not by the engine.

IMPACT: speed/robustness. A standalone FAST solve (user clicks Solve) has no time guarantee; worst case
= unbounded seed + 20s free + 12s peel + smoothing. The pack's 0.1-800ms envelope holds only because the
seed's internal caps are small in practice, not because FAST enforces a deadline.

PROPOSAL: give FAST a real overall deadline (even a generous default) so latency is bounded by policy,
not by whichever solver's internal cap happens to dominate.

CONFIDENCE: 0.95 (budgets dumped; internal-cap behavior inferred from node code).

DEPENDS-ON: A09-1.

---

## A09-10  Engine SmoothFaceRecovery runs AFTER the graph deadline and can overrun the budget

LOCATION: `runJob` smoothing block (AngleSolverEngine.java:1019-1030), constants (line 1087-1090).

CLAIM: The engine-level `smoothFacing` pass runs after `GraphRunner.run` returns (i.e. after the graph's
overall deadline has already elapsed) with a fresh budget of `deadline/8` capped at 6s (or 400ms for
FAST), and `solveNanos` is recomputed to include it, so the REPORTED runtime is honest but the ACTUAL
solve exceeds the user's time budget by up to `deadline/8` whenever Smooth (TAS) is on.

EVIDENCE: `smoothBudget = deadlineNanos>0 ? min(deadlineNanos/8, MAX_SMOOTH_BUDGET_NANOS) :
SMOOTH_BUDGET_NANOS` with `SMOOTH_BUDGET_NANOS=400ms`, `MAX_SMOOTH_BUDGET_NANOS=6s` (lines 1021-1022,
1088-1089); the smoothing deadline is `System.nanoTime()+budgetNanos` computed at line 1080 (fresh, after
the graph finished); `solveNanos = System.nanoTime() - solveStart` recomputed at line 1029. The objGuard
constraint bounds objective loss to `SMOOTH_OBJ_SLACK = 3.0e-4` b (line 1090, 1065-1067) and the pass
re-verifies feasibility before adopting (line 1084), returning null (original yaws kept) otherwise.

IMPACT: speed/smoothness. For OPTIMIZE(10) with Smooth (TAS), total solve can reach ~11.25s (10 graph +
1.25 smoothing). The overrun is invisible in the panel because solveNanos includes it, but it is real
wall-clock past the requested budget.

PROPOSAL: reserve the smoothing slice INSIDE the overall deadline (like the wrap reserve) rather than
appending it, so the reported and enforced budgets coincide.

CONFIDENCE: 0.95 (re-verified constants: deadline/8, 6s cap, 400ms FAST floor, 3e-4 objGuard).

DEPENDS-ON: A09-9.

---

## A09-11  Simplicity audit: five distinct solve entry paths, three foldable

LOCATION: `AngleSolverEngine.solve/buildJob/runJob` (AngleSolverEngine.java:473-524,357-414,945-1051), `RunTicksController.start/advance` (RunTicksController.java:96-130,194-209).

CLAIM: A solve has five distinguishable entry paths: (1) single-jump seed (`rJumps TRUE -> seedSingle`
dualChain), (2) multi-jump receding horizon (`rJumps FALSE -> horizon` LongRunSolver), (3) free-start
(detected in buildJob, threaded as a cross-cutting startBox), (4) legal mode (goal wall peeled in
buildJob, consumed only by wrap), (5) run-ticks (external controller iterating FAST solves). Paths 1 and
2 are the fold candidate from open direction 4.

EVIDENCE: single vs multi is the `rJumps` JUMPS_LE_ONE router (BuiltinGraphs.java:74,176-177); horizon
calls `LongRunSolver.solve` (RecedingHorizonNode.java:34) while seedSingle calls the static
`AngleSolverEngine.dualChain` (DualChainNode.java:64). free-start: `deriveFreeStartBox` at startTick==0
(AngleSolverEngine.java:376-383) then `runJob` re-pins seed (lines 950-964). legal: `selectLegalGoalWall`
+ `constraints.remove(legalGoal)` (lines 396-404); only WrapIlsNode/`LEGAL_PUSH` consume it. run-ticks
wraps `engine.solve(FAST,...)` per node (RunTicksController.java:207).

IMPACT: simplicity, high. A single jump is just a 1-window `LongRunSolver` problem; the dualChain fast
path is a special case. Folding removes the `rJumps` split and the `seedSingle`/`seedMulti` duplication.
Free-start and legal are cross-cutting flags, not paths, but each is wired into a different subset of
nodes (A09-4), which is the real duplication cost.

PROPOSAL: (a) route single-jump through `LongRunSolver` with window=numJumps and keep dualChain as the
window-solver's inner seed (open direction 4); (b) express free-start, dF, legal, smoothing as uniform
capabilities on ONE tail every path shares (target capability 5), so they cannot be present-on-some.

CONFIDENCE: 0.8 (structure measured; fold feasibility is a design claim to prototype).

DEPENDS-ON: A09-1, A09-2, A09-4.

---

## A09-12  dF handling is asymmetric: declined pre-search, notice on success, deterministic optimizer disabled

LOCATION: `hasUnsupportedDf` (AngleSolverEngine.java:1092-1095,999), DF notices (lines 1097-1108), success notice (lines 1044-1046), dualChain dF no-op (lines 1749-1759).

CLAIM: A non-zero dF constraint is not routed anywhere: every stage declines it, the failure result
attaches `DF_UNSUPPORTED_NOTICE`, and a SUCCESSFUL solve that has any facing wall attaches
`DF_DIRECTION_NOTICE` warning that the deterministic direction optimizer (levelSetTopUp) was skipped.
So dF support is "decline or best-effort", never a first-class capability, and this branch is identical
across all presets (it is engine-level, not a node).

EVIDENCE: `hasUnsupportedDf` only consulted on the failure branch (line 999). `DF_DIRECTION_NOTICE` set
when `finalViolation <= FEAS_TOL && JumpLinearModel.hasFacingWall(spec.constraints)` (line 1044).
`levelSetTopUp` documented "No-op with dF constraints (no dual bound)" (line 1748). dF=0 (seam) is
compiled as an F-mode equality corridor in `addSeamDeltaFacing` (lines 1497-1508); non-seam dF as a
relative F wall (line 1456).

IMPACT: correctness/simplicity. dF composes poorly with the dual fast path; the notice is the tell that
the optimizer is not dF-aware. Target capability 2 (dF=0 pinning composing with free-start) is only
partially met: seam dF=0 compiles, but any facing wall demotes the solve off the deterministic optimizer.

PROPOSAL: treat dF=0 as a facing equality in the same linear compile the walls use (it already is), and
make the direction optimizer dF-aware rather than declining, so the notice can be retired.

CONFIDENCE: 0.85.

DEPENDS-ON: none.

---

## A09-13  Caching is per-context and per-node; nothing is shared across race arms or solves

LOCATION: `GraphContext` memoized bounds (GraphContext.java:155-169), `RecedingHorizonNode` WindowCache (RecedingHorizonNode.java:33), `SetupPeelNode` (no cache, SetupPeelNode.java:90).

CLAIM: The only caches are (a) `reachBound`/`headroomBound` memoized per GraphContext, and (b) a
`WindowCache` created fresh inside each RecedingHorizonNode call. There is no cache shared between the
FAST primary and explore race arms (separate GraphContexts), none across run-ticks candidate solves
(each is a fresh Job/GraphContext), and setupPeel re-solves tails with no window cache.

EVIDENCE: `reachBound`/`headroomBound` guarded by `reachBoundSet`/`headroomBoundSet` on the context
instance (GraphContext.java:155-169); race builds two independent contexts `primaryCtx`/`exploreCtx`
(AngleSolverEngine.java:868,892) so the explore arm recomputes both bounds from scratch. `WindowCache
windows = new LongRunSolver.WindowCache()` per node execute (RecedingHorizonNode.java:33). SetupPeelNode
calls `LongRunSolver.solve(em, tail, ...)` with the 3-arg overload (no cache) inside its sweep loop
(SetupPeelNode.java:90). Run-ticks: every `advance()` builds a fresh Job via `engine.solve` (each call
`buildJob` -> new GraphContext), so nothing carries over between the O(jumps x ticks) candidate solves.

IMPACT: speed, moderate. The run-ticks search re-derives the entire per-tick physics and dual bounds for
every candidate that differs by one inserted run-up tick; overlapping windows across candidates are never
reused. The race recomputes bounds twice.

PROPOSAL: hoist the WindowCache to GraphContext so peel and horizon share it; let the race arms share the
immutable bound memo; give run-ticks a cross-candidate window/seed cache keyed by prefix.

CONFIDENCE: 0.8 (cache locations verified; speed magnitude not benchmarked this session).

DEPENDS-ON: A09-11.

---

## A09-14  capCertify / dual-gap and AT_OBJECTIVE_CAP are unevenly wired

LOCATION: cap nodes (BuiltinGraphs.java:78-80,124-126), `RouterPredicate.AT_OBJECTIVE_CAP` (RouterPredicate.java:42-49).

CLAIM: `capCertify` (with dual-gap computation) is on the build-based graphs (FAST/OPTIMIZE/CUSTOM) but
NOT on explore, so a FAST solve WON by the explore arm reports no dual-gap and never marks settled; and
the `AT_OBJECTIVE_CAP` router predicate is implemented but wired into zero builtin graphs (dead).

EVIDENCE: `cap1`/`cap2` present in FAST and OPTIMIZE dumps, absent in EXPLORE dump (A09-1..A09-3). When
explore wins, `ctx = exploreCtx` which has no cap node, so `ctx.dualGap()` stays NaN and the "Dual bound
gap" detail is omitted (AngleSolverEngine.java:1005,1159). `AT_OBJECTIVE_CAP` appears only in
RouterPredicate.evaluate; no `router(...,"AT_OBJECTIVE_CAP")` call exists in BuiltinGraphs (the graphs use
CANDIDATE_FEASIBLE_RAW / VIOLATION_AT_MOST instead), and CapCertifyNode does the cap check directly.

IMPACT: simplicity/correctness, low. Inconsistent reporting between race arms; one dead predicate to
prune.

PROPOSAL: fold cap-certify into the shared tail (A09-4) so all arms report identically; delete the unused
AT_OBJECTIVE_CAP predicate or wire it where the manual cap check now lives.

CONFIDENCE: 0.85.

DEPENDS-ON: A09-3, A09-4.
