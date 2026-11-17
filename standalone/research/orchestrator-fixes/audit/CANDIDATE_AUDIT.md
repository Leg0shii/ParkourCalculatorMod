# Real11 candidate and numerical-request audit

2026-11-03. Read-only audit of the three cold sessions archived by96681997. No new IPOPT solve, candidate pruning or search-policy change was made. The separate fixed-reference seeded control and unit-test solves are outside the208-call cold count. All original reports are preserved.

## Exact count and scope

| What was tried | Count | Meaning |
| --- | --- | --- |
| Prescribed spatial routes | 1 | R1, through B2/B3/B4/B5 and left of B6; B7 landing optional |
| Main movement programs evaluated | 7 | 2 prefixes at state10;5 at state28 |
| Main fixed constraint branches handled | 227 | Each row in the complete branch tables below |
| Main branches sent to IPOPT | 172 | All172 worker returns report feasible |
| Branches rejected before dispatch | 37 | Necessary reachability screen; no IPOPT call |
| Branches stopped before dispatch | 18 | Deadline/call budget; unknown, not infeasible |
| Spatial-goal program evaluations | 19 | All19 listed separately;36 worker calls |
| Total worker calls | 208 | 172 main +36 spatial =150+36+22 across sessions |
| Recorded accepted main branch validations | 108 | Valid prefixes, not64-input completions |
| Recorded route-signature rejections | 62 | 62 of62 independently physically replayed and passing |
| Missing main validation records | 2 | Geometry-validation deadline paths; exact branch mapping is partly ambiguous |
| Emitted programs still ready | 20 | Listed below; none had a main numerical evaluation |
| Symbolic prefixes awaiting/inside expansion | 16 | 15 pending +1 in-progress; listed below |
| Saved pre-numerical movement exclusions | 57 | 25 dead falls,29 interval contradictions,3 norm contradictions; all listed below |

| Session | Wall seconds | Numerical proposals | Worker calls | New main branches | Where it stopped |
| --- | --- | --- | --- | --- | --- |
| 1 | 302.737 | 182 | 150 | 149 | state28, unfinished constraint enumeration |
| 2 | 303.891 | 55 | 36 | 48 | state28, unfinished constraint enumeration |
| 3 | 303.502 | 30 | 22 | 30 | state28, unfinished constraint enumeration |

Main worker-reported elapsed time totals47.411s, versus910.130s session wall time. Spatial-worker timings were not individually saved. Compiler/footprint/scene-bridge timings sum to2.937s. The large remaining time is outside the recorded main-worker timing; these logs cannot precisely apportion it among constraint generation/LP screening, Python assembly, worker startup, copying and checkpoint I/O. It is incorrect to describe the main172 calls as failed IPOPT feasibility searches.

## Shared route, indexing and model

R1 is one prescribed spatial route: B1 start -> B2 -> B3 -> B4 -> B5 -> left passage around B6 -> final state64 maximizing Z. The route representative uses cells [2, 4, 8, 12, 16, 17, 21, 22, 26, 29, 31]. No competing spatial route was enumerated in these sessions. Projection is the geometry-derived free-space union; waypoint X728.9 is not a solver wall. Exact points, projection rectangles and geometry are in candidate-ledger.json.gz.

The saved cell IDs form the unordered set touched by the representative guide, not an ordered itinerary imposed on every solution. These are player-center free-space cells/surfaces; exact bounds are in the JSON, with the following display rounded to6 decimals. The complete declared physical blocks are the seven user-specified boxes below.

| Block | Physical AABB [minX,minY,minZ,maxX,maxY,maxZ] |
| --- | --- |
| b1 | [742, 0, -1105, 747, 5, -1103] |
| b2 | [741, 0, -1104, 742, 6, -1103] |
| b3 | [738, 0, -1101, 739, 7, -1100] |
| b4 | [734, 0, -1100, 735, 7.5, -1099] |
| b5 | [730, 0, -1096, 731, 6, -1095] |
| b6 | [730, 0, -1095, 731, 20, -1094] |
| b7 | [729, 0, -1095, 730, 7, -1094] |

| Guide cell | Dimension | X interval | Feet-Y interval | Z interval |
| --- | --- | --- | --- | --- |
| 2 | 3 | [728.700000,729.700000] | [-1.800000,7.000000] | [-1107.700000,-1095.300000] |
| 4 | 3 | [728.700000,729.700000] | [7.000000,22.200000] | [-1107.700000,-1085.300000] |
| 8 | 3 | [729.700000,731.300000] | [6.000000,20.000000] | [-1107.700000,-1095.300000] |
| 12 | 3 | [731.300000,733.700000] | [-200.000000,22.200000] | [-1107.700000,-1085.300000] |
| 16 | 3 | [733.700000,735.300000] | [7.500000,22.200000] | [-1107.700000,-1085.300000] |
| 17 | 3 | [735.300000,737.700000] | [-200.000000,22.200000] | [-1107.700000,-1085.300000] |
| 21 | 3 | [737.700000,739.300000] | [7.000000,22.200000] | [-1107.700000,-1085.300000] |
| 22 | 3 | [739.300000,740.700000] | [-200.000000,22.200000] | [-1107.700000,-1085.300000] |
| 26 | 3 | [740.700000,742.300000] | [6.000000,22.200000] | [-1107.700000,-1085.300000] |
| 29 | 3 | [741.700000,742.300000] | [5.000000,6.000000] | [-1107.700000,-1104.300000] |
| 31 | 3 | [742.300000,747.300000] | [5.000000,22.200000] | [-1107.700000,-1085.300000] |

Input t uses state t and produces state t+1. Tables use zero-based input indices and pre-input state indices. A support Y contact at state k is resolved by input k-1 and requires horizontal overlap at state k-1. Repeated B1 contacts during run inputs are support checks, not repeated voluntary jumps. X/Z facings remain free on every re-solve from the original state (746.5,5,-1104.5), horizontal velocity(0,0). Momentum is carried normally; inertia thresholds are0. All main scopes model every declared solid through that program horizon, but allow a prefix of the full route signature. No main solve reached state44/55/64.

| Gate | Block | X crossing plane | Allowed lateral Z range for side0 | R1 crossing |
| --- | --- | --- | --- | --- |
| 0 | b5 | 730.5 | [-1096.300000011921, -1094.699999988079] | [0, -1, 0] |
| 1 | b6 | 730.5 | [-1095.300000011921, -1093.699999988079] | [1, -1, -1] |
| 2 | b4 | 734.5 | [-1100.300000011921, -1098.699999988079] | [2, -1, 0] |
| 3 | b3 | 738.5 | [-1101.300000011921, -1099.699999988079] | [3, -1, 0] |
| 4 | b2 | 741.5 | [-1104.300000011921, -1102.699999988079] | [4, -1, 0] |
| 5 | b1 | 744.5 | [-1105.300000011921, -1102.699999988079] | [5, -1, 0] |

A token[gate,-1,side] means crossing its X plane toward decreasing X. side=-1/0/+1 means below/inside/above the listed Z interval. This policy classifies the horizontal trace without considering that the player may already be above the block. In particular P03-B001 crosses the B3 plane on input27 at feet Y7.176759, above B3 top7, but fails the side0 expectation because Z is below the interval. 62 of62 independently physically replayed and passing. Physical validity alone does not establish that an alternative completes the selected route or later landing.

The main clock direction is the same for all candidates: [-0.8715755371245493, 0.49026123963255896]. Relative progress subtracts the original anchor; the raw solver objective has a constant offset. These two values must not be confused. Main objectives are fixed-clock progress proxies, not route-completion times. Spatial probes separately minimize achieved time to a common support. The configured per-worker cap is2s; actual calls may have a smaller remaining allowance. Each local evaluation has8s and up to6 calls; deferred constraint work resumes from its cursor.

Only normal movement inputs actually occur inside these seven prefixes. P04/P05 retain an approach row from a stepping/blip proposal truncated before its future contact, but do not execute a step within28. There are no ladder, ceiling-hit or intentional wall-hit decisions in the evaluated prefixes. These manual checkpoints add no explicit X/Z portal rows. Ordered corridor crossings remain post-solve checks and may add repair rows before the next call, explaining the first rejected and second accepted solves for P03/P04/P05.

| Input phase | Compiled acceleration magnitude | Compiled stored-velocity drag |
| --- | --- | --- |
| jump | 0.32739998400211334 | 0.546000063419342 |
| air | 0.02599999835384377 | 0.9100000262260437 |
| ground run | 0.12999997332997262 | 0.546000063419342 |

## Every main movement program tried

| Program / saved ID | Horizon | Jump inputs | Grounded nonjump inputs | Chosen contacts / decisions | State at horizon | Main branches / IPOPT calls | Best relative progress / forward v / speed | Spatial comparison | Disposition |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| P01 / e52273dc6e0b | 10 | 0 | - | - | Y=5.495201, vy=-0.373904, air | 1 / 1 | 2.392651 / 0.226888 / 0.226888 | b2 at 21 | retained after state10 |
| P02 / 7146760ea770 | 10 | 8 | 0..7 | b1 Y-@1..8 | Y=5.753200, vy=0.248136, air | 1 / 1 | 2.721058 / 0.263399 / 0.263399 | b2 at 17 | retained after state10 |
| P03 / eab9321e65bf | 28 | 0,12,21 | - | b1 Y-@12; b2 Y-@21 | Y=7.176759, vy=-0.152335, air | 2 / 2 | 8.559762 / 0.299809 / 0.300590 | b3 at 30 | awaits state28 window completion; not heuristically pruned |
| P04 / 9b299ee1689b | 28 | 0,12,21 | - | b1 Y-@12; b2 Y-@21; extra C23@28 | Y=7.176759, vy=-0.152335, air | 2 / 2 | 8.559762 / 0.299809 / 0.300590 | b3 at 30 | awaits state28 window completion; not heuristically pruned |
| P05 / fb17cc11d181 | 28 | 0,12,21 | - | b1 Y-@12; b2 Y-@21; extra C01@28 | Y=7.176759, vy=-0.152335, air | 2 / 2 | 8.559762 / 0.299809 / 0.300590 | b3 at 30 | awaits state28 window completion; not heuristically pruned |
| P06 / 032c8f2df912 | 28 | 0,16 | 12..15 | b1 Y-@12..16,28 | Y=5.000000, vy=-0.078400, ground | 151 / 120 | 4.667929 / 0.133393 / 0.232837 | b3 at 46 | awaits state28 window completion; not heuristically pruned |
| P07 / 3106480f596d | 28 | 0,17 | 12..16 | b1 Y-@12..17 | Y=5.121297, vy=-0.444826, air | 68 / 44 | 7.173663 / 0.247851 / 0.255267 | b3 at 47 | awaits state28 window completion; not heuristically pruned |

P03/P04/P05 have identical scalar/contact/input histories. P04 additionally has the future-blip approach row Z[28]<=-1100.300001011921; P05 has X[28]>=735.3000010119209. No blip contact has occurred within these prefixes. Their physical feasible sets at28 are subsets of P03, and their accepted scores differ only around floating-point noise. P06 instead self-hops/returns to B1 after four run inputs. P07 waits five inputs on B1 and is still airborne at28. Their B3 spatial probes add future decisions; they are not already committed B3 landings in the main28-input programs.

Committed decisions: after the state10 comparison, both the immediate0 and delayed8 setup were retained as parents. At state28 no outer comparison/commit occurred because the window never finished. P03..P07 are proposed continuations of those parents, not five successive committed decisions. The engine repeatedly resumes constraint choices for the same P06/P07 movement program; all earlier facings remain variable. Spatial-probe future decisions inform comparison measurements but are not automatically adopted as the main committed continuation.

## Exact constraint notation

Every Cxx is the exact saved/reconstructed numeric inequality below. Cxx@a..b means that inequality at every integer state a through b, inclusive. A branch contains its program baseline plus the extra rows in its table. Y motion/contact schedules are fixed by the program table and full scalar profiles in the JSON ledger. Duplicate numeric inequalities are listed once in this notation; raw captured plans retain original multiplicities. Initial support bounds are copied from saved witness plans to preserve Java-derived float endpoints. Other rows are reconstructed with the existing builders; all62 captured corridor plans have exactly matching numeric constraint sets. Original names/order/multiplicity for some other branches were not saved.

| Symbol | Inequality on state t | Boundary provenance |
| --- | --- | --- |
| C01 | X[t] >= 735.3000010119209 | b4 upper X outside/avoid boundary |
| C02 | X[t] >= 739.3000010119209 | b3 upper X outside/avoid boundary |
| C03 | X[t] >= 740.7000009880791 | b2 lower X inside/support boundary |
| C04 | X[t] >= 741.7000009880791 | b1 lower X inside/support boundary |
| C05 | X[t] >= 741.7000009880793 | b1 lower X inside/support boundary |
| C06 | X[t] >= 742.3000010119209 | b2 upper X outside/avoid boundary |
| C07 | X[t] <= 742.2999990119209 | b2 upper X inside/support boundary |
| C08 | X[t] <= 747.2999990119207 | b1 upper X inside/support boundary |
| C09 | X[t] <= 747.2999990119209 | b1 upper X inside/support boundary |
| C10 | Z[t] >= -1105.299999811921 | b1 lower Z inside/support boundary |
| C11 | Z[t] >= -1105.2999990119208 | b1 lower Z inside/support boundary |
| C12 | Z[t] >= -1105.2999990119204 | b1 lower Z inside/support boundary |
| C13 | Z[t] >= -1104.299999811921 | b2 lower Z inside/support boundary |
| C14 | Z[t] >= -1104.2999990119208 | b2 lower Z inside/support boundary |
| C15 | Z[t] >= -1102.699998988079 | b1 upper Z outside/avoid boundary; b2 upper Z outside/avoid boundary |
| C16 | Z[t] >= -1101.299999811921 | b3 lower Z inside/support boundary |
| C17 | Z[t] >= -1099.699998988079 | b3 upper Z outside/avoid boundary |
| C18 | Z[t] <= -1104.300001011921 | b2 lower Z outside/avoid boundary |
| C19 | Z[t] <= -1102.7000009880796 | b1 upper Z inside/support boundary; b2 upper Z inside/support boundary |
| C20 | Z[t] <= -1102.7000009880792 | b1 upper Z inside/support boundary; b2 upper Z inside/support boundary |
| C21 | Z[t] <= -1102.700000188079 | b1 upper Z inside/support boundary; b2 upper Z inside/support boundary |
| C22 | Z[t] <= -1101.300001011921 | b3 lower Z outside/avoid boundary |
| C23 | Z[t] <= -1100.300001011921 | b4 lower Z outside/avoid boundary |
| C24 | Z[t] <= -1099.700000188079 | b3 upper Z inside/support boundary |

