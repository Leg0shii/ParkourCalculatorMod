# Concrete solver-orchestration improvements

**Status: deferred by the user (2026-11-15).** Preserve this plan for a later explicit resumption; do not begin scheduler implementation now. Current runtime remains unchanged. This pause does not cancel the separately assigned bookkeeping performance work. See the [deferred-work register](DEFERRED_WORK.md).

2026-11-15. This is the single current planning document for the solver work in
this session. It consolidates branch scheduling, work eligibility and numerical
outcome handling. **The changes below are planned, not implemented.** Current
runtime remains the DFS implementation at `a11b9b18`; subsequent commits so far
are documentation/evidence only. The filename stays stable for existing links.

User-confirmed direction:

- Do not finish the run8 subtree merely because its first anchor arrival is
  earlier; give promising protected alternatives such as run0 timely service.
- All ordinary continuations compete using the **existing priority**, ordinary
  `(T,-J,R,id)` / turnaround `(T,-J,A,id)`, with absolute T and current span-local
  counts. Interleave meaningful movements across families; do not replace this
  with an optimistic-finish, progress, speed or weighted key.
- Ordinary waiting work remains eligible without requiring another branch to
  fail. Recovery-only alternatives are activated for explicit feasibility fallback.
- Under the agreed IPOPT-feasibility-oracle assumption, a completed normal check
  without an accepted feasible solution rejects that exact checked model. Do not
  retain a generic numerical-unknown retry category for those completed checks.
- Work that remains after a feasible witness is ordinary work, not a separate
  scheduling eligibility class. A stored feasible witness is result metadata.
- Continue preserving original-state re-solves, exact resume, actual ownership,
  count protections, strict finish bounds and recoverable heuristic alternatives.

Recommended implementation details and remaining decisions are identified below;
no scheduler, solver-result classifier or checkpoint schema has changed yet.
Bookkeeping optimization has a [separate agent handoff](BOOKKEEPING_PERFORMANCE_AGENT_HANDOFF.md).
That agent must preserve the current runtime contract; integrate these later
behavior changes deliberately, rather than hiding them in a performance patch.

## Scope of the deferred change: service order, not new solves

Interleaving is a priority/service-order change. It keeps the existing priority,
movement/constraint domain, meaningful-node cadence and solve intents. It does
not add decision families, ancestor checks, per-input NLPs or a solve solely for
switching. With a fixed finite ordinary domain/oracle outcomes and sufficient
resources, the intent is to service the same ordinary alternatives unless existing
eligibility rules exclude or defer them.

A literal identical numerical-call list is not promised. Earlier validated
incumbents, hard latches or compatible peer evidence may avoid work sooner;
recovery-only activation remains conditional on failure. Warm starts and physical
repair choices can also change concrete requests/outcomes with service order,
and per-query/other caps still apply even if the outer wall limit is removed.
Check equivalent candidate coverage/eligibility and justified differences, rather
than declaring any change in worker count a changed candidate domain.

The goal is to receive useful evidence earlier and avoid, for example, late run8
work once a valid exclusion applies. No extra cross-history pruning rule is part
of the scheduling change. The category/oracle clarifications below remain recorded
requirements for future scoped cleanup; their current code has not been migrated.

## 1. Simplify work categories and keep execution state separate

There are three scheduling eligibility classes:

| Eligibility | Meaning | Service / reactivation |
| --- | --- | --- |
| `ordinary` | Untested choices, protected siblings such as run0, unfinished generation/repair, other variants of a feasible program. | Competes in normal scheduling. Enough time alone does not override later exclusions or configured limits. |
| `recovery_only` | A slower comparable hard-span arrival without more J or fewer R/A protection, such as first9–12 behind first8. | Saved outside the ordinary competition; restore one at a time on explicit required-continuation failure after ordinary runnable alternatives drain. Recheck bounds before restoration. A successful finish does not activate it. |
| `excluded` | Checked infeasibility, domain violation or strict incumbent-bound exclusion for the relevant scope. | No automatic execution in that unchanged context. Keep the reason/evidence as an audit record, not a second runnable copy. |

