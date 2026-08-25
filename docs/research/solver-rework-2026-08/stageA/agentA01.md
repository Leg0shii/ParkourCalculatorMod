# Stage A shard: agent A01 (Lagrangian dual and its recovery)

Agent: A01
Territory: `CostateDualSolver` (the dual of the continuous relaxation, closed-form costate recovery, the
convergence machinery, and every call site that constructs or drives it).

Files inspected:
- `core/.../anglesolver/solver/CostateDualSolver.java` (whole)
- `core/.../anglesolver/solver/ClosedFormSolve.java` (solveClosed ladder 360-432, dualBound 443-471)
- `core/.../anglesolver/solver/RelaxationRecovery.java` (whole)
- `core/.../anglesolver/solver/SlpSolve.java` (seed 120-165)
- `core/.../anglesolver/solver/BoundPrunedRecovery.java` (nodes 795-855, recover 1177-1195, bounds 427/457)
- `core/.../anglesolver/solver/FreeStartSolve.java` (jointLadder 505-585, buildFreeP0 712-717)
- `core/.../anglesolver/solver/FacingPrefold.java` (expand 320-343)
- `core/.../anglesolver/solver/JumpLinearModel.java` (recoverYawDeg 353-359)
- Handoffs re-verified against current code: `smooth-and-convergence-handoff-2026-08-24.md`,
  `dual-newton-hessian-handoff.md`, `dual-newton-iteration-audit.md`, `angle-solver.md`.

Commands run (all direct `java -cp`, JDK 25, no gradle; classpath = core main+test classes/resources +
gson-2.8.0 + imgui-java-binding-1.86.12 + junit-4.13.2 + hamcrest-1.3):
- `JUnitCore ...ContinuousDiscreteScreen` with `PKC_DIAG_FILE=<capture>` over
  {f2f-dfchain-multijump, df not run, loopmm-3jump-lands, loopmm-3jump-solver-misses, j019-3jmmtruenix,
  j016-X2jmmp2p, j008b-2jump, gh398-optimize-2jump, j001, j008-bfneo, j020-panewallsingleblockbf,
  j022-1bmhbfly, razor-proof}. This screen does a single cold `CostateDualSolver.solve(0.0,null)`, recovers
  `u*=m g/|g|`, reports dual iters/pgres, the continuous recovery violation, AND the byte-exact violation
  (replay of the recovered yaws through `ExactJumpModel`), so every number below is byte-exact verified.

---

## A01-1: What the dual computes and its exact role

LOCATION: `CostateDualSolver.costate` 390-409; `grad` 411-431; `JumpLinearModel.recoverYawDeg` 357-359.

CLAIM: It minimizes `D(lambda) = sum_t m_t*sqrt(|g_t|^2+EPS2) + sum_j lambda_j*b'_j` over `lambda>=0`
(free for equality walls), where `g_t = c_t - sum_j lambda_j A_{j,t}` is the friction-propagated costate,
and recovers each tick's input as `u*_t = m_t*g_t/|g_t|`, from which the yaw is `atan2(gz,gx)-baseArg[t]`.
It dualizes ONLY the linear position walls and solves the constant-modulus inner max `max_{|u|=m} g.u = m|g|`
EXACTLY, so the modulus nonconvexity is absorbed with no gap WHEN the dual is tight.

EVIDENCE: Measured tight-and-exact on convergent instances. `j016-X2jmmp2p`: dual iters=13, pgres=1.02e-10,
continuous recovery worstViol=1.02e-10, byte-exact worstViol=1.06e-4 (feasible), X=-4.857908 = dual bound.
`j019-3jmmtruenix`: iters=18, pgres=1.52e-9, recovery worstViol=4.04e-10, byte-exact 2.13e-5. `j022`:
iters=22, pgres=6.13e-5, recovery 4.76e-7. Both `j016` and `j019` are MULTI-jump, so the mechanism is not
single-jump-only.

IMPACT: correctness/speed - this IS the fast global-optimum path for the convex-tight class; microseconds.

PROPOSAL: none (baseline). It is the general primitive the other stages should defer to.