| Program | Baseline inequalities | Horizontal clip contacts | Fixed finite scene contacts |
| --- | --- | --- | --- |
| P01 | C05@0; C08@0; C12@0; C19@0 | [] | - |
| P02 | C04@0..7; C05@0; C08@0; C09@0..7; C11@0..7; C12@0; C19@0; C20@0..7 | [] | b1 Y-@1..8 |
| P03 | C03@20; C04@11; C05@0; C07@20; C08@0; C09@11; C11@11; C12@0; C14@20; C19@0; C20@11,20 | [] | b1 Y-@12; b2 Y-@21 |
| P04 | C03@20; C04@11; C05@0; C07@20; C08@0; C09@11; C11@11; C12@0; C14@20; C19@0; C20@11,20; C23@28 | [] | b1 Y-@12; b2 Y-@21 |
| P05 | C01@28; C03@20; C04@11; C05@0; C07@20; C08@0; C09@11; C11@11; C12@0; C14@20; C19@0; C20@11,20 | [] | b1 Y-@12; b2 Y-@21 |
| P06 | C04@11..15,27; C05@0; C08@0; C09@11..15,27; C11@11..15,27; C12@0; C19@0; C20@11..15,27 | [] | b1 Y-@12..16,28 |
| P07 | C04@11..16; C05@0; C08@0; C09@11..16; C11@11..16; C12@0; C19@0; C20@11..16 | [] | b1 Y-@12..17 |

## Every main fixed constraint branch tried

These227 rows include172 actual IPOPT calls,37 pre-dispatch rejections and18 pre-dispatch budget stops. Every IPOPT row below has a recorded feasible worker return. The objective column is shown only if the returned candidate or its final saved incumbent was preserved; it is not inferred from another branch. A displayed incumbent need not be the only accepted trajectory produced by that problem.

Selected passage notation is block:face>face with its saved gate offsets. X+/Z+ mean the greater-coordinate side, X-/Z- the lesser-coordinate side; Y+ means above the block and is checked against the fixed scalar trace. For Z+>X+ with gate26, Z+ ends at state26 and X+ begins at state27 because X movement precedes Z movement. Other orderings may share a state. The explicit Cxx@state inequalities are authoritative; face metadata is absent for some derived corridor-repair rows.

* Two post-validation records were omitted on geometry-deadline paths. Alignment is exact for the other programs and all62 route-rejection records. For P06, the missing entries can be among B001..B007 and B108..B113. Those13 rows say accepted-or-missing rather than assigning unsupported outcomes. The ledger preserves every possible alignment; the total missing count is2, not13.

### P01

| Branch | Session | Selected interactions / gates | Extra inequalities | IPOPT / elapsed s | Post-check result | Raw objective if saved | Problem hash |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P01-B001 | 1 | no passage metadata; see rows | - | feasible / 0.1398 | accepted physical prefix | -1189.732026371 (saved incumbent) | b3c1fcd4837fbc61e34c84f638f1f59040226ee0683c12cc8be8ef568e212b4d |

### P02

| Branch | Session | Selected interactions / gates | Extra inequalities | IPOPT / elapsed s | Post-check result | Raw objective if saved | Problem hash |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P02-B001 | 1 | no passage metadata; see rows | - | feasible / 0.1996 | accepted physical prefix | -1189.403619633 (saved incumbent) | 1e0a365f8b95d8bb6513da1ccca1984bbfa4761c30385663eec3b775eaed76bb |

### P03

| Branch | Session | Selected interactions / gates | Extra inequalities | IPOPT / elapsed s | Post-check result | Raw objective if saved | Problem hash |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P03-B001 | 1 | no passage metadata; see rows | - | feasible / 0.1504 | route [3, -1, 0] vs [3, -1, -1]; physical PASS | -1183.537598637 | 3c778e289d9e0c769bf324fa195a869459c774ff89db470bf025c5ba52927a30 |
| P03-B002 | 1 | no passage metadata; see rows | C16@27; C24@27 | feasible / 1.5756 | accepted physical prefix | -1183.564915812 (saved incumbent) | 0405f667830fb4c27f993b1bdea5815a1cd4eea981705e2d3f2f940d35d6d26b |

### P04

| Branch | Session | Selected interactions / gates | Extra inequalities | IPOPT / elapsed s | Post-check result | Raw objective if saved | Problem hash |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P04-B001 | 1 | no passage metadata; see rows | - | feasible / 0.1492 | route [3, -1, 0] vs [3, -1, -1]; physical PASS | -1183.537598637 | 6a4c4d9e1e9304ff93e1a4eead3ac2fd073f963353b56037b0da258375d4290a |
| P04-B002 | 1 | no passage metadata; see rows | C16@27; C24@27 | feasible / 1.7761 | accepted physical prefix | -1183.564915812 (saved incumbent) | e30641248ac79ee36a65001ca9e858b9093e11bcf53271ead38062bfc881fb23 |

### P05

| Branch | Session | Selected interactions / gates | Extra inequalities | IPOPT / elapsed s | Post-check result | Raw objective if saved | Problem hash |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P05-B001 | 1 | no passage metadata; see rows | - | feasible / 0.1480 | route [3, -1, 0] vs [3, -1, -1]; physical PASS | -1183.537598637 | 619ed0402c852a896d89c53e3dd9221d54e2f50f8c126a7f8747674f4aa85b6a |
| P05-B002 | 1 | no passage metadata; see rows | C16@27; C24@27 | feasible / 1.4522 | accepted physical prefix | -1183.564915812 (saved incumbent) | 67b707910a24b9acd2b7ab2ee5b33a9c7fb3e3e31aff95e77bc49f980c9a9ef3 |

### P06

