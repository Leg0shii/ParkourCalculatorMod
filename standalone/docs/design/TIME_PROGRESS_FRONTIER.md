# Time and progress comparisons: two distinct goals

Authoritative user clarification, 2026-11-01. The new path is to generate the supported candidates and evaluate them in two complementary ways. We are not trying to generate candidates specifically at different completion times. See [the implementation plan](TIME_PROGRESS_IMPLEMENTATION_PLAN.md) and [implemented behavior and measurements](experiments/two-goal-comparisons/README.md). The separate comparisons are now the default checkpoint-search policy.

Latest user refinement, 2026-11-02: retain the separate time/progress comparisons, and include both horizontal position and velocity in the fixed-clock progress assessment. This is a selected design direction; current runtime retention still uses position-only clock results. Both Real11 initial0/8 setup families remain explicitly protected by the user's preceding decision. The policy/solver has not been changed by this discussion.

Latest consolidated plan: [velocity and schedule protection](STATE_PROTECTION_IMPLEMENTATION_PLAN.md). User keeps current margins: progress0.25 blocks and spatial time1tick; the floated0.05 is not selected. For compatible same-clock states, greater forward velocity OR greater horizontal speed OR position within the existing progress margin of the peer is a veto on heuristic exclusion. Matching schedule/context is required, and different-schedule run/jump protections remain. Eligible overflow must be saved and actually resumed in `(n,-J,R)` order rather than lost at the active cap. Implementation remains pending; the linked plan specifies modules, audit fields, persistence and regression gates.

## Selected schedule comparison and processing rules

Latest explicit user rules, 2026-11-02. These supersede using absolute-clock progress to eliminate candidates with different ground/air schedules. This section records selected policy, pending implementation; no runtime behavior changes in this discussion turn.

1. Use absolute-tick position/velocity comparisons only for decisions whose ground/air/jump schedule is the same, with compatible incoming and contact context. Equality of elapsed tick or equality of aggregate counts does not establish the same jump phase. A per-input run/jump/air sequence distinguishes the schedules.
2. When schedules differ, protect a candidate against a peer-based heuristic exclusion if it has **fewer run inputs OR more actual jump takeoffs** than that peer. Either advantage is sufficient. This preserves a structural reason for later benefit even when the short horizontal score and nearby arrival time favor the peer. The guard provides retention protection, not a proof that the protected candidate is faster or farther.
3. Continue using shared spatial-event time/state labels for comparisons across schedules, retaining time/state and structural tradeoffs. A candidate can still be rejected by a valid feasibility screen or merged as an exact equivalent. A resource limit is a separately recorded omission and must not be relabeled as inferiority; protect the selected setup families and keep deferred work resumable.
4. Process candidates lexicographically by **fewest total elapsed ticks, then most jumps, then fewest run ticks**. Use a stable deterministic tie-break only after those three values. Processing priority does not authorize pruning a later candidate.

Definitions for a constructed prefix with n inputs:

```text
J(c) = number of effective grounded jump inputs in inputs0..n-1
R(c) = number of grounded inputs without such a jump in inputs0..n-1
processing_key(c) = (n, -J(c), R(c), stable_tie_break)

protect(c, peer), for different schedules:
    R(c) < R(peer) OR J(c) > J(peer)
```

Jump count means actual takeoff/jump-regime inputs, not all airborne inputs or ignored midair jump presses. Run count includes grounded nonjump inputs already embedded in the composed movement profile; the inner solver's additional-run allocation[0] is not that count. Count the whole compared constructed prefix, not a longer provisional future used only to estimate a spatial goal. Total ticks includes both ground and air inputs. At a shared clock all prefixes have the same n, so jump count and run count determine their order. At a shared map event the different elapsed arrival times remain the primary processing key.

Real11 examples:

| Comparison | Immediate setup | Delayed setup | Processing and protection |
| --- | --- | --- | --- |
| state10 prefixes | n10,J1,R0 | n10,J1,R8 | immediate first; its fewer runs protect it against delayed setup |
| found B2 arrival prefixes | n21,J2,R0 | n17,J1,R8 | delayed first by elapsed time; immediate remains protected by fewer runs and more jumps |

