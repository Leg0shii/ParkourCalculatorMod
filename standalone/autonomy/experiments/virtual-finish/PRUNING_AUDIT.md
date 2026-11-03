# Why these requests survived pruning

2026-11-03. Offline audit of runtime `71c9bb7e`, using the unchanged [combined cold report](evidence/cold.json.gz), [initial0 cold report](evidence/initial0-cold.json.gz), [worker ledger](CALLS.md) and current source. No new numerical solves or runtime changes were made for this audit. Sections1–5 describe the archived pre-policy implementation; sections6–8 record the subsequent user decisions and shipped local-count fix. Answers below distinguish the audited implementation, the user's clarified heuristic and a proof limited to an unchanged continuation family.

## 1. Combined cold: why calls 10–12 followed successful call 9

They should be skipped when comparing the same normal-jump interaction sequence with the already feasible minimum-run representative. The current implementation does not apply that rule. It also checks the incumbent's finish time too late to avoid these prefix solves.

The ledger calls the intermediate support **B5**, not B6. The actual requests were:

| Call | Jump from B4 | Total run inputs | B5 arrival / request horizon | Finish with immediate ordinary B5→height7 jump | Existing finish |
| --- | --- | --- | --- | --- | --- |
| 7, successful representative | 43 | 15 | 58 | 67 | — |
| 8–9, completed continuations | 43 | 15 | — | 67 | 67 |
| 10 | 44 | 16 | 59 | 68 | 67 |
| 11 | 48 | 20 | 63 | 72 | 67 |
| 12 | 49 | 21 | 64 | 73 | 67 |

These later calls add ground runs on B4 before the same normal landing on B5. The immediate ordinary jump from B5's height6 crosses height7 on descent after nine inputs. Thus their corresponding normal completions lose on time, regardless of Z. The recorded calls are not consecutive59–61 arrivals; they are59,63,64.

`depth_first.time_bound` currently rejects only `program.airtime > incumbent.finishTick`. Calls10–12 end at59/63/64, all below67, so they pass. There is no addition of the remaining nine inputs at this point. After a feasible prefix is chosen, its terminal continuation is assembled and *then* the same cutoff rejects it. The report records two exclusions at68 after call10 and two at73 after call12 (free/physical final-contact variants). Call11 remained unknown after a geometry deadline and was not promoted to a terminal frame;72 is the duration its unchanged ordinary continuation would have, not a completed/attempted terminal solve.

The DFS also deliberately retains already tried siblings. Once call9's frame closes, it backtracks to the B5 frame, which still has untried delayed departures. A preference for fewer runs only changes their order; it does not remove them.

This explains the wasted work. For an already identified unchanged normal continuation family, the known nine-input suffix can be propagated before the B5 numerical call. It does not require a new continuous model or a sophisticated global bound. If a different future stepping/contact sequence could shorten that suffix, that sequence needs its own family/bound; the nine-input statement is not a proof about every possible future interaction.

## 2. Is the minimum-run-tick heuristic implemented?

**No: the current DFS has ordering by runs, not minimum-run pruning within an interaction family.** This is an implementation gap relative to the rule clarified in this conversation.

The intended rule is: hold the spatial route and interaction choices fixed, test run timings from the minimum upward until a timing has a validated feasible solve, continue that representative, and stop spending normal search work on versions with additional ground runs but the same interactions. Preserve earlier unresolved timings as unknown; preserve alternative interaction choices as separate families. If required for a false-pruning regression, test the skipped delayed candidates in a separately labelled audit, rather than consuming ordinary discovery work.

The implementation currently does the following:

