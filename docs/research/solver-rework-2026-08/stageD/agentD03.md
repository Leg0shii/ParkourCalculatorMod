# Stage D shard: agent D03

Agent: D03
Territory (research): SDP rank bounds (Pataki, Barvinok) and SDR rank reduction / rank-one recovery.
The theoretical backbone of the SPEC's "low-dimensional residual" claim (section 4.2) and the practical
question of EXTRACTING a low-rank / rank-1 constant-modulus solution from the SDP.

Sources actually inspected this session (WebSearch/WebFetch verified; PDFs read locally where cited):
- Pataki 1998 (INFORMS abstract + secondary confirmations).
- Im & Wolkowicz 2021, "A strengthened Barvinok-Pataki bound on SDP rank": full 8-page PDF read
  (theorems 2.1, 2.5, 2.11, corollary 2.13 transcribed).
- Boumal, Voroninski, Bandeira 2019/2020, "Deterministic guarantees for Burer-Monteiro ...": pages 1-4
  read (Barvinok-Pataki review in sec 2.2, Theorem 1.4, the Burer-Monteiro "flat face" caveat quote).
- Sturm & Zhang 2003; Huang & Zhang 2007; Ai & Zhang 2009; Beck & Eldar 2006; Goemans & Williamson 1995;
  Wang & Kilinc-Karzan 2022; Ai, Liang & Yuan 2024 (arXiv 2304.04174); Kojima, Kim & Arima 2026; STRIDE
  (Yang, Liang, Toh, Carlone) GitHub + Math Prog 2023; SE-Sync (Rosen et al 2019): abstracts/READMEs
  fetched and cross-checked.

Code re-verified for the applicability analysis: `JumpLinearModel.compileWall` (each wall is a
single-axis linear functional `a . sum_s coef[s] u_s <= bPrime`; the only equalities are the n per-tick
moduli). Cross-read against `stage0-copt/FINDINGS.md` (eig2/eig1, modulus slack, throttled-tick counts)
and `SPEC.md` section 4.2.

All eigen-spectrum / slack / rank numbers below are Stage 0's COPT measurements (re-cited, not re-run by
D03). All theorem statements are ESTABLISHED literature. All applicability claims are labeled
ESTABLISHED / THEORETICAL / UNMEASURED-HYPOTHESIS.

---

## Findings

### D03-1
TITLE: The Barvinok-Pataki rank bound, stated exactly, counts AFFINE constraints, not "walls".
LOCATION: Research. Pataki 1998; Barvinok 1995/2001.
CLAIM: For a spectrahedron `F = {X >= 0 : A(X) = b}` with `A: S^n -> R^m` (m linearly independent affine
EQUALITY constraints), every extreme point `X in F` satisfies `t(rank X) <= m`, where `t(r) = r(r+1)/2`
is the triangular number; equivalently `rank X <= floor((sqrt(8m+1)-1)/2)`. For a face `F` of dimension
`d`, `t(rank X) <= m + d` (Pataki Thm 2.1). The bound depends SOLELY on the constraint algebra, not the
objective or the data values.
EVIDENCE: ESTABLISHED. Pataki, "On the Rank of Extreme Matrices in Semidefinite Programs and the
Multiplicity of Optimal Eigenvalues", Math. of OR 23(2):339-358, 1998. Barvinok, "Problems of distance
geometry and convex properties of quadratic maps", Discrete Comput. Geom. 13:189-202, 1995; and "A remark
on the rank of positive semidefinite matrices subject to affine constraints", DCG 25:23-31, 2001. Exact
inequality `t(rank X) <= m` transcribed verbatim from Im-Wolkowicz 2021 Thm 2.5 (which cites Barvinok [1]
and Pataki [7]).
IMPACT: correctness of the SPEC's math framing. The bound is the right tool but the count `m` must be
taken literally (all affine equalities), which the SPEC's "#active walls" phrasing under-specifies (D03-4).
PROPOSAL: adopt the exact statement in SPEC section 4.2; write `m` as the number of linearly independent
constraints ACTIVE at the optimal face, and enumerate them (D03-4).
CONFIDENCE: 0.98
DEPENDS-ON: -

