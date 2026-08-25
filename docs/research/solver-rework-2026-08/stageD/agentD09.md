# Agent D09 shard: ADMM / MM / penalty-CCP / SCA for the constant-modulus angle solve

AGENT: D09 (Stage D methods research).
TERRITORY: ADMM, majorization-minimization (MM), penalty convex-concave procedure (penalty-CCP),
and the general successive-convex-approximation (SCA) family, as candidate PRIMAL engines for the
linearly-constrained constant-modulus program of SPEC section 4. Central question: the shipped
`SlpSolve` is a bespoke trust-region SCA; is there a cleaner MM / ADMM that converges better?

FILES / SOURCES INSPECTED (this repo):
- `docs/research/solver-rework-2026-08/00-context-pack.md` (whole), `SPEC.md` sections 4 and 6.
- `docs/research/solver-rework-2026-08/stage0-copt/FINDINGS.md` (H1/H2, bound tightness, global QCQP).
- `docs/research/solver-rework-2026-08/stageA/SYNTHESIS.md` (F2 reject-rate, consistency matrix).
- `core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/SlpSolve.java` (whole).
- `core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/TrustRegionLp.java` (kernel head).

LITERATURE CONSULTED (WebSearch / WebFetch, real, checkable; full list in the Citations section):
Huang-Sidiropoulos consensus-ADMM (TSP 2016); Bai-Ma-So convergence analysis of consensus-ADMM
(Signal Processing 2023); Mehanna-Huang-Gopalakrishnan-Konar-Sidiropoulos FPP-SCA (SPL 2015);
Lipp-Boyd penalty-CCP (Opt. Eng. 2016); Scutari et al SCA framework (TSP 2017 / 2018); Song-Babu-Palomar
and Sun-Babu-Palomar MM (TSP 2015/2017); Park-Boyd nonconvex-QCQP heuristics (2017); Wang-Yin-Zeng
nonconvex-ADMM global convergence (JSC 2019); Yu-Shen-Zhang-Letaief MO-AltMin (TSP 2016); Li et al
Riemannian ADMM (2022/2024); recent scaled-ADMM / Riemannian-ADMM constant-modulus work (2024-2025).

No claim here is a measurement of an ADMM/MM prototype on our corpus: none was built this shard. Every
applicability claim is tagged ESTABLISHED (a literature fact or a repo-measured incumbent number) or
SPECULATION / UNMEASURED-HYPOTHESIS (my inference, with the experiment that would settle it). Per the
rigor bar this shard proposes; Stage E must prototype and measure against the COPT references before any
integration.

---

## Findings

### D09-1 The shipped SlpSolve is already an SCA / penalty-CCP, parametrized in theta
- LOCATION: `SlpSolve.java:195-297` (the two-phase trust-region SLP loop); `SlpSolve.java:235-249`
  (the `du/dyaw = i*u` Jacobian rows); classification against SPEC 4.1-4.2.
- CLAIM: The incumbent is not a novel method; it is textbook sequential convex approximation on the
  circle constraints, linearized at the current iterate, with a trust region and byte-exact acceptance
  test. The `du/dyaw` row it builds (`row[v] += wall.coef[t] * (wall.axis==0 ? -uz[t] : ux[t]) * RAD`)
  is exactly the first-order tangent of the per-tick circle at the current `u_t`; this is the same
  first-order object a penalty-CCP would place as the supporting-hyperplane linearization of the
  nonconvex modulus shell (D09-5). So "bespoke trust-region SLP" and "SCA / linearized penalty-CCP in
  theta-space" name the same algorithm.
- EVIDENCE: ESTABLISHED (code + literature). The SCA family and its per-iterate linearize-then-solve
  structure is Scutari et al (TSP 2017); the circle tangent equals the CCP linearization of the
  concave modulus part (Lipp-Boyd 2016). Repo-measured incumbent behaviour: F2 (stageA/SYNTHESIS.md:57)
  re-counted 45430 reject / 51907 total LP steps = 87.52%, 8.01 LP/accept over 109 trace files.
- IMPACT: simplicity / framing. Reframing the incumbent as SCA makes the real question crisp: not
  "SLP vs ADMM" but "which SCA variant, in which variable space, with which feasibility device."
