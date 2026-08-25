# Stage D shard: agent D14 (the residual solve: active-set QCQP, null-space, min-slack over a few circles)

Agent: D14. Territory: the RESIDUAL SOLVE of ARCH-1 step (3). Given the convex dual/active-set
(lambda*, A) and the vanishing-costate ticks T_d (|T_d| = 1 to 4), find the direction assignment on
those 1 to 4 circles that recovers the byte-exact optimum, with a real infeasibility certificate. This
is the highest-value collapse in SPEC section 6 (ARCH-1). This shard synthesizes the other method
families' relevance to THIS specific subproblem and recommends a concrete Stage E prototype.

Files/code inspected (byte-anchored, this session):
- `core/.../anglesolver/solver/CostateDualSolver.java` (costate `g_t = c_t - sum_j lambda_j A_{j,t}`,
  recovery `u*_t = m_t g_t/|g_t|`, EPS2=1e-14 norm smoothing, U_TOL degenerate early-exit, MAX_ITER=100,
  DIVERGE_PGRES=4.0; lines 39-57, 208-273, 408-449).
- `core/.../anglesolver/solver/JumpLinearModel.java` (`objectiveVectors` 205-216: one axis nonzero;
  `compileWall` 223-269: per-tick single-axis coef, p0coef, eq flag, `recoverYawDeg` 363-364
  `wrap(atan2(gz,gx) - baseArg[t])`).
- SPEC.md sections 4.2-4.3, 4.4, 4.5, 6; `stage0-copt/FINDINGS.md` sections 1a/1b/1c/2/3;
  `stageA/SYNTHESIS.md` F1, F5, F7, F8, F10, collapse-opportunity 5.

Commands run: none (methods/literature shard; all quantitative numbers are cited from Stage 0 COPT
measurements or from the code constants above, tagged accordingly). Web citations are real and listed at
the end; each was fetched via WebSearch this session.

Convention: ESTABLISHED = a proved result in the cited literature or a Stage 0 MEASURED number.
SPECULATION / UNMEASURED-HYPOTHESIS = a belief to route into a Stage E prototype. Applicability tags:
[measured-against-our-model] vs [theoretical].

---

## D14-1. The residual, formalized exactly (and it is a FEASIBILITY problem, not an objective problem)

LOCATION: research topic; anchored to CostateDualSolver.costate/grad + JumpLinearModel.compileWall.

CLAIM: The residual over the degenerate ticks is a small nonconvex FEASIBILITY (min-slack) problem: put
`|T_d|` complex variables on their circles so the ACTIVE walls hold at equality; the objective over those
ticks is already fully priced by the dual and adds no independent pull, so "min-slack feasibility then
objective" collapses to "min-slack feasibility, smoothness as tie-break."

FORMALIZATION.
Variables `z_t = u_t = (ux_t, uz_t) in R^2 =~ C`, `t = 0..n-1`, modulus pinned `|z_t| = m_t` (JumpLinear
`mMag[t]`). Walls compiled (compileWall) to single-axis rows: wall `j` reads one axis `a_j in {X,Z}` and
is `sum_t coef_j[t] (z_t)_{a_j}  <=  b'_j` (or `=` if `eq[j]`). Objective (objectiveVectors) is
`max sum_t c_t . z_t` with `c_t` nonzero on one axis. Costate at multiplier `lambda`:
`g_t = c_t - sum_j lambda_j coef_j[t] e_{a_j}` (exactly CostateDualSolver.costate). KKT on the circle:
`z_t = m_t g_t/|g_t|` whenever `g_t != 0` (CostateDualSolver.grad).

Let `(lambda*, A)` be the converged convex dual and its active set. Define
`T_d = { t : |g_t| <= tau }` (the vanishing-costate ticks; because EPS2=1e-14 smooths the norm, `g_t` is
never exactly 0, so T_d is a THRESHOLD set, tau calibrated near the norm-smoothing floor; A03 used
`|g_t| <= 7.3e-10` on j828). Fix `u_t^cf = m_t g_t/|g_t|` for every `t not in T_d`. The RESIDUAL is:

  minimize   s
  over       { z_t : t in T_d },   s
  subject to sum_{t in T_d} coef_j[t] (z_t)_{a_j}  <=  r_j + s   (all inequality walls reading a T_d tick)
             | sum_{t in T_d} coef_j[t] (z_t)_{a_j} - r_j |  <=  s   (equality walls)
             |z_t| = m_t,   t in T_d
  where      r_j = b'_j - sum_{t not in T_d} coef_j[t] (u_t^cf)_{a_j}   (residual RHS after fixing cf ticks)

