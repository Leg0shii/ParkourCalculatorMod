# Stage A synthesis: reduced, deduped, audited findings

Reducer/auditor: Stage A. Inputs: `00-context-pack.md` + `stageA/agentA01.md`..`agentA10.md` (all read in full).

Audit method: for the highest-impact claims and every conflict I re-read the cited code and re-ran the
deterministic checks that do not need gradle. Independently re-verified THIS session (not just trusted
from a shard):
- `grep \.stepRange\(` over `core/src` -> no matches; `stepRange` sole call is `forward` at
  `ExactJumpModel.java:131` with `from=0`.
- `grep LatticeRepair` over `core/src/main` -> only the class definition, zero callers.
- `glob **/FacingReconstruction*.java` -> no file.
- `new JumpLinearModel(` = 24 sites in main across 8 files; `new CostateDualSolver(` = 8 sites in main.
- `CostateDualSolver` constants: `MAX_ITER=100`, `DIVERGE_PGRES=4.0`, `DIVERGE_STALL=12`, `EPS2=1e-14`,
  `GRAD_TOL=1e-8`, `U_TOL=1e-9`, `LAMBDA_CAP=1e9`, `P0_SMOOTH_DEFAULT=0.05` (lines 39-55, 209-250).
- `compileWall` p0coef: `int tc=(t2==null)?1:(opSign>0?2:0); double p0coef=(GE)?tc:-tc;`
  (`JumpLinearModel.java:248-249`).
- Gate walls: `velocityWalls` (2 rows/zeroed tick), `keepAliveWall` (1 complement row), `zeroingPattern`
  replay (`JumpLinearModel.java:278-345`).
- Smoothing constants: `DeWiggle.MAX_GIVE_BACK=8e-3` (mutable static, :29),
  `SmoothingPolish.MAX_GIVE_BACK=8e-3` (mutable static, :46), `SMOOTH_OBJ_SLACK=3e-4`
  (`AngleSolverEngine.java:1090`), `REVERSAL_COST_DEG=90`, `RATE_TIEBREAK=0.02`, `TASER_SMOOTH_LAMBDA=1e-2`.
- SLP reject rate: re-counted `SLP .*phase=` over 109 `build/reports/solver-trace-*.txt`:
  6477 accept / 45430 reject = 87.52% reject, 8.01 LP/accept (matches A04-4 to the digit).
- `FreeStartTranslationTest.checkZeroWidth`: byte-exact (`Double.doubleToRawLongBits`) assertion that a
  zero-width box translates to tx==tz==0 and byte-equal violation across 10 yaw draws x 3 captures.

Probe results I did NOT independently re-run (byte-exact screens need a compile step I avoided to dodge
build-lock contention): A01 `ContinuousDiscreteScreen`, A03 `RelaxSlackProbe`/`EndToEndProbe` (j828
13/39 off-sphere), A08 `MetricProbe` (dither vs mono roughness), A05 BnB trace node counts, A02 Drift/
Build/Forward benches. For these the finding is tagged on the strength of the mechanism + constants I did
verify; where the magnitude is unre-run I say so.

---

## Top findings (ranked by IMPACT x CONFIDENCE)

### F1 (CONFIRMED, mechanism; UNVERIFIED, multi-jump H1/H2) - the multi-jump recovery failure is a duality gap / degenerate dual face, NOT a convergence bug
Sources: A01-2, A01-3, A03-6, A03-7, A07-9, A07-11, A04-10.
The `u*=m g/|g|` costate recovery is exact when the dual is tight (A01-1: j016 multi-jump iters=13
pgres=1.02e-10 byte-exact feasible; j019 iters=18). It fails on the coupled/opposing-corridor cases by
landing on a flat degenerate dual face where many `lambda>=0` give the same `D` but uniformly infeasible
`u*`. Raising iterations tightens the BOUND but cannot fix the RECOVERY (context pack section 5:
100->10000 iters still null; re-confirmed the code has no other lever - `MAX_ITER=100`, `U_TOL=1e-9`
early-exit at line 231, `DIVERGE_PGRES=4.0` at line 221 all verified).
Audit verdict: the "not a convergence bug" claim is CONFIRMED - the constants and exit logic are exactly
as described, and A01-3 measured (byte-exact) that the realization is no better than the continuous
recovery on the failing cases (j008b 4.17e-1 vs 4.18e-1 b; razor-proof 5.56 vs 5.56; loopmm 0.245 vs
0.276), which localizes the loss to the fixed-modulus continuous recovery, not quantization.
H1-vs-H2 nuance (this is a real, load-bearing caveat): A03-6 MEASURED H2 (disk relaxation tight,
face-degenerate) ON j828 ONLY (13/39 ticks off-sphere but every off-sphere tick has `|g_t|<=7.3e-10`,
so modulus slack lives entirely in the `g=0` null space; per SOCP KKT interior ticks can only occur where
`g=0`). That is a single hard capture (39 ticks, 12 walls), NOT the coupled multi-jump class
(thousand/1, f2f-dfchain-multijump) that the known-hard issue is about. The multi-jump H1/H2 question is
UNSETTLED and is exactly the Stage 0 COPT task. Do NOT generalize A03-6's H2 to multi-jump.
IMPACT: correctness, decisive for target capability 4. CONFIDENCE 0.9 (mechanism), 0.6 (multi-jump class).

