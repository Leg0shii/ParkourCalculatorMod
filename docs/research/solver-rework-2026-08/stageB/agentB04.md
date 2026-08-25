# Stage B shard B04: redundant recomputation and caching gaps

Agent: B04
Territory: REDUNDANT RECOMPUTATION and CACHING GAPS. Model rebuilds, forward() vs stepRange, cross-window
dual warm-start, WindowCache, ranked by wall-clock.

## Method (deterministic, contention-free, NO gradle)
- Instrumented AtomicLong counters (guarded by a default-off `SolveCounters.ENABLED`) added to
  `JumpLinearModel` (ctor = model build == one `precompute()`; `compileWall`; `compileWalls`;
  `objectiveVectors`; `velocityWalls`; `keepAliveWall`), `ExactJumpModel` (`forward`; `stepRange`;
  `stepRange` with `from>0`), `CostateDualSolver` (ctor; `solve`; cold vs warm by `warm==null`; per-loop
  iteration; stall break), `LongRunSolver.runHorizon` (window-cache hit/miss), and a per-node snapshot in
  `GraphRunner.walk` (forward/model/dualSolve/dualIters deltas + wall-clock per node). All counter code and
  a probe `core/src/test/.../anglesolver/B04CensusProbe.java` were compiled to an OVERLAY dir and reverted
  after; `core/build/classes` left pristine. MUST-REVERT: the counter edits + `SolveCounters.java` +
  `B04CensusProbe.java` are temporary.
- Probe modes: `direct` = build spec via `engine.debugBuildSpec()`, call `AngleSolverEngine.dualChain`
  (100% reproducible, single-thread); `cfonly` = `ClosedFormSolve.optimize`; `longrun` =
  `LongRunSolver.solve`; `engine` = full graph `engine.solve()` + poll to completion.
- Run via direct `java -cp <overlay>;<build/classes>;<deps>` with JUnitCore. JVM: Temurin 25. Captures:
  `j021-rinav1-01` (n=39, 13 walls, coupled multi-jump), `j008b-2jump` (n=25, 10 walls, coupled 2-jump),
  `df-chain-free-start` (n=15, 18 walls, free-start box), `j001` (n=353, 30 jumps, 81 walls, long run).
- Every FAST/direct/longrun table below was byte-identical across 2 to 3 repeats (counts reproducible to
  the unit). THOROUGH tables are anytime/time-budgeted and therefore vary run to run; reported as ranges.
- Byte-exact verification: every produced yaw replayed through `sc.toGameFacings` + `ExactJumpModel.forward`
  + `JumpConstraintCompiler.maxViolation`; all reported solves land `viol=0.0` (see B04RESULT lines).

---

## B04-1 Model-rebuild census per dualChain: tens of JumpLinearModel builds, hundreds of wall compiles, zero cache
LOCATION: `AngleSolverEngine.dualChain` 1703-1744; `ClosedFormSolve.optimizeReturning`/`runLadder`;
`JumpLinearModel` ctor/`compileWall`/`objectiveVectors`.
CLAIM: One `dualChain` call rebuilds the scenario-only `JumpLinearModel` (with its per-tick atan2/hypot
`precompute`) tens of times and recompiles each wall ~20-44x, with no model or wall cache anywhere; this
confirms and extends A02-5's "~9x per direction, ~36x per dualChain" prediction with measured per-capture
numbers.
EVIDENCE (direct mode, deterministic; one `dualChain` solve):
| capture | n | walls | modelBuilds | compileWall calls | compileWalls(list) | objectiveVectors | dualCtor | outcome |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| j021 | 39 | 13 | 20 | 248 | 3 | 18 | 4 | closed form -> SLP -> level set |
| j008b | 25 | 10 | 43 | 436 | 3 | 40 | 4 | closed form -> SLP |
| df-chain-free-start | 15 | 18 | 18 | 138 | 7 | 8 | 8 | null (dualChain has no free-start; wrong path) |
compileWall/wall ratio: j021 248/13 = 19.1x, j008b 436/10 = 43.6x. Only 3 of the compiles route through the
`compileWalls` list method; the rest (245 on j021) are per-margin/per-pass individual `compileWall` calls.
IMPACT: speed (LOW absolute) + simplicity. A02-5's BuildBench put `new JumpLinearModel` at ~1.04 us at n=49,
so ~20-43 builds is ~20-45 us/solve, negligible vs the 0.1-800 ms envelope; the real cost is the
consistency defect (no `JumpLinearBase(scenario)` shared immutable, so every objective/direction/margin pays
the trig again).
PROPOSAL: split `precompute` into an immutable scenario base shared across passes/objectives/directions plus
a thin pattern view (A02-5); compile each wall once and reuse across margin rungs.
CONFIDENCE: 0.92. DEPENDS-ON: A02-5.