- `solve_spec.processing_key` sorts symbolic proposals by `(elapsed inputs, -effective jumps, run inputs, identity)`.
- `depth_first.arrival_key` sorts accepted arrivals at the same spatial goal by the corresponding arrival/jump/run tuple.
- The scoring loop pauses later alternatives while an untried feasible arrival is available, follows that arrival, then returns to the saved alternatives.
- `depth_first.decision_family` includes the **absolute jump input and absolute first contact event**. Moving a jump from43 to44 therefore produces a different family. This key spreads scoring across candidate timings; it is not an interaction-only key for minimum-run pruning.
- There is no table saying “this interaction pattern already has a feasible representative with fewer runs”; there is no rejection based on such a table. DFS windows explicitly report `heuristicDrops=0`.
- The older `time_progress.protections` helper can protect fewer-run schedules from peer exclusion. That one-sided protection does not mean every more-run schedule is discarded. The current DFS does not call that older peer-retention routine to prune siblings.

For the cold B4→B5 family, call6 (jump42, B5@57) was unknown; call7 (jump43, B5@58) was validated feasible. Under the clarified rule, continue call7 while retaining the earlier unknown, and stop testing extra-run copies of the same interaction family. For initial0, call4 already validates zero runs on B4 before B5; extra runs are not needed to establish feasibility for that family.

A generic interaction key must ignore absolute timing and repeated ground-hold contacts on the same block. It must still distinguish different support sequences, effective jumps, stepping/ceiling/hit modes and genuinely different obstacle passage choices. The key cannot be the existing exact program/request hash, because delaying a schedule changes that hash. Nor should it be only a total run count: the initial0 route has an extra B1 return/jump compared with initial8, so those are distinct interaction sequences and both stay eligible.

The minimum-run rule is a search heuristic, as requested. More runs can change position/velocity and occasionally help a later decision; raw run count alone does not prove mathematical dominance of all such states. That is a reason to label the exclusion and retain regression evidence, not to silently omit the requested heuristic from ordinary search.

## 3. Initial0 calls 5–8: what differs?

These are **two final-contact models, each solved once and then repaired once**. They are not four different jump/run schedules. All use jumps `[0,12,21,30,40,55]`, zero run inputs, the same B5 arrival55, and finish64.

| Call | Final-contact model | Hard rows | What happened |
| --- | --- | --- | --- |
| 5 | Free-air overshoot of finite B7 | 104 | IPOPT feasible; corridor check rejects the B3 crossing |
| 6 | Same free-air model plus two corridor rows | 106 | Accepted; Z64=−1093.267554656693 |
| 7 | Actual finite B7 Y collision at input63 / state64 | 110 | IPOPT feasible; same B3 corridor crossing rejected |
| 8 | Same physical-contact model plus two corridor rows | 112 | Accepted; Z64=−1093.4154657769086 |

Calls5 and7 cross the B3 gate at X=738.5 on its negative-Z side, token `[3,-1,-1]`, while this authored corridor asks for the central crossing `[3,-1,0]`. Each produces the same repair:

```
-1101.299999811921 <= Z[27] <= -1099.700000188079
```

Calls6 and8 add those two `cell_corridor:0` rows before IPOPT and update the facing seed. Comparing the archived request objects confirms no other problem fields change within5→6 or7→8, apart from `yaw`/`fallbackYaw`. The input schedule stays fixed. The first requests are rejected before physical scene validation, so that rejection is a corridor-policy result, not evidence that they phase through blocks or are physically impossible.

Calls5–6 end with actual scalar Y≈6.7967356 and ground=false. The model must avoid the finite B7 collision on the last downward input to realize that free-air profile. Calls7–8 prescribe a B7 landing collision and its finite footprint at the collision phase, ending Y=7 and ground=true. This changes landing/avoid constraints; it is a distinct interaction choice despite the identical control schedule. Row names `blockage:5` and `blockage:6` use zero-based solid indices and refer to **B6 and B7**, respectively.

Neither requested heuristic removes these pairs: they have equal completion time and equal run count, and the final physical interactions differ. Equal-time choices still compete on Z. In this initial0 run, free-air call6 wins. In the combined cold run, physical-landing call9 beats free-air call8 (−1093.823129 versus−1093.939112 at67), so dropping the physical choice categorically would be wrong.

There may be avoidable repeated corridor discovery between5→6 and7→8. That is a separate constraint-generation/repair issue, not extra-run pruning. A reusable learned corridor alternative could help, but it must not freeze a globally unnecessary crossing tick; no such reuse is implemented or claimed by this audit.