### F2 (CONFIRMED) - SLP rejects 87.5% of its LP steps; the waste is trust-region management, not LP-kernel cost
Sources: A04-4, A04-5, A04-11.
Independently re-counted over 109 trace files: 45430 reject / 51907 total = 87.52%, 8.01 LP calls per
accepted step (phase-2 is the worst at ~92%). The trust region is a binary halve-on-reject /
conditional-double-on-accept with no curvature model, so near a hugged wall it proposes-large, gets
rejected, and halves ~12x down toward the float lattice. TrustRegionLp is ~11% of solver CPU
(dual-newton-iteration-audit.md:89) and most of that is thrown away.
IMPACT: speed, up to ~5% of solver CPU recoverable with no objective change. CONFIDENCE 0.9.
Caveat carried forward: A04-11's j022-noland hug regression (TrustRegionLp lands ~1e-4..2.7e-4 b worse
than the removed commons-math simplex, above the certify floor) is real but bounded to one capture and
tight level-set ladders; do NOT re-add an LP library to fix it (A04-7: measured-net-negative packaging).

### F3 (CONFIRMED, decomposition; north-star REFUTED) - the four smoothing stages are three heuristics for ONE non-convex reversal-count object plus one wrong-metric stage; they collapse to ONE term but only in nonconvex theta-space
Sources: A08-1..A08-9, A09-5.
turnCost (search-ranking bias), DeWiggle (run-flattening), and SmoothFaceRecovery (null-space walk) all
minimize the SAME object: the L0 count of sign changes of `D1 theta` (reversal count). SmoothingPolish
minimizes `||D1 theta||_2^2`, which the A08 MetricProbe measured cannot distinguish a dither from a
monotone step of equal magnitude (roughness 401.25 for both, reversals 4 vs 0) - it is weak because it is
the WRONG convex surrogate, not because it is redundant. The tightest convex surrogate for "few
reversals" is `||D2 theta||_1` (TV of turn rate), which is turnCost's and the face-walk's tiebreak.
Audit verdict: the "collapse four stages to one term" claim is CONFIRMED as a decomposition (constants
and metric definitions verified; the roughness-blindness is a mathematical certainty for equal-magnitude
sign flips). BUT the context-pack north star "smoothing as JUST ANOTHER term over the EXISTING convex
program" is REFUTED with a measured/model-anchored obstruction (A08-7): a term convex in `theta` is NOT
convex in the relaxation's input variables `u`, because `theta_t = atan2(uz,ux) - baseArg` is nonconvex
in `u`; adding it also destroys the LCvx constant-modulus tightness that makes `u*=m g/|g|` exact. So the
single term can only live in a post-solve nonconvex theta-space search, not in the SOCP/dual.
IMPACT: simplicity/smoothness, decisive for target capability 3. CONFIDENCE 0.85.

### F4 (CONFIRMED) - free-start is separable from the yaws by rigid translation; p0 is exactly 2 box-bounded linear vars with wall coef tc
Sources: A06-1, A06-2, A06-13.
For any FIXED yaw sequence the whole trajectory is a rigid translate in `p0` (MC horizontal physics has no
absolute-position term); the best `p0` is a 2-variable interval LP solved in closed form. Verified: the
wall coefficient is `p0coef = +-tc` with `tc = (single-tick)1 / (sum wall)2 / (difference wall)0`
(`compileWall:248-249`), and `FreeStartTranslationTest` asserts byte-exact translation invariance
(doubleToRawLongBits equality across 10 draws on razor-proof/j004/j318). The joint dual is only needed for
the fixed-start-infeasible-but-translatable residual (A06-11); the p0 dimension itself never needs to sit
inside the yaw search.
IMPACT: simplicity, large - "solve pinned at box center, then one PathTranslation pass" is the baseline
free-start mechanism. CONFIDENCE 0.9.
Caveat: multi-jump free-start commits the start on window 0 only and never re-optimizes against the tail
(A06-12, A07-13), leaving objective on the table; a final whole-chain PathTranslation would recover it.

