# Nix full-route free-start: diagnosis and plan

**Status 2026-08-21:** HomotopyCloser, MomentumAssembly, SolveCore, and every Nix* harness named below were deleted with the CMA-ES removal (PRs 373-379). nix-full-t1 is now an accepted-fail expectation (shouldSolve false), and the §10 solve does not reproduce on current code. The measured results and the falsified approaches stand as the record.

**▶▶ SOLVED 2026-07-07: the engine now solves nix-full-fails.json cold from t1 (success 15/15, objX 8.700000, solver "CMA-ES -> momentum assembly", feasible at ~153 s live). The full mechanism, the falsified paths, and the shipped code are in §10 at the end of this file. Everything between here and §10 is the (accurate) historical diagnosis that led to it.**

Goal: the full 54-tick Nix route must solve from tick 1 (t1). A t1 solution **exists and is proven in-game** (see §2). After step 1 (§4.1) the solver ships viol **4.7e-3** from t1 (down from the old 14/17 = viol 0.64), still infeasible. This document records what the jump is, why the solver falls short (measured), the plan to close it, AND (§6–§7) a deep investigation into whether a *certified global* solver for MC's exact movement exists or can be built. Written 2026-07-05, updated 2026-07-06, solved 2026-07-07. Repro harness: `core/src/test/.../anglesolver/NixFullDiag.java` (`PKC_SOLVE_FILE=<save> ./gradlew :core:test --tests '*NixFullDiag'`).

## 0. Handoff status (read first)

**Done in the prior session:**
- **Step 0 complete (§3a):** diagnosed that the constraints were over-specified. User confirmed `Z@18` and `Z@42` over-tight; **removed** from both files. The proven run is now byte-exact feasible (`constraintViol = 0.0`), so a model-feasible t1 target exists. Model byte-exact to game (`maxPosDiff = 8.9e-16`).

