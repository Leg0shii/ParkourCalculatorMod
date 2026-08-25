# FINAL REPORT: re-founding the angle solver on a convex-plus-low-dimensional-residual core

Closing synthesis of the 2026-08 campaign. Everything below is backed by a measurement whose method and
capture are named; the supporting docs are `stage0-copt/FINDINGS.md`, `stageA/SYNTHESIS.md`,
`stageB/SYNTHESIS.md`, `SPEC.md`, `RESEARCH-DOSSIER.md`, `RESULTS.md`, `stageE/poc-residual-validation.md`,
`stageE/byte-exact-roundtrip.md`. Nothing here is merged; this is a PROPOSED path. COPT was used as a
research oracle only and is never shipped. No git operations were performed; the working tree carries only
the pre-existing feature-branch changes plus two env-gated test files (StructureDump, ReplayYaws) and the
`research/copt/` harness. The shipped path stays green on `:core:test -PslowTests`.

No em dashes. No code comments in any code.

---

## 1. The headline answer

YES, a clean convex/analytical global core exists for this problem, for the sizes the tool actually hits
(n <= ~49, single- and multi-jump), and it is SIMPLER than the incumbent. The problem is a
linearly-constrained constant-modulus program that REDUCES to a convex dual/active-set solve plus a
LOW-DIMENSIONAL nonconvex residual over the "vanishing-costate" ticks. Measured proof: COPT solves the
exact nonconvex constant-modulus QCQP GLOBALLY in under 0.5 s at n<=49 (0.27 s at n=39/4 jumps), and a
prototype that solves the convex relaxation and then branches only over the degenerate ticks reaches that
global optimum within 1e-5 to 3e-5 b on every coupled case tested (j021, j008b, loopmm). The incumbent's
famous multi-jump failure is NEITHER an intractable nonconvexity NOR a genuine circle-vs-disk gap of any
size that matters (the disk relaxation is loose by only ~1.6e-3 b at 1-4 ticks); it is purely the
closed-form recovery DEFAULTING the degenerate ticks and then falling to a full-n local search that
thrashes. This is the single most valuable finding of the campaign, and it is measured and reproducible.

The collapse is real but NOT total: it is bounded to the coupled/degenerate multi-jump class, which is
exactly the known-hard class. On single and half-angle jumps the shipped byte-exact search is already
optimal and should be kept; on gate/momentum jumps the gate must be modeled as a small mixed-integer layer.
An honest, measured statement of where each mechanism applies is in section 6.

---

## 2. Stage 0 sub-question: H1 vs H2, settled with numbers

The open question was whether the multi-jump recovery failure is H1 (a genuine circle-vs-disk / SOCP gap)
or H2 (pure dual-face degeneracy). MEASURED ANSWER (COPT SOCP disk slack + Shor SDP rank + nonconvex QCQP,
FINDINGS section 1):

- It is a SMALL H1 ON TOP OF H2, and the distinction is largely moot. On the coupled cases the SOCP disk
  relaxation IS loose (throttles |u_t| below m_t at 1-4 low-authority ticks: j021 t12 by 0.083, j008b t1
  by 0.101, loopmm t0 by 0.095), which is H1; AND the Shor SDP is rank 2-3 (eig2/eig1 <= 0.024) with no
  tighter bound than the disk, which is H2. But the objective looseness is only ~1.6e-3 b, and the
  constant-modulus QCQP is globally solvable in < 0.5 s.
- On single and easy multi-jump the SDR is rank-1 tight (eig2/eig1 <= 9e-8, 0 throttled ticks): the
  hidden convexity holds outright, recovery is closed-form exact.
- The shipped dual BOUND is tight on easy captures (matches COPT SOCP to ~1e-6) and loose on coupled
  captures ONLY from non-convergence (j021 shipped 1067.8898 vs COPT-converged SOCP 1067.86548, loose by
  0.024; COPT solves the same SOCP in < 20 ms). So "the dual bound is loose" is the shipped solver not
  converging, not a fundamental gap.
- CAVEAT measured: byte-exact can OUT-reach the continuous constant-modulus optimum by up to ~1.0e-2 b via
  favorable half-angles (LUT norm>1), so COPT is a near-exact reference, NOT a strict upper bound;
  byte-exact certification stays mandatory.

---

## 3. The mathematical classification and the simplest reduction (the prize)

The continuous relaxation IS a LINEARLY-CONSTRAINED CONSTANT-MODULUS PROGRAM: a linear functional of the
stacked per-tick input vectors, subject to linear inequality walls and n per-tick unit-modulus quadratic
EQUALITIES, with a causal banded friction-convolution map from inputs to position. Equivalently, the
optimization of a linear functional over a product of circles (a scaled torus) with linear side
constraints. It is milder than the general constant-modulus QP (the objective is affine, not quadratic),
and it is exactly the fixed-thrust structure of lossless convexification (LCvx).

