# Anchor DFS and physical Real11 finish

Implemented on `research/real11-initial-run-ticks` in `/private/tmp/parkour-real11-initial-run-ticks`. This feature uses the existing movement/constraint/native validation pipeline. The [controlling plan](../../ANCHOR_FEASIBILITY_IMPLEMENTATION_PLAN.md) and [decisions](../../ANCHOR_POLICY_DECISIONS.md) explain the policy; [resume](../../../docs/ANCHOR_IMPLEMENTATION_RESUME.md) records current status.

Physical automatic tasks now compare only at required anchors/final task. Missing anchors means one open span. Internal meaningful decisions are solved when selected for descent, retaining all earlier facing freedom. Guided intermediate queries stop on feasibility; the final objective is optimized. Exact oracle rejection, physical repair exhaustion and suspended/error work are separate outcomes. A child rejection never marks an unqueried ancestor infeasible.

The new [Real11 fixture](real11-anchors.json) supplies B2/B3/B4/B5, keeps the explicit0/8 initial jump domain and existing budgets, and extends B7 to `[729,0,-1095,730,7,-1093]`. The task requires actual physical B7 landing, least T then MAXZ. Its guide point `(729.5,7,-1093)` does not constrain the player center. B6 is unchanged because its replacement bounds have not been supplied. The [old virtual-task evidence](../event-route-discovery/README.md) remains immutable and is not acceptance for this task.

Direct-jump success globally latches that transition to one-jump structure. Matching ordinary extra runs are retained and restored on backtracking, without widening declared caps. Same-block jumps/chains are generated; departed physical owners cannot be revisited. A cancel/grind impulse counts J without inventing a landing on the touched obstacle. Full future revisit work remains [deferred](../../REVISIT_DISCOVERY_DEFERRED_PLAN.md).

The preliminary turn rule uses a110° cue, physical approach edge/imaginary diagonal and0.3-block margin. It assigns one whole ordinary `(T,−J,R,id)` or turnaround `(T,−J,A,id)` key. Counts are local to the required-anchor span. Geometry, signed distances, labels, keys, selection/query metrics, run deferral/restoration and later witness label flips are observable. Diagnostic drift never fixes a pose or silently changes a queued key.

## Validation status

[Final native acceptance](native-acceptance-2026-11-04/report.json): **527 tests, zero failures/errors/skips**,248.906s,101 runtime hashes unchanged. The scope is the inherited52 autonomy modules, three new anchor suites and seven IPOPT modules. All54 historical event-evidence artifact hashes remain unchanged. A preliminary523-test run overlapped final source edits; its `sourceUnchanged=false` excludes it from acceptance. [Matched physical-task summary](matched-2026-11-04/summary.json): both cold arms found T67, Y7/grounded, Z≈−1093.823129064619. All43 independent accepted-witness replays pass, source hashes stay unchanged, and both arms preserve9 deferred/restored run alternatives plus all5 global hard latches.

| Arm | First full finish | Wrappers / workers / seed returns | Expansions | Accepted-witness replays | Search / total wall |
| --- | ---: | ---: | ---: | ---: | ---: |
| Guided early stop |14.0235s |22 /18 /4 |108 |22/22 |302.921 /303.789s |
| Guided polish control |13.5634s |22 /22 /0 |113 |21/21 |312.580 /313.198s |

Each arm declares300s. Actual wall includes boundary/serialization overrun; total additionally includes post-run replay. This single matched comparison shows similar first-finish time, not a speedup claim. Early stop avoids four worker launches through validated feasible seeds. It does not establish global minimum T or Z optimality. The stored scene and continuous facings are validated; executable-angle/actual-world replay is not part of this evidence.

