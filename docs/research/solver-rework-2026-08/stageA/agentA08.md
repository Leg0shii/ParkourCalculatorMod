# Agent A08 shard: the four smoothing stages and the objective

Agent id: A08
Territory: `solver/Objective.java`, `solver/DeWiggle.java`, `solver/SmoothingPolish.java`,
`solver/SmoothFaceRecovery.java`, support `solver/BucketAscentPolish.java`, `FacingLattice.java`,
`YawTies.java`, `Angles.java`, and the smoothing wiring in `AngleSolverEngine`, `graph/nodes/SmoothingNode`,
`graph/BuiltinGraphs`, `graph/Scoring`, `graph/nodes/*` ranking sites.

Files inspected (all under `core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/`, plus one test):
`solver/Objective.java`, `solver/DeWiggle.java`, `solver/SmoothingPolish.java`, `solver/SmoothFaceRecovery.java`,
`solver/Angles.java`, `solver/BucketAscentPolish.java`, `solver/FacingLattice.java`, `solver/YawTies.java`,
`solver/SolveProgress.java`, `solver/ClosedFormSolve.java` (recoverFace only), `AngleSolverEngine.java`,
`AngleSolverState.java`, `graph/nodes/SmoothingNode.java`, `graph/BuiltinGraphs.java`, `graph/NodeCatalog.java`,
`graph/Scoring.java`, `ui/anglesolver/AngleSolverWindow.java`,
`core/src/test/.../anglesolver/FaceSmoothScreen.java`; handoff `docs/research/smooth-and-convergence-handoff-2026-08-24.md`.

Commands run: compiled+ran a standalone `MetricProbe` against `core/build/classes/java/main`
(`java -cp ".;<coreMain>" MetricProbe`) to measure the four metric functions on synthetic yaw sequences.
Gradle NOT invoked (per instruction). Ablation numbers that require the slow hpk suite are labeled
re-verify-hypothesis with the reproduction command.

---

## Section 1: what each stage computes, its metric, its accept-gate

### A08-1: Objective.turnCost is a reversal-count ranking bias, not a post-pass
- LOCATION: `Objective.java:28-37`, `Angles.turnCost` `Angles.java:88-94`, injected via `Objective.scored`,
  `SolveProgress.scoredOf` `SolveProgress.java:115-119`, `Scoring.scoredObjective` `Scoring.java:64-72`.
- CLAIM: turnCost adds `smoothLambda * (90.0 * reversals(anchor, yaws) + 0.02 * wiggleDeg(anchor, yaws))` to
  the ranked objective, where `reversals` counts sign changes of the per-tick wrapped delta (floor 0.01deg)
  and `wiggleDeg` is the total variation of the first difference (= L1 of the second difference of yaw).
- EVIDENCE: `REVERSAL_COST_DEG=90.0`, `RATE_TIEBREAK=0.02` (`Angles.java:69-70`); `REVERSAL_FLOOR_DEG=0.01`
  (`Angles.java:39`). Probe measured turnCost of a dithering tail = 360.27 = 90*4 reversals + 0.02*13.5
  wiggle; the reversal term (360.0) is 1333x the wiggle tiebreak (0.27).
- IMPACT: smoothness. It is the ONLY smoothing that steers the SEARCH (all `scored*` ranking sites); the
  other three run after a winner is chosen.
- PROPOSAL: treat turnCost as the "search-side" smoothness surrogate and the other three as "post-pass" ones
  when reasoning about unification (A08-11).
- CONFIDENCE: 0.98
- DEPENDS-ON: A08-6.

