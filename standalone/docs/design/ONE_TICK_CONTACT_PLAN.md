# One-input wall and ceiling contacts

2026-10-27. Shared movement compilation, exact selected-event scene checks, automatic side-wall reversals and ceiling-profile discovery are implemented. This refines [OBSTACLES_AND_BLOCKAGES.md](OBSTACLES_AND_BLOCKAGES.md) using the user's contact families. Existing avoidance and interleaved blockages remain implemented as described in [OBSTACLE_SEARCH.md](OBSTACLE_SEARCH.md).

## Contact families and scope

| Family | Purpose | Selected contact |
| --- | --- | --- |
| Side-wall hit | Cancel the wall-normal velocity quickly, then move in the opposite normal direction | One input with X or Z clipping |
| Ceiling hit | Cancel upward velocity and shorten the time to the next landing | One input with upward Y clipping |
| Ladder/vine climbing | Sustain contact to climb | Implemented separately in [LADDER_STRATEGY_PLAN.md](LADDER_STRATEGY_PLAN.md) |

One input describes each selected hit episode, not a limit of one contact in the whole route. Sustained wall pressing, sliding with repeated normal clipping and climbing are outside this slice. Separate selected episodes may occur later. Existing per-block H2/V2/combined3 caps count blockage transitions, not wall contacts; do not silently change that definition. Contact/event candidate budgets must be reported separately.

Keep sprint-jump/sprint-45 keys and free facings. A reversal concerns the wall-normal component; the tangential component can retain momentum. No facing pin or requirement for a 180-degree camera turn follows from selecting a wall hit. A ceiling hit changes the Y profile, not horizontal sprint by itself.

## A contact event and its schedule

Each orchestration event identifies the physical block, sub-box/solid index, signed face, leg/event anchor and local input offset. Derive the player-center contact plane from actual geometry and widened legacy float body dimensions. Also retain its finite tangential intervals, contact family, before/after avoidance choices and selected hit window. These are planner metadata projected into the existing numerical contact and positional-row schemas, not new fields in the shared scene parser.

For input t, p[t] is the incoming position and p[t+1] the result. The existing numerical contact uses state t+1. Its intended displacement is evaluated before clipping. Selecting the event produces three phases:

1. **Before input t:** avoid this solid at every swept collision stage. The solver must not hit it a tick earlier and then reuse a movement array compiled for a later hit.
2. **During input t:** approach the selected face, actually attempt to move beyond it, overlap its finite tangential extent at the correct stage, and clip against that face. Preserve avoidance of its other faces unless another contact is explicitly selected on the same input.
3. **After input t:** avoid further unintended clipping. For a side-wall reversal, the next movement is away in the normal direction. For a ceiling, the regenerated Y trace descends after the selected cancellation. All other solids and later route legs retain their checks.

Avoidance constrains movement, not a blanket strictly positive distance at every endpoint. p[t+1] is on the hit plane. It must be legal to start the next input touching that plane and move away. Likewise, arriving exactly at a face without clipping on the preceding input does not establish an earlier hit. Shared clipping predicates and the intended-versus-resolved displacement distinguish these cases.

The complete assumed contact mask is part of compilation: selected horizontal hits are true on their result states; all other unselected horizontal hits are false. Ordinary floor/support contacts remain allowed according to the Y profile. Matching only the number of hits is insufficient; match tick, axis, direction and limiting face. Equal coplanar limiting solids may be geometrically equivalent, but an occluded or nearer different face is not the selected event.

## Side-wall constraints

Let a be X or Z, c its expanded face plane, and d in {-1,+1} the direction into the wall. Let u[t,a] be intended displacement after inertia and acceleration but before collision. Emit:

```text
d * (p[t,a] - c) <= 0
d * (p[t,a] + u[t,a] - c) >= hitMargin
p[t+1,a] = c
v[t+1,a] = 0
```

Use the worker's existing positive clipping guard, currently 1e-7, and validate actual clipping with the shared scene helper. The independent scalar validator currently allows equality in its reach test, so scalar validity alone does not prove a sprint-cancelling hit. Do not add a second inertia threshold or clear the tangential carry.

The finite overlap guards follow the actual Y-X-Z sweep:

| Selected contact | Body coordinates used for tangential overlap |
| --- | --- |
| X wall | New Y, old Z |
| Z wall | New Y, resolved new X |
| Ceiling | Old X, old Z |

Use strict body/box interval overlap on both tangential axes. An X/Z pair on the same input is a composite selected event, resolved in order; Z guards use X after its clipping. A successful step can replace provisional side clipping, so the scene helper must confirm the final movement outcome rather than accept an apparent wall intersection.

For a simple reversal while the face remains relevant, before-hit rows keep the trajectory on its approach side through p[t]. The selected input alone can attempt to cross it. The next result p[t+2] moves back onto the approach side, with an explicit outward normal displacement guard. Subsequent no-hit constraints cover the remaining relevant interval. If the route later rounds an edge, hand this interval to ordinary blockage choices rather than extending an infinite wall through the rest of the course.

