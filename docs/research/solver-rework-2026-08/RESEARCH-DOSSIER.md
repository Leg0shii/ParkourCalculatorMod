# RESEARCH DOSSIER: the ARCH-1 method chain (Stage D reduce + audit)

Stage D canonical output. This is the 2-level reduce of the fourteen Stage D shards (agentD01..agentD14)
plus an audit against OUR model (SPEC section 4) and Stage 0 (COPT) measurements, ranked into one
recommended method chain for ARCH-1. It is written for the Stage E prototyper: every stage of the chain
names a specific technique, its real citation, its per-k behavior, and what remains unmeasured.

Rules: no em dashes; real citations only (never invented); every quantitative number carries its measured
source; latency estimates are labeled and moved to the Stage E appendix. COPT is a research oracle, never
shipped. Cross-references: `SPEC.md` (section 4 math class, section 6 ARCH-1/2/3), `stage0-copt/FINDINGS.md`
(H1/H2, bound tightness, global-solvability, all COPT-measured), `stageE/poc-residual-validation.md` and
`stageE/byte-exact-roundtrip.md` (the two orchestrator Stage E precursors), and the shards in `stageD/`.

AUDIT NOTE (citations): four of the most load-bearing citations were re-verified this session against the
publisher/arXiv record (author, venue, year, and the claimed result): Ai-Liang-Yuan (Math. Programming
vol. 211, 2025; arXiv 2304.04174; the 3-real/4-complex tightness test with polynomial global recovery),
Luo-Echigo-Acikmese (arXiv 2410.09748, accepted Automatica; discrete-time LCvx, normal + long-horizon
cases), Huang-Zhang (Math. of OR 32(3):758-768, 2007; the complex-Hermitian rank-one decomposition), and
Chen-Atamturk-Oren (Math. Programming 2017; arXiv 1705.09057; spatial branch-and-cut for nonconvex QCQP
with bounded complex variables via 2x2 rank-one Hermitian PSD convex-hull cuts). All four are real and
their content matches the shard claims. The shards were disciplined about verifying their own citations;
the spot-check found no fabrication.

---

## 1. EXECUTIVE SUMMARY: the recommended ARCH-1 method chain

ARCH-1 is "converge the convex bound, reduce to the low-dimensional residual, solve that residual exactly,
snap to byte-exact, tie-break with smoothing." The reduction is proved from KKT (SPEC 4.2) and MEASURED by
COPT: every non-degenerate tick (costate `g_t != 0`) is closed-form `u_t = m_t g_t/|g_t|`; only the
DEGENERATE (vanishing-costate) ticks carry residual freedom, MEASURED at 0-1 on single/redirect/neo jumps,
4 on j008b, and 10-22 on the momentum/nix class (Stage 0; poc-residual-validation degenerate-count sweep).
The orchestrator's Stage E precursor already VALIDATED the central thesis: a branch over the degenerate
tick(s) with a convex re-solve of the rest reaches the COPT global optimum within 1e-5 to 3e-5 b on
j021/j008b/loopmm (each with only ONE degenerate tick). The recommended technique for each stage:

STAGE 1, the convex kernel / bound. A from-scratch pure-Java primal-dual interior-point SOCP on the disk
relaxation `|u_t| <= m_t` (2-D cones, Nesterov-Todd scaling, Mehrotra predictor-corrector, Schur-reduced to
the tiny wall-count space), returning the tight bound AND the disk primal (Alizadeh-Goldfarb 2003;
Nesterov-Todd 1997/1998; Domahidi-Chu-Boyd ECOS 2013; D12-7). This is the correct convex relaxation because
our class IS lossless-convexification structure (Acikmese-Blackmore 2011; annular limit Kunhippurayil-Harris-
Jansson 2021; D01). PER-K: for k=0 (single/easy, 0 degenerate ticks, MEASURED SDR rank-1 to eig2/eig1 <= 9e-8)
the disk equals the sphere and the closed-form costate recovery is the exact global optimum in microseconds,
which is the shipped fast path and is KEPT (the incumbent CostateDualSolver is already tight to ~1e-6 there,
Stage 0 section 2). The IPM is a RESCUE kernel for the coupled and large-n minority, where it must converge
at n up to 353, which the shipped AL-FISTA does not (viol 15.5, A03-14). LARGE-K momentum handling: the
kernel converges regardless of the many degenerate ticks because IPM iteration count is conditioning-
independent; the many degenerate ticks are handled downstream by the residual solver, not the kernel.

STAGE 2, the residual solve (the headline collapse). The converged dual identifies `T_d = {t : |g_t| <= tau}`;
the residual over those circles is, by KKT, a PURE min-slack FEASIBILITY problem (objective and feasibility
are aligned on the degenerate ticks: `sum_{t in T_d} c_t . z_t = sum_j lambda*_j (active-wall slack)`, D14-1),
with smoothness as the free tie-break. Dispatch by `k = |T_d|`:
- k=0: no residual; closed-form exact (LCvx; D01-1, D04-5). Microseconds.
- k=1 (j021 t12, loopmm t0, the common coupled case): CLOSED FORM by arc-intersection plus sinusoid minimax
  on the single circle; provably exact by S-lemma-with-equality (Xia-Wang-Sheu 2016) and the one-linear-cut
  TRS (Hsia-Sheu 2013; Jeyakumar-Li 2014). Sub-microsecond, no SDP (D14-2, D02-2). The poc measured the
  branch-residual reaches COPT within 2.7e-5 b on j021.
- k=2: null-space reduction to a 1-D root solve when the active set is determining (D14-4), else the
  univariate reduction "fix theta_1, closed-form z_2, refine" (D02-5). Sub-millisecond, dependency-free.
