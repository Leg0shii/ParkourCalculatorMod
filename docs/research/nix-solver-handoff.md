# Solver handoff: nix razor jumps, complete record and next directions

**Status 2026-08-21:** the section 0 scoreboard is superseded by razor-campaign-2026-07-09-handoff.md: 5.4375 was subsequently solved and improved in-tool (user-confirmed), rung 5.375's legal record was beaten (see next-session-lever2-2026-07-10.md), and the weirdpane legal record was achieved. The section 2b Stage A/B plan was executed as AlmSnapStage and later removed (issue 380; see the alm-snap design docs). Every harness named in sections 3-4 was deleted in the 2026-08 cleanup, so section 4's run instructions are historical. The failure taxonomy (section 1) and the Sheepram source audit (section 2) remain current research records.

Written at session end 2026-07-08. Read this FIRST before touching any nix / razor-jump solving. Companion docs: nix-real-54375-campaign.md (the 5.4375 campaign detail), nix-full-freestart.md (the earlier nix-full arc), angle-solver.md (engine design record).

## 0. The honest scoreboard

- 5.4375bm nix neo, corrected constraints: SOLVED externally (prover), NOT by us. The proof (5.4375bm_nix_proof.json, hand-recreated by the user) replays byte-exact in ExactJumpModel (posDiff 8.9e-16, viol 0, pad margin 1.64e-4). It satisfies our constraint encoding completely: our model and specs were right, our SEARCH lost.
- Our best search residual on that problem: 2.59e-4 violation, three independent basins converging there and plateauing.
- Rung 5.375bm (z-lo raised 0.0625): the community's best known attempt is 2.74e-4 off (user-stated fact). Our heavy pipeline reached only 3.96e-3 before the session ended. The gap between what exists and what we find is roughly 10x and systematic.
- The recurring pattern across EVERY razor problem this project has faced (nix-full, 5.4375, 5.375): our stack stalls at 1e-4..1e-2 residuals on solutions that hug 4-6 constraints exactly, while Sheepram (C++, ALM+BFGS plus Discrete Local Search over significant angles) finds them. This is a capability gap with a known name, not bad luck.

## 1. Why our stack systematically loses on razors (root-cause analysis)

Precision matters here; an earlier draft overclaimed. Yaw enters physics only through McSineTable (65536-entry float LUT), so the fitness landscape is piecewise-constant: within one bucket the trajectory is EXACTLY constant. A continuous search over yaws still covers every solution (the lattice points are a subset of the continuous domain, evaluated through the same exact model); the problem is search EFFICIENCY, not visibility. A razor solution that requires specific buckets at k ticks simultaneously occupies a hyper-rectangle of volume (bucket width ~0.0055 deg)^k in yaw space, and inside neighboring buckets there is no gradient signal pointing at it. A lattice search does not see more solutions; it DECOMPOSES the joint needle into single- and paired-bucket decisions, each scored by max-slack, which is tractable where joint sampling is not.

IMPORTANT epistemic status: "Sheepram wins because of DLS over significant angles" is the LEADING HYPOTHESIS, not a validated fact. Supporting evidence is circumstantial (they solve this class, we do not; the user reports DLS was their added feature; our failure signature is plateaus at wall intersections with no improving continuous direction). Unexcluded confounds: their ALM+BFGS core may outperform our CMA/SLP regardless of DLS; restart strategy; constraint handling; compute budget; model details. The falsification test is defined below (section 2, Direction 2, regression pair): add ONLY a DLS stage to our own rung-5.375 plateau warm (3.96e-3) and check whether it reaches <= 2.74e-4. If it does not, the gap lives elsewhere and the Sheepram bridge doubles as the diagnostic.

Stage-by-stage failure modes against the piecewise-constant landscape:

- CMA-ES (SolveCore): Gaussian sampling must land k specific buckets jointly; success probability decays with k and there is no within-bucket gradient; penalty landscapes plateau at the nearest wall intersection (the observed 2.6e-4 walls).
- Pattern B&B (BoundPrunedRecovery): branches on movement patterns, not on sine buckets; its granularity cannot represent "this exact bucket at t13, that one at t38".
- HomotopyCloser: pure continuation. Its bucket descent does 1-tick scans and 2-tick joint scans only; a razor that needs a coordinated 3+-tick lattice move (or a move through a temporarily-worse ridge) is unreachable. It closed nix-full because that basin was reachable by continuation from the assembled warm; it cannot JUMP.
- Kick cycles: random multi-tick perturbations; the needle neighborhoods have measure ~0 for random kicks.
- Momentum frontier scan (NixMomentumScan): dedup grid at 2.5 cm / 3e-4 velocity cannot carry razor lineages through 50 ticks; tightening the grid explodes memory.

Conclusion: every stage is either continuous (blind to the lattice) or discrete at the wrong granularity. Sheepram's DLS over significant angles is discrete at the RIGHT granularity. That is the entire difference.

## 2. Sheepram, source-verified (read 2026-07-08, local repo C:\Users\benja\Desktop\Coding\06 C++\Sheepram, Odin)

Files that matter: src/optimizer/{optimizer,discrete,exact_sim,trig,comp_expr,raw_expr}.odin (~1900 lines), TECHNICAL.md. What it ACTUALLY does:

**Phase 1, continuous**: compiles the movement recurrence into per-tick linear-in-(theta, sin theta, cos theta) expressions (identical concept to our JumpLinearModel), then runs ALM (multiplier update lambda <- max(0, lambda + rho g); rho *= 2 when violation stalls; <= 25 outer iters; feasibility tol 1e-5) with a BFGS inner loop (<= 80 iters, dense inverse-Hessian, ANALYTIC gradients from the compiled form, strong-Wolfe line search with binary-only zoom). Smooth sin/cos, LUT deliberately ignored. Multistart = constant-angle seeds only (every tick the same theta; optimize_best_of loops over a seed list). Initial speed direction is a continuous variable; positions are relative (constraints written as X[m]-X[0], start-translation invariant).

**Phase 2, discrete** (discrete.odin local_search): snap facings to u16 LUT indices, then Repair mode (until exact-feasible) / Polish mode (objective only, feasibility-preserving):
- Fast grader: the compiled expressions evaluated at LUT-quantized angles in f64 (claimed e-7 accurate); exact grader: f32-arithmetic simulation (exact_sim.odin). Fast filters, exact verifies; exact ineq standard is violation > 0 -> infeasible (strict), eq tol 1e-5.
- 1-opt rounds: full scan of +-1 bucket for EVERY tick, keep top-32 by grade, exact-check best-first, accept first exact-feasible (improving, in Polish).
- 2-opt rounds: pair moves with deltas {(+-1,+-1), (+-1,+-2), (+-1,+-3), (+-2,+-1), (+-3,+-1)}; Regular mode tries each pair once per round (shuffled); Cooking mode samples 512*n random pairs per round and, after 256 attempts, accepts up to 128 bounded worse moves (objective drop <= 1e-5 each, exact-feasible only): a bounded annealing escape.
- NO "significant angle" prioritization exists in the source; the discrete search is plain +-1..3 bucket 1-opt/2-opt. The earlier "DLS over significant angles" description was secondhand and is NOT what the code does.

**Their weaknesses (verified in source), where we can dominate**:
1. **NO inertia gate anywhere**: exact_simulation has no 0.005 threshold; velocities always carry. Any route with a gate-crossing tick (every reversal, e.g. the nix hop turnaround) is MIS-MODELED by their "exact" grader. Our ExactJumpModel handles per-axis legacy gates exactly. On gate-touching problems we are strictly more faithful; they cannot even represent gate-cancel tricks.
2. **Sprint-factor lag is manual** ("Sheepram intentional has user to do sprint delay manually", source comment); ours is modeled (factorSprintAt).
3. Multistart diversity is weak (constant-angle seeds only); no warm import; no basin generators like our CMA/BnB/MomentumAssembly.
4. Discrete moves capped at 2-opt with delta <= 3; no model-guided k-opt, no segment moves.
5. Single-threaded search loop; binary-only line-search zoom (self-admitted weaker than scipy).
6. Feasibility tolerance for eq constraints 1e-5 (we close to <= 0 exactly).

