# Ladder-ceiling ownership and generation

2026-10-30. The user requested an implementation plan for the remaining ladder-ceiling work. Implementation is now authorized. Latest scope: only climbing and its optional one-input stop-climbing periods. Leave existing Sneak and falling behavior unchanged; do not expand those families. The generic open movement composer is already implemented; preserve that interface and the standalone-only acceptance scope.

## Implementation checkpoint

Implemented in `b49a205`: one neutral finite ceiling factory; ladder-local ownership and repeated-sequence prediction; prefix alternatives and duplicate suppression; regenerated schedules with the required climbing successor; local effects and contact offsets preserved through the generic composer. Entry-producing normal ceiling/wall contacts transfer to climbing when the next unit establishes ladder membership. Normal approach/final-air ceilings retain their ordinary ownership. Physical arithmetic is unchanged.

The implementation checks successor phase ownership, not the presence of a horizontal hit. Thus the allowed endpoint stop remains climbing and uses the actual unclipped or clipped scalar movement to determine carry. Ceiling effects remain low-level `role=ceiling` finite contacts, with `effect=ladder_ceiling`, owning type/region and the required successor added as metadata. A unit has one effect for each physical ceiling contact; normal ceiling effects are removed when ownership transfers.

Fresh complete witnesses for continuous climb, beginning stop, end stop and both stops pass IPOPT, scalar and shared scene acceptance. They use the same finite scene and no Sneak. The end-stop variants reach the goal in 10 inputs versus 12 for their corresponding continuous-end variants. This verifies the height change for the supplied schedules, not global optimality. The [saved evidence](experiments/ladder-climbing-ceiling/README.md) contains full numerical problems and all-state Y/VY tables.

Acceptance: 64 focused standalone regression methods pass in 54.220 seconds, including fresh solves, the legacy approach/second-ladder/final-air ceiling witnesses, generic five-ladder and stepping continuations, schedule generation/units and combined Y/X/Z contacts. The additional saved-evidence test revalidates all four new files in 0.574 seconds. No mod Java tests or game replay tests were run; no numerical worker arithmetic changed.

Search remains bounded. Sequence prediction proposes finite contacts; it does not establish horizontal overlap without a numerical solve. Existing mixed-contact branching and shared scene acceptance enforce selected clips and reject extra collisions. Ceiling effects outside the climbing phase keep the legacy physical handling; no new falling/Sneak behavior or route pruning was added. Cold exhaustive ladder discovery, ranking efficiency and water remain separate work.

## Scope and existing behavior

Ceiling clipping, ladder carry, repeated ceiling contacts and combined horizontal contacts already have scalar and numerical coverage. `ceiling_sequence` supplies shared scalar prediction; `mixed_schedule_pool` and `mixed_ladder.with_contacts` generate/replay variants. `open_ladder.ladder_choices` currently reaches them through the generic mixed-contact pool.

The remaining work is explicit ownership, one source of ladder-ceiling proposals, coherent regeneration of the schedule and integration with the generic composer's boundary resolution. Keep `ladder_ceiling` a local event within the ladder interaction. Its immediate successor is required to be `ladder_climb`. Keep ordinary normal-air ceilings in the registered `ceiling` type. Preserve the existing single-climbing-phase grammar within one ladder hitbox.

## Physical contract

Reuse `vertical_move`, `vertical_step`, `vertical_carry`, shared finite geometry and the existing contact compiler. Do not add another movement equation or collision resolver.

Y resolves before X and Z. A horizontal hit ending inside a ladder can refresh stored climbing velocity to about +0.1176. When that refresh does not apply, use the existing normal vertical recurrence on the actual resolved movement, including any real ceiling clip, ladder cap or Sneak effect. Do not introduce an unconditional velocity reset when climbing stops. For example, without clipping or Sneak, stored VY evolves +0.117600 -> +0.036848 -> -0.042289. Ceiling clipping is a separate collision operation; the presence of a ladder-ceiling event alone does not prescribe the following carry.

Continued wall contact can produce ceiling hits on consecutive inputs. Omitted beginning/end hits, Sneak, entry and exit can change the sequence. Normal-movement one-input contact restrictions must not cap a ladder ceiling sequence at one hit.

The allowed immediate transition is `ladder_ceiling -> ladder_climb`. Reject direct transitions from `ladder_ceiling` to `ladder_fall`, normal movement, stepping or an exit. Subsequent movement becomes available through the resumed climbing phase and its legal exit. Encode this requirement in the ladder-local phase grammar; the generic route queue remains type-independent. Do not infer a climbing refresh from this label: the actual horizontal contact and membership determine it.

## Step 1: extract ladder-owned prediction and ownership

Add `ladder_ceiling.py` for ladder-specific candidate sequences, local effect ownership and schedule integration. Reuse the neutral ceiling primitives. Retain compatibility re-exports where callers/tests need the old names.