- k=3-4 (j008b, 4 ticks): at/near the complex-4-constraint boundary. Run the Ai-Liang-Yuan (2024/2025)
  verifiable tightness test on the tiny residual SDP; when tight, extract the exact global point by the
  complex-Hermitian rank-one decomposition (Huang-Zhang 2007, strengthening Sturm-Zhang 2003); when not
  tight, a coarse phase-grid / tiny spatial B&B over the k angles with the k=1 inner solve, or an active-set
  enumeration (D02-4/6, D03-6/7, D14-3, D06-7). Low single-digit ms, dependency-free (the enumeration route
  needs no SDP; even the SDP route is a 4x4 Hermitian eigensolve).
- large k (momentum/nix, k=10-22 on j716/j828/j1150): NOT a free high-dimensional torus but a coordinated
  low-DOF momentum phase (axis-locked; poc-residual-validation), which COPT solves globally in < 0.5 s. This
  regime needs a SMART residual solver, not a brute k-D grid: a small spatial branch-and-cut on the phases
  (the exact-setting prior art is Chen-Atamturk-Oren 2017 complex-variable B&C; D06-4) or a Riemannian
  trust-region on the product of circles (D05, D07-2). Both are COPT-verified to handle both regimes.
The residual solve is GLOBAL on the measured k-range and is the fix for SPEC 4.3's measured recovery failure
(the shipped code DEFAULTS the degenerate tick to the objective axis, producing 0.34 b (j021) / 2.89 b
(thousand) infeasible, then thrashes a full-n SLP/ILS).

STAGE 3, the byte-exact snap (discrete layer). The continuous optimum is a BOUND and a STRUCTURE GUIDE, not
the byte-exact answer (byte-exact-roundtrip, measured). The snap is an objective-aware Schnorr-Euchner
sphere-decoding enumeration over the 0-4 coupled ticks (plus decoupled nearest-bucket rounding on the
straightaways), scored by REAL ExactJumpModel reach, so it captures the float32 half-angle norm>1 gain
(Schnorr-Euchner 1994; Agrell-Eriksson-Vardy-Zeger 2002; Hassibi-Vikalo 2005 polynomial-expected-complexity;
D11-3/4, D07-5). It replaces the dead LatticeRepair and the implicit dither. It MUST be objective-aware
because a distance-minimizing snap of an arbitrary degenerate direction byte-realizes WORSE than the shipped
result (j008b: continuous -0.197 snaps to -0.219 vs shipped -0.215; byte-exact-roundtrip). ARCH-1 therefore
ENDS with this objective-aware byte-exact search plus the existing BucketAscent/ILS finisher, then certifies
through ExactJumpModel (FEAS_TOL=0, mandatory). On pure half-angle single jumps (j005) the byte-exact optimum
sits at DIFFERENT yaws than the continuous optimum, so the shipped fast-path + ILS byte-exact search is the
right tool there and is kept (D06-9 "keep the good improve").

STAGE 4, smoothing (the residual tie-break). ONE give-back-constrained mechanism: minimize the order-1
trend-filter reversal surrogate `||D2 theta||_1` (Kim-Koh-Boyd-Gorinevsky 2009; Tibshirani 2014) with the L0
reversal count as accept-gate (Johnson 2013 / PELT Killick-Fearnhead-Eckley 2012), under ONE epsilon-constraint
`obj >= best - X` (Haimes-Lasdon-Wismer 1971), seeded by an exact O(n) taut-string solve (Condat 2013 /
Davies-Kovac 2001) and repaired by the EXISTING Gauss-Newton restore (D13). On the ARCH-1 reduction path this
specializes to an enumerated L0 tie-break over the 0-4 degenerate ticks, since the non-degenerate ticks are
costate-fixed and already smooth (D13-7). It replaces the four shipped passes (turnCost, DeWiggle,
SmoothingPolish, SmoothFaceRecovery-metric), fixing F3 (four metrics to one) and F6 (stacked give-back
1.63e-2 b to one budget X). The joint-Riemannian-objective-plus-jerk framing is REJECTED (D13-9): it buys no
tightness and forces the wrong (L2, reversal-blind) or nonsmooth metric.

Free-start and dF fold in without new machinery: free-start `p0` enters as two box-bounded linear variables,
separable by rigid translation (proven, F4), carried in the same convex kernel or a final translation pass;
dF=0 pins a phase (removing a tick from `T_d` or adding one phase equality), and a general dF is a sector/arc
constraint that REDUCES residual dimension rather than raising it (D02-12), which closes the SPEC C3 gap where
RelaxationRecovery and the residual currently BAIL on facing walls (F8).

---

## 2. RANKED CANDIDATE SHORTLIST

Verdict key: ESTABLISHED = closed-form/proved, near-certain to work as stated; PROTOTYPE-NEEDED = mechanism
and applicability are sound and measured-consistent, pure-Java robustness/latency is the open Stage E
measurement; REJECTED = ruled out with a measured or theoretical reason (kept as diagnostic or oracle where
noted). "global/bound/local" is what the technique delivers on the COUPLED multi-jump class.

