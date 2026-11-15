# Obstacles, contacts and directed blockages

2026-10-26. Investigated against the mod, its cached Minecraft 1.8.9 movement code, the earlier block solver and the integration agent's current helpers. The geometry/height/avoidance slices are now implemented in [OBSTACLE_SEARCH.md](OBSTACLE_SEARCH.md). One-input wall reversals and ceiling profiles are implemented as recorded in [ONE_TICK_CONTACT_PLAN.md](ONE_TICK_CONTACT_PLAN.md); one ladder/vine interaction is implemented separately in [LADDER_STRATEGY_PLAN.md](LADDER_STRATEGY_PLAN.md). General contact-selected steps and multiple climbing interactions remain later work. The existing run-ticks and route-search interfaces remain the inner search.

## Findings grounded in code

The Forge simulator calls Minecraft's inherited movement, rather than resolving collisions itself. In 1.8.9, `Entity.moveEntity` obtains collision boxes for the swept player box, clips Y, moves the box in Y, clips X, moves it in X, then clips Z. `AxisAlignedBB.calculateXOffset/YOffset/ZOffset` tests overlap on the two other axes at that stage. Clear full-tick endpoints alone do not establish a clear movement.

Writing p[t] as the pre-input feet-center position, the intermediate positions on a no-step movement are:

```text
before Y: (x[t],   y[t],   z[t])
after Y:  (x[t],   y[t+1], z[t])
after X:  (x[t+1], y[t+1], z[t])
after Z:  (x[t+1], y[t+1], z[t+1])
```

Here the updated coordinates are the resolved ones, including any earlier clipping. Consequently:

| Contact axis | Tangential overlap used by the contact |
| --- | --- |
| Y, floor or ceiling | Old X and old Z |
| X | New Y and old Z |
| Z | New Y and new X |

The current standalone numerical API can prescribe X/Z contacts and scalar floor/ceiling contacts. It anchors the contacted coordinate and clears normal carry, but does not establish the finite face's tangential overlap or choose which obstacle is first. Those conditions belong to orchestration. `ConstraintDeriver.deriveWall` supplies the horizontal player-center boundary; its click/facing-based footprint clipping selects one region, not every possible avoidance branch.

