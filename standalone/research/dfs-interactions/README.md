# Goal-aware DFS and early interaction eligibility

Implementation source: `498c054c` (2026-11-03). This replaces the old outer checkpoint breadth traversal. [Current plan](../../DFS_INTERACTION_IMPLEMENTATION_PLAN.md), [current state](../../../docs/CURRENT_STATE.md), [architecture](../../../docs/ARCHITECTURE.md) and [runbook](../../../docs/RUNBOOK.md) provide context. Core solver equations and IPOPT request format are unchanged.

## Behavior

A comparison frame names one spatial landing/region goal. Known fixed-schedule durations order proposed arrivals; numerical validation establishes actual feasible measurements. Earlier arrival wins, then more effective grounded jumps, fewer grounded runs, stable identity. Explicit initial takeoff domains receive a scoring pass across those families. Later local candidates and optional numerical refinement remain saved work instead of blocking descent. The chosen child's actual inputs/contact/constraint program is adopted; every later solve starts at the original state with all previous facings variable.

Frames form a persistent DFS stack. A completed terminal solve records an incumbent immediately, before another batch or a deadline can hide it. Backtracking visits saved siblings; refinements remain separate. Unknowns are preserved rather than declared dead. Accepted fixed-constraint variants are saved independently of the inner solver's best-objective result, so a locally lower objective cannot silently erase a useful future branch. State version5 rejects old checkpoint queues as exact continuations.

The shared interaction gate queries conservative compiled prefix/free-approach envelopes before normal/stepping/ceiling alternatives are built. Proposed post-contact height is unknown at that point; the horizontal envelope is used, then the complete proposal is checked. Required/unwanted contact checks and scoped corridor gates use the same underlying block/tick/phase query. Full proposals are checked before projection, and row ownership removes uncommitted future approach conditions. A selected arrival cannot copy them back from a longer witness.

Per-obstacle memoization merges identical partial effective constraints only with compatible chain position/resource state. Options dedup before obstacle products; equivalent partial products dedup before LP refinement. Complete effective keys are checked before repeated expensive refinement. This removes redundant descriptions while retaining distinct actual models and blockage budgets.

The Java bridge also restarts after a timed-out request on a subsequent operation, allowing independent replay after a search deadline. Explicitly closed bridges remain closed. This is a process-lifecycle fix, not a physics change.

## Evidence

Source-matched numerical cold/resume reports and logs are archived under `evidence/`. The geometry/input domain is the unchanged seven-box [Real11 fixture](../linkcraft11-opening/fixtures/clock64-orchestrator.json): initial jump inputs0/8, requiredB2→B3→B4→B5, MAXZ at64, optional B7 landing, normal momentum with zero thresholds. Only the wall-time budget changes to150s per session in [the archived fixture](real11-fixture.json); candidate limits and allowed decisions are unchanged. No recorded route/yaw is supplied to cold discovery.

The earlier development smoke run reached B2/B3/B4/B5 at17/28/40/58 and a valid airborne state64 objective−1094.5795853397567 in eight worker calls, with seven independent witness replays passing; first terminal acceptance was~89s. That run preceded the direct-terminal and final persistence refinements, so it is a diagnostic rather than the final benchmark. It already contrasts with the pre-DFS stall at28 after208 calls/~910s, but is not a controlled timing comparison.

[Verified summary](evidence/summary.json), [all14 worker calls](CALLS.md), [cold report](evidence/real11-cold.json.gz), [exact continuation](evidence/real11-resume.json.gz) and [artifact manifest](evidence/manifest.json) preserve the current evidence.

| Measurement | Cold | Exact continuation |
| --- | --- | --- |
| Source | `498c054c` | `072a1111` (additive request/branch logging) |
| Nominal search budget | 150s | 150s |
| Reported elapsed, including progress persistence | 161.46s | 155.29s |
| Worker calls in session | 10 | 4 |
| Cumulative calls | 10 | 14 |
| First full solution | 91.22s, after8 worker calls | incumbent preserved |
| Best Z at64 | −1094.5755032869683 | unchanged |
| Independent witness replays | 10/10 pass | 18/18 pass |
| Repeated completed exact-context requests | — | **0** |

The same semantic context fingerprint is verified across the two source revisions; the change adds audit fields and does not change physics, inputs, constraints or traversal. Cold calls retain problem hashes and fixed-program context; all four new resumed requests are stored in full and checked against their hashes. The raw reports preserve unscored siblings, pending repairs, generation cursors, active stack and deferred refinements. Both initial setup families were measured atB2:17/21;17 was followed first. The21-arrival zero-run fallback is still saved at the root and was not fully continued within these sessions.