Execution state is a separate dimension: `ready`, `in_flight`, `paused`,
`complete`, or `error`. The exact field names are a proposed representation;
they are not current schema keys. An ordinary job can be paused or already have
a feasible incumbent while other work remains. An excluded item is not runnable.

This replaces the earlier conversational categories3/4/5:

- **3, unfinished work:** keep the exact generation/repair cursor or in-flight
  query as paused ordinary work. Resuming does not repeat completed variants.
  An interrupted numerical call can restart; IPOPT's internal iterations are not
  checkpointed by the current worker.
- **4, completed query with no feasible result:** under the agreed oracle
  assumption, reject that exact model instead of inventing another retry pool.
  A budget interruption belongs to3, and a runtime/protocol/validation problem
  belongs to error handling. These exceptions must not be silently converted
  into infeasibility.
- **5, work after finding a feasible witness:** ordinary work. Different input
  sequences are ordinary branches; the same input sequence with different
  obstacle-side/contact/time rows is also a different discrete model/variant.
  Unfinished continuous optimization of one fixed model is the same job, not a
  new discrete branch for every yaw vector. A fully completed query does not
  become runnable again merely because we would like a better objective.

Feasible witnesses, completed-query records and incumbents are stored results.
They can coexist with ordinary pending work. Remove the conceptual special
eligibility for `refinements`; migrate its remaining work into the ordinary
scheduler while retaining all saved variants and its ownership. Physical repair
queues can remain nested within an owning job; this is not a request to flatten
all raw combinations into one giant queue or duplicate their prefixes.

### Hard-only tick deferral versus ordinary non-hard work

The active physical-anchor backend applies the minimum-run/generalized tick
heuristic **only after the current span is hard-latched**, and only to comparable
complete anchor arrivals. Open prefixes do not borrow a future arrival's counts.
It may defer a strictly later arrival only without more J or fewer R (ordinary) /
fewer A (turnaround) protection. This is recovery-only storage, not permanent
physical rejection. Hard status and turnaround mode are independent.

In an unlatched/non-hard span, extra run inputs remain ordinary work. The existing
ordering is still absolute T first, then more J, then fewer R normally / fewer A
in turnaround, with deterministic identity ties. Ordering a candidate later does
not move it to recovery-only storage. This lets longer preparation and optional
multi-support choices remain available without requiring a failure first.

Non-hard work still obeys physical reachability, exact-query feasibility, domain/
resource limits and the strict global optimistic-finish bound. Those are separate
from minimum-run/tick peer deferral. A newly validated one-transfer jump can
permanently latch the span and enable hard-only deferral for subsequent work.

Implementation guards: `dfs.run_bound` returns without peer deferral for an
unlatched anchor span; `comparison.compare_candidates` independently disables
local deferral for `backend=automatic_anchor` without `hardLatched`. Preserve
both guarantees through the new scheduler. Historical supplied/virtual compatibility
paths retain their older matched-preparation policy; do not describe that legacy
behavior as the current non-hard anchor policy or silently migrate it.

## 2. Normalize feasibility outcomes at one boundary

The user accepts IPOPT as the operational feasibility oracle. This is a search
policy assumption, not a claim of a mathematical global infeasibility certificate.
It applies to a **completed configured check of an exact model**, with all earlier
facings free. Lack of a result before the computation finishes is different.

