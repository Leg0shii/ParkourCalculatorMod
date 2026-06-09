# Findings — solving long multi-jump runs from scratch (desert-hard v12 / v13)

`deserthard-v12` (354 ticks, 30 jumps, 81 X/Z constraints) now **solves from scratch** — using only the
resume start state, the input-specified physics structure, and the constraints. No recorded trajectory, no
recorded facings. `deserthard-v13` (v12 with one input facing reset, so its recorded path is broken/off the
map) is the **identical** optimization problem and solves identically — the honest proof that the debug
oracle is gone.

## The hard lesson

An earlier iteration warm-started the long-run fallback from the editor's current trajectory
(`boxes.getState(t+1).yaw`). That is the recorded path the handoff explicitly forbade depending on
(§3: "the general solution must still solve each jump from scratch"). It made v12 look solved, but it was
solving *from the answer*: feeding the recorded facings (≈0.4 blocks from feasible) into a local polish.
v13 — one input changed — exposed it: the warm start was 4.2 blocks off and the polish failed outright.
Proven directly: the local restorer only reaches feasibility from a ≤~0.5-block start; from any honest
init (50–100 blocks) it fails. **It was a polish, not a solver.** The whole approach was scrapped.

## What is legitimately available (and what the solve uses)

- **Start state** — resume pos/vel/yaw at `startTick` (`boxes.getState(startTick)`); present on any solve.
- **Structure** — per-tick ground/air from the slip overrides + `JUMP` keys in the input rows
  (`defaultSlipperiness: AIR` + `slipperiness: DEFAULT` ground ticks), strafe mode. This is input-specified,
  **not** derived from the trajectory — v12 and v13 build a byte-identical problem (35 ground ticks, same
  masks, same start, same 81 constraints).
- **Constraints** — the 81 walls. Critically they are **footprint boxes at each jump's landing**, e.g. tick
  36: `X∈[−10.3,−8.7], Z∈[−4.3,−2.7]`. Their centers are natural waypoints.

## The from-scratch solver (`solver/LongRunSolver.java`)

Runs only as a post-failure fallback for multi-jump spans (the closed-form dual does not converge at scale);
single jumps return on the µs fast path and never reach it. Everything is on the byte-exact `ExactJumpModel`
in game-facing space (the thing optimized is the thing run — no affine surrogate to drift, no facing
round-trip):

1. **Waypoint construction.** A heading controller faces each tick toward its segment's footprint centre
   (heading corrected by the per-tick strafe phase `JumpLinearModel.baseArg`), forwarding tick-by-tick. A
   constructive guess from the constraints alone — lands a few blocks from feasible (v12: 2.77).
2. **Wide Gauss-Newton.** Active set = constraints within a (widening-on-stall) buffer; damped min-norm step,
   byte-exact finite-difference Jacobian via the incremental forward `ExactJumpModel.stepRange`, line search
   on the true max-violation. Collapses the bulk (v12: 2.77 → 0.065).
3. **Refine** (`FeasibilityRestorer.refine`): a narrow Gauss-Newton + block-1/block-2 coordinate polish that
   crosses MC's discrete sine buckets.
4. **Feasibility pump** (bounded): if the polish leaves a single binding constraint a bucket short, attack it
   directly — scan its highest-leverage facings over a fine grid for the best max-violation move.

Supporting primitives (additive, fast path untouched): velocity on `ForwardPath`; `ExactJumpModel.stepRange`
incremental forward; `JumpLinearModel.baseArg` (analytic facing→position derivative); `debugBuildSpec` hook.

## Status (all measured, full `:core:test` green — 14 classes, 0 failures)

| fixture | ticks | path | result | time |
|---|---|---|---|---|
| j121 / j154 / j1097 | 9–11 | closed-form µs fast path | feasible | **72 / 20 / 69 µs** |
| deserthard-v7 / vfail | 176–189 | closed form (margin ladder, alt direction) | feasible | green |
| **deserthard-v12** | **354** | **LongRunSolver (from scratch)** | **81/81 byte-exact** | **~2.2 s** |
| **deserthard-v13** | **354** | **LongRunSolver (from scratch, broken trajectory)** | **81/81 byte-exact** | **~2.2 s** |

- `<0.1 ms` fast path: **preserved** (closed-form solves byte-identical).
- No trajectory dependence anywhere: v12 ≡ v13 ⇒ identical solve.

## Remaining work

- **Speed.** v12 is ~2.2 s, not `<10 ms`. ~1.9 s of it is the closed form running its full margin ladder
  across four Solve-For directions *before* the fallback. (An earlier "skip margins when the first is far
  infeasible" optimization was reverted — it wrongly killed v7/vfail, whose ladder legitimately climbs from a
  >0.1-block first margin to feasibility. The safe version keys off the *dual's* own divergence signal, not
  the violation magnitude — not yet done.) The from-scratch solve itself is ~0.35 s; the analytic-Jacobian
  GN and a tighter dual-divergence gate are the levers to push it down.
- **Robustness of the pump** on minimax plateaus (a pair-move variant) so the long-run solver, not CMA-ES,
  also covers shorter hard multi-jump spans.
- **Phase 2** (maximise the objective from the feasible run) is still future work; the solver returns a
  feasible run, not the Z-maximal one.
