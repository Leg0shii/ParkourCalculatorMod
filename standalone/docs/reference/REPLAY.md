# Local Minecraft movement replay

Replay runs locally in Java. It needs no Minecraft client, server, world directory or mod installation. The Python IPOPT solver is unchanged; replay is a separate optional consumer of a completed solve and an effective-input TAS template.

The initial profile is Minecraft 1.8.9. It reuses `ExactJumpModel` for float movement arithmetic, the Minecraft sine table, inertia and ladder caps, and `PlayerSprintMachine` for actual row input processing. A movement callback supplies collision results without changing the existing solver's default collision-free path.

The current capability boundary, source map and unported mechanics are listed in [MOVEMENT_PORT.md](MOVEMENT_PORT.md). Collision discovery is implemented for the supported static scene; a complete local replica of every Minecraft movement type is not yet implemented.

## Two sources of contacts

**Declared contacts** reuse an IPOPT problem's horizontal contacts, vertical contacts and ladder flags. They check reach/approach, place the player on the declared plane and zero the blocked velocity component. A wall hit in a ladder sets the upward carry for the next input tick. Missing a declared contact adds a replay violation; a contact is not granted merely because the optimizer requested it. This mode supports source.startTick = 0. It validates the specified event model and does not discover other obstacles.

**Static scenes** discover contacts from supplied collision boxes. They resolve Y, X and Z in Minecraft 1.8.9 order, preserve the moved bounding box between ticks, zero only blocked components, implement both step-up alternatives and the sneak edge guard, derive grounded state and check ladder occupancy from player-center X/Z and bounding-box minimum Y. Ladder caps use pre-movement occupancy; wall-triggered climb carry uses the resulting collision/occupancy. The following tick moves with that stored upward carry and its own pre-movement ladder caps.

Scene replay compares the discovered horizontal contact schedule with the declared schedule. The replay evaluator also checks ground/ladder timing, every original scalar row, declared contact positions/normal velocities, supplied Y/VY references, initial state and objective give-back. A changed schedule is a failed assumption for this fixed-event solve; orchestration can recompile/re-solve it.

## Scene format

```json
{
  "version": 1,
  "profile": "minecraft-1.8.9",
  "bounds": [-10, -10, -10, 10, 10, 10],
  "solids": [
    [-5, 0, -5, 5, 1, 5],
    [1.75, 1, -2, 2, 5, 2]
  ],
  "ladders": [[1, 1, 0], [1, 2, 0]],
  "surfaces": [{"cell": [0, 0, 0], "slipperiness": 0.6}],
  "stepHeight": 0.6
}
```

Boxes are world-space `[minX,minY,minZ,maxX,maxY,maxZ]` collision AABBs. Split multipart shapes into individual boxes. Solids represent inert blocks; ladder cells are non-solid climbable occupancy and can have separate thin collision boxes in `solids`. Ground friction looks up the cell below the player's bounding-box minimum Y and defaults to normal 0.6F. Omitted ladder/surface lists are empty. Step height is 0.6F by default; zero disables stepping. Player size is the legacy 0.6F by 1.8F float-promoted size.

`bounds` declares a region whose geometry is completely supplied, including empty space. Every movement/step/sneak query must stay inside it. Leaving coverage fails instead of silently treating unknown space as air. A bounds declaration does not itself prove that the supplied geometry is complete.

The scene can be produced by the block planner or a future one-time world geometry exporter. Current real11 fixtures contain no complete scene, so their whole-route regression uses declared contacts. Do not infer arbitrary obstacle geometry from the captured trajectory or describe that test as scene discovery parity.

The profile excludes fluids, webs, soulsand callbacks, entity interactions, block changes, teleports, damage, bouncing blocks and other block callbacks. Speed, Slowness, Jump Boost and Blindness row effects are accepted. Other scene fields/effects are rejected. Input resume supports sprint state, previous keys, jump cooldown and standard legacy air factors; a stuck/web resume is rejected. Repeated jump keys use the normal cooldown, although the pipeline's intended inputs are taps. Pitch does not affect the supported horizontal movement.

