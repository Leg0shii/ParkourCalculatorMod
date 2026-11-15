# Shared block-interaction reachability

Current consolidated implementation scope/order: [ORCHESTRATOR_IMPLEMENTATION_PLAN.md](ORCHESTRATOR_IMPLEMENTATION_PLAN.md). This document supplies supporting detail; all workstreams included by the consolidated plan remain required even where an older section describes a separate implementation slice.

2026-11-02. The shared reachability prepass is implemented for step3 of [the corridor/constraint fix plan](CORRIDOR_CONSTRAINT_FIX_PLAN.md), in reachability.py and screen.py. The checkpoint orchestrator uses it for selected-program screening, obstacle phases, avoid variants and conservative dead-prefix checks. It uses the existing compiler, normal momentum, zero inertia thresholds and a fixed discrete movement/contact schedule. All facings remain free. No IPOPT call is needed.

The optional refinement in linear_reachability.py carries coupled linear position/velocity equations and selected horizontal clip/reset contacts. Each acceleration lies inside a16-face polygon circumscribed around the true acceleration circle. Every circle control is allowed; this is a necessary feasibility screen, not a replacement model sent to IPOPT or an objective bound. Dynamics are widened by2e-8, positional rows by2e-7 and controls by1e-9. Only HiGHS status2 (infeasible) excludes. Timeout, numerical failure, missing SciPy, nonzero inertia thresholds or ladder controls exclude nothing. The LP's numerical infeasibility verdict is not an independently checked rational certificate. Random feasible-control/selected-clip regressions guard against erroneous exclusions.

Constraint-generation checkpoints save excluded row hashes/proofs and duplicate counts. Whole-obstacle exclusions save the actual separating tick/phase/axis bounds. These screens can leave substantial false positives; see [the results and remaining discovery bottleneck](experiments/orchestrator-fixes/README.md).

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

These are the same geometric reachability question with different caller actions. In particular, unreachable contact and unreachable avoidance branch must not be conflated. Known required landings are screened through the same helper, rather than being exempted from reachability screening; a reachable landing still retains its contact constraints.

Reuse the same precomputed envelopes and helper in block-opportunity generation, avoid generation and fixed-branch screening. Cache by original state, discrete movement schedule and constraint context; do not apply one schedule's envelope to a stepping/ceiling alternative that changes the coefficients or Y history. Begin with the existing conservative travel/interval mechanisms; tighter propagation can be shared later. The research-only B2 probe below demonstrates the underlying bound, not a separate production obstacle subsystem.

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

R_t bounds distance from the original horizontal position at state t. R_(t+1) also conservatively covers the horizontal movement phases during input t: an intermediate X-only displacement cannot exceed D_t. For a cheap axis test, enclose the disk in X0+/-R and Z0+/-R and compare that box against the expanded obstacle rectangle. If either axis is separated, the obstacle is irrelevant for that input. A disk-to-rectangle test can tighten this but is optional. Use outward numerical padding; tangency or an uncertain near-boundary comparison must retain the interaction.

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

The existing standalone Java compiler manifest must be available. Results, exact coefficients, padded envelopes and per-input exclusions are in [audit/b2-reachability.json](experiments/linkcraft11-opening/audit/b2-reachability.json). No production search/model change or claim of global route optimality is made.
