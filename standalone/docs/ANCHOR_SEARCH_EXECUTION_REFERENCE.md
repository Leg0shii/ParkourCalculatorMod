# Anchor search: combinations, pruning and IPOPT dispatch

Avoidance follow-up: [geometry-driven phase covers](design/AVOIDANCE_PASSAGES.md)
replace full-horizon permutations and local repair/passage caps, carrying tightened
bounds into downstream generation. Constraint policy8/state3 and repair assembly10
are current. The measured source-era tables below retain their original settings;
[replacement evidence](../research/geometry-passages/README.md) records the new run.

Runtime described: `perf/early-passage-screen` at `7e4d85d4`; measured tables keep their source revisions. This is the current execution reference
for physical required-anchor search. The [detailed implementation record](design/HARD_JUMP_TICK_PRUNING_AND_REACHABILITY_PLAN.md)
contains the design/caller migration; [measured evidence](../research/hard-jump-comparisons/README.md)
contains source hashes, fixtures and validation. Historical supplied-corridor,
virtual-finish and common-clock experiments retain their own policies.

## What constitutes a candidate

A **program** fixes input counts, requested jumps, scalar vertical/contact history,
physical choices and owned constraint rows. Its horizontal facings remain continuous
variables. A **proposal** appends a movement choice. A **constraint variant** chooses
concrete obstacle-side/contact/tick rows for that program. One program can produce
several variants and numerical calls. Neither is the same unit as a spatial route.

A frame spans source anchor S to next anchor E; start/finish supports are implicit
anchors. All choices within that span are coupled. Optional supports remain choices
until a validated one-transfer jump permanently latches S→E as hard. Hard status
and the ordinary/turnaround ordering mode are independent. There is no separate
three-way classifier for time, hard and momentum routes.

The catalogue below describes combinations the implementation can propose, subject
to the declared fixture/domain and current search state. It does not claim exhaustive
service before wall/call/catalogue caps. IPOPT never chooses these discrete methods.

## Combinations we generate and when they reach the solver