- PROPOSAL: Adopt this classification in the SPEC so the comparison is apples to apples; the incumbent
  is the SCA baseline, and ADMM/MM/penalty-CCP are alternative members of the same local-method class
  (see D09-8 for the global-vs-local consequence).
- CONFIDENCE: 0.9.
- DEPENDS-ON: none.

### D09-2 Consensus-ADMM (Huang-Sidiropoulos) fits our structure with closed-form subproblems and no LP kernel
- LOCATION: research topic; against SPEC 4.1 (n circle equalities + m affine walls + linear objective).
- CLAIM: Huang & Sidiropoulos consensus-ADMM reformulates a general QCQP so that each constraint gets a
  local copy and all copies reach consensus on one global variable; every per-constraint subproblem is a
  single-constraint QCQP (QCQP-1) that is solvable "irrespective of (non-)convexity." For OUR problem
  each QCQP-1 is trivial: a per-tick circle `|u_t| = m_t` subproblem is projection onto a circle
  (radial rescale of the ADMM target, closed form: `u_t = m_t * v_t / |v_t|`), and each affine wall
  `a . u <= b` subproblem is projection onto a halfspace (closed form). The linear objective folds into
  the consensus (z) update, which is an unconstrained quadratic minimization (closed-form average with a
  `-c / (rho N)` shift). So the entire method is closed-form projections plus dual updates: no simplex,
  no trust region, no `TrustRegionLp` kernel.
- EVIDENCE: ESTABLISHED (literature). Huang-Sidiropoulos abstract, verbatim: "each of the sub-problems
  is a QCQP with only one constraint (QCQP-1), which is efficiently solvable irrespective of
  (non-)convexity" (arXiv:1601.02335; TSP 64(20):5297-5310, 2016). Circle/sphere QCQP-1 as a closed-form
  radial projection is standard (Park-Boyd 2017 "suggest-and-improve"). Applicability to our exact model
  is SPECULATION until prototyped.
- IMPACT: simplicity (removes the LP kernel and the trust-region rejection machinery) + potential speed
  (D09-7). Magnitude UNMEASURED.
- PROPOSAL: Stage E prototype a 2-block or (n+m)-block consensus-ADMM in the CONTINUOUS `u`-space
  (JumpLinearModel coefficients only), compared head to head with SlpSolve on j021 / j008b / loopmm /
  thousand and the dF-chain captures, byte-exact-certified after a final LUT snap.
- CONFIDENCE: 0.8 (structural fit), 0.4 (that it beats the incumbent, pending measurement).
- DEPENDS-ON: D09-3, D09-6, D09-7, D09-8.

### D09-3 The clean split is 2-block: the product-of-circles torus vs the affine-wall polytope; nonconvex-ADMM stationarity theory covers it
- LOCATION: research topic; the task's part (a) split.
- CLAIM: The cleanest formulation is a 2-block ADMM, not per-constraint consensus. Block x = the torus
  `T = prod_t { |u_t| = m_t }`, whose projection is per-tick closed form (independent circle projections,
  O(n), embarrassingly parallel). Block y = the convex set `{ y : A y <= b }` carrying the linear
  objective `c^T y`, whose subproblem is a convex QP / LP projection with a linear tilt (one small convex
  solve, or itself closed-form for a single active wall). Couple x = y by an equality and run ADMM.
  This is exactly the setting Wang-Yin-Zeng analyze: ADMM minimizing over coupled linear equality
  constraints where one block is the indicator of a COMPACT MANIFOLD (the torus is a compact smooth
  manifold) and the other is convex.
- EVIDENCE: ESTABLISHED (literature). Wang, Yin, Zeng, "Global Convergence of ADMM in Nonconvex
  Nonsmooth Optimization," JSC 78:29-63, 2019 (arXiv:1511.06324): their theory "allows nonconvex
  constraints such as compact manifolds," proving subsequence convergence of ADMM to a stationary point
  under a sufficiently large, structurally conditioned penalty. CAVEAT (SPECULATION): their coercivity /
  image-space conditions were written for a smooth-plus-nonsmooth objective; our objective is purely
  LINEAR (affine), the borderline coercivity case, so the guarantee transfers only if the affine block's
  polytope is bounded (it is, given box/corridor walls) or a proximal/Tikhonov term is added. This is a
  proof-obligation, not a proven fact for our instance.