### F5 (CONFIRMED, structure; MIP performance UNVERIFIED) - the inertia gate is a per-(tick,axis) big-M indicator, already realized as linear walls, duplicated across 5 solvers
Sources: A05-1, A05-9, A10-4, A10-5, A05-7.
Verified in code: `velocityWalls` emits the z=1 (inside-band, `|v|<=thr`) side as two linear rows per
zeroed tick (`inertiaX@t+/-`), `keepAliveWall` emits the z=0 (outside-band) complement as one row
(`keepX@t+/-`), and `zeroingPattern` replays the exact top-of-tick gate; friction propagation is cut at
the first zeroed tick via `zNext`. This IS a standard big-M indicator system over ~2n binaries. It is
consumed by BoundPrunedRecovery, ClosedFormSolve, FreeStartSolve, SlpSolve, SmoothFaceRecovery (two
byte-identical `patternEffective` helpers). A10-4 (GateProbe) measured the gate is INERT on the vast
majority of ticks and fires destructively only on a coasting axis (decay-coast: zeroed at tick 5 of 20),
so a gate-free relaxation is the correct default with branching only on the handful of critical ticks.
Audit verdict: the fold-to-big-M is CONFIRMED structurally. Whether a single MIP is FASTER than the
current microsecond banded-incumbent fast path (A05-3: dsf-neo solved with 0 tree nodes) is
UNMEASURED-HYPOTHESIS (A05-9, correctly tagged) and is the risk - the modulus nonconvexity may dominate.
IMPACT: simplicity + correctness (a real infeasibility certificate, which BnB-null is not - see F10).
CONFIDENCE 0.85 (structure), 0.55 (MIP wins).

### F6 (CONFIRMED) - smoothing give-back double-counts to ~1.63e-2 b because each pass floors against its OWN input
Sources: A08-10, A08-11.
Constants verified: DeWiggle 8e-3 + SmoothingPolish 8e-3 + face-walk 3e-4 = 1.63e-2 b worst case, each cap
relative to that pass's input rather than one shared pre-smoothing reference. That is ~160x the ~1e-4 b
certify floor. Both `MAX_GIVE_BACK` are mutable public statics (slider-ready), but turnCost is a soft
`obj - lambda*turnCost` bias at ~8 ranking sites with no single choke point, so a shared-budget slider
needs turnCost converted to a hard `obj >= originalObjective - X` constraint.
IMPACT: correctness/robustness, real defect for target capability 5. CONFIDENCE 0.9.

### F7 (CONFIRMED, code) - a single recovery already serves single AND multi-jump; the collapse axis is convex-tight vs degenerate-face, not single vs multi
Sources: A01-7, A04-9, A04-10, A09-11.
The identical `u*=m g/|g|` recovers single jumps and convergent multi-jumps (j016/j019); the failure set
(j008b/loopmm/razor/j828) is degenerate-face, orthogonal to jump count. SLP is already the shared primal
engine at ~15 call sites reading one compiled `JumpLinearModel.Wall`. So "fold single-jump into
LongRunSolver" and "one recovery for single+multi" are largely realized in practice; the obstruction is
face selection (F1), not modeling divergence.
IMPACT: simplicity. CONFIDENCE 0.8.

### F8 (CONFIRMED) - RelaxationRecovery is the working j828 rescue that ClosedForm drops, but is blind to dF and EQ
Sources: A03-10, A03-11, A03-2.
Measured (A03-11): ClosedFormSolve.optimize -> null on j828; RelaxationRecovery.solve -> byte-exact
feasible obj 4978.013116, 0.0107 b under the dual bound. But RelaxationRecovery bails on ANY facing wall
or EQ (`solve:32-34`), so both dfchain captures get null from it by construction. The disk-SOCP+one-sphere
realization can subsume the pure-position degenerate class but cannot represent dF/EQ.
IMPACT: robustness (removing it drops the dualrecovery class) + simplicity (dF/EQ is the hard boundary for
any "one recovery" fold). CONFIDENCE 0.93.

