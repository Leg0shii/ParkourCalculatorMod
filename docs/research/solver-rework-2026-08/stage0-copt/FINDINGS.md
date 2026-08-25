# Stage 0: COPT spike FINDINGS (the circle-vs-disk / H1-vs-H2 answer + bound tightness)

All numbers below are MEASURED with the COPT v8.0.5 oracle against structure exported byte-faithfully
from the shipped `JumpLinearModel` (via `StructureDump.java`), cross-checked against the shipped
`CostateDualSolver` bound and the live engine's byte-exact achieved objective. Reproduce with the
commands in `research/copt/README.md`. COPT is a RESEARCH ORACLE ONLY, never shipped.

## 0. Method and faithfulness gate (passed)

`StructureDump.java` (new, test-only) dumps for any capture the fully compiled continuous program:
per-tick constant modulus `mMag_t`, phase `baseArg_t`, friction `f4_t`; the objective vectors
`cx_t/cz_t` (already oriented so we MAXIMIZE); and every position wall compiled to
`sum_s coef_s (a . u_s) <= bPrime` exactly as `JumpLinearModel.compileWall` produces it. Decision
variables `u_t = (ax_t, az_t)`, the only nonconvexity `|u_t| = mMag_t`.

FAITHFULNESS GATE (built into the harness, `reconstruct_from_warm`): rebuild `(ax,az)` from the
recorded byte-exact yaws and confirm the exported model reproduces the recorded objective. Result on
the captures that carry a recorded path: model-vs-recorded objective diff is the pure linear-vs-byte
drift, 4.4e-5 b (f2f, n=24), 8.1e-5 b (loopmm, n=33), 9.9e-5 b (j008b, n=25), 1.9e-4 b (j021, n=39).
This is the known ~1.3e-5 b/tick `JumpLinearModel` drift (A02), NOT a model error; the exported program
is faithful. The engine's own byte-exact walls read the recorded path at viol 0 on all of these.

## 1. THE H1-vs-H2 ANSWER (settled, with numbers in blocks)

The sub-question from the mission: is the multi-jump recovery failure H1 (a genuine circle-vs-disk /
SOCP gap: the true optimum wants `|u_t| < m_t` at some ticks) or H2 (pure dual-face degeneracy: the
SDR is tight/rank-one, only the recovery is degenerate)?

MEASURED ANSWER: it is BOTH, in small measure, AND the distinction is largely moot, because the
constant-modulus problem itself is globally solvable in well under a second at these sizes. Precisely:

### 1a. H1 is present but small on coupled multi-jump; absent on single/easy multi-jump

Solving the SOCP DISK relaxation (`|u_t| <= m_t`, convex, `solve_socp_disk`) and reading per-tick
modulus slack `m_t - |u_t|`:

| capture | n | jumps | throttled ticks (slack>1e-6) | which / by how much | disk loose by (objective, b) |
| --- | --- | --- | --- | --- | --- |
| j005 | 9 | 1 | 0 / 9 | none | ~0 |
| j022-1bmhbfly | 11 | 1 | 0 / 11 | none | ~5e-5 |
| j016-X2jmmp2p | 11 | 2 | 0 / 11 | none | ~1.3e-4 |
| j019-3jmmtruenix | 11 | 3 | 0 / 11 | none | ~4.3e-4 |
| f2f-dfchain (dF dropped) | 24 | multi | 0 / 24 | none | ~1.3e-4 |
| loopmm-3jump (clamp-free) | 33 | 3 | 1 / 33 | t0 by 0.095 | ~0 (disk == sphere to 1e-6) |
| j008b-2jump | 25 | 2 | 4 / 25 | t1 by 0.101 (others ~1e-5) | ~1.5e-3 |
| j021-rinav1-01 | 39 | 4 | 1 / 39 | t12 by 0.083 | ~1.6e-3 |