The delayed setup remains useful for its earlier arrival; the protection of the immediate setup does not eliminate the delayed one. Both user-selected families survive. Equal J/R counts with different schedules still do not permit a cross-phase absolute-clock dominance claim. The earlier Real11 support-progress-first proposal hook is an experimental ordering, not the newly selected priority order; it must not silently override this lexicographic key when implementing the refinement.

## Spatial goal: compare elapsed time

Hold a physical target fixed: for example, land on the same block or complete the same passage. Determine the earliest validated achievement time found for each candidate. This comparison helps identify the fastest potential route.

Landing requires the actual declared finite contact; being airborne over the goal is insufficient. Preserve input/state indexing: an event produced by input t is observed at state t+1. Earlier unresolved times remain unknown. A found time is not automatically a proof of minimum time.

### Compare arrival states at a shared spatial event

Recommendation following the user's event-alignment question, 2026-11-02: retain an arrival label `L_E=(t_E,p_XZ_E,v_XZ_E,mode_E)` at a shared map event E, alongside the existing common-clock progress label. E must mean the same block/portal and compatible approach/contact phase. Comparing each route's next landing is insufficient if those are on different blocks. On Real11, compare both arrivals atB2, not the immediate setup's return toB1 against the delayed setup's landing onB2.

Velocity can be compared at different absolute times when aligned to that common event, provided elapsed time stays in the label. A consistent prelanding sample is possible; the preferred canonical sample is the fully resolved landing state before its next input. It includes collision/friction effects and places ordinary landings in the same ready-to-jump mode. Full X/Z position still varies within the support region and must accompany velocity. Stepping/ceiling/airborne portal modes require their own consistent phase; they cannot all be merged as ordinary grounded arrival.

The same-clock and same-event labels answer complementary questions:

| Alignment | What stays fixed | Measurements | Interpretation |
| --- | --- | --- | --- |
| common clock | absolute stateh and matching ground/air/jump schedule | p/v at h plus compatible movement/contact context | how far/how fast at the same elapsed time and phase |
| common map event | block/portal and contact phase | elapsed arrivalt and p/v there | time spent to reach a comparable stage, and the state available afterward |

Do not interpret p/v at different event times as a same-clock objective comparison, or combine p from h with v from E into one physical state. They are separate labels, each measured from one accepted witness. Grounded does not guarantee identical future state: lateral alignment, direction of momentum and selected next interaction still matter. Arrival time, position and velocity create tradeoffs rather than an automatically justified scalar ranking or dominance certificate.

Concrete found complete trajectories atB2, after landing: immediate0 arrives at21, X/Z[740.816154,-1102.565592], outgoing velocity0.265297 towardB3; delayed8 arrives at17, X/Z[742.033572,-1102.902301], outgoing velocity0.242594. The immediate setup costs4 extra ticks but is1.098933 blocks farther along theB2-to-B3 direction at its own arrival and has greater outgoing velocity. That is a reason to retain both. These states come from the saved64-optimized continuations; the earlier independently optimizedB2 probes land at other p/v, so these numbers are not a proof of a unique best arrival state for either program.

Choosing between them requires future value: for minimum completion time compare `t_E + remaining_time(q_E)`; for the common terminalh compare what each event state can achieve with `h-t_E` inputs left. The future term depends on the remaining map and is not determined by speed alone. Reuse existing fixed-input continuations to the next shared meaningful event or downstream decision group to estimate it; preserve unresolved/tradeoff alternatives. In Real11 there are just two protected setup families, so carrying both throughB6/final64 is a practical choice. No evidence establishes that jump0 is necessary or globally optimal; it wins among the complete continuations tested. Every continuation solve still starts from the original state with earlier facings free; arrival labels are summaries for ranking, not frozen numerical splice states.

This is a documented recommendation for extending the existing time/progress framework, not implemented runtime behavior. Exact event evidence is archived with the Real11 experiment.

## Time goal: compare objective

