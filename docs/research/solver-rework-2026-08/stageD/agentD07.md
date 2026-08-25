# Stage D shard D07: MIMO detection, hybrid beamforming (constant-modulus), and sphere decoding

Agent: D07. Method family: MIMO detection and hybrid/RIS beamforming under constant-modulus /
constant-envelope constraints (the continuous side), plus sphere decoding / Schnorr-Euchner integer
least-squares enumeration (the discrete byte-exact side). This is the closest ENGINEERING analogue to
our model: per-element constant modulus, linear channel coupling, plus a lattice/integer layer.

Territory: literature survey with real citations, then applicability mapping onto our model as fixed by
`00-context-pack.md` section 2, `SPEC.md` section 4, and `stage0-copt/FINDINGS.md`. Every applicability
claim is tagged measured-against-our-model or theoretical.

Files/commands actually inspected:
- `docs/research/solver-rework-2026-08/00-context-pack.md`, `SPEC.md`, `stage0-copt/FINDINGS.md`,
  `01-plan.md` (read whole).
- `core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/McSineTable.java` (read whole:
  SIZE=65536, MASK=65535, INDEX_FROM_RAD=10430.378F; confirms the 65536-bucket integer sine LUT and the
  360/65536 = 0.0054931640625 deg bucket spacing the context pack rounds to ~0.0055 deg).
- `core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/JumpLinearModel.java` (read the
  header + precompute: confirms `u_t = m_t(cos phi, sin phi)`, `phi = baseArg_t + yaw_t`, position affine
  via `coef(s,k)=(sPre[k]-sPre[s])/fPre[s]`, only nonconvexity `|u_t|=m_t`; the du/dtheta = i*u Jacobian
  the SLP already uses).
- `core/.../solver/LatticeRepair.java` (read whole: the incumbent discrete repair is a coordinate /
  pairwise grid descent over yaw deltas judged by byte-exact violation; A03/A10 flagged it dead outside
  tests; it is the heuristic that a certified closest-vector solve would replace).
- Web literature (real, checkable; URLs in Sources).

ESTABLISHED = published result or textbook fact. SPECULATION = my inference, routed to Stage E prototype.

---

## D07-1. The constant-modulus + linear-coupling class is a mature engineering field; its canonical solver is the complex-circle manifold, confirming SPEC 4.6

- LOCATION: research topic (foundational provenance for SPEC section 4.1/4.6).
- CLAIM: constant-envelope precoding and unit-modulus hybrid/RIS beamforming solve exactly our structural
  class (optimize a linear or quadratic functional over per-element unit-modulus variables coupled by a
  linear channel), and the field's converged best tool is Riemannian optimization on the product-of-unit-
  circles (complex circle) manifold. This is independent corroboration of SPEC 4.1 ("optimization over the
  product of circles") and 4.6 ("Riemannian product-of-circles / oblique manifold ... the native geometry").
- EVIDENCE: ESTABLISHED. Foundational chain: Mohammed and Larsson, "Per-antenna constant envelope precoding
  for large multi-user MIMO systems," IEEE Trans. Comm. 61(3):1059-1071, 2013 (arXiv:1201.1634) established
  per-element constant-envelope transmit design. Sohrabi and Yu, "Hybrid digital and analog beamforming
  design for large-scale antenna arrays," IEEE JSTSP 10(3):501-513, 2016 (arXiv:1601.06814) set the
  unit-modulus analog phase-shifter constraint that dominates the field. Yu, Shen, Zhang, Letaief,
  "Alternating minimization algorithms for hybrid precoding in millimeter wave MIMO systems," IEEE JSTSP
  10(3):485-500, 2016 (arXiv:1601.07340) introduced MO-AltMin, whose analog subproblem is a
  complex-circle-manifold Riemannian conjugate-gradient solve (their public Manopt code confirms the manifold
  is the complex circle). The 2023-2026 RIS literature has converged on the same tool for unit-modulus phase
  design (see D07-4 for the recent thread). No paper invented here; each has a live URL below.
- IMPACT: simplicity / correctness. Confirms the SPEC's native-geometry claim from a second, large, mature
  literature: our problem is a mild instance of a class the wireless community solves routinely at n in the
  hundreds to thousands, so the sizes we hit (n <= 49, Stage 0) are small for these tools.
- PROPOSAL: adopt the complex-circle manifold as the reference geometry for the continuous residual solve
  (D07-2), and cite this provenance in the Stage E prototype rationale.
- CONFIDENCE: 0.95.
- DEPENDS-ON: none.

---

## D07-2. MO-AltMin is NOT a drop-in for our theta, but its complex-circle RCG engine IS the concrete pure-Java realization of SPEC ARCH-1's "Riemannian on the product of circles" residual solve

- LOCATION: research topic -> SPEC ARCH-1 step 3, SPEC 4.4 (moment/local residual), SPEC C1/C6.
- CLAIM: MO-AltMin's outer alternating-minimization does not transfer (it factors a target matrix; we have
  no matrix to factor), and its manifold subproblem is UNCONSTRAINED on the manifold whereas our difficulty
  IS the linear inequality walls. But the inner engine, Riemannian conjugate gradient (RCG) on the product
  of scaled circles `M = {u in R^{2n} : |u_t| = m_t}`, ports directly and is the concrete, dependency-free
  realization of the residual solve the SPEC leaves as an open method choice.
