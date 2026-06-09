# Findings — solving long multi-jump runs from scratch (desert-hard v12)

Long multi-jump runs (`deserthard-v12`: 354 ticks, 30 jumps, 81 footprint/wall constraints) now **solve from
scratch, robustly, in ~0.16 s** — using only the resume start state, the input-specified physics structure,
and the constraints. No recorded trajectory, no recorded facings, no per-fixture tuning.

| fixture | ticks / jumps | path | result | time |
|---|---|---|---|---|
| j121 / j154 / j1097 | 9–11 / 1 | closed-form µs fast path | feasible | 72 / 20 / 57 µs |
| deserthard-v7 / vfail | 176–189 / 15–16 | receding-horizon (from scratch) | 81/81 | green |
| **deserthard-v12** | **354 / 30** | **receding-horizon (from scratch)** | **81/81 byte-exact** | **~159 ms** |
| **deserthard-v13** | 354 / 30 (broken trajectory) | receding-horizon | 81/81 | ~157 ms |
| **deserthard-nothing** | 353 / 30 (no trajectory, 1-tick shift of v12) | receding-horizon | 81/81 | ~65 ms |

Full `:core:test`: all green.

## The two dead ends (why the final approach is what it is)

1. **Warm-starting from the editor's recorded trajectory.** The handoff forbade depending on the recorded
   path (§3), and it was load-bearing: the fallback only reached feasibility from a ≤~0.5-block start, i.e.
   the recorded answer. v13/v-nothing (same problem, no usable trajectory) exposed it. Scrapped.
2. **A monolithic 354-dimensional local search** (waypoint guess + global Gauss-Newton + polish). FRAGILE: it
   lands in different local minima / minimax plateaus depending on incidental details — a **one-tick shift**
   (`deserthard-nothing`) made it stall a sine-bucket short and fail. Not a solver. Scrapped.

## The robust solver: receding-horizon (model-predictive) decomposition

`solver/LongRunSolver.java`. The convex Lagrangian dual (`ClosedFormSolve` / `CostateDualSolver`) solves a
single jump to GLOBAL optimality in microseconds, and — measured — keeps converging for windows of up to
~10 jumps, but not across the full run (the degenerate landscape of dozens of jumps). So:

- Solve a sliding **window** of 10 jumps to global optimality with the dual (trying alternate Solve-For
  directions, since feasibility is objective-independent).
- **Commit** its first few jumps, chaining their exact byte-exact exit state (pos+vel) into the next window's
  seed; slide.
- The remaining **overlap is lookahead**: a committed jump's exit is, by construction, the entry of a feasible
  (window − commit)-jump continuation, so it can never doom the next (window − commit) jumps — the coupling
  that defeats a greedy one-jump-at-a-time chain. (A research pass confirmed this is multiple shooting with the
  per-jump dual as inner solver, the overlap playing the role of backward-reachability "feasible-entry"
  pruning.)
- Windows chain their own game facings, so the concatenated run is feasible by construction; **re-verified
  byte-exact**, so a coupling failure returns null rather than ever a false success.

**There is no free global guarantee** — a window sees only 10 jumps, so the lookahead must exceed the run's
coupling horizon. Measured (lookahead sweep on v12 / nothing): **greedy (lookahead 0) fails; the coupling
horizon is ~5 jumps** (lookahead ≤4 fails, ≥5 solves). The shipped first try commits 3 (lookahead 7, a margin
above 5); if a commit ever gets stuck the solver **retries with a smaller commit = more lookahead** (down to
commit 1 = lookahead 9, the most a 10-jump window can give); and the byte-exact verify is the final net. A
window-size ladder (10→7→…→1) additionally covers spots where the dual can't solve a full 10-jump window.

Why it's robust where the local search wasn't: **every window is solved by the convex dual** — no local
optima, no plateaus, no sine-bucket stall, no initial guess, no tuning — so the result is invariant to the
incidental problem details that broke the monolithic search.

## Engine integration (and the speed win)

`AngleSolverEngine.runJob` now branches on jump count: a **single jump** takes the µs closed-form fast path
(unchanged, `<0.1 ms`); a **multi-jump span** goes straight to the receding-horizon solver. Each window IS the
closed form, so a short multi-jump span is solved in one window exactly as before, while a long span no longer
wastes the monolithic dual's full margin ladder across four directions on the whole horizon first — which is
what cut v12 from ~2.1 s to ~0.16 s. CMA-ES remains the last-ditch fallback.

Supporting primitives (additive, fast path untouched): velocity on `ForwardPath`; `JumpLinearModel.baseArg`;
`AngleSolverEngine.debugBuildSpec` test hook. (Agent-A's `CostateDualSolver` early-divergence bail stays — it
speeds each dual solve and does not affect convergent duals.)

## Remaining work

- **Phase 2 (maximise the objective).** The solver returns a feasible run; only the final window optimises the
  real objective. A global strictly-feasible objective ascent (`BucketAscentPolish` on the byte-exact model)
  from the feasible run is the next step.
- The window/commit sizes (10/5) are robust across all fixtures here; if a future run has a longer coupling
  horizon, the principled upgrade is explicit multiple shooting with backward-reachable feasible-entry boxes
  as seam constraints (see the research synthesis) — but the simple receding horizon has not needed it.
