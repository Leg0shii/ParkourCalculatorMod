# Shared hard-jump comparisons and proposal reachability

For the complete current combination catalogue, pruning/deferral/IPOPT dispatch tables and decision rationale, read the [execution reference](../../../docs/ANCHOR_SEARCH_EXECUTION_REFERENCE.md). Runtime feature commit: `dd738c98`. This README records the measured implementation acceptance; its dated source/provenance artifacts are unchanged by documentation updates.

2026-11-11. Implements the [single controlling plan](../../../docs/HARD_JUMP_TICK_PRUNING_AND_REACHABILITY_PLAN.md)
on baseline `5d54f02b` in the session checkout. **Declared assumption:** a hard
transfer between anchors never uses stepping. Source-local setup hops remain
legal; completed stepped transfers cannot establish/satisfy the hard latch.
Only shortening ceiling contacts affect the conditional ordinary flight bound.
Generic stepped/cancelled controls remain valid outside this hard domain.

The shared comparison module owns T/J/R/A, priorities, independent more-J OR
fewer-R/fewer-A protections, equality and strict full-finish bounds. Slower
unprotected arrivals defer against compatible validated peers and restore one
at a time on explicit failure. A bound later than the incumbent overrides count
protection. Incumbent updates perform no queued-bound scans. Terminal ranking
still minimizes actual T then maximizes the requested objective.

`InteractionGate` shares fixed-program preparation, exact compiler payloads,
envelopes and screens. Lower/higher stepping conjunctions are checked before
side/corner fan-out. A common proposal composer preserves consumed raw ordinals,
screens unchanged models once, and counts/checks owned arrivals independently
from unused tails. Changed model rows get their own analyses. All original-state
continuous facings stay free; guide objectives and early feasible stopping remain.

# Validation

[Native gate](2026-11-11/native-gate/report.json): **600 tests pass, no failures,
errors or skips**, 226.94s; all runtime hashes and54 historical evidence artifacts
stay unchanged. The recorded module list retains the prior scope: dedicated
ladder/climbable/vine/movement-entry modules are excluded, while generic mixed
ladder controls and all IPOPT modules are included. Five scoped profiler tests
also pass. No native helper rebuild was needed.

The [witness-bound audit](2026-11-11/witness-bound-controls.json) applies current
bounds to **all131 prefixes** of the archived accepted T67/T64 finishes, with all
latches activated. None exceeds its known finish. These controls are never seeded
into cold discovery and do not prove global admissibility or optimal time.
[Current domain controls](2026-11-11/current-domain-controls.json) preserve the
two-input generic stepping examples while explicitly rejecting their hard-transfer
classification. The old policy2 probe JSON remains unchanged.

The focused tests cover normal/turn protections and equal times; exact-node
recovery/resume; hard latches with multiple source hops; an owned valid arrival
with a contradictory unused tail; JSON alias loss; consumed-ordinal composition;
row-conditioned cache isolation; B3@28 conjunction rejection before wall/corner
builders; reachable/far ceilings and unknown fallback; phase-correct post-landing
enclosures; latch activation; and paid airborne clocks.

# Matched cold arms

[Full comparison](2026-11-11/comparison.json) includes per-span counters and profiles.

| Arm | Finish T | Z | Wrappers / workers | Expansions | Independent replays |
| --- | ---: | ---: | ---: | ---: | ---: |
| Combined baseline300s |67 |−1093.823129063534 |198 /159 |1,114 |75/75 |
| Combined updated300s |64 |−1093.2381026602416 |61 /55 |35 |57/57 |
| Initial0 baseline90s |64 |−1093.4443574703134 |73 /56 |427 |34/34 |
| Initial0 updated90s |64 |−1093.2381026602416 |26 /22 |7 |23/23 |

First full-finish times (initial T67 for combined) are15.631s baseline versus
14.839s updated; initial0 is8.392s versus8.304s. The combined updated progress
snapshot already shows T64 by33.732s. Actual search walls are300.851/301.256s
combined and90.017/90.612s initial0. Budget/worker guards can overshoot slightly.
Runtime hashes stayed unchanged in every arm. Both updated runs stop on the
wall guard; minimum time/global objective optimality remains unproved.

