# Stage A shard: agent A03

Agent: A03
Territory: closed-form recovery, the SOCP disk relaxation, and the margin ladder.
Primary files: `solver/ClosedFormSolve.java`, `solver/RelaxationRecovery.java`.
Support read: `solver/CostateDualSolver.java`, `solver/JumpLinearModel.java`, `graph/nodes/DualChainNode.java`, `AngleSolverEngine.dualChain` (1703-1744), `AngleSolverEngine` smoothing hook (1060-1084), `RelaxDiagScreen.java`.

Measurements were run with direct `java -cp` probes against the already-compiled `core/build/classes` (no gradle). Two probes written to scratchpad and compiled clean on JDK 25:
- `RelaxSlackProbe`: reflectively invokes the package-private `RelaxationRecovery.relaxedPrimal` disk-SOCP FISTA, then reads per-tick `|u_t|/m_t` and the reduced costate `g_t = c_t - A^T lambda` at each interior tick.
- `EndToEndProbe`: times `ClosedFormSolve.optimize` and `RelaxationRecovery.solve` on j828, verifies byte-exact via `ExactJumpModel`.
Captures probed: `hpk/d11/j828-1bm_5.3125-1.5` (the dualrecovery hard case), `j001` (353-tick run), `f2f-dfchain-multijump`, `df-chain-free-start`. Locale prints decimal comma; transcribed here as points.

---

## Findings

### A03-1
TITLE: ClosedFormSolve is the microsecond fast path: dual + on-sphere costate recovery + inertia passes + inward-margin ladder.
LOCATION: `ClosedFormSolve.java` optimizeReturning 112-128, solveWithPrefold 299-347, runLadder 357-432.
CLAIM: `optimize` prefolds facing pins/dF chains (`FacingPrefold`), builds one `CostateDualSolver` over the position walls, and per inertia-pass runs a margin ladder that recovers each tick's yaw from its friction-propagated costate and re-checks byte-exact on `ExactJumpModel`, returning strictly-feasible facings or null.
EVIDENCE: read of the three methods; `runLadder` calls `solver.solve(margin, warm)` then `violOnExact` and returns on `viol <= feasTol` (419-422). The recovery direction is always on the sphere: `CostateDualSolver.grad` sets `u*_t = m_t g_t/|g_t|` (411-418).
IMPACT: speed (the sub-millisecond path for the whole single-jump class).
PROPOSAL: keep as the first stage; it is the LCvx closed form and nothing here is faster.
CONFIDENCE: 0.97
DEPENDS-ON: -

### A03-2
TITLE: RelaxationRecovery is the disk-SOCP rescue: augmented-Lagrangian FISTA on `|u_t|<=m_t`, realized back to the sphere by two seeds, then finished by budgeted SLP, over a seed-margin ladder.
LOCATION: `RelaxationRecovery.java` solve 26-66, seedAtMargin 74-141, relaxedPrimal 143-262, ditherSeedYaws 299-336, projectionSeedYaws 344-364.
CLAIM: For each seed margin it warm-solves the same `CostateDualSolver`, seeds `u` on the sphere from the dual costate, runs `relaxedPrimal` (FISTA projecting each tick onto the disk of radius `m_t`, 205-209; AL multiplier updates 253-257; rho growth 256), realizes the disk optimum onto the sphere via `ditherSeedYaws` (carries a forward velocity error, 331-333) and `projectionSeedYaws` (radial), then hands each seed to `SlpSolve.optimizeBestEffort` (125-126) and keeps the first byte-exact-feasible result.
EVIDENCE: read; the disk projection is the only place in the whole solver that can produce `|u_t| < m_t` (compare `CostateDualSolver` which is always on-sphere).
IMPACT: robustness (recovers the degenerate-dual multi-jump cases the closed form drops; see A03-11).
PROPOSAL: keep; this is the working rescue. But see A03-10/A03-12 for scope limits and stale-doc removal.
CONFIDENCE: 0.96
DEPENDS-ON: A03-1

