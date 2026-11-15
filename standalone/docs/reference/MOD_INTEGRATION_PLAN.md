# Minecraft and mod integration plan

Prepared 2026-10-26 on feature/ipopt-mod-integration, starting from handoff commit 4089be9. This is a detailed implementation plan, not a report that the proposed adapters exist. Autonomous input/route search is owned by the other agent. This agent owns the implementation that captures Minecraft state, compiles fixed-input problems, invokes the existing solver, realizes output rows, obtains game replay feedback and connects those operations to the mod.

## Current decision point: compare replay backends before implementing

The user asked to think through priority and effort before starting a standalone replay/certificate implementation. The deployment and replay-first proposals below are alternatives under discussion, not an instruction to start a full headless Minecraft runtime or physics port immediately. No replay implementation has been started.

The user's constraint-certificate alternative is promising: run exact movement locally and validate enough geometric/event conditions to prove that Minecraft would choose the same transitions in a complete static scene. This can avoid a complete game runtime. The difficult work is sound event coverage, including omitted collisions, support/landing timing, step alternatives, ladder activation, friction selection and relevant input/state history. Support a declared profile/domain rather than claim every block interaction is covered.

Current recommendation: prioritize the backend-independent exact-angle realizer and TAS artifact validation/export. Use the existing Minecraft simulator as the first reference/validation backend, then add local contract checking against recorded reference traces. Failed-constraint and first-event-mismatch diagnostics can be implemented on that existing backend too; they do not require a standalone engine. Fast simulator replay is not real-time player playback and does not have to wait 50 ms per simulated input.

Rough engineering estimates for one version, static geometry and existing TAS templates, not measured implementation durations: basic existing-simulator validation/report/export about 1-2 focused days; intelligent nearby-angle repair adds about 2-4 days and is shared by either backend. A local contract checker for a tightly restricted ordinary-block model may add several days; extending it to well-tested wall/ladder/step and transition coverage is closer to 1-2 additional weeks, with uncertainty around edge cases. A small headless-runtime or contract-checker feasibility spike can reduce uncertainty before committing to either. Full multi-version/all-block guarantees are outside these estimates.

## Updated deployment requirement: standalone local replay

The user prefers the entire solving/replay workflow to work locally without opening Minecraft or running the mod. The existing standalone numerical solver already meets that deployment requirement; the current loader simulator wrapper does not, because it obtains a client/player/world from Minecraft.getMinecraft/McClient (or the Fabric equivalent). Using those wrappers unchanged would require a live game. This dependency is in the host adapter, not evidence that the movement calculations inherently require rendering or user playback.

The next feasibility spike should establish a headless Java replay backend for the initial 1.8.9 profile using actual Minecraft movement/collision code and a small supplied block-state world. It should initialize only the required runtime/registries/world/entity path, accept explicit start/input/scene data and emit states through a local CLI. Prove floor, wall and ladder ticks without a game window before designing the broader service. The Forge SimulatorEntity source has no client or rendering imports, but reusing it still requires runtime/world setup and compatible transformed access; no successful headless bootstrap has been demonstrated yet.

Keep this MC-dependent harness separate from the MC-free core. Reuse exact angle-cell helpers, input semantics and SaveIO on the JVM. A bounded scene/block list can supply world data initially; local save-region loading can follow. The scene needs block identity/state/neighbor information where vanilla collision or activation depends on it, not merely a list of solid cubes. Client-only APIs, camera playback, a network server and mod UI must not be prerequisites for the initial headless service.

A faithful standalone port is the fallback if actual-runtime bootstrap is impractical. That path must add exact world collision/support/step/ladder behavior and validate it against game traces; the current collision-free ExactJumpModel and test-only MediumWorldFakeSimulator are not substitutes for that work. Mod-hosted replay becomes an optional later cross-check/integration adapter. The earlier mod-hosted phase descriptions below are reference material, superseded where they assume a running client.

## Updated priority from the user: replay first

The user clarified that orchestration is primarily owned by the other agent, the solving layer is largely built, and the solver's input interface is still undecided. Our immediate work should therefore start after an existing solve: intelligently realize nearby exact Minecraft angle cells, replay the resulting inputs in the real simulator, check the original constraints and export a TAS JSON accepted by the mod. This takes priority over the compiler/process/backend-selection order below. Those phases remain reference material for later integration, not prerequisites to replay development.

