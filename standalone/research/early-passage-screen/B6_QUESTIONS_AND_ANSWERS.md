# B6 questions and answers

Ongoing Q&A requested by the user. Append subsequent questions and their checked
answers here. Current focus: **History B** in the [complete option ledger](B6_OPTIONS.md).
Production source for the ledger: `732624f5`.

## 1. Why generate X- → Z- → X-, and why does it have the same constraints as B001?

**Question:** “lets focus on B. why do we generate X- -> Z- -> X-. how does this result in same constraints as B001”

**Answer:** The matching three-face description is **B007**. B001 is
`Z- → X-` with switch61; B007 is `X- → Z- → X-` with switches0,61.

### Why the generator creates it

The generic passage language allows a sequence to leave a face and return to
it later. `face_chains` explicitly generates a prefix followed by its first
face again. The sequence X-→Z-→X- has two horizontal transitions, within the
current cap, so it is an allowed template.

The generator assigns nominal phases over the entire0–67 horizon, then only
emits constraints where B6's collision checks require them. In this context,
B6 needs X constraints at states59–67 and Z constraints at58–67. Earlier face
labels do not impose position requirements.

### Why B007's extra phase adds nothing

These intervals are taken from the production `intervals` function:

| Option | Nominal phase | Nominal states | Actual emitted constraints |
| --- | --- | --- | --- |
| B001 | Z- | 0–61 | Z- at58–61 |
| B001 | X- | 62–67 | X- at62–67 |
| B007 | First X- | 0 only | None: state0 is outside B6's required X states |
| B007 | Z- | 0–61 | Z- at58–61 |
| B007 | Final X- | 62–67 | X- at62–67 |

The X-→Z- transition can share state0 under the collision-axis ordering. The
later Z-→X- transition goes from state61 to62. These are interval conventions,
not evidence that the player physically passes B6 at state0.

Both options therefore emit exactly these ten position rows:

- `Z <= -1095.300001011921` at states58,59,60,61.
- `X <= 729.6999989880791` at states62,63,64,65,66,67.

I compared the stored row lists directly: **they are exactly equal**, including
values, tick indices and row metadata. This is not an inference from similar
paths. B007 is an extra description of the same position requirements as B001.

### Why duplicate suppression keeps both

The option key includes the constraints **and nominal blockage counts**:

| Option | Horizontal transitions | Vertical transitions | Total |
| --- | ---: | ---: | ---: |
| B001 | 1 | 0 | 1 |
| B007 | 2 | 0 | 2 |

The ineffective first phase still contributes a transition to B007's count.
Consequently the keys differ even though the position rows are identical.
The current generator does not normalize that inactive phase away before
counting transitions and identifying options.

### Checked evidence

- [History B option records](b6-options-evidence/history-B-options.json.gz): B001 and B007.
- [Captured History B input](b6-options-evidence/history-B-input.json.gz): required X/Z states and prepared faces.
- [Passage language and interval rules](../../src/parkour_solver/constraints/avoidance.py): `face_chains`, `intervals`, `blockage_counts`.
- [Option generation and key](../../src/parkour_solver/constraints/variants.py): `option`.

SHA256 of either row list, JSON encoded with sorted keys and compact separators:
`78880f3dd1b529864edc197e7f78e852ab48c3019fa13a2033fc024cb137c3a7`.

## 2. Why are we generating a switch at 0?

**Question:** “why are we generating a switch at 0”

**Answer:** Each obstacle's face sequence starts at the beginning of the whole
journey, state0. It does not start at the obstacle's first relevant collision
state. The option search initializes a chain with `depth=0, start=0` and tries
switch boundaries from `start` through the horizon, so0 is included.

B6's relevant X states in History B are59–67; its relevant Z states are58–67.
Before those states the generator marks faces as allowed and gives them zero
cost because they impose no collision-avoidance requirement. The captured input
confirms that X- at state0 has `mask=true`, `guaranteed=true`, `cost=0.0`.
Those flags mean the phase is irrelevant there, not that the player physically
satisfies X<=729.7 at state0.

For B007 this allows an X- phase ending immediately at0, followed by Z- through61
and X- from62. The first phase contributes no position rows. Several placements
of that first switch before B6 becomes relevant give the same constraints; the
search can retain0 as their first representative. Zero is consequently a
bookkeeping artifact, not a useful B6 manoeuvre or a measured turn.

The redundancy survives because the empty first phase still contributes to
nominal transition counts. B007 is kept as a two-transition description while
B001 is kept as a one-transition description. Moving the placeholder switch
away from0 alone would not remove the underlying redundant phase.

**Checked sources:** `ConstraintVariants.option` initializes the frontier and
loops over switch boundaries; `_prepare_item` assigns masks/costs to inactive
states. The recorded required-state lists and tick0 flags are in the
[History B input](b6-options-evidence/history-B-input.json.gz).

## 3. How does the current obstacle constraints generator work?

**Question:** “lets rethink our obstacle constraints generator. can read the relevant code and explain how it works”

**Answer:** The main generator is `ConstraintVariants` in
[constraints/variants.py](../../src/parkour_solver/constraints/variants.py).
It represents obstacle avoidance as a **sequence of outside faces covering
stretches of ticks**. Physical route classes such as “left around B6” and “right
around B6” are not its starting representation.

### A. What it receives

`search.engine.evaluate_program` supplies one already-selected movement program:
its jump/key/contact history, vertical profile, horizon, original starting state,
existing position constraints and physical geometry. The horizontal facing
angles are still free. Existing model analysis supplies conservative reachable
position/velocity ranges. An initial facing guess supplies a reference X/Z path
used to rank options; that reference need not be a valid obstacle-avoiding path.

The generator changes obstacle constraints, not the chosen jump timings or keys.
Different movement programs are handled by the outer route search.

### B. Turn each collision box into six outside conditions

The box is expanded to account for player width/height, allowing tests against
the player's X/Z center and foot Y. The six labels are X-, X+, Y-, Y+, Z-, Z+.
For B6, examples are X<=729.7 on the winning side, X>=731.3 on the opposite
side and Z<=-1095.3 behind it, with the actual float/margin values preserved in
the ledger. Avoidance asks for some suitable outside condition, not all six.

### C. Determine when this obstacle can matter

For each tick, `_prepare_item` queries possible unwanted contact during the Y,
X and Z collision phases. These phases matter because Minecraft processes the
axes in order and uses different pre/post-movement coordinate combinations.
If contact is ruled out in every phase, the obstacle needs no avoidance option.
Required landing/contact constraints are separate and are not removed by that.

Possible contacts become per-axis required-state lists. In the code:

| Possible phase at t | Required X states | Required Z states |
| --- | --- | --- |
| Y | t | t |
| X | t,t+1 | t |
| Z | t+1 | t,t+1 |

History B's B6 lists are X=59–67 and Z=58–67. States outside the relevant sets
do not receive that face's position constraints.

### D. Precompute masks and costs for each face/state

For each face at each state it records:

- **Possible:** can this outside condition fit the reachable bounds? For Y,
  check the already-selected vertical path directly.
- **Guaranteed:** do the ranges already imply this condition?
- **Cost:** squared distance by which the reference path violates the face.

Inactive states are marked possible/guaranteed with cost0. This allows unused
early phases such as B007's X- at0. The flags there express irrelevance, not
the player's physical side of the obstacle.

### E. Enumerate face-sequence templates

`avoidance.face_chains` generates permutations of faces and explicit sequences
that return to their first face. Adjacent faces on the same axis are excluded.
Counts must fit the caps: in this case at most2 horizontal transitions,
2 vertical transitions and3 transitions total, allowing up to4 face phases.

Examples include Z-→X-, X-→Z-→X-, Z-→X+→Z+, and sequences with Y phases.
Endpoint masks and a backwards dynamic-program table remove templates with no
mask-valid timing completion. The table also estimates the minimum reference
cost of completing each remaining suffix. Templates are ordered by that cost,
then length and stable face order. History B retains33 templates.

This is heuristic ordering, not a proof that the first template gives the best
terminal Z or the easiest trajectory.

### F. Search switch times within one template

`option` uses a locally cost-ordered DFS over `(phase, start state, switches)`.
Each template starts at0 and considers switch boundaries through the full
horizon. A switch may use the same state for both phases or advance one state,
according to collision-axis order; Z-→X- after61 starts X- at62.

It uses masks to stop impossible intervals, suppresses already-visited states
with equivalent effective rows, and drops some unnecessary tails when a face
already guarantees coverage for the remainder. Child order uses reference cost
plus the precomputed suffix estimate.

Important limitation: the full stronger propagation/travel/linear screen does
**not** run at every internal phase/switch node. It runs after a complete
per-obstacle option is produced. Also, individual children with a missing
suffix-table completion can still be queued with infinite ranking cost, then
discarded later; the table is not used to exclude every such child upfront.

### G. Compile a complete template/timing choice into position rows

`compile_passages` converts horizontal face intervals into X/Z inequalities.
The generator keeps rows only at the required states for that axis. Y phases
are handled by the fixed vertical profile/masks rather than new facing-solver
variables. For B001, the retained rows are Z- at58–61 and X- at62–67.

