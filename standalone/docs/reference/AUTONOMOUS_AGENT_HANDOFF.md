# Standalone IPOPT handoff for the autonomous-solve agent

Consolidation update, 2026-10-30: use `feature/ipopt-consolidated` and [standalone setup](../README.md). The current branch contains both orchestration and the integration/replay implementation. Earlier branch/import/ownership instructions are historical; see [CONSOLIDATION.md](../CONSOLIDATION.md).

Read [RECENT_REPLAY_HANDOFF.md](RECENT_REPLAY_HANDOFF.md) first for the short current handoff. Later baseline/planning paragraphs in this longer document predate the implemented local backend and are superseded by that handoff and MOVEMENT_PORT.md.

Updated 2026-10-28: the current handoff now covers all replay/compiler changes since the original baseline, runnable compile/solve/replay commands, corrected 400-input success, publication/import guidance and unfinished window state carry. Final actual-world validation belongs to the mod after manual TAS import; no running client or mandatory complete-scene export is required by the standalone workflow. The implementation now exists; older statements below saying otherwise are historical.

Prepared 2026-10-26. This document describes implemented behavior at clean solver baseline `498ecc1`, plus the ownership split requested by the user. Read [README.md](README.md) for the full request schema and [FINDINGS.md](FINDINGS.md) for measurements and known limitations.

Local replay addition: [REPLAY.md](REPLAY.md) specifies the optional Java replay API/CLI, static scene schema, supported 1.8.9 mechanics and event-mismatch feedback. Supply complete collision boxes/ladder cells from the planner to enable discovery; no running Minecraft client is required. Existing fixtures remain usable in declared-contact mode. Replay/angle realization and TAS export belong to the integration agent; input/route/event search remains with the autonomous agent. A changed event schedule is feedback for recompilation, not an infeasibility certificate.

Movement compilation update: [MOVEMENT_PORT.md](MOVEMENT_PORT.md) is the current source map, capability/limitation ledger and recompilation workflow. Fixed wall hits cancel sprint, and legacy air acceleration carries the previous factor for one tick. Use `compileStandaloneIpoptPhysics` on old requests before solving for game replay; legacy benchmark arrays retain the older assumptions. Corrected 200/400-input solves now pass exact declared-contact repair/export. The compiler and replay share `ReplayInputState`, `PlayerSprintMachine` and row realization. The `movement` object makes collision/ground assumptions explicit and independently checked by the Python validator. Fluids/webs/slime/block callbacks and other profiles still require ports; do not encode them as ordinary inert geometry or reuse ordinary air coefficients.

## Ownership and branches

The autonomous-solve agent owns reducing the information a user must supply: discovering candidate input patterns, choosing useful subproblems/objectives, probing feasibility, retaining alternatives and assembling a good route. This agent owns the implementation work that connects the standalone solver to Minecraft and the mod: state/geometry capture, movement-coefficient compilation, game-thread boundaries, external worker invocation, replay feedback, result realization and application/UI wiring. These adapters are future integration work, not APIs already available.

The integration agent's isolated checkout is `/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-mod-integration`, branch `feature/ipopt-mod-integration`, created from `feature/ipopt-clean` at `498ecc1`. Do not edit that checkout from the autonomous agent. Work in your own worktree. Prefer separate autonomy modules consuming the public solver API; coordinate shared request-schema or numerical-model changes rather than editing the same files concurrently. Do not change the other native-solver agent's handoff or checkout.

The detailed integration sequence is in [MOD_INTEGRATION_PLAN.md](MOD_INTEGRATION_PLAN.md). Its first shared deliveries are a reusable fixed-input compiler/process client and a portable scene/start snapshot, before full UI integration. Candidate-row realization and actual Minecraft replay feedback follow. These are planned capabilities; until they land, use the existing compiled request/API and fixtures described below.

