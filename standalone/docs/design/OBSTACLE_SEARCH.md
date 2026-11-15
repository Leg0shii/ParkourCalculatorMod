# Collision-box obstacle avoidance

Updated 2026-10-27. Implemented the geometry, height pruning and avoidance slices of [OBSTACLES_AND_BLOCKAGES.md](OBSTACLES_AND_BLOCKAGES.md). The route search now accepts actual collision volumes, tries directed blockage chains and validates accepted continuous trajectories through the shared scene collision helper. [One-input wall reversals and ceiling profiles](ONE_TICK_CONTACT_PLAN.md) are now implemented through the corrected shared movement compiler.

## Input

Use `sceneModel: collision_boxes`. Supply each block's actual world-space collision sub-boxes once, plus the region whose geometry is supplied:

```json
{
  "version": 1,
  "sceneModel": "collision_boxes",
  "geometryBounds": [-10, -10, -10, 20, 20, 20],
  "initial": {
    "position": [0.5, 1, 0.5],
    "velocity": [0, -0.0784000015258789, 0],
    "yaw": 0
  },
  "startBlock": "start",
  "goalBlock": "goal",
  "blocks": [
    {"id": "start", "boxes": [[0, 0, 0, 1, 1, 1]]},
    {"id": "wall", "boxes": [[1.7, 0, 0.25, 2, 4, 0.75]]},
    {"id": "goal", "boxes": [[3, 0, 0, 4, 1, 1]]}
  ],
  "maxJumps": 1,
  "maxRunTicksPerJump": 0,
  "maxTotalRunTicks": 0,
  "maxContactBranches": 24,
  "maxBlockagesPerObstacle": 3,
  "maxHorizontalBlockagesPerObstacle": 2,
  "maxVerticalBlockagesPerObstacle": 2,
  "maxPassageCandidates": 128,
  "maxWallHits": 2,
  "maxWallHitCandidates": 24,
  "ceilingHits": true
}
```

Box order is `[minX,minY,minZ,maxX,maxY,maxZ]`. The adapter derives candidate top supports and a shared `StaticScene` description from those boxes. A multipart block produces separate support IDs `id#0`, `id#1`, etc.; start/goal IDs must identify the desired part. Its disjoint parts remain separate solids and support footprints. A single-part block keeps its ID.

The normalized result retains collision solids, support provenance and finite coverage bounds. A query outside coverage returns unknown for that branch. The model is normal 1.8.9 movement on static inert boxes with normal friction and step height. Ladders/vines use explicit integer `climbableCells` plus their real collision geometry through the [ladder route search](LADDER_STRATEGY_PLAN.md). Unsupported surface/callback behavior remains rejected. The legacy `support_planes` modes still work and retain their explicitly narrower acceptance scope.

This adapter declares a clear initial input state with the supplied position/velocity/yaw and effective sprint policy, without an incoming wall hit or held-jump cooldown. Additional initial resume fields are rejected. Collision-box profiles use the shared movement compiler and input-state implementation. Additional initial resume fields remain outside this adapter.

## Candidate generation and constraints

The first landing filter uses the existing Y model's normal jump height, approximately 1.249187 blocks. A higher top is excluded as a landing from that launch, while its volume remains an obstacle. The chosen Y trace then determines whether over/under faces can be used and when. A tall wall with neither vertical option leaves only horizontal passages; reachable tops and ceilings retain horizontal alternatives.

For each obstruction found by the shared collider, the planner generates face chains rather than independent arbitrary walls:

- One horizontal blockage is a **cross neo**.
- Two horizontal blockages are a **wide neo**.
- Chains using Y faces describe passing over or under. A chain may contain both vertical and horizontal blockages; the neo name counts horizontal ones.
- Per physical block, the caps are two horizontal transitions, two vertical transitions and three combined. A vertical transition involves a Y face; a horizontal transition joins X and Z faces. `maxHorizontalBlockagesPerObstacle`, `maxVerticalBlockagesPerObstacle` and `maxBlockagesPerObstacle` may tighten these limits. Larger values are rejected. Zero-transition branches represent staying outside one face.

The six legacy timing templates are preserved: X->Z, Y->X and Y->Z require an outside-both state; Z->X, X->Y and Z->Y permit consecutive-state handoffs. Every face also has a signed boundary, and the approach/exit intervals remain outside their chosen faces. The face can change at a later corner, so leaving one side does not create a permanent half-plane wall.

Different blocks keep independent chains on the same timeline. A wall can have gates at 4 and 10 while a ceiling has a gate at 7, yielding wall -> ceiling -> wall without finishing one obstacle first. The constraints for the wall remain active during the ceiling passage, and the numerical solver optimizes the common positions/facings together. Gates may coincide when their state constraints are compatible; the shared collision helper still checks the actual Y-X-Z movement stages.

Cap accounting uses each passage's physical `blockId`, shared by all collision sub-boxes and retained across selected route legs. Refining one chain replaces its old usage while reserving other chains' usage for that block. Passing a different block does not reset the first block's budget. There is no global three-event limit across different blocks.

Passage gates are relative to the transition's departure. Adding runs retimes the gates and all associated rows; extending the run-up also extends the first outside-face interval. Both earlier facings and run allocation remain adjustable. Geometry-derived waypoint guesses account for carried velocity using the existing scalar model and compiled friction; these guesses add no facing constraints.

