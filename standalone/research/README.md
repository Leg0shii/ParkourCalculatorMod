# Research and preserved evidence

This directory is outside the installed solver. Current production code is in
[`src/parkour_solver`](../src/parkour_solver); use the [runbook](../docs/RUNBOOK.md)
for active commands and the [decisions index](../docs/DECISIONS_AND_PLANS.md) for
which proposals are implemented or deferred.

`route_guidance/` is the disk/SOCP scientific prototype, with current imports and
optional research dependencies. `legacy_cell_routes/` contains maintained
comparison/diagnostic drivers removed from production. These can use production
APIs but production cannot import them.

All other existing experiment directories are **frozen bundles** relocated from
`autonomy/experiments/`. Their Python, readmes, data, reports, diagrams, manifests,
SQLite companions and instrumentation snapshots remain byte-identical. An old
README command or source path refers to its recorded source era, not the current
installed package. Preserve that evidence; do not edit it to fix present imports.
For exact historic reproduction, create a separate checkout of its recorded
revision and use its recorded patch/configuration. Current controls belong in
`tools/` or a new experiment directory, with a new output path.

| Current tool | Purpose |
| --- | --- |
| `python -m tools.geometry_bounds` | Static player collision/landing-cover report; `--faces` adds the production catalog of merged exposed physical faces across all six orientations. |
| `python -m tools.profiling.run` | Cold current task run, scoped bookkeeping timings, source/config hashes and retained-witness replays. |
| [Bookkeeping setup and measurements](bookkeeping-measurement/README.md) | Isolated baseline, sampled digest/preparation/copy measurements, hook calibration and durable resume; no production optimization. |
| [Enumeration attribution](enumeration-attribution/README.md) | Exact block/side-chain/exit counters and fixed-work option replay identify duplicate internal states around B7; production search unchanged. |
| [Enumeration reduction](enumeration-reduction/README.md) | Implemented early suppression of already-visited children, canonical key memoization, ordered-catalogue equivalence, timing and legacy-resume evidence. |
| [Continuous anchor run budget](anchor-run-budget/README.md) | Removes anchor service-turn/per-program quotas, preserves unstarted repair work and measures native, cold and continuation behavior. |
| [JVM-free bridge](jvm-free-bridge/README.md) | Python bridge parity with Java: fixed-work and 300 s Real11 runs, cross-bridge resume, installed wheel without Java. |
| [Early passage screening](early-passage-screen/README.md) | Checks accumulated obstacle constraints before downstream generation while retaining coupled numerical solves. |
| `python tools/native_gate.py` | Explicit recorded native acceptance scope, plus packaging/profiler regressions. |
| `python -m tools.audit_candidates` | Offline saved cold-candidate/worker ledger, optional independent Java replay. |
| `python -m tools.witness_bound_audit` | Current optimistic bound checks over every prefix of supplied archived valid finishes. |
| `tools.plotting.cells`, `.regions`, `.turns` | Optional current decomposition/region/turn render helpers. |

[Relocation catalog](../tools/layout-migration.json) records all old/new paths and
protected hashes. `python -m tools.validate_layout` verifies the902 protected
files. Keep report manifests and companion SQLite databases together and decode
with `parkour_solver.io.checkpoint_store.read_report`.

[Real11 extension](real11-extension/README.md) is new geometry evidence, separate
from the frozen relocated bundles: unit B6/B7, supplied continuation and exact
player-bounds output. The later solver trials are recorded separately below.

[Longer Real11 fixed-prefix trials](real11-long-solve/README.md) supply B7/B20
additional anchors and terminal B22, retain initial-0 decisions through B5@55,
and keep original-state facings free. They validate B7@64, fix an exhausted-empty
node retry loop and persist the unfinished B11@68 constraint search for resumption.

For a new experiment use a named topic, a README describing the question,
`driver.py`, `configs/`, and new `runs/<run-id>/` directories. Local bulky outputs
can go under ignored `../runs/`; commit only deliberate evidence. Include source
revision/hashes, settings, validation scope and checksums with measurements.
