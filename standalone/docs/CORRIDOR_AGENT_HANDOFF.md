# Handoff: changes since the standalone AGENTS consolidation

Package/layout update: production is now installed as `parkour_solver`; see [current layout](LAYOUT.md), [runbook](RUNBOOK.md) and [refactor validation](STANDALONE_REFACTOR_IMPLEMENTATION.md). Source-era checkout paths and measurements below do not override those current commands. Solver policy and compatible checkpoint schemas are unchanged.

Baseline: `93652150` (initial standalone AGENTS/documentation consolidation). Runtime and focused-regression changes below are implemented through `c1e71cb3` on **`research/ipopt-cell-routes`**, pushed to the user's fork `origin` (`arvind-murty/ParkourCalculatorMod`). The surrounding documentation commit adds this handoff; it does not change runtime. If an old AGENTS snapshot still calls DFS unimplemented, that status is superseded by the changes below.

The user assigned corridor/cell-decomposition improvements to the other agent. Integrate these solver/orchestration changes with that work; preserve the other agent's checkout, branch and unfinished edits. This handoff is not an instruction to reset or switch its worktree. Our physical checkout is `/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes`; use your own checkout paths.

## What changed after AGENTS.md

| Change | Commits | Relevant sources and integration contract |
| --- | --- | --- |
| Goal-aware DFS replaces global checkpoint-window draining | `cdf46afc` | `cell_routes/depth_first.py` is new; `checkpoint_search.find_checkpoint_route` delegates to it. Compare candidates at a common spatial goal, follow the chosen child deeply, retain siblings/unknowns/refinements and backtrack. Absolute clock windows are optional observations, not a barrier. |
| Early shared interaction eligibility | `cdf46afc` | New `interactions.py`; open normal/stepping/ceiling builders query original-state compiled reach envelopes before generating interaction rows. Proven impossible means no interaction-derived choices, rows or checks; unknown retains coverage. Full proposals are screened before projection. |
| Effective-model dedup and constraint ownership | `cdf46afc` | `constraint_variants.py`, `block_contacts.py`, `movement_composition.py`, `movement_frontier.py`. Equivalent partial obstacle products dedup before expensive refinement. Generated rows have owner completion states; removing an uncommitted future event must also remove its approach rows. Promoting a shorter child must not reintroduce them. |
| Scoped terminal gates use the same eligible subset | `3ebd9407` | `checkpoint_search.py`, `cell_routes/local.py`. Stage checks cannot demand a corridor gate already excluded by the modeled-horizon reach query. This is a compatibility fix, not a redesign of corridor semantics. |
| Step/grind reach-envelope correction | `498c054c` | `reachability.py`. Realized scalar Y endpoints omit named collision probes; preserve unknown Y and intended unclipped X/Z coverage on those inputs. Existing case/scene checks decide validity. Tightening envelopes from realized endpoints reintroduces a known false exclusion. |
| Exact request/branch logging | `072a1111` | `block_contacts.py`, `search.py`. Saved diagnostics include fixed numerical requests/problem hashes, solve/scope identities and branch validation results. Distinguish worker feasibility, corridor acceptance, scene replay and final program result. |
| Generic virtual landing-height finish | `71c9bb7e` | New `terminal_goals.py`; changes in `movement_generation`, `movement_frontier`, `movement_search`, `comparison_goals`, `depth_first`, `support_order`, `fixed_clock_experiment`. The finish tick is discovered, not fixed64; see precise semantics below. |
| Minimum preparation runs for the latest jump | `db211b2b` | New `cell_routes/minimum_runs.py`, DFS wiring and focused tests. Per-pattern feasible threshold is learned dynamically; it is neither total run count nor a hardcoded3/4 rule. |
| Audits and generic regressions | `bda44d98`, `ffb35b84`, `b88cf490`, `2fa80945`, `c1e71cb3` | Current docs, source-specific evidence, exact request ledgers and focused tests. Do not replace the current status with historical pending-plan wording. |