### F9 (CONFIRMED, code) - receding-horizon does NOT behave identically to a single-jump solve per window
Sources: A07-2..A07-8, A07-16, A09-11.
Necessary divergences (keep): lead-in windows solve a Z/MAX surrogate objective and a centered/robust
(non-hugging) solve; only the terminal window hugs the real objective (A07-2/3). Gaps to close: the window
solver OMITS RelaxationRecovery (A07-4), the primary terminal SLP result is NOT LevelSetAscent-topped-up
(A07-5), there is no per-window byte-exact race (A07-6, up to ~0.05 b left on interior windows), and
seam-straddling relative/velocity/dF constraints are DROPPED in the following window (A07-7/A07-14; the
audit.md "re-checked as trivial tick-0" text does not match current `sliceConstraints`). The full-run
byte-exact re-verify backstops false success but can force a ladder retry. Clean fold: make
`solveWindow(last=true)` delegate to `dualChain`.
IMPACT: correctness/robustness + simplicity. CONFIDENCE 0.86.

### F10 (CONFIRMED, code) - BnB null is NOT an infeasibility certificate, yet the Optimize graph treats a cold miss as "no solution"
Sources: A05-5, A05-6.
Three independent incompleteness sources: the pattern family is finite/structured (isolated multi-tick
interior zeroings are outside it), the dual bound is F-blind (`compileWall` null for Mode.F), and `restore`
is a 45-iter capped Gauss-Newton. Pruning is SOUND (per-pattern conditional bounds); the unsoundness is the
incomplete pattern SET. The graph routes `coldBnb` null straight to `emit` with SeamSweepRecovery
unreachable on that branch, surfacing a solvable jump as "no solution".
IMPACT: correctness, the single most dangerous use of this stage. CONFIDENCE 0.85.

### F11 (CONFIRMED, dumped) - engine/graph capability gaps: FAST-explore lacks free-start+capCertify; legal push is OPTIMIZE-only; a shared mutable scenario keeps (start,yaws) consistent only by convention
Sources: A09-3, A09-4, A09-7, A09-14.
The explore race arm (9 nodes) has no capCertify, free-start, translate, horizon, or setupPeel; when it
wins a FAST solve those capabilities are silently skipped (A09-3). Wrap-ILS + legal push exist only in the
`ilx` graph, so LEGAL MODE under FAST effort gets no legal push at all (A09-4). Result selection is a
decentralized threaded incumbent over a SHARED mutable `JumpPhysicsInputs`; feasibility-first-then-objective
is re-implemented in >=6 nodes and `startPos` is mutated in-place, kept consistent only by per-node
re-verification (A09-7) - the architectural surface behind the "Optimize dropped-feasible" class.
IMPACT: simplicity/robustness/correctness, high. CONFIDENCE 0.88.

### F12 (CONFIRMED) - ExactJumpModel.stepRange(from>0) is dead; there is no model/precompute cache anywhere
Sources: A02-4, A02-5, A02-6, A09-13, A10-12.
Verified: no `.stepRange(` call in `core/src`; the sole `stepRange` call is `forward` with `from=0`
(`ExactJumpModel.java:131`). The incremental-tail capability the javadoc advertises for local searches is
never used, while the anytime polishers spend their whole budget on full O(n) forwards (A02-6: BucketAscent
3.16M-36.1M evals/call at n=49, each a single-tick change). `new JumpLinearModel(` = 24 sites in main; the
scenario-only `precompute()` is rebuilt up to ~36x per dualChain solve with no cache (A02-5).
Audit verdict on the A02-4 vs A10-12 conflict: RESOLVED for A02-4. A10-12's "used by local searches" is
imprecise - it describes the javadoc's aspiration, not a call site. stepRange is present-but-dead.
IMPACT: speed - but LOW absolute (A02-5 measured ~9-36 us/solve for the precompute rebuild at n=49,
negligible vs the 0.1-800 ms envelope). The real speed lever here is A02-6 (route perturbation rescoring
through stepRange, ~2x on the dominant polisher cost). CONFIDENCE 0.9.

### F13 (CONFIRMED) - LatticeRepair is dead in the shipped path; no FacingReconstruction class exists; RelaxationRecovery has no pin ladder
Sources: A03-12, A10-3, A10-8.
Verified: `LatticeRepair` has zero callers in `core/src/main` (only the class definition + two test
screens); `FacingLattice.stepToSinBucket` likewise test-only. No file matches `FacingReconstruction*`. The
>180-deg delta realization lives in `toGameFacings` via `Angles.wrapDelta`. These are context-pack
corrections (see below). CONFIDENCE 0.95.