Updated priority from the user: integration now focuses on replay after an existing solve, while the solver-input interface remains open. The first adapter will use effective rows/start context from a TAS template plus solved angles, probe exact angle cells, obtain actual Minecraft validation and export a reloaded/revalidated TAS. The replay-first section of the integration plan supersedes its earlier compiler-first ordering. Orchestration can continue against the existing API without waiting for a new input compiler.

Deployment clarification: the user prefers standalone local replay without an open Minecraft client or running mod. The existing loader wrapper requires a live client. Alternatives under discussion are a headless actual-Minecraft harness or a local exact-movement replay with sufficient world/event contract checks. The user asked to compare priority/effort before implementing either; see the current decision point in MOD_INTEGRATION_PLAN.md. No new backend is implemented. The numerical Python solver and existing fixture tests already run independently of Minecraft.

For the current block-sequence/minimum-run-ticks task, read [RUN_TICKS_PHYSICS_HANDOFF.md](RUN_TICKS_PHYSICS_HANDOFF.md). It links the in-repo physics mirror and existing run-tick implementation, and explains support geometry, Y-first landing timing, inserted-row coefficients/reindexing and why unresolved cheaper solves prevent a minimum certificate.

The published clean solver is `origin/feature/ipopt-clean`. It adds only `standalone/ipopt/` to upstream main. `feature/ipopt-standalone` at `5ef088b` is the preserved research archive, containing inherited native solver experiments, benchmark tools and extensive traces. Do not merge that archive just to obtain IPOPT. Neither `tools/angle-model-comparison` nor `tools/angle-feasibility-bench` is required by this package.

To create a fresh autonomous worktree using only the clean baseline, run from an existing clone:

```sh
git fetch origin feature/ipopt-clean
git worktree add -b feature/ipopt-autonomous ../ParkourCalculatorMod-ipopt-autonomous origin/feature/ipopt-clean
cd ../ParkourCalculatorMod-ipopt-autonomous
```

Use an unused branch/path if those already exist. To bring the solver into an existing agent branch without importing upstream or research history, first verify there are no conflicting standalone files, then cherry-pick `ca77997` and `498ecc1` in order. The first introduces the package and the second only extends FINDINGS.md. Revalidate on your base. Do not overwrite uncommitted work or blindly merge the integration branch as it develops. This handoff can be read directly from the integration checkout without changing your branch.

## What the solver does

The public operation is a bounded local optimization over continuous per-tick yaw, horizontal position and horizontal stored velocity. Inputs are fixed for that call through compiled acceleration magnitude, direction offset and friction arrays. The solver does not choose W/A/S/D/Jump rows, infer world geometry, select a route or automatically splice windows.

The sparse NLP has separate position and velocity states, so long recurrence derivatives are not expanded into one dense angle-only problem. Positions inside the worker are relative to the supplied start; independent validation reconstructs world-coordinate positions from returned yaw. Per-axis automatic inertia and actual ladder clamps are always applied by the validator. Worker success and claimed states are not trusted for acceptance.

Normal optimization retains the best independently validated objective. Feasibility-only stops on the first accepted path. A valid supplied seed can satisfy a feasibility call without launching IPOPT. A few seconds per solve is an acceptable budget per the user; completing earlier is allowed. The time limit is a maximum and includes worker startup/construction, not a required minimum runtime.

## Files to read

| File | Purpose |
| --- | --- |
| __init__.py | Exports prepare, solve, validate and attach_capture. |
| model.py | Request validation/defaults, physical hash, scalar horizontal/vertical replay and acceptance. |
| solver.py | Standard-library parent, worker process, deadline/cancellation, incumbent retention and result/diagnostics. |
| worker.py | CasADi sparse NLP, callback iterates and bounded ladder cap-branch trials. |
| continuation.py | Automatic inertia phases, warm/fallback allocation and valid-incumbent polishing. |
| ladder.py | Scalar component caps, vertical movement, prescribed vertical contacts and climb carry. |
| capture.py | Infer prescribed ladder/Y data from recorded Minecraft 1.8.x debug states. |
| solve.py | CLI, gzip/plain JSON input and atomic output. |
| java/ExportStandaloneIpopt.java, export.gradle | Optional headless captured-TAS compiler using the existing Java core and test fixture reader. |
| tests/ | Scalar, continuation, process/CLI and real IPOPT integration regressions. |

