# Why the closed form fails to certify (gh-418)

Measured 2026-08-23 on dev `3ba6d3c4`. Tool: `core/src/test/.../anglesolver/DualCertScreen.java`
(`PKC_SCREENS=1 PKC_DC_FILE=<save.json>`).

## Result

**The failure is the dual returning a primal point that is infeasible in its own linear model. It is
not a linear-versus-byte-exact model gap.**

At the dual's own answer, for six solved saves, comparing the dual's exact violation against the
worst violation the *linear* model itself reports on the same yaws, and against the largest
per-tick divergence between the linear prediction and the byte-exact forward:

| save | dual violation | worst linear-model violation | worst model divergence | certifies |
| --- | --- | --- | --- | --- |
| dsfdsffds | 0 | none | 8.6e-4 | yes |
| ben-one-turn | 0 | none | **1.46e-1** | yes |
| mopus/1-dev | 3.77e-2 | -3.47e-2 | 3.0e-3 | no |
| ben-test | 7.25e-2 | -2.11e-2 | 8.39e-2 | no |
| thousand/1 | 1.116 | **-1.116** | **6.9e-4** | no |
| jvel_Linkcraft | 1.840 | **-1.860** | 2.86e-2 | no |

Two readings settle it:

- On `thousand/1` the dual violation and the dual's own linear violation agree to three digits
  (1.116) while the physics divergence is **1600x smaller** (6.9e-4). The dual is not being betrayed
  by the physics; it never solved its own problem.
- `ben-one-turn` **certifies while carrying the largest model divergence in the corpus** (1.46e-1).
  Divergence does not predict certification. Linear feasibility does: the two certifying saves are
  exactly the two with zero linear-model violations.

