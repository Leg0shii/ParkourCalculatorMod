## Future capability (ARCH-2 outer layer): certified GLOBAL-OPTIMAL no-turn stratfinder

Recording the target so ARCH-2 is built to support it. This is the **outer** input-search layer that *calls* the
certified byte-exact angle B&B of this issue as its inner oracle. Studied against real human no-turn strats
(`hpk_human/`): `j1150-2x2bm_Nix_Neo` (pure), `j716-1bm_Cobblewall...` + `j716_cold_sibling` + `j716_new`
(no-turn "with ja", the last uses sneak).

### What a no-turn is (the goal)

Every tick except T1 has **dF = 0 (constant facing θ) up to and including the last `space`**; only after the last
`space` does the facing turn. So the *entire setup* (all momentum jumps) is aimless: the player just presses keys
on time and then executes one turn. That is the "holy grail": inputs give inhuman line-up precision, and aiming
reduces to a single turn. Variants: **pure** (dF=0 even on the final jump tick, e.g. j1150) and **no-turn+ja**
(the final jump tick carries a jump-angle, dF≠0; acceptable, strictly worse). Measured structure in j1150:
constant 20.53° across T1..T39, keys move only at *edges* nothing→AS(back)→WD(fwd, sprint engaged once at T17)
→WA(turn), run ticks = the grounded ticks inside air ranges.

### The mathematics: what problem class this is

A **Mixed-Integer Optimal Control Problem (MIOCP) on a switched / hybrid system**, i.e. combinatorial *mode
scheduling* (the inputs) around a *hybrid nonlinear* plant (angle-on-a-circle + inertia dead-zone):

- **Decision variables.** (i) A combinatorial **input schedule**: per-tick key mode (each fixing that tick's
  speed `m_t` and strafe offset `baseArg_t`), a single monotone sprint-engage tick, sneak binaries (rare), and
  **structural integers** = run-tick insertions per segment (before jump 1 ∈ {0..5}; between jumps ∈ {0,1}) which
  *change the horizon length n itself* and shift all downstream inputs+constraints in lockstep. (ii) One
  continuous **setup facing θ** (dF=0 ⇒ shared by every setup tick), plus the **per-tick facings of the final
  turn only**. (iii) The **free start position** `(p0x, p0z)` within a box (the launch pad): a *linear* rigid
  translation of the whole path (the existing `FreeP0` term), so just two continuous variables, no new
  nonconvexity.
- **Dynamics (byte-exact).** friction-0.91 velocity chain; state-triggered dead-zone `w_t = ṽ_t if |ṽ_t|≥ε else 0`;
  move `m_t(cos φ_t, sin φ_t)` with φ on the 65 536-entry sine grid.
- **Constraints.** position walls at each momentum-jump block + final landing box; contiguity of key holds
  (edges, not arbitrary presses); sprint monotone; dF=0 on all setup ticks (pure) or all-but-jump-tick (ja).
- **Objective.** feasibility first ("does a no-turn exist?"), then easiness (pure ≻ ja; fewest edges/run-ticks).

