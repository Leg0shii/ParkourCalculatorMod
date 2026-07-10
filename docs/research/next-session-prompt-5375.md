# Next-session prompt: crack 5.375bm

Mission: land the first legal solve of rung 5.375 (nix razor class). Read docs/research/razor-campaign-2026-07-09-handoff.md COMPLETELY before any other action; it is the canonical record of the 2026-07-08/09 campaign and every claim below is evidenced there or in the artifacts it lists. Do not re-derive or re-litigate anything it settles.

## Where the problem stands (all measured)

- The jump is certified NOT impossible in the norm-aware smooth model: annulus ceiling +5.1e-5 above the pad, landing-region depth t* = 6.276e-6 (proven optimal), landable ONLY by riding norm>1 significant-angle LUT cells, reached in practice through WRAP-WINDOW re-expressions (gf +-360/+-720 of the same physical heading rides different LUT cells).
- Best byte-exact approach: viol 1.2246666e-5 (tools/miqcp/rung5375-ils-point.json, delivered as ATTEMPT_5.375bm_closest.json, in-tool replay confirmed model-consistent). It carries a MOVE-MILP certificate: OPTIMAL over its entire simultaneous non-gate-flipping cell-move neighborhood; gate-flip pairs/triples exhausted. That point is a dead end by local moves; do not burn compute re-searching its neighborhood.
- RECORDS ARE LEGAL-METRIC ONLY (user ruling, final): a record run satisfies every wall except the landing constraint. The community's 2.74e-4 legal attempt is the standing record. Our legal best is 2.005e-3 (warm-chained). The 1.22e-5 point is NOT a record (violates five walls).
- The annulus certificate is quadrant-INACCURATE: it over-promises Q1 ticks (provably capped at norm 1 there) and under-models Q4 wrap cells. The true realizable ceiling could sit above OR below the pad. This is the single most important open question.

## Definition of done (in order of value)

1. PRIMARY: viol <= 0 on the patched rung spec (RazorFixtures.applyRung5375Patch, count-asserted), delivered as a locked-RAW-row TAS (SOLVED_5.375bm_first.json, add-only), fresh-reparse verified byte-exact WITHOUT wrapAll anywhere, and confirmed legal by the USER in the live tool. No claim before the in-tool confirmation.
2. SECONDARY: a LEGAL run with landing shortfall < 2.74e-4 (beats the community record honestly).
3. TERTIARY (the honest negative): a per-quadrant-corrected certified ceiling BELOW the pad, agreed by COPT and SCIP independently at gap 1e-8, assembled as an impossibility-evidence dossier.

## The levers, in priority order (from the final campaign report)

(a) PER-QUADRANT REALIZABLE NORM BOUNDS: rebuild the annulus in tools/miqcp/build_model.py with per-tick norm bounds derived from the cells actually realizable at that tick's heading quadrant and wrap window (exact LUT scan machinery exists; FacingLattice + the move-table code enumerate realizable cells). Re-certify (gap 1e-8, tube bounds, both solvers). This either relocates the incumbent onto genuinely realizable cells (better seeds) or drops the ceiling below the pad (route to done-3).
(b) MOVE-MILP AT OTHER LOCAL OPTIMA: the kicked run's other kick-cycle optima (1.23-1.28e-5) each get an exact move table + MILP certificate in ~30 s (tools/miqcp/move_milp.py pattern). Any point whose certificate is NOT empty-selection-optimal is a live seed.
(c) GATE-PATTERN ALTERNATIVES for the t13-t25 diagonal: the entire campaign inherited one gate pattern from the annulus incumbent; enumerate nearby patterns (the gate 3-state disjunctions are already first-class in build_model.py) and re-certify/re-close per pattern.
(d) BASIN DIVERSITY: perturbed annulus objectives (small random linear tilts) produce different certified incumbents; wrap-ILS (MiqcpClose normIls) each. Also deeper wrap ranges (+-1080).
(e) STRUCTURE SWEEP (user-approved reductions): key releases collapse to a per-tick magnitude choice {1.0, 0.98, 0}; add as binaries to the MIQCP and test dominance in one run. Extra runway ticks: certify L, L+1, L+2 and use the saturation curve. Jump-timing variants: separate certified instances (enumerate, cheap).
(f) LEGAL-RECORD PUSH independent of the full solve: legal mode (goal wall removed, ALL else hard) seeded from the best rung shapes; the X@42 ceiling is the binder; a legal run under 2.74e-4 is a standing deliverable even if viol <= 0 stays out of reach.

## Orchestration and epistemic rules (unchanged from the prior mission, plus the paid-for lessons)

- You are architect/critic/validator; delegate code and runs to Opus subagents, extraction to Sonnet. Every delegation carries the exact deliverable, acceptance test, and file paths. Reject reports lacking acceptance output.
- NEVER GUESS at a failure cause; instrument with grep-able [DBG-*] tags; an investigation ends only when the blocking line/quantity/mechanism is named with the log line proving it.
- No claim without validation; anything called a record needs the LEGAL metric AND user in-tool confirmation. Every env-var-driven variant prints an applied: line; runners grep for it.
- ARM A WATCHDOG MONITOR AT SESSION START (no-solver-process + no-new-artifact for 6 min = stall event; artifact events on results/reports/SOLVED files). Subagents supervising long builds died silently 8+ times last session; run long jobs as direct background commands and never rely on an agent's own patience. Do not give the user ETAs without checking artifacts on disk first.
- Gradle: ALWAYS --rerun on env-gated reruns (UP-TO-DATE trap) and --no-daemon (env-var propagation trap); verify report file mtimes before trusting any run.
- Realization: locked RAW rows only (SaveIO passes yaws raw; the tool replays them faithfully to 718 deg, user-confirmed); NEVER unlocked/delta rows (drift ~1e-6 in-tool, and provably cannot express wrap-window points); never wrapAll in verify paths; SnapRepairPolish wraps internally so use the normIls descent as the polisher for wrap points.
- Display tick = internal + 1 in ALL user communication. The rung's raised walls are the harness's in-memory patch, not in the save files.
- COPT (coptpy 8.0.5, free cap 2000 vars: fits route models, NOT big move-MILPs, use SCIP/pyscipopt there) and SCIP are installed and smoke-tested. Certification needs gap 1e-8; relative 1e-6 is too coarse for 1e-4 decisions.
- Suite gate: ./gradlew :core:test stays green; never edit ExactJumpModel/McSineTable/Constants; JumpLinearModel additively only.

## Key artifacts to load before deciding anything

docs/research/razor-campaign-2026-07-09-handoff.md (canonical), alm-snap-stage-a-design.md + alm-snap-stage-b-design.md (dispositions), sheepram-port-spec.md; tools/miqcp/ (build_model.py, move_milp.py, rung5375-ils-point.json, rung5375-move-table.json, results-*.json); core/build/reports/miqcp-*.txt and pattern-pinned-*.txt; harnesses RazorBench / PatternPinnedProbe / MiqcpDump / MiqcpClose / RazorFixtures; delivered files in the game folder (SOLVED_5.4375bm_proof_improved.json, ATTEMPT_weirdpane_legal_v2.json, ATTEMPT_5.375bm_closest.json).
