# Stage A shard: agent A04

- Agent: A04
- Territory: sequential LP recovery (`solver/SlpSolve.java`) and the bespoke LP kernel (`solver/TrustRegionLp.java`).
- Files inspected: `SlpSolve.java`, `TrustRegionLp.java`, `TrustRegionLpTest.java`, `CostateDualSolver.java`, `RelaxationRecovery.java`, `AngleSolverEngine.java` (dualChain, 1703-1775), `DualChainNode.java`, `BoundPrunedRecovery.java` (call sites), `FreeStartSolve.java` (call sites), `LongRunSolver.java` (call sites), `LevelSetAscent.java`/`SeamSweepRecovery.java` (call sites), `ClosedFormSolve.java` (dual/model usage), `Objective.java`, `core/build.gradle`.
- Commands run: enumerated all 15 shipped call sites via Grep; re-verified the SLP reject-rate over 75 accumulated `build/reports/solver-trace-*.txt` files with grep/python counters; cross-checked the 85% figure origin in `docs/research/dual-newton-iteration-audit.md:90`.
- Did NOT invoke gradle (background compile running). No new code written.

---

## A04-1: SLP is the project's single primal recovery workhorse, invoked from ~15 shipped sites
- LOCATION: `SlpSolve.optimize` (SlpSolve.java:102); callers in AngleSolverEngine.java:1716,1736 (dualChain), RelaxationRecovery.java:125, BoundPrunedRecovery.java:236,776,778, FreeStartSolve.java:68,89, LongRunSolver.java:325,326,330,351, LevelSetAscent.java:58, SeamSweepRecovery.java:99,179,268,343.
- CLAIM: Every recovery path that is not a clean closed-form dual (single-jump, relaxation, B&B nodes, free-start candidates, multi-jump windows, level-set top-up, seam sweep) ultimately closes feasibility and hugs the objective through SLP, so SLP is already the de-facto shared primal engine.
- EVIDENCE: 15 distinct `SlpSolve.*` call sites in `core/src/main/` (Grep count). In `dualChain` (AngleSolverEngine.java:1708-1740) the order is closed form -> SLP -> relaxation -> alt-seed SLP; SLP is the first non-closed-form stage and the last (reseeded) stage.
- IMPACT: simplicity (positive): the folding target of section 5 is largely already realized in practice; a rework can treat SLP as the primal core rather than one of many peers.
- PROPOSAL: Treat SLP + its byte-exact accept gate as the canonical primal-recovery contract; audit whether the other recovery stages (RelaxationRecovery, SeamSweep) add measured wins over "SLP from a better seed" (see A04-9).
- CONFIDENCE: 0.9
- DEPENDS-ON: A04-9

## A04-2: What SLP computes, and the exactness properties (byte-exact slacks in the loop, analytic i*u Jacobian)
- LOCATION: SlpSolve.java:195-321 (phase loop), 202-205 (byte-exact evaluate), 235-259 (linearization + Jacobian), 261-296 (LP step + accept).
- CLAIM: SLP is a trust-region SQP-flavored method where the constraints and objective are relinearized exactly around the current facings each iteration, the LP proposes a bounded facing delta, and acceptance is decided on the true byte-exact ExactJumpModel slacks, not the linear prediction.
- EVIDENCE: each iterate calls `exact.forward(sc, gf)` and `exactSlacks(...)` on both the incumbent (204-205) and the candidate (277-278); accept tests `cViol`/`cObj` from those byte-exact paths (282-284). The Jacobian is closed form: `du = wall.axis==0 ? -uz : ux` scaled by RAD (247-249), i.e. for u_t = m_t e^{i(baseArg+theta)}, du/dtheta = i*u_t (d Re/dtheta = -Im u = -uz on X walls, d Im/dtheta = Re u = ux on Z walls). No finite differences anywhere in the file.
- IMPACT: correctness (positive): the LP is only a step oracle; feasibility/optimality claims never rest on the linearization, so linearization error costs iterations, never a false "feasible". This is the design property that makes the bespoke LP kernel safe to be imperfect.
- PROPOSAL: Preserve the "LP proposes, byte-exact disposes" invariant in any rework; it is the load-bearing safety net.
- CONFIDENCE: 0.97
- DEPENDS-ON: none

