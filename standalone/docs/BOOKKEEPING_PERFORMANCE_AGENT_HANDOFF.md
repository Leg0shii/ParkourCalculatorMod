# Agent handoff: measured bookkeeping costs

2026-11-15. User requested a separate agent, branch and worktree for this work.
This is the controlling handoff for **performance-preserving implementation**:
investigate repeated hashing, model preparation and snapshots while preserving
exact resume and ownership behavior. Branch scheduling is separate work; the user has now [deferred its implementation](BRANCH_SCHEDULING_INVESTIGATION.md). This does not pause/cancel the bookkeeping assignment. Do not combine policy changes with bookkeeping optimization.

## 1. Starting point and worktree coordination

Production baseline: `a11b9b18` on `research/ipopt-cell-routes`. A later docs-only
handoff commit adds this file and the baseline profiles. Start the separate branch
from that handoff commit, or from `a11b9b18` with this documentation available.
Read [AGENTS](../AGENTS.md), [development workflow](DEVELOPMENT.md),
[layout](LAYOUT.md), [current state](CURRENT_STATE.md) and the specific source below.
Do not treat historical plans as a new implementation request.

If scheduling is resumed, its likely shared edit area is `search/dfs.py`, branch/frontier scheduling, `search/events/engine.py`,
and scheduling-specific tests and policy identity. This agent owns bookkeeping
inside identity/model-preparation/repair-snapshot/persistence services. Report
necessary changes to a shared file before a broad rewrite. In particular, do not
reshape the DFS frame lifecycle or replace the frontier as a performance cleanup.
A small caller change may be necessary; keep it isolated and easy to review.

Use the new worktree's Python environment and compiler descriptor. **Do not install
another checkout editable into the original session's `.venv-guidance`: that would
retarget its imports while it is running.** Do not overwrite another worktree's
`build/native/compiler.json`, classes or output paths. Read archived evidence
without modifying it. Each run needs a new output directory and its own writer.
The user will start the other agent; this handoff does not spawn one.

## 2. What works now and must remain working

- Maintained Real11 case: [physical-anchors.json](../cases/real11/physical-anchors.json).
  Required B2/B3/B4/B5 anchors; physical extended B7; min landing T then MAXZ.
  No first-jump whitelist. Existing heuristics naturally query first0/8 and
  defer9–12 without numerical calls in the recorded unrestricted control.
- A hard transfer has one jump after the last source contact. Multiple source
  setup hops remain legal. Hard latches are permanent, solve-global and monotone.
  Hard transfers exclude stepping; ceiling uncertainty retains conservative bounds.
- Span counts and all intermediate comparison predicates have one owner:
  `search/comparison.py`. More J OR fewer R normally / fewer A in turnaround
  protects a slower comparable hard arrival. Slower unprotected arrivals are
  recoverably deferred, not physically rejected. No incumbent-triggered queue scan.
- Compare actual owned anchor prefixes, preserving row/event owners and stable
  consumed raw ordinals. An invalid unused tail cannot invalidate a valid arrival.
- Every continuous solve starts at the original state with earlier facings free.
  Intermediate configured guide objective + feasible stop; terminal optimization
  has its own identity. No frozen witness endpoint/velocity/yaw seam constraints.
- Meaningful selected decision queries use cadence1; no raw-hold NLP and no
  separate ancestor probe. A child's failure never rejects an unqueried ancestor.
- Exact external resume, interruption, unknown retention, warm starts, shared
  manifest/SQLite reports and independent witness validation already exist.

See [execution reference](ANCHOR_SEARCH_EXECUTION_REFERENCE.md) for the full contract.
The existing610-test acceptance is scoped: all numerical modules and generic mixed
ladder controls, excluding dedicated ladder/climbable/vine/movement-entry suites.
Later initial-domain validation adds assertions inside an existing test, not a
new numerical model. No production Python changed after the refactor acceptance.

## 3. Baseline evidence and what the timings mean