- IMPACT: robustness (a convergence certificate the trust-region SLP does not carry) + simplicity.
- PROPOSAL: In the Stage E prototype, bound the y-block (or add a tiny proximal term) to satisfy the
  Wang-Yin-Zeng coercivity condition, and MEASURE iterations-to-tolerance vs the 8.01 LP/accept baseline.
- CONFIDENCE: 0.75 (theory exists), 0.5 (transfers cleanly to the linear-objective instance).
- DEPENDS-ON: D09-2.

### D09-4 MM gives a majorizer, but the linear objective makes the objective-side MM trivial; MM only bites on a wall PENALTY (soft feasibility)
- LOCATION: research topic; the task's part (b).
- CLAIM: There is NO useful majorization-minimization of "linear objective + circle constraints" as
  stated, because a linear objective needs no majorizer: minimizing `c^T u` over the free torus
  separates per tick and is solved in ONE closed-form step, `u_t = m_t c_t / |c_t|` (this is precisely
  the SPEC 4.2 zero-active-wall costate recovery). MM earns its keep only when the affine walls are
  folded in as a differentiable PENALTY, e.g. `c^T u + mu * sum_j max(0, a_j.u - b_j)^2`; majorize the
  convex quadratic penalty by a separable per-tick quadratic (a Jacobi/proximal upper bound) and each MM
  step becomes independent per-tick circle projections. That is the Song-Babu-Palomar unimodular
  "power-method-like" MM iteration `u_t <- m_t * exp(j * arg(y_t))` adapted to our per-tick modulus. MM
  then guarantees MONOTONE descent of the penalized objective and convergence to a stationary point.
- EVIDENCE: ESTABLISHED (literature). Song-Babu-Palomar unimodular MM and the power-method surrogate
  (TSP 2015/2016); the general recipe and surrogate catalog in Sun-Babu-Palomar, "Majorization-
  Minimization Algorithms in Signal Processing, Communications, and Machine Learning," TSP 65(3):794-816,
  2017. The one-step free-torus solution is SPEC 4.2 (repo-measured: single/easy jumps are exactly this,
  0 throttled ticks, stage0-copt/FINDINGS.md:37-46).
- IMPACT: robustness (monotone, parameter-light) BUT it is a PENALTY method: it reaches the walls only
  as `mu -> inf` and is weakest exactly where our optima live, hugging active walls byte-exact
  (FEAS_TOL=0). So MM alone cannot deliver exact feasibility; it needs the same final byte-exact snap +
  certify as everything else.
- PROPOSAL: Treat MM as a cheap, monotone WARM-START / smoother that produces a good torus point fast,
  then hand off to an exact-feasibility stage (the SPEC 4.2 residual solve, or a short ADMM). Do NOT
  position MM as the exact-feasibility engine.
- CONFIDENCE: 0.8.
- DEPENDS-ON: D09-5, D09-8.

### D09-5 Penalty-CCP DC-decomposes the modulus equality; its linearization is byte-identical to the incumbent's Jacobian; FPP-SCA slacks fix SlpSolve's null-return failure
- LOCATION: research topic; the task's part (c). Incumbent failure at `SlpSolve.java:304-316` (phase-1
  returns null / best-effort on unrestored feasibility).
- CLAIM: The modulus equality `|u_t|^2 = m_t^2` is a difference of convex: the convex disk
  `|u_t|^2 <= m_t^2` plus the concave shell `|u_t|^2 >= m_t^2`. Penalty-CCP keeps the disk and linearizes
  the concave shell at the current `u_t^{(k)}` to the supporting halfplane
  `u_t^{(k)} . u_t >= (m_t^2 + |u_t^{(k)}|^2) / 2`. Each CCP iteration is then an LP (linear objective +
  linear walls + n tangent halfplanes) or an SOCP if the disks are retained. This tangent halfplane is
  the SAME first-order object the incumbent already builds as its `i*u` Jacobian row (D09-1), so
  penalty-CCP in u-space and SlpSolve in theta-space are first-order-equivalent SCAs. The value-add is
  the feasibility device: FPP-SCA (Mehanna et al) adds a slack `s >= 0` to every linearized constraint
  and minimizes it, so each convex subproblem is ALWAYS feasible and returns a point; when the slack
  reaches zero a feasible point is obtained and thereafter convergence to a KKT point holds. The
  incumbent SLP has no such slack: its phase 1 can fail to restore feasibility and return null
  (`SlpSolve.java:311-316`), a shipped failure mode FPP-SCA structurally removes.
