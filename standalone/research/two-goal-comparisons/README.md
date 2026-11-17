# Separate spatial-time and fixed-clock comparisons

Implemented on `research/ipopt-cell-routes`, isolated worktree `ParkourCalculatorMod-ipopt-cell-routes`. Runtime source: `1306f0e5`. Audit effort reporting: `689345e7`; stricter generation-coverage accounting and new separated-jump controls: `3e31e732`; competitive follow-up: `448588b2`; shared conservative spatial-probe coverage metadata: `10b0a2ae`; reference error handling: `1b0edc66`. This is the default checkpoint-search direction. The old `objective_band` policy is an explicit experimental baseline.

The two measurements remain distinct:

- Shared spatial goal G: best-found accepted time to actual landing on the same block, or entry into a declared intermediate region/stage.
- Shared absolute state tick h: horizontal progress along the common outgoing route direction, with the same anchor and readiness context.

Progress at one candidate's arrival is never compared with another candidate's progress at its different arrival. A terminal trajectory ending before h has an unknown clock result, without a held endpoint. All Y remains feasibility. Existing discrete generation, movement/contact/blockage builders, normal momentum and zero velocity thresholds are reused; IPOPT is unchanged.

## Behavior

`checkpoint_search.find_checkpoint_route` now defaults to `comparison_policy='time_progress'`. It evaluates existing discrete candidates before progress-only retention can erase a faster final completion. Spatial probes use the existing natural continuation generator, not a request to populate distinct finishing-time buckets. Their accepted final-goal witnesses immediately update the incumbent.

`time_progress.retain_two_goals` requires a single comparable peer that is no worse on both spatial time and fixed-clock progress, and better by more than an explicit margin on at least one. Defaults are one tick and 0.25 blocks. Preserve near candidates, tradeoffs and incomplete measurements. A hard active cap still exists; its omissions have separate labels and saved symbolic programs. Missing measurements never establish marked inferiority. This is heuristic retention, not exact state dominance.

`checkpoints.retain_decisions` carries symbolic decisions, conditional repair state and facing guesses. It does not turn temporary guards inferred for one clock optimum into permanent commitments. Later solves start at the original full position/velocity, with every earlier facing variable. Bounded runtime rescue reopens parked histories when there is no final incumbent and resources remain. Successful-but-suboptimal commitments require reference testing, not just failure rescue.

Final landing requires the target top and grounded state, together with existing finite support/scene/corridor validation. Intermediate region comparisons support an explicit horizontal rectangle, optional Y range and required route segment. These goals are evaluated through existing constraints; there is no generic new disjunctive goal solver or new physics model.

## Search results

Single serial run per condition, one numerical thread, the same fixture search deadlines and numerical-call limits. Historical baseline is `e3d1087b`, saved in `../orchestrator-fixes/evidence/`. It is a historical control, not a statistical timing claim. The extra spatial evaluation work is deliberate and reported.

| Scene | Previous completion | New completion | New calls | New wall time | Main / spatial prefix expansions |
| --- | ---: | ---: | ---: | ---: | ---: |
| ceiling | 13 | 12 | 52 | 11.10 s | 71 / 157 |
| grind-or-run | 11 | 11 | 3 | 5.30 s | 512 / 0 |
| multi-support | 14 | 14 | 52 | 11.85 s | 66 / 307 |
| open | 12 | 12 | 29 | 5.46 s | 60 / 116 |
| step-or-jump | 14 | 13 | 47 | 20.00 s | 161 / 543 |
| turn-blockage | 13 | 12 | 99 | 14.04 s | 18 / 100 |
| separated-two-jumps, reverse | new control | 24 | 86 | 16.62 s | 144 / 269 |
| separated-two-jumps, forward | new control | 24 | 86 | 16.17 s | 138 / 269 |

All eight return accepted routes. The original six improve three completion times and preserve the others. They take 282 numerical calls versus the previous 202, about 67.75 s versus 37.02 s, and 2,111 total prefix expansions versus 682. The latter includes 1,223 extra spatial-probe expansions; do not present the main expansion counter alone as total work. This implementation improves the decisions, not speed yet. Stepping still hits its 20 s search deadline, after finding its 13-tick route at 1.76 s. Grind still reaches its main prefix limit after finding 11 ticks at 0.63 s.

