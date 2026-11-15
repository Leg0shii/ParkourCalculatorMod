# Search order: total ticks, then objective

2026-10-26. User ruling: minimize the complete route's elapsed tick count, then optimize the numerical objective among candidates with the same count. This supersedes the block-route prototype's first-feasible/default and run-count-first improvement behavior. The ordered run-ticks prototype keeps its original defaults; the block solver selects the stronger search mode explicitly.

## Ranking

```text
total ticks = sum(selected transition durations) + sum(inserted run inputs)
rank for MAX = (total ticks, -objective value)
rank for MIN = (total ticks,  objective value)
```

Run count is accounting, not the primary cross-route objective. A five-input route with one run tick beats a nine-input route with none. At equal elapsed time, a better objective wins regardless of how flight and ground inputs are distributed.

The scene accepts `objective: {"axis":"X","sense":"MAX"}`, with X/Z and MAX/MIN supported. The default preserves the prototype's MAX X at its final state. The objective tick is rebuilt from each candidate's actual length. It is not copied from a route with a different schedule.

## Why retain a shared queue

The native Stratfinder uses a common queue across input-pattern families. Its simplicity/screen ranking does not define our ranking. We retain the common-queue organization so candidates from every route/profile family compete under the same total-time lower bound.

Each entry's bound is its selected flight durations plus the optimistic graph's shortest remaining flight duration. Unknown ground delays contribute a lower bound of zero. A successful parent's observed run count is only a tie-order hint; later constraints can change earlier allocations, so it is not used as a proven time lower bound.

After a complete T-input witness exists:

- A prefix with a completion bound above T is excluded.
- A prefix bounded at T remains eligible for objective improvement.
- An inner prefix search receives at most T minus the graph's remaining minimum flight time. This caps allowed run count without freezing any earlier facings or allocation.
- A newly accepted route replaces the incumbent only for fewer total ticks or a better equal-time objective.

There is no first-feasible or zero-run stopping rule in block-route search. The existing `--improve` CLI flag remains accepted for compatibility; this ranking is always enabled.

## Inner evaluation

For a fixed route/profile, flight time is constant. Minimizing its run count therefore minimizes its elapsed time, but equal-run allocations must all remain eligible. The block solver enables this mode in the existing first-failure/backtracking search instead of pruning allocations equal to the incumbent cost.

Complete goal requests ask IPOPT to optimize the objective rather than stop on feasibility. Earlier prefix probes can still stop at a witness. An objective-directed fallback facing seed lets the existing numerical API explore another inertia pattern when the straight initial seed has zero carry on the transverse axis; the initial accepted seed remains an incumbent. Neither the worker nor the .005 inertia rule is changed.

Contact-face branches retain their best valid time/objective incumbent while evaluating siblings. Every returned candidate still passes independent horizontal validation, strict support/contact checks and the generated Y schedule.

## What is proved

`ticksLowerBound` is the optimistic graph's start-to-goal flight-time bound. `minimumTicksProven` is true when an accepted witness reaches it. Otherwise a bounded search returns the shortest route found without claiming a certificate: lower-time allocation attempts or route prefixes may remain unknown.

`objectiveOptimalityProven` remains false. IPOPT is a bounded local optimizer, and candidate/queue/contact caps can leave other equal-time regions unexamined. `tick_bound_exhausted` means no queued route bound can match or improve the incumbent; it does not convert previously unresolved numerical attempts into infeasibility proofs.

## Normal edge behavior, always enabled

Minecraft checks support before the tick's horizontal movement. That movement can carry the player beyond the edge while leaving the next input grounded and eligible to jump. This is ordinary movement and has no separate transition name. Run-off specifically means leaving the block without jumping and then falling.

The graph's `movementAfterSupportCheck` bound covers the possible difference between the supporting position and the later jump position. It only makes the pruning bound conservative. It does not append an input, invent displacement or change the forward model. The previous graph field was incorrectly named `runoffAllowance`; historical artifacts retain that old metadata name, which does not refer to an actual run-off transition.

An integration regression compiles exactly one run input plus a 12-input ordinary jump. The player leaves the support during the run, is grounded at the next input and jumps there. The accepted path contains exactly 13 inputs and a matching Y schedule.

## Validation

The original ranking checkpoint passed 73 tests with Java coefficient compilation and the actual IPOPT runtime. New tests cover shorter routes beating zero-run routes, equal-time routes comparing objective even with different run counts, MAX/MIN direction, equal-cost run allocations, unresolved shorter routes preventing a minimum claim, and normal edge behavior without extra inputs.

An actual 12-input case optimizes final Z to approximately 1.472712 under MAX Z and -0.472712 under MIN Z. The first profile's initial Z is .5. Both have the same minimum tick count and use the existing continuous model. [Measured cases](experiments/ticks-and-objective/summary.json) retain full scene inputs and search counters. Earlier saved first-feasible artifacts remain historical witnesses, not outputs of the new ranking policy.

Run-offs now join ordinary/stair profiles under the same ranking. Their first input has no jump impulse, and Y-derived fall time and compiled movement constants determine their own reach bound. A half-block-drop fixture selects four added ground inputs plus a four-input fall over a 13-input jump alternative. [Saved run-off cases](experiments/runoff-profiles/README.md) include a mixed jump/fall route and full scalar validation; neither example claims a minimum certificate. Subsequent source-block collisions during Y overlap remain deferred with obstacles/ceilings, so these are plane-model witnesses. A nearer side exit may avoid forward run inputs; [RUN_OFF_OBSTACLES.md](RUN_OFF_OBSTACLES.md) records the required conditions.
