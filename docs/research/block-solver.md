# Block solving: research record and post-mortem

Status: **shelved 2026-07-04.** This document is the full record of the attempts to solve jumps from picked blocks (no human-authored constraints), why it was shelved, and what remains viable for a future effort. It supersedes the block-solving material that used to live in `angle-solver.md` (the DERIVE notes and the gh-212 "blocks-only" section); that file is now scoped to the shipped angle solver (human constraints in, yaws out).

The shipped angle solver is not affected by anything here. It takes human-authored per-tick constraints and is documented in `angle-solver.md`. Block solving was an attempt to *remove* the human from that loop.


## 1. The goal and why it is hard

North star (`docs/VISION.md`): two blocks in, full TAS out. Block solving was the first rung: the player tags blocks by role and the solver derives everything else.

- **MOMENTUM**: surfaces available for run-up and takeoff (you jump *from* these).
- **COLLISION**: must be avoided.
- **LAND**: the target.

Plus the hand-set jump tick(s) in the input rows and the recorded per-tick contact schedule (ground/air, slip, feet-Y). A capture counts as SOLVED only if the solve is byte-exact feasible on the derived spec, swept clean against every picked block, and lands the pad. The hard rule: the solver never sees recorded yaws, recorded positions, or the capture's human `angleSolver.ticks` constraints. Recorded data feeds only the spec inputs and developer diagnosis.

Corpus: 53 hpk captures (58 minus 5 gap captures with no blocks or no LAND target: j344, j1150, j718, j343, j315).

**Why it is hard, stated up front (this is the post-mortem's conclusion):** the blocks do not uniquely determine the jump. A real jump is close to unique on its binding ticks (there is essentially one point that must be threaded, plus supporting constraints), but the blocks-only derivation is a *lossy* description of that jump. It leaves a feasible region far wider than the real corridor, and the search that has to pick a point out of that region does not converge on the multi-jump cases. Both halves of that sentence are the two walls this effort hit.


## 2. Precursor: DERIVE (`BlockSolver`)

Before the blocks-only phase, `BlockSolver` (core, `anglesolver/solver/BlockSolver.java`) was the block-to-constraint planner: solve collision-free, sweep the result against obstacle AABBs, derive linear keep-out walls for the violated faces, re-solve, up to a bounded number of iterations. It drove CMA-ES multistart as its inner solver and had an in-world picker UI that was removed 2026-06-10 (internals and tests kept). This is the machinery the blocks-only phase reused and extended; its keep-out-wall idea and swept-collision derivation are the reusable core (see section 8).

The engine entry `AngleSolverEngine.solveFromBlocks()` is the CMA-ES-driven block path that predates the gh-204 linear stack. It was never rewired to the linear pipeline below.

Note 2026-08-21: `BlockSolver` and `AngleSolverEngine.solveFromBlocks` were later deleted entirely in the 2026-08 simplification train (PRs 371-382). gh-307 tracks re-deriving block constraints from world geometry; this document is the reference record for that work.


## 3. What was built (the blocks-only pipeline, gh-212)

`core/.../anglesolver/BlocksOnlySolver.java` derived the spec and ran the gh-204 linear stack (closed form, duals, SLP relaxation, first-feasible pattern B&B), never CMA-ES.

**Task zero (plumbing, still valid, see section 7).** `BlockSelection.Kind` became `{MOMENTUM, COLLISION, LAND}` (legacy `START` loads as MOMENTUM via the alias in `SaveIO.toBlockSelection`). `AngleSolverState` gained three block lists (multiple LAND blocks are real: j153, j424 carry 2; j757 carries 3). `SaveFile.BlockSel` round-trips the role, integer coords, hull box, and collision sub-boxes. `BlockCaptureSaveRoundTripTest` guards it.

**Spec derivation.**
- Land footprint: union of LAND boxes expanded by the exact half-width `(double)(0.6f/2f)` at the landing tick.
- Momentum support: for each grounded jump tick `k`, the union of momentum blocks whose top equals `feetY[k]`, expanded, binding `pos[k-1]` (see the edge-jump mechanic below). No height match meant no constraint. **This union-of-blocks step is the central bug; see section 5.**
- Collisions: clean side branching. The obstacles that actually clip are found by a default-sides run, then every side assignment over them (up to 4 obstacles, 16 combos, popcount order, up to 3 discovery waves) runs as an independent branch with fresh hit-driven keep-out walls. A block has two sides to pass on, so the space stays small.
- Escalation on branch failure: `BlockSolver.runPlanner` via a pluggable `solveWith` (closed-form inner, then the dual chain as inner solver; recorded feet-Y override; Y-axis hits tolerated).
- Pass-tick pin enumeration: after the side waves, the two best branch assignments are retried with an exit pin on the primary crossing gate ("be past the obstacle's far band edge by tick k*"), candidates ordered by distance from the crossing tick, capped at 12.

**Sweep semantics.** `SweptCollision` runs on the solver's X/Z with the recorded feet-Y (the model's posY is intentionally non-physical between jumps). Y-axis hits are legal (bonks and landings are part of the schedule); only X/Z clamps count as clips. All picked blocks are sweep obstacles.