The immediate service accepts a completed solution plus effective input rows, complete initial state, constraints/objective and world/profile context. These data can initially come from a source TAS/template and solved-window indices, independently of the future orchestration interface. A bare array of angles cannot reconstruct keys, initial Y/resume state or geometry. Start with whole-template output retaining the unchanged prefix; a standalone mid-route slice needs a separate resume-equivalence check.

Immediate implementation checkpoints:

1. Reuse exact angle-cell enumeration and TAS float-yaw semantics to build a constraint-aware realizer. A deterministic float cast or nearest bucket is only a baseline. Probe nearby movement/boost joint cells, check F/dF rows, and repair an initially infeasible snapped path before objective polish. Keep input keys fixed unless an explicitly permitted strafe-side realization is part of the request.
2. Reuse Simulator/SimulationRunner and loader SimulatorEntity for actual world replay, isolated from the live TAS/player. ExactJumpModel can cheaply score collision-free intervals but is not a wall/ladder/landing world oracle. Reuse checkpointed tail replay with validated state restoration for expensive probes.
3. Reuse SaveIO/SaveFile and effective key/yaw realization to write the final TAS. Reload that exact JSON, replay the reloaded rows, evaluate the original constraints and record final achieved objective/residuals. The exported artifact, rather than a pre-serialization angle array, is the acceptance target.
4. Benchmark deterministic conversion versus cell probing on matching inputs/scenes. Report max constraint violation, objective change, trajectory drift, changed discrete events and runtime separately. The user's approximately 1e-4 reconstruction target is a measurement goal; preserve the requested constraint/equality semantics and do not claim that nearby cells always yield a witness.

The detailed source investigation and the 61-test helper verification are recorded in FINDINGS.md under the replay-first investigation. No replay adapter has been implemented yet, and no live Minecraft validation is claimed.

## Recommendation and first milestone

Build a complete short-window round trip before adding route search, elaborate world extraction or broad UI configuration:

1. Capture the current document/window and actual simulator start state.
2. Compile the exact effective inputs, constraints and initial event schedule into the existing version-1 request.
3. Invoke the existing Python parent/validator and IPOPT worker on a background thread.
4. Realize its continuous facings and effective keys into copied TAS rows.
5. Replay those rows through the existing Minecraft simulator and report constraint/event differences.
6. Apply through normal TAS edit/undo/simulation wiring when the user uses the existing Apply interaction.

First milestone: a manually specified approximately 50-150-input window can complete this path in the mod, using a local configured Python runtime, with no need to save a debug capture or invoke Gradle to solve it. The 200/400 Real11 fixtures remain numerical regression/stress cases. The game replay can expose a mismatch; that must be reported honestly rather than hidden by weakening constraints. Later feedback phases improve agreement. Initial acceptance targets Minecraft 1.8.9, matching the implemented vertical profile and the source route.

Keep the current Python numerical implementation as the single source. Retain the native solver as a selectable backend. Local setup is sufficient; do not bundle Python/CasADi, redesign input search or require a multi-version physics implementation to reach this milestone.

## Ownership and dependency boundary

| Integration agent supplies | Autonomous agent supplies |
| --- | --- |
| Immutable start/input/environment snapshots and compilation into solver coefficients. | Candidate key/jump/sprint patterns, goals and useful subproblem windows. |
| Optional portable scene/surface/contact data, with provenance and supported-profile information. | Policies for choosing discrete hypotheses, ranking candidates and deciding which alternatives matter. |
| Bounded fixed-input solve and structured continuous acceptance. | Search budgets across candidates, caching and conservative pruning policies. |
| Effective TAS row realization and Minecraft replay reports. | Route composition, overlap selection and alternative exit-state retention. |
| Deterministic recompile from replayed events and integration hooks. | Higher-level decisions to retry a pattern, change inputs or advance the route. |

Both sides use the existing compiled request/result as the numerical boundary. A new snapshot/feedback wrapper can describe raw inputs, scene identity and replay status without changing version-1 equations. Deterministic event refresh can be an optional fixed-input service; it must not silently choose new key patterns. The autonomous agent can keep using the existing fixtures/API while integration develops.

