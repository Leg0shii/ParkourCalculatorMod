# Plan: anchor DFS, global hard latches and observable binary turn heuristics

**Implemented anchor baseline, with current follow-ups through `dd738c98`.**
The [execution reference](../docs/ANCHOR_SEARCH_EXECUTION_REFERENCE.md) contains
current combination/pruning/IPOPT tables, rationale and owners. The later
[hard-jump implementation record](../docs/HARD_JUMP_TICK_PRUNING_AND_REACHABILITY_PLAN.md)
controls shared comparisons, early proposal reachability and flight bounds.
[Current evidence](experiments/hard-jump-comparisons/README.md) records600 native
passes and cold physical T64. There is no M1 implementation to resume and no
checkout switch authorized by the original planning worktree below.

This document preserves the anchor architecture/milestones originally implemented
at `0d340c03`, then source-hop correction `769cdbe3` and solver integration
`91381e5a`. Original milestone results below belong to those revisions. Current
hard policy3 means one S→E transfer **without stepping**, preserving source-local
setup hops; protected hard-arrival deferral now applies in both binary modes.

## 1. What the user has agreed to

| Topic | Latest contract |
| --- | --- |
| Anchors/coupling | Ordered required anchors; Real11 B2/B3/B4/B5. All movement choices between anchors are coupled. Comparisons/promotions only at required anchors/final task. |
| Counts | Absolute T; requested grounded J and grounded nonjump R/air A local to the current anchor span. Named jump cancels still have their horizontal impulse and count J. |
| Default/turn preference | At each decision choose ordinary T,−J,R or turnaround T,−J,A from current point versus edge+margin. One boolean, no blended counts or uncertain label; progress never ranks decisions. |
| Hard jump | A fully validated direct jump anywhere latches that anchor transition to single-transfer structure for the rest of the solve, preserving starting-anchor setup hops, across backtracking/changed incoming histories. This is intentional domain restriction. The actual hard transfer excludes stepping by user assumption; only ceilings affect its flight minimum. |
| Recovery | Restore/reconstruct deferred run alternatives and earlier preparation, walking actual tree edges. Additional same-block setup jumps remain allowed even with a global hard-transfer latch; inter-block returns are deferred. |
| Guides/solver | Backward guide inference; optimize onward toward the next point with existing POSITION objective and stop-on-feasible. User reports another agent measured similar speed. No slack/new constant objective. |
| Finish | Extend physical B7 one block in +Z and require actual B7 landing. Minimize achieved landing T then maximize Z. Terminal guide is +Z physical edge, X midpoint. |
| Exact-node feasibility | Adopt IPOPT accuracy operationally: only a solve of the exact current node, with no later decisions, can mark that node/model infeasible. Child failure does not mark ancestors infeasible. No separate ancestor-testing subsystem. |
| Turn inference | Preprocess >110° guide turns and approach-facing axis/imaginary diagonal edge; initial spatial margin≈0.3 toward S/N. Each decision selects one heuristic from that boundary; preliminary/tunable and observable. |
| Visits | Inter-block returns are not generated now. Same-block setup jumps/chains remain supported after global hard-transfer latching. Future revisit plan persisted separately. |
| Preserved invariants | Original-state continuous re-solves/all earlier facings free, normal momentum/zero thresholds, finite physical contacts, owned prerequisites, caps, physical validation, exact siblings/repair/source ownership. |

Required anchor occurrences remain ordered. Same-block hops and permitted acyclic optional support visits do not advance/reset the cursor except on the next required milestone. Supporting repeated mandatory IDs and physical returns after another support are deferred features. Intentional wall hits and new water/ladder/actual-world mechanics are not added by this change.

## 2. Verified baseline and reuse points

| Source | Existing behavior | Work |
| --- | --- | --- |
| [`guides.py`](cell_routes/guides.py), [`routes.conditioned_anchor`](cell_routes/routes.py), [`Checkpoint.objective`](cell_routes/checkpoints.py) | Midpoint/clamp guide, onward global-finish direction, existing POSITION/customYaw conversion. | Backward anchor projection, finite physical B7 endpoint, next-guide-point objective and turn geometry. |
| [`event_adapter.py`](cell_routes/event_adapter.py) | Dynamic next-contact frames; supportAllowed current+target; finite required region alternatives. | Required-anchor stages, acyclic optional contacts/same-block jumps internally, compact guide/turn refs; retain physical ORs/audit. |
| [`depth_first.py`](cell_routes/depth_first.py) | Schema7 DFS, elapsed>full-finish bound, global run switch, saved excluded programs, no targeted restoration. | Anchor frames, global hard latches, regional priorities, shared-node statuses, actual edge recovery. |
| [`minimum_runs.py`](cell_routes/minimum_runs.py) | Latest-jump family normalization/validated least-run representatives. | Reuse; apply recoverable deferral only to eligible ordinary preparation, with restoration bypass. |
| [`movement_frontier.py`](movement_frontier.py) | Pending/ready full-program records, seen set, resumable expansion; no NLP at every internal node. | Actual node IDs/parent references, exact-query results and solve cadence; same-block jumps and departed-owner eligibility. |
| [`solve_spec.py`](solve_spec.py) | Cumulative J/R and scope/model identities. | Span start/A count/binary decision mode, semantic node identity separate from repair progress. |
| [`interactions.py`](interactions.py), [`reachability.py`](reachability.py) | Shared compiled prefix envelopes/phase geometry. | Keep physical screens; do not use envelope/probe uncertainty to choose a third heuristic mode. |
| [`checkpoint_search.py`](cell_routes/checkpoint_search.py), [`search.py`](search.py) | Guide objective with optimize_goal=True; optimize_goal controls polish and early stop. | Separate objective/early-stop/polish; intermediate first fully validated witness, final real optimization. |
| [`ipopt/solver.py`](../ipopt/solver.py), [`continuation.py`](../ipopt/continuation.py), [`worker.py`](../ipopt/worker.py) | Feasible seed fast return, POSITION/customYaw, stop callback. | Reuse and test, no new objective type/equation. |

