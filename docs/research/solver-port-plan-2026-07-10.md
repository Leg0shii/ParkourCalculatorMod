# Solver port plan: rung-5375 campaign findings into the v1.7.0 position-free solver

EXECUTED. Outcome recorded in angle-solver.md's port section: S3-S5 shipped, S6 shipped off-by-default after the A/B, the S7 headline gate MISSED with basin-discovery attribution. The section 11 AlmSnapStage deferral was closed by issue 380 (the machinery was removed, PR 382). Branch names and the MiqcpClose/SnapRepairPolish/FacingReconstruction references are historical. The companion solver-port-handoff-2026-07-10.md was consolidated into this file's Phase-1 validation record (section 1) and deleted.

APPROVED by the user 2026-07-10, with amendments: the old item 3 (legality knobs + winding re-expression) is DROPPED, and |gameFacing| is hard-capped at 360 as both default and max. This document is the exact plan for the executing session. Follow it step by step, in order; run the tests at every GATE; do not reorder, do not skip gates, do not re-ask for plan approval. Produced from a completed Phase 1 validation of docs/research/solver-port-handoff-2026-07-10.md plus one adversarial review (9 findings, all dispositioned into the specs below).

## 0. Binding rules

1. ANTI-STALE PROTOCOL, verbatim from next-session-lever2-2026-07-10.md section 1, for every env-gated test and solver run: fresh run tag per invocation, tag inside the artifact filename; grep the applied:-line and check artifact mtime before reading any number; gradle --rerun --no-daemon on env-gated runs; long runs as direct background commands with a watchdog from minute one, never subagent-supervised; orphan sweep after any kill.
2. The gate is a green full ./gradlew :core:test (--rerun --no-daemon at GATE boundaries). Never edit ExactJumpModel, McSineTable, Constants; no item in this plan requires touching them.
3. Core stays Minecraft-free, Java 8, no code comments, no em dashes in docs, Conventional Commits when the user commits.
4. GIT: the user does all commits and pushes. ONE explicit exception, authorized by the user 2026-07-10: the executing session runs the S2 merge of v1.7.0-position-free-solver into nix-backward-march, including conflict resolution and the merge commit. No other git mutation by the session: no commit, no push, no rebase of published history, no branch creation or deletion.
5. Solver tests are cold-start seedless (warm-starting from an answer is cheating). Regression fixtures go through the problems/ folder-driven ProblemsTest shape with a TESTS.md entry. One approved deviation: the S4 bounded polish regression starts from a recorded incumbent, because it tests the polisher, not a solve.
6. Locked RAW rows are the only valid realization for wrap-class points; display tick = internal + 1 in all user communication.
7. Escalation points, and only these: S1 precondition failure (dirty tree); S2 merge validation failure without an obvious resolution error; the S7 headline-gate delivery gate (pass or attributed miss); a certified blocker. Everything else: proceed.

## 1. Context, already validated 2026-07-10 (do not redo)

- Phase 1 validation complete. Discrepancies recorded: RazorBench benchmark 1 HAS run cold 8+ times (all MISS; never through the engine pipeline at t1 with a free start, which is what section 3 of the handoff actually gates); MomentumAssembly also consumes the free box (engine ~line 848), not only solveJoint + freeStartImprove; the ExactJumpModel working-tree diff is a 4-line additive modern() accessor only; core/tools/miqcp/rung5375-snap-point.json is a byte-identical stray duplicate (delete); the branch tip is b62f0c6, not dfa9ac7.
- setupPeel (commit 3f4fea9, only on v1.7.0-position-free-solver) is NOT redundant. Measured 2026-07-10 via EngineFileScreen on the extracted t25 fixture: the current engine without it stalls at 8/13 met for the full 120 s (liveFeasibleSeen=false); with it, 13/13 in ~4.5 s FAST inside a 30 s sidecar bar. The commit is purely additive: +113 lines AngleSolverEngine, +10 LongRunSolver (suffixSpec), fixture + sidecar.
- Fresh full :core:test baseline green (2m42s, 2026-07-10).
- Reviewer-verified facts the specs below rely on: a zero-width translation domain in SnapRepairPolish.bestTranslation returns tx=tz=0 and a viol byte-identical to JumpConstraintCompiler maxViolation (SnapRepairPolish.java ~1471-1494); Expect sidecars support effort, optimizeSeconds, maxSolveMs; wrap-class results are destroyed today by violationOf (engine ~1130 wraps), SmoothingPolish, IlsPolish, and the unlocked-row wrapDelta apply path, and FacingReconstruction.findDelta requires |delta| < 180.

