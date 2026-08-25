# Agent D01 shard: Lossless Convexification (LCvx) and fixed-thrust / minimum-fuel trajectory optimization

Agent id: D01
Territory (research topic): Lossless convexification of optimal-control problems with non-convex control
constraints (fixed-thrust / min-fuel powered-descent lineage), its exactness theorem and boundary
conditions, and the 2024-2026 discrete-time LCvx losslessness caveats. This is the theoretical backbone
for WHY the single-jump angle-solve is closed-form and WHY the residual (degenerate) set is small.

Method: literature verification only (WebSearch / WebFetch). Every citation below was checked for
author, venue, year, and the specific quoted result against arXiv HTML/abstract pages, ScienceDirect,
ADS, and the ACM DL. No paper, author, or result is invented. No new measurement of the shipped code was
run by D01; all "measured-against-our-model" numbers are CITED from Stage 0 (`stage0-copt/FINDINGS.md`)
and SPEC section 4, not re-measured here, and are tagged as such.

Sources verified (all real, checkable):
- Acikmese & Blackmore, "Lossless convexification of a class of optimal control problems with non-convex
  control constraints," Automatica 47(2):341-347, Feb 2011. (ACC-2010 precursor at larsblackmore.com.)
- Acikmese & Ploen, "Convex Programming Approach to Powered Descent Guidance for Mars Landing," J.
  Guidance, Control, and Dynamics 30(5):1353-1366, 2007. DOI 10.2514/1.27553.
- Blackmore, Acikmese & Scharf, "Minimum-Landing-Error Powered-Descent Guidance for Mars Landing Using
  Convex Optimization," JGCD 33(4):1161-1171, 2010.
- Harris & Acikmese, "Lossless convexification of non-convex optimal control problems for state
  constrained linear systems," Automatica 50(9):2304-2311, 2014. DOI 10.1016/j.automatica.2014.06.008.
- Kunhippurayil, Harris & Jansson, "Lossless convexification of optimal control problems with annular
  control constraints," Automatica 133:109848, Nov 2021.
- Luo, Echigo & Acikmese, "Revisiting Lossless Convexification: Theoretical Guarantees for Discrete-time
  Optimal Control Problems," arXiv:2410.09748 (2024); accepted Automatica 2025 (ScienceDirect
  S0005109825004327).
- Luo, Spada & Acikmese, "Discrete-Time Lossless Convexification for Pointing Constraints"
  (a.k.a. "Discrete lossless convexification for pointing constraints"), arXiv:2501.06931 (2025).
- Malyuta, Reynolds, Szmuk, Lew, Bonalli, Pavone & Acikmese, "Convex Optimization for Trajectory
  Generation," IEEE Control Systems Magazine 42(5):40-113, 2022; arXiv:2106.09125 (survey; LCvx chapter).

---

## Section 1: FOUNDATIONAL LCvx (ESTABLISHED literature; theoretical)

### D01-1: The control-constrained LCvx exactness theorem (Acikmese-Blackmore 2011) is the backbone of single-jump closed-form
- LOCATION: Acikmese & Blackmore, Automatica 47(2):341-347, 2011; Acikmese & Ploen JGCD 2007
  (the min-fuel Mars-landing convexification it generalizes).
- CLAIM: For a finite-horizon linear-dynamics optimal-control problem with a convex cost and a NON-CONVEX
  control-magnitude constraint of the form `rho_min <= |u(t)| <= rho_max` (a thrust that cannot throttle
  below a floor), the convex relaxation obtained by lifting the lower bound to a slack `|u(t)| <= sigma(t),
  sigma(t) <= rho_max` has an optimal solution that is GLOBALLY optimal for the original non-convex
  problem: the relaxed optimum lands on the non-convex boundary (`|u| = sigma`, and `sigma` on its
  bound) almost everywhere. There is ZERO relaxation gap.
