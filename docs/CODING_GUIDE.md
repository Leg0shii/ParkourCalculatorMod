# Coding Guide

How code is split across modules and how to decide where a new file goes. `CLAUDE.md` is the day-to-day map (and a "where to find what" table); this is the rulebook for the multi-module split. When in doubt, pick the simpler rule.


## Modules at a glance

```
core/                  Java 8. ImGui-only UI, input data, the angle solver, value types, ports.
                       No Minecraft, Fabric, Forge, or LWJGL imports.
forge-core/            Java 8. Shared by both Forge loaders. No Minecraft or Forge imports.
loader-fabric/         Java 25. Fabric (Loom, LWJGL 3), tracks the latest MC. MC-touching code. Source under src/client/java.
loader-forge-1.8.9/    Java 8.  Forge (Unimined, LWJGL 2). MC-touching code.
loader-forge-1.12.2/   Java 8.  Forge (Unimined, LWJGL 2). MC-touching code.
```

One root Gradle build covers all five subprojects; each pins its own toolchain. Build tasks and JDK requirements are in `CLAUDE.md` § Build & Run.


## Where does X go?

Top-down, first rule that fits wins:

1. Imports `net.minecraft.*`, `net.fabricmc.*`, `net.minecraftforge.*`, or `org.lwjgl.*`? It goes in a loader module.
2. An entry point (`@ClientModInitializer` / `@Mod`), a Mixin, or anything wiring lifecycle/events to a loader? Loader module.
3. An ImGui window, UI state, or in-table selection/drag/keyboard behavior? `core/ui/` (solver UI under `core/ui/anglesolver/`).
4. A data model or save type (e.g. `InputData`, `InputRow`, `SaveFile`)? `core/ui/` for the live model, `core/save/` for persistence.
5. A shared value type or pure math (e.g. `Vec3dCore`, `TickState`, solver `Constants`)? The owning feature package: `core/sim/` for movement value types, `core/anglesolver/` for solver math.
6. Something core needs but only the loader can answer (player position, block lookup, window size, real-MC simulation)? A port in `core/.../ports/`, implemented loader-side. See § Ports.

If none fit, you probably do not need the file. Re-check.


## Rules per module

### `core/`

- **Java 8 source** (`options.release = 8` on the JDK 21 daemon). No `var`, `record`, `getFirst`, `Math.clamp(int,int,int)`, switch expressions, or other Java 9+ API/syntax. The Forge loaders run on JDK 8 and will reject anything newer at runtime.
- **No `net.minecraft.*`, `net.fabricmc.*`, `net.minecraftforge.*`, or `org.lwjgl.*` imports.** The build passes either way (ImGui compiles), but the Forge 1.8.9 jar explodes at class-load. Enforce in review.
- **imgui-java is `compileOnly`, pinned to 1.86.12.** Every loader bundles that exact runtime, so core's compile version must match. 1.86.12 is the floor (its macOS native is universal x86_64 + arm64; 1.86.11 was x86_64-only and crashed Apple Silicon) and the ceiling (the koxx12-dev LWJGL 2 shim was built against 1.86.11's native surface, of which 1.86.12 is a strict superset; higher versions throw `NoSuchMethodError`). Do not change it. See `CLAUDE.md` § Don't.
- **No logging framework.** Forge 1.8.9 ships no SLF4J. Core surfaces errors via exceptions or return values for the loader to log; verbose debug dumps go through `System.out` gated behind `DebugFlags`.
- **No MC-filesystem I/O.** The save/config location is a loader concern (run-dir layout differs per version). Core takes a `Path` or stream from the loader.

### `loader-fabric/`

- **Java 25** (auto-provisioned toolchain). Use modern features where they help. Source lives under `src/client/java` (not `src/main`).
- **All MC-touching code lives here:** `SimulatorEntity`, `FabricSimulator` (implements the `Simulator` port), the world/HUD overlay renderers (implement `BoxRenderer`), `FabricPlaybackBridge`, `ImGuiImpl`, every Mixin, and the `FabricParkourCalculator` entry point. (`BoxController` is core, not here.)
- **Bundles imgui-java 1.86.12** (binding + LWJGL 3 backend + natives) via Loom `include`. Only loader on LWJGL 3.
- **Mixins** are declared in `parkourcalculator.client.mixins.json`. A new mixin must be added there or it silently will not apply.

### `loader-forge-1.8.9/` and `loader-forge-1.12.2/`

The two are intentional duplicates: 1.8.9 and 1.12.2 have incompatible MC APIs. Only MC-free code is shared, in `forge-core/`.

- **Java 8**, required by the MC runtime.
- **Log4j 2 for logging** (`org.apache.logging.log4j`), not SLF4J.
- **imgui-java pinned to 1.86.12.** The LWJGL 2 shim was built against 1.86.11's native surface, of which 1.86.12 is a strict superset (only the unused `ImGuiKnobs` extension was added); 1.90.0 throws `NoSuchMethodError` on the first frame.
- **Render via `RenderTickEvent.END`**, which fires while `framebufferMc` is bound. Do not bind FBO 0; drawing into the bound `framebufferMc` is what makes ImGui visible after MC's later blit.
- **Hotkeys via Forge `KeyBinding`** (`ClientRegistry.registerKeyBinding` + `kb.isPressed()` drained in a per-frame `while`). Polling `Keyboard.isKeyDown` works but never shows in the Controls menu.