The previous 447-test/T67 acceptance used original B7 and virtual task. It does not validate the extended B7 task, global latch, exact-node solve cadence or active visit restrictions. Keep all old sources/evidence unchanged.

## 3. Architecture

```text
scene + original state + required anchors + finite terminal task
  → raw exposed contact/connectivity substrate and physical options
  → backward guide points + preprocessed turn regions
  → one existing outer DFS frame per end anchor/final task
  → actual DFS decision nodes with lazy ordinary/same-block preparation choices
  → time/regional-momentum priority + global hard structure eligibility
  → cheap screens and normal exact-node solves at configured decision cadence
  → full end-anchor guided early-stop solve/physical validation
  → actual child or rejection/backtrack/recovery
  → final minimum-T then actual MAXZ optimization
```

Use DFS ancestry and exact-node outcomes. Only the queried node can receive a numerical infeasibility result; a failed child leaves untested ancestors unresolved. Cadence controls when normal node solves occur, without a separate backward-testing pass. Full program records can retain parent references/statuses; no second search engine is needed.

No inter-block cyclic route generation in this release. Decomposition remains exposed-region/physical incidence data; no raw-cell containment, waypoint equalities, guide-progress pruning or new geometry-based physics. Surface-only decomposition replacement is deferred.

## 4. Anchors, visits and records

### 4.1 Records

| Record | Contents |
| --- | --- |
| AnchorPlan1 / AnchorCursor1 | Ordered required occurrences, initial/final endpoints; next index, actual last anchor/full-input boundary, span start and actual owner. |
| HardJumpLatch1 | Ordered anchor pair/solve context, monotone set bit, certifying single-jump witness/provenance, policy revision. Not parent-prefix scoped. |
| GuidePlan1 / TurnRegion1 | Backward points/patch provenance, finite endpoint, directions/110° cues, one approach edge normal/offset per turn, spatial margin and version. |
| DecisionNode1 | Immutable semantic/model identity, parent/last choice, completed decision depth, last solved depth, actual program, selected physical alternative/scope, status and children/source cursors. |
| NodeFeasibility1 | Untested/feasible/oracle_rejected/exhausted/suspended/error; exact queried node/model, allocation/status and yaw seed. Never inferred negative ancestor status. |
| DecisionHeuristic1 / PriorityRecord1 | Local J/R/A, decision point/source, isTurnaround boolean, selected heuristic/key and edge-policy version. No per-input blended cost. |
| DeferredAlternative1 / RecoveryRequest1 | Actual parent/program or recipe, physical rows/resources, reason, residual work, attempted actions/active edge and restored-run bypass. |
| SupportVisitState1 | Initial/current physical owner and departed-owner set, reconstructed from actual support progression; current owner remains eligible for local jumps. No revisit-family record in active state. |
| SolvePolicy1 | Normal node/anchor guided feasibility or terminal optimization; objective, early stop/polish, completed-decision cadence and exact validation scope. No probe purpose. |

SolvePolicy purpose is distinct from existing preliminary/complete solve_scope values. Changing queue policy does not change the meaning of validation scope.

### 4.2 Physical predicates and continuous freedom

- Required anchors need actual supported contact in finite exposed regions, with original owner/component provenance. Mere height/footprint overflight is insufficient.
- Keep existing normal pre-X/Z Y-phase and named post-input conventions. Same-input landing/run-off remains valid; next span uses actual grounded/airborne boundary, no fabricated ground state.
- Earlier anchors/selected branch rows survive every later full-history re-solve. Preliminary accepted without required event validation does not advance a cursor.
- Full proposals are screened before owned projection/truncation. Omit uncommitted future rows, never keep orphan prerequisites or use a future-failed model to reject its entire weaker ancestor.
- Keep all earlier facings free; no frozen yaw, velocity or endpoint. Existing feasible yaw seeds remain reusable. Prefix feasible means seed/evidence, not a solved seam.
- Physical B7 landing is now terminal. Canonical duplicate final anchor predicates do not add an extra input/arrival; hypothetical same-input achievement still uses actual event binding.

### 4.3 Visits

