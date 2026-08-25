# Smoothness feature + the multi-jump convergence root cause: handoff 2026-08-24

Successor to `smooth-tas-handoff-2026-08-23.md` and `defect-correction-measured-2026-08-23.md`. Two threads
ran this session: a shipped smoothness feature, and a deep diagnosis of *why* the solver jitters and misses
findable solutions. The diagnosis is the important part. Read section 4 first if you only read one thing.

Branch `feature/418-defect-correction-polish`, HEAD `3d19c9ff`, rebased cleanly on `origin/dev`
(`49def653`, which now carries #415 DeWiggle, #417 turnCost, #410 diagnostics). One commit on top of dev,
plus uncommitted probe test files (section 5).

## 1. What shipped: SmoothFaceRecovery, gated by the Smooth (TAS) checkbox

`SmoothFaceRecovery` (core solver, committed in `3d19c9ff`). Given a byte-exact-feasible facing sequence, it
walks it along the null space of the active walls toward fewer turn reversals (Gauss-Newton restore under a
second-difference metric, exact-gated every step). Wired in `AngleSolverEngine.runJob` via `smoothFacing` ->
`ClosedFormSolve.recoverFace`, on the winning result of a user-facing solve only, gated by
`objective.smoothLambda > 0` (the existing **Smooth (TAS)** checkbox) plus a per-solve `smoothFinal` flag
that run-ticks search candidates pass false. Smooth off => byte-identical microsecond solver; the ladder,
velocity finder, sweeps, and bruteforce are never touched.

Measured (58 hpk captures): reversals 130 -> 98 on the 25 the ladder certifies (never rougher), and it
**complements DeWiggle rather than duplicating it** (head-to-head on 41 solved captures: DeWiggle alone 185
reversals, DeWiggle then this pass 149, improving 17 of 41). Robust: 400ms budget + try/catch, an objective
guard (`SMOOTH_OBJ_SLACK`), pinned facings frozen via `FacingPrefold`. Full slow suite green off and forced
on (`PKC_SMOOTHFACING=1`).

Two caveats the user surfaced, not yet addressed:
- The **reported runtime excludes the smoothing time** (`solveNanos` is measured at `runJob:1006`, before
  the hook at `runJob:1019`). On a Smooth (TAS) solve the UI shows the pre-smoothing time.
- The **400ms budget is a coarse backstop** (`SMOOTH_BUDGET_NANOS`), checked only at the face-walk outer
  loop, and is an arbitrary value. Tie it to the solve's own deadline rather than a separate constant.

The earlier `DefectCorrectionPolish` dead end and the `FACE_RECOVERY` fallback were removed. `SmoothFaceRecovery`
is smooth-only now (the multistart certify path was deleted per the user's "smoothness only, the solver
already solves everything" ruling). Latent multistart certify data, if ever wanted: hpk 25 -> 40, but SLP
already solves 16 of those 18 in production, so it was never a real solve gain.

## 2. User rulings this session

- **Smoothness only.** Do not wire the certify path; the solver already solves what the user hits.
- **Reuse the Smooth (TAS) checkbox**, no new setting.
- Exhaustive debugging demanded on the hard cases: instrument every step, question every assumption.

## 3. The two probe jumps

- `mopus/1` (`.minecraft/parkourcalculator/mopus/1.json`): 60 ticks, 48 constraints, MAX X, Smooth off. The
  raw solve has 60 heading changes (every tick) and 15 reversals. The "constant between constraints" idea
  (BlockSolveProbe): most of the jump flattens cleanly to constant runs that track the real path, but the
  tight tail (t44+) goes infeasible with one heading per segment. So part of the jitter is arbitrary and
  removable; a tight corridor genuinely needs more changes than one-per-segment. Fixed 6-tick blocks got
  within 0.15 blocks; a proper feasibility-preserving simplifier would land the achievable minimum.
- `thousand/1-dup2` (`.../parkourcalculator/thousand/1-dup2.json`): 49 ticks, **3 jumps**, tight (several
  constraints hit at margin 0.00000), **free start** (translated), MAX X. This is the diagnostic case.

## 4. ROOT CAUSE (thousand/1): the dual does not converge on multi-jump windows

Every symptom the user reported reduces to one defect. Evidence, all reproducible:

- **The recorded save was solved by THIS build** (`modVersion = 1.9.0+build.313.3d19c9f`), reaching
  X = 6523.30772. So there is NO version regression to bisect: it is the current build re-solving worse.
- **The re-solve is deterministic and plateaus at 6523.303681** (4 identical runs, NondetProbe; also
  identical at 20s and 60s, so not time-budget). Below the recorded 6523.30772 and below both the
  6523.305 / 6523.307 targets the user tried, which is exactly why those "fail" now.
- **The dual bound is 6523.789** but it is a **loose, unconverged** weak-duality bound: a single
  `CostateDualSolver.solve` on this window runs all `MAX_ITER=100` iterations with **pgres = 2.435** (should
  be < 1e-8). It never converges. The `DIVERGE_PGRES=4.0` bail does not fire because 2.4 < 4.0, so it grinds
  the full cap. This is the "degenerate high-dimensional landscape of a long multi-jump run" the previous
  handoff flagged.
- **Raising `MAX_ITER` does not fix it**: swept 100 -> 10000, closed form returns null at every value
  (MaxIterSweep, since deleted). So it is not an iteration-count problem; the dual plateaus, it does not
  slowly converge.
- **The recovery from the unconverged dual is garbage**: recovered continuous X = 6520.489 with
  worstViol = 3.09 blocks (ContinuousDiscreteScreen). Three blocks infeasible.
- **Quantization is negligible**: continuous -> byte-exact X drop is 0.007, of which the sine buckets are
  only -0.0003. So the jitter is NOT the discrete bucket grid; the continuous optimum is a clean smooth
  path, and the solver simply cannot find it.
- **B&B confirms the recovery is the wall, not the dual value**: it computes correct per-pattern bounds
  (~6523.79-6523.86) but every node's restore stalls 0.03-0.5 blocks outside feasibility, so the best
  feasible incumbent is ~6523.302. The engine limps to a feasible 6523.304 via SLP/B&B/ILS, jittery
  (15 reversals).

Conclusion: on multi-jump windows the dual does not converge, so the closed-form recovery is broken, so the
engine falls back to slow local search (SLP/B&B/ILS) that produces jittery, suboptimal, fragile results.
Jitter, "6523.307 fails", "auto-improves over re-solves", and environment-sensitivity are all the same
defect. The user's intuition was correct: the optimal path is a clean smooth turn (quantization-negligible),
and the jitter is a solver artifact of a non-converging dual.

Not fully resolved: whether the *true* max is close to 6523.31 or meaningfully higher. The 6523.789 bound is
loose (unconverged), and B&B restores never approach it, so the practical ceiling looks like ~6523.31, but a
converging dual / working recovery is needed to know for sure.

## 5. Git state and the probe harnesses

- Committed (`3d19c9ff`, the feature): `SmoothFaceRecovery.java`, `AngleSolverEngine` (solve overload +
  `smoothFacing` + gate), `ClosedFormSolve.recoverFace`, `FacingPrefold` accessors, `RunTicksController`
  search-candidate flag, `FaceSmoothScreen`, `docs/research/defect-correction-measured-2026-08-23.md`.
- Uncommitted diagnostic screens (all env-gated, skip by default): `BlockSolveProbe` (segment-constant
  solve, `PKC_BLOCK_FILE`), `ThousandDiagScreen` (full-engine solve + `SolverTrace`, `PKC_DIAG_FILE`),
  `NondetProbe` (repeat-solve spread), `WarmStartLoopProbe` (apply/re-sim loop; proved box re-sim does not
  warm-start the solve), `ContinuousDiscreteScreen` (the section-4 continuous-vs-discrete + convergence
  check). Decide whether to keep or drop these.
- `CostateDualSolver.MAX_ITER` was made public for the sweep and reverted to `private static final`.
- Trace: `SolverTrace` writes `build/reports/solver-trace-<tag>.txt`, toggled by `PKC_SOLVER_TRACE` or
  `pkc.solver.trace`. The full thousand/1 trace (53k lines) shows the whole chain.
- Jars built this session (1.8.9 Forge + 26.2 Fabric) at `3d19c9f`; the 1.9.0 comparison jar is at
  `C:\pkc190` (a worktree at tag `v1.9.0`, commit `ac1b35e2`).

## 6. Recommended next steps, in priority order

1. **Fix the dual convergence on multi-jump** (`CostateDualSolver`). It plateaus at pgres ~2.4 and grinds
   all 100 iterations. This is the single defect under everything in section 4. Options: a better step
   (the Newton/PG hybrid is stalling), a proper handling of the degenerate multi-jump landscape, or a
   different recovery that does not depend on the dual converging. Fixing this fixes jitter AND objective
   AND fragility together.
2. If (1) is intractable, restore a real **search** for hard multi-jump free-start jumps (the CMA question
   from the prior handoff): the removed 1.9.0 CMA-ES SEARCHED for feasible high-objective paths instead of
   recovering from a degenerate dual, and it is self-contained (no commons-math3). ~2400 lines, re-integrate
   into today's graph. The user has a v1.9.0 comparison jar to A/B first.
3. The "constant/sparse between constraints" idea (section 3, BlockSolveProbe) is a promising smooth-by-
   construction reparametrization worth developing into a feasibility-preserving segment simplifier.
4. Fix the two smoothing caveats in section 1 (runtime reporting excludes the smoothing; tie the smoothing
   budget to the solve deadline).

## Do not re-run (measured dead this session)

- Raising `CostateDualSolver.MAX_ITER` alone (100 -> 10000): closed form still null on thousand/1.
- Blaming quantization for the jitter: the sine-bucket gap is ~3e-4, negligible.
- Box-state re-sim as a warm-start: WarmStartLoopProbe proved the solve ignores per-tick box pos/vel/yaw
  (recomputes them), so it is dead flat; the app's re-solve "climb" is not box re-sim.

## RESOLUTION (2026-08-24, executed): it is a DUALITY GAP, and the fix is the give-back, not the dual

Section 4 framed the root cause as "the dual does not converge." Measurement sharpened it: it is a genuine
**duality gap / degenerate recovery**, not a convergence bug. A clean 100k-iteration reference subgradient
solver drives the dual value LOWER than `CostateDualSolver` (2.775 vs 3.089, a tighter bound) yet the
MINIMUM recovery violation across all 100k iterates is 2.89 blocks. No `lambda >= 0` yields a near-feasible
recovery: the constant-modulus hidden convexity that makes single-jump recovery exact breaks down on this
coupled 3-jump / 22-wall corridor. So section 6 step 1 ("make it converge") is provably impossible here, and
step 2 (CMA) is unnecessary for the SMOOTHNESS goal.

Why the byte-exact max-X path wiggles (the real question): maximizing X lands on a vertex hugging several
tight opposing-pair corridors at once. To hug them byte-exact on the 65536-bucket sine grid, the yaw
DITHERS at the jump-transition/redirect zones (t13-t17, t26-t30 on thousand); the straightaways are already
smooth. The dither costs sub-micron X per reversal but is load-bearing for the last microns of distance:
measured, at <= 5e-5 blocks give-back ZERO reversals are removable, and at 2.5e-4 give-back the straightaway
dither collapses (15 -> 10 reversals). The remaining ~10 are intrinsic redirect dither; the smooth 4-5
reversal continuous optimum is not byte-exact reachable (that is the gap).

The shipped fix is four lines in `AngleSolverEngine`, all on the existing Smooth (TAS) face-walk (no dual
recovery, no search, no new setting):
- `SMOOTH_OBJ_SLACK` 5e-5 -> 3e-4 (the sub-mm give-back the user approved). The face-walk only ever removes
  reversals; a looser guard just gives it room, so smoothness is monotone in the slack and the give-back is
  bounded and sub-mm.
- Smoothing budget tied to the solve deadline (`deadline/8`, cap 6s; FAST keeps 400ms). The old flat 400ms
  is far too small at n=49 (the face-walk barely moves), which is why smoothing looked broken on thousand.
- `solveNanos` recomputed after the smoothing hook so the reported runtime includes it.

Both were also the two caveats in section 1. Result: thousand/1-dup2 15 -> 10 reversals, deterministic, in
one solve, give-back 2.5e-4 blocks. Slow + fast suites green (CI smooths only the 4x2 `smoothLambda>0`
problems, 1e-2 gap tolerance; j022 has `smoothLambda=0`).

MEASURED DEAD this session (do not re-attempt): the "primal smooth-restore" architecture (seed the restore
from the dual's continuous recovery, GN-restore under the second-difference metric, homotopy to the feasible
fallback). Prototyped fully; the restore plateaus at ~0.035 blocks (inertia-pattern floor) and the homotopy
always lands at ~fallback, so `recoverFace` (the existing face-walk) at the same slack reaches the identical
result. The dual seed contributes nothing over the slack. Removed.

Still open (deferred, optional): 10 is the single-face-walk floor (more time does not help). A multi-restart
face-walk reaches ~8 but needs ~30s and touches the shipped face-walk (mid-size 130->98 regression risk); not
worth 2 reversals now.

## Smoothness stack ablation + the objective-floor cap (2026-08-24, executed)

Ablation over the 58 hpk captures (FAST forced), lower sumRev = smoother. There are FOUR smoothing stages and
none is cleanly removable: DeWiggle (#415) is dominant (+72 reversals without it); the final face-walk (this
session's fix) adds +11 on single jumps and is decisive on multi-jump; turnCost (#417) and SmoothingPolish are
~5% each (weak, overlapping DeWiggle). The mid-solve stages (DeWiggle/Polish/turnCost) are gated by the Smooth
checkbox, NOT by effort, so FAST/CUSTOM already smooth fully; only the final face-walk's depth scales with the
budget (FAST 400ms, CUSTOM deadline/8, THOROUGH cap 6s).

The ablation exposed a real defect: only the final face-walk had a give-back cap. DeWiggle accepted ANY feasible
fewer-runs move (no objective check) and SmoothingPolish traded at the soft smoothLambda rate, so the smoothing
could spend up to 0.169b of objective on some jumps (j148) with no bound. Fix shipped: a hard objective floor
(`obj >= achieved - MAX_GIVE_BACK`) as an accept-gate in `DeWiggle.run` and `SmoothingPolish.accepts`, each with
a settable `public static double MAX_GIVE_BACK`. Default 8e-3b per the user (loose: +19 reversals / 7% on hpk,
kills the 0.02-0.17b trades). The cap is per-pass, so total give-back ~= 2*floor + the final stage's 3e-4b.
Tradeoff curve (floor -> sumRev): off 255, 8e-3 274, 2e-3 288, 1e-3 296, 5e-4 319.

Next (planned, not built): a single "max give-back (blocks)" slider replacing the Smooth checkbox, driving all
stages consistently. Wire it to the two `MAX_GIVE_BACK` statics, the final face-walk `SMOOTH_OBJ_SLACK`, and
turnCost (harder: it is a search-ranking bias, so bounding it means a hard `obj >= best - X` constraint in the
search, not a post-pass gate). Cleanest: floor every pass against the ORIGINAL solve objective (shared
reference) so total give-back equals X exactly rather than stacking.