### F14 (CONFIRMED, code) - dF pinning is implemented twice and rigid translation three times
Sources: A10-10, A06-10, A08-16.
FacingPrefold (closed-form/free-start/dual path, `PIN_MATCH_TOL=1e-9`) and YawTies (SLP path,
`PIN_MATCH_TOL=1e-6`) both fold near-equal-width dF pins/linked-delta groups at width 2.5e-4, differing
only in whether they carry the linear objective - and the tolerances already diverge, a latent
inconsistency. Rigid translation is implemented three times (PathTranslation, FreeStartSolve.bestTranslate,
FreeStartSolve.pinTranslate) with triplicated objective-axis pickers.
IMPACT: simplicity + latent-correctness (tolerance drift). CONFIDENCE 0.85.

### F15 (CONFIRMED, constants) - Smooth (TAS) runs after the graph deadline and can overrun the budget; FAST has no overall deadline
Sources: A09-9, A09-10, A08-13.
`smoothFacing` runs after `GraphRunner.run` returns with a fresh `deadline/8` (cap 6s, floor 400ms), so
OPTIMIZE(10)+Smooth reaches ~11.25s actual while the panel reports it honestly. FAST `deadlineNanosFor=0`,
so a standalone FAST solve is bounded only by internal iteration caps + fixed-second node budgets (peel 12s,
freeImprove 20s); the 0.1-800 ms envelope holds by practice, not policy. Also: SmoothingPolish runs even
with Smooth OFF (objective-preserving roughness tie-break), so "Smooth off" is not a true yaw no-op, and
the run-ticks warm path never DeWiggles (A08-13, A09-6).
IMPACT: speed/robustness/smoothness consistency. CONFIDENCE 0.9.

---

## Consistency matrix (capability x subsystem, merged with file:line)

Columns: Caching = cross-call model/dual/window reuse. Smoothing-aware = does the stage's own
objective/ranking carry turnCost/jerk. dF = can it represent facing constraints. Free-start = does it
choose/carry p0. Defaults = are its tunables single-sourced.
Legend: P present, `-` absent, ~ partial. Evidence in parentheses.

| Subsystem | Caching | Smoothing-aware | dF | Free-start | Defaults |
| --- | --- | --- | --- | --- | --- |
| CostateDual (A01) | ~ intra-ladder warm only; cold at 8 ctor sites (`:187`, sites 379/456/518/46/152/427/457/805) | - raw cx,cz only (`costate:391`) | - stripped upstream; converges yet byte-viol=135 on f2f-dfchain (A01-8) | P FreeP0 +2 box vars (`:158-177`) | ~ MAX_ITER100/DIVERGE4.0(inert)/EPS2 mismatches caller guards |
| Linear+Exact models (A02) | - `new JumpLinearModel` x24 main, no cache; stepRange dead (`:131`) | - objectiveVectors raw (`:204`) | - compileWall rejects F (`:222`) | ~ constPos reads StartBox, reconciled by copyWithStart pin (A02-9) | ~ getters legacy-default; precompute hardcodes legacy diagonal (A02-8) |
| ClosedForm (A03) | ~ warm lambda across margin rungs (`runLadder:379-397`) | ~ recoverFace hook, Smooth-only, not in optimize path (`:72-79`) | P FacingPrefold pins + dF=0 chains | - added externally by FreeStartSolve | margins/marginsRobust/maxInertiaPasses=4 |
| RelaxationRecovery (A03) | ~ warm lambda across seed-margins+5 restarts (`:46,55`) | - no hook | - BAILS on any F or EQ (`:32-34`) | - none | outerIters30/inner500/seedMargins/dualRestarts5 |
| SLP (A04) | - rebuilds model+dual every call (`:127,152`) | - reads only obj axis/sense/tick (`:372`) | ~ only YawTies-absorbable dF=0 (`:108-121`) | - fixed start; orchestrated above (FreeStartSolve `:68,89`) | ~ 40/60 default, but 160/220 (RR), tree (BnB), graph (node) |
| BnB (A05) | - fresh dual + full forward per node (`makeNode:805`), no stepRange | - raw single-axis (`normObjective:1224`) | ~ dF=0 passes gate, dropped from bounds, F-exact only at accept (`:60-63`) | - reads startBox as constant (`:194-200`) | ~ Config defaults vs BnbNode overrides |
| FreeStart (A06) | ~ multi-jump WindowCache+retryCache; single-jump none | ~ scoredObjective adds translation (`Scoring:65`) | ~ FacingPrefold, but anchorRotationScan bails on unbounded F (`:167`) | P by construction; startTick==0 only (`:376`) | ~ jointWrapClose true in Config, false in node (fork) |
| RecedingHorizon (A07) | ~ WindowCache result-memo; NO cross-window dual warm (`:106,291`) | - 3-arg Objective drops smoothLambda (`:215,390`) | ~ within-window ok; cross-seam dropped; non-zero dF declined | ~ first-window-only joint (`:238`) | window10/commit{3,1}/ladder{10,7,5,3,2,1} |
| Smoothing stages (A08) | ~ SmoothFaceRecovery metric cache only (`:376`) | P (this IS the smoothing) | ~ face-walk respects F; DeWiggle repair ignores F (A08-14) | ~ via translated scenario | 3 mutable statics + turnCost bias at ~8 sites (no choke) |
| Engine/Graph (A09) | ~ reachBound per-context; none across race arms or run-ticks (`GraphContext:155`) | ~ split graph+engine; SmoothingPolish fires even Smooth-off (A09-5) | ~ non-zero dF declined engine-wide; notice on success (`:1097`) | ~ FAST-primary/OPTIMIZE yes, FAST-explore NO (A09-3) | FAST deadline=0 (unbounded); OPTIMIZE per-node clamped |
| Discrete layer (A10) | - WrapWindowIls uses full forward not stepRange (`:485`) | - smoothing-agnostic (correct) | ~ FacingPrefold vs YawTies twice (A10-10) | ~ linear model uniform; reaccumScore fork (A10-12) | MAX_ABS_GF=12000 only in WrapWindowIls |

