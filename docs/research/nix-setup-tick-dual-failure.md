# Nix "setup tick" solve failure: why the closed-form dual dies on a redirecting prefix

Status 2026-08-21: the shipped fix now lives as the SetupPeelNode graph node (the engine-stage setupPeel
moved into the solver graph), and the nix-t25-setup-tick regression is still green. The four probe
classes and the CMA-ES race described in the failure narrative were deleted in the 2026-08 cleanup.
Appendix line numbers and git SHAs are historical; the 2026-08 history collapse invalidated the old SHAs.

Handoff for a fresh session. Everything below was established on branch `nix-backward-march`
(HEAD `b62f0c6`) against one save file. Production code is unchanged; four untracked probe tests
under `core/src/test/.../anglesolver/` carry the experiments. Read "Tick indexing" before anything else.

Related prior docs: `docs/research/nix-full-freestart.md`, `docs/research/nix-backward-march-handoff.md`
(the backward-march file was consolidated into `nix-full-freestart.md` section 10 and deleted, 2026-08-21).
This file is a smaller, sharper instance of the same "the setup is the hard part" story, isolated to a
single grounded setup tick and traced to the exact failing step in `ClosedFormSolve`.


## TL;DR

The user reports: solving the route from tick "t25" fails (best-objective near-miss, 8/13 constraints),
but solving from "t26" with t25 preset to almost any global yaw (a hand-tested ~240 degree range) solves,
some in under 50 ms. The only structural difference is one prepended **grounded, non-jump, turning "setup"
tick**.

Root cause, trace-proven: the multi-jump feasibility path is the convex Lagrangian dual
(`ClosedFormSolve` / `CostateDualSolver`, driven by `LongRunSolver`). It **recovers each tick's yaw as the
direction of that tick's costate** (`recoverYawDeg = atan2(gz, gx) - baseArg`). That only yields a feasible
primal when every tick's required facing is either the objective pull or a wall normal. From the seam (t26)
every tick qualifies and the dual certifies viol=0 in 12 iterations. The t25 setup tick must instead point
at about -63 degrees, a **redirect** whose only job is to arrive at the seam the tail needs; that facing is
neither the objective pull (about -110) nor any wall normal, so the costate direction cannot equal it. The
dual mis-aims the setup tick, the tail then misses its walls by ~0.26, `LongRunSolver` returns MISS, and
CMA-ES starts **cold** (no warm start) and cannot crack the full-dimensional thin manifold. Engine returns
the best infeasible incumbent.

Two intuitive fixes were tested and **falsified**: feeding the dual the exact zeroing pattern of a known
feasible solution still fails to certify; pinning the discovered seam position (and the first 12 feasible
positions) as constraints still fails to certify. So the dual cannot be repaired for a redirecting prefix
via patterns or constraints. The only un-falsified direction is to sidestep the dual for the prefix
(warm-start CMA-ES homotopy, or a direct discrete setup search).


## The problem instance

File: `C:/Users/benja/Desktop/Games/MultiMC/instances/1.8.9/.minecraft/parkourcalculator/5.1875bm_nix_weird_solve.json`

- `modVersion` 1.6.1+build.208.**6d02be0** (the user's in-game jar), `mcVersion` 1.8.9.
- `angleSolver`: `startTick=24`, `landingTick=61`, axis X, goal MAX, effort CUSTOM, `stopOnFeasible=false`,
  customBudget {restarts 256, maxEval 100000, polishCount 64, EXHAUSTIVE, window 14, commit 13,
  useWindowSolver true, ilsExhaustive true}. `defaultInputs=FORCE_45`, `defaultSprint=ALWAYS`,
  `defaultSlipperiness=AIR`.
- Objective: maximize X at the landing tick. Landing box (abs61): X in [8.696, 10.3], Z in [9.3, 10.3].
  Feasible objX sits right on the 8.696 floor (about 8.697), so the landing is a razor.

Not fixed on HEAD: the user's jar is `6d02be0`. Commits since are `cf34994` (free-start seed recovery,
only runs when `startTick == 0`), `c550906` (adds `EngineFileScreen` test + CONTEXT note), `b62f0c6`
(tests). None touch this code path; the failure reproduces identically on HEAD.


