# RESULTS: Stage E measured prototypes and benchmarks

Stage E canonical output. Every number measured this campaign: the ARCH-1 pipeline prototyped end-to-end
(convex step by the COPT oracle standing in for the future pure-Java interior-point SOCP of D12; residual
solve by `research/copt/residual_branch.py`; byte-exact certification by `ReplayYaws.java` through the real
`ExactJumpModel`), benchmarked against the shipped solver and the COPT global reference. COPT is a research
oracle, never shipped. All continuous optima are byte-exact-round-tripped before any achievability claim
(the continuous model is a near-exact reference, not a strict bound; FINDINGS section 4).

Prototype code (in tree, test-only or research harness, shipped path stays GREEN on
`:core:test -PslowTests`, verified 703 tests):
- `core/src/test/.../anglesolver/StructureDump.java`: exports the compiled linear program per capture.
- `core/src/test/.../anglesolver/ReplayYaws.java`: byte-exact replay of a yaw sequence through ExactJumpModel.
- `research/copt/`: `coptlib.py` (SOCP disk / nonconvex QCQP / Shor SDP), `run_h1h2.py`, `residual_branch.py`
  (ARCH-1 branch-with-convex-reopt residual solve), `residual_poc.py`, `export_yaws.py`.

## 1. The end-to-end ARCH-1 benchmark (measured)

For each capture: shipped THOROUGH byte-exact objective (EngineFileScreen, 12 s); the COPT global
constant-modulus optimum (the reference); the byte-exact realization of that continuous optimum through
ExactJumpModel (what ARCH-1 delivers if it snaps the continuous optimum directly); and the verdict.

| capture | class | shipped THOROUGH | COPT cont optimum | byte-exact of cont | viol | ARCH-1 vs shipped | verdict |
| --- | --- | --- | --- | --- | --- | --- | --- |
| j021-rinav1-01 | coupled 4-jump | 1067.862397 | 1067.863733 | 1067.863789 | 9.8e-5 | +1.4e-3 b | PROMOTE (clean win) |
| j022-1bmhbfly | single | -531.700150 | -531.700200 | -531.700190 | 4.8e-5 | +4e-5 b | tie (both optimal) |
| j008b-2jump | coupled 2-jump | -0.215314 | -0.197052 | -0.219554 | 7.4e-5 | worse if snapped | ITERATE (objective-aware snap) |
| loopmm-3jump | gate 3-jump | -279.2997 (rec) | -279.299065 (clamp-free) | -279.324484 | 1.4e-3 | worse | ITERATE (needs gate in model) |
| j005 | single half-angle | -41.291516 | -41.294959 | -41.298231 | 4.3e-6 | shipped wins | shipped byte-search wins |
| j016-X2jmmp2p | easy 2-jump half-angle | -4.855680 | -4.857772 | -4.857783 | 9.3e-5 | shipped wins | shipped byte-search wins |
| j019-3jmmtruenix | 3-jump half-angle | -13.292335 | -13.302783 | -13.303185 | 2.5e-5 | shipped over-reaches | shipped byte-search wins |

(All MAX objectives higher-is-better except j022/j008b MIN X lower-is-better. viol is the compiled
maxViolation at sine-floor scale, closed by the margin ladder except loopmm; see the loopmm root cause.)

## 2. What ARCH-1 wins, and its measured root causes where it does not

### 2.1 PROMOTE: coupled multi-jump with the recovery breakdown (j021)
The shipped THOROUGH solve is STUCK 1.5e-3 b below optimum (its closed-form recovery defaults the single
degenerate tick t12, then the full-n SLP/ILS fallback thrashes; B02 measured j021 100% dual-capped, ILS
sub-floor). ARCH-1's residual solve (branch on the 1 degenerate tick + convex re-optimize the rest)
reaches the COPT global optimum (residual_branch: gap 2.7e-5, poc-residual-validation.md), whose byte-exact
realization is 1067.863789, RECOVERING +1.4e-3 b that the shipped solver leaves on the table. This is the
headline value: on exactly the known-hard multi-jump recovery-breakdown class, ARCH-1 delivers the global
optimum where the incumbent misses.

