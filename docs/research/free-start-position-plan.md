# Free start position (and entry velocity) as solve variables

Status (2026-08-21): executed. Phases 0-2 shipped, phase 3 partially (StartBox velocity bounds), phase 4 superseded by the dF=0-chain free-start line (PRs 387-390, commit e171cc52). Current mechanics: `dual-newton-iteration-audit.md` sections 5-6 and `free-start-handoff.md` section 0.

Implementation plan for a future session. Status: Phases 0, 1, 2 done. Branch context: this design was worked out on `v1.7.0`.

Free-start trigger (decided with the user, supersedes the block-footprint approach in Sections 3-5): free the start iff the solve window begins at the route's first tick (`startTick == 0`, UI "tick 1") AND that tick carries an X and/or Z RANGE constraint (`Constraint.Op.IN`). The range IS the footprint box; an axis without a first-tick range stays pinned to the seed; one-sided/scalar first-tick constraints do NOT free an axis. No block capture, no `deriveFootprint`, no save-schema change: entirely constraint-driven. First-tick range constraints are pulled out of the wall list (they were the "trivial tick-0 constant") and become the box.

Progress:
- Phase 0 (done): `StartBox` value type (`solver/StartBox.java`), `JumpPhysicsInputs.startBox`, `buildPhys` emits a pinned zero-width box, `JumpLinearModel.constPos` sources the pinned start from the box. Full `:core:test` byte-identical to baseline (verified via `HpkDualRecoveryScreen` column diff).
- Phase 1 solver core (done): `solver/FreeStartSolve.java` implements realization (B): start from the seed; iterate solve-yaws-at-start, and when infeasible translate the start toward feasibility using the exact per-axis constraint slacks (single-tick walls shift with the translation; two-tick MINUS and facing walls are translation-invariant), objective axis pushed to its footprint/wall bound. Proven by `FreeStartSolveTest` (binding-constraint pin + footprint-corner pin), byte-exact certified.
- Phase 1 engine wiring (done): `AngleSolverEngine.buildJob` builds the free `StartBox` from first-tick ranges and excludes them from the walls; `runJob` uses keep-better (solve the seed as today, adopt `FreeStartSolve` only if byte-exact feasible AND strictly better, or the seed was infeasible), so it can never regress a solvable seed. The pinned start rides in `Plan`; `apply()` moves the start box (gated on `startTick==0` and an actual change) via a new `onStartMoved` callback wired in `Application` to `runner.setStartPosition`. Covered by `EngineFreeStartTest`; anchored fixtures byte-identical (only the diagnostic wall-count `m` shifts as footprints move into the box). The apply/start-move (sim retrigger) is implemented but needs in-game verification.
- Phase 2 realization A (done): the free start is folded into `CostateDualSolver` as bounded deviation variables `delta = p0 - px` (px = seed reference, already in `bPrime`). The dual gains `sum_axis supportBox(H_a)`, `H_a = objDev_a + sum_j lambda_j*p0coef_j`, `supportBox(H) = H>=0 ? H*deltaHi : H*deltaLo`; `Wall.p0coef = (cmp==GE ? +tc : -tc)`, `tc = t2==null ? 1 : op==PLUS ? 2 : 0`. Piecewise-linear in lambda, so the Hessian is unchanged; the term and its gradient are identically 0 for a point box (`deltaLo=deltaHi=0`), so all `freeP0==null` callers (the whole anchored gate, which reaches the dual only via `dualChain`) stay byte-identical. `FreeStartSolve.solveJoint` runs the p0-aware dual over the margin ladder, recovers the jointly-optimal yaws, then recovers the objective-optimal `p0` via `pinTranslate` and verifies byte-exact. The engine's keep-better prefers `solveJoint` (A), falling back to `solve` (B). `FreeStartSolveTest.jointSolveImprovesObjectiveOverSeed` shows the joint start beats the fixed seed by the footprint width. So free-start now optimizes the start even when the seed is already feasible.
- Known limitations for Phase 3+: `solveJoint` does not fold the low-velocity inertia clamp (`velocityWalls`) or F-mode facing walls; those fall back to realization B / the seed via keep-better (no regression). Entry velocity `v0` as a free variable is Phase 3. No capture-JSON free-start fixture yet (hand-built solver + engine tests cover it). The apply/start-move (sim retrigger) still needs in-game verification.

