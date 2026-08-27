# RESEARCH PROMPT: ARCH-2 Step 1, "can the problem be simplified further?"

Paste this into a fresh session. It is step 1 of the 3-step plan recorded at the bottom of
https://github.com/Leg0shii/ParkourCalculatorMod/issues/422 (step 2 = component inventory, step 3 =
mandatory cleanup/wiring pass). This step is RESEARCH ONLY: measure and conclude, do not implement.

## Mission

Before building the ARCH-2 certified byte-exact solver (general per-tick facing, NOT just dF=0), run a
VERY thorough investigation of whether the problem can be simplified further than the converged design in
issue #422. The continuous side (SOCP disk kernel + SLP + recovery) is considered solved; the weakest link
is the translation into the game (sine-grid buckets, inertia gates, float chains, collision clamps).
Every claim must be grounded in this codebase and measured on real captures. The output decides what
ARCH-2 actually needs to branch on, and what it can skip.

## Required reading, in order

1. Issue #422 (the design) and BOTH no-turn comments on it (PoC + session-2 findings).
2. `docs/research/global-solver-2026-08/NOTURN-HANDOFF.md` (sections 4, 4b, 4c: the working pipeline,
   every measured failure mode, the rulings).
3. `docs/research/global-solver-2026-08/DESIGN.md` and `docs/research/solver-rework-2026-08/BENCHMARK-STEP9.md`.
4. Code: `core/.../anglesolver/` (AngleSolverEngine, ClosedFormSolve, SlpSolve, LongRunSolver, GateMip,
   ExactJumpModel, McSineTable, CostateDualSolver and the dual-newton audit doc, BoundPrunedRecovery,
   SphereDecodeSnap, TrendFilterSmooth), `velocity/VelocityFinder`, `runticks/`.
5. `research/copt/` (noturn_miqcp.py and friends: the flags are documented in the session-2 comment).
6. Prior art: https://github.com/Curryocity/Stratfinder (enumeration-first in exact game arithmetic,
   velocity-window targets, strat-family grammar, easiness-aware depth refunds). Compare, do not copy.
7. Memory/docs pointers that mark DEAD ends (do not re-derive them): constant-offset SLP margins across
   key flips, taut-string alone, capture-seed and Farkas-ray recovery, CMA-ES, spline camera analogies.

## Hypotheses to evaluate (each gets a verdict: SIMPLIFIES / DEAD / NEEDS-BUILD-TO-MEASURE, with evidence)

H1. Gate near-determinism. Claim from #422: chain FBBT fixes almost every inertia gate before branching.
    Measure on the corpus (short, long, and nix-tail jumps, legacy 0.005 and modern 9e-6): what fraction
    of gates is fixed by forward+backward interval propagation, and what is the ambiguous count k per jump?
    If k stays <= ~6 even on j003 (n=176), gate branching is a non-problem and ARCH-2 shrinks massively.

H2. Bucket-window reduction. Session-2 measured that knife-edge solutions live within 1-2 sine buckets of
    the continuous optimum. Is that general? Measure the bucket distance between SOCP/SLP continuous optima
    and byte-exact optima across the corpus. If bounded by a small window W, the per-tick angle domain is
    ~2W+1 exact-float directions instead of 65536, and the whole angle side becomes a small MILP layer.

H3. Costate collapse. The objective is terminal-linear, so the tail couples through one adjoint vector of
    the 0.91 friction chain. Determine whether the per-tick optimal facing given the costate is a pointwise
    Hamiltonian argmax on the sine grid (bang-bang in buckets), which would reduce the continuous search to
    boundary conditions only. Explain the measured CostateDualSolver plateau (pgres ~2.4, see the dual
    non-convergence handoff): structural obstruction or fixable? This decides if the warm start is O(n) as
    claimed.

H4. Velocity-window (MITM) decomposition. Can multi-jump runs be split at ground touches into segments with
    2D entry-velocity-window interfaces (VelocityFinder fields as the oracle, Stratfinder zSolver-style)?
    Quantify the optimality loss of windowing (bound it against known optima on 2-3 multi-jump captures) and
    the interface discretization cost. This is the main scaling lever for long runs.

H5. Structure grammar. Does the hpk corpus factor through a small strat-family grammar (Stratfinder
    families, the strat-corpus grammar prior)? Estimate coverage: what fraction of the 1275 human jumps
    matches a family template? If high, the outer structure search proposes from dozens of templates, and
    the pool driver (session-2 open problem) becomes tractable.