- EVIDENCE: The paper's main theorem (verified via larsblackmore.com/losslessconvexification.htm and the
  ACC10 PDF) proves the relaxation is lossless under (i) controllability of the linear pair, (ii)
  NORMALITY of the extremal (no abnormal Pontryagin extremal, guaranteed when the costate cannot vanish
  on an interval), and (iii) the pointwise condition that the objective/costate pull `-B^T lambda(t)` is
  nonzero, which forces the thrust to an extreme point of the control set. THEORETICAL result.
- IMPACT: correctness / simplicity. This is the named theorem behind SPEC 4.2's `u_t = m_t g_t / |g_t|`
  and behind Stage 0's rank-1 SDR on single/easy jumps: when the costate does not vanish, the per-tick
  input is pinned to the boundary in closed form. It is WHY the fast path exists.
- PROPOSAL: cite this as the authority for capability C1's single-jump exactness in the SPEC and any
  Stage E writeup; do NOT re-derive it, reference it.
- CONFIDENCE: 0.95
- DEPENDS-ON: none.

### D01-2: Our |u_t| = m_t is the degenerate-annulus (rho_min = rho_max) limit of annular LCvx
- LOCATION: Kunhippurayil, Harris & Jansson, Automatica 133:109848, 2021 ("annular control constraints");
  Acikmese-Blackmore 2011 (annulus with a hole).
- CLAIM: Our per-tick constraint `|u_t| = m_t` (constant modulus, a CIRCLE) is the `rho_min -> rho_max`
  limit of the annular constraint `rho_min <= |u| <= rho_max`; it is the thinnest, most stringent annulus,
  where the only feasible controls are on the boundary circle and every interior point is an LCvx
  violation. The annular-constraint LCvx paper gives sufficient conditions for a fixed-time problem with
  exactly this class to be solvable as a SINGLE convex program.
- EVIDENCE: The 2021 annular paper (verified via ACM DL 10.1016/j.automatica.2021.109848 and the
  ScienceDirect abstract) removes earlier controllability/terminal-gradient assumptions and establishes
  losslessness for annular (fixed magnitude range) control, "representative of a rocket landing problem."
  Our constant-modulus is its degenerate case. THEORETICAL correspondence (the identification of our model
  with this constraint class), not a new measurement.
