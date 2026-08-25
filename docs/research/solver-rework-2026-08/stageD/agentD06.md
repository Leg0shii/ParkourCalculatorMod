# Stage D shard D06: general QCQP SDR + suggest-and-improve heuristics (the practical recovery layer)

AGENT: D06.
TERRITORY: general nonconvex QCQP semidefinite relaxation (SDR / Shor lifting), the Park-Boyd
suggest-and-improve heuristic framework, modern spatial branch-and-bound global QCQP solvers, and their
port feasibility as the pure-Java realization of the SPEC section 4.2 low-dimensional residual solve
(ARCH-1 step 3).

INSPECTED / RUN:
- Read: `docs/research/solver-rework-2026-08/00-context-pack.md`, `SPEC.md` (all of section 4),
  `stage0-copt/FINDINGS.md` (H1/H2 + rank + bound + timing tables). All Stage 0 numbers below are
  REUSED-MEASURED (their commands are in `research/copt/README.md`, not re-run by me).
- Web research (real, checkable, cited at the end): Luo-Ma-So-Ye-Zhang SDR survey (IEEE SPM 2010);
  Park-Boyd suggest-and-improve (arXiv 1703.07870) + the cvxgrp/qcqp package README; Chen-Atamturk-Oren
  complex-variable spatial branch-and-cut (arXiv 1705.09057, Math Prog 2017); Shor 1987 origin;
  Burer-Monteiro low-rank SDP; Goemans-Williamson rounding; COPT/Gurobi 12/SCIP 9/BARON global QCQP
  landscape and licensing; complex-circle-manifold constant-modulus work (2023-2025).
- NO code was run by me; all applicability claims are tagged ESTABLISHED (literature or reused Stage 0
  measurement) or SPECULATION / UNMEASURED-HYPOTHESIS (routed to Stage E).

Shorthand: "the residual" = the SPEC 4.2 nonconvex residual, dimension = number of vanishing-costate
(degenerate) ticks, MEASURED 0 to 4 (Stage 0 section 1). "SDR" = Shor semidefinite relaxation.

---

## D06-1. Foundational: the SDR / Shor recipe and the bound it gives
LOCATION: research topic (SDR survey; Shor 1987).
CLAIM: The canonical SDR recipe (lift `X = uu^T`, drop the rank-1 constraint, keep `X >= 0`) is the
Shor relaxation; it yields a convex SDP whose optimum is a valid bound (lower for min, upper for max) on
the nonconvex QCQP, and it is exactly the lifting the mission's Stage 0 already ran.
EVIDENCE: ESTABLISHED. Luo, Ma, So, Ye, Zhang, "Semidefinite Relaxation of Quadratic Optimization
Problems," IEEE Signal Processing Magazine 27(3):20-34, 2010 (the canonical SDR survey): SDR is "a
powerful, computationally efficient approximation technique" for nonconvex QCQP, obtained by writing the
homogenized quadratic in the lifted matrix and dropping rank-1. The lifting/bound is attributed to
Shor (1987); the SDP "provides a dual bound... does not always provide sufficiently strong bounds if
linear constraints are also involved" (matches our case: many linear walls). Stage 0's `solve_shor_sdp`
is precisely `M = [[1,u^T],[u,X]] >= 0`, dim `2n+1`, modulus equality on the diagonal blocks.
IMPACT: correctness / simplicity (names our tool's Stage-0 SDP with the textbook recipe; anchors the
rank-readout diagnostic).
PROPOSAL: keep the full Shor SDP as a RESEARCH/DIAGNOSTIC oracle only (rank readout = residual
dimension, D06-6); do not ship it (D06-6 shows it buys no bound over the SOCP disk here).
CONFIDENCE: 0.97.
DEPENDS-ON: D06-6.