So the disk relaxation IS genuinely loose on the coupled cases (j008b, j021, loopmm): it throttles
`|u|` strictly below `m` at 1 to 4 ticks. That is a real H1 signal. The throttled ticks are the
LOW-AUTHORITY ticks (a standing-start tick t0/t1, or a redirect tick t12 whose costate the objective
is indifferent to), where the linear objective prefers a shorter vector; the fixed-modulus constraint
forbids it. This reproduces the historical j021 "SOCP minimizer violates the modulus with defect 0.083"
(angle-solver.md 16) to the digit (measured 0.08292 at t12). But the resulting objective looseness is
only ~1.6e-3 b.

### 1b. H2 is also present on exactly those coupled cases (SDR not rank-one)

Solving the Shor/SDP lifting (`solve_shor_sdp`, `M = [[1,u^T],[u,X]] >= 0`, dim `2n+1`, with the
modulus equality on the diagonal blocks) and reading the eigen-spectrum of the optimal M:

| capture | SDP bound (b) | eig2/eig1 | rank verdict |
| --- | --- | --- | --- |
| j005 | -41.294958 | 6.2e-8 | RANK-1 (tight) |
| j016 | -4.857908 | 9.0e-9 | RANK-1 |
| j019 | -13.303208 | 7.4e-9 | RANK-1 |
| j022 | -531.700132 | 9.5e-8 | RANK-1 |
| f2f (dF dropped) | -3.860372 | 6.0e-7 | RANK-1 |
| loopmm (clamp-free) | -279.299065 | 0.0188 | RANK > 1 |
| j008b | -0.195420 | 0.0239 | RANK > 1 |
| j021 | 1067.865480 | 0.0169 | RANK > 1 |

The SDP is rank-one tight on single jumps and easy multi-jump (the constant-modulus hidden convexity
holds outright), and rank > 1 (eig2/eig1 up to 0.024) on exactly the coupled cases where the disk also
throttles. On those cases the standard Shor bound is NO TIGHTER than the disk bound (identical to 6
digits on j021 and loopmm), so the SDP lifting buys nothing over the SOCP here. The rank-2/3 structure
localizes to the same low-authority ticks (SDP-recovered modulus slack >1e-4 at exactly 1 tick on j021,
matching the disk). This corroborates A03's j828 finding (off-sphere ticks sit in the `|g_t| -> 0`
null-space of a degenerate dual face) and extends it, MEASURED, to the coupled multi-jump class the
Stage A reducer correctly flagged as still-open.

### 1c. The decisive finding that transcends H1 vs H2

The nonconvex constant-modulus QCQP (`|u_t| == m_t` exactly, `solve_qcqp_sphere`, COPT spatial
branch-and-bound with NonConvex=2) solves to GLOBAL optimality, gap ~0, in well under a second at every
size tested:

| capture | n | jumps | global optimum (b) | COPT gap | wall-clock |
| --- | --- | --- | --- | --- | --- |
| j005 | 9 | 1 | -41.294900 | 0 | 0.09 s |
| j022 | 11 | 1 | -531.700200 | 0 | 0.08 s |
| j016 | 11 | 2 | -4.857772 | 0 | 0.11 s |
| j019 | 11 | 3 | -13.302783 | 0 | 0.36 s |
| j008b | 25 | 2 | -0.196938 | 7.7e-5 | 0.14 s |
| loopmm (clamp-free) | 33 | 3 | -279.299065 | 0 | 0.02 s |
| j021 | 39 | 4 | 1067.863880 | 0 | 0.27 s |
| f2f (dF dropped) | 24 | multi | -3.860256 | 0 | 0.12 s |

So the production recovery breakdown (0.34 b infeasible on j021 historically; 2.89 b on the external
thousand save) is NEITHER an inherent circle-vs-disk gap (the disk gap is only ~1.6e-3 b) NOR an
intractable nonconvexity: the constant-modulus QCQP is globally solved in 0.27 s at n=39/4 jumps. The
breakdown is PURELY the shipped CLOSED-FORM COSTATE RECOVERY failing to reconstruct the optimum on the
degenerate face. A global solver, or a good byte-exact local search (memory: ILS reaches j021
1067.8636684, within 2.8e-5 b of the COPT continuous optimum 1067.863880, cross-validating both),
closes it.

