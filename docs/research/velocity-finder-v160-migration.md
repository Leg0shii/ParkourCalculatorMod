# Velocity Finder → v1.6.0 migration & feature handoff

Executable plan for porting the **velocity finder** (the `VelocityFinder` core service plus
the `VelocityMapWidget` 2D/3D heatmap) from the experimental branch onto the clean `v1.6.0`
line, dropping the experimental cruft, and adding four new capabilities. Written so an agent
can execute it step by step without breaking anything that currently works.

Every factual claim below (signatures, line numbers, "exists on v1.6.0?") was verified against
both branches with `git`. Re-verify before editing — line numbers drift.

---

## 0. TL;DR

- The velocity finder lives on `claude/exciting-turing-y97kgx` (the source). `v1.6.0`
  (`fa6d17d`) is the clean target. **Both forked from `main` (`5bff20b`)** — so the apparent
  "removals" on the experimental branch (pitch, solve budget, constraint visualization) are
  just v1.6.0 features the experimental branch *predates*. **The migration must be ADDITIVE on
  top of v1.6.0 — never a wholesale copy of the experimental branch's shared files, or it
  silently reverts shipped v1.6.0 features.**
- **Keep:** `VelocityFinder`, `LandingPad`, `VelocityMapWidget`, plus 4 tiny additive edits to
  shared files (`AngleSolverEngine`, `SaveIO`, `AngleSolverWindow`, `Application`) and a
  verbatim copy of `ClosedFormSolve` (identical base on both branches).
- **Drop:** the entire **run-up finder** (`RunupFinder`, `AnchoredRunupFinder`,
  `VelocityBandTarget`, `Footprint`) and its Application wiring
  (`RunupSearch`, `RunupApplyResult`, `buildAnchoredRunupFinder`, `applyRunup`,
  `findAndApplyRunup`, `findAndApplyBestVelocity`), all debug logging
  (`dumpVelocityApply`, `keyStr`, `DEBUG_VELOCITY_APPLY`, `DebugFlags` additions,
  `PlaybackController` debug edits, loader `SelfTest`s), and the run-up tests/docs.
  **Verified: the run-up entry points are reachable only from loader `SelfTest` harnesses —
  never from shipping UI or keybinds — so dropping them breaks no user-facing path.**
- Add: **highlight (0,0)**, **temp-apply trajectory** (recoverable, auto-save-safe),
  **remove the "start tick has no jump" warning**, **enter velocity range (square + center)**.

---

## 1. Branch topology (verified)

```
origin/main      5bff20b ── merge base of both branches
   ├── origin/v1.6.0  fa6d17d   (TARGET: curated PR merges #145/#143/#140/#166/#173 …)
   └── claude/exciting-turing-y97kgx 692029a  (SOURCE: velocity finder + run-up experiments)
```

