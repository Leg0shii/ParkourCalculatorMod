# Branch scheduling: recognize stronger continuations before finishing a subtree

2026-11-15. User direction: reconsider strict depth-first completion. We should
not finish/refine the run8 subtree before examining run0 when evidence makes run0
more promising. This document is an investigation/recommendation, **not an
implemented scheduler or permission to change comparison semantics silently**.
Bookkeeping optimization has a [separate agent handoff](BOOKKEEPING_PERFORMANCE_AGENT_HANDOFF.md).

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
  infeasibility assertion or loss of pending work is required. An optimistic
  estimate or bounded lookahead can justify this scheduling choice.
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

## Recommendation: global best-bound scheduling, with local continuation

Use the existing remaining-time relaxation to define a candidate finish estimate:

```text
optimistic_finish = absolute_inputs_used + remaining_time_lower_bound
remaining_time_lower_bound = max(horizontal_lower_bound, applicable_flight_lower_bound)
```

Make retained live continuations compete by this estimate before a subtree is
completed. The existing discrete tick key can order comparable local work/ties;
add deterministic service/aging handling so ties do not starve a protected family.
Use parent links to switch paths at their common ancestor and preserve each
branch's exact continuation, rather than copying the entire search tree per job.
The movement/repair logic can still make local depth-first progress between
scheduler checkpoints. No fixture-specific preference for initial jump0 is needed.

This is A*-like in its `g+h` ranking, and branch-and-bound once feasible complete
incumbents exist. Do not claim a textbook A* optimality guarantee: current hard
latches/domain restrictions, bounded enumeration and operational numerical oracle
already constrain that claim. Heuristic scheduling and proof-producing exclusions
must remain distinguishable in records and tests.

### Reuse the existing bound, including before an incumbent exists

`dfs.time_bound` currently returns immediately when `state['best']` is absent.
Factor bound **construction** from its incumbent **comparison**. Scheduling needs
an optimistic estimate even before the first finish. Reuse
`search/bounds/remaining.remaining_bound`, `search/bounds/flight.transfer_bounds`
and `search/comparison.compare_candidates`; do not create another duration formula
or another strict-time predicate.

Pruning uses full landing covers/phase-correct enclosures and valid motion upper
bounds. Soft guide-point distances and current optimized witness positions are
not replacement lower-bound proofs. Vertical terms apply only to already learned
hard spans under their declared no-stepping/ceiling domain; unknown terms retain
zero contribution. Never assume future latches just to make ranking more precise.

### Concrete example once future hard spans are certified

Suppose B2→B3→B4→B5→B7 hard-flight minima are9+10+15+9=43:

- Run8 at B2@17 has optimistic finish at least60, not an established finish60.
- Run0 at B2@21 has optimistic finish at least64, not a feasible upper bound64.
- Run8 at B4@40 has flight-only estimate64; run0 still at B2@21 can now compete
  with estimate64. A global scheduler need not finish run8 first.
- Run8 at B5@58 has estimate67. A run0 continuation with estimate64 should get
  service first; it may then validate a complete64 finish.
- Once complete64 is validated, the run8 B5@58 continuation has58+9=67>64 and
  can be excluded **before its final numerical solve**.

Horizontal terms can strengthen these estimates. Before those hard spans are
certified, use only known contributions. If run0's optimistic64 continuation
fails, run8 remains available; lower estimates alone cannot establish feasibility.
Likewise, ruling out every run8 path requires a bound for every live run8
continuation, or a valid bound covering their common prefix. One slow B5 arrival
does not invalidate all siblings that share the initial delay.

### What earliest detection requires

1. Materialize and validate protected alternative arrivals when their selection
   becomes competitive; do not let the earlier arrival own all later service.
2. Refresh the selected continuation's finish estimate after meaningful decisions
   and anchor arrivals, then let other live work compete. Do not add per-input
   NLPs or separate solves on unselected ancestors.
3. If estimates remain too weak, give competing protected families a bounded
   lookahead/service quantum at meaningful boundaries. Continue the promising
   family, preserve the other, and log the switch reason. This changes ordering,
   not physical feasibility or the existing protection rule.
4. Measure opportunity before changing bound strength: record when run0 was live,
   its estimate versus selected run8 work, which later jump caused the reversal,
   and how much terminal optimization/refinement was spent before switching.
   A trace-based counterfactual is a hypothesis; confirm with an unseeded run.

Avoid spending repeated MAXZ refinement on a time-disadvantaged family while a
competitive protected time-search continuation is waiting. Whether to split final
landing feasibility from subsequent MAXZ optimization is a separate solve-intent
change to decide explicitly; current terminal policy forbids first-feasible stop.
It would need correct identity/versioning and equal-T objective tests, not a
silent reuse of intermediate-solve intent.

## Implementation constraints for a future approved change

- Frontier items identify an owning frame/path, candidate, lifecycle phase and
  exact mutable continuation. Keep single ownership and serialized selected job.
  No duplicated executable records for a shared prefix or lost in-flight repair.
- No queue-wide refresh when the incumbent improves. Store optimistic keys and
  recompute/recheck only selected work. If latch/proof revisions strengthen its
  estimate, reinsert that item as needed; do not scan every queued program.
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
optimistic favorite proves infeasible; two equal-time finishes differ in Z; a
selected branch gains a stronger latch bound; switching/resume preserves all
pending work without repeated completed calls; one infeasible child leaves a
shared unqueried ancestor and its other children intact. Verify no incumbent
update scans the full frontier.

Real11 control: same unrestricted case/budgets/physical model. Compare first
validated finish time, first T64 time, worker work spent on run8 after a competitive
run0 continuation was available, and final result/replays. Do not require a
particular family to win every map or hardcode these recorded arrival ticks.
No runtime scheduler change has been made in this investigation.