## Tick indexing (read this first)

- User "t25" = internal `startTick 24` = displayed/result **T25** = a **grounded, non-jump, turning setup
  tick** (authored slipperiness DEFAULT = 0.60 < 1.0 means grounded). Seed state: on ground, pos
  (11.30, 1.5125), velocity (0, -0.0784, -0.269), yaw -135.
- User "t26" = `startTick 25` = the **jump tick** (abs25 has the JUMP key; it is the takeoff). Its seed is
  the **seam**: pos (11.4235, 1.2028), velocity (0.0674, -0.0784, -0.1691), yaw -63.175.
- Constraint at config tick `n` binds `pos[n]` = position at the **start** of tick `n` (before tick n's
  input). Display tick = config tick + 1. So the config-24 constraint shows as outcome "T25".
- `JumpConstraint.t1` inside the solver is a **segment** index; abs tick = startTick + t1.
- Harness caveat: `Fixtures.buildBoxes` seeds `boxes` from the file's saved `debug` array (not a live
  re-sim), except at index `startTick` where it substitutes `angleSolver.seed`. So `getState(25)` in the
  harness equals `debug[25]` (a fixed seam). In the live app, `boxes` come from re-simulating the rows, so
  presetting t25's yaw does move the t26 seed there. The mechanism is identical either way.
- In-window grounded ticks (slip 0.60): abs 24, 25, 37, 49, 50. JUMP rows: abs 25, 37, 50 (3 jumps).
  Only the landing region is razor-tight; the mid-route corridors (X in [9.7,11.3]) are ~1.6 blocks wide.


## Timeline of findings (what was tried, what was found, and what was wrong)

Ordered as run. Several early conclusions were wrong and are marked. Do not re-derive the wrong ones.

### F1. A feasible t25 solution provably exists; the solver never finds it. (`NixStartTickProbe`)
- `getState(24)` = (11.3, 1.5125) v(0, -0.269); `getState(25)` = (11.4235, 1.2028) v(0.0674, -0.1691),
  yaw -63.175.
- Sweeping tick0 to reproduce the seam: best yaw -63.18, residual 1.9e-5. So the byte-exact model
  reproduces the sim for the grounded tick; there is no physics/model discrepancy.
- `BoundPrunedRecovery` (pattern B&B) on the 36-tick t26 tail: feasible, objX 8.697. On the full 37-tick
  t25 problem: **NULL at 60 s**.
- Cross test (the decisive one): take the t26 feasible tail, prepend tick0 = -63.18, forward through the
  **t25** spec: **maxViolation 0, feasible, objX 8.697238**. So a feasible t25 solution exists and the
  solver, including exhaustive B&B, does not find it.
- WRONG SIDE NOTE from this probe: "feasible tick0 window ~0.12 degrees" was measured with the tail FROZEN
  to one specific solution. That is a lower bound, not the real basin. Ignore the 0.12 number.

### F2. Basin scan via B&B is an unreliable oracle. (`NixSetupBasinProbe`)
- Fixing tick0 and re-solving the tail with a 4 s B&B gave "12 percent feasible" (7/60), scattered.
  This CONTRADICTS the user's hand-tested ~240 degree working range.
- Resolution: B&B is a bad feasibility oracle on these tails (it false-negatives seams the real engine
  solves). The user's in-tool hand tests via the actual engine are ground truth. Do not use B&B
  feasible/NULL to measure a basin.

### F3. Full engine reproduces the asymmetry on HEAD. (`EngineFileScreen`)
- t25 (startTick 24, CUSTOM as saved): success=false, met 8/13, obj 8.696136, solver
  "receding horizon -> CMA-ES".
- t25 under FAST (`PKC_SOLVE_EFFORT=FAST`): still 8/13, same solver chain (the B&B rescue does not rescue).
- t25 with the full ~52 s budget: reaches 11/13, never feasible.
- t26 (startTick 25, `runB.json`): live tracker feasible at **144 ms**, 11/11, obj 8.697267.

