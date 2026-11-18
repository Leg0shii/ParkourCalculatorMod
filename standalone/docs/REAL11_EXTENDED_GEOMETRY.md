# Real11 extended geometry and player bounds

2026-11-15. Geometry-only preparation for the longer Real11 task. The user’s
latest correction keeps **B6 at X[730,731]**, its original unit footprint;
B7 returns to its original unit footprint. The earlier proposed B6 extension
to physical X735 is withdrawn. The old extended-B7 benchmark and its results
remain separate in [physical-anchors.json](../cases/real11/physical-anchors.json).

## Input and coordinate conventions

[Extended geometry](../cases/real11/extended-geometry.json) contains the original
B1–B7 plus the supplied additions. That file remains geometry-only. The later
user-selected [extended task](../cases/real11/extended-anchors.json) declares
B2/B3/B4/B5/B7/B20 and terminal B22, minimizing T then maximizing Z. The
[fixed-B5 experiment](../research/real11-long-solve/README.md) reuses initial-0
decisions through55 while re-solving earlier facings. Other named boxes remain
optional supports/obstacles, not automatically mandatory anchors.

- An integer X/Z is a unit block coordinate: X731 occupies physical [731,732].
  An inclusive range of block coordinates 731 through732 contains two cells and
  occupies physical [731,733]. This applies to B11, B15, B20 and B22.
- B21’s X range is a physical interval [732.875,734], width1.125, as clarified
  by the user. Its Z−1093 means physical Z[−1093,−1092].
- Y ranges are literal physical extents; they are not inclusive block indices.
- The two entries named B9 are retained as B9a and B9b without renumbering later
  labels. They share a full side at Z−1092 over Y[9,9.5], so they are contiguous
  boxes at different top heights. They are separate identifiers in this input;
  neither mandatory routing nor final ownership semantics are inferred from the
  accidental duplicate label.
- B13 is a compound +Z stair: lower half Z[−1091,−1090.5], Y[6,6.5]; upper half
  Z[−1090.5,−1090], Y[6,7]. Normalization names these b13#0 and b13#1, with
  shared physical owner b13.
- Coverage remains the old declared [726,−200,−1108,749,24,−1085] box, which
  contains these supplied solids. This does not assert undeclared world terrain.

## Physical boxes

All intervals below are physical minimum/maximum coordinates after the shorthand
has been expanded. Touching boundaries do not mean overlapping solid interiors.

| Block | X | Z | Y |
| --- | --- | --- | --- |
| B6 | [730,731] | [−1095,−1094] | [0,20] |
| B7 | [729,730] | [−1095,−1094] | [0,7] |
| B8 | [730,731] | [−1094,−1093] | [8.5,11] |
| B9a | [730,731] | [−1093,−1092] | [9,10.5] |
| B9b | [730,731] | [−1092,−1091] | [9,9.5] |
| B10 | [730,731] | [−1091,−1090] | [9,10] |
| B11 | [730,731] | [−1094,−1092] | [6,6.5] |
| B12 | [730,731] | [−1092,−1091] | [5,6] |
| B13 lower | [730,731] | [−1091,−1090.5] | [6,6.5] |
| B13 upper | [730,731] | [−1090.5,−1090] | [6,7] |
| B14 | [730,731] | [−1090,−1089] | [1,21] |
| B15 | [731,733] | [−1090,−1089] | [10,11] |
| B16 | [733,734] | [−1089,−1088] | [10.8125,11] |
| B17 | [733,734] | [−1090,−1089] | [10.5,14] |
| B18 | [731,732] | [−1090,−1089] | [6.5,7] |
| B19 | [732,733] | [−1090,−1089] | [6.5,7.5] |
| B20 | [733,734] | [−1090,−1088] | [7.5,8] |
| B21 | [732.875,734] | [−1093,−1092] | [11,12] |
| B22 | [731,734] | [−1095,−1094] | [8.5,9] |

## How the bounds are calculated

Positions here mean **horizontal player center and feet Y**. Current standalone
geometry uses `HALF_WIDTH = f32(0.6)/2 = 0.30000001192092896` and
`BODY_HEIGHT = f32(1.8) = 1.7999999523162842`. Rounded prose uses0.3 and1.8.
These are the existing Python spatial constants, not a new body model.

For physical box [xmin,ymin,zmin,xmax,ymax,zmax], the existing
[expanded_box](../src/parkour_solver/scene/geometry.py) returns the forbidden
**open interior** for the player’s feet/center:

`[xmin−halfWidth, ymin−bodyHeight, zmin−halfWidth, xmax+halfWidth, ymax, zmax+halfWidth]`.