| Combination / candidate | What we construct or preserve | Filtering / ordering before numerical work | Given to IPOPT? |
| --- | --- | --- | --- |
| `run(r) → jump → E` | Ground holds compose one input at a time; the launch/landing clock comes from scalar physics. Different run placements remain different histories. | Initial-jump domain, resource caps, required order, physical screens, current hard policy, strict optimistic finish bound; at a hard arrival, protected peer comparison. | An eligible selected anchor arrival is evaluated. Raw hold construction does not independently request an NLP. |
| `jump → S → jump → E` | A source setup hop followed by one transfer. Setup impulses remain in the span's J count. | Same tests; more J or fewer R/A can protect the later arrival from peer deferral. | Selected completed setup hop gets a node feasibility query at cadence1; eligible arrival gets its anchor query. |
| `S → S → S → E`, including runs before/between hops | Multiple current-owner setup hops, before or after hard latching; bounded by existing caps. | Never banned merely for having multiple span jumps. A late optimistic full finish can still exclude it. | Selected meaningful nodes and eligible anchor arrivals, not every generated sibling. |
| `S → C → E` through a new optional support | Optional acyclic support contacts within an unlatched span. Required anchors still stay ordered. | Owner/order/reachability checks. After S→E latches hard, an intermediate support is outside that transfer domain. | Only surviving selected nodes/arrivals. A latch-excluded topology does not get a recovery NLP. |
| `S → air → E` or run-off/continuation | Normal free movement from the actual scalar boundary; ordinary grounded approaches consider jump and nonjump outcomes, airborne ones do not invent grounded impulses. | Scalar landing clock, supports and reachability. A completed hard transfer must have exactly one transfer jump, so pure run-off cannot satisfy it. | Eligible selected meaningful landing/contact or anchor query; no per-air-input NLP. |
| Named stepping: normal step, slab boost, corner cancel, blip/up/corner variants | Existing case builders fix the contact outcome and corresponding physical rows; enabled cases depend on scene/generator settings. | Cheap height/owner checks, shared approach reachability, then the common joint outcome before side/corner fan-out where applicable. Unknown named probe heights retain coverage. | Surviving selected cases can reach numerical evaluation in generic/unlatched movement or permitted source preparation. Completed stepped transfers cannot establish/satisfy a hard latch. |
| Lower/higher blip pair with 8 wall choices or 4 corner choices | First build one shared scalar outcome per case and test its jointly required contacts/rows. | If the conjunction is ruled out, build no side variants for that case. B3@28 rejects both common outcomes before the old8+4 fan-out. | No worker for a rejected common outcome. Surviving variants still require fixed-model screening/selection. |
| Ceiling clip or launch grind | Existing ceiling family when enabled; fixed contact/case history. | Shared eligibility and scene checks. A possible/unknown shortening ceiling disables the ordinary-flight contribution for that transfer. | Eligible selected choices can reach a worker. The maintained Real11 case enables normal, stepping and ceiling movement. |
| Go left/right around an obstacle; different phase timings | `constraints/encounters.py` prepares one phase cover per box encounter and landing segment; `ConstraintVariants` conditions later domains on earlier rows. Upfront generation and collision repair share this builder. | Scoped relevance removes absent unwanted contacts; canonicalization merges identical effective models; new rows get new reachability checks. | A surviving concrete variant may produce a numerical call. No discrete side/corner variable is left for IPOPT to select. |
| Compound choice arriving at E before an unused tail ends | Derive the actual owned E prefix, with only its committed rows/events and its own T/J/R/A. | Check owned prefix separately. Rejection of an unused extension is scoped to the extension. | Valid owned arrival remains eligible; an impossible tail does not force its rejection. Legacy fixed-checkpoint controls retain their full-first checks. |
| `S → C → S`, or revisiting a departed owner | Not generated in the current physical-anchor domain. Same-owner patches are tracked by physical owner. | Departed-owner filter plus emitted-program audit. Repeated required anchors are also unsupported. | No. Current-owner `S→S` hops are different and remain legal. |
| Arbitrary intentional wall-hit combinations | Deferred feature; named cases can still carry their required finite contact. | No new intentional-hit generator in this feature. | Not an additional search family. |
| First jump at9/10/11 rather than0/8 | The maintained Real11 case has no whitelist; archived controls retain `[0,8]`. | Shared physical screening, hard-anchor peer deferral and strict finish bounds choose work. Explicit `initialJumpInputs` remains available for controlled experiments. | In the unrestricted control, first0/8 were queried;9–12 arrivals were recoverably deferred before numerical work. See [audit](DECISIONS_AND_PLANS.md). |

Ordinary landing durations are not independent air-tick permutations. The model
finds the descending support-height crossing, then choices compose that history.
Different source preparations change the launch time/momentum; they do not shorten
a no-stepping/no-shortening-ceiling flight between fixed heights.

## The dispatch pipeline: what “pruned” actually means

