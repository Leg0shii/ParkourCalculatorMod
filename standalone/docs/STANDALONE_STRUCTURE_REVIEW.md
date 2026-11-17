# Standalone layout inventory and proposed structure

**Approved source-era review; implementation now complete.** See the [implementation ledger](STANDALONE_REFACTOR_IMPLEMENTATION.md) and [current layout](LAYOUT.md). The inventory and proposal below describe the pre-refactor tree. Inspected checkout HEAD
`7def2875`, runtime feature `dd738c98`, including the existing uncommitted
`AGENTS.md` and `docs/CODE_MAP.md` edits. This review inventories the filesystem,
reads all tracked Python modules with AST, and manually assigns the118 modules
in library areas. It does not infer production status from the filename alone.

The recommendation is one installable solver package under `src/`, with tests,
research, reusable tools, task cases, native bridge sources, docs and generated
outputs alongside it. Separate responsibilities first; reorganize imports second.
There is no reason to change solver algorithms, pruning, schemas or dependencies'
versions as part of a layout cleanup.

## Complete inventory and what the counts mean

The [audit bundle](../research/structure-audit/README.md) contains:

| Inventory | Coverage |
| --- | --- |
| [files.csv](../research/structure-audit/snapshot-7def2875-final/files.csv) | Every2,809 physical file: current path, tracked/ignored status, bytes, type, category and proposed placement. Includes dotfiles, local runs, classes and caches. |
| [modules.csv](../research/structure-audit/snapshot-7def2875-final/modules.csv) | Every275 tracked Python file: role, destination(s), size in lines, importers, top-level definitions and static reachability flags. Multiple destinations mean extraction is required. |
| [imports.csv](../research/structure-audit/snapshot-7def2875-final/imports.csv) | Resolved local Python import edges, symbols and source lines, including function-local/conditional imports. |
| [directories.csv](../research/structure-audit/snapshot-7def2875-final/directories.csv) | Direct-child file/byte/tracked counts for every populated directory. |
| [summary.json](../research/structure-audit/snapshot-7def2875-final/summary.json) | Exact totals, reviewed categories, boundary violations, path/test coupling, large artifacts and methodology limits. |

The snapshot excludes this newly written review and its own audit directory to
avoid self-inventory. Bundle-data categories include historical configs/inputs
as well as results; not every JSON file is a solver output. These categories
are placement guidance, not a content audit of every saved trajectory. Bytes are logical file sizes, not compressed Git object
size or allocated filesystem blocks. No large report was expanded to count it.

| Current group | Tracked files | Physical files | Significance |
| --- | ---: | ---: | --- |
| Entire `standalone/` |1,199 |2,809 |670.44 MiB tracked;861.67 MiB on disk. |
| `autonomy/experiments/` |834 |876 |666.36 MiB tracked. Drivers, fixtures, reports, logs, diagrams, source snapshots and data are interleaved. |
| Direct `autonomy/*.py` |52 |52 source files |Broad mix of model, generation, geometry, constraints, search, validation, persistence and CLI. |
| `autonomy/cell_routes/` |48 |75 |47 Python modules+README; current search, spatial code, diagnostics and experiment drivers mixed together. |
| `autonomy/route_guidance/` |13 |23 |10 Python modules, dependency lists and README for a separate research prototype. |
| `autonomy/tests/` + `ipopt/tests/` |79 |141 |Tests plus local bytecode. Fixture builders are also imported from other tests. |
| `autonomy/fixtures/` + `ipopt/fixtures/` |61 |61 |Small reusable fixtures,139,544 bytes total. More regression data currently lives under experiments. |
| `autonomy/build/` |0 |1,409 |185.60 MiB. Mostly local experiment outputs, not just compiled helpers. |
| `docs/` |24 |24 |Current guides/indices; other documentation is scattered under source and experiment folders. |

The275 tracked Python files divide as follows; the rows are disjoint:

| Role | Python files | Recommended home |
| --- | ---: | --- |
| Runtime-bearing library/API/CLI/compatibility modules, including mixed modules needing extraction |96 |`src/parkour_solver/` for the runtime portions. |
| Research/diagnostic modules sitting in library areas |22 |12 cell-route drivers/diagnostics and10 route-guidance prototype modules belong outside the shipped package. |
| Test modules |79 |`tests/`, grouped by responsibility. |
| Experiment drivers/scripts already under experiments |57 |`research/<topic>/`; promote only genuinely reusable developer tools to `tools/`. |
| Frozen instrumentation source copies |21 |Keep inside their evidence bundles, byte-for-byte. They are records, not maintained runtime modules. |
| Total |275 | |

Other tracked content includes161 Markdown documents, four production Java bridge
sources plus two Gradle adapters, three experimental Java probes and one C++ probe.
The rest is fixtures/evidence/metadata/dependency lists. Runtime-bearing Python is
only921,299 bytes before extraction. Most repository bulk is evidence, not the
solver implementation.

Locally there are186 cache files,1,397 run/scratch files, six native-build files
and21 other untracked research results. Do not delete useful local runs merely
because they are ignored. `build/` currently contains1,070 JSON files but only
five `.class` files, which is a particularly misleading directory name.

## Where the current boundaries are wrong

| Current location | What it actually contains | What to extract / keep |
| --- | --- | --- |
| [`checkpoint_experiment.py`](../research/legacy_cell_routes/checkpoint_experiment.py) | Production `replay_witness`, report summary helper, benchmark arms and CLI. DFS, event search and corridor resume import its witness validator. | Move validator to `validation/witness.py`; put reporting outside the solver kernel; keep experiment arms in research. Moving the whole file to research would break live solves/resume. |
| [`window_experiment.py`](../research/legacy_cell_routes/window_experiment.py) | Shared atomic JSON/gzip `save` plus decision-window experiment runner. The normal automatic runner imports `save` here. | `io/reports.py` owns the writer; experiment driver imports it. Runtime must not import the driver. |
| [`automatic_experiment.py`](../src/parkour_solver/cli/solve.py) | The current generic physical task solve/resume runner, CLI, source hashing, persistence and final replay. | Promote to production API/CLI/provenance modules. The word “experiment” is misleading here. |
| [`fixed_clock_experiment.py`](../src/parkour_solver/cli/fixed_clock.py), [`two_goal_experiment.py`](../research/legacy_cell_routes/two_goal_experiment.py) | A maintained compatibility runner imports generic witness enumeration from an experimental comparison driver. | Extract witness iteration to validation/reporting; keep the fixed/virtual compatibility CLI separate from comparison experiments. |
| [`local.py`](../src/parkour_solver/validation/scoped_scene.py) | Search work budgets, counting compiler and scene/corridor adapter. | Split into `search/work.py`, `bridge/counting.py` and validation adapters. “local” says nothing about ownership. |
| [`block_contacts.py`](../src/parkour_solver/search/repair.py) | Vertical validation, floor requirements, fixed-variant repair search and numerical dispatch. | Separate validation/constraint construction from repair orchestration. Retain a single implementation of each rule. |
| [`block_search.py`](../src/parkour_solver/search/support_profiles.py) | Scene normalization, older support-profile search and generic movement dispatch. | Scene parsing belongs to `scene/`; strategy/orchestration belongs to `search/`/API. |
| [`ipopt/model.py`](../src/parkour_solver/numerics/model.py) | Generic input validators, request preparation, numerical scalar dynamics and validation/fingerprints. Many higher layers import its basic validators. | Factor low-level schema helpers and authoritative physics before imposing a dependency direction. Do not duplicate scalar equations into new folders. |
| [`support_order.py`](../src/parkour_solver/search/domain.py), [`time_progress.py`](../src/parkour_solver/search/compat/time_progress.py) | Current initial-domain / generation-completion helpers mixed with old supplied-order/common-clock machinery. | Keep current helpers in normal search modules; retain callable compatibility policies separately. “Legacy” does not mean disposable. |
| [`geometry.py`](../src/parkour_solver/spatial/cells.py), [`regions.py`](../src/parkour_solver/spatial/regions.py), [`turn_observability.py`](../src/parkour_solver/observability/turns.py) | Runtime geometry/records and SVG/plot rendering in the same modules. | Runtime geometry/compact observations stay shipped; rendering goes to developer tools. |
| `route_guidance/` | Compact circle/disk/SOCP experiments and scientific reports. | Move as a research prototype, with its own extra dependencies. It is not the active anchor solver's reachability engine. |
| `autonomy/README.md` and dozens of design files under source | Chronological plans/results intermixed with current mechanics. | Current guides in `docs/`; implemented design records in `docs/design/`; source-era records in `docs/history/`; literature reviews in `docs/research/`. Review authority per file, not by age alone. |