Local duplicate detection normalizes effective position bounds but also includes
nominal horizontal/vertical/total transition counts. Consequently B007's extra
inactive phase survives as a different resource description despite identical
rows. Completing History B yields153 descriptions; the B6-prefix screen rejects
117 and retains36. These are complete-replay counts, not153 actual cold-run
solver requests or36 certified paths.

### H. Combine obstacles with a second DFS

`_next_variant` maintains a stack of accumulated rows and selected passages.
Items include optional route guards, required-contact alternatives, scoped
collision boxes and unwanted-floor-contact checks. Obstacle order follows the
scope's stored solid list, not necessarily chronological encounter order.

It takes a B6 option, adds its rows to previous choices, and runs the stronger
screen on the accumulated model. Rejected choices do not expand B7. Surviving
ones receive a child at the B7 item. Adding B7 checks B6+B7 together, retaining
their physical coupling. Prefix/complete duplicate keys also retain resource
counts. Passing a screen means only “not ruled out.”

Each new B6 child gets a new B7 timing cursor. Prepared B7 masks/tables are
shared, but the traversal of B7's local options is repeated. The generator is
lazy and yields combined assignments one at a time; it does not build the
entire Cartesian product before yielding the first assignment.

### I. Repair queue, angle solver and physical validation

`solve_contact_branches` consumes combined assignments. It can merge equal
flattened constraint sets even when different face histories produced them,
recording the alternative descriptions. This occurs after their downstream
generation, so it does not recover that generation cost. An emitted assignment
is therefore not automatically a separate angle-solver call.

Surviving fixed problems reach `find_route` and the coupled angle solver,
starting at the original state with previous facing angles free. Candidate
trajectories undergo contact, vertical, goal and scene validation.

There is also a separate collision-driven fallback: if a candidate collides,
`avoidance.passage_options` proposes further face/tick passages around the
reported solid, using that trajectory as its reference. It shares face/interval
helpers but uses its own bounded best-first proposal pool. B001/B007 in the
ledger come from the upfront `ConstraintVariants` catalogue, not that fallback.

### J. State and implications for a rethink

The iterator saves preparation progress, pending obstacle combinations, local
phase/timing frontiers and duplicate identities. This supports interruption and
JSON continuation. `prefixScreened` records that the accumulated necessary
checks completed, not that a trajectory has been solved.

The design issues established by code/data are:

1. Templates cover0–horizon even when the obstacle matters much later.
2. Inactive phases remain in the nominal passage/resource description.
3. Equal physical rows can remain distinct because nominal counts differ.
4. Strong prefix screening follows a complete per-obstacle timing search,
   rather than every internal phase assignment.
5. Every surviving earlier-obstacle prefix starts a fresh downstream timing
   traversal, even when that downstream local catalogue is unchanged.
6. Reference-path closeness orders enumeration; it is not the final objective.

These facts identify where the representation and search boundaries need
consideration. This answer changes no generator behavior.

**Additional checked sources:**
[engine setup](../../src/parkour_solver/search/engine.py),
[passage helpers](../../src/parkour_solver/constraints/avoidance.py),
[interaction relevance](../../src/parkour_solver/reachability/service.py),
[repair and solver handoff](../../src/parkour_solver/search/repair.py),
[player-space box expansion](../../src/parkour_solver/scene/geometry.py).

## 4. Why include Y patterns when B6 only needs X/Z restrictions?

**Question:** “if we only have x/z restrictions for b6 why do we have patterns which include y”

**Answer:** The generic generator includes all six faces because other obstacles
can be cleared above or below. Y is already determined by the selected movement
program, so a meaningful Y phase is checked against that known vertical path;
it does not create a Y variable for the facing solver. Knowing Y can legitimately
remove horizontal avoidance requirements when the player clears a short box.

For this B6, however, neither vertical side is possible at its relevant states:

| Face | Requirement on player-foot Y | Possible at any relevant state58–67? |
| --- | --- | --- |
| Above / Y+ | Y>=20 | No |
| Below / Y- | Y<=-1.7999999523162842 | No |

The saved masks confirm both Y faces are false at every relevant state, but true
at state0 because B6 is irrelevant there. That permits an inactive Y phase
before the collision window. It does not represent actually moving under or
over B6.

For example, B034 is Y-→Z-→X- with switches0,61. Its Y- phase at0 emits nothing;
the remaining rows are exactly equal to B001's Z- at58–61 and X- at62–67.
Its extra nominal vertical transition prevents local duplicate suppression
from merging it with B001.

In the completed History B catalogue, **77 of153 descriptions contain a Y
label**. Those labels cannot cover B6's active collision window; they survive
through inactive phases. This is the same representational redundancy as the
initial X- phase in B007, not an additional physical route around this wall.
All77 have the same position requirements as an earlier option without a Y
label, as verified against the recorded row sets. Their nominal resource
counts are still different.

**Checked evidence:** History B's recorded Y masks, relevant-state list and
face planes in the [captured input](b6-options-evidence/history-B-input.json.gz);
B034/B001 in the [option records](b6-options-evidence/history-B-options.json.gz);
`_prepare_item`'s inactive-state handling and `compile_passages`'s treatment of Y.

## 5. Is involving irrelevant ticks a generator bug?

**Question:** “so this is a bug? we are dealing with constraints for b6 so we shouldnt be involving ticks other than those we potentially interact with it”

**Answer:** Yes: the verified cases expose a bug in how the generator creates
and counts passage descriptions. The position rows are already restricted to
B6's relevant states, but the nominal face history is not. An inactive phase
can therefore generate extra options and consume nominal transition counts
without imposing a B6 constraint.

For History B, the required-state lists are X=59–67 and Z=58–67. B007's initial
X- at0 and B034's Y- at0 are outside those windows, emit nothing and should
not create extra meaningful B6 passage choices. These examples do not impose
an erroneous B6 position inequality at0; their defect is redundant option
generation and artificial transition bookkeeping.

The expected contract is to base B6 passage choices on its actual potential
interaction states, normalize inactive phases away, then identify options and
count meaningful transitions. Simply forbidding switch0 would leave equivalent
inactive phases at other irrelevant boundaries.

The relevant-state sets must retain the pre/post-tick endpoints required by
Minecraft's collision-axis order; “relevant” is not just a visual estimate of
when the player approaches the wall. The existing per-axis lists already carry
that distinction. This answer records the requirement; it does not implement
the generator redesign.

## 6. Why construct irrelevant face histories—is that also a bug?

**Question:** “\"But the generator still constructs face histories outside those states. Empty phases such as ‘X− at tick 0’ or ‘Y− at tick 0’ then create extra options and artificial transition counts.\" why do we do this? is this also bug”

**Answer:** Yes. Generating the meaningless alternatives and counting their
transitions are parts of the same representation bug.

The current code combines two mechanisms: every face sequence starts at0 over
the full journey, and an irrelevant state marks every face allowed. Allowing
movement without an avoidance condition at that state is appropriate. However,
the representation expresses that absence of a condition using arbitrary face
labels, then enumerates and counts different label sequences.

“No B6 condition here” should not become “choose among six different B6 faces
here.” There should be no B6 face decision at an irrelevant state. The code
does not currently normalize those inactive phases away before option identity
and transition counting.

There is no physics or angle-solver requirement for B6 switches at state0.
They are a consequence of the generic whole-horizon representation. This
explains the source's mechanics; it does not establish the original author's
historical rationale for choosing that representation.

## 7. Do ceilings and landings use the same generator?

**Question:** “does ceiling/landing also go through this same code? how are those handled?”

**Answer:** Partly. A required landing or ceiling hit is selected as part of the
movement plan, before the generic obstacle-face histories. Avoiding an unwanted
collision with those same boxes can use the shared constraint generator.

| Situation | How it is represented |
| --- | --- |
| Planned landing | A downward vertical contact at the block's top, plus X/Z requirements making the player's footprint overlap the finite landing block at the collision phase. |
| Planned ceiling hit | An upward vertical contact at `ceiling underside - player height`, plus X/Z footprint overlap with that finite ceiling box. |
| Avoiding a ceiling or floor box as an obstacle | Generic face choices can keep the player below, above, or beside it. Y faces are checked against the already selected vertical trajectory. |
| Avoiding an earlier, unintended landing or an unplanned step | Specialized contact checks generate alternative X/Z rows that keep the player outside that block's footprint at the relevant contact state. These alternatives pass through `ConstraintVariants`, without enumerating a six-face history for that check. |

For a planned ceiling hit, the ceiling-profile code finds an upward crossing of
the underside and inserts a contact. The vertical replay clips the movement to
that contact plane and clears its vertical movement carry before applying the
following gravity/friction update. A planned landing similarly clips to the
block's top while descending and sets grounded. For example, a ceiling whose
underside is Y=3 puts the player's feet at approximately Y=1.2 on contact; a
landing on a block whose top is Y=1 puts the feet at Y=1. Neither contact can be
established from Y alone: the X/Z overlap rows require the player to actually be
under/over the chosen box. Their state follows collision ordering; ordinary Y
contacts use the pre-horizontal position, and step cases have distinct timing.