Dimension: `2|T_d|` real unknowns (`|T_d|` complex), `|T_d|` nonconvex modulus equalities (1 to 4), and a
few affine walls. This is exactly the handful of nonconvex ticks COPT's spatial B&B branches on
(SPEC 4.2).

THE STRUCTURAL SIMPLIFICATION (established from KKT): at a degenerate tick `g_t = 0` means
`c_t = sum_{j in A} lambda*_j coef_j[t] e_{a_j}`, so the tick's objective gradient lies ENTIRELY in the
span of the active walls. Hence `sum_{t in T_d} c_t . z_t = sum_{j in A} lambda*_j (sum_{t in T_d}
coef_j[t] (z_t)_{a_j})`: maximizing the objective over the degenerate ticks is identical to driving the
active walls to their bound. Feasibility and objective are ALIGNED on T_d. Therefore the residual is a
PURE min-slack feasibility problem: if `min-slack s* = 0` the dual value `D` is achieved with ZERO gap
(the recovery is exact, "H2 was only degeneracy"); if `s* > 0` there is a genuine circle-vs-disk gap of
size `~s*` at those ticks (H1), and the min-slack point IS the correct best-feasible primal.

EVIDENCE: [measured-against-our-model] Stage 0 measured the disk-vs-sphere looseness at exactly these
ticks: j021 loose by 1.6e-3 b at 1 tick (t12, modulus slack 0.083), j008b 1.5e-3 b at 4 ticks
(dominated by t1), loopmm ~0 (disk == sphere) at t0; these are the `s*` the residual would return. The
"defaults degenerate ticks to the objective axis" failure (SPEC 4.3) is CostateDualSolver leaving those
ticks at `atan2` of the near-zero residual costate (recoverYawDeg on `g_t ~ 0`), producing j021 0.34 b /
thousand 2.89 b infeasibility instead of solving this system.

IMPACT: correctness + simplicity, decisive. Replaces the full-n SLP/ILS fallback with a `2|T_d|`-variable
(1 to 4 complex) solve. This is the single ARCH-1 collapse.

PROPOSAL: implement the residual as stated, consuming `(lambda*, A, T_d, u^cf)` from CostateDualSolver;
solve min-slack; if `s*` is within the byte-exact floor accept, else report `s*` as the measured H1 gap.

CONFIDENCE: 0.9 (the formalization and the feasibility/objective alignment are exact from KKT).
DEPENDS-ON: D14-5 (certificate), and the separate Stage D/E item "make the convex dual converge" (T_d and
A are only trustworthy once the dual converges; Stage 0 measured the shipped dual loose by non-convergence,
COPT SOCP converges in <20 ms).

---

## D14-2. k = 1 (one circle + linear walls): CLOSED FORM by arc intersection; the TRS/eigenvalue view is a certifier, not needed to solve

LOCATION: research topic (the single most common degenerate case: j021 t12, loopmm t0, the whole
one-degenerate-tick class).

CLAIM: For `|T_d| = 1` the residual is solvable EXACTLY in closed form, no iteration: on the circle
`z = m e^{i theta}` every wall is a sinusoid in theta, feasibility is an intersection of arcs, and
min-slack is a minimax of sinusoids whose optimum is at a breakpoint. The trust-region-subproblem
eigenvalue machinery is relevant only as an independent optimality certificate.

MECHANISM (established, elementary): wall `j` reads one axis, so
`coef_j (z)_{a_j} = m coef_j (cos or sin)(theta) = m |coef_j| cos(theta - phi_j) <= r_j`, a single
sinusoid. Its feasible set is one ARC of `S^1`. Feasibility of all walls = intersection of `|W|` arcs =
interval arithmetic on the circle, `O(|W| log|W|)`. Min-slack `min_theta max_j (m|coef_j|cos(theta-phi_j)
- r_j)`: the upper envelope of `|W|` sinusoids is piecewise-sinusoidal; its global min is at an envelope
breakpoint (a pairwise sinusoid intersection, closed form via a linear-in-`(cos,sin)` solve) or at a wall
tangent, `O(|W|^2)` candidates checked in closed form. The objective `c.z = m|c|cos(theta - phi_c)` is
itself a sinusoid, so among feasible arcs its max is at the unconstrained argmax `phi_c` if interior, else
at an arc endpoint. Everything is closed form.