The compiler bridge also recovers after timed-out requests on a subsequent operation, so post-deadline independent replay is possible. It does not reopen an explicitly closed bridge.

## Current solver boundary and DFS state

The orchestrator fixes every discrete input/event/hard constraint before IPOPT. Reuse existing builders. Every solve starts from the original position and stored horizontal velocity; all prior facings remain variables. Normal momentum is retained; **inertia thresholds are zero**, not velocity carry.

A promoted child carries selected decisions and hard constraints, plus an optional facing seed. No optimized intermediate position/velocity is frozen. Frames hold goal/parent identity, symbolic generation cursor, ready/scored candidates, accepted constraint variants, inner repair progress and unfinished refinements. Schema is5 (`ordered_goal_dfs`); the latest local-run policy additionally enters the context fingerprint. Old pre-policy schema5 states therefore do not silently resume as the same context.

Existing protected-state/time-progress helper functions remain available, but DFS does not perform their old global peer-retention pass. Its new heuristic exclusion is the latest-jump extra-run rule; do not describe every historical pruning proposal as active.

## Local run-count rule

For a candidate, find the latest effective grounded jump at input `j` and the start `g` of its immediately preceding ordinary ground hold. Its local run count is **`j-g`**. For fixed prior decisions and a matching last-jump interaction pattern, learn the least validated feasible count `k` and exclude copies with count greater than `k`.

The key keeps the prior prefix, support identity, relative in-air scalar schedule, contacts/cases, hard constraints and final contact choice. It normalizes away only repeated ordinary preparation on the same support. Equal-run choices, earlier smaller-run candidates, earlier unknowns and distinct interaction patterns remain eligible. Unknown/interacting preparation, sneak and ladder cases are conservatively retained.

Representatives are per-frame and persist as `minimumRuns`; saved `minimumRunExclusions` include the discrete program, local counts and representative ID. `minimum_run_pruning=False` supports a fresh unpruned regression control; changing policy intentionally changes resume identity. This is a heuristic, not a proof of global dominance. The generic test exercises minima0,1,2,5,9 and dynamic tightening.

In the archived example, B4 arrival40 followed by jump43 means **three** local runs, even though the prefix has15 total. That is only regression data. Distinct initial0/initial8 histories must both remain eligible.

Additional continuation-time pruning is **deferred** by the user. Do not infer a global nine-input remaining-time bound from one ordinary suffix. The existing elapsed-input cutoff is unchanged.

## Corrected Real11 finish

Use the seven user-supplied physical boxes and the selected B2→B3→B4→B5 route. The virtual terminal goal is the descending **height7 clock**, with player-center **X<729.7 and Z>=−1095.3**, without a new lower X or upper Z goal bound. Minimize achieved finish tick first, then maximize Z at that tick. `maxInputs=200` is a search guard;64 is not hardcoded.

Free-air overshoot and actual finite B7 collision are separate valid final-contact choices. The free-air model can end below Y7 and airborne; the reported nominal crossing height is not an artificial floor. A physical B7 contact model has its actual finite collision constraints. Both must pass the original supplied scene. No enlarged B7 block or fictitious side walls are added. The runner derives a conservative finite horizontal collision-query envelope from the horizon without adding terrain.

Canonical current fixture: `research/virtual-finish/fixture.json`. The CLI module is still named `cell_routes.fixed_clock_experiment`, but it now also handles the virtual goal. Old state64 fixtures/reports remain historical controls, not evidence for earliest landing under the new task.

## Corridor integration: keep the current calls visible

The user expects currently known corridor-related calls to persist until the corridor implementation is corrected. **Do not suppress or special-case calls8/9 to make the counts look smaller.** Call numbers are specific to each saved ledger; compare their actual fixed models and validation histories.

In the archived combined cold ledger, calls8/9 share inputs and finish67 but use free overshoot versus physical B7 contact. In the archived initial0 ledger,5→6 and7→8 are two terminal models, each with a corridor repair. Same input schedule does not by itself establish an exact duplicate numerical problem. The user's latest direction is to deal with unnecessary corridor-induced distinctions/repairs in the corridor work, while keeping their costs visible in this retry.

