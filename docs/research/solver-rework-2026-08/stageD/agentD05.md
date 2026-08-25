# Stage D shard: agent D05

Agent: D05
Territory (research method family): RIEMANNIAN OPTIMIZATION over the PRODUCT OF CIRCLES / OBLIQUE
MANIFOLD (Riemannian gradient descent, conjugate gradient, and trust region on the per-tick yaw
coordinate theta_t), and the handling of the coupling walls via a Riemannian augmented Lagrangian /
smoothed exact penalty. Assessed as "arguably the native geometry" per the mission.

Inputs inspected:
- Read whole: `00-context-pack.md`, `SPEC.md` (esp. sections 4.1-4.6, 5, 6), `stage0-copt/FINDINGS.md`
  (H1/H2, bound tightness, residual-dimension measurements), `stageA/agentA03.md` (schema + the
  closed-form / disk-SOCP anchors).
- Code reads (for the our-model mapping, MEASURED from source, not run):
  `solver/JumpLinearModel.java:166` (analytic Jacobian d(addX)/dy=-addZ, d(addZ)/dy=addX),
  `solver/CostateDualSolver.java:11-14,408,429,451` (recovery u*_t = m_t g_t/||g_t||, the free-set
  Hessian), `solver/SlpSolve.java:147,163,236-238,247,257` (the incumbent already runs in theta and
  linearizes u_t(theta) with the i*u Jacobian), `solver/SmoothFaceRecovery.java:191,450`
  (`tangentProjector`, an existing tangent-space projection in the smoothing stage).
- Literature: WebSearch/WebFetch only; real citations listed in the References block. No prototype was
  run; every convergence/quality claim about OUR captures is labeled UNMEASURED-HYPOTHESIS and routed
  to Stage E. Cost-per-iteration is an ANALYTICAL flop count, labeled as such.

Legend: [ESTABLISHED] = textbook/peer-reviewed result or a fact read directly from our code.
[SPECULATION] = a belief about our problem not yet measured on our captures (route to Stage E).

---

## Findings

### D05-1
TITLE: The Riemannian toolkit (retraction, Riemannian gradient, RTR, vector transport) is mature and
its guarantees are exactly the ones SPEC section 4 asks about.
LOCATION: research topic; foundational.
CLAIM: Absil-Mahony-Sepulchre (2008) and Boumal (2023) give a complete first- and second-order
optimization calculus on smooth manifolds; Riemannian trust region (RTR, Absil-Baker-Gallivan 2007)
converges globally to critical points and locally superlinearly with the Riemannian Hessian, and
Boumal-Absil-Cartis (2019) prove RGD reaches an eps-first-order point in O(1/eps^2) and RTR reaches an
(eps,eps)-second-order point in O(1/eps^3), matching the Euclidean rates. [ESTABLISHED]
EVIDENCE: AMS 2008 (Princeton, free PDF sites.uclouvain.be/absil/amsbook); Boumal 2023 (Cambridge, free
PDF nicolasboumal.net/book); Absil-Baker-Gallivan, Found. Comput. Math. 7(3):303-330, 2007;
Boumal-Absil-Cartis, IMA J. Numer. Anal. 39(1):1-33, 2019 (the O(1/eps^2)/O(1/eps^3) result, confirmed
via WebSearch).
IMPACT: correctness/robustness (the second-order convergence rate is the property D05 leans on for the
degenerate ticks, D05-7).
PROPOSAL: adopt the RTR/RGD framework as the language for the residual-resolution engine of SPEC
ARCH-1 step 3.
CONFIDENCE: 0.97
DEPENDS-ON: -

