# Bookkeeping setup and measurement

2026-11-15. This implements only setup and measurement from the
[controlling handoff](../../docs/BOOKKEEPING_PERFORMANCE_AGENT_HANDOFF.md).
Production Python, search policies, physics, canonical identities and checkpoint
schemas remain unchanged. Fixed-work continuation benchmarks and production
optimizations are still subsequent work.

## Isolation and reproducibility

- Branch `research/bookkeeping-performance`, base `0f6b873c` (documentation on
  production `a11b9b18`); worktree `/private/tmp/pkc-bookkeeping-performance`.
- Own `.venv-bookkeeping`, Python 3.12, editable installation of this worktree;
  pinned numerical/reachability dependencies plus the research extra required
  by seven tests in the recorded native gate.
- Own Java helpers rebuilt through the runbook, own `build/native/compiler.json`
  and class outputs. The original environment/build products were not retargeted.
- Original Real11 fixture SHA256:
  `0c134fd3ebf4625689a3947792253356228070a938db37efb5bc84c2f3f10dc1`.
  All 131 production Python files matched the starting checkout byte for byte.
- `OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1`; each control owns a fresh output
  directory and report/store pair. No archived witness enters cold search.

Raw outputs are under this worktree's ignored `standalone/runs/bookkeeping-*`.
Curated evidence and raw-output hashes are recorded in the
[evidence index](evidence/2026-11-15/index.json). Large resumable manifest/SQLite
pairs stay together in their recorded local run directories; they are not seeds
for cold search. Each run retains its exact instrumentation source snapshot and
hashes, including small tool changes between runs.

## Measurement ownership

[Bookkeeping hooks](../../tools/profiling/bookkeeping.py) count digest callers,
sample canonical byte sizes/repeated hashes, time preparation functions and
cache lookups, and measure top-level copies without replacing stdlib recursion.
Samples and tracked cache keys are bounded; raw mutable program/model objects
are not retained as profiler keys. Nothing is serialized into semantic search
state. Hooks restore imported aliases on exit, including exceptional exits.

[The cold harness](../../tools/profiling/run.py) keeps its existing `legacy`
default and adds `bookkeeping` and `none` modes. `none` disables production
function/copy hooks; the same outer progress/GC/report harness remains.
`--durable` uses the production `SearchCheckpoint` callback protocol and gives
transition/dispatch/repair publication separate buckets. `--resume` reads the
paired report using the production decoder.

[Hash calibration](../../tools/profiling/calibrate.py) rotates unwrapped,
sampled and legacy hooks over identical captured payloads/iteration counts.
It records five repetitions and separate traced digest allocation peaks.
This is a hook calibration, not the subsequent fixed-work search benchmark.

## Cold and hash measurements

The fresh legacy 300-second control finds T64 / Z=-1093.2381026602416, with
61 numerical invocations (55 workers, six feasible seed returns), 39 expansions
and 57/57 independent retained-witness replays. It records 30,278,333 digest
calls / 65.376 self seconds and 34.572 seconds copying inside `retain_prefix`.

The sampled 300-second control finds the same T64/Z, calls/workers and expansion
count, with 56/56 retained-witness replays. Exact digest count: 33,560,133.
These wall-budget controls process different amounts of inner work; their
counts/timings are not a fixed-work speedup comparison.

| 300-second mode | T / Z | Invocations / workers | Witness replays | Total wall / parent CPU seconds |
| --- | --- | ---: | ---: | ---: |
| Legacy hooks | 64 / -1093.2381026602416 | 61 / 55 | 57/57 | 306.445 / 291.746 |
| Sampled hooks | 64 / -1093.2381026602416 | 61 / 55 | 56/56 | 306.614 / 291.444 |
| Unwrapped | 64 / -1093.2381026602416 | 55 / 49 | 50/50 | 306.078 / 292.217 |

All end at their deadline, retain 39 expansions and report unchanged production
source. All reported retained witnesses replay. Total wall time includes final
replay/report writes; the declared search budget is 300 seconds. Different inner
work and numerical request counts are precisely why the subsequent fixed-work
benchmark is necessary. No optimality or production-speedup claim follows.

