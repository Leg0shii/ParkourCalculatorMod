# Geometric checkpoint guidance

Implemented in the isolated `research/ipopt-cell-routes` worktree. This experiment tests the user's position-only local score on the existing cell-route polyline. It runs normal, ceiling and stepping movement, including blockage avoidance, run inputs and landing. The production search default is unchanged.

Follow-up, 2026-11-01: [joint decision windows and objective bands](../decision-windows/README.md) now implement clustering supplied opportunity ranges, comparison before the next separate decision, near-tie retention and fair per-parent work quotas. The older measurements below describe the explicit-tick/fixed-width implementation. A richer multi-tick or velocity score is deferred while the new experiment diagnoses useful discarded alternatives.

## Model and algorithm

Choose an outgoing horizontal polyline segment and a shared absolute state tick `h` for each comparison window. The score is `direction dot position[h]`, with the same normalized horizontal direction and `h` for every candidate in that window. Tick zero is the original pre-input state; input `h-1` produces state `h`. Y is excluded from the score. Different methods may have different Y, ground state and vertical phase at `h`.

Generate symbolic movement histories up to `h`, truncating longer choices there. A free-flight continuation extends directly to the checkpoint, avoiding one-input partitions of the same history. Actual step, ceiling and landing opportunities remain separate choices. Physically equivalent profiles are deduplicated independently of movement-unit partition names.

Each score uses the existing continuous facing solver with the selected physical profile, finite contacts, blockage repairs, cell-corridor checks and shared collision acceptance. All facings from input zero remain variables. The original full starting position and velocity are preserved. Normal stored velocity, acceleration, drag and contact resets remain; velocity thresholds are zero in both horizontal and vertical physics. This is the agreed decision-phase model, not a bound on thresholded game movement.

Keep a configurable number of scored candidates plus an unknown reserve. Resume generation with their symbolic input/contact/clearance decisions and warm-start facings. Numerical intermediate positions, velocities and facings are never pinned. Scoring-only comparison-region constraints are discarded before continuing. A completed goal is ranked by actual elapsed ticks and the configured final objective, separately from the checkpoint score.

The local score and beam drops are heuristics. A lower score, failed IPOPT attempt, cap or wrong checkpoint stage is not proof of dominance or infeasibility. The result always leaves minimum-tick and objective optimality claims false.

## Checkpoint selection

This implementation accepts an explicit schedule of ticks and outgoing polyline segment indices. It does not automatically predict when a route reaches its next turn. Choose `h` after the turnaround and before the next ambiguous turn or decision. Every candidate in the batch uses that same `h`.

Stage checks preserve the ordered landmark-crossing prefix, require nonnegative outgoing progress, and reject progress beyond the next turn on a nonterminal segment. An optional common X/Z comparison rectangle can make the desired stage explicit during scoring. It must suit all methods being compared; it is a probe assumption, not a proven necessary condition for the final route. A wrong-stage result remains unknown. These tests do not automatically guarantee that every geometric corner is behind a candidate when the corner has no landmark or region guard.

The geometric guide starts at the original position and ends on the goal region. It supplies direction and corridor context; it does not prescribe numerical portal-center states or a vertical flight trajectory. Vertical-only guide segments are skipped when constructing horizontal directions.

## API and reproduction

The experimental API is `cell_routes.checkpoint_search.find_checkpoint_route`. Its important settings are `checkpoints`, `beam_width`, `unknown_slots`, `max_candidates`, `max_solver_calls`, `score_call_limit`, `final_call_limit`, `score_seconds` and `candidate_seconds`. The movement-search prefix and queue caps come from the scene. Calls and expansions are counted across windows. Per-candidate scoring limits do not exhaust another candidate's local allocation. Global limits still apply, so a long scoring phase can leave insufficient final-search work; inspect termination and window records.

Prepare the shared Java compiler using the normal standalone setup. From this worktree:

```sh
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 .venv-guidance/bin/python \
  -m standalone.autonomy.cell_routes.checkpoint_experiment \
  --input standalone/autonomy/experiments/checkpoint-guidance/fixtures \
  --output standalone/autonomy/build/checkpoint-guidance.json.gz \
  --repeats 3
```

The three arms use identical fixture settings and no-threshold physics:

| Arm | Partial checkpoints | Retention order |
| --- | --- | --- |
| `baseline` | None | Existing movement generation |
| `generation` | Score and validate at shared ticks | Generation order |
| `guided` | Score and validate at shared ticks | Descending horizontal score |

`generation` intentionally pays the same scoring/feasibility cost as `guided`. This isolates score ordering from the effect of stopping generation at checkpoints. It is not an optimized cheap screening-only algorithm.

For the greedy diagnostic, add `--arms generation guided --beam-width 1 --unknown-slots 0`. This deliberately discards every alternative and is not the recommended default.

The report stores exact fixtures, source commit, all candidates, retained IDs, accepted prefix/full witnesses, numerical/compiler work and independent replay results. Every saved accepted prefix and final route is reconstructed from its original initial state and checked against scalar physics, contacts/support, shared scene collisions and exact stored horizontal positions/velocities. Actual-world execution and threshold-pattern repair remain later work.

## Validation and results

Focused checks with the compiler/IPOPT enabled: 26 pass, including the new checkpoint tests and existing cell, stepping and movement-composition integration tests. The broader autonomy gate runs 359 tests with 81 environment skips and no failures after excluding one previously reproduced ladder failure. IPOPT: 51 tests, eight environment skips, no failures. The ladder failure predates this implementation and ladders are outside this experiment.

```sh
PKC_RUN_TICKS_COMPILER="$PWD/standalone/autonomy/build/compiler.json" \
PKC_IPOPT_PYTHON="$PWD/.venv-guidance/bin/python" \
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 .venv-guidance/bin/python -m unittest \
  standalone.autonomy.tests.test_checkpoint_guidance \
  standalone.autonomy.tests.test_cell_experiment.CellNumericalTests \
  standalone.autonomy.tests.test_stepping_cases.SteppingNumericalTests \
  standalone.autonomy.tests.test_movement_composition.MovementCompositionIntegrationTests -q
```

Repeated measurements and the greedy diagnostic are recorded in `RESULTS.md` with their source and saved evidence. Fixtures are deliberately small, with supplied spatial polylines and explicit checkpoint times. They test local decision guidance within a route; they do not establish cold spatial-route discovery or exhaustive optimality.