## A04-3: Phase 1 vs phase 2 differ in objective, accept rule, and clearance target
- LOCATION: SlpSolve.java:195-320.
- CLAIM: Phase 1 minimizes the worst byte-exact wall violation to reach an inward clearance and accepts any strictly-decreasing-violation step; phase 2 fixes feasibility (`cViol <= feasTol`) and accepts only strict objective improvement, hugging the wall it optimizes into.
- EVIDENCE: phase-1 LP passes `phase==1=true` and no objective row (objRow built only when `phase != 1`, 251-259); accept is `cViol < maxViol` (282-283); break at `maxViol <= -targetClearance` (206). Phase 2 sets `sCap = max(-CLEARANCE, maxViol)` to hold feasibility (262) and accepts `cViol <= feasTol && cObj > objNorm` (284). Between phases the trust region is reset to 10 deg (319) because phase-1 shrink may have collapsed it. Budgets: phase1Calls=40, totalCalls=60 default (Config:24-25).
- IMPACT: simplicity: the two phases are a standard phase-1/phase-2 LP split reused as an SQP; clean and worth keeping. The reset-to-10-deg and the CLEARANCE=1e-6 inward margin (19) are hand-tuned couplings a rework should document as parameters, not constants.
- PROPOSAL: Keep the two-phase split; expose the phase-2 trust-region reset as a Config field for the tuning matrix.
- CONFIDENCE: 0.95
- DEPENDS-ON: none

## A04-4: RE-VERIFIED: SLP rejects ~87.5% of its LP steps (~8 LPs per accepted step), matching the handoff's 85%
- LOCATION: accept/reject decision SlpSolve.java:282-296 (trust-region halve on reject, 293-295); handoff origin `docs/research/dual-newton-iteration-audit.md:90`.
- CLAIM: The handoff's "SLP rejects 85% of its LP steps, ~6.8 LPs per accepted step" is confirmed and is slightly conservative on the accumulated trace corpus.
- EVIDENCE: counted the per-LP `accept`/`reject` trailer over 75 `build/reports/solver-trace-*.txt` files (produced by prior sweep/bench runs of the current SLP logic; SlpSolve.java is unmodified in this working tree per git status): 6,477 accept vs 45,430 reject = 87.5% reject, 8.01 LP calls per accepted step. Phase split: phase 1 87.1% reject (6,168 acc / 41,713 rej), phase 2 92.3% reject (309 acc / 3,717 rej). The audit's own instrumented sweep counter was 36,034 rej / 6,197 acc = 85.3% / 6.8. Method: `grep -hE "SLP .*phase=" ... | grep -c "accept$|reject$"`.
- IMPACT: speed. At ~52k LP calls per sweep and TrustRegionLp ~11% of solver CPU (dual-newton-iteration-audit.md:89), most of that 11% is thrown-away work; cutting the reject rate in half is a direct wall-time win on the recovery path with no objective change.
- PROPOSAL: Route into research: the reject-heavy pattern is a trust-region-management problem (A04-5). Re-run the counter under a candidate TR policy on the same trace corpus as the acceptance gate.
- CONFIDENCE: 0.9
- DEPENDS-ON: A04-5

