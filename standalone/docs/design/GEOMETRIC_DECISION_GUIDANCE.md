# Use the existing cell route as the first decision guide

Latest discussion, 2026-11-01: [provisional route-aware initial solves](PROVISIONAL_ROUTE_GUIDANCE.md). User emphasizes the value of numerical feedback from earlier decisions to later ones. The recommendation is to preserve important route/obstacle geometry using reasonable existing provisional choices, then refine their timing/method after the solve—not enumerate every downstream alternative first. Fixed decisions within a call remain revisable across calls. This is a proposed middle ground, not a shipped or user-approved policy.

Latest user ruling and fixes, 2026-11-01: [orchestrator contract](cell_routes/ORCHESTRATOR_CONTRACT.md), [coverage/state fixes and results](experiments/orchestrator-fixes/README.md). Inputs, geometry and timing constraints are selected by the orchestrator before IPOPT; do not put discrete routing choices inside the continuous model. Adjacent-cell corridor repair and loss of unresolved repair state are fixed in isolated research source e3d1087b. The cold turn now yields a replayed 13-tick route under unchanged limits; previous successful warm objectives are preserved. All 92 new saved witnesses replay. The pilot still has lazy geometry discovery, so complete branch compilation remains orchestration work. Historical unknown-turn and solver-redesign suggestions below are superseded by this ruling/results.

Latest authorized runtime fixes, 2026-11-01: [implementation and validation](experiments/failure-fixes/README.md). The standalone runtime now preserves fully physically accepted incumbents separately from repair proposals, honors fallback starts in zero-threshold carry mode, and separates completed proposals from partial-prefix eviction with balanced discovery. The grind window search now accepts 11 ticks; all 28 known warm cases survive with no observed successful-baseline objective loss. Cold turn discovery remains unknown under current limits. The event-ready time/progress frontier and richer scores remain deferred. Normal momentum, zero thresholds in the current experiments, original initial state and free earlier facings are preserved. Historical no-runtime-change statements below apply to their dated research turns.

Latest root-cause findings: [generic queue, incumbent and seed failures](experiments/failure-rootcause/RESULTS.md). These mechanisms precede a new score: constructed complete proposals can be evicted before numerical verification; a better inner-model candidate can hide a physically valid seed from outer acceptance; and no-threshold mode can skip the fallback that escapes an angularly degenerate cold start. The controlled matrices include additional scenes and counterexamples to naive fixes. No default solver, physical model or scoring change was shipped by this investigation.

Latest direction, 2026-11-01: [time-first progress frontiers](TIME_PROGRESS_FRONTIER.md). The user identifies the earlier required landing behind the terminal ceiling/step examples. Retain useful event-ready prefixes per absolute tick count and process shorter completions first, preserving later labels that gain progress over the cumulative earlier envelope. This takes priority over inventing a richer horizontal score. Final ranking was already ticks-first; early prefix retention did not yet account for completion time. A new turn probe also shows a valid seed being lost during optimization/repair. The frontier and incumbent fixes are not implemented yet; the experimental window/band code below remains the measured baseline.

Latest implementation, 2026-11-01: [joint decision windows](experiments/decision-windows/README.md) implement comparison before the next separate opportunity, retention of similarly scoring alternatives, and joint treatment of nearby coupled decisions. Clusters and shared checkpoints are computed from supplied opportunity time ranges/dependencies; objective bands and per-parent work quotas are implemented. Automatic opportunity timing discovery remains separate. Position-only scoring is retained; clear-tick position/velocity/contact features are diagnostic data for a later score experiment.

Latest measurements: [joint-window results](experiments/decision-windows/RESULTS.md), numerical source `d9cffafc`, 54 cold searches plus 54 controlled prefix/continuation trials and 402 independently passing saved witness replays. The faster-completing ceiling/step histories fall farther behind in horizontal score as h moves later. Slower histories also lead in forward velocity at every recorded common clear tick. Preserve different next-contact timing/flight-phase histories as the next retention hypothesis; adding a positive forward-velocity bonus or averaging these stored progress samples would still favor the slower examples. No richer score has been selected or implemented. A dense grind cluster and a turn with no accepted partial scores expose distinct generation/repair/readiness limits.

Original proposal, 2026-10-31: use the cell-route polyline as the geometric proxy. Mixed-input and provisional full-route solves remain optional later improvements, not prerequisites.