Height masks and conservative forward/backward reach envelopes prune impossible face/time choices. Between-gate movement bounds exclude corner pairs requiring more movement than the profile can supply. On the first leg, the fixed initial state and allowed ground runs tighten the bound further. Side preference ranks remaining candidates by adjustment to the current trajectory; it does not delete the other side. The existing per-axis interval screen still rejects impossible compiled rows before IPOPT.

## Acceptance and shared implementation

For every complete candidate, orchestration validates the horizontal numerical request, strict support/exit predicates and the generated Y schedule. It then sends the intended X/Z displacements and raw Y movement to the existing Java `StaticScene` helper. That helper resolves actual Y-X-Z clipping, both step alternatives and body-volume clearance against all supplied solids. Orchestration compares the resulting positions and ground state with the declared profile and rejects every unplanned horizontal hit. It does not instantiate another movement engine or export/replay a TAS.

Collision diagnostics identify the actual limiting sub-box and axis. Step diagnostics identify which side obstruction triggered a selected step attempt. A mismatch generates a new blockage branch, subject to the declared budget. The outer solve does not trust the trajectory or feasibility flag returned by IPOPT.

Run-offs include their source in the same solid set. Departure clearance alone no longer admits later source penetration. Source avoidance is active while the body overlaps its finite Y span, including on later route legs, and allows passage underneath once the head clears the actual bottom. The side-run-off fixture leaves a nearby Z edge with one added run instead of traveling to the far X edge.

## Search limits and output

Total elapsed inputs remain the primary rank; the numerical objective ranks equal-time witnesses. Goal branches continue after the first witness and preserve the best accepted time/objective result. Normal failure, budget exhaustion and an excluded passage family are unknown, not general infeasibility.

`maxContactBranches` bounds the combined floor/blockage/wall-hit branch search. `maxWallHits` defaults to two selected wall hits per route and `maxWallHitCandidates` to 24 retained candidates; zero wall hits disables that family. `ceilingHits` defaults to true and adds one-ceiling profiles to the transition graph. `best.hitEvents`, `best.ceilingEvents` and `best.plan.sceneContacts` expose the selected relative/absolute events. `attempts[].wallHitSearch` reports generation, candidate drops and tested branches. These limits are separate from per-block blockage caps. `maxPassageCandidates` bounds retained alternatives per discovered obstruction, with additional explicit bounds on timing-state expansion and its queue. `attempts[].obstacleSearch` reports generated candidates, reach pruning, vertical-face pruning, dropped candidates/timings, incomplete generation and accepted strategy kinds. `best.passages` stores the selected faces, blockage types, physical block IDs and gates. `best.blockageTimeline` merges their absolute gate states for review, including separate first/second states for consecutive-state handoffs. `best.blockageUsage` reports horizontal, vertical and total counts per physical block. `best.geometryCheck` records final shared-scene acceptance. A deadline retains an already validated incumbent.

`minimumTicksProven` only compares a witness with the static graph bound for the declared current profile family. It is not a certificate over omitted contact-selected steps, sustained-contact families or every Minecraft strategy. `objectiveOptimalityProven` remains false. Passage complexity, numerical budgets and queue/candidate caps are explicit scope limits.

## Run and measured cases

Prepare the Java coefficient compiler as in [README.md](README.md), including rebuilding it after this change, then:

```sh
PKC_IPOPT_PYTHON="$PWD/.venv-ipopt/bin/python" \
python3 -m standalone.autonomy.solve_blocks \
  --input standalone/autonomy/fixtures/obstacle-wide-neo.json \
  --output standalone/autonomy/build/wide-neo-result.json \
  --seconds 35 --route-seconds 25 --candidate-seconds 1 --retries 0
```

The actual IPOPT/Java integration tests find a 12-input cross neo, a 12-input wide neo, a five-input side run-off and a 12-input passage under a ceiling. A further [interleaved wall/ceiling case](experiments/interleaved-blockages/README.md) solves in 12 inputs with two horizontal gates for the wall and two vertical gates for the ceiling; the four events interleave on one timeline while both per-block caps hold. A tall, broad wall retains no reachable passage in that fixed single-jump family and is never accepted. [Saved requests and results](experiments/obstacle-avoidance/README.md) include independent scalar and shared swept-collision checks. Gate compilation is also cross-checked against all 24 signed cases from the Minecraft-method probe.

## Next contact families

The route search now reuses existing numerical X/Z and Y contacts with the integration agent's corrected `FixedMovementCompiler`/`ReplayInputState`. Side-wall reversals use one clipped input and normal-side avoidance before/after; ceiling profiles regenerate Y and landing time. Actual intended movement, finite face/timing and collision masks pass shared scene checks. The shared compiler handles sprint loss and air-factor recovery. The constant-effective-sprint adapter remains only for legacy ordered/support-plane models.

Generated ceiling durations are included before time bounds are computed. [Saved contact results](experiments/one-tick-contacts/README.md) cover a wall reversal, a three-input bonk and a simultaneous Y/X hit. Next broaden multi-leg evidence, generalize same-solid approach/exit corridors and handle contact-selected steps. One ladder/vine interaction per route is now implemented separately in [LADDER_STRATEGY_PLAN.md](LADDER_STRATEGY_PLAN.md). Replay, float-angle realization and TAS export remain with the integration agent.


Ladder scenes use a separate bounded route evaluator with ordinary prefix/suffix profiles and one selected climbing interaction. It reuses blockage avoidance and whole-scene acceptance. Its repeated climb hits are independent of reversal-hit caps, and its results always leave minimum-time/objective proofs false. See [the ladder plan](LADDER_STRATEGY_PLAN.md) for settings, diagnostics and remaining contact-composition limits.