| Sampled digest caller | Calls | Sample information |
| --- | ---: | --- |
| `ConstraintVariants.effective_key`, line 226 | 16,766,863 | 4,106 samples, 17 distinct hashes; 3,626 samples (88.3%) hash `[]`; maximum 146 canonical bytes |
| `ConstraintVariants.option`, line 265 | 16,599,412 | 4,065 samples, 3,694 distinct hashes; maximum 80 canonical bytes |
| `ConstraintVariants.option`, line 293 | 165,526 | 53 samples, 20 distinct hashes |

The first two callers account for 99.4% of digests. This is predominantly small
row/node key work, rather than repeated serialization of whole model contexts.
Do not replace these identities with constant IDs or merge their contracts.

Preparation makes 462 requests: 230 hits / 232 misses; coefficient preparation
254 hits / 208 misses; analysis 230 hits / 232 misses. Tracking was not truncated;
the existing shared cache reaches its 512-entry bound. `prepare` totals 0.529 s,
`analyze_model` 0.841 s and all program-physics-key calls 0.970 s. These inclusive
measurements overlap and include hook effects. Whole-model key caching alone
does not explain the tens of millions of digests.

The principal sampled copy sites are `retain_prefix` (37.450 s, largest sampled
JSON payload 4,694,849 bytes), repair constraint-generation snapshots (18.345 s),
accepted-variant snapshots (14.951 s) and attempt-log snapshots (4.120 s).
`retain_prefix` copies the program before dropping `repairState`; snapshots
recopy growing retained state. These are investigation targets, not a claim
that the entire measured cost can be removed safely.

On one captured empty-list payload, median per-call hash times are 1.469 us
unwrapped, 1.862 us sampled and 2.999 us legacy. On a 77-byte node-key payload:
1.915 / 2.339 / 3.490 us. Values are for identical inputs and five repetitions;
they do not describe every payload at a caller. Hook overhead is substantial
at millions of calls and must remain separate from recoverable production cost.

## Durable checkpoints and external resume

A separate 60-second durable control finds T67, with 12 invocations / nine
workers and 10/10 witness replays. A new-process 30-second resume reports
`resume.exact=true`, keeps the same valid incumbent, makes two invocations
(one worker, one feasible seed return) and passes 12/12 witness replays.
Neither resumed `(problem fingerprint, stopOnFeasible)` pair repeats a prior
feasible pair. This is observed evidence at that request boundary; the generic
completed-request/interruption and snapshot ownership contracts also require
the existing continuation/persistence tests.

| Durable callback | Initial calls / inclusive seconds | Resume calls / inclusive seconds |
| --- | ---: | ---: |
| Full transition checkpoint | 77 / 34.091 | 17 / 22.369 |
| Dispatch root archival | 18 / 8.979 | 4 / 6.632 |
| Repair patch | 85 / 5.142 | 12 / 0.198 |
| Final publication/compaction | 1 / 0.781 | 1 / 1.474 |

These callbacks use the existing production adapter/store unchanged. The
measurements include CPU serialization/content addressing/archive work and
publication/transactions; they are not pure disk IO. Callbacks are distinct
from the progress-summary callback of cold runs. Parent numerical/search buckets
include these callbacks and overlap them. The shorter durable controls are
resume/publication probes, not the 300-second cold T64 acceptance control.

## Allocation probes

A separate 60-second traced-copy control reaches the same T64/Z with 26
invocations / 20 workers and 23/23 witness replays. Its extra payload/allocation
diagnostics cost 0.298 seconds; that run is not part of a speedup comparison.
A 15-second diagnostic resume from the sampled 300-second checkpoint captures
larger retained objects immediately. It reports exact resume, launches no
numerical requests and replays all 56 retained witnesses successfully.