## A04-5: The waste is trust-region management, not LP-kernel cost; phase 2 is the worst offender
- LOCATION: SlpSolve.java:290-296 (TR grow x2 only when step>0.8*tr; shrink x0.5 on every reject), 319 (phase-2 reset to 10 deg), trMinDeg=1e-7 default (Config:29).
- CLAIM: The reject cascade comes from a pure halve-on-reject / conditional-double-on-accept trust region with no curvature model, so near a hugged wall (phase 2) the method proposes a large step, gets rejected, and halves repeatedly down toward the 1e-7 float lattice, spending ~12 rejects to shrink 30 deg to a workable step.
- EVIDENCE: phase-2 reject rate 92.3% (A04-4); the shrink is unconditional x0.5 (293) and the grow requires step>0.8*tr AND an accept (292), so a run of rejects is monotone geometric shrink; a 30 deg -> sub-0.01 deg descent is ~12 consecutive rejects. The in-tree TR floor of 1e-3 deg shipped precisely to cut this tail (angle-solver.md:317: 6.5s -> 4.3s on loopmm by raising trMinDeg off 1e-7).
- IMPACT: speed (est. up to ~5% of solver CPU recoverable; TrustRegionLp is 11% and most is rejected steps per A04-4). Knife-edge risk: dual-newton-iteration-audit.md:90 flags TR adaptation as measure-gated only.
- PROPOSAL: Prototype a predicted-vs-actual trust-region ratio test (classic SQP: grow/shrink by rho = actual reduction / predicted reduction `lp.s`, which SLP already computes at 286) instead of the binary step>0.8*tr heuristic; gate on the A04-4 counter plus the full slow suite. UNMEASURED that it helps; experiment = wire a rho-based TR update behind a Config flag, replay the 75-file corpus, compare reject rate and per-capture objective.
- CONFIDENCE: 0.75
- DEPENDS-ON: A04-4