Publish one small example snapshot, its compiled request and its replay report when the boundary lands. Avoid making the autonomy code depend on ImGui state, a live entity or the other worktree's filesystem. Headless use can operate on a previously exported snapshot, while the live mod obtains the same data from its ports. Live cross-process orchestration/daemon protocols are not prerequisites.

To avoid blocking the autonomous agent, deliver the first portable scene/start snapshot alongside the initial compiler/process work, before full UI integration. A bounded static example with loaded-region coverage, collision shapes, climbable information, movement properties and a clear game profile is already useful. Expand its fidelity with replay feedback later; do not make that agent wait until all integration phases finish.

## Existing code to reuse

| Existing source | Relevant behavior |
| --- | --- |
| core Application | Owns data, engine and simulator wiring, undo, dirty tracking, world replacement and runSimulation. |
| AngleSolverEngine | Compiles scenarios/constraints, snapshots jobs, runs background work, polls outcomes and writes facings/effective Force-45 rows. Its private Plan and native validation must not be assumed to fit continuous IPOPT results. |
| JumpPhysicsInputs, JumpLinearModel | Existing coefficient construction; avoid duplicating Minecraft acceleration formulas in Python or a second Java adapter. |
| standalone exporter | Already reproduces the 400-input physical fixture hash. Reuse its transformations without depending on test Fixtures in production. |
| Simulator, SimulationRunner | Actual Minecraft movement, checkpoints, initial resume state and incremental replay. Reuse actual game movement rather than constructing another mod-side physics engine. |
| TickState | Position, stored velocity, ground/collision, effective move inputs and surface samples. |
| StartResumeState | Ground/contact, sprint machine, held inputs and other game resume fields. Preserve the existing state rather than reducing it to XYZ alone. |
| MinecraftAccess | Collision boxes, climbable queries, readiness/chunk state and the existing correct-thread dispatch path. |
| standalone solve.py / solver.py | Existing validated parent, deadlines, cancellation, retained incumbents and atomic result files. |

Important indexing: state t is before input t. Surface/movement samples produced by input t are stored on TickState t+1. Application.applyPathSurfaceState already uses that offset. Its incoming velocity, ground and previous horizontal collision belong to state t. Keep raw recorded state and per-input samples distinct in the integration snapshot.

The old feature/ipopt-local spike at 97f545a is reference material only. It embeds an older worker and predates current inertia/cap/incumbent behavior. Do not merge it wholesale or create a second maintained numerical implementation.

## Phase 1: reusable compiler and process client

### Deliverables

Introduce a small Java 8-compatible adapter under core/.../anglesolver/ipopt. Working responsibilities are request compilation, process invocation and parsed results; exact class names can follow the implementation. All objects handed to a background task must own copied arrays/rows rather than retaining live editor or Minecraft references.

Extract the exporter's request construction into a shared compiler callable with an explicit compiled scenario/spec, initial state, effective-input metadata and prescribed events. Make the headless exporter delegate to it where practical. Remove the production dependency on the test-only Fixtures helper. Expose a narrow supported compilation path from the engine or its shared builder instead of using debugBuildSpec as a permanent production entry point. Avoid a large engine refactor or any change to native pipeline ordering.

Compiler requirements:

- Preserve every hard position/facing/relative row, including authored goal walls. Restore any start bounds or first-turn rows consumed by native free-start folding when IPOPT pins its start. Reject unsupported cross-window references instead of dropping them.
- Capture source-to-window indexing, initial facing and full horizontal state. Keep the selected objective fixed; do not accidentally inherit native legal-mode goal-wall removal or fallback behavior.
- Record the effective keys/masks, side choices and input/sprint/effect policies used to derive magnitude/base/friction. This metadata is needed for later output realization.
- Make unsupported physics explicit. Initial 1.8.9 support must not silently label threshold-zero combined-inertia approximations as complete game support. Unsupported teleports, server interactions or effects should be declared outside the initial capability set, not silently ignored.
- Distinguish temporary replay-derived event hypotheses from user-authored hard constraints. Captured Y reference arrays are regression data, not automatically hard requirements on a live solve.

For the first transport, launch the existing Python CLI through ProcessBuilder with an argument list, explicit interpreter and absolute script paths. Use per-job temporary request/result files and the CLI's atomic JSON output. Capture stdout/stderr with bounds and clean up job files. Do not invoke a shell or interpolate command strings. The solver location can be a configured local checkout; distribution/resource extraction can come later.

