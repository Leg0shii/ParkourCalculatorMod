# Lattice polish: start position and facings solved together, 2026-11-10

Question from the maintainer: gains of 1e-5 and less matter, and the search over the free start position is the
least explored part of the solver. Repeated Fast solves of `hpk_human/d12/j154_1bm_Head_Butterfly_Neo` also scatter:
each Apply moves the start by about 1e-5 and the next solve lands between -1599.700034 and -1599.700092 in 2 to
350 ms.

## Diagnosis

Before this change no stage searched the start position and the facings together. A solve found a landing for the
pinned start, and `translatedStart` then slid that fixed set of facings inside the start box. The result is a
coordinate-wise optimum: the facings are best for the old start, the start is best for the old facings. On the twelve
human captures the Fast result sat 2e-6 to 2e-3 short of what the same landing basin allows, and Optimize (10 s)
closed only part of that.

Two facts make a direct method possible:

1. With the key schedule and the inertia pattern fixed, every position and every carry velocity is an exact linear
   function of the per-tick (sin, cos) pairs and of the start translation. Every wall, every start box bound and the
   objective are therefore rows of one form:
   `g(z) = c + sum over v of (a_v cos z_v + b_v sin z_v) + p_x t_x + p_z t_z <= 0`,
   where `z_v` is the facing of tie group `v` (a dF = 0 chain is one variable, `YawTies`) and `(t_x, t_z)` moves the
   start. The inertia pattern itself is a set of rows of the same form on the carry velocities.
2. The game only offers 65536 facings per tick, and the exact model is additive across ticks: changing tick `t` by
   `k` sine table steps changes every row by an amount that does not depend on what the other ticks do, as long as
   the inertia pattern holds.

## Method

`LatticePolish` (driver), `TrigRowModel` (the rows and the smooth solve), `LatticeClosure` (the grid search), run by
the `latticePolish` graph node from any landing that already holds every constraint.

1. **Smooth joint optimum.** `TrigRowModel` builds the rows from the incumbent's exact path (constraints, inertia
   gate rows for the incumbent's pattern, four start box rows) and solves start and all facings together with an
   augmented Lagrangian and a damped Newton step (shifted Cholesky). The rows are calibrated against the byte-exact
   path at a grid reference, so the model has no linearisation error at the point it starts from.
2. **Tied facings by enumeration.** A facing that several ticks share (a run-up on one angle) moves the whole jump,
   so it is not rounded. Its grid steps around the smooth optimum are enumerated (`facings`), each with a warm
   started re-solve of everything else, and the best few (`searches`) go on, best first, pruned by their smooth
   value against the best exact result so far.
3. **Grid closure by meet in the middle.** The single-tick facings must land on the grid without leaving any wall.
   The ticks with the most leverage on the tight rows become adjusters (`adjusters`, default 8); every other
   single-tick facing takes its best single step. The adjusters are split in two halves, every combination of steps
   per half is listed with its exact effect on every row (`tableSize`), one half is sorted by a key that does not
   depend on the start translation, and each combination of the other half scans the nearest partners (`window`).
   For a pair the start translation is eliminated per axis in closed form. With m rows that the translation cannot
   absorb, the expected loss against the smooth optimum falls with the number of pairs, about 2 D / N for m = 2
   (D the spread of a row over one half, N the combinations per half).
4. **Byte-exact verification.** The best candidates are replayed through `ExactJumpModel` with the float game
   facings, the start is re-optimised by `PathTranslation`, and a candidate counts only when it lies in the start
   box, holds every constraint with zero tolerance and beats the incumbent strictly. The stage can therefore only
   improve a result.

The smooth solves carry a work limit counted in Newton steps (`work`), not in time, so a solve of the same jump
gives the same result on every machine.

The stage declines a spec it cannot model exactly (`LatticePolish.unsupported`): a facing inequality the tie fold
does not absorb (`YawOrder`), a cross-axis constraint, a position equality, a damage tick, a flow push, a free start
velocity, a smoothed objective.

## Related work

- Lossless convexification in discrete time (Luo, Echigo, Acikmese, arXiv 2410.09748): the relaxed problem can leave
  the constraint surface at up to n_x - 1 grid points. This is the reason the dual stages need a recovery step at
  all, and why the polish starts from a landing instead of from the relaxation.
- Sphere decoding for multistep finite control set model predictive control (Geyer and Quevedo): the closest work on
  picking the best point of a finite input set around an unconstrained optimum. It handles a quadratic cost without
  state constraints; here the walls are the hard part, so the closure pairs two halves against the tight rows.
- Meet in the middle for knapsack type problems (Horowitz and Sahni, 1974): the two-list structure of the closure.
- Sum-up rounding and integer approximation error in mixed-integer optimal control (Sager and co-authors): bounds the
  loss of rounding a relaxed control by the grid size. The same argument gives the 2 D / N estimate above.