---

## Collapse opportunities (each with its measured obstruction)

1. Smoothing as ONE term (target capability 3). Candidate: replace turnCost + DeWiggle + face-walk-metric
   with one `beta*||D2 theta||_1 + gamma*||D1 theta||_2^2` objective (drop SmoothingPolish's L2-D1 as a
   reversal remover; it is measured-blind). OBSTRUCTION (measured/model-anchored, F3/A08-7): the term is
   convex in theta but NOT in the relaxation's `u` variables, and it destroys the LCvx tightness that makes
   the dual recovery exact; so the single term lives ONLY in a post-solve nonconvex theta-space search, not
   in the convex program. This REFUTES the pack's "smoothing as a term over the existing convex program."
   Also fold the three give-back caps against one shared pre-smoothing reference (F6).

2. Free-start as translation (target capability 1). Candidate: "pin at box center, solve, one
   PathTranslation pass"; reserve the joint dual for the fixed-start-infeasible-but-translatable residual.
   OBSTRUCTION: byte-exact separability is PROVEN (F4, FreeStartTranslationTest), so there is no math
   obstruction; the open item is the UNMEASURED fraction of the corpus needing the joint dual (A06-14) and
   the multi-jump window-0-only commit that leaves objective behind (A06-12). A single disk-SOCP with box
   p0 would fold the whole stack (A06-13) IF the disk relaxation is tight (Stage 0).

3. Gate as big-M (target simplicity + a real infeasibility certificate). Candidate: one shared band-emitter
   feeding all 5 solvers, or a single MIP over ~2n binaries replacing the suffix-pattern enumeration.
   OBSTRUCTION: the shared-emitter fold is free (F5, structure confirmed); the MIP-replaces-enumeration
   claim is UNMEASURED (A05-9) and risks being slower than the microsecond banded fast path (A05-3) because
   the per-tick modulus nonconvexity remains. Hybrid (banded fast path, MIP only on cold miss) preserves
   speed while fixing the BnB-null false-negative (F10).

4. One shared compiled model (target simplicity + speed). Candidate: split `precompute` into an immutable
   `JumpLinearBase(scenario)` shared across passes/objectives/directions/windows, plus a thin
   pattern-view; pass a prebuilt model (and dual seed) into SLP/RR/BnB instead of rebuilding.
   OBSTRUCTION: none structural (A02-5, A04-8; the models already share the `Wall` interface, A04-9). But
   the absolute speed win is LOW (A02-5: ~9-36 us/solve). The larger win is routing perturbation rescoring
   through the dead stepRange (A02-6, ~2x on the polisher). The two models CANNOT merge byte-exactly
   (A02-11: float/LUT + state-dependent gate break linearity) - keep linear=relaxation, exact=truth.

5. One recovery for single AND multi (target simplicity). Candidate: one "dual + face-resolving recovery"
   primitive; on a degenerate optimum, solve a min-slack projection over `null(A_active^T)` before
   declaring a miss. OBSTRUCTION: the axis of failure is convex-tight vs degenerate-face, not single vs
   multi (F7), so this is 90% done; whether the projection suffices is the H1/H2 question (F1). Prototype
   the null-space LP in COPT first.

