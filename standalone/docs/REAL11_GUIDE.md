# Linkcraft Real11 opening: agent guide

## Physical B7 and anchor implementation (2026-11-04)

The [fresh fixture](../autonomy/experiments/anchor-feasibility/real11-anchors.json) extends B7 one block +Z to `[729,0,-1095,730,7,-1093]` and requires actual B7 landing. It minimizes achieved T, then maximizes Z; terminal guide is `(729.5,7,-1093)`. Normal body-overlap support still applies. The old virtual target/extra X<729.7 row is absent from the new fixture.

The new default physical-task engine uses caller-declared anchors B2/B3/B4/B5, backward/onward guides, intermediate early stopping, global hard-jump latches and recoverable ordinary extra runs. Internal queries check selected completed decisions without future anchor rows or fixed intermediate poses. The preliminary binary110° turn heuristic records its edge/margin/distances/keys and replay drift. Multiple jumps on the current block remain available; inter-block returns are [deferred](../autonomy/REVISIT_DISCOVERY_DEFERRED_PLAN.md).

[New-task implementation and evidence](../autonomy/experiments/anchor-feasibility/README.md), [plan](../autonomy/ANCHOR_FEASIBILITY_IMPLEMENTATION_PLAN.md), and [resume](ANCHOR_IMPLEMENTATION_RESUME.md) describe current source/verification. The2026-11-03 results below remain historical controls with original geometry/virtual semantics. B6's reported extension still needs actual bounds.

## Automatic corridor implementation (2026-11-03)

The new [automatic fixture and evidence](../autonomy/experiments/automatic-corridors/README.md) discovers contact/passage itineraries without supplied points, support order or clocks. Typed region/corridor/DFS integration and fixed 3D guard selection are implemented in the isolated branch. The default50-route shortlist admits the archived initial0,64-input witness after independent replay, without seeding discovery. This is representation coverage, not a new cold-search result. The final source-stable300-second comparison found no automatic finish in13 calls; supplied routing found replay-validated T67 in41 calls. This remains a cold-search performance limitation, not an infeasibility result. The supplied virtual fixture below stays an unchanged control; exact B6 extension remains unspecified.

## Previous terminal goal: virtual B7 landing (2026-11-03)

The user clarified the intended finish after the fixed-clock benchmark. At the B7-height landing event (height7), use the horizontal goal **X < 729.7 and Z >= -1095.3**, with no goal-imposed lower X bound or upper Z bound. The region is a virtual landing target extending indefinitely toward +Z, not B7's original finite support footprint. Its coordinates are player-center bounds; do not expand them by the hitbox again.

The task objective is lexicographic: **minimize the achieved landing tick T, then maximize Z[T] among candidates at that earliest tick**. T is discovered, not fixed to64. Preserve normal momentum, zero inertia thresholds and original-state continuous re-solves. Use existing numerical clearances for strict inequalities.

This goal does not authorize adding artificial side walls or treating an enlarged goal footprint as new collision terrain during the approach. Supplied physical obstacles and the virtual terminal condition remain distinct. Finite implementation envelopes must come from reach/horizon bounds, not arbitrary goal-coordinate caps.

The generic `terminal_goals` adapter and the [new fixture](../autonomy/experiments/virtual-finish/fixture.json) now implement this objective. It discovers the downward height-crossing tick from the existing scalar movement model, adds only endpoint horizontal rows and preserves actual collisions with the supplied scene. An overshooting trajectory can end below height7 and airborne: this is a nominal landing-height clock, not an artificial collision floor. Actual Y and ground state are reported separately. Ordinary finite-support landing choices remain available. The 200-input horizon is a search guard, not a fixed finish. [New cold evidence](../autonomy/experiments/virtual-finish/README.md): combined0/8 DFS finds67 (Z≈−1093.82313); a separate initial0-only cold diagnostic finds64 (Z≈−1093.26755), with no reference seeds. All34 replay checks pass. The combined run still spends its budget in the initial8 subtree, so do not present the diagnostic as default traversal success. Neither run proves the earliest possible tick. All372 selected regression tests pass. The archived91.22s MAXZ-at64 result remains historical.

