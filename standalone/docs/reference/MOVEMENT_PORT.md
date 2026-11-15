# Movement port status and limitations

Current scope, 2026-10-26: Minecraft 1.8.9 parkour on static inert collision shapes and ladders. The local replay has collision discovery for this scope. It is not a complete replica of every Minecraft movement mechanic or version.

## Implementation sources

The reference is the mod's actual simulation path, not the native optimizer's simplifying assumptions:

- `core/.../sim/LazyEntitySimulator.applyInput`: apply input effects, then row yaw/pitch, then tick the entity.
- `loader-forge/.../sim/SimulatorEntity.applyMovementInput` and `forge-core/.../sim/PlayerSprintMachine`: keys, sneak scaling, sprint start/stop, previous collision, blindness and sprint timers. Standalone compilation/replay directly share this existing sprint machine.
- `SimulatorEntity.onLivingUpdate`, `travel` / `moveEntityWithHeading`, reset/resume/checkpoint methods: entity tick ordering, sampled pre-movement medium, friction, carried state and delegation to Minecraft movement.
- Local decompiled 1.8.9 `EntityLivingBase.onLivingUpdate`, `jump`, `moveEntityWithHeading`; `EntityPlayer.onLivingUpdate` and `getAIMoveSpeed`; `Entity.moveEntity`; `AxisAlignedBB.calculateXOffset/YOffset/ZOffset`: inertia, input damping, jump cooldown, current ground attributes, previous-tick air factor, float arithmetic, collision/step order and ladder carry. Generate sources with the repo's `genSources` task, as specified in AGENTS.md.
- Loader `MovementEffects` and core `MovementEffect.clientLevel/clientAmplifier`: effect application and legacy signed-byte amplifier wrapping. Local supported effects use the same conversion.
- Existing `ExactJumpModel`, `Constants` and `McSineTable`: the exact search/replay arithmetic and table. The new coefficient compiler obtains its horizontal control basis from this kernel at yaw zero, and float friction from `JumpLinearModel`; it does not invent replacement movement constants.

Fabric's simulator delegates to the corresponding Minecraft version too. Its APIs/physics differ, so its existence does not make the standalone 1.8.9 port a validated modern-version implementation. The mod contains the relevant entry points, but many mechanics execute in inherited Minecraft or block callbacks and must be read there before a port is claimed complete.

## Supported scope

| Mechanic | Current standalone behavior |
| --- | --- |
| W/A/S/D, diagonal normalization, sneak input scaling | Uses the mod input pipeline and exact legacy float movement chain. KEEP preserves the keys; FORCE_45 realizes its explicit W/A-or-D/sprint policy. |
| Sprint start, stop, double-tap/timers, blindness | Reuses `PlayerSprintMachine`. A pressed sprint key cannot prevent wall-triggered sprint cancellation. |
| Air movement factor | Carries the previous tick's sprint factor. The corrected compiler and exact replay share `ReplayInputState`, including the one-tick lag after cancellation/restart. |
| Ground friction and current movement attributes | Uses float slipperiness/friction and the current supported row effects. Scene replay reads the cell below the player's feet. |
| Inertia | Uses the 1.8.9 per-axis incoming-velocity threshold, before acceleration. |
| Ground jump and Jump Boost | Ports ground eligibility, cooldown, release reset, impulse and legacy amplifier conversion. The pipeline normally taps Jump. Negative impulses from exotic wrapped amplifiers are rejected by the current Python vertical request profile. |
| Walls, floors, ceilings | Scene replay clips Y, X and Z in order, preserves the bounding box, resets only blocked velocity components and derives collision/ground flags. |
| Step-assist and sneak edge guard | Scene replay ports both 1.8.9 step alternatives and the edge reduction loops. The continuous optimizer does not perform general scene collision discovery. |
| Ladders | Pre-movement occupancy controls component caps/descent. A wall hit with resulting ladder occupancy sets climb carry; the following input moves with that stored carry. |
| Static multipart shapes | Supply every inert collision AABB, including separate parts. This supports geometry such as slabs/panes/fences, without claiming their shape implies every possible block callback. |
| Speed, Slowness, Jump Boost, Blindness | Uses the mod's effect level conversion and input consequences. Other requested row effects are rejected. |
| Saved resume | Standard sprint/previous-key state, jump cooldown and supported legacy air factors. A stuck/web resume or nonstandard air factor is rejected. Preserve the original prefix when exact portable mid-route state is unavailable. |

## Mechanics not yet ported end to end

