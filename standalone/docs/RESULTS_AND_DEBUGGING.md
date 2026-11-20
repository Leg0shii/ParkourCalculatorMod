# Results, terminology and debugging

For current physical-anchor search use the [execution reference](ANCHOR_SEARCH_EXECUTION_REFERENCE.md). It separates a consumed proposal, admitted program, constraint variant, evaluation, solver wrapper and worker launch; these counters must not be interchanged. A strict finish-bound exclusion is different from a recoverable peer deferral, a resource cap, an analytical absence proof, exact operational oracle rejection or suspended work.

Current saved evidence: `DFS.transferTimeBounds`, `timeBoundExclusions`, `comparisonWork`, `comparisonDecisions` and the single `minimumRunExclusions` compatibility table; frame-local `tickPeers`; `interactionGate.metrics`; and the profiled arms' `requests.json` invocation fingerprints/stop/worker status/owning routes and `summary.json` `stop` report ([solve reports](#solve-reports)). Use [matched results](../research/hard-jump-comparisons/README.md) for source provenance. A common-case rejection before side fan-out creates no side programs/workers. A rejected unused tail cannot label its valid owned anchor prefix infeasible.

Start with [architecture](ARCHITECTURE.md) for call flow, [runbook](RUNBOOK.md) for execution, and [Real11 guide](REAL11_GUIDE.md) for the existing corpus. Do not launch a new long solve just to answer a question already logged.

## Vocabulary and units of work

| Term | Meaning |
| --- | --- |
| Cell / portal / corridor | Spatial decomposition region / opening / selected broad route through regions. No intrinsic tick cost or fixed dynamic state. |
| Prefix inputs | Number and identities of already modeled **input ticks**, not estimated arrival at a common spatial goal. Old priority incorrectly favored shorter prefixes. |
| Program | Concrete discrete movement/input/contact history, scalar vertical trace, constraints and provenance; horizontal facings are still variables. |
| Passage description | The phase cover chosen for one box encounter within a landing segment (`constraints/encounters.py`) and the clearance rows it implies. Many descriptions can yield one effective row set. |
| Constraint variant | One concrete hard row/contact alternative for a fixed program. A schedule can require many such trials. |
| Branch | One handled constraint alternative. It may be screened or stopped before worker dispatch. |
| Solver wrapper / worker launch | The Python solve API may return a validated feasible seed without IPOPT in early-stop mode. A worker launch starts a subprocess numerical solve. One worker can have multiple internal phases/iterations/callbacks. |
| Relaxed candidate / finalization | Automatic discovery answers numerical requests with the Clarabel disk relaxation (no worker); its candidates carry `model: relaxed_disk`. One IPOPT finalization at the end turns the relaxed incumbent into the published physical witness. |
| Repair search/state | Inner fixed-program enumeration/refinement and its resumable queues/logs. It is not an IPOPT mixed-integer mechanism. |
| Spatial probe | Auxiliary arrival solve used to compare alternatives at a common spatial goal; does not by itself establish a complete route. |
| Accepted prefix | Numerically and physically accepted for its recorded scope/horizon. Not a complete terminal incumbent. |
| Unknown / suspended / exhausted | No accepted witness yet / unfinished saved work / declared domain drained. None alone certifies physical impossibility. |

## Result layers

1. **Numerical:** IPOPT/parent scalar validation satisfies the fixed continuous request. Read `status`, `termination`, `best` or `candidate`, violations, worker phase diagnostics. Worker “feasible” is not global optimality or scene discovery.
2. **Vertical/event:** Selected run/jump/landing/stepping/ceiling schedule agrees with scalar motion, impulses and ground timing.
3. **Scene:** Swept Y→X→Z collision/body checks agree with supplied solids and scope. Omitted external blocks are not magically validated.
4. **Corridor:** The accepted physical trajectory also meets the selected broad route/projection/signature. A corridor rejection can be physically valid on a different signature; inspect rather than label IPOPT infeasible.
5. **Executable replay/export:** Float yaw, input/sprint/cooldown, sine-table and finite-scene replay plus JSON round trip pass the replay report. Actual-world mod validation remains separate.

Inspect `solveScope`, `solveIdentity`, `best.validationScope`, `fullSceneValidated`, and `candidateFilter.corridorAudit`. Preliminary scope omits future interactions and matching corridor requirements; it cannot become a complete incumbent. `validation_prefix` ensures candidate arrays and checks use the same horizon. A skipped check is neither PASS nor FAIL.

## Report layout and safe inspection

Fixed-clock reports contain `source`, `fixture`, `windows`, progress `searchState`, and final `result`/`validation`. A live progress file may not yet contain a final `result`; final files put continuation under `result.searchState`. Window candidate rows contain programs/scores and retained/deferred classifications. Terminal attempts and spatial probes have separate records. Inner `repairState` has `attemptLog`, `contactLog`, constraint generation and pending/inFlight/completed/unresolved state. Read the producing module for the exact schema/version; don't assume all historical reports share it.

