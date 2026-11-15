# Follow-up: what can actually be pruned

2026-11-03. This continues [the full candidate audit](CANDIDATE_AUDIT.md). It investigates the unfinished generation question, without changing search policy or running IPOPT. The original 208-call accounting is unchanged.

## 1. The million duplicate hits come from duplicate descriptions

The source was reconstructed in the exact saved P06/P07 contexts, including decorated profiles, vertical coefficient metadata, scopes and the original reference. The saved context digest must match; the probe fails rather than guessing if it does not. Each obstacle option stream was inspected for at most three seconds. These are bounded prefixes of enumeration, not exhaustive domain sizes or feasibility counts.

| Program | Obstacle | Descriptions inspected | Distinct effective numeric row sets | Duplicate descriptions | Stream exhausted? |
| --- | --- | --- | --- | --- | --- |
| P06 | b2 | 85,945 | 162 | 85,783 | False |
| P07 | b2 | 85,552 | 232 | 85,320 | False |
| P07 | b3 | 104,810 | 8 | 104,802 | False |

Effective rows remove only inequalities already guaranteed by the same conservative base envelope, and collapse exact duplicate inequalities. No approximate coordinate rounding or position-based trajectory merging is used. Some distinct row sets may still be infeasible; this probe does not decide that.

Three concrete examples:

| Program / block | First description | Duplicate description | Identical actual inequalities |
| --- | --- | --- | --- |
| P06 / B2 | Z+ -> X+, gate 18 | Z+ -> X+, gate 19 | Z >= -1102.699998988079 at states 16..18; X >= 742.3000010119209 at 24..28 |
| P07 / B2 | X+ -> Z+, gate 20 | X+ -> Z+, gate 21 | X >= 742.3000010119209 at 16..19; Z >= -1102.699998988079 at 25..28 |
| P07 / B3 | X+ throughout | Y- -> X+, gate 0 | X >= 739.3000010119209 at 27..28 |

For P06/B2, states 19..23 require no horizontal avoidance: the scalar trace and phase screening already clear the obstacle. Moving a notional gate inside that gap changes no rows. For P07/B3, only states 27 and 28 require the horizontal inequalities. An arbitrary inactive face prefix before those states creates many descriptions of the same model. Y- here is an inactive avoidance label, not a ceiling hit or a change in Y physics.

The current implementation enumerates those descriptions for each obstacle, takes their combinations, and deduplicates the completed row set at the leaf. The duplicate explosion therefore occurs before it can benefit from final deduplication. Source code also runs the shared reachability/LP refinement before checking whether the completed row set was already emitted. LP caching can reduce actual LP executions, but still leaves row assembly, normalization, hashing, cache lookups and tree traversal.

A two-second saved-cursor probe produced 3703 more duplicate hits and no new variant. Its cProfile record includes cold SciPy import/setup and profiling overhead; it is not a steady-state timing benchmark and does not establish a whole-search speedup. Counts and exact duplicate pairs are the useful evidence here.

### Generic, domain-preserving reductions

1. Deduplicate each obstacle's compiled effective row sets before combining them with other obstacles. Keep the fixed profile, coefficient/constraint context, phase scope and relevant resource metadata in the cache key.
2. Deduplicate equivalent partial states at the same remaining-item index: identical accumulated numeric constraints plus identical remaining work represent the same future model family. Preserve any passage/cap metadata that can affect later validity. If such metadata is committed in a future version, keep its alternatives or include it in the equivalence state.
3. At a complete leaf, check an already-emitted canonical row key before repeating expensive refinement. Only skip keys already handled in the exact context; do not turn unknown or unexamined keys into infeasibility.

These remove duplicate model descriptions. They do not require discarding different jump schedules, different controls, or genuinely different feasible constraint regions. Timing/side choices that produce different actual rows must remain distinct. The current row-equality proof does not justify dropping choices merely because one optimized trajectory looks similar.

## 2. This does not mean the 164 expensive IPOPT calls were identical

After tightening repeated direct inequalities to their strongest lower/upper bounds, all 151 handled P06 branches and all 68 handled P07 branches remain distinct constraint sets. All 172 dispatched main problem hashes were already distinct. Early deduplication targets the large amount of work before dispatch; it cannot be described as removing 164 repeated IPOPT calls.

P04/P05 are a separate redundancy opportunity. [The inclusion evidence](program-inclusion.json) verifies the same scalar/input history, compiled coefficient arrays and finite contacts as P03. Their only additional baseline rows are:

| Narrower program | Broader program | Extra inequality |
| --- | --- | --- |
| P04 | P03 | Z[28] <= -1100.300001011921 |
| P05 | P03 | X[28] >= 735.3000010119209 |

Therefore every trajectory feasible for P04/P05 is also feasible for the broader P03 model at that horizon. This is a precise model-set inclusion, not an assertion that P03's one saved optimal trajectory dominates every future velocity tradeoff. Avoiding their extra prefix scoring is justified only if future generation from P03 preserves the corresponding refinements. Otherwise it should be a provisional deferral with a retained audit path. Their eight spatial calls and four main calls are potential savings, not measured safe pruning results.

## 3. Timing-family pruning remains unproved

P06/P07 have 2 jumps and 4/5 run inputs; P03 has 3 jumps and no runs. Their observed B3 probe arrivals are 46/47 versus 30. The blanket incomparable_schedules guard preserves both, even though neither benefits from the fewer-run or more-jump exceptions. However, comparing absolute-clock progress/velocity across phases does not certify dominance, and a found arrival time is an upper bound, not a proof of earliest arrival.

