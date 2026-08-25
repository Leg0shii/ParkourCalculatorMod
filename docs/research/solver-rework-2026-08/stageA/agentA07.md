# Agent A07 shard: multi-jump via receding horizon

Territory: `solver/LongRunSolver.java`, `graph/nodes/RecedingHorizonNode.java`, and their consistency
against the single-jump path (`AngleSolverEngine.dualChain`) and `FreeStartSolve`.

Files inspected (read in full unless noted):
- `core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/LongRunSolver.java`
- `core/.../anglesolver/graph/nodes/RecedingHorizonNode.java`
- `core/.../anglesolver/solver/ClosedFormSolve.java`
- `core/.../anglesolver/solver/FreeStartSolve.java`
- `core/.../anglesolver/graph/BuiltinGraphs.java`
- `core/.../anglesolver/graph/nodes/DualChainNode.java`
- `core/.../anglesolver/AngleSolverEngine.java` (lines 1-1113, 1679-1777; dualChain + smoothing + notices)
- `core/.../anglesolver/solver/SlpSolve.java` (signatures + entry, lines 1-170)
- `docs/research/angle-solver.md` sections 2.1, 2.1.1, 2.1.2, 2.1.3, 3 (audit), 3.1
- `docs/research/smooth-and-convergence-handoff-2026-08-24.md`
- `docs/research/solver-rework-2026-08/00-context-pack.md`

Method note: gradle was NOT invoked (background compile running), so the wall-clock / objective /
reversal numbers below are re-cited from the two research docs and are labelled prior-measurement,
not-independently-re-run. All code-mechanism claims (file:line, control flow, divergences) are
re-verified this session by direct source reading and are first-hand.

---

## A07-1 What receding horizon computes (multiple-shooting decomposition)
LOCATION: `LongRunSolver.runHorizon` 191-289; `RecedingHorizonNode.execute` 28-53.
CLAIM: The run is sliced into overlapping windows of `WINDOW=10` jumps (`LongRunSolver:43`); each window
is solved to convex-dual global optimality, the first `commit` jumps are committed with their exact
byte-exact exit state (pos, vel, yaw) chained into the next window's seed, and the window slides.
EVIDENCE (code, first-hand): jump boundaries from grounded-tick starts, sub-2-tick pieces merged
(`jumpBoundaries` 364-382); per-window seed carried as `seedPos/seedVel/seedYaw` via
`exact.forward(win,wgf)` at `commitTicks` (270-278, `Vec3dCore(wp.posX[commitTicks]...)`); commit
`ce = last ? we : min(i+min(commit,w), jumps)` then `i = ce` (271, 279); the whole chain is
re-verified byte-exact before return (`solve` 179-182, `viol = compiled.maxViolation(replay, forward)`,
returns null on `viol > feasTol`). Commit ladder `{3,1}` (48), window ladder `{10,7,5,3,2,1}` (50).
IMPACT: correctness / simplicity: the load-bearing multi-jump mechanism; window solve to global
optimality per window with a byte-exact backstop that makes false success impossible.
PROPOSAL: keep as the frame of reference; A07-2..A07-14 audit its internal consistency.
CONFIDENCE: 0.98. DEPENDS-ON: none.

## A07-2 Lead-in windows solve a SURROGATE objective, not the real one
LOCATION: `runHorizon` 214-216.
CLAIM: Only the final window (`last = we==jumps`) carries the user objective; every lead-in window is
handed `new Objective(Axis.Z, Sense.MAX, c-a)` ("any feasible"), so a lead-in window's solve is NOT
the same optimization problem as a standalone single-jump solve of those ticks.
EVIDENCE (code): `Objective obj = last ? new Objective(spec.objective.axis, spec.objective.sense, c-a)
: new Objective(Axis.Z, Sense.MAX, c-a);` (215-216). Confirmed intentional in `angle-solver.md` 2.1.2:
"A lead-in window's objective is only a surrogate ('any feasible')".
IMPACT: correctness/robustness (intended, not a bug): the surrogate is what lets lead-ins solve centered
(A07-3); the objective is only meaningful on the terminal hugging window. This is the first structural
NON-identity between per-window and single-jump solves.
PROPOSAL: document as necessary divergence #1. Do not "fix" toward the real objective on lead-ins;
`angle-solver.md` 2.1.2 measured that hugging lead-ins commits fragile seam states.
CONFIDENCE: 0.97. DEPENDS-ON: A07-3.

