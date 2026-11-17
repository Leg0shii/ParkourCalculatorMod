# Anchor search decisions: DFS, binary turns and observability

Current decisions through runtime `dd738c98` (2026-11-11). The
[execution reference](../ANCHOR_SEARCH_EXECUTION_REFERENCE.md) is the current
combination/pruning/IPOPT and implementation-owner table. This O1–O7 record
explains the choices; [current acceptance](../../research/hard-jump-comparisons/README.md)
records600 native tests,131 known-witness prefix checks and80 retained-witness
replays. The feature is complete in the session checkout; old private-worktree
paths and planning-only authorization notes have been removed from this entry.

Feasibility scheduling advice is in [O4: exact DFS node feasibility and cadence](#o4--exact-dfs-node-feasibility-and-solve-cadence). Current visit scope is in [O6](#o6--revisit-generation-deferred-same-block-jumps-required); the full future revisit design is preserved separately. Other sections record the newly settled decisions and their implementation implications. Suggested implementation details are labelled recommendations; the user's explicit policies are adopted as policies rather than presented as mathematical completeness claims.

## O1 — Hard-jump status is a solve-wide latch

**Corrected hard-jump contract (user clarification,2026-11-04):** a hard jump has one transfer jump between S and E. It can have any allowed number of setup jumps that land back on S before that transfer. This applies to every starting anchor, including the implicit opening support. The earlier implementation counted all span impulses and rejected same-source returns; that was an implementation/design-record error, not the user's intended restriction.

A validated one-transfer S→E witness latches the ordered pair globally for the solve, across changed incoming histories/backtracking. Preparatory S→S hops remain legal after the latch. Ordinary runs and setup jumps remain subject to the original domain/caps, and all requested grounded impulses still count in J for ordering. No initial-span exception is needed.

The transfer starts after the last completed source contact before the target arrival. Earlier impulses are preparation; the final transfer has exactly one requested grounded impulse. An unfinished prefix can be preparing or in flight without already committing to target versus source landing. A source contact in the target-arrival input belongs to that same physical transfer case rather than resetting preparation. Aliased patches use physical owners.

Examples after a latch:

```text
S → S → S → E    allowed: setup hops on S, one transfer to E
S → air → S → E allowed when the final S→E transfer jumps
S → C → E        outside the hard transfer domain: intermediate support C
S → S → E        excluded if the final transfer is only a run-off, without a jump
```

Initial-0 Real11 jumps at0 back onto B1@12 and at12 to B2@21. The first is preparation; the second is the hard transfer. A latch learned from jump8→B2@17 must preserve that initial-0 program. Preparatory hops on later anchors receive exactly the same treatment.

Global latches survive certifier abandonment. Current hard-arrival comparison is
frame-local and broader than the older matching latest-jump run family. A slower
compatible arrival stays first-class if it has **more J OR fewer R ordinarily /
fewer A in turnaround**. Otherwise it can be recoverably deferred against one
validated earlier peer. Earlier preparation histories/actual programs are not
merged. Recovery preserves parent/rows and uses a scoped bypass. Optional inter-block revisits remain deferred; same-source setup does not consume the required-anchor cursor.

**Declared assumption (2026-11-11):** hard transfers between anchors never involve
stepping; only ceilings can shorten the ordinary-flight bound. Completed stepped
transfers cannot learn/satisfy a latch. Closed or pending source-local preparation
is not blanket-rejected. Unknown shortening ceilings contribute zero to the bound.

Hard-jump policy version3 and shared comparison policy1 are part of the outer/DFS/stage/movement semantic context. Old exact checkpoints are rejected because the wrong policy may already have discarded setup choices. Latch/arrival records expose preparation impulses, transfer impulses and the source-contact boundary. No movement physics or continuous objective equation changes.

## O2 — Requested grounded impulses are the jump count

**Settled:** absolute T; J/R/A local to the current required-anchor span; J counts requested grounded jump impulses. The user clarified that named jump cancels retain the horizontal jump impulse while losing the vertical effect. They still count as J.

```python
J = count(grounded[t] and jump[t])
R = count(grounded[t] and not jump[t])
A = count(not grounded[t])
span_ticks = J + R + A
```

There is no separate effective-launch ranking metric and no need to reopen this question. Preserve the named case's existing compiled horizontal/vertical behavior and independent validation. An ordinary jump with false support assumptions remains invalid; the named cancellation is an explicitly modeled outcome rather than evidence of such a mismatch. [Existing counter](../../src/parkour_solver/search/identity.py), [named vertical validation](../../src/parkour_solver/physics/vertical.py), [case regressions](../../tests/unit/movement/test_stepping_cases.py).

Same-block hops do not reset the span start. Only a required-anchor advancement does. Whole inputs retain their existing phase/counting convention, including landing then horizontal run-off during that input.

## O3 — Finite extended B7, backward guides and onward objectives

### New Real11 task

**Settled:** extend B7 one block forward in +Z and use actual landing on B7 instead of the unbounded virtual +Z finish. Keep minimum landing time, then MAXZ at that landing time.

The stored box in [the accepted event fixture](../../research/event-route-discovery/real11-auto.json) is:

```text
old B7: [729, 0, -1095, 730, 7, -1094]
new B7: [729, 0, -1095, 730, 7, -1093]
         xmin ymin zmin xmax ymax zmax
```

The terminal guide point is the physical +Z edge, X midpoint:

```text
q_finish = (729.5, 7, -1093)
```

X midpoint is a neutral guide choice because the objective has no X preference. Actual finite support still imposes horizontal landing bounds. The old extra task inequality X<729.7 and the old virtual height-region goal are replaced by the finite B7 landing predicate in the **new** fixture; do not accidentally keep them as additional constraints.

Existing support semantics allow player-body overlap rather than requiring the center strictly inside the solid. Consequently the physical guide edge Z=−1093 is not necessarily the maximum legal player-center Z: the ordinary center bound extends by the player half-width, approximately 0.3, subject to clearance/exposed-region checks. Reuse [`route.center_bounds`](../../src/parkour_solver/scene/route.py) and existing finite contact regions. Do not add a new center cap at the guide point or remove normal overhang support merely to make the picture and optimum coincide.

This changes collision geometry and terminal validation: B7 is now a real extended solid with real contact. Use a fresh fixture/report path under the next experiment directory. Do not edit archived old fixtures/evidence or present their virtual T67 results as validation of the new finite task. The new physical fixture is implemented; archived original-box reports remain unchanged.

### Guide recursion and objective

**Settled:** infer guide points backward from this finite endpoint, projecting each successor guide point into the previous anchor's allowed landing cover. Reuse clamping/nearest-patch helpers; retain physical owner/height provenance, stable ties and original initial position. Do not average disconnected covers into blocked space.

At arrival E, optimize in the onward direction to the next guide point N:

```python
d = normalize_xz(q_N - q_E)
objective_E = maximize(d.x * X[T_E] + d.z * Z[T_E])
```

Reuse [`Checkpoint.objective`](../../src/parkour_solver/spatial/checkpoints.py) and existing POSITION/customYaw. Physical landing constraints remain independent. No waypoint equality, progress-based pruning, slack work or constant FEASIBILITY objective is introduced.

**Settled design evidence:** the user reports another agent measured approximately equal speed for this objective with early stop. Adopt that as the design input; this session did not reproduce or inspect the other agent's measurement. No further approval or constant-objective comparison is required to proceed with this direction. Implementation/regression/cold acceptance is recorded in the current evidence linked above.

Use stop-on-feasible and no intermediate polishing/equal-cost variant drain. Keep native scene/anchor acceptance, repair, feasible-yaw seeds and original-state re-solves. A warm seed may already be feasible and return without improvement; it remains a valid early-stop result. Final actual landing uses real MAXZ optimization.

## O4 — Exact DFS node feasibility and solve cadence

### Settled rejection rule

There is no separate ancestor-testing or prefix-check subsystem. We choose **when to run the normal continuous solver on the exact current DFS node**. That model contains the node's full fixed history from the original initial state through this node and nothing later. Earlier facings remain free. It contains all required past contacts/anchors and the selected physical alternatives, without adding a future end-anchor condition at this node's clock.

- If that exact node's designated solve is infeasible under the adopted IPOPT assumption, reject that node and its descendants and backtrack.
- If a descendant is infeasible, do not mark any untested ancestor infeasible. Return to its parent and try another child.
- A failed longer end-anchor program says nothing negative about a shorter ancestor unless the solver actually solved that shorter node's own model.
- If one physical alternative fails, only that alternative fails; unresolved sibling alternatives remain.
- If all represented children are exhausted, DFS may backtrack from the exhausted parent. That is search exhaustion, not a new continuous-infeasibility result for the parent.

Example: P has children P+A and P+B. Solving P+A and finding it infeasible rejects P+A. P and P+B remain eligible. Only solving P itself, with neither A nor B in the model, can give an infeasible result for P. Parent links store ordinary DFS ancestry; there is no attempt to infer a failed ancestor from the failed child.

A completed designated no-solution attempt is an operational rejection under the user's solver-accuracy assumption. Record the actual status/allocation. Cancellation, unstarted work, errors and incomplete model construction do not pretend to be a completed node solve. Cache results by exact node/model/scope so a checked node is not redundantly solved after resume. Do not put changing repair progress into its semantic physical identity.

### Implemented default: solve each completed movement decision

The default performs a guided feasibility solve after every **completed meaningful movement decision**, always at required anchors and the finish. This is a clear initial baseline: an impossible selected jump/contact is rejected before extending it, and rejection has an unambiguous exact-node owner. It also fits the existing stop-on-feasible/feasible-yaw warm-start machinery.

A decision level here means a completed jump/landing or selected physical contact/case choice. It does **not** mean a simulation input or a raw one-input run-hold construction node. Consecutive ordinary ground-run inputs are preparation of a run-length choice and are included in the next completed movement decision's model. Do not add an IPOPT call merely because the generator extended an ordinary run from R2 to R3. If a whole run-duration action is exposed as a meaningful decision in a different generator, count that action once, not once per input.

For example, run3→jump→land on the same block is one completed movement decision including its run preparation. A second setup jump is another level. Required anchor arrival is always solved even if it occurs before the next scheduled level. Existing [`append_choice`](../../src/parkour_solver/movement/program.py), [`normal_choices`](../../src/parkour_solver/movement/normal.py) and named-choice boundaries provide the underlying actual choices; distinguish raw construction steps from this solve cadence explicitly.

Each feasible node retains its selected physical/avoidance inequalities and a yaw seed; no fixed X/Z/velocity seam is created. `evaluate_node` calls `commit_arrival`, whose `retain_prefix` preserves those selected rows before the next movement extension. Later node/anchor solves still optimize all earlier facings jointly from the original state, within the retained constraints. This does not introduce optional-support **comparison goals**: actual time comparisons/promotion remain at required anchors/final task. An internal feasible node is not a new required anchor. The [avoidance follow-up plan](AVOIDANCE_PASSAGES.md) separately records the need to specify recoverable passage-alternative retention; continuous reoptimization alone does not reopen a selected discrete side.

### Alternative: every 2 or 4 decision levels

Make cadence a small deterministic interval, measured in those completed decision levels since the last actual solve. Implemented default interval is1;2/4 are configurable, unselected alternatives for future matched cadence measurements. An interval 2 or4 can reduce NLP launches when most choices are feasible and solving dominates runtime. Its cost is exploring more discrete work below an impossible, still-untested ancestor before reaching the next scheduled solve.

When a scheduled deeper node fails, reject only that solved node. Do not walk upward running extra ancestor tests or label its unsolved parent infeasible. Resume ordinary DFS alternatives there. Changing cadence changes when we spend solver work, not what a child rejection logically establishes.

Always force the normal solve at required anchors/final task regardless of cadence, and avoid a duplicate solve if it is already the scheduled node query with the same objective/policy/scope. The final task still needs actual MAXZ optimization; an intermediate early-stop result does not replace that final work. Existing cheap scalar/physical screens remain during generation independently of continuous solve cadence.

### How to choose after the baseline works

Compare intervals 1,2,4 and anchor-only as matched diagnostics using the same new finite task/domain/budget. Measure total wall time, solver calls/worker launches, time spent constructing subsequently rejected descendants, first full finish and final T/Z. Do not choose from number of solves alone. If interval 1 has cheap warm-start returns and prevents expensive dead work, keep it; if calls dominate and nearly all nodes are feasible, increase the interval. No adaptive controller or separate probing mechanism is needed initially.

Cadence1 is implemented and covered by current tests/cold runs. A matched cadence1/2/4 study has not selected a replacement; no claim that1 is fastest follows. There is no unconfirmed cadence question blocking implementation.

### Implemented full-finish pruning and lazy checks

One validated full incumbent bounds new/selected candidates. Compute optimistic
finish `T+max(H,V)` from phase-correct landing-state covers and applicable latched
ceiling-aware flight minima, subtracting already-paid ordinary flight inputs.
Exclude only if strictly later than the incumbent. Equality remains eligible,
including unfinished work; a separately justified positive remaining bound can
then exclude it. Guide distances are estimates, not these pruning enclosures.
This bound overrides local count protection. Incumbent updates do not rescan
queued bounds. A different earlier history is a different candidate.

## O5 — Preliminary binary turnaround heuristic and observability

**Settled latest correction:** each decision is either turnaround or ordinary. Classify its current decision point against the preprocessed approach-facing edge plus margin, then select the corresponding existing heuristic. Do not split a decision into input contributions, aggregate different mode costs or introduce a third membership class. This is a preliminary heuristic and must be observable/tunable.

### One approach edge, including an imaginary diagonal edge

Detect guide turns >110° as already agreed. For E between S/N, construct one horizontal normal pointing away from their approach side:

```python
u_S = normalize_xz(q_E - q_S)
u_N = normalize_xz(q_E - q_N)
n = normalize(u_S + u_N)  # approach bisector; away from S/N
c = min(dot(n, v) for v in physical_top_footprint(E))
m = 0.3  # initial spatial margin; configurable and logged
is_turnaround = dot(n, decision_point_xz) >= c - m
```

The line n·p=c is E's approach-facing support edge. If the approach is diagonal, it is the imaginary diagonal edge supporting the footprint on that side. Move it toward S/N by m. At an axis-aligned reversal with E starting at X=10, the boundary is X=9.7. For a diagonal normal `(1,1)/sqrt(2)` and footprint `[10,12]×[10,12]`, the boundary is X+Z=20−0.3sqrt(2). Use physical footprint geometry, without adding player half-width a second time. The margin is heuristic geometry, not a new collision clearance.

The one-edge/bisector construction replaces the earlier two-plane wedge. It gives a deterministic binary answer for every finite decision point; equality belongs to turnaround. A zero horizontal guide leg cannot define the110° cue, so no turn boundary is registered for that triple; the decision uses ordinary behavior unless another applicable boundary contains it. Do not add an undefined/possible membership label.

Use only the relevant route neighborhood `(S,E,N)` for a boundary; a past turn's unbounded half-space must not change heuristics in unrelated later spans. Multiple applicable boundaries still produce one boolean (inside any), with matched boundary IDs recorded.

### Decision location and heuristic selection

The initial implementation uses the actual current DFS decision location: original initial X/Z at the root, then the current node's validated witness position. That point is a reference for choosing a heuristic, not a fixed continuous endpoint. Children still re-solve all earlier facings from the original state.

Choose one mode for the decision and use it for ordering that decision's alternatives:

```python
if is_turnaround:
    key = (absolute_T, -span_J, span_A, stable_id)
else:
    key = (absolute_T, -span_J, span_R, stable_id)
```

All J/R/A counts remain local to the current required-anchor span. There is no R_outside+A_inside score. A jump/compound choice crossing the boundary is not partitioned; the next decision is classified again at its own current point. Pending symbolic construction work uses the owning decision's already selected mode rather than inventing an uncomputed landing location. Actual node solves/anchor comparisons retain their existing scopes.

The default solve cadence 1 supplies a validated node point before its next movement decision. If later benchmarking a larger cadence, explicitly define/log the deterministic seed/reference position used at an unsolved node; the classifier still accepts a point and returns one boolean. Do not substitute reachability-envelope membership or branch the heuristic into several possibilities.

In turnaround mode, use T/−J/A and protect more J OR fewer A. Outside, use T/−J/R and protect more J OR fewer R. Local hard-arrival deferral applies in either mode only without the corresponding independent protections; raw extra R/A alone is not protection. Global hard topology stays single-jump wherever already latched. Do not split one run hold into differently ranked pieces.

The initial margin is approximately0.3 blocks. The previously suggested1-input margin is an optional later tuning representation, not an automatic temporal dilation or an extra membership state in this initial edge-distance classifier. Any later change must remain a documented binary rule.

### Required observability for this preliminary rule

Persist/log a compact record whenever a decision's mode is selected:

| Field | Purpose |
| --- | --- |
| Decision/node/parent IDs, anchor span, decision depth and absolute input/state | Identify the exact choice and its owner. |
| Decision X/Z and position source | Reproduce which point was classified; distinguish original state, validated witness and any later batching reference. |
| Boundary/guide IDs, source S/E/N, edge normal/c, margin/value units and policy version | Reconstruct the axis/imaginary-diagonal edge without copying geometry into every row. |
| Signed edge distance n·p−c and signed margin-boundary distance n·p−(c−m) | Explain how close the decision is to changing its binary label. |
| isTurnaround, selected heuristic and actual priority tuple with T/J/R/A | Verify the label selects precisely one agreed rule. |
| Hard-latch state, effective run deferral, actual deferred/restored alternatives and reason | Verify turnaround preparation is not suppressed accidentally. |
| Feasibility/selection outcome and solver/worker timing references | Relate heuristic choices to search behavior and cost. |

Later full-history solves can move the earlier decision location. Record its replayed position/distance and whether the same boundary rule would flip the label. This is a diagnostic boolean, not a third mode or an extra hard constraint. Keep keys stable for already-owned work; a deliberately reclassified decision is a versioned service event with an explicit reason, not silent in-place mutation.

Add aggregate per-span/per-turn counts for the two labels, mode selections, run deferrals/restorations and later label flips. Include a distance histogram and solver calls/time/accepted continuations by mode. Near-boundary distances are diagnostic measurements only; do not create an uncertain category or blend scores.

New diagrams show the physical edge, margin-offset edge, shaded turnaround side, guide points and decision points colored by their two labels. Show the imaginary diagonal boundary directly. A decision timeline can show its binary label versus input/depth. Mark replayed label flips as diagnostic changes. Historical evidence/diagrams remain untouched.

Store boundary geometry once and reference its ID in compact events. Resume needs a stable logging cursor so the same event is not emitted twice; diagnostics must not change ordering, constraints or consume numerical-solve budget. Avoid copying full trajectories/NLP requests into every mode event. Test that logging on/off leaves selected keys and physical rows identical.

## O6 — Revisit generation deferred; same-block jumps required

**Settled current scope:** do not generate inter-block revisits now. Preserve their design for later in [REVISIT_DISCOVERY_DEFERRED_PLAN.md](REVISIT_DISCOVERY_DEFERRED_PLAN.md). That document records complexity, lazy repetition-family generation, future milestones/tests and resumption requirements; it is not current implementation work.

Multiple jumps on the current block remain supported:

```text
A → A → A → B       allowed: setup jumps on A, then forward
A → air → A → B    allowed: local hop, no other support visited
A → C → B          allowed if C is a permitted new optional support
A → C → A → B      not generated now: inter-block return
A → B → C → B      not generated now: inter-block return
```

Use stable physical support-owner provenance. Ignore transient air; compress consecutive same-owner occurrences only for eligibility. Keep every jump event, input count, physical patch/case and actual program in the solver model. Moving among decomposed patches belonging to the same block is not automatically a different block visit.

Track departed owners: the current owner remains eligible for repeated local jumps; after another owner is visited, the departed owner is ineligible. Apply this before expensive contact generation and audit emitted programs as well. Initialize from the original support and reconstruct the same state on resume; supplied roots violating this selected domain must be identified explicitly. Do not use a visited set that rejects the second landing on the **current** owner.

A required-anchor cursor advances only on its next actual required contact, never from a same-block setup hop. Optional supports are not globally disabled. Required order remains enforced; do not enter a later required anchor prematurely if that would require a currently unsupported return to fulfill the required sequence.

Global hard latches intentionally restrict the transfer to one jump. Same-block setup preparation remains available on the departure anchor in both latched and unlatched spans, including recovery. Do not accidentally remove all same-block hops from the generator while enforcing no inter-block returns.

Current tests must assert both sides: several legal same-block jumps are generated/validated, while A→C→A is excluded as deferred feature scope. Logical DFS backtracking is allowed and is not a physical revisit. No revisit-pair source/family cursor is implemented now; positive-duration horizon and ordinary source limits still bound local chains.

## O7 — Turnaround heuristic means momentum ordering

**Settled:** one turnaround decision uses `(T,-J,A,id)`. At equal T/J, fewer A
favors R. For a *slower* candidate the confirmed protection is **more J OR fewer A**,
not raw more R. Extra run time with unchanged J/A can therefore defer against an
earlier compatible hard peer. Ordinary mode analogously protects more J OR fewer R.
Equal T remains live. This replaces the earlier ordinary-only run-deferral exemption.

Global hard topology and regional mode are independent: a latched span still has
one actual transfer after allowed source setup hops. Restore an unprotected slower
candidate only on explicit failed-continuation recovery, with scoped bypass and
all bounds/domain limits still enforced. No O7 design question remains.

## Settled versus remaining implementation choices

Settled: DFS; solve-wide single-jump hard latch; requested grounded impulses; extended finite B7/onward guides/early stop;110° turn with approach-facing boundary and regional margin; no separate ancestor-testing subsystem; a failed descendant cannot invalidate an untested ancestor; inter-block revisit generation deferred; multiple setup jumps on the current block preserved even after a hard-transfer latch.

Implemented default: normal feasibility after each completed movement decision, with anchors/final mandatory; benchmark 2/4 later if solving dominates. Turn mode is a preliminary binary edge-plus-margin decision using its current point, with compact diagnostics, plots and outcome counters. The blended regional-score and uncertain-membership proposals are removed; no new turn-policy question needs reopening.

The [deferred revisit plan](REVISIT_DISCOVERY_DEFERRED_PLAN.md) preserves future cyclic-route work in full. The current feature is implemented/validated; cadence comparison, revisit generation and turn tuning are future work, not pending steps from this plan.