H6. Easiness-first restriction. Is it sound to search the easy-input class first (edge caps, minimum key
    dwell, settable angles, sprint-latch legality, low turn cost via the existing smoothness stack) and
    escalate only on infeasibility? Measure on the corpus how often the easy class contains a feasible
    solve. This ties to the binding ruling: easy inputs beat wall slack.

H7. One certified translation layer. Can the entire byte-exactness burden be concentrated into a single
    interface: per-wall rigorous error bounds (interval propagation over the float chain, collision-clamp
    clearance included) plus bucket-exact direction constants, so every layer above works in translated
    space and never touches floats? Sketch the contract and verify the bound magnitudes are usable
    (must be << the e-5 corridor widths measured in session 2).

H8. Symmetry and legality dedup. 45-degree combo relabeling symmetry, |gf| <= 720 wrap legality, sprint
    monotonicity vs latch: how much of the discrete space do these quotient away?

## Method rules

- Ground every verdict in either code reading (cite file and line) or a measurement (name the capture,
  the command, the number). Gradle judges need `--no-daemon` for env vars and `--rerun` to defeat caching;
  full solver suite is `:core:test -PslowTests`.
- Seedless honesty: any claim about discovery must not warm-start from recorded answers. MIP-starting from
  the machine's own chain is legitimate.
- Prefer measuring with the existing harnesses (`StructureDump`, `NoTurnReplay`, `ProblemsTest` captures,
  `research/copt` pipeline) over writing new ones.
- No implementation work beyond throwaway measurement scripts under `research/`.

## Harness (how to run this)

Run in a Fable session as the delegator, orchestrating with the Workflow tool. The hardest thinking is
Fable; breadth and code/corpus work is Opus 4.8 with the 1M context window. MODEL RULE (hard): agent
model = claude-opus-4-8 with 1M context, NEVER Opus 5 or any other Opus version; if opus-4.8-1m is not
available in the harness, stop and ask the user instead of substituting. Three phases:

Phase 1, parallel fan-out:
- 5 WEB research agents (Opus 4.8 1M), split by FIELD not by hypothesis; nothing Minecraft, only the problem
  structure: (1) MIOCP / switched-system mode scheduling (combinatorial integral approximation, bang-bang
  structure); (2) global MINLP with lookup-table / piecewise-trig nonlinearity (SOS2/MILP grid encodings,
  FBBT/OBBT bound tightening); (3) certified optimization over floating point (interval methods, SMT-FP,
  rigorous global solvers); (4) homotopy/continuation in MIP, solution pools, no-good-cut diversity, primal
  heuristics for measure-thin feasible sets (feasibility pump, RINS); (5) contact-implicit trajectory
  optimization in robotics (the inertia gate is a dead-zone/complementarity constraint; locomotion
  literature is the closest match). Each returns: named techniques, what they would replace or simplify in
  the #422 design, and citations.
- 2 CLUSTER agents (Opus 4.8 1M): cluster A = H1, H2, H7, H8 (byte-exact micro-structure; shares the
  ExactJumpModel/StructureDump/NoTurnReplay toolset; H7 consumes H1+H2 numbers). Cluster B = H4, H5, H6
  (decomposition and outer structure; corpus statistics). They read code, design the measurement commands,
  and interpret outputs.
- 1 Fable agent for H3 alone (costate collapse: the adjoint math, the sine-grid Hamiltonian argmax
  question, and the root cause of the measured CostateDualSolver plateau).

Execution rule (binding): heavyweight sweeps (gradle judges, COPT batches) run in the DELEGATOR's
background shell; cluster agents design and interpret, they never babysit runs.

Phase 2, consolidation (Fable, 2 agents): a CONNECTIONS finder that maps web findings onto our components
by name (gate = dead-zone complementarity; schedule/angle split = CIA; bucket windows = grid MILP layer),
and a SIMPLIFICATION adversary whose only job is to argue what the #422 design can DROP given all evidence.

Phase 3, synthesis: the Fable main loop itself (not an agent) writes the verdict document and the #422
comment with full context.

## Deliverable

One verdict document `docs/research/global-solver-2026-08/ARCH2-STEP1-SIMPLIFY.md`: per-hypothesis verdicts
with evidence, then a final ranked reduction stack ("ARCH-2 must branch on X and Y; Z is fixed by
propagation; W is handled by the translation layer; the outer search proposes from the grammar"), and an
explicit list of what the #422 design can DROP. Post a summary comment on issue #422. Do not commit or
push; the user handles git.
