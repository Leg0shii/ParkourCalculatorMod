# Free-start (position-free search): handoff and post-mortem

Status (updated 2026-07-05): **the convex floor is now solid.** The `solveJoint` bug diagnosed in Section 9 is fixed, the engine calls it, and the `:core:test` gate is green (328 tests, anchored path byte-identical). The remaining ambition (a rebuilt nonlinear CMA search, escalation, parallelism) is unchanged from the plan below and is now genuinely optional. See Section 0 (top) for the resolution.

Original status (pre-fix): **works with caveats, but has bugs; the free-start SEARCH is not trustworthy yet.** Phases 0-2 of `docs/research/free-start-position-plan.md` are implemented and the `:core:test` gate is green, but the in-app behaviour on real jumps is wrong (an *easy* jump returns 6/7 = fails to land). This document is the ground truth for the next session. Read `free-start-position-plan.md` first for the original design, then this.

---

## 0. Resolution (2026-07-05): the convex floor is fixed

The Section 9 diagnosis was right that `solveJoint` was broken, but the localization ("the p0-aware dual emits the WRONG yaws; audit the `FreeP0` support term's sign/scale") was only half correct. The `p0coef`/`hAxis`/gradient SIGNS are actually self-consistent (re-derived from scratch: the wall delta-coefficient is `+tc` for LE / `-tc` for GE, and `H_a = objDev + sum lambda*p0coef` with `p0coef = GE?+tc:-tc` matches it). The two real defects were:

1. **The box-support term is kinked at `H_a=0`, which stalls the dual.** `supportOf(H) = max(H*lo, H*hi)` has a subgradient that jumps `lo -> hi` at `H=0`, so `deltaOf()` (the delta contribution to the dual gradient) is discontinuous there. The projected-dual convergence residual then plateaus far from 0 (observed `pgres` stuck at 0.2-3.2 instead of ~1e-8), and the recovered yaws are a non-optimal shape. **Fix:** Moreau-smooth the support: `s_mu(H) = max_delta (H*delta - (mu/2)delta^2)`, giving `delta*(H) = clamp(H/mu, lo, hi)` (continuous) with curvature `1/mu` in the interior band. Implemented in `CostateDualSolver` as `supportOf`/`deltaOf`/`supportCurv` + a `buildHessian` term, `P0_SMOOTH = 0.05`, all gated on `freeP0 != null` (so the anchored gate is byte-identical by construction). This alone made the well-conditioned cases converge to `pgres ~ 1e-11`, `vRec -> 0`.

2. **`solveJoint` referenced the linear model at the box CORNER (the clamped far seed), not the feasible region.** For a seed far outside the footprint (f2f: seed `(-5.1456, 2.2462)`, box `X[-3.925,-3.075] Z[1.075,1.700]`), `box.px` clamps to a box corner. Referencing there forces a LARGE interior `delta`, which the degenerate dual (its `lambda` wanders in a null space at a benign `pgres~0.02` plateau even in the anchored case) mis-recovers into bad yaws. **This was THE dominant defect** (smoothing alone did not fix it; `mu` was irrelevant to it). **Fix:** reference the linear model at the box CENTER (`refX = 0.5*(pxLo+pxHi)`), build a re-centered `cbox`, clamp the recovered start back to the original footprint. Proven by a reference-choice diagnostic: `free@corner` -> viol 0.39 (FAIL), `free@center` / `free@feasible` -> viol 0.0 (feasible). For real f2f the feasible start sits ~0.12 from the footprint centre, so the centre reference lands right next to it.