## D06-2. Foundational: Park-Boyd "suggest-and-improve" is a two-leg template, both legs already
partly present in our stack
LOCATION: research topic (arXiv 1703.07870 + cvxgrp/qcqp package).
CLAIM: Park-Boyd's framework is: SUGGEST a candidate point from a relaxation, then IMPROVE it with a
feasibility-preserving local method; the specific suggest methods are SDR (sample from the Gaussian
whose covariance is the SDP moment matrix), SPECTRAL, and RANDOM, and the improve methods are coordinate
descent, ADMM (consensus), and DCCP (convex-concave). Our shipped ILS/SLP IS an "improve" leg; our
shipped closed-form costate recovery IS a (degenerate, failing) "suggest" leg.
EVIDENCE: ESTABLISHED. Jaehyun Park & Stephen Boyd, "General Heuristics for Nonconvex Quadratically
Constrained Quadratic Programming," arXiv:1703.07870, 2017; open-source package `cvxgrp/qcqp`
(README verbatim): `suggest(SDR)` "fills the values of the variables drawn from an optimal probability
distribution given by the semidefinite relaxation," saving a bound to `sdr_bound`; `suggest(SPECTRAL)`
combines all constraints into one and relaxes (`spectral_bound`, "performance yet to be optimized");
`improve(COORD_DESCENT)` "two-stage coordinate descent: first find a feasible point, then optimize the
objective over the feasible set"; `improve(ADMM)` consensus ADMM; `improve(DCCP)` splits indefinite
quadratics into convex+concave and runs DCCP.
IMPACT: simplicity (the framework is a clean lens on the existing stack: our ARCH-1 is a suggest step
that works replacing a suggest step that does not).
PROPOSAL: frame ARCH-1's recovery as suggest-and-improve: SUGGEST = converge the convex dual + residual
solve (D06-7); IMPROVE = the existing byte-exact ILS/coordinate polish and the smoothness tie-break.
CONFIDENCE: 0.9.
DEPENDS-ON: D06-7, D06-9.

## D06-3. Latest 2023-2026: every modern global solver uses spatial B&B with a convex node relaxation;
none is redistributable pure-Java
LOCATION: research topic (COPT 8, Gurobi 12, SCIP 9, BARON).
CLAIM: COPT (the mission oracle), Gurobi 12, SCIP 9, and BARON all solve nonconvex QCQP to global
optimality by SPATIAL branch-and-bound: branch on continuous nonconvex terms, solve a convex relaxation
(RLT/McCormick linear or SDP/SOCP) at each node, bound-tighten (OBBT), prune. This is exactly the
mechanism to replicate in miniature on our residual (D06-7).
EVIDENCE: ESTABLISHED. Gurobi 12 docs: "global nonlinear optimization... using a spatial
branch-and-bound algorithm with dynamically refined linear outer approximations... maintains globally
valid objective bounds." COPT 8.0 release notes: global nonconvex (MI)QCQP solver with "RLT, BQP, and
PSD cuts, convexity detection, OBBT, and local solves." SCIP Optimization Suite 9.0 (arXiv 2402.17702)
and BARON (AMPL/NEOS) are the same sBB paradigm. MEASURED (Stage 0 section 1c, reused): COPT's spatial
B&B solves our full constant-modulus QCQP globally in 0.02-0.36 s at n=9..49 (j021 n=39: 0.27 s; j008b
n=25: 0.14 s), gap ~0.
IMPACT: correctness (confirms the residual B&B mechanism is the industry-standard one, at 1/10th the
scope) / robustness.
PROPOSAL: adopt the sBB mechanism at residual scope; do NOT ship any of these solvers (licensing +
native packaging, D06-8).
CONFIDENCE: 0.9.
DEPENDS-ON: D06-7, D06-8.

