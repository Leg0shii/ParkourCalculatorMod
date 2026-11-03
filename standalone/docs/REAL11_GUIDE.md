# Linkcraft Real11 opening: agent guide

This is a navigation and interpretation guide, not a new solve. Generic code must not depend on these labels/coordinates/ticks. [Current state](CURRENT_STATE.md) and [current DFS/interaction plan](../autonomy/DFS_INTERACTION_IMPLEMENTATION_PLAN.md) supersede old pilot policies. Read the archived ledgers to answer existing questions before rerunning jobs.

## What exactly is the task?

[Current cold fixture](../autonomy/experiments/linkcraft11-opening/fixtures/clock64-orchestrator.json): original start `(746.5,5,-1104.5)`, stored velocity `(0,-0.0784000015258789,0)`. Only the seven confirmed physical boxes are present inside declared geometry coverage. No external world context is inferred. Required support order B2→B3→B4→B5 is a supplied route restriction; B7 landing is optional. **Maximize Z at absolute state64**, even if airborne or beyond B7. Normal momentum is retained with zero inertia thresholds. Initial jump domain `[0,8]` is explicitly selected, not proof that eight is the minimum useful delay. Later run bounds in this fixture are broader than the user's original simple no-run/B6-only intent.

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

Approved correction: compare arrivals at B2, P02 observed17 versus P01 observed21, follow P02 to completion first, keep P01 for backtracking. These measurements are not proven minimum arrivals. A complete reference from the P01 family ultimately does better at the final objective than the saved delayed continuation; early arrival alone does not authorize dropping the fallback.

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
