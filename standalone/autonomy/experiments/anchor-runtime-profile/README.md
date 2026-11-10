# Coordinated anchor/solver integration and runtime profiling

Current implementation: [integration contract](../../../docs/ANCHOR_SEARCH_INTEGRATION.md).
This harness measures a cold search without injecting archived programs or
facings. Existing task/limits remain unchanged. Its hooks are scoped and restored
on exit; copied instrumentation source and runtime hashes identify each run.

The implementation combines numerical row reduction and corrected bound
extraction, nested shared persistence, directional feasible stopping, explicit
extra-run recovery, strict elapsed/remaining-time bounds and live DFS reuse.
Unresolved-only internal nodes are retained for explicit retry while other DFS
candidates continue; they do not consume repeated empty service slices.

## Reproduce

From the repository root, with the existing native environment:

```sh
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 PYTHONPATH="$PWD" \
PKC_IPOPT_PYTHON="$PWD/.venv-guidance/bin/python" \
PKC_RUN_TICKS_COMPILER="$PWD/standalone/autonomy/build/compiler.json" \
.venv-guidance/bin/python -B \
  standalone/autonomy/experiments/anchor-runtime-profile/profile_run.py \
  --input standalone/autonomy/experiments/anchor-feasibility/real11-anchors.json \
  --output /path/to/new-output --seconds 300
```

Use a new output directory. `summary.json` records the achieved T/Z, work,
restorations and independent accepted-witness replay results. `profile.json`
contains per-site wall time and per-slice work; `report.json.gz` retains the full
result as a manifest with a companion SQLite store; keep both files together
and read them with `standalone.autonomy.checkpoint_store.read_report`.
`progress.json` is a small live diagnostic, not an exact checkpoint.
The automatic CLI supplies exact shared-store persistence separately.

Inclusive times overlap. Exclusive times subtract instrumented children;
unwrapped work stays in its parent. GC overlaps the stage totals and must not
be added again. Solver invocation includes worker handling and seed returns,
while numerical evaluation additionally includes model/repair bookkeeping.
Profiler overhead can affect which work fits a time slice; comparisons describe
observed work rather than guaranteeing a global search speedup or optimality.

## Validation status

The [final native gate](integrated-2026-11-10/native-final/report.json) passes
571 tests with zero failures/errors/skips in248.27s. All105 runtime hashes match
the cold run and remain unchanged. Historical event artifacts remain unchanged.
[Five profiler tests](integrated-2026-11-10/profile-tests.log) additionally pass.

The [combined cold run](integrated-2026-11-10/combined-final-300s/summary.json)
uses the original initial0/8 domain and300-second budget. Best finish remains
**T67/Z−1093.823129063534**, physically grounded on B7 at Y7. First finish is at
15.17s; actual search wall300.06s. All71 accepted-witness independent scene
replays pass. It expands1057 prefixes and launches149 workers, versus252/45 in
the [earlier source-specific combined run](../anchor-transfer-preparation/combined-300s-2026-11-04/summary.json).
These are different implementations, not an isolated single-change benchmark.

The [profile](integrated-2026-11-10/combined-final-300s/profile.json) records zero
whole-DFS internal resume copies and zero empty service slices across40 slices.
There are296 saved extra-run deferrals, zero restorations and five retained
unresolved nodes. The [queue/pruning audit](integrated-2026-11-10/findings.json)
records3975 elapsed-time and3101 optimistic remaining-time exclusions in movement
generation. Initial0's source setup remains queued and eligible; the final stack
is still in initial8's B2→B3 span. No combined T64 or earliest-finish proof is claimed.

The largest remaining measured work is generator advancement, choice generation,
model/repair handling and smaller program copies. Shared final-report encoding
takes36.84s **after** the search budget; it is measured separately. Keep its
manifest and companion database together. The full process wall is339.01s.

Earlier outputs in the same directory are preserved diagnostics: `native/`
loaded two archived fixtures from the wrong working directory; the final runner
sets the repository root and passes those checks. `combined-300s/` was deliberately
cancelled after exposing repeated unresolved-only node retries, and is not a
completed300s measurement. The fix retains those unknowns rather than rejecting
them. Earlier temporary results lost during the environment reset are not
presented as a completed gate. No archived facing seeds, new input caps or
game/executable-angle replay were introduced.
