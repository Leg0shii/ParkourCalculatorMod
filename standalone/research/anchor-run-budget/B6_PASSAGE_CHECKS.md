# B6 passage choices and pre-solver rejection

This audit records commit641c68b5. The later [early-screen implementation](../early-passage-screen/README.md)
moves the stronger checks ahead of downstream enumeration; baseline findings
below describe the source before that change.

2026-11-15. Investigation only; no production changes. Scope: the first run-8
constraint history, with jumps8/19/30/43/58 and landing B7 at67. The targeted
replay completes B6 alone, without repeatedly enumerating B7 or launching an
angle-solver worker. It rebuilds the original base model with `InteractionGate`
and verifies every stored B6 horizontal reachability mask matches.

## Why more than two choices

B6 is the tall box `[730,0,-1095,731,20,-1094]`. The winning side requires
X<=729.6999989880791; the opposite side requires X>=731.3000010119209.
Its entry-side condition is Z<=-1095.300001011921; the far side is
Z>=-1093.699998988079. The physical picture is two ways around this wall.

The generic generator instead starts with permutations of six outside faces,
allowing three transitions subject to horizontal/vertical caps. Reachability
and suffix masks retain 88 face-sequence templates. Completing this single
B6 catalogue produces 277 descriptions, covering only137 distinct sets of
position rows. This is a separate complete B6-only replay, not the91 returned
B6 options observed across both histories in the truncated300-second run.

Inactive phases add no rows but still count toward the passage's nominal
horizontal/vertical transition counts. Deduplication includes those counts.
For example, all five descriptions below generate the exact same rows as the
simple winning-side crossing after61:

- Z-→X-, switch61.
- X-→Z-→X-, switches0,61.
- Y-→Z-→X-, switches0,61.
- X-→Y-→Z-→X-, switches0,1,61.
- Y-→X+→Z-→X-, switches0,0,61.

The early extra phases occur outside B6's relevant collision window, not below
or around the wall during the final jump. They survive as different resource
descriptions despite identical physical rows.

## What is rejected before the angle solver

| Choice | Current pre-solver behavior for this schedule |
| --- | --- |
| Winning-side switch58 or earlier | Later travel/reachability checks reject it. |
| Winning-side switch59 | The relaxed linear control check proves infeasible. |
| Winning-side switch60 | Passes prechecks; the recorded angle-solver attempts found no solution. |
| Winning-side switch61 | Passes; recorded feasible and best of the submitted67-tick requests. |
| Winning-side switches62–65 | Pass; recorded feasible, worse Z. |
| Winning-side switch66 | Rejected in the face masks: remaining behind B6 at state66 is unreachable. |
| Winning-side switch67 | No subsequent side interval exists before the horizon ends. |
| Full opposite-side route Z-→X+→Z+ | Five mask-admissible timing pairs; all rejected by the relaxed linear control check. |
| Opposite-side variants including inactive prefixes | All13 such emitted descriptions are rejected by that check. |
| Active over/under passage | Vertical masks reject it: the trajectory is neither above20 nor below-1.8 during B6's relevant states. |
| Extra right→behind→right or opposite→behind→right requirements | Some are redundant aliases; others fail distance/interval/linear checks. A few loose relaxations survive and require numerical attempts. |

The five full opposite-side timing pairs are (61,62), (60,62), (59,62),
(0,62), (58,62). Their X+ interval must end by62, since X+ is unreachable from63
onward; Z+ cannot begin before62. The combined corner/landing constraints fail
even the relaxed control model. There is no generic rule excluding that side
because its objective is worse. This rejection is specific to the fixed schedule.

The linear check is a simpler optimization over an optimistic acceleration
polygon, distinct from an angle-solver call. Infeasibility there rejects the
candidate; success merely means it has not been ruled out.

## The rejection happens late

All277 emitted B6 descriptions pass the current cheap B6-prefix row check.
Applying the existing full pre-solver checks to B6 alone rejects237, leaving40
descriptions (not40 certified feasible paths). The generator normally performs
the stronger linear check only after combining B6 with later items such as B7;
the final fixed-model check runs before numerical dispatch.

Therefore there are two separate sources of excess work: equivalent physical
paths retained under different nominal transition counts, and B6-impossible
descriptions that cause downstream enumeration before the stronger check.
The [277-description table](b6-passage-checks.csv) records each face sequence,
switch tuple and early/linear/full pre-solver outcome. These targeted outcomes
must not be presented as277 candidates actually submitted in the cold run.

Raw reproduction inputs/script/results remain under ignored
`runs/b6-passage-audit/`; the source snapshot is commit641c68b5. No whole-course
infeasibility or global left/right dominance claim follows from this audit.
