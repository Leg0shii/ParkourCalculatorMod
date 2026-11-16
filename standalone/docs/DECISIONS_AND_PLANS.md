# Decisions and plan authority

2026-11-15 user-directed scope: the [single face-merging plan](design/FACE_MERGING_IMPLEMENTATION_PLAN.md)
contains exactly two parts: detecting adjacent coplanar faces in any of the six
orientations, and representing exact merged covers with source membership for
later decisions. It replaces the former ceiling-only/integration/bug-fix plan;
that extra work has been removed from this task. Implementation has not begun.
The detailed plan recommends clipping internal/buried face portions before
grouping, while retaining raw source faces, and specifies geometric IDs,
component-specific provenance and the physical/player-coordinate distinction.

2026-11-15 longer-course geometry: use [extended Real11 geometry and bounds](REAL11_EXTENDED_GEOMETRY.md)
for the newly supplied B8–B22. The final user correction withdraws the proposed
B6 extension: B6 remains physical X[730,731]. B7 returns to unit size. B9's two
entries are temporarily named B9a/B9b without renumbering other blocks; B13 is
a two-box stair. This geometry does not yet select a longer solve's mandatory
anchors, terminal objective/block or budgets. The existing extended-B7 task and
all its evidence remain separate; prior missing/wider-B6 notes are historical.

2026-11-15 user deferral: interleaving implementation is paused. The [single preserved solver plan](BRANCH_SCHEDULING_INVESTIGATION.md) retains the confirmed existing tick priority, ordinary versus recovery-only distinction, normalized oracle-outcome proposal, ownership/overhead analysis and open details. It is a scheduling change intended to cover the same ordinary work, except existing exclusions/deferrals may act earlier; it does not add solves merely for switching. Current runtime remains DFS. Work-category/outcome clarifications are recorded but unimplemented, not an instruction to start them automatically.

The [deferred-work register](DEFERRED_WORK.md) indexes all known deferred/unimplemented follow-ups with source links and status, including completed/superseded exceptions. The [bookkeeping performance handoff](BOOKKEEPING_PERFORMANCE_AGENT_HANDOFF.md) remains assigned separately to another agent/worktree; this scheduler deferral does not cancel it.

2026-11-15 initial-domain decision: remove the explicit `[0,8]` whitelist from the maintained Real11 case; let existing producers/screens, hard-arrival comparisons and finish bounds select work. [Unrestricted evidence](../research/initial-domain-selection/README.md) confirms first0/8 numerical preparation and recoverable9–12 deferral. [Configuration/CI audit](INITIAL_DOMAIN_AND_CONFIG_AUDIT.md) records remaining supplied inputs and policies. Archived fixtures and generic explicit-domain controls remain unchanged; no new route/pruning policy is introduced.

The approved standalone structure refactor is implemented: [layout](LAYOUT.md), [implementation ledger](STANDALONE_REFACTOR_IMPLEMENTATION.md). This changes file ownership/entrypoints, preserves all physical and decision policy versions, and leaves revisit/other deferred work deferred. The inventory/review is now a source-era proposal record, not a new pending task.

Folder-structure follow-up: [inventory and proposed layout](STANDALONE_STRUCTURE_REVIEW.md) separates runtime, research, tests, tooling, native sources, cases, docs and local outputs. This is a review/proposal, not an instruction to apply moves or change solver policy. Full file and Python dependency ledgers are linked there.

2026-11-11 controlling implementation: the [hard-jump comparison/reachability plan](HARD_JUMP_TICK_PRUNING_AND_REACHABILITY_PLAN.md)
is implemented; its ledger and [feature evidence](../research/hard-jump-comparisons/README.md)
record acceptance. The user confirmed more J OR fewer R normally / fewer A in a
turnaround. Raw additional A/R is no protection. Slower unprotected hard arrivals
are recoverably deferred against compatible validated peers; equal time stays
live. A strict optimistic full-finish bound overrides local protection.

**Declared assumption:** hard jumps between anchors never involve stepping.
Completed stepped transfers cannot learn or satisfy a hard latch. Closed or
pending source-local setup remains allowed. Flight minima therefore consider
ceilings only; unknown shortening ceilings yield zero. This assumption is part
of hard policy3 and exact continuation identity, not a proved fact about all maps.
The old generic stepped/cancelled fixtures retain their validity outside this hard
domain. Guides still optimize continuous solves; they do not prune/count-rank.
All original-state facings remain free. Inter-block revisits stay deferred.