- EVIDENCE: ESTABLISHED (literature). Lipp-Boyd, "Variations and extension of the convex-concave
  procedure," Opt. Eng. 17(2):263-287, 2016 (penalty-CCP, infeasible start, vector inequalities);
  Mehanna-Huang-Gopalakrishnan-Konar-Sidiropoulos, "Feasible Point Pursuit and Successive Approximation
  of Non-convex QCQPs," IEEE SPL 22(7):804-808, 2015 (slack + L1 penalty, KKT convergence once feasible).
  The Jacobian equivalence is code-level ESTABLISHED (`SlpSolve.java:247`). The null-return failure is
  repo-measured behaviour (the code path exists and F8/F9 note the resulting nulls).
- IMPACT: robustness (removes a null-return failure mode) + correctness on infeasible-start captures.
  Magnitude UNMEASURED.
- PROPOSAL: Stage E: add FPP-SCA slacks to the incumbent's phase-1 LP (cheapest possible change: it
  keeps `TrustRegionLp` but makes every subproblem feasible), and separately prototype a slacked
  penalty-CCP in u-space. Measure null-rate and objective on the dF-chain and coupled captures.
- CONFIDENCE: 0.85 (slack fixes null-return), 0.5 (net objective win).
- DEPENDS-ON: D09-1, D09-6.

### D09-6 Working in u-space (Cartesian on the torus) rather than theta-space removes the sin/cos linearization error that drives the 87.5% reject
- LOCATION: `SlpSolve.java:235-249` (theta linearization), `SlpSolve.java:290-296` (halve-on-reject);
  F2 (stageA/SYNTHESIS.md:57-63).
- CLAIM: The 87.5% LP reject is NOT LP-kernel cost; it is trust-region management of the error in the
  first-order Taylor of `u_t(theta) = m_t (cos, sin)(baseArg_t + theta_t)`. The trust region starts at 30
  deg (`Config.trStartDeg = 30.0`), far outside the linear regime of sin/cos, so it proposes-large,
  gets rejected on the byte-exact test, and halves toward the float lattice. ADMM, MM, and penalty-CCP
  all operate on `u` directly and treat each circle as a SET with an EXACT closed-form projection
  (`u_t = m_t v_t / |v_t|`); there is no sin/cos Taylor and no step-size that can overshoot the
  parametrization, so the primary source of the 87.5% rejection disappears by construction. This is the
  single strongest structural argument for the u-space methods over the incumbent.
- EVIDENCE: ESTABLISHED (repo-measured + mechanism). F2 re-count 87.52% reject, 8.01 LP/accept, phase-2
  worst at ~92%; the trust region is "a binary halve-on-reject with no curvature model" (SYNTHESIS.md:60);
  `TrustRegionLp` is ~11% of solver CPU, most thrown away (SYNTHESIS.md:62). That u-space projection is
  exact and needs no trust region is ESTABLISHED geometry. That this NETS a faster solve is
  UNMEASURED-HYPOTHESIS (u-space methods may need more, cheaper iterations; D09-7).
- IMPACT: speed (removes the dominant waste source, up to the ~11% TrustRegionLp CPU and the rejected
  forward-sims of D09-7) + simplicity (no trust-region state machine).
- PROPOSAL: Any Stage E prototype MUST work in u-space, not theta-space, and MUST iterate on the linear
  model (no per-step ExactJumpModel.forward); certify byte-exact once at the end.
- CONFIDENCE: 0.85 (mechanism), 0.5 (net speed win pending measurement).
- DEPENDS-ON: D09-2, D09-7.

