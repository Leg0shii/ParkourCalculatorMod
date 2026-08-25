# IMPLEMENTATION GUIDE: the one-day clean slice + the reuse API map

Written for a fresh implementation session. It fills the gaps the research docs leave for actually WRITING
the code: the exact reuse API points (verified this session), the concrete algorithms for the achievable
one-day slice, the per-capture acceptance targets, the verify commands, and the load-bearing gotchas.

Read `SPEC.md` (esp. section 4 the math + section 6 ARCH-1) and `RESULTS.md` first for WHY; this doc is HOW.
Do NOT git commit/push. No em dashes. No code comments (pick clear names). Shipped path stays green on
`./gradlew :core:test -PslowTests` at all times; prototypes live in `core/src/test/` or behind a default-off
flag.

---

## 0. Build order for the FULL pipeline (incremental; verify at each stage)

This builds the complete ARCH-1 solver. Build in this order so every stage is verified in-repo against the
COPT references before the next depends on it; keep the shipped path green throughout and keep new stages
behind a default-off flag until proven. There is no time constraint; correctness and cleanliness first.

- STAGE P0: the three orthogonal perf levers (bit-identical / measurable, ship-independent). Section 2.
- STAGE P1: the degenerate-tick RESIDUAL SOLVE for all k (k=0..4 + large-k momentum), first as a test probe
  reusing the shipped dual/SLP as the convex step, then wired as the recovery core. Section 3 (all k).
- STAGE P2: the objective-aware byte-exact SNAP (sphere decoding) + certify, replacing the dead
  LatticeRepair. Section 3.5.
- STAGE P3: the from-scratch pure-Java interior-point SOCP CONVEX KERNEL (D12), replacing the reused
  dual/AL-FISTA so the bound converges and the disk primal is clean at all n. Section 3.6.
- STAGE P4: the inertia-gate big-M MIP layer (D11), hybrid (banded fast path + small branch on cold miss),
  with a real infeasibility certificate. Section 3.7.
- STAGE P5: free-start as p0 variables + one rigid translation; dF as phase constraints threaded through
  the kernel and residual. Section 3.8.
- STAGE P6: the smoothing collapse to ONE give-back-constrained trend-filter against a shared reference.
  Section 3.9.
- STAGE P7: the entry-path unification (one solve tail carrying every capability; fold single-jump and the
  receding-horizon terminal window into the point-solve; remove the FAST-explore / legal-only gaps).
  Section 3.10.

Each stage lands the pipeline closer to the SPEC section 6 ARCH-1 and is independently green-gated. The
reused dual/SLP in P1 lets you validate the residual mechanism BEFORE the IPM (P3) exists, so nothing is
blocked; P3 then swaps the convex step under a stable interface.

---

## 1. Reuse API map (verified this session; file:line where load-bearing)

- `ClosedFormSolve.optimize(ExactJumpModel exact, JumpSpec spec, double feasTol, AtomicBoolean cancel)` ->
  `double[]` byte-exact yaws or null. `dualBound(JumpSpec)` -> weak-duality bound.
  `Result{double[] yaws; double violation; boolean feasible;}` via `optimizeRobustGraded`.
- `RelaxationRecovery.solve(exact, spec, feasTol, cancel)` -> byte-exact yaws or null (the working
  degenerate-face rescue on pure-position specs; BAILS on any facing or EQ wall, `solve:32-34`).
- `SlpSolve.optimize(exact, spec, feasTol, cancel, double[] seedAbsWrapped, ...)` and
  `optimizeBestEffort(..., phase1Calls, totalCalls, boolean inertiaAware)` -> byte-exact yaws. Handles F
  (facing) constraints via `YawTies`. This is the completion oracle for the pin-scan.
- `JumpSpec(JumpPhysicsInputs inputs, List<JumpConstraint> constraints, Objective objective)`: constraints
  are an unmodifiable list; to add a pin, build a NEW list = old + the pin constraint, then a new JumpSpec.
