# Anchor search and solver integration

Current execution tables and policy/rationale: [execution reference](ANCHOR_SEARCH_EXECUTION_REFERENCE.md). The later `dd738c98` feature generalizes ordinary run-family deferral to protected hard-anchor tick comparisons, shares proposal preparation and adds ceiling-aware flight minima under the declared no-stepping transfer assumption. [Current evidence](../autonomy/experiments/hard-jump-comparisons/README.md) supersedes this integration baseline's counts. Integration is complete; the measurements below retain their source-era scope.

2026-11-10. This implements the user's authorization following the
[nine-commit review](SOLVER_BRANCH_INTEGRATION_REVIEW.md). The working checkout
is `/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes`, branch
`research/ipopt-cell-routes`. Both development histories were joined here;
the old isolated anchor worktree is historical, not the current work location.
The implementation and acceptance checks are complete. The feature commit
bundles both development histories, implementation, tests, documentation and
durable evidence.

## Solver changes selected from the review

Numerical row reduction and corrected simple-bound extraction are retained.
Original requests, row provenance and independent parent validation remain
intact. Stronger absolute X/Z bounds replace weaker numerical copies; angular,
relative and equality restrictions retain their distinct semantics.

Early stopping and the numerical objective are independent. The default is
`objectiveMode: configured`, including anchor/node calls that stop at the first
validated feasible witness. Their backward guides and onward progress objective
remain active before stopping. Final calls optimize the actual objective at the
discovered finish time. An explicitly requested `objectiveMode: constant_zero`
remains available for API diagnostics, requires feasible stopping, and is not
the anchor default. Original objective values remain measurements in either
mode. Numerical controls do not change the physical request fingerprint;
orchestrator solve-policy identities control repair reuse.

Shared checkpoint storage now covers automatic anchor/event searches as well
as the legacy fixed-clock runner. `SearchCheckpoint` understands nested outer
event state. `begin_repair` captures the actual active program and additional
mutable node-cache entries. Repair callbacks patch those aliases plus the
cumulative call count against a frozen dispatch root. Anchor score callbacks
and internal-node callbacks have separate ownership but the same storage
contract. Automatic CLI resume reads legacy JSON and shared-store reports;
manifest/database companions must be kept together.

## The three coordinated search changes

The later lazy-bound follow-up keeps the same pruning rule but evaluates it only
for the candidate currently being considered. Arrival selection and refinement
selection no longer call the bound while filtering every queued record. After
choosing a candidate using the existing priority, compare its optimistic finish
with the current best finish and discard it if strictly later. Equal finishes
remain eligible. An excluded fixed refinement is marked so future slices do not
retry it; the saved program and exclusion proof remain available. New proposals
are still checked when examined during generation. There is no queue-wide
time-bound refresh when the incumbent changes.

This follow-up passes [51 focused checks](../autonomy/experiments/anchor-runtime-profile/lazy-bounds-2026-11-10/report.json),
including assertions that unselected arrivals/refinements are not checked and
equal optimistic finishes remain eligible. The571-test gate and300s profile below
describe integration revision `91381e5a`; they have not been rerun to claim a new
timing result for this follow-up. Checkpoint shape and the bound formula are
unchanged.

### Ordinary extra runs versus explicit recovery

Exhausting a generator no longer automatically restores saved greater-run
preparations. A full validated finish marks its actual ancestor stack as
successful, suppressing those ordinary post-success restorations. A failed
required descendant can request recovery after an exact oracle rejection;
this is a request to change preparation, not an infeasibility label on its
feasible ancestors. Numerical unknowns alone do not request recovery.

Recovery restores one saved preparation at a time and consumes its failure
reason. Another restoration requires another explicit failure. Each restoration
records the responsible failed frame/candidate. Deferred programs remain saved
for audit/reconstruction, and restored programs do not immediately defer
themselves again. Hard-transfer latches remain global. Same-source setup hops
remain legal; recovery cannot add extra jumps to the actual S→E transfer.

### Elapsed and optimistic remaining time

At candidate admission, reject when elapsed fixed inputs exceed the best
validated full finish. Equality stays eligible, including finished candidates
that may improve Z. Recheck queued arrivals and refinements against an improved
incumbent rather than spending a solve on stale work.

