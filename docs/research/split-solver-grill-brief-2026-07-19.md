# Split-solver plan: grill brief (2026-07-19)

Status: GRILLED 2026-07-19, same day. Every assumption A1-A23 carries a VERDICT line in section 4, section 6 carries the decisions, section 10 records the persona objective stacks, the amended architecture facts, and the approved sequencing checklist. The amended architecture in section 3 is approved for execution in that order. Nothing is built yet.

Status update 2026-08-21: the checklist is fully executed. The step-6 verdict, the A6 reversal, the tick-cap 256 ship, and the jerk metric with the 1e-2 lambda pin are recorded in docs/research/data/matrix-step6-finale/analysis.md. The CMA-ES, AlmSnapStage, and momentum-assembly mechanisms named in A5/A7/A8 and steps 4-6 were later removed (PRs 371-382), so mechanism names there are historical. The A-verdicts and persona rulings below stand verbatim as the dated record.

Companions: `solver-graph-learning-survey.md` sections 8, 8b, 8c (measurements + corrections). The former companion `telemetry-handoff-2026-07-18.md` was executed and deleted (2026-08-21); the telemetry outcome lives in code (SolveRunRecord/SolveRunLog) and in `solver-graph-learning-survey.md` section 8.

## 1. The user's stated goals (source of truth for the grill)

Verbatim intent, lightly structured, with grill annotations in brackets:

1. Reduce solve time.
2. Improve the objective where it matters, which is jump verification. [CONFIRMED 2026-07-19: the verifier wants a verdict against the given jump first; best value + margin is the secondary readout of the same run. See A11/A12 and section 10.]
3. Improve long solves ("solve length") WITHOUT the wiggle that CMA-ES produces. Smooth yaw trajectories. [AMENDED 2026-07-19: the solver never minimizes tick count; the TASer cuts T by hand and re-solves. TASer mode = solve at the given T, then smooth as much as possible. See section 10.]
4. A test harness that allows testing different OBJECTIVE TYPES (the named ones to start), so new modules can be evaluated quickly, including their parameter tuning.
5. Support three personas:
   - Stratfinders: need INPUT solving (keys, not only yaws) scored on an objective function that does not exist yet (seed: the human-easiness ladder, see section 5). [Persona ruling 2026-07-19: "the only thing that matters for the stratfinder is making the strat as easy as possible, nothing else".]
   - TASers: long solves, smooth. [Persona ruling 2026-07-19: losing a tick is worse than being not smooth; smoothness never trades across a tick count.]
   - Jump verifiers: is this jump possible, and what is the true best value. [Persona ruling 2026-07-19: a verifier does what a TASer does but does not care about wiggle or input difficulty.]
6. The user explicitly rejects "squeezing everything into a monolithic solver". Wanted shape: a solver for long solves, a solver for loopmm-class jumps (large constraint area, lots of free room to explore), and the default solver for simple jumps.
7. The user's own worry about their proposal: splitting brings back "a classifier for which solver is best", which this week's measurements no-go'd. [RESOLVED 2026-07-19: the grill went further than the counterposition; even structural predicates are dropped. Staged late-race replaces class detection entirely. See A1-A3.]

## 2. Evidence base (what this week actually established)

- Telemetry (Phase A+B, in-game verified): every solve emits a JSONL SolveRunRecord (config, problem hash, outcome, incumbent trajectory with per-node attribution); unseen problems auto-dump as replayable captures; editor shows node-colored incumbent strip + per-node improvement badges.
- full1 matrix (44 problems x 5 presets, 120 s): learned selector no-go on the solved corpus; VBS-SBS gap ~0 feasibility, <=1.4e-4 objective. Time spread real (exh30 = optimize60 quality at half wall).
- gen corpus (CaptureMutations: goal shift / corridor tighten / momentum scale; 190 mutants + frontier; 1002 records): 0 of 69 generated all-fail rungs cracked by bigger or structurally different presets. Capability frontier = 136 rungs + razor-proof-t1 + razor-weirdpane. band.txt is the standing benchmark ladder.
- CORRECTIONS (survey 8c, user-caught): loopmm-3jump-solver-misses ships with its pad DISABLED (weakened spec, was mislabeled frontier); original all-fails were wrongly excluded from escalation. After completing them: bidirectional preset flips on the redirect/long class:
  - loopmm-tight-t39: fast starves B&B (stuck viol 2e-2 at 120 s); bnb-heavy60 solves in 14.4 s (pattern B&B -> ILS); exh30 16.6 s. The user's years-old "run B&B first" claim is confirmed.
  - nix-full-t1: mirror image; fast solves in 52.6 s via momentum assembly; bnb-heavy60 fails outright.
