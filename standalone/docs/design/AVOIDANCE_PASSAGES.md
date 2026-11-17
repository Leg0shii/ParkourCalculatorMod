# Geometry-driven avoidance passages

The avoidance generator assigns meaningful collision-phase covers within selected
landing segments. Upfront generation and collision repair use one implementation.
See [measurements and validation](../../research/geometry-passages/README.md) and the
[discussion with concrete B6 examples](../../research/early-passage-screen/B6_QUESTIONS_AND_ANSWERS.md).

## Movement, geometry and the passage domain

Generate landing, stepping and intended ceiling contacts before avoidance. Their
selected Y history and finite contact regions determine what needs avoiding.
An airborne ceiling query does not begin a new landing segment. A later selected
landing can introduce another encounter with the same obstacle.

Each solid part receives a cover for its potentially interacting phases in the
segment. Physical-owner metadata groups parts without a switch-count budget;
all applicable part/obstacle requirements share the original motion model. The
initial domain uses one encounter per box per segment, with simple horizontal
face walks connected by fixed vertical clearance or already-owned separating
requirements. An entry can be its exit. No independent encounter-revisit producer
or arbitrary numerical switch-count cap is present.

Infer entry/exit regions from finite support geometry. Ordinary landing overlap
precedes that input's horizontal motion, so its next-state envelope may extend
beyond the support. Keep those two meanings separate. For B6 from B5, geometry
supplies Z− entry; a genuinely ambiguous source can admit both X-first and Z-first.
This metadata adds no position or facing pin. Destination geometry and existing
requirements decide whether another exit face is needed.

A cross has one horizontal face transition; a wide passage has two. Vertical
clearance can produce H→Y+→H, Y+→H, H→Y− or Y−→H, with unnecessary ends trimmed.
Mixed horizontal/vertical and interleaved obstacles use the same representation.
A wholly over/under-clear segment emits no horizontal rows. A fixed Y history can
produce more than the old three transitions; counts are descriptions, not resources.

## Exact phase rows and conditional traversal

`avoidance.phase_states` owns the ordinary Y/X/Z state-coordinate handoffs:

| Sweep | X coordinates | Z coordinates |
| --- | --- | --- |
| Y at input t | t | t |
| X at input t | t and t+1 | t |
| Z at input t | t+1 | t and t+1 |

`constraints.encounters` asks existing `block_interaction` about potential phases,
builds separating-face requirements only there, and preserves known vertical-clear
windows. It does not build full-horizon face permutations or switch labels on
inactive ticks. A known entry remains geometry metadata when its inequalities
are already entailed.

A proved-clear intervening side can disappear from the unresolved choice list.
Preserve it as a bridge: its omission must not reject the following exit face.
Do not confuse such an already-owned bridge with phases covered by the current
run's newly chosen rows, which do not restart the walk at every input. Named
step/grind probe uncertainty is also preserved; endpoint Y is not a probe model.

`ConstraintVariants` operates as follows:

1. Prepare the selected movement, required finite contacts, scope and baseline
   conservative analysis through the existing compiler/reachability service.
2. Select the next unresolved item by prerequisite ownership, earliest possible
   interaction and domain tightness, with deterministic ties. This is traversal
   priority; it does not impose a physical order or declare separated times independent.
3. Select a concrete face cover with actual switch times. Rank by the reference
   path but retain other legitimate sides and timings lazily.
4. Add its canonical rows and call `condition_envelopes`. Use the returned
   tightened bounds before preparing downstream choices. It reuses existing
   interval/travel propagation and optional relaxed-linear exclusion.
5. Backtrack on a proved exclusion. Retain unknowns. A failed timing or local NLP
   attempt is not a universal side incompatibility. A later C can invalidate A+B
   even when every pair passes.
6. Dispatch only a complete joint alternative to the existing numerical solver,
   support/contact checks and shared scene replay. Every solve still starts at
   the original state with earlier facings free.

No unresolved-range propagation is required. For example, B6 gate61's exact rows
are conditioned separately from the saved gate62 sibling. Neither branch borrows
the other's bounds. Analysis/preparation caches are disposable accelerators and
are keyed by the exact base context and effective accumulated requirements.