| # | Candidate (cluster) | SPEC cap | Port feasibility | global / bound / local | Discrete-layer handling | COPT benchmark | Verdict |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Pure-Java primal-dual IPM SOCP disk kernel (D12-7) | C1,C2,C6 bound step | pure-Java, from scratch, ~few hundred lines, reuses in-repo Cholesky, no dep | BOUND (+ disk primal; conditioning-robust to n=353) | n/a (continuous); feeds the snap | match COPT SOCP <20ms: j021 disk 1067.86548, j008b -0.195409; converge at n=353 where AL-FISTA gives viol 15.5 | PROTOTYPE-NEEDED (primary rescue kernel) |
| 2 | LCvx theory backbone (D01) | C1 exactness cert | none (theory, no code) | proves k=0 global closed-form; horizon-independent residual bound n_x-1 (=3), 2n_x-2 (=6) with gate/dF | names the exactness class | corroborated by rank-1 SDR on j005/j016/j019/j022/f2f | ESTABLISHED (cite, do not port) |
| 3 | Arc-intersection + sinusoid minimax, k=1 residual (D14-2, D02-2) | C1,C6 | pure-Java closed form, sub-us, no dep | GLOBAL, provably exact (S-lemma-with-equality) | hands to objective-aware snap | reproduce COPT j021 1067.863880, loopmm -279.299065; poc within 2.7e-5 b | ESTABLISHED (closed form) / prototype to confirm byte-exact |
| 4 | Null-space + complex SDR/rank-one decomp + tiny B&B, k=2-4 (D14-3/4, D03-6, D02-4/5/6) | C1,C6 | pure-Java enumeration route dep-free; SDP route optional (4x4 Hermitian eigensolve) | GLOBAL (Ai-Liang-Yuan tightness cert k<=3; tiny B&B k=4) | objective-aware snap | close j008b 1.8e-2 b headroom to COPT -0.196938; poc within 1.1e-5 b | PROTOTYPE-NEEDED |
| 5 | Small spatial B&B on residual phases / Chen-Atamturk-Oren complex B&C (D06-7, D06-4) | C1,C6 (large-k, momentum) | hand-rolled pure-Java, no dep (no redistributable global QCQP solver) | GLOBAL | objective-aware snap | COPT full-problem <0.3s (j1150 22-degen 0.46s, j716 0.19s); residual strictly smaller | PROTOTYPE-NEEDED (momentum/large-k route; node counts unmeasured) |
| 6 | Riemannian trust-region on product-of-circles (D05, D07-2/4) | C1,C6 + carries C4 in-objective | pure-Java, retraction=angle-add, gradient = the i*u the code already forms, ~150-250 lines, no dep | LOCAL (2nd-order stationary; needs costate seed + multistart for global) | hands to snap | vs COPT + ILS on j021/j008b/loopmm; RTR negative-curvature step targets the degenerate saddle | PROTOTYPE-NEEDED (residual inner-engine, ARCH-3 guard, momentum solver; STANDOUT: only family carrying smoothing inside the optimizer) |
| 7 | Schnorr-Euchner sphere-decode LUT snap (D11-3/4, D07-5/6/7) | C5 | pure-Java ~100-200 lines, reuses FacingLattice + ExactJumpModel.stepRange, no dep | certified minimal-perturbation snap (not a byte-exact optimality cert) | PRIMARY discrete layer; norm>1-aware, objective-scored | leaf count + byte-exact residual vs COPT integer optimum; recover half-angle gain (j005 +3.4e-3, j019 +1.0e-2 b) | PROTOTYPE-NEEDED |
| 8 | Gate as indicator MISOCP / small-binary B&B, hybrid (D11-5/6/7) | C1,C6 gate layer | refactor of BoundPrunedRecovery, pure-Java, no dep (MISDP solvers research-only) | GLOBAL over gated set + real infeasibility certificate (fixes F10) | branch on gate-critical binaries (Balas disjunction) | does one small MIP land loopmm/dsf-neo at byte-exact obj AND certify infeasibility, within envelope | PROTOTYPE-NEEDED |
| 9 | Give-back-constrained order-1 trend filter + L0 tie-break (D13) | C4 | pure-Java taut-string O(n) + existing restore, negative LOC, no dep | exact 1D convex seed; L0 accept-gate; nonconvex constrained repair (local) | byte-exact repair via existing restore | A/B reversal sums vs 4-pass stack on hpk; Johnson/PELT exact-L0 reference | PROTOTYPE-NEEDED |
| 10 | u-space consensus-ADMM / penalty-CCP / FPP-SCA (D09) | C1,C6 local fallback | pure-Java, no LP kernel, no trust region, no per-step forward-sim, no dep | LOCAL / KKT-only | final snap | vs SLP + ILS: iterations, latency, null-rate; FPP-SCA slacks remove SlpSolve null-return | PROTOTYPE-NEEDED as ARCH-2/3 fallback (NOT the global engine, wrong altitude for ARCH-1) |
| 11 | UQP power-method / MM (D04) | (local polish only) | trivial pure-Java, no dep | LOCAL; STALLS at degenerate ticks (arg of vanishing costate undefined) | n/a | reproduces incumbent failure, no improvement | REJECTED as primary/global; optional local polish only |
| 12 | Full Shor SDP / moment-SOS / CS-TSSOS (D06-6, D10) | (oracle) | no pure-Java SDP solver; B-M fails above Barvinok-Pataki (Waldspurger-Waters) | oracle / certifier | n/a | full SDP bound == disk bound on coupled (buys nothing but rank readout); CS-TSSOS full-problem redundant vs COPT B&B | REJECTED for shipping; Stage-E oracle/certifier only |
| 13 | Phase-retrieval (GS / HIO-DR / Wirtinger flow) (D08) | (diagnostic) | trivially pure-Java but pointless | LOCAL; global guarantees need random m~n log n / n^2 oversampling, do NOT transfer | n/a | names the RelaxationRecovery stall as non-transversal AP | REJECTED as method; keep as diagnostic vocabulary |
| 14 | SMT-FP (Bitwuzla / OptiMathSAT) (D11-8) | (verifier) | out-of-tree only, never shipped | verifier only; band-local UNSAT; not a searcher | certifies a given window | 3 free ticks -> unknown at 300 s | REJECTED as searcher; optional out-of-tree certifier |

---

## 3. PER-CLUSTER DETAIL (with the real citations)

### 3a. The convex bound / kernel

