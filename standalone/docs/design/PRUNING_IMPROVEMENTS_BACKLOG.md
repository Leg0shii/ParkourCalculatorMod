# Deferred pruning and orchestration improvements

## Automatic close-decision grouping and commitment (2026-11-16)

Deferred by the user while the [avoidance-generation plan](AVOIDANCE_PASSAGES.md)
is developed. Future consolidation must:

- Infer nearby/dependent movement-decision groups automatically; require no
  authored opportunity ranges or dependency lists. Define when a group is ready
  for selection and what internal feasibility checks may run before then.
- Unify checking, retaining alternatives and committing choices. Earlier continuous
  values remain free; an early feasible side must not silently erase a sibling
  needed by a later coupled decision. Audit the internal-success cache/repair-state
  boundary and preserve original-state solves and resumable alternatives.
- Replace the old supplied-window/commit paths with one maintained policy.
  Migrate callers and useful tests; remove unused/superseded runtime code instead
  of leaving parallel implementations or an unused compatibility mode. Preserve
  frozen research evidence. The old planner began at `9556c38e`; provenance is
  summarized in the avoidance plan.

The required end state is settled; the grouping/commit details are deferred.
Current solve cadence and anchor policy remain unchanged by this note.

## Known pruning gaps

- **Suffix duration and minimum runs.** For an identical interaction pattern, counts
  currently only affect ordering. Known ordinary suffix clocks are already enough to
  reject the delayed completions in the [virtual-finish call audit](../../research/virtual-finish/PRUNING_AUDIT.md)
  within their unchanged interaction families. Semantic grouping, feasible-representative
  exclusion and early suffix-duration propagation would remove those calls.
- **Remaining-time bound is weak.** A prefix can be shorter than the incumbent and
  still need too much time to finish; default DFS can spend the whole budget inside
  nearer siblings ([virtual-finish evidence](../../research/virtual-finish/README.md)).
  The implemented `T+max(H,V)` bound is in [anchor policy](ANCHOR_POLICY_DECISIONS.md);
  stronger future-chain bounds are deferred item S7.
- **Named collision probes.** The early gate keeps unknown Y and intended unclipped
  X/Z bounds on named step and grind inputs, so it can generate false-positive
  interactions. If that becomes costly, derive conservative probe extents from the
  existing movement implementation rather than reimplementing collision physics.

Measured numerical-row and checkpoint costs are recorded in
[IPOPT constraint reduction](../IPOPT_CONSTRAINT_REDUCTION.md) and
[checkpoint storage](../CHECKPOINT_STORAGE.md); see [performance](../PERFORMANCE.md).