### D03-2
TITLE: To bound the rank of an OPTIMAL (not merely feasible) SDP solution, add the objective hyperplane;
the optimal-face rank bound is the operative one for us.
LOCATION: Research. Pataki 1998 Thm 2.1; Im-Wolkowicz 2021 sec 1.
CLAIM: For an SDP `min <C,X> s.t. A(X)=b, X>=0`, the optimal solutions form a face `F* = F ∩ {<C,X> = p*}`.
Applying the extreme-point bound on `F*` counts the `m` original equalities PLUS the objective equality
PLUS every inequality active at the optimum, all as binding affine constraints. So the number the bound
uses for the OPTIMAL rank is `m_opt = (#equalities) + 1 (objective) + (#active inequalities)`, restricted
to a linearly independent subset.
EVIDENCE: ESTABLISHED. Im-Wolkowicz 2021 sec 1 note "for a linear SDP ... we can add the constraint
`f(X)=p*` to apply the rank bound for optimal solutions". Pataki 1998 Thm 2.1 (`t(r) <= m + d`).
IMPACT: correctness. Fixes the exact count to plug into the bound for j021/j008b/loopmm.
PROPOSAL: use `m_opt` (D03-4) not "#walls" when quoting the residual-dimension bound.
CONFIDENCE: 0.96
DEPENDS-ON: D03-1

### D03-3
TITLE: The strengthened Barvinok-Pataki bound (singularity degree / facial reduction) tightens the rank
when Slater fails; directly relevant because constant-modulus lifts are often not strictly feasible.
LOCATION: Research. Im & Wolkowicz 2021.
CLAIM: If the singularity degree `s = sd(F) > 0` (the minimum number of facial-reduction steps to reach
strict feasibility), then there is a solution with `t(rank X) <= min{t(n - s), m - s}` (Thm 2.11), hence
`rank X <= floor((sqrt(1 + 8 min{t(n-s), m-s}) - 1)/2)` (Cor 2.13). Each facial-reduction step removes at
least one linearly independent constraint (Lemma 2.7) and drops the embedding dimension by at least one,
so a non-Slater spectrahedron has provably lower rank than the classic count predicts. `sd(F) <= min{n-1, m}`.
EVIDENCE: ESTABLISHED, LATEST. Im & Wolkowicz, "A strengthened Barvinok-Pataki bound on SDP rank", 2021
(optimization-online 8346; Operations Research Letters). Read locally: Thm 2.11 `t(r) <= min{t(n-s), m-s}`,
Cor 2.13, and Example 2.16 where `sd(F)=2` proves an all-rank-1 optimal face for a `S^4` SDP whose naive
count allowed rank 3.
IMPACT: correctness + it is a candidate a-priori certificate. Our Shor lift of a constant-modulus program
pins the diagonal blocks to exact values `|u_t|^2 = m_t^2`, which frequently costs strict feasibility, so
`s > 0` is plausible and would formally lower the rank bound toward the measured 2-3.
PROPOSAL: as a Stage E measurement, compute `sd(F)` (facial-reduction depth) on the COPT-dumped j021/j008b
SDP and check whether the strengthened bound predicts the measured rank 2-3 where the classic bound does
not (D03-4). UNMEASURED-HYPOTHESIS that `s > 0` for our lifts.
CONFIDENCE: 0.85
DEPENDS-ON: D03-1

### D03-4
TITLE: The SPEC's "r(r+1)/2 <= #active walls" is a LOOSE (and strictly incorrect) application of Pataki to
the full Shor SDP; the true classic count includes the n per-tick modulus equalities.
LOCATION: Research + code (`JumpLinearModel.compileWall`) + `SPEC.md` sec 4.2, `stage0-copt/FINDINGS.md` 1b.
CLAIM: Our real Shor lift has decision matrix `M` of side `2n+1` (real) with equality constraints:
1 normalization (`M[0,0]=1`), `n` modulus equalities (2x2 diagonal-block trace `= m_t^2`), the objective
hyperplane, plus the active walls. So `m_opt = n + 2 + #active_walls`. For j021 (`n=39`, 1 degenerate tick,
few active walls) this is `m_opt ~ 45`, giving the classic bound `rank <= floor((sqrt(8*45+1)-1)/2) = 9`,
NOT 2-3. The measured SDR rank is 2-3 (`eig2/eig1 <= 0.024`, Stage 0). Therefore the classic Barvinok-Pataki
bound on the FULL lift is a VALID upper bound but LOOSE by ~3-4x; "#active walls" alone under-counts by
omitting the n moduli and only coincidentally lands near the small measured rank.
EVIDENCE: THEORETICAL (arithmetic on the exact bound) cross-checked against MEASURED Stage 0 ranks.
`compileWall` confirms each wall is one linear (not quadratic) functional, so walls lift to first-row/column
constraints; the n moduli are the only quadratic equalities (context pack sec 2, re-verified). Classic bound
value 9 vs measured rank 2-3 from `stage0-copt/FINDINGS.md` table 1b.
IMPACT: correctness of the spec (a citation defect). The rank theory does NOT tightly explain 2-3 as stated.
PROPOSAL: amend SPEC 4.2 to either (a) apply Pataki to the RESIDUAL SDP over the degenerate ticks (D03-5),
where the small count is real, or (b) invoke the strengthened singularity-degree bound (D03-3) with a
measured `sd(F)`. Do not cite "r(r+1)/2 <= #active walls" as the explanation of the full-lift rank.
CONFIDENCE: 0.9
DEPENDS-ON: D03-1, D03-2, D03-3

