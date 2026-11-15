# Real11 finite physical-anchor case

`physical-anchors.json` derives from
[the recorded anchor-feasibility fixture](../../research/anchor-feasibility/real11-anchors.json).
It declares ordered anchors, finite extended B7, original state, movement domain
and budgets. The maintained case omits `initialJumpInputs`: the orchestrator
selects initial preparation through existing reachability, hard-anchor tick
comparison and finish bounds. Archived `[0,8]` fixtures are unchanged.

The [unrestricted initial-delay control](../../research/initial-domain-selection/README.md)
found T64 with only0/8 receiving first-anchor feasibility work;9–12 were saved
as recoverable alternatives without numerical calls. This is observed behavior
in that run, not a new generic `[0,8]` rule.

The fixture retains stored B6; the reported wider B6 still lacks replacement
coordinates and is not silently inferred. Use the [runbook](../../docs/RUNBOOK.md)
with an installed solver and native compiler. For local budget/domain experiments
copy the case to a new path and record the change. Old restricted checkpoints
require their original domain for exact resume; the unrestricted domain has a
different context. Supplied-scene acceptance and actual-world/executable-angle
replay remain separate checks.