### D09-7 Port feasibility: pure-Java, no LP kernel; the concrete lever is deleting the per-step byte-exact forward sims, not the LP arithmetic
- LOCATION: `SlpSolve.java:203-205` and `:276-278` (two `exact.forward` calls per LP iteration);
  `TrustRegionLp.java` (the bespoke bounded simplex being replaced).
- CLAIM: A u-space consensus/2-block ADMM or penalty-MM is ~50-100 lines of pure Java: per-iteration it
  needs n circle projections (O(n)), m halfspace projections (O(m)), one consensus/objective update that
  applies the causal banded friction map `C(s,k)` (O(n^2) dense, or O(n*band) exploiting the lower-
  triangular banded structure of `JumpLinearModel.coef`), and dual updates (O(n+m)). No matrix
  factorization is required if the friction map is applied directly; no simplex. Per iteration this is
  far cheaper than the incumbent's per-LP cost, which is a simplex solve (`lpMaxIter=2000`,
  `STALL_LIMIT=400`) PLUS two byte-exact `ExactJumpModel.forward` calls. The real cost lever is those two
  forward sims per LP step: the u-space methods iterate on the CLOSED-FORM linear model and touch the
  byte-exact model only at final certification, which Stage 0 justifies because the continuous->byte-exact
  drop is tiny (~3e-4 b sine-bucket, ~1e-4 b accumulated).
- EVIDENCE: ESTABLISHED (code structure + Stage 0 drop numbers). Iteration COUNT and wall-clock vs the
  incumbent are UNMEASURED-HYPOTHESIS: nonconvex ADMM commonly needs many hundreds of cheap iterations
  and is penalty-parameter (rho) sensitive; whether total time beats the incumbent's ~110-310 ms FAST
  envelope (SPEC section 5) is exactly what Stage E must measure. The dependency policy is satisfied
  (pure-analytical, no numeric-solver dependency; SPEC section 5 invariant).
- IMPACT: speed (removes per-step forward sims and the simplex) + simplicity (no LP kernel, no
  trust-region state) + zero packaging cost. Magnitude UNMEASURED.
- PROPOSAL: Stage E microbenchmark via direct `java -cp` (Gradle swallows env): warmup then timed
  medians for consensus-ADMM(u), penalty-MM(u), FPP-slacked-SLP, each vs SlpSolve, reporting
  iterations, ms median/spread, final byte-exact residual, and objective gap to the COPT reference.
- CONFIDENCE: 0.7 (pure-Java feasibility), 0.4 (net latency win).
- DEPENDS-ON: D09-2, D09-6.

### D09-8 Convergence verdict: every method in this family reaches a LOCAL / KKT point only; it is global exactly where Stage 0 already proved the SDR is rank-1 (single / easy), and NOT global on coupled multi-jump
- LOCATION: research topic; against stage0-copt/FINDINGS.md sections 1-2.
- CLAIM: Consensus-ADMM, FPP-SCA, penalty-CCP, MM, and Riemannian/manifold ADMM are ALL local
  stationary-point (KKT) methods; none carries a global-optimality guarantee on a nonconvex QCQP. So on
  our problem: (i) single jumps and easy multi-jump, where Stage 0 measured the SDR is RANK-1 tight and
  the disk equals the sphere (0 throttled ticks), any KKT point of the relaxation IS the global optimum,
  and these local methods are global-by-tightness (as the shipped closed-form recovery already is); (ii)
  coupled multi-jump (j008b, j021, loopmm), where Stage 0 measured SDR rank 2-3, the disk loose by
  ~1.6e-3 b, and a 1-4 dimensional vanishing-costate residual, these methods are ONLY local: they can
  land on the wrong sheet of the residual and there is no guarantee they reach the COPT global optimum.
  The global mechanism there is the SPEC 4.2 reduction (convex dual + exact solve of the tiny residual)
  or a spatial B&B (COPT), NOT any full-dimension primal iteration.
