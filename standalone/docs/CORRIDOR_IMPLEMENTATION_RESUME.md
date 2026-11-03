# Resume after compaction: implement the spatial corridor refactor

Prepared2026-11-03 at the user's request before compaction. The user intends implementation next; this turn only made the plan/handoff thorough. Continue the existing task in the existing isolated branch. Do not restart the research or ask again whether to implement merely because context was compacted.

## 1. Checkout and exact state

- Physical repo: `/private/tmp/parkour-real11-initial-run-ticks`.
- Standalone root: `/private/tmp/parkour-real11-initial-run-ticks/standalone`.
- Branch: `research/real11-initial-run-ticks`.
- HEAD: `984b701bc0d158620b38971afa0fbd1c2f8ee2a6`, merging committed handoff `61355976` from `research/ipopt-cell-routes`.
- The original session environment may still report the source checkout as cwd. Set each command's workdir explicitly to this isolated repo/standalone path. Do not switch/edit the source checkout at `/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes`.
- Milestone1 is implemented: support-aligned refinement in `geometry.py` and typed graph in `regions.py`, plus `test_route_regions.py`.29 geometry/region/legacy route/local tests and52 native DFS/terminal tests pass without skips. Continue milestone2 (semantic route identity/enumeration), then the remaining milestones. The rest of this resume preserves the original implementation contract; the main plan status is authoritative.
- **Uncommitted/untracked work is intentional and must survive.** In particular `autonomy/SPATIAL_CORRIDOR_REFACTOR_PLAN.md` is an untracked full implementation plan. Current docs, AGENTS and investigation artifacts are also uncommitted. Read `git status`; do not clean/reset/replace them with the older committed docs.
- Before handoff integration, the work was backed up to `/private/tmp/real11-corridor-handoff-backup-zfb336e9` and stash object `2e247cf33d8a113e68154b1279fe14a95bf1d777`. These are **older backup snapshots**, not the latest plan. Do not apply them again or overwrite current files from them.
- Only committed handoff changes were merged. The source agent's uncommitted fresh Real11 retry was left alone. Do not import/run/alter it as a side task.

## 2. Read order and authority

1. [AGENTS](../AGENTS.md): standalone scope; only commit at feature implementation boundaries, bundling related tests/docs/fixtures.
2. [Spatial corridor refactor plan](../autonomy/SPATIAL_CORRIDOR_REFACTOR_PLAN.md), especially sections0,3–8,10–11. This is the detailed spatial implementation contract.
3. [DFS implementation/integration plan](../autonomy/DFS_INTERACTION_IMPLEMENTATION_PLAN.md): DFS/interaction work is implemented; extend it rather than build another DFS.
4. [Imported solver handoff](CORRIDOR_AGENT_HANDOFF.md) and [current state](CURRENT_STATE.md) for the runtime/policy/terminal boundary.
5. Read affected code/tests, not the entire historical documentation corpus. Earlier research is already summarized and linked in the plan.

No skill is needed for this standalone Python task. Do not spawn or message other agents unless the user subsequently asks. Preserve unrelated work. The task does not call for a mod Gradle suite or game launch.

## 3. Settled user decisions

- Cells/portals exist to distinguish actual spatial alternatives: different supports, different obstacle passages and different vertical layers.
- **Overflying a block and landing then running off it are different routes even when X/Z matches.** A support surface must therefore be an explicit contact state; a broad volume above it alone cannot distinguish those routes.
- A route is an untimed ordered support/contact/passage itinerary. Local runs, jump times and facings can vary within it. Same-neighborhood setup hops remain local unless another meaningful passage is crossed; broad revisits stay ordered.
- Automatically discover intermediate support order from unordered geometry plus existing movement-aware proposals. A selected discovered route's support stages then become real requirements for that route. Do not hardcode B2/B3/B4/B5 into generic code. Keep supplied-route fixtures as unchanged controls.
- Representative guide points do not define route identity or become numerical waypoint walls. Air anchors should be conditioned on neighboring selected support/passage regions. Full portals remain feasible sets.
- Current Real11 task is **virtual descending height7 finish**, X<729.7, Z>=−1095.3, no extra lower X/upper Z bound. Minimize achieved tick then maximize Z there. Free overshoot and actual finite B7 contact are distinct valid terminal alternatives. Do not restore the earlier literal-finite-B7-only or fixed64 interpretation.
- Current canonical virtual fixture has `maxInputs=200` as a guard. Do not silently raise domain/caps. B6's reported rightward extension lacks exact bounds; use stored geometry for regressions and do not claim full real-map coverage until corrected.
- No new physics/continuous backend, fractional movement decisions, global objective/time-bound project, discrete decision transfer, inertia-threshold work or intentional wall-hit/ladder/water expansion. Normal momentum and zero thresholds remain.

## 4. Imported behavior that must survive

Implemented modules: `cell_routes/depth_first.py`, `interactions.py`, owned rows/effective dedup in existing builders, probe uncertainty in `reachability.py`, exact request logging, `terminal_goals.py`, and `cell_routes/minimum_runs.py`.

Reuse `InteractionGate.context/query/required`. Preserve step/grind unknown Y and intended unclipped X/Z probe coverage; realized endpoints are not a complete internal probe trace. Preserve `constraintOwners`, full-proposal screening, `commit_arrival` and no orphan future rows. Re-solve from original state with earlier facings free.

