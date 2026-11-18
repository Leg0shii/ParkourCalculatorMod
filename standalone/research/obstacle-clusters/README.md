# Avoidance on obstacle clusters

2026-11-17. Implements step 2 of the [generation follow-ups](../../docs/design/GENERATION_FOLLOWUPS.md)
on `perf/early-passage-screen`. The evidence was measured on the uncommitted
working tree over parent `b2977e0f` (the recorded `source.patch` and runtime
hashes are in the [cold provenance](2026-11-17/cold-provenance.json) and the
native report); the implementation commit is the one that adds this file.

## Behavior

Avoidance encounters are per obstacle cluster (solids whose expanded boxes overlap
or touch) instead of per solid. A cluster's sides at each phase are the maximal
free rectangles around its members active at the player's height
(`spatial/footprints.py`), so a face two flush blocks share is one side and no
side runs through a neighbour. Sides of different active sets whose regions nest
continue one side in the walk. See [avoidance passages](../../docs/design/AVOIDANCE_PASSAGES.md).
New identities: constraint policy9/state4.

## Validation

The selected native gate passes **646 tests** with zero failures, errors or skips
and unchanged source during the run ([report](2026-11-17/native-report.json)).
The wall-and-ceiling integration fixture is now one obstacle: its passage takes a
corner side bounded by both blocks, with the same 12-tick feasible route.

## Real11 cold 300 s control

Command from the [runbook](../../docs/RUNBOOK.md#run-a-solve-and-report-it),
`--profile-mode bookkeeping`. Baseline: `runs/solve-report-check` at `ed5e9c95`
(per-solid encounters).

| | Baseline | Clusters |
| --- | ---: | ---: |
| Best T / Z | 64 / -1093.2381026602418 | 64 / -1093.2381026602416 |
| First full solution | 14.7 s | 6.6 s |
| Search ended (`numerical_unknown`) | 26.6 s | 12.0 s |
| Solver requests / workers | 53 / 49 | 25 / 21 |
| Independent replays | 47/47 | 20/20 |
| Frame 4 alternatives solved / rejected before solving / dropped | 21 / 76 / 47 | 7 / 49 / 23 |
| Frame 8 alternatives solved / rejected before solving / dropped | 21 / 76 / 47 | 7 / 49 / 23 |

Frame 4 now solves one alternative per B6 south-to-west switch tick (t61 to t66;
t61 comes back IPOPT `Infeasible_Problem_Detected`) plus the east route, which the
linear control relaxation blocks before solving and which stays unresolved, as in
the baseline. Frame 8 solves the switch ticks t58 to t63 plus one route that adds
`Z ≥ -1093.7` at the end. Every duplicate B7 row alternative is gone. The Z
difference in the last digit is a different IPOPT path to the same optimum.
Report: [cold report](2026-11-17/cold-report.md); summary:
[cold summary](2026-11-17/cold-summary.json).
