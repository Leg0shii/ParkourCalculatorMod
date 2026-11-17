# Deferred plan: physical block revisits and momentum preparation

2026-11-04. **Deferred by explicit user instruction. Do not implement or generate inter-block revisits in the current anchor-search feature.** Multiple jumps on the current block remain in scope; they are not inter-block revisits. This document preserves the previously discussed revisit analysis and future implementation path so it can be resumed later. The active [decision doc](ANCHOR_POLICY_DECISIONS.md) and [implementation plan](ANCHOR_FEASIBILITY_IMPLEMENTATION_PLAN.md) control current work.

## Current boundary

Current generation permits A→A→A→B and A→air→A→B, but rejects A→B→A. Ignore transient air and compress consecutive same physical owner for the no-revisit check. Optional acyclic supports remain allowed. Required anchors remain ordered, global hard latches remain single-jump, and original-state continuous re-solves keep earlier facings free.

This document is a future design record, not permission to enable revisit families, extra repeat cursors or work on a new search engine now. Revisit deferral is a declared feature/domain restriction, not a physical infeasibility claim.

## When resumed

1. Confirm authorization to enable physical revisits. Remove the active departed-owner filter only for the newly declared supported domain; preserve current same-block setup behavior.
2. Keep ordered support occurrences/physical provenance and required-anchor progress. Incidental returns must not reset the cursor or bypass a required milestone.
3. Use existing positive-duration choices, finite horizon, global hard latches and validated finish-time bound. A latch forbids intermediate-support transfers, while source-local setup hops remain permitted.
4. Reuse exact DFS node feasibility/results/ownership. A failed child never invalidates its untested parent; no independent ancestor-testing mechanism is added.
5. Generate legal revisit choices lazily, then add compact local pair/same-block preparation-family repetition cursors only when measured useful. Validate the fully expanded actual program through existing builders.
6. Service repetition counts incrementally, preserve residual work, and avoid blanket generic visit-count caps or witness-based speed/position dominance.
7. Test useful adjacent-block momentum loops, same-block chains, required-order preservation, different incoming/input/resource histories, interruption/resume and cold performance before default integration.

Expected files: anchor_policy.py for visit eligibility/occurrences; movement_frontier.py/movement_generation.py for lazy choices/family cursors; depth_first.py for actual owners/latch/time-bound integration; solve_spec.py/repair_state.py for exact model identity; focused future revisit-family tests and source-specific experiments. Reuse the current engine.

## Preserved analysis and proposed controls

The following preserves the earlier analysis as **future context**. References to generating revisits apply only after this feature is resumed. Feasibility scheduling in the active plan is authoritative; the older selective-check recommendation is superseded by exact-node solves with a configurable decision-level cadence.

### Does allowing revisits explode combinations?

**It can increase them substantially, but we can support them without enumerating an unbounded route catalogue.** Even without revisits, movement inputs/contacts/timings already form a branching tree. With two admissible continuation choices for each of k steps, there are up to 2^k descriptions; revisits add useful cycles to that existing combinatorial search.

A repeated block is not automatically a duplicate: A→B→A can return with different elapsed time, input history, speed/direction possibilities and physical commitments. Same-block jump chains and sideways bouncing between adjacent blocks may produce useful momentum with little forward displacement. We must not remove them merely because block position or guide progress repeats.

The existing search has a finite maxInputs horizon (stored Real11 200), and [`append_choice`](../../src/parkour_solver/movement/program.py) enforces positive duration for every physical movement choice. Thus no infinite physical cycle exists within the declared finite horizon. Finite does not imply cheap: long alternating sequences can still produce many combinations. [`movement_settings`](../../src/parkour_solver/search/movement.py) also bounds interactions/work allocations; a work slice stops with residual cursors rather than proving no route.

The [event backing graph](../../src/parkour_solver/spatial/event_routes.py) is already cyclic. Its raw silent-closure seen set prevents repeated geometry expansion within one query; that set is not a global ban on physical support revisits. Do not reintroduce a catalogue of all simple/cyclic raw-cell paths just to support A→B→A.

### Recommended controls, in order

