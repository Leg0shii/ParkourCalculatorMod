# Disk relaxation in automatic discovery

2026-11-19, `feat/disk-interim-solver`. Automatic discovery solves its interim
numerical queries with the Clarabel disk relaxation (`|a| <= magnitude`), confirms
acceleration-shortfall alarms with IPOPT, and publishes one IPOPT finalization; the
HiGHS LP screen is removed. Behavior: [numerical backend](../../docs/ANCHOR_SEARCH_EXECUTION_REFERENCE.md#numerical-backend-and-finalization),
[solver](../../docs/reference/NUMERICAL_SOLVER.md#disk-relaxation),
[decision](../../docs/DECISIONS_AND_PLANS.md). New identities: reachability proof
policy3, constraint generation policy14, event constraint policy15 and numerical
policy1, DFS `numericalBackend`, solve policy `numerical`.

## Validation

The native gate passes **775 tests** with zero failures, errors or skips and
unchanged source at `a5f42e35` ([report](2026-11-19/native-report.json)).

## Real11 matrix

Disk arm `4375cd96` against stock `c9deef42`, each in its own worktree and venv
(Clarabel 0.11.1), Python bridge, `OPENBLAS_NUM_THREADS=OMP_NUM_THREADS=1`,
`tools.profiling.run --profile-mode bookkeeping`, three serial paired repetitions
with alternating arm order, load average 3.3 to 3.8 ([script](2026-11-19/matrix.sh),
[runs](2026-11-19/matrix-runs.jsonl), [per-run table](2026-11-19/matrix-summary.txt)).
Fixtures: `cases/real11/physical-anchors.json` (unrestricted, 300 s), plus
`initialJumpInputs: [0, 8]` (combined, 300 s) and `[0]` (initial-0, 90 s). Every
run of both arms ends `exhausted` well inside its budget.

| Fixture | Arm | Median search s | Median process wall s | T / objective | Requests / interim workers | Finalization IPOPT calls |
| --- | --- | ---: | ---: | --- | ---: | ---: |
| unrestricted | disk | 3.58 | 4.28 | 64 / -1093.2381026602418 | 14 / 0 | 1 |
| unrestricted | stock | 7.54 | 8.25 | 64 / -1093.2381026602416 | 18 / 17 | n/a |
| combined | disk | 3.49 | 4.15 | 64 / -1093.2381026602418 | 14 / 0 | 1 |
| combined | stock | 7.38 | 8.08 | 64 / -1093.2381026602416 | 18 / 17 | n/a |
| initial-0 | disk | 1.36 | 1.62 | 64 / -1093.2381026602418 | 8 / 0 | 1 |
| initial-0 | stock | 2.95 | 3.22 | 64 / -1093.2381026602416 | 9 / 8 | n/a |

All 18 runs passed independent replay with unchanged source. The disk arm raised
no alarm, made no confirmation or fallback, and served every candidate from the
screen's cached relaxation. Reports of one unrestricted pair:
[disk](2026-11-19/real11-disk-report.md) ([summary](2026-11-19/real11-disk-summary.json),
[provenance](2026-11-19/real11-disk-provenance.json)),
[stock](2026-11-19/real11-stock-report.md) ([summary](2026-11-19/real11-stock-summary.json)).

## Held-out requests

[`probe_production.py`](2026-11-19/probe_production.py) solved, with the
production module, every request the native suite issued at `c9deef42` (247
distinct, plus 161 threshold-0 variants of the threshold requests), joined with
IPOPT optimizations and random-seed restarts from the pre-implementation audit
(kept outside the repository with the study results)
([summary](2026-11-19/heldout-probe-summary.json)): 227 supported; 0 wrong
exclusions, 0 relaxed points rejected by the validator, 0 relaxed-feasible requests
without a full-strength point; 9 material score gaps (1.3e-4 to 0.296 blocks), all
alarmed, and 45 alarms in total. Two threshold-0 variants end `NumericalError`
(undecided, never excluding) where the audit prototype, with rows in request rather
than sorted order, had one `PrimalInfeasible` and one `MaxIterations`. With the
zero-cost retry of undecided specs both are `PrimalInfeasible` (58 infeasible, 0
undecided, otherwise unchanged; [summary](2026-11-19/heldout-probe-retry-summary.json));
they are the `disk-undecided` test fixture.

## Search tests through the dispatch

[`coverage.py`](2026-11-19/coverage.py) counted relaxed-dispatch outcomes in the
automatic-search test modules ([counts](2026-11-19/search-test-dispatch.json)):
`test_anchor_search` 5 requests (4 Clarabel, 1 confirmation, no material gap),
`test_semantic_search` 2 Clarabel, `test_integration` and `test_event_search` none
(they use non-automatic entry points or mocked evaluations). The relaxation's
breadth evidence is therefore the held-out probe above, not these modules.
