# Automatic semantic corridors on the stored Real11 scene

The automatic fixture copies the current virtual-finish control's physical request, terminal predicate, initial `[0,8]` jump domain, policy and numerical budgets. It removes `spatialPoints`, `requiredSupports`, `comparisonTicks`, `terminalDirection` and authored intermediate `spatial_goals`. Spatial limits remain50 routes,5000 expansions,64 region occurrences and2 visits; the200-input horizon is a guard. The stored B6 box is unchanged; its reported rightward extension still has no exact replacement bounds.

The task is descending height7, player-center X<729.7 and Z>=−1095.3, minimum achieved tick followed by maximum Z there. Free overshoot may end below7 and airborne. Physical B7 contact is a separate route alternative. Neither the finite query envelope nor guide points add goal walls or a floor.

## Representation and native scope

Raw decomposition refines33 cells to47 around finite exposed supports. The routing graph uses checked overlapping free convex cuboids, explicit support surfaces and directed finite interfaces/contact transitions. Cuboids are expanded in all axis orders from exact free seeds and physical obstacle planes using existing open-solid subtraction. This removes artificial extended partition walls; it is not a completeness claim about maximal cuboids.

Normal/run-off proposals use the existing zero-threshold movement builders and compiled travel bounds. B1's ordinary proposals reach itself and B2; absent ordinary proposals leave other methods unknown. Last-support flight height and finish context carry across air portals. The spatial queue gives different source neighborhoods, supports and passage/finish regions service while retaining the full bounded frontier. It does not reset flight at portals or discard unknown mechanics as impossible.

Each route supplies physical comparison goals and outgoing guide directions to the existing DFS. Intermediate `customYaw` objectives use the compiled guide-objective API; the task objective retains its original schema. Normal landing opportunities are conditioned on active support stages before row construction. Fixed connected 3D sweep alternatives enter `ConstraintVariants`, and actual contacts/passages are audited with complete variant cursors. Multiple portals can cross within one input. Named step/grind route certification remains unknown if the shared trace omits its internal probes; no Python probe physics was invented.

Typed inner DFS states use schema6; outer route queue1, corridor2 and region graph1 have exact context fingerprints. New variants discovered after resume receive separate work owners instead of changing an in-flight frame's language. Global time/call/prefix caps remain enforced; each local generation allocation supplies a full configured choice batch. A zero remaining prefix quota drains existing ready work without inventing a new expansion.

## Coverage audit

The final source-hashed [coverage audit](coverage-allocation-context-final-2026-11-03.json.gz) admits the archived initial0,64-input witness as automatic route26 after independent replay. Scene/task/proposals and the unchanged spatial limits are the only enumeration inputs; the witness's controls and positions enter only after routes are generated. This is representation coverage, not a seeded search or new numerical solution.

Earlier failed audits are retained under their own paths. They exposed rigid raw-cell order, initial-neighborhood sampling, missing free-overshoot eligibility and missing continuous-flight proposal context. They are not final-source measurements. The final [raw cell rectangles](coverage-allocation-context-final-2026-11-03.json.cells.svg) and [transit/support rectangles](coverage-allocation-context-final-2026-11-03.json.regions.svg) use the local display crop. The transit view shows cuboids intersecting Y=6 plus red outlines of finite support surfaces at every height; those outlines are player-center support domains. The source geometry remains unmodified and the task has no added walls/floor.

## Contact-phase, promotion and streaming corrections

The full64-input witness was represented, but its B2 comparison prefix originally was not: truncating exactly at the support node forced the remaining X/Z to stay on B2. Intermediate views now permit outgoing air/portals before the next required contact and record the actual required occurrence separately. A final physical landing ends the route requirement at its contact phase while whole-input physical replay still runs; the virtual finish uses the full endpoint. Diagnostics record the route horizon phase and required occurrences.

Typed score selection can commit an already scored arrival before the next budget check. Its tried flag and child execution owner publish atomically, including when a window callback interrupts. The outer queue gives a newly committed child the next bounded allocation; a stalled route yields to other routes. It compares no unrelated goal arrival times and drops no sibling.

