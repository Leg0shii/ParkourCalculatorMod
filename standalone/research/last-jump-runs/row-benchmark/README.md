# Do duplicate rows cost time?

2026-11-03. Measurement-only experiments on one saved fixed continuous request from source61355976, run against production code at56806581 (CasADi3.8.1). No production code changed and no full route search was run. [A/B data](results.json), [run log](run.log), [callback probe](callback-results.json), [probe log](callback.log).

## Solver behavior and source references

The current worker appends every wall row as a general constraint. It does not enable CasADi's `detect_simple_bounds`, which is false by default according to the [CasADi API](https://web.casadi.org/api/internal/d4/d89/group__nlpsol.html). Consequently the simple bounds are not automatically moved to `lbx/ubx` by our setup. IPOPT's optional [dependency detector](https://www.coin-or.org/Ipopt/OPTIONS.html#OPT_dependency_detector) is disabled by default and concerns dependent equalities, not this list of duplicate positional inequalities. We should not assume a generic presolver will clean up this row list.

The benchmark keeps the same67 inputs, coefficients, momentum, all335 decision variables, initial facing seeds and objective. It changes only the explicit simple positional bounds: original278 rows, exact114, or strongest91. Total CasADi constraints including motion equations remain546,382,359 respectively. Every benchmark incumbent is rechecked against the original278 rows.

## Controlled A/B results

Five repetitions per formulation, rotating order; plugin load is warmed outside the first-phase timer. The first-phase200-iteration cap and5-second safety limit never bind. Fresh-worker tests use the existing solver wrapper, original initial/fallback seeds, a2-second budget and the same200-iteration cap, which is also inactive (all phases converge in≤25 iterations). OPENBLAS/OMP threads are1.

| Formulation | Explicit rows | First-phase iterations | IPOPT call median | Graph + solver setup + first solve median | Fresh worker/two-seed median |
| --- | --- | --- | --- | --- | --- |
| Original | 278 | 17 | 13.31ms | 46.32ms | 198.55ms |
| Exact duplicates removed | 114 | 12 | 7.16ms | 34.11ms | 177.09ms |
| Strongest bounds retained | 91 | 11 | 6.01ms | 32.61ms | 182.79ms |

The fresh-worker column has three repetitions per formulation; startup and other fixed costs make the difference smaller and noisier than the isolated IPOPT change. All24 fixed-model runs produce candidates feasible against the original rows, and their best objectives agree within about1.1e−9. This is one saved request, not a suite-wide speed guarantee. Exact-row and strongest-bound transformations are mathematically equivalent here; no decision or geometry is relaxed.

Removing duplicates helps, but the absolute savings on this case are milliseconds. It does not explain the original317.53-second search. The saved retry's12 numerical-call diagnostics sum to21.312s; those durations include parent-side incumbent callbacks and are not pure IPOPT CPU time. Compiler timings total about4.02s. Most wall time is outside those recorded call spans and has not been completely attributed.

## Orchestration overhead from rows

Per-operation means over100 calls, with a common feasible yaw and the LP-result cache warmed:

| Operation | 278 rows | 91 rows |
| --- | --- | --- |
| Copy/validate request (`prepare`) | 1.394ms | 0.778ms |
| Fingerprint | 0.460ms | 0.250ms |
| Request JSON encoding | 0.399ms | 0.254ms |
| Scalar candidate validation | 0.258ms | 0.196ms |
| Reach envelope | 2.591ms | 1.998ms |
| Cached linear-screen preprocessing | 0.265ms | 0.186ms |
| Compact request size | 43,772 bytes | 23,763 bytes |

These are isolated timings, not an exclusive accounting of a whole solve. Effective-row/branch logic and `linear_exclusion` already merge/deduplicate rows for their own keys/LPs; duplicate final rows do not automatically multiply the number of discrete obstacle choices. The row count still enlarges copying, hashing, validation, request payloads and retained state. Prefix keys can also distinguish lists with different duplicate multiplicity; proving additional model equivalence remains separate from mere solver-row cleanup.

## A much larger measured cost: synchronous progress snapshots

Reconstructing the actual final progress-publication payload (source, fixture, windows, searchState) gives the following one-snapshot measurements. This excludes disk writes and the additional copying done when building inner repair snapshots:

| Encoding of the same data | Uncompressed JSON | Compressed JSON | Encode + compress |
| --- | --- | --- | --- |
| Current indented JSON | 109.30MB | 4.32MB | **4.039s** |
| Compact JSON | 44.73MB | 2.30MB | **0.746s** |

There are71 recorded DFS transitions, and additional inner progress publications. Earlier snapshots are smaller, so multiplying4s by71 would be invalid. This nevertheless identifies a substantial late-stage cost that dwarfs the per-request row-processing savings.

The callback chain is synchronous: numerical incumbent → `search.publish` → `block_contacts.retain/publish` → DFS progress → `fixed_clock_experiment.checkpoint_progress` → whole-report `save`. The parent solver's deadline continues advancing during this work.

A separate probe replays the same fixed278-row request with a2-second budget and a callback that encodes/compresses the late snapshot, without writing it to disk:

| Incumbent callback | Solver wrapper elapsed | Outcome |
| --- | --- | --- |
| None | 0.235s | both phases converge; worker_finished |
| Current-format snapshot | 4.169s | time_limit after one4.046s callback; valid incumbent preserved |
| Compact snapshot | 2.284s | time_limit after three snapshots totaling2.162s; valid incumbent preserved |

This is a causal callback probe, not a full live-search profile. It demonstrates that reporting alone can consume a solve budget and prevent the parent from collecting later phase messages. Compact formatting helps but does not solve excessive publication frequency or repeated full-state copying. It does not establish how much of all317.53s is attributable to reporting.

## Recommendation/status

Final-row canonicalization is a justified small cleanup. Larger priority: preserve exact resumability while reducing redundant full-state encoding/copying and controlling synchronous publication cost. Keep in-flight requests, repair cursors and incumbents durable; do not simply remove logging or discard state. Stage-level timing would close the remaining attribution gap. None of these runtime fixes was implemented by this measurement turn; corridor/cell changes remain separate.

Reproduce from the physical repo root:

```sh
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 PYTHONPATH="$PWD" .venv-guidance/bin/python \
  standalone/autonomy/experiments/last-jump-runs/row-benchmark/benchmark.py \
  --input standalone/autonomy/experiments/last-jump-runs/evidence/real11-retry-after-handoff.json.gz \
  --output /tmp/row-benchmark.json --repeats 5 --iterations 200 --external-repeats 3

OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 PYTHONPATH="$PWD" .venv-guidance/bin/python \
  standalone/autonomy/experiments/last-jump-runs/row-benchmark/callback_probe.py \
  --input standalone/autonomy/experiments/last-jump-runs/evidence/real11-retry-after-handoff.json.gz \
  --output /tmp/row-callback-probe.json
```

## Can we simply enable bound extraction?

It is a promising optimization, but the one-line toggle is not compatible with the current monitor. [Probe data](extraction-probe.json) and `extraction_probe.py` test three cases on the saved request, without changing production:

| Probe | Backend | Original constraints at backend's final point | Our existing monitor |
| --- | --- | --- | --- |
| Extraction off | converges in17 iterations | feasible | accepts |
| Extraction on, stock monitor | converges in11 iterations | feasible | rejects |
| Extraction on, recompute original expressions for monitoring | converges in11 iterations | feasible | accepts |

The stock monitor interprets CasADi's returned `g` vector against the original row bounds. With extraction enabled in installed CasADi3.8.1, that interpretation fails: its largest apparent residual is about35.31, while independent scalar validation confirms the actual final point is feasible. The backend is not returning an invalid solution; the monitor rejects a valid one and the current phase fallback can return the old seed instead. The prototype checks the original `g(x)` expression directly before reporting incumbents, preserving full original-row validation.

Thus enabling `detect_simple_bounds=True` plus a small monitor compatibility change is a concrete next implementation. The flag alone is not a drop-in change in this worker. Production remains unchanged pending focused worker regressions, including incumbent/early-stop behavior and non-simple constraints. Extraction also leaves the Python row lists, inherited state and JSON payloads intact, so it cannot remove orchestration/reporting overhead by itself. The [CasADi option documentation](https://web.casadi.org/api/internal/d4/d89/group__nlpsol.html) describes extraction as moving single-variable constraint rows to variable bounds.

## What exactly is snapshot saving?

A snapshot is a checkpoint of search progress so a budget stop or restart can resume without losing candidates or replaying completed requests. It contains the DFS stack and siblings, discrete programs/constraints, current in-flight request and repair/generation cursors, seen/completed work, feasible incumbents and local-run representatives. Our current file also embeds full diagnostic/audit history: numerical requests, candidate trajectories, past windows, accepted variants and repeated copies of related records.

The current sequence is:

1. The worker emits an improved candidate; the parent validates it and updates the incumbent.
2. The parent builds an inner repair-state snapshot and attaches it to the outer DFS state.
3. The progress callback rewrites the **whole report**, using indented JSON and gzip, then atomically replaces the checkpoint file.
4. Only after this returns does the parent continue receiving worker messages and checking the search queue.

This is synchronous work in the orchestration process. The IPOPT subprocess can continue or finish meanwhile, but the parent cannot collect its updates until saving finishes. The same wall-time deadlines continue to advance. That is why the probe's otherwise0.235-second numerical call returns `time_limit` after4.169 seconds with one large save; its accepted incumbent is still preserved.

The measured109MB is one late **uncompressed progress payload**, not merely the278-row request (about44KB). It includes extensive repeated history. About4.04 seconds was JSON encoding plus compression alone, excluding disk writes. Compact formatting lowers the same payload to44.73MB and0.746 seconds, but several such saves can still exhaust a two-second call budget.

A better design keeps current mutable resume state small, records immutable audit/request/trajectory data once and refers to it by identity, and avoids rewriting the entire accumulated history for every incumbent. Small durable records for in-flight/completed work and incumbent updates can preserve resumability, with periodic/full checkpoints and a final report on exit. This is a recommended change, not an implemented redesign. It is separate from corridor work and does not require changing solver physics or the decisions being searched.

Reproduce only the extraction compatibility probe:

```sh
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 PYTHONPATH="$PWD" .venv-guidance/bin/python \
  standalone/autonomy/experiments/last-jump-runs/row-benchmark/extraction_probe.py \
  --input standalone/autonomy/experiments/last-jump-runs/evidence/real11-retry-after-handoff.json.gz \
  --output /tmp/extraction-probe.json
```

## What state is actually used, and where do109MB go?

[Byte accounting](snapshot-breakdown.json) measures the same late progress checkpoint. The figures below are additive; MB means decimal megabytes:

| Component | Bytes represented |
| --- | --- |
| Historical comparison windows, saved twice | 29.49MB compact JSON |
| Everything else, including live state and additional history/copies | 15.24MB compact JSON |
| Indentation/newlines/spaces added by pretty formatting | 64.57MB |
| **Current progress JSON** | **109.30MB** |

The input scene/settings fixture itself is only3,227 compact bytes. The100MB is not the map or a single trajectory. The two identical window histories are `report.windows` and `report.searchState.windows`,14.743MB each. They account for about66% of compact data. JSON writes each occurrence by value even if they shared references in memory.

For the modern resume path, the scheduler uses:

- Version/context/initial roots and counters, to restore compatible work and identities.
- Active DFS stack, saved siblings, deferred refinement/suspended frames and their selected discrete programs/constraints.
- Movement-generation pending/ready queues, current expansion/cursors and seen identities.
- Numerical-repair pending/unresolved/in-flight work, selected interaction alternatives, generator cursors and seen identities, accepted witnesses and facing seeds.
- The best validated result, needed for comparisons and independent replay on resume.
- Learned per-pattern local-run minima and exclusion identities.

Historical `windows` are appended and returned for reports; they are not consulted to choose the next branch in current DFS. The CLI prefers `searchState` for exact resume. Only the legacy fallback for old reports without searchState reconstructs parents from windows. Existing post-run audit/replay helpers use those histories, so the fix is to store them once outside the frequently rewritten working checkpoint, not delete the evidence.

Even the remaining15.24MB is not a minimal checkpoint. The saved frame collections contain nine frame locations but only seven distinct frame IDs. `frames` repeats closed-frame records also held in `refinements`; final-attempt records and witness/child/accepted-result copies repeat related data. Actual semantic owners of unfinished execution must remain unique, as existing checks require, even if audit views reference them elsewhere.

A further3.757MB across stored generator seen lists consists of258 full serialized program-key strings. Their membership is used for deduplication, but their full bodies are not reconstructed from those strings. Using64-character SHA256 identities would represent those lists in about17KB, consistent with existing hashed candidate IDs, while pending programs remain available separately. This requires versioned/compatible key handling and tests; merely truncating strings would be wrong.

The active generator pending programs account for about2.11MB in this representation. Those discrete alternatives and their constraints are useful working data and must not be arbitrarily discarded. Keys, immutable program definitions and accepted witnesses can be stored once and referenced; historical requests/diagnostics can live in an audit store. A smaller layout needs resume-equivalence validation; this audit does not claim a proven minimum byte count.

Source trace: `depth_first` reads stack/refinement/suspended frames to schedule; its `windows` are append/report data. `movement_frontier` restores pending/ready/expansion/seen. `block_contacts` restores queues, accepted state, choices and seen identities; `attemptLog`/`contactLog` are preserved to continue audit history. Their inclusion in the same object does not make every historical diagnostic necessary for numerical continuation. The structured breakdown has nested views; only peer fields within a view should be added together.

No production checkpoint/schema change was made in this audit. The recommended split is a small operational checkpoint plus immutable, separately stored audit/program objects and durable updates, preserving every pending candidate and completed numerical identity.


## Checkpoint cleanup implemented (2026-11-03)

The earlier reporting recommendation is now implemented for the fixed-clock CLI. [Format and callback contract](../../../../docs/CHECKPOINT_STORAGE.md), [probe source](checkpoint_probe.py), [results/source hashes](checkpoint-results.json). Production numerical rows and simple-bound extraction remain unchanged.

Repeated content is stored once in a compressed SQLite companion. Window snapshots are archived once. Full checkpoints publish at DFS transitions; numerical repair progress writes small durable replacements against the dispatch root, including aliases of the same active program. Final compaction removes obsolete roots. All runtime search state remains JSON-compatible and DFS schema5 is unchanged. Legacy files and ordinary JSON export remain available.

| Measurement | Result |
| --- | --- |
| Original complete report |166,627,153 bytes indented /68,405,810 compact /6,302,813 gzip |
| Same complete report, manifest + compacted store | **1,974,444 bytes**;10,565,118 bytes of unique encoded content |
| Complete decoded report equals original | **Yes**, including all windows/requests/results/constraints/audit history |
| Late state with an active candidate, old pretty encode + gzip |110,518,965 bytes;4.284s (excludes disk) |
| New initial archival + full checkpoint |1.193s including disk |
| Five durable repair updates | **12.46ms median**, including disk |
| Final full report save and compaction |0.878s |

The active-candidate probe inserts a saved candidate into the outer dispatch state, so its110.52MB differs from the earlier109.30MB stopped-state measurement. The final-report comparison above uses the **exact original entire report**, not that modified progress payload. Stores are temporary benchmark artifacts; the source and compact measurement JSON are committed.

A fixed recorded278-row NLP with a2s budget completes both phases with three repair saves (about12–14ms each), with the same objective as the no-callback control. The observed0.210s versus0.284s wrapper elapsed values are single samples with startup noise, not evidence that adding persistence accelerates the solver. They show the new callback no longer exhausts this budget; earlier full-report callback probes returned `time_limit`. No full route search was run and there is no measured overall Real11 speedup yet. Full transition saves and inner repair-state copies still cost time; in-memory histories remain.

Focused tests:74 pass in17.945s, native compiler/IPOPT enabled, no skips. Includes failed publication recovery, mutable-reference independence, old/new format reading, pending work and completed-request resume, DFS backtracking, and equality of every native repair publication to the full live state before independent replay.

```sh
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 PYTHONPATH="$PWD" .venv-guidance/bin/python \
  standalone/autonomy/experiments/last-jump-runs/row-benchmark/checkpoint_probe.py \
  --input standalone/autonomy/experiments/last-jump-runs/evidence/real11-retry-after-handoff.json.gz \
  --output /tmp/checkpoint-results-new.json

OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 \
PKC_RUN_TICKS_COMPILER="$PWD/standalone/autonomy/build/compiler.json" \
PKC_IPOPT_PYTHON="$PWD/.venv-guidance/bin/python" PYTHONPATH="$PWD" \
.venv-guidance/bin/python -m unittest \
  standalone.autonomy.tests.test_checkpoint_store \
  standalone.autonomy.tests.test_minimum_runs \
  standalone.autonomy.tests.test_depth_first \
  standalone.autonomy.tests.test_orchestrator_continuation \
  standalone.autonomy.tests.test_fixed_clock_goal \
  standalone.autonomy.tests.test_terminal_goals \
  standalone.autonomy.tests.test_repair_resume -v
```


## IPOPT optimizations implemented (2026-11-03)

Both row reduction and bound extraction now ship in production, superseding the earlier pending recommendation. [Implementation/validation contract](../../../../docs/IPOPT_CONSTRAINT_REDUCTION.md), [current controlled probe](production_bounds_probe.py), [all results and code hashes](production-bounds-results.json). The earlier extraction-probe failure is historical; reproduce it at its recorded source, since the production monitor now recomputes unextracted g(x) on callbacks and final output.

Equivalent numerical rows are chosen in the worker; original orchestrator requests/constraints/owners/hashes and every-row parent validation remain intact. Phase `wallReduction` maps numerical rows back to all original indices. Distinct angular/relative/equality restrictions remain; no RHS rounding or motion/tolerance change.

Five repetitions per configuration, rotated order, same fixed67-input problem, corrected expression monitor in every variant,200-iteration cap inactive:

| Configuration | Numerical wall rows | All scalar rows before extraction | Iterations | IPOPT-call median | Graph/setup/first-solve median |
| --- | --- | --- | --- | --- | --- |
| Baseline: reduction/extraction off |278 |546 |17 |12.87ms |47.12ms |
| Reduction only |91 |359 |11 |7.17ms |34.00ms |
| Extraction only |278 |546 |11 |6.56ms |37.59ms |
| Production: both enabled |91 |359 |11 |6.21ms |32.54ms |

All20 first-phase runs and three fresh production worker runs satisfy every original row; the first-phase objective spread is1.09e-9. Fresh workers complete both seeds in174–177ms. This is not a full new Real11 route search or a measured whole-search speedup. Original request/audit rows remain intentionally stored; [checkpoint persistence cleanup](../../../../docs/CHECKPOINT_STORAGE.md) handles repeated storage separately.

Validation:63 IPOPT tests pass in10.006s,109 affected autonomy tests pass in31.270s, native compiler/IPOPT enabled, no skips. The pure translated-control test was then strengthened to require a feasible witness in each trial and its five-test module passed again.

```sh
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 PYTHONPATH="$PWD" .venv-guidance/bin/python \
  standalone/autonomy/experiments/last-jump-runs/row-benchmark/production_bounds_probe.py \
  --input standalone/autonomy/experiments/last-jump-runs/evidence/real11-retry-after-handoff.json.gz \
  --output /tmp/production-bounds-new.json

OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 \
PKC_IPOPT_PYTHON="$PWD/.venv-guidance/bin/python" PYTHONPATH="$PWD" \
.venv-guidance/bin/python -m unittest discover -s standalone/ipopt/tests -v

OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 \
PKC_RUN_TICKS_COMPILER="$PWD/standalone/autonomy/build/compiler.json" \
PKC_IPOPT_PYTHON="$PWD/.venv-guidance/bin/python" PYTHONPATH="$PWD" \
.venv-guidance/bin/python -m unittest \
  standalone.autonomy.tests.test_checkpoint_store \
  standalone.autonomy.tests.test_minimum_runs \
  standalone.autonomy.tests.test_depth_first \
  standalone.autonomy.tests.test_orchestrator_continuation \
  standalone.autonomy.tests.test_fixed_clock_goal \
  standalone.autonomy.tests.test_terminal_goals \
  standalone.autonomy.tests.test_repair_resume \
  standalone.autonomy.tests.test_stepping_cases \
  standalone.autonomy.tests.test_ceiling_contacts \
  standalone.autonomy.tests.test_contact_combinations -v
```


## Pure feasibility for intermediate solves (2026-11-04)

The user's new policy is implemented: intermediate frames use constant-zero numerical cost and first independently validated feasibility; final frames optimize. [Contracts, mode labels and checkpoint compatibility](../../../../docs/IPOPT_FEASIBILITY_MODE.md). The existing API `stop_on_feasible=True` and CLI `--feasibility-only` now select this behavior; previously they kept a directional objective while stopping early.

[Probe source](feasibility_probe.py), [all measurements](feasibility-results.json). Four archived requests, three rotated repetitions for directional early stop / constant-objective early stop / optimization, plus eight fresh worker runs.36 first-phase records retain unknowns rather than hiding unsuccessful solves.

| Inputs | Archived outcome | Directional early-stop iterations | Constant-objective early-stop iterations | Fresh optimization | Fresh pure feasibility |
| --- | --- | --- | --- | --- | --- |
|27 |unknown |118, no accepted point |33, no accepted point |unknown,254ms |unknown,167ms |
|40 |feasible |16 |16 |feasible,164ms |feasible,115ms |
|57 |unknown |55, no accepted point |42, no accepted point |unknown,268ms |unknown,236ms |
|58 |feasible |11 |8 |feasible,186ms |feasible,122ms |

The27/57 requests were already unknown in the archived source; no feasibility-only regression is inferred from those outcomes. Both successful models satisfy every original row, and no invalid candidate is accepted. Times are small fixed-request observations, not a guaranteed speedup. Pure feasibility intentionally returns different measured progress, and that value is not an optimized bound. There is no new full Real11 search.

Validation:64 IPOPT tests pass in11.356s and162 autonomy tests pass in102.950s, compiler/native worker enabled, no skips. New checks assert an actually constant symbolic objective, retain the accepted witness when costs tie, and replay feasibility→optimization of the same hard model after resetting the old cursor. Existing full integration/capture/contacts/stepping/ceiling/checkpoint tests pass. The changed policy fingerprint means pre-change DFS states need explicit reconstruction rather than silent exact continuation.

Reproduce the saved-request comparison:

```sh
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 PYTHONPATH="$PWD" .venv-guidance/bin/python \
  standalone/autonomy/experiments/last-jump-runs/row-benchmark/feasibility_probe.py \
  --input standalone/autonomy/experiments/last-jump-runs/evidence/real11-retry-after-handoff.json.gz \
  --output /tmp/feasibility-results-new.json
```


## All-nine-request repeated Real11 benchmark

[Analysis, setup and reproduction](REAL11_INTERMEDIATE_BENCHMARK.md), [all timing rows](real11-intermediate-results.md), [complete data](real11-intermediate-results.json.gz), [script](real11_intermediate_benchmark.py).135 fresh API/worker runs (nine intermediate requests × three modes × five repetitions), plus81 warm first-phase probes.105 accepted trajectories independently revalidated,30 unknowns; no status disagreements, errors or deadlines.

The principal win is stopping after feasibility. Pure feasibility is139–151ms on the three successful requests needing numerical work, versus180–207ms optimization; directional early stop is about equally fast. Four already valid seeds return in0.2–0.7ms without worker startup. The pure witnesses have0.21–0.47 blocks less measured Z on those three numerical cases. Production policy remains unchanged; these are fixed requests and exclude scene/corridor/orchestration work.
