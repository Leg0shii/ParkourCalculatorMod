# Research findings — per-tick constraint derivation & folding collision into the solver

> **Companion to** `block-constraint-derivation-and-collision-integration.md` (the
> research *brief*). This is the *answer*: a cited, confidence-ranked synthesis of
> a five-angle literature pass, mapped back onto the brief's Q1 (§6), Q2 (§7), and
> the §8 "GCS is the most unifying lens" hypothesis. The brief is the question;
> read it first.

## Provenance & confidence calibration

Five parallel research passes were run (lossless convexification; safe-corridor/MILP;
Graph-of-Convex-Sets; SCP/SDF/MPCC collision-in-solver; swept-CCD/homotopy/kinodynamic).
Each returned falsifiable claims with primary-source citations; claims were
cross-checked across passes for convergence and contradiction. **No surviving
contradictions** — the five angles are mutually reinforcing.

**Caveat:** three of five passes hit HTTP 403 on direct fetches of arXiv / Science /
Drake / IJRR, so a subset of claims rest on search-result summaries of those primary
sources rather than full-text reads. Author/venue/year provenance is confirmed and the
works are canonical; **exact theorem numbers and quantitative benchmark figures are
marked `med` and should be re-verified against the PDFs before being cited as
load-bearing.** Confidence tags: `high` = multiple independent summaries agree and the
fact is canonical; `med` = single summary or quantitative figure; `low` = inferred.

---

## 0. Bottom line (read this, then the rest is justification)

1. **Your crown-jewel zero-gap dual is a theorem about *control* constraints, not
   *state* constraints.** Lossless convexification (LCvx) covers the fixed-modulus
   `‖u_t‖=m_t` sphere; it does **not** cover obstacle/keep-out zones, which are
   non-convex *state* constraints. Adding collision avoidance **breaks the
   losslessness proof in general** — *unless* the per-tick keep-out is held as a
   **convex set fixed outside the inner solve** (a single half-space / box face /
   corridor cell). [high]

2. **That one escape hatch is the unifying thread.** It is simultaneously: the LCvx
   "convex state constraint active at isolated instants" condition (angle 1), the
   safe-corridor / IRIS face set (angle 2), and a GCS node (angle 3). Your §8
   hypothesis is therefore **correct in a refined form**: GCS-over-free-space is the
   right *outer skeleton*, **but it is not a drop-in total solver** — it cannot host
   either of your two hard nonconvexities (the modulus sphere or the asymmetric
   swept clamp) natively.

3. **Recommended architecture = your instinct, made principled:** an outer discrete
   layer that picks a **corridor of convex free-space cells** (= homotopy class),
   wrapping your **existing closed-form dual** run *inside the fixed cell*, with the
   **exact swept oracle as the gating verifier you already own**. This keeps the
   microsecond inner solve, deletes most of the bespoke DERIVE heuristics, and gives
   the discrete layer real completeness/optimality structure. Two backstops: an **SCP
   shell** with *your own* exact swept signed-distance (no margin) for cell-boundary
   riding, and **SST*** against the black-box clamp as the honest completeness
   fallback when no convex cell can capture the asymmetric sweep.

4. **Two latent correctness warnings even for today's collision-free core:**
   discrete-time LCvx is provably *leaky* (a bounded number of ticks can land
   strictly inside the ball, modulus unmet), and the annular/pinned-modulus case is
   only *sometimes* solvable in a single convex pass. **Budget an explicit per-tick
   boundary check / projection** rather than trusting losslessness tick-by-tick. This
   may be a latent bug in `ClosedFormSolve` / `CostateDualSolver`. [med]

---

## 1. The fault line that organizes everything: convex vs non-convex *state* constraints

Every paradigm surveyed lives on one side of a single fault line.

- **Convex side (fast, certifiable):** affine `u↦p` dynamics, box footprints, linear
  objective, and the fixed-modulus sphere *via LCvx*. Here a closed-form dual / SOCP
  runs in microseconds–milliseconds (G-FOLD ran a powered-descent SOCP in ~100 ms on
  a Pentium-M, <700 ms on a rad-hard RAD750). [high]