Invoke the Python parent, not worker.py directly: bypassing the parent would lose current independent validation and incumbent/continuation behavior. Pass the same configured runtime to the parent/worker. Preserve feasible, unknown, error and cancelled outcomes, plus problem identity, objective, violations, timings and diagnostics. A nonzero CLI exit is not by itself an infeasibility proof.

The Python budget should finish before a slightly larger Java process watchdog. Allow bounded result/cleanup time so a validated timeout incumbent is not unnecessarily discarded. Cancellation must also reap the child worker: killing only the CLI parent is insufficient if a worker can be orphaned. Keep this covered by a process-tree lifecycle test on supported launch platforms.

### Acceptance

The shared compiler produces the saved original 400-input physical hash 295b76ca7ee3f215fcd9714660119c0552ae951ac0e9f2f57b152da7ea97f0b3, with all 341 rows and 34 contacts. A Java test invokes the existing solver on a small known optimum and the copied fixture without launching Minecraft. Fake-process tests cover malformed/non-finite output, bad dimensions/identity, missing runtime, unknown, timeout with an incumbent, cancellation, blocked output and cleanup. Java 8 compilation/runtime checks pass.

## Phase 2: live snapshot and job lifecycle

### Deliverables

Capture the current solve range without save/load/debug JSON round trips. The snapshot should contain copied rows/constraints, compile policies, position/velocity/facing, available StartResumeState, a local-to-document tick mapping and initial sampled surfaces/events. If the current cached simulation is stale, obtain a fresh normal simulation before treating it as a schedule seed. A successful historical route is not required: the current simulation is only a hypothesis.

Take Minecraft state on the existing valid client/server execution path. The numerical process sees only immutable data. Do not access world/entity objects from the IPOPT background thread or hold the game thread waiting for optimization. Use polling/scheduling so a numerical worker waiting for replay cannot deadlock with a game thread waiting for that worker.

Add a small IPOPT controller with capture, solve, ready/unknown/error/cancelled states and a main-thread publication point. Reuse the engine's lifecycle pattern, keeping native graph stages intact. Each job carries an identity including document generation, world/session identity, row/constraint/start/policy signature and relevant environment snapshot identity. The Python problemHash alone is insufficient because source/world metadata is excluded from it.

Cancel or invalidate on a newer solve, relevant document edits, load/new document, start-state changes and world replacement. Check identity again before publishing and before applying a result. World time alone is not a useful invalidation rule: a solve spans many ticks. Use relevant scene content/revision and recheck queried blocks when needed. Unloaded or uncaptured terrain remains unknown rather than assumed empty.

Initial event data can come from the existing simulator path, preserving state-t versus sample-t+1 conventions. Ladder entry flags need an actual boundary occupancy sample/predicate, including the initial and final states; a wall hit alone is insufficient. Extend the existing loader/core sample at the narrowest point if it lacks that information. Preserve complete game resume state, even though numerical requests only need a subset. Tapped jumps remain the user contract; no held-jump cooldown search is needed.

Provide an initial portable scene/start export in this phase, early enough for independent autonomous work. Capture a bounded relevant loaded region, version/profile, player dimensions, block collision AABBs, climbable activation information and movement-property data available from the ports; add narrow property queries where actually needed. Preserve missing coverage and unsupported block behavior explicitly. Collision shape and climbable activation are different data. Start/goal locations and an export region do not prescribe a successful route or its inputs. Keep the scene identity separate from the numerical request and record its frame/coordinate convention. A data export does not by itself supply a headless collision engine.

### Acceptance

A live range produces the same numerical request as an equivalent captured/exported state. Starting, cancelling or editing while solving keeps the UI responsive, never mutates TAS rows and never publishes an old job onto a new document/world. Tick-boundary tests include nonzero range starts and ladder entry/exit.

## Phase 3: output realization and isolated Minecraft replay

### Deliverables

Convert candidate facings into copied effective TAS rows before applying anything to the document. Reuse existing absolute/relative yaw writing and Force-45 side realization semantics, but do not pass continuous candidates through a native byte-exact-only success gate. Preserve source rows outside the selected span, pitch and unrelated row metadata. Model yaw is double; TAS/game yaw is float, so retain both the continuous candidate and the realized row values.