## D06-4. Directly on-point prior art: complex-variable spatial branch-and-cut is our exact setting
LOCATION: research topic (arXiv 1705.09057).
CLAIM: Chen-Atamturk-Oren solve nonconvex QCQP with BOUNDED COMPLEX variables (CQCQP) by spatial
branch-and-cut whose node relaxation is an SDP strengthened by valid inequalities from the convex hull
of 2x2 rank-one Hermitian PSD matrices, branching on a local-violation surrogate for the rank-one
constraint. Our `u_t in C` with `|u_t| = m_t` IS a bounded (in fact circle-constrained) complex
variable; this is the closest published algorithm to our per-tick structure.
EVIDENCE: ESTABLISHED. Chen Chen, Alper Atamturk, Shmuel S. Oren, "A Spatial Branch-and-Cut Method for
Nonconvex QCQP with Bounded Complex Variables," arXiv:1705.09057, Mathematical Programming 2017. Node
relaxation = SDP + convex-hull cuts of the 2x2 rank-one Hermitian PSD set; branching on an "alternative
to the rank-one constraint that allows local measurement of constraint violation"; closed-form
bound-tightening; applications AC-OPF and box-QP. (Abstract does not report instance sizes/times; the
mechanism is the transferable content.)
IMPACT: correctness (a published per-complex-variable branching scheme our per-tick residual can copy).
PROPOSAL: for the residual ticks, branch on the phase `arg(u_t)` (the 1-D circle coordinate), using the
2x2-block rank-one convex-hull cut as the node relaxation; this is the CQCQP scheme restricted to the
degenerate ticks.
CONFIDENCE: 0.82.
DEPENDS-ON: D06-7.

## D06-5. Applicability 3a (i): the SDR SUGGEST step recovers the exact solution on single/easy
(rank-1), matching what closed-form already gets, but by a convergence-free route
LOCATION: research topic x Stage 0 measurement.
CLAIM: On single jumps and easy multi-jump, the Shor SDP is rank-1 tight, so its leading eigenvector IS
the global optimum (`u_t = m_t * hat{v}_t`), recovered deterministically with NO randomized rounding and
NO dual convergence. This is the LCvx/S-lemma/TRS exactness class (single or non-interfering quadratics
keep the SDR rank-1).
EVIDENCE: ESTABLISHED (literature) + REUSED-MEASURED (Stage 0 section 1b): eig2/eig1 <= 9.5e-8 on
j005/j016/j019/j022/f2f -> RANK-1; SDR bound matches COPT true to ~1e-6. Literature: SPEC 4.6 threads
(Acikmese-Blackmore LCvx 2011; Polik-Terlaky S-lemma 2007; More-Sorensen TRS) and the SDR survey's
rank-1 exactness discussion. A single equality/homogeneous quadratic gives provable SDR tightness.
IMPACT: robustness (a rank-1 eigenvector read is more robust than the shipped `CostateDualSolver`, which
grinds MAX_ITER=100 at pgres~2.4 without converging even where the answer is closed-form).
PROPOSAL: on the easy class, ANY of {closed-form costate, SDR rank-1 eigenvector, converged SOCP} return
the same optimum; keep the cheapest (closed-form costate) but note the SDR gives a convergence-free
backstop. Do not build the SDP just for this.
CONFIDENCE: 0.85.
DEPENDS-ON: D06-6.

## D06-6. Applicability 3a (ii): the full Shor SDP buys NO tighter bound than the SOCP disk on the
coupled cases; its only unique value is the rank (residual-dimension) readout
LOCATION: research topic x Stage 0 measurement.
CLAIM: On exactly the coupled cases where recovery breaks, the Shor SDP is rank 2-3 and its bound EQUALS
the SOCP disk bound to 6 digits; so lifting to the `(2n+1)x(2n+1)` SDP gives nothing over the cheap
convex disk for the BOUND. The SDP's only unique product is the rank/spectrum, which reveals the
residual dimension (Pataki/Barvinok: `r(r+1)/2 <= #active walls`).
EVIDENCE: REUSED-MEASURED (Stage 0 sections 1b, 2): j021 SDP bound 1067.865480 == COPT tight disk
1067.86548; loopmm SDP -279.299065 == disk; eig2/eig1 up to 0.024 (rank 2-3) localized to the 1-4
degenerate ticks. ESTABLISHED (literature): the Pataki/Barvinok rank bound (SPEC 4.6; Pataki 1998,
Barvinok 1995) explains why few active walls -> small SDR rank -> small residual.
IMPACT: simplicity / speed (kills the case for a pure-Java SDP on the shipped path: it is the expensive
half of the SDR recipe and adds no bound here; a converged SOCP disk is the whole bound).
PROPOSAL: ship the CONVEX DISK (SOCP) as the bound and costate source (converge it, D06-9 improve-leg
notwithstanding); use the full SDP only offline (research) to confirm residual dimension. This is why
"SDR/dual bound" collapses to "converged SOCP + rank-readout," not a shipped SDP.
CONFIDENCE: 0.88.
DEPENDS-ON: D06-1, D06-8.