Ordinary route landings get their footprint rows from the scheduled support
checks; explicit contact events also compile footprint rows and vertical
contacts. Some required-contact alternatives are supplied to `ConstraintVariants`
as preconstructed row choices. This is shared constraint assembly, not selection
of the required landing or head hit through arbitrary Y-/Y+ face histories.

The same box can have both roles. B7 can be the required landing support at its
selected landing time while its sides/interior still need avoidance earlier.
The floor checks explicitly exclude the intended landing/step when checking
for accidental contacts. Required contacts are not discarded merely because
unwanted interior collision with that box is unreachable.

Thus a **Y- passage label means staying below a box**, not hitting its underside;
Y+ means staying above it, not requesting a landing. The irrelevant-phase bug
described above can also affect generic avoidance histories for ceiling/floor
boxes. The required contact events themselves are not created by that faulty
whole-horizon face enumeration.

**Checked sources:**
[ceiling profiles](../../src/parkour_solver/movement/ceiling_profiles.py),
[vertical contact replay](../../src/parkour_solver/physics/vertical.py),
[contact-event compilation](../../src/parkour_solver/movement/contact_combinations.py),
[scheduled support constraints](../../src/parkour_solver/scene/route.py),
[unwanted floor/step checks](../../src/parkour_solver/constraints/contacts.py),
[shared generator](../../src/parkour_solver/constraints/variants.py), and
[interaction role handling](../../src/parkour_solver/reachability/service.py).

## 8. Where are planned contacts—are they just anchors?

**Question:** “where are planned landing/ceiling hits. are these just anchors?”

**Answer:** Anchors specify required landing blocks in order. A movement program
specifies the actual selected contacts and their times. They are related, but
not interchangeable. The anchor list is not an exhaustive list of every block
the movement generator can propose landing on; see question9 below.

In this run, the input declares `anchorSearch.anchors = [b2,b3,b4,b5]`, with b1
as the starting support and b7 as the final landing goal. The anchor adapter
requests a landing on the next required block; it does not prescribe its tick.
The movement search generates candidate programs with concrete jump/contact
timings. Other program choices can produce different timings for the same anchor.

For example, the captured **History B candidate program** has these arrivals:

| Block | Role in the request | First contact state in this candidate |
| --- | --- | --- |
| B2 | Required anchor | 17 |
| B3 | Required anchor | 28 |
| B4 | Required anchor | 40 |
| B5 | Required anchor | 58 |
| B7 | Final landing goal | 67 |

These are recorded candidate decisions, not a claim that this B6 option or
the whole program was accepted by the numerical solver. Contacts are stored in
`program.profile.contactEvents`, including `axis`, `direction`, `plane`, `box`,
`role` and `offset`. For example, the B7 entry has `axis='Y'`, `direction=-1`,
`plane=7`, `role='landing'`, `offset=66`. That is contact during input66,
producing state67. The profile also contains vertical replay contacts and the
resulting Y/grounded trajectory. It records repeated ground-support contacts
during run-up ticks too; these are not separate jumps or extra declared anchors.

The normal movement generator creates landing choices in `normal_choices`.
It finds the descending crossing of a candidate support's height and attaches
the finite landing box and overlap constraints. The current anchor adapter
restricts support choices according to its ordered/no-return policy; local
same-owner hops may occur without introducing another declared anchor.

A ceiling hit is a separate movement choice, generated by
`open_ceiling_choices` on the physical movement path. It produces an upward Y
contact with `role='ceiling'`, its box and time. **The current anchor adapter
only requests landing goals; a ceiling hit is not one of its declared anchors.**
The captured run enabled movement types `normal` and `stepping`, not
`ceiling`, and History B contains no ceiling contact events. Question10 records
the subsequent configuration change. The preceding
answer described the available ceiling implementation, not an additional
ceiling event in this run.

There is also a different field named `anchors` inside some movement units/case
events, such as `anchors={'hit': ...}`. Those are named timing markers within
a movement, not the ordered block milestones in `anchorSearch.anchors`.

**Checked sources/evidence:**
[run input](../../cases/real11/physical-anchors.json),
[anchor goal selection](../../src/parkour_solver/search/anchors/adapter.py),
[anchor visit auditing](../../src/parkour_solver/search/anchors/policy.py),
[normal landing generation](../../src/parkour_solver/movement/normal.py),
[ceiling movement generation](../../src/parkour_solver/movement/ceiling_generation.py),
[program contact storage](../../src/parkour_solver/movement/program.py), and
[captured History B program](b6-options-evidence/history-B-input.json.gz).

## 9. How is a landing on a non-anchor block generated?

**Question:** “you didnt answer my question. what if we land decision on a non-anchor block. how is that generated?”

**Answer:** It is generated by the same ordinary landing generator,
`normal_choices`, which loops over scene blocks rather than only the declared
anchors. Being an anchor is not a prerequisite for a generated landing.

For each candidate vertical approach from the current program, it:

1. Considers jumping or continuing without a jump as permitted by the current
   grounded state. These are generated movement alternatives, not the current
   numerical solver witness's future motion.
2. Loops over every scene block permitted by `supportAllowed`.
3. Finds the first descending crossing of that block's top in the candidate
   vertical trajectory. If there is no crossing, there is no ordinary landing
   choice on that block for that approach. Reachability can also rule it out.
4. Adds an explicit downward Y contact at that crossing and four X/Z bounds
   requiring footprint overlap with the block before horizontal movement.
5. Replays the vertical contact, yields a movement choice with
   `terminalSupport` set to that block, and appends it to the existing program.
   Appending shifts relative event times into the full program's timeline.

For a hypothetical additional non-anchor block N, topY=6, if the chosen approach
first crosses its top during input9 relative to the current prefix, the proposed
choice ends at local state10. It has a Y landing event with `offset=9`,
`direction=-1`, `plane=6`, N's actual box, and X/Z overlap rows at local state9.
The numerical facing solver must then find an actual horizontal path satisfying
that landing and all earlier constraints. This example illustrates the code;
it is not a recorded additional block or event in the Real11 run.

An ordinary jump/airborne landing is a meaningful movement decision. If it
does not reach the current anchor goal, it remains a pending prefix. When
selected for descent, the current interval1 policy queries that decision with
the numerical solver before further extension. It does not advance the anchor
index merely because it landed somewhere else. A one-tick continuation while
already grounded is not treated as another meaningful jump decision.

There are important filters in this specific anchor search:

- `AnchorAdapter.context` forbids landing on departed owners, later anchors
  ahead of the current target, and the final goal before the anchors. A fresh
  non-anchor owner is not categorically forbidden by this initial filter.
- Once a fully validated direct hard transfer from the source anchor to the
  next anchor is found, the controller records a per-span `hardLatches` entry.
  For that span, `transfer_structure` rejects an alternative visiting a third
  owner with reason `intermediate_support_between_anchors`. Thus the generator
  may propose such a landing, but the controller can reject it before a
  numerical call. The same policy also restricts transfer jump count and
  stepping; source-local setup hops remain possible.

The generic avoidance face-history generator does not discover these landings.
A generated landing adds contact constraints and changes the vertical program;
an accidental landing in an avoidance-only candidate is a conflict to resolve,
not automatically converted into a new accepted movement decision.

My previous response described the declared milestones and the recorded
History B contacts, but omitted this general non-anchor generation path.

**Checked sources:**
[ordinary landing generation](../../src/parkour_solver/movement/normal.py),
[overlap bounds](../../src/parkour_solver/movement/cases.py),
[program composition](../../src/parkour_solver/movement/program.py),
[pending-prefix generation and node solves](../../src/parkour_solver/search/frontier.py),
[meaningful decisions](../../src/parkour_solver/search/feasibility.py),
[anchor support filter](../../src/parkour_solver/search/anchors/adapter.py),
[transfer policy](../../src/parkour_solver/search/anchors/policy.py), and
[controller filtering and latch creation](../../src/parkour_solver/search/dfs.py).

## 10. Enable ceiling hits; are they part of the same generation?

**Question:** “you also didnt explain ceiling. lets enable ceiling. is it part of this same generation.”

**Answer:** Yes. Ceiling hits are generated alongside ordinary landings and
stepping by the same movement-choice dispatcher and persistent search. They
use their own generator because an upward contact has different physics from
a downward landing. They are not discovered through generic obstacle-face
history enumeration and do not require the contacted box to be an anchor.

On 2026-11-16, the maintained Real11 input was changed to explicitly set
`ceilingHits: true` and append `ceiling` to `movementSearch.types`, preserving
the existing normal/stepping ordering. The implementation already existed;
no production algorithm was added or changed. Historical inputs/reports remain
unchanged. The changed movement domain requires a fresh search context; exact
resume of an older checkpoint needs its original input.

The physical/open generation path is:

1. From the current prefix's vertical state, consider the allowed jump/no-jump
   approaches, just as for ordinary landing generation.
2. For each scene block, compute its underside's player-foot contact plane:
   `box.minY - BODY_HEIGHT`. Find the first upward crossing on that approach.
   If there is no crossing, this box produces no ordinary head-hit choice.
   Shared interaction reachability can also rule the approach out.
