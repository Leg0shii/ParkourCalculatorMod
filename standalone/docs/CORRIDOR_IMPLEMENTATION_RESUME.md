# Resume authorized anchor implementation and completed event baseline

Package/layout update: production is now installed as `parkour_solver`; see [current layout](LAYOUT.md), [runbook](RUNBOOK.md) and [refactor validation](STANDALONE_REFACTOR_IMPLEMENTATION.md). Source-era checkout paths and measurements below do not override those current commands. Solver policy and compatible checkpoint schemas are unchanged.

**Current implementation:** [ANCHOR_IMPLEMENTATION_RESUME.md](ANCHOR_IMPLEMENTATION_RESUME.md) and [new evidence](../research/anchor-feasibility/README.md) supersede the historical checkpoint below. Physical tasks now use schema2/DFS8 anchor/open-span policy.

## Latest request and next plan (2026-11-04)

**Implementation is explicitly authorized.** The user said "go ahead and implement", then requested compaction preparation before resuming. Start with [ANCHOR_IMPLEMENTATION_RESUME.md](ANCHOR_IMPLEMENTATION_RESUME.md), the self-contained current handoff with settled contract, exact checkout/status, first M1 actions, dependencies/commands and evidence checks. Do not request permission again or reopen mixed/uncertain heuristic proposals.

Work remains `/private/tmp/parkour-real11-initial-run-ticks`, branch `research/real11-initial-run-ticks`, baseline `4cf39caa`/algorithm `08b00185`. No runtime tools or source edits began in the interrupted implementation turn; only documentation changes exist and no task processes are running. Preserve uncommitted plans/navigation/deferred design and old evidence. The [controlling plan](design/ANCHOR_FEASIBILITY_IMPLEMENTATION_PLAN.md), [decision doc](design/ANCHOR_POLICY_DECISIONS.md) and [deferred revisit plan](design/REVISIT_DISCOVERY_DEFERRED_PLAN.md) are saved. Begin M1 after compaction, then continue the authorized implementation. The completed/historical baseline sections below are context only.

## Current implementation

Feature commits: `24b787e8` (M1 immutable raw geometry/domain), `5c4e2e36` (M2 scoped interactions and resumable physical preparation), `6bc187fd` (M3 cyclic event language and local guide witnesses), `1d1e320c` (M4/M5 dynamic DFS integration/default automatic cutover). Use `git log` and `git status` for later acceptance fixes.

- No-corridor `find_checkpoint_route` selects `event_goals_v1`; its outer state is `event_route_search` schema1, inner DFS schema7. The domain/graph is stored once. Frame references are compact event/guide IDs.
- Dynamic next-contact/finish goals replace complete support/air itineraries. Raw cells remain a covering substrate and geometric witness source. There are no automatic `RouteGuards`, box/projection/prefix filters, or first-corridor support-order checks.
- Compatible actual prefixes own future goal alternatives before their preferences split. In-flight work, pending next-goal owners, generation/repair cursors, numeric variants, siblings and unknowns persist. Objectives have explicit score contexts. Newly committed child work continues first; stalled owners can yield. No sibling solution transfer.
- Unwanted physical interior absence is screened before face/corner construction. Required contacts and named prerequisites remain owned physical obligations. Constraint generation is state2/context7 with resumable phases, masks and O(n) chain-cost tables. Both supplied DFS modes fingerprint the changed shared policy.
- Optional guide classification is separate from required physical/task validation. Selected physical passage/resource alternatives remain in evidence; raw witness paths are never hard movement constraints. Unknown probe heights and unknown query enclosure remain explicit.
- `discoverySettings` version1 replaces legacy automatic catalogue settings:5000 graph expansions/50 guide materializations per session with residual cursors. Physical task/input/call/time limits retain their meaning. Old automatic settings/states are rejected, not silently migrated. Supplied corridors stay controls.

## Validation and next action

Final runtime source is `08b00185`. Its final native gate passes447 tests with zero failures/errors/skips and sourceUnchanged=true; the earlier62-test focused gate remains source-specific intermediate evidence. The generic flat gap finishes at tick14 and independently replays; finite and free virtual-finish regressions pass. Broad native and stored-Real11 raw/semantic/materialized-guide coverage pass. Final matched cold acceptance is recorded under [event-route-discovery](../research/event-route-discovery/README.md). Read that evidence directory and the plan ledger for final results; do not reuse the old420-test report as coverage of new runtime.

The initial interrupted Real11 diagnostic is **not** acceptance: it had7 calls and accepted B2/B3 stages, exposed loose long-skip scheduling hints, and was stopped deliberately. Ordinary-flight ordering now uses existing per-flight coefficients; it remains only a scheduling hint. The saved-context M0 profile identifies raw-route guard enumeration and copying, not eager constructor cost, as the dominant work in those two contexts.