Canonicalization includes remaining items and continuation capabilities. A row
hash alone cannot merge unfinished face walks. Once an encounter is assigned,
nominal switch counts do not create another branch. Completed stronger covers
can be omitted only when a weaker cover entails them under the same capabilities.
Guide preferences, timeouts and inconclusive LP results never establish exclusions.

## Unknown probes and shared collision repair

Named step/grind phases come from both the profile and shared model metadata.
Their internal Y probes remain uncertain. Keep the selected prerequisites and
let the existing named-case scene replay judge them; rejected uncertain repair
work remains unresolved rather than acquiring an absence certificate.

The automatic path generates avoidance before NLP. Legacy collision-driven repair
uses `ConstraintVariants.for_plan` after an observed mismatch. It builds bounds
with the real compiler and selected vertical schedule for that fixed allocation,
then assigns every applicable box jointly. It uses the same phase builder and
maps rows back to event-relative offsets. There is no second bounded permutation
implementation. Earlier facings and later numerical run allocation remain adjustable;
shared scene validation still judges the result.

## Budgets, continuation and compatibility

Avoidance/repair runs until completion, the session's global time/call limit or
cancellation. There are no local 32-attempt, 128-option or derived timing-queue
limits, periodic slices or branch rotation. Per-request numerical timeouts and
unrelated movement/wall-hit domain settings keep their separate meanings.

The frontier saves a selected option before interruptible analysis, publishes a
complete expansion atomically and retains untried siblings. Repair-source inputs
and cursors survive JSON/checkpoint persistence; ephemeral infinity-containing
envelopes are rebuilt. A saved source counts as pending in outcome classification.
Completed exact requests are not dispatched again unless explicitly retrying
unresolved work under its own contract.

Constraint generation uses policy8/state3 and repair assembly10. The DFS context
fingerprints the new policy. Old raw generator cursors reject; reproduce an old
run with its original source and inputs. Source-era scene fields
`maxContactBranches`, `maxPassageCandidates`, `maxBlockagesPerObstacle`,
`maxHorizontalBlockagesPerObstacle` and `maxVerticalBlockagesPerObstacle` are
accepted for reading archived scenes and stripped during normalization. Maintained
callers no longer pass deleted repair-limit keywords.

## Diagnostics and measurements

Generated descriptions carry `phaseSpans` and `switchPhases`; repair adds
`phaseReferences` for retiming. Integer gates are convenient labels. These new
phase covers are never fed to the authored full-leg passage compiler: actual rows
are in the route constraints. `best.passages`, timeline and descriptive counts
also expose the selected covers. Authored fixed passages retain their existing
compiler/validation contract.

`ConstraintVariants.state.metrics` records actual phase checks/exclusions,
meaningful nodes/children/options, duplicates/dominance and analysis cache work.
Timings distinguish preparation, generation and conditional analysis; inclusive
times overlap. `avoidanceMetrics` combines completed/pending sources, and legacy
support-search `obstacleSearch` exposes these counters and accepted kinds.
`tools.profiling` records conditional propagation/LP calls, copies/persistence,
solver wrappers/workers and instrumentation overhead. Do not exhaust a lazy
catalogue just to count its hypothetical size.

Compare fixed inputs by path coverage, then matched physical scenes, movement
families and global budgets by numerical outcome and independent replay. The
old “wide” fixture permits an edge landing that stays beside the wall; a strict
wide control needs a destination requiring the return face. Interleaving means
overlapping requirements, not a prescribed number of switch markers. Pre-check
success, numerical feasibility, supplied-scene replay and actual-world executable
replay remain different claims.

## Deferred boundary

Current movement cadence1, anchor comparisons and inter-movement retention remain.
Automatic close-in-time grouping/commitment consolidation is
[deferred](PRUNING_IMPROVEMENTS_BACKLOG.md#automatic-close-decision-grouping-and-commitment-2026-11-16).
The supplied-window planner remains live compatibility code until its callers are
migrated; it is not wired into this generator. The remembered authored two-tick
clustering policy and its measured limitations remain in the
[historical decision-window evidence](../../research/decision-windows/README.md).
This replacement makes no global optimality or arbitrary-world completeness claim.
