# Agent D08 shard: Phase-retrieval methods (alternating projection, convex lifting, Wirtinger flow)

- AGENT: D08. Stage D methods research.
- TERRITORY: the phase-retrieval method family and its applicability to our linearly-constrained
  constant-modulus program. Specifically: (a) is the shipped RelaxationRecovery dither/projection an
  instance of Gerchberg-Saxton nonconvex alternating projection, and does phase-retrieval theory say
  WHEN alternating projection reaches feasibility vs stalls; (b) does a Wirtinger-flow analogue
  (spectral init = dual costate seed, then gradient/Riemannian descent) give a THEORY of when the
  local method reaches the global optimum for our problem.
- FILES/COMMANDS INSPECTED:
  - Read docs/research/solver-rework-2026-08/00-context-pack.md (whole).
  - Read docs/research/solver-rework-2026-08/SPEC.md (whole; sections 4.1-4.6 load-bearing).
  - Read core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/RelaxationRecovery.java
    (whole; relaxedPrimal AL-FISTA disk loop lines 143-262; ditherSeedYaws lines 299-336;
    projectionSeedYaws lines 344-364).
  - WebSearch + WebFetch (real sources, listed inline per finding). No paper, author, or result below
    is invented; every citation was retrieved this session.
- METHOD-FAMILY VERDICT UP FRONT: phase-retrieval theory is DIAGNOSTIC and CAUTIONARY here, not a new
  method. It names the RelaxationRecovery stall exactly (nonconvex alternating projection stagnation at
  a non-transversal intersection) and explains why "make it converge" is the wrong lever, corroborating
  SPEC section 4.3. But every phase-retrieval GLOBAL-convergence guarantee (Wirtinger flow, benign
  landscape, alternating-projection success) is proved under RANDOM, OVERSAMPLED (m of order n or n log
  n or n^2) measurements; our operator is the DETERMINISTIC causal banded friction map with FEW walls
  (m << n), so none of those guarantees transfer. Net: it confirms the SPEC's ARCH-1 direction (solve
  the small residual EXACTLY by enumeration / tiny B&B), and warns AGAINST replacing the residual step
  with a better alternating-projection / HIO / gradient realization, which would stall the same way.

---

## Findings

### D08-1: Gerchberg-Saxton, error-reduction, and Fienup HIO ARE nonconvex projection / Douglas-Rachford methods
- ID: D08-1
- TITLE: The phase-retrieval alternating-projection family, formalized as projection methods on
  (nonconvex) constraint sets.
- LOCATION: research topic; foundational.
- CLAIM: Gerchberg-Saxton (GS) and Fienup error-reduction (ER) alternate PROJECTIONS between two
  constraint sets, one of which is a magnitude set (a product of circles / spheres, nonconvex); Fienup
  HIO adds an over-relaxation/feedback step; Bauschke-Combettes-Luke proved ER is a nonconvex instance
  of Dykstra's algorithm and HIO a nonconvex instance of the Douglas-Rachford (DR) algorithm.