**Gate.** `problems/blockonly/` wired into `ProblemsTest`, 53 sidecars, `shouldSolve` per capture, `maxSolveMs` as a solver budget for known misses. Screens: `BlockOnlyScreen` (per-capture census with the `rec:` classifier), `BlockCensusScreen` (load/round-trip proof). Harness: `BlocksOnlyFixture` (strips tick constraints, keeps the slip schedule; `debugReplayDelta()` re-proves the plumbing per capture, 0.0 everywhere except the known wall-press captures).


## 4. Two genuine discoveries (worth keeping)

**The edge-jump mechanic.** The first support model bound `pos[k]` (the jump tick itself) and stalled with captures reporting an infeasible base spec. A census over all 110 grounded jump ticks falsified it: at the jump tick the recorded player is up to a full run-speed step *past* the block footprint, and with the constraint moved one tick earlier every height-matched departure is covered (62/62 at `pos[k-1]`, 0 at `pos[k]`). Mechanism: MC 1.8.9 clamps Y first using the start-of-tick X/Z, so the tick that runs off the edge still clamps Y (onGround stays true) and the jump fires one position later. This is why the old `solveFromBlocks` constrained `jumpAbs - 1`; that indexing was correct.

**Collision-axis ordering (1.8.9).** MC clamps Y, then X, then Z. An X-clamp tests the start-of-tick Z, so passing an obstacle in X needs the perpendicular clearance a tick *before* the crossing (wall binds `pos[t]`); a Z-clamp tests the already-moved X, so passing in Z can clear the perpendicular in the same tick (wall binds `pos[t+1]`). Z-passes need 1 wall, X-passes 2; pass ticks cluster 1-2 after takeoff and 6-8 before landing on typical neos. 1.21.10 orders differently, so `SweptCollision` (a 1.8.9 port) does not transfer untouched.


## 5. The central bug: false-positive solves

The gh-212 record claimed **29/53** blind (the walk: naive walls 4, gates 9, pos[k-1] support 19, planner escalation 27, side branching 28, pin enumeration 29). **That number is inflated.**

The momentum support constraint is `expand(unionOf(supportAt(feetY[k])))`: the **bounding box of the union of all same-height momentum blocks**. When the momentum blocks are spread out (a staircase or a flat diagonal neo), that bounding box spans the *gaps between blocks*. The solver parks a departure `pos[k-1]` in mid-air over a gap and still reports violation 0, sweep clean, landed. The route jumps from empty air and the gate calls it solved.

Proven on **j345** (True Nix Neo, 4 coplanar y=74 momentum blocks, 3 jumps, all three momentum constraints equal to the same union box X[-659.3, -649.7] Z[1085.7, 1093.3]): the solved `pos[11] = (-650.66, 1087.6)` satisfies the box but sits on no real block. Found by exporting the solve to a loadable save and looking at the route against the blocks in-world.

This is the nonconvex-union case the gh-212 record itself flagged as deferred (its design note 3c, "true disjunction via branching"). The census that had "validated" the union only checked that *recorded* departures fall *inside* the box; it never checked that the solver cannot exploit the box's gaps.


## 6. The measurements that settled it (2026-07-04)

Five PKC_SCREENS probes (test-only). Numbers at a uniform 5 s budget, so absolute counts run below the tuned gate; the comparisons are apples-to-apples within each probe.