- Conclusion carried into this plan: feasibility on the hard classes is decided by stage ORDER and allocation, i.e. by which solver shape runs, which is the user's split-solver intuition.

## 3. The architecture as amended by the grill (2026-07-19)

1. `simple`: the current fast chain (closed form -> SLP -> relax, race backup). Unchanged; dominant on its class.
2. `explore`: the bnb-heavy60 shape (pattern B&B early -> ILS), promoted from test-side GraphBuilder to a builtin graph. It is never dispatched by predicate; it spawns as the second arm of a staged late-race: fast runs alone, and at an effort-scaled checkpoint, only if there is no feasible incumbent yet, the explore arm starts alongside (fast is NOT killed). Rationale: pure kill-and-switch loses nix-full-t1 (fast needs 52.6 s and can look stuck at a 20 s checkpoint while bnb-heavy fails outright); race-from-t0 pays contention on every trivial solve.
3. `smooth-long`: NOT committed to AlmSnapStage. The TASer core is decided by a bake-off on the pinned-lambda TASer band at 60/80/100 ticks: incumbent chain with lambda scoring + existing polish vs AlmSnapStage with lambda. Winner takes the persona; the loser remains an ordinary graph node. Rationale: Stage A passed only a gate microbench, the Sheepram precedent is a razor-precision argument not a length argument, and nobody has ever measured the incumbent chain with smoothness in the score (A8).
4. Dispatch: UI = Auto + every persona selectable. Auto is STRATEGY-ONLY: it picks how the search runs but never changes what is optimized (same spec = same metric at any length). Personas are the only objective switch. No free-room measure, no redirect predicate, no ML. Manual override via persona selection and Custom.
5. Smoothness made measurable: all three candidate stats recorded in SolveRunRecord (total |delta yaw|, yaw-direction-change count, max per-tick delta). No metric is chosen by debate; logging all three is one field each.
6. Persona mapping: see section 10 objective stacks.

## 4. OPEN POINTS AND ASSUMPTIONS, with grill verdicts (2026-07-19)

Dispatch and classes:
- A1. Free-room area as a class signal. VERDICT: dropped as a requirement. Tick count already exists as a predicate (RouterPredicate.TICKS_LE_CAP); no free-room measure is needed because staged late-race replaces class detection. No thresholds get invented from n=2.
- A2. Redirect detection. VERDICT: dropped. The explore arm spawns on "no feasible incumbent at the checkpoint", a signal that needs zero new machinery, not on structural detection.
- A3. Do three classes partition the space. VERDICT: moot. Classes are search strategies racing under one objective; an overlap cannot misroute anything because both arms score identically.
- A4. Racing contention. VERDICT: confirmed real by code (BoundPrunedRecovery takes availableProcessors()-2 threads, IlsPolish batches availableProcessors()). MEASURED 2026-07-19 after step 4: nix-full-t1 pays ~9-15 s end-phase contention (55 s solo baseline vs 64-70 s with the explore arm alongside from the 20 s checkpoint). Race telemetry (SolveRunRecord.race: spawned, spawnElapsedNanos, winner, explore chain + node walls) records it per run, including capped runs.
- A5. Split evidence is n=2. VERDICT: firmed 2026-07-19 (matrix-race-gen1; its raw runs.jsonl was dropped in the 2026-08 doc cleanup, this verdict text is the record). The flip pair behaves exactly as predicted under the race (loopmm 36 s via explore arm, duplicated base identical; nix 64-70 s via primary); the gen-ladder mutants are UNCHANGED (all-fail rungs still fail at cap, easy g-2 rungs still instant), consistent with the survey ruling that those rungs are capability frontier, not config, and confirming the race regresses nothing.