M6 is complete for this release. Runtime source `08b00185`; the [final evidence](../research/event-route-discovery/README.md) includes the447-test native gate, source-stable coverage/generic controls, matched runs, full-route diagram and acceptance manifest. Automatic: T67 first at16.09s, Z=−1093.939078845141,28 calls/1635 prefixes,24/24 replays; supplied: T67 first at43.79s, Z=−1093.8231290646224,51 calls/511 prefixes,95/95 replays. Actual total session walls301.90s/305.76s for declared300s guards. All runtime hashes agree and80 historical artifact hashes are unchanged. No active experiments remain; do not poll obsolete session IDs or rerun completed gates without a new change/concern. No implementation remains for that completed release. The requested next change is planned above and is not implemented. Baseline limitations/deferred work are listed in the generic plan’s final release outcome. The automatic result satisfies the agreed virtual height7 clock with actual Y≈6.7967/airborne; the supplied control is physically grounded at Y7. No fixed64 requirement or optimality claim. If the cold performance goal is unmet, preserve unknown work and profile rather than widen caps. Keep docs/manifest/report source claims exact.

## Historical a28e5b71 baseline

Everything below records the previous semantic-corridor feature and its original evidence. Its automatic route identity/guard behavior is superseded by the implementation above. Historical measurements/files remain unchanged.

## Checkout and preservation

- Task checkout: `/private/tmp/parkour-real11-initial-run-ticks`, branch `research/real11-initial-run-ticks`, standalone directory beneath it. The tool environment may name the other agent's checkout; set workdir explicitly and confirm physical paths.
- Imported committed handoff through `61355976`, merged as `984b701b`; support-region milestone1 is `f9aa0d96`. Use `git log -1` for the subsequent semantic corridor feature commit. Its runtime is identified exactly by the final evidence's sourceHashes, measured before that commit.
- The other checkout's uncommitted solver work was not imported or edited. During feature implementation no mod/Java changes, helper rebuild, game launch, remote push or delegation were performed; the later research delegation is recorded above. Existing compiled helper and native worker were reused read-only.
- Preserve unrelated edits. Only commit after feature implementation, bundling related docs/tests/fixtures/evidence. No new implementation is authorized merely because an old plan contains future tense.
- Old stash/backups predate this feature; never apply them to the implemented tree. Before-source audit patches in the evidence directory preserve the measured policy changes and pass reverse dry-runs against the final feature source.

## Implemented baseline scope (`a28e5b71`)

The following records the existing feature. The new implementation plan linked above supersedes its automatic route identity/guard contract for future work; the existing spatial and DFS plans retain their source-specific baseline details.

1. `geometry.py` refines finite physical support footprints, retaining raw incidence/provenance (Real11 raw cells33→47).
2. `regions.py` canonicalizes exposed support unions and builds explicit support/air/terminal nodes. Its bounded checked overlapping convex cover removes artificial extended subtraction walls. Existing open-solid subtraction checks every cuboid. It is not a complete maximal-box enumerator.
3. `annotate_normal_proposals` reuses zero-threshold ordinary jump/run-off builders and compiled travel bounds. Last-support flight context survives portals. Missing normal proposals leave other methods unknown, not universally rejected.
4. `routes.py` corridor2 uses ordered support/contact and 3D passage occurrences, whole compatible variants, conditioned local anchors and a resumable bounded frontier. Meaningful revisits remain ordered; setup hops stay local where no new passage occurs.
5. `route_progress.py` audits Y→X→Z phases, finite interfaces and physical contacts; several portals can cross in one input. Variant cursors prevent splicing different paths.
6. `route_guards.py` chooses connected fixed 3D axis-sweep/contact branches through existing `ConstraintVariants` before NLP. Existing physical avoidance and blockage/face-chain caps remain active.
7. `semantic_adapter.py` derives goals and outgoing progress direction via existing helpers. Immutable views distinguish required contact from optional outgoing air; landing then running off within one input is valid. Physical final contact and virtual full-endpoint finish have different horizons.
8. The existing `depth_first.py` is extended, not replaced. Typed routes use schema6, legacy supplied controls schema5. Scored promotion/child ownership is checkpoint-atomic. Typed generation scores the first ready proposal while preserving unconsumed iterator work. Zero prefix quota can drain ready work without adding expansions.
9. `route_queue.py` allocates globally bounded work across routes, continues actual newly committed children and yields stalled peers. Each in-flight DFS owns an immutable variant language; newly discovered variants receive separate owners. Policy fingerprints include Stage2/Commit1/Proposal1/Allocation2 plus MovementGeneration3. Physical scene/domain identity excludes duplicated allocation sizes; changed horizons still reject.
10. No-points/no-corridor `find_checkpoint_route` now uses automatic routing from unordered geometry/task. `InitialJumpDomain` is independent of inferred support order; no generic0/8 restriction. `automatic_experiment.py` provides cold/resume CLI, atomic snapshots and source hashes.