- **Non-convex side (slow, local, or sampled):** the obstacle-free space is non-convex,
  and Minecraft's swept axis-sequential clamp is non-convex **and** non-smooth (the
  X/Z resolution order even switches on `sign(|v_x|−|v_z|)` post-1.14). [high]

**Key theorem boundary (angle 1).** LCvx (Açıkmeşe–Blackmore, *Automatica* 2011;
Harris–Açıkmeşe, *Automatica* 2014; tutorial Malyuta et al., *IEEE CSM* 2022) is exact
for the **control-constraint class** and for **convex state constraints active only at a
finite set of instants**. A keep-out zone is a **non-convex state constraint active over
an interval** → outside the theorem. Every primary source routes obstacles to
**successive convexification (SCvx / GuSTO)**, which keeps only *local* optimality.
[high] **Implication:** you cannot fold a non-convex obstacle into the dual and expect
the zero gap to survive. You *can* fold a **fixed convex cell face** in, and the gap
survives. This single fact dictates the architecture.

---

## 2. Q1 — best per-tick constraints from blocks (brief §6)

**Framing confirmed:** "pick per-tick half-spaces that wrap obstacles" **is** the
*convex free-space decomposition + corridor assignment* problem, and the cleanest
modern unification of its discrete+continuous halves **is** Graph-of-Convex-Sets.
Ranked options:

### 2.1 Convex free-space cells + corridor assignment (recommended core)
Decompose obstacle-free space into convex polytopes; each tick (or tick-run) is assigned
to a cell; **the cell's faces *are* the per-tick linear position bounds your alphabet
already accepts**. [high]

- **For axis-aligned blocks specifically, skip IRIS.** IRIS/IRIS-NP/FIRI exist to grow
  large convex regions in *arbitrary* geometry via an SDP (max-volume inscribed
  ellipsoid) that is the bottleneck (~8–75 s per region) [med]. **Your free space is the
  complement of AABBs — its convex cells are axis-aligned rectangles obtainable by a
  plane-sweep / rectangular arrangement in O(n log n), no SDP.** This sidesteps angle
  2's headline failure mode: *IRIS/SFC ellipsoid inflation goes degenerate/empty at
  ~1e-6 gaps* — you never run that inflation. [high, synthesis]
- **Safe Flight Corridors** (Liu, Watterson, Kumar et al., *RAL* 2017) are the
  zero-integer special case: fix tick→cell assignment from a skeleton path, inner solve
  is a pure QP. Fast, but **locks one homotopy class** and can miss the global corridor.
  [high]

### 2.2 Homotopy-class enumeration (the "which side" decision, your `k*` replacement)
Your forced-crossing-tick `k*` is a hand-coded homotopy-class selector. State of the art
is the **h-signature** (Bhattacharya, Likhachev, Kumar; AAAI 2010 / *Auton. Robots* 2012;
3-D via Biot–Savart, RSS 2011): a topological invariant; augment graph-search states with
the partial signature so one A*/Dijkstra enumerates the K distinct classes. [high]
**Temporal-reachability gating (brief §8-H) is cheap here:** with affine dynamics the
per-tick forward-reachable set is a disk of radius `Σ gains·m_t`, so a class/cell that is
not reachable in time is pruned by a closed-form radius test — directly answering the
brief's "is this side admissible in time?" question. [synthesis]

### 2.3 Disjunctive MILP/MISOCP (the explicit form; usually dominated by GCS)
Richards–How (ACC 2002): one binary per obstacle face + big-M; "outside ≥1 face" via
`Σ binaries ≤ faces−1`. **Adversarial flags:** big-M relaxations are *loose*, and for
AABBs spanning large coordinate ranges M must be huge, further loosening the LP and
slowing branch-and-bound; integer count ≈ ticks×regions drives exponential blow-up.
[high] The **perspective / convex-hull (multiple-choice)** reformulation is the tightest
disjunction — and **GCS *is* that perspective formulation applied to corridor
selection**, with empirically tight relaxations, so it generally **subsumes** the big-M
MILP framing. [high]