For B3, current code constructs a reference plane X=738.5 and classifies its crossing by lateral Z. Expected Z lies inside approximately[−1101.3,−1099.7]. The rejected initial0 paths cross that plane during input27's X phase at Z≈−1101.507 or−1101.429, then move inside the footprint by state29 and satisfy landing rows for state30. This approach restriction is stronger than the landing constraint. Token `[3,-1,-1]` means gate3, crossing toward decreasing X, on the negative-Z side; `[3,-1,0]` means the same crossing inside the lateral interval. Current repair adds a Z[27] band. Y is not used to exempt that airborne crossing. Full explanation and exact coordinates: [pruning audit sections3/7](../research/virtual-finish/PRUNING_AUDIT.md).

Integration-sensitive files: `cell_routes/local.py`, `checkpoint_search.py`, `routes.py`, `geometry.py` and consumers of scoped gate signatures. Preserve reach eligibility, matching modeled scope and row ownership when changing geometry. Keep independent physical replay distinct from corridor acceptance; a physically acceptable alternative can violate a guide signature.

## Validation and reproduction

Latest focused acceptance: **52 tests pass, no skips**, native compiler/IPOPT enabled; expanded generic threshold run17.118s. [Rule/tests](../research/last-jump-runs/README.md), [focused log](../research/last-jump-runs/evidence/generic-focused-tests.log). Earlier wider checks are source-specific; the known ladder-inclusive baseline is not a claim of a fully green ladder suite.

From your physical repo root, set the environment documented in [RUNBOOK](RUNBOOK.md), then:

```sh
.venv-guidance/bin/python -m unittest \
  tests.unit.search.test_minimum_runs \
  tests.unit.search.test_depth_first \
  tests.integration.test_orchestrator_continuation \
  tests.unit.search.test_fixed_clock_goal \
  tests.unit.search.test_terminal_goals -v
```

Do not rebuild shared Java helpers while another numerical run is using their classpath. No Minecraft launch or mod Gradle test run is needed for ordinary standalone work.

## Evidence and current next action

The pre-local-run virtual-goal combined cold run found67 / Z≈−1093.82313; an independent initial0-only cold diagnostic found64 / Z≈−1093.26755. Those are best-found observations, not global certificates or proof that combined DFS visited initial0. [Raw reports/ledgers](../research/virtual-finish/README.md).

The user now authorizes another **fresh combined Real11 retry after this handoff is pushed**, with the current local-run policy enabled and corridor behavior unchanged. New evidence must use new paths; do not overwrite the archived reports, inject a known schedule/yaw or silently raise candidate caps. The retry will be documented separately after completion. Corridor/cell improvements remain the other agent's work; time bounds, inertia-threshold work and discrete decision transfer remain deferred.

## Retry now completed

The fresh retry at source `61355976` is complete: [result/limitations](../research/last-jump-runs/REAL11_RETRY.md) and [all12 calls](../research/last-jump-runs/REAL11_RETRY_CALLS.md). Calls8/9 remain; nine simple extra-run copies are excluded. Best is67 / Z≈−1093.82313,13/13 replay checks pass, and initial0 remains pending. An extra equal-run variant adds a B6 avoidance row (not a corridor row), so inspect exact constraints before labelling all remaining work as corridor duplication. Runtime and corridor code are unchanged from the pushed handoff source.

## Subsequent row-count finding

[Hard-row audit](../research/last-jump-runs/HARD_ROWS_AUDIT.md): the initial8 request already had278 rows before local-run pruning; the~100-row example was initial0. The278 entries contain164 exact duplicate constraints (114 distinct;91 strongest simple X/Z bounds). Ordinary footprint bounds are duplicated by contact compilation and retained compiled walls. This is a separate final-assembly cleanup opportunity, not evidence that the corridor retry introduced hundreds of new decisions. No runtime fix is included in that audit.

