# Search checkpoint storage

2026-11-10: shared persistence also supports the automatic anchor/event CLI.
The adapter accepts the nested outer event state and explicit `begin_repair`
ownership, including mutable internal-node cache entries. Incremental writes
cover the active program aliases, those declared extra targets and cumulative
calls. Unknown mutation sets require a full checkpoint. See the
[current integration contract](ANCHOR_SEARCH_INTEGRATION.md); the schema5-only
and fixed-clock-only descriptions below record the earlier implementation.
Keep both manifest and database companion when moving any new report.

Implemented 2026-11-03 for `cli.fixed_clock`. Search decisions, queues, constraints, key semantics and DFS state schema5 are unchanged. Storage has its own version1. [Measured probe](../research/last-jump-runs/row-benchmark/checkpoint-results.json), [probe source](../research/last-jump-runs/row-benchmark/checkpoint_probe.py).

## What is removed

Frequent saves no longer format, compress and rewrite the whole report. Historical windows are archived once; repeated programs, long seen-key strings, witnesses and diagnostics share stored content. Full reports still decode to the same ordinary JSON structure, preserving all candidate/pruning evidence and exact doubles. No pending or unknown candidate, accepted variant, unfinished repair cursor or completed request is discarded.

The runtime still keeps audit history in memory. This change removes repeated **stored content and serialization work**, not all in-memory copies. It does not hash or alter the search's seen-key semantics, canonicalize solver rows, enable simple-bound extraction or change corridor/cell behavior.

## Files and commands

An output such as `/tmp/run.json.gz` is now a small JSON manifest with a companion `/tmp/run.json.gz.store.sqlite3`. **Keep/copy both files together, keeping the companion basename.** The SQLite database uses ordinary transactions, compressed immutable objects, content hashes and a store identity. Objects commit before the manifest atomically switches to the new root. An interrupted write leaves the previous committed root readable; a request interrupted before its result is checkpointed may need to run again.

`--resume` takes the manifest path, just as it took the old JSON report path. It accepts both formats. For programmatic inspection:

```python
from parkour_solver.io.checkpoint_store import read_report
report = read_report('/tmp/run.json.gz')
state = report.get('result', report)['searchState']
```

For external tools that require ordinary JSON, export a self-contained copy:

```sh
.venv-guidance/bin/python -m parkour_solver.io.checkpoint_store \
  /tmp/run.json.gz --output /tmp/run-export.json.gz
```

The export intentionally expands historical copies again. Legacy reports remain readable without conversion. `research.legacy_cell_routes.failure_diagnostic.read` and `experiments/dfs-reachability.service/summarize.py` use the shared reader; older one-off scripts that directly call `gzip`/`json` should consume an export or adopt `read_report`.

## Code and callback contract

| File | Responsibility |
| --- | --- |
| [checkpoint_store.py](../src/parkour_solver/io/checkpoint_store.py) | Generic lossless object encoding, sharing, transactional storage, atomic manifest publication, patch roots, final compaction, legacy reader and JSON export. Standard library only. |
| [checkpoint_persistence.py](../src/parkour_solver/io/search_checkpoint.py) | Adapts DFS reports to the store; archives immutable window snapshots once; tracks every alias of the active program and writes repair updates against the frozen dispatch checkpoint. |
| [depth_first.py](../src/parkour_solver/search/dfs.py) | Still publishes full state at transitions. During evaluation, invokes the optional `on_progress.repair_progress(state)` method; ordinary function callbacks still receive full progress as before. |
| [fixed_clock_experiment.py](../src/parkour_solver/cli/fixed_clock.py) | Uses the adapter for progress/final persistence and the shared reader for resume. Window callback prints a summary; it no longer saves a second report copy. |
| [test_checkpoint_store.py](../tests/unit/io/test_checkpoint_store.py) | Storage equality, mutation isolation, interruption/atomicity, legacy compatibility, compaction, pending repair restore, completed-call dedup, DFS continuation, and native callback-by-callback equality/replay. |

During `evaluate_program`, the outer state changes only the active program's `repairState` and `cumulativeCalls`. The adapter persists those fields against the dispatch root, including any repeated references to that same program in records/archived refinement frames. Every update starts from that frozen root; it does not build an ever-growing patch chain. A new transition publishes a full checkpoint. **If evaluation starts mutating other outer state, extend this contract and the callback equality regression.** Snapshot handles never enter the search's runtime state.

The decoder makes independent mutable copies at each reference, matching ordinary JSON's behavior. Literal lists/dictionaries that resemble storage tags are escaped by the encoding. Content hashes are checked while reading. The store rejects a missing/mismatched companion. On final publication it deletes superseded unreachable objects and vacuums the database; current queues and all report audit records remain reachable. Active runs can temporarily retain older roots until final compaction. A single writer owns each output path.

## Measurements and limits

The saved Real11 report is166.63MB indented /68.41MB compact JSON,6.30MB gzipped. The new final manifest plus compacted store is **1.97MB**, containing **10.57MB of unique encoded data**; decoding equals the entire original report. Its windows, solver requests, constraints, results and histories are all retained.

A saved late state with an active candidate is110.52MB in the old pretty format; encoding/compression alone takes4.284s. The new first checkpoint, including archival and disk commit, takes1.193s. Five durable repair saves have a **12.46ms median**, including disk publication. Final report save/compaction takes0.878s. The fixed-request2s probe completes both IPOPT phases with repair saves enabled (three callbacks about12–14ms each), with the same objective as the no-callback control. These are saved-state/fixed-request measurements, not a full new Real11 search or a measured overall search speedup. First/full transition saves and inner repair-state copying still have costs.

Focused validation:74 tests pass with Java/IPOPT enabled and no skips, including the native test that decodes **every repair publication** and compares it to the full live state before independent scene replay. No new Real11 route search was run. Reproduction commands and source hashes are in the benchmark README/results.
