# Solve report

Source revision `6b964cb07ccc5d74d77f03db7a8239aed681da2c`, command `--input cases/real11/physical-anchors.json --output runs/step4-switch-climb --seconds 300 --profile-mode bookkeeping`.

## Outcome

| Measure | Value |
|---|---|
| Best route ticks | 64 |
| Best objective | -1093.2381026602416 |
| First full solution | 6.1 s |
| Search ended | 10.0 s, `numerical_unknown` |
| Solver requests / worker launches | 18 / 17 |
| Expanded prefixes | 37 |
| Independent replays | 10/10 passed |

The search ran out of runnable work; the only work left is retrying solves that came back unknown. A timeout or an unknown solve is not an infeasibility or optimality certificate.

## Why the search stopped

Frames left suspended (not fully explored):

| Frame | Goal | Routes | Unresolved alternatives | Pending alternatives | Deferred nodes |
|---|---|---|---|---|---|
| 4 | b7 | 1 | 1 | 0 | 0 |

Solver requests that came back `unknown`:

| Request | IPOPT status | Owning routes |
|---|---|---|
| 2 | Infeasible_Problem_Detected | route 1.1 (lands B3 t26), oracle_rejected |
| 5 | Infeasible_Problem_Detected | route 3.1 (lands B5 t56), oracle_rejected |
| 7 | Infeasible_Problem_Detected | route 4.1 (lands B7 t66), feasible |
| 15 | Infeasible_Problem_Detected | route 7.1 (lands B5 t54), feasible |

Routes closed as oracle-rejected (IPOPT reported every alternative infeasible): route 3.1 (lands B5 t56), route 1.1 (lands B3 t26).

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
- **Decision**: one thing to choose, either the sides to keep around one obstacle or which landing-cover rectangle of the goal block to land in. **Option**: one choice for one decision. **Alternative**: one surviving option per decision, combined. Each leg keeps only its best feasible alternative.
- **Family**: alternatives that differ only in when the route switches sides of an obstacle. A switch is named by the input whose sweep first uses the new side, so that side's rows start at the next tick. The climb solves one switch input, then its neighbours, and stops when neither is better; the inputs it never needed are counted as not solved. **Fallbacks**: up to 2 other alternatives within 0.3 blocks of the best (same ticks), restored only if the route fails further on.
- Request numbers index `requests.json`.

### Frame 0: reach B2 from the start

#### Movement: jumps at t8; starts on B1 t0, runs to t7; lands B2 t16; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 0.1 | feasible | 17 | -1304.565 | no obstacle in reach | no avoidance rows |

Obstacle checks: 36; 36 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

Options rejected before solving: 0.

No obstacle is in reach, so there is no avoidance choice: the movement was solved once as is.

| # | Rows added | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | no avoidance rows | feasible | accepted | request 1 feasible, IPOPT Solve_Succeeded, 0.23 s |