Represent a local effect with its input, feet plane, actual finite box/provenance and owning phase/region. Keep the low-level physical `role=ceiling`; add ladder-specific effect/owner metadata. Existing scalar/scene compilation continues to consume the same Y contact.

Resolve ownership from the composed units and actual pre/post membership. This includes entry-producing climb inputs and direct transfers. When appending a ladder resolves ownership of a previously open boundary, transfer the existing ceiling effect to the ladder owner without adding a second physical contact. A normal approach/exit air interval retains normal ceiling ownership.

Gate: existing scalar Y/VY/contact arithmetic is unchanged, every physical ceiling contact has one resolved owner, and each generated ladder-ceiling event satisfies the required climb successor.

## Step 2: give ladder generation one ceiling provider

Have the ladder schedule path call the local provider with its existing climbing and one-input stop candidates. Reuse the current mixed scheduler for X/Z contacts and combinations. Route upward Y opportunities through the owning ceiling provider, deduplicating them against already selected contacts.

Keep simultaneous ceiling plus X, ceiling plus Z and ceiling plus X/Z combinations, consecutive contacts and repeated ceiling sequences. Generic contact compilation remains shared; it must not independently rediscover another copy of an owned Y event. Preserve current work limits and report truncation/cancellation as incomplete coverage.

Gate: repeated and combined contacts remain available within the corrected local grammar. Direct ladder-ceiling-to-fall candidates are excluded. No separate route template or rarity pruning is introduced.

## Step 3: regenerate coherent schedules

Feed selected ceiling variants into the existing scalar schedule generator and replay. Recompute Y, VY, actual refreshes, phase masks and valid entry/exit times. A changed ceiling history may require a later exit; do not attach metadata to the old schedule or freeze its original exit tick.

Use scalar prediction to propose sequences and validate regenerated candidates against the selected finite contacts. Reuse existing enumeration/work limits for alternatives that require a changed stop/Sneak/duration choice. Preserve all positional and input constraints through retiming.

Ceiling overlap is checked at the pre-input X/Z state because Y resolves first. Enforce the chosen upward clip on its selected input. On other inputs, require avoidance only where their Y sweep could actually collide. Remaining beneath the ceiling at a lower height is legal. Use the existing obstacle/contact branch helpers for the required avoidance alternatives.

Gate: emitted schedules have matching controls, contact times, Y history, phase grammar and exits before numerical solving.

## Step 4: integrate the generic open program

Keep `append_choice` and the shared queue type-independent. Ladder-owned ceilings travel as local effects with the required `ladder_climb` successor. The resumed climbing phase can later hand off its actual exit state to normal movement, stepping or another ladder through the existing interface.

Preserve complete scalar history, entry-producing carry and coincident effects during phase normalization. Keep finite X/Z wall contacts and shared sprint compilation. Never fix an intermediate optimizer X/Z position, velocity or facing.

Gate: a composed ceiling/ladder interaction can continue without an artificial landing or state reset, and the normal ceiling generator does not claim a ladder-owned input.

## Step 5: focused standalone validation

Scalar/generation tests:

- One ceiling hit, continued pressing and several consecutive hits.
- Beginning stop, final omitted hit and both stops, with their expected Y/VY and exit changes.
- Normal +0.1176 -> +0.036848 -> -0.042289 evolution on unclipped stop inputs. Existing Sneak/falling code is outside the implementation scope.
- Acceptance of `ladder_ceiling -> ladder_climb`, rejection of direct `ladder_ceiling -> ladder_fall` and other immediate successors, and no inference of a velocity refresh from the event label.
- Entry-input ceiling plus horizontal hit, inside-ladder hit, exit-input hit and direct-transfer ownership.
- Ceiling plus X, plus Z and plus X/Z on the same input, and consecutive-input combinations.
- Normal approach/final-air ceiling ownership, retiming and duplicate suppression.
- Missing, mistimed, extra and wrong-box contact rejection; finite footprint/stage constraints.

Fresh complete IPOPT plus scalar/shared-scene checks:

- Repeated ceiling contact while pressing a ladder wall.
- A stopped-hit variant whose height/carry/exit differs as predicted.
- A ceiling variant that needs a later exit and a following unit that consumes that actual state.
- Normal ceiling -> ladder-owned ceiling -> ladder_climb -> legal exit -> normal/stepping continuation through the common composer.
- Existing ladder-ceiling, appended-ladder-ceiling and final-air-ceiling regression witnesses.

Run focused standalone tests during the work. Run numerical regressions if shared scalar/worker behavior changes. Do not run the mod's Java test suite or game replay tests. Search speed/pruning and water remain separate work.

## Commits

1. Extract local helpers and explicit ownership, preserving behavior.
2. Integrate proposal deduplication, regeneration and generic boundary ownership.
3. Add new numerical witnesses, run relevant standalone regressions and update the handoff.
