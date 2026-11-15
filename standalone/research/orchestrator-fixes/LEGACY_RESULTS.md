# Orchestrator coverage and continuation-state fixes

User ruling: keep the existing continuous solver. The orchestrator selects inputs and constraints within a spatial route before each IPOPT call. Facings remain continuous variables. Geometry/timing decisions belong in orchestration; a model-feasible trajectory rejected by physical geometry exposes an incomplete selected branch. See the [contract](../../cell_routes/ORCHESTRATOR_CONTRACT.md). No IPOPT model/interface change accompanies these fixes.

Implementation sources: `90ab0a28` fixes axis-corridor coverage and stores unresolved search state; `e3d1087b` preserves validated reference guesses while resuming repairs. This work remains on isolated branch `research/ipopt-cell-routes`.

## Fixed bugs

The corridor repair previously required an offending axis movement to fit within one original projected rectangle. This excluded valid movements across adjacent rectangles. `movement_regions` sweeps the rectangle union into exact axis-movement regions, including closed boundary slices. The orchestrator emits the existing X/Z position bounds. Tests check both axes/directions, shared boundaries, genuine gaps and equivalence against union coverage over a coordinate grid. This fixes geometric projection coverage, not every ordered-gate/physical decision coverage issue.

Contact search now serializes pending and unresolved rows, passage/hit selections, deduplication history, incumbent branch hints and facing guesses. Unknown prefixes carry that state through physical extension and resume matching branches. Search identity distinguishes different pending repair work. Original position/velocity, scene, corridor and the selected physical prefix are compatibility checked. Changed context starts a fresh search; compatible extension retains conditional branches but reopens the base branch because old failures are not infeasibility certificates. Every candidate is validated again. No intermediate X/Z endpoint or facing is pinned.

A regression during implementation showed why proposal guesses and validated references must be stored separately: replacing a supplied valid reference with an invalid scalar winner lost 0.9693 progress on an existing reverse-turn stress test. Repairs now prefer a fully validated reference guess when available and otherwise use saved proposal guesses. This is initialization only. The saved reference never substitutes for acceptance under new constraints. A numerical regression checks the previously successful objective.

The pilot still discovers some missing geometry through lazy validation/repair. These fixes do not claim all pilot calls already contain all relevant geometry. Completing fixed branch selection before IPOPT is remaining orchestrator work, not a reason to add route decisions to the continuous model.

## Measurements

At final runtime source `e3d1087b`, with the previous window fixtures and numerical limits unchanged:

| Window scene | Before these fixes | After |
| --- | ---: | ---: |
| Open | 12 | 12 |
| Ceiling | 13 | 13 |
| Multiple supports | 14 | 14 |
| Stepping | 14 | 14 |
| Dense grind/run | 11 | 11 |
| Blockage turn | Unknown | 13 |

The cold turn uses about 10.4 of 20 seconds and 75 of 128 permitted solves. Four tick-10 prefixes receive accepted scores, whereas none did before. This is a valid found route, not an optimality claim.

The 36 warm contract conditions yield 35 accepted cases (28/28 known-valid starts, 7/8 deliberately perturbed starts), versus 34 previously. No previously successful objective regresses by more than 1e-6. Reverse objectives are stress conditions rather than separate scene geometries.

A matched pause/resume probe uses the same cold complete programs and up to twelve solver calls. For the 12-tick program, restarting three four-call searches remains unknown after twelve calls; storing/resuming the frontier accepts a replayed route after seven cumulative calls. A single uninterrupted twelve-call allowance accepts after six. For the 16-tick program, all three modes accept; the four-call modes need four calls, while the single allowance spends twelve including continued optimization. These are six bounded conditions, not a broad scheduling-speed claim.

All 92 returned witnesses pass independent replay: 35 warm, 52 window/goal, five pause/resume. These counts are distinct from older archived matrices. One new run per condition is saved; no repeatability, completeness, or global-optimality claim is made. Independent replay uses the scalar/shared finite-scene models, not an actual Minecraft client.

## Validation and reproduction

The focused compiler/IPOPT integration gate passes 63 tests. Wider autonomy runs 394 tests, with 84 environment skips and no failures after excluding the already documented baseline repeated-ladder-ceiling failure. No mod Gradle suite was run.

Use the standalone compiler/IPOPT environment, with `OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1`:

```sh
.venv-guidance/bin/python -m standalone.autonomy.cell_routes.fix_validation \
  --part warm --output standalone/autonomy/build/orchestrator-fixes-warm.json.gz
.venv-guidance/bin/python -m standalone.autonomy.cell_routes.repair_resume_experiment \
  --output standalone/autonomy/build/orchestrator-fixes-resume.json.gz
.venv-guidance/bin/python -m standalone.autonomy.cell_routes.window_experiment \
  --input standalone/autonomy/experiments/decision-windows/fixtures --gaps 0.25 \
  --output standalone/autonomy/build/orchestrator-fixes-windows.json.gz
```

Exact requests/programs, selected guards, resumed state, accepted witnesses, work summaries and test logs are in [evidence](evidence/summary.json), with source and SHA-256 hashes in [the manifest](evidence/manifest.json).