Typed generation streams the persistent iterator's first ready proposal to scoring. It does not fill the max_candidates buffer first; the other proposals remain in the iterator cursor. No domain/cap was widened and no proposal was discarded. The proposal, stage, commit and allocation policies are in continuation fingerprints. The old policy's queues are not silently migrated.

To reconstruct the before-source from the final feature, first reverse [allocation-context-fix.patch](allocation-context-fix.patch), then reverse [contact-promotion-fix.patch](contact-promotion-fix.patch) for the first matched run or [first-ready-streaming-fix.patch](first-ready-streaming-fix.patch) for the second. Both reconstruction chains were applied and checked in temporary copies; [hashes/checks](before-source-patch-checks.json) retain the results. The expected-red tests and earlier reports remain beside them.

## Allocation identity and generic matched controls

The first generic matched run completed both flat-gap arms, then exposed `ValueError: Incompatible movement generation checkpoint` in the intermediate-support automatic arm. Its partial files and [exception scope](generic-allocation-context-before.json) remain preserved. The same budget value appeared inside `scene.movementSearch` as well as in the normalized settings; a smaller later allocation changed the former's fingerprint.

Movement generation now uses context3: physical scene fields plus normalized domain settings, with allocation fields excluded once. The existing persistent-generation regression changes scene/settings allocation sizes across JSON reload, retains all30 candidate markers and still rejects a changed input horizon. Inner DFS and outer queue fingerprints include `movementGenerationPolicy=3`; older queues are explicitly incompatible, including legacy frames whose schema number is still5. Legacy supplied search semantics are unchanged. This is bookkeeping, not a widened domain or a discarded candidate.

[Fresh generic comparison](matched-generic-allocation-context-2026-11-03/summary.json) uses the same physical task and12s/8-call budgets for each arm, no movement/facing seeds. All four final witnesses pass independent replay, and sourceUnchanged=true.

| Scene | Automatic | Authored corridor/goals |
| --- | --- | --- |
| Flat gap |T14,1 call |T14,1 call |
| Intermediate support available |T17,8 calls, overflight |T24,3 calls, required landing then departure |

The second case demonstrates different contact itineraries; the authored route deliberately requires the intermediate landing, so its time is not a claim that the same route is optimized worse. None of these bounded searches proves global optimality. Complete settings, reports and exact requests are retained; [driver](compare_generic.py) and [fixtures](generic-fixtures.json) reproduce them.

## Numerical evidence

| Run | Recorded result | Interpretation |
| --- | --- | --- |
| `smoke-2026-11-03` |30s deadline;0 worker calls |8-prefix local allocation ended before allowed setup produced a landing proposal. Corrected to a full configured choice batch. |
| `smoke-choice-batch-2026-11-03` |Exception before dispatch |Found outgoing customYaw passed through the task-objective schema. Corrected to guideObjective; a native two-goal/strict-quota/resume regression now covers it. |
| `smoke-guide-objective-2026-11-03` |30s deadline;2 worker calls, validated17-input B2 arrivals, no full finish |Stage modeling executes. Source predates periodic snapshot throttling and the final quota boundary fix. All pending frames/candidates are retained in the report. |

The first matched run (`matched-2026-11-03/summary.json`) found no automatic finish in20 calls (actual308.2s against a300s deadline); supplied routing found T67, Z−1093.8231290646224 in41 calls. All7 unique automatic partial replays and51 supplied replays passed. The second run (`matched-contact-promotion-2026-11-03`) reached B3 but still buffered proposals: automatic9 calls/no finish, supplied41 calls/T67; all recorded replays passed. These are before-streaming measurements, not final-source claims.

The final [allocation-context native gate](allocation-context-final-native-2026-11-03.json) passes420 selected tests with zero failures/errors/skips in234.1s; sourceUnchanged=true. Its explicit47-module scope includes generic mixed ladder controls and excludes the dedicated ladder/climbable/vine/movement-entry modules.

