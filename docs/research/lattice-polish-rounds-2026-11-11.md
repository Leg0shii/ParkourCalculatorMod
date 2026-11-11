# Lattice polish: the grid search repeated from its own result, 2026-11-11

Follow-up to `lattice-polish-start-turn-2026-11-11.md`. The open item there was the ranking of tied facings by
measured loss. The measurement says the loss is somewhere else: in the grid search itself. This change lets the
grid search run again from its own result with other ticks, which closes most of that loss for about twice the
stage time.

## Where the loss is

The stage reports two numbers per jump: the smooth optimum (start and facings as real numbers) and the best grid
point it found. The difference is what the sine table costs plus what the search missed.

**Tied facings.** A facing that several ticks share is enumerated by grid step (`facings` 5, `searches` 3). Twelve
times that budget (`facings` 60, `searches` 36) gains on 2 of 39 jumps with a tied facing, 1e-5 each. The tied step
is found with the default budget; ranking the steps differently cannot gain more than that.

**The grid search.** `LatticeClosure` moves eight ticks together (the four with the most leverage on the tight
rows and the four with the least) and gives every other free tick its single best grid step. On the jumps with a
gap of 3e-5 to 1e-4 the optimum sits in a thin wedge between several tight rows, and eight ticks with a table of
2200 combinations per half do not reach the grid point in it. A larger table helps, at a high price:

| `tableSize` | total gain over 67 captures | stage time |
| --- | --- | --- |
| 2200 (default) | 0 | 1.0 x |
| 4400 | 1.76e-4 | 2.3 x |
| 8800 | 2.9e-4 | 4.3 x |
| 20000 | 3.6e-4 | 7 x |
| 35200 | 3.9e-4 | 12 x |

## Method

The search result is a verified grid point that holds every row. From there the same search runs again with
another group of eight ticks, and the ticks outside the group stay where they are:

- The free ticks are sorted by leverage as before. Round 0 takes the first four and the last four. Round `k` takes
  the same pattern shifted by `4 k` places around the sorted list, so after `size / 4` rounds every tick was in a
  group.
- A later round starts from the grid point itself. It is not moved to the cell centres and not settled behind the
  gates again, because it already is feasible, and no tick outside the group is rounded again.
- A round counts when its best candidate replays byte-exact strictly better than the result so far. The loop stops
  when a full pass over every group found nothing, when the result is within 2e-7 of the smooth optimum
  (`ROUND_FLOOR`), after `rounds` rounds (16 by default) or at the deadline.
- When the first search only finds a grid point after backing the smooth optimum off the walls (`BACK_OFF`), the
  later rounds run against the model that is not backed off (`LatticeClosure.refine`), so they win back what the
  back-off gave away.

Every round is the same deterministic search, so a second run returns the same facings. The stage as a whole can
still only improve a result.

## Results

Cloud container, 4 cores, JDK 21. The option `Full turn start facing` is off by default since the ruling of
2026-11-11 (`lattice-polish-start-turn-2026-11-11.md`), so the numbers are given for both settings. The repeated
grid search gains about the same either way.

### Capture sweep

`LatticePolishSweep` over all 155 specs, standalone stage from the stored seed, against the start turn change:

| | option off (default) | option on |
| --- | --- | --- |
| seeded | 67 | 67 |
| better than before | 29 | 28 |
| worse | 0 | 0 |
| total gain | 6.63e-4 | 6.76e-4 |
| violations | 0 | 0 |
| stage time over all seeded specs | 722 ms to 1537 ms | 1323 ms to 3089 ms |
| median gap to the smooth optimum | 1.30e-5 to 4.59e-6 | 4.65e-6 to 1.80e-6 |
| mean gap to the smooth optimum | 3.15e-5 to 2.08e-5 | 2.79e-5 to 1.70e-5 |

Largest gains, the same in both settings: `mcc11-cross-axis-sign` 1.26e-4, `hpk/d9/j111` 8.6e-5,
`j013-cw2cwwinged` 7.3e-5, `hpk/d11/j1149` 7.1e-5, `j018-tds2tdsbf` 6.6e-5, `j019-3jmmtruenix` 4.9e-5, `j004`
3.9e-5. The slowest jumps take about 120 ms with the option off (`hpk/d9/j108`) and up to 290 ms with it on
(`hpk/d11/j1149`), where the stage runs once per turn.

The sweep prints four digits of the gain; compared at full precision 32 specs are better in both settings. With 8
rounds instead of 16 the total gain is 6.0e-4 for about 13 percent less time (measured with the option on).

