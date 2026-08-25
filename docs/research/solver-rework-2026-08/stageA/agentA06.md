# Stage A shard: Agent A06 (free-start)

Territory: free-start solve (choose start position inside a box). Files inspected in full:
`solver/FreeStartSolve.java`, `solver/PathTranslation.java`, `solver/StartBox.java`,
`solver/CostateDualSolver.java`, `graph/nodes/FreeStartImproveNode.java`,
`graph/nodes/TranslatedStartNode.java`, `graph/Scoring.java`, `graph/GraphContext.java`,
`graph/RouterPredicate.java`, `graph/BuiltinGraphs.java`, `AngleSolverEngine.java`
(`buildJob`/`deriveFreeStartBox`/`runJob`/`dualChain`), `solver/JumpLinearModel.java` (`compileWall`),
`solver/JumpSpec.java`, `solver/LongRunSolver.java` (`solveFree`/`runHorizon`).
Tests read: `FreeStartTranslationTest.java`, `EngineFreeStartTest.java`. Docs re-verified:
`docs/research/dual-newton-iteration-audit.md` (lines 30-31, 69-74, 93).
Method note: no gradle run (background compile active); claims are code-anchored reads plus two
existing tests that already assert the load-bearing invariants. Numerical claims requiring a fresh
corpus sweep are tagged UNMEASURED-HYPOTHESIS.

---

## A06-1 What the start box adds: two box-bounded linear variables with wall coef = tc

LOCATION: `JumpLinearModel.compileWall` line 248-249, 265; `CostateDualSolver.FreeP0` 158-177.
CLAIM: A free start is exactly a pair of box-bounded linear decision variables `p0=(p0x,p0z)` that
enter every position wall with coefficient `p0coef = ±tc`, where `tc = 1` for a single-tick position
wall (`t2==null`), `2` for a `t1+t2` sum wall, and `0` for a `t1-t2` difference (relative) wall.
EVIDENCE: `compileWall` sets `int tc = (t2==null)?1:(opSign>0?2:0); double p0coef = (GE)?tc:-tc;`
(line 248-249). This is the byte-exact statement that a rigid start shift `+p0` moves a single-tick
wall by `1*p0`, a sum wall by `2*p0`, and leaves a difference wall unchanged (`tc=0`). The dual folds
`p0coef[j]` into the same lambda machinery (`CostateDualSolver` ctor line 113, `hAxis` 365-369).
IMPACT: simplicity. This is the correct minimal encoding; free-start is genuinely just +2 variables.
PROPOSAL: keep this encoding as the canonical free-start representation for any collapsed program.
CONFIDENCE: 0.97. DEPENDS-ON: A06-2.

## A06-2 Free-start is separable from the yaws by rigid translation (the folding lever)

LOCATION: `PathTranslation.translationCore` 38-103; `Scoring.scoredObjective` 64-73;
`FreeStartTranslationTest.checkZeroWidth` 43-60.
CLAIM: For any FIXED yaw sequence the whole trajectory is a rigid translate in `p0` (MC horizontal
physics has no absolute-position term), so the best `p0` for fixed yaws is a 2-variable interval LP,
solved in closed form by `PathTranslation.solveAxis`; the joint `(yaws,p0)` optimum equals
`max over yaws of [obj(yaws) + bestTranslate(yaws)]`. This holds for single AND multi jump (start
propagates to every tick with coef 1).
EVIDENCE: `FreeStartTranslationTest` asserts a zero-width box gives `tx==tz==0` and the translated
violation byte-equals the pinned violation (`Double.doubleToRawLongBits` equality, line 57-58) across
10 random yaw draws on razor-proof, j004, j318. `p0coef=tc` per A06-1 is the coefficient of that rigid
shift. `translationCore` decouples X and Z into two independent 1-D interval problems (nx/nz lists,
line 62-70, `solveAxis` 110-148).
IMPACT: simplicity/correctness, large. The elaborate joint dual is only needed when the fixed-start
yaw search fails to find yaws that a later translation could rescue; the p0 dimension itself never
needs to sit inside the yaw search.
PROPOSAL: treat "solve pinned at box center, then one PathTranslation pass" as the baseline free-start
mechanism; reserve the joint dual for the measured residual (A06-11).
CONFIDENCE: 0.9. DEPENDS-ON: A06-1.