- EVIDENCE: ESTABLISHED. Local/KKT-only is the stated guarantee of every cited method: consensus-ADMM
  convergence is to a stationary point under a large penalty and a positive-definite/coercivity
  condition (Bai-Ma-So, Signal Processing 2023, arXiv:2205.14884); FPP-SCA guarantees KKT ONCE feasible
  (SPL 2015); penalty-CCP finds local optima of DC programs (Lipp-Boyd 2016); MM converges to a
  stationary point (Sun-Babu-Palomar 2017); nonconvex-ADMM converges to a stationary point (Wang-Yin-Zeng
  2019). Rank-1-tight vs rank 2-3 and the 1.6e-3 b disk gap and the 1-4 dim residual are all
  repo-measured (stage0-copt/FINDINGS.md:37-70, 118-126).
- IMPACT: correctness / scope. Decisive for target capability 4: these methods CANNOT be the
  global-optimum engine on coupled multi-jump; they can only be a better LOCAL engine than the SLP.
- PROPOSAL: Position the whole family as a replacement for the LOCAL fallback (SLP/ILS), never as a
  replacement for the closed-form costate recovery or the SPEC 4.2 residual solve. Reserve global claims
  for the rank-1 regime, which needs no iteration at all.
- CONFIDENCE: 0.9.
- DEPENDS-ON: D09-2, D09-4, D09-5.

### D09-9 Altitude verdict: full-n ADMM/MM is the wrong dimension given the 0-4 tick residual; its honest role is the residual inner engine or the robust fallback, not the ARCH-1 headline
- LOCATION: research topic; against SPEC 4.2 and section 6 (ARCH-1/2/3).
- CLAIM: SPEC 4.2 measured that only 0-4 ticks are degenerate (free); every other tick is costate-
  determined in closed form. A full-n ADMM/MM/CCP re-solves the entire n-dimensional torus to recover
  what closed form already gives on n-4 of the ticks. That is the wrong altitude: it spends the whole
  iteration budget rediscovering the easy part. The measured structure (tiny residual, Pataki bound
  r(r+1)/2 <= #active walls) favors the SPEC 4.2 reduction (solve the convex dual, identify the residual,
  solve 1-4 dimensions EXACTLY by enumeration / grid / tiny B&B) over any full-dimension primal iteration.
  Even INSIDE the residual, at dimension 1-4 direct enumeration on the circle angles is exact and
  trivially fast, so ADMM/MM has no clear edge there either. Therefore the family's defensible role is
  narrow: (a) the robust LOCAL fallback that REPLACES the 87.5%-reject SLP when the active set is
  ambiguous or the dual has not converged, and (b) a monotone warm-start feeding the residual solve.
- EVIDENCE: ESTABLISHED (measured structure). 0-4 degenerate ticks and rank 2-3 (stage0-copt/
  FINDINGS.md:37-70); SPEC 4.2 reduction and Pataki bound (SPEC.md:243-267); ILS already within 2.8e-5 b
  of the COPT optimum on j021 (stage0-copt/FINDINGS.md:104), showing a good LOCAL search is already
  nearly enough. That a targeted residual solve dominates full-n iteration is a reasoned inference
  (SPECULATION) pending the Stage E head-to-head.
- IMPACT: simplicity / correctness (keeps the architecture at the right altitude) and guards against
  over-investing in a full-dimension primal engine.
- PROPOSAL: Do NOT make ADMM/MM the primary Stage E target (that remains SPEC ARCH-1's convex-dual +
  low-dim residual). Do prototype the u-space consensus-ADMM and the FPP-slacked SLP as ARCH-2/ARCH-3
  fallbacks and as the residual's inner solver, measured against the COPT references and the ILS baseline.
- CONFIDENCE: 0.75.
- DEPENDS-ON: D09-2, D09-8.

### D09-10 The 2023-2026 landscape confirms the family is mature, closed-form-projectable, and KKT-guaranteed, with constant-modulus + linear constraints the actively-studied analog
- LOCATION: research topic; the task's part 2 (latest work).
- CLAIM: Recent work does not change the verdict but strengthens the port case: (i) constant-modulus
  with linear constraints under ADMM is a live topic with KKT-convergence proofs under a penalty-lower-
  bound condition (scaled-ADMM array-pattern synthesis, Signal Processing 2024; ADMM transmit
  beampattern under constant modulus, Signal Processing 2020); (ii) manifold/Riemannian ADMM now has
  stationarity guarantees and directly targets the circle/oblique manifold that IS our torus (Li et al
  "A Riemannian ADMM," arXiv:2211.02163; inertial Riemannian-gradient ADMM, Optimization Online 2024;
  dual Riemannian ADMM for unit-diagonal low-rank SDP, 2025); (iii) the manifold-AltMin lineage
  (Yu-Shen-Zhang-Letaief MO-AltMin, TSP 2016) shows a conjugate-gradient retraction on the product-of-
  circles manifold is a competitive, dependency-free alternative to both ADMM and SLP for pure
  constant-modulus objectives. A Riemannian gradient / CG method on our torus (retraction = per-tick
  radial renormalization) is an even simpler pure-Java option than ADMM for the wall-free or
  penalty-folded sub-solve.
