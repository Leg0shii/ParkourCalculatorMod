# Research prompt — deriving per-tick constraints from selected blocks, and folding collision into the solver

> **How to use this document.** This is a *research brief*, not an implementation
> ticket. Hand it to a research pass (a model, a person, or a structured
> algorithm-search workflow). The goal is to (a) restate our problem in clean,
> standard mathematical language, (b) map it onto well-studied problem classes so
> the literature can be mined for solutions, and (c) return concrete, citeable
> candidate methods for two coupled questions. The *answer* we want back is a
> ranked set of formulations + algorithms with their assumptions, complexity, and
> how they'd handle our Minecraft-specific wrinkles — **not** code yet.
>
> Everything below is self-contained: a researcher with no access to this repo
> should be able to reason about it. Repo pointers are given in `§9` for anyone
> who wants to ground-truth claims.

---

## 0. The ask, in one paragraph

We have a fast, reliable solver that, **given per-tick constraints + one scalar
objective, finds the per-tick facing angles of a Minecraft jump in ~100 ms–3 s**
(this part is *solved*; treat it as an oracle). What is *not* solved well is the
step *before* it: **given a set of selected block hitboxes (a launch pad, zero or
more obstacles, a landing pad), automatically emit the per-tick constraints that
(a) carve out a collision-free landing and (b) the inner solver can actually
satisfy.** Today we do this with a hand-rolled geometric homotopy planner that
emits keep-out half-spaces and then *verifies* against a faithful swept-collision
oracle. We want research into (1) **better ways to choose those per-tick
constraints from block geometry**, and (2) whether we should **stop deriving
collision constraints at all and instead fold collision directly into the
optimizer** (penalty / barrier / complementarity / projection / lazy constraint
generation). Define the problem space cleanly, then tell us which established
problem families this is an instance of and what their best methods are.

---

## 1. Domain primer (read once, then forget the game)

- Minecraft movement is a **discrete-time dynamical system**: the world advances
  in *ticks* (20 Hz). Each tick the player picks movement **inputs** and a
  **facing angle (yaw)**; the engine integrates one deterministic step.
- A **"jump segment"** is a contiguous window of ticks `[startTick … landingTick]`
  (e.g. 11 ticks). The player launches from a known **seed state** (position,
  velocity, yaw) at `startTick` and must land on a target block at `landingTick`.
- The keyboard inputs (which keys, when to jump, sprint, sneak, speed/jump
  potions, ground vs air per tick) are **fixed and given**. The **only free
  variables are the per-tick yaw angles** during the air ticks. Yaw rotates the
  thrust vector applied that tick; that is the entire steering authority.
- Vertical motion (`Y`) is **independent of yaw** (gravity + jump impulse only),
  so the per-tick feet height `y(t)` is known exactly up front. Only the
  **horizontal** `(X, Z)` trajectory depends on the decision variables.
- Player collision hitbox: an axis-aligned box, **0.6 wide (half-width 0.3)** and
  1.8 tall (1.5 sneaking). Blocks are arbitrary axis-aligned boxes (full cubes,
  slabs, stairs, skulls, fences…), captured as real AABBs at selection time.

Once abstracted, **none of the Minecraft flavor matters except two things**: the
exact per-tick horizontal map (`§2`) and the exact between-tick collision rule
(`§4`). Both are nailed down below.

---

## 2. The forward dynamics (precise)

Let the segment have `n` air ticks, decision vector `θ = (θ₀, …, θ_{n-1})` where
`θ_t ∈ ℝ` is the **absolute yaw** at tick `t` (degrees, periodic mod 360).

### 2.1 Per-tick horizontal update (byte-exact)

Each tick applies, in order: (1) a small-velocity momentum cutoff, (2) on a jump
tick an impulse, (3) a thrust `moveFlying` term rotated by the yaw, (4) position
integration `p ← p + v`, (5) friction `v ← v·f`. Concretely, for horizontal
velocity `v_t ∈ ℝ²` and position `p_t ∈ ℝ²`:

```
v_t⁺  = v_t + R(θ_t) · a_t        # a_t = fixed thrust magnitude this tick (m/s²-ish)
p_{t+1} = p_t + v_t⁺
v_{t+1} = f_t · v_t⁺              # f_t = per-tick friction scalar (slip·0.91)
```

where `R(θ_t)` is rotation by yaw and `a_t` is a **fixed-length** input vector
(its magnitude `m_t = |a_t|` is set by the keys: ground-accel, air-accel 0.02,
jump-boost 0.2, ±strafe at 45°). The crucial structural facts:

### 2.2 The horizontal map is **affine in fixed-modulus per-tick input vectors**

Define the per-tick **steering vector** `u_t = R(θ_t)·â_t ∈ ℝ²` with the hard
constraint `‖u_t‖ = m_t` (constant modulus — only its *direction* is free).
Then **position at any tick `k` is an affine function of the `u_t`'s**:

```
p_k = p₀ + Σ_{s<k} C(s,k) · u_s ,     C(s,k) = (S_k − S_s)/F_s   (a scalar friction-coupling gain)
```

This is the single most important structural property. The map `u ↦ p` is
**linear**; the *only* nonconvexity in the unconstrained problem is the
**constant-modulus sphere** `‖u_t‖ = m_t` on each tick. This is exactly the
structure exploited by **lossless convexification of minimum-fuel / fixed-thrust
trajectory problems** (Açıkmeşe–Blackmore): the inner maximization
`max_{‖u‖=m} g·u = m‖g‖` is tight, so the Lagrangian **dual has zero gap** and a
closed-form costate recovery `u*_t = m_t · g_t/‖g_t‖`. **We already ship this**
(microsecond closed-form solver) for the *collision-free, position-wall-only*
case. Hold onto this: any collision formulation that preserves the affine
`u ↦ p` map and adds only convex cuts inherits this fast machinery.

### 2.3 The objective

A single scalar: **maximize or minimize one horizontal coordinate (`X` or `Z`) at
one chosen tick** — a *linear functional* `c·p_k` of the trajectory. (Used e.g.
to "reach as far south as possible at landing.")

### 2.4 The constraint vocabulary the inner solver accepts

This is the **target output alphabet** of the derivation step. The inner solver
consumes only:

- **Per-tick position bounds**: `X_t ≥/≤/= v`, `Z_t ≥/≤/= v`, or a closed range
  `X_t ∈ [lo,hi]` (likewise `Z`). (Position walls — linear in `u`.)
- **Per-tick facing bounds**: `θ_t ≥/≤/= v` or `θ_t ∈ [lo,hi]` (nonlinear in `u`;
  forces the slower fallback solver).
- **Per-tick velocity bounds** (expressed as a difference of consecutive
  positions): `ΔX_t = X_t − X_{t-1} ∈ [lo,hi]`, likewise `ΔZ`.
- **Exactly one** MAX/MIN objective at one tick.

