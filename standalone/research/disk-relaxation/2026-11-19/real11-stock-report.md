# Solve report

Source revision `c9deef426a0584ba0a476b7ea9506053ebe903f9`, command `--input /Users/arvindmurty/CS/pkc-wt/real11-ipopt-solver-study-results/disk-integration/acceptance/unrestricted.json --output /Users/arvindmurty/CS/pkc-wt/real11-ipopt-solver-study-results/disk-integration/runs/production-4375cd96-unrestricted-300s-r1-stock --seconds 300 --profile-mode bookkeeping`.

## Outcome

| Measure | Value |
|---|---|
| Best route ticks | 64 |
| Best objective | -1093.2381026602416 |
| First full solution | 4.5 s |
| Search ended | 7.6 s, `exhausted` |
| Solver requests / worker launches | 18 / 17 |
| Expanded prefixes | 37 |
| Independent replays | 10/10 passed |

The search explored every runnable alternative. A timeout or an unknown solve is not an infeasibility or optimality certificate.

## Why the search stopped

No frame was left suspended.

Solver requests without a solution:

| Request | IPOPT status | Counted as | Owning routes |
|---|---|---|---|
| 2 | Infeasible_Problem_Detected | infeasible (IPOPT local) | route 1.1 (lands B3 t26), oracle_rejected |
| 5 | Infeasible_Problem_Detected | infeasible (IPOPT local) | route 3.1 (lands B5 t56), oracle_rejected |
| 8 | Infeasible_Problem_Detected | infeasible (IPOPT local) | route 4.1 (lands B7 t66), feasible |
| 15 | Infeasible_Problem_Detected | infeasible (IPOPT local) | route 7.1 (lands B5 t54), feasible |

Routes closed because every alternative is infeasible (IPOPT local or a pre-solve proof): route 3.1 (lands B5 t56), route 1.1 (lands B3 t26).

Linear pre-checks: 132 solved (1 more from cache), 15 proved infeasible, 0 undecided by the LP solver and counted as not ruled out; 1 needed the interior-point retry.

## Route tree

Each frame extends one parent route to the next anchor. Route `F.N` is route N of frame F.

- Frame 0 → B2: 2 routes (2 feasible)
  - Frame 1 → B3: 2 routes (1 oracle_rejected, 1 feasible), from route 0.1 (lands B2 t16)
    - Frame 2 → B4: 1 route (1 feasible), from route 1.2 (lands B3 t27)
      - Frame 3 → B5: 2 routes (1 oracle_rejected, 1 feasible), from route 2.1 (lands B4 t39)
        - Frame 4 → B7: 1 route (1 feasible), from route 3.2 (lands B5 t57)
  - Frame 5 → B3: 1 route (1 feasible), from route 0.2 (lands B2 t20)
    - Frame 6 → B4: 1 route (1 feasible), from route 5.1 (lands B3 t29)
      - Frame 7 → B5: 1 route (1 feasible), from route 6.1 (lands B4 t39)
        - Frame 8 → B7: 1 route (1 feasible), from route 7.1 (lands B5 t54)

## Partial routes

How to read the sections below:

- **Movement**: the fixed part of a route, its jumps and landings. Routes sharing a movement differ only in which side of each obstacle they pass.
- **Rows** read `obstacle: axis ≥/≤ value at ticks`, on the player's centre at the start of each tick, so a block edge appears shifted by the 0.3 half-width (B7's +X face at 730 is `X ≥ 730.3`). The prefix names the obstacle whose side the row keeps; a plane shared by two boxes names both.
- **Obstacle**: a group of touching blocks the player cannot pass between, named like `B6+B7`. **Obstacle check**: every obstacle is paired with every landing segment (the ticks from one landing or jump to the next). A ground tick on a block is its own segment.
- **Phase**: one sweep of one tick. Minecraft moves the player along Y, then X, then Z each tick and checks collisions per sweep, so `56X` is the X sweep of input 56.
- **Already clear**: phases where the player's reach box cannot touch the obstacle. **Possible phases**: where it can, so a side must be chosen. **Faces ruled out**: sides the player cannot be on at that phase. **Vertical clear**: phases where the player is certainly above or below it. **Uncertain**: phases decided by a named step or grind contact the model cannot settle, left to replay.
- **Sides** (`X+`, `Z-`, a corner such as `X+Z+`, `over`, `under`) are the largest free rectangles around an obstacle at the player's height. A side never runs through a neighbouring block, and a face two flush blocks share is one side. Sides change when the player rises above or drops below one of the blocks.
- **Decision**: one thing to choose, either the sides to keep around one obstacle or which rectangle of a contacted face to touch (a face a neighbour clips or two touching blocks merge into one can have several). **Option**: one choice for one decision. **Alternative**: one surviving option per decision, combined. Each leg keeps only its best feasible alternative.
- **Family**: alternatives that differ only in when the route switches sides of an obstacle. A switch is named by the input whose sweep first uses the new side, so that side's rows start at the next tick. The climb solves one switch input, then its neighbours, and stops when neither is better; the inputs it never needed are counted as not solved. **Fallbacks**: up to 2 other alternatives within 0.3 blocks of the best (same ticks), restored only if the route fails further on.
- Request numbers index `requests.json`.

### Frame 0: reach B2 from the start

Choices the movement generators ruled out by reachability before building them: 72 (a choice is checked again for every prefix the frame extends, for a jump and a no-jump start, and by every generator that offers it, so one choice can be ruled out several times).

| Choice | Player-centre area | Ticks | Check | Times | Example |
|---|---|---|---|---|---|
| B1 landing | X [741.700, 742.300] × Z [-1105.300, -1104.300] | t0-15 | distance reachability | 19 | at tick 11 the nearest point is 4.200 blocks away, travel at most 2.646 |
| B1 lower landing | X [741.700, 742.300] × Z [-1105.300, -1104.300] | t0-15 | distance reachability | 19 | at tick 11 the nearest point is 4.200 blocks away, travel at most 2.646 |
| B2 flight case | X [740.700, 742.300] × Z [-1104.300, -1102.700] | t0-14 | distance reachability | 18 | at tick 11 the nearest point is 4.205 blocks away, travel at most 2.646 |
| B2 landing | X [740.700, 742.300] × Z [-1104.300, -1102.700] | t8-15 | distance reachability | 8 | at tick 8 the nearest point is 4.205 blocks away, travel at most 1.898 |
| B2 lower landing | X [740.700, 742.300] × Z [-1104.300, -1102.700] | t8-15 | distance reachability | 8 | at tick 8 the nearest point is 4.205 blocks away, travel at most 1.898 |