### Twelve human jumps, a Fast landing plus the stage, solve, Apply, solve again

Measured while the Fast graph still carried the stage (default parameters). Since the second ruling of 2026-11-11
a Fast solve does not run the stage; the numbers of a 10 s Optimize are under "In Optimize" below.

Option off (default):

| jump | start turn change | this change | gain |
| --- | --- | --- | --- |
| j140 | -826.231873309 | -826.231873309 | 0 |
| j345 | -660.197872806 | -660.197871611 | 1.2e-6 |
| j1099 | -2501.298719493 | -2501.298719493 | 0 |
| j1149 (minimum) | 4950.794602430 | 4950.794602340 | 9.0e-8 |
| j1150 | -2805.297967220 | -2805.297966942 | 2.8e-7 |
| j155 | 4984.763186192 | 4984.763186192 | 0 |
| j716 (minimum) | -699.950269410 | -699.950269446 | 3.6e-8 |
| j718 | -1902.861611313 | -1902.861571206 | 4.01e-5 |
| j828 | 4978.013121953 | 4978.013121953 | 0 |
| j925 | 2809.336732908 | 2809.336749669 | 1.68e-5 |
| j154 (minimum) | -1599.700114039 | -1599.700114039 | 0 |

j335 does not land in a Fast solve in either build. The second and third solve after Apply return the same objective,
with one exception per build: before this change j718 came back with -1902.861583318 on the second solve (now the
same value three times), and with this change j345 comes back with -660.197872806, the value of the build before,
1.2e-6 under its first solve. A Fast landing after Apply starts from the applied start position, lands somewhere
else and the grid search is local to that landing.

Option on:

| jump | start turn change | this change | gain |
| --- | --- | --- | --- |
| j140 | -826.231856187 | -826.231856187 | 0 |
| j345 | -660.197806168 | -660.197806168 | 0 |
| j1099 | -2501.298692362 | -2501.298692362 | 0 |
| j1149 (minimum) | 4950.794602430 | 4950.794602328 | 1.0e-7 |
| j1150 | -2805.297958400 | -2805.297958168 | 2.3e-7 |
| j155 | 4984.763186192 | 4984.763186192 | 0 |
| j716 (minimum) | -699.950269427 | -699.950269446 | 1.9e-8 |
| j718 | -1902.861582285 | -1902.861547669 | 3.46e-5 |
| j828 | 4978.013121953 | 4978.013121953 | 0 |
| j925 | 2809.336757148 | 2809.336769916 | 1.28e-5 |
| j154 (minimum) | -1599.700117640 | -1599.700117640 | 0 |

Every jump returns the same objective on the second and third solve after Apply.

## In Optimize

Since the second ruling of 2026-11-11 the stage runs in the Optimize and Custom graphs only
(`lattice-polish-2026-11-10.md`, "Where the stage runs"). The Optimize graph gives it wider parameters than the
defaults (`facings` 64, `searches` 21, `tableSize` 30000, `window` 64, `BuiltinGraphs.POLISH_*`) and a time share
of 5 percent of the budget, at most 2 s: 150 ms of a 3 s Optimize, 500 ms of 10 s, 2 s from 40 s on.
`LatticePolishSweep` measures that case with `-Dpkc.polish.optimize=true`, `-Dpkc.polish.ms=<n>` and
`-Dpkc.polish.rounds=<n>`. All numbers below: 67 seeded specs, closed form seed, single run each; a run under a
deadline depends on the machine.

### One round against 16 with the Optimize parameters

| time per jump | better with 16 rounds | worse | total gain |
| --- | --- | --- | --- |
| 150 ms | 11 | 0 | 9.2e-5 |
| 500 ms | 14 | 0 | 1.68e-4 |
| 2 s | 17 | 0 | 3.66e-4 |
| no deadline | 18 | 0 | 4.05e-4 |

Without a deadline the stage takes 17.9 s over the 67 specs with one round and 65.1 s with 16 (`taser-80t` 9.4 s,
`j020-panewallsingleblockbf` 7.1 s, the human `j925` 7.0 s). Inside Optimize the deadline cuts that off: the
rounds use the time the stage has and no more.

### The wide parameters under the deadline: a quick search first

One grid search with the wide parameters can take the whole time share and end without a grid point. The wide
stage under its deadline against the default parameters without one:

| time per jump | wide is worse on | of those without any gain |
| --- | --- | --- |
| 150 ms | 10 | `j005` (default 6.8e-3), `hpk/d9/j121` (6.4e-3), `hpk/d10/j148` (4.2e-3) |
| 500 ms | 6 | `hpk/d10/j148` (4.2e-3) |
| 2 s | 3 | none, each loses under 2e-5 |