### D03-5
TITLE: The tight explanation of the measured rank 2-3 is the KKT active-set reduction (SPEC 4.2), not
Pataki on the full lift; Pataki applies cleanly to the small RESIDUAL SDP over the degenerate ticks.
LOCATION: Research + `SPEC.md` sec 4.2/4.3 + `stage0-copt/FINDINGS.md`.
CLAIM: Every non-degenerate tick (`g_t != 0`) is closed-form rank-1 by stationarity `u_t = m_t g_t/|g_t|`,
so it contributes a rank-1 block and adds no SDR rank. The rank beyond 1 lives ONLY at the degenerate
(vanishing-costate) ticks. Form the residual SDP over just those `d` complex variables with the walls that
couple them: its constraint count is `~ d moduli + (#coupling active walls)`, a handful, and Barvinok-Pataki
there gives `rank <= 2-3`. This matches Stage 0 exactly: 1 degenerate tick on j021 -> rank ~2; 4 on j008b
(t1 dominant) -> rank ~2-3; the SDP-recovered modulus slack exceeds 1e-4 at exactly the same 1 tick the
disk throttles.
EVIDENCE: MEASURED-consistent. Stage 0 FINDINGS 1a/1b: throttled ticks {j021:1, j008b:4, loopmm:1};
`eig2/eig1` {j021:0.0169, j008b:0.0239, loopmm:0.0188} i.e. numerical rank 2 with a ~2% second eigenvalue.
The residual-dimension = throttled-tick count reproduces the rank ordering. Reduction proof: SPEC 4.2 (KKT
costate), consistent with A03's j828 off-sphere-ticks-in-null-space finding.
IMPACT: correctness + it is the load-bearing structural result behind ARCH-1. The problem is rank-1 plus a
1-4 dimensional nonconvex residual; the rank theory that matters is Pataki/Sturm-Zhang on that residual.
PROPOSAL: define the residual SDP explicitly in Stage E and verify its Pataki bound gives 2-3 by direct
constraint count (closes D03-4's looseness with a measurement).
CONFIDENCE: 0.86
DEPENDS-ON: D03-4

### D03-6
TITLE: Sturm-Zhang rank-one decomposition (and its complex Huang-Zhang strengthening) is the exact,
constructive procedure to EXTRACT a rank-1 constant-modulus solution from a low-rank SDP optimum.
LOCATION: Research. Sturm & Zhang 2003; Huang & Zhang 2007.
CLAIM: Given a PSD `X` of rank `r` optimal for an SDP with a small number of prescribed matrices, Sturm-Zhang
constructively decompose `X = sum_i x_i x_i^T` such that each rank-one term preserves the inner products
`<A_j, x_i x_i^T>` for the prescribed `A_j`. In the two-constraint real case (and, crucially for us, MORE
constraints in the COMPLEX Hermitian case, Huang-Zhang) this yields a single rank-one matrix `xx^T` still
feasible and optimal, i.e. the EXACT constant-modulus solution `u`. This is the "when rank is 1, extract the
exact solution" mechanism the mission asks for, and it is a finite linear-algebra procedure (spectral
decomposition + rank-2 sweeps), not an iterative solve.
EVIDENCE: ESTABLISHED. Sturm & Zhang, "On cones of nonnegative quadratic functions", Math. of OR
28(2):246-267, 2003 (the matrix rank-one decomposition theorem). Huang & Zhang, "Complex matrix
decomposition and quadratic programming", Math. of OR 32(3):758-768, 2007: "a rank-one decomposition for a
positive semidefinite Hermitian matrix such that the inner-product between any of the rank-one matrices and
two prescribed Hermitian matrices are constant" -- the complex case preserves MORE constraints per term.
IMPACT: robustness + simplicity. Direct route to the exact answer on the rank-1 (single/easy) cases and a
principled reducer on rank 2-3. Our `u_t in C` are genuinely complex, so Huang-Zhang (the stronger complex
result) applies natively, a real advantage over a real-only reduction.
PROPOSAL: in the residual solve (ARCH-1 step 3), when the residual SDR is rank-1, use a Sturm/Huang-Zhang
eigen-decomposition to read off the exact degenerate-tick directions; benchmark vs enumeration in Stage E.
CONFIDENCE: 0.9
DEPENDS-ON: D03-5

