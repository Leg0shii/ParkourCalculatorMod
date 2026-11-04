# Consolidated plan: depth-first decisions and shared interaction eligibility

> **Current extension (2026-11-04):** [anchor plan](ANCHOR_FEASIBILITY_IMPLEMENTATION_PLAN.md) M1–M5 is implemented in the isolated worktree; [new evidence](experiments/anchor-feasibility/README.md) records validation. Physical tasks default to required-anchor/open-span DFS with schema8; virtual/supplied source controls remain separate. Binary turnaround/observability, exact-node solves, global hard latches, run recovery and same-block setup are active. Inter-block revisits remain [deferred](REVISIT_DISCOVERY_DEFERRED_PLAN.md). [Resume](../docs/ANCHOR_IMPLEMENTATION_RESUME.md).

> Current automatic implementation: [GENERIC_ROUTE_DISCOVERY_IMPLEMENTATION_PLAN.md](GENERIC_ROUTE_DISCOVERY_IMPLEMENTATION_PLAN.md) controls the implemented event-language/stage-provider refactor at `08b00185`. Its dynamic `GoalRequest` stages use the existing DFS with schema7, event-stage policy3, separate score contexts, pending-child owners and retained siblings/unknowns. Constraint generation is state2/policy7 and repair assembly8.447 selected native checks pass with stable source hashes; automatic stored-Real11 cold search finds T67 at16.09s and all24 independent post-run replays pass. See [current evidence](experiments/event-route-discovery/README.md) for the matched-control status. The remainder records the source-specific `a28e5b71` baseline and its original design history.

## Anchor DFS extension obligations (implemented; acceptance below)

