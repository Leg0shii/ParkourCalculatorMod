# Hard transfer with source-local preparation

The user's correction applies to every anchor span: a hard jump has **one transfer jump from S to E**. Multiple setup jumps that land back on S remain eligible before that transfer, even after a global latch. The previous `0d340c03` implementation incorrectly imposed one requested impulse across the entire span and rejected returning to S. It was wrong to propose an initial-span exemption as the solution.

`anchor_policy.transfer_structure` now identifies the last completed source contact before arrival. Earlier requested impulses are preparation; the transfer has one impulse. Same-input lower/source contact during target arrival remains part of the same transfer case. Physical-owner aliases are preserved. Pending source hops and unfinished flights are retained; optional intermediate supports, multiple transfer impulses without a source landing, and a completed zero-jump run-off are outside the hard-transfer structure.

All setup impulses still count in local J and remain subject to explicit caps. All earlier facings remain variable. Original-state solves, binary turn keys, run-family normalization/restoration and no inter-block returns are unchanged. The global latch still applies across incoming histories and backtracking; it constrains the transfer rather than deleting its preparation.

Hard-jump policy2 is fingerprinted in the outer, DFS, stage and movement contexts. Exact continuation from the wrong policy is rejected because it may already have discarded setup work. Latch and arrival records expose preparation inputs, transfer input and source-contact boundary.

## Validation

Focused68 tests pass. They include arbitrary source-hop chains, noninitial source boundaries, physical aliases, same-input cases, declared caps, optional-support/two-transfer rejection, the actual Real11 initial-0 prefix, and generation/resume/backtracking after a latch has already been learned. [Source-stable native gate](native-2026-11-04/report.json):535 tests, zero failures/errors/skips,256.431s, with101 runtime hashes unchanged and the prior54 event-evidence artifacts unchanged. Fresh cold evidence is complete; both arms retain unchanged source hashes and all51 recorded accepted-witness replays pass. No archived programs/yaws or later decisions are supplied.

The [historical initial-0 audit](../anchor-initial0-audit-2026-11-04/README.md) validated a physical T64 landing using archived facings. That is a seeded diagnostic, not cold evidence for this correction. New cold runs use no archived programs/yaws; an initial-0-only arm restricts the initial domain without supplying later decisions.

## Cold outcomes and remaining traversal limit

| Arm | Declared wall | First full finish | Best T / Z | Wrappers / workers / seed returns | Accepted-witness replays |
| --- | ---: | ---: | --- | ---: | ---: |
| Initial0 only |90s |8.3855s |64 /−1093.4443574708994 |42 /32 /8 |25/25 |
| Combined0/8 |300s |14.2448s |67 /−1093.8231290646195 |53 /45 /8 |26/26 |

Both finishes physically land on B7 at Y7/grounded. Actual search/total walls are93.796/94.456s and301.176/301.874s, respectively, including boundary/serialization overrun and post-run replay. These arms have different initial domains/allocations and are coverage diagnostics, not a speed comparison. T64's Z remains below the separately replayed archived-yaw control; no optimality claim is made.

The [initial0 summary](initial0-90s-2026-11-04/summary.json) certifies the opening with policy2:

```text
preparationJumps: [0]
preparationThrough: 12
transferJumps: [12]
B2 arrival: 21
```

Subsequent jumps at21,30,40,55 reach B7 at64, with J6/R0/A58 overall. The source setup remains legal after the latch; the same rule is tested after prior-anchor arrival, learning, backtracking and resume.

The [combined summary](combined-300s-2026-11-04/summary.json) still ends at67. The [saved queue audit](combined-queue-audit.json) confirms its initial-0 setup at12 remains queued **and eligible** under policy2. The run never returned to that root alternative before its limit. This remaining traversal/service-order limitation is distinct from the corrected structural rejection; this fix does not change branch service order or pretend combined cold discovery reached64.

[Native report](native-2026-11-04/report.json) and the new manifest record exact source/test hashes. Prior event and anchor-feature evidence remains immutable. New report compression checks unchanged decompressed SHA256 in [storage.json](storage.json). The [policy2 manifest](manifest.json) reconciles535 native tests,51 accepted-witness replays and unchanged prior artifacts. [Initial0 route diagram](initial0-90s-2026-11-04/route.pdf).

## Reproduce

From the physical repository root with the existing read-only native Python/compiler environment:

```sh
"$PKC_IPOPT_PYTHON" -B standalone/autonomy/experiments/anchor-feasibility/native_gate.py \
  --output /tmp/transfer-preparation-native-new
"$PKC_IPOPT_PYTHON" -B standalone/autonomy/experiments/anchor-transfer-preparation/cold.py \
  --output /tmp/transfer-preparation-initial0-new --seconds 90 --arms initial0
"$PKC_IPOPT_PYTHON" -B standalone/autonomy/experiments/anchor-transfer-preparation/cold.py \
  --output /tmp/transfer-preparation-combined-new --seconds 300 --arms combined
```

Fresh directories preserve earlier evidence. This change does not exempt any anchor from hard-transfer semantics, enable inter-block revisits or widen input/run/method limits. Supplied-scene/native replay differs from executable-angle/game/actual-world replay; no global optimality certificate is claimed.