### D03-7
TITLE: For the FEW-active-constraint regime that Stage 0 measured, the Shor relaxation is provably tight
and rank-1 recoverable; two-quadratic (S-lemma-with-equality) and complex few-constraint theorems give
a-priori certificates.
LOCATION: Research. Beck & Eldar 2006; Ai & Zhang 2009; Ai, Liang & Yuan 2024; Wang & Kilinc-Karzan 2022.
CLAIM: When only a small number of constraints are active, exact-SDR theorems apply. (i) Two quadratic
constraints (real): strong duality / rank-1 SDR holds under mild conditions (Beck-Eldar; the CDT necessary-
and-sufficient condition, Ai-Zhang). (ii) Homogeneous QCQP with up to THREE real or FOUR COMPLEX homogeneous
constraints: Ai-Liang-Yuan give a necessary-and-sufficient test for Shor tightness and, when tight, recover
a global optimum in polynomial time. (iii) General sufficient tightness via "quadratic eigenvalue
multiplicity" / symmetry (Wang-Kilinc-Karzan). Our degenerate residual has 1-4 active couplings, squarely
inside the complex-4-constraint regime, so rank-1 tightness is not just measured but has theoretical backing.
EVIDENCE: ESTABLISHED, one LATEST. Beck & Eldar, "Strong Duality in Nonconvex Quadratic Optimization with
Two Quadratic Constraints", SIAM J. Optim. 17(3):844-860, 2006. Ai & Zhang, "Strong Duality for the CDT
Subproblem: A Necessary and Sufficient Condition", SIAM J. Optim. 19(4):1735-1756, 2009. Ai, Liang & Yuan,
"On the tightness of an SDP relaxation for homogeneous QCQP with three real or four complex homogeneous
constraints", Math. Programming 2024 (arXiv 2304.04174). Wang & Kilinc-Karzan, "On the tightness of SDP
relaxations of QCQPs", Math. Programming 193:33-73, 2022.
IMPACT: robustness + correctness. Gives a checkable a-priori certificate for the rank-1 (hence exactly
extractable) case, complementing Stage 0's a-posteriori eig2/eig1 readout.
PROPOSAL: implement the Ai-Liang-Yuan complex-4-constraint tightness test on the residual as the "is the
residual exactly recoverable?" gate before falling back to enumeration/B&B. Route to Stage E.
CONFIDENCE: 0.82
DEPENDS-ON: D03-5

### D03-8
TITLE: Goemans-Williamson Gaussian-hyperplane rounding is the generic rank-reduction rounding, but for us
it is a BOUND-quality heuristic, not exact recovery; our extraction should be eigenvector/Sturm-Zhang.
LOCATION: Research. Goemans & Williamson 1995.
CLAIM: GW rounding draws a Gaussian direction and projects the SDP factors to produce a feasible integer/
sign solution with a guaranteed expected ratio (0.87856 for MAX-CUT). It is the archetype of "recover a
rank-1-flavored primal from a higher-rank SDP", and Gaussian rounding generalizes to continuous problems.
BUT its guarantee is an APPROXIMATION ratio, not exactness, and our target is byte-exact constant modulus,
so GW rounding is the wrong primitive for the final answer. It is only useful as a seed for the residual
local search. Direct leading-eigenvector rounding (as in STRIDE, D03-10) or Sturm-Zhang decomposition
(D03-6) is the exact/near-exact route on our rank-1 / low-rank residual.
EVIDENCE: ESTABLISHED. Goemans & Williamson, "Improved approximation algorithms for maximum cut and
satisfiability problems using semidefinite programming", J. ACM 42(6):1115-1145, 1995 (alpha_GW ~ 0.87856,
random-hyperplane rounding of the SDP; integrality gap equals alpha_GW).
IMPACT: scoping. Prevents a wrong turn (GW rounding will not deliver byte-exact optima).
PROPOSAL: use Gaussian/eigenvector rounding only to SEED the byte-exact local search on the degenerate
ticks, never as the reported answer.
CONFIDENCE: 0.9
DEPENDS-ON: D03-6

