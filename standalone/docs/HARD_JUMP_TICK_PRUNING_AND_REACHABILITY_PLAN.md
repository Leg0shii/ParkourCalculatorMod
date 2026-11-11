# Hard-jump comparisons, proposal reachability and transfer-time bounds

2026-11-11. **Implemented; final measured acceptance below.** Pre-edit runtime:
`5d54f02b`. Work in the session's physical checkout and preserve unrelated edits
to AGENTS.md and CODE_MAP.md. This is the single controlling follow-up plan for
these changes. Its sections describe one implementation with the confirmed
count protections below. Matched pre-edit combined/initial0 baselines were captured
before production edits. Implementation and validation status are tracked
in this document and the new hard-jump-comparisons experiment directory.

Evidence: the [candidate audit](../autonomy/experiments/anchor-candidate-audit/README.md)
reconstructs the last full integrated 300-second run; the
[read-only controls](../autonomy/experiments/anchor-candidate-audit/hard-jump-plan-2026-11-11.json)
check current generators, hard-transfer structure, ordinary clocks and geometry.
The source-era [integration contract](ANCHOR_SEARCH_INTEGRATION.md) describes the
shipped baseline, including lazy incumbent checks and explicit recovery.

## 1. Required behavior

- A validated one-transfer S→E jump latches that topology globally. S is the
  implicit initial support or an explicit anchor. Setup hops returning to S are
  legal before the transfer. Keep them eligible after latching and in recovery.
  **Declared assumption:** the actual hard transfer between anchors never uses
  stepping. It can use ordinary flight or ceiling interactions. Enforce that
  domain at latch learning/eligibility; source-local preparation is not banned.
- Use absolute T and span-local requested grounded J/R/A. Ordinary ordering is
  `(T,-J,R,id)`; turnaround ordering is `(T,-J,A,id)`. One frozen binary mode
  belongs to each decision; do not blend modes or add an uncertainty class.
- Before deferring a slower hard-span candidate against a faster compatible
  peer, preserve it if it has **more J OR fewer R normally / fewer A in a
  turnaround**. The guards are independent OR tests.
  Protected alternatives stay first-class after a peer finds a full finish.
- Defer only an unprotected, strictly slower, comparable alternative. Save its
  exact work for explicit feasibility recovery. Equal-T alternatives stay live.
  Counts do not establish physical equivalence or global optimality.
- A certified optimistic full finish strictly later than the best validated full
  finish is an exclusion, regardless of local count protections. Equality stays
  eligible for a better terminal objective. Keep one global incumbent and check
  only new or selected work; never rescan the candidate queue on its update.
- One shared comparison helper owns tick arithmetic, count protections and the
  peer-based verdict for every caller of this kind of policy. Callers normalize
  data and execute the verdict; they do not reimplement it.
- One shared reachability service conditions whole proposed sequences before
  side/corner fan-out and reuses identical fixed-model preparation downstream.
- Add conservative remaining hard-transfer durations to the finish bound. Keep
  physical eligibility, backward/onward guide objectives, early feasible stopping,
  original-state free-facing solves and final minT→MAXZ behavior unchanged.
- Continue excluding inter-block revisits. Generate multiple jumps on the current
  block. No new intentional wall-hit, ladder or numerical physics work is included.

### 1.1 Confirmed count rule

The user selected the existing fewer-cost protection: more J OR fewer R normally,
more J OR fewer A in a turnaround. The shared label stores `tickCost = R` or `A`
respectively. This one rule governs both ordering and recoverable peer deferral;
raw additional air/run time is not itself a retention advantage.

### 1.2 Caveats controlling implementation and measurements

- Protected does not mean promptly serviced: strict DFS can still spend its wall
  budget in the initial8 subtree. This work changes no root scheduling/fairness
  policy and cannot promise combined cold T64 discovery.
- More-J setup hops remain protected unless a certified full-finish bound removes
  them. The main expected reduction in their worker calls comes from section 6,
  not from blanket suppression of same-block preparations.
- Ordinary contact overlap can be checked before the last input's horizontal
  movement. A landing cover alone is not a proved enclosure of the resulting
  launch state. Section 6 requires phase-correct enclosures for both H and V.
- Transfer duration bounds use the declared no-stepping hard-transfer assumption.
  Only possible ceiling clips/grinds can shorten their ordinary flight clock.
  Unknown ceiling reachability leaves that contribution at zero.
- Shared analysis does not make differently constrained models identical, and
  moving a check earlier need not make it cheaper. Cache/proposal allocation and
  book-keeping costs must be measured alongside workers.
- New comparison/state semantics require new exact-continuation identities.
  Historical checkpoints remain readable evidence; they are not silently resumed
  under the new policy. Supplied legacy APIs keep their compatibility controls;
  active anchor search does not restore progress-clock comparisons.
- The fixture still has the stored B6 box; the user's reported rightward extension
  has no supplied replacement bounds. Measurements and validation concern the
  declared scene, not an updated actual-world map. Correct B6 geometry is separate
  input needed before making that broader claim, not a reason to hardcode routes.

## 2. Pre-edit code inventory and migration ownership

The inspection found duplicated count extraction and sorting, a separate legacy
protection engine, and a minimum-run decision that bypasses those protections.
The pre-edit anchor DFS did not call `time_progress.protections`; current adapters
now use the common module directly.