### 2.4 The minimal alphabet extension you asked about (brief §6.5)
**Add oblique half-spaces `aᵀp_t ≤ b`.** Your affine `u↦p` map supports them natively
(they stay linear in `u`), they are essentially free, and they are *necessary*: axis-
aligned `X/Z` bounds cannot tightly wrap a diagonal corridor or the **sheared
parallelogram** swept volume of §4.2. This is the one extension that earns its place;
recommend it. Everything else (facing/velocity bounds) you already have. [synthesis]

---

## 3. Q2 — fold collision *into* the solver (brief §7)

Scored against your constraints (preserve fast convex/dual core · handle *swept*
continuous collision · exact at ~1e-6 with **no inflation**):

| Paradigm | Fast convex inner solve? | True swept model? | Exact at 1e-6, no inflation? |
|---|---|---|---|
| **SCP + signed distance** (TrajOpt, SCvx) | ✅ **yes** — convex QP/SOCP per iter (its *defining* feature) | ⚠️ swept = **convex hull of consecutive poses** — symmetric, conservative, *opposite* of your asymmetric clamp | ❌ needs `d_safe` margin by construction |
| **SDF penalty** (CHOMP, STOMP, GPMP2) | ◑ cheap, but collision is a *soft cost*, not a constraint — no certifiable dual | ❌ discrete knots (GP interp ≠ true sweep) | ❌ uses `ε` clearance band; no feasibility guarantee |
| **MPCC / contact-implicit** (Posa; ALTRO; CALIPSO) | ❌ general nonconvex SQP / AL-IP-Newton; **LICQ & MFCQ fail at every feasible point** | ◑ models contact, not your axis-ordered sweep | ❌ exact only as `α,ε→0`; finite values permit interpenetration |
| **Projected/black-box rollout** (CMA-ES — you already use) | ❌ derivative-free | ✅ calls exact clamp | ✅ exact (it *is* the oracle) — but loses the independent verifier role |
| **Kinodynamic sampling** (SST*) | ❌ sampling | ✅ exact black-box clamp | ✅ exact, no margin |

**Reading of the table:**

- **SCP-with-signed-distance is the *only* collision-in-solver paradigm that preserves
  your fast convex/dual inner solve** [high]. Even the "convex" contact-implicit methods
  (Önol+SCvx 2019; **CRISP** 2025) work precisely by *reducing the MPCC back to a
  convex-QP-per-iteration SCP* — confirming SCP is the structural winner. **MPCC is the
  worst fit** (breaks constraint qualifications). [high]
- **But every published collision model inflates** (`d_safe` / `ε` / Scholtes `α` /
  Fischer–Burmeister `ε` / SCvx virtual buffers), which is *exactly* the §4.4 thing you
  cannot afford. [high] So you do **not** adopt any paper's collision model wholesale.
- **The actionable recipe:** keep an **SCP shell** (trust region + ℓ1 *exact* penalty),
  but **linearize *your own* exact asymmetric swept signed-distance and its subgradient**
  as the per-iteration cut, with **zero margin**, and gate acceptance with the exact
  swept oracle you already ship. This is **not** the "reactive cutting plane that didn't
  converge" (brief §5): the differences that buy convergence are (i) a **trust region**
  bounding the per-iteration step, (ii) **simultaneous multi-tick cuts** instead of one
  violated wall at a time, (iii) **homotopy-aware initialization** from §2.2, and (iv)
  ℓ1-exact-penalty convergence theory. [synthesis]