This document is written to be executed by a fresh session with no prior context beyond `AGENTS.md`, `docs/research/angle-solver.md`, and the code. Read those first. The hard requirement running through the whole plan: **every change is gated so that existing solves stay byte-identical, and the full hpk regression gate must stay green at every step** (Section 8).

## 1. What and why

Today the angle solver fixes the start position and the entry velocity, and searches only per-tick yaw. To find whether a jump lands, a user manually places the start (for example at jump tick minus one), solves, looks at what lands, nudges the start, and re-solves. That manual loop is grid search over the start position by hand.

The goal: let the solver treat the start position (and, where meaningful, the entry velocity) as a bounded decision variable, so the binding constraint drops out the best start position analytically instead of by manual nudging. "Solve relative, pin against the binding constraint, read off the start position."

This is the velocity-finder / run-up half of the `docs/VISION.md` two-block workflow. It does not replace any existing solver; it adds freedom where the start is a genuine choice.

## 2. The property that makes it exact

`JumpLinearModel` (`core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/JumpLinearModel.java:14`) writes horizontal position as:

```
pos_k = p0_k + Σ_{s<k} C(s,k) · u_s
```

where `p0_k = startPos + initialVelocity · sPre[k]` (line 16, and `posAt` at `:175-178` returns exactly `p0 + v0 * sPre[end]`). The per-tick input vectors `u_s` carry the fixed-modulus nonconvexity (the yaw circles); they are the trajectory shape.

Two facts follow, and the whole design rests on them:

1. **Start position enters as a pure additive constant with coefficient 1 at every tick.** Horizontal MC movement reads no absolute X/Z, so shifting `startPos` by δ shifts every `pos_k` by δ, shape unchanged. Translation invariance.
2. **Entry velocity enters linearly too, but with weight `sPre[k]` that grows with the horizon.** So `startPos` is a rigid translation of the whole path; `initialVelocity` is a shear that tilts the later part more than the start. They are not interchangeable, which is why moving the start and changing the momentum produce different landing patterns. Both can be lifted; neither substitutes for the other.

Because both enter every position constraint linearly, adding them as box-bounded variables keeps the problem convex. The only nonconvexity stays the per-tick modulus, exactly as today.

Where the constant is currently baked in: `JumpLinearModel.compileWall` (`:200-241`) folds `constVal` (which includes `p0` via `posAt`) into each wall's right-hand side `bPrime` (`:221` for `>=`, `:223` for `<=`). `objectiveVectors` (used at `ClosedFormSolve.java:151`) does the same for the objective. Lifting a variable out means moving its contribution from the constant RHS into a new column of the decision vector.

## 3. Free start vs anchored start (the correctness gate)

A start position is only a free variable when it is a genuine choice:

- **Free start**: the player stands on a block at the first solved tick and chooses where. The start position ranges over the block's standable footprint. Box = footprint.
- **Anchored start**: the solve begins inside a path (a mid-route jump, or a receding-horizon window seeded from a committed jump). The start state is *produced by the prior tick*; it is an output, not a choice. It must never move. Box = a single point (the handed-off state), i.e. zero width.

The mechanism unifies both: **the box width is the distinction.** Anchored solves get a zero-width box and reduce byte-for-byte to today's fixed-start solve. Freeing the start is a non-degenerate box, never a separate code path. This is what preserves causality: a seam can never be teleported because its box has zero width by construction.