Evaluate those copied rows through the existing Minecraft Simulator. Prefer a dedicated scratch simulator/runner where supported; otherwise use a serialized transactional replay with complete checkpoint/start-configuration restoration. Preserve the main runner's path/checkpoint/event caches, user start state, displayed boxes and actual player. Do not temporarily replace the live InputData and hope to restore it after an exception. Candidate evaluation must not teleport the real player or write blocks/world state. Server-paired interactions/dynamic block edits are outside the initial static-environment acceptance scope unless their restoration is explicitly supported.

Replay returns a structured report: actual states, original constraint satisfaction, first divergent tick, expected/actual wall/ground/ladder events, affected friction/move inputs, final game resume state and diagnostic position/velocity differences. Keep these separate:

- continuousStatus: whether the supplied continuous problem passed the independent parent replay;
- replayStatus: passed, failed or not evaluated for realized rows in the current captured environment;
- lifecycle status: ready, stale, cancelled or error.

These names describe separate fields to introduce, not new public meanings for Python's existing status. A failed realized replay does not prove the input pattern impossible. Small arithmetic/yaw quantization differences and changed discrete events need different diagnostics. Keep the 1e-7 continuous acceptance tolerance and existing authored constraint semantics; do not make game acceptance depend on arbitrary full-path equality with a continuous model. A realized game candidate passes only when its own replay satisfies the applicable constraints and supported event checks.

### Acceptance

Tests cover float/relative yaw round trips, KEEP and FORCE_45 keys, strafe side, a nonzero start, wall tangential carry, ladder entry caps and next-tick climbing. Fake Simulator tests prove checkpoint/start/cache isolation on success, failure, cancellation and exceptions. In-game QA on the supported version replays representative air, landing, wall and ladder windows and confirms that document/player state stays unchanged until normal Apply.

## Phase 4: selectable backend and normal Apply

### Deliverables

Add a Local IPOPT selection alongside the existing native solver. Expose only the initial useful controls: configured Python/solver location, total budget and feasibility/optimize mode. Runtime configuration belongs in local mod settings rather than TAS content. Persist a backend choice only if it fits the existing solver setting model and remains backwards compatible; do not add transient process paths to saved TAS files.

The UI should communicate model/replay status, objective, elapsed time and a concise actionable error such as a missing runtime. Unknown remains a failed search, never an infeasibility claim. Keep detailed process and mismatch diagnostics in existing details/logging paths. No graph stages or native certification labels should be repurposed for IPOPT.

Normal Apply checks freshness, creates the normal undo boundary, writes only realized rows in the selected span, marks the document dirty and calls the existing onApplied/onUserChange path so Application.runSimulation executes. Keep existing solver behavior outside the selected backend. The state after Apply must match the rows evaluated in the replay report, or the report is invalidated. Changes in downstream constraints should be reported even if the local span passed.

Python's current CLI cancellation discards the in-flight result. Do not map native Stop and use best to that operation and claim equivalent behavior. The first version can expose cancellation only for IPOPT. If live incumbent/stop-best support is needed, add a thin streaming wrapper around the existing Python solve API and callback, keeping numerical and acceptance code single-sourced. Treat explicit cancel and return-best requests separately.

### Acceptance

The first end-to-end milestone is complete: select IPOPT, solve a current short window, inspect the continuous/replay outcome, Apply through normal document wiring and undo it. Editing, switching worlds, repeated starts and cancelling cannot apply a stale result. Missing runtime and unsupported profiles fail clearly. Build affected jars and perform the required in-game QA; a loader build alone does not establish game correctness.

## Phase 5: event consistency and bounded fixed-input refinement

### Deliverables

Make replay feedback usable both by the manual backend and by autonomy. Given the same keys, derive actual ground/air, wall contact, ladder occupancy and jump eligibility from replay, rebuild affected coefficients/contact/cap data and re-solve within one shared deadline. Preserve user constraints, objective and input choices. The initial service can use a bounded small number of deterministic refreshes; measure before selecting the default limit.

Use actual new Y/ground state. Do not force the original capture's Y/vy reference arrays into production requests. Reaching a finite wall requires the correct height/tangential overlap; a declared step or landing needs support; ladder activation uses the version-correct occupancy rule rather than only intersection with its thin solid shape. Additional collisions must be reported, not only missing prescribed contacts. Preserve the last current-scene replay-accepted incumbent across failed refinement attempts.

