# Decisions and plan authority

2026-11-03. Read [current state](CURRENT_STATE.md) for implemented behavior. This page prevents an agent from treating older experiments as a new implementation request. User instructions override documentation. Within docs, the current consolidated plan and current-state guide supersede historical proposals.

## Current implementation authority

[DFS_INTERACTION_IMPLEMENTATION_PLAN.md](../autonomy/DFS_INTERACTION_IMPLEMENTATION_PLAN.md) is the approved design, implemented at `498c054c` with [results](../autonomy/experiments/dfs-interactions/README.md). It replaces global shortest-prefix/checkpoint-window traversal with ordered DFS at common spatial decision goals, adopts validated child decision programs, applies one early interaction gate across all builders/checks, removes duplicate effective models before expensive combination, preserves owned prerequisites through projection, and saves a versioned backtracking stack.

[GENERATION_RELEVANCE_PLAN.md](../autonomy/GENERATION_RELEVANCE_PLAN.md) supplies interaction/ownership detail; the consolidated DFS plan controls integration and ordering. The seven earlier workstreams in [ORCHESTRATOR_IMPLEMENTATION_PLAN.md](../autonomy/ORCHESTRATOR_IMPLEMENTATION_PLAN.md) were implemented to their recorded scope; the DFS/early-interaction change addresses the later traversal and builder-wiring gaps. Its old traversal policy is superseded.

## Durable user decisions

| Topic | Decision and rationale |
| --- | --- |
| Layering | Enumerate a small number of broad spatial routes, then optimize discrete local interactions within each. A spatial route does not determine a jump schedule or constant time edge weight. |
| Real11 task correction | Latest user request minimizes time to an actual B7 landing, then maximizes Z at that same tick. The existing fixed64/optional-B7 fixture and measurements remain an older benchmark; no corrected-objective result exists yet in this investigation. |
| Real11 route/support discovery | Use generated feasible decisions to determine support order, rather than requiring B2/B3/B4/B5 by fixture. Reuse the implemented automatic routing/window/direction/goal components; their integration is incomplete. Preserve meaningful B1 setup self-hops. The user reports B6 extends farther right; the stored one-block-X box is incomplete for that description, and exact revised bounds remain unspecified. Intentional wall hits stay deferred. |
| Local guidance | Naive connected route endpoints can supply direction/progress guidance. No new relaxation is required to implement current search. Re-solve all facings from the beginning so later decisions can alter the continuous path. |
| Comparing alternatives | A shared spatial goal compares **time**; a shared absolute tick compares **objective/progress**. Generate allowed candidates, then compare; don't manufacture completion-time families as the objective of generation. |
| Retention | Discard only markedly worse candidates on justified comparable measurements; preserve close/unknown/protected alternatives. Full regression continuations of heuristically dropped candidates reveal false pruning. |
| State protection | For matching schedule/phase/scope, position within existing margin, greater directional velocity OR greater horizontal speed blocks peer exclusion. Full position and velocity from the same witness; no arbitrary weighted score. |
| Different schedules | Absolute-tick p/v is not apples-to-apples across different ground/air histories. Preserve fewer-run/more-effective-jump protections and incomparability. Equal counts do not establish equal schedule. |
| Ordering, clarified | Fewest **achieved arrival ticks at the same point**, then most effective grounded jumps, then fewest grounded nonjump runs. Follow chosen child to completion before root siblings. Old simulated-prefix rule is wrong. |
| Coupling | Close decisions can be grouped. Weak influence of separated decisions is a provisional map-dependent assumption, not a theorem; backtracking and retained siblings are needed if evidence contradicts it. |
| Solver boundary | Orchestrator fixes inputs, events and constraints before IPOPT; reuse builders. Continuous success should satisfy its specified model, with separate rounding/exact replay limits. |
| Inertia | Disable thresholds only; retain normal momentum. Exact inertia-pattern follow-up is deferred. |
| Relevance | One generic block/tick/phase eligibility helper for required and unwanted interactions; no landing/avoid special-case system. Proved impossible means no interaction-derived choices, rows or checks. Unknown retains coverage. |
| Corridor | Keep checks plus diagnostic logging. Preliminary constraints and corridor checks have matching scope. Guide-point coordinate envelopes must not become accidental walls. Future removal remains undecided. |
| Budget | Minutes or longer are acceptable for accuracy. Explain structural failures before widening caps; preserve unfinished work and reference controls separately from discovery. |
| Workflow | Isolated branch/worktree, no other-agent communication, commits at reasonable boundaries, persist decisions and measured facts. This is the new path, not a feature-flag rollout exercise. |

## Prior research: consult, do not restart by default