The new controls use three flat supports requiring two jumps. Supplied opportunity ranges produce checkpoints at absolute state ticks 10 and 22. They exercise separated decisions and the reverse X objective; each keeps 12 histories at both windows. They are useful continuity/direction controls, not adversarial evidence that all separated decisions are weakly coupled.

## Additional-candidate reference

The reference saves raw discrete programs before retention and actually generates, numerically optimizes and independently replays their full continuations. It removes inherited closed repair state, keeps all earlier facings free, and applies no progress retention or incumbent time cutoff. It includes hard-cap omissions and the additional produced candidates that normal search did not evaluate. Necessary infeasibility screens may reject a prefix without producing a complete numerical program.

Initial reference budgets are separately reported: 4 s and 48 numerical calls per source history, 0.7 s per individual numerical candidate, at most 12 calls for a complete program evaluation. The original search's movement-generation settings remain explicit. This is separate reference effort, not a matched-work speed comparison.

| Scene | Saved source histories | Initially completed references | Complete programs tested | Accepted reference replays |
| --- | ---: | ---: | ---: | ---: |
| ceiling | 27 | 27 | 15 | 15 |
| grind-or-run | 3 | 1 | 3 | 1 |
| multi-support | 116 | 79 | 299 | 229 |
| open | 22 | 22 | 10 | 10 |
| step-or-jump | 23 | 13 | 8 | 7 |
| turn-blockage | 18 | 17 | 8 | 7 |
| separated-two-jumps, reverse | 48 | 48 | 14 | 14 |
| separated-two-jumps, forward | 48 | 48 | 14 | 14 |
| Total | 305 | 255 | 371 | 297 |

All 305 source histories were processed. The reference made 742 numerical calls. Neither an omitted history nor a retained history produced a better final result than the heuristic incumbent. No omitted history tied the best completion time in these runs. There are 138 search-witness replays plus 297 reference-witness replays, all 435 passing the standalone scalar, finite support and shared scene checks with exact horizontal reproduction.

The 50 initially incomplete references are not classified infeasible or safe. Reasons include capped stepping-choice generation, exhausted contact alternatives without a proof, contact-branch limits, and numerical/deadline limits. For example, the two ordinary grind alternatives have an initially model-feasible trajectory colliding with the low ceiling; their available avoidance branches are not exhausted. Most large support-map unknowns occur on slow continuations already beyond the incumbent's completion time. Increasing numerical solve time alone does not remove a choice-generation or contact-branch cap.

A second competitive follow-up targets previously incomplete omitted histories whose prefix ends no later than the accepted incumbent. It allows 8 s and 96 calls per history, increases generation limits to 4,096 prefixes/queued prefixes and 200,000 choices per type, and sets the horizon to the accepted finishing time. A route completing after that time cannot improve the time-first final objective; a tie still remains eligible. This follow-up is additional quality verification, not a replacement for the full continuation simulations above.

Seven support-map and four stepping histories met that condition. All 11 follow-ups exhausted their continuation generation with no complete program achievable by the incumbent time in the supported model. No numerical call was necessary in this follow-up: generation and necessary screens ruled out such continuations before IPOPT. Other scenes had no incomplete competitive omissions to follow up. The original unrestricted references remain incomplete; these narrower results close the omitted-candidate quality question for this saved pool and tested catalogue/horizon, not global search completeness.

The numerical test `test_full_regression_continues_a_discarded_actual_faster_route` separately marks the known early ceiling history as omitted and reoptimizes it. The audit correctly detects 12 ticks beating a retained 13-tick history. Thus the reference can detect a real lost winner; its lack of a new counterexample here is not merely a result of replaying retained candidates.

## Validation and reproduction

The focused gate passes 86 tests, including numerical solves. The wider gate passes 418 tests with 87 environment skips. After the final metadata/error-accounting corrections, all 24 goal/retention contract tests pass, including numerical controls. See the archived logs and manifest for source/runtime hashes. The wider suite excludes the previously documented unrelated ladder failure `test_ladder_composition.LadderRegionTests.test_repeated_ladder_ceiling_events_regenerate_the_complete_schedule`; environment-gated numerical tests skip there and run in the focused gate. No mod Gradle suite or actual Minecraft replay was run.