### D05-2
TITLE: Our feasible set is LITERALLY the complex circle manifold (a scaled oblique manifold OB(2,n)),
for which a large constant-modulus Riemannian literature already exists.
LOCATION: research topic + `SPEC.md:230-241`.
CLAIM: With u_t identified as z_t in C of fixed modulus m_t, the feasible torus prod_t m_t S^1 is the
COMPLEX CIRCLE MANIFOLD; as a real object it is the oblique manifold OB(2,n) (n columns of fixed norm)
rescaled per column by m_t. Constant-modulus problems on exactly this manifold are solved routinely in
hybrid-beamforming and radar-waveform design by Riemannian CG / trust region (project the Euclidean
gradient onto the tangent of each circle, retract, repeat), reported low-complexity and
fast-converging. [ESTABLISHED]
EVIDENCE: AMS 2008 defines the oblique manifold OB(p,n) as fixed-norm columns; Yu-Shen-Zhang-Letaief,
"Alternating Minimization Algorithms for Hybrid Precoding ...", IEEE JSTSP 10(3):485-500, 2016 (MO-AltMin,
constant modulus = product of complex circles, Riemannian CG); "Transmit MIMO Radar Beampattern Design
Via Optimization on the Complex Circle Manifold" (arXiv:1904.07329); Soltanalian-Stoica, "Designing
Unimodular Codes Via Quadratic Optimization", IEEE TSP 62(5):1221-1234, 2014 (UQP, the unit-modulus
class our modulus generalizes).
IMPACT: simplicity/robustness (we are not inventing geometry; the retraction and tangent projection are
off-the-shelf and battle-tested on the identical manifold).
PROPOSAL: treat the beamforming complex-circle-manifold recipe as the reference implementation to port.
CONFIDENCE: 0.95
DEPENDS-ON: D05-1

### D05-3
TITLE: The pure-Java port is trivial: the retraction on a circle is ANGLE ADDITION, and the Riemannian
gradient is the tangential (i*u) projection our code ALREADY computes.
LOCATION: `JumpLinearModel.java:166`, `SlpSolve.java:236-238,247,257`, `SmoothFaceRecovery.java:450`.
CLAIM: On the circle S^1 parametrized by theta_t the exponential map / retraction is theta_t <-
theta_t + step (no matrix, no re-orthonormalization). The Riemannian gradient component at tick t is the
Euclidean gradient dotted with the unit tangent t_hat = i*u_t/m_t = (-sin phi, cos phi), i.e. a single
scalar per tick. Our source ALREADY forms exactly this: `JumpLinearModel:166` documents the analytic
Jacobian d(addX)/dy=-addZ, d(addZ)/dy=addX (= i*u); `SlpSolve:247` computes `du = -uz` or `ux` (the wall
tangent slope) and `:257` `objRow += -(cx*-uz + cz*ux)*RAD` (the objective's Riemannian gradient in
theta); `SmoothFaceRecovery:450` `tangentProjector` is already a manifold tangent-space projection.
[ESTABLISHED / code-MEASURED]
EVIDENCE: direct reads of the five line anchors above. The ingredients of a Riemannian solver on theta
are present in the codebase today, scattered across SLP and the face-walk.
IMPACT: simplicity (near-zero new primitive code: a retraction that is `+=`, a gradient the model class
already exposes, no linear-algebra dependency, stays MC-free and dependency-free per SPEC invariants).
PROPOSAL: factor the existing `i*u` Jacobian and `tangentProjector` into one small `CircleManifold`
utility (retract = angle add; egrad2rgrad = tangential projection) and build the residual solver on it.
CONFIDENCE: 0.9
DEPENDS-ON: D05-2

### D05-4
TITLE: The linear inequality WALLS are the only non-manifold part, and the standard handling is a
Riemannian augmented Lagrangian / smoothed exact penalty (Liu-Boumal 2019).
LOCATION: research topic; maps to `SPEC.md:227` (walls) + section 4.5.
CLAIM: The manifold (modulus) is enforced exactly by the geometry; the linear walls A_j . u <= b_j are
enforced by an OUTER augmented-Lagrangian or smoothed-exact-penalty loop, each inner solve an
unconstrained Riemannian minimization on the product of circles. Liu-Boumal give this method with
convergence results, and it is implemented in Manopt/Manopt.jl (`augmented_Lagrangian_method`).
[ESTABLISHED]
EVIDENCE: Liu & Boumal, "Simple algorithms for optimization on Riemannian manifolds with constraints",
arXiv:1901.10000, 2019 (Appl. Math. Optim. 82:949-981, 2020): extends the augmented Lagrangian and
smoothed exact penalty to equality AND inequality constraints on manifolds (WebFetch of the abstract
confirms both methods and "fundamental convergence results"); Manopt.jl solver docs confirm a shipped
implementation.
IMPACT: robustness/simplicity (ONE outer ALM loop replaces the shipped stack of two bespoke inward-margin
ladders + AL-FISTA disk projection + dither/projection realization; the modulus never has to be relaxed
to a disk and re-snapped, unlike RelaxationRecovery A03-2/A03-5).
PROPOSAL: prototype the walls as a Riemannian ALM in Manopt/Pymanopt (oracle only), then port the outer
loop; the inner solve is D05-3's RGD/RTR.
CONFIDENCE: 0.85
DEPENDS-ON: D05-3

