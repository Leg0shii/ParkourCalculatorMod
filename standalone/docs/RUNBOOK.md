# Standalone runbook

Use the session's physical checkout. From `standalone/`, `pwd -P` and
`git rev-parse --show-toplevel` identify it; do not change checkout based on an old
handoff path. Ordinary Python/documentation work requires standalone checks.
Java bridge build/export commands below explicitly run from the repository root.

## Install and environment

The existing repository-root `.venv-guidance` is the native environment on this
machine. From `standalone/`, install the package into the parent and worker Python:

```sh
../.venv-guidance/bin/python -m pip install --editable . --no-deps
export PKC_IPOPT_PYTHON="$PWD/../.venv-guidance/bin/python"
export PKC_RUN_TICKS_COMPILER="$PWD/build/native/compiler.json"
export OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1
```

For a fresh environment, use Python3.12+ and the pinned extras:

```sh
python3.12 -m venv ../.venv-guidance
../.venv-guidance/bin/python -m pip install --editable '.[numerics,reachability]'
# Optional scientific prototype and rendering dependencies:
../.venv-guidance/bin/python -m pip install --editable '.[research,plot]'
```

The parent API uses the standard library. CasADi/NumPy run in the numerical
worker; SciPy supplies optional LP reachability. Clarabel and plotting are
optional research/tool dependencies. Nothing installs at runtime. Keep the venv
executable path rather than resolving its symlink to a system interpreter.
Install `parkour_solver` into each interpreter when parent and worker differ.
Installed API and CLI/worker launch work outside the repository cwd; task,
compiler and output paths must then be absolute. Developer `tools`/`tests` run
from `standalone/`, without a repository-root `PYTHONPATH` requirement.

## Java bridge, only when absent or affected sources changed

From the repository root, build the standalone bridge adapters using current
checked-in core/Forge sources. This does not launch Minecraft or run mod tests:

```sh
./gradlew --configure-on-demand \
  -Dorg.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home \
  -I standalone/native/gradle/compiler.gradle -I standalone/native/gradle/export.gradle \
  :core:prepareRunTicksCompiler :core:compileStandaloneIpoptExport
```

The JDK path is machine-specific. Outputs live under `standalone/build/native/`;
`compiler.json` embeds a machine-specific classpath and is ignored. Do not rebuild
while another numerical job uses that classpath. Four bridge sources live under
`native/java/`; [mod references](MOD_REFERENCES.md) identifies reused APIs.

## Tests and layout checks

From `standalone/` with the environment above:

```sh
"$PKC_IPOPT_PYTHON" -B -m unittest tests.unit.search.test_candidate_comparison tests.unit.reachability.test_proposal_analysis tests.unit.search.test_transfer_time_bounds
"$PKC_IPOPT_PYTHON" -B tools/native_gate.py --output /tmp/parkour-native-new
"$PKC_IPOPT_PYTHON" -B -m tools.validate_layout
"$PKC_IPOPT_PYTHON" -B -m unittest discover -s tests -t .
```

The explicit gate preserves the earlier600-test scope and adds10 package/profiler
checks. Full discovery additionally includes dedicated ladder/climbable/vine
capabilities and their recorded baseline; it is a different scope. Native
variables absent means skips, not native acceptance. See [testing](TESTING.md).
Use a new gate directory; its report records source hashes, source stability,
counts, skips, exclusions and evidence-integrity results.

## Current physical-anchor solve, profile and resume

[Execution reference](ANCHOR_SEARCH_EXECUTION_REFERENCE.md) describes current
combinations, pruning, bounds and dispatch. The curated Real11 case declares
finite physical B7 and anchors, with initial preparation selected by the orchestrator
without a first-jump whitelist; its origins and domain change are in
[cases/real11](../cases/real11/README.md). Run from `standalone/`:

```sh
"$PKC_IPOPT_PYTHON" -B -m parkour_solver \
  --input cases/real11/physical-anchors.json --output /tmp/real11-new.json.gz
"$PKC_IPOPT_PYTHON" -B -m parkour_solver \
  --input cases/real11/physical-anchors.json --resume /tmp/real11-new.json.gz \
  --output /tmp/real11-resumed-new.json.gz
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input cases/real11/physical-anchors.json --output /tmp/real11-profile-new --seconds 300
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input research/hard-jump-comparisons/2026-11-11/initial0.json \
  --output /tmp/real11-initial0-new --seconds 90
```

Console `parkour-solve` is equivalent to `python -m parkour_solver`. The CLI accepts
explicit `--compiler` and `--python` paths as well. Profile runs preserve cold
settings, instrumentation/source/configuration hashes, work counters and
independent retained-witness replay; inclusive timings overlap. Profiling overhead
and local solver behavior affect the work fitting a time budget. No archived
facings or trajectories are seeded into a cold profile.

`--resume` restores saved search state only when its exact context matches.
Compatible JSON/shared-store identities are preserved by this folder move; solver
policy changes still require their usual compatibility checks. Each re-solve
starts at the original state with earlier facings free. Inspect `resume` metadata
before claiming exact continuation. Cancellation preserves partial work; timeouts
and caps do not certify infeasibility or optimality.

Reports can be shared-store manifests with paired `.store.sqlite3` files. Keep
both together and read through:

```python
from parkour_solver.io.checkpoint_store import read_report
report = read_report('/tmp/real11-new.json.gz')
```

Do not treat raw gzip JSON as a fully expanded shared-store report. Write new
experiments to fresh output paths; preserve dated evidence and source hashes.

## Fixed numerical problem and replay/export

This optimizes facings for a captured fixed-input problem:

```sh
"$PKC_IPOPT_PYTHON" -m parkour_solver.cli.numerical \
  --input tests/fixtures/numerics/real11-200.json.gz \
  --seconds 10 --output /tmp/fixed-ipopt-new.json
```

`parkour-ipopt` is equivalent. See the [numerical reference](reference/NUMERICAL_SOLVER.md)
for formats, worker options, capture export and exit codes. Captured fixtures keep
their original inertia/contact settings; decision search explicitly uses threshold0.

The [replay contract](reference/REPLAY.md) supplies the full Gradle command and
TAS/export validation. The adapter is now `standalone/native/gradle/export.gradle`;
its tasks/properties are unchanged. Use the validated problem/result, effective
input template and supplied scene. A current validated replay report establishes
local replay acceptance. Scene/numerical validity does not establish undeclared
actual-world geometry validity.

## Saved-data audits and older strategies

```sh
"$PKC_IPOPT_PYTHON" -m tools.audit_candidates \
  --directory research/orchestrator-fixes/evidence --output /tmp/candidate-audit-new
"$PKC_IPOPT_PYTHON" -m tools.witness_bound_audit \
  --reports /absolute/path/to/accepted/report.json.gz --output /tmp/bound-audit-new.json
```

Candidate auditing makes no IPOPT calls; optional `--compiler` adds independent
Java replay. Bounds auditing checks retained valid witnesses and is not a cold
discovery seed. Read the [Real11 guide](REAL11_GUIDE.md) before interpreting counts.

Fixed-clock/virtual controls remain available as `parkour-fixed-clock` or
`python -m parkour_solver.cli.fixed_clock`, for example with
`research/virtual-finish/fixture.json`. Supplied-corridor library strategies live
under `spatial/compat` and `search/compat`. Their virtual/state64/source-specific
measurements are distinct from current finite-anchor evidence.

[research/README.md](../research/README.md) explains preserved bundles. Historical
scripts and READMEs retain their original commands/imports; reproduce those against
their recorded revision. Use current tools above for current code. No permanent
facades were created for every old private module.