3. Create a finite upward Y contact with that box, hit time, direction+1 and
   `role='ceiling'`. Replay the collision, clearing upward movement carry.
4. Yield a movement choice ending just after the hit. It can end **airborne**;
   it does not force a landing tail or invent a new required anchor.
5. Append the choice to the full program. When selected for descent, the
   current cadence1 policy queries the meaningful ceiling decision. The
   compiled contact requires X/Z overlap with the ceiling at the Y collision
   phase, together with earlier contacts and obstacle avoidance. Further
   movement choices can then extend this prefix to a landing or another event.

The same ceiling family also includes its existing low-clearance grind cases.
An upward head hit is not counted as an ordinary landing visit by the anchor
audit, which reads downward contacts/grounded support visits.

**Concrete generator check:** With the existing `ceiling-bonk.json` fixture,
initial feetY=1 and ceiling undersideY=3, `generate_choices('ceiling', ...)`
returns a one-input choice hitting during input0. It ends at state1 with feetY
`1.2000000476837158`, `grounded=false`, and an explicit upward contact on the
ceiling box. Extending that prefix with the normal generator produces a goal
landing two inputs later, at full state3. This is an actual inspected generated
choice, not a hypothetical Y-face description. The native ceiling integration
test also solves and validates a three-input ceiling-bonk route.

**Current Real11 geometry check:** All seven stored boxes have minY=0, so their
underside contact plane is approximately -1.8. They do not offer an overhead
head-hit opportunity on the above-ground transfers. Direct ceiling-generator
checks at the original state and captured History B states17/28/40/58/67 all
returned zero choices, even without reachability pruning. These checks reused
the captured vertical programs under the newly enabled configuration; they
were not a new cold Real11 numerical search or a performance comparison.

**Validation:** 27 tests passed with native compiler and worker enabled,
zero failures/errors/skips: ceiling contacts, generic composition, ceiling-aware
transfer time bounds and package layout. Layout validation passed, including
902 protected evidence/fixture hashes. No new full cold benchmark was run for
this configuration change.

**Checked sources:**
[enabled maintained input](../../cases/real11/physical-anchors.json),
[movement dispatch](../../src/parkour_solver/movement/generation.py),
[ceiling generator](../../src/parkour_solver/movement/ceiling_generation.py),
[shared prefix traversal](../../src/parkour_solver/search/frontier.py),
[finite-contact constraints](../../src/parkour_solver/movement/contact_combinations.py),
[ceiling fixture](../../tests/fixtures/movement/ceiling-bonk.json),
[native ceiling checks](../../tests/unit/movement/test_ceiling_contacts.py), and
[composition checks](../../tests/integration/test_generic_composition.py).

## 11. Do landing, ceiling and stepping precede obstacle-avoidance generation?

**Question:** “do landing, ceiling, and stepping all happen before the avoid obstacle generation?”

**Answer:** Yes, for each proposed movement prefix. The movement generators
select the intended event/case first. The avoidance face/timing generator then
works with that prefix's selected contact schedule and vertical trajectory.

The current physical anchor search follows this order:

1. Propose a landing, ceiling hit or stepping case and append it to the current
   program, with its event time, finite box and physical prerequisites.
2. Apply movement-policy/reachability screening. When the meaningful prefix
   is selected for further descent, query it at the current decision cadence1.
3. Compile its contact schedule, replay the vertical trajectory, and assemble
   required finite-contact X/Z constraints. This establishes the Y/grounded
   timeline the horizontal solver must respect.
4. Construct `ConstraintVariants` for unwanted collisions over the scoped
   prefix, using that Y timeline and reachable horizontal envelopes. Generate
   avoidance face/timing alternatives, plus applicable unintended floor/step
   avoidance rows. Apply the pre-solver checks to these alternatives.
5. Query the numerical facing solver with the intended-contact requirements,
   accumulated earlier constraints and the selected avoidance alternative.
   Validate the result, then extend the accepted movement prefix further.

For example, “land on N at state20” is a movement proposal. The avoidance
generator must subsequently find ways around obstacles along the approach
while preserving the required overlap with N at the landing phase. If no
acceptable avoidance/solve result is found, that proposal is not extended as
a validated landing. The avoidance generator does not turn a Y+ face label
into a landing or change the selected landing into a head hit.

This order repeats at each meaningful movement decision. It does not mean
first fixing every landing/head hit/step for the entire route and only then
performing the first avoidance solve. Avoidance constraints already committed
by an earlier prefix solve remain part of subsequent extensions. Cheap
interaction eligibility checks can run inside movement generation; those are
not the combinatorial avoidance face-history enumeration discussed for B6/B7.
One-tick grounded continuations may accumulate without a separate meaningful
decision solve, as described in question9.

**Checked sources:**
[movement proposal/composition](../../src/parkour_solver/movement/generation.py),
[selected-prefix traversal](../../src/parkour_solver/search/frontier.py),
[decision queries](../../src/parkour_solver/search/dfs.py),
[contact preparation and avoidance assembly](../../src/parkour_solver/search/engine.py), and
[avoidance alternatives](../../src/parkour_solver/constraints/variants.py).

## 12. Recommended redesign of avoidance-passage generation

**Question:** “ok good. lets now rethink how we generate the avoid passages. what do you recommend”

**Recommendation:** Generate distinct avoidance requirements over actual possible
collision windows, rather than enumerating arbitrary full-journey face strings
and discarding their irrelevant parts afterward. Keep the existing movement,
collision-order, reachability and numerical models authoritative. This is a
proposal, not an implemented generator change.

### Start from the obstacle's actual interaction window

The selected landing/ceiling/stepping program already supplies a vertical
trajectory. Existing interaction queries provide the possible collision phases
and their required X/Z endpoint states. Use these as the generator's domain.
Before/after an obstacle's relevant windows, store no face choice. Preserve
phase-specific endpoints: for History B, X states59–67 and Z states58–67 do not
become one naive common tick range. Disconnected windows must remain explicit;
do not invent switch decisions in a gap merely because neither side constrains
the trajectory there.

Apply the fixed-Y information before face-history enumeration. If neither Y
side is possible anywhere active, those sides cannot enter the active passage
search. If the selected vertical path actually clears a box above/below, use
that known clearance where it applies; do not create several aliases for it.
This preserves legitimate over/under routes while removing B6's ghost Y phases.

### Build one meaningful segment/switch at a time

Start from possible useful faces in the first relevant phase, extend a segment
over relevant states, and lazily branch on a different face and switch boundary
only when it produces a distinct avoidance requirement. Do not construct every
three/four-face permutation in advance. Preserve every genuinely different
timing choice: an earlier voluntary switch can be feasible even if a later
switch is also mask-valid. “Switch only when forced” would incorrectly discard
such alternatives.

Use the existing collision-order handoff rules, `face_clearance`, contact
requirements and envelope masks. Normalize away inactive phases and count
meaningful switches afterward. A partial-search identity must include its
active position, continuation face/capabilities, effective constraints and real
remaining resource counts. Equal rows alone do not justify merging unfinished
states with different possible continuations. Normalizing the old ghost counts
is an intentional policy fix and requires a new continuation context.

### Prune before adding more choices

Use cheap masks/row contradiction checks as each meaningful segment is added.
Use stronger accumulated propagation/travel/relaxed-linear checks at distinct
constraint prefixes where they can prevent further fan-out, with exact-context
caching. Do not invoke the LP for every raw gate label or run a numerical
facing solve at every tick. Measure check cost against avoided enumeration.

Keep the already implemented complete-B6 screen before extending B7 and the
B6+B7 screen after that extension. A B6 prefix passing necessary checks is
not a feasible trajectory. The numerical solve still handles B6/B7 together
with the movement program and all earlier requirements.

### Merge equal options and remove provably unnecessary stronger ones

For completed options in the same physical/model context, canonicalize the
effective position constraints and meaningful resource usage. Generate an
equivalent option once. If resource usages are incomparable, preserve their
different continuation capabilities instead of picking an arbitrary label.

Also discard a stronger completed option when a weaker valid avoidance option
covers all its trajectories, consumes no more of any resource, and preserves
the same contact and future capabilities. Initially use exact row inclusion
and elementary bound implication; a solver heuristic score is not such a proof.
Do not apply this rule to unfinished prefixes without checking their remaining
continuations. A tighter variant could still be used as an optional numerical
initialization aid, but need not be a separate geometric branch.

**Concrete, freshly checked History B evidence:** Grouping the saved records
by exact sets of `(tick, axis, comparison, rhs)`, without float rounding, gives:

| View of the saved B catalogue | Count |
| --- | ---: |
| Emitted descriptions | 153 |
| Distinct position-row sets | 69 |
| Distinct position-row sets rejected by the existing checks | 57 |
| Distinct position-row sets not ruled out | 12 |
| Those 12 after removing the six verified stronger return variants | 6 |

The old 36 not-ruled-out descriptions represent those 12 row sets. Every
duplicate row-set group has consistent recorded screening results.