| Branch | Session | Selected interactions / gates | Extra inequalities | IPOPT / elapsed s | Post-check result | Raw objective if saved | Problem hash |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P06-B001 | 1 | b2:Z+>X+ gates=[26] | C06@27..28; C15@16..18,24..26 | feasible / 1.0981 | accepted or missing audit* | not saved | e3a7980ef2c84b3fa0598eaf93f783e1788c499ad024eab26ba75c870e755ffc |
| P06-B002 | 1 | b2:Z+>X+ gates=[25] | C06@26..28; C15@16..18,24..25 | feasible / 1.0532 | accepted or missing audit* | not saved | 533f3e32fd01e1d3dc11264a24379c91d070480e59f3d6d2e22890d640d80c82 |
| P06-B003 | 1 | b2:Z+>X+ gates=[24] | C06@25..28; C15@16..18,24 | feasible / 1.5239 | accepted or missing audit* | not saved | e932f6ac9a44d64362d3a812b5eee93aa689ff2f473148fc60ba37bc33f6c9db |
| P06-B004 | 1 | b2:Z+>X+ gates=[18] | C06@24..28; C15@16..18 | feasible / 0.5856 | accepted or missing audit* | not saved | d3ac0b369d9335f9f09342bc82aa58073077a919ba0d49ada6cad44fe20fe565 |
| P06-B005 | 1 | b2:Z+>X+ gates=[0] | C06@16..18,24..28 | feasible / 1.1932 | accepted or missing audit* | not saved | 2a070fcedaff5205d3c935b6a39d7efe7d4784dc8a6b7586a4fb6cb3e80a4346 |
| P06-B006 | 1 | b2:Z+>X+ gates=[16] | C06@17..18,24..28; C15@16 | feasible / 0.1786 | accepted or missing audit* | not saved | 7aad96c38e1e29df487404a38ec7fbbb8ad5ff1d1237055814cd4db5a6f7233a |
| P06-B007 | 1 | b2:Z+>X+ gates=[17] | C06@18,24..28; C15@16..17 | feasible / 0.1760 | accepted or missing audit* | not saved | d8eacf089263c672e53025884f4e97703262dfde111a85465a0e7af92a1300f7 |
| P06-B008 | 1 | b2:X+>Z+>X+ gates=[16, 26] | C06@16,27..28; C15@16..18,24..26 | feasible / 0.1621 | accepted physical prefix | not saved | cffb4727e6524bfadd7f04946d7aa335192a07ed459f09e1e0ad0d83b47568ed |
| P06-B009 | 1 | b2:X+>Z+>X+ gates=[16, 25] | C06@16,26..28; C15@16..18,24..25 | feasible / 0.1770 | accepted physical prefix | not saved | 13e4138b422b3a99a1586b8743f63680bd10b3472cf923326dc7725c4ef2e63c |
| P06-B010 | 1 | b2:X+>Z+>X+ gates=[16, 24] | C06@16,25..28; C15@16..18,24 | not called / unknown | deadline | not saved | not recorded |
| P06-B011 | 1 | b2:X+>Z+>X+ gates=[16, 18] | C06@16,24..28; C15@16..18 | feasible / 0.1955 | accepted physical prefix | not saved | 1012a87c0580a743cad9810fbe6e7032f46df0e51725538ffec8be8e6953791e |
| P06-B012 | 1 | b2:X+>Z+>X+ gates=[16, 16] | C06@16..18,24..28; C15@16 | feasible / 0.1784 | accepted physical prefix | not saved | e1bb20a8591c1b7c5f9c10a7f6c905ad90d43073d668223571c20d01bc6bb14c |
| P06-B013 | 1 | b2:X+>Z+>X+ gates=[16, 17] | C06@16,18,24..28; C15@16..17 | feasible / 0.1779 | accepted physical prefix | not saved | 81ac51fc84e3594c95d3042fa1c175feb1638ce3406810955370173ac7b2509b |
| P06-B014 | 1 | b2:X+>Z+>X+ gates=[17, 26] | C06@16..17,27..28; C15@17..18,24..26 | feasible / 0.1733 | accepted physical prefix | not saved | 52beaada21f9d55b3a444e74f1b073105584cc634a88dff64250d059b3630c5b |
| P06-B015 | 1 | b2:X+>Z+>X+ gates=[17, 25] | C06@16..17,26..28; C15@17..18,24..25 | feasible / 0.1780 | accepted physical prefix | not saved | 5e3fff5d6c0cc95b80f2f5d2b443eb533b51e1d571f653d2f2a6e035835983c6 |
| P06-B016 | 1 | b2:X+>Z+>X+ gates=[17, 24] | C06@16..17,25..28; C15@17..18,24 | feasible / 0.1646 | accepted physical prefix | not saved | cab744a7b3b331d6efb6366f51921c9f7ba2bcc08c2776a77735099fd29dd109 |
| P06-B017 | 1 | b2:X+>Z+>X+ gates=[17, 18] | C06@16..17,24..28; C15@17..18 | feasible / 0.1676 | accepted physical prefix | not saved | 5c7b001b4ce75671ee2641386778fe19ccf9225dfe99b60e1709c449f014d0fb |
| P06-B018 | 1 | b2:X+>Z+>X+ gates=[17, 17] | C06@16..18,24..28; C15@17 | feasible / 0.1684 | accepted physical prefix | not saved | c2f66fbc6c2bc59a8693b61fd35b683c276027d08ace59191b8eec64c48b1a57 |
| P06-B019 | 1 | b2:X+>Z+>X+ gates=[18, 26] | C06@16..18,27..28; C15@18,24..26 | feasible / 0.1643 | accepted physical prefix | not saved | 47fcc538fdab5f9b8ea22db26586cdfd1a216d77fac8c26201fd03d352bc51ad |
| P06-B020 | 1 | b2:X+>Z+>X+ gates=[18, 25] | C06@16..18,26..28; C15@18,24..25 | feasible / 0.1658 | accepted physical prefix | not saved | ab9911fe2fdeaaf5df1bb203ed75741776c4d354420a09c9bb505a118e986ac4 |
| P06-B021 | 1 | b2:X+>Z+>X+ gates=[18, 24] | C06@16..18,25..28; C15@18,24 | feasible / 0.1504 | accepted physical prefix | not saved | 7243d23cc9ec6f0535e39c203d76ef4d83fb64497b779d75887b20a50f5a0f63 |
| P06-B022 | 1 | b2:X+>Z+>X+ gates=[18, 18] | C06@16..18,24..28; C15@18 | feasible / 0.1709 | accepted physical prefix | not saved | b7d230ba30544379f5bca0a2c17e435335dad0058293d6ebf4ca95fd07101c79 |
| P06-B023 | 1 | b2:X+>Z+>X+ gates=[19, 26] | C06@16..18,27..28; C15@24..26 | not called / unknown | numerical_call_limit | not saved | not recorded |
| P06-B024 | 1 | b2:X+>Z+>X+ gates=[19, 25] | C06@16..18,26..28; C15@24..25 | feasible / 0.1647 | accepted physical prefix | not saved | bf0b1200f82fae362a8a3536e82ba09ac112d7d7418fc595d856c11844858d0e |
| P06-B025 | 1 | b2:X+>Z+>X+ gates=[19, 24] | C06@16..18,25..28; C15@24 | feasible / 0.1666 | accepted physical prefix | not saved | e99261bec6dd387964bbd986c2b0105b30a235767a52552ec6403aa47f407cf9 |
| P06-B026 | 1 | b2:X+>Z+>X+ gates=[24, 26] | C06@16..18,24,27..28; C15@24..26 | feasible / 0.1595 | accepted physical prefix | not saved | 71a6593a370b050d0164d2e0e2f4fe26bb39784362e9b16bcba7065c277d010c |
| P06-B027 | 1 | b2:X+>Z+>X+ gates=[24, 25] | C06@16..18,24,26..28; C15@24..25 | feasible / 0.1548 | accepted physical prefix | not saved | 2d7d5e63eb27698a859adff2274d39e2613926eae8094786437ba50d06b7971e |
| P06-B028 | 1 | b2:X+>Z+>X+ gates=[24, 24] | C06@16..18,24..28; C15@24 | feasible / 0.1601 | accepted physical prefix | not saved | fc7ae1bdf3b16ccf305a3d85395152812f0e0bdb06f98dcaa97999f6d6829717 |
| P06-B029 | 1 | b2:X+>Z+>X+ gates=[25, 26] | C06@16..18,24..25,27..28; C15@25..26 | feasible / 0.1612 | accepted physical prefix | not saved | fd2bed6313fb74729e16b1d04f423901d4be4e68f7ce94a7c5432a43dd5fddd0 |
| P06-B030 | 1 | b2:X+>Z+>X+ gates=[25, 25] | C06@16..18,24..28; C15@25 | not called / unknown | numerical_call_limit | not saved | not recorded |
| P06-B031 | 1 | b2:X+>Z+>X+ gates=[26, 26] | C06@16..18,24..28; C15@26 | feasible / 0.1642 | accepted physical prefix | not saved | f59ca0827da38fcdf8a1da78324b00cf8c0f03169f4e30ec34bef672dceb41db |
| P06-B032 | 1 | b2:Z+>X+>Z+ gates=[26, 28] | C06@27..28; C15@16..18,24..26,28 | feasible / 0.1730 | accepted physical prefix | not saved | 7f6d9dd0cd84da5d651323db8b1a2d43484a06ba057ba142821a076d8b3d32a2 |
| P06-B033 | 1 | b2:Z+>X+>Z+ gates=[25, 28] | C06@26..28; C15@16..18,24..25,28 | feasible / 0.1813 | route [5, -1, 0] vs [5, -1, 1]; physical PASS | -1187.562772072 | af84e6de8f08a273d946a1bba8c44d033e670ad9149a1f810030b8d625bf654c |
| P06-B034 | 1 | no passage metadata; see rows | C06@26..28; C10@16; C15@16..18,24..25,28; C21@16 | not called / blocked | interval_reach_contradiction | not saved | c37e0c69c8177939978e93056cd96896256dbc95de6483074925733de848d360 |
| P06-B035 | 1 | b2:Z+>X+>Z+ gates=[24, 28] | C06@25..28; C15@16..18,24,28 | feasible / 0.1678 | accepted physical prefix | not saved | 97ae7623e05d8eae93876f473dcd99856da73bf7bc7713da383a9bb70743e84a |
| P06-B036 | 1 | b2:Z+>X+>Z+ gates=[18, 28] | C06@24..28; C15@16..18,28 | feasible / 0.1673 | accepted physical prefix | not saved | 2203c0ded9e368ba6008ea0ff116fde907cc6d2eb637e486823359a22cde1e75 |
| P06-B037 | 1 | b2:Z+>X+>Z+ gates=[0, 28] | C06@16..18,24..28; C15@28 | not called / unknown | deadline | not saved | not recorded |
| P06-B038 | 1 | b2:Z+>X+>Z+ gates=[16, 28] | C06@17..18,24..28; C15@16,28 | feasible / 0.1737 | accepted physical prefix | not saved | 9de7cc5b8e95c3bd4c1ae628b119007d3c6f7636300307080666fea78cbdde4f |
| P06-B039 | 1 | b2:Z+>X+>Z+ gates=[17, 28] | C06@18,24..28; C15@16..17,28 | feasible / 0.1975 | accepted physical prefix | not saved | d1714bcc10f5cf4bfdc80aaac5b9653e526d1a0d750a655bd794b61007c2fe2b |
| P06-B040 | 1 | b2:Z->Y->Z+>X+ gates=[19, 20, 20] | C06@24..28; C18@16..18 | feasible / 1.2304 | accepted physical prefix | not saved | ac7123610f3bc9c9381615c9adfedd6de10f772005bf0cf990b08d2f99ee5ee0 |
| P06-B041 | 1 | b2:Z+>Y->Z->X+ gates=[0, 1, 16] | C06@17..18,24..28; C18@16 | feasible / 0.1575 | accepted physical prefix | not saved | e58abe018631b98b50610955dd6f17cb5ff9a593e0257dceca6eb190a2d073ed |
| P06-B042 | 1 | b2:Z+>Y->Z->X+ gates=[0, 1, 17] | C06@18,24..28; C18@16..17 | feasible / 0.1586 | accepted physical prefix | not saved | 158fc37a61615759b54c2db3ad0bc41b255d6427633dafdb851fe79cdc70c877 |
| P06-B043 | 1 | b2:Z+>Y->Z->X+ gates=[0, 1, 24] | C06@25..28; C18@16..18,24 | feasible / 0.1605 | accepted physical prefix | not saved | 5b7929f4ebd56a018f4a514b43cc726dc5541617d38ee06e6c5aa89daeb90638 |
| P06-B044 | 1 | b2:Z+>Y->Z->X+ gates=[0, 1, 25] | C06@26..28; C18@16..18,24..25 | feasible / 0.1668 | accepted physical prefix | not saved | cb76fabb2881a057ffa33b13603fb01aef7bc9dd0f98e3bff721cb5e7a07cbb9 |
| P06-B045 | 1 | b2:Z+>Y->Z->X+ gates=[0, 1, 26] | C06@27..28; C18@16..18,24..26 | feasible / 0.1675 | accepted physical prefix | not saved | cf49bf871605c0e7e10f4c6faf77e3c5fb2f14eb9ca120d7f33c73620e7b4e4c |
| P06-B046 | 1 | b2:Z+>Y->Z->X+ gates=[0, 1, 27] | C06@28; C18@16..18,24..27 | not called / unknown | deadline | not saved | not recorded |
| P06-B047 | 1 | b2:X+>Z->X+ gates=[19, 24] | C06@16..18,25..28; C18@24 | feasible / 0.1471 | accepted physical prefix | not saved | cdf61f4fe014c267970a0ae7240492a2d5d745cfafd5650999d93056b3287cb5 |
| P06-B048 | 1 | b2:X+>Z->X+ gates=[19, 25] | C06@16..18,26..28; C18@24..25 | feasible / 0.1596 | accepted physical prefix | not saved | 4a4ea9f4d65452c9844ecb8a2243281807bd00e39659e4ca83ea21c0baeb661c |
| P06-B049 | 1 | b2:X+>Z->X+ gates=[19, 26] | C06@16..18,27..28; C18@24..26 | feasible / 0.1733 | accepted physical prefix | not saved | 5c988e60a2002877e6946ec28f8c8fc08cb199e4f4bd52c81c3f2392c249aa19 |
| P06-B050 | 1 | b2:X+>Z->X+ gates=[19, 27] | C06@16..18,28; C18@24..27 | feasible / 0.1593 | accepted physical prefix | not saved | 7d46f6e12405ebbeed6b2a4a0cb510cda68c9f1e62dfd508b7e0d2c53e9bdcf7 |
| P06-B051 | 1 | b2:X+>Z->X+ gates=[16, 16] | C06@16..18,24..28; C18@16 | feasible / 0.1533 | accepted physical prefix | not saved | cc92a64ffb96254780dc9fb9503f8ea1444d93da63979a83a056017f89fce292 |
| P06-B052 | 1 | b2:X+>Z->X+ gates=[16, 17] | C06@16,18,24..28; C18@16..17 | not called / unknown | deadline | not saved | not recorded |
| P06-B053 | 1 | b2:X+>Z->X+ gates=[16, 18] | C06@16,24..28; C18@16..18 | feasible / 1.2514 | accepted physical prefix | not saved | 80f61f94bea83ba3d5e33a1d037b63c78599264290c30c5da189de14f787bd96 |
| P06-B054 | 1 | b2:X+>Z->X+ gates=[16, 24] | C06@16,25..28; C18@16..18,24 | feasible / 0.1514 | accepted physical prefix | not saved | 4dabec065aa2fae17432074879eb2d056f674ca643f2dcb55010c730f3d6895d |
| P06-B055 | 1 | b2:X+>Z->X+ gates=[16, 25] | C06@16,26..28; C18@16..18,24..25 | feasible / 0.2322 | accepted physical prefix | not saved | 086951a6d146b5b067fc42cf711515b881b24b2f5ef29805949523ee2dfbe7d4 |
| P06-B056 | 1 | b2:X+>Z->X+ gates=[16, 26] | C06@16,27..28; C18@16..18,24..26 | feasible / 0.1606 | accepted physical prefix | not saved | d517f672c8cd5fe48416d4a9172a2cb3c73e3bec4db6374ac72df773f685fcc4 |
| P06-B057 | 1 | b2:X+>Z->X+ gates=[16, 27] | C06@16,28; C18@16..18,24..27 | feasible / 0.1601 | accepted physical prefix | not saved | 2023a4e20e5dc9ca381c13b859cde6b80edb762d2f30286c433489158e6d7814 |
| P06-B058 | 1 | b2:X+>Z->X+ gates=[17, 17] | C06@16..18,24..28; C18@17 | feasible / 0.1535 | accepted physical prefix | not saved | 86beae074f7cd7ab68cd3e97009710565c31e1731f285d15a651dddcfbfa823e |
| P06-B059 | 1 | b2:X+>Z->X+ gates=[17, 18] | C06@16..17,24..28; C18@17..18 | feasible / 0.1600 | accepted physical prefix | not saved | f3eab07658940ae9f9d45892b2ded6f82ce03f0ebbf80c8790cd22eac719ab59 |
| P06-B060 | 1 | b2:X+>Z->X+ gates=[17, 24] | C06@16..17,25..28; C18@17..18,24 | feasible / 0.1585 | accepted physical prefix | not saved | 4ac4e7ac002f5a179a570a545b0515439ee547ba296336b22809ed6751a646a1 |
| P06-B061 | 1 | b2:X+>Z->X+ gates=[17, 25] | C06@16..17,26..28; C18@17..18,24..25 | feasible / 0.1598 | accepted physical prefix | not saved | 2e811b02066b786773efdf1dfa4434ffc70bf24d51f912b629c1d647b5f91651 |
| P06-B062 | 1 | b2:X+>Z->X+ gates=[17, 26] | C06@16..17,27..28; C18@17..18,24..26 | feasible / 0.1745 | accepted physical prefix | not saved | a5e0422cff0355264bde1ce53174cb7b621b5c6be4828b58d56b54f6969a426d |
| P06-B063 | 1 | b2:X+>Z->X+ gates=[17, 27] | C06@16..17,28; C18@17..18,24..27 | feasible / 0.1625 | accepted physical prefix | not saved | 064a0fb9cc6d705d3974b2d7363ae537143bca44cc105711df775819e4ef9c1e |
| P06-B064 | 1 | b2:X+>Z->X+ gates=[18, 18] | C06@16..18,24..28; C18@18 | feasible / 0.1554 | accepted physical prefix | not saved | 45c81db735065745fbf2609cdea8565d1eebf4454af01047330039a775c53d3f |
| P06-B065 | 1 | b2:X+>Z->X+ gates=[18, 24] | C06@16..18,25..28; C18@18,24 | feasible / 0.1589 | accepted physical prefix | not saved | e77455d58652d50479d854480276caf5651e219e72c9d18bc2a07d442953cd7c |
| P06-B066 | 1 | b2:X+>Z->X+ gates=[18, 25] | C06@16..18,26..28; C18@18,24..25 | feasible / 0.1671 | accepted physical prefix | not saved | 90757400d136b31e303a540f861c89c2361ae4ed64da61b7cc7ed563503f419e |
| P06-B067 | 1 | b2:X+>Z->X+ gates=[18, 26] | C06@16..18,27..28; C18@18,24..26 | feasible / 0.1679 | accepted physical prefix | not saved | 8c701d54973660e0aed28a1fa903669599cc7434512f6ab2b206a5488a491070 |
| P06-B068 | 1 | b2:X+>Z->X+ gates=[18, 27] | C06@16..18,28; C18@18,24..27 | feasible / 0.1552 | accepted physical prefix | not saved | 76ceb8124ad6a8ca266943efa23d71c362e89a3870eb740002fb877d0a07edbc |
| P06-B069 | 1 | b2:X+>Z->X+ gates=[24, 24] | C06@16..18,24..28; C18@24 | feasible / 0.1551 | accepted physical prefix | not saved | 6ed233999b8a5f7d3d3c409946ff9b08480eca180513af63ce8a423729f820a4 |
| P06-B070 | 1 | b2:X+>Z->X+ gates=[24, 25] | C06@16..18,24,26..28; C18@24..25 | feasible / 0.1579 | accepted physical prefix | not saved | 5ea57732f677c4126ea3ed602d385bc5a730ad0985d0f4e0de28c45e67b80ace |
| P06-B071 | 1 | b2:X+>Z->X+ gates=[24, 26] | C06@16..18,24,27..28; C18@24..26 | feasible / 0.1612 | accepted physical prefix | not saved | ac59b565d5384e1194ea01ce13a619c21336fc275ddd9228e267aa65334403af |
| P06-B072 | 1 | b2:X+>Z->X+ gates=[24, 27] | C06@16..18,24,28; C18@24..27 | feasible / 0.2109 | accepted physical prefix | not saved | 665d1dcc25e04e623b6803fcac5ad93805f7c06cf62a2b2d924f9139d3c8b589 |
| P06-B073 | 1 | b2:X+>Z->X+ gates=[25, 25] | C06@16..18,24..28; C18@25 | feasible / 0.1597 | accepted physical prefix | not saved | 52ae7146d677d98ef0d8d1db007985b3918db37a5aa45503f7a864685d324486 |
| P06-B074 | 1 | b2:X+>Z->X+ gates=[25, 26] | C06@16..18,24..25,27..28; C18@25..26 | feasible / 0.1651 | accepted physical prefix | not saved | f94c715d157b091b0d3a90ef466fe0ce669f90f710631f954263cfb68b727d6e |
| P06-B075 | 1 | b2:X+>Z->X+ gates=[25, 27] | C06@16..18,24..25,28; C18@25..27 | feasible / 0.1627 | accepted physical prefix | not saved | d21477aadc42a740c989b5dee591474478a10a6d3d3f9dddef4d3ff4cda39d26 |
| P06-B076 | 1 | b2:X+>Z->X+ gates=[26, 26] | C06@16..18,24..28; C18@26 | feasible / 0.1580 | accepted physical prefix | not saved | 4cb3b23b20b541bcfb0acb098bdb5bb4a203623e418b1a22dbb39f62cbbc0127 |
| P06-B077 | 1 | b2:X+>Z->X+ gates=[26, 27] | C06@16..18,24..26,28; C18@26..27 | feasible / 0.1800 | accepted physical prefix | not saved | 702cf428780c28d11d1dadf51c0c97d93c06d49180a4176f0c9603734a8b33d8 |
| P06-B078 | 1 | b2:X+>Z->X+ gates=[27, 27] | C06@16..18,24..28; C18@27 | feasible / 0.1669 | accepted physical prefix | not saved | a202f62b2601dfd0e6ab9d7abf275f3c008736a9e3647c709aca005cd3a2cb0c |
| P06-B079 | 1 | b2:Z->X+>Z+ gates=[16, 28] | C06@17..18,24..28; C15@28; C18@16 | feasible / 0.1499 | accepted physical prefix | not saved | c02e504e95055c0eec7e183d9ad0ba944016d5dd805eee7d88b091c6059cd92f |
| P06-B080 | 1 | b2:Z->X+>Z+ gates=[17, 28] | C06@18,24..28; C15@28; C18@16..17 | feasible / 0.1659 | accepted physical prefix | not saved | 73ed47aaff3e2dc9e5850f0e0e364d8c926690db9cdc3e6668c6d899566e43f4 |
| P06-B081 | 1 | b2:Z->X+>Z+ gates=[18, 28] | C06@24..28; C15@28; C18@16..18 | feasible / 0.1537 | accepted physical prefix | -1187.456748264 (saved incumbent) | 7ba616a9ffa1f23d12b002592f33642d7ead17f9d6c61203af00058cb1ab909d |
| P06-B082 | 1 | b2:Z+>X+>Z- gates=[18, 28] | C06@24..28; C15@16..18; C18@28 | feasible / 0.1782 | accepted physical prefix | not saved | e0bd7ec6f98bd10eec8fbef4bf22f0cff94d0ed0261bf185560b0e34298c5013 |
| P06-B083 | 1 | b2:Z+>X+>Z- gates=[18, 27] | C06@24..27; C15@16..18; C18@27..28 | feasible / 0.2063 | accepted physical prefix | not saved | 456163285572407d10423a50a5bc465278856ab1e2ec62c6c95bc783702f898d |
| P06-B084 | 1 | b2:Z+>X+>Z- gates=[0, 28] | C06@16..18,24..28; C18@28 | feasible / 0.1592 | accepted physical prefix | not saved | a5e925e06887c431ce08c16a3393b17e809ef940ec8e39561e33c5845680c35a |
| P06-B085 | 1 | b2:Z+>X+>Z- gates=[0, 27] | C06@16..18,24..27; C18@27..28 | not called / unknown | deadline | not saved | not recorded |
| P06-B086 | 1 | b2:Z+>X+>Z- gates=[0, 26] | C06@16..18,24..26; C18@26..28 | feasible / 0.1796 | accepted physical prefix | not saved | 143d19dd5ea8033b8febc77171a7771d6297785fa6d6b1b678a1f19fc0d804c7 |
| P06-B087 | 1 | b2:Z+>X+>Z- gates=[0, 25] | C06@16..18,24..25; C18@25..28 | feasible / 0.1738 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.612935003 | 3f7ae49543f854f9c07f2a6130fc251e8afc6d3f8bd37f604fa40c5b0fecec41 |
| P06-B088 | 1 | b2:Z+>X+>Z- gates=[0, 19] | C06@16..18; C18@24..28 | feasible / 0.1821 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.591974083 | b5b4e6c0769a3e0bc3fbd7aab77224a23f6ebd7cec784415fb84bd4655a9ec9b |
| P06-B089 | 1 | no passage metadata; see rows | C06@16..18,24..25; C13@27; C18@25..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | 521f6371386f684b010267380831dc48feabe8c5626786918977ea89e9d105d1 |
| P06-B090 | 1 | b2:Z+>X+>Z- gates=[0, 24] | C06@16..18,24; C18@24..28 | feasible / 0.1657 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.591974083 | e72b1f961899364540059e164e177d0eb4be59549ad461bd102a5355ea4fa699 |
| P06-B091 | 1 | no passage metadata; see rows | C06@16..18; C13@27; C18@24..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | 9a0dcbd6c4e516c49a584bf0b2680ecd44c0e8d8c3f6d88b20c5126cdc5dacd6 |
| P06-B092 | 1 | b2:Z+>X+>Z- gates=[0, 18] | C06@16..18; C18@18,24..28 | feasible / 0.1891 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.592043452 | 00e1365583e223276eb9d0a80628f067ba6c4cea3bcf8aae27db753574a946bf |
| P06-B093 | 1 | no passage metadata; see rows | C06@16..18,24; C13@27; C18@24..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | 37b73b40dffb2fc101e8dc87c249dac8f3f4202ec5f6eafea7f5185868614cea |
| P06-B094 | 1 | b2:Z+>X+>Z- gates=[0, 17] | C06@16..17; C18@17..18,24..28 | feasible / 0.1916 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.592054679 | 43abad5c7ea5ca8bd9d92beb669e7baf04d63c8ad6e2922593881c2b28111f2e |
| P06-B095 | 1 | no passage metadata; see rows | C06@16..18; C13@27; C18@18,24..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | 1cd2c52a4b5cd78a612a10656c08247520616ee545e8ae7e15e09b20bd14d93e |
| P06-B096 | 1 | b2:Z+>X+>Z- gates=[0, 1] | C18@16..18,24..28 | feasible / 0.1694 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.592070120 | 1eae8c59905a9031293bb772e460669ac3974b3ac625899951f880bdc67256ce |
| P06-B097 | 1 | no passage metadata; see rows | C06@16..17; C13@27; C18@17..18,24..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | 53d9ef6a7d0401aaabbe4f417e936170a20283c4ad31ffeed82a3034701f7e60 |
| P06-B098 | 1 | b2:Z+>X+>Z- gates=[0, 16] | C06@16; C18@16..18,24..28 | feasible / 0.1840 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.592070120 | 028365c289a27533639fe00a3d3ef298c2c3ebab765eb9df63b4a5adf4fdbda9 |
| P06-B099 | 1 | no passage metadata; see rows | C13@27; C18@16..18,24..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | 8051302ce0a4681f910014025f3ce5c897dc05b24d7c938efca5e0702101753c |
| P06-B100 | 1 | b2:Z+>X+>Z- gates=[16, 28] | C06@17..18,24..28; C15@16; C18@28 | feasible / 0.1577 | accepted physical prefix | not saved | dc2562268e2d33d946028a56618f07c4570e1a2857560d9e9686957903ef7be5 |
| P06-B101 | 1 | no passage metadata; see rows | C06@16; C13@27; C18@16..18,24..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | 70149f29c1828942fad18d7dcf2e0d18d8191c8a9295e3ad0fc514c8ea54e819 |
| P06-B102 | 1 | b2:Z+>X+>Z- gates=[16, 27] | C06@17..18,24..27; C15@16; C18@27..28 | feasible / 0.1871 | accepted physical prefix | not saved | cf5d683981fc1162bdd85f16646c2fd040ed349487bdead832f4b904d898cbe9 |
| P06-B103 | 1 | b2:Z+>X+>Z- gates=[16, 26] | C06@17..18,24..26; C15@16; C18@26..28 | not called / unknown | deadline | not saved | not recorded |
| P06-B104 | 1 | b2:Z+>X+>Z- gates=[16, 25] | C06@17..18,24..25; C15@16; C18@25..28 | feasible / 0.1899 | accepted physical prefix | not saved | 033058eb651958a1e9c43020f2e8dc8aaea4f91b56e05b19b665b199f9fe158f |
| P06-B105 | 1 | b2:Z+>X+>Z- gates=[16, 19] | C06@17..18; C15@16; C18@24..28 | feasible / 0.1766 | accepted physical prefix | not saved | c82977c45bfb37a0b609e6d9e2d52f794aa677ae3e30dbb2d1be75b2cb6a5daf |
| P06-B106 | 1 | b2:Z+>X+>Z- gates=[16, 24] | C06@17..18,24; C15@16; C18@24..28 | feasible / 0.1814 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.823205932 | ca7eacadc976a9e9c6bcb5e172800102c807b30a1a6e2f9e53414bf9c2e42779 |
| P06-B107 | 1 | no passage metadata; see rows | C06@17..18,24; C13@27; C15@16; C18@24..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | b1b8631ab050eb3530ef368b886d65099e0db861b4797a9b02fa01d80edb221b |
| P06-B108 | 1 | b2:Z+>X+>Z- gates=[17, 28] | C06@18,24..28; C15@16..17; C18@28 | feasible / 0.1608 | accepted or missing audit* | not saved | be2f55fc96b925087730ffb2d87554c3d5888e116e5e7727a287adf0c432cee4 |
| P06-B109 | 1 | b2:Z+>X+>Z- gates=[17, 27] | C06@18,24..27; C15@16..17; C18@27..28 | feasible / 0.1812 | accepted or missing audit* | not saved | c0a3d2e66827778a4ead11dbac491aa0d01cc2e9865964c9695c75dbc9a086d1 |
| P06-B110 | 1 | b2:Z+>X+>Z- gates=[17, 26] | C06@18,24..26; C15@16..17; C18@26..28 | feasible / 0.1879 | accepted or missing audit* | not saved | 976af9124a19eaee88893d050c07f2f6661e87463f7fc0c75a33fb0e407664f7 |
| P06-B111 | 1 | b2:Z->X+>Z- gates=[16, 28] | C06@17..18,24..28; C18@16,28 | feasible / 0.1489 | accepted or missing audit* | not saved | 80ca94468a182c90e9bac445fb07a9d946a269f618a2d5882b85ad412399c61f |
| P06-B112 | 1 | b2:Z->X+>Z- gates=[16, 27] | C06@17..18,24..27; C18@16,27..28 | feasible / 0.1702 | accepted or missing audit* | not saved | e70be362ef8c0ffc6b099176b0ae882003d7ce51a52e68353317ff61f8ffb499 |
| P06-B113 | 1 | b2:Z->X+>Z- gates=[16, 26] | C06@17..18,24..26; C18@16,26..28 | feasible / 0.1732 | accepted or missing audit* | not saved | 079e468747ec695ca4791e5df06f170c47a1e7feba8e97f62bd73ebd80546f5a |
| P06-B114 | 1 | b2:Z->X+>Z- gates=[16, 25] | C06@17..18,24..25; C18@16,25..28 | feasible / 0.1738 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.613321786 | a44a8f5e99b10a898a0d8726d8201278ea0139e3d7c1945ab260ef159baf83fe |
| P06-B115 | 1 | b2:Z->X+>Z- gates=[16, 19] | C06@17..18; C18@16,24..28 | feasible / 0.1744 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.592070120 | 064d324e5a19a42e008400b651910fa9a89f6b33951c42227bdbef8f82d43a66 |
| P06-B116 | 1 | no passage metadata; see rows | C06@17..18,24..25; C13@27; C18@16,25..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | fe3909680777e57de38c9a4fa7bd27e82a06eb893e743f36af93aaf6a8160050 |
| P06-B117 | 1 | b2:Z->X+>Z- gates=[16, 24] | C06@17..18,24; C18@16,24..28 | feasible / 0.1833 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.592070120 | 99eda090cf22a3787183071aae97cf4f48bf86fe701bdf7b88e53776f79c0802 |
| P06-B118 | 1 | b2:Z->X+>Z- gates=[16, 18] | C06@17..18; C18@16,18,24..28 | feasible / 0.1865 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.592070120 | 6c236b43fc5787c2e522c1bbd4d94bcbb74703ba870dc3544a59ea7a8ddba131 |
| P06-B119 | 1 | no passage metadata; see rows | C06@17..18; C13@27; C18@16,24..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | 439c4824d84d3ac9fb3ba214e1eac304af6223476e683f10b0635fb489216b6c |
| P06-B120 | 1 | b2:Z->X+>Z- gates=[16, 17] | C06@17; C18@16..18,24..28 | feasible / 0.1800 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.592070120 | 0f39562ebf2e06ecba9633588c89670956c8ae56a679a87954a817a63c23db8a |
| P06-B121 | 1 | no passage metadata; see rows | C06@17..18,24; C13@27; C18@16,24..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | 024f91856147fea6c64dc4c6c25aa2b5b30d11a5f2f1073ad8c655e34fa6fc0c |
| P06-B122 | 1 | b2:Z->X+>Z- gates=[17, 28] | C06@18,24..28; C18@16..17,28 | feasible / 0.1595 | accepted physical prefix | not saved | 949e7e7064958cb88ee494a006448c1b864faf63eae815fddc32491a5d6ff02d |
| P06-B123 | 1 | b2:Z->X+>Z- gates=[17, 27] | C06@18,24..27; C18@16..17,27..28 | feasible / 0.1703 | accepted physical prefix | not saved | 51a0daaa8c0ecf042006f91c4d7cb1124c19a55bbd9b412426ad37da87ce0055 |
| P06-B124 | 1 | no passage metadata; see rows | C06@17..18; C13@27; C18@16,18,24..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | 35844f629dd710ac96b37673a5beb5874dd8658ed84d20c412514e897647abbb |
| P06-B125 | 1 | b2:Z->X+>Z- gates=[17, 26] | C06@18,24..26; C18@16..17,26..28 | feasible / 0.1976 | accepted physical prefix | not saved | 96f6d4952343a659c24796fb1e44aee8967c9ceb2c1ba5bf621dd32129bf20f2 |
| P06-B126 | 1 | no passage metadata; see rows | C06@17; C13@27; C18@16..18,24..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | 14bdaae7e984317fdcefff6b7c52e2a7e1e84e5da1a435f203260cb1a359ae5f |
| P06-B127 | 1 | b2:Z->X+>Z- gates=[17, 25] | C06@18,24..25; C18@16..17,25..28 | feasible / 0.1767 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.613321786 | 55ec5a8b52b1009f47d450c079beff97d1122cc5cadfa5cf18d08e1ce05ec73e |
| P06-B128 | 1 | b2:Z->X+>Z- gates=[17, 19] | C06@18; C18@16..17,24..28 | feasible / 0.1825 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.592070121 | 73b1d7d6137e23de9b87b15d34490485be5d88cd4bab6b822e80f24cbdc9918a |
| P06-B129 | 1 | no passage metadata; see rows | C06@18,24..25; C13@27; C18@16..17,25..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | c8a69bb1ecdeefb4d5be63c9adc6b442b609114a878de8cc973a9d3a646cfd90 |
| P06-B130 | 1 | b2:Z->X+>Z- gates=[17, 24] | C06@18,24; C18@16..17,24..28 | feasible / 0.1571 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.592070120 | 1d630c7a8694a0e0376cb7941d96e4b3e759539f98a4794f191cd5f4b1e3ce1b |
| P06-B131 | 1 | no passage metadata; see rows | C06@18; C13@27; C18@16..17,24..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | ad72d4d9451d5f60290986af623aa864dbd6b3eebd0992921e49b6593d9fa814 |
| P06-B132 | 1 | b2:Z->X+>Z- gates=[17, 18] | C06@18; C18@16..18,24..28 | feasible / 0.1745 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.592070120 | 861316c42177fd86c682dc4713b79cf77046a5e66af2f6879b88ecc696d11802 |
| P06-B133 | 1 | b2:Z->X+>Z- gates=[18, 28] | C06@24..28; C18@16..18,28 | feasible / 0.1688 | accepted physical prefix | not saved | 6ae7a298ce7647cfb36a085be08a90e1028f761fed587e04233527037a4d1f65 |
| P06-B134 | 1 | no passage metadata; see rows | C06@18,24; C13@27; C18@16..17,24..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | 17c53f5d59a88bbcd357a61beadb975cf7d1569a9636eb3630ed108cc0fe6de3 |
| P06-B135 | 1 | b2:Z->X+>Z- gates=[18, 27] | C06@24..27; C18@16..18,27..28 | feasible / 0.1814 | accepted physical prefix | not saved | 0d96d89284b500726b0f450dd50732f45d7d174891e167aa59c64bb6fe2ccfd7 |
| P06-B136 | 1 | no passage metadata; see rows | C06@18; C13@27; C18@16..18,24..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | 9612144b8d21ac998bc537b2f55cd78b18096b0191141f2a2cd2504bff3c5650 |
| P06-B137 | 1 | b2:Z->X+>Z- gates=[18, 26] | C06@24..26; C18@16..18,26..28 | not called / unknown | deadline | not saved | not recorded |
| P06-B138 | 1 | b2:Z->X+>Z- gates=[18, 25] | C06@24..25; C18@16..18,25..28 | feasible / 0.1815 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.614855017 | 54f3ac2b33488d0ca61bfcec0b083c13f040261280bbe6a28a1475160763887e |
| P06-B139 | 1 | no passage metadata; see rows | C06@24..25; C13@27; C18@16..18,25..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | 98bdb942a8ee557baa25924a3f7276c77319992f713a8ae7f8f68d32f73b1f0f |
| P06-B140 | 1 | b2:Z->X+>Z- gates=[18, 24] | C06@24; C18@16..18,24..28 | feasible / 0.1722 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.592070120 | 731e7e63d5f5bbb71a53258ac0789d58f8a0864da3c3c01937051fe688102048 |
| P06-B141 | 1 | no passage metadata; see rows | C06@24; C13@27; C18@16..18,24..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | a1bd78343fe7904e668ed4d01cae500caa75b925ebbe6086e529f5011d6ba413 |
| P06-B142 | 2 | b2:Z->X+>Z- gates=[24, 28] | C06@25..28; C18@16..18,24,28 | feasible / 0.1578 | accepted physical prefix | not saved | b94060045b921b657850619724c7a85668d202a4f17a9fbe21f7f078d2c59133 |
| P06-B143 | 2 | b2:Z->X+>Z- gates=[24, 27] | C06@25..27; C18@16..18,24,27..28 | feasible / 0.1783 | accepted physical prefix | not saved | 19ea5a43ed4f8168ff05a3e24ce1fd6ef0dc94d5c461dd31f26db45ba49f54c5 |
| P06-B144 | 2 | b2:Z->X+>Z- gates=[24, 26] | C06@25..26; C18@16..18,24,26..28 | feasible / 0.1804 | accepted physical prefix | not saved | 3223ed23396d81247ad5bbfb5d4e8ec21b66120396db04f5e85243a5626c8d4c |
| P06-B145 | 2 | b2:Z->X+>Z- gates=[24, 25] | C06@25; C18@16..18,24..28 | feasible / 0.1749 | route [4, -1, 0] vs [4, -1, -1]; physical PASS | -1187.613321786 | c1b9f7776e36e4135df5d3ab8d4f18ba7cf52d3dabecde45eedc7ffa61194b34 |
| P06-B146 | 2 | b2:Z->X+>Z- gates=[25, 28] | C06@26..28; C18@16..18,24..25,28 | feasible / 0.1589 | accepted physical prefix | not saved | 2df3630b40f91caf2bdac944e7079ad1c23faf7a96f558af13c933fb4a18103a |
| P06-B147 | 2 | no passage metadata; see rows | C06@25; C13@27; C18@16..18,24..28; C21@27 | not called / blocked | interval_reach_contradiction | not saved | 6fff702ff08547a7f50351b4ee00e0bef2637cd8b97ee161b756a75270adbd97 |
| P06-B148 | 2 | b2:Z->X+>Z- gates=[25, 27] | C06@26..27; C18@16..18,24..25,27..28 | feasible / 0.1672 | accepted physical prefix | not saved | 3cf0bd9e21946c4ba8bb46f8138e46093ebbec7d4d701418f93ecd2aa1036dc9 |
| P06-B149 | 2 | b2:Z->X+>Z- gates=[25, 26] | C06@26; C18@16..18,24..28 | feasible / 0.1891 | accepted physical prefix | not saved | cea0b265fffb01af727cf26dabb9d1d2724d2481060a3058312cc78c94817e9b |
| P06-B150 | 2 | b2:Z->X+>Z- gates=[26, 28] | C06@27..28; C18@16..18,24..26,28 | not called / unknown | deadline | not saved | not recorded |
| P06-B151 | 2 | b2:Z->X+>Z- gates=[26, 27] | C06@27; C18@16..18,24..28 | feasible / 0.1663 | accepted physical prefix | not saved | 85a3ec8614fee31921e3f3c8ee9dc9f277b2a1e4545b51373bbc7900d68a23ec |