Two structural facts (same spirit as this issue's inner reframe):
1. **Finite** ⇒ a B&B terminates at **gap=0 with the certified byte-exact global optimum** (COPT/BARON/SCIP would
   only certify the *continuous* optimum). See run_3other diagnosis: COPT is the continuous oracle, we certify the grid.
2. **The no-turn constraint is a massive collapse:** dF=0 removes n−1 continuous angle DOF from the setup, leaving
   a *combinatorial schedule + one shared θ + a short turn*. This is why a *global* method is realistic. The only
   nonconvexities are angle-on-circle and the gate (both local+finite); the chain is otherwise linear so
   RLT/McCormick/factorable reformulations buy nothing.

### The key simplification: keys ARE a coarse angle grid ⇒ ONE quadratic for the whole setup

The keys are not an abstract "mode": the strafe offset from W/A/S/D is **exactly** one of 8 angles
{0,45,90,135,180,225,270,315}; sprint/sneak/cardinal-vs-diagonal/ground-vs-air only scale the magnitude. The
model already stores this as `mMag(t)` (strength) + `baseArg(t)` (the coarse offset) per tick, move =
`mMag(t)·(cos(baseArg(t)+yaw(t)), sin(baseArg(t)+yaw(t)))`. For a no-turn `yaw(t)=θ` is constant, so per tick you
choose only an integer `k_t ∈ {0..7}` (baseArg = 45·k_t) and a small strength; the ONLY fine-grid unknown is θ.

Substitute `a=cosθ, b=sinθ`: `u_t = mMag_t·(cos(45k_t)·a − sin(45k_t)·b, sin(45k_t)·a + cos(45k_t)·b)` is
**linear in (a,b)** for fixed `(k_t, strength_t)`. Under a fixed gate pattern the whole position chain is linear
in the `u_t`, hence linear in (a,b) and the discrete choices; walls are linear. **The only nonconvexity left in
the entire setup is the single circle `a²+b²=1`.** dF=0 has collapsed n unit-circle constraints (one per facing)
to ONE, plus a per-tick coarse integer.

### The best global-optimal solver (recommended architecture)

A **bilevel certified branch-and-bound**, with the setup reduced to single-quadratic hardness:

- **Run-tick structure (outer, small).** Adding/removing run ticks changes n and shifts all downstream
  inputs+constraints, so it is not a fixed-size binary; enumerate it in a **prefix-pruned tree** (reuse
  `RunTicksSearch` + `RunTicksController` insert-at `jumpTick+Σshift`), each vector a fresh inner solve. The
  count is tiny (before jump1 ∈ {0..5}, between ∈ {0,1}) and infeasible prefixes die early. Equivalently the
  reachability-DP below absorbs run ticks as optional extra ground-steps per phase (variable-length action
  sequences) with no separate enumeration.
- **Setup = a Mixed-Integer program with a SINGLE nonconvex quadratic** (per run-tick structure). Vars
  `(a, b, p0x, p0z, {k_t}, {s_t})`. Continuous relaxation over the disk `a²+b²≤1` + per-tick offset/strength as
  SOS1 integers + free-start `p0` + linear walls = a **MILP**. Recover the exact facing by branching the ONE arc
  `θ∈[α,β]` with the chord cut `cos(μ)a + sin(μ)b ≥ cos(Δ)` (the same cut this issue uses, now on a single angle;
  ~8 bisections to grid resolution, snap θ once). Equivalently, since finite, a **coarse-action reachability DP**
  (≤8 actions/tick over the (pos,vel) frontier, dedup-pruned, free-start = a box of seed states) with θ a 1-D
  outer parameter. Either is globally solvable and cheap. Cast the mode/transition constraints (sprint-once
  monotone, contiguous holds, run-tick insertions, shared-θ) as a **Graph of Convex Sets** (Marcucci–Tedrake,
  SIAM J. Opt. 2024) if a tighter global relaxation than plain MILP branching is wanted.
- **Turn = the ARCH-2 certified byte-exact angle B&B of this issue**, only on the few post-`space` ticks (the one
  place the fine 65536 grid is needed). Round-and-simulate through `ExactJumpModel` certifies on the grid ⇒ gap=0.

Why not the classic MIOC toolkit (outer convexification + combinatorial-integral-approximation + sum-up
rounding; Sager/Bock/Kirches)? Those give *bounded-suboptimal* integer controls, not a certificate. For a
**global optimum**, use the single-quadratic MILP/GCS + the byte-exact turn B&B, not rounding.

**Honest caveat.** Generic mode-scheduling is NP-hard in the number of ambiguous switches. What buys the global
optimum *in practice* is the no-turn collapse + the heavy structural constraints (sprint-once, contiguous holds,
tiny run-tick ranges, and the human blueprint), which keep the GCS graph and the inner ambiguous-tick count k
small. Where a run has many near-rest ticks it degrades toward generic B&B: mitigate with the receding-horizon
window split (sensitivity decays along the 0.91 chain).

### What ARCH-2 (this issue) should provide so the outer layer is efficient ("equip preemptively")

- **First-class dF=0 / constant-facing segments:** collapse a maximal run of dF=0 ticks to ONE facing variable in
  FBBT + the node relaxation (the setup becomes 1-D). Biggest single lever for outer-search speed; reuses the
  existing `Mode.F` dF handling (gh-163) and `YawTies` folding.
- **Cheap fixed-schedule evaluation API** (call it thousands of times): forward the deterministic setup at θ,
  chain into the short turn B&B, return {feasible?, certified objective, pure-vs-ja}. Must be far cheaper than a
  general per-tick solve.
- **Structure re-parameterization = run-tick insert/shift as a first-class op** (reuse `RunTicksSearch`
  prefix-pruned per-jump tree + `RunTicksController` insert-at `jumpTick+Σshift`, copying/shifting rows and
  constraints; run tick = grounded tick with a DEFAULT-slip override, `RunTicksRows.isRunTick`). Insert BEFORE
  the tick-before-`space` so the block constraint shifts with its tick.
- **Emit the per-mode convex pieces (disk+arc+wall) as GCS-ready convex sets**, and expose the arc chord cut so
  the outer GCS relaxation and the inner B&B share one relaxation.

### References (contribution in one line)
- Marcucci et al., *Graph of Convex Sets*, SIAM J. Opt. 2024; Marcucci–Tedrake HSCC 2019: global mode-sequence
  over a horizon via a tight perspective relaxation (the outer layer).
- Sager, Bock, Kirches; Jung/Kirches: mixed-integer optimal control, outer convexification + CIA + sum-up
  rounding (the *approximate* alternative; why we don't use it for a global cert).
- Belotti et al., MINLP (Acta Numerica 2013); SCIP/BARON: spatial B&B skeleton (inner).
- Coffrin–Hijazi–Van Hentenryck, QC relaxation: trig arc/chord envelope (shared relaxation).
- Bemporad–Borrelli–Morari (Automatica 2006): PWA value-function region blow-up (why plain DP over the hybrid
  state is exponential, hence relaxation+B&B not brute DP).
- Shin–Zavala–Anitescu: exponential decay of sensitivity along the chain (licenses the window split).

### Measured: COPT proof-of-concept on j1150 (2026-08-26; full record in NOTURN-HANDOFF.md section 4b)

The single-quadratic MIQCP was built and solved cold (no recorded inputs or angles used) on the
`j1150-2x2bm_Nix_Neo_inputs_gone` structure (free start box, dF=0 on T1..T38, three momentum pads, turn
walls, X-MAX@49). Headline results:

- **It found a byte-exact FEASIBLE pure no-turn**: X@49 = -2805.2990460856336 through `ExactJumpModel` with
  maxViol 0.0 on the full compiled constraint set. The recorded human reaches X = -2805.2979844800548 but is
  NOT a pure no-turn under this spec (its dF@38 is 0.052 deg off), so the solver's point is the best known
  feasible one. Setup facing 20.2075 deg (human 20.535), sprint engage found at t17 (human t16), and the
  same back-shuffle-then-rush key shape as the human, discovered from the structure alone.
- **The linear model is faithful on momentum jumps once the inertia-gate pattern is folded.** Clamp-free the
  error is ~1e-2 (a handful of per-axis 0.005 gate events amplified ~11x by the friction chain); with the
  anchor's zeroing pattern folded via the pattern-aware `JumpLinearModel` + `velocityWalls`, every wall
  matches byte-exact to <= 1.5e-4. A solve -> replay -> re-extract-pattern loop hit its fixed point in 2
  rounds; each pattern solve reached OPTIMAL in 14-18 s.
- **Incumbent-finding is the practical bottleneck, exactly where the ARCH-2 costate warm start goes.** COPT
  found zero incumbents on the exact-circle MIQCP in 500k+ nodes; the convex disk relaxation finds them via
  heuristics and saturates |(a,b)| = 1 on its own, so relax-then-verify is the working shape.
- **Constant-offset SLP does NOT work across key flips** (the gate pattern is discontinuous in the schedule);
  gate-pattern folding or gate branching is required. This confirms the 3-way gate branch of this issue's
  design as the certification path for the outer layer too.

### Prior art to supersede: `feature/stratfinder` (studied)
Three code bodies there: (A) warm substitution `stratfinder/` (shipped "Refine recording"); (B) the cold
byte-exact engine `coldsearch/` (`ColdProblem`, `ArcSweep`, `ColdBeamSolver`, `ColdMitmSolver`, `KeyLine`),
which already implements the facing-fix reduction over the 9-combo alphabet and judges through
`ExactJumpModel`; (C) the block bridge `BlockStratFinder`/`ProblemCompiler`. Honest ceiling: it is an L2-L3
cold solver; beam + MITM explode at change-level >= 4 on the wide-arc run-ups with no global certificate.
The single-quadratic MIQCP/GCS replaces exactly that combinatorial search with a certified bound; reuse its
`KeyLine` alphabet, `ColdProblem.fromSave` structure derivation, and the byte-exact judge. The strat-corpus
grammar prior (`CorpusIndex`, `resources/strats/library.json`) can rank candidate schedules but is a label
lookup today.
