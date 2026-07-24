# Handoff: run telemetry, attribution viz, run matrix (2026-07-18)

Approved plan for the first implementation milestone coming out of `solver-graph-learning-survey.md`. Read that doc first (especially §3 objective representation, §5 dataset spec); this file is the execution plan with code anchors. The user wants autonomous execution with intervention only at the marked points.

## 0. State as of this session

Update 2 (same day): user verified Phases A+B in-game ("everything works fine"; open anomaly: CUSTOM with a saved preset runs long on nodes, suspect pre-M5 presets materializing missing params at long catalog defaults). Phase C EXECUTED: `RunMatrixScreen` + `MatrixAnalysisScreen` in core test sources, 220-run matrix (44 problems x 5 presets, 120 s cap, 1 h 41 min), artifacts at `core/build/reports/matrix-full1/`. Verdict in survey section 8: VBS-SBS gap is zero on feasibility and <=1.4e-4 on objective, learned selector (L2) is a NO-GO on this corpus; the spread is time (custom-exh30 = optimize60 quality at half the wall; window stage earns its place; deep polish only matters at the last sine bucket).

Update (later session, 2026-07-18): Phases A and B are IMPLEMENTED, both gates green (`:core:test` incl. new `SolveRunRecordTest`, `:core:build` incl. tableStyleCheck). Shipped: `SolveRunRecord` + `SolveRunLog` (graph pkg), timestamped samples with stage + active node in `SolveProgress.report`, record finalize on all four engine paths (SOLVED at runJob end, FAILED on miss/throw, CANCELLED in cancel(), STOPPED_BEST in stopAndUseBest(), one-record-per-solve CAS guard), sink wired in `Application.setupUi` to `<saveDir>/runs/<yyyy-MM-dd>.jsonl`, editor sampling deleted, sparklines replaced by node-colored incumbent strip (obj+viol lanes, hover tooltip) + per-node "+n" improvement badges. In-game verify still pending (editor viz + M5 crash fix together). Phase C not started.

- Branch `v1.7.0-angle-solver-node-editor`, uncommitted work present (user commits, never run git write ops).
- Crash fix applied this session: `ImGui.pushID(id)` / `popID()` around the node body in `GraphEditorWindow.drawNode`. Root cause: imgui-node-editor's `BeginNode` pushes NO ImGui ID scope, so same-label widgets in different nodes aliased (dead fields, MarkItemEdited assert, hard `System.exit` from imgui-java's assert callback which cannot be overridden). `:core:build` green. In-game verify pending. Any future widget drawn inside the canvas needs a unique ID scope.
- `docs/research/solver-graph-learning-survey.md` written: verified survey + learning ladder (portfolio -> RF preset selector -> AutoFolio-style conditional space; free topology learning empirically loses; objectives are scoring functions over run telemetry, never dataflow nodes).
- The M4 global obj/viol sparklines are the user-confirmed pain; they get replaced in Phase B.

## 1. Phase A: run records + provenance (core only)

Goal: every solve, in-game or headless, appends one self-describing JSONL record. This is simultaneously the training dataset, the go/no-go measurement input, and ComfyUI-style provenance.

1. `SolveRunRecord` in `core/.../anglesolver/graph/`:
   - config: preset name, canonical graph hash, resolved per-node param vector, effort tier, metric config (hierarchical default: feasibility at feasTol first, then objective sense, then time)
   - problem: hash over spec + scenario (constraints, ticks, start state, per-tick masks)
   - outcome: wall nanos, status enum SOLVED / STOPPED_BEST / CANCELLED / FAILED, final objective, final max violation, feasible flag
   - trajectory: incumbent improvement samples (elapsedNanos, obj, viol, feasible, stage string, active node id)
   - per-node: visits, elapsedNanos, branch taken, evals (copy of `GraphRunState` statuses at end)
   - counters: `ctx.cmaesEvals`, `ctx.smoothingEvals`; model/mod version string
   - JSON style: follow the hand-rolled approach of `SaveIO` / `GraphPresetIO`, no new dependency.