| Current location | Actual responsibility today | Planned ownership |
| --- | --- | --- |
| [`solve_spec.py`](../autonomy/solve_spec.py): `motion_record`, `processing_key` | Classifies whole histories as J/R/A; builds normal T/J/R key and schedule/phase metadata. | Delegate counts and key to the shared comparison module. Keep solve identity/scope and existing record formatting here. |
| [`anchor_policy.py`](../autonomy/cell_routes/anchor_policy.py): `local_counts`, `priority`, `within_caps` | Independently classifies a span and constructs normal/turn keys. | Thin compatibility adapters using the same shared label/key. Keep anchor/owner/cap and hard-transfer structural rules here. |
| [`depth_first.py`](../autonomy/cell_routes/depth_first.py): `arrival_key`, nested `priority`, `record_priority` | Another T/J/R tuple; chooses anchor versus non-anchor processing; score-loop key comparison. | Resolve context and delegate. Remove literal duplicate tuples; score-loop ordering must not become pruning. |
| `depth_first.py`: `time_bound`, `run_bound`, `eligible`, `screen` | Applies finish bounds, run-family exclusion and saving at several boundaries. Queue filters also call the run rule. | Build one comparison request for new/selected work; persist/execute its returned action. No count or peer-dominance logic here. |
| [`minimum_runs.py`](../autonomy/cell_routes/minimum_runs.py): `latest_jump`, `remember`, `exclusion` | Normalizes a matching latest-jump family, remembers least runs, decides extra-run exclusion itself. | Retain family normalization and peer references. Replace the decision arithmetic with the shared helper; no independent legacy exclusion algorithm. |
| [`time_progress.py`](../autonomy/cell_routes/time_progress.py): `protections`, `clearly_worse`, `retain_two_goals` | Separate OR guards, common-clock compatibility/state checks and complete time/progress peer test. Production references to the first two are internal to this module; tests also call the retention API. | Adapt old records to the shared helper and format its result. Move all comparison predicates into that helper, including common-clock safeguards. Retain storage/selection orchestration here. |
| [`movement_frontier.py`](../autonomy/movement_frontier.py) | Uses `processing_key` or an injected anchor key, then filters/solves a selected prefix. | Uses the common key and invokes the owning frame's common assessment at selection. No copied count guards. |
| [`movement_search.py`](../autonomy/movement_search.py) | Persistent dispatch or older balanced/legacy queue; independent elapsed-versus-accepted-finish checks. | Use the common bound predicate wherever that bound applies; keep declared balanced service allocation separate from candidate inferiority. |
| [`turn_observability.py`](../autonomy/cell_routes/turn_observability.py) | Recomputes counts/key via anchor wrappers for diagnostics. | Record the actual shared label/key/verdict used at selection; no parallel interpretation. |
| [`anchor_time_bound.py`](../autonomy/cell_routes/anchor_time_bound.py) | Divides remaining raw landing-cover distances by a speed bound, without recording contact-to-boundary phase offsets. | Establish a phase-correct bound, then supply bounds/provenance to the comparison helper. Add duration certificates as section 6 specifies; no peer pruning here. |
| [`two_goal_experiment.py`](../autonomy/cell_routes/two_goal_experiment.py): `competitive_followup` | Independently filters unfinished audit entries by elapsed T versus the accepted finish. | Request the shared incumbent verdict for this test. Keep its explicitly declared reference-domain construction separate. |
| [`event_search.py`](../autonomy/cell_routes/event_search.py), [`route_queue.py`](../autonomy/cell_routes/route_queue.py) | Carry minimum-run policy identity and aggregate deferral/restoration counters. | Include the shared comparison version in resume identity and summarize the new decision records consistently. |

The shared-helper scope is intermediate tick/count comparison and pruning.
[`ranking.candidate_rank`](../autonomy/ranking.py) remains the terminal
`(actualT, actualObjective)` ranking; it must not acquire intermediate jump or
run/air preferences. `decision_windows.retain_candidates` is an explicit older
objective-band/resource policy, not a duplicate tick comparator. Geometric route
priorities, physical feasibility, cap checks, exact model equivalence and solver
repair ordering also keep their own meanings. They must not silently become
additional ways to apply the intermediate tick heuristic.

Experimental drivers that perform this same comparison must call the public
helper. Preserve historical copied source/evidence snapshots unchanged. Update
current adapters, audits and tests rather than editing frozen experiment code.

## 3. One shared comparison module and decision helper

### 3.1 API and dependency direction

Add `autonomy/candidate_comparison.py`, below the cell-route layer and independent
of solvers, compilers, geometry and mutable queues. It owns:

```text
comparison_label(program, *, start, through, identity, mode, context, evidence)
    -> serializable normalized label with counts and schedule metadata

priority_key(label)
    -> the one T/J/R-or-A/stable-ID ordering key

compare_candidates(candidate, peer=None, *, context, incumbent=None)
    -> ComparisonDecision
```

`compare_candidates` is the sole decision helper for this type of comparison.
All structural protections and numerical comparison operators live there.
`comparison_label` and `priority_key` are shared data/key operations, not parallel
pruning policies. No caller may implement a second `more_jumps`, `preferred_ticks`,
`clearly_worse`, extra-run, or strict-incumbent comparison beside it.

The module imports only basic Python utilities. `solve_spec` and `anchor_policy`
import it, not vice versa; avoid a circular import through digest/physical keys.
Adapters supply stable existing context/phase identities. The legacy
`motion_record` serializer can retain its current field names and phase digest
while using the common classification rather than maintaining a second loop.

### 3.2 Label and context contract

```text
ComparisonLabel {
    id, T, start, through, J, R, A, mode, tickCost,
    scheduleSignature, phaseSignature,
    incomingContext, scope, milestone, landingPhase,
    basis: anchor_arrival | matched_preparation | open_prefix | clock,
    evidence: {validated, witnessId, modelId, optional measurements},
    remainingFinishBound: optional {ticks, proofIds}
}
ComparisonContext {
    purpose: hard_anchor | matched_preparation | common_clock,
    backend: automatic_anchor | supplied_legacy,
    policyVersion, comparisonContextId, localDeferralEnabled,
    hardLatched, familyId, requestedMeasurementContexts, margins
}
```

Use full original input history to extract a requested interval. T is the absolute
endpoint `through`; J/R/A cover `[start, through)` and obey `T-start = J+R+A`.
A jump is `grounded[t] and jump[t]`, including named cancellations that preserve
horizontal impulse. Ignored airborne presses do not count. Unknown or malformed
metadata is unavailable, not zero. Preserve old scheduling fallbacks for such
records only; unavailable counts cannot justify exclusion.

An anchor comparison requires the same actual milestone/contact phase, compatible
incoming history, scope and binary mode. The incoming identity is the owned
parent history/domain, not the full candidate profile key: ordinary alternatives
with different input timing must still be comparable at the anchor. Different
future cases or elevated/airborne boundary states cannot masquerade as the same
ordinary landing phase. Different modes/contexts are retained as incomparable
for the local heuristic, while its global finish bound remains applicable.

The purpose is an explicit evidence alignment, not permission for a caller to
supply its own comparison callbacks. All three purpose implementations live in
this module and share classification, key construction and count-guard logic:

- `hard_anchor`: apply the settled count rule at a common anchor in a latched span.
- `matched_preparation`: require the existing normalized family proof; use the
  same count rule for the specified ordinary preparation. Family extraction is
  an adapter, not another exclusion formula.
- `common_clock`: preserve legacy required clock/goal coverage, exact schedule/
  phase/scope compatibility, position/forward-velocity/speed protections and
  existing margins before its complete-known time/progress test. Those state
  predicates move out of `time_progress`; active anchor search requests no new
  clock measurements or progress-pruning objective.

The helper checks eligibility as well as count protections. In the automatic
anchor backend, local deferral requires the enabled policy and a validated hard
latch, including when a matched preparation description is available. An unlatched
span keeps normal time/momentum ordering without this local suppression. Explicit
supplied legacy controls retain their existing opt-in family/clock policy through
their declared context; they cannot enable broader automatic-anchor pruning.
The existing `minimum_run_pruning` option remains the compatibility switch for
recoverable local deferral. Disabling it does not disable structural hard latches
or full-finish bounds. Do not add a second independent tick-pruning switch.

Do not turn the legacy ban on comparing different schedules at a common clock
into a ban on structural count comparisons at a common anchor. Conversely, do
not apply the anchor rule to legacy clock states lacking comparable measurements.
Keep each legacy clock/state sample and spatial-arrival measurement under its own
original key; a record with several measurements is not one combined physical
state. The helper retains the existing same-peer/all-required-context coverage
test. Never substitute a spatial-arrival T into a clock state's count interval.

### 3.3 Canonical rule and returned verdict

For compatible complete anchor labels C and B:

```text
if a validated incumbent exists and T(C) + certifiedRemaining(C) > incumbent.T:
    return exclude_bound
if the context does not enable local peer deferral:
    return keep
if no peer exists:
    return keep
if comparison context/counts are missing or incompatible:
    return keep_incomparable

reasons = shared_count_protections(C, B)
    # more_jump_inputs if J(C) > J(B)
    # fewer_run_inputs / fewer_air_inputs if tickCost(C) < tickCost(B)
if reasons:
    return keep_protected
if T(C) == T(B):
    return keep_equal_time
if local deferral is enabled by the purpose and validated T(B) < T(C):
    return defer_preparation
return keep
```

`shared_count_protections` is an internal operation of this one module, reused
by its evidence alignments; it is not a second public caller policy. Missing local
metadata never prevents an elapsed-time exclusion when that elapsed clock is
known. Unknown remaining time contributes the trivial bound zero. A lower-bound
certificate affects only its matching scope. Guards protect from peer-based
heuristic deferral, not from physical impossibility, caps or certified late finish.
An incumbent must belong to the same actual task and validation domain. Preserve
the existing fixed-terminal-clock exception in supplied controls: a clock/progress
experiment does not acquire a new minimize-finish-time rule just because it calls
the common helper. A configured horizon is also not a validated incumbent.

One same peer must meet the entire deferral test. No fastest-time peer plus an
unrelated best-count peer. The OR guards do not require both advantages, and
stable IDs never authorize exclusion. Equal T always remains eligible.

Use `(T,-J,tickCost,id)` for ordering. A candidate's greater T alone does not
permit deferral when it has more J or lower tickCost. At fixed T/J, minimizing
R maximizes A and minimizing A maximizes R; that identity must not be used to
replace the confirmed guard with raw more A/R at different times. The common
module owns `tickCost` selection and both OR predicates. Callers supply only the
frozen binary mode and cannot choose another metric.

Return data, not queue mutations:

```text
ComparisonDecision {
    version, action, candidateId, peerId, contextId,
    priorityKey, candidateCounts, peerCounts,
    protectionReasons, compatibilityReason,
    elapsedT, optimisticFinish, incumbentT, boundProofIds,
    peerLookupAllowed
}
```

Stable actions distinguish ordinary keep, protected, equal time, incomparable,
recoverable deferral and certified bound exclusion. Retention caps or service
slices are separate scheduling/resource events, never heuristic inferiority.
The no-peer assessment returns `peerLookupAllowed` only when context, evidence
and policy permit a local comparison. Use it before querying the peer index.
Reuse the candidate label and its derived bound across pairwise checks; neither
the original-state analysis nor count/phase hashing runs once per peer.

### 3.4 Owned candidate views, partial prefixes and caller boundaries

Peer comparisons remain at anchors. Use exact counts from a composed candidate
ending at that anchor, or the existing matching-preparation description for that
same candidate. Do not add internal comparison anchors or a new solver pass.
A raw shorter prefix cannot be compared against an arrival as though future
setup/transfer choices had already been made. Such prefixes get the certified
full-finish bound; they do not get a new speculative future-count/max-J analysis.
In particular, retaining a hold prefix retains its possible setup-hop children.

Resolve the candidate's owning endpoint before computing comparison counts or
finish time. A generated compound proposal can extend beyond the first required
anchor. Its owned anchor prefix has its own T/J/R/A, rows and physical scope;
future tail inputs must neither cause a false late-finish exclusion nor supply
extra protective jumps. Use `_landing_prefixes` and the existing owned projection
logic to identify that view, without adopting any future-owned prerequisites.

Invoke the helper on each new owned candidate and after selecting a queued
prefix, arrival or refinement, before expansion/NLP where possible. Exact-model
reachability still checks the appropriate physical proposal/view in section 5.
Re-evaluate lazily against current peers/incumbent before dispatch.
Queue filters inspect stored lifecycle status only; remove `run_bound()` side
effects from filtering the whole queue, alongside the already removed time scans.
A new peer/incumbent does not trigger queue-wide reassessment.

Scheduling-only score-loop breaks may compare the common priority key, but must
leave the later candidate queued. A lexicographic key is never itself evidence
for `defer_preparation`. Observability records the returned verdict used by the
caller, rather than recomputing a potentially different decision.
Build count/key labels through the shared helper and reuse each candidate label
across peer comparisons. Decision caching is keyed by frame, immutable physical
identity, binary mode and peer/latch revisions; mutable repair logs are not count
cache keys. The implementation does not retain a second mutable profile-count
cache. The profiler measures label/key construction separately to make repeated
ordering work observable.

## 4. Frame state, protected alternatives and recovery

### 4.1 Peer evidence

Reuse existing frame arrival records. Index validated peer references by the
shared label's `(comparisonContext, mode, J, tickCost)` bucket. Choose its
least-T peer using the common priority key; do not add a local preference formula.
Retain all actual equal-count/equal-time programs and physical variants in their
normal records; an index entry is not a merge of search branches. Only a peer
reference with appropriate validated arrival evidence may suppress an untested
candidate. Update the small index transactionally on `score()` acceptance.

This index is a set of count tradeoffs, not the candidate queue and not a second
incumbent. The global best full finish remains one record. Query the peer index
only for the new/selected candidate, using the common helper for pairwise verdicts;
record lookup cost. No eager queue rescanning or arbitrary label cap is introduced.
Partition multiple incoming roots rather than borrowing numerical feasibility
from a different frame. The hard topology latch remains solve-global.

### 4.2 Real11 examples required by the policy

B1 is the implicit starting anchor. Run8→jump8→B2@17 establishes a feasible
one-transfer jump and latches B1→B2. Jump0→B1@12→jump12→B2@21 remains legal
source preparation followed by one transfer.

| B2 label | T | J | R | A | Comparison against the first row |
| --- | ---: | ---: | ---: | ---: | --- |
| Initial8 | 17 | 1 | 8 | 8 | Earlier arrival |
| Initial0 | 21 | 2 | 0 | 19 | Protected by more J and fewer R |
| Comparable extra-run transfer | 18 | 1 | 9 | 8 | No ordinary protection; recoverable deferral |

The initial0 label is protected by both more J and fewer R in ordinary mode;
its additional J also protects it in turnaround mode. The extra-run ordinary
transfer is unprotected. In a turnaround, fewer A protects; an extra R with
unchanged J/A merely adds time and is not protected. Likewise, extra A with
unchanged J/R does not protect an ordinary candidate. Protected alternatives
stay in normal search after a peer succeeds; they do not wait for recovery.

The [separate cold experiments](../autonomy/experiments/anchor-transfer-preparation/README.md)
found finish67 from the earlier B2 arrival and finish64 from the later one.
The policy keeps that latter possibility. It does not guarantee that strict DFS
services every protected root before a finite wall limit or prove final optimality.

### 4.3 Deferred work and recovery transitions

Use `cell_routes/preparation_deferrals.py` only for storing/restoring work and
peer-index ownership. It imports the common decision helper and contains no
second count comparator, preference formula or legacy minimum-run fallback.