Smooth-long:
- A6. AlmSnapStage scales to 60-100+ ticks. VERDICT: not assumed; bake-off required (section 3.3). The 0.08-block gateless model drift is precisely the kind of error expected to grow with horizon; the band decides, not the precedent.
- A7. What is "smooth" and what does it trade. VERDICT: smoothness is a weighted objective term (objective - lambda*wiggle) that reaches inner-loop scoring (natively in ALM, via scoredObjective for CMA stages), because stage-level selection alone cannot produce smooth candidates from a wiggly pool. It trades ONLY against positional value margin WITHIN a fixed tick count; it never trades feasibility and never a tick (user ruling: losing a tick is worse than not smooth). All three candidate stats are recorded regardless.
- A8. How wiggly are current solutions. VERDICT (MEASURED 2026-07-19, step 3 done): wiggle is SEARCH-INTRINSIC, not a polish-configuration gap. Baseline (matrix-a8base1, 51 problems x fast/optimize60): closed-form solutions are perfectly smooth (0 reversals); horizon/CMA/recovery chains reverse direction on 15-62% of ticks; optimize60 is WIGGLIER than fast (20% vs 9% mean reversal rate, worse on 28 of 48 paired problems), so more budget worsens style. A 16x smoothing budget (matrix-a8smooth1) changed reversal counts by 0-2 and travel by <1% on the 7 worst problems, paying objective at the 1e-4..1e-5 scale where it moved at all. Consequence: the lambda term must live inside search scoring (step 5) and/or a smooth-by-construction core must win the bake-off (step 6); post-hoc polish is a dead end. Full analysis: docs/research/data/matrix-a8base1/analysis.md.
- A9. Smoothness vs easiness ladder. VERDICT: hard separation. Lambda-wiggle serves TASers only. Rung 1 of the ladder is constant yaw, which is a constraint, not a smoothness score; TASer work must never be sold as stratfinder progress.

Explore:
- A10. Pattern B&B-first generalizes. VERDICT: not assumed. B&B-first is an empirical race arm, not a routing rule; the gen-ladder variants in checklist step 4 are the evidence mechanism. The nix-vs-loopmm boundary stays unexplained and does not need explaining for the staged design to be safe.

Jump verification persona:
- A11. What the verifier needs. VERDICT (user-confirmed): verdict-first against the given jump; best value + margin secondary. Three-state vocabulary: POSSIBLE (byte-exact witness + margin), BOUND-BLOCKED (relaxed-model bound + margin, shown ONLY when a certifying stage actually ran), UNKNOWN (effort + best value + gap). The word "impossible" never appears unqualified; certified byte-exact impossibility remains off the table per the prior OMT/SMT-FP survey.
- A12. Aggregating stage guarantees. VERDICT: v1 surfaces only certificates that already exist (closed form, capCertify dual gap) instead of swallowing them. No new aggregation plumbing; general relaxed-bound stages are admitted as real new scope and not scheduled.

Stratfinder / input solving:
- A13. Discrete input space. VERDICT: refinement before synthesis. v1 starts from a solved TAS and searches the discrete neighborhood descending the easiness ladder; full synthesis is not attempted first.
- A14. X/Z model cannot score input candidates. VERDICT (user-confirmed): entity-verified search. The loader SimulatorEntity already evaluates a full key+yaw script byte-exactly including Y and collisions; the "entity cannot be the inner loop" ruling was about the continuous yaw solver's 1e5-1e6 evaluations, and discrete refinement needs orders of magnitude fewer. The X/Z model pre-filters only candidates whose slip pattern is untouched. No Y/collision-aware headless model is ever built; the standing "X,Z + tick only" ruling stands. Consequence: input-solving verification is in-game, matching the existing headless-test-boundary rule.
- A15. Easiness objective form. VERDICT (user ruling): easiness is the ONLY thing that matters for a stratfinder besides making the jump. Lexicographic ladder descent: try rung 1, fall back down; output = best rung achieved + solution; manual rung-pin is the degenerate case.
- A16. Strat output format and tolerances. VERDICT: partially resolved for free. Rung 1 is parameterized by (key combo, yaw, start position); the sweep's surviving intervals ARE the tolerance windows, so the rung-1 deliverable is a yaw window + start-position window. Tolerance for higher rungs stays open and deferred with the rest of the stratfinder build.

