# Orchestrator fixes and regression results

> Current implementation: [DFS/early-interaction results](../dfs-interactions/README.md). This supersedes earlier pending-status and checkpoint-ordering descriptions below; historical evidence remains unchanged.

> Current next implementation: [DFS and universal early interaction eligibility](../../DFS_INTERACTION_IMPLEMENTATION_PLAN.md), still pending. [Current state](../../../docs/CURRENT_STATE.md) separates the seven implemented workstreams below from their remaining coverage/ordering gaps.

The earlier e3d1087b coverage/repair milestone and its unchanged evidence are preserved in [LEGACY_RESULTS.md](LEGACY_RESULTS.md). The current implementation/evidence below are a separate milestone.

2026-11-02. Implementation on isolated research/ipopt-cell-routes. The seven workstreams in [the consolidated plan](../../ORCHESTRATOR_IMPLEMENTATION_PLAN.md) are wired into the standalone checkpoint orchestrator. This does not establish complete cold discovery or heuristic optimality.

## Behavior

- Each evaluation declares its original state, fixed movement/contact program, geometry/body/coefficient identity, objective, modeled blocks/horizon and preliminary/complete scope. Preliminary guidance checks its declared scope and cannot become a complete incumbent. Complete evaluations supply obstacle alternatives before IPOPT and still require full scene validation.
- Existing normal/stepping/ceiling/contact/avoid builders produce the constraints. IPOPT still receives fixed discrete inputs and constraints, with every facing free from the original state. Momentum is carried normally; inertia thresholds remain zero. No ladder, water or intentional wall-hit search is added.
- Spatial projections come from the declared free-space decomposition, without bounding them by helper waypoints or selected-cell seams. Route signatures remain checked and logged. The broad horizontal projection is not a physical collision model; the shared scene checker remains authoritative for physical acceptance.
- One shared interaction helper handles mandatory contacts, unwanted contact relevance and avoid-branch screening. It combines swept Y/X/Z phase envelopes, position/velocity intervals, norm travel bounds and an optional linear feasibility refinement. The refinement uses a circumscribed16-face acceleration polygon and fixed contact equations. It excludes only numerical LP infeasibility; solver uncertainty, unsupported cases or missing SciPy retain the alternative. This does not change the continuous problem dispatched to IPOPT.
- Constraint alternatives are generated with serializable cursors. Vertically irrelevant phases, unreachable interactions, equivalent rows and implied floor-avoid clauses do not consume fresh numerical calls. Guide trajectories order alternatives but are never hard reachability bounds. Vertical passage options remain available even for a selected lateral route.
- Accepted-witness position and velocity are compared at the same declared absolute tick. Greater forward velocity, greater horizontal speed or close/ahead position protect a compatible-schedule candidate. Different schedules/phases and unavailable metadata remain incomparable. Existing0.25-block and1-tick margins are retained.
- Available work uses `(elapsed inputs, -effective grounded jumps, grounded nonjump inputs, stable ID)`. Active batch/retention sizes yield saved deferred work rather than deleting eligible candidates. Explicit user jump/run limits are enforced; absent limits are not silently replaced by block-search defaults.
- Versioned search checkpoints save outer symbolic generation, active/deferred programs, current numerical variants, unresolved requests, in-flight work and validated incumbents. Completed exact-context requests are cached. `--retry-unknown` deliberately retries unresolved numerical outcomes; ordinary resume continues pending work without automatically spending another budget on every unknown. Old parent-only reports are explicitly reconstructed, not called exact resumes.
- Exact old-problem repair queues are not copied into every longer child program: their full solve identities cannot match a changed horizon. The parent work remains saved and its accepted facing guess can guide the child. Compatible legacy prefix repair machinery remains available to its existing callers.
- A conservative viability screen rejects a descending airborne prefix only when mandatory contacts remain and every possible first floor contact is excluded through the remaining horizon. It considers every supplied support with unconstrained horizontal reach. Grounded/rising, unsupported and potentially recoverable cases remain unknown. Optional terminal flight needs no invented final landing.

## Measured controls

| Control | Result |
| --- | --- |
| Fixed Real11 reference, original recorded schedule/yaw used only as a diagnostic seed | Independent full replay passes at state64, Z=-1093.2675546360433 versus recorded-1093.2675533415547, about1.3e-6 difference. First accepted branch in about1.1s in the separate40s control. |
| Numerical saved-frontier control |2 calls before saving plus8 after restoration; exact compatible resume; zero repeated completed branches; validated incumbent preserved; more alternatives remain pending. |
| B2 relevance on that fixed program | All possible extra B2 contact phases excluded before avoidance enumeration; intended finite landing rows remain. B6 alternatives are already included in complete requests. |
| Historical falling prefixes |43 distinct scalar boundary cases rejected, including Y2.9305/Y3.5797 at28 and Y-8.6062 at44. Required contacts remain, all support tops are at least5, and descent has no possible first floor contact. |
| Separate two-jump map |24-input completion, X=8.622964650232278,18 worker calls, about4.63s. All stored witnesses independently replay. |
| Two-jump continuation audit | All11 saved intermediate/final entries processed to exhausted supported continuation domains; no unknown entries; no better completion than the search incumbent. This map had no heuristic drops, so it does not measure a nontrivial false-pruning rate. |

The reference schedule/yaw is not supplied to cold discovery. Reference recovery demonstrates correct complete model assembly, not discovery of the correct discrete sequence.