## A07-3 Lead-in = centered/robust solve; only the terminal window hugs
LOCATION: `solveWindow` 312-335; `closedForm` 357-360; `SlpSolve.optimizeCentered` (SlpSolve 88-93).
CLAIM: Lead-in windows use `ClosedFormSolve.optimizeRobust` (descending `marginsRobust`
`{5e-2..0}`, keeps clearance) and, on fallback, `SlpSolve.optimizeCentered` (phase-2 hug SKIPPED,
clearance driven toward `centerClearance=2e-2`). The terminal window and the single-jump path use
`ClosedFormSolve.optimize` (ascending `margins {0..1e-2}`, hugs) and `SlpSolve.optimize` (phase-2 hug ON).
EVIDENCE (code): `closedForm` = `last ? ClosedFormSolve.optimize : ClosedFormSolve.optimizeRobust`
(358-359); `y = last ? SlpSolve.optimize(...) : SlpSolve.optimizeCentered(...)` (325-326);
`optimizeCentered` calls `optimize(..., cfg.centerClearance, false=hugObjective, ...)` (SlpSolve 90-93);
`ClosedFormSolve.marginsRobust` largest-first (ClosedFormSolve 43). Measured (prior, doc-cited,
`angle-solver.md` 2.1.2): centered lead-ins dropped j001 from ~133 ms to ~95 ms.
IMPACT: speed (~28% on j001, prior measurement) / robustness. Necessary divergence #2.
PROPOSAL: keep. Note it in the spec's consistency matrix as an intentional per-window difference.
CONFIDENCE: 0.96. DEPENDS-ON: A07-2.

## A07-4 The window solver OMITS RelaxationRecovery that the single-jump chain has
LOCATION: `solveWindow` 312-335 vs `AngleSolverEngine.dualChain` 1703-1744.
CLAIM: `dualChain` (single-jump seed, `DualChainNode`) has a 5-stage ladder: ClosedFormSolve.optimize ->
SlpSolve.optimize -> `RelaxationRecovery.solve` -> alt-seed reseeded SLP -> `levelSetTopUp`. `solveWindow`
has NO RelaxationRecovery and NO reseeded-SLP-of-the-real-objective stage; its fallback is
closed-form(real) -> closed-form(alternates) -> SLP(real) -> SLP(alternates)+hug. So a window that only
`RelaxationRecovery` can crack fails and forces a window/commit shrink, where the same ticks as a lone
single jump would have been recovered.
EVIDENCE (code): `dualChain` 1716-1727 calls `SlpSolve.optimize(...,slpCfg,miss)` then
`RelaxationRecovery.solve(em,spec,FEAS_TOL,cancel,rrCfg)`; `solveWindow` never references
`RelaxationRecovery` (grep of LongRunSolver: no `RelaxationRecovery` symbol). `solveWindow`'s SLP call is
the 4-arg `SlpSolve.optimize(exact, spec, 0.0, cancel)` (325) = default Config, `seed=null`, `miss=null`.
IMPACT: robustness (bounded): a real capability gap between the two paths, but masked because
LongRunSolver shrinks the window and the graph has a `seedMulti` monolithic fallback (A07-17).
PROPOSAL: candidate for folding (A07-10): route the terminal window through `dualChain` so both paths
share the identical recovery ladder. Verify no perf regression from RelaxationRecovery on wide windows.
CONFIDENCE: 0.9. DEPENDS-ON: A07-10, A07-17.