## A06-3 The joint dual: p0 enters as a smoothed box-support term in D(lambda)

LOCATION: `CostateDualSolver` `supportOf`/`deltaOf`/`supportCurv`/`hAxis` 365-388, `costate` 407,
`grad` 426-430, `buildHessian` 437-467; `FreeStartSolve.buildFreeP0` 712-718.
CLAIM: The joint dual adds `supportOf(hAxis(lam,a),a)` to `D(lambda)` per axis, where
`hAxis = objDev + sum_j[axis==a] lam_j*p0coef_j` is the reduced cost of the start coordinate and
`supportOf(h) = h*d - 0.5*smooth*d^2` with `d = clamp(h/smooth, dvLo, dvHi)`. The un-smoothed dual
term would be the exact box support `max_{p0 in box} h*p0`, which is piecewise-linear in `h` (a corner
solution, zero curvature, discontinuous p0 recovery). The `-0.5*smooth*d^2` is the Moreau/Huber
envelope that replaces the corner with a ramp of slope `1/smooth`.
EVIDENCE: `deltaOf` returns `clamp(h/smooth, lo, hi)` (376-381); `supportCurv` returns `1/smooth`
strictly inside the box else 0 (383-388); `buildHessian` adds `p0coef[i]*p0coef[j]*curv` on same-axis
free walls (465-466). So the p0 block contributes Hessian curvature `1/smooth` exactly where the start
is interior. `recoveredDelta`/`deltaOf` (257-259, 376-381) is the recovered start offset.
IMPACT: correctness/robustness. Without the term the joint dual has a flat, non-smooth p0 subspace and
the Newton step is undefined there; the term is what makes the joint recovery conditioned.
PROPOSAL: document that `FreeP0.smooth` is a DUAL-METHOD conditioner (a proximal smoothing of the
support function), not a physics or TAS-smoothness parameter; a true SOCP interior-point solve of the
disk relaxation would not need it (A06-13).
CONFIDENCE: 0.9. DEPENDS-ON: A06-1.

## A06-4 P0_SMOOTH is a ladder {0.05, 2e-3, 5e-4}; sharp end is the gh-386 fix

LOCATION: `CostateDualSolver.P0_SMOOTH_DEFAULT=0.05` line 55; `FreeStartSolve.Config.jointP0Ladder =
{2.0e-3, 5.0e-4}` line 25; `solveJointBest` rung loop 128-135.
CLAIM: `solveJointBest` runs rung 0 at the well-conditioned default 0.05, then sharpens to 2e-3 and
5e-4 only if a rung near-misses, returning on the first feasible rung. The memory/handoff "working
range [5e-4, 2e-3]" is exactly the sharp tail of this ladder, and the "sharpening ladder" is the
shipped gh-386 fix.
EVIDENCE: `for (rung=0; rung<=jointP0Ladder.length; rung++){ smooth = rung==0 ? P0_SMOOTH_DEFAULT :
jointP0Ladder[rung-1]; ... if (r.feasible) return r; }` (128-135). Re-verified against
`dual-newton-iteration-audit.md`: line 69 "P0_SMOOTH 0.05 -> 0.01 / 0.002 / 0.0005 ... the real lever,
but not as a constant"; line 71 "the 4x2 nosolve save SOLVES at 0.002 and 0.0005 (26/26 ... the 0.0005
objective beats the lucky-seed solve). 0.01 is not enough." Code ships the audit's recommended sharp
values (2e-3, 5e-4) and skips 0.01 (measured insufficient).
IMPACT: robustness. The needle-class free-start cases (4x2) that used to depend on a lucky seed now
solve deterministically via the sharp rung.
PROPOSAL: keep the ladder; it is the measured fix, not a candidate for removal.
CONFIDENCE: 0.85 (4x2 solve numbers re-read from the audit doc, not re-run this session).
DEPENDS-ON: A06-3, A06-11.