Solver time for this movement: 0.23 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| no obstacle sides | no switch (#1: -1304.565) | no switch (#1: -1304.565) | 0 |

Kept: alternative #1; fallbacks: none.

#### Movement: jumps at t0, t12; lands B1 t11; lands B2 t20; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 0.2 | feasible | 21 | -1303.461 | no obstacle in reach | no avoidance rows |

Obstacle checks: 8; 8 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

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

Inherited 80 constraint rows from the parent's solved route (36 normal:landing, 18 contact:finite_lo, 18 contact:finite_hi, 1 initial:0:X:lo, 1 initial:0:X:hi, 1 initial:0:Z:lo, 1 initial:0:Z:hi, 1 landing:1:X:lo, 1 landing:1:X:hi, 1 landing:1:Z:lo, 1 landing:1:Z:hi); starting facings seeded from it: yes.

#### Movement: jumps at t8, t18; starts on B1 t0, runs to t7; lands B2 t16, runs to t17; lands B3 t26; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 1.1 | oracle_rejected | - | - | - | - |

Obstacle checks: 44; 44 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

Options rejected before solving: 0.

No obstacle is in reach, so there is no avoidance choice: the movement was solved once as is.

| # | Rows added | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | no avoidance rows | unknown | no_candidate | request 2 unknown, IPOPT Infeasible_Problem_Detected, 0.22 s |

Solver time for this movement: 0.22 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| no obstacle sides | no switch (#1: infeasible) | none feasible | 0 |

Kept: none; fallbacks: none.

#### Movement: jumps at t8, t19; starts on B1 t0, runs to t7; lands B2 t16, runs to t18; lands B3 t27; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 1.2 | feasible | 28 | -983.818 | no obstacle in reach | no avoidance rows |

Obstacle checks: 48; 48 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

Options rejected before solving: 0.

No obstacle is in reach, so there is no avoidance choice: the movement was solved once as is.

| # | Rows added | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | no avoidance rows | feasible | accepted | request 3 feasible, IPOPT Solve_Succeeded, 0.17 s |

Solver time for this movement: 0.17 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| no obstacle sides | no switch (#1: -983.818) | no switch (#1: -983.818) | 0 |

Kept: alternative #1; fallbacks: none.

Next partial solves built from this frame: frame 2 from route 1.2 (lands B3 t27).

### Frame 2: reach B4 from route 1.2 (lands B3 t27)

Inherited 148 constraint rows from the parent's solved route (48 normal:landing, 42 contact:finite_lo, 42 contact:finite_hi, 2 initial:0:X:lo, 2 initial:0:X:hi, 2 initial:0:Z:lo, 2 initial:0:Z:hi, 2 landing:1:X:lo, 2 landing:1:X:hi, 2 landing:1:Z:lo, 2 landing:1:Z:hi); starting facings seeded from it: yes.

#### Movement: jumps at t8, t19, t30; starts on B1 t0, runs to t7; lands B2 t16, runs to t18; lands B3 t27, runs to t29; lands B4 t39; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 2.1 | feasible | 40 | -1272.413 | no obstacle in reach | no avoidance rows |

Obstacle checks: 60; 60 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

Options rejected before solving: 0.

No obstacle is in reach, so there is no avoidance choice: the movement was solved once as is.

| # | Rows added | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | no avoidance rows | feasible | accepted | request 4 feasible, IPOPT Solve_Succeeded, 0.17 s |

Solver time for this movement: 0.17 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| no obstacle sides | no switch (#1: -1272.413) | no switch (#1: -1272.413) | 0 |

Kept: alternative #1; fallbacks: none.

Next partial solves built from this frame: frame 3 from route 2.1 (lands B4 t39).

### Frame 3: reach B5 from route 2.1 (lands B4 t39)

Inherited 188 constraint rows from the parent's solved route (60 normal:landing, 54 contact:finite_lo, 54 contact:finite_hi, 3 landing:1:X:lo, 3 landing:1:X:hi, 3 landing:1:Z:lo, 3 landing:1:Z:hi, 2 initial:0:X:lo, 2 initial:0:X:hi, 2 initial:0:Z:lo, 2 initial:0:Z:hi); starting facings seeded from it: yes.

#### Movement: jumps at t8, t19, t30, t42; starts on B1 t0, runs to t7; lands B2 t16, runs to t18; lands B3 t27, runs to t29; lands B4 t39, runs to t41; lands B5 t56; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 3.1 | oracle_rejected | - | - | - | - |

Obstacle checks: 72; 71 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

| Obstacle | Segment ticks | Possible phases | Already clear | Faces ruled out | Vertical clear | Uncertain |
|---|---|---|---|---|---|---|
| b5+b6+b7 | 42-57 | 56X-56Z | 43 | X+ at 56X; X+Z+ at 56X-56Z; X- at 56X-56Z; Z+ at 56X-56Z | none | none |

Decisions:

- which sides of B5+B6+B7 to keep (segment t42-57)

Options rejected before solving: 1 (1 linear control relaxation).

| # | Decision | Option | Check | Detail |
|---|---|---|---|---|
| 1 | B5+B6+B7 sides t42-57 | B5 landing/B6/B7: Z ≤ -1095.300 at t56; B6: X ≥ 731.300 at t57 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |

Alternatives solved: 1. Each combines one surviving option per decision; the best feasible one is kept.

| # | Rows the alternative adds | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | B5 landing: X ≥ 729.700 at t56; B5 landing: X ≤ 731.300 at t56; B5 landing: Z ≥ -1096.300 at t56; B5 landing/B6/B7: Z ≤ -1095.300 at t56-57 | unknown | no_candidate | request 5 unknown, IPOPT Infeasible_Problem_Detected, 0.34 s |

Solver time for this movement: 0.34 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| B5+B6+B7: Z- | no switch (#1: infeasible) | none feasible | 0 |

Kept: none; fallbacks: none.

#### Movement: jumps at t8, t19, t30, t43; starts on B1 t0, runs to t7; lands B2 t16, runs to t18; lands B3 t27, runs to t29; lands B4 t39, runs to t42; lands B5 t57; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 3.2 | feasible | 58 | -1155.104 | B5+B6+B7: Z- (t43-58) | B5 landing/B6/B7: Z ≤ -1095.300 at t56-58; B5 landing: X ≥ 729.700 at t57; B5 landing: X ≤ 731.300 at t57; B5 landing: Z ≥ -1096.300 at t57 |

Obstacle checks: 76; 75 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

| Obstacle | Segment ticks | Possible phases | Already clear | Faces ruled out | Vertical clear | Uncertain |
|---|---|---|---|---|---|---|
| b5+b6+b7 | 43-58 | 56X-57Z | 40 | X+ at 56X-57X; X+Z+ at 56X-57Z; X- at 56X-57Z; Z+ at 56X-57Z | none | none |

Decisions:

- which sides of B5+B6+B7 to keep (segment t43-58)

Options rejected before solving: 1 (1 linear control relaxation).

| # | Decision | Option | Check | Detail |
|---|---|---|---|---|
| 1 | B5+B6+B7 sides t43-58 | B5 landing/B6/B7: Z ≤ -1095.300 at t56-57; B6: X ≥ 731.300 at t58 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |

Alternatives solved: 1. Each combines one surviving option per decision; the best feasible one is kept.

| # | Rows the alternative adds | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | B5 landing/B6/B7: Z ≤ -1095.300 at t56-58; B5 landing: X ≥ 729.700 at t57; B5 landing: X ≤ 731.300 at t57; B5 landing: Z ≥ -1096.300 at t57 | feasible | accepted | request 6 feasible, IPOPT Solve_Succeeded, 0.19 s |

Solver time for this movement: 0.19 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| B5+B6+B7: Z- | no switch (#1: -1155.104) | no switch (#1: -1155.104) | 0 |

Kept: alternative #1; fallbacks: none.

Next partial solves built from this frame: frame 4 from route 3.2 (lands B5 t57).

### Frame 4: reach B7 from route 3.2 (lands B5 t57)

Inherited 242 constraint rows from the parent's solved route (76 normal:landing, 68 contact:finite_lo, 68 contact:finite_hi, 4 landing:1:X:lo, 4 landing:1:X:hi, 4 landing:1:Z:lo, 4 landing:1:Z:hi, 3 avoidance, 3 event:required_contact_region, 2 initial:0:X:lo, 2 initial:0:X:hi, 2 initial:0:Z:lo, 2 initial:0:Z:hi); starting facings seeded from it: yes.

#### Movement: jumps at t8, t19, t30, t43, t58; starts on B1 t0, runs to t7; lands B2 t16, runs to t18; lands B3 t27, runs to t29; lands B4 t39, runs to t42; lands B5 t57; lands B7 t66; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 4.1 | feasible | 67 | -1093.823 | B5+B6+B7: Z- then X- (t58-67) | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≤ 729.700 at t62-67 |

Obstacle checks: 80; 79 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

| Obstacle | Segment ticks | Possible phases | Already clear | Faces ruled out | Vertical clear | Uncertain |
|---|---|---|---|---|---|---|
| b5+b6+b7 | 58-67 | 58Z-66Z | 2 | X+ at 62X-66Z; X+Z+ at 58Z-60Y; X- at 58Z-59X; Z+ at 58Z-61Z; Z- at 65Z-66Z | none | none |

Decisions:

- which sides of B5+B6+B7 to keep (segment t58-67)

Options rejected before solving: 49 (41 interval reachability, 4 linear control relaxation, 4 travel-distance reachability).

| # | Decision | Option | Check | Detail |
|---|---|---|---|---|
| 1 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≤ 729.700 at t61-66; B6: Z ≥ -1093.700 at t66-67 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |
| 2 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≤ 729.700 at t61-65; B6: Z ≥ -1093.700 at t65-67 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |
| 3 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≤ 729.700 at t61-64; B6: Z ≥ -1093.700 at t64-67 | interval reachability | empty Z interval at state 64 |
| 4 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≤ 729.700 at t61-63; B6: Z ≥ -1093.700 at t63-67 | interval reachability | empty Z interval at state 63 |
| 5 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≤ 729.700 at t61-62; B6: Z ≥ -1093.700 at t62-67 | interval reachability | empty Z interval at state 62 |
| 6 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≥ 731.300 at t61-62; B6: Z ≥ -1093.700 at t62; B6: X ≤ 729.700 at t63-67 | interval reachability | empty Z interval at state 62 |
| 7 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≥ 731.300 at t61-62; B6: Z ≥ -1093.700 at t62-63; B6: X ≤ 729.700 at t64-67 | interval reachability | empty Z interval at state 62 |
| 8 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≥ 731.300 at t61-62; B6: Z ≥ -1093.700 at t62-64; B6: X ≤ 729.700 at t65-67 | interval reachability | empty Z interval at state 62 |
| 9 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≥ 731.300 at t61-62; B6: Z ≥ -1093.700 at t62-65; B6: X ≤ 729.700 at t66-67 | interval reachability | empty Z interval at state 62 |
| 10 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≥ 731.300 at t61-62; B6: Z ≥ -1093.700 at t62-66; B6: X ≤ 729.700 at t67 | interval reachability | empty Z interval at state 62 |
| 11 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≥ 731.300 at t61-62; B6: Z ≥ -1093.700 at t62-67 | interval reachability | empty Z interval at state 62 |
| 12 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6: X ≤ 729.700 at t61-62; B6: Z ≥ -1093.700 at t62-67 | interval reachability | empty Z interval at state 62 |
| 13 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6: X ≤ 729.700 at t61-67 | travel-distance reachability | state 51 unreachable from state 0 (gap 16.142 > travel 16.074) |
| 14 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6: X ≤ 729.700 at t61-66; B6: Z ≥ -1093.700 at t66-67 | travel-distance reachability | state 51 unreachable from state 0 (gap 16.142 > travel 16.074) |
| 15 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6: X ≤ 729.700 at t61-65; B6: Z ≥ -1093.700 at t65-67 | travel-distance reachability | state 51 unreachable from state 0 (gap 16.142 > travel 16.074) |
| 16 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6: X ≤ 729.700 at t61-64; B6: Z ≥ -1093.700 at t64-67 | travel-distance reachability | state 51 unreachable from state 0 (gap 16.148 > travel 16.074) |
| 17 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6: X ≤ 729.700 at t61-63; B6: Z ≥ -1093.700 at t63-67 | interval reachability | empty Z interval at state 63 |
| 18 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B6: X ≥ 731.300 at t60-62; B6: Z ≥ -1093.700 at t62; B6: X ≤ 729.700 at t63-67 | interval reachability | empty Z interval at state 62 |
| 19 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B6: X ≥ 731.300 at t60-62; B6: Z ≥ -1093.700 at t62-63; B6: X ≤ 729.700 at t64-67 | interval reachability | empty Z interval at state 62 |
| 20 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B6: X ≥ 731.300 at t60-62; B6: Z ≥ -1093.700 at t62-64; B6: X ≤ 729.700 at t65-67 | interval reachability | empty Z interval at state 62 |
| 21 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B6: X ≥ 731.300 at t60-62; B6: Z ≥ -1093.700 at t62-65; B6: X ≤ 729.700 at t66-67 | interval reachability | empty Z interval at state 62 |
| 22 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B6: X ≥ 731.300 at t60-62; B6: Z ≥ -1093.700 at t62-66; B6: X ≤ 729.700 at t67 | interval reachability | empty Z interval at state 62 |
| 23 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-59; B6: X ≥ 731.300 at t60-62; B6: Z ≥ -1093.700 at t62-67 | interval reachability | empty Z interval at state 62 |
| 24 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≤ 729.700 at t62-66; B6: Z ≥ -1093.700 at t66-67 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |
| 25 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≤ 729.700 at t62-65; B6: Z ≥ -1093.700 at t65-67 | interval reachability | empty Z interval at state 65 |
| 26 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≤ 729.700 at t62-64; B6: Z ≥ -1093.700 at t64-67 | interval reachability | empty Z interval at state 64 |
| 27 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≤ 729.700 at t62-63; B6: Z ≥ -1093.700 at t63-67 | interval reachability | empty Z interval at state 63 |
| 28 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≤ 729.700 at t62; B6: Z ≥ -1093.700 at t62-67 | interval reachability | empty Z interval at state 62 |
| 29 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≥ 731.300 at t62; B6: Z ≥ -1093.700 at t62; B6: X ≤ 729.700 at t63-67 | interval reachability | empty Z interval at state 62 |
| 30 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≥ 731.300 at t62; B6: Z ≥ -1093.700 at t62-63; B6: X ≤ 729.700 at t64-67 | interval reachability | empty Z interval at state 62 |
| 31 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≥ 731.300 at t62; B6: Z ≥ -1093.700 at t62-64; B6: X ≤ 729.700 at t65-67 | interval reachability | empty Z interval at state 62 |
| 32 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≥ 731.300 at t62; B6: Z ≥ -1093.700 at t62-65; B6: X ≤ 729.700 at t66-67 | interval reachability | empty Z interval at state 62 |
| 33 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≥ 731.300 at t62; B6: Z ≥ -1093.700 at t62-66; B6: X ≤ 729.700 at t67 | interval reachability | empty Z interval at state 62 |
| 34 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≥ 731.300 at t62; B6: Z ≥ -1093.700 at t62-67 | interval reachability | empty Z interval at state 62 |
| 35 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-62; B6: X ≤ 729.700 at t63-66; B6: Z ≥ -1093.700 at t66-67 | interval reachability | empty Z interval at state 66 |
| 36 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-62; B6: X ≤ 729.700 at t63-65; B6: Z ≥ -1093.700 at t65-67 | interval reachability | empty Z interval at state 65 |
| 37 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-62; B6: X ≤ 729.700 at t63-64; B6: Z ≥ -1093.700 at t64-67 | interval reachability | empty Z interval at state 64 |
| 38 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-62; B6: X ≤ 729.700 at t63; B6: Z ≥ -1093.700 at t63-67 | interval reachability | empty Z interval at state 63 |
| 39 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-63; B6: X ≤ 729.700 at t64-66; B6: Z ≥ -1093.700 at t66-67 | interval reachability | empty Z interval at state 66 |
| 40 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-63; B6: X ≤ 729.700 at t64-65; B6: Z ≥ -1093.700 at t65-67 | interval reachability | empty Z interval at state 65 |
| 41 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-63; B6: X ≤ 729.700 at t64; B6: Z ≥ -1093.700 at t64-67 | interval reachability | empty Z interval at state 64 |
| 42 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-64; B6: X ≤ 729.700 at t65-66; B6: Z ≥ -1093.700 at t66-67 | interval reachability | empty Z interval at state 66 |
| 43 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-64; B6: X ≤ 729.700 at t65; B6: Z ≥ -1093.700 at t65-67 | interval reachability | empty Z interval at state 65 |
| 44 | B5+B6+B7 sides t58-67 | B6/B7: Z ≤ -1095.300 at t58-65; B6: X ≤ 729.700 at t66; B6: Z ≥ -1093.700 at t66-67 | interval reachability | empty Z interval at state 66 |
| 45 | B5+B6+B7 sides t58-67 | B6: X ≥ 731.300 at t59-62; B6: Z ≥ -1093.700 at t62; B6: X ≤ 729.700 at t63-67 | interval reachability | empty X interval at state 63 |
| 46 | B5+B6+B7 sides t58-67 | B6: X ≥ 731.300 at t59-62; B6: Z ≥ -1093.700 at t62-63; B6: X ≤ 729.700 at t64-67 | interval reachability | empty X interval at state 64 |
| 47 | B5+B6+B7 sides t58-67 | B6: X ≥ 731.300 at t59-62; B6: Z ≥ -1093.700 at t62-64; B6: X ≤ 729.700 at t65-67 | interval reachability | empty X interval at state 65 |
| 48 | B5+B6+B7 sides t58-67 | B6: X ≥ 731.300 at t59-62; B6: Z ≥ -1093.700 at t62-65; B6: X ≤ 729.700 at t66-67 | interval reachability | empty X interval at state 66 |
| 49 | B5+B6+B7 sides t58-67 | B6: X ≥ 731.300 at t59-62; B6: Z ≥ -1093.700 at t62-67 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |

Options dropped without solving: 23 (23 duplicate).

| # | Decision | Reason | Option |
|---|---|---|---|
| 1 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≤ 729.700 at t61-67 |
| 2 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≥ 731.300 at t61-62; B6: Z ≥ -1093.700 at t62; B6: X ≤ 729.700 at t63-67 |
| 3 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≥ 731.300 at t61-62; B6: Z ≥ -1093.700 at t62-63; B6: X ≤ 729.700 at t64-67 |
| 4 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≥ 731.300 at t61-62; B6: Z ≥ -1093.700 at t62-64; B6: X ≤ 729.700 at t65-67 |
| 5 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≥ 731.300 at t61-62; B6: Z ≥ -1093.700 at t62-65; B6: X ≤ 729.700 at t66-67 |
| 6 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-59; B7: X ≤ 728.700 at t60; B6: X ≤ 729.700 at t61-67 |
| 7 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-59; B6: X ≥ 731.300 at t60-62; B6: Z ≥ -1093.700 at t62; B6: X ≤ 729.700 at t63-67 |
| 8 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-59; B6: X ≥ 731.300 at t60-62; B6: Z ≥ -1093.700 at t62-63; B6: X ≤ 729.700 at t64-67 |
| 9 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-59; B6: X ≥ 731.300 at t60-62; B6: Z ≥ -1093.700 at t62-64; B6: X ≤ 729.700 at t65-67 |
| 10 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-59; B6: X ≥ 731.300 at t60-62; B6: Z ≥ -1093.700 at t62-65; B6: X ≤ 729.700 at t66-67 |
| 11 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≤ 729.700 at t62-67 |
| 12 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≥ 731.300 at t62; B6: Z ≥ -1093.700 at t62; B6: X ≤ 729.700 at t63-67 |
| 13 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≥ 731.300 at t62; B6: Z ≥ -1093.700 at t62-63; B6: X ≤ 729.700 at t64-67 |
| 14 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≥ 731.300 at t62; B6: Z ≥ -1093.700 at t62-64; B6: X ≤ 729.700 at t65-67 |
| 15 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≥ 731.300 at t62; B6: Z ≥ -1093.700 at t62-65; B6: X ≤ 729.700 at t66-67 |
| 16 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-62; B6: X ≤ 729.700 at t63-67 |
| 17 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-63; B6: X ≤ 729.700 at t64-67 |
| 18 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-64; B6: X ≤ 729.700 at t65-67 |
| 19 | B5+B6+B7 sides t58-67 | duplicate | B6/B7: Z ≤ -1095.300 at t58-65; B6: X ≤ 729.700 at t66-67 |
| 20 | B5+B6+B7 sides t58-67 | duplicate | B6: X ≥ 731.300 at t59-62; B6: Z ≥ -1093.700 at t62; B6: X ≤ 729.700 at t63-67 |
| 21 | B5+B6+B7 sides t58-67 | duplicate | B6: X ≥ 731.300 at t59-62; B6: Z ≥ -1093.700 at t62-63; B6: X ≤ 729.700 at t64-67 |
| 22 | B5+B6+B7 sides t58-67 | duplicate | B6: X ≥ 731.300 at t59-62; B6: Z ≥ -1093.700 at t62-64; B6: X ≤ 729.700 at t65-67 |
| 23 | B5+B6+B7 sides t58-67 | duplicate | B6: X ≥ 731.300 at t59-62; B6: Z ≥ -1093.700 at t62-65; B6: X ≤ 729.700 at t66-67 |

Alternatives solved: 4. Each combines one surviving option per decision; the best feasible one is kept.

| # | Rows the alternative adds | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | B6/B7: Z ≤ -1095.300 at t58-60; B6: X ≤ 729.700 at t61-67 | unknown | no_candidate | request 7 unknown, IPOPT Infeasible_Problem_Detected, 0.39 s |
| 2 | B6/B7: Z ≤ -1095.300 at t58-61; B6: X ≤ 729.700 at t62-67 | feasible | accepted | request 8 feasible, IPOPT Solve_Succeeded, 0.21 s |
| 3 | B6/B7: Z ≤ -1095.300 at t58-62; B6: X ≤ 729.700 at t63-67 | feasible | accepted | request 9 feasible, IPOPT Solve_Succeeded, 0.19 s |
| 4 | B6: X ≥ 731.300 at t59-62; B6: Z ≥ -1093.700 at t62-66; B6: X ≤ 729.700 at t67 | unknown | no_candidate | blocked (linear_control_relaxation_infeasible) |

Solver time for this movement: 0.79 s over 3 requests.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| B5+B6+B7: Z- then X- | input 60 (#1: infeasible); input 61 (#2: -1093.823); input 62 (#3: -1094.162) | input 61 (#2: -1093.823) | 3 |
| B5+B6+B7: Z- then X+ then Z+ then X- | inputs 58, 62, 66 (#4: infeasible) | none feasible | 0 |

Kept: alternative #2 (the last leg keeps no fallbacks).

Unresolved alternatives (kept for an explicit retry):

- B6: X ≥ 731.300 at t59-62; B6: Z ≥ -1093.700 at t62-66; B6: X ≤ 729.700 at t67

### Frame 5: reach B3 from route 0.2 (lands B2 t20)

Inherited 32 constraint rows from the parent's solved route (8 normal:landing, 6 contact:finite_lo, 6 contact:finite_hi, 2 initial:0:X:lo, 2 initial:0:X:hi, 2 initial:0:Z:lo, 2 initial:0:Z:hi, 1 landing:1:X:lo, 1 landing:1:X:hi, 1 landing:1:Z:lo, 1 landing:1:Z:hi); starting facings seeded from it: yes.

#### Movement: jumps at t0, t12, t21; lands B1 t11; lands B2 t20; lands B3 t29; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 5.1 | feasible | 30 | -982.821 | no obstacle in reach | no avoidance rows |

Obstacle checks: 12; 12 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

Options rejected before solving: 0.

No obstacle is in reach, so there is no avoidance choice: the movement was solved once as is.

| # | Rows added | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | no avoidance rows | feasible | accepted | request 12 feasible, IPOPT Solve_Succeeded, 0.16 s |

Solver time for this movement: 0.16 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| no obstacle sides | no switch (#1: -982.821) | no switch (#1: -982.821) | 0 |

Kept: alternative #1; fallbacks: none.

Next partial solves built from this frame: frame 6 from route 5.1 (lands B3 t29).

### Frame 6: reach B4 from route 5.1 (lands B3 t29)

Inherited 48 constraint rows from the parent's solved route (12 normal:landing, 10 contact:finite_lo, 10 contact:finite_hi, 2 initial:0:X:lo, 2 initial:0:X:hi, 2 initial:0:Z:lo, 2 initial:0:Z:hi, 2 landing:1:X:lo, 2 landing:1:X:hi, 2 landing:1:Z:lo, 2 landing:1:Z:hi); starting facings seeded from it: yes.

#### Movement: jumps at t0, t12, t21, t30; lands B1 t11; lands B2 t20; lands B3 t29; lands B4 t39; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 6.1 | feasible | 40 | -1271.548 | no obstacle in reach | no avoidance rows |

Obstacle checks: 16; 16 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

Options rejected before solving: 0.

No obstacle is in reach, so there is no avoidance choice: the movement was solved once as is.

| # | Rows added | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | no avoidance rows | feasible | accepted | request 13 feasible, IPOPT Solve_Succeeded, 0.19 s |

Solver time for this movement: 0.19 s over 1 request.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| no obstacle sides | no switch (#1: -1271.548) | no switch (#1: -1271.548) | 0 |

Kept: alternative #1; fallbacks: none.

Next partial solves built from this frame: frame 7 from route 6.1 (lands B4 t39).

### Frame 7: reach B5 from route 6.1 (lands B4 t39)

Inherited 64 constraint rows from the parent's solved route (16 normal:landing, 14 contact:finite_lo, 14 contact:finite_hi, 3 landing:1:X:lo, 3 landing:1:X:hi, 3 landing:1:Z:lo, 3 landing:1:Z:hi, 2 initial:0:X:lo, 2 initial:0:X:hi, 2 initial:0:Z:lo, 2 initial:0:Z:hi); starting facings seeded from it: yes.

#### Movement: jumps at t0, t12, t21, t30, t40; lands B1 t11; lands B2 t20; lands B3 t29; lands B4 t39; lands B5 t54; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 7.1 | feasible | 55 | -1154.498 | B5+B6+B7: Z- (t40-55) | B5 landing/B6/B7: Z ≤ -1095.300 at t53-55; B5 landing: X ≥ 729.700 at t54; B5 landing: X ≤ 731.300 at t54; B5 landing: Z ≥ -1096.300 at t54 |

Obstacle checks: 20; 19 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

| Obstacle | Segment ticks | Possible phases | Already clear | Faces ruled out | Vertical clear | Uncertain |
|---|---|---|---|---|---|---|
| b5+b6+b7 | 40-55 | 53X-54Z | 40 | X+ at 53X-54X; X+Z+ at 53X-54Z; X- at 53X-54Z; Z+ at 53X-54Z | none | none |

Decisions:

- which sides of B5+B6+B7 to keep (segment t40-55)

Options rejected before solving: 0.

Alternatives solved: 2. Each combines one surviving option per decision; the best feasible one is kept.

| # | Rows the alternative adds | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | B5 landing/B6/B7: Z ≤ -1095.300 at t53-55; B5 landing: X ≥ 729.700 at t54; B5 landing: X ≤ 731.300 at t54; B5 landing: Z ≥ -1096.300 at t54 | feasible | accepted | request 14 feasible, IPOPT Solve_Succeeded, 0.18 s |
| 2 | B5 landing/B6/B7: Z ≤ -1095.300 at t53-54; B5 landing: X ≥ 729.700 at t54; B5 landing: X ≤ 731.300 at t54; B5 landing: Z ≥ -1096.300 at t54; B6: X ≥ 731.300 at t55 | unknown | no_candidate | request 15 unknown, IPOPT Infeasible_Problem_Detected, 0.25 s |

Solver time for this movement: 0.44 s over 2 requests.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| B5+B6+B7: Z- | no switch (#1: -1154.498) | no switch (#1: -1154.498) | 0 |
| B5+B6+B7: Z- then X+ | input 54 (#2: infeasible) | none feasible | 0 |

Kept: alternative #1; fallbacks: none.

Next partial solves built from this frame: frame 8 from route 7.1 (lands B5 t54).

### Frame 8: reach B7 from route 7.1 (lands B5 t54)

Inherited 86 constraint rows from the parent's solved route (20 normal:landing, 18 contact:finite_lo, 18 contact:finite_hi, 4 landing:1:X:lo, 4 landing:1:X:hi, 4 landing:1:Z:lo, 4 landing:1:Z:hi, 3 avoidance, 3 event:required_contact_region, 2 initial:0:X:lo, 2 initial:0:X:hi, 2 initial:0:Z:lo, 2 initial:0:Z:hi); starting facings seeded from it: yes.

#### Movement: jumps at t0, t12, t21, t30, t40, t55; lands B1 t11; lands B2 t20; lands B3 t29; lands B4 t39; lands B5 t54; lands B7 t63; no ceiling hit

This movement produced one route.

| Route | Outcome | Solved ticks | Objective | Obstacles passed | Accepted avoidance rows |
|---|---|---|---|---|---|
| 8.1 | feasible | 64 | -1093.238 | B5+B6+B7: Z- then X- then Z+ (t55-64) | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≤ 729.700 at t58-63; B6: Z ≥ -1093.700 at t63-64 |

Obstacle checks: 24; 23 cannot touch the player anywhere in their segment. A check is repeated when the rows chosen so far change what is reachable.

| Obstacle | Segment ticks | Possible phases | Already clear | Faces ruled out | Vertical clear | Uncertain |
|---|---|---|---|---|---|---|
| b5+b6+b7 | 55-64 | 55Z-63Z | 2 | X+ at 59X-63Z; X+Z+ at 55Z-57Y; X- at 55Z-56X; Z+ at 55Z-58Z; Z- at 62Z-63Z | none | none |

Decisions:

- which sides of B5+B6+B7 to keep (segment t55-64)

Options rejected before solving: 49 (41 interval reachability, 4 linear control relaxation, 4 travel-distance reachability).

| # | Decision | Option | Check | Detail |
|---|---|---|---|---|
| 1 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≤ 729.700 at t58-62; B6: Z ≥ -1093.700 at t62-64 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |
| 2 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≤ 729.700 at t58-61; B6: Z ≥ -1093.700 at t61-64 | interval reachability | empty Z interval at state 61 |
| 3 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≤ 729.700 at t58-60; B6: Z ≥ -1093.700 at t60-64 | interval reachability | empty Z interval at state 60 |
| 4 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≤ 729.700 at t58-59; B6: Z ≥ -1093.700 at t59-64 | interval reachability | empty Z interval at state 59 |
| 5 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≥ 731.300 at t58-59; B6: Z ≥ -1093.700 at t59; B6: X ≤ 729.700 at t60-64 | interval reachability | empty Z interval at state 59 |
| 6 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≥ 731.300 at t58-59; B6: Z ≥ -1093.700 at t59-60; B6: X ≤ 729.700 at t61-64 | interval reachability | empty Z interval at state 59 |
| 7 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≥ 731.300 at t58-59; B6: Z ≥ -1093.700 at t59-61; B6: X ≤ 729.700 at t62-64 | interval reachability | empty Z interval at state 59 |
| 8 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≥ 731.300 at t58-59; B6: Z ≥ -1093.700 at t59-62; B6: X ≤ 729.700 at t63-64 | interval reachability | empty Z interval at state 59 |
| 9 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≥ 731.300 at t58-59; B6: Z ≥ -1093.700 at t59-64 | interval reachability | empty Z interval at state 59 |
| 10 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≥ 731.300 at t58-59; B6: Z ≥ -1093.700 at t59-63; B6: X ≤ 729.700 at t64 | interval reachability | empty Z interval at state 59 |
| 11 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6: X ≤ 729.700 at t58-64 | travel-distance reachability | state 51 unreachable from state 0 (gap 17.174 > travel 17.118) |
| 12 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6: X ≤ 729.700 at t58-63; B6: Z ≥ -1093.700 at t63-64 | travel-distance reachability | state 51 unreachable from state 0 (gap 17.174 > travel 17.118) |
| 13 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6: X ≤ 729.700 at t58-62; B6: Z ≥ -1093.700 at t62-64 | travel-distance reachability | state 51 unreachable from state 0 (gap 17.174 > travel 17.118) |
| 14 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6: X ≤ 729.700 at t58-61; B6: Z ≥ -1093.700 at t61-64 | travel-distance reachability | state 51 unreachable from state 0 (gap 17.174 > travel 17.118) |
| 15 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6: X ≤ 729.700 at t58-60; B6: Z ≥ -1093.700 at t60-64 | interval reachability | empty Z interval at state 60 |
| 16 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6: X ≤ 729.700 at t58-59; B6: Z ≥ -1093.700 at t59-64 | interval reachability | empty Z interval at state 59 |
| 17 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B6: X ≥ 731.300 at t57-59; B6: Z ≥ -1093.700 at t59; B6: X ≤ 729.700 at t60-64 | interval reachability | empty Z interval at state 59 |
| 18 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B6: X ≥ 731.300 at t57-59; B6: Z ≥ -1093.700 at t59-60; B6: X ≤ 729.700 at t61-64 | interval reachability | empty Z interval at state 59 |
| 19 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B6: X ≥ 731.300 at t57-59; B6: Z ≥ -1093.700 at t59-61; B6: X ≤ 729.700 at t62-64 | interval reachability | empty Z interval at state 59 |
| 20 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B6: X ≥ 731.300 at t57-59; B6: Z ≥ -1093.700 at t59-62; B6: X ≤ 729.700 at t63-64 | interval reachability | empty Z interval at state 59 |
| 21 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B6: X ≥ 731.300 at t57-59; B6: Z ≥ -1093.700 at t59-64 | interval reachability | empty Z interval at state 59 |
| 22 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-56; B6: X ≥ 731.300 at t57-59; B6: Z ≥ -1093.700 at t59-63; B6: X ≤ 729.700 at t64 | interval reachability | empty Z interval at state 59 |
| 23 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≤ 729.700 at t59-63; B6: Z ≥ -1093.700 at t63-64 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |
| 24 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≤ 729.700 at t59-62; B6: Z ≥ -1093.700 at t62-64 | interval reachability | empty Z interval at state 62 |
| 25 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≤ 729.700 at t59-61; B6: Z ≥ -1093.700 at t61-64 | interval reachability | empty Z interval at state 61 |
| 26 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≤ 729.700 at t59-60; B6: Z ≥ -1093.700 at t60-64 | interval reachability | empty Z interval at state 60 |
| 27 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≤ 729.700 at t59; B6: Z ≥ -1093.700 at t59-64 | interval reachability | empty Z interval at state 59 |
| 28 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≥ 731.300 at t59; B6: Z ≥ -1093.700 at t59; B6: X ≤ 729.700 at t60-64 | interval reachability | empty Z interval at state 59 |
| 29 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≥ 731.300 at t59; B6: Z ≥ -1093.700 at t59-60; B6: X ≤ 729.700 at t61-64 | interval reachability | empty Z interval at state 59 |
| 30 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≥ 731.300 at t59; B6: Z ≥ -1093.700 at t59-61; B6: X ≤ 729.700 at t62-64 | interval reachability | empty Z interval at state 59 |
| 31 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≥ 731.300 at t59; B6: Z ≥ -1093.700 at t59-62; B6: X ≤ 729.700 at t63-64 | interval reachability | empty Z interval at state 59 |
| 32 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≥ 731.300 at t59; B6: Z ≥ -1093.700 at t59-64 | interval reachability | empty Z interval at state 59 |
| 33 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≥ 731.300 at t59; B6: Z ≥ -1093.700 at t59-63; B6: X ≤ 729.700 at t64 | interval reachability | empty Z interval at state 59 |
| 34 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-59; B6: X ≤ 729.700 at t60-63; B6: Z ≥ -1093.700 at t63-64 | interval reachability | empty Z interval at state 63 |
| 35 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-59; B6: X ≤ 729.700 at t60-62; B6: Z ≥ -1093.700 at t62-64 | interval reachability | empty Z interval at state 62 |
| 36 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-59; B6: X ≤ 729.700 at t60-61; B6: Z ≥ -1093.700 at t61-64 | interval reachability | empty Z interval at state 61 |
| 37 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-59; B6: X ≤ 729.700 at t60; B6: Z ≥ -1093.700 at t60-64 | interval reachability | empty Z interval at state 60 |
| 38 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-60; B6: X ≤ 729.700 at t61-63; B6: Z ≥ -1093.700 at t63-64 | interval reachability | empty Z interval at state 63 |
| 39 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-60; B6: X ≤ 729.700 at t61-62; B6: Z ≥ -1093.700 at t62-64 | interval reachability | empty Z interval at state 62 |
| 40 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-60; B6: X ≤ 729.700 at t61; B6: Z ≥ -1093.700 at t61-64 | interval reachability | empty Z interval at state 61 |
| 41 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-61; B6: X ≤ 729.700 at t62-63; B6: Z ≥ -1093.700 at t63-64 | interval reachability | empty Z interval at state 63 |
| 42 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-61; B6: X ≤ 729.700 at t62; B6: Z ≥ -1093.700 at t62-64 | interval reachability | empty Z interval at state 62 |
| 43 | B5+B6+B7 sides t55-64 | B6: X ≥ 731.300 at t56-59; B6: Z ≥ -1093.700 at t59; B6: X ≤ 729.700 at t60-64 | interval reachability | empty X interval at state 60 |
| 44 | B5+B6+B7 sides t55-64 | B6: X ≥ 731.300 at t56-59; B6: Z ≥ -1093.700 at t59-60; B6: X ≤ 729.700 at t61-64 | interval reachability | empty X interval at state 61 |
| 45 | B5+B6+B7 sides t55-64 | B6: X ≥ 731.300 at t56-59; B6: Z ≥ -1093.700 at t59-61; B6: X ≤ 729.700 at t62-64 | interval reachability | empty X interval at state 62 |
| 46 | B5+B6+B7 sides t55-64 | B6: X ≥ 731.300 at t56-59; B6: Z ≥ -1093.700 at t59-62; B6: X ≤ 729.700 at t63-64 | interval reachability | empty X interval at state 63 |
| 47 | B5+B6+B7 sides t55-64 | B6: X ≥ 731.300 at t56-59; B6: Z ≥ -1093.700 at t59-64 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |
| 48 | B5+B6+B7 sides t55-64 | B6: X ≥ 731.300 at t56-59; B6: Z ≥ -1093.700 at t59-63; B6: X ≤ 729.700 at t64 | linear control relaxation | no input sequence satisfies the linearised dynamics and these rows |
| 49 | B5+B6+B7 sides t55-64 | B6/B7: Z ≤ -1095.300 at t55-62; B6: X ≤ 729.700 at t63; B6: Z ≥ -1093.700 at t63-64 | interval reachability | empty Z interval at state 63 |

Options dropped without solving: 23 (23 duplicate).

| # | Decision | Reason | Option |
|---|---|---|---|
| 1 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≤ 729.700 at t58-64 |
| 2 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≥ 731.300 at t58-59; B6: Z ≥ -1093.700 at t59; B6: X ≤ 729.700 at t60-64 |
| 3 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≥ 731.300 at t58-59; B6: Z ≥ -1093.700 at t59-60; B6: X ≤ 729.700 at t61-64 |
| 4 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≥ 731.300 at t58-59; B6: Z ≥ -1093.700 at t59-61; B6: X ≤ 729.700 at t62-64 |
| 5 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≥ 731.300 at t58-59; B6: Z ≥ -1093.700 at t59-62; B6: X ≤ 729.700 at t63-64 |
| 6 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-56; B7: X ≤ 728.700 at t57; B6: X ≤ 729.700 at t58-64 |
| 7 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-56; B6: X ≥ 731.300 at t57-59; B6: Z ≥ -1093.700 at t59; B6: X ≤ 729.700 at t60-64 |
| 8 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-56; B6: X ≥ 731.300 at t57-59; B6: Z ≥ -1093.700 at t59-60; B6: X ≤ 729.700 at t61-64 |
| 9 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-56; B6: X ≥ 731.300 at t57-59; B6: Z ≥ -1093.700 at t59-61; B6: X ≤ 729.700 at t62-64 |
| 10 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-56; B6: X ≥ 731.300 at t57-59; B6: Z ≥ -1093.700 at t59-62; B6: X ≤ 729.700 at t63-64 |
| 11 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≤ 729.700 at t59-64 |
| 12 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≥ 731.300 at t59; B6: Z ≥ -1093.700 at t59; B6: X ≤ 729.700 at t60-64 |
| 13 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≥ 731.300 at t59; B6: Z ≥ -1093.700 at t59-60; B6: X ≤ 729.700 at t61-64 |
| 14 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≥ 731.300 at t59; B6: Z ≥ -1093.700 at t59-61; B6: X ≤ 729.700 at t62-64 |
| 15 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≥ 731.300 at t59; B6: Z ≥ -1093.700 at t59-62; B6: X ≤ 729.700 at t63-64 |
| 16 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-59; B6: X ≤ 729.700 at t60-64 |
| 17 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-60; B6: X ≤ 729.700 at t61-64 |
| 18 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-61; B6: X ≤ 729.700 at t62-64 |
| 19 | B5+B6+B7 sides t55-64 | duplicate | B6: X ≥ 731.300 at t56-59; B6: Z ≥ -1093.700 at t59; B6: X ≤ 729.700 at t60-64 |
| 20 | B5+B6+B7 sides t55-64 | duplicate | B6: X ≥ 731.300 at t56-59; B6: Z ≥ -1093.700 at t59-60; B6: X ≤ 729.700 at t61-64 |
| 21 | B5+B6+B7 sides t55-64 | duplicate | B6: X ≥ 731.300 at t56-59; B6: Z ≥ -1093.700 at t59-61; B6: X ≤ 729.700 at t62-64 |
| 22 | B5+B6+B7 sides t55-64 | duplicate | B6: X ≥ 731.300 at t56-59; B6: Z ≥ -1093.700 at t59-62; B6: X ≤ 729.700 at t63-64 |
| 23 | B5+B6+B7 sides t55-64 | duplicate | B6/B7: Z ≤ -1095.300 at t55-62; B6: X ≤ 729.700 at t63-64 |

Alternatives solved: 3. Each combines one surviving option per decision; the best feasible one is kept.

| # | Rows the alternative adds | Outcome | Scene validation | Solves |
|---|---|---|---|---|
| 1 | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≤ 729.700 at t58-64 | feasible | accepted | request 16 feasible, IPOPT Solve_Succeeded, 0.18 s |
| 2 | B6/B7: Z ≤ -1095.300 at t55-57; B6: X ≤ 729.700 at t58-63; B6: Z ≥ -1093.700 at t63-64 | feasible | accepted | request 17 feasible, IPOPT Solve_Succeeded, 0.18 s |
| 3 | B6/B7: Z ≤ -1095.300 at t55-58; B6: X ≤ 729.700 at t59-64 | feasible | accepted | request 18 feasible, IPOPT Solve_Succeeded, 0.19 s |

Solver time for this movement: 0.55 s over 3 requests.

| Family | Switches solved | Best | Not solved |
|---|---|---|---|
| B5+B6+B7: Z- then X- | input 57 (#1: -1093.238); input 58 (#3: -1093.725) | input 57 (#1: -1093.238) | 4 |
| B5+B6+B7: Z- then X- then Z+ | inputs 57, 63 (#2: -1093.238) | inputs 57, 63 (#2: -1093.238) | 0 |

Kept: alternative #2 (the last leg keeps no fallbacks).