- IMPACT: simplicity / correctness. Confirms the SPEC-4 framing "fixed-thrust structure of LCvx" is the
  right named class, and that the constant-modulus disk relaxation `|u_t| <= m_t` (already
  RelaxationRecovery's kernel) is exactly the published LCvx relaxation.
- PROPOSAL: adopt "constant-modulus = degenerate-annulus LCvx" as the canonical description; the disk
  SOCP `|u_t| <= m_t` is the correct convex relaxation to solve for the bound and costates.
- CONFIDENCE: 0.9
- DEPENDS-ON: D01-1.

### D01-3: The state-constrained LCvx exactness (Harris-Acikmese 2014) requires state constraints active only at ISOLATED instants
- LOCATION: Harris & Acikmese, Automatica 50(9):2304-2311, 2014.
- CLAIM: Extending LCvx to problems that ALSO carry convex STATE constraints (linear position walls in our
  language), losslessness is preserved only when the active state constraints touch on a DISCRETE set of
  time instants (isolated points, a measure-zero set), via "strongly controllable subspaces" and an
  appropriate maximum principle. State constraints active over a positive-measure time INTERVAL can
  destroy losslessness (the thrust is pushed interior on the active arc).
- EVIDENCE: verified via the Automatica abstract (Dialnet, ScienceDirect S0005109814002362) and the
  survey's LCvx chapter: "lossless convexification can be guaranteed ... when state constraints are
  activated at a discrete set of times ... a set of isolated points is a discrete set with measure zero
  ... the relaxed solution is optimal almost everywhere." THEORETICAL. This is the precise boundary
  condition SPEC 4.1 invokes.
- IMPACT: correctness. Names the EXACT reason single/easy jumps are lossless (walls touch at isolated
  ticks) and coupled multi-jump is not (opposing corridors are active over intervals; see D01-6).
- PROPOSAL: use this as the formal statement of the LCvx boundary in the SPEC; it is the theorem that our
  coupled-corridor case sits on the wrong side of.
- CONFIDENCE: 0.9
- DEPENDS-ON: D01-1.

---

## Section 2: DISCRETE-TIME LCvx CAVEATS (ESTABLISHED 2024-2026 literature; theoretical) - the load-bearing bound

### D01-4: Continuous-time LCvx exactness does NOT transfer to discrete time; the loss is bounded to n_x - 1 grid points (Luo-Echigo-Acikmese 2024)
- LOCATION: Luo, Echigo & Acikmese, arXiv:2410.09748 (2024), Automatica 2025. Theorem 4.28 (normal case),
  Theorem 5.39 (long-horizon case).
- CLAIM: After discretization, the equivalence between the convex relaxation and the non-convex problem
  holds ONLY approximately: the relaxed optimal control satisfies the original non-convex control
  constraint (lands on the boundary) at ALL grid points EXCEPT at most `n_x - 1`, where `n_x` is the STATE
  dimension. This bound is INDEPENDENT of horizon length `N` and of control dimension `n_u`; it depends
  solely on `n_x`. The guarantee is generic: it holds with probability one under an arbitrarily small
  random perturbation of the dynamics matrix (a transversality assumption), given controllability and
  normality.
- EVIDENCE: verified via arXiv:2410.09748 HTML/abstract. Theorem 4.28: "the probability of the optimal
  trajectory violating the nonconvex constraints at more than `n_x - 1` grid points is zero." Long-horizon
  (Thm 5.39) needs a bisection split at a transition time `t_s*`, after which phase two satisfies the
  normal-case bound. Assumptions: controllability of {A,B}, normality, Slater, small-perturbation
  genericity. THEORETICAL.
- IMPACT: correctness / simplicity, HIGH. This is the literature bound the mission asked for. It says the
  set of modulus-throttled (degenerate) ticks is capped by a fixed DIMENSIONAL count, not by route length.
  It is the theoretical companion to the Pataki active-constraint bound (D01-8) and to COPT's "branch only
  on the handful of nonconvex ticks that matter."
- PROPOSAL: cite `n_x - 1` as the horizon-independent cap on the residual (degenerate) dimension; it
  justifies ARCH-1's "low-dim residual" as bounded a priori, not just measured 0-4.
- CONFIDENCE: 0.85 (the theorem is solid; its DIRECT applicability to our exact structured friction
  dynamics is D01-7, lower confidence).
- DEPENDS-ON: D01-1, D01-3.

### D01-5: The annular / dual-mode (control null OR on the circle) discrete bound is 2 n_x - 2, and it covers BOTH our gate AND our dF (Luo-Spada-Acikmese 2025)
- LOCATION: Luo, Spada & Acikmese, arXiv:2501.06931 (2025). Theorem 3 (pointwise sufficient condition),
  Theorem 8 (horizon bounds), Problems P3 (pointing) and P4 (annular dual-mode).
- CLAIM: (a) Pointwise, discrete LCvx is VALID at grid point `i` iff `-B^T eta_i* != lambda * xi` for all
  `lambda in R` (`eta_i*` = the relaxed problem's costate/dual, `xi` = the constraint normal), i.e. LCvx
  fails at a tick exactly when the projected costate ALIGNS with the constraint direction (the discrete
  analogue of the vanishing-costate condition). (b) Over the horizon, violations occur at no more than
  `n_x - 1` grid points for a pure pointing constraint (P3), and at no more than `2 n_x - 2` grid points
  for the ANNULAR DUAL-MODE constraint P4, where the control is EITHER null (`theta_i = 0`) OR on the
  annulus with a sector/pointing condition (`theta_i rho_min <= |u_i| <= theta_i rho_max`,
  `xi^T u_i >= gamma |u_i|`, `theta_i in {0,1}`). P4 is a mixed-integer problem whose convex relaxation is
  provably tight at all but `<= 2 n_x - 2` grid points.
- EVIDENCE: verified via arXiv:2501.06931 HTML. Theorem 8 gives `n_x - 1` (P3) and `2 n_x - 2` (P4). The
  P4 formulation ("control inactive or in an annular sector," a sector-shaped feasible region in 2D control
  space) is a near-exact structural template for OUR inertia gate (axis zeroed OR fresh acceleration only)
  and shows a facing/sector (dF) constraint fits the SAME LCvx frame. THEORETICAL.
- IMPACT: correctness / simplicity, HIGH. This is the single most on-target citation: it is discrete-time,
  it is 2D control, it handles BOTH the on/off gate AND a pointing/sector (dF-like) constraint, and it
  gives an explicit horizon-independent violation bound. It says dF and the gate degrade the residual bound
  only from `n_x - 1` to `2 n_x - 2`, they do not break LCvx.
- PROPOSAL: model our inertia gate as the P4 annular dual-mode and dF as the sector condition; take
  `2 n_x - 2` as the residual cap when the gate/dF are live. Point RelaxationRecovery (which currently
  BAILS on any facing wall, SPEC F8) at this formulation so it stops bailing.
- CONFIDENCE: 0.85
- DEPENDS-ON: D01-4.

---

## Section 3: APPLICABILITY TO OUR MODEL (map the theory onto the angle-solve; theoretical mapping + cited measurements)

### D01-6: Our opposing corridors are the interval-active state-constraint case that continuous-time LCvx loses; but discrete time CAPS the loss
- LOCATION: research mapping of D01-3/D01-4 onto SPEC 4.1 and Stage 0 FINDINGS 1a/1b.
- CLAIM: SPEC 4.1's premise is correct in continuous time: our walls (opposing-pair corridors) are active
  over time INTERVALS, the exact case Harris-Acikmese 2014 shows can lose losslessness, which is why the
  disk relaxation throttles `|u| < m` at those redirect/standing-start ticks. BUT the discrete-time result
  (D01-4/D01-5) SHARPENS this: the loss is not unbounded, it is capped at `n_x - 1` (or `2 n_x - 2` with
  gate/dF) grid points, INDEPENDENT of how many corridors are interval-active and independent of horizon
  length. So interval-active walls do not blow up the residual; they cap it at a state-dimension count.
- EVIDENCE: CITED (not re-measured by D01): Stage 0 measured throttled ticks = 0 on single/easy
  (j005/j016/j019/j022, f2f-no-dF), 1 dominant on j021 (t12, slack 0.083) and loopmm (t0, 0.095), and 4 on
  j008b at a 1e-6 threshold of which 1 is dominant (t1, 0.101) and 3 are ~1e-5 (near-boundary numerical).
  With the horizontal state `(x, z, vx, vz)`, `n_x = 4`, so `n_x - 1 = 3` (pure constant-modulus) and
  `2 n_x - 2 = 6` (gate live, e.g. loopmm/j008b). Every measured dominant count (0-1) is well within
  `n_x - 1 = 3`; the loose-threshold j008b count 4 sits within the gate bound 6. This is CONSISTENT with
  the LCvx discrete bound; it is corroboration, not proof (see D01-7).
- IMPACT: correctness. Directly answers mission question 3: the LCvx literature gives the bound as a
  function of STATE DIMENSION (`n_x - 1`, or `2 n_x - 2` with gate/dF), NOT as a function of the number of
  interval-active constraints. That is a stronger and more useful statement than the Pataki active-wall
  bound for our fixed small `n_x`.
- PROPOSAL: state in the SPEC that the residual (degenerate) tick count is bounded by `min(n_x - 1 or
  2 n_x - 2, Pataki r(r+1)/2 <= #active walls)`; use the smaller. For our `n_x = 4`, the LCvx cap (3, or 6
  with gate) is the operative a-priori bound on the residual solve size.
- CONFIDENCE: 0.75 (mapping is sound; the exact `n_x` count for the friction dynamics is D01-7).
- DEPENDS-ON: D01-3, D01-4, D01-5, D01-8.

### D01-7: The n_x-1 bound requires genericity + controllability + normality we have NOT verified for the MC friction dynamics (honest caveat)
- LOCATION: research caveat on D01-4/D01-6 (assumptions of Luo-Echigo-Acikmese Thm 4.28).
- CLAIM: The `n_x - 1` bound is a GENERIC guarantee: it holds with probability one under an arbitrarily
  small random perturbation of the dynamics matrix, given controllability of `{A,B}` and normality
  (no abnormal extremal). Our dynamics are a FIXED, structured, per-axis friction chain
  (`f4` prefix products) with possible degeneracies (repeated `f4`, per-axis decoupling of X and Z coupled
  only through the modulus). We have NOT verified that our specific `{A,B}` is controllable, normal, and
  generic in the theorem's sense, so `n_x - 1` is a THEORETICAL guide, not a proven bound for our exact
  system. A non-generic structure could in principle exceed it.
- EVIDENCE: the theorem's own perturbation/genericity hypothesis (arXiv:2410.09748, Def 4.22, Assump 4.19,
  Assump 3), reconciled against our deterministic friction model (SPEC 4.1, JumpLinearModel). The measured
  0-4 (Stage 0, CITED) is consistent with the bound but does not prove genericity. THEORETICAL caveat.
- IMPACT: correctness / rigor. Prevents over-claiming a proven cap. The honest statement is: measured
  residual 0-4, cross-validated by COPT, CONSISTENT with a dimensional LCvx cap of 3 (or 6 with gate); a
  proof for our system would require checking controllability + normality + genericity of the friction
  `{A,B}`.
- PROPOSAL: route "verify controllability/normality/genericity of the friction `{A,B}`, or empirically
  confirm the residual never exceeds `n_x - 1` across the whole corpus" to Stage E as a measurement (sweep
  the SOCP-disk throttled-tick count on all 54+ captures; if it never exceeds 3, or 6 with gate, the bound
  holds empirically for our corpus).