**What they have that we LACK (the actual gap, source-verified)**:
1. A gradient-based smooth-model core: ALM+BFGS with analytic gradients converges superlinearly to razor wall-intersections; our workhorse is CMA sampling the piecewise-constant exact model, which plateaus (the observed 2.6e-4 walls). We have the compiled linear form (JumpLinearModel) but never built a first-order constrained optimizer on it.
2. The snap -> repair -> polish discrete pipeline with fast-grade filtering and exact-verify acceptance. Our HomotopyCloser is continuation-only; its bucket descent has no systematic 1-opt full scans, no top-K exact-check ordering, no bounded-worse annealing.

## 2b. The dominance plan (goal: beat Sheepram on EVERY search)

Stage A, replicate their architecture on our (stronger) exact model:
1. **AlmBfgsCore** (new, core/anglesolver/solver): smooth model from JumpLinearModel coefficients; ALM outer exactly as theirs (lambda update, rho doubling); BFGS inner with analytic gradients (d pos/d theta_t = coef chain x (cos, -sin) from JumpLinearModel); strong-Wolfe line search with polynomial-interpolation zoom (better than their binary). ~400 lines Java. Feasibility target 1e-6 (tighter than their 1e-5).
2. **SnapRepairPolish** (new): port local_search faithfully: snap to McSineTable indices; 1-opt full scans with top-32 exact-check; their exact 2-opt delta set; Regular + Cooking modes with their thresholds (256 attempts, <= 128 downhills, drop <= 1e-5). Fast grader = JumpLinearModel at LUT values; exact grader = ExactJumpModel (byte-exact WITH gates and sprint lag, strictly more faithful than theirs). Incremental exact re-sim from earliest changed tick for speed.
3. Wire as an engine stage: AlmBfgsCore multistart (constant-angle seeds + our existing warm generators) -> SnapRepairPolish -> existing certify.

Stage B, exceed:
4. Warm diversity tournament: seed Stage A from constant angles (theirs), CMA basins, BnB patterns, MomentumAssembly templates, recorded warms; parallel across threads (they are single-threaded).
5. Model-guided k-opt: compensated pairs and triples chosen via JumpLinearModel.coef to null downstream drift (walks along constraint manifolds; their 2-opt is blind pairs); segment shifts (all buckets in [a,b] +- 1).
6. Gate-pattern enumeration wrapper: enumerate plausible inertia-event patterns near reversals (our model knows where |v| ~ 0.005), solve each smooth variant; Sheepram cannot represent gates at all, so every gate-touching problem is an automatic win.
7. Exactness edge: our acceptance is viol <= 0 byte-exact including gates/lag; their accepted solutions can be MC-infeasible near gates. Advertise and test this.

Regression/benchmark suite for "dominate on ANY and EVERY search" (build as problems/solve cases + a timing harness):
- the 5.4375 proof, cold (known answer, viol 0, objX 212.7001641);
- rung 5.375 (beat the community best, 2.74e-4 off);
- weirdpanethingtest (beat -8.86477, target >= -8.8625);
- nix-full-t1 (existing regression, keep green);
- a gate-crossing microbench (hop reversal window) where Sheepram's model is provably wrong.
Metric order: exact feasibility first, objective second, wall time third. Run Sheepram itself on the same problems (its DSL, presets/ folder has examples) for direct comparison.

### Direction 3: MIQCP certification track (background, for floors and proofs)

The recurrence is LINEAR in (sin theta_t, cos theta_t) (see reference_global_optimization_mc memory and JumpLinearModel). With the integer LUT encoded as SOS/piecewise constraints, Gurobi/SCIP can certify the SMOOTH model to global optimality in about a day per instance, then ExactJumpModel verifies byte-exact. Use for: proving momentum floors ("5.3125 is impossible") rather than finding solutions fast. Prereq: fix the inertia-gate pattern per instance (enumerate the few plausible patterns; TAS-Wolfram does exactly this with 'inertia_events').

