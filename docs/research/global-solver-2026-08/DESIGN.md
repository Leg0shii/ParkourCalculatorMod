# A certified byte-exact global solver for the MC parkour angle problem (ARCH-2 research)

Synthesis of a 3-agent SOTA survey (2026-08-25): global-MINLP internals, tight relaxations, and
structure-exploiting switched-trajectory optimization. All three converged on one architecture. This doc is
the design record + build order. Raw agent findings summarized inline; key references at the end.

## 0. The reframe that makes this tractable

This is NOT a generic nonconvex NLP. It is a **finite** mixed-integer problem: every angle lives on the
65536-entry sine grid and every inertia gate is binary. So a branch-and-bound **terminates exactly at gap=0
with the certified byte-exact global optimum** - no epsilon-convergence, no "give up and hope". That is the
thing COPT (which solves the continuous MISOCP) does not give us: COPT is the oracle for the continuous
optimum; WE can certify the byte-exact one.

The problem, in solver vocabulary (per axis a in {x,z}, friction f=0.91, gate threshold eps):
```
vtilde_t = f*w_{t-1} + u_t          # u_t = m_t (cos th_t, sin th_t), the move; LINEAR chain
w_t      = vtilde_t if |vtilde_t| >= eps else 0     # the INERTIA GATE (state-triggered disjunction)
x_t      = x_{t-1} + w_t
maximize c . x_n   s.t.  linear wall constraints A x_t <= b
```
Two facts drive everything:
- **Fact A (linear under a fixed gate pattern).** Fix which ticks gate. The chain unrolls to
  `x_n = x_0 + C v_0 + sum_s W_s u_s`, `W_s = (1 - f^{n-s+1})/(1-f)`. Objective and walls become LINEAR in the
  u_s. Conditioned on a gate pattern the whole problem is a convex SOCP on angles.
- **Fact B (only two nonconvexities, both local+finite).** (1) angle on a circle -> relax to the disk
  |u_t| <= m_t; (2) the gate disjunction. Nothing else. The dynamics are linear, so RLT/McCormick/product
  cuts buy NOTHING.

## 1. The converged architecture: Reduced-space, FBBT-driven, certified spatial B&B

Node = per-tick angle bracket `[alpha_t, beta_t]` + gate fixings. The loop:

1. **Presolve / bound-tightening (the single biggest lever).** Forward+backward interval propagation along the
   LINEAR chain (per axis, O(n) each way), iterated to fixpoint (FBBT). It (a) tightens arcs and (b) FIXES
   almost every gate, because eps is tiny (5e-3 legacy / 9e-6 modern) next to a moving player's velocity
   (~0.2-0.3 b/tick). Only k << n "straddling" ticks near sign-changes / near-rest stay ambiguous. This is
   the exact per-axis LTI propagation COPT's generic FBBT does not have.
2. **Warm start in O(n) (the domain edge COPT lacks).** The closed-form adjoint/costate of the friction chain:
   ```
   lam_pos = c;  lam_vel = 0
   for t = n..1:  lam_vel_t = lam_vel;  lam_vel = f*lam_vel + lam_pos
   for t = 1..n:  u_t = m_t * unit(project_to_disk(lam_vel_t))   # Hamiltonian-max move
   ```
   Replay through ExactJumpModel (true gate) -> a strong feasible incumbent from tick 0. The terminal-linear
   objective's "global coupling" is fully captured by this one costate; it does NOT break separability.
3. **Node relaxation.** Maximize the linear objective over: per-tick disk cone `|u_t| <= m_t` + linear walls
   + the **arc chord cut** (below). START with the existing IPM SOCP at nodes (correct, slow). SWAP to a
   k-gon LP (48-64 tangents ~= 5e-4, byte-grid resolution) with a warm-started **dual simplex** for speed:
   because every cone is 2-D, an LP is exact enough and warm-starts across the tree in a few pivots where an
   IPM cannot. This is the one genuinely hard build (a revised dual-simplex in Java 8); IPM-at-nodes is the
   correct fallback.
4. **Branch, on the k ambiguous ticks only** (reduced space: 2^n -> 2^k):
   - **Arc**: bisect `[alpha,beta]` at the midpoint; each child re-emits a tighter chord cut. ~8 levels to 1e-4.
   - **Gate**: 3-way spatial branch `w=0 / w in [eps,M] / w in [-M,-eps]` (interval bounds only, no big-M).
5. **Incumbent**: snap relaxed angles to the sine grid within the arc, replay through ExactJumpModel (resolves
   gates + magnitudes byte-exact), keep if it beats the incumbent. The corridor is far tighter than physics
   tolerance, so the snap loss is bounded/safe.
6. **Prune** `node.ub <= incumbent + gapTol`. Finite domain => gapTol=0 gives the certified global optimum;
   the running gap `(max open ub) - incumbent` is the anytime signal that maps onto Fast (first dive) vs
   Optimize (run to gap<=tol / budget).

### The one formula that unlocks it: the arc chord cut (drops into our kernel unchanged)
For angle bracket `[alpha,beta]`, `mu=(alpha+beta)/2`, `Delta=(beta-alpha)/2`, the EXACT convex hull of the
arc is the disk intersected with ONE half-plane:
```
cos(mu)*x_t + sin(mu)*z_t >= m_t*cos(Delta)
```
That is a **linear wall**. `DiskSocpKernel.solve(n, cx, cz, mMag, walls)` already takes `List<Wall>`, so the
core tightening needs NO kernel change (answers the general-cone-vs-specialized fork: stay specialized).
Sagitta error `m_t(1-cos Delta) ~ m_t Delta^2/8`; with friction amplification ~1/(1-f)~11x, target arcs ~1 deg
per tick to hold end position to 1e-4. Branch the CONTINUOUS relaxation to ~1 deg, then snap to the grid at
the leaf (never branch to grid resolution).

