# Handoff: changes since the standalone AGENTS consolidation

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

Canonical current fixture: `autonomy/experiments/virtual-finish/fixture.json`. The CLI module is still named `cell_routes.fixed_clock_experiment`, but it now also handles the virtual goal. Old state64 fixtures/reports remain historical controls, not evidence for earliest landing under the new task.

## Corridor integration: keep the current calls visible

The user expects currently known corridor-related calls to persist until the corridor implementation is corrected. **Do not suppress or special-case calls8/9 to make the counts look smaller.** Call numbers are specific to each saved ledger; compare their actual fixed models and validation histories.

In the archived combined cold ledger, calls8/9 share inputs and finish67 but use free overshoot versus physical B7 contact. In the archived initial0 ledger,5→6 and7→8 are two terminal models, each with a corridor repair. Same input schedule does not by itself establish an exact duplicate numerical problem. The user's latest direction is to deal with unnecessary corridor-induced distinctions/repairs in the corridor work, while keeping their costs visible in this retry.

For B3, current code constructs a reference plane X=738.5 and classifies its crossing by lateral Z. Expected Z lies inside approximately[−1101.3,−1099.7]. The rejected initial0 paths cross that plane during input27's X phase at Z≈−1101.507 or−1101.429, then move inside the footprint by state29 and satisfy landing rows for state30. This approach restriction is stronger than the landing constraint. Token `[3,-1,-1]` means gate3, crossing toward decreasing X, on the negative-Z side; `[3,-1,0]` means the same crossing inside the lateral interval. Current repair adds a Z[27] band. Y is not used to exempt that airborne crossing. Full explanation and exact coordinates: [pruning audit sections3/7](../autonomy/experiments/virtual-finish/PRUNING_AUDIT.md).

Integration-sensitive files: `cell_routes/local.py`, `checkpoint_search.py`, `routes.py`, `geometry.py` and consumers of scoped gate signatures. Preserve reach eligibility, matching modeled scope and row ownership when changing geometry. Keep independent physical replay distinct from corridor acceptance; a physically acceptable alternative can violate a guide signature.

## Validation and reproduction

Latest focused acceptance: **52 tests pass, no skips**, native compiler/IPOPT enabled; expanded generic threshold run17.118s. [Rule/tests](../autonomy/experiments/last-jump-runs/README.md), [focused log](../autonomy/experiments/last-jump-runs/evidence/generic-focused-tests.log). Earlier wider checks are source-specific; the known ladder-inclusive baseline is not a claim of a fully green ladder suite.

From your physical repo root, set the environment documented in [RUNBOOK](RUNBOOK.md), then:

```sh
.venv-guidance/bin/python -m unittest \
  standalone.autonomy.tests.test_minimum_runs \
  standalone.autonomy.tests.test_depth_first \
  standalone.autonomy.tests.test_orchestrator_continuation \
  standalone.autonomy.tests.test_fixed_clock_goal \
  standalone.autonomy.tests.test_terminal_goals -v
```

Do not rebuild shared Java helpers while another numerical run is using their classpath. No Minecraft launch or mod Gradle test run is needed for ordinary standalone work.

## Evidence and current next action

The pre-local-run virtual-goal combined cold run found67 / Z≈−1093.82313; an independent initial0-only cold diagnostic found64 / Z≈−1093.26755. Those are best-found observations, not global certificates or proof that combined DFS visited initial0. [Raw reports/ledgers](../autonomy/experiments/virtual-finish/README.md).

The user now authorizes another **fresh combined Real11 retry after this handoff is pushed**, with the current local-run policy enabled and corridor behavior unchanged. New evidence must use new paths; do not overwrite the archived reports, inject a known schedule/yaw or silently raise candidate caps. The retry will be documented separately after completion. Corridor/cell improvements remain the other agent's work; time bounds, inertia-threshold work and discrete decision transfer remain deferred.
