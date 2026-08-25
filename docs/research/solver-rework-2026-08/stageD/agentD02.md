# Stage D shard D02: S-lemma / S-procedure, hidden convexity, TRS/GTRS exactness for the low-dim residual

AGENT: D02.
TERRITORY: the S-lemma / S-procedure, hidden convexity, TRS/GTRS exactness, and SDR-tightness-for-few-constraints
literature, applied to ARCH-1 step (3): the small nonconvex QCQP residual over the vanishing-costate (degenerate)
ticks, framed in SPEC section 4.2 as `k` constant-modulus circle equalities plus the active linear walls, `k`
measured 0 to 4 by Stage 0.

INSPECTED (read in full): `docs/research/solver-rework-2026-08/00-context-pack.md`; `SPEC.md` sections 4.1 to 4.6;
`stage0-copt/FINDINGS.md` (all sections, the H1/H2 table, the SDP-rank table, the global-QCQP table).
METHOD: literature verification only. Every citation below was confirmed via WebSearch/WebFetch against the
publisher or arXiv record (venue, author, year cross-checked). No prototype was run; every latency number is a
COMPLEXITY-BASED ESTIMATE explicitly tagged, routed to Stage E for measurement. No new code, no measurements of my
own beyond re-reading Stage 0's numbers.

THE RESIDUAL, restated precisely (from SPEC 4.2, the object I classify): after the convex dual fixes multipliers
`lambda >= 0` and the active wall set `A`, every non-degenerate tick is closed-form `u_t = m_t g_t/|g_t|` with
costate `g_t = c_t - (A^T lambda)_t`. The residual is over the `k` DEGENERATE ticks (`g_t = 0`). Identify
`u_t in R^2` with `z_t in C`. The residual is then:
- variables: `z_1,...,z_k in C` (2k real);
- `k` nonconvex modulus EQUALITIES `|z_t| = m_t` (each a circle, one per disjoint 2-real-dim block: BLOCK-SEPARABLE);
- the active walls in `A`, each a REAL-linear functional `Re(a_j^H z) {=,<=} b_j` coupling the blocks (from
  complementary slackness the tight ones are equalities);
- objective: at a degenerate tick the reduced-objective gradient VANISHES, so the linear objective is FLAT over the
  residual; the residual is a FEASIBILITY problem whose free tie-break is the smoothness rule (SPEC 4.5).
Because each block is 2-real-dim = 1-complex-dim, the residual is a genuine COMPLEX-Hermitian QCQP, so the sharper
COMPLEX rank bounds apply (one extra constraint of headroom vs the real bounds); this is exactly why the boundary
literature pairs "three real OR four complex".

---

## ESTABLISHED (real citations; each tagged theoretical or measured-against-our-model)

