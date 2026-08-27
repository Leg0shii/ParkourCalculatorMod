# IMPLEMENTATION PROMPT: ARCH-2 engine fix (issue #422), start the M1 batch

Paste this into a fresh session. M0 and stage 1 are DONE; this session executes the M1 batch (M1a-M1f) and, after the user's mid-point commit, the M2 batch. The previous session prompt (`422-impl-session-prompt.md`) is superseded by this one.

## Mission

Implement the staged plan in the #422 "Engine-fix implementation plan" comment to production readiness, with the M0-measured revisions below. Scope unchanged: solve 12-75 tick sequences to the in-game (sine-bucket, byte-exact) global optimum for GIVEN key inputs; FAST = first in-game feasible solve as fast as possible; OPTIMIZE = budgeted anytime search toward the in-game global optimum. NO input search anywhere (stratfinding is #424, parked).

## Execution mode (binding user rulings, 2026-08-26, recorded in issuecomment-5430476974 + amendment)

- Batched autonomous build: M1a-M1f in one pass, then M2a+M2b in a second pass. Do not pause between stages to report.
- Orchestrate via ONE Fable subagent per task (M1a, M1b, ... M2b), each reporting back; the orchestrator keeps only plan state, gate results, and inter-task contracts so its context stays lean. The orchestrator runs and judges all gates ITSELF (never delegate run supervision of gradle/benchmark runs to agents; subagents may run their own targeted tests while implementing).
- Every numeric gate stays binding. A red gate is fixed and re-gated autonomously; escalate to the user ONLY for a design ruling the plan does not answer.
- Full battery only at M1-complete and M2-complete. Post gate results and stage outcomes to #422 as records, not approval requests.
- User touchpoints: (1) one commit when the M1 battery is posted green (pause and ask, the user handles all git), (2) commit + one in-game QA pass at M2-complete. In-game QA is deliberately deferred to the end.

## State at handoff (2026-08-26, branch feature/solver-rework-arch1-cutover)

- M0 falsification pre-check PASSED, all three legs byte-exact (maxViol exactly 0.0, no human data in any solve chain), posted as issuecomment-5430366350:
  thousand-1-dup2 6523.3086596 (human 6523.3077209); j716 -699.9501305 (human -699.9501279); j1150-hpk -2805.2947298; j003 -31.2999974 (COPT sphere path) and -31.2999980 (Java-portable chord path; OLD -31.2999 beaten, continuous bound -31.3000002).
- Stage-1 component inventory posted as issuecomment-5430559757 (keep/replace/delete verdicts, CostateDualSolver call-site dispositions, LOC budget).
- NO Java has been changed. The M0 driver and all run artifacts live under research/copt/.

## Required reading, in order

1. Issue #422: body banner first, then ALL comments. Authoritative build order = the "Engine-fix implementation plan" comment (issuecomment-5428709916), REVISED by the M0 result comment (5430366350); execution mode 5430476974; inventory 5430559757.
2. `research/copt/inner_fixed.py`: the reference implementation of every M1 mechanism. The Java port mirrors it (using DiskSocpKernel instead of COPT). Read it fully.
3. `docs/research/global-solver-2026-08/ARCH2-STEP1-SIMPLIFY.md` (all Step-1 numbers and verdicts).
4. `docs/research/global-solver-2026-08/NOTURN-HANDOFF.md` sections 4, 4b, 4c, 6 (pipeline provenance, plumbing gotchas).
5. `docs/research/solver-rework-2026-08/BENCHMARK-STEP9.md` (baselines, ship rubric, the j003 regression).
6. Code as mapped by the inventory comment: AngleSolverEngine, solver/, graph/, graph/nodes/.

## The M0-measured revisions to the plan (binding)