Objective-type test harness:
- A17. Objective is axis+tick+sense only. VERDICT: confirmed in code (AngleSolverEngine.java:400). The abstraction shrinks dramatically after the persona rulings: run-level scoring config {base axis/tick/sense + optional weighted wiggle term}. NO time objective type (the user cuts ticks by hand); easiness is outer-loop config for later; verification margin is a report, not an objective. The objectives-are-not-nodes ruling survives intact: lexicographic tiers (easiness rungs) live in outer drivers that re-solve fixed problems; only the weighted smooth/value blend enters inner-loop scoring. BUILT 2026-07-20 (step 5): Objective.smoothLambda + scored(); see checklist step 5 for the feasibility firewall and the 64-tick reach limit.
- A18. Parameter tuning support. VERDICT: build the param-sweep mode during the bake-off (checklist step 6), which needs it anyway for lambda pinning and ALM tuning.
- A19. Metric plumbing. VERDICT: confirmed write-only in code. SolveRunRecord.metric becomes self-describing (records lambda) and is consumed by the band tooling in checklist step 5.

Process / hygiene:
- A20. Benchmark artifacts durability. VERDICT: ALREADY DONE, verified 2026-07-19: docs/research/data/ holds matrix-full1 (runs.jsonl + analysis.md) and matrix-gen1 (runs.jsonl + band.txt + analysis.md). Pending commit only. (2026-08: the raw runs.jsonl files were dropped in the doc cleanup; the analysis.md and band.txt files are the durable record.)
- A21. Uncommitted surface. VERDICT: checklist step 1; the user commits.
- A22. CUSTOM + saved preset runs long. VERDICT (DIAGNOSED 2026-07-19, step 2 done): the suspected mechanism is FALSIFIED. Real artifacts (t1.json saved at M2, formatted_legacy.json saved at M5, both under loader-forge-1.8.9 run/client/parkourcalculator/graphs/) materialize with params byte-identical to their builders; M5 kept every catalog default equal to the old hard-coded constants, and the "missing" rescue nodes are the stopOnFeasible-gated part of the builder, not drift. Actual mechanism: both presets are snapshots of the legacy Custom shape built with timeBudgetSeconds=0, which bakes momentumAssembly budgetSec=240 and uncapped race nodes; under CUSTOM effort there is no overall deadline (timeBudgetSeconds default 0) and stopOnFeasible defaults false, so on problems the seed chain does not crack, nodes grind their full baked budgets. Reproduced headlessly on loopmm-tight-t39: CUSTOM+formatted_legacy = 242 s with the momentum node burning its full 240 s and returning NONE (final infeasible), vs 29.9 s solved for an optimize60-shaped graph; on nix-full-t1 and j001 the same presets behave normally. Deterministic (budget-cap exhaustion, not a race). CONSEQUENCE: saved presets ARE trustworthy for steps 3-6 (materialization is faithful; the matrix caps runs externally). The in-game trap is budget semantics, a design decision, not a defect; options recorded with the user. Round-trip fidelity already regression-locked by GraphPresetIOTest.
- A23. Disabled constraints define the problem. VERDICT: rule stands unchanged; already recorded twice.

## 5. Assets inventory (what exists and is reusable)