| Research or implementation | Where | How to use now |
| --- | --- | --- |
| Cell decomposition / Baritone / stratfinder | [spatial research](../autonomy/SPATIAL_ROUTE_RESEARCH.md), [source review](../autonomy/HEURISTIC_SOURCE_REVIEW.md), [stratfinder investigation](../autonomy/STRATFINDER_INVESTIGATION.md) | Motivation and sources behind route abstraction; cells are already implemented. |
| Mixed-input formulations and disk/SOCP | [mixed-integer research](../autonomy/MIXED_INTEGER_RESEARCH.md), [continuous decisions](../autonomy/CONTINUOUS_DECISION_RELAXATION.md), [guidance plan](../autonomy/CONVEX_ROUTE_GUIDANCE_PLAN.md), [guidance module](../autonomy/route_guidance/README.md) | Restricted-family experiments. Containment depends on modeled choices/domain; numerical local NLP success is not a global certified bound. Omitting obstacle constraints can yield phasing. |
| No-threshold clarification | [model note](../autonomy/NO_THRESHOLD_DECISION_MODEL.md) | Normal momentum retained; not a new frictionless or memoryless model. |
| Naive route-direction guidance | [baseline](../autonomy/ROUTE_BASELINE_GUIDANCE.md), [geometric guidance](../autonomy/GEOMETRIC_DECISION_GUIDANCE.md) | Basis of current geometric proxy; richer weighted scoring remains unselected. |
| Time/progress and state guards | [frontier](../autonomy/TIME_PROGRESS_FRONTIER.md), [state protection](../autonomy/STATE_PROTECTION_IMPLEMENTATION_PLAN.md) | Comparison semantics retained; old processing-key policy replaced by goal-aware DFS. |
| Resume and dead prefixes | [continuation plan](../autonomy/SEARCH_CONTINUATION_FIX_PLAN.md), [implemented results](../autonomy/experiments/orchestrator-fixes/README.md) | Existing machinery to extend; first-floor death proof is conservative and incomplete. |
| Known future improvements | [backlog](../autonomy/PRUNING_IMPROVEMENTS_BACKLOG.md) | Revisit when a measured map exposes a need, not a mandate to raise counts now. |

Discrete decision transfer from completed/sibling solves, new objective bounding, expanded ladders/water/intentional wall hits and automatic redesign of all spatial routes remain deferred. Numerical initialization using compatible facing guesses is different from committing another branch's decisions.

## Historical material

[PRUNING_RESEARCH_HANDOFF.md](../autonomy/PRUNING_RESEARCH_HANDOFF.md) is now a concise entry. Its former accumulated text is preserved verbatim as [PRUNING_RESEARCH_HISTORY.md](../autonomy/PRUNING_RESEARCH_HISTORY.md), with a historical banner. [PRUNING_RESEARCH.md](../autonomy/PRUNING_RESEARCH.md), [COMPACTION_HANDOFF.md](../autonomy/COMPACTION_HANDOFF.md), [CONSOLIDATION.md](../CONSOLIDATION.md) and IPOPT handoffs remain provenance, not current branch/implementation instructions. The [document index](DOC_INDEX.md) gives every document's topic/status.

When a decision changes, update this page/current state and the active plan. Preserve evidence and source-era results; annotate superseded claims instead of rewriting historical measurements to match new behavior.


## Latest Real11 terminal-goal clarification (2026-11-03)

Supersedes the earlier fixed64 experiment and the literal-finite-B7 interpretation: use the B7-height virtual landing region X<729.7, Z>=-1095.3, extending without an upper Z bound (and without a new lower X bound). Minimize landing time first, then maximize Z at that earliest tick. This is the actual terminal objective, not a common-clock progress proxy. Do not add fictitious approach collisions by extending physical B7 geometry. The existing fixed-clock evidence remains historical; the corrected goal is implemented at `71c9bb7e` and has new [cold evidence](../autonomy/experiments/virtual-finish/README.md). The existing scalar model determines the nominal downward height clock; actual finite-scene Y/ground remains authoritative for physical replay. Rank complete candidates by time, then objective, without fake goal geometry. See CURRENT_STATE.md for the full contract.

## Minimum-run rule clarified by the user (2026-11-03)

For the same interaction pattern (support/obstacle/contact choices), test the least-run timings for feasibility and continue the feasible representative rather than normally solving extra-run copies. This is a heuristic; retain earlier unknowns and audit skipped candidates in false-pruning regressions. Distinct interaction choices remain distinct, and absolute timing or repeated standing contacts alone must not create a new pattern. Current DFS does **not** implement this exclusion: it uses run count for ordering, and its family key includes absolute timing. The [call audit](../autonomy/experiments/virtual-finish/PRUNING_AUDIT.md) records the implementation gap, alongside missing early propagation of known remaining finish duration. No runtime fix is included in this documentation turn.

## Latest implementation: local run pruning; time reasoning deferred

User clarification supersedes a total-run interpretation: compare ordinary preparation runs immediately before the **last effective jump**, keeping prior decisions and interaction choices fixed. Implemented at `db211b2b`, with [results](../autonomy/experiments/last-jump-runs/README.md). Earlier missing-status paragraphs describe the archived pre-policy audit. Higher-run copies are heuristic exclusions with saved audit programs; smaller/unknown/equal-run alternatives and distinct interactions remain eligible. Keep the additional completion-time reasoning deferred: proving no faster continuation of7–9 before ruling out10–12 is a separate future improvement. No new remaining-time bound is introduced.