| Boundary | Decision and evidence | Fate of the work | IPOPT worker at this boundary? |
| --- | --- | --- | --- |
| Domain / ownership | Enabled movement types, requested caps, initial-jump domain, required order, departed owners, learned hard structure. | Outside-domain proposals are excluded. Caps are limits, not physical-impossibility proofs. | No. |
| Early producer prerequisite | Shared `InteractionGate` approach and common-case checks. Required conjunction absent means reject that proposal; unwanted contact absent means omit its avoidance alternatives. | Retain unknowns; budget/timeout does not become absence. | No; Java coefficient compilation and interval/optional LP work are analytical preparation. |
| Composition / owned projection | Compose once at stable raw ordinal; identify first actual owned anchor arrival; preserve row/event ownership. | Keep owned model independently of unused-tail evidence. Persist projection flag/cursor across JSON resume. | No. |
| Fixed-model screen | Scalar validity, initial-floor guard, required-event envelopes, propagated reach/travel bounds and optional optimistic LP exclusion. Accumulated obstacle rows are screened before downstream generation and again when more rows are added. | Reject only the checked proposal/model. Changed rows/projections need their own analysis; separate passes do not establish joint feasibility. | No. The optimistic LP is not the nonlinear IPOPT solve. |
| Global finish bound | Compare current `T + max(H,V)` with the one best validated full finish. | Exclude only if strictly greater; equality remains eligible. No queue-wide bound refresh. This overrides local count protection. | No. |
| Local hard-arrival peer comparison | One compatible validated peer must be strictly earlier, with no more-J or lower-cost protection for the candidate. | **Recoverably defer**, retaining exact program/ownership/proof. Open prefixes and unlatched spans are not suppressed by this arrival rule. | No. |
| Queue selection | Shared ordinary/turn priority; inspect saved lifecycle flags; lazily recheck selected work against current peers/incumbent. | Descend via DFS; unselected alternatives remain queued/preserved. Priority alone is not a deferral proof. | No. |
| Selected meaningful internal decision | Default cadence1 since last solved completed movement decision; retain prior contacts/anchors but omit the pending end-anchor predicate. | Evaluate exact node from original state. Reuse saved exact feasible/rejected node result where applicable; unknown work remains saved. | Possibly. Eligible solve preparation can still screen/block before a wrapper call. |
| Required anchor arrival | Query the actual achieved anchor clock, regardless of cadence. | Guided early-stop feasibility and physical validation; validated arrival can learn hard topology and become peer evidence. | Possibly. A valid seed can satisfy early-stop mode without launching a worker. |
| Final physical landing | Optimize configured terminal objective at fixed candidate T; outer search compares actual T first, then terminal objective. | Update the single validated incumbent. There is no fixed64 target and no maximum-Z-at64 task. | Normally for surviving terminal variants: `stopOnFeasible=False`, so a feasible seed does not replace optimization. Caps/screens may prevent a call. |
| Rejected / suspended result | Distinguish exact operational oracle rejection, exhausted repair catalogue, unresolved/budget/error and feasible evidence. A constraint generator exhausted before emitting any numerical branch is also exhausted when no pending/in-flight/unresolved repair remains. | Only the exact solved model receives its rejection. Empty-domain exhaustion backtracks its node without inventing an IPOPT rejection. A failed child never invalidates an unqueried ancestor. | No additional ancestor-probing pass. |
| Feasibility recovery | Definite required-continuation failure plus drained runnable first-class work in its owning context; successful finish alone does not reopen slower work. | Restore one eligible saved alternative with frame-scoped bypass. Skip globally excluded alternatives; reuse matching saved witness when available. Keep all hard/cap/order limits. | Only if restored work needs a query; not automatically on every restoration. |

A **candidate evaluation**, **solver wrapper invocation** and **worker launch** are
three counts. One evaluation can enumerate several fixed variants, be screened,
or reuse work. In early-stop mode a validated feasible yaw seed can return from
the wrapper without IPOPT. Worker feasibility still needs the recorded vertical,
scene and scope checks before anchor/terminal acceptance.

## Tick policy, protections and examples

`T` is the absolute end input/state. J/R/A count only inputs since the current
required anchor: requested grounded impulse J, grounded nonjump R, otherwise A.
A named cancellation with a horizontal jump impulse still counts J. Airborne
jump presses do not become grounded J. Same-source hops do not reset the span.

| Mode | Ordering key | Protection against a strictly faster compatible peer |
| --- | --- | --- |
| Ordinary | `(T, -J, R, stable_id)` | **More J OR fewer R** |
| Turnaround | `(T, -J, A, stable_id)` | **More J OR fewer A** |

Raw extra A ordinarily, or extra R in turnaround, is not protection by itself.
Equal T stays live. Incompatible mode/incoming-frame/scope/milestone/landing-phase
or missing required metadata prevents local deferral. Equal counts do not merge
physical schedules. The peer index stores count representatives; complete actual
programs and physical variants stay in their owning records.

| Example | T/J/R/A | Current outcome and reason |
| --- | --- | --- |
| Initial8: run8, transfer to B2 |17/1/8/8 | Earlier B2 arrival; can learn global B1→B2 hard latch. |
| Initial0: jump0 back to B1@12, jump12 to B2@21 |21/2/0/19 | Remains first-class: more J and fewer R than initial8. One source hop plus one transfer, not two transfer jumps. |
| Ordinary slower peer: baseline20/1/0/19 versus candidate21/1/0/20 |21/1/0/20 | Defer if otherwise compatible and hard: merely spending another air tick is not a momentum advantage under the selected rule. |
| Turn peer20/1/10/9 versus candidate21/1/12/8 |21/1/12/8 | Protect for fewer A. |
| Same turn peer versus candidate21/1/11/9 |21/1/11/9 | Unprotected: extra R alone does not improve J/A; eligible for recoverable deferral. |
| Grounded B3@38 with later hard spans bounded10+15+9 |Elapsed38, remaining≥34 | Finish≥72; exclude against validated67 even with more-J protection. |
| Optimistic finish equals best full finish |Any valid counts | Keep for potentially better terminal Z. |