- EVIDENCE: ESTABLISHED (the engine) + SPECULATION (the fit to us). The complex circle manifold and its RCG
  are textbook (Absil, Mahony, Sepulchre, "Optimization Algorithms on Matrix Matrices/Manifolds," 2008, the
  oblique/circle manifold; already in SPEC 4.6) and are exactly what MO-AltMin (arXiv:1601.07340) and the
  phase-only-beamforming RMOCG method (Fan et al., "RMOCG: A Riemannian Manifold Optimization-Based Conjugate
  Gradient Method for Phase-Only Beamforming Synthesis," IEEE, 2022) use. The operators, per tick, are:
  - tangent space at u_t: directions v_t with `Re(conj(u_t) . v_t) = 0` (v_t orthogonal to the radial u_t);
  - Riemannian gradient: `rgrad = egrad - (Re(conj(egrad) o u_hat)) o u_hat` per tick, i.e. project the
    Euclidean gradient off the radial component (u_hat = u_t/|u_t|);
  - retraction: `R_{u_t}(v_t) = m_t (u_t + v_t)/|u_t + v_t|` (renormalize each tick to its own modulus m_t).
  For US the Euclidean objective gradient is the constant costate `c_t` plus (for a penalized/augmented-
  Lagrangian wall term) `sum_j lambda_j a_j coef_j(t)` (the exact linear wall pull already in
  `JumpLinearModel.Wall.coef`, verified in the file). The mismatch is real: MO-AltMin has NO inequality
  walls on the manifold, so it is not a drop-in; the walls must enter as an AL / penalty term, which is
  precisely what `RelaxationRecovery` already does but on the DISK (|u_t| <= m_t) via AL-FISTA. Moving that
  same AL loop onto the CIRCLE via RCG keeps the modulus EXACT (no disk slack, no dither-rounding step) and
  attacks the exact structure Stage 0 measured as H1 (disk loose by ~1.6e-3 b at 1-4 throttled ticks,
  FINDINGS 1a): on the circle there is no throttling to lose.
- IMPACT: simplicity + correctness. One RCG primitive can serve (i) the low-dim residual of SPEC ARCH-1 step
  3 (1-4 vanishing-costate ticks, Stage 0 FINDINGS 1a/1b) and (ii) a full-n exact-modulus replacement for
  RelaxationRecovery's disk AL-FISTA, which A03-14 measured diverges at n=353 (j001 viol 15.5). Magnitude:
  unquantified until Stage E; the target is to match the COPT continuous optima in FINDINGS 1c
  (j021 1067.863880, j008b -0.196938) within the 0.1-800 ms envelope.
- PROPOSAL: Stage E prototype "circle-manifold penalized RCG" as the recovery kernel, benchmarked against the
  COPT references on j021/j008b/loopmm and the dF-chain captures, in two modes: full-n, and residual-only
  (RCG restricted to the degenerate ticks with all non-degenerate ticks pinned by closed-form costate).
- CONFIDENCE: 0.7 (engine ports for sure; that it beats the incumbent within the envelope is the Stage E
  question).
- DEPENDS-ON: D07-1, D07-3.

---

## D07-3. Our objective is LINEAR, so the pure manifold subproblem is closed-form; RCG earns its keep ONLY where the walls couple, which is exactly the small residual

- LOCATION: research topic -> SPEC 4.2 (the reduction), SPEC 4.1.
- CLAIM: MO-AltMin/RMOCG need RCG because their objective is QUADRATIC on the manifold. Ours is a LINEAR
  functional `c^T u`. The unconstrained maximum of a linear functional over the product of circles is
  closed-form per tick, `u_t = m_t c_t/|c_t|`, which is IDENTICALLY the SPEC's costate recovery (SPEC 4.2,
  line "u_t = m_t g_t/|g_t| whenever g_t != 0"). Therefore iterative manifold optimization buys nothing on
  the non-degenerate ticks; it is needed ONLY where the active walls make the per-tick costate vanish
  (`g_t = 0`) or the inequality walls bind, i.e. the 1-4 degenerate ticks Stage 0 measured. This sharpens
  D07-2: the right use of the beamforming machinery is a LOW-DIMENSIONAL circle-manifold solve, not a full-n
  one, and it inherits the Pataki rank bound (SPEC 4.2) on its dimension.
