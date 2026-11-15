# Run-ticks prototype: status and remaining work

2026-10-26. **Prototyped.** Active orchestration work now moves to the [block solver](BLOCK_SOLVER_PLAN.md). This status replaces the earlier implementation plan and its proposed replay/compilation handoff. All work remains in the isolated `codex/run-ticks-planning` worktree.

## Scope and ownership

There are three layers: orchestration, numerical solver, and replay. This module belongs to orchestration. It constructs schedules, compiles numerical requests using existing model helpers, calls the solver and selects a validated continuous-model candidate. The numerical solver optimizes facings under a fixed request. Replay is separate and is not a prerequisite or current interface deliverable.

The run-ticks problem assumes an initial state, ordered landing/support blocks, supplied air times and transition/contact conditions, an input policy, and run-count limits. Its job is to choose added supported ground inputs and solve the resulting horizontal trajectory. Deriving air time, ceiling contacts, stair boosts and obstacle corridors belongs to the block solver.

The user's stair-boost abstraction is a supplied 2-input jump profile with extra positional constraints, just as an ordinary flat jump has a 12-input profile. This is distinct from the one-input grounded, non-jump `step` event retained in the Linkcraft fixture. That event is an accounting/schedule representation, not an implementation of step-assist physics.

## Implemented

- Immutable route preparation, per-transition run limits, copied candidate arrays, and forward schedule generation. The original route and live TAS are not edited.
- Zero-run first-failure search, one-tick increments, run-support probes, backtracking, bounded retries and fallback guesses. No binary search or exact-count monotonicity assumption.
- Every prefix is solved from the original initial state with momentum carried across landings. Earlier facings remain free rather than freezing one exit velocity. There are no F rows.
- Existing `ConstraintDeriver.deriveFootprint` and `SupportOverlap` helpers derive/check support regions through the Java adapter. A landing region can differ from the region available for running.
- Existing `JumpPhysicsInputs` and `JumpLinearModel` compile takeoff, ground and air acceleration/drag with the 1.8.9 float-friction path. The standalone solver applies automatic per-axis .005 inertia.
- Supplied jump, run-off and grounded-step schedules; pre/post-horizontal landing checks; relative X/Z constraints and block-entry conditions for reduced hops and stepped landings. Run-offs leave the block without jumping and use the legacy serialized kind `walk_off`.
- Conservative interval contradictions before IPOPT, independent scalar validation, and strict support/exit predicates after solving. Numerical row tolerance alone cannot admit an unsupported departure.
- Global/per-probe budgets, cancellation, optional improvement below an incumbent, unknown-attempt bookkeeping, compact diagnostics and self-contained accepted requests.

For transition i, arrival state Li, added runs ri and supplied duration Ai:

```text
departure Ji = Li + ri
next arrival Li+1 = Ji + Ai
```

Input t produces state t+1. Run inputs use the incoming ground regime. A normal landing-producing input is airborne and tests support at the preceding horizontal state. Supplied post-horizontal step contacts use their declared state instead. The next jump uses the previously established grounded flag even if the horizontal movement ended beyond the support; do not add a takeoff-support condition that contradicts this normal movement order.

## Evidence

| Case | Accepted allocation or result |
| --- | --- |
| Two ordinary jumps | `[0,0]` |
| A required initial run tick | `[1]` |
| Backtracking to improve the preceding arrival | `[2,0]` |
| Two jumps requiring turning | `[0,0]`, facings adjusted by IPOPT |
| Linkcraft 11 j5-j9 | Zero added runs; useful smoke case but insufficient as the main benchmark |
| Linkcraft 11 j0-j16 | 12 ground run inputs, 130 inputs total, zero final scalar violation |

The full Linkcraft cold search took **80.63 seconds**, **333 probes** and **24 backtracks**. It found 11 optional delays plus one mandatory grounded-step input. Per-jump run inputs are j10=4, j13=3, j14=2, j15=3, all others zero. The run-off is preserved separately. The recording also has 12 ground run inputs, distributed differently.

The search used neither recorded facings nor recorded run counts as a seed. The fixture supplies air times, landing/contact phases and center regions; no extra authored scalar rule table is copied. It records two full-grid support inferences and a wider coplanar run strip inferred from ground samples. These are declared fixture assumptions, not discovered world geometry.

The request, independently accepted solution, source identity, geometry assumptions and trace are retained in [experiments/linkcraft11-j0-j16](experiments/linkcraft11-j0-j16/README.md). All 30 autonomy tests at the run-ticks checkpoint passed with the Java compiler and actual IPOPT runtime.

The user expects 12 to be the minimum for this path. The implementation reports a best-found witness, not a mathematical certificate: cheaper local attempts can remain unknown. That distinction does not block the prototype milestone.

## Remaining work, deferred

1. Broader continuous-model benchmarks and performance measurement on longer routes. Profile work before changing the search or adding caching.
2. Generalize candidate compilation only when required profiles exist: movement effects, special surfaces, wall/contact schedules and ladders need correct retiming and model compilation. These were outside the original ordered-route adapter. The collision-box block solver now compiles wall/ceiling contacts and one ladder/vine interaction with retiming; see [LADDER_STRATEGY_PLAN.md](LADDER_STRATEGY_PLAN.md). Effects and special surfaces remain unsupported.
3. Better search quality if needed: improve incumbent margins, revisit cheaper unknowns with larger budgets, or retain alternatives for long-route coupling. No minimum proof is required for the prototype.
4. Stable external route/transition identities and an agreed orchestration API when there is a caller beyond the current Python/CLI prototype. Existing relative rows and event mappings suffice for the current cases.
5. Mod UI, replay-ready export and game validation are separate future work. They are not the next task and do not hold this milestone open.

Ceilings, stair/step boosts, deriving air time from heights, obstacle-side/crossing choices and generation of their positional conditions are tracked in the block-solver plan, not as unfinished run-ticks search.

## Verification and navigation

The numerical worker is unchanged. No native `core/` implementation was modified. Existing unrelated native-suite failures were recorded separately; this prototype does not attempt to repair them.

```sh
PKC_RUN_TICKS_COMPILER="$PWD/standalone/autonomy/build/compiler.json" \
PKC_IPOPT_PYTHON="$PWD/.venv-ipopt/bin/python" \
python3 -m unittest discover -s standalone/autonomy/tests -v
```

- [README.md](README.md): setup, route format and search behavior.
- `route.py`: schedules, support rows, entry/exit constraints and guesses.
- `search.py`: allocation search, compilation, solver invocation and acceptance.
- `compiler.py` and `java/CompileRunTicksPhysics.java`: existing Java model helpers.
- [BLOCK_SOLVER_PLAN.md](BLOCK_SOLVER_PLAN.md): current next layer of orchestration.
- [Historical block-solver post-mortem](../../docs/research/block-solver.md): prior failure modes.
