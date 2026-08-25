# Stage A shard A05: BoundPrunedRecovery and the inertia-gate branch-and-bound

Agent: A05
Territory: branch-and-bound over the inertia (momentum-clamp) gate patterns (`solver/BoundPrunedRecovery.java`) and its interaction with `JumpLinearModel.velocityWalls/keepAliveWall/zeroingPattern` and `SlpSolve`.

Files inspected:
- `core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/BoundPrunedRecovery.java` (full)
- `core/.../solver/JumpLinearModel.java` (full; `velocityWalls` 278, `keepAliveWall` 307, `zeroingPattern` 324, `coefAxis` 186, `constPos` 194)
- `core/.../solver/ExactJumpModel.java` (inertia gate 145-160, `inertiaThreshold`/`perAxisInertia` 75-81, `forMcVersion` 105-111, `zeroSubThresholdVelocity` 83-94)
- `core/.../solver/ClosedFormSolve.java` (`optimizeWithPattern` 81-88, pattern inference loop 306-349, `velocityWalls` 373)
- `core/.../solver/SlpSolve.java` (inertia-aware re-derivation 187-233)
- `core/.../solver/SeamSweepRecovery.java` (full), `core/.../solver/LatticeRepair.java` (gate grep: none)
- `core/.../solver/FreeStartSolve.java` (gate grep: `zeroingPattern` 472, `velocityWalls` 516, `patternEffective` 498), `SmoothFaceRecovery.java` (`zeroingPattern` 225)
- `core/.../graph/nodes/BnbNode.java` (full), `core/.../graph/BuiltinGraphs.java` (full)
- Trace-file measurements (SolverTrace output from earlier runs of this exact code path, not re-run this session because gradle is blocked): `core/build/reports/solver-trace-{loopmm,dsf-bnb,dsf-fix,thousand,t6loopmm}.txt`
- Memory: `project_383_loopmm_winback.md`, `project_dsf_neo_inertia_solve_fail.md`, `reference_jumps_not_nonconvex.md`; `docs/research/angle-solver.md` sections 10-12

---

## A05-1: The gate is a mixed-integer indicator; BnB enumerates fixed per-axis suffix/single-tick zeroing realizations and folds each into an affine model
LOCATION: `ExactJumpModel.stepRange` 152-160; `JumpLinearModel.coefAxis` 186-191, `velocityWalls` 278-302; `BoundPrunedRecovery.enumeratePatterns` 342-371, `addPattern` 373-388
CLAIM: The MC momentum clamp "if `|v_axis*0.91| < thr` then that axis's carry is zeroed" (per-axis `thr`=0.005 1.8 / 0.003 1.12; combined `|v|^2 < 9.0e-6` modern) is a per-tick, per-axis binary indicator; BnB does not solve the indicator, it ENUMERATES a small fixed family of zeroing patterns (`free`; per-axis suffix `zx@k`/`zz@k` = zero from k to n; single-tick `zx1@k`/`zz1@k` = zero only at k; combined `zxz@k`/`zxz1@k`), folds each into the affine map by cutting friction propagation at the first zeroed tick (`coefAxis` stops at `zNext[axis][s]`), and enforces the assumed pattern with two linear "band-in" walls per zeroed tick (`velocityWalls`, `|v_axis(t)| <= thr`).
EVIDENCE: `addPattern` builds `new JumpLinearModel(sc, zeroX, zeroZ)` then `lin.velocityWalls(thr)`; drops the pattern if `vel.isEmpty()` (no input reaches a zeroed tick) or `rootBound==null` (dual unbounded). `enumeratePatterns` iterates `k in [1,n)`: per-axis emits `zx@k,zz@k` and (k<n-1) `zx1@k,zz1@k`; combined emits `zxz@k` and (k<n-1) `zxz1@k`. Candidate count before the `maxPatterns` cut is `4n-6` (per-axis) or `2n-3` (combined), re-derived from the loop.
IMPACT: correctness (this is the machinery that lands momentum-clamp jumps, loopmm/dsf-neo, whose feasible basin does not exist in the clamp-free model); simplicity (a bespoke ~1240-line stage for one discrete layer).
PROPOSAL: keep as the reference behavior; candidate for collapse into a single indicator formulation (A05-9).
CONFIDENCE: 0.95
DEPENDS-ON: A05-6