- EVIDENCE: ESTABLISHED (the closed form is elementary and matches JumpLinearModel + SPEC 4.2) +
  measured-against-our-model (Stage 0 FINDINGS 1a/1b: 0 throttled ticks on single/easy, 1-4 on coupled;
  SDR rank 1 vs 2-3 on the same split). The linear-vs-quadratic distinction is why our class is MILDER than
  the UQP/CMQP the beamforming papers target (SPEC 4.1 already states this; Soltanalian and Stoica,
  "Designing unimodular codes via quadratic optimization," IEEE TSP 62(5):1221-1234, 2014 is the quadratic
  parent class).
- IMPACT: simplicity + speed. Confines the ported iterative solver to a 1-4 dimensional problem in the
  common coupled case, making worst-case cost irrelevant and the port trivially within the envelope.
- PROPOSAL: in Stage E, always run the closed-form costate recovery first; hand ONLY the vanishing-costate /
  binding-wall ticks to the circle-manifold RCG (or the sphere-decode of D07-5). This is the D07 rendering of
  SPEC ARCH-1.
- CONFIDENCE: 0.85.
- DEPENDS-ON: D07-2.

---

## D07-4. Projected-gradient-descent for unit-modulus least squares is a lighter, convergence-guaranteed alternative to RCG for the circle solve

- LOCATION: research topic -> SPEC ARCH-1 step 3, port-cost.
- CLAIM: a simpler primitive than RCG exists for the same circle constraint: projected gradient descent
  (PGD) that after each gradient step renormalizes every u_t back to modulus m_t. Recent work proves it
  converges linearly near the solution for unit-modulus least squares, so it is a defensible, minimal
  pure-Java kernel if full RCG (conjugate directions + line search) is more than we need.