The bound step must produce the tight disk-SOCP value AND the disk primal (including throttled `|u_t| < m_t` at
degenerate ticks) reliably up to n=353. The measured incumbent failures: CostateDualSolver leaves the bound
LOOSE by non-convergence (j021 dualBound 1067.889761 vs COPT-converged SOCP 1067.865480, 0.024 b, Stage 0
section 2), and RelaxationRecovery's AL-FISTA is a first-order augmented-Lagrangian penalty method whose inner
Lipschitz constant is `rho * lambda_max(A^T A)`, so it degrades exactly where conditioning is worst (n=353, viol
15.5, A03-14). This is inherent to the first-order-penalty choice, not a tuning bug (D12-3).

- RECOMMENDED (D12-7): a from-scratch pure-Java primal-dual IPM. All cones are 2-D disks (Nesterov-Todd scaling
  is a trivial 2x2 per tick), Schur-eliminating to a dense `m x m` system in the tiny wall count (m <= ~30 even
  at n=353, D12-1), so latency is bounded by m, not n, at any size the tool hits (D12-8). Iteration count is
  conditioning-independent (~10-30), which is the whole reason to prefer IPM over the incumbent first-order
  method. Citations: Alizadeh & Goldfarb, "Second-order cone programming," Math. Programming 95:3-51, 2003
  (the O(sqrt(numCones) log 1/eps) NT-direction complexity); Nesterov & Todd, Math. of OR 22(1):1-42, 1997 and
  SIAM J. Optim. 8(2):324-364, 1998 (NT scaling); Domahidi, Chu, Boyd, "ECOS," ECC 2013 (the embedded-C
  realization to mirror).
- FALLBACK (D12-9): a proper operator-splitting SOCP (factor the KKT system once, project onto the product of
  2-D disks and the orthant each iteration, homogeneous self-dual embedding for infeasibility certificates, Ruiz
  preconditioning), strictly better-founded than AL-FISTA. Citations: Stellato et al., "OSQP," Math. Prog. Comp.
  12(4):637-672, 2020; O'Donoghue et al., "SCS," JOTA 169:1042-1068, 2016.
