# Preliminary plan: generate only relevant, distinct interaction models

> Implementation boundary: `498c054c`. [DFS/interaction results](experiments/dfs-interactions/README.md) and [current state](../docs/CURRENT_STATE.md) supersede pending-status language below; the original requirements remain as design provenance.

Current consolidated scope: [DFS_INTERACTION_IMPLEMENTATION_PLAN.md](DFS_INTERACTION_IMPLEMENTATION_PLAN.md). This document supplies the interaction half; the user now also selects depth-first decision traversal and backtracking.

2026-11-03. User requests moving relevance filtering to the correct place and making it robust. This is a preliminary implementation plan, not a claim that runtime changes have been made. It closes a gap in workstreams3/4 of [the consolidated plan](ORCHESTRATOR_IMPLEMENTATION_PLAN.md), rather than introducing another continuous solver or larger budgets.

## Governing invariant, clarified by the user

This is one root cause: block interactions were not discarded early enough, consistently across consumers. The user explicitly required a shared interaction test. Landing, stepping, avoidance, contacts and route checks are consumers of that test, not separate relevance subsystems or unrelated fixes.

For each block/input/state/collision phase in the actual fixed or proposed movement context, obtain conservative interaction eligibility before creating any block-derived alternative, prerequisite, constraint or interaction-specific check. If the interaction is provably unreachable, none of those consumers may introduce it. Unknown/not-ruled-out retains coverage. The eligibility result must be shared across all builders and validators and keyed by geometry/body, original state, movement/scalar/contact context and scope. It must be recomputed when that context changes.

The generator must construct choices from the eligible interaction set rather than enumerate arbitrary block/tick descriptions and strip rows later. Projection/truncation may not reintroduce a discarded interaction or orphan its conditions. Any future-interaction prerequisite must have explicit ownership and pass the same gate for the interaction it depends on; a bare feasible inequality is not eligibility evidence. Complete-event proposal exclusion and irrelevant-tick omission are consequences of the same gate, not independent landing-versus-avoid policies.

P04/P05 and the irrelevant face/gate explosion are regressions for this one invariant. The implementation plan below describes where to enforce it and how to preserve that invariant through enumeration, combination, truncation and validation. No runtime fix is claimed yet.

## Confirmed problems

1. The current face grammar enumerates irrelevant gate states and inactive face prefixes, strips their rows later, then deduplicates after joining blocks and refining reachability. Exact-context probes document substantial duplicate work in [the audit](experiments/orchestrator-fixes/audit/PRUNING_FOLLOWUP.md).
2. Stepping choices construct approach/contact/case bundles before checkpoint truncation. Truncation currently keeps position rows at t<=cut, while dropping finite contact events at input>=cut and case events at input>=cut. A future event can therefore leave an orphan approach row at the cut. Subsequent screening examines only the truncated model and can no longer reject the impossible full proposal that produced it.

## 1. Determine relevance before constructing decision alternatives

Use the existing shared interaction_reachability helper and existing builders/compiled equations. Compute phase-aware windows before enumerating face chains, switch states, target contacts or stepping/ceiling alternatives. Include the Y->X->Z sweep, player body, selected contacts, original state and fixed scalar/input history. Above a block means the relevant sweep remains clear, not merely one pre-input sample being above its top. Preserve intended landing/run/step/ceiling constraints; they are required interactions, not unwanted avoidance. Unknown overlap keeps an alternative.

There must be one relevance path for generic obstacle avoidance and named movement preconditions. A row originating in a stepping builder must not bypass checks applied to generic avoid rows. Use lightweight provenance attached to existing row/event bundles: owning interaction/block, input/state/phase and role (required contact, approach prerequisite, unwanted-contact avoidance, or explicit task constraint). Do not infer ownership by matching a coordinate or row name.

For an excluded unwanted contact, generate no avoidance decision. For an excluded required contact/precondition of a proposed movement, reject that proposed movement; do not delete its required conditions and pretend the movement is feasible.

## 2. Generate meaningful constraint changes, not arbitrary inactive history

Use only relevant states/phase transitions when placing gates. Switches inside a gap that generate identical numeric rows should have one canonical model representation. Retain a valid face-chain/cap witness, including any metadata that affects remaining choices. Do not merge different actual constraints, different scalar histories, or different committed decisions merely because optimized trajectories look similar. Unsupported/uncertain cases keep conservative coverage.

## 3. Canonicalize before products and expensive refinement

Deduplicate per-obstacle alternatives before joining blocks. Deduplicate equivalent partial nodes using their remaining-item index, exact accumulated constraints, fixed physics/scope and relevant resource/cap/commitment state. Check already handled complete keys before repeating refinement. Preserve genuinely different cap histories if they can affect continuation; numerical row equality alone is insufficient in that case. Keep full source cursors and pending work resumable. Version changed generator checkpoints explicitly; do not claim old raw cursors are exact new-generator continuations.

