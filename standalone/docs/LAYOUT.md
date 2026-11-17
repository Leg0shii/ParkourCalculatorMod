# Standalone ownership and package layout

The reviewed folder refactor is implemented. This changes ownership and packaging,
while preserving solver policy, physical equations and saved request/checkpoint
schemas. [Implementation and validation](STANDALONE_REFACTOR_IMPLEMENTATION.md)
and the [original inventory/review](STANDALONE_STRUCTURE_REVIEW.md) record its scope.

| Root | Contents and dependency direction |
| --- | --- |
| `src/parkour_solver/` | Installed production code. Cannot import research, tools, tests or old checkout namespaces. |
| `tests/unit/`, `tests/integration/`, `tests/research/` | Domain checks, native/package boundaries and deliberate research/archive regressions. |
| `tests/support/`, `tests/fixtures/` | Shared builders/harnesses and small reusable test data. No test imports another test case. |
| `cases/` | Curated runnable user tasks. Real11 physical anchors are a byte-identical promoted input with provenance in its README. |
| `tools/` | Current native gate, scoped profiling, saved-report audits and optional plotting. Tools may import production APIs. |
| `native/java/`, `native/gradle/` | Four Java bridge sources and their standalone build/export/replay adapters. They reuse existing mod physics. |
| `research/` | Scientific prototype, maintained older experiment drivers and immutable dated evidence bundles. Excluded from the installed package. |
| `docs/design/` | Detailed implemented/deferred designs; use the decisions index for authority. |
| `docs/history/`, `docs/research/`, `docs/reference/` | Source-era records, literature investigations and detailed reusable contracts. |
| `build/native/` | Ignored compiled helpers and machine-specific compiler configuration. Regenerate when needed. |
| `runs/` | Ignored new local runs; prior ignored output was preserved under legacy subdirectories. |

Production groups follow responsibilities: `schema`, `physics`, `scene`,
`movement` (including ladders), `spatial`, `search`, `constraints`, `reachability`,
`numerics`, `validation`, `io`, `bridge`, `observability` and `cli`. Low-level
schema/physics/scene primitives do not import search. Rendering lives in
`tools/plotting`; compact turn records remain in production. Numerical replay,
vertical checks and full scene validation have explicit owners. Shared helpers
have one implementation; a few source-module re-exports preserve internal patch
and caller contracts during this change. [Code map](CODE_MAP.md) gives each file.

## Supported public entrypoints

```python
from parkour_solver.api import solve_task, find_checkpoint_route, solve, replay_witness

# solve_task(task_fixture, compiler, ...) returns (result, independent_validation).
# solve(fixed_problem, ...) performs fixed-program continuous optimization.
```

Console commands are `parkour-solve`, `parkour-blocks`, `parkour-route`,
`parkour-ipopt` and `parkour-fixed-clock`. `python -m parkour_solver` is the
physical automatic task CLI. See the [runbook](RUNBOOK.md) for native dependencies,
input files and resume. Neither package imports nor worker launch depend on the
checkout working directory. Runtime takes task/compiler paths explicitly.

The old **public** autonomy/ipopt facades remain for `standalone.autonomy` and
`standalone.ipopt`, their solve/solve_blocks CLIs, the cell_routes public facade,
and automatic_experiment/fixed_clock_experiment CLIs. They require an installed
`parkour_solver`; the `standalone` namespace itself requires the repository parent
on the import path. Old private module paths and direct execution of moved
implementation files are not supported. Maintained tests/tools use current names.
Use module or console entrypoints, rather than executing `src/.../*.py` by filename.

## Evidence, relocation and reproduction

[tools/layout-migration.json](../tools/layout-migration.json) maps original paths
to destinations and stores original SHA256 values. Its902 protected files include
whole old experiment bundles and the61 original reusable fixtures. Their bytes,
embedded paths, saved source hashes and SQLite/report companions are unchanged.
The [extraction catalog](../tools/layout-extractions.json) records later symbol
owners, and [test support catalog](../tools/layout-test-support.json) records
shared builders. Source provenance recursively hashes installed production files;
research scripts, tests and plots no longer appear as runtime dependencies.

Frozen scripts/readmes describe their recorded revision. Reproduce them in a
separate checkout at that revision; their relative/source paths are historical.
The current [research guide](../research/README.md) and runbook provide maintained
replacements. A report manifest and its `.store.sqlite3` companion must move
together and be read with `parkour_solver.io.checkpoint_store.read_report`.
Do not rewrite old provenance to resemble the current checkout.

Regenerate navigation with `python -m tools.docs_index` from `standalone/`.
`python -m tools.validate_layout` checks production import boundaries and protected
file hashes. The native gate preserves all600 baseline tests and adds10 package/
profiler regressions; it does not silently include or retire excluded capability
suites. No physical/decision policy version was bumped for directory moves.
