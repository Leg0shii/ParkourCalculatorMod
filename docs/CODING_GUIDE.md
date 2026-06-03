# Coding Guide

How code is organized across modules and how to decide where a new file goes.
CLAUDE.md is the day-to-day reference; this guide is the rulebook for the
multi-module split. When in doubt, prefer the simpler rule.


## Modules at a glance

```
core/                       Java 8 source. No Minecraft, no LWJGL, no loader APIs.
                            ImGui-only UI, data, selection, ports, util.
forge-core/                 Java 8. Shared code for both Forge loaders.
                            lwjgl2/ holds the LWJGL 2 + ImGui bootstrap; sim/ holds
                            EntityPlayerSP-shaped algorithm code (sprint machine).
                            NO Forge, NO Minecraft imports.
loader-fabric-1.21.10/      Java 21. Fabric + LWJGL 3. MC-touching code lives here.
loader-forge-1.8.9/         Java 8.  Forge + LWJGL 2. MC-touching code lives here.
loader-forge-1.12.2/        Java 8.  Forge + LWJGL 2. MC-touching code lives here.
```

One root Gradle build covers all four. Each subproject pins its own toolchain.
See CLAUDE.md § Build & Run for tasks and JDK requirements.


## Where does X go?

Use this decision tree top-down. The first rule that fits wins.

```
Does the code import net.minecraft.*, net.fabricmc.*, net.minecraftforge.*,
or any org.lwjgl.* package?
├─ YES → it goes in a loader/ module. Stop.
└─ NO ↓

Is it the @ClientModInitializer / @Mod entry, a Mixin, a coremod, or anything
that wires lifecycle / events to a specific loader?
├─ YES → loader/. Stop.
└─ NO ↓

Is it an ImGui window, UI state, selection logic, drag/keyboard handling
inside the table, or anything else that's pure UI behaviour?
├─ YES → core/ui/. Stop.
└─ NO ↓

Is it a data model, JSON schema, or storage type (e.g. InputRow, InputData)?
├─ YES → core/ui/ (today) or core/data/ (when we split that out). Stop.
└─ NO ↓

Is it a port interface (something core/ defines and the loader implements,
e.g. Simulator, MinecraftAccess)?
├─ YES → core/.../ports/. The implementation goes in loader/. See § Ports.
└─ NO ↓

Is it a pure utility (math, formatting, no I/O)?
├─ YES → core/util/. Stop.
└─ NO ↓

You're probably wrong about needing the file. Re-check.
```


## Rules per module

### `core/`

- **Java 8 source.** No `var`, `record`, `getFirst`, `Math.clamp(int,int,int)`,
  switch expressions, or other Java 9+ features. The Forge 1.8.9 / 1.12.2
  loaders run on JDK 8 and will reject anything newer at runtime.
- **No `net.minecraft.*`, `net.fabricmc.*`, `net.minecraftforge.*`, `org.lwjgl.*`
  imports.** Build will pass if you do (ImGui compiles fine), but the loader-
  Forge-1.8.9 jar will class-load at runtime and explode. Enforce in review.
- **imgui-java compileOnly only.** Each loader bundles the runtime. All loaders
  bundle the **same version** (1.86.11 today, set by the koxx12-dev shim's expected
  API), so core/'s compileOnly version matches every runtime. Do not bump it above
  what the loaders bundle. See CLAUDE.md § Don't.
- **SLF4J is unavailable.** Forge 1.8.9 doesn't ship SLF4J. core/ uses
  `java.util.logging` if it needs to log at all, or, preferably, exposes
  errors via thrown exceptions / return values for the loader to log.
- **No file I/O against MC's filesystem.** The config/save location is a
  loader concern (run dir layout differs across versions). core/ takes a
  `Path` or `InputStream` from the loader.

### `loader-fabric-1.21.10/`

- **Java 21.** Use modern features where they help.
- **All MC-touching code lives here:** `SimulatorEntity`, `MovementSimulator`,
  `BoxController`, world rendering, all Mixins, `ImGuiImpl`, the
  `@ClientModInitializer`.
- **Bundles imgui-java 1.86.11** (binding + lwjgl3 backend + native libs)
  via Loom's `include`. This is the only loader on LWJGL 3 today.
- **Mixins** are declared in `parkourcalculator.client.mixins.json`. New
  mixins MUST be added there or they won't apply.

### `loader-forge-1.8.9/` and `loader-forge-1.12.2/`

- **Java 8.** Required by MC 1.8.9 / 1.12.2 runtime.
- **Use Log4j 2 for logging** (`org.apache.logging.log4j.{LogManager,Logger}`).
  Not SLF4J.
- **imgui-java pinned to 1.86.11.** The koxx12-dev LWJGL 2 shim was built
  against this version; 1.90.0 throws `NoSuchMethodError` at first frame.
- **Render via `RenderTickEvent.END`**: fires inside MC's runGameLoop while
  `framebufferMc` is bound. Do NOT explicitly bind FBO 0: drawing into the
  currently-bound framebufferMc is what makes ImGui visible after MC's
  later `framebufferRender` blit. See the memory file on this for the
  full diagnosis.
- **Forge `KeyBinding` for hotkeys.** Polling `Keyboard.isKeyDown` works
  but won't surface in the Controls menu. Use
  `ClientRegistry.registerKeyBinding(...)` + `kb.isPressed()` in a `while`
  drain loop per frame.

### `forge-core/`

Shared code for both Forge loaders, organised into two subpackages:

- `lwjgl2/`: `Lwjgl2ImGuiHost` wraps `ImGuiLwjgl2` + `ImGuiGL3`, exposes
  `renderFrame(int displayWidth, int displayHeight)` for the call site to
  invoke per frame, and applies `Settings.SCALE` at init time.