This reproduces and generalises the 2026-06-10 finding on `speedrun_nightmare_v3` ("a recovery that
violates the LINEAR model's own X@102 wall by ~0.19, dual recovery gap, unrelated to quantization")
and on `_v7` ("linViol==exactViol to ~1e-3, so model-independent"). See
`closedform-recovery-gap` notes and gh-204.

## Ruled out, with numbers

Each of these was tried on `mopus/1-dev` before the linear-feasibility check pointed elsewhere.

- **Margin ladder too short.** `ClosedFormSolve.Config.margins` tops out at 1.0e-2 and its comment
  sizes it for "the ~1e-4 sine-table perturbation". Seventeen single margins from 0 to 0.5 blocks:
  **none certify**. The ladder cannot bridge a gap that is not a tolerance.
- **Sine-table quantization.** Measured drift before the first inertia event is ~5e-6 per tick,
  1.3e-4 over 26 ticks. That matches the 8e-6/tick measured in 2026-06 and is three to four orders
  below the observed failures.
- **Dual Newton iteration cap.** Every rung reports `iters=100` (`CostateDualSolver.MAX_ITER`).
  Raising it: 100 gives 3.77e-2, 300 gives 3.08e-2, 1000 / 3000 / 10000 all give 3.50e-2. It reaches
  its own fixed point. Note gh-384 measured *cuts* to this cap and found them load-bearing; this is
  the opposite direction and it is equally dead.
- **Inertia zeroing pattern.** Inertia is respected via a fixed-point walk that re-derives the
  pattern from each answer, capped at `maxInertiaPasses = 4`. On `mopus/1-dev` it never settles:
  `free -> x@26+z@0 -> x@15+z@0-7 -> x@15-16,@26+z@0-7`. The `if (pass > 0 && !improved) break` bail
  stops it after the first non-improving pass; removing that bail alone moves the violation
  3.77e-2 to 1.20e-2, but the shape gets worse (reversals 7 to 12) and five of six saves are
  unaffected, so it is not a fix.
- **Pattern neighbourhood search.** Twenty-seven patterns around the walk (every pattern visited,
  their union, their intersection, the empty pattern, every single-tick flip off the union), through
  `optimizeWithPattern`: **0 certified**, best 3.39e-2, worse than the walk's 1.20e-2.

## Where the model divergence does come from

Worth recording even though it is not the failure mode. On `mopus/1-dev` the linear-versus-exact
divergence is not accumulation: it sits at ~1.3e-4 through tick 26, then **steps** at tick 27 from
+9.75e-5 to -8.32e-4 on X and compounds to 3.0e-3 by tick 49. Tick 26 is where the exact model
zeroes `vx` (-0.000919, under the 0.005 legacy per-axis threshold) and the linear model agrees
`zeroX[26] = true`. The step immediately after a zeroing tick is the signature of the two models
applying the gate at different points within the tick. Pre-event drift is the sine table; the step is
the inertia gate.

## Inside the dual: where the infeasibility comes from

Tool: `CostateScreen` (`PKC_SCREENS=1 PKC_CS_FILE=<save.json>`), which runs `CostateDualSolver`
directly on the same walls `ClosedFormSolve` builds and prints, per tick, the costate norm and the
magnitude the recovery actually produces, then per wall what the dual believes its value is against
what the recovered yaws really give.

`grad` recovers each tick as `u_t = m_t * g_t / sqrt(|g_t|^2 + EPS2)` with `EPS2 = 1e-14`. Where
`|g_t|` approaches `sqrt(EPS2) = 1e-7` the smoothing term stops being negligible and the recovered
vector falls **inside** the circle of radius `m_t` instead of sitting on it. On `mopus/1-dev`, tick 41
has `|g| = 1.945e-07`, so the recovery returns magnitude 0.291182 against `m_t = 0.327400`, a ratio
of **0.889**. Every wall whose coefficients reach past tick 41 is then evaluated against a vector
11 percent short:

| wall | tick | dual believes | recovered yaws give | slack believed | slack actual |
| --- | --- | --- | --- | --- | --- |
| 27 X | 49 | 4.2769175 | 4.4187883 | +8.62e-3 | **-1.33e-1** |
| 29 Z | 49 | 5.5574759 | 5.5696812 | +4.33e-3 | **-7.87e-3** |

So the dual converges believing wall 27 has slack and it does not.

**Shrinking EPS2 does not fix it.** Sweeping `1e-14 / 1e-18 / 1e-22 / 1e-26 / 1e-30`, dual violation:

| save | 1e-14 | 1e-18 | 1e-22 | 1e-26 | 1e-30 |
| --- | --- | --- | --- | --- | --- |
| mopus/1-dev | 3.77e-2 | 1.47e-1 | 5.03e-2 | 5.14e-2 | 6.07e-2 |
| ben-test | 7.25e-2 | 7.31e-2 | 2.16e-1 | 9.70e-2 | 9.68e-2 |
| thousand/1 | 1.116 | 6.39e-1 | 1.91e-1 | 2.60e-1 | 3.49e-1 |
| jvel | 1.840 | 1.363 | 1.312 | 1.799 | 1.799 |

Nothing certifies at any value, and the ranking is not even monotone: `thousand/1` improves sixfold
at 1e-22 while `mopus/1-dev` gets worse. The shrinkage is a symptom of sitting at a degenerate point,
not the cause; removing the smoothing just relocates the degeneracy.

## Why: this is a duality gap, not a bug

Each tick's decision is an angle, so `u_t` is constrained to a **circle** of radius `m_t`, not a
disc. That primal is nonconvex. The Lagrangian decouples per tick and its maximiser is
`u_t = m_t * g_t / |g_t|`, which is exactly the recovery above. For a nonconvex primal the Lagrangian
maximiser is primal-feasible only when the multipliers happen to make it so; in general the dual
returns a bound it cannot attain.

The gap is directly measurable on `mopus/1-dev`: the dual reports `dualValue = 4.285996460` while the
cap wall `X t=49` has `bPrime = 4.2855385`. Every primal-feasible point satisfies that wall, so the
primal optimum is at most 4.2855385 and the dual bound sits **4.6e-4 above it**. The bound is not
attained, so no amount of converging the dual will produce a feasible recovery.

Active-wall counts line up with this. Cold solve at margin 0, no inertia pattern:

| save | walls | active multipliers | worst actual linear slack |
| --- | --- | --- | --- |
| dsfdsffds | 17 | 4 | -2.9e-10 |
| ben-one-turn | 20 | 8 | -1.59 |
| thousand/1 | 19 | 10 | -0.82 |
| ben-test | 16 | 11 | -0.29 |
| mopus/1-dev | 36 | 13 | -0.13 |
| jvel | 64 | 39 | -1.65 |

With four active walls the maximiser lands feasible to 1e-10. As the active set grows it stops doing
so. (These single-shot numbers are not the ladder's: `ben-one-turn` certifies through the full ladder,
which sweeps margins and inertia patterns, despite this cold margin-0 probe being far off.)

## What to do next

Making the dual itself return a linear-feasible point on these windows is **not reachable by tuning**.
Margins, the iteration cap, the inertia pattern and the norm smoothing were each measured and each
failed, and the duality gap above explains why: the bound is not attained, so a converged dual still
recovers an infeasible point.

That leaves two honest directions, both larger than a patch:

- **Convexify the per-tick set.** Relax `u_t` from the circle to the disc `|u_t| <= m_t`, which makes
  the primal convex and closes the gap, then handle the radial slack (the relaxed answer will want to
  move less than full speed on some ticks). This changes what the closed form means, so it needs a
  ruling before anyone builds it.
- **Accept the gap and repair downstream.** This is what happens today: `RelaxationRecovery` (gh-204)
  and `SlpSolve` pick up infeasible recoveries. The cost is that `SlpSolve` phase 1 returns a simplex
  vertex and shatters the turn structure (gh-414). A repair that preserves turn direction would be
  worth more here than a better dual.

Do not re-attempt: margin ladder extension, `MAX_ITER` increases, `EPS2` changes, inertia pattern
enumeration. All four are measured dead above.

A cheap detector still falls out of this audit: after the dual, evaluate its answer against the
linear walls. If the linear model itself reports a violation, the margin ladder and the inertia
passes are both provably pointless for that solve, and the run should go straight to recovery.
