# Context Pack: Angle-Solver Re-Founding Mission (2026-08)

READ THIS FIRST, WHOLE, before doing anything. It is the shared brief for every agent in this
campaign. It distills the domain, the exact math, the current architecture with file anchors, the
COPT research oracle, the corpus, and the rigor rules. When this pack is wrong or thin on something
all later agents need, tell the orchestrator so it gets updated here.

No em dashes anywhere in what you write (use commas, semicolons, colons, or sentence breaks). No code
comments in any code you write (no javadocs, no inline notes; pick clearer names). Never git commit,
push, branch, or stage. Leave edits in the working tree.

---

## 0. The mission in one paragraph

Investigate honestly and with measurements whether the project's ANGLE SOLVER, today a tall stack of
special-purpose stages, can collapse into as few, as general, as analytical mechanisms as possible,
ideally closed-form or convex-global where the problem admits it. Simpler is a win ONLY if it is
MEASURED to be at least as robust and at least as fast on the corpus. An honest, measured
cannot-collapse conclusion for a problem class is a valid and valuable outcome. The single
highest-weight intellectual task is to name the exact optimization class this problem occupies with
textbook precision, PROVE it from the model, and actively hunt for the SIMPLEST equivalent reduction
(do not assume the current constant-modulus / circle-vs-disk framing is final). The single
highest-weight discipline: nothing is claimed without a measurement behind it and the exact method
used; every failed approach ends in a precise, measured root cause.

---

## 1. Domain primer (this vocabulary is NOT in an LLM's training data; do not assume)

ParkourCalculatorMod is a Minecraft (MC) parkour TAS (tool-assisted-speedrun) tool. Its headline
feature is the ANGLE SOLVER: given a start block (or a start box to choose a start position within)
plus target constraints, compute the exact per-tick YAW (facing) sequence that executes a jump or
multi-jump route BYTE-EXACTLY on real MC horizontal-movement physics.

- Tick: one MC sim step, 1/20 s. Inputs are per-tick. The solver reasons tick by tick.
- Facing / yaw: the player's horizontal look angle (degrees). Yaw is the raw unbounded float; facing
  is yaw wrapped to [-180,180]. Per tick, the only free decision variable is the yaw (the movement
  keys, jump, sprint, sneak, and the ground/air state of each tick are FIXED and given).
- Direction vs facing: direction D_t = where the player MOVES (inputs + rotation); facing F_t = where
  they LOOK (rotation only). Movement thrust is applied along a rotated input vector.
- Byte-exact: reproduces MC movement bit-for-bit, no approximation. Required because target jump
  margins go down to ~1e-7 blocks and below (a canonical hard jump clears by ~6.6e-7 b; j318's
  validity corridor is ~1e-7 b; the tool hugs caps to ~1e-10). NEVER call a sub-milliblock reach gain
  "negligible": the only defensible noise floor is the accumulated sine-residual certify floor
  (~1e-4 b); above it, gains count. Distances are ALWAYS in blocks (1 block = 16 px), never mm/px/m.
- Vertical motion is independent of yaw (gravity + jump impulse), so y(t) is known in advance; only
  the horizontal (X,Z) trajectory depends on the decision variables.

## 2. THE MATH (internalize exactly; carry it verbatim into your reasoning)

Per tick t the game adds to velocity a contribution of FIXED magnitude m_t (the "constant modulus")
rotated to whatever direction the yaw picks, then propagates it through a friction chain. Writing the
per-tick input as u_t = (addX_t, addZ_t) with |u_t| = m_t:

  In code (JumpLinearModel.zeroingPattern): phi_t = baseArg_t + yaw_t(rad);
    addX_t = m_t * cos(phi_t),  addZ_t = m_t * sin(phi_t).
  So u_t traces a CIRCLE of radius m_t as yaw varies; direction free, modulus PINNED.

Position at any tick k is AFFINE in the u_s:

  pos_k[axis] = constPos(k,axis) + sum_{s<k} coefAxis(axis,s,k) * u_s[axis]
  coef(s,k) = (sPre[k] - sPre[s]) / fPre[s]      (0 for s>=k)
  fPre = prefix product of per-tick friction f4;  sPre = prefix sum of fPre.
  constPos folds start position + decaying initial velocity.

  (Note: an X wall reads only addX_s = m_s cos phi_s across ticks; a Z wall reads only
   addZ_s = m_s sin phi_s. The objective likewise reads one axis at one tick.)