The [new file map](ANCHOR_FEASIBILITY_IMPLEMENTATION_PLAN.md#12-file-by-file-implementation-map) extends this engine:

- Coupled required-anchor frames, local requested J/R/A, original-state prior physical history with earlier facings free.
- Normal exact-node feasibility at selected decision cadence; only a query through that node and no later choices can mark it infeasible. Failed descendants never invalidate unqueried ancestors. No separate ancestor-testing pass.
- Recommended interval1 per completed jump/contact decision, not per input/raw one-input hold; anchor/final solves mandatory, later2/4 diagnostics. Persist actual query depth/outcome and avoid duplicate exact queries.
- Backward/onward POSITION/early-stop intermediate work, finite extended physical B7/final MAXZ, no slack/new constant objective.
- Global monotone hard-jump latch and preliminary110° approach-edge classifier: current decision point vs one edge+margin gives one boolean and selects the whole ordinary/momentum key. Diagonal uses an imaginary diagonal edge. Record geometry/distances/label/key/pruning/replayed flips and two-label diagrams; no blended score or uncertain mode.
- Active departed-owner filter excludes A→C→A but permits A→A→A→B and optional acyclic supports. Preserve all local jump inputs/physical cases. Deferred [revisit design](REVISIT_DISCOVERY_DEFERRED_PLAN.md) is not an active milestone.
- Actual one-edge earlier preparation/run recovery with scoped bypass, hard-latch restriction and atomic source/child publication.

The implemented sections below describe the accepted baseline, not these planned changes.

## Implemented automatic integration contract

- Use semantic event/goal cursors and immutable stage views; a frame's index is decision depth rather than a lookup into a fixed future support list.
- One compatible actual prefix/next-goal owner can service latent future guide aliases without copying solved sibling decisions.
- Different objectives get separate score-record identities and repair state. Each physical repair queue keeps a fixed comparison objective.
- Remove all automatic raw-cell filters with `RouteGuards`; keep physical collision/contact conditions, cap witnesses, owned prerequisites and full-scene validation.
- Version automatic states/schema and changed shared-generator policies explicitly. Preserve legacy supplied controls and source-era evidence.

2026-11-03. **Implemented baseline and typed spatial integration, with bounded numerical evidence.** Core DFS/early-interaction work landed through `498c054c`, request logging at `072a1111`, virtual finish at `71c9bb7e`, and latest-jump run pruning at `db211b2b` with generic regressions through `c1e71cb3`. The [corridor-agent handoff](../docs/CORRIDOR_AGENT_HANDOFF.md) was imported through `61355976`, merged as `984b701b` in isolated branch `research/real11-initial-run-ticks`. Do not rebuild these completed workstreams or describe them as unimplemented.

The [spatial corridor refactor plan](SPATIAL_CORRIDOR_REFACTOR_PLAN.md) records the implemented region/route/stage contract and remaining acceptance scope. This document records the implemented search invariants and the exact integration obligations. Earlier pre-handoff drafts are superseded; the original source-era plans, experiments and corridor investigation ledgers preserve their evidence. The implementation-status sections distinguish current runtime from the original integration checklist.

After compaction, use the [implementation resume handoff](../docs/CORRIDOR_IMPLEMENTATION_RESUME.md) and the generic plan’s implementation ledger. The older spatial-plan sections10–11 remain historical context. They enumerate the existing DFS `goal_list`/frame/terminal call sites, stage-scoped adapter requirements, virtual-finish outstanding-contact trap and first feature slice. Do not start a second DFS or re-run old investigations as the first implementation action.

## Spatial implementation update (2026-11-03)

The [spatial refactor plan](SPATIAL_CORRIDOR_REFACTOR_PLAN.md#implementation-status) now has the support/air graph, untimed route queue, 3D progress and fixed guard integration, plus stage-derived goals/directions in this existing DFS. Typed routes use schema6; legacy supplied controls retain schema5. `SemanticCorridorAdapter.goals/for_goal` provides each frame an immutable support-stage prefix. `RouteGuards` supplies fixed disjunctive axis-sweep rows through `ConstraintVariants`; physical events, owned row retention, full-history facings, accepted variants, minimum-run tables and suspended work retain their existing owners.

The automatic path requires no authored support order or comparison clocks. Comparisons are selected support visits and the true terminal predicate; intervening portals are untimed membership/progress events. Optional explicit zero-width portal comparisons are not required by this path and have no epsilon-box implementation. The outgoing objective derives from the selected stage's guide segments using existing helpers. A free virtual finish has no fallback physical-B7 requirement.

Normal support opportunities are conditioned on the selected stage before normal landing rows are built; the imported interaction gate still screens normal/step/ceiling physics. Named internal probes retain unknown route status when their geometry is absent from shared diagnostics. No global continuation-time bound or sibling decision transfer was added. The source-stable matched300-second Real11 run found no automatic finish in13 calls (5 unique partial witnesses replayed); supplied routing found T67 in41 calls (51 replay checks). Coverage of the archived64-input itinerary passes separately. The final native gate passes420 selected tests with no skips/failures and sourceUnchanged=true; dedicated ladder modules remain outside its scope. See the [evidence and limitations](experiments/automatic-corridors/README.md); these outcomes do not prove infeasibility or optimality.

Movement-generation context3 hashes physical scene fields and normalized domain settings without allocation-size duplication. Both DFS and outer queue include `movementGenerationPolicy=3` and reject older continuations explicitly. Allocation changes preserve all pending candidates; changed input horizons still reject.

### Contact-phase and persistent promotion boundary

Typed physical goals record a required occurrence plus optional outgoing air/portal occurrences, so post-landing X/Z can leave the finite support within the same input. A physical terminal route may end its requirement at contact; physical replay still checks the whole input. Virtual finish remains a full endpoint predicate.

Typed `choose` and child ownership form one published transaction; callbacks cannot save a tried arrival without its child. An expired allocation can finish this pure promotion before the next numerical dispatch check. The outer queue continues actual newly committed children and yields stalled work. Typed generation scores the first ready proposal and retains the rest in the existing iterator; it does not introduce another buffer/window drain. These policies are fingerprinted and legacy controls are unchanged. Focused regressions reproduce the previous delayed promotion, catalogue drain, contact-prefix rejection and half-published choice.

## 1. Implemented search behavior and current task

`checkpoint_search.find_checkpoint_route` prepares geometry/scope and delegates to `depth_first.search_goals`. Compare current alternatives at the same spatial goal, continue the selected validated child deeply, retain siblings/unknowns/refinements and backtrack. Absolute clock windows are optional observations, not a barrier. Every continuous solve starts from the original position and stored velocity, with all earlier facings free.

Current Real11 finish is the **descending height7 clock with player-center X<729.7 and Z>=−1095.3**, minimizing the achieved tick T first and maximizing Z[T] second. Use `terminal_goals` and the canonical [virtual-finish fixture](experiments/virtual-finish/fixture.json). The target has no new lower X or upper Z bound and is not an artificial collision floor. A free overshoot can end below height7 and airborne. A physical finite B7 contact is a separate valid final model with actual collision constraints. The 200-input horizon is a guard, not a fixed finish64.

The current fixture still supplies B2→B3→B4→B5 and initial0/8 as explicit controls. The automatic spatial path now discovers untimed support/contact and 3D passage itineraries, rather than demand that intermediate order from input. Once a route selects a support visit, its inner search must realize it. Overflight and landing/run-off are different spatial routes even if X/Z projections match; local timing and facings remain variable within the selected itinerary. Finite-support terminal tasks remain supported as separate generic cases.

## 2. Compare candidates at the same point

Current arrival ordering is `(validated absolute arrival, -effective grounded jumps, grounded nonjump runs, stable identity)`. The shorter simulated-prefix processing key organizes local generation; it is not a completion-time comparison. Observed feasible arrival is not a proof of earliest arrival or a lower bound on unseen alternatives.

The historical Real110/8 control measured B2 arrivals21/17 for P01/P02. These remain regression evidence for ordering, not constants to put in generic code or proof that P01 loses at the terminal objective. Current DFS retains alternatives and follows the actual validated arrival program. Optional repair/refinement work cannot recreate a global window drain.

Keep the agreed0.25-block/1-tick margins and same-witness position/velocity/schedule metadata where used. The former `time_progress` peer-protection/retention helpers remain available, but **the implemented DFS does not run that global peer-pruning pass**. It retains siblings except for its explicit latest-jump run heuristic and existing necessary exclusions. The spatial refactor must not silently reactivate historical exclusions.

### 2.1 Integration plan: infer support order from generated decisions

The remaining work is spatial route discovery, not a second movement generator. Build typed support-surface/air regions from the existing decomposition and finite contact patches, with movement-aware possible transfers from current builders and `InteractionGate`. The resulting route stages supply the required contact itinerary. The current supplied `SupportOrder` is retained only for source-specific controls and historical reproduction.

Extend the existing DFS frame's stage/goal source instead of creating another DFS. The present `goal_list`/index mapping must consume the selected route's next support/passage stage. Keep original-state generation, setup self-hops, unknown destinations, owned constraints, accepted variants and suspended generator work. An omitted support/passage belongs to a different route; a newly found alternate route is queued without transferring another branch's decisions as a seed.

### 2.2 Integration plan: select a common comparison goal

Reuse `comparison_goals.goal_spec`, landing/region validation, `probe_spatial`, `goal_key` and the existing virtual-goal adapter. A support stage supplies a finite landing goal, an open passage supplies its actual portal/region predicate, and the terminal stage supplies the task's virtual or finite-support goal. Add an explicit zero-thickness portal goal when necessary; do not fake one by adding arbitrary epsilon-width walls.

All sibling timing comparisons share goal/context identity. Different spatial routes can have different intermediate goals and cannot be ranked by unrelated local arrival times. Retain unknown/incomparable alternatives and compare completed routes by the actual terminal objective. Region probes evaluate arrivals in already generated programs; they do not automatically discover a future continuation. Adopt the returned accepted program, not merely its score or facing guess.

### 2.3 Integration plan: select progress direction from ordered route stage

Reuse `horizontal_segments` and `checkpoint`, with a common outgoing segment selected from the route-stage cursor before scoring siblings. Replace the old Real11 start→B5/+Z driver rule. Do not infer progress by a global nearest-point projection that can jump ahead at revisits/self-intersections.

Use the same new 3D route-progress predicates for prefix eligibility, goal achievement and final stage auditing. `plan_windows` already derives a clock from supplied real decision ranges/readiness/dependencies; the primary spatial-goal DFS does not need authored10/28/44/55 clocks. `score_features.quietFromState` remains diagnostic, not a clock selector. Stage metadata never freezes optimized position, velocity or facings.

### 2.4 Implementation order and focused acceptance

Follow the spatial plan's milestones: semantic regions; typed route identity/enumeration; shared phase/contact progress; fixed guard integration; stage-derived goals/directions in the **existing** DFS; then matched-budget cold/coverage audits. Pair every feature with meaningful generic tests, source-specific fixtures, updated diagnostics and docs. Commit at feature implementation boundaries, not for this planning turn.

Keep the imported execution-owner and persistence rules throughout integration. A new outer route queue cannot require every route's optional refinement to finish before a promising child descends. New stage rows must retain owner completion states and exact request/scope/validation identities.

### 2.5 Cell decomposition and anchor investigation: focused improvement proposal

The pre-handoff geometry diagnostics were performed at `93652150`, not the new solver baseline. [Corridor comparison](experiments/linkcraft11-opening/audit/corridor-comparison-2026-11-03.json), [default shortlist](experiments/linkcraft11-opening/audit/automatic-route-defaults-2026-11-03.json) and [decomposition investigation](experiments/linkcraft11-opening/audit/decomposition-investigation-2026-11-03.json) remain unchanged evidence.

They show existing exposed top patches, reflected rather than fixed negative-Z selection, scene-wide portal midpoint sensitivity, and a geometry-only33→47 support-aligned refinement preserving free volume. The conditioned B2→B3 anchor fits existing cells. Those are diagnostic findings, not a completed route refactor, executable flight or replacement physics. The new spatial contract also requires explicit support-contact stages so overflight cannot satisfy a landing route.

Current virtual-goal execution expands the finite collision-query envelope from the horizon; its edges/midpoint must not become goal bounds, mandatory spatial walls or local guide anchors. Keep full-map geometry coverage claims distinct from stored-geometry regression work. The user's reported B6 rightward extent still lacks exact revised bounds.

## 3. Promote actual decision programs and preserve ownership

`depth_first.commit_arrival` promotes actual validated decisions and selected hard constraints, preserving only optional compatible facing initialization. It removes rows owned by removed, uncommitted future events; `truncate_program`/`constraintOwners` and full-proposal screening must keep that invariant. Do not copy comparison-only rows into permanent task requirements, pin intermediate states or recreate orphan approach conditions when adding stages.

Each numerical alternative contains fixed inputs, actual selected interactions and all claimed in-scope constraints before IPOPT. Reuse the imported effective-model deduplication, constraint builders and exact solve identities. Final physical acceptance remains independent of route membership.

## 4. Persistent DFS and implemented route-stage extension

Supplied legacy routes use schema5 and typed route stages use schema6, both `kind=ordered_goal_dfs`. Frames retain parent/goal identity, ready/scored candidates, generator cursor, numerical in-flight state, siblings, accepted variants, refinements and unknown/suspended work. Each unfinished frame has exactly one execution owner. The incumbent survives deadline and resume.

Extend those frames with semantic route key, stage occurrence/variant cursor and task goal identity. Preserve existing child selection, descent and backtracking. Version/context changes must be explicit; pre-local-run schema5 states already differ by policy fingerprint and cannot silently resume as equivalent. Persist transitions before dispatch and after results/backtracking. Do not deserialize the old checkpoint queue or a legacy horizontal route signature as exact new-stage continuation.

## 5. Existing interaction gate and conservative probe semantics

Use `interactions.InteractionGate.context/query/required` and shared `interaction_reachability` in the original-state compiled context. Proven impossible required interactions reject proposals; a block proved unable to interact contributes no prevention choices or interaction-derived checks. Unknown preserves coverage. Recompute/cache against actual discrete context, scope and model identity.

Preserve the matching eligible gate subset in pre-solve and terminal stage checks (`3ebd9407`). Adding typed regions must not resurrect an excluded gate through a stage predicate or projected guard.

Preserve the step/grind correction (`498c054c`): realized scalar endpoints omit named collision probes. Keep Y unknown and intended unclipped X/Z coverage on those phases, then rely on existing case/scene validation. Do not infer noninteraction or route impossibility solely from realized feet height. Full proposal checks remain before projection/truncation.

## 6. Existing effective-model dedup and exact diagnostics

Per-obstacle/partial effective models deduplicate before expensive combination/refinement. Keep physical scope, owned prerequisites, prior decisions and remaining resource distinctions in identity where relevant. Different input times or descriptions do not necessarily make different models; identical input schedules do not prove identical models either.

Preserve exact fixed numerical requests/problem hashes, solve/scope IDs, selected branch rows and validation results. The handoff explicitly requires current corridor-related calls to remain visible until actual corridor changes explain any savings. Combined-ledger8/9 differ by free/physical terminal model; initial0-ledger5→6 and7→8 include two models with corridor repairs. Do not special-case or suppress those call IDs.

The B3 audit is a mandatory integration regression: crossing its X=738.5 midpoint outside the old Z strip can be physically valid before the later landing. Replace that overly strong route predicate through general 3D stage semantics; keep the before/after model and physical/corridor outcomes. Do not merely delete the checker or misclassify a corridor rejection as numerical failure.

## 7. Implemented latest-jump run heuristic; time reasoning deferred

`minimum_runs.latest_jump` measures runs immediately before the latest effective grounded jump: `jumpInput-groundState`. It normalizes ordinary repeated same-support preparation only, keeping earlier decisions, support, in-air schedule, contacts/cases, hard rows and terminal contact pattern. Per-frame `minimumRuns` records the least validated feasible count; larger copies of the same pattern are heuristic exclusions.

Equal-run choices, earlier smaller/unknown candidates, interacting preparation, sneak/ladder cases and distinct patterns remain eligible. A prefix with15 total runs can have only3 local runs before its last jump. Generic regressions cover minima0/1/2/5/9 and dynamic tightening; those are tests, not fixed thresholds.

New route rows must have correct ownership and scope so they neither merge distinct semantic/contact patterns nor accidentally turn a known local run comparison into total-run pruning. Preserve `minimumRunExclusions`, representative IDs and the fresh `minimum_run_pruning=False` control; policy changes alter resume context. Keep the existing elapsed-input cutoff. **Additional continuation-time bounds/pruning remain deferred**, as do discrete decision transfer, inertia-threshold work, water, and intentional wall-hit/ladder expansion.

## 8. Implementation status and reuse map

| Workstream | Status / existing location | Spatial refactor action |
| --- | --- | --- |
| Goal-aware traversal and promotion | Implemented: `cell_routes/depth_first.py`, `checkpoint_search.py` | Derive frame goals/cursors from route stages; preserve DFS lifecycle |
| Shared early eligibility | Implemented: `interactions.py`, open movement/step/ceiling callers, `reachability.py` | Reuse the gate for region/passage proposals and scoped checks; retain probe uncertainty |
| Effective-model dedup and owned projection | Implemented: `constraint_variants.py`, `block_contacts.py`, `movement_composition.py`, `movement_frontier.py` | Add correctly owned spatial guard alternatives and exact route context |
| Exact numerical/validation evidence | Implemented: `block_contacts.py`, `search.py`, diagnostics | Preserve all models/costs and add semantic-stage provenance |
| Virtual finish | Implemented: `terminal_goals.py`, comparison/generation/runner integration | Represent terminal predicate without fake terrain; preserve free/physical alternatives |
| Latest-jump local runs | Implemented: `minimum_runs.py`, DFS wiring | Preserve policy/counts/representatives and pattern distinctions |
| Typed support/air regions, 3D corridor stages and automatic support order | Pending: [spatial refactor plan](SPATIAL_CORRIDOR_REFACTOR_PLAN.md) | Implement semantic regions, route discovery/progress and fixed stage guards |
| Stage-derived goals/anchors/directions | Pending coordination of existing helper APIs | Connect route stages to existing probes/checkpoints; no second solver/DFS |

## 9. Acceptance and integration evidence

Existing core evidence: [DFS results](experiments/dfs-interactions/README.md), [virtual-finish results](experiments/virtual-finish/README.md), [latest-jump runs](experiments/last-jump-runs/README.md) and the [handoff](../docs/CORRIDOR_AGENT_HANDOFF.md). Older208-call/fixed64 reports remain historical; no global optimality or fully green ladder-inclusive claim follows from focused checks.

Fresh post-merge acceptance at `984b701b`: **52 tests passed, no skips**, native compiler/IPOPT enabled, about17 seconds. Modules: `test_minimum_runs`, `test_depth_first`, `test_orchestrator_continuation`, `test_fixed_clock_goal`, `test_terminal_goals`. [Log](experiments/linkcraft11-opening/audit/handoff-integration-focused-2026-11-03.log), [settings/summary](experiments/linkcraft11-opening/audit/handoff-integration-focused-2026-11-03.json). Existing helpers were used read-only; no shared Java rebuild or cold retry was started.

Future spatial acceptance must retain these controls and add semantic contact/overflight and 3D passage separation, union guards, multiple portal crossings per input, mandatory stage preservation, scoped future omission, transformed geometry, automatic historical-witness representation, route-stage resume parity and unchanged local-run/terminal model distinctions. Compare current corridor repairs by exact models before/after the refactor. Keep all unknown/capped work visible and write new evidence paths. Numerical feasibility, route acceptance, independent scene replay and executable/game replay remain separate claims.
