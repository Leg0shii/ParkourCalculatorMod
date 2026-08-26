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

MEASURED VERDICT (STEP 9 benchmark done 2026-08-25, see BENCHMARK-STEP9.md): the cutover is SOUND but do NOT ship
yet. FAST tier: 0 feasibility regressions of 126, objective 60 wins / 6 regressions, tight graph-path gate holds
at 10s (j021 +1.86e-2). THOROUGH-4s regressions are budget artifacts (all solve at 10s). FASTER is MIXED (+48ms
per-solve floor, better tail/aggregate). CONDITIONAL SHIP is blocked on exactly TWO items: (a) fix the one real
regression j003 (NEW's FAST first-feasible lands short and THOROUGH overruns its 10s deadline; ProblemsTest-green
so it is config-specific), and (b) STEP 8 in-game QA. FULL SHIP additionally needs STEP 7 (structure/LOC + recover
the FAST floor). The stated goal (simpler/stabler/stronger/faster) is proven on stronger/stabler, partial on
simpler, mixed on faster.

## The plan (execute in order; each code step green-gated on ./gradlew :core:test then -PslowTests before the next)

- STEP 0 - DONE (2026-08-25) [OI-02]: `:core:test -PslowTests` on the committed tree HEAD 7556aceb = BUILD
  SUCCESSFUL 5m05s, exit 0. Trusted baseline recorded. OLD benchmark baseline = pre-campaign 3d19c9ff.
- STEP 1 - DONE (2026-08-25) [OI-10, OI-12, OI-15]: deleted the 3 passthrough SmoothingNode instances + class +
  NodeCatalog registration (edges rewired), LatticeRepair (screens retargeted), SmoothingPolish + SmoothingPolishTest
  (TrendFilterSmoothProbe/SmoothLambdaScoringTest retargeted). Slow suite GREEN 4m32s. Net -344 LOC main source.
- STEP 2 - DONE (2026-08-25) [OI-07, OI-16]: deleted SMOOTH_FINAL_FACING/PKC_SMOOTHFACING (smoothRequested driven by
  smoothLambda>0 alone) -> ZERO capability flags on the default path (only PKC_SOLVER_TRACE debug-log remains).
  Corrected the ClosedForm/SLP/CostateDual "deletable" misnaming (they are the FOUNDATION), the IMPLEMENTATION-GUIDE
  j008b target (-0.197 infeasible -> true optimum -0.2153), and the stale ResidualRescue dF class comment. Slow
  suite GREEN 4m32s. All STEP 1+2 changes are UNCOMMITTED on the working tree (user commits).
- STEP 3 - DONE (2026-08-25) [OI-05, OI-19]: NEW GraphPathObjectiveGateTest byte-exact-recertifies j021 + loopmm
  through the FULL graph; tightened j021 solve/ maxObjectiveGap 0.4 -> 1e-4. MEASURED: the j021 gate is genuinely
  ResidualRescue-sensitive (unwiring drops obj + breaks feasibility). loopmm-3jump-lands LANDS through the graph
  (VERIFIED OLD-vs-NEW: engine shipped objective -279.299868 on BOTH trees, NO regression). An earlier "graph misses"
  claim was a HARNESS ARTIFACT (the recompute re-applied toGameFacings through the non-yaw-locked scenario; sphereSnap
  yaw-locks the result, so the recompute re-rounded to a phantom -279.300084) - now fixed; the test asserts engineObj.
  REAL follow-up (in-game, STEP 8): sphereSnap makes loopmm's landing YAW-LOCK-DEPENDENT (div 2.16e-4 straddles the
  edge); confirm sphere-adopted yaw-locked results actually land through save/playback. See the top BUILD-LOG entry.
- STEP 4 - PARTIAL (2026-08-25) [OI-09 deferred, OI-18 DONE]: OI-18 threaded a real deadline through LongRunSolver
  -> solveWindow -> RecoveryLadder (was 0L); terminal window skips relaxation when <3s, slow GREEN. OI-09 DEFERRED:
  the dropped cross-seam constraint is SAFE (final full-spec replay backstop makes it a miss, never a false success);
  no reproducing seam-straddle capture exists (constraints are generated from geometry, not stored), and the fix
  needs the committed trajectory threaded in. Needs a constructed straddle capture before it is worth doing.
- STEP 5 - PARTIAL (2026-08-25) [OI-14 deferred, OI-08 DONE]: OI-08 measured worst-case give-back 7.8884e-3 (AT the
  8e-3 cap, so the cap is binding/justified, not loose); hardened MAX_GIVE_BACK to final; the corpus objective-guard
  is already in TrendFilterSmoothProbe. OI-14 DEFERRED: removing the turnCost search-bias risks a smoothness
  regression INVISIBLE to the corpus (prior evidence: complementary, not redundant); the raw-ranking experiment was
  inconclusive (probe uses a closed-form seed, not the ILS/BnB polish where the bias operates). Needs a full-graph
  smoothLambda>0 reversal gate first, then remove and verify against it.
- STEP 6 - DONE (2026-08-25) [OI-17, OI-11, OI-06 all DONE]: OI-17 added a determinism guard
  (GraphPathObjectiveGateTest.graphSolveIsBitIdenticalAcrossRepeats: j021 solved twice, byte-exact objective+viol
  bit-identical; cross-machine check still manual). OI-06 DONE: bounded the deadline-free FAST terminal polish
  (SphereSnapNode snap + DualChainNode residual now pass now+2s when deadlineNanos<=0; OPTIMIZE untouched). MEASURED
  via HpkEngineBench FAST: a tight 500ms/250ms cap regressed exactly 1 capture (j135 -615.032605 -> -615.032706,
  1.01e-4, time 475->358ms) proving the polish tail is real; a 2s cap is BYTE-IDENTICAL to baseline across all 59
  captures (defensive/non-binding, bounds only the pathological many-turn-tick tail - there is NO actual cold outlier
  in the corpus). OI-11 DONE: GateMip's tree cold-miss no longer re-runs BoundPrunedRecovery cold; added
  `BoundPrunedRecovery.solve(...warmIncumbent)` (folds a feasible incumbent into the pruning floor - MONOTONE, never
  loses a better solution) and GateMip.treeCompletion warm-seeds with the better of {gate best, caller seedBaseline};
  GateMipProbe.loopmm (drives the tree cold-miss directly) still LANDS. Slow suite GREEN (740 tests, 0 fail).
  NOTE: through the engine graph the double-BnB is inert on the corpus, so OI-11 is a monotone win only where
  GateMip.solve's tree fires; the cross-machine determinism confirmation (OI-17) remains a manual user step.
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
- STEP 9 - DONE (2026-08-25): benchmark built (CorpusBench.java, env-gated; OLD worktree at 3d19c9ff) and the
  5-question verdict rendered in BENCHMARK-STEP9.md with re-runnable per-capture TSVs + compare.py under
  benchmark/. Outcome above. RE-RUN this on the final tree for the FULL-SHIP verdict.
  Open follow-up surfaced by the benchmark: fix j003 (the one real regression) - DONE 2026-08-25 (FINISH
  session, top BUILD-LOG entry): objective -30.27 -> -31.30 (TIE with OLD) at FAST and THOROUGH by restoring
  the terminal-window objective hug, and the deadline overrun 11.7s -> 8.4s by adding enumeration-deadline
  checks to BoundPrunedRecovery/GateMip + LongRunSolver outer loops; slow suite GREEN, j021/loopmm no-regress.

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