### Direction 4: Wolfram per-phase QCQP (cheap, already scaffolded)

momentum_qcqp.wl (scratchpad) formulates the forward phase over 26 angles with exact per-tick coefficients (CoefDump output). It was killed before producing results. To use: fix gate patterns as constraints, run NMaximize (DifferentialEvolution, multiple seeds) per phase, snap to buckets, verify. Precedent: NMaximize beat CMA-ES on j021. Lower priority than 1/2 because it is still continuous (same blindness), but useful as a diverse basin generator.

### Direction 5: ladder methodology (once any of 1/2 lands rungs)

Warm-chain rung to rung (0.0625 z-lo raises); on failure diagnose WHICH wall binds (print per-constraint slack at the plateau, tooling gap: add a slack-profile printout to NixWeirdClose); only then try structural edits. Structural edits that were REFUTED on this geometry (do not retry blindly): run-tick pattern permutation (12,13,13), forced +-X weave launches, gate-cancel constraint at the reversal, land-box relaxations (illegal).

## 3. Complete experiment log (both campaigns, 2026-07-07..08)

Uncorrected 5.4375 (pad Z >= 9.4875): SOLVED by us. Chain: heavy basin search -> NixArcMap arc-demand map (landing band vz 0.30-0.40) -> seam-target probe (no-run-tick lead caps vz 0.236) -> USER's run-tick insight (land T50, ground run, jump T51) -> closed 41 s, margin 8.7e-3. TAS: SOLVED_REAL_5.4375bm_runtick.json.

Corrected 5.4375 (pad Z >= 9.550000011921): NOT solved by us; proof verified post-hoc.
- Heavy pipeline (BnB tol ladder 0/1e-3/5e-3/2e-2; megas 768 restarts at eps 3e-2/1e-2; intense close = 4x(300 s ladder + 12 feasOnly-CMA kicks); seam-splits at 4 seams): three basins all plateau 2.6-3.1e-4.
- Arc maps at fixed entries: 8-9e-3 short; the pz-vz trade capped by Z@53 wall and ground-friction run-tick cap (vz <= ~0.20, f4 0.546).
- Seam-target with displacement-encoded velocity targets: f4 conversion trap found (use f4 of the seam-previous tick); lead caps confirmed.
- t0 full-route: stale warm 8.59 ceiling; pinned prover start 8.68 ceiling.
- Gate-cancel (|dz| <= 0.00499/f4 at reversal, prover start): t24 best 7.0e-4; t23/t25 stopped early.
- Structure permutations, weaves: all worse (see section 2, Direction 5).
- NixMomentumScan (48-action frontier, 300 starts, 400k cap): collapses at t46-47 on the z-budget razor; retention fixes (phase-aware score, kinematic debt) insufficient; confirmed geometry, found nothing new.
- NixZPlan 1-D: pure-Z forward caps vz@50 = 0.169 at the z-ceiling; diagonal build essential.
- FALSE SOLVE incident: dropped boxes at internal 23/36/48 on a wrong cycle-length derivation ("phantom boxes"); both routes then "closed" trivially; user caught it (display T37 IS a land tick). Lesson recorded: land grid = space tick + 11 internal; display = internal + 1; NEVER drop authored constraints on a structural theory.
- Proof analysis: basin = hop-land hugging z-lo exactly + jump-2 backward bbox-coyote launch (z center 1.248, 0.26 past the edge, bbox grounded) + jump-3 gentle +x coyote (11.3055) + five exact hugs + pad margin 1.64e-4. Their A->D strafe flip is expressiveness-neutral (look yaw -90 gives identical game facings) but requires KEEP-mode spec authoring to replay (FORCE_45 global strafeSign diverges 3.3 blocks).

Rung 5.375 (z-lo +0.0625, warm from proof): standard close failed (warm viol exactly 6.25e-2, violation entirely in the lead; the tail stays feasible); heavy reached 3.96e-3 before session end. Community best known: 2.74e-4 off. THE ACTIVE TARGET for directions 1/2.

## 4. How to run everything (operational knowledge)

