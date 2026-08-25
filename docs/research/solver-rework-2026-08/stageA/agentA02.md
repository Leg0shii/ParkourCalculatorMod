# Stage A shard A02: byte-exact and linear models, and their reuse

Agent: A02
Territory: JumpLinearModel, ExactJumpModel, JumpConstraintCompiler, JumpConstraint, JumpSpec,
JumpPhysicsInputs, ForwardModel, ForwardPath, Constants; and their reuse across the solve pipeline.

## Files inspected
- core/.../solver/JumpLinearModel.java (whole)
- core/.../solver/ExactJumpModel.java (whole)
- core/.../solver/JumpConstraintCompiler.java, JumpConstraint.java, JumpSpec.java (whole)
- core/.../solver/JumpPhysicsInputs.java, ForwardModel.java, ForwardPath.java, Constants.java, StartBox.java (whole)
- core/.../solver/ClosedFormSolve.java, BucketAscentPolish.java, IlsPolish.java (whole)
- core/.../solver/FreeStartSolve.java (constPos/startBox reconciliation: 66-124, 233-289, 772-794)
- core/.../AngleSolverEngine.java (dualChain 1696-1744; alternateObjectives 1761-1775)
- core/.../graph/CountingForwardModel.java (whole)
- core/src/test/.../anglesolver/InertiaFoldingTest.java, ContinuousDiscreteScreen.java (whole)

