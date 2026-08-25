# Agent D11 shard: the DISCRETE BYTE-EXACT LAYER (CVP / lattice / sphere decoding for the sine LUT; MISDP / big-M for the gate; SMT-FP as verifier only)

AGENT: D11
TERRITORY (research, method family): the discrete byte-exact layer under the continuous constant-modulus relaxation. Two discrete sub-problems plus one verifier:
- (a) the integer sine LUT snap = closest-vector / integer-least-squares (LLL, Babai nearest-plane, Schnorr-Euchner / Fincke-Pohst sphere decoding);
- (b) the inertia gate = ~2n big-M indicator constraints = a small mixed-integer (MISOCP / MISDP / disjunctive) layer;
- (c) bit-precise SMT-FP (Bitwuzla, OptiMathSAT OMT(FP)) as a per-window VERIFIER, never a searcher.

FILES / DOCS INSPECTED (code-anchored, this session):
- Context: 00-context-pack.md (whole), SPEC.md section 4.5 + 4.6 (the separable layers + seed literature), stage0-copt/FINDINGS.md sections 4 + 5, stageA/agentA05.md (whole), stageA/agentA10.md (whole), stageA/agentA03.md (A03-12), memory reference_byte_exact_certified_solver.
- Code (re-verified this session, not from shards): core/.../anglesolver/solver/McSineTable.java (SIZE=65536, MASK=65535, INDEX_FROM_RAD=10430.378F, the `(int)(rad*10430.378) & 65535` snap and the 26.x double-scale variant), FacingLattice.java:7,12-42 (DEG_PER_BUCKET = 180/(pi*10430.378); radOf two casts; jointCellId packed 4-index cell).
- Web (real citations, WebSearch/WebFetch this session): full URL list in the CITATIONS block at the end.

