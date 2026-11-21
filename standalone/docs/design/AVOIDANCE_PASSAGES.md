# Geometry-driven avoidance passages

The avoidance generator assigns meaningful collision-phase covers within selected
landing segments. Anchor search generates them conflict-driven (below); legacy
collision repair uses the same implementation over every obstacle.
See [measurements and validation](../../research/geometry-passages/README.md) and the
[discussion with concrete B6 examples](../../research/early-passage-screen/B6_QUESTIONS_AND_ANSWERS.md).

## Movement, geometry and the passage domain

Generate landing, stepping and intended ceiling contacts before avoidance. Their
selected Y history and finite contact regions determine what needs avoiding.
An airborne ceiling query does not begin a new landing segment. A later selected
landing can introduce another encounter with the same obstacle.

Each obstacle cluster receives a cover for its potentially interacting phases in
the segment. A cluster (`spatial.surfaces`) is a set of solids whose expanded
boxes overlap or touch, so the player cannot pass between them; Real11's B5, B6
and B7 are one cluster. At each phase the cluster's members active at the
player's height (open Y overlap) form a union of rectangles, and its sides are
every maximal free rectangle around that union: a half-plane such as `Z ≤ c`, or
a corner or slot bounded by two to four planes. Every free sweep lies inside some
side, a face two flush solids share is one side, and a side never runs through a
neighbour (B7's `X+` plane against B6 is not offered on its own). Free pieces
narrower than 0.012 are numerical artifacts and are dropped (see
[decisions](../DECISIONS_AND_PLANS.md)).

A segment's obstacles are built only from the solids in the block table of the
segment's launch block ([obstacle relevance](OBSTACLE_RELEVANCE.md)): one flight from
that block cannot touch any other solid, so their space is free for that segment
(user, 2026-11-19: the obstacle group is a subset of the encounter group). The
segment's clusters are the touching groups of those solids (`basis` on the item),
so dropping an unreachable member can split a cluster. `search.engine.segment_solids`
gives each segment its table solids; a segment after a ladder tick, or longer than
the table's flight (an open terminal or free fall past every top), uses every
scope solid. Clusters
are built with `SceneGeometry.near` candidates instead of all solid pairs.

The domain uses one encounter per cluster per segment, with simple horizontal
side walks connected by fixed vertical clearance or already-owned separating
requirements. Sides of different active sets whose regions nest describe one
place (west of B7 below its top lies inside west of B6 above it), so moving
between them continues the same side rather than revisiting one. Two sides may
follow each other directly only when their regions intersect, unless a
vertical-clear phase lies between them (for one box this is the old opposite-face
rule). An entry can be its exit. No independent encounter-revisit producer or
arbitrary numerical switch-count cap is present. All cluster requirements share
the original motion model.

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

`constraints.encounters` asks existing `block_interaction` about each active
member at potential phases and builds that phase's sides around the members it
cannot rule out (the rest cannot be touched then, so their space is free at that
phase); it builds side requirements only there (one row per side bound and phase
state), and preserves known vertical-clear windows, where no member is active. A
phase where every active member is ruled out is covered by the sides the full
active set already satisfies. Entry and exit faces still use the full active set;
`same` relates the nested regions of different member sets. It does not build full-horizon face permutations or switch labels on
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
Guide preferences, timeouts and undecided relaxation results never establish exclusions.

## Contact regions

A finite contact on a face of several rectangles (a merged ceiling, a top split by a
taller neighbour) is one generator choice with a `regionChoices` entry in its
profile. Inside the DFS the generator first drops every rectangle the shared
`InteractionGate` approach envelope cannot reach at the contact tick
(`contact_combinations.reachable_option`); a choice left with one rectangle has no
entry, and one left with none is not generated. `ConstraintVariants` adds one `contact_region` item per entry inside the
scope, before the cluster items, and walks it first.
Each rectangle is its own family, so the switch climb never mixes them, and a
rectangle is screened jointly with every later item. A repair source refines one
solved branch and adds no region items.

## Conflict-driven obstacles in anchor search

Anchor search (`search/engine.py`) gives `ConstraintVariants` an obstacle set,
initially empty, so contact checks, contact regions and route guards are
enumerated up front but obstacle clusters are not. A decision check or anchor leg
first solves its program without avoidance rows. When an accepted point's scene
replay hits a solid, the repair loop (`search/repair.py` `grow`) adds it to the set
as `[stretch begin, solid]` and restarts the generator over the larger set. The
stretch is the one holding the state the colliding movement ends in, so a movement
into a landing belongs to the stretch that landing starts. That stretch's cluster
around the solid then gets its passages, families and switch ticks. Families and the
switch climb therefore stay joint over every obstacle hit so far. The old generator's
unsolved branches are dropped, since the new one emits them refined, and accepted
points stay. A hit on a settled stretch, or on an obstacle already in the set, has
no repair. Obstacles no solve touches never get items: the optimum without them
bounds every variant, so they can be ignored safely (user, 2026-11-20). Partial
combinations are screened by intervals only, because every branch is solved and the
solve is the relaxation check. The set and the emitted branches persist
(`constraintGeneration.obstacles`, `generatedBranches`). A per-call A/B on the
extended control took 22.6 s against 35.2 s for up-front enumeration, with 208
Clarabel solves against 466 and equal best relaxed objectives
([evidence](../../research/encounter-groups/README.md)).

## Unknown probes and shared collision repair

Named step/grind phases come from both the profile and shared model metadata.
Their internal Y probes remain uncertain. Keep the selected prerequisites and
let the existing named-case scene replay judge them; rejected uncertain repair
work remains unresolved rather than acquiring an absence certificate.

Legacy collision-driven repair (callers without an initial generator, such as the
finalization's re-solve of a fixed branch)
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

## Choosing among alternatives

Each emitted variant carries a `family`, a digest of every chosen item with its
side keys (or the effective key of a non-passage option such as a landing
rectangle), and `switches`, the input of every side change in item order. Variants
of one family differ only in when they switch. In rank mode with `climb_families`
(anchor legs and the last leg of the anchor search) `solve_contact_branches`
solves a family's first arrival, then the held variants one switch input away from
its best point, until neither neighbour improves; a family without a feasible
point visits its next held point in arrival order, and IPOPT unknown counts as
infeasible. Variants never visited are counted as `heldUnsolved`. A family from a
collision-repair source is keyed with that source and climbs separately. Every
family is climbed, the best accepted alternative by `candidate_rank` is kept, and
up to `FALLBACK_LIMIT` (2) others with the same ticks and an objective within
`FALLBACK_GAP` (0.3 blocks) are saved as `fallbackVariants` for the DFS to restore
if a later leg fails. On the relaxed backend these are relaxed points: the
[confirmation walk](../ANCHOR_SEARCH_EXECUTION_REFERENCE.md#numerical-backend-and-finalization)
confirms the kept candidates when the loop ends, and a point it rejected is never
saved as a fallback. The family state and held variants persist in the repair
snapshot (version 3), so a resumed climb solves nothing twice.

Constraint generation uses policy19/state11 and repair avoidancePolicy14 with assembly13. The DFS context
fingerprints the new policy. Old raw generator cursors reject; reproduce an old
run with its original source and inputs. Source-era scene fields
`maxContactBranches`, `maxPassageCandidates`, `maxBlockagesPerObstacle`,
`maxHorizontalBlockagesPerObstacle` and `maxVerticalBlockagesPerObstacle` are
accepted for reading archived scenes and stripped during normalization. Maintained
callers no longer pass deleted repair-limit keywords.

## Side families in search branches

Terms (user, 2026-11-19): a **commit** keeps a decision for the rest of the solve
and is walked back only on infeasibility, through fallbacks (group-end arrivals,
`dfs.commit_arrival`). An **intermediate choice** is a branch decision inside an
[encounter group](ANCHOR_POLICY_DECISIONS.md#o8--encounter-groups) that sibling
branches can still replace; decision checks make intermediate choices.

Inside a group, movement and avoidance are decided together: a feasible decision
check climbs every side family's switch timings at the node objective, keeps the
best point as the branch's intermediate choice for the stretches it settled, and
holds the other choices (user decisions, 2026-11-18 and 2026-11-19).
`ConstraintVariants.branch_choice` defines what an accepted variant chooses:

- an obstacle item whose landing segment is closed (it ends before `through`, or
  at a grounded `through`) chooses its side family `dict(kind='cluster',
  clusterId, begin, end, sides)` (the same side keys the climb's `family` uses);
- a `contact_region` item chooses its exact rows (`kind='region'`, `surface`,
  `offset`); a contact check chooses its exact rows (`kind='contact'`).

Each variant carries `branchChoices` (those choices, canonically sorted) and
`branchKey`. The decision check (`search/dfs.py` node evaluation) computes the
program's settled tick (`encounters.settled_through`: `through` when the prefix
ends grounded, else the start of its last landing segment) and keeps the
witness's exact rows (avoidance, contact-region, contact-check and contact-repair
rows) before it as retained position constraints; rows at or after it, which
belong to the open stretch, are dropped, and so are `regionChoices` before it.
The program records `settledThrough`; `append_choice` passes it to children and
`truncate_program` clamps it. Group-end arrivals already keep every row up to the
arrival and record `settledThrough` = the arrival tick. Every later solve builds
`ConstraintVariants(settled=settledThrough)`, which creates no item for an
obstacle stretch ending at or before the settled tick and no contact-check item
before it, so a settled stretch is never re-decided; scene validation of every
solve still covers it, and a collision there becomes a repair source as before.
The other choices are held, not queued (`search/repair.py` `node_alternatives`):
accepted variants are grouped by `branchKey` (open stretches are re-decided
later, so families that differ only there are one choice), and each group keeps
its best point plus up to `FALLBACK_LIMIT` (2) others with the same ticks and an
objective within `FALLBACK_GAP` (0.3) of it, all ordered by `candidate_rank`
without the winner. Each becomes a solved program built like the winner (its own
settled rows) with its own `decisionNode` and the winner's `decisionParent`; one
that equals an earlier program is dropped. The frontier keeps them per node in its
continuation (`alternatives`, with `decisionParents`) and queues the next one only
when the branch died: no live program (queued, in flight, expanding, or waiting
in the frame for a group-end solve) descends from the node or from an alternative
already restored for it (`frontier.restore_alternatives`, checked before every
take, before the frontier reports exhaustion, and by the DFS once the frame's
source is exhausted). A node with a validated group-end arrival below it
(`succeededNodes`) restores nothing; group-end fallbacks recover from later
failures there. Held alternatives are stored in the node cache entry, so a resume
that re-evaluates the in-flight node does not lose them. A check interrupted mid-climb (cancelled, out of
time or calls) has a best but has not decided the node: it is `suspended`, and the
resume continues the same climb before the node's choice is used.

## Diagnostics and measurements

Generated descriptions carry `clusterId`, `members`, `blockId` (the cluster's
block names joined with `+`), side dicts as `faces` (`key`, `bounds` with
per-bound owners, `region`, and `axis` `X`/`Z`, `XZ` for a corner or `Y` for a
vertical-clear pseudo-side), `phaseSpans` with side keys and `switchPhases`; repair adds
`phaseReferences` for retiming. Integer gates are convenient labels. These new
phase covers are never fed to the authored full-leg passage compiler: actual rows
are in the route constraints. `best.passages`, timeline and descriptive counts
also expose the selected covers. Authored fixed passages retain their existing
compiler/validation contract.

`ConstraintVariants.state.metrics` records actual phase checks/exclusions,
meaningful nodes/children/options, duplicates/dominance and analysis cache work.
The state also lists what those counts summarize: `encounterLog` holds each
prepared encounter's sides, possible, covered and vertical-clear phases and the
sides its reachability screen excluded at each phase; `droppedOptions` holds each duplicate
or dominated cover with its rows; a `screenExclusions` entry for a rejected option
names the item it assigned, the option rows and the passage.
Timings distinguish preparation, generation and conditional analysis; inclusive
times overlap. `avoidanceMetrics` combines completed/pending sources, and legacy
support-search `obstacleSearch` exposes these counters and accepted kinds.
`tools.profiling` records conditional propagation/relaxation screen calls, copies/persistence,
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
[Automatic grouping](PRUNING_IMPROVEMENTS_BACKLOG.md#automatic-grouping-joint-constraint-solving)
means solving coupled constraints together; this generator already does so for
one fixed program.
[Commitment](PRUNING_IMPROVEMENTS_BACKLOG.md#commitment-deferred) remains deferred.
Caller-supplied decision windows are removed. The authored two-tick clustering
policy and its measured limitations remain in the
[historical decision-window evidence](../../research/decision-windows/README.md).
This replacement makes no global optimality or arbitrary-world completeness claim.
