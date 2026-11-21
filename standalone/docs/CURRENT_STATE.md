# Current standalone state

Snapshot, 2026-11-16. This file describes what is true now; dated results live in
the linked evidence. Standing user decisions are in [decisions](DECISIONS_AND_PLANS.md);
everything not being worked on is in the [deferred-work register](DEFERRED_WORK.md).

## Branches and status

- Base branch: the fork's `main`. It contains the Real11 extension work: extended
  geometry and bounds, the six-direction [face catalog](design/FACE_MERGING_IMPLEMENTATION_PLAN.md)
  (not yet consumed by any generator) and the [longer Real11 task](../research/real11-long-solve/README.md).
- Merged as PR #5 (`feat/disk-interim-solver`): automatic discovery runs on the
  Clarabel [disk relaxation](reference/NUMERICAL_SOLVER.md#disk-relaxation): the
  HiGHS LP screen is gone, interim candidates are relaxed, an acceleration-shortfall
  alarm on jump ticks confirms with IPOPT, and one IPOPT finalization
  publishes the physical route ([execution](ANCHOR_SEARCH_EXECUTION_REFERENCE.md#numerical-backend-and-finalization)).
  Real11 keeps the replayed T64 with 0 interim IPOPT workers and 1 finalization call,
  median search 3.6 s against 7.5 s on `c9deef42` ([evidence](../research/disk-relaxation/README.md)).
- Merged as PR #4 (`feat/jvm-free-orchestrator`): search is JVM-free. The
  in-process Python movement bridge is the default and the Java bridge an explicit
  oracle ([evidence](../research/jvm-free-bridge/README.md)).
- Active: `feat/encounter-groups`, local. It removes caller-supplied decision
  windows, gives the block table per-kind sets, and searches each automatically
  found [encounter group](design/ANCHOR_POLICY_DECISIONS.md#o8--encounter-groups)
  as one frame, where decision checks compare climbed side families, keep the
  best with the exact rows of settled stretches, and hold the others as
  alternatives restored when the branch dies. On the relaxed backend each
  decision optimizes toward the next required anchor, alarmed candidates are
  confirmed once at the end of the repair loop, an obstacle gets passages
  only once a solve hits it, and an ordinary decision builds only minimum run
  ticks: before advantageous jumps faster than its fastest jump-now landing, and
  before run-offs and step-ups onto other blocks, a run-off leaving its launch
  block by an open face ([controls](../research/encounter-groups/README.md)).
- Merged as PR #3 (`perf/early-passage-screen`): it adds
  [geometry-driven avoidance passages](design/AVOIDANCE_PASSAGES.md) on obstacle
  clusters (touching blocks are one obstacle; [evidence](../research/obstacle-clusters/README.md)), screening of
  accumulated passage prefixes before extension, anchor legs that optimize their
  alternatives with a switch-tick climb and keep close fallbacks
  ([evidence](../research/switch-climb/README.md)), anchor search under the global
  run budget instead of service-turn quotas, and ceiling moves in the Real11 case.
- Native gate (`tools/native_gate.py`): the current result is in [testing](TESTING.md).
- Latest cold Real11 control from PR #3: T64 at 5.9 s with 18 solver requests; the
  DFS explores every runnable alternative and ends `exhausted` at 9.7 s. See
  [performance](PERFORMANCE.md) for the full series of controls.
- Longer Real11 trials are paused until the solver changes settle. The saved
  fixed-B5 checkpoints predate constraint policy9/state5, so the next trial starts
  cold from the [resume checklist](../research/real11-long-solve/README.md#status-and-resumption).

## Focus areas and where to read

**Improving the avoidance generator**
1. [Avoidance passages](design/AVOIDANCE_PASSAGES.md): the contract implemented by
   `constraints/encounters.py` and `constraints/variants.py`.
2. [Generation relevance](design/GENERATION_RELEVANCE_PLAN.md): one shared eligibility
   gate; required versus unwanted interactions; screen the whole proposal before truncation.
3. [Obstacle relevance](design/OBSTACLE_RELEVANCE.md): interval and disk-relaxation reachability screens.
4. [Obstacles and blockages](design/OBSTACLES_AND_BLOCKAGES.md): Y-X-Z overlap, blockage
   timing templates, wall-hit sprint loss, run-off sources.
5. [Generic route discovery research](research/GENERIC_ROUTE_DISCOVERY_RESEARCH.md),
   sections 6.2 to 6.4 (when side or corner avoidance matters), 7.2 and 7.3 (required
   versus unwanted, exact merging) and 8.3 (generate before products).
6. [Pruning research](research/PRUNING_RESEARCH.md): "fixed support constraints already
   exist; avoid assembly over-enumerates B2", "systematic obstacle relevance" and the
   "Real11 full candidate audit" (canonicalize per obstacle before products).
7. [Spatial route research](research/SPATIAL_ROUTE_RESEARCH.md): a chosen free cuboid
   replaces the six-way avoid disjunction with linear bounds.

**Reducing DFS states**
1. [Anchor execution reference](ANCHOR_SEARCH_EXECUTION_REFERENCE.md): the combination
   catalogue and every dispatch and pruning boundary.
2. [Anchor policy decisions](design/ANCHOR_POLICY_DECISIONS.md): hard transfer, cadence,
   finish bound, protections.
3. [Hard-jump comparison and reachability record](design/HARD_JUMP_TICK_PRUNING_AND_REACHABILITY_PLAN.md):
   the canonical comparison rule, `InteractionGate` semantics, duration certificate.
4. [Branch scheduling](design/BRANCH_SCHEDULING_INVESTIGATION.md) (deferred): when work runs
   and when it is excluded.
5. [Automatic grouping](design/PRUNING_IMPROVEMENTS_BACKLOG.md#automatic-grouping-joint-constraint-solving):
   every interaction type now reads one [shared scene geometry](design/FACE_MERGING_IMPLEMENTATION_PLAN.md)
   (`spatial/surfaces.py`); grouping itself is to be re-planned on it. Commitment
   and cadence stay deferred. Other pruning gaps stay in the backlog.
6. [Pruning research](research/PRUNING_RESEARCH.md): "why one best arrival fails",
   "how branch and bound could help", "candidate pruning and reuse mechanisms" and
   "schedule-gated comparison, run/jump protection and exact processing order".
7. [Search objective](design/SEARCH_OBJECTIVE.md) and [time-progress labels](design/TIME_PROGRESS_FRONTIER.md).

**Improving performance**
1. [Performance](PERFORMANCE.md): controls, hotspots, owners and invariants.
2. [IPOPT constraint reduction](IPOPT_CONSTRAINT_REDUCTION.md), [feasibility mode](IPOPT_FEASIBILITY_MODE.md)
   and [checkpoint storage](CHECKPOINT_STORAGE.md).
3. [Stratfinder investigation](research/STRATFINDER_INVESTIGATION.md): coefficient caching
   and early prefix rejection.

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

Search needs only Python and the numerical extras: the default movement bridge
(`bridge.python_compiler`) answers coefficient, footprint and scene requests in
process with the Java bridge's replies, and the Java bridge is an explicit oracle
([runbook](RUNBOOK.md#install-and-environment)).

All intermediate comparison predicates live in `search.comparison`; callers
normalize evidence and execute its verdict. Legacy `minimum_runs` family extraction
and `time_progress` record APIs delegate rather than owning a second policy.
Terminal actualT/objective ranking stays separate. Cells, portals and soft guide
points are not discrete input clocks or fixed dynamic seams.

Physical automatic continuation is outer schema2 / DFS8 / movement context4 /
repair assembly13 / constraint policy17/state11. Hard4/search9/comparison1/guide2/proposal1 semantic identities
also matter; schema equality does not make old policy checkpoints compatible.
Storage remains manifest/SQLite version1. Physical anchor DFS now uses the session's
global deadline/call allowance continuously, without service-turn or per-program
quotas; per-request solver timeouts remain. Non-anchor event search retains its
internal slices. External resume decodes isolated mutable values and checks the
saved incumbent. The [run-budget change](../research/anchor-run-budget/README.md)
records the removed limits and continuation checks.

## Real11 task

The [maintained case](../cases/real11/physical-anchors.json) supplies B2/B3/B4/B5
anchors, normal, stepping and ceiling movement, horizon 200 and no first-jump
whitelist. B7 is the physical extended box `[729,0,-1095,730,7,-1093]`; the landing
minimizes T, then maximizes Z. Guide `(729.5,7,-1093)` is not a center-coordinate
cap. The [unrestricted control](../research/initial-domain-selection/README.md)
queries first jumps 0 and 8 and recoverably defers 9 to 12 before numerical work.
Timed controls do not establish global minimum T or Z, or executable replay in the
real world. The [Real11 guide](REAL11_GUIDE.md) maps each question to its evidence.

## History

These records explain earlier implementations/results; their old checkout paths,
status paragraphs and policies do not direct current work.

| Stage | Evidence / design record |
| --- | --- |
| Selective numerical solver integration, nested persistence, explicit recovery and lazy selected-only bounds | [Architecture](ARCHITECTURE.md), [profiling](../research/anchor-runtime-profile/README.md) |
| Anchor feasibility / finite B7, then correction preserving source hops | [Initial anchor implementation](../research/anchor-feasibility/README.md), [transfer/preparation correction](../research/anchor-transfer-preparation/README.md) |
| Generic potential route/contact discovery and event backend | [Cited research](research/GENERIC_ROUTE_DISCOVERY_RESEARCH.md), [cold evidence](../research/event-route-discovery/README.md) |
| Typed cells/portals/corridors, guide geometry and old automatic-versus-supplied shortlists | [Automatic corridor evidence](../research/automatic-corridors/README.md) |
| Virtual B7 height crossing and latest-jump run-family comparisons | [Virtual-finish evidence](../research/virtual-finish/README.md), [local-run evidence](../research/last-jump-runs/README.md) |
| Imported goal-aware DFS/early interactions and original fixed64 pilot | [DFS evidence](../research/dfs-interactions/README.md), [Real11 source-era ledgers](REAL11_GUIDE.md) |