### `forge-core/`

Shared by both Forge loaders, **no Minecraft or Forge imports** (it only knows ImGui, the shim, and core's `OverlayManager` / `Settings`). Packages:

- `lwjgl2/`: the ImGui-on-LWJGL 2 host (`Lwjgl2ImGuiHost`, `ImGuiGl3Compat`, `Lwjgl2InputState`); exposes a per-frame `renderFrame(...)` and applies `Settings.SCALE` at init.
- `sim/`: `PlayerSprintMachine` (the pure-Java sprint state machine both Forge `SimulatorEntity`s share) and `PlaybackMoveState`. Lives here, not in `core/`, because its input field set is shaped after `EntityPlayerSP`'s 1.8/1.12 read set.
- `render/`: `CountingBoxRenderer` (shared box-draw counting).
- `io/`: `OsFilePicker` (the `FilePickerPort` impl both Forge loaders use).
- **Java 8 toolchain, no `--release`.** JDK 8's javac has no `--release`, so this module uses an actual JDK 8 toolchain. Core can use `--release 8` only because it builds on the JDK 21 daemon.

A symmetrical `fabric-lwjgl3-common/` is not worth it until a second LWJGL 3 loader exists. Today the Fabric bootstrap stays inline.


## Ports (port-and-adapter pattern)

When core needs something only the loader has, define a small interface in `core/.../ports/` and let the loader implement it. The live ports are `Simulator`, `MinecraftAccess`, `BoxRenderer`, `PlaybackBridge`, and `FilePickerPort`.

Interfaces use core's own value types, never Minecraft ones:

```java
// core/.../ports/Simulator.java
public interface Simulator {
    void resetToStart();
    void applyInput(InputRow row);
    void tick();
    Vec3dCore getCurrentPosition();
    boolean isCurrentOnGround();
    // ...
}
```

`FabricSimulator` (Fabric) and the Forge `SimulatorEntity` adapters implement it, translating MC types to `Vec3dCore` / `InputRow` at the boundary. Core never sees an MC type.

Rules:
- **Define the type once in `core/.../ports/`** using core value types (`Vec3dCore`, `InputRow`, ...). If MC types are handy at the call site, the loader translates; core cannot import them.
- **Wire implementations explicitly** in the loader entry point (static field, constructor, or a `setX(...)` call). No reflection or service locator at this scale.
- **One interface per concern.** Do not lump everything the loader provides into one mega-interface.
- **Defer the abstraction.** Do not add a port until some core code actually calls it. Speculative ports are dead weight.

We use this manual pattern rather than Architectury's `@ExpectPlatform` because Architectury solves a different problem. It shares code between loaders on the *same* Minecraft version (for example Fabric and NeoForge on 1.21); our variation is across Minecraft *eras* (1.8.9, 1.12.2, 26.2), each with a single loader, where the APIs differ by generation (`Vec3` vs `Vec3d`, LWJGL 2 vs 3, whole render rewrites) and no unified API bridges them. Architectury's API and Loom also do not support 1.8.9 or 1.12.2 at all, and adopting Architectury Loom would displace Unimined, the toolchain those versions rely on. Revisit only if a second loader ever ships on the same MC version (for example NeoForge 26.2 beside Fabric 26.2).


## What we deliberately don't do

- **No Lombok.** It does not fit the classes that look like its use case. `Vec3dCore` exposes `public final` fields read directly in the byte-exact hot loop, so `@Value` (which makes fields private and generates getters) would force a repo-wide rewrite for no gain; `InputRow` carries an `EnumSet`, a static id counter, and save-ordinal constraints it cannot generate. The realistic payoff is deleting a handful of `equals` and trivial getters. Against that, it would be the build's first annotation processor, one that leans on internal javac APIs and has historically needed updates to track new JDK releases, across a JDK 8 plus JDK 21 toolchain split. When the Java 8 floor is dropped, `record`s give the same value-type win natively with no dependency.
- **No Spongepowered Mixin on the Forge loaders.** Forge 1.8.9 predates its wide adoption; use a Forge coremod (`IFMLLoadingPlugin`) if bytecode patching is ever needed there. Fabric uses Mixin because Loom requires it. Do not try to unify the two.
- **No Architectury** (it cannot cover the 1.8.9 / 1.12.2 loaders and would displace Unimined; see § Ports) **or Cloth Config** (core already has its own ImGui settings surface).
- **No reflection for cross-module dispatch.** Wire ports explicitly. Reaching for reflection means the port API is too coarse.
- **No `core/` to loader dependency.** Loaders depend on core, never the reverse. Verify with `./gradlew :core:dependencies`.


## Updating this guide

When you add a module or port, or deliberately break a "don't do" rule, update this file in the same PR. It drifts from reality fast otherwise. The closest external analog to grow toward is MPK Mod 2 (static MC-facade ports in a `common/` module, one impl class per loader).
