# Why request `5e10cd17` is relaxation-feasible but IPOPT-infeasible

Request `5e10cd17…` comes from the encounter-groups checkpoint (`4fa120e1` plus the p5 run's patch) on the
Real11 extended course. It is one of the 32 p5 requests whose disk relaxation is feasible while no IPOPT run
finds a point. It has 66 ticks, threshold 0, and the objective is position along `customYaw` -41.4° (toward
+X+Z). Files in this folder:
- `request.json`: the captured request;
- `dump.txt`: per-tick rows and the relaxed solution;
- `experiments.txt`: geometry, row names and the IPOPT experiments.

## Short answer

The leg ends with a tiny hop:
1. Stand on B5.
2. Sprint-jump.
3. Bump your head on B22's underside while beside the B6 pillar.
4. Come back down on B5.

Every position from the jump to the landing must lie within a few hundredths of one corner point. The
relaxation does this by using only **38% of the jump input's push**, a gentle hop that brakes the incoming
momentum. Minecraft always applies the full sprint-jump push (0.327 blocks/tick² in the facing direction). With
the full push the player leaves the corner too fast. Air control (0.026 blocks/tick² per input) cannot bring
them back around the corner within the 4 air inputs before landing. With the push scaled to 60% or less IPOPT
solves it every time; at 80% or more it never does.

The landing block does not have "large slack" where it matters. The hop must land at B5's corner, with 0.000
blocks of room on the +X edge and 0.002 on the +Z edge. The 1.0 blocks of room is toward the −Z edge, which
the hop never approaches.

## Geometry

Top view, X to the right, Z upward. Boxes from `extended-anchors-control.json`:

```
  Z
-1094 +-----------+-------------------------------+
      |    B6     |   B22: slab overhead,         |
      |  pillar   |   y 8.5 to 9 (underside 8.5)  |
      |  y 0..20  |                               |
-1095 +-----------*-------------------------------+
      |    B5     |
      | top y = 6 |   (open air below B22)
-1096 +-----------+
     730         731                             734    X
```

| Block | X | Z | Y |
| --- | --- | --- | --- |
| B5 (stand and land here) | 730–731 | −1096 to −1095 | 0–6 |
| B6 (pillar, wall) | 730–731 | −1095 to −1094 | 0–20 |
| B22 (overhead slab) | 731–734 | −1095 to −1094 | 8.5–9 |

Rows constrain the player's centre, so every face is offset by the 0.3 half-width. The point marked `*`, block
corner (731, −1095), becomes the centre-space corner **C = (731.3, −1095.3)**. At C four regions meet:
- south-west: over B5, where the player stands and lands;
- north-west: inside B6, forbidden;
- north-east: under B22, beside B6;
- south-east: open air beside B5.

## What the request schedules (ticks 56–66)

Row names come from the request (`experiments.txt`). Feet height is the vertical schedule's reference
position.

| State | Feet Y | Event | X/Z rows on the centre | Meaning |
| --- | --- | --- | --- | --- |
| 53–60 | (descending) to 6 | land on B5 at 59, ground 59–60 | Z ≤ −1095.3 (avoidance) | stay south of the B6 pillar |
| 58, 59 | 6.08, 6 | landing and support on B5 | X 729.7–731.3, Z −1096.3 to −1095.3 (contact region) | on B5 |
| 60 | 6 | **sprint-jump input** | Z ≤ −1095.3 | still south of B6 |
| 61 | 6.42 | rising | X 731.3–734.3, Z −1095.3 to −1093.7 (contact region), X ≥ 731.3 (avoidance) | **north-east of C**: under B22 and east of B6, so the head can hit B22 |
| 62 | 6.70 | **head hits B22** (feet 6.7 + 1.8 = 8.5) | X ≥ 731.3 | east of B6 |
| 63 | 6.62 | falling | X ≥ 731.3 | east of B6 |
| 64 | 6.47 | falling | X ≥ 731.3, Z ≤ −1095.3 | **south-east of C** |
| 65 | 6.24 | last air state before landing | X 729.7–731.3, Z −1096.3 to −1095.3 | **south-west of C**, over B5 again |
| 66 | 6 | lands on B5 | Z ≤ −1095.3 | |

So the player must:
- be south-west of C (on B5) at the jump;
- be north-east of C one tick later;
- go round to the south-east by state 64;
- be back in the south-west by state 65.

That is a loop around a single point in 5 ticks.

## The relaxed solution

Positions relative to C (dx = X − 731.3, dz = Z + 1095.3), from `dump.txt`:

| State | dx | dz | Input at this state | Push used |
| --- | --- | --- | --- | --- |
| 59 | −0.092 | −0.350 | ground | 99.6% |
| 60 | −0.073 | −0.054 | **jump** | **38.0%** |
| 61 | +0.001 | +0.001 | air | 98.6% |
| 62 | +0.022 | +0.015 | air | 96.8% |
| 63 | +0.021 | +0.012 | air | 92.8% |
| 64 | 0.000 | −0.004 | air | 97.3% |
| 65 | 0.000 | −0.002 | air | 100.0% |
| 66 | +0.026 | 0.000 | (end) | |

The player arrives at the jump moving +Z at 0.16 blocks/tick (velocity (0.010, 0.162)). The jump input pushes
(0.064, −0.107), only 0.124 of the available 0.327, and mostly against that momentum. The jump-tick
displacement shrinks to (0.074, 0.055), which lands the centre on C. From there it hovers within 0.03 blocks of
C until landing. This is the optimum for the objective too: it ends as far toward +X+Z as B5 allows.

## Why the full model cannot do it

Each horizontal input moves the player by displacement = velocity + acceleration. The next velocity is friction
× displacement: 0.546 for the ground and jump inputs, 0.91 in the air. I checked this against the relaxed
numbers: (0.010, 0.162) + (0.064, −0.107) = (0.074, 0.055) = position 61 − position 60.

The full model must use the whole push every input (|a| = 0.327 on the jump input). The facing chooses only
its direction. Whatever the direction, the jump-tick displacement is at least 0.327 − |incoming velocity|.
The only way to keep it small is to arrive fast and push straight against the motion. The incoming velocity is
limited by the landing on B5 and the single ground input before the jump.

The relaxation needs a jump-tick displacement of just 0.092 blocks, aimed exactly at C, followed by four air
inputs of at most 0.026 each to loop round C. The experiments below show the full push cannot be absorbed that
way: the hop works with up to 60% of the push and fails from 80%. I didn't derive a closed-form bound.

Measured with IPOPT, from the confirmation seed plus 3 random seeds per case (`experiments.txt`):

| Change to the request | IPOPT runs feasible |
| --- | --- |
| none | 0 of 4 (`Infeasible_Problem_Detected`) |
| jump push scaled to 0.80 × 0.327 | 0 of 4 |
| jump push scaled to 0.60 × 0.327 | 4 of 4 (hovers at C like the relaxation) |
| jump push scaled to 0.30–0.50 × 0.327 | 4 of 4 at each scale |
| rows at state 61 removed (no head-hit region) | 4 of 4 (moves steadily +Z, never loops) |
| rows at states 62–64 removed | 0 of 4 |
| rows at states 61–64 removed | 4 of 4 |
| landing rows at state 65 removed | 4 of 4 (drifts east along Z ≈ −1095.3) |

- The failure needs the loop: being north-east of C at state 61 and back over B5 at state 65.
- The failure disappears when the jump's push is small enough.
- Removing the side rows around B6 at states 62–64 doesn't help.

The p5 seed study adds the request's own seed, a cold seed and 8 random restarts, and none found a point
(`../p5-study-summary.txt`). IPOPT is a local solver, so this isn't a proof of infeasibility. The push
scaling gives the physical reason.

## What this means for the alarm

- The relaxation's only tell is the braked jump input, 62% of its push unused. Every other input is at 93% or
  more. The current alarm fires on exactly that, a jump tick more than 1% short, which is why all 32 such p5
  requests are confirmed by IPOPT and rejected.
- A landing-room check misses it:
  - the binding constraints are the head-hit region and the return over B5, all within 0.03 blocks of C;
  - the landing itself sits on B5's corner;
  - the 1.0 blocks of room toward −Z is irrelevant to the loop.
- The 32 requests are variants of this same leg: 66–69 ticks, the hop's jump at input 60, 61 or 62, with 28%
  to 76% of the jump push unused (`../jump-shortfall.txt`).
- Whether a non-sprint jump could make this hop is not tested: the request fixes sprinting, so the jump push
  is always 0.327. The scaling runs only suggest that a push of 0.196 or less would fit.