### The gate has NO useful root cut (measured truth)
The gate's convex hull in w-space is the whole interval [-M,M]; the "zero-or->=eps" hole is intrinsically
nonconvex. So do NOT hunt for a gate cut - branch it (step 4). Perspective/rotated-cone machinery is INERT in
our model (it only tightens a CONVEX term switched by the gate; our dynamics+objective are linear). It becomes
relevant only if we later add a gated quadratic (e.g. an L2 smoothing/turn-energy penalty).

## 2. Why this is "truly superior" to COPT (honest version)

Not asymptotically - the problem is NP-hard in the mode count (state-triggered dead-zone; the exact DP value
function has exponentially many PWA regions). The win is a large PRACTICAL factor + two things COPT cannot give:
- **Certified BYTE-EXACT global** (COPT certifies the continuous MISOCP optimum, not the sine-grid one).
- **Domain edges COPT structurally lacks**: the O(n) closed-form costate warm start, and exact per-axis LTI
  interval propagation that fixes most gate binaries before branching. COPT does generic OBBT/FBBT/perspective;
  it does not know the 0.91 chain in closed form.
- **Shippable pure Java 8** (COPT is native+licensed; unusable in the Forge loaders - the whole reason we build).

HONEST CAVEAT: the edge is reduced-space branching on k ambiguous ticks. If a run has many near-rest ticks
(k grows with n), the advantage erodes toward generic B&B. j003 (n=176, gate-critical tail ticks 98-166) is
plausibly that worst case. Mitigation: the receding-horizon window decomposition keeps per-window k small
(exponential decay of sensitivity along the friction chain licenses the split); escalate the hardest runs to a
tight GCS/perspective relaxation + rounding, or temporal Lagrangian bounds.

## 3. Build order (ROI-ranked; S=hours, M=1-3 days, L=1-2 weeks)

| # | Piece | ROI | Effort | Reuse |
| --- | --- | --- | --- | --- |
| 1 | Chain FBBT + per-axis reachability gate-fixing (forward+backward to fixpoint) | highest | S-M | new |
| 2 | Arc model + chord cut (one wall) + bisection | highest | S | DiskSocpKernel |
| 3 | Costate warm start + round-and-simulate incumbent | highest | S | ExactJumpModel |
| 4 | Reduced-space B&B driver (best-bound + DFS dive; arc-bisect + 3-way gate) | high | M | new |
| 5 | Validate root bound + incumbents vs COPT oracle on the corpus | high | S | COPT harness |
| 6 | Swap node engine: k-gon LP + revised dual-simplex warm start | high | **L** | new (hard piece) |
| 7 | Reliability pseudocost branch selection (most-fractional first) | med | M | new |
| 8 | Escalation for large-k: window decomposition / GCS-perspective / Lagrangian bound | med | M-L | LongRunSolver |

TRAPS (documented negative-ROI for THIS chain geometry - do not build): OBBT (FBBT dominates on a chain),
factorable reformulation / auxiliary-var DAG / alpha-BB, Ben-Tal-Nemirovski SOC lifting (overkill at N=2, a
flat k-gon is exact enough), general SOS1/lifted-conic-cut engines, heavy presolve/probing, big-M gates
(branch by fixing), branching directly on the 65536 buckets (branch arcs, snap at leaves).

Phase 0 (buildable now, all reuse): pieces 1+2+3 give a working certified B&B with IPM-at-nodes. Benchmark vs
COPT (5), then piece 6 for the speed that makes n~176 tractable.

## 4. Benchmark plan (COPT as oracle)

- Run the COPT harness (research/copt) across the corpus for the continuous-MISOCP global optima + COPT's own
  time/gap. This is the target. (Needs the COPT license; user-run.)
- Validate: our root SOCP bound should match COPT's continuous relaxation bound to tolerance (mismatch = a
  modeling bug). Our byte-exact incumbent should meet-or-exceed the byte-exact optimum; on small n, let COPT
  solve the full MISOCP with a fine grid encoding and confirm.
- Report per capture: certified global vs current NEW-solver objective, and time. Reuse CorpusBench.

## 5. References (contribution in one line)
- Belotti et al., Mixed-Integer Nonlinear Optimization, Acta Numerica 2013 - spatial B&B skeleton.
- Quesada-Grossmann 1992; Lundell-Kronqvist-Westerlund (SHOT) 2022 - single-tree LP-outer-approx node engine.
- Benson-Saglam MISOCP survey 2013 - LP-OA vs NLP-B&B node-engine menu; LP warm-starts win.
- Coffrin-Hijazi-Van Hentenryck, the QC relaxation (PowerModels) - trig envelopes; arc chord = tangent cut.
- Gunluk-Linderoth 2010; Frangioni-Gentile 2006; Atamturk-Gomez 2020 - perspective/indicator hulls (for a
  FUTURE gated convex term only).
- Marcucci-Tedrake HSCC 2019; Marcucci et al. Graph of Convex Sets, SIAM J. Opt. 2024 - perspective disjunction
  strength (big-M vs convex-hull: "minutes vs hours"); GCS for mode-sequence-over-horizon.
- Bemporad-Borrelli-Morari, Automatica 2006 - PWA value function region blow-up (why exact DP is exponential).
- Quirynen et al. 2023 - tailored presolve / exact binary fixing via block-sparse temporal propagation.
- Shin-Zavala-Anitescu - exponential decay of sensitivity (licenses temporal decomposition).
- SCIP reliability pseudocost branching (Achterberg).