`v1.6.0` features the port must coexist with (added over `main`): user-tunable solve budget
(#173), in-world landing-constraint visualization (#145), configurable Tick Info (#143),
per-tick **pitch** + configurable columns + mouse buttons (#100/#101), block→landing-constraint
keybind (#115), editing start attributes (#166), Open-dialog sub-folders (#108).

Do the migration on a **new branch cut from `origin/v1.6.0`** (e.g. `feat/velocity-finder`).
Do **not** commit the port to `v1.6.0` directly and do **not** push to `main`/`v1.6.0`.

---

## 2. What the velocity finder is (the keep set)

Reachable from the shipping UI: Angle Solver window → **"Velocity band"** section →
**"Find velocities"** → a worker sweeps the relative initial-velocity (vx,vz) plane and fills a
heatmap; clicking a landing cell applies it. The map opens in a separate **"Velocity Map"**
window with **2D** (heatmap + contour + marquee-refine + zoom/pan) and **3D** (orbit surface)
views.

Data/coupling flow (all problem coupling enters the widget through suppliers/consumer, so the
widget is self-contained):

```
AngleSolverWindow ── owns ──▶ VelocityMapWidget(finderFactory, gridSupplier, onApply,
                                                markerV0, anchorWarning, threads)
Application wires the lambdas:
  finderFactory = buildVelocityFinder()      → builds a VelocityFinder bound to the current jump
  gridSupplier  = velocityGrid()             → search rectangle (centered on current entry vel)
  onApply       = applyVelocityCandidate(c)  → stage start pos/vel/yaw + lock solved yaws, replay
  markerV0      = currentEntryVelocity()      → "you are here" dot
  anchorWarning = velocityAnchorWarning()     → ⚠ REMOVE (feature 3)

VelocityFinder ──uses──▶ AngleSolverEngine (template solve + debug masks),
                         ClosedFormSolve.optimizeRobust / optimizeRobustGraded (fast path),
                         ExactJumpModel/ForwardModel/ForwardPath/JumpSpec/JumpConstraint/
                         JumpPhysicsInputs/Angles, BoxController/InputData/TickState/Vec3dCore
LandingPad.derive(...) ── tightens the landing pad box from the landing-tick X/Z constraints
                          (used by Application.landPadBounds → buildVelocityFinder)
```

### Keep / port

| File | Action | Why / notes |
|---|---|---|
| `core/.../anglesolver/velocity/VelocityFinder.java` | **copy verbatim** | All deps exist on v1.6.0 after the two edits below. 5-arg `AngleSolverEngine` ctor, `TickState` ctor, `AngleSolverState` getters all match. |
| `core/.../anglesolver/velocity/LandingPad.java` | **copy verbatim** | Pure utility; only depends on `anglesolver.Constraint` (exists on v1.6.0). Absent from v1.6.0. |
| `core/.../ui/anglesolver/VelocityMapWidget.java` | **copy, then add features** | No dependency on any v1.6.0-changed type except `ThemeManager` (compatible). |
| `core/.../anglesolver/solver/ClosedFormSolve.java` | **copy verbatim from source** | `git diff origin/main origin/v1.6.0` for this file is **empty** → v1.6.0's copy == the source's base. The source adds `Result` + `optimizeRobustGraded` (+`optimizeRobust` if not already present); change is additive and self-contained. |

### Adapt (tiny additive edits onto v1.6.0's versions — see §3)

`AngleSolverEngine.java`, `SaveIO.java`, `AngleSolverWindow.java`, `Application.java`.

### Drop entirely

`RunupFinder.java`, `AnchoredRunupFinder.java`, `VelocityBandTarget.java`, `Footprint.java`
(run-up finder — none reachable from shipping UI; `VelocityBandTarget` only implements
`RunupFinder.TargetBand`, `Footprint` only feeds the run-up search). Plus all the run-up wiring
and debug listed in §0, the run-up tests, and the run-up research docs.

---

## 3. The four adaptation points (verified diffs)

These are the only shared files both branches touched. Re-apply the source's **additions** onto
v1.6.0's version; do not overwrite.

### 3.1 `AngleSolverEngine.java` — add debug mask accessors (purely additive)

v1.6.0 already has `Phys ph = buildPhys(startTick, numTicks);` (≈ line 237) and a `Phys` with
`force45Mask`/`strafeMask`. Add, mirroring the source:

```java
// near lastSpecDebug():
private volatile boolean[] lastForce45MaskDebug;
private volatile boolean[] lastStrafeMaskDebug;
public boolean[] lastForce45MaskDebug() { return lastForce45MaskDebug; }
public boolean[] lastStrafeMaskDebug()  { return lastStrafeMaskDebug; }

// immediately after `Phys ph = buildPhys(startTick, numTicks);`:
lastForce45MaskDebug = ph.force45Mask;
lastStrafeMaskDebug  = ph.strafeMask;
```

`VelocityFinder` reads these in `template()`/`evaluateViaEngine()`. Without them it won't
compile. (`lastSpecDebug()` already exists on v1.6.0.)

### 3.2 `SaveIO.java` — add `snapshotJson`, threading v1.6.0's `startPitch`

v1.6.0's `buildFile`/`save` take an extra `float startPitch`. Port the helper with pitch:

```java
public static String snapshotJson(FileSystemSaveStore store, InputData inputData, Vec3dCore startPos,
                                  Vec3dCore startVel, float startYaw, float startPitch,
                                  AngleSolverState angleSolver, List<TickState> states) {
    SaveFile file = buildFile(store, inputData, startPos, startVel, startYaw, startPitch, angleSolver, states, false);
    return new GsonBuilder().setPrettyPrinting().create().toJson(file);
}
```

### 3.3 `AngleSolverWindow.java` — host the widget (additive)

Add the field `private final VelocityMapWidget velocityMap;`, the `velocityExpanded` flag, the
5th constructor parameter (assign it), `if (velocityMap != null) velocityMap.renderWindow(ThemeManager.uiScale());`
as the first line of `render(...)`, and after `renderApplyModal();`:

```java
if (velocityMap != null) {
    ThemeManager.sectionSpacing();
    velocityExpanded = sectionToggle("Velocity band", "velband", velocityExpanded, scale);
    if (velocityExpanded) velocityMap.render(scale);
}
```

Keep v1.6.0's existing body (no custom-budget UI on v1.6.0 — leave it as is).

### 3.4 `Application.java` — wire the widget, drop run-up & debug

Port onto v1.6.0's `Application` (which has `startDragController`, a `constraintSelection`
field, and `new AngleSolverTable(state, settings, selection, constraintSelection, inputData::size)`
— leave all that intact). Concretely:

- Add imports for `VelocityFinder`, `LandingPad`, `VelocityMapWidget` only (**not** the run-up
  classes).
- In `setupUi()`, build the widget and pass it as the **new 5th arg** to `AngleSolverWindow`
  (v1.6.0 currently calls the 4-arg ctor at ≈ line 137):
  ```java
  VelocityMapWidget velocityMap = new VelocityMapWidget(
      this::buildVelocityFinder, this::velocityGrid,
      this::applyVelocityCandidate, this::currentEntryVelocity,
      Math.max(2, Runtime.getRuntime().availableProcessors()));   // anchorWarning arg removed — feature 3
  AngleSolverWindow angleSolverWindow = new AngleSolverWindow(
      angleSolverState, settings, inputData::size, angleSolverEngine, velocityMap);
  ```
- Port these methods verbatim except where noted: `buildVelocityFinder`, `landPadBounds`,
  `baseLandBox`, `velocityGrid`, `currentEntryVelocity`, `applyVelocityCandidate`,
  `realizeForce45`, and the `velocitySnapshotJson` field.
  - In `buildVelocityFinder`, the `SaveIO.snapshotJson(...)` call must pass
    **`runner.getStartPitch()`** (v1.6.0 has it) in the new pitch position.
- **Do not port:** `velocityAnchorWarning` (feature 3), `dumpVelocityApply`, `keyStr`,
  `DEBUG_VELOCITY_APPLY`, `RUNUP_GROUND_SLIP`, `RunupSearch`, `RunupApplyResult`,
  `buildAnchoredRunupFinder`, `applyRunup`, `findAndApplyRunup`, `findAndApplyBestVelocity`.
- Drop the `if (DEBUG_VELOCITY_APPLY) dumpVelocityApply(...)` line at the end of
  `applyVelocityCandidate`.

### Do NOT touch (leave v1.6.0's versions untouched)

`DebugFlags.java`, `PlaybackController.java`, all loader files (`SelfTest`, `build.gradle`,
`SimulatorEntity`, `Forge8PlaybackBridge`), `AngleSolverState`, `SaveFile`, `InputRow`,
`BoxController`, `BoxSelectController`, `AngleSolverTable`, `Vec3dCore`. The source's diffs to
these are either run-up/debug scaffolding (drop) or are the source predating v1.6.0 features
(carrying them over would revert pitch / solve budget / constraint visualization).

---

## 4. Not-DRY / refactoring observations (do as you port, keep PRs reviewable)

1. **Duplicated pad-wall construction.** `VelocityFinder.evaluateFast` inlines the same four
   `padWall(...)` constraints that `withPadWalls(...)` already builds. Replace the inline block
   with `List<JumpConstraint> cons = withPadWalls(tmpl.constraints);`. One behavior, one place.
2. **Apply-from-snapshot is duplicated.** `applyVelocityCandidate` and the dropped `applyRunup`
   shared ~25 lines of "parse snapshot → rebuild rows from start tick → lock yaws". With run-up
   gone this collapses to one method; extract `rebuildRowsFromSnapshot(...)` only if the new
   temp-apply path (feature 2) reuses it (it will — see §5.2).
3. **`Candidate` is a 12-field telescoping constructor.** Leave as-is for the port (changing it
   is churn with regression risk), but it's the obvious future cleanup (builder / record).
4. **Magic colors/feature constants in `VelocityMapWidget`** are local `static final` — fine;
   if you touch them for feature 1/4, name them rather than adding more literals.
5. **`velocityGrid()` radius/step are magic numbers** (`0.25`, `0.02`). Feature 4 ("enter
   velocity range") naturally turns these into user-controllable state — fold the constants into
   that state rather than leaving both.
6. **`drawZeroAxes` vs the new origin highlight (feature 1)** must not double-draw — see §5.1.

---

## 5. New features (designs + exact touchpoints)

All four are additive to `VelocityMapWidget` / `Application` / `SaveController`. None touch the
solver core. Add a focused unit/render test for each where practical.

### 5.1 Highlight (0,0)

**Goal:** make the origin visually unmistakable, beyond the current faint grey zero-axes.

- `VelocityMapWidget.drawZeroAxes(...)` (2D) already draws faint lines at vx=0 / vz=0. Add a
  distinct origin glyph: a small ring + center dot (and optional "0,0" label) drawn at
  `vxToScreen(0)`, `vzToScreen(0)` only when the origin is within the canvas. Use a dedicated
  color constant so it reads apart from the white "you are here" marker (`drawMarker`).
- 3D: in `draw3D(...)`, after `drawCubeFrame`, project (0,0) through the existing normalization
  (`(0 - vxMid)/halfX`, `(0 - vzMid)/halfZ`) and draw the same glyph at the base plane; clamp
  like `drawMarker3D` so it stays in the cube.
- Guard: skip if origin is outside `[vxLo,vxHi]×[vzLo,vzHi]`. Keep it cosmetic — no input
  handling. This is the lowest-risk feature; do it first to validate the build pipeline.

### 5.2 Temp-apply trajectory (recoverable; auto-save must not persist it)

**Goal:** clicking a velocity cell applies it as a *temporary* trajectory; the original is one
click away; **auto-save never writes the temp state**; a button reapplies the initial trajectory.

Home the snapshot/restore + suppression in **`SaveController`** (it already owns `inputData`,
`runner`, `angleSolver`, `boxController`, `currentName`, `dirty`, and the `save()`/`load()`
serialization — so it can snapshot and restore exactly what auto-save would write):

```java
// SaveController
private String preTempSnapshotJson;     // the initial trajectory, captured before the first temp apply
private boolean tempActive;

public boolean isTempActive() { return tempActive; }
public boolean isAutoSaveSuppressed() { return tempActive; }

/** Capture the live trajectory once, before the first temp apply. */
public void beginTempTrajectory() {
    if (tempActive) return;                       // already captured; keep the ORIGINAL
    preTempSnapshotJson = SaveIO.snapshotJson(store, inputData,
        runner.getStartPosition(), runner.getStartVelocity(), runner.getStartYaw(),
        runner.getStartPitch(), angleSolver, boxController != null ? boxController.getStates() : null);
    tempActive = true;
}

/** Restore the captured initial trajectory and resume normal auto-save. */
public void restoreInitialTrajectory() {
    if (!tempActive || preTempSnapshotJson == null) return;
    SaveFile f = SaveIO.parseSafe(preTempSnapshotJson);
    if (f != null) {
        if (solverEngine != null) solverEngine.onProblemReplaced();
        SaveIO.applyRowsTo(f, inputData);
        SaveIO.applyAngleSolverTo(f, angleSolver);
        runner.invalidate();
        runner.setStartPosition(SaveIO.posOf(f.start));
        runner.setStartVelocity(SaveIO.velOf(f.start));
        runner.setStartYaw(f.start.yaw);
        // also restore pitch if the v1.6.0 Start carries it
        retriggerSimulation.run();
    }
    clearTempTrajectory();
}

/** Accept the temp trajectory as the new baseline (drop the snapshot, resume auto-save). */
public void clearTempTrajectory() { tempActive = false; preTempSnapshotJson = null; }

/** Persist the LIVE (temp) trajectory to a NEW save file without switching the active save:
 *  currentName / dirty / tempActive are untouched, so auto-save still treats the initial
 *  trajectory as current and stays suppressed. This is "Save As" minus the switch. */
public Result<String> saveCopyAs(String rawName) {
    if (store == null) return Result.failure("Save store not initialized.");
    List<TickState> states = boxController != null ? boxController.getStates() : null;
    boolean fullDebug = settings != null && settings.saveDebugValues;
    return SaveIO.save(store, rawName, inputData, runner.getStartPosition(), runner.getStartVelocity(),
        runner.getStartYaw(), runner.getStartPitch(), angleSolver, states, fullDebug);
    // NOTE: deliberately does NOT set currentName/dirty=false (that is what save() does to "switch").
}
```

Wiring:
- **Application.applyVelocityCandidate**: call `saveController.beginTempTrajectory()` as the very
  first line (before mutating start state / rows). Everything else stays — it already rebuilds
  from `velocitySnapshotJson` so repeated clicks remain idempotent.
- **Auto-save gate** — one line in `FileMenu.tickAutoSave()` (v1.6.0 ≈ line 286):
  ```java
  if (!settings.autoSave) return;
  if (controller.isAutoSaveSuppressed()) return;       // ← add: never persist a temp trajectory
  if (controller.currentName() == null || !controller.isDirty()) return;
  ```
  Auto-save pauses while temp is active and the dirty flag is preserved, so once the user
  restores (or commits) the trajectory, auto-save resumes and persists the *correct* state.
- **Default behavior (decided):** every velocity-map cell click is a **temp apply** — auto-save
  pauses and the original stays recoverable. Provide an explicit **"Keep"** to commit the temp
  trajectory as the new baseline.
- **UI**: in `AngleSolverWindow`'s "Velocity band" section (and/or the "Velocity Map" window
  toolbar), when `saveController.isTempActive()`, show a banner ("Temp trajectory applied —
  auto-save paused") plus three actions:
  - **"Reapply initial trajectory"** → `restoreInitialTrajectory()` (revert + resume auto-save).
  - **"Keep"** → `clearTempTrajectory()` (commit temp as the new baseline; auto-save resumes and
    will persist it to the *current* save).
  - **"Save copy as…"** → prompts for a name and calls `saveController.saveCopyAs(name)` — writes
    the temp trajectory to a **new** file **without switching** the active save (the current save
    stays the initial trajectory; auto-save stays suppressed). Reuse FileMenu's existing
    name-input modal pattern for the prompt; guard against overwriting an existing name the same
    way `FileMenu` does.
  Expose these via small callbacks added to the `VelocityMapWidget` ctor (same supplier pattern)
  or render them in the window from `Application` state — prefer the supplier pattern to keep the
  widget self-contained.
- **Scope decision (decided):** only **auto-save** is gated while temp is active. Manual save is
  not blocked; instead, the **"Save copy as… (no switch)"** button is the intended, non-destructive
  way to persist a temp result — it never disturbs the current save or switches to the new one.
- **Lifecycle:** `restoreInitialTrajectory`/`clearTempTrajectory` must also fire on `load`,
  `newSession`, and `onWorldChange` (clear temp state so a freshly loaded save isn't considered
  "temp"). Add `clearTempTrajectory()` to those paths.

### 5.3 Remove the "start tick has no jump" warning

- Drop `Application.velocityAnchorWarning()` and stop passing it to the widget ctor (§3.4).
- In `VelocityMapWidget`: remove the `anchorWarning` field + ctor param, and delete the block in
  `render(...)` that does `anchorWarning.get()` / `textColored(...)`. No other code reads it.
- Note: this is **not** the same as the Angle Solver's `longSpanWarning` (the "Nt span" notice)
  or the sprint-derive tooltip — leave those alone.

### 5.4 Enter velocity range (draws a square and centers)

**Goal:** type a velocity rectangle; the view frames it (centers + zooms) and draws a square
there, replacing the implicit auto-grid for that interaction.

- Add a small input group to the **Velocity Map** window toolbar (next to the 2D/3D + Contour
  controls in `renderWindow`): four `ImFloat` fields `vxLo,vxHi,vzLo,vzHi` (or
  center+half-extent — pick one and label it) + an **"Apply range"** button. Locale-safe parsing
  (match how the Angle Solver parses constraint values).
- On apply:
  1. Normalize (`lo<hi`, reject zero/negative span).
  2. **Center + frame:** set `centerVx/centerVz` to the rect center and `pxPerUnit` to fit the
     rect with margin (reuse the `initView` math against the entered span, not the grid span).
  3. **Draw the square:** reuse the refine-rect path — `startRefine(vxLo,vxHi,vzLo,vzHi)` already
     samples a fine grid over a rectangle and `drawRefineOverlay` draws its bordered square. That
     gives "draws a square" + content for free. If you don't want the fine sweep, add a
     lightweight `enteredRect` overlay drawn like `drawRefineOverlay`'s border.
  4. The existing `maybeResample(...)` will refill the heatmap for the new viewport
     automatically (it re-sweeps when the visible vx/vz window changes enough).
- Keep it 2D-only initially (the entry is a vx/vz rectangle). Fold `velocityGrid()`'s magic
  `radius/step` into this state if you want the typed range to also seed the initial sweep
  (optional; the auto-grid default still works if the user never types a range).

---

## 6. Safety / regression strategy ("nothing breaks")

The design *is* the safety: the port is additive on v1.6.0, the velocity finder is MC-free and
reaches the rest of the app only through the supplier/consumer seam, and the only shared-file
edits are the four small insertions in §3. Beyond that:

1. **Branch off v1.6.0**, never push to `v1.6.0`/`main`. One PR for the port (§2–3), then one
   PR per feature (§5) so each is independently reviewable/revertible.
2. **Build matrix after every phase** — all three loaders + core compile, since the seam touches
   shared `core`:
   ```
   ./gradlew :core:test
   ./gradlew :loader-fabric-1.21.10:build :loader-forge-1.8.9:build :loader-forge-1.12.2:build
   ```
   (`gradle.properties` pins JDK 21 via `org.gradle.java.home`; ensure a 17+ JDK is available.)
3. **Port the velocity tests, drop the run-up tests.** From the source, bring over the tests that
   exercise the *kept* code and its capture resources:
   - keep: `J008VelocityFinderTest`, `J008VelocityFieldTest`, `Aab93VelocityApplyTest`,
     `LandingPadTest`, `J008BandSweepTest`/`J022BandSweepTest`, `ClosedFormGradedTest`,
     `ClosedFormProbeTest`, plus `src/test/resources/captures/aab93d57*.json`.
   - drop: `J008RunupFinderTest`, `J008AnchoredRunupFinderTest` (run-up).
   Confirm the kept tests reference only kept symbols; fix any that transitively touch run-up.
   These tests are the behavioral lock that "currently working" stays working.
4. **Add a regression test per new feature** where unit-testable: temp-apply (assert
   `isAutoSaveSuppressed()` true after a temp apply; `tickAutoSave()` writes nothing while
   suppressed; `restoreInitialTrajectory()` restores rows + start vel byte-for-byte and resumes
   auto-save). Extend the existing v1.6.0 `AutoSaveTest` rig — it already drives `tickAutoSave`.
5. **Manual verification in-game** (see `docs/CODING_GUIDE.md` / the run skill): set up a jump,
   Solve, open Velocity band → Find velocities, confirm 2D heatmap + contour + marquee refine +
   zoom/pan, 3D orbit, origin highlight, click-to-apply, the temp banner + restore button, the
   typed range framing, and that no warning text appears. Confirm auto-save leaves the file at
   the initial trajectory while temp is active and writes the restored state afterward.
6. **Keep diffs surgical** in shared files — only the §3 insertions. If a shared file shows more
   than the documented additions in the PR diff, you've over-copied; revert to v1.6.0's version
   and re-apply just the additions.

---

## 7. Ordered task list for the executor

> Checkpoint = "compiles + `:core:test` green + three loaders build". Commit at each ✅.

**Phase A — port the finder (no behavior change vs source for kept code)**
1. Branch `feat/velocity-finder` from `origin/v1.6.0`.
2. Copy `ClosedFormSolve.java` from the source (verbatim). ✅ build only.
3. Add `VelocityFinder.java` + `LandingPad.java` (verbatim). Won't compile until step 4.
4. `AngleSolverEngine.java`: add the 2 fields + 2 accessors + 2 assignments (§3.1). ✅
5. `SaveIO.java`: add `snapshotJson` with `startPitch` (§3.2). ✅
6. Add `VelocityMapWidget.java` (verbatim, including the `anchorWarning` param for now).
7. `AngleSolverWindow.java`: add the 5th ctor param + section (§3.3).
8. `Application.java`: imports + `setupUi` wiring + port the kept methods; **omit** all run-up &
   debug members (§3.4). ✅ Checkpoint — finder works exactly as on the source branch.
9. Port the kept velocity tests + capture resources (§6.3). ✅
10. Remove the run-up research docs from the copied set; keep this handoff doc.

**Phase B — feature 3 (remove warning), smallest behavior change**
11. Delete `velocityAnchorWarning`, drop the widget's `anchorWarning` field/param/render block,
    drop the ctor arg in `setupUi` (§5.3). ✅

**Phase C — feature 1 (highlight 0,0)**
12. Add the origin glyph in 2D + 3D (§5.1); ensure it doesn't double-read with the marker. ✅

**Phase D — feature 4 (enter velocity range)**
13. Add the range inputs + Apply (center/frame + square via refine overlay) (§5.4). ✅

**Phase E — feature 2 (temp apply), most plumbing**
14. `SaveController`: snapshot/restore/suppress API + `saveCopyAs(name)` (no-switch copy) (§5.2).
15. `FileMenu.tickAutoSave`: add the `isAutoSaveSuppressed()` gate (§5.2).
16. `Application.applyVelocityCandidate`: call `beginTempTrajectory()` first; clear temp on
    `load`/`newSession`/`onWorldChange`.
17. UI: temp banner + "Reapply initial trajectory" + "Keep" + "Save copy as… (no switch)"
    (reuse FileMenu's name-input modal) via supplier callbacks. Default = always temp.
18. Tests: temp-apply / auto-save-suppression / restore + `saveCopyAs` writes a new file while
    `currentName`/`dirty`/`tempActive` stay unchanged (§6.4). ✅ Final checkpoint + manual §6.5.

**Cleanup**
19. Apply the §4 DRY fixes that fall out naturally (pad-wall dedup; fold `velocityGrid` magic
    numbers into feature-4 state). Re-run the build matrix.
