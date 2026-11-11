# Standalone runbook

Commands below run from the **physical research repository root**, not from `standalone/` or the main checkout. In a symlinked standalone directory, run `pwd -P` then `cd "$(git rev-parse --show-toplevel)"`; verify the task’s branch using `git branch --show-current`. The current integrated task uses `research/ipopt-cell-routes` in this checkout; the earlier `research/real11-initial-run-ticks` path is historical. Root Gradle builds/tests and Minecraft are unnecessary for normal standalone iteration.

## Current physical-anchor entry and evidence

Read the [execution reference](ANCHOR_SEARCH_EXECUTION_REFERENCE.md) for the actual
combination/pruning/IPOPT boundaries and decisions. The completed runtime is
`dd738c98`; do not resume an older policy merely because its integer schema matches.
Use the environment below, then these commands from the physical repository root:

```sh
"$PKC_IPOPT_PYTHON" -B standalone/autonomy/experiments/hard-jump-comparisons/native_gate.py --output /tmp/anchor-current-native-new
"$PKC_IPOPT_PYTHON" -B standalone/autonomy/experiments/anchor-runtime-profile/profile_run.py --input standalone/autonomy/experiments/anchor-feasibility/real11-anchors.json --output /tmp/real11-current-combined-new --seconds 300
"$PKC_IPOPT_PYTHON" -B standalone/autonomy/experiments/anchor-runtime-profile/profile_run.py --input standalone/autonomy/experiments/hard-jump-comparisons/2026-11-11/initial0.json --output /tmp/real11-current-initial0-new --seconds 90
```

Outputs must be new. Profiled arms retain invocation fingerprints, wrapper/worker
status, source/configuration hashes, instrumentation snapshots, counters and
independent retained-witness replay. `report.json.gz` is a manifest with a paired
`.store.sqlite3`; decode with `standalone.autonomy.checkpoint_store.read_report`.
Never overwrite dated evidence or separate manifest/database companions. Read
[measured results](../autonomy/experiments/hard-jump-comparisons/README.md) before
starting another long run. Older commands later in this runbook remain supplied,
virtual, native-helper or replay controls with their own task semantics.

## Environment

The existing ignored `.venv-guidance` is the current numerical environment on this machine. If creating a fresh environment, use Python 3.12 and pinned dependencies:

```sh
python3.12 -m venv .venv-guidance
.venv-guidance/bin/python -m pip install -r standalone/autonomy/route_guidance/requirements.txt
# Optional plots only:
.venv-guidance/bin/python -m pip install -r standalone/autonomy/route_guidance/requirements-plot.txt
```

The smaller `standalone/ipopt/requirements.txt` installs only CasADi/NumPy. Guidance requirements add Clarabel/SciPy; some reachability tests need SciPy. The parent API otherwise uses standard-library Python and an explicit worker interpreter. Do not silently install at runtime.

```sh
export OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1
export PKC_RUN_TICKS_COMPILER="$PWD/standalone/autonomy/build/compiler.json"
export PKC_IPOPT_PYTHON="$PWD/.venv-guidance/bin/python"
export PYTHONPATH="$PWD"
```

Keep the virtualenv executable path. Resolving that executable's symlink to the system Python can lose its installed native dependencies. Resolve the **workspace**, not the worker executable.

## Java bridge, only if absent or affected code changed

The compiler and replay reuse checked-in core/Forge classes. They are not a Minecraft launch or mod test run. Build before starting concurrent numerical jobs, not while a job is using the classpath.

```sh
./gradlew --configure-on-demand \
  -Dorg.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  -I standalone/autonomy/compiler.gradle -I standalone/ipopt/export.gradle \
  :core:prepareRunTicksCompiler :core:compileStandaloneIpoptExport
```

The Java-home path is machine-specific. The generated compiler descriptor/classpath is local build output, not portable evidence. Existing [autonomy README](../autonomy/README.md) describes bridge operations. [Mod references](MOD_REFERENCES.md) identifies the sources to reuse.

## Quick checks and broader suites

```sh
python3 -m unittest standalone.autonomy.tests.test_candidate_audit -v
python3 -m unittest standalone.autonomy.tests.test_cell_geometry standalone.autonomy.tests.test_cell_routes -v
.venv-guidance/bin/python -m unittest standalone.autonomy.tests.test_orchestrator_continuation standalone.autonomy.tests.test_repair_resume -v
.venv-guidance/bin/python -m unittest discover -s standalone/autonomy/tests -v
.venv-guidance/bin/python -m unittest discover -s standalone/ipopt/tests -v
```