## A07-5 Terminal-window primary SLP result is NOT level-set-topped-up
LOCATION: `solveWindow` 325-327 vs `dualChain.levelSetTopUp` 1719, 1749-1759.
CLAIM: When the terminal window is solved by the primary `SlpSolve.optimize` (line 325), `solveWindow`
returns it directly (`if (y != null || !last) return y;` 327) with NO `LevelSetAscent`. The single-jump
`dualChain` always runs `levelSetTopUp` after SLP (1719). Only `solveWindow`'s ALTERNATE-direction SLP
branch (328-333) applies `hugObjective` -> `LevelSetAscent.improve` (348-355).
EVIDENCE (code): 325-327 vs 348-355; `dualChain` 1716-1719.
IMPACT: correctness/objective (bounded): the terminal window can return an SLP objective short of the
dual bound that `LevelSetAscent` would ladder up, where a lone single jump would not. Partially
recovered downstream only in Optimize/Explore (ILS/bnb nodes), NOT in Fast.
PROPOSAL: apply `LevelSetAscent.improve` to the primary terminal SLP result too, or fold into dualChain
(A07-10). Low risk (LevelSetAscent is strictly feasible-gated).
CONFIDENCE: 0.85. DEPENDS-ON: A07-10.

## A07-6 Multi-jump windows are NOT raced against the byte-exact search; single jumps are
LOCATION: `angle-solver.md` 2.1.3 (line 100); `BuiltinGraphs.build` 175-191; `RecedingHorizonNode` 28-53.
CLAIM: The single-jump path treats the closed-form/LP result as a seed and races byte-exact search
stages that out-reach the linear model; the receding-horizon windows do not get that per window ("its
windows use the closed form internally as surrogates ... only the final user-facing objective carries
the claim", 2.1.3). The measured linear-model under-reach is real (j022 X/MIN: LP -531.6506 vs byte-exact
-531.7001, a 0.0494 b gap; j019 8.6e-3; j005 6.8e-3; j020 4.0e-3 -- prior measurement, doc-cited).
EVIDENCE: `RecedingHorizonNode` emits `Candidate.of(ctx, fromScratch)` with no per-window byte-exact
ascent; downstream nodes act on the whole chain only. `angle-solver.md` 2.1.3 states this explicitly.
IMPACT: correctness/objective: an interior window can leave up to ~0.05 b of reach on the table that a
per-jump byte-exact race would recover; the terminal objective only gets whatever downstream global
nodes (`seamSweep`/`bnb`/`ils`/`wrapIls`) reclaim, which operate on all ticks jointly.
PROPOSAL: UNMEASURED-HYPOTHESIS: a per-window byte-exact micro-ascent on the terminal window (or
committed-jump ascent) closes this; experiment = solve j001 windowed with vs without a per-window
`BucketAscentPolish`, compare terminal X. Route to Stage D/E.
CONFIDENCE: 0.8. DEPENDS-ON: none.

## A07-7 Seam-straddling relative/velocity constraints are dropped in the following window
LOCATION: `sliceConstraints` 420-432.
CLAIM: A window keeps only constraints with BOTH `t1` and `t2` inside `[a, hi]` (F-mode `hi=c-1`, else
`hi=c`). A relative/velocity pair straddling a commit seam (e.g. `t1=bounds[ce]`, `t2=bounds[ce]-1`) is
enforced in the WIDER committing window that first sees both endpoints (windows overlap: next window
starts at `bounds[ce] < bounds[we]`), but is DROPPED from the following window (its `t2` is now outside).
EVIDENCE (code): `in2 = jc.t2 == null || (jc.t2>=a && jc.t2<=hi)` (425); no branch converts a
seam-crossing pair into a trivial tick-0 constraint. The `angle-solver.md` 2026-06-10 audit (line 119)
claims such pairs are "re-checked as trivial tick-0 constraints in the next" window; the CURRENT
`sliceConstraints` does not do that (it drops them). The full-run byte-exact re-verify (`solve` 179-182)
is the actual backstop: a seam-coupling violation returns null and the commit/window ladder retries.
IMPACT: robustness/correctness: no false success (re-verify catches it), but a feasible instance with a
seam-crossing relative/velocity wall can fail-to-solve or need a ladder retry it would not need if the
following window re-imposed the frozen-endpoint half. Discrepancy with the audit text should be resolved.
PROPOSAL: in `sliceConstraints`, when a pair has one endpoint in the committed prefix (frozen exact) and
one in the window, emit the reduced single-tick constraint (substitute the frozen value) rather than
dropping it. Then re-verify the audit's "trivial tick-0" claim holds.
CONFIDENCE: 0.82. DEPENDS-ON: none.