## Runtime setup in your own worktree

From the worktree root:

```sh
python3.12 -m venv .venv-ipopt
.venv-ipopt/bin/python -m pip install -r standalone/ipopt/requirements.txt
export PKC_IPOPT_PYTHON="$PWD/.venv-ipopt/bin/python"
```

Pinned/tested runtime: Python 3.12.2, CasADi 3.8.1 and NumPy 2.5.4. The parent/API uses only the standard library; the worker interpreter needs those packages. For the CLI use `--python` or `PKC_IPOPT_PYTHON`. For direct Python API calls pass `python_executable` explicitly unless the caller's interpreter has the dependencies. The API itself does not read PKC_IPOPT_PYTHON.

On this machine an already installed worker is available read-only at `/Users/arvindmurty/CS/ParkourCalculatorMod/tools/angle-model-comparison/.venv/bin/python`. It can be used with `--python` or the test environment variable without modifying that environment or using its source tree. Prefer an owned virtual environment for independent work; this existing path is a local convenience, not a repository dependency. Do not install into another agent's environment.

## First solve and interpretation

These commands require no Minecraft client, world, source capture or Java build:

```sh
python3 standalone/ipopt/solve.py \
  --input standalone/ipopt/fixtures/real11-200-ladders.json.gz \
  --python "$PWD/.venv-ipopt/bin/python" \
  --seconds 5 --output /tmp/ipopt-autonomous-200.json

python3 standalone/ipopt/solve.py \
  --input standalone/ipopt/fixtures/real11-400-ladders.json.gz \
  --python "$PWD/.venv-ipopt/bin/python" \
  --seconds 10 --output /tmp/ipopt-autonomous-400.json
```

`--feasibility-only` changes the objective search to first accepted feasibility. `--initial-solution /tmp/previous.json` seeds yaw from another result and retains the request's original yaw as a fallback for automatic inertia. Both starts share one budget. Changing `--threshold 0` or `--no-recorded-walls` changes the physical problem; neither is a way to certify the original request. CLI inputs accept both JSON and gzip JSON. The output file contains the complete result; stdout only has a short status summary.

| Status | CLI exit | Meaning |
| --- | --- | --- |
| feasible | 0 | Independent continuous replay accepted a candidate under the supplied model. |
| unknown | 2 | No accepted candidate found in this local search/budget. Not proof of infeasibility. |
| error | 3 | Bad request, missing runtime, protocol or worker failure without a retained candidate. |
| cancelled | 130 | Cancellation discarded the result. |

Do not use subprocess check=True and interpret every nonzero exit as an impossible input pattern. Read the result status. IPOPT local infeasibility, restoration failure and iteration limits are unknown for the overall problem. A valid incumbent can survive a timeout or worker error; inspect termination, workerStatus and error in addition to status. feasible does not certify global optimality or byte-exact game execution.

## Python API example

Run from your worktree root, or put its root on PYTHONPATH:

```python
import gzip
import json
import os
from standalone.ipopt import prepare, solve, validate

with gzip.open("standalone/ipopt/fixtures/real11-200-ladders.json.gz", "rt") as stream:
    request = json.load(stream)

result = solve(
    request,
    seconds=5,
    stop_on_feasible=False,
    python_executable=os.environ["PKC_IPOPT_PYTHON"],
)

if result["feasible"]:
    candidate = result["candidate"]
    checked = validate(prepare(request), candidate["yaw"])
    assert checked["feasible"]
    exit_xz = candidate["positions"][-1]
    exit_velocity_xz = candidate["velocity"][-1]
    exit_vertical = candidate.get("resume")
```

The API also accepts a threading.Event as cancel_event and an on_incumbent callback. The callback receives a copied valid objective improvement; keep it short. Invalid API requests raise ValueError before starting a process. Programmatic validate expects a prepared problem and a list of n absolute degree facings.

