# Why the anchor run missed initial-0 / T64

**Superseded recommendation:** the user's correction confirms that source-local setup hops are compatible with a hard jump on every anchor span. The proposed initial-span exception at the end of this historical diagnosis was wrong. [Current correction](../anchor-transfer-preparation/README.md) preserves preparation globally and limits only the transfer. The recorded `result.json` describes the earlier `0d340c03` policy and remains unchanged. Reproduction on current source separately reports `currentEligibilityPolicyVersion`; its eligibility values then follow that policy, while the saved cold trace still describes policy1.

[Audit result](result.json), source `0d340c03`. No runtime or search policy changed. [Reproduction](check.py) performs no IPOPT solve: it reuses the archived initial-0 facings, replaces the old virtual final flight with an ordinary physical B7 landing, and independently replays the resulting program on the new extended-B7 scene. This is a seeded diagnostic, not cold discovery.

The physical T64 control passes scalar, finite-support and full native-scene replay, with unchanged horizontal trajectory. Final position is `(728.9505309738937, 7, -1093.267554656693)`, grounded. Thus the changed physical goal does not explain this miss.

The reference jumps at inputs `[0,12,21,30,40,55]`: jump0 returns to B1 at12; jump12 reaches B2 at21. Its preparation before B2 contains two jumps. The new cold run evaluated only the direct jump8→B2@17 at the root, then learned a global `b1→b2` single-jump latch. It never numerically evaluated initial-0. At the deadline its actual jump0→B1@12 program remains pending, and `single_jump_structure` rejects it under that latch because it lands back on the source after jumping. Restoration bypasses extra-run deferral, not this structural latch.

| Arrival | Initial-0 control | Initial-8 cold witness |
| --- | ---: | ---: |
| B2 |21 |17 |
| B3 |30 |28 |
| B4 |40 |40 |
| B5 |55 |58 |
| B7 |64 |67 |

There are two distinct limitations: service stayed in the initial-8 subtree until the deadline, and even if it returned, the learned initial-span latch would exclude the known setup hop. More wall time alone does not remove the structural restriction. B1 is an implicit starting endpoint rather than one of the caller's declared B2/B3/B4/B5 anchors; the implementation nevertheless makes its opening span latchable.

A narrow proposed correction is to preserve an open preparation span before the first declared anchor, while retaining learned hard-jump restrictions between declared anchors. Initial-0 setup work must also receive service. This is a recommendation, not an implemented policy change; the current user request asked for diagnosis.

Reproduce from the physical repository root with the established native compiler environment:

```sh
"$PKC_IPOPT_PYTHON" -B standalone/autonomy/experiments/anchor-initial0-audit-2026-11-04/check.py \
  --output /tmp/initial0-audit-new.json
```