The original ledger's “Orchestrator result” column was the **final result of the whole program evaluation**, including later repair calls. Thus a feasible final label beside call5 did not mean call5 itself was accepted. The ledger heading has been clarified; the per-call acceptance sequence is explicit in the table above.

## 4. Initial0 calls 9–15: why were these tried?

They survive for the same two reasons as cold10–12: the time cutoff compares an unfinished prefix directly with the final incumbent, and there is no minimum-run interaction-family exclusion.

Call4 already gave the zero-run B4→B5 timing, and calls5–8 completed it at64. Every later listed request delays departure from B4:

| Call | Jump from B4 | Added runs on B4 | B5 arrival / request horizon | Unchanged ordinary finish | Compared with incumbent64 |
| --- | --- | --- | --- | --- | --- |
| 4 / 5–8, successful representative | 40 | 0 | 55 | 64 | incumbent |
| 9 | 41 | 1 | 56 | 65 | later |
| 10 | 42 | 2 | 57 | 66 | later |
| 11 | 43 | 3 | 58 | 67 | later |
| 12 | 44 | 4 | 59 | 68 | later |
| 13 | 45 | 5 | 60 | 69 | later |
| 14 | 47 | 7 | 62 | 71 | later |
| 15 | 48 | 8 | 63 | 72 | later |

All prefix horizons56–63 are below64, so the current cutoff allows their numerical requests. Calls9–12 are accepted prefixes, but their direct terminal variants at65/66/67/68 are rejected before another numerical solve: the report contains eight such exclusions, two per duration. Calls13–15 remain unknown at the orchestration deadline and are not accepted/promoted;69/71/72 are conditional ordinary-continuation clocks, not solved finishes. Missing arrivals such as61 should not be interpreted as proved infeasible.

For the unchanged normal completion family these calls cannot beat64, so they should be excluded before IPOPT. Under the minimum-run heuristic, the extra-run copies of an already feasible interaction pattern should also be skipped. Merely backtracking to an already saved timing is not a reason to solve it.

The assembled prevention rows sometimes change with the delayed timing (for example, different B7 avoidance faces become available in some requests). A genuinely different obstacle-side interaction can be a separate choice; a timing-generated row/hash difference by itself is not a new strategy and must not defeat minimum-run grouping. If a distinct face choice needs testing, test its least-run feasible representative rather than automatically scanning every delay. None of these prevention-row differences rescues the listed later ordinary finish times.

## 5. What pruning exists now, and what is missing?

| Rule | Current implementation | Required interpretation / gap |
| --- | --- | --- |
| Finish-time ranking | Complete candidates rank by finish tick, then horizontal objective | Correct. Equal-time alternatives remain useful for Z. |
| Finish-time exclusion | Reject a fixed program once its own elapsed inputs exceed the incumbent finish | Implemented, but too late for these prefix calls. |
| Known continuation time | No prefix-level propagation of the same interaction family's remaining duration | Missing. For these normal B5→height7 continuations, propagate the existing nine-input duration before numerical dispatch. |
| Minimum runs for the same interactions | Fewer runs affect ordering; delayed siblings remain saved and are revisited | Missing as a pruning rule. Track a feasible least-run representative by interaction pattern and skip its extra-run copies in ordinary search. |
| Earlier unresolved timing | Unknown is retained and not called infeasible | Keep this behavior; finding a later feasible timing does not prove the earlier one impossible. |
| Different interactions | Distinct physical contact models and obstacle choices remain candidates | Keep them distinct. Free overshoot versus finite B7 landing explains5–8; initial0 versus initial8 also stays distinct. |
| Prior velocity/schedule protections | Older comparison helpers exist; current DFS does no heuristic peer exclusion | These do not implement the requested minimum-run rule and should not be cited as though they do. |
| Feasibility/relevance/dedup | Reach screens, interaction eligibility, exact-model dedup and scene checks still run | Useful, but exact-model dedup cannot identify a same-interaction delayed schedule as redundant. |