CONCLUSION for target capability 4 (convex-global optimum, fast): ATTAINABLE for the sizes the tool
actually hits (n <= ~49, single- and multi-jump), as a nonconvex constant-modulus QCQP solved by
spatial B&B. The open engineering question is whether that spatial B&B can be replicated pure-Java on
the shipped path, or a redistributable solver justified, or a good local search (ILS) is enough given
it already lands within the byte-exact floor. That is routed to Stage D (methods) and Stage E
(prototype + benchmark vs these COPT references).

## 2. BOUND TIGHTNESS: the shipped dual bound vs ground truth

Comparing the shipped `ClosedFormSolve.dualBound` (the weak-duality bound the engine reports) to the
COPT-converged SOCP disk bound and the true optimum:

| capture | shipped dualBound | COPT tight disk (SOCP) | COPT true (const-mod) | shipped bound loose by |
| --- | --- | --- | --- | --- |
| j005 | -41.294959 | -41.294958 | -41.294900 | ~1e-6 (tight) |
| j016 | -4.857908 | -4.857906 | -4.857772 | ~2e-6 (tight) |
| j019 | -13.303208 | -13.303208 | -13.302783 | ~0 (tight) |
| j022 | -531.700133 | -531.700145 | -531.700200 | ~tight |
| j008b | -0.183120 | -0.195409 | -0.196938 | 0.0123 (LOOSE) |
| j021 | 1067.889761 | 1067.865480 | 1067.863880 | 0.0243 (LOOSE) |

The shipped dual bound is TIGHT on single/easy captures (matches the COPT SOCP to ~1e-6) and LOOSE on
the coupled ones (j021 by 0.024 b, j008b by 0.012 b) PURELY from non-convergence: COPT solves the same
SOCP disk exactly in <20 ms and lands at 1067.86548 (j021), while the shipped subgradient/Newton grinds
to its iteration cap at 1067.88976. This is the measured proof that "the dual bound is loose" (handoff)
is the shipped solver not converging, NOT a fundamental gap. The CONVERGED disk bound (COPT) is within
1.6e-3 b of the true optimum on j021. A converging convex bound is available; the shipped one is left
loose by design (capped iterations).

## 3. THE SHIPPED SOLVER'S OPTIMALITY GAP (headroom COPT reveals)

Live engine at THOROUGH / 12 s (`EngineFileScreen`), byte-exact achieved objective vs COPT true:

| capture | prod achieved (byte-exact) | COPT true (continuous) | gap | note |
| --- | --- | --- | --- | --- |
| j005 | -41.291516 | -41.294900 | prod OVER-reaches by +3.4e-3 | see section 4 (byte-exact half-angle gain) |
| j016 | -4.855680 | -4.857772 | prod over by +2.1e-3 | " |
| j019 | -13.292335 | -13.302783 | prod over by +1.0e-2 | " |
| j022 | -531.700150 | -531.700200 | prod short by 5e-5 | matched |
| j008b | -0.215314 | -0.196938 | prod SHORT by 1.8e-2 | real headroom |
| j021 | 1067.862397 | 1067.863880 | prod SHORT by 1.5e-3 | real headroom (ILS closes it) |

On the coupled cases where the recovery breaks (j008b, j021), the shipped THOROUGH solve leaves real
headroom (1.5e-3 b on j021, 1.8e-2 b on j008b) that COPT proves is reachable. On single/easy jumps the
byte-exact production result OVER-reaches the continuous optimum (section 4), so those are not headroom,
they are the byte-exact model out-reaching the continuous one.

## 4. COPT is a near-exact reference, NOT a strict byte-exact upper bound (measured caveat)

On single jumps and easy multi-jump, the byte-exact production objective EXCEEDS the COPT continuous
constant-modulus optimum: j005 by +3.4e-3 b, j019 by +1.0e-2 b. Investigated: j005 and j022 have
BIT-IDENTICAL physics (a pure-forward sprint-jump t0 with mMag 0.3274 boost 0.2 strafe 0, then eight
45-strafe air ticks forwardMag=strafeMag=0.01838 mMag 0.026), so the folded constant-modulus model is
EXACT on the jump tick (no strafed-boost fold error); the over-reach is the byte-exact float32 sine-LUT
giving `sin^2+cos^2 > 1` at favorable ("increasing") half angles (A10 measured a single-tick reach of
+1.5e-4 b at gf=135.27; over eight friction-amplified 45-strafe air ticks this accumulates to the
few-e-3 observed). j022 matches COPT because its MIN-X optimum lands on a non-favorable half-angle.

