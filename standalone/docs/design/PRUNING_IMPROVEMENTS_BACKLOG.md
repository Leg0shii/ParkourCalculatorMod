# Deferred pruning and orchestration improvements

## Automatic close-decision grouping and commitment (2026-11-16)

2026-11-17 clarification: these are separate workstreams. The user defines
automatic grouping as solving coupled constraints together and has requested an
implementation suggestion. Commitment remains explicitly deferred. See G1/G2
in the [register](../DEFERRED_WORK.md); no runtime implementation is authorized
by this discussion.

### Automatic grouping: joint constraint solving

Use actual movement/contact/avoidance requirements, without authored opportunity
ranges or dependency lists. The fixed-program path already assembles contact
regions and avoidance alternatives in one `ConstraintVariants` iterator, conditions
later items on accumulated rows and solves the complete joint alternative.
See [assembly](../../src/parkour_solver/search/engine.py),
[joint traversal](../../src/parkour_solver/constraints/variants.py) and
[three-way conflict control](../../tests/unit/constraints/test_encounters.py).

The remaining design discussion must identify a concrete consumer that still
separates coupled requirements, or a distinct need to assemble several movement
choices before a query. Shared momentum/facings mean separated encounter windows
are not proof of independent models. Preserve original-state joint solves;
grouping alone does not change solve cadence, select winning alternatives or
fix intermediate states.

### Commitment: deferred

When choices become fixed, which alternatives remain open downstream, and how
checking/retention/commitment are unified remain deferred. An early feasible
side must not silently erase a sibling needed by a later coupled decision.
Audit the internal-success cache/repair-state boundary and retain resumable work
when this workstream is selected. Remaining P7 downstream-open choices belongs
to this discussion, not automatic grouping alone.

Replace supplied-window/commit paths when their replacements are implemented;
migrate callers/useful tests and remove superseded runtime code. Preserve frozen
evidence. The old planner began at `9556c38e`; provenance is in the avoidance
plan. Current cadence, anchor comparison and retention remain unchanged.

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
