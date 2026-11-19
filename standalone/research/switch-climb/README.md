# Choosing obstacle sides by optimizing, with a switch-tick climb

2026-11-17. Implements step 4 of the [generation follow-ups](../../docs/design/GENERATION_FOLLOWUPS.md#7-choose-obstacle-sides-by-optimizing-with-a-switch-tick-climb)
on `perf/early-passage-screen`. The evidence was measured on the uncommitted
working tree over parent `6b964cb0` (the recorded source hashes are in the
[cold provenance](2026-11-17/cold-provenance.json)); the implementation commit is
the one that adds this file. The report was regenerated after the run with the
final `tools/profiling/trace.py`, which changed only how families are labelled.
After the run, two identical `offer` branches in `repair.py` `arrive` were merged
without changing behavior; the native gate below ran on the committed source.

## Behavior

Every anchor leg optimizes its alternatives instead of returning the first
feasible one. Alternatives that differ only in when they switch obstacle sides form
a family; each family is climbed from its first solved switch to its ±1
neighbours and stops when neither improves. The best alternative is kept; up to 2
others with the same ticks and an objective within 0.3 blocks are kept as
fallbacks and restored only when a later leg fails, before slower run-tick
arrivals. Decision checks only prune and commit none of their alternative's rows.
See [avoidance passages](../../docs/design/AVOIDANCE_PASSAGES.md#choosing-among-alternatives).
New identities: constraint policy9/state5, repair avoidancePolicy10, repair
snapshot version 3.

## Validation

The selected native gate passes **659 tests** with zero failures, errors or skips
and unchanged source during the run ([report](2026-11-17/native-report.json)).

## Real11 cold 300 s control

Command from the [runbook](../../docs/RUNBOOK.md#run-a-solve-and-report-it),
`--profile-mode bookkeeping`. Baseline: `runs/step3-min-width` at `350064f6`.

| | Step 3 | Switch climb |
| --- | ---: | ---: |
| Best T / Z | 64 / -1093.2381026602416 | 64 / -1093.2381026602416 |
| First full solution | 6.72 s | 6.10 s |
| Search ended (`numerical_unknown`) | 12.08 s | 9.96 s |
| Solver requests / workers | 25 / 21 | 18 / 17 |
| Independent replays | 20/20 | 10/10 |
| Frame 4 alternatives solved (IPOPT requests) | 7 (6) | 4 (3) |
| Frame 8 alternatives solved (IPOPT requests) | 7 (7) | 3 (3) |

Frame 4 climbs the B6 south-then-west family from input 60 (IPOPT
`Infeasible_Problem_Detected`, counted as infeasible) to input 61 (best, Z
-1093.823) and input 62 (worse); inputs 63 to 65 are never solved. The east route
is its own family and is blocked by the linear control relaxation before IPOPT, as
before. Frame 8 solves inputs 57 (best) and 58 of the south-then-west family plus
the one south-west-north alternative, which reaches the same Z; four switch
inputs are never solved. The intermediate legs had one or two alternatives each,
so no fallback was stored. They keep the same alternatives; because they now
optimize, three witnesses make more onward progress than the step 3 first-feasible
ones (frame 0 route 1: -1304.565 instead of -1304.993; frame 5: -982.821 instead of
-982.866; frame 6: -1271.548 instead of -1271.694, from `routes.json`), and the
others match to 1e-8. Fewer replays reflect the sibling routes no longer created,
not lost coverage.
Report: [cold report](2026-11-17/cold-report.md); summary:
[cold summary](2026-11-17/cold-summary.json).