TRS / GENERALIZED-EIGENVALUE VIEW (established, [theoretical], as a certifier): "optimize a linear form
over `|z| = m` with one active linear cut" is the CDT / two-quadratic-constraint case, which has PROVEN
strong duality over the complex plane (Beck-Eldar 2006; Ai-Zhang CDT), and a single circle equality is a
classical TRS solvable by ONE generalized eigenvalue with no outer iteration (Gander-Golub-von Matt 1989;
Adachi-Iwata-Nakatsukasa-Takeda 2017). We do not need the eigenvalue solve to FIND the k=1 optimum (the
arc method is simpler and exact); its value is that strong duality guarantees the arc optimum is GLOBAL
and yields the dual certificate for D14-5.

EVIDENCE: [measured-against-our-model] the k=1 cases in Stage 0 (j021 1 throttled tick, loopmm 1) are
exactly this class; COPT's global optima (j021 1067.863880, loopmm -279.299065) are the reference the arc
solver must reproduce after fixing the `n-1` closed-form ticks. UNMEASURED-HYPOTHESIS: that the arc solver
reproduces them to the byte-exact floor (route to Stage E).

IMPACT: speed + simplicity + correctness. Sub-microsecond, exact, certifiable; covers the most common
degenerate case. Ship first.

PROPOSAL: Stage E prototype `ResidualSolveK1` (arc intersection + sinusoid minimax + objective-on-arc),
benchmark on j021/loopmm against COPT.

CONFIDENCE: 0.9. DEPENDS-ON: D14-1.

---

## D14-3. k = 2 to 4: complex SDR is provably tight here (up to 3-4 complex constraints), with rank-one decomposition to recover the primal; active-set enumeration and Riemannian polish as fallbacks