The key correction is to add **semantic interaction-family pruning**, not increase budgets or candidate caps. The current `decision_family` key is timing-sensitive, so it cannot serve that purpose unchanged. Repeated standing contacts during a ground hold must collapse into the same support interaction; meaningful landings, effective jumps, obstacle sides and stepping/ceiling/hit choices must remain explicit.

For time pruning, use `earliest finish within this interaction family = elapsed prefix + minimum remaining inputs for its allowed continuation`. Compare that to the incumbent before solving the prefix. Preserve equality because Z is the secondary objective. If alternative future interactions remain open, use a lower bound covering them or prune only the identified family; do not turn one ordinary-flight duration into an unsupported global bound.

Minimum-run exclusion is the user's requested heuristic, not a certified dominance theorem. Log which feasible representative caused each exclusion and retain the ability to run skipped candidates in false-pruning regressions. If a new map shows the minimum-run representative cannot support a later required decision, explicitly reopen/reassess that heuristic rather than quietly exhausting every delayed sibling on every normal run.

The earlier results note's suggestion to investigate generic remaining-time bounds was incomplete: it did not call out that the clarified same-interaction minimum-run policy is absent. For these fixed continuation families, known scalar durations already provide the relevant time comparison. This audit corrects the implementation-status description without claiming either fix has been shipped.

Source pointers: [DFS traversal, `time_bound`, `decision_family` and scoring/backtracking](../../cell_routes/depth_first.py), [run/jump ordering metadata](../../solve_spec.py), [existing scalar descent and ordinary landing generation](../../open_movement.py), [virtual finish binding](../../terminal_goals.py), [older state/schedule protection helpers](../../cell_routes/time_progress.py). The [offline checks](PRUNING_AUDIT_CHECKS.json) verify all12 listed representative/delayed-prefix contact comparisons and their nine-input ordinary suffix clocks, plus both exact corridor-row request diffs. No solver budget or runtime code was changed in this audit.

## 6. User decision: continuation-time reasoning remains deferred

The user clarified that propagating a nine-input suffix is not the selected fix. To exclude cold10–12 by final time, we would need to establish that the completed family in7–9 admits no faster continuation (no useful additional block/interaction choices, no extra run ticks to remove), then show the later prefixes cannot improve that result. An ordinary-jump duration alone does not establish this for the still-open search. Keep this as a potential improvement, **deferred**; the recommendations in sections1/4/5 do not authorize implementing it now. Existing elapsed-input cutoff remains unchanged.

The selected implementation is the minimum-run heuristic **on the latest jump**, within the same prior decisions and interaction pattern. At B4@40, jump43 uses three preceding ground runs; the feasible representative in cold7–9 therefore excludes jump44 or later copies (four or more runs) of that last-jump pattern. Earlier run totals do not define this comparison. For initial0, jump40 from B4@40 already validates zero runs for this jump, so extra-run copies should be excluded. Distinct initial0/initial8 histories and distinct contact/passage choices remain separate. Implementation and validation are recorded below when completed.

## 7. Initial0: the B3 corridor in coordinates, and why landing does not imply it

B3's physical box is X=[738,739], Z=[−1101,−1100], top Y=7. Including the player's horizontal half-width, its landing-overlap region for the player's center is approximately X=[737.7,739.3], Z=[−1101.3,−1099.7], with the usual small numerical clearances.

The additional corridor check invents a **vertical reference plane through the middle of B3, X=738.5**. When the player crosses that plane toward decreasing X, the authored guide expects Z to lie inside [−1101.3,−1099.7]. This is a restriction on the approach, separate from actually landing on B3. It does not specify a fixed crossing tick and it does not use Y to exempt an above-block crossing.

The logged token `[3,-1,-1]` means:

| Field | Meaning here |
| --- | --- |
| 3 | Reference gate ID3, belonging to B3; this is not a cell ID or tick |
| First −1 | Cross the X=738.5 reference plane from larger X to smaller X |
| Second −1 | Z is on the negative-Z side of the expanded B3 footprint: Z≤approximately−1101.3 |