| Mechanic | Missing work |
| --- | --- |
| Water/lava | Fluid occupancy/depth and version-correct travel, drag, vertical movement and wall escape behavior. Ordinary air coefficients are not a substitute. |
| Cobwebs | Pending stuck state, scaled movement, velocity reset and its persistence through checkpoints. The existing request's simple carry recurrence cannot represent all of this by changing friction alone. |
| Soulsand | Actual cell overlap and the legacy per-cell post-movement damping callback. The exact kernel contains relevant arithmetic, but the scene/compiler path does not support this medium yet. |
| Slime/bouncing blocks and other callbacks | Block identity/behavior, landing response and movement callbacks. A collision box and slipperiness alone do not describe these mechanics. |
| Damage, knockback, server/paired simulation | Packet/velocity state, timing and server behavior. Damage overrides are rejected by the current compiler. |
| Moving/changing blocks, entities, interactions, teleports | The scene is static and inert. Clicks and teleports are rejected. |
| Flight, riding, item-use movement, other player modes | Additional input/entity state and corresponding movement paths. The current profile models the mod's ordinary parkour player with food level 20, without item use or interactions. |
| Other MC versions/effects | Separate version ports and parity evidence. Levitation, Slow Falling, Dolphin's Grace and Weaving are not accepted by this profile. |

The scene producer must not encode unsupported block behavior as an inert box and interpret a passing replay as validation of that behavior. Unknown scene fields/effects/medium overrides are rejected where represented explicitly. Geometry completeness and correct block classification remain input responsibilities.

## Validation boundaries

Final validation against the actual world belongs to the mod. The standalone result is a TAS JSON checked against the supplied movement/block contract, which the user can import and replay in Minecraft later. A complete scene export is optional for this workflow. Conditional accuracy requires that declarations cover all relevant swept obstructions and support/contact/ladder/friction states; endpoint bounds alone do not establish that completeness.

**Declared-contact replay** checks the supplied wall/Y contacts and ladder schedule; it does not discover other obstacles. It currently requires source.startTick = 0. Reach/approach failures and changed ground/contact assumptions reject acceptance. It provides a strong movement regression for the recorded real11 route, but it is not a proof about all blocks in the actual world.

**Scene replay** discovers contacts from supplied collision shapes, ladder cells and friction cells. Its bounds are an explicit assertion that all relevant geometry, including empty space, is supplied. Queries leaving coverage fail. The current real11 fixtures have no complete scene snapshot, so the real11 400-tick result uses declared contacts; scene discovery is separately tested with supplied geometry.

**Continuous optimization** uses a smooth rotated control basis with explicit inertia/contact/cap branches. Even when its coefficients come from the exact movement kernel, arbitrary-angle float and sine-table execution still differs. A continuous feasible result is not automatically an executable TAS. Nearby-angle repair checks original constraints and explicit objective give-back, then replays serialized/reloaded rows before writing an accepted TAS.

The repair is bounded and local; `unknown` means no accepted witness within that search, not infeasibility. This is not a global optimum or minimum-input-pattern certificate. Prefix and final state must be replayed consistently at splices; finding a feasible short segment does not prove a continuation exists.

## Correct coefficient compilation

Legacy real11 fixture arrays are retained for reproducible numerical benchmarks. They keep the old always-sprint/healed-sprint assumption and must be recompiled before treating them as Minecraft-accurate fixed-wall inputs. Their historical objective bars are not the corrected model's objectives.

The optional Java exporter now compiles 1.8.9 coefficients with the mod input state. To correct an existing request without changing the source fixture:

```sh
./gradlew -I standalone/ipopt/export.gradle :core:compileStandaloneIpoptPhysics \
  -PipoptCapture=/path/to/template.json \
  -PipoptInput=/path/to/old-problem.json \
  -PipoptOutput=/tmp/mod-movement-problem.json
```

Use the corrected problem in the existing Python solve, then pass that same problem and its new result to the replay command in [REPLAY.md](REPLAY.md). Warm-start angles from the old problem are allowed as search seeds; old feasibility flags, hashes and objective claims do not transfer. All original scalar constraints remain unchanged by coefficient recompilation.

The compiled `movement` object records expected horizontal-collision/ground state, sprint/air-factor state and processed inputs. The Python validator checks contact timing and, when vertical replay is supplied, checks ground state against these coefficient assumptions. Without vertical replay, ground remains an authored assumption until exact replay checks it. Unsupported surface/effect overrides must be ported or materialized as supported effective rows before compilation. Explicit ladder overrides require an attached occupancy schedule. A nonzero start currently needs recorded prefix ground/contact state to reconstruct the input machine.

## Current evidence

The original first 400 real11 inputs replay from the saved start with exact double-bit agreement across all 401 positions and velocities, including 34 wall-hit and 35 ladder states. That regression uses declared contacts and recorded ground truth, with no per-tick reseeding.

After correcting sprint cancellation, air-factor carry and float coefficient extraction, the 200-input case solved in about 0.94 s and passed exact local repair/export in about 0.25 s. The 400-input case solved in about 2.42 s and passed repair/export in about 3.80 s. Final constraint violation was zero. The 400-input replay objective was 691.5261153484987 versus continuous 691.5261822673284, within the explicit 1e-4 give-back. These are measured local results, not runtime guarantees or full-scene real11 certification.
