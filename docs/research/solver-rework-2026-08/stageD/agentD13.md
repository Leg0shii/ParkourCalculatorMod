# Agent D13 shard: smoothness / turn-minimal trajectory as an optimization objective

Agent id: D13
Assigned territory (method family): SMOOTHNESS / TURN-MINIMAL trajectory as an optimization objective:
total-variation (TV) minimization, second-difference (jerk) minimization, sparse-jump / L1-TV, minimum-reversal
path smoothing, and the change-point (L0) view of reversal counting. Mapped to SPEC C4 (Smoothing) and
SPEC section 4.5 (smoothing as the residual tie-break, not a convex u-term).

Docs read: `00-context-pack.md` (whole), `SPEC.md` sections 1 (C4), 3, 4.1-4.6, 5, 6, 7,
`stageA/SYNTHESIS.md` (F3, F6, and the consistency matrix), `stageA/agentA08.md` (whole: A08-1..A08-16).
Code inspected (to anchor the metrics): `core/.../anglesolver/solver/SmoothFaceRecovery.java`
(`reversals` 323-333, `jerkOf` 335-340), plus the A08 file:line anchors for `Objective.turnCost`,
`DeWiggle`, `SmoothingPolish` (not re-opened; trusted A08's verified constants).
Commands run: WebSearch / WebFetch for the literature only. No solver was run (this is a methods shard);
every applicability claim is tagged ESTABLISHED (literature), MODEL-ANCHORED (follows from the SPEC/A08
measurements), or UNMEASURED-HYPOTHESIS (needs a Stage E prototype).

Convention: ESTABLISHED = a checkable published result. MODEL-ANCHORED = a deduction from our model plus an
already-measured Stage 0 / Stage A number (cited). SPECULATION / UNMEASURED-HYPOTHESIS = a belief to route to
a prototype. All citations are real and were fetched this session; none are invented.

---

## Section 1: FOUNDATIONAL literature (the exact tools for the four A08 metrics)

### D13-1: The A08 metrics are, verbatim, the discrete TV / trend-filtering / L0-Potts ladder; the literature names each and gives an exact solver
- LOCATION: research topic; anchors `Angles.turnCost`, `DeWiggle`, `SmoothingPolish.roughness`, `SmoothFaceRecovery.jerkOf`.
- CLAIM: the four shipped smoothing metrics are not ad hoc; each is a named object in the 1D signal-smoothing
  literature, and the mapping is exact:
  - `SmoothingPolish.roughness = ||D1 theta||_2^2` (sum of squared first differences) is a discrete
    Tikhonov / quadratic-variation penalty; the continuous analogue integrated as a squared derivative is
    the minimum-jerk family of Flash and Hogan (D13-3). It is the L2 penalty, provably reversal-BLIND (A08-6).
  - `jerkOf / wiggleDeg = ||D2 theta||_1` (TV of the turn rate) is exactly the penalty of first-order L1
    TREND FILTERING (Kim-Koh-Boyd-Gorinevsky; Tibshirani order k=1), which produces piecewise-LINEAR headings,
    i.e. few kinks/reversals (D13-4).
  - `||D1 theta||_1` (TV of yaw itself, the classic ROF/fused-lasso penalty) is trend filtering order k=0 and
    produces piecewise-CONSTANT headings (D13-4). Not currently used, but it is the tightest convex surrogate
    for "few DIRECTION changes" as opposed to "few turn-rate changes."
  - `reversals` / DeWiggle run count = the L0 cardinality of sign changes = a CHANGE-POINT / Potts-segmentation
    object (D13-5). NON-convex; the convex surrogates above are its relaxations.
- EVIDENCE: metric definitions read from `SmoothFaceRecovery.java:323-340` and A08-1..A08-4 (measured
  constants). Literature identity is ESTABLISHED (D13-3, D13-4, D13-5 citations).
- IMPACT: simplicity/correctness. It converts A08's "which metric is right" question into a solved textbook
  taxonomy: reversal count is L0 change-point; its convex surrogates are order-0 (fused lasso) and order-1
  (L1 trend filter) TV; the L2 metric is the wrong (reversal-blind) surrogate, which A08-6 measured directly.
- PROPOSAL: adopt `||D2 theta||_1` (order-1 trend filtering) as THE single convex smoothness surrogate and
  drop the L2 `roughness` (SmoothingPolish) entirely; keep the L0 reversal count only as the discrete
  tie-break the surrogate approximates. This is the A08-8 recommendation, now literature-grounded.
- CONFIDENCE: 0.9
- DEPENDS-ON: A08-6, A08-8, D13-3, D13-4, D13-5.

### D13-2: Exact 1D L1-TV / fused-lasso has O(n) NON-ITERATIVE solvers (taut string, Condat direct, Johnson DP) that are trivially pure-Java
- LOCATION: research topic; port target = a helper in `core/.../anglesolver/solver/`.
- CLAIM: the 1D total-variation-regularized least squares problem
  `min_x (1/2)||x - y||_2^2 + lambda ||D1 x||_1` (the fused-lasso signal approximator) is solved EXACTLY, not
  iteratively, in O(n) time and O(n) (Condat: O(1) extra) space by three independent methods:
  the taut-string algorithm (Davies-Kovac 2001), Condat's direct forward algorithm (2013), and Johnson's
  dynamic program (2013). The same DP solves the L0-segmentation (exact reversal/jump minimization with a
  fidelity term) in O(n) for the order-0 case (Johnson 2013).
- EVIDENCE: ESTABLISHED. Condat (2013) reports the direct algorithm denoises signals with n in the tens of
  millions in a fraction of a second on a laptop; Johnson (2013) proves linear worst-case running time; the
  taut-string O(n) result is Davies-Kovac (2001). (Citations in Section 6.)
- IMPACT: port feasibility (decisive). A pure-Java taut-string or Johnson DP is ~60-120 lines, no dependency,
  microseconds at our n (<=353), well inside the 0.1-800 ms envelope (SPEC section 5). No LP library needed
  (respects A04-7: re-adding an LP dep is net-negative).
- PROPOSAL: if a full-sequence convex-metric smoothing seed is wanted (D13-9 verdict path iii), implement the
  taut-string solver as the inner convex solve; it gives the ideal unconstrained smooth target in one O(n)
  pass, which a byte-exact projection/local-search then repairs onto the feasible face.
- CONFIDENCE: 0.92 (algorithmic port cost is well established; the caveat is that our problem is CONSTRAINED,
  D13-8).
- DEPENDS-ON: D13-8.

### D13-3: Minimum-jerk (Flash-Hogan 1985) is the origin of the squared-derivative smoothness objective, and it is precisely the reversal-BLIND metric A08 measured
- LOCATION: research topic; anchor `SmoothingPolish.roughness` (A08-3).
- CLAIM: the minimum-jerk principle (Flash and Hogan, 1985) minimizes the integral of squared jerk (third
  derivative of position) over a movement, yielding a quintic polynomial trajectory. Its discrete
  squared-derivative form is exactly the L2 penalty family that `SmoothingPolish.roughness` (`||D1 theta||_2^2`)
  belongs to. This is why SmoothingPolish produces a smooth-LOOKING path but cannot suppress a load-bearing
  dither: an L2 (min-jerk-type) penalty spreads energy evenly and is blind to the SIGN pattern of the
  differences (A08-6 measured roughness(dither)=roughness(mono)=401.25).
- EVIDENCE: ESTABLISHED (Flash-Hogan 1985, J. Neurosci.). The blindness is MODEL-ANCHORED to A08-6's measured
  equal-roughness result.
- IMPACT: correctness. Names WHY the one convex stage in the shipped stack is the wrong tool: it optimizes a
  min-jerk-style L2 objective, which is the right tool for a via-point-free smooth reach but the wrong tool
  for reversal/dither suppression on a hugging TAS path.
- PROPOSAL: do not use a squared-derivative (min-jerk / L2) penalty as the reversal remover. If an L2 spreader
  is kept at all, keep it ONLY as a secondary tie-break under the L1-TV term, and only if measured to help
  (A08-9 says drop it).
- CONFIDENCE: 0.88
- DEPENDS-ON: A08-6, D13-1.

### D13-4: L1 trend filtering (Kim-Koh-Boyd-Gorinevsky 2009; Tibshirani 2014) is the exact convex surrogate for our ||D2 theta||_1 tiebreak, with the fused-lasso as its k=0 special case
- LOCATION: research topic; anchor `wiggleDeg / jerkOf = ||D2 theta||_1`.
- CLAIM: L1 trend filtering minimizes `(1/2)||y - x||_2^2 + lambda ||D^(k+1) x||_1`. For k=1 the penalty is
  the TV of the first difference = `||D2 x||_1`, exactly our jerk/wiggle metric, and its solution is
  piecewise LINEAR with few kinks (= few reversals). For k=0 it reduces to the 1D fused lasso (TV of the
  signal, ROF in 1D). Tibshirani (2014) proves trend filtering is asymptotically minimax-adaptive over
  piecewise-polynomial classes and that k=0/k=1 coincide exactly with locally-adaptive regression splines in
  finite samples. So our chosen surrogate sits inside a well-characterized, provably good family, and its
  order parameter k is a principled knob (k=0 = "prefer few heading changes", k=1 = "prefer few turn-rate
  changes / few kinks").
- EVIDENCE: ESTABLISHED (Kim et al. 2009 SIAM Review; Tibshirani 2014 Ann. Stat.). The k=0/k=1 penalty
  identities are stated in both papers.
- IMPACT: simplicity/smoothness. Gives the single-term smoothness objective a named, tunable, minimax-optimal
  form and a free choice between "piecewise-constant heading" and "piecewise-linear heading" smoothing.
- PROPOSAL: standardize the smoothness objective as order-1 trend filtering `||D2 theta||_1` (matches the
  face-walk/turnCost tiebreak that A08 measured as the sensitive metric). Expose k as an optional setting only
  if a piecewise-constant-heading style is ever wanted.
- CONFIDENCE: 0.87
- DEPENDS-ON: D13-1, A08-6.

### D13-5: The reversal COUNT is an L0 change-point / Potts-segmentation object; exact DP (Johnson L0-segmentation) or pruned DP (PELT) solves it, and yaw's circle-valuedness has a dedicated L1/L0-Potts literature
- LOCATION: research topic; anchor `SmoothFaceRecovery.reversals` (`:323-333`), DeWiggle run count (A08-2).
- CLAIM: "minimize the number of turn reversals" is the L0 penalized-segmentation (Potts / piecewise-constant
  Mumford-Shah) problem on the turn-rate sign sequence, not a TV problem. Exact global L0-segmentation with a
  fidelity term is solved by dynamic programming: Johnson (2013) gives an O(n) DP for the k=0 L0 case; PELT
  (Killick-Fearnhead-Eckley 2012) prunes optimal-partitioning DP to expected-linear time while remaining
  EXACT. Crucially for us, yaw is a CIRCLE-valued signal, and the L1-Potts / Mumford-Shah literature for
  circle- and manifold-valued data (Weinmann-Storath-Demaret) solves the jump-sparse problem directly on the
  circle, avoiding the wrap/branch-cut hazard that a naive TV on wrapped angles would hit.
- EVIDENCE: ESTABLISHED (Johnson 2013 JCGS; Killick et al. 2012 JASA; Weinmann-Storath-Demaret, L1-Potts, SIAM
  J. Numer. Anal., and Potts for manifold-valued data, JMIV 2016; the arXiv:1304.4373 preprint explicitly
  treats circle-valued signals).
- IMPACT: correctness. Distinguishes cleanly what the shipped code conflates: the true target is L0
  (reversal count), the convex surrogate is TV/trend-filtering (D13-4), and there is an EXACT DP for the L0
  target when a fidelity term exists. It also flags the circle-valued subtlety and its published fix.
- PROPOSAL: for the SHIPPED post-pass, use the convex TV surrogate (cheap, D13-4) with the L0 count as the
  accept-gate (as the face-walk already does at `:210-211`). Reserve the exact L0 DP (Johnson/PELT) for a
  reference "reversal-minimal feasible path" per capture in Stage E, to A/B the surrogate against, per SPEC
  section 7's smoothing open item. If wrap ever bites (`MAX_ABS_GF` regimes, A10), switch the metric to the
  circle-valued Potts form rather than hand-patching wrapDelta.
- CONFIDENCE: 0.85
- DEPENDS-ON: D13-1, A08-2, A08-4.

---

## Section 2: LATEST (2023-2026) methods

### D13-6: Recent trend-filtering / change-point work generalizes the same TV core; nothing overturns the O(n) exact-1D result, and one 2024 result gives a min/max robustification worth noting
- LOCATION: research topic.
- CLAIM: 2023-2026 activity is generalization and application, not a replacement of the taut-string/DP core:
  - Chatterjee, "Minmax Trend Filtering: Generalizations of Total Variation Denoising via a Local Minmax/Maxmin
    Formula" (arXiv:2410.03041, Oct 2024, rev. 2026): a local min/max characterization of TV/trend-filtering
    estimators, giving pointwise robustness and a unifying view of the whole TV family. Relevant only as
    theory (confirms our surrogate sits in a well-understood family); not a new solver we need.
  - DTF-net / "Dynamic Trend Filtering through Trend Point Detection with RL" (arXiv:2406.03665, 2024/2025):
    argues classic TV OVER-smooths abrupt changes and adds a detection stage to PRESERVE them. This is a
    warning directly on-point for us: our load-bearing dither/redirect ticks are exactly the "abrupt changes"
    a naive TV would flatten, which is why our smoothing MUST be constrained by byte-exact feasibility and a
    give-back cap, not a free denoiser (D13-8).
  - GNSS-monitoring TV filtering (Measurement, 2025) and moving-sum change-point under piecewise linearity
    (arXiv:2208.04900): applications; no new core.
- EVIDENCE: ESTABLISHED (arXiv/journal listings fetched this session). Applicability is MODEL-ANCHORED
  (the "TV over-smooths abrupt change" caution maps onto our load-bearing dither, context-pack section 5).
- IMPACT: robustness. The recent literature's main lesson for us is a caution (don't let TV eat the
  load-bearing redirects), not a new algorithm to adopt.
- PROPOSAL: keep the exact 1D solvers from D13-2 as the engine; treat the recent "preserve abrupt change"
  results as the design constraint that our feasibility walls + give-back cap already encode.
- CONFIDENCE: 0.8
- DEPENDS-ON: D13-1, D13-8.

---

## Section 3: APPLICABILITY to OUR model (the three framings the task asks to adjudicate)

### D13-7: (framing a) The residual tie-break IS a trend-filtering/change-point problem, but over 1-4 ticks it is DEGENERATE-SMALL; direct enumeration dominates a taut-string call there
- LOCATION: SPEC 4.2, 4.5; Stage 0 measured residual dimension.
- CLAIM: SPEC 4.5 reframes smoothing as "among the feasible direction assignments for the degenerate ticks,
  choose the smoothest." Stage 0 measured the degenerate (vanishing-costate) set at 0 ticks on single/easy,
  1 on j021/loopmm, up to 4 on j008b (SPEC 4.2, COPT-measured). At that size the "smoothest feasible yaw
  sequence" selection is a change-point/TV problem in PRINCIPLE but a TINY combinatorial one in PRACTICE:
  1-4 free directions, each choosing which feasible arc endpoint to sit on. The O(n) taut-string / Johnson DP
  is the right tool for a LONG signal; for 1-4 free variables embedded in an otherwise costate-fixed
  (already-smooth) sequence, direct enumeration of the few feasible-face vertices with the reversal-count
  L0 tie-break (D13-5) is simpler, exact, and faster than invoking a TV solver. The non-degenerate ticks are
  `u* = m g/|g|` (SPEC 4.2), already monotone/smooth, so there is nothing for a full-sequence TV pass to do
  there.
- EVIDENCE: MODEL-ANCHORED to Stage 0's measured residual dimension (0-4) and the costate-determined-tick
  smoothness (SPEC 4.2). The "enumeration beats TV at n<=4" claim is UNMEASURED-HYPOTHESIS pending a Stage E
  timing, but is near-certain on complexity grounds.
- IMPACT: simplicity. Inside ARCH-1 (the reduction), smoothing is NOT a TV/taut-string post-pass; it is a
  reversal-count tie-break folded into the residual solve, i.e. one of the "enumeration / null-space" choices
  SPEC 4.3 already lists. The TV machinery is overkill THERE.
- PROPOSAL: in ARCH-1, make the residual solver return, among the feasible degenerate-tick assignments, the
  one minimizing the L0 reversal count then `||D2 theta||_1` (D13-5 tie-break), by enumeration. No taut-string
  needed on the reduction path.
- CONFIDENCE: 0.8
- DEPENDS-ON: D13-1, D13-5, SPEC 4.2/4.3/4.5.

### D13-8: (framing a, caveat) The FULL-sequence smoothing the SHIPPED path does is a CONSTRAINED TV problem; the exact O(n) solvers apply only to the UNCONSTRAINED convex seed, the byte-exact walls force a local-search repair
- LOCATION: SPEC 4.5, A08-7 (the u-space obstruction), context-pack section 5 (the byte-exact dither).
- CLAIM: two facts break the clean "just run taut-string" hope and must be stated honestly:
  1. NON-CONVEX WALLS IN THETA: the positional/velocity walls are LINEAR in `u` but TRIG (nonconvex,
     nonsmooth) in `theta` (A08-7). The exact 1D TV solvers (D13-2) solve the UNCONSTRAINED
     `min TV(D theta) + fidelity`. With the byte-exact walls the problem is `min TV(D2 theta)` s.t.
     nonconvex walls, so the taut-string result is only an ideal SEED / inner convex solve; a
     projection or trust-region local search must repair it back onto the feasible face (this is what
     `SmoothFaceRecovery`'s Gauss-Newton restore under the second-difference metric `W` already approximates,
     A08-4). So the honest role of the exact TV solver is "compute the target the local search aims at," not
     "solve the problem."
  2. THE DITHER IS NOT NOISE: context-pack section 5 measured that the byte-exact max-X path DITHERS at
     redirect/seam zones because it hugs opposing corridors on the 65536-bucket grid; that dither costs
     sub-micron X per reversal but is LOAD-BEARING for the last microns. A free TV denoiser would flatten it
     and LOSE feasibility/objective. This is exactly the "TV over-smooths abrupt change" failure mode
     (D13-6). The give-back cap (below) plus byte-exact feasibility are what forbid that.
- EVIDENCE: MODEL-ANCHORED: A08-7 (convex-in-theta-not-u, measured metric decomposition), context-pack
  section 5 (dither is load-bearing, ~sub-micron per reversal, measured last session, re-verify tag carried).
- IMPACT: correctness. Prevents the naive collapse "smoothing = one taut-string pass," which would regress
  feasibility. The exact solver is a SEED, not the mechanism.
- PROPOSAL: keep the smoothing as a give-back-CONSTRAINED local search whose convex inner target is the
  order-1 trend filter (taut-string on D2), and whose accept-gate is byte-exact feasibility + L0 reversal
  count. One mechanism, but explicitly a constrained one.
- CONFIDENCE: 0.85
- DEPENDS-ON: A08-7, D13-2, D13-6.

### D13-9: (framing b) The joint Riemannian objective+gamma*jerk descent is elegant but DOMINATED: it does NOT restore convexity, destroys LCvx tightness, and has a worse pure-Java port than the taut-string seed
- LOCATION: SPEC 4.5 (obstruction), A08-7, sibling family D05 (theta-manifold descent).
- CLAIM: writing one smooth objective `d^T p(theta) + gamma ||D2 theta||_2^2` on the product-of-circles
  manifold and running Riemannian descent (D05's territory) does optimize objective and smoothness jointly in
  a single primitive, which is attractive. BUT:
  - It provides NO convexity/tightness benefit. A08-7 measured that any theta-smoothness term is nonconvex in
    `u` and destroys the LCvx constant-modulus tightness that makes `u* = m g/|g|` exact (SPEC 4.1). So the
    joint objective can only be a nonconvex LOCAL search, exactly like the constrained post-pass (D13-8), with
    no global guarantee that the residual reduction (ARCH-1) does give.
  - The `gamma ||D2 theta||_2^2` term is the L2 (min-jerk) surrogate, which A08-6 MEASURED as reversal-blind.
    To be reversal-sensitive it must be `||D2 theta||_1`, which is nonsmooth, so the "single smooth objective"
    selling point evaporates (subgradient / proximal needed), landing back at D13-8's constrained TV.
  - Port cost: there is no pure-Java Riemannian toolbox in the repo; a manifold line-search + retraction on
    the product of circles is more code than a taut-string seed + Gauss-Newton restore (which already exists
    in `SmoothFaceRecovery`).
- EVIDENCE: MODEL-ANCHORED (A08-6 measured L2 blindness; A08-7 measured LCvx destruction; SPEC 4.1 LCvx
  statement). Port-cost comparison is UNMEASURED-HYPOTHESIS but follows from repo contents.
- IMPACT: simplicity. Rules OUT framing (b) as the primary mechanism; it buys elegance but no tightness and a
  harder port, and forces the wrong (L2) or nonsmooth (L1) metric choice.
- PROPOSAL: do not adopt joint Riemannian objective+jerk as the smoothing mechanism. If D05's manifold descent
  is adopted for the PRIMARY solve for other reasons, smoothing still enters as the tie-break/constrained-TV
  layer (D13-7/D13-8), not by baking gamma*jerk into the primary objective.
- CONFIDENCE: 0.72
- DEPENDS-ON: A08-6, A08-7, D13-4, D13-8; sibling D05.

### D13-10: (framing c) The give-back budget as a hard constraint obj >= best - X IS the standard epsilon-constraint reformulation; it is clean, Pareto-correct, and fixes the F6 double-count
- LOCATION: SPEC C4, F6/A08-10, A08-11.
- CLAIM: "maximum objective sacrificed for smoothness" expressed as the constraint `obj >= best - X` with
  smoothness as the objective is exactly the epsilon-constraint method for bi-objective optimization
  (Haimes-Lasdon-Wismer 1971): fix the primary objective as a constraint at its best-minus-slack level, then
  optimize the secondary. It is the textbook-correct way to trade a bounded slice of a primary objective for
  a secondary, and unlike a weighted sum it works on NONCONVEX Pareto fronts (relevant, since our feasible set
  is nonconvex in theta). This is the clean fix for F6/A08-10 (each shipped pass floors against its OWN input,
  stacking give-back to ~1.63e-2 b): a single shared reference `best` (the pre-smoothing objective) inside one
  epsilon-constraint makes the total give-back EXACTLY X. It also fixes A08-11 (turnCost is a soft
  `obj - lambda*turnCost` bias at ~8 ranking sites with no choke point): converting it to the hard constraint
  gives the single choke point.
- EVIDENCE: ESTABLISHED (Haimes et al. 1971 epsilon-constraint; augmented epsilon-constraint for strict
  Pareto-optimality). The double-count is MEASURED (A08-10: 8e-3 + 8e-3 + 3e-4 = 1.63e-2 b, ~160x the ~1e-4 b
  certify floor).
- IMPACT: correctness/robustness (removes the uncontrolled ~1.6e-2 b give-back) and simplicity (one budget X,
  one reference, one choke point). Directly serves SPEC C4's "must not double-count the give-back."
- PROPOSAL: replace the three per-pass `MAX_GIVE_BACK`/`SMOOTH_OBJ_SLACK` floors and the soft turnCost bias
  with ONE epsilon-constraint `obj >= originalObjective - X` threaded through the smoothing search, with the
  L0/TV reversal metric as the pure secondary objective. X becomes the single "smoothness slider."
- CONFIDENCE: 0.88
- DEPENDS-ON: F6, A08-10, A08-11.

### D13-11: The reversal count also has a control-theory identity (maximum hands-off / L0 sparse control), whose L1-optimality theorem is the same TV-surrogate justification
- LOCATION: research topic.
- CLAIM: "minimize the number of turn-DIRECTION changes" is structurally the maximum-hands-off (L0 sparse)
  control problem (Nagahara-Quevedo-Nesic): minimize the L0 measure of a control signal, whose convex L1
  relaxation is exact under a normality/bang-off-bang condition. The analogy is not perfect (their sparsity is
  on the control's SUPPORT, ours is on the SIGN-CHANGE set of the turn rate), but it supplies the same lesson
  the trend-filtering literature does from the control side: the L0 turn-minimal object has a principled L1
  convex surrogate that is exact or near-exact under structural conditions, and the optimizer is naturally
  piecewise-constant/bang-off-bang, i.e. long straightaways separated by sharp redirects, which is exactly the
  human-strat "few reversals" aesthetic (project memory: jump classes, easiness = minimize input changes).
- EVIDENCE: ESTABLISHED (Nagahara-Quevedo-Nesic, Maximum Hands-Off Control; arXiv:1307.8232, arXiv:1408.3025).
  The mapping to our sign-change set is MODEL-ANCHORED / analogy, not measured.
- IMPACT: robustness (cross-validates the L1-TV surrogate choice from a second field) and simplicity (one more
  reason the piecewise-linear/constant heading is the right target shape).
- PROPOSAL: cite as corroboration only; no new mechanism. It reinforces D13-4/D13-10, not a separate route.
- CONFIDENCE: 0.65
- DEPENDS-ON: D13-4.

---

## Section 4: PORT feasibility

### D13-12: Every tool in the recommended stack is dependency-free pure-Java at trivial cost; the residual enumeration is tiny, the taut-string seed is O(n) with n<=353
- LOCATION: port target `core/.../anglesolver/solver/`.
- CLAIM: cost accounting for the recommended one-mechanism smoothing:
  - Exact 1D L1-TV / order-1 trend-filter seed (taut-string or Johnson DP): O(n), ~60-120 lines, no dep,
    microseconds at n<=353. ESTABLISHED (D13-2).
  - Residual L0 tie-break (ARCH-1 path): enumeration over 0-4 free degenerate ticks x their feasible-face
    vertices; a handful of forward evaluations; already within the existing `SmoothFaceRecovery` cost class.
    MODEL-ANCHORED to Stage 0's measured residual dim 0-4.
  - Epsilon-constraint give-back: one extra linear row `obj >= best - X` in the byte-exact wall set; zero new
    machinery (walls already compiled by `JumpLinearModel.compileWall`). ESTABLISHED-trivial.
  - The byte-exact repair (Gauss-Newton restore under the `W` metric) ALREADY EXISTS in
    `SmoothFaceRecovery.java`; the collapse REUSES it, replacing three metric functions with one
    (`||D2 theta||_1`) and three give-back floors with one epsilon-constraint. Net LOC change is negative.
- EVIDENCE: ESTABLISHED (Condat/Johnson O(n)); MODEL-ANCHORED (residual dim 0-4, Stage 0). Actual wall-clock
  is UNMEASURED-HYPOTHESIS pending Stage E, but every piece is below the current smoothing budget
  (`deadline/8`, cap 6s, floor 400ms; A08-4).
- IMPACT: port feasibility (green). No dependency, negative LOC, within envelope.
- PROPOSAL: implement the collapse as an edit to the existing smoothing stage, not a new subsystem.
- CONFIDENCE: 0.85
- DEPENDS-ON: D13-2, D13-7, D13-10.

---

## Section 5: VERDICT (the cleanest ONE-mechanism smoothing) and SPEC C4 mapping

### D13-13: VERDICT - one mechanism at two layers: (iii) a give-back-constrained order-1 trend-filter post-pass on the incumbent path, specializing to (i) an enumerated L0 residual tie-break on the ARCH-1 reduction path; framing (ii) Riemannian joint descent is rejected
- LOCATION: SPEC C4, section 4.5, section 6 (ARCH-1/ARCH-2).
- CLAIM: the three candidate framings are NOT peers at the same layer, and conflating them is the error to
  avoid. The clean answer:
  - PRIMARY (maps to SPEC C4 "ONE smoothness mechanism against ONE shared pre-smoothing reference"):
    a SINGLE give-back-constrained smoothing pass = minimize the order-1 trend-filter reversal surrogate
    `||D2 theta||_1` (D13-4) with the L0 reversal count as accept-gate (D13-5), subject to the byte-exact walls
    AND ONE epsilon-constraint `obj >= best - X` (D13-10, framing c), using an exact taut-string/DP seed
    (D13-2) repaired by the EXISTING Gauss-Newton restore (D13-8). This is framing (iii) done right, and it
    REPLACES all four shipped stages (turnCost bias, DeWiggle, SmoothingPolish, SmoothFaceRecovery-metric) with
    one objective, one budget, one reference. It fixes F3 (four metrics -> one), F6/A08-10 (stacked give-back
    -> single X), and A08-9 (drops the reversal-blind L2 metric).
  - SPECIALIZATION on the reduction path (framing a): when ARCH-1 is used, the non-degenerate ticks are
    costate-fixed and already smooth, so smoothing collapses further to an ENUMERATED L0 tie-break over the
    1-4 degenerate ticks (D13-7); the taut-string is unnecessary there. Same objective (`min reversals then
    ||D2 theta||_1`), smaller instance.
  - REJECTED (framing b): joint Riemannian objective+gamma*jerk descent (D13-9) buys no convexity/tightness,
    forces the wrong (L2, reversal-blind) or nonsmooth (L1) metric, and has a worse pure-Java port. Do not bake
    gamma*jerk into the primary objective.
  So the ONE mechanism is: "constrained minimum-reversal trend filtering, give-back bounded by an
  epsilon-constraint, byte-exact repaired," which degenerates to a tiny enumeration whenever the reduction has
  already fixed the smooth part.
- EVIDENCE: synthesis of D13-1..D13-12; ESTABLISHED tools + MODEL-ANCHORED reductions (A08-6/7/9/10, Stage 0
  residual dim, F3/F6). The head-to-head reversal-sum and timing A/B vs the four-pass stack is
  UNMEASURED-HYPOTHESIS, routed to Stage E per SPEC section 7's smoothing item.
- IMPACT: simplicity (4 stages -> 1), correctness (single controlled give-back X; right convex metric),
  robustness (byte-exact + budget forbid eating the load-bearing dither). Decisive for SPEC C4 and mission
  target capability 3.
- PROPOSAL (SPEC C4 mapping): amend SPEC C4 TARGET to read: "smoothness is one give-back-constrained
  objective `min ||D2 theta||_1` (order-1 trend filtering; L0 reversal count as accept-gate) under
  `obj >= best - X` (epsilon-constraint, single shared reference) and the byte-exact walls; on the ARCH-1
  reduction path it specializes to an enumerated L0 tie-break over the 0-4 degenerate ticks." Stage E
  prototype: taut-string seed + existing restore, A/B reversal sums and give-back vs the four-pass stack on
  the hpk corpus, with a Johnson/PELT exact-L0 per-capture reference for the reversal-minimal feasible path.
- CONFIDENCE: 0.8
- DEPENDS-ON: D13-1, D13-2, D13-4, D13-5, D13-7, D13-8, D13-9, D13-10, D13-12; F3, F6; SPEC 4.2/4.3/4.5/6.

---

## Section 6: Citations (all real, fetched this session)

Foundational:
- L. I. Rudin, S. Osher, E. Fatemi, "Nonlinear total variation based noise removal algorithms," Physica D 60
  (1992) 259-268. DOI 10.1016/0167-2789(92)90242-F. (ROF: origin of TV regularization.)
- P. L. Davies, A. Kovac, "Local extremes, runs, strings and multiresolution," Annals of Statistics 29(1)
  (2001) 1-65. (The taut-string algorithm; O(n) exact 1D TV.)
- L. Condat, "A Direct Algorithm for 1-D Total Variation Denoising," IEEE Signal Processing Letters 20(11)
  (2013) 1054-1057. DOI 10.1109/LSP.2013.2278339. https://lcondat.github.io/publis/Condat-fast_TV-SPL-2013.pdf
  (Non-iterative O(n) direct solver.)
- N. A. Johnson, "A Dynamic Programming Algorithm for the Fused Lasso and L0-Segmentation," Journal of
  Computational and Graphical Statistics 22(2) (2013) 246-260. DOI 10.1080/10618600.2012.681238.
  (O(n) DP for both the L1 fused lasso and the exact L0 segmentation.)
- T. Flash, N. Hogan, "The coordination of arm movements: an experimentally confirmed mathematical model,"
  Journal of Neuroscience 5(7) (1985) 1688-1703. https://www.jneurosci.org/content/5/7/1688
  (Minimum-jerk = squared-derivative L2 objective family.)
- S.-J. Kim, K. Koh, S. Boyd, D. Gorinevsky, "l1 Trend Filtering," SIAM Review 51(2) (2009) 339-360.
  DOI 10.1137/070690274. https://web.stanford.edu/~gorin/papers/l1_trend_filter.pdf
  (`||D2 x||_1` piecewise-linear trend surrogate; the exact match for our jerk/wiggle metric.)
- R. J. Tibshirani, "Adaptive piecewise polynomial estimation via trend filtering," Annals of Statistics
  42(1) (2014) 285-323. DOI 10.1214/13-AOS1189.
  (k=0 = fused lasso, k=1 = piecewise-linear; minimax-adaptive; finite-sample spline equivalence.)
- R. Killick, P. Fearnhead, I. A. Eckley, "Optimal Detection of Changepoints With a Linear Computational
  Cost" (PELT), Journal of the American Statistical Association 107(500) (2012) 1590-1598.
  DOI 10.1080/01621459.2012.737745. (Exact pruned-DP L0 change-point, expected-linear.)
- A. Weinmann, M. Storath, L. Demaret, "The L1-Potts Functional for Robust Jump-Sparse Reconstruction," SIAM
  Journal on Numerical Analysis 53(1) (2015) 644-673. https://epubs.siam.org/doi/10.1137/120896256
  and M. Storath, A. Weinmann, L. Demaret, "Jump-sparse and sparse recovery using Potts functionals,"
  arXiv:1304.4373 (2013) (treats scalar AND circle-valued signals).
- A. Weinmann, M. Storath, L. Demaret, "Mumford-Shah and Potts Regularization for Manifold-Valued Data,"
  Journal of Mathematical Imaging and Vision (2016). DOI 10.1007/s10851-015-0628-2. (Circle/manifold Potts.)

Give-back as constraint:
- Y. Y. Haimes, L. S. Lasdon, D. A. Wismer, "On a bicriterion formulation of the problems of integrated
  system identification and system optimization" (the epsilon-constraint method), IEEE Transactions on
  Systems, Man, and Cybernetics SMC-1(3) (1971) 296-297. (Primary-as-constraint bi-objective reformulation;
  augmented epsilon-constraint for strict Pareto-optimality, handles nonconvex fronts.)

Sparse/turn-minimal control corroboration:
- M. Nagahara, D. E. Quevedo, D. Nesic, "Maximum Hands-Off Control: A Paradigm of Control Effort
  Minimization," IEEE Transactions on Automatic Control 61(3) (2016) 735-747. arXiv:1408.3025; and
  "Maximum-Hands-Off Control and L1 Optimality," arXiv:1307.8232 (2013). (L0 sparse control = L1 relaxation
  exact under normality; bang-off-bang.)

Latest (2023-2026):
- S. Chatterjee, "Minmax Trend Filtering: Generalizations of Total Variation Denoising via a Local
  Minmax/Maxmin Formula," arXiv:2410.03041 (2024, rev. 2026).
- "Towards Dynamic Trend Filtering through Trend Point Detection with Reinforcement Learning" (DTF-net),
  arXiv:2406.03665 (2024/2025). (Warning: classic TV over-smooths abrupt change; preserve it.)
- (application) "Total variation filtering based GNSS deformation monitoring trend extraction," Measurement
  (2025), ScienceDirect S0263224125021633.

Riemannian (framing b, rejected as primary):
- N. Boumal, "An Introduction to Optimization on Smooth Manifolds," Cambridge University Press, 2023.
- P.-A. Absil, R. Mahony, R. Sepulchre, "Optimization Algorithms on Matrix Manifolds," Princeton, 2008.
  (Product-of-circles / oblique manifold; Manopt.)