These are observed dependencies, not hypothetical style objections. The import
ledger records eight edges from runtime/mixed modules into mixed/research modules.
`checkpoint_search`'s whole-module static closure contains81 modules, or83 when
package initializers are included. That is an overapproximation of execution:
conditional old strategies are included. Conversely the required `ipopt/worker.py`
is launched by filename through a subprocess, so static imports alone miss it.
Unused-by-this-closure is not evidence that a module can be archived/deleted.

Current ceilings, ladder/vine movement, supplied corridors, virtual goals and
ordinary ordered-route APIs are supported capabilities or compatibility paths,
even when Real11 does not use them. Keep them as such until explicitly retired.
Keep production `InteractionGate`/interval/LP reachability distinct from the
experimental conic guidance models.

## Recommended target tree

```text
standalone/
├── AGENTS.md
├── README.md
├── pyproject.toml                  # package, entrypoints, dependency groups
├── src/
│   └── parkour_solver/
│       ├── __init__.py             # small public facade; no eager experiment imports
│       ├── api.py                  # scene solve, fixed-program solve, resume/replay APIs
│       ├── schema.py               # shared primitive request validation
│       ├── physics/                # authoritative scalar horizontal/vertical rules
│       ├── scene/                  # scene/route schemas, normalization, boxes/supports
│       ├── movement/               # profiles, units, composition, normal/step/ceiling
│       │   └── ladder/             # existing ladder/vine capability
│       ├── spatial/                # cells, regions, contact/event graph, soft guides
│       ├── search/                 # orchestration, frontier, DFS, comparisons, recovery
│       │   ├── anchors/
│       │   ├── events/
│       │   ├── bounds/
│       │   └── compat/             # retained supplied/window/common-clock strategies
│       ├── constraints/            # physical variants, floor/avoidance/contact rows
│       ├── reachability/           # shared preparation, phase intervals, optimistic LP
│       ├── numerics/               # current IPOPT request/model/worker/continuation
│       ├── validation/             # numerical, vertical, support, scene, witness replay
│       ├── io/                     # checkpoints, reports, capture/TAS and provenance
│       ├── bridge/                 # Python compiler adapter / counting wrapper
│       ├── observability/          # compact runtime records, not plotting
│       └── cli/                    # thin solve/blocks/numerical/replay entrypoints
├── native/
│   ├── java/                       # four production Java bridge sources
│   └── gradle/                     # standalone bridge build adapters
├── tests/
│   ├── unit/                       # mirror runtime domains
│   ├── integration/                # native/worker/scene/CLI/resume boundaries
│   ├── regression/                 # small stable derived witness/model cases
│   ├── support/                    # builders, fixture paths, native environment helpers
│   └── fixtures/                   # current61 small fixtures + promoted minimal cases
├── cases/                          # curated runnable tasks, e.g. Real11 physical anchors
├── tools/                          # reusable test/profile/render/audit/export drivers
├── research/
│   ├── route-guidance/             # scientific prototype, drivers, configs, results
│   ├── legacy-cell-routes/         # historical drivers removed from source packages
│   └── <experiment>/               # preserved old bundles, new organized experiments
├── docs/                           # current user/developer/architecture/run guides
│   ├── design/
│   ├── history/
│   └── research/
├── build/native/                   # ignored classes/compiler config; regenerated
└── runs/                           # ignored local run outputs/scratch
```