## A06-5 The pairCap bounds the joint margin ladder at half the tightest opposing-corridor width

LOCATION: `FreeStartSolve.jointLadder` 521-543.
CLAIM: `jointLadder` scans wall pairs on the same axis that are exact anti-parallels
(`wi.p0coef == -wj.p0coef` and `wi.coef[t] == -wj.coef[t]` for all t) and caps the inward margin at
`0.5*(wi.bPrime + wj.bPrime)` (the point where the two opposing walls squeeze to a corridor of width
0). `hi = min(jointMarginMax, max(0, pairCap))` then bounds the tStar bisection.
EVIDENCE: loop 522-535 sets `pairCap = min(pairCap, 0.5*(wi.bPrime+wj.bPrime))`; line 543
`hi = Math.min(cfg.jointMarginMax, Math.max(0.0, pairCap))`. This is the free-start analog of the
single-jump inward-margin ladder, specialized to opposing-pair (EQ-split / footprint) corridors so the
margin never over-shrinks a two-sided corridor into infeasibility.
IMPACT: robustness (prevents the margin ladder from certifying past a two-sided corridor). Narrow
feature; only fires with anti-parallel wall pairs.
PROPOSAL: keep; note it is a special case that a disk-SOCP formulation handles automatically (the
corridor is just two linear walls).
CONFIDENCE: 0.85.

## A06-6 CONSISTENCY: after the start is picked, the solve IS a pinned solve (the pick collapses)

LOCATION: `FreeStartImproveNode.improve` 140-142, 161-162; `jointRescue` 88-93.
CLAIM: Every free-start node that adopts a start MUTATES `ctx.scenario.startPos` and sets
`ctx.scenario.startBox = StartBox.pinned(adopted)`, so from that node onward the scenario is a normal
pinned scenario and all downstream nodes (bnb, ils, wrap, seamSweep, smoothing) run byte-identically
to a fixed-start solve. This is the reassuring half of the mission-critical consistency question: the
start choice is not threaded through the rest of the pipeline as a live variable, it is committed to a
pin.
EVIDENCE: `improve` line 140-141 `sc.startPos = new Vec3dCore(conv.startX,y,conv.startZ);
sc.startBox = StartBox.pinned(...)`; identical at 161-162 (seed-recovered adopt) and `jointRescue`
88-89. `EngineFreeStartTest.freeBoxWithKnownEdgeOptimalTranslationSolvesAndAdoptsIt`
(FreeStartTranslationTest line 72) asserts `sc.startBox == null || sc.startBox.isPinned()` after solve.
IMPACT: correctness (positive). Free-start does not fork the downstream solver.
PROPOSAL: none; this is the desired invariant. A collapsed design should preserve "pick, then pin".
CONFIDENCE: 0.95.

## A06-7 CONSISTENCY divergence: routers score with translation, node solvers run raw-pinned