The SIMPLEST EQUIVALENT REDUCTION (proved from KKT, measured, and independently corroborated by four
literature families in Stage D): at any KKT point, every NON-degenerate tick is determined in closed form
by the convex dual, u_t = m_t g_t/|g_t| where g_t is the tick's costate (objective pull minus active-wall
pull); only the DEGENERATE ticks (g_t = 0) have a free direction. So the problem REDUCES to a convex
dual/active-set solve PLUS a nonconvex residual whose dimension equals the number of vanishing-costate
ticks. That dimension is:
- MEASURED 0-1 on redirect/neo jumps (the majority), and 10-22 on momentum/nix jumps;
- bounded by two independent theorems: discrete-time LCvx caps it at n_x - 1 = 3 ticks, horizon-independent
  (D01, Luo-Echigo-Acikmese 2024/2025); and the Pataki rank bound applied to the RESIDUAL SDP (not the
  full lift, a rigor correction from D03) gives residual rank 2-3, matching the measured eig-ratios.
- Even the large-count momentum cases are globally solved by COPT in < 0.5 s, so their EFFECTIVE difficulty
  is low (a coordinated low-DOF momentum phase, not a free high-dimensional torus). The operative hardness
  is how tightly the degenerate directions are constrained, not their count.

The residual is a MIN-SLACK FEASIBILITY problem (D14): at a degenerate tick, maximizing objective is
identical to driving the active walls to their bound, so min-slack s* = 0 means zero gap (pure degeneracy,
H2) and s* > 0 means a genuine circle-vs-disk gap of size ~s* (H1). The residual therefore MEASURES H1/H2
per instance and is solved exactly per degenerate-count k: k=0 nothing, k=1 closed-form arc intersection,
k=2 univariate reduction, k=3 complex-SDR tightness test (Ai-Liang-Yuan 2024, exact for <=4 complex
constraints), k=4 tiny spatial B&B, large-k momentum via the wall-coupling-graph structure or Riemannian
descent. All pure-Java, dependency-free, with a REAL infeasibility certificate (fixing the incumbent's
BnB-null false-negative). Full literature threads are tagged in SPEC section 4.6 and the dossier.

---

## 4. Consistency questions, answered

- Q1 free-start == fixed-start once p0 is picked? YES structurally (rigid-translation separability proven
  byte-exact by FreeStartTranslationTest; A06/F4), with unreconciled ORCHESTRATION divergences (scored-vs-
  raw feasibility fork, two start references, jointWrapClose true/false, startTick==0-only). These are bugs
  to fix, not necessary differences; the baseline mechanism is "solve pinned at box center, then one rigid
  translation."
- Q2 receding-horizon == single-jump per window? NO. Necessary differences (surrogate objective + centered
  solve on lead-ins). Gaps to close (F9): dropped seam-straddling constraints, missing per-window
  RelaxationRecovery/LevelSetAscent/byte-exact-race. Clean fold: make the terminal window delegate to the
  point-solve chain.
- Q3 capability gaps: enumerated in the SPEC section 3 consistency matrix. The load-bearing ones: no
  caching anywhere (model rebuilt 19-44x/dualChain, cross-window warm-start unbuilt, stepRange dead);
  smoothing is not seen by the dual/SLP/BnB/receding-horizon; dF handled by two pin mechanisms with
  different tolerances and dropped by RelaxationRecovery and cross-seam; free-start absent from the
  FAST-explore arm; defaults not single-sourced.

---

## 5. Can the four smoothing stages collapse into one?

MEASURED YES, into ONE give-back-constrained order-1 trend-filter (minimize the total variation of the turn
rate, ||D2 theta||_1, with the L0 reversal count as the accept-gate) against ONE shared pre-smoothing
reference objective (an epsilon-constraint obj >= best - X), which on the ARCH-1 path specializes to a tiny
enumerated tie-break over the 0-4 degenerate ticks (D13). This fixes the measured stacked give-back
double-count (~1.63e-2 b, F6) and the wrong-metric SmoothingPolish (F3). IMPORTANT measured caveat: the
single term is convex in theta but NOT in the relaxation's u variables (theta = atan2(u) is nonconvex, and
adding it destroys the LCvx tightness that makes the dual recovery exact), so it CANNOT live inside the
convex program as the context pack originally hoped. It lives either as a post-solve theta-space pass or,
uniquely, JOINTLY inside a Riemannian solve on the product of circles (D05), which is the only method
family that can optimize reach + walls + smoothness in one objective. Either way the four stages become
one mechanism against one reference.

---

