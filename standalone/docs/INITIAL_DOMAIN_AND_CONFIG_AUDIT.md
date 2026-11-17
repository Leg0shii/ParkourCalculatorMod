# Initial-delay selection and remaining supplied policies

The first-jump whitelist in the original Real11 fixture is a domain restriction,
not the output of the run-tick heuristic. `search.application.run` passes the
optional `initialJumpInputs` field to `search.domain.InitialJumpDomain`; omission
already disables that restriction. The experiment in
[initial-domain-selection](../research/initial-domain-selection/README.md)
removes only that restriction and records which first-anchor choices actually
receive feasibility/anchor queries.

## How the existing heuristic selects work

1. Producers propose physically relevant jumps, source-local setup hops and
   one-input ground holds. Shared reachability screens impossible proposals.
2. A validated single transfer latches the anchor span as hard. Setup hops on
   the starting anchor remain allowed.
3. At a comparable complete hard-anchor arrival, shared T/J/R/A comparison can
   defer a strictly slower arrival unless more J or fewer R (ordinary) / fewer A
   (turnaround) protects it. Deferred programs remain available for explicit
   feasibility recovery.
4. Open prefixes do not borrow a future arrival's counts. Meaningful selected
   setup decisions can get their own feasibility query.
5. Once a validated complete finish exists, the strict optimistic finish bound
   also rejects new/selected work; equal finish time survives.

Consequently, the expectation of first attempts at0 and8 must be verified against
actual queries. It is not correct to reproduce the heuristic by embedding a new
`[0,8]` rule. Generated descriptors, queried internal nodes, queried arrivals and
recoverably deferred arrivals are different categories.

## What remains supplied, inferred or fixed

| Item | Current source / meaning | Intended treatment |
| --- | --- | --- |
| Initial first-jump list | Original case supplies `[0,8]`; omission permits selection by the orchestrator. | Removed from the maintained case after the unrestricted control verified behavior; archived fixtures preserved. |
| Required anchors | Caller supplies B2/B3/B4/B5; start and finish are implicit endpoints. | Intentional task input. Optional contacts and their order within each span are movement decisions. |
| Geometry and terminal objective | Supplied block AABBs, original state, B7 landing, minT then MAXZ; B7 intentionally extended and B6 still stored geometry. | Task definition, not a discovered fact. Updating B6 needs actual replacement bounds. No fixed finish64 or terminal Z cap is imposed. |
| Enabled movement / inputs | Real11 enables normal+stepping and sprint/Force45; the current automatic backend supports normal/stepping/ceiling families. | Explicit movement domain. Arbitrary intentional wall-hit and ladder expansion are outside this backend. |
| Hard status and flight clocks | Hard latches follow validated transfers. Flight durations derive from scalar physics/heights and ceiling eligibility. | Inferred; no authored Real11 hard flags,9/10/15 clocks or support itinerary beyond declared anchors. |
| Soft guides | Terminal objective edge/midpoint, then backward nearest projections on exposed landing covers; onward objective at anchors. | Generic heuristic, not authored Real11 guide points or containment constraints. |
| Turnaround |110° guide cue and0.3-block approach-edge margin; one binary mode. | Agreed configurable heuristic. No continuous/slack score or mixed mode. |
| Feasibility cadence | Selected meaningful decisions at cadence1; anchors/final mandatory. | Configurable strategy. No NLP per raw run input or separate ancestor probe. |
| Model / recovery policy | Zero inertia threshold while carrying momentum; permanent hard latches, no stepping in hard transfer, no departed-owner returns; saved slower alternatives recover on explicit failure. | Agreed domain/search policies, not Real11-specific constants. Same-owner hops remain supported. |
| Budgets and caps |200 inputs,64 total/run-per-jump caps, wall/call/expansion/service quotas in the case. | Explicit limits, not physical impossibility proofs. Some retained legacy API fields schedule service or apply only to compatibility paths; they are not all beam-eviction rules. |
| Internal parser placeholder | `scene.normalize.prepare_scene` supplies `[12]` airtimes temporarily when calling the ordered-route validator. Only normalized inputs/initial/support clearance are used from that result. | Existing schema-reuse coupling; it does **not** set the search's flight or comparison clocks. A future parser cleanup can remove the dummy field without changing physics. |
| Numerical tolerances and physical constants | Existing shared models/builders and native compiler. | Model/acceptance definitions, not route hints. Reuse their owners rather than duplicate values. |

## What the CI gap means

The existing repository build workflow runs Java/mod checks and does not invoke
standalone Python layout/package checks. Its push trigger is main/dev; pull
requests run the existing mod jobs. A research-branch push does not itself prove
standalone verification, and a passing mod build does not verify the Python
package.

The standalone layout/import/package checks currently run when a developer or
agent follows the documented local workflow. If a later change adds a forbidden
production import, breaks installation or introduces test-to-test coupling, GitHub
will not automatically catch that through a standalone job. The610-test record
validates its saved source snapshot, not future revisions.

A dedicated standalone CI job could install the package and run the fast
structural/docs/package checks on relevant changes. Worker/native checks require
their own dependencies and regenerated compiler descriptor; they should not rely
on this machine's ignored build files. No workflow, branch protection or remote
settings are changed by this investigation.
