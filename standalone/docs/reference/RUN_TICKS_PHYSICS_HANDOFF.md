# Block-sequence and minimum-run-ticks physics handoff

Prepared 2026-10-26 for the autonomous-solve agent. The task is to model an ordered sequence of blocks, insert run-up ticks and find a feasible route with as few added run ticks as possible. This document points to existing physics/search code and identifies assumptions that can otherwise produce false feasibility or false minimum claims. Read [AUTONOMOUS_AGENT_HANDOFF.md](AUTONOMOUS_AGENT_HANDOFF.md) for solver setup/API/worktree instructions. Work in your own worktree; this integration worktree remains owned by the Minecraft/mod adapter agent.

Terminology corrected in this isolated copy: run-off means leaving a block without jumping and then falling. Normal movement followed by a jump is not a run-off transition.

## Read these first

The repository contains the relevant Minecraft Parkour Wiki material locally. No external wiki access is needed. The wiki's decimal formulas are explanatory approximations; use its documented constants and float chains plus the version-correct code when constructing a precise model. Do not copy the wiki's illustrative angle convention into Minecraft yaw unchanged.

| Source | What to extract |
| --- | --- |
| [CONTEXT.md](../../CONTEXT.md) | Facing versus movement direction, feet position, stored velocity versus displacement, run ticks, tiers, run-up and jump terminology. |
| [Movement formulas](../../docs/reference/mcpk/01-movement-formulas.md) | Ground/air acceleration, sprint-jump boost, input normalization, drag, inertia threshold and vertical recurrence. |
| [Collisions and stepping](../../docs/reference/mcpk/03-collisions-and-stepping.md) | Player AABB, legacy Y-X-Z collision order, support/landing timing, step-assist, blips and jump-cancels. |
| [Blocks and friction](../../docs/reference/mcpk/04-blocks-and-friction.md) | Actual shapes, normal/ice/slime friction, partial blocks, ladder activation versus solid ladder shape. |
| [Effects, tiers and durations](../../docs/reference/mcpk/05-status-effects-tiers-jumps.md) | Useful initial flight-time hypotheses, height bands and effect differences. |
| [Timings and momentum](../../docs/reference/mcpk/06-timings-momentum-glitches.md) | Immediate jumps versus delayed runs, why extra run ticks can change rather than uniformly improve a route. |
| [Angles and sine table](../../docs/reference/mcpk/02-angles-and-mouse.md) | Game-facing conventions, float yaw and why a continuous witness is distinct from a game witness. |

Initial scope should be explicit: Minecraft 1.8.9, static normal blocks, tapped jumps, declared sprint/input policy and a fixed ordered route. Heights, ceilings, special surfaces and alternate input families can be added deliberately. A minimum within one fixed jump/input/timing family is not a global minimum over every possible Minecraft movement strategy.

Pointers below resolve on the clean integration branch. An agent on an older native-research base may lack newer helpers or tests. Read those files with git show from feature/ipopt-mod-integration when necessary; do not merge unrelated native solver history or overwrite concurrent work merely to obtain a reference helper.

## Code map for movement and block constraints