The pure predicates live only in `search.comparison`. `minimum_runs` retains
legacy matched-family normalization; `time_progress` retains legacy record APIs
and delegates their common-clock safeguards. Those compatibility paths do not
restore clock/progress pruning to active anchor search.

## Flight bounds, guides and turn geometry

**Declared domain assumption:** hard transfers between anchors never involve
stepping. A completed transfer containing stepping cannot learn/satisfy the latch.
Closed or pending source-local preparation is not blanket-rejected. The latch is
solve-global and monotone, even if its certifying branch is later abandoned.

The derived whole-flight clocks on the stored Real11 heights are:

| Transfer | B1→B2 | B2→B3 | B3→B4 | B4→B5 | B5→B7 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Ordinary inputs, including launch |9 |9 |10 |15 |9 |

These come from the existing scalar helper at the active threshold. Only potential
ceiling changes before ordinary arrival can shorten the conditional bound. Retained
or unknown shortening ceilings contribute zero. An applicable global hard latch
is required before using its duration. Remove completed transfers, subtract paid
ordinary airborne launch age, and omit unsupported/noncanonical current-boundary
contributions. No additional route/NLP solve derives this table.

`H` uses full phase-correct landing-state covers and a shared displacement upper
bound, not point-to-point guide distances or one witness's velocity. Pre-contact
landing constraints may precede a horizontal move, so covers enclose that move.
`V` sums applicable remaining conditional flight minima. Use **max(H,V)**, not
H+V; horizontal and vertical motion share inputs. Guide-distance estimates are
diagnostic only. This is conditional pruning within the declared domain, not a
proof that stepping could never help in an arbitrary world.

Backward guide inference starts at the finite terminal objective edge, projects
each successor guide into the previous anchor's exposed landing cover, and uses
the original initial point at the start. At E, the intermediate continuous objective
aims onward toward N via the horizontal direction E→N. Guides are soft: no guide
waypoint equality, guide-based discrete progress pruning, slack score, or raw-cell
walk containment is imposed by current physical automatic search.

Preprocess turns exceeding110° using guide triples S/E/N. The approach-facing
physical edge, or its imaginary diagonal support line, is shifted0.3 blocks toward
the approach. At each decision a point belongs to that half-space or does not;
equality is turnaround. Applicable boundaries are local to their adjacent spans.
Freeze the resulting binary ordering mode for owned alternatives. Later full-history
re-solves may move the earlier point; log flips without silently changing queued
keys or adding a pose constraint. No mixed or uncertainty mode exists.

## Decisions, rationale and present scope

