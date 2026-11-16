# Suppress already-visited constraint children before queueing

2026-11-15. Implements the first improvement following the
[enumeration diagnosis](../enumeration-attribution/README.md), based on
`efac6f94`, on `research/bookkeeping-performance`.

## Implemented change

The owner remains `ConstraintVariants.option`. While constructing children it
now checks the **existing** canonical node key against the authoritative
already-visited set. A state already visited cannot do useful work when popped
again, so that child is omitted before sorting and queue insertion.

This does **not** mark a queued state visited. An unvisited duplicate is still
eligible to supply the first-popped gate representative. The remaining stable
sort/pop order stays unchanged. No new reachability, suffix, comparison, route,
or physical rejection rule is added.

`_OptionKeys` freezes effective face rows for one invocation after item
preparation. It uses the existing `effective_key` to determine which rows are
already guaranteed and to hash each needed frozen-row sequence. Node hashes
still encode exactly `[chain, depth, start, effectiveRowsHash]`. Row-sequence
tokens refer to owned frozen scalar fields, preserving order, scalar types and
signed zero. Repeated row/node identities use bounded 1,024-entry caches. This
avoids moving the same expensive row reconstruction/serialization from pop to
enqueue. Preparation and caches are discarded at return or exception, rebuilt
after a mutation/new call, and never serialized into repair state.

## Why the same first representative survives

Within an option call the existing visited set only grows. A child already in
that set would enter the old loop's immediate duplicate `continue`; it would
emit no option, append no new visited identity and expand no descendants.
Removing it cannot reorder the other sorted children. All unvisited states
retain the old eligibility and order, including gate histories that differ
while queued. This is deliberately narrower than first-enqueued deduplication.

Prepared rows are frozen over the synchronous call's unchanged context. The
same interval/phase-order helper supplies their ranges. Dropping a row whose
single-row effective key is empty reproduces the existing canonical filter;
the authoritative effective-key function still merges the remaining rows.
The node-key bytes remain those of the old implementation.

## Fixed-work result

The unchanged reference is in `/private/tmp/pkc-enumeration-reference` at
`efac6f94`, with its own `.venv-reference` editable install. The implementation
uses its own `.venv-bookkeeping`. Both launch the same Python 3.12 binary; the
reference does not retarget the implementation or original session environment.
Each catalogue is run to exhaustion three times, sequentially without native
jobs in parallel. Item preparation and native startup are excluded.

| Captured catalogue | Ordered options | Reference checks | New checks | Reference / new median seconds | Speedup |
| --- | ---: | ---: | ---: | ---: | ---: |
| B6 / through 57 | 18 | 179,248 | 10,512 | 1.774 / 0.601 | 2.95x |
| B7 / through 67 | 46 | 693,120 | 36,828 | 7.425 / 2.175 | 3.41x |
| B6 / through 58 | 34 | 182,640 | 10,628 | 1.860 / 0.635 | 2.93x |
| B7 / through 64 | 46 | 627,828 | 35,070 | 6.367 / 1.787 | 3.56x |

Every complete catalogue matches its reference's exact hash of **ordered
options, selected gate histories, final cursor/visited lists, prepared item and
duplicate-variant count**. Iteration checks fall by 94.1–94.7%. These measure
the targeted enumeration work, not an asserted 3x whole-solver improvement.

The [comparison driver](../../tools/profiling/enumeration_compare.py) records
source hashes, interpreter/package paths, repetitions, timings and exact result
hashes. The four cases are the previously captured prepared inputs, not cold
search seeds. The compact regression fixture promotes one case with the old
ordered options and an actual legacy JSON cursor; ordinary tests do not load
large search reports.

## Resume and cache contracts

The cursor's fields, node hashes, option hashes and model/policy identities are
unchanged. A saved old frontier can still contain duplicate entries; the old
pop-time guard remains and consumes those safely. Newly built frontiers omit
entries already known to be no-ops. Thus raw mid-search frontier lists can be
shorter, while the still-unvisited order and chosen representatives match.
At exhaustion, complete cursor hashes match the old implementation.

Tests exercise the old saved cursor, a fresh JSON interruption/resume, timeout
during key construction, exact signed-zero identities, cache eviction and a
changed-model rebuild. No preparation timeout consumes a meaningful cursor
entry. There is no identity/schema bump because serialization remains compatible
and the ordered effective candidate stream is unchanged.

This change keeps original-state free-facing solves, ownership fields, resource
counts and the repair/store callback protocol intact. Numerical equations,
solver tolerances, outer DFS/branch scheduling, fixture domain and caps remain
unchanged.

## Native, cold and legacy-resume acceptance

