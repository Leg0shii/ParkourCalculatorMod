# IPOPT constraint cleanup

Implemented 2026-11-03. Both numerical row reduction and CasADi simple-bound extraction are enabled in every `worker.fixed_phase`. The orchestrator still chooses all inputs, contacts and constraints before solving. Request contents, ownership metadata, hashes, resume contexts and the parent validator remain unchanged.

## Numerical rows

[constraints.reduce_walls](../src/parkour_solver/numerics/constraints.py) constructs a worker-local list without mutating the request:

- For absolute X/Z inequalities on the same state, retain the largest GE bound and smallest LE bound separately.
- For other constraints, remove only exact duplicates of the expression/comparison/RHS. Diagnostic names and ownership metadata do not distinguish numerical expressions; the complete originals remain in the request.
- Preserve different states, axes, relative operations, RHS values and equalities. Inconsistent bounds are not replaced with a feasible one. No coordinate rounding occurs.
- Facing bounds wrap at180 degrees. Different facing RHS values are **not** reduced by scalar bound ordering. Relative/cross-axis rows likewise retain distinct restrictions.

`phase.wallReduction` records `original`, `retained`, `representatives` (original row indices), and `sourceIndices` (the original indices covered by each retained row). This preserves the provenance of exact duplicates and weaker bounds. `result.problem.constraints` continues reporting the original request count. The row reduction is numerical assembly, not candidate pruning or a new equivalence relation for search/resume.

## Bound extraction and monitor correctness

[worker.fixed_phase](../src/parkour_solver/numerics/worker.py) sets `detect_simple_bounds=True`. The [CasADi API](https://web.casadi.org/api/html/d4/d89/group__nlpsol.html) describes extraction of single-variable constraint entries into variable bounds. This does not relax the model or change inertia, motion equations, contacts, facing choices or tolerances.

The old monitor compared the returned `g` vector to the original bounds. The earlier CasADi3.8.1 probe showed that extraction could produce a scalar-valid final point whose returned vector failed that comparison. `Monitor` now evaluates a saved CasADi function of the **unextracted assembled constraint expressions at x**. It uses this on iteration callbacks and the final solver result, retaining the same1e-8 nominal feasibility threshold. Callback sparsities still match the declared NLP. The parent independently reconstructs the trajectory and checks **every original requested constraint**, actual inertia/contact behavior and existing acceptance rules.

Phases report `simpleBoundExtraction: true` and `constraintRowsBeforeExtraction`, the number of assembled motion/guard/wall scalar rows **after numerical row reduction but before extraction**. It is not the backend's remaining general-constraint count. A successful local solve is still not a global optimality or infeasibility certificate.

## Tests and measurements

- **63 IPOPT tests pass**, native worker enabled, no skips, including all legacy capture, warm-start, inertia, ladder, relative/facing/nonlinear, cancellation and seed-retention checks.
- **109 src/parkour_solver tests pass**, compiler/IPOPT enabled, no skips: checkpoint/store/repair continuation, DFS, local-run pruning, terminal goals, stepping, ceilings and contact combinations.
- New [pure reduction tests](../tests/unit/numerics/test_constraints.py) cover source mapping, contradictory bounds/equalities, angular/relative distinctions, exact float values, input immutability and translated random trajectories with known feasible controls.
- New [native worker tests](../tests/unit/numerics/test_worker_bounds.py) cover a misleading supplied constraint vector, nonlinear expression checks, mixed extracted/non-simple rows, final-point preservation with iteration reporting disabled, and feasibility early stopping. Returned candidates are checked against the full original request.

The [production probe](../research/last-jump-runs/row-benchmark/production_bounds_probe.py) compares four configurations of the same archived67-input NLP over five rotated repetitions. All use the corrected monitor. [Results and source hashes](../research/last-jump-runs/row-benchmark/production-bounds-results.json):

| Configuration | Wall rows | All scalar rows before extraction | Iterations | IPOPT-call median | Graph + setup + first solve median |
| --- | --- | --- | --- | --- | --- |
| Baseline, both optimizations disabled |278 |546 |17 |12.87ms |47.12ms |
| Row reduction |91 |359 |11 |7.17ms |34.00ms |
| Extraction |278 |546 |11 |6.56ms |37.59ms |
| Production, both enabled |91 |359 |11 |6.21ms |32.54ms |

Three fresh production workers finish in174–177ms, with both phases completing. All23 solves satisfy every original row; first-phase objective spread across configurations is1.09e-9. The200-iteration cap does not bind. This is a fixed-request benchmark, not a new Real11 route search or a measured overall search speedup. Startup/generation/scene checks and historical request storage still have their existing costs; original rows intentionally remain in audit/checkpoint data. [Checkpoint cleanup](CHECKPOINT_STORAGE.md) is separate and already implemented.

The earlier `extraction_probe.py` and its saved failure evidence document the **pre-fix monitor**. Reproduce that specific failure at its recorded source; the current production monitor is fixed. Use `production_bounds_probe.py` for current-code comparisons.