CONFIDENCE: 0.98

DEPENDS-ON: none.

---

## A01-2: The pgres plateau reproduced on CORPUS captures, not just the local thousand save

LOCATION: `CostateDualSolver.solve` loop 209-252; convergence tests 216 (`pgres<=GRAD_TOL`),
231 (`du<=U_TOL`), 221 (DIVERGE stall).

CLAIM: The "grinds all MAX_ITER=100 at a mid pgres and recovers garbage" pathology the handoff measured on
the non-corpus `thousand/1` save reproduces on committed corpus captures; the thousand pgres~2.4 number is a
re-verify-only external save, but the phenomenon is corpus-resident and measured here.

EVIDENCE (ContinuousDiscreteScreen, byte-exact verified):
- `j008b-2jump`: dual iters=100 (hit cap), pgres=1.661e-2, continuous recovery worstViol=4.18e-1 b,
  byte-exact worstViol=4.17e-1 b. DIVERGE_PGRES=4.0 does not fire (0.0166 << 4.0).
- `razor-proof`: iters=100 (cap), pgres=6.421, recovery worstViol=5.56 b, byte-exact 5.56 b. Note pgres 6.42
  > DIVERGE_PGRES 4.0 yet it still grinds the full 100 (see A01-4).
- `loopmm-3jump-lands` (== `loopmm-3jump-solver-misses` numerically): iters=21, pgres=7.12e-3, recovery
  worstViol=2.45e-1 b, byte-exact 2.76e-1 b. Stops at 21 via the `du<=U_TOL` costate-space exit, NOT the cap.
- `j001` as a single whole-run window (353-tick 30-jump): iters=22, pgres=44.47, recovery worstViol=44.5 b.
  Confirms a single window over many coupled jumps is meaningless; receding-horizon windowing is mandatory.
- Contrast convergent (A01-1): j016/j019/j020(pgres 5.17e-10, iters=60)/j022 all recover feasible.

IMPACT: speed (wasted ~80-100 iters/solve on the degenerate cases) + correctness (0.25-5.6 b infeasible
recovery hands the solve to slow SLP/B&B/ILS fallback).

PROPOSAL: Do not "make the dual converge" (A01-3). Add a cheap detector: the recovery is degenerate iff the
recovered slack `grad` cannot be zeroed on the dual-optimal face; short-circuit to the face-resolving
recovery or to search the moment `du` stalls with `pgres` still above a per-instance floor.

CONFIDENCE: 0.95

DEPENDS-ON: A01-3.

---

## A01-3: It is a duality gap / degenerate recovery, NOT a convergence bug (re-verified on corpus)

LOCATION: flat-face rationale in javadoc 17-24 ("curvature `1-ghat.ghat` vanishes"); `buildHessian` 433-472
(the `(sameAxis?1:0) - hatI*hatJ` curvature term); `grad` slack 419-425.

CLAIM: On the coupled multi-jump cases the BOUND is near-tight but the RECOVERY `u*=m g/|g|` is uniquely
pinned by the costate directions and lands infeasible; raising iterations tightens the bound but cannot fix
the recovery, because at the degenerate dual optimum many `lambda>=0` give the same `D` yet different,
uniformly infeasible `u*`. The constant-modulus hidden convexity that makes single-jump recovery exact
breaks on opposing-pair corridors.

EVIDENCE: On `j008b` the byte-exact realization (4.17e-1 b) is NO better than the continuous recovery
(4.18e-1 b): the gap is in the continuous fixed-modulus recovery itself, not in quantization/inertia
(the drop continuous->byte-exact X is only +0.019 b). Same shape on razor-proof (5.56 vs 5.56) and loopmm
(0.245 vs 0.276). This is the corpus analogue of the handoff's thousand/1 result (100k-iter reference
subgradient drove D lower yet min recovery violation stayed 2.89 b): re-verified qualitatively on captures
that ship. The `du<=U_TOL` early-exit at loopmm iter 21 is the code CONFIRMING it: `u*` has stopped moving
(the real optimum) while `pgres` is still 7e-3 (lambda still wandering the null space).