### F4. The solver DOES consider all tick0 angles. (temporary instrument, reverted)
- Instrumented `ExactJumpModel.stepRange` (`from==0`) to histogram `yawAbsDeg[0]` across a full CUSTOM
  solve. Result: **14,375,243** tick0 facings evaluated, **all 72 of the 5-degree bins hit**. Mass is
  ~13 percent at [45,50), falling off, and only ~0.03 percent (3872) at the feasible -63.18 (= 296.8 in
  [0,360)). So "the solver never considers those angles" is FALSE. It considers all of them but is
  anchored in the wrong basin and never assembles a feasible full trajectory.
- The instrument was reverted; production `ExactJumpModel` is byte-exact regression-pinned (do not leave
  edits in it).

### F5. Root cause located: the convex dual, via `SolverTrace`. (nixt25 vs nixt26 traces)
Enable with `PKC_SOLVER_TRACE=<tag>`; writes `core/build/reports/solver-trace-<tag>.txt`.
- **t26 trace**: `CF ... viol=0.000e+00 certified` in 12 iters at ~70 ms -> `ENGINE receding horizon
  solved` -> `ENGINE race start warm=true` -> `race end solved`. The dual carries the whole thing.
- **t25 trace**: `CF` grinds ~20 momentum patterns for 4.5 s, best viol never below **0.11**, ->
  `ENGINE receding horizon miss` (518 ms) -> the fallback dual chain repeats the stall ->
  `ENGINE race start warm=false` (4528 ms), i.e. **CMA-ES starts cold**.
- Conclusion: `ClosedFormSolve` (the convex dual) is the feasibility linchpin. CMA-ES is a warm-start
  polish, not a feasibility finder here. The one prepended grounded setup tick degenerates the dual, and
  with the dual failing there is no warm start, so cold CMA-ES cannot crack the 37-D thin manifold.
- With `window=14 >= 3 jumps`, `LongRunSolver` is a single monolithic dual window, not a receding one, so
  "receding horizon" in the solver name is a misnomer for this instance.

### F6. Where the dual's recovered primal fails. (`NixDualProbe`)
- t25 `optimizeRobustGraded`: recovered viol 0.262, tick0 yaw -110.38, misses are all on the tail:
  Z GE 9.3 at abs60 (0.262), X LE 8.7 at abs54 (0.235), corridor X LE 11.3 at abs36 (0.193),
  landing Z GE 9.3 at abs61 (0.077). `optimize` (ascending ladder, what the fast path uses): **NULL**.
- t26 `optimizeRobustGraded`: viol 0.025 (one wall). `optimize` (ascending): **viol 0, feasible,
  tick0 13.84**.

### F7. The exact recovery step. (`ClosedFormSolve` + `JumpLinearModel`, code read)
- `ClosedFormSolve.recover` (line 249): `yaws[t] = lin.recoverYawDeg(t, gx, gz)`; fallback at line 254
  (`gx*gx+gz*gz < 1e-18`) defaults a vanishing costate to "face the objective axis".
- `JumpLinearModel.recoverYawDeg` (line 319): `wrap(atan2(gz, gx) - baseArg[t])`. The recovered yaw is the
  **direction of the costate only** (magnitude is fixed by the physics).
- Costate `(gx, gz)` = objective pull (`objectiveVectors`, line 186; friction coupling `coefAxis`, line 168,
  which is **cut at the first velocity-zeroing tick** after `t`) + sum over active walls of lambda times the
  wall gradient.
- The setup tick's OUTPUT (the seam, seg1 = abs25) carries **no constraint** (abs25 is empty). The abs24
  X/Z box lands on seg0, the pinned start, which `compileWall` drops as a tick-0 constant (line 200); and
  the feasible seam (11.42, 1.20) lies **outside** that box anyway (X over by 0.12, Z under by 0.31), so
  even moved to the output it would forbid the solution.