For warm initialization, copy the request, save its current yaw as fallbackYaw, and replace yaw with a previous candidate's yaw of matching length. solve validates the seed under the new request before retaining it. Reusing yaw across physics changes is allowed; reusing an old feasible label is not. Explicit inertiaPattern and threshold-zero requests take their single-phase path, so they do not explore the automatic-inertia fallback sequence.

## Request contract and autonomous input generation

The version-1 request is self-contained numerically. Required arrays are magnitude[n], friction[n], base[n] and yaw[n], plus position[2], velocity[2], ticks, version and an objective. prepare supplies supported defaults. position/velocity use X,Z order. base is radians; yaw and facing constraints are absolute degrees. yaw is an initial guess, not a locked facing. startYaw does not substitute for an explicit F constraint.

At tick t the horizontal model applies automatic inertia to incoming velocity, adds `magnitude[t] * (cos(base[t] + radians(yaw[t])), sin(base[t] + radians(yaw[t])))`, applies any ladder clamp, clips prescribed contacts and stores friction-scaled surviving velocity. Thus key selection belongs upstream of this call. Changing a key/sprint/jump pattern requires compiling its magnitude/base/friction and any affected vertical/event data; do not merely change source metadata or a nonexistent keys field. Ground/air/sprint policies already affect the coefficients.

The optional Java exporter compiles existing input rows through AngleSolverEngine.debugBuildSpec and JumpLinearModel. Its KEEP/FORCE_45 and sprint policies matter: the effective keys can differ from the original row keys. The standalone result currently returns yaw, not rewritten TAS rows. Integration will need to realize the effective keys when applying results.

The historical walls field is the scalar constraint list, including X, Z, F, DXZ and DZX. It is distinct from contacts, the physical plane-clipping/reset schedule. Relative rows use t1/t2 and PLUS/MINUS; F residuals wrap. Objectives are X/Z position or a custom direction, with supported motion projection; smoothLambda must be zero. Bounds and typed equality corridors are compiled by the exporter. See README.md and model.prepare for all supported combinations.

Optional inertiaPattern[n][2] uses 0 for zeroing, 1/-1 for signed survival and 2 for unrestricted carry in the phase model. Explicit patterns require a positive threshold and are independently checked against actual automatic thresholding. With no explicit pattern, continuation tries carry, inferred zero events and complete patterns, then polishes the best accepted path. Small branch guards can make a fixed-pattern attempt decline; that is not a proof that all patterns are impossible.

Per-axis inertia is the supported horizontal inertia model. For versions requiring a combined horizontal threshold, the Java exporter deliberately emits threshold 0 and source.perAxisInertiaSupported false. That is an explicit carry approximation, not complete inertia support for that game version. Vertical capture attachment is restricted to Minecraft 1.8.x and uses the 1.8.9 profile. Do not infer multi-version game fidelity merely because the exporter can read another version's TAS.

The physical problemHash excludes source metadata, yaw/fallback guesses, budgets and iteration settings. It identifies the compiled numerical model, not an entire scene or search history. Geometry or raw input rows stored only in source need their own cache/provenance key. Do not cache an unknown result as permanently infeasible.

## Tick boundaries, ladders and Y

All request ticks are zero-based and local to the window. State t precedes input t. Input t produces state t+1. Position/velocity trajectories have n+1 entries; yaw and per-input arrays have n. Constraint X/Z@t applies before input t, and F@t describes that input's facing. source.startTick maps back to the capture.

ladderTicks has n+1 flags. Tick t's start occupancy controls that movement's X/Z caps, independently at double(float(.15)). A completed movement's horizontal collision and next state's ladder occupancy establish the next stored climb carry, `(0.2 - 0.08) * double(float(.98)) = .11760000228881837`. The upward displacement happens on the next input tick. Initial vy is already stored carry; do not apply climb/gravity a second time at a splice. The previous horizontal collision must be retained.