For required-anchor tasks, add an optimistic remaining-time bound. Traverse the
remaining ordered anchors to the terminal anchor. Guide-to-guide distance is
reported as an estimate; pruning uses minimum horizontal distances between the
full permitted landing covers behind those guides. Sum those relaxed distances
and divide by the existing shared per-input movement upper bound, rounding up
with numerical clearance. Independently minimizing neighboring segment endpoints
only relaxes the path. The speed bound allows the fastest admitted momentum;
it never resets at an anchor or freezes a solved endpoint/velocity.

If the current decision is airborne, omit its uncertain approach segment and
start the distance sum at the next required anchor. Unknown movement bounds or
missing covers contribute zero rather than an unsupported exclusion. A completed
terminal arrival has zero remaining distance. Reject only when
`elapsed + optimistic remaining > incumbent`, preserving equality. This is a
horizontal relaxation: vertical flight durations, obstacles and turns can make
the actual continuation longer. It is not a proof that an admitted route works.

### Live state ownership and profiling

The event allocator owns its live DFS state and passes it directly between
internal service slices. It no longer deep-copies the entire accumulated tree
or independently replays the same incumbent before each slice. Public external
resume still copies caller-owned state once and independently replays the saved
incumbent. Historical window snapshots retain their own copy boundaries.

The first profiled attempt exposed a second source of wasted service slices:
an unresolved-only internal-node query was suspended, but retried automatically
with unknown retries disabled. No solver call or expansion could result. Such
nodes are now saved in the movement source's `deferredNodes`, allowing runnable
siblings to proceed. Their exact decision/parent/repair state remains available.
An explicit `retry_unknown` restores them; an interrupted query with remaining
runnable repair work still resumes in place. These deferrals are not oracle
rejections and do not invalidate ancestors. The report counts them separately.

The scoped profiling harness is in
`autonomy/experiments/anchor-runtime-profile/`. It records inclusive/exclusive
wall times for copies, generator advancement, hashing, solver invocation,
evaluation, callbacks and replay, plus per-slice work/budget statistics and
overlapping GC. Profiling does not enter resume identity or search snapshots.
Full-report serialization is measured separately after the search. Source hashes,
the staged-plus-unstaged Python patch and copied instrumentation source identify
the tested implementation while a merge commit is pending.

The earlier external profile measured205.65s of304.77s in whole-DFS resume
copies. Its temporary raw outputs were lost when the execution environment
changed; that number remains a previously observed diagnostic, not a new
reproduced baseline. New acceptance outputs live under the experiment directory
so they survive the environment reset. The new combined run still ends at67;
it demonstrates greater coverage and eliminates empty copying/retry slices,
not combined T64 discovery or a global optimality certificate.

## Compatibility and validation

The anchor semantic fingerprint includes search policy version4. Old-policy
exact checkpoints are rejected rather than silently accepting already-pruned
queues. State schemas remain unchanged; storage format version1 is separate.
Changing stop/rank policy also resets incompatible constraint-generation work.

Focused checks cover successful-finish/no-restoration, explicit-failure recovery,
unknown/no-recovery, multiple source hops under a learned latch, external resume
isolation, equal-time improvement, wide/diagonal/translated landing regions,
airborne uncertainty, explicit pure versus directional stopping, nested node
repair equality, native checkpoint publications and ordinary legacy resumes.
The broader gate additionally runs the recorded non-ladder autonomy scope and
all IPOPT tests. See the experiment README for fresh counts and cold outcomes.

Final gate:571 tests pass with zero failures/errors/skips,248.27s,105 runtime
hashes unchanged and matching the cold run, historical event artifacts unchanged.
The five profiler tests also pass. The300.06s combined run finds67 after15.17s,
expands1057 prefixes, launches149 workers and passes71/71 accepted-witness scene
replays. It restores zero extra runs, retains five unresolved nodes and has zero
empty service slices. Initial0's source setup remains eligible and queued;
strict DFS is still exploring the initial8 subtree at the cutoff.

Numerical feasibility, full supplied-scene replay and executable/game replay
remain distinct. No input/domain cap was widened. Inter-block revisits and
intentional wall-hit generation remain deferred.