## 2. S0: user pre-work (before the executing session starts)

The user commits the existing uncommitted state on nix-backward-march:

- C1 feat(solver): product classes + additive model accessors. New: SmoothJumpProblem, AlmBfgsCore, AlmSnapStage, FacingLattice, FacingReconstruction, SnapRepairPolish, HomotopyCloser, MomentumAssembly (all core/.../anglesolver/solver/). Modified: AngleSolverEngine (+30, MomentumAssembly hook), JumpLinearModel (+24/-4 additive accessors), ExactJumpModel (+4, modern() accessor, physics untouched). Product-only: compiles and the suite is green because the new tests do not exist yet at this commit.
- C2 test(solver): all new test sources (AlmBfgsCoreTest, CellSetDump, CoefDump, FacingLatticeTest, FacingReconstructionTest, GateMicrobenchTest, MiqcpClose, MiqcpDump, Nix* x13, NormCellProbe, PatternPinnedProbe, ProofNeighborhoodProbe, RazorBench, RazorFixturesTest, SmoothJumpProblemTest, SnapRepairPolishTest, StructureVariantDump, TranslationEliminationTest, harness/RazorFixtures) + captures (nix-full-t1.json, razor-*.json x11) + problems/solve/nix-full-t1.expect.json. Gate-green with C1 present.
- C3 docs(research): the untracked docs/research files (including this plan) + the nix-full-freestart.md modification.
- C4 chore(tools): either commit tools/miqcp .py tools + canonical point JSONs and .gitignore the bulk (dir is 23MB), or keep tools/ local entirely. User's call; the port does not depend on it because required reference points are copied into test resources in S4/S6.
- Cleanup: delete core/tools/miqcp/rung5375-snap-point.json (verified byte-identical duplicate of the tools/miqcp copy).

## 3. S1: preconditions (executing session, first actions)

1. Read, in order: this plan; docs/research/solver-port-handoff-2026-07-10.md (since consolidated into this file and deleted, 2026-08-21); docs/research/free-start-handoff.md section 0 (mandatory before touching start-position code); docs/research/razor-campaign-2026-07-09-handoff.md (standing traps); docs/research/next-session-lever2-2026-07-10.md section 1 (the protocol).
2. git status --porcelain: the tree must be clean. If any core/src file is dirty (S0 not done), STOP and escalate to the user. Untracked non-source leftovers (build outputs, scratch) may be ignored.
3. Fresh full ./gradlew :core:test --rerun --no-daemon: green. GATE.

## 4. S2: merge v1.7.0-position-free-solver into nix-backward-march (authorized)

1. On nix-backward-march: git merge v1.7.0-position-free-solver. Merge, not rebase: both branches are pushed; rebasing published history would need a force-push, which is forbidden.
2. Expected conflict: AngleSolverEngine.java only (their setupPeel +113 vs our MomentumAssembly hook +30). Resolution: keep BOTH additions. setupPeel fires only after receding horizon and dual chain both miss; the MomentumAssembly hook stays after the CMA race block, before freeStartImprove. LongRunSolver (+10, suffixSpec) and the fixture files merge clean.
3. Validation GATE: full :core:test --rerun --no-daemon green, explicitly confirming problems/solve/nix-t25-setup-tick passes (FAST, 30 s bar, 13/13) and problems/solve/nix-full-t1 passes. If red and the cause is not an obvious resolution slip, escalate with the conflict diff and the failure output.
4. This merge commit is the only commit the session makes.

## 5. S3 (P1): translation-aware scoring under a free start (~1-2 days)

- Thread the free box into runJob's post-race scoring instead of discarding it at the pin (~line 694): byte-exact arbitration comparisons, seam-sweep acceptance, B&B rescue acceptance, and the new S4 stage score translated when startFree(): viol via SnapRepairPolish.bestTranslation over the authored box domain, objective via bestTranslationObj. dualChain and LongRunSolver stay pinned (the convex path stays solveJoint per free-start-handoff section 0). The CMA race stays pinned (translated CMA fitness historically capped at 6/7; do not spread it).
- Central scoring helper; a pinned box means a zero-width domain, byte-identical by construction.
- Single terminal adoption step: before result finalize, apply the winning translation once (mutate sc.startPos, pin the box); every comparison downstream of any adoption uses one metric. If MomentumAssembly or freeStartImprove adopted a start mid-pipeline, recompute the translation domain relative to the authored box before further translated scoring.
- MomentumAssembly's existing free-box use is unchanged; no double translation.
- HARD CONTRACT: byte-identical behavior when the start is pinned; keep-better always.
- Acceptance: (a) unit test that the zero-width translated score byte-equals the pinned score on random gf across 3 captures; (b) a free-box case with a KNOWN NONZERO optimal translation solves and adopts it (a translated-lower-than-pinned assertion alone is tautological, do not use it); (c) full :core:test green including nix-full-t1 and nix-t25-setup-tick. GATE.