### D03-9
TITLE: Burer-Monteiro low-rank factorization has a deterministic global-optimality guarantee at rank
`p(p+1)/2 > m`, but its known caveat is EXACTLY our measured degenerate flat face.
LOCATION: Research. Boumal, Voroninski & Bandeira 2019/2020; Burer & Monteiro 2003/2005.
CLAIM: Factor `X = YY^T`, `Y in R^{n x p}`, turning the SDP into a smaller nonconvex program with the PSD
constraint free. If `p(p+1)/2 > rank(A)` (>= m suffices) and the constraint manifold is smooth, then for
ALMOST ALL cost matrices every second-order critical point `Y` is globally optimal and `X = YY^T` solves the
SDP (Boumal-Voroninski-Bandeira Thm 1.4). The mission-relevant caveat, in Burer-Monteiro's own words quoted
by BVB: "positive-dimensional faces of (SDP) which are 'flat' with respect to the objective function can
harbor non-global local minima." That flat face is PRECISELY our measured degeneracy (SPEC 4.3 / Stage 0
sec 2: dual `D` flat in 2.60-2.64 while recovery thrashes 2.82-5.5 b). So BM local search does NOT
automatically escape our degeneracy; it inherits it.
EVIDENCE: ESTABLISHED, LATEST. Read locally: BVB, "Deterministic guarantees for Burer-Monteiro
factorizations of smooth semidefinite programs", Comm. Pure Appl. Math. 73(3):581-608, 2020 (arXiv
1804.02008): sec 2.2 reviews `r(r+1)/2 <= m`; Thm 1.4 is the `p(p+1)/2 > rank A` global-optimality result;
the "flat face" caveat is quoted verbatim on p.2. Burer & Monteiro, "A nonlinear programming algorithm for
solving SDPs via low-rank factorization", Math. Programming 95:329-357, 2003.
IMPACT: robustness (with a warning). BM is the right dimension-reducer but is not a free lunch on the
coupled cases; it needs overparameterization or a rounding/rank-reduction escape, same as COPT's B&B.
PROPOSAL: if BM is used, overparameterize `p` beyond the Pataki rank and pair it with the rounding/
rank-reduction step; do not expect a bare rank-2 BM to escape the flat face. Route the flat-face-escape
question to Stage E.
CONFIDENCE: 0.9
DEPENDS-ON: D03-1, D03-5

### D03-10
TITLE: STRIDE (rounding + lifting + KKT certificate) is the modern engineering realization of rank-1
extraction from an SDR of a QCQP, and it is the closest published analogue to ARCH-1's residual solve.
LOCATION: Research. Yang, Liang, Toh & Carlone (STRIDE).
CLAIM: STRIDE solves rank-one SDP relaxations of polynomial/quadratic optimization by (1) ROUNDING: project
the SDP iterate to the POP via leading eigenvector(s); (2) LIFTING: run a fast local NLP on the POP from the
rounded point and lift the improved point back to a rank-one SDP matrix as a warm start; iterate under an
inexact projected-gradient backbone that preserves global convergence; (3) CERTIFY: report KKT residuals at
`(Xopt, yopt, Sopt)` to a tolerance (default 1e-6). This is exactly the "extract rank-1, locally improve,
certify" loop ARCH-1 needs, validated on geometric-perception QCQPs with up to millions of constraints.
EVIDENCE: ESTABLISHED, LATEST. STRIDE GitHub (MIT-SPARK/STRIDE) "Solver for Large-Scale Rank-One Semidefinite
Relaxations", MATLAB, PGM + user local search + SDPNAL+ init, KKT-residual certificate. Yang, Liang, Toh,
Carlone, "An inexact projected gradient method with rounding and lifting by nonlinear programming for solving
rank-one semidefinite relaxation of polynomial optimization", Math. Programming 2023.
IMPACT: robustness + simplicity. A published, benchmarked template for the residual-recovery primitive. Our
residual is tiny (1-4 vars), so the heavy SDPNAL+/PGM machinery is unnecessary; the rounding+lifting+certify
IDEA ports, the large-scale solver does not need to.
PROPOSAL: mirror STRIDE's rounding(eigenvector)+lifting(byte-exact local search)+certify(weak-duality gap)
on the residual only; this is ARCH-1 step 3 with a name and a reference implementation to check against.
CONFIDENCE: 0.85
DEPENDS-ON: D03-6, D03-9