- BOUND-ONLY cheap option (D12-4): a Nesterov-smoothed accelerated dual (Nesterov, "Smooth minimization of
  non-smooth functions," Math. Prog. 103:127-152, 2005) closes the 0.024 b bound gap but does NOT produce the
  disk primal at the degenerate ticks, so it is not a complete kernel for ARCH-1.
- THEORY (D01, LCvx): Acikmese & Blackmore, Automatica 47(2):341-347, 2011 (single-jump exactness when the
  costate does not vanish); Kunhippurayil-Harris-Jansson, Automatica 133:109848, 2021 (our `|u_t|=m_t` is the
  degenerate-annulus limit); Harris & Acikmese, Automatica 50(9):2304-2311, 2014 (losslessness needs state
  constraints active only at ISOLATED instants; our opposing corridors are interval-active, the exact boundary
  case that loses tightness, which is WHY the disk throttles on coupled cases). LCvx contributes THEORY, not
  code: the disk-SOCP relaxation already exists; LCvx bounds the residual, it does not solve it (D01-9).

### 3b. The residual solve (ARCH-1 step 3, the highest-value collapse)

The residual is `2k` real unknowns (k = 1-4 complex circles on the redirect/neo class, up to 22 on momentum),
k nonconvex modulus equalities, and a few active walls; by KKT it is a min-slack feasibility problem where
`s* = 0` means the recovery is exact (H2 was only degeneracy) and `s* > 0` is the genuine circle-vs-disk gap
(H1) at those ticks, MEASURED at ~1.6e-3 b on j021/j008b (D14-1). Per-k methods, with the exactness citation:

- k=1 (D14-2, D02-2): CLOSED FORM. On the circle every single-axis wall is one sinusoid `m|coef|cos(theta-phi)`,
  feasibility is an arc intersection, min-slack is the min of an upper envelope of sinusoids at a breakpoint.
  Exactness: Xia, Wang, Sheu, "S-Lemma with Equality and Its Applications," Math. Programming 156:513-547, 2016;
  Hsia & Sheu, arXiv:1312.1398, 2013 (TRS with one linear cut has an exact SOCP reformulation); Jeyakumar & Li,
  Math. Programming 147:171-206, 2014.
- k=2 (D14-4, D02-5): null-space reduction to 1-D (parametrize `w = w_p + N y`, substitute the moduli, small
  polynomial root) or univariate reduction (fix theta_1, closed-form the other circle). Hidden-convexity /
  two-quadratic lineage: Ben-Tal & Teboulle, Math. Programming 72:51-63, 1996; Ye & Zhang, SIAM J. Optim.
  14(1):245-267, 2003; Ai & Zhang, SIAM J. Optim. 19(4):1735-1756, 2009; Sakaue-Nakatsukasa-Takeda-Iwata,
  SIAM J. Optim. 26(3), 2016 (GCDT via two-parameter eigenvalues).
- k=3-4 (D14-3, D03-6/7, D02-4/6): Ai, Liang, Yuan, "On the tightness of an SDP relaxation for homogeneous QCQP
  with three real or four complex homogeneous constraints," Math. Programming vol. 211, 2025 (arXiv 2304.04174):
  a verifiable necessary-and-sufficient tightness test plus polynomial global recovery when tight; extract the
  point by the complex-Hermitian rank-one decomposition, Huang & Zhang, Math. of OR 32(3):758-768, 2007
  (strengthening Sturm & Zhang, Math. of OR 28(2):246-267, 2003). The complex structure gives one extra
  constraint of headroom over the real bound (rank-1 when the complex constraint count m <= 3), which is why
  k<=3 is automatically tight and k=4 is the tested boundary. When not tight, a coarse phase-grid / tiny spatial
  B&B over the k angles closes it globally (the industry sBB mechanism, restricted to the residual; Luo-Ma-So-
  Ye-Zhang SDR survey, IEEE SPM 27(3):20-34, 2010; Park & Boyd suggest-and-improve, arXiv:1703.07870, 2017).
- large k / momentum (D05, D06-4): a smart residual solver, either a small spatial branch-and-cut on the phases
  (Chen, Atamturk, Oren, Math. Programming 2017, arXiv:1705.09057; node relaxation = SDP + 2x2 rank-one
  Hermitian convex-hull cuts; branch on `arg(u_t)`) or a Riemannian trust-region on the product of circles
  (section 3c). COPT solves these full problems globally in < 0.5 s, so the residual-only solve is tractable.
- RANK-BOUND JUSTIFICATION, corrected (D03-4/5, resolved in section 5): the residual dimension is small because
  Pataki/Barvinok applies to the RESIDUAL SDP over the degenerate ticks (a handful of constraints, rank 2-3),
  NOT to the full 2n+1 lift (which counts all n moduli and gives a loose rank<=9). Pataki, Math. of OR
  23(2):339-358, 1998; Barvinok, DCG 13:189-202, 1995; strengthened Im & Wolkowicz, Oper. Res. Letters, 2021.
- PORT: pure-Java, dependency-free across the measured k range (D02-10, D03-12, D14-6). k=1 is O(1) arithmetic;
  k=2 a univariate scan or 1-D root solve; k=3-4 a 4x4 Hermitian eigensolve or a bounded enumeration; the SDP is
  never assembled at full size. No redistributable pure-Java global QCQP solver exists (SCIP is native + 3-loader
  packaging; COPT/Gurobi/BARON commercial; ALGLIB GPL/local), so the residual B&B is hand-rolled (D06-8).

### 3c. Local / polish engines (all LOCAL, subordinate to the residual)

- Riemannian trust-region on the product-of-circles (D05, D07-2). This is the native geometry (the scaled
  oblique / complex-circle manifold; Absil-Mahony-Sepulchre 2008; Boumal 2023; Absil-Baker-Gallivan RTR, FoCM
  7(3):303-330, 2007; the constant-modulus beamforming analogue Yu-Shen-Zhang-Letaief MO-AltMin, IEEE JSTSP
  10(3):485-500, 2016). PORT is trivial: the retraction is angle addition, the Riemannian gradient is the `i*u`
  tangential projection the code already forms (JumpLinearModel:166, SlpSolve, SmoothFaceRecovery.tangentProjector).
  It is LOCAL (second-order stationary; needs the costate seed and possibly a small multistart), but its
  second-order Hessian is the mechanism that can navigate the vanishing-costate saddle the shipped closed-form
  default botches (D05-7), and it is the ONLY family that can carry the smoothness term inside the optimizer
  jointly with reach and walls (D05-10). Recommended role: the residual inner-engine, the ARCH-3 guard, and the
  momentum-class solver. Walls enter by a Riemannian augmented-Lagrangian (Liu & Boumal, arXiv:1901.10000, 2019).
- Projected gradient descent for unit-modulus least squares (D07-4): a lighter alternative to RCG with proved
  local linear convergence (IEEE TSP 71:3947-3961, 2023). A minimal pure-Java fallback if RCG's line search is
  finicky.
- u-space ADMM / consensus-ADMM / penalty-CCP / FPP-SCA (D09): the shipped SlpSolve is already an SCA linearized
  in theta whose 87.5% LP reject is sin/cos Taylor error managed by a trust region (D09-1/6). Working in u-space
  (each circle has an exact closed-form radial projection) removes the trust region, the LP kernel, and the
  per-step byte-exact forward-sim. Huang & Sidiropoulos consensus-ADMM (IEEE TSP 64(20):5297-5310, 2016);
  Wang-Yin-Zeng nonconvex-ADMM to a stationary point over compact manifolds (JSC 78:29-63, 2019); Mehanna et al.
  FPP-SCA slacks that structurally remove SlpSolve's null-return (IEEE SPL 22(7):804-808, 2015); Lipp & Boyd
  penalty-CCP (Opt. Eng. 17(2):263-287, 2016). VERDICT (D09-8/9): CLEANER in structure and fixes two measured
  warts, but LOCAL / KKT-only and the wrong ALTITUDE for ARCH-1 (it re-solves the whole n-torus to recover what
  closed form already gives on n-k ticks). Role: the ARCH-2/ARCH-3 local fallback replacing SLP, not the global
  engine.
- UQP power-method / MM (D04): REJECTED as primary. Our objective is the degenerate R=0 UQP; the no-wall case is
  already the one-step closed-form align the code ships. With walls it degrades to a penalized projected-gradient
  step that is LOCAL and STALLS at the degenerate ticks (arg of a vanishing costate is undefined), reproducing
  the incumbent failure (Soltanalian & Stoica, IEEE TSP 62(5):1221-1234, 2014; D04-7/8).
- Phase-retrieval (D08): DIAGNOSTIC and CAUTIONARY, not a method. It names the RelaxationRecovery dither as
  nonconvex alternating projection (Gerchberg-Saxton; HIO = nonconvex Douglas-Rachford, Bauschke-Combettes-Luke
  2002/2003) that converges locally-linearly under transversality and STALLS at non-transversal (tangential)
  intersections (Lewis-Luke-Malick, FoCM 9:485-513, 2009; Drusvyatskiy-Ioffe-Lewis, FoCM 15, 2015), with
  stagnation points vanishing only at random m~n^2 oversampling (Waldspurger, IEEE IT 64(5):3301-3312, 2018).
  Our degenerate ticks ARE the non-transversal case and our few-wall model is the opposite of oversampled, so
  the coupled stall is PREDICTED, not a tuning failure. The Wirtinger-flow global guarantee (Candes-Li-
  Soltanolkotabi 2015; Sun-Qu-Wright 2018) requires random oversampled measurements and does NOT transfer to our
  deterministic banded operator. LESSON: do not chase a fancier alternating-projection / HIO / gradient
  realization for the coupled case; solve the residual exactly.

### 3d. The discrete / byte-exact + gate layer

- LUT snap (D11-1/2/3/4, D07-5/6/7). NOT a dense-lattice CVP: each tick is one integer bucket on a uniform 1-D
  grid (McSineTable, 0.0055 deg pitch), weakly coupled through the few active walls, residual dimension 0-4. So
  LLL/BKZ is overkill; the tool is decoupled nearest-bucket rounding on straightaways plus a small
  Schnorr-Euchner enumeration over the coupled ticks, scored by real ExactJumpModel reach. Because the snap is a
  MAXIMIZATION (byte-exact can OUT-reach the continuous model by up to ~1e-2 b via half-angle norm>1, Stage 0
  section 4), a distance-minimizing Babai round leaves reach on the table that objective-scoring recovers.
  Citations: Schnorr & Euchner, Math. Programming 66:181-199, 1994; Agrell-Eriksson-Vardy-Zeger, IEEE IT
  48(8):2201-2214, 2002; Hassibi & Vikalo, IEEE TSP 53(8):2806-2818, 2005 (polynomial EXPECTED complexity in the
  lattice-point-plus-small-noise regime); Jalden & Ottersten, IEEE TSP 53(4):1474-1484, 2005 (worst-case
  exponential, hence restrict to the small window). CAVEAT (D07-6): sphere decoding certifies the minimal-
  perturbation snap, NOT the byte-exact optimum; ExactJumpModel replay stays the certificate.
- Gate (D11-5/6/7). The inertia gate is a per-(tick,axis) big-M indicator (velocityWalls = band-in, keepAliveWall
  = band-out; Balas disjunctive programming, Annals of Discrete Math 5:3-51, 1979). The shipped BoundPrunedRecovery
  enumerates an INCOMPLETE fixed subset of these disjunctions and its null is NOT an infeasibility certificate
  (F10). Replace with branching on the gate-critical binaries (band-in/band-out), which is complete over the
  reachable indicator lattice and yields a REAL infeasibility certificate, shipped as a HYBRID (banded closed-form
  fast path when the gate-critical set is empty/tiny, small MIP only on cold miss), preserving the microsecond
  path (dsf-neo solved with zero tree nodes, A05-3). MISDP/MISOCP solvers (SCIP-SDP, Gally-Pfetsch-Ulbrich, OMS
  33(3):594-632, 2018) are the research-oracle lens; the shipped replacement is a bespoke small-binary B&B, a
  refactor of the existing tree.
- SMT-FP (D11-8). Bitwuzla (Niemetz-Preiner, CAV 2023) and OptiMathSAT OMT(FP) (Trentin-Sebastiani, JAR
  65:1071-1109, 2021) are per-window VERIFIERS only, never searchers (3 free ticks return unknown at 300 s),
  never shipped. Kept as an optional out-of-tree certifier.
- External analogue (D11-9): discrete-phase constant-modulus beamforming (quantized phase shifters) is the same
  sine-LUT snap, with published linear-complexity optimal discrete quantizers (arXiv:2303.13046, 2023),
  independent evidence the snap is cheap on the decoupled part.

### 3e. Smoothing

The four shipped metrics are the discrete TV / trend-filtering / L0-Potts ladder (D13-1): `SmoothingPolish`
roughness is the reversal-BLIND L2 (min-jerk, Flash-Hogan 1985); `jerkOf = ||D2 theta||_1` is order-1 trend
filtering (Kim-Koh-Boyd-Gorinevsky, SIAM Review 51(2):339-360, 2009; Tibshirani, Ann. Stat. 42(1):285-323, 2014);
the reversal COUNT is an L0 change-point object solved exactly by DP (Johnson, JCGS 22(2):246-260, 2013; PELT,
Killick-Fearnhead-Eckley, JASA 107(500):1590-1598, 2012; circle-valued Potts, Weinmann-Storath-Demaret). Exact
1D L1-TV has O(n) non-iterative solvers (taut-string Davies-Kovac, Ann. Stat. 29(1):1-65, 2001; Condat, IEEE SPL
20(11):1054-1057, 2013). The give-back as a hard constraint `obj >= best - X` is the epsilon-constraint method
(Haimes-Lasdon-Wismer, IEEE SMC-1(3):296-297, 1971), which is Pareto-correct on nonconvex fronts and gives the
single choke point that fixes the F6 stacked give-back. CAVEAT (D13-8): the walls are nonconvex in theta, so the
exact TV solver is a SEED for a byte-exact-repaired local search, not the whole mechanism, and the load-bearing
dither MUST NOT be flattened (the give-back cap plus byte-exact feasibility forbid it). On the ARCH-1 reduction
path smoothing collapses to an enumerated L0 tie-break over the 0-4 degenerate ticks (D13-7).

---

## 4. TO BE MEASURED IN STAGE E (unmeasured hypotheses and latency estimates)

Every latency figure in the shards is a complexity-based ESTIMATE; every pure-Java-robustness claim is an
UNMEASURED-HYPOTHESIS. Consolidated, with the experiment that closes each:

1. Pure-Java IPM SOCP wall-clock and robustness at n in {9, 39, 49, 353}; must match COPT SOCP references and
   converge at n=353 where AL-FISTA fails (D12-7/8). The bound's latency is estimated low-single-digit ms at
   n<=49 and tens of ms at n=353 (dominated by the tiny wall count), UNMEASURED.
2. Residual-solve pure-Java wall-clock per k (arc-k1, null-space/univariate-k2, SDP-or-enumeration-k3-4) vs the
   COPT references and the incumbent SLP/ILS, byte-exact round-tripped, on j021/j008b/loopmm and the dF-chain
   captures (D14-6, D02-10, D06-7). Estimated sub-ms for k<=2, low-ms for k=3-4, UNMEASURED.
3. k=2-4 pure-Java robustness of the tiny complex SDP + rank-one decomposition (does the 4x4 Hermitian eigensolve
   extract the exact point, or is an enumeration fallback needed) (D03-12, D14-3).
4. Degenerate-set crispness AFTER the dual is converged (D02-11): is `T_d` crisp at a threshold near the
   norm-smoothing floor, or must a few candidate active sets be enumerated? This is upstream of the whole residual
   solve; the shipped dual's flat degenerate face does not pin it.
5. Corpus-wide SOCP-disk throttled-tick sweep on all 54+ captures to empirically confirm the residual never
   exceeds n_x-1 = 3 (or 6 with gate live), settling the D01-7 genericity caveat for our friction {A,B}.
6. Residual coupling-graph structure test (forest / tridiagonal / bipartite) per coupled capture; if it holds, the
   SDR is exact by structure and a plain convex solve recovers the point (D02-7).
7. Gate indicator MIP discriminator: does one small MISOCP land loopmm-3jump and dsf-neo at the byte-exact
   objective AND certify infeasibility where BnB returns null, within the 0.1-800 ms envelope (D11-6, A05-9)?
8. Sphere-decode snap: leaf count, wall-clock, byte-exact residual vs the COPT integer optimum on
   j021/j008b/loopmm/f2f; must match within the 1-bucket budget (~1.5e-4 b) and recover the half-angle gain (D11-4).
9. Riemannian RTR with a negative-curvature step: does it resolve j021 t12 and thousand where the costate default
   lands 0.083 b off, and does it solve the momentum class (j716/j828/j1150) from the costate seed (D05-7)?
10. Smoothing A/B: realized reversal sums and give-back of the one-mechanism trend filter vs the four-pass stack on
    the hpk corpus and thousand, with a Johnson/PELT exact-L0 reference for the reversal-minimal feasible path (D13-13).
11. u-space consensus-ADMM / FPP-slacked-SLP vs SLP: iterations, ms median/spread, final byte-exact residual,
    null-rate, objective gap to COPT (D09-7).
12. dF as a phase/sector constraint in the residual: the COPT dF-constrained reference (needs dF modeled in COPT),
    verifying the residual dimension does not grow (D02-12, D14-7).
13. The residual MECHANISM: confirm branch-WITH-convex-reopt (not rigid fix-and-solve) pure-Java, per the measured
    poc infeasibility of the naive decomposition (see section 5).

All Stage E benchmarks run via direct `java -cp` (Gradle swallows -D/env), warmup then repeated timed runs,
medians and spreads, byte-exact certified through ExactJumpModel, against the Stage 0 COPT references.

---

## 5. DEPENDENCY STANCE

The shipped path stays dependency-free / pure-analytical, which the repo already prefers (it dropped commons-math3
for cross-loader packaging; re-adding an LP library measured net-negative, A04-7). The one hard question was the
convex SOCP kernel, and D12 settled it: NO maintained, permissively-licensed, redistributable PURE-JAVA SOCP
solver exists. The verified survey:

- ojAlgo (MIT, pure Java, zero deps): mature QP, but native SOCP only PARTIAL; usable only for QP, never SOCP.
- JOSQP (MIT, pure Java 8+): QP-only (no SOCP).
- Apache Commons Math3 (Apache-2.0, pure Java): LP-only (SimplexSolver), already dropped.
- ECOS (GPLv3, ANSI-C), SCS (MIT, C), OSQP (Apache-2.0, C, QP-only), Clarabel (Apache-2.0, Rust/FFM), ALGLIB
  (GPL free / commercial): every real SOCP is native or copyleft/commercial.

The native-binding route (JNI/FFM) is independently dead on the loader packaging matrix: two of the three loaders
are Java 8 (Forge 1.8.9, 1.12.2) where FFM (Java 22) does not exist, and per-platform binaries shaded across three
loaders is strictly heavier than the imgui-java native burden the project already fights (D12-6). VERDICT: build
the convex kernel from scratch in pure Java (the IPM of section 3a), reusing the in-repo dense Cholesky. Every
other stage of ARCH-1 is pure-Java dependency-free by construction: the residual solve is closed-form / small
eigensolve / bounded enumeration (no external QCQP solver exists to ship anyway, D06-8); the snap is a self-
contained Schnorr-Euchner loop reusing FacingLattice; the smoothing is a taut-string plus the existing restore.
The only permissive pure-Java dependency that could EVER be justified is ojAlgo (MIT), and only for its mature QP,
never SOCP. COPT, Manopt/Pymanopt, and any SDP/moment solver (GloptiPoly, TSSOS, SCIP-SDP) stay strictly
research-side oracles, never on any shipped classpath.

---

## 6. CONFLICTS AND CORRECTIONS

1. THE PATAKI MIS-APPLICATION (D03-4/5, the one cross-shard tension the mission flagged; already amended by the
   orchestrator in SPEC 4.2). The SPEC's original "residual bounded by Pataki r(r+1)/2 <= #active walls" was a
   loose and strictly incorrect application of the rank bound to the FULL 2n+1 Shor lift, whose affine-constraint
   count is `m_opt ~ n + 2 + #active_walls` (all n per-tick modulus equalities, the normalization, the objective
   hyperplane, and the active walls), giving for j021 the loose bound rank <= 9, NOT the MEASURED rank 2-3. The
   CORRECTED statement: the tight explanation is the KKT active-set reduction, non-degenerate ticks are rank-1
   (costate-aligned) and contribute no residual rank, so Pataki applies cleanly to the RESIDUAL SDP over the
   degenerate ticks alone, whose handful of constraints gives rank 2-3, matching eig2/eig1 <= 0.024. SPEC 4.2 now
   reads "amended per D03-4/D03-5," which this dossier confirms is correct. COMPLEMENTARY BOUND (D01): the
   discrete-time LCvx result (Luo-Echigo-Acikmese, arXiv 2410.09748) gives a HORIZON-INDEPENDENT, state-dimension
   cap on the residual (throttled-tick) count of `n_x - 1 = 3` for the pure constant-modulus program, or
   `2 n_x - 2 = 6` with the gate/dF live (Luo-Spada-Acikmese, arXiv:2501.06931), for our `n_x = 4`. Take the
   minimum of the two caps. HONEST CAVEAT (D01-7): the `n_x - 1` bound rests on controllability + normality +
   genericity of the friction `{A,B}` that we have NOT verified, so for us it is measured-consistent (0-4 observed),
   not a proven cap; the corpus-wide sweep (appendix item 5) would settle it. Note the two caps do not explain the
   large-k momentum count (10-22 degenerate ticks on j716/j828/j1150); that class exceeds `2 n_x - 2` because it
   is not covered by the pure-modulus genericity hypothesis, and its degenerate ticks form a coordinated low-DOF
   momentum phase (COPT still solves it globally in < 0.5 s), which is why the residual solver there must be smart
   (spatial B&B / Riemannian), not the k<=4 closed-form ladder.

2. RESIDUAL "FIX-AND-SOLVE" vs "BRANCH-WITH-CONVEX-REOPT" (D14 vs poc-residual-validation). D14-1/D14-4 formalize
   the residual as "fix every non-degenerate tick at its closed-form costate direction `u_t^cf = m_t g_t/|g_t|`,
   then solve the small system over `T_d`." The orchestrator's Stage E precursor MEASURED that this naive
   decomposition is INFEASIBLE on j021 and j008b (residual_poc returned INFEASIBLE), because the disk's
   non-degenerate directions are conditioned on the throttled tick being SHORT; forcing the throttled tick to full
   modulus while holding the rest rigid breaks the walls. The CORRECT mechanism RE-OPTIMIZES the non-degenerate
   ticks convexly at each branch node (they shift slightly). loopmm happens to work with the naive fix because its
   degenerate tick is the near-decoupled start tick. CORRECTION: the residual solve is branch-WITH-convex-reopt,
   not rigid fix-and-solve; D14-1/D14-4's clean "fix cf, solve T_d" is the structure, but the realization must
   re-solve the convex completion per candidate. This is inexpensive (a converged pure-Java disk SOCP is sub-ms to
   low-ms at n<=49) and the poc confirmed it reaches the COPT global optimum within 1e-5 to 3e-5 b.

3. THE CONTINUOUS OPTIMUM IS A BOUND/GUIDE, NOT THE BYTE-EXACT ANSWER (byte-exact-roundtrip). Three MEASURED
   regimes: (a) 0 degenerate ticks + small half-angle (j019, j021) snaps cleanly and RECOVERS headroom (j021
   +1.4e-3 b over shipped THOROUGH); (b) a degenerate tick's direction is ARBITRARY on the degenerate face
   (j008b), so the specific continuous direction byte-realizes WORSE (-0.219 vs continuous -0.197 vs shipped
   -0.215), which is the concrete reason the snap must be OBJECTIVE-AWARE (sphere-decode the byte-exact objective
   at the degenerate tick, not snap the arbitrary direction); (c) half-angle-dominated single jumps (j005) have
   their byte-exact optimum at DIFFERENT yaws (norm>1 gain), so the continuous optimum is not even a good start
   and a byte-exact search is mandatory (the shipped fast-path + ILS already does this). CONSEQUENCE: ARCH-1 must
   END with an objective-aware byte-exact search over the degenerate AND half-angle-relevant ticks, then certify
   through ExactJumpModel; the ARCH-1 win is replacing the BAD suggest (defaulted degenerate direction -> 0.34 b
   infeasible) with the residual-solve suggest while KEEPING the good improve (BucketAscent/ILS/sphere-decode).

4. THE FULL SDP BUYS NO BOUND OVER THE DISK (D06-6, D03, D10, corroborating Stage 0). On the coupled cases the
   Shor SDP bound EQUALS the SOCP disk bound to 6 digits (j021 1067.865480, loopmm -279.299065); the SDP's only
   unique product is the rank/spectrum readout that reveals the residual dimension. So "SDR / dual bound" collapses
   to "converged SOCP disk + rank readout," NOT a shipped SDP, and no pure-Java SDP solver is needed on the
   shipped path (moment-SOS and CS-TSSOS stay Stage-E oracles; the full-problem CS-TSSOS is redundant against the
   COPT spatial B&B that already solves n<=49 globally in < 0.3 s). The direct small-QCQP methods (arc, null-space,
   generalized-eigenvalue, tiny B&B) dominate a moment SDP on the residual and are dependency-free (D10-8).

5. NO STANDALONE CONFLICT, but a scope note: D04 (UQP power/MM), D08 (phase retrieval), and D09 (ADMM/MM) all
   independently reach the SAME verdict from different literatures: every full-dimension primal iteration on our
   class is LOCAL / KKT-only and is global ONLY where Stage 0 already proved the SDR rank-1 (single/easy, needing
   no iteration). None is the ARCH-1 global engine; the globality is the section-4.2 convex-dual-plus-low-dim-
   residual reduction. The three agree, which strengthens the verdict rather than conflicting.