Current scope does not generate inter-block returns. Track initial/current physical owner and departed owners. A→A→A→B and A→air→A→B are valid local setup; A→C→B can be a valid acyclic optional route; A→C→A is deferred and excluded before expensive generation. Current-owner repeated jumps remain eligible. Compress consecutive same-owner contacts only for this check; preserve every actual input, contact event, component and physical branch in the program.

Apply departed-owner eligibility via supportAllowed and audit emitted programs. Reconstruct from actual history on resume, including original support. Use sourceBlock/physical owner provenance across fragments/coplanar groups; do not reject a same-block hop merely because its decomposed patch changed. Required order remains; do not use later required anchors early if completion would demand an unsupported return. Global hard latches restrict the actual transfer to one jump; setup/recovery must generate multiple same-source jumps even in a latched span.

The complete future revisit mechanism, lazy repetition families, complexity analysis and tests are persisted in [the deferred plan](REVISIT_DISCOVERY_DEFERRED_PLAN.md). Do not implement those families/cursors/benchmarks now. This is explicit feature scope, not a claim that return trajectories are physically impossible.

### 4.4 Initial domain

Original support is implicit; preserve public initial-state fields and independent InitialJumpDomain. Explicit 0/8 controls remain explicit restrictions, not generic exclusions of 9/10/11 starting runs. Recovery does not widen them. Roots are declared controls/resume, not archived cold seeds. Initialize departed-owner state from original support and validate any supplied nonempty root against current no-return scope.

## 5. Counts and binary decision priorities

Local counts cover s≤t<T since the previous required anchor: J=requested grounded jump impulses, R=grounded nonjump, A=airborne; T−s=J+R+A. Named cancel counts J.

One boolean is selected from the decision's current point and preprocessed approach-edge/margin:

```python
key = (T, -J, A, stable_id) if decision.isTurnaround else (T, -J, R, stable_id)
```

Each decision uses one whole heuristic. Do not split its inputs or aggregate counts from different mode regions. Pending symbolic choices inherit the owning decision's known mode for generation. Classify the next actual decision at its current node position. Physical/global hard-jump structure is separate from the boolean; ordinary matching hard-run deferral is permitted outside the turnaround label, not inside it.

Apply the chosen comparator consistently to that decision's pending/ready alternatives and relevant selection/retry service. Existing physical variant ordering within a fixed program can remain. Persist immutable key/label and source decision position/edge-policy ID. Later witness movement is observable diagnostic drift, not silent mutation of queued keys or a position constraint. Reclassification is an explicit versioned service event preserving work.

Keep ready streaming, actual child owners and saved siblings; no global A* score/clock drain. Boundary proximity is logged only and never changes the binary rule or adds a progress key.

## 6. Guided early stop and oracle outcomes

Intermediate: existing onward POSITION/customYaw, stop_on_feasible=True, no repeated polish/equal-cost physical-variant drain. Final: physical terminal objective MAXZ with optimization at actual landing T. Preserve parent seed/no-worker fast path, deadlines, native scene checks and repair.

Factor objective/early-stop/polish settings currently bundled by optimize_goal. For an internal node, compile through exactly its actual endpoint, retain its selected physical/past-anchor obligations, and omit all later decisions/end-anchor rows. The next pending guide point supplies soft direction; reaching the pending anchor is not required at the shorter node clock. Include resolved policy/objective/scope in repair compatibility. A first scalar feasible/native-invalid point still needs physical repair/alternative validation before acceptance. A completed failed **exact physical model** prunes that model's descendants under the user's oracle assumption; other unselected physical alternatives remain separate branches.

Designate what constitutes one completed feasibility attempt (allocation/retries/physical branch). Completed valid no-solution attempt may prune under the adopted oracle policy, including spent allocated query budget; cancellation, unstarted work, worker/compiler errors or suspended model construction remain distinct. Report oracle_rejected, not a claimed mathematical certificate. The user has explicitly selected this operational assumption; do not reinsert a certificate-only gate.

Exact-node feasibility identity includes original state, exact retained input/contact history, physical rows/alternative/resources, earlier anchors, horizon/scope and solve policy. Factor out dynamic repair cursor from program_search_key for semantic identity. Reuse compatible feasible yaw but no cross-scope exact rejection. A failed end-anchor child never marks an unqueried ancestor infeasible; only its own queried model is rejected.

## 7. Finite extended B7 and backward guides

New fixture only:

```text
B7 old solid [729,0,-1095,730,7,-1094]
B7 new solid [729,0,-1095,730,7,-1093]  # +1 block in +Z
physical landing on B7; no old virtual rows/X<729.7 condition
terminal guide q=(729.5,7,-1093)
objective: least actual landing T, then MAXZ
```

X midpoint is neutral objective guidance; finite support still constrains X. Existing player overlap can permit center Z≈−1092.7 minus clearance, so do not cap at the physical guide edge or replace support conventions. Geometry extension affects collision scene/domain hashes, not just a plotted line. Archived fixtures/results remain unchanged; new measured task has a separate identity.

Backward recursion projects successor point into each preceding finite landing cover, with stable nearest-patch ties and original initial point. Reuse/factor conditioned_anchor projection instead of original-start/global-finish midpoint calls. Keep disconnected covers separate; guide-selected patch is not a required physical branch.