| Actual event | Planned treatment | What must not happen |
| --- | --- | --- |
| Accepted feasible witness | Save it; apply node/anchor/terminal solve intent and continue ordinary pending work as appropriate. | Do not equate a feasible witness with exhaustive completion of all variants or objective optimization. |
| Normal completed oracle check, no accepted feasible witness | Mark that exact numerical model/variant infeasible for this policy; stop retrying the same completed check automatically. | Do not preserve it as generic unknown solely because the low-level API uses the string `unknown`. |
| Budget ends before a check finishes; cancellation; iteration/resource cap | Pause unfinished work, retaining its model/cursor/warm data as appropriate. Budget exhaustion is not a completed oracle answer. | Do not mark infeasible or trigger recovery solely from the interruption. Do not spin on immediate retries with the same exhausted budget. |
| Worker launch/process/protocol/compiler/evaluation failure | Record an explicit error with stage, model ID and diagnostics; preserve existing validated incumbents and resumable work. | Do not call an error infeasible or quietly turn it into ordinary no-solution search work. |
| Worker claims success but independent numerical replay rejects its claimed witness | Diagnose a validation/model/tolerance mismatch and surface it distinctly. Keep rejection details. | Do not accept the worker flag or silently treat the contradiction as ordinary oracle infeasibility. |
| Numerically valid witness fails a physical scene/contact check | Use the existing physical repair/constraint-variant machinery; reject that physical alternative if necessary. | Do not mistake normal physical alternative refinement for a worker/runtime bug or accept numerical feasibility as scene acceptance. |
| All legal alternatives for the exact queried node are rejected and generation really exhausted | Reject/backtrack that node under the declared domain and oracle policy. | A per-call cap, one failed variant or one failed descendant is not exhaustion of its parent. |

Current implementation facts to fix deliberately:

- `numerics/solver.solve` exposes `unknown` for several distinct cases, including
  a parent time limit and normal worker completion without a validated candidate.
- `search/repair.solve_contact_branches` interprets a whitelist of worker statuses
  as `oracleRejected`; other no-best results enter `unresolved`. The whitelist
  currently includes `Solve_Succeeded` / `Solved_To_Acceptable_Level` even when no
  accepted candidate remains. Inspect rejected-candidate diagnostics before
  treating those as normal infeasibility; the fake-success regression already
  demonstrates why a worker success flag alone cannot be trusted.
- `search/feasibility.outcome` combines completed/pending/in-flight/unresolved and
  generator state; `search/dfs.py` uses the resulting node status for backtracking,
  recovery and suspended work. Classify the actual completion/interruption/error
  before making that decision. Do not test only `best is None`.

Implement a shared search-facing outcome adapter, owned by `search/feasibility.py`
with explicit worker/repair evidence supplied by callers. It must be used by
meaningful-node, anchor and terminal paths; do not scatter new string checks
through the scheduler. Preserve the public numerical API/compatibility paths
unless their contracts are intentionally migrated and tested. New operational
oracle interpretation must participate in exact continuation identity.

## 3. Concrete implementation sequence

| Step | Concrete change | Acceptance / important constraints |
| --- | --- | --- |
| I1 | Expose the existing priority helper consistently for every ordinary work item; retain the shared finish-bound check separately. | Identical `(T,-J,R/A,id)` semantics; lower bounds filter selected work against an incumbent, not rank the queue. One strict `> bestT` predicate; equality stays live. |
| I2 | Define an owned work item with eligibility separate from execution state and accepted-result metadata. | One executable owner per prefix/continuation. Generation cursor, node/anchor query, variant work and interrupted work have explicit locations; no duplicated work records. |
| I3 | Normalize completed-oracle rejection versus interruption/error using the shared adapter above. | A normally completed failed check does not enter a generic retry pool. Interrupted checks remain paused; false worker success remains rejected/diagnosable. A single variant/child failure never rejects an unqueried ancestor. |
| I4 | Interleave ordinary continuations through one logical scheduler using the existing tick key, returning after each meaningful movement/query boundary. | Run0 is not forced to wait for run8 completion. Requeue each proposed child by its own actual endpoint T before a numerical query; do not score it inline using its parent priority. No new per-input/ancestor NLPs or queue-wide scans. |
| I5 | Put former refinement work in the same ordinary scheduler, retaining model/task kind and result metadata. | A feasible incumbent does not hide other branches in a failure-only queue. Do not rerun already completed requests or abandon equal-T Z improvement. |
| I6 | Preserve a separate recovery-only pool, restoration conditions and retirement checks. | Successful finish alone does not reopen it. Paused/error work does not masquerade as a feasibility failure. Restore one at a time, retaining original program/owners. |
| I7 | Persist selected owner, work state, frontier keys/proof revisions and remaining nested cursors; add switch/outcome observability. | Interrupt/resume and JSON round-trip do not repeat completed work or lose candidates. Version scheduler/outcome semantics explicitly. Coordinate callback changes with the bookkeeping branch. |
| I8 | Run deterministic scheduling/outcome tests, native resume/replay checks and an unseeded Real11 comparison. | Demonstrate earlier service for competitive alternatives and fewer wasted final calls, not merely a changed final score or more work fitting the budget. |