2. Collection hooks:
   - `SolveProgress.report(...)` (core/.../solver/SolveProgress.java:34) is the single incumbent-improvement point (version bump on accepted improvement, `stage` already recorded via `setStage`). Append a timestamped sample there.
   - Active node attribution: `GraphRunState.activeNodeId()` (graph/GraphRunState.java); wire it into the progress reporter when `GraphContext` is constructed (AngleSolverEngine.runJob ~line 779). Check thread-safety: report() is called from solver worker, runState is synchronized.
   - Delete the editor-side sampling: `GraphEditorWindow` fields `sampleTimes/sampleObjectives/sampleViolations` (~lines 136-140), the collection block (~226-253), and have `renderLiveStats` (~393) read the engine record instead.
3. Assembly + sink:
   - Assemble in `AngleSolverEngine.runJob` (success path ~line 826, failure ~794) plus the interrupted paths: `cancel()` (~640) and `stopAndUseBest()` (~646). `poll()` (~687) publishes on the main thread; the record can finalize on the worker.
   - Preset name threading: `AngleSolverState.graphPresetName` exists; verify how `buildJob()` captures graph identity and thread name + serialized-graph hash onto `Job`.
   - Sink: append JSONL to `<gameDir>/parkourcalculator/runs/<yyyy-MM-dd>.jsonl` through the same path plumbing `FileSystemSaveStore` uses for `graphs/`. Always on.
4. Gate: `:core:test` green, plus a new unit test for record assembly + JSONL round-trip. `ModernStepRegressionTest` and the problems gates must stay untouched.

## 2. Phase B: per-node attribution viz

5. Replace the two global sparklines (`GraphEditorWindow.sparkline` calls ~455-462) with:
   - per-node improvement badge: count of incumbent improvements credited to that node this run (data: trajectory samples' active node id)
   - one incumbent strip: time on x, objective and violation lanes, segments colored by active node (reuse node category colors from `categoryColor`)
   - keep it minimal per the constraint-vis-minimal ruling; signals must be observable and attributable.
6. Reminder: any new widget inside the canvas goes inside the per-node `pushID` scope.
7. Gate: `:core:build`; then in-game check. **INTERVENTION POINT: user verifies editor viz + the crash fix in one session.**

## 3. Phase C: run matrix + first numbers

8. Headless matrix driver in core test sources (reuse `ProblemsTest` fixtures machinery; see `anglesolver/TESTS.md` for the corpus map; capture tests MUST use Fixtures.buildBoxes):
   - every (preset x problem) over `problems/solve` + `problems/closedform`, cold starts only (seedless rule: never warm-start from the answer)
   - capped per-run budget, proposal 120s cutoff; PAR-style scoring treats caps as censored
   - preset set v1: builtin Fast, budget-capped Optimize, plus 2-3 knob variants as portfolio candidates
   - writes the same JSONL into a run-tagged artifacts directory; resumable (skip recorded pairs)
   - run supervision rule applies: supervise directly, run-tagged artifacts, watchdog, never delegate to agents. Smoke slice of ~5 problems first to measure real cost. **INTERVENTION POINT: confirm total matrix budget after the smoke slice.**
9. Analysis over the matrix: per-preset table, single best preset (SBS), virtual best (VBS), VBS-SBS gap. Append as a measurements section to `solver-graph-learning-survey.md`. The gap is the go/no-go for the learned selector (survey §4); the same matrix feeds Hydra-style portfolio growth.

## 4. Non-goals this milestone

No learned selector. No feature engineering beyond free fields (numTicks, jump count, constraint counts). No seed threading through stochastic stages (repetitions cover variance later). No metric scalarization beyond the hierarchical default. No objective-node UI rework beyond the strip. No new dependencies in core.

## 5. Traps and rulings to respect

- Core stays MC-free; file paths go through ports/save-store plumbing, not hardcoded dirs.
- NodeCatalog type ids / param keys / Guarantee names are a stable serialization contract; the record must reference them, never rename them.
- No git commit/push; no `runClient` while MC is open; no code comments; no em dashes.
- tableStyleCheck runs on `:core:build` (known false positive on SolverWidgets only).

## 6. Next-session prompt

"Read docs/research/telemetry-handoff-2026-07-18.md and docs/research/solver-graph-learning-survey.md, then implement Phase A (SolveRunRecord + collection hooks + JSONL sink) gated on :core:test, and continue into Phase B unless blocked."