The native gate passes **627 tests** with zero failures/errors/skips, including
the five new key/representative/resume regressions. Its dedicated
ladder/climbable/vine/movement-entry exclusions remain those of the recorded
baseline; generic mixed-ladder controls remain included. Layout/evidence checks
pass. Only `constraints/variants.py` changes in the 131-module production tree.

| 300-second enumeration-instrumented control | Before | After |
| --- | ---: | ---: |
| Visited entries | 15,183,845 | 2,506,708 |
| Already-seen entries popped | 14,340,595 | **0** |
| Returned local options | 1,144 | 3,167 |
| Numerical invocations / workers | 61 / 55 | 72 / 66 |
| Expanded prefixes | 39 | 39 |
| Independent retained-witness replays | 57/57 | 61/61 |
| Best T | 64 | 64 |
| Best Z | -1093.2381026602416 | -1093.4443574703134 |

The fresh cold control meets the handoff's T64/replay acceptance. It **does not
show improved end-to-end solution quality**: Z is worse in this single timed
control: the endpoint loses **0.20625481007186863 blocks** at the same T64.
Follow-up inspection identifies the missed winning branch, rather than merely
attributing the difference to timing:

- The old winner's fingerprint is
  `be95000c50b6f192cb3dfcea2156aa733aba2949b4b4ae7a4fd40a46f1125527`.
  It succeeded at old ledger invocation 23 and is absent from the new ledger.
- Both runs generated that exact repair branch in context
  `1e5c61ecd3b3461a370a9a9c85c6698d3669a96c816f9d738771ea4da7951c36`.
  The new run's second attempt in that context instead records
  `status=unknown, error=numerical_call_limit`, with no numerical diagnostics
  or problem hashes. The branch remains first in its final unresolved list.
- Event search gives each slice a shared six-call budget (`score_call_limit`),
  even though the per-final-evaluation allowance is 24 and global allowance
  1024. The shared budget was exhausted before this branch's worker launch.
  `retry_unknown=False` leaves unresolved branches parked on subsequent slices,
  while fresh constraint generation continues. Faster enumeration changes which
  branch encounters that boundary; a generated but never-solved branch therefore
  misses later fresh budgets.
- The retained new winner is old ledger invocation 17 / new invocation 24,
  fingerprint `1f1a52e04e4a81c07d23ca0aa92729b384ce81a29a21f0b505a275e1f54487f2`.
  It transitions around B6 one tick earlier: X at state 57 must already be
  at most `729.6999989880791`. The old winner permits the transition a tick later
  and has X `729.8616187010521` at that state, outside the new bound by
  `0.1616197129729926` blocks. The old winning model and path were therefore
  unavailable to the new run's completed solves.

The missed branch is a budget/retry scheduling interaction exposed by this
optimization; exact catalogue equivalence does not establish equal timed search
coverage. The proven performance result remains the complete-catalogue comparison
above. At this optimization baseline no scheduling fix was applied; the subsequent
user-approved [run-budget change](../anchor-run-budget/README.md) removes those
anchor service limits. Extracted requests, winning
branches, repair logs and generator states are under the ignored
`runs/endpoint-regression-investigation/`; the paired original reports and
request ledgers remain unchanged.

The old implementation's durable checkpoint from the earlier 60-second control
also resumes with `exact=true`. It makes five numerical invocations / three
workers, reaches T64 and passes all 15 retained-witness replays. No prior
feasible `(problem fingerprint, stopOnFeasible)` pair is invoked again in this
observed continuation. The generic exact-repair tests cover the stronger
context-specific completed-request contract. The resumed declared 60-second
budget stops at its deadline; measured search wall time is 68.847 seconds,
including in-progress callback/work overshoot. This is not an optimality result.

The [evidence index](evidence/2026-11-15/index.json) preserves before/after
timings and hashes, exact-order golden data, the native gate, cold counters,
legacy continuation and source snapshots. Prior evidence is unchanged. Large
manifest/SQLite pairs remain together in the recorded ignored run directories.

## Reproduction

From each checkout's `standalone/`, use its own installed interpreter. Run the
current comparison script by absolute path for the unchanged reference:

```sh
"$REFERENCE_PYTHON" -B /path/to/current/standalone/tools/profiling/enumeration_compare.py \
  --cases /path/to/option-cases.json.gz --output /new/path/reference.json
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.enumeration_compare \
  --cases /path/to/option-cases.json.gz --baseline /new/path/reference.json \
  --output /new/path/improved.json
```

Run the documented native gate and unrestricted Real11 control with this
worktree's compiler/interpreter paths; durable legacy resume is a separate
control. New output directories are required. Historical evidence is preserved.