## Measured independent overhead

[Row-cost/callback benchmark](../research/last-jump-runs/row-benchmark/README.md) finds small absolute solver gains from row cleanup and a larger synchronous whole-state reporting cost. A late progress payload is109MB as indented JSON and costs≈4s to encode/compress; the callback consumes the solver's wall-time budget. This is separate from corridor correctness and remains an unimplemented performance improvement. Preserve in-flight/repair/incumbent durability when addressing it.


## New persistence integration (2026-11-03)

[Checkpoint storage](CHECKPOINT_STORAGE.md) is implemented after the row/count audits. Fixed-clock outputs now consist of the named JSON(.gz) **manifest and its `.store.sqlite3` companion**. Resume accepts new or legacy files; inspect with `parkour_solver.io.checkpoint_store.read_report`, or export ordinary JSON using the documented CLI. The DFS summary reader is updated. Candidate lists, constraints, unknown queues, repaired variants, call logs and pruning history are preserved; no corridor/cell/model/pruning behavior changes.

`depth_first.score.progress` can call an optional `on_progress.repair_progress(state)` adapter method. Its contract is that evaluation changes only the active program's repairState and cumulativeCalls in the outer state; all active-program aliases are patched. Ordinary callback functions retain the previous full-state interface. If your work adds outer-state mutation during evaluation, extend the adapter and native publication-equality test.74 focused tests pass/no skips. Saved full report equals its decoded store exactly; about12ms repair saves and1.97MB final storage. No new Real11 route solve.


## IPOPT numerical cleanup now implemented (2026-11-03)

[Constraint cleanup](IPOPT_CONSTRAINT_REDUCTION.md) is worker-local: exact duplicate rows and weaker absolute X/Z inequalities are omitted numerically, CasADi extraction is enabled, and the monitor checks original assembled expressions at every iterate/final point. Original orchestrator requests, ownership, physical hashes, resume contexts and every-row parent validation are intact. Phase logs add `wallReduction` with original-index provenance, `simpleBoundExtraction`, and `constraintRowsBeforeExtraction` (after reduction, before extraction). No corridor/cell changes.63 IPOPT and109 src/parkour_solver tests pass/no skips; saved fixed-request23-run A/B preserves feasibility/objective and reduces278→91 wall rows. No fresh full Real11 route search.


## Intermediate objective policy (2026-11-04)

[Pure feasibility mode](IPOPT_FEASIBILITY_MODE.md) is implemented: intermediate DFS evaluations disable polishing and the worker uses constant zero with first validated feasible stopping. Final evaluations retain their objective. All original row/vertical/scene/corridor checks remain. Intermediate position/velocity/objective are unoptimized witness measurements; `objectiveMode` is logged in results/records/diagnostics.64 IPOPT and162 src/parkour_solver tests pass/no skips.

DFS schema5 now includes `intermediateObjective: constant_zero_feasibility_v1` in the context fingerprint, so older DFS contexts require explicit reconstruction or their source-era code; do not call it exact continuation. Repair scope includes mode and mode changes clear the constraint-generation cursor before resetting repair work. Same-policy checkpoints and storage format1 keep their existing semantics. No corridor/cell work was changed, and no full new Real11 route solve was run.


## Real11 numerical benchmark (2026-11-04)

[Repeated intermediate comparison](../research/last-jump-runs/row-benchmark/REAL11_INTERMEDIATE_BENCHMARK.md) covers all nine archived nonterminal requests:135 fresh runs and81 warm probes, identical numerical feasible/unknown classification in all modes. Numerical success does not settle corridor/scene acceptance: the67-input B5 request remains only a fixed-NLP benchmark here, with its archived whole-program result unknown. No corridor/cell/search code changed. Early stopping matters more than zeroing the objective; constant-objective witnesses lose0.21–0.47 blocks of measured Z on numerical-success cases versus optimization. Keep the current requested policy; see the report before using unoptimized p/v as a comparison score.