## 4. Screen whole proposals before projecting to a checkpoint

Run cheap necessary geometry/interaction checks while the full proposed contact/case bundle is still available. Reuse the shared helper, prioritizing obvious incompatible finite footprints before more expensive propagation. Cache compatible compiled/profile context rather than adding a numerical solve. Reject proven-impossible proposals before their dead future condition can create a distinct shorter prefix. A truncated or budget-limited check remains unknown.

Projection must respect ownership and commitment, not only each row's tick. If a future interaction is not committed/modeled in the prefix, do not retain its orphan approach rows as unconditional prefix requirements. Preserve/re-generate that conditional choice when its decision enters scope. If a future choice is explicitly committed, retain its necessary earlier prerequisites and declare that scope. Keep explicit user task rows and constraints belonging to already modeled contacts. Canonicalize equivalent prefixes after projection.

## 5. Regression gates and measurement

- Exhaustible small scenes: compare old/new unions of distinct fixed models, including Y-clear gaps, unreachable blocks, disjoint relevant windows, genuine adjacent switch times, corners, returns and blockage-cap metadata. Prove exact duplicates collapse without silently dropping distinct feasible models.
- Keep required landing/run contacts, ceilings, stepping and near-tangent full sweeps. Include a state starting above a block whose next Y sweep lands/collides, to prevent an unsafe endpoint-only exclusion.
- P06/P07: preserve distinct effective row sets while collapsing inactive face/gate spellings. Measure descriptions, distinct per-block/partial/final models, refinement/cache calls and time outside IPOPT. Keep current margins and resource settings.
- P04/P05: reproduce the full blip proposals below. Required contacts are excluded before truncation; no orphan B4 approach row becomes a separate28-input prefix. Include a reachable adjacent-block blip and an explicitly committed future prerequisite so the fix does not delete real movement conditions.
- Save/load and generation-order checks: no repeated completed requests, lost alternatives or incompatible-context reuse. Retained versus provisionally omitted candidates remain independently auditable.

## P04/P05 evidence

The saved feet Y values are7.001336 at24,7.166109 at25,7.249187 at26,7.252203 at27 and7.176759 at28. These are above B3 top7 but below B4 top7.5. Therefore these B4 rows are not disproved by the above-B4 exclusion.

Both rows originate in landing_wall_options for a proposed B3-to-B4 blip: horizontal approach/contact at input28, blip at input29, resulting state30. The approach row at state28 survives truncation to inputs0..27; its owner contact at input28 and case at29 are removed. P04 keeps Z[28]<=-1100.300001011921; P05 keeps X[28]>=735.3000010119209. Neither is emitted by generic ConstraintVariants obstacle avoidance.

The existing shared screen already rejects both full30-input proposals with interval_reach_contradiction at state29 on X. Their required B4 contact and B3 landing footprints are incompatible there. After truncation to28, the screen returns not-ruled-out for both orphan-row prefixes. This was reproduced with existing coefficient compilation/screening and no IPOPT calls. Exact full/truncated programs and certificates are saved in [p04-p05-provenance.json.gz](experiments/orchestrator-fixes/audit/p04-p05-provenance.json.gz).

This establishes a second placement/scope gap: useful interaction information is discarded before the necessary screen runs. It is related to early generation filtering, but not the same as the irrelevant-gap duplicate enumeration.

## Scope

This work keeps the current continuous model, free facings from the original state, normal momentum with zero inertia thresholds, selected non-ladder movement families, corridor policy and comparison margins. The outer traversal/selection change is now covered by the consolidated DFS plan. Independent redesign of schedule protections, speculative future-time bounds and corridor equivalence remains separate. Do not claim that eliminating orphan prefixes or duplicate descriptions proves P06/P07 globally inferior.


### Confirmed B4 reachability exclusion for the ordinary prefix

The broader P03 prefix was replayed through the exact-context shared relevance helper. B4 is excluded in all84 Y/X/Z phases of inputs0..27 (all_contact_phases_unreachable), so this also covers the more restricted P04/P05 prefixes. At state28 the conservative X envelope is[737.7905441639986,745.293503318997], entirely beyond B4's expanded maximum X735.3000000119209. The last-input travel bound is8.709457205386883 while reaching B4's expanded footprint needs11.961605228096913 horizontal distance.

Thus the existing reachability bound is already sufficient. Generic avoid generation correctly omits B4; the blip builder creates an approach row outside that path, then truncation drops its owner interaction before screening. Feasibility of the leftover outside inequality cannot substitute for querying reachability of B4. The plan must apply the same interaction query before named-case constraints are emitted, reject the impossible required B4 proposal, and keep the valid ordinary B2->B3 prefix without a B4 avoidance choice. [Exact phase proofs](experiments/orchestrator-fixes/audit/b4-prefix-relevance.json). No IPOPT call or runtime change was made.