Hold the absolute elapsed state tick h fixed across candidates. Optimize/measure the same horizontal objective S(h), usually progress/distance along the shared outgoing route direction. Compare after turnarounds where the direction is meaningful. Y is feasibility, not a height reward.

The selected schedule rule above gates use of this comparison for pruning: identical clock alone is insufficient when the ground/air/jump inputs differ. Cross-schedule scores may still be logged for diagnosis, but do not justify the old state10 exclusion of the immediate setup.

This is a proxy for which candidate goes farthest. It is not equivalent to the shortest time to a spatial goal. Do not compare progress sampled at each candidate's own arrival time and call it a same-time objective comparison. Record spatial-goal times and fixed-clock objectives separately.

### Position and velocity at the common clock

Keep the accepted witness's full horizontal state `(p_XZ(h), v_XZ(h))`. For the shared route direction d and anchor a, report forward position `s=d dot(p-a)` and forward velocity `u=d dot v`; retain perpendicular position/velocity too, since lateral alignment affects a turn or blockage. Use identical absolute h, direction, anchor and stored-velocity convention for every compared candidate. Position and velocity in one state label must come from the same physically accepted trajectory. Combining the best position of one solve with the best velocity of another would invent a state that was never solved.

Direction convention, clarified2026-11-02: use signed components along a shared unit direction of the relevant spatial-route segment. Forward displacement is s and forward stored velocity is u, in blocks and blocks/input respectively. A negative u means moving backward along that segment. Speed magnitude `sqrt(vx^2+vz^2)` alone cannot distinguish useful forward momentum from sideways/backward movement. For a +Z segment, s=z-a_z and u=v_z; for a diagonal use the same dot products. Choose the route segment/direction before comparing candidates, ideally after the turn where the outgoing direction is unambiguous, or the common outgoing segment at an event-aligned landing. Do not let each candidate choose its own best projection.

With perpendicular unit direction n=(-d_z,d_x), also keep lateral offset `n dot(p-a)` and lateral velocity `n dot v`. There is no general preference for larger lateral components: the next portal/blockage defines the useful corridor and turn. Preserve relevant lateral tradeoffs rather than declaring a candidate superior from forward projections alone. Keep position and velocity as separate comparison components; no scalar conversion weight is selected. The earlier Real11 clock pilot used the fixed start-to-B5 unit direction at10/28/44 and +Z at55/64; its velocity values were diagnostic. Current runtime still ranks clock progress by position only. Using each relevant segment's position/velocity projections is the documented refinement, not a shipped velocity score.

### Computing the geometric reference direction

A cell sequence does not uniquely determine a direction inside its regions. First choose a shared geometric representative polyline: the prescribed guide in the Real11 pilot, or the existing automatic enumerator's portal-midpoint polyline. For consecutive waypoints A,B, compute the horizontal vector `delta=(B_x-A_x,B_z-A_z)` and normalize `d=delta/sqrt(delta_x^2+delta_z^2)`. Skip purely vertical segments. The existing `cell_routes.checkpoints.horizontal_segments` does this and merges adjacent collinear horizontal segments. Assign the relevant segment before comparing candidates; use its outgoing direction after a turn. It is a geometric scoring reference, not a fixed facing or a uniquely optimal movement direction. Each candidate's IPOPT facings remain free. A coarse guide can misrepresent the best movement within a cell, so do not treat direction-based ranking as a physical dominance proof.

For Real11's existing guide the main local directions(X,Z) are: B2->B3=(-0.707107,+0.707107), B3->B4=(-0.970143,+0.242536), B4->B5 approach=(-0.724999,+0.688749), B5 toward B6's left side=(-1,0), and after turning past B6=(0,+1). B5's guide approach point hasZ=-1095.7 rather than the physical block centerZ=-1095.5, explaining that diagonal. The B1 corner guide has two additional directions documented in the experiment audit. These segment vectors were available in the guide but were not the pilot's actual clock-score vectors: its explicit setup overrides them with normalized start-to-B5-center delta(-16,+9) at10/28/44, then +Z at55/64. Keep that implementation distinction explicit.

