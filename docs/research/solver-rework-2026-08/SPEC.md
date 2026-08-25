# SPEC: the angle solver as a specification (behavior, math class, consistency, and the collapse verdict)

Stage C canonical output. Authored by the orchestrator synthesizing Stage 0 (COPT), Stage A (code
analysis) and Stage B (benchmarking). This is the CONTRACT: a capability not here does not exist; a
claim here without a measurement is a spec defect. When a later stage finds this wrong, STOP, amend
here, record why.

Every quantitative claim carries its measurement. No em dashes, no code comments in any code. COPT is a
research oracle, never shipped.

Cross-references: `stage0-copt/FINDINGS.md` (H1/H2 + bound tightness, all measured), `stageA/SYNTHESIS.md`
(F1..F15 + the consistency matrix + collapse opportunities), `stageB/*` (timing + waste).

---

## 1. CAPABILITY CATALOG (behaviors, not implementations)

Each capability is a BEHAVIOR with inputs, outputs, and interaction rules. "Today" is the measured
current behavior; "Target" is the north-star behavior; the gap is marked.

### C1. Point solve (the core)
- INPUT: a per-tick physics scenario (fixed keys/jump/sprint/sneak/ground-air per tick, start position or
  box, initial velocity), a set of positional walls (X/Z linear bounds, corridors, footprints,
  velocity/relative walls), and exactly one objective (MAX or MIN of one position axis at one tick).
- OUTPUT: a per-tick yaw sequence that is BYTE-EXACT feasible (replayed through ExactJumpModel, viol 0)
  and reports the objective the realized facing chain achieves.
- SEMANTICS: the yaw is the only decision variable per tick; position is affine in the per-tick
  constant-modulus input vectors (section 4). Feasibility is byte-exact (FEAS_TOL=0). Objective is a
  linear functional at one tick.
- TODAY vs TARGET: single jumps and easy multi-jump solve to global optimality in ~0.1 ms to ~140 ms
  (closed form) and byte-exact-certify. Coupled multi-jump solves feasibly but leaves measured objective
  headroom: FAST j021 is 0.179 b below the true optimum, THOROUGH-12s is 1.5e-3 b below (Stage 0 section
  3; COPT true 1067.863880). TARGET: return the global optimum (COPT proves it is reachable in <0.3 s at
  n<=49; section 4) within the perf envelope.

### C2. Free-start solve
- INPUT: C1 plus a start BOX (px in [pxLo,pxHi], pz in [pzLo,pzHi], and optionally velocity ranges).
- OUTPUT: the best start position within the box AND the yaw sequence, jointly optimal.
- SEMANTICS: the start position enters every wall linearly (wall coefficient p0coef = +-tc, tc = 1 for a
  single-tick wall, 2 for a t1+t2 sum, 0 for a t1-t2 difference; verified compileWall:248-249) and the
  objective with coefficient +-1. For any FIXED yaw sequence the whole trajectory is a RIGID TRANSLATE in
  p0, so p0 is separable (F4, proven byte-exact by FreeStartTranslationTest).
- INTERACTION: composes with C1, C3 (dF), C4 (smoothing), C6 (multi-jump). MUST behave IDENTICALLY to a
  fixed-start solve once p0 is chosen (section 3, Q1).
- TODAY vs TARGET: free-start is a joint dual + sharpening ladder (P0_SMOOTH conditioner ladder
  {0.05, 2e-3, 5e-4}) that has known seed fragility, largely closed by the gh-386 center-derivation. It
  commits p0 on window 0 only for multi-jump and never re-optimizes against the tail (A06-12). TARGET:
  "solve pinned at box center, then one rigid PathTranslation pass" as the baseline, joint dual reserved
  for the fixed-start-infeasible-but-translatable residual; a final whole-chain translation for multi-jump.

