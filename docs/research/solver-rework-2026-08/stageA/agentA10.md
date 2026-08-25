# Agent A10 shard: the discrete byte-exact layer

AGENT: A10
TERRITORY: the discrete byte-exact layer. McSineTable, Constants, FacingLattice, LatticeRepair, YawTies, Angles, and the toGameFacings delta-vs-absolute realization; the inertia gate and its big-M folding; the norm>1 half-angle cells.

FILES INSPECTED (read in full unless noted):
- core/.../anglesolver/solver/McSineTable.java, Constants.java, FacingLattice.java, LatticeRepair.java, YawTies.java, Angles.java
- core/.../anglesolver/solver/ExactJumpModel.java (gate + forward), JumpPhysicsInputs.java (toGameFacings), JumpLinearModel.java (zeroingPattern, velocityWalls, keepAliveWall, compileWall)
- core/.../anglesolver/solver/WrapWindowIls.java (cell candidate gen + norm filter), BoundPrunedRecovery.java (gate-pattern B&B, lines 130-415), RelaxationRecovery.java (90-170), SlpSolve.java (100-118), FacingPrefold.java (1-55, callers)
- callers: graph/nodes/WrapIlsNode.java, FreeStartSolve.java (reaccumScore, FacingPrefold), NodeCatalog.java (maxAbsGf param)
- docs/reference/mcpk/01-movement-formulas.md, 02-angles-and-mouse.md; docs/research/solver-rework-2026-08/00-context-pack.md

COMMANDS RUN (direct java -cp against core/build/classes/java/main, no gradle):
- DiscProbe.java (self-contained reproduction of the exact sine-table math): bucket granularity + norm residual sweep.
- GateProbe.java (drives the real compiled ExactJumpModel): inertia-gate firing counts on monotone, reversal, decay-coast scenarios.

---

## A10-1 The angular grid is 0.00549 deg / 3.1e-5 b per tick, 1.5e-4 b per flat-jump path

LOCATION: McSineTable.java:12,31-37; FacingLattice.java:7 (DEG_PER_BUCKET)
CLAIM: MC's yaw quantizes onto a 65536-bucket grid of width 0.0054931643 deg; one bucket moves a sprint-jump tick's velocity add by 3.14e-5 b, and that grows to 1.54e-4 b at the objective tick of a flat jump; this is the closest-vector granularity, and it equals the "accumulated ~1e-4 b" certify floor the handoffs cite.
EVIDENCE: DiscProbe: DEG_PER_BUCKET = 180/(pi*10430.378F) = 0.0054931643 deg (matches wiki "~0.0055"). Per-tick add granularity m*d(theta): m=0.3274 (sprintjump modulus) -> 3.139e-5 b/bucket; m=0.02599 (air) -> 2.492e-6 b/bucket. Flat 12-tick sprintjump: friction-coupling reach of tick0's input onto pos12 is 4.917 b/unit, so one bucket at tick0 = 0.3274*d(theta)*4.917 = 1.543e-4 b. Joint (sin,cos) cell density in [45,46) deg = 208 cells = 0.0048 deg/cell (finer than a single axis because the cos bucket edge is offset from the sin bucket edge).
IMPACT: correctness/simplicity. Fixes the problem class: this is an integer-least-squares / closest-vector layer with a KNOWN, uniform grid pitch of ~1.5e-4 b at the objective, not an unknowable noise floor. Any "snap continuous to discrete" step has a certifiable rounding budget of one bucket.
PROPOSAL: adopt 1.5e-4 b (one objective-tick bucket) as the named certify floor constant; drive the closest-cell snap from FacingLattice.cellRepresentatives (A10-9).
CONFIDENCE: 0.95
DEPENDS-ON: none

## A10-2 FacingLattice enumerates candidate buckets by float bisection over joint (sin,cos,boost) cells