- Graph runtime + node editor + presets (M1-M5, in-game verified) including Router predicates; the dispatch router fits this natively.
- Telemetry: SolveRunRecord/SolveRunLog + per-node attribution + problem auto-dump.
- RunMatrixScreen / MatrixAnalysisScreen / CaptureMutations / band mechanism; benchmark ladder (band.txt + 136 rungs + razor pair).
- AlmSnapStage Stage A (built, V-gated, UNPROVEN on razor benches) = the smooth-long bake-off contender. REMOVED 2026-08 (issue 380).
- bnb-heavy60 graph (test-side GraphBuilder) = the explore arm; needs promotion to builtin.
- Human-easiness ladder recorded in memory (user's exact hierarchy).
- Known preset-flip regression pair: loopmm-tight-t39 (explore must win) + nix-full-t1 (simple/momentum must win).

## 6. What the grill decided (2026-07-19)

1. Classes: NOT the abstraction. Search strategies are. Persona is explicit (Auto + every persona selectable); Auto is strategy-only and never changes the objective; simple-vs-explore is handled by staged late-race, not detection; no predicates are invented from n=2.
2. Smooth: three stats recorded always; mechanism = lambda-weighted wiggle term in inner-loop scoring; trades only value margin within a fixed T; never feasibility, never a tick. The solver does not minimize ticks; the TASer does, by hand.
3. Verifier: verdict-first against the given jump (POSSIBLE / BOUND-BLOCKED / UNKNOWN with the honesty rules in A11); best value + margin is the secondary readout; only existing certificates are surfaced in v1.
4. Stratfinder: well-posed ONLY as entity-verified discrete refinement (A14); rung 1 is the v1 target and emits tolerance windows for free; build stays LATER per section 8.
5. Harness: minimal abstraction = run-level scoring config {base + lambda*wiggle}; no time type; easiness rungs are outer-loop config; objectives never become nodes. Standing ladder stays lambda=0 forever; TASer band is separate with one pinned lambda chosen after the A8 baseline; lambda recorded in SolveRunRecord.metric.
6. Sequencing: approved as the numbered checklist in section 10.

## 7. Positions taken during the discussion, with outcomes

- Claim: structural rule dispatch + racing is NOT the no-go'd classifier. OUTCOME: superseded; the grill dropped even structural predicates. Staged late-race needs no signals at all.
- Claim: smooth-long should be ALM/BFGS-based, not CMA-based. OUTCOME: demoted to bake-off hypothesis; the Sheepram precedent argues precision, not length.
- Claim: the monolith looked dominant partly because the metric ignored solution style. OUTCOME: plausible, unproven; the A8 baseline is the measurement that settles it.
- Retracted during the session: "nothing beats fast on feasibility" (survey 8c).

## 8. Raw decisions already made by the user (do not relitigate)

- All three mutation classes stay in the generator (goal shift, tighten, momentum incl. m0.0).
- Learned selector stays parked regardless of the split decision.
- Human-easiness solver is LATER; the grill recorded its design verdicts (A13-A16) but building it stays out of scope.
- The user handles all git operations.

## 10. Grill verdicts: persona stacks, verified facts, approved sequencing

Persona objective stacks (user-confirmed):
- Auto: strategy dispatch only; the spec's objective is never altered; staged late-race inside.
- TASer: T is fixed (the user cuts ticks by hand and re-solves; the solver never optimizes tick count). Job = solve at the given T, then smooth as much as possible; lambda controls how much positional value margin smoothness may eat; feasibility is never traded.
- Stratfinder (later): feasibility, then easiness only; lexicographic ladder descent; entity-verified refinement starting from a solved TAS; rung 1 first.
- Verifier: the TASer solve at lambda=0 and max effort; verdict-first three-state output; margins reported at the scales the reach-margin ruling demands where a certifying stage ran.

Codebase facts verified during the grill (2026-07-19):
- Objective = (axis, sense, tick) only: AngleSolverEngine.java:400.
- SolveRunRecord.metric is serialized, never read.
- RouterPredicate has TICKS_LE_CAP and JUMPS_LE_ONE; no free-room or redirect predicate exists.
- BoundPrunedRecovery uses availableProcessors()-2 threads; IlsPolish batches availableProcessors(); racing oversubscribes by construction.
- docs/research/data/ already holds matrix-full1 and matrix-gen1 durably (A20 done).

Approved sequencing checklist (approved as ordered, 2026-07-19):
1. User commits the outstanding surface (A21).
2. DONE 2026-07-19: A22 diagnosed (see the A22 verdict in section 4); presets are trustworthy, benchmarking may proceed.
3. DONE 2026-07-19: stats shipped (SolveRunRecord.smoothnessOf, wrap-normalized deltas, unit-tested); baseline + 16x-polish comparison ran (matrix-a8base1 + matrix-a8smooth1); verdict SEARCH-INTRINSIC, see the A8 verdict in section 4.
4. DONE 2026-07-19: staged late-race shipped. BuiltinGraphs.explore() (the bnb-heavy shape); AngleSolverEngine.runStagedRace under FAST effort only: primary runs alone, at the 20 s checkpoint (RACE_CHECKPOINT_NANOS) with no feasible incumbent the explore arm spawns ALONGSIDE with its own GraphContext, cancel token, copied scenario (fast-graph momentum/free nodes mutate theirs mid-solve), and own SolveProgress forwarding improvements to the primary (node label "explore"); a primary finishing infeasible pre-checkpoint spawns explore immediately (sequential escalation, zero contention); the first arm to finish byte-exact-feasible cancels the other; winner = feasibility first then objective. GATE GREEN: loopmm-tight-t39 solves under FAST in ~36 s (was never; new fixture solve/loopmm-tight-t39-fast pins it), nix-full-t1 still solves (64-70 s, ~9-15 s measured contention), full suite green, gen-ladder mutants unchanged (matrix-race-gen1; raw runs dropped in the 2026-08 doc cleanup, the A5 verdict above is the record). In-game verify pending; live editor viz shows the primary arm only, explore improvements appear as forwarded "explore" samples in the incumbent strip.
5. DONE 2026-07-20: objective abstraction shipped. Objective carries smoothLambda (default 0) with scored(raw, yaws) = raw -/+ lambda * yawTravelDeg (wrap-normalized deltas, Angles.travelDeg); the term reaches inner-loop scoring (CmaesJumpHarness fitness + compass polish, BucketAscentPolish, IlsPolish) and every acceptance comparison (SolveCore selection, scoredObjective, ILS/dualChain/freeStartImprove node acceptance, SolveProgress incumbent, staged-race winner). FEASIBILITY FIREWALL (trap found during build): lambda propagated into momentum assembly's window specs made nix-full-t1 INFEASIBLE under l1e-4 (assembly NONE at full budget via the closer's distorted entry search); all feasibility machinery now runs lambda-free by construction (momentum assembly, homotopy closer, receding-horizon windows, setup peel, feasibility-only CMA rescue), and certificates/B&B bounds/legal wrap search stay raw. metric.smoothLambda recorded; run-level knob AngleSolverState.setSmoothLambda (default 0, no UI, no persistence; standing ladder stays lambda=0). TASer band created: docs/research/data/matrix-taser-pin1/band.txt (all six 50+-tick corpus problems; NO 80/100-tick rungs exist, gap flagged for step 6). PIN = 1e-3 from the taser-pin1 sweep (l1e-5 inert, l1e-4 free wins, l1e-3 strictly more smoothing at milliblock cost; j007 travel 1404 -> 192 deg at zero cost, j335 reversals 10 -> 2, feasibility 15/15; standing preset taser60-l1e3). MEASURED LIMIT: over the 64-tick router cap the chain (receding horizon -> closed form/SLP windows) never consults lambda, so j001/j002/j003/j155 are lambda-inert; the incumbent-with-lambda bake-off contender is live only at <=64 ticks. Full record: matrix-taser-pin1/analysis.md; gate green (suite 3m37s, ladder untouched at lambda=0).
6. Bake-off (A6): incumbent-with-lambda vs AlmSnapStage at 60/80/100 ticks on the TASer band; winner takes the persona; build param-sweep mode (A18) here. IN PROGRESS 2026-07-20, machinery + first leg done: (a) AlmSnapStage has NATIVE lambda per A7: SmoothJumpProblem carries objective.smoothLambda as a wrap-aware pseudo-Huber yaw-travel term (eps 1e-4 rad) inside both augmented-Lagrangian paths with analytic gradients (FD-checked in AlmSmoothLambdaTest); acceptance is scored everywhere it selects among feasible candidates (AlmBfgsCore run selection via travelPenalty, AlmSnapStage winner via smoothPenalty on game facings, SnapRepairPolish Polish-mode grades); the firewall holds by construction: constraint terms, ALM violation measures, and Repair-mode feasibility decisions never see lambda, and lambda=0 is byte-identical through the whole stage (pinned by test). (b) A18 param-sweep mode: PKC_MATRIX_SWEEP env on RunMatrixScreen (grammar in TESTS.md; taser<sec> engine presets, alm<sec> direct-AlmSnapStage driver with l/seeds/topk/cooking/gate cross-products, static-preset reuse; RunMatrixSweepTest pins the parse). (c) 60t shakedown leg RAN (matrix-bakeoff60-1): incumbent 6/6 feasible and wins objective on every comparable row (sub-milliblock to 0.084 blocks); ALM 3/6 (misses nix-full-t1, trp, j001-353t where it snaps nothing in 60 s), always lower travel on solved rows (up to ~2x) but MORE reversals (62 vs ~30 on j002/j003), a low-travel high-jitter signature worth a metric-readout decision. STILL OPEN before the verdict: 80/100-tick rungs (user producing captures), ALM tuning sweep at fixed lambda, warm-seed policy for the production persona (matrix legs stay cold by the seedless rule). Full record: docs/research/data/matrix-bakeoff60-1/analysis.md.
7. Verifier verdict layer: three-state, verdict-first, surfacing existing certificates only.
8. Later, unscheduled: stratfinder per A13-A16 verdicts (entity-verified refinement, rung 1, tolerance windows).
