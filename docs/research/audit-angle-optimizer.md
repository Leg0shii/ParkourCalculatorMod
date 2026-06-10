# Code audit — angle optimizer (closed-form dual + receding horizon), 2026-06-10

**Status update (same day):** findings 1a, 1b, 1c, 1e and 1f are FIXED on this branch (one commit
each, suite green after every one; j001 stays in its 143–161 ms baseline band). 1d remains
documentation-only by design. Section 2's speed directions and section 4 remain open.

**Decision (2026-06-10): the speed headroom is deliberately deferred.** Current performance is
considered sufficient — ~150 ms for a from-scratch 354-tick / 30-jump solve is interactive for an
off-thread one-shot action, and single jumps sit at the structural floor (one dual solve +
certification, 14–130 µs). The remaining 2–4× on long runs (cross-window warm-started duals, §2a)
does not change how the tool feels and carries real implementation risk (see the three failed
prototypes in §2). Revisit when any of these become true:

1. **Solving becomes an inner loop** — block-derive / auto-routing / batch re-solving issuing many
   solves per interaction, where 150 ms × N starts to bind. Then §2a is the item to build.
2. **Runs grow several-fold** past ~350 ticks, scaling 150 ms toward seconds.
3. **An F (facing) constraint is used on a multi-jump run.** This is a CAPABILITY gap, not speed:
   today it disqualifies every window from the fast path and the full-span fallback cannot
   realistically converge, so such a spec is effectively unsolvable. Fix via facing walls in the
   closed form (§4.2) the moment this need appears.

`stepRange` in the polish (§2b) is an enabler for the Phase-2 global objective ascent and should be
wired up as part of that work, not before.

Audited at `65701fc` on `claude/eloquent-newton-7ony9j`. Scope: the solve pipeline
(`ExactJumpModel`, `JumpLinearModel`, `CostateDualSolver`, `ClosedFormSolve`, `LongRunSolver`,
`SolveCore`, `CmaesJumpHarness`, `BucketAscentPolish`, `JumpConstraintCompiler`,
`AngleSolverEngine`), with a lighter pass over `SweptCollision`, `BlockSolver`,
`FreeSpaceDecomposition`. All claims below were either verified by reading the float chains against
the MC 1.8.9 semantics they port, re-deriving the math, or measured with throwaway experiments run
against the real suite (all reverted; nothing in this audit changes behavior).

**Baseline reproduced**: 35/35 tests green. j001 (354 ticks, 30 jumps, 81 constraints) solves in
143–157 ms across three runs; j002/j003 solve all four Solve-For directions in 15–36 ms; the
single-jump closed form runs 11–136 µs per solve. The "never report a false success" invariant holds
on every path (closed form re-certifies byte-exact; `LongRunSolver` re-verifies the full
concatenation; `BlockSolver.ok()` requires swept-clean + landed; the CMA-ES path reports met/total
honestly).

## Verdict

No bug was found that breaks the shipped behavior on the tested paths. The math is sound: the
linear-model coefficients `C(s,k) = (S[k]−S[s])/fPre[s]` follow exactly from unrolling
`v_{t+1} = (v_t + u_t)·f4_t` and are numerically stable by construction (the numerator carries
`fPre[s]`, so `coef ≤ Σ 0.91^k ≈ 11` — no exponential blowup at 354 ticks). The zero-duality-gap
argument is correct generically: the inner `max_{|u|=m} g·u = m‖g‖` equals the max over the convex
relaxation `|u| ≤ m`, so the dual of the nonconvex problem is the dual of a convex program; when no
costate vanishes at the optimum the recovery is the unique relaxed maximizer and lies on the circle.
The degenerate case (vanishing costate, where a true gap can exist) is handled by the
objective-direction default plus byte-exact re-certification, which converts a potential wrong answer
into a fallback. The byte-exact model's float chain matches the 1.8.9 port it claims (both distinct
yaw→rad casts, sine-table generation order, per-axis vs combined inertia thresholds, ground/air accel,
pre-gravity move, gravity-then-drag carry).