Cold Real11 uses the declared B1..B7 geometry, initial0/8 families, generated later normal/stepping choices and unchanged20-candidate/8-active settings. The first300s session uses150 numerical calls and stops at the28-input comparison window with no final64-input incumbent. Both initial families survive state10. The next300s exact continuation adds36 calls, advances past the unfinished0/16 self-hop program to0/17, and still has no final incumbent. Completed work is retained instead of restarting the earlier window. A third300s continuation adds22 calls, bringing the cumulative total to208, and still stops at28. Across both resume boundaries, zero completed branch identities are dispatched again. See evidence/cold-summary.json. These runs overlap some independent tests, so wall times are observations rather than controlled speed comparisons.

This is an unresolved discovery/performance failure, not successful cold recovery. The current engine exhausts the available constraint alternatives in a comparison window before advancing the window. It can spend substantial time enumerating/probing late self-hop and timing histories which the first-event-only viability test cannot certify dead. Position/velocity and schedule guards intentionally do not discard those histories. The LP relaxation also admits false positives, and full route-signature checks still reject some scalar/physical candidates. No arbitrary candidate-limit increase is proposed as the remedy.

## Regression coverage

- Final standalone fast discovery:451 tests,89 skipped, one ladder-composition failure reproduced unchanged on clean3082f8fc. Wider numerical discovery:450 tests/449 pass, with only that same baseline failure, in742.3s. This wider process started before the final old-queue-copy regression was added; the final94-test focused gate covers that addition.
- Focused numerical orchestration contracts:94 tests passed at the final implementation boundary;49 of50 pure contracts pass with one numerical environment skip when the bridge is absent. Includes normal air/run/landing, stepping, ceilings, complete/preliminary scope, selected clip envelopes, velocity/schedule guards, late generation, in-flight restore and completed-result reuse.
- IPOPT model suite:54 tests,9 optional tests skipped, all executed tests pass.
- Randomized feasible-control and selected-clip checks ensure the interval/LP screens contain known trajectories. A coupled position/velocity counterexample exercises a useful exclusion. A vertical-clearance route counterexample prevents lateral signatures from removing necessary Y-face alternatives.
- The independent two-jump audit completes all entries. Wider maps with actual heuristic exclusions still require retained-versus-discarded completion audits; no global optimality claim is made.

No mod Gradle build or game client is required for this standalone work. The known ladder failure is outside this milestone and is recorded rather than hidden or fixed opportunistically.

## Reproduction

From the isolated worktree, set the existing compiler/Python environment:

```sh
export OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1
export PKC_RUN_TICKS_COMPILER="$PWD/standalone/autonomy/build/compiler.json"
export PKC_IPOPT_PYTHON="$PWD/.venv-guidance/bin/python"
export PYTHONPATH="$PWD"
.venv-guidance/bin/python standalone/autonomy/experiments/orchestrator-fixes/regressions.py
.venv-guidance/bin/python -m standalone.autonomy.cell_routes.fixed_clock_experiment --input standalone/autonomy/experiments/linkcraft11-opening/fixtures/clock64-orchestrator.json --output /tmp/orchestrator-cold.json.gz
.venv-guidance/bin/python -m standalone.autonomy.cell_routes.fixed_clock_experiment --input standalone/autonomy/experiments/linkcraft11-opening/fixtures/clock64-orchestrator.json --resume /tmp/orchestrator-cold.json.gz --output /tmp/orchestrator-resumed.json.gz
```

The regression script saves the complete fixed-reference/resume results, exact constraints, outgoing repair state and historical fall proofs. Compressed search/audit reports and logs live under evidence/. Source hashes distinguish the implementation from the earlier corridor-only logging experiment. Earlier prototype checkpoints with other assembly/context versions are not exact continuations of this implementation.

## Remaining work

1. Resolve cold discovery by measuring constraint-variant generation and necessary future reachability. Extend dead-prefix reasoning beyond the first floor only when an optimistic continuation graph covers intermediate supports, steps, ceilings, self jumps and waits. A failure to directly reach the next required block is not sufficient.
2. Reduce provably redundant constraint alternatives and repeated compatible assembly, while retaining genuinely distinct timing/side choices and numerical unknowns. Consider explicit saved downstream/provisional work if exhausting one local program still blocks useful completion; preserve the agreed scheduling semantics and measure the change.
3. Audit corridor-only rejections with independent physical replay before changing that policy. A physically valid other-signature result is not proof that the selected route was completed.
4. Expand full retained/discarded continuation audits to maps with actual pruning and interacting decisions. Keep all false-pruning counterexamples and unresolved outcomes.

Larger candidate caps, inertia-threshold patterns, global objective bounds and mixed-input relaxations remain separate work.


## Complete candidate audit, 2026-11-03

[All candidates and constraint variants](audit/CANDIDATE_AUDIT.md) lists7 main programs,227 handled constraint branches,19 spatial-probe evaluations and the untried frontier. [All208 worker-call slots](audit/WORKER_CALLS.md) reconcile172 main calls plus36 spatial calls. All172 main worker returns were feasible; all62 corridor-rejected traces pass independent physical replay. This corrects the earlier hypothesis that failed main IPOPT feasibility solves explained the stall. The audit records logging gaps and separates pruning proposals from scheduling issues; no search policy was changed.

The [generation follow-up](audit/PRUNING_FOLLOWUP.md) explains the million duplicate hits: inactive gate/face descriptions compile to identical rows and are deduplicated only after obstacle products and refinement. Bounded exact-context probes demonstrate more than100,000 descriptions for only8 distinct B3 row sets. Exact early canonicalization is the recommended first reduction; P06/P07 timing-family dominance remains unproved. No runtime pruning changed.

[Sequential answers about candidate ancestry, ordering and run counts](audit/CANDIDATE_QUESTIONS.md) explain why P02 descendants waited and why P06/P07 used most calls. The question-specific extraction records201 calls for the P01 family versus7 for P02.
