# Findings — solving long multi-jump runs (desert-hard v12)

Outcome of the handoff in `NEXT-SESSION-long-jump-solve.md`. **v12 now solves** (byte-exact, all 81
constraints) through the engine, where it previously reported "no solution" and dropped into a ~100 s
hopeless 354-dimensional CMA-ES.

## TL;DR

| fixture | ticks | jumps | constraints | path taken | result | time |
|---|---|---|---|---|---|---|
| j121 | 9 | 1 | — | closed form (µs fast path) | feasible | **75 µs** |
| j154 | 11 | 1 | — | closed form (µs fast path) | feasible | **21.6 µs** |
| j1097 | 11 | 1 | — | closed form (µs fast path) | feasible | **60.4 µs** |
| deserthard-v7 | 189 | multi | — | closed form + alternate direction | feasible | green |
| deserthard-vfail | 176 | multi | — | closed form + alternate direction | feasible | green |
| **deserthard-v12** | **354** | **30** | **81** | **feasibility restorer (fallback)** | **feasible 81/81** | **~1.37 s end-to-end** (restorer itself **~240 ms**) |

- `<0.1 ms` fast path: **preserved** — closed-form per-solve times unchanged (`ClosedFormSolveTest` green:
  75 / 21.6 / 60.4 µs). The new machinery is a strict post-failure fallback.
- `<10 ms` target for v12: **not met.** v12 solves in ~1.37 s. Reaching feasibility at 354 dims on the
  byte-exact model is ~240 ms; the rest is the closed form proving it cannot certify the run. See
  "Speed / the <10 ms gap" below for why and what remains.

## What the diagnosis established (data, not theory)

Run the (committed, `@Ignore`d) harness `V12DiagnosticTest` to reproduce all of this.

1. **The dual does not converge at scale — cause (a), not (b).** On the full 354-tick spec the costate dual
   hits its 100-iter cap with a violation of **36 blocks in the affine model itself** (48 byte-exact). So it
   is not affine-model drift recovering a bad path; the dual optimisation never converges on the degenerate
   landscape of 81 walls. A scalable dual is the only way to solve the whole run "in one go", and the current
   one is not it.
2. **Per-jump, the solver is perfect.** Each of the 31 ground-contact segments, seeded from its recorded
   state, solves with the closed form (31/31, all-objective). The wall is **coupling**, not per-jump
   solvability.
3. **Greedy chaining fails immediately** because the closed form is a wall-*maximiser*: segment 0 (Z/MAX)
   overshoots Z by 2.4 blocks and dooms segment 1. Oracle-guided headings still overshoot (fail by seg 3).
   Pinning a segment's exit to the recorded seam makes the closed form fail outright — it is a vertex solver
   and cannot hit an interior target.
4. **The recorded "drift" (pitfall #3) is a red herring for *solving*.** Forwarding the recorded facings —
   whether via the abs→delta→accumulate round-trip *or* the raw entity yaws directly — drifts the same 0.56
   blocks from the recorded positions and meets only 64/81. But feasibility is judged on the *forwarded*
   path, not on matching the recording. The reframed problem is simply: **find 354 facings whose
   `ExactJumpModel` forward satisfies the 81 constraints** — and such facings exist.

## The solution that landed: `FeasibilityRestorer`

A post-failure fallback (`solver/FeasibilityRestorer.java`) that works **directly in game-facing space on the
byte-exact model** — the thing optimised is exactly the thing run, so there is no affine surrogate to drift
over a long horizon and no facing round-trip. Warm-started from the **editor's current trajectory** (always
available — `boxes.getState(t+1).yaw`; for the debug file this is the recorded run, but in normal use it is
the work-in-progress the user is refining), it drives max-violation to zero in two stages:

1. **Inexact Gauss-Newton.** Active set = constraints within a buffer of their wall. Residuals are the
   byte-exact margins; the Jacobian is finite-differenced through the new **incremental forward**
   (`ExactJumpModel.stepRange`, which recomputes only the tail after a perturbed tick — O(n−t) per column).
   A damped **min-norm** step (smallest facing change, so it stays near the warm start) with a byte-exact
   line search collapses 0.43 → ~0.004 blocks in ~7 steps.
2. **Surgical coordinate polish.** The residual sub-bucket violation (MC's 65536-step sine table fragments
   the feasible set into discrete islands) is cleaned to strict feasibility by block-1 / block-2 moves
   restricted to the ticks influencing still-violated constraints, again via the incremental forward.

Wiring (`AngleSolverEngine.runJob`): it runs **only** after the closed form and its alternate directions fail
**and** the span contains `> 1` jump (`countJumps`), before the CMA-ES fallback. The restorer returns the
byte-exact game facings, which are forwarded directly for the result (re-deriving them via `toGameFacings`
would not be bit-identical). Single jumps return on the closed-form fast path and never reach it.

A second enabling change: `ClosedFormSolve` now **early-outs when the dual diverges** (first-margin violation
≫ the ≤1e-2 the margin ladder can reconcile). This cut a failing long-run closed-form attempt from ~2 s
(eight margins) to ~250 ms (one dual solve), and cannot affect any case that previously succeeded (a 0.1+
violation was never fixable by the ladder). This is what brought v12 end-to-end from ~8.2 s to ~1.37 s.

Supporting primitives added (all additive, fast path untouched): velocity exposed on `ForwardPath`;
`ExactJumpModel.stepRange` incremental forward; `JumpLinearModel.baseArg` (for the analytic Jacobian, see
below); `AngleSolverEngine.debugBuildSpec` test hook.

## Speed / the `<10 ms` gap (honest status)

v12 is ~1.37 s, not `<10 ms`. Breakdown: ~4 closed-form dual solves on 354 ticks (~1 s total, each ~250 ms
hitting the 100-iter cap) + restorer (~240 ms). Two independent reasons `<10 ms` is hard here:

- **The dual costs ~250 ms × 4 just to fail.** Skippable for multi-jump spans whose dual *diverged* (the
  divergence is objective-independent, so the alternate directions cannot help) — but the alternates are
  needed for the *solvable* multi-jump spans (v7/vfail), so this needs the closed form to signal
  *divergence* vs *converged-but-infeasible*. Would take v12 to ~0.5 s. Not done (kept the change focused).
- **Feasibility at 354 dims on the byte-exact model is ~240 ms minimum here.** The finite-diff Jacobian and
  the sine-bucket polish are the cost. An **analytic affine Jacobian** is implemented and exercised
  (`V12DiagnosticTest.analyticGN`, using `JumpLinearModel.baseArg`: `d(addX)/dyaw = −addZ`,
  `d(addZ)/dyaw = addX`) and is far cheaper, but it **stalls at ~0.076** because it omits the
  momentum-cancellation clamp — a hybrid (analytic for the bulk, finite-diff to finish) is the promising next
  step. A genuinely `<10 ms` solve almost certainly needs either a **scalable converging dual** (open
  research — see finding 1) or this analytic/clamp-aware first-order method matured.

## Definition-of-done status

1. v12 solves byte-exact (`isSuccess`, 81/81): **met.** Time `<10 ms`: **not met** (~1.37 s).
2. Single-jump / normal solves `<0.1 ms`: **met** (fast path unchanged).
3. v7 / vfail regressions green: **met.**
4. Phase 2 (maintain/maximise objective) only after phase 1: **phase 1 only.** The restorer returns a
   *feasible* run (near the warm start), not the Z-maximal one; the objective polish is deliberately left for
   phase 2 per the handoff's ordering.