### C3. dF (facing) constraints
- INPUT: C1 plus per-tick facing constraints: fix yaw_t (dF=const), pin yaw_t = yaw_{t-1} (dF=0, "do not
  change facing"), or bound the turn (a sector).
- OUTPUT: a yaw sequence honoring the facing constraints, byte-exact.
- SEMANTICS: dF=0 is theta_t = theta_{t-1} (LINEAR in theta, a dimension reduction / a pin); a general dF
  is a per-tick PHASE/sector constraint on theta_t. Nonlinear in the input vectors u (a rotation
  coupling), so it is NOT a linear wall; the closed-form/dual path handles it by FacingPrefold pins, SLP
  by YawTies.
- INTERACTION: composes with C2 (free-start): the pins reduce dimension, translation still separable.
- TODAY vs TARGET: dF is handled by TWO pin mechanisms with DIFFERENT tolerances (FacingPrefold 1e-9,
  YawTies 1e-6; F14, a latent inconsistency); non-zero dF is DECLINED engine-wide on multi-jump (a
  capability gap); RelaxationRecovery BAILS on any facing wall (F8), so the dF-chain captures get null
  from the working degenerate-face rescue. TARGET: one dF representation shared by all recovery stages;
  non-zero dF supported on multi-jump; dF expressible in the residual solve (section 4).

### C4. Smoothing (smooth TAS)
- INPUT: C1 plus a smoothness preference (a max objective give-back budget).
- OUTPUT: among byte-exact-feasible near-optimal yaw sequences, the one with the fewest turn reversals /
  least jerk, within the give-back budget.
- SEMANTICS: smoothness is measured as reversal count (L0 of sign changes of D1 theta) and jerk
  (||D2 theta||_1); it trades a bounded slice of objective (the give-back).
- INTERACTION: composes with all; must not double-count the give-back.
- TODAY vs TARGET: FOUR stages (turnCost search bias, DeWiggle, SmoothingPolish, SmoothFaceRecovery),
  three of which minimize the same reversal count and one (SmoothingPolish) using a measured-blind convex
  metric (F3). Give-back caps STACK to ~1.63e-2 b because each floors against its own input (F6). TARGET:
  ONE smoothness mechanism against ONE shared pre-smoothing reference objective; see section 4 for why it
  cannot be a convex term but IS naturally the residual-resolution step over the degenerate ticks.

### C5. Byte-exact certification
- INPUT: any candidate yaw sequence.
- OUTPUT: the byte-exact realized trajectory (ExactJumpModel) and the max constraint violation.
- SEMANTICS: MANDATORY on every reported result; FEAS_TOL=0. The continuous model is a near-exact but NOT
  strict reference: byte-exact can OUT-reach the continuous constant-modulus optimum by up to ~1.0e-2 b
  via the half-angle norm>1 effect (Stage 0 section 4, measured on j019). So certification is not a mere
  snap; it can gain.
- TODAY = TARGET (this is a non-goal to change; it is the invariant).

### C6. Multi-jump / route solve
- INPUT: a scenario spanning multiple jumps (multiple grounded jump ticks in the window) with walls across
  the whole span and one final objective.
- OUTPUT: one byte-exact yaw sequence for the whole route.
- SEMANTICS: jumps do NOT add nonconvexity (per-tick physics only); difficulty is coupling of walls across
  seams (the vanishing-costate degenerate set, section 4). Receding-horizon windows are an implementation
  choice, not a semantic requirement.
- TODAY vs TARGET: receding horizon (window 10, commit {3,1}) which does NOT behave identically to a
  single-jump solve per window (F9: surrogate objective + centered solve on lead-ins are necessary;
  dropped seam constraints + missing RelaxationRecovery/LevelSetAscent/byte-exact-race are gaps). No
  cross-window dual warm start (unbuilt, A07-12). TARGET: window solve == the point solve C1 modulo the
  necessary lead-in surrogate; cross-window warm starts; a global solve where the span is small enough
  (COPT solves n<=49 globally, so most routes fit).

### C7. Run-ticks search
- INPUT: C1/C6 plus a max run-ticks budget; the search inserts grounded W+sprint ticks before jumps.
- OUTPUT: the run-tick-count combination with the best objective.
- SEMANTICS: changes the scenario (adds ticks), so it wraps C1/C6 as an outer search.
- TODAY vs TARGET: run-ticks candidates do NOT get smoothing (smoothFinal=false) and skip the engine
  face-walk (A08-13). Ranking therefore uses a slightly different objective than the final shown result
  (A09-6). TARGET: consistent smoothing/scoring across candidates and the final.

### C8. Effort / budget
- INPUT: an effort tier (FAST first-feasible, THOROUGH budgeted anytime, CUSTOM graph) or a time budget.
- OUTPUT: the best result within the budget, honestly timed.
- TODAY vs TARGET: FAST has NO overall deadline (bounded only by internal caps; F15); Smooth (TAS) runs
  AFTER the graph deadline with a fresh deadline/8 budget, so OPTIMIZE(10)+Smooth reaches ~11.25 s actual
  (F15, reported honestly now). FAST leaves large objective gaps on coupled multi-jump (first-feasible by
  definition; j021 FAST 0.179 b short). TARGET: one budget policy across all paths; FAST should still be
  honest about multi-jump sub-optimality.

---

## 2. STAGE CONNECTIVITY MAP (the real node graph)

Measured by A09 via GraphDump (verbatim node/edge counts): FAST = 32 nodes / 54 edges, OPTIMIZE = 43 / 81,
EXPLORE = 9 / 18. FAST and OPTIMIZE are ONE builder (`BuiltinGraphs.build`) parameterized by three
booleans (stop-on-feasible `sof`, ils-exhaustive `ilx`, window `win`); single-vs-multi is an internal
`rJumps` router, not a separate entry. Five distinct solve ENTRY PATHS exist (A09-11): single-seed,
multi-horizon, free-start, legal, run-ticks.

Terminal solve stages (the "recovery" tail) in dependency order:
- `dualChain` (DualChainNode): ClosedFormSolve (dual + margin ladder + FacingPrefold dF) -> SLP (from dual
  seed, then reseeded from alternate directions) -> RelaxationRecovery (disk AL-FISTA + dither/projection
  + budgeted SLP). This is the single-jump / small-span path.
- `recedingHorizon` (RecedingHorizonNode -> LongRunSolver): window slicing; each window uses ClosedFormSolve
  internally as a surrogate, SLP per window on gap. Does NOT call RelaxationRecovery/LevelSetAscent
  per-window (F9).
- Byte-exact search: `bnb` (BoundPrunedRecovery, gate-pattern B&B), `seamSweep`, `ilsPolish`, `wrapIls`,
  each followed by BucketAscentPolish-style ascent. OPTIMIZE-only mostly.
- `capCertify` (the one model-free optimality certificate: user cap at objective tick within 1e-6).
- `freeStartImprove`, `translatedStart` (free-start), `setupPeel`, `smoothing` (SmoothingPolish +
  DeWiggle, gated), `markSettled`, `report`, `router`.
- Engine post-graph: `smoothFacing` -> SmoothFaceRecovery (the final face-walk), gated by smoothLambda>0
  and smoothFinal.

CAPABILITY-PRESENT-ON-SOME-PATHS-BUT-NOT-OTHERS (the gaps that must close; A09-3/4, F11):
- The EXPLORE race arm (9 nodes) lacks free-start, capCertify, translate, recedingHorizon, setupPeel. When
  it wins a FAST solve, those capabilities are silently skipped.
- Legal-mode push (wrapIls legalScore) exists ONLY in the `ilx`/OPTIMIZE graph, so legal mode under FAST
  effort gets no legal push.
- Smoothing is 3 sites (SmoothingPolish in the graph runs even with Smooth OFF; DeWiggle gated; the engine
  face-walk post-graph), not one switch (A09-5, F15).
- Result selection is a decentralized threaded incumbent over a SHARED MUTABLE scenario whose startPos is
  mutated in-place by >=6 nodes, consistent only by per-node re-verification convention (A09-7) - the
  surface behind the "Optimize dropped-feasible" class.

---

## 3. THE CONSISTENCY QUESTIONS, answered with file:line evidence

### Q1. Does free-start behave IDENTICALLY to a fixed-start solve once p0 is picked?
ANSWER: YES structurally, with unreconciled divergences at the orchestration layer. Every free-start node
pins `scenario.startBox = StartBox.pinned(...)` before the downstream solve, and FreeStartTranslationTest
asserts byte-exact translation invariance (doubleToRawLongBits equality across 10 yaw draws x 3 captures;
A06, F4). MEASURED divergences that remain (A06-2, each file:line in agentA06.md): routers score with
translation (`Scoring.scoredViol`) while node solvers self-certify raw-pinned (a feasibility fork, not yet
reconciled); two start references coexist (seed lane pins the clamped seed at runJob:954, the joint dual
re-centers to box center at solveJointBest:118); `jointWrapClose` defaults true in the API but is forced
false in the graph node; dF composes but has no downstream repair; free-start is only derived at
startTick==0. VERDICT: the DIVERGENCES are orchestration bugs/inconsistencies, not necessary differences;
the underlying math (rigid translation) makes identical behavior achievable (collapse opportunity 2).

### Q2. Does receding-horizon behave IDENTICALLY to a single-jump solve on each window?
ANSWER: NO. Necessary (keep) differences: lead-in windows solve a surrogate "any feasible" Z/MAX objective
(LongRunSolver:215-216) and a centered/robust non-hugging solve (:325-326, :358-359), which is correct
(hugging a surrogate wall is pure liability, and lead-in centering was measured to move j001 133 -> 95 ms).
Gaps (bugs/capability holes, F9, A07): the window solver OMITS RelaxationRecovery (vs dualChain:1723),
omits the primary-SLP LevelSetAscent top-up (:325-327 vs :1719), has no per-window byte-exact race (up to
~0.05 b left on interior windows, cf. j022 LP-vs-byte 0.0494 b), and DROPS seam-straddling
relative/velocity/dF constraints in the following window (sliceConstraints:420-432; the angle-solver.md
audit text "re-checked as trivial tick-0" is STALE, resolved against the code). The full-run byte-exact
re-verify backstops false success. VERDICT: make `solveWindow(last=true)` delegate to `dualChain`; enforce
seam constraints in the following window.

### Q3. Which capabilities are implemented on some stages but not all? (the full enumeration)
See the merged CONSISTENCY MATRIX below (from stageA/SYNTHESIS.md, verified). Summary of the gaps:
- CACHING: absent almost everywhere. No cross-call JumpLinearModel/precompute cache (24 `new
  JumpLinearModel` sites, rebuilt ~36x per dualChain; A02-5). No cross-window dual warm start (A07-12). No
  cross-race-arm or run-ticks-candidate result cache (A09-13). Only intra-margin-ladder lambda warm start
  exists (ClosedFormSolve).
- SMOOTHING-AWARE: only the smoothing stages carry it; the dual, models, SLP, BnB, receding-horizon all
  optimize the RAW objective (turnCost is a post-hoc ranking bias, dropped in the 3-arg window Objective;
  A07-8).
- dF: closed-form/dual (FacingPrefold) and SLP (YawTies) only; RelaxationRecovery, BnB-bounds, and
  receding-horizon-cross-seam all drop it; non-zero dF declined engine-wide on multi-jump.
- FREE-START: dual (FreeP0) + FreeStartSolve; absent inside SLP/BnB/RelaxationRecovery (orchestrated
  above them by re-invocation); FAST-explore arm lacks it.
- DEFAULTS: not single-sourced. SLP budgets 40/60 vs 160/220 (RR) vs graph-node vs BnB-tree; dF pin
  tolerances 1e-9 vs 1e-6; jointWrapClose true/false fork.

### CONSISTENCY MATRIX (capability x subsystem; P present, - absent, ~ partial)

| Subsystem | Caching | Smoothing-aware | dF | Free-start | Defaults single-sourced |
| --- | --- | --- | --- | --- | --- |
| CostateDual | ~ intra-ladder | - | - (stripped; f2f byte-viol 135 deg) | P FreeP0 | ~ |
| Linear+Exact models | - (stepRange dead) | - | - (compileWall rejects F) | ~ constPos reads box | ~ |
| ClosedForm | ~ warm lambda | ~ recoverFace hook (Smooth-only) | P FacingPrefold | - external | ~ |
| RelaxationRecovery | ~ warm lambda | - | - BAILS on F/EQ | - | ~ |
| SLP | - rebuilds each call | - | ~ YawTies dF=0 only | - external | ~ |
| BnB | - fresh per node | - | ~ gate-only, F-blind bounds | - box as constant | ~ |
| FreeStart | ~ WindowCache | ~ scoredObjective | ~ bails on unbounded F | P (startTick==0) | ~ |
| RecedingHorizon | ~ result-memo only | - drops smoothLambda | ~ cross-seam dropped | ~ window-0 only | fixed |
| Smoothing stages | ~ metric cache | P | ~ DeWiggle ignores F | ~ via scenario | 3 statics, turnCost 8 sites |
| Engine/Graph | ~ per-context | ~ split, Polish fires Smooth-off | ~ non-zero dF declined | ~ FAST-explore NO | FAST deadline 0 |
| Discrete layer | - full forward | - (correct) | ~ FacingPrefold vs YawTies | ~ reaccumScore fork | MAX_ABS_GF only in wrapIls |

---

## 4. THE MATHEMATICAL-FRAMING MANDATE (highest weight; classification proved + the simplest reduction)

### 4.1 The exact class (proved from the model)

Fix the start and drop the gate/dF for the core (they are separable layers, section 4.5). Let
`u_t in R^2` be the per-tick input vector, `|u_t| = m_t` (the constant modulus, computed in
JumpLinearModel.precompute). Position is affine:
`p_k = p0_k + sum_{s<k} C(s,k) u_s`, `C(s,k) = (S[k]-S[s])/Phi[s]` (a CAUSAL, lower-triangular, banded
friction convolution; verified JumpLinearModel.coef). The objective `d^T p_objTick` and every wall
`a_j . p <= b_j` are therefore LINEAR (affine) in the stacked `u`. The ONLY nonconvexity is the `n`
per-tick 2-norm EQUALITY constraints `|u_t| = m_t`.

CLASSIFICATION (textbook): this is a **linearly-constrained constant-modulus program**: minimize/maximize
a LINEAR functional of `u` subject to LINEAR inequalities and `n` per-tick unit-modulus quadratic
EQUALITIES. Identifying `u_t in R^2` with `z_t in C`, it is the optimization of a linear functional over a
PRODUCT OF CIRCLES (a scaled torus `T = prod_t m_t S^1`) with linear side constraints. It is:
- an instance of QCQP with a linear objective and rank-two nonconvex equality constraints;
- MILDER than the general unit-modulus / constant-modulus quadratic program (UQP/CMQP, Soltanalian-Stoica
  2014) because the objective and constraints are AFFINE in `u`, not quadratic;
- exactly the fixed-thrust structure of LOSSLESS CONVEXIFICATION (LCvx, Acikmese-Blackmore 2011): a linear
  objective over `|u_t|=m_t` with convex state constraints. LCvx guarantees zero duality gap when the
  convex state constraints are active only at ISOLATED instants; our walls are active over INTERVALS
  (opposing corridors), which is exactly the LCvx boundary case that can lose tightness;
- geometrically, optimization over the OBLIQUE / product-of-circles Riemannian manifold (Absil-Mahony-
  Sepulchre 2008; Boumal 2023).

### 4.2 The simplest equivalent reduction (the prize; measured)

Do NOT stop at "constant-modulus QCQP." The problem REDUCES further, to a convex solve plus a
LOW-DIMENSIONAL nonconvex residual. Proof from KKT: at any stationary point with active wall set A and
multipliers lambda >= 0, define the per-tick COSTATE `g_t = c_t - (A^T lambda)_t` (a 2-vector: the
objective pull minus the active-wall pull at tick t). Stationarity on the circle `|u_t| = m_t` forces
`u_t = m_t g_t / |g_t|` WHENEVER `g_t != 0`. Therefore:
- every NON-DEGENERATE tick (`g_t != 0`) is determined in CLOSED FORM by the convex dual `(lambda, A)`;
- only the DEGENERATE ticks (`g_t = 0`, where the active walls exactly cancel the objective at that tick)
  have a FREE direction, which the modulus constraint pins to full length and which must jointly satisfy A.

So: **the problem reduces to (1) a CONVEX dual / active-set identification, plus (2) a NONCONVEX RESIDUAL
of dimension equal to the number of vanishing-costate ticks.**

MEASURED (Stage 0, COPT, exact): the degenerate set is TINY.
- Single jumps and easy multi-jump (j005/j016/j019/j022, f2f-without-dF): 0 throttled ticks, SDR RANK-1
  (eig2/eig1 <= 9e-8), disk == sphere == true. The reduction terminates at step (1): closed-form exact.
- Coupled multi-jump: 1 degenerate tick on j021 (t12, modulus slack 0.083) and loopmm (t0), 4 on j008b
  (dominated by t1). SDR RANK 2-3 (eig2/eig1 <= 0.024). Residual dimension 1-4.

This is EXPLAINED by the Pataki/Barvinok rank bound, applied CORRECTLY (amended per D03-4/D03-5, a rigor
fix): the bound `r(r+1)/2 <= m` on the FULL 2n+1 Shor lift counts ALL active constraints including the n
per-tick modulus equalities (`m ~ n + 2 + #active walls`), which for j021 gives only the loose
`rank <= 9`, NOT the measured 2-3. The TIGHT explanation is the KKT active-set reduction: the
non-degenerate ticks are rank-1 (costate-aligned), and Pataki applies to the RESIDUAL SDP over the
degenerate ticks alone, whose handful of constraints gives rank 2-3, matching the measured eig2/eig1 <=
0.024. Sturm-Zhang (2003) rank-one decomposition, in its complex/Hermitian strengthening (Huang-Zhang
2007, native to our complex u_t), EXTRACTS the exact constant-modulus solution when the residual SDR is
rank-1; Ai-Liang-Yuan (2024) give an a-priori tightness test for complex QCQP with up to 4 constraints,
exactly our few-active-wall regime. That is WHY COPT's spatial branch-and-bound solves the
constant-modulus QCQP GLOBALLY in < 0.5 s at n<=49 (it branches only where the residual carries rank),
and WHY ILS reaches within 2.8e-5 b of the COPT optimum on j021.

DEGENERATE-COUNT NUANCE (measured, do not oversimplify): the throttled-tick count is 0-1 on redirect/neo
jumps (j021 t12, j008b t1, loopmm t0, j1099, j1149, j155, j718) but LARGE on momentum/nix jumps (j1150
2x2bm nix 22 ticks, j828 13, j716 10), where a whole momentum-building phase is direction-degenerate (the
objective is indifferent to the perpendicular direction over the run-up, the axis-locked-momentum
structure of CONTEXT.md). CRUCIALLY, COPT still solves these GLOBALLY in < 0.5 s (j1150 0.46 s, j716
0.19 s, j828 0.07 s), so the EFFECTIVE nonconvex difficulty stays low even when the count is high: the
degenerate ticks form a coordinated low-DOF momentum phase, not a free high-dimensional torus. The
operative hardness measure is therefore NOT the degenerate COUNT but how TIGHTLY the degenerate
directions are constrained by active walls (j021's 1 tick is tight-and-hard; j1150's 22 are
loose-and-easy). ARCH-1's residual solver must be a SMART solver (spatial B&B with convex node relaxation,
or Riemannian trust-region, both of which COPT-verified handle both regimes), NOT a brute k-dimensional
angle grid (which is fine for k<=2 but blows up at k=22). This is the measured refinement that keeps
ARCH-1 honest on the momentum/nix class.