## 6. The PROPOSED architecture (prioritized, with measured evidence and the DEAD ends)

A HYBRID collapse, matched to the measured regimes. This is a proposal to prototype and merge
incrementally, not a merge.

PRIORITY 1 (the headline, largest quality win on the known-hard class): the ARCH-1 recovery core.
- Replace the closed-form recovery's "default the degenerate tick then fall to full-n SLP/ILS" with:
  (1) a CONVERGED convex disk/dual solve (a from-scratch pure-Java primal-dual interior-point SOCP per
  D12, dependency-free, since no redistributable pure-Java SOCP exists and the shipped AL-FISTA fails at
  n=353); (2) closed-form non-degenerate ticks; (3) the residual solve over the degenerate ticks (per-k
  exact, section 3), with a real infeasibility certificate; (4) an objective-aware byte-exact snap
  (sphere decoding, D07/D11, replacing the DEAD LatticeRepair) + the existing BucketAscent/ILS as the
  byte-exact finisher.
- MEASURED win: on j021 this recovers +1.4e-3 b over the shipped THOROUGH result (byte-exact 1067.863789
  vs shipped 1067.862397), reaching the COPT-proven optimum. This is the class the incumbent provably
  cannot solve (its recovery is 0.34 b infeasible on j021, 2.89 b on thousand).
- MEASURED DEAD ends within this: the naive "fix all non-degenerate ticks at disk directions, free only
  the degenerate" is INFEASIBLE (must re-optimize the rest convexly per branch node, poc-residual-
  validation.md); "make the dual converge" alone tightens the bound but never fixes the recovery (the
  face is degenerate, not unconverged); raising MAX_ITER is a measured non-monotone lottery (dual-newton
  audit); SMT-FP as a searcher is dead (it verifies only). The pure-Java port needs a NEW "convex solve
  with a subset of ticks pinned to arbitrary yaws" entry (the shipped convex path bails on facing
  constraints), which is the first implementation step.

PRIORITY 2 (the gate/momentum class): model the inertia gate as ~2n big-M indicators (F5, D11), hybrid
(banded fast path + a small mixed-integer branch only on cold miss), which lands the gate captures AND
gives a real infeasibility certificate where the incumbent BnB returns an uncertified null (F10). MEASURED
motivation: the clamp-free continuous optimum does NOT transfer byte-exact on loopmm (replays to
-279.324 vs -279.299), because the gate is loopmm's mechanism.

PRIORITY 3 (free simplifications and the smoothing collapse): the one give-back-constrained trend-filter
(section 5); free-start as center-pin + one rigid translation (F4); one dF representation shared across
recovery stages (unify FacingPrefold/YawTies, F14); one shared solve tail so every terminal path carries
the same capability set (fixes the FAST-explore and legal-OPTIMIZE-only gaps, F11); delete the dead
LatticeRepair/stepToSinBucket.

PRIORITY 4 (orthogonal perf levers, ship independently, no rework needed):
- Cap buildHessian's inner loop at each wall-pair's last coupled tick: BIT-IDENTICAL, -28% of solver leaf
  CPU (B05/SB3). Highest value, zero risk.
- Route the anytime polishers' rescoring through the DEAD stepRange (+ incremental toGameFacings/
  maxViolation): ~2x on the dominant cost (B04/B05/F12).
- Do not run SmoothingPolish when Smooth is OFF (reclaims up to 94% of a Smooth-off solve, B04).
- THOROUGH bound-matched early-exit (6/13 captures reach the optimum at FAST but burn the full budget, B06).
- Cross-window dual warm-start (2.5-5x on long runs, B04/SB6) when long runs become an inner loop.

KEEP (measured already-optimal): the shipped fast path + byte-exact BucketAscent/ILS on single and
half-angle jumps. ARCH-1's continuous optimum is NOT the target there; the byte-exact search exploits
half-angles the continuous model lacks and already wins (j005/j016/j019 shipped over-reaches the continuous
optimum by up to 1.0e-2 b). This is the honest boundary of the collapse.

---

## 7. Dependency stance and performance

DEPENDENCY STANCE: the recommended architecture is PURE-JAVA, DEPENDENCY-FREE. D12 verified no
redistributable pure-Java SOCP/conic solver exists (ojAlgo SOCP is partial and needs Java 22+ FFM, dead on
the Java 8 Forge loaders; JOSQP is QP-only; commons-math is LP-only and was already dropped), and every
native/copyleft option (ECOS, SCS, OSQP, Clarabel, ALGLIB, SCIP) is strictly worse than the imgui native
burden the project already fights and was measured net-negative for even a pure-Java LP library (A04-7). So
the convex kernel is a from-scratch pure-Java interior-point SOCP; the residual solve is pure-Java
per-k exact methods; the snap is pure-Java sphere decoding. COPT is NEVER shipped. No new dependency is
proposed.

