# Current standalone state

2026-11-16: the [geometry-driven avoidance replacement](design/AVOIDANCE_PASSAGES.md)
is implemented. `constraints.encounters` builds
meaningful phase covers within landing segments; `ConstraintVariants` conditions
later domains on actual earlier rows. Upfront generation and collision repair use
one builder. Ghost face histories, switch-count budgets and local avoidance/repair
attempt and retention limits are removed. Global run/call budgets preserve lazy
unfinished work; per-request numerical timeouts remain.

The audit covers finite-contact entry geometry, proved-clear intermediate sides,
unknown named probes, joint obstacle requirements, exact continuation and descriptive
phase traces. Current movement cadence, comparisons and inter-movement commitment
remain; [automatic grouping/commitment consolidation](design/PRUNING_IMPROVEMENTS_BACKLOG.md#automatic-close-decision-grouping-and-commitment-2026-11-16)
is deferred. New identities are constraint policy8/state3 and repair assembly10;
old generator/DFS cursors require their original source checkout.
The native gate passes640 tests. The cold300s Real11 control finds T64 at14.1s
versus the parent's T67, then ends at25.1s with five IPOPT-unknown nodes suspended,
so it claims no exhaustive coverage. All47 retained witnesses replay.
See [implementation and measurement evidence](../research/geometry-passages/README.md).
The earlier records below describe their source revisions.

On `perf/early-passage-screen`, obstacle assignments receive the existing full
pre-solver reachability/linear checks before downstream alternatives are generated.
Adding B7 rechecks accumulated B6+B7 constraints; the angle solver stays coupled.
The captured B6 catalogue loses237 of277 descriptions before B7 generation.
See [implementation and validation](../research/early-passage-screen/README.md).
Native636 passes with zero skips. The cold control reduces B7 enumeration visits
39.75%, makes59 requests versus49, and retains the same T67/Z; all50 retained
witnesses replay. Pre-change checkpoint compatibility also passes.

On `fix/anchor-run-budgets`, physical anchor search now runs continuously under
the session deadline and global call guard, with per-request timeouts and no
service-turn/per-program time or call quotas. Unstarted repair work remains
pending at budget exhaustion. See the [implementation and validation](../research/anchor-run-budget/README.md).
This user-approved scheduling change follows the bookkeeping optimization below;
it does not implement the separate proposed branch-priority scheduler.
Native631 passes. The new 300-second control is worse at T67 versus T64: 42 of
49 requests refine 67-tick journeys and no 64-tick request is reached. All 41
retained witnesses replay; compatible durable continuation also passes.

Active follow-up investigations: [bookkeeping performance](PERFORMANCE.md) is handed to another agent/worktree; [branch scheduling](design/BRANCH_SCHEDULING_INVESTIGATION.md) remains in this session. Both start from `a11b9b18`; this handoff adds no runtime or pruning change. The current code still descends by DFS, while the proposal makes retained continuations compete earlier by optimistic finish.

2026-11-16 user deferral: **pause further longer Real11 trials until the other
agent finishes the solver improvements**. Preserve the fixed-B5 fixture and
latest manifest/SQLite pair; do not start another solve as background follow-up.
The [experiment resume checklist](../research/real11-long-solve/README.md#status-and-resumption)
records the integration review, compatibility and validation checks required
before returning. The other agent's assignment remains active independently.

2026-11-15 longer Real11 trial: [new task and fixed-B5 experiment](../research/real11-long-solve/README.md)
add required B7/B20 and terminal B22. Existing initial-program support fixes
initial-0 decisions through B5@55 while all earlier facings remain free. The first
300s trial reached unit B7@64, then exposed an empty-exhausted-constraint outcome
bug; that exact node now backtracks without a numerical infeasibility claim.
Pending/interrupted alternatives remain suspended. The611-test native gate passes
without skips. Corrected300s+120s exact continuations backtrack eight exhausted
nodes but have not reached B20/B22; latest work is constraint enumeration for
an optional B11@68 candidate. Prefix audits preserve all55 decisions, while the
B7 witness changes all55 earlier facings. Full evidence and latest saved resume
path are in the experiment README.

2026-11-15 geometry follow-up: [longer Real11 input and bounds](REAL11_EXTENDED_GEOMETRY.md)
record B8–B22, original unit B6 (latest user correction), unit B7 and the B13 stair.
The geometry-only input stays separate from the explicit longer task above.
The existing extended-B7 benchmark and its measurements below retain their
original input.
The [implemented face-merging contract](design/FACE_MERGING_IMPLEMENTATION_PLAN.md) has exactly
two parts: detect adjacent coplanar faces across all six orientations, and
represent their exact unions with original block/solid membership for later
decisions. It includes physical-boundary exposure, partial
occlusion, component-specific contributions, stable IDs and catalog queries.
Both parts are complete: immutable catalog queries/serialization and
`tools.geometry_bounds --faces` are available, with54 focused/compatibility/package
checks. Real11 has102 merged faces from144 source faces; source-specific static
validation is in the [record](../research/real11-extension/face-catalog-validation.json).
The catalog is available for later decision integration; current generators still
use their existing geometry contracts.

Follow-up status2026-11-15: the user has **deferred interleaving/scheduler implementation**. The [preserved plan](design/BRANCH_SCHEDULING_INVESTIGATION.md) retains the existing tick priority, work-category/outcome clarifications, ownership/cost analysis and resumption checks; none is implemented by this documentation. Current code remains DFS. The separate [bookkeeping performance handoff](PERFORMANCE.md) is not paused by this request. Use the [deferred-work register](DEFERRED_WORK.md) for other preserved follow-ups and their actual status.

On `research/bookkeeping-performance`, [already-visited child suppression](../research/enumeration-reduction/README.md)
now removes duplicate constraint queue visits while preserving ordered unique
options and legacy cursor compatibility. Its fixed-work enumeration improves
2.9–3.6x; native627 passes. The timed cold control preserves T64/replay but has a
worse Z endpoint, so improved whole-search quality is not established.

2026-11-15: package/layout refactor through `9c79e741`; solver behavior remains feature `dd738c98`. Production is installed from `src/parkour_solver`, with separate tests/tools/research/native roots. See [layout](LAYOUT.md) and the [refactor ledger](LAYOUT.md). Start with the [execution reference](ANCHOR_SEARCH_EXECUTION_REFERENCE.md)
for the combination catalogue, pruning/deferral table, IPOPT dispatch boundaries,
settled decisions/rationale and module ownership. The [decisions index](DECISIONS_AND_PLANS.md)
records authority; the [implementation record](design/HARD_JUMP_TICK_PRUNING_AND_REACHABILITY_PLAN.md)
and [evidence](../research/hard-jump-comparisons/README.md) record how
this version was implemented and validated. This feature is complete.

## Implemented search

| Area | Current behavior |
| --- | --- |
| Physical automatic routing | Ordered caller anchors; no anchors means one coupled start→finish span. Optional contacts remain movement choices. Geometry provides exposed supports/contact incidence; no authored raw-cell walk or guide containment filter is imposed. |
| Hard transfer | Validated one-transfer jump latches its ordered span globally, including across backtracking/changed incoming histories. Source-local setup hops remain legal. **Declared assumption:** no stepping in the actual hard transfer; only ceilings can shorten its ordinary flight bound. |
| Discrete ordering | Absolute T; requested grounded J/grounded nonjump R/air A local to span. Ordinary `(T,-J,R,id)`, turnaround `(T,-J,A,id)`. More J OR fewer R/A protects a slower compatible hard arrival. Raw extra A/R alone does not. |
| Recoverable alternatives | Slower unprotected hard arrivals are saved, not labelled physically infeasible. Explicit required-continuation failure can restore one saved alternative with scoped bypass after runnable first-class work drains. A successful finish alone does not reopen them. |
| Finish bound | One best validated full finish. Check only new/selected work; exclude `T+max(H,V)>bestT`, preserve equality. H uses phase-correct full covers; V uses applicable latched ceiling-aware flight minima and paid flight age. No incumbent-triggered queue scan. |
| Shared preparation | `InteractionGate` caches exact program preparation, canonical Java coefficient payloads and model envelopes/screens. Joint stepping prerequisites precede side/corner fan-out. Unknown probe bounds retain coverage. |
| Owned proposals / persistence | Common composition uses stable consumed raw ordinals. Actual anchor prefix owns its counts/rows independently of an unused tail; persisted projection flag survives JSON alias loss. Complete programs/repair/cursors/unknown work remain saved. |
| Numerical queries | Selected meaningful completed decisions at cadence1; anchors/final mandatory. No raw-hold NLP or separate ancestor-probe pass. Every query re-solves from original state with earlier facings free. |
| Solve intent / outcome | Intermediate configured onward objective plus early feasible stop; final configured optimization. A feasible seed can avoid an intermediate worker. Exact operational oracle rejection, repair exhaustion and suspended/error are distinct; child failure never labels an unqueried ancestor. |
| Guide / turnaround | Backward nearest-point landing-cover guides, onward E→N objective, no discrete progress/slack score. >110° guide cue; one approach-facing physical/imaginary diagonal edge+0.3-block margin gives one frozen binary mode, with distances and replay flips logged. |
| Visits / movement scope | Multiple current-owner hops supported; departed-owner returns and repeated mandatory anchors deferred. Intentional wall-hit generation deferred; required named-case contacts remain. Supplied corridors/virtual tasks keep separate compatibility paths. |

All intermediate comparison predicates live in `search.comparison`; callers
normalize evidence and execute its verdict. Legacy `minimum_runs` family extraction
and `time_progress` record APIs delegate rather than owning a second policy.
Terminal actualT/objective ranking stays separate. Cells, portals and soft guide
points are not discrete input clocks or fixed dynamic seams.

Physical automatic continuation is outer schema2 / DFS8 / movement context4 /
repair assembly10 / constraint policy8/state3. Hard3/search5/comparison1/guide2/proposal1 semantic identities
also matter; schema equality does not make old policy checkpoints compatible.
Storage remains manifest/SQLite version1. Physical anchor DFS now uses the session's
global deadline/call allowance continuously, without service-turn or per-program
quotas; per-request solver timeouts remain. Non-anchor event search retains its
internal slices. External resume decodes isolated mutable values and checks the
saved incumbent. The [run-budget change](../research/anchor-run-budget/README.md)
records the removed limits and continuation checks.

## Current Real11 task and evidence

The [maintained case](../cases/real11/physical-anchors.json) supplies
B2/B3/B4/B5 anchors, normal+stepping+ceiling, horizon200 and declared caps, with no first-jump whitelist.
Ceiling choices were enabled in the maintained case on 2026-11-16; the historical
controls below retain their recorded movement domains. The [case notes](../cases/real11/README.md)
describe the domain change and exact-resume boundary.
The [unrestricted control](../research/initial-domain-selection/README.md) naturally queries first jumps0/8 and recoverably defers9–12 before numerical work; it finds T64 with55/55 retained-witness replays. Archived controls below keep their original `[0,8]` domain. B7 is the
physical extended box `[729,0,-1095,730,7,-1093]`; actual landing minimizes T then
maximizes Z. Guide `(729.5,7,-1093)` is not a center-coordinate cap. The old
virtual goal, extra X<729.7 row and fixed64 task are absent.

| Matched arm | Best T | Workers | Expanded prefixes | Independent retained-witness replays |
| --- | ---: | ---: | ---: | ---: |
| Baseline combined300s |67 |159 |1,114 |75/75 |
| Current combined300s |64 |55 |35 |57/57 |
| Baseline initial0-only90s |64 |56 |427 |34/34 |
| Current initial0-only90s |64 |22 |7 |23/23 |

Both current arms reach Z=−1093.2381026602416. The combined arm has61 distinct
invocation fingerprints,22 saved finish-bound exclusions,8 recoverable deferrals,
0 restorations and18 early common-sequence exclusions. Comparison/bound checks
cost milliseconds; remaining measured cost is largely numerical preparation,
digesting and retained-state copies. The [evidence README](../research/hard-jump-comparisons/README.md)
qualifies timing overlap/instrumentation and gives exact budgets/provenance.

600 native-enabled tests pass without failures/errors/skips;131 archived T67/T64
prefix-bound controls and80 current retained-witness replays pass. Runtime hashes
stay unchanged;54 historical event artifacts remain unchanged. The native module
scope includes generic mixed-ladder controls/all IPOPT tests, but excludes dedicated
src/parkour_solver ladder/climbable/vine/movement-entry modules. No mod build/game run or
new native-helper rebuild was needed. See [testing](TESTING.md).

The wall guards ended both runs; global minimum T/Z and actual-world executable
replay are not established. The latest user correction confirms unit B6; the
longer-course geometry above is separate from these measured controls. They do
not cover unknown world geometry.

## Deferred work

The [central deferred-work register](DEFERRED_WORK.md) links the preserved scheduler plan, revisit design, intentional wall hits, water, cadence/turn tuning, inertia follow-up, proposed research and outstanding acceptance/input work. It distinguishes user deferrals from unapproved ideas and completed source-era backlog claims. Bookkeeping optimization is separately assigned, not deferred by the scheduling pause. Hard-latch/count policy remains a bounded heuristic/domain policy, not a global optimality theorem.

## Source-era history

These records explain earlier implementations/results; their old checkout paths,
status paragraphs and policies do not direct current work.

| Stage | Evidence / design record |
| --- | --- |
| Selective numerical solver integration, nested persistence, explicit recovery and lazy selected-only bounds | [Integration contract](ARCHITECTURE.md), [review](PERFORMANCE.md), [profiling](../research/anchor-runtime-profile/README.md) |
| Anchor feasibility / finite B7, then correction preserving source hops | [Initial anchor implementation](../research/anchor-feasibility/README.md), [transfer/preparation correction](../research/anchor-transfer-preparation/README.md) |
| Generic potential route/contact discovery and event backend | [Cited research](research/GENERIC_ROUTE_DISCOVERY_RESEARCH.md), implemented plan, [cold evidence](../research/event-route-discovery/README.md) |
| Typed cells/portals/corridors, guide geometry and old automatic-versus-supplied shortlists | Spatial plan, [automatic corridor evidence](../research/automatic-corridors/README.md), handoff |
| Virtual B7 height crossing and latest-jump run-family comparisons | [Virtual-finish evidence](../research/virtual-finish/README.md), [local-run evidence](../research/last-jump-runs/README.md) |
| Imported goal-aware DFS/early interactions and original fixed64 pilot | [DFS evidence](../research/dfs-interactions/README.md), [Real11 source-era ledgers](REAL11_GUIDE.md) |

Use [architecture](ARCHITECTURE.md), [runbook](RUNBOOK.md), [debugging](RESULTS_AND_DEBUGGING.md)
and [resume](design/ANCHOR_POLICY_DECISIONS.md) for current work. No implementation
or numerical run from the completed feature remains pending after compaction.