## D06-7. Applicability 3b: a SMALL spatial B&B over the 1-4 degenerate PHASES is the direct pure-Java
realization of global optimality (ARCH-1 step 3)
LOCATION: research topic (spatial B&B restricted to the residual).
CLAIM: After the convex dual/active-set fixes every non-degenerate tick in closed form
(`u_t = m_t g_t/|g_t|`, SPEC 4.2), the remaining freedom is the PHASE of each degenerate tick (1-4
scalar angles on a circle). A spatial B&B branches ONLY on those 1-4 phases: node relaxation = the
convex disk/SOCP (or an LP once the active set and non-degenerate directions are fixed), branch = bisect
a phase interval, prune by the convex bound. Because the objective and walls are AFFINE in `u`, a node
with all phases fixed is a pure LP; a node with free phases is the disk SOCP, which Stage 0 measures is
loose by only 1.5e-3 b, so nodes prune almost immediately.
EVIDENCE: mechanism ESTABLISHED (D06-3, D06-4: this is industry sBB restricted to the nonconvex terms).
Residual dimension REUSED-MEASURED 1-4 (Stage 0 section 1a: 1 degenerate tick on j021/loopmm, 4 on
j008b). Node-count/latency = SPECULATION / UNMEASURED-HYPOTHESIS: for 1 degenerate phase (j021, loopmm)
the tree is a 1-D global search over a circle, so with a near-tight convex bound (disk loose 1.5e-3 b)
I ESTIMATE single-digit-to-low-tens of node relaxations to reach 1e-6 rad; for 4 phases (j008b) a
box-bisection with OBBT, I ESTIMATE tens-to-low-hundreds of nodes; each node relaxation is the existing
dual/SOCP (microseconds to low ms), so total ESTIMATED well under COPT's 0.27 s full-problem sBB because
we branch on 1-4 ticks, not all n=39. THE EXPERIMENT THAT SETTLES IT: prototype the residual B&B in
COPT/Java and count nodes + wall-clock on j021/j008b/loopmm vs the Stage 0 reference optima (Stage E).
IMPACT: speed + correctness + simplicity (one small primitive replaces the full-n SLP/ILS thrash that
SPEC 4.3 names as the failure; provably global on the residual).
PROPOSAL: THIS is ARCH-1 step 3. Build it pure-Java: reuse `CostateDualSolver`/disk for the node
relaxation, branch on degenerate `arg(u_t)`, smoothness as the in-node tie-break (SPEC 4.5). Prototype
in COPT first (Stage E), then port.
CONFIDENCE: 0.7 (mechanism high; my node/latency numbers are hypotheses for Stage E).
DEPENDS-ON: D06-3, D06-4, D06-6, D06-9.