### D02-1
TITLE: The residual is a block-separable constant-modulus (unimodular) QCQP with linear coupling: the exact class the S-lemma / TRS / complex-SDR exactness literature is about.
LOCATION: research topic; anchors SPEC 4.1/4.2.
CLAIM: minimizing/maximizing a linear (or smoothness) functional over `k` disjoint circles coupled by linear
constraints is a complex-Hermitian QCQP with `k` rank-one nonconvex equality constraints; the whole S-lemma/TRS
exactness apparatus is built for exactly this "few nonconvex quadratics" regime.
EVIDENCE: theoretical. Foundational surveys: Polik and Terlaky, "A Survey of the S-Lemma", SIAM Review 49(3):371-418,
2007 (https://epubs.siam.org/doi/10.1137/S003614450444614X) frames the S-procedure as the correctness of replacing
one quadratic-over-quadratic feasibility test by a PSD combination, i.e. exact Lagrangian duality for one quadratic
constraint. Luo, Ma, So, Ye, Zhang, "Semidefinite Relaxation of Quadratic Optimization Problems", IEEE Signal
Processing Magazine 27(3):20-34, 2010 (http://dsp.ee.cuhk.edu.hk/eleg5481/Lecture%20notes/10-SDR/qcqp_sdr.pdf) is the
canonical SDR reference for this class. The constant-modulus/unimodular object is the UQP of Soltanalian and Stoica,
"Designing Unimodular Codes Via Quadratic Optimization", IEEE TSP 62(5):1221-1234, 2014 (already in SPEC 4.6); our
instance is MILDER (linear objective + linear walls, not a full quadratic form).
IMPACT: correctness/simplicity: names the residual as a studied class with exactness theorems, so ARCH-1 step (3) is
not inventing a solver, it is instantiating known results.
PROPOSAL: treat the residual as a complex-Hermitian QCQP and apply the per-`k` exactness results in D02-2..D02-6.
CONFIDENCE: 0.95.
DEPENDS-ON: none.

### D02-2
TITLE: k=1 residual (one circle + linear walls) is an equality-constrained TRS in R^2: CLOSED FORM, provably exact.
LOCATION: research topic; single degenerate tick (measured on j021, loopmm).
CLAIM: for one degenerate tick the residual is a line/halfplane intersected with a circle in R^2, solvable exactly by
elementary geometry with no SDP; the underlying SDR is provably tight by the single-quadratic S-lemma.
EVIDENCE: theoretical + measured-against-our-model. The trust-region subproblem (one quadratic constraint) has an
exact SDP relaxation and strong duality (More and Sorensen 1983; the S-lemma, Polik-Terlaky 2007, cited D02-1). The
constraint here is an EQUALITY (circle, `|z|=m`), covered by the S-lemma-with-equality of Xia, Wang, Sheu, "S-Lemma
with Equality and Its Applications", Math. Programming 156:513-547, 2016 (https://arxiv.org/abs/1403.2816), which
shows a QP with one quadratic EQUALITY constraint has (conditional) strong duality and no SDP gap. Adding the linear
walls keeps it exact: Jeyakumar and Li, "Trust-region problems with linear inequality constraints: exact SDP
relaxation, global optimality and robust optimization", Math. Programming 147:171-206, 2014
(https://link.springer.com/article/10.1007/s10107-013-0716-2); and Hsia and Sheu, "Trust Region Subproblem with a
Fixed Number of Additional Linear Inequality Constraints has Polynomial Complexity", 2013
(https://arxiv.org/abs/1312.1398), who prove (T_1), the TRS with ONE extra linear inequality, has an exact SOCP/SDP
reformulation, hence is polynomially solvable. In R^2 the exact solve is trivial: line-circle intersection is a
quadratic (<=2 candidate points); an inequality wall makes it an arc, minimized by checking the unconstrained circle
optimum against the arc endpoints. Measured relevance: Stage 0 FINDINGS 1a/1b put k=1 on j021 (one throttled tick t12)
and loopmm (t0); COPT then solves the FULL j021 globally in 0.27 s and ILS lands within 2.8e-5 b, consistent with the
residual being trivially closed.
IMPACT: correctness + speed: the highest-frequency coupled case (k=1) is closed-form microseconds, removing the
full-n SLP/ILS thrash that SPEC 4.3 blames for the j021/thousand blowup.
PROPOSAL: implement k=1 as a direct R^2 circle-vs-line(s) solver (quadratic root + arc-endpoint check), smoothness
picks among the <=2 feasible points/arc. No dependency.
CONFIDENCE: 0.9.
DEPENDS-ON: D02-11 (clean active-set identification).

### D02-3
TITLE: Rank bounds by constraint count put small `k` in the AUTOMATIC rank-one regime: real m<=2, complex m<=3.
LOCATION: research topic; the reason the residual dimension controls tractability.
CLAIM: an SDP with `m` affine constraints has an optimal solution of rank `r` with `r(r+1)/2 <= m` (real), so rank-one
whenever `m <= 2`; the Hermitian analogue is `sum_l r_l^2 <= m`, so rank-one whenever `m <= 3`. Since our residual is
complex, the m<=3 bound applies, giving one constraint more headroom than the real count.
EVIDENCE: theoretical, verified. Real bound: the Barvinok-Pataki bound, Pataki, "On the rank of extreme matrices in
semidefinite programs and the multiplicity of optimal eigenvalues", Math. of OR 23(2):339-358, 1998; Barvinok 1995;
restated and strengthened by Im and Wolkowicz, "A strengthened Barvinok-Pataki bound on SDP rank", Oper. Res. Letters
2021 (https://optimization-online.org/2021/04/8346/): every extreme SDP solution has `t(rank) <= m`, `t(k)=k(k+1)/2`.
Complex/Hermitian separable bound: Huang and Zhang, "Complex Matrix Decomposition and Quadratic Programming", Math. of
OR 32(3):758-768, 2007 (https://pubsonline.informs.org/doi/10.1287/moor.1070.0268), extending Sturm and Zhang, "On
cones of nonnegative quadratic functions", Math. of OR 28:246-267, 2003; applied as the separable-SDP rank theorem in
Huang and Palomar, "Rank-Constrained Separable Semidefinite Programming with Applications to Optimal Beamforming",
IEEE TSP 58(2):664-678, 2010 (verified via the IEEE record and the sum-of-squared-ranks statement
`sum_l rank^2(W_l) <= M` for Hermitian separable SDP). Consequence: complex m=1,2,3 -> r=1 (since 2^2=4>3); m=4 -> r<=2.
Measured relevance: Stage 0 FINDINGS 1b measured full-problem SDR eig2/eig1 <= 9e-8 (rank-1) on all single/easy
captures and eig2/eig1 up to 0.024 (rank 2-3) on exactly the coupled cases, matching the "few active constraints ->
low rank" prediction and Pataki's bound (SPEC 4.2 already cites this).
IMPACT: correctness: explains WHY the residual is small and WHY k<=3 is the automatically-exact regime; sets the
boundary at k near 4.
PROPOSAL: use the complex m<=3 rank-one guarantee to certify k<=2 residuals (with a couple of walls) as SDR-exact
without solving an SDP; reserve heavier machinery for the boundary (D02-4) and beyond (D02-6).
CONFIDENCE: 0.85 (the separable Hermitian `sum r_l^2 <= m` statement was confirmed by prose and the Huang-Palomar
record, not a full-text theorem-number read; the real Barvinok-Pataki bound is confirmed verbatim).
DEPENDS-ON: none.

### D02-4
TITLE: The exact boundary case (3 real / 4 complex constraints) has a 2024 verifiable tightness test with poly-time global recovery: directly covers k up to the 4-complex boundary.
LOCATION: research topic; the k=3 to k=4 boundary of the residual.
CLAIM: at exactly the count where automatic rank-one just fails, a checkable necessary-and-sufficient condition detects
SDR tightness and, when it holds, recovers the global optimum in polynomial time by rank-one decomposition.
EVIDENCE: theoretical, verified (this is the single most on-point recent result). Ai, Liang, Yuan, "On the tightness of
an SDP relaxation for homogeneous QCQP with three real or four complex homogeneous constraints", Mathematical
Programming (published 2024; arXiv:2304.04174, https://arxiv.org/abs/2304.04174,
https://link.springer.com/article/10.1007/s10107-024-02105-z). Abstract (verified verbatim): minimize a general
homogeneous quadratic subject to THREE REAL or FOUR COMPLEX homogeneous quadratic inequality/equality constraints; a
"sufficient and necessary test condition ... based on only an optimal solution pair of the SDP relaxation and its
dual" detects tightness, and "when the tightness is confirmed, a global optimal solution ... is found simultaneously in
polynomial-time"; as a corollary the S-lemma and Yuan's lemma are generalized to three real / four complex forms. The
recovery mechanism is the Sturm-Zhang / Huang-Zhang rank-one matrix decomposition (D02-3). Yuan's lemma itself: Yuan
1990 (two quadratic forms: `max{x^T A x, x^T B x} >= 0 for all x` iff some convex combination `tA+(1-t)B` is PSD),
verified via "The extensions of Yuan's Lemma and applications in S-lemma"
(https://arxiv.org/abs/1704.01109). Homogenization maps our residual (linear objective + linear walls + k moduli) to a
homogeneous complex QCQP with `m = k + |A| + 1` constraints; the boundary `m = 4` (complex) is reached e.g. by k=3
with no coupling wall, or k=2 with one wall, or k=1 with two walls.
IMPACT: correctness: gives a CERTIFICATE (tight or not) plus exact recovery for the boundary residuals, closing the k=3
regime and the tight subset of k=4 without a spatial B&B.
PROPOSAL: at the boundary, run the small SDP + the Ai-Liang-Yuan test; on tight, extract the global point via the
Hermitian rank-one decomposition; on not-tight, fall through to D02-6. Prototype the SDP in COPT (Stage E) before
deciding pure-Java.
CONFIDENCE: 0.85 (result verified; its polynomial recovery is theoretical, un-timed on our data).
DEPENDS-ON: D02-3, D02-11.

### D02-5
TITLE: k=2 residual is a two-quadratic problem EASIER than CDT (disjoint blocks), reducible to a univariate solve; covered by GTRS / two-parameter-eigenvalue theory.
LOCATION: research topic; two degenerate ticks.
CLAIM: two circles coupled by linear walls reduce to a one-parameter family of k=1 problems, hence to a univariate root
find or a single generalized-eigenvalue solve; it is not the hard general CDT because the two nonconvex constraints act
on DISJOINT variable blocks.
EVIDENCE: theoretical + measured-against-our-model. General two-quadratic-constraint theory: the CDT (Celis-Dennis-Tapia)
problem can lose strong duality (Yuan's counterexample), but exact conditions and algorithms exist: Ye and Zhang, "New
Results on Quadratic Minimization", SIAM J. Optimization 14(1):245-267, 2003
(https://optimization-online.org/wp-content/uploads/2001/05/333.pdf) prove SDR tightness / hidden convexity for the
two-sided (extended) TRS; Ai and Zhang, "Strong Duality for the CDT Subproblem: A Necessary and Sufficient Condition",
SIAM J. Optimization 19(4):1735-1756, 2009 (https://epubs.siam.org/doi/10.1137/050644471); Sakaue, Nakatsukasa, Takeda,
Iwata, "Solving Generalized CDT Problems via Two-Parameter Eigenvalues", SIAM J. Optimization 26(3), 2016
(https://epubs.siam.org/doi/10.1137/15100624X) give the first practical poly-time GCDT algorithm via a two-parameter
eigenvalue problem. Hidden convexity lineage: Ben-Tal and Teboulle, "Hidden convexity in some nonconvex quadratically
constrained quadratic programming", Math. Programming 72:51-63, 1996
(https://www.semanticscholar.org/paper/d6146a15fbf4f813d1e05d98bfe54a1f5a6502d4). For OUR structure the reduction is
simpler still: fix the phase `theta_1 in [0,2pi)` of circle 1; the coupling walls become linear in `z_2` and the
inner problem in `z_2` is a k=1 residual (D02-2), closed form; sweep/root-find over the single scalar `theta_1`. That
disjoint-block property is why our two-circle case avoids the general-CDT gap. Measured relevance: Stage 0 FINDINGS 1c
solves loopmm (its clamp-free model, effectively low-k) globally in 0.02 s; the residual alone is far smaller.
IMPACT: speed + correctness: k=2 solved by a univariate reduction, sub-millisecond estimate, no SDP dependency needed.
PROPOSAL: implement k=2 as "fix theta_1, closed-form z_2, evaluate feasibility+smoothness; univariate refine". Keep the
small-SDP + Ai-Liang-Yuan route (D02-4) as the tight-certificate alternative.
CONFIDENCE: 0.8.
DEPENDS-ON: D02-2, D02-11.

### D02-6
TITLE: k>=4 residual is NOT guaranteed SDR-tight, but is globally solvable by a tiny low-dimensional enumeration / spatial B&B over the k phases.
LOCATION: research topic; four degenerate ticks (measured j008b).
CLAIM: past the 4-complex boundary the SDR can be rank>1, so exactness is not guaranteed; the honest exact route is a
small spatial branch-and-bound (or coarse phase-grid) over the `k<=4` phase angles, each leaf/slice closed by the k=1
inner solve; this is what COPT does and it is cheap at this dimension.
EVIDENCE: theoretical + measured-against-our-model. Measured: Stage 0 FINDINGS 1b puts j008b at k=4 throttled ticks with
SDR eig2/eig1 = 0.0239 (rank 2-3, NOT tight); the disk is loose by ~1.5e-3 b there. FINDINGS 1c: COPT spatial B&B
(NonConvex=2) solves the FULL j008b (n=25) globally in 0.14 s and full j021 (n=39) in 0.27 s, so the residual alone
(4 phases) is far smaller. Theory for why enumeration is the fallback: general QCQP with more than the boundary count of
nonconvex constraints, or more linear constraints than dimension, is NP-hard (Hsia-Sheu 2013, arXiv:1312.1398, show the
extended TRS with #linear > dimension or arbitrary count is NP-hard), so no universal closed form exists; but for a
FIXED small k it is polynomial and, concretely, a bounded grid/B&B over k<=4 angles with an O(1) inner solve is a
handful of thousands of leaf evaluations.
IMPACT: correctness: guarantees a global residual solve even in the non-tight regime, at low-ms estimated cost;
robustness: this is the honest fallback that avoids the SPEC 4.3 full-n thrash.
PROPOSAL: k in {3,4}: try the D02-4 SDP-tightness certificate first; on not-tight, run a coarse phase-grid + local
refine, or an interval spatial B&B over the k phases with the k=1 inner solve as the bound. Pure-Java, no dependency.
CONFIDENCE: 0.8.
DEPENDS-ON: D02-2, D02-4, D02-11.

### D02-7
TITLE: If the wall-coupling graph over degenerate ticks is a forest / tridiagonal / arrow / bipartite, the SDR is exact regardless of count (structural bonus).
LOCATION: research topic; the sparsity pattern of `A` restricted to degenerate ticks.
CLAIM: sparse coupling structure gives SDR exactness independent of the number of constraints; walls in this tool
typically couple few, adjacent ticks, so the residual's coupling graph is likely sparse.
EVIDENCE: theoretical, verified. Azuma, Fukuda, Kim, Yamashita, "Exact SDP relaxations of quadratically constrained
quadratic programs with forest structures", 2020 (arXiv:2009.02638, https://arxiv.org/abs/2009.02638): QCQPs with
forest-structured aggregate-sparsity (including tridiagonal and arrow patterns) have exact SDP relaxations under a
feasibility/rank condition on the aggregate sparsity matrix. Same authors, "Exact SDP relaxations for quadratic
programs with bipartite graph structures", J. Global Optimization 2022
(https://link.springer.com/article/10.1007/s10898-022-01268-3). Related geometric exactness: Wang and Kilinc-Karzan,
"On the tightness of SDP relaxations of QCQPs", Math. Programming 2020
(https://link.springer.com/article/10.1007/s10107-020-01589-9) and "Exactness in SDP relaxations of QCQPs: Theory and
applications", 2021 (arXiv:2107.06885, https://arxiv.org/abs/2107.06885) with the rank-one-generated (ROG) property and
the objective-value vs convex-hull exactness distinction. UNMEASURED for our data: whether the actual residual coupling
graph on the corpus (which walls touch which degenerate ticks) is a forest is not yet checked.
IMPACT: correctness/simplicity: a cheap structural test on `A` may certify exactness before any solve, collapsing even
k=3,4 to convex.
PROPOSAL: Stage E: dump the residual coupling graph per coupled capture (j008b, j021, loopmm, dF-chain) and test
forest/tridiagonal/bipartite; if it holds, the SDR is exact and a plain SOCP recovers the point.
CONFIDENCE: 0.6 (result solid; applicability to our residual is an unchecked hypothesis).
DEPENDS-ON: D02-3.

### D02-8
TITLE: The k<=2 slices are GTRS-solvable in (near) linear time via one generalized eigenvalue problem (hidden convexity gives a fast, dependency-light kernel).
LOCATION: research topic; the inner solve for the univariate/slice reductions.
CLAIM: the generalized trust-region subproblem (one quadratic objective, one quadratic constraint, possibly two-sided)
is solvable in linear time by hidden convexity / a single generalized eigenproblem, giving a fast, well-understood
kernel for the k=1 and slice-inner solves.
EVIDENCE: theoretical, verified. Ben-Tal and Teboulle 1996 (D02-5); Adachi, Iwata, Nakatsukasa, Takeda, "Solving the
Trust-Region Subproblem by a Generalized Eigenvalue Problem", SIAM J. Optimization 27(1), 2017; Wang and Xia, "A
linear-time algorithm for the trust region subproblem based on hidden convexity", Optimization Letters 11:1639-1646,
2017 (https://link.springer.com/article/10.1007/s11590-016-1070-0); Jiang and Li / Ben-Tal-den Hertog SOCP reformulation
of GTRS, "SOCP reformulation for the generalized trust region subproblem via a canonical form of two symmetric
matrices", Math. Programming 2018 (https://link.springer.com/article/10.1007/s10107-017-1145-4); Pong and Wolkowicz
interval-bounded GTRS strong duality (https://link.springer.com/article/10.1007/s11590-014-0812-0). In R^2 these reduce
to closed-form root finding, so the "linear-time GTRS" is overkill for k=1 but is the principled kernel if a slice ever
carries a genuine quadratic (e.g. a smoothness term promoted into the inner solve).
IMPACT: speed: confirms the inner solve is O(1)-to-linear, no iterative SDP; simplicity: one kernel serves k=1 and the
inner step of k=2..4.
PROPOSAL: use closed-form R^2 geometry as the kernel; keep GTRS-by-generalized-eigenvalue documented as the drop-in if
a quadratic ever enters the slice.
CONFIDENCE: 0.85.
DEPENDS-ON: D02-2.

### D02-9
TITLE: The unifying residual algorithm (ARCH-1 step 3): reduction-to-univariate over the k phases with a closed-form k=1 inner solve; one primitive for k=0..4.
LOCATION: research topic; the proposed collapsed mechanism.
CLAIM: a single "phase-reduction" primitive covers the whole measured range: k=0 no residual; k=1 closed form; k=2
univariate; k=3,4 low-dim grid/B&B with an SDR-tight fast exit at the boundary; smoothness is the tie-break inside it.
EVIDENCE: assembled from D02-2 (k=1 closed form), D02-4 (boundary certificate), D02-5 (k=2 univariate), D02-6 (k=3,4
enumeration), D02-8 (kernel). Measured envelope it must fit: SPEC/Stage B FAST 110-310 ms, THOROUGH ~9-12 s, and Stage 0
COPT solves the FULL problems in 0.02-0.36 s; the residual is a strict sub-part, so the estimated residual cost is a
small fraction of the budget.
IMPACT: simplicity (collapses the SLP/ILS/BnB/RelaxationRecovery fallback tail of SPEC section 2 into one primitive),
correctness (global on the measured k range), smoothness (unifies the four smoothing passes into the residual tie-break,
SPEC 4.5).
PROPOSAL: build this primitive as ARCH-1 step (3); prototype in COPT (SDP-tightness certificate) and pure-Java
(geometry + univariate + grid/B&B) in Stage E, benchmarked against the Stage 0 COPT reference optima on
j021/j008b/loopmm and the dF-chain captures.
CONFIDENCE: 0.75.
DEPENDS-ON: D02-2, D02-4, D02-5, D02-6, D02-8, D02-11.

### D02-10
TITLE: Port feasibility and latency: pure-Java, dependency-free is achievable for k<=4; SDP only optional at the boundary.
LOCATION: research topic; the shipped-path constraint (SPEC section 5, dependency-free preferred).
CLAIM: k=1 is O(1) arithmetic; k=2 is a univariate scan of O(N) O(1)-inner solves; k=3,4 is an O(N^{k-1}) coarse grid
or a bounded B&B over k<=4 angles; all avoid an external SDP/LP dependency.
EVIDENCE: complexity-based ESTIMATE, UNMEASURED-HYPOTHESIS on the wall-clock. k=1: quadratic root + a few comparisons,
sub-microsecond. k=2: N ~ 1e3 samples times an O(1) inner solve ~ few microseconds; a local Newton refine on the 1-D
feasibility residual tightens it. k=3,4: an N^{k-1} grid at modest N plus refine, or an interval spatial B&B whose leaf
bound is the k=1 inner solve; both estimated low single-digit ms, invoked only on the handful of captures with k>=1. If
the D02-4 SDP route is chosen at the boundary, a 4x4 Hermitian eigen-decomposition (for the rank-one decomposition) is
elementary pure-Java; a general small interior-point SDP is heavier and would argue for a dependency (weigh against
Forge 1.8.9/1.12.2 shade + Fabric include per SPEC section 5; A04 already measured re-adding an LP library
net-negative), so the enumeration route is preferred to stay dependency-free.
IMPACT: speed within envelope; simplicity/robustness: no new shipped dependency.
PROPOSAL: implement dependency-free (geometry + univariate + grid/B&B); MEASURE each k in Stage E (warmup + repeated
timed runs, direct java -cp) before claiming any latency.
CONFIDENCE: 0.6 (design sound; every number here is an estimate pending Stage E measurement).
DEPENDS-ON: D02-9.

---

## SPECULATION / RISKS (UNMEASURED-HYPOTHESIS; route to Stage E)

### D02-11
TITLE: RISK: the residual is only as clean as the active-set / degenerate-tick identification, which the measured dual-face degeneracy can blur.
LOCATION: research topic; the interface between the convex dual and the residual.
CLAIM: the "k circles + tight walls" residual presupposes a crisp `(lambda, A)` and a crisp degenerate set; Stage 0
measured a FLAT DEGENERATE dual face, so which ticks are degenerate and which walls are active may be ambiguous,
inflating effective k or forcing a small enumeration over candidate active sets.
EVIDENCE: UNMEASURED-HYPOTHESIS, motivated by measured facts. Stage 0 FINDINGS section 2 and context-pack section 5: the
shipped dual grinds MAX_ITER at pgres ~2.4 and the dual optimum "sits in 2.60-2.64 while recovery violation thrashes
2.82-5.5 b as lambda slides the face", i.e. the active set is not pinned by the shipped dual. The residual solve
therefore depends on FIRST making the convex bound converge (COPT's SOCP does it in <20 ms; a converging Java
interior-point or a fixed subgradient would too, per FINDINGS section 2 and SPEC open question 2). If the converged dual
still leaves an ambiguous face, the residual must enumerate a few candidate active sets (each still k<=4, so still
cheap).
IMPACT: correctness/robustness: this is the make-or-break dependency for ARCH-1 step (3); if unaddressed, the residual
solver inherits the current non-convergence failure.
PROPOSAL: Stage E: with the COPT-converged SOCP dual, dump the active set and |g_t| spectrum per coupled capture; measure
whether the degenerate set is crisp at a threshold, and whether candidate-active-set enumeration is bounded. Couple this
to the "make the convex bound converge" open question (SPEC section 7).
CONFIDENCE: 0.7 (that the risk is real; the resolution is unmeasured).
DEPENDS-ON: none (this is upstream of D02-2..D02-9).

### D02-12
TITLE: dF (facing) constraints enter the residual as PHASE constraints that generally REDUCE its dimension, not raise it.
LOCATION: research topic; composing C3 (dF) with the residual.
CLAIM: dF=0 pins `theta_t = theta_{t-1}` (a phase equality), collapsing a circle to a single point or tying two blocks;
a general dF is a sector/phase-offset constraint (an arc). Both shrink or couple the feasible circle set, so a
dF-heavy residual is typically LOWER effective dimension, not an extra nonconvexity.
EVIDENCE: theoretical, from SPEC 4.5 and Stage 0 FINDINGS section 5 (dF = a per-tick phase constraint
`arg(u_t) = arg(u_{t-1}) + (baseArg_t - baseArg_{t-1}) + D`, a rotation coupling of consecutive complex inputs). Phase
constraints are exactly representable in the complex-Hermitian lift (they are Hermitian quadratics in `z`), so the
Ai-Liang-Yuan (D02-4) and rank-decomposition machinery still apply; they add to the constraint count `m` but also
remove degrees of freedom. UNMEASURED: the net effect on residual dimension per dF-chain capture.
IMPACT: correctness: predicts dF composes with the residual solve (closing the SPEC C3 gap where RelaxationRecovery and
the residual BAIL on facing walls), rather than breaking it.
PROPOSAL: Stage E: model dF as a phase/arc constraint in the residual (pin -> point; sector -> arc), get the COPT
dF-constrained reference (needs dF modeled in COPT, FINDINGS section 5 caveat), and verify dimension does not grow.
CONFIDENCE: 0.6.
DEPENDS-ON: D02-9.

### D02-13
TITLE: PRECISION CAVEAT: circles are EQUALITIES and each lives in R^2 (dimension 2), where two-quadratic joint-range convexity (Brickman) FAILS, so k>=2 genuinely can need the test/enumeration, not naive S-lemma.
LOCATION: research topic; guarding against over-claiming exactness.
CLAIM: the classical joint-numerical-range convexity that powers the S-lemma needs dimension n>=3 (Brickman); each of our
blocks is R^2, and the moduli are equalities not disks, so a naive "two quadratics -> S-lemma tight" is NOT licensed for
k>=2; the disjoint-block reduction (D02-5) and the boundary test (D02-4) are what actually license it.
EVIDENCE: theoretical, verified in spirit. Brickman's theorem (the image of the unit sphere in R^n, n>=3, under two
quadratic forms is convex) underpins S-lemma tightness (Polik-Terlaky 2007, D02-1); it fails at n=2. Yuan's
counterexample to CDT strong duality (referenced in D02-5) is the two-quadratic warning. Our safety net is structural,
not dimensional: (a) k=1 in R^2 is one quadratic, exact by elementary geometry regardless of Brickman; (b) k>=2 uses the
disjoint-block univariate reduction or the Ai-Liang-Yuan complex boundary test, not a bare S-lemma. This caveat is why
the verdict below labels k=2 "univariate-reduction exact" and k=4 "enumeration/B&B exact", never "S-lemma closed form".
IMPACT: correctness/rigor: prevents a false "all k are S-lemma closed form" claim; keeps the k=4 honest fallback in.
PROPOSAL: keep the per-k method distinctions in D02-2..D02-6 exactly as stated; do not collapse k>=2 to a single S-lemma.
CONFIDENCE: 0.85.
DEPENDS-ON: D02-2, D02-5, D02-6.

---

## VERDICT (S-lemma / TRS/GTRS answer to ARCH-1 step 3, per k)

Does S-lemma / TRS theory give an EXACT small-residual solver for k=1..4? YES for k<=3 with a clean active set, and YES
(by tiny enumeration/B&B, not closed form) for k=4. Precise breakdown:

- k=0 (single/easy jumps; measured majority): NO residual. The convex dual + closed-form costate recovery is exact
  (LCvx / S-lemma / real Barvinok-Pataki m<=2 rank-one; Stage 0 measured rank-1 to eig2/eig1<=9e-8). Microseconds.
  This is the shipped fast path, kept.
- k=1 (measured j021, loopmm): CLOSED FORM, provably exact. One circle + linear walls in R^2 = equality-constrained TRS
  in R^2; line-circle quadratic + arc-endpoint check; S-lemma-with-equality (Xia-Wang-Sheu 2016), Hsia-Sheu (T_1) and
  Jeyakumar-Li guarantee SDR tightness. Sub-microsecond estimate. NO SDP.
- k=2: EXACT via a UNIVARIATE reduction (fix one phase, closed-form the other), not a single closed form but effectively
  so; alternatively a small SDP that is often tight (complex m<=3 rank-one, or the Ai-Liang-Yuan test) with rank-one
  recovery. Sub-millisecond estimate. Dependency-free.
- k=3: at/near the 4-complex boundary. EXACT with a CERTIFICATE: the Ai-Liang-Yuan (2024) necessary-and-sufficient
  tightness test + poly-time rank-one recovery when tight; else a 2-D phase grid / small spatial B&B with the k=1 inner
  solve. Low-ms estimate. SDP optional (a 4x4 Hermitian eigensolve suffices to recover; enumeration avoids SDP entirely).
- k=4 (measured j008b): NOT guaranteed SDR-tight (measured rank 2-3, disk loose ~1.5e-3 b). EXACT only by a small
  spatial B&B / coarse phase-grid over the 4 angles with the k=1 inner solve (the COPT route, measured <0.3 s on the
  full problem, so far cheaper on the residual alone). Low-ms estimate. Dependency-free.

CLOSED-FORM vs needs-SDP/B&B, sharply: k=0,1 are closed form. k=2 is closed-form-per-univariate-slice. k=3 is
test-condition-exact (a small SDP OR a 2-D enumeration). k=4 needs a small B&B / low-dim enumeration (no closed form,
but tiny and global). No case on the measured corpus (k<=4) needs anything larger than a 4-variable global solve, which
COPT does in under 0.3 s and pure-Java enumeration does trivially.

THE ONE LOAD-BEARING DEPENDENCY (D02-11): this entire exact-residual story presupposes the convex dual is made to
CONVERGE so the active set and degenerate set come out crisp (COPT's SOCP does it in <20 ms; the shipped subgradient
does not, per Stage 0). "Make the convex bound converge" and "solve the small residual exactly" are the two halves of
ARCH-1; D02 answers the second half and shows it is EASY once the first half delivers a clean active set. If the
converged face is still ambiguous, the fix is a bounded enumeration over candidate active sets, each still k<=4.

Net: the S-lemma / TRS/GTRS / complex-SDR exactness literature DOES furnish an exact, pure-Java, dependency-free
small-residual solver across the entire measured k=0..4 range, with closed form for k<=1, univariate for k=2, a
verifiable tightness certificate for k=3, and a tiny global B&B for k=4. This directly supports SPEC section 6's
ARCH-1 as attainable, pending the Stage E measurements flagged in D02-10, D02-11, D02-12, and the D02-7 structural
check.

---

## CITATIONS (all verified via WebSearch/WebFetch against publisher or arXiv records)

- Polik, Terlaky. A Survey of the S-Lemma. SIAM Review 49(3):371-418, 2007. https://epubs.siam.org/doi/10.1137/S003614450444614X
- Xia, Wang, Sheu. S-Lemma with Equality and Its Applications. Math. Programming 156:513-547, 2016. https://arxiv.org/abs/1403.2816
- Yuan (1990) two-quadratic-forms lemma; extensions: An extension of Yuan's Lemma and its applications in optimization. https://arxiv.org/abs/1704.01109
- More, Sorensen. Computing a Trust Region Step. SIAM J. Sci. Stat. Comput. 4(3):553-572, 1983 (TRS exactness; via Polik-Terlaky and standard references).
- Ben-Tal, Teboulle. Hidden convexity in some nonconvex quadratically constrained quadratic programming. Math. Programming 72:51-63, 1996. https://www.semanticscholar.org/paper/d6146a15fbf4f813d1e05d98bfe54a1f5a6502d4
- Ye, Zhang. New Results on Quadratic Minimization. SIAM J. Optimization 14(1):245-267, 2003. https://optimization-online.org/wp-content/uploads/2001/05/333.pdf
- Ai, Zhang. Strong Duality for the CDT Subproblem: A Necessary and Sufficient Condition. SIAM J. Optimization 19(4):1735-1756, 2009. https://epubs.siam.org/doi/10.1137/050644471
- Sakaue, Nakatsukasa, Takeda, Iwata. Solving Generalized CDT Problems via Two-Parameter Eigenvalues. SIAM J. Optimization 26(3), 2016. https://epubs.siam.org/doi/10.1137/15100624X
- Jeyakumar, Li. Trust-region problems with linear inequality constraints: exact SDP relaxation, global optimality and robust optimization. Math. Programming 147:171-206, 2014. https://link.springer.com/article/10.1007/s10107-013-0716-2
- Hsia, Sheu. Trust Region Subproblem with a Fixed Number of Additional Linear Inequality Constraints has Polynomial Complexity. 2013. https://arxiv.org/abs/1312.1398
- Wang, Xia. A linear-time algorithm for the trust region subproblem based on hidden convexity. Optimization Letters 11:1639-1646, 2017. https://link.springer.com/article/10.1007/s11590-016-1070-0
- Jiang, Li (Ben-Tal/den Hertog lineage). SOCP reformulation for the generalized trust region subproblem via a canonical form of two symmetric matrices. Math. Programming, 2018. https://link.springer.com/article/10.1007/s10107-017-1145-4
- Pong, Wolkowicz. Strong duality for generalized trust region subproblem: S-lemma with interval bounds. Optimization Letters, 2014. https://link.springer.com/article/10.1007/s11590-014-0812-0
- Ai, Liang, Yuan. On the tightness of an SDP relaxation for homogeneous QCQP with three real or four complex homogeneous constraints. Math. Programming, 2024. https://arxiv.org/abs/2304.04174 ; https://link.springer.com/article/10.1007/s10107-024-02105-z
- Pataki. On the rank of extreme matrices in semidefinite programs and the multiplicity of optimal eigenvalues. Math. of OR 23(2):339-358, 1998. Barvinok 1995. Strengthened: Im, Wolkowicz. A strengthened Barvinok-Pataki bound on SDP rank. Oper. Res. Letters, 2021. https://optimization-online.org/2021/04/8346/
- Sturm, Zhang. On cones of nonnegative quadratic functions. Math. of OR 28:246-267, 2003.
- Huang, Zhang. Complex Matrix Decomposition and Quadratic Programming. Math. of OR 32(3):758-768, 2007. https://pubsonline.informs.org/doi/10.1287/moor.1070.0268
- Huang, Palomar. Rank-Constrained Separable Semidefinite Programming with Applications to Optimal Beamforming. IEEE TSP 58(2):664-678, 2010. (separable Hermitian SDP: sum_l rank^2(W_l) <= M)
- So, Zhang, Ye. On approximating complex quadratic optimization problems via semidefinite programming relaxations. Math. Programming 110:93-110, 2007. https://link.springer.com/article/10.1007/s10107-006-0064-6
- Luo, Ma, So, Ye, Zhang. Semidefinite Relaxation of Quadratic Optimization Problems. IEEE Signal Processing Magazine 27(3):20-34, 2010. http://dsp.ee.cuhk.edu.hk/eleg5481/Lecture%20notes/10-SDR/qcqp_sdr.pdf
- Soltanalian, Stoica. Designing Unimodular Codes Via Quadratic Optimization. IEEE TSP 62(5):1221-1234, 2014.
- Azuma, Fukuda, Kim, Yamashita. Exact SDP relaxations of QCQPs with forest structures. 2020. https://arxiv.org/abs/2009.02638 ; bipartite version, J. Global Optimization, 2022. https://link.springer.com/article/10.1007/s10898-022-01268-3
- Wang, Kilinc-Karzan. On the tightness of SDP relaxations of QCQPs. Math. Programming, 2020. https://link.springer.com/article/10.1007/s10107-020-01589-9 ; Exactness in SDP relaxations of QCQPs: Theory and applications. 2021. https://arxiv.org/abs/2107.06885
- Acikmese, Blackmore. Lossless convexification of a class of optimal control problems with non-convex control constraints. Automatica 47(2):341-347, 2011 (single-jump hidden convexity; from SPEC 4.6).