At E use normalize_xz(q_N−q_E) via Checkpoint.objective, actual final uses MAXZ. Zero direction uses a documented local tangent/fallback with undefined status, not a fabricated turn. User reports roughly equal speed measured elsewhere; adopt the objective direction, verify the implemented new task normally rather than requiring constant-objective research. No slack/new objective type.

## 8. Preprocessed edge and binary decision classifier

Detect guide turns >110° from full horizontal vectors. Preprocess one approach-facing edge at each E. With u_S=unit_xz(q_E−q_S), u_N=unit_xz(q_E−q_N), normal n=unit(u_S+u_N), support c=min_{p in physical footprint(E)} n·p, initial margin m≈0.3:

```text
turnaround(decisionPoint) = (n·decisionPoint >= c−m)
```

This one edge is imaginary/diagonal when needed and replaces the old two-plane wedge. It includes E and the farther-away side plus margin toward S/N. Physical footprint coordinates are not already hitbox-expanded center bounds. Equality is inside. A degenerate zero-horizontal guide leg registers no turn cue; there is no unknown/possible membership label. Multiple applicable boundaries use any-inside for one boolean, recording all matches. Scope boundaries to their route neighborhood `(S,E,N)` so old unbounded half-spaces do not affect unrelated later spans.

Use the actual current decision point: original X/Z at root, otherwise validated current-node witness X/Z under the starting every-decision solve cadence. This is a heuristic reference only; later all-history re-solves keep that position free. Pending symbolic alternatives use their owner's mode. A decision crossing the boundary is not subdivided; its successor gets a new binary label at its own current point. Any later batched-cadence experiment must name/log its deterministic position source for unsolved nodes and still return exactly one boolean.

Do not classify envelopes, infer uncertain membership, add mixed-region costs or temporal label dilation. The first implementation uses the explicit spatial margin. A1-input margin remains a possible later tuning representation only, with an explicit binary rule. Other physical reachability/probe uncertainty remains in physical validation and is not a third heuristic class.

The rule is preliminary: log position/edge/margin/distances, label, selected key and pruning behavior; draw axis/diagonal boundaries and two-label points. Later re-solve label flips are diagnostics. Global hard latch stays structural; binary turnaround selects A instead of R as tick cost; more J OR fewer A protects a slower hard arrival, replacing the original ordinary-only deferral exemption. No hard waypoint/edge containment constraint is introduced.

## 9. Bounds, rejection and recovery

| Reason | Action |
| --- | --- |
| Global latched structure / explicit domain/cap violation | Reject as selected policy domain; recovery cannot bypass a latch. |
| Physical contradiction or adopted completed oracle no-solution | Reject exact prefix/model descendants; preserve unresolved parent alternatives. |
| Fixed prefix already T>T_best validated full finish | Prune descendants and backtrack. Apply the shared strict optimistic-finish bound; equality stays live unless a justified remaining-time bound makes it strictly later. |
| Slower intermediate anchor arrival | Primary deferral/restoration as requested, not the full-finish bound. |
| Slower compatible hard arrival without more J OR fewer R/A | Recoverable deferral with exact program/ownership, validated peer proof and restored-work bypass. Applies in ordinary and turnaround modes. |
| Work slice/cancellation/error before oracle completion | Persist/suspend/error; no fabricated oracle rejection. |

Bind actual achieved event before comparing unused proposal suffix length. Changing an earlier decision creates another prefix and is not excluded by its old child's time/oracle rejection.

Global latch learning is a solve transaction, survives frame removal, and revises eligibility of all queued/deferred work. Retire ineligible multi-transfer/intermediate-support structures with audit; preserve same-source setup hops, other sources and run variants. Do not scope the latch to the certifying parent or silently reopen it on downstream failure.

Recover earlier arrival/run decisions one actual edge at a time. Generate extra current-block setup jumps and earlier runs for chaining both before and after a latch; do not generate inter-block return preparation now. A latched transfer remains one jump, with its source-local setup preserved during recovery. Never graft a sibling's selected constraints or yaw state. Stable request/action IDs prevent immediate redo/re-deferral loops.

## 10. Normal exact-node solves and decision cadence

There is no separate prefix/ancestor probe. On a scheduled node, run the normal continuous pipeline on its exact full history through that endpoint and no future decisions. A feasible result is a yaw seed/evidence; earlier facings remain free in descendants. An infeasible designated solve rejects only that node/physical model and descendants. Child failure never assigns infeasibility to its unqueried parent. If all eligible children are exhausted, DFS can backtrack from an exhausted parent without claiming its shorter continuous model is infeasible.

**Recommended initial cadence:**1 completed meaningful movement decision, with required anchors/final always forced. A level is a completed jump/landing or selected named contact/case action, not a simulation input or raw one-input run-hold extension. Ordinary run duration is preparation included in the next actual movement decision's exact model. Current normal generator can expose repeated single-input same-support holds; do not accidentally issue an NLP per such construction input.

