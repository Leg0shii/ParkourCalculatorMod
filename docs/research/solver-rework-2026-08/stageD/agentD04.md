# Stage D shard: agent D04

METHOD FAMILY: constant-modulus / unit-modulus quadratic programming (UQP / CMQP). Power-method-like
iterations and monotone majorization-minimization (MM) for optimizing over a product of circles.

AGENT: D04.
TERRITORY: whether a projected-power-method or MM iteration is a viable pure-Java local solver for our
class (linear functional over a product of circles with linear walls), where it is global vs local, and
how it treats the linear side constraints and the degenerate (vanishing-costate) ticks.

FILES / COMMANDS INSPECTED:
- docs/research/solver-rework-2026-08/00-context-pack.md (read whole).
- docs/research/solver-rework-2026-08/SPEC.md sections 1-6 (our class + the section-4.2 reduction).
- core/.../anglesolver/solver/JumpLinearModel.java (Grep): mMag/baseArg definition (lines 38-39, 137-138),
  zeroingPattern rotation lines 345-347 `vx += mMag[t]*cos(baseArg[t]+yaw*RAD)`, and recoverFacing
  line 364 `wrap((atan2(gz,gx) - baseArg[t]) * DEG)`.
- WebSearch / WebFetch: real sources cited inline per finding (URLs at bottom). No paper invented; every
  numeric iteration/convergence claim is quoted from a fetched source or labeled UNMEASURED-HYPOTHESIS.

NOTE ON RIGOR: this is a literature/applicability shard. Claims about the PUBLISHED methods are tagged
ESTABLISHED with a citation. Claims about how those methods would behave ON OUR MODEL are theoretical
deductions from the cited method plus the SPEC's already-measured structure; each such claim is tagged
SPECULATION / UNMEASURED-HYPOTHESIS and names the Stage E experiment that would settle it. I ran no new
solver benchmark; the measured numbers I lean on are the SPEC's and Stage 0's, cited as such.

---

## D04-1. The UQP / MERIT foundation (constant-modulus quadratic optimization)

TITLE: The canonical constant-modulus optimization problem and its reference method.
LOCATION: research topic; Soltanalian & Stoica, IEEE TSP 2014.
CLAIM: The reference formulation of our nonconvexity is UQP, max u^H R u subject to |u_t| = 1 for all t
(equivalently over a product of circles), NP-hard in general; the standard solver is a power-method-like
local iteration wrapped by MERIT for a case-dependent global/near-global certificate.
EVIDENCE: ESTABLISHED. M. Soltanalian and P. Stoica, "Designing Unimodular Codes Via Quadratic
Optimization," IEEE Trans. Signal Processing 62(5):1221-1234, 2014 (IEEE Xplore doc 6698378; ADS
2014ITSP...62.1221S). Fetched abstract: "power method-like iterations are introduced for local
optimization of UQP"; "a monotonically error-bound improving technique (MERIT) is proposed to obtain the
global optimum or a local optimum of UQP with good sub-optimality guarantees ... generally outperform the
pi/4 approximation guarantee of semi-definite relaxation." IEEE SPS Young Author Best Paper Award.
IMPACT: correctness/framing. This is the exact literature the SPEC (section 4.1, 4.6) names as the class
our objective is MILDER than; establishes that "constant-modulus" here means UQP, not the number-theory
sense of "unimodular matrix."
PROPOSAL: use as the anchor for the family; but note (D04-5) our objective is the DEGENERATE R=0 case.
CONFIDENCE: 0.98.
DEPENDS-ON: none.

## D04-2. The power-method-like iteration: exact form, monotonicity, locality

