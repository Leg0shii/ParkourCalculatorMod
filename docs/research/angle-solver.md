# The angle solver: design record, verified results, and open problems

This document is the single research record for the angle-solver subsystem. It consolidates the earlier research files (the block-solving handoff, the long-jump handoff and findings, the constraint-derivation brief and findings, and the code audit) into one place, preserving every durable result while omitting session-specific scaffolding. It was last consolidated on 2026-06-11 against `main`, shortly after the angle-optimizer merge (#122).

Contents:
1. [Problem structure](#1-problem-structure)
2. [The shipped solver pipeline](#2-the-shipped-solver-pipeline)
3. [Verified properties](#3-verified-properties)
4. [Negative results and pitfalls](#4-negative-results-and-pitfalls)
5. [Performance](#5-performance)
6. [Block solving (shelved)](#6-block-solving-shelved)
7. [Open directions](#7-open-directions)
8. [Headless validation and build notes](#8-headless-validation-and-build-notes)
9. [Citations](#9-citations)
10. [Inertia folding and dual-path recovery (#204, 2026-07-03)](#10-inertia-folding-and-dual-path-recovery-204-2026-07-03)
11. [Trace facility, B&B tuning, and the miss triage (gh-204 follow-up, 2026-07-03)](#11-trace-facility-bb-tuning-and-the-miss-triage-gh-204-follow-up-2026-07-03)

---

## 1. Problem structure

Minecraft (MC) movement is a discrete-time dynamical system advancing at 20 Hz. The keyboard inputs (keys, jump, sprint, sneak, potion effects, and the ground or air state of each tick) are fixed and given, so the only free variables are the per-tick yaw angles during air ticks. The yaw rotates the thrust vector applied in that tick and is therefore the entire steering authority. Vertical motion is independent of yaw, since it consists of gravity and the jump impulse alone. The per-tick feet height `y(t)` is consequently known exactly in advance, and only the horizontal `(X, Z)` trajectory depends on the decision variables.

The per-tick horizontal update is a byte-exact port of the 1.8.9 `moveFlying` and friction chain: $v^{+} = v + R(\theta)a$, followed by $p \leftarrow p + v^{+}$ and $v \leftarrow f v^{+}$, together with a small-velocity momentum cutoff and a jump impulse on grounded jump ticks. The player hitbox is 0.6 blocks wide (half-width 0.3) and 1.8 blocks tall (1.5 when sneaking). Blocks are arbitrary axis-aligned boxes such as slabs, skulls, and fences, captured as real hitboxes.

### 1.1 The horizontal dynamics are affine in constant-modulus inputs

Define the per-tick steering vector $u_t = R(\theta_t)\hat{a}_t$ with $\|u_t\| = m_t$. The modulus is constant and only the direction is free. Position at any tick is then affine in the $u_t$:

$$p_k = p_0 + \sum_{s \lt k} C(s,k)\,u_s, \qquad C(s,k) = (S_k - S_s)/\Phi_s$$

Here $\Phi_s$ denotes the prefix product of the per-tick friction factors and $S_k$ the corresponding prefix sum (named `fPre` and `sPre` in the code). The map $u \mapsto p$ is linear, and the only nonconvexity of the unconstrained problem is the modulus sphere. This is exactly the structure addressed by lossless convexification (LCvx) of fixed-thrust trajectory problems (Acikmese and Blackmore). The inner maximization $\max_{\|u\|=m} g \cdot u = m\|g\|$ is tight, so the Lagrangian dual has zero gap and admits the closed-form costate recovery $u^{*}_t = m_t\,g_t/\|g_t\|$. This property is what makes the microsecond fast path possible, and any future formulation that preserves the affine map and adds only convex cuts inherits the same machinery.

The boundary of the theorem matters in practice. LCvx covers control constraints, together with convex state constraints that are active only at isolated instants. A keep-out zone is a non-convex state constraint active over an interval and therefore falls outside the theorem. Collision can consequently not be folded into the dual without losing the zero gap, whereas a fixed convex cell face can, since it is just another linear wall. This single fact dictates the constraint-derivation architecture of section 6. A further caveat is that discrete-time LCvx is provably lossy: the relaxed solution can violate the modulus constraint at some temporal grid points, and the number of such points is bounded only under additional conditions (Luo et al. 2024, 2025). For this reason every closed-form result is gated by byte-exact re-certification instead of trusting losslessness tick by tick.

### 1.2 The constraint alphabet

The solver consumes per-tick scalar or range bounds on `X`, `Z`, and `F` (facing), velocity bounds expressed as position differences ($\Delta X_t = X_t - X_{t-1}$), and exactly one MAX or MIN objective on one axis at one tick. Position walls are linear in $u$. Facing walls are not, and currently force the CMA-ES fallback (section 7, item 2). One alphabet extension is known to be worth adding when the need arises: oblique half-spaces $a^T p_t \le b$ are supported natively by the affine map and are necessary to wrap diagonal corridors or the sheared swept volume of section 1.3 tightly.

### 1.3 The swept collision model

Collision is not an endpoint check. MC's `Entity.moveEntity` resolves the intended displacement one axis at a time, in the order Y, then X, then Z, offsetting the hitbox after each axis. `SweptCollision` is the byte-exact port. Several consequences follow.

First, collision is a property of the swept segment from $p_t$ to $p_{t+1}$, and endpoint-only checks silently pass corner clips. This caused real in-game failures. Second, the resolution is asymmetric. The X clamp tests the player's start-of-tick Z, so crossing a block in X requires being Z-clear on the previous tick, while the Z clamp tests the already-moved X, so X may be cleared in the same tick as a Z crossing. The effective keep-out region is therefore a sheared parallelogram volume rather than a symmetric Minkowski box. In practice it is encoded as a per-tick keep-out half-space that is active on the in-band tick and on the following tick (the "+1 dilation"). Third, temporal reachability couples the constraints over time. A condition such as "be east of the skull" may be unreachable before some tick `t*` and must hold before "cross south of the wall" can. Reactive, greedy, or per-tick-local fixing thrashes on such chains and does not converge (section 4). Fourth, no safety margin is affordable. The canonical hard jump clears by roughly `6.6e-7` blocks, and padding walls by even `1e-3` makes such jumps infeasible, so approximations must be tight, exact, or adaptively tightened rather than conservatively inflated. Finally, from 1.14 onward the X/Z resolution order additionally switches on `sign(|v_x| - |v_z|)`, which becomes relevant when porting beyond the 1.8.9 and 1.12 semantics.

`ExactJumpModel`, the collision-free byte-exact forward model, equals the real `SimulatorEntity` for any strictly-outside path, and `SweptCollision` is byte-exact MC collision. Together they make every candidate algorithm fully self-validatable headlessly, without a human in the loop or an in-game run (section 8).

---

## 2. The shipped solver pipeline

`AngleSolverEngine.runJob` (worker thread) routes a solve as follows.

| stage | class | role |
|---|---|---|
| fast path (single jump) | `ClosedFormSolve` -> `CostateDualSolver` | The LCvx closed-form dual on `JumpLinearModel` finds facings that are globally optimal FOR THE LINEAR MODEL in roughly 10 to 140 us per solve. A margin ladder with byte-exact re-certification returns null rather than an uncertified result. The byte-exact game can out-reach the linear model's optimum (section 2.1.3), so this result seeds the race below; it is never the final word on the objective. |
| degenerate-direction closer (single jump) | `SlpSolve`, seeded by `runJob` | Solving INTO a same-axis wall (e.g. max X against the landing corridor's X &le; hi) cancels the objective out of the costates at the dual optimum, so the recovery is undefined and that Solve-For direction fails to certify while the opposite one solves (section 2.1.1). The SLP closer repairs this primally on the user's own objective: first from the dual seed, then reseeded from an alternate direction's certified optimum (feasibility is objective-independent, so any direction's optimum is a feasible seed on the same constraints). Alternate directions are never returned as the result. |
| multi-jump span | `LongRunSolver` | Receding-horizon decomposition (section 2.1). The engine routes multi-jump spans there directly, because the monolithic dual was measured not to converge across dozens of jumps. |
| duality-gap closer | `SlpSolve` | Trust-region sequential LP on the facings, judging every iterate on the byte-exact forward's wall slacks with the closed-form friction coupling as the exact Jacobian. Invoked per window after the margin ladder failed in every direction, and per single jump as above (section 2.1). |
| objective race | `SolveCore` + `CmaesJumpHarness` | Multistart CMA-ES on the byte-exact model, followed by `BucketAscentPolish` (a strictly feasible compass ascent). The met/total count is reported faithfully. On every single jump, CMA-ES races the closed-form/SLP result and the better byte-exact objective wins: the SLP ascent is local (measured 2.69 vs 0.95 on j008 Z/MIN), and the closed form is exact only for the linear model, which the game out-reaches on swing-heavy jumps (section 2.1.3). The race is skipped only on the one certificate no model can break: a user cap on the objective axis at the objective tick achieved to within 1e-6 ("optimal at constraint cap" in the result details). The dual's weak-duality bound is reported as the "Dual bound gap" detail for diagnostics — it bounds the LP, not the game (a negative gap records the byte-exact path out-reaching the LP), so it never settles a result. |
| smoothing | `SmoothingPolish` | Smooths the free ticks of underdetermined solves toward their neighbors. Internal gates keep byte-exact feasibility and the achieved objective. |

Every path reports the facing chain that Apply actually realizes, namely `toGameFacings(wrapAll(yaws))`, so the reported trajectory is bit-for-bit the in-game one. No path can report a false success: the closed form re-certifies byte-exactly, the SLP returns null unless its final byte-exact violation meets the tolerance, `LongRunSolver` re-verifies the full concatenation, `BlockSolver.ok()` requires a swept-clean and landed path (the dormant block-solving path, section 2.2), and the CMA-ES path reports its met/total count faithfully.

### 2.1 The long-run solver as a receding horizon

Long multi-jump runs solve from scratch in about 0.1 s, using only the start state, the physics structure, and the constraints. The reference problem is j001 (353 ticks, 30 jumps, 81 constraints, a recapture of the former `deserthard-v12` save). No recorded trajectory and no per-fixture tuning are involved. The design follows from measurement.

The convex dual solves a single jump to global optimality in microseconds and keeps converging for windows of up to about ten jumps, but not across a full run. On the full span the dual hits its iteration cap 14 to 88 blocks away from feasibility, and more iterations do not help: raising the cap from 100 to 1000 to 8000 moved the error from 14.4 to 17.7 to 15.5 blocks at a cost of 146 s. CMA-ES at several hundred dimensions (one yaw per tick) is equally ineffective.

The solver therefore slides a window of ten jumps over the run. Each window is solved to global optimality by the dual, trying alternate Solve-For directions since feasibility is objective-independent. The first three jumps of the window are committed, their exact byte-exact exit state (position, velocity, and yaw) is chained into the next window's seed, and the window slides on. The window overlap acts as lookahead: a committed jump's exit is, by construction, the entry of a feasible continuation of (window - commit) jumps, so the commitment cannot render the following jumps infeasible. Exactly this coupling defeats a greedy jump-by-jump chain. The construction is multiple shooting with the per-jump dual as the inner solver, in which the overlap plays the role of backward-reachability pruning of feasible entries.

The measured coupling horizon is about five jumps. On j001 a lookahead of four or less fails while five or more solves, and the greedy variant fails outright. The shipped configuration commits three jumps, which leaves a lookahead of seven and thus a margin above the measured horizon. If a commitment gets the chain stuck, the solver retries with a commitment of one. A window-size ladder of 10 -> 7 -> ... -> 1 covers cases where a full ten-jump window does not solve (sizes that clamp to an already-tried window near the run's end are skipped), and a byte-exact verification of the full concatenated path provides the final check. Robustness comes from every window being solved by the convex dual, with the SLP of section 2.1.1 as the byte-exactly gated closer where the dual carries a gap: there are no local optima, no initial guesses, and no tuning parameters, so the result is invariant to the incidental details that break local search.

Two failed approaches shaped this design. Warm-starting from the editor's recorded trajectory reached feasibility only from starts within about 0.5 blocks, that is, when the answer was already known, and a variant of the same problem shifted by one tick exposed this dependence. A monolithic local search over all decision variables at once (waypoint guess, global Gauss-Newton, then polish) landed in different basins or plateaus depending on incidental details, and the same one-tick shift made it stall one sine bucket short of feasibility.

Should a future run exhibit a longer coupling horizon than the window provides, the principled upgrade is explicit multiple shooting with backward-reachable feasible-entry boxes as seam constraints. The simple receding horizon has not needed it so far.

#### 2.1.1 The duality gap on cross-seam-coupled windows, and the SLP closer

The dual is not gap-free on every window. The zero-gap argument requires the recovery $u^{*}_t = m_t\,g_t/\|g_t\|$ to be the unique inner maximizer, and a multi-jump window whose walls couple across seams can break that: on j021 (four jumps, 13 constraints; X must cross a wall between consecutive ticks and then hold a band about 0.04 wide) the dual converges to a 5e-9 projected-gradient residual while the recovered trajectory remains 0.34 blocks infeasible, at every margin rung and regardless of iteration budget (verified at 20 000 iterations). Before the closer existed, this degraded the window ladder into greedy single-jump commits whose seam states doomed the later jumps, and the engine fell into the CMA-ES multistart: FAST and BALANCED failed outright and THOROUGH needed about 176 s.

`SlpSolve` closes such gaps primally. The window is still exactly linear in the per-tick inputs, so it runs a trust-region sequential LP on the facings, seeded from the dual recovery at margin 0 (globally informed even when infeasible), with two exactness properties: every iterate is judged on the byte-exact forward's wall slacks, so float drift and the sine-bucket lattice are inside the loop rather than margin-patched afterwards, and the LP Jacobian is the closed-form friction coupling rotated by 90 degrees ($du/d\theta = i\,u$), no finite differences. Phase 1 minimizes the worst exact slack until at least 1e-6 inside (about 16 LPs on j021); phase 2 improves the real objective while staying strictly inside. It is budgeted at 60 LPs (about 0.2 s worst case), runs once per window only after the margin ladder failed in every direction (its phase 1 is objective-independent, so the other directions would fail identically), and cannot report a false success. j021 now solves 13 of 13 at every effort in about 150 ms, where the old THOROUGH near-solution also sat about 1 block short of the certified objective.

The same recovery degeneracy has a much more common SINGLE-jump form, and it is why "Solve For" used to work in some directions but not others. The objective is a position at the objective tick, and a UI wall on the same axis at (or friction-coupled near) that tick has a coefficient vector exactly parallel to the objective vector. When the Solve-For direction optimizes INTO such a wall (max X against an X &le; hi landing corridor, min Z against a Z &ge; lo, and so on), the dual optimum puts the cancelling multiplier on that wall, the costates $g_t$ vanish, and the recovery defaults the undetermined ticks to point along the objective axis, sailing straight through the wall it was supposed to hug. Every margin rung then fails byte-exact re-certification, while the OPPOSITE direction (which pulls away from that wall, leaving the costates nonzero) certifies in microseconds. Surveyed over the j004-j020 capture library, 12 of 17 single-jump captures had at least one such failing direction. The engine therefore runs the SLP closer on the user's own objective whenever the closed form cannot certify: first from the dual seed, and if that stalls too (a degenerate seed points every undetermined tick through the wall, which phase 1 sometimes cannot walk back), reseeded from an alternate direction's certified optimum, which is a feasible point on the same constraints. Two phase-2 details matter for this use: the acceptance gate is plain byte-exact strict feasibility (demanding extra clearance would forbid hugging the very wall the objective optimizes into, which is where the optimum lives), and a phase-1-collapsed trust region is reset before the ascent. Because the SLP ascent is local, CMA-ES still runs on such solves and the better objective is kept; the alternate direction's own result is never returned, so the reported solve always optimized the direction the user picked.

#### 2.1.2 Centered lead-in windows

A lead-in window's objective is only a surrogate ("any feasible"), so hugging walls there is pure liability: the margin-0 vertex quantizes fragilely (burning ladder rungs) and commits extreme seam states that can doom the next jumps. Lead-ins therefore solve centered. The closed form tries the largest margin first (`optimizeRobust`; the dual's vertex hugs the margin-tightened walls, so the first margin that certifies is the realized clearance on every active wall), and the SLP fallback deepens phase-1 clearance toward 0.02 and skips the hugging phase (`optimizeCentered`). Only the last window, which carries the real objective, still hugs. This dropped j001 from about 133 ms to about 95 ms, since lead-ins certify on the first robust rung instead of climbing the ascending ladder. The single-jump fast path and direct `ClosedFormSolve.optimize` callers are untouched.

#### 2.1.3 The linear model's reach is not the game's reach

The closed form's optimality certificate (and the weak-duality bound behind it) holds for `JumpLinearModel`. The byte-exact game is not inside that model's feasible set, and on jumps whose optimum needs a large yaw swing the game reaches FURTHER than the LP optimum — the linearization is not a relaxation, so "exceeds the dual bound" is not a contradiction. Measured on j022 (1bm hb fly, 11 airborne ticks, min X whose optimum swings the facing from about 0 to 135 degrees): the closed form certifies -531.6506 while the byte-exact reach is -531.7001, a 0.0494 under-reach past a 1.4e-4 landing margin. The capture library shows the same effect at smaller scale: racing CMA-ES recovered 8.6e-3 on j019 X/MAX, 6.8e-3 on j005 X/MAX, and 4.0e-3 on j020 X/MIN over the certified closed form, in several cases landing exactly on the recorded in-game reference the closed form had fallen short of. Wall-capped directions were short too: the ladder/lattice standoff leaves the LP result 3e-6 to 1.9e-3 inside its own cap, where the raced result hugs it to about 1e-10.

The engine therefore treats every linearized result as a seed and races the byte-exact search on all single-jump solves, except when a same-axis user cap at the objective tick is achieved to within 1e-6 — a model-free bound nothing can beat, and the only certificate strong enough to skip the race. The cost is the effort budget's CMA-ES pass (about 0.2 to 0.3 s at FAST) on solves that used to return in milliseconds; the multi-jump receding horizon is unchanged (its windows use the closed form internally as surrogates, where the certificate question does not arise — only the final user-facing objective carries the claim). Without a cap, the reported objective is the best the byte-exact search found, labeled by solver chain, never as "optimal".

### 2.2 Block solving

Block-to-constraint solving (the DERIVE planner and the gh-212 blocks-only phase) was shelved 2026-07-04; its full record and post-mortem is `block-solver.md`. The shipped pipeline documented here does not depend on it.

---

## 3. Verified properties

The code audit of 2026-06-10 verified the following claims by reading the float chains against the MC 1.8.9 semantics they port, by re-deriving the mathematics, or by measurement, with a baseline of 35 of 35 tests green. They are recorded here so that they are not re-derived.

- The linear-model coefficients `C(s,k) = (S[k] - S[s])/fPre[s]` follow exactly from unrolling `v_{t+1} = (v_t + u_t) * f4_t` and are numerically stable by construction, since the coefficient is bounded by $\sum 0.91^k \approx 11$ and therefore cannot grow exponentially even over hundreds of ticks.
- The zero-duality-gap argument is correct generically. The degenerate case of a vanishing costate is converted into a fallback by the objective-direction default combined with byte-exact re-certification. Generically is the operative word: a multi-jump window with cross-seam wall coupling can carry a genuine duality gap (section 2.1.1, measured on j021 after this audit), which is why the SLP closer exists.
- `McSineTable` generation and lookup are bit-identical to MC, including expression order. Both distinct yaw-to-rad casts (`0.017453292F` in `jump()` and `yaw*(float)PI/180F` in `moveFlying`) are ported.
- The per-axis (1.8 and 1.12) versus combined-XZ (after 1.12, applied to the 1.21 loader) inertia selection is correct. The threshold applies to the post-friction carry at the top of the tick, the jump impulse fires only when grounded, and strafe is disabled on grounded jump ticks in both the exact and the linear model.
- `toGameFacings` is the bit-exact model of Apply for the normal wrapped-absolute path, and locked rows resynchronize the float chain in both.
- The `LongRunSolver` window slicing is sound. Seam constraints are enforced in the window that commits them and re-checked as trivial tick-0 constraints in the next, velocity pairs straddling a commit seam stay enforced, and the committed coverage is gapless.
- The dual's convergence machinery (the `U_TOL` stationarity test, the free-set selection, and the Cholesky factorization of the damped positive-semidefinite Hessian with adaptive Levenberg damping) is sound.
- Determinism holds. Seeds are fixed everywhere, parallelStream results are order-preserving, and the worker handoff publishes through a single volatile field with the cancel token checked before publishing.

Five audit findings were fixed the same day and are recorded for traceability. The long-run result is now verified and reported on the facing chain Apply realizes, where previously the seam-chained facings were verified, a latent one-ulp re-rounding risk. The result panel now evaluates F-mode constraints on the same facing array the solver scored, where previously the two differed by a small epsilon. EQ constraints are now treated internally as ranges of plus or minus the met tolerance, where previously they silently disqualified the fast path and the polish. The margin ladder now stops at the first unbounded dual, since infeasibility is monotone along the ladder. The trivial-constraint feasibility check is now exact, where previously a tick-0 constraint violated by 1e-9 exhausted the full ladder and the CMA-ES fallback before the unavoidable negative answer.

Two magic numbers in the dual are deliberate and documented rather than changed. `DIVERGE_PGRES = 4.0` is the stall-bail floor. Removing it broke j020's closed-form solve and made j001 slower, so the gate protects in both directions. Should it ever misfire, the remedy is to make it relative to the initial residual. `LAMBDA_CAP = 1e9` declares divergence in absolute multiplier units and is adequate at current problem scales.

Three code constructs look duplicated but are deliberate and should not be unified: `wrap` versus `wrapDelta`, the ladder margin applied inside the dual versus `compileWall`'s margin parameter, and the result panel's `MET_TOL` versus the solver's `FEAS_TOL`.

### 3.1 Solver constants: why each value

Every tunable constant in the solve pipeline was swept over the capture library (72 single-jump direction-solves plus 20 long-run direction-solves; seeds are deterministic, so any diff is real). The verdicts live here, once, rather than next to each constant.

- **`FEAS_TOL = 0` (engine).** Not swept — an axiom. The model is byte-exact, so a positive tolerance would accept paths that clip in game; the achievable wall hug is bounded by the ~1e-6-spaced sine buckets, not by this.
- **`MET_TOL = 1e-4` (engine; EQ corridor half-width and met-reporting slack).** An EQ compiles to a ±MET_TOL wall pair, and a Solve-For along the EQ axis pins the corridor's FAR edge, so an EQ result sits the full half-width off the typed value while reporting met — at the former 1e-3 that error was 10x a common pk margin. Measured on EQ-at-landing solves: a 1e-4 corridor solved everywhere probed, including an EQ pinned to the reachability extreme (landed within 4e-7 of the typed optimum), while 1e-5 and 1e-6 failed at that extreme (the race lands ~1e-5 outside such corridors). 1e-4 is the tightest measured-safe corridor and coincides with the pk floor; j023 pins it in CI.
- **`CAP_GAP_TOL = 1e-6` (engine).** Raced winners hug a same-axis cap at 1.4e-10 to 1.4e-9; LP-path results stand off by 3e-6 or more; pk margins start near 1e-4. So 1e-6 labels every truly pinned result while claiming at most pk-invisible slack. Tightening to 1e-9 changed no objective and no time — it only stripped the optimal-at-cap label from the three hugs above 1e-9 (j004 X/MIN, j009 Z/MIN, j011 X/MAX).
- **`CMAES_SIGMA_DEG = 90` (engine).** 30/60/180 are flat within seed noise: objectives shuffle a few e-3 in both directions at identical cost, with a slight net-negative tilt away from 90.
- **FAST race budget, `16 restarts / 4500 evals / 2 polish basins` (engine).** The knee, measured from both sides: half the budget flips four direction-solves to infeasible (j006 X/MAX and Z/MAX, j022 X/MAX and X/MIN) and loses up to 9e-3 of objective on 35 of 76, while the THOROUGH budget as the default costs ~50x wall time (median ~12 s per solve) for seed-noise-level, non-monotone changes.
- **`WINDOW = 10`, `COMMIT_LADDER = {3, 1}` (LongRunSolver).** The 8/10/12 x 2/3/4 grid all solves 20/20. Window 12 runs up to 2x slower (bigger windows make harder duals); window 8 is ~20% faster but drops the default commit's lookahead to 5 — exactly the measured coupling horizon, with no margin. 10/3 keeps horizon+2.
- **`DIVERGE_PGRES = 4` (CostateDualSolver).** Insensitive across 2/8/16: closed-form solvability, speed, objectives, and long runs identical. Only its removal is known harmful (j020; see the magic-numbers note above).
- **`MAX_LP_CALLS = 60`, `MAX_PHASE1_CALLS = 40`, trust region `30/45` (SlpSolve).** Swept jointly (budget x2/x4 by trust-region start 15/30/60), since these only plausibly help together: no incumbent gained or lost in any of the nine configs and long runs stayed green, so the budget is not what limits SLP. Narrowing the trust region to 15 degraded five incumbents' objectives at every budget; widening to 60 changed nothing. The library's one chain failure (j022 X/MIN) persists at 4x budget and fails in under 4 ms — a wrong linearization, not an under-iterated one (section 2.1.3) — so the byte-exact race is structurally its only solver.

---

## 4. Negative results and pitfalls

The following results are negative and are recorded to prevent repetition. The first five are general, and the rest are specific to block solving and constraint derivation.

1. Velocity must never be pinned at segment seams. Matching seam velocity to a reference within a tolerance creates a precision floor of about 2e-3 that the byte-exact solve cannot reach, and it accumulates drift: a 1e-2 band accumulated to 0.34 blocks by the fifth jump. The correct approach is to chain the exact state and never to constrain velocity for stitching.
2. Row yaws are per-tick deltas, while the recorded `state.yaw` is absolute. The absolute facing used by the move at tick `t` is `getState(t+1).yaw`, and the +1 offset matters. Being off by one produces over 18 blocks of divergence, and feeding deltas as absolutes produces over 100.
3. The round trip from absolute facing to delta to `toGameFacings` is not bit-exact over long runs. It drifts by about 0.5 blocks over the full span of the reference run when reproducing recorded facings, which ruled out the approach of reproducing the recorded run segment by segment. The issue is moot for the shipped solver, which solves from scratch in one self-consistent model, but it still applies to anything that replays recorded yaw data through the model.
4. CMA-ES is ineffective at high dimension, regardless of warm starts or budget, and failed at several hundred dimensions in every configuration. Its value is limited to the low-dimensional fallback.
5. When a `JumpSpec` is built only to call `maxViolation`, its objective tick must still lie within the segment. Nothing validates the tick at construction or compilation, and `maxViolation` ignores the objective, but any later consumer of the objective (the linear model, the objective read-out) indexes per-tick arrays with it.
6. Endpoint-only collision checks are wrong (section 1.3) and were the actual cause of three in-game failures.
7. Safety margins on derived walls are not affordable (section 1.3). Padding an edge by even 1e-3 makes extremely tight jumps infeasible.
8. Deriving walls from a recorded route fails, because the route does not exist until after solving. A bad attempt yields useless walls, for example a north-of-obstacle wall on the landing tick, which renders the spec infeasible.
9. Reactive nearest-exit and per-tick side flipping thrash, since the homotopy becomes inconsistent, and reactive cutting planes driven by the swept oracle do not converge on lookahead-coupled corner wraps, even with a depth-first search over pass sides and delayed-crossing options. The convergent variant of the idea is the principled SCP shell described in `block-solver.md`, which differs by a trust region, simultaneous multi-tick cuts, homotopy-aware initialization, and an L1 exact penalty.

---

## 5. Performance

The remaining speed headroom was deliberately deferred on 2026-06-10. About 100 ms for a from-scratch 353-tick, 30-jump solve is interactive for an off-thread one-shot action (j001 takes roughly 95 ms since the centered lead-ins of section 2.1.2, and j002/j003 solve all four directions in 15 to 36 ms), and single jumps sit at the structural floor of one dual solve plus certification, roughly 10 to 140 us. The decision is to revisit when one of three conditions becomes true. The first is that solving becomes an inner loop, for example through block derivation, automatic routing, or batch re-solving that issues many solves per interaction. In that case cross-window warm starts (section 5.1) are the item to build. The second is that runs grow several-fold past about 350 ticks, scaling 150 ms toward seconds. The third is that an F (facing) constraint is used on a multi-jump run. That is a capability gap rather than a speed problem, and section 7, item 2 describes the fix to build the moment the need appears.

The time profile on j001 (measured before the centered lead-ins) was as follows. Eight window solves account for essentially all of the 150 ms. The margin ladder's small rungs exhaust the `MAX_ITER = 100` budget without certifying, and no window certifies below a margin of 6e-4, because sine-bucket quantization noise accumulates past those margins in windows of over 100 ticks. Of roughly 2200 dual iterations, only about 160 belong to certifying rungs. The centered lead-ins (section 2.1.2) cut exactly this waste, since a lead-in now certifies on the first robust rung, which is what moved j001 to about 95 ms.

Three prototyped speedups were measured and rejected and should not be retried in the same form. In the table, a negative percentage denotes a speedup relative to the baseline and a positive percentage a slowdown.

| prototype | j001 | j002/j003 | verdict |
|---|---|---|---|
| lead-in windows start the ladder at 6e-4, full ladder on failure | -28 % | some directions +60 % | no net improvement |
| stall-bail at any residual (dropping the `DIVERGE_PGRES` floor) | +33 % | mixed | breaks j020, rejected |
| per-run rung memory (starting at the last certifying rung) | +45 % | worse | cold mid-ladder starts are expensive and the ratchet over-tightens |

The common negative result is that skipping rungs is counterproductive: the warm-start chain provides a measurable benefit. A cold dual solve at any rung costs about 100 iterations on degenerate windows, so skipping rungs trades cheap warm iterations for expensive cold ones, and which rung profile is faster is problem-dependent. The shipped centered lead-ins are not a retry of these prototypes: lead-ins run a separate largest-first ladder whose goal is clearance rather than objective, while the real-objective window keeps the full ascending ladder and its warm chain.

### 5.1 Cross-window warm starts and smaller improvements

Consecutive windows share about 70 % of their walls, and wall identity is stable (name plus absolute tick). Seeding window k+1's dual at each rung from window k's multipliers would preserve the warm chain across windows instead of paying about 100 cold iterations per window and rung. The estimate is a speedup of 2.5 to 5x on long runs, which would bring j001 to roughly 30 to 60 ms. It requires an optional lambda-by-wall-name seed on `CostateDualSolver`.

Smaller known improvements follow, in descending value. `ExactJumpModel.stepRange` is exercised only through `forward`, which always starts at tick 0, so its incremental form is unused. It recomputes only the tail `[from, n)` bit-identically, which at least halves the dominant cost of the single-tick perturbation loops in `BucketAscentPolish` and `CmaesJumpHarness.polish` on long runs, and it should be wired up as part of any Phase-2 global-ascent work. Capping `buildHessian`'s inner loop at each wall's last coupled tick roughly halves the dominant per-iteration cost. `JumpLinearModel` and the compiled walls can be reused across the four direction retries, since only the objective vectors change. Thread-local scratch on the CMA-ES path is worthwhile only if that path's latency ever matters. A performance canary fixture with a `maxSolveMs` of about 2 s would catch a silent return of the 2.1 s behavior that preceded the receding horizon without introducing intermittent CI failures.

---

## 6. Block solving (shelved)

Block-to-constraint solving (deriving yaws from picked blocks, with no human constraints) has its own record: `block-solver.md`. It covers the DERIVE planner (`BlockSolver`, the forced-crossing-tick homotopy planner and its GCS/LCvx target architecture), the gh-212 blocks-only phase, the false-positive post-mortem, and what remains viable for a future attempt. The shipped angle solver in this document does not depend on any of it.

---

## 7. Open directions

The following items are ranked by value per effort, together with their triggers.

1. Phase-2 long-run objective polish, the known remaining work. `LongRunSolver` returns a feasible run, but only the final window optimizes the real objective. The options are to re-solve the final k windows with progressively earlier seams under the committed prefix, where each re-solve is one window dual, or to run a global strictly feasible `BucketAscentPolish` on the byte-exact model starting from the feasible run. The latter is tractable at several hundred dimensions precisely because it only improves and never searches. `stepRange` (section 5.1) should be wired up first.
2. Facing walls in the closed form, a capability item rather than a speed item. An F constraint is a sector constraint on the input direction. The inner maximization over the set where $\|u\| = m$ and $\hat{u}$ lies in the sector is still closed-form, since the costate direction can be clamped to the nearer sector edge. The dual stays convex and the zero-gap argument survives. Today a single F constraint anywhere disqualifies the entire fast path. This item should be built when the third trigger of section 5 fires.
3. Cross-window warm starts (section 5.1), when a trigger of section 5 fires.
4. Folding the single-jump path into `LongRunSolver`. A one-jump run is one final window with the full ladder, so the fold deletes the `countJumps` branch at zero behavioral change. The cost to weigh is one extra call layer on the microsecond path.
5. `JumpPhysicsInputs.jumpTick` survives only as a fallback. Collapsing it to the mask plus a `firstJumpTick()` helper removes a field with two sources of truth.
6. Direction-parallel window solves, a latency improvement only on windows whose first direction fails. Since j001 never fails a direction, this should be measured on a fixture that does before being built.

---

## 8. Headless validation and build notes

### 8.1 The committed harness

`core/src/test/resources/captures/` is the shared capture library (j001 through j021). The folders `resources/problems/<check>/` define which check each capture must pass: `solve/` requires that the capture still solves through the live engine within an optional time budget, and `closedform/` requires a byte-exact feasible, on-objective, and fast closed-form solve. `ProblemsTest` discovers everything, so adding coverage amounts to dropping a capture or an `.expect.json` sidecar into a folder. See `core/src/test/java/.../anglesolver/TESTS.md` and `resources/problems/README.md`.

For reading old branches and history, j001, j002, and j003 are recaptures of the former `deserthard-v12`, `-v7`, and `-vfail` saves, and the j154 block-derive fixtures live only in the `features/angle-optimizer` history (section 2.2).

j021 (rina v1 01, the duality-gap fixture of section 2.1.1) predates the `angleSolver.seed` field, so its launch state at tick 136 was reconstructed by replaying rows 0 to 135 through `ExactJumpModel` and validated bit-exact: replaying the capture's recorded failed yaws from the reconstructed seed reproduces all 13 recorded outcome positions to the full printed precision. The seed's Y components are not physical (the model does not clamp Y onto surfaces) and are irrelevant, since every constraint is X/Z and tick 136 is a grounded jump tick whose Y velocity is overwritten by the jump impulse.

The committed captures do not carry a `debug[]` array. `ProblemFixture` rebuilds the box trajectory from the recorded ticks. The save format does support debug saves (with "save debug values" enabled), which carry per-tick recorded state usable as a debugging oracle for fact-checking, for example by forwarding candidate facings through `ExactJumpModel` and comparing positions to measure drift. A recorded path is never an input the production solver may assume, since normal solves have no path given.

One measurement caveat applies. The engine `solve()` wall clock includes the worker-thread spawn overhead and must not be used for the microsecond fast-path number. The fast path is timed by running `ClosedFormSolve.optimize` in a tight loop, while `SolveBenchmark` provides the end-to-end number.

### 8.2 Validating block-derivation work

Shelved with the block solver. The derivation-validation recipe now lives in `block-solver.md`.

### 8.3 Build notes

`gradle.properties` pins a Windows JDK, which is overridden with `-Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64`. The loader modules require Gradle plugins (`fabric-loom`, `unimined`) that are unreachable offline, so for `:core` tests `settings.gradle` is trimmed to `include 'core'` and restored afterwards. Core is Java 8 with JUnit 4: `./gradlew :core:test -Dorg.gradle.java.home=...`.

---

## 9. Citations

Lossless convexification / fixed-modulus
- Acikmese & Ploen, "Convex Programming Approach to Powered Descent Guidance for Mars Landing," *JGCD* 30(5), 2007. https://doi.org/10.2514/1.27553
- Acikmese & Blackmore, "Lossless convexification of a class of optimal control problems with non-convex control constraints," *Automatica* 47(2), 2011. https://www.sciencedirect.com/science/article/abs/pii/S0005109810004516
- Harris & Acikmese, "Lossless convexification ... for state constrained linear systems," *Automatica* 50(9), 2014. https://www.sciencedirect.com/science/article/abs/pii/S0005109814002362
- Malyuta et al., "Convex Optimization for Trajectory Generation" (tutorial), *IEEE CSM*, 2022. https://arxiv.org/abs/2106.09125
- Luo et al., "Revisiting Lossless Convexification: Theoretical Guarantees for Discrete-time Optimal Control Problems," arXiv:2410.09748, 2024. https://arxiv.org/abs/2410.09748
- Luo, Spada, Acikmese, "Discrete-Time Lossless Convexification for Pointing Constraints," arXiv:2501.06931, 2025. https://arxiv.org/abs/2501.06931

Convex decomposition / safe corridors / MILP
- Deits & Tedrake, "Computing Large Convex Regions of Obstacle-Free Space Through Semidefinite Programming" (IRIS), WAFR 2014. https://groups.csail.mit.edu/robotics-center/public_papers/Deits14.pdf
- Liu, Watterson, Kumar et al., "Planning Dynamically Feasible Trajectories ... Safe Flight Corridors," *RAL* 2017. https://github.com/sikang/DecompUtil
- Richards & How, "Aircraft Trajectory Planning with Collision Avoidance Using MILP," ACC 2002. https://www.et.byu.edu/~beard/papers/library/RichardsEtAl02.pdf
- Kronqvist, Misener, Tsay, "P-split formulations: A class of intermediate formulations between big-M and convex hull for disjunctive constraints," *Math. Programming*, 2025. https://arxiv.org/abs/2202.05198

Graph of Convex Sets
- Marcucci, Umenberger, Parrilo, Tedrake, "Shortest Paths in Graphs of Convex Sets," *SIAM J. Opt.* 34(1), 2024. https://arxiv.org/abs/2101.11565
- Marcucci, Petersen, von Wrangel, Tedrake, "Motion planning around obstacles with convex optimization," *Science Robotics* 8(84), 2023. https://github.com/RobotLocomotion/gcs-science-robotics
- Chia, Jiang, Graesdal, Kaelbling, Tedrake, "GCS\*: Forward Heuristic Search on Implicit Graphs of Convex Sets," WAFR 2024. https://arxiv.org/abs/2407.08848
- Osburn, Peterson, Salmon, "Systematic Constraint Formulation and Collision-Free Trajectory Planning Using Space-Time Graphs of Convex Sets," arXiv:2508.10203, 2025. https://arxiv.org/abs/2508.10203

Collision-in-solver: SCP / SDF / MPCC
- Schulman et al., "Finding Locally Optimal, Collision-Free Trajectories with Sequential Convex Optimization" (TrajOpt), RSS 2013. https://www.roboticsproceedings.org/rss09/p31.pdf
- Zucker, Ratliff, Dragan et al., "CHOMP," *IJRR* 32(9-10), 2013. https://www.ri.cmu.edu/pub_files/2013/5/CHOMP_IJRR.pdf
- Posa, Cantu, Tedrake, "A direct method for trajectory optimization of rigid bodies through contact," *IJRR* 33(1), 2014. https://dl.acm.org/doi/10.1177/0278364913506757
- Mao, Dueri, Szmuk, Acikmese, "Successive Convexification of Non-Convex Optimal Control Problems with State Constraints," 2017. https://arxiv.org/pdf/1701.00558
- Li et al., "On the Surprising Robustness of Sequential Convex Optimization for Contact-Implicit Motion Planning" (the CRISP solver), arXiv:2502.01055, 2025. https://arxiv.org/abs/2502.01055

Swept CCD / homotopy / kinodynamic
- Tang, Kim, Manocha, "C2A: Controlled Conservative Advancement for Continuous Collision Detection," ICRA 2009. https://graphics.ewha.ac.kr/C2A/C2A.pdf
- Li, Ferguson et al., "Incremental Potential Contact (IPC)," SIGGRAPH 2020 (the positive-standoff contact model). https://ipc-sim.github.io/
- Li, Kaufman, Jiang, "Codimensional Incremental Potential Contact" (introduces additive CCD), SIGGRAPH 2021. https://arxiv.org/abs/2012.04457
- Wang, Ferguson et al., "A Large-scale Benchmark and an Inclusion-based Algorithm for CCD," *ACM TOG*, 2021. https://dl.acm.org/doi/10.1145/3460775
- Bhattacharya, Likhachev, Kumar, "Topological Constraints in Search-Based Robot Path Planning" (h-signature), AAAI 2010 / *Auton. Robots* 2012. https://www.lehigh.edu/~sub216/local-files/topology_AURO_author_version_57596.pdf
- Li, Littlefield, Bekris, "Asymptotically Optimal Sampling-based Kinodynamic Planning (SST/SST*)," *IJRR* 2016. https://arxiv.org/abs/1407.2896
- "Collisions" (axis-sequential Y-X-Z collide-and-slide), Minecraft Parkour Wiki. https://www.mcpk.wiki/wiki/Collisions

---

## 10. Inertia folding and dual-path recovery (#204, 2026-07-03)

Issue #204 asked for a stronger step-1 recovery after the dual bound so that multi-jump captures solve from the dual directly, without CMA-ES. Validation corpus: 27 proven-possible hpk captures (`core/src/test/resources/captures/hpk/`, d10 easier, d11 harder) plus `loopmm-3jump-lands`.

### 10.1 What shipped

- **Inertia folding.** MC's small-velocity momentum cutoff (per-axis 0.005 legacy, combined 9e-6 modern) is piecewise affine: for a fixed per-axis zeroing pattern, the position map stays affine in `u` with coefficients cut at the last zeroing tick. `JumpLinearModel` gained a pattern-aware constructor, `zeroingPattern` (a continuous stepper that reads the pattern off a candidate), and `velocityWalls` (validity walls keeping the pattern self-consistent). `ClosedFormSolve` runs an active-set fixed point (at most 4 passes, with a triviality guard) around its margin ladder. On loopmm this removes a 3.3e-2 inertia term from the recovery, leaving only the ~1e-4 sine residual (`InertiaFoldingTest`); j425 flips from miss to closed-form solve. The clamp-free `dualBound` is intentionally unchanged: with zeroing active the clamp-free dual is NOT a valid bound for the clamped dynamics (j1150 solves 1.03 beyond it).
- **RelaxationRecovery.** The ball relaxation (`|u_t| <= m_t`) is a convex SOCP whose optimum equals the dual value. It is solved by augmented-Lagrangian FISTA warm-started from the production dual (plus 5 dual warm restarts; the production dual alone stops too early), then realized on the modulus sphere by two seeds: error-diffusion dithering (full-modulus wiggles cancelling accumulated deviation, the butterfly technique) and plain projection. Both seed budgeted best-effort SLP runs (clamp-free and pattern-aware LP walls; neither variant dominates), then a bucket-lattice repair (`LatticeRepair`) and a pin ladder (re-solving with the relaxed path pinned in two-sided bands). Deterministic end to end.
- **Engine wiring.** `AngleSolverEngine.dualChain` = closed form -> SLP -> SLP reseeded from the three alternate-direction certified optima -> RelaxationRecovery. Single jumps got the RelaxationRecovery tail; multi-jump specs now run the whole chain when the receding-horizon solver misses (previously they fell straight through to CMA-ES). Chain results warm-start the race exactly like receding-horizon results.
- **Gate.** `problems/dualrecovery/` sidecars wire every hpk capture into `ProblemsTest`: the chain must byte-exact-solve each in its saved direction, no CMA-ES, no warm start. Dev screens `HpkDualRecoveryScreen` (per-capture stage table) and `RelaxDiagScreen` (stall margins, recorded-path replay) run only with `PKC_SCREENS` set.

Score: 24 of 28 solve from the dual chain (baseline before this work: 1). Runtimes range from sub-ms (closed form) to ~3 s (j346, relaxation recovery on n=39, m=110).

### 10.2 A harness trap that mattered

The test harness used to build placeholder boxes, so `Sprint: DERIVE` / `Inputs: KEEP` captures degraded to row-derived always-sprint specs (gh-120 sampling reads `boxes.getState(t+1)`). Under those wrong specs the recorded in-game paths were literally infeasible (violations up to 1.9) and unreproducible by `ExactJumpModel` (drift up to 2.06). `Fixtures.buildBoxes` now builds engine boxes from the capture's `debug` blocks; every hpk capture's recorded path replays byte-exact with zero violation. Any capture-driven work MUST use it; 12 of the 13 then-remaining misses were artifacts of the wrong specs.

### 10.3 Bake-off results (all falsified except the relaxation)

- **Bound-pruned B&B** over seam cells: 0/1 on the old base and dominated on corrected specs in its clamp-free form, but it became the breakthrough once branched over ZEROING PATTERNS (user push, 2026-07-03). Mechanism discovered on loopmm: the hand route runs |vx| = 0.00465 < 0.005 into tick 67, so the game zeroes X velocity for the last four airborne ticks; the human uses the momentum clamp as a free X-brake. That basin does not exist in the clamp-free affine model, so every clamp-free dual, LP, and cell bound points away from it. Fix: `BoundPrunedRecovery` now enumerates suffix zeroing patterns per axis (zero-a-from-tick-k), bounds each pattern's root with its own pattern-folded dual (validity walls included, so per-pattern bounds are sound), and runs the cell B&B inside patterns in best-bound order with a shared incumbent; velocity walls are evaluated in u-space by the restore machinery, and patterned searches use the inertia-aware SLP. Result: loopmm LANDS, Z@71 = -279.299912 (+8.8e-5 past the -279.3 pad edge; hand route -279.29973) via the zx@31 branch, byte-exact feasible, cold start, 144 s. Wired into the engine's Exhaustive-multi-jump path after the seam sweep.
- **Seam-sweep** (pin grids over the loose constraint bands of intermediate ticks, bound-ranked SLP rescue, beam rescue): 0 additional FEASIBILITY solves on corrected specs (on j344 it exits instantly because all constraints sit on the landing tick and there are no seams), but it is the clear winner for the REACH problem: on loopmm's objective it hops from the local plateau (-279.3084, 0.0084 short of the pad) to -279.30046 (4.6e-4 short), 18x closer than the relaxation recovery and past the historic exhaustive-ILS plateau (~0.006). Budget-insensitive beyond ~60 s (a new basin wall, not starvation); BucketAscentPolish adds 5e-5, ILS adds nothing on top. Shipped as `SeamSweepRecovery` (incumbent-seedable) and wired into the engine's Exhaustive-multi-jump path (60% of the ILS budget, before ILS, better objective kept).
- **Needle threader** (meet-in-the-middle over sine-bucket combinations on the most sensitive ticks, exact single-flip effect vectors, superposition prediction, exact validation): built and falsified. The full +-40-bucket enumeration on j344's five influential ticks tops out at a predicted margin of -1.5e-6, and large combined moves break superposition (predicted -1.5e-6, exact 1.7e-2). The remaining misses are not lattice-local.

### 10.4 The frontier: four misses and their shapes

All four stall at small exact violation but the feasible needle is in a DIFFERENT basin (recorded yaws differ by up to 170 deg):

- `j717` (1.9e-4): X@21 LE vs X@26 GE conflict; the differential lives on the five air ticks between them, each with ~1e-6/bucket authority.
- `j716` (1.1e-4): eight constraints simultaneously ~1e-4 violated across ticks 4..42; a fine-scale compromise point far from the recorded basin.
- `j335` (1.8e-3): chained needle; the recorded path threads 1e-6..1e-7 margins at ticks 9, 16, and 21 with a 14-deg yaw redistribution.
- `j828` (2.1e-1): coarse basin problem; the recovered path hugs the wrong X edge (margin 1.2e-8 at three pads) while the recorded path keeps 0.19..0.23 X margin. Also 13/39 relaxation ticks off-sphere, so the relaxed optimum genuinely wants sub-modulus thrust.

These need a global stage (the continuous-relaxation global seed of the Wolfram/anvil notes), not a better polish. The dual chain's certify floor is the sine residual, ~1e-4 accumulated; corridors narrower than that are a lottery by construction.

A fifth reach-class case, loopmm, is now CLOSED: the pattern-branched B&B lands it at Z@71 = -279.299912 (see 10.3). The remaining four misses should be re-attacked with the same lens: check each recorded path for zeroing events first (j717 and j816 are momentum jumps, prime suspects), then pattern-branched B&B; only what survives that is genuinely global-stage work. The reach benchmark for these loosened captures should score against the true pad edge, not the loosened constraints.

## 11. Trace facility, B&B tuning, and the miss triage (gh-204 follow-up, 2026-07-03)

The follow-up session executed `docs/research/dual-recovery-next-session.md`. Gate after it: `problems/dualrecovery/` at 27/28 (was 24/28), loopmm loose landing at ~4.3 s (was 6.5 s), tight landing now works at all.

### 11.1 SolverTrace

`solver/SolverTrace.java`: static trace sink, off by default, enabled via `PKC_SOLVER_TRACE=<tag>` (env) or `-Dpkc.solver.trace=<tag>`, or programmatically (`enable`/`disable`). Zero cost when off (call sites guard on `SolverTrace.on()`); events go to `build/reports/solver-trace-<tag>.txt` with a ms-since-solve-start column, a thread column, and a stage tag, all `Locale.ROOT`. Instrumented: ClosedFormSolve (per pass and per rung: margin, dual iters, dual value, exact violation, stop reason), SlpSolve (per LP: phase, trust radius, predicted vs exact violation, accept/reject; entry and phase transitions), RelaxationRecovery (the old RXT timing prints folded in, AL outer iterations, per-seed SLP outcomes), BoundPrunedRecovery (per node: pattern, depth, bound, seed violation, restore violation and iterations; SLP calls; the incumbent timeline; per-pattern summaries), SeamSweepRecovery (per cell: pins, closed-form outcome or bound; rescue ranks), and AngleSolverEngine (stage transitions with budgets). Every fix below was diagnosed from a trace file alone.

### 11.2 Goal A results: what the trace falsified and what shipped

The three levers proposed for the loopmm 6.5 s all had falsified premises:

- Contention was not the bottleneck: on a 12-thread machine the 9 pattern searches all ran truly concurrently, and per-node time was identical in the winner and the siblings. Capping the pattern pool at 3 STARVED the winning zx@31 branch (7th by root bound) and lost the landing entirely; full concurrency is restored and required.
- Ordering cells by restored violation loses the landing: the winning subtree's nodes restore at ~0.13 violation (the pad-hugging cells are exactly where Gauss-Newton stalls, see 11.4), so a violation penalty buries them. Falsified and reverted; node order stays best-bound (with a 2e-6-quantized depth-first tie-break, measured neutral).
- The 600 nodes are SLP-time-bound, not guidance-bound: 85% of the winning branch's wall clock was in-tree SLP (337 calls, ~36 LPs each), and the landing itself came from an in-tree phase-2 SLP ascent. What shipped: in-tree SLP budget 40/60 (final polish keeps 160/220) plus an in-tree trust-region floor of 1e-3 deg (`SlpSolve.optimizeBestEffort` overload with `trMinDeg`; the shrink-to-1e-7 tail gained ~1e-7 blocks per call). Result: 6.5 s to ~4.3 s, same landing objective.
- Pattern prediction from the incumbent's velocity profile is wired (`velocityProfile` + `patternScore` rank patterns by |v_axis(k)| distance to the threshold when a feasible seed exists) but is dormant on loopmm: the closed form has no feasible incumbent there, and the clamp-free fallback's profile does not graze the threshold. It orders submission only; it never gates a pattern out.
- `stopAtObjective` is now derived in the engine's exhaustive path from the user's same-axis objective cap (`objectiveCap - CAP_GAP_TOL`).

### 11.3 The miss triage: 3 of 4 solve blind

Recorded debug velocities (diagnosis only) classify the four misses: j828 = genuine suffix zeroing (X zeroed from tick 16-17 to the window end, plus a 0.00485 graze at 11); j717 = standing-still prefix (inert for the model) plus a Z window at ticks 14-15; j335 = scattered Z windows (6-10) plus single-tick grazes (vx@14 = 0.004992, vz@20 = 0.004872); j716 = a single Z zeroing tick at 10.

Empirically the existing blind suffix-pattern B&B already solves j335, j717, AND j828 byte-exact cold (first feasible in 0.15-0.85 s); the recorded window shapes were not required, alternative feasible paths exist inside the suffix family plus the free pattern. Their `dualrecovery` sidecars are flipped to `shouldSolve: true` with `bnbSeconds: 10`: the check now runs a bounded blind pattern-B&B (stop at first feasible) when the chain misses. A window-pattern family (per-axis zero-[k,k+len) enumeration, len 1..3, bounded per pattern) was built and falsified: on j716 the top window bounds are all late X windows, and folding the recorded wz@10 window by hand makes SLP stall FURTHER away (3.8e-3) than the clamp-free chain (1.1e-4). The zeroing tick is not j716's mechanism; j716 stays `shouldSolve: false` and is CLASS 3, genuinely global-stage (the fine-scale eight-constraint compromise of 10.4). The family was removed again; `HpkMissTriageScreen` keeps the probes.

### 11.4 Audited bugs fixed

- Exhaustive budget overrun (52 s vs 30 s): the anytime restart loop accumulates every batch's inits; on an infeasible result SolveCore's feasibility-only rescue pass re-ran ALL of them after the deadline had passed. The rescue now runs only if time remains and, under a deadline, on one batch of inits. j716 bench: 51.8 s to 30.2 s. Deadline-free paths are untouched (byte-identical).
- Window solver skipping the chain: multi-jump specs now always run `dualChain` and keep the better objective, gated to race-sized spans (`numTicks <= 64`); ungated it cost j001 (n=353) a full chain including a relaxation recovery and blew its solve budget.
- Failing closed form: the ascending margin ladder now breaks after 2 consecutive rungs with no exact-violation improvement (the m=110 ladders ran 8 rungs x 4 passes with violations plateauing after rung 3). A shortened alt-direction seed ladder was falsified (j344's reseed needs the late rungs) and reverted.
- Tight-spec B&B never restoring: root cause is corridor width, not the restore. A pad wall in the improving direction turns the reach ascent into threading a corridor narrower than the ~1e-4 sine-residual floor from outside (loopmm tight: 8.8e-5). Fix: `BoundPrunedRecovery` detects an objective-improving wall at the objective tick and, when the root-bound-to-wall corridor is under 2e-3, drops it from the search model and keeps it as the acceptance floor (offers still check the full spec; nodes bounding under the floor prune). Tight loopmm now lands the same -279.299912 point. The corridor gate matters: converting unconditionally broke j335, whose wide-corridor fallback relies on the wall steering the search.

### 11.5 Effort tiers reworked (same day)

The labels stopped describing the machinery, so the tiers were redefined (enum constants unchanged for save compatibility; only labels and wiring moved):

- **Fast** (FAST) = first byte-exact feasible solution, minimal latency. Stop-on-feasible is part of the tier's definition now, and a bounded (3 s, deadline-capped) first-feasible pattern-B&B rescue runs when chain and race both end infeasible on spans up to 64 ticks, so momentum-clamp jumps land on Fast too. Single-jump solve captures dropped from ~100 ms to ~30 ms (the race is skipped once the chain is feasible).
- **Optimize** (THOROUGH) = best result within one time-budget knob (`optimizeSeconds`, default 10 s, persisted in saves). Resolves to anytime Fast-sized race batches (16/4500, polish 4, THOROUGH polish schedule) plus the exhaustive multi-jump stages by default; stop-on-feasible is forced off. When the exhaustive stages are pending, the race is capped at 2/5 of the budget so they actually get time (before this, a deadline starved sweep/B&B/ILS to zero: the race consumed the whole budget, which is also why CUSTOM+budget+exhaustive never ran its stages).
- **Custom** unchanged; the stop-on-feasible toggle now only has effect there (the UI shows it forced-checked on Fast, forced-off on Optimize).

Exhaustive shares rebalanced per the section 11.2/11.3 measurements: seam sweep min(20%, 60 s cap), pattern B&B 3/4 of the remainder, ILS the rest. The chain skips the relaxation stage when under 3 s of budget remain. Re-baselined sidecars: j021 and j022-noland (the two objective-precision witnesses that need the race) moved to `"effort": "THOROUGH"`; j021 under Optimize lands within 6e-4 of the Wolfram reference. `BudgetResolutionTest` pins the per-tier resolution (budgets, deadlines, forced stop-on-feasible).

### 11.6 The d9 wave

30 easier hpk captures (d9) were wired into the dualrecovery gate the same day: 27 solve through the chain directly, 2 (j129, j135) through the bnbSeconds pattern-B&B fallback, and 1 is a new frontier miss. With a late d11 addition (j155, 4jmm, chain-solved via reseeded SLP) the gate stands at 56/58 sidecars (57/59 counting the loopmm landing). The miss, j318 (Waza -0 to Block Pane Postwalled, n=13), is the sharpest zeroing knife-edge in the library: the recorded path carries |vx| = 0.0049999356 into tick 6, 6.4e-8 UNDER the momentum threshold, so the human's basin requires holding a ~1e-7 validity corridor on the clamp boundary, three orders of magnitude below the ~1e-4 sine-residual certify floor. The blind suffix B&B finds no alternative basin in 10 s. Classified global-stage alongside j716; its sidecar stays shouldSolve: false without a bnbSeconds fallback (no point burning gate time on it).

### 11.7 Clamp-free census by tier

`HpkMissTriageScreen.clampFreeClosedFormCensus` (PKC_SCREENS) measures how many gate captures the pre-gh-204 fast path alone solves: the clamp-free dual margin ladder, no inertia folding, no SLP, no recovery, no search. Result over the 58 dualrecovery captures, split by hpk tier:

| Tier | Captures | Clamp-free CF | Full chain + B&B |
|------|----------|---------------|------------------|
| d9   | 30       | 8 (27%)       | 29 (j318 misses) |
| d10  | 20       | 5 (25%)       | 20               |
| d11  | 8        | 0 (0%)        | 7 (j716 misses)  |
| all  | 58       | 13 (22%)      | 56               |

Readings: up to d10, about a quarter of jumps are convex-easy (the LP optimum quantizes straight onto a feasible path); at d11 that population vanishes, so every d11 capture needs at least SLP. The solved-rate gradient across tiers (97/100/86%) is far flatter than the machinery-depth gradient: harder tiers are not much less solvable, they travel further down the chain before landing. The census also confirms the sine-floor band (clamp-free near-misses at 2.1e-6 to 5.8e-4 are exactly the ones SLP closes), that j318's clamp-free dual is unbounded at every margin (the knife-edge zeroing is required, not just helpful), and that today's stage-1 closed form solves 15/58 (folding adds j140, j248, j319, j425; the section 11.4 rung stall-break hands j321 and j345 to SLP instead, where they still solve).

### 11.8 CI core-count fix and the loopmm landing gate

The first CI run of the branch failed on j335: GitHub runners have 2-4 cores, so the B&B pattern pool (`cores - 2`) collapsed to a single thread and the first bound-ranked pattern hogged the whole search window, starving the winner (the same starvation mode section 11.2 measured for the pool-cap experiment). Fix: the pool has a floor of 2 threads, and when there are more patterns than threads each search gets a fair deadline slice (`window * threads / patterns`) instead of the shared deadline. On full-width machines nothing changes (slice inactive when threads == patterns). `:core:test -PtestCpus=N` pins `ActiveProcessorCount` on the test JVM to reproduce runner core counts locally; the gate is verified green at 12, 4, and 2 cores.

The ticket's last open test-plan item is also closed: `loopmm-3jump-lands` is wired into `problems/dualrecovery/` with `refObjective: -279.3` and `maxObjectiveGap: 0`. The check runs the chain, detects the target shortfall (the chain plateaus short of the pad), then runs the blind pattern-B&B with `stopAtObjective` at the pad edge and asserts the landing. Measured: lands -279.299912 in ~3.3 s at 12 cores, ~35 s of the 60 s budget at 2 and 4 cores.

### 11.9 Optimize dropped feasible results (user-reported, fixed)

Reported on a 1.12.2 save (trp): under Optimize the live tracker showed success in ~0.1 s, then the final result said no solution after the budget. Root cause: since the #201 stop-on-feasible rework, SolveCore returns its best-OBJECTIVE result even when infeasible, but the engine's race-vs-incumbent comparison still assumed both candidates were feasible and compared objectives only, so an infeasible race result with a longer (unrealizable) reach replaced the feasible chain result. The stale assumption predates this branch; Optimize made it visible because that tier always races feasible chain results to the full budget. Fix: the comparison gates on byte-exact feasibility first (a feasible incumbent is never traded for an infeasible reach), objectives break ties only within the same feasibility class. Regression capture: captures/trp-optimize-feasible-swap.json with a solve sidecar at THOROUGH (the sidecar's new optimizeSeconds field overrides the save's budget for test time). EngineFileScreen (PKC_SOLVE_FILE=path, optional PKC_SOLVE_EFFORT / PKC_SOLVE_TIMEOUT_MS) drives the live engine on any save file headlessly; it is how the report was reproduced and verified.