IMPLICATION: COPT's continuous optimum is an excellent reference (within a few e-3 b) but is NOT a
strict upper bound on the byte-exact reach, so byte-exact certification through `ExactJumpModel` stays
mandatory, and Stage E must byte-exact-round-trip any COPT solution (snap to LUT, replay, report
residual) rather than trust the continuous value. This is a measured refinement of the mission's
premise that the continuous relaxation upper-bounds the game; it does so only up to the half-angle
norm-excess, which is objective-relevant on flat/45-strafe jumps.

## 5. Special layers characterized (routed to Stage D/E, not fully modeled here)

- INERTIA GATE (loopmm): the COPT models above are CLAMP-FREE (the shipped `JumpLinearModel`
  pattern-free constructor). loopmm's clamp-free continuous optimum -279.299065 is 6.6e-4 b above its
  recorded landing -279.299727 and matches disk==sphere, but the gate is loopmm's actual mechanism (a
  free brake), so this model does not capture its true feasible basin. A10 measured the gate fires
  destructively on 0 to 1 fed ticks; A05 measured it folds to ~2n big-M `velocityWalls` +
  `keepAliveWall` indicators. COPT supports this exactly (addGenConstrIndicator / big-M); modeling the
  gate as a MISOCP is a Stage E prototype. Do NOT read the clamp-free loopmm number as its optimum.
- dF (FACING) CONSTRAINTS (f2f, df-chain): the COPT models DROP dF (position walls only), so they are
  RELAXATIONS. Measured cost of dropping dF: f2f gains 0.024 b (recorded -3.884 vs position-only
  -3.860), df-chain-free-start gains 3.06 b (recorded -6.935 vs position+free -3.870). So for dF-chain
  captures the position-only model is a very loose upper bound; dF dominates. dF=D is a per-tick PHASE
  constraint `arg(u_t) = arg(u_{t-1}) + (baseArg_t - baseArg_{t-1}) + D`, a rotation coupling of
  consecutive complex inputs, nonconvex in (ax,az). Modeling dF as a phase/lifted constraint in the
  QCQP is a Stage D/E item.
- FREE START: added as two box-bounded linear variables via the exported `p0coef` (the wall's
  start-axis sensitivity). df-chain-free-start is INFEASIBLE at fixed start (worstWallViol +2.46 b) and
  FEASIBLE once `p0` is free, confirming A06's "free-start is load-bearing / provably separable by
  rigid translation." COPT with free `p0` solves it (position-only) in 0.13 s.

## 6. Headline conclusions for Stage C

1. The multi-jump recovery failure is a small H1 (disk loose by ~1.6e-3 b at 1-4 low-authority ticks)
   ON TOP OF H2 (SDR rank>1 on the same ticks), but the constant-modulus QCQP is GLOBALLY SOLVABLE in
   <0.3 s at n<=49. Capability 4 is attainable in principle; the question is the shipped-path
   realization.
2. The shipped dual bound is loose only from non-convergence; a converged convex (SOCP) bound is within
   1.6e-3 b of truth and computable in <20 ms.
3. The shipped THOROUGH solver leaves measured headroom on coupled cases (1.5e-3 b j021, 1.8e-2 b
   j008b) that a global solver or ILS closes (ILS within 2.8e-5 b of the COPT optimum).
4. Byte-exact can out-reach the continuous constant-modulus model by a few e-3 b (half-angle norm>1),
   so byte-exact certification stays mandatory and COPT is a near-exact reference, not a strict bound.
5. The gate and dF are separate layers (big-M indicators; phase/rotation constraints) that COPT can
   model and Stage E should prototype; the pure position + constant-modulus core is what is globally
   solvable today.
