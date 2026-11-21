# Shared block-interaction reachability

Current consolidated implementation scope/order: ORCHESTRATOR_IMPLEMENTATION_PLAN.md. This document supplies supporting detail; all workstreams included by the consolidated plan remain required even where an older section describes a separate implementation slice.

2026-11-02. The shared reachability prepass is implemented for step3 of the corridor/constraint fix plan, in reachability.py and screen.py. The checkpoint orchestrator uses it for selected-program screening, obstacle phases, avoid variants and conservative dead-prefix checks. It uses the existing compiler, normal momentum, zero inertia thresholds and a fixed discrete movement/contact schedule. All facings remain free. No IPOPT call is needed.

The refinement (`refine=True`) solves the disk relaxation in [`numerics.disk`](../../src/parkour_solver/numerics/disk.py) with Clarabel: the exact position/velocity equations, selected horizontal clip/reset contacts with the IPOPT worker's approach and `1e-7` reach rows, the X/Z rows, and each acceleration inside its disk `|a[t]| <= magnitude[t] + 1e-9`. The disk contains every full-strength control, so this is a necessary feasibility screen; rows it cannot express (facing, `DXZ`/`DZX`) are dropped, which keeps it a relaxation. Only a `PrimalInfeasible`/`AlmostPrimalInfeasible` status excludes, with reason `disk_control_relaxation_infeasible`. The relaxation optimizes the configured progress objective: the model's own objective (the pre-solve `contradiction` check of a fixed route), else the caller's (legacy collision repair's constraint variants pass their route's guide objective) at the model's horizon. Anchor search's conflict-driven variants screen partial combinations by intervals only, since each branch's own solve is that check. A proposal's creation-time screen (`InteractionGate.check_program`, after the initial-floor guard and the interval checks of `required`) solves the request a decision check's repair loop sends for the program without avoidance rows, or, for a run-off, with each candidate [walk-off face](#run-off-walk-off-faces) row: `relaxation` builds it with `search.ordered_route.numerical_problem` from the compiler-derived footprints and the program's [decision objective](ANCHOR_POLICY_DECISIONS.md#o3--finite-extended-b7-backward-guides-and-onward-objectives) (outside anchor search, the stage's guide objective, else the scene objective). The repair loop's identical request is then a cache hit, not a second solve. An undecided solve of a spec with an objective (time or iteration limit, numerical error) is retried once with zero cost, since infeasibility does not depend on the objective; only that retry's infeasibility is kept. Decided verdicts are cached process-wide by the canonical spec; a still-undecided one is solved again on its next call, reported as `relaxation_undecided`, and never excludes. Nonzero inertia thresholds or ladder controls skip the refinement. The numerical infeasibility verdict is not an independently checked rational certificate.

Constraint-generation checkpoints save excluded row hashes/proofs and duplicate counts. Whole-obstacle exclusions save the actual separating tick/phase/axis bounds. These screens can leave substantial false positives; see [the results and remaining discovery bottleneck](../../research/orchestrator-fixes/README.md).

## Selected shared interface

User clarification: unreachable target blocks and irrelevant obstacle avoidance are applications of one shared block-interaction screen. Do not build separate target-reachability and avoid-relevance helpers. Compute conservative position/velocity envelopes once for the fixed movement/constraint context, then query existing interaction constraint bundles at their required ticks and Y/X/Z collision phases:

```text
interaction_reachability(envelopes, interaction_constraints)
    -> ruled_out(reason, tick, phase, separating_bound)
    or not_ruled_out
```

Use the rows, finite contact footprint and swept phase conditions from the existing movement/contact builders as the interaction description. A query can span multiple phases/ticks where a step or other movement requires them. Unsupported tests or loose envelopes return not_ruled_out; that result is not a feasibility certificate. Disjunctive region choices are ruled out only when all alternatives are excluded. A single required condition excluded by the envelope suffices to reject a conjunctive interaction bundle.

The helper has no target-versus-obstacle policy. Callers interpret its result:

| Query | If ruled out |
| --- | --- |
| Proposed required landing/run/step/ceiling/hit interaction | Discard that interaction candidate. If it was already mandatory in a composed candidate, discard that candidate. Do not remove its required rows and keep solving. |
| Potential unwanted contact whose prevention would require avoidance | Omit avoidance for that contact/tick because contact cannot occur. |
| One specific avoid-side/timing branch | Discard that branch if its own required constraints are unreachable; other branches may remain. |

Generators ask through `InteractionGate.query` (`reachability/service.py`): the envelope of the prefix plus a free normal approach, with Y widened for named outcomes. The gate builds one approach per prefix and jump/no-jump start, long enough for the last tick a generator can query (the first descent below the lowest block top, plus the blip's hit tick), and reads every shorter tick from it: an n-input approach's envelope is exactly the first n ticks of a longer one, because the free approach adds no rows. A tick past that horizon, or a prefix whose long approach cannot be built, falls back to its own approach (`approachFallbacks`). The gate keeps these approaches (512 entries, oldest evicted) keyed by the prefix's physics key and threshold, the start and the input count, so the normal, stepping and ceiling generators of one node share them; each context computes the prefix key once (`approachBuilds` counts builds). Every generator check goes through `bounds_allowed`, which raises when the context has no `interactionGate` key; a caller without a prefix program (profile and timeline modes, spatial proposals) sets it to `None` explicitly ([deferred P10](../DEFERRED_WORK.md)).

Before any route-based question, generators skip blocks no single flight from the node's launch block can touch. `InteractionGate.reachable_blocks(owner)` caches `block_table(scene, owner, traces)`, built once per owner for the gate's lifetime from scene geometry alone. The launch owner is the owner of the program's last visit (`search.anchors.policy.launch`), so a node that ends mid-air uses the block its flight left; ceiling hits and grinds only shorten that flight. After a ladder tick there is no table, because the ladder may have carried the player anywhere. For each block of the owner, the jump from its top falls through the lowest block top at tick D, and a generator queries positions up to state D+1 (a blip's higher block at the lower landing's next state). The horizontal travel radius uses the per-tick speed limits 0.7 blocks on a jump tick, 0.5 on a ground tick and 0.4 on an air tick, as given by the user: one air tick for the landing tick that ended the prefix (a landing row constrains the state before that tick's X/Z move), one jump tick, then D air ticks, so R = 0.4 + 0.7 + 0.4 D. Heights are measured from the launch piece's top (the feet). `JUMP_RISE` (`movement/normal.py`, 1.24919) is the game's highest feet rise of a jump from rest, replayed under Minecraft's 0.005 inertia threshold (user decision; the solver's threshold-0 peak is 1.2522), and head reach is the body height 1.8 plus `JUMP_RISE` (3.04919). The table has one set per kind, each the union over the owner's pieces of solids within R (Euclidean distance from the piece's player-space footprint to the solid's player-space X/Z rectangle):