## D06-8. Port feasibility 4: no redistributable pure-Java global QCQP solver exists; the residual is
small enough to hand-roll, which is preferred
LOCATION: research topic (solver licensing + Java bindings vs the residual size).
CLAIM: A shippable global nonconvex QCQP dependency does not exist for this repo: the global solvers
(COPT, Gurobi, BARON) are commercial/non-redistributable; SCIP is Apache-2.0 (from 8.0.3) with Java
bindings but is a NATIVE library needing per-platform `.so/.dll` shaded across Forge-1.8.9,
Forge-1.12.2, and Fabric, which SPEC already measured net-negative for even an LP library (A04-7);
ALGLIB has a Java QP/QCQP interface but its free edition is GPL and it is a LOCAL (not global nonconvex)
solver. So the residual B&B (D06-7) should be hand-rolled pure-Java.
EVIDENCE: ESTABLISHED. SCIP: Apache-2.0 from v8.0.3 (before that ZIB academic), Java via JSCIPOpt /
JNA_SCIP, but native-lib packaging (scipopt.org). BARON commercial (AMPL). Gurobi/COPT commercial.
ALGLIB "convex/non-convex QP and QCQP solver, C++/C#/Java" (alglib.net) is a local QP solver, GPL free
edition. Repo dependency policy (SPEC section 5, A04-7): dependency-free preferred; re-adding an LP
library measured net-negative across loaders. Residual dimension 1-4 (Stage 0) is small enough to
enumerate/branch by hand.
IMPACT: simplicity (settles the build/packaging question: no dependency; pure-Java residual solve).
PROPOSAL: hand-roll the residual B&B/enumeration in `core/` (no numeric dependency); keep COPT strictly
as the offline reference (SPEC invariant). If a future need for a full-n global solver ever arises,
SCIP-via-native is the only redistributable option and must be weighed against 3-loader native
packaging, but the residual solve removes that need.
CONFIDENCE: 0.85.
DEPENDS-ON: D06-7.

## D06-9. The "improve" leg already lands within the byte-exact floor; the missing piece is the
"suggest" leg, which the residual solve supplies
LOCATION: research topic x Stage 0 / memory measurement.
CLAIM: Suggest-and-improve's IMPROVE leg (coordinate descent / local search preserving feasibility) is
already shipped as ILS/SLP and already reaches within 2.8e-5 b of the COPT continuous optimum on j021;
the shipped FAILURE is the SUGGEST leg (closed-form recovery defaults degenerate ticks to the objective
axis, giving a 0.34 b (j021) / 2.89 b (thousand) infeasible seed). Replace the bad suggest with the
residual B&B; keep the good improve.
EVIDENCE: REUSED-MEASURED: ILS within 2.8e-5 b of COPT j021 optimum (Stage 0 section 1c, cross-validated
1067.8636684 vs 1067.863880); shipped defaulted-seed infeasibility 0.34 b (j021) / 2.89 b (thousand)
(SPEC 4.3). ESTABLISHED: this is exactly Park-Boyd's separation (a good improve rescues a suggest; a bad
suggest wastes it).
IMPACT: robustness / simplicity (the fix is localized to the suggest step; the polish stays).
PROPOSAL: ARCH-1 = {converged convex disk suggest for non-degenerate ticks} + {residual B&B suggest for
degenerate ticks} + {existing ILS/coordinate improve + smoothness tie-break}. This is the whole recovery
primitive (SPEC ARCH-1) expressed in suggest-and-improve terms.
CONFIDENCE: 0.85.
DEPENDS-ON: D06-2, D06-7.

