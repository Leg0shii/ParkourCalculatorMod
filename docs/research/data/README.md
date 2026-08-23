# Matrix run archives

Each subdirectory is one benchmark campaign of the solver run matrix (2026-07, split-solver rework era). Convention since the 2026-08 doc cleanup (issue 318): the `analysis.md` is the durable record; raw `runs.jsonl` files were dropped because their embedded graph hashes reference CMA-era nodes that no longer exist, so they cannot be replayed or extended against current code. `matrix-step6-finale` set the precedent (its raw runs were never kept).

Live contracts:

- `matrix-taser-pin1/band.txt` is referenced by the test suite (see `core/src/test/.../anglesolver/TESTS.md`, `PKC_MATRIX_BAND`). Do not delete or move it.
- `matrix-gen1/band.txt` is the standing generated-ladder benchmark definition.
- `matrix-a8smooth1/` holds only its `band.txt`; its analysis lives in `matrix-a8base1/analysis.md`.
- `matrix-race-gen1/` was removed entirely; its verdict is recorded in `split-solver-grill-brief-2026-07-19.md` (A5).

To regenerate a matrix, use the test-tree harness (`RunMatrixScreen`, `PKC_MATRIX_SWEEP`) and archive a fresh `analysis.md` here; keep new raw runs out unless a test contract needs them.