```text
DeferredPreparation {
    id, frame, incomingContext, span, parentDecision,
    program, optional savedExpansionCursor,
    comparisonDecision, peerEvidence, hardLatchRevision,
    status: deferred | restored | excluded_bound | exhausted,
    recoveryCause, restoredLineage
}
```

Keep full lossless programs and repair state initially, using the existing
checkpoint store. Preserve a selected expansion cursor and distinguish deferred
children from tried children in `seen`; restoration cannot lose work because
an admitted deferred program already has a search identity.

Save an explicit required-continuation failure as a pending recovery cause.
Serve runnable first-class siblings before restoring deferred work. Once there
is no runnable ordinary continuation in that owning context, restore one eligible
deferred alternative in the common priority order; attach the exact failed
candidate/frame/model. Parked unknown alternatives stay preserved.
Exhaustion of physically checked alternatives can request a preparation change
without labelling the feasible ancestor numerically infeasible. Unknown-only,
budget interruption and heuristic deferral alone do not trigger recovery.

A restored lineage bypasses the same local deferral in that owning frame until
the next anchor. It still obeys structural hard topology, caps, no-revisit and
full-finish bounds. Unrelated siblings do not inherit the bypass. Handle restored
internal-node failure in `evaluate_node()` as well as anchor failure in `score()`;
today only the latter explicitly supplies this recovery transition.

Skip globally excluded saved work without consuming the only restoration action.
Another definite failed required continuation may request another restored edge.
Do not reopen unprotected deferred work merely after success; protected work was
never removed from the normal queue. Preserve all source-local jumps and never
reopen a globally latched multi-transfer topology.

## 5. Shared optimistic reachability at proposal generation

### 5.1 Pre-edit landscape and migration

| Layer / file | Work currently performed | Refactor |
| --- | --- | --- |
| [`reachability.py`](../autonomy/reachability.py) | Interval state/velocity bounds; Y/X/Z movement phases; norm-distance checks; required rows/regions; optional LP refinement. | Keep as the low-level math, with model-conditioning support if needed. |
| [`linear_reachability.py`](../autonomy/linear_reachability.py) | Cached optimistic control LP; supports zero threshold/non-ladder contexts; returns no proof when unsupported. | Reuse; do not recreate a second LP. |
| [`interactions.py`](../autonomy/interactions.py) | Role/window query/cache, absence normalization, broad free-approach gate, fixed-program envelope compilation and required-event checks. | Make this the shared analysis/query service and sequence-prerequisite API. |
| [`open_movement.py`](../autonomy/open_movement.py) | Early single landing check before ordinary profile construction. | Use the same prerequisite API and preserve normal landing phase. |
| [`stepping_generation.py`](../autonomy/stepping_generation.py) | Early separate lower/higher checks, then 8 wall or 4 corner branches; duplicated profile/open case construction. | Condition the approach on the lower prerequisite before testing the higher; share prerequisite descriptors/builders across supported modes. |
| [`ceiling_generation.py`](../autonomy/ceiling_generation.py) | Early ceiling/launch-grind checks for open generation; separate profile construction. | Use the same service and event prerequisite descriptions. |
| [`movement_search.py`](../autonomy/movement_search.py) | `screen_program` repeats route compilation, vertical validation, coefficient compilation and interval/LP preparation done by `InteractionGate.envelopes`. | Delegate to shared fixed-program analysis and cache. |
| [`movement_frontier.py`](../autonomy/movement_frontier.py) and legacy iterator | Full proposal is screened, optionally projected, then screened again even when unchanged. | Compose/check once through a shared proposal wrapper; recheck only a genuinely different resulting program. |
| [`depth_first.py`](../autonomy/cell_routes/depth_first.py) | Time/hard/run policies, scalar first-floor viability, `gate.required`, `screen_program` orchestration. | Obtain tick decisions from the shared comparison helper and reachability from the shared analysis service; no local reimplementation of either. |
| [`checkpoint_search.py`](../autonomy/cell_routes/checkpoint_search.py) | Rebuilds fixed-program envelopes before constraint generation. | Reuse the shared prepared analysis only for an identical effective model/scope. |
| [`constraint_variants.py`](../autonomy/constraint_variants.py), [`block_contacts.py`](../autonomy/block_contacts.py), supplied corridor adapters | Reachability omits irrelevant unwanted contacts/solids, rejects impossible row combinations, filters gates and floor checks. | Keep these consumers, using the shared service/proof semantics. Their later added rows are new queries, not redundant copies of the producer query. |

There is already substantial shared mathematics. The missing pieces are shared
**model preparation**, conditioned **sequences**, early placement and explicit
reuse of the resulting exact-context analysis. Moving every check physically
into the stepping module would create a stepping-specific subsystem and leave
the other consumers duplicating the same work.

### 5.2 Shared API and ownership

Extend `InteractionGate` instead of adding a competing reachability engine:

```text
analyze(program, scope) -> Analysis
approach(context, jumping, through) -> Analysis or unknown
condition(analysis, prerequisiteRows, physicalContactChanges) -> Analysis or unknown
sequence(context, prerequisites, scalarOutcome) -> Proof
check_proposal(program, scope) -> {analysisKey, proof, status}
```

`Analysis` contains the prepared route/plan, validated scalar trace, coefficients,
effective rows/contacts, phase envelopes and a canonical exact-context key.
`condition` recompiles coefficients when a contact/case changes the schedule;
adding only X/Z bounds can reuse coefficients and rerun interval propagation
with those rows. Never add contact rows while reusing incompatible free-flight
friction, grounded flags or horizontal-impulse behavior.

Use the existing `compile_program`, `validate_vertical_schedule`, Java compiler,
`reachable_envelopes`, `interaction_reachability`, `query_interaction` and
`linear_exclusion`. Keep a single implementation of removal of synthetic open
terminal-support rows. Preserve `screen_program`'s initial-floor guard rather
than losing it during deduplication.

Handle an empty initial prefix through its proposed approach or an explicit
initial-state analysis; `compile_program` currently rejects a zero-input program.
Do not require a dummy input or a numerical solve to establish launch bounds.

Use two cache levels. Coefficients are keyed by the exact request payload already
sent by `JavaCompiler.compile` (ground/jump inputs, inputs/physics and shared
contact/vertical data). Factor that payload builder once in the existing compiler
path; do not duplicate its field selection to invent a cache key. X/Z row or
objective changes alone must not cause a new coefficient request.

Envelope/analysis keys additionally include immutable scene/geometry identity,
effective rows and contacts, initial-state intervals, scope/horizon, threshold,
phase convention and probe policy. Generation removes synthetic open terminal
rows while a solve may retain actual landing/task rows: those are distinct
envelopes even if their coefficient payload is identical. Sharing compilation
does not justify sharing the more constrained envelope in the relaxed context.
Family queries additionally include phase-correct state enclosures, initial velocity intervals,
allowed movement/case capabilities and hard-latch policy. Use existing digest
helpers; do not hash an entire mutable repair log for a reachability result.
Session-local caches are disposable; persisted decisions retain stable proof IDs
and the context needed to recompute them.