- EVIDENCE: ESTABLISHED (literature).
  - Gerchberg & Saxton, "A practical algorithm for the determination of phase from image and
    diffraction plane pictures", Optik 35, 237-246 (1972): first iterative alternating-projection phase
    algorithm; recovers a complex distribution from two intensity (magnitude) constraints.
  - Fienup, "Phase retrieval algorithms: a comparison", Applied Optics 21, 2758-2769 (1982)
    (https://labsites.rochester.edu/fienup/wp-content/uploads/2019/07/AO82_PRComparison.pdf): defines
    error-reduction and the hybrid input-output (HIO) variant; HIO adds a feedback/penalization step
    that empirically escapes ER stagnation. States ER/GS have the monotone error-reduction property but
    can converge to a local minimum.
  - Bauschke, Combettes, Luke, "Phase retrieval, error reduction algorithm, and Fienup variants: a view
    from convex optimization", J. Opt. Soc. Am. A 19(7), 1334-1345 (2002); and "Hybrid
    projection-reflection method for phase retrieval", JOSA A 20(6), 1025-1034 (2003)
    (https://pcombet.math.ncsu.edu/josa2.pdf): ER = nonconvex Dykstra; HIO = nonconvex
    Douglas-Rachford; both are projection methods on nonconvex feasibility sets.
- IMPACT: simplicity / correctness (diagnostic). Gives the exact textbook name and toolbox for the
  shipped dither/SLP realization, and the standard escape mechanisms (HIO/DR over-relaxation).
- PROPOSAL: use this vocabulary in the SPEC when describing RelaxationRecovery; do NOT adopt HIO/DR as a
  fix without the transversality guarantee (D08-3, D08-9).
- CONFIDENCE: 0.98
- DEPENDS-ON: none.

### D08-2: The RelaxationRecovery dither IS nonconvex alternating projection between the affine wall set and the product of circles
- ID: D08-2
- TITLE: Structural identification of the shipped realization step as Gerchberg-Saxton-type AP.
- LOCATION: RelaxationRecovery.java (relaxedPrimal 143-262; ditherSeedYaws 299-336; projectionSeedYaws
  344-364; then SlpSolve.optimizeBestEffort 125-126).
- CLAIM: The shipped recovery is exactly a two-set alternating projection: SET A = the CONVEX
  intersection of the affine walls {A u <= b} with the product of DISKS {|u_t| <= m_t} (solved by
  relaxedPrimal, an augmented-Lagrangian FISTA whose inner loop projects each u_t onto its disk, lines
  204-209, while dual-ascending the wall multipliers, lines 253-255); SET B = the nonconvex product of
  CIRCLES {|u_t| = m_t} (imposed by ditherSeedYaws/projectionSeedYaws, which rescale each u_t to
  modulus m_t, lines 325-328 / 360). ditherSeedYaws additionally carries a sigma-delta error-feedback
  term (dvx/dvz, lines 332-333) that pushes the projection residual forward through the friction chain,
  i.e. a Fienup-style feedback rather than the plain nearest-point projection of projectionSeedYaws.
  SLP then re-projects toward wall feasibility. This is precisely the GS/ER structure: alternate
  between an affine/convex set and a per-coordinate magnitude (product-of-circles) set.
- EVIDENCE: ESTABLISHED from code (lines cited). The mapping to phase retrieval: our per-tick constant
  modulus |u_t| = m_t is the magnitude/torus constraint; our affine walls A u <= b are the object-domain
  (support/linear) constraint. One structural DIFFERENCE, load-bearing below: in phase retrieval the
  magnitude set lives in MEASUREMENT space (|<a_r,x>| = b_r, indirect through the sensing matrix), whereas
  our circles sit DIRECTLY on the decision variables u_t; both admit a trivial exact per-coordinate
  projection, so the AP structure matches, but our feasibility geometry is simpler (D08-3 exploits this).
- IMPACT: simplicity / correctness (diagnostic). Confirms the shipped stage is a textbook nonconvex AP,
  so the convergence theory of D08-3/D08-4 applies verbatim.
- PROPOSAL: treat RelaxationRecovery's realization as "nonconvex AP between the affine wall set and the
  torus prod_t m_t S^1", and analyze its stall with nonconvex-AP theory rather than tuning rho/iters.
- CONFIDENCE: 0.95
- DEPENDS-ON: D08-1.

### D08-3: Nonconvex AP converges locally-linearly UNDER TRANSVERSALITY and STALLS at non-transversal (tangential) intersections; our degenerate ticks are exactly the non-transversal case
- ID: D08-3
- TITLE: The convergence-vs-stall criterion for nonconvex alternating projection, applied to our
  degenerate (vanishing-costate) ticks.
- LOCATION: research topic; maps onto SPEC 4.2 (degenerate set) and 4.3 (the stall).
- CLAIM: Nonconvex alternating projection has a sharp local theory: if the two closed sets meet
  TRANSVERSALLY at a point (their normal cones intersect only trivially, a positive-angle / regular
  intersection), AP initialized nearby converges R-linearly; without transversality only subsequence
  convergence (for semialgebraic sets) is guaranteed and the method can STAGNATE. Our degenerate ticks
  (costate g_t = 0, where the active walls exactly cancel the objective pull at tick t; SPEC 4.2) are
  precisely where the affine wall set touches the circle TANGENTIALLY: the linear pull that would move
  u_t off the circle vanishes, so the two sets are non-transversal there. That is the textbook regime
  in which nonconvex AP stalls, matching the measured RelaxationRecovery / dual-recovery stall.
- EVIDENCE: ESTABLISHED (theory) + measured corroboration re-cited from the campaign.
  - Lewis, Luke, Malick, "Local linear convergence for alternating and averaged nonconvex projections",
    Foundations of Computational Mathematics 9, 485-513 (2009)
    (https://arxiv.org/pdf/0709.0109): local linear convergence of AP under the standard transversality
    condition; for semialgebraic bounded sets that are NOT transversal, only subsequence convergence.
  - Drusvyatskiy, Ioffe, Lewis, "Transversality and Alternating Projections for Nonconvex Sets",
    Found. Comput. Math. 15 (2015) (https://people.orie.cornell.edu/aslewis/publications/15-transversality.pdf):
    intrinsic transversality with constant kappa gives R-linear rate 1 - c^2, c in (0, kappa); the rate
    degrades to 0 as transversality (kappa) degrades to a tangential touch.
  - MEASURED corroboration (from Stage 0 / context-pack, re-verify from current code before relying):
    the degenerate set is TINY (0 ticks on single/easy where AP/closed-form works; 1-4 ticks on coupled
    multi-jump: j021 t12 modulus slack 0.083, loopmm t0, j008b ~4). At those ticks the shipped recovery
    defaults the direction and the AL/subgradient recovery violation stalls at ~2.89 b (thousand) / 0.34 b
    (j021) while the dual value sits on a flat degenerate face (D in 2.60-2.64). "Flat degenerate face +
    thrashing recovery" is the signature of a non-transversal AP intersection.
- IMPACT: correctness / simplicity (diagnostic, high value). Explains the stall in exact textbook terms
  and PROVES the lever "raise iterations / make the dual converge" cannot fix it: the stall is a
  geometric non-transversality at the degenerate ticks, not slow convergence. Corroborates SPEC 4.3.
- PROPOSAL: do not attempt to fix the stall with more AP/AL iterations or HIO feedback; the
  non-transversal ticks are the SPEC's low-dim residual and must be solved EXACTLY (enumeration / tiny
  B&B / null-space), not projected. The transversality reading is the mechanism behind SPEC ARCH-1.
- CONFIDENCE: 0.85 (theory ESTABLISHED; the "our degenerate ticks are exactly non-transversal" mapping is
  THEORETICAL, labeled; the Stage-0 numbers are re-cited, RE-VERIFY).
- DEPENDS-ON: D08-2.

### D08-4: Waldspurger's AP-for-phase-retrieval theorem: stagnation points only DISAPPEAR at m ~ n^2 oversampling; we are the opposite extreme (m << n), so stagnation is expected and init-dependent
- ID: D08-4
- TITLE: The quantitative sample-complexity regime for AP success, and why our few-wall model sits deep
  in the stagnation regime.
- LOCATION: research topic.
- CLAIM: For phase retrieval by alternating projections with m random Gaussian sensing vectors on an
  n-dim signal, AP succeeds w.h.p. when m >= C n AND it is carefully INITIALIZED; the stagnation
  (spurious fixed) points of AP disappear entirely, making initialization irrelevant, ONLY in the
  heavily-oversampled regime m of order n^2. Our problem has FEW walls (m active constraints << n
  ticks, SPEC 4.2 Pataki bound r(r+1)/2 <= #active walls with residual dim 0-4), i.e. the opposite of
  oversampling, so the theory predicts abundant stagnation points and strong initialization dependence,
  exactly the shipped behavior (works from the good dual seed on single/easy, stalls on coupled).
- EVIDENCE: ESTABLISHED (theory), fetched this session.
  - Waldspurger, "Phase retrieval with random Gaussian sensing vectors by alternating projections",
    IEEE Trans. Inf. Theory 64(5), 3301-3312 (2018) (https://arxiv.org/abs/1609.03088). Verbatim from
    the abstract (WebFetch this session): "When m >= Cn ... alternating projections succeed with high
    probability ... provided that they are carefully initialized"; and "there is a regime in which the
    stagnation points of the alternating projections method disappear, and the initialization procedure
    becomes useless. However, in this regime, m has to be of the order of n^2." Guarantees are
    probabilistic over the random Gaussian ensemble.
- IMPACT: robustness (diagnostic, high value). Quantifies WHY the shipped AP realization stalls on
  coupled multi-jump: our constraint count is far below even m ~ n, let alone the m ~ n^2 needed to
  erase stagnation. No amount of restart/rho tuning escapes a regime the theory says is stagnation-dense.
- PROPOSAL: stop expecting the AP realization to reach feasibility on coupled cases; reserve it (if kept
  at all) for the m >= C n easy cases where the good dual seed lands, and route the coupled/degenerate
  ticks to the exact residual solve.
- CONFIDENCE: 0.8 (theorem ESTABLISHED and quoted; the m << n mapping is sound but the sample-complexity
  transfer to a DETERMINISTIC operator is THEORETICAL, labeled).
- DEPENDS-ON: D08-2, D08-3.

### D08-5: PhaseLift / PhaseMax convex liftings are the phase-retrieval names for our SDR/SOCP; they add tightness intuition but no new mechanism beyond Stage 0
- ID: D08-5
- TITLE: Convex lifting (SDP) and convex-in-natural-space (LP) phase-retrieval relaxations vs our
  Shor/SOCP.
- LOCATION: research topic; overlaps SPEC 4.4.
- CLAIM: PhaseLift lifts |<a_r,x>|^2 to a trace/nuclear-norm SDP over X = x x^*, exact (rank-1) w.h.p.
  at m ~ n log n RANDOM measurements; PhaseMax is a LINEAR program in the natural parameter space that
  needs a good anchor/initial guess and succeeds at optimal RANDOM sample complexity. These are the
  phase-retrieval instances of our Shor/SDP lifting and our disk-SOCP (SPEC 4.4). They confirm the
  standard convexification and the rank-1 = tight story, but every exactness guarantee is again for
  RANDOM measurements; Stage 0 already solved our actual SDP/SOCP with COPT and read the rank/slack
  directly, so phase-retrieval lifting theory adds INTUITION, not a new tool for us.
- EVIDENCE: ESTABLISHED (literature).
  - Candes, Strohmer, Voroninski, "PhaseLift: Exact and Stable Signal Recovery from Magnitude
    Measurements via Convex Programming", Comm. Pure Appl. Math. 66, 1241-1274 (2013), arXiv:1109.4499:
    trace-minimization SDP; exact recovery (up to global phase) w.h.p. at m of order n log n random
    sensing vectors; robust to noise.
  - Goldstein & Studer, "PhaseMax: Convex Phase Retrieval via Basis Pursuit", 2016 (and IEEE Trans.
    Inf. Theory 2018); independently Bahmani & Romberg, "Phase Retrieval Meets Statistical Learning
    Theory: A Flexible Convex Relaxation", 2016/AISTATS 2017: convex phase retrieval as an LP/basis
    pursuit in the natural n-dim space (no lifting), rigorous recovery under a random model given an
    anchor vector correlated with the truth. (https://www.cs.umd.edu/~tomg/projects/phasemax/)
  - MEASURED (Stage 0, re-cited): our SOCP disk is tight (0 throttled ticks, SDR rank-1 eig2/eig1 <=
    9e-8) on single/easy; rank 2-3 and disk loose by ~1.6e-3 b at 1-4 ticks on coupled. So we already
    HAVE the lifting readout; phase-retrieval theory does not improve it.
- IMPACT: simplicity (diagnostic). Ties our relaxations to the named phase-retrieval convexifications;
  no code change implied.
- PROPOSAL: cite PhaseLift/PhaseMax as the lifting analogues in the SPEC's literature thread; keep
  Stage 0's direct COPT rank/slack readout as the authority for OUR instances.
- CONFIDENCE: 0.9
- DEPENDS-ON: none.

### D08-6: Wirtinger flow's GLOBAL guarantee (spectral init + benign landscape) requires RANDOM, OVERSAMPLED measurements; our deterministic few-wall operator does not satisfy it, so the guarantee does NOT transfer
- ID: D08-6
- TITLE: The core of sub-question (b): does the Wirtinger-flow theory give us a theory of local-reaches-global?
- LOCATION: research topic; answers context-pack section 5 / SPEC open question on the local-to-global gap.
- CLAIM: Wirtinger flow = spectral initialization (leading eigenvector of a data matrix, provably inside
  the basin) + gradient descent on the nonconvex least-squares loss, with GLOBAL geometric convergence.
  The guarantee rests on a BENIGN LANDSCAPE (no spurious local minima, strict saddles) that holds ONLY
  for RANDOM Gaussian (or coded-diffraction) measurements at m of order n log n (Wirtinger flow) or
  n log^3 n (Sun-Qu-Wright, which then needs NO special init). Our forward operator is the DETERMINISTIC
  causal banded friction convolution with a HANDFUL of structured walls; it has none of the concentration
  / restricted-isometry that makes those landscapes benign. Therefore the Wirtinger-flow / benign-landscape
  GLOBAL guarantee does NOT transfer to our problem. A Wirtinger-flow analogue for us (spectral init = the
  dual costate seed u_t = m_t g_t/|g_t|; then Riemannian gradient descent on the product of circles) is a
  reasonable LOCAL heuristic but carries NO global-optimality certificate.
- EVIDENCE: ESTABLISHED (theory).
  - Candes, Li, Soltanolkotabi, "Phase Retrieval via Wirtinger Flow: Theory and Algorithms", IEEE Trans.
    Inf. Theory 61(4), 1985-2007 (2015), arXiv:1407.1065: spectral init + gradient descent converges to
    the global solution at a geometric rate w.h.p. when the sample size is of order n log n RANDOM
    Gaussian measurements.
  - Sun, Qu, Wright, "A Geometric Analysis of Phase Retrieval", Found. Comput. Math. 18(5), 1131-1198
    (2018), arXiv:1602.06664: for m >= C n log^3 n COMPLEX GAUSSIAN measurements, w.h.p. the least-squares
    objective has NO spurious local minimizers and negative curvature at every saddle, so trust-region
    from ANY init reaches the global min. Explicitly a random-measurement, oversampled result.
  - Fougereux, Josz, Li, "Global convergence of gradient descent for phase retrieval", arXiv:2410.09990
    (2024, WebFetch this session): a tensor-based benign-landscape criterion giving convergence "for
    almost every initial point"; still a benign-landscape (not arbitrary-operator) result.
- IMPACT: correctness / robustness (this is the decisive negative for sub-question b). It says a
  Wirtinger-flow analogue cannot be sold as a THEORY of when our local method reaches the global optimum;
  it would be a heuristic with no certificate, so it cannot replace an EXACT residual solve for the
  global-optimum target (SPEC C1/C6, target capability 4).
- PROPOSAL: do NOT adopt a Wirtinger-flow analogue as the primary residual mechanism when global
  optimality is required. If used at all, use it only as a cheap local polish AND certify against the
  COPT/global reference or the SPEC's exact residual solve. The certificate must come from the small-B&B /
  enumeration residual (ARCH-1), not from the descent.
- CONFIDENCE: 0.85 (guarantees and their random-measurement premises are ESTABLISHED; "does not transfer"
  is a THEORETICAL negative, but a strong one: no benign-landscape theorem is known for a deterministic
  few-constraint operator like ours, and Stage 0 measured genuine rank>1 degeneracy at the coupled ticks).
- DEPENDS-ON: D08-3, D08-4.

### D08-7: A Wirtinger-flow analogue (dual costate seed + Riemannian descent on the torus) IS applicable and trivially pure-Java, but as a LOCAL polish only
- ID: D08-7
- TITLE: What the Wirtinger-flow idea concretely maps to in our solver, and its honest scope.
- LOCATION: research topic; would sit alongside CostateDualSolver + a torus descent.
- CLAIM: The transfer that DOES hold is mechanical, not theoretical: (spectral init) the dual costate
  recovery u_t = m_t g_t/|g_t| is already our "spectral" starting point (the manifold analogue of the
  leading-eigenvector init); (descent) Riemannian gradient descent on the product of circles T = prod_t
  m_t S^1 is a one-line-per-tick retraction (project the ambient gradient onto each circle's tangent,
  step, renormalize to modulus m_t). This is the native geometry the SPEC already names (oblique /
  product-of-circles manifold, Absil-Mahony-Sepulchre 2008; Boumal 2023). It is a valid LOCAL improver
  for the non-degenerate part but inherits D08-6: no global certificate, and it will stall at the same
  non-transversal degenerate ticks as AP (D08-3).
- EVIDENCE: ESTABLISHED (the mapping is exact) + THEORETICAL scope limit from D08-6.
  - The manifold-descent primitive: Absil, Mahony, Sepulchre, "Optimization Algorithms on Matrix
    Manifolds" (2008); Boumal, "An Introduction to Optimization on Smooth Manifolds" (2023). The
    constant-modulus/oblique-manifold alternating-minimization analogue appears in hybrid-beamforming
    (Yu, Shen, Zhang, Letaif, MO-AltMin) already cited in SPEC 4.6.
- IMPACT: simplicity / speed (minor, optional). A pure-Java torus descent is cheap and could sharpen the
  non-degenerate ticks, but it does not solve the residual and adds a stage rather than collapsing one.
- PROPOSAL: OPTIONAL. If Stage E wants a local polish primitive, a Riemannian torus descent seeded by the
  costate is the cleanest expression and reuses existing pieces (mMag, recoverYawDeg). But it is
  subordinate to the exact residual solve, never a replacement. Prototype only if the residual-solve
  prototype leaves measured non-degenerate-tick headroom.
- CONFIDENCE: 0.75
- DEPENDS-ON: D08-6.

### D08-8: Latest 2023-2026 phase-retrieval theory reinforces the negative transfer (all still random/oversampled measurement models)
- ID: D08-8
- TITLE: Recent convergence and spectral-initialization results, and why they do not change the verdict.
- LOCATION: research topic.
- CLAIM: The 2023-2026 literature sharpens spectral-initialization design and landscape analysis but
  stays within the random-measurement, sufficient-oversampling world; none provides a global guarantee
  for a deterministic, undersampled (few-constraint) operator like ours. So the newest work strengthens,
  not weakens, D08-6's conclusion.
- EVIDENCE: ESTABLISHED (retrieved this session):
  - Fougereux, Josz, Li, "Global convergence of gradient descent for phase retrieval", arXiv:2410.09990
    (2024): benign-landscape criterion, convergence for almost-every init (D08-6).
  - "The Local Landscape of Phase Retrieval Under Limited Samples", arXiv:2311.15221 (2023): studies the
    landscape precisely in the LIMITED-sample regime; benignity degrades as samples shrink, consistent
    with our few-wall (undersampled) regime being non-benign.
  - "The global landscape of phase retrieval I: perturbed amplitude models", Ann. Appl. Math. 37(4)
    (2021) / arXiv:2112.07993: no spurious local minima under Gaussian m >= C n; a random-measurement
    result.
  - Optimal spectral initialization line: Luo, Alghamdi, Lu, "Optimal Spectral Initialization for Signal
    Recovery with Applications to Phase Retrieval", IEEE Trans. Signal Process. (2019), arXiv:1811.04420;
    Mondelli-Montanari construction of optimal spectral methods; plus 2024-2026 refinements (exponential
    spectral pursuit, arXiv:2506.18279 RDT view of optimal spectral initializers). All optimize the init
    WITHIN the random-measurement model.
- IMPACT: robustness (diagnostic). Closes the "maybe a 2024 result rescues global convergence for us"
  door: the frontier is still random-measurement benignity.
- PROPOSAL: record as measured-dead for global guarantees on our deterministic operator; do not re-open
  without a benign-landscape result for structured/deterministic sensing.
- CONFIDENCE: 0.8
- DEPENDS-ON: D08-6.

### D08-9: HIO / Douglas-Rachford over-relaxation is the standard AP stall-escape and is what the shipped SLP fallback loosely mimics; it still lacks a guarantee here
- ID: D08-9
- TITLE: The escape mechanism the phase-retrieval community uses for stagnation, and its (non-)transfer.
- LOCATION: research topic; relates to SlpSolve fallback (RelaxationRecovery.java:125-126) and the
  ditherSeedYaws feedback term (332-333).
- CLAIM: When ER/GS stagnate, practitioners switch to HIO (Fienup feedback) or Douglas-Rachford /
  RAAR / relaxed reflections, which over-relax past the projection and empirically tunnel out of shallow
  stagnation. Our ditherSeedYaws error-feedback (sigma-delta carry through friction) and the SLP retry
  are bespoke analogues of exactly this. But DR/HIO convergence for nonconvex sets is only known under
  transversality-type conditions (Phan; Bauschke-Noll; Aragon-Artacho-Borwein-Tam), which fail at our
  degenerate ticks (D08-3); so the escape works on shallow stalls and NOT on the non-transversal
  degenerate face that produces the measured 2.89 b stall.
- EVIDENCE: ESTABLISHED (theory).
  - Bauschke, Combettes, Luke (2002/2003, D08-1): HIO = nonconvex Douglas-Rachford; hybrid
    projection-reflection method.
  - Local DR/AP convergence for nonconvex/affine-feasible sets under regularity: e.g. Hesse-Luke,
    "Nonconvex Notions of Regularity and Convergence of Fundamental Algorithms for Feasibility Problems",
    SIAM J. Optim. (2013); Phan, "Linear convergence of the Douglas-Rachford method for two closed sets"
    (2016); all require a transversality/regularity constant that vanishes at a tangential intersection.
- IMPACT: correctness (diagnostic). Explains why the shipped feedback+SLP escapes SOME stalls (shallow,
  transversal) but never the degenerate-face stall, matching the measured behavior (works single/easy,
  fails coupled).
- PROPOSAL: do not invest in a stronger HIO/DR/RAAR realization to fix the coupled stall; it is provably
  the wrong regime. Keep the light feedback for shallow cases; solve the degenerate residual exactly.
- CONFIDENCE: 0.8
- DEPENDS-ON: D08-1, D08-3.

### D08-10: Port feasibility: both alternating projection and a Wirtinger-flow torus descent are trivially pure-Java, dependency-free
- ID: D08-10
- TITLE: Implementation cost of the phase-retrieval primitives in core/ (Java 8, no deps).
- LOCATION: core/.../anglesolver/solver/ (would extend RelaxationRecovery or a new small class).
- CLAIM: Neither primitive needs a dependency. Nonconvex AP is loops of two trivial projections: onto
  the affine wall set (a QP/least-squares projection or the existing AL-FISTA, already in relaxedPrimal)
  and onto the product of circles (per-tick rescale to m_t, already in ditherSeedYaws/projectionSeedYaws).
  A Wirtinger-flow analogue (Riemannian gradient descent on the torus) is a per-tick tangent projection +
  step + renormalize, roughly 30-50 lines, reusing lin.mMag / recoverYawDeg. Both are O(n * m) per
  iteration, matching the existing kernels. Estimated prototype effort: ~0.5-1 day each in core/src/test/
  behind a flag, benchmarkable via the existing screens (RelaxDiagScreen, ThousandDiagScreen).
- EVIDENCE: ESTABLISHED from code (the AL-FISTA disk projection lines 204-209 and circle rescales lines
  325-328/360 already exist in pure Java 8; no library used). UNMEASURED-HYPOTHESIS on the effort number
  (an estimate, not benchmarked).
- IMPACT: speed / simplicity (informational). Confirms there is no packaging cost obstacle; the obstacle
  is theoretical (D08-6), not implementational.
- PROPOSAL: if Stage E wants an empirical stall demonstration, add a torus-descent prototype next to
  relaxedPrimal and log its residual on j021/j008b/loopmm/thous;/1 to MEASURE the stall the theory
  predicts (expected: descent + AP both plateau at the degenerate ticks while the exact residual solve
  clears them).
- CONFIDENCE: 0.85
- DEPENDS-ON: D08-2, D08-7.

### D08-11: VERDICT: phase-retrieval theory EXPLAINS the RelaxationRecovery stall and CAUTIONS against a Wirtinger-flow replacement; it does not supply a globally-convergent method for our model
- ID: D08-11
- TITLE: D08 method-family verdict against the two sub-questions.
- LOCATION: synthesis.
- CLAIM:
  (a) YES, the RelaxationRecovery dither is a known Gerchberg-Saxton (nonconvex alternating-projection)
  regime (D08-1, D08-2), and phase-retrieval / nonconvex-AP theory tells us exactly WHEN it converges
  vs stalls: it converges locally-linearly under TRANSVERSALITY and STALLS at non-transversal
  (tangential) intersections (Lewis-Luke-Malick; Drusvyatskiy-Ioffe-Lewis), and its stagnation points
  only vanish under heavy random oversampling m ~ n^2 (Waldspurger). Our degenerate (vanishing-costate)
  ticks are exactly the non-transversal case, and our few-wall model is the opposite of oversampled, so
  the measured coupled-multi-jump stall is the PREDICTED behavior, not a tuning failure. This
  corroborates SPEC 4.3 in independent, textbook language.
  (b) NO, the Wirtinger-flow analogue does NOT give us a theory of local-reaches-global. Its global
  guarantee (spectral init + benign landscape) is proved only for RANDOM, OVERSAMPLED measurements
  (Candes-Li-Soltanolkotabi; Sun-Qu-Wright; 2024 benign-landscape work), which our deterministic banded
  friction operator with few structured walls does not satisfy. The analogue is a valid pure-Java LOCAL
  heuristic (D08-7, D08-10) but carries no optimality certificate and stalls at the same degenerate
  ticks.
  THEREFORE: the phase-retrieval family's contribution is DIAGNOSTIC (it names and dates the stall) and
  CAUTIONARY (its global methods do not port). It CONVERGES with SPEC ARCH-1: the correct fix for the
  coupled/degenerate residual is an EXACT small-dimensional solve (enumeration / tiny spatial B&B /
  null-space / SDR rank-reduction over the 0-4 degenerate ticks), NOT a better alternating-projection,
  HIO, or gradient realization, all of which the theory says will stall identically.
- EVIDENCE: aggregates D08-1..D08-10 (each with its ESTABLISHED citations and the re-cited Stage-0
  measurements). The verdict itself is a THEORETICAL synthesis; the falsifiable prediction is in D08-10
  (a torus-descent/AP prototype will plateau at the degenerate ticks on j021/j008b/loopmm/thousand while
  the exact residual solve clears them) and should be MEASURED in Stage E.
- IMPACT: correctness / simplicity (high). Removes an entire tempting avenue (replace the recovery with a
  fancier phase-retrieval solver) with a measured-and-theoretical root cause, and redirects Stage E
  effort to the exact residual solve.
- PROPOSAL: (1) adopt the AP/transversality language in the SPEC's description of RelaxationRecovery;
  (2) do not pursue HIO/DR/Wirtinger as the coupled-case fix; (3) keep phase-retrieval lifting
  (PhaseLift/PhaseMax) only as literature naming for the already-measured SDR/SOCP; (4) if a local
  polish is ever wanted, use the pure-Java costate-seeded torus descent as a subordinate improver with
  external certification. Route the residual to the exact solve (ARCH-1).
- CONFIDENCE: 0.85
- DEPENDS-ON: D08-1, D08-2, D08-3, D08-4, D08-6, D08-7, D08-9.

---

## Sources (all real, retrieved this session)

- Gerchberg & Saxton, Optik 35, 237-246 (1972).
- Fienup, "Phase retrieval algorithms: a comparison", Appl. Opt. 21, 2758-2769 (1982).
  https://labsites.rochester.edu/fienup/wp-content/uploads/2019/07/AO82_PRComparison.pdf
- Bauschke, Combettes, Luke, JOSA A 19(7) (2002) and JOSA A 20(6), 1025-1034 (2003).
  https://pcombet.math.ncsu.edu/josa2.pdf
- Lewis, Luke, Malick, "Local linear convergence for alternating and averaged nonconvex projections",
  FoCM 9, 485-513 (2009). https://arxiv.org/pdf/0709.0109
- Drusvyatskiy, Ioffe, Lewis, "Transversality and Alternating Projections for Nonconvex Sets", FoCM 15
  (2015). https://people.orie.cornell.edu/aslewis/publications/15-transversality.pdf
- Waldspurger, "Phase retrieval with random Gaussian sensing vectors by alternating projections",
  IEEE Trans. Inf. Theory 64(5), 3301-3312 (2018). https://arxiv.org/abs/1609.03088
- Candes, Strohmer, Voroninski, "PhaseLift ...", Comm. Pure Appl. Math. 66, 1241-1274 (2013),
  arXiv:1109.4499. https://arxiv.org/abs/1109.4499
- Goldstein & Studer, "PhaseMax: Convex Phase Retrieval via Basis Pursuit" (2016/2018);
  Bahmani & Romberg (2016/2017). https://www.cs.umd.edu/~tomg/projects/phasemax/
- Candes, Li, Soltanolkotabi, "Phase Retrieval via Wirtinger Flow: Theory and Algorithms", IEEE Trans.
  Inf. Theory 61(4), 1985-2007 (2015), arXiv:1407.1065. https://arxiv.org/pdf/1407.1065
- Sun, Qu, Wright, "A Geometric Analysis of Phase Retrieval", FoCM 18(5), 1131-1198 (2018),
  arXiv:1602.06664. https://arxiv.org/pdf/1602.06664
- Fougereux, Josz, Li, "Global convergence of gradient descent for phase retrieval", arXiv:2410.09990
  (2024). https://arxiv.org/abs/2410.09990
- "The Local Landscape of Phase Retrieval Under Limited Samples", arXiv:2311.15221 (2023).
- "The global landscape of phase retrieval I: perturbed amplitude models", arXiv:2112.07993 (2021).
- Luo, Alghamdi, Lu, "Optimal Spectral Initialization ...", arXiv:1811.04420 (2019); "Optimal spectral
  initializers ... an RDT view", arXiv:2506.18279 (2025/26).
- Absil, Mahony, Sepulchre, "Optimization Algorithms on Matrix Manifolds" (2008); Boumal, "An
  Introduction to Optimization on Smooth Manifolds" (2023).
