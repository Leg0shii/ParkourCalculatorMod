# Cold strat-finder: session summary + UI handoff (2026-08-10)

Self-contained handoff. The cold per-cycle beam brute-forcer now solves the acceptance set cold; the next step is to build a **standalone strat-finding UI** around it (not part of the Angle Solver window), wired to the input tick table. Everything is in worktree `.claude/worktrees/coldsearch`, branch `worktree/coldsearch`, uncommitted (the user commits). Read order: this file, then `j154-family-bruteforcer-handoff-2026-08-10.md` (solver internals + Path A/B), then `modular-cycle-bruteforcer-vision.md` (the product vision the UI realizes), then memory `project_momentum_exact_search.md`.

## 0. TL;DR

- **The cold solver works.** The per-cycle beam brute-forcer (`ColdCycleBeamScreen`) solves **j154** (37.5s) and **j716** (~95s) cold, byte-exact certified, and **j925/j1150** were already solved. All 7 `ColdSearchRegressionTest` pins green.
- **It lives in a TEST screen driven by env vars.** For a UI it must be **promoted to a production API** (a `ColdBeamSolver` in `core/src/main`) with a `Config` object, a `solve()` that streams progress and is cancellable, and threading.
- **NEXT: build the strat-finder UI** — its own tool/window (NOT integrated into the Angle Solver UI), connected to the input tick table (reads the current problem, applies the found line back into the input rows).
- **Honest caveat carried forward:** "certifies viol=0 against the capture's constraints" is what the solver guarantees; that equals "lands in-game" only when the capture's block constraints are precise. j716's capture had rounded/missing constraints and its sibling fell in-game by 8.7e-8 until the user fixed the constraints (see section 4). j925/j1150 constraints are fully precise; j154 mostly. The user's stance: if the constraints sit correctly, the solve is correct; do not add a block-hitbox gate for now.

## 1. What the cold solver is (the backend the UI drives)

Pipeline, per candidate line:
1. **Per-cycle family cross-product.** A jump's momentum splits into press-cycles (ground->air hops; detected from the slip pattern as `pressSegTicks`). Each cycle is enumerated from a grammar: `coast x (L-1-j) -> glide x j -> press`, plus a **brake-tick** variant `coast -> glide -> brake -> press`, a **no-glide brake** `coast -> brake -> press`, and an all-same **engage** pattern. Alphabets (coasts/glides/presses/engages/brakes) and per-cycle glide ranges are configurable. Cross-product the cycles.
2. **Momentum feasibility + tail reachability filter** (`Sweep.traceLineTo` width >= -slack at some facing, and `Sweep.lineTailReachable`, a sound forward-interval reachability). Prunes most candidates.
3. **Sprint-engage dimension.** The sprint hold is NOT fixed to `canRun(combo)`; it is a search dimension: `hold(t) = (t >= engageTick && canRun(combo))`. The engage tick is carried as a 3rd beam-partial array `{mk, hd, {engage}}`; seeds the beam with one partial per engage point. `sweep` = cycle boundaries + never; default `0` = always-sprint (old behavior). This is what let j716 keep sprint OFF at its t18 WD press (delayed sprint / winged-neo).
4. **Parallel byte-exact certify.** Streaming certify over the survivors across N threads (each thread its own `Sweep[]`), first byte-exact solve wins (`ColdSearch.benchSig` -> `certify`). Byte-exact judge is `ExactJumpModel`.

Result of a solve: a signature string (e.g. `5.5.5.5.4.4.5.7...0.3.3+...1+`) that `ColdSearch.certifyLine(file, sig, cfg)` re-certifies to a `ColdResult` (yaws, startX, startZ, the `KeyLine`).

## 2. The env-var interface (this becomes the UI's Config)

Driven today via `PKC_COLD_BEAM_*` on `ColdCycleBeamScreen`:

| env | meaning | default |
| --- | --- | --- |
| `PKC_COLD_BEAM_FILE` | capture JSON path | (required) |
| `PKC_COLD_BEAM_COASTS/GLIDES/PRESSES/ENGAGES` | per-role combo alphabets (comma names, e.g. `SD,S,WA`) | built-in full sets |
| `PKC_COLD_BEAM_BRAKES` | brake-tick combos (e.g. `NONE`); empty = no brake variants | empty |
| `PKC_COLD_BEAM_GLIDE` | global glide max | 2 |
| `PKC_COLD_BEAM_GLIDE_RANGES` | per-cycle glide bands, e.g. `1-2,8-12,8-12` | `1-glideMax` all |
| `PKC_COLD_BEAM_SPRINT_ENGAGE` | `0` (always) / `sweep` / a tick index | 0 |
| `PKC_COLD_BEAM_CAP` | beam cap (trim survivors by width if exceeded) | 4000 |
| `PKC_COLD_BEAM_CERTCAP` | max certifies | 4000 |
| `PKC_COLD_BEAM_PROBE_GATE` | probe gate; **>=100 = certifyAll (skip the broken probe)** | 0.15 |
| `PKC_COLD_BEAM_PROBE_STEP` / `FSTEP` | probe / feasibility facing step (deg) | 0.5 / 1.0 |
| `PKC_COLD_BEAM_BUCKET_BUDGET` | `ColdSearch.BUCKET_SLICE_BUDGET` for this run | 30 |
| `PKC_COLD_BEAM_THREADS` | certify threads | availableProcessors |
| `PKC_COLD_BEAM_BUDGET_MS` | wall-clock budget | 600000 |
| `PKC_COLD_BEAM_LOG` | append log path | stdout |

The reference config that solved j716: `COASTS=D,A,WD GLIDES=SA,WD PRESSES=A,WD,W ENGAGES=W,WD BRAKES=NONE GLIDE_RANGES=1-2,8-12,8-12 SPRINT_ENGAGE=19 PROBE_GATE=999 BUCKET_BUDGET=30 THREADS=10`. j154: `GLIDES=SD,S,WA COASTS=A,SD PRESSES=SD,WA,W ENGAGES=W,WA GLIDE_RANGES=1-2,1-3,8-12 SPRINT_ENGAGE=0 BUCKET_BUDGET=3`.

## 3. Findings this session

- **Path A (parallel certify):** ~16x. `PKC_COLD_BEAM_THREADS` + `PKC_COLD_BEAM_BUCKET_BUDGET` + `gate>=100 => certifyAll` (the probe is rank-inverted for j154, so gate=999 certifies all in beam order). Solved j154 in 37.5s.
- **Path B (certify speedup, data-backed):** profiling showed the expensive-miss certify is dominated by the SLP rescue (61%) + wasteful entry descent, NOT model rebuild or the entry grid. `SLP_RESCUE_TRIGGER 5e-2->5e-3` (SLP is decisive only at closed-form viol <= 9e-4 across all 7 pins) and `ENTRY_DESCENT_TRIGGER 2e-2->3e-3` (entry descent decisive=0 across pins). Both are tunable statics now. Result: **expensive miss 1664ms->401ms (~4.1x)**, all pins byte-exact green. BUT end-to-end j154 beam only ~6% (37.5->35.1s) because the beam is dominated by cheap misses + the fixed per-candidate probe scan. The "form-intersection replaces the grid" idea is BLOCKED (no closed-form signal discriminates the byte-exact facing bucket). Deeper Path B (the per-candidate `probeSig` 720-sweep scan; the `ClosedFormSolve` graded inner solve) is open and higher-risk (shared solver code).
- **Generalization (j716):** the simple grammar missed j716; added the **brake-tick grammar** (its line has a NONE tick before presses) and the **sprint-engage dimension** (its line delays sprint to the last cycle). Both are **additive supersets** (default off/engage=0 = old behavior; confirmed j154 still solves with them enabled, same sig). BUT the general **sweep is intractable**: engage-sweep x brake blew j716 to 461,852 survivors and the **single-threaded beam-build** timed out. So j716 solved with a single `engage=19` (last-cycle) structural hint. Verdict: pipeline general, mechanisms additive, but stacking DOFs multiplies the search and the single-threaded build cannot sweep them all -> per-jump tuning is required = the vision's **tunable brute-forcer, not a push-button oracle**.
- **Export + in-game validation:** `ColdExportSiblingScreen` writes a loadable mod save from a certified sig (`line.toRows()` keys + `toGameFacings(yaws)` as **locked-absolute CONTINUOUS game facings** [not mod-360, else the sine bucket differs] + the found feasible start; mirrors `AngleSolverEngine.writeYawRows`; `LazyEntitySimulator` honors `yawLocked -> setYawAbsolute`). The user loaded the j716 sibling in-game: it fell 8.7e-8 past the block-support edge because j716's capture constraints were rounded/incomplete (see 4); after the user fixed the constraints, the found strat solves and is correct. So the winged-neo strat is real and the export path is correct.

## 4. The constraint-precision lesson (carry forward, do NOT re-litigate)