What the audit did find: three latent correctness gaps (none active on current fixtures), one
measured large source of wasted work in the long-run path (with three prototyped fixes — two of which
made things *worse*, documented below so they aren't retried), and a handful of cheap wins and
directions.

## 1. Correctness findings (none currently firing, ordered by severity)

### 1a. The long-run result is verified on facings Apply cannot exactly reproduce (latent)

`runJob` verifies and reports the receding-horizon result on the seam-chained `directFacings`
(`AngleSolverEngine.runJob`, the `directFacings != null` branch), but `apply()` writes float *deltas*
recomputed from `wrapAll(directFacings)`; the game then realizes
`toGameFacings(wrapAll(directFacings))`. For the normal path this round-trip is bit-exact by
construction (the deltas are derived from the same wrapped-abs source the scoring used). For the
long-run path it is *not guaranteed*: re-deriving a float delta from two realized float facings and
re-accumulating reproduces the original chain only while `ulp(delta) ≤ ulp(facing)` — it can re-round
when consecutive facings straddle scales (a facing near 0° reached by a multi-degree delta). A 1-ulp
yaw change flips a sine bucket with probability ~0.3 %/tick, and with `FEAS_TOL = 0` walls a flipped
bucket can clip.

Measured on j001/j002/j003 (353/189/176 facings): the replay is bit-identical — zero facing-bit
diffs, zero bucket diffs, zero position delta, replayed violation 0. So this is latent, not active
(these runs keep |facing| ≫ |delta|). The cheap hardening: verify (and report) the path of
`sc.toGameFacings(Angles.wrapAll(directFacings))` — i.e. certify the chain Apply will actually
realize — instead of `directFacings`. One extra forward; if it ever differs and violates, fall
through to the existing fallbacks rather than report success.

### 1b. Result panel evaluates F-mode constraints on a different facing array than the solver

The solver enforces F-mode constraints on the *game facings* (`CmaesJumpHarness.penalized` evaluates
`c.penalty(gf, …)`), but `buildResult`/`findValue` reads `yaws[segTick]` — the wrapped-abs decision
variables — and `satisfied()` gates GE/LE at strict `FEAS_TOL = 0`. The two arrays differ by float
accumulation (~1e-6..1e-5 deg), so a solution hugging an F wall on the feasible side of `gf` can be
reported "not met" (hence `success=false`) by an epsilon on `yaws`. Position constraints are immune
(both sides read the same `path`). Low severity — F walls are rare and rarely hugged — but it
violates the "panel reports exactly what the solver certified" contract. Fix: `findValue` for F
should read the same `toGameFacings` array used for scoring (or `satisfied` should compare with the
wrapped difference against `gf`).

### 1c. EQ constraints silently disable the fast path and the polish

With `FEAS_TOL = 0`, an equality's residual `|pos − rhs|` is byte-exact-zero only when `rhs` is
exactly bucket-achievable. Verified by experiment: the closed form *does* solve an EQ spec when the
target is achievable (the doc comment "does not apply (facing/equality walls)" is wrong about
equalities — they compile to free multipliers and work); for a hand-typed `rhs` it predictably fails
certification at every margin, then the CMA-ES path's `filterFeasible` can also never accept
(`maxViolation` includes `|eqResidual|`), so: no polish ever runs, the rescue pass always runs, and
"success" comes only from the panel's separate `MET_TOL = 1e-3` for EQ. Net effect: any EQ constraint
quietly degrades a spec to worst-case solve time and unpolished output. Suggest treating UI EQ as a
±`MET_TOL` range internally (two walls), which restores both the fast path and the polish and matches
what the panel already considers "met". The same applies to `JumpLinearModel.compileWall`'s trivial-EQ
check.

### 1d. Two scale-dependent magic numbers in the dual (document, don't change)

- `DIVERGE_PGRES = 4.0` (blocks) gates the stall bail. The projected-gradient residual at λ=0 is the
  wall violation of the unconstrained optimum, which grows with window travel distance; a future
  problem class (longer windows, faster movement) could stall *under* convergence at >4 and trip it.
  Empirically the gate is load-bearing in the other direction too: an experiment removing the floor
  (bail on stall at any residual) **broke j020's closed-form solve** (a genuinely slow, flat-direction
  crawl that does converge) and made j001 *slower* (stalled rungs hand worse warm starts to the next
  rung). Keep the gate; consider making it relative to the initial residual if it ever misfires.
- `LAMBDA_CAP = 1e9` declares divergence in absolute multiplier units; fine at current scales.

### 1e. Margin ladder: `continue` after an unbounded dual should be `break`

`ClosedFormSolve.optimize` line ~65: margins only *tighten* inequality walls (`bPrime = bBase −
margin`; equalities unmargined), so primal infeasibility is monotone along the ladder — once
`solver.solve` returns null (λ past `LAMBDA_CAP`), every later rung must also return null. Each
futile rung still costs a divergence detection (~40 line-search probes × O(mn)). Strictly safe,
small win on infeasible specs.

### 1f. Trivial-constraint tolerance leaks a guaranteed-futile fallback