Without the environment variables, integration tests may **skip**, not pass numerical behavior. Broader discovery can take many minutes; user prioritizes accuracy over arbitrary speed. Choose checks from [testing](TESTING.md), record counts/skips and the known baseline ladder failure. Documentation-only changes need link/inventory checks, not numerical reruns.

## Small numerical request

This is a fixed captured-input problem, not autonomous route discovery:

```sh
.venv-guidance/bin/python standalone/ipopt/solve.py \
  --input standalone/ipopt/fixtures/real11-200.json.gz \
  --seconds 10 --output /tmp/fixed-ipopt-result.json
```

Captured fixtures can contain their own inertia/contact assumptions. Current decision-search fixtures explicitly use zero inertia thresholds; do not rewrite historical captures to match. See [IPOPT README](../ipopt/README.md) for every request field, worker options, exit codes and capture export.

## Cold Real11 discovery and resume

The current [virtual-finish fixture](../autonomy/experiments/virtual-finish/fixture.json) uses the selected support order and initial-jump domain `[0,8]`, discovers the descending height7 tick with X<729.7 and Z>=-1095.3, and ranks time before Z. The 200-input guard is not a fixed terminal tick. The runner derives collision-query coverage from a conservative reach bound; it adds no terrain. The older state64 fixture and its results remain historical controls. Use fresh output names; do not overwrite committed evidence.

```sh
.venv-guidance/bin/python -m standalone.autonomy.cell_routes.fixed_clock_experiment \
  --input standalone/autonomy/experiments/virtual-finish/fixture.json \
  --output /tmp/real11-cold-new.json.gz

.venv-guidance/bin/python -m standalone.autonomy.cell_routes.fixed_clock_experiment \
  --input standalone/autonomy/experiments/virtual-finish/fixture.json \
  --resume /tmp/real11-cold-new.json.gz --output /tmp/real11-resumed-new.json.gz
```

`--resume` uses saved `searchState` when available. A legacy report with only retained histories is explicitly reconstructed and is **not exact continuation**. `--retry-unknown` intentionally reopens numerical unknowns; ordinary resume does not restart every failed local solve. Current DFS state is version5. Goal definitions belong to its context fingerprint; a fixed64 report cannot be resumed as the new virtual finish. `finish` reports the nominal height, actual final Y and actual ground state separately; virtual landing does not create a physical floor. Do not silently reuse old raw checkpoint schemas as exact continuation.

To change budgets, copy the fixture into a new local file and change its settings with provenance; report the changed domain and budget. Fewer-run/more-jump preferences do not authorize silently deleting allowed choices. The seeded reference control below tests a different claim:

```sh
.venv-guidance/bin/python standalone/autonomy/experiments/orchestrator-fixes/regressions.py \
  --output /tmp/real11-fixed-reference-control.json.gz
```

It uses the saved fixed schedule **and yaw**. Successful reference validation/resume proves model and continuation behavior, not cold search success.

## Offline audit without a new solve

Read [Real11 guide](REAL11_GUIDE.md) first. Audit scripts write reports; always select a new output directory.

```sh
python3 standalone/autonomy/experiments/orchestrator-fixes/audit_candidates.py \
  --directory standalone/autonomy/experiments/orchestrator-fixes/evidence \
  --output /tmp/real11-offline-audit
```

This reads the three saved cold reports and reconstructs the ledger. Add `--compiler "$PKC_RUN_TICKS_COMPILER"` for independent replay (Java needed, no IPOPT). Without it, replay fields are not fresh validation. The checked-in audit includes additional follow-up/provenance artifacts not all recreated by that one command. Existing recorded evidence should remain untouched.

`audit_generation.py` performs bounded offline generator/profiler probes and writes into the checked-in audit location by default; inspect it before rerunning. It uses exact decorated coefficient/profile context, not an ad hoc reconstruction. No Java/IPOPT calls occur inside its probe. The historical script requires its original cursor context; for the current algorithm use `experiments/dfs-interactions/measure_generation.py --output /tmp/generation-new.json`, which explicitly rebuilds new cursors over the old physical models. Three-second samples are not exhausted-domain counts or controlled speedup benchmarks.

## Replay and TAS export

Use the validated problem/result and effective-input template; add a scene for discovery checks. The full command and report contract are in [REPLAY.md](../ipopt/REPLAY.md). Typical properties: `ipoptCapture`, `ipoptInput`, `ipoptResult`, `ipoptScene`, `ipoptOutput`, `ipoptReport`, `ipoptReplaySeconds`, `ipoptGiveBack` with Gradle task `:core:replayStandaloneIpopt` and init script `standalone/ipopt/export.gradle`.