1. **Keep revisits as ordinary lazy movement choices.** One coupled span generator creates the next contact/jump choice only when serviced. No eager enumeration of every block sequence. Required-anchor cursor advances only on its next required milestone; visiting an earlier support does not reset it. Required anchor references can remain ordered/unique while physical block visits repeat.
2. **Use time/horizon pruning.** Positive-duration cycles consume maxInputs and, once a finish exists, the validated finish-time bound. Keep equal-time actual finishes for MAXZ. Do not add an arbitrary generic maxVisits=1/2 restriction.
3. **Use the global hard latch.** Once S→E is certified one-transfer, intermediate-support loops are outside the selected transfer domain. Local S→S momentum/setup hops remain allowed on S, including in a latched span. This is a strong requested reduction in revisit branching.
4. **Reuse normal exact-node outcomes.** A designated solve of the exact current DFS node can reject that node and its descendants. Failed children never mark untested ancestors infeasible, and no separate ancestor-testing pass is added. Feasible revisit nodes retain their alternatives; exact shared ownership avoids duplicate solves.
5. **Group loop families for lazy generation.** A same-block hop family or local A↔B preparation family can own a repetition-count cursor and timing/physical-option cursors. Expand an exact k-repeat program only when needed, then validate it with the existing builders. The compact family is enumeration bookkeeping, not a fabricated physical shortcut or cached endpoint transition.
6. **Allocate work incrementally.** Service short repetitions first and keep residual longer-repeat counts for recovery/continued search. Use bounded service slices/iterative widening; report outstanding loop families instead of silently dropping them at a queue cap. This can prioritize the narrow local preparation patterns the user expects without banning other legal revisit paths.
7. **Deduplicate exact models only.** Preserve differences in jump/run schedule, actual prior history, constraint owners, physical case/resource branch, scope and required-anchor cursor. Earlier/faster or same position/block is insufficient to dominate a momentum-preparation revisit. A single feasible witness's velocity is not the entire continuous state set of that program.

A two-block family should be discovered from existing conservative contact/approach possibilities, not a hardcoded perpendicular-axis condition or a fixed Real11 block pair. Pair locality and a small net guide displacement can order candidate preparation families, but cannot serve as physical impossibility proofs. Do not require each loop to increase measured speed: direction changes, positioning and subsequent chaining can matter as well.

### Why this is reasonable, and its limit

For A↔B, repetition k has at least k times the positive loop duration; increasing k is naturally limited by remaining time. A repetition cursor avoids preallocating all k and their siblings. Timing/physical variants can still branch, so this does not turn the general problem into a polynomial search. The practical combination is lazy expansion, early shared-node checks, hard-latched structure, time bounds and resumable allocations.

No new beam width or irreversible visit cap is recommended. If measured revisits dominate runtime, profile family counts, repeated-prefix cache hits and oracle rejections first. Then adjust service allocation or add a separately declared optional repeat limit, with the coverage tradeoff visible. Preserve the broad generic mechanism rather than assuming all future maps use only two adjacent blocks.

### Concrete representation

```text
DecisionNode:
    parent, lastDecision, spanStart, nextRequiredAnchor, absoluteT
    semantic/model identity + physical alternative/scope
    untested / feasible / oracle_rejected / suspended
    pending ordinary choices + lazy preparation-family cursors
```

The node remains the same ancestor for every exact extension. Exact node/model identity carries repeated support **occurrences**, not just a visited set. A family-expanded program is still a real sequence of existing choices with original-state re-solves; it cannot splice yaw/velocity from another repetition count. Shared exact model work may be reused, but the physical history is not collapsed to just `(block, T, J)`.

Revisits inside an unlatched span are allowed, including same-block hops, A→B→A, and returns to completed anchors as incidental physical supports. They do not satisfy future required milestones early or bypass the next required anchor. A repeated required-anchor list is a separate API feature; it is not necessary to support these incidental support revisits.

## Future acceptance and measurements

Use new artifact paths and explicit source/settings. Compare finite-horizon exploration with current acyclic generation under matched task/budget. Record expanded exact nodes, repeated-support occurrences, lazy family counts/residuals, solver calls, feasible/oracle-rejected nodes, final T/Z and independent native validation. Preserve current no-revisit controls. Do not claim all cyclic routes are exhausted merely because a service allocation or repeat cursor slice ended.
