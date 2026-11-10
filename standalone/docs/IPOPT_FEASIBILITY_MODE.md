# Pure feasibility for intermediate solves

**Current policy (2026-11-10):** the integrated anchor solver uses the configured
directional objective with `stopOnFeasible=True`. Early stopping no longer
implicitly selects constant zero. To request the retained pure-feasibility mode
explicitly, pass `objectiveMode: constant_zero` and `stopOnFeasible: true` in the
numerical request. `objectiveMode: configured` is the default. Final optimization
retains the configured objective and does not stop at the first feasible point.
See [integration and checkpoint compatibility](ANCHOR_SEARCH_INTEGRATION.md).

The following text and measurements document the earlier solver-branch default
and the retained explicit constant-zero mode; its old API/default and schema5
instructions are historical. The benchmark evidence itself remains unchanged.

Implemented 2026-11-04 at the user's request. Intermediate spatial-goal solves now ask for any feasible trajectory, while the final solve retains the configured objective. For a fixed set of discrete decisions, the numerical problem is:

\[
\min_{\theta,p,v} 0\quad\text{subject to all fixed movement and interaction constraints.}
\]

There is no position/velocity objective, small regularizer or softened constraint in this mode. Ordinary momentum and the declared inertia settings remain unchanged. Facings stay variable and each later solve still starts from the original initial state; accepting an intermediate witness commits decisions, not its numerical trajectory.

## API and implementation

Use the existing `solve(request, stop_on_feasible=True)` API or `--feasibility-only` CLI switch. Previously these stopped early but retained the directional NLP objective. They now select a **constant-zero objective** and stop on independently validated feasibility. An already feasible seed can return without starting IPOPT. `stop_on_feasible=False` retains the requested optimization behavior.

`candidate.objective` and the original objective specification are still reported as measurements of the returned trajectory. They are not the zero solver cost and are not optimized in feasibility mode. Results, phase messages, DFS candidate records and numerical call diagnostics now state `objectiveMode: feasibility` or `optimization`.

| Layer | Behavior |
| --- | --- |
| [worker.fixed_phase](../ipopt/worker.py) | Uses constant zero when stopping on feasibility; keeps bound extraction and all reduced numerical rows. The monitor evaluates unextracted constraints and accepts an independently validated point even when all objective costs tie. |
| [solver.solve](../ipopt/solver.py) | Preserves seed/timeout/cancellation and every-original-row scalar validation; reports objective mode. |
| [search.find_route](../autonomy/search.py) | Non-polishing run/jump requests use feasibility; the declared terminal optimization uses the requested objective. Diagnostics record the mode and actual stop flag. |
| [checkpoint_search.evaluate_program](../autonomy/cell_routes/checkpoint_search.py) | A DFS intermediate `first_witness=True` disables objective polishing as well as equal-cost refinement. It still performs vertical, scene and corridor checks, and can generate repair constraints after rejected numerical witnesses. |
| [depth_first.search_goals](../autonomy/cell_routes/depth_first.py) | Intermediate frames seek feasible arrivals; final frames optimize. Arrival/jump/run ordering and retained discrete alternatives remain unchanged. |

The constant-objective formulation uses the ordinary NLP interface described by the [CasADi solver API](https://web.casadi.org/api/html/d4/d89/group__nlpsol.html). It does not force IPOPT's restoration phase. [`start_with_resto`](https://coin-or.github.io/Ipopt/OPTIONS.html) is a different control and can fail on an initially feasible point; it is not our feasibility switch. [`Feasible_Point_Found`](https://coin-or.github.io/Ipopt/OUTPUT.html) is a special square-problem return status, not the generic acceptance rule we depend on.

## Scores, acceptance and persistence

Intermediate position/velocity/progress describe **one feasible witness**, not a maximum or bound. The DFS traversal uses achieved arrival time, jumps and runs, with candidate identity for the final tie; it does not discard siblings for an inferior unoptimized progress measurement. Separately requested common-clock objective comparisons still optimize unless explicitly run in first-witness mode. The earliest terminal tick/maximal configured objective ordering is unchanged.

The parent checks every original constraint and existing contact/inertia semantics; the orchestrator checks scene/corridor/vertical acceptance before committing a witness. A numerical feasible point that fails those checks can trigger another fixed-constraint variant. A failed feasibility search remains unknown, not proof of infeasibility. IPOPT's [output documentation](https://coin-or.github.io/Ipopt/OUTPUT.html) also distinguishes local infeasibility from a global certificate.

The DFS context now fingerprints `intermediateObjective: constant_zero_feasibility_v1`; **pre-change DFS checkpoints are not exact-compatible** and require explicit parent/decision reconstruction or their recorded old code. Original evidence is retained; no checkpoint is overwritten or automatically reconstructed. State shape remains DFS schema5; storage version1 is unchanged. Repair context includes objective mode. Switching the same hard model from feasibility to optimization resets its constraint-generation cursor too, so completed feasibility work cannot falsely stand in for optimization or suppress the new request. Resume within the same new policy preserves pending/completed work as before.

## Validation and measurements

**64 IPOPT tests and162 autonomy tests pass**, compiler/native worker enabled, no skips. New native checks inspect the actual symbolic NLP objective for exact zero, keep the accepted point when flat costs tie, and run the same hard intermediate model first for feasibility and then for optimization with independent replay. Existing checkpoint/DFS/resume, branching, contacts, stepping, ceiling, captured-model and full integration regressions pass.

The [saved-request probe](../autonomy/experiments/last-jump-runs/row-benchmark/feasibility_probe.py) compares directional early stopping, constant-objective early stopping, and optimization on four recorded requests. [Results/source hashes](../autonomy/experiments/last-jump-runs/row-benchmark/feasibility-results.json) contain36 rotated first-phase runs plus eight fresh workers. The27/57-input requests were already unknown in the archived run and remain unknown under both fresh-worker modes. The40/58-input requests remain feasible with both modes, independently checked against every original row. No invalid point is accepted.

On the two successful first-phase examples, directional→constant-objective stopping uses16→16 and11→8 iterations respectively. Fresh optimization/feasibility worker times are164/115ms and186/122ms, single samples. These are small fixed-request observations, not a guaranteed speedup or evidence of all-map optimality. No new full Real11 route search was run. Pure feasibility can return a less advanced witness by design; subsequent decision solves still reoptimize all facings from the beginning.


## Larger repeated Real11 benchmark

The follow-up [all-nine-request benchmark](../autonomy/experiments/last-jump-runs/row-benchmark/REAL11_INTERMEDIATE_BENCHMARK.md) adds135 fresh runs and81 warm probes. All modes agree on numerical feasibility, with105 accepted trajectories independently revalidated and30 unknowns. Early stopping is the main benefit: three successful numerical cases take139–151ms pure feasibility versus180–207ms optimization, while directional early stop is about equally fast and yields better Z progress. Four already-valid seeds bypass the worker entirely. Pure feasibility is unchanged by this measurement; no end-to-end route-quality claim follows.