Statuses remain `absent`, `not_ruled_out`, `unknown`. Only `absent` excludes a
required proposal or omits unwanted-interaction alternatives. An absent unwanted
contact never rejects the route. Unsupported LP conditions or missing probe
bounds retain the candidate as unknown. Transient budget/timeout/cancellation
pauses the exact descriptor/query without advancing its cursor; it is not an
absence proof or a reason to emit a different fallback choice on this slice.

### 5.3 Stepping sequence: lower and higher must be compatible

For each scalar approach and possible lower landing:

1. Apply cheap case-height, horizon and support-owner eligibility checks.
2. Query the lower prerequisite with the existing phase convention.
3. Build/reuse a light common scalar/case skeleton for the proposed lower/higher
   sequence. This is one skeleton per shared outcome, not all side variants.
4. Condition its envelope on `region_rows(lower, landing, ...)` and any shared
   physical contact/outcome constraints. Repropagate; query the required higher
   interaction at its actual input and phases.
5. If the conjunction is absent, emit an exclusion record and construct no wall
   or corner variants. Otherwise enumerate the existing 8/4 variants and screen
   their added conditions through the same service.

Reuse existing `region_rows`, `landing_wall_options`, `corner_rows`, named event
metadata and scalar replay. No Real11 block IDs or distance constants belong in
the generic implementation.

`query_interaction.windows` currently describes alternative contact phases:
absence means **all** windows are excluded. A sequence of required prerequisites
is a conjunction. Do not model it by concatenating windows, or by independently
checking every region in the unconditioned envelope. Enforce preceding state
requirements in the model before checking the next prerequisite. If a swept
phase condition cannot safely become a state row, keep its uncertainty and use
the existing joint proposal/LP check rather than inventing an exact endpoint.

Named stepping/grind phases intentionally have unknown Y and conservative
intended X/Z probe extents in `reachable_envelopes`. Retain those semantics.
Their realized feet height is not a complete bound on both legacy step probes.
This change must preserve the nearby blip/grind controls and five-input route.

Preserve ownership distinctions: hard topology forbids an intermediate support,
but touching a higher block's side as a case prerequisite is not necessarily a
support visit. Do not apply `landingAllowed` indiscriminately to every physical
box used by a case. Final visit/transfer audits remain authoritative.

For B3@28, the saved probe generated 8 blips and 4 corner blips; all failed the
later screen. The shared lower-conditioned check should reject their common
impossible prerequisite before those 12 dense programs are built. For a nearby
valid pair it must still generate the relevant side/corner branches.

### 5.4 One full-proposal boundary, with context reuse

Add `propose_choices(kind, context)` alongside low-level `generate_choices` in
`movement_generation.py`. The existing registry and profile/timeline API remain.
For open search it yields a record containing the original choice, its composed
full child, its explicitly owned candidate view, raw ordinal and a persisted
projection flag. Exact analysis tokens live in the shared service cache. Producer-specific prerequisite checks run before the original choice is
built; this wrapper owns composition and exact proposed-sequence checks. Resolve
the owned view with section 3.4 before tick decisions, without recompiling or
copying the same physical program just to repeat a check.

Use this wrapper from both persistent and legacy open frontiers and direct open
generation paths in DFS. Avoid composing/copying the same program again merely
to repeat its screen. Keep cheap policy checks separable from reachability.
Preserve full-proposal safety checking and its rejection scope. It is a check of
the full child, not a time-bound verdict or infeasibility verdict for its prefix.

If a candidate is projected to an earlier landing or checkpoint, it is a new
model. Its retained owned rows and physical scope must be checked independently
or reuse a proof for exactly that model. A full child's contradictory future tail
must not erase an independently valid owned anchor candidate. Materialize/check
that candidate under its own context, following existing ownership rules; never
strip a case's indispensable prerequisite merely to manufacture a shorter route.
This is analytical proposal handling, not an extra numerical ancestor probe.
Store scoped rejection evidence; an impossible extended child never labels its
shorter unqueried ancestor infeasible.

Own the resume cursor at a stable raw descriptor/stage boundary, not an ordinal
in a newly filtered output stream. Save the current descriptor, prerequisite
stage and any already completed proof ID before budgeted work. Advance only
after its retain/reject result is committed. Reconstructing an iterator must not
change identity because a previous slice timed out before a now-successful gate.
Version this generator/proposal policy in continuation context. Test interruption
before/after common-pair rejection and within side/corner fan-out; no choice is
lost or redispatched as a different one. Report descriptor, constructed-choice
and emitted/admitted counts separately so the new earlier filters do not make
old/new generation counters appear to measure the same unit when they do not.

At solve preparation, use cached coefficients/envelopes only when route, rows,
contacts, objective-independent physics and scope match. Newly committed
physical avoidance rows, constraint variants, changed cases or scope changes
require new envelope analyses; coefficient reuse is still controlled by the
exact compiler payload. Continuous numerical solve identities/policies remain
separate from geometric query identities. No solved yaw/endpoint/velocity seam
is fixed by reuse.

Unify `screen.contradiction` and `screen_program` as compatibility callers of the
same service. Keep the late safety boundary for externally supplied programs and
new constraint variants, but eliminate repeated preparation for identical keys.
Report an envelope-wide contradiction directly with its state/axis; do not blame
the first earlier required contact (the current misleading B1 diagnostic).

Profile/support generation can opt into the same prerequisite descriptors when
it has a valid scene/approach context. Timeline and externally supplied profile
modes retain existing validation when they lack one. Audit all registry modes;
do not infer a fixed-prefix absence proof for an unspecified movement family.

## 6. Minimum hard-transfer duration bounds

### 6.1 What is valid for Real11 and what is not a generic theorem

Current ordinary scalar durations are:

| Transfer | Whole input duration, including launch input |
| --- | ---: |
| B1→B2 | 9 |
| B2→B3 | 9 |
| B3→B4 | 10 |
| B4→B5 | 15 |
| B5→B7 | 9 |

They come from `block_profiles.ordinary_jump_profile`, not a hardcoded table.
The follow-up probe confirms every physical pillar's underside lies below the
ascending head sweep for these launch heights: there is no candidate overhead
ceiling in this declared scene. For the only immediate step/cancel height match,
B3/B4, the raw contact-cover-to-expanded-B4 gap is about 2.4 blocks. That is not
yet a launch-state proof: the ordinary contact check can precede one horizontal
move. With a 0.72114545 displacement enclosure, the relaxed launch gap is still
at least about 1.67885, greater than another input's 0.72114545 bound. The wider
phase-correct enclosure is what a family certificate must actually use. The saved
probe itself did not build that certificate.

The no-stepping hard-transfer assumption is user-specified, not a theorem about
arbitrary maps. Existing two-input slab/corner-cancel fixtures remain valid in
unlatched generic movement and source-local preparation. They do not qualify as
an eligible hard transfer. `transfer_structure` must expose/reject stepping in
the transfer window, while preserving stepping that belongs to an earlier closed
source-local preparation. The hard-policy identity changes accordingly.

A normal/ceiling transfer can establish the permanent hard latch. A successful
stepped transfer alone cannot establish it. Once latched, no stepped transfer is
reintroduced during recovery. Thus a duration bound cannot silently assume away
an alternative that the same hard-transfer search continues to admit.