## A07-8 Windows are smooth-unaware; smoothing is a single global post-pass
LOCATION: `runHorizon` 215-216, `suffixSpec` 390 (3-arg `Objective`, drops `smoothLambda`);
`AngleSolverEngine.runJob` 1019-1030 (`smoothFacing`); `BuiltinGraphs` `smoothFinal` node 160-161.
CLAIM: Every window `Objective` is built with the 3-arg constructor, dropping `objective.smoothLambda`,
so `turnCost` reversal-penalty ranking is inert inside windows. Smoothing (DeWiggle, SmoothingPolish,
turnCost, final face-walk) runs ONCE on the finished full chain, identically for single and multi jump.
EVIDENCE (code): 215-216 and 390 use `new Objective(axis, sense, len)`; `Objective` with smoothLambda is
the 4-arg form built only in `AngleSolverEngine.buildJob` 386. `smoothFacing` (runJob 1019-1030) applies
to the winning yaws regardless of jump count; `SMOOTH_OBJ_SLACK=3e-4`, budget 400ms/`deadline/8`/6s cap
(AngleSolverEngine 1088-1090). thousand/1-dup2: 15 -> 10 reversals via this final face-walk (prior
measurement, handoff RESOLUTION section).
IMPACT: smoothness (minor inconsistency): the final smoothing IS consistent across single/multi; only the
window-internal ranking ignores smoothness. Low practical cost because the final face-walk dominates.
PROPOSAL: note in the consistency matrix that smoothing is a global post-pass, not window-decomposed.
No change needed unless a smooth-by-construction window objective is pursued (see A07-11 / BlockSolveProbe).
CONFIDENCE: 0.9. DEPENDS-ON: none.

## A07-9 Per-window duality-gap coping: it DOES fall to SLP per window; that is where the jitter lives
LOCATION: `solveWindow` 312-335; `angle-solver.md` 2.1.1; handoff section 4 + RESOLUTION.
CLAIM: When a window carries the degenerate-recovery / duality gap (context-pack section 5; measured on
j021 four-jump and thousand/1 three-jump), the closed form returns null at every margin AND every
alternate direction, and `solveWindow` falls to `SlpSolve.optimize` (terminal) which closes the gap
primally by hugging the opposing-pair corridors byte-exact -- and that byte-exact hug is exactly the
source of the reversal jitter.
EVIDENCE (code): fallback order in `solveWindow` (314 closed-form real; 316-322 closed-form alternates;
325 SLP real; 328-333 SLP alternates). Prior measurement (re-cited): j021 dual converges to 5e-9 pgres
yet recovered path 0.34 b infeasible at every margin; thousand/1 dual grinds MAX_ITER=100 at pgres 2.435,
min recovery violation ~2.89 b across 100k reference iterates (handoff RESOLUTION); the byte-exact max-X
vertex dithers at redirect zones t13-t17, t26-t30 (handoff). NOT independently re-run this session.
IMPACT: correctness/smoothness: the jitter is a genuine duality-gap artifact, not a windowing bug; the
per-window SLP is the correct-and-only closer. thousand routes here because 3 jumps < WINDOW so it is a
single terminal window (jumps<WINDOW => one `last` window covering all ticks).
PROPOSAL: no local fix; this is the Stage-0 H1-vs-H2 SOCP/SDP question. Confirm the gap is intrinsic
(circle-vs-disk) vs recoverable (dual-face degeneracy) with COPT before any windowing change.
CONFIDENCE: 0.88. DEPENDS-ON: A07-11.

## A07-10 Single-jump path is NOT foldable into LongRunSolver at zero behavior change (today)
LOCATION: `BuiltinGraphs.build` 176-177 (routing split), `dualChain` 1703-1744, `solveWindow` 312-335.
CLAIM: The handoff open-direction-4 idea "a one-jump run is one final window" is structurally true
(jumps==1 => one `last` window, real objective), but folding it today would CHANGE behavior: the graph
routes `JUMPS_LE_ONE` TRUE to `seedSingle`=`dualChain`, never through LongRunSolver, and `dualChain`
carries RelaxationRecovery (A07-4), always-on `levelSetTopUp` (A07-5), reseeded-SLP structure, and a
`ClosestMiss` diagnostic sink that `solveWindow` lacks.
EVIDENCE (code): `g.edge("rJumps", TRUE, "seedSingle")` and `FALSE, win?"horizon":"seedMulti"` (176-177);
`solveWindow` never calls RelaxationRecovery/levelSetTopUp on the primary path.
IMPACT: simplicity (potential): a real fold exists but requires unifying the recovery ladder first, not a
free rename. Verified NOT zero-change.
PROPOSAL: the clean unification is the reverse: make `solveWindow(last=true)` delegate to `dualChain`
(share one recovery ladder), then single-jump and terminal-window become the same code. Measure j004-j022
and j001 for regressions (RelaxationRecovery cost on wide terminal windows is the risk).
CONFIDENCE: 0.85. DEPENDS-ON: A07-4, A07-5.

