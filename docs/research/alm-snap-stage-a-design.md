# Stage A design note: AlmBfgsCore + SnapRepairPolish

Status (2026-08-21): the machinery this note designed was removed in PR #382 (issue #380); the ALM+Snap research line concluded per user ruling 2026-08-21. Survivors in live code: FacingLattice, the JumpLinearModel forwardMag/strafeMag/boostAt accessors, the item-17 sprint/amp factor-lag fix, and the translation-elimination concept (now FreeStartSolve.bestTranslate). The rest of this doc is the dated historical record.

Written 2026-07-08, revised same day after adversarial review (section 8 records every objection and its disposition). Implements handoff nix-solver-handoff.md section 2b items 1-3. Normative algorithm reference: docs/research/sheepram-port-spec.md (source-verified, wins over any summary). Interface facts were verified against the repo by a scouting pass and re-verified by the adversarial reviewer on 2026-07-08.

Goal: a two-phase solver stage, continuous ALM+BFGS on the smooth compiled model followed by discrete snap/repair/polish over the sine-LUT lattice graded by the byte-exact ExactJumpModel (gates + sprint lag included), that beats Sheepram on the section 5 benchmark suite. Faithful port first, upgrades second.

## 0. Scope and non-goals

In scope (Stage A): pinned-start solves, X/Z model, per-axis legacy gates and modern combined gate via fixed zeroing pattern, F-mode constraints as linear theta terms, multistart from constant-angle sweep PLUS cold warm-seed generators already in the engine (CMA basins et al, per mission item 3; cold = generated from the problem, never from a known answer), standalone benchmark harness.
Non-goals (Stage B or later): free-start translation, the warm-diversity TOURNAMENT across generators with racing/budget allocation, model-guided k-opt, gate-pattern enumeration, engine parallelism, MIQCP.

## 1. Components and interfaces

New files, all in core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/ (Java 8, no MC imports, no code comments):

### 1.1 SmoothJumpProblem (compiler + evaluators)

Compiles a JumpSpec plus a fixed zeroing pattern into per-tick coefficient form, the shared substrate for the ALM smooth evaluator, the analytic gradient, and the discrete fast grader.

```java
public final class SmoothJumpProblem {
    public static SmoothJumpProblem compile(JumpSpec spec, boolean[] zeroX, boolean[] zeroZ);
    public int n();
    public Term objective();
    public double objectiveSign();
    public List<Term> ineq();
    public List<Term> eq();
    public double smoothValue(Term t, double[] thetaRad);
    public void smoothGradient(double[] thetaRad, double[] lambda, double[] nu, double pen, double[] gOut);
    public double fastValue(Term t, float[] gameFacingDeg);
}
```

Term layout (one per constraint and one for the objective): `double constant; double[] sinC, cosC, boostSinC, boostCosC, thetaC; String name; boolean isEq;` canonicalized to g(theta) <= 0 for ineq and h(theta) = 0 for eq, mirroring Sheepram's Compiled_Expr (port spec section 4) extended with the boost pair.

Construction path: `new JumpLinearModel(spec.asScenario(), zeroX, zeroZ)` (ctor JumpLinearModel.java:71), then per constraint `compileWall`/`compileWalls` (JumpLinearModel.java:203-258) giving Wall {axis, coef[], bPrime} with couplings and GE/LE sign already folded into coef[]; the objective is compiled the same way with coef[s] = coefAxis(axis, s, objTick) and constant from constPos(objTick, axis) (JumpLinearModel.java:168-182).

Per-tick cartesian input form: the exact model computes addX = q*cosD - p*sinD and addZ = p*cosD + q*sinD (ExactJumpModel.java:172-173, 188-189) where p/q are the forward/strafe magnitudes. CRITICAL (adversarial finding 2): JumpLinearModel.pConst ALREADY INCLUDES the sprint-jump 0.2 boost at grounded sprint-jump ticks (JumpLinearModel.java:34, :128), and pConst/qConst are private. Required additive accessors on JumpLinearModel: forwardMag(int t) returning the boost-EXCLUDED forward magnitude, strafeMag(int t), and boostAt(int t) returning 0.2 on grounded sprint-jump ticks else 0. The mapping is then, for axis X: cosC[s] += wall.coef[s]*strafeMag(s), sinC[s] -= wall.coef[s]*forwardMag(s), boostSinC[s] -= wall.coef[s]*boostAt(s); for axis Z: cosC[s] += wall.coef[s]*forwardMag(s), sinC[s] += wall.coef[s]*strafeMag(s), boostCosC[s] += wall.coef[s]*boostAt(s). Smooth evaluation sums main and boost pairs at the same continuous angle; fast evaluation looks the two LUT pairs up separately. The boost rad cast differs from the movement cast on the LEGACY path only (ExactJumpModel.java:126 vs :185; on modern, :119 and :169 are identical casts, so the modern boost pair is redundant but harmless).