**Current authority:** [execution reference](ANCHOR_SEARCH_EXECUTION_REFERENCE.md) for combinations, pruning/deferral/solver boundaries and decision rationale; [implementation record](HARD_JUMP_TICK_PRUNING_AND_REACHABILITY_PLAN.md) and [evidence](../research/hard-jump-comparisons/README.md) for completed `dd738c98`. No feature implementation or verification remains pending. [Resume](ANCHOR_IMPLEMENTATION_RESUME.md) supersedes older checkout/M1 instructions.

2026-11-04. Read [current state](CURRENT_STATE.md) for implemented behavior. This page prevents an agent from treating older experiments as a new implementation request. User instructions override documentation. Within docs, the current consolidated plan and current-state guide supersede historical proposals.

## Current anchor DFS policy

**Corrected:** one hard transfer S→E can follow repeated S→S setup hops, even after latching. This applies to every source anchor, not only B1. Earlier wording that prohibited those hops was wrong. See [transfer/preparation evidence](../research/anchor-transfer-preparation/README.md) and the corrected O1 decision. All setup impulses still count in J; original caps and the no-inter-block-return scope remain.

The [O1–O7 decision record](design/ANCHOR_POLICY_DECISIONS.md) explains the anchor design; the [execution reference](ANCHOR_SEARCH_EXECUTION_REFERENCE.md) and later [hard-jump implementation record](HARD_JUMP_TICK_PRUNING_AND_REACHABILITY_PLAN.md) describe the current shared comparison/reachability policy. Latest clarification: DFS, normal exact-node solves at a cadence, no separate prefix/ancestor-testing subsystem, and no negative inference from a failed child to an untested parent. Under the user's numerical assumption, only the exact queried node/model can receive the infeasible result. Exhausted alternatives are a different search status.

Implemented default cadence is1 completed meaningful jump/contact decision, not every simulation input/run-hold construction step; required anchors/final always solved. Compare2/4 later from measured wall cost, not solver-call count alone. Use this as the starting default; later batching is a measurement-driven adjustment.

Approach-facing E edge is settled. Physical returns after another support are deferred/not generated; repeated current-block setup jumps remain supported even after a hard-transfer latch; optional acyclic supports remain available in open spans. Full future design is in [the deferred revisit plan](design/REVISIT_DISCOVERY_DEFERRED_PLAN.md), not current milestones.

The prior settled contract remains: global solve-wide single-jump latch, requested impulses, backward/onward guides and early stop,110° spatial turn regions, extended finite B7/minT→MAXZ. Implementation now uses physical-task outer schema2/DFS8/movement context4/repair assembly9; archived old geometry/task/evidence remains unchanged. See [new implementation evidence](../research/anchor-feasibility/README.md). Turnaround selection is now a preliminary binary decision-point test against one approach edge (imaginary diagonal if needed) plus spatial margin. Select exactly T,−J,A or T,−J,R. Record point/edge/margin/distances/label/key, pruning behavior and later replay label flips; include two-label diagrams/counters. Blended scores and uncertain membership are removed.

## Completed generic event-discovery baseline

The user requests a generic representation of all potential route choices, leaving physical feasibility to movement decisions and solves while discarding proved irrelevant/impossible interactions early. Meaningful routes can include approach sides/corners as well as support contacts. A proof that a target's premature side interaction cannot occur must preserve its required landing; an unknown interaction remains available. See [GENERIC_ROUTE_DISCOVERY_RESEARCH.md](research/GENERIC_ROUTE_DISCOVERY_RESEARCH.md) for the primary-source comparison, formal scope/merge conditions, proposed contact/interaction abstraction and staged validation. [GENERIC_ROUTE_DISCOVERY_IMPLEMENTATION_PLAN.md](design/GENERIC_ROUTE_DISCOVERY_IMPLEMENTATION_PLAN.md) records the completed automatic route refactor, including scoped relevance, lazy event/goal discovery, shared prefix owners and the physical-only solve boundary. Implementation and acceptance are complete: immutable domains, lazy event/guide discovery, resumable physical preparation and schema7 dynamic DFS are implemented. That source-era automatic entry uses the event backend; its acceptance evidence is in [event-route-discovery](../research/event-route-discovery/README.md). The earlier contracts below describe their source-specific baseline.