The default parameters find those gains in 20 to 63 ms. In the engine the human j345 showed it: a 10 s Optimize
came back with -660.206476562, the stage spent its 500 ms on one search and found no grid point. The same landing
with the default parameters gives -660.197872806, which is 8.6e-3 further.

`LatticePolish.polish` therefore runs the search with the default parameters first whenever its own parameters
are wider (`Config.quick`; with the option `Full turn start facing` on it covers every start turn), and then the
wide search from that result with the time that is left. Option off:

| time per jump | against the wide search alone | against the default parameters without a deadline |
| --- | --- | --- |
| 150 ms | 12 better, 4 worse, total +1.86e-3 | 18 better, none worse |
| 500 ms | 8 better, 1 worse, total +4.32e-3 | 23 better, none worse |
| 2 s | 4 better, 1 worse, total +2.75e-5 | 25 better, none worse |

The worse ones lose 1e-7 to 2e-7, because the wide search starts from another point. The exception is `taser-80t`
(80 ticks) at 150 ms: the wide search alone finishes its first search right at that deadline (about 100 ms
without one), and with the quick search before it (9 ms, no result on that jump) the run found nothing and lost
1.57e-2. At 500 ms it is back.

With the option on, the quick search has tried every turn before the wide search starts. The start facing is one
turn away on 39 of the 67 jumps at each of the three deadlines, against 16, 20 and 30 with the wide search alone
(40 without a deadline).

### Twelve human jumps, Optimize 10 s

One run each, with the quick search first. The stages before the polish are cut at 7.0 s, the polish runs until
7.5 s at the latest and the solve ends at about 7.6 s.

| jump | option off | option on | start facing turn |
| --- | --- | --- | --- |
| j140 | -826.231873309 | -826.231845885 | +360 |
| j345 | -660.197872806 | -660.197752012 | +360 |
| j1099 | -2501.298703062 | -2501.298678096 | +360 |
| j1149 (minimum) | 4950.794602257 | 4950.794602257 | -360, same value |
| j1150 | -2805.297958184 | -2805.297949439 | +360 |
| j155 | 4984.763186192 | 4984.763186192 | |
| j716 (minimum) | -699.950269473 | -699.950269454 | |
| j718 | -1902.861547729 | -1902.861532334 | +360 |
| j828 | 4978.013118202 | 4978.013118202 | |
| j925 | 2809.336761230 | 2809.336769916 | -360 |
| j154 (minimum) | -1599.700114069 | -1599.700117640 | -360 |

j335 does not land in 10 s. With the wide search alone and the option on, two of the eleven jumps came back a turn
away (j1150, j154), and j345 and j1099 came back without a gain from the stage. j716 differs by 1.9e-8 between
the two columns because its stage runs into the deadline in both. j828 stays 4.7e-6 under its smooth optimum,
which is the tied facing limit named in `lattice-polish-facing-rows-2026-11-11.md`.

## A stall the cross check found on the way

`YawDirectionCrossCheckTest` draws its one-way rows from the path the engine solves, so a better `gh386-j335-lowx`
result drew other rows: dF >= 0 on T1, T19 and T20. The solved path holds all three by 2 to 5 degrees, every pair
of them lands in about 6 s, and all three together ran 39 s without a landing, in this build and in the builds
before it. The free rescue's centre chain lands the pairs through the relaxation recovery at one margin of its
ladder; with the third row its SLP misses at every margin.

`FreeStartImproveNode.centerChain` now repeats the chain without the one-way rows when the ordered chain misses
and keeps the result when the byte-exact check of the full constraint list passes. The spec lands in 4 s that way
(`freestart/gh553-j335-lowx-dfge`). It is the same idea as the first round of the tie cascade: solve without the
order and look whether the order holds anyway.

## What is left

- **One tied group that carries the jump** (the `gh386` lucky seed captures): the gap is the tied facing's own grid
  step, which no search over the other ticks can close.
- **A grid point only behind the back-off** (`hpk/d11/j1149`, 1.2e-4 left): the first grid point is found at a
  back-off of 4e-6 and the later rounds win back part of it. A search that walks toward the walls from that point
  with more than eight ticks at once is open.
- `j008-bfneo` 9.6e-5, `j020-panewallsingleblockbf` 8.7e-5, `hpk/d10/j148` 5.0e-5 and the human `j140` 3.4e-5 keep
  a gap after 16 rounds.