Problem files: game folder C:/Users/benja/Desktop/Games/MultiMC/instances/1.8.9/.minecraft/parkourcalculator/ (5.4375bm_nix_proof.json = ground truth; UNSOLVED_*.json = the encodings; SOLVED_REAL_5.4375bm_runtick.json = our valid solve; SOLVED_REAL_5.4375bm_prover.json = INVALID reference). Scratchpad copies under the session scratchpad (corrected_real54375_baked.json etc.) die with the session; rebuild from the game-folder files plus this doc if needed.

Harness invocation, direct JVM (bypasses gradle daemon contention):

```
CP="core/build/classes/java/test;core/build/classes/java/main;core/build/resources/test;core/build/resources/main;<junit,hamcrest,gson,commons-math3 jars with C:/ paths>"
MSYS2_ARG_CONV_EXCL="*" PKC_WC_FILE=<save.json> java -cp "$CP" org.junit.runner.JUnitCore de.legoshi.parkourcalc.anglesolver.NixWeirdClose
```

MSYS2_ARG_CONV_EXCL="*" is MANDATORY under git-bash (it silently corrupts -cp path lists otherwise; symptom: NoClassDefFoundError with the jar demonstrably on the CP). Recompile a single test sideways with javac -d <dir> and prepend the dir to CP; gradle-run tests are fine too (./gradlew :core:test --tests ...) when no other gradle build is running.

NixWeirdClose env: PKC_WC_FILE, PKC_WC_FLOOR (generalized pad tighten: any near-pad GE within 0.02 below raises to it), PKC_WC_WARMDUMP (TASROW-format dump of a previous run), PKC_WC_JUMPVAR "from,to" (abs ticks, extends ground when moving later), PKC_WC_START0 + PKC_WC_STARTX/Z (full-route pinned start), PKC_WC_GATECANCEL <absTick>, PKC_WC_HEAVY, PKC_WC_BUDGET_S. Output: warm line (model-vs-sim diff + viol), CLOSED/NOT, TASSIGN/TASROW/TASPOS dump on success.

File semantics traps (each cost us a day, respect them):
- Display tick = internal + 1 everywhere the user speaks. Land tick = space tick + 11 internal. "Runs a tick" = 13-gap between spaces, "without running" = 12-gap.
- Land-tick input is AIR physics (onGround flips at move end); first ground-accel tick is the next one. Slip overrides mark input-ground ticks, boxes mark position-on-block states; they differ by one at landings.
- Row realization: solver results MUST be written as yawLocked=true rows with yaw = game facing and keys re-authored (W+SPRINT+A/D+JUMP); delta rows corrupt replay.
- KEEP vs FORCE_45: files with mixed A/D strafe need defaultInputs=KEEP for spec authoring; hand-recreated files may lack landingTick/axis/goal (debugBuildSpec -> null -> NPE), patch before running.
- Jump ticks are position-unconstrained (user rule); grounded jump/land states may hang up to 0.3 past block edges via bbox (the proof uses both).
- The engine result yaw contract: absolute wrapped yaws whose replay toGameFacings(wrapAll(yaws)) is feasible; concatenate ABS yaws across seams, never game facings.

## 5. Loose ends at session close

- weirdpanethingtest.json (task #9, OPEN): pane jump, user best -8.86477, needs >= -8.8625 (gap 2.27e-3). Baseline harness reproduces the user state byte-exact (warm viol 2.2718e-3 = the gap); fixed-start closer failed; a 48-cell start-position grid (x {-8.80..-8.14} x z {0.43..1.69}, 150 s budget cells) was sweeping when the session ended, results incomplete in scratchpad pane_grid/. Restart it or, better, hit it with Direction 1/2. The start position is the user's suspected lever; the objective outcome list shows four constraints hugged (T40 Z, T41 X, T47 Z, T47 X), same razor class.
- The proof-warm ladder (rung 5.375) was killed mid-heavy; re-runnable via scratchpad proof_ladder.sh pattern reconstructed from section 3.
- The DLS regression pair (proof + rung 5.375 target 2.74e-4) is defined and ready in section 2.