An optional explicit ladderPattern[n][2] selects negative/free/positive cap branches with -1/0/1; non-ladder rows must be zero. Otherwise the worker infers branches and explores a bounded set of neighboring regimes. Scalar replay always applies the actual clamp.

Optional vertical currently supports the Minecraft 1.8.9 profile, initial Y/vy/ground/previous collision, per-input sneak and prescribed fired jump impulses, plus prescribed floor/ceiling/step contacts. Jump inputs are taps by user instruction; held-jump cooldown is out of scope. jumpVelocity is a fired impulse schedule, not a generic raw jump-key intent. Its supplied grounded precondition is checked. Vertical inertia is independent of horizontal threshold.

referencePosition/referenceVelocity, when supplied, are hard scalar acceptance rows. attach_capture supplies them to reproduce the captured Y trace. They are regression oracles, not automatically appropriate user constraints for a route whose events change. Y is replayed outside the horizontal NLP. Removing a reference does not supply missing geometry, landing detection or correct coefficient recompilation.

## Physical assumptions to preserve or replace explicitly

The current ladder fixtures infer occupancy from recorded climb carry/collision and climb-proven cells. Those cells are source metadata: the scalar validator does not check candidate positions against them. A prescribed wall is a plane without a finite block face, and vertical contacts/steps have no support-geometry checks. Validation establishes the supplied continuous event model, not world-schedule consistency. Do not count that as independently proven world feasibility.

The convenient capture/export path still requires recorded states or a saved angle-solver seed. Compiled requests can be supplied directly without a capture, but a convenient general compiler from candidate input rows and explicit initial state/environment is integration work. In particular, changing Y/ground/ladder events may require rebuilding acceleration/friction and jump eligibility.

The intended boundary is that integration supplies snapshots/compilation and replay feedback, while autonomy chooses candidate inputs, subproblems and revisions. Integration can expose geometry and detected contacts/ground/ladder state without requiring autonomy to access live Minecraft. Keep continuous-model acceptance separate from optional game replay validation. No complete scene/feedback API is implemented yet; agree the data contract before both sides add incompatible structures.

Integration source navigation: core Application owns simulation retrigger and result application; AngleSolverEngine builds specs; core ports MinecraftAccess/Simulator expose world queries and replay; TickState carries position, stored velocity, ground, previous collision and live surface samples; loader SimulatorEntity delegates actual game movement to Minecraft. Use those existing paths. Avoid a second independent mod-side physics engine or another copied IPOPT worker. The old feature/ipopt-local spike predates the current package and is reference material only.

## Windows and objective quality

Prefer roughly 150-200 inputs or shorter useful sections; the successful 400-input case is a stress test, not a recommended default. There is no automatic multi-window planner or general problem slicer in this package. Prescribed contact ticks, positional/facing rows, references, objective ticks and every per-tick array need consistent reindexing. The Java exporter rejects relative references outside a selected span; a future slicer must preserve a crossing row using committed state or a coupled window, not silently discard it.

Carry position, horizontal stored velocity, Y/vy, ground, previous horizontal collision and facing at the seam. Additional sprint/input/effect state is needed when recompiling real input rows. candidate.resume contains only vertical/ground/collision fields; X/Z and horizontal velocity are separate trajectory arrays. There is a scalar vertical seam regression, not a general optimized-route splice test. Validate the assembled trajectory as well as individual windows.

Endpoint objective alone can hide different intermediate paths and exit velocities. The user expects most splices at good stopping points, with little far-future velocity dependence; retain a small set of alternatives where a difficult seam warrants it. Optimize mode matters for comparing input patterns. Compare objective quality only for identical physical models, constraints and time budgets; caps/inertia changes are not necessarily nested relaxations.

## Tests and expected results

Standard-library tests and actual-worker tests are deliberately separate:

```sh
python3 -m unittest discover -s standalone/ipopt/tests -v

PKC_IPOPT_PYTHON="$PWD/.venv-ipopt/bin/python" \
  python3 -m unittest discover -s standalone/ipopt/tests -v
```

