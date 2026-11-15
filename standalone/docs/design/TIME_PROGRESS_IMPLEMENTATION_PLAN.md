# Implementation plan: spatial-goal time and fixed-time progress comparisons

Revised 2026-11-01 after the user's distinctions. This is our selected new search direction, not a rollout proposal. Implementation is now delivered; see the dated status below and [measured results](experiments/two-goal-comparisons/README.md). Work stays in isolated `research/ipopt-cell-routes`, reusing existing candidate generation, movement/constraint builders, numerical solving, physical checks and saved state.

Current follow-up plan: [STATE_PROTECTION_IMPLEMENTATION_PLAN.md](STATE_PROTECTION_IMPLEMENTATION_PLAN.md), covering the selected velocity/schedule guards, unchanged0.25-block/1-tick margins, `(n,-J,R)` processing, persisted deferred work, exact audit and standalone/Real11 acceptance gates. The previous two-goal implementation exists; these protections and queue changes remain pending. The proposed0.05 margin is not adopted. This consolidated plan supersedes scattered discussion-only suggestions for the next runtime change; extra velocity-objective solves and unrelated route/generation redesign are outside its scope.

## Latest selected refinement, 2026-11-02

The earlier two-goal implementation exists; the following newly selected refinements are not implemented yet. Follow [the precise schedule/order rules](TIME_PROGRESS_FRONTIER.md#selected-schedule-comparison-and-processing-rules):

- Gate absolute-tick position/velocity pruning on matching ground/air/jump schedules and compatible contact context. Count equality alone does not establish phase equality.
- Across different schedules, do not discard a candidate in favor of a peer if the candidate has fewer run inputs OR more effective grounded jump inputs. Preserve shared-event time/state tradeoffs and the user-selected0/8 setup families.
- Use the exact processing order `(total elapsed ticks, -actual jump count, grounded nonjump count)`, with a stable final tie-break. Supersede the pilot's support-progress-first ordering for this policy; processing rank is separate from retention protection.
- Extract counts from existing composed profiles. Grounded nonjump inputs can be embedded even when the inner run allocation is zero. Do not count airborne duration or ignored jump presses as jumps.
- Preserve position/velocity from the same accepted witness, existing fixed movement/geometry constraints, full-original-state free-facing resolves and normal momentum with zero thresholds. Feasibility screens, resumable deferrals and honest resource-omission reporting remain.

Acceptance cases for the eventual policy change: at commonstate10 the0 setup (n10,J1,R0) precedes8 (n10,J1,R8), while both survive; atB2 the found8 setup(n17,J1,R8) precedes0(n21,J2,R0), while0 survives on both structural guards. Add equal-count/different-schedule and more-jumps/fewer-runs tradeoff controls; verify a lower short score never bypasses the cross-schedule protection. No new numerical solve or runtime change was made in this planning turn.


## Accuracy priority, 2026-11-02

User accepts minutes or longer per solve and prioritizes accuracy over latency. This supersedes the preceding recommendation to make repeated-generation cost the next priority. Do not tune toward the old 20-30 second pilot settings or treat a larger wall-clock allowance alone as the fix. No runtime setting or numerical result changes in this discussion checkpoint.

The next milestone is an accuracy-oriented continuation/coverage experiment using the existing solver and movement builders:

1. Expose and independently widen all loss mechanisms: root/choice/queue generation limits, candidate evaluation limits, active-history retention, fully unknown reserves, contact-branch enumeration, spatial-probe coverage and elapsed numerical work. The API's defaults still include four active histories and one fully unknown reserve; the measured fixtures used eight or twelve histories. More time does not restore a candidate already dropped by a count cap. Keep resumable pending/parked histories and report every unresolved branch and omission reason.
2. Build small fresh-root finite references, jointly considering the relevant discrete decisions without time/progress retention. Existing saved-pool audits cannot reveal a useful root history that was never generated. Reoptimize all facings from the original state with unchanged IPOPT. Larger reference effort must lift count caps as well as time limits and distinguish supported-domain exhaustion from unknowns and unsupported branches.
3. Compare sequential retention with those references and with enlarged joint clusters. Add held-out geometry where an earlier discrete decision interacts with a much later ceiling, step, blockage, landing or turnaround. Include successful-but-suboptimal searches, not only failures. Use counterexamples to choose when to reopen earlier histories or merge clusters; current failure-only bounded rescue cannot fix a slower successful incumbent.
4. Give each candidate enough geometry-valid provisional continuations to make its shared-goal time useful. A measured feasible completion time is an upper bound on that candidate history's best achievable time, not a lower bound or proof that an untested history is worse. An inferior/default future can create a misleading time comparison. Broaden/resume these probes and compare alternative futures before trusting a heuristic exclusion; unknown remains eligible. Keep fixed-clock progress separate. No certified-bound machinery, weighted motion score or continuous-model redesign is required for this milestone.
5. Extend tests from supplied corridor/opportunity domains to alternate spatial routes and perturbed interaction ranges. Automatic graph/route machinery exists, but the eight guided controls do not establish end-to-end coverage. Track whether the winning branch was generated, evaluated, retained, revisited and independently replayed, rather than reporting only final feasibility.
6. Measure quality convergence, for example at one, five and fifteen minutes with independently widened generation/retention settings, using repeated runs where deadlines change discovery. Report best final time, same-time final objective, unresolved references and discrepancies against joint finite references. Stabilization over tested settings is useful evidence, not a global optimum certificate. Optimize duplicate work only after these accuracy gaps are understood, unless reuse directly enables broader coverage.

Existing finite-scene acceptance/replay is a strength, but validates found trajectories rather than search completeness. The remaining lazy geometry-selection/repair loop can still leave valid branches unresolved; complete relevant existing constraints before each call where practical while retaining useful iterative feedback. This is orchestration work, not a reason to change IPOPT or rebuild the movement catalogue. Current no-ladder/no-intentional-wall-hit/zero-threshold scope remains.

## 1. Preserve candidate generation and the decision model

Generate the candidate discrete histories within the declared supported search domain: nearby coupled choices jointly, separated choices sequentially, with earlier decisions retained in each branch. The purpose is to consider the candidates, not to manufacture a collection with deliberately different finishing times. Different timing outcomes arise from the candidates and their continuous optimization. Do not bias generation around a preselected list of arrival-time buckets or require candidates to finish at distinct times.

Generation still uses existing constraints and necessary feasibility screens. Any finite horizon, work limit or heuristic generation omission is recorded separately from the comparison/retention policy. A truncated generated pool is not all legal game paths.

Each numerical call fixes the selected discrete inputs and constraints while all facings remain variable. Later decisions are evaluated by re-solving from the original full initial state, not by fixing a previously optimized intermediate position, velocity or facing. Weak coupling of separated decisions remains a hypothesis to test. No new movement physics, mixed-input relaxation, richer weighted objective, velocity-threshold logic or objective bound. Normal momentum carries and current thresholds stay zero.

## 2. Evaluate the same candidate pool in two different ways

The central distinction is what is held fixed and what is optimized/measured.

| Comparison | Shared condition | Measured/optimized quantity | Meaning |
| --- | --- | --- | --- |
| Spatial goal | Same physical target/readiness, such as actual landing on block G or completing the same portal passage | Elapsed absolute ticks T to achieve it | Evidence about the fastest potential route |
| Time goal | Same absolute elapsed state tick h, same route direction/reference and applicable comparison context | Horizontal objective S(h), normally distance/progress | Proxy for which candidate goes farthest; not necessarily fastest |

For candidate c and spatial predicate G, record the earliest physically accepted achievement time found T_c(G), together with feasibility/unknown and search-coverage status. For a final goal, require the actual finite goal contact, not merely an airborne position over the block. Intermediate goals use their declared region/contact/passage requirements; do not invent a landing when none is needed. For an event produced by input t, the corresponding state tick is t+1.

The first valid crossing/contact that satisfies G matters. Do not relabel later samples of an already-completed event as new arrival times. Numerical/branch search may need to test permissible horizons/timings to estimate T_c(G), but that is an evaluator implementation detail, not a directive to generate candidates specifically at different completion times. Unknown earlier times stay unknown; best found arrival is not a minimum-time certificate by default.

For a common absolute time h, solve/evaluate the candidate's existing horizontal objective S_c(h). All compared candidates use the same h, common normalized direction and anchor, and suitable route-stage conditions. Choose comparison points after relevant turnarounds and before another separate decision where possible, following the existing comparison policy. Y determines physical feasibility, not a distance reward. Evaluate multiple predeclared common h values only if useful; do not combine them with a new weighted score in this work.

A clock comparison must not give one candidate more elapsed time because its event ends later. In particular, do not compare S_a(T_a(G)) with S_b(T_b(G)) as though it were the common-time objective comparison. Those are positions at different times. A candidate record needs separate spatial-goal results and fixed-time objective results, not just one pair of arrival time and progress sampled at that arrival.

When a candidate's terminal program ends before h, do not fabricate a held endpoint or zero movement. Its final-goal timing is already meaningful. If an intermediate-goal history needs a later fixed-time evaluation, construct a valid continuation using the existing model; otherwise mark that objective result unavailable/unknown. Lack of that measurement is not evidence that the candidate is poor.

## 3. Record comparison context and validation explicitly

Represent a candidate with stable identity, route and discrete decisions, parent/provenance, constraint/search state, facing guesses and accepted witnesses. Attach separate results:

```text
spatial_results[G] = accepted time found, validation, unknown/coverage information
clock_results[h, direction, context] = objective, validation, unknown information
```

Shared goals and clocks are declared before comparing candidates. Do not allow candidates to select more favorable goal semantics, directions or evaluation times after their results are known. Different spatial targets, route stages or clock conditions are not silently comparable.

Use the existing scalar, finite-scene and route/readiness checks. A selected Y schedule alone does not establish a spatial achievement. Store contact/velocity/method information for preserving different histories and for diagnosis, not as fixed continuation states or an added objective.

Avoid the existing failure where a mandatory progress-only cutoff before terminal landing removes the faster completion before its spatial-goal time can be evaluated. Required final landing is a target to compare, not automatically another independent control opportunity that forces stopping just before it. Identify it from explicit goal semantics/contact ownership, not fixture names.

## 4. Preserve similar candidates; remove only clearly worse ones

Keep candidates competitive in spatial-goal time, competitive in fixed-clock objective, and useful time/progress tradeoffs. A faster arrival and a farther-at-h trajectory are distinct pieces of evidence; do not collapse them into one score or automatically prefer one at an intermediate decision.

Replace strict frontier exclusion with a conservative margin-based heuristic. Compare only known results under matching G/h contexts. A candidate can be marked clearly worse when a comparable alternative is no worse on the available relevant time/progress criteria and better by a meaningful margin on at least one. Preserve small differences and tradeoffs. For example:

- Faster to G but less progress at h: retain both histories.
- Similar time and similar progress: retain both, especially if symbolic/contact histories differ.
- Materially slower to G and materially less progress at the same h: a candidate for heuristic omission.
- Missing/unknown goal or clock measurement: keep unresolved; do not manufacture a bad score.

Use explicit time and objective margins, distinct from floating-point feasibility tolerance. Calibrate and report sensitivity with regression results, not fixture-specific tuning. The initial implementation should favor retaining ambiguous cases. Do not compare a candidate with unrelated best-time and best-distance envelopes and falsely assert that a single peer dominates it.

These comparisons are still heuristics: unscored momentum/contact details or later discrete choices can reverse an apparent ranking. Retain symbolic diversity, saved alternatives and accepted incumbents. Exact duplicate merging must preserve pending search work. Hard-cap evictions are separately labeled resource losses, not evidence of marked inferiority. Keep omitted candidates available for regression evaluation and, subject to explicit storage limits, runtime reopening.

At the actual final goal, earlier verified completion wins under the existing final objective; a later farther completion does not change which is faster. Preserve near alternatives for validation/analysis and use the objective to refine ties as appropriate. Intermediate comparisons remain proxies for eventual route quality.

## 5. Schedule and continue without freezing earlier numerical states

Use spatial-goal timing to prioritize faster potential routes and fixed-clock progress to preserve promising farther-reaching alternatives. Reuse the protected completed-candidate queue and balanced work among parents/unknowns. Do not revert to exhausting all short partial prefixes before any complete candidate can be tested.

For A followed by B, retain A's selected discrete decisions, enumerate B's choices and re-solve all continuous facings from the original state. Keep similar A alternatives. Saved failed/pending branches remain resumable. A later unresolved continuation may justify reopening an earlier decision or enlarging a cluster, but it is not proof of infeasibility or of an incorrect A choice.

A successful but slower route may also expose a bad heuristic commitment, so failure-triggered reopening alone is insufficient validation. Joint/unpruned regression controls must measure these hidden losses. No universal independence claim based on a ten-air-input gap.

## 6. Full regression must continue the additional candidates

The ordinary search may deprioritize/omit candidates that appear clearly worse. Full regression must additionally optimize and simulate their continuations, including the candidates classified as worse. Merely storing them or reporting that the retained routes replay is insufficient evidence that the heuristic works.

For each declared finite regression search domain:

1. Capture the candidate pool and all omission reasons before retention. Include near-ties, tradeoffs and candidates the heuristic would discard. Distinguish proven impossible branches from unknowns and resource omissions.
2. Run the planned heuristic search and record its accepted final results/work.
3. Evaluate the additional candidate continuations from the original state, with the same existing physical model and full facing reoptimization. Check the later discrete alternatives, rather than merely replaying one already-poor facing sequence.
4. Build an unpruned/joint or exhaustive reference over that finite test domain where tractable. Disable the tested retention heuristic in the reference continuations too; otherwise later pruning can conceal the counterexample.
5. Independently replay every accepted witness and compare final completion times and configured objectives. Record which would-be-discarded or merely near candidate ties/improves the best result, how much is lost and which earlier discrete decision needs reopening.
6. Save each false exclusion as a reproducible regression and revise the heuristic generically. Report final-quality losses even if both searches return feasible routes.

Full regression has its own explicit larger evaluation budget so omitted candidates are actually examined. Matched-work comparisons remain useful separately for practical performance; they must not starve the reference and then claim the heuristic preserved optimality. Numerical unknowns or incomplete reference expansion make that audit incomplete, not evidence that the omitted histories cannot win.

On larger maps where the full finite domain cannot be completed, explicitly report generated/evaluated/unevaluated coverage and label any sampled diagnostic as partial. Do not call a capped or sampled reference exhaustive. Best found reference results are not certified global optima outside their proven/fully evaluated domain.

The tests must challenge both the retention rule and the weak-coupling assumption: nearby clusters, separated decisions with strong coupling, different landing/ceiling/step histories, both travel directions, missing measurements, and identical positions with different physical histories. Revalidate existing early/late ceiling and step controls under current physics. Keep the existing six cold controls, including grind11 and turn13, and add held-out geometry.

## 7. Implementation sequence and code reuse

1. **Separate evaluation contracts and records.** Add spatial-goal time and fixed-clock objective results beside existing checkpoints; reuse movement/contact/readiness validation and original-state solving.
2. **Evaluate candidates before misleading retention.** Adapt the current pipeline so existing candidates can obtain the appropriate spatial and/or clock results before a progress-only cutoff erases them. Start with final-goal versus fixed-clock ceiling/step controls, then intermediate shared spatial goals. Generation remains driven by supported discrete candidates, not desired differences in completion time.
3. **Implement conservative retention.** A pure component in `cell_routes/time_progress.py` handles matching contexts, meaningful margins, near candidates, tradeoffs, unknowns and omission diagnostics. Existing fixed-clock band policy remains a regression comparator only.
4. **Integrate continuation and saved alternatives.** Reuse protected ready queues, discrete commitment, global facing reoptimization, repair-state resumption and incumbent preservation. Add bounded reopening with explicit provenance.
5. **Run full additional-candidate regression.** Continue would-be-discarded candidates and compare with unpruned/joint finite-domain references; add every quality counterexample to tests. Also measure total generation-plus-solve cost and active candidate counts.

This is the new search path. There is no opt-in rollout phase or deployment gate in this plan. Historical policies are retained only as experimental baselines where useful. Tests are for correctness and understanding the heuristic, not a separate rollout project.

## Deliverables, limits and reporting

Deliver separate tests for spatial-goal timing, same-clock objective comparisons, the fact that farther does not imply faster, similar-candidate preservation, clearly-worse classification, unknowns, original-state/free-facing continuation, and full reference audits that detect a discarded eventual winner. Keep all accepted witness replays and work diagnostics.

Report accomplishments, remaining limits and next steps after each implementation checkpoint. Important remaining limits are finite candidate coverage, comparator choice, unobserved future effects, reference-solve unknowns and combinatorial continuation cost. We are measuring these assumptions, not claiming exact dominance from time/distance or recreating the movement machinery.

## Implementation checkpoint, 2026-11-01

The runtime now implements separate goal/clock contracts, conservative retention as the default, saved alternatives, original-state continuation and bounded failure reopening. Final landing and intermediate region goals are supported. comparison_audit.py and two_goal_experiment.py implement the additional-candidate continuation/replay reference with separate budgets and explicit incomplete status. Existing movement/constraint builders and IPOPT are reused. This checkpoint preceded the measurements below; implementation coverage does not establish search completeness or optimality.


## Measured completion, 2026-11-02

Implementation and references are complete for the declared test scope. [Results, reproduction commands and archived evidence](experiments/two-goal-comparisons/README.md) cover six existing scenes and two new separated-jump controls. The ceiling, stepping and turn improve by one tick; other completions are preserved. All 305 saved source histories were processed without progress retention in the reference; 371 complete numerical programs were tested and all 435 accepted search/reference witnesses replay. No omitted candidate wins or ties the fastest found time. The 50 incomplete unrestricted references remain labeled incomplete. Eleven incomplete competitive omissions receive additional generation with the incumbent finishing time as horizon; none can produce a complete program within that horizon in the supported catalogue. This closes that saved-pool omission question, not all ungenerated histories or continuous global optimality.

The new path is default. Similar/tradeoff candidates are retained subject to separately labeled hard caps. Spatial time is best-found rather than certified; unknown scores are not poor scores. Failure reopening is bounded; success-quality errors are tested through the separate references. Supplied routes/opportunity ranges, capped generation, lazy constraint discovery, coupling on more adverse maps and repeated continuation cost remain limitations. Measured original-six work increases from 202 to 282 numerical calls and from 682 to 2,111 total prefix expansions. Next work should reduce duplicate symbolic generation and test nearer spatial goals/stronger coupling before inventing a richer weighted score. No IPOPT or movement-model redesign is required by these results.

## Generation versus retention clarification, 2026-11-02

The accuracy expansion has two distinct layers. Generation coverage means reaching supported candidate histories before numerical evaluation; retention coverage means extending evaluated histories into later decisions. Broader generation cannot compensate for later irreversible retention loss, and a wider retained set cannot recover alternatives that were never generated/evaluated. The representative original fixtures allow16 raw choices per type/expansion,512 prefix expansions,384 queued prefixes,20 evaluated programs/window and8 retained histories. Raw-choice counting precedes feasibility/dedup filtering, so the cap is not a promise to evaluate16 useful choices.

Add complete outer-frontier persistence and incremental enumeration state, extending existing protected complete queues and fair-parent scheduling. Existing repairState concerns inner geometry/contact numerical search; parkedCandidates preserves emitted symbolic histories. Neither stores every un-emitted queued prefix or truncated generator tail. Treat generation caps as resumable work allocation, retain separate pending evaluation/parked continuation ledgers, and use extra accuracy budget at both layers. Keep necessary infeasibility screens and the declared route/opportunity domain explicit. This is a proposed orchestration addition, not currently implemented and not a new movement or continuous solver model.

## Refinement: useful candidates before larger counts, 2026-11-02

User rejects arbitrary cap increases and processing useless candidates. This refines the earlier accuracy experiment: keep measured main widths8/12 as working settings while diagnosing coverage and constructing small fresh-root joint references. The eight current controls show no omitted winner; they do not establish a universally sufficient width. API default4 has not been separately calibrated by those width8/12 controls. No new correctness bug is established merely by a deliberate work cap or missing full outer-search persistence.

Post-hoc analysis of saved reference reports finds74 of78 unknown_resource_cap histories exhaust supported continuation generation without producing a complete numerical program. Nine of10 resource_cap histories also do so. This argues for diagnosing unusable prefixes and applying existing necessary reach/timing/compatibility checks earlier, rather than immediately widening the beam. It is not permission to reject numerical unknowns as infeasible or to infer completeness outside the tested domain. Existing exact deduplication and necessary screens should be audited for ordering and usefulness, not reinvented.

Use joint fresh-root reference discrepancies to justify changes. For each missed winner identify whether it was never generated, had an incomplete/poor continuation estimate, lost on the comparison heuristic, lacked distinct decision-history representation, or hit a count cap despite being competitive. Fix or revise the responsible mechanism and preserve the case as a regression. Extra runtime should purchase a justified accuracy improvement. Outer-frontier persistence, adaptive width and larger quotas are available orchestration additions when that evidence warrants them, not the default next implementation solely because more time is acceptable. No runtime settings change in this refinement.

Concrete next diagnostic from the four multi-support unknown-cap exceptions: the existing competitive reference, restricted to the verified14-tick incumbent time, exhausts generation with no possible continuation for each. One is nevertheless feasible at19 ticks; the other three have unresolved15/16/16-tick proposals. Move this supported-catalogue competitive-completion viability check ahead of active retention where it can finish cheaply and without truncation. Use it to avoid spending slots on histories unable to tie the final incumbent, instead of increasing the unknown quota indiscriminately. An unresolved/capped check cannot reject a history, and a slower feasible completion alone does not prove the history cannot finish faster. This remains a proposed integration; the current original search drops these histories on its quota, and only the separate audit supplies the retrospective evidence.