- EVIDENCE: ESTABLISHED (literature, listed in Citations). Bai-Ma-So (2023) prove consensus-ADMM
  convergence only under a positive-definite objective-quadratic and a large penalty; our objective is
  linear, so that specific theorem does not apply verbatim (a caveat, SPECULATION that a proximal term
  restores it). Applicability of any of these to our exact model is UNMEASURED-HYPOTHESIS.
- IMPACT: robustness / option-value: a Riemannian-CG retraction method is a lighter alternative worth a
  Stage E slot alongside ADMM.
- PROPOSAL: Include a Riemannian-gradient / CG-on-the-torus prototype in the Stage E bake-off (retraction
  = radial renormalize, wall handling by penalty or projected step), as it may beat both SLP and ADMM in
  simplicity for the penalty-folded sub-solve.
- CONFIDENCE: 0.7.
- DEPENDS-ON: D09-4, D09-8.

---

## Bottom line (for the orchestrator)

1. The shipped `SlpSolve` is already an SCA / linearized penalty-CCP, just parametrized in theta; the
   87.5% LP reject is sin/cos linearization error managed by a trust region (D09-1, D09-6). ESTABLISHED.
2. The cleaner engine is any of consensus-ADMM / penalty-MM / penalty-CCP / Riemannian-CG worked in
   u-SPACE, where each per-tick circle has an EXACT closed-form projection: no LP kernel, no trust
   region, no per-step byte-exact forward sim, pure Java, zero packaging cost (D09-2, D09-6, D09-7).
   The structural cleanliness is ESTABLISHED; that it converges faster / more robustly is
   UNMEASURED-HYPOTHESIS and must be prototyped in Stage E.
3. FPP-SCA slacks (Mehanna et al) structurally remove SlpSolve's phase-1 null-return failure and are the
   cheapest concrete upgrade to the incumbent (D09-5). ESTABLISHED mechanism.
4. GLOBAL vs LOCAL: the entire family is LOCAL / KKT-only. It is global-by-tightness exactly where Stage 0
   already proved rank-1 SDR (single/easy, needing no iteration), and NOT global on coupled multi-jump,
   where global still requires the SPEC 4.2 convex-dual-plus-low-dim-residual reduction or a spatial B&B
   (D09-8). ESTABLISHED.
5. ALTITUDE: given the measured 0-4 tick residual, a full-n ADMM/MM is the wrong dimension; its honest
   role is the robust local FALLBACK that replaces the 87.5%-reject SLP, and/or the inner solver for the
   tiny residual, not the ARCH-1 headline (D09-9). The primary Stage E target stays ARCH-1 (convex dual +
   residual); ADMM/MM/CCP/Riemannian-CG belong in the ARCH-2/ARCH-3 fallback bake-off.

VERDICT on the task's question "is consensus-ADMM or MM a cleaner, better-converging pure-Java primal
engine than the bespoke trust-region SLP?": CLEANER in structure, yes, and it fixes two measured incumbent
warts (the trust-region rejection loop and the null-return); BETTER-CONVERGING is unproven and, crucially,
it does not change the COMPLEXITY CLASS (still local/KKT). It reaches GLOBAL only where the problem is
already rank-1 tight. So it is a better LOCAL primal engine to prototype as a fallback, not a route to the
global optimum on coupled multi-jump. Route to Stage E: u-space consensus-ADMM + FPP-slacked SLP +
Riemannian-CG, measured against the COPT references, the ILS baseline, and the FAST latency envelope.

---

## Citations (all real, checkable; consulted this shard)