- `JumpConstraint(Mode mode, int t1, Integer t2, Op op, Cmp cmp, double rhs, String name)`. For a facing
  pin: `Mode.F`. Use a tight two-sided BAND (two F constraints, GE at yaw-eps and LE at yaw+eps, eps ~1e-3
  deg), because `YawTies.of` pins a tick to `0.5*(absLo+absHi)` only when the F band width is in
  `[0, WIDTH_MAX]` (`YawTies.java:79-83`) and then folds that tick OUT of the SLP variables
  (`varOf[t] < 0`, `YawTies.java:45-53`). A single EQ may or may not register as a band; verify the pin
  took by replaying and checking the realized facing at that tick equals the pin to <1e-3 deg.
- `JumpLinearModel(JumpPhysicsInputs)`: `mMag(t)`, `baseArg(t)`, `friction(t)`, `coefAxis(axis,s,k)`,
  `constPos(k,axis)`, `objectiveVectors(obj,cx,cz)`, `compileWalls(constraints,margin,trivial)`,
  `recoverYawDeg(t,gx,gz)`. This is the affine model; use it for the objective vectors and (if you build a
  detector) the wall coefficients.
- `ExactJumpModel.forMcVersion(mcVersion)`, `.forward(sc, gf)` -> `ForwardPath` (`getPos(tick, Axis)`,
  `posX/posZ/velX/velZ`). `JumpPhysicsInputs.toGameFacings(double[] absYaws)`, `Angles.wrapAll`,
  `JumpConstraintCompiler.compile(spec).maxViolation(gf, path)`. This is byte-exact truth (FEAS_TOL=0).
- Engine: `AngleSolverEngine(state, boxes, inputs, cb, model)`, `debugBuildSpec()` -> JumpSpec (used by
  StructureDump/ReplayYaws/CoefDump). The graph nodes live in `graph/nodes/`; the recovery tail is
  `DualChainNode` -> ClosedFormSolve/SLP/RelaxationRecovery.

DEGENERATE-TICK DETECTION: `CostateDualSolver` holds the per-tick costates `gx,gz` and recovered `ux,uz`
PRIVATELY (`CostateDualSolver.java:73-74`); there is NO public accessor. Two options:
- (recommended, minimal) add a tiny additive getter exposing the final costate magnitudes
  `sqrt(gx^2+gz^2)` per tick (a getter, no behavior change; keep the slow suite green). Degenerate ticks =
  those with magnitude below a threshold relative to the max costate (start ~1e-3 * max, tune).
- (zero-main-change) detect empirically: the degenerate ticks are the jump-transition / redirect ticks; on
  a failed coupled recovery they are where the recovered path first diverges. For the one-day slice you may
  hardcode the detector to "scan each tick, pin-scan the one that most improves the objective" if the
  accessor is undesirable, but the accessor is cleaner.

---

## 2. P0: the three orthogonal perf levers (bit-identical / measurable)

Ship these independently; they help every solve and carry no rework risk. Exact locations and measured
savings are in `stageB/agentB05.md` (B05) and `agentB04.md` (B04).

1. CAP buildHessian's inner MAC loop at each wall-pair's last coupled tick (`CostateDualSolver` buildHessian,
   the O(walls^2 x ticks) loop, hottest leaf line ~460). Each wall's `coef[]` is nonzero only on a causal
   prefix `[0, lastCoupled]`; cap the inner `t` loop at `min(lastCoupled_i, lastCoupled_j)+1`. This drops
   only trailing zeros: BIT-IDENTICAL (assert the Hessian sums are unchanged in a test). Measured
   op-weighted saving 64.8%, timed 48-60% of buildHessian, ~28% of solver leaf CPU. Precompute each wall's
   lastCoupled once (last nonzero index of `coef[]`).
2. Do NOT run `SmoothingPolish` when Smooth is OFF (`smoothLambda <= 0`). Measured: it did 94% of a
   Smooth-off j001 solve. Gate the `SmoothingNode`/`SmoothingPolish` roughness pass on `smoothLambda > 0`
   (it currently runs unconditionally, A09-5). Verify no objective change on the corpus (it is
   objective-preserving, so this only removes wasted time).
