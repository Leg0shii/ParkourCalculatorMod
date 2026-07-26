# Handoff: port the rung-5375 campaign findings into the v1.7.0 position-free solver

Written 2026-07-10 ~13:00 at the end of the Lever-2 / wrap-tier / turn-tier session. This doc is the single entry point for the NEXT session, whose job is to produce a VALIDATED implementation plan and get user approval BEFORE porting anything. The user's chosen prompt for that session is in section 8.

## 0. Read order and binding rules

Read in this order: this doc; docs/research/next-session-lever2-2026-07-10.md (EXECUTION RECORD covers everything certified and delivered on 2026-07-10); docs/research/free-start-handoff.md section 0 (MANDATORY before touching any start-position code; records the box-corner-vs-center bug and the dual stall post-mortem); docs/research/razor-campaign-2026-07-09-handoff.md (standing traps and realization rules).

Binding rules, all inherited and non-negotiable:
1. ANTI-STALE PROTOCOL verbatim from next-session-lever2-2026-07-10.md section 1 for every env-gated test and solver run: fresh run tag per invocation in the artifact filename, applied:-line grep plus mtime check before reading any number, gradle --rerun --no-daemon on env-gated runs, long runs as direct background commands with a watchdog from minute one, orphan sweep after any kill.
2. The gate is a green full ./gradlew :core:test (--rerun --no-daemon when env vars are involved). Never edit ExactJumpModel, McSineTable, Constants without a green ModernStepRegressionTest baseline first.
3. Core stays Minecraft-free, Java 8, no code comments, no em dashes in docs, user does all git operations, Conventional Commits when the user commits, release-please owns mod_version.
4. Solver tests are cold-start seedless (warm-starting from the answer is cheating). Regression fixtures go through the problems/ folder-driven ProblemsTest shape with a TESTS.md entry.
5. Locked RAW rows are the only valid realization for wrap-class points; display tick = internal + 1 in user communication; the rung's raised walls exist only via RazorFixtures.applyRung5375Patch (count-asserted 3).

## 1. Campaign state (what is delivered, certified, pending)

- Rung 5.375 SECONDARY (legal metric) attempts delivered, all byte-exact verified and fresh-reparse PASSED:
  - ATTEMPT_5.375bm_legal.json: shortfall 9.683582974e-5, maxGf 1437.8. USER-CONFIRMED real in-tool, accepted with a wrap-depth caveat.
  - ATTEMPT_5.375bm_legal_wrap720.json: shortfall 9.704755232e-5, maxGf 717.649 (clean-rules tier, |gf| <= 720). In-tool confirm pending. Deep wraps bought only 2.1e-7: the record is clean-angle-robust.
  - ATTEMPT_5.375bm_legal_turn360.json: shortfall 2.121321382e-4, e0=1269, every per-tick turn <= 359.9 deg, winding drifts to 2879 max (turn-continuous tier). In-tool confirm pending. First sub-community attempt under the turn rule; the 9.7e-5-class points are PROVEN not turn-expressible (wrap-locked cells force >360 turns; chainExpress DP).
- All three beat the community 2.74e-4. Certified legal ceiling bracket in the searched basin: [9.34e-5, 9.68e-5] (uncapped, +-2 deg window).
- PRIMARY (viol <= 0) certified dead in-window with FREE gate patterns at spans +-64 buckets and +-2 deg (max min-slack <= -1.131e-5, SCIP dual bounds, timelimit); in-tube (+-30 deg) dead for all swept structures by the capped-annulus relaxation (t* ~ -3.6e-6) whose caps cover wrap bases to +-2880. New closest approach: viol 1.152396763e-5 (persisted tools/miqcp/rung5375-cellset-point-cs0710e2.json, chain-infeasible under the turn rule).
- Norm-boost geography (probe-certified): boosts exist only at facings (90,180) and (270,360) mod 360, peaking at 135/315, envelope 9.594196e-5 x |sin 2theta| at every wrap base to +-2880; (0,90) is slowdown-only, (180,270) clean; deep bases enrich cell density, not magnitude (base+1080 rich, base+1440 nearly dead).
- Full-circle / other-basin question: OPEN. The arc-disjunction MILP (tools/miqcp/fullcircle_milp.py) is sound but SCIP-non-convergent at K=72 and K=12 (root LP degenerates to a controllable-thrust disc, bound pinned at 0.125). Parked pending licensed spatial B&B or a disaggregated reformulation. Do not rerun on SCIP as-is.
- Licenses: user requested the COPT free personal license (username benja, ~2 business days) and the Gurobi 30-day commercial evaluation. Both backends are BUILT and smoke-tested to their license walls in tools/miqcp/cellset_milp.py (--solver copt|gurobi; gurobipy 13.0.2 installed). When a license lands: run the two-solver certificate batch (PRIMARY negatives both spans, legal ceilings all three tiers) and attempt the full-circle question.
- Tier-B round 2 LANDED (both verified byte-exact, worstDelta 0.0): legal improved to shortfall 2.017473311e-4, DELIVERED as ATTEMPT_5.375bm_legal_turn360_v2.json (tb0710k, reparse PASS, turn-chain PASS, winding range to 2720; v1 stays on disk); minimax improved the best TURN-CONTINUOUS approach to viol 3.135141768e-5 (verified tb0710j, persisted tools/miqcp/rung5375-cellset-point-turn360-tb0710h.json). Both bounds vacuous (cold-blind on chain models): Tier-B ceilings are OPEN in both directions, a licensed-solver target.