### P07

| Branch | Session | Selected interactions / gates | Extra inequalities | IPOPT / elapsed s | Post-check result | Raw objective if saved | Problem hash |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P07-B001 | 2 | b2:X+>Z+ gates=[17]; b3:X+ gates=[] | C02@27..28; C06@16..17; C15@17..19,25..28 | feasible / 0.1443 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.242984523 | e33bdabc428d9938052b213b8ed2c3eb61855ff5a0144c817cd161148b8d4fdf |
| P07-B002 | 2 | no passage metadata; see rows | C02@27..28; C06@16..17; C13@20; C15@17..19,25..28; C21@20 | feasible / 0.1479 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.680431054 | 926aa5bb85c1d562a45e1bff718fff6e7ca84aba90e2945927f818c399d736a5 |
| P07-B003 | 2 | b2:X+>Z+ gates=[17]; b3:Z->X+ gates=[27] | C02@28; C06@16..17; C15@17..19,25..28; C22@27 | feasible / 0.1441 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.255737589 | 4c74d54c6cfa207918752702a87f24cc8d9a0fffef7c4fb282a87bcca89e4049 |
| P07-B004 | 2 | no passage metadata; see rows | C02@28; C06@16..17; C13@19; C15@17..19,25..28; C21@19; C22@27 | not called / blocked | interval_reach_contradiction | not saved | 4bcf9ef81689455a1ae8ddac80ffe0c1ac1e7b4ab8befe0fd39382638558f796 |
| P07-B005 | 2 | b2:X+>Z+ gates=[17]; b3:Z+>X+ gates=[27] | C02@28; C06@16..17; C15@17..19,25..28; C17@27 | feasible / 0.1501 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.946669566 | 9e27f31097decbbc6eef9386638f4e0cb4277d87589de33bcb6f626bebb5eac2 |
| P07-B006 | 2 | b2:X+>Z+ gates=[17]; b3:X+>Z->X+ gates=[27, 27] | C02@27..28; C06@16..17; C15@17..19,25..28; C22@27 | feasible / 0.2118 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.255737589 | cb840fcdaf3fa78cb159e5f6d0fca3864b1388009d15733cce890a8f7773373a |
| P07-B007 | 2 | no passage metadata; see rows | C02@28; C06@16..17; C13@25; C15@17..19,25..28; C17@27; C21@25 | not called / blocked | interval_reach_contradiction | not saved | c053dba11091055027bbd649d9984f84897f31ace30a40fddd10ae63bc136200 |
| P07-B008 | 2 | b2:X+>Z+ gates=[17]; b3:X+>Z+>X+ gates=[27, 27] | C02@27..28; C06@16..17; C15@17..19,25..28; C17@27 | feasible / 0.1714 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.946669566 | 7d959da357f76e60910da908de3b2e93dfe67aae278ddc0eae97e875923efb1f |
| P07-B009 | 2 | b2:X+>Z+ gates=[17]; b3:X+>Z+ gates=[28] | C02@27..28; C06@16..17; C15@17..19,25..28; C17@28 | feasible / 0.2701 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.655541160 | d868c6eea7b8794f1fc877b86ef47d4ff847b1ce508ee9d3938c4721e3f710c9 |
| P07-B010 | 2 | no passage metadata; see rows | C02@27..28; C06@16..17; C13@19; C15@17..19,25..28; C21@19; C22@27 | not called / blocked | interval_reach_contradiction | not saved | 672c192201e90387b233f96c4478c4b4bfda85c8881f76996731bed1b7158451 |
| P07-B011 | 2 | b2:X+>Z+ gates=[17]; b3:X+>Z+ gates=[0] | C06@16..17; C15@17..19,25..28; C17@27..28 | feasible / 0.1666 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.946669566 | cfe4e4a63db5ff6611872d0ae74f855201915647d6c34ea682031c832e3db9bb |
| P07-B012 | 2 | b2:X+>Z+ gates=[17]; b3:X+>Z+ gates=[27] | C02@27; C06@16..17; C15@17..19,25..28; C17@27..28 | feasible / 0.1707 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.946669566 | b33ff62cb195e9c3e04422858e6bb8b26458ec04b030d0b1ee1be5dc7e5d2f72 |
| P07-B013 | 2 | no passage metadata; see rows | C02@27..28; C06@16..17; C13@25; C15@17..19,25..28; C17@27; C21@25 | not called / blocked | interval_reach_contradiction | not saved | ef7b9800757e5045c098735007b09345cb1a2e1df59815e04f00f171ecfff314 |
| P07-B014 | 2 | b2:X+>Z+ gates=[17]; b3:X+>Z- gates=[28] | C02@27..28; C06@16..17; C15@17..19,25..28; C22@28 | feasible / 0.1674 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.280499182 | 71c2060a170016e659c4f00fa983ef210cbc6fae7a27a1bcd7070d0a0642fa55 |
| P07-B015 | 2 | no passage metadata; see rows | C02@27..28; C06@16..17; C13@22; C15@17..19,25..28; C17@28; C21@22 | not called / blocked | interval_reach_contradiction | not saved | 46048570376d29c646b411e21693048e525fbd0af1e1eabba3d21d8403f6b282 |
| P07-B016 | 2 | b2:X+>Z+ gates=[17]; b3:X+>Z- gates=[0] | C06@16..17; C15@17..19,25..28; C22@27..28 | feasible / 0.1743 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.267490095 | b027eeac7cef819ecd9ae0cfff8eb70bcfe529afc6315de89f21a89add62fe0d |
| P07-B017 | 2 | b2:X+>Z+ gates=[17]; b3:X+>Z- gates=[27] | C02@27; C06@16..17; C15@17..19,25..28; C22@27..28 | feasible / 0.1711 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.267490095 | e3505f12ae4145a7e2a445533e01151018db9ac8cbafcf70c728bc811fff0d8e |
| P07-B018 | 2 | no passage metadata; see rows | C06@16..17; C13@25; C15@17..19,25..28; C17@27..28; C21@25 | not called / blocked | interval_reach_contradiction | not saved | d29e7b4ff055cd5749ef259a0a1f9d253ed956faa4e87b1ba5c7aee75316ea32 |
| P07-B019 | 2 | b2:X+>Z+ gates=[18]; b3:X+ gates=[] | C02@27..28; C06@16..18; C15@18..19,25..28 | feasible / 0.2810 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.452200537 | d021e07c073423b7b00275a3ed8e8eccc25cac330c66edebed14d7edc5081160 |
| P07-B020 | 2 | no passage metadata; see rows | C02@27; C06@16..17; C13@25; C15@17..19,25..28; C17@27..28; C21@25 | not called / blocked | interval_reach_contradiction | not saved | 0b7ddbfb00da796e99486208ac7b1f9fa4638b9f3b84eca2701422a61b9d6444 |
| P07-B021 | 2 | b2:X+>Z+ gates=[18]; b3:Z->X+ gates=[27] | C02@28; C06@16..18; C15@18..19,25..28; C22@27 | not called / unknown | deadline | not saved | not recorded |
| P07-B022 | 2 | b2:X+>Z+ gates=[18]; b3:Z+>X+ gates=[27] | C02@28; C06@16..18; C15@18..19,25..28; C17@27 | feasible / 0.2615 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.946669566 | 31e5bc7b3343ebc7676c7caf258c291107e66fc2dc1c43dcfd1d2bb230cc808c |
| P07-B023 | 2 | no passage metadata; see rows | C02@27..28; C06@16..17; C13@19; C15@17..19,25..28; C21@19; C22@28 | not called / blocked | interval_reach_contradiction | not saved | 9eb75c32102c29cb9acfb4ee1a3e0628bcd668f379a02a1b6ea54ab94298a91c |
| P07-B024 | 2 | b2:X+>Z+ gates=[18]; b3:X+>Z->X+ gates=[27, 27] | C02@27..28; C06@16..18; C15@18..19,25..28; C22@27 | feasible / 0.1641 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.519112778 | 709be27d7d6284587b82253f95fd19c6404c18440f7f5f4446b7b6cda272a0ea |
| P07-B025 | 2 | no passage metadata; see rows | C06@16..17; C13@19; C15@17..19,25..28; C21@19; C22@27..28 | not called / blocked | interval_reach_contradiction | not saved | 522204b379282efb76800446d11e174b3e1b949a651a409344c8d60ae1ccff41 |
| P07-B026 | 2 | b2:X+>Z+ gates=[18]; b3:X+>Z+>X+ gates=[27, 27] | C02@27..28; C06@16..18; C15@18..19,25..28; C17@27 | feasible / 0.2184 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.946669566 | c46a77ee8018449a591ce5c625cfc552c26b250a8bb98f0cb9a75b9a0d45cfc7 |
| P07-B027 | 2 | no passage metadata; see rows | C02@27; C06@16..17; C13@19; C15@17..19,25..28; C21@19; C22@27..28 | not called / blocked | interval_reach_contradiction | not saved | e1a60a340a8e44c808e8f9cb638dd8ee0cd4ba9e4aae2886fc1c184497152ae2 |
| P07-B028 | 2 | b2:X+>Z+ gates=[18]; b3:X+>Z+ gates=[28] | C02@27..28; C06@16..18; C15@18..19,25..28; C17@28 | not called / unknown | deadline | not saved | not recorded |
| P07-B029 | 2 | b2:X+>Z+ gates=[18]; b3:X+>Z+ gates=[0] | C06@16..18; C15@18..19,25..28; C17@27..28 | feasible / 0.2593 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.946669566 | de31f8bbc83a960d346b4987070c2c1435cf9cf229ee0a6ca877b865a8eaf687 |
| P07-B030 | 2 | no passage metadata; see rows | C02@27..28; C06@16..18; C13@21; C15@18..19,25..28; C21@21 | feasible / 2.0311 | accepted physical prefix | not saved | 24f4736a1e94ff2cb6f35fd09ba6c38749bad349357b3f1b1abb8a0f051830e0 |
| P07-B031 | 2 | b2:X+>Z+ gates=[18]; b3:X+>Z+ gates=[27] | C02@27; C06@16..18; C15@18..19,25..28; C17@27..28 | not called / unknown | deadline | not saved | not recorded |
| P07-B032 | 2 | b2:X+>Z+ gates=[18]; b3:X+>Z- gates=[28] | C02@27..28; C06@16..18; C15@18..19,25..28; C22@28 | feasible / 0.2289 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.541916940 | 82e84de33fe441f8c6a6809eb121a05939e1a6de01ec82c4262be3379ddd842f |
| P07-B033 | 2 | b2:X+>Z+ gates=[18]; b3:X+>Z- gates=[0] | C06@16..18; C15@18..19,25..28; C22@27..28 | feasible / 0.1860 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.541980129 | c750363fe8af5677be45d38ebef3b0424133e21c455f37be2401f3828c6ef128 |
| P07-B034 | 2 | no passage metadata; see rows | C02@28; C06@16..18; C13@25; C15@18..19,25..28; C17@27; C21@25 | not called / blocked | interval_reach_contradiction | not saved | 6cca5e5964d75bc81b9218bca88654cb56e585595e3573b1ec2712db90163b1d |
| P07-B035 | 2 | b2:X+>Z+ gates=[18]; b3:X+>Z- gates=[27] | C02@27; C06@16..18; C15@18..19,25..28; C22@27..28 | feasible / 0.1689 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.541980129 | ffe5f48e35f2431b3beeabf525c9a0c60bda1df1ccb0b7c25104675538ae2774 |
| P07-B036 | 2 | no passage metadata; see rows | C02@27..28; C06@16..18; C13@21; C15@18..19,25..28; C21@21; C22@27 | not called / unknown | deadline | not saved | not recorded |
| P07-B037 | 2 | b2:X+>Z+ gates=[19]; b3:X+ gates=[] | C02@27..28; C06@16..19; C15@19,25..28 | not called / unknown | deadline | not saved | not recorded |
| P07-B038 | 2 | b2:X+>Z+ gates=[19]; b3:Z->X+ gates=[27] | C02@28; C06@16..19; C15@19,25..28; C22@27 | feasible / 0.2489 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.750188609 | b5341b3b71ddcd37ae4d7a5cc8ef3a5b4253084b457a1b610f5b1efb49e9faf7 |
| P07-B039 | 3 | b2:X+>Z+ gates=[19]; b3:Z+>X+ gates=[27] | C02@28; C06@16..19; C15@19,25..28; C17@27 | feasible / 0.1692 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.953399021 | e97aa389edf1434eb023fa43dc45a165410dec7fffff918ed1d12ddc3425bbf4 |
| P07-B040 | 3 | no passage metadata; see rows | C02@27..28; C06@16..18; C13@25; C15@18..19,25..28; C17@27; C21@25 | not called / blocked | interval_reach_contradiction | not saved | 216bb72b3188f45df4c1822455df50c5a08a92588f0587dd19c88e0b518ef00f |
| P07-B041 | 3 | b2:X+>Z+ gates=[19]; b3:X+>Z->X+ gates=[27, 27] | C02@27..28; C06@16..19; C15@19,25..28; C22@27 | feasible / 0.1805 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.750188609 | fe61249944fbc3e2ec3026ed39c8ec97be7320a2b9f6b7e563c5873e28cc71bc |
| P07-B042 | 3 | b2:X+>Z+ gates=[19]; b3:X+>Z+>X+ gates=[27, 27] | C02@27..28; C06@16..19; C15@19,25..28; C17@27 | feasible / 0.1697 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.953399021 | 53188b220b69d2087abc03c25593d12f41f3fb6dda12a52900d3e86ab9d16e69 |
| P07-B043 | 3 | no passage metadata; see rows | C06@16..18; C13@25; C15@18..19,25..28; C17@27..28; C21@25 | not called / blocked | interval_reach_contradiction | not saved | cbccb36b9ecfc0d43f1a8dd697a267d565770ad6c20f139f14b1f667e04bf04c |
| P07-B044 | 3 | b2:X+>Z+ gates=[19]; b3:X+>Z+ gates=[28] | C02@27..28; C06@16..19; C15@19,25..28; C17@28 | feasible / 0.1627 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.770847609 | 5a24d99f7b06d0a47ade6117c106f0427653347d38c76643530c146e4b6abccf |
| P07-B045 | 3 | no passage metadata; see rows | C02@27..28; C06@16..18; C13@20; C15@18..19,25..28; C21@20; C22@28 | feasible / 2.0726 | accepted physical prefix | not saved | 213817f2ed1bb936ac0e25cc9f65dd6980c913fa87903f53baf85b53ba3394dd |
| P07-B046 | 3 | b2:X+>Z+ gates=[19]; b3:X+>Z+ gates=[0] | C06@16..19; C15@19,25..28; C17@27..28 | feasible / 0.1775 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.953399021 | 80ecfbab8d248ad89b2aa2c124ca65fc392d2eb0cb807d14f6b5a75bce41e067 |
| P07-B047 | 3 | no passage metadata; see rows | C06@16..18; C13@20; C15@18..19,25..28; C21@20; C22@27..28 | feasible / 2.1254 | accepted physical prefix | not saved | ef7893f0509c5d0a57a91cd51d355f1e65de949ff5b0d927039687f76e254239 |
| P07-B048 | 3 | b2:X+>Z+ gates=[19]; b3:X+>Z+ gates=[27] | C02@27; C06@16..19; C15@19,25..28; C17@27..28 | not called / unknown | deadline | not saved | not recorded |
| P07-B049 | 3 | b2:X+>Z+ gates=[19]; b3:X+>Z- gates=[28] | C02@27..28; C06@16..19; C15@19,25..28; C22@28 | feasible / 0.1649 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.775952357 | 9c67480c778e7d2180e2583af0e317023ee07a4c624e6a2ac89ff33a0ff8f586 |
| P07-B050 | 3 | b2:X+>Z+ gates=[19]; b3:X+>Z- gates=[0] | C06@16..19; C15@19,25..28; C22@27..28 | feasible / 0.1680 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.775952357 | 47005e0cb566f623fe4e8677e0903eb23e27f3a932bae9db1093fbe35dcfe976 |
| P07-B051 | 3 | no passage metadata; see rows | C02@27; C06@16..18; C13@20; C15@18..19,25..28; C21@20; C22@27..28 | feasible / 1.0933 | accepted physical prefix | -1184.951014815 (saved incumbent) | 32b990dfff4a021be82c228a59d5b9a4c0b674a66925fc66d79ea7acb402781a |
| P07-B052 | 3 | b2:X+>Z+ gates=[19]; b3:X+>Z- gates=[27] | C02@27; C06@16..19; C15@19,25..28; C22@27..28 | feasible / 0.1669 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.775952357 | 55ea04dfbb46300ca52fa4f3bd723f71b42ba1ed37f963dd4c06d5fc94a8d3ef |
| P07-B053 | 3 | b2:X+>Z+ gates=[20]; b3:X+ gates=[] | C02@27..28; C06@16..19; C15@25..28 | not called / unknown | deadline | not saved | not recorded |
| P07-B054 | 3 | b2:X+>Z+ gates=[20]; b3:Z->X+ gates=[27] | C02@28; C06@16..19; C15@25..28; C22@27 | feasible / 0.1485 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.750307851 | 45dc4b4d6628e526c190fb0fe3e109c0c159f756e8ccdb459a7eb1d4bd8e74c5 |
| P07-B055 | 3 | no passage metadata; see rows | C02@28; C06@16..19; C13@22; C15@19,25..28; C21@22; C22@27 | feasible / 0.1653 | accepted physical prefix | not saved | fc6346657021c08fe265c23a9c901d7b9a49f1e34965aba2afa6048f94216480 |
| P07-B056 | 3 | b2:X+>Z+ gates=[20]; b3:Z+>X+ gates=[27] | C02@28; C06@16..19; C15@25..28; C17@27 | feasible / 0.1582 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.953399021 | d54176b3e208d0aa5a03da92d8237ffed67b2e213a81baa1ecd4df3238f8ae54 |
| P07-B057 | 3 | b2:X+>Z+ gates=[20]; b3:X+>Z->X+ gates=[27, 27] | C02@27..28; C06@16..19; C15@25..28; C22@27 | feasible / 0.1760 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.750307851 | aece6db94c3bdbb5a62feb9a0edfdb656cf1ce0f732a333f6838c07557bf3104 |
| P07-B058 | 3 | no passage metadata; see rows | C02@28; C06@16..19; C13@25; C15@19,25..28; C17@27; C21@25 | not called / blocked | interval_reach_contradiction | not saved | e80a7951baa41ce479145bd39b899f113bfec536abe320a3bd30e6ce2ce97b4e |
| P07-B059 | 3 | b2:X+>Z+ gates=[20]; b3:X+>Z+>X+ gates=[27, 27] | C02@27..28; C06@16..19; C15@25..28; C17@27 | not called / unknown | deadline | not saved | not recorded |
| P07-B060 | 3 | b2:X+>Z+ gates=[20]; b3:X+>Z+ gates=[28] | C02@27..28; C06@16..19; C15@25..28; C17@28 | feasible / 0.1539 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.770847609 | e71f8dae70ac5984d4b15a70708f58023f1d1cf7dcc09be4e0c731c2eccdd849 |
| P07-B061 | 3 | no passage metadata; see rows | C02@27..28; C06@16..19; C13@22; C15@19,25..28; C21@22; C22@27 | feasible / 0.1591 | accepted physical prefix | not saved | 08de716d84ed81fbfe39f380d52791b6d39c0d4e9116bca987545f5954ee7d62 |
| P07-B062 | 3 | b2:X+>Z+ gates=[20]; b3:X+>Z+ gates=[0] | C06@16..19; C15@25..28; C17@27..28 | feasible / 0.2275 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.953399021 | bc28ba4fc3631b157ff12749c80bbcd69f8d4dc6f224c19a8c7ed2cbef083fa3 |
| P07-B063 | 3 | no passage metadata; see rows | C02@27..28; C06@16..19; C13@25; C15@19,25..28; C17@27; C21@25 | not called / blocked | interval_reach_contradiction | not saved | e5b46acdf1ec8e5e9baac2aaafe397600f03a4f202268a5ce1564a7ffb07f0f9 |
| P07-B064 | 3 | b2:X+>Z+ gates=[20]; b3:X+>Z+ gates=[27] | C02@27; C06@16..19; C15@25..28; C17@27..28 | feasible / 0.1588 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.953399021 | b157cecef84e34fedf7b92a8589da426440ca93df7cdc60f402a4115125be552 |
| P07-B065 | 3 | b2:X+>Z+ gates=[20]; b3:X+>Z- gates=[28] | C02@27..28; C06@16..19; C15@25..28; C22@28 | feasible / 0.1544 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.775952357 | 5cf703e974cb6fc0e2e9eb29bda7042957ccb540a88ad3f6e82525678b13f4a4 |
| P07-B066 | 3 | b2:X+>Z+ gates=[20]; b3:X+>Z- gates=[0] | C06@16..19; C15@25..28; C22@27..28 | feasible / 0.1694 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.775952357 | a81e76bc415727c04441a20af907dc1c0a22a77b6db1f47d5a98b5594724b5cc |
| P07-B067 | 3 | no passage metadata; see rows | C02@27..28; C06@16..19; C13@23; C15@19,25..28; C17@28; C21@23 | not called / blocked | interval_reach_contradiction | not saved | 75ec4b9dbc69c1aabbb4720265e8e0657caf0abb8022581b4903f436560b1d75 |
| P07-B068 | 3 | b2:X+>Z+ gates=[20]; b3:X+>Z- gates=[27] | C02@27; C06@16..19; C15@25..28; C22@27..28 | feasible / 0.1690 | route [4, -1, 0] vs [4, -1, 1]; physical PASS | -1184.775952357 | 78f02244d1c311f7bfa644ca47e88caf9dbbc4d82856380a5a5a27417524cf16 |