A normal-jumps-only remaining-time argument would also be conditional: the enabled catalogue includes stepping/blip and run-off alternatives. An actual death proof must cover those, intermediate supports and any relevant contact that changes the scalar evolution. No such complete proof was established here. Do not hard-prune P06/P07 solely from these scores.

There is also a scheduling barrier independent of that guard: the state-28 retention comparison never ran. The engine exhausts pending constraint work before it compares the window. Merely tightening the final retention rule would not save these already-incurred calls. Provisional comparison/parking while retaining the frontier is a distinct scheduling proposal, which needs retained-versus-parked completion audits.

## 4. Corridor and logging findings remain open

The previous audit already replayed all 62 corridor-rejected trajectories successfully against physical geometry. The B3 examples cross its horizontal gate while above the block. This requires review of the intended 3D route equivalence; physical validity alone does not prove future route completion. No corridor checker was removed or weakened here.

Per-call spatial-probe details, some accepted per-branch objectives and two final validation records were not saved. Those gaps still prevent an exact per-call objective-improvement audit. No missing results were invented in this follow-up.

## Recommendation

Start with the exact-context generation reductions in section 1. They have a clearer correctness argument than discarding timing families or changing a score, and directly address measured duplicate work. Then separately review height-aware route semantics and incremental comparison. Keep P06/P07 until a sound continuation exclusion or an explicitly measured heuristic justifies parking or dropping them.

No runtime behavior was changed. Reproduction: `OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 PYTHONPATH="$PWD" .venv-guidance/bin/python standalone/autonomy/experiments/orchestrator-fixes/audit_generation.py`. The bounded counts will vary with throughput; exact duplicate examples and saved-context checks are reproducible. [Raw probe and profile](generation-detail.json).


## Terminology: passage descriptions and unnecessary generation

A passage description is an ordered list of clearance faces for one block, plus the state indices where the chosen face changes. For example, use the block's +Z clearance inequality through state18, then its +X clearance inequality. These are alternative spellings of avoidance constraints within a fixed movement program and spatial route; they are not new jumps, new spatial routes or automatically distinct numerical problems. Y-face labels describe clearance under the already-fixed scalar trace, not a new ceiling/stepping decision.

The current generator permits up to four faces/three switches under its existing blockage caps and enumerates switch states throughout the prefix. It eventually filters out rows on irrelevant states, but still enumerates many chains/gates that differ only there. On P06/B2, states19..23 add no relevant horizontal constraints, so changing the Z+->X+ gate from18 to19 leaves exactly the same rows: Z+ at16..18 and X+ at24..28. On P07/B3, only27/28 matter; many different earlier face prefixes collapse to the same late rows. Cross-obstacle combination magnifies this before leaf deduplication.

Yes, the current description generation is unnecessarily broad. The concrete remedy is to generate and canonicalize constraints on actual interaction windows, merge gate choices that give identical rows, and deduplicate partial combinations before expensive refinement. Preserve distinct effective constraint sets and relevant cap/resource metadata; do not fix this by raising limits or discarding a jump schedule just because its description stream is large. The100,000-plus descriptions/eight distinct B3 row sets are a bounded observed enumeration prefix, not a proof that the entire domain has only eight possibilities.


## Correction: what the seven improvements did and did not finish

The no-horizontal-avoidance rule is implemented and working for phases that remain safely above a block. The earlier seven-workstream completion summary overstated how fully that relevance result had simplified generation. The implementation filters the final rows, but inactive states still have permissive masks, so the face-chain grammar enumerates arbitrary faces/gates there. Complete variants are deduplicated late, after combining blocks and invoking refinement. Early per-obstacle/partial-state canonicalization and generation restricted to meaningful changes are still an implementation gap. No new physical model or larger cap is needed to close it.

The concrete examples are both immediate initial-jump0 histories, not a comparison of initial0 against initial8:

| Audit program | Initial jump | First B1 return | Intervening run inputs | Second jump | B2 horizontal-row states retained | Above/clear gap with no B2 horizontal rows |
| --- | --- | --- | --- | --- | --- | --- |
| P06 | 0 | state12 | 12..15 | 16 | 16..18 and24..28 | 19..23 |
| P07 | 0 | state12 | 12..16 | 17 | 16..19 and25..28 | 20..24 |

A late state can start slightly above B2 top6 yet still need an X/Z condition if that input's Y sweep crosses the top and the selected scalar program does not land on B2. For example P06 state24 is Y6.024424 but input24 falls to Y5.796736; avoiding an unintended landing/side interaction is then relevant. That differs from states19..23 where the relevant phases remain clear. Intended support/landing rows are retained separately.

Thus the model is not receiving side-avoid rows throughout the above-block gap. The generator is redundantly deciding a nominal switch time inside that empty gap, then producing the same actual rows for multiple switch times. Finishing the previously recommended three reductions means closing this generator gap, not reimplementing the above-block exclusion or calling the seven-workstream implementation fully optimized.


## Preliminary fix plan and P04/P05 provenance

[GENERATION_RELEVANCE_PLAN.md](../../../GENERATION_RELEVANCE_PLAN.md) now covers early interaction filtering, generation on meaningful states, per-obstacle/partial-state canonicalization, screening full proposals before truncation and preserving row ownership/commitment. No runtime fix is claimed.

P04/P05 are not above B4: feet Y is7.001336 at24 and7.176759 at28, peaking7.252203 below B4 top7.5. Their C23/C01 rows are blip:wall_approach preconditions from a proposed B3-to-B4 blip, with contact at input28 and case at29. Truncation to28 retains the state28 row but removes both owner events. The existing necessary screen rejects both full30-input proposals at state29/X; screening after truncation misses that contradiction. [Exact reproduction](p04-p05-provenance.json.gz). This identifies an orphan future-condition bug in addition to the duplicate-generation issue.