The six weaker representatives are B001–B006: Z-→X-, with switch boundaries
60–65, in the recorded priority order61,62,60,63,64,65. For boundary61, retain
Z<=-1095.300001011921 at58–61 and X<=729.6999989880791 at62–67. Moving that
boundary changes actual constraints and must remain a distinct alternative.

B125–B130 are Z-→X-→Z- with the matching first boundaries and a final switch67.
Each has exactly its B001–B006 counterpart's rows **plus**
Z<=-1095.300001011921 at67. It uses H2/V0/total2 instead of H1/V0/total1.
The extra Z restriction is unnecessary for avoidance because the X restriction
still applies there. These stronger alternatives cover no additional geometric
paths. This is distinct from the identical-row ghost-phase duplicates, such
as B007 and B034.

The count6 describes these saved B6 constraints after the recorded necessary
checks and this verified redundancy removal. It is not six solver-certified
trajectories, a count for every movement history, or a measured runtime claim.
The redesign may reach different work under a fixed wall budget.

### Order the implementation and validate coverage

First fix inactive-phase generation/counts and canonical completed-option
identity/dominance, using the complete A–D ledgers as evidence. Then replace
prebuilt face strings with the lazy segment builder and add measured internal
prefix screening. Reuse the existing compiler/interaction primitives instead
of building another collision model.

Compare feasible-path coverage against the old generator on small exhaustive
cases and independently replay retained witnesses. Include real above/below
clearance, nonoverlapping windows, voluntary early switches, Y/X/Z handoffs,
intended landings/head hits/steps, different resource capabilities and interrupted
resume. Fixing the count bug may intentionally admit paths the old inflated
counts excluded; require an explanation rather than demanding identical raw
catalogues. Measure attempted partial nodes, unique row sets, screen calls,
downstream B7 expansion and numerical outcomes at fixed work. Keep the current
DFS behavior and global budgets; this recommendation introduces no new batching
or branch-rotation policy. Deprioritize an expensive side by guidance if useful,
but discard it only with a valid exclusion/dominance argument, not because it
looks worse in one witness.

**Checked sources/evidence:**
[current generator](../../src/parkour_solver/constraints/variants.py),
[face/timing/contact-order primitives](../../src/parkour_solver/constraints/avoidance.py),
[interaction queries](../../src/parkour_solver/reachability/service.py),
[strong pre-check entry point](../../src/parkour_solver/reachability/screen.py), and
[complete History B records](b6-options-evidence/history-B-options.json.gz).

## 13. Entry/exit geometry and left/right/above/below passage families

**Question:** The user proposes using fixed landing blocks to derive cross
versus wide neo patterns; generating left/right as Z→+X or Z→-X with an
optional return to Z; ranking sides by the guide and expanding both when close;
and deriving above/below patterns from the fixed vertical trajectory. They ask
whether this makes sense, what is missing, and for the plan to be explained back.

**Assessment:** This is a useful replacement for unconstrained face-string
permutations: use entry/exit geometry and the selected Y trajectory to generate
a few meaningful passage families, then vary genuine switch times. Four useful
descriptions are left, right, over and under. They are not necessarily four
independent or exhaustive branches in a multi-window/compound case.

**Clarification:** Question14 corrects my interpretation of the user's first
point: they were identifying when the entry face is determined, not attempting
to classify cross versus wide from the source alone. Question14 also replaces
the secondary compound-case fallback proposed here with a unified design.

### Horizontal passages: use both entry and exit, with signed faces

For a simple encounter, use the previous support/entry region and destination
support/exit region to determine which outside faces can start/end the passage.
Infer a face as mandatory only if the applicable feasible region actually
requires it. Landings are fixed blocks, not fixed X/Z points; the landing
overlap region, collision phase and reachable approach still matter. A corner
region may allow more than one outside face. Use player-expanded box faces,
not block centers or a single guide point.

The source being outside Z alone does not determine cross versus wide: both
Z→X and Z→X→Z can start there. The destination determines whether an exit onto
another face is needed. In the glossary, one horizontal face change is a cross
neo and two are a wide neo.

| Entry/exit relation | Simple passage family |
| --- | --- |
| The whole relevant path can remain outside one face | One face, with no switch. |
| Entry outside Z-, exit outside X- | Z-→X-: cross neo. Reverse/rotate signs and axes as appropriate. |
| Entry outside Z-, exit outside Z+, passing via X+ | Z-→X+→Z+: one side of a wide neo. |
| Entry outside Z-, exit outside Z+, passing via X- | Z-→X-→Z+: the other side. |
| Entry outside Z-, exit can remain outside X± | Z-→X±; no extra Z exit merely for symmetry. |

The optional final Z phase should therefore be required by actual exit needs,
not generated automatically. Z-→X-→Z+ is not the same physical trip as
Z-→X-→Z-. The latter may describe a genuine return in some contexts, but the
History B variants B125–B130 merely add an unnecessary final constraint to an
already sufficient X- exit, as verified in question12.

After choosing a family, keep every switch timing that changes the effective
constraints and is not excluded. An early switch can matter even if a later
one is also mask-valid. Preserve the existing Y/X/Z handoff rules rather than
assuming every corner must be cleared simultaneously at one position state.

### Guide selection: priority and lazy alternatives

Use the guide to try the favored side first. When both look competitive,
expose both early. When one is much worse, it can remain an unexpanded fallback;
do not have to materialize all its timing alternatives immediately.

A guide preference alone does not prove the other side impossible or inferior
for the full route. Incoming velocity, another obstacle, the landing region
or the next jump can reverse the apparent preference. Delete a side only with
a valid reachability/objective/dominance exclusion, or explicitly declare a
heuristic restriction. This does not require rotating away from a chosen DFS
branch or changing the current global budgets.

### Vertical passages: derive the clearance windows instead of guessing Y phases

For the already selected movement program, feet at/above the box top can clear
it above; the player's head at/below the underside can clear it below. Use the
shared phase/swept collision checks, including named-step uncertainty, to decide
when clearance is established. Endpoint height alone cannot certify a Y sweep
that passes through a box. Existing required landing/head-hit/step contacts keep
their separate contact requirements.

Once the safe vertical windows are known, omit horizontal avoidance during
those windows. Generate horizontal requirements only in the remaining possible
collision phases. The familiar H→Y+→H and H→Y- shapes then describe the result;
there is no need to enumerate arbitrary Y labels or treat their timing as a
new independent vertical decision. H means an X or Z outside face. The selected
horizontal path still has to reach the box's footprint within the permitted
clearance window; the solver handles that timing and travel.

The user's above pattern H→Y+→H is the normal full jump-over case: approach
beside the obstacle, clear its top while crossing, then remain outside an exit
face once descending into its height range. If the prefix already begins above
it, the first H is unnecessary. If it ends still above, the last H is unnecessary.

The user's below pattern H→Y- is correct when the player stays safely below for
the remaining relevant encounter. If the player subsequently rises into the
obstacle's height range, a horizontal exit becomes necessary: H→Y-→H. A prefix
that begins under a ceiling can instead need Y-→H before its head rises into
the underside. If the whole encounter is vertically clear, it needs no horizontal
avoidance choice at all. This concerns avoiding contact; a selected ceiling hit
is already a different movement program with an explicit upward contact.

At any instant, above and below are mutually exclusive. For a common fixed
jump, geometry may eliminate one or both families outright. Do not assume one
is always feasible, or that a longer falling/multi-movement profile cannot have
both kinds of clearance at different times. Derive the actual windows once
from the full selected trajectory.

### Retain narrowly generated compound cases

For a simple isolated box and one encounter, the short families are a good
primary generator. Multiple encounters with the same box, disjoint safe-Y
windows, multipart blocks and mixed horizontal/vertical passages can require
more structure. For example, Z→X→Y→Z combines a horizontal corner and vertical
clearance; the existing model admits mixed three-transition chains. Keep a lazy
search over actual unresolved interaction windows for these cases rather than
reintroducing all full-horizon face permutations or claiming the simple table
is exhaustive. Constraints for different obstacles remain coupled/interleaved
on the same timeline and are solved together.

**Recommended resulting flow:** derive phase-correct contact/clearance windows;
infer allowed entry/exit faces from finite regions and reachability; generate
short geometry-compatible horizontal families; order sides by the guide;
enumerate distinct timings lazily; apply accumulated pre-checks, canonicalization
and completed-option dominance; solve coupled obstacles; extend the validated
movement prefix. This is a design assessment only, not a production change.

**Checked sources:**
[domain glossary](../../../CONTEXT.md),
[player-space box expansion](../../src/parkour_solver/scene/geometry.py),
[interaction windows](../../src/parkour_solver/reachability/service.py),
[finite landing-cover guides](../../src/parkour_solver/spatial/anchor_guides.py),
[face and handoff rules](../../src/parkour_solver/constraints/avoidance.py),
[current generator](../../src/parkour_solver/constraints/variants.py), and
[signed/vertical/mixed passage regressions](../../tests/unit/movement/test_obstacles.py).

## 14. Known entry faces, overflight, jump boundaries and compound encounters

