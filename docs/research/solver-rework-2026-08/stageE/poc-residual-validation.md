# Stage E precursor: ARCH-1 residual-solve validation (measured, COPT oracle)

Orchestrator prototype, run before the Stage E fan-out, to VALIDATE the SPEC section 4.2 central thesis:
the problem reduces to a convex solve determining the non-degenerate ticks PLUS a low-dimensional
nonconvex residual over the vanishing-costate (throttled) ticks. If a branch over the few degenerate ticks
with a convex re-solve of the rest reaches the COPT global optimum, ARCH-1 is real and is the primary Stage
E port target.

Harness: research/copt/residual_branch.py + residual_poc.py (COPT oracle; NEVER shipped). Reproduce with
`COPT_LICENSE_DIR=... python residual_branch.py j021-rinav1-01 j008b-2jump loopmm-3jump-lands`.

## The mechanism tested

1. Solve the convex SOCP DISK relaxation. Read per-tick modulus slack; the DEGENERATE set D = ticks with
   slack > 1e-3 (the disk throttles |u_t| below m_t there).
2. BRANCH over the degenerate ticks: grid each degenerate tick's circle angle (720 points + local refine).
3. At each branch node, FIX the degenerate ticks at full modulus at the branched angle and RE-OPTIMIZE the
   rest with the convex disk SOCP. Keep the best feasible objective.

## Measured result (the decisive numbers)

| capture | n | jumps | degenerate ticks |D| | which | branch-residual pos | COPT global QCQP | gap | branch time (Python, 720-grid) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| j021-rinav1-01 | 39 | 4 | 1 | [12] | 1067.8638504 | 1067.8638773 | 2.7e-5 | 12.5 s |
| j008b-2jump | 25 | 2 | 1 | [1] | -0.19704188 | -0.19705262 | 1.1e-5 | 4.6 s |
| loopmm-3jump-lands | 33 | 3 | 1 | [0] | -279.2990659 | -279.2990651 | 7.9e-7 | 7.7 s |

The branch-with-convex-reopt residual solve reaches the COPT GLOBAL optimum within 1e-5 to 3e-5 b on all
three coupled multi-jump cases, each with ONLY ONE degenerate tick. The residual gap is grid resolution
(720 points = 0.5 deg; local refine to 0.001 rad) plus COPT's own equality feasibility tolerance (~1e-5,
see caveat), so it is essentially exact.

## What this proves (and the key refinement)

1. THE EFFECTIVE NONCONVEX DIMENSION IS 1 on these coupled cases (measured), not n=25 to 39. A 1D search
   over the single degenerate tick's circle angle, with a convex re-solve of the other 24 to 38 ticks per
   candidate, reaches the global optimum. This is the SPEC section 4.2 reduction, validated.

2. THE CURRENT SOLVER FAILS ON EXACTLY THIS by DEFAULTING the degenerate tick to the objective axis
   (producing the measured 0.34 b infeasible recovery on j021) then falling to a full-n SLP/ILS search. The
   fix is a 1D (or k-D, k<=4) search over the degenerate ticks' angles with the convex completion. Trivial
   pure-Java, fast.

3. KEY REFINEMENT (measured, do not skip): the NAIVE decomposition "fix ALL non-degenerate ticks at their
   disk directions, then free only the degenerate ticks" is INFEASIBLE on j021 and j008b (residual_poc.py
   returned status INFEASIBLE), because the disk's non-degenerate directions are conditioned on the
   throttled tick being SHORT; forcing the throttled tick to full modulus while holding the rest rigid
   breaks the walls. The CORRECT mechanism RE-OPTIMIZES the non-degenerate ticks convexly per branch node
   (they shift slightly). loopmm happens to work with the naive version because its degenerate tick is the
   near-decoupled start tick t0. So Stage E must implement the branch-WITH-convex-reopt, not fix-and-solve.

## Cost projection to the shipped path (for Stage E)