**Why the gate never caught it:** the `:core:test` dual path is all `freeP0 == null` (byte-identical), and `jointSolveImprovesObjectiveOverSeed` used an EMPTY wall list (degenerate single-active-constraint support). The bug only bites a real multi-wall free box whose feasible shape differs from the pure-reach shape and whose reference is far. That exact case is now pinned by `FreeStartSolveTest.jointSolveSolvesConvexFreeBoxFromFarCornerReference` (fails on the corner reference, passes on the centre; a config-2-style synthetic since the user's f2f save files are not in the tree).

**Engine wiring:** `AngleSolverEngine.freeStartImprove` now tries `solveJoint` (A) then `solve` (B) FIRST; if a byte-exact-feasible free start beats the anchored seed (keep-better), it adopts (mutating the shared `sc.startPos` in place, the codebase's static-wiring pattern) and returns. Only when the convex path finds nothing (a genuinely nonconvex free-start jump, e.g. `j318`, which has no closed-form-feasible start anywhere in its box) does it fall through to the pre-existing CMA two-stage search. So nonconvex free-start is unchanged (`EngineFreeStartTest` still green) and convex free-start (f2f) is now solved by the trustworthy dual. Note: the THOROUGH post-passes (ILS/seam-sweep/B&B) that run after adoption read `spec.asScenario()`, which IS the mutated `sc` (one object), so they operate at the adopted start and only ever return start-feasible candidates; the objective-only adoption there cannot lose feasibility (pinned by `ThoroughAdoptFeasibilityTest`).

**Files:** `CostateDualSolver.java` (smoothing), `FreeStartSolve.java` `solveJoint` (centre reference), `AngleSolverEngine.java` `freeStartImprove` (wiring), `FreeStartSolveTest.java` + `ThoroughAdoptFeasibilityTest.java` (regression). Not committed. Gate green (329 tests). An adversarial review flagged one candidate (THOROUGH adopting a seed-feasible-but-adopted-infeasible candidate); it was refuted both by object identity (`sc == spec.asScenario()`, mutated in place) and empirically (the sub-solvers return adopted-start-feasible results on the exact trigger). **Unverified in-game** (the apply/start-move sim retrigger, per Section 7, still needs a real client run).

---

The single most important fact, established late and the reason to re-open everything:

> **The user's failing jump is EASY (you can land it with ~0.05 of slack). So a 6/7 result is a BUG, not a byte-precision limit.** Most of this session was spent under the wrong assumption that the jump was frame-perfect. It is not. Re-reason from "the feasible set is large; the search should trivially hit it."

---

## 1. What free-start is supposed to do

The Angle Solver normally fixes the start position and searches only per-tick yaw. Free-start lets the solver also choose the **start position** within a bounded footprint, so the player can place a start roughly and have the solver find the best exact spot.

**Trigger (user-decided, authoritative):** free the start iff the solve window begins at the route's first tick (`startTick == 0`, UI "tick 1") AND that tick carries an X and/or Z **range** constraint (`Constraint.Op.IN`). The range IS the footprint box. One-sided/scalar first-tick constraints do NOT free an axis. No block capture; entirely constraint-driven. First-tick range constraints are pulled out of the wall list (they were the "trivial tick-0 constant") and become the box.

**Intended semantics (user-corrected mid-session):** free-start must *improve* the objective by finding a better start AND rescue a start that fails, but must **never** turn a working solve into a worse one (keep-better). It is NOT rescue-only.

---

## 2. What actually shipped (Phases 0-2, all gate-green)

- `solver/StartBox.java`: the footprint/velocity box. `px/pz/vx/vz` reference (the seed) + per-axis `lo/hi`. `pinned()`, `startFree()`, `isPinned()`. A pinned (zero-width) box reduces byte-for-byte to the fixed-start solve.
- `JumpPhysicsInputs.startBox`; `AngleSolverEngine.buildPhys` emits a pinned box by default; `buildJob` builds a FREE box from first-tick ranges and excludes them from the walls.
- `JumpLinearModel.constPos` sources the pinned start from the box (byte-identical fallback when null).
- Phase 2 lifted `p0` into `CostateDualSolver` as bounded deviation vars (`FreeP0`, `Wall.p0coef`, `supportOf/deltaOf/hAxis`), gated on `freeP0 != null` so all existing callers are byte-identical. `FreeStartSolve.solveJoint` uses it. This is the CLEAN, correct part and it works on convex jumps (`FreeStartSolveTest.jointSolveImprovesObjectiveOverSeed` gains the full footprint width).

The anchored regression gate is genuinely protected: `PKC_SCREENS=1 ... '*HpkDualRecoveryScreen'` column-diffs byte-identical (only the diagnostic wall-count `m` shifts for the 5 captures that already had tick-0 ranges). But **that gate never exercises the free-start SEARCH** (the fixtures have no first-tick ranges, and `runDualRecovery` calls `dualChain` directly, bypassing the engine dispatch). So "gate green" says nothing about whether free-start works.

---

## 3. The debugging saga (what was tried, why, and why each failed)

This is the important part: do not repeat these.

1. **Placement bug (real, fixed).** Free-start adopt ran *after* the `if (yaws == null) return null;` and after the CMA-ES race. So on an infeasible seed the whole budget burned at the wrong fixed start and free-start never ran. Fixed by moving it earlier, then restructured (below).

2. **Linear free-start can't solve nonlinear jumps.** `FreeStartSolve.solve` (realization B) and `solveJoint` (realization A) use the closed-form/dual (`ClosedFormSolve`, `CostateDualSolver`). The user's jumps are wall-collision nix jumps whose saved solver is `"closed form -> CMA-ES"` — closed form FAILS even at the right start. So the linear free-start finds nothing and gives up. This is why free-start "did nothing" on real jumps.

3. **Approach A: put the start in the CMA-ES (n+2 dims).** `CmaesJumpHarness` searched `[yaws, startX, startZ]`, forwarding at the candidate start via a per-restart scenario copy. Result: **4-5/7.** Suspected cause (NOT confirmed): the start `sigma` was `0.25 * boxWidth ≈ 0.4` on a ~1.6-wide footprint, so most samples land on infeasible starts far from the feasible region and the search wastes itself. **This is a prime suspect to revisit — a small, seed-centred start sigma was never tried.**

4. **Approach B: translated-feasibility fitness (n dims).** `CmaesJumpHarness` (current code) keeps `n` yaw dims but scores feasibility *after optimal translation*: forward at the reference, `FreeStartSolve.bestTranslate` picks the per-axis start deviation, `JumpConstraintCompiler.translatedPenalty/translatedSlack/translatedEvaluate` evaluate the shifted path; the engine recovers the start afterward. Result: **6/7.** Gets close but never nails the last constraint.

5. **`recoverStart` — PROVEN CORRECT.** `EngineFileScreen` diagnostic on `nix_slight_shift` fed the *saved working yaws* (feasible at 9.7) through recovery at the shifted seed:
   ```
   savedYaws@seed(9.7050000, 6.5000000) viol=5.000e-03
     -> recovered(9.7000001, 6.5046649) viol=0.000e+00
   ```
   Given the correct yaws, the recovery finds the byte-exact feasible start and lands 7/7. **The recovery/translation math is not the bug.**

6. **Two-stage (locate then solve).** Stage 1: translated search -> `recoverStart` -> approximate start. Stage 2: strong fixed-start CMA-ES there. Result: still **6/7**, and the tell-tale:
   ```
   located start (9.7000, 6.5012)   vs   true (9.7000001, 6.5046649)   <- Z off by 0.0035
   ```
   Stage 1's yaws are imperfect (6/7), so its recovered Z is wrong, so stage 2 solves at the wrong Z and cannot reach 7/7. **Chicken-and-egg:** correct start needs correct yaws; correct yaws need correct start. The binding Z constraint lives at ticks 11-12 and its position moves with the (imperfect) yaws, so each pass lands slightly off.

7. **Iterate with the NORMAL fitness (fixed start each pass).** Failed immediately: the normal CMA-ES tunes the shape *for the pinned start it is handed*, so `recoverStart` pulls the start straight back — it never migrates toward the true feasible start. The translation-awareness is what moves it; the normal fitness cannot.

8. **Margin ladder + feasibility-centering in recovery, SLP polish at the recovered start.** None closed the last constraint, because the yaws (not the start) were the problem, and `polish` bails on a non-strictly-feasible start (`if (sv[1] > 0.0) return cur;`) so it cannot restore feasibility.

9. **Engine dispatch restructured twice.** First to "rescue only when the seed fails" (WRONG — the user wants improvement, not just rescue). Then to keep-better (`freeStartImprove`): run the pinned anchored solve for the seed, then a free-start pass, adopt only if feasible AND better (or the seed failed). This is the correct *shape*, but `freeStartImprove` currently contains the failed two-stage search, and it runs the free pass *sequentially after* the anchored pass so under Custom it is budget-starved (`FREE_START_BUDGET_NANOS = 20s`, capped by the effort deadline).

### Debug tooling that exists (use it)
- `PKC_SOLVE_FILE=<save.json> ./gradlew :core:test --tests '*EngineFileScreen' --rerun-tasks` drives the live engine on any save. It prints `preSolve` (model/startTick/box/cons), a `DIAG` line (recovery of the saved yaws), `success/met/obj/solver`, and `finalStart`. Optional `PKC_SOLVE_EFFORT=FAST|THOROUGH`, `PKC_SOLVE_TIMEOUT_MS`. **This is the fastest repro loop; extend it, do not re-derive it.** (Note: it currently has temporary debug prints and a `DIAG` block added this session; keep them.)
- `SolverTrace` via `PKC_SOLVER_TRACE=<tag>` writes `build/reports/solver-trace-<tag>.txt` with `ENGINE`/`FREE` lines. CAVEAT: in the test worker the file did not appear where expected (worker cwd differs); the trace was effectively unavailable this session. Fix the path or print to stdout instead.
- Gate: `./gradlew :core:test`. Anchored-regression check: the `HpkDualRecoveryScreen` column diff (drop cols 4=`m`, 9/11/13=ms).

---

## 4. Observed bugs still open (from the user, on real jumps)

1. **An EASY jump (screenshot, ~0.05 slack) returns 6/7 = fails to land.** This is THE bug. Not precision.
2. **"Move the path away and it can't solve anymore"** on that easy jump. Moving the start within the footprint (or within the jump's slack) should still solve.
3. **"Optimize" (THOROUGH) does not find it / optimizes poorly.** Possibly budget spent badly (the sequential anchored-then-free split), possibly the same search bug.
4. Not yet tested (user interrupted): **does a strong FIXED solve at the moved start get 7/7?** This single test decides whether the bug is in the free-start code (if the fixed solve works, free-start is degrading it) or in the anchored solve/budget. DO THIS FIRST.

---

## 5. Reasoning: does the suggested plan actually make sense?

(The user asked for this section explicitly. I am reasoning against my own earlier plan, honestly.)

My earlier plan was "rebuild as a joint start+yaw search + escalation, because the frame-perfect jump needs more search." **The new fact breaks the premise of that plan.** Reason it through:

**(a) For an easy jump the feasible set is LARGE, so the search should be trivial.** At a fixed start an easy jump has abundant feasible yaws (the fixed solve lands it). Freeing the start only ADDS feasible `(start, yaws)` points (every start within the jump's slack, each with its own feasible yaws). A larger feasible set is *easier* to hit, not harder. Therefore a 6/7 result on an easy jump cannot be "the search needs more budget" — it means **the search code is not actually exploring the (now larger) feasible set.** That is a bug, and **escalation/more restarts will not fix a bug** — it just repeats a broken search.

**Conclusion 1: escalation is a good feature (the user is right that FAST should escalate), but it is NOT the fix for the 6/7 bug. Do not lead with it. Diagnose the bug first.**

**(b) Where is the bug most likely to be?** Ranked by suspicion:
   - **Approach B's translated fitness is the current code path and it caps at 6/7 even here.** `bestTranslate` picks ONE deviation per axis by intersecting per-axis intervals and then *centering* (`pickBest` returns the interval midpoint). On an easy jump with a wide feasible band this should be trivially feasible, so if it still yields 6/7 either (i) `translatedPenalty`/`translatedEvaluate` has a sign/`transCoef` error, or (ii) the CMA-ES is being handed a deceptive, non-smooth fitness (the centred-δ jumps discontinuously as the active set changes) and stalls. Both are checkable by unit-testing `bestTranslate`/`translatedPenalty` against a hand-built easy case with known feasible `(start, yaws)`.
   - **`freeStartImprove` scenario mutation.** The function swaps `sc.startPos` and `sc.startBox` many times (pin seed -> free box -> pin recovered -> restore seed). `sc` is the SHARED `spec.asScenario()`. A single missed restore leaves the scenario in a wrong state and every downstream `violationOf`/objective is computed at the wrong start -> spurious 6/7. This is exactly the kind of stateful bug that passes the gate (which never frees a start) and fails in-app. **Audit every mutation path, or stop mutating shared state and thread an explicit scenario instead.**
   - **Approach A's start sigma (0.4 on a 1.6 box) was never corrected.** If the plan goes to a joint search, this must be small and seed-centred.

**(c) Does the joint search (approach A) still make sense?** Yes, as the ARCHITECTURE, precisely because it removes the two failure sources above: it forwards at the *actual* candidate start (no `bestTranslate` math, no `translatedPenalty`), and it has no recover/locate chicken-and-egg (the start is a plain decision variable). It is the least-bug-prone formulation. BUT it must be validated on the EASY jump first (should be instant), and its start sigma must be small. If approach A *also* returns 6/7 on an easy jump, the bug is not in the fitness at all but in the engine plumbing (scenario mutation / keep-better), and no search change will help.

**Conclusion 2: the plan direction (single joint search) is sound, but the FIRST deliverable is a diagnosis, not a rewrite.** Prove where the 6/7 comes from on an easy jump with a minimal repro before writing more solver code. The strong temptation (mine included) is to add another clever search; resist it until the easy-jump bug is explained.

**(d) Is any of this worth it vs. just shipping the convex path?** The clean Phase-2 dual (`solveJoint`) already improves the start on convex jumps and never regresses. If the nonlinear (CMA-ES) case cannot be made trustworthy quickly, a defensible ship is: free-start ONLY via `solveJoint` (convex), and on nonlinear jumps free-start stays a no-op (keep the anchored seed). That is honest and never regresses. The nonlinear joint search is the ambition; the convex dual is the safe floor.

---

## 6. Suggested plan (revised, in order)

0. **Revert the experimental free-start SEARCH code to the clean keep-better baseline.** Keep Phases 0-2 (StartBox, buildJob split, dual `FreeP0`, `solveJoint`, keep-better shape). Remove: the `CmaesJumpHarness` translated fitness, `bestTranslate`/`translatedPenalty`/`recoverStart` ladder, two-stage `freeStartImprove`. Rebuild on the clean base rather than on the mess.

1. **Diagnose the easy-jump 6/7 (no new solver).** Get the screenshot jump's save file. Run `EngineFileScreen`. First answer 4.4: does a FIXED strong solve at the moved start reach 7/7? Then unit-test `solveJoint` and a minimal joint fitness on that easy jump with a known feasible `(start, yaws)`. Find the actual defect (fitness math vs scenario mutation).

2. **Rebuild free-start as ONE joint search** (approach A): CMA-ES over `[yaws, dStartX, dStartZ]`, start bounded to the footprint, **small seed-centred start sigma**, warm-started from the anchored seed yaws, normal penalty forwarding at the actual candidate start. keep-better vs the anchored seed. Validate on the easy jump (must be instant/feasible) BEFORE the tight one.

3. **Escalation (the user's idea, high value, do after the search is correct):** start cheap for latency; if no feasible solution, keep doubling restarts/budget until feasible or a time cap. Applies to the normal solve too, not just free-start.

4. **Parallel** the anchored and free-start searches on separate workers (fixes Custom budget-starvation, speeds up FAST). Optimization, last.

---

## 7. Files touched this session (state to reconcile)

- `core/.../solver/StartBox.java` (new, keep), `FreeStartSolve.java` (new; `solveJoint` keep, `bestTranslate`/`recoverStart`/`violationAt`/`pinTranslate`/`solve` are the experimental search - candidates to drop/rebuild).
- `core/.../solver/CostateDualSolver.java` (FreeP0 dual lift - KEEP, it is clean and byte-identical-gated).
- `core/.../solver/JumpLinearModel.java` (`Wall.p0coef` - keep), `BoundPrunedRecovery.java` (Wall ctor arg - keep), `JumpConstraintCompiler.java` (`translated*` - drop if dropping approach B).
- `core/.../solver/CmaesJumpHarness.java` (translated fitness - EXPERIMENTAL, drop/rebuild; the anchored path is byte-identical when `!free`).
- `core/.../anglesolver/AngleSolverEngine.java`: `buildJob` split (keep), `pinFreeStart`/`adoptFreeStart`/`freeStartRescue`/`freeStartImprove` history (current = `freeStartImprove` two-stage, EXPERIMENTAL), `pinnedScenario` helper, `Plan.start` + `apply()` start-move via `onStartMoved` (keep - the apply/sim-retrigger path is still UNVERIFIED in-game), `FREE_START_*` constants.
- `core/.../Application.java`: `setOnStartMoved(runner::setStartPosition)` (keep).
- Tests: `FreeStartSolveTest`, `EngineFreeStartTest` (keep), `EngineFileScreen` (temporary DIAG/pre-solve prints added - keep for the next session's repro loop).

Nothing is committed; it is all in the working tree, gated, gate-green. Memory: `project_free_start_position`. Related: `project_jump_solver_status` (1.21.10 vs 1.8.9 exact model), `feedback_movement_model` (collision-free model; walls are constraints), `project_wolfram_j021_result` (CMA-ES restart counts).

## 9. f2f jump investigation (the concrete easy-jump bug, with ground-truth files)

The user supplied four saves of one EASY fence-to-fence jump (lands with ~0.05 slack): `f2f_works.json` (the solution) and `f2f_fails_shifted{,_optimized,_custom_MAX}.json` (start shifted, no effort solves it). Repro via `PKC_SOLVE_FILE=<file> ... '*EngineFileScreen'`. This is the case the next session should fix first; it is far more diagnostic than nix.

**Ground truth (measured):**
- `f2f_works`: startTick 0, 12 constraints, X-MAX. **Solves 10/10 via `"closed form (first feasible)"` in ~1s.** So the jump is **CONVEX** (closed form, no CMA-ES needed) and the working start (-3.6211, 1.4755) is feasible with `viol = 0.000e+00`. `EngineFileScreen` `DIAG` confirms: recovery of the saved yaws lands feasible.
- The three fail files share the SAME footprint box `X[-3.925,-3.075] Z[1.075,1.700]` (identical to works) and the SAME shifted seed **(-5.1456, 2.2462)**. All three fail at **7/10** under FAST/THOROUGH/CUSTOM alike.
- **The footprint CONTAINS the working start** (-3.6211 in [-3.925,-3.075], 1.4755 in [1.075,1.700]). So free-start only has to pick that point out of the box and it is done. It does not.
- **The shifted seed is OUTSIDE the footprint** (X -5.1456 < -3.925; Z 2.2462 > 1.700), and the shift is LARGE (dX ~ -1.52, dZ ~ +0.77) — NOT the ~0.005 of nix. So this also exercises "seed far from / outside the box," which nix did not.

**Bugs found (concrete, ranked):**

1. **`solveJoint` (the clean CONVEX dual free-start) fails on a convex jump.** `EngineFileScreen` DIAG: `solveJoint=null (why=no-margin-certifies bestViol=3.100e-01)`. The p0-aware dual runs (not trivial, not unbounded), recovers yaws + a start, but they are **0.31 off feasible** across the whole margin ladder. For a jump closed form solves at the true start, the dual+`FreeP0` lift must be producing the WRONG yaws (or `pinTranslate` recovers the wrong start for them). **This is the highest-value bug: the convex path is supposed to be the solid floor, and it is broken on real multi-constraint free-start problems.**
   - **Why it was never caught:** the `:core:test` gate only exercises `freeP0 == null` (point boxes, byte-identical). `FreeStartSolveTest.jointSolveImprovesObjectiveOverSeed` uses an EMPTY constraint list (pure max-reach), so the `FreeP0` support term is exercised only in a degenerate, single-active-constraint case. **A genuine free box with several real walls (like f2f) is untested.** Add exactly that as the first regression test.
   - **LOCALIZED (test run, conclusive):** in `solveJoint` I ran both recoveries on the dual's yaws: `pinTranslateViol=3.100e-01`, **`recoverStartViol=2.779e-01`**. Both high, including the proven-exact `recoverStart` -> **the recovery is NOT at fault; the p0-aware dual is emitting the WRONG yaws.** The defect is in `CostateDualSolver`'s `FreeP0` support term (`hAxis`/`supportOf`/`deltaOf` and their gradient contribution in `grad`). Audit there first: the sign/scale of `p0coef` (`Wall.p0coef = GE? +tc : -tc`), `objDev` (should match `objectiveVectors` dx/dz), and the `deltaOf` argmax tie-break at `H≈0`. Suspicion: the support term's gradient pushes the costates `g` (hence yaws) toward a shape that is optimal for the RELAXED (translation-augmented) problem but that no single realized `(start, yaws)` satisfies — i.e. the `FreeP0` relaxation is not being tightened/rounded back to a realizable point. Build a tiny unit test: a hand-made convex free box with 2-3 walls whose feasible `(start, yaws)` is known, assert `solveJoint` returns it.

2. **Box reference `px/pz` was set to the raw seed, which can be OUTSIDE the box.** `DIAG box=... pxRef=-5.1456 (inBox=false)`. `deriveFreeStartBox` now clamps the reference into the box (`refX = clamp(seedX, pxLo, pxHi)`) — this keeps byte-identical for in-box seeds (the 5 gate captures) and is more correct, BUT it did NOT fix `solveJoint` (still `bestViol 0.31`), and the downstream CMA-ES search got WORSE with it (7/10 -> 3/10, `finalStart` walked to the box corner). So the reference was a smell; the real defect is #1 plus the unstable search. Keep the clamp (correct) but know it is not the fix.

3. **The whole nonlinear search is unstable / anchored on a useless seed.** For a seed far outside the box, the anchored pipeline burns budget solving at (-5.1456) (hopeless, 7/10 or 3/10 best-effort) before free-start runs, and the free-start pass is warm-started from those useless yaws. The result oscillates 3-7/10 across efforts. This is the plumbing (`freeStartImprove` two-stage + warm-start-from-seed) the plan already says to delete.

**Revised conclusion (updates Section 5(d)):** I earlier called the convex dual path "the safe floor." **f2f disproves that** — `solveJoint` is broken on real multi-constraint free boxes, just not on the toy tests. So there is no working floor yet. The FIRST deliverable is unambiguous now: **make `solveJoint` solve the f2f free-start** (add the multi-wall free-box regression test, run the localization test above, fix the `FreeP0` dual or the recovery). That is a small, convex, deterministic problem — no CMA-ES, no escalation, no parallelism needed — and if the clean dual cannot pick a feasible start out of a box that contains one, nothing built on top will work. Only after the convex floor is solid should the nonlinear (CMA-ES) search, escalation, and parallelism follow.

**Debug additions left in the tree for the next session (keep):** `EngineFileScreen` prints `preSolve`/`DIAG box`/`DIAG solveJoint (why=...)`/`success`/`finalStart`; `FreeStartSolve.lastJointDebug` records why `solveJoint` returned null; `solveJoint` tracks `bestViol`.

## 8. The one-paragraph version

Free-start's trigger, footprint, dual lift, keep-better shape, and byte-exact recovery all work and are gate-green. The unsolved problem is the nonlinear (CMA-ES) SEARCH for the freed start: it returns 6/7 on an EASY jump, which — because an easy jump has a large feasible set — is a code bug, not a precision limit. Before writing any more search code, diagnose why the search misses a large feasible set (suspects: the translated-fitness math, or shared-scenario mutation in `freeStartImprove`), starting with the single test "does a fixed strong solve at the moved start reach 7/7?". Then rebuild as one joint start+yaw CMA-ES with a small seed-centred start sigma, validated on the easy jump first; add escalation and parallelism after correctness.