| Source | Use and limitation |
| --- | --- |
| [JumpPhysicsInputs.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/JumpPhysicsInputs.java) | Per-input jump, ground/air, movement, sprint/effects and initial state. Read contactAt, slipAt, factorSprintAt, airFactorSprintAt and slice. Ground state is authored here, not inferred from block geometry. |
| [JumpLinearModel.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/JumpLinearModel.java) | precompute produces mMag, baseArg and friction for the standalone request, including the sprint-jump contribution. Reuse this rather than guessing a ground-speed constant. |
| [ExactJumpModel.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/ExactJumpModel.java) | Float/lookup-table arithmetic and movement order. Its class contract explicitly says collision-free and authored ground state. Its Y is not clamped onto block tops. It is not a world-landing oracle. |
| [Constants.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/Constants.java) | Version-sensitive float constants and movement attribute chains. |
| [SupportOverlap.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/SupportOverlap.java) | Strict legacy support overlap and exact representable center bounds; avoids hand-written .3 edge rules. |
| [ConstraintDeriver.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/ConstraintDeriver.java) | Player-center wall offsets, support footprints, coplanar support handling and obstacle clipping. Inspect deriveWall and deriveFootprint. Click/facing-based footprint clipping can choose one usable region; it is not an exhaustive enumeration of every feasible region around obstacles. |
| [ConstraintKeyController.java](../../core/src/main/java/de/legoshi/parkourcalc/core/ui/ConstraintKeyController.java) | How selected world geometry becomes authored support/wall constraints through the existing ports. Useful integration example, not an autonomous planner. |
| [Simulator.java](../../core/src/main/java/de/legoshi/parkourcalc/core/ports/Simulator.java), [SimulationRunner.java](../../core/src/main/java/de/legoshi/parkourcalc/core/sim/SimulationRunner.java) | Actual Minecraft movement/collision via the loader, state snapshots and resume/checkpoint handling. |
| [MinecraftAccess.java](../../core/src/main/java/de/legoshi/parkourcalc/core/ports/MinecraftAccess.java) | Collision-shape and climbable queries for scene export. Integration agent owns the live bridge. |
| [StartResumeState.java](../../core/src/main/java/de/legoshi/parkourcalc/core/sim/StartResumeState.java) | Resume ground/contact/sprint/held-input state, in addition to position/velocity/facing. |

JumpPhysicsInputs.copy currently shares many per-tick arrays. Clone mutable arrays before modifying a candidate in parallel or inserting rows; a new wrapper object alone does not provide independent search state. Its slice method demonstrates which lagged movement factors and per-tick fields need to follow a seam.

## Essential state and tick conventions

Use feet-center position p[t], stored incoming velocity v[t] and grounded/contact state at the start of input t. Input t produces state t+1. Yaw/input arrays have n entries; position/velocity arrays have n+1. All standalone indices are local and zero-based. A constraint at state t concerns the position before input t. Surface/move samples captured for input t are stored on TickState t+1; incoming ground and velocity are from TickState t.

Retain X/Z velocity between blocks. Landing cancels clipped vertical velocity, not horizontal velocity in general. Do not restart every jump at zero speed or at the block center. Normal resting stored Y velocity is `-(0.08 * (double) 0.98F)`, approximately -.0784000015, rather than an arbitrary zero. Preserve sprint/effect/input history needed by the version's movement-factor rules. Initial position/velocity alone do not describe all game resume state.

Suppose state L is the first grounded state after a landing. With r inserted run ticks, inputs L through L+r-1 are the run-up, and input L+r is the next jump. With r=0, the player can jump immediately at input L: do not insert an extra mandatory idle/ground tick. The landing-producing input was L-1, and the jump input itself is not an added run tick.

The user always taps Jump, so do not add held-jump cooldown search. Jump eligibility still requires the correct grounded state when that input starts. A JUMP flag or a declared DEFAULT slipperiness override is not proof that a landing actually happened.

## Ground, air and jump coefficients

For intuition on normal 1.8 blocks, ignoring float rounding:

| Input starts in | Acceleration scale before key normalization | Horizontal stored-velocity multiplier |
| --- | --- | --- |
| Grounded on default surface | About .1 walking or .13 sprinting | About .91 * .6 = .546 |
| Airborne | About .02, or .026 with the appropriate sprint factor | About .91 |
| Grounded sprint-jump | Ground acceleration plus a .2 boost along facing | Ground multiplier for that input |

These are explanations, not replacement literals for the compiled arrays. Ground acceleration also depends on slipperiness through its cube, and move inputs pass through the .98 scaling/normalization. Forty-five strafe and W-only are not interchangeable. The sprint-jump boost points along facing, whereas strafe acceleration points along the key-rotated movement direction. Their vector sum is what JumpLinearModel compiles into fixed magnitude/base.

Takeoff starts grounded. Do not change the jump input to air coefficients merely because it moves upward. Conversely, an input starting airborne and landing during its movement initially selected the airborne regime; the following input uses the new grounded state. This distinction changes the momentum available for a zero-run-tick jump.