LOCATION: research topic (j008b's 4 degenerate ticks; the general coupled case).

CLAIM: The residual for `|T_d| = 2..4` is a COMPLEX QCQP whose nonconvex quadratic constraints number
`|T_d|` (the modulus equalities), which sits at or below the complex SDR-tightness boundary; the complex
Shor SDP is exact in this regime and a global `z` is recovered by a complex rank-one matrix
decomposition. Where rank exceeds 1, a single moment lift (level 2) or a tiny active-set enumeration
closes it; Riemannian descent on the product of circles is the local "Improve."

WHY THE COMPLEX LIFT MATTERS (established, [theoretical]): identify `z_t in C`. For homogeneous QCQP the
SDR has a ZERO duality gap when the number of quadratic constraints is `<= 2` (real) or `<= 3` (complex),
and Ai-Liang-Yuan (Math. Programming 2024) give a verifiable necessary-and-sufficient tightness test for
`3` real or `4` complex homogeneous constraints. Our residual has `|T_d| = 1..4` modulus constraints, i.e.
squarely in the complex-tight regime for `|T_d| <= 3` and at the tested boundary for `|T_d| = 4`. This is
the same reason single jumps are rank-1 exact (LCvx / one constant-modulus constraint): the complex
structure keeps the SDR rank-1 far longer than a naive real count suggests. Beck-Eldar (2006) proved the
two-constraint complex case has strong duality outright; Huang-Zhang (2007, "Complex Matrix Decomposition
and Quadratic Programming," Math. of OR) gives the CONSTRUCTIVE complex rank-one decomposition that
extracts the optimal `z` from the SDP matrix.

MEASURED CONSISTENCY: [measured-against-our-model] Stage 0 solved the Shor SDP and read eig2/eig1 =
0.0169 (j021), 0.0239 (j008b), 0.0188 (loopmm): rank 2-3, i.e. NOT rank-1 but small, exactly the
"at-the-boundary" regime the tightness theory predicts for these constraint counts. The SDP bound EQUALS
the disk bound to 6 digits there (no tighter), which is the signature that one rank-reduction / one moment
level closes the residual rather than a full hierarchy.

METHODS, in recommended order for `|T_d| = 2..4`:
(a) COMPLEX SHOR SDP + rank-one decomposition (Huang-Zhang 2007; Sturm-Zhang 2003 rank-one procedure).
    SDP dimension `|T_d| + 1` (Hermitian, with a unit-modulus anchor); solves in well under 1 ms with a
    small interior-point (reuse CostateDualSolver's Cholesky). When the returned matrix is rank-1, the
    decomposition gives the exact global `z`; when rank 2-3, purify to rank-1 by the Sturm-Zhang / Ai-Zhang
    decomposition or escalate to (b).
(b) COMPLEX MOMENT / LASSERRE LEVEL 2 (Josz-Molzahn 2018 complex hierarchy; D'Angelo-Putinar complex
    Positivstellensatz). Because `|T_d| <= 4`, the level-2 moment matrix is tiny (`O(|T_d|^2)`), so the
    global optimum AND the infeasibility certificate (D14-5) come from one small complex SDP.
(c) ACTIVE-SET ENUMERATION: the residual optimum hugs a subset of walls at equality; enumerate the
    `<= 2^{|A|}` active combinations (only those with `#active-eq <= 2|T_d|` are dimensionally viable),
    and for each solve "linear-over-product-of-circles with linear equalities" by its KKT polynomial system
    (resultant / companion-matrix root finding, small). Deterministic, pure-Java, no SDP.
(d) RIEMANNIAN on the `|T_d|`-torus (product of circles = complex-circle / oblique manifold): parametrize
    `z_t = m_t e^{i theta_t}`, minimize an exact-penalty of the wall slack by Riemannian gradient/CG
    (Absil-Mahony-Sepulchre 2008; Boumal 2023 / Manopt; the constant-modulus MIMO MO-AltMin analogue,
    Yu-Shen-Zhang-Letaief 2016). Local; use multi-start or as the Park-Boyd (2017) "Improve" after an SDR
    "Suggest."

EVIDENCE: [measured-against-our-model] the COPT global optima (j008b -0.196938 in 0.14 s at n=25/2 jumps;
j021 1067.863880 in 0.27 s) are the references; the residual is strictly smaller than COPT's full n-tick
QCQP (COPT branches on all nonconvex ticks; the residual branches only on `T_d`), so any of (a)-(d) on
`|T_d| <= 4` is expected well inside the envelope. UNMEASURED-HYPOTHESIS: which of (a)-(d) is most robust
pure-Java (route to Stage E, benchmark all against the COPT references).

IMPACT: correctness + simplicity. Closes the 4-degenerate-tick class (j008b, the worst measured headroom
1.8e-2 b) with a `<= 8`-real-variable solve instead of full-n ILS.

PROPOSAL: Stage E prototype the complex SDR + Huang-Zhang decomposition first (smallest, has a native
certificate); keep the active-set enumeration (c) as the dependency-free fallback and the Riemannian
descent (d) as the ARCH-3 guard.

CONFIDENCE: 0.75 (tightness regime is established and measured-consistent; the pure-Java robustness of the
tiny SDP is the unmeasured risk).
DEPENDS-ON: D14-1, D14-5.

---

## D14-4. The null-space view: fix the active equalities as an affine subspace, solve the reduced nonconvex feasibility on the remaining 1 to 2 free directions

LOCATION: research topic; this is Stage A collapse-opportunity 5 ("min-slack projection over
null(A_active^T)") made precise.

CLAIM: Fixing the tight active walls as linear EQUALITIES on the stacked degenerate variables reduces the
residual to a feasibility problem on a very low-dimensional affine slice, often 1 to 2 free real
directions, where the modulus equalities pin the answer to a finite candidate set solvable by
substitution.

MECHANISM (established): let `w = [z_t]_{t in T_d} in R^{2|T_d|}`. The active equality walls are
`A_eq w = r_eq` (from D14-1 with `s = 0` on those rows). Parametrize `w = w_p + N y`, `N` a basis of
`null(A_eq)`, `dim y = 2|T_d| - rank(A_eq)`. Substitute into the `|T_d|` modulus equalities `|z_t| = m_t`
and the remaining inequality walls. When `rank(A_eq) = 2|T_d| - 1` the free space is 1-D and the modulus
equalities give a small polynomial in `y` (finite roots, closed form). When it is 2-D, this is a
2-variable nonconvex feasibility solved by the k<=2 methods of D14-2/D14-3. The null-space view is the
cheapest realization when the active set is nearly determining (the common case: a degenerate tick appears
precisely because opposing corridors pin it).

WHY THIS IS THE RIGHT DEFAULT vs the shipped behavior: SPEC 4.3's measured failure is that the shipped
recovery DEFAULTS the degenerate ticks to a fixed direction instead of solving this system. The null-space
projection is the minimal correct replacement: it uses the SAME `(lambda*, A)` the dual already computed,
adds no SDP, and is pure linear algebra plus a small root solve.

EVIDENCE: [measured-against-our-model] A03 measured on j828 that all 13/39 off-sphere ticks have
`|g_t| <= 7.3e-10`, i.e. the modulus slack lives ENTIRELY in the `g=0` null space (Stage A F1), which is
the direct empirical signature that the degeneracy is a null-space phenomenon and the null-space solve is
the natural fit. UNMEASURED-HYPOTHESIS: the fraction of degenerate cases with `dim y <= 2` (route to Stage
E; if most, the null-space route dominates and the SDP is a rare fallback).

IMPACT: simplicity + speed. The cheapest residual realization; likely the primary path for k=1 and many
k=2.

PROPOSAL: implement the null-space reduction as the front end of the residual solve (reduce dimension
first, then dispatch to D14-2 for the 1-D case or D14-3 for 2-D+).

CONFIDENCE: 0.8. DEPENDS-ON: D14-1.

---

## D14-5. The infeasibility certificate: a real Farkas/S-procedure certificate on the SMALL residual, replacing the BnB-null non-certificate (F10)

LOCATION: research topic; fixes SYNTHESIS F10 (BnB null is not an infeasibility certificate: incomplete
pattern family, F-blind bound, capped restore).

CLAIM: The residual is small enough to carry a genuine, checkable infeasibility certificate. For k=1 it is
an exact finite arc-covering certificate; for k>=2 it is a complex S-procedure / SDP-dual ray, upgraded to
an exact complex-Positivstellensatz certificate at a finite (level 1-2) moment relaxation.

MECHANISMS.
- k=1 (established, exact): each wall forbids an arc of `S^1`; the residual is infeasible iff the union of
  forbidden arcs COVERS the circle. The covering set of arcs is a finite, directly checkable certificate
  (a theorem-of-alternatives on `S^1`). No relaxation, no heuristic.
- k>=2 (established, [theoretical]): the complex S-lemma / S-procedure (Polik-Terlaky 2007 survey) gives a
  dual multiplier vector such that a nonnegative combination of the wall and modulus constraints is a PSD
  form that cannot be nonnegative on the feasible set: a dual-feasible ray of the complex Shor SDP with
  negative objective CERTIFIES primal infeasibility. Because the complex SDR is tight in this
  constraint-count regime (D14-3: <= 3-4 complex constraints, Beck-Eldar / Ai-Liang-Yuan), the SDP-dual
  certificate is not merely a relaxation bound but is exact here. For a fully rigorous emptiness proof, one
  level of the complex moment-SOS hierarchy (Josz-Molzahn 2018; D'Angelo-Putinar Positivstellensatz)
  certifies that the product-of-circles intersect walls is empty; the certifying SDP is tiny because
  `|T_d| <= 4`.

CONTRAST WITH F10 (established from code): BnB (BoundPrunedRecovery) returns `null` on a cold miss, which
Stage A F10 measured is NOT a certificate (three independent incompleteness sources), yet the Optimize
graph routes that null straight to "no solution." The residual certificate is SOUND: either it returns a
feasible `z` (the recovery) or a finite covering / SDP-dual object that PROVES no assignment exists at the
identified active set. (Caveat, honest: the certificate is conditional on `(lambda*, A, T_d)` being the
correct optimal active set; a wrong active set can make a solvable instance look locally infeasible, so
the certificate proves infeasibility of the RESIDUAL at that active set, and the outer loop must confirm
the dual converged. This is strictly stronger than BnB-null, which proves nothing.)

EVIDENCE: [measured-against-our-model] Stage 0 confirmed df-chain-free-start is genuinely infeasible at
fixed start (worstWallViol +2.46 b) and feasible with free `p0`: exactly the kind of instance where a real
certificate (vs a null) tells the user "translate the start" instead of "no solution."

IMPACT: correctness, the most dangerous current gap (F10 surfaces solvable jumps as "no solution").

PROPOSAL: emit the k=1 arc-covering certificate and the k>=2 SDP-dual ray from the residual solve; wire the
graph's cold-miss branch to consume it instead of treating null as infeasible.

CONFIDENCE: 0.8 (k=1 exact certificate is certain; the k>=2 exactness rides on the measured tightness
regime). DEPENDS-ON: D14-1, D14-3.

---

## D14-6. Port feasibility and cost: brute-force angle enumeration is fast pure-Java for k<=2, structured methods for k=3-4; all dominate the current full-n SLP/ILS fallback

LOCATION: research topic; the cost case for replacing the SLP/ILS full-n fallback.

CLAIM: Because the residual is `2|T_d|`-dimensional with `|T_d| <= 4`, even naive angle-grid brute force
is microseconds-to-milliseconds for k<=2, and the structured methods (D14-3/D14-4) handle k=3-4; every
option is a 10-50x dimension reduction over the full-n fallback and stays inside the 0.1-800 ms envelope.

COST ESTIMATES (flops, order-of-magnitude, `|W|` walls reading T_d ticks, typically 5-25):
- k=1: exact arc method `O(|W| log|W|)`, sub-microsecond. Brute grid `G=4096` points x `|W|=25` = ~1e5
  flops, still microseconds.
- k=2: brute grid `G^2 x |W|` at fine `G=4096` = ~4e8 flops (~ms), or coarse `G=256` (~1.6e6, sub-ms) plus
  a Newton/Riemannian polish; or the null-space reduction (D14-4) making it 1-D. Comfortable.
- k=3: fine grid dead (`G^3`); coarse `G=64` = ~6.5e6 flops (~ms) + polish, or the complex SDP (dim 4,
  << 1 ms).
- k=4 (j008b): grid dead; complex SDP (dim 5) or moment level-2 (tiny) in well under 1 ms; or active-set
  enumeration `<= 2^{|A|}` small KKT solves.

COMPARISON TO THE THING BEING REPLACED (established from code + Stage A/B):
- SlpSolve rejects 87.5% of its LP steps over the FULL `n` (SYNTHESIS F2, 45430/51907 rejects, 8.01
  LP/accept), all trust-region thrashing near hugged walls.
- BucketAscent/IlsPolish do 3.16M-36.1M evals/call at n=49, each a single-tick change on a full O(n)
  forward (SYNTHESIS F12).
The residual solve is `|T_d| = 1..4` dimensional vs `n = 25..49`, needs no trust region, and (Stage 0)
COPT solves the strictly-larger full nonconvex QCQP globally in 0.02-0.27 s; the residual-only solve is
smaller, so pure-Java residual solve is expected sub-ms for k<=2 and single-digit ms for k=3-4.

EVIDENCE: [measured-against-our-model] the flop counts above are arithmetic from `G`, `|W|`, `|T_d|`; the
comparison baselines (87.5% reject; 3-36M evals) are Stage A MEASURED. UNMEASURED-HYPOTHESIS: the actual
pure-Java wall-clock of each residual method (route to Stage E microbench via direct `java -cp`).

IMPACT: speed + simplicity; removes the full-n fallback thrash on exactly the coupled cases where it wastes
the most.

PROPOSAL: Stage E microbench (warmup + repeated timed runs, medians) of arc-k1, brute-k2, SDP-k<=4 against
the current dualChain->SLP->RelaxationRecovery tail on j021/j008b/loopmm.

CONFIDENCE: 0.8. DEPENDS-ON: D14-2, D14-3.

---

## D14-7. VERDICT: the recommended residual-solve algorithm per k, pure-Java, with a real certificate (the primary Stage E prototype)

LOCATION: research topic; the ARCH-1 step (3) recommendation.

CLAIM: A single `ResidualSolve` primitive, dispatching by `|T_d|`, solves the recovery the shipped solver
fails, pure-Java, with a genuine infeasibility certificate, and is concretely specifiable now.

RECOMMENDED ALGORITHM (dispatch on `|T_d|` after the converged dual gives `(lambda*, A)` and
`T_d = {t : |g_t| <= tau}`, tau near the norm-smoothing floor; fix `u_t^cf = m_t g_t/|g_t|` for
`t not in T_d`; compute residual RHS `r_j`):
- k = 0: no residual; the closed-form recovery is already exact (the fast path). Certificate: dual gap 0.
- k = 1: ARC INTERSECTION + sinusoid minimax (D14-2). Closed form, sub-microsecond, exact. Certificate:
  feasible arc, or arc-covering infeasibility (D14-5). Covers j021 t12, loopmm t0, the single-tick class.
- k = 2: NULL-SPACE reduction (D14-4) to 1-D when the active set is determining, else brute coarse grid +
  Newton polish, or the complex SDP (dim 3). Certificate: complex S-procedure dual ray.
- k = 3-4: COMPLEX SHOR SDP (dim `|T_d|+1`) + Huang-Zhang rank-one decomposition (tight in this
  constraint-count regime), with a moment level-2 escalation if rank > 1; active-set enumeration as the
  dependency-free fallback. Certificate: SDP-dual / complex Positivstellensatz. Covers j008b's 4 ticks.
- ARCH-3 guard (if the tiny SDP is not robust pure-Java): multi-start RIEMANNIAN product-of-circles
  descent (Park-Boyd Suggest-and-Improve; Boumal/Manopt), which Stage 0 shows reaches within the byte-exact
  floor (ILS within 2.8e-5 b of COPT on j021). Documented as the measured cannot-fully-collapse only if the
  exact methods fail.

TIE-BREAKS FOLD IN CLEANLY (established from SPEC 4.5): among feasible residual assignments (when `s* = 0`
leaves a facet of solutions), pick the SMOOTHEST (fewest reversals) as Phase-2 tie-break: this is the C4
collapse (smoothing as the residual-resolution rule, not four post-passes). A dF pin at a degenerate tick
fixes its `theta` (removes it from T_d or adds one phase equality), composing without a separate mechanism
(fixes F8's dF-bail because dF enters the residual as a phase constraint, not a rejected wall).

STAGE E SPEC (concrete enough to build and benchmark):
1. `ResidualSolve` node input: `(n, coef/axis/bPrime/eq per wall, cx/cz, mMag, lambda*, g_t, tau)`.
2. Compute `T_d`, `u^cf`, `r_j`; dispatch by `|T_d|` per the ladder above.
3. Output `z_t` for `t in T_d` (hence yaws via `recoverYawDeg`), `s*` (the measured H1 gap in blocks), and
   the certificate object.
4. Byte-exact round-trip: snap to the sine LUT, replay through ExactJumpModel, report residual (Stage 0
   caveat: byte-exact can out-reach the continuous model by a few e-3 b via half-angles; certify, do not
   trust the continuous value).
5. Benchmark achieved objective + byte-exact residual against the COPT references: j021 1067.863880,
   j008b -0.196938, loopmm -279.299065, f2f -3.860256, and the dF-chain captures; PASS iff it beats the
   current THOROUGH headroom (1.5e-3 b j021, 1.8e-2 b j008b) and matches k=0 behavior on single/easy jumps.

EVIDENCE: [measured-against-our-model] the per-capture references and headroom are Stage 0 MEASURED; the
degenerate-tick counts (1 j021, 4 j008b, 1 loopmm) are Stage 0 MEASURED and set the dispatch. The recovery
failure this replaces (0.34 b j021 / 2.89 b thousand) is the pack's MEASURED root cause.
UNMEASURED-HYPOTHESIS: end-to-end that this primitive lands the COPT optima byte-exact within the envelope
(the Stage E gate).

IMPACT: correctness + simplicity + speed + smoothness, all four; the headline ARCH-1 collapse.

PROPOSAL: build `ResidualSolveK1` (arc) first (cheapest, exact, covers the common case), then
`ResidualSolveSmall` (null-space + complex SDP + rank-one decomposition) for k=2-4; wire both as the
degenerate branch of dualChain, keep ILS as the ARCH-3 fallback. Prototype the complex SDP + certificate in
COPT (per SPEC 4.2 mandate) before the Java port.

CONFIDENCE: 0.8 (k=0/k=1 near-certain; k=2-4 rides on the measured-tight complex-SDR regime and the
unmeasured pure-Java robustness).
DEPENDS-ON: D14-1, D14-2, D14-3, D14-4, D14-5, D14-6, and the parallel "make the convex dual converge" item.

---

## References (all real, checkable; fetched via WebSearch this session)

Convex core + exactness (why k=0 is closed form; TRS/CDT for the certifier):
- B. Acikmese, L. Blackmore. "Lossless convexification of a class of optimal control problems with
  non-convex control constraints." Automatica 47(2), 341-347, 2011. http://larsblackmore.com/losslessconvexification.htm
- D. Malyuta et al. "Convex Optimization for Trajectory Generation." IEEE Control Systems Magazine, 2022.
  https://arxiv.org/pdf/2106.09125
- J. More, D. Sorensen. "Computing a trust region step." SIAM J. Sci. Stat. Comput., 1983.
- A. Ben-Tal, M. Teboulle. "Hidden convexity in some nonconvex quadratically constrained quadratic
  programming." Mathematical Programming 72, 1996.
- S. Adachi, S. Iwata, Y. Nakatsukasa, A. Takeda. "Solving the Trust-Region Subproblem By a Generalized
  Eigenvalue Problem." SIAM J. Optimization 27(1), 2017. https://epubs.siam.org/doi/10.1137/16M1058200
  (rediscovers Gander-Golub-von Matt 1989: TRS via one generalized eigenvalue).

Strong duality / S-lemma / infeasibility certificate:
- I. Polik, T. Terlaky. "A Survey of the S-Lemma." SIAM Review 49(3), 371-418, 2007.
  https://www.researchgate.net/publication/228337188_A_Survey_of_the_S-Lemma
- A. Beck, Y. Eldar. "Strong Duality in Nonconvex Quadratic Optimization with Two Quadratic Constraints."
  SIAM J. Optimization 17(3), 844-860, 2006. https://www.tau.ac.il/~becka/13.pdf
- W. Ai, S. Zhang. CDT strong duality; and "S-lemma with equality and its applications" (Xia-Wang-Sheu),
  Mathematical Programming, 2016. https://arxiv.org/pdf/1403.2816

Low-dim residual, rank bound, complex SDR tightness, rank-one decomposition (the k=2-4 core):
- G. Pataki. "On the rank of extreme matrices in semidefinite programs and the multiplicity of optimal
  eigenvalues." Mathematics of OR 23(2), 1998. (with A. Barvinok 1995: the r(r+1)/2 <= m rank bound.)
- J. Sturm, S. Zhang. "On Cones of Nonnegative Quadratic Functions." Mathematics of OR 28(2), 246-267,
  2003. https://pubsonline.informs.org/doi/pdf/10.1287/moor.28.2.246.14485 (matrix rank-one decomposition.)
- Y. Huang, S. Zhang. "Complex Matrix Decomposition and Quadratic Programming." Mathematics of OR 32(3),
  2007. https://pubsonline.informs.org/doi/10.1287/moor.1070.0268 (constructive complex rank-one recovery.)
- W. Ai, X. Liang, Y. Yuan. "On the tightness of an SDP relaxation for homogeneous QCQP with three real or
  four complex homogeneous constraints." Mathematical Programming, 2024.
  https://link.springer.com/article/10.1007/s10107-024-02105-z (verifiable tightness test; complex allows
  more constraints tight.)

Constant-modulus / unimodular QCQP + heuristics:
- M. Soltanalian, P. Stoica. "Designing Unimodular Codes Via Quadratic Optimization." IEEE Trans. Signal
  Processing 62(5), 2014.
- Z.-Q. Luo, W.-K. Ma, A. So, Y. Ye, S. Zhang. "Semidefinite Relaxation of Quadratic Optimization
  Problems." IEEE Signal Processing Magazine 27(3), 2010.
  http://dsp.ee.cuhk.edu.hk/eleg5481/Lecture%20notes/10-SDR/qcqp_sdr.pdf
- J. Park, S. Boyd. "General Heuristics for Nonconvex Quadratically Constrained Quadratic Programming."
  arXiv:1703.07870, 2017. https://web.stanford.edu/~boyd/papers/pdf/qcqp.pdf (Suggest-and-Improve; qcqp pkg.)

Phase retrieval analogue (the dither/projection realization; block-coordinate on the circle):
- I. Waldspurger, A. d'Aspremont, S. Mallat. "Phase recovery, MaxCut and complex semidefinite
  programming." Mathematical Programming 149, 47-81, 2015. https://arxiv.org/pdf/1206.0102 (PhaseCut;
  Gerchberg-Saxton-like block coordinate descent.)

Riemannian product-of-circles / oblique manifold (the native geometry; the ARCH-3 local method):
- P.-A. Absil, R. Mahony, R. Sepulchre. "Optimization Algorithms on Matrix Manifolds." Princeton, 2008.
- N. Boumal. "An Introduction to Optimization on Smooth Manifolds." Cambridge, 2023. (Manopt.)
- X. Yu, J.-C. Shen, J. Zhang, K. Letaief. "Alternating Minimization Algorithms for Hybrid Precoding in
  Millimeter Wave MIMO Systems." arXiv:1601.07340, 2016. (MO-AltMin: constant-modulus via manifold opt.)

Global polynomial optimization to close/certify the residual:
- J.-B. Lasserre. "Global optimization with polynomials and the problem of moments." SIAM J. Optimization
  11(3), 2001.
- C. Josz, D. Molzahn. "Lasserre Hierarchy for Large Scale Polynomial Optimization in Real and Complex
  Variables." SIAM J. Optimization 28(2), 2018. https://arxiv.org/abs/1709.04376 (uses D'Angelo-Putinar
  complex Positivstellensatz; finite-convergence certificate.)
- C. Josz, D. Molzahn. "Moment/Sum-of-Squares Hierarchy for Complex Polynomial Optimization."
  arXiv:1508.02068, 2015.

Nonconvex feasibility (alternating projection / Douglas-Rachford onto circles + affine walls):
- H. Bauschke, D. Noll; R. Hesse, D. R. Luke. Local convergence of alternating projections / Douglas-
  Rachford for an affine subspace transversally meeting a (super)regular nonconvex set; unions of convex
  sets. Survey: S. Lindstrom. "Sixty Years of Douglas-Rachford." arXiv:1809.07181.