### F8. Two dual-repair fixes falsified. (`NixPatternProbe`)
- Reconstructed feasible t25: tick0 = -63.28, viol 0; its zeroing pattern is `x@0,11-13,34`.
- Feed that **exact** pattern to the dual on t25 via `optimizeWithPattern`: **does NOT certify**. So it is
  not a pattern-identification failure; branching over momentum patterns would not help.
- Sanity: t26 fed its own feasible pattern `x@10-12,33` certifies.
- Pin the discovered seam position at seg1 as a wall at +/- 0.05, 0.01, 0.002: **none certify**.
- Pin the first K feasible positions (K = 1, 2, 3, 5, 8, 12) at +/- 0.01: **none certify**.
- Caveat: tight multi-tick pins could be infeasible in the *linear* model (linearization error vs the
  byte-exact positions). The strong signal is the loose single-seam pin at +/- 0.05 failing, and the exact
  feasible pattern failing.


## Root cause (final, tested)

The dual recovers each tick's facing as the direction of its costate, which is a combination of the
objective pull and active wall normals. A tick is recovered correctly only if its feasible facing equals
that costate direction.

- t26 launches from the seam. Every free tick either hugs a wall (the tail razor) or points at max-X.
  All facings are objective- or wall-aligned, so the dual certifies in 12 iterations.
- t25 must first execute the setup tick, whose feasible facing (about -63 degrees) is a **redirect**: its
  purpose is to reach the specific seam velocity the tail needs, not to maximize X or hug a wall. That
  facing is neither the objective pull (about -110) nor any wall normal. The costate direction cannot be
  made equal to it, and (tested) neither the correct zeroing pattern nor added position constraints bend it
  there. So the setup tick is mis-aimed, the seam velocity is wrong, and the tail misses its walls by
  ~0.26. `LongRunSolver` returns MISS, CMA-ES goes cold, the engine returns the best infeasible incumbent.

The requirement the dual cannot express is downstream-determined: "aim here because the tail, many ticks
later, needs this seam." The forward costate recovery has no term for it.


## What is NOT the cause (dead ends, do not retry)

- NOT jumps or "multi-jump nonconvexity". Jump count is identical in t25 and t26; the only nonconvexity is
  the per-tick unit-circle on sin/cos. See `~/.claude` memory `reference_jumps_not_nonconvex`.
- NOT "the solver does not consider the angles". It evaluates all 360 degrees, 14 million times (F4).
- NOT a small basin. ~240 degrees of setup presets work via the real engine; the 12 percent B&B number was
  a false-negative artifact (F2).
- NOT pattern identification. Feeding the exact feasible pattern still fails (F8).
- NOT a missing or addable constraint. Pinning the seam and the first 12 positions still fails; t26 works
  with no constraint on its start tick (F8). "Add a constraint" is falsified.
- NOT already fixed on HEAD.
- Secondary aggravator only: under CUSTOM `stopOnFeasible=false`, the first-feasible pattern-B&B rescue
  (`AngleSolverEngine` ~line 844) never fires, and the exhaustive B&B/ILS block (~line 875) only runs when
  the incumbent is already feasible. So an infeasible incumbent gets no rescue and you get the near-miss.
  This makes "CUSTOM maxed" worse than FAST for feasibility. But even FAST fails, and B&B run directly on
  the full 37-tick span returns NULL, so this gating is not the primary cause.


## The un-falsified fix direction

Sidestep the dual for the redirecting prefix; keep the dual for the tail (its regime, which is exactly the
t26 problem it certifies in 12 iterations).

1. **Warm homotopy.** Solve the tail from the seam with the dual (feasible). Then extend the start backward
   one tick at a time, each time warm-starting **CMA-ES** (a general search not limited to costate
   directions) from the previous feasible solution, seeding the newly added tick so it reproduces the old
   start state (a feasible point of the longer problem). Each step is a small perturbation of a feasible
   point, never a cold high-D search, so depth does not blow up. The warm start is the solver's own prior
   sub-solution, not the user's recorded rows, so it does not break the cold-determinism principle.
