# Real11 finite physical-anchor case

The new [extended anchor task](extended-anchors.json) uses the supplied longer
course, original unit B6/B7, B7/B20 additional intermediate anchors and terminal
B22. The [fixed-B5 experiment](../../research/real11-long-solve/README.md) retains
the initial-0 movement decisions through B5@55 while re-solving all facings from
the original B1 state. The trials validate B7@64; corrected continuations are
saved with B11@68 constraint work pending, without a B20/B22 finish.

The longer-course [extended geometry](extended-geometry.json) and
[bounds report](../../docs/REAL11_EXTENDED_GEOMETRY.md) are now recorded separately.
They keep B6 at its original unit footprint, restore unit B7 and add B8–B22
(including B9a/B9b and the compound B13 stair). This file is geometry-only;
the geometry file itself does not supply a task. The extended anchor task above
now records the selected milestones and finish with the existing horizon/caps.
It can be inspected with `python -m tools.geometry_bounds`.
Add `--faces` to include the merged physical-face catalog; the existing bounds
remain available in the same report.

`physical-anchors.json` derives from
[the recorded anchor-feasibility fixture](../../research/anchor-feasibility/real11-anchors.json).
It declares ordered anchors, finite extended B7, original state, movement domain
and budgets. The maintained case omits `initialJumpInputs`: the orchestrator
selects initial preparation through existing reachability, hard-anchor tick
comparison and finish bounds. Archived `[0,8]` fixtures are unchanged.

As of 2026-11-16, the maintained movement domain enables `ceiling` alongside
`normal` and `stepping`, with `ceilingHits: true`. Ceiling choices use the same
movement proposal/composition, decision validation and constraint/solver path;
they do not require a declared anchor. The existing type order is preserved,
with ceiling choices appended. All seven supplied boxes start at Y=0, so their
undersides do not provide head-hit opportunities on the above-ground transfers.
Enabling the family does not create new geometry. This domain change requires
a fresh run; older checkpoints must use their original input to resume exactly.

Validation for the enablement: 27 tests passed with native compiler/worker and
zero skips across ceiling contacts, generic composition, ceiling-aware transfer
bounds and package layout. Direct ceiling-generation checks at the original
state and captured History B states17/28/40/58/67 returned zero choices. These
are configuration/generation checks, not a new cold Real11 performance run.

The geometry-driven avoidance replacement removes local repair/passage caps;
the maintained case omits their obsolete fields. Source-era inputs remain readable,
but normalization removes those values. Global time/call budgets and per-request
numerical timeouts apply. See the [implementation and measurements](../../research/geometry-passages/README.md).
Old face-generator continuations require their original source checkout.

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