| First large copy in diagnostic resume | JSON bytes | Traced retained bytes | Traced peak bytes |
| --- | ---: | ---: | ---: |
| Program in `retain_prefix` | 5,446,799 | 6,835,744 | 9,495,408 |
| Accepted variants in repair snapshot | 1,428,491 | 2,797,240 | 4,044,792 |
| Constraint-generation snapshot | 3,234,073 | 2,159,808 | 2,782,336 |

These are individual repeated-copy samples, not maximum process memory or
total allocation traffic. Tracing includes deepcopy's temporary memo while
retained bytes keep the cloned value alive. Canonical transport bytes and heap
bytes differ substantially. The profiler retains bounded hashes/rows and one
bounded encoded digest example per caller, rather than live mutable model
objects; it does not introduce any production cache or persisted cache state.

## Validation and remaining work

The final recorded native gate passes **617 tests**, with no failures, errors or
skips: the original 610-test scope plus seven profiler tests. Dedicated
ladder/climbable/vine/movement-entry suites remain excluded, as in the baseline;
generic mixed-ladder controls and all numerical modules remain included.
The gate includes exact repair/orchestrator continuation, ownership/JSON
restoration, checkpoint-store and package-boundary checks. The new profiler
checks cover canonical output/model equality, bounded samples, cache hit/miss
and failed-build behavior, alias/cycle-preserving copies, allocation memo
isolation, callback protocol forwarding and hook restoration after exceptions
or late imports. Layout validation passes all 902 protected artifacts and all
131 runtime modules. Both archived bookkeeping baseline profiles still match
their recorded SHA256 hashes. No production Python file changed.

The initial gate recorded 610 tests with one time-bounded checkpoint solver test
returning `unknown` and seven skips because Clarabel was absent. An overlapping
31-second cold attempt was interrupted and excluded from timing comparisons.
After installing the pinned research extra, the isolated final gate passes
without changing test budgets, solver equations or production tolerances.
The initial report is preserved alongside the successful one rather than hidden.

Next is handoff step 3: fixed-work continuation/oracle and snapshot controls.
Use the measured row/node-key and retained-state-copy hotspots to choose a
behavior-preserving implementation only after those controls exist. Durable
transition archival deserves its own fixed-work control. Whole-model preparation
alone is a comparatively small measured cost here. Scheduling/traversal and
terminal-solve policy remain outside this branch's ownership.

## Reproduction

From this worktree's `standalone/`, set the runbook environment to its own paths:

```sh
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input cases/real11/physical-anchors.json --output /new/path/legacy \
  --seconds 300
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input cases/real11/physical-anchors.json --output /new/path/sampled \
  --seconds 300 --profile-mode bookkeeping --sample-every 4096 --sample-limit 16384
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input cases/real11/physical-anchors.json --output /new/path/unwrapped \
  --seconds 300 --profile-mode none
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.calibrate \
  --examples /new/path/sampled/digest-examples.json --output /new/path/hash-overhead.json
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input cases/real11/physical-anchors.json --output /new/path/durable \
  --seconds 60 --profile-mode bookkeeping --durable
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input cases/real11/physical-anchors.json --output /new/path/resumed \
  --seconds 30 --profile-mode bookkeeping --durable --resume /new/path/durable/report.json.gz
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input cases/real11/physical-anchors.json --output /new/path/allocations \
  --seconds 60 --profile-mode bookkeeping --allocation-samples 1
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input cases/real11/physical-anchors.json --output /new/path/large-copy-resume \
  --seconds 15 --profile-mode bookkeeping --allocation-samples 1 \
  --resume /new/path/sampled/report.json.gz
```

Allocation probes repeat selected large/mature copies separately with
`tracemalloc`; body-copy timings exclude those extra copies. JSON byte size is
a transport-size diagnostic, not heap size. Use a separate run with
`--allocation-samples 1`; never mix its wall-budget results into an ordinary
cold performance comparison. Inclusive buckets overlap, RSS includes postrun
replay/archive work, and systematic samples are not an unbiased cardinality
estimate. A wall deadline can be exceeded by in-progress bounded work/cleanup.