[Early-stop diagram](matched-2026-11-04/guided_early_stop.pdf) and [polish diagram](matched-2026-11-04/guided_polish_control.pdf) show full required-anchor guides/decisions and the best trajectory. Gray arrows are backward guide segments, green is the validated trajectory, dotted rectangles are exposed body-overlap covers, blue/orange points are ordinary/turnaround decisions, red crosses denote replay label flips, and purple edge/margin/shading shows any inferred turnaround. The Real11 guide has no>110° turnaround; reflected generic turn controls exercise those labels. Full-scene variants include B6's obstacle footprint.


The interrupted `native-2026-11-04/tests.log` and preliminary `native-final-2026-11-04/` are development records. Do not describe them as final-source acceptance. New output directories are created exclusively; historical reports are never overwritten.

## Reproduce

From the physical repository root, set `PYTHONPATH` to that root and set `OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1`. Reuse the existing read-only native Python/compiler dependencies from [the resume](../../../docs/ANCHOR_IMPLEMENTATION_RESUME.md).

```sh
"$PKC_IPOPT_PYTHON" -B standalone/autonomy/experiments/anchor-feasibility/native_gate.py \
  --output /tmp/anchor-native-new-run
"$PKC_IPOPT_PYTHON" -B standalone/autonomy/experiments/anchor-feasibility/measure.py \
  --output /tmp/anchor-matched-new-run
```

The native driver runs the preserved52-module autonomy scope, the three new anchor suites and all IPOPT test modules, recording exact source/test hashes and immutable-history checks. Dedicated autonomy ladder/climbable/vine/movement-entry suites remain outside that inherited scope; generic mixed ladder controls still execute.

The measurement driver runs cold guided early-stop and guided-polish control arms on the same new fixture/domain/guides/allocation. Only intermediate `stopOnFeasible` differs; terminal optimization is identical. It copies hashed runtime sources, records dirty source, saves resumable progress, independently replays accepted anchor/final witnesses and exports SVG/PDF guides/decision diagrams. `--seconds` can declare a different matched wall allocation. No archived programs/yaws seed discovery.

Validation covers continuous equations and the full declared collision scene. Executable-angle/game/actual-world replay and global minimum-time/optimality certificates are outside this evidence. Wall/call/horizon limits are recorded as limits, not infeasibility proofs.

Periodic `*.progress.json.gz` checkpoints and copied `matched-*/runtime/` sources remain local and ignored by Git to avoid duplicating final continuation reports and the committed production tree. Final reports preserve continuation state, full source hashes, dirty patch and fixture; native reports also hash the test sources.

## Generic native controls

[Cold generic controls](generic-2026-11-04/summary.json) pass3/3 independent native replays with unchanged source. The same-block case completes two setup hops on its original block then the goal jump: T36, J3/R0/A33, no archived seed. The positive/negative-X reflected turn cases both finish T18, J2/R0/A16, and select the turnaround heuristic in the outgoing span. These controls test same-owner generation and reflected binary membership beyond Real11; they do not certify global optimality.

The generic controls declare20s each; actual walls1.73s,22.69s and23.61s respectively. The two turn cases stop at their budget boundary with feasible incumbents. Full logs/fixtures/counters and continuation state are in their compressed reports. [Positive turn diagram](generic-2026-11-04/turn-positive-x-full-scene.pdf) and [reflected turn diagram](generic-2026-11-04/turn-negative-x-full-scene.pdf) include the physical edge, margin and colored decision points.

[Full-scene Real11 diagram](matched-2026-11-04/guided_early_stop-full-scene.pdf) includes B6 and every other declared obstacle footprint; filled gray boxes are actual solids' horizontal footprints. The main measurement diagrams show guide-anchor footprints only. `render_full_scene.py` reads a completed report without rerunning physics. Final report storage is deterministically recompressed after measurement, checking unchanged decompressed SHA256; [storage ledger](storage.json) records both sizes and payload identities.

[Acceptance manifest](acceptance-manifest.json) reconciles all101 runtime hashes,527 native tests,43 matched accepted-witness replays, three generic controls and54 unchanged historical artifacts.