### A08-2: DeWiggle removes short same-sign turn runs; metric = run count; gate = MAX_GIVE_BACK objective floor
- LOCATION: `DeWiggle.java:16-95` (run loop), `usable` `168-178`, `repair` `97-166`, `smoothMetric` `223-244`.
- CLAIM: DeWiggle splits the facing path into maximal same-sign turn runs, flattens each run whose absolute
  arc is below `MIN_ARC_DEG=45.0` to a constant turn rate, and restores byte-exact feasibility with a
  Gauss-Newton step whose correction size is measured in the SECOND-difference metric
  `W=(D2^T D2 + eps I)^-1` (`METRIC_EPS=5e-3`). A candidate is kept only if strictly feasible
  (`FEAS_TOL=0`), carries FEWER runs, and its objective is >= `objFloor = objAt(input) - MAX_GIVE_BACK`.
- EVIDENCE: `MAX_GIVE_BACK=8.0e-3` (`DeWiggle.java:29`, public static); `objFloor` at `DeWiggle.java:40-41`;
  fewer-runs gate `runs(...).size() >= arcs -> continue` at `69/74`; give-back gate at `77`. The metric it
  MINIMIZES (run count = reversals+1) is the same non-convex object as turnCost's dominant term.
- IMPACT: smoothness (dominant stage per handoff, see A08-5); simplicity.
- PROPOSAL: see A08-9 (it and turnCost and the face-walk are three heuristics for the SAME objective).
- CONFIDENCE: 0.97
- DEPENDS-ON: A08-6, A08-9.

### A08-3: SmoothingPolish minimizes sum-of-squared first differences (convex quadratic); runs unconditionally
- LOCATION: `SmoothingPolish.java:48-84` (smooth loop), `roughness` `86-96`, `accepts` `197-206`.
- CLAIM: SmoothingPolish descends `roughness = sum_t wrapDelta(y_t - y_{t-1})^2` (anchored at startYaw), i.e.
  the squared L2 norm of the first difference of yaw, via bisected single-tick, joint-pair, and
  anti-symmetric transfer pulls toward neighbor midpoints. Gate: strictly feasible AND strictly lower
  roughness AND at smoothLambda=0 `e <= floor` (exact objective preservation, no give-back), at
  smoothLambda>0 `e <= floor + MAX_GIVE_BACK` with a scored-value ratchet.
- EVIDENCE: `roughness` sum of `d*d` (`SmoothingPolish.java:90-94`); gate branches `accepts` `200-205`;
  `MAX_GIVE_BACK=8.0e-3` (`SmoothingPolish.java:46`, public static). SmoothingNode calls it UNCONDITIONALLY
  (`SmoothingNode.java:48/51`), unlike DeWiggle which is gated by `smoothLambda>0` (`SmoothingNode.java:37`).
- IMPACT: smoothness (weak: ~5% per handoff; measured-blind to reversals, A08-9).
- PROPOSAL: this is the one genuinely convex metric in the stack (a Tikhonov first-difference penalty), but
  it is the WRONG convex surrogate for reversals (A08-6/A08-9).
- CONFIDENCE: 0.97
- DEPENDS-ON: A08-6, A08-9.

### A08-4: SmoothFaceRecovery walks the wall null space toward fewer reversals; metric = reversals then jerk; gate = SMOOTH_OBJ_SLACK wall + frozen pins
- LOCATION: `SmoothFaceRecovery.java:146-219` (`smooth`/`faceWalk`), `reversals` `323-333`, `jerkOf` `335-340`,
  `tangentProjector` `450-487`, `restore`/`restoreExact` `230-305`; wired via
  `ClosedFormSolve.recoverFace` `ClosedFormSolve.java:72-79` and `AngleSolverEngine.smoothFacing` `1053-1085`.
- CLAIM: on the final winning result it flattens the smallest-mass run, projects the flatten direction onto
  the tangent (null space) of the active walls, Gauss-Newton-restores exact feasibility under the
  second-difference metric `W`, and accepts only when reversals do not increase and (reversals drop OR jerk
  strictly drops). jerk = `sum |d_t - d_{t-1}|` = TV of the first difference (L1 of the second difference).
  Objective give-back is bounded by an `objGuard` wall at `achieved - SMOOTH_OBJ_SLACK`; pinned ticks are
  held via `FacingPrefold` frozen mask; budget is time-bounded.