Run ticks insert real movement and additional ground drag; they are not zero-length waits or free acceleration. They must fit on the finite support, with actual Y/ground checks. A short pad can become impossible with too many run ticks. Slippery blocks alter both drag and acceleration. Sprint/air factors and effects can have lagged or sampled semantics; use the existing compiler/getters instead of assuming every SPRINT key immediately supplies identical coefficients.

For 1.8 inertia, each incoming component with absolute value below .005 is zeroed before acceleration. Later versions differ. Removing inertia or caps can change the available braking as well as travel distance; those changes are not automatically sound feasibility relaxations. The standalone request currently supports per-axis inertia; a combined-inertia export using threshold 0 is an explicit approximation.

## Blocks, support and landing timing

Represent a block by its actual collision AABB(s), support top height and movement properties. A single integer block coordinate is sufficient only for an explicitly restricted full-cube scene. Slabs, fences, panes and stairs differ in height/width or have multiple shapes. Surrounding ceilings/walls matter even if only landing blocks are listed. Missing surroundings must be an explicit simplified-scene assumption, not an assertion that an actual Minecraft route has no obstacles.

The nominal player box is .6 wide and 1.8 tall; the position is its bottom center. Legacy support uses half-width `(double) (0.6F / 2.0F)`, approximately .30000001192092896. For a support interval [a,b], legacy overlap in one horizontal axis is:

```text
center + halfWidth > a
center - halfWidth < b
```

Equivalently the conceptual center interval is (a-halfWidth, b+halfWidth), with strict overlap and actual floating evaluation. Apply both X and Z checks. The center can be outside the block while the player's edge catches it. Shrinking the support by .3 would instead require the entire body footprint on top, imposing a much stronger condition. Exact edge touch is not support. Use SupportOverlap.minCenter/maxCenter for representable inclusive bounds, rather than treating mathematical endpoint equality as a valid landing.

The numerical solver allows ordinary row residuals up to 1e-7, which is larger than a one-ULP support adjustment. Therefore an accepted numerical bound alone does not guarantee strict physical overlap at a knife edge. Check the actual support predicate independently, or specify a deliberate geometric clearance and still replay the result. Do not silently widen the support because IPOPT's scalar row tolerance permits it.

Legacy movement resolves Y, then X, then Z. In the simple no-step case, a downward floor collision during input t is evaluated at the pre-horizontal X/Z location p[t]. The final X/Z at p[t+1] is reached afterward. Merely constraining p[t+1] to overlap a target top can claim a landing when horizontal arrival was too late for that tick's Y check. Conversely, horizontal movement may end beyond the support after a real Y landing, retaining a grounded flag and allowing the next input to jump. This is normal movement and does not establish stationary support at the end of the input.

For the simplified first model, either deliberately restrict each landing/run interval to stable support, or model this order correctly and validate it. Clearly state which family is searched. Stable-support restrictions are useful, but any minimum is conditional on them. Do not call a restriction a universal Minecraft impossibility rule.

Landing also requires downward movement crossing the top plane from above; a player below a block top cannot be snapped onto it because a horizontal interval matches. Account for head clearance and swept, axis-ordered collisions. Clear endpoints alone do not rule out collision during the tick. A wall plane needs height and tangential overlap; the standalone solver currently assumes its prescribed plane events and does not check these world conditions.

Tier tables give useful first flight-time hypotheses: the reference lists 12 inputs for an ordinary flat jump, 10 for +.5 and 9 for +1. Compute/replay the discrete vertical trace and check support at the actual collision phase instead of assigning 12 to every transition. Height, ceilings, jump boost, the .005 Y threshold and stepping can change the schedule. A lower or higher support is not only a horizontal reach problem.

## Existing run-tick machinery to inspect