### 6.2 Derive a reusable duration certificate

Add `cell_routes/transfer_time_bounds.py` as a small consumer of the shared
proposal service. It derives a conservative duration; it does not solve routes.
Keep its scalar mechanics in the existing movement/profile builders.

First define a phase-correct boundary-state enclosure. In `route.schedule`, a
`pre` landing constrains overlap at `state-1`, while anchor comparisons use the
grounded state at `state`. Horizontal movement between them can leave the contact
cover. The five representative saved parent endpoints are inside their covers,
so this audit has not demonstrated a false exclusion in that saved run. Uniform
pre-landing offsets can cancel when deriving a total horizontal-time bound;
the current raw-cover H is not automatically wrong for those routes. It still
needs a justification for all admitted phase combinations, and a raw cover is
not directly a launch-state domain for the new event-absence proof.

Use the shared phase/footprint and motion helpers to enclose the state after the
landing input: for ordinary `pre` landings, outward-expand the contact cover in
X/Z by a conservative one-input displacement bound. A containing rectangle is
an allowed relaxation. A `post` constraint may use its corresponding cover
directly. Exact prefix interval envelopes can tighten the enclosure without
freezing a witness. If phase/displacement is unsupported, omit that segment's
bound. Apply this same enclosure definition to H and to transfer launch contexts.
This may relax H compared with its old raw-cover estimate. Measure that effect;
the new positive V should supply the useful hard-flight cost, without assuming
that tightening one component automatically improves the whole search.

For an ordered anchor pair, create an optimistic family context:

- Launch anywhere in the full phase-correct source-state enclosure; evaluate separate
  components and take the minimum resulting bound over them.
- Allow every incoming horizontal velocity within the shared global speed bound.
  A componentwise box can relax the velocity norm; do not narrow it to a witness.
- Use actual grounded launch Y/carry, threshold and requested jump semantics.
- Use the declared normal/ceiling one-transfer domain. Source-local preparations
  remain possible, but stepping is outside the actual transfer; bound queries
  consider ceiling clips and launch grinds only.
- No endpoint, guide point, solved yaw or previously observed velocity is fixed.

Check the launch-height/carry prerequisites against the actual scalar boundary,
not `terminalSupport` alone. A named elevated-ground outcome can have a support
label without the canonical ordinary standing state. Return unknown/zero for
that pending transfer in this implementation; do not recharge an ordinary jump
from the physical block top by assumption.

Extend `ordinary_jump_profile` and its shared flight derivation to accept an
explicit threshold, retaining `.005` as the compatibility default. Pass the
active threshold when deriving D; do not fork the equations. Match the existing
`first_descent`/landing-state conventions exactly.
If no ordinary profile is derived within the active domain, return unknown/zero;
do not dereference a missing profile or infer that a named hard transfer is impossible.

Then ask whether an allowed ceiling interaction can occur and shorten D:

1. Use section 5's shared ceiling descriptors and optimistic launch enclosure to
   test ascending clips and launch grinds that can take effect before D. Use
   resulting state `input+1 < D`; a change first at D cannot finish earlier.
2. Rule out ceilings using physical height windows and shared swept reachability.
   Missing probe/motion bounds remain unknown. Disabled ceiling capabilities are
   recorded as outside the admitted domain, not as absent world geometry.
3. If all shortening ceiling prerequisites are absent, certify ordinary D. If
   any is retained/unknown, return zero for that transfer contribution. Do not
   run a separate route solver to prove which ceiling route would be fastest.
4. A source-local landing closes preparation and pays its elapsed inputs; restart
   the pending transfer bound from its actual canonical grounded launch state.
   A noncanonical boundary keeps an unknown/zero contribution.

The certificate records `assumption: hard_transfer_without_stepping`, the hard
policy version and ceiling proof IDs. It needs no stepping-absence proof. The
stepping producer's joint-reachability refactor in section 5 remains required
for generic and preparation generation, independently of these flight bounds.

Persist `TransferDurationBound` with source/target, scope/context hash, domain and
physics versions, duration, derivation kind and prerequisite proof IDs. Derive
it once per compatible anchor-family context and reuse it. Geometry-only proofs
can be reused across prefixes; a proof from the exact B3@28 prefix cannot certify
all future B3 arrivals. Activate hard-only certificates only after the applicable
global latch is learned. Precomputation does not itself prove a hard jump exists.

### 6.3 Combine and apply the bounds

Extend `anchor_time_bound.remaining_bound` with optional per-transfer duration
records. Preserve its public diagnostic distinction between guide estimates and
pruning bounds.

```text
H = horizontal travel time from phase-correct state enclosures / speed bound
V = sum of applicable minimum whole-transfer durations
remaining = max(H, V)
optimisticFinish = elapsed + remaining
exclude only if optimisticFinish > incumbentFinish
```

Do not add H and V; their travel occurs during the same inputs. Do not round up
independent segments more aggressively without proving their phase boundaries.
Non-hard or unresolved spans contribute zero to V. H uses only segments with
proved state enclosures; unknown segments contribute zero distance rather than
the old raw-cover assumption. A completed target arrival omits its transfer. A
completed terminal arrival has zero remaining time and remains eligible at
equal T to improve the actual objective.

For an unfinished certified transfer, subtract its already-paid launch age from
D, using the actual last source contact and transfer impulse. If its current
case/airborne boundary does not satisfy the certificate, omit that residual
segment rather than restarting a full jump duration at an arbitrary air state.
Future known grounded transfers can still contribute their own bounds. Same-input
contact phases and named airborne anchor boundaries require explicit tests.

At a grounded B3@38 boundary, certified `10+15+9=34` means finish at least 72;
with incumbent 67 this is an irreversible time-bound exclusion, before another
setup-hop NLP. A B3 setup hop landing at T40 similarly has finish at least 74.
These are distinct from section 4's reversible local-time deferrals.

Maintain a suffix sum of applicable duration records indexed by anchor and latch
revision. Rebuild this small anchor table when its certificates/latches change,
not the candidate queue. Evaluate only a new/selected candidate against it and
the current incumbent; recheck immediately before any worker dispatch after a
resume or changed incumbent.

## 7. Implementation sequence

Before source edits, capture the matched baseline on verified `5d54f02b` in this
checkout, using fresh durable output paths and source hashes. Use combined0/8
300 seconds and an initial0-only control with a declared unchanged per-arm budget;
repeat those same arms/settings after implementation. This avoids needing to
switch checkouts later to obtain a valid baseline. The planning audit does not
start these solver runs.

### M1 — Centralize comparison without duplicating legacy logic

Implementation ledger (updated as work proceeds): baseline artifacts are under
`autonomy/experiments/hard-jump-comparisons/2026-11-11/`. Both source-stable arms
completed before runtime edits. Combined: T67, 198 wrappers/159 workers, 1,114
prefixes, first 15.63s, 75/75 witness replays. Initial0-only: T64, 73/56,
427 prefixes, first 8.392s, 34/34 replays, 90-second budget.