## Current implementation authority

2026-11-10 integration baseline (superseded only where the current execution reference says so): the user approved [selective solver integration and the three search
fixes](ANCHOR_SEARCH_INTEGRATION.md). This replaces the review-only status:
retain directional early stopping, adapt nested persistence, gate greater-run
recovery on actual failure, preserve equal times under optimistic remaining-time
bounds, and reuse owned live DFS state between internal slices. New acceptance
is recorded with the profiling experiment; older source-specific results remain
historical. The two branch histories were consolidated in this checkout.

2026-11-05: the [solver-branch integration review](SOLVER_BRANCH_INTEGRATION_REVIEW.md)
evaluates the nine commits after `61355976` against anchor head `769cdbe3`.
Recommendation, not implementation: adopt numerical row reduction/corrected
bound extraction, adapt shared persistence to anchor/event execution owners,
retain historical evidence, and preserve the later anchor requirement for a
directional objective with early feasible stopping. No branch has been merged
by this review; the constant-zero policy below describes this branch's shipped
behavior, not the recommended anchor default. Fresh64 IPOPT and25 persistence/
DFS checks pass with native dependencies and no skips.

[DFS_INTERACTION_IMPLEMENTATION_PLAN.md](design/DFS_INTERACTION_IMPLEMENTATION_PLAN.md) is the approved design, implemented at `498c054c` with [results](../research/dfs-interactions/README.md). It replaces global shortest-prefix/checkpoint-window traversal with ordered DFS at common spatial decision goals, adopts validated child decision programs, applies one early interaction gate across all builders/checks, removes duplicate effective models before expensive combination, preserves owned prerequisites through projection, and saves a versioned backtracking stack.

[GENERATION_RELEVANCE_PLAN.md](design/GENERATION_RELEVANCE_PLAN.md) supplies interaction/ownership detail; the consolidated DFS plan controls integration and ordering. The seven earlier workstreams in [ORCHESTRATOR_IMPLEMENTATION_PLAN.md](design/ORCHESTRATOR_IMPLEMENTATION_PLAN.md) were implemented to their recorded scope; the DFS/early-interaction change addresses the later traversal and builder-wiring gaps. Its old traversal policy is superseded.

