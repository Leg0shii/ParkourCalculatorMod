# Search failure root-cause probes

Subsequent user-authorized implementation: [the three runtime fixes and validation](../failure-fixes/README.md), source `bc7659b8`. The research-only/default-unchanged descriptions below refer to the original archived investigation.

This work investigates generic mechanisms behind proposal starvation and loss of fully feasible candidates. It does not change the production/default solver, the scalar movement model, physical acceptance, the score or the current experimental window policy. All behavioral changes below are isolated research hooks. Earlier facings remain free and all problems begin at the original full initial position/velocity. Velocity thresholds are zero. Ladders and intentional wall hits remain excluded.

[Results and generic fixes](RESULTS.md) identify complete-proposal starvation/eviction, mismatched incumbent validity between layers, and skipped fallback seeds at degenerate angular starts. Counterexamples reject naive goal-only scheduling and replacing optimization with seed return. The evidence spans additional controls and explicitly separates warm feasibility, cold discovery and scalar-only numerical behavior.

## Numerical-layer contract

The low-level solver validates and preserves the best candidate for its supplied constraint model. Corridor membership, optional-floor conflicts and finite-scene collision checks happen later in `solve_contact_branches`. A higher-scoring inner candidate can fail those additional guards. Returning only the best inner candidate hides a fully valid earlier seed from the outer incumbent.

The probe uses fourteen known program variants over ten scene geometries, forward and reversed diagnostic objectives, and eight deliberately perturbed seed/objective cases. Each experimental mode sees the same problems, per-candidate four-call cap and acceptance criteria. Reverse objectives are stress tests of the acceptance contract, not claims about normal routing quality or independent new maps. Old controls are clipped before their apex/threshold difference, switched to zero thresholds, and actually revalidated; their old feasibility is not assumed under the new model.

The modes are unchanged optimization, replacing an invalid final inner output with a fully validated seed, feasibility-first solving, and preserving validated seed incumbents in a separate channel while leaving optimization/repair proposals unchanged. Candidate audits check scalar constraints, support/exit conditions, Y schedule, optional-floor conflicts, corridor and finite collision geometry. Every returned feasible result is independently replayed. The broad warm audit concerns the shared inner/outer acceptance contract; the earlier focused turn probe additionally checks the checkpoint stage.

The replacement and feasibility-only modes are diagnostic simplifications. They can suppress useful repair work or sacrifice objective quality. The separate incumbent channel tests how to keep a valid seed without discarding the optimizer's invalid-but-informative proposal. None of these experiments demonstrates cold discovery from an invalid initial guess merely by preserving a feasible seed.

## Generation contract

Ten scene geometries include the original six window scenes plus separate run-up, remote-support, grind/blip and repeated-ceiling controls. Each scene uses a fixed 512-expansion, 384-queued-prefix limit and up to twelve emitted complete proposals. The observer has a 30-second limit. Recorded times include research instrumentation and are not production performance measurements.

An AST hook replaces only queue operations and the generator key function in a private function namespace. It never mutates the shared module's generator or the global heap implementation. A unit test checks unchanged baseline outputs and generation counts. The observer distinguishes complete profiles constructed, complete profiles evicted by the queue cap, and complete profiles actually yielded for validation. It records partial depths and emitted times.

Four arms isolate legacy shortest-prefix ordering, the same ordering with canonical identity, complete-candidate-first ordering, and complete-first ordering with fair interleaving/pruning across elapsed-time buckets. Canonical keys retain physical profiles, constraints, terminal identity and interaction-budget context; sampled duplicates are checked for identical compiled coefficients, vertical replay, finite contacts and support/exit checks. The experiment remains restricted to the enabled non-ladder families; it does not establish universal key equivalence for all future movement types.

Completed profiles are proposals, not feasible solutions. A separate cold validation pass sorts the emitted pools by elapsed ticks and uses matched bounded physical solves on the dense chain, turn, multiple-support and two additional interaction scenes. Accepted results get independent replay. This separates queue starvation from actual physical solvability and exposes shorter-plan coverage lost by naive goal prioritization.

## Reproduction

Prepare the compiler and IPOPT environment using the standalone setup, then run serially with numerical thread counts pinned:

```sh
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 .venv-guidance/bin/python \
  -m standalone.autonomy.cell_routes.failure_diagnostic --part generation \
  --output standalone/autonomy/build/failure-generation.json.gz

OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 .venv-guidance/bin/python \
  -m standalone.autonomy.cell_routes.failure_diagnostic --part warm \
  --output standalone/autonomy/build/failure-warm.json.gz

OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 .venv-guidance/bin/python \
  -m standalone.autonomy.cell_routes.failure_diagnostic --part verify \
  --generation-input standalone/autonomy/build/failure-generation.json.gz \
  --output standalone/autonomy/build/failure-verify.json.gz

OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 .venv-guidance/bin/python \
  -m standalone.autonomy.cell_routes.repair_diagnostic \
  --input standalone/autonomy/build/failure-generation.json.gz \
  --output standalone/autonomy/build/failure-repair.json.gz

OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 .venv-guidance/bin/python \
  -m standalone.autonomy.cell_routes.scalar_seed_diagnostic \
  --capture standalone/autonomy/experiments/failure-rootcause/evidence/failure-scalar-capture.json.gz \
  --output standalone/autonomy/build/failure-scalar.json.gz
```

Reports contain exact requests/programs, guard outcomes, generation counts, optimizer inputs/outputs, work caps and source commit. Conclusions must distinguish original-example failures, additional controls, deliberately adverse objectives, and warm versus cold discovery. Parameters are research limits rather than recommended production defaults.

The instrumentation gate passes five focused invariant tests; with existing compiler/IPOPT checks the focused gate passes 43 tests. The wider autonomy run completes 376 tests with 82 environment skips and no failures after excluding the previously reproduced baseline ladder failure. No mod Gradle test suite was run for this standalone research.