## 2. The port mission

User directive: release the campaign findings AS SOLVER IMPROVEMENTS IN THE CODE, landing in the v1.7.0 position-free branch solver (current branch nix-backward-march, commit dfa9ac7 "feat: position free solve"). SCIP/Python cannot ship in the mod: the port is pure-Java, into the core/ solver pipeline. The tools/miqcp Python infrastructure stays research-only (it is the certification arm).

User-approved plan skeleton (next session validates, details, and adversarially reviews it, then asks for approval):

1. Wrap-window lattice polish stage in the engine (~2-3 days). Port the norm-ILS machinery from the MiqcpClose.normIls test harness (candSetFor, candFull, kickCells, translatedViol, 1-opt/2-opt/2optB rounds, kick cycles) into a solver stage in core/.../anglesolver/solver/, wired into AngleSolverEngine after the existing closers, gated on the Effort knob (Optimize/Custom). This machinery took 6.5e-5 to 1.15e-5 on razors; the product has none of it.
1b. Translation-aware scoring across all stages (~1-2 days, arguably FIRST since item 1 wants it). Today AngleSolverEngine.runJob PINS the free start box for the whole main pipeline (engine ~line 690: sc.startBox = StartBox.pinned(...)) and only FreeStartSolve.solveJoint plus the freeStartImprove post-pass use it. Make the score function translation-aware everywhere (SnapRepairPolish.bestTranslation / bestTranslationObj per evaluation), gated on startFree(); CMA-ES either scores translation-aware or gains (tx,tz) dimensions; consider widening the deriveFreeStartBox trigger (engine ~line 337) to any known standable footprint. HARD CONTRACT: byte-identical behavior when the start is pinned; keep-better semantics; free-start-handoff.md section 0 read first.
2. Legal/record objective mode (~1-2 days). The maximize-goal-wall-subject-to-others mode (MiqcpClose.legalIls pattern: goal wall objectified, bestTranslationObj with domain tighten) as a first-class solver option plus a small AngleSolverWindow toggle. This is what produced all three record attempts.
3. Legality knobs (~1 day). Max |gameFacing| cap and max per-tick turn cap as solver constraints (the wrap720/turn360 tiers), plus the chainExpress winding re-expression (CellSetDump.chainExpress: per-cell 360-winding equivalence, id-checked, physics bit-exact) as a default post-pass so results come out with human-like turning when possible.
4. Gate-flip-aware candidate moves (~1 day, the one research-risk item). The campaign's biggest discovery: momentum-gate timing flips break vise-locked optima (the fixed-gate move table was legal-INFEASIBLE where free gates were FEASIBLE). No Java equivalent exists. Cheapest port: include gate-flipping cells in ILS candidate sets and add a targeted flip move around |v|~threshold ticks. Will not match the MILP's simultaneous view; measure what it buys on the benchmarks and stop if bespoke.
5. Regression tests (half day, mandatory). problems/ folder-driven fixtures from the campaign points (cold-start), TESTS.md map updated, goldens for the new stages, tableStyleCheck-clean.
6. Docs and release. Design-record section (docs/research/angle-solver.md), Conventional Commit series on the branch, release-please flow. User does the git operations.

## 3. The acceptance test (headline gate)

COLD SOLVE OF 5.4375 FROM T1: razor-proof-improved geometry, start tick 1, authored input rows, NO yaw seeds, free start position within the footprint. PASS = byte-exact viol <= 0 through the normal engine pipeline (no env-gated harnesses), verified by fresh reparse. This is RazorBench benchmark 1, pending since the Sheepram work and never run truly cold.

Why it is the right gate and why it is genuinely uncertain: 5.4375 was solved only as a seeded refinement of the prover's solution; the architecture verdict (three reproductions) says cold smooth-then-snap has a ~2e-4 LUT-residual floor, and 5.4375's certified headroom above the solved point is +3.0e-4, so cold success is marginal-but-plausible with the homotopy closer plus the newly ported wrap-ILS. If it misses, diagnose the fork: basin discovery (needs the licensed global stage, research arm) vs closing precision (fixable in Java). The nix-full precedent (cold from t1, free start, 15/15 via HomotopyCloser + MomentumAssembly) is the existence proof for the t1/free-start plumbing.

