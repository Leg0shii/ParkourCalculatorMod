# Real11 finite physical-anchor case

The longer-course [extended geometry](extended-geometry.json) and
[bounds report](../../docs/REAL11_EXTENDED_GEOMETRY.md) are now recorded separately.
They keep B6 at its original unit footprint, restore unit B7 and add B8–B22
(including B9a/B9b and the compound B13 stair). This file is geometry-only;
the longer task's ordered anchors, finish and budgets have not been specified.
It can be inspected with `python -m tools.geometry_bounds`.
Add `--faces` to include the merged physical-face catalog; the existing bounds
remain available in the same report.

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

The benchmark fixture retains stored B6 and extended B7. The latest longer-course
input explicitly keeps unit B6 and unit B7; do not apply old benchmark claims to
that revised geometry. Use the [runbook](../../docs/RUNBOOK.md)
with an installed solver and native compiler. For local budget/domain experiments
copy the case to a new path and record the change. Old restricted checkpoints
require their original domain for exact resume; the unrestricted domain has a
different context. Supplied-scene acceptance and actual-world/executable-angle
replay remain separate checks.