## A04-6: TrustRegionLp is a general bounded-variable primal simplex, structurally specialized to the SLP step, and cross-validated against commons-math3
- LOCATION: TrustRegionLp.java (whole); variable layout 41-57, phase switch 64-97, ratio test with bound-flips 193-255, Bland anti-cycle 257-272, Gauss-Jordan refactor 274-332. Validation: `TrustRegionLpTest.java`.
- CLAIM: It is a complete revised bounded-variable simplex (explicit B-inverse, Bland's rule, periodic refactor), not a toy, but its column structure is hard-wired to SLP's problem: split step vars d+ , d- each in [0, tr], one free max-violation surrogate s, and m constraint slacks; it does not accept arbitrary A x <= b with arbitrary bounds.
- EVIDENCE: `total = 2n+1+m` and `sVar = 2n` are fixed (46-47); `hiOf`/`loOf` bake tr and the free s (182-191); `extractD` reconstructs d = d+ - d- (344-350). Phase 1 minimizes s (cost[sVar]=1, 75), phase 2 caps s<=sCap and minimizes obj (85-90). TrustRegionLpTest.matchesCommonsMathOnRandomInstances checks 300 random instances (n<=30, m<=80) against `org.apache.commons.math3 SimplexSolver` to value tol 2e-6 and infeasibility agreement (>=200 must be checked).
- IMPACT: correctness (positive): the bespoke-simplex risk is materially bounded by a differential test against a reference simplex. That test is in the FAST suite (package `...core.anglesolver.solver`, not tagged SlowSolverTests), so it gates every build.
- PROPOSAL: Keep the differential test as the correctness contract for any TrustRegionLp edit; expand its m/n range if the corpus ever exceeds n=30 windows.
- CONFIDENCE: 0.92
- DEPENDS-ON: none

## A04-7: Replacing TrustRegionLp with a permissive LP library is measured-net-negative under the dependency policy
- LOCATION: `core/build.gradle:34-37` (commons-math3 is test-only), context-pack section 6 dependency policy.
- CLAIM: The one obvious library candidate (commons-math3 `SimplexSolver`, Apache-2.0) was already the incumbent and was deliberately removed for shipping; re-adding an LP library would re-incur the cross-loader packaging cost the project paid to shed, for a kernel that is small, allocation-light, hot (~52k calls/sweep), and already validated.
- EVIDENCE: build.gradle:34 comment: "commons-math3 is test-only since the bespoke TrustRegionLp replaced the SimplexSolver"; it survives only as `testImplementation` (37). Shipped packaging per AGENTS.md: Forge 1.8.9/1.12.2 must shade+relocate, Fabric must include; commons-math3 is a large dense-optimization jar. dual-newton-hessian-handoff.md:108-110 records that TrustRegionLp already differs from commons-math on hug quality (j022-noland ~1e-4), so a library swap is not even objective-neutral.
- IMPACT: simplicity/speed (negative if adopted): re-adds a dropped dependency and its 3-loader packaging burden; commons-math SimplexSolver allocates per solve and is slower in a 52k-call inner loop.
- PROPOSAL: Do NOT replace TrustRegionLp with a library. If the bespoke risk is ever a concern, invest in the differential test (A04-6), not a dependency. Keep the pure-analytical shipped path.
- CONFIDENCE: 0.85
- DEPENDS-ON: A04-6

## A04-8: SLP re-derives the full linear model (and re-solves the dual) on every call, duplicating work its callers already did
- LOCATION: SlpSolve.java:127 (`new JumpLinearModel(sc)`), 134-139 (compileWall loop), 152-154 (fresh `CostateDualSolver.solve(0.0, null)` seed), 210-224 (rebuild a patterned JumpLinearModel + recompile all walls + objective vectors on every inertia-pattern change).
- CLAIM: The coef arrays (prefix products of friction) and compiled walls are a pure function of (scenario, constraints) yet are rebuilt inside SLP even when the immediate caller just built the identical model, and the dual seed re-runs the same CostateDualSolver that the closed-form stage ran moments earlier.
- EVIDENCE: on the dualChain path, `ClosedFormSolve.optimize` builds JumpLinearModel + compileWalls + CostateDualSolver (ClosedFormSolve.java:120,193,379) and, on failure, dualChain immediately calls SLP (AngleSolverEngine.java:1716) which rebuilds all three (SlpSolve.java:127,134,152). RelaxationRecovery builds lin+walls+dual once (RelaxationRecovery.java:37-46) then calls SLP (125), which rebuilds lin+walls again. Inertia-aware SLP allocates a new `JumpLinearModel(sc, zeroX, zeroZ)` and recompiles every wall each time the zeroing pattern flips (212-224), inside the LP loop.
- IMPACT: speed/simplicity: redundant O(walls x ticks) model builds and a redundant microsecond dual per recovery attempt; small per-call but multiplied across B&B nodes (per-pattern SLP at BoundPrunedRecovery.java:776/778) and the free-start start sweep (FreeStartSolve.java:89 per candidate).
- PROPOSAL: Pass a prebuilt/compiled model (and optional dual seed) into SLP instead of a raw JumpSpec, so callers that already compiled it (RR, BnB, dualChain, free-start) reuse it; cache the patterned models by zeroing-pattern key inside the loop.
- CONFIDENCE: 0.85
- DEPENDS-ON: none

## A04-9: SLP shares the exact same linear model with the dual, RelaxationRecovery, and BnB; it is a fourth consumer of one model, not a contradicting one
- LOCATION: `JumpLinearModel.compileWall`/`objectiveVectors`/`mMagAll` used identically by CostateDualSolver (via walls, CostateDualSolver.java:107-114), RelaxationRecovery (37-46), BoundPrunedRecovery (per-pattern), and SlpSolve (127-165).
- CLAIM: There is no model contradiction across the recovery stack: the dual, RR's AL-FISTA disk relaxation, BnB's pattern-folded duals, and SLP all read the same wall.coef/bPrime/axis and the same i*u geometry; they differ only in how they search (dual = costate recovery, RR = projected FISTA on the disk |u|<=m, SLP = trust-region LP on the circle |u|=m, BnB = branch over inertia patterns).
- EVIDENCE: all four construct `JumpLinearModel.Wall` via the same compiler; SLP's seed IS a `CostateDualSolver` (152); RR seeds SLP with its relaxed primal projected to yaws (RelaxationRecovery.java:115-125); the wall struct (axis, coef, bPrime, eq, p0coef) is the shared interface (CostateDualSolver.java:107-114).
- IMPACT: simplicity (positive): confirms the rework can converge these on one compiled-model type; the duplication is re-derivation (A04-8), not divergent modeling.
- PROPOSAL: Unify on one compiled-model object shared by all four consumers; measure whether RR and SeamSweep still earn their place once SLP takes a good relaxed seed directly.
- CONFIDENCE: 0.8
- DEPENDS-ON: A04-8

## A04-10: SLP already spans single- and multi-jump; the folding blocker is wrong-linearization / LP-hug quality, not iteration budget
- LOCATION: single/window use LongRunSolver.java:325-326,351 (per-window SLP), dualChain (single); failure records dual-newton-hessian-handoff.md:106-110 (j022-noland), smooth-and-convergence-handoff-2026-08-24.md:131-147 (thousand/1).
- CLAIM: SLP is already the primal engine for both single jumps and multi-jump receding-horizon windows, so folding is mostly a question of removing peers, not adding capability; what blocks SLP from being globally optimal on coupled instances is that it is a LOCAL method whose LP vertex hugs imperfectly, not that it needs more iterations.
- EVIDENCE: LongRunSolver drives each window through SLP (325-326) and hugs via SLP (351). Failure is linearization/hug-bound: j022-1bmhbfly-noland stays 2.7e-4 short because "LevelSetAscent rungs feasible on commons-math are not on TrustRegionLp" (dual-newton-hessian-handoff.md:108-110); thousand/1 recovery is 2.89 blocks infeasible for ALL lambda, a genuine duality gap (smooth-and-convergence-handoff-2026-08-24.md:131-138). Raising iteration budget is measured dead: MAX_ITER 100->10000 leaves closed form null (same handoff:126).
- IMPACT: robustness/correctness: SLP cannot be promoted to "the certified global optimizer" for coupled multi-jump; it remains a strong local recoverer. Any folding must keep a global oracle (dual bound + B&B) above it.
- PROPOSAL: Fold the primal LAYER onto SLP but keep the global-bound layer (dual + BnB) as the certifier; target the LP-hug-quality gap (A04-5, A04-11) rather than iteration count.
- CONFIDENCE: 0.85
- DEPENDS-ON: A04-5

## A04-11: LP-hug quality is a measured correctness/quality regression vs the removed commons-math simplex (j022-noland)
- LOCATION: dual-newton-hessian-handoff.md:106-110; level-set ladder LevelSetAscent.java:58; TrustRegionLp phase-2 hug SlpSolve.java:280-284.
- CLAIM: TrustRegionLp lands at a slightly worse vertex than commons-math3 SimplexSolver did on the level-set ladder, costing ~1e-4 blocks that the ILS cannot fully close, which is why solve/j022-1bmhbfly-noland carries a 5e-4 objective-gap pin.
- EVIDENCE: dual-newton-hessian-handoff.md:108-110: seed regressed -531.70000 -> -531.69975 "because LevelSetAscent rungs feasible on commons-math are not on TrustRegionLp, and the ILS closes only ~1e-4 of that." The pin `maxObjectiveGap: 5.0e-4` is recorded in dual-newton-hessian-handoff.md:12.
- IMPACT: correctness/robustness (small, ~1e-4 to 2.7e-4 blocks, one capture): above the ~1e-4 certify floor, so it is a real, non-negligible gap per the domain rule, but bounded to tight-hug level-set ladders.
- PROPOSAL: Root-cause the vertex difference (degeneracy tie-break? EPS_FEAS 1e-9 vs commons tolerance? bound-flip ordering at TrustRegionLp.java:211/219?) as part of A04-5; add j022-noland to the LP-hug regression watch.
- CONFIDENCE: 0.8
- DEPENDS-ON: A04-6, A04-5

## A04-12: CONSISTENCY MATRIX for SLP (caching, smoothing, defaults, dF, free-start)
- LOCATION: SlpSolve.java as cited per row.
- CLAIM: SLP implements dF (partially) but has no caching, no smoothing awareness, inconsistent per-caller budgets, and no free-start awareness; the gaps are enumerated below with file:line.
- EVIDENCE:
  - CACHING: ABSENT. No memoization; JumpLinearModel + walls rebuilt every call (SlpSolve.java:127,134-139) and per inertia pattern (212-224). Window/result caching lives above SLP in the graph, never inside it. (See A04-8.)
  - SMOOTHING: ABSENT. SLP reads only `spec.objective.axis/sense/tick` (normObjective, SlpSolve.java:372-375; objectiveVectors, 143-145). `Objective.smoothLambda`/`smoothPenalty` (Objective.java:12,28-31) are never referenced in SlpSolve (grep: none). All smoothing is post-SLP (DeWiggle, SmoothingPolish, SmoothFaceRecovery).
  - DEFAULTS: PRESENT but INCONSISTENT across callers. Internal Config default 40/60 calls, tr 30/45/1e-7, lpMaxIter 2000, centerClearance 2e-2, CLEARANCE 1e-6 (SlpSolve.java:19,22-34). Overridden to 160/220 by RelaxationRecovery (RelaxationRecovery.java:16-17), to tree/polish budgets by BnB (BoundPrunedRecovery.java:777,779), to graph params by DualChainNode (DualChainNode.java:29-35). Free-start, LongRunSolver, LevelSetAscent, SeamSweep call the no-Config overloads and get the 40/60 default. So the same kernel runs at 40/60, 160/220, and graph-tuned budgets on different paths.
  - dF (facing constraints): PRESENT but RESTRICTED. SLP accepts facing walls only if YawTies can absorb them as position-linear (SlpSolve.java:108-121): dF=0 delta pins (MINUS to prior tick, same group) or fixed-yaw eliminations; any other F constraint returns null (119). Position EQ constraints are also rejected (124). Contrast: RelaxationRecovery bails on ANY facing wall (RelaxationRecovery.java:32). So dF support is SLP-only within the recovery stack, and only for the absorbable subclass.
  - FREE-START: ABSENT in SLP. SLP always solves at the fixed start folded into constPos; its dual seed uses the 5-arg `new CostateDualSolver(n, cx, cz, mMag, walls)` with freeP0=null (SlpSolve.java:152), never the FreeP0 free-start dual (CostateDualSolver.java:94-95). Free-start is orchestrated ABOVE SLP by re-invoking it at each candidate start (FreeStartSolve.java:68,89 via specAtStart), so SLP never chooses a start.
- IMPACT: simplicity/robustness: four of five capabilities are handled outside or absent, so SLP is a fixed-start, smoothing-blind, uncached kernel with partial dF. A rework that wants "one primal engine with consistent capabilities" must add caching (A04-8), decide whether smoothing enters SLP's objective or stays a post-pass, unify the budget defaults, and either teach SLP free-start or keep it strictly a fixed-start primitive.
- PROPOSAL: Document these as the SLP capability contract; converge budgets to one effort-scaled schedule; keep smoothing a post-pass unless a measured case shows in-loop turnCost helps; keep free-start orchestration above SLP (the per-start re-invoke is simple and correct).
- CONFIDENCE: 0.92
- DEPENDS-ON: A04-8

## A04-13: SLP's own fallback ladder (inertia-aware retry, alt-direction reseed) partly duplicates the dualChain/RR ladders
- LOCATION: SlpSolve.java:311-316 (self-recurse into inertiaAware on phase-1 infeasible), AngleSolverEngine.java:1731-1741 (alt-objective reseed loop around SLP), RelaxationRecovery.java:118-139 (aware x seed cross-product around SLP).
- CLAIM: The "seed from another direction / retry inertia-aware" recovery idea is implemented three times at three layers: inside SLP (self-recursion to inertiaAware, 311-314), inside dualChain (alternateObjectives seeds then SLP, 1731-1740), and inside RR (awareOptions x seeds, 121-139).
- EVIDENCE: SlpSolve.java:311-314 re-enters `optimize(... inertiaAware=true ...)` when a non-aware non-bestEffort phase-1 misses; dualChain iterates alt objectives to seed SLP (1734-1736); RR loops `for (boolean aware) for (seed)` calling `optimizeBestEffort(..., aware)` (121-126).
- IMPACT: simplicity: three nested reseed/aware ladders make the recovery control flow hard to reason about and mean the same SLP can be entered many times per solve with slightly different seeds and awareness.
- PROPOSAL: Hoist a single reseed/awareness policy above one SLP entry point; measure whether the SLP-internal self-recursion (311-314) is still needed once callers own the ladder.
- CONFIDENCE: 0.72
- DEPENDS-ON: A04-1, A04-9