## Every spatial-goal program evaluation

These19 evaluations consume36 additional worker calls. They deliberately add future decisions to compare time-to-support; they are not additional main spatial routes. Only each probe's best program/witness and aggregate per-evaluation counts were retained. Full per-call constraint alternatives/returns for the other calls cannot be recovered from these archives. A feasible probe result proves an accepted witness existed, not that every worker return in its allowance was feasible.

| Evaluation | Parent / target | Candidate horizon | Calls | Returned result | Saved winning decisions / exact constraints |
| --- | --- | --- | --- | --- | --- |
| P01-S01 | P01 -> b2 | 21 | 1 | feasible / contact_search_exhausted | jumps 0,12; hash e838b866eae6d187e632862e26e0915d0a956a05dde603de2747fae7db74aaca; full plan in JSON |
| P01-S02 | P01 -> b2 | 22 | 1 | feasible / contact_search_exhausted | not saved for this evaluation |
| P01-S03 | P01 -> b2 | 23 | 4 | feasible / numerical_call_limit | not saved for this evaluation |
| P02-S01 | P02 -> b2 | 17 | 1 | feasible / contact_search_exhausted | jumps 8; hash c341eef87b4b285e501e33ddd76cbdb09d8f7116a5ae9712aa02f329a9b7ac88; full plan in JSON |
| P02-S02 | P02 -> b2 | 29 | 5 | feasible / numerical_call_limit | not saved for this evaluation |
| P03-S01 | P03 -> b3 | 30 | 1 | feasible / contact_search_exhausted | jumps 0,12,21; hash fe6f24a6f9aa8431ea1c2aa518965612fc1da6a4a17ef4cb310d18d0ffc6930a; full plan in JSON |
| P03-S02 | P03 -> b3 | 42 | 1 | feasible / contact_search_exhausted | not saved for this evaluation |
| P03-S03 | P03 -> b3 | 43 | 1 | feasible / contact_search_exhausted | not saved for this evaluation |
| P03-S04 | P03 -> b3 | 44 | 1 | feasible / contact_search_exhausted | not saved for this evaluation |
| P04-S01 | P04 -> b3 | 30 | 1 | feasible / contact_search_exhausted | jumps 0,12,21; hash a85ab278de3f358fe23067c05a877fd3762c09ad666059e328625a0a5cc44bbe; full plan in JSON |
| P04-S02 | P04 -> b3 | 42 | 1 | feasible / contact_search_exhausted | not saved for this evaluation |
| P04-S03 | P04 -> b3 | 43 | 1 | feasible / contact_search_exhausted | not saved for this evaluation |
| P04-S04 | P04 -> b3 | 44 | 1 | feasible / contact_search_exhausted | not saved for this evaluation |
| P05-S01 | P05 -> b3 | 30 | 1 | feasible / contact_search_exhausted | jumps 0,12,21; hash 00d786ff365b0a1db93301dec7eb007bb2e2803e5e34e7926e4233d0beab33d7; full plan in JSON |
| P05-S02 | P05 -> b3 | 42 | 1 | feasible / contact_search_exhausted | not saved for this evaluation |
| P05-S03 | P05 -> b3 | 43 | 1 | feasible / contact_search_exhausted | not saved for this evaluation |
| P05-S04 | P05 -> b3 | 44 | 1 | feasible / contact_search_exhausted | not saved for this evaluation |
| P06-S01 | P06 -> b3 | 46 | 6 | feasible / numerical_call_limit | jumps 0,16,28,37; hash 10dc91058cd9ed6ac14efa1550d1f7c06214b29f46e38a78f5b4a8b72dcc308e; full plan in JSON |
| P07-S01 | P07 -> b3 | 47 | 6 | feasible / numerical_call_limit | jumps 0,17,29,38; hash 79c1eccb017c7218df5dc618ce233df457ec46e1e9db63eeed0000e802a9adb0; full plan in JSON |