## D06-10. Complementary alternative suggest: Riemannian descent on the product-of-circles is the
native-geometry version of the same recipe
LOCATION: research topic (constant-modulus / complex circle manifold).
CLAIM: Our feasible set `prod_t m_t S^1` is the (scaled) COMPLEX CIRCLE MANIFOLD; the active literature
optimizes constant-modulus objectives by Riemannian gradient/CG directly on it, which is an alternative
"suggest" (or "improve") that keeps `|u_t| = m_t` exactly at every iterate without any projection. It is
complementary to the SDR/B&B route, not a substitute for the residual global solve.
EVIDENCE: ESTABLISHED. "A Complete Derivation of Complex Circle Manifold (CCM) Riemannian Optimization
Equations" (arXiv 2508.07396, 2025); "Transmit MIMO Radar Beampattern Design via Optimization on the
Complex Circle Manifold" (arXiv 1904.07329); SPEC 4.6 (Absil-Mahony-Sepulchre 2008; Boumal 2023;
Manopt). These solve constant-modulus + linear (constant-envelope beamforming), our exact objective
class, but are LOCAL (no global guarantee), hence only a suggest/improve component.
IMPACT: robustness (a projection-free local polish that never leaves the torus; a strong in-node
improver for the residual B&B).
PROPOSAL: consider Riemannian CG on the product-of-circles as the IMPROVE step inside the residual B&B
node (it respects the modulus by construction, unlike SLP which re-projects). Cross-ref the manifold /
LCvx agents; do not duplicate their depth here.
CONFIDENCE: 0.7.
DEPENDS-ON: D06-7, D06-9.

## D06-11. VERDICT: "small spatial B&B on the residual" wins over "full SDR + suggest-and-improve" as
the pragmatic pure-Java realization of global optimality
LOCATION: research topic (the deliverable-5 decision).
CLAIM: The pragmatic pure-Java route is: (1) converge the CONVEX DISK / dual (SOCP), NOT a full SDP, for
the bound and the non-degenerate closed-form ticks; (2) a SMALL SPATIAL B&B (or low-dim enumeration)
over the 1-4 degenerate phases as the exact residual solve; (3) the existing ILS / coordinate /
Riemannian polish as the in-node improve and the smoothness tie-break. The full Shor SDR is a research
oracle only: it gives no tighter bound than the disk on the coupled cases (D06-6) and has no pure-Java
solver (D06-8). Pure suggest-and-improve without the residual B&B leaves the coupled multi-jump global
optimum to luck (a good improve gets within 2.8e-5 b but has no certificate); the residual B&B makes it
provable and tiny.
EVIDENCE: synthesis of REUSED-MEASURED (Stage 0: residual dim 1-4; SDP bound == disk bound; COPT global
in <0.3 s; ILS within 2.8e-5 b) and ESTABLISHED literature (D06-1..D06-4). The head-to-head
node-count/latency of the residual B&B vs. a plain converged-dual + ILS is SPECULATION until the Stage E
prototype (the experiment in D06-7).
IMPACT: simplicity + speed + correctness (one convex solve + one tiny global residual solve + the
existing polish, replacing the full-n SLP/ILS thrash; directly instantiates SPEC ARCH-1).
PROPOSAL: Stage E should prototype the residual spatial B&B in COPT (branch on degenerate phases, disk
node relaxation), benchmark nodes + wall-clock + byte-exact round-trip against the Stage 0 reference
optima on j021 / j008b / loopmm / the dF-chain captures, then port pure-Java. Ship neither the SDP nor
any external solver.
CONFIDENCE: 0.75.
DEPENDS-ON: D06-6, D06-7, D06-8, D06-9.

---

## ESTABLISHED vs SPECULATION (summary)

ESTABLISHED (literature or reused Stage 0 measurement):
- The SDR/Shor recipe, bound, and rank-1 exactness class (D06-1, D06-5).
- The Park-Boyd suggest-and-improve template and its exact suggest/improve method list (D06-2).
- Every modern global QCQP solver = spatial B&B + convex node relaxation + OBBT (D06-3); COPT solves our
  full problem globally in <0.3 s at n<=49 (Stage 0).
- Chen-Atamturk-Oren complex-variable spatial branch-and-cut is our exact per-variable setting (D06-4).
- Full Shor SDP bound == SOCP disk bound on the coupled cases; SDP's unique value is the rank/residual
  readout (D06-6, Stage 0).
- No redistributable pure-Java global QCQP solver; SCIP needs native 3-loader packaging (D06-8).
- The improve leg (ILS) already reaches within 2.8e-5 b of the COPT optimum (D06-9, Stage 0).
- Product-of-circles = complex circle manifold; Riemannian methods solve constant-modulus + linear but
  are local (D06-10).

