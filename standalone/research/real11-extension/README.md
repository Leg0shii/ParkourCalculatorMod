# Real11 extension: geometry evidence

2026-11-15. Current [geometry and player-bounds report](../../docs/REAL11_EXTENDED_GEOMETRY.md)
is authoritative for the user’s supplied continuation and latest unit-B6 correction.
[Input](../../cases/real11/extended-geometry.json) is geometry-only, not a completed
task with inferred anchors/finish/budgets. [Exact output](player-bounds.json) was
produced with `tools.geometry_bounds`, reusing current production geometry at base
revision `e891f264` plus the new reporting adapter. No production code changed.
The earlier unit/extended-B7 benchmark measurements are separate.

Reproduce with the command in the annotated report. No numerical solve was run.

[Ceiling-preprocessing audit](ceiling-preprocessing-audit.json) records direct
helper calls at source `c241ac57`: B9a/B9b share an underside plane but existing
combination dedup keeps just the first finite box. Reversing the input reverses
the retained box. The audit is source-specific evidence of that coverage gap. No
ceiling-generator/constraint behavior was changed by the investigation.

[Profile-collector audit](profile-dedup-audit.json) independently isolates the
second geometry-blind key: two valid11-tick singleton profiles are offered by an
injected combination iterator, but only one is retained. This probe bypasses
the first dedup deliberately; it makes no numerical/full-scene claim.
The [single face-merging plan](../../docs/design/FACE_MERGING_IMPLEMENTATION_PLAN.md)
contains only detection of adjacent coplanar faces in all six orientations and
representation of their exact unions/source membership for later decisions.
The probes above do not add bug-fix or solver-integration work to that plan.

[Face-detection planning probe](face-detection-plan-probe.json) explores the
recommended physical-boundary rule using existing subtraction/union primitives.
It records input/source hashes, normal-side occlusion conditions, counts and
partial/hidden B9 face examples. This is planning evidence, not a production
face catalog, player-clearance check or route solve.