The first implemented family holds the same approach-side half-plane within this leg's Y-overlap intervals. Same-leg rounding of the contacted solid remains an extension; it is not claimed by these reversal candidates. All other solids retain their usual passage/scene checks. `maxWallHits` defaults to two selected hits per route and `maxWallHitCandidates` to 24 retained candidates; the joint `maxContactBranches` cap still applies. Separate physical blocks can be hit on one input, and separated later inputs/legs can select another episode. Consecutive same-axis hits are excluded. Generated/dropped candidates and tested hit branches are reported. The [saved reversal checkpoint](experiments/one-tick-contacts/README.md) and cold X/Z, both-sign regressions establish the first family.

In general the finite solid can be avoided through another face, including above or below it. Reuse outside-face chains and same/consecutive-state gates before and after the hit, split at the selected contact. Do not force the hit-state body outside the touched face, drop the obstacle for adjacent inputs, or only check the immediately preceding/following input. The event and avoidance intervals must retime together when run inputs are inserted.

## Ceiling constraints and Y regeneration

For a ceiling with bottom b and player height h, the feet plane is c=b-h. At the selected input require upward intended movement, approach from below, strict reach beyond c and finite old-X/old-Z overlap. Anchor the feet at c, cancel the upward component, then use the existing Y carry recurrence. The next stored Y velocity is the gravity/drag rest carry, not zero; with no new jump or climbing the next input moves downward, giving a single upward-clipping input.

Before the selected input, forbid earlier underside clipping. Where the body is horizontally below the solid, the raw Y sweep must remain clear until the chosen hit; elsewhere ordinary finite-face avoidance applies. After the hit, rebuild the entire remaining Y trace to the chosen landing, including ground flags, duration and support checks. Check downward movement and no extra unselected Y contact with the scene helper. Other earlier floors and neighboring ceilings still matter.

Regeneration changes horizontal overlap windows, landing time, later event offsets and run allocation. Rebuild all affected blockage guards and recompile movement, then solve from the original initial state. A ceiling-only event must not set the horizontal-collision sprint flag. Generate possible ceiling hits proactively in reachable height/time windows: a valid ordinary flight can still be slower than an undiscovered bonk.

Implemented: `ceilingHits` defaults to true in collision-box mode. Every relevant underside contributes a candidate ordinary-jump profile with one ceiling event. Its hit input is derived from the upward plane crossing. Before that input the generated Y sweep remains below the underside, and after it the trace descends, so these scalar conditions supply the ceiling's before/after avoidance. Finite old-X/old-Z overlap is emitted at the selected input; shared stage checks reject any shifted, missing or extra upward hit. No redundant free Y variables are introduced. All resulting durations enter the transition graph before reverse time bounds are computed, while ordinary and horizontal-avoid alternatives remain. One ceiling per transition is the current generated family; contact-selected steps and more complex vertical skeletons remain outside it.

## Compilation and independent acceptance

Reuse the Real 11 prototype's existing `standalone/ipopt/worker.py` prescribed X/Z contact rows, `model.py` scalar validation and `ladder.py` vertical contact recurrence. Its ladder regressions already cover wall detection, normal reset and carried climb velocity. No new contact integrator is needed. Ladder/vine route generation is now implemented separately; see [LADDER_STRATEGY_PLAN.md](LADDER_STRATEGY_PLAN.md).

The sprint/air-factor bug is already fixed by the integration agent in `af12801`, documented in `RECENT_REPLAY_HANDOFF.md`. Historical Real 11 arrays predate that correction; recompile schedules through the corrected compiler rather than copying their coefficients. Do not implement a second fix.

Import the reviewed shared `FixedMovementCompiler`, `ReplayInputState` and required dependencies from `feature/ipopt-mod-integration`; retain the existing sprint machine and exact input arithmetic. Do not duplicate them in Python. Compile after selecting keys, runs, ground and contacts, and compile again when any of those assumptions changes. A hit and an avoidance branch need their own movement arrays.

The standalone helper now materializes unchanged shared Java sources from pinned commit `f057c0ce9b454ae1ce2706843bb141cce799b5cb` into ignored build storage and compiles them in an isolated helper classpath. Its newer core APIs do not replace this worktree's native solver files. The local extended `StaticScene` remains the shared collision source. The Git object must be available to rebuild; no network download or mutable checkout is used. The movement-contract validator and its four tests were imported from `af12801` unchanged. The first checkpoint passed 113 orchestration tests and seven focused movement/contact checks.

The compiler accounts for next-input sprint cancellation and delayed air-factor recovery. The plan must not approximate those effects by switching one friction constant on the hit input. Validate the returned movement contract against the solved schedule.

