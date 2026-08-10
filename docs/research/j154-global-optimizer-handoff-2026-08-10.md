# j154 cold-solve handoff: the certify is solved, the enumeration needs an objective-bounded B&B (2026-08-10)

Self-contained continuation doc for finally solving **j154 cold** (`core/src/test/resources/captures/hpk_human/d12/j154_1bm_Head_Butterfly_Neo.json`). Read alongside `coldsearch-handoff-2026-08-09.md` (sections 8-10 are this session), `momentum-milp-handoff-2026-08-08.md` section 0 (goal/rules), and memory `project_momentum_exact_search.md`. Everything lives in the worktree `.claude/worktrees/coldsearch`, branch `worktree/coldsearch`, uncommitted (the user commits). All 7 pins + full `-PslowTests` green as of this writing.

## 0. TL;DR

- **The certify is SOLVED and fast.** Given j154's key-pattern, we now find its byte-exact facing bucket + start + air yaws in ~105 ms (`certifyLine SOLVED, viol 0.0`). This was the multi-session wall ("gate zero") and it is gone. Pinned as `j154KnifeEdge`.
- **The cold enumeration is NOT solved.** Finding j154's key-pattern (level 6, backwards-sidestep) from constraints alone is the remaining blocker. It is the same blocker as j716.
- **What finally cracks it: an objective-bounded branch-and-bound** (minimize the capture's declared objective subject to the boxes, LP-relaxation lower bound per branch, prune suboptimal branches, verify leaves byte-exact via the existing bucket sweep). This is the user's B&B idea done with an OBJECTIVE bound, not just feasibility. It is the repo's long-flagged "byte-exact certified solver". It is a real build; it is the correct one; it also lets us BEAT human lines.
- **DO THE PROTOTYPE FIRST (go/no-go).** Before building the B&B tree, build ONLY the LP-relaxation lower-bound primitive and prove it prunes (milestone M-A, section 6). If the bound is too loose to separate the knife-edge line from its real-feasible siblings, the whole approach is a no-go and we reconsider. Do not build the tree until the primitive passes its gate.
- **STAY GENERALIZABLE.** One shared method and code path for all captures, exactly like the certify (which already generalizes across j012/j264/j014/j276/j154). Nothing here may be tuned to j154. The prototype and every milestone are validated on MULTIPLE captures (at minimum j154 AND j716, plus a check that the solved pins do not regress). If the method degenerates into per-capture logic, stop and report.

## 1. j154, exactly

- Goal: axis X, goal MIN. startTick 0, landingTick 39.
- Boxes: start rect at tick 0 AND tick 1 (X[-1601.3,-1599.7] Z[4930.3,4931.3]); momentum checkpoints at ticks 14 and 27 (same X/Z box); intermediate air constraints Z>=@29, X>=@30, X>=@33, X>=&Z<=@34; landing box at 39 (X[-1600.137,-1599.7] Z[4927.863,4928.7]). DF=0 (facing tied) on ticks 1..27.
- Human line (validation only; NEVER a solver input): sig `4.8.8.8.8.8.8.8.8.8.8.8.8.8.6.2+4.2+2+2+2+2+2+2+2+2+2+2+1+`, level 6, presses [2,15,28]. Momentum held at facing **-76.627464** (t1..t28), ja turn at seg 28 (-179.5), free air to -225. Structure per cycle: A / SD-glide / press.
- `CaptureSanityScreen`: stored line maxViolation **0.0** (real, byte-exact), tightest satisfied constraint **X@34 clearance 9.999894e-10** (a ~1e-9 knife-edge on an INTERMEDIATE air constraint). This is why it is hard.

## 2. What is SOLVED: the certify (byte-exact lattice search)

The realization (user-driven): a no-turn line clearing a constraint by 1e-9 was CALCULATED on a discrete lattice, not hand-aimed. The yaw is quantized to the integer sine LUT: 65536 buckets over 2*pi, so **one bucket = 0.005493 deg = (180/pi)/10430.378350470453** (constant `YAW_BUCKET_DEG` in ColdSearch).

`ColdLatticeProbeScreen` (PKC_COLD_LATTICE_FILE) proved it: take the human byte-exact line, nudge each yaw by +-N buckets:
- The **momentum facing has EXACTLY ONE feasible bucket**: -76.627464 clears; both neighbours violate (+1 bucket -> maxViol 1.23e-5, -1 -> 3.79e-5).
- Air yaws at ticks 30-34 sit pinned on a constraint seam (-1 bucket violates, +1 is slack).

So "solve continuously then round" is structurally wrong: the continuous optimum rounds to a violating neighbour, and the dual (LP relaxation) is feasible across a whole ~34 deg band so it cannot localise the exact bucket. It MUST be a discrete lattice search.

The fix (all in `ColdSearch.certifyDirect`, fires only when the normal seed loop misses and quickBest < BUCKET_SWEEP_TRIGGER 0.15):
1. `dualRefineTheta` walks quickSliceViol (the dual screen) down into the LP-feasible band.
2. `bucketSweepCertify` enumerates the momentum facing over LUT buckets (radius BUCKET_SWEEP_RADIUS=40 = +-0.22 deg), computes the cheap dual per bucket, sorts ascending, and byte-exact `sliceSolve`s the lowest-dual buckets first (BUCKET_SLICE_BUDGET=30, BUCKET_LP_MAX=5e-2). The feasible bucket has the lowest dual so it is hit early.
3. `sliceSolve`'s entry grid (9-to-81 points) nails the sliver-thin start; this grid is INHERENT to the knife-edge (a lean one-shot FreeStartSolve per bucket was tried and BROKE it, solved=0).

Result: `certifyLine SOLVED in ~105 ms (was 4.8 s), viol 0.0`, start (-1599.7896753542130, 4930.6121918914305). Pinned as `j154KnifeEdge` in `ColdSearchRegressionTest`. This is a GENERAL tool for every "calculated" human line, not a j154 patch.

## 3. Certify performance (measured, ColdBenchScreen)

- Raw physics (ExactJumpModel forward + constraint check, fixed inputs AND yaws) = **2.4 us**. This is the floor.
- LineSpec.build (spins up an AngleSolverEngine + debugBuildSpec) = **38 us**. The certify cost is search + engine/model-rebuilds, NOT physics.
- SCREEN (probeSig one sig) = ~5 ms => **~190 sigs/sec**.
- CERTIFY LAND (finds the feasible bucket) = **105 ms** (was 4.5 s; 45x from sorting the bucket sweep by dual and solving the lowest-dual bucket first).
- CERTIFY MISS (near-feasible, no engine) = **~385 ms**; clearly-infeasible sigs fast-reject at the screen.
- `ENGINE_FALLBACK` flag (ColdSearch, ~line 57) set **false**: engineCertify (3 s FAST + 20 s escalate) is redundant now that certifyDirect has the bucket sweep, and it was pure cold-ladder waste (j154 level-1 certify 96 s -> 6.5 s with it off). All pins + full slowTests green without it. Kept behind the flag.

## 4. Cold enumeration: everything tried and its result

The certify is done; the open problem is producing j154's key-pattern from constraints only. j154 is level 6; the arc-sweep exhaustive pass only reaches level 4.

1. **Blind arc ladder** (ColdSearchScreen, PKC_COLD_SEEDED=false): reaches level 2 in ~1 min (49457 sigs, 37.6M nodes); levels 3-4 explode. A level-2 sig that probed 0.0 certify-MISSED (the probe/dual was a false positive). No certifiable sibling at level <= 2. Verdict: cannot reach level 6.
2. **Per-cycle family BEAM** (ColdCycleBeamScreen; added `Sweep.traceLineTo`, box-pruned partials, 3-phase coast/glide/press families, probe-ranked final certify): built, runs end to end, and EMPIRICALLY hits the documented trap. j154's mid-boxes (ticks 1/14/27) are loose (~1.6 blocks), so box-pruning barely discriminates (cycle 0 keeps ALL families); cycles 1-2 blow past any beam cap; capping by rect width in EITHER direction discards the needle (best probe among 1500 survivors = 1.30, all junk). Re-proves: constraint-free stretches give NO ranking signal, heuristic beams are a lottery.
3. **Feasibility branch-and-bound / funnel tightening** (user idea): the arc sweep ALREADY has the feasibility half of B&B - `funnelOk` (ArcSweep.java:422) prunes any DFS branch whose max-accel velocity envelope cannot reach the next box. I tightened it to look ahead to ALL remaining boxes; **zero effect** (j154 stayed byte-identical at 37.6M nodes). Reason: the max-accel envelope IS the tightest SOUND reachability bound, and it overlaps a ~1.6-block box regardless. Feasibility-pruning is toothless against wide boxes. Reverted.
4. **Grammar prefix seeder** (StratPrefixes + ArcSweep.runSeeded, prior session): non-viable for long run-ups (j716 preCap 0 = 197k sigs / 138M nodes / 241 s and cannot emit a certifiable sibling since the line needs 6+ changes after the first press >> sufCap 2). Same failure class for j154.

## 5. The diagnosis: why j154 cold is hard (the exact mechanism)

j154's hardness is NOT real-arithmetic infeasibility. It is:
- Many key-patterns are REAL-feasible (thread the wide boxes in real arithmetic). The arc sweep's 49457 level-2 sigs are all real-feasible.
- The BYTE-EXACT feasible set is a near-unique knife-edge (X@34 clearance 1e-9), and it is only visible at a LEAF (exact inputs + exact facing bucket).
- So no real-arithmetic bound (reachability, funnel) can distinguish the certifiable line from its thousands of real-feasible siblings. And no heuristic ranking (width, arc length, velocity) correlates with byte-exact certifiability (width is anti-correlated, trap #4). Beams therefore lose the needle; sound feasibility-B&B cannot prune the siblings.

The one signal that DOES separate the certifiable line from the siblings is the **OBJECTIVE**. j154 is MIN-X, and its feasible region is a knife-edge, so the min-X optimum IS (essentially) the certifiable line. Optimising rather than checking feasibility is the missing lever.

## 6. WHAT IS NEEDED TO FINALLY SOLVE j154: objective-bounded branch-and-bound

This is the user's "B&B on inputs AND angles" with an OBJECTIVE bound. It is the repo's long-referenced byte-exact global optimiser (`reference_global_optimization_mc`, `reference_byte_exact_certified_solver`: "BnB + seam search"). Design:

**Variables** (per key-pattern the tree fixes as it descends): one momentum facing theta (discrete: sine-LUT bucket), 2-D start (continuous, affine), per-tick key combo (9 + sprint), press schedule (outer), air yaws after the last press (each a bucket).

**Why it is a tractable global optimisation**: with theta fixed, the whole momentum is LINEAR in (sin theta, cos theta) and the start; every box is linear. So per (theta-interval, partial key assignment) node, a LINEAR PROGRAM gives a valid LOWER BOUND on the objective X (relax the remaining per-tick combos to the convex hull of the 9 combo accel vectors; relax theta to its interval via the 24-gon / arc forms already in ArcSweep).

**The B&B loop**:
1. Branch on: theta interval (bisect), and/or the next undecided tick's combo (9 children), and/or press schedule.
2. Bound: solve the LP relaxation at the node -> lower bound on X (and feasibility). Prune if infeasible OR if lowerBound(X) >= incumbent (worse than the best byte-exact line found so far). The incumbent starts at +inf (or a quick feasible line if one is found).
3. Leaf (all combos fixed, theta narrowed to a few buckets): run the EXISTING `bucketSweepCertify` to get the byte-exact facing bucket + start + air yaws, verify through ExactJumpModel. If viol 0 and X better than incumbent, update incumbent.
4. Continue until the tree is exhausted or the gap closes. The optimum is the near-unique knife-edge line.

**Why this prunes where everything else failed**: the objective lower bound cuts the thousands of real-feasible-but-suboptimal siblings (they cannot beat the incumbent's X), leaving only the branches that can reach the min-X knife-edge. Feasibility bounds could not do this because all siblings are feasible; only the objective separates them.

**Pieces that already exist to build on**:
- Facing carried symbolically as linear forms in (sin, cos): `ArcSweep` (Arcs, Form, evalRange, leqForms/leqZero, the arc intersection machinery).
- LP-relaxation certificate precedent (24-gon outer approximation of the circle): see `stratfinder-levers` doc; and `ClosedFormSolve.dualBound` / `CostateDualSolver` for fast dual bounds.
- The byte-exact leaf certify: `ColdSearch.bucketSweepCertify` (this session) + `ExactJumpModel`.
- Reachability envelope (a loose feasibility bound, keep as a cheap first cut): `ArcSweep.funnelOk`.

**Milestone ladder (M-A is a hard go/no-go; build nothing past it until its gate passes)**:

- **M-A: PROTOTYPE the LP-relaxation LOWER BOUND primitive ONLY. Do not build the tree yet.** Implement one function: given a node = (facing interval [thetaLo,thetaHi], a partial per-tick key assignment with the rest free), return a valid LOWER BOUND on the capture's objective (min X for j154) over all completions, using the convex hull of the 9 combo accels for free ticks and the arc/24-gon relaxation for the facing interval. Build a screen (e.g. ColdBoundProbeScreen) that evaluates it on validation lines derived from the human (validation only): the bound must be (1) a true lower bound (<= the human line's actual objective at every prefix depth), and (2) TIGHTENING - as more ticks are fixed to the human's keys and the facing interval shrinks toward the -76.627 bucket, the bound must climb toward the human's landing X. GENERALIZABILITY GATE: it must pass on BOTH j154 AND j716 (and be finite/sane on j925/j1150). GO CRITERION: at, say, half the ticks fixed, the bound already excludes a comfortable fraction of the objective range (i.e. it would prune suboptimal siblings). NO-GO: the bound stays near -inf / does not tighten -> the relaxation is too loose, report and reconsider (e.g. tighter per-tick envelopes, or a different separator than the objective). Estimated cost: a focused build + a few short screen runs; NO long solves.
- M-B: only if M-A is GO. Build the B&B tree (branch facing interval + next-tick key, prune by infeasibility OR lowerBound >= incumbent, leaf = existing bucketSweepCertify, seed the incumbent with the first byte-exact feasible line). ONE shared code path. Gate: j154 AND j716 cold SOLVED byte-exact through solve(), no human input; 7 pins + full slowTests still green.
- M-C: generalisation sweep - run the same solve() unchanged over several more hpk_human captures (the corpus has 1275) to defend "one method, all jumps". Any capture that needs bespoke tuning is a failure of the approach, not a patch to add.
- M-D (the prize): since it optimises, report the BEST line (lowest objective / fewest input changes) - potentially beating the human line.

**Risks / unknowns to watch**:
- LP-relaxation tightness: if the convex-hull-of-combos + arc-interval relaxation is loose, the bound prunes little. Test the primitive (M-A) before committing.
- The objective must be the capture's real objective (MIN X here). Confirm goal/axis per capture; feasibility-only captures (no meaningful objective) may still need the grammar prior.
- Leaf cost: bucketSweepCertify is 105 ms; keep leaves rare (deep pruning) or the tree's leaf-certify budget dominates.

## 7. Tools / screens built (all env-gated, test-only, same package)

- `ColdLatticeProbeScreen` (PKC_COLD_LATTICE_FILE): per-yaw +-N-bucket sensitivity; proved the 1-bucket feasible facing.
- `ColdBenchScreen` (PKC_COLD_BENCH_FILE/SIG): throughput - screen rate, certify land/miss, raw verify vs spec-build.
- `ColdCycleBeamScreen` (PKC_COLD_BEAM_FILE + GLIDE/CAP/FSTEP/CERTCAP/BUDGET_MS): per-cycle family beam (demonstrates the trap; reusable if the prior is added).
- `ColdSeededCheckScreen` (PKC_COLD_SEEDED_FILE + ...): prefix-seeder emission gate.
- `ColdHeldDebugScreen` (PKC_COLD_HELD_FILE/SIG): fully-held-facing guard/viol debug (from the j012 work).
- `ColdWarmCheckScreen` (PKC_COLD_WARMCHECK_FILE): warm oracle - human rows -> sig -> certifyLine. RUN FIRST on any capture.
- `CaptureSanityScreen` (PKC_CAPTURE_SANITY_FILE): stored line vs stored constraints, prints tightest clearance. RUN FIRST.
- `ColdSearchScreen` (PKC_COLD_FILE + BUDGET_MS/MAX_CHANGES/CERTIFY_CAP/SEEDED): the real cold solve().
- Supporting ColdSearch hooks added: `benchSig`, `probeViolOf`, `Sweep.traceLineTo`.

## 8. Pins + how to run

7 pins in `ColdSearchRegressionTest` (SlowSolverTests), all via `ColdSearch.certifyLine`: j925, j1150, j012, j264, j014, j276 (cold-solved), j154KnifeEdge (warm certify, the lattice-search proof). Run:
`.\gradlew --configure-on-demand :core:test --tests "*ColdSearchRegressionTest" "-PslowTests" "-PtestHeap=3g" --rerun`
Full gate: `.\gradlew --configure-on-demand :core:test "-PslowTests" "-PtestHeap=3g" --rerun` (~4 min, green).

## 9. Key code (worktree, ColdSearch.java unless noted)

- Certify lattice search: `dualRefineTheta`, `bucketSweepCertify`, `quickViolAtTheta`; constants `YAW_BUCKET_DEG`, `BUCKET_SWEEP_RADIUS`, `BUCKET_SLICE_BUDGET`, `BUCKET_LP_MAX`, `BUCKET_SWEEP_TRIGGER`, `DUAL_REFINE_SCALES`. Called from `certifyDirect` after the seed loop.
- `ENGINE_FALLBACK=false` (redundant engine certify, kept behind flag).
- `sliceSolve` (entry grid, inherent to the knife-edge), `stitch`, `sliceProbeSolve`, `evalMomentum`, `MomentumEval`.
- `heldChainScan` + `ColdProblem.singleHeld` (fully-held class, j012 etc.).
- Enumeration substrate: `ArcSweep` (Arcs/Form/evalRange/funnelOk), `Sweep.traceLine`/`traceLineTo`.

## 10. Operating rules (unchanged)

Cold inputs only (human rows/yaws/debug/result NEVER feed the solver; warm use ONLY for validation oracles). One shared code path; if it degenerates into per-capture logic, stop and report. Byte-exact through ExactJumpModel is the only judge. Community nomenclature only. Never git commit/push/branch (ask, with a ready message, no attribution). Long runs: detached cmd scripts in `C:\Users\benja\AppData\Local\Temp\claude\coldlogs\` with the FULL gradlew path, done-markers, Monitor tails, verify the run started; PKC_* env changes need `--rerun`; `-PtestHeap=3g`. State full-loop costs upfront. WARM-ORACLE and CAPTURE-SANITY every capture before optimising. Fast targeted gates before any long run. Never rank by rect width.
