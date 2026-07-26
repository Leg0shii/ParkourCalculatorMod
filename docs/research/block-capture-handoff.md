# Handoff: block-role capture for fixtures and the future whole-jump solver

Status: IMPLEMENTED 2026-06-28 (core schema/state/persistence + Forge 1.8.9 loader capture/render). In-world verification by the user (`:loader-forge-1.8.9:runClient`) is the only remaining gate. This doc is self-contained; a fresh session can act on it without prior context. Conversation date 2026-06-28.

Amendment 2026-06-28 (supersedes the four-role design below): the user ruled that START blocks ARE momentum blocks, so the START role was dropped. The shipped roles are exactly three: MOMENTUM, COLLISION, LAND. START was a launch-footprint leftover from the shelved block solver; the future whole-jump solver only needs the momentum source. Legacy save files with `kind: "START"` load as MOMENTUM (alias in `SaveIO.toBlockSelection`). Where the text below says "four roles" or "START", read three roles with momentum subsuming the old start.

## What this is

Add a capture-only block picker (1.8.9 only, for now) that lets the user tag blocks by role (MOMENTUM, COLLISION, LAND; see the amendment above) while standing in the world, stores each block's real AABB in the save file, and renders them back for visual confirmation. The captured blocks are optional and inert to every current solver; they exist as the durable, version-stable input the future whole-jump solver (start position + momentum + inputs + yaws + landing, the `docs/VISION.md` north star) will consume.

This is a different feature from the #204 recovery/inertia work. It came out of a discussion about building a fixture library, then widened into "capture the geometry, not just the constraints."

## Why we are doing it this way (the decisions, with rationale)

These are settled. Do not relitigate without a reason.

1. **Capture is separable from derivation.** The block picker was built once for 1.8.9 and removed in commit `92ea571` "chore: remove block solver from UI". It was shelved because the block to constraint *derivation* (`BlockSolver`) was not trustworthy enough to ship as a user-facing solve path, NOT because the picker was hard. A capture-only picker stores raw AABBs, which are valid regardless of any solver; the derivation gets built and tested against that captured data later. This is what breaks the chicken-and-egg ("can't build the solver without block data, can't justify capturing block data without the solver").

2. **Blocks are the durable source of truth; constraints are derived and disposable.** Capturing constraints bakes in one derivation; capturing raw AABBs in-world (with F3 context, one keypress per block) is cheaper, more accurate, and survives improvements to the derivation logic. Raw geometry is interpretation-agnostic, so "no consumer yet" is a weak reason not to capture it.

3. **Roles are hints on interpretation-agnostic geometry.** Do not over-categorize. The strategy chosen (called option (b) in discussion) is: capture momentum as raw geometry tagged with a role, but do not invent a momentum sub-taxonomy (stepped, partial-cover, backwalled, etc.) until the consumer exists.

4. **All roles are LISTS, all OPTIONAL, all INERT to the current solver.** MOMENTUM, COLLISION, LAND (START folded into MOMENTUM per the amendment above). A fixture with none of them still works for the angle solver. CEILING (headhitter) is deferred; it is a geometrically distinct role the future solver will need, but not now.

5. **1.8.9 only for now.** The other two loaders (1.12.2, 1.21.10) read block hitboxes differently (`AxisAlignedBB` vs `VoxelShape`) and are deferred.

6. **Render is required**, not optional, so the user can confirm the capture was done correctly in-world.

## Grounding facts (verified against the code on 2026-06-28)

Save schema (`core/.../save/SaveFile.java`):
- `BlockSel` (line 76): `String kind`, `int x,y,z`, `double[] box` = `[minX,minY,minZ,maxX,maxY,maxZ]`. The AABB slot already exists.
- `angleSolver.selectedBlocks` (line 57): `List<BlockSel>`.
- `DebugTick` (line 151): full per-tick dump (pos, vel, yaw, onGround, sneaking, sprinting, wall/soft collision, collisionAngle, moveForward, moveStrafe). Its own doc: "written ONLY when save debug values is on ... the solver does not use it (it re-solves from rows and angleSolver.seed)." Inspection + byte-exact pinning only.
- `Result` (line 115): `yaws`, `objectiveValue`, `outcomes`, `success`. This is where the witness floor lives.

Role enum (`core/.../anglesolver/BlockSelection.java`):
- `Kind` (line 10) = `{START, COLLISION, LAND}`. Need to add `MOMENTUM`.
- `cube(kind,x,y,z)` (line 31): full-cube fallback `[x,y,z,x+1,y+1,z+1]`.

Persistence (`core/.../save/SaveIO.java`):
- Load: lines 135-145 switch on kind into `setStartBlock` / `setLandBlock` / `addCollisionBlock`. Unknown kind strings are dropped: `toBlockSelection` (line 392) uses `parseEnumOrNull(Kind.class, b.kind)`, returns null on miss, caller `continue`s. So new roles MUST be real enum values to persist.
- Write: `toSaveFile` lines 344-346 emit startBlock + collisionBlocks + landBlock via `toSaveBlock` (line 379), which writes kind.name + xyz + box. The `box` round-trips today for the existing three roles.

State (`core/.../anglesolver/AngleSolverState.java`):
- `startBlock` single (line 170, getter 500, setter 504), `landBlock` single (line 171, 508/512), `collisionBlocks` List (line 172, 516/520). START and LAND must become lists; add a `momentumBlocks` list.

Only consumer of the single getters (`AngleSolverEngine.solveFromBlocks`, line 856): reads `getLandBlock` (862), `getStartBlock` (877), `getCollisionBlocks` loop (883). This is the SHELVED block-solve path, compiled but not invoked from any UI. After promoting to lists, make it read the first element so it still compiles; no behavior change.

