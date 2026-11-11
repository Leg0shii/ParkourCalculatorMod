# Lattice polish: facing inequalities, cross-axis walls and thin feasible wedges, 2026-11-11

Follow-up to `lattice-polish-2026-11-10.md`. That record left nine of the 154 capture specs declined by the stage:
five with a facing inequality (`YawOrder` links and one-sided walls), two with a cross-axis wall and two with a damage
tick. This change makes the stage accept the first two classes and fixes two reasons why an accepted jump could come
back without a gain.

## What was missing

1. **Facing inequalities.** A dF >= v / dF <= v between consecutive ticks or a one-sided absolute F wall is not
   absorbed by the tie fold, so the row model had no way to keep it and `LatticePolish.unsupported` declined the spec.
   These are the #553 jumps (j022, j121, j335, j345, j925), where the user asks for a one-way turn.
2. **Cross-axis walls.** `X - Z` and `Z - X` constraints compare `|x1 - x2|` with `|z1 - z2|`, which is not one row.
3. **The smooth optimum on an inertia threshold.** With the facing rows in place j335 and j345 still came back
   unchanged. The trace showed the reason: the smooth optimum of the rows puts a carry velocity exactly on the
   inertia threshold (the gate row is tight). The driver then re-solves around that point for every tied facing, and
   it rebuilt the model from the exact path there. In float arithmetic the exact path at that point stops the other
   axis, so the rebuilt model had another inertia pattern and no feasible point.
4. **Thin wedges.** On the same jumps the feasible grid points sit in a wedge between two or three tight rows that
   is a few 1e-6 wide. The grid search listed every adjuster tick two steps to each side and paired each combination
   with its 24 nearest partners by one key. Neither is enough there: the wedge needs an early tick five or more steps
   away, and the nearest partners by the key row all break another row.

## Method

### Facing rows

`TrigRowModel` takes the carried order from `YawOrder.carry` and adds rows that are linear in the facings
(`Row.facing`, evaluated in radians):

- per link `wrapDelta(F[hi] - F[lo]) >= minTurn`: one row at `minTurn` (plus `YawOrder.FLOAT_MARGIN_DEG` when the
  turn is non-zero, the same margin `SlpSolve` and `YawOrder.project` keep, because a float game facing can round
  across an exact wall) and one guard row at `180 - 0.02` degrees, which keeps the turn away from the wrap where the
  order would flip;
- per one-sided wall: the wall itself with the same margin rule, and a guard row 0.02 degrees inside the far side of
  the 180 degree window `YawOrder.Bound` gives it.

Calibration fixes the wrap branch of each row at the incumbent. An incumbent that already violates a guard row sits on
the wrap; the stage declines it (`onWrap`) and keeps the landing.

On the grid the facing rows are exactly additive in the per-tick steps, so the grid search evaluates them on the grid
yaws. The final check is unchanged: every candidate is replayed byte-exactly and judged on the float game facings
with zero tolerance, so the stage still can only improve a result.

`LatticePolish.unsupported` now declines a facing constraint only when `YawOrder.carry` cannot carry it: an
equality that the fold does not absorb, a link across more than one tick, or two links that contradict each other.

### Cross-axis rows

At the build path the signs of `x1 - x2` and `z1 - z2` are known. With those signs fixed the constraint is one linear
row in four positions, and two guard rows `sx (x1 - x2) >= 0`, `sz (z1 - z2) >= 0` keep the signs
(`TrigRowModel.crossAxisRows`). The polish stays inside the sign orthant of its incumbent, which is the same kind of
restriction as the fixed inertia pattern.

### Keeping the inertia pattern

- `TrigRowModel.sameStoppedAxes` compares the exact path's stopped axes at a point with the pattern the model was
  built for. `LatticePolish.subproblem` builds the re-solve model at the smooth optimum when the pattern holds there.
  Otherwise it bisects toward the incumbent (`PATTERN_BISECTIONS`, the tied facing under enumeration and the pins stay
  at their target) and builds at the closest point that still has the pattern. The calibrated rows are exact at the
  build point, and the pattern is a property of the rows, so the solve from there reaches the same optimum.
- `LatticeClosure.settleGates` moves the grid base back into the pattern before the search: while a gate row is
  violated at the base, it moves one earlier tick by one to three grid steps. Among the steps that close the gate it
  takes the cheapest (objective change plus the multiplier-weighted new violation of the other rows); when none
  closes it, the one that reduces it most. A step that breaks a facing row or an earlier gate is never taken.
  `settleFacingRows` does the same for facing rows by moving the later tick of a link in whole steps.

### Grid search for thin wedges

- The adjuster ticks are split by time: the earlier half is one list, the later half the other.
- A row is closed for a partial combination when no later tick and no start translation can change it. A combination
  that violates a closed row is dropped while the list is built (`enumerate`). An early wall therefore cuts the early
  ticks' combinations down hard, and the freed table space goes into reach: each half is listed with the widest
  uniform reach whose pruned list still fits `tableSize` (`widest`), up to `maxOffset` steps instead of a fixed two.