2. **Direct discrete setup search.** The existing `NixSetupSearch.java` line: search the prefix facings
   (possibly with key toggles, since the human route holds yaw and toggles W+A/S+D to keep vx exactly 0)
   to reach the seam, then hand the tail to the dual.

Durability requirement from the user: any fix must survive moving the start up 1, 10, or 20 ticks (a deeper
prefix). Validate at t25, ~t15, ~t5 before claiming durable. The "seam" is not a chosen cut point; it is
the boundary where the dual's costate recovery stops working (clean launch vs redirect), so it does not
need to be decided by a heuristic.


## Fix shipped: the setup peel (2026-07-07)

Direction 2 above, built as a new engine stage. When the multi-jump path exhausts both the receding
horizon and the full dual chain (`yaws == null`), `AngleSolverEngine.setupPeel` runs:

1. Scan the leading run of grounded, non-jump segments (`lead`); skip if none.
2. Sweep a constant heading for that prefix on a 15 degree grid starting at the seed yaw (24 candidates).
3. For each candidate: forward the prefix byte-exact, reject candidates violating any prefix-tick
   constraint, then hand the tail (reseeded at the resulting seam state via `LongRunSolver.suffixSpec`)
   to `LongRunSolver.solve`, exactly the machinery that certifies the t26 problem.
4. First candidate whose assembled full trajectory re-verifies byte-exact feasible wins and flows into
   the normal pipeline (smoothing, warm CMA-ES race, and, now that the incumbent is feasible, the
   previously-dead exhaustive seam sweep / B&B / ILS block under CUSTOM).

Budgets: 12 s stage cap, 600 ms per candidate via a watchdog thread flipping a per-candidate cancel
token (bad-seam tails measured 7-70 ms to miss, so the sweep is typically well under 1 s). If no
candidate assembles feasible, the lowest-violation assembly is still returned as a warm start for the
race; if no tail ever solves, the stage returns null and behavior is exactly as before the fix.

Measured on this instance:
- t25 FAST: success 13/13 in ~4.5 s, solver "receding horizon -> setup peel (first feasible)". The
  sweep misses -135/-120/-105/-90/-75 in ~15-60 ms each, then -60 certifies with assembled viol 0.
- t25 CUSTOM (as saved): live tracker feasible 13/13 at ~4.3 s, obj 8.698247 after the improve stages
  (the proven reference was 8.697238).
- t26 (`runB.json`): unchanged, feasible at ~146 ms; the peel never fires when anything else solves.
- Durability probes t15 (`startTick 14`) and t5 (`startTick 4`, seeds from `debug[]`): both already
  solve via the plain receding horizon (1.9 s and 9.7 s), so the broken class really is "start ON a
  grounded redirect tick", which the peel covers for any prefix length; airborne lead-ins did not
  need it on this route.
- t24 (`startTick 23`, one airborne tick before the redirect): solves 15/15 in 1.2 s via
  "receding horizon -> closed form -> relaxation recovery", no peel. Even a single slack tick in
  front of the redirect restores the existing recovery chain; t25 was the uniquely pathological
  configuration (redirect pinned exactly on the start).
- Full `:core:test` gate green (100 checks); the peel fires on no other capture.

Regression pinned: `captures/nix-t25-setup-tick.json` + `problems/solve/nix-t25-setup-tick.expect.json`
(FAST, shouldSolve, 30 s budget).

Code: `AngleSolverEngine` (stage wiring after the dual-chain fallback, `setupPeel`, `PeelWatchdog`,
`PEEL_*` constants) and `LongRunSolver.suffixSpec` (public tail-spec slice reseeded at an interior
state, incoming sprint/amp lag threaded from the peeled tick).

Not done here: the peel only sweeps a constant prefix heading, which is complete for a 1-tick prefix
and covers "hold one heading through the setup" for longer grounded prefixes; a multi-tick grounded
redirect needing per-tick heading changes (the nix t1-28 key-toggle setup) remains the separate
free-start monster (`docs/research/nix-full-freestart.md`). The single-jump path does not run the
peel; if a grounded-redirect-prefix single-jump case shows up, the same stage applies there.