| Source | Useful behavior and caveat |
| --- | --- |
| [RunTicksController.java](../../core/src/main/java/de/legoshi/parkourcalc/core/RunTicksController.java) | Removes old run rows, identifies jump taps, rebuilds inserted candidates, shifts constraints, solves prefixes and restores the document. Read start, applyStep, rebuild, dropPreviousRunTicks and pollStep. It mutates editor state, so use it as a reference rather than an off-thread headless API. |
| [RunTicksRows.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/runticks/RunTicksRows.java) | A run row is non-JUMP with an explicit DEFAULT slipperiness override. The predicate does not require W/sprint specifically. Rows on other surfaces do not satisfy that marker. |
| [RunTicksSearch.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/runticks/RunTicksSearch.java) | Generic prefix tree with payload/warm starts and total-added-tick rungs. In minimize mode it enumerates exact total sums. |
| [RunTicksFilter.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/runticks/RunTicksFilter.java) | Per-jump authored RT bounds, ranges and equalities. RT belongs to the outer search, not IPOPT's scalar walls list. |
| [AngleSolverState.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/AngleSolverState.java) | onRowsInserted/onRowsRemoved move tick-anchored constraints, references and start/landing/objective locations. |
| [StepTimeouts.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/runticks/StepTimeouts.java), [RunTicksSettings.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/runticks/RunTicksSettings.java) | Existing native prefix timeouts/adaptation. Their roughly 200 ms defaults are not requirements for IPOPT; the user accepts several seconds. |

The controller inserts before a jump, copies a prior row, clears JUMP, sets W and SPRINT and gives inserted inputs DEFAULT slipperiness. It can preserve other inherited keys. It also marks the shifted jump input DEFAULT so takeoff uses ground coefficients. It copies range constraints from a preceding template and rebuilds candidates from a baseline. When designing a new block planner, derive the run corridor from the actual support; blindly copying a prior row's landing range can overconstrain a different schedule.

Important search semantics in this exact code version:

- Non-minimize mode enumerates allocations whose total is at most maxTicks. Minimize mode starts its first exact-sum rung at the configured maxTicks, not necessarily zero. Start at zero or a sound lower bound when seeking the smallest total.
- MAX_UNCONSTRAINED_TICKS is an implementation cap of 40, not a physical limit or proof that larger totals are impossible.
- pollStep sends an unsuccessful result, including a timed-out search, to recordFailure. That prunes the branch for this heuristic search. Do not reuse this as a certified infeasibility oracle.
- Prefix solve results/payloads are useful seeds. Fixing one chosen prefix's facings/exit state for all descendants can lose routes using another feasible prefix. Reoptimize relevant prefixes or retain alternatives at coupled seams when necessary.

## Inserting run ticks into standalone requests

Adding r inputs changes ticks and every per-input/state array. Rebuild coefficients from the new input/ground schedule; duplicating an air magnitude/friction entry does not create a ground run tick. Do not keep the original flight's hard Y reference arrays as new-schedule truth.

Maintain stable block/jump/event identities, then map them to new ticks. Shift physical contacts, constraints, objective and any ladder/Y event schedules consistently. Relative constraints require both referenced ticks to move correctly. Earlier candidates' yaw arrays have the wrong length after insertion; remap existing guesses and supply guesses for inserted rows rather than passing them unchanged to solve. Explicit inertia/cap patterns must also be remapped or reinferred. The feasibility label from the previous schedule does not transfer.

The numerical loop remains candidate construction, prepare, solve and independent validate. stop_on_feasible is appropriate for an initial witness; optimize is useful for margin/exit quality among equal-cost candidates. Existing cold/warm fallback and incumbent retention can help. A budget expiry is unknown. Standalone physical hashes cover numerical coefficients/constraints, not geometry stored only as source metadata; include scene/start/input-family identity in outer cache keys.

Do not enforce each target block's full footprint as a sequence of infinite wall constraints on unrelated flight ticks. Landing, standing, obstacle avoidance and intentional contact have different meanings. With walls/caps/steps prescribed, the current scalar validator checks that model only. Integration will provide actual Minecraft replay feedback; a block planner must either use that feedback or explicitly declare its simplified geometry model.

## What minimum feasible means

Define the cost explicitly, usually R = sum(r_i), with r_i the added grounded non-jump inputs before transition i. Specify whether the initial run-up counts, whether all blocks must be visited, which takeoff/landing times and input keys may vary, permitted ceilings/special surfaces and any per-jump caps/RT constraints. Minimize added run ticks is not automatically minimize elapsed time if flight structure changes. Do not allow uncounted idle/ground-delay rows to evade the intended cost.

