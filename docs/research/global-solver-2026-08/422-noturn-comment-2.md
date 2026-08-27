## No-turn campaign, session 2 (2026-08-26): j154 solved in-game, the translation gap mapped, easiness ruled

Continuation of the PoC comment above. Everything below is byte-exact through `ExactJumpModel` unless noted, and the two headline strats were **verified in the real 1.8.9 game** (bit-identical debug on every tick). Canonical long-form record: `docs/research/global-solver-2026-08/NOTURN-HANDOFF.md` section 4c; pipeline code `research/copt/noturn_miqcp.py` (+`noturn_slp.py`, `noturn_mkstart.py`), judges `StructureDump` / `NoTurnReplay`.

### Results (j154 `1bm_Head_Butterfly_Neo`, no-turn + ja)

- **Solved and in-game verified**: X@39 = -1599.7001161 vs human -1599.7000892, human 6-edge key schedule, machine angles, all wall faces cleared >= 2e-6. Delivered save `j154_1bm_Head_Butterfly_Neo_noturn_ja_solved.json`.
- **Pure no-turn (dF=0 through the jump tick): PROVEN infeasible** (B&B exhausted, 14917 nodes, relaxation certificate).
- **Removing a jump (2-jump variant): PROVEN infeasible** in 2 s. Adding a 4th jump: no solve found cold (no certificate). Extra run-up ticks: measured dead (solver leaves them empty).
- **j1150 easy variant delivered**: `j1150-2x2bm_Nix_Neo_noturn_easy.json` = the human 3-edge structure (NONE x3, SA x13, WD x22, W ja, flown WA landing) at his settable angle 20.529644 with machine turn yaws, X@49 = -2805.2981315 (his run +1.5e-4; the unplayable 16-edge TAS solve is only 9.1e-4 deeper).

### The translation gap (the current weakest link), measured

1. **Sine buckets dominate at knife edge.** Byte-exact outcomes are piecewise constant in facing (0.00549 deg buckets); the continuous model moves smoothly. Free-theta SLP oscillates by e-5 per round and never converges when wall slack is < 1e-4. FIX that converged in 3 rounds every time: pin theta (and ja theta2) to the anchor bucket, box the 10 turn-tick inputs to a few 1e-6, translate walls with measured SLP margins (byte minus linear at a replayed anchor), re-anchor each round. A final 1-ulp/e-7 residual is closed by nudging the free start by e-12..e-7.
2. **Margins are translations, not safety pads.** Guessed uniform margins cut off the true manifold (the operating point rides walls at e-6, the human's tightest proven-safe clearance is 4e-7). Always compute margins at a byte-exact anchor.
3. **Wall-face bounds are collision clamps.** An authored wall like `Z GE 4930.05` sits exactly at face+0.3; the optimizer rides every bound to e-13, which in game means touching the wall, `wallCollision`, sprint killed, run destroyed (observed in-game). Rule now in force: solve with explicit ~2e-6 clearance on every wall-face constraint; only pad/floor edges may be ridden to zero. Two spec-authoring bugs were also found this way (a misplaced and a missing X wall); wall constraints should eventually be derived from captured block geometry, not authored by hand.
4. **Gate patterns still fold cleanly** (velocityWalls + re-extraction fixed point), including on a jump with only two real gate events; even near-gateless jumps are NOT safe clamp-free when slack is e-5.

### Cold discovery: what works and what is still open

- **Wall homotopy WORKS and is the missing cold front end for knife-edge jumps**: relax the landing walls (0.05), cold-solve (incumbents appear; a wrong facing family appears too and dies on tightening), tighten in stages (0.01 snaps to the human family), fold the candidate's OWN gate pattern, finish with the pinned-bucket walk. On j154 this rediscovered the human's schedule family with zero human data in the chain and produced the deepest solve.
- **Cold discovery of the EASY structure is still open** (j1150, 3 failed attempts): capped edge search at exact walls finds no incumbent (33k nodes); fat-wall optima (a 2-edge and a 4-edge structure) are artifacts that die at 1e-3..5e-3 relaxation; the human's 3-edge structure (feasible at every relaxation) is never returned because MIN X at fat walls prefers artifacts. Identified fix: a **structure-pool driver** (no-good cuts on the schedule, re-solve until dry at moderate relaxation, homotopy-tighten every candidate with per-round pattern re-extraction, keep survivors). All components exist; the loop is unbuilt.
- COPT behavior on knife edges, for the record: dual side is strong (fast infeasibility certificates), primal heuristics never land in a 4e-5-wide corridor, integer objectives (edge count) starve the heuristics entirely (MIP start mandatory), exact-circle MIQCP still finds zero incumbents cold.

### Rulings and easiness (user decisions, binding)

- **Easy INPUTS beat wall slack as the simplicity metric.** The j1150 human strat was performed at sub-1e-6 slacks; it is human-possible because every input is quantized-reproducible (3 key edges, one locked settable angle, tick jump taps, only the landing turn flown by feel). Max-margin ("executability budget": j154 3.8e-5, j1150 3.8e-4) stays useful as a diagnostic, not as the ranking.
- Mid-jump jump-angles are forbidden. Sneak is assessed Python-only (~9 combos with exact float kappas, sprint-latch interaction) but not built.
- 1.8.9 free-sprint legality is now modeled: sprint may engage on any forward tick (mid-air too), may not drop while forward is held (latch constraints).

### New pipeline capability (all in `research/copt/noturn_miqcp.py`)

`--ja-tick` (second facing circle for the jump-angle tick), `--free-sprint`, `--fix-theta/--fix-theta2` (bucket pinning), `--trust-turn`, `--min-edges`, `--max-edges` (cap), `--max-margin`, plus the variant-authoring scripts (`j154_mk_2jump.py`, `j154_mk_variants.py`) and the homotopy relaxation flow.

### Plan agreed with the user for the ARCH-2 build (this ticket)

1. **Simplify first**: a dedicated research pass on whether the general problem (free per-tick dF, not just no-turn) can be reduced further before building, covering this codebase, these findings, and prior art (Curryocity/Stratfinder: enumeration-first in exact game arithmetic, velocity-window targets, strat-family grammar, easiness-aware pruning).
2. **Component inventory**: identify exactly what is already built vs missing against the converged design (FBBT, costate warm start, disk+chord nodes, arc/gate branching, round-and-simulate).
3. **Cleanup step (mandatory)**: a final pass that guarantees the implementation is clean, old code is removed, and everything is wired end to end and ready to use.
