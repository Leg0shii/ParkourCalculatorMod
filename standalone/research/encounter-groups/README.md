# Encounter groups: Real11 controls

Paired cold runs of the base (`c9deef42`) and the encounter-group branch, back to
back on one machine (load average 2.4 to 2.8), each from its own worktree with
`tools.profiling.run --seconds 300 --profile-mode bookkeeping` on a copy of the
case with `candidate_seconds` 20 and `spatial_seconds` 50 so wall-clock budgets do
not decide outcomes. Reports compare correctness; timings are indicative only.

## Group frames (`b4180095` plus group frames), 4-anchor case, 2026-11-19 01:22 PST

| Run | Best T | Best objective | First full | Search end | Requests / workers | Expanded | Replays |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| base `c9deef42` | 64 | -1093.2381026602416 | 4.7 s | 7.7 s `exhausted` | 18 / 17 | 37 | 10/10 |
| group frames | 64 | -1093.2381026602416 | 10.4 s | 15.9 s `exhausted` | 25 / 23 | 75 | 8/8 |

The base compares at B5 (frames B4 → B5 and B5 → B7); the group branch searches
B4 → B7 as one frame, so no anchor query runs at B5 and the B7 frame sees every
B5 approach before comparing. It expands twice the prefixes and sends 7 more
requests, because B5 arrivals that the base set aside at its B5 comparison now
continue to B7. Same best route, objective and validated replays.

## Side families in branches (`533e3831` plus side families), 2026-11-19

4-anchor case, 01:29 PST:

| Run | Best T | Best objective | First full | Search end | Requests / workers | Expanded | Replays |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| base `c9deef42` | 64 | -1093.2381026602416 | 4.6 s | 7.7 s `exhausted` | 18 / 17 | 37 | 10/10 |
| side families | 64 | -1093.2381026602416 | 11.2 s | 17.5 s `exhausted` | 26 / 24 | 75 | 8/8 |

Extended case (anchors B2, B3, B4, B5, B7, B20, terminal B22), 01:29 and 01:34 PST,
measurement only:

| Run | Finish | Search end | Requests | Decision checks (IPOPT infeasible) | Leg/terminal solves | Deepest request | Expanded | Replays |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| base `c9deef42` | none | 300.5 s `deadline` | 149 | 140 (11) | 9: B2, B3 ×2, B4, B5 ×2, B7 ×3 | 78 ticks | 172 | 5/5 |
| side families | none | 300.3 s `deadline` | 238 | 234 (89) | 4: B2, B3 ×2, B4 | 68 ticks | 375 | 3/3 |

Groups: start → B2, B2 → B3, B3 → B4, B4 → B20 (B5, B7, B20 inside), B20 → B22.
The base reaches its B7 frame; the group branch never completes the B4 → B20
frame within 300 s. In that frame 290 decision checks queued a sibling side
family, sibling chains reached 6 excluded families, and 398 generated variants
were skipped as owned by a sibling branch; the extra checks are mostly sibling
re-solves in the congested B5–B7 stretch, and 89 of them ended IPOPT
`Infeasible_Problem_Detected`. Per-frame generated and screened alternative
counts are not broken out by the report for decision checks (their requests
carry no owning frame).

## Lazy identity tiebreak in the frontier, 2026-11-19

`frontier.take` used to evaluate every queued program's full priority key, whose
last field hashes the whole program JSON (`dfs.candidate_key`). On the saved
B4 -> B20 queue of the extended side-families run (762 pending programs), one take
with full keys cost 0.478 s; the same take without identities 0.004 s; 8 programs
tied at the minimum on the other fields (largest tie group 33). In an unwrapped
240 s cProfile of that run, `frontier.take` accounted for 41.2 s cumulative over
278 takes, its key ran 57,452 times, and 57,475 of the 67,821
`program_physics_key` calls came through `candidate_key`.
`take_first` now hashes only the tied programs; the selected program is the same.

Extended control at `7a4dbac7`'s source (run 2026-11-19 13:39 PST, 300 s, bookkeeping
profile, same settings as above), against the side-families run:

| Run | Requests (IPOPT infeasible) | Expanded | Deepest request | `program_physics_key` calls / self s | `solver.invoke` self s |
| --- | ---: | ---: | ---: | ---: | ---: |
| side families | 238 (90) | 375 | 68 ticks | 187,426 / 93.0 | 57.1 |
| lazy tiebreak | 324 (136) | 449 | 69 ticks | 27,488 / 14.4 | 92.5 |

Neither finishes; the freed time goes to more requests in the same B4 -> B20 frame.

## Settled stretches keep exact rows, 2026-11-19

Decision checks keep the exact rows of every stretch settled at the node, and
later generators make no item for those stretches (`settledThrough`). Phase 1's
`committedFamilies` never reached child programs (`append_choice` did not copy
it), so every later solve re-prepared the earlier stretches.

4-anchor case, 16:21 PST:

| Run | Best T | Best objective | First full | Search end | Requests / workers | Expanded | Replays |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| base `c9deef42` | 64 | -1093.2381026602416 | 4.7 s | 7.8 s `exhausted` | 18 / 17 | 37 | 10/10 |
| settled rows | 64 | -1093.2381026602416 | 9.0 s | 12.6 s `exhausted` | 24 / 22 | 75 | 8/8 |

Extended case, 16:21:50 PST, measurement only (prepared encounters are summed over
the generator states saved in the report):

| Run | Requests (IPOPT infeasible) | Expanded | Deepest request | Prepared encounters (no possible phase) | `program_physics_key` calls / self s |
| --- | ---: | ---: | ---: | ---: | ---: |
| lazy tiebreak | 324 (136) | 449 | 69 ticks | 17,172 (15,280) | 27,488 / 14.4 |
| settled rows | 303 (66) | 567 | 70 ticks | 5,103 (3,423) | 40,069 / 21.3 |

Neither finishes within 300 s; the search still ends in the B4 -> B20 frame.

## Decision checks compare climbed families; held alternatives, 2026-11-19

Decision checks climb every side family at the node objective and keep the best;
each other branch choice's best point and up to 2 close switch timings are held
and restored one at a time when the branch below the node dies. The re-solving
sibling branches are gone. Measured with
`/Users/arvindmurty/CS/pkc-notes/standalone/encounter-groups-2026-11-19/phase2-investigation/groundticks.py`
(node checks and their requests from the observation log).

4-anchor case, 16:33 PST:

| Run | Best T | Best objective | First full | Search end | Requests / workers | Expanded | Replays |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| base `c9deef42` | 64 | -1093.2381026602416 | 5.0 s | 8.2 s `exhausted` | 18 / 17 | 37 | 10/10 |
| climbed node families | 64 | -1093.2381026602416 | 10.0 s | 13.6 s `exhausted` | 24 / 24 | 75 | 8/8 |

Extended case, 16:34 PST, measurement only:

| Run | Requests (IPOPT infeasible) | Node checks (feasible) | Node requests / check | Node-check seconds | Expanded | Deepest request |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| settled rows | 303 (66) | 368 (234) | 0.81 | 105.0 | 567 | 70 ticks |
| climbed node families | 398 (68) | 147 (138) | 2.68 | 146.7 | 434 | 69 ticks |

The settled-rows run queued 213 siblings; 89 were feasible, and 60 of those kept
exactly the winner's rows (their side labels differed, their settled rows did
not). The new run holds 62 alternatives at 28 nodes, restores none within 300 s,
and still ends inside the B4 -> B20 frame.

## Group growth from every member, 2026-11-19

Every touching member now extends the group (the block-table kinds only label
landings and hit/ceiling obstacles). The extended task's B4 -> B20 and B20 -> B22
groups merge into one B4 -> B22 group (anchors B5, B7, B20, B22), through the
hit-only blocks B6 and B8 that B22 touches; the 4-anchor groups are unchanged.

4-anchor case, 16:45 PST:

| Run | Best T | Best objective | First full | Search end | Requests / workers | Expanded | Replays |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| base `c9deef42` | 64 | -1093.2381026602416 | 5.1 s | 8.3 s `exhausted` | 18 / 17 | 37 | 10/10 |
| groups from every member | 64 | -1093.2381026602416 | 10.1 s | 13.9 s `exhausted` | 24 / 24 | 75 | 8/8 |

Extended case, 16:45:26 PST, measurement only: 363 requests (65 IPOPT infeasible),
136 node checks (127 feasible, 2.64 requests per check), 420 expanded, deepest
request 69 ticks, 60 held alternatives with none restored; the search ends in the
B4 -> B22 frame.

## Obstacles built from reachable members, 2026-11-19

Each landing segment builds its obstacle clusters only from the solids in its
launch block's table, and each phase builds its sides around the active members
the envelope cannot rule out. User's example: from B5 toward B7 inside the group,
B20 should not be considered. Checked with
`/Users/arvindmurty/CS/pkc-notes/standalone/encounter-groups-2026-11-19/phase2-investigation/b20check.py`
on every saved program of the extended run below (1,216 programs): in 2,558
segments launched from B5, B20 is Y-active in 24,762 phases and reachable in none.

4-anchor case, 16:51 PST:

| Run | Best T | Best objective | First full | Search end | Requests / workers | Expanded | Replays |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| base `c9deef42` | 64 | -1093.2381026602416 | 4.4 s | 7.4 s `exhausted` | 18 / 17 | 37 | 10/10 |
| reachable members | 64 | -1093.2381026602416 | 10.0 s | 13.6 s `exhausted` | 24 / 24 | 75 | 8/8 |

Extended case, 16:51:29 PST, measurement only: 387 requests (71 IPOPT infeasible),
143 node checks (134 feasible, 2.68 requests per check), 428 expanded, deepest
request 69 ticks, 61 held alternatives with none restored; the search ends in the
B4 -> B22 frame.