Whatever the derivation produces **must be expressible in this alphabet** (or we
extend the alphabet — that's fair game to propose).

---

## 3. The true feasibility problem (what we actually want to satisfy)

Stripped of surrogates, the real problem is:

> **Find** `θ ∈ [−180,180)ⁿ` **such that** the trajectory `p_{0..n}(θ)`:
> 1. **stands on** the launch footprint the tick before the jump,
> 2. **never collides** with any obstacle AABB on any between-tick sweep (`§4`),
> 3. **lands inside** the landing footprint (an axis-aligned box) at tick `n`,
> 4. and **optimizes** the scalar objective among all such `θ`.

Footprints (1, 3) and the objective (4) are easy: convex box constraints + a
linear functional. **The entire difficulty is constraint (2): swept collision
avoidance**, which is nonconvex, nonsmooth, and history-dependent. The free space
(complement of obstacles) is non-convex, so "find a trajectory through it" is the
classic obstacle-avoidance motion-planning hard part — *plus* the Minecraft swept
rule below.

---

## 4. The collision model (the crux — read carefully)

Collision is **not** an endpoint check and **not** a Minkowski-AABB overlap. It is
Minecraft's `Entity.moveEntity` **swept, axis-sequential clamp**, ported
byte-exactly. Per tick, the intended displacement `(Δx, Δy, Δz)` is resolved
**one axis at a time in the order Y → X → Z**, offsetting the hitbox after each:

- **Y** is clamped first (irrelevant to us — `Y` is yaw-independent and we stay
  strictly outside in `Y` or we don't, known up front).
- **X is clamped against the player's *start-of-tick* `Z`.** ⇒ to translate
  through a block in `X`, you must **already be `Z`-clear the previous tick**.
- **Z is clamped against the player's *already-moved* `X`.** ⇒ you may clear `X`
  in the **same** tick you cross in `Z`.

Consequences that any formulation must respect:

1. **Collision is a property of the swept segment** `p_t → p_{t+1}`, not of
   endpoints. An endpoint-only check is *wrong* and silently passes corner clips
   (this caused real in-game failures).
2. **The resolution is asymmetric and order-dependent** (X uses old Z, Z uses new
   X). The effective keep-out region is a **sheared / parallelogram swept volume**,
   not the symmetric box-Minkowski-sum. In practice we model it with a **"+1 tick
   dilation"**: a per-tick keep-out half-space is active on the in-band tick *and*
   the following tick.
3. **Temporal reachability couples the constraints.** A half-space like "be east
   of the skull (`X ≥ v`)" is **physically unreachable** until some tick `≥ t*`
   given the bounded per-tick thrust; and you must satisfy "east" *before* you may
   satisfy "south of the wall (`Z ≤ w`)". The constraints **interlock with
   look-ahead** — purely reactive, greedy, or per-tick-local fixing thrashes and
   fails to converge (we've burned this path; see `§6`).
4. **No safety margin is affordable.** The canonical hard jump clears the obstacle
   by ~`6.6e-7` blocks. Padding walls by even `1e-3` makes razor-tight jumps
   infeasible. So approximations must be *tight*, or *exact*, or *adaptively
   tightened* — not conservatively bloated.
5. **Block geometry is arbitrary AABBs** (not just unit cubes): slabs, skulls,
   stairs. A formulation must take a list of boxes, each possibly partial.

We have a **byte-exact swept-collision oracle** and a **byte-exact collision-free
forward model** that *agree with the live game for any strictly-outside path*.
This gives us **ground-truth headless validation** of any candidate method (see
`§8`) — no human, no in-game run needed. This is a big lever: the research can
recommend *learned* or *sampling* methods and we can self-validate them offline.

---

## 5. Current architecture (baseline to beat)

Two decoupled sub-problems:

- **SOLVE** *(solved — reuse as an oracle)*: per-tick constraints + objective →
  per-tick yaws. Implemented as a cascade: a **microsecond closed-form
  Lagrangian-dual** solver for the affine/position-wall case (`§2.2`), falling
  back to **multistart CMA-ES** over the byte-exact model (~100 ms–3 s) when
  facing constraints or infeasibility-certification require it, then a
  compass/bucket-ascent polish. It minimizes `objective_sign·objective +
  quadratic penalty(constraint violations)`.

- **DERIVE** *(the weak link)*: block hitboxes → the per-tick constraint set. Our
  current method is a **forced-crossing-tick geometric homotopy planner**:
  1. Pick a **travel axis** (the larger seed→land separation) and a perpendicular
     **keep-out axis**.
  2. For each obstacle, choose a **side** to wrap (a *homotopy class*), defaulting
     to the launch-corridor side, flipping ambiguous/crossing obstacles. Wrap with
     **per-tick keep-out half-spaces** active only while the player's `Y`-window
     overlaps the block, **+1-tick dilated** for the swept rule.
  3. **Pin a forced crossing tick `k*`** (enumerated over a small window) so the
     descent rate — hence the keep-out window — is deterministic, not
     solver-dependent.
  4. Sweep the four endpoint objectives; for each `(homotopy, k*, objective)`,
     SOLVE, then **verify with the swept oracle + landing footprint**. First
     `clean ∧ landed` wins; never report a clipping path as success (returns
     best-effort instead).

**How collision is handled today:** it is *not* in the forward model (which is
deliberately collision-free for byte-exact verification). It is **(a) approximated
up-front as derived keep-out half-space *constraints*** fed to SOLVE as a
quadratic penalty, and **(b) checked exactly post-hoc** by the swept oracle as a
*rejection filter*. Collision is thus an **outer-loop discrete search** (which
homotopy / which `k*`) wrapped around the continuous SOLVE.

**Known limitations / why we're researching:**
- The homotopy/keep-out derivation is **bespoke and brittle** — lots of
  Minecraft-specific heuristics (side selection, `k*` window, `+1` dilation) that
  don't obviously generalize to dense obstacle clusters, multiple interacting
  obstacles, 3-D wraps, or non-cube geometry.
- It is an **outer enumeration × inner solve**; the homotopy/`k*`/objective grid
  can blow up and offers no optimality or completeness guarantee.
- Collision living in **two places** (approximate constraints *and* exact verifier)
  is duplicative and is the source of false-pass/false-fail bugs at the seams.
- **Reactive cutting-plane** off the swept oracle (add the violated wall, re-solve)
  **does not converge** on look-ahead-coupled corner wraps (`§4.3`).

---

## 6. Research Question 1 — best-fitting per-tick constraints from selected blocks

**Frame it as: choosing a convex (or mostly-convex) *surrogate feasible set* for a
nonconvex motion-planning problem, expressed in the per-tick constraint alphabet
of `§2.4`, such that the inner SOLVE finds a swept-clean optimum.**

Questions for the research:

1. **What standard problem is "pick per-tick half-spaces that wrap obstacles" an
   instance of?** We believe it overlaps strongly with **convex decomposition of
   free space + corridor/segment assignment** (e.g. *IRIS* convex regions, *Safe
   Flight Corridors*): decompose the obstacle-free space into convex polytopes,
   assign each tick (or contiguous tick-run) to a polytope, and the polytope's
   faces *are* exactly the per-tick linear position bounds we need. Is this the
   right lens? What are the best modern variants, and how do they pick the
   tick→region assignment (the discrete part)?
2. **Homotopy / topology.** The "which side of each obstacle" choice is a
   **homotopy class** selection. What's the state of the art for **enumerating and
   searching homotopy classes** in cluttered 2-D (H-signatures, winding numbers,
   homology classes à la Bhattacharya–Likhachev)? How do these scale with obstacle
   count, and can they incorporate **temporal reachability** (a side is only
   admissible if reachable in time given bounded control)?
3. **Disjunctive / mixed-integer encodings.** Obstacle avoidance as **big-M
   disjunctive linear constraints** with one binary per (obstacle face, tick) is
   the classic **MILP/MIQP trajectory-with-obstacles** formulation
   (Richards–How, Schouwenaars). Given our *affine* `u↦p` map and *linear*
   objective, the continuous relaxation is convex except for the constant-modulus
   `‖u_t‖=m_t`. Can we get a **MISOCP/MIQCP** where binaries pick obstacle sides and
   the continuous core stays in the fast affine/dual regime? What solver tech makes
   this real-time (warm-started branch-and-bound, presolve, lazy big-M)?
4. **Tightness vs. the `§4.4` no-margin requirement.** Many of these methods rely
   on inflating obstacles by the robot radius and a safety margin. We *cannot*
   inflate (razor-tight jumps). What techniques give **exact or adaptively-tight**
   keep-out encodings — and how do they encode the **asymmetric swept/CCD volume**
   (`§4.2`) rather than a symmetric Minkowski box? Look at **continuous collision
   detection / swept-volume constraints** in trajectory optimization and
   **conservative-advancement** methods, and whether the swept volume can be given
   as an exact polytope per tick.
5. **The constraint *alphabet* fit.** Our SOLVE only ingests axis-aligned per-tick
   `X/Z/F` bounds and one objective. Is that alphabet *expressive enough* to encode
   any homotopy wrap, or do we provably need to extend it (e.g. **oblique
   half-spaces** `aᵀp_t ≤ b`, which the affine map supports natively)? Recommend the
   **minimal alphabet extension** that makes the derivation clean.

---

## 7. Research Question 2 — fold collision *into* the solver (drop the derived-constraint surrogate)

The deeper question: **instead of deriving keep-out constraints and verifying
post-hoc, can collision be a first-class part of the optimization itself**, so the
solver natively avoids walls and we delete the brittle DERIVE heuristics? We see
several candidate paradigms — evaluate each against our structure (affine map,
constant-modulus inputs, nonsmooth swept rule, ~100 ms–3 s budget, no-margin,
periodic yaw, headless ground-truth available):

1. **Penalty / barrier with signed-distance fields.** Add a smooth penalty on the
   **(swept) signed distance** to obstacles (CHOMP / TrajOpt / GPMP style). How to
   build a *swept* SDF matching `§4`? How to keep it tight (no inflation) and avoid
   local minima behind obstacles? Does **graduated nonconvexity / continuation on
   the penalty weight** recover the look-ahead-coupled wraps?
2. **Sequential convex programming (SCP) with collision linearized as per-iter
   cuts.** Each iteration, query the swept oracle, linearize the collision residual
   into a half-space, re-solve the convex (affine + constant-modulus dual) core.
   This is essentially **principled cutting-plane / trust-region SCP** —
   how does it differ from the *reactive* cutting plane that failed us (`§5`), and
   what (trust regions, simultaneous multi-tick cuts, homotopy-aware
   initialization) makes it converge on look-ahead corners?
3. **Through-contact / complementarity (MPCC, LCP).** Treat the axis-sequential
   clamp as **contact dynamics with complementarity constraints** and solve the
   whole thing as an **MPCC / contact-implicit trajectory optimization**
   (Posa–Cantu–Tedrake; Stewart–Trinkle time-stepping). Does the Minecraft clamp
   map cleanly onto an LCP per axis per tick? Is the resulting program tractable at
   our sizes, and does contact-implicit optimization *exploit* contact (sliding
   along a wall) the way expert players do?
4. **Make the forward model collision-aware (projected dynamics) and optimize
   through it.** Bake the real clamp into the step (`p_{t+1} = clamp(p_t + v_t⁺)`),
   making the rollout physical but **nonsmooth/piecewise**. Then optimize with
   methods that tolerate nonsmoothness: **derivative-free (CMA-ES, which we already
   use), bundle/subgradient, or randomized smoothing**. Trade-off: we'd lose the
   byte-exact *collision-free* model's role as an independent verifier — quantify
   that.
5. **Reformulate as a graph / search problem and skip continuous optimization for
   the discrete part.** Discretize reachable per-tick states (or use a **kinodynamic
   sampling planner — RRT*/PRM/SST**) with the swept oracle as the steering/validity
   check and SOLVE as a local connector. Where's the boundary between "search the
   homotopy discretely, optimize yaw continuously" and "search everything"?
6. **Lossless-convexification-preserving collision.** Our killer feature is the
   zero-gap dual on the affine + constant-modulus problem (`§2.2`). **Which of the
   above preserve that** (so collision-aware solves stay near-closed-form), and
   which destroy it (forcing full nonconvex search)? Rank by how much fast structure
   they keep.

We want an **honest comparison**: which paradigm best fits a problem that is
*affine in the controls except for a per-tick modulus sphere*, with *nonsmooth
swept obstacle avoidance*, *hard look-ahead coupling*, *zero-margin tolerance*,
and a *tight time budget* — and which is most likely to **subsume the DERIVE
heuristics entirely**.

---

## 8. Translatability — the problem families to mine (do this explicitly)

The user's core instruction: **define the space so cleanly that it's obvious which
*other* problems this reduces to**, then pull solutions from those mature fields.
Our reading of the candidate reductions (confirm, correct, prioritize, and find
the SOTA in each):

| # | Established problem family | Why our problem is an instance | What to extract |
|---|---|---|---|
| A | **Fixed-thrust / minimum-fuel trajectory opt with lossless convexification** (Açıkmeşe, Blackmore, Mao SCP) | Affine `u↦p`, constant-modulus `‖u_t‖=m_t`, linear objective — *exactly* their structure; we already exploit the zero-gap dual | How they add obstacle/keep-out constraints while keeping convexity; successive convexification |
| B | **MILP/MIQP trajectory planning with obstacle avoidance** (Richards & How; Schouwenaars; Mellinger) | Obstacle = disjunction of axis half-spaces; one binary per face×tick; continuous core is convex | Big-M tightening, branch-and-bound warm-starts, real-time MILP |
| C | **Convex decomposition of free space / safe corridors** (IRIS — Deits & Tedrake; SFC — Liu et al.; GCS — Marcucci et al.) | Per-tick position bounds = faces of a convex region; tick→region = corridor assignment | *Graph-of-Convex-Sets* unifies the discrete (which region) + continuous (where in it) — likely the cleanest single framing |
| D | **Homotopy / homology-class path planning** (Bhattacharya & Likhachev; winding/H-signatures) | "Which side of each obstacle" = topological class; our forced-crossing-tick is a hand-coded class selector | Enumerating/searching classes; making class admissibility *temporal* (reachability-gated) |
| E | **Kinodynamic sampling planners** (RRT*, SST, kinodynamic PRM) | Bounded per-tick control, swept validity oracle, known goal set | Steering with our SOLVE oracle; asymptotic optimality; homotopy-aware sampling |
| F | **Nonsmooth / through-contact trajectory optimization** (CHOMP, TrajOpt, contact-implicit MPCC; Posa, Mordatch) | Collision = nonsmooth contact; sliding along walls = exploiting contact | Signed-distance penalties, complementarity formulations, continuation/graduated nonconvexity |
| G | **Continuous collision detection / swept-volume constraints** | MC collision is explicitly *swept & axis-sequential*, not endpoint | Exact swept-volume polytopes; conservative advancement; encoding the asymmetric Y→X→Z clamp |
| H | **Reachability analysis** (forward reachable sets per tick) | Temporal coupling: a half-space is only active once reachable | Cheaply computing per-tick reachable sets to gate constraint activation and prune homotopy classes |

**We suspect the single most unifying lens is C+D combined — a *Graph of Convex
Sets* over free-space regions where the discrete path through regions encodes the
homotopy class and the continuous program inside each region is our affine
constant-modulus problem.** Validate or refute that hypothesis, and contrast it
with the B (MILP) and F (contact-implicit) framings for Question 2.

---

## 9. Hard requirements any proposed solution must respect

1. **Ground-truth swept-clean is non-negotiable.** Output must be verified
   collision-free under the exact swept rule (`§4`). Approximate internal models are
   fine *iff* a final exact check gates success. Never report a clipping path as a
   solution.
2. **No safety inflation** (`§4.4`): razor-tight (`~1e-6`) clearances must remain
   feasible. Tightness or adaptivity over conservative padding.
3. **Output alphabet** = per-tick `X/Z/F` scalar/range bounds + one MAX/MIN
   objective (`§2.4`), *or* an explicitly justified minimal extension (oblique
   half-spaces are essentially free; anything else must earn its place).
4. **Performance budget**: the whole derive+solve should stay interactive —
   target sub-second typical, a few seconds worst-case. Methods are welcome to
   spend an offline/precompute phase.
5. **Decision variables are periodic yaw** (mod 360); the input map is *affine in
   the steering vectors* with a *constant-modulus* per-tick constraint — prefer
   methods that exploit this rather than treating the dynamics as a black box.
6. **Autonomy preferred.** A fully automatic block→solution pipeline is the goal;
   "ask the user to sketch the rough path / pick the pass-side" is an acceptable
   *fallback*, not the target.
7. **Headless self-validation exists** — propose methods that can be *trained,
   tuned, or benchmarked offline* against the exact oracle without a human or the
   live game.

---

## 10. Repo grounding (for anyone verifying the above)

- **Forward dynamics (byte-exact, collision-free):** `core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/ExactJumpModel.java`
- **Affine/constant-modulus structure & closed-form dual:** `JumpLinearModel.java`, `CostateDualSolver.java`, `ClosedFormSolve.java` (same dir)
- **Inner SOLVE (CMA-ES multistart + polish):** `SolveCore.java`, `CmaesJumpHarness.java`, `BucketAscentPolish.java`
- **Objective / constraint alphabet:** `Objective.java`, `JumpConstraint.java`, `JumpConstraintCompiler.java`, and UI-level `Constraint.java`, `TickConstraints.java`
- **Swept collision oracle (the §4 rule):** `SweptCollision.java`
- **Current DERIVE (forced-crossing-tick homotopy planner):** `BlockSolver.java`; engine glue in `AngleSolverEngine.java` (`solveFromBlocks`, footprints, per-tick heights)
- **Block selection inputs:** `BlockSelection.java`
- **Prior art / problem history & the canonical hard case (j154):** `docs/angle-solver-block-solving.md` — read this; it records what's been tried and what *failed* (don't re-propose those without addressing why they broke).
- **Headless validation harness & fixtures:** `core/src/test/java/de/legoshi/parkourcalc/anglesolver/derive/` and `core/src/test/resources/anglesolver/` (`./gradlew :core:test`).

---

## 11. What to return

For **each** of the two research questions, deliver:

1. The **cleanest standard formulation(s)** of our problem in that paradigm, with
   the exact mathematical program written out (variables, constraints, objective)
   and where our `§2`/`§4` structure plugs in.
2. **2–4 ranked candidate methods** with citations, each annotated by: assumptions
   they need, how they handle the swept/asymmetric/no-margin collision (`§4`), how
   they handle look-ahead coupling (`§4.3`), expected runtime at `n ≈ 10–40` ticks
   and `0–10` obstacles, and whether they **preserve the fast affine/dual core**
   (`§2.2`).
3. The **single most promising direction** to prototype first against the headless
   oracle, and the **smallest experiment** that would falsify it.
4. Any **constraint-alphabet extension** (`§2.4`) the method requires, justified as
   minimal.

Be adversarial about our current homotopy-planner baseline (`§5`) and about the
failed approaches in `docs/angle-solver-block-solving.md`: tell us where they sit
in the taxonomy and what the literature says we *should* have done instead.
