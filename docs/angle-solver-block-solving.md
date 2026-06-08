# Angle Solver: block-based solving — problem statement & handoff

Status (2026-06-07): **SOLVED (headless).** Both the physics AND the constraint-derivation are now solved and validated headlessly. `solver/BlockSolver` derives the per-tick constraints from the picked blocks, the SOLVE finds a swept-clean landing, and j154 (the canonical hard SE-corner wrap) validates `clean + landed`, matching the hand-solved path to ~1e-4. It also generalizes across every obstacle subset of the j154 arc. NOT yet re-verified in-game by the user. The rest of this doc keeps the original problem statement + everything learned.

## THE SOLUTION (`solver/BlockSolver`, forced-crossing-tick homotopy planner)

Approach A (geometric homotopy planner) with a forced crossing tick. Derives everything from geometry + a bounded search + the swept oracle; no per-jump constants.

1. **Travel axis** = the larger seed->land separation (Z for j154); the perpendicular is the **keep-out axis** (X). Each obstacle is wrapped on the perpendicular side **nearest the launch corridor** (player center vs the obstacle edge +- half).
2. **Forced crossing tick k\***: the killer detail is that MC's swept X-clamp tests the player's *start-of-tick* travel coordinate, so a perpendicular move while the travel extent still overlaps the obstacle clips (this is the exact bug that broke nr3: `HIT X @move6` against the head). Pinning `A[k*] <= band-bottom` (descending) makes the descent rate, hence the keep-out window, deterministic instead of solver-dependent. **k\* is enumerated** over a small window around the no-keep-out estimate.
3. **+1 dilation**: a keep-out is active at each in-band tick AND the tick after, covering the move whose start-of-tick coordinate is still in the band.
4. **Objective sweep**: the pad edge sits at the **reachability frontier**, so the toward-pad objective (X/MIN for j154) lands on it while the away objective (X/MAX) overshoots off the pad by ~1e-6. Try all four endpoint objectives.
5. Each candidate `(objective, k*, timing-iteration)` is verified with `SweptCollision` + landing-footprint; the first `clean && landed` wins. `Result.ok()` is never reported for a clipping/missing path; on a layout it cannot wrap it honestly returns the best non-ok attempt.

Headless harness: `core/src/test/.../anglesolver/derive/` (`DeriveOracle` = ground truth, `DeriveFixtures` loads real `selectedBlocks`). Gates: `BlockSolverTest` (j154 solves + safety + recorded no-FP/no-FN), `DeriveOracleTest` (oracle both directions), `DeriveGeneralizationTest` (production solver over obstacle subsets). Run `./gradlew :core:test`. A structured Workflow also tried 4 alternative approaches (homotopy-class enumeration, reachability-corridor, reactive-forced-exit, fixed-descent-schedule); none beat the forced-crossing-tick planner, which is the kept solution.

## The core insight (the user's framing)

There are two sub-problems:

1. **SOLVE**: given per-tick constraints + an objective, find the per-tick yaws. **This is solved** — CMA-ES over the byte-exact model finds a hard jump in ~100 ms (`SolveCore.optimize`). Reuse it as-is.
2. **DERIVE**: given the block hitboxes (start / collision / land), produce the per-tick constraints that (a) admit a collision-free landing and (b) the SOLVE can find. **This is the unsolved hard problem.**

The information is complete and a solution provably exists (the user has hand-solved j154 in-game). We just can't auto-derive the constraints yet. It is a motion-planning / homotopy problem with MC-specific swept collision and temporal reachability.

## Problem statement (precise)

