# Smooth (TAS) regression and the closed form's degenerate recovery: handoff 2026-08-23

Session worked from one report: **Smooth (TAS) on dev produces visibly choppier facing paths than the
released 1.9.0.** It ended in the closed form's primal recovery. This file is the whole trail, so the
next session does not repeat the four dead ends.

Baseline dev `3ba6d3c4`. Reproduction files `parkourcalculator/mopus/1-dev.json` and `1-main.json`
(same TAS, same settings, solved on dev and on 1.9.0 respectively).

## 1. What smooth means here (user rulings, these override any earlier note)

**Only the yaw values matter.** They are stored in the TAS as per-tick relative changes. Not the flown
XZ path, not the velocity heading. An earlier phrasing about "wiggles in the air trajectory" meant the
yaw over the airborne ticks, not a path metric. Do not build path or curvature metrics.

The defect is a **change of turn direction**. Rate variation inside one direction is not a defect:

| relative yaw values | verdict |
| --- | --- |
| `10 10 10 10` | good |
| `10 20 30 40` | good, steady acceleration |
| `10 20 10 20` | acceptable, never turns back |
| `10 -10 10 -10` | bad |
| `10 20 -1 40` | bad, and the `-1` is as unwanted as a `-10` |

**The objective does not matter at all**, as long as every constraint is met.

Two measurement traps found the hard way:

- The "typical 44 degree arc" in the hand-made part of the TAS is largely **45-strafes**, not a
  smoothness target. `DeWiggle.MIN_ARC_DEG = 45` survives only because a parameter sweep picked it.
- The panel's "Yaw jerk" uses the **unanchored** `Angles.wiggleDeg(yaws)` while the objective uses the
  **anchored** `wiggleDeg(anchor, yaws)`. Panel and objective numbers differ by the launch-yaw term.
  Every jerk figure in this file and in the PRs is anchored.

## 2. Why dev regressed against 1.9.0

`538c92bf` (the CMA-ES removal) deleted every stage whose **inner fitness** contained the smooth
penalty. Count of `smoothPenalty` / `objective.scored` sites per stage:

| stage | v1.9.0 | dev |
| --- | --- | --- |
| `CmaesJumpHarness` | 2 | deleted |
| `SolveCore` | 5 | deleted |
| `SnapRepairPolish` | 3 | deleted |
| `AlmSnapStage` | 1 | deleted |
| `BucketAscentPolish` | 1 | 1, only reachable via ILS |
| `IlsPolish` | 1 | 1 |
| `SeamSweepRecovery` | 0 | 0 |
| `BoundPrunedRecovery` | 0 | 0 |
| `SlpSolve` | 0 | 0 |

On the reported file 1.9.0's chain reads *"CMA-ES (better objective), optimal at constraint cap"* with
**654,851 evaluations**. Every one of those samples was ranked on a fitness containing the turn
penalty. dev's replacements (seam sweep, B&B) score `path.getPos(obj.tick, obj.axis)` and nothing
else, so turn direction only enters afterwards as a filter over candidates generated blind to it.

## 3. Three PRs open against dev

| PR | issue | contents | state |
| --- | --- | --- | --- |
| #415 | #414 | `GraphRunner` wrap-reserve fix, new `DeWiggle` pass | green |
| #417 | #416 | `Angles.turnCost` replaces jerk in the objective | green |
| #419 | #418 | this audit, `DualCertScreen`, `CostateScreen` | green |

**#415.** Two things. `GraphRunner` set `wrapPending` when the graph merely *contained* a `wrapIls`
node and only cleared it when that node executed, so a route branching around wrap never released the
2.5 s reserve and every later node got a deadline already in the past: `smoothFinal` entered with
`remainingMs=0` while the overall budget still had **2478 ms**. And `DeWiggle` collapses same-sign turn
runs below 45 degrees to a constant turn rate, restoring feasibility with a Gauss-Newton step that
freezes the flattened span and measures the correction in second differences. Gated to the final
smoothing node and to `smoothLambda > 0`, so a solve with Smooth off is unchanged bit for bit. On the
reported file: turn runs 11 to 7, reversals 10 to 6, jerk 1040 to 902.