- EVIDENCE: reversal gate `rv > curRev -> continue`, jerk tiebreak `rv==curRev && jk>=curJerk -> continue`
  (`SmoothFaceRecovery.java:210-211`); `SMOOTH_OBJ_SLACK=3.0e-4` and objGuard build
  (`AngleSolverEngine.java:1064-1070,1090`); frozen from FacingPrefold (`1073-1078`); budget
  `deadline/8` cap `MAX_SMOOTH_BUDGET_NANOS=6s`, else `SMOOTH_BUDGET_NANOS=400ms`
  (`AngleSolverEngine.java:1021-1022,1088-1089`); `restoreMargin=5e-3`, metric `W_EPS=5e-3`
  (`SmoothFaceRecovery.java:20,15`).
- IMPACT: smoothness (decisive on multi-jump per handoff; +11 single-jump).
- PROPOSAL: its primary metric (reversal count) overlaps DeWiggle/turnCost; its tiebreak (jerk = TV(D2)) is
  the convex surrogate that SmoothingPolish's L2 metric misses.
- CONFIDENCE: 0.96
- DEPENDS-ON: A08-6, A08-9.

### A08-5: give-back constants re-verified from code; the 58-hpk ablation magnitudes are handoff-sourced, not re-run here
- LOCATION: handoff `smooth-and-convergence-handoff-2026-08-24.md:172-193`; harness
  `core/src/test/.../anglesolver/FaceSmoothScreen.java:30-86`.
- CLAIM: every CODE constant behind the ablation is re-verified exact: `MAX_GIVE_BACK=8e-3` (DeWiggle+Polish),
  `SMOOTH_OBJ_SLACK=3e-4`, `TASER_SMOOTH_LAMBDA=1e-2`, `REVERSAL_COST_DEG=90`, `RATE_TIEBREAK=0.02`. The
  ablation MAGNITUDES (DeWiggle +72 reversals, face-walk +11 single / decisive multi, turnCost and
  SmoothingPolish ~5% each, floor->sumRev curve off 255 / 8e-3 274 / 2e-3 288 / 1e-3 296 / 5e-4 319,
  130->98, thousand 15->10) are copied from the handoff and NOT independently re-run.
- EVIDENCE: constants confirmed at the file:lines in A08-1..A08-4 and `AngleSolverState.java:127`,
  `AngleSolverWindow.java:220-221`. FaceSmoothScreen is env-gated (`PKC_SCREENS=1`), sweeps `/captures/hpk`,
  prints `Sum reversals over both-certify: ladder=.. face=..`.
- IMPACT: correctness of this shard's provenance.
- PROPOSAL (re-verify experiment): run `PKC_SCREENS=1 ./gradlew :core:test --tests '*FaceSmoothScreen'
  -PslowTests` for the face-walk-vs-ladder number; re-run the full-stage ablation by toggling the two
  `public static MAX_GIVE_BACK` fields and the SmoothingNode `deWiggle` param and re-reading reversal sums.
- EVIDENCE TAG: UNMEASURED-HYPOTHESIS (magnitudes only; constants are measured).
- CONFIDENCE: 0.9 (constants), 0.6 (unre-run magnitudes)
- DEPENDS-ON: none.

---

## Section 2: THE MISSION QUESTION - can smoothing be ONE convex term over the existing program?

