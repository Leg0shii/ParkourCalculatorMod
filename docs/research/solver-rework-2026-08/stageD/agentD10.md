# Agent D10: Global polynomial optimization (moment-SOS / Lasserre, TSSOS, complex moment-SOS)

- AGENT: D10, Stage D (methods survey).
- TERRITORY: the moment-SOS (Lasserre) hierarchy, sum-of-squares (Parrilo), sparse/correlative
  variants (TSSOS / CS-TSSOS / Chordal-TSSOS), complex moment-SOS (Josz-Molzahn), and their
  applicability to the constant-modulus QCQP of section 4 and its 1-4 dimensional residual (section 4.2).
- INSPECTED: docs/research/solver-rework-2026-08/00-context-pack.md; SPEC.md sections 4.1-4.6, 5, 6;
  stage0-copt/FINDINGS.md (the measured H1/H2, SDR-rank, residual-dimension, and COPT global-optimum
  numbers this shard reasons against).
- METHOD: WebSearch / WebFetch on the primary literature (citations at the end; all real and checkable).
  No prototypes run this shard; every applicability claim is tagged ESTABLISHED (literature),
  THEORETICAL (holds by construction but not run on our data), or UNMEASURED-HYPOTHESIS (route to Stage E).
- NAMING: "level d" = relaxation order d of the moment hierarchy (moment matrix uses monomials up to
  degree d). "Shor / SDP / level 1" = the rank-lifting Stage 0 already computed. Distances in blocks.

---

## Findings

### D10-1 The problem is polynomial and the hierarchy is guaranteed to converge (Putinar)
- ID: D10-1
- LOCATION: research topic. Anchors: JumpLinearModel per-tick input `u_t=(m_t cos phi_t, m_t sin phi_t)`;
  SPEC 4.1 (constant-modulus QCQP), 4.4 (MOMENT/LASSERRE row).
- CLAIM: our angle-solve continuous relaxation is a genuine polynomial optimization problem, so the
  Lasserre moment-SOS hierarchy applies verbatim and is guaranteed to converge to the global optimum.
