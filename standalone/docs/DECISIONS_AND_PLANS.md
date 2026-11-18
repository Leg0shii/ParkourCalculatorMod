# Decisions

Standing user decisions and the rationale behind them. User instructions override
these docs. What is implemented now is in [current state](CURRENT_STATE.md); work
that is deferred or unapproved is in the [deferred-work register](DEFERRED_WORK.md).
When a decision changes, update this page and current state in the same change;
keep historical measurements tied to their source revision instead of rewriting them.

## Standing decisions

| Topic | Decision and rationale |
| --- | --- |
| Layering | Preserve physical contacts, passages and movement alternatives in the shared DFS. The engine decides in coupled required-anchor spans, uses backward guide objectives with early stopping, and retains recoverable alternatives. Spatial geometry does not set jump timing or constant edge weights. |
| Minimum free width | A passable gap between blocks is at least 0.612 (0.012 beyond the player's 0.6 width), so no player-space free piece narrower than 0.012 exists; such pieces are numerical artifacts and are not kept anywhere (width measured along a tangent axis). Implementation: [generation follow-ups §2](design/GENERATION_FOLLOWUPS.md#2-minimum-free-width-in-the-landing-cover-path). |
| Obstacle geometry | Build one player-space obstacle representation per scene (union of rectangles per height band) and use it wherever geometry becomes avoidance sides, contacts or landing regions; faces shared by solids the player cannot pass between are one side; collision checks stay per box. Implementation: [generation follow-ups §3](design/GENERATION_FOLLOWUPS.md#3-obstacle-footprints-one-player-space-representation). |
| Switch ticks | The objective is unimodal in a switch tick: pick each obstacle-cluster family's switch ticks by hill-climbing on optimized solves; IPOPT unknown counts as infeasible; keeping several open until downstream is deferred. Implementation: [generation follow-ups §7](design/GENERATION_FOLLOWUPS.md#7-switch-tick-hill-climb-with-optimize-calls). |
| Avoidance generation | Geometry-driven passages ([design](design/AVOIDANCE_PASSAGES.md)): one encounter per box per landing segment, phase covers from geometry, later domains conditioned on actual earlier rows, one builder for upfront generation and collision repair. No local avoidance, repair, switch-count or retention caps; global run and call budgets keep unfinished work lazy; per-request numerical timeouts remain. Source-era cap fields are read and dropped. |
| Grouping and commitment | Automatic grouping and commitment of close movement decisions is deferred ([backlog](design/PRUNING_IMPROVEMENTS_BACKLOG.md#automatic-close-decision-grouping-and-commitment-2026-11-16)). Until then movement cadence, anchor comparison and inter-movement retained constraints stay as they are, and the supplied-window planner remains live compatibility code. |
| Relevance | One generic block, tick and phase eligibility helper for required and unwanted interactions; no landing or avoid special-case system. Proved impossible means no interaction-derived choices, rows or checks. Unknown retains coverage. |
| Spatial route identity | Support occurrences and 3D passages remain meaningful. Generation excludes returning to a departed physical owner after another block; consecutive same-block setup hops stay supported. Revisits are deferred ([plan](design/REVISIT_DISCOVERY_DEFERRED_PLAN.md)). |
| Hard transfer | One hard transfer S to E can follow repeated S to S setup hops, even after latching, for every source anchor; all setup impulses count in J. Direct feasibility anywhere latches that anchor transition to single-jump structure for the whole solve, across changed incoming histories. **Declared assumption:** hard jumps between anchors never involve stepping, so flight minima consider ceilings only and unknown shortening ceilings yield zero. This is part of hard policy3 and continuation identity, not a proved fact about all maps. |
| Ordering | Absolute T, then requested grounded J, then R (ordinary) or A (turnaround), as `(T,-J,tickCost,id)` in `search/comparison.py`. Turnaround is a binary test against one approach edge (imaginary diagonal if needed) plus a 0.3-block margin, with a 110 degree guide cue. Each decision selects one whole key; no blended scores or progress key. |
| Comparing alternatives | Compare only at required anchors and the final task; optional movement inside a span is coupled. Slower compatible hard arrivals are recoverably deferred unless protected by more J OR fewer R (fewer A in a turnaround); raw extra A or R is no protection. Equal time stays live. A strict optimistic full-finish bound `T+max(H,V)>bestT` overrides local protection. Deferrals are not infeasibility proofs. |
| Different schedules | Absolute-tick position and velocity are not comparable across different ground and air histories. Equal counts do not establish equal schedules. Legacy common-clock guards stay in their compatibility context. |
| Coupling and cadence | All decisions between required anchors stay coupled through original-state re-solves with earlier facings free. Solve at every completed meaningful jump or contact decision (cadence 1), never per raw input; anchors and the final solve are mandatory. Cadence 2 or 4 is an open measurement, chosen by wall cost, not call count. |
| Solver boundary | Inputs, events and physical history are fixed before each exact-node query. Only that node can receive an infeasible result; a child failure never marks an untested ancestor. No ancestor-testing pass. An empty, exhausted constraint generator with no remaining repair work is exhausted (a backtrack), not suspended and not infeasible. |
| Intermediate solves | Intermediate and anchor solves use the configured onward objective and stop at the first validated feasible point; the final solve optimizes the real objective. `constant_zero` remains an optional API mode, not the default ([feasibility mode](IPOPT_FEASIBILITY_MODE.md)). |
| Run budgets | Physical anchor DFS uses the session deadline and global call allowance continuously, with no service-turn or per-program quotas. Work that cannot start inside the budget stays pending. |
| Local guidance | Backward nearest-point guides and a continuous progress objective with stop-on-feasible. Progress is not a discrete key or pruning rule. |
| State protection | Position and velocity protection helpers are inactive for a global peer-exclusion pass; the tick-only policy adds no such dominance claim. A first feasible pose is not a bound on later re-solves. |
| Inertia | Disable thresholds only; keep normal momentum. Exact inertia-pattern work is deferred. |
| Corridors | No automatic raw-walk containment or portal-clock filters. Guide points are soft objective and turn cues. Required physical contacts, passages and full scene validation stay hard. |
| Real11 task | Physical B7 is `[729,0,-1095,730,7,-1093]`; land on actual B7, least T then maximum Z; guide `(729.5,7,-1093)`. No virtual or X<729.7 rows. Anchors B2/B3/B4/B5 are caller-declared for the benchmark; movement choices and optional supports remain search decisions. Required anchors are not a fixture-specific algorithm rule. The maintained case has no first-jump whitelist: producers, screens, comparisons and finish bounds select work. |
| Longer Real11 | [Extended task](../cases/real11/extended-anchors.json): anchors B2/B3/B4/B5/B7/B20, terminal B22. B6 stays physical X[730,731] and B7 unit size; B9's two entries are B9a/B9b; B13 is a two-box stair ([geometry](REAL11_EXTENDED_GEOMETRY.md)). Trials are paused until the current solver changes settle; resume cold through the [checklist](../research/real11-long-solve/README.md#status-and-resumption). |
| Face catalog | [Face merging](design/FACE_MERGING_IMPLEMENTATION_PLAN.md) detects adjacent coplanar faces in six orientations and represents exact merged covers with source membership. Nothing consumes it yet. |
| Budget | Minutes or longer are acceptable for accuracy. Explain structural failures before widening caps; preserve unfinished work. |
| Workflow | Persist decisions and measured facts in these docs; plan, handoff and resume notes stay out of the repository. Commit a feature with its tests, docs and fixtures. |

## Prior research: consult before restarting

| Topic | Where | How to use it |
| --- | --- | --- |
| Cell decomposition, Baritone, Stratfinder | [spatial research](research/SPATIAL_ROUTE_RESEARCH.md), [source review](research/HEURISTIC_SOURCE_REVIEW.md), [Stratfinder](research/STRATFINDER_INVESTIGATION.md) | Sources behind route abstraction; cells are implemented. |
| Route discovery theory | [generic route discovery](research/GENERIC_ROUTE_DISCOVERY_RESEARCH.md) | Formal scope and merge conditions for interactions. |
| Mixed-integer and disk/SOCP models | [mixed-integer research](research/MIXED_INTEGER_RESEARCH.md), [guidance module](../research/route_guidance/README.md) | Restricted-family experiments; a local NLP success is not a certified bound. |
| Pruning framework | [pruning research](research/PRUNING_RESEARCH.md) | Safe merging, bounds and the history of earlier failures. |
| Time and progress labels | [time progress](design/TIME_PROGRESS_FRONTIER.md) | Comparison semantics retained; the old processing key is replaced by goal-aware DFS. |
| Resume and dead prefixes | [architecture](ARCHITECTURE.md), [orchestrator fixes](../research/orchestrator-fixes/README.md) | The first-floor death proof is conservative and incomplete. |
| Known future improvements | [pruning backlog](design/PRUNING_IMPROVEMENTS_BACKLOG.md) | Revisit when a measured map exposes a need. |
