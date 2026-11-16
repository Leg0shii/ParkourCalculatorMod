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
the retained box. The annotated report explains current partial simplifications,
this coverage gap and the proposed role-specific face-union extension. No
ceiling-generator/constraint behavior was changed by the investigation.

[Profile-collector audit](profile-dedup-audit.json) independently isolates the
second geometry-blind key: two valid11-tick singleton profiles are offered by an
injected combination iterator, but only one is retained. This probe bypasses
the first dedup deliberately; it makes no numerical/full-scene claim.
The [single implementation plan](../../docs/design/CEILING_FACE_PREPROCESSING_IMPLEMENTATION_PLAN.md)
now owns the proposed fixes, shared ceiling representation and acceptance.