Persist completed decision depth and the last actually solved decision depth. Proposed interval 2/4 diagnostics skip internal solves until that many further completed decisions. If a deeper scheduled node fails, return to its parent and try another child; do not launch an upward ancestor-testing pass. Required-anchor promotion/final optimization still force a normal query at their actual event clock regardless of interval. Deduplicate only truly identical objective/policy/model/scope; final MAXZ must not be replaced by an earlier intermediate early-stop result.

Compare intervals 1,2,4 and anchor-only under matched new task/budget after the baseline works. Record full wall time, actual worker/attempt counts, first full finish/T/Z and discrete work beneath eventually rejected nodes. Interval1 is a recommendation for a clear baseline, not measured fastest. Use2/4 if solver cost dominates and nodes are usually feasible; keep1 if cheap warm starts prevent expensive dead expansion. No adaptive controller needed initially.

Parent links/result records belong to normal DFS state. Reuse exact dedup/candidate identity without dynamic repair-progress hash, record which node was actually queried, and retain unresolved physical OR alternatives. Do not add speculative ancestor statuses, separate probe caches or repeated per-air-input solves.

Current revisit eligibility is the departed-owner filter with repeated current-owner setup jumps allowed; future repeat-family generation is only in [the deferred plan](REVISIT_DISCOVERY_DEFERRED_PLAN.md).

## 11. Proposed API and migration

```json
{
  "anchorSearch": {
    "version": 1,
    "anchors": ["b2", "b3", "b4", "b5"],
    "turnaround": {"degrees": 110, "spatialMargin": 0.3},
    "solveEveryDecisionLevels": 1,
    "feasibilityPolicy": "ipopt_oracle"
  }
}
```

Names/layout and interval 1 are engineering recommendations; the starting default is one solve per completed meaningful decision, always anchors/final. Required-anchor/final solves remain mandatory. Current no-inter-block-return scope is fixed for this release, with same-block hops allowed; do not add a revisit-enabling option now. Global hard latches are learned state, not caller-authored per-parent flags. No declared anchors means one coupled open task span; final physical B7 in new Real11 fixture uses the normal landing-goal path instead of terminal_goals virtual binding.

Keep physical/call/time/input/method limits; discovery/refinement allocations retain residuals. Preserve supplied legacy controls with source-specific semantics. Reject incompatible exact states explicitly. Do not edit archived fixture/evidence paths.

## 12. File-by-file implementation map

| File | Work |
| --- | --- |
| proposed cell_routes/anchor_policy.py | Canonical ordered milestones, cursor/span boundary and actual prior-contact audit; departed-owner filter preserving same-block jumps. |
| proposed cell_routes/anchor_guides.py or extend [`guides.py`](cell_routes/guides.py) | Finite endpoint/backward projections,110° cues/edge regions, next-point objective, deterministic annotation data. |
| proposed cell_routes/span_priority.py + [`solve_spec.py`](solve_spec.py) | Local counts, binary decision comparator/shared immutable key and pure exact-node identity. |
| [`event_adapter.py`](cell_routes/event_adapter.py), [`event_routes.py`](cell_routes/event_routes.py), [`event_search.py`](cell_routes/event_search.py) | Required-anchor stage views/open acyclic and same-block support choices, shared physical/guide references; retain finite region ORs/provenance. |
| [`depth_first.py`](cell_routes/depth_first.py) | Global monotone latch table/eligibility revision, anchor frames, full-finish backtrack, prefix statuses, targeted run recovery. |
| [`movement_frontier.py`](movement_frontier.py), [`movement_search.py`](movement_search.py), [`movement_generation.py`](movement_generation.py) | Normal node IDs/parent/outcome and completed-decision solve interval; regional key hooks/departed-owner eligibility, no revisit-family cursors. |
| [`candidate_comparison.py`](candidate_comparison.py), [`preparation_deferrals.py`](cell_routes/preparation_deferrals.py), [`minimum_runs.py`](cell_routes/minimum_runs.py) | One shared count/key/guard/bound predicate owner; frame-local peer storage; minimum_runs retains legacy matched-family normalization only. DFS owns scoped recovery bypass. |
| [`checkpoint_search.py`](cell_routes/checkpoint_search.py), [`search.py`](search.py), [`block_contacts.py`](block_contacts.py) | Explicit objective/stop/polish, exact-node endpoint assembly, cadence and rejection-scope handling, physical alternatives/native audit and repair. |
| [`interactions.py`](interactions.py), [`reachability.py`](reachability.py), [`prefix_viability.py`](prefix_viability.py) | Reuse cheap screens/envelopes and phase sweeps, no new physical equations. |
| [`repair_state.py`](repair_state.py), [`support_order.py`](cell_routes/support_order.py), [`comparison_goals.py`](cell_routes/comparison_goals.py) | Correct scope/policy compatibility and contact helpers; strict old filters must not ban same-block setup or acyclic optional supports; apply explicit departed-owner eligibility. |
| [`automatic_experiment.py`](cell_routes/automatic_experiment.py), fixture/plot/report drivers | New extended-B7 task, latches/regional membership/exact queried-node outcomes/cadence/call counters and source-specific diagrams. |
| [`ipopt/solver.py`](../ipopt/solver.py), [`continuation.py`](../ipopt/continuation.py), [`worker.py`](../ipopt/worker.py), [`model.py`](../ipopt/model.py) | Reuse existing callbacks/POSITION/seed path; tests through real worker. No new objective type. Expand runtime hashes/tests if actual changes become necessary. |

