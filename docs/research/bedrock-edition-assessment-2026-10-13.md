# Assessment: Bedrock Edition parkour (issue #219)

Written 2026-10-13. Status: assessment, no code changed. Origin: issue #219 (M33Galaxy, 2026-07-11) asks for Bedrock Edition jump optimisation and lists five Java vs Bedrock differences; the follow-up on Discord (2026-10-12 and 13) answers the maintainer's "I don't even have a Bedrock account" with "you don't need to, just use BPK mod", pointing at https://github.com/xiaozi233/BPKMod. This record reads BPKMod's source, maps every difference onto this codebase, and says what each level of support would cost. Decision asked of the maintainer: whether to start, and at which level.

Sources read: BPKMod `master` at 3e8fbba (release 2.1.0, 2026-09-15, Fabric for Minecraft 26.2) and tag `1.5.7` (2025-08-24, Forge 1.12.2 via RetroFuturaGradle and MixinBooter); this repository at f388bba on `dev`. Nothing was run in-game: no Bedrock client and no BPKMod client were available, so every behavioural claim below is a reading of code, not a measurement.


## 0. Summary

1. The tool cannot be byte-exact against real Bedrock Edition from here, and nobody can: Bedrock's movement code is not public and BPKMod does not claim to replicate it (its inertia option is "try 0.0001 or less", its defaults are not Bedrock, several of its rules are reverse-engineered approximations). The only reachable target is **Java plus BPKMod**: byte-exact against a Java client running BPKMod with a given config. Under the project's "simulation accuracy is non-negotiable" rule that is the honest name for the feature: a BPKMod profile, not a Bedrock mode.
2. Most of BPKMod already applies to the simulator for free: `SimulatorEntity` is a real `Player`, so BPKMod's mixins on `LivingEntity`, `Entity`, `Player` and the block classes (inertia threshold, collision order, chest / trapdoor / pane / cactus / spawner / honey boxes, soul sand, ladder, slime bounce, sneak edge) run for the simulated player exactly as for the real one, and the block constraint capture reads the modified collision shapes. The three rules BPKMod injects into `LocalPlayer` do **not** apply, because the simulator re-implements that client code privately: the 45-strafe cancel, the sprint-stop-on-wall rule and sprint backward. With BPKMod installed and configured as Bedrock, the simulator therefore diverges from the real player on every W+A tick (2% acceleration) and on every wall touch (sprint kept vs dropped).
3. The angle solver, velocity finder, onejump forecast and run-ticks search all run on `ExactJumpModel`, which knows only the Java versions. Any BPKMod change to inertia or strafe makes every solve disagree with the simulator; the post-Apply deviation check would flag it.
4. Today none of this can even be tried on the main loader: BPKMod 2.x depends on Minecraft `~26.2`, `loader-fabric` moved to 26.3 in 1.13.0 (#505). The 1.12.2 line (BPKMod 1.5.7 next to `pkc-forge-1.12.2`) has no version gap.
5. Recommendation (section 5): do not start the solver work on one request. Answer the issue with the "Java plus BPKMod" framing, ask who would use it and on which line, and if there are users do the loader-side mirroring first (days, one loader, directly QA-able against the real player). The core profile is weeks and its value is capped by BPKMod's own fidelity.


## 1. The five differences, as the issue lists them, against what BPKMod implements

| # | Issue #219 says | BPKMod 2.1.0 (Fabric 26.2) | BPKMod 1.5.7 (Forge 1.12.2) | For this tool |
| --- | --- | --- | --- | --- |
| 1 | No 45 strafe, WA is the same as W | `strafeAccelerateAllowed` (default **true**, so 45 strafe stays on unless the user turns it off). Off: `LocalPlayer.modifyInputSpeedForSquareMovement` returns its raw argument, so the diagonal input keeps length 0.98 (0.98 / sqrt 2 per component) instead of being stretched to the unit square (length 1.0). Magnitude of W+A equals W; direction stays diagonal, which is the "Jam WA has some differences" the issue mentions. | `strafeAccelerateAllowed` (default false). `MovementInputFromOptions` normalises moveForward / moveStrafe to unit length before the 0.98 scaling, so moveFlying's "divide only above 1" branch never fires and the diagonal gets 0.98 magnitude. Same end state as modern. | Input scaling in the solver model (section 4, item B1) and in the simulator's private copy of the square mapping (section 3). |
| 2 | No inertia | Not removed, made configurable: `inertiaThreshold` (default 0.0001, range 0 to 0.005) applies only with `oldInertiaThreshold=true` (default **false**). With it, the per-axis pre-1.14 rule applies to players too, X, Y and Z each zeroed below the threshold; 0 removes the gate. | `inertiaThreshold` (default 0.0001) replaces the 0.003 literal in `EntityLivingBase.onLivingUpdate`; always on. | Threshold and axis rule of `ExactJumpModel` (B2). The loaders inherit it. |
| 3 | No sprint-sneaking, no sprint-crawling | Not modelled at all. | Sprint is refused and dropped while the player is in the sneak pose (the `setSprintTrueConsiderSneak` / `setSprintFalseConsiderSneak` modifiers, keyed on `sneakHeight`). | Nothing on 26.2; on 1.12.2 the sprint machine (`forge-core` `PlayerSprintMachine`) would need the extra stop condition. |
| 4 | Trapdoor 0.1825 thick, pane and anvil as in Java 1.8.9, chest 0.95 wide and 0.95 tall | Chest and ender chest `(0.025, 0, 0.025) to (0.975, 0.95, 0.975)`; trapdoor thickness `0.1825 * 16` px; cactus collision = its full outline; spawner shrunk by 1e-4 per side; glass panes, iron bars and copper bars with the 1.12 arm-union table (2 px arms to the centre line, the 1 px inner-corner notch open; TrueModels geometry); honey block 14 px wide, friction 0.8, jump factor 0.6, no speed factor, with a Bedrock slide (vx, vz *= 0.4 and vy >= -0.12 every tick inside it); soul sand speed factor dropped and the slowdown moved into the acceleration (friction * 1.225). **No anvil change** in either version. | Chest, ender chest, cactus, spawner, pane, trapdoor, wall mixins. | Free: the simulator collides with the modified shapes and `FabricBlockPicker` / `FabricMinecraftAccess` capture them for constraints (section 3). |
| 5 | Hitting a wall never cancels sprint | `beCollisionStopsSprint` (default true): `horizontalCollision` is no longer consulted. Sprint stops only when the dominant axis of the intended move (Z when abs(prevDelta.z) > abs(prevDelta.x), else X) actually moved less than 5e-5 that tick, a head-on stop, plus the vanilla "not possible" and "no forward impulse" conditions. | `ignoreCollidedHorizontally` (default true): the `collidedHorizontally` read in `EntityPlayerSP.onLivingUpdate` is forced false. | The simulator's private `shouldStopRunSprinting` and the engine's wall-hit sprint model (section 3, B3). |

BPKMod also changes things the issue does not list. Fixed collision order Y then X then Z (`oldCollisionOrder`, default true) instead of 26.x's larger-horizontal-axis-first order. Sprint backward (`sprintBackward`, default false). Bedrock sneak edge protection (`beSneak`, default false: the box shrunk by 0.025 on X/Z, the move shaved in 0.05 steps, so the player stops 0.025 short of the edge). Two-tick slime bounce with a 0.08 threshold (`beSlimeBounce`, default false). Bedrock ladder (`beLadder`, default false: 0.2 climb and descend, no horizontal clamp, the climb wins over the jump). 1.5.7 only: "new touch" (`isNewTouch`, the strafe rotated to 11.48 degrees, acos 0.98), sprint delay on ground and in air, non-sprinting strafe, climbable trapdoor. The Discord mentions of reverse sprint and joystick 11 strafes are, per the requester, patched in current Bedrock; `sprintBackward` is the former and nothing models the latter.

Two consequences of the defaults. A player who installs BPKMod and changes nothing gets the Bedrock blocks, the fixed collision order and the wall-sprint rule, but keeps Java's 45 strafe and Java's inertia. "Bedrock physics" is therefore a config, not the mod, and a BPKMod profile in this tool has to read that config (`config/bpk.json`, Jankson via Cloth AutoConfig on 2.x; Forge annotation config on 1.5.7) or ask the user to restate it.


## 2. What "Bedrock support" can mean here

The tool's whole value is certification at 1e-4 margins, which only a byte-exact model gives (`docs/VISION.md`, "Simulation accuracy is non-negotiable"). Three targets exist, with very different reach:

- **Real Bedrock Edition.** A C++ engine with its own float arithmetic, tick order, input pipeline (analog, with the joystick rules) and block registry. No source, but the modding community has symbol-named disassembly (Mojang shipped the dedicated server's PDB until 1.21.20; LeviLamina carries per-build runtime data and about 17,000 reconstructed headers forward to 26.51, movement systems included) and the Bedrock parkour community has written the rules down ([MCBE_Parkour_wiki](https://github.com/Acce5sDenied/MCBE_Parkour_wiki), current for 26.5x: no inertia, no strafe boost, 32-bit float positions, plain sin and cos, step height 0.5625, the 5e-5 sprint-cancel rule, the sneak edge algorithm, the two-phase slime bounce). Per-tick float-exact recordings are possible through the official Script API (`player.inputInfo`, location, velocity). A byte-exact replica would still be a float32 pipeline with platform-specific trig, a new model rather than a flag on the current one, and it would be the "custom physics math" the project forbids. Out of scope.
- **Java plus BPKMod.** Real Java code paths with a handful of constants and branches changed. Byte-exact certification is possible in the same way it is today, against the modded client. Margins certified here transfer to real Bedrock only as far as BPKMod is right; for block sizes that is a lookup, for strafe, inertia and sprint it is unverified by anyone.
- **The current tool, unaware of BPKMod.** The state today: the simulator half-follows the mod (section 3) and the solver ignores it, so a user running both gets silently wrong paths.

Any work should be named and shown in the UI as a BPKMod profile. A "Bedrock" label would promise something the tool cannot check.


## 3. What already works with BPKMod installed, and what silently breaks (Fabric 26.x)

`SimulatorEntity extends Player` (`loader-fabric/.../sim/SimulatorEntity.java:33`) and `GhostPlayerEntity` likewise; both run Minecraft's own `LivingEntity.aiStep`, `travel`, `Entity.move` and `collideWithShapes` and the real block classes. A mixin on any of those applies to the simulated player as it does to the real one, with no code in this repository:

- inertia threshold and the per-axis player rule (`LivingEntity.aiStep`),
- the fixed Y, X, Z collision order (`Entity.collideWithShapes`, a static wrap),
- every block collision change, both for the simulator's collision and for the constraint capture, since `FabricBlockPicker.java:27` and `FabricMinecraftAccess.java:77,168` read `getCollisionShape` from the world,
- honey slide, soul sand acceleration, ladder speeds, slime bounce, and the sneak edge back-off (`Player.maybeBackOffFromEdge`).

What does not follow, because the simulator re-implements `LocalPlayer`'s client logic privately instead of inheriting it:

1. **45-strafe cancel.** `SimulatorEntity.java:420-450` and `GhostPlayerEntity.java:245-262` carry their own copy of `modifyInput` and `modifyInputSpeedForSquareMovement`. BPKMod's `strafeAccelerateAllowed=false` wraps `LocalPlayer`'s method only, so the simulator keeps the vanilla diagonal boost while the real player does not: every W+A tick differs by the 1.0 vs 0.98 factor.
2. **Sprint stop on a wall.** `SimulatorEntity.java:355-359` mirrors vanilla `shouldStopRunSprinting` (`horizontalCollision && !minorHorizontalCollision`). Under `beCollisionStopsSprint` (default on) the real player keeps sprint through grazing and angled wall hits; the simulator drops it, and from that tick on every position differs. The replacement rule needs the intended move and the actual per-axis displacement of the tick; `SimulatorEntity.move` (`:73-85`) already brackets `super.move`, so both are available.
3. **Sprint backward** (`sprintBackward`, off by default): not mirrored. Low priority.
4. **Sub-tick path order.** `SimulatorEntity.move` records the drawn sub-tick corners with the vanilla axis order (`abs(x) >= abs(z)` decides X first, `core/.../sim/SubtickPath.java`). Under `oldCollisionOrder` X always goes first, so the drawn corners are wrong on ticks where abs(z) > abs(x). Display only (`BoxController`, `PathRenderPlan`); positions are unaffected.

The core model is unaware of all of it. `ExactJumpModel.forMcVersion` (`core/.../solver/ExactJumpModel.java:120-126`, wired in `Application.java:219-220`) picks threshold, axis rule and arithmetic chain from the Minecraft version string alone. With `oldInertiaThreshold` or the strafe cancel on, the solver's byte-exact forward and the simulator disagree, so the angle solver, the velocity finder, `LandingForecast`, `DeepCheck`, the run-ticks search and the no-turn solver are all off, and the post-Apply deviation check (1e-9 per tick, `docs/adr/0001`) reports every apply as a divergence. The solver's own sprint model (`AngleSolverEngine.healWallHitSprint`, `:773-786`, fed by `TickState.wallCollision` / `softCollision`) encodes the Java rule as well.

Version gate: BPKMod 2.x declares `"minecraft": "~26.2"`; `loader-fabric` has been on 26.3 since 1.13.0 (#505). Until BPKMod moves to 26.3 (or someone tests on the 1.12.0 jar, the last 26.2 release), nothing above can be exercised on the modern loader. `loader-fabric-1.21.3` has no BPKMod build at all. On Forge 1.12.2 the shapes are the same: `SimulatorEntity extends EntityPlayer` there, so BPKMod 1.5.7's `EntityLivingBase` and `Entity` mixins (inertia, new touch in `moveRelative`, ladder, sneak box) apply to the simulator, while the `EntityPlayerSP` sprint rules and the `MovementInputFromOptions` strafe normalisation do not, because `forge-core` `PlayerSprintMachine` computes moveForward / moveStrafe and the sprint state itself.


## 4. What a BPKMod profile would take

### Tier A: loader-side mirroring (recommended first step if there is demand)

Scope: make the simulator follow the installed BPKMod config, so the manual calculator, playback, the onejump tracker and block constraints are correct under BPKMod. The solver stays Java-only and is guarded (hidden or labelled "Java physics only" while a non-Java profile is active), because an un-guarded solver would be 2% off and the deviation check would scream on every apply.

- Detect the mod (`FabricLoader.getInstance().isModLoaded("bpk")`) and read `config/bpk.json` (three flags matter loader-side: `strafeAccelerateAllowed`, `beCollisionStopsSprint`, `sprintBackward`; `oldCollisionOrder` for the sub-tick drawing). Re-read it on world join and when a screen closes, since BPKMod edits the file live from its Mod Menu screen.
- `SimulatorEntity` and `GhostPlayerEntity`: skip the square mapping when strafe accelerate is off; replace `shouldStopRunSprinting` with the BPKMod rule using the move deltas; mirror sprint backward in `canStartSprinting` / `shouldStopRunSprinting`; pass the fixed axis order to `SubtickPath.appendMove`.
- The loader entry point reports the profile to core (a new `MinecraftAccess` port method, e.g. the active physics profile with its flags) so the HUD and the TAS file (`SaveFile.mcVersion` sits at `SaveIO.java:398`; the profile belongs next to it, and `FileMenu` already shows the version column) say which physics a TAS was made under. A TAS saved under BPKMod and reopened under vanilla should warn, the same way a version mismatch would.
- QA: byte-exact playback replays a TAS on the real player, and the `AttemptTracker` records the real player's per-tick state against the TAS. Build a few BPKMod jumps (a W+A run-up, a wall graze, a chest edge), replay them and confirm zero per-tick deviation. That is a direct byte-exact check and needs no Bedrock knowledge.
- Prerequisite: BPKMod on 26.3, or the test on the 26.2 jars. Forge 1.12.2: the same four points against BPKMod 1.5.7 (`ignoreCollidedHorizontally`, the strafe normalisation, the sneak-pose sprint stop, and `isNewTouch` is inherited through `moveRelative` so nothing to do there).

Effort: one to two days per loader plus the in-game pass. Risk: low, every change is behind "BPKMod installed and flag on".

### Tier B: a core physics profile for the solver

Scope: `ExactJumpModel` and the engine learn a profile orthogonal to `mcVersion`, so solves, the velocity finder, forecasts and the no-turn solver are byte-exact under BPKMod.

- **B1, input scaling.** The 0.98 literals and the square-diagonal constant are spread over `AngleSolverEngine.java:718,732-733`, `ExactJumpModel.SQUARE_DIAG_INPUT` (`:32-45`), `NoTurnKeys.SCALE` (`:16`) and `JumpPhysicsInputs.forwardAt`. Under the strafe cancel the diagonal components become 0.98 / sqrt 2 and the modern double chain never normalises (length squared 0.9604 < 1), so the W+A acceleration magnitude equals W's. On the legacy float chain (1.12.2) the pre-scaled normalisation gives the same magnitude. The `Force 45` input mode (`AngleSolverState.InputMode.FORCE_45`) becomes meaningless and must be hidden; the no-turn key combos lose their strafe advantage.
- **B2, inertia.** Threshold from the config, `perAxisInertia=true`, Y included (the model already zeroes Y on the per-axis branch, `ExactJumpModel.java:161-175`). The hard part is threshold 0: `inertiaThreshold()` has about two dozen call sites (`AnchorSlp`, `GateFoldFinder`, `SmoothFaceRecovery.zeroingPattern`, `CertifiedBnb`, `NoTurnJointFastCheck`, `JumpLinearModel.velocityWalls` / `keepAliveWall`, the #397 gate-open branching). With no gate the gate-fold machinery, the zeroing patterns and the pattern branching are no-ops, which is fine for the search (the problem becomes smooth) but every site must be audited for a division, an empty wall list or a loop that assumes a positive band. A threshold of 0.0001 (BPKMod's default) keeps the gates and only moves them; that case is cheap.
- **B3, sprint.** `healWallHitSprint` and the Derive sprint machine need the BPKMod stop rule, which depends on the per-axis displacement of the tick. `TickState` carries `wallCollision`, `softCollision`, `moveForward`, `moveStrafe` and `sprinting` today; it would gain the intended and actual per-axis deltas, recorded by every loader.
- **B4, persistence and UI.** The profile in the TAS header (as in Tier A), shown in the solver window and the file browser; graph presets unaffected.
- **B5, tests.** `ProblemsTest` and the capture fixtures are Java captures. A BPKMod profile needs its own captures recorded in-game with BPKMod (the capture pipeline exists), new golden hashes in `CrossJvmDeterminismTest` for the profile branch, and the slow suite run per profile. CI shards would grow by whatever the new problems weigh.

Effort: the arithmetic changes are small; the audit of the certified branch-and-bound and gate machinery under a changed or absent inertia band, the recorded fixtures and the validation are weeks, and `CertifiedBnbEngineTest` / `PipelineShapeTest` (the very-slow tier) must pass per profile. Everything certified is certified against BPKMod, not Bedrock.

### Tier C: a real Bedrock replica

Not recommended and not reachable: no source, no ground truth recorder, no community reference; it would be the approximate custom physics the project rules out.


## 5. Recommendation

1. Reply on #219 (draft in section 6): the tool can target Java plus BPKMod, not Bedrock; most of BPKMod already reaches the simulator, three client-side rules and the whole solver do not; ask how many people would run it, on 26.x or 1.12.2, and with which config. Label the issue "needs demand" rather than closing it; the loader-side gap is real and cheap to close once it can be tested.
2. If BPKMod users show up: Tier A on the loader they use, gated on BPKMod's 26.3 port for the modern line. Measure whether they then ask for solves before touching core.
3. Tier B only on that evidence. Its value is capped by BPKMod's fidelity to Bedrock, which nobody has measured.
4. Independent of Bedrock and worth doing now: a one-line startup warning in the Fabric and Forge 1.12.2 entry points when BPKMod is loaded (the simulator does not follow BPKMod's sprint and strafe rules), so divergence is not reported as a simulator bug. Small enough to ride any next loader PR; it needs the usual in-game pass.


## 6. Implemented 2026-10-13: Tier A on the Fabric loader (branch `feature/bedrock-assessment`)

The maintainer's call after this record: the requester only needs the calculator to be right with BPKMod installed, so Tier A was built, plus the cheap part of Tier B.

- `loader-fabric/.../sim/BpkModBridge.java` reads BPKMod's live config by reflection (`cn.xiaozi0721.bpk.config.ConfigHandler.generalConfig`: `strafeAccelerateAllowed`, `beCollisionStopsSprint`, `sprintBackward`, `oldCollisionOrder`, `oldInertiaThreshold`, `inertiaThreshold`). No compile dependency; a missing mod or a renamed field falls back to vanilla rules and a HUD line says so.
- `SimulatorEntity` and `GhostPlayerEntity` refresh the rules every tick and mirror the three `LocalPlayer` injections: the square mapping is skipped when the strafe boost is off; the sprint-stop rule becomes BPKMod's (the intended move of the last `move` call and its float displacement are recorded in `move`, checkpointed, cleared on reset and teleport; sprint stops only when the dominant intended axis displaced under 5e-5, `horizontalCollision` is not consulted); sprint backward follows the four sites BPKMod touches. Under the fixed collision order the sub-tick corners are drawn X first. On playback the real player's BPKMod fields (`prevDelta`, `deltaX`, `deltaZ`) are seeded from the checkpoint by reflection, best effort.
- Core: `PhysicsVariant` (inertia threshold, axis rule, strafe boost, source label), `ExactJumpModel.forMcVersion(version, variant)` and `withVariant`, the raw diagonal input (`1/sqrt 2` as float times 0.98) on Force 45 ticks and on typed W+A rows (both components exactly 0.98F, the signature of an input authored from keys without the game's normalisation; sampled inputs are never touched), the same rule in `SineTableGeometry` (certified bounds) and `JumpLinearModel` (linearisation), `JumpPhysicsInputs.strafeBoost` copied by every scenario builder. `Application.setPhysicsVariant` is called by the Fabric entry point before `setupUi`, so the solver follows BPKMod's config as it was at launch; the HUD line on world join reports a config that changed since.
- Tests: `PhysicsVariantTest` (fast suite). The vanilla path is byte-identical (default flags), which the existing golden-hash and problem suites guard.

Not done, in order of importance:

1. **In-game verification.** No Minecraft client here, and BPKMod 2.x still targets 26.2 while the loader is on 26.3. The byte-exact check is the one from Tier A: build a W+A run-up, a wall graze and a chest edge under BPKMod, replay them and compare per tick.
2. **Resume state.** A TAS that starts mid-motion from the real player's state seeds the BPKMod sprint-rule state as zero (no pending stop); `StartResumeState` and the TAS file do not carry it. A start from rest is exact.
3. **Inertia threshold 0.** The solver's gate machinery (gate folds, zeroing patterns, the #397 pattern branching, `velocityWalls`) was not audited for an absent band; BPKMod's default 0.0001 keeps the gates and is covered by the model change alone.
4. **Forge 1.12.2** with BPKMod 1.5.7: the same four mirrors against the `forge-core` sprint machine; not started.
5. **Sprint-sneak and crawl** (issue item 3): BPKMod 2.x does not model it, so neither does the calculator.

## 7. Draft answer for the issue

> Thanks for the pointer to BPKMod, that settles the "no Bedrock account" part. I read its source (2.1.0 on Fabric 26.2 and 1.5.7 on Forge 1.12.2). What the calculator could realistically support is "Java + BPKMod", byte-exact against the modded Java client, not real Bedrock: nobody has Bedrock's movement code, and BPKMod itself approximates (its inertia option lowers the threshold rather than removing the rule, and its defaults keep Java's 45 strafe and inertia).
>
> Where it stands: because the simulator is a real `Player`, BPKMod's block boxes, inertia threshold, collision order, honey, soul sand, ladder, slime and sneak changes already apply to it. Three things do not: the 45-strafe cancel, the "walls don't cancel sprint" rule and sprint backward, because those live in `LocalPlayer`, which the simulator re-implements. The angle solver does not know BPKMod at all. Mirroring the three rules in the loader is a few days of work; teaching the solver a BPKMod profile is weeks.
>
> Two things I need before starting any of it: (1) BPKMod on 26.3 (the Fabric loader moved there in 1.13.0), or testers on the 1.12.2 line; (2) a sense of how many people would use it and with which BPKMod config, since "Bedrock physics" in BPKMod is a config, not the default. If there is a group that plays BPKMod parkour, point them here.
