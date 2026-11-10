# Review of the nine unintegrated solver-branch commits

The user subsequently approved the selective recommendation. See
[implementation and current acceptance](ANCHOR_SEARCH_INTEGRATION.md).
The review below preserves the pre-integration findings and source identities.

2026-11-05. Review only: no merge, cherry-pick, production edit or branch reset.
Reviewed solver head `c96f8d5a` against anchor head `769cdbe3`; common ancestor
`61355976`. Existing AGENTS.md and CODE_MAP.md edits are preserved.

**Recommendation:** take numerical row reduction and corrected bound extraction;
adapt shared persistence to the new anchor/event execution owners; retain the
historical evidence; do not import the constant-zero intermediate objective as
the default. Do not merge all nine blindly. The dominant measured resume-copy
problem and the extra-run restoration defect require separate fixes.

## Commit-by-commit decision

| Commit | Change | Recommendation |
| --- | --- | --- |
| `57522a72` | Real11 retry evidence and exact pruning/call accounting | Keep as historical evidence. Its source and virtual-finish task differ from the newer anchor run. Do not replace current anchor results/status with it. |
| `56806581` | Hard-row audit and benchmark script/data | Keep diagnostic tools and evidence; no production behavior to import. Use the recorded revision when reproducing old measurements. |
| `8fbdb803` | Numerical row/callback overhead probes | Keep evidence. Its checkpoint timing measures disk callbacks, whereas the newer combined driver's callback did not serialize full reports during search. |
| `4a927023` | Bound-extraction probe and checkpoint measurements | Keep the historical extraction-monitor failure and its provenance. The later implementation fixes that monitor; this probe is not evidence that corrected extraction is broken. |
| `7d2726c3` | Live-state versus duplicated-history accounting | Keep the diagnosis. It does not implement an in-memory copying optimization. |
| `dca14538` | Shared checkpoint store and durable repair patches | Adopt core storage/reader and tests; adapt callback wiring and state paths before using on anchor/event runs. Preserve independent-copy decode behavior and the two-file format contract. |
| `e9345360` | Numerical row reduction and corrected simple-bound extraction | Adopt implementation and tests. This preserves original requests/constraints and is largely independent of anchor traversal. Keep the monitor correction together with extraction. |
| `090aa1ac` | Constant-zero objective whenever stopping on feasibility, plus orchestrator mode wiring | Do not adopt wholesale: conflicts with the later anchor requirement to retain onward progress while stopping on feasibility. Resume/mode diagnostics and accepted-point retention are useful ideas, but must describe the actual policy and fit current contexts. |
| `c96f8d5a` | Repeated Real11 comparisons of solve modes | Keep evidence and benchmark tooling. It supports retaining directional early stopping; it does not establish a route-search speedup or mandate pure feasibility. |

Only three commits alter production behavior: storage, numerical constraints,
and intermediate objective policy. The others supply scripts, evidence and
documentation. Existing evidence should remain immutable and source-labelled.

## Numerical row reduction and extraction: suitable to integrate

In [constraints.py](../ipopt/constraints.py), absolute X/Z GE rows retain the
largest lower bound and LE rows retain the smallest upper bound for each state.
Other modes remove only exact duplicates of expression/operator/comparison/RHS.
Different angular restrictions, relative operations, equalities, states and
axes remain distinct. Conflicting lower/upper bounds are retained, rather than
silently repaired. No rounding is introduced. The original request stays intact,
and sourceIndices maps every numerical representative back to original rows.

[worker.py](../ipopt/worker.py) enables simple-bound extraction and recomputes
the original assembled expressions at each candidate x, rather than trusting
the returned g vector to have pre-extraction semantics. The monitor correction
must accompany extraction. Parent scalar replay still checks every original
requested row. Facings, movement equations, contact rules and original-start
re-solving remain unchanged.

The native worker tests cover mixed simple/non-simple and angular rows,
misleading supplied g, final-point acceptance and early stopping. Constraint
tests include contradictory bounds and translated trajectory controls. I found
no correctness blocker in these changes. This is source review and regression
evidence, not a numerical equivalence proof for every possible map.

The recorded fixed-request benchmark reduced 278 wall rows to91, with graph,
setup and first-solve median about47ms to33ms. It is a useful local optimization,
but the newer profiled anchor baseline used only7.45s in solver invocation over
304.77s, versus205.65s copying the whole DFS state. Importing this will not by
itself fix the dominant runtime stall.

## Shared persistence: useful core, incomplete wiring for anchor search

[checkpoint_store.py](../autonomy/checkpoint_store.py) stores unchanged content
once, commits objects before publishing an atomic root manifest, verifies stored
content hashes on read, and supports legacy JSON plus JSON export. The manifest
and companion SQLite database must travel together. Decoding intentionally
returns independent mutable copies, matching JSON semantics.