The requested [spatial corridor refactor plan](design/SPATIAL_CORRIDOR_REFACTOR_PLAN.md) records the implemented spatial layer and its acceptance contract: typed support/air regions, meaningful 3D passage identity, untimed route discovery, stage-aware constraints/audits and goals/directions, with concrete modules and acceptance gates. Overflight versus landing/run-off is explicitly a different route. It supersedes the earlier [support/goal/direction investigation breakdown](design/DFS_INTERACTION_IMPLEMENTATION_PLAN.md#21-integration-plan-infer-support-order-from-generated-decisions) where route identity differed; the DFS/early-interaction requirements remain. Milestone1 is committed; the typed route/progress/guard/DFS integration is implemented, with [source-stable coverage and matched cold evidence](../research/automatic-corridors/README.md). The new automatic shortlist admits the archived64-input initial0 witness after independent replay, without using its trajectory/decisions for discovery. The pre-refactor default shortlist excluded the saved reference. The new shortlist covers it, but the300-second automatic cold run still found no finish; supplied routing found T67.

The [corridor handoff](CORRIDOR_AGENT_HANDOFF.md) through `61355976` is imported at merge `984b701b`. The spatial and DFS plans now explicitly preserve the implemented `InteractionGate`, probe uncertainty, dedup/ownership, schema5 execution state, exact request logs, virtual finish and latest-jump local-run policy. Existing historical state-protection helpers are not the active global DFS exclusion pass; no such pass or additional continuation-time pruning is enabled by this refactor.

## Durable user decisions

| Topic | Decision and rationale |
| --- | --- |
| Layering | Preserve physical contacts/passages and movement alternatives in the existing shared DFS. The current engine makes decisions in coupled required-anchor spans, uses backward guide objectives with early stopping, and retains recoverable alternatives. Spatial geometry does not specify jump timing or constant edge weights. |
| Spatial route identity, clarified | Support occurrences and3D passages remain meaningful. Current generation excludes returning to a departed physical owner after another block; consecutive same-block setup hops remain supported. Revisit design is preserved for later. Required milestones/global hard latches still apply. |
| Real11 task correction | Latest requested task extends physical B7 one block +Z to `[729,0,-1095,730,7,-1093]`, actual B7 landing, least T then MAXZ, guide +Z physical edge/X midpoint `(729.5,7,-1093)`. No old virtual/X<729.7 extra task rows in the new fixture. New task evidence is separate from the preserved original-box virtual results. |
| Real11 route/support discovery | The current engine accepts caller-declared ordered required anchors B2/B3/B4/B5 in the measured benchmark, while movement choices and permitted optional supports remain search decisions. The completed baseline discovers dynamic support/contact choices without that mandatory list. Reuse existing geometry/movement/contact helpers; do not make required anchors a fixture-specific generic algorithm rule. Preserve meaningful B1 setup hops. Latest longer-course input keeps unit B6 and unit B7; its new anchors/finish are not inferred. Intentional wall hits stay deferred. |
| Local guidance | Restore backward nearest-point guide inference and continuous progress objective with stop-on-feasible. Progress is not a discrete key/pruning rule; no slack. Re-solve all earlier facings from original state so later constraints may change the path. |
| Comparing alternatives | Compare only at required anchors/final task; optional movement inside a span is coupled. Slower compatible unprotected hard arrivals are recoverably deferred, not global infeasibility proofs; open prefixes and unlatched spans do not receive that local suppression. Full terminal incumbents minimize achieved T then real objective at equal T; current elapsed-time bound remains distinct. |
| Retention | Compatible slower hard arrivals defer only without more J OR fewer R normally / fewer A in turnaround. This generalizes the older ordinary latest-jump run rule; exact saved work remains recoverable. Direct feasibility anywhere permanently latches that anchor transition to single-jump structure for the solve, even after changed incoming histories; source-local setup hops remain eligible, while recovery cannot introduce intermediate supports into the hard transfer. Keep actual run/source/repair ownership and earlier admissible preparation. |
| State protection | Existing position/velocity protection helpers are historical/inactive for a global peer-exclusion pass. The current tick-only policy adds no such dominance claim. Preserve physical schedule/model/scope distinctions; a first feasible pose is not a bound on later re-solves. |
| Different schedules | Absolute-tick p/v is not apples-to-apples across different ground/air histories. Use requested grounded J and the confirmed more-J OR fewer-R/fewer-A protections at anchors. Legacy common-clock schedule/phase/state guards stay in their own compatibility context. Equal counts do not establish equal schedule. |
| Ordering, clarified | Absolute T/local requested J/R/A. Ordinary T,−J,R; turnaround T,−J,A within preprocessed spatial region, no progress key.110° accepted; a0.3-block spatial margin. Each decision selects one whole key; no mixed aggregation or uncertainty label. Global hard structure independent of regional priority. |
| Coupling | All decisions between required anchors remain coupled through original-state re-solves. Feasibility can be scheduled at completed internal movement decisions without fixing their endpoint/velocity or creating extra comparison anchors. Implemented cadence1; future matched2/4 options are not yet selected; raw input steps are not decision levels. |
| Solver boundary | Inputs/events/physical history fixed before each exact-node query, with earlier facings free. Only that queried node/model can receive an infeasible result under the user assumption; child failure never marks untested ancestors infeasible. No separate ancestor-testing pass. Physical alternative/cancellation/error scopes remain distinct. |
| Inertia | Disable thresholds only; retain normal momentum. Exact inertia-pattern follow-up is deferred. |
| Relevance | One generic block/tick/phase eligibility helper for required and unwanted interactions; no landing/avoid special-case system. Proved impossible means no interaction-derived choices, rows or checks. Unknown retains coverage. |
| Corridor | No automatic raw-walk containment or portal-clock filters. Backward guide points are soft continuous-objective/turn cues. Required physical contacts/passages and full scene/task validation remain hard; explicit supplied controls retain their source-era checks. |
| Budget | Minutes or longer are acceptable for accuracy. Explain structural failures before widening caps; preserve unfinished work and reference controls separately from discovery. |
| Workflow | Isolated branch/worktree, no other-agent communication, persist decisions and measured facts. Only commit after a feature implementation; bundle related documentation, tests and fixtures. Investigation/planning/documentation-only turns do not require a commit. This is the new path, not a feature-flag rollout exercise. |

## Prior research: consult, do not restart by default

| Research or implementation | Where | How to use now |
| --- | --- | --- |
| Cell decomposition / Baritone / stratfinder | [spatial research](research/SPATIAL_ROUTE_RESEARCH.md), [source review](research/HEURISTIC_SOURCE_REVIEW.md), [stratfinder investigation](research/STRATFINDER_INVESTIGATION.md) | Motivation and sources behind route abstraction; cells are already implemented. |
| Mixed-input formulations and disk/SOCP | [mixed-integer research](research/MIXED_INTEGER_RESEARCH.md), [continuous decisions](design/CONTINUOUS_DECISION_RELAXATION.md), [guidance plan](design/CONVEX_ROUTE_GUIDANCE_PLAN.md), [guidance module](../research/route_guidance/README.md) | Restricted-family experiments. Containment depends on modeled choices/domain; numerical local NLP success is not a global certified bound. Omitting obstacle constraints can yield phasing. |
| No-threshold clarification | [model note](design/NO_THRESHOLD_DECISION_MODEL.md) | Normal momentum retained; not a new frictionless or memoryless model. |
| Naive route-direction guidance | [baseline](design/ROUTE_BASELINE_GUIDANCE.md), [geometric guidance](design/GEOMETRIC_DECISION_GUIDANCE.md) | Basis of current geometric proxy; richer weighted scoring remains unselected. |
| Time/progress and state guards | [frontier](design/TIME_PROGRESS_FRONTIER.md), [state protection](design/STATE_PROTECTION_IMPLEMENTATION_PLAN.md) | Comparison semantics retained; old processing-key policy replaced by goal-aware DFS. |
| Resume and dead prefixes | [continuation plan](design/SEARCH_CONTINUATION_FIX_PLAN.md), [implemented results](../research/orchestrator-fixes/README.md) | Existing machinery to extend; first-floor death proof is conservative and incomplete. |
| Known future improvements | [backlog](design/PRUNING_IMPROVEMENTS_BACKLOG.md) | Revisit when a measured map exposes a need, not a mandate to raise counts now. |

Discrete decision transfer from completed/sibling solves, new objective bounding and expanded ladders/water/intentional wall hits remain deferred. Current spatial implementation and final evidence status are linked above. Numerical initialization using compatible facing guesses is different from committing another branch's decisions.

## Historical material

[PRUNING_RESEARCH_HANDOFF.md](research/PRUNING_RESEARCH_HANDOFF.md) is now a concise entry. Its former accumulated text is preserved verbatim as [PRUNING_RESEARCH_HISTORY.md](research/PRUNING_RESEARCH_HISTORY.md), with a historical banner. [PRUNING_RESEARCH.md](research/PRUNING_RESEARCH.md), [COMPACTION_HANDOFF.md](history/COMPACTION_HANDOFF.md), [CONSOLIDATION.md](../CONSOLIDATION.md) and IPOPT handoffs remain provenance, not current branch/implementation instructions. The [document index](DOC_INDEX.md) gives every document's topic/status.

When a decision changes, update this page/current state and the active plan. Preserve evidence and source-era results; annotate superseded claims instead of rewriting historical measurements to match new behavior.


## Latest Real11 terminal-goal clarification (2026-11-03)

Historical virtual-target contract, superseded by the2026-11-04 extended physical B7 task above. Existing measurements remain tied to their original task/source.

Supersedes the earlier fixed64 experiment and the literal-finite-B7 interpretation: use the B7-height virtual landing region X<729.7, Z>=-1095.3, extending without an upper Z bound (and without a new lower X bound). Minimize landing time first, then maximize Z at that earliest tick. This is the actual terminal objective, not a common-clock progress proxy. Do not add fictitious approach collisions by extending physical B7 geometry. The existing fixed-clock evidence remains historical; the corrected goal is implemented at `71c9bb7e` and has new [cold evidence](../research/virtual-finish/README.md). The existing scalar model determines the nominal downward height clock; actual finite-scene Y/ground remains authoritative for physical replay. Rank complete candidates by time, then objective, without fake goal geometry. See CURRENT_STATE.md for the full contract.

## Minimum-run rule clarified by the user (2026-11-03)

Historical pre-`db211b2b` ruling: for the same interaction pattern, test least-run timings for feasibility and retain earlier unknowns/distinct interactions. The [call audit](../research/virtual-finish/PRUNING_AUDIT.md) records the then-missing local exclusion and additional time-reasoning questions. The following implementation section supersedes that missing-status diagnosis: latest-jump local runs are now implemented, while further continuation-time reasoning remains deferred.

## Latest implementation: local run pruning; time reasoning deferred

User clarification supersedes a total-run interpretation: compare ordinary preparation runs immediately before the **last effective jump**, keeping prior decisions and interaction choices fixed. Implemented at `db211b2b`, with [results](../research/last-jump-runs/README.md). Earlier missing-status paragraphs describe the archived pre-policy audit. Higher-run copies are heuristic exclusions with saved audit programs; smaller/unknown/equal-run alternatives and distinct interactions remain eligible. Keep the additional completion-time reasoning deferred: proving no faster continuation of7–9 before ruling out10–12 is a separate future improvement. No new remaining-time bound is introduced.


## Checkpoint cleanup shipped (2026-11-03)

The user's instruction to remove extraneous snapshot material is implemented as [lossless shared persistence](CHECKPOINT_STORAGE.md), rather than deleting requested audit evidence or pending work. Store unchanged content once, archive immutable window snapshots once, save repair/call-count deltas against the dispatch checkpoint during a solve, and compact obsolete roots after final publication. Maintain ordinary JSON export/legacy resume and independent mutable copies on decode. Storage version1 is independent of unchanged DFS schema5. Generic replay/resume tests and the saved-data probe verify no loss of accepted/pending/completed work. Simple-bound extraction, numerical-row canonicalization, corridor changes and new time pruning are still separate.


## IPOPT optimizations shipped (2026-11-03)

The user's implementation request supersedes the earlier pending status for simple-bound extraction/numerical-row canonicalization. [Implemented behavior and evidence](IPOPT_CONSTRAINT_REDUCTION.md): reduce only the worker's numerical rows, retaining original request/provenance/hash/validation; enable extraction after changing the monitor to evaluate unextracted expressions at x, including the final point. Preserve all distinct angular/relative/equality restrictions and original solver tolerances.63 native-enabled IPOPT tests plus109 src/parkour_solver checks pass/no skips. Corridor/cell work and new time pruning remain independent.


## Intermediate solve policy changed (2026-11-04)

The user wants intermediate calls to be feasibility checks with no particular objective. [Implemented](IPOPT_FEASIBILITY_MODE.md): constant-zero NLP objective for the existing feasibility-only mode; stop at a scalar-valid point, then apply existing orchestrator scene/corridor checks. DFS intermediate frames select first-witness mode, final frames optimize. Keep all hard constraints/momentum/facing variables and original-start re-solving. Intermediate scores are unoptimized observations. DFS context fingerprints the new policy; do not silently load old policy checkpoints as exact continuation.64 IPOPT plus162 src/parkour_solver checks pass/no skips. No new objective bounds, corridor changes or full Real11 run.


## Evidence for feasibility mode (2026-11-04)

The requested [repeated Real11 intermediate benchmark](../research/last-jump-runs/row-benchmark/REAL11_INTERMEDIATE_BENCHMARK.md) is complete. Early stopping is the main gain; eliminating the directional objective adds little successful-case speed and reduces observed witness Z progress by0.21–0.47 blocks in the three numerical successes. Pure feasibility remains the user's selected policy. Directional early stopping is a measured alternative if later p/v guidance or initialization quality matters; do not silently change the policy or claim a full-route outcome from this fixed-request benchmark. All nine requests retain their feasible/unknown outcomes across modes.