## Existing representation already supplies a guide

`cell_routes/routes.py` constructs `Corridor.points` from the original start, successive finite portal midpoints and a point in the goal landing region. It also stores ordered cell-stage paths, route signatures and corridor projections. The generator builds consecutive line segments through convex cells. A prescribed point route can supply the same kind of geometric guide.

Thus a cheap untimed polyline already exists. It supplies the broad direction and upcoming turns that motivated cell decomposition. It does not need to satisfy gravity, acceleration or an exact tick schedule to serve as an objective guide. Its numerical midpoint choices are references; the solver must retain freedom to cross elsewhere in the portal or land elsewhere in the allowed goal region.

Connecting endpoints means connecting the successive cell/portal anchors within the selected corridor. A single global start-goal chord could erase the intended obstacle side or cross a blocked region. Optional shortcutting must preserve corridor membership and ordered progress. Straight segments through the existing points are sufficient for the first baseline; no new path-smoothing algorithm is required.

## Decision clusters and the latest useful comparison

Treat a group of nearby decisions as one local decision cluster. Enumerate compatible joint choices for the cluster, including event timing and method choices, regenerate their actual physical profiles, and compare those joint alternatives after their effects have developed. Do not greedily commit the first member of a tightly coupled group and only afterward consider its neighbors.

Temporal proximity is a first grouping signal. Overlapping possible timing ranges, a shared turn/contact sequence, or one choice changing the next choice's availability/timing are additional reasons to expand the cluster. The grouping threshold and dependence detector need experiments; no fixed tick distance is selected here. Existing single-event physical generators do not by themselves establish independent feasibility of every joint combination.

For each cluster, extend its alternatives through the available decision-free continuation to the latest common absolute state h immediately before the next separate unresolved decision. Preserve one h, one route stage and one horizontal direction for the entire sibling batch. Do not give a candidate a longer horizon merely because its next decision happens later. If the window contains a turnaround, complete the turn before comparing, using the outgoing segment's common direction.

Indexing: the stored position p[q] is the state before input q. Thus, if the next decision controls input q, comparing immediately before that input means scoring p[q], the state after input q-1. It does not mean scoring p[q-1] and accidentally stopping one input earlier. Time-indexed collision/contact effects must also be placed using their actual movement phase.

The next decision's time can differ among siblings and may remain a range until solved. The horizon cannot depend on each sibling's optimized endpoint. A conservative first policy is to stop before the earliest possible onset of the next unresolved decision across the batch. If that leaves no common useful post-cluster/post-turn comparison point, or the next decision is causally coupled, include that decision in the cluster and choose a later common boundary. Estimated opportunity times are proposals, not proofs that a free-facing optimizer cannot interact sooner; acceptance must check the actual event order and detect an unresolved interaction entering the scoring prefix. Do not silently fix future event times merely to make the comparison convenient.

## Retain an objective band

For a shared comparison, normalize the objective to maximization and let S_i = d dot p_i[h]. Retain valid alternatives satisfying:

```text
S_best - S_i <= epsilon
```

With unit horizontal d, epsilon is a horizontal progress gap in blocks. Use objective differences, not a percentage of the absolute coordinate-based score, which changes with the map's origin. No numerical epsilon is selected yet. Calibrate it jointly with the later horizon, using independently continued siblings and their best-found full results.

The current short-window counterexamples illustrate this dependency: the useful early-jump ceiling alternative is 0.210311 blocks behind at state 7; the useful normal-step alternative is 0.382581 blocks behind the best slab-boost score at state 4. A tighter band would drop them at those old horizons. These gaps are diagnostic observations, not proposed default tolerances, necessary gaps at later h, or certified global-optimum losses.

Retain distinct method/timing/contact continuations within the band even if their position scores are nearly equal. Their carried velocity and vertical/contact state can differ. Merge truly equivalent symbolic profiles, not states merely because they occupy the same cell or have similar scores. Numerical unknowns need a separate work reserve; missing a score is not evidence of a large gap.

A hard resource cap is still useful when many alternatives lie in the band. Treat any such omission as a recorded heuristic budget drop, separate from falling outside the band. Balance initial continuation work among retained parents before spending remaining work by score. The new objective band replaces fixed top-k as the intended quality-retention policy; a pool cap remains an independent work limit, not a claim that only k candidates are worth keeping.

