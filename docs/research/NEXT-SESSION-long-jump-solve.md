# NEXT SESSION — solve long multi-jump runs (the desert-hard v12 problem)

> **This is a prompt + context handoff. Read it fully, then act.**
>
> **Start by using `ultracode`** (multi-agent orchestration) and the **`deep-research`
> workflow** — this problem is genuinely hard and prior attempts hit subtle walls.
> Research *deeply and properly* before coding (see §7). Do not jump straight to a fix;
> the last session burned hours on approaches that turned out to be wrong.

---

## 0. The one-line goal

Make the angle solver **find a feasible solution for a long multi-jump run** (the
354-tick "desert hard v12" file) and report it — today it falsely says "no solution."

**Target: solve that file in `<10 ms`.** Break the problem into two phases, in order:
1. **Solve at all** — produce *any* full run that satisfies all constraints (byte-exact).
2. **Then reduce/maintain speed** — only once (1) works, make it keep the run's momentum.

Do **not** try to do both at once. "Solve at all" first.

---

## 1. 🚨 HARD CONSTRAINT — DO NOT REGRESS THE FAST PATH 🚨

**Single-jump and normal solves MUST stay below `0.1 ms`.** This is non-negotiable.

The current fast path is the closed-form Lagrangian-dual solver
(`ClosedFormSolve` → `CostateDualSolver`), ~20–70 µs per single jump. Any long-run
machinery you add **must be a post-failure fallback**: it may only run *after* the
closed form (and its alternate-direction retry) has already failed, and only when the
span actually contains multiple jumps. Single jumps return on the fast path and never
touch your new code. **Verify this**: `ClosedFormSolveTest` prints per-solve µs — keep
it green and fast.

---

## 1.5 🔬 WORK AUTONOMOUSLY · BENCHMARK EVERYTHING · REPORT BACK (REQUIRED)

You are expected to run **autonomously**: try → benchmark → diagnose → repeat, headlessly,
without waiting for the user between attempts. Fact-check every attempt yourself (the
fixtures are committed; see §3). Only come back to the user at real milestones or with the
final report — but **always** with numbers.

**Benchmark every attempt across the full jump-type matrix, with solve TIME as the primary
metric.** Never claim something works (or is fast) without a benchmark table. There is
already a harness to extend: `core/src/test/java/.../SolveBenchmark.java` (times
`engine.solve()` median/min/max ms over fixtures) and `ClosedFormSolveTest` (times
`ClosedFormSolve.optimize` in a tight loop for the **microsecond** fast-path number).

The fixture matrix (all committed in `core/src/test/resources/anglesolver/`):
| class | fixtures | what it guards |
|---|---|---|
| single / short jump | `j121`, `j154`, `j1097`, `jc-xt43`, `long-seq-with-ground` | the **<0.1 ms** fast path — must NOT regress |
| medium multi-jump | `deserthard-v7` (189t), `deserthard-vfail` (176t) | solvable today; keep solving, watch time |
| long multi-jump | `deserthard-v12` (354t) | THE target: must solve, **<10 ms** |

For each fixture measure and log, **for every attempt**: (a) **closed-form solve time in µs**
(tight-loop, the <0.1 ms metric — engine `solve()` wall-clock includes thread-spawn overhead
so do NOT use it for the µs number), (b) end-to-end solve compute time, (c) success +
met/total, (d) which path was taken (closed form / segmented / CMA-ES). Keep a running
before/after table so regressions are obvious.

**Report findings back to the user** as a structured summary: the benchmark table (per
fixture: ticks, jumps, constraints, solve time, pass/fail, path), what changed, what the
data shows, any regression vs the `<0.1 ms` and `<10 ms` targets, and the conclusion. Data
first, prose second.

## 2. The codebase (where to look)

Repo: `ParkourCalculatorMod`, branch **`features/angle-optimizer`**. Core module only
matters here (`core/`).

Solver pipeline (`core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/`):
- `AngleSolverEngine.java` — orchestration. `solve()` snapshots a `Job`; `runJob()` (on a
  worker thread) runs: **closed form → (alternate directions) → CMA-ES**, then
  `buildResult()`. This is where a segmentation fallback would slot in.
- `solver/ClosedFormSolve.java` + `solver/CostateDualSolver.java` — the **fast path**
  (closed-form dual, microseconds). `MAX_ITER=100`. Returns `null` when it can't certify
  byte-exact feasibility.
- `solver/SolveCore.java` + `solver/CmaesJumpHarness.java` — CMA-ES multistart fallback.
  **Slow** and **useless at high dimension** (see §5). `SolveCore.optimize(...,warmStart)`
  exists (7-arg) for warm-starting.