### D03-11
TITLE: The Riemannian staircase (SE-Sync) is the portable BM pattern: local trust-region optimization over
a low-rank factor with an eigenvalue optimality certificate; it is the template for a pure-Java port IF the
full SDP is ever solved directly.
LOCATION: Research. Rosen, Carlone, Bandeira & Leonard 2019 (SE-Sync).
CLAIM: The Riemannian staircase solves the BM factorization at increasing rank `p = p0, p0+1, ...`, each a
Riemannian trust-region (RTR) local optimization; at each level a single minimum-eigenvalue test on the dual
certificate either certifies global optimality (and `X = YY^T` is the SDP optimum) or produces a descent
direction to climb one stair. It provably terminates at a rank far below `n`. The whole method is
dependency-light: RTR + a symmetric eigenvalue problem, both implementable in pure Java. It is the standard
"certifiably optimal, low-rank, portable" recipe.
EVIDENCE: ESTABLISHED. Rosen, Carlone, Bandeira, Leonard, "SE-Sync: A certifiably correct algorithm for
synchronization over the special Euclidean group", Int. J. Robotics Research 38(2-3):95-125, 2019 (Riemannian
staircase over BM factors, RTR, min-eigenvalue certificate). Riemannian-staircase origin: Boumal 2015;
Boumal-Absil-Cartis smoothed analysis (arXiv 1806.03763).
IMPACT: port feasibility (template). Shows the certificate is a single eigenvalue computation, cheap in Java.
PROPOSAL: if Stage E prototypes a direct low-rank SDP, follow the staircase pattern (RTR + eig certificate)
rather than an interior-point SDP; but prefer the residual-only route (D03-12).
CONFIDENCE: 0.85
DEPENDS-ON: D03-9

### D03-12
TITLE: Port verdict: a full `2n+1` SDP is heavy for pure-Java-within-envelope, but the RESIDUAL SDP over
1-4 degenerate ticks is trivially portable (dim <= ~9, a single small eigensolve, sub-millisecond).
LOCATION: Research + Stage 0 sizes + code (`ExactJumpModel`, `CostateDualSolver`).
CLAIM: The reduction (D03-5) means the SDP machinery need NEVER run at full size. Full lift for j021 is
`79x79` real with `m_opt ~ 45`, Pataki rank <= 9, BM factor `~ 79x11 ~ 870` vars: solvable via a Java RTR
staircase but at tens-of-ms cost and with the flat-face risk (D03-9). The RESIDUAL for j021 is 1 complex
tick coupled by a handful of walls: a `<= 9`-dimensional SDP / a `2-3`-column BM factor, whose eigensolve
and rounding are microseconds in pure Java. Identifying the degenerate set is the convex dual/active-set the
shipped `CostateDualSolver` already computes. So the analytic recommendation is: convex dual identifies
degeneracy -> tiny residual SDP/Sturm-Zhang/enumeration -> byte-exact snap, NOT a full BM/SDP solve.
EVIDENCE: THEORETICAL estimate on MEASURED sizes. Stage 0: j021 `n=39` -> lift side `79`, throttled ticks 1;
j008b throttled 4; loopmm 1. Pataki value 9 from D03-4. No pure-Java SDP timing measured by D03 ->
UNMEASURED-HYPOTHESIS for the residual-solve wall-clock (0.1-800 ms envelope, SPEC sec 1).
IMPACT: speed + simplicity + port feasibility. The expensive object is avoidable; only a tiny dense
eigenproblem needs porting, well inside the envelope by construction.
PROPOSAL: Stage E prototype: (a) enumerate/eigen-round the residual over the degenerate ticks; (b) time it
pure-Java on j021/j008b/loopmm/thousand and the dF-chain captures vs the COPT references in
`stage0-copt/FINDINGS.md`; (c) compare to a residual-only 2-3 column BM+RTR. Do NOT port a full-size SDP.
CONFIDENCE: 0.8
DEPENDS-ON: D03-5, D03-9, D03-11