Position-only band filtering remains a heuristic, not a dominance proof. Later comparison reduces one source of ranking error but does not make the score a complete continuation value. No velocity term, height objective, mixed initializer or new bound is introduced by this policy.

## Minimum local evaluator

User refinement: use a comparison checkpoint between decisions/portals, after the relevant turnaround, where the outgoing horizontal objective is unambiguous. This follows the user's commit-window comparison intuition. All siblings use the same absolute tick and the same signed horizontal objective. The checkpoint is an evaluation time/route stage, not a hard spatial endpoint or committed facing.

For a pending decision, use the cell-route polyline to identify the outgoing straight segment beyond the turn and before the next interaction. Determine its common horizontal forward direction d. Do not choose each candidate's direction after seeing its optimized result, or score alternatives on opposite sides of a turnaround using an inappropriate direction.

For tick 4 versus tick 5 alternatives, pick a common later absolute state h. Solve each candidate's original-state prefix through h with its actual discrete movement/contact constraints, all earlier facings variable, and objective:

```text
maximize d dot p[h]
```

The guide supplies where to make progress. For an axis-aligned outgoing segment this can simply be maximize X, minimize X, maximize Z or minimize Z; use a directional projection for other orientations. The existing continuous physical model determines how far each candidate can get while satisfying its selected input/contact effects. This needs neither a mixed future-contact model nor a solved full-route reference.

Y is excluded from the comparison objective. Candidates need not share a height or vertical phase at the checkpoint. Their actual Y/contact/ground dynamics still determine feasibility and horizontal movement coefficients; no equality of Y, scalar 3D path distance or nominal time-parameterized Y trace should be imposed merely for comparison.

The standalone objective already supports an arbitrary horizontal direction through `customYaw`; both `standalone/ipopt/model.py` and `worker.py` use the corresponding signed X/Z projection. This is enough for a direction-only prototype. Adding a velocity term is a separate change, not an existing combined objective silently assumed available.

Use the full selected prefix from the original initial position and velocity. A prior solved position/velocity at the decision is a seed, never a fixed endpoint. The evaluated candidate includes real step/ceiling effects; the geometric guide itself need not choose a method or encode those dynamics.

Different method durations are compared at that same absolute h, rather than their separate endpoints or separate offsets after completion. Choose h so the comparison represents the intended outgoing segment for the batch. If a checkpoint lies inside a turnaround or is too early for the alternatives, adjust it for the whole batch. If another unresolved interaction must be crossed before a useful checkpoint, include the needed local decisions in the window or select another valid common checkpoint. Do not silently give candidates different comparison times.

## Small extensions only if measurements require them

Position-only progress at the shared post-turn tick is the agreed first baseline. Velocity weighting is deferred unless measured errors justify it. A possible later ablation is:

```text
d dot (p[h] + ell*v[h])
```

Here ell is a short lookahead measured in ticks, not a claimed exact suffix value. A turn-aware guide point or next-segment direction may help before corners. Do not introduce several weights, terminal models or a dynamics initializer before establishing whether the simplest score fails.

Use the ordered active route segment and recorded portal progress. Global nearest-point projection can jump ahead at a self-intersection or on a revisited cell. A polyline progress score should include accumulated segment distance and preserve its route stage. A fixed tangent score is only locally meaningful and must not be compared blindly across unrelated stages.

The geometric Y path is not a prescribed flight trajectory. Use Y/contact conditions from the actual candidate and the corridor/opportunity representation. For horizontal facing guidance, use the horizontal direction toward a useful later route point. A vertical-only guide segment must not produce an undefined direction or reward height simply for being higher; use a subsequent horizontal direction or a relevant local contact/progress criterion.

## What can fail, and why it is still the right baseline

The path does not predict useful braking before a turn, a ceiling shortcut's earlier landing, the value of optional support contact, or a pair of coupled method changes. A short horizon may miss those downstream effects. These are possible ranking errors to measure; they do not establish that a more expensive whole-route initializer is necessary.

Retain a small configurable set of promising alternatives, apply genuine local feasibility checks, and validate complete routes with the actual configured final objective. A lower geometric score is not proof of infeasibility or dominance. The score is not a bound. No inertia thresholds are needed in the decision phase; carry ordinary momentum and defer threshold-pattern handling as already agreed.