Bookkeeping optimization remains independently owned. This session's outcome
mapping may need a narrow edit near the result-classification portion of
`search/repair.py`, while that agent works on snapshot construction in the same
file. Keep those edits isolated; review/cherry-pick conflicts rather than copying
an entire file across branches.

## Current behavior and observed crossover

`search/dfs.py` picks the smallest validated local arrival priority and immediately
pushes a child frame. The stack top keeps service until it finishes, exhausts or
suspends. Other protected incoming histories remain saved at ancestors. There are
service slices, pending/refinement queues and exact resumes, but a slice does not
mean all protected root histories compete for the next globally best continuation.

The [unrestricted current-policy run](../research/initial-domain-selection/README.md)
records the following first chosen paths. These are feasible sampled histories,
not proofs of the best possible arrival for either family:

| Required landing | Initial jump8 | Initial jump0 |
| --- | ---: | ---: |
| B2 |17 |21 |
| B3 |28 |30 |
| B4 |40 |40 |
| B5 |58 |55 |
| B7 |67 |64 |

Run8 jumps at8/19/30/43/58. Run0 jumps at0/12/21/30/40/55; its first jump returns
to B1 at12 and gains momentum before the single B1→B2 transfer. At B2, run8 is
earlier; by B4 the advantage disappears, and by B5 run0 is earlier. The current
trace finishes run8 at67 and services more work on that family before choosing
run0's B2 arrival. A lower early arrival tick does not predict all later jumps.
The shared local comparator correctly keeps run0 protected; scheduling determines
how soon that retained alternative receives service.

## Separate switching, heuristic deferral and bound exclusion

- **Switching:** choose another live continuation for the next work unit. No
  infeasibility assertion or loss of pending work is required. The next available
  ordinary item is selected by the existing tick key, independently of its family.
- **Recoverable heuristic deferral:** use the existing shared compatible-peer
  count rule where its context applies, preserving its protected alternatives
  and explicit feasibility recovery. Do not compare unrelated anchor contexts
  as if their span-local J/R/A were a dominance proof.
- **Bound exclusion:** discard selected work when its optimistic full finish is
  strictly later than a validated complete incumbent. Equality stays live for
  potentially better final Z. Numerical/physical exact-node rejection remains
  a separate existing exclusion path.

A promising run0 prefix is not an upper bound on a feasible complete run0 finish.
Nor does one higher-speed witness prove that the run8 history's entire reachable
state set is inferior: earlier facings are reoptimized on every solve. Without
another proof, switch or defer rather than permanently deleting it.

## Confirmed scheduling direction: one ordinary queue, existing tick priority

The user's correction supersedes the earlier best-bound-first recommendation.
Keep the existing comparator and apply it across all ordinary live continuations:

```text
ordinary:   (absolute T, -span J, span R, stable id)
turnaround: (absolute T, -span J, span A, stable id)
```

Use `search/comparison.priority_key` and the current label/count adapters. Do not
add remaining distance/time to the priority, use a speed/progress scalar, add
aging ahead of this key or reinterpret span-local J/R/A as cumulative path counts.
The hard/non-hard status controls eligibility/deferral, not a second priority.

"Parallel" means logically interleaved progress of all ordinary continuations.
It does not require concurrent numerical processes. After one meaningful jump/
contact decision or its necessary feasibility work, return control so another
ordinary item with a smaller key can receive service. Completing an entire
root-to-finish subtree is no longer the default unit of service. Work may still
be excluded by the shared checks before a numerical request.

### Concrete time-ordered example

Suppose run8 has just reached B2@17 while the ordinary run0 setup hop ending at
B1@12 is waiting. The tick key selects run0's12-input work before continuing the
run8 path. Run0 can then propose B2@21 while run8 proposes B3@28;21 comes before28.
Later the available candidates may be run8 B3@28 and run0 B3@30, so28 goes first.
Both reach B4@40 in the recorded paths. Existing count/mode/identity tie rules,
not an initial-delay preference, decide tied work. They subsequently offer B5@55
and B5@58, so55 receives service first.