At a top surface Y=ymax, support initially permits body overlap within the
expanded horizontal footprint. Subtract every neighboring forbidden interior
at that height. The existing [decompose](../src/parkour_solver/spatial/cells.py)
and [support_components](../src/parkour_solver/spatial/regions.py) retain the
result as a union rather than selecting one arbitrary rectangle. The report
uses the existing [supports](../src/parkour_solver/scene/route.py) predicate to
omit regions where the body merely touches its own support edge with zero area
of overlap. The routing geometry itself keeps no free line, point or sliver
narrower than the 0.012 minimum free width (see [decisions](DECISIONS_AND_PLANS.md));
zero-thickness faces such as an exact 1.8 vertical slot remain.

The table gives closures/limiting edges, rounded for readability. Actual support
requires positive horizontal overlap, and solver constraint builders apply their
existing clearances. These static standing regions are not swept movement paths
or reachability/landing-time proofs.

| Support | Feet Y | Available center X × Z (union of rectangles) | Blockers at this height |
| --- | ---: | --- | --- |
| b1 | 5 | [741.7, 742.3] × [-1105.3, -1104.3]; [742.3, 747.3] × [-1105.3, -1102.7] | b2 (side) |
| b2 | 6 | [740.7, 742.3] × [-1104.3, -1102.7] | — |
| b3 | 7 | [737.7, 739.3] × [-1101.3, -1099.7] | — |
| b4 | 7.5 | [733.7, 735.3] × [-1100.3, -1098.7] | — |
| b5 | 6 | [729.7, 731.3] × [-1096.3, -1095.3] | b6 (side), b7 (side) |
| b6 | 20 | [729.7, 731.3] × [-1095.3, -1093.7] | — |
| b7 | 7 | [728.7, 729.7] × [-1095.3, -1093.7] | b6 (side), b8 (overhead) |
| b8 | 11 | [729.7, 731.3] × [-1093.7, -1092.7] | b6 (side) |
| b9a | 10.5 | [729.7, 731.3] × [-1092.7, -1091.7] | b8 (side) |
| b9b | 9.5 | [729.7, 731.3] × [-1091.7, -1091.3] | b9a (side), b10 (side) |
| b10 | 10 | [729.7, 731.3] × [-1091.3, -1090.3] | b14 (side), b15 (side) |
| b11 | 6.5 | [729.7, 731.3] × [-1093.7, -1091.7] | b6 (side), b7 (side) |
| b12 | 6 | [729.7, 731.3] × [-1091.7, -1091.3] | b11 (side), b13#0 (side), b13#1 (side) |
| b13#0 | 6.5 | [729.7, 731.3] × [-1091.3, -1090.8] | b13#1 (side), b14 (side), b18 (side) |
| b13#1 | 7 | [729.7, 731.3] × [-1090.8, -1090.3] | b14 (side) |
| b14 | 21 | [729.7, 731.3] × [-1090.3, -1088.7] | — |
| b15 | 11 | [731.3, 732.7] × [-1090.3, -1088.7] | b14 (side), b17 (side) |
| b16 | 11 | [732.7, 734.3] × [-1088.7, -1087.7] | b17 (side) |
| b17 | 14 | [732.7, 734.3] × [-1090.3, -1088.7] | — |
| b18 | 7 | [731.3, 731.7] × [-1090.3, -1088.7] | b14 (side), b19 (side) |
| b19 | 7.5 | [731.7, 732.7] × [-1090.3, -1088.7] | b20 (side) |
| b20 | 8 | [732.7, 734.3] × [-1090.3, -1087.7] | — |
| b21 | 12 | [732.575, 734.3] × [-1093.3, -1091.7] | — |
| b22 | 9 | [731.3, 734.3] × [-1095.3, -1093.7] | b6 (side), b8 (side) |

## Interaction details

- **B7 beside B6:** B6 limits center X to729.7 on B7. Returning B7 to1×1
  means physical Z ends at−1094; the body-overlap limit is still−1093.7.
  B8’s underside is only1.5 above B7’s top, but its obstructed X range is already
  excluded by B6 at this height. It adds no further cut to the surviving B7 strip.
- **Upper row B8→B9a→B9b→B10:** their physical tops are11,10.5,9.5,10.
  B6 cuts the −Z end of B8; B8 cuts the −Z end of B9a. On B9b, the higher
  B9a and B10 cut both ends, leaving center Z[−1091.7,−1091.3], width0.4.
  B14 cuts the +Z end of B10. B15 also appears as a potential raw-footprint
  blocker there, but adds no stricter cut after B14.