- `sim/`: `PlayerSprintMachine`, the pure-Java sprint state machine the
  Forge 1.8.9 and 1.12.2 SimulatorEntities share. Lives here, not in
  `core/`, because its `Inputs` field set is shaped after EntityPlayerSP's
  1.8/1.12 read-set and that scope is honest to advertise at the module
  level rather than smuggle into the universal core.

- **No Forge or Minecraft imports.** Forge `@SubscribeEvent` wiring and
  the `Minecraft.getMinecraft()` call to fetch `displayWidth/Height` stay
  per-loader. The shared module only knows about ImGui, the shim, and
  core/'s `OverlayManager` / `Settings`.
- **No natives, no binding runtime.** The loaders bundle the imgui-java
  binding + natives; this module only `compileOnly`'s the binding API
  and `api`'s the shim so consumers transitively pick it up.
- **Java 8 toolchain, no `--release` flag.** JDK 8's javac doesn't have
  `--release`; setting it errors at "Java compilation initialization."
  `core/` uses `--release 8` because it runs on the JDK 21 daemon;
  this module uses an actual JDK 8 toolchain instead.

Symmetrical extraction for `fabric-lwjgl3-common/` only makes sense when
a second LWJGL 3 loader exists (NeoForge 1.21, Fabric 1.20.x, …). Today
the Fabric bootstrap stays inline.


## Ports (port-and-adapter pattern)

When core/ needs information that only the loader has (current player
position, block at world coords, MC's window size), define a small
interface in `core/.../ports/` and let the loader implement it.

### Pattern

```java
// core/src/main/java/.../core/ports/PlayerStateProvider.java
public interface PlayerStateProvider {
    Vec3d getPosition();         // Use a core-defined Vec3d, NOT net.minecraft
    float getYaw();
    boolean isGrounded();
}
```

```java
// loader-fabric-1.21.10/.../FabricPlayerStateProvider.java
public class FabricPlayerStateProvider implements PlayerStateProvider {
    @Override
    public Vec3d getPosition() {
        var p = MinecraftClient.getInstance().player;
        return new Vec3d(p.getX(), p.getY(), p.getZ());
    }
    // ...
}
```

```java
// In the loader entry point:
PlayerStateProvider provider = new FabricPlayerStateProvider();
core.registerPlayerStateProvider(provider);  // or pass via constructor
```

### Rules

- **Define the type once in `core/.../ports/`.** Use core's own value types
  (a small `Vec3d`, `BlockPos`, etc.) in the interface signature; never
  Minecraft types. If MC's types are convenient at the call site, the
  loader can translate; core cannot import them.
- **No reflection-based discovery.** Wire the implementation explicitly
  in the loader's entry point (`onInitializeClient`, `@Mod.EventHandler init`).
  Static fields / constructor injection / a `setProvider(...)` call are all
  fine. Don't build a service-locator at this scale.
- **One interface per concern.** Don't lump "everything the loader can
  provide" into one mega-interface; the test boundary becomes useless.
- **Defer the abstraction.** Don't define a port until at least one piece
  of core code actually calls it. Speculative ports become dead weight.

### Why this shape and not Architectury / @ExpectPlatform

Architectury's `@ExpectPlatform` solves the same problem with classpath-
discovered impl classes. It's clean for projects that already use it and
need lots of platforms. For our scale, the manual port pattern wins on:
fewer build-tool dependencies (no Architectury plugin), easier debugging
(impl class is named and import-visible), and no annotation-processing
magic. If we ever cross five+ loaders or need NeoForge + Forge sharing,
revisit.


## What we deliberately don't do

- **No Lombok.** The desktop ParkourCalculator uses it heavily; the mod
  doesn't.
- **No Mixin library (Spongepowered) on the Forge loaders.** Forge 1.8.9
  predates Mixin's wide adoption. If we need bytecode patching there,
  it's a Forge `IFMLLoadingPlugin` coremod. The Fabric loader uses
  Spongepowered Mixin (because Loom requires it). Don't try to unify the
  two; they target different MC eras.
- **No Architectury, no Cloth Config dependency.** Both pull in big
  toolchains for marginal benefit at our scale.
- **No reflection / `Class.forName` for cross-module dispatch.** Wire ports
  explicitly. If you find yourself reaching for reflection, the port API
  is probably too coarse.
- **No `core/` → loader/ dependencies in build.gradle.** Loaders depend on
  core, never the other way around. Verify with `./gradlew :core:dependencies`.


## Prior art and references

- **MPK Mod 2** (`../MPK2`): the closest analog. `common/` holds GUI
  components, screens, events, util, and `compatibility/MCClasses/*`
  static facades with inner `Interface` ports. Each `forge-X/` or
  `fabric-X/` has a single `FunctionCompatibility` class implementing
  every Interface, plus an `EventListener` that dispatches Forge/Fabric
  events into the common `API.Events` static methods. This is the pattern
  we're growing into.
- **Architectury API**: multi-loader abstraction via `@ExpectPlatform`
  annotation + classpath impl discovery. Mature, widely used for Fabric +
  Forge + NeoForge. Worth understanding even if we don't adopt it.
- **Sodium / Embeddium**: separate forks for Fabric vs Forge rather than
  a shared module. The opposite trade-off (no abstraction tax, but no
  code sharing either). Mentioned for completeness; not our model.


## Updating this guide

When adding a new module, a new port, or breaking one of the "don't do"
rules deliberately, update this file in the same PR. The guide drifts
from reality fast otherwise.