### A03-3
TITLE: The ClosedFormSolve inward-margin ladder trades objective for byte-exact feasibility one geometric rung at a time.
LOCATION: `ClosedFormSolve.java` margins 38 `{0,1e-4,3e-4,6e-4,1.2e-3,2.5e-3,5e-3,1e-2}`, marginsRobust 43, runLadder 387-427.
CLAIM: A rung tightens every wall inward by `margin` inside the dual solve; the continuous optimum hugs walls exactly, but the 65536-bucket sine table perturbs the realized path ~1e-4 b and can flip a tight wall infeasible, so the ladder grows the margin until the quantized path clears; the smallest feasible margin gives the best objective, so it stops on the FIRST feasible rung (419-422). Multiple rungs are needed because the required clearance is instance-dependent (which wall is tight, how the quantization lands) and unknown a priori. Other stops: dual unbounded => primal infeasible, ascending breaks (392-396); `rungStall >= rungStallLimit(2)` when violation stops improving (423-426); ladder exhausted. Each rung warm-starts the previous rung's multipliers (389, 397), so the ladder costs barely more than one solve.
EVIDENCE: read; `marginsRobust` (43) is the descending variant used by lead-in windows, largest-first, first-that-certifies wins (optimizeRobust 95-99).
IMPACT: robustness + simplicity (one scalar ladder absorbs the entire continuous-to-byte-exact quantization drop).
PROPOSAL: keep; the ladder is the cheap, general quantization cushion. A COPT/analytic per-wall margin (Stage 0/E) could in principle pick the exact rung in one shot, worth a measured comparison.
CONFIDENCE: 0.95
DEPENDS-ON: A03-1

### A03-4
TITLE: RelaxationRecovery has its own separate seed-margin ladder that only advances when no feasible sphere path has landed yet.
LOCATION: `RelaxationRecovery.java` seedMargins 14 `{0,3e-4,1.2e-3,5e-3,1e-2,2e-2,5e-2}`, ladder 50-62.
CLAIM: Margin 0 is tried first; subsequent margins run ONLY while `best == null` (55), i.e. the ladder is a feasibility fallback, not an objective search; a larger margin pushes the disk optimum further inside so the sphere realization survives quantization, at objective cost; it stops at the first margin whose realization is byte-exact feasible.
EVIDENCE: read; loop guard `best == null && !cancel.get()` at 55. Warm lambda carried across margins (56-57); 5 `dualRestarts` per margin (82-87).
IMPACT: simplicity (this is a second, structurally-identical ladder to A03-3 living in a different class with different rung values and different stop rule).
PROPOSAL: candidate for unification: one margin-ladder utility parameterized by (ascending/descending, stop-on-first-feasible/best-objective) would remove the duplicated logic across ClosedFormSolve and RelaxationRecovery.
CONFIDENCE: 0.9
DEPENDS-ON: A03-3

### A03-5
TITLE: The in-house disk SOCP is solved by AL-FISTA, and BOTH realization seeds discard the per-tick modulus, forcing every realized tick back onto the sphere.
LOCATION: `RelaxationRecovery.java` disk projection 205-209; ditherSeedYaws rescales each tick to `m*t/|t|` (326) and only redistributes the discrepancy as a forward-carried velocity error (331-333); projectionSeedYaws takes `atan2` of `(ux,uz)` and drops the magnitude (360).
CLAIM: The relaxation genuinely solves `max c.u s.t. Au<=b, |u_t|<=m_t` (a disk/ball SOCP), but the two realizations both put `|u_t| = m_t` at every tick, so any modulus slack at the relaxed optimum is thrown away and must be re-absorbed by the downstream SLP.
EVIDENCE: read of both seed builders; neither preserves `|u_t|`.
IMPACT: correctness/robustness (whether this discard is lossy is exactly the H1/H2 question, settled measured in A03-6).
PROPOSAL: none on its own; feeds A03-6.
CONFIDENCE: 0.97
DEPENDS-ON: A03-2

### A03-6
TITLE: KEY THEORY, MEASURED: on j828 the disk optimum leaves 13/39 ticks off-sphere, but every off-sphere tick has reduced costate `|g_t| <= 7.3e-10` (zero). This is H2 (dual-face degeneracy), not H1 (circle-vs-disk gap).
LOCATION: `RelaxationRecovery.relaxedPrimal`; discriminator computed in `RelaxSlackProbe`.
CLAIM: The modulus slack lives entirely in the `g_t = 0` indeterminate null-space of a degenerate dual face; the disk relaxation does NOT prefer `|u_t| < m_t` at any tick the objective or walls actually pin.
EVIDENCE: `RelaxSlackProbe` on `hpk/d11/j828` (n=39, 12 position walls, MAX Z@t39): disk `relaxedPrimal` bestViol=6.39e-9 (feasible on the disk), off-sphere counts `<0.999m:13  <0.99m:12  <0.9m:5  <0.5m:0`; maxDeficit=3.89e-3. All 12 interior ticks (t=1..12) report `|c_t|` = 8.68..4.24 (strong objective coupling) yet `|g_t|` = 3.24e-10..7.29e-10 (`g/m` <= 2.2e-8); `interiorTicksWith|g|>1e-6 = 0`. Per SOCP KKT the per-tick maximizer of `g_t.u_t` over the disk is on the sphere whenever `g_t != 0`, so interior ticks can only occur where `g_t = 0`; the data shows exactly that. The "13/39 off-sphere" handoff number REPRODUCES.
IMPACT: correctness (this is the Stage-0 H1-vs-H2 discriminator, previewed from the shipped solver: it points at H2, so the disk/ball relaxation is value-tight and target capability 4 is not blocked by a genuine circle-vs-disk gap on this case).
PROPOSAL: Stage 0 should confirm with COPT SOCP + SDP/Shor rank on the same capture; expect rank-one/value-tight with a flat dual face. The lever is the recovery on the null-space ticks, not a tighter relaxation.
CONFIDENCE: 0.9
DEPENDS-ON: A03-5

