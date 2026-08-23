# Anvil / multi-jump solver-quality investigation

> **SUPERSEDED for loopmm (UPDATE 2026-07-04).** The "loopmm is an optimizer-reach failure needing #186"
> framing in this doc is obsolete. loopmm is SOLVED on the current tree: the recovery refactoring (#204
> inertia folding, #209 dual recovery, #213 margin-laddered seed) plus the pattern-branched B&B
> (`BoundPrunedRecovery`, see `angle-solver.md` 10.3 and 11.8) land `loopmm-3jump-lands` byte-exact at
> Z@71 = -279.299912 (past the -279.3 pad), and it is now a passing regression check in
> `problems/dualrecovery/`. The fix was a stronger RECOVERY, i.e. recover the feasible path the dual bound
> already promises and fold inertia into it, NOT the ILS-basin-hopping / global-search direction #186
> proposed. `EarlyCrossSweep` (an early #186 basin-diversity attempt on an experimental branch) is likewise
> superseded and was never merged. What still stands: the anvil momentum-ceiling analysis below (a yaw
> optimizer cannot land anvil at its recorded momentum; that is a route/momentum question, #178). Only the
> loopmm optimizer-gap framing is retracted.
>
> 2026-08: CMA-ES was subsequently removed entirely (PRs 371/373/375); loopmm now lands via the graph's cold/rescue B&B nodes.

Investigation + decision record (2026-06-23). Question raised: the `claude/brave-planck-lyr5ng` branch got
within ~1.3e-6 of a known-good byte-exact result on the close-range anvil fixture; can we port that into
the shipped solver to "replicate the solver quality"? The investigation reframed the goal several times and
ended somewhere quite different from where it started. This records the evidence and the corrected
understanding, including directions that were explored and abandoned, so the reasoning is not lost.

## What the branch actually was

- Its only production change was raising knob *ceilings* in `AngleSolverState`; no algorithm changed.
- Its headline result (anvil X `186.82489091`) was seeded from an external Wolfram continuous solve that is
  not committed. The in-tool cold best was the prior `~186.824818`.
- Its #1 recommended lever, the ILS ratchet, is already shipped as `IlsPolish`.

## What the spikes measured (live solver, headless `:core:test`)

Anvil fixture (3 jumps, n=38, MAX X@38; known-good = `186.82489222324986`).

| run | gap to known-good | note |
|---|---|---|
| shipped CUSTOM + ILS exhaustive (120s) | 2.34e-4 | meets 16/16, but against a deliberately relaxed landing wall (below) |
| + 180s more isolated ILS from that seed | 1.90e-4 | a crawl, not a basin hop |
| CMA on a ported smooth (continuous) model | ~3.3e-4 | finds a smooth *local* optimum, not the global |
| SLP ascent on the smooth model | no ascent | local method, stuck in CMA's basin |

Structural facts: (1) the shipped plateau is a basin problem, not a budget problem; (2) known-good is
byte-feasible but smooth-INFEASIBLE, so no continuous method reaches it; (3) the smooth problem is
nonconvex/multi-basin, so local SLP + CMA-multistart plateau ~3.3e-4 short.

## Anvil does not land, and a yaw optimizer cannot make it land at the recorded momentum

The anvil capture's landing wall is set ~0.001 *below* the true block edge on purpose, so the near-miss
trajectory renders instead of failing. The real edge is the AABB clamp at `~186.825` (`GOAL =
186.82499998807907`).

- The best byte-exact path ever (known-good `186.82489222`) is **-1.0777e-4** short of the edge.
- The research's continuous **ceiling** (`~186.8248951`) and convex **upper bound** (`~186.82489297`) both
  sit ~1.05-1.07e-4 *below* the edge. So at the momentum this capture carries, the free-flight (yaw-only)
  optimization ceiling is itself below the landing requirement: a stronger yaw optimizer, including a full
  global-QCQP, would close the optimization gap and *still not land*. (Caveat: confirm `186.82489297` is a
  *rigorous* relaxation bound, not Wolfram's best-found, before treating that as proven; a clean SOCP/SDP
  bound would settle it.)

What this does **not** say: it does not say anvil is unreachable in general. The free variable here is yaw
at a fixed momentum. **More loop-mm momentum** (a longer/optimized backward run-up) changes the achievable
distance, and whether that can reach the edge is open. The principled way to answer it is the backward
reachability of #178 (compute the entry velocity the landing requires, then whether it is achievable).
Anvil is best treated as a *near-ceiling case at its recorded momentum*, not a solved-impossible one.

## Loop mm, correctly understood (and why collision is not needed)

An early framing (from `anvil-cold-solve.md` on the brave-planck branch; that file was never merged and the branch is gone) called loop mm a "hitbox extension / collision
technique." That is wrong per the glossary (CONTEXT.md): **loop mm is repeated backward momentum**: move
backward to open up run-up space so you accelerate over more distance and carry more velocity into the
jump, looping back and forth to accumulate a little more each pass. The optimization is: go back far enough
to extend momentum, but not so far/fast that you cannot recover forward in time.

The momentum bookkeeping is fully in the free-flight model. The two things collision would have supplied
are both **known up front for a standard technique**, so no collision simulation is needed:

- **Ground/air timing** is the cycle (flat mm = 12 ticks), set per tick via the existing `slipPerTick`
  override.
- **The walkable surface** (e.g. 1 x 1.875bm) is expressed as position constraints on the on-ground ticks,
  which also keep the assumed ground state self-consistent (the optimizer cannot drift off the surface
  while a tick is marked grounded).

So a correctly set-up loop-mm solve is just the **existing free-flight solver plus the right constraints +
ground/air pattern**; the residual risk (a hand-set ground pattern that the real entity would not produce)
is caught by Apply -> `SimulatorEntity`.

## Considered and abandoned: a byte-exact colliding model

Before the two points above were clear, the plan was to build collision into the model the optimizer
evaluates (`CollidingJumpModel implements ForwardModel`, extending `SweptCollision` from detect to apply,
plus persisting world geometry at capture). It was **dropped**: collision is unnecessary here (ground/air
timing is known from the cycle, the surface is constraints), and the proof below shows the failure is the
optimizer not reaching a feasible optimum that already exists, not a missing collision model.

## The actual finding: an optimizer-reach failure on a reachable jump

A user-supplied pair (`loopmm-3jump-lands.json` / `loopmm-3jump-solver-misses.json`) settles it. Same spec
(3 jumps at 38/50/62, the 12-tick flat cycle, MAX Z@71), byte-exact:

| path | Z@71 | feasible | lands (>= -279.3) |
|---|---|---|---|
| hand-made | **-279.29973** | viol 0.0 | **yes** |
| optimizer (CMA-ES) | -279.30585 | viol 0.0 | **no**, short by 0.0058 |

A byte-exact-feasible landing **exists** (the hand path is a witness) and the shipped cascade converged
0.0061 below it in a worse basin. That is an **optimizer-reach failure**, not a model, collision, or setup
problem. (Note also: closed form is fenced to single-jump, and seeding from the window solver did *worse*
here, `-279.31294`, because its greedy per-window commit steers into a worse basin.)

## Directions (filed as issues)

- **#186** (optimizer side): strengthen the global search so it reaches feasible optima it currently misses
  on multi-jump (structure-agnostic ILS basin-hopping + ensemble; no jump-specific seeding). Validate on a
  benchmark of reachable-but-failed cases, never regress the passing suite.
- **#178** (reshape-the-problem side): backward reachability over the movement state space. Refinement from
  this discussion: propagate the landing requirement backward over the ~3 jump *boundaries* (pad -> V3 ->
  V2 -> V1), handing each jump a concrete target region (a chain of single-jump solves) plus a feasibility
  certificate (which jump is the bottleneck). The `VelocityFinder` is the primitive; it needs to also track
  *exit* velocity. This is also what would answer "can more momentum land anvil."

## Committed fixtures (for #186 / #178)

In `core/src/test/resources/captures/`, library-only (no `ProblemsTest` sidecar yet), committed so the
issue work has concrete data:

| Fixture | What it is |
|---|---|
| `loopmm-3jump-lands.json` | Reach-failure witness. Hand-made 3-jump loop-mm route that **lands** (Z@71 = -279.29973, byte-exact feasible). Proof a feasible landing exists. |
| `loopmm-3jump-solver-misses.json` | Same jump, the solver's result (CMA-ES): Z@71 = -279.30585, feasible but **0.0058 short**. |

The anvil captures (`1x1.875bm_bfly_to_anvil_close`, `j008-bfneo-to-anvil-loopmm`, `anvil-best-facings`)
are intentionally **not committed**: anvil is the near-ceiling-at-recorded-momentum case, and the numbers
that matter (known-good X = `186.82489222324986`, the ceiling, the gap) are recorded inline above.

When #186 / #178 work starts, give the loopmm pair a `solve` sidecar with a `refObjective` so they become
real regression checks (the solver must reach >= -279.29973 on `loopmm-3jump-lands`, i.e. must land).

**DONE (2026-07-04):** `loopmm-3jump-lands` now has a `dualrecovery` sidecar (`refObjective: -279.3`,
`bnbSeconds: 60`) and is a passing check; the current chain lands it at Z@71 = -279.299912. The mechanism
was the recovery refactoring + pattern-branched B&B, not the #186 optimizer-strengthening this section
anticipated. See the superseded note at the top of this file.
