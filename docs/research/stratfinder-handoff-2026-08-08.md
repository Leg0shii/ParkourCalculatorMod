# Stratfinder handoff (2026-08-08)

Continuation document for the next session. Read this first, then `stratfinder-levers-2026-08.md` (experiment numbers) and `difficulty-metric-handoff.md` (metric background). Everything below happened on `feature/stratfinder`.

## 0. First step: rebase onto dev

The branch is behind `origin/dev`. Dev has a newer save layer (`deviation`/`deviationKind` on AngleSolver, `medium`/`soulsandCells` on overrides, `SaveIO.sanitize` name sanitization, undo snapshot helpers) and several merged PRs. This session's changes are almost entirely test-side (`core/src/test/`), plus `core/build.gradle` (the `-PtestHeap` property), the remeasured captures under `core/src/test/resources/captures/hpk_human/`, and docs; conflicts should be light. The session's work is committed on `feature/stratfinder` (message in section 9). Rebase onto `origin/dev`, then re-run `MeasurementInvariantsTest` as the sanity gate.

## 1. Where the project stands

The strat-template work went through three phases this session:

1. A generation phase (synthesize approaches from a hand-coded strat vocabulary). It produced benchmark wins, then failed the in-game review, then failed it again after fixes. It is now PARKED (see decisions).
2. A correction phase where the user re-measured all 34 hpk_human captures in-game, adding the true start region as a tick-0 X/Z range constraint, and taught the decisive game mechanics (see section 3).
3. The current phase: a substitution engine that transforms the recorded strat in place. Validated headless. The next session's main task is wiring it into the product as the approved Strat Finder v1 feature (section 4).