- CONFIDENCE: 0.8 (that the caveat is real); the empirical sweep would raise or settle it.
- DEPENDS-ON: D01-4, D01-6.

### D01-8: LCvx dimensional bound vs Pataki active-wall bound: complementary, take the min
- LOCATION: research reconciliation of D01-4 with SPEC 4.2 (Pataki/Barvinok `r(r+1)/2 <= #active walls`).
- CLAIM: Two independent bounds cap the residual (degenerate / vanishing-costate) tick count: (a) the LCvx
  discrete bound `n_x - 1` (or `2 n_x - 2` with gate/dF), a STATE-DIMENSION bound independent of the number
  of walls; (b) the Pataki SDR rank bound `r(r+1)/2 <= m` (m = active walls), an ACTIVE-CONSTRAINT bound.
  They are derived from different structures (costate dimension vs SDR extreme-point rank) and are
  complementary; the operative cap is their minimum. For our small `n_x = 4` and small active-wall counts,
  both give single-digit caps, agreeing with the measured 0-4 and the rank-2/3 SDR.
- EVIDENCE: SPEC 4.2 (Pataki, measured SDR rank 2-3 on coupled, rank-1 on easy) and D01-4 (`n_x - 1`),
  CITED. The pointwise LCvx failure condition `-B^T eta_i* = lambda xi` (D01-5, Thm 3) is the discrete
  restatement of SPEC 4.2's vanishing costate `g_t = 0`; the two frameworks describe the SAME degenerate
  set from dual (LCvx) and lifted-primal (SDR/Pataki) sides. THEORETICAL reconciliation.