CRISP STATEMENT (for the researcher going to the literature):
> The angle-solve continuous relaxation IS a linearly-constrained constant-modulus program (a linear
> functional over a product of circles with linear walls and a causal banded friction map). It REDUCES to
> a convex dual/active-set solve that determines every non-degenerate tick in closed form, u_t = m_t
> g_t/|g_t|, PLUS a nonconvex residual whose dimension equals the number of vanishing-costate ticks,
> measured to be 0 to 4 and bounded by the Pataki rank bound r(r+1)/2 <= #active walls.

### 4.3 Why the shipped solver fails, in these terms (measured root cause)

The shipped closed-form recovery DEFAULTS the degenerate ticks to a fixed direction (the objective axis)
instead of SOLVING the small residual, producing a wildly infeasible point (j021: 0.34 b; thousand: 2.89
b; measured), then falls back to a FULL-n local search (SLP/ILS) that thrashes on the coupled corridors
and dithers to thread them byte-exact. The bound is left loose only by non-convergence (Stage 0 section 2:
shipped dualBound 1067.8898 vs COPT-converged SOCP 1067.86548 on j021). The CORRECT mechanism is: solve
the convex dual/bound (COPT does the SOCP in <20 ms; a converging Java SOCP or a fixed subgradient would
match), identify the degenerate set, then solve the 1-4 dimensional residual EXACTLY (enumeration / tiny
spatial B&B / SDR rank-reduction / a null-space projection). This is the single highest-value collapse
(section 6).

