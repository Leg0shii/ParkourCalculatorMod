# MISSION: finish the solver cutover and produce a benchmarked SHIP VERDICT

You are Claude Code at the repo root of ParkourCalculatorMod (Windows; PowerShell primary, Bash tool available).
A multi-session campaign (stages P0-P7, recorded in docs/research/solver-rework-2026-08/BUILD-LOG.md) re-founded
the angle solver toward ARCH-1 ("convex dual + low-dimensional residual") and then did a CUTOVER that removed the
8 default-off capability flags and wired the proven components as the single default path. A 10-agent independent
audit then validated what is real vs overstated and produced the plan below. YOUR JOB THIS CAMPAIGN: execute that
plan to close every open item that makes the solver genuinely simpler / more maintainable / more stable / stronger
/ faster, then run a thorough OLD-vs-NEW benchmark over ALL problems and render a clear, evidence-based SHIP
VERDICT. Work in green-gated steps; this spans multiple sessions (continue from the BUILD-LOG, newest entry first).

## Where things actually stand (validated 2026-08-25; do not re-derive, verify by code)

DONE (cross-validated in code):
- The 8 capability flags (oneTail, strictSuccess, trendFilter, residualRescue, diskIpm, gateMip, sphereSnap,
  p0.tailScore) and all their scaffolding are gone. One success rule (inline compiled maxViolation<=FEAS_TOL=0),
  one recovery tail (RecoveryLadder, 2 callers), one smoother owner (TrendFilterSmooth). The four ARCH-1 recovery
  components are default-on and are all KEEP-BETTER + byte-exact-feasible, so defaulting them on cannot regress
  feasibility. P0-P6 capabilities are realized on the default path. Working tree compiles; fast suite green.

NOT done / OVERSTATED (the audit corrected these; treat as facts):
- "zero flags" is FALSE: `PKC_SMOOTHFACING` (AngleSolverEngine SMOOTH_FINAL_FACING) is a live env behavior flag
  (defaults off, predates the campaign, redundant with smoothLambda>0). Must resolve (OI-07).
- "simpler" is FLAG-surface only. The cutover is NET ADDITIVE (~+1,900 LOC, +7 classes); the four components are
  layered ON the fully-intact ClosedForm/SLP/Relax/BnB ladder; NO old solver is deleted. Three passthrough
  SmoothingNode instances (smooth/smoothWarm/smoothFinal) + dead-but-test-referenced SmoothingPolish + LatticeRepair
  still present.
- (RESOLVED for the commit half of OI-01) The campaign is now COMMITTED on branch
  `feature/solver-rework-arch1-cutover` in 4 commits: 998ee62e feat (ARCH-1 code + cutover), dec2f52c test,
  dbff526f docs, 0e8e55c1 chore (gitignore). Base = 3d19c9ff (feature/418 tip = origin/dev + the unmerged #418
  smooth-TAS commit the campaign is stacked on). NOT pushed. The benchmark OLD baseline = 3d19c9ff (pre-campaign,
  == HEAD before 998ee62e); NEW = branch HEAD. STILL OPEN: OI-02 (run -PslowTests on this committed tree and
  record it - the committed content equals the tree the session ran green 6x, but no committed-tree run is
  recorded yet); and the commits are logical groups (code/tests/docs), not per-P0..P7 (the working tree was a
  mixed blob; per-stage was not reconstructable).
- STRONGER is asserted by code inspection, not by any tight graph-path objective gate (the one tight gate, loopmm
  gap 0.0, bypasses the graph via dualChain+BoundPrunedRecovery). FASTER is UNMEASURED and the added unconditional
  disk-IPM work + the GateMip double-BnB point slower, not faster. Nothing ARCH-1 has ever run in a real MC client.
- Smooth-TAS give-back loosened 160x: TrendFilterSmooth.MAX_GIVE_BACK=8e-3 replaced the old SMOOTH_OBJ_SLACK=5e-5,
  so on smoothLambda>0 solves the reported objective can regress up to ~8e-3 b, invisible to the feasibility-only
  corpus (OI-08). IMPLEMENTATION-GUIDE's j008b target -0.197 is STALE/infeasible; the true optimum is -0.2153.

PROVISIONAL VERDICT (the benchmark decides): do NOT ship on current evidence. The flag-cutover itself is sound and
low-risk in isolation (CONDITIONAL SHIP after the gates below). The campaign's stated goal (simpler/stabler/
stronger/faster) is NOT yet met and NOT yet proven.

## The plan (execute in order; each code step green-gated on ./gradlew :core:test then -PslowTests before the next)