Without PKC_IPOPT_PYTHON, eight actual-worker methods are skipped. With it, all 47 tests passed on the clean baseline in about 24 seconds. Coverage includes analytic objective optima and cap-branch switching, contradictory rows returning unknown, automatic/explicit inertia, warm-start recovery, scalar collision/cap/vertical timing, resume state, process deadlines, cancellation, seed retention, rejected fake success and CLI capture attachment.

| Fixture | Purpose | Typical last measured result |
| --- | --- | --- |
| real11-200.json.gz | Original prescribed walls/inertia, no ladder/Y attachment | X 737.5142588007, about .63 s. |
| real11-400.json.gz | Same original model over 400 inputs | X 691.4017522467, about 4.11 s. |
| real11-200-ladders.json.gz | Caps and captured vertical replay | X 737.5142588030, about .67 s. |
| real11-400-ladders.json.gz | Caps and captured vertical replay | X 691.5810209172, about 3.67 s. |
| real11-400-warm-yaws.json.gz | Prior no-inertia and inertial seeds | Warm-start regression data, not a problem request. |
| real11-ladder-evidence.json.gz | Reduced recorded states/rows | Reproduces first-400 ladder/Y attachment without an external capture. |

Times are local measurements, not guarantees. All modeled constraints and conditional contacts are validated at 1e-7; do not loosen them to make a benchmark pass. Exact captured Y/vy/ground agreement through 400 was checked. This is still one route with overlapping spans; broaden known-feasible, known-impossible and near-boundary cases before making reliability claims. The very long original route has a recorded constraint/model mismatch around states 651/652; do not use it as an unquestioned exact feasible oracle.

For a changed numerical model or objective policy, report first-feasible time, final objective, row violation, unknown outcomes and phase diagnostics. Keep any claimed impossibility proof separate from local IPOPT status. Archived bounds/relaxation experiments can be consulted without copying both tools trees into the clean branch.

## Optional captured-TAS export in your worktree

```sh
./gradlew -I standalone/ipopt/export.gradle :core:exportStandaloneIpopt \
  -PipoptCapture=/absolute/path/to/captured-tas.json \
  -PipoptTicks=200 -PipoptOutput=/tmp/ipopt-autonomous-problem.json
```

Optional properties: ipoptStart is zero-based, ipoptInputs is KEEP or FORCE_45, and ipoptSprint is DERIVE/ALWAYS/NEVER. Add `-Dorg.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` on this machine if the repository's Windows Java-home setting prevents startup. The task compiles the core/test fixture reader and Java 8-compatible exporter, not a Minecraft client. Never run runClient against an already running Minecraft instance.

The original source capture used locally is `/Users/arvindmurty/Downloads/LinkcraftII_Competition_01/real/11.json`, SHA-256 af5d2d1bc6e0ab466f1b6cf309f3a26e76e4a63765ac8b15ff02fb4ad55d28bf. Treat it as read-only. The committed fixtures are sufficient for standard testing. Exporting 400 inputs on clean upstream produced all 341 rows and 34 horizontal contacts with exactly the saved original physical hash 295b76ca7ee3f215fcd9714660119c0552ae951ac0e9f2f57b152da7ea97f0b3.

For Java/integration changes follow AGENTS.md. The last clean-base core fast gate passed: 849 tests, zero failures/errors, two skipped. The archived experimental branch had three unrelated failures; those are not failures of the clean baseline. Run native slow/very-slow tiers when the relevant native solver or pipeline stages are changed, rather than treating a standalone Python run as that gate.

## Suggested first autonomous-agent checks

Run the two fixture commands and the full standalone suite from your own worktree. Build a small controller that evaluates an explicitly compiled candidate and records feasible/unknown/error, objective, exit state, elapsed time and problem identity. Then exercise a genuinely different input candidate through correct coefficient compilation. Keep recorded ground/event assumptions visible. For minimal-information planning, list which initial state, target geometry and permitted input choices are truly required, and which schedules can be inferred through integration replay. This gives a measurable interface before broad input enumeration or elaborate scheduling.