- `solver/JumpLinearModel.java` — the affine `u↦p` model the dual uses (omits the
  momentum-cancellation clamp deliberately).
- `solver/ExactJumpModel.java` — **byte-exact** MC forward. `forward()` already computes
  per-tick `velX/velY/velZ` internally but `ForwardPath` only exposes positions (you can
  expose velocity trivially if needed — last session did).
- `solver/JumpConstraint.java` / `JumpConstraintCompiler.java` — constraint representation;
  `compile(spec).maxViolation(gameFacings, path)` gives the byte-exact violation (blocks).
  Velocity (ΔX/ΔZ) constraints are encoded as `new JumpConstraint(Mode.X, t1, t2,
  Op.MINUS, Cmp.GE/LE, value, name)` = `pos[t1]-pos[t2]`.
- `solver/JumpPhysicsInputs.java` — the per-tick physics snapshot (seed `startPos/
  initialVelocity/startYaw`, masks). `toGameFacings(double[] absYaws)` converts absolute
  facings to the game's float-accumulated facings.
- `AngleSolverEngine.buildPhys(startTick, numTicks)` builds the masks + seed (from
  `boxes.getState(startTick)`); `addMapped(...)` maps a UI constraint to solver
  `JumpConstraint`s (note the segTick remap).

Existing research (READ THESE): `docs/research/block-constraint-derivation-and-collision-integration.md`
and `docs/research/findings-block-constraints-and-collision-integration.md`. The findings
doc literally predicts the coupling wall the segmentation hit.

---

## 3. The test fixtures (committed — you have them)

`core/src/test/resources/anglesolver/`:
- `deserthard-v12.json` — **THE hard file.** 354-tick span (`startTick 0, landingTick
  354`), **30 jumps**, **81 X/Z constraints**, Solve-For **Z/MAX**, FAST. Currently
  unsolvable by the engine.
- `deserthard-v7.json` (189 ticks), `deserthard-vfail.json` (176 ticks) — solvable;
  regression fixtures for the already-landed Solve-For fix (`DesertHardSolveForTest`).

**These are *debug* saves**: they contain a `debug[]` array of per-tick recorded state
(`pos, vel, yaw, onGround, ...`). Load them like `DesertHardSolveForTest` /
`ClosedFormSolveTest` do (build `BoxController` from `file.debug`, build `AngleSolverEngine`).

### ⚠️ Critical distinction the user stressed
**Normal solves do NOT have a path given** — the solver finds the trajectory from
scratch; you don't know what it looks like. These debug files **do** carry the recorded
path, *for debugging only*. So: you may use the recorded path as **ground truth to
fact-check** attempts, and a "reproduce the recorded run" trick can help you reach
"solve at all" for v12 — but the **general solution must still solve each jump from
scratch** (no recorded path available in normal use). Treat the recorded path as a
debugging oracle, not as something the production solver can assume.

---

## 4. PROVEN FACTS (don't re-derive these)

- **The v12 constraint set is FEASIBLE.** The recorded run satisfies **all 81
  constraints, 0 violations, 0 intra-tick contradictions.** The recorded path is a live
  witness. There is **no bad/infeasible constraint.** (Verified by evaluating the debug
  positions against the constraints.)
- **v12 is collision-free**: 0 wall-collision ticks, 0 soft-collision ticks. So the
  failure is **not** a collision-clamping issue.
- **The closed form cannot solve v12 in any direction.** The dual hits its 100-iter cap
  without converging and lands **14–88 blocks** off. Raising the cap does NOT help:
  100→1000→8000 iters gave 14.4→17.7→15.5 blocks (146 s for 8000), never converging.
  **More runtime is useless here.** CMA-ES at 354 dimensions is hopeless. NOTE: this test
  (`ClosedFormSolve.optimize` on the v12 spec) solves for facings **from scratch** and
  takes **no facings as input**, so it is *not* affected by the facing-delta bug (§6.2) —
  it is a genuinely separate failure from the reproduction drift (§6.3).