### 2.2 ITERATE: objective-aware snap needed (j008b)
Shipped is STUCK at -0.215 (B06: 7.6 s THOROUGH gains only 1.2e-5, wrong basin). COPT proves the optimum is
-0.197 (1.8e-2 b better). BUT the byte-exact replay of COPT's continuous optimum gives -0.219554 (WORSE),
because the single degenerate tick t1's direction is ARBITRARY on the degenerate face and the specific
continuous direction byte-exact-realizes poorly. MEASURED ROOT CAUSE: ARCH-1's residual solve must optimize
the BYTE-EXACT objective at the degenerate tick (objective-aware sphere-decode snap, D07/D11), not snap the
arbitrary continuous direction. This is a prototype gap, not a dead end: the mechanism (search t1's angle
byte-exact) is specified and small; a Stage E follow-up prototype should land -0.197 byte-exact.

### 2.3 ITERATE: gate captures need the gate in the model (loopmm)
The clamp-free continuous optimum (-279.299065) byte-exact-replays to -279.324484 (viol 1.4e-3, above the
sine floor), because ExactJumpModel HAS the inertia gate that the clamp-free continuous model omits, and
the clamp-free path triggers the gate differently. MEASURED ROOT CAUSE: loopmm's mechanism IS the gate (a
free brake); ARCH-1 must model the gate as ~2n big-M indicators (F5, D11) and branch on the 0-1
gate-critical ticks. The clamp-free ARCH-1 does not transfer to gate captures. Stage E follow-up: the
gate-MIP prototype (D11), which COPT supports via addGenConstrIndicator.

### 2.4 shipped byte-exact search already wins (j005, j016, j019: half-angle single/easy jumps)
On flat / 45-strafe single and easy jumps, the byte-exact OPTIMUM exploits favorable half-angles (LUT
norm>1) that the continuous constant-modulus model lacks, so the byte-exact optimum is at DIFFERENT yaws
ABOVE the continuous optimum (j019: shipped -13.292335 vs cont -13.302783, shipped +1.0e-2 higher). The
shipped fast path + BucketAscent/ILS byte-exact search finds these; snapping the continuous optimum LOSES
(j005: byte-of-cont -41.298 < shipped -41.292). MEASURED CONCLUSION: ARCH-1's continuous optimum is NOT the
right target on half-angle single jumps; the shipped machinery is already correct there. ARCH-1's value is
concentrated on the coupled-multi-jump recovery-breakdown class, not single jumps.

## 3. The measured refinements (do not skip)

1. NAIVE fix-others-rigid is INFEASIBLE (residual_poc.py returned INFEASIBLE on j021/j008b). The disk's
   non-degenerate directions are conditioned on the throttled tick being short; the residual solve MUST
   re-optimize the rest convexly per branch node. Confirmed.
2. The degenerate-tick COUNT is 0-1 on redirect/neo jumps but 10-22 on momentum/nix jumps (j1150 22, j828
   13, j716 10). YET COPT solves all globally in < 0.5 s (the momentum degenerate ticks are a coordinated
   low-DOF phase, not a free high-D torus). So the residual solver must be smart (spatial B&B / Riemannian),
   not a brute k-D grid, but the class stays tractable (SPEC 4.2 amended, poc-residual-validation.md).
3. COPT's nonconvex QCQP carries ~1e-5 to 4e-4 equality-tolerance slop; tighten FeasTol and always
   byte-exact-round-trip. Done for the final references (FeasTol 1e-9 in the residual re-solve).

## 4. Recommended architecture (measured-evidence-backed)

The Stage E evidence supports a HYBRID collapse, not a single monolith, matched to the measured regimes:

- CORE (all captures): a CONVERGED convex disk/dual solve (D12: from-scratch pure-Java primal-dual
  interior-point SOCP, dependency-free; the shipped dual left loose only by non-convergence, COPT SOCP
  tight in < 20 ms) giving the tight bound + the active set + the degenerate (vanishing-costate) set.
  Non-degenerate ticks: closed-form costate recovery (shipped, exact).
- COUPLED MULTI-JUMP (the win): the RESIDUAL SOLVE (D02/D14) over the degenerate ticks: k=0 nothing, k=1
  closed-form arc, k=2 univariate, k=3 tightness-test, k=4 tiny B&B; large-k momentum via coupling-graph
  structure (D02-7) or Riemannian (D05). This REPLACES the bad "default the degenerate tick" suggest that
  causes the 0.34 b infeasible recovery. Real infeasibility certificate (fixes F10). Then the objective-
  aware byte-exact finisher (sphere-decode + BucketAscent, D07/D11) KEEPS the good improve.
- GATE (momentum): the gate as ~2n big-M indicators, hybrid (banded fast path + small MIP on cold miss),
  fixing the BnB-null false-negative (F10).
- SINGLE / HALF-ANGLE JUMPS: KEEP the shipped fast path + byte-exact ILS/BucketAscent (already optimal;
  ARCH-1's continuous optimum is not the target there).
- SMOOTHING: one give-back-constrained order-1 trend-filter against ONE shared reference, specializing to
  the residual tie-break over the degenerate ticks (D13), replacing the four stacked stages (fixes F3/F6).
- FREE-START: two box-bounded linear vars in the same convex program + a final rigid translation (F4).
- dF: a per-tick phase equality/sector in the residual (fixes F8's RelaxationRecovery-bails-on-facing).

## 5. Pure-Java port status (honest)

- Convex kernel: VALIDATED via oracle (COPT SOCP < 20 ms tight). Pure-Java port DESIGNED (D12: from-scratch
  primal-dual IPM, dependency-free; no redistributable pure-Java SOCP exists). NOT yet ported. This is the
  single largest implementation piece.
- Residual solve: VALIDATED (residual_branch reaches the global optimum). Pure-Java per-k algorithms
  SPECIFIED (D02/D14, k=0..4 closed-form/univariate/tightness-test/tiny-B&B; all dependency-free). The port
  needs a NEW "convex solve with a subset of ticks pinned to arbitrary yaws" entry (the shipped
  ClosedFormSolve/RelaxationRecovery BAIL on facing constraints, so pin-and-reconverge is not available
  today).
- Byte-exact certify + objective-aware snap: PARTIALLY built (ReplayYaws certifies; sphere-decode snap
  DESIGNED, D07/D11, replaces the dead LatticeRepair).
- Quick-win perf levers found orthogonally (ship independently of ARCH-1): cap buildHessian inner loop
  (bit-identical -28% leaf CPU, B05); stepRange incremental rescoring (~2x on the polishers, B04/B05);
  don't run SmoothingPolish with Smooth off (B04: 94% of a Smooth-off j001 solve).

## 6. Scorecard summary

| approach | captures reaching optimum | byte-exact | vs COPT gap | vs shipped | verdict | measured failure cause |
| --- | --- | --- | --- | --- | --- | --- |
| ARCH-1 (convex+residual+snap), coupled | j021 | yes | +5.6e-5 (over cont) | +1.4e-3 recovered | PROMOTE | - |
| ARCH-1, loose-degenerate | j008b | needs obj-aware snap | continuous reached | worse if naive snap | ITERATE | arbitrary degenerate direction snaps poorly |
| ARCH-1, gate | loopmm | no (clamp-free) | n/a | worse | ITERATE | gate not in clamp-free model |
| shipped fast + ILS, half-angle single | j005/j016/j019/j022 | yes | over-reaches cont | - | KEEP shipped | ARCH-1 cont optimum not the target |
| naive fix-others-rigid | j021/j008b | - | - | - | DEAD | INFEASIBLE (measured); must re-optimize rest |
