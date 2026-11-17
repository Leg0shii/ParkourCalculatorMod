# Stepping terminology and cases

User definitions, updated 2026-10-30. These are the orchestration vocabulary and case descriptions. They supersede the earlier broad blip/grinding labels and the proposal to enumerate Minecraft's internal step branches as the primary movement types.

## Tick terminology

- **Landing tick L:** the last air position tick before the lower block's landing/contact would resolve.
- **Hit tick H:** the following position tick, H = L + 1. A stepping case can change the resulting height on H.
- **Jump tick J:** the position tick after applying the jump impulse. Usually feet Y is about 0.42 above the jumping block; step/ceiling cases can change that result.
- **Jump input:** the input applying the impulse. With the existing pre-input indexing, input t produces position state t+1, so J = t+1.

The position label does not change the input's movement constants. The input producing a normal jump tick begins grounded; the input producing a blip hit tick begins airborne.

Terminology conflicts to resolve in adapters/docs:

- Internal `JumpPhysicsInputs.jumpTick` names an impulse-applying input, not the user's resulting jump position tick. Preserve its API meaning and translate explicitly.
- Internal route event `landing` commonly denotes the resolved contact/end state H; the standard pre-landing support check is on L. Use explicit L/H names in the new case layer.
- Existing `hit`/`hitInputs` masks generally mean horizontal wall collisions, especially on ladders. Do not confuse them with the user's landing-related hit tick.
- The previous glossary's broad `Blip` included airborne/unsupported outcomes. The user distinguishes blip from blip-up by the landing/higher-height relation and hit result.
- **Grind** means the specified ceiling jump-cancel variant. One occurrence is sufficient. The old glossary wording was broader; the code does not require repetition.
- Jump cancel remains an umbrella description, not a synonym for only slab boost or only grind. The mirrored external reference retains its historical broad wording; the project definitions here are authoritative for this work.

## Accepted cases

Let a be the lower block's height, b the higher block's height, yL the landing-tick feet height, and yFreeH the next feet height from ordinary air Y movement without a Y obstacle. The lower block's usual landing-tick X/Z constraint remains present where landing is required.

### Normal stepping

Grounded movement rises by at most 0.6. All inputs in this movement are run inputs; there is no invented air tick or jump impulse. Use the appropriate before/after block constraints.

### Slab boost / stair boost

The position before the jump tick is on the lower level. The jump tick is on the higher level, with the specified rise approximately 0.42 through 0.6. The jump impulse and usual jump horizontal movement apply, while stepping changes the Y outcome. One air input follows the jump, producing the ordinary two-input airtime/cycle before subsequent movement.

### Blip

The higher block is a standard wall on L. Generate avoidance alternatives and actual retained wall-hit alternatives. A real hit uses the normal finite-wall contact constraints, horizontal velocity cancellation and sprint transition; it does not imply reversal.

The ordinary air schedule lands on the lower level, and the resulting hit tick steps to the higher level. Require 0 <= b-yL <= 0.6. The landing tick satisfies the lower landing constraint; the hit tick additionally satisfies the higher block's X/Z condition and yH=b. The movement producing H is still air movement. Normal grounded movement/jumping follows from the higher level.

### Blip-up

Require yL>b and yFreeH<a, so the lower landing actually happens on this input rather than leaving another air tick. The hit tick moves into the higher-block region but retains the landing-tick height: yH=yL>b. It is grounded for subsequent movement despite being above the higher surface.

The blip-up case ends at that hit state. A jump taken from it starts at the elevated height; its resulting position is the next jump tick. With no jump, use the normal run-off continuation: one ground-regime input, then air as needed until another vertical landing/step. Do not extend a special blip-up mode across that continuation or invent a support at yH.

### Grind

The tick before the jump tick is outside a low ceiling; the jump tick is under it. The available head clearance is less than the nominal 0.42 jump rise. This stepping/ceiling combination cancels the rise entirely, so the jump tick has the same feet Y as the preceding tick, followed by one air input and ordinary continuation. With a flat floor that following input lands, giving the same two-input cycle as a stair boost. Different floor geometry can produce a longer ordinary continuation.

This differs from an ordinary ceiling hit: a ceiling hit can leave a positive rise up to the ceiling, and can occur while already under it or later in the jump. The grind's specified entry geometry and zero-rise result must be retained.

### Corner jump cancel

This is an X-facing corner avoidance case. The obstacle is about 0.42-0.6 above the lower level. The position before J is in front of the obstacle's X face; J clears its Z edge. Upward velocity is cancelled as in a grind, with the usual subsequent gravity/carry. Use the successive-state timing of a Z-facing blockage: outside the first face before J and outside the Z edge on J, rather than requiring both clearances at the same state.

It is an avoidance case, not a planned retained X wall hit. Preserve ordinary jump horizontal movement unless another actual contact says otherwise. X-facing describes the geometry, not a camera-facing pin. The Z-facing counterpart is an ordinary jump.

The supported movement family cannot pass straight through the minimum two-pixel obstruction in one input: that would require about 0.725 displacement including the player's width. Generate diagonal corner clearance instead of the earlier unconstrained straight-through proposal.

### Corner blip-up

This is the landing analogue of corner jump cancel. It is X-facing and uses the same Z-blockage-style successive-state conditions, anchored on L and H rather than before-J and J. Keep the ordinary lower landing condition on L. H clears the obstacle's Z edge while retaining yH=yL and becoming grounded.

Use blip-up air movement and the same elevated-jump or normal run-off continuation. Do not blindly impose the ordinary blip-up condition yL>b: the corner case can retain a height below the cleared obstacle's top because H is outside its footprint. The Z-facing counterpart lands normally on the lower level.

## Supported family and rejected proposal

The catalogue now contains normal stepping, slab/stair boost, blip, blip-up, corner jump cancel, corner blip-up and grind, plus ordinary ceiling-hit cases handled by ceiling logic.

The earlier partial-rise jump-cancel proposal (1 -> 1.25) is excluded from the supported family. Its synthetic collision probe did not establish a realizable route with the game's block geometry and this movement policy. For the ordinary lower obstacle case, the nominal jump clears an obstacle below its jump rise. Do not add a partial-rise type based on that probe.

The active probe labels are updated to `corner_jump_cancel` and `corner_blip_up`. Probe boxes/displacements are low-level collision inputs, not evidence for an arbitrary block shape, a straight-through route or a new supported type. The example with lower floor 1, obstacle top 1.5 and yL=1.2 is the corner blip-up, not ordinary blip-up.

## Ceiling classification

Approved organisation, 2026-10-30: the normal-air `ceiling` type generates ordinary ceiling hits and grind. The stepping type generates normal step, slab/stair boost, blip, blip-up and the two corner cases. Grind and corner jump cancel share cancelled-jump Y/control primitives while retaining different geometry.

Inside a ladder, `ladder_ceiling` is local handling within ladder climbing, with the shared primitive helpers. Do not turn a ladder-owned input into a normal ceiling unit. Normal air before, between and after ladders can use the ceiling type. Keep the actual pre/post occupancy masks at entry/exit boundaries and preserve inherited ladder-local contact combinations.

See MOVEMENT_TYPE_REFACTOR_PLAN.md for the concrete module extraction, common-program contract, migration commits and validation gates.

Repeated steps, axis directions, multiple boxes and compatible contact combinations are variations/compositions. Exact resolver branches and equality bookkeeping are not extra named movement types. Reuse the common continuous model, event constraints and existing acceptance helpers rather than reproduce the Minecraft step algorithm in orchestration.