- EVIDENCE: ESTABLISHED. Liu, Cui, et al. (Wang/So group), "On Local Linear Convergence of Projected
  Gradient Descent for Unit-Modulus Least Squares," IEEE TSP 71:3947-3961, 2023, proves local linear
  convergence of the renormalize-after-gradient scheme for the unit-modulus constraint. The 2023-2026 RIS
  literature independently confirms complex-circle-manifold RCG as the mainstream tool (survey: Zhou et al.,
  "Phase Shift Design in RIS Empowered Wireless Networks: From Optimization to AI-Based Methods,"
  arXiv:2204.13372; Wu and Zhang, "Intelligent reflecting surface enhanced wireless network via joint active
  and passive beamforming," IEEE TWC 18(11):5394-5409, 2019 is the foundational SDR-with-unit-modulus RIS
  paper the manifold methods later beat). ADMM is the third mature route (Liang et al., "ADMM-based transmit
  beampattern synthesis ... under a constant modulus constraint," Signal Processing, 2020) with derived
  penalty-parameter convergence conditions.
- IMPACT: simplicity. PGD is ~40-80 lines pure Java vs ~150-250 for RCG, with a published local-convergence
  guarantee; a good fallback if RCG's line search is finicky.
- PROPOSAL: Stage E benchmarks PGD-renormalize against RCG on the residual; pick the smaller one that hits
  the COPT reference. Both are dependency-free.
- CONFIDENCE: 0.75.
- DEPENDS-ON: D07-2, D07-3.

---

## D07-5. The byte-exact snap IS a small box-constrained integer least-squares problem after the du = i*u linearization, and Schnorr-Euchner enumeration is the certified solver for it

- LOCATION: research topic -> SPEC 4.5 (discrete byte-exact layer), SPEC C5.
- CLAIM: the discrete layer is a per-tick snap of the continuous yaw to one of 65536 buckets
  (`k_t = (int)(theta_t * 10430.378) & 65535`, verified in McSineTable). Choosing the bucket offsets
  `d_t in Z` near the continuous optimum to best satisfy the byte-exact walls and objective is, LOCALLY
  (within a few buckets, using the exact Jacobian `du_t/dtheta = i*u_t` that JumpLinearModel/SLP already
  use), a BOX-CONSTRAINED INTEGER LEAST-SQUARES / closest-vector problem whose generator matrix is the
  friction-coupled wall-and-objective Jacobian (the coef[] arrays as the effective Gram matrix). Schnorr-
  Euchner sphere-decoding enumeration is the standard certified exact solver for this, and its natural
  "start at the Babai/rounding point, shrink the radius" behavior matches "snap then repair."
- EVIDENCE: ESTABLISHED (the tool) + SPECULATION (the local-linearization fit). Closest-point search:
  Agrell, Eriksson, Vardy, Zeger, "Closest point search in lattices," IEEE Trans. IT 48(8):2201-2214, 2002
  (the AEVZ enumeration and the identification of ILS = closest lattice point). Schnorr and Euchner, "Lattice
  basis reduction: improved practical algorithms and solving subset sum problems," Math. Programming
  66:181-199, 1994 (the enumeration order our snap wants). The MIMO-detection lineage: Damen, El Gamal,
  Caire, "On maximum-likelihood detection and the search for the closest lattice point," IEEE Trans. IT
  49(10):2389-2402, 2003; survey Yang and Hanzo, "Fifty Years of MIMO Detection," arXiv:1507.05138. The
  linearization is exact to first order because the bucket spacing is 0.0055 deg (McSineTable) and the snap
  lives within a handful of buckets of the continuous optimum, where sin/cos is linear to ~1e-8.
- IMPACT: correctness + smoothness. Replaces the incumbent heuristic dither / LatticeRepair coordinate
  descent (which A03/A10 measured dead outside tests and which the shipped path currently reaches via ad-hoc
  dithering) with a CERTIFIED minimal-perturbation bucket assignment. Because the enumeration minimizes total
  angular perturbation subject to feasibility, it simultaneously minimizes the load-bearing "dither"
  described in the handoff (yaw flicks at redirect zones), so it is smoothness-aware for free.
- PROPOSAL: Stage E prototype a Schnorr-Euchner enumeration over bucket offsets on the coupled/redirect
  window only (D07-7 bounds the size), objective = minimize wall-violation-repair then maximize the linear
  objective, seeded at the rounding (Babai) point. Compare its certified snap against the current dither on
  thousand/1 (handoff sine-bucket gap ~3e-4 b) and j021.
- CONFIDENCE: 0.6.
- DEPENDS-ON: D07-6 (the caveat), D07-7 (the size bound).

---

## D07-6. CAVEAT that bounds D07-5: a certified closest-to-continuous snap is NOT a certified byte-exact optimum, because byte-exact can out-reach the continuous model via the half-angle norm excess

- LOCATION: research topic -> SPEC C5, Stage 0 FINDINGS section 4.
- CLAIM: sphere decoding certifies the bucket assignment closest (in the friction-coupled metric) to the
  continuous optimum. It does NOT certify the byte-exact OPTIMUM, because the float32 sine LUT can produce
  `sin^2 + cos^2 > 1` at favorable ("increasing") half-angles, letting the byte-exact reach EXCEED the
  continuous constant-modulus model by up to ~1.0e-2 b (measured). That norm-excess is a rounding gain
  orthogonal to the first-order angular perturbation the ILS models, so the true byte-exact maximizer can sit
  at a bucket the closest-vector metric does not prefer.
- EVIDENCE: ESTABLISHED (measured, Stage 0). FINDINGS section 3-4: production byte-exact OVER-reaches the
  COPT continuous optimum by +3.4e-3 b (j005), +1.0e-2 b (j019); A10 measured a single-tick half-angle reach
  of +1.5e-4 b at gf=135.27, accumulating over friction-amplified 45-strafe air ticks. FINDINGS section 4
  explicitly rules the continuous optimum a near-exact reference, not a strict byte-exact bound.
- IMPACT: correctness (scoping). Sphere decoding is the right tool for a CERTIFIED MINIMAL-PERTURBATION SNAP
  and for principled dither replacement, but it cannot be sold as a byte-exact global optimality certificate.
  That role stays with ExactJumpModel replay (mandatory anyway, SPEC C5) and, where a true discrete optimum
  is wanted, a widened enumeration that scores every candidate by actual ExactJumpModel reach rather than the
  linearized metric.
- PROPOSAL: use SE to PROPOSE a short list of near-closest bucket assignments, then score each by real
  ExactJumpModel reach and keep the best. This preserves certification honesty while capturing the half-angle
  gain. Do not claim SE alone certifies the byte-exact optimum.
- CONFIDENCE: 0.85.
- DEPENDS-ON: D07-5.

---

## D07-7. Sphere-decoding worst-case is exponential, so the port must restrict enumeration to the small coupled/redirect window; on 1-4 ticks it is trivially bounded

- LOCATION: research topic -> port feasibility, SPEC perf envelope.
- CLAIM: full-n sphere decoding cannot be promised polynomial at our scale; the safe, envelope-respecting use
  is to enumerate bucket offsets ONLY over the degenerate / redirect ticks (the same 1-4 ticks D07-3 hands to
  the continuous residual solve), where enumeration over a +-few-bucket box is a handful of nodes.
- EVIDENCE: ESTABLISHED. Hassibi and Vikalo, "On the sphere decoding algorithm I: expected complexity," IEEE
  TSP 53(8):2806-2818, 2005, show expected complexity is polynomial (often ~cubic) in the operating regime,
  BUT Jalden and Ottersten, "On the complexity of sphere decoding in digital communications,"
  IEEE TSP 53(4):1474-1484, 2005, prove the expected complexity is EXPONENTIAL in the dimension at any fixed
  SNR. So no polynomial guarantee at full n=49. Mitigant is structural: the snap perturbation is tiny (bucket
  0.0055 deg; per-bucket wall effect 3.14e-5 to 1.54e-4 b, A10), the friction map is causal and banded
  (JumpLinearModel coef lower-triangular), and only the redirect/degenerate ticks carry the load-bearing
  dither (handoff). Restricting to those ticks bounds the tree to O(w^r) with r = 1-4 and w = a few buckets.
- NOTE: the Jalden and Ottersten exponential-complexity result is the 2005 IEEE TSP paper cited above.
- IMPACT: speed / robustness. Keeps the certified snap inside the 0.1-800 ms envelope by construction.
- PROPOSAL: enumerate SE over the redirect/degenerate window with a hard node cap and a radius seeded from
  the Babai point; fall back to the current dither if the cap is hit (it never should on r <= 4).
- CONFIDENCE: 0.8.
- DEPENDS-ON: D07-5.

---

## D07-8. Port cost: both primitives are pure-Java, dependency-free, and small; no MATLAB/Manopt or numeric dependency is required

- LOCATION: research topic -> SPEC 5 invariants (dependency-free preferred), CODING_GUIDE.
- CLAIM: neither technique needs a shipped dependency. The reference implementations (Manopt for RCG,
  MATLAB/C sphere decoders) are just for provenance; both algorithms are short, self-contained, and fit the
  "dependency-free / analytical preferred" invariant with zero loader packaging cost across Forge 1.8.9 /
  1.12.2 / Fabric.
- EVIDENCE: SPECULATION (sizing) grounded in the algorithm definitions. Circle-manifold RCG: gradient
  projection + a Polak-Ribiere or Fletcher-Reeves beta + Armijo backtracking along the renormalize
  retraction, ~150-250 lines; per-iteration cost O(n * #activeWalls) for the penalized-objective gradient
  (n <= 49, walls <= ~22 in the corpus), i.e. sub-millisecond per iteration, tens of iterations. PGD variant
  (D07-4): ~40-80 lines. Schnorr-Euchner over the small window: a Cholesky/QR of the r x r coupled Gram (r <=
  4) plus a bounded depth-first enumeration, ~120-200 lines, microseconds at r <= 4. All arithmetic is
  double; no BLAS needed at these sizes. Contrast: A04-7 measured re-adding an LP library is net-negative, so
  the dependency-free property is a real advantage here.
- IMPACT: simplicity. Both fold into `core/.../anglesolver/solver/` as leaf classes with no new deps and no
  MC coupling.
- PROPOSAL: implement as `CircleManifoldRecovery` (RCG/PGD) and `SphereSnap` (SE) test-side prototypes first
  (Stage E), behind the same flag discipline as the existing screens.
- CONFIDENCE: 0.7.
- DEPENDS-ON: D07-2, D07-5.

---

## D07-9. VERDICT and SPEC capability mapping: MO-AltMin's circle manifold ports to the CONTINUOUS residual, Schnorr-Euchner ports to the DISCRETE snap; the constant-envelope/hybrid-beamforming outer algorithms do not

- LOCATION: research topic -> SPEC section 6 (ARCH-1/ARCH-2), section 7 open questions.
- CLAIM: of the D07 family, exactly two pieces port, each to a different SPEC capability, and each replacing
  a measured-weak incumbent:
  1. Complex-circle manifold RCG (from MO-AltMin/RMOCG, D07-2/3/4) -> the CONTINUOUS recovery: SPEC ARCH-1
     step 3 residual solve (primary), and a candidate EXACT-modulus replacement for RelaxationRecovery's disk
     AL-FISTA that diverges at n=353 (A03-14). Serves capabilities C1 (point solve) and C6 (multi-jump),
     with free-start p0 as extra linear variables in the same penalized gradient (SPEC 4.5 free-start
     separability) and smoothing as a tie-break term on the degenerate ticks (SPEC 4.5). Best-fit, highest
     confidence.
  2. Schnorr-Euchner sphere decoding (D07-5/6/7) -> the DISCRETE byte-exact snap: SPEC 4.5 discrete layer and
     capability C5 certification, as a certified minimal-perturbation, smoothness-aware replacement for the
     dead LatticeRepair / ad-hoc dither. Scoped by D07-6 (proposes, does not certify the byte-exact optimum)
     and D07-7 (small window only). Lower-value because the discrete drop is measured ~1e-4 b (A10), but it is
     the only route that gives a CERTIFIED snap and a principled account of the load-bearing redirect dither.
  What does NOT port: the outer algorithms of constant-envelope precoding (Mohammed-Larsson) and
  hybrid-beamforming AltMin (Sohrabi-Yu, MO-AltMin outer loop) target different objectives (sum-rate, MSE,
  matrix-factorization Frobenius error) with no linear inequality walls, so they are provenance, not
  transplants. RIS/SDR (Wu-Zhang) is superseded by the manifold methods on our exact structure.
- EVIDENCE: synthesis of D07-1..D07-8 (each with its own evidence tag) against SPEC section 4/6 and Stage 0
  FINDINGS. The single measured anchor for "worth doing": FINDINGS 1c proves the continuous constant-modulus
  QCQP is globally solvable in < 0.3 s at n <= 49, and ILS already reaches within 2.8e-5 b of COPT on j021,
  so a principled circle-manifold residual solve is competing against a near-solution, not a wide gap; its
  value is robustness and collapse (one primitive), not a large objective jump.
- IMPACT: simplicity (one continuous primitive + one discrete primitive replace RelaxationRecovery's disk
  kernel, LatticeRepair, and the dither), correctness (exact modulus + certified snap), with the honest
  caveat (D07-6) that byte-exact certification stays with ExactJumpModel.
- PROPOSAL: route to Stage E: (E-a) circle-manifold penalized RCG/PGD as the ARCH-1 residual kernel,
  benchmarked vs COPT references and vs the incumbent SLP/ILS on j021/j008b/loopmm/dF-chain; (E-b)
  windowed Schnorr-Euchner snap vs the current dither on thousand/1 and j021, scored by ExactJumpModel reach
  per D07-6. Both dependency-free, both inside the envelope by D07-7/8.
- CONFIDENCE: 0.75.
- DEPENDS-ON: D07-1, D07-2, D07-3, D07-4, D07-5, D07-6, D07-7, D07-8.

---

## Sources (all real, checkable)

Continuous side (constant-modulus / manifold):
- Mohammed and Larsson, per-antenna constant envelope precoding, IEEE TC 2013: https://arxiv.org/abs/1201.1634
- Sohrabi and Yu, hybrid digital/analog beamforming, IEEE JSTSP 2016: https://arxiv.org/abs/1601.06814
- Yu, Shen, Zhang, Letaief, MO-AltMin (alternating minimization, manifold), IEEE JSTSP 2016:
  https://arxiv.org/abs/1601.07340 ; code https://github.com/yuxianghao/Alternating-minimization-algorithms-for-hybrid-precoding-in-millimeter-wave-MIMO-systems
- RMOCG phase-only beamforming (complex circle manifold RCG, 2022): https://ieeexplore.ieee.org/document/9779535/
- Soltanalian and Stoica, unimodular codes via quadratic optimization (UQP), IEEE TSP 2014 (parent quadratic class)
- Projected gradient descent for unit-modulus least squares, local linear convergence, IEEE TSP 2023:
  https://dl.acm.org/doi/10.1109/TSP.2023.3324181
- Wu and Zhang, IRS joint active/passive beamforming, IEEE TWC 2019: https://arxiv.org/pdf/1912.01818
- RIS phase-shift design survey (optimization to AI), 2022/updated: https://arxiv.org/pdf/2204.13372
- ADMM transmit beampattern under constant modulus, Signal Processing 2020:
  https://www.sciencedirect.com/science/article/abs/pii/S0165168420300724
- Constant modulus beamforming via convex optimization: https://arxiv.org/pdf/1704.03004

Discrete side (sphere decoding / integer least squares):
- Agrell, Eriksson, Vardy, Zeger, closest point search in lattices, IEEE Trans. IT 2002 (AEVZ enumeration)
- Schnorr and Euchner, lattice basis reduction / enumeration, Math. Programming 1994
- Damen, El Gamal, Caire, ML detection and closest lattice point, IEEE Trans. IT 2003:
  https://www.academia.edu/6851855/On_maximum_likelihood_detection_and_the_search_for_the_closest_lattice_point
- Hassibi and Vikalo, sphere decoding expected complexity (polynomial in regime):
  https://users.ece.utexas.edu/~hvikalo/pubs/paper1r.pdf
- Jalden and Ottersten, complexity of sphere decoding is exponential at fixed SNR, IEEE TSP 2005:
  https://scholar.google.com/scholar_lookup?doi=10.1109/TSP.2005.843746
- Yang and Hanzo, Fifty Years of MIMO Detection (survey): https://arxiv.org/pdf/1507.05138