## Open questions for the next session

1. Is the pin-test failure (F8) a true "the dual cannot express the redirect", or an artifact of the pins
   being infeasible in the *linear* model? Re-test with EQUALITY pins, with velocity pins (a DX/DZ style
   constraint via `t2`), or by directly forcing the dual's tick0 direction and checking whether the tail
   then certifies. This distinguishes "recovery cannot aim the tick" from "linearization error too large".
2. Can the seam be made an explicit interior boundary node the dual solves for, i.e. multiple shooting?
   `FreeStartSolve` already solves for a free *start* position; could a free interior seam state
   (position + velocity) be added as a shooting node so the prefix becomes a two-point BVP the dual can
   handle?
3. Why does the momentum-pattern fixed-point (`ClosedFormSolve` lines 116-128) oscillate for t25 and never
   converge, given that even the known-feasible pattern does not certify? Is there any certifying pattern
   at all, or is the linear recovery fundamentally unable to hit this trajectory?
4. Would `SlpSolve` (SLP) do better than the closed-form dual on the redirecting prefix, or does it share
   the same linearization limitation?
5. Coupling horizon: how much prefix must be committed before the dual can take over (the homotopy depth)?
   The K-position pin experiment (F8) was inconclusive because of the linearization caveat; a clean measure
   would come from the homotopy prototype itself.


## Reproduction

Repo root: `C:\Users\benja\Desktop\Coding\10 Minecraft\Mods\ParkourCalculatorMod`, branch
`nix-backward-march`. Commands shown for the Bash tool (git-bash); the shell is also PowerShell-capable.
The real gate is `:core:test` (pure Java, no Minecraft, seconds). Every command uses
`-x tableStyleCheck --console=plain --rerun-tasks`.

Common env:
```
export PKC_SOLVE_FILE="C:/Users/benja/Desktop/Games/MultiMC/instances/1.8.9/.minecraft/parkourcalculator/5.1875bm_nix_weird_solve.json"
export PKC_OUT="<scratch>/out.txt"   # probes write here; else stdout is captured only in the JUnit XML
```
JUnit stdout (for the tests that print to System.out rather than PKC_OUT, e.g. EngineFileScreen) lands in
`core/build/test-results/test/TEST-<class>.xml` inside a `<system-out>` CDATA block. Read that file.

### The t26 variant file
`runB.json` is a copy of the save with `angleSolver.startTick` set to 25 and `angleSolver.seed` set to the
seam (debug[25]):
```
pos = [11.423508748183691, 58.0, 1.2027763179564805]
vel = [0.06743577795703981, -0.0784000015258789, -0.1691091523549076]
yaw = -63.174957
```
A copy currently exists at
`<scratch>/runB.json`; regenerate by copying the save and editing those two fields.

### Probes (untracked, in `core/src/test/java/de/legoshi/parkourcalc/anglesolver/`)
- `NixStartTickProbe` (F1): seam reachability, B&B feasibility at startTick 24 vs 25, the decisive cross
  test, and the (misleading, frozen-tail) tick0 window. Env: `PKC_BNB_NANOS` optional (default 60e9).
- `NixSetupBasinProbe` (F2): basin scan (fix tick0, re-solve tail via B&B). Env: `PKC_STEP_DEG`,
  `PKC_TAIL_SEC`, `PKC_YAWS` (comma list of specific tick0 yaws). Remember: B&B is a bad oracle here.
- `NixDualProbe` (F6): runs `ClosedFormSolve.optimizeRobustGraded` and `optimize` on both specs and prints
  the recovered primal's per-constraint residual and recovered tick0.
- `NixPatternProbe` (F8): reconstructs the feasible t25, extracts its zeroing pattern, feeds it to the dual,
  and pins the seam / first K positions. This is where the two dual-repair fixes were falsified.