- **Lower row B11→B12→B13:** B11 and both B13 halves cut B12 to the same
  narrow Z strip[−1091.7,−1091.3]. B13’s upper riser cuts the lower tread
  at center Z−1090.8; B14 limits the upper tread at Z−1090.3. Their step
  heights are0.5. The finite tread regions include permitted body overhang.
- **Lower-row ceilings:** B11 has2.0 blocks of gap to B8 and2.5 to B9a.
  B12 has3.0 to B9b. B13’s lower/upper tops have2.5/2.0 to B10. All fit
  the standing body. Staying under the same footprint, upward feet travel before
  head contact is limited to approximately0.2/0.7,1.2,0.7/0.2 respectively.
  Jumping may hit the ceiling or leave its footprint; the movement solver must
  decide which. No fixed-airtime claim follows from standing clearance.
- **Upper cross-row:** B15 lies between B14 and B17, so its two-block physical
  width becomes center X[731.3,732.7]. B17 cuts the −Z side of B16 to
  center Z≥−1088.7. B16 is a thin solid Y[10.8125,11], top11.
- **Lower cross-row:** B18 lies between the B14 wall and higher B19, leaving
  center X[731.3,731.7], width0.4. B20 cuts B19 at center X732.7. B18/B19
  have3.0/2.5 headroom below B15. B20 has2.5 below B17 and2.8125 below B16.
- **B21:** standalone top at12, width1.125; unobstructed static overlap bounds
  are center X[732.575,734.3], Z[−1093.3,−1091.7]. This is not evidence that
  a jump reaches it.
- **B22:** with B6 restored to its unit footprint, B22 is exposed. B6 clips its
  −X edge to center X≥731.3. B8 adds no stricter restriction after that cut.
  Available bounds are X[731.3,734.3], Z[−1095.3,−1093.7], feetY9. The
  earlier no-surface result belonged only to the now-withdrawn wide-B6 geometry.

## Face-merging preprocessing

The [single implementation plan](design/FACE_MERGING_IMPLEMENTATION_PLAN.md)
contains exactly two parts: detect adjacent coplanar faces across all six
orientations, and represent the exact merged cover plus its original members
for later decisions. B9a/B9b/B10 provide one underside example; matching top and
side faces follow the same rule. Both detection and representation are implemented.
The contract covers internal/partly hidden faces, exact nonrectangular
unions, source contributions, orientation/coordinate conventions and later-query
contracts. [Geometry-only probe](../research/real11-extension/face-detection-plan-probe.json)
records the proposed boundary calculation for this input.
The [production validation record](../research/real11-extension/face-catalog-validation.json)
confirms102 merged faces from144 raw faces, including the shared B9a/B9b/B10
underside and B9a's partial side. Use `--faces` below for all six orientations.

The earlier [combination probe](../research/real11-extension/ceiling-preprocessing-audit.json)
and [profile probe](../research/real11-extension/profile-dedup-audit.json) preserve
source-specific findings about geometry-blind contact dedup. They are evidence,
not additional instructions in this face-merging plan.

## Reproduce and existing helpers

Existing production geometry helpers are sufficient; there was no maintained
single-command neighbor-aware bounds report. The small
[geometry_bounds tool](../tools/geometry_bounds.py) now wraps them without Java,
IPOPT, routing or new collision formulas. It accepts a geometry scene or a task
fixture with a `request` object. From `standalone/`, with the package installed:

```sh
../.venv-guidance/bin/python -B -m tools.geometry_bounds \
  --input cases/real11/extended-geometry.json \
  --output runs/real11-bounds-new/player-bounds.json \
  --markdown runs/real11-bounds-new/player-bounds.md
```

Add `--faces` to the command to include the physical-face catalog and statistics
in JSON, plus an all-orientation face/member table in Markdown. The default
bounds-only report is unchanged. Face coordinates are physical surfaces; they
do not include player expansion. The [face API](../src/parkour_solver/spatial/faces.py)
also supports exact source contribution queries and validated catalog loading.

[Exact report](../research/real11-extension/player-bounds.json) retains all
float-derived bounds and the full physical input per box. The older
[Java footprint adapter](../research/linkcraft11-opening/PLAYER_BOUNDS.md)
can derive one reference/facing-selected rectangle with neighbors; that is not
a complete union report. The production JavaCompiler convenience footprint call
currently omits neighbors. Use the spatial cover for the full set.

Focused checks cover both arms of a corner, overhead clearance, compound stairs
and buried/tangent-only supports. No cold solve, dynamic jump validation or
executable replay of the extended course has been performed. The original
benchmark fixture, search policy and historical evidence remain intact.
