# Longer Real11 with fixed initial-0 decisions through B5

2026-11-15. User-requested extension using the supplied B8–B22, original unit B6
and unit B7. Ordered physical arrivals are B2→B3→B4→B5→B7→B20→B22; B22 is the
terminal landing, implicit in the anchor policy rather than repeated in its list.
The task minimizes achieved finish T, then maximizes Z at equal T.

Inputs:

- [Unseeded task](../../cases/real11/extended-anchors.json): complete extended
  geometry and anchor/goal settings, with existing normal/ceiling/stepping
  mechanics enabled. Horizon200 and existing declared caps are retained.
- [Fixed-prefix task](initial0-fixed-b5.json): the same task with one existing
  `settings.initial_programs` root. This uses the already supported lower-level
  `find_checkpoint_route(initial_programs=...)` argument through application
  settings; no new runtime API or solver policy was introduced.

The root is the accepted B5 parent from frame9 of the preserved unrestricted
opening run, initial-jump0 family. It owns55 inputs and requested jump inputs
`[0,12,21,30,40]`; required arrival clocks are B2@21, B3@30, B4@40, B5@55.
It includes the same-block B1 setup landing at12. Actual landing/contact rows
and selected physical avoidance rows are retained. Old search-node/comparison/
repair/guide-point metadata is removed. The original55 facings remain only as
`guideSeedYaw`, a numerical initialization, not facing equality constraints.

The prefix was independently replayed through tick55 against **all24 new scene
solids**, passing scalar, support, exact horizontal and shared Java full-scene
collision checks. No old B7 finish, final incumbent, hard latch or old search
continuation is imported. This is a seeded new task, not an exact resume of the
old extended-B7 benchmark and not a cold discovery of B1–B5.

Search starts with the next unmet anchor B7. It may choose preparation after
tick55 (including movement on B5) and optional contacts toward B7/B20/B22, under
the existing movement/visit domain. It cannot change the first55 inputs or their
selected contact schedule. Every numerical request still starts at the original
B1 initial position/velocity and leaves earlier facings free; it does not freeze
the horizontal arrival position/velocity at B5.

Run from `standalone/` with the installed native environment:

```sh
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 \
PKC_IPOPT_PYTHON="$PWD/../.venv-guidance/bin/python" \
PKC_RUN_TICKS_COMPILER="$PWD/build/native/compiler.json" \
../.venv-guidance/bin/python -B -m tools.profiling.run \
  --input research/real11-long-solve/initial0-fixed-b5.json \
  --output runs/real11-long-fixed-b5-new --seconds 300
```

Use a fresh output directory. The ordinary CLI can run/resume the same fixture:

```sh
../.venv-guidance/bin/python -B -m parkour_solver \
  --input research/real11-long-solve/initial0-fixed-b5.json \
  --output runs/real11-long-fixed-b5-new.json.gz
```

The [first300-second experiment](first300-summary.json) reached unit B7@64 with
one numerical worker and a passing independent replay, but stalled at a65-input
grind proposal under B9a. Constraint generation exhausted with zero emitted
alternatives, yet `search.feasibility.outcome` required a completed numerical
branch before returning exhausted. The node remained suspended and was serviced
repeatedly without additional solves. A narrow fix recognizes exhausted empty
constraint generation as operational backtracking, while pending/unresolved/
in-flight or unfinished generation remains suspended. It does not call an
untested ancestor infeasible or change the movement/continuous model.

The [prefix validation](prefix-validation.json) records its full-new-scene check.
A continuation of the saved longer search under the correction is being run.
A fixed prefix restricts which complete routes are discoverable;
failure of this experiment is not a statement about other earlier decisions.
The all-six-face catalog is a separate geometry API and is not yet consumed by
the movement generators; this experiment uses their existing original-box paths.