### D05-5
TITLE: Our objective is LINEAR (affine in u), which makes the problem MILDER than the constant-modulus
literature and makes the UNCONSTRAINED torus problem benign and closed-form.
LOCATION: `SPEC.md:227-241`; `CostateDualSolver.java:11-14`.
CLAIM: Maximizing c^T u over the bare torus (no walls) is SEPARABLE: each circle independently maximizes
c_t . m_t(cos,sin), a single sinusoid with a unique maximizer u_t = m_t c_t/|c_t|. That is precisely the
LCvx costate closed form the code already ships (`CostateDualSolver:13`, recovery u*_t = m_t g_t/||g_t||).
So the ENTIRE difficulty, and all nonconvex multi-basin structure, is injected by the coupling walls
(through the ALM penalty, which couples the circles). The beamforming/radar literature optimizes a
QUADRATIC form on the same manifold (harder); our linear objective is the easy end of that family.
[ESTABLISHED / from our model]
EVIDENCE: c^T u = sum_t m_t (c_{x,t} cos phi_t + c_{z,t} sin phi_t); each term is unimodal on its circle;
argmax is the phase of c_t. This is identical to the shipped closed-form recovery when g_t = c_t (no
active walls), corroborated by Stage 0: single/easy captures are 0-throttled, SDR rank-1, closed-form
exact (`FINDINGS.md:38-46,66-70`).
IMPACT: correctness (bounds expectations: the manifold method is trivially global where walls do not
bind; it inherits difficulty exactly and only from binding walls, matching the COPT residual-dimension
measurement, D05-6).
PROPOSAL: seed the Riemannian solve with the costate closed form (free, already computed) so the inner
solve starts at the unconstrained global optimum and only has to negotiate the active walls.
CONFIDENCE: 0.9
DEPENDS-ON: D05-4