IMPACT: correctness - "raise MAX_ITER" and "add a better dual step" are both wrong levers for this class;
the deliverable is a recovery that selects the right face point or a search hand-off.

PROPOSAL: Route the H1-vs-H2 discriminator (SOCP disk slack vs SDP rank) to Stage 0 / COPT on j008b,
loopmm, razor-proof (all corpus, cheaper than the external thousand save). If H2 (rank-one, face
degeneracy): add an inner min-violation projection over the dual-optimal null space. If H1 (true circle-
vs-disk gap): the fixed-modulus dual cannot serve this class and search stays mandatory; document it.

CONFIDENCE: 0.9

DEPENDS-ON: A01-2.

---

## A01-4: DIVERGE_PGRES=4.0 stall bail does not fire on the degenerate cases it targets

LOCATION: constants 51-53; bail 218-221 (`pgBest > DIVERGE_PGRES && stall >= DIVERGE_STALL`).

CLAIM: The stall bail, whose stated purpose (comment 44-50) is to stop the long-multi-jump grind early, is
tuned so high (4.0) and gated on best-so-far non-improvement that none of the measured degenerate corpus
cases trip it: they either grind the full 100 or exit via `U_TOL`.

EVIDENCE: `lastStalled` inferred false from iters: j008b iters=100 at pgres 0.0166 (0.0166<4.0, cannot
trip); loopmm iters=21 exits via U_TOL not stall; razor-proof iters=100 at pgres 6.42 (> 4.0, yet ran the
full cap, so `pgBest` must have dipped under 4.0 or kept beating the 5% relative-improve test - mechanism is
an UNMEASURED-HYPOTHESIS, but the "ran 100" outcome is measured). Handoff records thousand/1 also grinds 100
at pgres 2.4 (2.4<4.0). So across every degenerate case seen, the bail is inert. It is only known
load-bearing for j020 (per angle-solver.md, its removal regresses j020) which here converges (pgres 5e-10).

IMPACT: speed - the intended early-out saves nothing on the actual multi-jump plateaus; they pay the full
cap or rely on the unrelated U_TOL exit.

PROPOSAL: Replace the absolute-pgres bail with a recovery-feasibility bail: track the recovered `grad`
worst-slack and stop as soon as it stalls (it is already computed every `grad`). Fold into A01-2's detector.
Keep whatever guard j020 needs, but drive it off recovery slack, not raw pgres.

CONFIDENCE: 0.85

DEPENDS-ON: A01-2.

---

## A01-5: Every stage cold-constructs its own solver; warm-start reuse is intra-ladder only

LOCATION: construction sites - ClosedFormSolve 379 (+ dualBound 456), SlpSolve 152, RelaxationRecovery 46,
BoundPrunedRecovery 427, 457, 805 (per B&B node), FreeStartSolve 518. Warm param: `solve(margin, warm)` 187.

CLAIM: There are ~7 distinct `new CostateDualSolver(...)` sites, each re-copying the wall arrays and
reallocating all per-solve scratch; warm-start (`warm=prev.lambda`) is reused only WITHIN a single object's
margin ladder. There is no cross-stage, cross-window, or cross-node solver cache; B&B allocates a fresh
solver PER node (805) even though it threads `parent.lambda` as the warm value.

EVIDENCE: grep of all construction/`solve` sites (listed). Cold entries with `null` warm: ClosedFormSolve
456, SlpSolve 153, BoundPrunedRecovery 427/457, FreeStartSolve 536, plus each top-level ladder's first rung.
Re-verify (from `dual-newton-iteration-audit.md`, dev 64dada4, PRE the current stall bail so treat as
stale-direction-only): a 104-capture sweep issued 9,551 solves / 598k iterations, 53% capped at MAX_ITER;
capped solves owned 13.3 s of 15.7 s in-solve. The receding-horizon multi-jump path pays a cold solve per
window (angle-solver.md 178: no lambda-by-wall-name seed across windows; est. 2.5-5x speedup if added).

IMPACT: speed (repeated cold ladders + per-node allocation) + simplicity (7 near-identical setup blocks).