6. Fold single-jump into LongRunSolver (open direction 4). Candidate: route single-jump through
   `LongRunSolver` with window=numJumps; make `solveWindow(last=true)` delegate to `dualChain`.
   OBSTRUCTION: NOT zero-change today (A07-10, F9) - dualChain carries RelaxationRecovery, always-on
   LevelSetAscent, reseeded-SLP, and ClosestMiss that `solveWindow` lacks; the clean fold is the reverse
   delegation, and it must keep the monolithic `seedMulti` fallback for the small-jump-count regime where
   the dual still converges (A07-17).

7. One shared solve tail (target capability 5). Candidate: one capability set (free-start, translate,
   capCertify, smoothing, legal push) that EVERY terminal path shares, gating only search intensity by
   effort; candidate carries its own immutable (start,yaws) pair with a central best-feasible selector.
   OBSTRUCTION: none technical; it is a graph rewrite. Removes the FAST-explore gap (F11/A09-3), the
   legal-OPTIMIZE-only gap, the shared-mutable-scenario dropped-feasible risk (A09-7), and the smoothing
   graph/engine split (A09-5).

8. Dead-code / duplication removals (free simplicity): delete LatticeRepair + stepToSinBucket (F13, dead);
   delete the unused `AT_OBJECTIVE_CAP` predicate (A09-14); unify FacingPrefold+YawTies (F14) and the three
   translation impls (A06-10); unify the two `patternEffective` copies (A05-7).

---

## Conflicts and how resolved

- A02-4 (stepRange is dead) vs A10-12 ("stepRange used by local searches"). RESOLVED for A02-4, verified:
  no `.stepRange(` call anywhere in `core/src`; the sole call is `forward(from=0)`. A10-12's phrasing
  restates the javadoc's advertised capability, not an actual call site. Both agree the incremental cache
  is unused by the big inner loops.
- H1 (disk loose / circle-vs-disk gap) vs H2 (disk tight / dual-face degeneracy). NO shard-to-shard
  contradiction. A03-6 is the only MEASUREMENT and it reads H2 - but on j828 ONLY, a single hard capture,
  NOT the coupled multi-jump class. A01-3, A07-9, A07-11 explicitly defer the multi-jump H1/H2 to Stage 0.
  Resolution: record H2 as established for j828; leave multi-jump (thousand/1, f2f-dfchain-multijump) OPEN
  for COPT. Do not let A03-6 be quoted as "multi-jump is H2".
- A07 seam-constraint handling vs the angle-solver.md audit text. A07-7 finds current `sliceConstraints`
  DROPS seam-straddling relative/velocity pairs, contradicting the audit doc's "re-checked as trivial
  tick-0" claim. RESOLVED in favor of the code (A07-7): the doc text is stale; the full-run byte-exact
  re-verify is the actual backstop. Flag for the doc to be corrected.
- LatticeRepair status. A03-12 and A10-3 AGREE (dead); the CONFLICT is with the CONTEXT PACK (section 3
  lists it live). RESOLVED against the pack (verified: zero callers in main).
- Give-back magnitude. A08-10 "~1.63e-2 b" vs handoff "~2*floor + 3e-4". Same double-count, consistent;
  confirmed arithmetically from the verified constants (8e-3 + 8e-3 + 3e-4).

---

## Open questions routed to Stage C/D/E

- (Stage 0/E, gates capability 4) H1 vs H2 for the COUPLED MULTI-JUMP class via COPT SOCP disk slack + SDP/
  Shor rank on f2f-dfchain-multijump.json, df-chain-free-start.json, and thousand/1; plus the single-jump
  j001 and the j828 cross-check. A03-6's H2 holds only for j828. (F1, A07-11, A01-3)
- (Stage E) Validate CostateDualSolver's bound tightness vs the true continuous optimum per capture; how
  often does the `U_TOL` u-space early exit land OFF the optimal face (A03-14 measured pgres 0.117 exit on
  j828)? Is the 4.7404 dual delta the true optimum or loose?
- (Stage D/E) Does a single MIP over the ~2n gate binaries land loopmm-3jump and dsf-neo at the byte-exact
  objective AND certify infeasibility where BnB returns null, without being slower than the banded fast
  path? (F5, A05-9)
