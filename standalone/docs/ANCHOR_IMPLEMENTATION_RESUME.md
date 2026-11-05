# Anchor DFS implementation: completed feature resume

**Latest correction complete:** the user clarified that hard means one transfer jump S→E, with repeated S→S setup hops still permitted even after a latch. Policy2 implements this for every source anchor, not only B1. `anchor_policy.transfer_structure` separates preparation/transfer impulses, `single_jump_structure` delegates to it, and DFS latch/rejection records expose the distinction. Semantic contexts fingerprint policy2 and reject wrong-policy exact checkpoints. [Correction evidence](../autonomy/experiments/anchor-transfer-preparation/README.md):535 native tests pass,101 runtime hashes stable, with51/51 accepted-witness replays across fresh cold arms. Initial0-only finds physical T64 in8.3855s (J6/R0/A58); combined0/8 still ends at67 after300s with initial0 queued and eligible. The structural bug is fixed; traversal/service order remains an explicit separate limitation. The original [audit](../autonomy/experiments/anchor-initial0-audit-2026-11-04/README.md) remains historical. No initial-span exception, physics change, cap widening or inter-block revisit generator was added. This correction's feature commit bundles the policy/tests/docs/diagnostics; consult git log.

2026-11-04. The authorized implementation is complete in `/private/tmp/parkour-real11-initial-run-ticks`, branch `research/real11-initial-run-ticks`. Use this task's isolated checkout; the tool default may point at the other agent's checkout. The feature commit bundles runtime, tests, fixture, documentation and evidence. Consult `git log -1` for its identity. No push, helper rebuild or actual-world/game execution was requested.

## Implemented behavior

- Physical automatic tasks now use required-anchor DFS; no anchors means one coupled open span. Real11 specifies B2→B3→B4→B5. Virtual tasks and supplied corridors remain separate source-specific controls.
- All continuous queries start at the original state with every earlier facing free. Feasible positions/velocities/yaws never become fixed seams. Prior physical contacts/required-anchor rows remain; exact internal-node queries omit the pending end-anchor predicate.
- Backward finite nearest-point guides and onward POSITION objectives remain soft. Intermediate queries stop on fully validated feasibility; terminal queries optimize the actual objective. No slack/new objective equation or guide-progress pruning was added.
- Meaningful-node feasibility runs when selected for descent, initially each completed jump/air landing/named case, not construction of unused siblings or raw ordinary run-holds. Anchors/final always force queries. Exact oracle rejection, physical repair exhaustion, suspension/error and feasibility are distinct; child failure never labels an unqueried ancestor.
- Absolute T, requested grounded J/grounded nonjump R/air A local to the anchor span. Valid jump cancellations count their horizontal impulse. A110° guide cue plus approach-facing physical edge/imaginary diagonal and0.3-block spatial margin gives one boolean per decision: ordinary `(T,-J,R,id)` or turnaround `(T,-J,A,id)`.
- Direct-jump success latches that anchor transition globally for the rest of the solve, including after changed incoming histories/certifier abandonment. Only one transfer; local setup hops remain eligible after the latch. Matching ordinary extra runs remain recoverable; restoring them walks actual frame ownership and uses a scoped bypass. Restoration preserves explicit input/run/method limits.
- Same-block setup hops/chains remain generated. Once a different physical owner is visited, the departed owner cannot return. Inter-block returns/repeated mandatory anchors remain deferred. Cancel/grind geometry touches do not invent support visits.
- Actual landing clocks bind before unused suffix lengths, with future-owned rows removed through the existing truncation path. Strict elapsed T>best validated finish rejects unchanged descendants; actual equal-time finishes still compete on objective.
- Persisted observations include decision/parent/span/depth/position source, edge distances, two labels/local counts/keys, query/selection/worker metrics, run deferrals/restorations and later witness label flips. Diagnostic drift never creates a seam or silently mutates queued work. Full-scene PDF/SVG/PNG figures show solids, covers, guides, decisions and trajectories.

Physical automatic continuation is outer schema2 / DFS8 / movement context4; solve-policy/validation identity uses repair assembly9. Old physical exact states are rejected for this policy. Current source is content-addressed by101 runtime hashes in [native acceptance](../autonomy/experiments/anchor-feasibility/native-acceptance-2026-11-04/report.json) and [manifest](../autonomy/experiments/anchor-feasibility/acceptance-manifest.json).

## Finite Real11 task and measured results

[Fresh fixture](../autonomy/experiments/anchor-feasibility/real11-anchors.json): B7 changes from `[729,0,-1095,730,7,-1094]` to `[729,0,-1095,730,7,-1093]`; actual physical landing, least T then MAXZ; terminal guide `(729.5,7,-1093)`. Old virtual/X<729.7 task rows are absent. Normal player-body overlap/phase contact semantics remain; the guide is not a player-center cap. Initial0/8 domain, horizon200 and other declared caps remain explicit. No fixed64. B6 remains unchanged because its replacement bounds were never supplied.