- `blocks`/`solids` (hit): raw bottom at most feet plus head reach. Any reachable block can be hit. Avoidance (`search/engine.py` `segment_solids`: each landing segment's obstacles are built from these solids only) and encounter grouping read this set.
- `landing`: top at most feet plus `JUMP_RISE`. The normal generator reads it.
- `stepping`: `landing` plus blip secondaries. A stepping, grind or blip case's primary block is a landing block; a blip variant's higher block is touched one air tick (0.4 blocks) later, so it is kept when its player-space rectangle is within 0.4 of a landing block's and its top is at most feet plus `JUMP_RISE` plus the 0.6 step (`STEP_HEIGHT`); the cases' own height tests stay exact. Jumping variants (slab boost, corner jump cancel, a jump from a blip state) add no reach: anything after their jump belongs to the next jump's table. The stepping generator reads it.
- `ceiling`/`ceilingSolids`: raw bottom between feet plus 1.8 and feet plus head reach. Ceiling hits and grind read it.

No lower height bound applies: every top is at or above the lowest top. Candidates come from `SceneGeometry.near`, a 4-block X/Z grid over the expanded boxes. `candidate_blocks(context, kind)` and `candidate_option(context, option, kind)` (`reachability/service.py`) apply the kind's set in the normal, stepping, ceiling and grind loops; ceiling options are filtered after they are merged, so coplanar faces merge as before. `tests/unit/movement/test_block_contacts.py` `BlockTableTests` checks that every generator returns the same choices with and without the table on Real11, the stepping fixtures and 20 random box scenes, for roots, their children and mid-air truncations, and pins the extended Real11 sets for `b5`.

These are the same geometric reachability question with different caller actions. In particular, unreachable contact and unreachable avoidance branch must not be conflated. Known required landings are screened through the same helper, rather than being exempted from reachability screening; a reachable landing still retains its contact constraints.

Reuse the same precomputed envelopes and helper in block-opportunity generation, avoid generation and fixed-branch screening. Cache by original state, discrete movement schedule and constraint context; do not apply one schedule's envelope to a stepping/ceiling alternative that changes the coefficients or Y history. Begin with the existing conservative travel/interval mechanisms; tighter propagation can be shared later. The research-only B2 probe below demonstrates the underlying bound, not a separate production obstacle subsystem.

## Run-off walk-off faces

A run-off (a grounded no-jump input whose position descends) must have the player centre off its launch block's exposed top at its walk-off state: that input's Y sweep starts from the state's X/Z, so a centre still over the top keeps the player grounded. `constraints.contacts.walk_off_faces` gives the faces to leave by. The launch support L is the touching component of feet-height exposed tops that holds what the previous input landed on (the initial floor at tick 0); feet-height blocks across a gap keep their generic floor checks. When L's pieces, together with the space solids at the feet height fill (a wall clipping the top), do not cover L's bounding box (an L-shaped support), it returns None and only the generic floor checks apply. The faces are ordered by where the landing block lies, on raw footprints (player-space regions of flush blocks overlap): beyond one face, that face and the two side faces, the side toward the landing block first, with the opposite face only when those three are blocked; diagonal, the two facing faces, the two opposite only when both are blocked; overlapping, all four, nearest the envelope's midpoint first. A face is open when some free side of the support's cluster at the feet height (`SceneGeometry.sides` over the cluster's members active there) is bounded by that face's plane and owned by an L solid, so a partly covered face stays open. Faces the envelope cannot reach at the walk-off state are dropped. Rows are `avoid_contact_rows` rows with the support clearance.