Proposed initial retention treatment: keep position/velocity tradeoffs explicitly, including farther-but-slower versus nearer-but-faster states, rather than immediately choosing an arbitrary conversion weight. Continue comparing time to the shared spatial goal separately. No weight, velocity margin or rigorous dominance certificate is established by this discussion.

The now-selected protection is explicit: for a comparable peerb, preserve candidatec if `s_c >= s_b-0.25`, `u_c > u_b`, or `hypot(v_cx,v_cz) > hypot(v_bx,v_bz)`, using the existing arithmetic tolerance. These are independent OR guards, with no new velocity preference margin. The magnitude guard preserves useful possibilities even when the directional projection is smaller. The spatial-time margin remains1tick, but a time disadvantage cannot bypass a position/velocity protection. Missing accepted state metadata means unknown/incomparable. This is intermediate heuristic retention; completed valid terminal solutions remain comparable on the actual final objective across schedules.

Movement/contact phase is also necessary comparison context. The Real11 state10 witnesses show jump0 has position2.392651 and forward velocity0.226887836, while jump8 has position2.721058 and forward velocity0.263399323. Both horizontal measurements favor8. Yet0 is descending with vy=-0.373904036 and8 is ascending with vy=+0.248135999; their next contact/jump opportunities differ. Protect their distinct setup families as instructed. A horizontal position/velocity frontier alone would still falsely classify0 as inferior if used across those phases. This uses vertical phase for future-event context; the progress objective remains horizontal.

Current IPOPT calls maximize position for a fixed discrete program. Reading velocity from that one returned witness does not explore the program's own continuous position/velocity tradeoff. A possible later experiment is a second fixed-input solve maximizing route-directed velocity while preserving a small allowed loss of position through an explicit row, with full physical validation. That is a proposal, not an implemented or newly selected numerical objective/weight. It would reuse the existing continuous solver and original-state/free-facing contract.

## Retention

Preserve similar candidates and time/progress tradeoffs. A slightly slower or slightly shorter-progress history is not a reason to discard it. Only mark a candidate heuristically worse when the evidence shows a meaningful disadvantage under comparable conditions, without an offsetting advantage. Unknown measurements do not count as bad measurements.

Do not construct a false dominance argument from the fastest candidate and a different farthest candidate. A genuine tradeoff may sit between those two. Even comparison with one apparently better peer is heuristic because physical/contact/velocity history can affect later choices.

Keep near alternatives, symbolic diversity, accepted incumbents and resumable search state. Resource-cap omissions are reported independently from comparisons that classify a candidate as markedly worse. At the final goal, the earlier accepted completion remains the fastest even if a later route has a larger horizontal objective.

## Commitment and provisional assumptions

Commit/retain discrete inputs and constraints within a spatial-route branch. For later decisions, re-solve from the original full state with all earlier facings free. Do not fix optimized intermediate positions or velocities. Nearby coupled decisions remain jointly considered; weak coupling of farther decisions is provisional and can be overturned by maps.

Retain alternatives and support reopening/expanded clusters when evidence warrants it. A failed numerical continuation is not a proof that the earlier decision was wrong. Successful-but-suboptimal commitments must also be checked through reference comparisons.

## Regression is part of the design

Full regression must additionally optimize/simulate the continuations of near and would-be-discarded candidates, including apparently inferior ones, to discover whether they produce the best final route. Replaying only retained results does not validate pruning.

Use unpruned/joint or exhaustive references over declared finite test domains, independently validate returned witnesses, and record every discarded candidate that ties/beats the heuristic result. Give this reference evaluation sufficient separately reported work; unknown or unevaluated branches leave the audit incomplete. On larger domains explicitly report incomplete coverage instead of claiming optimality preservation.

This is the selected new search path, not an opt-in rollout project. Existing movement/constraint builders and IPOPT remain unchanged. No runtime implementation is claimed by this planning update. The earlier source-specific failure reports remain in PRUNING_RESEARCH.md and the experiment directories; the earlier unknown grind/turn failures have already been fixed (current controls find 11 and 13 ticks respectively).