PROPOSAL: One reusable solver keyed by (n, wall-structure) with a lambda-by-wall-name warm cache that
survives across ladder rungs, B&B siblings, and receding-horizon windows. Measure against the audit's
capped-solve share.

CONFIDENCE: 0.8

DEPENDS-ON: none.

---

## A01-6: The recovery formula and its vanishing-costate fallback are duplicated with inconsistent epsilons

LOCATION: canonical in `CostateDualSolver.grad` 413-418 (uses EPS2=1e-14 smoothing). Re-implemented at
`FacingPrefold.expand` 328-341 (guard 1e-18), `SlpSolve` 156-164 (1e-18), `BoundPrunedRecovery.recover`
1180-1194 (1e-18) and 427-434 (1e-18), `RelaxationRecovery.seedAtMargin` 91-98 (1e-12),
`ditherSeedYaws` 307-334 (1e-12), `projectionSeedYaws` 350-361 (1e-16).

CLAIM: The same `u*=m g/|g|` recovery plus the same "costate vanished -> point along the objective axis"
default is copy-pasted into 5+ production sites, each with a DIFFERENT zero-costate threshold
(1e-18 x4, 1e-16 x1, 1e-12 x2), none matching the solver's own EPS2=1e-14.

EVIDENCE: grep of the guard literal across `solver/*.java` (6 hits listed above plus the two
RelaxationRecovery variants). The solver never emits an exactly-zero costate (EPS2 additive), so the callers'
guards fire only on near-degenerate ticks, where the threshold choice changes which ticks get the axis
default - an undocumented behavioral fork.

IMPACT: simplicity + correctness (small): one recovery, one threshold, one fallback would remove ~5 forks and
the threshold drift.

PROPOSAL: Expose a single `recoverYaws(Result, Objective)` on `CostateDualSolver` (or `JumpLinearModel`)
that owns the fallback and the `FacingPrefold` pin-expand, and delete the per-caller copies.

CONFIDENCE: 0.9

DEPENDS-ON: none.

---

## A01-7: A single recovery already serves single AND multi-jump; the obstruction is face selection

LOCATION: whole `solve`/`grad`/`recoverYawDeg` path; degeneracy at `buildHessian` flat subspace 460-464.

CLAIM: Collapsing to one recovery mechanism for both single- and multi-jump is already 90% done: the exact
same `u*=m g/|g|` recovers j001-class single jumps AND convergent multi-jumps (j016, j019). The only
obstruction to it serving the rest is choosing the correct point on the DEGENERATE dual-optimal face; that is
orthogonal to the single-vs-multi distinction.

EVIDENCE: A01-1 measured multi-jump j016/j019 recovering feasible from the identical formula; A01-3 measured
the failure set (j008b/loopmm/razor) is degenerate-face, not "multi-jump". So the axis of collapse is
convex-tight vs degenerate-face, not single vs multi.

IMPACT: simplicity - argues for ONE `dual + face-resolving recovery` primitive rather than the current
scatter of stage-specific recover+recheck+fallback blocks.

PROPOSAL: Define the primitive as: minimize D; on the returned optimum, if recovered worst-slack > tol, solve
a small min-slack projection over `null(A_active^T)` (the flat subspace the Hessian already identifies) before
declaring a miss. Prototype in Stage 0/COPT (LP over the null space) before committing. If COPT shows H1
(true gap), the obstruction is fundamental and the primitive must expose "gap, hand to search".

CONFIDENCE: 0.72

DEPENDS-ON: A01-3.

---

## A01-8: Consistency matrix for the dual stage

LOCATION: as cited per row.

CLAIM: The dual implements free-start and warm-start-in-ladder, but is caching-poor, entirely
smoothing-blind, and cannot represent dF facing constraints (they are stripped upstream).

EVIDENCE (present/absent, file:line):
- Caching: ABSENT across calls (A01-5). Intra-ladder warm-start PRESENT (`solve(margin,warm)` 187,
  `commit`/`commitPath` reuse). No memoization; B&B reallocates per node (805).