`JumpLinearModel.compileWall` accepts a decision-independent (tick-0 / t1==t2) constraint violated by
up to 1e-6 as "ok", but downstream certification is `FEAS_TOL = 0`, so a tick-0 constraint violated
by 1e-9 burns the full ladder *and* the CMA-ES multistart before reporting the unavoidable "no
solution". Tighten the trivial check to exact (`rawBound >= 0`) so unfixable specs fail in
microseconds. (Interacts with 1c for EQ-at-tick-0.)

### Checked and found correct (so nobody re-audits them)

- `McSineTable` generation and lookup are bit-identical to MC (expression order included); the two
  distinct rad casts (`0.017453292F` in `jump()` vs `yaw·πF/180F` in `moveFlying`) are both ported.
- Per-axis (1.8/1.12) vs combined-XZ (1.9+) inertia selection; threshold applied to the post-friction
  carry at top of tick; jump impulse only when grounded; strafe disabled on grounded jump ticks in
  both the exact and the linear model.
- `toGameFacings` is the bit-exact model of Apply for the normal (wrapped-abs) path; locked rows
  resync the float chain in both.
- Window slicing in `LongRunSolver`: seam constraints are enforced in the window that commits them
  and re-checked as trivial tick-0 constraints against the chained seed in the next; velocity pairs
  straddling a commit seam stay enforced; committed coverage is gapless (`bounds` always ends at n).
- The dual's convergence tests: `U_TOL` catches λ wandering in the null space of Aᵀ while the
  recovered inputs are stationary; free-set selection includes eq and `λ>0 or grad<0`; Cholesky on
  the damped PSD Hessian with adaptive Levenberg ρ is sound.
- Determinism: fixed seeds everywhere (CMA-ES per-start, polish restarts); parallelStream results
  are order-preserving.
- Worker handoff: single volatile `pending` publish, cancel token checked before publishing.

## 2. Where the time actually goes (measured), and what didn't work

Enabling the existing `LongRunSolver.DEBUG` + `ClosedFormSolve.DEBUG` on j001: the run is 8 window
solves ≈ 100 % of the 150 ms. Per window, the ladder's small rungs grind `MAX_ITER = 100` without
certifying — **no window certified below margin 6e-4** (certifying rungs: 6e-4–2.5e-3, converging in
10–53 warm-started iterations), while rungs {0, 1e-4, often 3e-4} cost 90–100 iterations each. Of
~2200 total dual iterations, only ~160 belong to certifying rungs. The sub-6e-4 rungs cannot certify
at window scale because sine-bucket quantization noise accumulates across 100+ ticks past those
margins.

Three prototyped fixes, with results (all reverted):

| prototype | j001 | j002/j003 | verdict |
|---|---|---|---|
| lead-in windows start ladder at 6e-4 (static), full ladder on failure | 109–111 ms (−28 %) | some directions +60 % (their windows certify cheaply at low rungs) | not a net win |
| stall-bail at any residual (drop the `DIVERGE_PGRES` floor) | 199 ms (worse) | mixed | **breaks j020** closed form; rejected |
| per-run rung memory (start at last certifying rung) | 217 ms (worse) | worse | cold mid-ladder starts are expensive; the ratchet over-tightens and triggers double fallback |