The before-allocation-fix [streaming native gate](streaming-final-native-2026-11-03.json) passed420 tests with no failures/errors/skips in255.1s; sourceUnchanged=true. The47 modules include generic mixed ladder controls but exclude the dedicated ladder/climbable/vine/movement-entry modules with the recorded unrelated baseline. Earlier source-stable gates passed417 and419 tests at their recorded sources.

The source-stable before-allocation-fix [matched comparison](matched-streaming-2026-11-03/summary.json) also records sourceUnchanged=true:

| Arm | Declared / actual search seconds | Calls | Full finish | Independent replay |
| --- | --- | --- | --- | --- |
| Automatic |300 /306.1 |11 |None before deadline |5 unique partial witnesses, all passed |
| Supplied |300 /313.2 |41 |T67, Z−1093.8231290646224 |51 checks, all passed |

Automatic routing progressed through B2 and B3; its B4 fixed model remained unresolved. Saved queues retain unvisited routes, suspended programs and repair work. Long symbolic guard/physical-avoidance combinations and numerical unknowns remain a performance limitation. This comparison reports the limit; it does not bypass physical blockage/face-chain caps, discard requests or widen search domains. Actual deadline overshoot includes an in-flight bounded allocation/checkpoint return; independent post-search replay is recorded separately. Do not present a covered historical witness as a cold-search result or a deadline as infeasibility/optimality. Exact settings/source hashes and every numerical request/validation outcome stay in the reports.

## Final acceptance snapshot

[Final matched Real11 report](matched-allocation-context-2026-11-03/summary.json), after the allocation identity fix, records sourceUnchanged=true and the same settings/domains as the supplied control:

| Arm | Declared / actual search seconds | Calls / expanded prefixes | Full finish | Independent replay |
| --- | --- | --- | --- | --- |
| Automatic |300 /303.3 |13 /113 |None before deadline |5 unique partial witnesses, all passed |
| Supplied |300 /311.8 |41 /418 |T67, Z−1093.8231290646224 |51 checks, all passed |

The final native420-test gate, source-hashed coverage audit, generic matched controls and Real11 comparison all match the final runtime hashes. [Acceptance manifest](acceptance-manifest-2026-11-03.json) records those sources, settings, native scope and artifact hashes. Earlier reports above retain their own source-specific measurements; all failed/intermediate evidence is preserved. No runtime was changed during either final matched arm.

This feature completes the typed representation and existing-DFS integration. Automatic Real11 completion remains unresolved at the measured budget; neither route coverage nor a local timeout is an optimality/infeasibility proof. Exact revised B6 geometry, named internal-probe certification and executable Minecraft/float-angle replay remain outside this acceptance scope. No mod source, shared compiled helper or other-agent uncommitted work was changed.

## Commands

Run from the physical repository root with `OPENBLAS_NUM_THREADS=1`, `OMP_NUM_THREADS=1`, repository `PYTHONPATH`, and the existing native worker/helper (read-only):

```sh
python3 -B -m standalone.autonomy.cell_routes.automatic_experiment \
  --input standalone/autonomy/experiments/automatic-corridors/fixture.json \
  --output /tmp/automatic-new.json.gz \
  --compiler "$PKC_RUN_TICKS_COMPILER" --python "$PKC_IPOPT_PYTHON"
```

Resume with `--resume` and a new output path. Source hashes and exact continuations are saved; a changed physical/task/route/domain/policy context is rejected. The CLI saves the latest complete state periodically and always saves its final report.

For the no-seeding coverage/replay check:

```sh
python3 -B -m standalone.autonomy.cell_routes.coverage_audit \
  --input standalone/autonomy/experiments/automatic-corridors/fixture.json \
  --witness standalone/autonomy/experiments/virtual-finish/evidence/initial0-cold.json.gz \
  --output /tmp/automatic-coverage-new.json.gz \
  --compiler "$PKC_RUN_TICKS_COMPILER"
```

The SVG view is cropped to the original local scene only for display. Geometry and guards still use actual finite query coverage, without adding terrain or goal bounds. Compare numerical calls, goal completion, exact request identities and independent replay tiers separately.