The guide's `[3,-1,0]` has the same plane and travel direction; its final0 means Z is inside the footprint's lateral interval. A final+1 would mean Z≥approximately−1099.7. These three-way labels are generated from the guide; they are not Minecraft collision outcomes.

The rejected paths show the distinction concretely:

| Worker call | State27 (X,Z) | State28 (X,Z) | Z when the X phase crosses738.5 | Landing-approach state29 (X,Z) |
| --- | --- | --- | --- | --- |
| 5 | (738.712818,−1101.507353) | (738.420320,−1101.350424) | −1101.507353 | (738.131837,−1101.194267) |
| 7 | (738.762534,−1101.429240) | (738.476684,−1101.261375) | −1101.429240 | (738.194802,−1101.094384) |

Minecraft resolves the horizontal X movement before Z movement. During input27's X phase, Z is still Z[27]. Both paths therefore cross the reference plane south of the corridor interval, while airborne above B3: Y after that input's Y phase is about7.176759. The following Z movement brings them closer. By state29, both X/Z positions are inside B3's expanded landing-overlap region. The downward Y movement of input29 can then land on top7, producing state30.

Thus a diagonal airborne approach can cross the block's center plane slightly outside its lateral footprint, turn into that footprint, and land successfully. The B3 landing constraints require the correct overlap **at the landing collision phase**; they do not require central alignment at the earlier center-plane crossing. The corridor check is stronger and can reject such a landing approach. The initial rejection happened before independent physical scene validation, so these logs alone establish landing-row satisfaction and corridor rejection, not a separately replay-certified physical path.

The two repair rows at Z[27] force this earlier center-plane crossing into the guide's band. They are not missing B3 landing rows. Keeping the corridor checker was an earlier user choice; this explanation does not change its behavior. Its stronger-than-landing restriction remains relevant when deciding whether these signature checks should eventually be weakened or removed.

## 8. Local-count implementation and focused regression result

Implemented at `db211b2b`: count the consecutive ordinary grounded non-jump inputs immediately before the latest effective jump. For a fixed prior decision prefix and matching last-jump interaction pattern, a scene-validated feasible representative with3 local runs excludes copies with4 or more; a zero-run representative excludes positive-run copies. Earlier run totals are not used as the pruning threshold.

The pattern preserves prior decisions, support identity, relative in-air contacts/cases, scalar schedule and hard constraints. Repeated ordinary standing preparation is normalized away. Distinct obstacle sides and free/physical final contacts remain distinct. Equal-run choices, fewer-run candidates and earlier numerical unknowns remain eligible. A numerical unknown never establishes the feasible threshold. Interacting/unclassified ground preparation, sneak and ladders are conservatively retained.

Representatives survive exact resume. Each heuristic exclusion records the local count, feasible peer and discrete program for later false-pruning checks; changing the pruning policy changes the resume context. The rule is active before numerical dispatch and in symbolic filtering. It is not a proof that extra runs can never help a different map or later interaction.

**All52 focused regression tests passed with no skips**, including native compilation/IPOPT tests, dispatch controls, unknown preservation, resume, distinct interactions and the archived cold/initial0 counterexamples. [Implementation and test evidence](../last-jump-runs/README.md). The user's latest steering defers additional Real11 solves and corridor work. Section7 remains a description of archived behavior; no corridor change or additional continuation-time bound was implemented.

## 9. Genericity clarification: three/four are example values

The algorithm has no hardcoded3/4 threshold or Real11 block/tick coordinates. It learns a local run minimum `k` per matching interaction pattern from validated feasible results, excludes candidates with a larger local count, and updates `k` when a smaller feasible result arrives. Generic regressions cover minima0,1,2,5,9 plus dynamic tightening; all52 focused tests pass with no skips. [Verification evidence](../last-jump-runs/README.md#generic-threshold-verification). Runtime code is unchanged; no Real11 solve was run for this check.