- Augmented Lagrangian methods (Hestenes, Powell, Rockafellar) for the smooth solve.

## Results

Engine runs through `HpkStartBenchmark` (research source set), 1.8.9 captures, every constraint met.

Objective before and after, both tiers. The gain is the Fast gain in the objective's own direction.

| jump | goal | Fast before | Fast with the stage | Fast gain | Optimize 10 s before | Optimize 10 s with the stage |
| --- | --- | --- | --- | --- | --- | --- |
| j140 | MAX | -826.2338869 | -826.2318733 | 2.0e-3 | -826.2319303 | -826.2318733 |
| j345 | MAX | -660.2078867 | -660.1978799 | 1.0e-2 | -660.2064766 | -660.1978689 |
| j1099 | MAX | -2501.2991744 | -2501.2987195 | 4.5e-4 | -2501.2987031 | -2501.2987031 |
| j1149 | MIN | 4950.7951792 | 4950.7946024 | 5.8e-4 | 4950.7958548 | 4950.7946023 |
| j1150 | MAX | -2805.2992182 | -2805.2979672 | 1.3e-3 | -2805.2980520 | -2805.2979582 |
| j155 | MAX | 4984.7631860 | 4984.7631862 | 2e-7 | 4984.7631862 | 4984.7631862 |
| j716 | MIN | -699.9502144 | -699.9502694 | 5.5e-5 | -699.9502616 | -699.9502695 |
| j718 | MAX | -1902.8624458 | -1902.8616148 | 8.3e-4 | -1902.8616929 | -1902.8615488 |
| j828 | MAX | 4978.0130819 | 4978.0131219 | 4.0e-5 | 4978.0131181 | 4978.0131182 |
| j925 | MAX | 2809.3363553 | 2809.3367308 | 3.8e-4 | 2809.3362871 | 2809.3367511 |
| j154 | MIN | -1599.7000639 | -1599.7001140 | 5.0e-5 | -1599.7001109 | -1599.7001141 |

Fast with the stage is at or past the old Optimize result on every jump except j1099 (1.6e-5 short). The stage
itself takes 1 to 30 ms in Fast.

j335 stays unsolved in both tiers with and without the stage: the stage needs a landing to start from.

The reference optima stored in `HpkStartBenchmark` are not a like for like bar for these captures: they were solved
under smaller constraint sets than the captures carry today (5 to 8 rows against 22 to 55), and the j1099 entry is a
stored failed solve (42 of 52 constraints met). The honest comparison is against the same build without the stage.

Repeated Fast solves of j154 with Apply moving the start between solves (`RepeatSolve ... FAST 10 applysim`):

| | objective range | time per solve after the first |
| --- | --- | --- |
| before | -1599.700034 to -1599.700092 | 1 to 341 ms |
| with the stage | -1599.700114039 every time | 9 to 26 ms |

The scatter came from the start drifting with every Apply; once the stage puts the start at its joint optimum the
start no longer moves, so the closed form lands on the first try each time.

Sweep over every capture in the test resources from the closed form seed (deterministic, no engine): 154 specs, 9
declined, 65 with a feasible seed, 56 improved, 9 kept, none returned that failed the byte-exact check. Typical
gains 1e-5 to 4e-3, time 0.5 to 30 ms.

In Optimize the stage needs its own share of the time budget (`reserveFraction` 0.05, at most 2 s): the stages
before it run up to the reserve of the wrap stage, so without a reserve the polish started with no time left and
changed nothing.

## Limits and open work

- The remaining distance to the smooth optimum is 3e-7 to 4e-5 on these jumps and comes from the grid, for example
  two tight walls with one free tick between them (j140).
- Facing inequalities (`YawOrder` rows) are rows of the same form in the facings and can be added.
- The stage stays inside the base wrap window. The cosine index of 1.8.9 can slip against the sine index at
  facings 360 degrees apart, which is worth another 1e-5 to 7e-5 on diagonal jumps; that needs locked absolute
  facings per tick in the closure tables.
- Tied facings along a flat valley. On j828 three tied facings and the start can trade against each other at almost
  no smooth loss, and the grid quality differs along that valley: Fast lands at 4978.0131219, a cold Optimize in
  another part of the valley at 4978.0131182, the smooth optimum is 4978.0131230. The tied steps are enumerated by
  distance from the rounded smooth optimum, which does not follow the valley. The matching method is a closest
  vector search in the metric of the reduced Hessian (the sphere decoding idea above), ranked before the re-solves.
- A cold search for other landing basins (j335) is a different problem: this stage polishes a basin, it does not
  find one.