- **⚠️ The ROOT CAUSE of that dual failure was NOT isolated — DIAGNOSE THIS FIRST.** The
  dual's own optimization did not converge (residual never met, cap hit), but *why* is
  open, and there are two very different candidates:
  - **(a) Dual-algorithm scalability** — the local Newton/projected-gradient optimizer
    struggles with the degenerate landscape of 81 walls. → a better dual (matrix-free
    Newton-CG, better flat-direction handling) might converge. *Fixable.*
  - **(b) Affine-model drift** — the dual optimizes `JumpLinearModel`, which omits the
    momentum-cancellation clamp and is only an affine approximation; over 354 ticks / 30
    jumps it diverges from byte-exact, so even a perfectly converged dual would recover a
    byte-exact-bad path. *Same class as the facing drift — model fidelity over a long
    horizon — fundamental to the fast approach.*
  **To isolate:** check the recovered path's violation in the AFFINE model vs the
  byte-exact model. If the dual converges in the affine model but the byte-exact violation
  is large → cause (b). If the dual never converges even in the affine model → cause (a).
  This determines whether a scalable dual can ever solve long runs in one go.
- A single jump is ~8–14 air ticks; v12's 30 jumps each sit squarely in the fast
  closed-form regime. **Per-jump, the solver works.**

---

## 5. THE APPROACH THAT GOT FAR — auto-segmentation (and how to finish it)

Split the run at ground contacts (launch = last grounded tick before air) into ~11-tick
per-jump segments; solve each in the fast regime; chain; verify the full path byte-exact.
Last session got **all 29 lead-in jumps to reproduce exactly (viol 0)** and the final
jump to solve — but the **full concatenated path verified at only 62/81** (see §6).

Skeleton that worked up to the verify step:
- `segmentBoundaries`: ticks `t` in `(start,landing)` with `boxes.getState(t).onGround &&
  !boxes.getState(t+1).onGround`, plus both endpoints; merge sub-2-tick pieces.
- For each segment `[a,c]`: `phys = buildPhys(a, c-a).inputs` (this already seeds from the
  **exact recorded state** `boxes.getState(a)` — no manual seed override needed, no
  velocity chaining needed, because each jump re-seeds from the recorded state).
- Constraints: `addMapped` for `uiConstraints` with `absTick in [a,c]` remapped to
  `absTick-a`.
- Lead-in jumps: **reproduce** — use the run's own facings (see §6 for the facing bug!);
  if they satisfy the segment's constraints (`maxViolation <= 0`), keep them verbatim.
- The jump containing the objective tick: actually solve it (`closed form`, warm-started).
- Concatenate; in `runJob`, forward the full facings and `buildResult` — only accept if
  `isSuccess()`.

---

## 5.1 ARCHITECTURE QUESTION — one-go vs segmentation (investigate this!)

A natural question: *if we make facing bit-exact, can we solve the whole run in one go and
skip segmentation?* **Answer from last session's analysis: no — bit-exact facing alone is
not sufficient.** There are TWO independent blockers and facing-exactness only touches one:

1. **Solver scalability (the real wall).** The closed-form dual does not *converge* at 354
   ticks (proven — §4). Bit-exact facing does nothing for this. One-go would need a
   fundamentally more scalable solver.
2. **Model fidelity over long runs.** The dual optimizes an *affine* approximation
   (`JumpLinearModel`, no momentum clamp). It is faithful over ~11 ticks (margin-ladder
   reconciles it) but diverges over 354. And the dual *requires* the affine structure — the
   real physics (clamp, sine quantization) is not affine. So **byte-exact + one-go + fast
   are in fundamental tension.**

Therefore **segmentation is very likely still needed** — it keeps each piece in the regime
where BOTH the dual converges AND the approximate model stays faithful. "Enforce no drift in
one go" essentially reduces to either (a) running the byte-exact model *inside* the
optimization (kills the fast affine structure) or (b) **re-anchoring the exact state
periodically — which IS segmentation.**