**Done this session (2026-07-05, second pass):**
- **Step 1 SHIPPED + mechanism-verified (§4.1):** `AngleSolverEngine.freeStartImprove` now runs `recoverStart(seedYaws)` and feeds the translated violation into the keep-better tracker *before* Stage 1. On `nix-full-fails` this turns the main solve's shipped result from viol **0.640 → 4.679e-3** (the −0.64 Z translate the pipeline used to discard). Live `EngineFileScreen` `FILE DIAG` confirms `savedYaws@seed viol=0.640 -> recovered(11.30000,1.20012) viol=4.679e-3`. Gate 329 green (free-start suite included), no regression. **Does not land Nix alone** (still infeasible at 4.7e-3), as predicted.
- **Full structural diagnosis of step 2 (§3b, the decisive experiments).** Established *exactly* what the automation must do and de-risked every component. Key results: the proven start is **(11.3, 1.84)** (not the monolithic-translate's (11.3, 1.20); they share X, differ 0.64 in Z); monolithic BnB cannot reach the proven basin even *at* the proven start (8-pattern cap); the last-2-jump tail **[30,54) is BnB-feasible from the proven seam** (1-jump and 3-jump tails are not; **2 jumps is the BnB sweet spot**); the tail is feasible over a coherent seam-velocity **band** (`vz ≳ 0.20`); and the blocker is that the lead-in must hit that band velocity **without overshooting the seam position** (a position-surrogate Z-MAX lead-in hits the velocity band but overshoots seam Z to 8.17 vs the needed ~3.19, unreconcilable by the footprint translation).

- **Certified-global-solver investigation (§6, §6b, §6c), later this session.** Prompted by "why not just use a global solver" and then "why not build one that needs no in-game re-validation." Corrected a model bug (must use MC's integer sine LUT, not continuous `Sin`; §6), mapped what a certified global solve would take (the sparse-MIQCP reformulation; §6b), and researched (6 agents total) whether an all-three byte-exact + certified + scalable tool exists (no) and whether one can be built (yes, windowed; §6c).

**▶ §7 DONE (2026-07-06): the SMT-FP window feasibility experiment ran; full result in §8.** Headline: byte-exact windows DECIDE with **Bitwuzla** (not Z3, which dies at 4 ticks): the final-jump `[42,54)` 12-tick tight-pad window is `sat` in ~35 s / `unsat` in ~0.5 s, and the full 24-tick 2-jump tail `[30,54)` (§3b's BnB sweet spot) is `sat` in ~10 min; a certified byte-exact optimum was bracketed. **BUT (decisive, §8.6): SMT-FP here VERIFIES tight neighborhoods, it does not SEARCH.** All those "decides" numbers used a ±0.02 deg tube around the PROVEN yaws (re-derivation). A 3-tick FULL-yaw-range solve returns `unknown` after 5 min, while the same 3 ticks seeded solve in ~0 s. So SMT-FP is a byte-exact per-window VERIFIER (bound/prove a candidate from another solver), NOT a route finder; §6c track B as a *search* path is dead, and the §8.3 scaling curve only measures re-derivation at length. New in-repo: `core/src/test/.../anglesolver/NixSmtDump.java` (env-gated dumper, gate green). Scratch Python (numpy byte-exact sim + Z3/Bitwuzla encoders + `frsolve.py` full-range test) in the session scratchpad `smt/`.

**▶ NEXT SESSION: TRACK A (§4.2). The deliverable is a GENERAL solver for the momentum-cancellation (Nix/neo) route CLASS, not a fix for this one jump.** Acceptance = ≥2-3 Nix routes solving cold from t1, zero route-specific literals (derive tail size, seam, band per route). So **FIRST run the cross-route generality probe (§4.2)** on the sibling saves (`j345_3jmm`, `j422_2jmm`, `j347`, `j425_1jmm`); if each route is its own puzzle rather than one shape, STOP. Only if the shape holds, build the coupled seam solve: BnB the largest-solvable tail from its seam + a non-overshooting lead-in that hits the seam band + one global translation. (Track B / SMT is CLOSED as a search path, §8.6: it verifies, it cannot search.) This route is diagnosed in §3b: proven start (11.3, 1.84); tail `[30,54)` BnB-feasible; seam band `vz ≳ 0.20`; blocker = the lead-in must hit the band velocity without overshooting seam-Z.

**Two ongoing tracks this doc serves (pick per goal after §7):**
- **(A) Land Nix**, the coupled seam solver (§3b → §4.2): search the seam velocity (band known), realize it with a non-overshooting lead-in, BnB the 2-jump tail, reconcile position by global translation. A genuine joint search (lead-in shape ↔ seam velocity ↔ seam position ↔ tail).
- **(B) A certified byte-exact solver**, CLOSED as a search path (§8.6). SMT-FP (Bitwuzla) can only VERIFY a byte-exact candidate / a tight neighborhood (±0.02 deg tube around a known answer); it cannot SEARCH the facing space even at 3 ticks. Its only durable use is as a per-window verifier of a candidate that came from track A (or another solver). Not a route finder; do not pursue as a from-t1 solver.

**Git state (branch `v1.7.0-position-free-solver`):**
- The *convex-floor free-start fix* from the prior session is **committed as `6d02be0 fix`** (orthogonal to Nix; single-jump convex f2f only, `countJumps > 1` bypasses `solveJoint`).
- **Step 1 is COMMITTED** as `cf34994 fix` (the 13-line `AngleSolverEngine.freeStartImprove` `recoverStart(seedYaws)` keep-better probe + the 245-line `NixFullDiag.java` with the proven-start BnB probe). Verified present at `AngleSolverEngine.java:963`.
- `LongRunSolver` was touched and **reverted** (a last-window BnB rescue: did not land Nix and would 10s-stall every infeasible last window in `VelocityFinder`'s per-cell `windowSolve`; do not re-add without gating it away from `VelocityFinder`). Verified clean (no `BoundPrunedRecovery` reference). The throwaway harnesses `NixWindowProto` + `NixWolframDump` were deleted (findings folded into §3b/§6).
- New this session (2026-07-06): `core/src/test/.../anglesolver/NixSmtDump.java` (untracked; the §8 extraction harness, `assumeTrue`-gated so inert in the normal gate, `:core:test` re-verified green with it present) plus this doc's §8. The SMT scratch Python (numpy float32 sim + Z3/Bitwuzla encoders + certified-optimum driver) lives in the session scratchpad `smt/`, not in-repo (`core/` is MC-free Java 8; the SMT work is intentionally out-of-tree). This doc is untracked; working tree otherwise clean.
- *Gate:* `./gradlew :core:test -x tableStyleCheck` (**329 green, re-verified 2026-07-06**). Run before/after any change; the convex fix's byte-identical invariant depends on `freeP0 == null` staying untouched.

**Save files were edited (permanent):** `Z@18` (tick-18 `Z IN [1.1375,7.3]`) and `Z@42` (tick-42 `Z LE 6.4875`) deleted from both nix-full files. Session-local backups (in the scratchpad) are gone in a fresh session; to reconstruct the originals, re-add those two constraints. All other nix save files are untouched.

**⚠ SAVE FLOOR IS RELAXED (reconcile before track A):** the `X@54` landing floor in `nix-full-works.json` is currently `>= 8.6994`, a RELAXATION the user set to view the trajectory. The REAL floor is `X@54 >= 8.70000` (the pad is `X@54 in [8.7,10.3]` per §1). A solution at e.g. 8.69998 is a ~2e-5 MISS under the real floor. **Before running track A, RE-TIGHTEN `X@54` to 8.70000 in the save (and check `nix-full-fails.json` for the same relaxation), or the solver will target the wrong pad.**

## 1. What the jump is

- MC 1.8.9. 54 ticks, starts from **rest** (v0 = 0), `startTick 0`, `landingTick 54`, objective **X-MAX @ 54**, effort CUSTOM.
- **Four jumps** (JUMP fires while grounded at ticks **5, 18, 30, 43**). Ground/jump pattern the model runs (`.`=air `g`=ground `J`=jumpFires):
  `gggggJ...........gJ...........J...........gJ..........` → grounded only at 0-4, 17, 30, 42-43; airborne ~85% of the route (a chained-momentum route). `countJumps = 4`.
- Per-axis **inertia gate** (`|v| < 0.005 → 0`, every tick, top of tick) is load-bearing throughout; this is a Nix (momentum-cancellation) route.
- **Free-start footprint** at t1: `X[9.7, 11.3] Z[1.2, 4.3]`. Constraints run the whole route: an X-corridor `[9.7,11.3]` through ticks 0-41, Z ceilings (`Z@46 ≤ 7.7`), `X@47 ≤ 8.7`, `Z@53 ≥ 9.3`, landing pad `X@54 ∈ [8.7,10.3] Z@54 ∈ [9.3,10.3]`. Originally 26 compiled `JumpConstraint`s; **now 23** after removing `Z@18` and `Z@42` (§0/§3a).
- `useWindowSolver = False` → the engine solves it **monolithically** over all 54 ticks (the receding-horizon multi-jump path is bypassed).

The two supplied files (`nix-full-fails`, `nix-full-works`) differ only in `timeBudgetSeconds` (0 vs 600) at the top level; the real difference is in their `rows`/`result`.

## 2. Proof the t1 solution exists (and how it was found)

`nix-full-works` **replays from t1 and lands in-game**, a full t1 solution the solver could in principle find. It was produced by a **manual receding-horizon** process (the model behind the reasoning below):

1. **Hand-set the momentum setup** through ~tick 22 (start facing + the first 1-2 jumps + the cancellation ticks). In the save these appear as row yaws at t0, t18, t19 with **null yaws elsewhere in t1-27** (null = "hold previous" in replay; a placeholder for the hand-set setup, which is why a naive forward of the save's rows does not reproduce the landing: the setup ticks are underspecified in the persisted file, not wrong physics).
2. **Solve from ~tick 29/30** (the 3rd jump), **relaxing the landing requirement by 0.001** so a feasible entry is found, then let **ILS tighten** it.
3. **Re-solve from ~tick 43/44** (the 4th/final jump) from that improved entry. The persisted `result` (11 yaws, t44-54, 5/5) is this last window.

Two techniques from this process are load-bearing and must be automated: **(a) decompose at the jump boundaries and chain committed exit states**; **(b) relax a too-tight constraint by ~1e-3 to reach a feasible basin, then ILS back to byte-exact**.

## 3. Why solving from t1 falls short (measured on `nix-full-fails`)

The monolithic solve returns **14/17**, solver chain `CMA-ES -> free start`, objX 8.6987. Evaluating that 54-yaw result:

1. **The shape is good but mispositioned.** The dominant failures are a Z-corridor bulge: `Z@42` and `Z@46` are **~0.64 too high**. This is a **pure translation error**: `recoverStart` on the shape shifts the start **Z by −0.64066** (into the footprint, Z 1.84→1.20) and the violation collapses **0.64 → 4.7e-3**. (Translation invariance holds under the inertia gate: the gate acts on velocity, which is position-independent; `constPos` keeps the start coefficient = 1 across zeroing ticks.)

2. **Free-start never applies that translation, a plain bug.** `AngleSolverEngine.freeStartImprove` runs `recoverStart` only on *fresh CMA shapes it re-searches* (`locYaws`), never on the main solve's already-good shape (`seedYaws`). So the one shape that translates to near-feasibility is the one it never translates; it ships 14/17 at the seed.

3. **Even translated, the last 4.7e-3 cannot be closed at that fixed start.** `BoundPrunedRecovery` (exhaustive, clamp-aware, pattern-enumerating feasibility solver) finds **no feasible shape at (11.3, 1.20)**. The residual is a non-translatable X-coupling conflict (`X@47` wants start-X down; `X@42`/`X@54` want it up). **The feasible start is not the fixed shape's translate; start and shape must be co-optimized.**

4. **The convex free-start dual (`solveJoint`) is inapplicable here.** It is clamp-free and single-model; on this 4-jump inertia route it returns null (viol 0.85/3.6). The recent convex-floor fix (center reference + Moreau smoothing) does not reach this case.

Root cause: there is **no joint (start position + multi-jump-clamped-shape) optimizer**. The pipeline decouples (main solve fixes the start and optimizes the shape; free-start moves the start but with a shape it cannot co-adjust), which is exactly why the manual process had to interleave start/shape/entry by hand.

## 3a. Step 0 result (2026-07-05): the constraints are over-specified

Forwarding the `works` save's **`debug` block** (the recorded sim trajectory; the actual per-tick yaws live there, resolving the null row-yaws) through `ExactJumpModel`:

- **The model is byte-exact to the game:** `model-vs-sim maxPosDiff = 8.9e-16`. No physics/model discrepancy; the solver's model is correct.
- **The proven-in-game run lands the pad (X@54 = 8.70002, Z@54 = 9.4885) but is NOT byte-exact feasible against the constraints as specified.** It violates:
  - `Z@18 ∈ [1.1375, 7.3]`: actual **0.954**, below by **0.184** (the momentum setup dips below the corridor floor).
  - `Z@42 ≤ 6.4875`: actual **6.505**, over by **0.017**.
  - Every *late* constraint (Z@46, X@47, Z@53, landing pad) is hit byte-exactly (wall-hugging).
- Setup yaws are a constant **45°** (one tick at 0°) through t27, then the solved yaws.

**Consequence:** the monolithic solver (`FEAS_TOL = 0`, all constraints simultaneously) can *never* reproduce the known-good run: that run violates `Z@18` and `Z@42`. This is not primarily a weak-search problem; the **constraint set as authored is mutually infeasible with the real landing** (the 14/17 solver run and the proven run violate *different* constraints; no single byte-exact run satisfies all). The manual windowed process succeeds because it never enforces all constraints at once: `Z@18` lives in the hand-set setup window (unsolved), and the rest were reached via relax-then-ILS.

**Resolved (2026-07-05, user decision): both are over-tight and were REMOVED.** The user confirmed `Z@18` and `Z@42` are not real obstacles (over-tight corridors). Removed from both `nix-full-fails.json` and `nix-full-works.json` (backups in the session scratchpad):
- tick 18: `Z IN [1.1375, 7.3]` deleted (tick 18 now has no constraints).
- tick 42: `Z LE 6.4875` deleted (tick 42 keeps `X IN [9.7,11.3]`).

After removal (`#JumpConstraints` 26 → 23) the **proven-in-game run is byte-exact feasible: `constraintViol = 0.0`** (model = sim at 8.9e-16, lands X@54 = 8.70002, Z@54 = 9.4885). So a model-feasible t1 landing now provably exists and the solver has a valid target. This was the primary blocker; the multi-jump search (below) is the remaining work.

## 3b. Decisive experiments (2026-07-05): what step 2 must do, measured

All numbers below come from harness probes against `nix-full-works.json` (whose `debug` block **is** the proven run; the `nix-full-fails` debug is a *different, non-landing* run; do not read seams from it). Jump boundaries `bounds = [0, 17, 30, 42, 54]` (4 jumps). The proven run's seam states (forward the proven `debug` yaws through `ExactJumpModel`):

| tick | pos | vel | note |
| --- | --- | --- | --- |
| 0 | (11.3000, 1.8408) | (0, 0) | proven start (X-max corner, Z=1.84) |
| 30 | (11.3023, 3.1942) | (0.00288, 0.20688) | entry to the **last 2 jumps** |
| 42 | (9.7000, 6.5047) | (-0.16305, 0.19379) | entry to the final jump |
| 54 | (8.7000, 9.4885) | - | landing |

**(1) The proven start is (11.3, 1.84), and monolithic search cannot reach its basin, even *given* that start.** The monolithic solve's shape translates to a *different* start, (11.3, **1.20**) (same X, Z lower by 0.64), and residual-violates there (§3.3). At the actual proven start (11.3, **1.84**), where a feasible shape provably exists (proven yaws → viol 6.6e-7), `BoundPrunedRecovery` runs **48 s and returns NULL (exhausted, not timed out)**. Cause: BnB caps at `MAX_PATTERNS = 8` cancellation patterns over the whole 54-tick route; the proven shape's pattern is outside its top-8. So no fixed-start solver (CMA, closed-form, or BnB) reaches the proven basin monolithically. **A start-position search is therefore not enough**: the shape itself is unreachable at full-route scale.

**(2) The last-2-jump tail is BnB-solvable; 2 jumps is the sweet spot.** Slice `[a,54)`, seed it with the exact proven seam (pos+vel+yaw), solve for the real objective:
- `[42,54)` (1 jump): ClosedForm / SLP / **BnB all NULL**.
- `[30,54)` (2 jumps): ClosedForm / SLP NULL, **BnB → viol 0.0, feasible, objX 8.69954**. ✅
- `[17,54)` (3 jumps): all NULL (too many patterns for the 8-cap again).

So decompose at `seam = bounds[jumps-2] = 30`: **lead-in `[0,30)` + tail `[30,54)`**, and solve the tail with **BnB** (the convex dual and SLP cannot handle the 2-jump cancellation clamp).

**(3) The tail is feasible over a seam-velocity BAND, not a point.** Fix the tail entry position at the proven (11.3023, 3.1942), sweep entry velocity, BnB each (`F`=feasible):
```
vz=0.14  vx[-0.06 -0.03 0.00 0.03 0.06]:  . . . . .
vz=0.17                                  :  . . . . .
vz=0.20                                  :  F F . . .
vz=0.23                                  :  F F F F .
vz=0.26                                  :  F F F F F
```
A coherent region: roughly **`vz ≳ 0.20`, widening as `vz` rises**. The proven seam (0.003, 0.207) sits near its lower-left edge. **Higher `vz` is strictly more permissive**, so the lead-in wants *high Z-velocity* at the seam.

**(4) The blocker: the lead-in must hit the band velocity WITHOUT overshooting the seam position.** A position-surrogate lead-in `[0,30)` solved for **Z-MAX@30** is feasible and produces seam **vel (0.0, 0.262)**, squarely in the band (this means **no velocity-*targeting* is even needed to reach the band**). But its seam **position** is (11.23, **8.17**): Z-MAX maximizes Z-*position*, overshooting to Z=8.17 versus the needed ~3.19. Translating that seam down to the feasible position needs `dZ ≈ −5`, which drives the free start to Z ≈ −3.8, far outside the footprint `Z[1.2,4.3]`. Confirmed both directions: **tail BnB from the Z-MAX lead-in's own seam = NULL; tail BnB with the same Z-MAX velocity but placed at the proven position = FEASIBLE.** So velocity ✓ and position ✗ under the same lead-in; the two must be produced together. (X-MIN@30 lead-in: seam vel (−0.08, 0.0), `vz=0` → outside the band, tail NULL everywhere. Confirms the objective choice matters.)

**Net:** step 2 is a **coupled seam solve**: a lead-in that lands the seam simultaneously in the velocity band and at a position the footprint-translation can reconcile with a feasible tail. Every *component* is proven to work (tail BnB lands from a good seam; the band and proven seam are mapped); only the joint lead-in↔seam coupling is unautomated. This is exactly the hand-interleaved part of §2.

Repro: the probes above were a throwaway harness (deleted). To reconstruct, extract the proven seam via `NixFullDiag`'s DEBUG-forward block, then slice `[a,54)` with `LongRunSolver`'s `sliceScenario`/`sliceConstraints` (copy them; they are `private`), seed the exact seam, and call `BoundPrunedRecovery.solve(model, tailSpec, 0.0, cancel, 10e9, 1e300)`. Use `nix-full-works.json`.

## 4. Plan

0. **DONE: constraints reconciled.** `Z@18` and `Z@42` removed (over-tight, user-confirmed); the proven run is now byte-exact feasible (viol 0.0). Model verified byte-exact (8.9e-16).

### 4.1 Step 1: SHIPPED (translate the main solve's shape)

In `AngleSolverEngine.freeStartImprove`, before the `sc.startBox = freeBox;` line, `recoverStart(exact, spec, seedYaws)` now runs and its `violationAt` is fed into the `foundYaws/foundX/foundZ/foundViol` keep-better tracker. This makes the −0.64 Z translate of the *main solve's own shape* a candidate (it used to only ever `recoverStart` the fresh Stage-1 CMA shapes). Effect on `nix-full-fails`: shipped viol **0.640 → 4.679e-3** at start (11.30, 1.20). Verified via `EngineFileScreen` `FILE DIAG`; gate 329 green. **This is the whole of step 1 and it is done.** It does not land Nix (still 4.7e-3 infeasible); §3b explains why (no fixed-start shape is feasible at (11.3, 1.20); the feasible start is (11.3, 1.84) with a *different*, windowed shape).

### 4.2 Step 2: the real fix (coupled seam solve). NOT the vanilla windowed solver.

**Generality is a must-have, not an afterthought (user directive).** Everything in §3b is measured on ONE route (N=1): `seam=30`, "2-jump tail", `vz≳0.20`, the footprint extent are all Nix-specific *numbers*. The *algorithm shape* may generalise ("grow the BnB tail to the largest window its pattern budget can still solve; search/target the seam the lead-in must produce; translate"), but that is unproven. Requirements: (i) **derive every constant**: tail size = largest window BnB solves (try large→small, not a hardcoded 2); seam = a jump boundary; band = measured per route; **zero Nix literals in code**. (ii) **reuse existing general machinery** (`LongRunSolver`, `BoundPrunedRecovery`, `VelocityFinder`, `FreeStartSolve`), not a new Nix module. (iii) **acceptance gate = the CLASS**, ≥2–3 Nix routes solving cold, not just this one. **Before building, run the cheap cross-route probe below; if each route is its own puzzle rather than one shape, STOP**: a single hard jump is not worth a bespoke subsystem. (Step 1 in §4.1 is already general and stands regardless.)

**Cross-route generality probe (do this FIRST).** Sibling Nix saves already on disk in the same folder: `j345_3jmm_*`, `j422_2jmm_*`, `j347_*`, `j425_1jmm_*` (a spread of jump counts; confirm which are free-start first). Run the §3b experiments on 2–3: does the largest-BnB-solvable tail always fall at a jump boundary, and is there always a seam band? Only if the shape holds do you build the general solver below.

**Do not just enable `useWindowSolver` / `LongRunSolver`.** Measured (§3b): vanilla receding-horizon gets stuck at the final jump: its lead-in windows optimize a surrogate (Z-MAX) and commit a seam whose *velocity* lands in the tail band but whose *position* overshoots (Z=8.17), so the tail is unreconcilable. Adding a BnB last-window rescue to `LongRunSolver` was tried and reverted (no help + it would 10s-stall `VelocityFinder`). The decomposition point is fixed at `seam = bounds[jumps-2] = 30` (last 2 jumps), **not** a sliding window.

The automation the manual §2 process performs, made concrete by §3b:

1. **Tail solver = BnB on `[30,54)`.** Given a seam state (pos, vel) in the feasible region, `BoundPrunedRecovery.solve(model, tailSpec, 0.0, cancel, ~10e9, firstFeasible)` lands it (proven). The 2-jump tail is the unit BnB can actually solve; do not slice finer (1-jump [42,54) is unsolvable) or coarser (3-jump [17,54) blows the 8-pattern cap).
2. **Seam target.** The tail-feasible band is `vz ≳ 0.20` (widening with `vz`) at a seam position the footprint translation can reach. `vz` wants to be *high*; the constraint is that the seam *position* stays reconcilable (the Z-MAX failure is pure position overshoot). Practically: search/solve for a lead-in whose seam sits at high `vz` **and** moderate seam-Z (~3–4), i.e. a Nix cancellation that builds Z-velocity while cancelling Z-drift, which is exactly what the proven lead-in does and what a naive position objective does not.
3. **Lead-in solver = produce that seam.** This is the unautomated core. Two candidate formulations:
   - **(a) Velocity-targeted lead-in.** Add a seam-velocity target to `[0,30)` (the objective framework is position-only today; a per-tick displacement proxy `posZ[30]−posZ[29]` is expressible as a `JumpConstraint` with `t2=29, op=MINUS`, but it is a friction-approximate stand-in for `vz`, not exact). Solve `[0,30)` (BnB: it has 2 cancellation jumps too) to hit the target seam velocity, then chain.
   - **(b) Outer seam-velocity search (VelocityFinder-shaped).** Treat seam `vz` (and `vx`) as a 2-D outer variable. For each candidate: BnB the tail (fast reject if infeasible), and BnB the lead-in to realize it; accept the first that chains + globally-translates feasible. `VelocityFinder` already sweeps entry velocities against feasibility (`core/.../velocity/VelocityFinder.java`); its machinery is the closest existing analog but is UI-coupled (`BoxController`, `ProblemFactory`).
4. **Position reconciliation = one global translation.** The whole route (lead-in + tail) is translation-invariant, so after chaining, apply `recoverStart`/`pinTranslate` over the footprint to pin the absolute-position constraints (landing pad, X-corridor). The free start's 2 DOF are spent here. The tail's own BnB already satisfies the tail constraints at its solved position; the translation aligns the lead-in exit to that position while keeping the start in the footprint.
5. **(Optional) relax-then-ILS** per §2 if a seam sits just outside a byte-exact basin: relax the binding tail constraint ~1e-3 to admit a feasible BnB, then `IlsPolish` back to viol 0.

Recommended order: (0) cross-route generality probe above: gate the whole effort on the shape holding; (1) prototype formulation (a) first (fewer moving parts: one extra constraint + BnB lead-in + BnB tail + translate); (2) fall back to (b) if the displacement-proxy target is too loose to steer the cancellation. Validate end-to-end in the harness on **the class** (≥2–3 Nix routes solving cold from t1), not just this one, before wiring into `runJob`. Only then decide the engine entry point (likely a new `countJumps > 1 && freeStart` branch that calls this coupled solver instead of, or before, the monolithic `dualChain` + `freeStartImprove`). If generality forces many route-specific special cases, that is the signal to abandon the subsystem rather than ship a Nix-only contraption.

**Former step 3 (fold the inertia gate into a joint per-window dual)** is superseded by §3b: the tail is solved by *BnB*, not the convex dual, because the convex dual provably cannot handle the 2-jump cancellation clamp here (measured NULL). Keep it only as a possible objective-polish once feasibility is automated.

## 5. Files and repro

Saves live in the user's game instance (NOT in-repo):
`C:/Users/benja/Desktop/Games/MultiMC/instances/1.8.9/.minecraft/parkourcalculator/`
- `nix-full-fails.json`: t1 solve. Its saved `result` is the 54-yaw monolithic attempt (viol 0.640 at the seed start; with step 1 the engine now ships its −0.64 translate at viol 4.679e-3). Its `debug` is a **non-landing** recorded run (viol 3.84e-3); **do not read proven seams from it.**
- `nix-full-works.json`: the proven t1 landing. **The landing yaws are in its `debug` block** (`debug[k+1].yaw` = tick k), NOT in `rows` (setup rows t1-27 are null) nor `result` (stale 11-yaw t44-54 window). Both files now have `Z@18`/`Z@42` removed.
- `nix_works.json`: the extracted t44-t54 window (solves standalone). `nix_slight_shift.json`: single-window seed shifted 0.005 (the convex-floor case from `free-start-handoff.md` §0).

Repro commands (Git Bash; the harness is `assumeTrue`-gated on the env var, so it is a no-op in a normal gate run):
```
export PKC_SOLVE_FILE="C:/Users/benja/Desktop/Games/MultiMC/instances/1.8.9/.minecraft/parkourcalculator/nix-full-fails.json"
./gradlew :core:test --tests '*NixFullDiag' --rerun-tasks -q
# read stdout from core/build/test-results/test/TEST-*NixFullDiag.xml <system-out> CDATA
```
`NixFullDiag` prints: countJumps + ground/jump pattern; the RECORDED and DEBUG (proven) runs forwarded through the model with per-constraint violations + model-vs-sim maxPosDiff; **the proven start + an exhaustive `BoundPrunedRecovery` probe at the proven start** (the §3b.1 result: NULL even where a feasible shape exists); the saved-result per-constraint eval; `recoverStart` of the saved shape (the −0.64 translate + residual); a `BoundPrunedRecovery` probe at the translated start; and `solveJoint`. Run it on **`nix-full-works.json`** to get the proven seams (its debug is the proven run). `EngineFileScreen` (same env var) drives the *full live engine* on a save (`PKC_SOLVE_EFFORT`, `PKC_SOLVE_TIMEOUT_MS`); note `nix-full-fails` has `timeBudgetSeconds=0` (unbounded), so a full run may not terminate; read its `FILE DIAG` line for the step-1 mechanism, or use a budgeted save copy for an end-to-end met/total.

Key facts a new session needs: the model is byte-exact to the game (proven, 8.9e-16); translation invariance holds under the inertia gate (start shifts positions rigidly, velocities/gate pattern unchanged; `constPos` keeps p0 coef 1 across zeroing ticks); `countJumps > 1` here so the single-jump convex path (`solveJoint`) is bypassed/inapplicable; `useWindowSolver = False` so it solves monolithically; **BnB caps at `MAX_PATTERNS = 8`; this is why it reaches the 2-jump tail basin but not the 4-jump full-route or 3-jump basins.**

Related: `docs/research/free-start-handoff.md` (§0 convex-floor fix, this session), `docs/research/free-start-position-plan.md` (Phase 4 = multi-jump single-translation; since consolidated into `free-start-handoff.md` §10 and deleted, 2026-08-21), memory `project_nix_multijump_freestart` + `project_free_start_position`.

## 6. Global-solver investigation (2026-07-05): can Wolfram / a general optimizer solve this?

Prompted by the recurring "just use a global solver" idea. Two mature external projects solve the same MC-parkour movement model (both mirror ParkourCalc's linear-in-(sin,cos) model; unknowns = per-tick facing angles):
- `02 Python/TAS-Wolfram`: **global** `NMaximize` over movement directions, in Wolfram. Handles the inertia gate by **fixing the cancellation pattern as constraints** (`inertia_events`: `hit` → `|v| ≤ 0.005`, `avoid` → `|v| ≥ 0.005`, velocity proxied as `X[F,slot] − X[F,slot−1]`). Stages **strict → relaxed** (relax tolerance to reach feasibility) then snaps to the exact 2^16 angle and re-verifies bit-exact against mothball. So even the *global-solver* project does not feed the raw gate to the optimizer, and it stages relax-then-tighten (the §2 trick).
- `06 C++/Sheepram` (Odin, by Curryocity): **local** Augmented-Lagrangian + BFGS. Assumes the per-tick movement method is predetermined (pattern fixed). Explicitly names the momentum-cancellation case **"Neo: the rabbit hole"**: "the optimal route is not the geometric boundary; a trade-off between short-term distance loss and later velocity gain." That is Nix exactly.

**Empirical run + a corrected model (this session).** Ported Nix's exact per-tick physics (`f4`/drag, `mMag`, `baseArg` from `JumpLinearModel`, at the proven start) into a Wolfram forward recurrence. **The first attempt used continuous `Sin`/`Cos` and was WRONG**: it scored the byte-exact-feasible proven path at `totalViol = 0.0135` (lands `(8.686,9.488)` vs real `(8.700,9.489)`). This is NOT a "sine-table gap" (smooth sine error is ~1e-5/1e-6); it is that MC uses an **integer sine LUT** `TABLE[(int)(rad*10430.378) & 65535]` (`McSineTable`), and a ±1 bucket shift changes a velocity by ~1e-4, which is enough to **flip whether the inertia gate `|v|<0.005` fires at a cancellation tick**. Momentum-cancellation routes live exactly on those flips, so a smooth-sine model silently mis-cancels. **Fixed**: re-porting with the exact integer LUT drops the proven path to `totalViol = 8.4e-5` (lands `(8.70004,9.48851)`), faithful to ~1e-5 (float32-vs-double residual). *Lesson: any continuous/external port MUST use the integer LUT, or it is not modelling this problem class.* (The earlier "NMinimize scores 0.70 cold" number was on the broken smooth model and is discarded; NMinimize is heuristic anyway; §6b.)

## 6b. Can a solver find a CERTIFIED global optimum for the exact movement? (web research, 2026-07-05)

Four parallel research agents (rigorous MINLP solvers; hybrid/PWA optimal control; game-TAS communities; Wolfram capabilities); full citations in memory `reference_global_optimization_mc`. Synthesis:

- **Byte-exact model (real 65536-entry LUT): a certified global optimum is combinatorially hopeless and has NEVER been done in any game.** The discrete-angle space is 65536^54 ≈ 10^240. Every mature game-TAS effort (TrackMania Bruteforce/Linesight, SM64 brute-force/Scattershot, Celeste Featherline/Radeline, Quake greedy strafe theory) uses black-box local search or RL against a fast exact simulator, because the LUT + hard threshold defeat analytic solvers. ParkourCalc is *ahead*: it has a closed-form per-tick recurrence; they only have opaque simulators.
- **Wolfram cannot auto-certify this.** `NMinimize`/`NMaximize` are heuristic (no global guarantee for nonconvex); `Minimize` (CAD) certifies only linear/polynomial-algebraic and is doubly-exponential in #vars; a `floor`-indexed LUT is not algebraic. The only certifying path in Wolfram is a hand-built MILP fed to `LinearOptimization[…,Integers,Method→"Gurobi"]`.
- **THE reformulation (key result): the recurrence is LINEAR in `(sin θ_t, cos θ_t)`.** Substitute `s_t=sinθ_t, c_t=cosθ_t` with `s_t²+c_t²=1`. Then position, objective, and all corridor/landing constraints are linear in `{s,c}`; the entire nonconvexity is 50 **decoupled** unit circles (tight disk relaxation) + ~100 inertia-gate **indicator/big-M binaries** (the gate fires on only a few ticks). This is a **sparse MIQCP** = a textbook discrete-time PWA optimal-control problem (Marcucci–Tedrake MICP; Bemporad–Morari MLD). Sparse, so at the FRONTIER of solvable (dense nonconvex dies ~50 vars). Route-agnostic → also the GENERAL path.
- **Certified global of the SMOOTH model is plausibly achievable in a day** with Gurobi 12/13 / SCIP 9 / BARON (BARON works: trig eliminated → QCQP). Not guaranteed (worst-case exponential; hinges on tight velocity bounds → small big-M). Variants: MISOCP (disk relaxation, *exact* when the objective saturates `|u|`); polygon-approx circle (two-sided certificate, error ~1/K²). Certificate is for the smooth model, ~1e-4 off the game.
- **Recommended pipeline for "nothing left to be done":** (1) reformulate to the sparse MIQCP; (2) solve to a global certificate (Gurobi/SCIP/BARON, 24h, gap 1e-6); (3) snap + verify/repair byte-exact with the in-repo `ExactJumpModel` (local integer search over ±few LUT buckets/tick). Result = **a provable bound (no smooth path beats X) + a byte-exact solution within ~1e-4 of X**. Since 1e-4 is the game's angular resolution, that is a practical proof of optimality.
- **Feasibility-only certificate** (just "does it land"): hybrid-zonotope reachability or PARC backward reach-avoid give an EXACT yes/no reachability certificate for PWA systems, without optimizing.

**Consequence for this repo.** The certified route is a separate tool (Julia+JuMP+Gurobi or Python+Pyomo), not in-repo Java. But it validates the existing architecture: ParkourCalc's **BnB (branch the gate/sign pattern, convex inside, bound per branch)** IS the domain-specific version of the endorsed MICP approach; its horizon-scaling limit is exactly why windowing (§4.2) exists. Cheap borrow from TAS communities: **incremental resimulation** (resume from a cached prefix state when only late ticks change) as a search speed lever. "Use a global solver" is real and certifiable, but as a *smooth MIQCP + byte-exact verify*, not a monolithic byte-exact global solve (impossible at this scale) and not raw `NMaximize` (heuristic).

## 6c. Byte-exact + certified + scalable: does an all-three solver exist, and can we build one? (research, 2026-07-05)

Two more research agents (full citations in memory `reference_byte_exact_certified_solver`), triggered by "why not build a solver that does exact float arithmetic so there is no in-game re-validation." Note first: **the "verify" step is NOT in-game**: `ExactJumpModel` is a byte-exact in-repo replica (8.9e-16 vs the live entity) and the existing solvers already optimize against it, so their results are byte-exact; the only relaxed part is any MIQCP/smooth bound.

**Does an all-three tool exist? NO (confirmed).** The one engine that fuses byte-exact + certified is `OptiMathSAT`'s OMT over the floating-point theory, but it optimizes a SINGLE FP variable on single SMT-LIB queries; even FP *feasibility* stalls at a few hundred FP vars (bit-blasting: one fp32 multiply ≈ 1048 SAT vars; a 50-tick chain + sine table is pathological). Every other corner drops exactly one axis: exact-rational SCIP 10 + VIPR (certified, but rationals not floats); 2026 GPU rigorous optimizer (thousands of dims but weakly-coupled only, not byte-exact); interval B&B Ibex/Charibde (byte-exact-valid + certified but coupled n≈5–10; Charibde: separable Michalewicz n=50 in 8s vs coupled Keane n=4 in ~4h); heuristic TAS/RL incl. ParkourCalc CMA/ILS (scale + exact, no certificate). No MC-movement optimality/impossibility proof exists anywhere (only seed-cracking); closest exact-physics-certified precedents are tiny (Atari Dragster ~6s near-1D; MrWint smb-opt certifies sub-movements only, not whole levels). This problem is at the frontier.

**Can we build one? YES, but only WINDOWED, and it is an upgrade to `BoundPrunedRecovery`, not a rewrite.** The scheme "exact incumbent + validated over-approximating bound + B&B" is the standard template (MILP, α,β-CROWN NN verifiers, Ibex, dReal δ-complete SMT). It DODGES the PSPACE-hardness of bit-exact decision by *enclosing* (padded interval/zonotope arithmetic over the LUT + float32) not *deciding* the bit model, and *verifying* flagged candidates with `ExactJumpModel`. Monolithic 50 is out (coupled interval B&B caps ~n5–10; a window of ~8–15 ticks = ~8–15 free angles sits right at that ceiling). Windowed (~8–15 steps, carried entry-state intervals, stitched compositionally) is realistic BECAUSE of the favorable structure: 4-D state, friction ×0.91 contracts the enclosure each step, few gate firings (branch only ambiguous gates → 2^few not 2^50), strong incumbents (B&B certifies a known answer). **Architecture:** `ExactJumpModel` = incumbent oracle + verifier (exists); `ClosedFormSolve`/CMA = incumbents (exist); the ONE new hard piece = a validated one-step enclosure `T#` (LUT bucket min/max + half-ulp float padding, zonotope/affine to fight the wrapping effect) replacing `BoundPrunedRecovery`'s linearized dual bound; plus DP-reachability + windowing. Effort multi-week to few-month; **the make-or-break risk is `T#` tightness over the per-step trig ANNULUS** (the real enemy, not the gate). The wall is real: NP-hard, byte-exact strictly harder than the real relaxation, no guarantee a given route certifies (instance-dependent).

**Decisive next experiment:** the SMT-FP feasibility ceiling (~1000 FP vars) plausibly covers a ~10-tick window (~50–100 float32 ops). Encode a byte-exact ~10-tick Nix window in SMT-FP (Bitwuzla / Z3 FP theory: LUT as table, ops as `fp.mul`/`fp.add` RNE, gate as ite), ask "lands the pad?". A byte-exact `sat`/`unsat` in reasonable time proves the exact+certified corner is reachable for windows (→ windowed-SMT-FP + stitching is viable); a timeout means the validated-bound B&B (softer per-window certificate) is the path. This is the one bounded test that converts "plausibly reachable" into a measured yes/no. **Fully specced in §7, the next session's first step.**

## 7. FIRST STEP FOR THE NEXT SESSION: SMT-FP window feasibility experiment

**DONE 2026-07-06: RESULT IN §8.** The spec below was executed. Outcome: Bitwuzla decides byte-exact windows (12-tick tight-pad ~35 s, 24-tick tail ~10 min); Z3 does not (dies at 4 ticks). The next-session pointers in §0 and the verdict are in §8; keep this section as the experiment spec.

**Goal.** Measure whether a byte-exact SMT-FP solver can DECIDE a short MC-movement window (feasibility first; optimality later) in reasonable time. Outcome interpretation: `sat`/`unsat` fast at ~12 ticks → the "byte-exact + certified, zero re-validation" corner is reachable per window → windowed-SMT-FP + stitching becomes a real certified path (§6c track B). Timeout early → the bit-blasting wall bites; fall back to the validated-bound (`T#`) B&B upgrade to `BoundPrunedRecovery` (§6c). This is a bounded ~1-day probe, not a commitment to build the whole thing.

**Tooling.** Python 3.12.6 is available; **z3 is NOT installed**: `pip install z3-solver` (Z3 has the FloatingPoint theory). If Z3 stalls, escalate to **Bitwuzla** (stronger QF_FP, has Python bindings). Note: Z3/νZ **cannot take an FP objective**, so for optimization binary-search feasibility (or use OptiMathSAT). This is a scratch Python effort (use the scratchpad dir), NOT in-repo Java: `core/` is MC-free Java 8.

**Staged plan (fail fast).**
1. **Size probe (double-only, NOT byte-exact yet):** encode the window with the angle as a 16-bit bucket + the sine TABLE as constants, but velocity/position in reals/Float64 only. Just to see if Z3/Bitwuzla handles the SIZE (~12 ticks ≈ 50–100 fp ops + the gate ite + the LUT select). If even this times out at 12 ticks, the byte-exact version (strictly harder) won't work; stop here and go to the `T#` B&B path.
2. **Add byte-exact float semantics:** mirror `ExactJumpModel.stepRange` op-by-op; that method is THE reference for exact types/order (read it first). Legacy 1.8.9 uses **float32** for the sine table values, the input scaling (`strafe*=fm; forward*=fm`), the jump impulse, and the trig; then **widens to double** for the velocity adds (`vx += (double)(strafe*cosD − forward*sinD)`), the position adds, and friction (`vx * (double)f4`). The gate compares `|motion| < 0.005` in **double**, **per-axis** (1.8.9; `perAxisInertia=true`, threshold `0.005`). So the SMT model mixes Float32 `(_ FloatingPoint 8 24)` and Float64 with `fp.to_fp` conversions, all round-to-nearest-even. A double-only model is NOT byte-exact: matching ExactJumpModel's per-op float/double types + order IS the whole point.

**Encoding choices.**
- **Decision variable = the 16-bit bucket `k_t`** (a `BitVec 16`), NOT a continuous angle. Then `sin_t = TABLE[k_t]` and `cos_t = TABLE[(k_t + 16384) & 65535]` are exact float32 constants selected from the table (nested `ite` / array-select over a PRUNED candidate bucket set per tick: the physically-reachable band, ideally ≤ a few hundred). This sidesteps the fragile float32 `(int)(rad*10430.378f)` index computation; the bucket IS the decision.
- **Gate:** `ite(fp.lt(fp.abs(vx), 0.005), (fp 0), vx)` in Float64, per axis, at the top of each tick.
- **Per tick, mirror ExactJumpModel:** gate → (on jump ticks, jump impulse in float32: `vx -= sinStep(k)*0.2f`, `vz += cosStep(k)*0.2f`) → input accel in float32 widened to double → `pos += vel` (double) → `vel *= f4` (double).
- **Constraints:** the window's sliced linear position constraints (double consts) as `fp.leq`/`fp.geq` at the constrained window-local ticks.
- **Objective (later):** X@last, via binary-search over feasibility.

**Extraction harness (reconstruct the deleted `NixWolframDump`).** A gated `core/src/test` harness (`assumeTrue` on an env var, like `NixFullDiag`; NO code comments per repo rule) that, for a chosen window `[a,c)` of `nix-full-works.json` at the proven start `(11.3, 1.84078)`, dumps: per-tick `f4`, the scaled `fF`/`sF` (float32), and contact/jump/sprint flags; the window ENTRY state (pos+vel, double) obtained by forwarding the proven `debug` yaws to tick `a`; the sliced constraints (window-local ticks); and a note that TABLE[i] = `(float)Math.sin(i*2π/65536)`. Emit as an SMT-LIB2 `.smt2` or a Python-z3 script. **Model references:** `ExactJumpModel.stepRange` (exact float/double types), `JumpLinearModel.precompute` (how `fF`/`sF`/`f4` are derived), `McSineTable` (the table + index math). NB: the prior-session Wolfram port got the DOUBLE model faithful to 8.4e-5 by using `mMag`/`baseArg` from `JumpLinearModel`; for SMT-FP you need the exact float32 per-op types, so re-derive from `ExactJumpModel`, not the double linear model.

**Window choice.** Start with the smallest meaningful piece to find the tractability frontier: the **final jump `[42,54)` = 12 ticks** (§3b: the tight landing piece), seeded with the proven tick-42 seam `pos=(9.7000,6.5047) vel=(-0.16305,0.19379)`. Ask "can it land the pad byte-exactly?"; should be `sat` (the proven run does; a sanity check that also times the solve). Then grow toward the 24-tick tail `[30,54)`. The deliverable is the largest window that decides in ~minutes.

**Success criteria / what each outcome means.**
- `sat` + byte-exact witness fast at ~12 ticks → promising; grow the window.
- decides up to ~15–24 ticks in reasonable time → **windowed-SMT-FP + stitching is viable** (§6c track B) → that becomes the certified byte-exact path (compositional certificate: each window certified against a carried entry-state, exit ⊆ next entry).
- timeout at ~12 ticks → the wall bites early → go to the validated-bound `T#` B&B upgrade to `BoundPrunedRecovery` (§6c), which trades a tighter proof for scalability.

**Honest caveat.** Even a `sat` window is only a per-window certificate; the full 54-tick route needs stitched windows, and large windows may not decide (NP-hard, bit-blasting). This experiment measures the frontier; it does not by itself certify the whole route. It also does not land Nix; that is track (A), §4.2, orthogonal.

## 8. SMT-FP window feasibility experiment: RESULT (2026-07-06)

**Bottom line (CORRECTED, read §8.6 first): Bitwuzla DECIDES byte-exact windows where Z3 cannot, BUT only inside a narrow tube around the already-known answer. It is a per-window VERIFIER, not a route searcher.** A byte-exact SMT-FP encoding of the whole final-jump window `[42,54)` (12 ticks, tight landing pad) DECIDES: `sat` in ~35-41 s (41 s under the real `X@54 >= 8.70000` landing floor; see §8.3 caveat), and the paired infeasible query `unsat` in ~0.5 s. Z3's FP theory dies at 4 ticks; Bitwuzla clears 24 ticks. **But every one of those solves used a ±0.02 deg band around the proven yaws (re-derivation, not discovery); a genuine full-yaw-range search is undecidable even at 3 ticks (§8.6).** So this is NOT a certified route solver and track B is closed as a search path; the durable product is a byte-exact per-window verifier of a candidate produced elsewhere.

### 8.1 What was built (all in the scratchpad; harness in-repo)

- **Extraction harness** `core/src/test/.../anglesolver/NixSmtDump.java` (gated on `PKC_SMT_FILE`/`PKC_SMT_WINDOW`/`PKC_SMT_OUT`, like `NixFullDiag`; no comments; inert in the normal gate, `:core:test` stays green). For window `[a,c)` of `nix-full-works.json` at the proven start it dumps `window.json` (entry pos+vel, per-tick float32 constants `sF/fF/f4`, flags, proven game-facing buckets + proven trajectory, sliced window-local constraints, all as raw IEEE bits + decimal) and `sine_table_f32.bin` (Java's exact 65536 float32 table). Reusable for any window/route.
- **numpy float32 reference simulator** (`common.py`, `validate.py`): a bit-exact mirror of `ExactJumpModel`'s legacy 1.8.9 chain (X/Z only; Y is decoupled). Validated: for `[42,54)` and `[30,54)` it reproduces Java's proven trajectory with `maxAbsDiff = 0.0` (byte-identical) and its float32 index casts match Java's proven buckets on all ticks (incl. the tick-5 cos off-by-one). This is the trust anchor: every SMT witness is replayed through it to confirm it truly lands.
- **Z3 encoder** `smt.py` (modes: `real` = stage-1 relaxation, `fp` = byte-exact). **Bitwuzla encoder** `bzsolve.py` (byte-exact QF_FP, BitVec candidate indices). **Certified-optimum driver** `opt.py` (binary search on the objective via repeated feasibility).

### 8.2 Encoding (the design that made it byte-exact AND tractable)

- **Decision = per-tick game-facing bucket.** Only `(sinD, cosD)` (and on jump ticks the jump-cast pair) are yaw-dependent; `sF, fF, f4` and all flags are yaw-independent float32 constants dumped from Java. This sidesteps the entire `toGameFacings`/absolute-yaw chain: the SMT chooses, per tick, one candidate facing from a pruned band around the proven yaw. **Critical correctness point:** the doc's earlier shortcut `cos = TABLE[(k+16384)&M]` is NOT byte-exact (tick 5: sinB 63473, true cosB 14320, but `(63473+16384)&65535 = 14321`, off by one). So each candidate carries the EXACT `(msinB,mcosB,jsinB,jcosB)` tuple from the real float32 casts (done in Java/numpy, never in-solver).
- **Precomputed per-candidate velocity deltas.** The velocity a tick adds is a fixed float32 per candidate; precomputing it (jump impulse `float32(jsinD*0.2f)`, accel `float32(sF*cosD - fF*sinD)`) means the SMT only ever does `symbolic +/- constant` and `symbolic x constant(f4)` FP ops, never symbolic-times-symbolic. This was decisive: the first Z3 real encoding (symbolic products through If-chains) timed out at 60 s on 19 cand/tick; with precomputed deltas it dropped to 9.8 s.
- **Per-op FP semantics** mirror `ExactJumpModel.stepRange`: gate (`fp.lt(fp.abs(v),0.005)` in Float64, per-axis), jump impulse (float32 mul, widen to Float64, add), accel (float32 products, widen, add), move (Float64 add), friction (Float64 mul by widened float32 `f4`). All RNE. Float32 = `(8,24)`, Float64 = `(11,53)`. Constants built from raw IEEE bits (`fp.to_fp` from BV), so bit-exact.

### 8.3 Measured frontier

Single-threaded solver, `nix-full-works.json`, proven seams. "band" = candidates per tick from `proven_gf +/- delta` at bucket resolution (proven bucket always included). Witnesses all replayed through the numpy sim = genuinely land.

**Z3 (default FP tactic), falls over almost immediately:**

| window | ticks | binding constraint | result |
| --- | --- | --- | --- |
| `[42,54)` first 2 | 2 | none | sat 0.00 s |
| `[42,54)` first 3 | 3 | none | sat 0.00 s |
| `[42,54)` first 4 | 4 | `Z@46<=7.7` (first real wall) | **unknown, >90 s** |
| `[42,54)` 6/8/10/12 | 6-12 | (same) | **unknown, >90 s** |

Z3 cliff = the first binding constraint at 4 ticks. (Z3 stage-1 `real` relaxation does solve the full 12-tick at 19 cand/tick in 9.8 s, but that is LRA over exact rationals, NOT the byte-exact float model, so it is only a size sanity check.)

**Bitwuzla (byte-exact QF_FP), clears the whole tail:**

| window | ticks | tight pad? | delta | cand/tick | solve |
| --- | --- | --- | --- | --- | --- |
| `[42,54)` mt6 | 6 | no | 0.02 | ~7 | 2.2 s |
| `[42,54)` mt8 | 8 | no | 0.02 | ~7 | 2.2 s |
| `[42,54)` mt10 | 10 | no | 0.02 | ~7 | 2.0 s |
| `[42,54)` full | 12 | **yes** | 0.02 | ~7 | **34.9 s** |
| `[42,54)` full | 12 | yes | 0.10 | ~37 | 65.1 s |
| `[42,54)` full | 12 | yes | 0.50 | ~183 | 40.5 s |
| `[42,54)` full, `X@54>=11` | 12 | yes | 0.02 | ~7 | **unsat 0.5 s** |
| `[30,46)` (lead-in) | 16 | no | 0.02 | ~7 | 2.3 s |
| `[30,50)` | 20 | no | 0.02 | ~7 | 63.9 s |
| `[30,54)` full 2-jump tail | 24 | **yes** | 0.02 | ~7 | **sat ~603 s** (under core contention; uncontended faster) |

The 24-tick row is the capstone: the ENTIRE last-2-jump tail `[30,54)` that §3b identified as the BnB sweet spot decides byte-exactly (~10 min, and that run was sharing cores with another solve, so it is an upper bound). It exceeds §7's hoped-for ~12-tick target.

**Constraint caveat (important, corrected 2026-07-06).** The dumped `nix-full-works.json` carries `X@54 >= 8.6994`, but that is a RELAXATION the user set to view the trajectory; the REAL landing floor is `X@54 >= 8.70000` (plus `Z@54 in [9.3,10.3]`). Every headline time in the tables above was measured against the save's relaxed 8.6994, but the window stays feasible under the real 8.7 floor and the tighter floor barely costs anything: enforcing `X@54 >= 8.70000` on `[42,54)` is `sat` in **41 s** (delta 0.02) / **61 s** (delta 0.10) vs 34.9 s / 65.1 s relaxed, witness lands `X@54 = 8.70001, Z@54 = 9.4885`. The proven run lands `X@54 = 8.70002`, a margin of only ~2e-5 above the 8.7 floor: byte-level wall-hugging.

Certified optimum (feasibility probes of `X@54`, `[42,54)`, delta 0.10 band, real 8.7 floor): `X@54 >= 8.70002` (the proven value) is `sat`, `X@54 >= 8.70050` is **unsat**, so the certified byte-exact **max `X@54` is in [8.70002, 8.70050)**: the whole reachable landing-X range is a `~5e-4`-wide sliver `[8.70000, 8.70050)`, consistent with §3a's "late constraints hit wall-huggingly" (the route clears the pad's X floor by microns, and cannot be pushed materially deeper into the pad). This exercises the §6b "certified bound" pipeline on the REAL byte-exact float model, not a smooth relaxation. (An earlier draft of this line used the save's relaxed 8.6994 floor plus a coarse `eps = 0.02` search, giving a misleading bracket `[8.695, 8.71]` and a bogus "lands at 8.69988" witness that is actually a 1.2e-4 MISS under the real 8.7 floor.)

### 8.4 What the numbers mean

1. **Bitwuzla, not Z3, is the tool.** Z3's FP theory cannot decide even a 4-tick byte-exact window with one landing wall; Bitwuzla decides the full 12-tick tight-pad window in ~35 s and the 24-tick tail in ~10 min. The doc's "escalate to Bitwuzla" was right and load-bearing.
2. **Difficulty is driven by constraint TIGHTNESS, not chain length.** 16-tick lead-in with a loose wall = 2.3 s; 12-tick with the byte-exact landing pad = 35 s. The tight box (hitting the real landing floor `X@54 >= 8.70000`, `Z@54 in [9.3,10.3]` to the ULP; 8.6994 in the save is a relaxation) is the cost, and it lives only in the LAST window. This is exactly the structure that makes stitching favorable: lead-in windows are cheap, only the terminal window pays.
3. **The ~35 s is not a seeding artifact.** Widening the per-tick band 26x (84 to 2196 candidates, a +/-0.5 deg cone = 183^12 ~ 10^27 combinations) barely moved solve time (34.9 to 40.5 s). The bit-blasted FP recurrence dominates; the combinatorial candidate count is nearly free. So the certificate is over a real search space, not a hand-narrowed neighborhood of the answer. **[WRONG, SUPERSEDED by §8.6: this is an ARTIFACT. The proven solution sits inside every band, so Bitwuzla finds it fast regardless of width because the answer is handed to it. Band width is NOT nearly free for a genuine search: a 3-tick FULL-range solve is undecidable in 5 min. These bands ARE a hand-narrowed tube around the known answer.]**
4. **Both sat and unsat decide.** The certified property needs feasibility DECISION (both directions). Infeasible queries returned `unsat` fast (0.5 s), and the binary search terminated with sat below / unsat above a byte-exact threshold, so the window optimum is genuinely certified, not merely found.

**Soundness scope (honest caveat).** A `sat` witness is GLOBALLY sound: it is a byte-exact facing sequence that provably lands (re-verified through the numpy sim). An `unsat` (and hence the certified optimum) is BAND-LOCAL: it certifies infeasibility only over the searched per-tick facing band, not the full 65536-bucket cone. Since solve time is nearly flat in band width (point 3), widening toward a full-cone certificate is cheap in principle, but only +/-0.5 deg/tick was measured here. **[WRONG, SUPERSEDED by §8.6: full-cone is NOT cheap: a 3-tick full-yaw-range solve is undecidable in 5 min. The flatness was an artifact of the proven solution being in every tested band.]** Two further modeling notes: the SMT treats each tick's game-facing as a free choice (the <=180 deg/tick yaw-rate coupling in `toGameFacings` is not modeled; non-binding for landing windows where facings do not swing that far), and Y is dropped (decoupled from X/Z).

### 8.5 Verdict and next step

**SUPERSEDED by §8.6 (read that first); this verdict was written before the full-range test and is wrong on the key point.** The experiment decides byte-exactly up to ~12-24 ticks in seconds-to-~10 min, BUT only within a ±0.02-0.5 deg tube around the proven yaws (re-derivation), so it does NOT establish a *search* path. Windowed-SMT-FP is a per-window VERIFIER, not a route solver; track B is NOT a viable search path. The compositional stitch/`T#` idea only ever made sense as verifier-composition (bound a candidate that came from another solver), never as a way to FIND a route from t1.

This does NOT land Nix (track A, §4.2, is the way to that). What §7 actually established: the tool is Bitwuzla (Z3 is useless for FP here); the byte-exact encoding re-derives a seeded window in ~35 s (12t) to ~10 min (24t); and (§8.6) it cannot SEARCH, so it is a verifier, not the certified route solver this section originally claimed.

Repro (scratch Python, not in-repo; Java harness in-repo): `PKC_SMT_FILE=<nix-full-works> PKC_SMT_WINDOW=42,54 PKC_SMT_OUT=<dir> ./gradlew :core:test --tests '*NixSmtDump'` to dump; then `python bzsolve.py --delta 0.02` (Bitwuzla) / `python smt.py --mode fp` (Z3) / `python validate.py` (byte-exact check) / `python opt.py` (certified optimum). Needs `pip install z3-solver bitwuzla numpy`. Scratch scripts live under the session scratchpad `smt/`.

### 8.6 The decisive limit (2026-07-06): it VERIFIES, it does not SEARCH

**Read this before trusting §8.3.** Every solve-time in §8.3 used a NARROW BAND centered on the proven yaws (±0.02 to ±0.5 deg/tick): a thin tube around the ALREADY-KNOWN answer, plus the entry state seeded from the proven run. So those solves confirm "a byte-exact solution exists within ±delta of the route we already have" - re-derivation, NOT discovery. Two facts expose this:

- The "band width is nearly free" result (§8.4 point 3) is an ARTIFACT: the proven solution sits inside every band, so Bitwuzla finds it fast no matter how wide the band, because the answer is handed to it.
- **Decisive test (3 ticks, FULL yaw range).** Window `[51,54)` (3 airborne ticks, real pad landing), each tick's facing a FREE 16-bit value over the whole 65536-facing range (compact shared-array sine LUT; array build 0.3 s). Result: **`unknown` after 300 s** (Bitwuzla 5-min cap). The SAME 3 ticks with the ±0.02 deg tube solve in **~0.0 s**. Going from re-derivation to genuine search, at the SMALLEST meaningful size, turns an instant solve into undecidable-in-5-min - and it only worsens with tick count. Script: `frsolve.py`.

**Conclusion.** SMT-FP here is a byte-exact per-window VERIFIER/certifier (given a candidate or a tight neighborhood, prove feasibility/optimality to the ULP). It is NOT a route searcher: it cannot explore the real facing space even at 3 ticks. The §8.3 scaling curve (24t->54t "decides") therefore measures only how the byte-exact ENCODING re-derives the seeded answer at length, not any search capability - and it does not contradict §6b's "monolithic cold global over the 65536^n LUT space is combinatorially hopeless." Its one durable product is the per-window verifier (verify/bound a candidate that came from another solver). Actually FINDING/landing a Nix route is **track A (§4.2)** - the numeric BnB + seam search, built to search. Do NOT resurrect "solve it from t1 with an SMT/global solver" without a decomposition that only ever hands the solver tight neighborhoods.

## 9. Cross-route generality probe (2026-07-06): the §3b shape does NOT generalise. STOP.

**Verdict: do NOT build the §4.2 coupled-seam contraption.** The §4.2 gate ("run the cross-route probe FIRST; if each route is its own puzzle rather than one shape, STOP") was run on the four sibling saves plus nix. The §3b decomposition shape (monolithic-fails -> a unique 2-jump-tail BnB sweet spot -> a coherent seam band) is **not a shared shape**, and the one route it was derived from (nix) is not even blocked by decomposition. Harness: `core/src/test/.../anglesolver/NixTailProbe.java` (env-gated `PKC_PROBE_FILES` semicolon-list; per route it computes jump boundaries, extracts the proven seam by forwarding the `debug` yaws, forwards the proven tail yaws to prove a feasible tail EXISTS, then BnBs each tail `[bounds[jumps-k],n)` seeded at the proven seam, and sweeps a seam-velocity band). Copies `LongRunSolver`'s private `sliceScenario`/`sliceConstraints`/`jumpBoundaries`. Gate stays green (inert without the env var).

**The measured table** (`BnB` = `BoundPrunedRecovery.solve` at `feasTol=0`, tail budget 20-30 s; `provTail viol` = proven tail yaws forwarded through the slice, proves a byte-exact feasible tail exists):

| route | ticks | jumps | monolithic BnB (whole route) | largest byte-exact BnB-solvable tail | seam band |
| --- | --- | --- | --- | --- | --- |
| j425_1jmm | 24 | 2 | **FEASIBLE 4.3 s** | whole route (2) | clean triangle, widens with vz |
| j422_2jmm | 36 | 3 | **FEASIBLE 24 s** | whole route (3) | clean, widens with vz |
| j345_3jmm | 49 | 4 | **FEASIBLE 24 s** | whole route (4) | clean, widens with vz |
| j347 | 52 | 4 | NULL | **3-jump** tail (k1,k2,k3 feasible; k4/mono NULL) | FRAGMENTED (not monotone in vz) |
| nix-full-works | 54 | 4 | NULL | **NONE** (every tail NULL at feasTol=0) | clean, but at the 1-jump seam only |

For all five, `provTail viol = 0` and `objX = provenObjX` on every tail (byte-exact seam extraction confirmed; proven-run `maxPosDiff` 0 for the four siblings, 8.9e-16 for nix).

**Three facts kill the "one shape" premise:**

1. **The majority of the class solves MONOLITHICALLY with the existing general BnB.** j425 / j422 / j345 (2, 3, 4 jumps) all return a byte-exact `viol=0` full-route solution; the whole route fits under `BoundPrunedRecovery`'s `MAX_PATTERNS=8` cap. No seam, no lead-in coupling, no translation. The coupled-seam machinery is **unnecessary** for 3 of 5.

2. **The two that don't solve monolithically disagree on structure.** j347's largest byte-exact tail is **3 jumps** (a clean monotone frontier), not 2, and its seam band is **fragmented** (feasibility is not the clean "higher vz is strictly more permissive" region §3b(3) reported for nix). So even the tail size is route-specific (whole / 3 / none), confirming §4.2's own worry that "2-jump" is a Nix literal. The uniform *algorithm* ("largest BnB-solvable tail, derived per route") survives; the specific *shape* does not.

3. **The §3b "2-jump tail is the BnB sweet spot" was an artifact of the RELAXED floor, and nix's real blocker is NOT decomposition.** §3b measured the 2-jump tail feasible at `objX 8.69954` under the then-relaxed `X@54 >= 8.6994`. The file now carries the real floor `X@54 >= 8.70000` (already re-tightened; the §0/§4.2 caveat is stale), and `8.69954 < 8.70000`. Under the real floor **every** nix tail is BnB-NULL at `feasTol=0`, even though a byte-exact feasible tail provably exists (proven yaws: `viol=0`, `objX=8.70002`). A `feasTol` sweep on the 2-jump tail `[30,54)` pins the gap exactly:

   | feasTol | 0 | 1e-4 | 3e-4 | 5e-4 | 1e-3 | 2e-3 |
   | --- | --- | --- | --- | --- | --- | --- |
   | result | NULL | NULL | objX 8.70053 viol 2.9e-4 | 8.70123 viol 4.8e-4 | 8.70292 viol 9.8e-4 | 8.70644 viol 2.0e-3 |

   So nix's blocker is a sub-1e-3 **byte-level wall-hug** (BnB's numeric restore + top-8 patterns get within ~3e-4 but cannot reach `viol=0`), the same wall §8.3's certified sliver `[8.70002, 8.70050)` and §8.6 already identified. Decomposition does not touch this; the coupled-seam solver would produce the same near-miss tail. The missing capability is a byte-exact closer for the last ~3e-4 (relax-then-ILS per §2/step-5, or the §8 SMT-FP per-window verifier as a polish), **not** a lead-in/seam search.

**What is actually general (small, not a contraption), if the user wants to pursue anything here:**
- A "monolithic-first, else largest-BnB-tail fallback" wrapper (derive the tail size large->small, zero Nix literals). This is essentially what `LongRunSolver`'s window ladder already does; it would only add value on j347, and it still would NOT land nix.
- For nix specifically: test whether `IlsPolish` can close the ~3e-4 `feasTol=3e-4` BnB tail to `viol=0` (unproven), or wire the §8 Bitwuzla per-window verifier as a byte-exact polish on a near-feasible BnB tail. Both are route-agnostic precision tools, orthogonal to the §4.2 decomposition.

**Bottom line for the next session:** the §4.2 build steps (coupled seam solve, velocity-targeted lead-in, outer seam-velocity search) are **not justified by the class** and would not solve the one route they were designed for. Step 1 (§4.1, the `recoverStart(seedYaws)` translate) stands as a general, shipped improvement. Everything downstream of the probe is superseded by this section. New in-repo: `NixTailProbe.java` (env-gated, gate green).

### 9a. Live full-engine confirmation at max effort (2026-07-06, `EngineFileScreen`)

The §9 probe measured `BoundPrunedRecovery` in isolation. To answer "does the real solver at max effort solve these" (not just one component), the FULL engine (`AngleSolverEngine.solve()`, CMA-ES + receding-horizon windowing + ILS + free-start) was run live via `EngineFileScreen` (`PKC_SOLVE_FILE` / `PKC_SOLVE_EFFORT` / `PKC_OPTIMIZE_SECONDS` (new env override) / `PKC_SOLVE_TIMEOUT_MS`).

- **j347 is SOLVED live: `54/54` feasible in 127 s** (`receding horizon -> CMA-ES -> ILS`, feasible at 2.5 s). The §9 "j347 = hard, 3-jump tail" line was an ISOLATED-BnB artifact: monolithic `BoundPrunedRecovery` returns NULL, but the engine does not solve j347 monolithically, it windows it. **Correction: j347 is a fully solved route, not a hard case.** Likewise j425/j422/j345 carry `success=true` results (j425 10/10, j422 13/13, j345 14/14) via the same windowed path. So **4 of 5 class routes are fully solved by the EXISTING engine**; only nix-from-t1 is open.
- **nix-from-t1 is unsolved at EVERY effort tier, and the engine self-terminates before its budget:** CUSTOM (monolithic, `useWindow=False`) converges at **135 s -> shipped 10/15 infeasible, obj 8.6985**; THOROUGH with `optimizeSeconds=600` converges at **254 s -> shipped 11/15 infeasible, obj 8.7885**. Both `liveFeasibleSeen=false`; neither used its full budget. Step 1's `recoverStart` translate reaches viol `4.679e-3` in both (near-feasible but not shipped, since the engine ships best-objective not lowest-violation). So nix is not time-limited; the monolithic basin is unreachable by CMA/BnB (proven pattern outside the top-8, §3b.1) and the last ~3e-4 is a byte-level wall-hug (§9 feasTol sweep, §8.3 sliver). **More effort does not help.**

**Net (unchanged, now live-verified):** the class is already solved by the shipped engine except nix-from-t1; the §4.2 coupled-seam solver is unnecessary for the solved routes and would not land nix (seeded even at the exact proven seam, BnB cannot produce a byte-exact feasible tail under the real floor). The one lever that could land nix is the byte-exact CLOSER the user performs BY HAND (§2: solve from ~t42/t44 with the landing relaxed ~1e-3, then ILS to viol 0). Open question worth exactly one experiment: can `IlsPolish` close the `feasTol=3e-4` BnB tail (viol 2.9e-4) to viol 0 automatically? That is route-agnostic precision, orthogonal to the abandoned seam decomposition. New env override: `EngineFileScreen` now reads `PKC_OPTIMIZE_SECONDS`.

### 9b. The real reason the solver misses nix: wrong SEARCH COORDINATES for the setup (2026-07-06)

Prompted by the user's observation that the nix setup holds ONE facing and only strafes. Decoding the proven `nix-full-works` run tick-by-tick (see the input dump) settles it:

- **The nix setup (t1-28) holds yaw = 45 (one tick at 0, the reset) and toggles the KEYS** (W+A build, S+D cancel/reverse, W-only reset), keeping **vx = 0.00000 exactly**. The solvable siblings do the opposite: **j345/j422/j347 VARY the yaw (11-12 distinct facings in 14 ticks) with W+A held (never S)**. The user confirms t1-28 is done by a human.
- **Why W+A / S+D at 45 (physics, doc 01 `updateMotionXZ`):** you strafe for the ~2% speed (accel scaled 1.0 vs 0.98 for W alone); at 45 the diagonal strafe cancels the diagonal facing so `accelX = strafe*cosF - forward*sinF = 0` and accel is pure +/-Z. W+A = +Z, S+D = -Z (exact opposites on one axis). Fixed facing keeps it byte-exact (same integer sine bucket) and axis-locked; a sprint-jump boost (0.2 along the FACING) is off-axis at 45, so the setup's jump ticks are NON-sprinting (proven t5/t18). Recorded in `CONTEXT.md` -> "Axis-locked momentum".
- **The solver searches the wrong variable.** In FORCE_45 `buildPhys` hardcodes forward = +0.98 (W) and one strafe sign and searches only the CONTINUOUS yaw. It can express the yaw-varying siblings (so they solve) but not nix's key-toggle setup: producing -Z needs an ~180 deg facing flip, which leaks off-axis unless exactly 225 and lands on different LUT buckets, desyncing the byte-exact cancellation (§6). So the momentum phase is unsearchable in facing-space; it is a discrete key-pattern problem at a fixed facing.

**Prototype confirms it (`core/src/test/.../anglesolver/NixSetupSearch.java`, env `PKC_SETUP_FILE`, gate-inert).** A cold byte-exact reachability search over the discrete alphabet {facing in {0,45}} x {W/S, A/D} x {sprint} for `[0,30)`, pruning any state with |vx| > 0.02, reaches the seam in ~12 s: `vel=(0.00000, 0.20572) pos=(11.3000, 3.1950)` vs proven `vel=(0.00288,0.20688) pos=(11.3023,3.1942)` -- band velocity AND reconcilable position together (the §3b.4 blocker), via a DIFFERENT key pattern than the human's. Mechanism check: W+A then S+D at 45 non-sprint gives `max|vx| = 0.000e+00` (axis lock exact in the model).

**BREAKTHROUGH (2026-07-06, docs §9f, position-free framing from the user): a +0.005 vz OVERSHOOT at t30 makes the whole tail [30,54) close BYTE-EXACT.** The user reframed the whole thing as position-free (the branch's premise): the route is translation-invariant, so the seam is a VELOCITY, and the position falls out of the free-start translation onto the binding constraint. Consequences, all measured: (1) the "razor" I feared was a fixed-position artifact of `NixSeamMap` (perturbing seam-X without letting the route translate); position-free, `recoverStart` places X@42=9.7 by construction. (2) `NixMinVz`/`NixMargin`: the proven human run lands maximally tight (pad X@54 by +2.1e-5, wall-hugs X@47/Z@46/Z@53/X@42 to ~1e-7) and sits only +1.7e-5 above the position-specific vz floor -- it is near-minimal-momentum. (3) `NixChainVz` (decisive): from the proven t30 seam the tail is stuck at 9.9e-5, but boosting t30 vz by just +0.005 (0.20688 -> 0.21188) makes `SolveCore` close the ENTIRE [30,54) tail to viol=0, and every larger boost too. The minimal-momentum proven seam is razor-tight (no solver slack); a hair of overshoot gives room and the tail closes clean. The chained t42 vz lands BELOW the fixed-position 0.193773 floor because real landability is the full 4-D state manifold, not a single vz. **So the tail is SOLVED byte-exact from a slightly-boosted t30 seam; the setup only needs to build vz a touch above proven (the easy one-sided direction); position falls out via translation.** Remaining: assemble setup (key search) + tail + one translation over all 23 constraints. New harnesses: `NixMinVz`, `NixMargin`, `NixChainVz` (all env-gated). [This supersedes the §9d "coupled seam is a razor" pessimism -- position-free dissolves it.]

### 9g. Full cold assembly (`NixSolve`): every piece works; nix's extreme tightness is the last blocker

`NixSolve.java` (env `PKC_SOLVE2_FILE`, gate-inert) assembles the whole thing cold from t1: corridor-pruned key-search setup [0,30) -> `SolveCore` tail [30,54) -> one footprint translation checked over all 23 constraints via exact translation-invariance (interval intersection, no re-forward). Findings:

1. **Cold setup -> byte-exact tail chains for real.** A cold key-search setup reaching a boosted seam feeds `SolveCore`, which closes [30,54) to viol=0. Confirmed end-to-end (no proven data).
2. **The setup must respect its OWN corridor, not just the seam** (else the setup's Z-shape and the tail want opposite translations). Added a corridor prune to the key search (drop states leaving `Z@5,Z@17 in [1.1375,7.3]` / the X-corridor). This is `violatesSetupCons`.
3. **Decisive negative for nix specifically:** with the corridor prune, corridor-compliant setups near the reconcilable pz (~3.19) **cap at vz ~= 0.207 = the proven minimal-momentum razor seam**. High-vz corridor-compliant seams exist only at high pz (0.257 @ pz 7.16), where the tail fails (viol 3.7e-2). To get the +0.005 vz slack the tail needs at pz~3.19 you must dip Z below the corridor floor (forbidden) or drift pz high (tail fails). So the decomposition can REACH the razor seam but not thread it: from the exact minimal seam, cold `SolveCore` can't find the byte-exact tail (stuck 9.9e-5) though it provably exists (proven yaws).

**Conclusion of the whole position-free push:** the general method is validated (setup key search, byte-exact tail, position-free translation, corridor-aware setup) and would solve *less-tight* momentum routes cold. Nix is a True Nix: maximally tight, ~zero slack everywhere, so the ONLY remaining piece is a **byte-exact tail closer that finds viol=0 from the razor-minimal seam** -- the relax-by-1e-3-then-ILS-back continuation the human does by hand (§2). Cold CMA and `maxViolation` coordinate descent can't (thin basin / minimax, §9c); a directed homotopy (relax the landing constraint, solve feasibly, tighten back to byte-exact) is the genuine remaining build. New harness: `NixSolve.java`. Gate re-verified green with all 9 new env-gated harnesses present.

### 9h. The vx/vz TRADE cracks the tail needle (user idea #2); remaining blocker is joint 4-D seam targeting

The §9g "razor tail needle" is dissolved by the user's idea #2: **carry a little vx into the seam.** `NixVxTrade.java` (env `PKC_TRADE_FILE`): from the proven t30 position, the tail is stuck at 6.6e-5 with vx=0, but with just **vx = -0.027 (a slight left curve) it closes BYTE-EXACT at the same vz**, and with vx = -0.097 it closes at vz = 0.195 (below proven). The vx is an extra DOF that lets `SolveCore` thread the byte-exact landing where the vz-only seam was an isolated point. This also relaxes the §9c Z-rise bind (less vz = less rise = more corridor margin), so idea #2 beats idea #1 (more forward vz is self-defeating: more vz = more Z-rise = ceiling violation; the total Z-rise t17->t46 must be <= 6.5625, proven uses 6.50).

`NixSolve` was extended (backward sprint-jump actions for idea #1, curving facings W@20/70/110 for idea #2, 2-D velocity reachability with packed-`long` action encoding to fit memory). Result: it now reaches curved corridor-compliant seams cold (e.g. `vel=(-0.031,0.198)`), but **the curve couples position and velocity**: building vx drifts the seam off the proven position (to X~9.83, Z~4.18), and the tail (which closes from the *proven* position + vx) fails from the drifted position; translating the seam back breaks the setup corridor floor. No corridor-compliant curved seam exists near the proven position.

**Net (final state of this session):** every piece and both physical insights are validated -- position-free seam (velocity, not position), discrete key-pattern setup, byte-exact tail via `SolveCore`, the vx/vz trade that un-sticks the needle, corridor-aware + 2-D curving setup search. The remaining blocker is a **joint 4-D seam-targeting**: the tail closes only from a specific (position, velocity) 4-tuple (proven position + a little vx), and reaching that exact 4-tuple with the curve is coupled (velocity drifts position). The last build is a seam-targeting setup search (hit the 4-tuple) or a joint setup+tail+translation optimizer -- the interleaving the human does by hand. New harnesses: `NixVxTrade.java`, extended `NixSolve.java`.

**Implication.** The correct decomposition is NOT the §4.2 coupled-seam contraption. It is: **(lead-in) a discrete key-pattern momentum-setup search at a fixed facing** (the general neo mechanism, validated cold here) **-> (tail) the existing yaw-search/BnB + a byte-exact wall-hug closer** (relax-then-ILS, still unbuilt). This validates the LEAD-IN half only; nix is not yet landed end-to-end (the tail closer is the remaining piece), and the win is specific to fixed-facing key-toggle routes (the yaw-varying ones already solve). New harness `NixSetupSearch.java`; `CONTEXT.md` gained the "Axis-locked momentum" entry.

### 9c. The tail closer already exists: SolveCore closes the 12-tick landing window cold (2026-07-06)

`NixTailClose.java` (env `PKC_CLOSE_FILE` / `PKC_CLOSE_SEAMS`, gate-inert) tested how to close the byte-exact tail. Two findings:

- **A naive `maxViolation` coordinate descent FAILS** (stalls at 2.7e-4 in 1 ms): `maxViolation` is a MINIMAX, so relieving the binding wall pushes another wall up and no single-coordinate move lowers the max. This is why the continuous SLP/dual floor at ~3e-4 and why a smooth-penalty population method is needed, not coordinate descent.
- **`SolveCore` (byte-exact CMA multistart + feasibility rescue) closes the FINAL 12-tick window `[42,54)` COLD to viol=0** (objX 8.70003 vs proven 8.70002) in **~4 s**, at custom budget (256/100k), seeded at the proven t42 seam. The longer windows do not fully close: `[30,54)` (24t) reaches 4.1e-5, `[28,54)` (26t) reaches 1.1e-6 (warm from the BnB near-feasible seed). More coupled walls across more ticks defeat the byte-exact nail-down; the 12-tick window is the tractable unit.

**So the tail needs NO new closer.** The attack is: **split the tail at the t42 jump boundary. `[30,42)` is loose (reach a good t42 seam; airborne, no landing pad). `[42,54)` holds every tight wall (Z@46, X@47, Z@53, pad@54) and `SolveCore` closes it cold in seconds.** Isolated BnB returns NULL on `[42,54)` (the 1-jump tail), and the monolithic 54-tick `SolveCore` only reaches 14/17, but the ISOLATED 12-tick window solves clean: the win is windowing at the jump boundary, exactly the user's `[42,54)` hypothesis.

**Full nix pipeline, each piece now validated by existing machinery:** (1) lead-in `[0,30)` discrete key search -> t30 seam (§9b, cold); (2) `[30,42)` reach the t42 seam (loose, existing yaw-search); (3) `[42,54)` `SolveCore` cold -> byte-exact landing (§9c). **Remaining work = the CHAINING** (each window seeds the next) plus confirming `[42,54)` still closes from a real (non-proven) t42 seam within the band, and the global footprint translation. New harness `NixTailClose.java`.

## 10. SOLVED (2026-07-07): the momentum assembly + homotopy closer

Nix now solves cold from t1, twice over:
- **Harness proof** (`NixYawAssemble`, env `PKC_YA_FILE` / `PKC_YA_SEAM=28`): cold from `nix-full-fails.json`, no debug data, yaws-only over the rows' keys: full 54-tick route `viol = 0.0` over all 23 constraints, objX 8.7000001, start (11.3000000, 1.8407790), in **65 s**.
- **Live engine** (`EngineFileScreen`, CUSTOM as saved): `success=true met=15/15 obj=8.700000 solver="CMA-ES -> momentum assembly"`, live-feasible at 153 s, final at 225 s. Regression: `captures/nix-full-t1.json` + `problems/solve/nix-full-t1.expect.json`.

### 10.1 The two shipped pieces

**(1) `HomotopyCloser` (solver pkg): the byte-exact feasibility closer.** The pipeline had NO mechanism to convert a near-feasible incumbent into viol=0: `BucketAscentPolish` / `IlsPolish` / the CMA compass polish all hard-require an already-feasible seed, and CMA's quadratic penalty stalls ~1e-4 on razors (§9). The closer is a relax-tighten homotopy: relax EVERY inequality by eps (uniform rhs shift), solve the relaxed spec (real feasible volume, `SolveCore` finds it), then walk eps to 0 in halving rungs with adaptive refinement, each rung repaired by warm feasibility-only CMA-ES (sigmas 0.3/1/3, 60k evals); finish with a **sine-bucket max-slack descent** (block-1 + joint block-2 scans over the ~0.0055 deg buckets, minimizing the worst violation), the discrete last mile CMA cannot navigate. Measured on the tail from the proven seam: cold stall 9.9e-5 -> ladder reaches 1.8e-6 -> descent closes to 0.0 in 0.1 s. Both stages are load-bearing: descent alone from 9.9e-5 stalls at 6.0e-5 (the §9c minimax wall); ladder alone stalls at 1.8e-6.

**(2) `MomentumAssembly` (solver pkg) + engine hook.** Fires in `runJob` after the CMA race (before `freeStartImprove`, which may mutate the start) when the incumbent is infeasible and the model is ExactJumpModel; jumps < 3 returns instantly; budget cap 240 s (`MOMENTUM_ASSEMBLY_NANOS`), bounded further by the job deadline. Pipeline, all constants derived:
1. **Seam** = `bounds[jumps-2] - trim` for trim in {2, 0, 4} (jump boundaries from the scenario). The -2 "trim room" is essential: the human's last two setup ticks (t28 = 46.93, t29 = 38.02) are continuous trims that convert a sliver of momentum into +0.0023 seam-X; they belong to the TAIL solver, not the discrete setup.
2. **Axis-boost templates**: hold each diagonal {45,135,225,315} (axis-locked momentum: the rows' W+A / S+D key toggles then accelerate on one axis only); at each grounded sprint-jump tick inside the setup, pick the boost facing from {0,90,180,270} maximizing seam velocity toward the pad (pad direction derived from the objective-tick box constraints), then fan the boost facing up to +/-28 deg (the vx-trade knob, §9h; the per-axis inertia gate eats tilts under ~6 deg during the glide). Groups ranked by their axis-pure score; fan order (0 first) kept WITHIN a group (score-ranking the tilted variants first re-orders duds ahead of the winner and blows the budget; measured).
3. **Candidate filter + close**: per template, the setup's translation window over all pre-seam constraints must contain (0,0) (window = exact interval intersection); the tail `[seam,n)` is then closed PINNED at the seam exactly as produced (`HomotopyCloser`, eps0 1e-3, per-candidate cap 120 s). Solving the tail with a free StartBox instead measurably fails: in free mode `CmaesJumpHarness` skips its compass polish and `BucketAscentPolish` is inert (+INF on untranslated scores), and `bestTranslate`'s per-eval window-midpoint re-picking wanders the placement; the free ladder stalls ~1e-4 where the pinned ladder sails to 0.
4. **Result contract**: the full-route result = setup facings + the closer's ABSOLUTE wrapped yaws concatenated (NOT the tail's game facings: feeding gf back through the engine's float delta re-accumulation drifts ~1e-6 and breaks the 1e-7 wall-hugs; measured, cost one full stage budget). The concatenated replay is float-identical to the tail solve by construction; the stage re-verifies `toGameFacings(wrapAll(yaws))` against all constraints and only returns on viol <= FEAS_TOL (a full-spec bucket descent is the repair fallback).
5. **Frontier fallback** (only when every template fails): discrete yaw-menu search over the rows' keys ({diagonals, cardinals, 20/25/65/70}, per tick), corridor prune with 0.35 slack (translation-reconcilable), dedup on (vz, vx, height-above-own-dip, px) with score-descending expansion order, banded candidates. Covers momentum setups that are not pure hold+boost templates. Capped at 32 setup ticks (packed-long action encoding).

### 10.2 What made the difference (the final session's discoveries, in order)

1. **The closer gap was real and general**: relax-tighten + bucket descent closes what every shipped stage cannot.
2. **The full-spec ladder from the engine's own incumbent stalls at ~3e-4** (falsified as a fix): the last 3e-4 needs a coordinated setup reshape that local repairs cannot find in 54-D. Decomposition at the seam is required.
3. **The rows already carry the key pattern** (`defaultInputs=KEEP`): works and fails files have byte-identical keys, so the search is yaws-only and the result needs no key edits (engine-result compatible). The §9b "wrong coordinates" story applies to FORCE_45 saves, not this one.
4. **The proven setup is a TEMPLATE**: all-45 with the t18 sprint-jump at facing 0 (axis-pure 0.2 boost; at facing 45 the boost leaks vx -0.141 and loses ~0.019 vz). The proven run's t0-t27 are EXACTLY this template; z@18 dips to 0.954 (why the user removed the tick-18 floor).
5. **Seam-2 trim room**: from the template's t28 state (byte-equal to proven), the tail solver generates the t28/29 trims itself; at seam 30 it is ~1e-4 short on all five razor walls simultaneously (X@42 / Z@46 / X@47 / Z@53 / pad, opposing pairs, translation-neutral).
6. **Pinned-first closing** and **abs-yaw concatenation** (10.1 points 3-4).

### 10.3 Falsified this session (do not retry)

- Bucket descent alone from the 1e-4 cold stall (stalls 6e-5; minimax).
- The eps-ladder on the full 54-tick spec warm from the monolithic incumbent (stalls 3e-4; `NixFullClose`).
- Free-StartBox tail solving inside the ladder (stalls ~1e-4; polish stack inert in free mode).
- Pinning at `recoverStart` / min-violation placements ~2e-3 off the setup's natural placement (viol=0 unreachable there; the razor's shape freedom spans ~1e-4 of placement).
- Score-ordering the boost fan with the vx bonus (runs tilted duds first; group-rank by axis-pure score instead).
- Key-toggle setup search with hard corridor pruning at the fixed start + raw vz-max candidate ranking (`NixColdT1`): surfaces high-pz overshoot seams, drops the razor lineages at the frontier cap.

### 10.4 Files

- Production: `core/.../solver/HomotopyCloser.java`, `core/.../solver/MomentumAssembly.java`, `AngleSolverEngine` hook (after the race, before free start; `MOMENTUM_ASSEMBLY_NANOS`).
- Regression: `captures/nix-full-t1.json` + `problems/solve/nix-full-t1.expect.json` (FAST, shouldSolve, maxSolveMs 240000).
- Harnesses (env-gated, gate-inert): `NixHomotopy` (closer experiments incl. `PKC_HOMO_SKIPLADDER`), `NixFullClose` (full-spec ladder), `NixColdT1` (key-toggle assembly, superseded), `NixYawAssemble` (the winning yaws-only assembly, `PKC_YA_SEAM`).

### 10.5 What is next (the user's actual goal)

The user wants to BEAT the current best solve: the same route with 0.0625 blocks less momentum (corridor floor 1.2 instead of 1.1375), and possibly another 0.0625 beyond. Now that the class solves, the play is: author the tighter save and run the same engine; the assembly's template family and the closer are momentum-agnostic. If the tighter route is feasible at all, the §8 Bitwuzla per-window verifier can bound how much deeper the pad can be hit; if the solve comes back infeasible everywhere at, say, floor 1.2625, that is evidence (not proof) the momentum is truly required.

**First attempt (2026-07-07, same session): floor-1.2 variant NOT yet solved, but it is almost certainly solvable.** A scratchpad copy of nix-full-fails with the two Z floors (t5/t17) raised 1.1375 -> 1.2 ran CUSTOM 414 s -> 10/15 infeasible. Key facts: the winning template's dip bottoms at z@17 = 1.1999999893, i.e. **1.07e-8 below the new floor**, so today's solved route needs only a +1.1e-8 Z translate to satisfy floor 1.2, and its wall margins (~1e-5..1e-7) absorb that; a floor-1.2 solution therefore exists within ~1e-8 of the found route. The stage's window-nearest shift (added for exactly this: `MomentumAssembly.Result` carries the shifted start; the strict (0,0) filter would have rejected the template over the 1e-8) fired but the closer did not converge from the shifted seam within the 240 s stage budget: the razor ladder is path-sensitive, and a 1e-8 seam change can reroute it. Next lever, cheap first: warm the closer with the nix-full-t1 solved yaws (engine-internal, not user data), or raise the per-candidate/stage budget for CUSTOM, or add a final +translate probe that re-verifies the assembled route under micro-shifts sampled from the residual window. The floor-1.2625 question (start must rise to >= 1.9025, seam correspondingly higher vs the Z@46 ceiling) is genuinely open.

### 10.6 The never-built backward march (planned 2026-07-06; consolidated 2026-08-21 from nix-backward-march-handoff.md, now deleted)

Before the forward assembly above landed, a tick-by-tick backward march from the landing anchor was planned (2026-07-06) and never built: invert one physics tick at a time (undo friction `v = v_next / f4`, undo the move `p = p_next - v`, subtract that input's acceleration, undo the gate: `|v_before| >= 0.005` restores the velocity, else it sits in the `(-0.005, 0.005)` band heading to zero), branch on the per-tick input, prune any state off the block/corridor on the ticks that must be on it (coyote and airborne ticks exempt), and forward-verify byte-exact at the end, because float inversion is only approximate; the march would steer branch/prune, the guarantee comes from the forward replay. Nix solved forward the next day (§10.1), so the march was never needed. Recorded open risks, still unmeasured: float-inversion drift over many ticks; airborne branching width (the ~12-tick airborne arc has weak block pruning); whether the anchor must be the whole landable velocity band at the takeoff tick rather than one minimal point; and whether the backward-reachable set reaches v=0 on the block at all (a negative would have proven the route needs an un-cut whole-route solve).