[checkpoint_persistence.py](../autonomy/cell_routes/checkpoint_persistence.py)
patches active `repairState` aliases and `cumulativeCalls` against a frozen
dispatch checkpoint. It assumes the outer state is the direct DFS dictionary
and an active evaluation is `stack[-1].inFlight.program`. That was appropriate
for the older fixed-clock CLI, which is the only writer wired by this commit.

The anchor branch adds a different execution structure:

- The outer event state nests DFS under `state['dfs']`. Its forwarding callback
  currently does not forward an optional `repair_progress` method.
- Selected internal-node evaluation lives in a movement continuation, can update
  `nodes[identity]` during repair, and is not necessarily `frame.inFlight`.
- Anchor scoring, node results, turn observations, accepted variants and
  multi-owner references must remain consistent in every durable publication.
- The automatic CLI uses its own ordinary-JSON save path; importing the old
  fixed-clock writer alone does not switch it to shared persistence.

Required integration work is to define full and incremental publications for
both anchor arrivals and internal nodes, wrap/forward the nested outer state,
and test decoded equality at every callback, interrupted resume and final
publication. If a callback cannot identify the complete mutation set, use a
full checkpoint rather than publishing an incomplete repair patch. Preserve
pending siblings, hard latches, setup hops and exact completed-request dedup.

This storage implementation explicitly leaves live DFS state copies intact.
It cannot remove the205.65s spent in `deepcopy(resume_state)` in the newer
profile. That needs ownership/reuse across in-process slices, retaining copy
isolation at external resume and actual historical snapshot boundaries.

## Pure feasibility: semantic conflict with the later requirement

The anchor requirement is to keep backward guides and optimize onward progress
while stopping at the first feasible point. These are independent settings:
the objective supplies a useful direction before feasibility; stopping controls
how long the solver continues after reaching feasibility.

`090aa1ac` couples them in `worker.fixed_phase`: `if stop: cost = ca.SX(0)`.
Even cherry-picking just that worker hunk silently removes the guide objective
from current anchor/node queries. Avoiding the conflicting DFS hunks does not
avoid this semantic change. Its `objectiveMode` labels would also need updating
if adapted to retain a directional objective under early stopping.

The current anchor branch already passes explicit `stop_on_feasible` through
contact and route search, with `solvePolicy` in repair identity and assembly
version9. Do not replace that with the older `not polish` inference, assembly
version6 or the older DFS schema5. Mode changes must reset incompatible repair
and constraint-generation work without dropping compatible same-policy work.

The [repeated benchmark](../autonomy/experiments/last-jump-runs/row-benchmark/REAL11_INTERMEDIATE_BENCHMARK.md)
compares nine fixed continuous requests, including already-feasible seeds and
unknowns. Successful numerical medians were approximately133–148ms for
directional early stopping and139–151ms for pure feasibility. The pure witnesses
lost approximately0.21–0.47 blocks of Z progress in the three successful
numerical cases. Both modes can bypass a worker for an already-valid seed.
Neither then optimizes that seed further, which is part of the agreed fast
first-feasible policy. The evidence does not establish which discrete schedule
will win after later full-history re-solving.

Useful pieces to adapt separately include explicit mode labels, guarding repair
reuse when numerical policy changes, and retaining the actually accepted point
when constant costs tie if an optional pure-feasibility mode is retained. They
are not a reason to switch the selected anchor default to constant zero.

## Merge and validation findings

An object-only `git merge-tree --write-tree` preview found conflicts in three
production files: `checkpoint_search.py`, `depth_first.py` and `search.py`.
There are also six documentation conflicts. It did not change either branch,
index or worktree. A cleanly applying worker hunk still has the objective-policy
conflict described above; textual merge success is not semantic compatibility.

Fresh tests of the existing solver-branch code, using the existing native Python
and compiler with BLAS/OMP threads1:

- Full IPOPT discovery: **64 passed**, no failures/errors/skips,9.657s.
- Checkpoint store, intermediate feasibility and DFS tests: **25 passed**, no
  failures/errors/skips,3.318s. Includes native callback round trips and physical
  replay, plus distinct feasibility/optimization repair work.

Logs, branch identities, preserved-edit hashes and merge-preview output are in
`/tmp/pkc-solver-integration-review-2026-11-05/`. The first discovery command used
an incompatible top-level discovery option and loaded no tests; the corrected
discovery log contains the64-test run. No tests were skipped to obtain a pass.
These are tests of the reviewed source branch, not an integrated build. No
integrated cold solve or speedup is claimed.

Before an approved integration is treated as complete, run the numerical tests
alongside the anchor branch's setup-hop/global-latch, node cadence, turnaround,
minimum-run and exact-resume regressions; add the persistence controls above;
retain the directional early-stop objective; then rerun the profiled combined
case with fresh outputs and unchanged task/limits. Keep the unconditional
extra-run restoration fix separate and visible in that comparison.