M1–M4 are implemented. `candidate_comparison` owns count/priority, OR protections,
strict incumbent comparisons and legacy common-clock safeguards. All listed
adapters delegate. `preparation_deferrals` owns peer storage/orchestration only;
the existing `minimumRunExclusions` list is the single recoverable-work table.
Anchor candidates are assessed lazily against validated frame-local peers;
open prefixes get only full-finish bounds. Cached decisions invalidate on peer
or latch revision. Selected recovery restores one eligible saved alternative,
reuses a matching validated witness, and skips globally excluded saved work.

`InteractionGate` owns bounded session caches for program preparation, canonical
compiler payloads, exact envelope analyses and screens. Actual shared scalar
blip/corner prerequisites reject before side fan-out. The common proposal wrapper
composes each new raw ordinal once, projects actual owned arrivals before
counting/bounds, checks unchanged models once and records unused-tail rejection
separately. Persistent/nonpersistent and direct DFS open paths use the wrapper.
A persisted `projected` flag preserves that distinction across JSON alias loss.
Early producer checks are deterministic; a timeout does not advance a failed
query or commit an absence result. Timeline/profile modes without a full scene
keep their existing validation instead of borrowing a fixed-prefix proof.

`transfer_time_bounds` derives the ordinary clock at the active threshold under
hard policy3's no-stepping transfer assumption. It queries only shortening
ceilings, returns zero for retained/unknown prerequisites, and never fixes an
observed pose. `state_covers` encloses post-input landing positions. H/V are
combined with max; durations require their corresponding global latch, completed
transfers are removed and already-paid ordinary flight inputs are subtracted.
The small anchor list is accumulated when a selected candidate is checked;
there is no cached mutable suffix or queue-wide rebuild. Duration certificates
are cached per pair in `DFS.transferTimeBounds`.

Focused acceptance includes shared-helper cases, legacy callers, native B3@28
joint rejection before builders, valid generic stepping, protected more-J setup
work after a full finish, explicit failure recovery, ceiling near/far controls,
paid flight clocks, owned-tail isolation and resumed ordinal/screen reuse.
A development regression that skipped corner checking after a blip-only rejection
was caught and fixed. M5 is complete: the600-test native gate passes without failures/errors/skips;
runtime hashes and all54 recorded historical evidence artifacts stay unchanged.
All131 archived T67/T64 witness prefixes pass current bounds. Matched cold
combined300s improves T67→T64, wrappers198→61, workers159→55 and
expansions1,114→35. Initial0-only90s retains T64, wrappers73→26, workers56→22
and expansions427→7. Both post-change arms reach Z=−1093.2381026602416;
all57+23 retained-witness replays pass with unchanged runtime hashes. The
combined arm retains8 recoverable deferrals and performs0 restorations; its
61 invocation fingerprints are unique. The evidence README qualifies overlapping
read-only audit work and additional profiling overhead; no precise wall-speedup
or global optimality claim is made. The first 20-second integration smoke found T67 at14.14s,
with9/9 independent accepted-witness replays and unchanged runtime hashes; it
ran alongside the test suite and is not a performance comparison.

Implementation, tests, fixtures and evidence are ready for the feature commit.
All pre-edit baseline artifacts and unrelated AGENTS/CODE_MAP edits are preserved.
The experiment README records final acceptance, source hashes, profiling costs
and both matched arms.

1. Add the pure `candidate_comparison` module and normalize program counts,
   metadata, keys and stable decisions there.
2. Route `solve_spec.motion_record/processing_key`, `anchor_policy.local_counts/
   priority`, DFS `arrival_key` and score/generation/selection sorting through it.
   Keep existing serializers and compatibility signatures as thin delegates.
3. Move comparison predicates from `time_progress.protections/clearly_worse` into
   the common helper's explicit common-clock context; its public functions become
   adapters. `retain_two_goals` uses the returned verdict, not another peer test.
4. Keep `minimum_runs.latest_jump` as a family describer. Its remember/exclusion
   adapters supply normalized labels to the same helper; delete the independent
   greater-run rejection predicate. The matched-family ordinary behavior remains
   covered, and turnaround protection cannot be bypassed by a legacy run check.
5. Centralize the strict incumbent predicate used by DFS, movement iteration and
   the current competitive-follow-up audit. The remaining-time model only supplies
   bounds; lifecycle adapters execute results. A declared horizon/cap remains a
   domain restriction and must not be labelled as a validated incumbent.

Exit: every intermediate T/J/R/A comparison and protection decision has the same
owner. Legacy common-clock controls retain their existing compatibility, state
protection and margins. Terminal ranking and explicit resource policies retain
their separate semantics. No production caller embeds its own count-guard math.

### M2 — Unify reachability preparation and condition prerequisites early

Factor fixed-program analysis from `InteractionGate.envelopes` and `screen_program`.
Implement section 5's conditioned sequence API and joint stepping prerequisites.
Share producer descriptors and register the composed-proposal wrapper in every
open search path. Reuse matching analysis in solve/constraint generation, while
checking changed physical rows/projections in their own context.

Exit: saved B3@28 rejects the common impossible prerequisite before constructing
8 blips and 4 corner variants; valid nearby controls remain. Unchanged proposals
are not compiled/propagated twice. Unknown named-probe Y stays conservative.

### M3 — Certified transfer durations

First make the horizontal/launch state enclosures phase-correct. Expose active-
threshold scalar derivation via existing helpers. Use M2's family ceiling prerequisites
to certify normal hard-transfer minima under the declared no-stepping assumption;
return zero for retained/unknown shortening ceilings. Combine the duration suffix with H using `max` and feed that proof to
M1's incumbent decision. No queue rescans.

Exit: establish phase-correct family-wide Real11 10/15/9 certificates and exclude
provably late B3 preparations before workers. Generic stepped controls remain valid outside hard transfers. Reachable
shortening-ceiling controls must not inherit ordinary D.
If conservatism prevents a Real11 certificate, record the exact retained/unknown
prerequisite and resulting zero contribution as an acceptance blocker to resolve;
do not hardcode the desired numbers or describe an incomplete proof as shipped.

### M4 — Wire protected hard-span deferral and recovery

Add frame-local peer indexes, deferred records and restored-lineage ownership.
Replace the anchor run rule with the shared helper at new/selected work boundaries.
Use actual anchor candidate counts and existing matching-preparation descriptions;
open prefixes get the valid full-finish bound without speculative future-count
machinery. Remove peer/bound decisions from queue-wide filters.
Wire internal and anchor failure recovery without dropping protected alternatives.

Exit: initial0 remains first-class after initial8 succeeds, redundant ordinary
extra-run families defer, turnaround fewer-A alternatives remain protected, and
saved unprotected alternatives restore on actual failure without immediate re-deferral.
Many extra-J setup hops intentionally stay protected; M3's valid full-finish bound
is what should remove the provably too-late ones.

### M5 — Persistence, observability and measured acceptance

Version shared comparison policy, preparation-state schema, duration/query policies
and all affected semantic contexts. Include the comparison version in anchor
semantic settings, DFS/movement continuation, `event_search` and the supplied
`route_queue` context. Keep family-normalization versions distinct from comparison
semantics; old `minimum_runs.POLICY_VERSION` callers cannot silently miss the new
shared policy. Update constraint/repair compatibility where model state changes.
Preserve raw descriptor/prerequisite-stage cursors for early producer checks, not
only the old count of yielded filtered choices; record that policy in outer and
movement continuation identities.
Reject old exact continuations explicitly. Checkpoint storage format need not
change just because search records change; preserve owned live state internally
and external isolation.