## B04-2 stepRange is dead at runtime, not just by grep: stepRangePartial==0 on every solve measured
LOCATION: `ExactJumpModel.forward` 131 (sole `stepRange` caller, `from=0`); `stepRange` 141.
CLAIM: Across every capture and every mode (direct, longrun, engine FAST, engine THOROUGH) the counter
`stepRange == forward` exactly and `stepRangePartial (from>0) == 0`, so 100% of trajectory re-evaluations
are full O(n) forwards from tick 0; the incremental-tail capability the class doc advertises is inert.
EVIDENCE: every COUNTERS block printed `forward == stepRange` and `stepRangePartial=0` (e.g. j001 engine
FAST forward=141260 stepRange=141260 stepRangePartial=0; j008b THOROUGH forward=2597756 stepRange=2597756
stepRangePartial=0). `grep '\.stepRange('` over `core/src` (excluding ExactJumpModel) returns nothing.
IMPACT: speed. This is the single largest recompute LEVER: every million-forward polisher (B04-3, B04-4)
does a full O(n) recompute for a single/two-tick change that `stepRange(from=t)` would make O(n-t).
PROPOSAL: route the anytime polishers' single-tick rescoring through `stepRange` on a persistent
`ForwardPath` (A02-6 predicts ~2x on the dominant cost). Guard with a full-forward equivalence check.
CONFIDENCE: 0.97. DEPENDS-ON: A02-4, A02-6, B04-3, B04-4.

