# Real11 virtual finish

The corrected task is earliest descending height7 clock with player-center X<729.7 and Z>=-1095.3, then maximum Z at that tick. There is no goal-imposed lower X or upper Z bound. No artificial support or side walls are added. Normal momentum is retained with zero inertia thresholds, and all facings are reoptimized from the original state.

The [fixture](fixture.json) preserves the seven supplied boxes, required B2→B3→B4→B5 order and initial0/8 decision domain. maxInputs200 is a guard horizon. The runner expands only collision-query coverage using a horizon-derived reach bound, keeping obstacle geometry unchanged. The [initial0 diagnostic](initial0-fixture.json) starts independently with only initial0 allowed; it uses no saved decision prefix or facings.

`terminal_goals` discovers the next descending height crossing from existing scalar replay. A free-air candidate adds only the endpoint horizontal rows; actual finite-support landing candidates remain available too. If a candidate overshoots finite B7 it can finish airborne below Y7; the nominal height clock and actual Y/ground are recorded separately. Every accepted witness must still pass original-scene geometry and independent replay. This abstraction matches the virtual landing objective, not a claim of physically standing on an infinite floor.

Candidates rank by actual terminal tick, then the requested horizontal objective. Once a complete incumbent exists, a fixed program with more elapsed inputs is safely excluded; equal-time alternatives stay eligible for Z optimization. This bound says nothing about earlier incomplete or unresolved programs.

Runtime implementation: `71c9bb7e`. Cold evidence is archived in `evidence/`. Old tick64 evidence is preserved in [DFS interactions](../dfs-interactions/README.md) and is not evidence of earliest virtual landing. See the [runbook](../../../docs/RUNBOOK.md) for commands.

## Results

[Summary](evidence/summary.json), [all 27 worker calls](CALLS.md), [combined cold report](evidence/cold.json.gz), [independent initial0 report](evidence/initial0-cold.json.gz) and [artifact hashes](evidence/manifest.json) preserve exact inputs, constraints, validation and continuation.

| Measurement | Combined initial0/8 cold run | Independent initial0 cold run |
| --- | --- | --- |
| Best discovered finish tick | 67 | **64** |
| Maximum Z found at that tick | −1093.8231291279697 | **−1093.267554656693** |
| Final X | 728.8506329602875 | 728.9505309738937 |
| Actual final Y / on ground | 7 / true, physical B7 landing | 6.796735600668692 / false, overshoot |
| Effective jump inputs | 8,19,30,43,58 | 0,12,21,30,40,55 |
| Required support arrivals | B2@17, B3@28, B4@40, B5@58 | B2@21, B3@30, B4@40, B5@55 |
| First full solution | 100.95s | 59.46s |
| Actual search elapsed | 312.71s | 306.42s |
| Worker calls | 12 | 15 |
| Independent replay checks | 13/13 pass | 21/21 pass |

Neither terminal tick was supplied. The independent initial0 run restricted only the initial family, already part of the combined domain; it loaded no reference prefix or facings. All later discrete choices were generated, and prior facings stayed variable. Its result matches the older seeded reference's Z within about 1.4e-6, now under the explicit virtual goal and independently discovered. This is the best found across these runs, not a global optimality certificate.

Both runs used a 300-second search budget and stopped with saved unfinished work. Recorded elapsed includes progress persistence and post-deadline handling, so it exceeds the nominal budget. Runs overlapped each other and numerical regression checks: timings are observations, not isolated speed comparisons.

The combined DFS follows the earlier B2 arrival, initial8, and spends the budget exploring its B5 siblings before returning to initial0. Thus the default combined run still has the worse incumbent. The separate cold diagnostic proves initial0 is discoverable; it does not prove default traversal reached it or constitute exact continuation of that run. Preserve that distinction when comparing quality. A faster early arrival can lead to a later final finish, so keeping the root alternative matters.

The simple safe bound excludes programs whose elapsed inputs already exceed the incumbent finish (4 and 8 recorded exclusions), while retaining equal-time alternatives. It cannot yet rule out an earlier prefix that needs too much remaining time. Deriving stronger generic remaining-time bounds is preferable to blindly raising candidate caps; no such bound or traversal change is introduced here. Numerical unknowns and unfinished symbolic/repair work prevent minimum-time or maximum-Z certification. The inner `lower_bound_reached` label in the request ledger does not certify global route time.

## Validation

All 372 selected non-ladder autonomy tests passed in 202.64s with the native compiler/IPOPT environment; [full test log](evidence/nonladder-tests.log). The selection excludes test IDs containing ladder/climbable/hanging_vines, as in the earlier baseline. Some remaining composition tests still exercise shared movement machinery. The focused 42-test DFS/continuation/fixed-clock/virtual-goal run also passed. The expanded virtual-goal checks cover strict left bounds, actual airborne overshoot, exact saved-state resume and rejection of a changed goal context; [focused log](evidence/terminal-resume-tests.log). All 27 archived numerical requests reproduce their recorded problem fingerprints. No mod, loader or continuous worker equations changed.

## Pruning clarification after the call audit

[Answers to the pruning questions](PRUNING_AUDIT.md) explain cold10–12, initial0 5–8 and9–15, with [offline checks](PRUNING_AUDIT_CHECKS.json). The current elapsed-time cutoff rejects the slow terminal programs but does not propagate their nine-input ordinary suffix before prefix solves. The clarified minimum-run rule within the same interaction pattern is **not implemented**: run count currently orders DFS work, and timing-sensitive family keys preserve delayed copies. These omissions explain the unnecessary calls; they should not be presented solely as a need for a larger budget or a more elaborate global bound. The audit changes documentation only.