## Settled semantics and preserved handoff protections

Overflight versus landing/run-off are DIFFERENT routes even with equal X/Z. Routes are untimed itineraries; input timings/methods/facings are local. No generic B2→B3→B4→B5 order or authored comparison clocks. Intermediate comparisons use chosen physical supports and the actual finish; portals are untimed constraints/events, not extra inputs or epsilon boxes.

Real11 task: descending HEIGHT7, player-center X<729.7, Z>=−1095.3; minimize achieved tick then maximize Z there. No lower X/upper Z goal bound, fake floor or fixed64. Free overshoot can finish below7 and airborne; finite B7 contact is a separate valid model. Horizon200 is a guard. The stored B6 `[730,0,-1095,731,20,-1094]` lacks the user's reported right extension; exact replacement bounds remain unknown.

Preserve original-state continuous re-solves with all earlier facings free, normal momentum and zero thresholds. Preserve InteractionGate, named probe unknown Y/intended movement, full-proposal screens, owned-row projection, exact requests, local latest-jump minimum-run policy, siblings/refinements/unknowns/suspended work and single execution ownership. No new global objective/time bounds, peer-retention pass, sibling decision transfer, or intentional wall-hit/ladder/water expansion.

## Final verification and evidence

All reports are in [automatic-corridors](../research/automatic-corridors/README.md), under distinct paths; earlier failed/intermediate reports are preserved.

- Final `allocation-context-final-native-2026-11-03.{json,log}`:420 tests pass, zero failures/errors/skips,234.1s, sourceUnchanged=true. Explicit47-module list includes generic mixed ladder controls; dedicated ladder/climbable/vine/movement-entry modules remain outside this scope because of the documented unrelated baseline. This is not an all-suite/actual-world claim.
- Final `coverage-allocation-context-final-2026-11-03.json.gz`: unseeded default50/5000/64/2 enumeration admits archived initial0 T64 witness as route26, independent replay passes. Controls/positions enter only after enumeration. Rectangle SVGs show raw cells and transit/support domains. Native coverage also checks B2 landing/run-off within one input.
- Final `matched-allocation-context-2026-11-03/summary.json`, sourceUnchanged=true: automatic declared300s/actual303.3s,13 calls, no finish,5 unique partial replays all pass. Supplied declared300s/actual311.8s,41 calls,T67,Z−1093.8231290646224,51 replay checks all pass. Both retain unresolved/pending work; neither proves minimum time.
- `matched-generic-allocation-context-2026-11-03/summary.json`: flat-gap T14 both entry paths; intermediate automatic overflight T17 versus authored required-contact T24, all independently replayed, sourceUnchanged=true.
- Earlier `matched-2026-11-03` and `matched-contact-promotion-2026-11-03` preserve before-source policies and expected-red regressions. Their partial outcomes are not final-source claims. Reverse audit patches reconstruct those versions; do not restore them as a new baseline.

## Remaining limits and future investigation

Automatic Real11 reaches intermediate supports but has not found a full finish under the matched budget. Symbolic route-guard plus physical-avoidance combinations are expensive; the B4 fixed model remained unresolved. Profile exact saved cursors/requests before proposing reductions. Never bypass physical caps, suppress fixture-specific calls, widen domains silently, or convert deadlines/local numerical failures into infeasibility proofs. This belongs in a separately scoped follow-up, coordinated with the solver agent's committed fixes.

Named step/grind route certification remains explicitly unknown when shared diagnostics omit internal probes; no Python probe physics was invented. Exact probe instrumentation, exhaustive spatial coverage, actual-world/float-angle replay, intentional wall hits and unknown B6 geometry remain outside the measured feature. Representation coverage is not cold discovery or optimality.

## Commands

From the physical repository root:

```sh
export OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1
export PYTHONPATH="$PWD"
export PKC_IPOPT_PYTHON=/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes/.venv-guidance/bin/python
export PKC_RUN_TICKS_COMPILER=/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes/standalone/autonomy/build/compiler.json
"$PKC_IPOPT_PYTHON" -B -m unittest tests.unit.search.test_semantic_search tests.unit.spatial.test_semantic_routes tests.unit.spatial.test_route_regions tests.unit.spatial.test_route_progress tests.unit.spatial.test_route_guards -v
```

See the evidence README for automatic cold/resume and coverage CLI commands. Always choose new output paths. No active experiment sessions remain after feature finalization; do not poll obsolete session IDs from earlier handoffs.