Only a current `validated` replay report authorizes claiming the exported TAS passed local replay. A pre-existing output file can survive a failed invocation. Declared-contact replay, static-scene discovery and actual-world mod validation are distinct. Full actual-world validation remains a later explicit mod step.


Current source-matched validation uses [dfs-interactions/real11-fixture.json](../autonomy/experiments/dfs-interactions/real11-fixture.json): the same geometry/input domain and candidate limits,150s per session. The archived cold and exact-resume reports have the same model-context hash despite additive request logging between sources. Reconcile or regenerate their call table without Java/IPOPT:

```sh
PYTHONPATH="$PWD" .venv-guidance/bin/python \
  standalone/autonomy/experiments/dfs-interactions/summarize.py \
  --cold standalone/autonomy/experiments/dfs-interactions/evidence/real11-cold.json.gz \
  --resume standalone/autonomy/experiments/dfs-interactions/evidence/real11-resume.json.gz \
  --output /tmp/dfs-summary-new.json --calls-output /tmp/dfs-calls-new.md
```

The included reports stop with a valid incumbent plus saved unfinished work. To continue them use the fixed-clock CLI, the same archived fixture, `--resume` on the saved resume report and a **new** output path. Ordinary continuation does not intentionally retry completed or unresolved numerical requests; `--retry-unknown` reopens unresolved ones. Report pending root/subtree coverage and any time spent serializing state; the time setting is a search budget, not a hard process-duration guarantee.

## Automatic untimed corridor runs

From the physical repository root, with the native worker environment above:

```sh
python3 -B -m standalone.autonomy.cell_routes.automatic_experiment \
  --input standalone/autonomy/experiments/automatic-corridors/fixture.json \
  --output /tmp/automatic-corridors-new.json.gz \
  --compiler "$PKC_RUN_TICKS_COMPILER" --python "$PKC_IPOPT_PYTHON"
```

Resume with `--resume /tmp/automatic-corridors-new.json.gz` and a NEW output path. The fixture supplies unordered physical geometry, the terminal predicate, independent initial jump inputs and explicit existing budgets. It supplies no points/support list/comparison clocks. CLI progress keeps the latest full in-memory state and writes atomic periodic snapshots (at most one per second between completed writes), plus an unconditional final report. Source hashes distinguish dirty implementation measurements from archived revisions.

`coverage_audit --input <fixture> --witness <archived-report.json.gz> --output <new-audit.json.gz> --compiler "$PKC_RUN_TICKS_COMPILER"` first enumerates without the witness, then independently replays/classifies it. Its SVGs show actual raw/transit/support rectangles, locally cropped only for display. Coverage does not seed a cold numerical search or establish minimum time.

## Automatic event-guided discovery

No-points/no-corridor search now uses the shared event backend. [The migrated Real11 fixture](../autonomy/experiments/event-route-discovery/real11-auto.json) preserves the physical task, initial0/8 domain, horizon and numerical budgets; `discoverySettings` version1 provides resumable graph/materialization work quotas. Legacy `spatialSettings` and automatic schema6/cell-walk continuations are rejected explicitly. Supplied corridor controls remain executable.

From the task worktree root with the native environment set (the shared compiler/worker paths in the [resume handoff](CORRIDOR_IMPLEMENTATION_RESUME.md) remain read-only):

```sh
"$PKC_IPOPT_PYTHON" -B -m standalone.autonomy.cell_routes.automatic_experiment \
  --input standalone/autonomy/experiments/event-route-discovery/real11-auto.json \
  --output /tmp/real11-event-cold-new.json.gz \
  --compiler "$PKC_RUN_TICKS_COMPILER" --python "$PKC_IPOPT_PYTHON"

"$PKC_IPOPT_PYTHON" -B -m standalone.autonomy.cell_routes.automatic_experiment \
  --input standalone/autonomy/experiments/event-route-discovery/real11-auto.json \
  --resume /tmp/real11-event-cold-new.json.gz --output /tmp/real11-event-resumed-new.json.gz \
  --compiler "$PKC_RUN_TICKS_COMPILER" --python "$PKC_IPOPT_PYTHON"
```

Outer state `event_route_search` schema1 stores one immutable domain/graph and schema7 dynamic DFS. Physical constraint generation uses state2/context7. Materialization guards suspend residual language/work and do not establish all-routes exhaustion. Full scene/task replay remains necessary for an accepted execution; raw cell-walk completion is no longer an automatic acceptance condition.