Read large files selectively:

```sh
python3 - <<'PY'
from pathlib import Path
from parkour_solver.io.checkpoint_store import read_report
p = Path('/tmp/real11-cold-new.json.gz')
report = read_report(p)  # Shared manifest/SQLite or legacy plain/gzipped JSON.
r = report.get('result', report)
print('keys:', sorted(r))
print('status:', r.get('status'), 'termination:', r.get('termination'))
print('work:', r.get('work'))
s = r.get('searchState', report.get('searchState', {}))
print('continuation keys:', sorted(s))
PY
```

For automatic discovery, `best` is the physical witness from the IPOPT
finalization, or `None` with `termination: finalization_failed`; the relaxed
incumbent stays in `searchState.dfs.best`, and `provisionalBest` (ticks,
objective, model) appears whenever the published route differs from it.
`finalization` lists the attempts and stage. In `work`, `workerLaunches` and
`feasibleSeedReturns` count interim IPOPT only; `conicCandidates`,
`relaxedFallbacks` (and `relaxedFallback:<reason>`: `unsupported`,
`relaxed_undecided`, `rejected_relaxed_point`) describe relaxed requests, and
`finalizationIpoptCalls` the finalization's worker launches. The solve report
labels each request's status with its solver and adds a `Finalization` row.

Do not dump complete multi-MB reports to the conversation. Use tables/counters and link exact provenance. Resume snapshots repeat history; count identity deltas, not list lengths summed across files. Logs do not always align one-to-one: the saved Real11 corpus has two missing post-validation records with ambiguous possible branch placements. Preserve that ambiguity.

## Solve reports