LOCATION: `GraphContext.scoredViol/scoredObjective` 98-104 vs `violationOf/exactObjective` 90-96;
`RouterPredicate` CANDIDATE_FEASIBLE_SCORED (line 32) vs CANDIDATE_FEASIBLE_RAW (line 30).
CLAIM: The graph's feasibility/objective ROUTERS in the free-start (stop-on-feasible) lane use the
translation-aware `scoredViol`/`scoredObjective` (best rigid translation applied), while the actual
node SOLVERS (dualChain, bnb, ils, wrap, seamSweep) run and self-certify against the RAW pinned start.
A candidate can therefore be "feasible (scored)" yet raw-infeasible until `translatedStart` commits the
translation, so which router a path takes differs between the free and pinned pipelines.
EVIDENCE: `scoredViol` routes through `Scoring.scoredViol` -> `PathTranslation.bestTranslation`
(Scoring 55-62); `violationOf` does not (Scoring 44-47). BuiltinGraphs uses CANDIDATE_FEASIBLE_SCORED
in the sof lane (`rImproveFeas`, `rRescueFeas`, `rNearFeas`, `rWrapFeas`: lines 92, 108, 140, 152) and
CANDIDATE_FEASIBLE_RAW in the cold lane (`rEarlyFeas`, `rFeasFast*`: 101, 104-105).
IMPACT: correctness (necessary difference, not a bug): translation legitimately makes a path feasible.
But it is a real behavioral fork: the free pipeline gates on a different feasibility notion than the
pinned one. This is one of the "gaps present on some paths but not all" the mission asks to enumerate.
PROPOSAL: unify by making the scored (translation-aware) violation the single feasibility notion when
`freeStart`, and applying the winning translation eagerly (fold PathTranslation into the candidate at
creation, not at a late node), so RAW and SCORED coincide.
CONFIDENCE: 0.85. DEPENDS-ON: A06-2, A06-8.

## A06-8 CONSISTENCY divergence: two different start references coexist (center vs clamped-seed)

LOCATION: `AngleSolverEngine.runJob` 954-963 (clamped-seed pin for the seed lane);
`FreeStartSolve.solveJointBest` 118-120 and `solve` 57-58 (box CENTER); `deriveFreeStartBox` 687-689
(box.px = clamped seed).
CLAIM: The seed lane (`seedSingle`/`seedMulti` dualChain) and every pinned recovery node run at the
CLAMPED-SEED start that `runJob` writes into `sc.startPos`, while the free-start joint dual re-derives
its reference as the box CENTER. So warm yaws entering `freeStartImprove` come from a solve at a
different start than the joint dual uses. For a single jump this is harmless (translation-invariant,
A06-2); for coupled multi-jump the two references can produce different yaw sets.
EVIDENCE: `runJob` 954 `refX = clamp(seed into box)`, 961-963 re-reference + pin; `solveJointBest`
118-119 `refX = 0.5*(pxLo+pxHi)`; `deriveFreeStartBox` 687 `refX = max(pxLo,min(pxHi,seedX))`.
`GraphContext.scenario = spec.asScenario()` (GraphContext 55) is the SAME mutable object runJob pins,
confirmed by `JumpSpec.asScenario` returning the stored instance (JumpSpec 21-23).
IMPACT: simplicity/robustness. The dual reference discipline is inconsistent across the pipeline.
PROPOSAL: pin the seed lane at box CENTER too (not the clamped seed), so the whole pipeline shares one
deterministic reference; by A06-2 this cannot hurt single-jump and removes a multi-jump seed axis.
CONFIDENCE: 0.8. DEPENDS-ON: A06-2, A06-9.

## A06-9 SEED DEPENDENCE current state: the free-start SOLVERS are deterministic; residual is the seed lane

