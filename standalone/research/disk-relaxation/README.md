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

## Alarm ticks

On the held-out population all 9 material gaps had exactly one tick more than 1%
short: tick 0, a jump tick; 23 of the 36 false alarms also had a short jump tick,
with shortfalls in the same range (genuine 0.10 to 0.84, false 0.012 to 0.75). No
cheap Clarabel-only estimate separated them: pinning the short ticks at full
strength and re-solving cleared at most 5 of 36 false alarms (angle scans: 2 to 3,
up to 1.08 s per request, slower than an IPOPT confirmation). The alarm is
therefore limited to jump ticks and ground ticks on a thin ledge (the user's
observation of where real gaps occur). On the native suite re-run with every
`find_route` request at threshold 0 through the relaxed dispatch
([script](2026-11-19/capture_relaxed.py)), the requests that carry the program's
vertical and movement schedules had 6 material gaps, all still alarmed, and 18
false alarms, 9 dropped.

## Confirmation seed

Confirmations are one IPOPT optimization per alarmed request; in production they
meet both feasible and infeasible requests, so a retry on failure is not an
option. Four single-attempt seeds were compared, each at the request's own time
budget with its fallback facing kept: every tick at its disk direction (full),
disk directions only on ticks using at least 99% of their acceleration and the
request's own facing elsewhere (partial, the previous rule), the request's own
seed, and every tick at `startYaw` (cold). A request counts as feasible when any
seed or one of 8 random-seed restarts found a full-strength point.

| Population | Requests | Full | Partial | Own | Cold |
| --- | ---: | --- | --- | --- | --- |
| Real11 extended control (anchors b1 to b20, goal b22), stock and relaxed captures | 164 | 0 / 4 | 5 / 9 | 5 / 13 | 0 / 11 |
| Native suite, held-out requests (IPOPT-seeded prefixes) | 169 | 1 / 1 | 2 / 2 | 1 / 0 | 0 / 2 |
| Native suite re-run on the relaxed dispatch at threshold 0 | 110 | 1 / 0 | 0 / 2 | 0 / 0 | 2 / 1 |

Cells are false infeasibilities / optima more than `1e-4` worse than the best
known. CPU was the same within 14% (Real11 medians 0.194 s full, 0.196 s partial,
0.198 s own, 0.221 s cold). The full seed's two false infeasibilities are requests
without the program schedules, which never alarm. All 164 relaxation-feasible
Real11 requests had a full-strength point (13 more were relaxation-infeasible and
never reach IPOPT). Summaries: [Real11](2026-11-19/seed-real11-extended-summary.json),
[held-out](2026-11-19/seed-heldout-summary.json),
[relaxed native](2026-11-19/seed-native-relaxed-summary.json); scripts
[capture_run.py](2026-11-19/capture_run.py), [seed_bench.py](2026-11-19/seed_bench.py),
[seed_study.py](2026-11-19/seed_study.py), [seed_study_relaxed.py](2026-11-19/seed_study_relaxed.py).

The full seed's false infeasibilities are a symmetry trap. On a mirror-symmetric
request Clarabel returns the symmetric optimum, so a short tick points exactly
between its two full-strength completions (all three failing seeds were exactly
axis-aligned on every tick; a 1-tick model with X capped at 0.5 relaxes to
`(0.5, 0)` while its full-strength optima are at +-60 degrees). IPOPT iterates
from a symmetric start stay symmetric, so it stops at a local infeasibility; any
offset solves the 1-tick model. Turning every tick by one small angle breaks every
mirror symmetry ([script](2026-11-19/seed_tilt.py)):

| Seed | Held-out (169) | Native relaxed (110) | Real11 (164) | CPU total |
| --- | --- | --- | --- | ---: |
| Full | 1 / 1 | 1 / 0 | 0 / 6 | 65.3 s |
| Full, all ticks + `1e-4` rad | 0 / 0 | 0 / 0 | 0 / 6 | 63.8 s |
| Full, all ticks + `1e-3` rad | 0 / 0 | 0 / 0 | 0 / 7 | 63.8 s |
| Full, all ticks + `1e-2` rad | 0 / 0 | 0 / 0 | 0 / 7 | 63.8 s |

(References here include these arms' points, so Real11's full row has 6 worse
optima instead of 4.) The Real11 false infeasibilities of the partial and own seeds
are a different mechanism: those seeds are 18 degrees off-axis, and IPOPT settles
in a wrong basin; the full seed avoids them. The turned full seed (`1e-4` rad) is an
assumption: revisit it if confirmations start rejecting feasible requests or
report spurious gaps.