`parkour_solver` is a proposed package name, not an existing API. Keep the folder
names tied to responsibilities, not chronology (“cell_routes”, “checkpoint”,
“new_solver”) or one map. Avoid introducing a directory for every one-file helper;
only the already-large anchors/events/ladder areas need a second level initially.
The exact per-module proposed destinations are in `modules.csv`, including splits.
They are planning assignments, not executable rename commands.

There should be one maintained solve path and one shared comparator/reachability
service after the move. This proposal does not create a second solver, parallel
experimental backend, feature flags or new pruning semantics.

## Boundary rules that make the layout meaningful

1. The shipped package cannot import `research`, `tools`, `tests`, their fixtures,
   or saved experiment source. Drivers/tools/tests can import the solver API.
   Add a dependency check for this rule, not just a naming convention.
2. Schema/physics/scene primitives must not depend on search or research.
   Search composes movement/spatial/constraint/reachability/numerical services;
   the numerical worker does not import DFS. Validation consumes models/witnesses,
   not experimental runners. Existing mixed modules need extracting to get here.
3. Keep ordinary import light. CasADi belongs to the numerical worker/backend;
   plotting and Clarabel do not become mandatory to load a normal solver API.
   Current pinned versions can be retained in appropriate dependency groups.
   SciPy is used by runtime optional LP screening; it is not purely a research
   dependency even though its current requirements file lives under route guidance.
4. Generic CLI takes user task/config paths. The solver never locates Real11 or a
   benchmark through `Path(__file__).parents[...]`. Stable cases/test fixture
   resolvers are separate from runtime. Install the package for both parent and
   selected worker interpreter; do not rely on the launch working directory.
5. “Compatibility” means a supported older strategy/format, not abandoned code.
   Keep it tested and findable, using common physical/model/policy helpers.
   Do not move source into research merely for not being on Real11's hot path.
6. Historical evidence stays immutable. Keep manifest/SQLite pairs and copied
   instrumentation together. Record old→new artifact location mapping; do not
   rewrite recorded source paths, provenance hashes or old results to look current.

## Data, fixtures, tools and research

For new experiments, use `research/<topic>/README.md`, `driver.py`, `configs/` and
`runs/<run-id>/` with provenance/source/settings/checksums. Old experiments should
first move as whole bundles with their internal paths unchanged; do not force
all historic layouts into the new shape in the same change.

Only maintained, scenario-independent profilers/test runners/renderers belong in
`tools/`. Scenario probes and method comparisons stay in research. Promote the
shared writer/replay/hash/record services into runtime once, then make both tools
and research use those APIs. Frozen instrumentation copies remain frozen copies.

A canonical Real11 case should be a small versioned task fixture under `cases/`,
with its origin recorded. The runtime takes it as input. Ordinary regression
tests should use minimal promoted cases under `tests/fixtures/`, not deserialize
an entire300s experiment just to obtain one program. Keep archive-replay tests
separate where full old evidence is genuinely the subject of the test.

The834 tracked experiment files total698,724,141 bytes (~666.36 MiB), including
seven shared-store databases and large compressed reports. Relocating them will
not reduce existing Git clone/history size. Keep them initially. Deciding whether
future bulky evidence lives outside Git with a checksum catalog is a separate
storage decision; this review does not authorize deleting evidence, rewriting
history or switching storage services.

## Concrete migration sequence

