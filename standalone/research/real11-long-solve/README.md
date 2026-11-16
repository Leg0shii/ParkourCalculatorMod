# Longer Real11 with fixed initial-0 decisions through B5

2026-11-15. User-requested extension using the supplied B8–B22, original unit B6
and unit B7. Ordered physical arrivals are B2→B3→B4→B5→B7→B20→B22; B22 is the
terminal landing, implicit in the anchor policy rather than repeated in its list.
The task minimizes achieved finish T, then maximizes Z at equal T.

## Status and resumption

**2026-11-16: user deferred further trials until the other agent finishes the
solver improvements.** No further solve is running. The fixture, saved work and
historical evidence below remain intact; the separate agent's assignment stays
active. The known [bookkeeping handoff](../../docs/BOOKKEEPING_PERFORMANCE_AGENT_HANDOFF.md)
owns hashing, preparation and snapshot costs while preserving policy and exact
resume/ownership. Do not infer that its current implementation progress is known.

When returning:

1. Review the other agent's branch/base, ordered commits, changed-file ownership,
   tests and measured results before integrating. Account for this branch's
   completed face catalog and `4ace2b8a` empty-exhausted-domain fix; preserve
   pending constraint-generation cursors and exact-node outcome distinctions.
2. Revalidate the fixed55-input root and retained B7 witness against all24 scene
   solids with the integrated solver. Keep the earlier facings free and the same
   B2/B3/B4/B5/B7/B20→B22 task; do not freeze a B5 state or import an old finish.
3. Check the latest manifest and its paired SQLite store for compatibility under
   the integrated code. Exact continuation is preferred only when supported.
   If incompatible, record the reason and start a fresh seeded experiment from
   `initial0-fixed-b5.json`; do not claim exact resume or discard the old pair.
4. Use a fresh output path and record source revision, environment/settings,
   resume mode and validation scope. Last validated required arrival is B7@64;
   pending optional B11@68 work inside B7→B20 was dominated by constraint
   enumeration/dedup. Profile that work separately from numerical solve time.
5. Keep the original extended-B7 benchmark and scheduler/interleaving deferral
   separate. This task has unit B6/B7 and no validated B20/B22 finish yet.

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
The corrected continuations are complete; their [summary](corrected-runs-summary.json)
records exact resumes, source stability, prefix audits and retained-witness replay.

| Pass | Budget | New numerical calls / workers | Newly expanded prefixes | Validated required-anchor progress |
| --- | ---: | ---: | ---: | --- |
| Initial, before correction |300s |1 /1 |2 |B7@64 |
| Corrected exact continuation |300s |1 /1 |3 |B7@64 |
| Further exact continuation,30s service slices |120s |0 /0 |0 |B7@64 |

No B20/B22 arrival or full finish was found. The correction backtracks eight
exhausted nodes instead of repeatedly servicing the original empty node. The
latest pending candidate is an optional B11 landing at68 inputs inside the
B7→B20 span. Its constraint generator is unfinished: during the last120s its
emitted descriptors increased7→48 and duplicate-variant count2,552→55,256, with
zero new numerical calls. The remaining bottleneck is constraint-alternative
enumeration/dedup, not a claim that the coupled route is infeasible. The300s
corrected run overlapped the native gate, so these are not controlled speed
comparisons. Numerical/scene models and decision domains are unchanged by the
longer service slice.

The validated B7 solution changed **all55** prefix facings from their seed
(largest change≈5.923°), demonstrating that the earlier continuous variables
remained free. Audits of102 retained program objects in each final checkpoint
confirm the fixed first55 input/contact decisions and selected physical rows,
with no facing equality rows. This object count includes retained state copies;
it is not a count of distinct solves or expanded prefixes. The retained B7
witness independently replays against the full new scene in both continuations.

The [native gate](native-gate-summary.json) passes611 tests with no failures,
errors or skips; source hashes stayed unchanged and all902 protected evidence/
fixture files pass layout validation. The narrower regression/control scope
also passes57 tests. The completed gate keeps its recorded ladder exclusions.

Latest local exact-resume manifest (retain its paired SQLite file):

```text
runs/real11-long-fixed-b5-resume120-2026-11-15/report.json.gz
runs/real11-long-fixed-b5-resume120-2026-11-15/report.json.gz.store.sqlite3
```

After the deferral ends and compatibility is verified, use the ordinary CLI's
`--resume` with that manifest and a fresh output path.
The run used only a timing override (`seconds=120`, `score_seconds=30`); those
are not physical-domain changes. Root/model/pending-work ownership is preserved.

A fixed prefix restricts which complete routes are discoverable;
failure of this experiment is not a statement about other earlier decisions.
The all-six-face catalog is a separate geometry API and is not yet consumed by
the movement generators; this experiment uses their existing original-box paths.