## Replay and export an existing result

Compile the problem with the mod input state before solving it, particularly when walls can cancel sprint. The exporter does this for 1.8.9; existing legacy benchmark requests need the separate recompilation step in [MOVEMENT_PORT.md](MOVEMENT_PORT.md). Compilation and replay now share the input/sprint/cooldown state and row-realization policy. Re-solving the corrected real11 200/400 cases produces replay-accepted TASes; this supersedes the earlier unknown result from inconsistent sprint coefficients.

Use the existing Gradle init script:

```sh
./gradlew -I standalone/ipopt/export.gradle :core:replayStandaloneIpopt \
  -PipoptCapture=/path/to/template.json \
  -PipoptInput=/path/to/compiled-problem.json \
  -PipoptResult=/path/to/ipopt-result.json \
  -PipoptOutput=/tmp/realized-tas.json \
  -PipoptReport=/tmp/replay-report.json \
  -PipoptReplaySeconds=5 \
  -PipoptGiveBack=0.0001
```

Add `-PipoptScene=/path/to/scene.json` to enable discovery. Paths accept plain JSON or gzip inputs. Machines with the repository's Windows Gradle Java-home setting must override it with `-Dorg.gradle.java.home=/path/to/jdk-21` as for other Gradle commands. The CLI itself uses core/forge-core Java classes, not the MC loader classes. Its compilation currently shares the optional exporter's test harness classpath.

`ipoptResult` accepts the Python CLI result with a `candidate`, or a candidate object containing `yaw` and `objective`. The template supplies keys/effects/start/resume/prefix and authored surface overrides. The adapter realizes KEEP or FORCE_45 policies, probes neighboring executable joint sine-table cells, preserves original hard facing rows and bounds objective loss by `ipoptGiveBack` (default 1e-4). This is a bounded local repair, not a global discrete optimizer; a repair miss is `unknown`, never a proof of infeasibility. A malformed or unsupported request is `error`.

The output keeps the original prefix and truncates the unvalidated suffix. Solved rows use absolute float yaw locks to avoid delta-accumulation drift. `SaveIO` serializes the result, reloads it and independently replays all rows again before acceptance. Only a `validated` report writes an output; failed jobs leave any old output untouched, so an existing output file is not an acceptance signal. Read the report from the current invocation. Writes are atomic. The report includes backend/scope, baseline and final violation, failed check names/residuals, objective, path drift and evaluation count. Position/constraint tolerance is 1e-7; objective give-back is separately explicit.

For a caller inside Java, use `new LocalReplayOracle(problem, sceneOrNull).replay(tas, true)` for raw replay, or pass that oracle to `IpoptReplay.advance` until the session finishes for repair/validation/export. The final extra list entry only reports final medium occupancy and is not an additional exported input tick. No live TAS or simulator cache is mutated.

## Regression evidence

- `LocalReplayOracleTest` rolls the genuine real11 first 400 inputs forward from the saved start through every recorded state, including collisions and ladders, and compares X/Y/Z, VX/VY/VZ, yaw, ground, horizontal collision, sprint and movement inputs. It does not reseed from each next recorded state.
- `StaticSceneTest` checks wall-normal zeroing, tangential carry, strict face overlap, Y-before-X/Z landing, stepping/ceiling rejection, sneak-edge carry, wall-triggered ladder entry and missing scene coverage.
- The scene integration tests discover a previously undeclared wall/ladder entry, check uncapped entry movement and the following tick's caps, report a changed schedule, then validate a TAS through scene replay and a JSON round trip.
- `IpoptReplayTest` checks neighboring-cell repair, strict original F pins, objective give-back, saved artifact validation, prefix/suffix handling, cancellation and malformed requests.

The existing real11 ladder evidence fixture was enriched with its original start, complete input rows, recorded debug fields and surface overrides. Existing positions, velocities and input keys were checked against the source capture before enrichment. No predicted states were substituted. This reuses the existing fixture rather than adding a second real11 copy.