These illustrate already available candidates. If the setup hop and first direct
arrival are both available initially, the same key may choose the setup hop first;
there is no requirement to force run8 first. Nor should we hardcode these recorded
clocks or guarantee the same candidate order on every map.

### Correct priority boundary and lazy generation

A cheap expansion and a numerical query are different work. When a parent at17
produces a child ending at28, insert/compare the child at28 before solving it.
Do not let selection of the parent reserve a whole jump, batch or subtree while
an existing child at21 waits. An owned arrival earlier than a proposed unused tail
uses its actual owned endpoint/counts, not the tail's larger horizon.

Preserve resumable generator cursors; do not eagerly materialize every combination
or create an NLP for every raw ground-hold input. The producer must expose the
next proposed meaningful decision or preserve a pending generation task. Specify
how a lazy source advertises an earliest-next-work bound/time so a cursor does
not permanently retain a cheap parent key while doing arbitrary later work.
Producer slices may bound work, but do not invent an alternative numerical-priority
rule. A meaningful node needing a query must yield an owned job rather than being
solved inline inside a generator that bypasses other ordinary continuations.

Each branch retains its real decisions, scope, free-yaw seed and nested repair/
generation cursor. Shared prefixes have one executable owner. Use parent links
for switching instead of cloning the whole tree. Preserve one selected in-flight
job across interruption/resume. Simultaneous worker execution is outside this
initial scheduler change.

## Exclusion and recovery: prevent over-pruning when one family overtakes another

Being selected later by the tick key is not itself a reason to change eligibility.
Keep three different actions and their evidence separate:

### 1. Ordinary scheduling: no removal

A smaller current key selects run0 next. Run8 remains ordinary work unless a
separate eligibility check applies. At equal T, both stay eligible even if one
witness has more speed or a better landing point. A witness is not its history's
entire feasible velocity/position set; all previous facings remain free.

### 2. Hard-span recovery-only deferral: the existing guarded heuristic

Move a particular arrival to recovery-only storage only through the shared
comparison helper, with all of its conditions:

- The relevant span is hard-latched, and the candidate is a complete arrival at
  the same required anchor/milestone. Non-hard work and open prefixes do not
  receive this tick-based heuristic deferral.
- The representative is validated and strictly earlier. Equal T stays ordinary.
- Incoming comparison context, modeled scope, landing phase and frozen ordinary/
  turnaround mode are compatible. Missing metadata retains the candidate.
- Preserve a candidate with **more J OR fewer R normally / fewer A in turnaround**.
- Retain its exact program, owners, nested cursor, representative/decision and
  restoration reason. Restore one at a time on the explicit feasibility-fallback
  trigger; never label it physically infeasible merely because it was slower.

**Current important guard:** `dfs.tick_label` builds comparison context from the
actual incoming parent programs as well as span/goal/score context. Initial0 and
initial8 usually have different incoming histories at later anchors. Reaching the
same block is not enough to make them compatible. Do not drop that guard just to
make an overtaken family disappear; no new cross-history dominance rule has been
approved. The current predicate deliberately keeps different contexts incomparable.

Recovery-only deferral is an agreed heuristic, not an optimality certificate.
Restoring only on infeasibility protects feasibility fallback, but does not prove
that a later arrival could never produce a faster or better-Z finish. A preferred
history might finish feasibly but lose later to a history put in recovery-only
storage. Extending the heuristic to arbitrary incoming histories would increase
that risk. Recommendation: keep cross-history overtaken candidates ordinary unless
an existing compatible hard-span rule or actual exclusion evidence applies.

### 3. Actual exclusion: exact rejection or strict full-finish bound

The existing optimistic finish test is a separate filter:

```text
optimistic_finish = absolute_inputs_used + max(horizontal_lower_bound,
                                             applicable_flight_lower_bound)
exclude only if optimistic_finish > best_validated_complete_finish_T
```