Pick primitive already in the port layer (`core/.../ports/MinecraftAccess.java`): `getLookedAtBlock()` returns `int[]` coords (default null), `isBlockSolid(x,y,z)`, plus eye/camera position for raycasts.

Removed picker, recover with `git show 92ea571`: core ports `BlockPicker` (10 lines) + `PickedBlock` (21 lines), `Forge8BlockPicker` (50 lines), block render in `Forge8WorldOverlayRenderer` (~69 lines), `AngleSolverWindow` UI, `Application` wiring, en_US.lang. Only 1.8.9 was ever wired.

Prototyping the future solver needs zero captures: `BlockSolver.solve(...)` already takes raw `List<Obstacle>` (AABBs) + landing geometry directly, so an algorithm can be developed against synthetic `new AABB(...)` layouts in a unit test.

## Implementation plan

Core (schema + state + persistence; all headless, gated by `:core:test`):
1. Add `MOMENTUM` to `BlockSelection.Kind`.
2. `AngleSolverState`: promote `startBlock`/`landBlock` to `List`, add `momentumBlocks` list. Update getters/adders.
3. `AngleSolverEngine.solveFromBlocks`: adapt the two single-block reads (862, 877) to take the first list element so the shelved path still compiles. No behavior change.
4. `SaveIO`: load switch routes all four roles into their lists; write loop (344-346) emits every block from every list. `box` already round-trips.
5. Core round-trip test: a selection with all roles including multi-block MOMENTUM/LAND, save, reload, assert identical, and assert the current angle solve is unaffected (blocks inert).

Loader 1.8.9 only (needs the user's in-game verification; the agent cannot runClient while the user has MC open):
6. Re-add the capture port (reuse the `BlockPicker`/`PickedBlock` shape from `92ea571`, OR fold "looked-at block + real AABB" into `MinecraftAccess`; see open question 1).
7. `Forge8` impl: raycast the looked-at block, read its real collision AABB (mirror the deleted `Forge8BlockPicker`, ~50 lines).
8. Keybinds via `ClientRegistry` (Forge uses the FML event bus + ClientRegistry keybinds, no mixins): one per role tags the looked-at block and toggles it off on repeat; one clear-all key (see open question 2).
9. World overlay render of selected blocks, color-coded per role (mirror the deleted block render in `Forge8WorldOverlayRenderer`).

Close-out: `:core:test` + linter/formatter; user runs `:loader-forge-1.8.9:runClient` to verify pick + render + save/reload.

## Open questions to resolve before/while starting

1. **Capture port shape.** Reuse the old `BlockPicker`/`PickedBlock` ports (proven shape, keeps `MinecraftAccess` focused; recommended) vs fold "looked-at block + AABB" into `MinecraftAccess` (fewer types).
2. **Selection UX.** Per-role keybind that toggles the looked-at block + one clear-all key (recommended) vs a cycle-role + single-pick-key scheme.

The agent offered to start core steps 1-5 (independent of both answers) while the user decides. That is a safe entry point.

## Constraints and gotchas

- Project rule: NO code comments of any kind (no javadocs, no inline). Write code only. (This doc is prose, fine.)
- No em dashes anywhere in the repo.
- `core/` stays Minecraft-free; all MC-touching code is loader-side.
- Forge keybinds use `ClientRegistry` + FML event bus, not mixins (mixins are Fabric-only here).
- Do not runClient while the user has MC open (shared run/ file + world locks deadlock).
- Run `:core:test` and the linter/formatter after changes; fix failures before moving on.
- imgui-java pinned to 1.86.11; do not bump.
- Do not hand-edit `mod_version` in `gradle.properties` (release-please manages it).
- Git: the user does all staging/committing/pushing; do not commit or push. No Claude attribution on commits.
- Conventional Commits: this adds a new capability (block capture), so `feat:`.
- Branching: the current branch `v1.7.0-recovery-inertia` is for the #204 recovery/inertia diagnostics, a separate concern. This block-capture work likely wants its own branch off `v1.7.0` (confirm with the user). The user creates branches.

## Related context (fixture-library design, the original thread)

This came out of designing a fixture/bake-off set for issue #204 (stronger step-1 path recovery after the dual bound). Those fixture decisions, also settled, are adjacent:
- Fixtures span FROM the momentum (long span), not from the jump takeoff; the short span is a trivial single-turn that closed form already solves and tests nothing. Precedent: `loopmm-3jump-lands.json` has `startTick=38`, 33 ticks, 3 jumps.
- Only capture jumps where a human strat exists (reachability guarantee).
- Bracket each fixture: store the best-known landing as a witness (the solver's solution if it solved it, the hand-input strat otherwise) in `angleSolver.result`; the dual bound is computed at test time as the ceiling. The witness is a known-good landing, NOT an optimum (nobody can compute the optimum). Do NOT freeze a "missed" flag; classify live (current solver vs witness floor vs bound) so a future fix migrates a fixture from missed to passing automatically.
- Save the byte-exact trajectory (turn on "save debug values") for inspection and byte-exact pinning.
- Landing of any kind (multi-block, ladder, vine, water, lava) collapses to a single XZ landing constraint at a specific tick; the witness disambiguates which block and which tick. Y is irrelevant. Nothing to change in the solver for these. Terminology: "landing constraint", not pad or footprint.
- Diagnostics already on branch `v1.7.0-recovery-inertia`: `DualBoundCoverageTest`, `DualBoundTrajectoryTest`.

Relevant memories: `project_angle_solver_blocks`, `project_multijump_ils_ceiling`, `project_velocity_finder_no_pad`, `feedback_comments_terse`, `feedback_git_workflow`.