- **`AuditSupportScreen`** (strict per-block on-block test at `pos[k-1]`): of the 16 momentum-departure solves, **5 are false positives** (j345, j422, j108, j121, j133). The recorded human route stands on a real block in **44/44** momentum captures, so tightening support cannot break the human witness.
- **`RouteDeviationScreen`** (solver route vs recorded route): only **1 of 21** solved routes reproduces the human line pre-landing; the rest diverge mid-flight by up to **3.5 blocks**. The big divergers are exactly the false positives: the divergence *is* the gap-exploit. Root cause of the looseness is threefold: the union bbox, footprint slack, and a reach-maximizing objective that pulls the solver to an extreme the human never visits.
- **`SideOracleScreen`** (force the recorded pass-side per obstacle): net **negative** (+2 flips, -3 regressions) at equal budget, and both flips were captures the tuned gate already solves. On near-unique jumps the side is not a free variable, so annotating it adds nothing; the -3 were `oracleHi` mislabeling the single determined side.
- **`WaypointOracleScreen`** (pin the recorded binding point per jump). This was the go/no-go:
  - blind honest (on-block): **16/53**
  - block hint ("here is the block each jump leaves from", the best case any disjunction or waypoint approach can reach): **17/53**
  - exact-point pin: broke (every capture with a momentum departure fails the +-1e-6 equality; a numerical artifact, not a ceiling).
- **`DiagnoseJ345Screen`, `SolveExportScreen`**: the tools that made the above concrete (constraint dump; export a solve as a loadable save, row yaws written locked-absolute at `startTick+i`, alignment residual 0).

**The verdict.** The block hint moved the honest count 16 to 17. The four false positives forced onto their *correct* blocks still did not solve, even though each provably contains the recorded route as a feasible witness. So the wall was never the constraint model (union bbox, side, block choice); it is **yaw-search convergence on the multi-jump nonconvex problem**. No taggable hint fixes that, because information is not what is missing. A fully implemented disjunction fix would land at ~17/53; it would only make the count honest, not higher.

Against the shelving bar (would consider shipping near 50/53), 16-17 with the search as the wall is a clear no.


## 7. What remains viable for the future

**Keep and reuse:**

1. **The capture plumbing** (`BlockSelection` model with collision sub-boxes, the three `AngleSolverState` block lists, `SaveFile.BlockSel` + `SaveIO` round-trip). This is durable, correct input for the eventual whole-jump solver and is independent of any solving. Shipped state 2026-08: the picker is product code on all loaders (the `BlockPicker` port with `FabricBlockPicker` on both Fabric loaders plus `Forge8BlockPicker` / `Forge12BlockPicker`) behind `Settings.experimentalBlockCapture`, and `SaveFile.BlockSel` carries `box` plus the `boxes` collision sub-boxes.
2. **The derivation machinery** as scaffolding: land/momentum footprints, keep-out-wall derivation from swept collisions, the edge-jump `pos[k-1]` indexing, the 1.8.9 collision-axis ordering rule. A whole-jump solver will still need to turn blocks into constraints.

**Ideas that were not falsified, for a future attempt:**

3. **The whole-jump solver is the real path, not blocks-only.** The lesson is that the difficulty is the *search*, not the spec. Choosing when to land or bonk (the contact schedule) plus threading a near-unique multi-jump route is a global search problem. The receding-horizon and global-seed work in `angle-solver.md` (sections 10-11, the gh-204/gh-213 line) is the machinery that would have to mature first. Blocks-only assumed a fixed recorded schedule and still could not converge; a solver that also picks the schedule is strictly harder and needs that global search to work first.
4. **Disjunctive support** (design note 3c), *if* block solving is ever revived: momentum support must be "on one of the blocks", an OR of convex footprints, resolved by branching (one block per departure per branch), not a bounding box. Measured ceiling with an oracle block assignment: ~17/53, so this is a *correctness* fix (kills false positives), not a capability fix. Do not build it expecting the count to rise.
5. **A centering objective instead of reach.** The reach objective pulls solutions to the boundary of the loose region, away from the human's interior line. A margin-max or minimal-turn objective (cf. the gh-213 regularized relaxation) might land nearer the true route. Untested for blocks-only; plausible but unproven.
6. **Minimal human hints, if a feature is ever wanted.** A per-jump waypoint is a taggable annotation, but measured it barely helps (17/53) because the bottleneck is search, not information. Do not invest here without first fixing search convergence.

