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
unchanged source at `a5f42e35` ([report](2026-11-19/native-report.json)), and
**779 tests** at `2f9eef00`, after the undecided retry, the alarm ticks and the
confirmation seed ([report](2026-11-19/native-report-2f9eef00.json)), and **778
tests** at `ea33c191` without lineage promotion ([report](2026-11-19/native-report-ea33c191.json)),
and **783 tests** at `9ca57442` with the relaxed scenario and cancel/resume tests ([report](2026-11-19/native-report-9ca57442.json)).

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
`RelaxedAutomaticSearchTests` later added relaxed searches over a slab boost, a
ceiling hit, a grind, a turn and a run-off, and a native cancel/resume sequence
through `search.application.run`.

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

Later the alarm was narrowed to what can make the relaxation wrong about
feasibility, not merely about the objective: a jump tick more than 10% short. On
758 relaxation-feasible requests with IPOPT truth (held-out, native relaxed, Real11
extended and the encounter-groups p5 requests), 32 have no IPOPT point and every
one has a jump tick at least 28.5% short, so the 10% jump alarm catches all 32
while flagging 103 requests with no material gap and 93 with one
([shortfalls](2026-11-19/jump-shortfall.txt), [ground alarm value](2026-11-19/ledge-alarm-value.txt)).
With it in place the thin-ledge ground rule would alarm no further request.
Landing room on the final block does not predict infeasibility: the 32 land with
0.33 to 1.0 blocks of room before the near edge. In the one examined
([report](2026-11-19/infeasible-5e10cd17/report.md)) the leg asks for a sprint jump from
B5 that loops around the corner shared with the B6 pillar and the B22 slab to hit
the head on B22 and land on B5 again; the relaxation brakes the jump's push to 38%,
and IPOPT solves the request only with that push scaled to 60% or less.

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

## No lineage promotion

`ea33c191` removed promotion: a confirmed gap no longer sends the program's later
optimization requests and its descendants' to IPOPT; every request is solved
relaxed and only its own alarm calls IPOPT. A/B on the whole native suite with
every route at threshold 0 ([script](2026-11-19/ab_capture.py),
[comparison](2026-11-19/promotion-ab-compare.txt)), two runs per arm, each
repeating exactly: all 79 automatic searches common to both arms published the same
status, objective (to `1e-6`), finalization stage and termination, and every
replayable published route passed independent replay (28 searches in mocked unit
tests have no scene to replay, identically in both arms). Only the wall-in-flight
resume scene promoted: its 7 searches published 6.598204 in both arms with 103
IPOPT calls with promotion (100 promoted, 3 confirmations) against 93 without (93
confirmations, 78 of them material gaps, 13 relaxed candidates); its total search
time was 97 to 104 s with promotion and 105 to 118 s without. The Real11 fixtures
are unchanged at `ea33c191`: T64, objective -1093.2381026602416, 14/14/8 requests,
0 interim IPOPT workers, 1 finalization call, all replays passed.

## Extended course and the encounter-groups requests

The Real11 extended control (anchors b2 b3 b4 b5 b7 b20, goal b22) runs to its
300 s deadline on every revision. At `ea33c191` it issued 144 requests with one
IPOPT launch (one confirmation) and expanded 171 prefixes; stock `c9deef42`
issued 145 requests with 131 launches and expanded 161. The run is
generation-bound, not solver-bound ([profile comparison](2026-11-19/extended-profile-compare.txt)):
solver invocation fell from 30.3 s to 6.8 s and the interval screen from 49.7 s
(HiGHS) to 20.6 s (Clarabel), while program keys, preparation and model analysis
take 164 s. The call limit does not bind there (144 of 1024).

The encounter-groups checkpoint `4fa120e1` (with its run's patch) makes every
request an optimization, so its 334 distinct requests on the same course are the
population a relaxed confirmation would see. [`p5_study.py`](2026-11-19/p5_study.py)
([summary](2026-11-19/p5-study-summary.txt)) solved each with the production
relaxation and IPOPT from the request's own seed, the production confirmation
seed and a cold seed, plus 8 random restarts wherever an arm found no point or
the arms' objectives differed by more than 1e-4:

- The relaxation called 19 requests infeasible; no IPOPT run found a point for any.
- 32 relaxation-feasible requests had no IPOPT point; all 32 alarm (a jump tick
  60 to 62 short by 0.29 to 0.76), so confirmations reject them.
- 153 of 315 relaxation-feasible requests alarm: 32 full-infeasible, 53 material
  gaps and 68 false alarms (13.1 s of the 35.0 s confirmation CPU).
- 7 material gaps do not alarm (largest 1.37e-3): ground ticks 58 and 59 short
  by 0.34 to 0.63 on a 1.6 by 1.0 landing area, wider than a thin ledge.
  They are 4 situations, each in two requests that differ by one row: three are
  the first ground tick after landing on B5 moving -X while the objective needs
  +X, so the relaxation brakes with 39% to 63% of the acceleration unused; one
  runs along B5's X edge before a jump with 34% unused ([details](2026-11-19/p5-missed-gaps.txt)).
  Alarming every short ground tick would add these 7 and 8 false alarms (peak
  ground shortfalls 1.1% to 28.5%).
- Production seed: 0 false no-point and 2 optimizations more than 1e-4 short of
  the best known; own seed (stock): 2 false `Infeasible_Problem_Detected` in the
  capturing run, 1 on re-solve; cold: 1 and 3.

Row order ([`row_order.py`](2026-11-19/row_order.py), [summary](2026-11-19/row-order-summary.json)):
882 distinct requests from five captures, 691 with a spec, solved in sorted,
reversed and three shuffled row orders. No request switched between feasible and
infeasible; 5 switched between infeasible and undecided (an undecided request goes
to IPOPT), and the largest objective spread was 3.7e-6.

Retained solvers: an IPOPT confirmation takes a median 0.21 s, of which worker
start and imports are 0.09 s; a Clarabel solve takes 0.4 ms to assemble, 0.4 ms to
set up and 2.8 ms to solve ([times](2026-11-19/clarabel-phase-times.txt)). With one
or two IPOPT calls per run neither a persistent worker nor solver reuse changes a
run measurably. The search-level screen cache had 0 hits in 6041 lookups on the
extended course; the process-wide spec cache answered all 144 candidate requests
from their screens.
