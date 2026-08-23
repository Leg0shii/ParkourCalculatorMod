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

## What to do next

The lever is `CostateDualSolver` primal recovery, not the physics model, not the margins, and not
the inertia pattern. `RelaxationRecovery` (gh-204) already exists downstream to repair degenerate
dual recoveries; the open question is whether the dual can be made to return a linear-feasible point
directly, since a certifying dual returns from `runLadder` immediately and `SlpSolve` never runs,
which is what preserves the turn structure (gh-414).

A cheap detector already falls out of this audit: after the dual, evaluate its answer against the
linear walls. If the linear model itself reports a violation, the margin ladder is pointless and the
inertia passes are pointless, and the run should go straight to recovery.