Given, for a jump segment `[startTick, landingTick]` (j154: 32..43, jumpTick=32):
- **Seed** at `startTick`: position, velocity, yaw (the fixed launch state; the solver does NOT change it).
- **Per-tick inputs**: jump tick, 45-strafe mask, slipperiness, speed/jump-boost, yaw-lock. Fixed.
- **Per-tick feet-Y**: yaw-independent (vertical motion doesn't depend on facing), so known exactly from any prior sim of the same keys. Read from `BoxController.getState(t).position.y`.
- **Blocks** (real MC AABBs, captured at pick time): one START, zero+ COLLISION, one LAND.
- Player hitbox: 0.6 wide (half = 0.3), 1.8 tall (1.5 sneaking).

Decision variables: the per-tick **yaws** for the air ticks.

Find yaws such that the **real MC SimulatorEntity**:
- stands on the START block the tick before the jump (center in `[bx-0.3, bx+1.3]` etc.),
- **never collides** with any COLLISION block at any tick (MC swept collision — see below),
- **lands on** the LAND block (final position in `[bx-0.3,bx+1.3] × [bz-0.3,bz+1.3]`).

The SOLVE accepts per-tick scalar/range constraints on `X`, `Z`, `F` (facing) and a single-axis MAX/MIN objective at one tick. So DERIVE must output exactly that constraint vocabulary.

## MC swept collision (CRITICAL, validated — `solver/SweptCollision`)

MC `Entity.moveEntity` clamps the intended motion **axis by axis, order Y → X → Z**, offsetting the hitbox between each (ported from decompiled 1.8.9 `AxisAlignedBB.calculate{X,Y,Z}Offset`). Consequences:
- Collision happens **between ticks** (swept), NOT at tick endpoints. An endpoint-only check is wrong (this was the bug behind 3 in-game failures).
- **X clamp** tests the player's **start-of-tick Z** ⇒ to cross a block in X you must already be Z-clear the **prior** tick.
- **Z clamp** tests the **already-moved X** ⇒ to cross in Z you can clear X the **same** tick.
- VALIDATED: forwarding the recorded (working) j154 yaws through `ExactJumpModel` is swept-clean (`BlockSolverTest.recordedSolutionIsSweptClean`). The byte-exact model equals the real entity for any strictly-outside path, so `SweptCollision`-clean ⇒ works in-game.

## The canonical hard case (j154)

A 2-tall cube wall at Z4929 between start (Z4930) and land (Z4928), with a skull (head) just east at X[-1600,-1599.5]. The player can't go over (apex < wall top) or straight through, and west is blocked by the cubes, so it must wrap the cluster's **SE corner**:
- stay **NORTH** of the head (`Z ≥ 4930.05`) early,
- drift **EAST** of the head (`X ≥ -1599.2`) by ~T35–T38,
- cross **SOUTH** (`Z ≤ 4928.95`) at ~T39, then land.

These constraints **interlock and require lookahead**: you must go east *before* you can cross south (crossing south early is infeasible — `X ≥ -1599.2` is physically unreachable until ~T35). This is why reactive collision-fixing fails.

## What's built and reusable (DON'T rebuild)

- `solver/SweptCollision` — faithful MC swept collision. **Validated. Keep.**
- `solver/SolveCore.optimize(model, spec, budget, sigma, feasTol, cancel, warmStart?)` — the fast CMA-ES solver. Works. Has warm-start.
- `solver/ExactJumpModel` — byte-exact collision-free forward (posX/Y/Z). == real sim when strictly outside boxes.
- Engine plumbing: `AngleSolverEngine.solveFromBlocks()` (off-thread), writes derived constraints + objective + result; `buildPhys` (scenario snapshot), `collectUiConstraints`. `poll()` writes derived constraints + axis/goal back to the table.
- UI: "Solve from blocks" button next to Solve (`AngleSolverWindow`), grayed until Start+Land picked; block picking is keybind-driven (Forge 1.8.9: J/K/N add Start/Land/Collision, M remove looked-at) via the `BlockPicker` port (`Forge8BlockPicker`, 1.8.9 only); selected blocks drawn in-world (Start green / Land red / Collision purple) by `Forge8WorldOverlayRenderer`; persistence (`SaveFile.BlockSel` / `SaveIO`). The objective is the user's Axis+Goal (no auto-pick); default Effort is FAST.
- Geometry helpers in the engine: `expand()` (block ± 0.3), footprints, per-tick heights.
- `BlockSolver` — **the solution** (forced-crossing-tick homotopy planner; replaced the non-converging cutting-plane DFS). See "THE SOLUTION" at the top.

## Safety guarantee that currently holds

`BlockSolver.Result.ok() = clean(no swept collision) && landed`. The solver verifies every candidate with `SweptCollision`, so it **never reports a colliding solution as success** — it reports "no solution" instead (`BlockSolverTest` asserts ok() ⇒ swept-clean). Simple jumps may solve; j154 does not.

## What failed (DON'T retry)

1. Deriving walls from a "recorded route" — the route doesn't exist until after solving; a bad attempt yields garbage walls (e.g. `Z≥4930.05` on the landing tick → infeasible).
2. Endpoint-only collision check — misses the swept corner-clip (the actual bug).
3. Safety margin on walls (`edge ± 1e-3`) — kills razor-tight jumps (the manual j154 clears by ~6.6e-7).
4. Reactive nearest-exit / per-tick side flipping — thrashes (inconsistent homotopy).
5. Reactive cutting-plane off the swept collision, even with a DFS over pass-sides + a "stay on entry side / delay crossing" option — does NOT converge on the corner-wrap. It lacks lookahead.

## Promising directions for the structured algorithm search (next session)

The user wants this **autonomous** (additional user input like "click the rough path / pick the pass-side" is a fallback, not preferred).

- **A. Geometric homotopy planner (most promising):** from the block geometry alone, decide HOW to wrap the obstacle cluster (e.g. project to XZ within the Y-window; the head is east of the cubes, land is south ⇒ wrap the SE corner), then EMIT the full interlocking constraint set up front (`Z≥north` early, `X≥east` mid, `Z≤south` late) and SOLVE once. Verify swept-clean; if not, try another homotopy. This mirrors how a human hand-authors it.
- **B. Homotopy-class enumeration:** enumerate the topological ways to pass the obstacle cluster (visibility-graph / winding classes in XZ, accounting for the per-tick Y-window so a block only matters while the torso overlaps it). For each class, derive constraints (respecting MC swept order + temporal reachability), solve, verify, backtrack.
- **C. Formulate as CP/MILP** over per-tick keep-out half-spaces with the swept-order coupling and a reachability model, then solve with the fast oracle for the continuous yaws.
- **D. Sampling planner (RRT/PRM) over homotopy** using `SolveCore` + `SweptCollision` as the steering/validity oracle.

Recommended start: **A**, with **B** as the generalization. Define "homotopy" concretely first (per obstacle: which corner/side, derived from start→land direction + obstacle layout + the Y-window), then the temporal-reachability rule (a half-space `X≥v` is only active from the first tick it's reachable given the jump dynamics).

## AUTONOMOUS self-validation (Claude can test + validate without the user / in-game)

The whole point: `SweptCollision` is byte-exact MC collision (validated against a real working jump), and `ExactJumpModel` == the real SimulatorEntity for any strictly-outside path. So a DERIVE algorithm can be **fully self-validated headlessly**:

```
load saved file -> SaveIO.parseSafe -> SaveFile
seed scenario from file.debug (BlockSolverTest.scenario)            // JumpPhysicsInputs
blocks from file.angleSolver.selectedBlocks (SaveIO -> BlockSelection)  // real MC hitboxes
constraints = MY_DERIVE_ALGORITHM(blocks, scenario, feetY[])        // <-- the thing under test
yaws = SolveCore.optimize(model, JumpSpec(scenario, constraints, objective), ...)  // ~100ms
path = model.forward(scenario, scenario.toGameFacings(yaws))
VALID  <=>  (no SweptCollision.firstHit over all moves vs the real block AABBs) AND (path[N] in land footprint)
```

`VALID` is ground truth — no user, no in-game run. Iterate DERIVE algorithms in a loop (or a parallel Workflow) and keep what validates. The pieces (`SweptCollision`, `SolveCore`, `ExactJumpModel`, `SaveIO`) are all pure-Java in `:core`. Run with `./gradlew :core:test` (JDK 21 runs gradle; no MC needed). The loader block-picker can't be headless-tested, but DERIVE+SOLVE+verify fully can.

Two SweptCollision regressions to keep/extend (both validate the model from opposite sides):
- `recordedSolutionIsSweptClean` — forwarding j154's recorded working yaws is swept-CLEAN (no false positives).
- TODO add: forwarding `j154-fails-nr3`'s solved yaws must FLAG a collision ~tick 40 (the model detects the real clip the user saw — no false negatives).

## Test data files

Saved files live in `loader-forge-1.8.9/run/client/parkourcalculator/*.json`. Committed fixtures are copied into `core/src/test/resources/anglesolver/` (currently j154, j1097, j121). To add a DERIVE fixture, copy a file with `selectedBlocks` into that resources dir and load via `SaveIO.parseSafe`.

**Files WITH block selections (DERIVE inputs — load the real picked hitboxes, don't hand-type):** all are j154, seg 32-43, START:1 COLLISION:3 LAND:1, with `debug`:
- `j154-fails-nr3.json` — endpoint result "3/3 success" but the solution SWEPT-COLLIDES at ~tick 40 (the user's nr3 failure). Best DERIVE+validation case: blocks are real, and it proves endpoint-success ≠ collision-free.
- `j154-still-fails.json` — my deterministic v2 output, 14/24, infeasible (`Z≥4930.05` on the landing tick). Shows a bad derive.
- `j154-fail-auto-assign.json` — my v1 (recorded-route) output, 26/29. Over-constrained.
- (NOTE: extract the j154 block geometry from these `selectedBlocks` — it's the real MC hitboxes, more accurate than the hand-typed head box `X[-1600,-1599.5] Y[89.25,89.75] Z[4929.25,4929.75]` used in `BlockSolverTest`.)

**Files with a KNOWN-GOOD solution (SOLVE references / regression; success + debug path, NO blocks yet):**
- `j154.json` (seg 32-43, 7/7) and `j154-less.json` (7/7) — the canonical hard corner-wrap, hand-solved. The recorded path is the target homotopy.
- `j1097.json` / `j1097-0491850.json` (seg 18-29, 10/10) — another solved jump (also j1097-hand-solution is a partial 3/6).
- `j121.json` (seg 42-51, 10/10).
- `single-neo.json` (4-15, 8/8), `winged-single-neo-solver.json` (4-17, 10/10) — "neo"/headhitter style, different obstacle topology — good GENERALIZATION targets once they have block selections.
- `f2f-gapped-angler-solved-debug.json` (0-15, 16/16, debug), `f2f-angler-solved.json` (4-15, 9/9), `jcx-1-solver.json` (0-9, 4/4).

To turn the SOLVE-reference jumps into DERIVE tests, the blocks must be added (pick them in-game, or hand-construct from the world). j154 is the only one with blocks today.

## Test/data anchors (j154 specifics)

- `BlockSolverTest.scenario(route)` builds `JumpPhysicsInputs` (seed from `route.get(32)`, jumpTick 0, strafe = all but the jump tick). Recorded per-tick facing = `route.get(START+k+1).yaw` (outgoing-facing convention; the off-by-one matters — `recordedSolutionIsSweptClean` would fail if wrong).
- j154 blocks (hand-typed; prefer loading from `selectedBlocks`): START(-1601,87,4930), COLLISION(-1601,88,4929), COLLISION(-1601,89,4929), HEAD AABB(X[-1600,-1599.5], Y[89.25,89.75], Z[4929.25,4929.75]), LAND(-1601,87,4928). startTick 32, landingTick 43, jumpTick 32.
