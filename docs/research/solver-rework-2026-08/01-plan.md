# 01 Plan and Live Status Board: Angle-Solver Re-Founding (2026-08)

Single entry point for the campaign. Kept live. Gate = a stage's canonical doc exists, its auditor pass
is done, and the gate box is ticked, before the next stage's fan-out launches.

## Pipeline and gates

| Stage | What | Fan-out | Canonical output | Gate |
| --- | --- | --- | --- | --- |
| 0 | COPT spike: H1 vs H2, bound tightness | harness + experiments | stage0-copt/FINDINGS.md | [x] DONE |
| A | Codebase deep-analysis | 10 agents + reducer | stageA/SYNTHESIS.md | [x] DONE |
| B | Corpus + hot-loop benchmarking | 6 agents + reducer | stageB/SYNTHESIS.md | [x] DONE |
| C | SPECIFICATION + math framing | orchestrator | SPEC.md | [x] DONE (math class pinned; perf fold-in pending B) |
| D | Deep research, ranked candidates | 14 agents (focused by SPEC), 2-level | RESEARCH-DOSSIER.md | [x] DONE (dossier confirms ARCH-1 chain; 4 citations audited) |
| E | Implement + benchmark vs COPT | prototypes + agents | RESULTS.md | [x] DONE (ARCH-1 validated end-to-end; RESULTS.md + stageE/*) |
| Close | Final synthesis + proposed architecture | orchestrator | FINAL-REPORT.md | [x] DONE |

## ALL DELIVERABLES COMPLETE (paths in FINAL-REPORT.md section 10). Green-gate slow suite running (bg).

## PIVOTAL RESULTS SO FAR (measured)

- Stage 0 (COPT): the constant-modulus QCQP is GLOBALLY SOLVABLE in <0.3s at n<=49 (COPT spatial B&B).
  The multi-jump recovery failure is a SMALL H1 (disk loose ~1.6e-3b at 1-4 low-authority ticks) + H2
  (SDR rank 2-3 there), but NOT an intractable nonconvexity. Shipped dual bound is loose ONLY from
  non-convergence (COPT SOCP tight in <20ms). Byte-exact can OUT-reach the continuous model by up to
  1e-2b (half-angle norm>1), so COPT is a near-exact reference not a strict bound.
- SPEC math framing: the problem is a LINEARLY-CONSTRAINED CONSTANT-MODULUS PROGRAM that REDUCES to a
  convex dual/active-set solve (determines every non-degenerate tick in closed form u_t=m_t g_t/|g_t|)
  PLUS a nonconvex residual of dimension = #vanishing-costate ticks. Bounded by: LCvx (D01) <= n_x-1 = 3
  horizon-independent; Pataki r(r+1)/2 <= #active walls. MEASURED 0-1 dominant.
- ARCH-1 PoC (orchestrator, COPT): branch over the 1 degenerate tick + convex re-solve of the rest reaches
  the COPT GLOBAL optimum within 1e-5..3e-5b on j021/j008b/loopmm. The effective nonconvex dimension is 1.
  The shipped solver fails by defaulting that 1 tick then falling to full-n search. REFINEMENT: naive
  fix-others-rigid is INFEASIBLE; must re-optimize the rest convexly per branch node.
- D-research (partial): LCvx (D01) = residual-size theory + identification test (g_t=0). Riemannian (D05)
  = trivial pure-Java port, local+costate-seeded, UNIQUELY carries smoothness jointly (SPEC 4.5 unify).
  UQP (D04) = local polish only, ARCH-1 dominates.

Consumption chain: Stage 0 -> C (convex-attainability) + E (reused harness). A + B -> C. C -> D. D -> E.

## Status log (newest first)

- 2026-08-24: Orientation complete. Read AGENTS/CONTEXT/VISION, mcpk 01+02, the latest handoff
  (smooth-and-convergence-2026-08-24), angle-solver.md (full), dual-newton-iteration-audit, the math
  memories, JumpLinearModel.java, MiqcpDump.java, CoefDump.java. COPT v8.0.5 VERIFIED WORKING (license
  at C:\Users\benja\Desktop\Coding\98 Anderes\copt, LP solve returns optimal). Python 3.12.6, java 25
  on PATH (core builds Java 8 via toolchain). Working tree + context pack + this board created.
- Next: build the general StructureDump exporter + research/copt harness; launch Stage A (10) and
  Stage B (10) in parallel; run Stage 0 COPT experiments.

## Pre-flight facts established (re-verify before building on)

- COPT reachable: coptpy imports, license OK, solve status 1 (optimal). Version 8.0.5 build May 2026.
- Corpus present: captures/ (54+ top-level, hpk/{d9,d10,d11}), problems/{solve,closedform,
  dualrecovery}. Multi-jump stand-ins: f2f-dfchain-multijump.json, df-chain-free-start.json.
- Exporter MiqcpDump.java present and razor-specific; to be generalized. CoefDump.java, MiqcpDump.java,
  StructureVariantDump.java, MatrixAnalysisScreen.java, RelaxDiagScreen.java exist as references.
- The linear model (JumpLinearModel.java) matches the math in the context pack exactly:
  u_t = m_t (cos phi, sin phi), phi = baseArg_t + yaw_t; pos affine via coef(s,k)=(sPre[k]-sPre[s])/
  fPre[s]; walls linear; only nonconvexity |u_t|=m_t.

## Stage dispatch tracking

### Stage 0 (COPT)
- harness build: PENDING
- H1/H2 experiment: PENDING
- bound-tightness sweep: PENDING
- auditor: PENDING

### Stage A (ALL 10 SHARDS IN; reducer dispatched)
- A01 dual: DONE. pgres plateau corpus-wide (j008b 0.418b, razor 5.56b, loopmm 0.245b); duality gap not convergence bug; recovery copy-pasted 5+ sites; caching/smoothing/dF ABSENT, free-start present.
- A02 models: DONE. drift n-dependent 7.1e-5..6.5e-4b; stepRange DEAD; precompute rebuilt ~36x/dualChain; BucketAscent 36M forward evals n=49; sine262 force-45 fidelity bug in precompute.
- A03 closed-form+relax: DONE. j828 13/39 off-sphere BUT |g|<=7.3e-10 there => H2 (dual-face degeneracy) evidence; LatticeRepair DEAD in shipped path (fix context pack); RR bails on facing/EQ.
- A04 SLP+TRLp: DONE. 87.5% LP reject re-verified; TrustRegionLp cross-checked vs commons-math3 in FAST suite; do NOT re-add math3; SLP already the primal engine single+multi.
- A05 gate B&B: DONE. gate folds to ~2n big-M indicators; bespoke suffix-pattern family INCOMPLETE (false-neg infeasibility); routed coldBnb null -> emit no-solution is dangerous.
- A06 free-start: DONE. p0 PROVABLY separable by rigid translation; could collapse to one disk-SOCP with free p0; 3 duplicate translation impls.
- A07 receding horizon: DONE. NOT identical to single-jump per window (surrogate obj, centered vs hug, dropped seam constraints); cross-window warm-start UNBUILT; global convex replacement measured-dead for the dual.
- A08 smoothing: DONE. 4 stages collapse to ONE convex-in-theta term beta||D2 theta||_1 + gamma||D1 theta||_2^2, but NOT in u-space (destroys LCvx tightness); give-back caps STACK; turnCost is a search bias not post-pass.
- A09 engine+graph: DONE. FAST 32 nodes / OPTIMIZE 43 / EXPLORE 9; explore lacks free-start/capCertify/translate; legal-mode push only in OPTIMIZE; startPos mutated in-place on shared scenario (dropped-feasible surface).
- A10 discreteness: DONE. bucket 3.14e-5..1.54e-4b; gate inert on fed ticks (0-1 destructive fires); half-angle reach 1.5e-4b (45x smaller than 0.007b gap => NOT the gap source); LatticeRepair/FacingLattice dead outside tests; dF pinned twice (FacingPrefold vs YawTies).
- reducer: DISPATCHED. auditor: folded into reducer.

### Stage B (6 focused agents; B01 running, B02-B06 DONE; reducer pending)
- B01 timing distribution: RUNNING
- B02 wasted iterations: DONE. 58.6% dual rungs cap; j021/df-chain 100% capped (50-56 of 100 iters past-progress); SLP 87.5% reject (j022 99%); ILS plateau ~36% of THOROUGH wall sub-floor. Biggest safe levers: SLP trust-region termination + anytime sub-floor termination + recover-differently (not converge-faster).
- B03 correctness: DONE. Slow suite GREEN (703 tests, ProblemsTest 120/120, 3m28s). Determinism HOLDS (byte-identical). ZERO false-successes in 18 solves (but EQ/range MET_TOL vs FEAS_TOL latent risk confirmed in code). j318/j716 now SOLVE (stale miss labels); only nix-full-t1 (1.85e-4 precision) + razor-proof-t1 (basin) genuine misses. RazorColdT1 headline gate RED (9/14), env-gated/CI-excluded.
- B04 recompute+cache: DONE. stepRange dead at runtime (0 partial forwards); SmoothingPolish 99.96% of forwards on Smooth-OFF j001 (94% of solve!); model rebuilt 19-44x/dualChain; cross-window warm-start UNBUILT (8 cold duals on j001); bnb/seamSweep model+dual rebuild storms.
- B05 hot leaf: DONE. buildHessian inner loop uncapped, capping = BIT-IDENTICAL -28% leaf CPU (biggest zero-risk lever); cholesky refactored 8x/step; BucketAscent full-forward rescoring ~2x via stepRange; TrustRegionLp 86.2% reject.
- B06 envelope+headroom: DONE. Stage E baseline table (14 captures); real headroom only on coupled (j008b +1.84e-2, j021 +1.6e-3); over-reach on single (j345 +1.97e-2 largest, half-angle); 6/13 FAST already AT COPT yet THOROUGH burns 7.6s; envelope tail nix-full-t1 ~40s + loopmm-tight ~35s.
- reducer: pending B01.

## STAGE E prototype code (measured, in tree)
- research/copt/{coptlib.py, run_h1h2.py, residual_branch.py, residual_poc.py, export_yaws.py} + StructureDump.java: COPT oracle + ARCH-1 PoC. VALIDATED ARCH-1 reaches global optimum.
- ReplayYaws.java (test): byte-exact roundtrip. Continuous optimum is a bound/guide; byte-exact must be searched at degenerate + half-angle ticks (objective-aware snap).
- PORT NOTE: the shipped convex path (ClosedFormSolve/RelaxationRecovery) BAILS on facing constraints, so a from-scratch Java pin-a-tick-and-reconverge reuse is not available; the pure-Java ARCH-1 needs a NEW "convex solve with a subset of ticks pinned to arbitrary yaws" entry. This is the first implementation step; the mechanism is validated.

### Stage D clusters (assigned at dispatch)
- D01..D12: PENDING

### Stage E approaches (assigned from dossier)
- E01..E20: PENDING

## Rulings and invariants to respect (from memory + mission)

- Dependency-free / analytical PREFERRED; a justified redistributable dependency allowed (note loader
  packaging cost); COPT NEVER shipped.
- core stays MC-free. Do not break Application.runSimulation() retrigger.
- Never call sub-milliblock reach gains negligible; floor is the ~1e-4 sine residual.
- Jumps do NOT make the problem nonconvex; the per-tick sin/cos (constant modulus) does. Never
  attribute difficulty to jump count.
- Shipped path green on :core:test -PslowTests at ALL times. Prototypes in test/ or behind a flag.
- No git ops. No code comments. No em dashes.