The instructive negative result: **the warm-start chain is doing real work** — a cold dual solve at
*any* rung costs ~100 iterations on these degenerate windows, so skipping rungs trades cheap warm
iterations for expensive cold ones, and which rung profile wins is problem-dependent (j001's windows
certify high, j002's low).

### 2a. The fix that follows from the data: cross-window warm starts (largest headroom)

Consecutive windows share ~70 % of their walls (slide 3 of 10 jumps), and wall identity is stable
(name + absolute tick). Seeding window k+1's dual at each rung from window k's multipliers for the
shared walls (zero for new ones) preserves the cheap warm chain *across* windows instead of paying a
~100-iteration cold start per window per rung. Combined with starting lead-in windows mid-ladder
(safe once warm), the productive-iteration count suggests j001 lands around 30–60 ms (2.5–5×).
Moderate effort: `ClosedFormSolve` constructs a fresh `CostateDualSolver` per call; it would need an
optional λ-by-wall-name seed.

### 2b. `ExactJumpModel.stepRange` is implemented, documented for exactly this, and unused

`BucketAscentPolish.block1/block2` and `CmaesJumpHarness.polish` re-forward from tick 0 for every
single-tick perturbation; `stepRange(…, from, path)` recomputes only `[from, n)` bit-identically.
For single jumps (n≈10) it's irrelevant; for the CMA-ES fallback and any future global polish on
354-tick runs it halves (block1, average) or better (late ticks) the dominant cost. Wiring it in is
mechanical: keep a scratch `ForwardPath` with velocity arrays per candidate, restore the perturbed
facing on rejection.

### 2c. Smaller, safe wins

- `buildHessian`: cap the inner t-loop at the last tick either wall couples (coef is 0 for
  `s ≥ wallTick`; precompute per-wall `lastTick`). The Hessian is the dominant per-iteration cost at
  window scale (m²n); this saves ~half of it on average wall pairs.
- Reuse `JumpLinearModel` + compiled walls across the four direction retries
  (`LongRunSolver.solveWindow`, `runJob` alternates): only the objective vectors change per direction.
- 1e's `break`, 1f's exact trivial check.
- Allocation churn on the CMA-ES path: each evaluation allocates `wrapAll` + `toGameFacings` + six
  `double[n+1]`s in `ForwardPath`. Thread-local scratch would cut tens of MB/s of garbage during a
  multistart; only worth it if the fallback path's latency ever matters.

## 3. Can it be simplified without losing speed?

Mostly no — and that is praise. The layering (byte-exact forward / linear structure / convex dual /
margin-ladder certification / receding-horizon decomposition / CMA-ES net) gives each file one job,
and the three places that look duplicated are deliberate and documented (`wrap` vs `wrapDelta`;
ladder-margin application inside the dual vs `compileWall`'s margin parameter; the panel's `MET_TOL`
vs solver `FEAS_TOL`). Two genuine simplifications:

- `JumpPhysicsInputs.jumpTick` survives only as a fallback and for `BlockSolver`'s launch footprint;
  collapsing to the mask plus a `firstJumpTick()` helper removes a dual-source-of-truth field.
- `ClosedFormSolve`'s doc comment (and `runJob`'s) should stop claiming equality walls are
  unsupported (1c); the *behavioral* simplification is to make EQ a ±tol range and delete the
  special-casing discussion entirely.

The one structural simplification worth *considering* is folding the single-jump path into
`LongRunSolver` (a 1-jump run is one final window with the real objective and full ladder — exactly
`ClosedFormSolve` + direction retries). That deletes the `countJumps` branch in `runJob` at zero
behavioral change, at the cost of routing the microsecond path through one more call layer.

## 4. Promising directions

1. **Cross-window warm-started duals** (2a). The data says this is where the next 2.5–5× on long
   runs lives, and it needs no new math.
2. **Facing walls in the closed form.** An F-mode constraint is a sector constraint on the input
   *direction*; the inner maximization over `{|u| = m, û ∈ sector}` is still closed-form (clamp the
   costate direction to the nearer sector edge), the dual stays convex, and the zero-gap argument
   survives (a linear max over the convex hull of a circular arc is attained on the arc). Today one F
   constraint anywhere in a span disqualifies the entire fast path — for a long run that means the
   hopeless 354-dim CMA-ES. This is the only constraint class the dual can't express, and it can.
3. **Phase-2 objective polish for long runs** (the known remaining work): rather than a 354-dim
   bucket ascent, re-solve the *final k windows* with progressively earlier seams under the committed
   prefix — each re-solve is a window dual, and the objective lives almost entirely in the tail.
   `stepRange` (2b) makes the bucket-ascent variant viable as the finishing step.
4. **EQ as ranges** (1c) — small, unlocks fast path + polish for a whole constraint class.
5. **Direction-parallel window solves**: lead-in windows accept any feasible direction, so racing the
   four objectives and taking the first certificate is a latency (not throughput) win on windows
   whose first direction fails; j001 never direction-fails, so measure on a fixture that does before
   bothering.
6. **A perf canary**: `problems/solve/j001.expect.json` allows 50 s. Now that ~150 ms is stable, a
   second long-run fixture with `maxSolveMs` ≈ 2 s would catch a silent return of the ~2.1 s
   pre-receding-horizon behavior without flaking CI.

## How this was validated

- Full suite at baseline ×3 (35/35 green; j001 143/154/157 ms).
- Replay experiment (1a): solver-returned chained facings vs `toGameFacings(wrapAll(·))` on
  j001/j002/j003 — bit-identical, violations 0 on both.
- EQ experiment (1c): closed form on j004 + an EQ wall at a bucket-achievable target — certifies
  (in 1.46 ms; the eq multiplier makes the dual crawl); unachievable targets cannot certify by
  construction.
- DEBUG-instrumented j001 run (section 2's iteration/margin table).
- Three prototype patches benchmarked and reverted (section 2's table).