TITLE: u_{k+1} = e^{j arg(R u_k)}; monotone under diagonal loading; LOCAL optimum only.
LOCATION: research topic; Soltanalian-Stoica 2014 and the companion arXiv:1303.0152.
CLAIM: The power-method-like iteration for max u^H R u over |u_t|=1 is u_{k+1} = e^{j arg(R u_k)}
(apply R, then project each entry back to the unit circle by keeping only its phase); the objective is
monotonically non-decreasing when R is positive semidefinite, enforced by diagonal loading
R~ = R + lambda I with lambda >= -lambda_min(R); it converges to a LOCAL optimum in general, at O(n^2)
per iteration (one dense mat-vec).
EVIDENCE: ESTABLISHED. WebFetch of arXiv:1303.0152 ("Designing Unimodular Codes via Quadratic
Optimization is not Always Hard," Soltanalian-Stoica): iteration "u_{k+1} = e^{j.arg(R u_k)}"; "reaches
local optima, not global optima in general"; per-iteration cost "O(n^2) for matrix-vector multiplication
... plus phase extraction." WebSearch corroboration: update "s^(t+1) = e^(j.arg(G s^(t))) ... leads to a
monotonically increasing objective when G is positive semidefinite"; diagonal loading
"G~ <- G + lambda_m I with lambda_m >= -lambda_min(G)." The term matching u^H R u increases monotonically;
the constant-modulus power iteration is convergent.
IMPACT: robustness/speed. This is the whole family in one line: a mat-vec plus a per-entry atan2. It is a
FIRST-ORDER local method; it carries NO global guarantee by itself (MERIT adds an SDP-based bound on top).
PROPOSAL: treat as a candidate LOCAL polish only; globality must come from elsewhere (D04-8).
CONFIDENCE: 0.95 (formula and monotonicity directly quoted; "in general" locality is the authors' own).
DEPENDS-ON: D04-1.

## D04-3. Monotone MM is the same family with an auxiliary-function proof

TITLE: MM / majorization-minimization for constant-modulus sequence design coincides with the power
iteration and shares the monotone-but-local property.
LOCATION: research topic; Stoica-He-Li 2009 (CAN/WeCAN); Song-Babu-Palomar 2015-2016; Sun-Babu-Palomar
2017 survey.
CLAIM: The constant-modulus sequence-design line (minimize integrated/peak sidelobe over |x_n|=1) solves
the same product-of-circles problem by MM: majorize the objective by a surrogate whose per-iterate
minimizer is a per-entry phase alignment, giving a monotone descent that (like the power method) reaches a
stationary/local point; the MM survey gives the unified convergence theory.
EVIDENCE: ESTABLISHED. P. Stoica, H. He, J. Li, "New Algorithms for Designing Unimodular Sequences With
Good Correlation Properties," IEEE TSP 57(4):1415-1425, 2009 (CAN/WeCAN; plaza.ufl.edu/haohe/papers/
CAN.pdf). J. Song, P. Babu, D.P. Palomar, "Optimization Methods for Designing Sequences With Low
Autocorrelation Sidelobes" (arXiv:1501.02252) and "Sequence Set Design ... via Majorization-Minimization"
(arXiv:1510.01899): "algorithms ... fall into the general framework of majorization-minimization (MM) ...
share the monotonic property." Y. Sun, P. Babu, D.P. Palomar, "Majorization-Minimization Algorithms in
Signal Processing, Communications, and Machine Learning," IEEE TSP, Feb 2017 (unified convergence,
non-smooth objectives); 2020 SPS Young Author Best Paper.
IMPACT: simplicity. Confirms the power method and MM are the SAME family for our purposes: monotone,
cheap, LOCAL. MM's virtue is a clean monotonicity certificate and FFT-accelerated mat-vecs; neither buys
globality.
PROPOSAL: fold MM and power-iteration into one "monotone local circle-descent" candidate in Stage E.
CONFIDENCE: 0.9.
DEPENDS-ON: D04-1, D04-2.

## D04-4. Classical UQP/MM carries NO linear side constraints; side constraints need ADMM / ADPM / manifold splitting

