# Intermediate feasibility: objective, stopping and worker dispatch

Current physical-anchor policy: decision checks retain the configured onward
objective and stop at an independently valid feasible witness; anchor legs and the
final leg optimize, because choosing between alternatives needs their objectives. Objective
selection and early stopping are separate settings. The [execution reference](ANCHOR_SEARCH_EXECUTION_REFERENCE.md)
shows which generated decisions reach numerical preparation and why. The
[integration contract](ARCHITECTURE.md) records how the earlier
solver-branch constant-zero default was replaced.

## Current numerical contract

| Purpose | `objectiveMode` | `stopOnFeasible` | Result / worker behavior |
| --- | --- | --- | --- |
| Selected internal completed decision | `configured` | `false` | Exact-node guided optimization of the program's decision objective from the original state, preserving earlier free facings; pending end-anchor predicate is absent. Climbs every side family; the best is the branch's [intermediate choice](design/AVOIDANCE_PASSAGES.md#side-families-in-search-branches) (exact rows of the stretches settled at the node), the other choices are held alternatives. |
| Required-anchor arrival | `configured` | `false` | Optimize the onward E→N direction over the alternatives the switch-tick climb visits, rank them, keep the best plus up to two close fallbacks, with physical validation. |
| Final physical landing | `configured` | `false` | Optimize real configured terminal objective for candidate T. Outer ranking minimizes actual T then maximizes objective at equal T. |
| Explicit optional constant-zero control | `constant_zero` | `true` | Retained API mode: minimize0 under the same fixed movement/contact constraints. It is not the current anchor default. |

`numerics.solver.solve(request, stop_on_feasible=True)` overrides the stopping flag;
it does **not** implicitly change `objectiveMode` to constant zero. To request the
optional zero objective, set `objectiveMode: constant_zero` explicitly. The model
accepts only `configured` or `constant_zero`; constant zero requires early stopping.
The default objective mode is configured.

A valid seed returned by the wrapper need not improve onward progress. If a
worker runs, the directional objective guides its path until a scalar-valid
feasible point is accepted. Neither early stopping nor IPOPT feasibility replaces
vertical/event and supplied-scene acceptance at the owning search scope.

The optional constant-zero NLP is `min 0` subject to the original fixed movement,
rows and contacts. It adds no slack, regularizer or softened constraints. Reported
configured objective remains a measurement of its witness; it was not optimized
in that optional mode. Normal momentum is preserved, with the current search's
zero inertia thresholds. Every descendant still re-solves earlier facings from
the original initial state; no position/velocity/yaw seam is frozen.

## Scheduling and exact outcomes

Default cadence1 applies to selected meaningful completed jump/landing/contact
choices, not every simulation input or raw ground-hold construction. Required
anchors/final force their query regardless of cadence. Only the exact queried
model can receive an operational oracle rejection under the user's solver-accuracy
assumption. A failed child never invalidates an unqueried ancestor. Repair
exhaustion, unknowns, timeouts, errors and unfinished construction remain distinct.

Objective, stopping/ranking intent, node-versus-anchor scope and assembly policy
participate in repair compatibility. Physical automatic search uses repair
assembly13; source-era schema5/default instructions do not describe current exact
resume. The solver-call count may exceed worker launches because an early-stop
feasible seed returns without IPOPT.

## Why this policy was chosen; evidence

The user restored backward guide inference and onward progress objectives because
guidance is useful for witnesses even when decision priority/pruning uses ticks.
The [repeated nine-request Real11 benchmark](../research/last-jump-runs/row-benchmark/REAL11_INTERMEDIATE_BENCHMARK.md)
measured135 fresh runs and81 warm probes. Early stopping supplied the main
successful-case timing gain; directional early stop was about as fast as pure
feasibility and yielded better Z progress in the successful numerical cases.
The associated [results](../research/last-jump-runs/row-benchmark/real11-intermediate-results.md)
and [earlier probe](../research/last-jump-runs/row-benchmark/feasibility-results.json)
remain source-era fixed-request evidence, not a global/end-to-end speed claim.

The [current feature evidence](../research/hard-jump-comparisons/README.md)
records600 native-enabled passes,131 known-witness prefix-bound checks and80
post-change retained-witness replays. Combined Real11 improves T67→T64 with
61 wrappers/55 workers versus198/159 before. Initial0-only keeps T64 with26/22
versus73/56. Multiple feature changes were measured together; these counts do
not isolate the numerical stopping policy's individual causal contribution.
