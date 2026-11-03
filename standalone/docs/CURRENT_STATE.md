# Current standalone state

## Current requested terminal goal: virtual B7 landing (2026-11-03)

The user clarified the intended finish after the fixed-clock benchmark. At the B7-height landing event (height7), use the horizontal goal **X < 729.7 and Z >= -1095.3**, with no goal-imposed lower X bound or upper Z bound. The region is a virtual landing target extending indefinitely toward +Z, not B7's original finite support footprint. Its coordinates are player-center bounds; do not expand them by the hitbox again.

The task objective is lexicographic: **minimize the achieved landing tick T, then maximize Z[T] among candidates at that earliest tick**. T is discovered, not fixed to64. Preserve normal momentum, zero inertia thresholds and original-state continuous re-solves. Use existing numerical clearances for strict inequalities.

This goal does not authorize adding artificial side walls or treating an enlarged goal footprint as new collision terrain during the approach. Supplied physical obstacles and the virtual terminal condition remain distinct. Finite implementation envelopes must come from reach/horizon bounds, not arbitrary goal-coordinate caps.

The generic `terminal_goals` adapter and the [new fixture](../autonomy/experiments/virtual-finish/fixture.json) now implement this objective. It discovers the downward height-crossing tick from the existing scalar movement model, adds only endpoint horizontal rows and preserves actual collisions with the supplied scene. An overshooting trajectory can end below height7 and airborne: this is a nominal landing-height clock, not an artificial collision floor. Actual Y and ground state are reported separately. Ordinary finite-support landing choices remain available. The 200-input horizon is a search guard, not a fixed finish. [New cold evidence](../autonomy/experiments/virtual-finish/README.md): combined0/8 DFS finds67 (Z≈−1093.82313); a separate initial0-only cold diagnostic finds64 (Z≈−1093.26755), with no reference seeds. All34 replay checks pass. The combined run still spends its budget in the initial8 subtree, so do not present the diagnostic as default traversal success. Neither run proves the earliest possible tick. All372 selected regression tests pass. The archived91.22s MAXZ-at64 result remains historical.

2026-11-03. Runtime implementation boundary: `498c054c`; additive request/branch logging: `072a1111`. [DFS/interaction results](../autonomy/experiments/dfs-interactions/README.md) records current validation and exact cold/resume evidence. Earlier experiment reports describe their own revisions. The root AGENTS files remain unchanged.

Latest Real11 user correction, recorded in isolated investigation branch `research/real11-initial-run-ticks`: require an actual B7 landing, minimize its absolute time, then maximize Z at that tick; infer support order from generated feasible decisions. The recorded fixed64 fixture has not been changed. Existing automatic route/window/direction/goal components and landing-time ranking can be reused, but their Real11 integration is unfinished. B6's reported rightward extension is absent from the stored box; exact new bounds are unknown. See [Real11 task/status](REAL11_GUIDE.md) and the linked capability audit. These are new requirements, not shipped solver changes.

## What exists

`ipopt` solves fixed-input continuous horizontal trajectories, with independent scalar validation, subprocess isolation, incumbent retention and prescribed contact/vertical/ladder support. `autonomy` builds discrete movement/input/contact programs with existing Java physics and constraint helpers. `cell_routes` decomposes player configuration space and guides a selected spatial corridor. Earlier disk/SOCP/mixed-input experiments remain in `route_guidance`; they are not the selected search implementation.

The seven earlier workstreams remain: explicit solve scope/identity, scoped corridor projection, shared reachability, fixed constraints before numerical solves, state/schedule retention helpers, generator/terminal repair persistence and conservative dead-fall exclusion. The current change fixes their incomplete early wiring and replaces outer traversal.

## Implemented DFS and early interaction changes

| Area | Current behavior |
| --- | --- |
| Traversal | `find_checkpoint_route` delegates to `depth_first.search_goals`. Compare arrivals at a common spatial goal, then follow the chosen child deeply. No global checkpoint-window drain barrier. |
| Goal ordering | `(validated absolute arrival, -effective grounded jumps, grounded nonjump runs, stable identity)`. Shorter simulated prefixes organize local generation only. Explicit initial-jump families are compared before selection. |
| Scoring | Fixed goal-contact programs have known schedule durations. Evaluate necessary earlier alternatives; save later arrivals and optional refinements rather than waiting for all numerical work. First feasibility still requires scene/vertical/corridor acceptance. |
| Candidate promotion | Continue the actual validated arrival program plus selected hard constraints. Earlier facings and the original horizontal state remain free/fixed respectively; no optimized endpoint is frozen. |
| Terminal work | Test existing normal jump/run-off air continuations first, then retain generic symbolic terminal generation and refinements. A direct continuation has the same full constraints/checks as other candidates. |
| Early interaction gate | `interactions.InteractionGate` uses compiled original-state prefix/approach envelopes before open normal/stepping/ceiling builders expand block rows. Required full-proposal checks, avoidance phases, floor checks and derived corridor gates use the shared query. Unknown keeps coverage. |
| Generation | Per-obstacle partial nodes/options and cross-obstacle partial effective models dedup before expensive leaf refinement; resource distinctions remain where relevant. Complete keys are checked before LP refinement. |
| Ownership | Generated row bundles carry owner completion states. Full proposals are screened before truncation; uncommitted removed events lose their approach rows. Promotion cannot copy those orphan rows back from a longer witness. |
| Retention | DFS preserves scored siblings, unscored proposals, unknowns, inner search and accepted constraint variants. It now excludes extra ordinary ground runs before the latest jump within an otherwise matching interaction pattern. Other peer exclusions remain inactive. Existing position/velocity/schedule helpers remain available. |
| Persistence | Version5 `ordered_goal_dfs` state stores frames, active/in-flight work, generators, siblings, refinements, suspended work and incumbent. Numerical progress does not change candidate identity. Each unfinished frame has one execution owner. |