TITLE: The published constant-modulus machinery is UNCONSTRAINED apart from the modulus; linear/side
constraints are bolted on by penalty-splitting, not native.
LOCATION: research topic; 2022-2026 radar/comms waveform literature.
CLAIM: The core UQP power method and the sidelobe-MM methods handle ONLY the unit-modulus constraint; any
additional linear/quadratic side constraint (spectral mask, power, beampattern null, per-user linear
constraint) is added by an outer splitting scheme (ADMM, adaptive-penalty ADPM, penalty-dual-decomposition,
or manifold-ADMM / AltMin), each of which yields a LOCAL optimum.
EVIDENCE: ESTABLISHED. WebFetch of arXiv:1303.0152 and the 2014 TSP paper: "the paper addresses the
unconstrained case primarily. Linear side constraints are not explicitly incorporated." For side
constraints: "Quadratic Optimization for Unimodular Sequence Design via an ADPM Framework" (adaptive
penalty dual method, splits unimodular + correlation constraints into subproblems, "local optimal
solution"); "Waveform Design for Optimal PSL Under Spectral and Unimodular Constraints via Alternating
Minimization" (arXiv:2210.08564); "ADMM-Based Constant Modulus Waveform Design for DFRC," Entropy 25(7):1027,
2023; "Modulus Waveform Design Based on Manifold ADMM," Electronics 13(14):2726, 2024; "ADMM-based transmit
beampattern synthesis ... under a constant modulus constraint," Signal Processing 2020. Riemannian route:
MO-AltMin on the complex-circle manifold transforms constant-modulus + transmit-power constraints into an
unconstrained manifold problem (AltMin for hybrid precoding, arXiv:1601.07340; RMOCG phase-only
beamforming, IEEE 9779535; complex-circle-manifold derivation arXiv:2508.07396).
IMPACT: robustness. Directly answers our sub-question 3(b): the UQP family does NOT natively take our
linear walls. Handling walls means an outer penalty/AL/ADMM loop, i.e., leaving the closed-form regime and
accepting a local solution with a tuning parameter.
PROPOSAL: any wall-carrying power/MM solver for us is a PENALIZED / manifold-AL scheme (D04-6), not the
textbook one-shot iteration.
CONFIDENCE: 0.92.
DEPENDS-ON: D04-1, D04-2.

## D04-5. Our objective is the DEGENERATE R=0 UQP: without walls it is one-step global closed form, already in the code

TITLE: max Re(c^H u) over |u_t|=m_t is the trivial UQP; the power step converges in ONE iteration to the
global optimum, which is exactly JumpLinearModel.recoverFacing.
LOCATION: research topic + core/.../solver/JumpLinearModel.java:345-364.
CLAIM: Our objective is LINEAR (a functional c^T u), i.e. UQP with R = 0 plus a linear term. Maximizing
Re(c_t^H u_t) over |u_t| = m_t is separable per tick and solved in closed form by u_t = m_t c_t / |c_t|
(align each tick's vector to its coefficient's phase). Equivalently the power iteration
u_{1} = e^{j arg(R u_0 + c)} with R = 0 reduces to u_1 = e^{j arg(c)}, a SINGLE step to the GLOBAL
optimum. This is not novel machinery: it is precisely what the shipped code already does at
recoverFacing (line 364), yaw_t = wrap(atan2(g_z,g_x) - baseArg_t), i.e. u_t = m_t g_t/|g_t| with the
costate g_t (= c_t when no wall is active).
EVIDENCE: ESTABLISHED (theory + code). WebSearch: "maximizing the real part of a linear function of
complex vectors with unit modulus ... aligning the phase of each entry independently ... phase equal to
the argument of the corresponding coefficient ... closed form, non-iterative." Code: JumpLinearModel line
346-347 is the constant-modulus map addX=mMag*cos(baseArg+yaw), addZ=mMag*sin(baseArg+yaw); line 364 is the
per-tick arg alignment. SPEC section 4.2 measures this closed form as EXACT on single jumps and easy
multi-jump (0 throttled ticks, SDR rank-1).
IMPACT: correctness/simplicity. Confirms sub-question 3(a): the no-wall case is the TRIVIAL UQP, global
and closed form, and the incumbent already implements it. The power-method/MM family therefore adds
NOTHING to the no-wall path.
PROPOSAL: do not reintroduce an iterative UQP solver for the no-wall case; the one-step align is already
optimal and shipped.
CONFIDENCE: 0.95.
DEPENDS-ON: D04-2.

## D04-6. WITH walls, a penalized power/MM iteration is projected-gradient on the torus: LOCAL, penalty-tuned, and blind to the section-4.2 structure

TITLE: Penalizing wall violation turns our linear objective into an indefinite quadratic UQP whose power
step degrades to a small projected-gradient step; local only.
LOCATION: research topic; applied to our model (SPEC section 4.2).
CLAIM: The natural way to bring our walls a_j^T u <= b_j into the UQP family is a quadratic penalty:
maximize Re(c^H u) - rho * sum_j max(0, a_j^T u - b_j)^2 over |u_t| = m_t. The penalty Hessian is
rho * A^T A (PSD), so the objective is max of an indefinite quadratic (R = -rho A^T A, negative
semidefinite) plus a linear term. MERIT's monotone power step then needs diagonal loading
lambda >= -lambda_min(R) = rho * lambda_max(A^T A); with lambda that large, the step
u_{k+1} = e^{j arg((R+lambda I)u_k + c)} is dominated by the lambda*u_k term and reduces to a SMALL
projected-gradient / Riemannian-gradient step on the product of circles. It converges monotonically but to
a LOCAL optimum, its fixed point depends on rho (needs a penalty-continuation ladder), and it optimizes
the RAW penalized objective with no use of the convex dual / active-set / low-dim-residual structure the
SPEC proved is the actual reduction.
EVIDENCE: SPECULATION / UNMEASURED-HYPOTHESIS. The degradation-to-gradient argument is standard MM/diagonal-
loading algebra (D04-2 loading rule) applied to R = -rho A^T A; the "local, penalty-dependent fixed point"
matches the cited ADMM/ADPM waveform results (D04-4: "local optimal solution"). Not benchmarked here.
STAGE E EXPERIMENT: implement the penalized power/manifold-gradient iteration in the research harness on
j021, j008b, loopmm, thousand/1, and the dF-chain captures; measure (i) byte-exact objective gap vs the
COPT reference optima in stage0 FINDINGS, (ii) iterations and wall-clock to a fixed residual, (iii)
sensitivity to the rho ladder. Compare head-to-head with the incumbent ILS (SPEC: ILS reaches 2.8e-5 b of
COPT on j021).
IMPACT: robustness (likely NEUTRAL-to-NEGATIVE vs incumbent). Predicted to match, not beat, the existing
full-n SLP/ILS local search, because it is the same class of first-order local method on the same coupled
corridors, minus the structure exploitation.
PROPOSAL: do NOT adopt penalized power/MM as the primary wall-carrying solver; if used at all, only as an
anytime LOCAL polish inside ARCH-2, never as the globality mechanism.
CONFIDENCE: 0.75.
DEPENDS-ON: D04-2, D04-4, D04-5.

## D04-7. The vanishing-costate (degenerate) ticks STALL the arg-iteration: the family inherits the incumbent's exact failure

TITLE: At a degenerate tick g_t = 0, arg(g_t) is undefined; the power/MM update leaves the direction
unresolved, reproducing the section-4.3 closed-form-default failure.
LOCATION: research topic; applied to our model (SPEC section 4.2-4.3).
CLAIM: The section-4.2 reduction proves that the hard ticks are exactly those where the costate g_t
vanishes (objective pull cancels active-wall pull). The power/MM update at tick t is u_t <- m_t * (R u +
c + lambda u)_t / |(...)_t|. At a degenerate tick the objective+wall contribution (R u + c)_t is ~0, so the
update direction is governed only by the loading term lambda*u_t: the iterate STAYS where it is (a fixed
point for any direction), and the arg of a near-zero pre-loading vector is numerical noise. So the power/MM
family does NOT solve the 1-4 dimensional nonconvex residual; it stalls on it, which is the SAME behavior
the SPEC measures for the shipped closed-form default (j021 0.34 b, thousand 2.89 b infeasible before the
full-n fallback).
EVIDENCE: SPECULATION / UNMEASURED-HYPOTHESIS, deduced from the update rule (D04-2) and the SPEC's measured
degenerate set (stage0: 1 degenerate tick on j021 with modulus slack 0.083, 4 on j008b; SDR rank 2-3). The
"arg of a vanishing vector is unconstrained" property is intrinsic to e^{j arg(.)}.
STAGE E EXPERIMENT: instrument the penalized power/MM run to log |g_t| and the per-tick direction change
at the SPEC-identified degenerate ticks (t12 on j021, t0 on loopmm); confirm the update magnitude collapses
there and the residual is left unresolved. Then confirm the ARCH-1 residual solve (enumeration / tiny
spatial B&B over just those 1-4 ticks) closes it, which the power/MM step cannot.
IMPACT: correctness (decisive against MM-as-global). This is the core negative result: the family is
structurally unable to resolve the exact ticks that make the problem hard.
PROPOSAL: the degenerate residual must be handled by the SPEC's ARCH-1 low-dim exact solve, not by any
arg-iteration; if a power/MM polish is used, freeze the non-degenerate ticks and never let it drive the
degenerate ones.
CONFIDENCE: 0.75.
DEPENDS-ON: D04-2, D04-6.

## D04-8. Globality: MERIT's guarantee is for the QUADRATIC objective; our globality comes from the Pataki low-dim residual, not from the UQP family

TITLE: The power/MM family is local on our class; the small-problem globality the SPEC wants is delivered
by SDR/spatial-B&B on the residual, not by MERIT.
LOCATION: research topic; SPEC section 4.2/4.4.
CLAIM: MERIT attains global-or-certified-local by pairing the power iteration with an SDP bound and a
case-dependent suboptimality certificate for max u^H R u. That machinery keys off a nontrivial R. Our
R = 0 (linear objective), so the interesting structure is entirely in the LINEAR WALLS, where the SPEC's
Pataki/Barvinok rank bound (r(r+1)/2 <= #active walls) makes the nonconvex residual dimension 0-4 and
COPT's spatial B&B global in < 0.3 s at n<=49. So on OUR class the correct global tool is enumeration /
tiny spatial B&B / SDR rank-reduction on the residual (ARCH-1), not the UQP power/MM iteration, which stays
LOCAL. On our SMALL problems the residual solve reaches global cheaply; the power/MM iteration does not.
EVIDENCE: ESTABLISHED (family locality) + citing SPEC's measured global reduction. UQP power method local:
D04-2. MERIT global tool is the pi/4-beating SDR bound on u^H R u (D04-1), not applicable to a linear
objective without walls. SPEC section 4.2/4.4: residual dimension 0-4, COPT global < 0.3 s at n<=49, ILS
within 2.8e-5 b on j021.
IMPACT: correctness/framing. Settles sub-question 5: the family does NOT reach global on our class by
itself; it needs the convex-dual + residual structure (which is a DIFFERENT method family) to be global.
PROPOSAL: keep the UQP family classified as a LOCAL method for our problem; globality is ARCH-1's job.
CONFIDENCE: 0.85.
DEPENDS-ON: D04-2, D04-7.

## D04-9. Port feasibility: pure-Java trivial and fast, but that is not the bottleneck

TITLE: A power/MM iteration ports to pure Java in a few lines and is cheap on our sizes; feasibility is not
the question, value is.
LOCATION: research topic; applied to our model.
CLAIM: One power/MM step is a mat-vec plus a per-tick atan2/normalize, both trivially pure-Java (no
dependency). Our friction map is causal/banded and the wall matrix A has few rows (~<=22 walls on
thousand/1), so the penalized mat-vec (R = -rho A^T A applied via A then A^T) is O(n * #walls), roughly
O(49 * 22) ~ 1e3 flops per step; even thousands of steps are sub-millisecond at n<=49. So the family sits
comfortably inside the 0.1-800 ms envelope. The costs that matter are NOT the port or the per-step flops
but (a) the penalty-continuation outer loop and rho tuning, and (b) the local-optimum quality on coupled
corridors and the degenerate-tick stall (D04-6, D04-7).
EVIDENCE: SPECULATION / UNMEASURED-HYPOTHESIS for the flop and iteration estimates (arithmetic from n<=49,
#walls<=22, and the O(n^2)/mat-vec cost quoted in D04-2). Wall counts from SPEC section 5 (thousand/1: 22
walls) and stage0. No microbenchmark run.
STAGE E EXPERIMENT: if D04-6 is prototyped, report warmup-then-median iteration counts and wall-clock on
the corpus; only then quote a converged-iteration number.
IMPACT: speed (NEUTRAL). Cheap enough to run, but cheapness is not a reason to prefer it over ARCH-1.
PROPOSAL: none beyond D04-6/D04-10.
CONFIDENCE: 0.7.
DEPENDS-ON: D04-6.

## D04-10. VERDICT: viable as a pure-Java LOCAL polish, NOT as the solver; it needs the convex-dual+residual structure to be global

TITLE: Projected-power-method / MM verdict for our class.
LOCATION: research topic; synthesis of D04-1..D04-9.
CLAIM: (1) The UQP power-method / MM family is a MONOTONE, cheap, pure-Java LOCAL method (D04-2, D04-3).
(2) On the NO-WALL case our problem is the trivial R=0 UQP: one-step global closed form, already shipped
(D04-5), so the family adds nothing there. (3) On the WITH-WALLS case the family is not native; it becomes
a penalized / manifold-AL projected-gradient iteration that is LOCAL, rho-tuned, blind to the section-4.2
structure (D04-6), and STALLS on exactly the degenerate ticks that make coupled multi-jump hard (D04-7),
reproducing the incumbent's measured failure rather than curing it. (4) It does NOT reach global on our
class by itself; globality here comes from the Pataki low-dim residual solve (ARCH-1), a different family
(D04-8). (5) So it is viable ONLY as an optional anytime LOCAL polish inside ARCH-2/ARCH-3, never as the
primary or the global mechanism; the SPEC's convex-dual + low-dim-residual (ARCH-1) strictly dominates it
on our class because it exploits structure the arg-iteration cannot see.
EVIDENCE: synthesis; each leg cited above (ESTABLISHED for the method facts D04-1/2/3/4/5/8; SPECULATION
with named Stage E experiments for the on-our-model behavior D04-6/7/9).
IMPACT: simplicity/correctness. Removes the UQP power/MM family as a candidate PRIMARY solver, keeps it on
the table only as a local polish, and reinforces ARCH-1 as the primary target.
PROPOSAL: do not build a projected-power-method/MM primary solver. If a local polish slot is wanted in
Stage E, prototype the penalized manifold-gradient step (D04-6) and A/B it against the incumbent ILS; adopt
only if it MEASURES faster or better at equal robustness, which is not expected.
CONFIDENCE: 0.8.
DEPENDS-ON: D04-5, D04-6, D04-7, D04-8.

---

## Sources (all fetched or searched this session; none invented)

- M. Soltanalian, P. Stoica, "Designing Unimodular Codes Via Quadratic Optimization," IEEE Trans. Signal
  Processing 62(5):1221-1234, 2014. https://ieeexplore.ieee.org/document/6698378/ ;
  https://ui.adsabs.harvard.edu/abs/2014ITSP...62.1221S/abstract
- M. Soltanalian, P. Stoica, "Designing Unimodular Codes via Quadratic Optimization is not Always Hard,"
  arXiv:1303.0152. https://arxiv.org/abs/1303.0152 (iteration u_{k+1}=e^{j arg(R u_k)}; local; O(n^2)).
- S. Ragi, E.K.P. Chong, H.D. Mittelmann, "Polynomial-Time Methods to Solve Unimodular Quadratic Programs
  With Performance Guarantees," arXiv:1703.08589 (dominant-eigenvector-matching, greedy (1-1/e)).
  https://arxiv.org/abs/1703.08589
- P. Stoica, H. He, J. Li, "New Algorithms for Designing Unimodular Sequences With Good Correlation
  Properties," IEEE TSP 57(4):1415-1425, 2009 (CAN/WeCAN).
  http://plaza.ufl.edu/haohe/papers/CAN.pdf
- J. Song, P. Babu, D.P. Palomar, "Optimization Methods for Designing Sequences With Low Autocorrelation
  Sidelobes," arXiv:1501.02252 ; "Sequence Set Design With Good Correlation Properties via
  Majorization-Minimization," arXiv:1510.01899.
- Y. Sun, P. Babu, D.P. Palomar, "Majorization-Minimization Algorithms in Signal Processing,
  Communications, and Machine Learning," IEEE TSP, Feb 2017.
  https://www.semanticscholar.org/paper/37a67228271527037c9250ae3fd220199275e42e
- "Quadratic Optimization for Unimodular Sequence Design via an ADPM Framework" (adaptive penalty dual
  method). https://www.researchgate.net/publication/341748401
- "Waveform Design for Optimal PSL Under Spectral and Unimodular Constraints via Alternating Minimization,"
  arXiv:2210.08564. https://arxiv.org/pdf/2210.08564
- "ADMM-Based Constant Modulus Waveform Design for Dual-Function Radar-Communication Systems," Entropy
  25(7):1027, 2023. https://doi.org/10.3390/e25071027
- "Modulus Waveform Design Based on Manifold ADMM Idea in Dual-Function Radar-Communication System,"
  Electronics 13(14):2726, 2024. https://doi.org/10.3390/electronics13142726
- "ADMM-based transmit beampattern synthesis for antenna arrays under a constant modulus constraint,"
  Signal Processing, 2020. https://www.sciencedirect.com/science/article/abs/pii/S0165168420300724
- MO-AltMin / complex-circle Riemannian manifold: "AltMin Algorithms for Hybrid Precoding in mmWave MIMO,"
  arXiv:1601.07340 ; "RMOCG: Riemannian Manifold Optimization-Based Conjugate Gradient for Phase-Only
  Beamforming," IEEE doc 9779535 ; "A Complete Derivation of Complex Circle Manifold (CCM) Riemannian
  Manifold Optimization Equations," arXiv:2508.07396.