The old `SweptCollision` is recoverable from `538c92b^`. It gets the ordinary Y-X-Z sweep and first blocking box, but excludes step-assist and prioritizes a horizontal failure in its result. Its old acceptance allowed Y hits under recorded Y. It is a reference, not the new acceptance oracle. [The post-mortem](../../docs/research/block-solver.md) records that limitation; [PR #379](https://github.com/Leg0shii/ParkourCalculatorMod/pull/379) confirms the unused implementation's removal.

The integration branch already supplies better shared machinery:

- `core/.../replay/StaticScene.java`: finite inert sub-boxes, sequential clipping, both step alternatives, collision flags, normal velocity reset and coverage checks. This class and its seven regression tests were imported unchanged from `f057c0c`; it now also exposes clipping and step-trigger diagnostics for orchestration.
- `forge-core/.../replay/ReplayInputState.java`: the existing `PlayerSprintMachine`, processed inputs and previous air-factor state.
- `forge-core/.../replay/FixedMovementCompiler.java`: movement arrays compiled from fixed keys, ground and contact assumptions, using the existing exact kernel. Its Python movement contract rejects changed contact/ground schedules.

The latter two are available on `feature/ipopt-mod-integration`, with the wall-aware fix in `af12801`. They require that branch's newer effects/input APIs and movement callback. Adopt the shared compiler and its dependency set before enabling intentional wall-hit profiles, rather than rewriting or partially porting its mechanics. Our current `CompileRunTicksPhysics` assumes a constant effective sprint policy and must not compile the new wall-hit family. This plan does not turn replay or TAS export into an orchestration acceptance requirement.

## 1. Scene geometry and temporary roles

Represent geometry once. A block has a stable ID, one or more actual world-space collision sub-boxes and its supported surface/behavior metadata. Each box has six bounds, including bottom and top. Preserve the existing `BlockSelection.boxes` capture data; the outer hull is useful for broad-phase queries only. Do not infer a bottom from a support top, merge separated shapes into their bounding rectangle or treat a render outline as collision geometry.

Use the shared scene format for the geometry projection:

```json
{
  "version": 1,
  "profile": "minecraft-1.8.9",
  "bounds": [-10, -10, -10, 20, 20, 20],
  "solids": [[2, 0, 0, 3, 4, 1]],
  "stepHeight": 0.6
}
```

Keep block ID/sub-box provenance and candidate support-face IDs in the orchestration wrapper, with a stable map to `solids` indices. This avoids adding incompatible fields to the shared scene parser. Coverage bounds describe where supplied geometry, including empty space, is known. A query outside that region is unknown, not unobstructed air.

Obstacle and landing support are profile-relative roles, not mutually exclusive labels on a block:

- A top face is a candidate support if the chosen Y profile can reach it from above on a downward movement, and horizontal support can be present at the Y collision stage. Earlier supports and head clearance must also be consistent.
- A sub-box is an obstruction whenever it can clip a stage of the movement. Its height determines when the player can overlap it, together with player height, current vertical velocity and selected contacts.
- The same block can be a landing candidate, an obstructing side face during approach and a source obstacle after departure. Intentional floor, ceiling, wall and step contacts are alternative events on the same geometry.

Use a cheap binary height test first. For the current normal 1.8.9 jump, the existing Y recurrence gives a maximum feet rise of 1.2491870787446813 blocks, approximately 1.24919. If a box's top is higher than the current launch support plus that rise, remove both landing on it and passing over it from that ordinary-jump candidate. Below that limit it remains a potential landing/over candidate, subject to the usual horizontal and collision-stage checks. Obtain the threshold from the shared Y model rather than copying the rounded decimal into a separate physics implementation. Recompute relative to each selected launch support; the classification need not be global across the whole route.

Next use the box bottom, player height and selected Y trace to classify under/ceiling possibilities. This shrinks the event family before generating signed edges or numerical requests:

| Height class for this transition | Vertical candidates | Horizontal candidates |
| --- | --- | --- |
| Top within the jump's reachable height | Pass over; land as a separate route transition | Pass left or right |
| Underside reachable by the player's head, with a possible passage below | Pass under without contact; hit the underside and regenerate Y | Pass left or right |
| Neither over nor under possible in the available Y/time range | None for this profile; remove all Y-involving blockages | Pass left or right only |

The first two classes can overlap for a small suspended shape. A tall obstruction can occupy the last class even when its bottom is above the launch feet: there may be too little head clearance to get underneath and too much height to get over it. Use actual bottom/top bounds and body height rather than a universal obstacle-height label. A reachable top does not remove its obstructing sides. Landing changes the support route, so it is not an avoidance blockage.

Height also restricts when a candidate can pass. From the existing Y simulation, derive the state/stage windows in which feet clear the top, the full body clears the bottom, a descending top contact can occur, or an upward underside contact can occur. Empty windows eliminate that option. A short remaining window gives a shorter horizontal reach bound to the relevant face/edge, just as a shorter flight profile gives a smaller landing radius. Apply conservative reach bounds or the existing interval screen at those actual states; there is no reason to send a late, unreachable corner crossing to IPOPT.

Keep the existing ordinary, stair and run-off generators as initial profile families, then recompute Y, passage windows and reach bounds after each selected Y contact. A classification for one fixed vertical skeleton must not remove a different skeleton whose earlier contact or launch height changes the available motion.

For feet-center coordinates and legacy half-width w and body height h, expand each collision box into forbidden reference-point intervals:

```text
X: [box.minX - w, box.maxX + w]
Y: [box.minY - h, box.maxY]
Z: [box.minZ - w, box.maxZ + w]
```

Use the actual widened legacy float dimensions from the collision model. Keep face touching, strict overlap and actual clipping distinct using its predicates. For example, a mere arrival at a horizontal face without clipped displacement is not a sprint-cancelling wall hit.

## 2. Directed blockage representation

A blockage describes moving from outside one signed face to outside another signed face around their common edge. Store:

```text
solid/sub-box ID
firstFace  = (axis, sign)
secondFace = (different axis, sign)
edgeSpan   = the finite interval on the remaining axis
leg/event anchor
candidate pass-state interval
timing     = derived from the version's collision order
```

Each box has 12 geometric edges. Traversing each edge in either direction gives 24 signed blockage orientations: six axis pairs, with four sign combinations per pair. The named pair is the order of outside-face regions visited by the player, not the order of collision calculations.

These are the representation's possible orientations, not an instruction to enumerate all 24 for every obstacle. Apply the height classification first, then build the relevant left/right passage chains from the feasible approach and exit faces. With no vertical passage, the face graph reduces to the four horizontal faces. Its common passages use one blockage to cross an adjacent edge, or two blockages to go around a side between opposite faces.

Let E(A,k) mean that the player is outside signed face A at state k. For the ordinary legacy Y-X-Z sweep:

| Directed blockage | User's family | Required corner transition |
| --- | --- | --- |
| X -> Z | X-facing | E(X,k) and E(Z,k) |
| Y -> X | X-facing | E(Y,k) and E(X,k) |
| Y -> Z | X-facing | E(Y,k) and E(Z,k) |
| Z -> X | Z-facing | E(Z,k) and E(X,k+1) |
| X -> Y | Z-facing | E(X,k) and E(Y,k+1) |
| Z -> Y | Z-facing | E(Z,k) and E(Y,k+1) |

In general, if the first face's axis is processed earlier than the second face's axis, an outside-both state is needed. In reverse order, consecutive-state clearance suffices for the local corner handoff. The [Minecraft-method probe](experiments/blockage-order/README.md) checks all six pairs and all four sign combinations, plus outside-both controls, using Minecraft's actual AABB offset methods.

The rows at k are the handoff gate, not a complete arbitrary-trajectory collision test. A compiled branch also needs an outside-first approach interval and an outside-second exit interval over the relevant finite face. Otherwise a nonmonotone trajectory could enter the box before or after the gate. An edge stops being relevant when the remaining coordinate is clear of its finite span; another face/edge may then describe the passage. Preserve distinct sequences for a path that rounds multiple corners or revisits an obstacle.

Y-facing predicates are evaluated against the selected scalar Y profile. Enumerate pass ticks where they hold, then emit the corresponding X/Z inequalities. If none hold, this Y profile cannot realize that blockage; consider a different Y contact/profile instead. Do not relax the impossible Y row or hide it in a horizontal constraint. Ordinary movement does not require adding Y variables to IPOPT.

Blockages guide passage choices. Full stage-by-stage collision validation still checks all boxes, including neighboring parts of a multipart shape. Internal or occluded sub-box edges cannot justify passage through another part. Use exterior/visibility screening to prioritize sensible edges, while full geometry is the acceptance guard.

Search the common cross neo/wide neo chains first, with at most two horizontal, two vertical and three combined transitions per physical block. Mixed three-transition chains are allowed; four-transition chains are outside the current family. Collision sub-boxes and selected route legs share their physical block's budget. A chain carries a small ordered list of face transitions and compatible pass windows; it does not generate the Cartesian product of arbitrary edges and every tick. Propagate the windows through the same/consecutive-state timing rules, then apply reach screens between gates before solving.

Left/right is relative to the current approach and onward route, while the collision constraints retain world X/Y/Z axes. Rank the sides using an optimistic passage distance/time and the incoming momentum/required turn as ordering hints. One side will often be clearly preferable, so try it first. Remove the other side when its valid time/reach bound excludes it; a longer geometric detour or a worse local turning score alone is not a proof that it cannot help the rest of the route. Neighbors can make the initially less direct side the only useful one.

## 3. Hit versus avoid is another branch

The implemented contact slice is narrowed to [one-input side-wall reversals and ceiling hits](ONE_TICK_CONTACT_PLAN.md). Each selected hit needs finite reach/overlap guards and avoidance before and after it so actual clipping matches the compiled tick. Sustained ladder/vine contact is implemented as a separate family in [LADDER_STRATEGY_PLAN.md](LADDER_STRATEGY_PLAN.md). One input is per hit episode, not per route; contact budgets remain separate from blockage caps.

For a relevant block, retain avoidance and legal contact alternatives. Do not declare every collision a failure or force every apparently useful contact.

| Selected event | Required geometry and movement consequences |
| --- | --- |
| Avoid through a blockage | Correct same/consecutive-state gates, approach/exit intervals and no unplanned clipping |
| Hit an X face | Approach and actual normal clipping; finite overlap at new Y/old Z; anchor X and clear X carry; retain Z movement |
| Hit a Z face | Approach and actual normal clipping; finite overlap at new Y/new X; anchor Z and clear Z carry; retain X movement |
| Land on top | Downward Y contact at old X/Z; ground state changes; subsequent movement and next jump remain possible |
| Hit underside | Upward Y contact at old X/Z with head reaching the bottom; clear vertical velocity, apply normal gravity/drag and regenerate the remaining Y profile/air time |
| Step/cancel | Select the existing step alternatives under the correct incoming-ground/downward-collision guard; keep the branch only if the actual distance comparison selects it |

A contact event refers to a particular box and signed face, input offset, collision stage, reach/approach guards and tangential overlap guards. X and Z contacts can coexist in an input. A Y hit changes the Y used by both horizontal stages of that same input. The nearest blocking face among all supplied boxes must match the selected event. A prescribed infinite plane is not sufficient evidence.

Normal inertia handling is an outcome of actual clipping, not a change to the .005 inertia threshold. A wall zeroes its normal component before drag. Tangential components retain the applicable ground/air drag. A floor or ceiling zeroes vertical velocity before gravity/drag, so the next stored vertical carry is not generally zero. Ground/surface changes choose subsequent coefficients.

Horizontal clipping also changes sprint. With a single wall hit during input t and W+sprint held:

| Input | Sprint state and air-factor consequence |
| --- | --- |
| t | Uses the pre-existing sprint/air state; the hit is discovered during movement |
| t+1 | Previous horizontal-collision flag cancels sprint, including jump boost eligibility; air acceleration still uses t's factor |
| t+2 | Sprint may restart if the key is held and there was no further hit; air acceleration reflects t+1's cancelled sprint |
| t+3 | Air factor has recovered if sprint restarted |

Repeated hits and other input conditions alter that timeline. The shared input/compiler implementation is the authority, not this explanatory table. Recompile after changing any hit, key, ground, Y-contact or run schedule. An avoidance branch must not inherit the sprint loss of a different hit branch.

After a run-off, include its departure source as an ordinary obstacle throughout relevant Y-overlap intervals. Being clear at departure does not permit later side penetration. Once the player's full vertical box clears above or below a sub-box, its horizontal footprint does not require avoidance. Reactivate checks if vertical overlap returns. The source uses the same blockage/contact machinery as other boxes; see [RUN_OFF_OBSTACLES.md](RUN_OFF_OBSTACLES.md).

## 4. Search and optimality

Extend the existing shared queue entry from support route plus profile IDs to include selected contact events, directed blockages and pass-time choices. Store choices relative to transition events, not absolute global ticks; inserting runs retimes their rows, contact states and movement arrays together. Every numerical evaluation starts from the original initial state and can change all earlier facings, run allocations and eligible profile choices. Do not freeze one arrival velocity or deduplicate all paths reaching the same support.

Candidate generation:

1. Query actual sub-boxes intersecting a conservative swept reach envelope, including player dimensions and allowed incoming momentum. Use Y ranges to remove irrelevant geometry for that profile. A center-line ray or current witness alone is not a sound scene filter.
2. Apply the binary maximum-jump-height filter and the under/ceiling height bands. Generate only remaining landing tops and allowed Y contacts, including ceiling/step alternatives that change air time. Restrict their passage/contact windows and horizontal reach using the existing Y trace.
3. For each vertical skeleton, generate the relevant left/right one- and two-blockage chains rather than every signed edge. Propagate their pass windows and screen each required gate. Try the preferred side first, and retain a no-new-contact branch and plausible intentional wall-hit branches. A locally feasible avoidance path does not prove that a wall hit cannot improve an equal-time objective.
4. Solve the relaxed/selected branch, inspect the first unselected collision or invalid contact with the shared scene helper, and lazily branch over local avoid/hit/land/ceiling/step explanations. A diagnosis can add a new obstacle, edge, contact or pass tick to the existing candidate pool.
5. Recompile the full chosen event/input schedule with the shared movement compiler and solve again. An accepted branch must meet its contact reach, finite geometry, support, Y schedule and movement-state assumptions. A changed event is a new branch, not a success under stale coefficients.

Lazy repair alone is insufficient for tick optimality. For example, a collision-free ordinary jump can be slower than a deliberately ceiling-cancelled jump that the nominal path never hit. Generate possible shorter Y-contact families proactively within the broad envelope, rather than discovering contacts only on failed incumbents. Keep unresolved branches separate from proven reach contradictions.

Retain the user's lexicographic rank:

```text
(total elapsed inputs, negative objective for MAX / objective for MIN)
```

For a fixed contact/profile schedule, the current run-ticks allocator still supplies the added runs. Across schedules, every elapsed input counts, including run-offs and contact-producing inputs. Contact count and route simplicity are evaluation-order hints, not primary objectives.

The current graph's ordinary-flight duration is not an admissible time bound once a ceiling can create a shorter profile. Rebuild bounds over the supported contact families; where that set is incomplete, use an explicitly optimistic floor such as one input per possible transition rather than an overestimate. Similarly, broaden reach bounds to include all allowed input/contact regimes. Known normal-velocity resets can tighten a selected branch, but a generic free-flight failure must not remove every differently timed contact family.

Once T inputs is best, prune only a valid completion lower bound greater than T. Retain equal-T branches for objective improvement. Do not prune them by an unproved objective estimate. Queue/contact/profile/event caps and local numerical timeouts remain explicit heuristic limits, so results are best found unless a valid bound is reached. Neither local IPOPT failures nor a bounded blockage search establish global infeasibility or objective optimality.

The current caps are two horizontal, two vertical and three combined per physical block. Preserve different blocks' chains on one shared timeline: A -> B -> A is legal, and intervening B events do not reset A's counters. Count all selected chains and sub-boxes of A together, and consider every allowed chain whose valid time bound can match or beat the incumbent even if a simpler route already exists. Complexity and left/right preference order candidates within the relevant time search; they do not replace total elapsed ticks as the primary objective.

## 5. Implementation slices and acceptance

1. **Shared geometry, height classification and collision diagnostics.** Import the common scene helper, accept actual collision sub-boxes with provenance, apply the cheap 1.24919 normal-jump height split, then derive under/over/contact windows and conservative reach intervals with the shared Y model. Add a diagnostic view of resolved collision stages/box IDs in that shared helper if needed, without another collision engine.
2. **Avoidance blockages.** Compile all six timing templates, but generate only the height-compatible left/right one- and two-blockage chains, with propagated finite approach/exit/pass windows. Integrate lazy passage branching and source run-off checks using fixed ordinary Y profiles first. Validate that a nearer side exit can be attempted without requiring the old four forward run inputs. Permit mixed three-transition chains under the two-horizontal/two-vertical/three-total per-block caps.
3. **Intentional X/Z hits and shared movement compilation.** Bring in the existing compiler and dependencies unchanged, wire the orchestration adapter to it, then generate finite hit guards and coefficient assumptions. Keep .005 inertia independent of collision reset. Include paired hit/avoid cases that exercise sprint loss and air-factor lag.
4. **Y hits and step choices.** Generate top/underside contacts, recompute Y/air time, retain legal step/cancel alternatives and refresh timing lower bounds. Cover Y->X/Y->Z versus X->Y/Z->Y handoffs and interactions with subsequent horizontal clipping.
5. **Joint search.** Retain route/profile/contact/blockage/time alternatives under total-input ranking. Compare a small explicit family with exhaustive enumeration, and test that a faster deliberate contact can beat a longer collision-free route. Acceptance stays within the continuous model; the integration agent retains replay/export work.

Regression gates include all six signed blockage families; clear endpoints with an obstructed intermediate stage; finite face height/span; passing below after head clearance; multipart and overlapping boxes; two contacts in one input; normal reset with tangential carry; held sprint across hit/recovery; ceiling-shortened air time; step-distance competition; run insertion retiming; source run-off side/corner passage; and a valid lower bound that never removes a faster contact profile.

Pruning regressions cover tops just below/above the derived jump-height limit, a tall obstruction with horizontal options only, a ceiling retaining both under/hit and side options, a reachable top retaining both land/over and side options, short Y windows excluding unreachable gates, one-blockage crossing versus two-blockage wraparound, both left/right approaches, and a locally longer side retained because it improves or enables continuation. Per-block horizontal/vertical/combined caps must be visible in the reported candidate family, including shared accounting across multipart geometry and interleaved events.

Current evidence is 24 signed Minecraft-method corner cases plus their outside-both controls. The imported scene tests cover wall normal reset, strict tangential overlap, Y-before-horizontal landing, step/ceiling competition, sneak carry, ladder-triggered carry and scene coverage. These verify building blocks, not a completed autonomous obstacle solver.