**Why true swept exactness can't be made smooth:** angle 5 is decisive — *no published
CCD method is simultaneously smooth, exact, and zero-inflation.* The only family safe at
1e-6 is **conservative-inclusion CCD** (C2A / additive-CCD / Tight-Inclusion), which is
provably no-tunneling but **rounds toward reporting collision (false positives)** and, in
IPC's case, needs a positive standoff `d̂` — again incompatible with your permissive
zero-clearance ground truth. [high] **Good news:** for *segment-vs-AABB* the slab /
Minkowski test is already **exact and closed-form**, so your only genuinely non-analytic
piece is faithfully reproducing the **Y→X→Z order-dependent clamp** — which you already
have byte-exact in `SweptCollision`. Don't import a CCD library; you have the exact
oracle.

---

## 4. The §8 hypothesis verdict — "GCS over free-space regions is the most unifying lens"

**Refined verdict: TRUE for the spatial/combinatorial + affine-dynamics + swept-corridor
structure; FALSE as a single total solver for your two hard nonconvexities.**

**For (the strong case):**
- GCS (Marcucci–Umenberger–Parrilo–Tedrake, *SIAM J. Opt.* 2024; *Science Robotics* 2023)
  jointly picks the discrete corridor and continuous trajectory; its perspective
  relaxation is **empirically tight** so cheap rounding usually recovers the global path.
  It **provably generalizes** MILP corridor selection and big-M MLD encodings with a
  tighter relaxation. [high]
- Your **affine dynamics + box obstacles fit GCS nodes natively.** [high]
- **Swept/continuous collision has a GCS form: Space-Time GCS** (arXiv 2508.10203 /
  2503.00583, 2025) lifts time into the graph, giving collision-free higher-order splines
  *without* time-sampling. [med]
- **GCS\*** (Chia, Jiang, Graesdal, Kaelbling, Tedrake; WAFR 2024) gives an A*-style
  *cost-optimal, complete* search over *implicit* graphs of convex sets — i.e. you get
  completeness/optimality structure your current outer enumeration lacks. [high]

**Against (the load-bearing limitation):**
- **The fixed-modulus sphere `‖u_t‖=m_t` is a non-convex equality no GCS node can
  represent.** Drake's own escape hatch: feed a **convex surrogate** (ball/annulus) to
  the relaxation and defer the true equality to the **non-convex rounding/restriction**
  step. So for *your core nonconvexity* GCS degrades from "global convex solver" to "a
  relaxation that must be rounded against a constraint it cannot certify" — **no tightness
  guarantee**, same residual feasibility risk as an NLP. [med] This is exactly where
  **your LCvx dual is strictly better than GCS** (angle 1 vs angle 3): GCS convexifies the
  *spatial/combinatorial* nonconvexity; LCvx convexifies the *input-modulus* nonconvexity;
  **neither subsumes the other.** [med]
- **GCS optimality is empirical, not a-priori** (relaxation = lower bound, rounded path =
  upper bound; gap observed, not theoretically bounded for general graphs). [high]
- GCS cannot host the **non-smooth asymmetric clamp** as a per-node constraint either —
  every per-region constraint must be convex. [med]

**Therefore the right division of labor (your hypothesis, corrected):**
> **GCS (or GCS\*) as the *outer corridor selector / homotopy enumerator*** over cheap
> axis-aligned free-space cells, **wrapping your LCvx closed-form dual as the *inner*
> per-cell solver**, **gated by your exact swept oracle.** GCS supplies the discrete
> structure (completeness, tight corridor selection, implicit class enumeration); LCvx
> supplies the thing GCS can't (the certified fixed-modulus inner solve); the oracle
> supplies the thing neither can (exact asymmetric swept truth).

---

## 5. Recommended architecture, mapped to your repo