## A07-11 A single global convex formulation cannot replace the windowing (measured obstruction)
LOCATION: `angle-solver.md` 2.1 (line 74), context-pack section 5, handoff section 4/RESOLUTION.
CLAIM: The windowing exists because the monolithic convex dual does NOT converge across a full run, and
even a converged dual would not recover (duality gap on coupled seams). Position is affine in the inputs
(so the primal is exactly linear per fixed direction), but the constant-modulus nonconvexity plus
cross-seam coupling defeats a single global convex solve.
EVIDENCE (prior measurement, re-cited): full-span dual hits its cap 14 to 88 b from feasibility; cap
100->1000->8000 moved error 14.4->17.7->15.5 b at 146 s (angle-solver.md 2.1); thousand/1 duality gap
2.89 b min recovery violation across 100k iterates (handoff). NOT re-run this session (gradle blocked).
IMPACT: simplicity (negative result): "collapse windowing into one convex solve" is measured-dead for the
dual. Whether a global SOCP/SDP relaxation is tight (H2) or loose (H1) is unresolved and gates capability
target 4 for multi-jump.
PROPOSAL: route to Stage-0 COPT on `f2f-dfchain-multijump.json` + `df-chain-free-start.json`: solve the
SOCP disk relaxation (read per-tick modulus slack) and the SDP/Shor relaxation (read rank). If rank-one
and tight, a global convex solve could replace windowing; if loose, windowing stays.
CONFIDENCE: 0.85. DEPENDS-ON: A07-9.

## A07-12 Cross-window warm-starts are UNBUILT; WindowCache is a result-memo only
LOCATION: `WindowCache` 106-108, `windowKey` 291-298, `runHorizon` 217-237.
CLAIM: There is NO carry of the dual multipliers (`lambda`) from one window's `CostateDualSolver` into
the next; each window builds a fresh solver inside `ClosedFormSolve.runLadder`. `WindowCache` memoizes
whole-window RESULTS keyed by the exact byte-exact seed (`a:c:last:pos.bits:vel.bits:yaw.bits`), so it
only hits when an identical `(a,c,seed)` recurs -- chiefly the first window `[0,c)` across commit-ladder
rungs (identical seed) and the shared `windows` cache between `solve` and `solveFree`.
EVIDENCE (code): `windowKey` folds `doubleToLongBits` of pos/vel + `floatToIntBits(yaw)` (291-298); cache
put/get around `solveWindow` (219-237); `windows` passed to both `solve` (RecedingHorizonNode 34) and
`solveFree` (37). No `warm`/`lambda` field survives a window boundary (warm-start lives only INSIDE one
`runLadder`, ClosedFormSolve 397).
IMPACT: speed (opportunity, not a bug): adjacent windows overlap by `(we-ce)` jumps and share physics
structure; a seam-to-seam dual warm-start could cut per-window iterations. Currently unexploited.
PROPOSAL: UNMEASURED-HYPOTHESIS: warm-start window k+1's dual from window k's lambda on the overlap.
Experiment = instrument `CostateDualSolver.lastIters` per window on j001 with and without a seeded lambda.
CONFIDENCE: 0.83. DEPENDS-ON: none.