First obtain this consistency through the real simulator, which already owns Minecraft collision rules. Expand the portable scene snapshot introduced in phase 2 where these cases expose missing shapes, climbable activation data or movement properties. Capture additions on the correct thread and use them off-thread as immutable values. Static geometry is the initial scope; changing interactables/server effects need explicit later support.

Scene snapshot export is an adapter, not a second complete Minecraft movement engine. The autonomous agent can infer constraints and schedules from that snapshot, while the mod can certify a proposed realized route through actual simulator feedback. Any headless replay implementation or schedule-search policy should be coordinated with that agent to avoid duplicate physics and overlapping ownership.

### Acceptance

Cases intentionally leave a declared ladder, miss a finite wall edge, land on a different support, change a climb tick or introduce an extra collision. The service either refreshes to a replay-consistent candidate or returns an honest unresolved result, never a game-passed label based only on the old event schedule. A seam case transfers full game state correctly. Total budget covers all numerical attempts and validation work; cancellation/stale-world checks remain effective between phases.

## Phase 6: autonomous-facing adapter and broader acceptance

Expose the same capture/compile/solve/realize/replay functions without requiring AngleSolverWindow. Candidate input rows and explicit start state must be accepted as data, so the autonomous agent can call fixed-input solves from its own orchestration and test against exported snapshots. Publish supported capabilities and a complete example with numerical/replay result identities. Return enough mismatch information for the outer agent to decide whether to alter keys, schedules, windows or budgets.

Retain multiple exit-state alternatives only when the autonomous policy asks for them. Full window planning, input enumeration, pruning, route objectives and how little the user supplies remain that agent's work. Do not integrate native long-run window selection into the IPOPT backend merely to provide this service.

Expand validation to independent routes, cold or perturbed inputs, tight feasible/impossible cases, cap/inertia boundaries, nonzero starts and realistic seams. Measure first-feasible time, final objective, model/replay acceptance and unknown rate separately. Compare the same physical request against a larger-budget reference; do not compare different cap/inertia models as objective-quality evidence. Add the standalone Python gate to CI when the integration is ready to merge so the normal Java gate is not the only check.

## Implementation order and commit boundaries

1. Shared compiler, typed request/result handling and headless export parity.
2. Process client and lifecycle tests against the existing Python parent.
3. Immutable live/portable scene/start snapshot, profile capabilities and job invalidation controller. Ship an example to the autonomous agent before waiting for the UI.
4. Effective row realization and isolated simulator replay/report.
5. Backend selector, settings, Apply/undo and game QA for the first milestone.
6. Replay-driven event refresh and portable scene fidelity improvements.
7. Autonomous-facing entry points, cross-agent example and broader acceptance/CI.

Each item can be one or more ordinary commits. No amending/rebasing of the research branch is needed. Keep artifacts and fixtures minimal; store concise progress in FINDINGS.md and use existing archived research links rather than importing benchmark trees.

## Test and validation policy

Run the 47-test standalone suite with an actual worker when the compiler/protocol/solver adapter changes could affect its behavior. For Java changes run core fast tests. AGENTS.md requires the slow tier for changes under core/.../anglesolver, including a new IPOPT adapter, or to model/capture resources. Add the very-slow tier when native certification or pipeline fire/guard ordering can change. Maintain Java 8 compatibility in core and exercise its Java 8 launcher for the process adapter. A protocol-only fake worker should cover failure behavior without expensive numerical solves in every unit test.

The last clean baseline had 47 standalone tests passing and core fast 849 tests with zero failures/errors and two skipped. Its Java exporter exactly matched the saved 400-input physical request. The handoff smoke checks passed from this worktree. Those are baseline evidence, not verification of the integration proposed here.

Do not start a second Minecraft client while one is already running. Build through Stonecutter with the required active-version reset discipline and test the supported target in the user's existing or deliberately selected test session. This plan does not claim in-game QA has happened.

## Explicitly deferred

Automatic input-pattern/route search, replacing native graph strategies, guaranteed global optima/infeasibility, distributed Python/native packaging, every Minecraft profile, held-jump cooldown search, general dynamic/server-world interactions and a full standalone Minecraft clone. These can be revisited when evidence or the autonomous agent requires them; none should block the initial live fixed-input round trip.