- STEP 0 - baseline you can trust [OI-01 commit DONE, OI-02 open] (P0, blocks ship): the campaign is committed on
  `feature/solver-rework-arch1-cutover` (SHAs in the status section; base 3d19c9ff). REMAINING: run
  `./gradlew :core:test -PslowTests` on this committed tree and RECORD runtime + pass/fail (OI-02); proceed only if
  GREEN. (Never push without the user; ask before any further commit.)
- STEP 1 - zero-risk subtraction [OI-10, OI-12, OI-15]: delete the 3 passthrough SmoothingNode instances + class +
  NodeCatalog registration (rewire each inbound edge to its outbound target); retarget EngineFileScreen/RelaxDiagScreen
  off LatticeRepair then delete LatticeRepair; delete SmoothingPolish + retarget/relabel its 3 test dependents.
- STEP 2 - honesty + docs [OI-07, OI-16]: resolve SMOOTH_FINAL_FACING (delete, driving final-facing from
  smoothLambda>0, OR promote to a documented diagnostic like pkc.solver.trace); correct BUILD-LOG flag/node counts,
  the "ClosedForm/SLP/CostateDual are deletable" misnaming (they are the FOUNDATION, not deletable), the
  IMPLEMENTATION-GUIDE j008b target (-0.2153), and the stale ResidualRescue dF class comment.
- STEP 3 - test integrity [OI-05, OI-19]: add graph-path objective gates with a TIGHT ~1e-4 maxObjectiveGap that go
  RED if any ARCH-1 component node is unwired (a loopmm-class capture through engine.solve, not dualChain; a
  tightened j021 graph solve). Map the ~12 new probes in anglesolver/TESTS.md.
- STEP 4 - correctness [OI-09, OI-18]: fix the A07-7 seam-straddling dropped-constraint gap in
  LongRunSolver.sliceConstraints (substitute frozen exact values instead of silently dropping a cross-seam pair);
  thread a real deadline into solveWindow's RecoveryLadder delegation (currently 0L).
- STEP 5 - one smoothing owner [OI-14, OI-08]: remove the Angles.turnCost search-bias (Objective.scored,
  SolveProgress) so the terminal TrendFilterSmooth owns ALL smoothing; update/delete SmoothLambdaScoringTest; bound/
  document MAX_GIVE_BACK (measure worst-case give-back on smoothLambda>0 captures first) and add a smooth-requested
  objective-guard assertion to the corpus.
- STEP 6 - perf + determinism [OI-11, OI-06, OI-17]: reconcile the GateMip double-BnB (run the seam tree ONCE,
  seeded by the already-computed BoundPrunedRecovery result under the disk-kernel bound); bound residual+sphere on
  the deadline-free FAST path (FAST has deadlineNanosFor=0L; give sphereSnap a budget); add a determinism CI guard
  (deterministic iteration/eval budgets, or a non-env-gated @Category(SlowSolverTests) test asserting identical
  shipped objective across repeated solves, verified on a second machine).
- STEP 7 - the SIMPLER headline [OI-13, OI-20] (XL, ARCH-3 risk): make the ARCH-1 residual primitive the SOLE
  recovery in RecoveryLadder and DELETE RelaxationRecovery and/or BoundPrunedRecovery ONLY where the full slow suite
  stays byte-exact green, capture-by-capture. KEEP ClosedFormSolve/SlpSolve/CostateDualSolver (the convex-dual
  FOUNDATION the components are built on; NOT deletable). Where loopmm/momentum refuse to collapse, STOP and record
  an honest ARCH-2 cannot-fully-collapse in SPEC section 6 - NEVER reintroduce a flag. Consolidate the remaining
  duplication SPEC named: one shared free-start/translation support-term (now mirrored ~5 sites), merge FacingPrefold
  + YawTies dF-pin (F14), close FAST/OPTIMIZE seed-path parity (F11), add cross-window dual warm-start (F12, the
  long-run FASTER lever).
- STEP 8 - in-game gate [OI-03] (P0, blocks ship): QA on all three touched loaders (26.2 Fabric + both Forge),
  replaying results that fire each always-on stage (GateMip tree, SphereDecodeSnap adopt, ResidualRescue improve,
  Smooth-TAS) through the real SimulatorEntity. Headless self-agreement against ExactJumpModel is NOT verification.
- STEP 9 - the VERDICT: run the benchmark (below) and publish the evidence + the 5-question rubric.

## The benchmark (all problems; OLD vs NEW; speed + objective) [OI-04]

- CORPUS: every capture, not a sample - the ~55 top-level captures + the hpk sets + the solve/ (59) and closedform/
  (15) problems under core/src/test/resources. Run each at BOTH FAST and THOROUGH (OPTIMIZE).
