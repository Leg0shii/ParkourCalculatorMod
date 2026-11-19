# Additional run-8 candidates

2026-11-15. Investigation only; finishing the selected DFS branch is intentional.
Compare `runs/enumeration-reduction-cold300` with `runs/anchor-run-budget-cold300`.

Both runs check the same run-8 partial journeys: B2 at17; B3 at27 (unknown),
then28; B4 at40; B5 at57 (unknown), then58; B7 at67. The successful complete
schedule jumps at inputs **8,19,30,43,58**. There are no additional run-8 jump
schedules or visited landing blocks in the new run. Two carried constraint
histories share this schedule; the second adds X>=731.3000010119209 at state56.

The old run submits 11 numerical requests for this final jump. The new submits
42, including all 11 old fingerprints and exactly 31 additional requests:

| Additional B6/B7 choice | Requests | Result |
| --- | ---: | --- |
| B6 Z- through60, X- from61 | 4 | No solution found |
| B6 Z- through63,64 or65, then X- | 24 | Feasible; all worse than the original switch61 solution |
| Switch62 with another B7 Z-→X+ switch59 alternative | 1 | Feasible; Z=-1094.161663 |
| B6 X+→Z-→X-, switches59 and65 | 2 | No solution found |

The 24 later-switch cases are three switch states × four B7 alternatives ×
two histories. The B7 alternatives are X+ alone, X+→Z- switch0, X+→Z- switch59,
and Z-→X+ switch59. Phases may have no active constraints after vertical masking.
Twenty-five extra requests are feasible and six are unknown; none improves the
best 67-tick endpoint. Worker-reported infeasibility is not an ancestor proof.

Concrete requests: #7 (also old) switches B6 after61 and gets
Z=-1093.823129063534; #13 switches after60 and finds no solution; #15 switches
after63 and gets -1094.4640826396794; #19 switches after64 and gets
-1094.7175450013515; #23 switches after65 and gets -1094.9329016452627.
B6 Z- means Z<=-1095.300001011921; X- means X<=729.6999989880791.
Request #27 adds B6 X>=731.3000010119209 at59, Z- through65 and X- from66;
it finds no solution. See [all 42 requests](run8-requests.csv) for B7 choices,
both histories, old request numbers, hashes and achieved positions.

| Generation within these two terminal contexts | Old | New |
| --- | ---: | ---: |
| Returned B6 passage options | 4 | 91 |
| Returned B7 passage options | 135 | 3,978 |
| Combined variants emitted | 56 | 574 |
| Internal face/tick entries visited | 92,918 | 3,283,413 |

These counts are not unique full movement routes. New B6 options include 76
three-face options, versus none before. In `ConstraintVariants.__next__`, a
surviving B6 prefix creates a new child at the B7 item with a fresh cursor.
B7's prepared masks/tables are shared, but its face/tick catalogue is walked
again for each child. Of the new internal entries, 2,410,137 belong to four-face
B7 chains such as X+→Z-→X-→Y-. Many phases are empty or masked; descriptions
are later masked, deduplicated or screened before numerical submission.

Thus the added investigation is deeper obstacle-face/switch enumeration within
the same run-8 journey, including repeated downstream B7 catalogue traversal.
It is consistent with the intended DFS order. The [compact comparison](run8-comparison.json)
records exact context counts. This audit changes no production code.