F-mode constraints: compileWall rejects Mode.F (JumpLinearModel.java:204). SmoothJumpProblem compiles them itself as thetaC terms. thetaC is stored in DEGREE units; smoothValue/smoothGradient convert thetaRad to degrees for these terms, fastValue consumes gameFacingDeg directly (adversarial finding 9). The F residual in the exact grader is wrapped to (-180,180] (JumpConstraintCompiler.java:103), so a GE/LE F constraint is a wrapped band; the linear term models the local half-line near the wrap basis only (re-based against the seed, re-based again if an iterate moves more than 90 deg; counter f_rebase). Documented limitation; no F constraints exist in the benchmark suite.

Sprint-factor lag and slip MUST be baked into the per-tick constants by JumpLinearModel.precompute (JumpLinearModel.java:103-119) identically to ExactJumpModel. This was NOT true when this note was first written (the "nothing extra to do" claim here was wrong): precompute selected the airborne air-accel with the tick's OWN sprint (sc.sprintAt) and the grounded accel amplifier with the tick's OWN amplifier (sc.speedAmplifierAt), while ExactJumpModel reads the LAGGED sc.factorSprintAt / sc.factorAmpAt (MC recomputes the movement factor after the move, so it lags one tick; ExactJumpModel.java:143-147). Fixed 2026-07-08 by switching those two selector reads to the lagged accessors; slip and everything else are unchanged. See section 8 item 17 for the measured impact. Equality terms: the engine never emits Cmp.EQ (UI EQ becomes a +-MET_TOL corridor, AngleSolverEngine.java:1576-1584, MET_TOL = 1e-4 at :61), so eq() is empty in practice; the machinery is kept for spec-level generality with exact tolerance 1e-9.