LOCATION: FacingLattice.java:32-42 (jointCellId), 97-149 (cellRepresentatives/collectCells/record)
CLAIM: A tick's byte-exact velocity contribution is fully determined by up to four table indices, and FacingLattice enumerates every distinct such cell in a sin-bucket window around a continuous yaw by recursively bisecting the float axis, returning one representative float per cell.
EVIDENCE (code): jointCellId packs sinIndex<<48 | cosIndex<<32, and on a legacy jump-boost tick also (boostSin<<16 | boostCos), where sinIndex/cosIndex = (int)(rad*10430.378[+16384]) & 65535 with rad from radOf (A10-7's two casts). cellRepresentatives walks [gf + (loDelta-0.5)*pitch, gf + (hiDelta+0.5)*pitch], collectCells bisects until idA==idB or Math.nextUp(a)>=b (depth<=120), record keeps a cell only if its signed sin-delta from the current bucket is within [lo,hi]. So the enumeration is exact per float, not sampled.
IMPACT: simplicity. This is a correct, reusable closest-cell primitive; the machinery to do integer-least-squares over the LUT already exists and is byte-exact.
PROPOSAL: keep FacingLattice as the canonical lattice primitive; it is the one piece of the discrete layer that is clean and general.
CONFIDENCE: 0.9
DEPENDS-ON: A10-1

## A10-3 LatticeRepair is dead in the shipped path (only test screens call it)

LOCATION: LatticeRepair.java (whole file); callers only in core/src/test EngineFileScreen.java:140, RelaxDiagScreen.java:43,72
CLAIM: The "snap continuous to the lattice" repair pass is not wired into the graph pipeline at all; the context pack's "RelaxationRecovery ... + lattice repair" is stale.
EVIDENCE: repo-wide grep for LatticeRepair.repair / new LatticeRepair / LatticeRepair. returns only the two test screens; RelaxationRecovery.java references it zero times (it seeds SLP from ditherSeedYaws/projectionSeedYaws at :115-116 and calls SlpSolve at :125, no lattice repair). FacingLattice.stepToSinBucket (FacingLattice.java:52) is likewise called only from FacingLatticeTest.java.
IMPACT: simplicity, no robustness cost. Two public API surfaces (LatticeRepair, stepToSinBucket) are dead weight in the shipped jar; the continuous->discrete snap is actually done by SLP + inward margin + byte-exact verify, never by an explicit lattice projection.
PROPOSAL: either delete LatticeRepair + stepToSinBucket, or promote a single lattice-snap into the pipeline (A10-9) and delete the SLP-based ad hoc equivalent. Do not leave both.
CONFIDENCE: 0.9
DEPENDS-ON: none

## A10-4 The inertia gate: thresholds verified, and it fires destructively only on a coasting axis

LOCATION: ExactJumpModel.java:24-25,83-94,105-111,153-160; Constants.java (no threshold there, it lives in ExactJumpModel)
CLAIM: The gate is per-axis |v|<0.005 (1.8.x), per-axis |v|<0.003 (1.12.x, 1.21.3), or combined vx^2+vz^2<9.0e-6 (1.21.5+/26.x); it is a no-op on a fed sprint-jump axis and fires destructively (zeroing a real carry) only when an axis coasts down into the ~0.005 band.
EVIDENCE (GateProbe on the real compiled model): forMcVersion("1.8.9") -> thr=0.0050 perAxis=true; ("26.2") -> thr=0.0030 perAxis=false. Monotone flat sprintjump (yaw 0): 0 destructive, 0 critical of 12. Reversal (yaw 90->-90): 0 destructive, 0 critical of 12 (the boost keeps the objective axis fed above the band). Decay-coast (initial vX=0.05, no input, ground friction 0.546): velX = 0.050, 0.0273, 0.0149, 0.0081, 0.0044 -> ZEROED at tick5, giving exactly 1 destructive fire and 3 critical ticks (WrapWindowIls.gateCriticalTicks band [thr/4, 4*thr]) of 20. The modern combined literal is 9.0E-6, deliberately NOT 0.003*0.003 (comment ExactJumpModel.java:24).
IMPACT: correctness/simplicity. Confirms the mixed-integer big-M layer is INERT on the overwhelming majority of ticks; it becomes load-bearing only at a genuine momentum kill (loopmm brake), matching the domain claim. A collapsed solver can treat the gate-free relaxation as the default and branch only on the handful of coasting ticks.
PROPOSAL: gate the whole big-M machinery behind a cheap "any axis carry in [thr/4, 4*thr]?" pre-check (already exists as gateCriticalTicks) and skip pattern enumeration entirely when it is empty.
CONFIDENCE: 0.9
DEPENDS-ON: none

## A10-5 The gate's big-M indicator is realized twice: zeroing walls plus a keep-alive complement

LOCATION: JumpLinearModel.java:278-322 (velocityWalls, keepAliveWall), 324-345 (zeroingPattern); BoundPrunedRecovery.java:342-415 (enumeratePatterns, keepAlivePatterns)
CLAIM: The gate is folded into the linear model as a fixed per-axis zeroing pattern (each input's friction propagation cut at its first zeroing tick), and the B&B enumerates one pattern per (axis, zero-from-tick-k) plus the complementary keep-alive half-space; both directions are needed because the free relaxation never sees the gate.
EVIDENCE (code): zeroingPattern replays the forward carry and marks zx/zz when |v|<threshold; velocityWalls emits, per zeroed tick, a +/- pair bounding the pre-zero carry inside the band; keepAliveWall emits the complement (carry outside the band, one sign). enumeratePatterns builds zx@k/zz@k (+ single-tick zx1@k) for perAxis, or zxz@k for combined, each rooted by its own pattern-folded dual bound. GateProbe confirms these patterns are empty unless a carry actually reaches the band (A10-4), so the enumeration is usually the "free" pattern alone.
IMPACT: simplicity. The indicator layer is correct but spread across velocityWalls / keepAliveWall / enumeratePatterns / keepAlivePatterns; it is a textbook indicator-big-M constraint that could be one shared compiler emitting both half-spaces from the same band.
PROPOSAL: unify the gate walls into a single "band constraint" emitter (inside-band zeroing vs outside-band keep-alive as a sign flag), consumed identically by every recovery stage.
CONFIDENCE: 0.82
DEPENDS-ON: A10-4

## A10-6 Half-angle norm>1 cells top out at 9.6e-5 (1.5e-4 b); NOT the continuous->byte-exact gap

LOCATION: WrapWindowIls.java:316-337 (candSetFor, isHigh = normAt>1e-6), 430-435 (normAt); FacingLattice.record norm-agnostic (it filters on sin-delta, not norm)
CLAIM: The LUT's per-bucket unit-modulus error (half angles) is at most 9.59e-5 in the vanilla facing range, worth about 1.5e-4 b of extra reach on a flat jump; it is a FAVORABLE-direction discrepancy (byte-exact can slightly exceed the continuous unit-modulus model) and is 45x too small to be the measured 0.007 b continuous->byte-exact X drop on thousand/1.
EVIDENCE (DiscProbe, float chain matching WrapWindowIls.normAt): over [-180,180], max |sin^2+cos^2-1| = 9.594e-5 at gf=135.269 deg (wiki's illustrative 135.0055->5e-5 is the same order); mean 1.516e-5; 24.3% of buckets exceed the 3.1e-6 table-quant floor. Extra flat-jump reach at the max increasing half-angle = 9.59e-5 * 0.3274 * 4.917 = 1.544e-4 b (coincidentally equal to the bucket pitch of A10-1). Context-pack continuous->byte-exact X drop on thousand/1 = 0.007 b = 45x larger.
IMPACT: correctness. Settles a discriminator: the multi-jump gap is NOT the half-angle modulus error and NOT the grid pitch; both are at ~1.5e-4 b, far below 0.007 b. The razor campaign's norm>1 hunting buys real but sub-milliblock reach, right at the certify floor.
PROPOSAL: keep the norm>1 candidate bias in WrapWindowIls for the last microns, but do not model half angles in any collapsed continuous formulation; the unit-modulus relaxation is faithful to 1.5e-4 b.
CONFIDENCE: 0.88
DEPENDS-ON: A10-1

## A10-7 Two distinct yaw-to-rad casts are modelled and folded into the cell id

LOCATION: FacingLattice.java:12-17 (radOf), 32-42 (jointCellId boost bits); ExactJumpModel.java:189/195/246/262 (jump-boost cast vs moveFlying cast)
CLAIM: The move-input trig uses rad = yaw*(float)PI/180F (legacy, a multiply-then-divide) while the sprint-jump boost uses rad = yaw*(float)(PI/180.0) (a single premultiplied constant); these differ at large |yaw|, so a legacy jump-boost tick's cell identity requires BOTH casts' buckets to match.
EVIDENCE (code): radOf returns the two-step form when (!modern && !boostCast) and the single-constant form otherwise; ExactJumpModel step (4) legacy uses yawF*(float)PI/180.0F, jump() boost uses yawF*(float)(PI/180.0). jointCellId ORs boostSin/boostCos into the low 32 bits only when jumpBoostTick && !modern. This is exactly the wiki's "sprintjumping moves slightly to the side" (02-angles §Angles).
IMPACT: correctness. The cell abstraction is genuinely 4-index on legacy boost ticks; a snap must respect both casts or it will drift on those ticks.
PROPOSAL: preserve the 4-index cell id in any lattice-snap; do not collapse the boost cast into the move cast.
CONFIDENCE: 0.85
DEPENDS-ON: A10-2

## A10-8 toGameFacings is the delta-vs-absolute realization; there is no FacingReconstruction class

LOCATION: JumpPhysicsInputs.java:189-208 (toGameFacings), Angles.java:23-27 (wrapDelta), 9-14 (wrap)
CLAIM: The ">180 deg delta limit" the brief attributes to a FacingReconstruction lives in toGameFacings via Angles.wrapDelta: an unlocked tick accumulates the float of the wrapDelta-clamped step onto the running entity yaw, a locked tick sets the entity yaw to the absolute (float) facing; no separate reconstruction class exists.
EVIDENCE (code + grep): toGameFacings loops abs-wrapped facings, for locked ticks entity=(float)abs, else entity += (float)wrapDelta(abs-prevAbs); wrapDelta is the while-loop idiom that forces each per-tick turn into (-180,180], so a solver-side 200 deg jump is realized as a -160 deg game turn. No file named FacingReconstruction exists. Angles keeps wrap (modulo, absolute reduction) and wrapDelta (while-loop, delta) as two deliberately-non-foldable idioms (Angles.java:3-5) that can differ for multi-turn inputs.
IMPACT: correctness/simplicity. The delta realization is a single 20-line function used by every stage's scoring (grep: ~50 call sites across ClosedForm, SLP, BoundPruned, FreeStart, Smoothing, DeWiggle, Scoring, LongRun). This is the one genuinely-shared discrete primitive.
PROPOSAL: treat toGameFacings + Angles as the fixed shared kernel; any collapse builds on it unchanged.
CONFIDENCE: 0.9
DEPENDS-ON: none

## A10-9 Wrap-depth / |gameFacing| cap is MAX_ABS_GF=12000, enforced only inside WrapWindowIls

LOCATION: WrapWindowIls.java:15 (MAX_ABS_GF=12000), 31,82,304 (cfg.maxAbsGf guard/throw); NodeCatalog.java:243 (param 360..100000 default 12000)
CLAIM: The only hard wrap-depth limit is WrapWindowIls' maxAbsGf (default 12000 deg, ~33 turns), enforced by rejecting seeds and throwing if a stage produces a facing beyond it; no other stage bounds wrap depth.
EVIDENCE (code): polish rejects gf0 with |g|>maxAbsGf (:82), throws "stage produced gf beyond the wrap cap" on output (:304); candSetFor/candFull/kickCells cap-filter representatives at bases {0,+/-360,+/-720}. The user preference |gf|<=720 (memory: wrap-depth-legality) is NOT enforced here; 12000 is far looser. It is exposed as the maxAbsGf node param.
IMPACT: robustness/simplicity. Wrap-depth legality is a WrapWindowIls-local concern; other recovery paths can emit any wrap depth and are only bounded by Angles.wrap's <=2-turn assumption (Angles.java:8) upstream.
PROPOSAL: hoist a single wrap-depth policy (default the user's 720) into the shared kernel so free-start, SLP, and dual paths honour it, not just the wrap ILS.
CONFIDENCE: 0.8
DEPENDS-ON: A10-8

## A10-10 dF pinning is implemented TWICE (FacingPrefold vs YawTies); collapse candidate

LOCATION: FacingPrefold.java:1-55,133 (analyze/reduce/ChainScan); YawTies.java:61-156 (of/expand/reduce)
CLAIM: Two independent classes reduce dF (F-mode facing) constraints to fewer free variables: FacingPrefold on the closed-form / free-start / dual path, YawTies on the SLP path; both detect near-equal-width absolute pins (WIDTH/PIN_WIDTH_MAX = 2.5e-4) and linked delta groups and fold them out.
EVIDENCE (callers): FacingPrefold.analyze/scannable used by ClosedFormSolve (:85,121,375), FreeStartSolve (:124,313,517), BoundPrunedRecovery (:61 as a reject gate), AngleSolverEngine (:1074). YawTies.of used only by SlpSolve (:109). Both classes carry the same 2.5e-4 pin-width constant and the same group/offset/rep reduction; FacingPrefold additionally folds baseArg so it can drop pinned ticks from the linear objective.
IMPACT: simplicity. Two parallel implementations of the identical dF-reduction concept, differing only in whether they carry the linear objective; a divergence between them is a latent inconsistency (e.g. YawTies PIN_MATCH_TOL=1e-6 vs FacingPrefold PIN_MATCH_TOL=1e-9).
PROPOSAL: unify into one dF-reduction that returns both the reduced variable map (for SLP) and the baseArg-folded linear reduction (for the dual); delete the duplicate.
CONFIDENCE: 0.85
DEPENDS-ON: none

## A10-11 Could the discrete layer be one certified snap-and-verify? Yes, the primitives exist

LOCATION: FacingLattice (enumerate), toGameFacings (realize), zeroingPattern/gateCriticalTicks (gate), scattered consumers
CLAIM: The discrete layer is today split across FacingLattice (cells, live only in WrapWindowIls), LatticeRepair (dead), the SLP/dither snap inside RelaxationRecovery, the gate walls in JumpLinearModel/BoundPrunedRecovery, and two dF reducers; it could collapse to one "closest-cell + gate-consistency verify" module because every needed primitive already exists and is byte-exact.
EVIDENCE (measured structure): cell awareness reaches the shipped path ONLY through WrapWindowIls (grep: FacingLattice referenced solely by WrapWindowIls in main). Every other stage (ClosedForm, SLP, BoundPruned, Relaxation, FreeStart) searches continuous yaw and relies on the inward-margin ladder (compileWall margin, JumpLinearModel.java:246) + a byte-exact forward verify to stay feasible, never an explicit projection. The one-bucket rounding budget is known (A10-1: 1.5e-4 b), the gate-critical set is cheaply computable (A10-4), and the realization is a single function (A10-8).
IMPACT: simplicity (large): replace LatticeRepair + the RelaxationRecovery dither-snap + per-stage margin heuristics with one snap primitive; robustness neutral-to-positive if the snap carries an explicit gate-consistency check.
PROPOSAL: a LatticeSnap(continuousYaws) that, per tick, (1) picks the closest FacingLattice cell whose norm>=1 when a reach bonus helps, (2) realizes via toGameFacings, (3) verifies the resulting gate pattern equals the pattern the continuous solve assumed (zeroingPattern match), (4) reports the byte-exact residual against the one-bucket budget. Effort: moderate; it is de-duplication and wiring, not new math. Route it through COPT/StructureDump first to confirm the closest-cell snap matches the global integer optimum on the corpus before replacing the incumbent.
CONFIDENCE: 0.7
DEPENDS-ON: A10-1, A10-2, A10-3, A10-4, A10-8

## A10-12 CONSISTENCY MATRIX (discrete-layer capabilities, present/absent with file:line)

LOCATION: cross-stage
CLAIM: Caching, smoothing, defaults, dF, and free-start are each realized on some discrete-layer paths and not others; the reaccumScore split and the FacingLattice-only cell awareness are the two sharpest gaps.
EVIDENCE (code):
- CACHING (incremental forward): ExactJumpModel.stepRange (ExactJumpModel.java:141) recomputes only the affected tail from tick `from`; used by local searches. WrapWindowIls.score (:485) always calls full model.forward (no stepRange), so the wrap ILS does NOT use the incremental cache. Gap: present in the stepper, unused by the biggest inner loop.
- SMOOTHING: turnCost/wiggle live in Angles (:69-108) and are consumed by the Smooth (TAS) objective; the discrete layer itself is smoothing-agnostic (FacingLattice/LatticeRepair/YawTies have no smoothing awareness). Consistent (smoothing is not a discrete-layer concern).
- DEFAULTS (reaccumScore): FreeStartSolve.java:287 sets reaccumScore=true so its wrap ILS re-accumulates candidate gf through toGameFacings(wrapAll(gf)); WrapIlsNode (graph/nodes/WrapIlsNode.java, no reaccumScore set) leaves it FALSE, scoring candidate cells as literal absolute facings. Same WrapWindowIls stage, two different realizations of the discrete facing depending on caller. Gap.
- dF: reduced-variable dF folding present in FacingPrefold (ClosedForm/FreeStart/dual) and YawTies (SLP) only (A10-10); the pure-continuous inner loops (RelaxationRecovery, IlsPolish, WrapWindowIls) do NOT fold dF, they see F only through the compiled constraint violation. Gap: dF is a first-class reduced variable on two paths, an opaque penalty on the rest.
- FREE-START: constPos/coefAxis read startBox when present (JumpLinearModel.java:196-199), so the linear model is free-start-aware uniformly; but only FreeStartSolve toggles reaccumScore and drives PathTranslation domains (WrapWindowIls.score transDomain, :492). So the linear algebra is free-start-consistent, the scoring realization is not (ties back to the reaccumScore gap).
IMPACT: correctness/simplicity. Two same-stage behaviors (reaccumScore on/off) and two dF codepaths are the concrete "implemented in some stages but not all" gaps the spec must resolve.
PROPOSAL: make reaccumScore unconditional (always realize candidates through toGameFacings) so free-start and fixed-start score identically; unify dF (A10-10); give WrapWindowIls the stepRange cache.
CONFIDENCE: 0.8
DEPENDS-ON: A10-8, A10-10