RIGOR NOTE: this is a Stage D methods shard. Every applicability claim is tagged ESTABLISHED (a published result or a measured number already in this campaign's record, cited) or SPECULATION (a belief to route to Stage E prototype). No claim invents a paper. Numbers-in-blocks are quoted from Stage 0 / Stage A with their source finding id; I ran no new solves this session (COPT / gradle not invoked here), so every block figure carries its upstream measurement tag, not a fresh one.

---

## D11-1 The discrete layer is NOT a hard dense-lattice CVP; it is a per-tick scalar-integer grid, weakly coupled, of residual dimension 0 to 4

LOCATION: research topic (problem-class identification); anchored to McSineTable.java:31-33, FacingLattice.java:7, and Stage 0 FINDINGS section 1 + SPEC 4.2.
CLAIM: Each tick's only discrete freedom is one integer bucket index `b_t = (int)(yaw_t * 10430.378) & 65535` on a uniform 1-D grid of pitch DEG_PER_BUCKET = 0.0054931643 deg; the buckets couple across ticks ONLY through the linear objective and the few active linear walls, and the coupled ("residual") dimension is bounded by the Pataki rank bound r(r+1)/2 <= #active walls, MEASURED to be 0 to 4 on the whole corpus (Stage 0 FINDINGS 1a/1b: 0 throttled/degenerate ticks on single and easy multi-jump, 1 on j021/loopmm, 4 on j008b). So this is a structured, tiny, weakly-coupled integer program, not a random full-rank lattice needing LLL/BKZ reduction.
EVIDENCE: ESTABLISHED. Grid pitch and cell primitive verified in code this session (McSineTable, FacingLattice). Coupling/residual dimension is Stage 0's measured SDR rank (eig2/eig1 <= 9e-8 rank-1 on single/easy; up to 0.024 rank 2-3 on coupled) and disk-slack throttled-tick count (0 to 4), cited from FINDINGS 1a/1b. The 1-D-per-tick structure is intrinsic: yaw is a scalar per tick (context-pack section 1, "the only free decision variable is the yaw").
IMPACT: simplicity/correctness (large). Reframes method selection: the classical CVP heavy machinery (LLL/Babai basis reduction of an n-dimensional lattice) is OVERKILL. On the straightaway ticks the per-tick "basis" is effectively orthogonal at grid scale (weak coupling), so Babai nearest-plane degenerates to independent per-tick nearest-bucket rounding; only the 0 to 4 coupled/wall-hugging ticks need an actual coupled closest-point search. The correct instrument is a SMALL sphere-decoding enumeration over +-few buckets on the handful of coupled ticks, not a lattice-reduction pipeline.
PROPOSAL: adopt the framing "decoupled nearest-bucket rounding on straightaways + Schnorr-Euchner enumeration on the 0-4 coupled ticks" (D11-4); do NOT port LLL/BKZ; there is no dense random lattice here to reduce.
CONFIDENCE: 0.85
DEPENDS-ON: none

## D11-2 Foundational method map: LLL, Babai nearest-plane, Fincke-Pohst / Schnorr-Euchner sphere decoding, integer-least-squares

LOCATION: research (foundational citations for sub-problem a).
CLAIM: The certified-snap toolbox is exactly the integer-least-squares / closest-vector stack, whose primitives are (i) LLL basis reduction (preconditioning), (ii) Babai's nearest-plane / rounding (a fast, bounded-error approximate CVP), and (iii) Fincke-Pohst / Schnorr-Euchner sphere decoding (exact CVP by depth-first enumeration inside a shrinking radius).
EVIDENCE: ESTABLISHED, all published:
- LLL: A.K. Lenstra, H.W. Lenstra Jr., L. Lovász, "Factoring polynomials with rational coefficients," Mathematische Annalen 261(4):515-534, 1982 (DOI 10.1007/BF01457454). Original applications include integer linear programming in fixed dimension; the reduced basis makes rounding well-conditioned.
- Babai nearest-plane: L. Babai, "On Lovász' lattice reduction and the nearest lattice point problem," Combinatorica 6(1):1-13, 1986 (DOI 10.1007/BF02579403). Rounds one Gram-Schmidt coordinate at a time; error bound is basis-dependent, tight for near-orthogonal bases.
- Fincke-Pohst: U. Fincke, M. Pohst, "Improved methods for calculating vectors of short length in a lattice...," Mathematics of Computation 44(170):463-471, 1985. The original enumeration inside an ellipsoid.
- Schnorr-Euchner: C.P. Schnorr, M. Euchner, "Lattice basis reduction: improved practical algorithms and solving subset sum problems," Mathematical Programming 66:181-199, 1994 (DOI 10.1007/BF01581144). The zig-zag "start at the center, spiral outward, shrink the radius on each hit" refinement of Fincke-Pohst; this is the exact algorithm now called "sphere decoding."
- Closest-point-search canonical framing: E. Agrell, T. Eriksson, A. Vardy, K. Zeger, "Closest point search in lattices," IEEE Trans. Information Theory 48(8):2201-2214, 2002 (presents SE as the closest-point search, the reference implementation communications uses).
- Integer-least-squares equivalence + expected complexity: B. Hassibi, H. Vikalo, "On the sphere-decoding algorithm I. Expected complexity," IEEE Trans. Signal Processing 53(8):2806-2818, 2005. Establishes that although CVP/integer-LS is NP-hard in the worst case, the sphere decoder has POLYNOMIAL EXPECTED complexity when the target is a lattice point plus bounded noise, which is precisely our regime (the target is the continuous optimum, "noise" = the sub-bucket snap of ~1e-4 b).
IMPACT: correctness/simplicity. Names the exact algorithm class for the certified snap and supplies the complexity argument (D11-3) that it is cheap in our regime.
PROPOSAL: cite this stack as the method basis for the LatticeSnap primitive (A10-11).
CONFIDENCE: 0.95
DEPENDS-ON: D11-1

## D11-3 Sphere decoding is the RIGHT certified snap for the LUT, and it is norm>1-aware where Babai rounding is not

LOCATION: research applicability (task 3a); anchored to A10-1, A10-6, Stage 0 FINDINGS section 4, FacingLattice.java (cellRepresentatives).
CLAIM: The snap problem "given the continuous optimum yaws, choose the 65536-grid buckets that MAXIMIZE the friction-coupled byte-exact objective subject to the byte-exact walls" is a closest-vector / integer-LS problem in the friction-coupled metric, and Schnorr-Euchner enumeration over +-few buckets/tick on the coupled ticks is the correct certified solver BECAUSE the snap is NOT monotone-loss: the half-angle norm>1 effect lets a bucket OUT-reach the continuous unit-modulus point, so a distance-minimizing Babai round can leave reach on the table that an objective-scoring enumeration recovers.
EVIDENCE:
- ESTABLISHED (measured, this campaign): the snap is small and the grid is known. A10-1: one objective-tick bucket = 1.543e-4 b on a flat jump; accumulated snap floor ~1e-4 b. A10-6: half-angle unit-modulus error peaks at 9.594e-5 (gf=135.27 deg), worth ~1.544e-4 b of EXTRA reach per favorable tick, and Stage 0 FINDINGS 4 measured the accumulated byte-exact OVER-reach at +3.4e-3 b (j005), +1.0e-2 b (j019) over the continuous optimum. So byte-exact certification can GAIN up to ~1e-2 b; the snap is a maximization, not a rounding.
- ESTABLISHED (algorithmic): SE (Schnorr-Euchner 1994) enumerates lattice points inside a radius and keeps the best under an arbitrary per-point score; scoring each candidate through the real byte-exact model (ExactJumpModel) captures the norm>1 bonus that Babai's pure nearest-plane (Babai 1986, minimizes lattice distance) cannot see.
- ESTABLISHED (primitive exists): FacingLattice.cellRepresentatives (A10-2) already enumerates every distinct byte-exact cell in a +-window by float bisection and returns one representative float per cell; this is a ready per-tick candidate generator for an SE search. The coupled search over the 0-4 residual ticks (D11-1) is the small outer enumeration.
- ESTABLISHED (the incumbent has NO explicit certified snap): A03-12 + A10-3 measured LatticeRepair is DEAD (only test screens call it; grep of core/src/main returns zero live callers); the shipped continuous->discrete snap is done implicitly by SLP + inward-margin ladder + byte-exact verify (A10-11), which is neither certified nor norm>1-seeking.
IMPACT: correctness (recovers up to ~1e-2 b of half-angle reach that naive rounding misses; Stage 0 FINDINGS 4) + simplicity (one certified primitive replaces dead LatticeRepair + the implicit SLP/margin snap). The expected-complexity result (Hassibi-Vikalo, D11-2) says the enumeration is cheap in our "lattice-point-plus-small-noise" regime.
PROPOSAL: build LatticeSnap = per-tick Babai nearest-bucket on straightaway (costate-determined) ticks + SE enumeration over the 0-4 coupled ticks, scoring real byte-exact objective + wall slack (so it prefers norm>1 cells only when feasible), delete LatticeRepair. VALIDATE against the COPT integer/global optimum per capture (Stage E) before replacing the incumbent snap.
CONFIDENCE: 0.75
DEPENDS-ON: D11-1, D11-2

## D11-4 Port feasibility of sphere decoding: pure-Java, LOW effort, no dependency (reuses FacingLattice + ExactJumpModel.stepRange)

LOCATION: research (task 4, port estimate); anchored to FacingLattice.java, ExactJumpModel.stepRange (A10-12).
CLAIM: Schnorr-Euchner enumeration is a depth-first recursion over integer coordinates with a running radius/incumbent bound; it ports to pure Java in roughly 100 to 200 LOC because the per-tick cell generator (FacingLattice.cellRepresentatives) and the incremental byte-exact scorer (ExactJumpModel.stepRange, recomputes only the affected tail from tick `from`) already exist. No external library, no numeric dependency, no loader packaging cost.
EVIDENCE:
- ESTABLISHED (algorithm shape): SE is textbook depth-first with a scalar bound (Schnorr-Euchner 1994; Agrell et al. 2002 give reference pseudocode). Over the 0-4 coupled ticks with +-k buckets each, the search tree has O((2k+1)^d), d<=4, a few hundred leaves at k=3, d=4.
- ESTABLISHED (primitives in-repo): FacingLattice enumerates cells by exact float bisection (A10-2, verified this session); ExactJumpModel.stepRange gives the incremental forward the enumeration needs (A10-12 notes WrapWindowIls does NOT currently use stepRange, so this is also a latent speedup lever). Both are Java 8, MC-free, already on the core path.
- ESTABLISHED (dependency policy): SPEC section 5 invariants prefer dependency-free / pure-analytical; a self-contained SE loop satisfies it with zero packaging cost across Forge 1.8.9/1.12.2 shade + Fabric include.
IMPACT: simplicity/speed. Delivers a certified snap with no dependency and reuses the incremental cache the biggest inner loop currently skips.
PROPOSAL: implement SE as a small test-only prototype first (core/src/test screen), benchmark leaf count + wall-clock + byte-exact residual vs the COPT integer optimum on j021/j008b/loopmm/f2f, then promote if it matches COPT within the 1-bucket budget and beats the implicit snap.
CONFIDENCE: 0.8
DEPENDS-ON: D11-2, D11-3

## D11-5 The inertia gate is a textbook big-M / disjunctive indicator system; the bespoke suffix-pattern B&B is an INCOMPLETE enumeration of that system's disjunctions

LOCATION: research applicability (task 3b); anchored to A05-1/5/6/9, A10-4/5, JumpLinearModel.velocityWalls/keepAliveWall/zeroingPattern, BoundPrunedRecovery.
CLAIM: The gate "if |v_axis * 0.91| < thr then that axis carry is zeroed" is a per-(tick,axis) binary indicator z_{t,a}; velocityWalls already encodes the z=1 (band-in) half-space and keepAliveWall the z=0 (band-out) half-space (a 2-term disjunction, Balas), with the position map affine once the z's are fixed (friction propagation cut at zeroed ticks). The shipped BoundPrunedRecovery does NOT solve this indicator system; it ENUMERATES a fixed STRUCTURED SUBSET of its disjunctions (free + per-axis suffix zx@k/zz@k + single-tick zx1@k + combined + <=4 keep-alive), which A05-5 measured is INCOMPLETE (multi-tick interior zeroings and off-objective keep-alive are outside the family; restore capped at 45 iters; bounds F-blind), and A05-6 measured its per-pattern bounds are only CONDITIONAL upper bounds (a union of disjunct bounds), so its null is NOT an infeasibility certificate (F10).
EVIDENCE: ESTABLISHED.
- Disjunctive/big-M theory: E. Balas, "Disjunctive programming," Annals of Discrete Mathematics 5:3-51, 1979 (and Springer 2018 book); an indicator constraint is a 2-term disjunction; big-M uses one binary per disjunct, convex-hull is tighter but larger (Kronqvist et al., "P-split formulations," Math. Programming 2025, for the modern intermediate tradeoff).
- Our gate structure: A05-1/9 + A10-5 (verified in shards this session): velocityWalls = band-in rows, keepAliveWall = band-out row, zeroingPattern folds the friction cut. Binary per (tick,axis).
- Incompleteness + false-negative: A05-5 (three coded incompleteness sources), A05-6 (conditional bounds), SPEC F10 (BnB null routed straight to "no solution"). A10-4 measured the gate fires destructively on only 0 to 1 fed ticks and the gate-critical band membership set is cheaply computable (WrapWindowIls.gateCriticalTicks, band [thr/4, 4*thr]).
IMPACT: correctness (a real infeasibility certificate would fix F10, the single most dangerous use of this stage per A05-5) + simplicity (one indicator model replaces the pattern enumeration + keep-alive nomination + per-pattern restore + nested SLP, A05-7/8).
PROPOSAL: replace the fixed suffix-pattern family with branching directly on the gate-critical binaries z_{t,a} (band-in vs band-out disjunction), which is complete over the reachable indicator lattice and yields a real infeasibility certificate (D11-6).
CONFIDENCE: 0.85
DEPENDS-ON: D11-1

## D11-6 A small MISOCP / indicator B&B is the clean gate replacement, but only as a HYBRID: banded fast path + MIP on cold miss (the modulus nonconvexity is the risk)

LOCATION: research applicability (task 3b verdict); anchored to A05-3/9, A10-4, Stage 0 FINDINGS section 5.
CLAIM: Branching on the handful of gate-critical binaries (A10-4: typically 0 to 1 fired ticks, band-critical set a few ticks) over the disk-relaxed constant-modulus core gives ONE global bound over the WHOLE gated feasible set (vs A05-6's union of conditional bounds) and CERTIFIES infeasibility, which is the clean replacement for the bespoke B&B. BUT the residual per-tick modulus nonconvexity is orthogonal and, if folded into a full MIQCP, can dominate and make the MIP slower than the microsecond banded closed-form path that already lands most gate jumps (A05-3), so the honest design is HYBRID: keep the banded closed-form fast path when the gate-critical set is empty/tiny, invoke the small indicator MIP ONLY on cold miss, and use the MIP's bound as the infeasibility certificate.
EVIDENCE:
- ESTABLISHED (the fast path already wins): A05-3 measured dsf-neo solved by the banded incumbent (ClosedFormSolve.optimizeWithPattern) at 83 ms with ZERO tree nodes; loopmm's first incumbent likewise. The tree is often dead weight.
- ESTABLISHED (the binary count is tiny): A10-4 measured 0 destructive/critical fires on monotone and reversal flat sprint-jumps, 1 destructive + 3 critical on a decay-coast; the effective branching set is the coasting ticks, not 2n.
- ESTABLISHED (COPT models it exactly): Stage 0 FINDINGS 5 + SPEC 4.5: COPT addGenConstrIndicator / big-M models the gate; Stage E prototypes the MISOCP. This is the research-oracle lens, not a shipped dependency.
- SPECULATION (the discriminator, route to Stage E): does a single small indicator MIP land loopmm-3jump and dsf-neo at the byte-exact objective AND certify infeasibility where BnB returns null, within the ~0.1 to 800 ms envelope? Experiment (A05-9, restated): export loopmm/dsf-neo via StructureDump, build the indicator model in COPT (disk-relaxed core + gate indicators, modulus as a separate residual per SPEC 4.2), compare node count / wall-clock / feasibility-completeness against A05-2's trace numbers (loopmm 9 patterns x ~500-5000 nodes over 48 s; dsf 4 patterns, 0 tree nodes). Tag: UNMEASURED-HYPOTHESIS.
IMPACT: correctness (real infeasibility certificate, fixes F10) + simplicity (deletes pattern enumeration, keep-alive nomination heuristic, nested SLP) with a measured speed RISK on the easy cases that the hybrid guards against.
PROPOSAL: prototype the indicator MISOCP in COPT (Stage E) before any commit; ship a HYBRID (ARCH-2's "banded fast path + small MIP on cold miss") so A05-3's microsecond path is preserved and A05-5's false-negative is fixed. Do NOT ship a general MISDP/MISOCP solver dependency (D11-7).
CONFIDENCE: 0.6
DEPENDS-ON: D11-5

## D11-7 MISDP / MISOCP solvers are the RESEARCH-ORACLE lens, not a shipped dependency; the shipped gate MIP is a bespoke tiny B&B (pure Java, MEDIUM effort)

LOCATION: research (task 4, port estimate for the gate); anchored to SPEC section 5 invariants, A05 (existing tree), Stage 0 section 6.
CLAIM: The general mixed-integer conic machinery (MISDP branch-and-cut) is the right tool to PROTOTYPE and REFERENCE the gate in COPT, but it is far too heavy to ship; the shipped replacement is a bespoke B&B over the few gate-critical binaries, each leaf a folded affine model bounded by the existing CostateDualSolver, which is a REFACTOR of BoundPrunedRecovery (the tree already exists), not new external machinery. Pure Java, no dependency, MEDIUM effort.
EVIDENCE:
- ESTABLISHED (the general solvers, for the oracle/reference lens): T. Gally, M.E. Pfetsch, S. Ulbrich, "A framework for solving mixed-integer semidefinite programs," Optimization Methods and Software 33(3):594-632, 2018 (the SCIP-SDP branch-and-bound framework; source at opt.tu-darmstadt.de/scipsdp). Recent branch-and-cut advances: "A Branch and Cut Solver for Mixed-Integer Semidefinite Programming: New Sparse Cutting Planes" (AIChE 2024); symmetry handling in MISDP (Hojny-Pfetsch et al., Springer 2023). These are C/C++ solvers of thousands of LOC, NOT portable to a MC-free Java 8 core within the packaging invariant (SPEC section 5; A04-7 already measured re-adding an LP library is net-negative).
- ESTABLISHED (the shipped shape): the B&B tree, per-pattern folded dual bound, and byte-exact incumbent gate already exist in BoundPrunedRecovery (A05-1/6); the change is (i) branch on the binary z_{t,a} band-in/band-out disjunction instead of the fixed suffix family (completeness), (ii) emit an infeasibility certificate when every branch's bound falls below feasibility. No new dependency.
IMPACT: simplicity/correctness. Keeps the dependency-free invariant while fixing completeness; MISDP/COPT stay strictly research-side.
PROPOSAL: model the gate as a MISOCP in COPT for the Stage E reference and completeness check; port only the bespoke small-binary B&B to the shipped path.
CONFIDENCE: 0.75
DEPENDS-ON: D11-5, D11-6

## D11-8 SMT-FP (Bitwuzla, OptiMathSAT OMT(FP)) is a per-window VERIFIER only, never a searcher and never shipped

LOCATION: research (task 3c); anchored to memory reference_byte_exact_certified_solver, SPEC section 5 non-goals.
CLAIM: Bit-precise SMT over floating-point can CERTIFY a window (prove a given facing is byte-exact feasible, or prove UNSAT that no better facing exists in a band) but it CANNOT SEARCH the real 65536^n facing space; it is an optional EXTERNAL, out-of-tree verifier (like COPT), never on the shipped path and never a route finder.
EVIDENCE:
- ESTABLISHED (measured, this campaign's memory): reference_byte_exact_certified_solver's decisive test: 3 airborne ticks each FREE over the full 65536 range returned `unknown` after 300 s (Bitwuzla 5-min cap), while the SAME 3 ticks in a +-0.02 deg tube around the proven yaws solved in ~0 s. SMT-FP re-derives, it does not discover. Z3 dies at 4 ticks / first binding wall; Bitwuzla is the FP-capable engine that scales furthest, and even it stalls at hundreds of vars because bit-blasting one fp32 multiply is ~1e6 SAT vars.
- ESTABLISHED (the tools):
  - A. Niemetz, M. Preiner, "Bitwuzla," CAV 2023, LNCS 13965, pp. 3-17 (CAV Distinguished Paper); SMT for fixed-size bit-vectors, floating-point (via SymFPU bit-vector encodings), arrays, UF.
  - P. Trentin, R. Sebastiani, "Optimization Modulo the Theory of Floating-Point Numbers," CADE 2019; extended as "Optimization Modulo the Theories of Signed Bit-Vectors and Floating-Point Numbers," J. Automated Reasoning 65:1071-1109, 2021 (arXiv 1905.02838). OptiMathSAT OMT(FP) is the ONLY tool that fuses exact-FP feasibility with optimization, but it is single-objective on single SMT queries and stalls at hundreds of FP vars. A 2025 hybrid method (MDPI Mathematics 14(8):1381) improves OMT(FP) but does not change the scaling verdict.
- ESTABLISHED (our verifier already exists): ExactJumpModel is a byte-exact in-repo replica (8.9e-16 vs the live entity, memory), and every solver already certifies against it. SMT-FP's ONLY durable niche beyond ExactJumpModel is a formally-certified UNSAT (a proof that a window admits no better facing), which ExactJumpModel (a forward evaluator) cannot produce; even that is band-local, not global (memory: "sat = globally sound; unsat/optimum = band-local").
IMPACT: correctness/scope. Confirms SMT-FP stays a NON-GOAL as a searcher (SPEC section 5) and is at most an optional out-of-tree certifier; do not resurrect "solve from t1 with SMT/global."
PROPOSAL: keep SMT-FP entirely research-side (like COPT); if a formally-certified per-window UNSAT is ever wanted, wire Bitwuzla as an out-of-tree checker fed a candidate + band, never on the shipped classpath.
CONFIDENCE: 0.95
DEPENDS-ON: none

## D11-9 The directly analogous published problem is discrete-phase constant-modulus beamforming (quantized phase = the same sine-LUT snap)

LOCATION: research (external analogue for sub-problem a); anchored to SPEC 4.6 (constant-modulus / UQP thread).
CLAIM: Our sine-LUT snap of a constant-modulus (fixed-thrust) input to a discrete angular grid is the SAME structure as discrete-phase / quantized-phase-shifter beamforming (RIS, hybrid MIMO): a constant-modulus vector whose phase is restricted to a finite grid, optimized against a linear/quadratic objective; that literature's near-optimal linear-complexity quantizers are direct evidence the snap is cheap and near-closed-form on the decoupled part.
EVIDENCE: ESTABLISHED.
- Discrete-phase constant modulus: the RIS discrete-phase-shift line, e.g. "Quantized Phase Alignment by Discrete Phase Shifts for Reconfigurable Intelligent Surface-Assisted Communication Systems" (arXiv 2303.13046, 2023): dynamic-threshold phase quantization (DTPQ) computes the OPTIMAL discrete phase shifts in LINEAR complexity, and the degrees of freedom of the optimal discrete solution scale LINEARLY (not exponentially) with element count. That is the beamforming statement of D11-1's "weakly coupled, low residual dimension, decoupled rounding on straightaways."
- Constant-modulus / UQP objective class (already in SPEC 4.6): M. Soltanalian, P. Stoica, "Designing unimodular codes via quadratic optimization," IEEE TSP 62(5):1221-1234, 2014.
- Continuous constant-modulus / integer-LS bridge: the MIMO detection stack (Hassibi-Vikalo, D11-2) is the standard method transfer for the coupled part.
IMPACT: simplicity. Supplies a mature external method family (quantized-phase beamforming + integer-LS MIMO detection) whose "linear-complexity optimal discrete quantizer + sphere decode the coupled residual" pattern maps 1:1 onto D11-3/D11-4, reducing prototype risk.
PROPOSAL: mine DTPQ-style linear quantizers for the decoupled straightaway snap and SE / fixed-complexity sphere decoders for the coupled residual; cite as prior art in the Stage E prototype.
CONFIDENCE: 0.7
DEPENDS-ON: D11-1, D11-2, D11-3

## D11-10 VERDICT (the three method calls, with scope and the measurement that closes each)

LOCATION: research synthesis (task 5).
CLAIM: (a) sphere decoding for the LUT snap: YES, scoped to a small SE enumeration over the 0-4 coupled ticks with decoupled nearest-bucket rounding on straightaways, scoring real byte-exact objective so it captures the norm>1 gain; it replaces the dead LatticeRepair and the implicit SLP/margin snap. (b) small-MIP / indicator branching for the gate: YES, branching on the gate-critical binaries (band-in/band-out disjunction) for a single global bound and a REAL infeasibility certificate (fixing F10), shipped as a HYBRID (banded fast path + MIP only on cold miss) to preserve A05-3's microsecond path. (c) SMT-FP: verifier ONLY, out-of-tree, never shipped, never a searcher.
EVIDENCE: synthesis of D11-1..D11-9 (each ESTABLISHED/SPECULATION-tagged above). The three closing measurements for Stage E:
- Snap: SE leaf count + wall-clock + byte-exact residual vs the COPT integer/global optimum per capture; must match within the 1-bucket budget (1.5e-4 b, A10-1) and recover the half-angle gain (Stage 0 FINDINGS 4).
- Gate: does the indicator MIP land loopmm-3jump + dsf-neo at the byte-exact objective AND certify infeasibility where BnB returns null, within ~0.1 to 800 ms (A05-9 discriminator, UNMEASURED-HYPOTHESIS).
- SMT-FP: no measurement needed; the memory's decisive test (3 free ticks -> unknown at 300 s) already settles it as verifier-only.
IMPACT: simplicity/correctness. Two shipped collapses (certified snap; complete gate with a certificate) plus one firm scope boundary (SMT-FP research-only), all dependency-free.
PROPOSAL: route the snap and the gate to Stage E prototypes (COPT reference first, then pure-Java port); leave SMT-FP as an optional out-of-tree Bitwuzla certifier documented but unbuilt.
CONFIDENCE: 0.7
DEPENDS-ON: D11-3, D11-4, D11-6, D11-7, D11-8

---

## CITATIONS (real, checkable; consulted this session)

Foundational (sub-problem a, closest-vector / integer-LS):
- A.K. Lenstra, H.W. Lenstra Jr., L. Lovász, "Factoring polynomials with rational coefficients," Mathematische Annalen 261(4):515-534, 1982. DOI 10.1007/BF01457454. https://en.wikipedia.org/wiki/Lenstra%E2%80%93Lenstra%E2%80%93Lov%C3%A1sz_lattice_basis_reduction_algorithm
- L. Babai, "On Lovász' lattice reduction and the nearest lattice point problem," Combinatorica 6(1):1-13, 1986. DOI 10.1007/BF02579403. https://www.emergentmind.com/topics/babai-s-nearest-plane-algorithm
- U. Fincke, M. Pohst, "Improved methods for calculating vectors of short length in a lattice, including a complexity analysis," Mathematics of Computation 44(170):463-471, 1985.
- C.P. Schnorr, M. Euchner, "Lattice basis reduction: improved practical algorithms and solving subset sum problems," Mathematical Programming 66:181-199, 1994. DOI 10.1007/BF01581144.
- E. Agrell, T. Eriksson, A. Vardy, K. Zeger, "Closest point search in lattices," IEEE Trans. Information Theory 48(8):2201-2214, 2002.
- B. Hassibi, H. Vikalo, "On the sphere-decoding algorithm I. Expected complexity," IEEE Trans. Signal Processing 53(8):2806-2818, 2005. https://authors.library.caltech.edu/records/9k984-14325 ; "Integer least squares: sphere decoding and the LLL algorithm" https://dl.acm.org/doi/10.1145/1370256.1370261
- Recent CVP/enumeration (latest): N. Gama, P.Q. Nguyen, O. Regev, "Lattice enumeration using extreme pruning," EUROCRYPT 2010 (https://link.springer.com/chapter/10.1007/978-3-642-13190-5_13); Y. Aono, P.Q. Nguyen, "Random sampling revisited: lattice enumeration with discrete pruning," EUROCRYPT 2017; E. Doulgerakis, T. Laarhoven, B. de Weger, "Finding closest lattice vectors using approximate Voronoi cells," PQCrypto 2019 (sieving overtaking enumeration).

The gate (sub-problem b, big-M / disjunctive / MISDP):
- E. Balas, "Disjunctive programming," Annals of Discrete Mathematics 5:3-51, 1979; E. Balas, "Disjunctive Programming," Springer, 2018. https://lara.epfl.ch/w/_media/projects:disjunctive_programming.pdf
- J. Kronqvist, R. Misener, et al., "P-split formulations: a class of intermediate formulations between big-M and convex hull for disjunctive constraints," Mathematical Programming, 2025. https://link.springer.com/article/10.1007/s10107-025-02232-1 ; arXiv 2202.05198
- T. Gally, M.E. Pfetsch, S. Ulbrich, "A framework for solving mixed-integer semidefinite programs," Optimization Methods and Software 33(3):594-632, 2018. https://optimization-online.org/2016/04/5394/ (SCIP-SDP)
- "A Branch and Cut Solver for Mixed-Integer Semidefinite Programming: New Sparse Cutting Planes," AIChE Annual Meeting 2024. https://aiche.confex.com/aiche/2024/meetingapp.cgi/Paper/691673
- C. Hojny, M.E. Pfetsch, et al., "Handling symmetries in mixed-integer semidefinite programs," CPAIOR 2023. https://link.springer.com/chapter/10.1007/978-3-031-33271-5_5
- M. Soltanalian, P. Stoica, "Designing unimodular codes via quadratic optimization," IEEE TSP 62(5):1221-1234, 2014.

The verifier (sub-problem c, SMT-FP):
- A. Niemetz, M. Preiner, "Bitwuzla," CAV 2023, LNCS 13965, pp. 3-17 (CAV Distinguished Paper). https://cs.stanford.edu/~preiner/publications/2023/NiemetzP-CAV23.pdf ; https://bitwuzla.github.io/
- P. Trentin, R. Sebastiani, "Optimization Modulo the Theory of Floating-Point Numbers," CADE 2019; extended in J. Automated Reasoning 65:1071-1109, 2021 (arXiv 1905.02838). https://arxiv.org/abs/1905.02838 ; OptiMathSAT https://optimathsat.disi.unitn.it/
- "A Hybrid Method for Optimization Modulo Theory of Floating-Point Numbers," Mathematics 14(8):1381, 2025 (MDPI). https://www.mdpi.com/2227-7390/14/8/1381

External analogue (discrete-phase constant modulus):
- "Quantized Phase Alignment by Discrete Phase Shifts for Reconfigurable Intelligent Surface-Assisted Communication Systems," arXiv 2303.13046, 2023 (DTPQ/EIPQ optimal discrete-phase quantizers). https://arxiv.org/abs/2303.13046

In-campaign measured record cited above (not external): 00-context-pack.md; SPEC.md 4.2/4.4/4.5/4.6/5; stage0-copt/FINDINGS.md sections 1/4/5; stageA/agentA05.md (A05-1..10); stageA/agentA10.md (A10-1..12); stageA/agentA03.md (A03-12); memory reference_byte_exact_certified_solver.