- Smoothing-awareness: ABSENT. No `smoothLambda`/turn-cost anywhere in the file; objective is the raw linear
  `cx,cz` (costate 391-393). `FreeP0.smooth` (165) is a start-support Tikhonov term, NOT path smoothness. All
  path smoothing is downstream (DeWiggle/SmoothingPolish/SmoothFaceRecovery), so the dual optimum is
  smoothness-blind and can hand a jittery vertex to the smoother.
- Sensible defaults: PARTIAL. MAX_ITER=100 (40), DIVERGE_PGRES=4.0 (51, near-inert, A01-4),
  P0_SMOOTH_DEFAULT=0.05 (55), RHO0=1e-2 (56), LAMBDA_CAP=1e9 (41). EPS2=1e-14 (39) does not match the
  callers' recovery guards (A01-6).
- dF (facing): ABSENT / cannot represent. Walls are position-linear only; facing walls are excluded before
  the dual (`ClosedFormSolve.dualBound` returns NaN if `hasFacingWall` 447; SlpSolve 123 / RelaxationRecovery
  32-34 skip `Mode.F`). MEASURED: f2f-dfchain-multijump converges (pgres 1.12e-9, recovery slack 3.5e-10) yet
  byte-exact worstViol = 135 (deg) because the dF chain the dual never saw is violated. dF is a
  FacingPrefold-pin / SLP concern, not the dual's.
- Free-start: PRESENT. `FreeP0` (158-177), gated `freeP0!=null`; lifts p0 as bounded deviation vars via a
  smoothed support (`supportOf`/`deltaOf`/`hAxis`/`supportCurv` 365-388), recovered as `dvx/dvz`
  (`recoveredDelta` 257-259, Result 145-146). Byte-identical when null; used only by FreeStartSolve.jointLadder
  (518).

IMPACT: simplicity/correctness - names the exact gaps the spec's consistency question must answer.

PROPOSAL: For the collapse, decide whether smoothing and dF become terms/constraints the dual itself carries
(dF is a per-tick sector on `arg(u_t)` = a nonconvex angular bound; smoothing is a coupling across ticks,
also not linear-in-u) or stay strictly downstream. Both currently sit outside the dual by necessity, so the
"smoothing as just another constraint" north star does NOT reach this stage without a reformulation.

CONFIDENCE: 0.9

DEPENDS-ON: none.

---

## A01-9: Free-start recovery has no downstream safety net, unlike the pinned fast path

LOCATION: javadoc 28-35; `FreeStartSolve.jointLadder` bails on `solver.lastStalled` (537, 545, 553, 574).

CLAIM: The javadoc's "recovered angles need only modest dual accuracy" safety argument is scoped to the
pinned closed-form path (miss falls through to SLP/recovery). In the free-start role there is no such net:
`FreeP0.smooth` biases the recovered shape and start, and jointLadder treats `lastStalled` as a hard skip, so
a near-miss on a degenerate free-start window is a lost solve rather than a slow one.

EVIDENCE: code path - `jointLadder` continues/returns null on `solver.lastStalled` at 537/545/553/574; no
SLP hand-off inside the joint ladder (unlike ClosedFormSolve 399-431 which keeps bestYaws). The `smooth`
retry ladder (`jointP0Ladder`, FreeStartSolve 130) is the only recourse, bounded below by the `1/smooth`
support curvature (buildHessian 465-467). Re-verify HYPOTHESIS: I did not exercise a genuine multi-wall free
box through jointLadder headlessly this session; the free-start handoff (free-start-handoff.md 163) flags
that `:core:test` only covers `freeP0==null` and single-active-constraint boxes, so the degenerate free box
is UNMEASURED here. Experiment: run FreeStartSolve.solveJoint on f2f-dfchain-free-start with several real
walls and log `lastStalled`/recovered dvx,dvz vs the byte-exact miss.

IMPACT: robustness - the degeneracy of A01-3 is unrecoverable specifically in the free-start stage.

PROPOSAL: Give jointLadder the same keep-best-and-fall-through net the pinned path has, or make the
face-resolving recovery (A01-7) the shared primitive so free-start inherits it.

CONFIDENCE: 0.6

DEPENDS-ON: A01-3, A01-7.