**#417.** `turnCost = REVERSAL_COST_DEG * (sign changes) + RATE_TIEBREAK * jerk`, with
`REVERSAL_COST_DEG = 90` and `RATE_TIEBREAK = 0.02`. The tiebreak is not cosmetic: a pure integer
count is flat almost everywhere and leaves the search no direction to follow, and dropping it costs
three reversals on the longest corpus file. Reversals over six saves: 1-dev 10 to 6, thousand/1 8 to
6, ben-test 4 to 1, two unchanged, jvel 19 to 20.

## 4. Why the closed form fails to certify

**The recovery, not the dual.** The closed form recovers each tick from the costate direction,
`u_t = m_t g_t / ‖g_t‖`. At the optimum `c = A^T lambda`, which is plain stationarity, so the costate
vanishes: measured `‖g‖ ~ 1e-7` on **49 of 49 ticks**. `atan2` of a vanishing vector is noise, and
that noise is what fails the byte-exact check.

The dual is correct throughout:

- converges in **142 iterations**, `pgres` 3.5e-07 (`MAX_ITER = 100` truncates it 42 short)
- bound tight: `dualValue = 4.285538558` against the binding wall's `bPrime = 4.2855385`
- complementary slackness exact: every active wall reads `believed == bPrime` to eight digits

It produces correct multipliers and a correct **active set**. It has no rule for choosing a point on
the optimal face, because at the optimum the Lagrangian is flat in every tick direction. The
information about which point to take lives in the active walls, not the costate.

**There is no duality gap.** Relaxing each tick from the circle `|u_t| = m_t` to the disc
`|u_t| <= m_t` is lossless, since both have support function `m‖g‖`. This dual is the convex problem's
dual and its bound is attained. An earlier revision of the audit claimed a gap; that was wrong.

### Measured dead, do not re-attempt

- **Margin ladder.** `Config.margins` tops out at 1.0e-2 and its comment sizes it for "the ~1e-4
  sine-table perturbation". Seventeen single margins from 0 to 0.5 blocks: none certify.
- **Sine table.** Drift before the first inertia event is about 5e-6 per tick, 1.3e-4 over 26 ticks.
  Three to four orders below the failures. The code comment overstates it.
- **`MAX_ITER`.** 100 gives 3.77e-2, 1000 / 3000 / 10000 all give 3.50e-2 on the ladder's violation.
  Raising it makes the dual converge but does not fix the recovery. (gh-384 measured *cuts* and found
  them load-bearing, so any change here still needs its own corpus run.)
- **`EPS2` smoothing.** Magnitude shrinkage is real (ratio 0.889 at one tick) but secondary. A
  warm-started continuation from 1e-6 down to 1e-34 moves the violation only 0.509 to 0.175, and at
  1e-34 the magnitude ratio is 0.999965 with the violation still 0.175. Converging the dual and
  keeping the directions meaningful are opposed.
- **Inertia pattern enumeration.** The fixed-point walk never settles
  (`free -> x@26+z@0 -> x@15+z@0-7 -> x@15-16,@26+z@0-7`). Removing its `!improved` bail moves
  3.77e-2 to 1.20e-2 but worsens the shape and leaves five of six saves untouched. Twenty-seven
  patterns around the walk certify none.

## 5. The production fix

`ClosedFormSolve.repairRecovery`, on `scratch/smooth-primal-recovery` at **`a93af643`**. Not merged.

Damped Gauss-Newton on the violated walls of the same linear model the ladder used, correction
measured in second differences (`secondDifferenceMetric`) so a repair cannot flick the facing path it
is fixing, over `repairMargins = {1e-3, 5e-3, 2e-2, 6e-2}`.

Certified solves over the 58 hpk captures: **ascending 25 to 37, robust 20 to 35**, 2.2 s to 3.1 s.
Full `:core:check -PslowTests` green, 131 classes, 695 tests, 0 failures.

### Ordering is load-bearing. Three variants measured

1. **Repair inside a rung**: hpk 41/42, but regresses five `closedform/*` captures by 0.02 to 0.03
   blocks. An inward-margin answer wins a rung that a **later inertia pass** would have certified
   cleanly. Note `ProblemsTest.runClosedForm` calls `ClosedFormSolve.optimize` cold and direct, so
   there is no downstream stage to blame for this.
2. **After all rungs and passes, seeded from every rung recovery**: hpk 40/38, but breaks the three
   `loopmm` captures.
3. **Shipped**: after all rungs and all passes, seeded from the best point and the running best only.
   hpk 37/35, suite green.