[Bookkeeping baseline bundle](../research/bookkeeping-baseline/README.md) contains
raw profiles copied byte-for-byte from the completed local runs and an extracted
timing table. Source/fixture/instrumentation provenance links identify the runs.

Unrestricted300s run at source `bbb375cf`: T64, Z−1093.2381026602416,
61 numerical invocations /55 workers /6 feasible seed returns,39 expansions,
55/55 independent retained-witness scene replays. No archived seeds entered search.
`a11b9b18` subsequently promoted the same unrestricted physical/search domain.

| Unrestricted300s instrumentation bucket | Calls | Self seconds |
| --- | ---: | ---: |
| `numerical.evaluate` |53 |145.6983 |
| `identity.digest` |30,804,036 |67.1996 |
| `copy:...spatial.checkpoints:retain_prefix:92` |449 |29.8097 |
| `copy:...search.repair:snapshot:101` |699 |17.2735 |
| `copy:...search.repair:snapshot:97` |699 |12.1325 |
| `solver.invoke` |61 |9.9012 |
| `copy:...search.repair:snapshot:102` |699 |4.2347 |

The separate initial0-only90s post-refactor control found T64 with26 invocations /
22 workers,7 expansions and22/22 replays. It reports23.2208 self seconds for
10,514,980 instrumented digest calls and50.5678 evaluation self seconds.

These are measured **instrumentation categories**, not a guaranteed recoverable
speedup. Self times subtract wrapped children; inclusive times overlap. Unwrapped
work remains in its parent. Wrapping tens of millions of calls has overhead;
verify the digest call count and its call sites before attributing the cost.
`numerical.evaluate` includes work not broken down by existing hooks. The cold
profile does not have durable shared-store repair callbacks installed: its live
callback records progress summaries and it writes the shared store after search.
Measure durable CLI callbacks separately; do not call this entire cost disk IO.
The earlier [combined-feature profile](../research/hard-jump-comparisons/README.md)
corroborates the broad hotspots but is a different recorded source/run.

## 4. Relevant owners and likely questions

| File | Inspect / improve without changing behavior |
| --- | --- |
| `search/identity.py` | `digest`, `motion_record`, `solve_scope`, `solve_identity`, `validation_prefix`; repeated canonical serialization and preparation keys. |
| `movement/program.py` | `program_physics_key`, `program_search_key`, `compile_program`; avoid repeated sorting/serialization of immutable content without changing exclusions/ownership semantics. |
| `reachability/service.py` | `InteractionGate.prepare/analyze_model/analyze/condition`, `interaction_context_key`, exact proof cache; key construction can still cost on a cache hit. Existing cache is bounded512 and shared across internal service slices. |
| `search/engine.py` | `evaluate_program`: scope, schedule/vertical, coefficient analysis, model/reference replay, solve identity, variants and final validation. Measure repeated work by exact physical/context key. |
| `constraints/variants.py` | Repeated canonical row sets/options, constraint-generation cursor and timing records. Preserve deterministic enumeration and partial-product state. |
| `search/repair.py`, `search/repair_state.py` | `snapshot`, `publish`, `save_branch`, compatibility context, branch keys, completed/pending/unresolved/in-flight branches; avoid repeated copying of unchanged accepted results/log prefixes. |
| `spatial/checkpoints.py` | `retain_prefix` copies, owned projection and retained hard rows. Sharing must not let sibling programs mutate each other. |
| `io/search_checkpoint.py` | Dispatch-root archival, `identity_paths`, all active-program/node-entry aliases, repair-only patch callbacks and full transition checkpoints. |
| `io/checkpoint_store.py` | Content-addressed archival, frozen handles, transaction/manifest publication, independent mutable decode, patch roots and compaction. Existing small repair patches should not regress to full-tree writes. |
| `tools/profiling/runtime.py`, `tools/profiling/run.py` | Scoped hooks and cold harness. Add targeted measurements here or in a named research driver; never leave profiler hooks or fixture paths inside production. |