Feasibility for exactly r added ticks at one location is not generally monotone: another tick may leave a short pad, alter braking, change landing timing or harm a later jump. Search total-cost allocations rather than binary-searching an assumed monotone exact count. The existential predicate 'some candidate uses at most K ticks' is nested by definition when the candidate family is truly fixed, but exploiting that still needs a trustworthy feasibility oracle; a local IPOPT unknown is not a negative answer.

A useful result contract distinguishes a witnessed best cost from a proved minimum. Record best feasible allocation/cost, a sound lower bound, unresolved cheaper hypotheses and the declared search/model family. A continuous witness at cost 0 achieves the nonnegative run-tick lower bound within that family. At positive cost, unresolved smaller candidates prevent a minimum certificate. Enumeration in increasing cost does not eliminate this issue: the first found witness may follow several false-negative local searches.

Keep unknown cheaper candidates for escalation using another seed, more time or a sound bounding method. Only sound contradictions or relaxations that include all allowed physics/pattern choices can reject them conclusively. Dropping clamps, inertia or geometry constraints without proving feasible-set inclusion is not automatically such a relaxation. It is acceptable to report 'best found: 2 added run ticks; 0/1-tick cases unresolved' while continuing search.

For a first implementation, exact-sum enumeration 0,1,2,... with reusable prefixes, bounded few-second probes and explicit unresolved tracking is easy to audit. For equal cost, preserve objective quality and useful exit position/velocity; the endpoint coordinate alone does not always predict continuation. The user expects most seams to be simple and only a minority to need alternatives, so avoid requiring a large beam everywhere.

## Tests worth borrowing and adding

Existing focused references:

- [RunTicksSearchTest.java](../../core/src/test/java/de/legoshi/parkourcalc/core/anglesolver/runticks/RunTicksSearchTest.java): exact rung allocations, no repeats, starts at configured count and prefix bookkeeping.
- [RunTicksRowsTest.java](../../core/src/test/java/de/legoshi/parkourcalc/core/anglesolver/runticks/RunTicksRowsTest.java): run-row marker versus jump/airborne rows.
- [RunTicksFilterTest.java](../../core/src/test/java/de/legoshi/parkourcalc/core/anglesolver/runticks/RunTicksFilterTest.java): per-jump bounds.
- [RunTicksRollbackTest.java](../../core/src/test/java/de/legoshi/parkourcalc/core/RunTicksRollbackTest.java): document restoration.
- [SupportOverlapTest.java](../../core/src/test/java/de/legoshi/parkourcalc/anglesolver/SupportOverlapTest.java): strict edge contact and exact representable support bounds.
- [LandingConstraintsTest.java](../../core/src/test/java/de/legoshi/parkourcalc/anglesolver/LandingConstraintsTest.java): authored footprints, ranges and unrelated constraints.
- [AirborneStartJumpTest.java](../../core/src/test/java/de/legoshi/parkourcalc/anglesolver/AirborneStartJumpTest.java): jump eligibility at an airborne start.
- [Standalone tests](tests/): current IPOPT API, cap/inertia/vertical and process regressions. The original handoff gives full commands and 200/400 fixture expectations.

Add small block-sequence cases that isolate: zero-run immediate continuation; a truly required extra tick within a declared family; width-expanded edge catches versus exact touch; arriving horizontally after the Y landing check; takeoff/landing friction differences; unequal-height/ceiling timing; enough support for every run row; extra ticks making a short support fail; two allocations of the same total having different outcomes; correct constraint/event/seed remapping after insertion; cheaper unknown candidates blocking a minimum claim; and independent candidate arrays with no cross-branch mutation.

Numerical tests should include known feasible witnesses and controlled impossible bounds, not just failure to solve a cold candidate. Record first-feasible time, accepted cost/allocation, objective/margin, physical assumptions and unresolved cheaper counts. Keep at least one game/replay comparison for the landing-order and ground-transition cases when integration makes it available. No new physics or run-tick behavior was implemented for this handoff; it is based on inspection of the current source.
