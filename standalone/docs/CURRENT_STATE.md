# Current standalone state

Verified against `16a7423e`, 2026-11-03. This page and the [current implementation plan](../autonomy/DFS_INTERACTION_IMPLEMENTATION_PLAN.md) orient new work; historical experiment reports describe their source revision. Documentation consolidation changes no runtime.

Latest Real11 user correction, recorded in isolated investigation branch `research/real11-initial-run-ticks`: require an actual B7 landing, minimize its absolute time, then maximize Z at that tick; infer support order from generated feasible decisions. The recorded fixed64 fixture has not been changed. Existing automatic route/window/direction/goal components and landing-time ranking can be reused, but their Real11 integration is unfinished. B6's reported rightward extension is absent from the stored box; exact new bounds are unknown. See [Real11 task/status](REAL11_GUIDE.md) and the linked capability audit. These are new requirements, not shipped solver changes.

## What exists

`ipopt` solves fixed-input continuous horizontal trajectories, with scalar independent validation, subprocess isolation, incumbent retention, optional prescribed contacts/vertical/ladder schedules and replay/export support. `autonomy` builds discrete movement/input/contact programs using existing Java physics and constraint helpers, selects concrete constraint alternatives, invokes IPOPT, and validates against supplied geometry. `cell_routes` decomposes player configuration space and uses cell corridors for high-level guidance. `route_guidance` contains earlier disk/SOCP/mixed-decision experiments, not the selected implementation direction.

Spatial routes restrict broad geometry/topology; multiple ticks can occupy one cell, and multiple schedules can follow one route. Cells do not make time edge weights history-independent. The local orchestrator owns run/jump/landing/stepping/ceiling/blockage choices. Continuous re-solves revisit the entire original trajectory with all prior facings free. Only the branch's discrete decisions are committed.

The seven-workstream change recorded in [orchestrator results](../autonomy/experiments/orchestrator-fixes/README.md) is implemented: explicit solve scope/identity, scoped corridor projection, shared reachability machinery, fixed constraint alternatives before the worker, schedule/state retention protections, generator and terminal repair persistence, and a conservative dead-fall screen. This is not the same as saying every interaction builder uses the helper early enough.

## Known gaps and the approved next change

| Area | Current code | Approved next behavior |
| --- | --- | --- |
| Traversal | Global checkpoint windows; wait for eligible work before advancing; priority by simulated prefix length | Compare siblings at a common spatial goal, follow the most promising to full completion, retain others on a persistent DFS stack |
| Interaction relevance | Shared helper exists, but some builders/truncation paths bypass or apply it late | One eligibility query for every block/tick/collision phase before any interaction description, derived constraint or check |
| Variant generation | Many irrelevant face/gate descriptions; exact dedup too late | Canonicalize effective per-block and partial models before Cartesian products/refinement |
| Scope/projection | Future events can disappear while their earlier approach rows survive | Screen full proposal first; preserve row ownership and only justified prerequisites |
| Local comparison result | Can be used only as a measurement | Adopt actual validated decision program, without pinning witness state or copying temporary comparison rows |
| Persistence | Existing raw generator, repair and terminal continuation | Versioned DFS frames, active branch, siblings, unresolved/scoring/refinement work and incumbent |

Use the [full consolidated plan](../autonomy/DFS_INTERACTION_IMPLEMENTATION_PLAN.md), not a new parallel design. Its first regressions must be generic: translated/reflected geometry, ceiling/stepping/normal cases, a late useful sibling, interrupted generation, resume parity and required-contact preservation. Real11 supplies additional historical counterexamples.

## Requirements that must survive changes

- Inputs and constraints are fixed before each continuous call. Reuse builders; do not redesign IPOPT or reintroduce fractional movement decisions into this implementation.
- Normal momentum with zero inertia thresholds. Later exact inertia-pattern passes may be investigated separately. Zero threshold is not zero friction or zero velocity.
- Time compares candidates at the same spatial goal; fixed-clock progress is a proxy for distance, not fastest route. Compatible schedules permit position/velocity comparison; different ground/jump histories retain schedule protections. Current margins: 0.25 blocks, 1 tick.
- Follow one promising branch deeply; a feasible local arrival is not a lower bound or proof it wins unseen alternatives. Keep comparable-close, protected, unknown and unfinished siblings. Audit heuristic exclusions using full continuations in regression work.
- Required interaction proved unreachable rejects the proposal. Block unable to interact in a phase contributes no avoidance/interaction-derived checks there. Unknown preserves coverage. Swept Y→X→Z phases matter, not just endpoint height.
- All accepted final routes require full scoped geometry and actual terminal objective. Preliminary guidance cannot become a complete incumbent. Keep corridor diagnostics pending evidence for any future policy change.
- No blind cap increases, forced reference seeds, arbitrary scoring weights, inferred external blocks, or Real11-specific constants in generic code.

## What measurements do and do not establish

The current cold Real11 corpus used 208 worker calls across three 300-second runs (~910 seconds total). All 172 main dispatched calls were numerically feasible; cold orchestration still stalled at state28 without a terminal incumbent. Heavy duplicate symbolic generation and breadth/window ordering, rather than general IPOPT failure, are the main identified issues. A known fixed complete program solves and validates as a control; this does not establish cold discovery.

Recorded regression baseline: a wide numerical standalone run had 449 passes and one pre-existing ladder-composition failure out of 450 tests (~742 seconds); IPOPT run had 54 tests, nine skipped, executed tests passed. These are historical measurements, not checks performed by this documentation pass. See [testing](TESTING.md) for scope and baseline failure. See [Real11 guide](REAL11_GUIDE.md) for counts, caveats and artifact links.

## Explicitly deferred

Reuse of completed/sibling **discrete decisions** to seed subsequent branches; a new global objective-bound project; new mixed-integer/SOCP decision search; water; expanding ladder and intentional wall-hit search; universal map-route redesign. Existing ladder and replay modules remain implemented and should not be recreated. Compatible facing guesses remain numerical initialization, never fixed history. A map that refutes weak coupling between separated decisions should trigger measured backtracking/protection changes, not defense of that assumption.