LOCATION: `solveJointBest` 118-124, `solve` 57-73, `slpAnchorGrid` 76-99, `recoverStart` 698-710.
CLAIM: After gh-386 all free-start SOLVER entry points are seed-independent: `solveJointBest`,
`solve`, and `slpAnchorGrid` build their linear model from the box CENTER and sweep a fixed fraction
grid `{0.5,0.25,0.75,0,1}`; `recoverStart` is translation-invariant in `box.px/box.pz` (a start shift
shifts the path and the recovered delta cancels it, final start clamps into the same box). The residual
seed dependence is ONLY that `FreeStartImproveNode.improve` will adopt the CLAMPED-SEED seed-lane
result if the deterministic joint dual fails or fails to beat it.
EVIDENCE: center at `solveJointBest` 118-119, `solve` 57-58, `slpAnchorGrid` 80-81; grid fractions
`ANCHOR_GRID_FRACTIONS` line 76. `improve` tries the joint dual first and only falls to the
seed-recovered `foundYaws` when the dual is absent/worse (FreeStartImproveNode 133-163).
`EngineFreeStartTest.seedDisplacedOutsideTheBoxIsReferencedBackAndSolves` moves the seed +3.0 outside
the box and still solves (test 49-66), evidencing coarse seed-independence. The audit line 71 "the
0.0005 objective beats the lucky-seed solve" is the record that the sharp rung now out-competes the
seed path.
IMPACT: robustness. The 4x2 / j335-class fragility is largely closed by center-derivation + the sharp
rung; the residual bites only when the joint dual near-misses AND a specific seed happens to solve.
PROPOSAL: closing A06-8 (center-pin the seed lane) removes the last seed axis; verify with a
seed-sweep bench (A06-14).
CONFIDENCE: 0.8. DEPENDS-ON: A06-4, A06-8. RE-VERIFY: the 4x2 solve counts are re-read from the audit
doc, not re-run this session.

## A06-10 THREE separate rigid-translation implementations (duplication)

LOCATION: `PathTranslation.translationCore/solveAxis` 38-148; `FreeStartSolve.bestTranslate` 645-685;
`FreeStartSolve.pinTranslate` 720-764.
CLAIM: There are three independent implementations of "best rigid translation of a fixed path within a
box to satisfy X/Z walls and optimize one axis": (1) `PathTranslation` (used by all `Scoring` methods
and the graph), (2) `FreeStartSolve.bestTranslate`, (3) `FreeStartSolve.pinTranslate` (adds
`intervalMargin`/`invariantTol` and an invariant-slack reject). They share the same per-axis interval
math (`tc`, LE/GE/EQ half-plane accumulation, objective-axis pick).
EVIDENCE: all three compute `tc = (t2==null)?1:(op==PLUS?2:0)` and accumulate `-e0/tc` bounds:
`PathTranslation` 59-96, `bestTranslate` 655-668, `pinTranslate` 733-750. The objective-axis picker is
triplicated: `pickBest` 678-685, `pickDelta` 766-770, `PathTranslation.solveAxis` pick 124-135.
IMPACT: simplicity. ~120 lines of duplicated interval-LP logic across two files; a change to EQ or
relative handling must be made in three places.
PROPOSAL: collapse (2) and (3) onto `PathTranslation`, parameterizing the margin and the
invariant-reject as options; keep one translation core.
CONFIDENCE: 0.85.

## A06-11 The joint dual's whole purpose is the fixed-start-infeasible-but-translatable case

LOCATION: `FreeStartImproveNode.improve` 133-144; `FreeStartSolve.jointLadder` 570-641; graph
`translate` node placement (BuiltinGraphs `rTrans`/`translate` 158-159, 273-275).
CLAIM: Because p0 is separable (A06-2), the ONLY thing the joint dual buys over "pinned-solve then
translate" is finding yaws that are infeasible at the fixed reference start but feasible after
translation (the fixed-start yaw solvers self-certify against the reference walls and return null
there). The late `translatedStart` node already covers the easy case (a found path that just needs
shifting).
EVIDENCE: `improve` runs `solveJoint` (joint dual) and, separately, `recoverStart(seedYaws)` +
translate; it adopts whichever is feasible-and-better (133-163). `TranslatedStartNode` only rigid-
translates an existing candidate (TranslatedStartNode 23). The joint dual is reached via `freeImprove`
BEFORE the polishing nodes, `translate` runs AFTER them.
IMPACT: simplicity. Names the exact residual the joint dual must cover, so a collapse can be scoped to
just that case rather than reproducing the full machinery.
PROPOSAL: measure how many corpus free-start captures actually need the joint dual vs
center-pin+translate (A06-14); scope any replacement to the measured residual.
CONFIDENCE: 0.8. DEPENDS-ON: A06-2.