Use `search/bounds/remaining.remaining_bound`,
`search/bounds/flight.transfer_bounds` and
`search/comparison.compare_candidates`. The bound is **not a queue priority**.
It need not be computed before an incumbent exists merely for scheduling.

Pruning uses full landing covers/phase-correct enclosures and valid motion upper
bounds. Vertical terms apply only to learned hard spans under the agreed
no-stepping/ceiling domain; unknown terms contribute zero. Guide-point distance,
one witness's endpoint/speed, or a predicted future successful jump is not a
replacement exclusion proof.

For the recorded case: once run0 validates a complete T64 finish, a run8
continuation at B5@58 with a certified nine-input remaining flight has an optimistic
finish67>64 and can be excluded before its final numerical query. Merely observing
run0 at B5@55 is not a complete feasible64 upper bound. The last transfer might
still require extra preparation or prove infeasible.

Exclude only the scope covered by the evidence. A rejected/late child does not
reject all its siblings or their unqueried parent. To discard the entire initial8
family, a valid exclusion must cover its common prefix or every remaining
continuation in that family. Otherwise retain the other histories. Equal optimistic
finish64 remains eligible because it might improve final Z.

## Ordinary remaining work after a feasible witness

All remaining final/model-variant work is ordinary work, including work after a
feasible finish is known. Do not hide it in a separate eligibility class. Whether
to split final landing feasibility from later MAXZ optimization remains a separate
solve-intent decision; current terminal policy forbids first-feasible stop. If
split later, both are ordinary jobs with explicit intent and correct identity.
Changing service order does not itself authorize new terminal solve intent or
rerunning an already completed fixed query without an explicit unfinished task.

## Implementation difficulty, state saving and overhead

This is a moderate orchestration refactor, not a numerical-model rewrite. The
existing programs, frame parents, generation/repair cursors, in-flight records,
feasible witnesses and shared-store checkpoints already retain the necessary
search state. The main difficulty is exposing meaningful-node queries that
currently execute inside `persistent_programs`, and replacing implicit stack-top
ownership with explicit selected-work ownership without duplication or loss.
`SearchCheckpoint` currently locates active work through `stack[-1].inFlight`;
its contract must follow the selected owner after the scheduler changes.

Separate three operations:

| Operation | Interleaving requirement |
| --- | --- |
| In-memory scheduling | Requeue an owned continuation/reference and select another using its existing priority. Do not copy or serialize the whole tree per switch. |
| Capturing changed continuation state | Update the selected job's cursor/result/owner at a meaningful boundary. Most such state is already explicit; keep unselected jobs untouched. |
| Durable publication | Persist logical dispatch/completion/repair boundaries and interruption state using existing atomic snapshot/patch machinery. A cheap queue selection/reordering does not itself require another full disk checkpoint. |

Exact resume must record queue membership, selected/in-flight owner and completed
work consistently. A compact scheduler change may need to join the existing
publication transaction. Do not save a queue that loses the selected job, or a
completed result with its old runnable entry still present. Durability frequency
is separate from how often scheduling decisions occur; any new publication
boundary must be explicit and tested, not assumed free.

Expected small overhead: heap operations with stable precomputed tick keys,
selected-only eligibility/bound checks, owner/path switching and compact audit
records. Keep keys valid for immutable queued content; rebuild/reinsert when a
new owned child is committed rather than mutating a key inside the heap. Do not
recompute full program digests for every heap comparison or scan the entire queue
when the incumbent changes.

Material risks in the current implementation:

- `search/frontier.persistent_programs` deep-copies a supplied continuation at
  entry. Rebuilding it for every switch could repeatedly clone generation state
  and replay consumed descriptors. Prefer an explicitly owned live continuation
  path for internal service, retaining isolated copies for external resume.
- `search/repair.snapshot` and `spatial/checkpoints.retain_prefix` already copy
  substantial data. More service switches must not introduce another layer of
  whole-tree snapshots. Coordinate any optimization with the bookkeeping branch.
- Activating descendants of several families earlier can increase live frontier
  memory and change cache locality/eviction even though the current solver already
  retains waiting siblings. Share only explicitly immutable data and retain
  per-branch mutable ownership. Do not silently evict ordinary work to save memory.