### 4.4 Standard relaxations and when each is provably tight (measured)

- SOCP DISK (`|u_t| <= m_t`): convex, an upper bound. TIGHT (disk == sphere) on single/easy (measured
  0 throttled ticks); LOOSE by ~1.6e-3 b at 1-4 low-authority ticks on coupled cases. Solve time < 20 ms
  (COPT). This is the RelaxationRecovery kernel (AL-FISTA), which does not converge at n~353 (j001 viol
  15.5; A03-14), so a proper interior-point SOCP is the kernel question for large n.
- SHOR / SDP lifting: rank-1 tight on single/easy; rank 2-3 (no tighter than the disk) on coupled. Bound
  equals the disk here. Solve time < 0.13 s at n=39. Rank reveals the residual dimension (Pataki).
- MOMENT / LASSERRE (level 2+): would close the residual on the coupled cases but is expensive; only the
  low-dim residual (1-4 vars) needs it, so a LOCAL moment/SDP on the residual is tractable.
- NONCONVEX QCQP (spatial B&B, the true optimum): global, gap ~0, < 0.3 s at n<=49 (COPT). The reference.

Single-jump closed-form recovery IS relaxation tightness (LCvx / S-lemma / TRS exactness: a single
quadratic or non-interfering constraints keep the SDR rank-1). The multi-jump failure is H1 (disk loose by
~1.6e-3 b at the degenerate ticks) ON TOP OF H2 (SDR rank>1 there), but both are SMALL and LOCALIZED; the
constant-modulus QCQP is globally solvable. The discriminating experiment is exactly Stage 0's SOCP slack
+ SDP rank readout (done).