| Decision | Why it was requested | Implemented consequence / limit |
| --- | --- | --- |
| Ordered caller anchors; compare only there/final | Decisions between anchors are coupled; optional intermediate landings should not become independent comparison rubrics. | `AnchorAdapter` owns required milestones. Optional contacts remain internal decisions. With no supplied anchors, there is one open start→finish span. |
| Hard means one transfer, not one jump in the whole span | Chained source hops gain momentum; banning them incorrectly removed the initial0 T64 route. | Global topology latch, last-source-contact boundary, multiple setup hops preserved. |
| No stepping in hard transfer | User explicitly chose this assumption to make useful flight minima tractable. | Hard policy3 excludes completed stepped transfers; ceiling-aware bounds are conditional on that domain. |
| Protect more J or fewer R/A before slower-arrival deferral | A later intermediate arrival can lead to a better final finish. | Initial0 remains first-class after initial8 succeeds; raw extra air/run ticks alone do not protect. |
| Restore slower unprotected work only on actual feasibility fallback | Extra preparations should remain reconstructable without consuming routine improvement search after success. | Lossless saved alternatives, one-at-a-time restoration, scoped bypass and failed-edge cause. |
| One incumbent, lazy strict bound checks | Queue-wide rescans repeated unnecessary work and equality can improve Z. | Check new/selected candidates only; strict greater-than cutoff; no incumbent-triggered queue scan. |
| Shared prerequisite/model preparation | Independent lower/higher tests allowed jointly impossible stepping cases to fan out; identical screens/compiles were repeated. | `InteractionGate` owns preparation/coefficients/analyses; common-case check precedes fan-out; model-changing rows retain separate keys. |
| Guided intermediate feasibility | Early stopping is fast; configured onward objective can guide the witness when a worker is needed. | `objectiveMode=configured`, `stopOnFeasible=True`; valid seed may return without improving progress. No constant-zero default or slack proxy. |
| Normal exact-node queries at cadence1 | Reject a selected impossible action before extending it, with unambiguous ownership. | No raw-hold NLPs or separate ancestor tests. Cadence2/4 remains a future measured tradeoff. |
| Binary spatial turnaround, observable | A single decision needs one clear priority; witness positions can change on later re-solves. | One110° edge+margin classification, frozen key, distances/query/replay-flip records. Preliminary heuristic, not a completeness theorem. |
| No inter-block revisits yet | Cyclic histories enlarge the decision domain and need dedicated repeat-family design. | Departed-owner filter; same-owner hops remain. [Revisit plan](design/REVISIT_DISCOVERY_DEFERRED_PLAN.md) stays deferred. |
| Real physical B7 landing; minT then MAXZ | The old unbounded +Z/fixed64 proxy did not express the intended task. | B7 extends to `[729,0,-1095,730,7,-1093]`; terminal guide `(729.5,7,-1093)` is not a player-center cap. |

## Implementation ownership and persistence

| Owner | Current responsibility |
| --- | --- |
| [`event_search`](../src/parkour_solver/search/events/engine.py), [`anchor_adapter`](../src/parkour_solver/search/anchors/adapter.py) | Physical automatic dispatch, required-anchor frames, immutable stage objectives, continuous anchor DFS under the session budget. |
| [`anchor_policy`](../src/parkour_solver/search/anchors/policy.py) | Owner visits, required order/caps, source preparation versus hard transfer; hard policy3. |
| [`search.comparison`](../src/parkour_solver/search/comparison.py) | Sole intermediate count/key, OR-protection, peer verdict and strict-incumbent predicate owner; comparison policy1. |
| [`depth_first`](../src/parkour_solver/search/dfs.py), [`search.deferrals`](../src/parkour_solver/search/deferrals.py) | Traversal, validated peer index, single `minimumRunExclusions` compatibility table, selected-only checks and recovery. |
| [`movement.generation`](../src/parkour_solver/movement/generation.py), [`search.frontier`](../src/parkour_solver/search/frontier.py) | Shared composition/assessment, owned endpoints, raw ordinals, projected flag, queued nodes and `nodeInFlight`. |
| [`reachability.service`](../src/parkour_solver/reachability/service.py), [`bridge.compiler`](../src/parkour_solver/bridge/compiler.py) | Disposable bounded session cache for preparation, canonical coefficient payloads, exact envelopes/screens and common-case eligibility; proof policy2. |
| [`reachability.intervals`](../src/parkour_solver/reachability/intervals.py), [`reachability.linear`](../src/parkour_solver/reachability/linear.py) | Conservative phase envelopes and optional optimistic LP; named internal probe uncertainty retained. |
| [`transfer_time_bounds`](../src/parkour_solver/search/bounds/flight.py), [`anchor_time_bound`](../src/parkour_solver/search/bounds/remaining.py) | Conditional duration certificates, phase-correct state covers, H/V and paid-input residuals; transfer-bound policy1. |
| [`anchor_guides`](../src/parkour_solver/spatial/anchor_guides.py), [`observability.turns`](../src/parkour_solver/observability/turns.py) | Backward guide plan version2, onward objective, binary boundaries and compact reproducible diagnostics. |
| [`checkpoint_search`](../src/parkour_solver/search/engine.py), [`constraints.variants`](../src/parkour_solver/constraints/variants.py), [`search.repair`](../src/parkour_solver/search/repair.py) | Fixed scoped model preparation, discrete physical alternatives, resumable repair, numerical dispatch and scene acceptance. |
| [`node_feasibility`](../src/parkour_solver/search/feasibility.py), [`checkpoint_persistence`](../src/parkour_solver/io/search_checkpoint.py), [`io.checkpoint_store`](../src/parkour_solver/io/checkpoint_store.py) | Exact-model outcome/cadence, all mutable aliases at dispatch, lossless manifest/SQLite persistence, isolated external decode. |
| [`numerics.solver`](../src/parkour_solver/numerics/solver.py), [`search.ranking`](../src/parkour_solver/search/ranking.py) | Seed check/worker lifecycle/scalar validation; terminal actualT/objective ranking stays distinct from intermediate J/R/A policy. |