- (Stage B/E) Fraction of free-start corpus solved by center-pin+translate vs needing the joint dual
  (FreeStartSweepBench, 104 captures). Decides whether the joint dual demotes to a rare fallback. (A06-14)
- (Stage D/E) A single theta-space descent on `beta*||D2 theta||_1 + gamma*||D1 theta||_2^2` under the
  byte-exact walls, A/B'd on reversal sums vs the current four-pass stack on the hpk corpus; COPT reference
  for the reversal-minimal feasible path per capture. (F3, A08-8)
- (Stage B) stepRange-backed incremental rescoring in BucketAscentPolish/IlsPolish/WrapWindowIls: measured
  speedup vs the full-forward baseline (A02-6 predicts ~2x). (F12)
- (Stage B) Cross-window dual warm-start on the overlap: CostateDualSolver.lastIters per window on j001
  with vs without a seeded lambda (A07-12); cross-solve solver cache keyed by (n, wall-structure) vs the
  53%-capped-solve share (A01-5). 
- (Stage E) Per-window byte-exact micro-ascent on the terminal window: does it close the ~0.05 b interior
  under-reach (j022 LP vs byte-exact 0.0494 b)? (A07-6)
- (Stage D) A03-14/A07-11: the in-house disk AL-FISTA does NOT converge at n~353 (j001 bestViol 15.5 after
  731 ms); is a proper interior-point SOCP the right kernel, weighed against the loader packaging cost?
- (Stage B/C) A02-8: measure the JumpLinearModel legacy-diagonal hardcode divergence on a sine262 force-45
  capture (currently UNMEASURED); either mirror ExactJumpModel step(4) or refuse closed-form there.
- (Stage C) A07-15: re-run the window/commit 20/20 grid and the ~5-jump coupling horizon on the CURRENT
  build (the numbers predate the CMA-removal train PRs 373/375).

---

## Corrections to the context pack (`00-context-pack.md`)

1. Section 3, RelaxationRecovery line: "SOCP ball relaxation (|u_t|<=m_t) via augmented-Lagrangian FISTA +
   dither rounding + budgeted SLP + lattice repair + pin ladder" is STALE. VERIFIED current code =
   disk AL-FISTA + dither/projection realization + `SlpSolve.optimizeBestEffort` over a seed-margin ladder.
   There is NO LatticeRepair call and NO pin ladder in RelaxationRecovery. (A03-12; grep confirmed.)
2. Section 3 lists LatticeRepair.java among live solver internals. VERIFIED DEAD: zero callers in
   `core/src/main` (only the class definition + two test screens, EngineFileScreen/RelaxDiagScreen).
   `FacingLattice.stepToSinBucket` is likewise test-only. Both are deletion candidates. (A03-12, A10-3.)
3. The audit brief's "FacingReconstruction" (>180 deg delta limit) names a class that does NOT exist
   (glob returned nothing). The delta-vs-absolute realization is `JumpPhysicsInputs.toGameFacings` via
   `Angles.wrapDelta`. (A10-8.)
4. Section 5, "sine-bucket gap ~3e-4 b": REFINE. A10 measures the bucket pitch at the objective tick at
   ~1.5e-4 b and the half-angle norm>1 error at max 9.6e-5 (~1.5e-4 b of reach); the 0.007 b continuous->
   byte-exact X drop on thousand/1 is ~45x larger than EITHER, so the multi-jump gap is NOT the grid and
   NOT the half-angle - it is the fixed-modulus recovery (F1). (A10-1, A10-6; probe-measured, not re-run.)
5. Section 5 "Smoothing, FOUR stages, all measured to contribute": REFINE. Three chase the same L0 reversal
   count; SmoothingPolish uses the wrong convex metric (L2-D1) and is measured-blind to a dither vs a
   monotone step of equal magnitude - it is weak-because-wrong-metric, not weak-because-redundant. Two of
   the four (SmoothingPolish's base roughness) fire even with the Smooth checkbox OFF. (F3, A08-9, A09-5.)
6. Section 3, "smoothLambda>0" gating for all smoothing: PARTIAL. SmoothingPolish runs unconditionally in
   SmoothingNode; only DeWiggle, turnCost, and the engine face-walk are gated on smoothLambda>0. (A09-5.)
7. Section 5 lists the known-hard issue as thousand/1 with the dual grinding at pgres~2.4. The corpus
   analogues that ship (j008b-2jump, razor-proof, loopmm-3jump, j828) reproduce the same degenerate-recovery
   phenomenon and are cheaper to drive than the external thousand save; use them for the COPT H1/H2 work.
   (A01-2, A03-6.)