### A08-6: measured metric decomposition - the reversal count is non-convex and dominant; the L2-D1 metric is convex but blind to reversals; TV(D2) is the sensitive convex surrogate
- LOCATION: probe over `Angles.reversals/wiggleDeg/turnCost` and `SmoothingPolish.roughness`.
- CLAIM: on two yaw tails with IDENTICAL per-tick step magnitudes, one dithering (`+.5 -.5 +.5 -.5`) and one
  monotone (`+.5 +.5 +.5 +.5`), the reversal count and turnCost separate them sharply while the
  sum-of-squared-first-differences (SmoothingPolish's metric) CANNOT tell them apart; only TV of the second
  difference (jerk/wiggle) is both convex and reversal-sensitive.
- EVIDENCE (measured, MetricProbe): dither -> reversals=4, wiggle(TV of D2)=13.5, turnCost=360.27,
  roughness(sumD1^2)=401.25; mono(same abs steps) -> reversals=0, wiggle=9.5, turnCost=0.19,
  roughness=401.25; ramp(monotone) -> reversals=0, wiggle=0, turnCost=0, roughness=600. Roughness is
  identical (401.25) for dither vs mono; that is the measured blindness.
- IMPACT: simplicity/correctness. Names exactly which convex object each stage targets.
- PROPOSAL: any single-term smoothness must be a TV-of-turn-rate (`||D2 theta||_1`) surrogate, not an L2-D1
  penalty; the L2-D1 penalty (SmoothingPolish) provably cannot suppress the load-bearing dither.
- CONFIDENCE: 0.95
- DEPENDS-ON: none.

### A08-7: per-stage convex-term mapping, and the tightness obstruction (convex in theta, NOT in the relaxation's u-variables)
- LOCATION: model math `context-pack 00 sec 2`; stage metrics A08-1..A08-4.
- CLAIM: mapping each stage's metric to its convex correspondent:
  - turnCost dominant term = cardinality of sign changes of `D1 theta` = a reversal COUNT: NON-convex (L0).
    Its tiebreak `wiggleDeg` = `||D2 theta||_1`: CONVEX.
  - DeWiggle metric = run count (= reversals + 1): NON-convex (same L0 object).
  - SmoothingPolish metric = `||D1 theta||_2^2`: CONVEX quadratic (Tikhonov), but the wrong surrogate (A08-6).
  - SmoothFaceRecovery metric = reversal count (NON-convex) tiebroken by `||D2 theta||_1` (CONVEX).
  The tightest convex surrogates for "few reversals" are `||D1 theta||_1` (TV of yaw, promotes
  piecewise-constant heading) and `||D2 theta||_1` (TV of turn rate, promotes piecewise-linear heading, i.e.
  few kinks/reversals). Both are convex IN theta. THE OBSTRUCTION: the existing CONVEX program is written in
  the input variables `u_t in R^2` (the SOCP disk `|u_t|<=m_t` / the dual costate space), and
  `theta_t = atan2(uz_t, ux_t) - baseArg_t` is a NON-convex, non-smooth function of `u`. A term convex in
  theta is therefore NOT convex in `u`. Adding it also destroys the LCvx / constant-modulus hidden-convexity
  that makes single-jump recovery exact (that tightness needs the objective and walls LINEAR in `u`, so
  `u*_t = m_t g_t / |g_t|`); a smoothness term couples adjacent `u_t` through their ANGLES, which is neither
  linear nor convex in `u`, so the dual is no longer guaranteed tight and the closed-form recovery breaks.
- EVIDENCE: metric definitions measured/read (A08-1..A08-4, A08-6); the u-space linearity requirement for
  tightness is the context-pack section 2 LCvx statement, applied to our model (theoretical, model-anchored).
- IMPACT: correctness/simplicity - this is the decisive answer to target capability 3.
- PROPOSAL: do NOT attempt to layer a yaw-smoothness term into the convex (u-space/dual/SOCP) relaxation;
  it cannot preserve relaxation tightness. Express smoothness only in theta-space (A08-8).
- CONFIDENCE: 0.85
- DEPENDS-ON: A08-6; Stage 0 H1/H2 (whether the u-space relaxation is even tight on multi-jump).

### A08-8: single-term collapse IS possible, but only in the nonconvex theta-space post-solve, replacing all four metrics with one weighted TV-of-turn-rate objective
- LOCATION: consequence of A08-6/A08-7.
- CLAIM: the four stages' distinct metrics collapse to ONE objective term
  `S(theta) = beta * ||D2 theta||_1 + gamma * ||D1 theta||_2^2` (both convex in theta; the first is the
  reversal-sensitive surrogate, the second a mild spreader), optimized jointly with the byte-exact walls in
  theta-space. This is a SINGLE objective TERM but NOT a term over the existing CONVEX program: the walls are
  trig (nonconvex) in theta, so the overall solve stays nonconvex and still needs a local/search method. The
  reason there are four post-passes is not that the metric is irreducible; it is (a) the object three of them
  actually chase (reversal count) is combinatorial, so each is a different greedy heuristic for the same L0,
  and (b) they must run AFTER a byte-exact solve to hold exact feasibility, because the convex relaxation
  cannot carry a yaw-smoothness term (A08-7).
- EVIDENCE: A08-6 shows `||D2 theta||_1` is the only tested convex metric that separates dither from mono;
  A08-1/A08-2/A08-4 show three stages already minimize the reversal count with `||D2 theta||_1` as tiebreak.
- IMPACT: simplicity (4 metrics -> 1), robustness (one consistent objective). Not a speed regression risk if
  it replaces the existing greedy passes with one descent on the same object.
- PROPOSAL: prototype a single theta-space descent on `S(theta)` under the byte-exact walls (a convex-metric
  proximal / TV-flavored local search) and A/B its reversal sums against the current four-pass stack on the
  hpk corpus before proposing a collapse. Route to Stage E COPT for the reference reversal-minimal feasible
  path per capture.
- CONFIDENCE: 0.72
- DEPENDS-ON: A08-6, A08-7.

---

## Section 3: duplication / contradiction / give-back double-counting

### A08-9: three of four stages target the SAME nonconvex reversal-count object; SmoothingPolish is the odd one and is measured-blind to reversals
- LOCATION: A08-1 (turnCost), A08-2 (DeWiggle), A08-4 (face-walk) all minimize reversal/run count;
  A08-3 (SmoothingPolish) minimizes `||D1 theta||_2^2`.
- CLAIM: turnCost, DeWiggle, and the face-walk are three heuristics for the identical combinatorial object
  (reversal count of `D1 theta`): a search-ranking penalty, a run-flattening pass, and a null-space walk
  respectively. That is real triplication of a metric, not four independent goals. SmoothingPolish optimizes
  a different (convex L2) object that the probe shows cannot distinguish a dither from a monotone step of the
  same magnitude, which is why the handoff rates it "weak, overlapping": it is not weak-because-redundant, it
  is weak-because-wrong-metric.
- EVIDENCE: probe roughness(dither)=roughness(mono)=401.25 vs reversals 4 vs 0 (A08-6). Metric definitions
  A08-1..A08-4.
- IMPACT: simplicity (identifies the collapse target); explains the ablation overlap.
- PROPOSAL: replace turnCost + DeWiggle + face-walk-metric with one `||D2 theta||_1` term (A08-8); keep an L2
  spreader only if measured to help. Do not keep SmoothingPolish as a reversal remover; it is not one.
- CONFIDENCE: 0.85
- DEPENDS-ON: A08-6.

### A08-10: give-back caps STACK - each pass floors against its OWN input, so total give-back double-counts to ~1.63e-2 b
- LOCATION: DeWiggle `objFloor = objAt(input) - MAX_GIVE_BACK` (`DeWiggle.java:40-41`);
  SmoothingPolish `floor = eval(input)` then `e <= floor + MAX_GIVE_BACK` (`SmoothingPolish.java:60-61,201`);
  face-walk `rhs = achieved - SMOOTH_OBJ_SLACK` where `achieved = forward(post-Polish yaws)`
  (`AngleSolverEngine.java:1063-1067`).
- CLAIM: the three post-pass caps are each relative to the objective at that pass's INPUT, not a shared
  original-solve reference. They run in series (DeWiggle -> SmoothingPolish inside SmoothingNode, then
  face-walk in the engine), so worst-case total give-back = 8e-3 (DeWiggle) + 8e-3 (SmoothingPolish, only at
  smoothLambda>0) + 3e-4 (face-walk) = 1.63e-2 b. The handoff's "total give-back ~= 2*floor + 3e-4" is the
  same double-count, confirmed from code.
- EVIDENCE: the three floor expressions above each read `objAt/eval/forward` of their own input; no shared
  baseline is threaded. SmoothingPolish spends nothing at smoothLambda=0 (`e <= floor`, `accepts:200`), so
  the stack is 8e-3+8e-3+3e-4 only with the Smooth checkbox on.
- IMPACT: correctness/robustness (uncontrolled objective loss up to ~1.6e-2 b, above the ~1e-4 b noise floor
  by 160x); this is a real defect for target capability 5.
- PROPOSAL: floor every pass against ONE shared reference (the original pre-smoothing solve objective) so
  total give-back equals a single budget X exactly, per the handoff's own "cleanest" recommendation.
- CONFIDENCE: 0.9
- DEPENDS-ON: none.

---

## Section 4: readiness for the single "max give-back" slider

### A08-11: 3 of 4 stages are slider-ready; turnCost is the obstruction (a search-ranking bias at ~8 sites, not a post-pass gate)
- LOCATION: `DeWiggle.MAX_GIVE_BACK` (`DeWiggle.java:29`), `SmoothingPolish.MAX_GIVE_BACK`
  (`SmoothingPolish.java:46`), `SMOOTH_OBJ_SLACK` (`AngleSolverEngine.java:1090`) - all mutable statics;
  turnCost consumed at `Objective.scored`, `SolveProgress.scoredOf`, `Scoring.scoredObjective`, and the node
  rankers `IlsPolishNode.java:41-42`, `DualChainNode.java:75-76`, `BnbNode.java:72-73`,
  `SeamSweepNode.java:54-55`, `FreeStartImproveNode.java:110/137/152`, `WrapIlsNode.java:48`.
- CLAIM: the three post-pass caps are already single tunable statics; one slider can drive all three today.
  turnCost is NOT a post-pass gate - it is a soft `obj - lambda*turnCost` bias applied at every ranking site,
  so bounding its give-back requires a HARD `obj >= best - X` constraint threaded through the search, not a
  static edit. That is the concrete obstruction to a single shared-budget slider. The shared-reference fix
  (A08-10) additionally requires passing the pre-smoothing objective into each pass.
- EVIDENCE: the ranking sites above all call `scored*`, which fold in `smoothLambda*turnCost`; there is no
  single choke point where turnCost's objective spend can be capped.
- IMPACT: simplicity - names precisely what blocks the planned unification.
- PROPOSAL: convert turnCost from a scored bias to a hard search constraint `obj >= originalObjective - X`
  plus a pure reversal-count tiebreak, then the same X drives all four stages against one reference.
- CONFIDENCE: 0.85
- DEPENDS-ON: A08-10.

---

## Section 5: consistency matrix (checkbox vs effort; caching / dF / free-start / defaults)

### A08-12: gating and capability matrix per stage, with file:line
- LOCATION: as cited per row.
- CLAIM (each stage: trigger / effort-scaling / dF handling / free-start / caching / defaults):
  - turnCost: gated by `smoothLambda>0` (Smooth (TAS) checkbox, `AngleSolverWindow.java:220-221`,
    `TASER_SMOOTH_LAMBDA=1e-2` `AngleSolverState.java:127`); NOT effort-gated; independent of dF (pure on
    yaws); free-start OK (`Scoring.scoredObjective` adds translation before scoring, `Scoring.java:65-72`);
    no cache; defaults 90/0.02.
  - DeWiggle: gated by node param `deWiggle` AND `smoothLambda>0` (`SmoothingNode.java:37`); present in
    `fast()`.smooth and optimize.smoothFinal (deWiggle=true, `BuiltinGraphs.java:34,160-161`), ABSENT in
    `smoothWarm` (deWiggle default false, `BuiltinGraphs.java:86`, catalog default false
    `NodeCatalog.java:258`); NOT effort-scaled; dF walls IGNORED by its repair (A08-14); free-start via
    `spec.asScenario()` on the translated start (A08-13); no cache; cap 8e-3.
  - SmoothingPolish: runs UNCONDITIONALLY in SmoothingNode regardless of smoothLambda
    (`SmoothingNode.java:48/51`); at lambda=0 pure objective-preserving tie-break, at lambda>0 uses cap 8e-3;
    present in all three smoothing nodes; `countEvals` true in fast/optimize, false in warm
    (`BuiltinGraphs.java:34,86,160`); full-violation gate includes dF walls; free-start via translated
    scenario; no cache; defaults maxRounds=24, maxEvals=24000, pairSpan=3, fractions 1,.5,.25,.125
    (`NodeCatalog.java:259-262`).
  - SmoothFaceRecovery: gated in the ENGINE not the graph -
    `smoothRequested = smoothLambda>0 || SMOOTH_FINAL_FACING` AND `smoothFinalResult` AND
    `model instanceof ExactJumpModel` AND `!cancel` (`AngleSolverEngine.java:1019-1020`); run-ticks search
    candidates pass `smoothFinal=false` (`solve(effort,false)`, `AngleSolverEngine.java:481-483`);
    effort-SCALED budget `deadline/8` cap 6s else 400ms (`1021-1022`); dF walls RESPECTED (`build` handles
    `Mode.F`, `SmoothFaceRecovery.java:84-89`); free-start via translated scenario; metric cache
    `metricCache/metricN` for `W` (`SmoothFaceRecovery.java:376-393`); frozen pins via FacingPrefold
    (`1073-1078`); cap `SMOOTH_OBJ_SLACK=3e-4`.
- EVIDENCE: file:lines inline.
- IMPACT: correctness/simplicity - enumerates the consistency gaps for the spec.
- PROPOSAL: fold into the spec's consistency table; the two live gaps are A08-13 and A08-14.
- CONFIDENCE: 0.9
- DEPENDS-ON: none.

### A08-13: run-ticks warm path does NOT DeWiggle (deWiggle default false on smoothWarm), and SmoothingPolish runs even with Smooth off
- LOCATION: `BuiltinGraphs.java:86` (`smoothWarm` sets only `countEvals=false`, so `deWiggle` stays catalog
  default false `NodeCatalog.java:258`); `SmoothingNode.java:48/51` (SmoothingPolish unconditional).
- CLAIM: two inconsistencies. (1) The warm-start / receding-horizon warm node smooths with SmoothingPolish
  but never DeWiggles, while the cold fast and optimize paths do both, so identical yaws get different
  smoothing depending on which graph branch produced them. (2) SmoothingPolish runs on EVERY solve through a
  SmoothingNode even when the Smooth checkbox is off; at lambda=0 it is a strict objective-preserving
  roughness tie-break (no give-back), so "Smooth off" is not a true no-op for the mid-solve stage (only the
  face-walk is fully gated off). The handoff's "Smooth off => byte-identical microsecond solver" holds only
  because at lambda=0 SmoothingPolish accepts a move solely on `roughness` decrease AND exact objective
  equality, so the objective is preserved but the yaw path can still change.
- EVIDENCE: node param defaults and the unconditional call cited above; `accepts:200` `e <= floor` at
  lambda=0.
- IMPACT: robustness/simplicity - a path-dependent smoothing gap and a mislabeled "off".
- PROPOSAL: make deWiggle consistent across smoothing nodes (or drive all from smoothLambda), and gate
  SmoothingPolish's activation on smoothLambda>0 if "Smooth off" should mean no yaw perturbation.
- CONFIDENCE: 0.85
- DEPENDS-ON: A08-12.

### A08-14: dF handling diverges across the stages - DeWiggle's repair ignores facing walls, the face-walk respects them
- LOCATION: `DeWiggle.usable` drops `t2!=null` and non-X/Z modes (`DeWiggle.java:170-177`), and
  `DeWiggle.jacobian` builds rows only from those X/Z walls (`197-221`); SmoothFaceRecovery.build emits
  `Mode.F` rows (`SmoothFaceRecovery.java:84-89`) and `rowValue/rowGrad` handle them (`98-112`).
- CLAIM: DeWiggle checks FULL feasibility via `compiled.maxViolation` (`DeWiggle.java:301-305`) but its
  Gauss-Newton REPAIR Jacobian contains no facing (dF) rows, so on a jump with an active dF wall a flatten
  that perturbs facing cannot be repaired against that wall and the candidate is simply rejected (DeWiggle
  silently does less on dF jumps). The face-walk, by contrast, carries F rows in both its projector and its
  restore, so it smooths WITH dF constraints active. SmoothingPolish gates on full violation only (no
  Jacobian), so it neither breaks nor exploits dF. This is an inconsistent dF story across the four stages.
- EVIDENCE: file:lines above; `usable` explicitly `continue`s on `c.mode != X && != Z` and `c.t2 != null`.
- IMPACT: correctness/robustness - DeWiggle (the dominant stage) is partially disabled on dF jumps.
- PROPOSAL: give DeWiggle's repair the same F-row support the face-walk has, or document dF jumps as
  face-walk-only for smoothing.
- CONFIDENCE: 0.8
- DEPENDS-ON: A08-12.

### A08-15: BucketAscentPolish is an objective maximizer, not a smoothing stage, but leaks smoothLambda into its score
- LOCATION: `BucketAscentPolish.java:159-165` (`score` adds `obj.smoothPenalty`).
- CLAIM: BucketAscentPolish is block-coordinate ascent on the byte-exact objective (island-hopping on the
  65536-bucket grid), NOT one of the four smoothing stages, but its scoring folds in
  `obj.smoothPenalty = smoothLambda*turnCost`, so with the Smooth checkbox on it also weakly prefers smoother
  paths while maximizing. It has no give-back cap of its own (it only ever improves the scored objective).
- EVIDENCE: `score` line 164 adds `obj.smoothPenalty(scenario.startYaw, abs)`; FAST/THOROUGH configs
  (`BucketAscentPolish.java:43-54`).
- IMPACT: simplicity - a fifth site where turnCost is consumed, reinforcing A08-11 (turnCost is diffuse).
- PROPOSAL: include BucketAscentPolish among the turnCost consumers when converting turnCost to a hard
  constraint.
- CONFIDENCE: 0.8
- DEPENDS-ON: A08-11.

### A08-16: YawTies and FacingLattice are dF/lattice support, orthogonal to smoothing
- LOCATION: `YawTies.java:61-156`, `FacingLattice.java:32-118`.
- CLAIM: `YawTies` reduces the free-variable set by pinning/linking ticks whose dF band is narrower than
  `WIDTH_MAX=2.5e-4` (a variable-elimination for dF=0 chains); `FacingLattice` maps facings to the MC
  sine/cos bucket ids and finds bucket representatives. Neither computes a smoothness metric; they support dF
  pinning and byte-exact bucket search. The engine's face-walk uses `FacingPrefold` (not YawTies) for its
  frozen mask (`AngleSolverEngine.java:1074`), so YawTies and the smoothing freeze are two separate
  pin-elimination mechanisms - a candidate consolidation target for capability 5.
- EVIDENCE: `WIDTH_MAX=2.5e-4` (`YawTies.java:8`); FacingPrefold used at `AngleSolverEngine.java:1074-1078`,
  not YawTies.
- IMPACT: simplicity - flags a duplicate pin-elimination path (YawTies vs FacingPrefold).
- PROPOSAL: check with the dF/free-start agents whether YawTies and FacingPrefold can be one mechanism.
- CONFIDENCE: 0.7
- DEPENDS-ON: none.