### D03-13
TITLE: The causal banded friction structure is textbook sparsity; local-to-global exactness theorems say
per-clique (per-window) exactness composes into global SDP tightness, supporting receding-horizon over a
monolithic solve.
LOCATION: Research + `SPEC.md` sec 4.1 (banded lower-triangular `C(s,k)`). Kojima, Kim & Arima 2026.
CLAIM: `p_k` depends only on `u_s, s < k` through a lower-triangular banded `C(s,k)`, so the aggregate
sparsity pattern is banded/chordal. Local-to-global exactness (Kojima-Kim-Arima) shows that if the local
sub-SDPs on the maximal cliques of a chordal extension are exact, the global SDP relaxation is exact. This
is the theoretical backing for the measured ~5-jump coupling horizon: exactness certified window-by-window
(clique-by-clique) certifies the whole route, and no monolithic SDP is required for a tightness certificate.
EVIDENCE: ESTABLISHED, LATEST. Kojima, Kim & Arima, "Local-to-Global Exactness of SDP Relaxations for Sparse
QCQPs", Optimization Online, 2026 (clique-wise sub-SDPs on a chordal extension; local exactness -> global
exactness). Banded `C(s,k)` verified in code (context pack sec 2; `JumpLinearModel.coef`).
IMPACT: robustness + simplicity. Justifies A07/F9's receding-horizon as theoretically sound for the
tightness certificate, provided the seam cliques are handled (SPEC Q2 gaps).
PROPOSAL: exploit chordal/clique structure so the residual solve and its certificate are per-window; ties
to the cross-window warm-start opportunity (A07-12). Route to Stage E.
CONFIDENCE: 0.72
DEPENDS-ON: D03-5

### D03-14
TITLE: VERDICT (a): the rank theory DOES give a certificate that the residual is small, both a-posteriori
(measured) and a-priori (conditions).
LOCATION: Research synthesis.
CLAIM: A-posteriori: solve the convex SDP/SOCP, read `eig2/eig1` and the per-tick modulus slack; the
weak-duality gap `SDP_bound - byte_exact_achieved` upper-bounds the residual objective loss in blocks. Stage
0 already produced this: rank-1 tight on single/easy (`eig2/eig1 <= 9.5e-8`), rank 2-3 with residual objective
gap only ~1.6e-3 b on coupled cases. A-priori: the Ai-Liang-Yuan complex-4-constraint tightness test (D03-7)
and the strengthened singularity-degree bound (D03-3) certify rank-1 recoverability BEFORE solving on the
few-active-constraint regime we are in. So a certified-small-residual claim is available and is ESTABLISHED
theory backed by MEASURED numbers.
EVIDENCE: ESTABLISHED + MEASURED. Stage 0 FINDINGS tables 1a/1b/2. Certificate theorems D03-3, D03-7.
IMPACT: correctness. Answers the mission's part-5(a) affirmatively.
PROPOSAL: ship the weak-duality gap as the infeasibility/optimality certificate (a real number in blocks);
add the Ai-Liang-Yuan test as the a-priori rank-1 gate. Stage E.
CONFIDENCE: 0.85
DEPENDS-ON: D03-3, D03-5, D03-7

