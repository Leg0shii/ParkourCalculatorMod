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