### 4.5 The separable layers

- SMOOTHING as a constraint/term: MEASURED (F3, A08-7) that smoothness = one term convex in theta
  (`beta ||D2 theta||_1 + gamma ||D1 theta||_2^2`) but NOT convex in `u` (theta = atan2(u) is nonconvex in
  u), and adding it destroys LCvx tightness. So it CANNOT be a term in the convex program. BUT via the
  reduction (4.2): the straightaway ticks are costate-determined (already smooth); the reversals/dither
  live at the SAME degenerate/redirect ticks. So smoothness unifies as the RESIDUAL RESOLUTION RULE: among
  the feasible direction assignments for the degenerate ticks, choose the smoothest. This replaces the
  four post-passes with one smoothness-aware residual solve. (This is the mission's target C3/C4 reframed:
  not "smoothing as a convex term" (refuted) but "smoothing as the tie-break of the low-dim residual".)
- INERTIA GATE: `|v_axis * 0.91| < thr` zeroes an axis. MEASURED (F5, A05, A10-4) it folds to ~2n big-M
  indicator constraints (`velocityWalls` = inside-band side, `keepAliveWall` = outside-band side), fires
  destructively on 0-1 fed ticks, and is a per-(tick,axis) mixed-integer indicator. It is a small
  MIXED-INTEGER layer on top of the constant-modulus core (a MISOCP / big-M), branch only on the handful
  of gate-critical ticks. COPT models it exactly (addGenConstrIndicator); Stage E prototypes it.
