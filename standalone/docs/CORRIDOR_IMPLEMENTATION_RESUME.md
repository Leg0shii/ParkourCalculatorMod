Implementation authorized after compaction. Start with the [generic discovery plan](../autonomy/GENERIC_ROUTE_DISCOVERY_IMPLEMENTATION_PLAN.md) and its implementation ledger. M1 domain groundwork is implemented; automatic entry is not switched yet. Continue M2 onward in `/private/tmp/parkour-real11-initial-run-ticks`, preserving all earlier research and figures. Shared worker/helper dependencies remain read-only.

# Resume the spatial corridor implementation

Latest request (2026-11-03): make the generic route-discovery implementation plan concrete. Read [GENERIC_ROUTE_DISCOVERY_IMPLEMENTATION_PLAN.md](../autonomy/GENERIC_ROUTE_DISCOVERY_IMPLEMENTATION_PLAN.md) first: it contains the proposed records/APIs, raw-geometry backing graph, lazy contact/interaction language, role/phase-scoped pruning, exact/optimistic merging, DFS call-site edits, score/objective ownership, cache/resume policies, milestones and acceptance matrix. The user-authorized subagent's [cited research](../autonomy/GENERIC_ROUTE_DISCOVERY_RESEARCH.md) supplies the rationale. This turn changes planning/navigation only; runtime remains `a28e5b71`. On a later implementation request start at M0/M1, preserve all uncommitted research/figures/docs and do not restart conceptual research or implement the old air-walk contract again.

Updated2026-11-03 after the semantic corridor feature and final source-stable checks. The implementation is complete to the scope below; automatic cold Real11 completion remains a measured performance limit. Read this status before treating the original plan as unimplemented work.

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

All reports are in [automatic-corridors](../autonomy/experiments/automatic-corridors/README.md), under distinct paths; earlier failed/intermediate reports are preserved.

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
"$PKC_IPOPT_PYTHON" -B -m unittest standalone.autonomy.tests.test_semantic_search standalone.autonomy.tests.test_semantic_routes standalone.autonomy.tests.test_route_regions standalone.autonomy.tests.test_route_progress standalone.autonomy.tests.test_route_guards -v
```

See the evidence README for automatic cold/resume and coverage CLI commands. Always choose new output paths. No active experiment sessions remain after feature finalization; do not poll obsolete session IDs from earlier handoffs.