## Every worker-call slot, including probe calls with logging gaps

The208-row [worker-call table](WORKER_CALLS.md) and [CSV](worker-calls.csv) enumerate each dispatch. Main calls link to the complete branch tables above. Probe call slots are numbered within their saved evaluation; exact individual returns/hashes are marked unavailable where not persisted. This numbering does not claim a globally timestamped order across interleaved scoring/probe work.

## Every emitted but untried program, and pending symbolic prefix

Q rows are complete28-input programs already emitted into the ready batch. F rows are shorter symbolic prefixes awaiting expansion, not failed numerical candidates. All use R1 and the original state. Selected constraints/contact details for each are preserved in the JSON ledger. None of these rows has been main-scored. In particular Q09 already specifies a B3 landing at28 and F11 a B3 landing at27; these potentially useful branches were pending, not pruned by a numerical failure.

| ID / saved ID | State / status | Jumps | Run inputs | Selected contacts | Y / vy / ground | Extra approach rows |
| --- | --- | --- | --- | --- | --- | --- |
| Q01 / fc9de5d96563 | 28 / emitted_ready_not_evaluated | 0,17 | 12..16 | b1 Y-@12..17 | 5.121297 / -0.444826 / False | - |
| Q02 / fbadef789e58 | 28 / emitted_ready_not_evaluated | 8,17 | 0..7 | b1 Y-@1..8; b2 Y-@17 | 6.121297 / -0.444826 / False | - |
| Q03 / fb389af0b5d0 | 28 / emitted_ready_not_evaluated | 8,17 | 0..7 | b1 Y-@1..8; b2 Y-@17 | 6.121297 / -0.444826 / False | - |
| Q04 / 461a79114d6c | 28 / emitted_ready_not_evaluated | 0,18 | 12..17 | b1 Y-@12..18 | 5.495201 / -0.373904 / False | - |
| Q05 / 5647dcd53993 | 28 / emitted_ready_not_evaluated | 8,18 | 0..7,17 | b1 Y-@1..8; b2 Y-@17..18 | 6.495201 / -0.373904 / False | - |
| Q06 / e1a3bc406819 | 28 / emitted_ready_not_evaluated | 0,19 | 12..18 | b1 Y-@12..19; b2 Y-@28 | 6.000000 / -0.078400 / True | - |
| Q07 / 31a7d6c6b7bc | 28 / emitted_ready_not_evaluated | 0,19 | 12..18 | b1 Y-@12..19 | 5.796736 / -0.301535 / False | - |
| Q08 / bd46ea537ba2 | 28 / emitted_ready_not_evaluated | 8,19 | 0..7,17..18 | b1 Y-@1..8; b2 Y-@17..19 | 6.796736 / -0.301535 / False | - |
| Q09 / 53ef572ff13a | 28 / emitted_ready_not_evaluated | 8,19 | 0..7,17..18 | b1 Y-@1..8; b2 Y-@17..19; b3 Y-@28 | 7.000000 / -0.078400 / True | - |
| Q10 / 696ba19b33c5 | 28 / emitted_ready_not_evaluated | 0,20 | 12..19 | b1 Y-@12..20 | 6.024424 / -0.227688 / False | - |
| Q11 / 1783b636ee7e | 28 / emitted_ready_not_evaluated | 0,20 | 12..19 | b1 Y-@12..20 | 6.024424 / -0.227688 / False | - |
| Q12 / efb43858fbf6 | 28 / emitted_ready_not_evaluated | 8,20 | 0..7 | b1 Y-@1..8,20 | 6.024424 / -0.227688 / False | - |
| Q13 / 7de1c9245ca1 | 28 / emitted_ready_not_evaluated | 8,20 | 0..7 | b1 Y-@1..8,20 | 6.024424 / -0.227688 / False | - |
| Q14 / bd947952b548 | 28 / emitted_ready_not_evaluated | 8,20 | 0..7,17..19 | b1 Y-@1..8; b2 Y-@17..20 | 7.024424 / -0.227688 / False | - |
| Q15 / b8a1117d0b03 | 28 / emitted_ready_not_evaluated | 8,20 | 0..7,17..19 | b1 Y-@1..8; b2 Y-@17..20 | 7.024424 / -0.227688 / False | - |
| Q16 / bbb8146c1c20 | 28 / emitted_ready_not_evaluated | 8,20 | 0..7,17..19 | b1 Y-@1..8; b2 Y-@17..20 | 7.024424 / -0.227688 / False | Z[28] LE -1100.300001011921 |
| Q17 / fae2bd805034 | 28 / emitted_ready_not_evaluated | 8,20 | 0..7,17..19 | b1 Y-@1..8; b2 Y-@17..20 | 7.024424 / -0.227688 / False | X[28] GE 735.3000010119209 |
| Q18 / e97d36e30491 | 28 / emitted_ready_not_evaluated | 0,21 | 12..20 | b1 Y-@12..21 | 6.176759 / -0.152335 / False | - |
| Q19 / e328d502f324 | 28 / emitted_ready_not_evaluated | 8,21 | 0..7,20 | b1 Y-@1..8,20..21 | 6.176759 / -0.152335 / False | - |
| Q20 / 016965ff21db | 28 / emitted_ready_not_evaluated | 8,21 | 0..7,17..20 | b1 Y-@1..8; b2 Y-@17..21 | 7.176759 / -0.152335 / False | - |
| F01 / bb27ae351b95 | 24 / symbolic_prefix_pending_expansion | 0,12 | - | b1 Y-@12,24 | 5.000000 / -0.078400 / True | - |
| F02 / df38be4b1b0d | 25 / symbolic_prefix_pending_expansion | 0,13 | 12 | b1 Y-@12..13,25 | 5.000000 / -0.078400 / True | - |
| F03 / bc9bac60153d | 22 / symbolic_prefix_pending_expansion | 0,13 | 12 | b1 Y-@12..13; b2 Y-@22 | 6.000000 / -0.078400 / True | - |
| F04 / b8d4dfae26ef | 26 / symbolic_prefix_pending_expansion | 0,14 | 12..13 | b1 Y-@12..14,26 | 5.000000 / -0.078400 / True | - |
| F05 / 9fe1d49c4344 | 23 / symbolic_prefix_pending_expansion | 0,14 | 12..13 | b1 Y-@12..14; b2 Y-@23 | 6.000000 / -0.078400 / True | - |
| F06 / 7da8b0057a1d | 27 / symbolic_prefix_pending_expansion | 0,15 | 12..14 | b1 Y-@12..15,27 | 5.000000 / -0.078400 / True | - |
| F07 / b4bc7f34a14e | 24 / symbolic_prefix_pending_expansion | 0,15 | 12..14 | b1 Y-@12..15; b2 Y-@24 | 6.000000 / -0.078400 / True | - |
| F08 / de8d2e2e9961 | 25 / symbolic_prefix_pending_expansion | 0,16 | 12..15 | b1 Y-@12..16; b2 Y-@25 | 6.000000 / -0.078400 / True | - |
| F09 / 4e952337f6c7 | 26 / symbolic_prefix_pending_expansion | 0,17 | 12..16 | b1 Y-@12..17; b2 Y-@26 | 6.000000 / -0.078400 / True | - |
| F10 / 538663a762ca | 27 / symbolic_prefix_pending_expansion | 0,18 | 12..17 | b1 Y-@12..18; b2 Y-@27 | 6.000000 / -0.078400 / True | - |
| F11 / 1a2003691430 | 27 / symbolic_prefix_pending_expansion | 8,18 | 0..7,17 | b1 Y-@1..8; b2 Y-@17..18; b3 Y-@27 | 7.000000 / -0.078400 / True | - |
| F12 / 90e481bc209e | 22 / symbolic_prefix_pending_expansion | 0,12 | 21 | b1 Y-@12; b2 Y-@21..22 | 6.000000 / -0.078400 / True | - |
| F13 / 8e8b00293985 | 22 / symbolic_prefix_pending_expansion | 0 | 12..21 | b1 Y-@12..22 | 5.000000 / -0.078400 / True | - |
| F14 / 27489d62a435 | 22 / symbolic_prefix_pending_expansion | 8 | 0..7,20..21 | b1 Y-@1..8,20..22 | 5.000000 / -0.078400 / True | - |
| F15 / 539904c4fa88 | 22 / symbolic_prefix_pending_expansion | 8 | 0..7,17..21 | b1 Y-@1..8; b2 Y-@17..22 | 6.000000 / -0.078400 / True | - |
| F-current / 05a83d34b0b7 | 21 / symbolic_expansion_in_progress | 8 | 0..7,17..20 | b1 Y-@1..8; b2 Y-@17..21 | 6.000000 / -0.078400 / True | - |