MANDATORY verification inside compile (assert + counter): evaluating every Term via fastValue at the game facings of 32 random yaw vectors must match JumpConstraintCompiler evaluate/slack against an ExactJumpModel forward within 5e-6 on gate-quiet draws (gate-quiet = the exact path's gate events match the compiled pattern; mismatched draws are re-drawn, count logged). This is V1 in section 4 as a unit test; a 4-draw spot version runs at every compile behind the debug flag.

### 1.2 AlmBfgsCore (continuous phase)

```java
public final class AlmBfgsCore {
    public static final class Config {
        public int maxOuter = 25;
        public int maxInner = 80;
        public double feasTol = 1e-6;
        public double gradTol = 1e-6;
        public double penInit = 1.0;
        public boolean patternRefresh = true;
        public boolean binaryZoom = false;
    }
    public static final class Result {
        public final double[] thetaRad;
        public final double smoothViol;
        public final double smoothObjective;
        public final int outerIters;
        public final int patternFlips;
    }
    public static Result solve(JumpSpec spec, double[] seedThetaRad, Config cfg,
                               long deadlineNanos, AtomicBoolean cancel);
}
```

Algorithm exactly per port spec sections 1-3 with two sanctioned deviations:
- feasTol 1e-6 (theirs 1e-5), gradTol as theirs (1e-6 on the norm).
- Line-search zoom uses quadratic/cubic polynomial interpolation with bisection safeguard (fallback to pure bisection when the interpolant leaves [lo,hi] or shrinks the interval by < 1e-3). Config.binaryZoom = true restores the port's pure bisection for falsification runs (adversarial finding 12). Everything else (c1 = 1e-4, c2 = 0.9, alpha0 = 1, 20 bracket + 20 zoom caps, nonzero alpha on exhaustion) identical to the port spec.

Everything else faithful: ALM multiplier updates lamb = max(0, lamb + pen*g), nu += pen*h; pen *= 2 when max_vio > 0.5*prev_max_vio; max 25 outers with break at feasTol; Hestenes-Powell-Rockafellar augmented Lagrangian value/gradient; dense inverse-Hessian BFGS with identity init, gradient-norm exit, steepest-descent fallback on non-descent directions without H reset, curvature skip when (s.y)^2 <= 1e-24*(s.s)(y.y) (port the code, not the comment; port spec section 9 item 2). The minimized objective is objectiveSign()*rawObjective (MAX negates).

Gate handling (revised per adversarial finding 5): the zeroing pattern is derived from the EXACT model, not the continuous forward: run ExactJumpModel.forward on the seed yaws and mark tick t gated on an axis iff the stored carry velocity at index t is below the threshold (the gate at ExactJumpModel.java:93-101 zeroes exactly that carry; ForwardPath.velX/velZ expose it), matching byte-exact gate events at the seed by construction. JumpLinearModel.zeroingPattern (JumpLinearModel.java:286-307, continuous) is not used. The problem is compiled against that pattern with consistency walls from velocityWalls (JumpLinearModel.java:260-284) for the zeroed ticks only. patternRefresh: after ALM converges, re-derive the pattern from an exact forward at the solution; if changed, recompile and re-solve warm, at most 3 refreshes, keep the best-by-(viol,objective); counter pattern_flips. Crossing into a DIFFERENT gate basin than any seed reaches remains out of scope (Stage B enumeration, handoff Direction 6); the design accepts that Stage A's gate fidelity enters at seeding and acceptance, not at basin search, and the counters expose when that binds.

Variable domain: thetaRad = absolute yaw in radians per tick, all n ticks live (verified correct: the objective tick position depends on every input; do NOT port Sheepram's 1..n-2 restriction). Thetas wrapped only at outer-iteration boundaries.

### 1.3 SnapRepairPolish (discrete phase)

```java
public final class SnapRepairPolish {
    public static final class Config {
        public int topK = 32;
        public boolean cooking = false;
        public double fastErr = 5e-7;
        public double maxDrop = 1e-5;
        public int worseAcceptThreshold = 256;
        public int maxDownHills = 128;
        public double candGateWiden = 1.0;
    }
    public static final class Result {
        public final double[] absYawsDeg;
        public final double exactViol;
        public final double exactObjective;
        public final boolean feasible;
    }
    public static Result run(ExactJumpModel model, JumpSpec spec, double[] seedAbsYawsDeg,
                             Config cfg, long deadlineNanos, AtomicBoolean cancel);
}
```

Objective sense (adversarial finding 3): all internal comparisons (improveQ, good-candidate Polish margin, worse-move bound, best tracking, multistart winner) use the SIGNED objective objectiveSign()*raw so the ported minimize-only logic is correct for MAX problems; Result.exactObjective reports raw.

State space: the per-tick game-facing floats. Entry converts the seed once via the canonical chain gf = spec.asScenario().toGameFacings(Angles.wrapAll(seed)) (JumpPhysicsInputs.java:137-156). All search moves mutate the float array directly. Exit reconstructs absolute yaws that reproduce the final floats EXACTLY under toGameFacings: locked ticks take abs = (double) gf[k]; unlocked ticks take abs[k] = abs[k-1] + (double) d where d is the float delta the search applied, so (float) wrapDelta(abs[k]-abs[k-1]) == d by construction (verified by the reviewer to survive wrapAll for bounded facings). The exit assert re-runs toGameFacings on the reconstructed yaws and requires bit-equality with the search floats. Per adversarial finding 7 this is NOT a hard error: a failing candidate is rejected (counter reconstruct_fail); if the final winner fails, the stage returns infeasible-with-diagnostics instead of throwing. The harness asserts max |game facing| < 10000 deg per fixture and logs the max seen.

Lattice definition (where we are more faithful than Sheepram): the trajectory depends on tick t's float facing only through its LUT lookups; up to two rad casts per tick on legacy (movement, ExactJumpModel.java:185; jump-boost, :126, grounded sprint-jump ticks only; on modern the casts coincide), each feeding a sin index (int)(rad*10430.378F)&0xffff and a cos index (int)(rad*10430.378F+16384.0F)&0xffff (McSineTable.java:19-25) whose boundaries do NOT coincide in float (reviewer-confirmed numerically: a cos boundary can fall strictly inside one sin bucket). Helper:

```java
final class FacingLattice {
    static int sinIndex(float gfDeg, boolean modern, boolean boostCast);
    static float[] cellRepresentatives(float gfDeg, int sinDeltaLo, int sinDeltaHi,
                                       boolean modern, boolean jumpBoostTick);
    static float stepToSinBucket(float gfDeg, int targetIndex, boolean modern);
}
```

cellRepresentatives returns one representative float per DISTINCT joint cell (movement sin idx, movement cos idx, boost sin idx, boost cos idx) reachable within the given sin-bucket delta range around gfDeg, found by walking the sin-bucket span and splitting at cos/boost boundaries (bounded ULP walk; assert every representative reproduces its intended joint cell; on failure skip candidate, counter cell_miss). A "+-1 bucket move" maps to "all joint cells inside sin buckets +-1"; usually 1 cell per bucket, occasionally 2-3. Property tests must cover large magnitudes (to +-20000 deg) where float degree spacing approaches bucket width, characterizing where representatives stop existing.

Search structure faithful to port spec section 5 on this lattice: Repair/Polish modes with mode flip on first exact-feasible accept; 1-opt rounds scanning every tick, both directions, fast-graded, top-K kept sorted, exact-checked best-first, Repair fallback to best fast-improving neighbor; 2-opt with the exact 20-delta set over sin buckets (each delta expanded to its joint cells), Regular = each pair once per round shuffled, Cooking = 512*n random pairs per round with replacement, worse moves only in Polish+Cooking after 256 attempts in the round, whole-phase budget 128, each worse move exact-feasible with signed-objective increase strictly under 1e-5; termination when a round accepts nothing in each phase.

Graders:
- Fast: SmoothJumpProblem.fastValue per Term at the LUT float pairs of the candidate's facings, f64 accumulation. Feasibility standard per port spec section 6: ineq viol > fastErr infeasible, eq viol > 1e-5 infeasible, Repair rejects any violation_sqr > 0, good-candidate gate viosqr_tol = max(1, #cons)*(fastErr*candGateWiden)^2. candGateWiden (default 1 = faithful) widens the gate on razor runs because the fast-model error (~5e-7..5e-6, double vs float magnitudes) is a meaningful fraction of 1e-4 razor margins and can prune true candidates (adversarial finding 11); razor benchmark runs set it 4-8 with topK raised accordingly, recorded in the report. Incremental delta-evaluation per single-tick change (O(#cons) per candidate) with a full-recompute cross-check assert behind the debug flag.
- Exact: ExactJumpModel.stepRange from the earliest changed tick on a scratch ForwardPath whose arrays are copied from the incumbent's before stepping (reviewer-verified equivalent to a fresh forward; Y is yaw-independent), then JumpConstraintCompiler.Compiled.maxViolation(gameFacings, path) (JumpConstraintCompiler.java:24-29) and path.getPos(objTick, axis). Acceptance standard: ineq STRICT (viol <= FEAS_TOL = 0.0, AngleSolverEngine.java:59), eq |h| <= 1e-9 (empty in practice, see 1.1). Every 64 accepted moves plus at exit, a full forward from tick 0 must reproduce the incremental path bit-exactly (counter resim_drift, hard error: it means a code bug, not a data condition).

### 1.4 AlmSnapStage (driver)

```java
public final class AlmSnapStage {
    public static SolveOutcome solve(ExactJumpModel model, JumpSpec spec, List<double[]> warmSeedsAbsDeg,
                                     int constantSeedCount, boolean cooking,
                                     long deadlineNanos, AtomicBoolean cancel);
}
```

Multistart: constantSeedCount constant-angle seeds 360*i/N degrees across all ticks (port spec section 8) plus supplied warm seeds (engine-generated cold warms are Stage A scope per mission item 3; the tournament with racing/budgets is Stage B); each runs AlmBfgsCore then SnapRepairPolish on the ALM output; winner by (exact feasibility, signed exact objective, exact viol), the solution_better order (port spec section 8). Output: absolute wrapped yaws satisfying the engine result contract (replay via toGameFacings feasible). Engine wiring (a stage in runJob plus dualChain membership) happens only after the section 5 benchmarks validate the standalone stage; the harness drives AlmSnapStage directly until then.

## 2. Debug instrumentation (rule: instrument, never guess)

All tags gated by env var PKC_ALM_DEBUG=1 (read once, static final). One line per event, machine-greppable, stable prefixes:
- [DBG-alm1] per ALM outer: outer idx, pen, max_vio, worst constraint name and value, inner iters used, exit grad norm, last alpha, step norm, pattern flips so far.
- [DBG-bfgs1] per inner exit reason: gradTol | maxInner | lsFail | cancel, plus counters ls_zoom_exhausted, sd_fallback, curv_skip, h_reset.
- [DBG-srp1] per discrete round: mode (Repair/Polish), phase (1opt/2opt), round idx, candidates generated, fast-feasible count, exact-checked count, exact-check failures with failing constraint name and violation value, accepted move (ticks, sin-bucket deltas, joint-cell ids), signed objective and viol before/after.
- [DBG-srp2] counters at exit: snap_degradation (fast viol after snap minus smooth viol), fastexact_disagree (count of |fast - exact| > 5e-6 per constraint, with names), cell_miss, reconstruct_fail, resim_drift, down_hills used, gate_pattern_mismatch (exact-path gate events differing from compiled pattern).

The benchmark harness prints `applied: <VAR>=<value>` for every env-driven variant it honors and the runner must grep for these lines before trusting a run (handoff section 4 rule).

## 3. Failure-mode lists (each has an assertion or logged counter; component does not ship without it)

AlmBfgsCore:
1. Line search returns non-finite or non-positive alpha: assert finite, alpha > 0 (port guarantees nonzero); counter ls_zoom_exhausted for exhaustion returns.
2. BFGS curvature-skip loop (H never updates, inner spins): counter curv_skip; if curv_skip > maxInner/2 in one inner run, log [DBG-bfgs1] warn.
3. Non-descent direction every iteration (H corrupt): counter sd_fallback; if sd_fallback > 10 consecutive, reset H to identity once (counter h_reset) then continue.
4. Analytic gradient wrong: debug-flag check on the first iterate of each solve, central finite difference vs smoothGradient, relative error > 1e-6 is a hard assert (counter grad_check_fail).
5. Zeroing-pattern oscillation across refreshes: cap 3 refreshes, keep best, counter pattern_flips.
6. ALM stall (max_vio non-decreasing while pen saturates): detect pen > 1e12 with max_vio > 10*feasTol, log worst constraints with values, exit early.
7. Theta drift breaking F-constraint wrap basis: rebase check per outer, counter f_rebase.
8. Smooth-feasible but exact-infeasible at handoff to snap: metric smooth_exact_gap logged always; ASSERTED < 1e-2 on the V3/V4.5 gates (adversarial finding 6: was log-only, now test-gated).

SnapRepairPolish:
1. Snap lands far off (fast viol after snap minus smooth viol > 1e-5): counter snap_degradation, logged always.
2. Fast/exact disagreement > 5e-6 on any constraint: counter fastexact_disagree with names; if disagreeing candidates exceed 20 percent of exact checks in a run, switch that run to exact-only grading (log mode switch; correctness preserved, speed sacrificed).
3. Incremental resim drift: bit-exact full-forward cross-check every 64 accepts and at exit; hard error, counter resim_drift.
4. Cell representative fails to land in its intended joint cell: assert at generation, skip candidate, counter cell_miss.
5. Livelock: Repair fallback requires strictly lower violation_sqr (port improveQ semantics), Polish requires strictly lower signed objective; assert monotonicity of the accepted sequence per mode, counter monotonic_violation as hard error.
6. Worse-move budget exhaustion masking progress: log down_hills at exit.
7. Reconstruction mismatch: bit-equality check per 1.3; candidate reject + counter, infeasible-with-diagnostics if the winner fails.
8. Gate-pattern mismatch between compiled fast model and exact sim on accepted incumbents: counter gate_pattern_mismatch (expected nonzero near gates; the exact grader remains the acceptor so this is observability, not correctness).

## 4. Test and validation plan (small verified increments, all cold-start per the seedless rule)

V1 SmoothJumpProblem unit test: on 2 existing captures (one single-jump closedform case, one nix multi-jump), for 32 random yaw vectors: (a) smoothValue with true sin/cos matches a direct constPos+coefAxis reconstruction within 1e-12; (b) fastValue at LUT floats matches ExactJumpModel-derived slack/evaluate within 5e-6 on gate-quiet draws; (c) smoothGradient matches central differences (h = 1e-6 rad) within 1e-6 relative. Sprint-jump ticks MUST be covered by (b) (catches the boost double-count class of bug). Runs in :core:test.
V2 BFGS+line-search standalone: Rosenbrock 2D and 10D from standard starts converges to f < 1e-12; a random convex quadratic (n = 50, condition 1e4) reaches gradTol within 80 iters. Runs in :core:test.
V3 ALM vs known optima: on 3 existing problems/closedform cases with pinned refObjective, AlmBfgsCore cold (constant-angle multistart, N = 16) matches the closedform reference objective within 1e-6 and feasTol 1e-6; smooth_exact_gap asserted < 1e-2. This is the "trivial known problem" gate; AlmBfgsCore does not proceed to razors before V1-V3 are green.
V4 SnapRepairPolish standalone: seeded with AlmBfgsCore output on the same closedform cases, must reach exact viol <= 0 with objective within 5e-4 of the closedform reference; plus FacingLattice property tests (100k random floats across the circle AND magnitudes to +-20000 deg: stepToSinBucket changes the sin index by exactly the requested delta where a representative exists; cellRepresentatives reproduce their joint cells; legacy and modern casts both covered) and the reconstruction bit-equality test on mixed lock masks.
V4.5 Intermediate razor gate (adversarial finding 6, re-pinned 2026-07-08 after fixture verification): the UNCORRECTED 5.4375 encoding with the run-tick structure (known cold-solvable, our pipeline closed it in 41 s at objX 8.7086713, handoff section 3). Fixture verification found SOLVED_REAL_5.4375bm_runtick.json non-replayable (stale debug diverging 0.8 blocks from t50, row yaws scoring viol 1.69e-1 under model grounding), so there is NO stored-solution precheck; V4.5 is a solve gate only. Fixture: prefer FIXED_UNSOLVED_REAL_5.4375bm_runtick.json (run-tick structure baked in, onGround[50] corrected) if its recorded state replays byte-exact (posDiff < 1e-12); else UNSOLVED_REAL_5.4375bm.json (verified byte-exact, posDiff 3.5e-15, recorded viol 1.92e-1) with an in-memory jump-variant patch (jump input T50 to T51 plus t50 ground override, applied lines printed). PASS = AlmSnapStage cold reaches viol <= 0 and objX >= 8.7; IMPROVE marker = objX >= 8.7086713; smooth_exact_gap asserted < 1e-2. A benchmark-1 miss with V4.5 green isolates the failure to the corrected razor's basin, not the machinery.
V5 Benchmark suite (section 5).

Regression safety: ./gradlew :core:test stays green throughout (baseline 2026-07-08: 363 tests, 0 failures, nix-full-t1 55.7 s). No edits to ExactJumpModel, McSineTable, Constants; JumpLinearModel changes are additive accessors only (forwardMag/strafeMag/boostAt).

## 5. Benchmark suite (handoff 2b; RazorBench harness)

New env-gated JUnit harness core/src/test/java/de/legoshi/parkourcalc/anglesolver/RazorBench.java (pattern: HpkEngineBench), gated by PKC_RB=1, per-case selection PKC_RB_CASE, budget PKC_RB_BUDGET_S, cooking PKC_RB_COOKING, seeds PKC_RB_SEEDS; every honored variant prints `applied: VAR=value`. Writes build/reports/razor-<tag>.txt with per-case: exact viol, exact objective, wall ms, seeds tried, counters from section 2. Fixtures are copied into core/src/test/resources/captures/ (tests never read the game folder):
1. razor-proof-cold: fixture razor-proof.json SHIPPED and verified 2026-07-08 (5.4375bm_nix_proof.json with landingTick 49 and defaultInputs KEEP, two-line diff; spec builds, n = 49, objTick 49; recorded yaws replay posDiff 8.882e-16, viol 0, objX 212.7001641044). PRECHECK (adversarial finding 14): that replay reruns before any solving, expecting viol <= 0 and objX = 212.7001641 within 1e-6; this validates the legacy chain on this exact geometry every run. PASS = cold viol <= 0 and objX >= 212.7001641 - 1e-6.
2. razor-rung-5375: PINNED 2026-07-08 by measurement (the proof file's frame does NOT contain the REAL-frame z-lo 1.5125; the correct raise group is the lead floor). Patch: raise the Z-mode GE constraints with rhs = -1.487500011921 at ticks {12, 24, 37} by 0.0625, count MUST equal 3, every raised wall logged (name, tick, old rhs, new rhs). Plumbing check: the patched spec scored at the proof's recorded yaws gives viol = 6.248650866e-2 within 1e-9, binding at t=12 with the tail feasible (matches the campaign signature "violation entirely in the lead"). PASS = exact viol <= 0 (beats community 2.74e-4); IMPROVE = viol < 2.59e-4 (our previous plateau).
3. razor-weirdpane: fixture razor-weirdpane.json SHIPPED and verified 2026-07-08 (byte-identical copy; spec builds, n = 50; recorded state replays posDiff 8.882e-16, objX -8.8647718464 exactly, viol 2.271846e-3 = the known gap; all rows unlocked, exercising the float-delta accumulation path). PASS = viol <= 0 and objX >= -8.8625; IMPROVE = objX > -8.864771846396799.
4. gate-microbench: VERIFIED FEASIBLE 2026-07-08 without construction: the proof replay fires 14 X-axis gate ticks, meaningful cancellations at t=5 (velX 1.085313e-3), t=14 (velX 4.792645e-3, immediately after the hop reversal at t=13 to 14, velZ -0.144307 to +0.099969), and t=25 (velX 4.997855e-3). Run ExactJumpModel vs a gateless twin (new ExactJumpModel(0.0, true, false)) on the proof facings; assert the gated model replays viol <= 0 and report the per-tick position divergence of the gateless twin. PASS = divergence documented and > 1e-6 at the window end. This is the "Sheepram's model is provably wrong here" exhibit.
5. Wall-clock recorded for 1-3 in the report; first bar: proof-cold within 240 s.
6. Direct comparison (handoff 2b): encode cases 1-3 in Sheepram's DSL (presets/ folder has examples) and run their binary on the same machine, recording their viol/objective/time under their own semantics. This grounds the "beat Sheepram on every benchmark" claim in their measured results rather than assumptions, and directly addresses the reviewer's realism objection: if their cold run also misses case 1, the bar for "dominate" is their number plus our exactness edge.

Stage A definition of done (mission): benchmarks 1 and 4 pass, 2 or 3 improves on the predecessor plateaus (2.59e-4 / -8.86477), :core:test green, failure-mode counters implemented and documented, debug tags present. Escalation path if benchmark 1 misses with V4.5 green: attribute via section 2 counters (rule: instrument, never guess), then widen the multistart with additional cold engine-generated warms (in scope per mission item 3) before any parameter tuning; if it still misses, report the attribution and the Sheepram direct-comparison numbers to the user rather than silently redefining the bar.

## 6. Operational rules binding the implementation subagents

- Never edit ExactJumpModel/McSineTable/Constants; JumpLinearModel additively only; green :core:test before and after each increment.
- Direct-JVM runs under git-bash require MSYS2_ARG_CONV_EXCL="*"; classpath jars: junit-4.13.2, hamcrest-core-1.3, gson-2.8.0, commons-math3-3.6.1 (gradle cache paths recorded in the benchmark inventory). Gradle-run tests only when no other gradle build is running (daemon contention).
- Hand-authored captures must carry a valid angleSolver.landingTick strictly greater than startTick (primitive int, defaults to 0 and nulls the spec silently).
- Solver results are absolute wrapped yaws; replay contract is toGameFacings(wrapAll(yaws)); never emit game facings as yaws, never delta rows.
- No code comments, no em dashes, user handles all git operations.

## 7. Decisions taken (with the trade-off named)

1. Discrete state = game-facing floats with exact abs-yaw reconstruction at exit. Reason: the lattice is defined over the floats the exact model consumes; searching abs yaws would make single-tick moves perturb downstream buckets through float re-accumulation on unlocked rows. Trade-off: one reconstruction step and its check.
2. Joint-cell lattice (movement sin/cos + legacy boost sin/cos boundaries) instead of Sheepram's single-index lattice. Reason: razor solutions can live in a cos-split subcell a single-index scan never visits (reviewer-confirmed these subcells exist); cost is a slightly larger 1-opt neighborhood.
3. Fixed zeroing pattern derived from the EXACT path + consistency walls + bounded refresh, instead of gate enumeration. Reason: Stage A parity first; enumeration is Stage B item 6. Trade-off: a solution in a gate basin no seed reaches stays unreachable; pattern_flips and gate_pattern_mismatch expose it.
4. Polynomial-interpolation zoom (safeguarded) as the only line-search deviation, with a binaryZoom falsification flag. Reason: known-better and cheap; the flag preserves clean attribution.
5. Standalone harness before engine wiring. Reason: benchmarks are the acceptance gate; engine wiring adds confounds (other stages winning the race) that would blur attribution.
6. Exact eq tolerance 1e-9. Reason: eq terms are corridor-encoded upstream so the list is empty in practice; the value only guards spec-level generality.

## 8. Adversarial review dispositions (2026-07-08)

1 BLOCKER (cold DoD unreachable by scoped-in components): partially rebutted, partially incorporated. Rebuttal: the reviewed draft mis-scoped warm generators to Stage B; the mission's Stage A item 3 includes "constant-angle seeds + our existing warm generators" in the multistart, and cold means not-seeded-from-the-answer, so engine-generated warms are admissible (scope fixed in sections 0 and 1.4). Also, the proof WAS found by Sheepram's architecture (constant-angle phase 1 per source), so "no evidence this architecture reaches it" overstates; their exact seeding/budget is unknown. Incorporated: V4.5 intermediate razor gate, the escalation path in section 5, and the Sheepram direct-comparison run (section 5 item 6) to ground the bar in their measured cold result.
2 MAJOR (boost double-count, missing accessor): incorporated in full (section 1.1: forwardMag/strafeMag/boostAt additive accessors, boost-excluded main pair, V1 must cover sprint-jump ticks).
3 MAJOR (objective sense not threaded through discrete phase): incorporated (signed objective everywhere in comparisons, raw in reports; sections 1.3, 1.4).
4 MAJOR (rung patch scope unpinned): incorporated (raised-wall logging + pinned-count assert + the 6.25e-2 magnitude check; section 5 item 2).
5 MAJOR (continuous zeroingPattern vs exact gates): incorporated (pattern from exact ForwardPath carry velocities at seed and refresh; section 1.2). Residual basin-crossing limitation acknowledged and Stage B-scoped.
6 MAJOR (V4-V5 gap, failure-mode 8 untested): incorporated (V4.5 uncorrected-5.4375 gate; smooth_exact_gap asserted on V3/V4.5).
7 MINOR-MAJOR (large |gf| representative existence, hard-error reconstruct): incorporated (harness facing bound, property tests to +-20000, reconstruct_fail downgraded to candidate reject / infeasible-with-diagnostics).
8 MINOR (modern boost cast identical): incorporated (legacy-only wording, modern boost pair noted redundant).
9 MINOR (thetaC unit collision): incorporated (thetaC in degrees, smooth evaluators convert).
10 MINOR (F wrapped band vs half-line): incorporated as documented limitation (section 1.1).
11 MINOR (fast filter prunes razor candidates): incorporated (candGateWiden + raised topK on razor runs, recorded in report).
12 MINOR (zoom upgrade confounds falsification): incorporated (Config.binaryZoom).
13 MINOR (eq tolerance moot): incorporated (noted empty-in-practice; "flagged for review" removed).
14 MINOR (legacy chain not regression-pinned): incorporated (benchmark 1 byte-exact replay PRECHECK on the legacy model every run).
15 (2026-07-08, post-smoke fix) Stale-gate fast grader repaired in SnapRepairPolish: the SmoothJumpProblem recompiles whenever an accepted incumbent's exact-path gate events (derived from the incremental ForwardPath carry velocities, same rule as AlmBfgsCore) differ from the compiled pattern (counter pattern_recompiles, [DBG-srp1] logs differing ticks, capped at 16 per run because re-anchoring the Repair reference after a recompile breaks the finite-lattice strict-decrease termination argument), and every 256 Repair fast-fallback accepts an exact probe of the incumbent feeds the fastexact_disagree machinery so the 20 percent exact-only switch can fire even when top-K exact checks never trigger (counter probe_checks). Evidence: proof fixprobe (90 s, 2 seeds) best viol 6.2854e-04 vs the smoke baseline 5.5721e-03, exact_checks 78 vs 0, gate_pattern_mismatch 0 vs 1; the j005 diagnostic stays at finalViol 7.777611e-03 but now shows pattern_recompiles 16 (cap saturated, oscillation), probe_checks 3, exact_only fired, and 84141 exact-graded candidates all infeasible, isolating the residual gap to true lattice infeasibility of that basin rather than grader error. V4 gates j004/j006/j011 unchanged and green.

17 (2026-07-08, sprint/amp factor-lag bug in JumpLinearModel.precompute) Section 1.1's claim that the sprint-factor lag was baked in identically to ExactJumpModel was false, which is why ALM plateaued on a fake surface on razor-weirdpane. precompute selected the airborne air-accel with the tick's own sprint (sc.sprintAt) and the grounded accel amplifier with the tick's own amplifier (sc.speedAmplifierAt), whereas ExactJumpModel uses the lagged sc.factorSprintAt / sc.factorAmpAt (ExactJumpModel.java:143-147); on razor-weirdpane tick 2 goes airborne one tick after the sprint transition, so the own-sprint read injected a 6.0e-3 velocity error that compounded to about 5e-2 in position by tick 50. Fixed by switching those two reads to the lagged accessors (two lines, JumpLinearModel.java:115 and :118), touching nothing else. Evidence: SmoothJumpProblemTest V1 razor-weirdpane check(b) fast-vs-exact fell from about 5e-2 to 6.367e-07 (gate 5e-6), RazorBench weirdpane (tag lagfix, 240 s budget, 32 seeds) collapsed smooth_exact_gap from 4e-2 to 1.603072e-04 and stage viol from 4.06e-2 to 6.541046e-04 at objX -8.8630738 (now the sine-LUT residual floor, not an artifact; verdict still MISS but massively improved), and full :core:test stayed green (385 tests, 0 failures, 45 skipped).

18 (2026-07-09, analytic start-translation elimination supersedes the pinned-start decision) Stage A's pinned-start scoping is superseded: X/Z dynamics never read absolute position, so for any yaw vector the optimal bounded start shift is a per-axis 1-D minimax over constraint intervals, computed in closed form (SnapRepairPolish.bestTranslation, verified against a 400x400 brute-force grid to 2.7e-12 on j004 and razor-proof, translated viol <= pinned viol always). The shipped design is: AlmBfgsCore appends (tx, tz) decision variables with the domain box as four ALM inequality terms (a hard clamp alone left the augmented Lagrangian linear and unbounded in the shift, seeds stalled at smoothViol 1e-1; box terms restored 1e-7 convergence), the snap re-anchors the spec at the ALM shift and keeps pinned descent while grading every accepted incumbent by its analytically shifted viol (grading the search moves themselves by translated viol collapsed proof to 4.5e-2, a measured 163x regression, so it was rejected), and the winner is re-verified byte-exact by a true forward from the shifted start with up to 8 ulp nudges (counters trans_nudge, trans_reverify_fail). Evidence (trans1, 300 s, 32 seeds): proof 2.769e-4 to 1.769e-4 (prediction from recentering the old winner was 2.15e-4, beaten), rung 3.221e-4 to 2.463e-4 (below both the 2.59e-4 plateau and the community 2.74e-4 in viol terms, still short of feasibility), weirdpane 6.541e-4 to 6.038e-4 with the start relocated 0.693 blocks in z inside the footprint window; bound 0 stays byte-identical (asserted) and the full gate is green at 391 tests.

16 (2026-07-08, seed-order and exact-only throughput fixes) Two measured performance defects were repaired. The seed-order artifact (AlmSnapStage ran seeds 0,1,2,... in order and snapped every one, so 30-150 s snaps meant only near-0deg seeds ran inside 240 s and the 180deg proof and 45deg rung basins were never reached) is fixed by running ALM for all seeds first (cheap, about 0.1 to 0.3 s each), then snapping only a deduped low-discrepancy circle-order prefix (0, 180, 90, 270, 45, 225 deg) of min(6, available) basins with per-snap time slices of remaining budget over remaining snaps, with the winner still chosen by exact feasibility then objective then viol. Snap selection deviates from this note's draft (low-discrepancy circle order instead of smooth-objective rank, and K raised 4 to 6) because the smoke proved smooth objective does not predict snap quality: rank-by-objective top-4 snapped four poorly-snapping near-0/90/270 basins and excluded the good 180deg basin, whereas the circle-order prefix reaches both the 180deg (position 2) and 45deg (position 5) basins deterministically. The exact-only throughput collapse (once the 20 percent fast/exact disagreement switch fires, every fast grade became an exact sim and the 2-opt flood cost 4.99M to 19.9M exact checks) is fixed by, in exact-only mode, having 1-opt exact-grade its small 2n-cell neighborhood directly and 2-opt exact-check only the top 4*topK pair candidates by fast grade per round (counter exactonly_2opt_skipped). Evidence (razor-fix2, 240 s, 32 seeds): proof reached viol 2.798e-04 at the 225deg basin, below the 6.29e-04 fixprobe level, and rung reached 3.221e-04 at the 45deg basin, a deterministic converged floor byte-identical across two runs that sits marginally above the 3.198e-04 reference (which was a pre-convergence deadline-cut transient, not the basin floor); V4 j004/j006/j011 stayed green and the j005 diagnostic moved from finalViol 7.78e-03 to 6.17e-03 under the new exact-only 2-opt path.