- DISCRETE byte-exact layer: the integer sine LUT snaps directions to a ~0.0055 deg grid (bucket 3.14e-5
  to 1.54e-4 b; A10-1). This is a per-tick CLOSEST-VECTOR / integer-least-squares problem (LLL/Babai/
  sphere-decoding territory), MEASURED negligible (~1e-4 b accumulated). The half-angle norm>1 lets
  byte-exact OUT-reach the continuous model by up to 1.0e-2 b (Stage 0 section 4), so certification can
  GAIN; it is not a pure loss. Certification stays byte-exact through ExactJumpModel (SMT-FP VERIFIES but
  does not SEARCH; memory reference_byte_exact_certified_solver).
- dF: a per-tick PHASE/sector constraint on theta_t; dF=0 is a linear pin (dimension reduction). Nonconvex
  in u (rotation coupling). Handle as a pin (reduces the residual) or a theta-space sector.
- FREE-START: two box-bounded linear variables (p0coef sensitivity), SEPARABLE by rigid translation
  (F4, proven). Folds into the same convex dual (FreeP0) or a final translation pass.

### 4.6 Seed literature threads (tagged to each sub-question; all real, checkable)

- Convex core + exactness (why single/easy is closed-form): Acikmese & Blackmore, "Lossless
  convexification ...", Automatica 2011; Malyuta et al., "Convex Optimization for Trajectory Generation",
  IEEE CSM 2022; Polik & Terlaky, "A survey of the S-lemma", SIAM Review 2007; More & Sorensen (TRS);
  Ben-Tal & Teboulle (GTRS).