### D03-15
TITLE: VERDICT (b): a practical low-rank extraction pure-Java is feasible for the RESIDUAL, not (usefully)
for the full SDP; and it stays a CONTINUOUS-layer result, so the byte-exact snap remains mandatory.
LOCATION: Research synthesis + `stage0-copt/FINDINGS.md` sec 4.
CLAIM: Pure-Java extraction is feasible via: convex dual (existing) -> degenerate-set identification ->
residual solve by Sturm/Huang-Zhang rank-one decomposition (rank-1 case) or tiny enumeration / 2-3 column
BM+RTR (rank 2-3 case) -> eigenvector/Gaussian rounding seed -> byte-exact local search -> certify. Every
piece is small dense linear algebra, no external dependency, inside the envelope by the residual's tiny
dimension (D03-12, UNMEASURED wall-clock). CRITICAL LIMIT: all rank theory here is about the CONTINUOUS
constant-modulus relaxation. Stage 0 sec 4 measured byte-exact can OUT-reach the continuous optimum by up to
1e-2 b (half-angle norm>1), so a continuous rank-1 solution is a near-exact reference, not the final answer;
the LUT snap + `ExactJumpModel` certify (SPEC C5, FEAS_TOL=0) stays mandatory after any rank-based extraction.
EVIDENCE: ESTABLISHED (methods D03-6/9/10/11) + MEASURED limit (Stage 0 sec 4: j005 +3.4e-3, j019 +1.0e-2 b
byte-exact over-reach). Wall-clock of the pure-Java residual solve is UNMEASURED-HYPOTHESIS (Stage E).
IMPACT: speed + simplicity + robustness, scoped honestly. Answers part-5(b): yes for the residual, with the
byte-exact caveat.
PROPOSAL: adopt ARCH-1 with the residual-only SDP/rank-reduction; benchmark the residual solve pure-Java vs
COPT references and vs ILS (already within 2.8e-5 b on j021) in Stage E; keep byte-exact certify last.
CONFIDENCE: 0.83
DEPENDS-ON: D03-12, D03-14

---

## Citation ledger (all verified this session; real venues)

- Pataki, "On the Rank of Extreme Matrices in Semidefinite Programs and the Multiplicity of Optimal
  Eigenvalues", Mathematics of Operations Research 23(2):339-358, 1998.
- Barvinok, "Problems of distance geometry and convex properties of quadratic maps", Discrete &
  Computational Geometry 13:189-202, 1995; and "A remark on the rank of positive semidefinite matrices
  subject to affine constraints", DCG 25:23-31, 2001.
- Im & Wolkowicz, "A strengthened Barvinok-Pataki bound on SDP rank", 2021 (optimization-online 8346;
  Operations Research Letters). [PDF read locally]
- Sturm & Zhang, "On cones of nonnegative quadratic functions", Mathematics of Operations Research
  28(2):246-267, 2003.
- Huang & Zhang, "Complex matrix decomposition and quadratic programming", Mathematics of Operations
  Research 32(3):758-768, 2007.
- Beck & Eldar, "Strong Duality in Nonconvex Quadratic Optimization with Two Quadratic Constraints", SIAM
  Journal on Optimization 17(3):844-860, 2006.
- Ai & Zhang, "Strong Duality for the CDT Subproblem: A Necessary and Sufficient Condition", SIAM Journal on
  Optimization 19(4):1735-1756, 2009.
- Ai, Liang & Yuan, "On the tightness of an SDP relaxation for homogeneous QCQP with three real or four
  complex homogeneous constraints", Mathematical Programming, 2024 (arXiv 2304.04174).
- Wang & Kilinc-Karzan, "On the tightness of SDP relaxations of QCQPs", Mathematical Programming
  193:33-73, 2022.
- Goemans & Williamson, "Improved approximation algorithms for maximum cut and satisfiability problems
  using semidefinite programming", Journal of the ACM 42(6):1115-1145, 1995.
- Burer & Monteiro, "A nonlinear programming algorithm for solving semidefinite programs via low-rank
  factorization", Mathematical Programming 95:329-357, 2003.
- Boumal, Voroninski & Bandeira, "Deterministic guarantees for Burer-Monteiro factorizations of smooth
  semidefinite programs", Communications on Pure and Applied Mathematics 73(3):581-608, 2020 (arXiv
  1804.02008). [pages 1-4 read locally]
- Yang, Liang, Toh & Carlone (STRIDE), "An inexact projected gradient method with rounding and lifting by
  nonlinear programming for solving rank-one semidefinite relaxation of polynomial optimization",
  Mathematical Programming, 2023. GitHub: MIT-SPARK/STRIDE.
- Rosen, Carlone, Bandeira & Leonard, "SE-Sync: A certifiably correct algorithm for synchronization over
  the special Euclidean group", International Journal of Robotics Research 38(2-3):95-125, 2019.
- Kojima, Kim & Arima, "Local-to-Global Exactness of SDP Relaxations for Sparse QCQPs", Optimization Online,
  2026.