## Commands run (direct java -cp against core/build/classes/java/main; classes at 16:18, newer than sources; NO gradle)
- DriftProbe: JumpLinearModel (clamp-free and folded) vs ExactJumpModel max |Δpos| over k=0..n, legacy+modern, n in {6,12,24,49}, straight/sweep/wiggle yaws.
- BuildBench: median-of-8 wall time of `new JumpLinearModel(sc)` (dominated by precompute()).
- ForwardBench: median-of-8 wall time of ExactJumpModel.forward(); and CountingForwardModel eval counts inside BucketAscentPolish FAST/THOROUGH on a synthetic feasible MAX-X spec with one loose Z corridor.
(Numbers below are converted from the machine's comma decimal separator to points.)

---

### A02-1 The folded JumpLinearModel IS the exact affine relaxation of ExactJumpModel, up to float/LUT quantization only
LOCATION: JumpLinearModel.coef/coefAxis/constPos (181-200) vs ExactJumpModel.stepRange (141-346)
CLAIM: With the correct inertia pattern folded in, the linear model reproduces the byte-exact X/Z path to within an n-dependent quantization residual, not a modeling error; the residual is the accumulated float-authoring + sine-LUT drift.
EVIDENCE: DriftProbe, folded model max |Δpos| vs ExactJumpModel: straight (no mid-flight gate) 7.07e-5 (n=6), 1.438e-4 (n=12), 3.031e-4 (n=24), 6.522e-4 (n=49); sweep/wiggle at n=49 fold to 3.318e-4 / 2.040e-4. Per-tick accumulation ~1.2-1.3e-3 milliblock/tick (6.522e-4/49). Legacy and modern differ only in the 5th+ digit. InertiaFoldingTest asserts foldedDiff < 2.0e-3 on loopmm and passes.
IMPACT: correctness/robustness. The handoff figure "~2.6e-4 b over a window" is a point on this curve; the true residual is n-dependent and REACHES 6.5e-4 b at n=49, EXCEEDING the ~1e-4 b certify-floor the context pack cites. Any relaxation-only certification on long windows must budget ~1.3e-5 b/tick, not a flat 1e-4.
PROPOSAL: Treat the linear model as a certified relaxation only up to margin(n) = c*n with c~1.3e-5 b/tick; never certify feasibility from it directly on n>~10; the byte-exact re-check (already done, ClosedFormSolve.violOnExact 473) stays mandatory.
CONFIDENCE: 0.9
DEPENDS-ON: A02-3, A02-8

### A02-2 The ONLY nonconvexity in the linear model is the per-tick modulus equality |u_t| = mMag[t]
LOCATION: JumpLinearModel.precompute 103-138 (mMag/baseArg yaw-independent); objectiveVectors 204-214; compileWall 221-266
CLAIM: mMag[t] and baseArg[t] are computed with no reference to yaw; yaw enters only as the phase phi = baseArg + yaw, so u_t = mMag[t]*e^{i(baseArg+yaw)} traces a fixed-radius circle. Objective and every X/Z wall are exactly linear in the stacked u; the feasible set is a product of per-tick circles (a torus). This matches the context-pack constant-modulus QCQP framing to the letter.
EVIDENCE: precompute sets mMag[t]=hypot(pConst,qConst) (136), baseArg[t]=atan2(pConst,qConst) (137); zeroingPattern is the only place phi=baseArg+yaw*RAD appears (339); objectiveVectors reads a single objTick, single axis (208-213); compileWall returns null for any non-X/Z mode (222), so F/DXZ never enter the linear form.
IMPACT: simplicity. Confirms the whole continuous model is LP + per-tick unit-circle equalities; nothing else in this territory is nonconvex.
CONFIDENCE: 0.95
DEPENDS-ON: none

### A02-3 The inertia gate, unfolded, diverges up to 2.65e-2 b; folding it recovers byte-exactness to the sine residual
LOCATION: coefAxis stop=zNext (186-191); constPos end=zFirst (198-199); velocityWalls 278-302; zeroingPattern 324-345
CLAIM: The clamp-free linear model is wrong by up to 100x the sine residual when the gate fires mid-flight; the pattern fold (zNext/zFirst capping propagation) is load-bearing, and once applied the divergence collapses back onto the float/LUT residual of A02-1.
EVIDENCE: DriftProbe, clamp-free vs folded max |Δpos| at n=49: sweep 1.432e-2 -> 3.318e-4; wiggle 2.654e-2 -> 2.040e-4; n=24 sweep 1.334e-2 -> 1.875e-4. Straight yaws never dip below threshold mid-flight so clamp-free == folded (gate fires only the harmless tick-0 zero of a zero velocity). Confirms the gate is the dominant modeling term when active.
IMPACT: correctness. Justifies ClosedFormSolve's up-to-4-pass pattern fixed-point (solveWithPrefold 309-341): getting the pattern wrong costs ~1e-2 b, far above any wall margin.
CONFIDENCE: 0.9
DEPENDS-ON: A02-1

### A02-4 ExactJumpModel.stepRange(from>0) is dead code: every re-evaluation is a full O(n) forward from tick 0
LOCATION: ExactJumpModel.forward 131 (only caller, from=0); stepRange 141
CLAIM: The incremental-tail recompute the class doc advertises (141-140) is never used anywhere in main/ or test/; the handoff claim is confirmed by grep.
EVIDENCE: `\.stepRange\(` -> No matches (the only textual hits are the internal `stepRange(scenario,yawAbsDeg,0,path)` at line 131 and docs). All 65 `.forward(` call sites across 20 files go through the full O(n) path.
IMPACT: speed. A capability built specifically to make single-facing perturbations O(n-from) is inert while the anytime searchers (A02-6) spend their entire budget on full forwards.
CONFIDENCE: 0.98
DEPENDS-ON: A02-6

### A02-5 JumpLinearModel.precompute() (scenario-only) is rebuilt up to 9x per direction, ~36x per dualChain; nothing is cached
LOCATION: ClosedFormSolve optimizeReturning 120 (linA), solveWithPrefold 330 (throwaway lin per pass), runLadder 361 (lin per pass); AngleSolverEngine.dualChain 1708+1734 (up to 4 objectives)
CLAIM: precompute() computes mMag/baseArg/f4/fPre/sPre, all functions of the scenario ONLY (not the objective, not the zeroing pattern, not the margin), yet a fresh JumpLinearModel is allocated for the prefold analysis, once per inertia pass in runLadder, once more per pass just to call zeroingPattern, and again per alternate objective. Only zNext/zFirst (86-100) depend on the pattern.
EVIDENCE: Construction count, worst-case fallback (maxInertiaPasses=4): 1 (linA) + 4*(1 runLadder + 1 zeroingPattern) = 9 per direction; dualChain reseeds up to 3 alternate objectives (alternateObjectives 1763-1775) => ~36 per solve. BuildBench: `new JumpLinearModel(sc)` = 292 ns (n=12), 498 ns (n=24), 1044 ns (n=49), dominated by the per-tick atan2/hypot/sqrt in precompute.
IMPACT: speed, LOW absolute (~9-36 us/solve at n=49, negligible vs the 0.1-800 ms envelope) but a real simplicity/consistency defect: there is no model cache anywhere; the margin ladder is the ONLY place that avoids rebuild (walls compiled once, margin applied in the dual, warm-started: runLadder 361-397).
PROPOSAL: Split precompute into an immutable JumpLinearBase(scenario) shared across passes/objectives/directions, with a thin pattern view holding only zNext/zFirst and the pattern-dependent coefAxis/constPos/velocityWalls. Objective vectors and wall compilation become pure reads of the shared base. Removes the redundant trig without touching the math.
CONFIDENCE: 0.85
DEPENDS-ON: none

### A02-6 The anytime polishers spend their whole forward() budget on single/two-tick perturbations, the exact stepRange use case
LOCATION: BucketAscentPolish.block1 110-126 (one tick t scanned), block2 129-148 (pair i,j), score 159-165 (full forward); IlsPolish.score 101-107; forward cost ExactJumpModel.forward 114-133
CLAIM: block1 changes a single y[t] and rescoring only requires the tail from t (game facings from t onward, then forward from t); today each candidate does a full O(n) toGameFacings + O(n) forward. This is exactly what stepRange(from=t) plus an incremental toGameFacings would accelerate.
EVIDENCE: ForwardBench: forward() = 504 ns/call at n=49. CountingForwardModel inside one BucketAscentPolish call on a synthetic feasible spec (this schedule): FAST 3.16M evals, THOROUGH 36.1M evals at n=49 (FAST 0.75M / THOROUGH 7.7M at n=12). block1 issues n*(2*win/step+1) full-forward evals per resolution (e.g. THOROUGH b1 {1.5,0.02} = 151/tick*n), looped up to 60x until no-improve, times 6 resolutions, times maxRounds*restarts. Every one of those is a single-tick change.
IMPACT: speed, ~2x achievable on the dominant cost center. Bound: score = toGameFacings O(n) + forward O(n); stepRange fixes only the forward half unless toGameFacings (a trivial prefix accumulation, JumpPhysicsInputs.toGameFacings 189-208) is ALSO made incremental-from-t. Both incremental => average ~2x for uniformly distributed t; block2 with pairSpan=3 keeps i,j adjacent so from=i captures most of the tail.
PROPOSAL: Add an incremental scorer that keeps a persistent ForwardPath + game-facing prefix and recomputes only [t, n) on a single/two-tick kick, calling the already-present stepRange. Guard byte-identity with the existing InertiaFoldingTest-style equivalence check.
CONFIDENCE: 0.8
DEPENDS-ON: A02-4

### A02-7 The gate fold is consistent between the linear position map, the velocity walls, and the exact top-of-tick gate (test-proven), with one boundary-prediction risk
LOCATION: zeroingPattern 324-345 vs ExactJumpModel gate 152-160; coefAxis stop=zNext 186-191; velocityWalls coefHi 285-289; keepAliveWall 307-322
CLAIM: zeroingPattern replays the exact gate ordering (test |v| at top of tick, then add mMag*cos/sin, then *f4) so it predicts the SAME pattern ExactJumpModel would fire for given yaws; coefAxis, velocityWalls, and keepAliveWall all cut propagation at zNext consistently; the position fold and the velocity fold use the same coefficients.
EVIDENCE: InertiaFoldingTest.keepAliveWallIsTheComplementOfTheZeroingWall asserts keep.coef[s] == velocityWalls-mirror.coef[s] exactly and keep.bPrime == mirror.bPrime - 2*thr to 1e-15, and that keep-alive slack reads the byte-exact velocity to 2e-3 (test passes). zeroingPattern's gate test at 331-336 mirrors stepRange 153-159 (per-axis vs combined branch identical).
RISK: zeroingPattern computes the carried velocity in DOUBLE Math.cos/sin, while the exact gate fires on the FLOAT-chain velocity; near a threshold crossing the predicted pattern can differ from the realized one by the A02-1 residual. UNMEASURED-HYPOTHESIS: instrument a case where a tick's |v| sits within ~1e-4 of inertiaThreshold and compare predicted zeroingPattern vs realized ExactJumpModel gate; expected to be the source of the multi-pass pattern fixed-point occasionally not converging.
IMPACT: correctness (boundary only). The 4-pass fixed point (solveWithPrefold) and the byte-exact re-check absorb it today.
CONFIDENCE: 0.8
DEPENDS-ON: A02-3

### A02-8 The linear model hardcodes LEGACY diagonal input authoring; it diverges from ExactJumpModel on modern/sine262 force-45 ticks
LOCATION: JumpLinearModel.precompute 122-131 vs ExactJumpModel step (4) 220-268 (SQUARE_DIAG_INPUT 34-46)
CLAIM: precompute always uses strafe0 = strafeSign*0.98 and a DOUBLE sqrt normalization with raw<1->1, with no branch on modern or sine262. ExactJumpModel, on sine262, replaces both forward and strafe on a force-45 tick with the precomputed SQUARE_DIAG_INPUT constant, and on legacy uses a FLOAT sqrt chain. So mMag[t]/baseArg[t] on a 45-strafe tick are wrong for the 26.x sine262 era and float-rounded for legacy.
EVIDENCE: Code divergence is certain: linear precompute has no `modern`/`sine262` reference at all (only the JumpPhysicsInputs it reads); ExactJumpModel branches at 223 (sine262) and 232 (modern). For W-only and >unit diagonals both formulas coincide algebraically (verified by hand: sub-unit raw->1 == modern no-normalize; over-unit normalize matches); the exposure is specifically sine262 45-strafe ticks (SQUARE_DIAG_INPUT != 0.98-diag) plus float-vs-double rounding folded into A02-1's residual.
IMPACT: correctness, narrow. Magnitude on sine262 45-strafe ticks is UNMEASURED (no force-45 sine262 capture probed here); flag as re-verify with a 26.x capture that authors a force-45 tick.
PROPOSAL: Either pass modern/sine262 into JumpLinearModel and mirror ExactJumpModel step (4) exactly, or document the linear model as legacy-diagonal-only and refuse the closed-form path when sine262 && any force-45 tick.
CONFIDENCE: 0.75
DEPENDS-ON: A02-1

### A02-9 constPos reads StartBox; ExactJumpModel.forward reads startPos; they agree only under the copyWithStart invariant
LOCATION: JumpLinearModel.constPos 194-200 (box preferred over startPos); ExactJumpModel.forward 123-128 (startPos/initialVelocity only); FreeStartSolve.copyWithStart 776-794
CLAIM: constPos folds box.px/pz/vx/vz as the constant when a box is present, but the byte-exact forward always seeds from scenario.startPos/initialVelocity and never reads startBox. Consistency holds only because free-start resolves to copyWithStart, which sets startPos AND StartBox.pinned to the SAME point before any inner solve; the model does not enforce this coupling.
EVIDENCE: constPos 196-197 `box != null ? box.px : sc.startPos.x`; forward 123-124 uses scenario.startPos. copyWithStart (776-781) sets a.startPos=(p0x,p0z) and a.startBox=StartBox.pinned(p0x,p0z,...); FreeStartSolve builds refSc=copyWithStart(base,refX,refZ) at box center (118-122) and passes a SEPARATE non-pinned cbox for translation sensitivity, so the model's constPos is always evaluated at a pinned start == startPos while free-start freedom rides Wall.p0coef (compileWall 248-249) and candidateThetas halfX/halfZ (ClosedFormSolve 195-196, 225).
IMPACT: correctness/consistency. Answers context-pack question 1: free-start behaves IDENTICALLY to a fixed-start solve once the point is picked, because the inner path is the same pinned ClosedFormSolve. The latent risk is a future caller building a JumpLinearModel with a box whose px != startPos.x; constPos would then silently disagree with forward by that offset.
PROPOSAL: Assert box.px==startPos.x (and vx==initialVelocity.x) in the JumpLinearModel constructor when a box is present, or drop the box read from constPos and always use startPos (the translation sensitivity is already carried separately by p0coef).
CONFIDENCE: 0.8
DEPENDS-ON: none

### A02-10 The linear+exact model layer is smoothing-blind and dF-blind; both enter only the byte-exact scorer
LOCATION: JumpLinearModel.objectiveVectors 204-214 (no smoothing term); compileWall 222 (rejects F-mode); JumpConstraintCompiler.evaluate 79-80 (F handled) ; IlsPolish.score 106 / BucketAscentPolish.score 164 (smoothPenalty added post-forward)
CLAIM: The dual/closed-form path optimizes RAW position only; the turn/jerk smoothing objective and all facing (F/dF) constraints are invisible to JumpLinearModel and the CostateDualSolver, appearing only when the byte-exact scorer evaluates a candidate.
EVIDENCE: objectiveVectors builds cx/cz purely from coefAxis*+-1, no reference to startYaw or turn cost; compileWall returns null for mode != X/Z (222) and JumpLinearModel.hasFacingWall (348-351) exists precisely so callers bail; the smooth penalty is added only in the search scorers (obj.smoothPenalty at IlsPolish 106, BucketAscentPolish 164), never in the model. F constraints are evaluated by JumpConstraintCompiler on the realized game facings (79-80), never compiled to a wall.
IMPACT: simplicity/consistency. Explains a structural gap for the spec: the fast convex path cannot see smoothing or dF, so any smoothing/dF requirement forces the slower search/prefold stages; the two capabilities are implemented on the byte-exact side only, not in the model.
CONFIDENCE: 0.9
DEPENDS-ON: none

### A02-11 The two models cannot unify byte-exactly; the linear model already IS the single source of the COEFFICIENT math, and should stay so
LOCATION: JumpLinearModel.coef 181-184 vs ExactJumpModel.stepRange 141-346
CLAIM: A single model cannot be both the affine relaxation and byte-exact: ExactJumpModel diverges from any closed-form affine map by (a) the 65536-bucket float sine LUT vs Math.trig, (b) the float moveFlying/movementInputToVelocity chain vs double, and (c) a STATE-dependent gate whose firing depends on realized (not relaxed) velocity. All three are essential to byte-exactness and all three break linearity. The correct architecture is exactly the current one: linear model = relaxation for the dual/coefficient math, exact model = ground truth for scoring/certification.
EVIDENCE: measured divergence A02-1 (float/LUT) and A02-3 (gate); the gate's chicken-and-egg (pattern depends on yaws) forces the fixed-point loop (ClosedFormSolve 309-341). ExactJumpModel is a straight float port (docstring 5-21).
IMPACT: simplicity verdict. Do NOT try to make one model serve both roles. The achievable consolidation is A02-5 (share precompute) and A02-6 (route perturbation rescoring through stepRange), not a merge.
CONFIDENCE: 0.85
DEPENDS-ON: A02-1, A02-3

### A02-12 Consistency matrix for this territory
LOCATION: as cited per cell
CLAIM: present/absent capabilities across the model layer:
- Caching: ABSENT everywhere. Every use is `new JumpLinearModel(...)` (28 call sites via grep); no model or precompute cache. Sole reuse: the margin ladder warm-starts the dual and compiles walls once (ClosedFormSolve.runLadder 361-397). ExactJumpModel.stepRange incremental reuse EXISTS but is never called (A02-4).
- Smoothing-awareness: ABSENT in JumpLinearModel.objectiveVectors (204) and CostateDualSolver inputs; PRESENT only in the byte-exact scorers (IlsPolish 106, BucketAscentPolish 164) (A02-10).
- dF / facing: ABSENT in JumpLinearModel (compileWall rejects F, 222); PRESENT only in JumpConstraintCompiler.evaluate (79) on realized facings; facing pins/chains handled upstream by FacingPrefold, not the model.
- Free-start: constPos reads StartBox center/pinned (194-200); ExactJumpModel reads startPos (123); reconciled by copyWithStart pinning both to one point (A02-9). Inner solve identical to fixed-start.
- Defaults: JumpPhysicsInputs getters default to the legacy sprint-jump assumption (sprintAt->true 135, forwardAt->0.98 122, strafeInputAt->0 129, jumpAt->single jumpTick 161); JumpLinearModel.precompute consumes these but ALSO hardcodes the legacy diagonal irrespective of the modern/sine262 era (A02-8).
EVIDENCE: the cited lines and the grep of `new JumpLinearModel`.
IMPACT: simplicity/consistency map for the spec's gap enumeration.
CONFIDENCE: 0.85
DEPENDS-ON: A02-4, A02-8, A02-9, A02-10
