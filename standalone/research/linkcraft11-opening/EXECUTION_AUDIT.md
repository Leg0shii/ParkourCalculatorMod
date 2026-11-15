# What actually happened in the Real11 opening experiment

Follow-up: [controlled investigation of the four unknown searches](OTHER_FAILURES.md) adds generation-only exact reproduction, valid missed-continuation checks, inherited repair-queue diagnosis and a same-budget successful ordering control. It also quantifies the final-state resumption gap. The newer evidence refines earlier broad budget/generation descriptions below.

2026-11-02. Reconstructed from the saved reports and the executed source, without new numerical solves. This supersedes the earlier interpretation that the work demonstrates an inherently difficult local-decision problem on this map.

Latest user decision: the immediate-jump0 and run-then-jump8 setups are both meaningful alternatives and must both be retained. The no-run-only interpretation below records the preceding discussion, not authority to discard the8 setup going forward. Keep these two explicit setup families while avoiding unrelated generic enumeration. The measured crossover is documented in the next section.

Latest comparison refinement: the user selects time/progress with both position and velocity in the fixed-clock state assessment. The original state10-only witnesses have forward velocities0.226887836 for0 and0.263399323 for8, so8 wins both horizontal position and velocity there. Preserving both still requires their different jump/contact phase or explicit setup-family protection:0 is descending toward its next landing, while8 is ascending. Full X/Z position and velocity must be recorded from the same accepted witness. [Refined frontier design](../../TIME_PROGRESS_FRONTIER.md#position-and-velocity-at-the-common-clock). This is a documented design direction; no retention implementation or weighted score has changed this turn.

Subsequent event-alignment question: use the sameB2 landing event at different absolute times, carrying arrival time together with p/v. In the found complete routes, immediate0 arrives21 with outgoing-to-B3 velocity0.265297; delayed8 arrives17 with0.242594. Immediate0 is1.098933 blocks farther along theB2-to-B3 direction at its own arrival, but pays4ticks. That tradeoff is hidden by the state10-only ranking. Use a consistently resolved contact state, preferably after landing before the next input, and compare the same block, not each setup's different next landing. These are conditional64-optimized witness states, not independently optimal event-state frontiers. The [proposed event-arrival labels](../../TIME_PROGRESS_FRONTIER.md#compare-arrival-states-at-a-shared-spatial-event) supplement common-clock labels and need downstream continuation evidence to choose a winner. Numerical states are never frozen as splice starts. [Exact event samples](audit/event-aligned-b2.json). No new solve or runtime change.

## When does the immediate-jump advantage appear?

For the two saved complete trajectories, optimized for maximumZ at64, the jump0 setup first regains the lead in the original route-directed progress measure at **absolute state23**,13 ticks after the state10 comparison. It remains ahead in that measure through64. For pureZ the first regained lead is state28. These are different metrics; neither is a new combined score.

| Absolute state | Route-directed progress advantage of jump0 over jump8 |
| --- | ---: |
| 10 | -0.357413 |
| 22 | -0.030846 |
| 23 | +0.020670 |
| 28 | +0.218158 |
| 40 | +0.674760 |
| 55 | +1.308083 |

The arrival-time comparison changes more slowly:

| First arrival at support | Jump0 setup | Jump8 setup | Jump0 timing difference |
| --- | ---: | ---: | --- |
| B2 | 21 | 17 | 4 later |
| B3 | 30 | 28 | 2 later |
| B4 | 40 | 40 | tied |
| B5 | 55 | 58 | 3 earlier |

Thus the tested jump0 continuation shows a position-proxy benefit by23, catches up in support-arrival time at40, and first leads on these shared support goals atB5. At64 itsZ advantage is1.312030 blocks. Merely waiting until the nextB2 landing would still favor the other setup.

![Observed setup advantage over time](audit/setup-crossover.png)

Measurement limits: these samples come from the two **complete** accepted solutions with all facings optimized forZ at64, not new independently optimized prefix solves at every earlier clock. The earlier state10-only solves have a0.328407 gap; the full-route solutions have a0.357413 gap at10 because the earlier facings were reoptimized for completion. Both use the same absolute ticks, anchor and direction. The jump8 continuation also has additional later ground inputs, so23 is an observed crossover for these particular discrete continuations, not a certified horizon at which the globally best jump0 value exceeds the globally best jump8 value. Other later choices can move that crossover. This is sufficient evidence to retain both setup families, not to declare one safely dominated.

Exact samples and first-arrival extraction are saved in [setup-crossover.json](audit/setup-crossover.json) and [setup-crossover.csv](audit/setup-crossover.csv), generated by [measure_setup_crossover.py](measure_setup_crossover.py). Arrival means the first contact with that support, not a later repeated run contact before the next jump. No new numerical solve was performed for this measurement. Runtime retention has not been modified by this documentation turn; the user decision is a requirement for the next search change/run.

**The experiment searched a broader problem than the user intended.** The intended case has no extra run ticks and one meaningful local choice: when/how to pass the B6 blockage on the otherwise fixed jump chain. Instead, the experiment allowed ground-running, different initial takeoff times, repeated visits/jumps on a support, open-air continuations and terminal jump/run-off variants. It compared those prefixes at four manually chosen clocks before reaching the B6 decision. Much of the cost and the observed early-history ranking failure concerns those additional choices. It does not establish that the intended one-decision solve needs a large beam, multiple early commitments or a sophisticated score.

The final accepted candidate itself has **zero grounded nonjump inputs**, with jump inputs[0,12,21,30,40,55]. Its initial12-input B1-to-B1 hop is part of that accepted chain. The issue is the much larger set of alternatives searched around it, not that the reported winner secretly contains run ticks. The first-jump8/9 alternatives do contain run ticks and are outside the user's clarified intended comparison.

## Evidence and limits of this reconstruction

The [candidate appendix](audit/CANDIDATES.md) lists every evaluated prefix in the final main cold run, including exact jump ticks, ground inputs, contacts, scores, call counts and retention decisions. [candidate-ledger.csv](audit/candidate-ledger.csv) contains306 outer evaluated records across all four main/resumed runs and their rescues. [candidate-programs.json.gz](audit/candidate-programs.json.gz) preserves their exact symbolic programs, carried states and accepted witnesses. Same jump lists can have different contact/avoid rows; the JSON resolves that distinction.

[cells-and-route.json](audit/cells-and-route.json) contains every cell, portal, contact patch and the prescribed corridor. [detailed-evaluations.csv](audit/detailed-evaluations.csv) lists the29 constructive/terminal evaluations. [repair-attempts.csv](audit/repair-attempts.csv) lists214 recorded inner branch attempts for those evaluations. [winning-fixed-call.json](audit/winning-fixed-call.json) gives the exact accepted route,70 compiled scalar walls, finite contacts, profile, objective, selected repair rows and problem hash. These files are generated by [audit_trace.py](audit_trace.py), which asserts numerical-call accounting against each saved main-run total.

There is a real logging gap. Main checkpoint records save their symbolic program, physical witness if accepted, work counts and repair frontier, but discard the ordered `contactAttempts`/inner numerical attempt lists. Spatial probes save each attempted completion's time/status/call count and the best program, but not every unsuccessful completion's full program. For detailed probes, branch keys preserve all unique offered row/face/hit sets, but do not map each key to chronological pop order or its numerical problem hash. See [detailed-repair-branches.json.gz](audit/detailed-repair-branches.json.gz). Thus the exact outer combinations, retained/dropped histories, accepted calls and saved branch sets are recoverable; an exact chronological full-constraint transcript of every past IPOPT call is not. That information was not recorded. No missing combinations or prune identities are invented here.

Counting unit: `solverCalls` measures outer invocations of `standalone.ipopt.solve`, not individual internal IPOPT starts or iterations. At threshold0 that worker can try both initial and fallback facing seeds for the same fixed continuous problem. Those seed trials do not change the discrete inputs/constraints. The saved outer diagnostics do not retain enough phase data to count every internal start. All numerical-call totals below use this recorded request counter consistently.

## 1. Cells: what was actually constructed

The decomposition starts with the declared physical scene coverage, shrinks it for the player body, subtracts player-expanded solids and merges adjacent axis-aligned free boxes. It also keeps lower-dimensional free boundary pieces. It is not a uniform grid, nor a partition into one node per tick or per jump.

Actual result: **33 cells:30 volumes, two faces and one edge;92 portals;247 contact patches**. [All33 cells and their bounds](audit/CELLS.md) are listed separately. The following11 cells intersect the hand-supplied guide. Bounds below are rounded; the JSON retains exact float constants.

| Cell | X interval | FeetY interval | Z interval | Role along the guide |
| --- | --- | --- | --- | --- |
| C31 | [742.3,747.3] | [5,22.2] | [-1107.7,-1085.3] | start/B1 and air above it |
| C29 | [741.7,742.3] | [5,6] | [-1107.7,-1104.3] | B1 corner overhang arm |
| C26 | [740.7,742.3] | [6,22.2] | [-1107.7,-1085.3] | air above B2 |
| C22 | [739.3,740.7] | [-200,22.2] | [-1107.7,-1085.3] | gap toward B3 |
| C21 | [737.7,739.3] | [7,22.2] | [-1107.7,-1085.3] | air above B3 |
| C17 | [735.3,737.7] | [-200,22.2] | [-1107.7,-1085.3] | gap toward B4 |
| C16 | [733.7,735.3] | [7.5,22.2] | [-1107.7,-1085.3] | air above B4 |
| C12 | [731.3,733.7] | [-200,22.2] | [-1107.7,-1085.3] | gap toward B5 |
| C8 | [729.7,731.3] | [6,20] | [-1107.7,-1095.3] | B5/front side of B6 |
| C2 | [728.7,729.7] | [-1.8,7] | [-1107.7,-1095.3] | left of B6, before B7 |
| C4 | [728.7,729.7] | [7,22.2] | [-1107.7,-1085.3] | left of B6, above B7 |

These cells have no traversal time or fixed tick attached. They allow many ticks in one cell. Physical support constraints remain tied to the actual block and collision phase, not merely to membership in the cell above that block. B1's corner union is preserved in C29/C31, but the winning trajectory does not use C29.

## 2. Spatial routes: one prescribed corridor, no route enumeration

Exactly **one** spatial corridor was supplied. `routeGeneration=null`, `corridor.source='prescribed'`, `variants=1`; the automatic route enumerator was not run. No left-versus-right route comparison, shortest-path route search or branch-and-bound over spatial routes happened.

The polyline at `fixtures/clock64-event-priority.json:spatialPoints` was authored from geometry: B1 corner, above B2/B3/B4/B5, left of B6, then +Z beyond B7. It produces the ordered guide cell itinerary:

```text
C31 -> C29 -> C26 -> C22 -> C21 -> C17 -> C16 -> C12 -> C8 -> C2 -> C4
```

The corridor stores an empty `portals` list; this run does not enumerate portal transition choices. The graph has92 portals, but they were not converted into a local decision list. Separately, a support-order filter requires B2 -> B3 -> B4 -> B5. It permits repeated contacts with the current support, so it does not enforce immediate jumping/no run ticks.

Actual route enforcement is weaker than exact visitation of the above3D cell sequence. It uses the corridor's horizontal projection plus ordered landmark crossings; prefix filtering also reasons with horizontal intervals. The four projected rectangles are:

| Projection | X | Z |
| --- | --- | --- |
| P0 | [728.9,729.7] | [-1105.3,-1088] |
| P1 | [728.9,731.3] | [-1105.3,-1095.3] |
| P2 | [731.3,747.3] | [-1105.3,-1088] |
| P3 | [741.7,742.3] | [-1105.3,-1104.3] |

Landmarks are X planes through block centers. The required crossing order is B1, B2, B3, B4, B5, B6, all toward decreasingX. At B6's X730.5 plane the guide requires crossing on the Z<=-1095.3 side. These are geometric ordering markers, not physical wall hits or a complete set of B6 clearance inequalities. Crossing markers at earlier blocks also introduce repair constraints, despite those blocks not being meaningful tactical decisions in the intended solve.

For comparison, the winner's actual state-center cells are:

```text
states 0..15:C31; 16..20:C26; 21:C25/C26 boundary;
22..25:C22; 26..30:C21; 31..38:C17; 39..42:C16;
43..51:C12; 52..57:C8; 58..63:C4; 64:C3.
```

C3 is outside the guide's selected cell list. It is nevertheless allowed by the corridor's horizontal projection once the airborne player goes beyond B7 and descends belowY7. This illustrates the actual corridor contract, not an unreported extra enumerated spatial route. Finite-scene collision checking still applies. Exact visits are in [winning-cell-visits.json](audit/winning-cell-visits.json).

### Direction extraction versus directions actually used

For a polyline segment between shared guide waypoints A,B, the existing `horizontal_segments` helper computes `d=normalize(B_XZ-A_XZ)`, skips vertical segments and merges collinear horizontal ones. The cell sequence alone has no unique tangent; the representative points supply it. This guide is hand-authored. The automatic route enumerator, which was not used here, builds its own representative polyline through portal midpoints. These are geometric progress references, not fixed player facings or necessarily the optimum center line through the cells.

The actual seven horizontal directions of the saved guide are:

| Segment | Start X/Z | End X/Z | Unit direction(X,Z) |
| --- | --- | --- | --- |
| start toward B1 corner | [746.5,-1104.5] | [742.1,-1104.8] | [-0.997684,-0.068024] |
| B1 corner toward B2 | [742.1,-1104.8] | [741.5,-1103.5] | [-0.419058,+0.907959] |
| B2 toward B3 | [741.5,-1103.5] | [738.5,-1100.5] | [-0.707107,+0.707107] |
| B3 toward B4 | [738.5,-1100.5] | [734.5,-1099.5] | [-0.970143,+0.242536] |
| B4 toward B5 approach | [734.5,-1099.5] | [730.5,-1095.7] | [-0.724999,+0.688749] |
| B5 toward B6's left side | [730.5,-1095.7] | [728.9,-1095.7] | [-1,0] |
| after the B6 turn toward +Z | [728.9,-1095.7] | [728.9,-1088] | [0,+1] |

The last entry merges the two collinear +Z pieces separated by the B7 reference point. Vertical guide movement between entries supplies no horizontal direction. B5's approach reference is offset from its physical center for clearance; center-to-centerB4->B5 would instead yield[-0.707107,+0.707107]. Do not silently mix those two conventions.

The fixed-clock experiment explicitly constructs different scoring directions rather than selecting the above segment for each clock:

| Actual score clock | Construction | Direction(X,Z) |
| --- | --- | --- |
| 10,28,44 | original start[746.5,-1104.5] to physical B5 center[730.5,-1095.5], delta[-16,+9] | [-0.871575537,+0.490261240] |
| 55 | explicit +Z override | [0,+1] |
| 64 | user-specified maximumZ objective | [0,+1] |

The earlier B2 event-state example separately uses its outgoingB2->B3 direction. No runtime change has replaced the pilot's coarse early score with per-segment scores. [Exact extracted vectors and source configuration](audit/route-directions.json).

## 3. Decisions: intended versus actually generated

| Quantity | Intended simplified case | What this experiment did |
| --- | --- | --- |
| Ordinary support chain | fixed jumping chain, no extra run ticks | required support order, but many timings and repeat contacts allowed |
| Earlier movement | continuously optimize all facings | also varied first-jump inputs0..9, ground delays and additional same-support jumps |
| Tactical obstacle choice | B6 blockage timing | B6 choice appeared indirectly through corridor repair; no upfront named B6 timing menu |
| Comparison locations | after the relevant turn/before another real decision | arbitrary absolute states10,28,44,55 |
| Decision grouping | one local B6 region | no opportunity list or decision clustering at all: `decisionPlan=null` |
| Final objective | maximizeZ at64, no required B7 landing | correctly configured; both terminal jumping and run-off variants later probed |

The normal generator proposes a landing on each height-compatible support and an unlanded continuation to the current comparison tick. From a grounded state it proposes jumping and nonjumping forms. Repeated one-input nonjump contacts implement ground-running. From an airborne state it can extend without a selected landing. The first comparison truncates longer choices to10 inputs; this is how first-jump times and incomplete air paths enter the batch. Stepping was enabled but the final main run's evaluated prefixes use normal units; it did not discover a required stepping/ceiling decision here.

An important naming trap: `compile_program` sets the inner route's `runBounds=[[0,0]]` and `maxTotalRunTicks=0`. That means IPOPT's allocation search adds no further run inputs to this already composed program. It does **not** remove ground inputs already chosen by the outer movement generator. Looking only at the inner allocation[0] would incorrectly suggest all candidates had no run ticks. The audit explicitly lists grounded inputs whose jump flag is false.

## 4. Main cold run: which combinations were evaluated and retained

Three successive independent trials used the same corridor. The first used a distant B5 spatial goal for every comparison. The second used nearer B2/B3/B4/B5 goals. The third additionally prioritized proposals containing more completed required support contacts. Candidate/retention limits were not enlarged between these three trials. A fourth run resumed only the last retained55-tick histories from trial3.

Trial3 is the most relevant main trace. There were20 evaluated outer programs per comparison. Each could make multiple numerical calls to select/repair constraints, then additional calls for the separate spatial-time probe:

| State | Fixed-clock objective | Separate spatial goal | Accepted/unknown clock programs | Retained | Markedly worse drops | Resource drops | Clock + spatial calls |
| --- | --- | --- | --- | ---: | ---: | ---: | --- |
| 10 | progress toward B5, unit direction[-0.871576,+0.490261] | B2 landing | 13/7 | 8 | 5 | 7 | 46+60 |
| 28 | same direction | B3 landing | 9/11 | 8 | 1 | 11 | 53+42 |
| 44 | same direction | B4 landing | 4/16 | 3 | 2 | 15 | 50+40 |
| 55 | +Z | B5 landing | 12/8 | 8 | 0 | 12 | 96+58 |
| 64 | +Z | none | 0 accepted from18 final programs | none | none | final deadline | 328+0 |

Total773 numerical calls. The four earlier comparison stages consumed445 of those calls before the final batch. Their clock specifications have no region, no required signature and `bounded_end=false`; they do not automatically establish that the clock lies after a turn or before the next meaningful decision. The generic accepted-stage field `beforeNextTurn=true` therefore does not certify such placement in this fixture.

The exact80 combinations and each drop reason are in [CANDIDATES.md](audit/CANDIDATES.md). For example, at10:

| Candidate | Jump inputs | Grounded nonjump inputs | Measured progress | Best-found B2 time | Decision |
| --- | --- | --- | ---: | ---: | --- |
| H10-01 | [0] | [] | 2.392651 | 21 | drop, markedly worse than H10-16 |
| H10-02 | [1] | [0] | 2.572420 | 22 | drop, markedly worse |
| H10-04 | [2] | [0,1] | 2.660329 | 23 | drop, markedly worse |
| H10-06 | [3] | [0,1,2] | 2.701559 | 24 | drop, markedly worse |
| H10-16 | [8] | [0..7] | 2.721058 | 17 | retain |
| H10-18 | [9] | [0..8] | 2.717278 | 18 | retain |

### Same-clock check: what the labels0 and8 mean

The labels0/8 above are **first-jump input indices**, not progress amounts, comparison ticks or elapsed airtime. Both programs start at absolute state0 with position[746.5,5,-1104.5] and zero horizontal velocity. Both execute inputs0..9 and are scored at the same absolute state10, before input10. Each saved witness has11 states. The jump0 candidate is not compared at0 and the jump8 candidate is not compared at8, nor is either compared ten ticks after its own jump.

| Candidate | Inputs before the comparison | State10 X | State10 Z | State10 feetY | State10 progress |
| --- | --- | ---: | ---: | ---: | ---: |
| first jump0 | jump at0, then air inputs1..9 | 744.414624 | -1103.326976 | 5.495201 | 2.392651 |
| first jump8 | ground-run inputs0..7, jump at8, air input9 | 744.128392 | -1103.165971 | 5.753200 | 2.721058 |

Both use exactly the same direction and anchor:

```text
progress10 = -0.8715755371245493 * (X[10] - 746.5)
             +0.49026123963255896 * (Z[10] + 1104.5)
```

Recomputing from the saved coordinates reproduces2.3926512670823836 and2.7210580043446644. The run-then-jump alternative is ahead by0.3284067373 in this horizontal proxy at that clock. Ground acceleration builds speed while the immediate-jump alternative spends most inputs in the lower air-acceleration regime; the delayed sprint-jump boost is also close to the comparison. The saved progresses at absolute states2/4/6/8/10 are0.532160/0.963714/1.420404/1.897909/2.392651 for jump0, and0.330980/0.831610/1.382816/1.949099/2.721058 for jump8. Jump8 starts behind, then overtakes within this window. This does not establish better future completion or make that run-tick alternative relevant to the user's no-run solve. [Exact same-clock extraction](audit/same-clock-check.json).

Other retained10-tick prefixes have initial jump4/5/6/7, a nonjump/run-off prefix and a fully unknown prefix. Thus the search had already committed to several irrelevant ground-run alternatives before it ever reached B6. The zero-run initial jump0 history was parked. H10-19 also drops as markedly worse; it contains no jump and ground inputs0..9. Near-identical input lists can have different selected terminal contacts, which is why they remain distinct records.

At28, retained parents include jump histories[9,18,27] with B2/B3 contacts18/27, but also slower histories[5,17,26] and[6,18,27] with an extra B1 hop. At44 the retained set has an unknown fast history[9,18,27,37] and two distinct slower contact variants with jumps[5,17,26,35], B2@26 and B3@35. By55 the known retained slower histories are still at/just beyond B4, while the fast B5@52 history remains unknown. The exact prefix, contacts, parent ID and retained order are in the appendix. No final main-search program at64 was accepted. Resuming the same retained55-tick set evaluates20 more final programs with383 calls and still returns unknown.

## 5. What was pruned, and what was merely left untried

Before numerical evaluation, generation applies the chosen support-order policy, prefix/corridor interval checks, necessary contradiction screens, exact deduplication and work limits. The first trial3 window reports60 raw generated choices, five screened prefixes, two policy rejections and20 emitted programs, with no queue drops. Those raw choices are not60 distinct complete numerical programs. Aggregate counters survive, but rejected raw prefix identities were not recorded.

After evaluation, the two-goal retention rule drops a history as `markedly_worse` only when another measured history is no worse on all its required known time/progress comparisons and improves time by more than1 tick or progress by more than0.25. This is a heuristic, not a bound on all future decisions. Other candidates lose to a retained-width8 resource cap, with one reserved fully unknown slot. These reasons are separately preserved in the ledger; they must not be called mathematical infeasibility pruning.

Generation also stops after20 evaluated programs per window. The first additionally emitted but untested program is saved as `candidate_resource_limit`; the remaining un-emitted frontier is not exhaustively recorded. The first two trials each reopen one parked history in a bounded rescue. Trial3 reaches its deadline before rescue. [call-accounting.json](audit/call-accounting.json) gives these untested samples and reconstructs every main/rescue numerical call:430,471,773,383, total2057. Main-run output labels `unknown` do not prove a particular input schedule impossible.

The earlier claim of a useful omitted history is correct **within this over-broad experiment**: H10-01 eventually gives the best tested complete objective. It is not evidence that an intended no-run comparison should preserve the first-jump8/9 alternatives or increase a beam. Correcting the decision domain comes first.

## 6. How IPOPT results were used and what was committed

For each outer prefix, orchestration fixes its jump/ground/sneak flags, scalar vertical profile, finite contacts and landing/step rows. IPOPT receives a continuous facing problem from the original full initial state. Every facing remains variable, including earlier inputs. It does not choose a landing tick, run delay or cell branch.

Each fixed-clock call optimizes the specified direction at the common state. Separately, a spatial probe generates completions to the shared next support, solves those, and records the best-found arrival time. Time and progress are different comparisons and can come from different optimized trajectories. A best-found time is not a minimum-time certificate.

The main frontier commits the **selected symbolic prefix up to the comparison tick**, not the spatial probe's entire future. `retain_decisions` copies that program, carries its repair frontier and uses an accepted facing sequence as a warm guess. It does not freeze X/Z position, velocity or facing at the comparison. If a spatial probe finds B2@17 from a10-tick prefix, that later B2 completion is used for ranking and a facing guess; the complete B2 program is not automatically adopted as the next frontier state. Later generation can repeat that work. Selected repair branches remain an alternative queue rather than all becoming irrevocable rows. This is not sequential commitment of a single named B6 decision.

Acceptance has two levels. An inner result marked feasible satisfies its supplied scalar continuous constraints and declared support checks. The outer audit then checks additional contact consistency, vertical schedule, corridor and full finite-scene movement. A scalar-feasible result can fail that audit because relevant geometry/route constraints have not yet been selected. Those results may guide a repair, but are not accepted physical paths or final scores. Only outer accepted witnesses enter the clock score as feasible. An unknown clock history can still be retained due to an accepted separate spatial probe or the unknown reserve.

Consequently, the final accepted solution is valid under the declared standalone scene, but the stronger desired behavior that essentially every continuous result already represent a valid physical route was **not achieved**. Many calls still discover/select constraints lazily after seeing a trajectory. Nothing about IPOPT's continuous equations was changed; the incomplete constraint selection belongs to orchestration.

## 7. Constructive continuation: the successful sequence of commitments

After the failed main trials, targeted probes explicitly revisited saved first-jump0/3/6/8/9 prefixes and tried their B2 continuations. They found times21/24/27/17/18, using8/16/24/24/24 numerical calls respectively. These were generated/solved by this experiment, not loaded recorded facings. Unlike the main ranking-only probes, the following constructive experiment **adopts the complete accepted B2 program** and extends it support by support.

For initial jump0 it commits the following input/contact prefix after each accepted stage:

| Stage | Fixed jump inputs | Selected contact result states | Extra ground inputs | Numerical calls at this stage |
| --- | --- | --- | --- | ---: |
| Accepted B2 seed | [0,12] | B1@12, B2@21 | none | 8 in the targeted B2 probe |
| Extend to B3 | [0,12,21] | add B3@30 | none | 2 |
| Extend to B4 | [0,12,21,30] | add B4@40 | none | 1 |
| Extend to B5 | [0,12,21,30,40] | add B5@55 | none | 9 |
| First generated final | [0,12,21,30,40,55] | add B7@64 | none | 24 |
| Explicit airborne final | same jump inputs | no B7 contact | none | 30 |

The first final obtains Z-1093.415466 and is allowed to land on B7; then the explicit open-air version improves it to Z-1093.267553. Removing required final support did not automatically remove an optionally selected B7 contact from every candidate; these were separate fixed programs.

For the jump8 and jump9 controls, the constructive driver tries0..3 extra ground inputs before the next jump and adopts the **first accepted delay**, rather than optimizing all later combinations. Accepted extra delays before B3/B4/B5 are2/2/3 for jump8 and0/3/1 for jump9. Then it separately tests jump versus nonjump tails. Those are additional research controls, not required decisions in the user's intended no-run model. All29 constructive/terminal evaluations and exact programs/branches are archived. Their204 numerical calls, the96 B2-probe calls and three corridor diagnostic calls are additional to the2057 main-run calls. These totals exclude regression-test solves.

At every constructive stage all earlier facings are reoptimized from the original state. What becomes fixed is the accepted input/contact schedule. Candidate endpoint position/velocity never becomes a new starting condition. The first-feasible-delay rule and finite subset mean these probes do not certify an optimum over the larger run-tick problem.

## 8. The actual B6 choice in the winning fixed program

There is no `B6:pass_at_tick58` decision record and no upfront enumeration of a small list of B6 variants. The accepted B6 avoidance is encoded by selected `cell_corridor:0` rows discovered during repair. The important rows are:

| State | Selected constraints, rounded | Interpretation |
| --- | --- | --- |
| 54..57 | Z<=-1095.3 where selected | remain in front of B6 |
| 55..57 | X in[729.7,731.3] | stay in the B5/front corridor strip |
| 58 | X in[728.9,729.7] | move into B6's left-side strip |
| 57..58 | broad Z bounds[-1105.3,-1088] also selected | allow progress after clearing the X face |
| 27 | Z in[-1101.2999998,-1099.7000002] | an earlier B3 landmark repair, unrelated to choosing the B6 timing |

The winning actual positions make the B6 turn concrete:

| State | X | Z | FeetY |
| --- | ---: | ---: | ---: |
| 55 | 730.386378 | -1096.077853 | 6.000000 |
| 56 | 730.056917 | -1095.591151 | 6.420000 |
| 57 | 729.871529 | -1095.300000 | 6.753200 |
| 58 | 729.700000 | -1095.009207 | 7.001336 |

During input57, X movement happens before Z movement. The player clears the X face to X<=729.7, then can advance past Z=-1095.3. That is the relevant local blockage choice. These constraints are fixed for that IPOPT call; no decision variable inside IPOPT makes the corner materialize. The final route has no explicit passage records, wall hits or ceiling events. Its physical clearance is checked against B6 by the shared scene validator.

## 9. Every branch of the successful terminal evaluation

[FINAL_REPAIRS.md](audit/FINAL_REPAIRS.md) lists all32 branches, in recorded order, of the explicit jump0 airborne final. It made30 numerical calls; two other branches were screened before IPOPT. The scalar results were25 feasible, five unknown and two screened/blocked.

| Result of those scalar attempts | Branch count | Used as an accepted path? |
| --- | ---: | --- |
| corridor projection failure | 13 | no; generate fixed row alternatives |
| corridor crossing-signature failure | 5 | no; generate fixed row alternatives |
| terminal checkpoint stage failure | 3 | no |
| full finite-scene collision with B7 | 3 | no |
| numerical unknown | 5 | no |
| contradiction screen before IPOPT | 2 | no numerical call |
| returned result passes all checks | 1, branch25 | yes; retained even when later branches fail |

Thus25 scalar-feasible returns are not25 valid physical route solves. The21 corridor/checkpoint failures were rejected before a full scene acceptance verdict; do not classify all21 as physical collision failures. The three actual recorded collision failures are with **B7**, at input63, after the B6 turn. Two attempt an unmodeled top landing and one meets the Z face. An optional terminal landing does not make B7 nonphysical.

The solver also publishes intermediate incumbent callbacks; these are separately audited and can be preserved. `acceptedBranches` counts such notifications, not necessarily unique outer programs. The table describes returned results per recorded branch, and does not claim no other transient feasible incumbent existed.

After each B7 collision the generic passage generator considers face/timing chains over the **whole composed64-input leg**, not a short local B7 opportunity. Its diagnostics show8587 dropped timing-pool states,12 chain-budget prunes, incomplete generation and zero emitted passage options for each of those three repairs. Those are internal enumeration states, not8587 separate IPOPT calls or spatial routes. This is another concrete source of unnecessary generic work. It is not evidence that the map itself has thousands of tactical decisions.

The accepted repair contains26 added rows before duplicate removal, including B6-region rows and the earlier B3 landmark row pair. Branch25 returns Z-1093.267553 with zero scalar violation. All45 offered unique repair keys are preserved:32 branch pops,13 still pending at the branch cap; seven processed branches are saved unresolved by the outer wrapper. Two of those seven have inner interval-reach contradiction certificates for their own fixed rows (branches14/24, states61/62, X axis); the outer unknown label hides that distinction. The five numerical unknowns and13 pending alternatives lack such a proof. None of this establishes global superiority of the accepted variant. The accepted full solution independently recompiles/replays through the scalar and shared finite-scene model, passing all64 inputs. No actual game replay or global optimality proof was performed.

## 10. Correct conclusion and next setup

The prior run proves existence of a valid no-run solution, and exposes inefficiencies in the generic experimental harness. It does not faithfully benchmark the user's intended one-blockage decision model. The early run-tick/history counterexample is a counterexample inside an unnecessarily broadened search, so it should not drive an immediate increase in candidate counts or a more elaborate early score.

For the clarified case, preserve/derive the no-run normal jump chain and treat its ordinary land/jump constraints as fixed prerequisites. Identify the single B6 passage opportunity from geometry, enumerate its small set of legal face-transition ticks using the existing blockage builders, compile the full chain and those constraints before each solve, then compare all valid variants by Z at the same absolute state64. Keep B7 physical and handle its possible collision consistently within this final local region. All earlier facings remain variable. Log the selected fixed constraints and accepted/rejected result for every variant, so no prefix beam, early manual checkpoint or speculative ground-run delay is needed for this case.

This turn adds documentation and extraction of existing evidence only. It does not change the search policy or run a new numerical experiment. Narrowing the future fixture to the user's intended decision domain is the next correction, before evaluating whether any further abstraction or bounding is useful.

Source map for review: [fixture/checkpoint setup](../../cell_routes/fixed_clock_experiment.py), [cell decomposition](../../cell_routes/geometry.py), [prescribed route and projection](../../cell_routes/routes.py), [prefix generation](../../movement_search.py), [normal choices](../../open_movement.py), [program compilation](../../movement_composition.py), [main search and commitment](../../cell_routes/checkpoint_search.py), [two-goal pruning](../../cell_routes/time_progress.py), [corridor repairs](../../cell_routes/local.py), [scalar versus physical acceptance](../../block_contacts.py), [internal numerical seed phases](../../../ipopt/continuation.py). The saved numerical experiments use commits identified in their reports; the audit extraction does not modify these modules.