## 6. S4 (P2): wrap-window lattice ILS polish stage, |gf| <= 360 (~3-5 days)

- New core/.../anglesolver/solver/ class porting from MiqcpClose (test sources): candSetFor (adaptive span, norm filter, incumbent-cell injection), candFull, kickCells, applyKick, the 1-opt/2-opt/2optB rounds and kick cycles, scored through S3's translated helper. normAt via FacingLattice. Priority ticks from incumbent cell norms (> 1+1e-7), fallback all-ticks candFull. Deterministic RNG. Config constants: span 16, maxSpan 512, kick share.
- HARD CAP (user ruling 2026-07-10, replaces the old item 3): |gameFacing| <= 360 as default AND max. No knob, no UI. Wrap bases {0, +360, -360}; every enumerated cell filtered to |rep gf| <= 360; kickCells bases filtered the same way; the stage asserts maxGf <= 360 on its results. Consequence, accepted: wrap720-class points (maxGf ~718) are not expressible; the +-360 re-expressions keep the main wrap-window move class.
- Wrap-carrying contract (from the adversarial review; this is a contract change, not a wiring bypass): (a) the stage runs TERMINAL, immediately before result finalize, acceptance scored in gf space so no wrapped re-scoring touches its incumbent; (b) results with any |gf| > 180 are realized as locked RAW rows through the Plan/apply path (productize the CellSetDump.deliverLegal discipline; FacingReconstruction cannot reconstruct deltas >= 180 and unlocked rows wrapDelta); (c) audit every runJob-tail call site (violationOf wraps internally at ~1130; SmoothingPolish, IlsPolish, and the B&B rescue must not re-process a wrap-class incumbent); (d) fallback if the contract work stalls: ship the stage restricted to +-180 and escalate, because the headline gate likely needs the wrap window (norm>1 cells open only at re-expressions).
- Wiring: THOROUGH/CUSTOM only, deadline-bounded, fires on near-feasible incumbents (viol <= 1e-2) or in legal mode (S5).
- SnapRepairPolish.wrapAll trap: never route the stage's points through SnapRepairPolish's polish path.
- Acceptance: (a) unit: candidate sets contain only distinct joint cells, all |gf| <= 360, no output wrapping, deterministic across two runs; (b) bounded regression: copy tools/miqcp/rung5375-snap-point.json into test resources, span-16 no-kick descent with a FIXED EVAL/ROUND CAP (not wall-clock) reaches translated viol <= 3.5e-5; (c) env-gated full replication with kicks reaches <= 1.5e-5-class on rung (fresh tag, protocol); (d) end-to-end: a wrap-class result survives engine -> rows -> fresh reparse byte-exactly; (e) FAST tier byte-identical, pinned captures unchanged, full :core:test green. GATE.

## 7. S5 (P3): legal/record objective mode (~1-2 days)

- AngleSolverState legalMode flag + SaveIO round-trip. Engine splits the goal wall out; everything else stays hard; objective unchanged; result reports the worst removed-wall shortfall.
- Goal-wall selection rule (tightened per review): scalar (t2 == null), position-mode, non-EQ-derived constraints at the objective tick on the objective axis with cmp matching the objective sense; exactly one tightest wall; count-asserted; on ambiguity the mode REFUSES with a message, it never guesses. Velocity walls, EQ-derived pairs, and ranges at the objective tick are never touched.
- ILS legal scoring = the legalScore pattern (hard-infeasible -> 1e6 + viol, else shortfall via bestTranslationObj). The rung-specific z-lo +0.0625 domain tighten is bespoke: NOT ported.
- UI: AngleSolverWindow toggle, visible only when a qualifying goal wall exists; result shows the shortfall; solver-name suffix.
- Acceptance: (a) split removes exactly X@49lo on the proof spec; (b) velocity/EQ/range cases at the objective tick are untouched (regression against the over-match); (c) toggle off means byte-identical engine output; (d) a legal-mode solve on a cheap fixture is deterministic with hard walls <= 0 and a reported shortfall; TESTS.md updated; full :core:test green. GATE.