PERFORMANCE: the recommended core is within the measured envelope. COPT solves the full nonconvex QCQP in
< 0.5 s at n<=49 (the residual-only solve is strictly cheaper), and the convex SOCP Schur-reduces to the
tiny wall count (< 30 even at n=353), so a pure-Java IPM is low-single-digit ms at n<=49 and tens of ms at
n=353 (D12; realized wall-clock is the one Stage E measurement still to take). The corrected measured
envelope (B01) is FAST median 302 ms, p90 895 ms, p99 4163 ms, max 4882 ms with a 24-47 s accepted-fail/
slow-success tail; the priority-4 levers reduce it, and ARCH-1 must beat shipped THOROUGH on the coupled
class without regressing the 100-900 ms typical band.

---

## 8. What was measured DEAD (so nobody re-attempts it)

- Naive "fix non-degenerate ticks at disk directions" residual: INFEASIBLE on j021/j008b (measured).
- "Make the dual converge" as the fix for multi-jump recovery: tightens the bound (0.024 b on j021) but
  never fixes the degenerate-face recovery; the recovery needs the residual solve, not a tighter bound.
- Raising CostateDualSolver.MAX_ITER: non-monotone lottery, breaks the corpus (dual-newton audit,
  re-confirmed by Stage 0's finding that the bound is near-tight once converged).
- SMT-FP as a route searcher: verifies only, dead as a searcher (memory + D11 re-confirmed).
- Snapping the continuous optimum directly on loose-degenerate or half-angle captures: byte-exact
  suboptimal (j008b -0.2196, j005 -41.298); must search the byte-exact objective at those ticks.
- Full-size moment-SOS / Shor SDP on the shipped path: oracle-only, no pure-Java SDP, and no tighter than
  the SOCP disk on the coupled cases (D10/D06); the value is the RESIDUAL rank/certificate, not a shipped
  SDP.
- Re-adding an LP/QP/SOCP library to core: measured net-negative packaging across three loaders (A04-7,
  D12).

---

## 9. Honest limits and the open Stage-E measurements

The campaign PROVED the mechanism and MEASURED its wins and boundaries, but did not port the full solver to
pure Java (the convex IPM + the k>=2 residual robustness + the gate MIP + the sphere-decode snap are
designed and per-k specified, not yet implemented in Java; the shipped convex path's facing-constraint bail
means a new pinned-tick entry is needed first). The remaining measurements, each with the experiment
named, are:
- The pure-Java IPM SOCP realized wall-clock and robustness at n<=49 and n=353 vs the COPT SOCP references.
- The k=2-4 residual solve pure-Java robustness and latency (k=0/k=1 are near-certain; k>=2 is the risk).
- The objective-aware byte-exact snap landing j008b at -0.197 byte-exact (the sharpest target).
- The gate-MIP landing loopmm/dsf-neo with a real infeasibility certificate, within the envelope.
- The one-term smoothing A/B on the hpk corpus (reversal sums vs the four-pass stack).

The honest cannot-fully-collapse statement, forced by the numbers: on single and half-angle jumps the
continuous convex core is NOT the right tool (byte-exact out-reaches it), so the byte-exact search stays;
and the full pure-Java global core, while proven tractable, is a real implementation project, not a patch.
But for the coupled multi-jump recovery-breakdown class that motivated this campaign, a clean convex-plus-
low-dimensional-residual core exists, is globally optimal, is faster than the incumbent's thrashing
fallback, and is dependency-free. That is the collapse the evidence supports.

---

## 10. Deliverables (paths)

- docs/research/solver-rework-2026-08/00-context-pack.md, 01-plan.md (kept live)
- docs/research/solver-rework-2026-08/stage0-copt/FINDINGS.md (H1/H2 + bound tightness, measured)
- docs/research/solver-rework-2026-08/stageA/SYNTHESIS.md (+ agentA01..A10.md)
- docs/research/solver-rework-2026-08/stageB/SYNTHESIS.md (+ agentB01..B06.md, orchestrator-timings.md)
- docs/research/solver-rework-2026-08/SPEC.md (capability catalog, connectivity map, consistency answers,
  the mathematical-framing mandate, invariants/non-goals, Lens-5 verdict)
- docs/research/solver-rework-2026-08/RESEARCH-DOSSIER.md (+ stageD/agentD01..D14.md)
- docs/research/solver-rework-2026-08/RESULTS.md + stageE/{poc-residual-validation.md, byte-exact-
  roundtrip.md}
- research/copt/ (the COPT harness, outside every module, with README)
- core/src/test/.../anglesolver/{StructureDump.java, ReplayYaws.java} (env-gated, shipped path green)