## A07-13 Free-start is present but FIRST-WINDOW-ONLY in the receding horizon
LOCATION: `solveFree` 128-152, `runHorizon` free retry 238-267, `RecedingHorizonNode` 36-49.
CLAIM: Free-start composes with multi-jump only at the run's start: the free retry fires solely at
`i == 0 && freeBox != null`, routing through `FreeStartSolve.solveJoint`; interior seams are always
pinned to the chained exact exit. `RecedingHorizonNode` only attempts the free window after the pinned
`solve` returns null and `ctx.freeStart && !ctx.stageLocked()`.
EVIDENCE (code): `if (yaws == null && i == 0 && freeBox != null)` (238); `FreeStartSolve.solveJoint(...)`
(254); `runHorizon` for interior windows seeds `sliceScenario(..., seedPos, seedVel, seedYaw)` pinned
(212, 395-417); `RecedingHorizonNode` 36-40 gate. Free retry is per-window-boundary cached in
`retryCache`/`FREE_RETRY_MISS` (239-260).
IMPACT: correctness (intended): free-start is a start-position choice, so first-window-only is correct;
noted for the consistency matrix as free-start PRESENT for multi-jump but scoped to the start.
PROPOSAL: none; confirm the spec states free-start + multi-jump = first-window joint solve only.
CONFIDENCE: 0.9. DEPENDS-ON: none.

## A07-14 dF on a multi-jump run: supported inside a window, gap across seams
LOCATION: `sliceConstraints` F-mode `hi=c-1` (423); `runHorizon` seam chain `seedYaw` (278);
`AngleSolverEngine` DF notices 1097-1108; context-pack section 4 capability list.
CLAIM: A dF constraint fully inside one window is handled (ClosedFormSolve via FacingPrefold/ChainScan,
or SlpSolve via YawTies). dF continuity ACROSS a commit seam is not enforced: the seam carries only the
scalar `seedYaw = wgf[commitTicks-1]`, and a relative dF pair straddling the seam is dropped by
`sliceConstraints` (same mechanism as A07-7). Only dF = 0 is supported at all; non-zero dF is declined
engine-wide (`DF_UNSUPPORTED_NOTICE`, 1097).
EVIDENCE (code): F-mode slice bound 423; seam yaw scalar 278; `isUnsupportedDf` gate 1092-1095. The
target-capability list (context-pack section 4, item 2) names dF=0 pinning composing with free-start as a
NORTH STAR, i.e. not fully delivered for multi-jump.
IMPACT: correctness (capability gap): a dF=0 pin whose no-turn tick sits at a commit boundary is not
guaranteed across the seam; the full-run re-verify backstops false success but the instance may fail.
PROPOSAL: same fix as A07-7 (reduce seam-crossing dF pairs against the frozen `seedYaw` rather than
dropping). Add a multi-jump dF capture to the corpus (`f2f-dfchain-multijump.json` exists) as the gate.
CONFIDENCE: 0.8. DEPENDS-ON: A07-7.

## A07-15 Coupling horizon ~5 jumps and WINDOW/COMMIT rationale (re-verified provenance)
LOCATION: `angle-solver.md` 2.1 (line 78) and 3.1 (line 138); `LongRunSolver` 42-51.
CLAIM: The "~5 jump coupling horizon" is a measurement on j001 (353 ticks, 30 jumps, 81 constraints):
lookahead <=4 fails, >=5 solves, greedy fails outright; the 8/10/12 x 2/3/4 window/commit grid all solve
20/20; window 12 up to 2x slower, window 8 ~20% faster but its default lookahead of 5 has no margin.
`10/3` gives lookahead 7 = horizon+2.
EVIDENCE (doc, prior measurement, re-cited not re-run): `angle-solver.md` line 78 and line 138. Code
matches: `WINDOW=10` (43), `COMMIT_LADDER={3,1}` (48) => lookahead 7 then 9.
IMPACT: robustness: the shipped constants carry a measured margin above the coupling horizon.
PROPOSAL: keep. If Stage-E re-runs the grid, re-verify 20/20 on the CURRENT build (numbers predate the
CMA removal train PRs 373/375).
CONFIDENCE: 0.85. DEPENDS-ON: none.