SPECULATION / UNMEASURED-HYPOTHESIS (routed to Stage E):
- Residual B&B node counts and wall-clock (D06-7): single-digit-to-low-tens of nodes for 1 degenerate
  tick, tens-to-low-hundreds for 4; total well under COPT's full-problem 0.27 s. EXPERIMENT: prototype
  + count nodes/time vs Stage 0 optima on j021/j008b/loopmm.
- That the residual B&B beats plain converged-dual + ILS in practice (D06-11). EXPERIMENT: head-to-head
  on the coupled captures with byte-exact round-trip.
- Riemannian CG as the in-node improver being worth its complexity over SLP re-projection (D06-10).

## Citations (all real, checkable)

- Z.-Q. Luo, W.-K. Ma, A. M.-C. So, Y. Ye, S. Zhang, "Semidefinite Relaxation of Quadratic Optimization
  Problems," IEEE Signal Processing Magazine, 27(3):20-34, 2010.
  https://ui.adsabs.harvard.edu/abs/2010ISPM...27...20L/abstract
- N. Z. Shor, "Quadratic optimization problems," Soviet Journal of Computer and Systems Sciences, 1987
  (origin of the SDP/Shor relaxation; cited via the survey above).
- J. Park, S. Boyd, "General Heuristics for Nonconvex Quadratically Constrained Quadratic Programming,"
  arXiv:1703.07870, 2017. https://arxiv.org/abs/1703.07870 ; package: https://github.com/cvxgrp/qcqp
- C. Chen, A. Atamturk, S. S. Oren, "A Spatial Branch-and-Cut Method for Nonconvex QCQP with Bounded
  Complex Variables," Mathematical Programming, 2017; arXiv:1705.09057. https://arxiv.org/abs/1705.09057
- M. X. Goemans, D. P. Williamson, randomized-rounding SDP approximation (MAX CUT ratio ~0.87856),
  1995 (the rounding template behind SDR suggest). https://blog.lalovic.io/max-cut-sdp/
- S. Burer, R. D. C. Monteiro, "A nonlinear programming algorithm for solving semidefinite programs via
  low-rank factorization," Mathematical Programming, 2003.
  https://link.springer.com/article/10.1007/s10107-002-0352-8
- G. Pataki, "On the rank of extreme matrices in semidefinite programs...," Math. of OR, 1998 (rank
  bound; via SPEC 4.6).
- SCIP Optimization Suite 9.0, arXiv:2402.17702, 2024 (Apache-2.0 from v8.0.3; Java bindings JSCIPOpt /
  JNA_SCIP). https://arxiv.org/pdf/2402.17702 ; https://github.com/scipopt/JSCIPOpt
- Gurobi 12 nonlinear/global handling (spatial B&B): https://www.gurobi.com/resources/faq/minlp-faq-practical-mixed-integer-nonlinear-modeling
- COPT (Cardinal Optimizer) release notes, global nonconvex (MI)QCQP (RLT/BQP/PSD cuts, OBBT):
  https://github.com/COPT-Public/COPT-Release
- BARON global (MI)QCQP (commercial): https://ampl.com/products/solvers/global-solvers/baron/
- ALGLIB convex/nonconvex QP/QCQP solver, C++/C#/Java (local; GPL free edition):
  https://www.alglib.net/quadratic-programming/
- "A Complete Derivation of Complex Circle Manifold (CCM) Riemannian Optimization Equations,"
  arXiv:2508.07396, 2025. https://arxiv.org/abs/2508.07396
- "Transmit MIMO Radar Beampattern Design via Optimization on the Complex Circle Manifold,"
  arXiv:1904.07329. https://arxiv.org/abs/1904.07329
- (2023-2026 context) J.-C. Chen et al., "Strong Partitioning and a Machine Learning Approximation for
  Accelerating the Global Optimization of Nonconvex QCQPs," arXiv:2301.00306, 2023.
  https://arxiv.org/abs/2301.00306
