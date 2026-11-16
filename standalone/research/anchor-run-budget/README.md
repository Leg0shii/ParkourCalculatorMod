# Continuous anchor search under the run budget

2026-11-15. User requested removal of service-turn batching after the
[enumeration optimization](../enumeration-reduction/README.md) exposed an
unstarted winning request stranded by the six-call limit. This is an authorized
scheduling change, separate from the earlier performance-preserving work.
Implementation branch: `fix/anchor-run-budgets`, based on `86c06e39`.

## Behavior

`search/events/engine.py` owns the automatic run controller. Physical anchor
search now gives its existing DFS the remaining session deadline and global
solver-call allowance. Inner evaluations inherit that deadline without an
independent call allowance. The old six-call/eight-second service turns and
24-call terminal evaluation limit no longer interrupt anchor traversal.
Per-request `candidate_seconds`, cancellation, search-domain/generation limits,
physical checks, DFS order and comparison rules remain in force. This does not
claim exhaustive coverage of every physically possible route.

The public API still accepts `score_seconds`, `score_call_limit` and
`final_call_limit` for existing inputs and non-anchor controls; these limits do
not apply to anchor mode. The generic non-anchor branch-rotation scheduler keeps
its original budgets. No optional flag selects the old anchor scheduling. The
maintained Real11 input removes the three obsolete local-budget settings.

`search/repair.py` checks work before generation and again before dequeue after
generation. A branch that cannot start within the run budget stays pending,
rather than producing an unknown result that needs explicit unknown retries.
Genuine numerical unknown results retain their explicit-retry contract.

Budget settings are already excluded from physical/continuation identities.
This change preserves saved cursor/schema/model identities, accepts compatible
earlier checkpoints and preserves completed exact work. It does not automatically
retry numerical unknowns already recorded by an earlier run.

## Validation

The subsequent [candidate audit](RUN8_CANDIDATE_AUDIT.md) identifies the exact
additional run-8 obstacle passages and their outcomes. Finishing the current DFS
branch is intentional; the audit changes no scheduling policy.
The [B6 follow-up](B6_PASSAGE_CHECKS.md) separates the two physical sides from
redundant phase descriptions and identifies which checks reject each timing.

Focused regressions cover 90 requests across multiple route evaluations in one
allocation, despite legacy local settings of one call and 0.01 seconds; the
global deadline; JSON continuation; pending work at global call exhaustion and
generation expiry; and unchanged explicit retries for numerical unknowns.

The selected native gate passes **631 tests**, with zero failures, errors or
skips and unchanged source hashes during the gate. Its existing exclusions are
dedicated ladder/climbable/vine/movement-entry modules; generic mixed ladder
controls and numerical modules remain included. Layout validates all 902 protected
evidence/fixture files. Package-layout checks also pass (five tests).

The fresh cold control confirms removal of the quotas, but has **worse timed
route quality**:

| 300-second control | Best T | Best Z | Requests / workers | Expanded prefixes | Retained-witness replays |
| --- | ---: | ---: | ---: | ---: | ---: |
| Enumeration optimization, local quotas | 64 | -1093.4443574703134 | 72 / 66 | 39 | 61/61 |
| Continuous anchor run budget | 67 | -1093.823129063534 | 49 / 47 | 21 | 41/41 |

The new run records one continuous allocation of 49 requests, ending at the
global deadline (303.488 seconds measured search wall time, including overshoot).
Of those requests, **42 optimize 67-tick journeys; none request 64 ticks**. The
longest route evaluation takes 200.593 seconds. A full result reaches the outer
DFS at 208.580 seconds; successful inner candidates can exist before that return.
This demonstrates that returning from a limited evaluation influenced subsequent
DFS exploration even without explicit outer branch rotation. Continuous traversal
now spends the run on the earlier journeys' constraint alternatives. The old
64-tick winner is not numerically rejected; the run never reaches its query.

This implements the requested removal without substituting a new branch-priority
policy or reinstating another quota. Better scheduling between terminal refinement
and other route choices remains separate work. The native gate passing does not
establish equal timed search coverage or improved solution quality.

A pre-change durable 60-second checkpoint resumes with `exact=true`, session2,
using the new budgets. The requested 10-second continuation takes 12.821 seconds
including overshoot, makes one worker request, retains its T67 incumbent and
passes all 11 retained-witness replays. Its request does not repeat a previously
feasible `(fingerprint, stopOnFeasible)` pair in the original ledger. JSON and
native continuation tests cover pending work and completed exact-context reuse.

The [evidence index](evidence/2026-11-15/index.json) records native acceptance,
source patch/hashes, complete cold/resume request ledgers, settings and compact
comparison data. Large manifest/SQLite pairs remain in the recorded ignored run
directories. Historical reports and evidence are unchanged.

## Reproduction

From this checkout's `standalone/`, set `PKC_IPOPT_PYTHON` to its own
`../.venv-bookkeeping/bin/python` and `PKC_RUN_TICKS_COMPILER` to its own
`build/native/compiler.json`; set `OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1`.
Use fresh output directories:

```sh
"$PKC_IPOPT_PYTHON" -B tools/native_gate.py --output /new/path/native
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input cases/real11/physical-anchors.json --output /new/path/cold \
  --seconds 300 --profile-mode enumeration
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input cases/real11/physical-anchors.json --output /new/path/continuation \
  --resume /new/path/cold/report.json.gz --seconds 10 --durable \
  --profile-mode enumeration
```

The profiler records base revision, source patch/hashes, fixture, interpreter,
native descriptor, request ledger and independent retained-witness replays.
Report manifests require their adjacent SQLite companions. Timed coverage is a
scheduling observation, not an optimality certificate or isolated speedup claim.