- `EngineFileScreen` (existing, F3): full `engine.solve()` on the file. Env: `PKC_SOLVE_EFFORT`
  (FAST/OPTIMIZE/CUSTOM), `PKC_SOLVE_TIMEOUT_MS`, `PKC_OPTIMIZE_SECONDS`. Reads startTick from the file, so
  point it at the save (t25) or `runB.json` (t26). Prints to stdout (read the XML).
- `NixFullDiag` (existing): spec summary, recorded/debug/saved-result evaluation. Its debug section assumes
  startTick 0, so only its spec and recorded sections are meaningful for this file.

### Exact commands
```
# F3 full engine, t25 CUSTOM (as saved). Read core/build/.../TEST-...EngineFileScreen.xml afterward.
export PKC_SOLVE_TIMEOUT_MS=120000
./gradlew :core:test --tests "de.legoshi.parkourcalc.anglesolver.EngineFileScreen" -x tableStyleCheck --console=plain --rerun-tasks

# F3 t26 (feasible in ~144 ms): point PKC_SOLVE_FILE at runB.json instead.

# F5 trace: set the tag, then read core/build/reports/solver-trace-nixt25.txt
export PKC_SOLVER_TRACE=nixt25
./gradlew :core:test --tests "de.legoshi.parkourcalc.anglesolver.EngineFileScreen" -x tableStyleCheck --console=plain --rerun-tasks
# key greps: "ENGINE", "receding horizon", "certified", "fallback bestViol", "race start warm="

# F6 dual residual
export PKC_OUT="<scratch>/dual_out.txt"
./gradlew :core:test --tests "de.legoshi.parkourcalc.anglesolver.NixDualProbe" -x tableStyleCheck --console=plain --rerun-tasks

# F8 pattern + pin falsification
export PKC_OUT="<scratch>/pattern_out.txt"
./gradlew :core:test --tests "de.legoshi.parkourcalc.anglesolver.NixPatternProbe" -x tableStyleCheck --console=plain --rerun-tasks
```

### Yaw-histogram instrument (F4), reverted; to redo temporarily
In `ExactJumpModel`, add a `static volatile boolean LOG_YAW0`, a `static AtomicLongArray YAW0_HIST`, and a
`static AtomicLong YAW0_N`, and at the top of `stepRange` when `from==0 && n>0` bin `yawAbsDeg[0]` into
[0,360). Toggle it from `EngineFileScreen` around `engine.solve()` and dump after. REVERT afterward; the
model is byte-exact regression-pinned (`ModernStepRegressionTest`), and `git diff core/src/main` must be
empty before shipping.


## Appendix: code locations

- Orchestration and stage dispatch: `core/.../anglesolver/AngleSolverEngine.java`. Multi-jump path around
  lines 741-782 (`LongRunSolver` then dual chain then CMA-ES race); B&B rescue gating at 844; exhaustive
  ILS/B&B block gated on already-feasible at 875; seed/first-tick wiring in `buildPhys` (411); free-start
  is `startTick==0` only (332).
- Convex dual: `core/.../anglesolver/solver/ClosedFormSolve.java`. Pass/pattern loop 83-135; `runLadder`
  145-215; `recover` 249; `optimizeWithPattern` 63.
- Linear model and recovery: `core/.../anglesolver/solver/JumpLinearModel.java`. `objectiveVectors` 186;
  `coefAxis` (coupling cut at zeroing) 168; `compileWall` (drops tick-0) 200; `zeroingPattern` 286;
  `recoverYawDeg` 319.
- Dual solver internals (costates `gx,gz`, multipliers `lambda`): `CostateDualSolver.java`.
- Receding horizon: `LongRunSolver.java` (WINDOW and commit are in jumps; window 14 >= 3 jumps means one
  monolithic window here).
- Seam machinery that already exists: `SeamSweepRecovery.java` (pins seam states in bands, SLP rescue, up
  to 5 seams) but only runs post-feasibility as an objective improver.
- Byte-exact step model: `ExactJumpModel.java` (`forward` 55, `stepRange` 82). Regression:
  `core/src/test/.../ModernStepRegressionTest.java`.