### D05-6
TITLE: A Riemannian solve on theta is LOCAL (converges to a second-order stationary point), so it needs
a seed or multi-start; the number of hard directions is exactly the 0-4 degenerate ticks Stage 0
measured.
LOCATION: research topic; `stage0-copt/FINDINGS.md:38-70`, `SPEC.md:257-267`.
CLAIM: RGD/RTR guarantee only a second-order stationary point, not the global optimum; the coupled
multi-jump captures have a genuinely nonconvex landscape (Stage 0: SDR rank 2-3, disk loose by ~1.6e-3 b,
1-4 throttled ticks on j008b/j021/loopmm). So on those, a single Riemannian descent lands in a basin
whose quality depends on the seed, and reaching the COPT global optimum needs the convex-dual seed
(D05-5) plus, in the worst case, multi-start / small enumeration over the handful of degenerate ticks.
Empirically the constant-modulus manifold landscape is reported benign (algorithms "less sensitive to
initialization", small max-min gap over 50 random starts in the radar-waveform work), but that is NOT a
proof for our wall-coupled instances. [ESTABLISHED for the local-guarantee half; SPECULATION for
"benign enough that one seed suffices" on our captures]
EVIDENCE: Boumal-Absil-Cartis 2019 (second-order-stationary, not global); Stage 0 residual dimension
0-4 and rank readouts (`FINDINGS.md:66-70`); the "less sensitive to initialization / 50 trials" remark
from the complex-circle radar literature (WebSearch, arXiv:2508.19822 family). No run on our captures yet.
IMPACT: robustness (sets the honest expectation: Riemannian local + costate seed is the design; global
certification still needs the convex bound or a tiny branch, exactly SPEC ARCH-1's three steps).
PROPOSAL: pair the Riemannian residual solver with the existing costate seed; measure in Stage E how
often one seed reaches the COPT optimum on j008b/j021/loopmm and how many degenerate-tick restarts close
the rest.
CONFIDENCE: 0.8
DEPENDS-ON: D05-5

### D05-7
TITLE: The degenerate ticks (vanishing costate g_t = 0) are exactly where a SECOND-ORDER Riemannian
trust region should beat the shipped closed-form default: RTR reads negative curvature the costate
recovery cannot.
LOCATION: research topic; `SPEC.md:249-253,276-286`, `CostateDualSolver.java:451`.
CLAIM: At a degenerate tick the Riemannian GRADIENT on that circle vanishes (g_t = 0), so first-order
methods and the closed-form recovery have no direction and DEFAULT the tick to a fixed axis (SPEC 4.3,
the measured root cause of the 0.34 b / 2.89 b infeasibility). The Riemannian HESSIAN there is generally
indefinite (a manifold saddle / a flat degenerate face), and RTR with a strict-saddle / negative-curvature
step moves off it toward a minimizer. The free-set Hessian our dual already assembles
(`CostateDualSolver:451`, H = sum (m_t/||g_t||)(I - ghat ghat^T)) is the same object a Riemannian Hessian
would use, and it is singular exactly as ||g_t|| -> 0, which is the analytic signature of the degeneracy.
This is D05's central thesis: RTR navigates the vanishing-costate set by curvature, where the costate
default is blind. [SPECULATION on our captures; ESTABLISHED that RTR reaches second-order points and
strict-saddle RTR escapes saddles]
EVIDENCE: Absil-Baker-Gallivan 2007 (RTR local model with the Riemannian Hessian); "Riemannian
trust-region methods for strict saddle functions with complexity guarantees" (arXiv:2402.07614, Math.
Programming 2024) and Criscitiello-Boumal "Efficiently escaping saddle points on manifolds" (NeurIPS
2019); the shipped Hessian's ||g_t|| -> 0 singularity at `CostateDualSolver:451,83` (wOverNrm = m_t/||g_t||).
No measurement yet that RTR resolves j021's t12 or thousand's degenerate ticks.
IMPACT: correctness (this is the specific mechanism by which a Riemannian residual solver would fix the
shipped recovery breakdown; it is the highest-value hypothesis in this shard).
PROPOSAL: Stage E: on j021 (1 degenerate tick t12) and thousand (multi), run RTR-with-negative-curvature
from the costate seed and measure whether it reaches the COPT optimum where the closed-form default lands
0.083 b off; this is the discriminating experiment.
CONFIDENCE: 0.72
DEPENDS-ON: D05-6

### D05-8
TITLE: Cost per iteration is trivial at n<=49; an analytical flop count puts a full Riemannian solve
orders of magnitude inside the 0.1-800 ms envelope (compute), with byte-exact certify the real cost.
LOCATION: research topic; sizes from `SPEC.md` (n<=49), walls ~22 (thousand).
CLAIM: Per RGD step: Euclidean gradient of (linear obj + wall penalties) is O(n + n*W) (each wall's coef
array is length n); tangential projection is O(n); retraction is n scalar adds. For n=49, W~22 that is
~2k multiply-adds per gradient, ~tens of microseconds of raw FP. An RTR inner truncated-CG step is one
Hessian-vector product, same O(n*W); ~10-50 CG steps per outer step; ~20-100 outer steps; the ALM outer
loop ~10-30 rounds. Total ~1e6-1e7 flops => well under 1 ms of pure compute for the whole solve at these
sizes. The dominant cost stays the per-candidate byte-exact replay through ExactJumpModel (already in the
budget), not the manifold iteration. [SPECULATION: analytical estimate, not benchmarked]
EVIDENCE: flop accounting from the model dimensions; the incumbent SLP already runs this same
per-iteration work (SlpSolve builds the same tangent rows) at the measured envelope, so a Riemannian
variant is same-order. No timed run performed.
IMPACT: speed (the method is NOT the bottleneck at our sizes; it fits the envelope with large margin).
PROPOSAL: Stage E benchmark: warmup + repeated timed RGD/RTR solves on j021/thousand via direct java -cp,
report medians vs the shipped dualChain wall-clock.
CONFIDENCE: 0.75
DEPENDS-ON: D05-3

### D05-9
TITLE: The incumbent SLP is ALREADY a first-order, Euclidean-linearized Riemannian method on theta; a
true Riemannian trust region is its principled second-order upgrade, not a new stack.
LOCATION: `SlpSolve.java:10,147,163,236-257`, `TrustRegionLp.java`.
CLAIM: SLP works in theta (`:147,163`), rebuilds u_t = m_t(cos,sin) each step (`:236-238`), linearizes
each wall and the objective with the i*u Jacobian (`:247,257`), and takes a trust-region LP step
(`TrustRegionLp`). That is a first-order sequential-linearization on the circle manifold with a box trust
region in theta. A Riemannian CG/TR differs by (a) using a proper retraction instead of re-evaluating
u(theta) from scratch (same result here since the circle retraction IS theta-addition), and (b) adding
the Riemannian Hessian / negative-curvature model that the LP lacks. So D05 is not proposing a foreign
mechanism; it is proposing the second-order completion of what SLP already does. [ESTABLISHED /
code-MEASURED]
EVIDENCE: reads of the SLP anchors; the LP is first-order by construction (no curvature term), which is
exactly why it "thrashes on the coupled corridors" (SPEC 4.3) at the degenerate ticks D05-7 targets.
IMPACT: simplicity/robustness (reframes the residual solver as an upgrade-in-place of SLP, lowering port
risk and reusing the compiled-wall/Jacobian machinery).
PROPOSAL: build the Riemannian TR residual solver by adding the curvature model to the existing SLP
linearization path rather than as a parallel subsystem; retire the LP-only step for the degenerate case.
CONFIDENCE: 0.85
DEPENDS-ON: D05-3, D05-7

### D05-10
TITLE: SMOOTHNESS UNIFIES here and NOWHERE ELSE: it is convex in theta and native to the manifold, so a
Riemannian solve optimizes reach + walls + smoothness JOINTLY in one objective, collapsing the four
smoothing passes (the SPEC 4.5 / C4 target the convex-in-u relaxation provably cannot meet).
LOCATION: research topic; `SPEC.md:308-315` (4.5), `SPEC.md:72-78` (C4), `SmoothFaceRecovery.java`.
CLAIM: SPEC 4.5 (F3, A08-7) MEASURED that smoothness beta||D2 theta||_1 + gamma||D1 theta||_2^2 is convex
in theta but NOT in u (theta = atan2(u) is nonconvex in u), so it destroys LCvx tightness and cannot be a
term in the convex-in-u program. A Riemannian method optimizes IN theta on the manifold, where the
smoothness term is a smooth (indeed convex) function of the free variable; therefore it can be ADDED to
the manifold objective and minimized jointly with the reach objective and the wall penalties in a single
solve. This is the unification the mission and SPEC 4.5 explicitly wanted: not "smoothing as a convex
term in u" (refuted) but "smoothing carried natively by the theta-manifold solver", subsuming DeWiggle +
SmoothingPolish + SmoothFaceRecovery + turnCost into one objective. [SPECULATION on effect size;
ESTABLISHED that the term is smooth in theta and admissible in a Riemannian objective]
EVIDENCE: SPEC 4.5 measured non-convexity-in-u (F3); D2/D1 theta are linear maps of theta, so their
norms are convex in theta; `SmoothFaceRecovery:191,450` already does a tangent-space (theta) null-space
walk, i.e. a crude manual version of exactly this joint step. No measured reversal-count comparison yet.
IMPACT: smoothness + simplicity (a genuine collapse of four post-passes into one solve, and the ONLY
method family in this campaign that can carry smoothness inside the optimizer rather than as a post-hoc
tie-break; this is D05's standout contribution).
PROPOSAL: Stage E: add gamma||D1 theta||_2^2 + beta smooth-l1(||D2 theta||) to the Riemannian objective,
solve jointly, and A/B the realized reversal count against the shipped four-pass stack on the hpk corpus
and thousand, within a give-back budget expressed as a trust-region / penalty weight.
CONFIDENCE: 0.7
DEPENDS-ON: D05-7, D05-9

### D05-11
TITLE: Manopt (Matlab) / Pymanopt (Python) are the prototyping oracles for this method family; both ship
the oblique manifold, RTR, and the constrained ALM, and neither is ever a shipped dependency.
LOCATION: research topic; SPEC invariant (`SPEC.md:368-371`, dependency-free shipped path).
CLAIM: Manopt and Pymanopt implement the product/oblique manifold, Riemannian TR/CG, and (Manopt.jl) the
augmented-Lagrangian constrained solver, with automatic differentiation in Pymanopt; use them to validate
the formulation and to produce reference Riemannian optima before writing any Java, exactly as COPT is
used for the convex/global references. The shipped Java stays dependency-free (retraction = angle add,
gradient = existing i*u projection, D05-3). [ESTABLISHED]
EVIDENCE: Boumal-Mishra-Absil-Sepulchre, "Manopt, a Matlab toolbox for optimization on manifolds", JMLR
15:1455-1459, 2014 (arXiv:1308.5200); Townsend-Koep-Weichwald, "Pymanopt: A Python Toolbox for
Optimization on Manifolds using Automatic Differentiation", JMLR 17(137):1-5, 2016 (arXiv:1603.03236);
Manopt.jl `augmented_Lagrangian_method` docs.
IMPACT: correctness (a cheap oracle to de-risk the formulation before the Java port).
PROPOSAL: in research/ (never a module), reproduce the D05-4 ALM-on-circles formulation in Pymanopt on
the Stage 0 dumped captures; compare its optimum to COPT's global reference; only then port.
CONFIDENCE: 0.9
DEPENDS-ON: D05-4

### D05-12
TITLE: VERDICT: a Riemannian local solver on theta is a VIABLE, SIMPLE pure-Java engine for SPEC
ARCH-1's residual step; it is global only where the landscape is benign (single/easy, already
closed-form), and on coupled multi-jump it is a LOCAL solver that COMPLEMENTS the convex-dual seed
rather than replacing it, while uniquely unifying smoothness.
LOCATION: synthesis of D05-1..D05-11 against `SPEC.md:396-418` (ARCH-1/2/3).
CLAIM: (1) Viable and simple: retraction = angle addition, gradient = the i*u projection the code already
computes (D05-3), cost far inside the envelope at n<=49 (D05-8), dependency-free. (2) Global where benign:
the linear objective over the bare torus is separable and its global max IS the shipped costate closed
form (D05-5), so single/easy jumps are already solved; the manifold method adds nothing there but a
uniform framework. (3) Local on coupled multi-jump: RGD/RTR reach a second-order stationary point only,
so they need the costate seed and possibly a tiny multi-start over the 0-4 degenerate ticks (D05-6) to
match the COPT global optimum; this is precisely SPEC 4.2's convex-dual-plus-low-dim-residual, with the
Riemannian TR as the residual engine and its second-order Hessian as the mechanism that navigates the
vanishing-costate degeneracy the shipped default botches (D05-7). (4) Standout: it is the only method
here that carries the smoothness term inside the optimizer, jointly with reach and walls (D05-10). So it
does NOT obviate the convex dual; it is the natural realization of ARCH-1 step 3 (residual + smoothness
tie-break), and an in-place second-order upgrade of the incumbent SLP (D05-9). [SPECULATION on the
coupled-case quality/speed until Stage E measures it; the structural claims are ESTABLISHED/code-MEASURED]
EVIDENCE: the chain D05-1..D05-11; cross-validated against Stage 0's measured residual dimension 0-4 and
the shipped recovery's measured 0.083 b / 2.89 b default failure.
IMPACT: simplicity + robustness + smoothness (a single manifold solve for ARCH-1 step 3 that folds in
smoothing); correctness pending the Stage E degenerate-tick measurement (D05-7).
PROPOSAL: Route to Stage E as ONE prototype: `CircleManifold` (retract/egrad2rgrad) + Riemannian TR with
a negative-curvature step + an ALM outer loop for the walls + the smoothness term in the objective,
seeded by the costate closed form; benchmark against COPT references on j021/j008b/loopmm/thousand and
the dF-chain captures, byte-exact-certified through ExactJumpModel. Recommend it as the residual engine
for ARCH-1 (primary) and, failing robustness, the smoothing-and-degenerate-tick upgrade inside ARCH-2.
CONFIDENCE: 0.78
DEPENDS-ON: D05-1, D05-2, D05-3, D05-4, D05-5, D05-6, D05-7, D05-8, D05-9, D05-10, D05-11

---

## References (all real, checkable)

- P.-A. Absil, R. Mahony, R. Sepulchre, "Optimization Algorithms on Matrix Manifolds", Princeton
  University Press, 2008. Free PDF: sites.uclouvain.be/absil/amsbook. (Oblique manifold OB(p,n);
  retraction; Riemannian gradient/Hessian; RTR.)
- P.-A. Absil, C. G. Baker, K. A. Gallivan, "Trust-Region Methods on Riemannian Manifolds", Foundations
  of Computational Mathematics 7(3):303-330, 2007. (Original RTR.)
- N. Boumal, "An Introduction to Optimization on Smooth Manifolds", Cambridge University Press, 2023.
  Free PDF: nicolasboumal.net/book.
- N. Boumal, P.-A. Absil, C. Cartis, "Global rates of convergence for nonconvex optimization on
  manifolds", IMA Journal of Numerical Analysis 39(1):1-33, 2019. (RGD O(1/eps^2), RTR O(1/eps^3) to
  first/second-order KKT.)