## Every saved movement exclusion before numerical evaluation

These57 saved records contain a physical program hash and proof, but do not retain the full rejected program/decision history. There are25 descending-first-floor exclusions,29 interval reach contradictions and3 norm reach contradictions. The25 fall proofs are not evidence against the grounded/rising P06/P07 histories. Full proof objects, including floor-top/Y separation or interval/norm bounds, are in the JSON ledger. The prior prose calling56 of these dead falls was incorrect; the underlying proof records and208-call count are unchanged.

| Rejected ID | Window stage / state | Reason | Saved distinguishing evidence |
| --- | --- | --- | --- |
| 43a5dc97cea02de263507942c9ef3e29fc0921ae389baf2782e87cdd5b26a391 | 0 / 9 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| 8c23b80b38b7467d57a1d35e937821ac8cce1682738d76183a014801cf1d5267 | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=-11.010832634135157 |
| bee6ece6ede44420dd41da28ab4ae77b4dcc1209d7cdb9f87d0d017544ec67b0 | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=-1.270930588052516 |
| de2583b6db216de3dea3f98ea44aa8fdf25f60981a2ad79451b764294f10b479 | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=2.2158943455387234 |
| f8c38b747eefcd117631da9cd6583e55058bd383d4a4a701777fbb0ea3c681db | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=-4.6670672445759145 |
| e9decdb11a22902a4ddff1ffa051f38bb2e6552837fca451a5c73221e844e07d | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=2.930504511237166 |
| 4be69477b6fb5bf4cbddd26834ed821693c8dcdcaee9308b55a5a170ae361781 | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=-3.5843541392125413 |
| c35a96673a68222d210d4428a13bd057e3242be1908b9319bfd1d2e5d2c1494b | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=3.5796985436761153 |
| 89e527c48845ae2e778896df9b4028601b80f0395c2e09fad302cdaba4dd7679 | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=-2.559544869528043 |
| 96f07b165259eec21ff70b6471a537665d3e895499bb6026dc8b810320683741 | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=4.162141421026987 |
| d5a0ffa3a69ea580e7eb3d20b65c25e93db98b9b5a3c3a45a0ae1f46be9c2908 | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=-1.5938211453046744 |
| b66ff106f2f8e59a0e173f51ffb70767327818187dbb2934c6e6c5a56a229d83 | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=4.676470876144269 |
| ecf3685f6d0c4fb5a7f256efefda4bf1d0ca4f28ffca6e0db098bcfb88767241 | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=-0.6883887928273907 |
| 069757520f5019f29ac3d03dda28e137272d758ec14b0353b62e8c69155813cf | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=0.15552175294310777 |
| fc28e8c2ac506322c24cad2d30d39ca94b37676317f61c9fe4938b5c1af62fe7 | 1 / 26 | norm_reach_contradiction | required=None; maximum future feetY=not saved |
| 99e5572f039b8f3d8d51f73c263e09cd5830cd8a8bbd0b2e0d5830e704ddd870 | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| 19d9316409859bdccd6be06e6ce3d9eb3f92c358ecd109e39820b5544eda8b7b | 1 / 28 | descending_without_any_first_floor_contact | required=['b3', 'b4', 'b5']; maximum future feetY=1.1555217529431077 |
| 18bafe1c53d7ceab2c942ed4127cf6efa09c2529255bb8ba0b6c40a8c2501134 | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=0.9366549461529918 |
| 14db5748e39c81e6016c353b349763ec665046b48ed34fc055ff96fb07e9205d | 1 / 28 | descending_without_any_first_floor_contact | required=['b3', 'b4', 'b5']; maximum future feetY=1.9366549461529918 |
| 6771ee721f63c9722b2fde94c9cbcb52974ee9345e58ca8c7755fda8be6338b2 | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=1.6537296175885952 |
| 4158c3b28c44166943fd5e81e8d672a2a34366e2ee3b3632d9a97d7258a603f4 | 1 / 28 | descending_without_any_first_floor_contact | required=['b3', 'b4', 'b5']; maximum future feetY=2.653729617588595 |
| 58a605cae12d1600877f8037ef7a8bc3eb9848d94e10afd97dfe4ec4203e4b9e | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| efc120b7210baee8acb7b57e3cf5eec34bcbb22b4f6b38185736545ca4eb243e | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=2.3054384517512125 |
| 34513d23d9fb5e3fffe5dcd7256c071935021b2b8dad3afa97a81739172d4b53 | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| ac6aeec0b554a5607ce93192954780f9c72a3a25288ae9fe7fcfe73ec72b1507 | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=2.3054384517512125 |
| c1bd832ed4767a6d8a5404ffe64da46b8d7c6efe02c1073ac3f20d71281c3a58 | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| 8219b0eeee0090c0597fed23d996597989bca0b96fdeb0f4cb7c123be5ca0f1b | 1 / 28 | descending_without_any_first_floor_contact | required=['b3', 'b4', 'b5']; maximum future feetY=3.3054384517512125 |
| fa4f18cf8bac8c459b7b6a4d16a4638689137e1ccdfc36ed3abb62251ae6404e | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| f8b98e7caf12ae19e2110beee9de18332ca03c17fa4ec1999e1e0aca125ab552 | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| 1ecc390623304580799aea0ba8481415c2f9f235a5ce1c215665d486cd7673ea | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| 0f7d4e1c6a13436c16355ddac5011fba986f923cedc4742ee5383001c95f0bf0 | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| a24a3cb042f6626ae841745ad52daad6fcca4d7bb8fef33e3ef8c91f1d932cf8 | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| 80d2847059a8ea4440a5149f61406c2ca2cabcc0e17981886f043cde468cd93e | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| 504a7a463b0bd5a8abbd3aeb847563a1d740d46edc52cb7e7564cfbddbd591db | 1 / 28 | norm_reach_contradiction | required=None; maximum future feetY=not saved |
| 504a7a463b0bd5a8abbd3aeb847563a1d740d46edc52cb7e7564cfbddbd591db | 1 / 28 | norm_reach_contradiction | required=None; maximum future feetY=not saved |
| dbb9cf5b6b2ecd5988c1634de79a97b7af2dd857232e285dd8a5351e32e4608b | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| dbb9cf5b6b2ecd5988c1634de79a97b7af2dd857232e285dd8a5351e32e4608b | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| cd020186ed98804899d49b14ed9306560dcebe16690951210845f871afe11b5b | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| ea3996ec189d334742c5600e71125be8d2828aa2b0baf6cbb40edf1f726de913 | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| 408c2df8d0336cac38de8547eced50a8925ddb2ea56bc1c180a947db7064b69e | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| afbbf70c2976147421317b0c54b6d26a7579eea0d64a40207ab0beabe3307ba2 | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| dd86151925d18526470cb8e6fa133336a45d002d434b38b72634766d906af809 | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| 89d3be3e00eca904074c8fa2c851394c35d4cc04dcbd3fc08a5ae1f11b62b789 | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| 6a2a9f16d5f30883a89d9a1b0ac5529491aae5420e50a6e4d2191df35d389e98 | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| ffdd481c4dc7de757450ea6cb9f697a0d070bf1f9cd0962ea807c4a955cb319d | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| 4f28c3dbd6045d4cc4a0fffa690e2d4ff2ae21c0c92af9f427520f5a03b5cda1 | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| 4f28c3dbd6045d4cc4a0fffa690e2d4ff2ae21c0c92af9f427520f5a03b5cda1 | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| dca31993c7730028baa9f5e37919b28fb07e67160c8790025f1e67dafcaa608f | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| dca31993c7730028baa9f5e37919b28fb07e67160c8790025f1e67dafcaa608f | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| 279c143ca429e23ffb433c96ebc8e9d5df35876383d255284980285f67217925 | 1 / 28 | descending_without_any_first_floor_contact | required=['b3', 'b4', 'b5']; maximum future feetY=3.8904474532599647 |
| 8eaec4d92b7e95dd8b945a2deef0a4471098b291ed35821fabd4c54bd81355df | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| bd0a12003b7519e233a34336ef8f0af2f37cb7ed71b0b4ad10f82f86fa7a36fa | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| 8eaec4d92b7e95dd8b945a2deef0a4471098b291ed35821fabd4c54bd81355df | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| bd0a12003b7519e233a34336ef8f0af2f37cb7ed71b0b4ad10f82f86fa7a36fa | 1 / 28 | interval_reach_contradiction | required=None; maximum future feetY=not saved |
| 62a5d822ee39d9ee148eb7dac37773a6e634d40eeb38884ae5add255df9dcc28 | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=2.8904474532599647 |
| 574f26ea576cf1c5e01ed625b3cbd9aee7316570bd7eac8c690cfc333a82e941 | 1 / 28 | descending_without_any_first_floor_contact | required=['b2', 'b3', 'b4', 'b5']; maximum future feetY=2.8904474532599647 |
| 54ec09adc43fb1095820dea53b5b94727f29edef81560e2dbc0acb41fcb6d122 | 1 / 28 | descending_without_any_first_floor_contact | required=['b3', 'b4', 'b5']; maximum future feetY=3.8904474532599647 |

