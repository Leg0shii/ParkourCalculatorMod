# Run-off source support and obstacle behavior

2026-10-26. User correction to the run-off prototype: a run-off shares the scheduling/profile search with jumps, but requires additional source-block constraints. Source clearance after departure is now checked in collision-box mode through the shared finite scene collision helper; support-plane mode retains its earlier limitation. A shorter no-jump profile alone does not establish a collision-valid run-off.

## Departure constraints implemented now

Let d be the run-off departure input. Positions are pre-input states, so input d-1 produces position d.

- At state d-1 the player overlaps the source support. This establishes the incoming grounded flag, as for an ordinary jump. Use the actual standing support rather than a narrower authored landing region. For d=0 the declared initial state supplies ground/support; an initial state still overlapping the source cannot also satisfy the exit condition.
- At state d the player is outside the source support footprint. This is additional to the preceding support requirement. The input has no Jump and uses the incoming ground movement constants; later inputs use the derived air schedule.
- All four departure faces are alternatives in block-route search. The solver may turn throughout. No facing constraint or requirement to leave the front edge is added.

The compiler now emits the preceding-state support rows explicitly, as well as the existing departure exit row. Independent strict support and exit checks verify both. Added runs retime both constraints. Existing earlier-floor checks still apply.

## Source becomes an obstacle after departure

The source remains part of the world after the player leaves it. At subsequent states and movement intervals, require collision clearance whenever the player's vertical bounding box overlaps the source collision volume. This condition concerns the player's full height, not only feet height.

For a source sub-box with vertical bounds [bottom, top], and player feet y and height h, static vertical overlap is:

```text
y < top AND y + h > bottom
```

While that holds, the player's horizontal box must be outside that actual source sub-box. For expanded horizontal bounds [xmin, xmax, zmin, zmax], the player-center condition is the disjunction:

```text
x <= xmin OR x >= xmax OR z <= zmin OR z >= zmax
```

Touching and clipping must use the existing collision model's predicates and numerical margins. A single departure face must not become a permanent half-plane constraint: the player can round a corner and remain outside by another face. If the player becomes vertically clear above or below the source, its horizontal footprint no longer blocks the path. Later vertical overlap must activate collision handling again, including on later route legs. Intentional later landings or contacts need their own selected profiles.

The static predicates describe the required disjunction, not a complete collision algorithm. Endpoint clearance alone can miss movement through a block between states. The obstacle implementation must retain finite sub-boxes, detect the relevant swept intervals, and use Minecraft's Y-X-Z order and the player's vertically moved box when constructing horizontal clipping constraints. Reuse the existing collision/constraint helpers. Handle the source under the same obstacle machinery as every other block; a source-specific infinite wall is insufficient.

## Current boundary and implementation follow-up

The [obstacle and blockage design](OBSTACLES_AND_BLOCKAGES.md) extends these requirements into the joint route/contact search. All six directed edge timing classes have been checked against Minecraft's own offset methods. [Collision-box mode](OBSTACLE_SEARCH.md) now generates directed blockage constraints and checks all supplied finite solids with the integration agent's shared scene collision helper.

The legacy support-plane schema supplies horizontal footprints and top heights, but no source bottom or full collision sub-boxes. Earlier-floor exclusions only detect selected descending top-plane crossings; they do not prevent later side penetration below that plane. That mode still checks departure clearance only and exposes `runOffSourceObstacle: departure_exit_only_pending_y_obstacles`. Collision-box mode checks subsequent source collisions during actual Y overlap and reports `checked_during_y_overlap`; its five-input side-run-off fixture leaves the near edge with one added run. Do not infer a bottom from the top, or claim collision-valid run-offs from these plane-only solves.

When implementing obstacles and ceilings:

1. Carry actual collision sub-boxes, including vertical bounds, from scene geometry. Include the departure source in the obstacle set after leaving support.
2. Derive Y-overlap and swept-contact intervals from each candidate profile, then generate/branch on the required finite-face conditions. Keep face choices local to the relevant interval, with all positional rows anchored to transition-relative events so run insertion retimes them.
3. Reoptimize the full prefix, including earlier facings and run allocations, and independently verify support, departure clearance and swept obstacle consistency before acceptance.
4. Recheck the run-off examples under the full model. The saved eight-input result is a plane-model witness, not a proven minimum. Leaving a nearer side edge and turning around the source may avoid the four forward run inputs; feasibility and any minimum depend on the obstacle constraints and carried momentum.

Regression cases must cover a side exit with free turning, departure followed by source reentry while Y overlaps, corner rounding without intersection, passage underneath after the player's head clears the bottom, passage above with no vertical overlap, renewed overlap later in the route, between-state tunneling, composite source boxes and all constraint intervals retimed after added runs. Ordinary jumps must retain their existing source-support rule without acquiring a run-off departure-exit constraint.