**Question:** The user clarifies that source geometry determines whether the
first face is fixed: B6 should start on Z-, while a source with possible X
clearance should permit X-first as well as Z-first. Destination geometry then
determines the optional wide-neo exit. They ask whether above-clearance is a
landing choice or avoidance, whether falling-under then rising belongs to the
next jump, and how to handle compound encounters as a core capability.

**Correction on the first face:** I misread the source-geometry proposal. The
user was distinguishing a mandatory entry face from an ambiguous entry, not
classifying the whole passage from the previous landing alone. Use source
geometry to establish allowed entry faces, side choice to define the middle,
and destination geometry to establish the necessary exit.

B5 and B6 share physical X span[730,731]. B5 is on B6's near-Z side, with
Z[-1096,-1095] versus B6's Z[-1095,-1094]. A valid B5 landing cannot already
clear B6 on either X side while retaining the required B5 footprint overlap;
the feasible landing approaches B6 from Z-. The B6 passage therefore has a
known Z- entry. A subsequent switch may happen early, but this does not make
an arbitrary initial X/Y face a different entry geometry.

For another support with a feasible player position outside an X side, retain
that X-first possibility alongside Z-first when both are possible. Calculate
this from the actual feasible player-center landing/support region, including
player size, clearance and selected earlier constraints. The supported block's
raw corner or one solved/guide point is not the full feasible region. This
entry geometry identifies a meaningful known boundary face; it does not add a
face choice at every earlier irrelevant tick or manufacture a constraint row
where that boundary is already guaranteed. The relevant Y/X/Z contact phases
still govern when an actual handoff can occur.

### Landing on a box versus passing above it

These are separate movement/constraint roles:

- **Land on O:** the movement generator selects a downward contact with O,
  modifies/replays the vertical schedule and requires finite X/Z footprint
  overlap at the landing phase. The resulting contact is in the movement
  program before avoidance generation.
- **Pass above O without landing:** the selected movement program continues
  toward some other contact/destination. O does not acquire a landing event.
  Its known Y-clear intervals belong to avoidance analysis, where horizontal
  restrictions can be released while the body clears O above.
- **Descend past O's top without landing:** require X/Z separation at the
  downward top-crossing phase. Otherwise the player would physically land on
  O and the chosen vertical program would be wrong. The existing unintended
  floor checks provide these avoidance rows, alongside swept-solid checks.

Thus overflight is not covered merely by generating a possible landing on O.
Both alternatives can be generated, but their Y/contact schedules are different.
Avoidance is assembled before the numerical solve; it is not postponed until
after solving an unconstrained overflight.

### Falling under, landing, and a later rise

For an ordinary flight that has passed its apex and becomes low enough to
clear under an obstacle while falling, the user is right: it will not start
rising again without a new movement impulse/contact. If it lands and jumps
again, the new rising encounter belongs to the later movement extension. Do
not extend the current underpass into a guessed future jump or force an exit
for that unselected future encounter.

The original below-then-rising warning was too broad. There is a separate
single-flight case: a jump may begin below a sufficiently high ceiling and
need to exit its footprint before the rising head reaches the underside.
That exit can be needed without an intermediate landing. Detect it from the
already selected Y trajectory rather than inserting H→Y-→H into every underpass.

Current implementation detail: `solve_scope` defaults to the full composed
prefix and `ConstraintVariants` scans every modeled tick in it. Therefore an
extended program can contain multiple encounters/jumps around the same solid.
For the redesign, store avoidance by actual encounter/window and movement
ownership: retain earlier selected rows, append requirements for the new
encounter, and preserve a window that genuinely crosses a movement boundary.
These are not independent dynamical solves. Later numerical queries still
start at the original state with earlier facings adjustable and all retained
contact/avoidance rows enforced. A landing boundary does not erase obstacles,
velocity continuity or existing constraints.

### Make compound encounters native to one passage search

Replace the simple-family generator plus exceptional fallback with a single
small state graph per obstacle encounter and a joint constraint-search frontier.
Cross/wide/over/under are readable paths through this representation, not an
exhaustive set of mutually exclusive labels.

1. **Prepare the actual windows.** From the fixed movement program and shared
   collision-phase reachability, record the relevant encounter windows, known
   vertical-clear intervals, possible signed horizontal faces, entry faces and
   exit requirements. Use actual finite boxes; multipart solids retain their
   physical owner. Uncertain named-case probes remain uncertain.
2. **Represent meaningful local transitions.** States describe which horizontal
   separation is being used at an active window, or established vertical
   clearance. Edges describe a permitted face handoff with its meaningful
   timing range and Y/X/Z ordering. An unchanged face is one segment, not a
   repeated choice every tick. No arbitrary face labels live in inactive gaps.
   Track meaningful resource usage and future capabilities; known clearance
   does not justify resetting them or counting a phantom switch.
3. **Use the same graph for mixed paths.** A passage can use Z separation,
   switch to an X side, exploit an established Y-clear interval, then exit on
   Z. Z→X→Y→Z is generated through these same transitions. Another encounter
   later in the program creates another window of the same physical obstacle,
   rather than unrelated full-horizon face permutations. No prior commitment
   to a single permanent left/right/over/under label is necessary.
4. **Search all relevant obstacles jointly.** A frontier node owns the local
   continuation state for each active obstacle and one accumulated row set.
   Advance an unresolved window/junction, using timeline and tightness to
   prioritize it. Choices for A, B and A again may interleave. Avoid enumerating
   every complete A catalogue followed by every complete B catalogue. Apply
   existing masks, propagation/travel/linear checks to the accumulated constraints
   before another fan-out; unknown checks retain the branch.
5. **Canonicalize, then solve the coupled alternative.** Equal unfinished
   states may merge only when constraints, remaining choices, ownership and
   resources agree. Remove dominated completed alternatives as in question12.
   A facing solve receives a complete applicable joint avoidance alternative
   for the selected movement prefix. Passing a partial pre-check is not enough
   to solve/commit A independently while B's relevant constraints are missing.

For example, A's chosen side may constrain X over inputs10–15, while B's
choice becomes relevant at12. Test B's proposed rows against A's existing
rows and the motion model immediately. If those sides conflict, backtrack B's
choice or A's switch timing/side. If they coexist, keep both during the overlap
and let the numerical solver find the shared trajectory. The indexes here are
illustrative, not an additional measured run. Fixed vertical clearance on
either obstacle can remove its horizontal requirement during part of that
same overlap. This handles multiple-obstacle and mixed-axis compounds through
the same mechanism.

The graph/frontier is a design recommendation, not a claim about the current
obstacle-by-obstacle iterator or a new outer batching controller. It should
reuse existing geometry, masks, handoff compilation and checks. Validate it
with compound/interleaved feasible controls from the outset, together with
known/ambiguous entry faces, unwanted landings, cross-jump ownership and resume.

**Checked sources/evidence:**
[B5/B6 geometry](../../cases/real11/physical-anchors.json),
[landing choice construction](../../src/parkour_solver/movement/normal.py),
[intended versus unintended floors](../../src/parkour_solver/constraints/contacts.py),
[movement composition](../../src/parkour_solver/movement/program.py),
[full-prefix scope](../../src/parkour_solver/search/identity.py),
[retained earlier rows](../../src/parkour_solver/spatial/checkpoints.py),
[current avoidance windows](../../src/parkour_solver/constraints/variants.py), and
[compound/interleaving regression contracts](../../tests/unit/movement/test_obstacles.py).

## 15. One encounter per segment, underpass/contact composition, and current coupling

**Question:** The user proposes four common vertical shapes (H→Y+→H,
Y+→H, H→Y-, Y-→H), selected by geometry; assumes one avoidance encounter
per obstacle per segment, with later jumps handled separately; asks about
below→land→below and below→land→ceiling composition; and proposes ordering
obstacles by encounter/guide order so one side choice can constrain later
obstacles. They request an investigation of current coupling detection/handling
and a general method for arbitrary obstacle combinations. H means X or Z.

### Scope and segment boundaries

Use one encounter per obstacle per landing segment as the proposed initial
domain assumption. Do not pre-generate another encounter for an unselected
later jump. Once that movement is selected, append its avoidance requirements.
The same physical obstacle may then have another segment-owned encounter.

The four patterns describe common vertical entry/exit cases. Derive them from
the full phase-correct Y trajectory plus allowed horizontal entry/exit regions.
Trim entry/exit restrictions when unnecessary: a whole segment already below
or above needs no avoidance branch, and a segment ending above can omit a
horizontal exit. A segment boundary is not itself a face transition. In
particular, remaining below the same ceiling across a landing must not force
an invented horizontal exit/re-entry or consume phantom switch counts.

Keep one original-state dynamical model across these logical segments. Landing
does not fix its solved X/Z position, velocity or earlier facings; retained
contact/avoidance rows constrain them in subsequent queries. The current
implementation already retains earlier rows and re-solves from the original
state. New per-segment ownership should preserve this property. Existing
physical-owner resource policy must not silently reset because rows are grouped
by encounter instead of by whole obstacle.

### How below→landing→below/ceiling is generated