1. M1e is NOT a chatter-rounding DP. SUR-style tracking of the disk solution measured DEAD (thousand diverges 0.94 blocks because the game cannot throttle the 0.327-mMag jump/boost ticks; j003 sticks at 2.6e-2..5.3e-2; SLP from a tracked anchor stalls because chatter is nonlocal). The working mechanism: drive the SOLVE to full modulus via greedy chord-cut arc narrowing on the disk relaxation (per throttled tick add cos(mu)ux + sin(mu)uz >= mMag cos(delta) centered on the current direction; re-solve, re-center, shrink delta 0.7x from 60 deg; stop at slack < 1e-4 mMag), then decode directly. Same cut family the M2a B&B branches on: FAST = greedy narrowing, CERTIFIED = branching the same cuts.
2. Anchor margins alone oscillate at e-4 forever (measured, thousand rounds 1-11). The landing ladder, every rung measured necessary: gate-pattern fold from OWN replay (zeroX[t] = |velX[t]| < thr per axis, combined-norm form for modern; 1 round to fixed point on all four instances) -> per-wall signed margins at the replayed anchor -> lexicographic SLP (maximize min slack capped at 1e-4, then maximize objective; continuous per-tick dyaw trust region, shrink 0.4x on replay regression) -> free-start translation window (rigid FreeP0 shift is byte-exact to ~1e-12; per-axis interval intersection of measured violations) -> gate-banded integer bucket walk (steps of 0.0054931640625 deg, W=2, move budget 6 halved on regression; gate-band rows keep every carry velocity on its measured side of the threshold via friction couplings with pattern cuts) -> objective walk (same move space, maximize objective subject to min slack >= 2e-6, budget doubled while replay gain matches prediction within 30%).
3. Without the gate-band rows the walk flips gate events and replays 100x off prediction (measured). Without bucket-INDEXED steps (snap to bucket centers first) moves can miss sine-table boundaries entirely and the replay does not change at all (measured, j003).

## M1 stages and gates (unchanged numbers, from the plan comment)