## A05-2: Measured pattern counts, node counts, and tree depth per capture
LOCATION: `enumeratePatterns` 342-371 (cap `cfg.maxPatterns`, default 64 at 23-36 / `BnbNode` 33); per-pattern `Search.run` 753-791
CLAIM: The enumerated-and-triaged pattern set is small (single digits to the 64 cap); per-pattern tree searches run hundreds to a few thousand nodes to depth ~9-15 inside a time-sliced budget.
EVIDENCE (SolverTrace, files above):
- dsf-neo (n=13, m=8, X MIN, `solver-trace-dsf-fix.txt`): exactly 4 patterns survive root triage, all single-tick `zx1@4..zx1@7`; solved by the banded-incumbent path (A05-3) at 83 ms, ZERO tree nodes.
- loopmm (n=33, m=9, X MIN, `solver-trace-loopmm.txt` and `t6loopmm.txt`): 9 patterns (`zx@25..zx@32` + `free`); per-pattern tree over a 48 s slice ran nodes = {zx@28:5071, zx@29:4643, zx@30:4831, zx@25:498, free:572}, pruned {1239,1369,1476,77,78}, restoreHits {2586,2640,2675,388,434}; deepest pruned nodes logged at depth 9-12.
- thousand (n=49, m=22, X MAX, per-axis, `solver-trace-thousand.txt`): >=38 patterns logged (`zx1@`/`zz@`/`zz1@` mix) up toward the 64 cap; per-pattern nodes small under the slice (`zx1@8` reached node#80 depth 15, `zx1@19` node#62 depth 16) because the budget is sliced across many patterns (`sliceNanos` 172-177).
IMPACT: speed/robustness (the discrete layer itself is tiny; the cost is the per-pattern continuous restore/SLP, not combinatorial explosion of patterns).
PROPOSAL: this is the deterministic baseline for Stage E node-count comparisons against a single MIP.
CONFIDENCE: 0.8 (trace-file numbers, not re-run this session; the enumeration formula and pattern labels were re-verified against current code)
DEPENDS-ON: A05-1, A05-3

## A05-3: Most gate jumps are landed by the banded-incumbent fast path, NOT the tree
LOCATION: `BoundPrunedRecovery.solve` 123-133 (loop calling `ClosedFormSolve.optimizeWithPattern`); `ClosedFormSolve.optimizeWithPattern` 81-88 and pattern inference 306-349
CLAIM: Before any tree Search, `solve` tries each patterned candidate through `ClosedFormSolve.optimizeWithPattern` (a plain closed-form dual recovery on the pattern-folded model, byte-exact gated); this closed-form pass, not the branch-and-bound tree, is what solves dsf-neo and is the first incumbent on loopmm.
EVIDENCE: `solver-trace-dsf-fix.txt` logs `banded incumbent zx1@4=-8086.296335803` and no tree nodes for that capture; memory `dsf-neo-inertia-solve-fail` records the save solving FAST in ~130 ms via "closed form -> pattern B&B (first feasible)" with winning pattern `zx1@4` and no cap/slicing pressure. The tree (`Search`) only runs on patterns that survive `viable` filtering (141) after this incumbent is set.
IMPACT: simplicity (the expensive tree is often dead weight; the load-bearing mechanism is closed-form recovery on a fixed folded pattern, which duplicates `ClosedFormSolve.optimizeWithPattern`).
PROPOSAL: measure how many corpus gate-captures the banded loop alone lands vs those that require the tree; if the tree is rarely decisive, it is a prime deletion candidate.
CONFIDENCE: 0.85
DEPENDS-ON: A05-1

## A05-4: keepAlivePatterns adds the band-OUT complement on the objective axis; it is the loopmm-class fix and is reference-driven, not enumerated
LOCATION: `keepAlivePatterns` 394-415; `JumpLinearModel.keepAliveWall` 307-322; added at `solve` 114-122 (after the root incumbent)
CLAIM: The zeroing patterns only branch "carry IS inside the band"; `keepAlivePatterns` supplies the complementary "carry is OUTSIDE the band, on the objective-improving side" (one `keepAliveWall`, `v >= thr` for MAX / `v <= -thr` for MIN), nominated at up to `MAX_KEEP_ALIVE=4` ticks where a reference realization trips the gate (`|v[k]| < thr`) OR reverses sign (`v[k-1]*v[k] < 0`). Reference = `rankYaws` else `freeDualSeed` (margin-0 dual recovery of the free model).
EVIDENCE: memory `383-loopmm-winback` (verified against current code lines): only `keepZ@4+` lands loopmm-3jump; each `Z@k+` tested alone returned the unchanged objective; the gate-OR-reversal union is what selects tick 4. `keepAliveWall` bounds are near-duplicate of the free bound (differences ~1e-7, below the dual noise floor), so bounds cannot rank them; nomination must be reference-driven.
IMPACT: correctness (recovers a redirect/momentum jump class the free relaxation is structurally blind to); robustness caveat (depends on a single reference trajectory tripping the right indicator; either indicator alone misses on one of the two references).
PROPOSAL: in a unified indicator MIP (A05-9) the band-in / band-out disjunction is one binary per (tick,axis); the bespoke keep-alive nomination heuristic disappears.
CONFIDENCE: 0.9
DEPENDS-ON: A05-1, A05-9

## A05-5: BnB is an INCOMPLETE, F-blind, heuristic-restore feasibility finder; the graph nonetheless treats its null as a no-solution verdict
LOCATION: gate 60-63 (`hasFacingWall && FacingPrefold.analyze==null -> null`); `enumeratePatterns` pattern family 354-366; `restore` 868-997 (capped `cfg.restoreIters=45`); `BnbNode` FIRST_FEASIBLE 49-60; `BuiltinGraphs` `coldBnb`/`rescueBnb`/`nearBnb` 109-147, 117-122, cold routing `rColdHave` FALSE -> `emit` 238-239
CLAIM: `BoundPrunedRecovery.solve` returning null is NOT a certificate of infeasibility, yet in the Optimize graph the cold-miss branch (`coldBnb` null) routes straight to `emit` (declares no solution), with `SeamSweepRecovery` unreachable in that branch.
EVIDENCE (three independent incompleteness sources, all in code):
1. Pattern space is finite and structured: `free` + per-axis {suffix from k, single-tick at k} + combined variants + <=4 keep-alive. Isolated MULTI-tick interior zeroings (zero at k, re-accelerate, zero again at j), and any off-objective-axis keep-alive, are outside the family. Memory `dsf-neo` recorded the pre-gh-392 miss ("single-tick zeroing with re-acceleration is outside the pattern space -> no viable pattern"); the fix added single-tick masks but the family is still not the full 2^(2n) indicator lattice.
2. The bound is position-only and F-blind: `compileWall` returns null for Mode.F (`JumpLinearModel` 222) and `seamKey` rejects F (`Search.seamKey` 746-751), so facing constraints are absent from every dual bound and every branch; only the final byte-exact gate (`offer`/`normIfFeasible`, `compiled.maxViolation` 288, 1201) enforces them. A dF solve therefore searches with a loose (over-optimistic) bound and can fail to restore into the F sector.
3. `restore` is a damped Gauss-Newton capped at 45 iters with a `probeWorst` fallback; it can stop above `feasTol` on a node whose true feasible point exists.
Memory `reference_jumps_not_nonconvex` records BnB false-negativing nix-tail seams that the (removed) CMA-ES solved; ruling: "do not use BnB feasible/NULL to measure a basin."
IMPACT: correctness (a false-negative in `coldBnb`/`rescueBnb` surfaces to the user as "no solution" for a jump that is solvable); this is the single most dangerous use of this stage.
PROPOSAL: never let BnB null alone decide the no-solution verdict; route a cold miss through `SeamSweepRecovery` (which has its own cold search, `run` 91-101) before `emit`, or make the discrete layer complete (A05-9). This is a graph-wiring change (A09 territory) driven by this stage's incompleteness.
CONFIDENCE: 0.85
DEPENDS-ON: A05-1

## A05-6: The per-pattern dual bound is a valid CONDITIONAL upper bound, so outer pruning is sound; incompleteness is the only unsoundness
LOCATION: `rootBound` 448-464; outer prune 140-147 (`p.normBound > seedNorm + pruneTol`); node prune 760-763, 813-817
CLAIM: `p.normBound` upper-bounds the best trajectory that realizes EXACTLY pattern p (the `velocityWalls` pin `|v|<=thr` makes the folded affine map exact on p's realizations, and the position-only dual of the disk relaxation bounds it), so pruning a pattern whose conditional bound <= the byte-exact incumbent is sound; the `free` bound only bounds the clamp-free set and does not upper-bound the gated set. The unsoundness is purely the incomplete pattern SET (A05-5), not the pruning.
EVIDENCE: memory `383-loopmm-winback` measured zeroing-pattern bounds EXCEEDING the free bound (`zx@28`=-279.2965 vs free -279.2990), confirming patterns are disjunctive branches, not restrictions of a single relaxation; the incumbent used for pruning is byte-exact feasible (`normIfFeasible` 282-291), so it is a genuine achievable floor.
IMPACT: correctness (clarifies that the design is sound where it searches; the risk is entirely "didn't enumerate the winning pattern").
PROPOSAL: a formulation whose relaxation bounds the WHOLE gated set (single MIP) would give one global bound instead of a union of conditional bounds, and would make infeasibility a real certificate.
CONFIDENCE: 0.8
DEPENDS-ON: A05-1, A05-5

## A05-7: The gate-pattern machinery is duplicated across five solvers; LatticeRepair and SeamSweep do not model the gate at all
LOCATION: `JumpLinearModel.zeroingPattern`/`velocityWalls`/`keepAliveWall` consumed by: `BoundPrunedRecovery` (383, 406), `ClosedFormSolve` (330, 373, `patternEffective` 349), `FreeStartSolve` (472, 516, `patternEffective` 498), `SlpSolve` (208), `SmoothFaceRecovery` (225); `LatticeRepair.java` (grep: zero gate references)
CLAIM: Five separate solvers each re-derive or re-fold the inertia pattern with their own copy of the logic (including two byte-identical private `patternEffective` helpers in `ClosedFormSolve` 349 and `FreeStartSolve` 498), while `LatticeRepair` (sine-bucket snap) and `SeamSweepRecovery` (corridor cell sweep) are gate-BLIND, so they are complementary to BnB, not duplicative of it.
EVIDENCE: grep results above. `SeamSweepRecovery` extracts corridor seams and pins cells (`extractSeams` 367-405, `sweepDims` 200-278); it never calls `velocityWalls`/`zeroingPattern`. It overlaps BnB only in the SEAM-corridor branch-and-cut idea (both wrap opposing GE/LE walls into `[lo,hi]` bands and bisect), not in gate handling; see BnB `Search.build` band construction 628-728 vs `SeamSweepRecovery.axisBands` 407-434.
IMPACT: simplicity (the gate-folding concept has 5 implementations; a single shared indicator module would remove the duplication and the two identical `patternEffective` copies); robustness (drift risk between the copies).
PROPOSAL: extract one gate/indicator model consumed by all stages, or subsume it in a MIP (A05-9). Note for Stage C: BnB's seam-band bisection and SeamSweep's cell sweep are two implementations of the same opposing-corridor idea and should be reconciled.
CONFIDENCE: 0.9
DEPENDS-ON: A05-1

## A05-8: SlpSolve re-derives the realized pattern per LP iteration; the BnB tree nests this adaptive SLP inside its fixed-pattern nodes
LOCATION: `SlpSolve` inertia-aware block 208-233 (`zeroingPattern` each iter, rebuild patterned walls on change); BnB tree call `Search.run` 775-780 (`SlpSolve.optimizeBestEffort(..., inertiaAware=true)` for patterned nodes, plain `SlpSolve.optimize` otherwise)
CLAIM: There are two distinct gate strategies in play at once: BnB fixes a pattern per tree branch, while the SLP it calls inside each patterned node RE-derives the pattern from the current facings every iteration and rebuilds walls; the two can disagree within a single node.
EVIDENCE: `SlpSolve` line 208 calls `lin.zeroingPattern(theta, ...)` per iteration and, on change (209), recompiles all walls against a fresh `new JumpLinearModel(sc, zeroX, zeroZ)`; BnB passes `patterned` through to select `optimizeBestEffort` (776) which runs that adaptive path. The tree node already assumes a fixed folded model (`Search.lin`), so the inner SLP's per-iteration re-fold is a second, independent gate handler stacked on the first.
IMPACT: simplicity/robustness (nested, partially redundant gate handling; hard to reason about which pattern actually governs a node's result).
PROPOSAL: pick one gate strategy (per-iteration adaptive OR fixed-pattern-per-branch), not both nested.
CONFIDENCE: 0.75
DEPENDS-ON: A05-1

## A05-9: The gate folds cleanly into big-M indicator constraints; a single MIP/MIQCP could replace the bespoke suffix-pattern B&B (Stage D seed)
LOCATION: whole stage vs the indicator structure in `ExactJumpModel.stepRange` 152-160, `JumpLinearModel.velocityWalls`/`keepAliveWall` 278-322
CLAIM: Each (tick t, axis a) carries a binary z_{t,a}=1 iff `|v_{t,a}| < thr`; `velocityWalls` already encodes the z=1 side (`|v|<=thr`, two linear rows) and `keepAliveWall` the z=0 side (`|v|>=thr`, one linear row), and the position map is affine given the z's (friction propagation cut at zeroed ticks). So the entire discrete layer is a standard big-M indicator system over ~2n binaries linking v to z and switching the affine coefficients, solvable by MILP/MIQCP branch-and-cut in one model, instead of enumerating O(n) suffix/single-tick patterns and running an independent tree + heuristic restore per pattern. The residual nonconvexity (per-tick modulus `|u_t|=m_t`) is orthogonal and remains whichever way the gate is handled.
EVIDENCE: UNMEASURED-HYPOTHESIS. Experiment: in the COPT harness, export a gate-using capture (loopmm-3jump, dsf-neo) via the StructureDump; build (a) a MILP with the gate as indicator constraints over the affine (disk-relaxed) model and (b) a MIQCP adding the modulus as a nonconvex equality; compare COPT node count, wall-clock, and feasibility completeness against the trace-measured BnB numbers in A05-2 (loopmm 9 patterns x ~500-5000 nodes/48 s; dsf 4 patterns, 0 tree nodes). Discriminator for whether the MIP even helps: does a single indicator model land loopmm-3jump and dsf-neo at the byte-exact objective without the pattern enumeration, and does it CERTIFY infeasibility where BnB only returns null (A05-5)?
IMPACT: simplicity (potentially deletes the pattern enumeration, keep-alive nomination, per-pattern restore, and the nested SLP), correctness (a real infeasibility certificate), speed (unknown; the modulus nonconvexity may dominate and make the MIP slower than the microsecond banded path of A05-3, which is the risk).
PROPOSAL: prototype in COPT before any commit; if the MIP is slower on the easy banded cases, a hybrid (banded closed-form fast path, MIP only on cold miss) preserves A05-3's speed while fixing A05-5's false-negative.
CONFIDENCE: 0.55
DEPENDS-ON: A05-1, A05-5, A05-6

## A05-10: Consistency matrix for BoundPrunedRecovery (caching / smoothing / defaults / dF / free-start)
LOCATION: as cited per row
CLAIM: BnB is caching-free, smoothing-blind, split-defaulted, dF-gated-and-F-blind, and free-start-blind (treats the start as fixed).
EVIDENCE (file:line):
- CACHING: ABSENT. Every node builds a fresh `CostateDualSolver` (`makeNode` 805) and calls the FULL `exact.forward` (grep: 9 `exact.forward`/`forward(` sites, 0 `stepRange`), including up to `restoreIters=45` full forwards per node in `restore` 874-947 plus line-search doublings 943-959 and `probeWorst` 1026-1041. Only warm-starting is the parent lambda (`makeNode` warm 806, child `parent.lambda` 798). No path memoization, no incremental tail recompute. Perf hot spot.
- SMOOTHING: ABSENT (grep smoothLambda/turnCost/DeWiggle/smooth in file: 0 hits). BnB maximizes/minimizes the raw single-axis objective (`normObjective` 1224-1227); a Smooth (TAS) solve gets a jittery incumbent and relies entirely on the downstream `smoothFinal` node (`BuiltinGraphs` 160-161). Gap vs the smoothing-aware ranking elsewhere.
- DEFAULTS: SPLIT. Direct `solve(...)` overloads use `new Config()` defaults (`BoundPrunedRecovery` 23-36: searchShare 0.8, pruneTol 1e-6, maxPatterns 64, restoreIters 45, etc.); the graph path overrides all of them from `NodeCatalog` params via `BnbNode` 28-40. Two sources of truth for the same knobs.
- dF (facing): GATED then IGNORED IN BOUNDS. Entry refuses only when `hasFacingWall && FacingPrefold.analyze==null` (60-63), so dF=0 pins pass and dF-inequalities are refused (memory `dsf-neo` item 4, re-verified). Once past the gate, F walls are dropped from every dual bound and branch (`compileWall` null for Mode.F, `seamKey` rejects F 746-751); only the byte-exact `offer`/`normIfFeasible` gate enforces dF (1201, 288). Bound is F-blind/loose; acceptance is F-exact. FacingPrefold is used ONLY as the entry predicate, not to reduce the problem.
- FREE-START: BLIND / FIXED. `constPos` 194-200 reads `sc.startBox` px/pz/vx/vz as CONSTANTS (the box's current point), never adding start coords as free variables; BnB has no notion of `StartBox.startFree()` (`StartBox` 41-47). The graph resolves free-start upstream (`freeImprove`, `BuiltinGraphs` 94-95) and BnB runs on the pinned result. If a free box reaches BnB unresolved, it silently searches at `box.px/pz`. Consistency gap: free-start is composed by ordering, not by the search itself (contrast `FreeStartSolve` which has its own joint dual + its own gate machinery, A05-7).
IMPACT: simplicity/correctness (five capabilities each handled differently here than in sibling stages; the caching absence is the main speed lever, the smoothing/free-start blindness are the main consistency gaps).
PROPOSAL: feed these rows into the Stage C consistency table; the caching-absence and full-forward-per-node cost are Stage B benchmark targets.
CONFIDENCE: 0.9
DEPENDS-ON: A05-1, A05-5, A05-7