## A07-16 Consistency matrix (single-jump vs receding-horizon window vs FreeStartSolve)
LOCATION: synthesis of A07-2..A07-14.
CLAIM: Enumerated capability presence:
- Objective: single-jump=real; lead-in window=Z/MAX surrogate; terminal window=real; FreeStart=real.
- Wall handling: single-jump/terminal=hug (ascending margins); lead-in=centered (robust/optimizeCentered).
- RelaxationRecovery: single-jump=YES (dualChain 1723); window=NO; FreeStart(joint)=NO (own pattern B&B).
- LevelSetAscent top-up: single-jump=always; window=only alternate-direction branch, NOT primary terminal.
- ClosestMiss diagnostics: single-jump=threaded (dualChain miss arg); window=null; FreeStart=n/a.
- Byte-exact per-jump race: single-jump=yes (2.1.3); window=no.
- Caching: dual warm-start across windows=UNBUILT; WindowCache result-memo=present (narrow).
- Smoothing: global final post-pass for all; window-internal turnCost=DROPPED (3-arg Objective).
- Defaults: window 10 / commit {3,1} / window-ladder {10,7,5,3,2,1}; CUSTOM effort overrides via
  `LongRunConfig.of(window,commit)` (AngleSolverEngine 88-92).
- dF: within-window supported; cross-seam gap; non-zero dF declined globally.
- Free-start: multi-jump=first-window-only joint solve; single-jump=full FreeStartSolve.
EVIDENCE: all rows cited in the referenced findings.
IMPACT: simplicity/correctness: five of these rows (RelaxationRecovery, LevelSetAscent, ClosestMiss,
per-jump race, dual warm-start) are capabilities present on some paths and absent on others -- exactly
the "inconsistent gaps" target-capability 5 asks to eliminate.
PROPOSAL: the spec should adopt A07-10's unification (terminal window delegates to dualChain) plus A07-7
seam reduction; that collapses the RelaxationRecovery / LevelSetAscent / ClosestMiss / dF-seam rows in
one move. The per-jump race and dual warm-start remain separate follow-ups.
CONFIDENCE: 0.86. DEPENDS-ON: A07-2, A07-3, A07-4, A07-5, A07-6, A07-7, A07-8, A07-12, A07-13, A07-14.

## A07-17 The monolithic dual chain is still reachable as the multi-jump FALLBACK
LOCATION: `BuiltinGraphs.build` 177, 184, 192-195; `RecedingHorizonNode` 51-52.
CLAIM: If receding horizon returns NONE, the graph routes multi-jump to `seedMulti` (a `dualChain` with
`keepBetter=true`), i.e. the monolithic dual over ALL ticks is still tried as a fallback -- the same
solve `angle-solver.md` 2.1 measured as non-converging on long runs, but viable on short multi-jump runs
(few jumps) where the dual still converges.
EVIDENCE (code): `g.edge("rJumps", FALSE, win?"horizon":"seedMulti")` (177); `g.edge("horizon", NONE,
"seedMulti")` (184); `seedMulti` = `dualChain` keepBetter (81-83). `RecedingHorizonNode` returns
`Guarantee.NONE` when both pinned and free solves miss (51).
IMPACT: robustness: short coupled multi-jumps (e.g. thousand's 3 jumps) can be solved either by the single
terminal window OR by the monolithic seedMulti; this is a second per-window/monolithic non-identity worth
noting (they can produce different candidates; the graph keeps the better).
PROPOSAL: note in the spec that multi-jump has TWO seed producers (horizon, seedMulti); a unification
(A07-10) should keep the monolithic fallback for the small-jump-count regime where it converges.
CONFIDENCE: 0.88. DEPENDS-ON: A07-10.

---

Summary for the orchestrator: receding-horizon does NOT behave identically to a single-jump solve on
each window. The differences are, in decreasing "necessary vs gap" order: (necessary, measured)
surrogate objective + centered solve on lead-ins (A07-2/3), no per-window byte-exact race by design
(A07-6); (gaps to close) missing RelaxationRecovery (A07-4), missing primary-SLP LevelSetAscent (A07-5),
seam-crossing relative/velocity/dF constraints dropped in the following window (A07-7/A07-14), no
cross-window dual warm-start (A07-12), window-internal smoothness ignored (A07-8). The single-jump path
is NOT foldable at zero change today (A07-10) but the clean fold is to make the terminal window delegate
to `dualChain`. A single global convex formulation is measured-dead for the dual (A07-11) and gated on
the Stage-0 H1-vs-H2 SOCP/SDP result. All non-code numbers are prior measurements re-cited from
`angle-solver.md` / the 2026-08-24 handoff, NOT re-run this session (gradle was blocked).
