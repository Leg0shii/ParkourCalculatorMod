# Performance: measurements, hotspots and invariants

Where solver time goes, what has already been optimized, and the contracts any
speedup must preserve. Measurements are timed observations of one cold control,
not optimality certificates or guaranteed recoverable speedups.

## Measuring

Use the cold profiler from `standalone/` with the runbook environment, a fresh
output directory and single-threaded BLAS:

```sh
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input cases/real11/physical-anchors.json --output /new/path/cold \
  --seconds 300 --profile-mode bookkeeping
```

`--profile-mode enumeration` attributes constraint-enumeration work instead.
Reports record source revision and hashes, fixture, interpreter, native
descriptor, the request ledger and independent retained-witness replays; the
per-route report and the other outputs are described in
[solve reports](RESULTS_AND_DEBUGGING.md#solve-reports).
Self times subtract instrumented children; inclusive categories overlap; hook
overhead is included. Keep profiler hooks in `tools/profiling/`, never in
production code.

## Real11 cold 300 s controls

| Source | Change | Best T | Best Z | Requests / workers | Expanded | Replays | Evidence |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| `bbb375cf` | unrestricted initial domain | 64 | -1093.2381026602416 | 61 / 55 | 39 | 55/55 | [bookkeeping baseline](../research/bookkeeping-baseline/README.md) |
| `86c06e39` | skip already-visited constraint children | 64 | -1093.4443574703134 | 72 / 66 | 39 | 61/61 | [enumeration reduction](../research/enumeration-reduction/README.md) |
| `641c68b5` | anchor search under global run budgets | 67 | -1093.823129063534 | 49 / 47 | 21 | 41/41 | [run budgets](../research/anchor-run-budget/README.md) |
| `e2b5e02c` | screen passage prefixes before extension, ceiling moves on | 67 | -1093.823129063534 | 59 / 57 | 21 | 50/50 | [early passage screen](../research/early-passage-screen/README.md) |
| `e266785c` | geometry-driven avoidance passages | 64 | -1093.2381026602418 | 53 / 49 | 37 | 47/47 | [geometry passages](../research/geometry-passages/README.md) |
| `b2977e0f` + step 2 | avoidance on obstacle clusters | 64 | -1093.2381026602416 | 25 / 21 | 37 | 20/20 | [obstacle clusters](../research/obstacle-clusters/README.md) |
| `6b964cb0` + step 4 | legs optimize, switch-tick climb, close fallbacks | 64 | -1093.2381026602416 | 18 / 17 | 37 | 10/10 | [switch climb](../research/switch-climb/README.md) |

The switch-climb run finds T64 at 6.1 s and stops at 10.0 s with
`numerical_unknown`: the DFS ran out of runnable work while the B7 frame stayed
suspended on one avoidance alternative that the linear control relaxation
blocked before any IPOPT solve. Such a blocked alternative now keeps its proof and counts
as infeasible, so the current control (`1ea2bb1f` plus that change) ends `exhausted` with the
same T64, objective and 18 requests. Four IPOPT solves ended
`Infeasible_Problem_Detected`; a local IPOPT infeasibility is not a certificate,
so the run claims no exhaustive coverage. The B7 frames solve 3 alternatives each
instead of 7 (21 before obstacle clusters): the fourth alternative of the first B7
frame is now ruled out before solving, because the linear relaxation LP that HiGHS
used to leave `Unknown` is resolved by the interior-point retry (132 LPs solved in
that run, 15 proved infeasible, none undecided, one retry; requests unchanged); every anchor leg now optimizes
instead of stopping at its first feasible point, so feasibility-only solves remain
only in decision checks. Fewer replays reflect the dropped sibling routes, not
lost coverage of the kept routes.

Reading every generator reachability query from one approach per prefix and start
(instead of one per queried tick) cut the Real11 control's coefficient compiles from
213 to 156 and `analyze_model` self time from 0.82 s to 0.71 s, with an identical
report. An approach as long as the scene's 200-input limit cost more than the short
ones it replaced (2.14 s), so the shared approach stops at the last tick a generator
can query.

## Measured hotspots

Unrestricted 300 s profile at `bbb375cf`
([raw profile](../research/bookkeeping-baseline/evidence/2026-11-15/unrestricted300-profile.json)):

| Instrumentation bucket | Calls | Self seconds |
| --- | ---: | ---: |
| `numerical.evaluate` | 53 | 145.6983 |
| `identity.digest` | 30,804,036 | 67.1996 |
| `copy:spatial.checkpoints:retain_prefix` | 449 | 29.8097 |
| `copy:search.repair:snapshot` (three sites) | 699 each | 33.6407 |
| `solver.invoke` | 61 | 9.9012 |

`numerical.evaluate` includes unwrapped preparation, refinement and validation
work. Verify digest call counts and call sites before attributing cost; the cold
profiler writes its shared store only after search, so durable checkpoint IO
needs a separate control.

Constraint enumeration after `86c06e39` runs 2.93x to 3.56x faster on captured
B6/B7 catalogues, with 94.1% to 94.7% fewer iteration checks and identical
ordered options. That is targeted enumeration work, not a whole-solver speedup.

## Cost levers already in place

- IPOPT row reduction: final rows fall from 278 to 91 on the benchmark
  ([constraint reduction](IPOPT_CONSTRAINT_REDUCTION.md)).
- Feasibility-only solves stop at the first validated point; only the anchor and
  final solves optimize ([feasibility mode](IPOPT_FEASIBILITY_MODE.md)).
- Content-addressed manifest plus SQLite checkpoints
  ([checkpoint storage](CHECKPOINT_STORAGE.md)).
- Shared interaction screens before any solve: interval and LP reachability
  ([obstacle relevance](design/OBSTACLE_RELEVANCE.md)), and accumulated passage
  rows screened before each extension ([avoidance passages](design/AVOIDANCE_PASSAGES.md)).
- Bounded accelerator caches: interaction gate 512 entries
  (`reachability/service.py`), variant preparation 256 and option keys 1,024
  (`constraints/variants.py`).

Open measurement: solve cadence 1 versus 2 or 4 decision levels
([anchor policy O4](design/ANCHOR_POLICY_DECISIONS.md)).

## Owners to inspect

| File | What to look at without changing behavior |
| --- | --- |
| `search/identity.py` | `digest`, `motion_record`, `solve_scope`, `solve_identity`: repeated canonical serialization. |
| `movement/program.py` | `program_physics_key`, `program_search_key`, `compile_program`: repeated sorting and serialization of immutable content. |
| `reachability/service.py` | `InteractionGate` key construction, which costs even on a cache hit. |
| `search/engine.py` | `evaluate_program`: scope, coefficients, replay, solve identity, variants, validation. |
| `constraints/variants.py` | Repeated canonical row sets and cursor records. |
| `search/repair.py`, `search/repair_state.py` | `snapshot`, `publish`, `save_branch`: repeated copying of unchanged results. |
| `spatial/checkpoints.py` | `retain_prefix` copies; sharing must not let siblings mutate each other. |
| `io/search_checkpoint.py`, `io/checkpoint_store.py` | Root archival, repair patches, content addressing. |

## Cache and identity invariants

1. Canonical identity outputs stay identical for unchanged semantics: no rounding,
   coordinate normalization, dropped ownership fields or constant-ID shortcuts. A
   pure cache or copy optimization needs no physical or search policy bump.
2. Physical keys exclude descriptive metadata; search keys keep constraint owners,
   committed horizon, interactions, scope and sometimes repair state.
   `dfs.candidate_key` excludes repair state. Keep the contracts distinct.
3. Solve identity covers initial state, modeled geometry, input policy, program,
   objective, validation scope and coefficients, plus the caller's solve intent,
   node query and assembly context. Different `stopOnFeasible`, oracle behavior or
   validation horizon never share an exact repair result.
4. Reachability proofs are scoped to role, windows, phases, rows, contact context
   and named-probe uncertainty. A conditioned row set cannot poison the
   unconditioned analysis.
5. Never cache mutable objects by `id()` across mutations or saves. A timeout during
   construction commits no partial result and consumes no raw ordinal.
6. Caller mutations must not alter cached canonical values. Performance caches are
   disposable: rebuild them on external resume and never serialize them per node.
7. Ordering and deferral verdicts are not physical facts; incumbent, latch and peer
   revisions change independently of the model.

## Snapshot, resume and ownership invariants

- A published snapshot keeps its value at publication; returning the live repair
  dictionary or a shallow copy is not sufficient.
- Live aliases of the active program and its node entry receive the same repair
  publication; external decode yields independent mutable copies.
- Queue order, cursors, in-flight work, pending and unknown work, accepted
  variants, logs and numerical accounting are retained. Never improve timing by
  deleting evidence or work.
- Completed exact requests never rerun after resume; interrupted work stays
  recoverable; timeout, error and unknown are not infeasibility.
- Durable repair updates patch the frozen dispatch root, every active alias and
  cumulative calls. Store contents commit before root publication; manifest and
  SQLite companions stay paired.
- Stable ordinals and ownership survive JSON serialization.

## Related reading

- [Branch scheduling](design/BRANCH_SCHEDULING_INVESTIGATION.md): where earlier T64
  discovery is lost to DFS order, and the overhead and risk list for a scheduler.
- [Anchor execution reference](ANCHOR_SEARCH_EXECUTION_REFERENCE.md): which work
  reaches IPOPT and where pruning happens.
- [Pruning research](research/PRUNING_RESEARCH.md): root causes of earlier
  starvation and the anatomy of numerical requests.
- [Stratfinder investigation](research/STRATFINDER_INVESTIGATION.md): coefficient
  caching and filters that pruned nothing.