| Step | Scope | Acceptance before proceeding |
| --- | --- | --- |
| 1. Extract required runtime services in the existing layout | `replay_witness`, atomic report IO, witness enumeration, provenance/source manifest, search work/counting helpers. Make runtime callers independent of experiment drivers. | No runtime→research/tools imports. Existing numerical requests/policies and public entry behavior remain the same. Focused replay/persistence/CLI checks pass. |
| 2. Separate research/data/docs/test dependencies | Remove the12 drivers and10 prototype modules from library areas; organize docs; introduce curated cases/test support. Preserve original experiment bundles and hash/location index. | Every tracked file accounted for; no lost large artifacts. Current tests' fixture references resolve; baseline evidence byte hashes unchanged. |
| 3. Introduce installable production package and entrypoints | Add `src/parkour_solver`, packaging metadata, thin public API/CLI, native build root and ignored runs/build separation. Port maintained callers. | Import/CLI/worker from outside repo cwd in both parent/worker interpreters; no installed dependency on the research checkout. Bridge compiler/replay works with regenerated configs. |
| 4. Group runtime by domain | Apply reviewed per-module moves/splits incrementally; factor low-level schema/physics dependencies and separate rendering. Preserve existing algorithms. | Shared comparison/reachability still have one owner; no import cycles introduced; original-state solving, lazy bounds, owned endpoints and failure/recovery invariants pass. |
| 5. Consolidate tests and maintained developer tooling | Move test builders to `tests/support`, fixtures to their own root, maintained native gate/profile/render drivers to tools; retire only duplicate wrappers. | Same declared600-test acceptance scope, no silent loss of excluded/capability suites. Full native regression, known-prefix controls, replay/resume and a cold smoke pass. |
| 6. Refresh all navigation/provenance/compatibility | Regenerate code/function/file indexes; update active commands and source hashing; document supported public entrypoints and historical repro method. | Local links resolve, installed source manifest covers all real runtime files, saved checkpoint/model identities stay valid where semantics match. |

Steps should be small feature commits with their related tests/docs. Extracting
services must precede moving their old mixed modules. The sequence is a dependency
order, not a request to implement all steps now.

## Specific migration traps verified in this checkout

- **24 test modules reference experiments.** The exact list is in `summary.json`.
  `test_proposal_analysis`/`test_transfer_time_bounds` read saved B3 contexts;
  `test_minimum_runs` reads archived virtual-finish reports. Moving folders alone
  breaks these. Promote minimal cases or deliberately classify archive tests.
- **65 test→test import edges** share builders such as `flat_scene`, `flight` and
  `problem`. Move those helpers into test support rather than rewriting every
  fixture in each new test group.
- **84 tracked Python files use `__file__`.** Not all are bugs: some legitimate
  worker/resource locations are relative. Inspect each before moving; the ledger
  identifies them. Static scans do not discover every string-based script path.
- `automatic_experiment.source_hashes` scans three flat directories. It would miss
  nested runtime files after a package split. Replace it with a maintained runtime
  source manifest, while keeping old report hashes as historical records.
- `ipopt.solver._worker_command` launches sibling `worker.py`, which also has direct
  script import fallbacks. A package move must preserve that contract or move to
  a tested module launch, including the selected worker virtualenv.
- `autonomy/compiler.gradle` and `ipopt/export.gradle` hardcode standalone source/
  build locations. Generated `compiler.json` embeds machine classpaths. Move the
  build adapters deliberately and regenerate outputs; do not version local paths.
- Tests/mocks/native gates name old modules explicitly. Import rewrites must cover
  patch targets, suite manifests, subprocess `-m` commands and new entrypoints.
- The whole-module static graph has conditional cycles/shared helpers. This is
  not permission to break them by moving files into arbitrary “layers”; establish
  the package dependency direction by extraction and focused checks.

Avoid permanent shims for every old private module: they recreate the clutter.
If transitional public aliases are needed, keep a small documented facade for
supported API/CLI paths, port maintained tools, and reproduce frozen historical
scripts against their recorded revision. Do not silently edit archived scripts
or pretend every old private import remains a supported public interface.

Pure layout changes should not automatically bump physical/decision policy
versions or invalidate compatible checkpoints. Test stored JSON resume/model
identity explicitly; version only what actually changes. Retain current hard3,
comparison1, cadence1, no-stepping hard transfer, protected source hops, binary
turns, lazy bounds and minT→MAXZ behavior. No numerical feature or search-policy
change is part of this proposal.