Holding a repaired certificate back *inside* the pass loop does not work: pass N+1 then derives its
inertia zeroing pattern from the repaired path and never finds the clean certificate. The repair must
be fully outside the loop.

### The j008 golden was regenerated and that is correct

`J008VelocityFieldGoldenTest` drifted. Checked before regenerating: cells that land with constraints
met go **40 to 42 of 71**, same size and same cell count. The new field is strictly better. Always
verify with a probe before regenerating a golden; do not just delete and re-run.

## 6. Prototype: choosing the smooth member of the optimal face

Also on `scratch/smooth-primal-recovery`, in `SmoothRecoveryProbe` (test tree). Commits `c48fb4d9`
(recovery that certifies), `734ec2a2` (smooth-metric repair), `784731bd` (null-space face walk),
`f4e60c6a` (Pareto flooring), `7363d726` (multi-start).

On `mopus/1-dev` it reaches **exactViol 0.0 at 7 reversals** against the dual seed's 9, and because it
certifies, `runLadder` returns immediately and `SlpSolve` never runs, so nothing shatters the shape.

Key mechanics, each measured:

- **Flatten and repair fight.** Of 170 reversal-killing attempts, 147 had the repair fail outright and
  23 had it succeed while re-adding the reversals. The flatten was never wrong (it dropped reversals
  9 to 5 or 7 every time); the least-norm correction undid it. Projecting the flatten direction onto
  the **null space of the active walls** before stepping fixes this: the step is feasible to first
  order so the repair only removes second-order drift.
- **Pareto flooring.** Feasibility outranks shape, since a certifying result skips `SlpSolve`
  entirely. Between two feasible candidates the smoother wins. Between two infeasible candidates a
  violation gain that costs reversals is refused, because an infeasible result goes to the downstream
  repair anyway and only its shape survives as a seed. Without this, thousand/1 went 8 reversals to 16
  and jvel 23 to 38.
- **Multi-start.** The Gauss-Newton restore is local and stalls in a basin. Multi-starting from
  coherent perturbations reaches feasibility where one start cannot: jvel 1.90 to 0.0 (1 of 48 starts).
- **`-Dpkc.sr.usesaved`** seeds from the save's own recorded answer. That turns thousand/1 into 0.0 at
  3 reversals and jvel into 0.0 at 12. **Circular as a benchmark** and off by default. It is not
  circular in the engine, where the recovery would warm-start from the incumbent earlier graph stages
  produced, so those numbers indicate what that wiring would be worth.

The probe models a **subset** of the real problem and this is why it stalls on some saves. On
`ben-test` it covers 16 walls out of 80 constraints, ignores 64 facing constraints, and treats the
start as fixed when the spec has `freeStart=true` with a 0.725 by 1.6 block box. `ClosedFormSolve`
handles all of that already (`FacingPrefold`, `freeP0`), which is why the production fix in section 5
is the right home and the probe is not.

## 7. Tools

| tool | what | how |
| --- | --- | --- |
| `DualCertScreen` | per tick, linear model vs byte-exact position; per tick velocity vs the inertia gate; per constraint as each model sees it | `PKC_SCREENS=1 PKC_DC_FILE=<save>` |
| `CostateScreen` | costate norm and recovered magnitude per tick; per wall what the dual believes vs what the recovered yaws give | `PKC_SCREENS=1 PKC_CS_FILE=<save>` |
| `HpkCertifyScreen` | certification over all 58 hpk captures, A/B via `-Dpkc.cf.repair=0` | `PKC_SCREENS=1` |
| `SmoothRecoveryProbe` | the section 6 prototype | `PKC_SR_FILE=<save>`, `-Dpkc.sr.*` |

Run them via direct `java -cp $(printTestCp)`; the init-script recipe is in the post-CMA cleanup
notes. `-Dpkc.cf.repair=0` is a dev kill switch and should not ship as is.

## 8. Open

- `ben-test` and `thousand/1` still do not certify through the production path.
- The variant that gains hpk 40/38 is available if the three `loopmm` captures can be re-pinned.
- The prototype certifies **and** smooths, but only in the test tree against a subset of the problem.
  Porting the null-space face walk into `ClosedFormSolve`, where the facing constraints and the free
  start are already modelled, is the piece that would let the downstream repair, and with it the need
  for `DeWiggle`, be dropped entirely.
- `MAX_ITER = 100` truncates dual convergence at 142 on the reported window. Raising it is untested
  against the corpus in the direction that matters.