- OLD vs NEW: OLD = a git worktree at HEAD (== pre-cutover, since the campaign is uncommitted until STEP 0; after
  STEP 0 use the pre-campaign commit); NEW = the committed cutover tree. Both expose the same AngleSolverEngine.solve
  and the same tracked HpkEngineBench/EngineFileScreen/ReplayYaws harness, so one harness drives both.
- SPEED: EXTERNAL wall-clock around engine.solve() (NOT the engine solveNanos field - it now includes smoothing).
  Report COLD first-solve-of-JVM latency per capture (the FAST tier especially, which has no overall deadline) AND
  warm median (>=10 runs after 2-3 warmup discards), separately for FAST and THOROUGH. For OPTIMIZE (fixed deadline)
  also report objective-at-fixed-budget, since there the regression mode is objective-crowded-out, not wall-clock.
  Attribute per component (residual/gate/sphere) via a temporary bench stub of each .improve/.snap.
- OBJECTIVE: SolveResult objective RE-CERTIFIED byte-exact through ExactJumpModel (require viol==0 or it does not
  count - never trust the solver's self-reported objective). Signed delta vs the CORRECTED per-capture baseline
  (j021>=1067.8637; j008b optimum -0.2153 NOT -0.197; loopmm -279.3 WITH the gate, never clamp-free -279.299065;
  j005/j016/j019/j022 keep-shipped; every other capture uses the OLD tree's own achieved objective). Classify each
  cell WIN / TIE(<=1e-4 b) / REGRESSION. Report the FEASIBILITY-REGRESSION COUNT (the hard gate) and the
  smoothLambda>0 captures separately with worst-case give-back.
- OUTPUT: a committed, re-runnable per-capture table (capture, tier, OLD/NEW feasible, OLD/NEW ms cold+warm, OLD/NEW
  certified objective, WIN/TIE/REGRESSION) + a summary artifact (flag-count delta, net main LOC/class delta,
  feasibility-regression count, objective win/regression tally, independent -PslowTests result).

## The verdict to render (report as EVIDENCE, not adjectives)

- SIMPLER: PASS only when net anglesolver main LOC AND solver-path/flag count drop below the pre-campaign baseline
  (today: PASS-on-flags, FAIL-on-structure until STEP 7).
- MAINTAINABLE: PASS once committed + reviewable, dead code (nodes/classes) removed, TESTS.md updated, docs corrected.
- STABLER: PASS requires independent -PslowTests green on the committed tree AND a cross-machine shipped-objective
  determinism assertion (feasibility is already stabler by construction: FEAS_TOL=0, all components keep-better).
- STRONGER: PASS requires the OLD-vs-NEW objective table showing wins >= regressions, ZERO feasibility regressions,
  plus a graph-path tight objective gate.
- FASTER: PASS requires FAST warm-median within a chosen ms bound, no FAST cold outlier over a chosen seconds bound,
  and OPTIMIZE objective-at-budget not worse on any capture.
- SHIP RUBRIC: HARD NO-SHIP if ANY of {an OLD-feasible capture is NEW-infeasible; slow suite not independently green
  on the committed tree; in-game QA fails on a touched loader}. CONDITIONAL SHIP (flag-cutover value) if feasibility
  is preserved corpus-wide, objective net-wins with no acceptance regression beyond 1e-4 b, and FAST latency within
  bound - with SIMPLER/FASTER explicitly scheduled or deferred with an honest ARCH-2 record. FULL SHIP (campaign
  goal met) additionally requires the STEP 7 net LOC/path reduction, one smoothing owner, and a passing FASTER
  benchmark.

## Hard rules (unchanged)
- Never git commit/push/branch/stage without the user; the user handles git. (STEP 0 commits require asking.)
- No code comments (no javadocs/inline). No em dashes in any writing.
- core/ stays Minecraft-free; do not break Application.runSimulation(). No shipped numeric-solver dependency.
- FEAS_TOL=0 byte-exact; determinism must hold. Shipped path GREEN on ./gradlew :core:test -PslowTests at every
  handoff; fast suite after each change. Tag new corpus-driving tests @Category(SlowSolverTests.class).
- Full open-item detail (OI-01..OI-20 with why/effort/priority) and the per-lens evidence are in the BUILD-LOG
  10-agent validation entry (newest). Read it first, then continue from the newest BUILD-LOG handoff.

## Handoff (every session): append a dated BUILD-LOG entry (what closed, measured before/after, next step), update
the stage table, overwrite this file if the plan advances, and STOP without committing. Report the measured results
vs the verdict rubric.