- IMPACT: simplicity / correctness. Unifies the two bounds the campaign already uses into one statement,
  and confirms the residual-solve size is a-priori small from BOTH directions.
- PROPOSAL: SPEC 4.2's crisp statement should carry both caps: residual dimension
  `<= min(n_x - 1 [or 2 n_x - 2 with gate/dF], Pataki r(r+1)/2 <= #active walls)`.
- CONFIDENCE: 0.8
- DEPENDS-ON: D01-4, D01-5.

---

## Section 4: PORT FEASIBILITY

### D01-9: LCvx contributes THEORY not CODE; nothing new to port, the disk-SOCP relaxation already exists
- LOCATION: research-to-code mapping; SPEC 4.4 (RelaxationRecovery = the disk AL-FISTA kernel), A03/A04.
- CLAIM: LCvx as a METHOD is just "solve the convex relaxation"; the convex relaxation for our problem is
  the disk SOCP `|u_t| <= m_t` with the linear walls, which the shipped `RelaxationRecovery`
  (augmented-Lagrangian FISTA) already IS. So there is no LCvx algorithm to port. The VALUE of LCvx here
  is entirely the theory: (i) the certificate that non-interval-active cases are closed-form exact
  (D01-1/D01-3), and (ii) the horizon-independent bound `n_x - 1` / `2 n_x - 2` on the residual dimension
  (D01-4/D01-5), which tells Stage D/E that the residual solve only ever branches on a handful of ticks.