## A06-12 Multi-jump free-start uses a DIFFERENT strategy: pick start on window 0, then pin

LOCATION: `LongRunSolver.solveFree` 128-152, `runHorizon` free retry 238-267.
CLAIM: In the receding-horizon (multi-jump) path, free-start is resolved by calling
`FreeStartSolve.solveJoint` only on the FIRST window (`i==0`), taking `fr.startX/startZ` as the
committed start, then all later windows run pinned. This is not the single-jump "translate at the end"
strategy; the start is fixed after window 0 and never re-optimized against the committed tail.
EVIDENCE: `runHorizon` free retry guarded by `i == 0 && freeBox != null` (line 238); on success
`chosenStart[0/1] = fr.startX/startZ` and the window is re-based (262-265); `solveFree` then replays
the whole chain at the chosen start and certifies (146-149).
IMPACT: correctness/robustness. The chosen start is optimal for window 0's sub-objective (a lead-in
window uses `Z MAX any-feasible`, `Objective` line 252-253), not the global objective, so multi-jump
free-start can leave objective on the table vs a joint (start, full-run) optimum. Consistency gap vs
single-jump.
PROPOSAL: after the horizon commits, run a final PathTranslation over the whole committed chain (A06-2
makes this exact and cheap) to recover any objective the window-0 pick left behind.
CONFIDENCE: 0.75.

## A06-13 FOLDING: p0 is already folded into the DUAL; it is NOT folded into the rest of the pipeline

LOCATION: `CostateDualSolver.FreeP0` (whole), `jointLadder` 518; contrast BuiltinGraphs pinned nodes.
CLAIM: The context-pack target "free-start as extra decision variables in the same convex program" is
ALREADY achieved inside `CostateDualSolver` (p0 is +2 box-bounded vars in the same dual). What blocks
full folding is (a) the dual method needs the P0_SMOOTH proximal hack + sharpening ladder to recover
p0 (an artifact of the subgradient/Newton dual, not the problem), and (b) every OTHER stage
(ClosedFormSolve margin ladder, SlpSolve/TrustRegionLp, RelaxationRecovery, BnB, ILS, WrapWindowIls,
SeamSweep, smoothing) is written for a fixed start and handles p0 only by post-hoc translation +
per-stage feasibility gates.
EVIDENCE: `jointLadder` constructs `CostateDualSolver(..., buildFreeP0(cbox, obj, smooth))` (518) so
the dual carries p0; no other solver takes a `FreeP0`. The pinned nodes never see `freeBox`; only
`freeStartImprove`, `translatedStart`, `recedingHorizon` reference it (grep: GraphContext.freeBox users
are exactly those three nodes + Scoring).
IMPACT: simplicity. A single SOCP/QCQP (disk relaxation `|u_t|<=m_t` + box p0 + linear walls + linear
objective) solved by one interior-point call would fold ALL of this: no P0_SMOOTH, no sharpening
ladder, no translate node, no scored-vs-raw split (A06-7), no dual-reference split (A06-8). This is the
strongest folding case in the free-start territory.
PROPOSAL: prototype the disk-SOCP-with-free-p0 in COPT on `df-chain-free-start.json` and the 4x2 pin;
read per-tick modulus slack (Stage-0 H1/H2) and the p0 recovery; if the disk relaxation is tight the
entire free-start stack collapses to one convex solve.
CONFIDENCE: 0.75 (folding is analytically supported by A06-1/A06-2; tightness is the Stage-0 open
question). DEPENDS-ON: A06-1, A06-2, A06-3.

## A06-14 UNMEASURED: fraction of corpus needing the joint dual vs center-pin+translate