#### Movement: jumps at t8; starts on B1 t0, runs to t7; lands B2 t16; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 0.1 | feasible | 17 | -1304.565 | no obstacle in reach | no avoidance rows |

Obstacle checks: 18; 18 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

Options rejected before solving: 0.

No obstacle is in reach, so there is no avoidance choice: the movement was solved once as is.

| # | Rows added | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | no avoidance rows | feasible | accepted | request 1 feasible, IPOPT Solve_Succeeded, 0.18 s |

Solver time for this movement: 0.18 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| no obstacle sides | no switch (#1: -1304.565) | no switch (#1: -1304.565) | 0 |

Kept: alternative #1; fallbacks: none.

#### Movement: jumps at t0, t12; lands B1 t11; lands B2 t20; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 0.2 | feasible | 21 | -1303.461 | no obstacle in reach | no avoidance rows |

Obstacle checks: 4; 4 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

Options rejected before solving: 0.

No obstacle is in reach, so there is no avoidance choice: the movement was solved once as is.

| # | Rows added | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | no avoidance rows | feasible | accepted | request 11 feasible, IPOPT Solve_Succeeded, 0.14 s |

Solver time for this movement: 0.14 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| no obstacle sides | no switch (#1: -1303.461) | no switch (#1: -1303.461) | 0 |

Kept: alternative #1; fallbacks: none.

Next partial solves built from this frame: frame 1 from route 0.1 (lands B2 t16), frame 5 from route 0.2 (lands B2 t20).

### Frame 1: reach B3 from route 0.1 (lands B2 t16)

Inherited 44 constraint rows from the parent's solved route (36 contact:region, 1 initial:0:X:lo, 1 initial:0:X:hi, 1 initial:0:Z:lo, 1 initial:0:Z:hi, 1 landing:1:X:lo, 1 landing:1:X:hi, 1 landing:1:Z:lo, 1 landing:1:Z:hi); starting facings seeded from it: yes.

Choices the movement generators ruled out by reachability before building them: 10 (a choice is checked again for every prefix the frame extends, for a jump and a no-jump start, and by every generator that offers it, so one choice can be ruled out several times).

| Choice | Player-centre area | Ticks | Check | Times | Example |
|---|---|---|---|---|---|
| B3 flight case | X [737.700, 739.300] × Z [-1101.300, -1099.700] | t17-24 | distance reachability | 8 | at tick 17 the nearest point is 7.879 blocks away, travel at most 4.746 |
| B3 landing | X [737.700, 739.300] × Z [-1101.300, -1099.700] | t25 | distance reachability | 1 | at tick 25 the nearest point is 7.879 blocks away, travel at most 7.679 |
| B3 lower landing | X [737.700, 739.300] × Z [-1101.300, -1099.700] | t25 | distance reachability | 1 | at tick 25 the nearest point is 7.879 blocks away, travel at most 7.679 |

#### Movement: jumps at t8, t18; starts on B1 t0, runs to t7; lands B2 t16, runs to t17; lands B3 t26; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 1.1 | oracle_rejected | - | - | - | - |

Obstacle checks: 24; 24 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

Options rejected before solving: 0.

No obstacle is in reach, so there is no avoidance choice: the movement was solved once as is.

| # | Rows added | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | no avoidance rows | infeasible (IPOPT local) | no_candidate | request 2 infeasible (IPOPT local), IPOPT Infeasible_Problem_Detected, 0.21 s |

Solver time for this movement: 0.21 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| no obstacle sides | no switch (#1: infeasible) | none feasible | 0 |

Kept: none; fallbacks: none.

#### Movement: jumps at t8, t19; starts on B1 t0, runs to t7; lands B2 t16, runs to t18; lands B3 t27; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 1.2 | feasible | 28 | -983.818 | no obstacle in reach | no avoidance rows |

Obstacle checks: 27; 27 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

Options rejected before solving: 0.

No obstacle is in reach, so there is no avoidance choice: the movement was solved once as is.

| # | Rows added | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | no avoidance rows | feasible | accepted | request 3 feasible, IPOPT Solve_Succeeded, 0.16 s |

Solver time for this movement: 0.16 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| no obstacle sides | no switch (#1: -983.818) | no switch (#1: -983.818) | 0 |

Kept: alternative #1; fallbacks: none.

Next partial solves built from this frame: frame 2 from route 1.2 (lands B3 t27).

### Frame 2: reach B4 from route 1.2 (lands B3 t27)

Inherited 100 constraint rows from the parent's solved route (84 contact:region, 2 initial:0:X:lo, 2 initial:0:X:hi, 2 initial:0:Z:lo, 2 initial:0:Z:hi, 2 landing:1:X:lo, 2 landing:1:X:hi, 2 landing:1:Z:lo, 2 landing:1:Z:hi); starting facings seeded from it: yes.

Choices the movement generators ruled out by reachability before building them: 40 (a choice is checked again for every prefix the frame extends, for a jump and a no-jump start, and by every generator that offers it, so one choice can be ruled out several times).

| Choice | Player-centre area | Ticks | Check | Times | Example |
|---|---|---|---|---|---|
| B4 blip (joint prerequisites) | - | t40-45 | required contact reachability | 6 | empty X interval at state 40 |
| B4 corner_blip_up (joint prerequisites) | - | t39-44 | required contact reachability | 6 | X at tick 39 must lie in [733.700, 735.300], reachable [737.700, 739.300] |
| B4 corner_jump_cancel | X [733.700, 735.300] × Z [-1100.300, -1098.700] | t28-33 | distance reachability | 6 | at tick 28 the nearest point is 11.962 blocks away, travel at most 8.420 |
| B4 flight case | X [733.700, 735.300] × Z [-1100.300, -1098.700] | t28-33 | distance reachability | 6 | at tick 28 the nearest point is 11.962 blocks away, travel at most 8.420 |
| B4 landing | X [733.700, 735.300] × Z [-1100.300, -1098.700] | t37-38 | distance reachability | 2 | at tick 37 the nearest point is 11.962 blocks away, travel at most 11.708 |
| B4 lower landing | X [733.700, 735.300] × Z [-1100.300, -1098.700] | t37-38 | distance reachability | 2 | at tick 37 the nearest point is 11.962 blocks away, travel at most 11.708 |
| B4 normal_step | X [733.700, 735.300] × Z [-1100.300, -1098.700] | t28-33 | distance reachability | 6 | at tick 28 the nearest point is 11.962 blocks away, travel at most 8.420 |
| B4 slab_boost | X [733.700, 735.300] × Z [-1100.300, -1098.700] | t28-33 | distance reachability | 6 | at tick 28 the nearest point is 11.962 blocks away, travel at most 8.420 |

#### Movement: jumps at t8, t19, t30; starts on B1 t0, runs to t7; lands B2 t16, runs to t18; lands B3 t27, runs to t29; lands B4 t39; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 2.1 | feasible | 40 | -1272.413 | no obstacle in reach | no avoidance rows |

Obstacle checks: 36; 36 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

Options rejected before solving: 0.

No obstacle is in reach, so there is no avoidance choice: the movement was solved once as is.

| # | Rows added | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | no avoidance rows | feasible | accepted | request 4 feasible, IPOPT Solve_Succeeded, 0.13 s |

Solver time for this movement: 0.13 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| no obstacle sides | no switch (#1: -1272.413) | no switch (#1: -1272.413) | 0 |

Kept: alternative #1; fallbacks: none.

Next partial solves built from this frame: frame 3 from route 2.1 (lands B4 t39).

### Frame 3: reach B5 from route 2.1 (lands B4 t39)

Inherited 128 constraint rows from the parent's solved route (108 contact:region, 3 landing:1:X:lo, 3 landing:1:X:hi, 3 landing:1:Z:lo, 3 landing:1:Z:hi, 2 initial:0:X:lo, 2 initial:0:X:hi, 2 initial:0:Z:lo, 2 initial:0:Z:hi); starting facings seeded from it: yes.

Choices the movement generators ruled out by reachability before building them: 18 (a choice is checked again for every prefix the frame extends, for a jump and a no-jump start, and by every generator that offers it, so one choice can be ruled out several times).

| Choice | Player-centre area | Ticks | Check | Times | Example |
|---|---|---|---|---|---|
| B5 landing | X [729.700, 731.300] × Z [-1096.300, -1095.300] | t45-48 | distance reachability | 4 | at tick 45 the nearest point is 17.271 blocks away, travel at most 13.821 |
| B5 lower landing | X [729.700, 731.300] × Z [-1096.300, -1094.700] | t45-48 | distance reachability | 4 | at tick 45 the nearest point is 17.271 blocks away, travel at most 13.821 |
| B6 flight case | X [729.700, 731.300] × Z [-1095.300, -1093.700] | t40-43, t51-55 | distance reachability | 10 | at tick 51 the nearest point is 17.767 blocks away, travel at most 16.339 |

#### Movement: jumps at t8, t19, t30, t42; starts on B1 t0, runs to t7; lands B2 t16, runs to t18; lands B3 t27, runs to t29; lands B4 t39, runs to t41; lands B5 t56; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 3.1 | oracle_rejected | - | - | - | - |

Obstacle checks: 48; 47 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

| Obstacle | Segment ticks | Possible phases | Already clear | Faces ruled out | Vertical clear | Uncertain |
|---|---|---|---|---|---|---|
| b5+b6+b7 | 42-57 | 56X-56Z | 43 | X+ at 56X; X+Z+ at 56X-56Z; X- at 56X-56Z; Z+ at 56X-56Z | none | none |

Decisions:

- which sides of B5+B6+B7 to keep (segment t42-57)

Options rejected before solving: 1 (1 linear control relaxation).

| # | Decision | Option | Check | Detail |
|---|---|---|---|---|
| 1 | B5+B6+B7 sides t42-57 | B6/B7: Z ≤ -1095.300 at t56; B6: X ≥ 731.300 at t57 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |

Alternatives solved: 1. Each combines one surviving option per decision; the best feasible one is kept.

| # | Rows the alternative adds | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | B6/B7: Z ≤ -1095.300 at t56-57 | infeasible (IPOPT local) | no_candidate | request 5 infeasible (IPOPT local), IPOPT Infeasible_Problem_Detected, 0.25 s |

Solver time for this movement: 0.25 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| B5+B6+B7: Z- | no switch (#1: infeasible) | none feasible | 0 |

Kept: none; fallbacks: none.

#### Movement: jumps at t8, t19, t30, t43; starts on B1 t0, runs to t7; lands B2 t16, runs to t18; lands B3 t27, runs to t29; lands B4 t39, runs to t42; lands B5 t57; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 3.2 | feasible | 58 | -1155.104 | B5+B6+B7: Z- (t43-58) | B6/B7: Z ≤ -1095.300 at t56-58 |

Obstacle checks: 52; 51 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

| Obstacle | Segment ticks | Possible phases | Already clear | Faces ruled out | Vertical clear | Uncertain |
|---|---|---|---|---|---|---|
| b5+b6+b7 | 43-58 | 56X-57Z | 40 | X+ at 56X-57X; X+Z+ at 56X-57Z; X- at 56X-57Z; Z+ at 56X-57Z | none | none |

Decisions:

- which sides of B5+B6+B7 to keep (segment t43-58)

Options rejected before solving: 1 (1 linear control relaxation).

| # | Decision | Option | Check | Detail |
|---|---|---|---|---|
| 1 | B5+B6+B7 sides t43-58 | B6/B7: Z ≤ -1095.300 at t56-57; B6: X ≥ 731.300 at t58 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |

Alternatives solved: 1. Each combines one surviving option per decision; the best feasible one is kept.

| # | Rows the alternative adds | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | B6/B7: Z ≤ -1095.300 at t56-58 | feasible | accepted | request 6 feasible, IPOPT Solve_Succeeded, 0.18 s |

Solver time for this movement: 0.18 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| B5+B6+B7: Z- | no switch (#1: -1155.104) | no switch (#1: -1155.104) | 0 |

Kept: alternative #1; fallbacks: none.

Next partial solves built from this frame: frame 4 from route 3.2 (lands B5 t57).

### Frame 4: reach B7 from route 3.2 (lands B5 t57)

Inherited 163 constraint rows from the parent's solved route (136 contact:region, 4 landing:1:X:lo, 4 landing:1:X:hi, 4 landing:1:Z:lo, 4 landing:1:Z:hi, 3 avoidance, 2 initial:0:X:lo, 2 initial:0:X:hi, 2 initial:0:Z:lo, 2 initial:0:Z:hi); starting facings seeded from it: yes.

Choices the movement generators ruled out by reachability before building them: 1 (a choice is checked again for every prefix the frame extends, for a jump and a no-jump start, and by every generator that offers it, so one choice can be ruled out several times).

| Choice | Player-centre area | Ticks | Check | Times | Example |
|---|---|---|---|---|---|
| B7 flight case | X [728.700, 730.300] × Z [-1095.300, -1092.700] | t58 | distance reachability | 1 | at tick 58 the nearest point is 18.630 blocks away, travel at most 18.128 |

#### Movement: jumps at t8, t19, t30, t43, t58; starts on B1 t0, runs to t7; lands B2 t16, runs to t18; lands B3 t27, runs to t29; lands B4 t39, runs to t42; lands B5 t57; lands B7 t66; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 4.1 | feasible | 67 | -1093.823 | B5+B6+B7: Z- then X- (t58-67) | B6/B7: Z ≤ -1095.300 at t58-61; B6/B7 region: X ≤ 729.700 at t62-67; B7 region: X ≥ 728.700 at t66; B7 region: Z ≥ -1095.300 at t66; B7 region: Z ≤ -1092.700 at t66 |

Obstacle checks: 108; 106 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

| Obstacle | Segment ticks | Possible phases | Already clear | Faces ruled out | Vertical clear | Uncertain |
|---|---|---|---|---|---|---|
| b5+b6+b7 | 58-67 | 58Z-66Z | 2 | X+ at 61X-66Z; X+Z+ at 58Z-60Y; X- at 58Z-59X; Z+ at 58Z-61Z; Z- at 65Z-66Z | none | none |
| b5+b6+b7 | 58-67 | 58Z-66Z | 2 | X+ at 62X-66Z; X+Z+ at 58Z-60Y; X- at 58Z-59X, 65X-66X; Z+ at 58Z-61Z; Z- at 61Z-66Z | none | none |

Decisions:

- which sides of B5+B6+B7 to keep (segment t58-67)
- which of B7's 2 face rectangles to touch at t66: X [728.700, 729.700] × Z [-1095.300, -1092.700] or X [729.700, 730.300] × Z [-1093.700, -1092.700]

Options rejected before solving: 46 (34 interval reachability, 6 linear control relaxation, 6 travel-distance reachability).

| # | Decision | Option | Check | Detail |
|---|---|---|---|---|
| 1 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6/B7 region: X ≤ 729.700 at t62-66; B6/B7 region: Z ≥ -1093.700 at t66-67 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |
| 2 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6/B7 region: X ≤ 729.700 at t62-65; B6/B7 region: Z ≥ -1093.700 at t65-67 | interval reachability | empty Z interval at state 65 |
| 3 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6/B7 region: X ≤ 729.700 at t62-64; B6/B7 region: Z ≥ -1093.700 at t64-67 | interval reachability | empty Z interval at state 64 |
| 4 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6/B7 region: X ≤ 729.700 at t62-63; B6/B7 region: Z ≥ -1093.700 at t63-67 | interval reachability | empty Z interval at state 63 |
| 5 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6/B7 region: X ≤ 729.700 at t62; B6/B7 region: Z ≥ -1093.700 at t62-67 | interval reachability | empty Z interval at state 62 |
| 6 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6/B7 region: X ≤ 729.700 at t61-66; B6/B7 region: Z ≥ -1093.700 at t66-67 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |
| 7 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6/B7 region: X ≤ 729.700 at t61-65; B6/B7 region: Z ≥ -1093.700 at t65-67 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |
| 8 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6/B7 region: X ≤ 729.700 at t61-64; B6/B7 region: Z ≥ -1093.700 at t64-67 | interval reachability | empty Z interval at state 64 |
| 9 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6/B7 region: X ≤ 729.700 at t61-63; B6/B7 region: Z ≥ -1093.700 at t63-67 | interval reachability | empty Z interval at state 63 |
| 10 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6/B7 region: X ≤ 729.700 at t61-62; B6/B7 region: Z ≥ -1093.700 at t62-67 | interval reachability | empty Z interval at state 62 |
| 11 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6/B7 region: X ≤ 729.700 at t61-62; B6/B7 region: Z ≥ -1093.700 at t62-67 | interval reachability | empty Z interval at state 62 |
| 12 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6/B7 region: X ≤ 729.700 at t61-67 | travel-distance reachability | state 51 unreachable from state 0 (gap 16.142 > travel 16.074) |
| 13 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6/B7 region: X ≤ 729.700 at t61-66; B6/B7 region: Z ≥ -1093.700 at t66-67 | travel-distance reachability | state 51 unreachable from state 0 (gap 16.142 > travel 16.074) |
| 14 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6/B7 region: X ≤ 729.700 at t61-65; B6/B7 region: Z ≥ -1093.700 at t65-67 | travel-distance reachability | state 51 unreachable from state 0 (gap 16.142 > travel 16.074) |
| 15 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6/B7 region: X ≤ 729.700 at t61-64; B6/B7 region: Z ≥ -1093.700 at t64-67 | travel-distance reachability | state 51 unreachable from state 0 (gap 16.148 > travel 16.074) |
| 16 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6/B7 region: X ≤ 729.700 at t61-63; B6/B7 region: Z ≥ -1093.700 at t63-67 | interval reachability | empty Z interval at state 63 |
| 17 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-62; B6/B7 region: X ≤ 729.700 at t63-66; B6/B7 region: Z ≥ -1093.700 at t66-67 | interval reachability | empty Z interval at state 66 |
| 18 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-62; B6/B7 region: X ≤ 729.700 at t63-65; B6/B7 region: Z ≥ -1093.700 at t65-67 | interval reachability | empty Z interval at state 65 |
| 19 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-62; B6/B7 region: X ≤ 729.700 at t63-64; B6/B7 region: Z ≥ -1093.700 at t64-67 | interval reachability | empty Z interval at state 64 |
| 20 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-62; B6/B7 region: X ≤ 729.700 at t63; B6/B7 region: Z ≥ -1093.700 at t63-67 | interval reachability | empty Z interval at state 63 |
| 21 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-63; B6/B7 region: X ≤ 729.700 at t64-66; B6/B7 region: Z ≥ -1093.700 at t66-67 | interval reachability | empty Z interval at state 66 |
| 22 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-63; B6/B7 region: X ≤ 729.700 at t64-65; B6/B7 region: Z ≥ -1093.700 at t65-67 | interval reachability | empty Z interval at state 65 |
| 23 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-63; B6/B7 region: X ≤ 729.700 at t64; B6/B7 region: Z ≥ -1093.700 at t64-67 | interval reachability | empty Z interval at state 64 |
| 24 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-64; B6/B7 region: X ≤ 729.700 at t65-66; B6/B7 region: Z ≥ -1093.700 at t66-67 | interval reachability | empty Z interval at state 66 |
| 25 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-64; B6/B7 region: X ≤ 729.700 at t65; B6/B7 region: Z ≥ -1093.700 at t65-67 | interval reachability | empty Z interval at state 65 |
| 26 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-65; B6/B7 region: X ≤ 729.700 at t66; B6/B7 region: Z ≥ -1093.700 at t66-67 | interval reachability | empty Z interval at state 66 |
| 27 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6/B7 region: X ≤ 729.700 at t62; B6/B7 region: Z ≥ -1093.700 at t62-67 | interval reachability | empty Z interval at state 62 |
| 28 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6/B7 region: X ≤ 729.700 at t62-65; B6/B7 region: Z ≥ -1093.700 at t65-67 | interval reachability | empty Z interval at state 65 |
| 29 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6/B7 region: X ≤ 729.700 at t62-64; B6/B7 region: Z ≥ -1093.700 at t64-67 | interval reachability | empty Z interval at state 64 |
| 30 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6/B7 region: X ≤ 729.700 at t62-63; B6/B7 region: Z ≥ -1093.700 at t63-67 | interval reachability | empty Z interval at state 63 |
| 31 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≥ 731.300 at t62; B6/B7 region: Z ≥ -1093.700 at t62-66; B6/B7 region: X ≤ 729.700 at t67 | interval reachability | empty Z interval at state 62 |
| 32 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≥ 731.300 at t62; B6/B7 region: Z ≥ -1093.700 at t62-67 | interval reachability | empty Z interval at state 62 |
| 33 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6/B7 region: X ≤ 729.700 at t61-65; B6/B7 region: Z ≥ -1093.700 at t65-67 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |
| 34 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6/B7 region: X ≤ 729.700 at t61-64; B6/B7 region: Z ≥ -1093.700 at t64-67 | interval reachability | empty Z interval at state 64 |
| 35 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6/B7 region: X ≤ 729.700 at t61-63; B6/B7 region: Z ≥ -1093.700 at t63-67 | interval reachability | empty Z interval at state 63 |
| 36 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6/B7 region: X ≤ 729.700 at t61-62; B6/B7 region: Z ≥ -1093.700 at t62-67 | interval reachability | empty Z interval at state 62 |
| 37 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≥ 731.300 at t61-62; B6/B7 region: Z ≥ -1093.700 at t62-66; B6/B7 region: X ≤ 729.700 at t67 | interval reachability | empty Z interval at state 62 |
| 38 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≥ 731.300 at t61-62; B6/B7 region: Z ≥ -1093.700 at t62-67 | interval reachability | empty Z interval at state 62 |
| 39 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6/B7 region: X ≤ 729.700 at t61-65; B6/B7 region: Z ≥ -1093.700 at t65-67 | travel-distance reachability | state 51 unreachable from state 0 (gap 16.142 > travel 16.074) |
| 40 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6/B7 region: X ≤ 729.700 at t61-64; B6/B7 region: Z ≥ -1093.700 at t64-67 | travel-distance reachability | state 51 unreachable from state 0 (gap 16.148 > travel 16.074) |
| 41 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6/B7 region: X ≤ 729.700 at t61-63; B6/B7 region: Z ≥ -1093.700 at t63-67 | interval reachability | empty Z interval at state 63 |
| 42 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6/B7 region: X ≤ 729.700 at t61-62; B6/B7 region: Z ≥ -1093.700 at t62-67 | interval reachability | empty Z interval at state 62 |
| 43 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B6: X ≥ 731.300 at t60-62; B6/B7 region: Z ≥ -1093.700 at t62-66; B6/B7 region: X ≤ 729.700 at t67 | interval reachability | empty Z interval at state 62 |
| 44 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B6: X ≥ 731.300 at t60-62; B6/B7 region: Z ≥ -1093.700 at t62-67 | interval reachability | empty Z interval at state 62 |
| 45 | B5+B6+B7 sides t58-67 | B6: X ≥ 731.300 at t59-62; B6/B7 region: Z ≥ -1093.700 at t62-66; B6/B7 region: X ≤ 729.700 at t67 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |
| 46 | B5+B6+B7 sides t58-67 | B6: X ≥ 731.300 at t59-62; B6/B7 region: Z ≥ -1093.700 at t62-67 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |

Options dropped without solving: 7 (7 duplicate).

| # | Decision | Reason | Option |
|---|---|---|---|
| 1 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-61; B6/B7 region: X ≤ 729.700 at t62-67 |
| 2 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-60; B6/B7 region: X ≤ 729.700 at t61-67 |
| 3 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6/B7 region: X ≤ 729.700 at t61-67 |
| 4 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-62; B6/B7 region: X ≤ 729.700 at t63-67 |
| 5 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-63; B6/B7 region: X ≤ 729.700 at t64-67 |
| 6 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-64; B6/B7 region: X ≤ 729.700 at t65-67 |
| 7 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-65; B6/B7 region: X ≤ 729.700 at t66-67 |

Alternatives solved: 3. Each combines one surviving option per decision; the best feasible one is kept.

| # | Rows the alternative adds | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | B6/B7: Z ≤ -1095.300 at t58-61; B6/B7 region: X ≤ 729.700 at t62-67; B7 region: X ≥ 728.700 at t66; B7 region: Z ≥ -1095.300 at t66; B7 region: Z ≤ -1092.700 at t66 | feasible | accepted | request 7 feasible, IPOPT Solve_Succeeded, 0.21 s |
| 2 | B6/B7: Z ≤ -1095.300 at t58-60; B6/B7 region: X ≤ 729.700 at t61-67; B7 region: X ≥ 728.700 at t66; B7 region: Z ≥ -1095.300 at t66; B7 region: Z ≤ -1092.700 at t66 | infeasible (IPOPT local) | no_candidate | request 8 infeasible (IPOPT local), IPOPT Infeasible_Problem_Detected, 0.37 s |
| 3 | B6/B7: Z ≤ -1095.300 at t58-62; B6/B7 region: X ≤ 729.700 at t63-67; B7 region: X ≥ 728.700 at t66; B7 region: Z ≥ -1095.300 at t66; B7 region: Z ≤ -1092.700 at t66 | feasible | accepted | request 9 feasible, IPOPT Solve_Succeeded, 0.18 s |

Solver time for this movement: 0.75 s over 3 requests.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| B5+B6+B7: Z- then X- | input 61 (#1: -1093.823); input 60 (#2: infeasible); input 62 (#3: -1094.162) | input 61 (#1: -1093.823) | 3 |

Kept: alternative #1 (the last leg keeps no fallbacks).

### Frame 5: reach B3 from route 0.2 (lands B2 t20)

Inherited 24 constraint rows from the parent's solved route (12 contact:region, 2 initial:0:X:lo, 2 initial:0:X:hi, 2 initial:0:Z:lo, 2 initial:0:Z:hi, 1 landing:1:X:lo, 1 landing:1:X:hi, 1 landing:1:Z:lo, 1 landing:1:Z:hi); starting facings seeded from it: yes.

Choices the movement generators ruled out by reachability before building them: 1 (a choice is checked again for every prefix the frame extends, for a jump and a no-jump start, and by every generator that offers it, so one choice can be ruled out several times).

| Choice | Player-centre area | Ticks | Check | Times | Example |
|---|---|---|---|---|---|
| B3 flight case | X [737.700, 739.300] × Z [-1101.300, -1099.700] | t21 | distance reachability | 1 | at tick 21 the nearest point is 7.879 blocks away, travel at most 6.027 |

#### Movement: jumps at t0, t12, t21; lands B1 t11; lands B2 t20; lands B3 t29; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 5.1 | feasible | 30 | -982.821 | no obstacle in reach | no avoidance rows |

Obstacle checks: 7; 7 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

Options rejected before solving: 0.

No obstacle is in reach, so there is no avoidance choice: the movement was solved once as is.

| # | Rows added | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | no avoidance rows | feasible | accepted | request 12 feasible, IPOPT Solve_Succeeded, 0.15 s |

Solver time for this movement: 0.15 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| no obstacle sides | no switch (#1: -982.821) | no switch (#1: -982.821) | 0 |

Kept: alternative #1; fallbacks: none.

Next partial solves built from this frame: frame 6 from route 5.1 (lands B3 t29).

### Frame 6: reach B4 from route 5.1 (lands B3 t29)

Inherited 36 constraint rows from the parent's solved route (20 contact:region, 2 initial:0:X:lo, 2 initial:0:X:hi, 2 initial:0:Z:lo, 2 initial:0:Z:hi, 2 landing:1:X:lo, 2 landing:1:X:hi, 2 landing:1:Z:lo, 2 landing:1:Z:hi); starting facings seeded from it: yes.

Choices the movement generators ruled out by reachability before building them: 6 (a choice is checked again for every prefix the frame extends, for a jump and a no-jump start, and by every generator that offers it, so one choice can be ruled out several times).

| Choice | Player-centre area | Ticks | Check | Times | Example |
|---|---|---|---|---|---|
| B4 blip (joint prerequisites) | - | t42 | required contact reachability | 1 | empty X interval at state 42 |
| B4 corner_blip_up (joint prerequisites) | - | t41 | required contact reachability | 1 | X at tick 41 must lie in [733.700, 735.300], reachable [737.700, 739.300] |
| B4 corner_jump_cancel | X [733.700, 735.300] × Z [-1100.300, -1098.700] | t30 | distance reachability | 1 | at tick 30 the nearest point is 11.962 blocks away, travel at most 9.364 |
| B4 flight case | X [733.700, 735.300] × Z [-1100.300, -1098.700] | t30 | distance reachability | 1 | at tick 30 the nearest point is 11.962 blocks away, travel at most 9.364 |
| B4 normal_step | X [733.700, 735.300] × Z [-1100.300, -1098.700] | t30 | distance reachability | 1 | at tick 30 the nearest point is 11.962 blocks away, travel at most 9.364 |
| B4 slab_boost | X [733.700, 735.300] × Z [-1100.300, -1098.700] | t30 | distance reachability | 1 | at tick 30 the nearest point is 11.962 blocks away, travel at most 9.364 |

#### Movement: jumps at t0, t12, t21, t30; lands B1 t11; lands B2 t20; lands B3 t29; lands B4 t39; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 6.1 | feasible | 40 | -1271.548 | no obstacle in reach | no avoidance rows |

Obstacle checks: 10; 10 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

Options rejected before solving: 0.

No obstacle is in reach, so there is no avoidance choice: the movement was solved once as is.

| # | Rows added | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | no avoidance rows | feasible | accepted | request 13 feasible, IPOPT Solve_Succeeded, 0.16 s |

Solver time for this movement: 0.16 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| no obstacle sides | no switch (#1: -1271.548) | no switch (#1: -1271.548) | 0 |

Kept: alternative #1; fallbacks: none.

Next partial solves built from this frame: frame 7 from route 6.1 (lands B4 t39).

### Frame 7: reach B5 from route 6.1 (lands B4 t39)

Inherited 48 constraint rows from the parent's solved route (28 contact:region, 3 landing:1:X:lo, 3 landing:1:X:hi, 3 landing:1:Z:lo, 3 landing:1:Z:hi, 2 initial:0:X:lo, 2 initial:0:X:hi, 2 initial:0:Z:lo, 2 initial:0:Z:hi); starting facings seeded from it: yes.

Choices the movement generators ruled out by reachability before building them: 4 (a choice is checked again for every prefix the frame extends, for a jump and a no-jump start, and by every generator that offers it, so one choice can be ruled out several times).

| Choice | Player-centre area | Ticks | Check | Times | Example |
|---|---|---|---|---|---|
| B5 landing | X [729.700, 731.300] × Z [-1096.300, -1095.300] | t45 | distance reachability | 1 | at tick 45 the nearest point is 17.271 blocks away, travel at most 14.556 |
| B5 lower landing | X [729.700, 731.300] × Z [-1096.300, -1094.700] | t45 | distance reachability | 1 | at tick 45 the nearest point is 17.271 blocks away, travel at most 14.556 |
| B6 flight case | X [729.700, 731.300] × Z [-1095.300, -1093.700] | t40, t51 | distance reachability | 2 | at tick 51 the nearest point is 17.767 blocks away, travel at most 17.118 |

#### Movement: jumps at t0, t12, t21, t30, t40; lands B1 t11; lands B2 t20; lands B3 t29; lands B4 t39; lands B5 t54; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 7.1 | feasible | 55 | -1154.498 | B5+B6+B7: Z- (t40-55) | B6/B7: Z ≤ -1095.300 at t53-55 |

Obstacle checks: 14; 13 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

| Obstacle | Segment ticks | Possible phases | Already clear | Faces ruled out | Vertical clear | Uncertain |
|---|---|---|---|---|---|---|
| b5+b6+b7 | 40-55 | 53X-54Z | 40 | X+ at 53X-54X; X+Z+ at 53X-54Z; X- at 53X-54Z; Z+ at 53X-54Z | none | none |

Decisions:

- which sides of B5+B6+B7 to keep (segment t40-55)

Options rejected before solving: 0.

Alternatives solved: 2. Each combines one surviving option per decision; the best feasible one is kept.

| # | Rows the alternative adds | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | B6/B7: Z ≤ -1095.300 at t53-55 | feasible | accepted | request 14 feasible, IPOPT Solve_Succeeded, 0.18 s |
| 2 | B6/B7: Z ≤ -1095.300 at t53-54; B6: X ≥ 731.300 at t55 | infeasible (IPOPT local) | no_candidate | request 15 infeasible (IPOPT local), IPOPT Infeasible_Problem_Detected, 0.25 s |

Solver time for this movement: 0.43 s over 2 requests.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| B5+B6+B7: Z- | no switch (#1: -1154.498) | no switch (#1: -1154.498) | 0 |
| B5+B6+B7: Z- then X+ | input 54 (#2: infeasible) | none feasible | 0 |

Kept: alternative #1; fallbacks: none.

Next partial solves built from this frame: frame 8 from route 7.1 (lands B5 t54).

### Frame 8: reach B7 from route 7.1 (lands B5 t54)

Inherited 63 constraint rows from the parent's solved route (36 contact:region, 4 landing:1:X:lo, 4 landing:1:X:hi, 4 landing:1:Z:lo, 4 landing:1:Z:hi, 3 avoidance, 2 initial:0:X:lo, 2 initial:0:X:hi, 2 initial:0:Z:lo, 2 initial:0:Z:hi); starting facings seeded from it: yes.

#### Movement: jumps at t0, t12, t21, t30, t40, t55; lands B1 t11; lands B2 t20; lands B3 t29; lands B4 t39; lands B5 t54; lands B7 t63; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 8.1 | feasible | 64 | -1093.238 | B5+B6+B7: Z- then X- then Z+ (t55-64) | B6/B7: Z ≤ -1095.300 at t55-57; B6/B7 region: X ≤ 729.700 at t58-63; B7 region: X ≥ 728.700 at t63; B6/B7 region: Z ≥ -1093.700 at t63-64; B7 region: Z ≤ -1092.700 at t63 |

Obstacle checks: 32; 30 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

| Obstacle | Segment ticks | Possible phases | Already clear | Faces ruled out | Vertical clear | Uncertain |
|---|---|---|---|---|---|---|
| b5+b6+b7 | 55-64 | 55Z-63Z | 2 | X+ at 58X-63Z; X+Z+ at 55Z-57Y; X- at 55Z-56X; Z+ at 55Z-58Z; Z- at 62Z-63Z | none | none |
| b5+b6+b7 | 55-64 | 55Z-63Z | 2 | X+ at 59X-63Z; X+Z+ at 55Z-57Y; X- at 55Z-56X, 62X-63X; Z+ at 55Z-58Z; Z- at 58Z-63Z | none | none |

Decisions:

- which sides of B5+B6+B7 to keep (segment t55-64)
- which of B7's 2 face rectangles to touch at t63: X [728.700, 729.700] × Z [-1095.300, -1092.700] or X [729.700, 730.300] × Z [-1093.700, -1092.700]

Options rejected before solving: 45 (34 interval reachability, 6 travel-distance reachability, 5 linear control relaxation).

| # | Decision | Option | Check | Detail |
|---|---|---|---|---|
| 1 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6/B7 region: X ≤ 729.700 at t58-62; B6/B7 region: Z ≥ -1093.700 at t62-64 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |
| 2 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6/B7 region: X ≤ 729.700 at t58-61; B6/B7 region: Z ≥ -1093.700 at t61-64 | interval reachability | empty Z interval at state 61 |
| 3 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6/B7 region: X ≤ 729.700 at t58-60; B6/B7 region: Z ≥ -1093.700 at t60-64 | interval reachability | empty Z interval at state 60 |
| 4 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6/B7 region: X ≤ 729.700 at t58-59; B6/B7 region: Z ≥ -1093.700 at t59-64 | interval reachability | empty Z interval at state 59 |
| 5 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6/B7 region: X ≤ 729.700 at t58-64 | travel-distance reachability | state 51 unreachable from state 0 (gap 17.174 > travel 17.118) |
| 6 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6/B7 region: X ≤ 729.700 at t58-63; B6/B7 region: Z ≥ -1093.700 at t63-64 | travel-distance reachability | state 51 unreachable from state 0 (gap 17.174 > travel 17.118) |
| 7 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6/B7 region: X ≤ 729.700 at t58-62; B6/B7 region: Z ≥ -1093.700 at t62-64 | travel-distance reachability | state 51 unreachable from state 0 (gap 17.174 > travel 17.118) |
| 8 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6/B7 region: X ≤ 729.700 at t58-61; B6/B7 region: Z ≥ -1093.700 at t61-64 | travel-distance reachability | state 51 unreachable from state 0 (gap 17.174 > travel 17.118) |
| 9 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6/B7 region: X ≤ 729.700 at t58-60; B6/B7 region: Z ≥ -1093.700 at t60-64 | interval reachability | empty Z interval at state 60 |
| 10 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6/B7 region: X ≤ 729.700 at t58-59; B6/B7 region: Z ≥ -1093.700 at t59-64 | interval reachability | empty Z interval at state 59 |
| 11 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6/B7 region: X ≤ 729.700 at t59-63; B6/B7 region: Z ≥ -1093.700 at t63-64 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |
| 12 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6/B7 region: X ≤ 729.700 at t59-62; B6/B7 region: Z ≥ -1093.700 at t62-64 | interval reachability | empty Z interval at state 62 |
| 13 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6/B7 region: X ≤ 729.700 at t59-61; B6/B7 region: Z ≥ -1093.700 at t61-64 | interval reachability | empty Z interval at state 61 |
| 14 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6/B7 region: X ≤ 729.700 at t59-60; B6/B7 region: Z ≥ -1093.700 at t60-64 | interval reachability | empty Z interval at state 60 |
| 15 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6/B7 region: X ≤ 729.700 at t59; B6/B7 region: Z ≥ -1093.700 at t59-64 | interval reachability | empty Z interval at state 59 |
| 16 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-59; B6/B7 region: X ≤ 729.700 at t60-63; B6/B7 region: Z ≥ -1093.700 at t63-64 | interval reachability | empty Z interval at state 63 |
| 17 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-59; B6/B7 region: X ≤ 729.700 at t60-62; B6/B7 region: Z ≥ -1093.700 at t62-64 | interval reachability | empty Z interval at state 62 |
| 18 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-59; B6/B7 region: X ≤ 729.700 at t60-61; B6/B7 region: Z ≥ -1093.700 at t61-64 | interval reachability | empty Z interval at state 61 |
| 19 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-59; B6/B7 region: X ≤ 729.700 at t60; B6/B7 region: Z ≥ -1093.700 at t60-64 | interval reachability | empty Z interval at state 60 |
| 20 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-60; B6/B7 region: X ≤ 729.700 at t61-63; B6/B7 region: Z ≥ -1093.700 at t63-64 | interval reachability | empty Z interval at state 63 |
| 21 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-60; B6/B7 region: X ≤ 729.700 at t61-62; B6/B7 region: Z ≥ -1093.700 at t62-64 | interval reachability | empty Z interval at state 62 |
| 22 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-60; B6/B7 region: X ≤ 729.700 at t61; B6/B7 region: Z ≥ -1093.700 at t61-64 | interval reachability | empty Z interval at state 61 |
| 23 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-61; B6/B7 region: X ≤ 729.700 at t62-63; B6/B7 region: Z ≥ -1093.700 at t63-64 | interval reachability | empty Z interval at state 63 |
| 24 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-61; B6/B7 region: X ≤ 729.700 at t62; B6/B7 region: Z ≥ -1093.700 at t62-64 | interval reachability | empty Z interval at state 62 |
| 25 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-62; B6/B7 region: X ≤ 729.700 at t63; B6/B7 region: Z ≥ -1093.700 at t63-64 | interval reachability | empty Z interval at state 63 |
| 26 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6/B7 region: X ≤ 729.700 at t58-62; B6/B7 region: Z ≥ -1093.700 at t62-64 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |
| 27 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6/B7 region: X ≤ 729.700 at t58-61; B6/B7 region: Z ≥ -1093.700 at t61-64 | interval reachability | empty Z interval at state 61 |
| 28 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6/B7 region: X ≤ 729.700 at t58-60; B6/B7 region: Z ≥ -1093.700 at t60-64 | interval reachability | empty Z interval at state 60 |
| 29 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6/B7 region: X ≤ 729.700 at t58-59; B6/B7 region: Z ≥ -1093.700 at t59-64 | interval reachability | empty Z interval at state 59 |
| 30 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≥ 731.300 at t58-59; B6/B7 region: Z ≥ -1093.700 at t59-64 | interval reachability | empty Z interval at state 59 |
| 31 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≥ 731.300 at t58-59; B6/B7 region: Z ≥ -1093.700 at t59-63; B6/B7 region: X ≤ 729.700 at t64 | interval reachability | empty Z interval at state 59 |
| 32 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6/B7 region: X ≤ 729.700 at t58-62; B6/B7 region: Z ≥ -1093.700 at t62-64 | travel-distance reachability | state 51 unreachable from state 0 (gap 17.174 > travel 17.118) |
| 33 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6/B7 region: X ≤ 729.700 at t58-61; B6/B7 region: Z ≥ -1093.700 at t61-64 | travel-distance reachability | state 51 unreachable from state 0 (gap 17.174 > travel 17.118) |
| 34 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6/B7 region: X ≤ 729.700 at t58-60; B6/B7 region: Z ≥ -1093.700 at t60-64 | interval reachability | empty Z interval at state 60 |
| 35 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6/B7 region: X ≤ 729.700 at t58-59; B6/B7 region: Z ≥ -1093.700 at t59-64 | interval reachability | empty Z interval at state 59 |
| 36 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B6: X ≥ 731.300 at t57-59; B6/B7 region: Z ≥ -1093.700 at t59-64 | interval reachability | empty Z interval at state 59 |
| 37 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B6: X ≥ 731.300 at t57-59; B6/B7 region: Z ≥ -1093.700 at t59-63; B6/B7 region: X ≤ 729.700 at t64 | interval reachability | empty Z interval at state 59 |
| 38 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6/B7 region: X ≤ 729.700 at t59-62; B6/B7 region: Z ≥ -1093.700 at t62-64 | interval reachability | empty Z interval at state 62 |
| 39 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6/B7 region: X ≤ 729.700 at t59-61; B6/B7 region: Z ≥ -1093.700 at t61-64 | interval reachability | empty Z interval at state 61 |
| 40 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6/B7 region: X ≤ 729.700 at t59-60; B6/B7 region: Z ≥ -1093.700 at t60-64 | interval reachability | empty Z interval at state 60 |
| 41 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6/B7 region: X ≤ 729.700 at t59; B6/B7 region: Z ≥ -1093.700 at t59-64 | interval reachability | empty Z interval at state 59 |
| 42 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≥ 731.300 at t59; B6/B7 region: Z ≥ -1093.700 at t59-64 | interval reachability | empty Z interval at state 59 |
| 43 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≥ 731.300 at t59; B6/B7 region: Z ≥ -1093.700 at t59-63; B6/B7 region: X ≤ 729.700 at t64 | interval reachability | empty Z interval at state 59 |
| 44 | B5+B6+B7 sides t55-64 | B6: X ≥ 731.300 at t56-59; B6/B7 region: Z ≥ -1093.700 at t59-64 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |
| 45 | B5+B6+B7 sides t55-64 | B6: X ≥ 731.300 at t56-59; B6/B7 region: Z ≥ -1093.700 at t59-63; B6/B7 region: X ≤ 729.700 at t64 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |

Options dropped without solving: 7 (7 duplicate).

| # | Decision | Reason | Option |
|---|---|---|---|
| 1 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-57; B6/B7 region: X ≤ 729.700 at t58-64 |
| 2 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6/B7 region: X ≤ 729.700 at t58-64 |
| 3 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-58; B6/B7 region: X ≤ 729.700 at t59-64 |
| 4 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-59; B6/B7 region: X ≤ 729.700 at t60-64 |
| 5 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-60; B6/B7 region: X ≤ 729.700 at t61-64 |
| 6 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-61; B6/B7 region: X ≤ 729.700 at t62-64 |
| 7 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-62; B6/B7 region: X ≤ 729.700 at t63-64 |

Alternatives solved: 3. Each combines one surviving option per decision; the best feasible one is kept.

| # | Rows the alternative adds | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | B6/B7: Z ≤ -1095.300 at t55-57; B6/B7 region: X ≤ 729.700 at t58-64; B7 region: X ≥ 728.700 at t63; B7 region: Z ≥ -1095.300 at t63; B7 region: Z ≤ -1092.700 at t63 | feasible | accepted | request 16 feasible, IPOPT Solve_Succeeded, 0.19 s |
| 2 | B6/B7: Z ≤ -1095.300 at t55-57; B6/B7 region: X ≤ 729.700 at t58-63; B7 region: X ≥ 728.700 at t63; B6/B7 region: Z ≥ -1093.700 at t63-64; B7 region: Z ≤ -1092.700 at t63 | feasible | accepted | request 17 feasible, IPOPT Solve_Succeeded, 0.20 s |
| 3 | B6/B7: Z ≤ -1095.300 at t55-58; B6/B7 region: X ≤ 729.700 at t59-64; B7 region: X ≥ 728.700 at t63; B7 region: Z ≥ -1095.300 at t63; B7 region: Z ≤ -1092.700 at t63 | feasible | accepted | request 18 feasible, IPOPT Solve_Succeeded, 0.18 s |

Solver time for this movement: 0.57 s over 3 requests.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| B5+B6+B7: Z- then X- | input 57 (#1: -1093.238); input 58 (#3: -1093.725) | input 57 (#1: -1093.238) | 4 |
| B5+B6+B7: Z- then X- then Z+ | inputs 57, 63 (#2: -1093.238) | inputs 57, 63 (#2: -1093.238) | 0 |

Kept: alternative #2 (the last leg keeps no fallbacks).