Implemented: `obstacle_geometry.check_geometry` obtains raw intended X/Z from the compiled scalar trajectory and uses shared `StaticScene` resolution. The Java scene-check action matches selected contacts instead of rejecting every horizontal collision. Resolved positions, normal reset/tangential carry, Y/ground and selected collision masks must agree; extra hits, unplanned steps and uncovered scene queries remain rejected. Ladder inputs now apply clamping before collision resolution, as specified in [LADDER_STRATEGY_PLAN.md](LADDER_STRATEGY_PLAN.md).

Checking raw movement is necessary even when a numerical contact has anchored the endpoint correctly: feeding only the shortened displacement into the collision helper would present mere arrival at the plane and lose the collision that determines sprint state.

## Implemented slices and evidence

1. **Shared compilation and event-aware acceptance.** The corrected compiler and raw-movement scene checks are wired. Prescribed contact checks cover sprint loss/recovery, tangential guards, paired X/Z hits, repeated hits and boundary arrival.
2. **One-input side-wall reversal generation.** Signed X/Z faces and conservative reachable input windows generate bounded hit/avoid branches. Finite guards and same-side before/after avoidance retime with runs; full-prefix facings remain free. Cold regressions solve both axes and both signs. Disabling hits leaves the reversal control unknown.
3. **One-input ceiling generation.** Reachable underside crossings regenerate Y and landing durations, with finite footprint rows. Ordinary, under/side avoidance and existing landing/stair alternatives remain. A floor-touching ceiling can generate a two-input profile even when the ordinary flight exceeds the configured horizon.
4. **Time bounds and joint search.** Shorter ceiling durations enter graph/time bounds before search. Wall hits consume an existing input, not an extra appended tick. Total elapsed inputs rank first, numerical objective second. Run insertion retimes contact and avoidance phases together.
5. **Coupled benchmarks and evidence.** Saved geometry-only requests produce a 12-input wall reversal, a three-input ceiling bonk, and a three-input simultaneous Y/X contact. Requests/results, timings, movement masks and scalar/shared-scene acceptance are in [experiments/one-tick-contacts](experiments/one-tick-contacts/README.md). Existing avoidance cases explicitly disable intentional contacts to preserve their original test scope.

Generate plausible beneficial contacts even if the current witness has no collision. Collision-driven repair remains useful, but cannot discover every faster deliberate contact. Height/stage windows and conservative reach bounds prune impossible hit times before numerical work. Deferred climbing does not require sustained-contact or ladder-state enumeration in this slice.

## Acceptance cases

- X and Z reversals, both signs: one actual clipped input, next movement outward, zero normal carry and preserved tangential carry.
- Nominal hit shifted one input early or late, or repeated on the next input: rejected against the compiled schedule.
- Endpoint arrival without actual clipping: accepted only as no-hit movement, rejected as a selected hit.
- Finite-face misses and a nearer intervening solid: cannot satisfy the selected contact through an infinite-plane anchor.
- Two selected X/Z contacts in one input, correct stage overlap, and step-assist superseding a provisional wall hit.
- Sprint cancellation, jump eligibility and delayed air-factor decrease/recovery around a side hit, checked against the shared compiler.
- Ceiling cancellation on the selected input, downward following input and regenerated shorter landing time; no horizontal sprint loss for a ceiling-only hit.
- Run insertion moves contact and pre/post avoidance together; another obstacle's passage may interleave on either side of the event.
- A faster bonk branch survives graph pruning even when the ordinary collision-free branch already succeeds.

Acceptance remains a valid continuous-model IPOPT solve under correctly generated movement and finite geometry, independently checked against the shared movement/collision contract. Game end-to-end testing, angle realization, replay and TAS export retain their existing separate ownership.

Final gate: **120 orchestration tests passed in 101.636 s** with Java/IPOPT integration enabled; **51 standalone numerical tests passed in 25.102 s**. The repeated native core gate still has the same three documented baseline failures out of 798 tests, with 33 skipped. No native solver source changed.

## Remaining work

- Generalize a wall reversal's same-side corridor to approach/exit blockage chains around that same solid. Current generation does not claim same-leg corner rounding after a hit.
- Broaden multi-leg/repeated-contact benchmarks and measure candidate drops before increasing event/queue limits. Distinct episodes are representable, but saved cold evidence currently covers single reversals and one coupled ceiling/wall input.
- Add contact-selected step/ceiling competition and more complex vertical skeletons. Existing selected stair profiles still depend on shared scene acceptance; general step discovery is not added here.
- Ladder/vine route generation is now implemented as a separate family in [LADDER_STRATEGY_PLAN.md](LADDER_STRATEGY_PLAN.md). Reuse existing numerical mechanics, generate many cheap input/Y schedules, then solve promising candidates. Its consecutive climb hits are separate from this reversal family.
- Replace the pinned shared-source build dependency with the eventual common integrated source tree when both branches are combined. Until then the pinned Git object is required for rebuilding and native sources remain untouched.
- Improve unknown reasons and broaden real-course geometry coverage. Bounded candidates, branches and local IPOPT failures do not establish infeasibility or global objective optimality.