- The low-dim residual + rank bound (the key reduction): Pataki, "On the rank of extreme matrices in
  semidefinite programs...", Math. of OR 1998; Barvinok 1995; Sturm & Zhang 2003 and Ai & Zhang 2009 (SDR
  rank reduction, S-lemma with equality / the two-quadratic case); Burer & Monteiro (low-rank SDP factor).
- Constant-modulus / UQP (the exact objective class): Soltanalian & Stoica, "Designing unimodular codes
  via quadratic optimization", IEEE TSP 2014 (MERIT); the power-method-like / MM iterations.
- General QCQP + SDR: Luo, Ma, So, Ye, Zhang, "Semidefinite Relaxation of Quadratic Optimization Problems",
  IEEE SPM 2010; Park & Boyd, "General heuristics for nonconvex QCQP" (suggest-and-improve), 2017.
- Riemannian product-of-circles / oblique manifold (the native geometry): Absil, Mahony, Sepulchre,
  "Optimization Algorithms on Matrix Manifolds", 2008; Boumal, "An introduction to optimization on smooth
  manifolds", 2023; Manopt/Pymanopt.
- MIMO / hybrid beamforming with constant-envelope (directly analogous constant-modulus + linear):
  Mohammed & Larsson (constant-envelope precoding); Sohrabi & Yu; Yu et al. MO-AltMin (manifold
  alternating minimization for constant modulus); sphere decoding / Schnorr-Euchner for the integer-LS.
- Phase retrieval analogue (the dither/projection realization is Gerchberg-Saxton): Candes, Strohmer,
  Voroninski (PhaseLift); Gerchberg & Saxton; Wirtinger flow (Candes-Li-Soltanolkotabi).
- Global polynomial optimization (to close the residual): Lasserre moment-SOS; Parrilo SOS; sparse/
  correlative TSSOS (Wang-Magron-Lasserre); complex moment-SOS (Josz-Molzahn).
- The discrete layer: closest-vector / LLL / Babai; sphere decoding (Schnorr-Euchner); mixed-integer SDP
  and big-M indicators for the gate; bit-precise SMT-FP (Bitwuzla) as a per-window VERIFIER only (memory:
  it verifies, does not search).

---

## 5. INVARIANTS AND NON-GOALS

INVARIANTS (must hold):
- Byte-exact certification through ExactJumpModel on every reported result; FEAS_TOL=0. The continuous
  model is a near-exact reference, NOT a strict bound (byte-exact can gain up to ~1e-2 b via half-angles).
- Core stays Minecraft-free; no MC/Fabric/Forge/LWJGL in core/. Do not break Application.runSimulation()
  retrigger.
- Dependency policy: dependency-free / pure-analytical PREFERRED (the repo ships no numeric-solver dep,
  having dropped commons-math3 for cross-loader packaging; A04-7 measured re-adding an LP library is
  net-negative). A redistributable dependency is ACCEPTABLE only when MEASURED worth it, with the loader
  packaging cost (Forge 1.8.9/1.12.2 shade+relocate; Fabric include) noted. COPT is NEVER shipped.
- Perf envelope (CORRECTED to the measured FAST distribution, Stage B SB1/B01, 89 succeeding captures):
  min 99 ms, MEDIAN 302 ms, p90 895 ms, p95 1218 ms, p99 4163 ms, MAX 4882 ms; 13.5% exceed 800 ms. The
  "0.1 ms" is the internal warm-loop fast path only (engine floor ~100 ms from cold-JVM + worker spawn);
  "800 ms" is a p88, NOT a maximum. j001 (n=353) is ~2.0 s at FAST. THOROUGH is budget-bounded ~7.6-8.2 s.
  The hard TAIL is 24-47 s (accepted-fails razor-proof/nix-full-t1 + slow-successes loopmm-tight-t39),
  because FAST has no global deadline. Do not regress the 100-900 ms typical band; a replacement must beat
  shipped THOROUGH on the coupled/degenerate class (SB7) without regressing that band.
- Determinism: seeds fixed; results reproducible (re-verify per Stage B).
- Shipped path green on `./gradlew :core:test -PslowTests` at ALL times; prototypes in test/ or behind a
  flag.