## Constraint generation, pruning and where information is missing

Each row below is a resumed outer evaluation of one unchanged movement program. Counts do not mean fresh jump schedules. Calls=0 means the allowance stopped during preparation/generation or screening. The saved records do not contain a per-chunk best-objective history, so objective improvements across these chunks cannot be reconstructed exactly.

| Evaluation chunk | Session | Numerical proposals | IPOPT calls | Termination | Unresolved-retry flag |
| --- | --- | --- | --- | --- | --- |
| P01-E01 | 1 | 1 | 1 | contact_search_exhausted | True |
| P02-E01 | 1 | 1 | 1 | contact_search_exhausted | True |
| P03-E01 | 1 | 2 | 2 | contact_search_exhausted | True |
| P04-E01 | 1 | 2 | 2 | contact_search_exhausted | True |
| P05-E01 | 1 | 2 | 2 | contact_search_exhausted | True |
| P06-E01 | 1 | 4 | 4 | geometry_deadline | True |
| P06-E02 | 1 | 6 | 5 | deadline | False |
| P06-E03 | 1 | 6 | 6 | deadline | False |
| P06-E04 | 1 | 7 | 6 | numerical_call_limit | False |
| P06-E05 | 1 | 7 | 6 | numerical_call_limit | False |
| P06-E06 | 1 | 7 | 5 | deadline | False |
| P06-E07 | 1 | 3 | 3 | constraint_generation_deadline | False |
| P06-E08 | 1 | 6 | 5 | deadline | False |
| P06-E09 | 1 | 6 | 5 | deadline | False |
| P06-E10 | 1 | 5 | 5 | deadline | False |
| P06-E11 | 1 | 6 | 6 | deadline | False |
| P06-E12 | 1 | 6 | 6 | deadline | False |
| P06-E13 | 1 | 6 | 6 | deadline | False |
| P06-E14 | 1 | 6 | 6 | deadline | False |
| P06-E15 | 1 | 4 | 3 | deadline | False |
| P06-E16 | 1 | 6 | 4 | deadline | False |
| P06-E17 | 1 | 6 | 3 | deadline | False |
| P06-E18 | 1 | 6 | 3 | deadline | False |
| P06-E19 | 1 | 5 | 4 | deadline | False |
| P06-E20 | 1 | 4 | 4 | geometry_deadline | False |
| P06-E21 | 1 | 5 | 4 | deadline | False |
| P06-E22 | 1 | 5 | 3 | deadline | False |
| P06-E23 | 1 | 5 | 3 | deadline | False |
| P06-E24 | 1 | 5 | 3 | deadline | False |
| P06-E25 | 1 | 5 | 2 | deadline | False |
| P06-E26 | 1 | 4 | 2 | deadline | False |
| P06-E27 | 2 | 4 | 4 | deadline | False |
| P06-E28 | 2 | 5 | 3 | deadline | False |
| P06-E29 | 2 | 1 | 1 | contact_search_exhausted | False |
| P07-E01 | 2 | 5 | 4 | deadline | False |
| P07-E02 | 2 | 3 | 2 | deadline | False |
| P07-E03 | 2 | 0 | 0 | constraint_generation_deadline | False |
| P07-E04 | 2 | 0 | 0 | constraint_generation_deadline | False |
| P07-E05 | 2 | 3 | 2 | deadline | False |
| P07-E06 | 2 | 2 | 1 | constraint_generation_deadline | False |
| P07-E07 | 2 | 3 | 2 | deadline | False |
| P07-E08 | 2 | 2 | 1 | constraint_generation_deadline | False |
| P07-E09 | 2 | 0 | 0 | constraint_generation_deadline | False |
| P07-E10 | 2 | 0 | 0 | constraint_generation_deadline | False |
| P07-E11 | 2 | 0 | 0 | constraint_generation_deadline | False |
| P07-E12 | 2 | 3 | 1 | deadline | False |
| P07-E13 | 2 | 4 | 2 | deadline | False |
| P07-E14 | 2 | 2 | 1 | constraint_generation_deadline | False |
| P07-E15 | 2 | 0 | 0 | constraint_generation_deadline | False |
| P07-E16 | 2 | 1 | 0 | deadline | False |
| P07-E17 | 2 | 3 | 2 | deadline | False |
| P07-E18 | 2 | 1 | 1 | deadline | False |
| P07-E19 | 2 | 4 | 2 | deadline | False |
| P07-E20 | 2 | 0 | 0 | constraint_generation_deadline | False |
| P07-E21 | 2 | 0 | 0 | constraint_generation_deadline | False |
| P07-E22 | 2 | 0 | 0 | constraint_generation_deadline | False |
| P07-E23 | 2 | 1 | 0 | deadline | False |
| P07-E24 | 2 | 1 | 1 | deadline | False |
| P07-E25 | 3 | 3 | 2 | deadline | False |
| P07-E26 | 3 | 2 | 1 | constraint_generation_deadline | False |
| P07-E27 | 3 | 0 | 0 | constraint_generation_deadline | False |
| P07-E28 | 3 | 0 | 0 | constraint_generation_deadline | False |
| P07-E29 | 3 | 2 | 2 | deadline | False |
| P07-E30 | 3 | 3 | 2 | deadline | False |
| P07-E31 | 3 | 1 | 1 | deadline | False |
| P07-E32 | 3 | 3 | 3 | deadline | False |
| P07-E33 | 3 | 0 | 0 | constraint_generation_deadline | False |
| P07-E34 | 3 | 0 | 0 | constraint_generation_deadline | False |
| P07-E35 | 3 | 0 | 0 | constraint_generation_deadline | False |
| P07-E36 | 3 | 1 | 0 | deadline | False |
| P07-E37 | 3 | 3 | 3 | deadline | False |
| P07-E38 | 3 | 3 | 1 | deadline | False |
| P07-E39 | 3 | 0 | 0 | constraint_generation_deadline | False |
| P07-E40 | 3 | 0 | 0 | constraint_generation_deadline | False |
| P07-E41 | 3 | 2 | 2 | deadline | False |
| P07-E42 | 3 | 3 | 2 | deadline | False |
| P07-E43 | 3 | 1 | 1 | deadline | False |
| P07-E44 | 3 | 3 | 2 | deadline | False |
| P07-E45 | 3 | 0 | 0 | constraint_generation_deadline | False |
| P07-E46 | 3 | 0 | 0 | constraint_generation_deadline | False |
| P07-E47 | 3 | 0 | 0 | constraint_generation_deadline | False |
| P07-E48 | 3 | 0 | 0 | constraint_generation_deadline | False |
| P07-E49 | 3 | 0 | 0 | constraint_generation_deadline | False |
| P07-E50 | 3 | 0 | 0 | constraint_generation_deadline | False |

The retry flag permits unresolved work only on a fresh initial evaluation; later chunks use false. A true flag on the first evaluation does not establish that any prior unknown existed or was retried. Completed branch identities do not repeat across resume boundaries in these reports.

| Program | Source variants emitted | Source exhausted | Saved LP exclusions | Duplicate variant hits | Outer evaluation chunks |
| --- | --- | --- | --- | --- | --- |
| P01 | 1 | True | 0 | not instrumented | 1 |
| P02 | 1 | True | 0 | not instrumented | 1 |
| P03 | 1 | True | 0 | not instrumented | 1 |
| P04 | 1 | True | 0 | not instrumented | 1 |
| P05 | 1 | True | 0 | not instrumented | 1 |
| P06 | 130 | True | 1807 | 24803 | 29 |
| P07 | 44 | False | 0 | 1270929 | 50 |

P06 emits130 constraint-source variants and also triggers corridor repairs; the combined151 handled branches are not151 different jump schedules. Its recorded1,807 LP exclusions and24,803 duplicate hits consume no new IPOPT call. Duplicate-hit accounting began during the second session, so it is not a complete first-session generation count. P07 has44 emitted variants and an unfinished source. The large repeated generation cost needs profiling; exact duplicate-hit counts do not reveal which parent cursor generated each repeat.

No candidate was heuristically discarded after state10: both0/8 setups were retained, and the state28 comparison never finished. The current state28 generator records258 raw choices,25 emitted programs,56 screened prefixes and96 policy rejections, with no queue drops. The rejected96 decision histories, exact constraints for screened1,807 source variants (only row hashes/proofs were saved), and the detailed cursor work for duplicate hits cannot be listed as fully specified candidates from these archives. Treating those counters as solver calls or declaring their unseen decisions infeasible would be incorrect.

## What this says about potential pruning

The current protection function returns incomparable_schedules for P06/P07 against P03. Neither candidate has fewer run inputs or more jumps than P03: P03 has3 jumps/0 runs, P06 has2/4 and P07 has2/5. Their observed B3 arrival is16/17 inputs later, and all three recorded state28 progress/velocity/speed values are worse. The implementation nevertheless preserves every schedule mismatch unconditionally, which is more conservative than just applying fewer-run/more-jump protections. Those absolute-clock state differences are not apples-to-apples evidence of dominance across phases, and observed probe arrival times are feasible upper bounds, not proven minimum times.

There is a separate scheduling barrier: state28 retention was never invoked. The engine drains each program's pending constraint source and the window's available candidates before applying comparisons. Thus changing the end-of-window pruning rule alone would not avoid the work already spent on P06/P07 here. An incremental comparison could provisionally park an expensive history while keeping its full saved frontier, then audit its continuations later. That is a scheduling/heuristic proposal, not a sound dead-prefix proof or an implemented change.

1. **Prioritize logical row/program coverage.** P04/P05 are P03 plus an extra approach inequality with unchanged input/scalar/contact histories. They contribute4 main calls and8 spatial calls beyond P03. Their current optimized progress is effectively equal. A generic feasible-set inclusion/equivalence check could avoid redundant prefix scoring or defer stricter variants, provided later decision generation from the broader prefix covers their continuations. A single position-optimal witness does not represent all future velocity tradeoffs, so this is a model-coverage argument to verify, not a new safe final-route dominance claim.

2. **Review the route checker before spending more on physical infeasibility bounds.** All172 main worker calls are feasible; 62 of62 independently physically replayed and passing. Three reject B3 side position while above its top. Requiring a particular horizontal crossing side on a future block can be stricter than the intended3D cell route. Compare the saved traces against the desired route/contact semantics, then either encode the justified requirement before solving or correct the check. Do not relabel physical validity as completion of the chosen route.

3. **P06/P07 are the expensive unresolved timing families.** Together they account for164 of172 main calls and12 spatial calls. They have not completed B2 in the main prefix. Existing first-floor viability does not prove them dead, and different jump/run schedules cannot be pruned by their state28 position alone. A remaining-time/continuation proof must cover all enabled stepping, intermediate support and run-off choices; a normal-jumps-only calculation would be conditional. Their cost and weaker local scores are evidence for investigation, not enough for a sound discard.

4. **Inspect redundant enumeration and availability.** P06's source is now exhausted, but substantial time was spent finding duplicate or LP-excluded variants. Useful Q09/F11 support-bearing alternatives remained queued while this happened. Consider canonical generation and a measured scheduling change separately from correctness pruning. Neither a higher cap nor retrying these already-feasible IPOPT problems addresses this evidence.

5. **Close logging gaps before judging heuristic quality.** Save every probe's full fixed problem/result, a stable branch ID in each validation record, per-branch objective and accepted witness metadata, and counters/timings for initialization, generation, LP, worker startup and checkpoint writes. Preserve records on geometry-deadline paths. Without this, some individual calls and objective improvements cannot be reconstructed exactly. No logging/runtime policy changes are made in this audit.

## Reproduce and inspect

Run the saved-data audit, with optional existing Java scene replay and no IPOPT calls:

```sh
OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1 PYTHONPATH="$PWD" .venv-guidance/bin/python standalone/autonomy/experiments/orchestrator-fixes/audit_candidates.py --compiler standalone/autonomy/build/compiler.json
```

Sources: evidence/orchestrator-real11-stable{,-resume,-resume2}.json.gz. Full float constraints, branch keys, candidate traces, available solver diagnostics, scope, session mapping, ambiguous validation alignments, probe winners and independent physical replays are in [candidate-ledger.json.gz](candidate-ledger.json.gz). Source-file SHA-256 hashes and reconciled counts are embedded there. The audit uses existing constraint builders and copies the captured initial-support bounds; it does not change any model, run IPOPT or prove global optimality.

Follow-up: [duplicate-generation evidence and justified reductions](PRUNING_FOLLOWUP.md).

User questions, answered sequentially: [candidate ancestry, ordering, timing and work allocation](CANDIDATE_QUESTIONS.md).