Extend `SearchCheckpoint` mutable aliases for new deferred/recovery records and
references. Cache data can rebuild; persisted decisions keep stable proof/context
identities. Avoid copying coefficient arrays into every deferral. Update the
module/function/file maps at implementation completion, preserving unrelated edits.
Update `event_search` and report/audit summaries that currently read
`minimumRunExclusions`; their legacy names may be serialization aliases, never a
second decision table with an independently maintained pruning rule.

## 8. Observability and acceptance checks

Record the same comparison decision that controlled the search:

- Candidate/peer IDs, context/purpose, counts and binary mode; priority key;
  validated peer evidence; every OR guard; kept/incomparable/deferred/excluded
  action; bound components and proofs; policy/latch revision.
- Peer-index update/query counts and wall time, selected-candidate comparisons,
  deferred/restored/exhausted work, actual failed-edge recovery cause, saved
  cursor and restored lineage. Incumbent updates must trigger zero queue scans.
- Descriptor counts, joint prerequisites excluded before fan-out, side/corner
  programs constructed, shared analysis hits/misses, coefficient compiles,
  interval/LP work and changed-context checks, unknown/suspended preparation.
- Exact numerical request fingerprint and solve policy at invocation, including
  successful internal nodes; wrappers/workers/seed shortcuts and timings.
- Prefix expansions, bookkeeping time, first full finish, best T/Z, accepted
  replay count and retained protected/deferred/unknown work. Retain binary turn
  point/edge/margin/replay-flip observations; logging does not change decisions.

Focused tests must cover the helper and **each adapter/caller**, not only a new
module in isolation. Extend the existing time-progress, orchestrator continuation,
anchor-policy/DFS/search/time-bound, movement, stepping/ceiling, repair/resume and
checkpoint-store suites:

1. Table-driven T/J/R/A cases: more J OR fewer R normally / fewer A in turnaround
   independently protects. Raw extra ordinary A or extra turnaround R with the
   same J/tickCost is not protection. Equal T, missing counts, ignored airborne
   inputs and grounded cancelled impulses have the settled behavior.
2. Counting interval and key parity across solve-spec, anchor, arrival, frontier,
   legacy comparison, minimum-run adapters and observability. Nonzero span starts,
   truncated history and mixed modes cannot produce different interpretations.
3. Legacy common-clock complete measurement requirements, phase/scope/schedule
   incompatibility and position/velocity/speed guards survive the move. Common
   anchor comparisons do not require extra clock/progress solves. Fixed-clock
   supplied controls preserve their incumbent-bound applicability exception.
4. Every migrated pruning boundary uses the same verdict. A mocked shared helper
   returning protection must prevent a legacy minimum-run or DFS bypass from
   suppressing that candidate. New/selected-only comparisons; no bounds/peer
   evaluations for unselected queued work merely on incumbent/peer updates.
   Local-deferral-disabled and unlatched contexts do not trigger peer-index work;
   labels/counts are reused instead of rehashed on every key comparison.
5. Same-peer evidence, incompatible incoming roots, source-owner aliases and
   sparse/multiple physical variants. Peer-index entries do not merge branches.
   Terminal minT/objective ranking does not acquire J/A/R preferences.
6. The actual initial8 T17/J1/R8/A8 and initial0 T21/J2/R0/A19 labels: latter
   remains first-class after the former succeeds. Keep a setup-hop child under
   an open hold prefix even if its direct-transfer family is unprotected.
7. Strict full-finish exclusion overrides local protections when its lower bound
   is later; equal finishes remain eligible. Open shorter prefixes cannot be
   peer-deferred against anchor arrivals. Future-owned tail inputs cannot alter
   the owned candidate's T/J/R/A. No artificial maximum future J analysis.
8. Global hard latch survives backtracking; multiple same-source jumps remain
   before/after latch and during recovery. Failed exact child never invalidates
   an unqueried ancestor. Restored internal-node failure advances recovery;
    unknown/budget stops do not; successful peers do not erase protected work.
    A pending failure cause cannot skip runnable first-class siblings.
9. JSON/shared-store interruption around comparison, partial generation, node NLP,
   latch learning and restoration preserves work/aliases and completed query IDs.
   Old incompatible continuations reject rather than losing existing alternatives.
   An analytical timeout before early pair rejection cannot shift an iterator's
   descriptor identity, lose a variant or cause a repeated numerical query.
10. Individually reachable lower/higher but impossible conjunction rejects before
    fan-out; nearby blip/corner/step and five-input grind/blip controls survive.
    Include translations/reflections, Y/X/Z phases and intended named probes.
11. Absence of an unwanted contact omits avoidance only; absent prerequisite
    rejects its owner. Full proposals and changed projections retain distinct
    scope. A contradictory unused tail cannot erase an independently valid owned
    candidate. New rows/cases/thresholds reprepare the necessary cache level;
    identical compiler payloads share coefficients even with different X/Z rows.
12. Duration certificates cover actual threshold/launch carry/landing phase;
    reachable ceilings and noncanonical boundaries fall back safely. Stepped
    slab/cancel controls remain valid outside hard transfers; latch eligibility
    excludes their actual transfer, preserves source-local cases and records the
    declared assumption. Optional supports in unlatched spans remain available.
    `max(H,V)`, paid flight age and same-input contacts avoid overcounting. Include
    a feasible pre-landing-overlap / post-movement-outside-cover control: neither
    H nor launch-event exclusion may assume the endpoint remains in the cover.
    Missing ordinary profiles and unsupported movement bounds return unknown/zero.
13. Exhaustible small trees check retained/deferred/restored unions and worker
    counts. Static review/search confirms no duplicate count classification,
    literal T/J/R/A key, count guard or greater-run rejection remains outside the
    shared module, except thin adapters and preserved historical snapshots.

Run focused standalone tests first, then the affected broad native gate and
independent accepted-witness scene replay. Use the existing runbook/native
Python/compiler environment. No mod build or Minecraft launch is required.

Measure new source-stable combined0/8 300-second and initial0-only cold controls
in fresh output directories with unchanged geometry, required anchors, physical
B7, caps, thresholds, cadence, objectives and budgets. Supply no archived programs
or yaws. Use section 7's pre-edit `5d54f02b` baseline for paired comparisons;
the older integrated full run predates its lazy-check follow-up and cannot stand
in for a matched timing baseline. Preserve old evidence.

Acceptance requires all meaningful controls, one shared comparison implementation,
early rejection of useless stepping combinations and fewer provably late setup
workers. Report actual T/Z, first-discovery and total wall, work counts and pending
coverage; neither protected retention nor a local solver result proves global
optimality. Bundle implementation, tests, fixtures and current documentation in
feature commits only after implementation. Do not spawn agents, switch checkouts,
forbid initial0/source hops or fix numerical seams to change the measured outcome.