## Next experiment and escalation rule

Implemented follow-up: [checkpoint guidance](experiments/checkpoint-guidance/README.md) uses the existing polyline to rank physical partial programs at shared absolute ticks. Checkpoint ticks/segments are supplied explicitly. Position-only scoring, no-threshold normal momentum carry, original-state solves and free earlier facings are implemented. The production default is unchanged. The experiment below is the measurement contract; current results live with the implementation report.

Measured follow-up: [90-run results](experiments/checkpoint-guidance/RESULTS.md). Four-history checkpoint pools find accepted routes on all six scenes, compared with three for complete-program generation under the same fixture caps. Score order and generation order reach the same final results with that pool; score order reduces expansions on stepping and the blockage turn. All 456 saved prefix/final witnesses pass independent replay. Greedy retention exposes a ceiling ranking error: the larger tick-7 score finishes in 13 ticks rather than 12. Retaining alternatives recovers 12. Thus the checkpoint frontier is useful, and the score is useful guidance, but position-only greedy commitment is not reliable enough to discard all siblings.

Finite descendant budgets also make pool width nonmonotone: a one-history grind search finds 11 ticks while the four-history search finds 13. Next targeted tests should balance work per retained parent and use a later common comparison tick on unresolved ceiling/descent cases. The evidence does not establish a need for another mixed initializer or a bound.

Results-review recommendation: retain this architecture provisionally. Prioritize per-parent continuation work allocation, an independently continued sibling-family diagnostic, then common-h lookahead sweeps. Measure top-one/top-two/top-four useful-method retention and completed-route loss, keeping numerical unknowns separate. After understanding ranking quality, reduce easy-route overhead and test automatic checkpoint proposals on unseen routes. The full ordered follow-up is in the results report. This is a proposed next-work order, not a claim that those changes are implemented or that the user selected new implementation work.

2026-11-01 user-selected refinement: test joint decision clusters with the latest common pre-next-decision horizon and objective-band retention. Fair per-parent work allocation and independent sibling continuation remain necessary controls. In addition to top-k diagnostics, measure which useful alternatives survive the band, retained pool size, cap drops and best-found completed-route loss. The runtime implementation now exists for supplied opportunity ranges; clustering-gap and band values remain experimental, and automatic range discovery is unfinished. The user explicitly defers a more sophisticated score until these measurements show which alternatives need retention. Potential future scores may use position/velocity at h and earlier available decision-free ticks; no formula is selected.

Compare current ordering against this direction/progress-guided ordering on the same selected corridors and local decision opportunities. Include passage timing, normal step versus jump/slab boost, ceiling hit versus avoid/grind, optional landing versus overflight, a turn and the known multi-support discovery control. Keep the same generation/work budgets, original-state solves and physical acceptance. Ladder and intentional wall-hit tests remain excluded.

Measure top-k retention of useful methods, first accepted route, complete-proposal discovery, total physical/numerical work, elapsed ticks and final objective. Count the cost of local scoring. Retain generation unknowns and dropped alternatives explicitly. If direction-only ordering works, there is no demonstrated need for another initializer.

If it fails, identify the missing information and test a small targeted addition: more lookahead, outgoing momentum, coupled decisions, or an updated geometric guide. Only then compare provisional physical drafts or a mixed-method reference for that measured deficiency. This supersedes the previous recommendation to build the full provisional-draft initializer next.

## Source context

Repository source checks: `Corridor.points`, the portal midpoint extension and goal-region handling in `cell_routes/routes.py`; ordered-stage propagation and candidate auditing in `cell_routes/local.py`; direction objectives in `standalone/ipopt/model.py` and `worker.py`. Implementation: `cell_routes/checkpoints.py`, `checkpoint_search.py` and `checkpoint_experiment.py`. Tests check common absolute ticks across different heights, correct outgoing turn stage, independent replay and reoptimization of earlier facings after a later constraint.

Geometric path following is an established source of direction guidance; [Coulter's CMU report](https://publications.ri.cmu.edu/implementation-of-the-pure-pursuit-path-tracking-algorithm) is background for that idea. This proposal uses geometric targets to score discrete parkour decisions, not the report's vehicle steering controller or its performance claims.