**Cleaner segmentation that avoids the §6.3 drift entirely (investigate this first):** do
NOT reproduce recorded facings. Instead **solve each jump from scratch in `ExactJumpModel`
and chain the EXACT `ExactJumpModel` output state (position + velocity) into the next
jump's seed.** Everything then lives in one self-consistent model — no external reference to
drift against — and it generalizes to *normal* solves (no recorded path needed; the debug
path is only a fact-checking oracle). Chaining the exact state needs velocity out of the
forward model (`ExactJumpModel.forward` already computes `velX/velY/velZ`; just expose them
on `ForwardPath`). The greedy-chain risk (jump N's exit makes N+1 infeasible) is the
coupling problem — handle it with warm starts and, if needed, **multiple shooting** (solve
the segments jointly with continuity/defect constraints driven to zero) rather than a
strict left-to-right greedy pass.

### Two-phase architecture (recommended shape)

- **Phase 1 — "solve at all":** segmentation to produce *any* feasible full path. Use
  **multiple shooting** (segments solved jointly with continuity/defect constraints driven
  to zero) rather than strict left-to-right greedy, so an early choice that would doom a
  later jump is corrected globally.
- **Phase 2 — "optimize / maintain speed":** **warm-start a global *local* ascent on the
  BYTE-EXACT model** from that feasible full path. `BucketAscentPolish` already is a
  strict-feasible compass ascent (climbs the objective, never crosses a constraint). From a
  feasible full path it only *improves* — it does not have to SEARCH — so it is tractable
  even at 354 dims (coordinate-ascent, hundreds of cheap forwards per pass) and **does not
  drift** (it runs on the exact model, never the affine approximation). Do NOT use CMA-ES
  for this — 354 dims is hopeless regardless of warm start.

### Local-optima caveat (the user's concern — important)

Greedy per-segment solving can get stuck. Two kinds, with different fixes:
- **Continuous** (a segment lands slightly off, or optimizes its own piece at the next
  jump's expense): the Phase-2 global polish **fixes these** — it optimizes *across*
  segment boundaries.
- **Discrete / homotopy** (a segment commits to the wrong *side* of an obstacle): a local
  polish **cannot escape these** — different basin. Phase 1 must get the which-side choice
  right: multiple shooting, trying alternative homotopy options per jump, or the
  GCS/corridor enumeration in the research docs. **If segmentation locks in a globally-wrong
  homotopy, neither phase recovers it** — so this is the thing to guard.

### Route #3 worth real research — optimize the BYTE-EXACT model directly

The reason the fast solver drifts is that "fast" currently means "uses the affine
approximation," and approximations drift over long horizons. The byte-exact float chain is
deterministic and mostly differentiable (the non-smooth bits are the sine-table steps and
the momentum clamp). A **gradient / Gauss-Newton solver on the exact model** (or a smooth
surrogate hugging it) would be *fast-ish AND drift-free by construction* — no affine model
to diverge. The Phase-2 polish above is a zeroth-order version of this; a real first-order
method could be the "fast and never-drifts" solver. Whether the non-smoothness makes this
tractable is an open research question — investigate it.

**Things to research/decide (use `ultracode` + `deep-research`):**
- One-go path: is a scalable dual (matrix-free Newton-CG / better conditioning) or a
  direct-collocation / multiple-shooting formulation viable at 354 ticks while keeping the
  `<0.1 ms` single-jump fast path? What's the model-fidelity ceiling of the affine model
  over long runs?
- Segmentation path: solve-from-scratch + chain-exact-states (above) vs reproduce-facings;
  greedy chain vs multiple-shooting for the coupling; how to pick segment boundaries.
- Is bit-exact facing reproduction even needed if you chain exact `ExactJumpModel` states
  and never reproduce recorded facings? (Likely it drops to a lower-priority concern — it
  only governs whether `ExactJumpModel` matches the *live game*, which the single-jump path
  already assumes.) Confirm this.

## 6. ⚠️ PITFALLS (every one of these cost hours — do not repeat)

1. **DO NOT force/pin velocity at the seams.** Matching seam velocity to the reference
   (±tol) is WRONG. It created a **precision floor** (~2e-3 unhittable by the byte-exact
   solve) *and* **drift** (a 1e-2 band accumulated to 0.34 blocks by jump 5). Just chain
   the **exact recorded state** (each jump re-seeds from `boxes.getState(a)`); never
   constrain velocity. The user was emphatic and correct about this.

2. **Row yaws are per-tick DELTAS, not absolute facings.** `InputRow.getYaw()` returns a
   delta. `boxes.getState(t).yaw` is the **absolute** recorded facing. The absolute facing
   the *move at tick t* uses is **`boxes.getState(t+1).yaw`** (the **+1 offset matters**).
   Feeding row deltas as absolute facings → **100+ block** divergence. Use absolute
   recorded facings with the +1 offset.

3. **The facing round-trip is NOT bit-exact — THIS IS THE CURRENT BLOCKER.** Even with
   absolute facings + the +1 offset, forwarding the run's own facings through
   `ExactJumpModel` (via `toGameFacings`) drifts **~0.56 blocks over 354 ticks** versus
   the real recorded run. Per-segment (re-seeded from the exact recorded state each ~11
   ticks) the reproduction is exact (viol 0); but the **full continuous path accumulates
   the drift** → full verify = **62/81** (the razor-tight keep-outs trip on the ~0.5-block
   accumulation). Offset sweep, absolute facings: `+1 → 0.56`, `0 → 18.4`, `-1 → 18.9`.
   So +1 is right but not bit-exact. **The drift lives in the absolute-facing ↔ delta ↔
   `toGameFacings` float-accumulation round-trip**, NOT in the segmentation logic and NOT
   in collisions. Making the solver's facing representation reproduce the live sim
   bit-for-bit over a long run is the key unsolved piece.

4. **Verify-spec objective tick must be segment-local.** When you build a `JumpSpec` just
   to call `maxViolation`, give it an objective tick within the segment length, not the
   global landing tick (out of range otherwise). `maxViolation` ignores the objective but
   `compile` may validate the tick.

---

## 7. RESEARCH FIRST (use `ultracode` + `deep-research`)

**Do not skip the research.** This problem already defeated a session of straight-to-code
attempts. When you hit something you do not fully understand (the dual non-convergence root
cause, the facing round-trip, the model drift, the coupling), run a real `deep-research`
pass before guessing — research properly rather than tune-and-pray.

**Stay open to other routes.** The three routes in this doc (segmentation, scalable dual,
direct byte-exact optimization) are *starting points, not a closed set.* Actively look for
approaches not listed here — different problem framings (e.g. graph-of-convex-sets / corridor
planning from the existing research docs, reachability pruning, learned/amortized warm
starts, contact-implicit formulations), different solvers, different decompositions. If the
data points somewhere I did not anticipate, follow it. The only fixed constraints are the
two hard requirements (§1 `<0.1 ms` fast path, §10 `<10 ms` for v12) and "solve at all before
maintain speed."

Before coding, run a proper deep-research pass on:
1. **Bit-exact Minecraft movement/facing reproduction** — how the game accumulates yaw
   (float deltas), how `toGameFacings` should map an absolute-facing plan to the exact
   per-tick game facing so a long run reproduces to the ULP. This is pitfall #3, the
   blocker. (Search: MCP/mcpk movement, yaw float accumulation, `MathHelper` sin table,
   sub-tick rotation.)
2. **Multiple-shooting / direct-collocation trajectory optimization** — the principled
   framing for "solve a long horizon by segments with continuity defects driven to zero."
   The greedy left-to-right chain is fragile; multiple shooting with defect constraints is
   the textbook fix and may sidestep the drift entirely.
3. **How to verify/optimize long concatenated trajectories without accumulating model
   error** — re-seeding, defect constraints, closing the loop on the exact model.

Then synthesize a plan that (a) keeps the `<0.1 ms` fast path untouched, (b) solves v12
`<10 ms`, (c) does "solve at all" before "maintain speed."

---

## 8. FAST ITERATION TIPS

- **Consider disabling CMA-ES while iterating** (or short-circuiting it) so failed attempts
  return in ms instead of ~100 s — for v12 the CMA-ES fallback wastes enormous time and
  never helps at 354 dims. Test the closed-form / segmentation path in isolation. Only
  keep CMA-ES if a real case needs it.
- **Reproduce headlessly**: load `deserthard-v12.json` (now a committed resource via
  `getResourceAsStream("/anglesolver/deserthard-v12.json")`), build the engine like
  `DesertHardSolveForTest`, solve Z/MAX, check `state.getResult().isSuccess()`. Add a
  per-segment debug trace (last session used a `DEBUG_SEG` flag).
- **Fact-check against the recorded path**: forward candidate facings through
  `ExactJumpModel` and compare `posX/posZ` to `boxes.getState(t).position` to measure drift
  directly (that's how pitfall #3 was found).
- **Build/run gotchas**: `gradle.properties` pins a Windows JDK — override with
  `-Dorg.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64`. The loader modules need an
  offline-unreachable `fabric-loom` plugin, so for `:core` tests **trim `settings.gradle`
  to only `include 'core'`** (back it up, restore after). Java 8, JUnit 4.
  Run: `./gradlew :core:test --tests '...' -Dorg.gradle.java.home=...`.

---

## 9. What's already DONE and committed (don't redo)

On `features/angle-optimizer`:
- **Solve-For feasibility fix** (`f626900` + `25651fc`): every Solve-For direction now
  finds a solution on a *solvable* jump (CMA-ES feasibility-only fallback + engine
  alternate-direction closed-form retry). Verified on v7/vfail (`DesertHardSolveForTest`).
  This is the objective-independence fix; **v12 is a different, harder problem** (it's the
  *scale/long-horizon* problem, not objective dependence).
- `FreeSpaceDecomposition` + the two research docs (from the merge).

## 10. Definition of done

1. `deserthard-v12.json` solves: `state.getResult().isSuccess()` true, all 81 constraints
   met byte-exact, in **`<10 ms`**.
2. Single-jump / normal solves still **`<0.1 ms`** (`ClosedFormSolveTest` green & fast).
3. v7/vfail regression tests still green.
4. Phase 2 (maintain speed) only after phase 1 is solid.
