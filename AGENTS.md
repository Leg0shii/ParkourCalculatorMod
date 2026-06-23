# AGENTS.md

Shared guidance for AI coding agents and contributors. This is the canonical guide; `CLAUDE.md` imports it so Claude Code picks it up automatically, and other tools that read `AGENTS.md` get the same content. Navigation reference: where to find what, and the rules that must not break. For deeper context:
- `CONTEXT.md`: domain glossary. Decodes the Minecraft parkour and movement vocabulary (facing, direction, velocity, tier, neo, momentum, byte-exact, etc.). Read it first; almost none of these terms are in an LLM's training data.
- `docs/reference/mcpk/`: in-repo mirror of the Minecraft Parkour Wiki physics (movement formulas, constants, the sine table, collision order, block friction, status effects, tiers). The byte-exact ground truth this tool replicates; if code disagrees with a number there, the code is the bug.
- `docs/VISION.md`: north-star goal (two blocks in, full TAS out), the capability arc, design principles
- `docs/CODING_GUIDE.md`: module rules, where new code goes, port pattern, per-module toolchains
- `docs/research/`: angle-solver design record, ILS global-solve notes
- `CONTRIBUTING.md`: feature definition checklist, Conventional Commits, release-please flow


## Modules

```
core/                  Java 8.  ImGui-only UI/data + the angle solver. No MC, Fabric, Forge, or LWJGL imports.
forge-core/            Java 8.  Shared for both Forge loaders: lwjgl2/ ImGui bootstrap, sim/ sprint machine. No MC imports.
loader-fabric-1.21.10/ Java 21. Fabric (Loom, LWJGL3). MC-touching sim/render/mixins/entry point. Source under src/client/java.
loader-forge-1.8.9/    Java 8.  Forge (Unimined FG2, LWJGL2). MC-touching code.
loader-forge-1.12.2/   Java 8.  Forge (Unimined FG3, LWJGL2). MC-touching code.
```

The two Forge loaders are intentional duplicates: 1.8.9 and 1.12.2 have incompatible MC APIs (`Vec3` vs `Vec3d`, `theWorld/thePlayer` vs `world/player`, `moveEntity` vs `move`, etc.). `forge-core` holds only the MC-free shared parts.


## Core flow

1. **Inputs** (core): `InputOverlay` edits `InputData` (list of `InputRow`, one per tick: W/A/S/D/Jump/Sneak/Sprint/L-click/R-click + yaw/pitch + amplifiers).
2. **Simulation** (loader): the loader's `Simulator` port drives a `SimulatorEntity` (a real MC player subclass) tick by tick, recording positions.
3. **Visualization** (core + loader): `BoxController` (core) stores positions and decides what to draw, then calls the loader's `BoxRenderer` port for the actual GL draw. Start box is drag-to-reposition.

`Application` (core) is the singleton orchestrator that wires inputs, simulation, playback, and the solver. Any change to `InputData` or the start position must retrigger `Application.runSimulation()`. **Don't break this wiring.**


## Where to find what

| Task | Look here |
| --- | --- |
| Orchestration / wiring | `core/.../Application.java` (read first); loader entry points `FabricParkourCalculator`, `Forge8ParkourCalculator`, `Forge12ParkourCalculator` |
| Input model & editing | `core/.../ui/InputData.java`, `InputRow.java`, `InputOverlay.java` |
| Save / load (JSON) | `core/.../save/SaveIO.java` (schema + parse), `SaveFile.java`, `FileSystemSaveStore.java`; mediator `core/.../SaveController.java` |
| UI shell / theming | `core/.../ui/MainWindowOverlay.java`, `OverlayManager.java`, `ui/theme/ThemeManager.java` (Catppuccin Mocha), `Settings.java` |
| Angle solver (core logic) | `core/.../anglesolver/AngleSolverEngine.java` (orchestrator), `AngleSolverState.java` |
| Solver inner loop | `core/.../anglesolver/solver/ExactJumpModel.java` (byte-exact X/Z stepper), `McSineTable.java`, `Constants.java` |
| Solver strategies | `solver/ClosedFormSolve.java` (fast convex), `SolveCore.java` (CMA-ES multistart), `LongRunSolver.java` (multi-jump), `BlockSolver.java` (obstacle avoidance) |
| Velocity finder | `core/.../anglesolver/velocity/VelocityFinder.java` (vx/vz sweep against a pad) |
| Solver UI | `core/.../ui/anglesolver/AngleSolverWindow.java`, `AngleSolverTable.java`, `SolverWidgets.java` |
| Constraint visualization | `core/.../render/ConstraintPlate.java`, `ConstraintShapes.java`; source `core/.../ui/anglesolver/AngleSolverConstraintSource.java` |
| Playback (TAS replay) | `core/.../PlaybackController.java`; loader `FabricPlaybackBridge` and Forge equivalents |
| Ports (core interfaces) | `core/.../ports/`: `MinecraftAccess`, `Simulator`, `BoxRenderer`, `PlaybackBridge`, `FilePickerPort` |
| Simulation (Fabric) | `loader-fabric-1.21.10/.../sim/SimulatorEntity.java`, `FabricSimulator.java`, `SimulatorInput.java` |
| Rendering (Fabric) | `loader-fabric-1.21.10/.../render/FabricWorldOverlayRenderer.java`, `FabricHudOverlayRenderer.java`; ImGui `imgui/ImGuiImpl.java` |
| Mixins (Fabric) | `loader-fabric-1.21.10/.../fabric/mixin/`; registered in `parkourcalculator.client.mixins.json` (Forge uses the FML event bus, no mixins) |

