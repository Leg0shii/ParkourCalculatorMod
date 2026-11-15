# Real11 finite physical-anchor case

`physical-anchors.json` is a byte-identical promoted copy of
[the recorded anchor-feasibility fixture](../../research/anchor-feasibility/real11-anchors.json).
It declares ordered anchors, finite extended B7, original state, movement domain,
initial-jump inputs `[0,8]` and budgets. It is input data, not a runtime dependency.
The fixture retains stored B6; the reported wider B6 still lacks replacement
coordinates and is not silently inferred.

Use the [runbook](../../docs/RUNBOOK.md) to run it with an installed solver and
native compiler. For local budget/domain experiments copy it to a new path and
record the change. Physical/numerical acceptance concerns the supplied scene;
actual-world or executable-angle replay is a separate check.
