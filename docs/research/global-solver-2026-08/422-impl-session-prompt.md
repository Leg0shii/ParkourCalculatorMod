# IMPLEMENTATION PROMPT: ARCH-2 engine fix (issue #422), start at M0

Paste this into a fresh session. It starts the implementation of the staged plan on issue #422.

## Mission

Implement the staged build plan in the #422 implementation-plan comment (the one titled "Engine-fix implementation plan", which begins with the binding problem statement) to production readiness. Scope is exactly that problem statement: solve 12-75 tick sequences to the in-game (sine-bucket, byte-exact) global optimum for GIVEN key inputs; the only job is finding yaw values; FAST = first in-game feasible solve as fast as possible, OPTIMIZE = budgeted anytime search toward the in-game global optimum on a direction objective. NO input search anywhere (stratfinding is issue #424, parked; do not touch it).

## Required reading, in order

1. Issue #422: the body banner FIRST (the body design is partially superseded; the banner says what died), then ALL comments. The implementation-plan comment is the authoritative build order; the body remains rationale only.
2. `docs/research/global-solver-2026-08/ARCH2-STEP1-SIMPLIFY.md` (every number the plan cites, per-hypothesis verdicts, residual risks).
3. `docs/research/global-solver-2026-08/NOTURN-HANDOFF.md` sections 4, 4b, 4c (the measured pipeline the driver ports) and section 6 (plumbing gotchas: sampled inputs, StructureDump/NoTurnReplay env contracts, COPT FeasTol trap).
4. `docs/research/solver-rework-2026-08/BENCHMARK-STEP9.md` (baselines, the ship rubric, the j003 regression).
5. Code: `core/.../anglesolver/` (AngleSolverEngine, solver/, graph/ recovery nodes), judges `core/src/test/.../anglesolver/{StructureDump,NoTurnReplay,CorpusBench}.java`.

## Definition of done

The #422 body contains the binding "Definition of done" checklist (G1 STRICTLY STRONGER than BOTH released solvers, v1.9.0 and v1.10.0: never worse than either, strictly better wherever the certificate shows headroom, strictly more captures solved, strictly faster on median/p90/total; G2 clean; G3 one path; G4 OPTIMIZE contract; G5 ship hygiene; 15 boxes with verification commands). Whenever the user asks "what is left", answer by diffing the current repo state against that checklist and listing the unchecked boxes; check a box (edit the issue body) only when its stated verification passes. 100% done = all 15 checked.

## Execution rules

- Start at M0 (the falsification pre-check): python/COPT work under `research/` only; write NO Java until the M0 gate passes. Then post the M0 result and the step-1 component inventory as #422 comments before starting M1a.
- Proceed stage by stage; every stage has a numeric gate in the plan comment; a red gate stops the stage, it is not advisory.
- Gradle judges need inline env vars, `--no-daemon`, `--rerun`; full solver suite is `./gradlew :core:test -PslowTests`; corpus runs via `PKC_CORPUS=1 PKC_CORPUS_TIER=FAST ./gradlew :core:cleanTest :core:test --tests '*CorpusBench'` against the committed STEP-9 TSVs in `docs/research/solver-rework-2026-08/benchmark/`.
- New expects to author are listed in the plan comment's regression section (thousand-1-dup2, fixed-schedule j1150 and j154); tag engine-driving test classes with SlowSolverTests.

## Hard rules

- Never git commit, push, or branch; the user handles all git.
- No code comments, no em dashes in repo writing, core stays Java 8 and Minecraft-free, COPT stays research-side only (the Java path uses DiskSocpKernel).
- Byte-exact verification goes through ExactJumpModel replay only; self-agreement is not verification; wall-face constraints keep >= 2e-6 clearance in solves.
- Do not run :runClient while Minecraft is open.
- Deadline/budget behavior is part of every gate: OPTIMIZE must return its best-so-far at the deadline, never hang.