3. Route the anytime polishers' rescoring through the DEAD `ExactJumpModel.stepRange(from>0)`
   (`ExactJumpModel.java:131`, currently only called with from=0). `BucketAscentPolish`/`IlsPolish`
   rescore each single-tick perturbation with a full O(n) forward (measured 3-36M full forwards). Recompute
   only the changed tail from the perturbed tick. For the FULL 2x you also need incremental `toGameFacings`
   and `maxViolation`; the stepper alone is a partial win. This is the largest one but the most code; do it
   last in P0 or defer to follow-up if the day is tight.

Verify P0: `./gradlew :core:test -PslowTests` GREEN (bit-identical for lever 1; objective-unchanged for 2);
re-run the orchestrator timing spread (section 4) and record the measured speedup.

---

## 3. P1: the degenerate-tick residual rescue (the headline)

MECHANISM (validated in Python by `research/copt/residual_branch.py`, which reaches the COPT global optimum
within 1e-5..3e-5 b on j021/j008b/loopmm). The Java realization reuses SlpSolve as the convex completion:

New class `core/src/test/.../anglesolver/ResidualRescueProbe.java` first (prove it), then optionally a
main-side `solver/ResidualRescue.java` wired as a rescue in `DualChainNode` behind a default-off flag.

Algorithm (k=1, the measured-common case; covers j021 t12, loopmm t0):
1. Run the normal solve (ClosedFormSolve/RelaxationRecovery/SLP) -> baseline yaws + objective. Get the
   converged dual costates (via the section-1 accessor) -> degenerate set D (|g_t| below threshold).
2. If |D| == 0: return baseline (nothing to do; single/easy jumps).
3. If |D| == 1 (tick t*): golden-section (or a coarse 1-2 deg grid then refine) over t*'s absolute yaw
   theta in [-180,180]. For each candidate theta:
   a. Build a JumpSpec = original constraints + a tight F band pinning tick t* to theta (section 1).
   b. `double[] yaws = SlpSolve.optimizeBestEffort(exact, pinnedSpec, 0.0, cancel, baselineSeed, p1, tot,
      inertiaAware)`.
   c. If non-null, byte-exact evaluate: `gf = sc.toGameFacings(Angles.wrapAll(yaws)); path =
      exact.forward(sc, gf); viol = compile(spec).maxViolation(gf, path); obj = path.getPos(objTick, axis)`.
      Keep the best FEASIBLE (viol <= FEAS_TOL) by objective.
4. Return the best; if none beats the baseline, return the baseline (never regress).
CRITICAL (measured): do NOT hold the non-degenerate ticks rigid and solve only t* in closed form. That is
INFEASIBLE on j021/j008b (`residual_poc.py` measured). SlpSolve RE-OPTIMIZES the rest per pinned theta,
which is why the pin-scan works and the rigid version does not.

k=2 (two degenerate ticks): nested search, univariate-reducible. Outer golden-section over tick a's angle;
for each, inner golden-section over tick b's angle with a pinned and SlpSolve completing the rest. Or the
closed-form D02/D14 route: fix one phase, the other is a 1-circle problem. Keep the pin-scan realization for
robustness (it re-optimizes the rest, which the closed form assumes fixed).