This guide first describes the archived pre-DFS audit. [Current DFS cold/resume results](../autonomy/experiments/dfs-interactions/README.md) and evidence are a separate, newer corpus. Generic code must not depend on these labels/coordinates/ticks. [Current state](CURRENT_STATE.md) and [current DFS/interaction plan](../autonomy/DFS_INTERACTION_IMPLEMENTATION_PLAN.md) supersede old pilot policies. Read the archived ledgers to answer existing questions before rerunning jobs.

## Implemented virtual baseline and historical benchmark

The accepted baseline finish is the implemented **virtual height7 crossing described above**, superseding the earlier literal-finite-B7 interpretation in the source-era capability discussion. The [corridor handoff](CORRIDOR_AGENT_HANDOFF.md) through `61355976` is imported at merge `984b701b`; [spatial-plan section0](../autonomy/SPATIAL_CORRIDOR_REFACTOR_PLAN.md#0-imported-baseline-and-integration-requirements) lists compatibility requirements. The automatic spatial path must discover intermediate supports rather than require a supplied B2/B3/B4/B5 list; the canonical virtual fixture still supplies that route as a control. Intentional wall hits remain future work. B6's reported rightward extension lacks exact revised bounds. Historical capability audits describe their inspected `93652150` code, not the new implementation status.

Spatial contract clarified subsequently: different supports and 3D passages distinguish routes; overflight and landing/run-off are different even with identical horizontal projections. Support itineraries are discovered automatically by the spatial layer, then realized by local timing/method search. See the [concrete refactor proposal](../autonomy/SPATIAL_CORRIDOR_REFACTOR_PLAN.md). Earlier diagnostic grouping and supplied support lists are historical, not the desired route contract.

The [recorded cold fixture](../autonomy/experiments/linkcraft11-opening/fixtures/clock64-orchestrator.json) still defines the **older benchmark**: original start `(746.5,5,-1104.5)`, stored velocity `(0,-0.0784000015258789,0)`, seven stored boxes inside declared geometry coverage, prescribed support order B2→B3→B4→B5, and **maximize Z at absolute state64 with B7 landing optional**. No external world context is inferred. Normal momentum is retained with zero inertia thresholds. Initial jump domain `[0,8]` is explicitly selected, not proof that eight is the minimum useful delay. Later run bounds in this fixture are broader than the user's original simple no-run/B6-only intent. Preserve its source-era results; they do not answer the corrected landing-time objective.

| Block | Physical AABB `[minX,minY,minZ,maxX,maxY,maxZ]` |
| --- | --- |
| B1 | `[742,0,-1105,747,5,-1103]` |
| B2 | `[741,0,-1104,742,6,-1103]` |
| B3 | `[738,0,-1101,739,7,-1100]` |
| B4 | `[734,0,-1100,735,7.5,-1099]` |
| B5 | `[730,0,-1096,731,6,-1095]` |
| B6 | `[730,0,-1095,731,20,-1094]` |
| B7 | `[729,0,-1095,730,7,-1094]` |

[Player bounds](../autonomy/experiments/linkcraft11-opening/PLAYER_BOUNDS.md) and [constraint runbook](../autonomy/CONSTRAINT_GENERATION_RUNBOOK.md) explain float player expansion and B1's corner union. These are raw world boxes, not landing-center bounds. The old 200/400-tick IPOPT capture fixtures include other movements/ladders and are a different benchmark.

## Which evidence answers which question?

| Question | Read |
| --- | --- |
| Every cell, spatial route, guide direction, decision/commit/prune in original pilot | [EXECUTION_AUDIT.md](../autonomy/experiments/linkcraft11-opening/EXECUTION_AUDIT.md) |
| Why the original four trials returned unknown | [OTHER_FAILURES.md](../autonomy/experiments/linkcraft11-opening/OTHER_FAILURES.md) |
| Corridor bounds, 728.9 versus expanded obstacle edge, missing future constraints/checks | [CORRIDOR_FAILURES.md](../autonomy/experiments/linkcraft11-opening/audit/CORRIDOR_FAILURES.md), [corridor fix plan](../autonomy/CORRIDOR_CONSTRAINT_FIX_PLAN.md) |
| Exact seven programs, 227 handled fixed branches, probes, pending frontier, exclusions | [CANDIDATE_AUDIT.md](../autonomy/experiments/orchestrator-fixes/audit/CANDIDATE_AUDIT.md) |
| Every numerical request/result, including probe logging gaps | [WORKER_CALLS.md](../autonomy/experiments/orchestrator-fixes/audit/WORKER_CALLS.md), [CSV](../autonomy/experiments/orchestrator-fixes/audit/worker-calls.csv) |
| P01/P02 ancestry/order, P06/P07 introduction, jump16/17 versus run counts | [CANDIDATE_QUESTIONS.md](../autonomy/experiments/orchestrator-fixes/audit/CANDIDATE_QUESTIONS.md) |
| Why initial runs0/8 only; older successful9; coverage needed for10/11 | [Initial-delay follow-up](../autonomy/experiments/orchestrator-fixes/audit/CANDIDATE_QUESTIONS.md#7-why-only-initial-runs08-rather-than91011) |
| Why state10; supplied timings/routes/settings versus generated choices | [Experiment configuration audit](../autonomy/experiments/orchestrator-fixes/audit/CANDIDATE_QUESTIONS.md#8-why-compare-first-at-state10-and-what-was-supplied-rather-than-discovered) |
| Existing automatic components, remaining integration and corrected landing objective | [Capability follow-up](../autonomy/experiments/orchestrator-fixes/audit/CANDIDATE_QUESTIONS.md#9-which-requested-automatic-behaviors-already-exist) |
| Automatic versus supplied corridor, reference coverage and helper integration plan | [Corridor comparison](../autonomy/experiments/orchestrator-fixes/audit/CANDIDATE_QUESTIONS.md#10-how-does-the-automatic-corridor-compare-with-the-supplied-one), [support/goal/direction plan](../autonomy/DFS_INTERACTION_IMPLEMENTATION_PLAN.md#21-integration-plan-infer-support-order-from-generated-decisions) |
| How cells are built, support-top patches, portal midpoint bias and local refinement | [Decomposition investigation](../autonomy/experiments/orchestrator-fixes/audit/CANDIDATE_QUESTIONS.md#11-are-large-air-cells-and-negative-z-block-cells-built-into-decomposition), [improvement proposal](../autonomy/DFS_INTERACTION_IMPLEMENTATION_PLAN.md#25-cell-decomposition-and-anchor-investigation-focused-improvement-proposal) |
| Duplicate descriptions, what can actually be pruned, P04/P05 provenance | [PRUNING_FOLLOWUP.md](../autonomy/experiments/orchestrator-fixes/audit/PRUNING_FOLLOWUP.md), [generation detail](../autonomy/experiments/orchestrator-fixes/audit/generation-detail.json) |
| Machine-readable exact plans/coefficients/call data | [candidate-ledger.json.gz](../autonomy/experiments/orchestrator-fixes/audit/candidate-ledger.json.gz), [counts](../autonomy/experiments/orchestrator-fixes/audit/counts.json), [manifest](../autonomy/experiments/orchestrator-fixes/audit/manifest.json) |
| Current runtime fix scope and checks already run | [Orchestrator results](../autonomy/experiments/orchestrator-fixes/README.md) |
| Reference complete program, trajectory and crossover | [solution](../autonomy/experiments/linkcraft11-opening/solution.json.gz), [trajectory CSV](../autonomy/experiments/linkcraft11-opening/trajectory.csv), [execution audit](../autonomy/experiments/linkcraft11-opening/EXECUTION_AUDIT.md) |

The original opening README is source-era narrative with several subsequent corrections. Use current docs for status; original audit tables for what those runs actually did.

## Candidate labels and the ordering defect

P labels are local to the current cold audit corpus, not permanent solver types.

| Program | Prefix horizon | Jump input indices | Grounded nonjump run inputs | Meaning |
| --- | --- | --- | --- | --- |
| P01 | 10 | `0` | none | Immediate setup family |
| P02 | 10 | `8` | `0..7` | Delayed setup family |
| P03 | 28 | `0,12,21` | none | P01 descendant; B1 return12, B2 arrival21 |
| P04 | 28 | same | none | P03 physics plus orphan B4 `Z[28] <= -1100.300001011921` |
| P05 | 28 | same | none | P03 physics plus orphan B4 `X[28] >= 735.3000010119209` |
| P06 | 28 | `0,16` | `12..15` | Four run inputs after B1 return12, then return28 |
| P07 | 28 | `0,17` | `12..16` | Five run inputs after return12, airborne at28 |

P03 completed local work after two worker calls, but the window barrier prevented deeper continuation. P06/P07 repeatedly outranked ready P02 descendants using current `(prefix inputs,-jumps,runs,id)` priority; persistent traversal bypasses `fair_parents`. P06 received29 chunks, P07 received50. P01-family calls201, P02-family calls7. P06/P07 together account176 of208 worker calls (164 main+12 spatial). Second-jump inputs13..15 did appear as shorter pending F02..F07; they were not proved infeasible or main-scored. **16/17 are second-jump absolute indices, not run counts.**

Implemented correction: compare arrivals at B2, P02 observed17 versus P01 observed21, follow P02 to completion first, keep P01 for backtracking. These measurements are not proven minimum arrivals. A complete reference from the P01 family ultimately does better at the final objective than the saved delayed continuation; early arrival alone does not authorize dropping the fallback.

## Latest virtual-finish pruning questions

See [the dedicated audit](../autonomy/experiments/virtual-finish/PRUNING_AUDIT.md) for cold10–12, initial0 5–8 and9–15. It separates missing same-interaction minimum-run pruning from the weak elapsed-prefix time cutoff, and identifies corridor repairs versus actual terminal-contact choices. This is an offline explanation, not a new solve or shipped pruning fix.

## Exact counts and limits of the record

Three saved cold runs, 300 seconds each, total elapsed910.130s. Seven main programs; 227 handled main branches =172 dispatched+37 pre-dispatch screens+18 budget stops. Nineteen spatial program evaluations made36 additional worker calls: **208 total**. All172 main worker returns were numerically feasible. Recorded main worker time47.411s is much smaller than wall time; remaining time includes symbolic generation/assembly/compiler/other work without a complete attribution.

There are108 recorded accepted physical prefixes and62 corridor-signature rejections. All62 rejected traces pass independent physical scene replay. Two main post-validation records are missing, with13 possible branch slots in ambiguous alignment; the audit preserves uncertainty. Do not turn missing log entries into IPOPT failures or count them as definitively accepted. The search stopped at state28 with no terminal incumbent,20 ready untried programs and16 pending symbolic prefixes.

**Corrected generation exclusions:** 57 =25 dead falls+29 interval reach contradictions+3 norm reach contradictions. An older statement “56 dead falls+1 initial-floor” is wrong for this corpus. The separate43-fall regression dataset is different and is not contradicted by this correction.

## P04/P05: one early-interaction/ownership defect

Their feet Y at24 is7.001336, peak at27 is7.252203, and at28 is7.176759. They are above B3 top7, **below B4 top7.5**. The cause is not an above-B4 endpoint rule. The full generated B3→B4 blip has approach/horizontal contact input28 and blip input29/state30. Truncation to28 removes future events but retains the state28 approach row, producing an orphan alias. Full proposals fail the existing shared screen at29/X; truncated versions pass.

Moreover B4 is already unreachable in all84 swept phases through28 in the broader P03 context. X28 lower bound737.790544 is beyond B4 expanded Xmax735.300000. Thus shared eligibility should have prevented every B4-derived interaction/row/check there before generation. [Provenance](../autonomy/experiments/orchestrator-fixes/audit/p04-p05-provenance.json.gz), [reach evidence](../autonomy/experiments/orchestrator-fixes/audit/b4-prefix-relevance.json). Same generic root as late inactive-face filtering, not a new landing-versus-avoid system.

## Description explosion is not duplicate IPOPT calls

Bounded three-second exact-context samples: P06/B2 85,945 descriptions→162 distinct effective row sets; P07/B2 85,552→232; P07/B3 104,810→8. None exhausted. Equivalent gate18/19 or20/21 choices lie in irrelevant gaps; B3 also has inactive face prefixes before relevant states27/28. Dedup follows cross-obstacle combination/refinement, causing ~1.27 million duplicate hits in saved generation.

But actual handled P06 models include151 distinct direct-bound sets and P07 includes68. All172 actual main request hashes are distinct. Do not claim the164 P06/P07 worker calls were exact duplicates. Early relevance and canonicalization can remove redundant descriptions; deleting entire timing families needs stronger evidence.

## Progress, velocity and corridor coordinates

Direction is a chosen route reference, not velocity magnitude: progress `d·(p-anchor)`, forward velocity `d·v`, horizontal speed `hypot(vx,vz)`. The saved guide has segment-specific directions, but original clock10/28/44 scores use start→B5 center `(-0.871575537,+0.490261240)`;55 and64 use +Z. [Direction extraction](../autonomy/experiments/linkcraft11-opening/audit/route-directions.json). Do not claim per-segment scoring was used throughout.

At state10 delayed8 wins position and forward velocity, yet immediate0's advantage materializes at directional state23 (pure Z28) in the saved complete continuations. B2 arrivals21/17, B3 arrivals30/28, B4 both40, B5 arrivals55/58 for0/8. These are conditional final64-optimized witnesses, not universal event frontiers. They explain keeping both schedules, not a new scalar weight.

`728.9` is an authored guide point, not a player-expanded B6 face. B6 expanded horizontal X faces are approximately729.7 and731.3. Historical projection clipped excursions to guide-coordinate extents; the scoped corridor change uses broad physical movement regions and matching modeled obstacles. Keep logging to distinguish physically valid alternate signatures, too-narrow corridor bugs, and missing physical constraints.

The saved complete reference uses jumps `[0,12,21,30,40,55]`, achieves Z64 approximately−1093.2675533415547, airborne, with no required B7 landing. It is a validated best-found control, **not a global optimality certificate or cold-discovery result**. Run commands are in [runbook](RUNBOOK.md).


## Current implementation evidence

[DFS results](../autonomy/experiments/dfs-interactions/README.md) supersede the pre-DFS stall as current discovery status. The cold run reaches a complete standalone route after91.22s/eight calls; total cold/resume dispatches are10+4=14, with no repeated completed requests and all10/18 independent witness replays passing. [Summary](../autonomy/experiments/dfs-interactions/evidence/summary.json), [current call table](../autonomy/experiments/dfs-interactions/CALLS.md). Both root arrivals17/21 were measured and retained; only17 has been fully descended so far. Best Z64−1094.5755032869683 remains about1.308 blocks behind the separately seeded reference. Distinguish valid cold discovery from proof of optimality or exhaustive root coverage.

Named step/grind interaction queries now preserve probe-height uncertainty rather than using realized Y alone. The generic five-input grind/blip regression exposed and verified this correction; it is separate from the horizontally unreachable Real11 B4 bug. Production algorithms contain no Real11-specific tick/block rules.