- The 720-point grid is a naive PoC. A golden-section / 1D root-find on the single degenerate tick's angle
  needs ~20 to 40 convex solves; a pure-Java convex disk solve at n<=49 is sub-ms to low-ms (COPT does it
  in <20 ms including overhead; a lean pure-Java first-order SOCP or the existing dual made to converge
  should be faster). So the residual solve is projected at well under the 800 ms envelope, and likely under
  100 ms, at n<=49. Stage E measures this with a real pure-Java prototype.
- For |D|=2..4 (not seen in this sample but possible), a coordinate-descent or a small nested 1D search
  over the k angles, or a tiny spatial B&B, keeps it small. The Pataki bound caps k by the active-wall
  count.

## Caveats (rigor)

- COPT's nonconvex QCQP enforces |u|^2 = m^2 with FeasTol (default ~1e-6), which lets |u| exceed m by ~2e-5
  per tick, so the COPT "global optimum" carries ~1e-5 to 4e-4 numerical slop (this is why the disk bound
  can appear ~4e-4 below the QCQP on j019, a tolerance artifact, not a real relaxation violation). The
  disk-with-fixed re-solve uses FeasTol 1e-9. For the H1/H2 gaps (1.6e-3) and this validation (gap 1e-5)
  the slop is not material, but Stage E should tighten COPT tolerances for the final reference numbers and
  BYTE-EXACT round-trip every prototype solution through ExactJumpModel (the continuous optimum is a
  near-exact reference, not a strict bound; half-angle norm>1 can gain a few e-3, FINDINGS section 4).
- Degenerate-set identification here uses disk-slack > 1e-3. The SDP rank was 2-3 on these cases
  (eig2/eig1 <= 0.024) yet 1 throttled tick sufficed to reach the optimum, so the disk-throttle count is
  the operative (and smaller) residual dimension. Stage E should confirm the degenerate-set detector on
  more captures and set the threshold from the sine floor.

## Generality of the low-dimensional-residual thesis (degenerate-count sweep, measured)

Ran the SOCP disk on 13 fixed-start captures and counted degenerate ticks (disk slack > 1e-3):

| class | captures | degenerate-tick count |
| --- | --- | --- |
| single / easy multi-jump | j925, j019, j016 | 0 |
| redirect / neo multi-jump | j021, j008b, loopmm, j1099, j1149, j155, j718 | 1 |
| momentum / nix jumps | j716 | 10 |
| | j828 | 13 |
| | j1150 (2x2bm nix neo) | 22 |

So the "residual dimension is 1-4" holds for redirect/neo jumps (the majority) but NOT for momentum/nix
jumps, where a whole run-up phase is direction-degenerate (10-22 ticks). HOWEVER, COPT still solves the
FULL nonconvex constant-modulus QCQP GLOBALLY in < 0.5 s on all of them (j1150 22-degenerate 0.46 s, j716
0.19 s, j828 0.07 s, all gap ~1e-5 to 3e-5). So the many-degenerate momentum ticks are LOW effective
difficulty: they form a coordinated momentum phase (axis-locked, CONTEXT.md), not a free 22-D torus. The
hard residual is FEW-but-TIGHT (j021 t12 threading opposing corridors), not MANY-but-LOOSE. Implication
for Stage E: the residual solver must be a smart method (spatial B&B with a convex node relaxation, or a
Riemannian trust-region, both COPT-verified to handle both regimes), not a brute k-D angle grid. The
1-degenerate-tick cases are trivial (1D golden-section); the momentum cases need the smart solver but are
still fast (COPT < 0.5 s).

## Handoff to the Stage E fan-out

This validates ARCH-1 as the PRIMARY architecture. The Stage E prototype (pure-Java) is:
(1) a converging convex disk/dual solve (Stage D12 kernel question); (2) a degenerate-tick detector (disk
slack); (3) a branch-with-convex-reopt over the 1-4 degenerate ticks (1D golden-section per tick or small
B&B); (4) byte-exact snap + certify. Benchmark against these COPT references and the shipped THOROUGH
results (j021 shipped 1067.862397 vs this 1067.86385 vs COPT 1067.863877: the shipped solver leaves 1.5e-3
b that ARCH-1 recovers).
