# Screen accumulated passage constraints before extension

2026-11-15. User requested stronger pre-solver checks after each obstacle
assignment, while keeping coupled obstacles in one angle-solver problem.
Follows the [B6 audit](../anchor-run-budget/B6_PASSAGE_CHECKS.md) on commit
`641c68b5`; branch `perf/early-passage-screen`.

## Implementation

`ConstraintVariants._screen` runs the cheap interaction check, then the existing
`reachability.screen.contradiction` service when a physical model is available.
That service now accepts extra rows without mutating the original model: it
combines them with existing walls, propagates position/travel bounds and runs
the existing optional relaxed linear control exclusion.

Every accumulated prefix is screened before extending the next obstacle. B6 is
checked with the original-state model and all prior constraints before B7 is
enumerated. Adding B7 checks B6+B7 together. Separate passes do not establish
joint feasibility. Surviving assignments still use the coupled angle solver
and physical validation, with earlier facings free.

Passed nodes carry `prefixScreened=true` to avoid repeating the leaf check.
Legacy pending nodes without the flag are screened before extension, including
saved cursors already inside B7. The flag means the necessary-condition check
completed, not feasibility. Model/schema/keys remain compatible: rows only
accumulate in the same context, old proofs stay valid, and old cursors remain
readable. Completed numerical work is not restarted.

Missing or inconclusive optional checks retain work. No numerical failure becomes
an ancestor proof. DFS order, run budgets, contact caps, passage language and
nominal transition counts stay unchanged; inactive-phase aliases are separate.

## Validation

The ongoing [B6 questions and answers](B6_QUESTIONS_AND_ANSWERS.md) records the
user's follow-up questions and answers checked against the option evidence.

The [complete B6 option ledger](B6_OPTIONS.md) contains482 freshly logged options
across all four B6 catalogue contexts, with row-level rejection reasons. It
distinguishes the316 recorded-run options from166 replay-only options, verifies
all316 historical decisions, and marks two inconclusive LP checks explicitly.

The captured B6 model rebuilt with `InteractionGate` matches all saved horizontal
masks. The new production check rejects **237 of277 descriptions before B7
expansion**:108 travel contradictions,90 interval contradictions and39 linear
exclusions. Forty remain not ruled out, not40 certified feasible paths.

Focused51 tests pass. Regressions cover rejecting a prefix before downstream
generation, joint rejection of individually possible choices, feasible controls,
inconclusive optional checks, row normalization without mutation, legacy pending
prefixes and JSON continuation.

The selected native gate passes **636 tests** with zero failures/errors/skips,
including numerical modules and generic mixed ladder controls; dedicated
ladder/climbable/vine/movement-entry exclusions are unchanged. Source hashes
remain unchanged during the gate. Layout validates all902 protected files.

| 300-second cold control | Before | After |
| --- | ---: | ---: |
| B7 face/tick enumeration visits | 3,282,698 | 1,977,755 |
| B7 local options returned | 3,978 | 2,384 |
| All enumeration visits | 3,293,967 | 1,998,548 |
| Numerical requests / workers | 49 /47 | 59 /57 |
| Requests for67-tick journeys | 42 | 52 |
| Expanded route prefixes | 21 | 21 |
| Best T | 67 | 67 |
| Best Z | -1093.823129063534 | -1093.823129063534 |
| Independent retained-witness replays | 41/41 | 50/50 |

B7 enumeration visits fall39.75% in this timed observation while the search
reaches more numerical requests. The same incumbent is retained; no64-tick query
is reached. This is not an optimality or whole-search quality improvement claim.
Counts are timed coverage, not identical fixed work. The new run's measured
search wall time is303.235s including overshoot.

A checkpoint from the pre-change continuous run resumes with `exact=true`,
session2, preserving T67 and all41 retained-witness replays. The requested10s
durable continuation takes33.610s including checkpoint/work overshoot and makes
zero numerical requests. This demonstrates compatibility/retention, not new
numerical progress; unit and native continuation tests cover pending work reuse.

The [evidence index](evidence/2026-11-15/index.json) records source patch/hashes,
native scope, B6 prefix counts, cold/resume ledgers and settings. Original large
manifest/SQLite pairs remain together in their ignored run directories.
Historical evidence is preserved.

## Reproduction

Use this worktree's own installed Python/compiler descriptor, as in the
[runbook](../../docs/RUNBOOK.md), with single-threaded BLAS/OpenMP and fresh outputs:

```sh
"$PKC_IPOPT_PYTHON" -B tools/native_gate.py --output /new/path/native
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input cases/real11/physical-anchors.json --output /new/path/cold \
  --seconds 300 --profile-mode enumeration
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input cases/real11/physical-anchors.json --output /new/path/resume \
  --resume /path/to/pre-change/report.json.gz --seconds 10 --durable \
  --profile-mode enumeration
```