- C. Liu, N. Boumal, "Simple algorithms for optimization on Riemannian manifolds with constraints",
  arXiv:1901.10000, 2019; Applied Mathematics & Optimization 82:949-981, 2020. (Riemannian augmented
  Lagrangian + smoothed exact penalty for equality AND inequality constraints; the walls handler.)
- N. Boumal, B. Mishra, P.-A. Absil, R. Sepulchre, "Manopt, a Matlab toolbox for optimization on
  manifolds", JMLR 15:1455-1459, 2014 (arXiv:1308.5200). Manopt.jl `augmented_Lagrangian_method` solver.
- J. Townsend, N. Koep, S. Weichwald, "Pymanopt: A Python Toolbox for Optimization on Manifolds using
  Automatic Differentiation", JMLR 17(137):1-5, 2016 (arXiv:1603.03236).
- X. Yu, J.-C. Shen, J. Zhang, K. B. Letaief, "Alternating Minimization Algorithms for Hybrid Precoding
  in Millimeter Wave MIMO Systems", IEEE J. Sel. Topics Signal Process. 10(3):485-500, 2016. (MO-AltMin;
  constant modulus = product of complex circles; Riemannian CG.)
- "Transmit MIMO Radar Beampattern Design Via Optimization on the Complex Circle Manifold",
  arXiv:1904.07329. (Complex circle manifold = our feasible set, constant-modulus.)
- M. Soltanalian, P. Stoica, "Designing Unimodular Codes Via Quadratic Optimization", IEEE Trans. Signal
  Processing 62(5):1221-1234, 2014. (UQP; the unit-modulus class our fixed-modulus generalizes.)
- Riemannian trust-region strict-saddle: "Riemannian trust-region methods for strict saddle functions
  with complexity guarantees", arXiv:2402.07614 (Mathematical Programming, 2024); C. Criscitiello,
  N. Boumal, "Efficiently escaping saddle points on manifolds", NeurIPS 2019.
- "On Minimization/Maximization of the Generalized Multi-Order Complex Quadratic Form With
  Constant-Modulus Constraints", arXiv:2508.19822 (empirical initialization-insensitivity on the complex
  circle manifold).