Candidate questions, not preapproved implementations:

- Which callers generate the millions of digests? How many exact inputs repeat,
  how large are they, and how much is row-key hashing versus full program/context?
- Can immutable row/model/program fragments be canonicalized once and composed
  into the *same* existing identities? Do not unify physical and search keys.
- Which preparation stages repeat for identical content, and which intentionally
  differ in rows, scope, objective, phase, node-versus-anchor or validation intent?
- Can snapshot construction copy only changed mutable portions while retaining
  frozen, append-only data or store handles at the persistence boundary?
- How much time/allocated memory is spent preparing a snapshot when no callback
  consumes it, and how much when an ordinary function versus durable writer does?

## 5. Cache and identity invariants

1. Preserve canonical identity outputs for unchanged semantics. No rounding,
   coordinate normalization, removed ownership fields or constant-ID shortcuts.
   A pure cache/copy optimization should not need a physical/search policy bump.
2. Physical keys intentionally exclude selected descriptive metadata, whereas
   search keys retain constraint owners, committed horizon, interactions, scope
   and sometimes repair state. `dfs.candidate_key` intentionally excludes repair
   state. Preserve those different contracts rather than replacing them wholesale.
3. Solve identity includes original initial state, full modeled geometry, input
   policy, physical program, objective, validation scope and coefficients; its
   caller also supplies solve intent, node query and assembly context. Different
   `stopOnFeasible`, rank/oracle behavior or validation horizon cannot share an
   exact repair result merely because their coefficients match.
4. Reachability proofs are scoped to role, windows, Y/X/Z phases, rows, contact
   context and named-probe uncertainty. A conditioned row set cannot poison the
   unconditioned analysis. Absence of an unwanted contact and impossibility of a
   required contact have different consumers.
5. Do not cache mutable objects by `id()` across mutations/saves. Freeze an
   explicit boundary or use a proven revision/content contract. No stale cache
   after append, projection, constraint repair or scene/scope change. A timeout
   during construction must not commit a partial result or consume a raw ordinal.
6. Caller mutations must not alter cached canonical values or another branch.
   Dispose/rebuild performance caches on external resume; they are not required
   semantic checkpoint state. Do not serialize heavyweight caches into every node.
7. Do not cache ordering/deferral verdicts as physical facts. Incumbent, latch and
   peer revisions change independently of an unchanged model. Keep selected-only
   bound checks and the single authoritative comparison predicates.

## 6. Snapshot, resume and ownership invariants

- A published snapshot represents its value at publication. Retaining it across
  later mutations must not change the old snapshot. Replacing deepcopy with a
  shallow copy or returning the live repair dictionary is not sufficient.
- Live aliases of the active program and its meaningful-node entry must receive
  the same repair publication. On external decode, independent mutable copies
  must match ordinary JSON behavior. Alias loss must not break owned projections.
- Retain exact queue ordering/cursors, selected `inFlight`/`nodeInFlight`, all
  pending/unknown work, accepted variants, attempt/contact logs and cumulative
  numerical accounting. Never improve timing by deleting evidence or work.
- Completed exact requests must not run again after an interruption/resume.
  Interrupted work must remain recoverable. Timeout/error/unknown are not a
  completed infeasibility result. Reusing an accepted result still validates its
  current context and witness through the existing path.
- Durable repair updates patch the frozen dispatch root, every recorded active
  alias/extra target and cumulative calls. A new transition makes a new full
  checkpoint. If extra mutable fields change, extend the callback contract and
  its full-state-equality test; do not silently omit them from patches.
- Store database contents commit before root publication. Interrupted publication
  leaves the prior root readable. Manifest/SQLite companions stay paired. Keep
  reference escaping, store identity checks, old plain-JSON reads and final
  reachability-based compaction. Do not make patch chains grow per callback.