- EVIDENCE: SPEC 4.4 notes the disk SOCP is RelaxationRecovery's kernel and that AL-FISTA does NOT
  converge at `n ~ 353` (j001, viol 15.5; CITED A03-14), so the OPEN engineering question is a
  better-converging convex kernel (interior-point SOCP), NOT LCvx itself. THEORETICAL + CITED.
- IMPACT: simplicity. Sets expectations: LCvx bounds the residual, it does not solve it; the residual
  method is a separate Stage D deliverable (SDR rank-reduction / tiny B&B / enumeration / manifold).
- PROPOSAL: do NOT add an "LCvx module." Instead (a) keep/upgrade the disk-SOCP kernel to converge (the
  Stage D/E convex-bound question), and (b) implement the bounded residual solve over the `<= n_x - 1`
  (or `<= 2 n_x - 2`) degenerate ticks identified by the pointwise condition `-B^T eta* = lambda xi`.
  Pure-Java feasible because the residual is tiny and fixed-dimension.
- CONFIDENCE: 0.85
- DEPENDS-ON: D01-4, D01-5.

---

## Section 5: VERDICT

### D01-10: What LCvx BUYS and where it FAILS for us (the bottom line)
- LOCATION: synthesis of D01-1..D01-9.
- CLAIM: LCvx BUYS three things, all theoretical and all already corroborated by Stage 0 measurements:
  (1) the NAMED THEOREM (Acikmese-Blackmore 2011; Kunhippurayil-Harris-Jansson 2021 annular) that our
  single-jump / non-interval-active instances are LOSSLESS, i.e. closed-form exact via `u_t = m_t g_t /
  |g_t|` whenever the costate does not vanish, matching Stage 0's rank-1 SDR and disk == sphere on
  j005/j016/j019/j022/f2f;
  (2) a HORIZON-INDEPENDENT, STATE-DIMENSION bound (Luo-Echigo-Acikmese 2024: `n_x - 1`; Luo-Spada-Acikmese
  2025: `2 n_x - 2` with an on/off gate or a pointing/dF sector) on the number of modulus-violating
  (degenerate) ticks, i.e. the residual dimension, which for our `n_x = 4` is `<= 3` (pure) or `<= 6`
  (gate/dF), consistent with the measured 0-4 and complementary to the Pataki active-wall bound;
  (3) the pointwise degenerate-tick TEST `-B^T eta_i* = lambda xi` (Luo-Spada Thm 3), the discrete
  restatement of SPEC 4.2's `g_t = 0`, which tells the solver exactly WHICH ticks form the residual.
  LCvx FAILS / stops SHORT in three ways, all honestly bounded:
  (a) EXACTNESS is lost precisely at INTERVAL-ACTIVE opposing corridors (Harris-Acikmese 2014), our
  coupled-multi-jump case, where the disk throttles `|u| < m` (Stage 0: 1.6e-3 b loose on j021);
  (b) LCvx bounds the residual but does NOT recover it: solving the convex relaxation leaves the `<= n_x-1`
  degenerate ticks interior; fixing them is a separate (small, fixed-dim) nonconvex residual solve, the
  Stage D deliverable, not something LCvx provides;
  (c) the `n_x - 1` bound rests on controllability + normality + genericity we have NOT proven for the MC
  friction `{A,B}` (D01-7), so for us it is a measured-consistent guide, not a proven cap.