### A03-7
TITLE: MEASURED corroboration of A03-6: the sphere realization loses nothing in objective on j828; disk-primal value equals the byte-exact achieved value to within sine-table quantization.
LOCATION: `EndToEndProbe` + `RelaxSlackProbe` cross-check.
CLAIM: Because the off-sphere ticks are objective-neutral (`g_t = 0`), re-inflating them to the sphere in a feasible direction preserves the objective; the disk optimum and the final byte-exact solution carry the same objective delta.
EVIDENCE: dual bound (position walls) `constPos = 4973.283476`, dual delta `4.740380` => bound `4978.023856`. Disk-primal delta `4.730072` (RelaxSlackProbe). Byte-exact achieved obj `4978.013116` (EndToEndProbe) => achieved delta `4.729640`. achieved-vs-disk-primal = `4.32e-4` (a sine-quantization drop, ~1e-4 scale); both sit `~1.07e-2` below the dual bound. So the 0.0107 b "gap" is between two imperfectly-converged relaxation estimates (disk-primal 4.7301 vs dual-bound 4.7404), NOT a modulus-slack loss.
IMPACT: correctness/robustness (confirms the fold in A03-10 is objective-safe on this class).
PROPOSAL: Stage 0/E: quantify with COPT whether 4.7404 is the true continuous optimum or the dual is loose (`CostateDualSolver` here stopped at pgres 0.117, see A03-14).
CONFIDENCE: 0.88
DEPENDS-ON: A03-6

