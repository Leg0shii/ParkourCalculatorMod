# Razor campaign handoff: 2026-07-08 evening to 2026-07-09 evening

Read this FIRST before any razor work. Companions: alm-snap-stage-a-design.md (Stage A + all dispositions), alm-snap-stage-b-design.md (Stage B, three adversarial review rounds, B1x dispositions), sheepram-port-spec.md (normative port reference), nix-solver-handoff.md (the previous session's record). Memory file project_alm_snap_stage_a.md carries the condensed chronology.

## Scoreboard (all delivered files verified byte-exact at write time; in-tool replay is the final arbiter)

- 5.4375bm: SOLVED and IMPROVED, IN-TOOL CONFIRMED BY THE USER (2026-07-09 evening). SOLVED_5.4375bm_proof_improved.json (game folder) = viol 0, pad X 212.7001881826, +2.4e-5 over the prover's solve, best known in existence, replays working in the live tool. Seeded refinement of the prover's solution, not a cold solve. Annulus-certified headroom above it: +3.0e-4.
- 5.375bm rung: NOT SOLVED (by anyone). USER RULING (final): our 1.2246666e-5 point is NOT a record of any kind because it is not legal (it violates five walls, not only the landing constraint); records on this jump are legal-metric only, and the COMMUNITY's 2.74e-4 legal attempt STANDS as the record. Our best LEGAL rung attempt is the warm-chained legal run at shortfall 2.005e-3 (all walls hard except the pad). The 1.2247e-5 point (ATTEMPT_5.375bm_closest.json) remains a research artifact: the closest known infeasible approach, with a rigorous MOVE-MILP certificate (optimal over its entire simultaneous non-gate-flipping cell-move neighborhood; gate-flip pairs/triples move only the 14th digit) and in-tool replay confirmed consistent with the model.
- Addendum 2026-08-21: the rung 5.375 legal record fell the next day (2026-07-10): verified legal shortfall 9.683e-5 vs the community 2.74e-4, 2.83x better, later user-confirmed in-tool; full record in next-session-lever2-2026-07-10.md.
- weirdpane: LEGAL RECORD, IN-TOOL CONFIRMED BY THE USER (2026-07-09 evening): ATTEMPT_weirdpane_legal_v2.json replays legal in the live tool, all hard walls including t38 held, only the t50 landing constraint short by 2.040e-3, beating the user's hand best 2.2718e-3. The v1 file (unlocked reconstructed rows) drifted ~1e-6 in-tool and flipped the three thinnest walls; locked raw rows are the proven realization.
- Gate microbench: PASSED (gateless Sheepram-class model diverges 0.080 blocks on the proof trajectory; docs/research/gate-microbench.md).

## The five headline discoveries (each measured, citations in the design notes and reports)

1. SIGNIFICANT ANGLES ARE THE RUNG'S LANDING MECHANISM. Unit-circle certification puts the rung ceiling 1.16e-4 BELOW the pad; the annulus (exact LUT scan: norm 1 +- 9.594e-5, real via float32 index truncation at large rad) lifts it +5.1e-5 ABOVE. Landing region depth t* = 6.276e-6 (proven optimal). The pad is reachable only by riding norm>1 cells (39/49 ticks in the certified optimum).
2. THE WRAP-WINDOW LATTICE. The same physical heading expressed as gf, gf+-360, gf+-720 rides DIFFERENT LUT joint cells; norm>1 cells are provably unreachable at +-180 form for the ridden headings and open only at re-expressions. All the 6.5e-5 -> 1.22e-5 improvement came from wrap-window moves. Consequence: LOCKED RAW rows are the only valid realization for wrap-class solutions (unlocked delta rows cap per-tick travel at 180 deg and provably cannot express them; and they drift ~1e-6 in-tool even for non-wrap points, user-confirmed).
3. SMOOTH-THEN-SNAP CANNOT COLD-SOLVE RAZORS (architecture verdict, three independent reproductions): the smooth surrogate has a ~2e-4 LUT-residual floor (grades the prover's exactly-feasible corner 2.17e-4 infeasible; ALM seeded AT the corner walks 10.7 deg away), and the discrete closer bridges zero infeasibility (census: no feasible point within +-3 buckets of any 2e-4-infeasible incumbent). The prover's basin sits THOUSANDS of buckets from every basin our searches reach.
4. THE MIQCP CERTIFICATION PIPELINE WORKS AND IS CHEAP. tools/miqcp/: MiqcpDump (Java, per-tick constants + constraints JSON) -> build_model.py (COPT free-tier or SCIP; circle or annulus; gates as 3-state indicator disjunctions; tube bounds essential, full-circle is loose; relative gap 1e-6 is TOO COARSE for 1e-4 decisions, use 1e-8) -> MiqcpClose (byte-exact scoring + close). Proof certifies in 21-50 s. The MOVE-MILP pattern (exact per-move effect table + integer program over additive moves) gives optimality certificates over move neighborhoods in seconds.
5. LEGAL METRIC DISCIPLINE (user correction): maxViolation is NOT comparable to community numbers, which are legal runs short only of the landing. Legal mode = remove the goal wall(s) (weirdpane: t50 ONLY, t38 stays hard per user ruling), everything else viol <= 0, report the shortfall. All beat-community claims must use this metric.

## Named next levers for the rung kill (in priority order, from the final agent report)

(a) Per-quadrant realizable norm bounds in the annulus re-certification (current annulus over-promises Q1, under-models Q4 wrap cells), then re-close from the new incumbent. (b) MOVE-MILP tables at the other kick-cycle local optima (1.23-1.28e-5; ~30 s each end to end). (c) Alternative gate patterns for the t13-t25 diagonal (inherited unchanged from the annulus incumbent throughout). (d) In-game confirmation of >180-deg locked-yaw replay (half-confirmed via the user's in-tool rung check). Also open: basin diversity via perturbed annulus objectives; deeper wrap ranges (+-1080).

## Open non-rung work

- Engine wiring (AlmSnapStage into runJob + dualChain, hard sub-budget, nix-full-t1 wall-clock check) - designed, dispositioned, not built. [2026-08-21: subsequently built, then removed entirely with issue 380 / PR 382; see alm-snap-stage-b-design.md.]
- Sheepram head-to-head on our cases in their DSL - never run.
- Uncorrected/V4.5 class: pattern-bound (frozen-pattern diagnosis); needs gate enumeration (B2, designed + dispositioned) or template seeds.
- The 5.4375 improved solution and weirdpane v2 in-game user verification: DONE (user-confirmed in-game 2026-07-09 late).

## Installed solver tooling (new this session, ready to use)

- COPT 8.0.5 via `pip install coptpy` (Python 3.12 at `python`): INSTALLED and smoke-tested (nonconvex circle equality + indicator constraint certified to global optimality in 0.03 s; scratchpad solver_smoke.py pattern lives on in tools/miqcp usage). Runs UNLICENSED with a 2000-var/2000-con cap, which fits the route MIQCPs (530-690 vars) with room; the free personal license (registration at the COPT site) is an optional headroom upgrade, never needed this session.
- SCIP via `pip install pyscipopt` (6.2.1): INSTALLED and smoke-tested; the independent second certifier and the engine for large move-MILPs (2428 binaries) that exceed COPT's unlicensed cap. Apache-licensed, no registration, no size limit.
- Gurobi 13: NOT installed, held deliberately in reserve. The free pip tier caps at 200 vars with quadratics (does not fit); the 30-day full evaluation is a one-shot resource to be requested only when a model is debugged and a gold-standard certificate is wanted (it is the strongest engine on this problem class per the Oct 2025 Mittelmann benchmarks; research summary in the session transcripts, verdict: COPT primary, SCIP fallback, Gurobi eval for the definitive bound).
- Selection guidance measured this session: route certifications need gap 1e-8 (relative 1e-6 is too coarse for 1e-4 pad decisions); tube facing bounds are essential (full-circle bounds are loose: proof full-circle timed out at 212.745); both engines agreed to ~2e-6 on every certified bound, which is the cross-check standard any new certificate should meet.

## Operational traps recorded this session (respect them)

- Gradle marks :core:test UP-TO-DATE on env-var-only reruns: ALWAYS --rerun (Gradle 9.1) and verify report mtimes; the daemon does not propagate fresh env vars to forked test JVMs: use --no-daemon on env-gated runs.
- Subagents supervising long builds die silently (8+ incidents): run long jobs as direct background commands, and keep a watchdog Monitor (no-process + no-artifact for 6 min = stall event).
- SnapRepairPolish wraps yaws to +-180 internally: structurally incompatible with wrap-window points; use the norm-ILS descent as the polisher there. [2026-08-21: SnapRepairPolish is deleted; WrapWindowIls is the live wrap machinery.]
- COPT free tier caps at 2000 vars/2000 cons (fits the route MIQCP at 530-690 vars; does NOT fit 2428-binary move MILPs: use SCIP).
- Row realization: locked RAW rows (SaveIO passes yaws through raw); never unlocked/delta rows for solver results; never wrapAll in a verify path for wrap-class points.
- Display tick = internal + 1 in all user-facing communication; the delivered rung/weirdpane ATTEMPT files carry the proof/weirdpane angleSolver blocks and the rung's raised z-lo walls exist only as the harness's in-memory patch (RazorFixtures.applyRung5375Patch, count-asserted).

## Key artifacts

Best rung point: tools/miqcp/rung5375-ils-point.json. Move table/solutions: rung5375-move-{table,solutions}.json. Certifications: tools/miqcp/results-*.json (+ logs). Reports: core/build/reports/miqcp-*.txt, razor-*.txt, pattern-pinned-*.txt, proof-neighborhood.txt, gate-microbench.md. Fixtures: captures/razor-{proof,proof-improved,weirdpane,weirdpane-attempt,weirdpane-attempt-v2,rung-attempt,uncorrected}.json. Harnesses: RazorBench (PKC_RB_*), PatternPinnedProbe (PKC_PP_* modes incl. warmchain/segtarget/normils), MiqcpDump/MiqcpClose (PKC_MIQCP_*), ProofNeighborhoodProbe (PKC_NP), RazorFixtures.

2026-08-21: after the cleanup, MiqcpDump, CellSetDump, NormCellProbe, StructureVariantDump (the last three added 2026-07-09/10), RazorFixtures, and the razor-* captures survive in the test tree; MiqcpClose, RazorBench, PatternPinnedProbe, and ProofNeighborhoodProbe are deleted. The tools/ directory is git-ignored and machine-local, so every tools/miqcp artifact named here is absent from a fresh clone; the certified numbers quoted in these docs are the durable copy.