k=3-4 (complex-SDR-tight regime, Ai-Liang-Yuan 2024 is exact for <=4 complex constraints): a small spatial
branch-and-bound over the k degenerate-tick angles with the convex disk/SLP completion as the node bound
(this is COPT's mechanism restricted to the residual; D06/D14). Branch on the widest-uncertainty angle,
bound each node by the convex completion, prune, keep the byte-exact incumbent. k<=4 keeps the tree tiny.

Large-k momentum (10-22 degenerate ticks, j1150 nix 22, j828 13, j716 10): do NOT brute the k angles. These
degenerate ticks are a coordinated momentum phase (axis-locked; CONTEXT.md) with LOW effective DOF, so
solve them with a smart method that exploits the coupling structure: (a) a Riemannian trust-region descent
on the product of circles seeded from the costate directions (D05/D07: the retraction is angle addition,
the gradient is the i*u tangential projection already in the code at JumpLinearModel.java:166 /
SlpSolve.java:247), which reads the negative curvature the closed-form default is blind to; and/or (b) the
D02-7 coupling-graph test (if the degenerate ticks' shared-wall graph is a forest/tridiagonal/bipartite the
SDR is exact regardless of count, solve directly). COPT solves all of these globally in <0.5 s, so the
target is tractable; benchmark against the COPT references for j1150/j716/j828.

The dispatcher: one `residualSolve(spec, dual, D)` that switches on |D| = k -> k0 return / k1 golden /
k2 nested / k3-4 tiny B&B / large-k Riemannian, all sharing the convex-completion + byte-exact-evaluate
inner primitive. Real infeasibility certificate when no feasible assignment exists (fixes F10): for k=1 the
finite arc-covering is empty; for k>=2 a Farkas/S-procedure dual ray from the completion.

## 3.5 STAGE P2: objective-aware byte-exact snap (sphere decoding)

Replace the dead LatticeRepair (A03-12/A10-3, zero live callers) with a Schnorr-Euchner sphere decoder over
the sine-LUT grid (D07/D11). Given the continuous solution, enumerate +-few LUT buckets/tick on the
degenerate + redirect ticks (the straightaways are decoupled = Babai nearest-bucket rounding), scoring the
BYTE-EXACT objective through ExactJumpModel.stepRange (NOT min-distance: half-angle norm>1 lets a bucket
out-reach the continuous point by up to 1e-2 b, Stage 0 sec 4). Certify feasibility byte-exact. This is the
"keep the good improve" finisher; the shipped BucketAscent/ILS is the same search and can seed/verify it.

## 3.6 STAGE P3: the convex kernel (from-scratch pure-Java interior-point SOCP)

Replace the reused dual / AL-FISTA (which grinds the cap and fails at n=353) with a from-scratch pure-Java
primal-dual interior-point SOCP on the disk relaxation |u_t| <= m_t (D12). It Schur-reduces to the wall
space (<30 vars even at n=353), returns the tight bound + active set + multipliers + costates g_t in one
shot, and is conditioning-robust (O(sqrt(cones) log 1/eps) iterations). No redistributable pure-Java SOCP
exists (D12 verified: ojAlgo partial + Java 22+, JOSQP QP-only, ECOS/SCS/Clarabel native/GPL), and native
binding is dead on the Java-8 Forge loaders, so build it in core (dependency-free, MC-free). Nesterov-Todd
scaling; self-dual embedding or a Mehrotra predictor-corrector; the disk cones are trivial 2-D. Expose the
costate magnitudes and the disk primal so the residual dispatcher (P1) consumes them directly, replacing
the temporary CostateDualSolver getter. Fallback if the IPM slips: a preconditioned SCS-style
cone-projection ADMM (JOSQP is an MIT pure-Java operator-splitting reference to study, not ship).

## 3.7 STAGE P4: the inertia-gate big-M MIP layer

Model the momentum clamp (|v_axis*0.91| < thr zeroes an axis) as ~2n big-M indicators (F5, D11): the
inside-band side is the existing velocityWalls, the outside-band side the existing keepAliveWall. Replace
the incomplete suffix-pattern BoundPrunedRecovery with a small mixed-integer branch on the 0-1 gate-critical
ticks only (measured inert on fed ticks, A10-4), HYBRID: the banded closed-form fast path first, the MIP
branch only on a cold miss. This gives a REAL infeasibility certificate where BnB-null currently certifies
nothing (F10). Land loopmm/dsf-neo at the byte-exact objective. The modulus nonconvexity is orthogonal and
stays in the residual solve.

## 3.8 STAGE P5: free-start and dF

Free-start: add the two box-bounded p0 variables (px in [pxLo,pxHi], pz in [pzLo,pzHi]) to the convex
kernel with wall coefficient p0coef = +-tc (compileWall:248-249) and objective coefficient +-1; separability
by rigid translation is proven byte-exact (F4, FreeStartTranslationTest). Baseline: solve pinned at the box
center, then one PathTranslation pass; reserve the joint solve for the fixed-start-infeasible-but-
translatable residual. Add a final whole-chain translation for multi-jump (fixes the window-0-only commit,
A06-12). dF: a per-tick phase equality/sector (dF=0 pins theta_t=theta_{t-1}, a linear pin / dimension drop)
threaded through the kernel and the residual; unify the two pin mechanisms (FacingPrefold 1e-9 vs YawTies
1e-6, F14) into one, and let the residual carry dF so RelaxationRecovery stops bailing on facing walls (F8).

## 3.9 STAGE P6: the smoothing collapse

Replace the four stages (turnCost / DeWiggle / SmoothingPolish / SmoothFaceRecovery) with ONE
give-back-constrained order-1 trend-filter (D13): minimize the total variation of the turn rate
||D2 theta||_1 (exact O(n) taut-string / Condat), reversal-count L0 as the accept-gate, floored by an
epsilon-constraint obj >= originalObjective - X against ONE shared pre-smoothing reference (fixes the
~1.63e-2 b stacked give-back double-count, F6). On the ARCH-1 path this specializes to a tiny tie-break over
the degenerate ticks (the straightaways are costate-fixed and already smooth). Optionally fold smoothness
JOINTLY into the P1 large-k Riemannian solve (D05: smoothness is a smooth function on the manifold; the
only method that carries reach + walls + smoothness in one objective).

## 3.10 STAGE P7: entry-path unification

One solve tail carrying every capability (free-start, translate, capCertify, residual, snap, smoothing,
legal push), gating only search intensity by effort. Fold the single-jump path and the receding-horizon
TERMINAL window into the point-solve chain (make solveWindow(last=true) delegate to it; A07-10/F9). Enforce
seam-straddling constraints in the following window (A07-7). Remove the FAST-explore capability gap and the
legal-OPTIMIZE-only gap (F11). Add cross-window dual warm-start on the shared walls (2.5-5x, F12/SB6). One
central best-feasible selector with an immutable (start, yaws) candidate (kills the shared-mutable-scenario
dropped-feasible surface, A09-7). Derive isSuccess from the same maxViolation<=FEAS_TOL as the record
(kills the EQ/range false-success latent risk, B03-4/5).

BYTE-EXACT + objective-aware caveat (measured, RESULTS section 2.2, stageE/byte-exact-roundtrip.md): the
pin-scan already scores the BYTE-EXACT objective (step 3c), so it inherently does the objective-aware
search that snapping a continuous optimum does not. That is why the pin-scan should land j008b at -0.197
where snapping COPT's continuous optimum gave -0.2196. Confirm this.

Wiring (only after the probe proves it): add it as a rescue in the OPTIMIZE/coupled path AFTER the existing
chain, behind a default-off system property (e.g. `pkc.residualRescue`), keep-better semantics (byte-exact
feasibility first, then objective), and tag any new corpus test `@Category(SlowSolverTests.class)`.

DO NOT try to pin via the closed-form/dual path: it BAILS on facing constraints (`hasFacingWall` ->
`compileWall` returns null). The pin-scan MUST use SlpSolve (which handles F via YawTies).

---

## 4. Acceptance targets and verify commands

Reference optima (COPT oracle, byte-exact-round-tripped; the target the rescue must reach):

| capture | shipped THOROUGH (byte-exact) | COPT continuous optimum | ARCH-1 byte-exact target | pass = |
| --- | --- | --- | --- | --- |
| j021-rinav1-01 | 1067.862397 | 1067.863733 | 1067.863789 | rescue reaches >= 1067.8637, viol 0 |
| j008b-2jump | -0.215314 | -0.197052 | ~-0.197 (obj-aware) | rescue reaches ~-0.197, viol 0 |
| loopmm-3jump | -279.2997 (rec) | -279.299065 (clamp-free, gate) | needs gate MIP | do NOT expect the clamp-free number |
| j005/j016/j019/j022 (single/half-angle) | already optimal | continuous is BELOW byte-exact | KEEP shipped (do not route) | no regression |

Setup + verify (direct java -cp; Gradle swallows env, so compile once then run classes directly):
```
./gradlew :core:testClasses
# regenerate the classpath file if missing (writes core/build/test-classpath.txt): create an init script
#   allprojects { tasks.register('printTestCp') { doLast {
#     def cp = project(':core').sourceSets.test.runtimeClasspath.files.collect { it.absolutePath }
#     new File(rootProject.projectDir,'core/build/test-classpath.txt').text = cp.join(File.pathSeparator) } } }
# then: ./gradlew -I <that-file>.gradle :core:printTestCp
CP="$(cat core/build/test-classpath.txt)"
# byte-exact replay of any yaw json (COPT export or your rescue output):
PKC_REPLAY_CAP=core/src/test/resources/captures/j021-rinav1-01.json \
  PKC_REPLAY_YAWS=research/copt/data/yaws-j021-rinav1-01.json \
  java -cp "$CP" org.junit.runner.JUnitCore de.legoshi.parkourcalc.anglesolver.ReplayYaws
# live engine solve (baseline / after wiring), per capture:
PKC_SOLVE_FILE=<abs path> PKC_SOLVE_EFFORT=THOROUGH PKC_OPTIMIZE_SECONDS=12 \
  java -cp "$CP" org.junit.runner.JUnitCore de.legoshi.parkourcalc.anglesolver.EngineFileScreen
# COPT reference (oracle, optional re-check): cd research/copt; COPT_LICENSE_DIR=... python run_h1h2.py <cap>
```
Green gate (mandatory before declaring done): `./gradlew :core:test -PslowTests` GREEN. Fast suite after
each change: `./gradlew :core:test`.

---

## 5. Load-bearing gotchas (measured; each will bite an implementer who ignores it)

1. HOLD-REST-RIGID IS INFEASIBLE (residual_poc.py). The rest must re-optimize per pinned angle; use SlpSolve
   as the completion, not a closed-form fix of only the degenerate tick.
2. THE CONVEX PATH BAILS ON FACING CONSTRAINTS. ClosedFormSolve/RelaxationRecovery return null on any F/EQ
   wall. The pin must go through SlpSolve/YawTies.
3. SNAPPING A CONTINUOUS OPTIMUM IS BYTE-EXACT SUBOPTIMAL on loose-degenerate (j008b -0.2196) and half-angle
   (j005 -41.298) cases. Always score the BYTE-EXACT objective (the pin-scan does); never trust a
   continuous solution without a byte-exact round-trip. COPT is a near-exact reference, not a strict bound
   (byte-exact can out-reach it by up to 1e-2 b via half-angles).
4. HALF-ANGLE SINGLE JUMPS: the byte-exact search already beats the continuous optimum; do NOT route them
   through the convex core (you would regress the objective). The rescue only fires on coupled/degenerate
   cases (|D| >= 1 AND the baseline missed the dual bound by more than the sine floor).
5. GATE CAPTURES (loopmm, dsf, nix): the clamp-free model is not their feasible set; the rescue will not fix
   them without the gate big-M layer (follow-up). Do not chase the clamp-free loopmm number.
6. TICK INDEXING: a constraint on tick n affects posX[n]; to constrain what tick n's input produces, use
   tick n+1 (AGENTS.md). Verify the F pin lands on the intended facing by replaying and checking the
   realized facing at that tick.
7. FEAS_TOL = 0. Accept only byte-exact feasible (viol <= 0 through ExactJumpModel). The margin ladder /
   the sine floor (~1e-4 accumulated) is the certify floor; a rescue result at viol ~1e-4 needs the margin
   ladder to close it, same as the shipped path.
8. DETERMINISM: fix any seed; the corpus is byte-deterministic today (B03) and must stay so.

---

## 6. What is DONE vs TODO (so the session knows the boundary)

DONE (reuse, do not rebuild): StructureDump.java + ReplayYaws.java (env-gated), the COPT harness
(research/copt/), the measured references, the SPEC/RESULTS/DOSSIER/FINDINGS.
TO BUILD (this guide, stages P0..P7): the perf levers, the all-k residual solve, the sphere-decode snap,
the pure-Java IPM SOCP kernel, the gate big-M MIP, free-start/dF, the smoothing collapse, and the
entry-path unification. Build incrementally, green-gated at each stage; the reused dual/SLP in P1 lets you
validate the residual before the IPM (P3) exists, then P3 swaps the convex step under a stable interface.