**Falsified, do not retry:**

- Side annotations as a solver hint (net-negative; side is determined on near-unique jumps).
- The union bbox as a support model (admits gap departures).
- Treating "byte-exact feasible + sweep clean + landed" as "solved" without a strict on-block support check (that is what let the false positives through). Any revival must gate on-block support.
- Exact-point equality pins in the linear stack (numerically over-constrained; use a small box if ever needed).


## 7b. Capture-side design decisions (carried from block-capture-handoff.md, 2026-06-28; consolidated here 2026-08-21)

The capture handoff doc was folded in here during the research-folder cleanup. Its settled decisions, still binding for the shipped picker and any future consumer:

- **Capture is separable from derivation.** The picker was shelved because the block-to-constraint derivation was not trustworthy enough to ship, not because the picker was hard. A capture-only picker stores raw AABBs, valid regardless of any solver; the derivation gets built and tested against that captured data later. This breaks the chicken-and-egg between block data and the solver.
- **Blocks are the durable source of truth; constraints are derived and disposable.** Capturing constraints bakes in one derivation; raw in-world AABBs are interpretation-agnostic and survive improvements to the derivation logic.
- **Roles are hints on interpretation-agnostic geometry.** Do not over-categorize: no momentum sub-taxonomy (stepped, partial-cover, backwalled) until a consumer exists.
- **All roles are lists, all optional, all inert to the current solver.** MOMENTUM, COLLISION, LAND; CEILING (headhitter) is deferred. User ruling 2026-06-28: START blocks ARE momentum blocks, so the START role was dropped and legacy `START` saves load as MOMENTUM.
- **Fixture-library decisions from the same thread:** fixtures span from the momentum (long span), not from the takeoff; only capture jumps where a human strat exists (reachability guarantee); bracket each fixture with the best-known landing as a witness in `angleSolver.result` and classify live at test time, never freezing a "missed" flag, so a future fix migrates a fixture from missed to passing automatically; a landing of any kind (multi-block, ladder, vine, water, lava) collapses to a single XZ landing constraint at a specific tick, with the witness disambiguating which block and which tick.


## 8. Artifacts (for a future revival from git history)

If block solving is resurrected, these were the pieces. This work was never committed to a shared branch, so it survives only if the pre-shelve working tree was archived to a branch or tag before the cleanup (strongly recommended before deleting anything). Update 2026-08-21: that archive never happened. The blocks-only pipeline (`BlocksOnlySolver`, `problems/blockonly/`) and the probe screens never landed in git and are unrecoverable; `BlockSolver.java` and `SweptCollision.java` did land and were deleted by the 2026-08 cleanup (recoverable from history); `ConstraintDeriver` is still live (its `deriveFootprint` serves the constraint UI). For everything that never landed, this document's measurement record is the sole surviving evidence. The pieces:

- `core/.../anglesolver/BlocksOnlySolver.java` (the pipeline), reused `BlockSolver`, `SweptCollision`, `ConstraintDeriver`.
- `AngleSolverEngine.solveFromBlocks` / `buildBlockResult` (the CMA-ES engine path).
- Gate: `core/src/test/resources/problems/blockonly/` + `ProblemsTest.runBlockOnly`.
- Screens/harness: `BlockOnlyScreen`, `BlockCensusScreen`, `BlockWitnessScreen`, `BlocksOnlyFixture`.
- Post-mortem probes: `AuditSupportScreen`, `RouteDeviationScreen`, `SideOracleScreen`, `WaypointOracleScreen`, `DiagnoseJ345Screen`, `SolveExportScreen`.
- The gate ran on `:core:test`, PKC_SCREENS-gated screens are opt-in.

Traps banked for anyone who revives this: `Fixtures.buildBoxes` is mandatory for capture-driven tests (placeholder boxes silently degrade to always-sprint); env-driven test runs need `--rerun`; `debugReplayDelta()` is the per-capture plumbing guard; the recorded contact schedule (feet-Y, slip) is data, not a solver degree of freedom; a "solve" is not a solve until it passes a strict on-block support check.