Let O be an overhead obstacle and L a support beneath it. Passing under O is
avoidance; landing on L is a selected movement contact. They can apply together.
The first movement program contains L's downward Y contact and footprint rows,
while O's avoidance permits the known under-clearance. L's landing ends that
movement choice.

From the resulting grounded boundary, the movement generators offer the next
choices. The normal generator considers jump/no-jump approaches and their
possible landings. If the selected next trajectory stays below O, avoidance
requires no new horizontal restriction while that clearance holds. This yields
below→land→below. A higher normal jump that would hit O cannot be accepted by
pretending its Y trajectory remains unchanged under the ceiling.

The ceiling generator separately considers an upward crossing of O's underside
from that same boundary. It adds an upward contact with finite X/Z overlap,
clips the vertical trajectory and yields the post-hit airborne prefix. This
gives below→land→ceiling, followed by another movement/landing choice. Existing
low-clearance grind variants belong to this family too. Ceiling contact is a
movement alternative, not an extra avoidance Y label. Anchor/owner/hard-transfer
filters may independently restrict which optional supports L the run permits.

**Fresh checked example:** In `generic-repeat-ceiling.json`, the existing
generated program reaches the raised support at state5, then takes three ground
continuations to state8. FeetY=.75, headY=2.549999952316284 and the second
ceiling underside is2.85. At that boundary the normal generator offers a
one-input grounded continuation still below the ceiling. The ceiling generator
also offers a requested jump, upward contact during local input0, ending at
full state9 with feetY=1.050000047683716. These are inspected generated choices;
the grounded continuation is not a separate claimed under-ceiling jump witness.
The existing native repeated-ceiling test was rerun this turn and passed,
independently validating its selected 12-input composed route with the later
ceiling hit and goal landing. No new cold Real11 run was needed.

### What the automatic search currently calls coupled

There is **no automatic pair/group detector declaring B6+B7 a special coupled
unit**. My earlier use of “coupled” referred to their shared physical variables
and constraints, not a stored pair declaration.

- The movement search evaluates selected meaningful prefixes at cadence1,
  plus required anchor/final arrivals. `solve_scope` defaults to the entire
  prefix and all declared solids. `evaluate_program` combines its contacts,
  earlier retained rows and avoidance variants into a shared motion model.
- `ConstraintVariants` visits solids in scope order, which currently follows
  scene order, not an inferred encounter order. It prepares each obstacle's
  face/timing catalogue against the baseline reachable envelopes for that
  movement program. It then lazily extends accumulated choices obstacle by
  obstacle. New completed obstacle choices undergo a screen before extending
  the next one; B6 then B6+B7 is an instance of that general mechanism.
- `_screen` checks accumulated rows against envelopes, then `contradiction`
  builds the model with those rows, recomputes forward/backward reachability,
  checks travel bounds and may invoke the relaxed linear exclusion. Incompatible
  combinations can be rejected here. Survivors are given to one numerical
  facing solve with all their constraints; necessary checks do not certify
  feasibility.
- The strong screen returns an exclusion or no exclusion. It does **not**
  return the tightened envelopes to the next obstacle's generator. Its local
  propagation results are discarded after the verdict. Consequently A-left
  does not update B's cached masks/timing tables before B's options are built.
  An impossible B choice can be generated and only then rejected against A.

This is joint validation, but weak use of the joint information during generation.
It explains why the current process can enumerate many downstream possibilities
even when an earlier side choice greatly restricts them.

### A separate older supplied-window mechanism

`search.compat.decision_windows.plan_windows` accepts authored opportunity
input ranges. It merges groups whose ranges overlap/are within a supplied gap,
whose ready-state requirement crosses the next onset, or whose declared
`dependsOn` links cross the boundary. Dependencies merge intervening groups
transitively. Its default gap is2 inputs.

That planner does not infer geometric obstacle coupling. The current automatic
anchor path enters `search_event_routes` before this supplied-corridor planner
is called. It therefore does not use that two-input grouping rule for B6/B7.
The useful idea is explicit dependencies/shared solve scope, not importing a
fixed gap as a geometric independence test.

### Generalize side inference through conditioned generation

The user's sequential idea is sound as an order for making choices. Use guide
projection as a priority hint, with earliest possible interaction windows and
constraint tightness where available. Avoid making guide order a compulsory
physical itinerary: taking A's other side can change whether/when B is encountered.
If time windows overlap, decisions can interleave. Nonoverlapping windows can
still interact through speed, turning and available travel between them.

Recommended branch operation:

1. Construct small geometry-compatible encounter choices with the known entry,
   possible side/vertical clearance, destination-dependent exit and real timing
   ranges. Required landing/ceiling/stepping constraints are already present.
2. Select an unresolved encounter, usually following likely encounter order.
   Add a side/timing choice to the accumulated constraints.
3. Reuse the shared propagation with those rows and **retain its conservative
   tightened position/velocity/phase envelopes in this branch**. Exact motion
   coefficients/preparation remain shared; branch-specific bounds have their
   own immutable context.
4. Generate/filter other encounters from those conditioned bounds before
   enumerating their internal timing combinations. Remove excluded faces,
   shorten impossible timing ranges, and remove encounters now guaranteed
   irrelevant. If a choice is left as the only remaining one, apply it and
   propagate again. A still-unknown branch remains available.
5. Continue until there is another genuine alternative or the applicable joint
   constraint choice is assembled. Backtrack when a domain becomes empty.
   Reconsider earlier choices when later requirements constrain them. Use the
   numerical solver for the resulting joint problem, not separate fixed
   endpoint/velocity solves for A and B.

For an illustrative example, A's side/timing can require X<=.5 at state10.
If the maximum possible X movement to state11 is.3, B's choice requiring
X>=2 at11 is excluded before that B choice's other timings are expanded.
If B's opposite side remains possible, the branch can infer it. No handwritten
rule about “A-left always means B-right” is needed, and the same mechanism
works for translated, rotated and larger obstacle arrangements. An axis-aligned
interval method may miss some correlations; the existing travel/linear checks
and final numerical solve still handle unresolved compatibility.

Do not identify a branch with one solved trajectory or use a guide point as
the propagated envelope. Conservative domains preserve all allowed trajectories
for its constraints. Cache exact-context rejected combinations so another
obstacle does not cause the same A/B contradiction to be rediscovered; use only
the proven contributing choices, or conservatively cache the full tested
assignment. An NLP timeout/local failure must not become a universal side
incompatibility. Pairwise-compatible choices may still fail as a triple, so
all accumulated choices must continue to share the same model and checks.

This combines the geometry-derived short families with proper compound handling:
one encounter per obstacle per segment, conditional choice domains, a shared
constraint-search frontier, and full trajectory continuity. No new production
implementation or obstacle-order policy was applied in this investigation.

**Checked sources:**
[movement continuations](../../src/parkour_solver/movement/normal.py),
[ceiling contacts](../../src/parkour_solver/movement/ceiling_generation.py),
[composed state](../../src/parkour_solver/movement/program.py),
[default full-prefix scope](../../src/parkour_solver/search/identity.py),
[query assembly](../../src/parkour_solver/search/engine.py),
[current obstacle order/screens](../../src/parkour_solver/constraints/variants.py),
[strong-screen return boundary](../../src/parkour_solver/reachability/screen.py),
[interval/travel propagation](../../src/parkour_solver/reachability/intervals.py),
[supplied-window planner](../../src/parkour_solver/search/compat/decision_windows.py),
[automatic entry path](../../src/parkour_solver/search/events/engine.py), and
[rerun composition regression](../../tests/integration/test_generic_composition.py).

## 16. Plan document, supplied-window provenance and commitment boundaries

**Question:** The user requests that the conditional-generation weakness be
recorded as a fix, asks where the supplied-window planner came from and whether
to remove/wire it in, recalls grouping close-in-time decisions into one pass,
asks when current decisions are combined/committed and how anchors fit, and
requests an initial plan document for the agreed ideas.

**Written plan:**
[Geometry-driven passages and conditional generation](../../docs/design/AVOIDANCE_PASSAGES.md).
It is an initial design, not an implemented change. The required fix is explicit:
retain the stronger propagation's conservative branch-conditioned bounds and
use them in downstream face/timing generation before fan-out. The plan covers
known entry geometry, one encounter per obstacle per landing segment, vertical
clearance/contact composition, compound choices, joint propagation, meaningful
canonicalization and validation. Scheduling/commit details remain to be fleshed out.

**Provenance:** The user's memory matches commit `9556c38e`, dated2026-11-01,
“feat: group decision windows and retain objective near-ties.” It introduced
the old `autonomy/cell_routes/decision_windows.py`, experimental drivers,
fixtures and tests. The original rationale explicitly says not to greedily
commit the first member of a tightly coupled nearby group. Supplied ranges
were merged by overlap/proximity (default gap2), ready-state requirements and
explicit dependencies; the planner then chose a later common comparison clock.
It did not discover those ranges/dependencies from arbitrary scene geometry.
The original54 cold/54 diagnostic trials and402 witness replays are preserved.
They exposed both dense-cluster enumeration cost and progress-ranking failures;
they do not establish that temporal grouping itself should be discarded.