- EVIDENCE: the eight verified citations plus CITED Stage 0 numbers (rank-1 vs rank 2-3, throttled 0-4,
  disk loose 1.6e-3 b). THEORETICAL verdict backed by CITED measurement.
- IMPACT: correctness / simplicity, HIGH. LCvx is confirmed as the correct theoretical backbone for the
  fast path AND supplies the a-priori residual-size cap that makes ARCH-1 ("convex dual + low-dim
  residual") a principled architecture rather than an empirical bet. It does not, by itself, close the
  coupled-multi-jump residual; that is routed to Stage D residual methods and Stage E prototypes.
- PROPOSAL: adopt in the SPEC: (i) LCvx (Acikmese-Blackmore / annular) as the named authority for C1
  single-jump exactness; (ii) the discrete bound `min(n_x - 1 [or 2 n_x - 2 with gate/dF], Pataki)` as the
  a-priori residual cap; (iii) the pointwise `-B^T eta* = lambda xi` test to IDENTIFY residual ticks;
  (iv) an honest note that recovering the residual and proving genericity are open (Stage D/E). Route the
  empirical corpus-wide throttled-tick sweep (D01-7) and the P4 annular-gate/dF modeling (D01-5) to Stage E.
- CONFIDENCE: 0.85
- DEPENDS-ON: D01-1, D01-3, D01-4, D01-5, D01-6, D01-7, D01-8, D01-9.

---

## Appendix: ESTABLISHED vs SPECULATION ledger

ESTABLISHED (peer-reviewed / arXiv, verified authors+venue+result):
- D01-1 (Acikmese-Blackmore 2011 exactness), D01-2 (annular LCvx 2021), D01-3 (Harris-Acikmese 2014
  isolated-instant state-constraint condition), D01-4 (`n_x - 1` discrete bound, Luo-Echigo-Acikmese 2024),
  D01-5 (`2 n_x - 2` annular/pointing bound + pointwise test, Luo-Spada-Acikmese 2025). These are the
  literature; their internal correctness is not in question here.

MAPPING / RECONCILIATION (theoretical, sound but our-model-specific, corroborated by CITED Stage 0
measurements, not independently re-measured by D01):
- D01-6 (interval-active corridors = the LCvx boundary, capped by the discrete bound), D01-8 (LCvx vs
  Pataki, take min), D01-9 (nothing to port), D01-10 (verdict).

SPECULATION / OPEN (labeled, routed to measurement):
- D01-7: whether the MC friction `{A,B}` meets the controllability/normality/genericity hypotheses so the
  `n_x - 1` bound is a PROVEN (not just measured-consistent) cap. Test = corpus-wide SOCP-disk
  throttled-tick sweep + a controllability/normality check on `{A,B}` (Stage E).
- The EXACT value of `n_x` for our reduced dynamics (`4` for `(x,z,vx,vz)`; could reduce further under the
  per-axis friction decoupling, making the effective per-axis `n_x` smaller and the bound tighter). Test =
  read off the SDR null-space dimension vs the throttled-tick count on the coupled captures (Stage E).