**Multi-jump.** Only the first jump has a free start. Jumps 2..N start from the previous jump's exact landing (dependent, never free). Do not free those seeds; `LongRunSolver` already chains byte-exact exit state forward as the next window's fixed seed, and that must stay. The correct generalization: shifting jump 1's start rigidly translates the entire downstream route (every jump is translation-invariant, so the whole chain shifts together, each seam still equal to its predecessor's output). So a route has exactly one free translation (the tick-1 start, boxed by the block), pinned by whichever constraint binds anywhere along the route (a wall on jump 2, the final pad, or a mid-route landing footprint, all uniform linear constraints on that one variable).

Validity caveat: a rigid translation is only exact while it does not change the tick structure (landing one tick earlier or later, or on a different block, changes the ground/air masks). Within a one-block footprint the structure is stable, so it holds as a local convex model; the byte-exact `SimulatorEntity` verify re-certifies. If a footprint edge would flip the structure, that edge becomes the binding constraint.

## 4. Current state of the inputs

- `AngleSolverEngine.buildPhys` seeds `phys.startPos = seed.position` (`AngleSolverEngine.java:435`) and `phys.initialVelocity = seed.velocity` (`:437`) from the recorded `TickState`. So **every solve today is anchored.** This is the anchored path; keep it as the zero-width default.
- There is no `startBlock` field. The nearest existing hook is `AngleSolverState`'s inert block lists `momentumBlocks` / `collisionBlocks` / `landBlocks` (`AngleSolverState.java:173-175`), each a `List<BlockSelection>` (`BlockSelection.java`, carries a world-space `AABB box` / `List<AABB> boxes`). A `Kind.MOMENTUM` (or a new `Kind.START`) selection is the natural free-start signal and footprint source.
- `ConstraintDeriver.deriveFootprint(support, clickX, clickZ, obstacles)` (new on this branch) already computes an `[xLo,xHi,zLo,zHi]` standable footprint from a support block, shrinking around obstacles. This is the box for a free start. It is currently inert (see `docs/research/block-solver.md`).

## 5. The lift, concretely

Add up to four bounded scalar variables to the linear model: `p0x, p0z` (start position) and optionally `v0x, v0z` (entry velocity). Each has a box `[lo, hi]` and a coefficient in every position expression:

- `p0x` / `p0z`: coefficient 1 at every tick (`objectiveVectors` and every `compileWall` on that axis gains a `+1` term on the variable's column).
- `v0x` / `v0z`: coefficient `sPre[k]` at tick k.

Box bounds:

- Free start: `p0` box = footprint (`ConstraintDeriver.deriveFootprint`); `v0` box = the run-up-reachable velocity region (initially the `VelocityFinder` sweep bounds; a true reachable set is #178, out of scope here).
- Anchored: `p0` box = `[seed.position, seed.position]` (zero width); `v0` box = `[seed.velocity, seed.velocity]`.

Two realizations, pick per phase (Section 7):

- **(A) Variable in the dual.** `CostateDualSolver` (`CostateDualSolver.java`) currently solves the constant-modulus dual over per-tick inputs coupled to `List<JumpLinearModel.Wall>` (fields `coef[m][n]`, `bBase[m]`, objective `cx/cz`). Extend it to carry the bounded free variables: they enter the Lagrangian linearly, and a box-bounded free variable contributes its support function (a linear term plus the box bound) to the dual. This gives the jointly optimal `(startPos, v0, yaws)` in one solve, still convex, still microseconds. Cleanest end state; touches the dual math.
- **(B) Outer translation solve.** Keep `CostateDualSolver` unchanged. Solve the relative problem, then solve a small 2D (or 4D) LP for `(p0, v0)` over the box against the compiled constraint slacks, and re-solve yaws if the active set changed (iterate to a fixed point). Lower risk, does not touch the dual internals, proves the binding-constraint pin end to end. Good for the first prototype.

Both must funnel the final `(startPos, v0, yaws)` through the byte-exact `ExactJumpModel` and the `SimulatorEntity` verify, exactly as today.

## 6. Which solves this reaches

Because it is the same convex machinery, it flows everywhere, but the *freedom* only materializes where the box is non-degenerate:

- **FAST** (first byte-exact feasible via `dualChain`): folds in at no meaningful latency cost. Semantics change only for free starts, where FAST goes from "first feasible yaws at the pinned start" to "first feasible (start, yaws)" (strictly more capable). Anchored: no-op.
- **Optimize / exhaustive**: same, plus the reach stages already present.
- **Velocity map** (`VelocityFinder`): inherently a free-start tool (entry velocity is the jump-1 run-up question; a seam's entry velocity is determined upstream). Each swept `(vx,vz)` cell co-optimizes the freed start within the footprint and reports the required start position and which constraint binds. Output becomes a Pareto set over (velocity, start position, margin), ranked by margin as `rankedLanders` already does. For the convex region the LP can replace much of the grid; keep the sweep for visualization and the nonconvex cases.

## 7. Implementation phases

Each phase ends green on the full gate (Section 8) before the next begins.

**Phase 0. Anchored no-op scaffolding.** Introduce the bounded-variable plumbing with the box hard-wired to zero width (seed point). Wire `buildPhys` to always produce a zero-width box (read from `seed.position` / `seed.velocity`). Realization (A) or (B). Acceptance: the entire `:core:test` gate is byte-identical to `main` of this branch; the dualrecovery gate stays 58/59 solving; `ModernStepRegressionTest` green (that test was later deleted; the tripwire is now `CaptureReplayRegressionTest`). This phase proves the lift changes nothing when the box is a point.

**Phase 1. Free-start on a single jump, realization (B).** Add the free-start signal (a `Kind.START` or reuse `Kind.MOMENTUM` `BlockSelection`) and derive the footprint box via `ConstraintDeriver.deriveFootprint`. Solve one hand-built free-start fixture where the jump is infeasible at the pinned start but feasible somewhere on the block. Assert the solver returns feasible and the pinned start lies in the footprint. Add it as a new regression fixture (Section 9). Anchored fixtures untouched.

**Phase 2. Fold into the convex fast path, realization (A).** Move the free variables into `CostateDualSolver` so `ClosedFormSolve` returns joint `(startPos, yaws)` optima, gated on the footprint being present. Re-run the whole gate; the invariant is that every fixture without a footprint is byte-identical.

**Phase 3. Entry velocity + velocity map.** Add `v0x, v0z` as bounded variables; wire `VelocityFinder` cells to co-optimize the freed start and report required start position + binding constraint. Add free-start velocity-map fixtures.

**Phase 4 (optional, separate ticket). Multi-jump single-translation.** Let the first jump's free start rigidly translate a `LongRunSolver` route, pinned by the union of per-jump constraints. Keep seam chaining exact. This is larger; treat as a follow-up.

## 8. Regression protocol (mandatory)

The hpk jump dataset is the anti-regression gate. Do not merge any phase that is not green here.

**Command:**

```bash
./gradlew :core:test
```

Pure Java, no Minecraft, runs in seconds. To reproduce CI core counts locally (the B&B pattern pool is core-count sensitive): `./gradlew :core:test -PtestCpus=2` (also verify at 4 and 12). Single suites: `./gradlew :core:test --tests '*ProblemsTest'` and `--tests '*ModernStepRegressionTest'` (that test was since deleted; the byte-exact tripwire is now `CaptureReplayRegressionTest`). `tableStyleCheck` does not run on `:core:test` (it is on `:core:check`/`build`, and has a known false positive on `SolverWidgets`; skip with `-x tableStyleCheck` there).

**What the gate covers (do not weaken any of it):**

- **`ProblemsTest`** parameterizes over `core/src/test/resources/problems/{solve,closedform,dualrecovery}/`. The `dualrecovery/` folder holds 59 sidecars: all 58 hpk captures (`captures/hpk/d9` ×30, `d10` ×20, `d11` ×8) plus `loopmm-3jump-lands`.
- `runDualRecovery` calls `AngleSolverEngine.dualChain(...)` only (closed form → SLP → reseeded SLP → relaxation recovery), **no CMA-ES, no warm start**, and asserts **byte-exact feasibility** (`compiled.maxViolation(gf, path) <= 0.0`). On a chain miss with `bnbSeconds` set it runs the blind pattern `BoundPrunedRecovery`. Only `loopmm` pins an objective (`refObjective: -279.3`, `maxObjectiveGap: 0.0`). Baseline: 56/58 hpk solve through the chain, `j716` is the sole `shouldSolve:false` frontier miss, `loopmm` lands via the B&B.
- **`ModernStepRegressionTest`** pins `ExactJumpModel` / `McSineTable` / `Constants` bit-for-bit against three recorded 1.21.10 transitions (tolerance 0.0). Any drift in the step arithmetic fails immediately. Per `AGENTS.md`: do not edit model code without a green run first. **This plan must not touch the step model**; it only lifts constants into the LP, so this test is your tripwire proving the forward model is unchanged. Annotation (2026-08-21): that test was deleted; the byte-exact tripwire is now `CaptureReplayRegressionTest`.

**The core invariant, restated:** the feature is gated on a free-start footprint. Every one of the 59 existing dualrecovery fixtures (and all solve/closedform fixtures) has no footprint, so its box is zero-width and its result must be byte-identical before and after every phase. If any existing fixture changes objective or feasibility, the lift has leaked into the anchored path and is a bug. Add a temporary per-capture before/after objective diff (the gh-213 work used exactly this zero-regression check) while developing Phase 0–2.

**Dev screens for debugging a solve against the captures** (all `assumeTrue`-gated, no-op in a normal run):

- `PKC_SCREENS=1 ./gradlew :core:test --tests '*HpkDualRecoveryScreen'` then read `core/build/reports/hpk-screen.txt` (per-capture stage table). Also `RelaxDiagScreen`, `HpkMissTriageScreen`, `ReachGapScreen`, `LoopmmReachScreen`.
- `PKC_SOLVE_FILE=<save.json> ./gradlew :core:test --tests '*EngineFileScreen'` (optional `PKC_SOLVE_EFFORT`, `PKC_SOLVE_TIMEOUT_MS`) drives the live engine headlessly on any save file.
- `PKC_BENCH=1 ... '*HpkEngineBench'` for timing over the hpk library.
- Solver tracing (main/core source, not the test tree): `PKC_SOLVER_TRACE=<tag>` or `-Dpkc.solver.trace=<tag>` writes `build/reports/solver-trace-<tag>.txt`; instrumented across `ClosedFormSolve`, `SlpSolve`, `RelaxationRecovery`, `BoundPrunedRecovery`, `SeamSweepRecovery`, `AngleSolverEngine`. Guarded by `SolverTrace.on()`, zero cost when off. Add trace points for the new variables.

## 9. New regression fixtures to add

The existing gate proves no anchored regression but covers zero free-start behavior. Add free-start fixtures so the new capability is itself pinned. Adding a fixture is data-only, no Java change (`ProblemCatalog` auto-discovers):

1. Capture JSON into the shared library, e.g. `core/src/test/resources/captures/freestart/<name>.json`, with an `angleSolver` block (`seed`, non-empty `rows`, recorded `result`) plus the start-block footprint metadata the free-start path reads.
2. Sidecar in a check folder, e.g. `core/src/test/resources/problems/dualrecovery/<name>.expect.json`:
   ```json
   { "capture": "freestart/<name>", "shouldSolve": true }
   ```
   Add `"refObjective"` (+ optional `"maxObjectiveGap"`, matching the capture's `angleSolver.goal` sense) to pin the reach; set `"shouldSolve": false` for a known-unlandable-anywhere-on-the-block case.

Fixtures to author (at least):

- A jump infeasible at the recorded start but feasible elsewhere on the block (proves the pin finds it).
- A jump feasible only at a footprint corner (proves the block edge can be the binding constraint).
- An anchored control that is a copy of an existing hpk capture with an explicit zero-width box (proves anchored is unchanged with the plumbing active).
- A multi-jump route where translating jump 1 lands a downstream pad (Phase 4).

If a fixture references block geometry that `Fixtures.buildBoxes` does not yet reconstruct, extend the harness the same way gh-204 did (`Fixtures.buildBoxes` builds engine boxes from the capture's `debug` blocks); do not hand-fake footprints.

## 10. File-by-file change map

- `solver/JumpLinearModel.java`: lift `startPos` (and optionally `initialVelocity`) out of the folded `p0_k` constant into named decision columns; `compileWall` (`:200`) and `objectiveVectors` add the `+1` / `+sPre[k]` terms; add box metadata to `Wall` construction or a parallel free-variable list.
- `solver/CostateDualSolver.java`: (realization A) carry the bounded free variables in the dual; or leave unchanged (realization B).
- `solver/ClosedFormSolve.java`: pass the box through (`:151-160`); return the pinned `(startPos, v0)` alongside yaws.
- `anglesolver/AngleSolverEngine.java`: `buildPhys` (`:435-437`) produces a zero-width box by default; when a free-start footprint is present, produce the footprint / velocity box; thread the pinned start back into the result and the byte-exact verify.
- `anglesolver/AngleSolverState.java` + `BlockSelection.java`: add the free-start signal (new `Kind.START` or reuse `Kind.MOMENTUM`); expose the footprint.
- `anglesolver/ConstraintDeriver.java`: use `deriveFootprint` as the box source.
- `velocity/VelocityFinder.java`: per-cell co-optimize the freed start; extend the result to carry required start position + binding constraint (Phase 3).
- `solver/SolverTrace.java`: trace the new variables.
- UI (`ui/anglesolver/`): surface "start freely on this block" and show the pinned start position; out of scope for the first phases but note it.

## 11. Risks and open questions to resolve first

- **Dual extension math (realization A).** Confirm the box-bounded free variable's dual contribution before committing Phase 2; prototype with realization B first so there is a working oracle to diff against.
- **Active-set stability under translation.** The rigid-translation validity depends on the tick/landing structure not changing across the footprint. Add an assertion that the recovered tick structure matches at the pinned start; fall back to per-region solves if a footprint spans a structure change.
- **Momentum-clamp threshold.** Near the small-velocity cutoff, `v0` stops being linear (it flips which ticks zero). That is the regime `BoundPrunedRecovery` pattern-branching already handles; compose with it rather than re-deriving. Keep the linear lift to the generic regime and let the pattern branches cover the clamp.
- **Collision translation.** Walls are linear constraints in the solve, so translation is handled there; only the swept-collision `SimulatorEntity` verify is start-specific and it already runs. Keep it.
- **Anchored leak.** The single largest risk is the lift silently altering anchored solves. The zero-width-box invariant plus the per-capture before/after diff in Section 8 is the guard; keep it wired until Phase 3.

## 12. Acceptance summary

- Phases 0–2: full `:core:test` green at 2, 4, 12 cores; every pre-existing fixture byte-identical; new free-start fixtures pass.
- `ModernStepRegressionTest` never touched. (That test was since deleted; the byte-exact tripwire is now `CaptureReplayRegressionTest`.)
- No CMA-ES / warm start introduced into `dualChain`; determinism preserved. (Moot: CMA-ES was later removed entirely, PRs 371/373/375.)
- The manual "place start, solve, nudge, re-solve" loop is replaced by one solve that pins the start position against the binding constraint.