Goal-aware DFS (`cdf46afc`, 2026-11-03) and physical anchor policy
(`0d340c03`, 2026-11-04) superseded the automatic traversal/comparison policy.
Packaging commits `e233254f`/`9c79e741` moved the module into `search/compat`.
The current supplied-window production callers are `search/engine.py` (plan
and domain filter) and the optional `DecisionPolicy` audit in `search/dfs.py`.
Tests and `research/legacy_cell_routes` reproduction/diagnostic drivers also use it.
The automatic event/anchor entry bypasses the authored planner.

**Recommendation:** Do not wire this supplied-clock/progress-band mechanism into
the new automatic generator. Reuse the dependency principle through conditional
domains and accumulated physical constraints. It is removable if the explicit
supplied-opportunity mode and callers/tests/runners are retired or migrated,
but is not dead code today. Keep it isolated during the generation change;
decide retirement separately and preserve frozen evidence. No code was removed
or newly wired in during this documentation turn.

**What currently happens together:** Every numerical query jointly solves the
entire selected prefix from the original state, with earlier facings free and
all retained physical rows. The default cadence1 runs that query at each
selected meaningful completed movement decision, not per simulation input.
All applicable obstacle choices for that queried prefix are combined in its
constraint variant. There is no active automatic “within N ticks, delay both
movement choices into one solve” detector. Required anchors/final always have
their designated evaluation/comparison.

**What currently commits:** On a successful internal query, `evaluate_node`
already invokes `commit_arrival`. `retain_prefix` preserves selected physical
and avoidance inequalities plus a yaw seed; the successful program is cached.
This is before an anchor if the movement decision is internal. The model does
not pin solved X/Z/velocity/facings, but a selected side represented by retained
rows remains a restriction of this branch. At a required anchor, the controller
additionally chooses/promotes an arrival and advances the anchor index; hard
transfer learning is another separate structural restriction.

Thus anchors define comparison/promotion stages, not the only place constraints
are committed. Original-state reoptimization preserves continuous coupling;
it does not automatically undo a discrete choice or side inequality. The current
O4 paragraph saying a feasible node contributes “a yaw seed only” was corrected
to reflect this inspected implementation.

**Open item explicitly added to the plan:** Audit and specify preservation of
alternate avoidance choices across early feasible movement queries. The internal
success path drops that node's repair state and caches its selected program.
Ordinary movement/anchor backtracking is not by itself proof that all alternate
avoidance variants at that internal node remain recoverable. A targeted control
must show an early feasible side failing later while its sibling succeeds.
This source observation is not a claimed demonstrated route loss. The plan
must distinguish an early feasibility check, branch-local commitment and final
selection before choosing a new close-decision grouping/commit policy.

**Validation for this turn:** Documentation links/diff and layout/evidence
integrity checks; no runtime change or new numerical benchmark is claimed.

**Sources:**
[original experiment](../decision-windows/README.md),
[original measured results](../decision-windows/RESULTS.md),
original clustering rationale,
[current cadence contract](../../docs/design/ANCHOR_POLICY_DECISIONS.md),
[planner](../../src/parkour_solver/search/compat/decision_windows.py),
[dispatch and optional supplied planner](../../src/parkour_solver/search/engine.py),
[internal/anchor commitment](../../src/parkour_solver/search/dfs.py), and
[retained inequalities](../../src/parkour_solver/spatial/checkpoints.py).

## 17. Deferred consolidation and expanded avoidance plan

**Question:** The user asks for brief pointers in the deferred document for
automatic close-in-time grouping/commitment consolidation, with no unused or
superseded runtime code left afterward; asks to flesh out the current avoidance
plan's open items and present the remaining questions.

**Deferred item written:**
[Automatic close-decision grouping and commitment](../../docs/design/PRUNING_IMPROVEMENTS_BACKLOG.md#automatic-close-decision-grouping-and-commitment-2026-11-16).
It requires inferred groups rather than authored opportunity inputs, a unified
check/retention/commit policy, and migration/removal of superseded runtime paths.
Frozen evidence is preserved. This consolidation is not a prerequisite or runtime
change in the current avoidance rewrite.

**Plan expanded:**
[Avoidance passage generation](../../docs/design/AVOIDANCE_PASSAGES.md)
now specifies encounter/domain/analysis/frontier ownership, conditioned analysis
before downstream fan-out, shared upfront/repair generation, uncertain switch-time
handling, joint backtracking, canonicalization, cleanup, persistence boundaries
and concrete acceptance controls. It preserves current movement solve cadence
and inter-movement commitment until their deferred consolidation is implemented.

**Two policy questions were presented and answered this turn:**

1. Whether to retain the old two-horizontal/two-vertical/three-total switch caps
   or replace them with the agreed encounter rule. The user chose **the encounter
   rule without switch-count caps**.
2. Whether to retain local repair/alternative-count limits (32 per-program repair
   attempts and128 retained fallback options) or use global time/call budgets with
   saved unfinished work. The user chose **global budgets and saved unfinished
   work**. Derived fallback queue/expansion truncation must also disappear from
   the replacement path. Per-request timeout and unrelated movement/task constraints
   are not removed by this answer.

These are planned implementation requirements; this documentation turn does not
change the current generator's runtime limits.

**Remaining technical details to validate:**

- Encounter completion must distinguish geometric exit from a safe-Y gap or an
  airborne contact query. Segment ownership follows landings, and below clearance
  can continue across them without an invented face switch.
- If a switch remains a range, propagate common guaranteed constraints or lazily
  refine the range. Do not substitute the preferred timing and prune another
  valid timing's paths. Preserve the actual collision-phase rules.
- Start with immutable exact-context analysis caching through existing propagation;
  measure affected-domain updates and relaxed-LP frequency at fixed work. New
  timing thresholds or a second collision model are unnecessary.
- Version frontier/analysis identities and prove sibling isolation plus no lost
  alternatives at global-budget stops/resume. Retire duplicate avoidance builders
  when their callers use the shared replacement.

These implementation questions have recommended approaches in the plan. No
additional user policy decision is currently required to proceed with that design.
Cross-movement close-decision grouping/commit details remain explicitly deferred.

## 18. Why propagate undecided timing, profiling, and interruptions?

**Question:** “2 why do we need to propagate if a switch time is not decided?
3 yes. add profiling as well
4 why do we have interruptions?”

**Timing:** We do not need to. The initial conditional-generation fix can choose
a concrete passage and switch time first, then propagate those exact constraints
before generating the next obstacle. For B6 gate61, that means its actual Z rows
through61 and X rows from62. Gate62 remains a separate lazy sibling; the gate61
bounds do not prune it. Earlier discussion of whole-range propagation was an
optional optimization plus a correctness warning, not a necessity. The plan now
uses concrete timings by default and does not require unused range-propagation
machinery. Only add family-level pruning later if profiling motivates it and
the check preserves all remaining timings.

**Profiling:** Added as an explicit implementation/acceptance requirement in
the [plan](../../docs/design/AVOIDANCE_PASSAGES.md). Reuse existing
instrumentation to measure geometry generation, unique/equivalent branches,
conditioned propagation, relaxed-linear calls/cache use, downstream work avoided,
numerical requests/workers, validation and persistence/copying overhead. Compare
fixed work and matched-budget outcomes with source/settings provenance and
independent replay. Avoid exhaustive counting or per-node logging that itself
recreates the generation bottleneck.

**Interruptions:** This meant the run reaching its global deadline/call limit,
or user cancellation, not new periodic batching or rotation. A checkpoint is
saved state, not another scheduling boundary. If the300-second deadline occurs
while enumerating B after selecting A, save the A assignment and next B cursor
so a later resume can continue. Otherwise the generator runs normally. A per-call
solver timeout stops an individual numerical attempt; it can leave unknown work
without stopping the whole search. Old local repair/candidate caps still exist
in the current code but are explicitly removed from the planned replacement.

**Checked sources:**
[global budgets](../../src/parkour_solver/search/work.py),
[generation budget/cancellation checks](../../src/parkour_solver/search/frontier.py),
[repair work lifecycle](../../src/parkour_solver/search/repair.py), and
[current variant deadline](../../src/parkour_solver/constraints/variants.py).
This turn updates the design/docs; it does not implement or benchmark the rewrite.

## 19. Consolidate the implementation plan

**Question:** “ok thats fine. go ahead and update the implementation plan”

**Answer:** Consolidated the agreed decisions into the
[implementation plan](../../docs/design/AVOIDANCE_PASSAGES.md).
Its execution contract now explicitly selects concrete switch times before
propagation, removes switch-count/local search caps, retains global budgets and
unfinished work, and introduces no periodic batching or rotation. Profiling is
part of implementation from the first stages.

Pending milestones cover baseline/instrumentation, shared conditioned analysis,
geometry-driven encounter generation, joint conditioned traversal, shared repair
generation/cap removal, persistence/integration, and native validation/profiling/
cleanup. Each has a concrete exit gate. Current movement cadence and commitment
remain unchanged; their automatic consolidation is deferred separately. No policy
question remains open for this scope. This update changes documentation, not
runtime behavior; implementation has not started.