## 13. Persistence and ownership

Proposed versions: AnchorPlan/GuidePlan/TurnRegion/DecisionHeuristic/DecisionNode/NodeFeasibility/SupportVisitState/HardJumpLatch1; new automatic outer state1 and DFS8; changed movement generation context/cursor explicitly versioned; repair assembly9 if policy meaning changes. Physical generation state2/policy7 can remain if physical semantics unchanged. No constant-objective request schema change.

Persist global latches even after certifying subtree abandoned; include policy revision and apply eligibility on dequeue/promotion/reconstruction. Do not invalidate a valid physical prefix seed solely because another span latched, but recheck domain eligibility and exact state policy compatibility.

Store graph/guide/region tables once; parent IDs, semantic model keys, oracle statuses/scopes, ready/pending/deferred sources, departed-owner state/completed and last-solved depths, actual children/repair cursors and recovery actions survive resume. Descendants of an actually solved rejected node are skipped through parent refs; no unsolved ancestor acquires that status. Distinguish node alternatives and scope so failure does not spread to weaker/unrelated nodes.

Objective and node physics identity exclude changing repair status/yaw. Binary decision/key version stays immutable for queued records; later replay movement/label flips are diagnostic. Allocation-only slice changes retain work as today; scene/goal/anchor/threshold/input domain changes reject exact continuation. Learning a latch, publishing oracle status and publishing actual child/next-owner are atomic checkpoint transactions.

Old event schema7 states/virtual B7 fixtures are not exact new-policy continuations. Preserve them as controls at original source. A new extended solid needs rebuilt domain/task identity even if the block ID is still b7.

## 14. Settled policy and implementation defaults

Settled: DFS exact-node rejection, no ancestor inference, global hard latch, extended physical B7, onward guides/early stop, approach-facing110° turn cue, binary per-decision edge-plus-margin heuristic, deferred inter-block returns and required same-block setup jumps. Blended scores and uncertain membership are removed.

Start with one normal solve per completed meaningful movement decision, always anchors/final, using validated current position for binary classification. Initial spatial margin≈0.3 blocks; tune only through recorded settings. Later cadence2/4 and alternate margin representations are measured follow-ups, not new mechanisms to design now.

This preliminary heuristic requires full observability in section17: compact decision records, exact boundary distances, two labels/keys, run-deferral audit, replay label flips, aggregates and diagrams. There are no remaining mixed-rule or uncertain-class defaults to approve. Public names/versioning and logging storage are routine implementation details.

## 15. Implementation sequence

### M0 — Preserve baseline; define new finite task and defaults

Preserve `4cf39caa`/88 runtime hashes/all historical artifacts. New paths under experiments/anchor-feasibility. Write fresh extended-B7 fixture/task/guide settings, record settled binary approach-edge classifier, spatial margin and every-decision starting cadence explicitly. Matched measurements must use same new geometry/anchor/domain/guide/budgets. Old virtual16s is a separate baseline, not matched evidence for the new task.

### M1 — Normal solver policy and exact-node result contract

Separate objective/stop/polish using existing POSITION callbacks, retain original-state freedom and native audit. Add explicit completed-oracle rejection versus suspension/error and physical-branch scope. Preserve seed/no-worker fast path and final MAXZ.

**Exit:** first validated intermediate returns without polish; exact queried failed node/model rejects only its subtree/alternative; a failed child leaves unqueried ancestors unresolved; cancellation/unstarted/error work survives; feasible prefix seed doesn't freeze state or advance anchor.

### M2 — Anchors, finite B7 and regional guides

Required-anchor frames/actual prior-contact audit, acyclic optional supports and repeated current-owner jumps, local counts. New physical B7/contact goal/finite endpoint, backward projections,110° axis/diagonal edge and deterministic binary decision classifier with spatial margin. No whole-span blanket/hard guide rows.

**Exit:** guides/edge planes inspectable; finite B7 is actual physical landing; old virtual inequality not accidentally retained; diagonal/zero-leg/boundary equality and binary mode cases correct; no anchor skip/frozen seam.

### M3 — Shared priorities and global hard latch

Shared binary-selected ordinary/momentum comparator across generation/selection. Learn/latch one-jump anchor transition globally, preserve across changed parents/backtracking, recheck queued structures. Ordinary-decision-only recoverable run deferral, disabled for turnaround decisions. Full-finish T bound prunes/backtracks unchanged prefix; equal actual finishes still MAXZ.

**Exit:** latch learned in discarded subtree remains; new incoming histories retain source-local setup while never reopening multi-transfer/intermediate-support routes; permitted run choices/recovery remain; turn changes ordering but not global structure.

### M4 — DFS solve cadence, outcomes and recovery

Persist normal node/parent outcome and completed/last-solved decision depths. Implement chosen interval with mandatory anchor/final solves, no one-input hold/air solves or upward ancestor-testing pass. Reuse exact query results, keep physical alternatives distinct, restore deferred emitted/unmaterialized run work and walk actual recovery edges.