```
BLOCKS (AABBs)
   │
   ├─[A] Plane-sweep free-space decomposition → axis-aligned convex cells       (NEW; O(n log n), no IRIS/SDP)
   │
   ├─[B] Build Graph of Convex Sets over cells; edges = adjacency               (NEW; GCS / GCS* outer layer)
   │        prune edges by per-tick forward-reachable disk (radius Σ gains·m_t)  (cheap reachability gate, §8-H)
   │        h-signature on the chosen path = homotopy class (replaces k* grid)
   │
   ├─[C] For the selected corridor (sequence of cells):
   │        inner solve = EXISTING closed-form dual, cell faces as constraints   (REUSE ClosedFormSolve /
   │        + oblique half-spaces aᵀp_t ≤ b for sheared swept volume             (CostateDualSolver; minimal
   │        + per-tick boundary projection for LCvx discrete-leakiness            alphabet extension)
   │
   ├─[D] If a cell boundary must be *ridden* tightly: SCP shell linearizing      (NEW; trust region + ℓ1 exact
   │        YOUR exact swept signed-distance, zero margin, multi-tick cuts        penalty around the dual core)
   │
   ├─[E] GATE: exact swept oracle + landing footprint                            (REUSE SweptCollision)
   │
   └─[F] Fallback when no convex cell captures the sweep: SST* against the        (NEW; honest completeness
            black-box clamp; tune/benchmark offline via headless harness          backstop, self-validated)
```

Repo touch-points: `ExactJumpModel` / `SweptCollision` (oracle, unchanged) · `JumpLinearModel`,
`CostateDualSolver`, `ClosedFormSolve` (inner dual, add boundary projection + oblique
half-spaces) · `JumpConstraintCompiler` (emit cell faces instead of bespoke keep-outs) ·
new outer corridor/GCS layer replacing the forced-crossing-tick homotopy planner.

**What this deletes:** the bespoke side-selection, the `k*` enumeration window, the `+1`
dilation heuristic, and the *duplication* of collision living in both an approximate
constraint and a post-hoc verifier (collision now lives in exactly one place — the gate;
the cells are derived, not hand-tuned).

---

## 6. What NOT to do (adversarial)

- **Don't inflate.** Every off-the-shelf collision-in-solver model (TrajOpt `d_safe`,
  CHOMP/GPMP2 `ε`, MPCC `α`) bakes in a margin that kills your 1e-6 jumps. [high]
- **Don't run IRIS/SFC ellipsoid inflation on tight gaps** — it goes degenerate/empty at
  ~1e-6. Use the AABB-complement rectangular decomposition instead. [high]
- **Don't pursue MPCC/contact-implicit** for the main solver: it breaks LICQ/MFCQ at
  every feasible point and recovers tractability only via the relaxation you can't
  afford. (Contact-implicit's appeal — *exploiting* wall-sliding the way expert players
  do — is real but is better captured by the SCP-on-cell-boundary shell [D].) [high]
- **Don't trust discrete-time losslessness tick-by-tick** — it's leaky (Luo 2024; Lee
  2025). Project to the modulus boundary per tick. [med]
- **Don't expect GCS to solve the modulus sphere** — it can't; that's what the LCvx inner
  core is for. [med]

---

## 7. Citations

**Lossless convexification / fixed-modulus (Q1 core, brief §8-A)**
- Açıkmeşe & Ploen, "Convex Programming Approach to Powered Descent Guidance for Mars Landing," *JGCD* 30(5), 2007. https://doi.org/10.2514/1.27553
- Açıkmeşe & Blackmore, "Lossless convexification of a class of optimal control problems with non-convex control constraints," *Automatica* 47(2):341–347, 2011. https://www.sciencedirect.com/science/article/abs/pii/S0005109810004516
- Açıkmeşe, Carson & Blackmore, "Lossless Convexification of … Soft Landing Optimal Control," *IEEE TCST* 21(6), 2013. http://www.larsblackmore.com/iee_tcst13.pdf
- Harris & Açıkmeşe, "Lossless convexification … for state constrained linear systems," *Automatica* 50(9), 2014. https://www.sciencedirect.com/science/article/abs/pii/S0005109814002362
- Kunhippurayil, Harris & Jansson, "Lossless convexification … annular control constraints," *Automatica* 133:109848, 2021. https://www.sciencedirect.com/science/article/abs/pii/S000510982100368X
- Dueri et al., "Customized Real-Time Interior-Point Methods for Onboard Powered-Descent Guidance," *JGCD*, 2017. https://arc.aiaa.org/doi/10.2514/1.G001480
- Malyuta et al., "Convex Optimization for Trajectory Generation" (tutorial), *IEEE CSM*, 2022. https://arxiv.org/abs/2106.09125
- Luo et al., "Revisiting Lossless Convexification: … Discrete-time," arXiv:2410.09748, 2024. https://arxiv.org/abs/2410.09748
- Lee et al., "Discrete-Time Lossless Convexification for Pointing Constraints," arXiv:2501.06931, 2025. https://arxiv.org/abs/2501.06931