- M1a fold + fixed-point driver in core (clamp-free disk -> replay -> extract pattern -> folded re-solve, <= 4 rounds, every round a replayable incumbent, FreeP0 supported, keep-alive violations flip that pattern entry with bounded retries). GATE: thousand >= 6523.30, bit-identical across 5 re-solves; zero corpus feasibility regressions at FAST.
- M1b wall-homotopy ladder (0.05 / 0.01 / 0.002 / 0 on compiled wall RHS, warm-started rungs, >= 2e-6 collision clearance baked in, pad/floor edges exempt). GATE: >= 2 currently-failing knife-edge hpk captures solve byte-exact.
- M1c anchor-local margins. GATE: knife-edge expects converge to maxViolation 0 in <= 3 rounds.
- M1d bucket-window leaf (window scan legacy +-50 / 26.x +-3 capped to active-wall neighborhoods, pinned walk, ulp nudge; replaces nearest-bucket snapping; must not break the sphere-snap yaw-lock save/playback path). GATE: no corpus objective give-back; knife-edge targets hit.
- M1e arc narrowing + ladder (revised per above; triggered by the disk solve's throttled-tick fraction; explicit tail gate handling). GATE: j003 >= OLD's -31.2999 at FAST and THOROUGH, inside deadline, expect green.
- M1f engine wiring + budget discipline (driver = primary recovery in the graph; CostateDualSolver demoted to bound provider; SlpSolve default seed switches from the costate recovery to the driver decode; ClosestMiss fed from driver rounds; SolverTrace events; deadlines respected at round granularity; dev flag REMOVED before ship). M1 SHIP GATE: full CorpusBench vs the STEP-9 baselines with 0 feasibility regressions and the win set holding; j003 + thousand + knife-edge expects green; FAST floor recovered (median cold <= ~80 ms, p90 <= 562 ms); slow suite + determinism guard green; zero flags.
- M1 wrap-up: author the new expects (thousand-1-dup2 at 6523.30772; fixed-schedule j1150 at -2805.2990460856336 and j154 at -1599.7237570, compiled from the PRECISE specs, all tagged SlowSolverTests); update TESTS.md; post the battery to #422; PAUSE for the user's commit. Then M2a (certified arc + gate B&B) and M2b (cleanup per the inventory: delete BoundPrunedRecovery, SeamSweepRecovery, GateMip, RelaxationRecovery, SphereDecodeSnap, ResidualRescue after migrating their listed keepers; net LOC reduction; STEP-9 rubric PASS-on-structure).

## Traps and facts (measured; do not rediscover)

- MiniCmaSolve does NOT exist on this branch despite the plan naming it; nothing to delete. The pkc.gateMip flag is GONE (GateMip runs unconditionally inside BnbNode); only pkc.solver.trace / PKC_SOLVER_TRACE gates anything.
- FacingLattice bucket indexing is LEGACY-ONLY (float INDEX_FROM_RAD); 26.x uses ExactJumpModel's sinStep262 double indexing whose table differs at bucket boundaries. The M1d leaf must be era-aware.
- Core has NO MIP solver (COPT is research-only, never on any classpath). Java walk step = TrustRegionLp on the LP relaxation + guided rounding, or bounded enumeration of the tiny move space (declared judgment call in the inventory comment).
- FAST sets NO overall deadline anywhere (deadlineNanosFor(FAST)=0; the staged race never calls setOverallDeadline). The +48 ms STEP-9 floor is unconditional per-node work (DualChainNode's 2 s ResidualRescue cap, SphereSnap 2 s) plus the explore race arm on a miss. M1f bounds rounds, not nodes.
- The modern combined gate was approximated per-axis (3e-3 box) in the M0 band rows; fine for j003 (2 gate events) but gate-dense modern captures need the exact combined form.
- The in-repo hpk specs are looser than the PRECISE inputs_gone specs (j1150-hpk solved 3.3e-3 above the human); expects use the PRECISE specs.
- NoTurnReplay decode contract: yawsDeg/forward/strafe/sprint arrays (length n) + optional px/pz; inputs are the SAMPLED values (debug[startTick+k+1], FORCE_45 captures use uniform 0.98/0/true); ALWAYS validate a new schedule extraction by replaying warmYawsDeg and requiring warmObj/warmViol bit-exact. StructureDump env: PKC_STRUCT_FILE/OUT/ZERO; run judges via direct `java -cp "$(cat core/build/test-classpath.txt)" org.junit.runner.JUnitCore de.legoshi.parkourcalc.anglesolver.<Judge>` from the repo root (Gradle swallows env).
- Gradle: full solver suite `./gradlew :core:test -PslowTests`; corpus `PKC_CORPUS=1 PKC_CORPUS_TIER=FAST ./gradlew :core:cleanTest :core:test --tests '*CorpusBench'` vs the committed TSVs in docs/research/solver-rework-2026-08/benchmark/ (compare.py); tag engine-driving test classes SlowSolverTests.
- COPT FeasTol trap: default quadratic tolerance lets a sphere solve cheat ~1e-6/tick; always FeasTol 1e-9 and check max|u^2-mMag^2| (research side only).
- Memory files exist for continuity: project_422_engine_fix_m0.md (M0 + rulings + traps), project_arch2_step1_simplify.md.

## Hard rules

- Never git commit, push, or branch; the user handles all git.
- No code comments, no em dashes in repo writing, core stays Java 8 and Minecraft-free, COPT stays research-side only (the Java path uses DiskSocpKernel).
- Byte-exact verification through ExactJumpModel replay only; self-agreement is not verification; wall-face constraints keep >= 2e-6 clearance in solves (pad/floor edges exempt).
- Do not run :runClient while Minecraft is open.
- Deadline/budget behavior is part of every gate: OPTIMIZE returns best-so-far at the deadline, never hangs.
- A permission denial is probabilistic: retry 3-4x before reporting blocked.