The combined updated run records88 local assessments,49 decision-cache hits,
84 peer comparisons,8 recoverable deferrals and0 restores. All61 invocation
fingerprints are unique. Its22 saved time-bound exclusions include18 with a
positive hard-flight contribution; two final comparison records retain explicit
count protection. The individual contributions of bound pruning, deferral and
early generation changes are not isolated by these combined-feature arms. Shared-analysis counters record239 coefficient-cache
hits,215 preparation hits,215 envelope hits and18 early common-sequence
exclusions. Reconstructed iterator calls include replayed consumed descriptors;
they are not counts of unique full programs.

The combined profile attributes only0.0032s inclusive to581 shared comparisons,
0.0039s to201 remaining-bound evaluations,0.0017s to88 peer assessments and
0.0001s to five duration derivations (these nested times overlap). Generation
source self time falls from79.14s to0.24s and movement-choice self time from
42.68s to0.29s in these runs. The remaining measured costs are mostly inside
numerical preparation/refinement:146.19s evaluation self time,66.69s digest
self time,30.56s prefix-retention copies and17.25s repair snapshot copies.
These are instrumentation categories, not four independent proposed fixes;
digest/copy work and profiler overhead are included. Worker self time is9.89s.
No further optimization of that remaining work is bundled into this feature.
Both baselines were captured before production edits. Combined keeps the declared
initial0/8 domain and300s wall guard; the separate initial0 control changes only
that domain to `[0]` and uses90s before/after. Fixtures, caps and per-candidate
budgets otherwise match. Each run independently replays every retained accepted
witness after search. No archived yaw/decision/trajectory seeds enter discovery.

The20-second [smoke](2026-11-11/smoke-combined/summary.json) found T67 at14.14s,
with9/9 accepted-witness replays. It overlapped the native gate and is only an
integration control. The first roughly30s of the updated combined arm overlapped
the read-only archived-witness audit. It should not support a precise isolated
wall-speedup claim. Post-change instrumentation also adds shared-service timings
and invocation fingerprints; overhead is included in reported times. Count/search
outcomes concern these single source-specific runs, not statistical guarantees.

# Reproduction and evidence

From `standalone/`, use the runbook native environment:

```sh
export OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 PYTHONPATH=..
export PKC_IPOPT_PYTHON="$PWD/../.venv-guidance/bin/python"
export PKC_RUN_TICKS_COMPILER="$PWD/autonomy/build/compiler.json"
../.venv-guidance/bin/python -B autonomy/experiments/hard-jump-comparisons/native_gate.py --output /tmp/hard-jump-native-new
../.venv-guidance/bin/python -B autonomy/experiments/anchor-runtime-profile/profile_run.py --input autonomy/experiments/anchor-feasibility/real11-anchors.json --output /tmp/hard-jump-combined-new --seconds 300
../.venv-guidance/bin/python -B autonomy/experiments/anchor-runtime-profile/profile_run.py --input autonomy/experiments/hard-jump-comparisons/2026-11-11/initial0.json --output /tmp/hard-jump-initial0-new --seconds 90
```

Output paths must be new. Each arm preserves settings, runtime hashes, compiler
config hash, source patch, summary/profile, instrumentation snapshot and a paired
manifest/SQLite checkpoint. Use `checkpoint_store.read_report`, not raw gzip JSON,
to decode that pair. Updated arms add `requests.json`: exact request fingerprints
at invocation, ticks, stop policy and wrapper/worker status, including successful
internal nodes. Repeated fingerprints can be deliberate resumed unknown retries;
they do not alone establish wasted successful solves.

The three newly added runtime modules are also preserved in
[the additive source patch](2026-11-11/runtime-additions.patch), supplementing the
tracked-file `source.patch` in each updated arm. Together with baseline HEAD and
recorded hashes these identify the tested runtime. Baseline instrumentation
snapshots and historical artifacts were not rewritten. [Artifact hashes](2026-11-11/artifact-manifest.json)
cover the evidence once the final arms complete.

`witness_bound_audit.py` checks archived known completions without NLP calls.
`summarize.py` assembles matched results and saved new-policy counters. Fresh
acceptance remains against the declared scene (extended physical B7, stored B6);
the reported rightward B6 extension still lacks replacement coordinates. No
actual-world replay or global optimality certificate is claimed.