**Convex decomposition / safe corridors / MILP (Q1, brief §8-B/C)**
- Deits & Tedrake, "Computing Large Convex Regions of Obstacle-Free Space …" (IRIS), WAFR 2014. https://groups.csail.mit.edu/robotics-center/public_papers/Deits14.pdf
- Deits & Tedrake, "Efficient Mixed-Integer Planning for UAVs in Cluttered Environments," ICRA 2015. https://groups.csail.mit.edu/robotics-center/public_papers/Deits15.pdf
- Wang et al., "FIRI: Fast Iterative Region Inflation," arXiv:2403.02977, 2024. https://arxiv.org/abs/2403.02977
- Petersen & Tedrake, "Growing Convex Collision-Free Regions … (IRIS-NP)," arXiv:2303.14737, 2023. https://arxiv.org/abs/2303.14737
- Liu, Watterson, Mohta, Sun, Bhattacharya, Taylor, Kumar, "Planning Dynamically Feasible Trajectories … Safe Flight Corridors," *RAL* 2017. https://ke-sun.github.io/publication/liu-ral-2017/ — code: https://github.com/sikang/DecompUtil
- Richards & How, "Aircraft Trajectory Planning with Collision Avoidance Using MILP," ACC 2002. https://www.et.byu.edu/~beard/papers/library/RichardsEtAl02.pdf
- Tsay et al., "P-split formulations … between big-M and convex hull," *Math. Programming*, 2022. https://arxiv.org/abs/2202.05198
- Tang et al., "Towards Optimizing a Convex Cover of Collision-Free Space …," arXiv:2406.09631, 2024. https://arxiv.org/abs/2406.09631

**Graph of Convex Sets (brief §8-C, the hypothesis)**
- Marcucci, Umenberger, Parrilo, Tedrake, "Shortest Paths in Graphs of Convex Sets," *SIAM J. Opt.* 34(1), 2024. https://arxiv.org/abs/2101.11565
- Marcucci, Petersen, von Wrangel, Tedrake, "Motion planning around obstacles with convex optimization," *Science Robotics* 8(84), 2023. https://www.science.org/doi/10.1126/scirobotics.adf7843 — code: https://github.com/RobotLocomotion/gcs-science-robotics
- Chia, Jiang, Graesdal, Kaelbling, Tedrake, "GCS\*: Forward Heuristic Search on Implicit Graphs of Convex Sets," WAFR 2024. https://arxiv.org/abs/2407.08848
- von Wrangel, Tedrake et al., "Using Graphs of Convex Sets to Guide Nonconvex Trajectory Optimization," IROS 2024. https://groups.csail.mit.edu/robotics-center/public_papers/Wrangel24.pdf
- Marcucci & Tedrake, "Mixed-Integer Formulations for Optimal Control of Piecewise-Affine Systems," 2018/2021. https://groups.csail.mit.edu/robotics-center/public_papers/Marcucci18.pdf
- Osburn, Peterson, Salmon, "… Collision-Free Trajectory Planning Using Space-Time GCS," arXiv:2508.10203, 2025. https://arxiv.org/abs/2508.10203
- "Space-Time Graphs of Convex Sets for Multi-Robot Motion Planning," arXiv:2503.00583, 2025. https://arxiv.org/abs/2503.00583

