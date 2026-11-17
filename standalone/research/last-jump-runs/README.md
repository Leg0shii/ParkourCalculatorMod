# Minimum runs before the latest jump

Runtime `db211b2b` implements the user's local heuristic: once a jump interaction pattern has a validated feasible representative, skip copies with more ordinary ground runs immediately before that latest jump. This is based on local preparation, not cumulative run count. Cold Real11's B4@40→jump43 has3 runs here even though the whole prefix has15.

No continuous physics, final objective, facing freedom or corridor rule changes. Continuation-time bounds remain deferred; the existing elapsed-input cutoff is unchanged. See the [answers document](../virtual-finish/PRUNING_AUDIT.md), especially sections6–8, for the local-count decision. At the focused-test boundary the user deferred Real11/corridor work; the later authorized retry is linked below. Corridor/cell improvements remain with the other agent.

## Generic behavior

`cell_routes.minimum_runs.latest_jump` finds the latest effective grounded jump and walks backward through its immediately preceding ordinary ground hold. A per-frame key preserves prior decisions, support identity, the in-air scalar schedule, contacts/cases and hard constraints relative to takeoff. It removes only the repeated ordinary hold. Consequently different initial histories, blocks, obstacle sides, stepping/ceiling choices and final physical contacts stay separate. Equal-run choices and earlier smaller-run candidates remain eligible.

Only a scene-validated witness establishes a representative. An unknown does not. The feasible representative and exclusions persist in DFS state; excluded discrete programs and their representative IDs are stored for false-pruning audits. The rule runs before numerical dispatch and in the symbolic source's filter/screen. Unknown/interacting preparations, sneak holds and ladders retain coverage rather than being guessed equivalent. This is heuristic exclusion, not certified global dominance.

`minimum_run_pruning=False` is available for an explicitly unpruned regression control; it is not a rollout mode. The pruning policy belongs to the resume fingerprint: archived pre-policy states and states using a different policy cannot silently resume as identical contexts. Current schema is still5, with policy-version1 context and per-frame `minimumRuns` plus `minimumRunExclusions` state.

## Validation and evidence

Focused tests verify Real11 cold7 against10–12 using local3 versus4/8/9 runs, initial0 call4 against9–15 using local0 versus1–8, and protection of distinct histories and terminal contacts. Generic tests verify obstacle-side separation, unknown preservation, interacting/unclassified preparation, equal/smaller run counts, exact resume and a control that dispatches the skipped timings. No completion-time reasoning is needed for these tests.

**All52 focused regressions passed**, with no skips, in16.852s using the native Java compiler and IPOPT environment: [test log](evidence/focused-tests.log), [summary](evidence/focused-summary.json). The focused modules are `test_minimum_runs`, `test_depth_first`, `test_orchestrator_continuation`, `test_fixed_clock_goal` and `test_terminal_goals`. This includes archived Real11 program comparisons, not a new Real11 solve.

Reproduce from the physical repository root with the native environment configured as in the [runbook](../../../docs/RUNBOOK.md):

```sh
.venv-guidance/bin/python -m unittest \
  standalone.autonomy.tests.test_minimum_runs \
  standalone.autonomy.tests.test_depth_first \
  standalone.autonomy.tests.test_orchestrator_continuation \
  standalone.autonomy.tests.test_fixed_clock_goal \
  standalone.autonomy.tests.test_terminal_goals -v
```

An earlier already-started benchmark completed before the stop instruction; its artifacts are retained locally under ignored `standalone/autonomy/build/deferred-local-run-benchmark-db211b2b/` and are outside this focused validation record. No Real11 process remains running. Original evidence in `virtual-finish/evidence` remains unchanged.

## Generic threshold verification

The production rule contains no Real11 run-count threshold, block IDs or coordinates. For each matching interaction pattern it learns `k` from validated feasible solves, counts each candidate's local preparation as `jumpInput - groundState`, and excludes only counts greater than the learned `k`. A subsequently feasible smaller count replaces `k`.

The generic regression now exercises learned minima0,1,2,5,9, checks equality/smaller alternatives and verifies dynamic tightening. All52 focused tests pass again with no skips in17.118s: [log](evidence/generic-focused-tests.log), [summary](evidence/generic-focused-summary.json). No production change or Real11 solve was needed for this confirmation. The3/4 values in the explanation are only the archived Real11 example.

## Subsequent authorized retry

After pushing the [handoff](../../../docs/CORRIDOR_AGENT_HANDOFF.md), a fresh combined Real11 run was authorized and completed: [result and pruning accounting](REAL11_RETRY.md), [all12 requests](REAL11_RETRY_CALLS.md). It preserves calls8/9, excludes nine extra-run candidates before dispatch, finds67 / Z≈−1093.82313, and passes all13 replay checks. The initial0 root remains pending.