Verified ground truth: the in-game V-gate passed on 6 baked saves (j264 run1+jam/nt, j012 fmm1/nt exact tie, j018 run0+jam/nt, j029 run0+jam/nt, j066 pessi1, and j158 pessi4/nt deployed but still awaiting the user's check). These live in the user's MultiMC folder under `parkourcalculator/templates/`. Two capture repairs are still user-side: j030 (stored solver result is one tick short of the extended landing tick, needs an in-game re-solve and re-save) and j925 (baseline broke during its re-save).

## 2. The two-mode product decision

The user's framing, adopted: two modes that share the engine and the save format but have zero logic coupling.

- TAS mode: the existing product. Fixed inputs, solve yaws, byte-exact line. Unchanged.
- Strat mode (new): an outer loop around the same engine. Witness strat + constraints, enumerate input-timing substitutions, solve each on a scratch copy, rank, present a list the user steps through; clicking applies rows + result and retriggers the normal simulation.

Interop is at the artifact level only: both speak saves. A strat found in strat mode can be opened in TAS mode; a TAS line can serve as a witness in strat mode.

## 3. Domain rules the user taught (treat as spec)

- Edge-jump mechanic: 1.8.9 resolves the Y collision at the PRE-move X/Z, so `onGround` persists one tick past the block edge. Jump legality: only the tick BEFORE the jump must be within the block hitbox (plus the 0.3 standing overhang); the jump tick itself may be past the edge. EVERY timing (run jams, fmm, pessi, mark, bwmm) exploits this mechanic.
- Playability rule for strats: no turn on momentum, or a single turn at the jump angle (the tick where space is pressed the last time). 45-strafe jumps are exempt. Free-yaw solver lines that turn mid-momentum are not strats.
- bwmm is BACKWARD MOMENTUM (jump backward to gain runway), not backwalled. A wall behind means you cannot go backward even mid-air; the model cannot know walls.
- Constraints are the interface: the solver must adhere to constraints exactly as placed, and ONLY those. When a solve desyncs in-game, the player adds a constraint (wall plane etc.) and re-solves. The player is the geometry oracle.
- The timing parameter k (1t vs 6t pessi) is only marginally harder with experience; window tightness is the difficulty, not the ordering of named strats.
- Naming: "1bm 4b" reads as 1 block momentum, 4 block jump.

## 4. Approved next task: Strat Finder v1 (feature plan)

User approved this plan verbatim; start after the rebase.

1. Core sweep engine in `core/main`: enumerate in-place key-timing variants from the currently loaded inputs (post-jump key onsets and releases shifted one tick; W/SPRINT/A/D/S within ~12 ticks of each jump), solve each on a scratch copy via `AngleSolverEngine`, async and cancellable, mirroring the VelocityFinder threading pattern (per-sweep tokens, cancel must reach the drive loop). Open integration point: variants need per-tick input samples; in-app these come from the entity sim's TickStates, so either the `Simulator` port can run a scratch `InputData` (best, gives real collision per variant) or v1 derives samples row-side following the keepify convention (`SimplifyLoop.deriveDebugSamples` is the reference).
2. Ranking v1: feasible first, then fewest input edits from the witness, then solver margin. Porting combinedV4 into `core/main` is a follow-up ticket, not v1.
3. UI: Strat Finder section in the angle-solver window. Find button, progress, ranked table (label, edits, margin), row click applies (rows + result + retrigger so the replay is immediately visible), arrow-key step-through, top row is always the original.
4. Mode separation: touch nothing in TAS mode; interop through saves only.
5. Tests: enumeration unit test on fixture rows, headless sweep test; `HpkSubstScreen` remains the benchmark twin.
6. Handover: build all three loaders, commit message, user QA in-game (list feel, stepping, apply is the gate).

v1 scope limits (decisions, not surprises): no structure-changing variants (no new jump ticks), ranking is the proxy until the metric port.

## 5. The validated substitution engine (already built, test-side)

`metriclab/StratSubstitutions` + `HpkSubstScreen` (PKC_SUBST=1, PKC_SUBST_ONLY / _D / _MS, default 2000 ms). In-place key-timing edits on the recorded rows; tick structure and every placed constraint untouched; debug input samples re-derived via `SimplifyLoop.deriveDebugSamples`; free start within the save's own t0 constraint; the unmodified "self" variant is a canary (its failure means capture or budget problems, printed as CANARY FAIL).

Validation results (spot check, 2000 ms): 4/4 canaries pass (at 250 ms the tight lines miss their own re-solve, budget only, hence the 2000 default). j012 `SPRINT@1later` is the user's own "1t fmm to 2t fmm" example, feasible at +0.15. j066 has three feasible neighbors (A@1later +0.01, A@14later +0.07, W@1later +1.39). j158 and j032 have zero feasible neighbors: rigid, correctly detected precision strats. Rigidity is information (difficulty explanation), not a failure.

Not yet run: the full 34-capture substitution sweep (~30-40 min, one bounded JVM). No substitution in the 4-capture sample scored easier than the human; easier variants are expected on captures where the human overperforms.

## 6. Decisions and ruled-out approaches (ADR style)

None of these are set in stone. Each records the reasoning; with new evidence or a new approach they can be overruled. The "overrule trigger" states what would reopen it.

### D1. Generation layer PARKED (synthesized approaches, vocabulary grids, stamped runways)
Reasoning: the X/Z model has no world geometry. Every synthesized approach needed knowledge it cannot have (standable area, walls, edges) and each proxy failed: floor-expanded runways fabricated ground (off-block starts, failed V-gate round 1), recorded-hull runways denied real ground (killed verified-good saves), per-tick constraint stamping over-restricted (suffocated j001-class jumps by denying the edge-jump overhang, and broke every timing family). The final form (user t0 rect, fire-tick exemption) still guessed semantics the user never wrote.
Overrule trigger: captures carrying real block geometry (`experimentalBlockCapture` or re-saves with selected blocks). With true standable rectangles and wall boxes, generation plus SweptCollision post-checks becomes viable again, and the parked machinery (StratTemplates, plans grid, wide vocabulary) still exists in the tree.

### D2. Constraints exactly as placed; no synthesized constraints
Reasoning: user ruling after the stamping failures. The player defines the problem; the solver must not invent restrictions or relaxations. Desync handling is the player adding constraints, not the tool guessing legality.
Overrule trigger: none foreseen; this is the product philosophy.

### D3. Free start within the t0 constraint (not pinned to the recorded start)
Reasoning: user ruling. The t0 range constraint IS the start domain.

### D4. Free-yaw lines are not strat products
Reasoning: the playability rule (section 3). Free-yaw solves remain useful as feasibility probes (they show a line exists) but are never baked or presented as strats. Eleven tail captures flip feasible only via free-yaw; that is evidence for missing vocabulary or future modes, not output.
Overrule trigger: a future "TAS-assist" surface where unplayable lines are explicitly wanted (that is TAS mode's job already).

### D5. Corridor / automatic legality acceptance REJECTED
Reasoning: proposed as "transformed line must stay near the witness"; user ruled it makes no sense since the player adds constraints when a solve desyncs. Witness proximity is not a product concept.

### D6. Runway derivation from recordings REJECTED (both variants)
Reasoning: floor-expansion mis-attributes edge-overhanging positions to empty cells (j264 off-block class); pure recorded-hull is over-tight and kills verified-good saves (j158/j066 went infeasible). Both replaced by user-measured tick-0 constraints.
Overrule trigger: block geometry in captures (same as D1).

### D7. SHA ladder promotion BLOCKED; prune-only ladder validated
Reasoning: measured on d1-d6: prune-only (ReachBound rung 0) saves 35 percent wall at 97 percent recall (misses are solver run-to-run variance); SHA with met/total ranking saves 2.5x but drops recall to 64 percent and loses the pessi family specifically (met-count at low budget anti-correlates with closeness to feasibility).
Overrule trigger: a violation-magnitude ranking signal from failed solves.

### D8. ReachBound stays the norm bound only
Reasoning: two stronger variants (per-axis velocity-interval propagation, chained-gate boxes) measured exactly zero marginal prunes over the Euclidean speed-norm bound on 2380 instances. Box-shaped bounds cannot see angle-sliver infeasibility.
Overrule trigger: annulus-aware (zonotope-grade) bounds, or the LP relaxation as a ladder rung (below).

### D9. LP disk-relaxation certificates: shelved as tooling, sound and working
Reasoning: `HpkRelaxExportScreen` + `scratchpad relax_solve.py` (scipy HiGHS; Gurobi pip license rejects the QCP form; the 24-gon outer approximation is the working encoding). Proved j335 search-limited (90/104 relax-feasible) and j135 vocabulary-limited (68/104 certified infeasible), zero conflicts with solver outcomes. With generation parked, its consumer is gone.
Overrule trigger: generation revival (D1) or porting it in-repo as a ladder rung (commons-math3 SimplexSolver exists as a dependency).

### D10. GCS / joint mode-graph optimization: parked
Reasoning: literature-verified as the right machinery for the tail (Marcucci et al., shortest walks 2025), and the certificates identified its precise target (search-limited tails like j335). But the targets were free-yaw-only lines, which D4 removed as products, and generation itself is parked.
Overrule trigger: a class of playable, certified search-limited jumps appearing (for example after the corpus lands).

### D11. Older standing no-gos (from prior sessions, unchanged)
Learned per-instance solver selector (composition subsumes selection); CMA-ES with collision in the inner loop (model boundary; constraint generation is the right form if ever needed); manual input freezing (templates already are frozen inputs); SMT-FP as a searcher (verifier only); byte-exact certified global (combinatorially hopeless).

### D12. j066 grandfather
Its verified bake is a free-yaw-labeled line the user confirmed is playable in-game. It stays deployed; the playable-only bake filter would technically exclude it. Do not delete it on that technicality.

## 7. The 4K server corpus (upcoming, user-driven)

The user plans to export roughly 4000 strats from the server. Under the direction reset the PRIMARY use is a witness library: for a new jump, pick the nearest recorded strat and transform it with the substitution engine (transformation, not generation). The earlier idea (mine the corpus into a generalized template grammar: tokenize rows around fire ticks, motif clustering, known families as validation, residue clusters as new vocabulary, k priors for ordering) is documented and NOT ruled out, but it feeds D1 and therefore waits.
Before building anything: get a format sample of one or two strats first (mothball strings? rows? traces?). The tokenizer and importer design depends entirely on that.

## 8. Traps and practicalities

- Heavy screens OOM on the default 512m test heap: always pass `-PtestHeap=3g` (property added to `:core:test` this session). The template/bake pipelines OOMed twice before this existed.
- Solver run-to-run variance flips borderline instances between runs; never treat one run's feasible set as exact ground truth. 250 ms is below the re-solve cost of tight lines (300-620 ms); the subst screen defaults to 2000 ms for this reason.
- Compute discipline (user rule, learned the hard way): spot-check on a known-good plus known-bad pair before ANY full-population run; state the real cost up front; per-capture JVMs bound both heap and blast radius.
- Save names are sanitized on load by dev's SaveIO (only [A-Za-z0-9._-]); '+' in a filename makes the file unopenable. `HpkBakeScreen.safeName` handles it.
- The dev-based client re-saves captures with dev's schema; this branch parses them fine (Gson ignores unknown fields) but be aware after rebasing.
- `hpk_remeasure/` in the user's MultiMC parkourcalculator folder is scratch (already harvested); `templates/` holds the 6 deployed V-gate saves; the client's own `hpk_human/` folder is stale and diverged, do not sync over it.
- Env knob inventory: PKC_TEMPLATE(_ONLY/_D/_MS/_WIDE), PKC_BAKE(+PKC_BAKE_TAIL), PKC_LADDER(_ONLY/_D/_R1_MS/_TOP_MS/_PROMOTE), PKC_REACH, PKC_RELAX_EXPORT(_ONLY/_D), PKC_SUBST(_ONLY/_D/_MS).
- Artifacts under `core/build/hpk-metric/`: tags `-250ms`, `-tail2000ms`, `-timing250ms`, `-postfix250ms`, `-startrect`, `-rerun2000`, `subst-*`, `reach-report`, `relax/`. Pre-fix and post-fix artifacts are NOT comparable (different instances).
- d8 captures (j101/j105/j123) have no per-instance telemetry; j123's baseline carries a TAS-tight reconstruction flag (reads ~3 levels too hard).

## 9. Ready-to-paste commit message for the current tree

```
feat(anglesolver): strat substitution engine + user start regions; park generation

The stratfinder direction resets from generating approaches to
transforming recorded strats. StratSubstitutions + HpkSubstScreen
(PKC_SUBST=1) enumerate in-place key-timing edits over the recorded
rows (post-jump onsets and releases, one tick), keeping tick structure
and all placed constraints untouched, with the unmodified variant as a
solve canary; validated 4/4 canaries at 2000 ms, including the 1t to
2t fmm substitution on j012. All 34 hpk_human captures are remeasured
in-game with the true start region as a tick-0 range constraint;
template realization reads exactly that constraint (derivation
deleted) and exempts the jump tick per the edge-jump mechanic (only
the tick before a jump needs the block hitbox). The V-gate passed on
six baked saves. Screening ladder, ReachBound, LP relaxation
certificates, and the wide vocabulary remain as benchmark tooling for
the parked generation layer. :core:test accepts -PtestHeap (heavy
screens need 3g). Handoff: docs/research/stratfinder-handoff-2026-08-08.md
```