- For each combination the partner scan runs outward from the nearest key and counts partners that fit every row. It
  stops after `window` fitting partners or after `window * SCAN_DEPTH` scanned ones. Before, it took the `window`
  nearest whether they fit or not.
- When a search finds no grid point, the smooth optimum is backed off every wall and threshold by 1e-6, then 4e-6,
  then 1.6e-5 (`BACK_OFF`, a warm re-solve on the same model) and searched again. A backed-off centre has grid points
  around it on every side, where the tight optimum has them on one side of each active row only.

Tried and dropped: listing the combinations with a beam ranked by the Lagrangian value `objective + sum(mu * excess)`
instead of a uniform reach. The ranking prefers combinations at the far end of the reach that no partner can
complete, and j121 lost its gain. The uniform reach with closed-row pruning keeps the neighbourhood of the centre
complete, which is what the pairing needs.

## Results

Cloud container, 4 cores, JDK 21, single run each. Objective values as the engine reports them; a gain is counted in
the direction of the capture's objective.

### The five declined #553 captures, a Fast landing plus the stage

Landing: a Fast solve. Result: that landing plus the stage. Since the ruling of 2026-11-11 the Fast graphs do not
carry the stage (`lattice-polish-2026-11-10.md`, "Where the stage runs"), so in the engine these gains show in
Optimize and in a Custom graph, not in Fast.

| capture | landing | with the stage | gain |
| --- | --- | --- | --- |
| `gh313-j121-dfneo` | -41.299657879 | -41.299373211 | 2.85e-4 |
| `gh553-j022-minturn` (objective is a minimum) | -531.700118131 | -531.700131712 | 1.36e-5 |
| `gh553-j335-dfle` | -697.299247045 | -697.295056616 | 4.19e-3 |
| `gh553-j345-dfneo` | -660.194916871 | -660.193016532 | 1.90e-3 |
| `gh553-j925-dfle` | -1003.297971806 | -1003.297891060 | 8.08e-5 |

Each is a `problems/polish/` case in `engine` mode (a Fast landing, which never carries the stage) with a `minGain` below the measured gain.

### The two cross-axis captures

| capture | gain from the closed form seed | distance to the smooth optimum |
| --- | --- | --- |
| `gh398-optimize-2jump` | 3.08e-5 | 1e-11 |
| `mcc11-cross-axis-sign` | 8.32e-3 | |

### Capture sweep

`LatticePolishSweep` over all 154 specs, standalone stage from the stored seed:

| | PR 657 | this change |
| --- | --- | --- |
| declined | 9 | 2 (both damage tick) |
| seeded | 65 | 67 |
| improved | 56 | 62 |
| violations | 0 | 0 |
| stage time over all seeded specs | 305 ms | 779 ms |

Of the 65 specs both builds seed, 24 are better, 41 the same and none worse. The larger steps are jumps where the
old search found nothing because of the two problems above: `hpk/d9/j127` 0 to 1.71e-2, `hpk/d11/j1149` 0 to 1.35e-2,
`hpk/d9/j121` 0 to 6.43e-3, `j005` 2.8e-4 to 6.80e-3, `j017` 0 to 1.63e-3.

### Twelve human jumps, a Fast landing plus the stage

Measured while the Fast graph still carried the stage; a Fast solve itself returns the numbers from before PR 657
now. Identical to seven digits except four jumps that move by 2e-7 to 7e-6 in the good direction (j345 -660.1978799 to
-660.1978728, j718 -1902.8616148 to -1902.8616113, j925 2809.3367308 to 2809.3367329, j828 4978.0131219 to
4978.0131220). j154 stays at -1599.7001140.

## Limits and open work

- **Damage tick, flow push, free start velocity, smoothed objective, position equality** still decline. A position
  equality cannot hold byte-exactly at a second grid point, so it stays out on purpose.
- **Grid loss.** On j335, j121, j925, j140 and j1149 the result is still 2e-5 to 5e-5 short of the smooth optimum.
  That is the distance the sine table grid costs. The designed lever is the second set of table cells one full turn
  away for ticks that face (-90, 0): the float game facing there has another rounding, so the same tick has two
  grids. It needs a candidate that carries exact game facings through Apply (the `yawLocked` row flag and
  `Plan.lockYaws` exist, no stage sets them), a ruling on how far a facing may sit from the base window, and a check
  in game.
- **Tied facings.** The enumeration of a tied facing ranks its grid steps by the smooth value of the re-solve. Ranking
  by the measured loss per facing would find the last 1e-6 on j828.
- **Cross-axis sign orthant.** The polish never crosses `x1 = x2` or `z1 = z2`. A jump whose optimum is in another
  orthant needs a landing there first.
