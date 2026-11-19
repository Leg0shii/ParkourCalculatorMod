# JVM-free movement bridge

2026-11-18. The user approved making the in-process Python bridge the default for
every standalone search entry point, with the Java bridge kept as an explicit
oracle. Base: `861390e1` (local PR 3). The runs below used the uncommitted
feature tree whose production sources are those of the commit adding this
directory. Raw runs (traces, solver-request ledgers, search states) stay outside
the repository; [comparison.json](2026-11-18/comparison.json) keeps each run's
settings, counters, action times, signatures and ordered `(request hash, status)`
list.

## Method

The traced harness ran the unmodified application (`search.application.run`) with
either bridge, recording every bridge exchange and every solver request. "Pre-change
Java" ran in a clean worktree at `861390e1`; "feature Java" is the same feature
tree with an explicit compiler descriptor. Other agents loaded the host (load
average 3 to 12, logged per run), so fixed-work runs raised the per-request budget
to 20 s and the session to 1,200 s: their outcomes then do not depend on load.
Progress runs kept the fixture's own settings (2 s per request, 300 s).

## Fixed work

| Run | Requests | Outcome | Coefficient s | Scene + footprint s |
| --- | ---: | --- | ---: | ---: |
| Opening, pre-change Java | 18 | T64, exhausted | 0.528 | 0.070 |
| Opening, feature Java | 18 | T64, exhausted | 0.552 | 0.058 |
| Opening, Python | 18 | T64, exhausted | 0.015 | 0.012 |
| B22, 20 requests, pre-change Java | 20 | B7@67, call limit | 1.437 | 0.064 |
| B22, 20 requests, feature Java | 20 | B7@67, call limit | 1.717 | 0.089 |
| B22, 20 requests, Python | 20 | B7@67, call limit | 0.098 | 0.012 |

Within each task all three arms have the same ordered request hashes and statuses,
search records, best witness and action counts. All 252 opening and 664 B22 bridge
exchanges have equal messages and equal replies, including one B22 scene rejection.
Retained witnesses replay 10/10 (opening) and 5/5 (B22) in every arm.

## Exact continuation across bridges

The opening was cancelled after 6 requests on one bridge and resumed from the saved
search state on the other. In both directions the 6 + 12 requests equal the
uninterrupted control's 18 in order, no completed request ran again, and the final
T64 witness and search records match the control.
`test_saved_searches_resume_exactly_on_the_other_bridge` checks the same at a
pending repair boundary on a small scene.

## Equal 300 s budget

| Run | Load at start | Requests | Expanded | Last anchor | Bridge s |
| --- | --- | ---: | ---: | --- | ---: |
| Pre-change Java | 12.0 | 112 | 132 | B7@67 | 7.785 |
| Python | 3.1 | 149 | 168 | B7@67 | 0.720 |
| Feature Java | 3.0 | 148 | 167 | B7@67 | 6.842 |

All three hit the deadline while searching for B20. The first 148 Python and
feature-Java requests are identical; the pre-change run's 112 are a prefix of both.
These are single runs under a shared, varying load; the bridge-time column is the
direct measurement.

## Installed wheel

The wheel was installed with the `numerics` and `reachability` extras into a fresh
environment. `parkour-solve` (physical anchors), `parkour-route` (two jumps),
`parkour-blocks` (required run) and `parkour-fixed-clock` (virtual finish) ran from
an empty directory with only that environment on `PATH` (no Java, Gradle or Git) and
no compiler descriptor; the checkout with an explicit Java descriptor was the
control. Solve, route and blocks outputs are equal apart from elapsed times; the
solve report records `sourceRevision: null` and the Python bridge. Fixed-clock runs
to its 300 s deadline with equal work counters (40 requests, 35 workers) and the same
T67 best; the Java run's 67 retained-witness replays are a prefix of the Python
run's 77. The virtual-finish fixture still sets `fair_parents`, which `1df37d63`
removed from the search API, so both bridges reject it as committed; these runs drop
that key.

## Test gates

The native gate on `861390e1` passed 727 tests, and on the feature 742 tests (the
new bridge module and package test added), both with no failures, errors or skips
and unchanged sources. The excluded dedicated ladder, climbable, vine and
movement-entry modules ran 99 tests on each side with the same 2 failures and 12
errors, all pre-existing.