- Stable ordinals and ownership survive JSON serialization, not just in-memory
  Python identity. Keep original-state free-yaw re-solves and correct owned-tail
  constraint removal. Do not introduce seam equalities as a caching shortcut.

## 7. Concrete work sequence

1. Capture unchanged baseline in the new environment; confirm current keys,
   replay, resume and source/fixture hashes before edits. Use new paths.
2. Add sampled/per-call-site digest and preparation instrumentation, payload-size
   and allocation/copy measurements. Avoid dumping every giant input or wrapping
   every scalar operation. Measure profiling overhead with a matched unwrapped run.
3. Build fixed-work controls using saved valid programs and deterministic scripted
   oracle/continuation tests. Time key/preparation/snapshot work separately from
   worker/process startup. A wall-budget cold run alone cannot isolate speedup.
4. Implement one measured optimization at a time. First target repeated key/model
   preparation with explicit immutable ownership; then snapshot/archive work if
   its measured cost remains. Keep focused, tested recovery commits.
5. Verify semantic identity, cache invalidation and callback snapshot equality for
   changed owners, rows, scope, objective, interruption and JSON alias loss.
6. Run affected native tests, then the recorded native gate. Finally run cold
   unrestricted Real11 and a durable solve/resume control separately. A faster
   run may attempt more work in300s; do not require the same cold call count or
   claim new numerical behavior is a bookkeeping improvement.

## 8. Commands and acceptance

From the new worktree's `standalone/`, use its installed interpreter. For example
create `../.venv-bookkeeping` and install `--editable '.[numerics,reachability]'`;
follow [runbook](RUNBOOK.md) for that worktree's native helper build/config.
Set `PKC_IPOPT_PYTHON`, `PKC_RUN_TICKS_COMPILER` to its own absolute paths, and
`OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1`.

```sh
"$PKC_IPOPT_PYTHON" -B -m unittest \
  tests.unit.reachability.test_proposal_analysis \
  tests.unit.search.test_transfer_time_bounds \
  tests.unit.search.test_anchor_dfs tests.unit.search.test_depth_first \
  tests.integration.test_orchestrator_continuation \
  tests.integration.test_repair_resume tests.unit.io.test_checkpoint_store \
  tests.integration.test_profiling tests.integration.test_package_layout
"$PKC_IPOPT_PYTHON" -B tools/native_gate.py --output /new/path/native
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input cases/real11/physical-anchors.json --output /new/path/cold --seconds 300
"$PKC_IPOPT_PYTHON" -B -m tools.validate_layout
"$PKC_IPOPT_PYTHON" -B -m tools.check_docs
```

The native gate preserves610 tests; report actual counts, exclusions and skips.
Also exercise current CLI durable callbacks and exact resume, not only the cold
profiler's progress callback. Reuse existing fixture/support builders; promote
small necessary cases rather than reading huge reports in ordinary unit tests.

Fixed-work acceptance: unchanged canonical model/identity and request sequence,
no repeated completed request, correct interrupted node/repair restoration,
callback-by-callback lossless snapshots, no sibling/cache mutation, identical
independent fixed-witness acceptance, bounded cache/storage growth, measured
reduction in the targeted cost with profiling overhead distinguished.

Cold acceptance: valid finite T64 remains discoverable without historical seeds;
all reported retained witnesses replay. Search ends under its declared caps, not
with an optimality assertion. Protected evidence hashes remain unchanged.
No comparison/pruning/traversal/solver equations/tolerances change in this task.

## 9. Deliver back for integration

Provide branch/base and ordered commits, changed-file ownership, measured before/
after fixed-work results, cold/durable profiles with hashes/settings, test scope
and skips, cache invalidation/memory rationale and exact-resume evidence. Flag
shared-file conflicts with the scheduling work. The original agent will review
and integrate focused commits; do not blindly merge another branch or overwrite
its environment/build outputs. Keep current docs updated and historical evidence
unchanged. Scheduling and terminal-solve-intent changes belong in the other task.