DFS currently has schema5 `ordered_goal_dfs` state. Preserve frame ownership, generator cursors, ready/scored/untried siblings, accepted variants, unknowns, refinements, suspended work and incumbent. The current active heuristic is dynamically learned preparation runs immediately before the latest jump; never replace it with total-run pruning, hardcoded thresholds or an old global peer-retention pass. Additional continuation-time reasoning is deferred.

Keep all exact requests/model hashes and separate numerical/corridor/scene outcomes. The handoff's calls8/9 and initial0 calls5–8 include genuinely separate terminal models and corridor repairs. Do not suppress those IDs or assume equal controls imply duplicate models. Any saving must follow an actual generic corridor/model change with before/after evidence.

## 5. Implementation sequence; next action

**Milestone1 implemented; preserve its code/tests and continue milestone2. Original first-slice contract:** exposed support components, support-aligned local refinement and typed volume/surface region graph. Read current `geometry.py`, `routes.py`, `ContactPatch`, `expanded_box`, and geometry tests. Add canonical finite face unions and region graph records in `regions.py`; extract/reuse incidence helpers rather than rederive physics. Retain all residual air and boundary geometry. Add generic tests and actual cell/portal dumps, run focused geometry/region checks, then commit that implemented feature with related docs/tests/fixtures.

Then continue the full refactor through the plan's milestones:

1. Semantic support/air/terminal regions and typed open/contact edges.
2. Semantic route enumeration/normalization, preserving support, passage/layer and revisit identity rather than horizontal midpoint triples.
3. Shared 3D Y/X/Z phase/contact progress with stage/variant cursors; no extra ticks per portal or accidental path splicing.
4. Fixed stage/contact/axis-sweep guard alternatives through existing constraint builders/dedup/ownership, with matching preliminary/final scope.
5. Stage-derived goals/anchors/directions in existing `search_goals`; update every fixed `goal_list`/`index`/terminal call site listed in plan section10, preserve minimum-run and resume behavior.
6. Generic/native gates, automatic historical-witness representation without seeding, and matched supplied/automatic virtual-finish evidence.

Future schemas proposed in the plan: region graph1, semantic corridor2, route-stage DFS6. Those are not current runtime versions. Change context identity explicitly when features land, reject incompatible old state and do not invent a lossless horizontal-signature migration.

Important implementation trap: once all selected physical support stages are complete, a free virtual finish has **no outstanding physical B7 contact**. Update `screen`/remaining-requirement logic accordingly; preserve the finite-support fallback for ordinary landing tasks. Extend `search_goals`' existing frame lifecycle, not a replacement controller. Scope the adapter immutably per active frame so suspended work cannot inherit another frame's stage.

The plan contains a concrete API/call-site checklist, ownership rules, uncertain named-probe handling, test matrix and milestone done criteria. Implementation is not complete just because anchors improve or milestone1 passes.

## 6. Verification baseline and environment

Post-merge native focused run: **52 tests passed, no skips**, ~17s at `984b701b`. [Log](../autonomy/experiments/linkcraft11-opening/audit/handoff-integration-focused-2026-11-03.log), [command/settings](../autonomy/experiments/linkcraft11-opening/audit/handoff-integration-focused-2026-11-03.json). Geometry/route checks passed15 tests earlier; the imported Python code is unchanged after those source-specific investigations.

Use the existing worker/helper read-only unless affected code actually requires a rebuild:

```sh
cd /private/tmp/parkour-real11-initial-run-ticks
export OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1
export PYTHONPATH="$PWD"
export PKC_IPOPT_PYTHON=/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes/.venv-guidance/bin/python
export PKC_RUN_TICKS_COMPILER=/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes/standalone/autonomy/build/compiler.json
python3 -B -m unittest standalone.autonomy.tests.test_cell_geometry standalone.autonomy.tests.test_cell_routes -v
"$PKC_IPOPT_PYTHON" -B -m unittest \
  standalone.autonomy.tests.test_minimum_runs \
  standalone.autonomy.tests.test_depth_first \
  standalone.autonomy.tests.test_orchestrator_continuation \
  standalone.autonomy.tests.test_fixed_clock_goal \
  standalone.autonomy.tests.test_terminal_goals -v
```

Do not rebuild a helper/classpath being used by another numerical run. If shared checker instrumentation becomes necessary for exact named-probe route classification, follow the plan's explicit probe caveat and applicable root/module instructions; do not invent a Python approximation. The known unrelated ladder baseline is not a reason to fix ladder code here.

## 7. Existing investigation evidence; do not rerun blindly

In `autonomy/experiments/linkcraft11-opening/audit/`:

- `automatic-route-smoke-2026-11-03.json`: stored geometry33 cells/92 portals/247 patches; first geometric route after72 expansions.
- `automatic-route-defaults-2026-11-03.json`: default50 signatures do not admit the saved historical valid trajectory. This is a representation gap, not proof of50 usable routes.
- `corridor-comparison-2026-11-03.json` and PNG: selected cell rectangles/Y ranges, shared/unique regions, exact crossing differences and reference audits.
- `decomposition-investigation-2026-11-03.json`: finite top patches, Z reflection, empty-coverage midpoint bias and local33→47 refinement preserving free volume/1650 samples. The conditioned B2→B3 air anchor fits existing cells but is not a physics solution.

Do not overwrite those reports or reinterpret them as post-refactor measurements. New runs need new output paths, source/settings and validation scope. No new cold retry is required before milestone1; source-area retries belong to the other checkout's work.