## 4. Code map (port sources and targets)

- Port FROM (test sources, env-gated): core/src/test/.../anglesolver/MiqcpClose.java (normIls, legalIls, candSetFor, candFull, kickCells, translatedViol, legalScore), CellSetDump.java (chainExpress, deliverLegal delivery discipline, verify arbiter pattern), RazorBench/RazorFixtures (benchmark harness + rung patch).
- Port INTO / already product (core/src/main/.../anglesolver/solver/): SnapRepairPolish (bestTranslation, bestTranslationObj, Trans; NOTE it wraps yaws to +-180 in its polish path, structurally incompatible with wrap-window points; the ILS-style descent is the polisher there), FacingLattice (cellRepresentatives, jointCellId; already product), HomotopyCloser, MomentumAssembly, FreeStartSolve (solveJoint, recoverStart, violationAt), AlmBfgsCore, ExactJumpModel (NEVER edit), JumpLinearModel, AngleSolverEngine (runJob pinning ~line 690, deriveFreeStartBox ~line 337, freeStartImprove ~line 978).
- AlmSnapStage engine wiring (runJob + dualChain hook, hard sub-budget) is designed and dispositioned but not built (alm-snap-stage-b-design.md); decide in the plan whether it rides along or stays deferred.
- Research infra that stays put: tools/miqcp/*.py (build_model, move_milp, cellset_milp with scip/gurobi/copt backends, fullcircle_milp), the dumps/results/logs, NormCellProbe, StructureVariantDump, MiqcpDump.
- Fixtures and deliveries: core/src/test/resources/captures/razor-* (incl. razor-rung-legal-attempt{,-wrap720,-turn360}.json), game-folder ATTEMPT/SOLVED files, tools/miqcp/rung5375-*.json points.
- Branch state: nix-backward-march carries many uncommitted files (new solver classes, harnesses, docs, tools). The plan should include a commit-set proposal (what ships, what stays local) for the user to execute.

## 5. Known traps for the port (bite history)

1. Free-start: box-corner-vs-center reference bug and Moreau smoothing gate (free-start-handoff.md section 0). Contract: byte-identical when pinned, keep-better always.
2. SnapRepairPolish wrapAll: destroys wrap-window points; never in a verify path for wrap-class solutions.
3. Capture tests MUST use Fixtures.buildBoxes (DERIVE/KEEP specs degrade to always-sprint otherwise).
4. Gradle env-var staleness (--rerun --no-daemon) and subagent run-supervision ban (10+ multi-hour losses).
5. Sprint factor lag (factorSprintAt), per-tick sampled inputs from TickStates t+1, never row-derived.
6. SCIP incumbents under chain/e-var models carry feastol-level slop (drift ~6e-9): the exact recompute is authoritative; the 1e-9 fidelity halt applies to model-vs-exact, not solver slop.
7. Turn rule semantics: consecutive sub-360 turns still allow unbounded winding drift; the user may want an absolute cap stacked on top (delivered turn360 reaches 2879; probed envelope ends at +-2880).

## 6. Open user-side items

- In-tool confirms: ATTEMPT_5.375bm_legal_wrap720.json and ATTEMPT_5.375bm_legal_turn360.json.
- License emails (COPT, Gurobi), then the certificate batch and the full-circle attempt.
- Approval of the port plan the next session produces.

## 7. Style reference: the prompt that ran this session

"Continue the rung 5375 campaign. Read docs/research/next-session-lever2-2026-07-10.md first and follow it, including the ANTI-STALE PROTOCOL in section 1 verbatim for every test and solver run. Build and run the Lever 2 gate-flip exact-cell-set MILP (cell dump at span +-64 around the ILS point including gate-flipping cells, then cellset_milp.py with free 3-state gates, legal objective R1 first, R2/R3 after), verify every incumbent byte-exact, and escalate to me only at the delivery gates or a certified double-negative."

## 8. The next-session prompt (paste verbatim)

Port the rung-5375 campaign findings into the v1.7.0 position-free solver. Read docs/research/solver-port-handoff-2026-07-10.md first and follow it, including the ANTI-STALE PROTOCOL it binds in section 0 verbatim for every test and solver run. Phase 1: independently validate the handoff's claims against the code and artifacts (the runJob start-pinning, the harness sources named in section 4, RazorBench benchmark 1 state, the free-start-handoff section 0 contract) and report discrepancies. Phase 2: produce the implementation plan as a numbered checklist over items 1, 1b, 2-6 with per-item acceptance tests, the commit-set proposal, and the cold-5.4375-from-t1 gate from section 3 as the headline regression; adversarially review the plan once and record dispositions. Do NOT port or refactor anything before I approve the plan. Escalate to me only at plan approval, delivery gates, or a certified blocker.
