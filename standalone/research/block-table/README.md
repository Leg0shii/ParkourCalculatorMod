# Block table for avoidance

2026-11-18, `perf/early-passage-screen`. Measured on the working tree over `db7f8f61`
(the block table for generators); the commit that adds this file applies the table to
avoidance. Design: [obstacle relevance](../../docs/design/OBSTACLE_RELEVANCE.md) and
[avoidance passages](../../docs/design/AVOIDANCE_PASSAGES.md).

## Behavior

A landing segment's avoidance encounters cover only obstacle clusters with a member in
the block table of the segment's launch block. A segment after a ladder tick, or longer
than the table's flight, keeps every cluster. Obstacle clusters are joined from
`SceneGeometry.near` neighbours instead of every solid pair. New identities: constraint
policy13/state9, events constraintPolicy 14.

## Scaling run

`benchmark.py` builds `ConstraintVariants` for a synthetic 60-tick route with 10 landing
segments next to a start block at the origin, plus far solids at X/Z 1000+, and
enumerates every variant. With the table, each segment gets the start block's table
solids, as `search.engine.segment_solids` computes them for a route launched from it.

```sh
OPENBLAS_NUM_THREADS=1 PYTHONPATH=src:. python research/block-table/benchmark.py 60 10 both 7 25 50 100
OPENBLAS_NUM_THREADS=1 PYTHONPATH=src:. python research/block-table/benchmark.py 60 10 table 1000
```

Load average 5.5 to 11 during the runs (shared machine); seconds are wall time.

| Far solids | Items without / with | Prepared without / with | Seconds without | Seconds with, all-pairs clusters | Seconds with, grid clusters |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 7 | 80 / 10 | 80 / 10 | 0.033 | 0.005 | 0.006 |
| 25 | 260 / 10 | 1,034 / 10 | 0.368 | 0.004 | 0.005 |
| 50 | 510 / 10 | 97,409 / 10 | 41.604 | 0.004 | 0.007 |
| 100 | 1,010 / 10 | 477,659 / 10 | 356.718 | 0.077 | 0.005 |
| 1,000 | not run | not run / 10 | not run | 1.393 | 0.012 |

Raw output: [all-pairs clusters](2026-11-18/benchmark-both.txt) and
[1,000 solids](2026-11-18/benchmark-table.txt) before the grid clustering,
[grid clusters](2026-11-18/benchmark-clustered.txt) after it. The remaining 1.39 s
at 1,000 solids before the grid change was the all-pairs loop in
`SceneGeometry._cluster_map` (500,500 pairs).

## Real11 cold 300 s control

`runs/block-table-s2` against `runs/block-table-s1` (generator table only): T64,
Z -1093.2381026602416, 18 requests, 10/10 replays, `exhausted`, in both. Obstacle checks
fall from 600 to 380 over the 12 movements that report them, and the number of
touchable checks is the same in every movement; the report is otherwise identical apart
from timings.

## Soundness checks

`tests/unit/constraints/test_encounters.py` `BlockTableEncounterTests` prepares every
cluster item the table drops under the route's unconditioned envelopes and requires no
possible or uncertain phase: 40 dropped items on Real11 nodes and 921 over 20 random box
scenes. The drop itself rests on the table's flight bound; this check confirms it against
the route's own reachability analysis.

## Per-tick variant (evidence only, not adopted)

`per_tick.py` builds, for each launch owner, the ticks after the launch visit at which
each block can be touched: at tick t the radius covers states up to t+1
(0.4 + 0.7 + 0.4 t) and the feet lie between the run-off and jump traces at t and t+1,
with the same step and body-height allowance as the block table. It then runs Real11
with every generator question and every avoidance item checked against it.

```sh
PKC_RUN_TICKS_COMPILER=$PWD/build/native/compiler.json OPENBLAS_NUM_THREADS=1 \
  PYTHONPATH=src:. python research/block-table/per_tick.py <out>/per-tick.json
```

Results ([raw](2026-11-18/per-tick.json)), measured on `861390e1`:

| Scenes | Owners | Block entries, table / per-tick | Block-tick entries | CPU ms, table / per-tick |
| --- | ---: | ---: | ---: | ---: |
| Real11 | 7 | 31 / 31 | 332 | 2.89 / 8.63 |
| 20 random box scenes | 320 | 1,143 / 1,076 | 11,479 | 113.17 / 352.98 |

In the instrumented Real11 run the per-tick table would have removed 52 of 514 route-based
generator questions, and the route-based check had ruled out all 52 already, so no
generator choice changes; at the measured 0.2 ms per question that saves about 10 ms. It
would have removed 128 of 312 avoidance items the block table keeps; the Real11 report
shows all but 7 of 380 obstacle checks already cannot touch the player. The run's
machine was loaded (load average 7 to 13, a game client using two cores): it ended
`numerical_unknown` after 37.5 s with T64, the same objective and 18 requests, and a
plain control on the same source under the same load did the same (58.6 s), so the
search explored more nodes than the 366-question control. The counts are per question
and per item, so the ratios stand.