LOCATION: research topic; harness `FreeStartSweepBench.java` (104 captures per context pack).
CLAIM: The hypothesis that "center-pin at the box center, then one PathTranslation pass" solves the
great majority of free-start captures, with the joint dual + sharpening ladder needed only on a small
needle set (4x2-class), is not measured this session.
EVIDENCE: UNMEASURED-HYPOTHESIS. Experiment: for each free-start capture, run (a) `FreeStartSolve.solve`
(center + anchor grid, no joint dual) and (b) `solveJoint`; record feasible/objective/wall-ms for each,
via a direct `java -cp` probe over `core/build/classes` (Gradle swallows env for probes). The delta set
is exactly the residual the joint dual must keep covering (A06-11).
IMPACT: decides whether the joint dual can be demoted to a rare fallback (large simplicity win) or is
broadly load-bearing.
PROPOSAL: run the probe in Stage B/E once the background build settles; pair with the COPT disk-SOCP
reference (A06-13) so each capture has a global optimum to gap against.
CONFIDENCE: 0.6. DEPENDS-ON: A06-2, A06-11, A06-13.

## A06-15 CONSISTENCY MATRIX (caching / smoothing / defaults / dF / free-start)

LOCATION: as cited per row.
CLAIM: Per-capability presence across the free-start-relevant paths:
- CACHING: multi-jump free path has a window cache (`LongRunSolver.WindowCache`) and a per-`we`
  `retryCache` for the free retry (LongRunSolver 138, 239-255). Single-jump free (`solveJoint`) has NO
  solved-yaw cache; it only warm-starts `lambda` across the margin ladder (jointLadder `warm`
  541-577). Divergence: caching present multi-jump, absent single-jump (matches the pinned lane, which
  also has none).
- SMOOTHING: TAS smoothing (`Objective.smoothLambda`, runJob `smoothFacing` 1020-1030, `SmoothingNode`)
  runs on the POST-adoption pinned scenario, so it composes with free-start but at a FIXED start: after
  smoothing perturbs yaws within `objGuard` slack, the start is NOT re-translated (runJob smoothFacing
  is after the graph's `translate` node). Minor inconsistency: start optimal for pre-smoothing yaws.
  Separately, `FreeP0.smooth` (A06-3) is a DUAL conditioner, unrelated to `smoothLambda` despite the
  shared word "smooth" (naming collision).
- DEFAULTS: `FreeStartSolve.Config` defaults set `jointWrapClose=true` (line 24), but
  `FreeStartImproveNode` overrides it to FALSE (FreeStartImproveNode 33). So the direct `solveJoint`
  API and the graph node run different config; the wrap-close repair (jointWrapClose path 154-157,
  `jointWrapClose` 278-307) is reachable only via the direct API / `solveJointBest` tail, never from
  the graph node. Divergence.
- dF (facing): free-start composes with dF via `FacingPrefold` in `jointDispatch` (313-317) and
  `jointLadder`, but the `CostateDualSolver` javadoc (32-33) flags it as fragile ("rigid dF chains have
  no downstream repair, and a near-miss is a lost solve"), and `anchorRotationScan` BAILS on an
  unbounded F constraint (`if c.mode==F && c.t2==null return null`, FreeStartSolve 167-169). So
  free-start + dF is supported-with-caveats, not first-class.
- FREE-START: present by construction here; only derived at `startTick==0` (AngleSolverEngine 376), so
  free-start is unavailable for mid-run segments.
EVIDENCE: file:line as cited inline above.
IMPACT: simplicity/robustness. Concrete gap list for the spec's consistency question: seed-lane vs
center reference (A06-8), scored-vs-raw feasibility (A06-7), jointWrapClose default fork, dF-fragility,
`startTick==0`-only, and the smooth/re-translate ordering.
PROPOSAL: (1) share one reference (A06-8) and one feasibility notion (A06-7); (2) align the node's
Config with the API default or document why jointWrapClose is off in-graph; (3) re-run PathTranslation
after final smoothing so the start stays optimal for the smoothed yaws; (4) record `startTick>0`
free-start as an explicit non-goal or lift it.
CONFIDENCE: 0.85. DEPENDS-ON: A06-3, A06-7, A06-8.