**Exit:** a queried child rejection leaves parent status/siblings intact; interval 1/2/4 call nodes match schedule; anchor/final forced queries/no duplicates; multiple same-block setup jumps generated; changed-prefix recovery not caught by old exact-node rejection; cancellation/resume no ownership loss.

### M5 — Reporting and explicit deferred scope

Implement section17 observability: binary-label decision events/positions/edge distances/keys, run-deferral audit and replay label-flip diagnostics; aggregate by turn/span and draw boundary/margin with two-label decision points. Also report exact query ID/depth/cadence, physical alternative, oracle rejection versus search exhaustion and current same-block/departed-owner scope. Link future revisit design in handoff; do not implement pair/revisit families or repeat cursors now.

Separate solver wrapper attempts, feasible seed returns and actual worker launches. Preserve current solverCalls budget meaning. No slack or blended-score milestone.

### M6 — Native/generic tests

Focused new anchor/guide/region/latch/node-cadence/same-block/recovery tests plus affected existing DFS/generator/repair suites. Re-run baseline 52-module/447-test scope plus new tests with exact exclusions/counts. Run relevant existing IPOPT seed/continuation/physical validation tests through real worker; full affected suite if IPOPT runtime changes. Independent native replay for measured accepted witnesses.

Generic scenes: direct gap, unique S→E→N ascent/turn, diagonal turn, small component sign flip, zero horizontal tangent, multiple local same-block hops, forbidden A→B→A domain case, failed child with viable sibling/untested parent, physical alternative OR-node, global latch across incoming histories, earlier run restoration and extended physical terminal.

### M7 — New-task cold measurement/handoff

Fresh source-stable extended-B7 runs with declared anchors, no archived/yaw-reference discovery seeds, unchanged physical/budget guards and final optimization. Compare implementations under matched new task. Report actual T/Z/Y/ground state, first anchor/full finish, elapsed wall, wrapper/worker costs and exact-node cadence, pruned descendants, deferred/restored runs, local setup jumps and pending work. No unmeasured carryover of old T67 success.

Hash all affected runtime; expand source_hashes to IPOPT if touched. Update current docs/runbook/testing/manifest/resume with implemented/deferred facts. Feature commits bundle related docs/tests/fixtures; no remote push requested.

## 16. Test matrix

| Area | Required cases |
| --- | --- |
| Global hard latch | Learned on one parent applies to another; survives certifier subtree abandonment/reload; single-jump structure retained during recovery; irrelevant queued other-span work survives. |
| Binary decisions | Requested impulse J/local partition; exactly ordinary T,−J,R or turnaround T,−J,A; equality inside; crossing action not split; owner mode inherited by pending construction; diagnostics never change key/physical rows. |
| Turn geometry |110° cue, axis/imaginary diagonal approach edge, tiny sign flip nonturn, zero-leg no registered cue, physical footprint vs center expansion,0.3 margin, relevant-neighborhood scope; finite input point yields one boolean. |
| DFS/node cadence | Full-finish bound/backtrack; same-input/equal-time terminal; only exact queried node gets infeasible status; child failure preserves ancestor/siblings; intervals1/2/4 on completed decisions; mandatory anchors/final; raw one-input holds do not trigger repeated NLPs. |
| Oracle lifecycle | Completed no-solution accepted operational rejection; unstarted/cancel/error distinct; fixed-model scope/cached status; native rejection repair; no pose freezing. |
| Current visit scope | Multiple same-block jumps/air returns allowed; optional acyclic contacts allowed; A→B→A rejected before generation/promotion; departed-owner state survives aliases/resume; required cursor not advanced/reset by setup hops. |
| Local setup allocation | Positive-duration horizon and saved source cursors preserve multiple same-block jumps; no collapsed jump history/frozen velocity; no active inter-block repetition-family implementation. |
| Recovery/resume | Emitted/unmaterialized deferred runs restored, earlier preparation change, latch restriction respected, actual owner per edge, no immediate re-deferral/duplicate oracle loops, atomic publications. |
| Finite B7 | New exact box/domain hash, ordinary landing goal, terminal guide midpoint/+Z edge, no old virtual rows, normal overlap bounds, actual physical/native contact/MAXZ. |
| Early stop | POSITION/onward direction kept; validated seed returns; first scalar/native distinction; final optimization; repair identity separated from semantic prefix status. |

Use exhaustible small trees to check retained/pruned/visited program unions and number of oracle calls, not only first result. Establish valid native controls before blaming search for toy geometry/headroom failures. Avoid fixed wall-time assertions.

## 17. Preliminary heuristic observability and evidence

The user explicitly requested observability because the edge-based rule is preliminary. Implement this alongside the classifier, not as a later optional cleanup. Every decision still has only `isTurnaround=True/False`; distance/diagnostic fields never create another heuristic class.

### Compact per-decision event

Record stable decision/node/parent ID, required-anchor span/cursor, completed decision depth, absolute input/state, classifier-policy version and service-event ID. Include:

| Field group | Required content |
| --- | --- |
| Point | Actual decision X/Z used for classification and its source (initial/current validated witness; any later batched reference explicitly named). |
| Geometry references | Applicable/matched turn anchor IDs, S/E/N guide IDs, physical footprint reference, edge normal/offset c and margin m with units. Geometry stored once; event references its ID. |
| Boundary measurements | Signed raw edge distance n·p−c and signed offset-boundary distance n·p−(c−m); equality-inside convention. No applicable boundary means ordinary with an explicit reason, not an uncertain class. |
| Mode and key | isTurnaround boolean, ordinary/momentum name, absolute T/local J/R/A counts and exact priority tuple actually used. |
| Pruning/service | Hard-latch eligibility, shared tick-deferral enabled/disabled, actual independent count protections and deferred/restored alternatives/reasons, selected decision/owner. |
| Result references | Exact-node feasibility/selection outcome, solver wrapper/worker/timing IDs, accepted continuation/final incumbent references. |

Later full-history continuous re-solves can move earlier decision positions. Log replayed X/Z and signed distance and whether the **same** edge rule would flip its old binary label. The assigned label/key remains stable for its existing work; any intentional reclassification gets a new explicit service event/reason. Diagnostic drift never becomes a hard position seam or an automatically inferred infeasibility.

### Aggregates and figures

Per span/turn report two label counts, selected alternatives by mode, run deferrals/restorations, replay label flips, signed-distance distribution, feasible/oracle-rejected decisions and solver/worker time. Record threshold/margin and relevant-context settings so a future tuning comparison is reproducible. No near-boundary category changes the policy; distances are measurements only.

Fresh diagrams show finite extended B7, actual physical contact geometry, backward guide points/objective arrows, original approach edge, margin-offset edge and shaded turnaround side. Show an imaginary diagonal edge explicitly. Decision points use two label colors; mark observed label flips separately. Optional time/depth timeline shows one binary mode per decision. Guide/edge shading is not collision terrain. Preserve all historical diagrams/results.

### Logging behavior and tests

Store geometry/guide tables once and emit compact referenced events when label/key/service/result is chosen; avoid per-input full trajectory/request duplication. Persist an event cursor across resume so events are not emitted twice. Logging/plot collection must leave mode, keys, selected hard constraints and solver-call allocations unchanged.

Test exact boundary, either side, axis/diagonal/reflection/translation, no registered turn, scoped old boundary, no third classifier result, whole crossing decision retaining its selected mode, and replayed location change causing a logged flip without changing physical rows. Test event/log replay after interruption and logging on/off parity. Assert turnaround peer deferral uses the shared more-J OR fewer-A protections; do not reinstate an ordinary-only exemption or raw-more-R protection.

### Source/validation evidence

Keep revision/runtime/test/fixture hashes, worker/compiler context, original state/task/extended geometry, global latch ledger, exact solved-node status/scope/cadence and saved source ownership. Distinguish domain/latch reject, elapsed bound, adopted oracle reject, recoverable heuristic defer and interrupted work. Measure first/full/session walls and independent replay levels. The user's reported similar objective speed remains supplied context, not a newly reproduced measurement.

## 18. Commands and resume

From the physical task repository root, reuse dependencies read-only:

```sh
export OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1
export PYTHONPATH="$PWD"
export PKC_IPOPT_PYTHON=/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes/.venv-guidance/bin/python
export PKC_RUN_TICKS_COMPILER=/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes/standalone/autonomy/build/compiler.json
```

The proposed suites were implemented primarily in `test_anchor_policy`,
`test_anchor_search`, `test_anchor_dfs`, `test_anchor_time_bound` and the existing
DFS/continuation/repair suites. Later shared-helper/proposal/flight tests and the
current600-test native gate are mapped in [testing](../docs/TESTING.md). Use its
current driver and fresh output paths; do not execute dated writers over evidence.
No mod/Java/game rebuild is needed for documentation or standalone Python changes.

There is no first implementation session remaining. Resume from the [current
handoff](../docs/ANCHOR_IMPLEMENTATION_RESUME.md); consult this original milestone
plan for rationale, and the execution reference for the actual current code paths.
Do not restart M1–M7, switch to the old private worktree, freeze numerical seams,
or revive separate ancestor probes/whole-span turn modes/virtual B7 semantics.

## Implementation ledger

M1–M5 is implemented through AnchorAdapter, anchor_policy/anchor_guides/node_feasibility/turn_observability and the existing DFS/frontier/solver paths. Internal queries occur on selected descent, not construction of an unselected sibling. Required physical arrivals bind their actual clock before comparing unused suffix lengths. Physical-task automatic state is schema2/DFS8/movement context4, with repair assembly9; missing declared anchors means one open span. Repair exhaustion is explicitly distinct from oracle rejection. Explicit initial/input/run/method caps remain effective.

M6–M7 native source-stable acceptance, independent replay and matched finite-task cold measurements are recorded in [new evidence](experiments/anchor-feasibility/README.md). Inter-block returns, cadence/margin tuning and intentional wall hits remain deferred. Before this feature, the accepted baseline used original B7/virtual task fixtures, existing guides/polishing, optional event comparison frames, global run exclusion and schema7 DFS. Those historical artifacts remain unchanged.
