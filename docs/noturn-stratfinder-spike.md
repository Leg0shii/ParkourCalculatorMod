# No-turn stratfinder spike (ARCH-2 outer layer, issue #424)

A first in-game spike of the OUTER no-turn stratfinder from #424: given a jump STRUCTURE
(space positions, run-tick placement, ground/air pattern, precise constraints) with the
inputs and angles stripped, search the KEY schedule for a byte-exact NO-TURN (a single
settable facing across the whole run-up, then one turn), optionally with a jump-angle (ja)
on the last jump tick.

This is the layer #422 (the inner certified byte-exact angle solver) parked as its follow-up.
It CALLS the shipped inner engine as its byte-exact oracle: for a candidate key schedule the
finder builds a `JumpSpec` (candidate keys + a dF = 0 chain over the no-turn ticks + the
landing walls + free-start box + objective) and runs the normal solver graph
(`GraphRunner` over fold driver + wall homotopy + certified B&B + free start). A result counts
only when `JumpConstraintCompiler.maxViolation <= 0` on `ExactJumpModel` (byte-exact,
FEAS_TOL = 0). Nothing here re-derives physics; the inner engine is the truth.

## Using it in-game

The Angle Solver window has a **Find No-Turn** button.

The human defines the structure (the same way #424 specifies it):
- **no-turn ticks**: mark each run-up tick with a `dF = 0` constraint (constant facing). The
  finder ties those ticks to one shared facing theta. A jump tick left un-tied becomes the
  ja tick (a free jump-angle) instead of a pure no-turn.
- **jumps**: press JUMP on the rows where the player jumps (the spaces).
- **constraints**: the landing walls (X / Z, single value or IN range) on the momentum landings
  and the final landing, plus the objective (MAX / MIN X or Z at the landing tick).
- **free-start box**: an `X IN [lo,hi]` + `Z IN [lo,hi]` range on the start tick (tick 0).

Press Find No-Turn. The search runs on a background thread (status shows in the window and the
HUD). When it lands a byte-exact no-turn it writes the keys and yaws into the tick table, moves
the start into the free-start box, and re-simulates so it is ready to replay.

## Architecture (`core/.../anglesolver/noturn/`)

- `NoTurnKeys`: the 9-combo alphabet {NONE, W, WA, WD, A, D, S, SA, SD} (cardinal x0.98 /
  diagonal x1.0), the sprint single-engage latch, edge counting.
- `NoTurnProblem`: derives the search problem from the compiled `JumpSpec` + `ExactJumpModel`:
  the no-turn (dF = 0) ticks, the jump ticks, the ground/air pattern, the landing walls, the
  free-start box, the objective, and `setupEnd` (last space). `buildSpec(combos, sprint, ...)`
  turns a candidate schedule back into a certifiable `JumpSpec`.
- `NoTurnModel`: the fast clamp-free arc filter substrate. Movement is
  `u_t = mMag(t)*e^{i(baseArg(t)+theta)}`, exactly linear in (a,b) = (cos theta, sin theta) for a
  no-turn, so each landing wall is a half-plane arc on the unit circle. Per-tick magnitude table
  matches `JumpLinearModel`.
- `NoTurnScreen`: a byte-exact full-jump screen: sweep the setup theta, aim the turn at the
  landing target (single-aim homing), forward through `ExactJumpModel`, and measure the landing
  violation with the best free-start translation. Orders candidates by real feasibility.
- `NoTurnCertifier`: the byte-exact oracle: runs the inner solver graph over a candidate spec
  and recovers the free start; feasible iff `maxViolation <= 0`.
- `NoTurnFinder`: the driver: a beam search over the alphabet (arc-filtered, sprint-latched,
  edge-capped, family-diversified), the full-jump screen, then a certify ladder that climbs edge
  levels and stops at the easiest level that yields a byte-exact no-turn. Ranks pure over ja,
  fewest edges, best objective. Falls back to a jump-angle when no pure no-turn certifies.

Tick indexing follows the repo rule: a wall on tick n reads `pos[n]` (pre-tick state).

## What works, and the honest limit

The machinery is verified: given a key family for the j1150 structure the finder certifies a
byte-exact pure no-turn at **X@49 = -2805.2996** through `ExactJumpModel` (a hair past the COPT
proof-of-concept's -2805.2990460856336), so "find the no-turn INCLUDING inputs, byte-exact" is
real, and it applies in-game and re-simulates.

Fully-cold discovery of the tightest multi-jump structures is the same wall the COPT PoC and the
Step-1 study documented as still open (see the recovered `NOTURN-HANDOFF.md` section 4c and
`ARCH2-STEP1-SIMPLIFY.md` H6). On j1150 the beam local-traps on one run-up family (a straight
back-then-forward S/W shuffle that clears the momentum pads clamp-free but never completes the
jump byte-exactly) and does not surface the human's diagonal SA/WD family; the COPT run needed a
global MILP with wall homotopy and a structure-pool (no-good-cut) enumerator to cross that basin.
That pool driver is the remaining #424 build; this spike is the front end and the byte-exact
oracle it plugs into.

## Recovered assets (they were stripped from the tree)

The #424 assets were deleted as campaign scratch in the #422 implementation PR (commits
`7936257` handoffs, `752dfac` `research/copt`, `15fd625` design records) and are gitignored, so
they are not re-committed here. They are recoverable from the pre-deletion commits:
- `git show 06f794f6:docs/research/global-solver-2026-08/NOTURN-HANDOFF.md` (and
  `ARCH2-STEP1-SIMPLIFY.md`, `DESIGN.md`)
- `git show 6d764632:research/copt/noturn_miqcp.py` (and `inner_fixed.py`, `noturn_slp.py`,
  `coptlib.py`, ... the COPT no-turn pipeline)

## Next steps

1. The structure-pool driver (one-tree no-good-cut enumeration over run-up families + wall
   homotopy per candidate) to escape the single-family beam trap: the measured-missing piece.
2. A stronger reachability screen (turn-cone reachable set at the final landing) so straight-line
   run-up families that cannot complete the jump are rejected before the certify.
3. Run-tick outer enumeration (0-5 before jump 1, 0-1 between jumps) around the inner solve.