## 8. S6 (P4): gate-flip-aware candidate moves (~1 day + measurement, stop-if-bespoke)

- ILS candidates additionally enumerated at gate-critical ticks (any-axis carry |v| in [thr/4, 4*thr]) regardless of the norm filter, plus a targeted flip 2-opt restricted to that band. All under the 360 cap. The exact scorer handles flips natively; the point is offering cells the norm filter excludes.
- Copy tools/miqcp/rung5375-ils-point.json into test resources for the measurement.
- Measurement gate before default-on (env-gated, fresh tags, protocol): A/B on rung from the ILS point: the fixed-gate 1-opt optimum (1.2247e-5) must move toward the MILP's 1.152e-5 neighborhood without regressing proof/weirdpane/uncorrected. No gain within budget: record the numbers in the design doc, ship off-by-default, close the item. Do not iterate past one honest A/B.
- Acceptance: unit test for flip-cell presence at gate-critical ticks and determinism; the A/B report; full :core:test green. GATE.

## 9. S7 (P5): regressions + THE HEADLINE GATE (~0.5-1 day + gate runs)

- HEADLINE: cold 5.4375 from t1. Author a fixture from captures/razor-proof-improved.json: internal startTick 0 (display tick 1; the free-start trigger REQUIRES internal 0, and the capture already carries first-tick X/Z IN ranges as the footprint), authored input rows, NO yaw seeds. Sidecar problems/solve/razor-proof-t1.expect.json, provisional: shouldSolve true, effort THOROUGH, optimizeSeconds 180, maxSolveMs 300000. PASS = byte-exact viol <= 0 through the normal engine pipeline, verified by fresh reparse.
- Staging discipline: build the fixture and an env-gated cold runner FIRST; run cold after S3+S4 land and again after S6 (fresh run tags, applied-line grep, mtime checks, direct background command with watchdog from minute one, orphan sweep after kills). Pin into problems/solve/ ONLY after two consecutive fresh-process passes; set the final sidecar numbers from the measured passing runs. If pinning would add more than ~5 minutes to the suite, ask the user pinned-vs-env-gated at this delivery gate.
- If it misses: diagnose the fork and escalate with the attribution. Three named forks: basin discovery (multistart never reaches the proof basin; the research arm's licensed global stage is the remedy, and AlmSnapStage engine wiring becomes the deferred candidate), closing precision (the ILS cannot bridge the last 1e-4; fixable in Java), cap-bound (the needed norm>1 cells are only expressible beyond the 360 window; report which cells). The bar is never silently redefined.
- Additional regressions: byte-exact replay pins for captures/razor-rung-legal-attempt{,-wrap720,-turn360}.json asserting the recorded shortfalls to 1e-9 (cheap model-drift tripwire; scoring only, no wrapping of the raw rows); the S5/S6 unit fixtures; TESTS.md map updated; tableStyleCheck clean; all solve fixtures cold. GATE.

## 10. S8 (P6): docs + commit proposals

- angle-solver.md design-record section: stage design, campaign provenance, the 360-cap ruling, A/B numbers, traps honored, headline-gate result.
- Propose the new-work Conventional Commit series to the user (user executes): feat(solver): translation-aware free-start scoring; feat(solver): wrap-window lattice ILS stage (|gf| <= 360); feat(solver): legal objective mode; feat(solver): gate-flip ILS moves (only if shipped on); test(solver): campaign regressions + cold-5.4375 gate; docs(research): design record. Each proposed at a gate-green boundary.
- Report end state to the user: headline-gate verdict with evidence paths, suite time, anything deferred.

## 11. Deferred, do not do in this plan

- AlmSnapStage engine wiring (revisit only if S7 misses with basin-discovery attribution).
- deriveFreeStartBox trigger widening (separate user ruling).
- Old item 3 entirely: legality knobs UI, per-tick turn cap, chainExpress winding re-expression (dropped 2026-07-10; the 360 hard cap replaces it).
- Wrap windows beyond 360 (user ruling: 360 is default and max).
- tools/miqcp Python work of any kind (research arm, stays put).

## 12. Next-session prompt (user pastes verbatim)

Execute the solver port per docs/research/solver-port-plan-2026-07-10.md. Follow it step by step in order, starting at S1 preconditions; run the tests at every GATE and validate nothing is broken before moving on. The S2 merge of v1.7.0-position-free-solver into nix-backward-march is pre-authorized, including conflict resolution and the merge commit; make no other git mutations. Escalate to me only at the escalation points in section 0 rule 7.