NON-GOALS (out of scope for this rework):
- Changing the byte-exact physics model (it is ground truth).
- A certified GLOBAL solver on the shipped path via SMT/rational arithmetic (measured dead as a searcher;
  it verifies only).
- Shipping COPT or any commercial/Python dependency.
- Vertical (Y) motion (decoupled from yaw).

## 6. LENS-5 ARCHITECTURE VERDICT

The current stack is a tall pile of special-purpose stages (10 subsystems, 5 entry paths, 4 smoothing
passes, dF pinned twice, translation implemented three times, gate-pattern machinery duplicated across 5
solvers), with measured redundancy, dead code (LatticeRepair, stepRange), and consistency gaps (the
matrix in section 3). It is NOT near-optimal by Lens 5. The evidence supports a substantially smaller,
more analytical architecture, built on the reduction of section 4.2. Candidate collapsed architecture(s)
to TEST in Stage E (each measured against the COPT references and the perf envelope):

- ARCH-1 (the headline): "convex dual + low-dim residual". A single recovery primitive: (1) solve the
  convex dual/SOCP for the bound and the costates (a converging subgradient/interior-point, or reuse the
  existing dual made to converge); (2) determine all non-degenerate ticks in closed form; (3) identify the
  degenerate (vanishing-costate) ticks and solve the SMALL (1-4 dim) residual EXACTLY (enumeration / tiny
  spatial B&B / SDR rank-reduction / null-space projection), with smoothness as the residual tie-break;
  (4) byte-exact snap + certify. This one primitive would serve single- and multi-jump (F7), fold in
  free-start as p0 vars, express smoothing as the residual rule (C4), and give a real infeasibility
  certificate. PROTOTYPE the null-space/residual solve in COPT first, then port. THIS IS THE PRIMARY
  STAGE E TARGET.
- ARCH-2 (the pragmatic incumbent-plus): keep the fast closed-form path; on a degenerate miss, replace the
  full-n SLP/ILS fallback with the section-4.2 residual solve; make the dual converge (or replace the
  bound with a proper SOCP) so the bound is tight; unify free-start to center-pin+translate; collapse the
  four smoothing passes to one shared-reference residual tie-break; hybrid gate (banded fast path + small
  B&B/MIP only on cold miss, fixing the BnB-null false-negative F10). Lower risk, still large simplification.
- ARCH-3 (measured-negative guard): if the residual solve cannot be made robust pure-Java within the
  envelope, the HONEST fallback is ARCH-2's incumbent-plus with a good local search (ILS reaches within
  2.8e-5 b of the COPT optimum on j021; it is not far), documented as a measured cannot-fully-collapse for
  the coupled-multi-jump global-optimum sub-case.

The Lens-5 verdict is: COLLAPSE IS ATTAINABLE and warranted; the primary lever is section 4.2's
convex-dual-plus-low-dim-residual, which Stage 0 proves is globally solvable at these sizes. Whether it
ports to pure-Java within the envelope, and whether the residual solve is robust, are the Stage D (methods)
and Stage E (measured prototypes) questions. An honest measured cannot-collapse is reserved only for the
sub-case Stage E fails to close.

## 7. OPEN QUESTIONS routed to Stage D / E (each with the measurement that closes it)

- [Stage D methods, Stage E prototype] The residual solve: which method (enumeration over degenerate-tick
  directions / tiny spatial B&B / SDR rank-reduction a la Sturm-Zhang / a null-space min-slack projection /
  Riemannian on the product of circles) closes the 1-4 dim residual robustly and fast, pure-Java? Benchmark
  each against the COPT reference optima (stage0 FINDINGS) on j021/j008b/loopmm and the dF-chain captures.
- [Stage D/E] Making the convex bound converge: a proper interior-point SOCP vs a fixed subgradient vs the
  existing dual with a better step, measured to match COPT's <20 ms tight disk bound at n<=49 and to not
  blow up at n=353 (the AL-FISTA fails there). Weigh a redistributable SOCP dependency's loader cost.
- [Stage E] The gate as a MISOCP / big-M vs the banded fast path: does a single small MIP land loopmm and
  dsf-neo at the byte-exact objective AND certify infeasibility where BnB returns null (F10), within the
  envelope?
- [Stage D/E] dF as a phase/sector constraint in the residual/QCQP (so RelaxationRecovery and the residual
  solve stop bailing on facing walls, F8); COPT reference for the dF-constrained optimum (needs dF modeled
  in COPT, section-5 caveat).
- [Stage E] Smoothing as the residual tie-break vs the four-pass stack: A/B reversal sums on the hpk
  corpus; COPT/global reference for the reversal-minimal feasible path.
- [Stage B/E] stepRange-backed incremental rescoring (~2x on the polishers, A02-6); cross-window dual warm
  start (2.5-5x on long runs, A07-12); the single shared compiled model.
- [Stage E] Byte-exact round-trip: snap every COPT/prototype continuous optimum to the LUT, replay through
  ExactJumpModel, report residual and the half-angle gain (Stage 0 section 4).
