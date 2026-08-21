# Dual Newton / Hessian handoff (2026-08-21)

Written at dev 64dada4 (the #382 squash merge). Read this before touching `CostateDualSolver` or planning the next solver perf pass. Companion issues: #384 (the Hessian work), #383 (loopmm redirect win-back). Session record: issue #380, PR #382.

**Update (issue #384 executed): section 2's two lanes were measured and are both dead. Read `dual-newton-iteration-audit.md` before re-attempting anything here; it carries the per-callsite audit, the contribution audit, and the corpus breakages.**

## 0. State at handoff

- Sweep baseline: `FreeStartSweepBench` 104/104 at **25.1 s** (down from 57.1 s at 8484ea5), over-2s runs: j346 base ~2.2-3.0 s, j347 base ~3.0 s, j347 shift ~2.4 s. `:core:check -PslowTests` green at ~2m40s.
- Update (2026-08-21): the sweep baseline moved from 25.1 s to ~30 s after the #387-390 sharpening ladder shipped; accepted cost, see `dual-newton-iteration-audit.md` section 5.
- `SlpSolve` now runs on the bespoke `TrustRegionLp` (bounded simplex, split step variables). commons-math3 is a test-only dependency of core (`TrustRegionLpTest` is the 300-instance randomized value-equivalence oracle against the old formulation). The loaders still bundle commons-math (Fabric `include`, Forge shade relocations); dropping that is packaging work with in-game QA.
- Accepted frontier per ruling: `solve/loopmm-tight-t39` and `-fast` are `shouldSolve: false`; `dualrecovery/loopmm-3jump-lands` and `solve/j022-1bmhbfly-noland` carry `maxObjectiveGap: 5.0e-4`. Win-back criteria live in #383.

## 1. What the deep dive found

JFR over the 25.1 s sweep (all threads, 2,181 exec samples):

| where | share |
| --- | --- |
| `CostateDualSolver.buildHessian` | 42.4% leaf |
| `CostateDualSolver.choleskySolve` | 19.6% leaf |
| `TrustRegionLp` (whole LP) | 19.3% inclusive |
| `ExactJumpModel.stepRange` | 5.7% leaf |
| `CostateDualSolver` total | 68.3% inclusive |
| `LongRunSolver` total | 87.9% inclusive |

The Hessian math is analytical and per-build already optimal-ish: entries are closed form, the per-tick sqrt/divides were hoisted bit-identically in #378, and what remains is the irreducible O(activeWalls^2 x ticks) multiply-add volume of a dense walls-by-walls matrix (~1M flops per build at 100 walls x 40 ticks), plus O(walls^3/6) Cholesky per Levenberg damping attempt.

**The waste is iteration count, not iteration cost.** From the farseed trace (`-Dpkc.solver.trace`, CF rung lines carry `iters=`):

- 6,790 dual solves, 559k Newton iterations total.
- **5,195 of 6,790 (77%) burn the full `MAX_ITER = 100`** without converging: final projected-gradient residuals median 8.5e-2, p10 5.1e-3, p90 0.85, against `GRAD_TOL = 1e-8`. Median = p90 = max = 100 iterations.
- The existing divergence bail (`DIVERGE_PGRES = 4.0`, `DIVERGE_STALL = 12`) only fires while the best residual exceeds 4.0. The capped population plateaus in 5e-3..0.9, under the gate, so nothing stops it. This behavior predates the LP swap; it just owns the profile now that everything else is fast.
- Attribution of the volume: the free-start chain-scan structure. Each anchor theta runs a jointLadder = 1 probe solve + up to `jointBisectIters = 8` bisection solves + ~11 margin rungs, each solve up to 100 Newton iterations, and farseed scans ~140 thetas across ~6 pattern rounds. Most anchors are infeasible or degenerate, which is exactly where the damped Newton plateaus.

## 2. How to approach the speedup (issue #384)

Two lanes, both gated on the corpus. Lane B is likely the bigger win and matches the "filter out 99% of attempts beforehand" framing.

**Lane A, in-solver stall bail.** In `CostateDualSolver.solve` (the loop at ~line 201), add a relative-stall exit: when the best residual has not improved by `DIVERGE_REL` for a stall window AND is still far above tolerance (gate on something like `pgBest > 1e4 * GRAD_TOL` so slow-but-real convergers finish), break and return the current iterate as a **normal Result**. Constraints:

- Do NOT set `lastStalled`. jointLadder (`FreeStartSolve` ~lines 490-530) treats that flag as skip-the-branch; a bailed solve must look like a capped solve, just earlier. The iterates are monotone in the dual value, so the returned point is the same kind of best-effort point the cap returns.
- Capped solves' outputs are NOT pure waste: their recovered yaws feed rung candidates and the rung audit (issue #380) showed scattered rung wins. Expect small trajectory shifts; feasible-path changes are acceptable per standing ruling, lost solves are not.
- Expected effect: the 77% population exits at ~15-25 iterations instead of 100, cutting the ~62% CostateDualSolver CPU share by maybe 3-4x. Sweep estimate 25 s -> ~14-17 s.

**Lane B, pre-filter the solve volume.** Do not run full Newton on hopeless anchors at all:

- Weak duality gives a bound from ANY lambda: one or two Newton iterations already yield a dual value that upper-bounds the primal. A 2-3 iteration screening pass per theta could prune arcs whose bound is already hopeless before the ladder runs its ~12 full solves.
- Coarse-to-fine theta scanning: scan the prefix arcs at 3-4x spacing, run full ladders only near the best coarse anchors. Today all ~140 thetas get full treatment in EVERY pattern round; `prefixArcThetas` is computed once but re-laddered per round with a new pattern lin.
- Reuse across pattern rounds: rounds differ only in the zeroing pattern; theta viability is strongly correlated across rounds. Carrying per-theta best-viol from the previous round and skipping thetas that were hopeless by a margin is a cheap, general filter.

Measure first, kill-audit style: temporary counters (the RungAudit pattern from #380, uncommitted) for solves/iterations/capped per call site, then prototype behind a temporary system property, then the full gates: sweep 104/104 with no capture meaningfully slower, `:core:check -PslowTests`, farseed and j990 FAST budgets.

## 3. Traps learned this session (do not relearn these)

- **LP value-equality does not imply trajectory-equality.** TrustRegionLp matches the old commons-math optimal VALUES on 300 randomized instances, yet the loopmm redirect class stopped landing: its pad (~1e-5 headroom under the dual bound) rode one lucky hug trajectory (1 hit in ~320 SLP calls; 0 in ~6,700 on the new LP, hugs top out 2-5e-4 short class-wide). Two rescue attempts failed and were reverted: a minimal-norm lexicographic face pass in the LP, and lifting the B&B's best search-spec-feasible near-miss with `LevelSetAscent` (the bisection cannot close the last ~2e-4 either). Do not re-attempt those two; #383 needs a genuinely different mechanism.
- **Split variables, not shifted ones.** The first reformulation (e = d + tr on commons-math) broke dualrecovery/j346 because phase-1 degeneracy parked uninvolved ticks at d = -tr. The split (d = p - q, both in [0, tr]) keeps uninvolved ticks at zero; that alone fixed j346. Phase-1 of this LP is massively degenerate; vertex selection is a real degree of freedom.
- **The B&B floor conversion**: `BoundPrunedRecovery` converts a target wall into `searchSpec` (wall removed) + `floorNorm`; `offer()` checks the FULL spec, so near-misses are invisible today. If #383 wants them, that is where they surface.
- Sidecar re-pin precedent: accepted-fail = `shouldSolve: false` (nix-full-t1 pattern); feasible-but-short = widen `maxObjectiveGap`.
- `GraphPresetIO` now ignores unknown params on load (retired-key tolerance, Gson precedent). `applyParam` returning failure is gone; `GraphPresetIOTest.unknownParamIsIgnoredOnLoad` pins it.
- Bench method: direct `java -cp` (gradle daemon swallows PKC_*/-D), classpath via a printTestCp init script, `jfr print` is German-locale (comma decimals) and truncates stacks at 5 without `--stack-depth`, sweep filter is exact-stem comma list, solver threads are not "main".

## 4. The five biggest remaining levers, ranked

1. **Dual Newton iteration waste (#384)**: lanes A+B above. ~62% of solver CPU; the largest single win available, moderate risk (trajectory shifts in the capped population). DONE, negative: see `dual-newton-iteration-audit.md`; capped iterations are load-bearing.
2. **Free-start scan-volume restructure**: the 140-theta x ~6-round x ~12-solve multiplication is the machine that FEEDS lever 1; coarse-to-fine plus cross-round reuse attacks it structurally. Overlaps lane B; worth designing together. Farseed spends ~3.6 s here even before the Newton waste inside it. Declined by the audit's section 4: the theta machine never runs on the sweep and the j990 pin protects its exact enumeration.
3. **loopmm redirect win-back (#383)**: capability, not speed. The pad needs a deliberate crossing mechanism (the corridor between dual bound and pad is ~1e-5 deep; float-lattice realization inside it is the hard part). Acceptance criteria are in the issue.
4. **FAST-path last-window polish cost**: pre-LP measurements showed `LevelSetAscent` + hug on last windows at ~6.2 s of the then-57 s sweep (20 calls, all wins) and window CF/SLP alternates at ~10 s combined; all contribute results, so this is a budget/semantics question (how much objective polish does first-feasible FAST owe?), needs a ruling plus re-measurement post-LP before touching.
5. **Packaging simplification**: drop the commons-math bundles from the loaders (Fabric `include`, both Forge relocations plus the jar-relocator machinery that exists only for it) now that core no longer needs it at runtime. Mechanical, but it is loader packaging: needs the usual in-game QA pass on all three loaders.

## 5. Re-running the numbers

```
./gradlew :core:printTestCp --init-script printcp.gradle -q > cp.txt   (task from scratchpad init script; see TESTS.md)
java -cp $(cat cp.txt) -Dpkc.sweep=1 -Dpkc.sweep.tag=<tag> org.junit.runner.JUnitCore de.legoshi.parkourcalc.anglesolver.FreeStartSweepBench
java -cp $(cat cp.txt) -Dpkc.solver.trace=<tag> ... (CF rung lines carry iters= and pg=)
java -XX:StartFlightRecording=filename=out.jfr,settings=profile -cp ... (aggregate jdk.ExecutionSample leaves, --stack-depth 128)
```

Gates for any change here: sweep 104/104 with no capture meaningfully slower, `:core:check -PslowTests`, farseed/j990 FAST 20 s budgets, and the #383 pins staying at their re-pinned expectations.