- Objective: MAX or MIN of ONE position axis at ONE tick. A LINEAR functional of the stacked {u_t}.
- Constraints: LINEAR walls A_j . u <= b_j on position (block edges, corridors, landing footprints;
  each a linear functional of the WHOLE stacked input). Plus dF (facing) constraints (fix or bound
  yaw at chosen ticks; dF=0 pins a tick to not change facing) which are NOT linear in u (they are a
  sector/equality on the input direction). Plus free-start (choose the start position inside a box,
  adding start coordinates as free linear variables). Plus velocity walls DX_t = X_t - X_{t-1}.

THE ONLY NONCONVEXITY (in the current framing): per-tick |u_t| = m_t, a circle not a disk. Identify
u_t with a complex number of modulus m_t: the feasible set is a PRODUCT OF CIRCLES (a torus). This is
a constant-modulus / unimodular QCQP with a linear objective, linear walls, per-tick unit-modulus
quadratic EQUALITIES, and a causal banded friction-convolution input-to-position map.

TREAT THIS FRAMING AS A HYPOTHESIS TO BEAT, NOT A FIXED PREMISE. A top prize is finding a SIMPLER or
different equivalent reduction: a change of variables; a reparametrization by cumulative turn angle or
by exploiting the causal/banded friction structure; a lifting that is convex outright; a
lattice/integer reformulation; or a different identification of where (or whether) the nonconvexity
really lives. The constant-modulus QCQP is the best CURRENT understanding, not the final word.

The DISCRETE byte-exact layer under the continuous relaxation:
- MC quantizes yaw through a 65536-entry integer sine LUT: sin(v) = SIN_TABLE[(int)(v*10430.378) &
  65535]. So feasible directions live on a discrete angular grid (~0.0055 deg spacing). This is a
  closest-vector / integer-least-squares flavor of problem.