### A03-8
TITLE: The disk-SOCP solve is a bespoke reimplementation of what a convex SOCP library does; RelaxationRecovery also reuses the same `CostateDualSolver` as ClosedFormSolve and calls `SlpSolve` internally.
LOCATION: `RelaxationRecovery.java` builds `CostateDualSolver` (46) identical to ClosedFormSolve's (`runLadder` 379), rolls its own AL-FISTA (relaxedPrimal 143-262) with hand-written power-iteration Lipschitz (powerLambdaMax 264-297), and finishes with `SlpSolve.optimizeBestEffort` (125).
CLAIM: A single solve of a hard case touches the dual TWICE (once in ClosedFormSolve, once in RelaxationRecovery), an AL-FISTA disk solve, two sphere realizations, and SLP, several of which (dual, disk SOCP) are the same convex object solved by different bespoke methods.
EVIDENCE: read; the same `(cx, cz, mMag, walls)` object feeds `new CostateDualSolver(...)` in both classes.
IMPACT: simplicity (duplicated convex machinery; a permissively-licensed SOCP kernel could replace both the dual and relaxedPrimal, weighed against the loader packaging cost per the pack's dependency policy).
PROPOSAL: Stage 0/E: prototype one convex SOCP call replacing {CostateDualSolver + relaxedPrimal}, measure speed vs the microsecond closed form (the closed form will likely still win on the easy majority, so the SOCP kernel is a rescue-only candidate).
CONFIDENCE: 0.85
DEPENDS-ON: A03-2

### A03-9
TITLE: A single hard-case solve traverses 4 recovery stages inside `dualChain` alone, before the graph's later nodes.
LOCATION: `AngleSolverEngine.dualChain` 1703-1744.
CLAIM: Order is (1) ClosedFormSolve.optimize, (2) SlpSolve.optimize, (3) RelaxationRecovery.solve (gated by `RELAX_MIN_REMAINING_NANOS`), (4) alternate-objective reseed loop (ClosedFormSolve seed -> SlpSolve), each success followed by `levelSetTopUp` (LevelSetAscent). Downstream graph nodes (BnbNode/BoundPrunedRecovery, SeamSweepNode, IlsPolish, WrapIls, Smoothing) run after DualChainNode.
EVIDENCE: read of dualChain and DualChainNode; BnbNode/SeamSweepNode confirmed as separate graph nodes (NodeCatalog/BnbNode).
IMPACT: simplicity (deep stack; the fold in A03-10 targets stages 1-3).
PROPOSAL: none standalone; input to the collapse analysis.
CONFIDENCE: 0.95
DEPENDS-ON: A03-1, A03-2

### A03-10
TITLE: Folding opportunity: disk relaxation + one sphere realization already IS the working rescue on j828; the measured obstruction to folding it everywhere is that RelaxationRecovery is blind to dF and equality constraints.
LOCATION: `RelaxationRecovery.solve` 32-34 (bails on any facing wall or any EQ constraint).
CLAIM: Where the problem is pure X/Z inequalities, the disk-SOCP+realization path handles the degenerate-dual case that the closed form drops (A03-11), so it could subsume stages 1-3 for that class; but it cannot represent facing (dF) or equality constraints, so dF/EQ cases must still route through ClosedFormSolve's FacingPrefold or SLP.
EVIDENCE: `RelaxSlackProbe`: `f2f-dfchain-multijump` and `df-chain-free-start` both report `facingWall=true`; RelaxationRecovery.solve returns null on both by construction. With facing walls stripped (compileWalls ignores F-mode), f2f-dfchain is trivially tight (dual iters=0, gap 5e-12, 0 off-sphere) and df-chain-free-start is dual-unbounded, i.e. the stripped problem is not the real one.
IMPACT: simplicity/robustness (the fold is real for the pure-position class; dF/EQ is the hard boundary).
PROPOSAL: Stage design should treat "disk-SOCP recovery" as owning the pure-position degenerate class and keep a dF/EQ-capable path (prefold or an SOCP with rotated-cone dF sectors, prototyped in COPT) rather than assuming one mechanism covers all.
CONFIDENCE: 0.88
DEPENDS-ON: A03-2, A03-6

### A03-11
TITLE: MEASURED: on j828 ClosedFormSolve fails and RelaxationRecovery succeeds byte-exact; this is the concrete reason both stages exist.
LOCATION: `EndToEndProbe` on `hpk/d11/j828`.
CLAIM: The closed form cannot certify j828 through its margin ladder + 4 inertia passes; the disk-SOCP rescue does, at obj 4978.013116, byte-exact viol 0.
EVIDENCE: 3 warmed reps each: `ClosedFormSolve.optimize -> null` at 129.3 / 23.5 / 23.6 ms; `RelaxationRecovery.solve -> FEASIBLE obj=4978.013116 viol=0.000e+00` at 92.5 / 56.0 / 29.6 ms. dualBound=4978.023856 (achieved is 0.0107 b under the bound).
IMPACT: robustness (removing RelaxationRecovery would drop j828 and its dualrecovery class; the sidecar marks `shouldSolve:true`).
PROPOSAL: keep RelaxationRecovery until a folded replacement is measured to solve j828 at least as fast.
CONFIDENCE: 0.95
DEPENDS-ON: A03-1, A03-2

### A03-12
TITLE: RE-VERIFY: the handoff/pack description of RelaxationRecovery as "SOCP ball relaxation + dither + budgeted SLP + LatticeRepair + pin ladder" is STALE; current code has no LatticeRepair and no pin ladder, and LatticeRepair is dead in the shipped path.
LOCATION: `RelaxationRecovery.java` (whole file); `LatticeRepair.java`.
CLAIM: Current RelaxationRecovery = disk AL-FISTA + dither/projection realization + `SlpSolve.optimizeBestEffort` over a seed-margin ladder. No LatticeRepair call, no pin-ladder logic.
EVIDENCE: grep `LatticeRepair\.` across `core/src/main/**` returns NO matches; grep `LatticeRepair` returns only `LatticeRepair.java` itself. `SlpSolve.java` has no `LatticeRepair`/`pin` reference either. RelaxDiagScreen toggles `LatticeRepair.DEBUG` but nothing invokes it.
IMPACT: correctness of the mission's own map (context-pack section 3 overstates RelaxationRecovery and treats LatticeRepair as live).
PROPOSAL: fix the pack's RelaxationRecovery line; flag LatticeRepair as dead code (deletion candidate) unless a Stage owner reclaims it.
CONFIDENCE: 0.95
DEPENDS-ON: A03-2

### A03-13
TITLE: CONSISTENCY MATRIX for the two territory classes (caching / smoothing / defaults / dF / free-start).
LOCATION: as cited per cell.
CLAIM: The two classes diverge sharply on dF, EQ, smoothing, and free-start; only warm-start-across-rungs caching is shared.
EVIDENCE (file:line):
- Caching: ClosedFormSolve reuses one `CostateDualSolver` across margin rungs, warm-starting lambda (runLadder 379-397). RelaxationRecovery reuses one `dual` across seed-margins + 5 dualRestarts, carrying warm lambda (solve 46, 55-62; seedAtMargin 80-88). Neither caches ACROSS engine solves; the only cross-solve cache is `GraphContext.reachBound = ClosedFormSolve.dualBound(spec)` (GraphContext 157) computed once per solve.
- Smoothing: ClosedFormSolve exposes `recoverFace` (72-79) -> `SmoothFaceRecovery.smooth`, the final null-space face-walk, called from `AngleSolverEngine` 1081 with an objGuard (1065-1070) and dF-frozen ticks (1073-1078); it is Smooth-(TAS)-only and NOT part of the normal `optimize` path. RelaxationRecovery has NO smoothing hook.
- Defaults: ClosedFormSolve.Config margins/marginsRobust/maxInertiaPasses=4/rungStallLimit=2 (34-48). RelaxationRecovery.Config outerIters=30/innerIters=500/rhoStart=100/rhoGrow=3/rhoMax=1e6/seedMargins/dualRestarts=5/slpPhase1Calls=160/slpTotalCalls=220 (8-18). The two margin sets differ (A03-3/A03-4).
- dF / EQ: ClosedFormSolve HANDLES dF via `FacingPrefold` pins + dF=0 chains, with a single free chain scanned (`scanChain` 130-183, `candidateThetas` 190-280); EQ walls flow through the dual (eq multipliers). RelaxationRecovery BAILS on ANY facing wall AND ANY EQ (solve 32-34). MEASURED: both dfchain captures are facingWall=true and get null from RelaxationRecovery (A03-10).
- Free-start: NEITHER class does free-start itself. ClosedFormSolve builds its `CostateDualSolver` with NO `FreeP0` (runLadder 379, 5-arg ctor); free-start is added externally by `FreeStartSolve` (+ `PathTranslation`, `CostateDualSolver.FreeP0`), and `candidateThetas` accepts an optional `translationBox` (191, 195-196) for the free-start scan. RelaxationRecovery has no free-start awareness (uses `sc.startBox` implicitly through `constPos`).
IMPACT: simplicity/correctness (enumerates the exact capability gaps the spec's consistency question asks for).
PROPOSAL: spec should state that dF/EQ/free-start/smoothing are ClosedForm-or-external only; any "collapse to one recovery" must add dF/EQ/free-start to the disk-SOCP path or accept two paths.
CONFIDENCE: 0.92
DEPENDS-ON: A03-1, A03-2

### A03-14
TITLE: MEASURED interaction: `CostateDualSolver` declares convergence in u-space at pgres 0.117 (1 iter) on j828, but the AL-FISTA then finds a MORE degenerate face where 12 costates vanish.
LOCATION: `CostateDualSolver.solve` u-space early exit 224-231; `RelaxSlackProbe` output.
CLAIM: The dual's "recovered inputs stopped moving" exit (`du <= U_TOL`) fires while the projected-gradient residual is still 0.117, i.e. the dual returns a point off the true degenerate face; the AL then drives 12 ticks to `g_t = 0`. So the closed-form recovery reads a costate that is NOT the true dual optimum, which is one mechanism behind its j828 failure.
EVIDENCE: `RelaxSlackProbe`: dual `value=4.740380 iters=1 pgres=1.168e-1 stalled=false`, dual costate `|g_t|<1e-7 at 1/39 ticks`; after AL, 12/39 interior ticks have `|g_t|~1e-10`. On `j001` (n=353) the dual instead trips the divergence bail (`iters=23 pgres=1.452e+1 stalled=true`) and relaxedPrimal does NOT converge (bestViol 15.5 after 731 ms), showing the in-house disk SOCP is unreliable at large n.
IMPACT: robustness (names why closed form misses j828 and why the disk-FISTA is not a drop-in at n~350).
PROPOSAL: Stage 0/E: compare the true dual optimum (COPT) to `CostateDualSolver`'s early-exit point per capture; quantify how often the u-space exit lands off the optimal face.
CONFIDENCE: 0.85
DEPENDS-ON: A03-6, A03-11
