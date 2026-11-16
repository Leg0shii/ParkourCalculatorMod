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
| `python -m tools.geometry_bounds` | Static player collision/landing-cover report using production scene decomposition, including neighboring solids. |
| `python -m tools.profiling.run` | Cold current task run, scoped bookkeeping timings, source/config hashes and retained-witness replays. |
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
player-bounds output. It has not undergone a longer-course solve.

For a new experiment use a named topic, a README describing the question,
`driver.py`, `configs/`, and new `runs/<run-id>/` directories. Local bulky outputs
can go under ignored `../runs/`; commit only deliberate evidence. Include source
revision/hashes, settings, validation scope and checksums with measurements.
