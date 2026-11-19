# Geometry-driven avoidance passages

2026-11-16. Implements the [audited avoidance plan](../../docs/design/AVOIDANCE_PASSAGES.md)
on `perf/early-passage-screen`, implementation commit `e266785c`. The evidence
was measured on its uncommitted working tree over parent `e2b5e02c`; all 132
runtime source hashes recorded in the [native report](2026-11-16/native-report.json)
and the [cold provenance](2026-11-16/cold-provenance.json) match `e266785c`.

## Behavior

`constraints.encounters` builds phase covers for one box encounter within a
selected landing segment. `ConstraintVariants` conditions later domains on the
actual earlier rows. Upfront generation and collision repair use one builder.
Ghost face histories, switch-count budgets and the local avoidance/repair attempt
and retention limits are removed. Global run/call budgets preserve lazy
unfinished work; per-request numerical timeouts remain.

New identities are constraint policy8/state3 and repair assembly10, so old
generator/DFS cursors require their original source checkout. Source-era cap
fields stay readable and are removed by normalization. The maintained Real11
input drops `maxContactBranches` and `maxPassageCandidates`; that is its only
change between the two cold runs below.

## Validation

The selected native gate passes **640 tests** with zero failures, errors or
skips and unchanged source during the run ([report](2026-11-16/native-report.json),
[log](2026-11-16/native-tests.log)).

Fixed complete B6 catalogues, under the same recorded movement/parent
requirements ([report](2026-11-16/fixed-report.json)):

| History | Old descriptions | Old retained | New retained alternatives | Old retained covered | Seconds |
| --- | ---: | ---: | ---: | ---: | ---: |
| A | 277 | 40 | 7 | 40/40 | 0.444 |
| B | 153 | 36 | 6 | 36/36 | 0.320 |
| C | 18 | 8 | 1 | 8/8 | 0.026 |
| D | 34 | 13 | 1 | 13/13 | 0.028 |

Coverage compares entailed requirements, not cursor or order equality. Retained
means the pre-checks did not exclude an alternative; it is no NLP feasibility or
optimality claim.

Cold 300-second Real11 controls, `--profile-mode bookkeeping`:

| Run | Best T | Best Z | First full solution | Search ended | Termination | Requests / workers | Expanded prefixes | Retained-witness replays |
| --- | ---: | ---: | ---: | ---: | --- | ---: | ---: | ---: |
| [Parent `e2b5e02c`](2026-11-16/baseline-summary.json) | 67 | -1093.823129063534 | 201.6 s | 303.3 s | deadline | 59 / 57 | 21 | 50/50 |
| [Geometry passages](2026-11-16/cold-summary.json) | 64 | -1093.2381026602418 | 14.1 s | 25.1 s | numerical_unknown | 53 / 49 | 37 | 47/47 |

The new run ends well before its deadline. `numerical_unknown` means the DFS
stack emptied while a suspended frame remained (`search/dfs.py`). Five of its 53
requests returned `unknown` with IPOPT status `Infeasible_Problem_Detected`
([request ledger](2026-11-16/cold-requests.json)); the
[stop report](2026-11-16/cold-stop.json) links each to its route. Two closed
B3 and B5 routes as oracle-rejected under the anchor policy, one was a rejected
alternative on a feasible B5 route, and two were rejected alternatives on a
feasible B7 route. Separately, one B7 alternative (pass B6 on its +X side, then
+Z, then back to X ≤ 729.7) was blocked by the linear control relaxation
before any IPOPT solve and stayed unresolved, which suspends the B7 frame and
gives the termination label. Explicit unknown retries are the only remaining
work. The run therefore does not establish exhaustive coverage or optimality.

## Reproduction

From this checkout's `standalone/`, set `PKC_IPOPT_PYTHON` to
`../.venv-guidance/bin/python` and `PKC_RUN_TICKS_COMPILER` to
`build/native/compiler.json`; set `OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1`.
Use fresh output directories:

```sh
"$PKC_IPOPT_PYTHON" -B tools/native_gate.py --output /new/path/native
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input cases/real11/physical-anchors.json --output /new/path/cold \
  --seconds 300 --profile-mode bookkeeping
PYTHONPATH=. "$PKC_IPOPT_PYTHON" -B research/geometry-passages/benchmark.py \
  --compiler "$PKC_RUN_TICKS_COMPILER" --output /new/path/fixed
```

Large manifest/SQLite pairs remain in the ignored run directories. Timed coverage
is a scheduling observation, not an optimality certificate or isolated speedup claim.