- The normal generator (per landing event) and the stepping lower-landing loop ask `walk_off_allowed` (`InteractionGate.walk_off`, on the no-jump approach envelope `query` uses) for a no-jump approach from the ground onto a lower block. No reachable open face drops the proposal and logs an exclusion with reason `launch_support_not_left`.
- `InteractionGate.check_program` finds the program's last walk-off tick without a carried selection (`walk_off_tick`) and its first landing (`landing_boxes`), then solves the relaxation with each face row in order: the first not proven infeasible is the selection, or every one in a diagonal tie; none rejects. Clarabel undecided counts as not infeasible. Without the relaxed screen (wall hits, or a request outside its class) every reachable open face is carried. The screen cache keeps the selection, and the screen appends it to `program['walkOffs']`, which `append_choice` carries and `truncate_program` clips. It is not part of the program's search key, since it follows from the program.
- The node solve (`search/engine.py`) drops the floor checks on a carried selection's solids at its tick and gives `ConstraintVariants` one `walk_off` item per selection ([avoidance passages](AVOIDANCE_PASSAGES.md)).

## Procedure

1. Preserve all selected landing/run/step/ceiling contact constraints. The test below removes only additional avoid requirements and their branching, not intended interactions.
2. For each solid and input/movement phase, check whether the player's vertical hitbox could intersect it under the fixed scalar schedule. For a floor block with bottom0, feet at or above its top need no horizontal side avoidance. In general use both the top and bottom, player height, swept movement and the Y->X->Z collision phases. Planned landing on the top remains a required finite contact. Never interpret above-top irrelevance as permission to remove the landing constraint.
3. Build a conservative horizontal reach envelope from the original position and velocity. Use the existing per-input compiled acceleration magnitudes and friction, including sprint-jump effects. Ignore restrictive facings and obstacles when making this optimistic envelope. Cover the full input sweep, including the intermediate X-before-Z position, rather than checking only endpoint positions.
4. Intersect that envelope with the obstacle's player-center collision footprint. If disjoint, every candidate represented by this fixed schedule is clear for that phase, so no avoid choice/row is needed. Record the separating axis/bound and margin. If they overlap, keep the interaction as potentially relevant. Overlap is not proof of collision or reachability.
5. Remove all obstacle-specific avoidance branching when every relevant phase is excluded. Otherwise limit the existing face/timing builders to the remaining interaction windows. A selected route may further limit which sides are considered, but cannot turn an optimistic reach overlap into a proof that one side is forced.

## Cheapest travel upper bound

Let S_t bound the stored horizontal speed at pre-input state t, a_t bound the magnitude of that input's acceleration (from the compiler), and f_t be its drag multiplier. Starting at R_0=0 and S_0=norm(original horizontal velocity), use:

```text
D_t     = S_t + a_t
R_(t+1) = R_t + D_t
S_(t+1) = f_t * D_t
```

`reachability.intervals.travel_radii` is the one implementation of this recurrence; the interval engine, the support graph, spatial proposal annotation, event ranking and the ladder-exit check all call it with their own start speed, input slice and padding. `reachability.intervals.motion_bound` is the one stored-speed cap (the largest `f*m/(1-f)` of a run-and-jump sample, or the original speed if larger).

R_t bounds distance from the original horizontal position at state t. R_(t+1) also conservatively covers the horizontal movement phases during input t: an intermediate X-only displacement cannot exceed D_t. For a cheap axis test, enclose the disk in X0+/-R and Z0+/-R and compare that box against the expanded obstacle rectangle. If either axis is separated, the obstacle is irrelevant for that input. A disk-to-rectangle test can tighten this but is optional. Use outward numerical padding; tangency or an uncertain near-boundary comparison must retain the interaction.

`reachable_envelopes` compares every earlier state with each state up to the last one a row or contact constrains: the gap between their boxes against `R_j - R_i`. Later pairs never fire. Past the last constrained state each box contains the one before it, so its gap to any earlier box is at most that box's gap, while the allowance only grows. A free approach therefore costs no pairs (`tests/unit/reachability/test_screen.py` checks the shortened loop against all pairs).

This follows triangle inequalities for the fixed continuous movement model. It carries all momentum and does not assume the player resets to rest on each jump/landing. It is an upper bound on possible travel, not an estimated objective or a global optimality bound. A loose envelope causes extra work, not loss of valid candidates.

For longer prefixes, per-axis position/velocity intervals can be intersected with already-selected support/contact bounds. Forward/backward propagation from those bounds may be tighter than a growing disk. Preserve position/velocity dependence conservatively and correct contact phases; do not reset an envelope to the endpoint of one optimized witness. Start with the cheap forward bound and add tightening only when overlap remains expensive.

## Existing code to reuse

- block_search.build_transition_graph compares support gaps against optimistic travel from compiled magnitude/friction arrays to reject unreachable block transitions.
- screen.contradiction propagates position/velocity intervals, intersects supplied X/Z walls and accounts for selected horizontal contacts to reject inconsistent fixed problems.
- blockages.passage_options already combines forward/backward travel envelopes with vertical face feasibility and reachable face chains.
- The generic normal catalogue itself still visits candidate blocks, entry_possible checks the initial positional domain, and later screens/route filters remove choices. Do not claim all normal-block generation already has the desired per-obstacle/per-tick filter.

Extract/reuse these mechanisms behind the single shared interaction helper; do not implement another movement model or separate target/avoid reachability paths. The caller actions are specified above.

## Real11 B2 check

Expanded B2 right boundary is X742.3000000119209, while initial X is746.5. Reaching its footprint therefore requires at least4.199999988079071 blocks of negative-X displacement. The probe computes a padded forward travel bound through every input whose Y sweep might overlap B2.

| Fixed schedule | Largest travel bound during potentially relevant early inputs | Conclusion |
| --- | --- | --- |
| Immediate jump0, next jump12, B2 landing21 | About4.122302 through input14; minimum possible X742.377698 | Every potential early B2 interaction excluded; minimum X gap about0.077698. Keep B2 landing/contact constraints, but no additional B2 avoidance is needed for this schedule. |
| Five ground inputs, jumps5/17/26, B2 landing26 | Envelope can reach B2 on inputs15..19 | Cannot safely remove all earlier B2 avoidance with this bound. Retain those windows or tighten using selected constraints. This does not assert an actual collision. |

After the early window, feet stay at or above B2's top, so it cannot demand later horizontal exit-face timing choices. The earlier diagnosis that late B2 exit gates29/31/... are unnecessary still holds; it did not prove that every schedule's earlier B2 approach is irrelevant. This qualification is important when generalizing the user's observation.

Reproduce the two controls without IPOPT:

```sh
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 PYTHONPATH="$PWD" .venv-guidance/bin/python standalone/autonomy/experiments/linkcraft11-opening/probe_b2_reachability.py
```

The existing standalone Java compiler manifest must be available. Results, exact coefficients, padded envelopes and per-input exclusions are in [audit/b2-reachability.json](../../research/linkcraft11-opening/audit/b2-reachability.json). No production search/model change or claim of global route optimality is made.