**Collision-in-solver: SCP / SDF / MPCC (Q2, brief §8-F)**
- Schulman et al., "Finding Locally Optimal, Collision-Free Trajectories with Sequential Convex Optimization" (TrajOpt), RSS 2013; *IJRR* 33(9), 2014. https://www.roboticsproceedings.org/rss09/p31.pdf
- Zucker, Ratliff, Dragan et al., "CHOMP," *IJRR* 32(9-10), 2013. https://www.ri.cmu.edu/pub_files/2013/5/CHOMP_IJRR.pdf
- Mukadam, Dong, Yan, Dellaert, Boots, "Continuous-Time Gaussian Process Motion Planning" (GPMP2), *IJRR* 2018. https://arxiv.org/abs/1707.07383
- Posa, Cantu, Tedrake, "A direct method for trajectory optimization of rigid bodies through contact," *IJRR* 33(1), 2014. https://dl.acm.org/doi/10.1177/0278364913506757
- Mao, Szmuk, Açıkmeşe, "Successive Convexification of Non-Convex Optimal Control Problems with State Constraints," 2017. https://arxiv.org/pdf/1701.00558
- Howell, Jackson, Manchester, "ALTRO: A Fast Solver for Constrained Trajectory Optimization," IROS 2019. https://rexlab.ri.cmu.edu/papers/altro-iros.pdf
- Howell et al., "CALIPSO: A Differentiable Solver … Conic and Complementarity Constraints," ISRR 2022. https://arxiv.org/abs/2205.09255
- Önol, Long, Padır, "Contact-Implicit Trajectory Optimization Based on a Variable Smooth Contact Model and Successive Convexification," ICRA 2019. https://arxiv.org/abs/1810.10462
- Li et al., "CRISP: On the Surprising Robustness of Sequential Convex Optimization for Contact-Implicit Motion Planning," arXiv:2502.01055, 2025. https://arxiv.org/abs/2502.01055
- Scholtes, "A New Regularization Scheme for MPCC," *SIAM J. Opt.* https://epubs.siam.org/doi/10.1137/070705490

**Swept CCD / homotopy / kinodynamic (Q2 fallback, brief §8-D/E/G)**
- Tang, Kim, Manocha, "C2A: Controlled Conservative Advancement for Continuous Collision Detection," ICRA/TVCG. https://graphics.ewha.ac.kr/C2A/C2A.pdf
- Li, Ferguson et al., "Incremental Potential Contact (IPC)," SIGGRAPH 2020, *ACM TOG* 39(4) (introduces additive CCD). https://ipc-sim.github.io/
- Tang, Tong, Wang, Manocha, "Fast and Exact CCD with Bernstein Sign Classification," *ACM TOG* 33(6), 2014. http://gamma.cs.unc.edu/BSC/index.htm
- Wang, Ferguson et al., "A Large-scale Benchmark and an Inclusion-based Algorithm for CCD," *ACM TOG*, 2021. https://dl.acm.org/doi/10.1145/3460775
- Bhattacharya, Likhachev, Kumar, "Topological Constraints in Search-Based Robot Path Planning" (h-signature), AAAI 2010 / *Auton. Robots* 2012. https://www.lehigh.edu/~sub216/local-files/topology_AURO_author_version_57596.pdf
- Webb & van den Berg, "Kinodynamic RRT*," ICRA 2013. https://arxiv.org/pdf/1205.5088
- Perez et al., "LQR-RRT*," ICRA 2012. https://lis.csail.mit.edu/pubs/perez-icra12.pdf
- Li, Littlefield, Bekris, "Asymptotically Optimal Sampling-based Kinodynamic Planning (SST/SST*)," *IJRR* 2016. https://arxiv.org/abs/1407.2896
- "Collisions" (axis-sequential Y-X-Z collide-and-slide), Minecraft Parkour Wiki. https://www.mcpk.wiki/wiki/Collisions