Physical automatic continuation is outer schema2 / DFS8 / movement context4 /
repair assembly10. Hard3/search5/comparison1/guide2/proposal1 semantics participate
in the relevant context fingerprints. Matching schema numbers alone do not make
an older checkpoint compatible. Old wrong-policy exact continuations reject;
checkpoint storage format stays version1. Never redirect work to an old checkout
because a source-era handoff contains its absolute path.

## Real11 domain and work measured at `dd738c98`

The [fixture](../research/anchor-feasibility/real11-anchors.json) declares
B2/B3/B4/B5 anchors, normal+stepping generators, horizon200,
64 total/per-jump run cap, 2,048 prefix cap, 32 contact branches, 128 passage
candidates, cadence1, 2s candidate and8s score slices, 1,024 wrapper-call guard.
These caps/domain choices are not proofs that excluded alternatives cannot help. The contact-branch and passage caps and the score slices no longer exist; see [avoidance passages](design/AVOIDANCE_PASSAGES.md).
Normal momentum remains; inertia thresholds are zero. No B6 replacement bounds
have been supplied, so validation uses the stored B6 geometry.

| Updated combined300s span | Consumed composed proposals | Admitted programs | Expanded prefixes |
| --- | ---: | ---: | ---: |
| B1→B2 |22 |13 |10 |
| B2→B3 |26 |14 |9 |
| B3→B4 |19 |11 |7 |
| B4→B5 |15 |12 |5 |
| B5→B7 |12 |7 |4 |
| Total |94 |57 |35 |

These are saved continuation counters, not all iterator `next()` calls, all raw
case descriptors, all feasible routes, or worker calls. Reconstructed generators
can revisit consumed descriptors. The finite run does not exhaust all possible
maps/routes or prove optimality.

| Matched arm | Best T | Wrapper invocations / workers / feasible-seed returns | Expansions | Independent retained-witness replays |
| --- | ---: | ---: | ---: | ---: |
| Baseline combined300s |67 |198 /159 /39 |1,114 |75/75 |
| Updated combined300s |64 |61 /55 /6 |35 |57/57 |
| Baseline initial0-only90s |64 |73 /56 /17 |427 |34/34 |
| Updated initial0-only90s |64 |26 /22 /4 |7 |23/23 |

Updated final Z is−1093.2381026602416 in both arms. Combined records22 bound
exclusions (18 with positive flight contribution),8 recoverable deferrals,
0 restores and18 common-sequence exclusions. Its61 invoked fixed-model fingerprints
are distinct; this is not61 duplicate solves of one model. `numerics.model.fingerprint`
normalizes away seeds, service budgets and stopping/objective-mode flags; the
invocation ledger records the stopping flag separately. This is model identity,
not a byte-for-byte hash of the full request including its seed. Peer/bound predicates
cost milliseconds in the profile. Remaining wall cost is mostly numerical
preparation/refinement, digesting and retained-state copying. The simultaneous
feature changes do not isolate each change's individual causal contribution.

At `dd738c98`, 600 native-enabled tests passed without skips; all131 archived T67/T64 prefix
bounds and80 post-change retained-witness scene replays pass. Timings include
instrumentation; the early combined arm overlapped a read-only audit. See the
[evidence README](../research/hard-jump-comparisons/README.md) for
exact walls, scopes and limitations. Neither these runs nor an operational IPOPT
rejection prove global minimum time or actual-world executable replay. No further
implementation is pending from this plan; revisits, intentional wall hits,
cadence/turn tuning and remaining bookkeeping optimization require separate work.

The maintained Real11 case now omits the initial `[0,8]` filter. The [unrestricted control](../research/initial-domain-selection/README.md) verifies natural first0/8 query selection and recoverable9–12 deferral. Earlier measured arms above retain their recorded restricted domains. Domain identity changes, while solver policy versions do not.