[Final source-stable native gate](../autonomy/experiments/anchor-feasibility/native-acceptance-2026-11-04/report.json):527 tests (447 inherited autonomy +26 new anchor +54 IPOPT), zero failures/errors/skips,248.906s. All101 runtime hashes stable; all54 old event-evidence artifact hashes unchanged. The exact scope is62 modules; dedicated autonomy ladder/climbable/vine/movement-entry suites remain outside the inherited52-module scope, with generic mixed ladder controls included.

[Matched cold finite-task arms](../autonomy/experiments/anchor-feasibility/matched-2026-11-04/summary.json), each declared300s:

| Arm | First finish | Best | Wrappers/workers/seed returns | Replays | Search/total wall |
| --- | ---: | --- | ---: | ---: | ---: |
| Guided early stop |14.0235s |T67, Z−1093.8231290646195, Y7/grounded |22/18/4 |22/22 |302.921/303.789s |
| Guided polish control |13.5634s |T67, Z−1093.8231290646193, Y7/grounded |22/22/0 |21/21 |312.580/313.198s |

Both learn all5 global hard latches and preserve9 deferred/restored runs. Sources remain stable. This shows similar first-finish timing, not a speedup or global optimality certificate. Full-scene [Real11 diagram](../autonomy/experiments/anchor-feasibility/matched-2026-11-04/guided_early_stop-full-scene.pdf) includes B6. Blue/orange points show the witnesses used when decisions were classified, which can differ from the final green trajectory after full-history re-solving.

[Generic native controls](../autonomy/experiments/anchor-feasibility/generic-2026-11-04/summary.json) pass3/3 independent replays: two setup hops on the current block then the goal (T36, J3/R0/A33); positive/negative-X reflected turns (both T18, J2/R0/A16), with turnaround decisions in the outgoing span. No archived seed. The20s turn allocations overrun slightly at boundary/serialization; exact walls/statuses are recorded.

Validation is continuous equations and full supplied collision-scene replay. Executable-angle/game/actual-world validation and global minimum-time/Z certificates are not established. Archived original-box virtual T67/T64 evidence stays unchanged and is not used as the new-task acceptance control.

## Files and commands

[Implementation/evidence README](../autonomy/experiments/anchor-feasibility/README.md), [controlling plan](../autonomy/ANCHOR_FEASIBILITY_IMPLEMENTATION_PLAN.md), [decisions](../autonomy/ANCHOR_POLICY_DECISIONS.md), [deferred revisit plan](../autonomy/REVISIT_DISCOVERY_DEFERRED_PLAN.md), [code map](CODE_MAP.md), [testing](TESTING.md) and [architecture](ARCHITECTURE.md).

Runtime additions: `cell_routes/anchor_policy.py`, `anchor_guides.py`, `anchor_adapter.py`, `node_feasibility.py`, `turn_observability.py`. Integration stays in existing checkpoint/DFS/event/frontier/solver/stepping paths. New suites: `test_anchor_policy`, `test_anchor_search`, `test_anchor_dfs`.

From the physical repository root:

```sh
export OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1
export PYTHONPATH="$PWD"
export PKC_IPOPT_PYTHON=/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes/.venv-guidance/bin/python
export PKC_RUN_TICKS_COMPILER=/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes/standalone/autonomy/build/compiler.json
"$PKC_IPOPT_PYTHON" -B standalone/autonomy/experiments/anchor-feasibility/native_gate.py --output /tmp/anchor-native-new
"$PKC_IPOPT_PYTHON" -B standalone/autonomy/experiments/anchor-feasibility/measure.py --output /tmp/anchor-matched-new
"$PKC_IPOPT_PYTHON" -B standalone/autonomy/experiments/anchor-feasibility/generic_controls.py --output /tmp/anchor-generic-new
```

Drivers refuse existing output directories. Do not rerun historical dated writers. Periodic progress checkpoints and copied runtime snapshots stay local/Git-ignored; final reports preserve full continuation, fixture/settings, dirty patch and source hashes. Final gzip payloads were recompressed deterministically with unchanged decompressed SHA256 ([storage ledger](../autonomy/experiments/anchor-feasibility/storage.json)). The interrupted/preliminary gates are explicitly development records, not final-source acceptance.

## Deferred follow-ups

Inter-block revisits/repeated required anchors, intentional wall hits, cadence/margin tuning, alternate temporal margin representations and actual-world/export validation remain deferred. The binary turn heuristic remains preliminary and observable. There is no pending approval/design question and no active solver/test process to resume. Do not restart M1 or confuse older planning-only statements with current implementation state.