**Tick indexing:** `posX[t]` = position at the *start* of tick `t` (before that tick's inputs); box `k` shows pre-tick state. A constraint on tick `n` affects `posX[n]`; to constrain what tick `n`'s input *produces*, place the constraint on tick `n+1`.


## Build & Run

```bash
./gradlew build                                # everything; output jars to <module>/build/libs/
./gradlew :core:build                          # shared core lib (runs tableStyleCheck)
./gradlew :forge-core:build
./gradlew :loader-fabric-1.21.10:build         # / :runClient   -> pkc-fabric-1.21.10-VERSION.jar
./gradlew :loader-forge-1.8.9:build            # / :runClient   -> pkc-forge-1.8.9-VERSION.jar
./gradlew :loader-forge-1.12.2:build           # / :runClient   -> pkc-forge-1.12.2-VERSION.jar
```

JDK 21 runs the Gradle daemon. `:runClient` auto-switches toolchain: Fabric uses JDK 21, the Forge loaders need a local JDK 8 (Adoptium). Do not run `:runClient` while MC is already open (shared `run/` file + world locks deadlock). Per-module toolchain rules: `docs/CODING_GUIDE.md` § Rules per module.


## Tests

The real gate is `:core:test`. All tests are pure Java in `core/src/test/`, no MC needed, runs in seconds.

```bash
./gradlew :core:test
```

- Folder-driven problem checks: `core/src/test/.../anglesolver/ProblemsTest.java` (parameterized over `problems/solve/` and `problems/closedform/`, sharing captures in `core/src/test/resources/captures/`). Map in `anglesolver/TESTS.md`.
- Bit-exact model regression: `core/src/test/.../anglesolver/ModernStepRegressionTest.java` pins `ExactJumpModel`, `McSineTable`, `Constants` against a recorded 1.21.10 run. A failure means the model drifted; do not edit model code without a green run first.

There is **no** Fabric/Forge test task. The old `loader-fabric-1.21.10:test` + `M3RegressionTest` (spike0) paths are gone; the regression moved to `core/` and was renamed `ModernStepRegressionTest`. `tableStyleCheck` runs on `:core:check`/`build` (CI skips it with `-x tableStyleCheck`); it has a known false positive on `SolverWidgets`.


## Reading MC source (use local decompiled sources, don't fetch from the web)

To check an MC API surface, decompiled body, or local-var name, read the project's local decompiled sources. Web mirrors are often the wrong MC/MCP/yarn snapshot with decompiler-invented var names that won't match what compiles here.

Generate once:

```bash
./gradlew :loader-fabric-1.21.10:genSources :loader-forge-1.8.9:genSources :loader-forge-1.12.2:genSources
```

They appear under:

```
.gradle/unimined/net/minecraft/minecraft/1.8.9/.../mcp-stable-22-1.8.9-searge-1.8.9/...-sources.jar
.gradle/unimined/net/minecraft/minecraft/1.12.2/.../mcp-stable-39-1.12-searge-...-sources.jar
.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-common-<hash>/1.21.10-...-sources.jar
.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-clientOnly-<hash>/1.21.10-...-sources.jar
```

Unzip the relevant `-sources.jar` and grep for a single file (e.g. `EntityPlayerSP.java`, `ClientPlayerEntity.java`). IntelliJ resolves these automatically on Ctrl-Click in any loader module.


## Critical patterns

- **Simulation is the single source of truth.** The loader's `SimulatorEntity` reproduces real MC movement; never approximate it. Divergence from MC is a bug, not an optimization. (The solver's `ExactJumpModel` is a separate byte-exact X/Z replica for the search inner loop, which cannot afford the MC-coupled entity; the entity is used for cheap post-Apply verify.)
- **Core stays Minecraft-free.** No `net.minecraft.*`, `net.fabricmc.*`, `net.minecraftforge.*`, or `org.lwjgl.*` in `core/`. If core needs world data, define a port in `core/.../ports/` and implement it loader-side (see `docs/CODING_GUIDE.md` § Ports).
- **Static wiring.** `Application` and the loader entry points use static fields/methods deliberately; the mod is a singleton and statics avoid passing refs across mixin/event boundaries.
- **Fabric ImGui input routing.** When the overlay is open, `KeyboardMixin` / `MouseMixin` route events to ImGui; an `isUiFocused()` check gates pass-through, and box dragging is disabled while the UI is focused.
- **Fabric mixin lifecycle.** MC hooks go through mixins, not Fabric events (except `ClientTickEvents` for input polling). A new mixin must be added to `parkourcalculator.client.mixins.json` or it silently won't apply. Forge loaders use the FML event bus + `ClientRegistry` keybinds instead.
- **imgui-java is pinned to 1.86.11** everywhere (`core/` compileOnly; loaders `include`/`shade`). The LWJGL2 ImGui shim only exists for this version; bumping it throws `NoSuchMethodError`.


## Don't

- Add server-side code (client-only mod).
- Bypass `SimulatorEntity` with custom physics math.
- Import MC / Fabric / Forge / LWJGL types into `core/`.
- Break the simulation retrigger on input or start-position changes.
- Bump `core/`'s imgui-java compileOnly above 1.86.11.
- Hand-edit `mod_version` in `gradle.properties`. It is bot-managed by release-please via the inline `# x-release-please-version` annotation, which MUST sit on the same line as the version (`mod_version=X.Y.Z # x-release-please-version`); on a preceding line, release-please's generic updater silently skips the file.
- Use em dashes in any writing in this repo (docs, code, commits). Use commas, semicolons, colons, or sentence breaks instead.
- **Add code comments.** Not javadocs, not inline notes, not "why" one-liners, nothing. Write the code only. The sole exceptions are comments already in the file (leave them) and a comment the user explicitly asks for in that request. If a name or structure needs explaining, pick a clearer name instead.