- EVIDENCE: ESTABLISHED. Two equivalent polynomial encodings, both standard. (i) Trig->polynomial: set
  `c_t=cos phi_t, s_t=sin phi_t` with the equality `s_t^2+c_t^2=1`; objective and walls are then linear
  in `(c_t,s_t)`, i.e. a real polynomial program of degree 2 (the sphere equalities are the only
  nonlinearity). (ii) Complex: identify `u_t` with a complex number and impose `u_t * conj(u_t)=m_t^2`
  (a per-tick 2-sphere). Either way the feasible set is compact and the per-tick modulus equalities
  supply a ball constraint `sum |u_t|^2 = sum m_t^2`, so the quadratic module is Archimedean and Putinar's
  Positivstellensatz (1993) makes the SOS lower bounds converge to the true optimum monotonically
  (Lasserre 2001, Thm 4.2; the ball/Archimedean condition is exactly the "if one constraint is
  x1^2+...+xn^2<=1" case). This is the same convergence guarantee AC-OPF relies on.
- IMPACT: correctness / completeness of the methods map. Confirms moment-SOS is on the table as a
  reference and certifier for EVERY instance the tool hits, not just the easy ones.
- PROPOSAL: treat moment-SOS as the theoretically-complete backstop (it converges where the
  closed-form recovery and local search have no guarantee), used as an oracle/certifier not a workhorse
  (see D10-7/9).
- CONFIDENCE: 0.97
- DEPENDS-ON: none.

### D10-2 Level of the hierarchy needed maps to the number of ACTIVE walls (S-lemma / Ai-Zhang boundary)
- ID: D10-2
- LOCATION: research topic. Anchors: Stage 0 FINDINGS 1a/1b (0-4 throttled ticks, SDR rank 1 to 3);
  SPEC 4.2 (Pataki bound `r(r+1)/2 <= #active walls`), 4.4 (SOCP/Shor rows).
- CLAIM: the exactness of the CHEAPEST relaxation is governed by how many quadratic constraints are
  active, and our measured active-set is inside or at the boundary of the region where the level-1
  (Shor) SDP is already provably tight, so we rarely if ever need to climb to level 2.
- EVIDENCE: ESTABLISHED. Single quadratic constraint: Shor SDP is exact (S-lemma / TRS exactness;
  Polik-Terlaky 2007). Ai & Zhang (Math. Prog. 2024, arXiv 2304.04174, "On the tightness of an SDP
  relaxation for homogeneous QCQP with three real or four complex homogeneous constraints") prove a
  checkable condition under which the Shor SDP is exact for up to THREE real or FOUR complex homogeneous
  quadratic constraints, and generalize the S-lemma to those cases. Our residual (SPEC 4.2) carries the
  per-tick modulus equality plus the handful of active walls; Stage 0 measured the degenerate/rank>1
  structure at 1-4 ticks with SDR rank 2-3 (eig2/eig1 <= 0.024) on j021/j008b/loopmm. This is the
  narrow regime where level 1 is loose by ~1.6e-3 b and level 2 would be needed. THEORETICAL for our
  exact instances: the Ai-Zhang tightness test would have to be evaluated on our residual SDP to say
  which side of the boundary each capture sits on.
- IMPACT: simplicity. It bounds how much machinery is ever required: at most a level-2 relaxation on
  1-4 variables, and often not even that (level 1 already tight).
- PROPOSAL: before invoking any moment machinery on the residual, run the Ai-Zhang tightness test on the
  residual Shor SDP; only climb to level 2 when it fails. Prototype in COPT (Stage E).
- CONFIDENCE: 0.8
- DEPENDS-ON: D10-1, D10-6.

### D10-3 The COMPLEX moment-SOS hierarchy (Josz-Molzahn) is the natural formulation for our variables
- ID: D10-3
- LOCATION: research topic. Anchors: SPEC 4.1 ("identify u_t with a complex number of modulus m_t");
  Stage 0 FINDINGS (the AC-OPF-shaped structure).
- CLAIM: because each `u_t` is intrinsically a complex number on a circle `|u_t|=m_t`, the complex
  moment-SOS hierarchy fits our problem exactly and is strictly cheaper than a real embedding, and our
  problem is structurally the same object as AC optimal power flow (voltages `|V_i|` on circles), whose
  moment-SOS treatment is a mature, benchmarked literature.
- EVIDENCE: ESTABLISHED. Josz & Molzahn, "Moment/Sum-of-Squares Hierarchy for Complex Polynomial
  Optimization" (arXiv 1508.02068, 2015; SIAM J. Optim. 2018) transpose the Lasserre hierarchy to
  complex numbers, with convergence from the D'Angelo-Putinar Positivstellensatz (2008); they report the
  complex moment matrix is smaller than its real counterpart (the real moment matrix at order d is ~2d
  times larger, and a real localizing matrix ~2d-k_i times larger), and they solve OPF with several
  THOUSAND complex variables by additionally exploiting sparsity. The direct QCQP analogy: our
  `|u_t|=m_t` is the same per-node sphere as AC-OPF's `|V_i|`, a linear objective/walls over a product
  of circles. THEORETICAL for our data: the size-halving is a general property, not yet measured on a
  PKC residual.
- IMPACT: simplicity + speed of any oracle path. Halves the SDP dimension versus a real (cos,sin)
  encoding, and lets us borrow the AC-OPF tooling and exactness results wholesale.
- PROPOSAL: whenever a moment/SDP relaxation is formed (oracle or certifier), form the COMPLEX one, not
  the real (cos,sin) one. Reuse the AC-OPF playbook (Josz et al. arXiv 1311.6370; Molzahn-Hiskens).
- CONFIDENCE: 0.9
- DEPENDS-ON: D10-1.

### D10-4 Level-2+ moment relaxation is what closes an SDR rank>1 gap (the H2 case), but not with a fixed guaranteed order
- ID: D10-4
- LOCATION: research topic. Anchors: Stage 0 FINDINGS 1b (SDR rank 2-3 on coupled cases), 1c (COPT
  global closes it), SPEC 4.3/4.4.
- CLAIM: our measured "H2" situation (level-1 SDR rank 2-3, no tighter than the disk) is precisely the
  regime the higher-order moment relaxations are designed to close, and the analogous AC-OPF literature
  demonstrates second- and third-order relaxations closing exactly such first-order gaps, but there is
  no guarantee a FIXED low order is exact.
- EVIDENCE: ESTABLISHED (analogy). Molzahn, Josz, Hiskens, "Moment Relaxations of Optimal Power Flow
  Problems: Beyond the Convex Hull" (GlobalSIP 2016, arXiv 1612.02519): second- and third-order moment
  relaxations approach the convex hull and globally solve OPF instances for which the first-order SDP
  has a nonzero relaxation gap. Molzahn-Hiskens (application of moment-SOS to OPF, arXiv 1311.6370)
  likewise use level 2 to tighten where level 1 has rank>1. CAVEAT (ESTABLISHED): for NP-hard QCQP the
  order needed can in principle be arbitrarily large and the optimal moment matrix need not be rank-one
  even at the true optimum (stated in the AC-OPF moment literature); and moment-SOS can converge slowly
  precisely on degenerate/flat instances (Baldi-Slot, "Slow Convergence of the Moment-SOS Hierarchy for
  an Elementary Polynomial Optimization Problem", SIAM J. Appl. Algebra Geom. 2025, doi 10.1137/24m1645942).
  Our dual face is measured FLAT/degenerate (SPEC 4.3), which is the slow-convergence regime. So
  "level 2 closes it" is UNMEASURED-HYPOTHESIS for our residual until run in COPT.
- IMPACT: robustness. Level 2 is the principled closer for the rank>1 residual, but the flat-face
  slow-convergence caveat is a real reason not to make it the shipped workhorse.
- PROPOSAL: in Stage E, on j021/j008b/loopmm residuals, solve the complex level-2 moment relaxation in
  COPT and read the moment-matrix rank; confirm it certifies the COPT spatial-B&B global optimum. If
  level 2 is not rank-1, record the actual order needed. Measured result, not assumed.
- CONFIDENCE: 0.75
- DEPENDS-ON: D10-2, D10-3.

### D10-5 Correlative sparsity (CS-TSSOS) is the ONLY way the FULL n<=49 hierarchy stays tractable, and its coupling is set by wall spans, not friction
- ID: D10-5
- LOCATION: research topic. Anchors: context-pack section 2 (`coef(s,k)` causal map; walls read one axis
  across ticks); SPEC 4.4/4.5.
- CLAIM: a DENSE moment relaxation over all n=49 ticks is far too large, but the problem has exploitable
  correlative sparsity, and the correlative-sparsity graph is driven by which ticks share a WALL (not by
  the friction convolution, which is linear and creates no monomial coupling), so CS-TSSOS makes the full
  problem tractable as an oracle exactly as it scales AC-OPF.
- EVIDENCE: mixed. Size (THEORETICAL): the complex level-2 moment matrix over 49 complex variables is
  indexed by holomorphic monomials of degree <= 2, count `C(49+2,2)=1275`, i.e. a 1275x1275 complex
  Hermitian SDP plus localizing blocks: intractable dense. Correlative structure (THEORETICAL, from our
  math): the objective is LINEAR (one axis at one tick) and each wall is a LINEAR functional, so no two
  `u_s` ever share a monomial in the objective or a modulus constraint; the ONLY correlative edges come
  from each linear wall, which links all ticks it reads into a clique. Friction (`coef(s,k)` dense
  lower-triangular) enters only as wall COEFFICIENTS, not as extra monomial coupling, so it does NOT
  widen the sparsity graph. Therefore treewidth ~ max number of ticks jointly spanned by one wall; wide
  corridor walls give wide cliques, narrow footprints give narrow ones. Method (ESTABLISHED): CS-TSSOS
  (Wang, Magron, Lasserre; ACM TOMS 2023, doi 10.1145/3569709; arXiv 2005.02828) and Chordal-TSSOS
  (SIAM J. Optim. 2021, doi 10.1137/20M1323564) build block-diagonal SDPs from the chordal
  (correlative) and term-sparsity graphs and solve Max-Cut / OPF up to ~6000 variables. Josz-Molzahn
  (D10-3) is the complex analogue at thousands of variables. REDUNDANCY (ESTABLISHED, measured Stage 0):
  COPT spatial branch-and-bound already solves the full nonconvex constant-modulus QCQP at n<=49 to
  gap ~0 in <0.3 s (FINDINGS 1c), so a full-problem CS-TSSOS oracle would be a SECOND oracle, not a
  capability we lack.
- IMPACT: simplicity of the oracle story. Correlative sparsity is the reason a moment oracle COULD run
  at n=49, but it is not needed because a cheaper global oracle (spatial B&B) already exists.
- PROPOSAL: do NOT build a full-problem CS-TSSOS path. If a rigorous SOS CERTIFICATE for the full
  instance is ever wanted (beyond the spatial-B&B gap number), CS-TSSOS with wall-clique correlative
  sparsity is the correct construction; otherwise skip it in favor of the residual approach (D10-6).
- CONFIDENCE: 0.78
- DEPENDS-ON: D10-1, D10-3.

### D10-6 The correct use is a moment relaxation on the SMALL RESIDUAL only, and it is genuinely tiny
- ID: D10-6
- LOCATION: research topic. Anchors: SPEC 4.2 (convex dual + 1-4 dim residual), 4.4 ("LOCAL moment/SDP
  on the residual is tractable"); Stage 0 FINDINGS (residual dimension 1-4).
- CLAIM: confirmed correct. After the convex dual/active-set fixes every non-degenerate tick in closed
  form, the leftover is a constant-modulus QCQP in the 1-4 vanishing-costate ticks with the few active
  walls; a complex level-2 moment relaxation on that is a ~15x15 complex SDP that certifies global
  optimality, which is the right and only sensible place to spend moment machinery.
- EVIDENCE: THEORETICAL (size, exact) + ESTABLISHED (why the residual is small). Residual has k in
  {1,2,3,4} complex variables (Stage 0: 1 on j021/loopmm, 4 on j008b). Complex level-2 moment matrix:
  holomorphic monomials up to degree 2 in k=4 vars = `C(4+2,2)=15`, so a 15x15 complex Hermitian PSD
  (~30x30 real) plus one localizing block per modulus equality and per active wall. For k=1 it collapses
  to a single-circle problem (closed form). The residual dimension being small is EXPLAINED by the
  Pataki/Barvinok rank bound `r(r+1)/2 <= #active walls` (SPEC 4.2; Pataki, Math. OR 1998), the same
  bound that keeps AC-OPF SDR low-rank. A rank-1 optimal moment matrix on this tiny SDP is a global
  certificate; if rank>1, climb one order (still tiny). This is the "LOCAL moment/SDP on the residual"
  of SPEC 4.4, confirmed as the right framing.
- IMPACT: simplicity + correctness, large. Reduces "global optimality certificate" from a 1275-dim dense
  SDP to a <=15-dim complex SDP, matching the ARCH-1 residual-solve target (SPEC 6).
- PROPOSAL: adopt "convex dual identifies the residual, then a complex moment/SDR on 1-4 vars certifies
  or solves it" as the certification design. Prototype the residual complex SDP in COPT (Stage E) and
  confirm rank-1 on j021/j008b/loopmm.
- CONFIDENCE: 0.85
- DEPENDS-ON: D10-2, D10-3, D10-5.

### D10-7 Port feasibility: no pure-Java SDP solver exists; moment-SOS is an oracle/certifier, not a shippable solver
- ID: D10-7
- LOCATION: research topic. Anchors: SPEC 5 (dependency policy: dependency-free preferred, A04-7 LP
  library measured net-negative), 4 (perf envelope 0.1-800 ms).
- CLAIM: even a level-2 moment SDP on 4 complex variables requires an SDP solver, and there is no
  established pure-Java, dependency-free SDP solver, so moment-SOS cannot ship on the core path as-is; it
  is a research oracle and certifier.
- EVIDENCE: ESTABLISHED (tooling). The moment/SOS literature ships in MATLAB/Julia/C (GloptiPoly,
  SOSTOOLS, TSSOS.jl, YALMIP) backed by SDP solvers (Mosek, SDPA, SeDuMi, COPT); the web survey found no
  standard pure-Java SDP interior-point solver. Interior-point for even a 15x15 complex (30x30 real) SDP
  is a nontrivial numeric kernel (Cholesky/eigendecomposition per Newton step) versus the microsecond
  fast-path budget. Burer-Monteiro low-rank factorization (Burer-Monteiro 2003; recent guarantees
  arXiv 2206.03345 / 2207.01789) could shrink the factor to O(k) and is implementable pure-Java, BUT it
  is nonconvex and can fail even ABOVE the Barvinok-Pataki rank threshold (Waldspurger-Waters,
  arXiv 2211.12389, "The Burer-Monteiro SDP method can fail even above the Barvinok-Pataki bound"), and
  a plain B-M local minimum is not a hard optimality certificate without an added dual-feasibility check.
  Re-adding any numeric-solver dependency to core carries the Forge 1.8.9/1.12.2 shade+relocate and
  Fabric include cost, measured net-negative for an LP library (SPEC 5, A04-7).
- IMPACT: rules moment-SOS OUT as a shipped solver; keeps it IN as an oracle (COPT) and as a design
  reference for the certificate.
- PROPOSAL: do not ship an SDP/moment solver. If a global certificate is wanted on the shipped path, use
  the direct small-QCQP methods of D10-8 (dependency-free) and reserve moment-SOS for Stage-E oracle
  validation only.
- CONFIDENCE: 0.85
- DEPENDS-ON: D10-6.

### D10-8 Direct small-QCQP global methods beat a moment SDP on the residual and are dependency-free
- ID: D10-8
- LOCATION: research topic. Anchors: SPEC 4.2 (residual solve options: enumeration / tiny spatial B&B /
  SDR rank-reduction / null-space projection), 4.6 (unimodular MM references).
- CLAIM: for a 1-4 variable constant-modulus residual, several exact global methods are simpler and
  cheaper than assembling and solving a moment SDP, and are pure-arithmetic (no dependency), so they, not
  moment-SOS, are the shipped-path residual solver.
- EVIDENCE: ESTABLISHED (methods). (i) k=1 (one degenerate tick): a single circle with linear walls is
  a 1-D problem solvable in closed form / by one eigenvalue. (ii) k small, one sphere: the
  trust-region-subproblem / QCQP-with-one-quadratic optimum is found by a SINGLE generalized eigenvalue
  problem (Adachi-Nakatsukasa; Gander-Golub-von Matt), and extended TRS with a few linear cuts by at
  most three generalized eigenvalue problems (Adachi-Nakatsukasa 2019, "Eigenvalue-based algorithm and
  analysis for nonconvex QCQP with one constraint"; linear-time eTRS, arXiv 1807.07563). (iii) product of
  a few circles (unimodular): MM / power-method-like iterations converge cheaply (Soltanalian-Stoica,
  "Designing Unimodular Codes via Quadratic Optimization", IEEE TSP 2014, the MERIT method; SPEC 4.6),
  and MIMO SDR is exact under a checkable low-noise condition (Chen-Xiu-... / Ai; arXiv 1710.02048).
  (iv) tiny spatial branch-and-bound on 1-4 nonconvex ticks is exactly what COPT does to reach gap ~0 in
  <0.3 s (Stage 0 FINDINGS 1c). THEORETICAL for our data: which of these is fastest/most-robust on the
  PKC residual is a Stage-E measurement.
- IMPACT: simplicity + speed, large. Replaces "port an SDP solver" with a few-line generalized-eigenvalue
  or MM routine, staying inside the microsecond-to-sub-ms envelope for a 1-4 dim residual.
- PROPOSAL: implement the residual solve (ARCH-1 step 3) as a small spatial B&B or generalized-eigenvalue
  TRS-family routine with an MM warm start, NOT as a moment SDP. Use the moment SDP only as the Stage-E
  oracle that CERTIFIES this routine returns the global optimum.
- CONFIDENCE: 0.82
- DEPENDS-ON: D10-6, D10-7.

### D10-9 VERDICT: moment-SOS is a certification/oracle tool for the residual; correlative sparsity makes the full problem an oracle but a redundant one
- ID: D10-9
- LOCATION: research topic. Synthesis of D10-1..D10-8 against SPEC 6 (ARCH-1/2/3).
- CLAIM: moment-SOS is a CERTIFICATION tool (it proves the small B&B/eigenvalue residual solve found the
  global optimum, via a rank-1 optimal moment matrix and its SOS dual Positivstellensatz certificate),
  NOT a shipped solver; correlative-sparsity moment-SOS (CS-TSSOS) does make the full n<=49 problem
  tractable as an oracle, but that oracle is redundant because COPT spatial B&B already solves it
  globally in <0.3 s.
- EVIDENCE: synthesis. (a) Certifier, not solver: no pure-Java SDP solver and B-M is unreliable (D10-7),
  while direct methods solve the residual dependency-free (D10-8); moment-SOS's unique product is the
  rigorous global CERTIFICATE (rank-1 + SOS dual), which is exactly a certification role. (b) Full-problem
  tractability via correlative sparsity is real (D10-5, CS-TSSOS/Josz-Molzahn scale the analogous AC-OPF
  to thousands of variables) but redundant against the measured COPT global solve at our sizes
  (FINDINGS 1c). (c) The complex level-2 relaxation on the 1-4 dim residual is the tractable, correct
  locus (D10-6) and matches SPEC 4.4's "LOCAL moment/SDP on the residual." (d) Caveat: the flat degenerate
  dual face we measure (SPEC 4.3) is the slow-convergence regime for moment-SOS (D10-4), a further reason
  it belongs in the oracle/certifier role rather than the hot path.
- IMPACT: sets moment-SOS's place in ARCH-1: it is the Stage-E oracle and the optional global CERTIFICATE
  for the residual solve, not a shipped stage. No effect on the perf envelope (it never runs at solve
  time).
- PROPOSAL: (1) SHIP: convex dual + direct small-QCQP residual solve (D10-8), byte-exact snap + certify.
  (2) ORACLE/CERTIFY (Stage E, COPT, never shipped): complex level-2 moment/SDR on the residual to prove
  the shipped residual solve is global on j021/j008b/loopmm; read moment-matrix rank to confirm the
  order needed. (3) Do NOT build a full-problem CS-TSSOS path; do NOT port an SDP solver into core.
- CONFIDENCE: 0.85
- DEPENDS-ON: D10-4, D10-5, D10-6, D10-7, D10-8.

---

## ESTABLISHED vs SPECULATION summary

ESTABLISHED (literature, checkable citations):
- Moment-SOS/Lasserre converges under Putinar/Archimedean; our problem is polynomial (D10-1).
- S-lemma (1 constraint) and Ai-Zhang (<=3 real / <=4 complex homogeneous constraints) tightness of the
  Shor SDP; the order/active-set link (D10-2).
- Complex moment-SOS (Josz-Molzahn) transposes the hierarchy to complex numbers with smaller matrices;
  scales AC-OPF to thousands of complex variables (D10-3).
- Higher-order moment relaxations close first-order SDP gaps in AC-OPF, with no fixed guaranteed order and
  known slow convergence on degenerate instances (D10-4).
- CS-TSSOS / Chordal-TSSOS exploit correlative + term sparsity to scale to ~6000 variables (D10-5).
- No standard pure-Java SDP solver; Burer-Monteiro can fail above the Barvinok-Pataki bound (D10-7).
- Generalized-eigenvalue TRS / eTRS and unimodular MM (MERIT) solve small QCQP globally, dependency-free
  (D10-8).

SPECULATION / UNMEASURED-HYPOTHESIS (route to Stage E prototype in COPT):
- That complex level-2 is EXACT (rank-1) on OUR residuals (j021/j008b/loopmm), i.e. no higher order
  needed (D10-4, D10-6).
- Which side of the Ai-Zhang tightness boundary each capture's residual sits on (D10-2).
- Which direct residual method (spatial B&B vs generalized-eigenvalue vs MM) is fastest/most-robust on
  the PKC residual (D10-8).
- The measured size-halving of the complex vs real relaxation on a PKC residual (D10-3).

---

## References (all real, checkable)

Foundational:
- J. B. Lasserre, "Global Optimization with Polynomials and the Problem of Moments", SIAM J. Optim.
  11(3):796-817, 2001. doi 10.1137/S1052623400366802. https://dl.acm.org/doi/abs/10.1137/S1052623400366802
- P. A. Parrilo, "Semidefinite programming relaxations for semialgebraic problems", Math. Programming
  96:293-320, 2003. (SOS <-> SDP.) https://www.mit.edu/~parrilo/
- M. Putinar, "Positive polynomials on compact semi-algebraic sets", Indiana Univ. Math. J. 42, 1993
  (the Archimedean convergence guarantee).

Complex hierarchy + AC-OPF analogy:
- C. Josz, D. K. Molzahn, "Moment/Sum-of-Squares Hierarchy for Complex Polynomial Optimization",
  arXiv:1508.02068, 2015; SIAM J. Optim. 2018. https://arxiv.org/abs/1508.02068
- C. Josz et al., "Application of the Moment-SOS Approach to Global Optimization of the OPF Problem",
  arXiv:1311.6370. https://arxiv.org/abs/1311.6370
- D. K. Molzahn, C. Josz, I. A. Hiskens, "Moment Relaxations of Optimal Power Flow Problems: Beyond the
  Convex Hull", IEEE GlobalSIP 2016, arXiv:1612.02519. https://arxiv.org/abs/1612.02519
- "Minimal Sparsity for Second-Order Moment-SOS Relaxations of the AC-OPF Problem", arXiv:2305.19232, 2023.
  https://arxiv.org/abs/2305.19232

Sparse / correlative / term sparsity:
- J. Wang, V. Magron, J.-B. Lasserre, "TSSOS: A Moment-SOS hierarchy that exploits term sparsity",
  arXiv:1912.08899; SIAM J. Optim. 2021. https://arxiv.org/abs/1912.08899
- J. Wang, V. Magron, J.-B. Lasserre, "CS-TSSOS: Correlative and term sparsity for large-scale polynomial
  optimization", ACM Trans. Math. Software 2023, doi 10.1145/3569709; arXiv:2005.02828.
  https://arxiv.org/abs/2005.02828
- J. Wang, V. Magron, J.-B. Lasserre, "Chordal-TSSOS: A Moment-SOS Hierarchy That Exploits Term Sparsity
  with Chordal Extension", SIAM J. Optim., doi 10.1137/20M1323564.
- V. Magron, J. Wang, "Sparse Polynomial Optimization: Theory and Practice", World Scientific 2023;
  arXiv:2208.11158. https://arxiv.org/abs/2208.11158
- TSSOS software: https://github.com/wangjie212/TSSOS

QCQP / SDR tightness boundary + small-QCQP direct methods:
- W. Ai, S. Zhang et al., "On the tightness of an SDP relaxation for homogeneous QCQP with three real or
  four complex homogeneous constraints", Math. Programming 2024, arXiv:2304.04174.
  https://arxiv.org/abs/2304.04174
- D. Cifuentes et al., "On the tightness of SDP relaxations of QCQPs", Math. Programming 2022,
  doi 10.1007/s10107-020-01589-9.
- S. Adachi, Y. Nakatsukasa, eigenvalue-based algorithm for nonconvex QCQP with one constraint
  (TRS by a single generalized eigenvalue problem). https://people.maths.ox.ac.uk/nakatsukasa/preprints/TRSrev2.pdf
- "A linear-time algorithm for generalized trust region subproblems", arXiv:1807.07563.
  https://arxiv.org/abs/1807.07563
- M. Soltanalian, P. Stoica, "Designing Unimodular Codes via Quadratic Optimization", IEEE Trans. Signal
  Process. 62(5):1221-1234, 2014 (MERIT / MM iterations).
- "Tightness of a new and enhanced semidefinite relaxation for MIMO detection", arXiv:1710.02048;
  SIAM J. Optim. https://arxiv.org/abs/1710.02048

Burer-Monteiro / rank bound / caveats:
- G. Pataki, "On the rank of extreme matrices in semidefinite programs and the multiplicity of optimal
  eigenvalues", Math. of OR 23(2):339-358, 1998.
- L. Waldspurger, A. Waters, "The Burer-Monteiro SDP method can fail even above the Barvinok-Pataki
  bound", arXiv:2211.12389. https://arxiv.org/abs/2211.12389
- "Preconditioned Gradient Descent for Overparameterized Nonconvex Burer-Monteiro Factorization with
  Global Optimality Certification", arXiv:2206.03345; JMLR. https://arxiv.org/abs/2206.03345
- L. Baldi, L. Slot, "Slow Convergence of the Moment-SOS Hierarchy for an Elementary Polynomial
  Optimization Problem", SIAM J. Appl. Algebra Geom. 2025, doi 10.1137/24m1645942.