- Existing dispatch/repair/result publication must remain intact. Do not achieve
  a speedup by dropping durable state, diagnostics or exact-resume guarantees.

Switch only between completed meaningful work units/atomic repair boundaries in
the first implementation. Do not interrupt an active IPOPT process merely to
change families, restart completed solves, or add an NLP solely for switching.
No concurrent numerical workers or per-input OS-style context switches are needed.

Measure fixed-work scheduler/heap/key time, generator reconstruction and repeated
ordinal work, copy/serialization bytes and time, peak frontier memory/cache misses,
durable publication counts/bytes and numerical call sequence. Compare those
separately from first-T64 time and work avoided by earlier finish bounds. The
queue operations should be small relative to preparation/solving, but the net
wall-time/memory effect is an experiment, not a promised speedup.

## Implementation constraints for a future approved change

- Frontier items identify an owning frame/path, candidate, lifecycle phase and
  exact mutable continuation. Keep single ownership and serialized selected job.
  No duplicated executable records for a shared prefix or lost in-flight repair.
- No queue-wide refresh when the incumbent improves. Keep the existing tick
  priority; check the selected job against the latest incumbent/latch/peer state.
  Those checks may exclude or defer it, but do not insert a bound into its
  priority tuple. Preserve frozen decision labels and deterministic ties.
- Bounds for a prefix cover its descendants only under their stated model/domain.
  A child's numerical failure cannot reject its unqueried parent. Selecting a
  sibling reuses compatible cached evidence, not a new ancestor feasibility pass.
- Keep more-J/fewer-R-or-A protections, global hard latches, current-owner hops,
  owned-prefix rows, explicit recovery and no new inter-block revisit generation.
- Queue/scheduler semantics and resume identity must be versioned if they change.
  Preserve supplied/virtual compatibility controls deliberately. Bookkeeping work
  must not change those semantics or silently integrate this proposal.
- Observe active/waiting service, bound components/proof revisions, switches,
  selected branch, new incumbent, actual worker cost and deferred refinement.
  Keep heuristic switch reasons distinct from actual exclusion evidence.

## Suggested acceptance experiments

Generic tests: early-anchor winner loses later; fastest later path has setup hops;
an earlier-priority continuation proves infeasible; two equal-time finishes differ in Z; a
selected branch gains a stronger latch bound; different incoming histories stay
incomparable for heuristic deferral; a slower equal-time-Z contender stays live; switching/resume preserves all
pending work without repeated completed calls; one infeasible child leaves a
shared unqueried ancestor and its other children intact. Verify no incumbent
update scans the full frontier.

Real11 control: same unrestricted case/budgets/physical model. Compare first
validated finish time, first T64 time, worker work spent on run8 while an ordinary
run0 job had a smaller existing tick key, and final result/replays. Do not require a
particular family to win every map or hardcode these recorded arrival ticks.
No runtime scheduler change has been made in this investigation.

## Remaining decisions when this work is resumed

- Exact publication/ownership boundary for lazy generator work and nested repair
  variants: expose next meaningful proposed work before its numerical query while
  keeping the existing tick key. Deterministic priority/count rules are settled;
  there is no remaining decision to replace them with best-bound or aging keys.
- On a per-query time/iteration cap, how much additional service to grant and
  whether the next grant requires explicit resume. Recommendation: keep it paused
  with a recorded resource reason; never silently retry with an unchanged
  exhausted allowance, nor call that interruption a completed infeasible solve.
- Whether terminal feasibility and MAXZ optimization become separate work units.
  This is not required to merge category5 into ordinary work; defer this intent
  change until its benefit is measured and its contract is agreed.
- Exact serialized work-item shape and migration/version strategy. Preserve old
  DFS/supplied/virtual controls explicitly rather than loading their states into
  the new scheduler by matching only an integer schema.

User-confirmed tick priority, hard-only deferral and category/oracle clarifications
above are settled. The choices in
this section are implementation details/recommendations still to resolve. No
runtime behavior is changed by this planning document.