- MC applies a per-axis (legacy 1.8/1.12) or combined-XZ (modern) inertia/momentum gate: if the
  post-friction carry |v*0.91| < ~0.005 (legacy per-axis; modern 0.003 combined; code uses the
  model's inertiaThreshold), that axis's momentum is ZEROED (only the fresh acceleration remains).
  This is a mixed-integer indicator / big-M layer. It fires on few ticks but is load-bearing: some
  human routes USE the clamp as a free brake (loopmm).
- The continuous->byte-exact drop is measured tiny (quantization negligible; sine-bucket gap ~3e-4 b
  on thousand/1, of which continuous->byte-exact X drop is 0.007 b). The continuous optimum is a
  clean smooth path; the jitter is a solver artifact, NOT the discrete grid (see section 5).

TWO KEY FACTS (re-verify from code before relying on them):
- SINGLE jumps: constant-modulus hidden convexity (LCvx / lossless convexification of fixed-thrust
  trajectory problems, Acikmese-Blackmore) makes the Lagrangian dual of the continuous relaxation
  TIGHT and its closed-form costate recovery EXACT: each tick points its unit vector along its
  costate, u*_t = m_t g_t / |g_t|. Microseconds. This is the fast path.
- Multi-jump COUPLED windows: that breaks (section 5). The zero-gap argument needs the recovery to be
  the unique inner maximizer; cross-seam wall coupling can break it.

The byte-exact ground truth: MC's real movement, ported byte-exact. In core the search inner loop
uses ExactJumpModel (a byte-exact X/Z stepper, collision-free); the loader-side MC-coupled
SimulatorEntity is the ultimate post-solve verifier (in-game). ExactJumpModel == SimulatorEntity for
any strictly-outside path, so headless verification through ExactJumpModel IS byte-exact (do not claim
"needs in-game" for X/Z; flag only genuinely shippable things for in-game QA).

The MC physics reference lives in docs/reference/mcpk/ (01-movement-formulas.md is the core). If code
disagrees with a number there, the CODE is the bug (byte-exact ground truth). CONTEXT.md is the
glossary.

## 3. CURRENT ARCHITECTURE (verified file anchors; the incumbent you are auditing for collapse)

Core package: core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/
Test package DROPS the .core segment: core/src/test/java/de/legoshi/parkourcalc/anglesolver/ (real; do
not "correct" it).

Orchestrator: AngleSolverEngine.java (runJob is the entry; read first) + AngleSolverState.java (the
solve config: objective, constraints, effort, smoothLambda, legalMode, free-start box).

Solve is routed through a NODE GRAPH: graph/GraphRunner.java over graph/BuiltinGraphs.java presets
(fast(), optimize(), explore(), fromBudget()), with graph/{NodeCatalog, SolverGraph, GraphContext,
GraphFactory, GraphBuilder, Scoring, BudgetWatchdog}. Nodes in graph/nodes/: DualChainNode, BnbNode,
SmoothingNode, WrapIlsNode, IlsPolishNode, SeamSweepNode, RecedingHorizonNode, FreeStartImproveNode,
RouterNode, CapCertifyNode, SetupPeelNode, TranslatedStartNode, MarkSettledNode, ReportNode,
LabelNode, WrapYawsNode.

Solver internals in solver/:
- CostateDualSolver.java: Lagrangian dual of the continuous relaxation + closed-form costate recovery.
  Microseconds, solid on single jumps. MAX_ITER=100 cap; DIVERGE_PGRES=4.0 stall bail; LAMBDA_CAP=1e9.
- ClosedFormSolve.java: recovery + the inward-margin ladder (grow margin until byte-exact feasible);
  also carries the smoothing face-walk hook (recoverFace). RelaxationRecovery.java: SOCP ball
  relaxation (|u_t|<=m_t) via augmented-Lagrangian FISTA + dither rounding + budgeted SLP + lattice
  repair + pin ladder.
- SlpSolve.java on TrustRegionLp.java (bespoke bounded simplex): trust-region sequential LP on the
  facings, judged on byte-exact wall slacks, Jacobian du/dtheta = i*u (closed form, no finite diff).
- BoundPrunedRecovery.java: branch-and-bound over inertia-gate zeroing patterns (per-axis
  zero-from-tick-k), each pattern's root bounded by its own pattern-folded dual.
- IlsPolish.java, WrapWindowIls.java: iterated local search (anytime). SeamSweepRecovery.java,
  LatticeRepair.java, LevelSetAscent.java, ClosestMiss.java.
- FreeStartSolve.java (+ PathTranslation.java, StartBox.java): free start via joint dual + sharpening
  ladder.
- LongRunSolver.java + graph/nodes/RecedingHorizonNode.java: multi-jump via receding-horizon windows
  (window 10, commit ladder {3,1}; measured coupling horizon ~5 jumps).
- Smoothing, FOUR stages, all measured to contribute, gated by the Smooth (TAS) checkbox
  (smoothLambda>0): Objective.java turnCost (reversal penalty in ranking), DeWiggle.java (flick
  remover), SmoothingPolish.java (jerk minimizer), SmoothFaceRecovery.java (final null-space
  face-walk). Support: BucketAscentPolish.java, FacingLattice.java, YawTies.java.
- Byte-exact inner-loop model: ExactJumpModel.java (X/Z stepper), JumpLinearModel.java (THE linear
  model; read it, it is the math above in code), JumpConstraintCompiler.java, JumpConstraint.java,
  McSineTable.java, Constants.java, ForwardModel.java, ForwardPath.java, JumpPhysicsInputs.java,
  JumpSpec.java. Velocity: velocity/VelocityFinder.java.

Tick indexing: posX[t] = position at the START of tick t (before that tick's inputs). A constraint on
tick n affects posX[n]; to constrain what tick n's input produces, place it on tick n+1.

## 4. TARGET CAPABILITIES (north star for the spec and research)

Support these with as few, as general, as analytical mechanisms as possible:
1. FREE-START solve: find the best start position within a box, as part of the solve.
2. dF=0 pinning: force chosen ticks to never change facing, COMPOSING with free-start.
3. SMOOTHING expressed ideally as JUST ANOTHER CONSTRAINT or objective term over the existing
   constraints, not a four-stage post-pass.
4. CONVEX-GLOBAL optimality: when the instance (or its relaxation) is provably tight, return the
   global optimum fast, ideally closed form.
5. SIMPLICITY and GENERALITY: collapse stages and special cases; eliminate inconsistent gaps in
   caching, smoothing, defaults, dF, and free-start that exist on some paths but not others.

PERFORMANCE ENVELOPE: solves run 0.1 ms to 800 ms today and that is GOOD. Do not regress it. Determine
whether meaningfully faster is achievable; any proposal slower than today must justify itself against a
measured quality gain.

CONSISTENCY QUESTIONS the spec must answer with file:line evidence: (1) Does free-start solve behave
IDENTICALLY to a normal fixed-start solve once it has picked the start (if not, where and why does it
diverge: objective, recovery, smoothing, caching; bug or necessary)? (2) Does receding-horizon
multi-jump behave IDENTICALLY to a single-jump solve on each window? (3) Which capabilities (caching,
smoothing, defaults, dF, free-start) are implemented in some stages but not all? Enumerate every gap.

## 5. THE KNOWN HARD ISSUE (re-verify ALL numbers before relying on them; measured last session)

On multi-jump the dual BOUND is near-tight but the closed-form RECOVERY breaks. Measured on
thousand/1-dup2 (49 ticks, 3 jumps, 22 walls, free start, MAX X):
- CostateDualSolver runs all MAX_ITER=100 iters at pgres ~2.4 (should be <1e-8); never converges;
  DIVERGE_PGRES=4.0 does not fire (2.4<4.0), so it grinds the cap.
- A clean 100k-iter reference subgradient drives the dual LOWER (D~2.60, bound ~6523.30 b, near the
  achievable) yet the MINIMUM recovery violation across all 100k iterates is ~2.89 b. The dual optimum
  is a FLAT DEGENERATE FACE: D sits in 2.60-2.64 while recovery violation THRASHES 2.82-5.5 b as lambda
  slides the face. Costates do NOT vanish (meanAbsG ~1.5), so it is not epsilon-collapse.
- So it is a genuine duality gap / degenerate recovery, NOT a convergence bug. Raising MAX_ITER does
  not help (measured 100->10000, closed form still null). "Make the dual converge" is the WRONG lever:
  converging tightens the BOUND but never fixes the RECOVERY.
- The byte-exact max-X path lands on a vertex hugging several tight OPPOSING-PAIR corridors at once. To
  hug them byte-exact on the 65536-bucket grid, the yaw DITHERS (flicks a fraction of a degree) at the
  jump-transition/redirect zones. The dither costs sub-micron X per reversal but is load-bearing for
  the last microns of distance. The smooth 4-5 reversal continuous optimum is NOT byte-exact reachable
  (that is the gap).

THE OPEN SUB-QUESTION (Stage 0 settles this with COPT): is the recovery failure
- H1 (genuine circle-vs-disk / SOCP gap): the true optimum wants per-tick |u_t| STRICTLY below m_t at
  some ticks, which the fixed-modulus constraint forbids (so the disk relaxation |u_t|<=m_t is loose);
  or
- H2 (pure dual-face degeneracy): the SOCP/SDP relaxation is still tight/rank-one, only the recovery
  is degenerate?
Discriminator: solve the SOCP disk relaxation and read per-tick modulus slack (does any |u_t| throttle
strictly below m_t -> H1); solve the SDP/Shor relaxation and read its optimal-matrix rank/spectrum
(rank-one and tight -> H2). The answer decides whether target capability 4 is even attainable for the
multi-jump class.

MEASURED DEAD (do not re-attempt without NEW information; from prior sessions, re-verify if you build
on them): raising CostateDualSolver.MAX_ITER (both directions, non-monotone lottery, breaks corpus);
blaming quantization for jitter (sine-bucket gap ~3e-4, negligible); box-state re-sim as warm-start
(solve ignores per-tick box pos/vel/yaw); the "primal smooth-restore from dual recovery" architecture
(restore plateaus at ~0.035 b, homotopy lands at fallback, adds nothing over the face-walk at the same
slack); CMA-ES at high dimension (ineffective, since removed entirely); SMT-FP as a SEARCHER (it
VERIFIES, does not search; dead as a route finder, only a per-window certifier).

## 6. THE COPT RESEARCH ORACLE (research + ground-truth benchmark ONLY, NEVER shipped)

COPT (Cardinal Optimizer) v8.0.5 trial is INSTALLED AND VERIFIED WORKING (LP solve returns optimal).
LP, MIP, convex QP, SOCP, SDP; coptpy Python API.
- License: files live at C:\Users\benja\Desktop\Coding\98 Anderes\copt (license.dat, license.key,
  expiry 2026-10-06). Do NOT copy, move, print, or commit them. Reference in place.
- Before any coptpy call, set COPT_LICENSE_DIR to that exact folder:
  PowerShell: $env:COPT_LICENSE_DIR = 'C:\Users\benja\Desktop\Coding\98 Anderes\copt'
  Bash:       export COPT_LICENSE_DIR='/c/Users/benja/Desktop/Coding/98 Anderes/copt'
- COPT and any Python/SciPy are RESEARCH ORACLES / BENCHMARK GROUND TRUTH ONLY. NEVER import them into
  core/ or any loader, NEVER add to any Gradle module, NEVER on a shipped classpath or jar. The harness
  lives OUTSIDE every module at research/copt/ (a scratch project, not a Gradle module).

Dependency policy for the SHIPPED path: dependency-free / pure-analytical is PREFERRED (simplest,
fastest; the repo currently ships no numeric-solver dependency, having dropped commons-math3 for
cross-loader packaging reasons). A permissively-licensed, redistributable open-source dependency
(LP/SOCP/QP library, linalg kernel) to core/ is ACCEPTABLE when MEASURED worth it, provided you note
the packaging cost across the Forge (1.8.9, 1.12.2; shade/relocate) and Fabric (include) loaders and
weigh it against the simplicity/speed north star. COPT is the one hard exception: commercial trial,
NOT redistributable, oracle only.

The exporter: MiqcpDump.java (core/src/test/.../anglesolver/MiqcpDump.java) already dumps, per capture,
to build/reports/miqcp-dump-<case>.json: numTicks, objTick/objAxis/objSense, startPos, initialVelocity,
startBox (free/pinned + all bounds), per-tick {f4, mMag, baseArg, forwardMag, strafeMag, boost,
contact, slip, jump, sprint, factorSprint, amp}, constraints {name, mode, t1, t2, op, cmp, rhs}, and
warm yaws/paths. From this JSON the whole continuous model is reconstructable (coef(s,k) from f4 via
prefix products; constPos from startPos/vel; wall = mode/t1/t2/op/cmp/rhs compiled as in
JumpLinearModel.compileWall). Stage 0 extends this to a general StructureDump that runs on ANY capture
(currently it is razor-specific) and, to remove any Python reimplementation risk, ALSO dumps the
compiled wall coef[] arrays, bPrime, eq, axis, the objective vectors cx[]/cz[], and constPos(objTick).
Reference dumpers: CoefDump.java, StructureVariantDump.java, MatrixAnalysisScreen.java,
RelaxDiagScreen.java.

How to run a headless dumper (Gradle SWALLOWS -D/env into the test JVM only if wired; MiqcpDump reads
System.getenv, so pass env before gradle): the test is @Test gated by an env var. Example:
  PKC_MIQCP_DUMP=1 PKC_MIQCP_CASE=proof ./gradlew :core:test --tests '*MiqcpDump' -PslowTests
Or, for microbenchmarks and probes, compile once (./gradlew :core:testClasses) then run via direct
java -cp (assemble classpath from core/build/classes + resources + resolved deps); Gradle swallows
-D/env for those, so direct java -cp is the reliable path.

Stage 0 tasks with COPT: (a) SETTLE H1 vs H2 on f2f-dfchain-multijump.json and df-chain-free-start.json
(+ single-jump j001) by solving the SOCP disk relaxation, the SDP/Shor relaxation, and where tractable
a moment/Lasserre level-2 relaxation; report per-tick modulus slack and SDP rank/spectrum; state the
circle-vs-disk gap vs face-degeneracy IN BLOCKS. (b) VALIDATE THE BOUND: trusted relaxation/global
bounds per capture; quantify CostateDualSolver's actual bound tightness. (c) PROTOTYPE FORMULATIONS in
COPT before committing to any bespoke implementation. (d) STAGE E REFERENCE OPTIMUM: COPT global
answers are the per-capture reference (measured absolute + relative gap) for every new approach.

## 7. THE CORPUS (measure on these; re-verify counts from resources)

- core/src/test/resources/captures/ : 54+ top-level captures. Single jumps j001-j022-* (j001 also a
  353-tick 30-jump run). Multi-jump / free-start stand-ins: f2f-dfchain-multijump.json,
  df-chain-free-start.json. Momentum: loopmm-*. Razor family: razor-*. hpk/ folder with d9 (easier),
  d10, d11 (harder) tiers.
- core/src/test/resources/problems/{solve,closedform,dualrecovery}/ : folder-driven checks; each
  capture's sidecar defines the pass condition. ProblemsTest is parameterized over these (tagged slow).
  anglesolver/TESTS.md maps them.
- Benches/probes to reuse/extend (several are uncommitted env-gated diagnostic screens):
  FreeStartSweepBench.java (104 captures), HpkEngineBench.java, GateMicrobenchTest.java,
  ProblemsTest.java, BlockSolveProbe.java, ContinuousDiscreteScreen.java, NondetProbe.java,
  ThousandDiagScreen.java, WarmStartLoopProbe.java, MatrixAnalysisScreen.java, RelaxDiagScreen.java,
  EngineFileScreen.java (drives the live engine on any save: PKC_SOLVE_FILE, PKC_SOLVE_EFFORT,
  PKC_SOLVE_TIMEOUT_MS), SolveNodeStatsScreen.java. Trace: SolverTrace writes
  build/reports/solver-trace-<tag>.txt (PKC_SOLVER_TRACE or -Dpkc.solver.trace).

## 8. THE CORRECTNESS GATE AND BUILD

- Gate: ./gradlew :core:test -PslowTests GREEN. The fast :core:test EXCLUDES the expensive solver
  suites (category de.legoshi.parkourcalc.SlowSolverTests) and is INSUFFICIENT for solver code; CI
  always runs -PslowTests. Run the fast suite after any change; the full slow suite whenever solver
  code is touched. Tag any new corpus-driving test class @Category(SlowSolverTests.class).
- Prototypes live in core/src/test/ (screens/benches), or the research harness, or behind a flag. The
  shipped path stays GREEN at all times. You PROPOSE integrations; you do NOT merge. Do not rip out the
  working solver.
- JDK 21 runs the Gradle daemon; core is Java 8 + JUnit 4. tableStyleCheck runs on :core:build (known
  false positive on SolverWidgets; CI skips with -x tableStyleCheck).
- Windows PowerShell is primary (no bash line-continuations; quote -P args); a Bash tool is also
  available for POSIX scripts. The scratchpad for throwaway temp files:
  C:\Users\benja\AppData\Local\Temp\claude\...\scratchpad (but durable artifacts go under
  docs/research/solver-rework-2026-08/ and research/copt/).

## 9. RIGOR BAR (the priority; every agent, every claim)

- NO CLAIM WITHOUT A MEASUREMENT. Every quantitative statement cites the exact command, the capture,
  and the number. Ban "should be faster", "probably feasible", "likely converges", "seems to fail"
  from conclusions. A hypothesis is LABELED a hypothesis with the experiment that would test it.
- EVERY FAILED APPROACH ENDS IN A MEASURED ROOT CAUSE: on capture X, constraint j violated by V
  blocks, dual plateaued at P after N iters, wall-clock T ms; reproducible.
- BYTE-EXACT VERIFICATION IS MANDATORY: replay every produced yaw sequence through ExactJumpModel and
  report the residual. Solver self-agreement is NOT verification.
- BENCHMARK SYSTEMATICALLY: warmup then repeated timed runs; report medians and spreads, not single
  samples. Run microbenchmarks via direct java -cp (Gradle swallows -D/env).
- RESEARCH: cite REAL, checkable, named sources; never invent papers, authors, or results; separate
  established from speculative; label each applicability claim measured-against-our-model or
  theoretical; route anything uncertain to a prototype before claiming it.
- Re-measure from CURRENT code before building on any number in the handoffs or memory (including
  numbers in this pack). Treat every recalled figure as RE-VERIFY-ONLY.

## 10. SHARD SCHEMA (every fan-out agent emits exactly one markdown file into its stage folder)

Header: agent id; assigned territory; files/commands actually inspected or run.
Body: a numbered finding list. Each finding carries:
- ID: stage letter + agent number + sequence (e.g. A03-2).
- TITLE.
- LOCATION: file:line or subsystem (or research topic for Stage D).
- CLAIM: one sentence.
- EVIDENCE: a measured number and the exact method, OR the literal tag UNMEASURED-HYPOTHESIS if it is
  a belief to route into research.
- IMPACT: speed / simplicity / robustness / smoothness / correctness, with magnitude.
- PROPOSAL.
- CONFIDENCE: 0 to 1.
- DEPENDS-ON: other finding IDs.
Findings only, no essays. Write the file with the Write tool; report its path back to the orchestrator.