From the isolated worktree:

```bash
export OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1
.venv-guidance/bin/python -m standalone.autonomy.cell_routes.two_goal_experiment --part search --output standalone/autonomy/build/two-goal-search.json.gz
.venv-guidance/bin/python -m standalone.autonomy.cell_routes.two_goal_experiment --part audit --input standalone/autonomy/build/two-goal-search.json.gz --reference-seconds 4 --reference-calls 48 --output standalone/autonomy/build/two-goal-audit.json.gz
.venv-guidance/bin/python -m standalone.autonomy.cell_routes.two_goal_experiment --part competitive-audit --input standalone/autonomy/build/two-goal-search.json.gz --prior-audit standalone/autonomy/build/two-goal-audit.json.gz --reference-seconds 8 --reference-calls 96 --output standalone/autonomy/build/two-goal-competitive-audit.json.gz
.venv-guidance/bin/python -m standalone.autonomy.cell_routes.two_goal_experiment --part search --input standalone/autonomy/experiments/two-goal-comparisons/fixtures --output standalone/autonomy/build/two-goal-separated-search.json.gz
.venv-guidance/bin/python -m standalone.autonomy.cell_routes.two_goal_experiment --part audit --input standalone/autonomy/build/two-goal-separated-search.json.gz --reference-seconds 4 --reference-calls 48 --output standalone/autonomy/build/two-goal-separated-audit.json.gz
```

`evidence/` holds exact fixture copies inside each search report, complete results/repair states/witnesses, reference settings and outputs, logs, summary and hashes. The first search run overlapped a focused test run, so it is archived separately as `two-goal-search-concurrent-tests.*`; it found the same completion times but is not the serial timing measurement. Initial six-scene audit source `689345e7` used looser source-generation summary accounting. The later conservative accounting changes no per-history completeness outcome in those saved results; `summary.json` recomputes source coverage recursively from saved reports using the corrected rule. The final spatial-probe metadata correction also avoids labeling capped generation complete; it changes no numerical trajectory or retention decision. Archived earlier per-probe generationComplete fields must not be interpreted as a coverage proof.

## Limits and next work

The improvement is meaningful on these controls, but the time/progress margins are still heuristics. Only 11 original-six histories were classified markedly worse; many other omissions came from unknown/candidate caps. We have not established a useful general rejection rate, a global optimum, automatic coverage of arbitrary map opportunities or all spatial routes. Scene guides and opportunity ranges are supplied. Continuous optimization is best-found, without objective/minimum-time certification. Arbitrary mixed passage/contact goal predicates and automatic comparison-point selection remain beyond the implemented landing/region interface.

The first pass can still discover missing geometry constraints through the existing lazy repair loop. This work changes comparison and continuation orchestration, not the constraint catalogue or IPOPT model. Saved candidate-pool checks cannot find ungenerated root histories. A second reference in a restricted competitive horizon cannot prove the correctness or completeness of every movement generator.

Keep the present simple two-goal comparisons until a continuation audit shows a ranking counterexample. Next, reduce repeated continuation generation among similar prefixes while preserving separate continuous re-solves; measure nearer intermediate spatial goals on longer routes; and add held-out maps with stronger separated-decision coupling and geometry variation. Use those failures to choose retention diversity, reopening or a richer motion score. Runtime failure rescue alone cannot expose a successful-but-suboptimal earlier commitment, so the additional-candidate audit remains part of regression.


Priority update, 2026-11-02: the user accepts minutes or longer and values accuracy much more than latency. The next milestone is broader generation/retention and fresh-root joint references, stronger coupling tests, reliable provisional-continuation comparisons and quality convergence at larger settings. This supersedes the runtime-first ordering in the preceding historical recommendation. See the accuracy-priority section of [the implementation plan](../../TIME_PROGRESS_IMPLEMENTATION_PLAN.md). No new measurements or runtime behavior in this priority update.
