# Why collision-avoidance enumeration visits millions of entries

2026-11-15. Follow-up to the [bookkeeping measurements](../bookkeeping-measurement/README.md),
on `research/bookkeeping-performance` from `78ef575b`. This is diagnosis and a
fixed-work option control, with **no production/search-policy changes**.

## Result

The completed 300-second unrestricted Real11 control examines **15,183,845**
partial entries. **14,340,595 (94.4%)** immediately hit the existing internal
`node_seen` duplicate check. **B7 accounts for 14,653,995 visits (96.5%)**;
B6 accounts for the remaining 529,850. Earlier blocks do not enter this
side-chain enumeration in this control.

This makes the primary cause concrete: different earlier switch schedules
repeatedly generate the same internal remaining problem. Deduplication happens
after those children have been constructed, ranked, queued, popped, rebuilt
into accumulated rows and hashed. The ratio is not explained by millions of
new physical routes subsequently rejected by the numerical solver.

| Immediate outcome of a visited entry | Count |
| --- | ---: |
| Already-seen internal state | 14,340,595 |
| Expanded into further switch-time choices | 489,541 |
| Final side cannot cover the remaining mask | 175,134 |
| Complete option repeats an existing effective-rows/resource key | 150,818 |
| A preceding side already guarantees remaining coverage | 26,613 |
| Returned a local obstacle option | 1,144 |
| Previous-Y terminal redundancy / passage compilation failure | 0 / 0 |

These outcomes sum exactly to the visit count. Returned options are local to a
block/cursor/context, not distinct full routes. Subsequent combination and
repair checks remain separate. This run still makes 61 numerical invocations,
55 worker launches and six feasible seed returns, with 39 prefix expansions.

There are **15,178,691 generated children**, of which **6,647,816** have `None`
in the prepared suffix table at their next depth/start. Those entries receive
infinite rank but are still enqueued by the unchanged code. This is an
overlapping observation, not an additional disjoint rejection category or an
implemented pruning rule. Of all visits, 13,513,816 have an empty accumulated
effective row key.

## A captured B7 example

In context `1e5c61ec...`, through state 64, B7 needs X rows only at states
56–57 and Z rows at 55–57. Switch scheduling still ranges over the whole prefix.
The following two captured schedules use the same chain:

```text
X- -> Y- -> X+ -> Z-

switch gates [0, 1, 53]
switch gates [0, 2, 53]
```

Both are at depth 3, starting their final `Z-` segment at state 53, with the
same empty accumulated effective row key (`SHA256("[]")`). Their chain,
depth, start and effective constraints—the complete existing internal key—are
identical. Changing the early gate from 1 to 2 adds no constraint at B7's
relevant states. One is an already-seen state when reached again.

`X-` means the low-X side of the expanded collision box, `X+` the high-X side,
and similarly for Y/Z. These are avoidance-certificate labels, not prescribed
movement or camera inputs. A nominal `Y-` segment far from the block may impose
no actual condition because no interaction is possible there. All indices above
are the solver's zero-based state/gate indices.

The largest aggregated chain families include `Y- -> X+ -> Z- -> X-`,
`Y- -> X+ -> Z- -> Y-` and `Y- -> X+ -> Z- -> Y+`, each around 104–106 thousand
visits across contexts. Work is spread over many analogous chains; it is not
one single bad side sequence. B7 is the decisive block-level hotspot.

## Observation method and fixed-work control

[The enumeration observer](../../tools/profiling/enumeration.py) compiles a
private AST copy of `ConstraintVariants.option`, adding counters at existing
branches. Production files/globals remain untouched. It retains the original
conditions, keys, branch order, cursor operations and returns. It asserts the
expected insertion-point shape and records the original source SHA256. Class
and evaluation hooks are restored on normal or exceptional exit.

Counters aggregate by exact iterator context, solid index and side chain,
including repeated evaluations. Metadata records relevant states and evaluation
indices. Examples/samples are bounded, as are the context/chain report rows.
Prepared option captures are capped at four cases and 2 MiB per case. These
diagnostics never enter a repair snapshot or checkpoint identity.

The [fixed-work option replay](../../tools/profiling/enumeration_calibrate.py)
uses four captured prepared items: B6 through 57/58 and B7 through 67/64.
Each starts from a fresh cursor with the same 20,000 check allowance. Three
repetitions alternate unwrapped/observed order. **Every exact output/cursor/item/
duplicate-count hash matches**, including interrupted frontiers, row order and
option order. Native startup and item preparation are excluded.

| Captured case | Visits | Already-seen states | Returned options | Unwrapped / observed median seconds |
| --- | ---: | ---: | ---: | ---: |
| B6 / through 57 | 19,983 | 18,339 | 6 | 0.238 / 0.247 |
| B7 / through 67 | 19,981 | 18,121 | 14 | 0.246 / 0.258 |
| B6 / through 58 | 19,983 | 18,370 | 12 | 0.244 / 0.254 |
| B7 / through 64 | 19,979 | 17,999 | 15 | 0.216 / 0.225 |

The observations add roughly 4–5% to these fixed-work option timings. This
calibrates the branch counters alone; the cold mode also uses the previously
measured bookkeeping hooks. It is not a production speedup measurement. Timed
cold runs can perform different amounts of internal work.

The cold control finds T64 / Z=-1093.2381026602416 and independently replays
all 57 retained witnesses. Production source hashes remain unchanged. A first
diagnostic attempt hit its 8,192-row reporting cap before completion; its
partial counters are preserved as an excluded attempt, not cold acceptance.
Repeated contexts are now aggregated, giving 1,387 report rows for 53 evaluations
in the completed run, below the explicit 16,384-row cap.

## Evidence and reproduction

The [evidence index](evidence/2026-11-15/index.json) records raw-run hashes,
source/fixture/compiler provenance, compressed exact counters and prepared
cases, the fixed-work report and selected concrete examples. Large resumable
report/SQLite pairs remain together under the recorded ignored `runs/` paths.
Previous bookkeeping evidence and its protected hashes are unchanged.

From this worktree's `standalone/`, with its own runbook native environment:

```sh
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run \
  --input cases/real11/physical-anchors.json --output /new/path/enumeration \
  --seconds 300 --profile-mode enumeration --sample-every 4096 --sample-limit 16384
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.enumeration_calibrate \
  --cases /new/path/enumeration/option-cases.json --output /new/path/fixed-work.json \
  --checks 20000 --repeats 3
```

The calibration command also reads the curated `.json.gz` cases directly.

The recorded native gate passes **622 tests**, with no failures/errors/skips:
the previous 617 plus five enumeration-observer tests. Its existing dedicated
ladder/climbable/vine/movement-entry exclusions remain unchanged, and generic
mixed-ladder controls remain included. Tests cover exact options/cursors,
JSON interruption state, repeated-context aggregation, source-shape guards and
hook/global restoration after failure. Layout validation preserves all 902
protected artifacts and all 131 production source hashes still match the
starting baseline.

The implementation target is now earlier suppression of identical remaining
states and avoidance of irrelevant switch-time distinctions, with the same
ordered unique options, resource counts, exact rows and resume ownership. A
state marked seen on enqueue can keep a different earlier gate history than
the current first-popped DFS representative; an early-dedup implementation must
preserve that representative's observable passages/owners and ordering. A
suffix-table exclusion needs its own coverage/continuation proof. This report
does not implement either change, and the option-only replay does not replace
the handoff's full continuation/oracle benchmark or acceptance gates.