`ColdProblem.boundsOf` reads constraint bounds verbatim (no rounding; only EQ gets +-1e-4). So the coldsearch is faithful. The imprecision was in the **capture data**: j716's X constraints were mostly `-699.95` (rounded, 1.2e-8 looser than the real block-support edge `-699.9499999880792` = wall +X edge -700.25 + player half-width `(float)0.3`), and the final jump tick t31 had NO X constraint at all -> the (collision-free) coldsearch produced a line inside the rounded bound but off the real block, and drifted at t31. j925/j1150 constraints are fully precise; j154's binding constraints are precise. **User ruling: if the constraints sit correctly, the solve is correct; move on. Do not add a block-hitbox verification gate.** Just be careful to say "certifies against the capture," not "solved," until a capture's constraints are known precise.

## 5. Files (all in `core/`)

- `src/main/.../coldsearch/ColdSearch.java` (production): certify engine. Changed this session: `SLP_RESCUE_TRIGGER=5e-3`, `ENTRY_DESCENT_TRIGGER=3e-3` (now tunable statics), plus logic-neutral `prof*` instrumentation counters.
- `src/main/.../coldsearch/{ColdProblem,KeyLine,LineSpec,ColdResult,ArcSweep,ColdBound,StratPrefixes}.java`: unchanged production primitives. `KeyLine.toRows()` + `LineSpec.build` are the input-mapping path the UI's apply will reuse.
- `src/test/.../coldsearch/ColdCycleBeamScreen.java` (TEST, the beam to PROMOTE): parallel certify, brake grammar, sprint-engage dimension, env-config. THIS is the logic that becomes the production `ColdBeamSolver`.
- `src/test/.../coldsearch/ColdExportSiblingScreen.java` (TEST): the apply/export path (sig -> loadable save) to reuse for "apply to input table".
- `src/test/.../coldsearch/{ColdHumanDiagScreen,ColdCertifyProfileScreen,ColdSlpDecisiveScreen,ColdBenchScreen}.java`: diagnostics (keep).
- `src/test/.../coldsearch/ColdSearchRegressionTest.java`: the 7 byte-exact pins.
- The reference config runs are in `C:\Users\benja\AppData\Local\Temp\claude\coldlogs\` (j154/j716 cmd scripts with the self-retry loop for the concurrent-`--stop` daemon issue).

## 6. The UI to build (next session)

Goal: a **standalone strat-finding tool** for the cold solver, **NOT integrated into the Angle Solver window**, **connected to the input tick table**. Realizes `modular-cycle-bruteforcer-vision.md`.

Two parts:

### 6a. Promote the beam to a production solver
Move `ColdCycleBeamScreen.beam()` into `core/src/main/.../coldsearch/ColdBeamSolver.java` (production):
- A `ColdBeamSolver.Config` (POJO) replacing the env vars (alphabets, per-cycle glide ranges, per-cycle fixed/swept flag, sprint-engage mode, caps, threads, budget, bucketBudget).
- `ColdResult solve(SaveFile file, Config cfg, ProgressSink progress, AtomicBoolean cancel)` that runs the beam-build + parallel certify, streams progress (cycle survivor counts, certify progress, SOLVED), and returns the solved `ColdResult` (or null). Keep it Minecraft-free (core rules).
- Preserve byte-exactness: the certify path is `ColdSearch.benchSig`/`certifyLine`, unchanged. Re-run the 7 pins after the move.

### 6b. The UI widget + controller (follow the existing StratFinder pattern)
Reference the existing strat finder for the window/controller/apply pattern: `core/.../ui/anglesolver/StratFinderWidget.java`, controller `core/.../StratFinderController.java`, window `core/.../ui/anglesolver/AngleSolverWindow.java` (but this new tool is SEPARATE from the angle solver window). ImGui, Catppuccin Mocha theme via `ThemeManager` (see `docs/CODING_GUIDE.md` and the render-colors settings rule).

- **Its own overlay/window** (register in `OverlayManager`), a distinct tool from the Angle Solver.
- **Connected to the input tick table:** on open, build the `ColdProblem`/`SaveFile` from the CURRENT state (`InputData` + `AngleSolverState` + `BoxController`) via `SaveIO.buildSaveFile`; the press-cycles come from `ColdProblem.pressSegTicks`.
- **Per-cycle config panel:** show each detected press-cycle as a row/region; per cycle choose fixed-vs-swept, the alphabet (coasts/glides/presses/brakes), the glide band, and the sprint-engage. (The vision wants a 3D per-cycle picker eventually; start with a table/panel.)
- **Run/cancel** driving `ColdBeamSolver.solve` on a worker thread; live progress (survivor counts, certify progress) in the widget.
- **Apply result to the input tick table:** on SOLVE, write the found `ColdResult` into `InputData` rows via `KeyLine.toRows()` (keys) + set locked-absolute game-facing yaws from `toGameFacings(yaws)` (mirror `AngleSolverEngine.writeYawRows`, or reuse `ColdExportSiblingScreen`'s logic) + set the start to the found feasible start. This must retrigger `Application.runSimulation()` (do not break the sim wiring).

Design principles: keep `core` Minecraft-free (define ports if world data is needed); do not touch the Angle Solver UI; the tool reads/writes the input tick table only. QA in-game on the touched loader before merge (core-only UI change: one loader suffices).

## 7. Open levers (beyond the UI)

- **Parallelize the beam-build** (the single-threaded survivor filter is the scaling bottleneck; it is what makes the sprint-engage sweep intractable). This is the top lever for making the sweep push-button.
- **Deeper Path B:** the per-candidate `probeSig` 720-sweep scan and the `ClosedFormSolve` graded inner solve are the remaining certify costs; higher-risk (shared solver code).
- (Declined by the user for now) a block-hitbox verification gate.

## 8. Operating rules (unchanged)

Cold inputs only (human rows/yaws/debug/result never feed the solver; user-provided per-cycle alphabet/family/engage is a sanctioned prior). Byte-exact via `ExactJumpModel` is the only judge. Never git commit/push/branch (ask, ready message, no attribution). Long runs: detached cmd scripts in `C:\Users\benja\AppData\Local\Temp\claude\coldlogs\` with the FULL gradlew path, `--no-daemon` + a self-retry loop on "stop command received" (a concurrent session runs `gradlew --stop`), done-markers + Monitor tails, PKC_* env in the same command as gradle plus `--rerun`, `-PtestHeap=3g`. Pins: `.\gradlew --no-daemon --configure-on-demand :core:test --tests "*ColdSearchRegressionTest" "-PslowTests" "-PtestHeap=3g" --rerun`.

## 9. Next-session prompt

See the prompt provided alongside this handoff (also reproduced here for continuity):

> Build the standalone cold strat-finder UI. Work ONLY in the worktree `.claude/worktrees/coldsearch` (branch `worktree/coldsearch`); the main checkout stays untouched. READ FIRST, fully, in order: `docs/research/coldsearch-ui-handoff-2026-08-10.md` (this file: what exists, the solver's env/Config interface, the findings, the UI plan), then `docs/research/modular-cycle-bruteforcer-vision.md` (the product vision the UI realizes), then memory `project_momentum_exact_search.md`, then `docs/research/j154-family-bruteforcer-handoff-2026-08-10.md` (solver internals). GOAL: a standalone strat-finding tool UI for the cold per-cycle beam brute-forcer, FULLY SEPARATE from the Angle Solver window, CONNECTED to the input tick table. STEP 1 (backend): promote `ColdCycleBeamScreen.beam()` (a test screen) into a production `core/src/main/.../coldsearch/ColdBeamSolver.java` with a `Config` POJO (per-cycle alphabets/glide-ranges/fixed-or-swept, sprint-engage mode, caps/threads/budget/bucketBudget replacing the `PKC_COLD_BEAM_*` env vars), a cancellable, progress-streaming `solve(SaveFile, Config, progress, cancel) -> ColdResult`; keep `core` Minecraft-free; the certify path (`ColdSearch.benchSig`/`certifyLine`) is unchanged and byte-exact; re-run the 7 `ColdSearchRegressionTest` pins after the move. STEP 2 (UI): a new ImGui overlay/tool (its own window, registered in `OverlayManager`, Catppuccin Mocha via `ThemeManager`), NOT part of the Angle Solver UI; on open, build the problem from the current `InputData` + `AngleSolverState` + `BoxController` (via `SaveIO.buildSaveFile` -> `ColdProblem`); show each detected press-cycle (`pressSegTicks`) as a configurable unit (fixed/swept, alphabet, glide band, sprint-engage); Run/Cancel drives `ColdBeamSolver.solve` on a worker with live progress; on SOLVE, apply the `ColdResult` back into the input tick table via `KeyLine.toRows()` keys + locked-absolute `toGameFacings(yaws)` yaws + the found start (reuse `ColdExportSiblingScreen`'s logic; mirror `AngleSolverEngine.writeYawRows`), and retrigger `Application.runSimulation()`. Follow the existing StratFinder pattern (`core/.../ui/anglesolver/StratFinderWidget.java`, `core/.../StratFinderController.java`) for structure but as a separate tool. HARD RULES: core stays Minecraft-free (ports if needed); do not touch the Angle Solver UI; do not break the sim retrigger; byte-exact via `ExactJumpModel` is the only judge; validate the 7 pins stay green; QA in-game on one touched loader before merge; never git commit/push/branch (ask, ready message, no attribution); long runs via detached `--no-daemon` cmd scripts with a self-retry loop in `coldlogs/`, `-PtestHeap=3g`, `--rerun`.