- Huang, K. and Sidiropoulos, N.D. "Consensus-ADMM for General Quadratically Constrained Quadratic
  Programming." IEEE Trans. Signal Processing 64(20):5297-5310, 2016. arXiv:1601.02335.
- Bai, Y., Ma, S., So, A.M.-C. (and coauthors). "Convergence analysis of consensus-ADMM for general
  QCQP." Signal Processing (Elsevier), 2023; arXiv:2205.14884. Convergence to stationarity under a
  sufficiently large penalty and a positive-definite objective-quadratic condition.
- Mehanna, O., Huang, K., Gopalakrishnan, B., Konar, A., Sidiropoulos, N.D. "Feasible Point Pursuit and
  Successive Approximation of Non-convex QCQPs." IEEE Signal Processing Letters 22(7):804-808, 2015.
  arXiv:1410.2277. (FPP-SCA: slack + penalty, KKT once feasible.)
- Lipp, T. and Boyd, S. "Variations and extension of the convex-concave procedure." Optimization and
  Engineering 17(2):263-287, 2016. (Penalty-CCP, infeasible start, vector inequalities.)
- Scutari, G., Facchinei, F., Lampariello, L. "Parallel and Distributed Methods for Constrained Nonconvex
  Optimization - Part I: Theory." IEEE Trans. Signal Processing, 2017. And Scutari, G., Sun, Y. "Parallel
  and Distributed Successive Convex Approximation Methods for Big-Data Optimization." 2018, arXiv:1805.06963.
  (The unifying SCA framework.)
- Song, J., Babu, P., Palomar, D.P. "Sequence Set Design with Good Correlation Properties via
  Majorization-Minimization" / "Optimization Methods for Designing Sequences with Low Autocorrelation
  Sidelobes." IEEE Trans. Signal Processing, 2015/2016. (MM for unimodular; the power-method-like update.)
- Sun, Y., Babu, P., Palomar, D.P. "Majorization-Minimization Algorithms in Signal Processing,
  Communications, and Machine Learning." IEEE Trans. Signal Processing 65(3):794-816, 2017. (MM survey
  and surrogate catalog.) doi:10.1109/TSP.2016.2601299.
- Park, J. and Boyd, S. "General Heuristics for Nonconvex Quadratically Constrained Quadratic
  Programming." 2017, arXiv:1703.07870 / web.stanford.edu/~boyd/papers/qcqp.html. (Suggest-and-improve;
  per-constraint QCQP-1 projections.)
- Wang, Y., Yin, W., Zeng, J. "Global Convergence of ADMM in Nonconvex Nonsmooth Optimization." Journal
  of Scientific Computing 78:29-63, 2019. arXiv:1511.06324. (Nonconvex ADMM to a stationary point;
  explicitly covers compact-manifold constraints, i.e. our torus block.)
- Yu, X., Shen, J.-C., Zhang, J., Letaief, K.B. "Alternating Minimization Algorithms for Hybrid Precoding
  in Millimeter Wave MIMO Systems." IEEE J. Sel. Topics Signal Processing 10(3):485-500, 2016.
  arXiv:1601.07340. (MO-AltMin: conjugate-gradient retraction on the constant-modulus / product-of-circles
  manifold.)
- Li, J., Ma, S., et al. "A Riemannian ADMM." arXiv:2211.02163, 2022 (revised 2024). (First ADMM-type
  method for nonsmooth objective over a manifold; epsilon-stationary complexity.)
- "An inertial Riemannian gradient ADMM for nonsmooth manifold optimization." Optimization Online, 2024.
  And "A Dual Riemannian ADMM Algorithm for Low-Rank SDPs with Unit Diagonal," 2025. (Recent oblique-
  manifold ADMM with the Burer-Monteiro factorization on the oblique manifold.)
- Scaled-ADMM / ADMM constant-modulus array-pattern synthesis: "Array pattern synthesis with phase-only
  constraint based on scaled-ADMM algorithm," Signal Processing (Elsevier), 2024 (KKT convergence under a
  penalty lower bound); "ADMM-based transmit beampattern synthesis under a constant modulus constraint,"
  Signal Processing, 2020. (Directly analogous constant-modulus + linear constraints with convergence.)