The [approved plan](../autonomy/DFS_INTERACTION_IMPLEMENTATION_PLAN.md) remains the design record. [Architecture](ARCHITECTURE.md), [code map](CODE_MAP.md), [runbook](RUNBOOK.md) and [testing](TESTING.md) describe interfaces and validation. Spatial goals no longer require corresponding absolute-clock windows; supplied clocks remain diagnostics. A fixed-clock task does not imply a final landing.

## Latest pruning audit (2026-11-03)

The [virtual-finish pruning audit](../autonomy/experiments/virtual-finish/PRUNING_AUDIT.md) traces every questioned call. The selected fix is now implemented at `db211b2b`: within the same prior decisions and latest-jump interaction pattern, use the least-run validated feasible representative to skip extra-run copies. Counts are local to that jump (B4@40→jump43 means3), not cumulative15. Representatives/excluded programs persist and unknowns/equal-run alternatives remain eligible. [Implementation and validation](../autonomy/experiments/last-jump-runs/README.md): all52 focused native regressions pass, no skips. The user has reauthorized a fresh Real11 retry after pushing the [other-agent handoff](CORRIDOR_AGENT_HANDOFF.md); corridor/cell work remains with that agent. Additional continuation-time reasoning remains explicitly **deferred**; the existing elapsed-input cutoff is unchanged. Equal-time free/physical B7 contact alternatives remain distinct; initial0 calls5–8 are two such models, each with one corridor repair. The audit now explains B3's X=738.5 reference plane in ordinary coordinates.

## Durable requirements

- All inputs, events and hard constraints are fixed before IPOPT. Reuse builders; no new fractional decisions or continuous equations.
- Re-solve from the original position/stored velocity with all previous facings variable. Carry ordinary momentum with zero inertia thresholds.
- Compare time at the same spatial goal and objective at a compatible common clock. Keep current 0.25-block/1-tick margins; don't invent a weighted score. Preserving all DFS siblings also preserves agreed state/schedule protections.
- Proved impossible required interaction rejects its proposal; proved absent contact contributes no prevention choice or interaction-derived check. Unknown is not feasible, infeasible or dominated. Swept Y→X→Z phases matter.
- Full terminal acceptance requires the declared scene/horizon/objective. A preliminary witness cannot become a final incumbent. Keep physical and corridor acceptance distinct, with diagnostics.
- Generic algorithms, no Real11 IDs/ticks/coordinates in production rules, no reference seeds in cold discovery, no silent domain/cap increases.

## Evidence and remaining limits

The archived pre-DFS corpus stalled at state28 after208 worker calls over~910s; all172 main numerical calls were feasible. A first DFS diagnostic reached a full state64 solution through the delayed setup in eight calls, independently replayed. Current source-matched cold/resume measurements live in the [results](../autonomy/experiments/dfs-interactions/README.md); distinguish them from that earlier diagnostic and seeded reference controls.

DFS chooses the most promising **evaluated** candidate, not a proven globally earliest unseen arrival. Local generation and catalogue caps can suspend work; numerical unknowns remain unknown. There is no new admissible global objective bound. A completed incumbent does not prove slower local arrivals lose the final objective. Search inside one branch can still be large, and a root fallback can remain pending while closer siblings are explored. State/report size grows with retained evidence.

The gate is available whenever a compiled movement context exists; contexts without it retain unknown choices. The approach gate leaves post-contact Y unconstrained before a stepping/ceiling outcome is chosen, then checks the full proposed model. Existing interval/LP screens are conservative/numerical respectively, not rational certificates. Complete acceptance and original-state replay remain essential.

One supplied corridor (or the first automatically enumerated corridor) is searched per call. No claim of all-map, all-catalogue or global continuous optimality follows. Exact executable float-angle replay/export and actual-world validation remain separate from standalone continuous/scene acceptance.

## Deferred

Discrete decision transfer between completed/sibling solves; new global objective bounding; new mixed-integer/SOCP decision search; water; ladder/intentional wall-hit expansion; redesign of all spatial route enumeration. Existing ladder/replay code stays implemented. Weak coupling between separated decisions is provisional; use retained alternatives and evidence when a map contradicts it.


Named step/grind events need special care in **the shared envelope**, not separate relevance rules. Realized Y endpoints omit the movement probes that trigger those cases. Such phases keep Y unknown and include intended, unclipped X/Z movement in their outer bounds. The existing case builders and full scene checks decide validity. This restored the generic five-input grind→air→blip regression, which an overly tight realized-Y query had incorrectly removed. Precise probe-height envelopes are a possible later performance refinement; never infer vertical noninteraction for these phases from realized feet height alone.


Final recorded acceptance:368 non-ladder autonomy tests and54 IPOPT tests pass;25 post-logging repair/terminal tests pass. Cold Real11 reaches a first full route in91.22s/eight calls; two150s sessions make14 calls total with zero repeated completed requests and all10/18 witness replays passing. Best Z64 is−1094.5755032869683. The zero-run root alternative remains pending; the known seeded reference is about1.308 blocks farther. See source-specific logs/results rather than treating this as global optimality or a fully green ladder-inclusive suite.