A profiled run (commands in the [runbook](RUNBOOK.md#run-a-solve-and-report-it))
writes these files to its output directory:

| File | Contents |
| --- | --- |
| `report.md` | The readable report: outcome, why the search stopped, the route tree, and one section per partial route. |
| `routes.json` | The same per-route trace as data. |
| `summary.json` | Best route, timings, work counters, replay verdicts and the `stop` report. |
| `requests.json` | Every solver request with its fingerprint, status, IPOPT status, wall seconds and `owners`. |
| `report.json.gz` | The full saved search state; read it with `read_report` (paired `.store.sqlite3`). |
| `provenance.json`, `profile.json`, `bookkeeping.json` | Source revision and hashes, fixture, interpreter; timing buckets. |

`report.md` names routes by what they do, never by hash: route `F.N` is route N of
frame F, grouped under its movement (jump ticks, the blocks it starts on, lands on
and runs across, ceiling hits, wall touches and named cases), and each route lists
the obstacles it passes with the sides taken (for example `B5+B6+B7: Z- then X-`; an obstacle is a
cluster of touching blocks, and a corner side reads `X+Z+`).
Each partial route in `report.md` and `routes.json` has:

- its parent route, the constraint rows inherited from the parent's solved route
  (what `retain_prefix` copies) and whether its starting facings were seeded from it;
- the choices the movement generators ruled out by reachability before building them
  (the `InteractionGate` exclusions, tagged with the frame and the generator's block and
  contact kind): the choice, the player-centre area checked, the ticks, the check, how
  often it was ruled out and the first reason; a frame whose generators built nothing
  still gets this section;
- the avoidance alternative accepted for it. Routes from one movement program share
  a single repair run, kept on the first route of that program;
- on that route: every prepared obstacle encounter (possible phases, already-clear
  phases, the sides ruled out at each phase, vertical-clear and uncertain phases);
  the decisions to make (the sides to keep around each obstacle in reach, and which
  rectangle of a contacted face of several rectangles to touch or land on); every option rejected
  before solving, with its decision, rows, the check
  (interval, travel-distance, single-row or linear-relaxation reachability) and
  where it failed; duplicate and dominated options; every alternative sent to the
  solver with its outcome, its solves (request number, status, IPOPT status, seconds)
  and scene validation; an alternative without a solution is `infeasible (<check>)`
  when the pre-solve check on its compiled problem proved every attempt infeasible,
  `infeasible (IPOPT local)` when IPOPT reported local infeasibility for every solve
  (the search counts both as infeasible and never retries them) and `unknown` only when
  it is kept for a retry; the movement's total solver seconds; a families table (the
  alternatives that differ only in when they switch obstacle sides, each switch
  named by its input, with the switches the climb solved, the best one and how many
  were never solved); the kept alternative and its fallbacks (the last leg keeps
  none); and the unresolved or pending alternatives.

A request's `owners` are the routes whose repair log holds its problem hash; an
empty list means no saved route kept that solve. The `stop` report lists suspended
frames with their unresolved and pending alternatives, the routes closed because
every alternative is infeasible, and the owners of every request without a solution.
The `Disk-relaxation pre-checks` line under it counts the run's disk-relaxation screens
(`interactionGate.relaxation.screen`, also `summary.json` `relaxationChecks`, which also
holds the `candidate` counts): solved, served from cache, proved infeasible, and undecided
by Clarabel (counted as not ruled out). The trace is a pure
function of the saved search state, so regenerating it never changes a run.

## Failure investigation workflow

1. Fix the claim and context: cold discovery, seeded control, prefix, terminal objective or actual replay? Record fixture revision, allowed choices, scope and source hash.
2. Trace one candidate from generator choice → append/truncate → profile/schedule → compiled coefficients → exact rows/contact alternatives → screen → dispatched request → scalar result → physical/corridor checks → retention/child program → final/resume state.
3. If no numerical request occurred, inspect source cursor, deadlines, duplicate counters and pre-dispatch proof. Raising IPOPT time cannot fix symbolic starvation.
4. If the worker succeeded but the candidate was rejected, identify the exact check, phase and residual. Compare full physical replay with corridor signature separately. For an omitted future obstacle, both preliminary constraints and checks must omit its interactions.
5. For a suspicious block/tick, ask the **shared interaction query** with the actual compiled context. Inspect swept Y extent, X/Z envelopes and selected contacts. Endpoint height or a soft guide is not a reachability proof. Impossible required contact rejects the proposal; impossible unwanted contact creates no avoidance.
6. For orphan constraints, retain the full proposal and owner event before projection. Compare untruncated and truncated checks. A discarded future event must not leave accidental hard approach rows.
7. For repeated work, compare physical/context keys and exact effective rows; distinguish raw descriptions, unique models, dispatched calls and worker callbacks. Never infer that many duplicate descriptions mean every numerical call was duplicated.
8. For active anchor pruning, inspect the shared verdict: same frame/incoming context, mode, milestone/phase/scope, local J/R/A and validated peer; then distinguish recoverable deferral from strict optimistic-finish exclusion. Different schedules are not automatically incomparable at an anchor. Legacy common-clock state guards still require their original schedule/phase and same-witness position/velocity alignment. Retention caps must not erase saved protected/deferred work.
9. Add a minimal generic regression and a fixture-specific one. Then run only the checks needed to validate the hypothesis, before broadening.

## Profiling and evidence

Use existing `generation_diagnostic`, `audit_generation`, `CountingCompiler` and `SearchWork` counters. Offline `cProfile` can isolate generation/refinement. Avoid timed `faulthandler` stack-dump timers around the macOS native numerical runtime; prior diagnostics found that setup unsafe. Keep BLAS/OpenMP threads bounded, record overlapping jobs, and do not benchmark compiler rebuilds concurrent with solves.

Preserve plain/gzip JSON, exact constraints, source hashes, command/settings, wall and worker time, and any replay checks. Gzip evidence should use deterministic `mtime=0`; JSON must reject NaN/Infinity. Never round coordinates before re-evaluating a tight constraint. New outputs belong in new paths, with a manifest when archived. Historical source hashes are not updated just because explanatory documentation changes.


## DFS reports

`result.searchState` contains `stack`, archived `frames`, `suspended`, deferred `refinements`, ordered `transitions`, `initialRoots`, cumulative calls and the incumbent. Frames name their shared spatial goal and retain parents, a symbolic generator/cursor, unscored `ready` programs, scored `records` and `inFlight`. A frame may be archived as history, but has only one execution owner among active/suspended/refinement queues. Reports' old `windows` fields are selected-goal diagnostics, not breadth barriers.

Record `arrival` is a validated goal time. `value` is that program's local objective, not an arrival lower bound. `child` is the actual committed discrete program. A parent's current best child can improve during refinement; the descended frame's saved `parents` is the authoritative program used for that descent. `variantOf` records inherit numerical provenance but do not own additional worker calls; don't sum their copied logs again.

Per-worker diagnostics now retain `request`, `problemHash` and `callOptions`, with exact magnitude/friction/base arrays, fixed constraints, contact schedule and initial state. Re-run `prepare`/`fingerprint` offline to verify the hash. Completed inner branches and contact-validation entries carry a shared `branch` key; `validationStatus` distinguishes accepted, rejected, no candidate and not-run validation. A stopped validation is not an IPOPT failure. Older cold evidence predates these additive audit fields and is explicitly labelled; its fixed program/context and hashes remain available for reconstruction with the source/compiler.

[Current summary](../research/dfs-interactions/evidence/summary.json) and [14-call table](../research/dfs-interactions/CALLS.md) reconcile the measured cold/resume pair. `summarize.py` validates frame ownership, exact model context, request fingerprints, preserved completed history, no redispatch and independent replay results. The new traversal retains all siblings; legacy `comparison_policy`/`fair_parents`/beam settings no longer select the old traversal or permanently evict protected candidates. Read the actual `order`/`comparisonPolicy` fields.