The first complete path lands on B2/B3/B4/B5 at17/28/40/58. Backtracking then found a slightly better terminal objective through a B5 arrival at59, which demonstrates why later local arrivals are retained. The known separately seeded reference reaches Z64≈−1093.2675533415547, about1.308 blocks farther than this cold incumbent. Neither reference nor DFS result proves optimality; the unvisited zero-run family matters to quality.

A deadline can be exceeded by serialization/validation/teardown, as shown by elapsed times above. These runs overlapped some numerical tests; timings are observations, not isolated speed comparisons. Seeded reference success cannot substitute for cold discovery.

## Reproduce

Use the standard environment from [runbook](../../../docs/RUNBOOK.md). For cold/resume, invoke `standalone.autonomy.cell_routes.fixed_clock_experiment` with the unchanged fixture and new output paths. `--resume` restores version5 state; `--retry-unknown` deliberately reopens numerical unknowns. New output paths protect archived source-era evidence.

Offline generation measurement, without Java/IPOPT:

```sh
PYTHONPATH="$PWD" .venv-guidance/bin/python \
  standalone/autonomy/experiments/dfs-interactions/measure_generation.py \
  --output /tmp/dfs-generation-new.json --seconds 3
```

This rebuilds current generator cursors over the archived physical/coefficient contexts. It explicitly does **not** restore incompatible old cursors. The old three-second samples were unexhausted: P06/B2 85,945 descriptions/162 row sets; P07/B2 85,552/232; P07/B3 104,810/8. A development measurement of the new generator exhausted those three streams in0.60/0.88/0.90s with162/235/11 row sets. Counts above the old samples are expected because those samples never exhausted. Resource-distinct option counts are larger than unique row counts. Keep these distinctions; do not claim the old164 P06/P07 numerical calls were duplicates.

## Validation and limitations

Generic tests cover ordering at one goal, a lower-objective constraint alternative, true original-state re-solves, interrupted/in-flight resume without changing identity, old-schema rejection, no-clock goal APIs, ownership through projection/promotion, swept phases, unknown coverage, all three open movement families, and small exhaustible face-language coverage. Other numerical fixture families cover turns, stepping, ceilings, run requirements, run-off and obstacle combinations. The archived Real11 B4 case adds a regression proving that invalid blip construction is stopped before its wall options.

DFS improves when useful complete routes are tried; it is not a global pruning certificate. Local generation still uses resource limits and a movement catalogue. Every permitted family may not be scored before a deadline, and nearer siblings can delay a root fallback. All such work remains inspectable/saved. New peer exclusions, weighted velocity scoring, global bounds, cross-branch discrete reuse and expanded movement families remain deferred. See the current-state guide for the known baseline ladder failure and separate executable/actual-world validation tiers.


Named step/grind events need special care in **the shared envelope**, not separate relevance rules. Realized Y endpoints omit the movement probes that trigger those cases. Such phases keep Y unknown and include intended, unclipped X/Z movement in their outer bounds. The existing case builders and full scene checks decide validity. This restored the generic five-input grind→air→blip regression, which an overly tight realized-Y query had incorrectly removed. Precise probe-height envelopes are a possible later performance refinement; never infer vertical noninteraction for these phases from realized feet height alone.


## Final test results

- **368 non-ladder autonomy tests passed** in186.30s, selected from468 discovered tests. This includes stepping, grind/blip, ceilings, turns, run-off, required runs, obstacle combinations, DFS ordering/promotion/persistence and historical Real11 interaction regressions.
- **54 IPOPT tests passed** in25.47s, including native worker and capture cases.
- **25 focused request-logging/repair/terminal tests passed** after adding exact request and validation-branch fields; stored requests are independently fingerprint-checked.
- The preceding broader466-test numerical run found the known ladder baseline failure plus the two five-versus-six-input grind/blip regressions. The latter exposed the realized-Y bug and now pass in the targeted gate and final non-ladder pass. The old ladder failure remains unchanged/out of scope. The final pass excludes dedicated ladder/climbable/vine tests, so it is not a claim of a completely green full standalone suite.

Logs are archived under `evidence/`. No mod Gradle suite, loader build or game client was required. Exact executable-angle/actual-world replay remains a separate validation tier.

Next work should measure continued frontier coverage and quality against the saved reference, especially the zero-run root alternative, and profile symbolic generation plus state serialization. No new bound, score weights or blind cap increase follows from these results. A later map can still refute weak coupling between decisions, and all retained work remains available for that investigation.


[Final generation measurement](evidence/generation-current.json): all three streams exhaust, in0.58/0.84/0.81s, yielding162/235/11 distinct row sets (338/504/56 resource-distinct representatives). These are complete current streams over the archived physical contexts, not estimates of all route timings. [Validation metadata](evidence/validation.json) pins test scope/runtime; [calls](CALLS.md) distinguishes stored requests from older hashes/context records.