## B04-3 SmoothingPolish full-forwards dominate the FAST solve (96 to 99.96% of all forwards) and run even with Smooth (TAS) OFF
LOCATION: `graph/nodes/SmoothingNode.execute` 44-53 (always runs `SmoothingPolish.smooth`; DeWiggle only
when `smoothLambda>0`); `SmoothingPolish.smooth` eval 192 (`model.forward` per single-tick move), 67
(per-tick loop).
CLAIM: On a default (Smooth-off) FAST solve the `smoothing` node performs the overwhelming majority of all
`forward()` calls, every one a full O(n) recompute of a single-tick perturbation, and this happens even
though `smoothLambda=0` (the saves carry no smoothLambda), because `SmoothingPolish.smooth` runs
unconditionally as an objective-preserving roughness tie-break (F15/A09-5), the same metric A08 measured as
roughness-blind.
EVIDENCE (engine FAST, deterministic; `forward` total and the `smoothing` node's share):
| capture | n | total forward | smoothing forward | smoothing % | smoothing wall-clock (warm) | total wall-clock (warm) |
| --- | --- | --- | --- | --- | --- | --- |
| j021 | 39 | 18118 | 17925 | 98.9% | 31 ms | ~88 ms |
| j008b | 25 | 5467 | 5292 | 96.8% | 12 ms | ~50 ms |
| df-chain-free-start | 15 | 1440 | 1382 | 96.0% | 7 ms | ~49 ms |
| j001 | 353 | 141260 | 141202 | 99.96% | 3051-3940 ms | 3441-4208 ms |
j001's save has no `smoothLambda` field (effort=FAST, Smooth off), yet SmoothingPolish still ran 141202 full
O(353) forwards (~50M tick-steps) taking ~3.5 s, which is ~94% of the whole solve wall-clock. Derived rate:
~22-28 us/forward at n=353, ~1.7 us at n=39 (~30-80 ns/tick-step).
IMPACT: speed, LARGE on long runs (a smooth-OFF j001 spends 94% of its time in a roughness polish the user
did not request); smoothness/correctness (the metric is A08-measured-blind). This is recompute waste #1 by
wall-clock on long runs.
PROPOSAL: (a) gate `SmoothingPolish.smooth` on `smoothLambda>0` so Smooth-off is a true yaw no-op (closes
A09-5); (b) when it does run, route its per-tick rescoring through `stepRange` (B04-2). Either alone is a
large win on long runs.
CONFIDENCE: 0.9. DEPENDS-ON: B04-2, A09-5, A08-9.

## B04-4 THOROUGH: ilsPolish issues millions of full forwards; forward count is time-bounded, all via dead stepRange
LOCATION: `graph/nodes/IlsPolishNode` -> `IlsPolish`/`BucketAscentPolish` (full-forward scorers);
`BuiltinGraphs.build` ils node.
CLAIM: A THOROUGH solve of the coupled j008b (n=25, 5 s optimize budget) drives 2.6 to 3.2 MILLION full
O(n) forwards, 97-98% of them inside `ilsPolish`, each a single/two-tick perturbation; the count is
proportional to the time budget (anytime), confirming A02-6's "spend the whole budget on full forwards"
at engine scale (A02 measured 36M evals in a direct BucketAscent at n=49).
EVIDENCE (engine THOROUGH j008b, 5 s optimize, 2 runs; per-node forward):
| run | total forward | ilsPolish fwd (%) | bnb fwd | seamSweep fwd | ilsPolish ms |
| --- | --- | --- | --- | --- | --- |
| 0 | 2,597,756 | 2,546,069 (98.0%) | 42,267 | 2,281 | 2049 |
| 1 | 3,173,580 | 3,082,533 (97.1%) | 79,531 | 4,377 | 2274 |
stepRangePartial=0 in both (every one a full forward). ilsPolish is the largest single wall-clock consumer
in THOROUGH (~2.0-2.3 s of the ~3.8 s solve).
IMPACT: speed, LARGE. Same lever as B04-2/B04-3: incremental `stepRange` rescoring roughly halves the
dominant polisher cost.
PROPOSAL: incremental scorer through `stepRange` for IlsPolish/BucketAscentPolish/WrapWindowIls.
CONFIDENCE: 0.9. DEPENDS-ON: B04-2, A02-6.

## B04-5 THOROUGH: bnb and seamSweep are model + dual REBUILD storms (no model cache, fresh dual per node)
LOCATION: `BoundPrunedRecovery` (`makeNode` builds a fresh dual + full forward per pattern node);
`SeamSweepRecovery`; per-node counters via GraphRunner.
CLAIM: In THOROUGH, `bnb` rebuilds the JumpLinearModel and re-solves the dual per pattern node with no
cache, producing hundreds to thousands of model builds and hundreds of thousands of dual iterations, which
is the #2 wall-clock cost after ilsPolish and is dominated by dual/model rebuild, NOT forwards.
EVIDENCE (engine THOROUGH j008b, per-node; 2 runs):
| node | modelBuilds | dualSolve | dualIters | forward | wall-clock (warm) |
| --- | --- | --- | --- | --- | --- |
| bnb | 1352-1986 | 3888-6771 | 257,280-494,174 | 42,267-79,531 | 726-789 ms |
| seamSweep | 511-709 | 840-1105 | 73,750-91,626 | 2,281-4,377 | 705-710 ms |
Whole-solve totals rose to modelBuilds 1922-2754, compileWall 29045-42809, dualIters 334K-589K (vs
modelBuilds=16 in FAST). Each bnb node pays the scenario `precompute` trig and a cold dual again.
IMPACT: speed, MEDIUM in THOROUGH (~0.7 s each). A shared immutable `JumpLinearBase` (B04-1) + a
per-pattern thin view would remove the model-rebuild half; the dual-per-node is inherent to B&B but could
warm-start from the parent node's lambda.
PROPOSAL: pass a prebuilt scenario base and a warm lambda into each bnb/seamSweep sub-solve.
CONFIDENCE: 0.82. DEPENDS-ON: B04-1.

## B04-6 The dual never converges on coupled captures: ~72 to 100 iters per solve, DIVERGE bail never fires
LOCATION: `CostateDualSolver.solve` MAX_ITER=100 loop; DIVERGE_PGRES=4.0 stall bail.
CLAIM: On every coupled capture, the mean dual iterations per `solve()` sit near the MAX_ITER=100 cap and
`dualStalled` stays 0, so the dual grinds its full iteration budget without converging and without tripping
the divergence bail (pgres stays below 4.0 but above GRAD_TOL), exactly the context-pack section-5 grind,
now measured per capture.
EVIDENCE (direct/longrun mode; dualIters / dualSolve = mean iters/solve; dualStalled):
| capture | dualSolve | dualIters | mean iters/solve | dualStalled |
| --- | --- | --- | --- | --- |
| j021 (direct) | 15 | 1500 | 100.0 | 0 |
| j008b (direct) | 11 | 787 | 71.5 | 0 |
| df-chain (direct) | 32 | 2944 | 92.0 | 0 |
| j001 (longrun) | 18 | 1470 | 81.7 | 0 |
(The per-loop counter overcounts by <=1 per solve on the breaking iteration; negligible.)
IMPACT: speed (MEDIUM) + correctness context. Dual iterations are individually cheap (O(m*n) grad + free-set
Cholesky), but they multiply badly inside bnb (B04-5: 257K-494K). The wasted iterations are the ones between
the true optimum face and MAX_ITER; raising MAX_ITER is measured-dead (context pack), so the lever is
warm-starting (B04-7) and the H1/H2 face question (F1), not more iterations.
CONFIDENCE: 0.9. DEPENDS-ON: F1.

## B04-7 Cross-window dual warm-start is UNBUILT: j001 solves 8 windows, each dual COLD (one per window), re-verified
LOCATION: `LongRunSolver.runHorizon` window loop 191-289; `windowKey` 291-298; `ClosedFormSolve.runLadder`
(warm only WITHIN one window's margin ladder).
CLAIM: A full j001 long-run solve solves 8 windows and issues exactly 8 COLD dual solves (lambda=0), one per
window's first `ClosedFormSolve` solve; the only warm-starts are the 10 intra-window margin-ladder solves.
No lambda is carried across a window seam despite adjacent windows sharing ~70% of walls, confirming A07-12
("WindowCache is a result-memo, cross-window warm-start unbuilt").
EVIDENCE (j001 longrun, deterministic, 2 runs identical):
`windowCacheMiss=8 windowCacheHit=0; dualCtor=8 dualSolve=18 dualCold=8 dualWarm=10 dualIters=1470`.
8 misses = 8 windows solved; dualCold=8 = one cold solve per window; the 10 dualWarm are the margin ladder
inside windows (`runLadder` warm-starts the dual across margin rungs only). `windowKey` folds the byte-exact
seed bits (`doubleToLongBits` pos/vel + `floatToIntBits` yaw), so it is a pure result memo, never a lambda
carrier. The whole windowed solve (recedingHorizon node) is 195-206 ms wall-clock (1470 dual iters + 16
model builds + SLP + per-window byte-exact), of which the cold dual solves are a fraction.
IMPACT: speed, LOW absolute on this run (the ~8 cold solves at ~95 iters are ~760 of 1470 iters inside a
~200 ms node), but it is a clean, real, currently-unexploited caching opportunity; angle-solver.md 5.1
estimates 2.5-5x fewer iters on the warm-started windows.
PROPOSAL: seed window k+1's dual from window k's lambda on the shared-wall overlap. UNMEASURED-HYPOTHESIS
for the speedup: experiment = re-run j001 longrun instrumented with `CostateDualSolver.lastIters` per window
with vs without a seeded lambda; expect the 8 cold solves' iters to drop toward the warm rate (~40-70).
CONFIDENCE: 0.88. DEPENDS-ON: A07-12.

## B04-8 WindowCache is a narrow result-memo: 0 hits on the FAST long run; hits only on THOROUGH commit-ladder re-entry; no re-solve storm lives here
LOCATION: `LongRunSolver` `WindowCache` 106-108, `runHorizon` cache get/put 217-237; shared between `solve`
and `solveFree` (RecedingHorizonNode 34, 37).
CLAIM: The cache memoizes whole-window RESULTS keyed by `(a,c,last,seedBits)` and only hits when an identical
`(a,c,seed)` recurs. On the FAST j001 solve it never hits (the first commit-ladder rung {3} solves the run,
so no rung {1} retry re-presents the identical `[0,c)` seed). Hits appear only under THOROUGH, when a later
node re-enters the horizon and re-presents identical windows. There is NO re-solve storm attributable to the
cache; the THOROUGH storm is bnb's model rebuilds (B04-5), not repeated window solves.
EVIDENCE: j001 engine FAST `windowCacheHit=0 windowCacheMiss=8`; j008b engine THOROUGH `windowCacheHit=6-12
windowCacheMiss=8-14` (hits ~= misses, i.e. each cached window re-presented ~once, no storm). The cache maps
`String -> double[]`, unbounded, but window counts are tiny (<=~14).
IMPACT: correctness/simplicity: the cache is doing its narrow job (dedup identical window re-solves across
commit-ladder rungs and the solve/solveFree pair); it is not a general model/dual cache and cannot become one
(it is keyed by exact seed bits). No defect, one confirmed limitation.
CONFIDENCE: 0.85. DEPENDS-ON: A07-12, B04-7.

## B04-9 Wall compilation is recomputed ~8 to 44x per constraint even on the winning FAST path
LOCATION: `JumpLinearModel.compileWall`/`compileWalls`; ClosedFormSolve margin ladder + prefold passes.
CLAIM: Each position wall is recompiled from scratch many times per solve because every model rebuild
(B04-1) and every margin rung recompiles its walls; there is no compiled-wall cache keyed by
(scenario, constraint).
EVIDENCE (compileWall calls / walls): j021 direct 248/13 = 19.1x, j008b direct 436/10 = 43.6x, j021 engine
FAST 104/13 = 8.0x, j001 engine FAST/longrun 211/81 = 2.6x, df-chain engine FAST 156/18 = 8.7x.
IMPACT: speed, LOW absolute (compileWall is O(n) arithmetic, no trig), but a consistency defect: the wall
coef arrays are pure functions of (scenario, pattern, margin) and could be compiled once and margin-shifted
(the rhs is the only margin-dependent term, `bPrime -= margin`).
PROPOSAL: compile walls once per (scenario, pattern); apply margin as a scalar rhs shift in the dual (as the
margin ladder already partly does in `runLadder`).
CONFIDENCE: 0.85. DEPENDS-ON: B04-1.

## B04-10 Recompute wastes ranked by wall-clock (measured, per node)
LOCATION: synthesis of B04-1..B04-9 with GraphRunner per-node wall-clock.
CLAIM: Ranked by measured wall-clock impact:
1. FULL-FORWARD POLISHING via dead stepRange (B04-2/3/4). Long-run FAST: SmoothingPolish 141202 forwards =
   ~3.5 s = 94% of j001's solve. THOROUGH: ilsPolish 2.5-3.1M forwards = ~2.2 s = ~58% of j008b's solve.
   Lever: `stepRange` incremental rescoring (~2x) + gate SmoothingPolish on smoothLambda>0 (removes it
   entirely from smooth-off solves).
2. bnb/seamSweep MODEL+DUAL REBUILD STORM in THOROUGH (B04-5): ~0.7 s each; hundreds-to-thousands of model
   builds + 250K-490K dual iters per solve. Lever: shared immutable scenario base + warm dual per node.
3. DUAL MAX_ITER GRIND on coupled captures (B04-6): ~72-100 iters/solve, dualStalled=0; individually cheap
   but multiplied inside bnb. Lever: cross-window/parent-node warm-start (B04-7), NOT more iterations.
4. CROSS-WINDOW dual COLD re-solve (B04-7): 8 cold solves on j001, LOW absolute (~a fraction of a 200 ms
   node). Lever: seam lambda warm-start (unbuilt).
5. JumpLinearModel + wall REBUILDS with no cache (B04-1/B04-9): ~20-45 us/solve in FAST (negligible), the
   simplicity/consistency defect that also feeds ranks 2-4.
EVIDENCE: all per-node wall-clock from the GraphRunner snapshot (warm runs): j001 FAST smoothing 3051 ms vs
recedingHorizon 195 ms; j008b THOROUGH ilsPolish 2274 ms vs bnb 726 ms vs seamSweep 705 ms vs recedingHorizon
9-104 ms vs smoothing 6-29 ms.
IMPACT: speed. The one high-leverage, low-risk change touching ranks 1 and (via smooth-off) the long-run
critical path is B04-2/B04-3: `stepRange`-backed polisher rescoring plus gating SmoothingPolish on
smoothLambda.
CONFIDENCE: 0.88. DEPENDS-ON: B04-2, B04-3, B04-4, B04-5, B04-6, B04-7.

---

Notes for the orchestrator:
- Build state measured: `core/build/classes` compiled 2026-08-24 16:18; the working tree carries other
  agents' uncommitted edits to `AngleSolverEngine.java`/`DeWiggle.java`/`SmoothingPolish.java`/
  `CostateDualSolver.java` that are NOT reflected in those compiled classes. My instrumentation was an
  overlay over the 16:18 build, so all counts are against that coherent snapshot; the recompute STRUCTURE
  is stable across those edits.
- `df-chain-free-start` in `direct` (dualChain) mode returns null because dualChain has no free-start path;
  its representative census is the `engine` FAST row (B04-3) where the free-start is handled by dualChain +
  freeStartImprove + translatedStart nodes.
- All counter code and the probe were reverted; `SolveCounters.java` and `B04CensusProbe.java` are deletion
  candidates if any residue remains.
