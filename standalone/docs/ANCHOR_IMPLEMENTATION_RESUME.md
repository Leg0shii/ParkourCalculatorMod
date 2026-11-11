# Anchor search: current resume state

2026-11-11. Runtime feature `dd738c98` is complete. Continue in the checkout where
the session starts; this session uses `research/ipopt-cell-routes` in
`/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes`. Old private-worktree
paths in historical plans do not authorize switching checkouts. Preserve unrelated
edits, including this session's pre-existing `AGENTS.md` and `docs/CODE_MAP.md` changes.

## Read first

1. [Execution reference](ANCHOR_SEARCH_EXECUTION_REFERENCE.md): generated combinations,
   all rejection/deferral/selection/solver boundaries, decisions and rationale,
   implementation owners, current fixture and measured counts.
2. [Current state](CURRENT_STATE.md), [decisions](DECISIONS_AND_PLANS.md), and the
   [completed implementation record](HARD_JUMP_TICK_PRUNING_AND_REACHABILITY_PLAN.md).
3. [Evidence](../autonomy/experiments/hard-jump-comparisons/README.md),
   [architecture](ARCHITECTURE.md), [runbook](RUNBOOK.md) and [testing](TESTING.md).

## Settled implementation

Hard means one S→E transfer, with repeated source-local setup hops still eligible.
Global hard policy3 explicitly assumes no stepping in the actual transfer; only
ceilings can shorten its conditional ordinary-flight bound. Stepped generic
controls remain valid outside that transfer domain. Required anchors are ordered;
inter-block returns/repeated mandatory anchors remain deferred.

One common intermediate comparator owns absolute T/local requested J/R/A,
ordinary T/−J/R and turnaround T/−J/A, more-J OR fewer-R/fewer-A protections,
recoverable slower-peer deferral and strict optimistic finish checks. Equality
stays live. Open prefixes do not inherit speculative future arrival counts.
There is one full incumbent and no queued-bound rescan on its update.

Shared proposal analysis rejects impossible lower/higher conjunctions before
side/corner fan-out, caches exact preparation/coefficient/envelope contexts,
keeps stable consumed raw ordinals and preserves a valid owned anchor prefix
independently of its unused tail. Full-finish bound is elapsed+max(H,V), with
phase-correct covers, latched conditional flight minima and paid-input residuals.

Selected meaningful decisions use exact-node feasibility at cadence1; anchors/final
always query. Intermediate objective is configured onward progress with early stop,
not constant zero or slack. Final objective is actual physical landing minT→MAXZ.
Every later solve retains original-state free facings. Raw holds do not launch
NLPs, and a failed child never invalidates an unqueried ancestor.

Backward guides and the >110° approach-edge+0.3-block classifier select one binary
mode per decision. Diagnostics record point/edge/margin/distances/key and replay
flips without changing queued keys. Recovery restores one saved unprotected
alternative on definite failed-continuation backtracking, with scoped bypass;
a successful finish alone does not reopen it.

Physical state: outer schema2 / DFS8 / movement context4 / repair assembly9, plus
hard3/search5/comparison1/guide2/proposal1 semantic fingerprints. Storage version1
is unchanged. Old wrong-policy exact checkpoints reject; internal owned state and
external isolated decode remain separate.

## Acceptance and remaining work

600 native-enabled tests pass without failures/errors/skips;131 archived valid
prefixes pass current bounds; all80 retained-witness replays across the updated
arms pass with unchanged runtime hashes. Combined300s improves T67→T64,
workers159→55, expansions1,114→35. Initial0-only90s retains T64 with workers56→22
and expansions427→7. Both reach Z=−1093.2381026602416. These are bounded
source-specific runs, not minimum-time/global-objective or actual-world certificates.

Real11 uses declared anchors B2/B3/B4/B5, initial0/8, horizon200 and extended
physical B7 `[729,0,-1095,730,7,-1093]`. No fixed64, virtual +Z goal or extra
X<729.7 row. B6 remains stored geometry; replacement extension bounds are missing.

No feature implementation or solver/test process remains pending. Do not restart
old M1 lists. Revisit generation has a [deferred plan](../autonomy/REVISIT_DISCOVERY_DEFERRED_PLAN.md).
Intentional wall hits, cadence/turn tuning and measured remaining bookkeeping
optimization are separate future work. Use fresh evidence output paths if asked
to run another experiment; keep manifest/SQLite companions together.
