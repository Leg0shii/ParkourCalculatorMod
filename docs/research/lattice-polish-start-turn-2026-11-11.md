# Lattice polish: the start facing one full turn away, 2026-11-11

Follow-up to `lattice-polish-facing-rows-2026-11-11.md`. That record left a grid loss of 2e-5 to 5e-5 on several
jumps and named the lever: the same direction one full turn away uses other cells of the game's sine table. This
change lets the stage move the whole jump one turn, and carries the turn through the engine, the plan and Apply.

## Ruling: its own option, off by default

User ruling, 2026-11-11: a TAS may use facings one full turn away, but only as its own setting, and that setting is
off by default in every tier, Optimize included. A start facing a turn away is ugly to watch and the gain is small,
so whoever wants it switches it on.

- `AngleSolverState.fullTurnStart`, the `Full turn start facing` checkbox under More options of the Angle Solver
  window, saved per TAS as `fullTurnStart` (absent means off).
- `AngleSolverEngine.buildJob` sets `JumpPhysicsInputs.startTurnFree` only with the option on. Everything below
  ("Where the turn lives", "Method", the engine part) describes a solve with the option on.
- With the option off the solver never moves a turn. A pinned start keeps the stored start yaw whatever turn it is
  in, so a TAS that was applied a turn away stays there until the start facing is edited. A free start facing is
  written into the base turn, as before this change.
- The node parameter `turns` stays (on by default). It switches the search inside the stage and does nothing
  without the option.
- The numbers under "Results" are all measured with the option on. With it off the stage gives the results of the
  facing rows change.
- The stage runs in the Optimize and Custom graphs only (second ruling of 2026-11-11,
  `lattice-polish-2026-11-10.md`, "Where the stage runs"). A Fast solve does not carry it, so the option does
  nothing there.

## Why a full turn changes the jump

The game reads `sin` at table index `(int) (f * 10430.378F) & 65535` and `cos` at
`(int) (f * 10430.378F + 16384.0F) & 65535`, with `f` the facing in radians as a float. The `(int)` cast rounds toward
zero. For a facing above 0 degrees both indices round down. Below -90 degrees both round up. Between -90 and 0 the
sine index rounds up and the cosine index rounds down, so the two values come from neighbouring cells and the
direction vector `(sin, cos)` is short by up to 4.8e-5 of its length (table step 9.6e-5 rad times `|sin 2f| / 2`).
Every tick that accelerates along such a facing loses that share of its acceleration.

The same direction at +360 degrees is a positive facing and at -360 degrees a facing below -90, so both have the
full length. A jump whose ticks face (-90, 0) gains by starting one turn away, in either direction. Jumps that face
other directions change only by float rounding.

The first measurement moved the stored start yaw of every capture one turn and ran the stage from the closed form
seed: 44 of 57 seeded captures gained, mostly 1e-5 to 5e-5, `hpk/d10/j148` and `j149` 2.6e-4, nearly every HPK neo
jump. -360 and +360 gain about the same.

## Where the turn lives

A TAS row stores its yaw as a float delta from the previous tick, unless the row is yaw locked. The game facing of
tick `k` is the start yaw plus the float deltas so far, so the turn of the whole jump is the turn of the start yaw,
and a start yaw of 315 instead of -45 carries every tick one turn up without any change to the rows beyond float
rounding. The start yaw is taken from the real player yaw, which is unbounded, so jumps in other turns were normal
before (captures wind to 984 degrees).

The candidate therefore stays what it was: wrapped facings. `Scoring.violationOf` wraps every yaw it gets, so a
lifted first facing could not travel in the yaw list anyway. The turn is the scenario's start yaw:

- `JumpPhysicsInputs.startYawAtTurns(turns)` gives the start yaw in another turn, `Angles.wholeTurns` names the
  turn of a facing.
- A free start facing (a dF = 0 row on the first tick) makes the first tick a locked facing in the model, which
  the game reads as an absolute value. `JumpPhysicsInputs.lockedFacing` lifts that one tick by the turn of the
  start yaw. `CertifiedBnb.realizeCombo` uses it for its own leaf replay.
- `JumpPhysicsInputs.startTurnFree` says the solver may choose the turn. `AngleSolverEngine.buildJob` sets it when
  the solve starts on the first tick of the TAS, no row has a locked yaw (a locked row would jump back into the base
  turn) and no absolute F constraint exists (an F = v cell or a one-way turn on the first tick is compiled against
  the base turn). A copy carries the flag, a slice drops it.

## Method

`LatticePolish.polish` runs the stage as before in the turn the scenario is in. With `turns` (on by default) and a
free start turn (the option above) it then repeats the stage with the start yaw at 0, +360 and -360, each time from the best point so
far, and takes a result that is strictly better and keeps every game facing within 720 degrees
(`MAX_FACING`, the standing envelope for wraps). In a new turn the incumbent's own facings are judged first: when
they are feasible there and already better, they count as a result even when the grid search around them finds
nothing more (`taser-80t` gains 1.3e-3 that way).

`LatticePolishNode` writes the result's start yaw into the scenario before it scores the candidate and puts the
old one back when the candidate is not better. The stages after it run in the new turn.

Engine:

- `start` puts the start yaw of a jump with a free start turn into the base turn before the graph runs
  (`homeStartFacing`). The pipeline lands there and the last stage picks the turn, so a second solve after Apply
  starts from the same place and returns the same result. A free start facing was always written into the base
  turn before, so it is put there as well.
- `Plan.startYaw` carries the turn. Apply publishes it through `onStartYawChanged` and writes the rows as deltas
  from it; for a free start facing the start yaw is the first facing plus that turn. The result lists a
  `Start facing turn` detail when the turn is not the base one.
- An adopted incumbent (the previous solve under Optimize, #486, and the Smooth base) brings its turn back
  (`Job.incumbentStartYaw`), otherwise its facings would be judged in the wrong cells.
- `SeedSweepNode` clears the flag on its sub-runs. They are verified against the parent scenario, and only the
  last stage of the parent may move the turn.

## Results

Cloud container, 4 cores, JDK 21, single run each.

### Stage from the closed form seed, turn off against turn on

All captures under `captures/` that the stage accepts and the closed form seeds (57):

| | captures | better with the turn | worse |
| --- | --- | --- | --- |
| free start facing | 28 | 26 | 0 |
| pinned start facing, free turn | 7 | 7 | 0 |
| turn not free | 22 | 0 | 0 |

Total gain 2.49e-3. Typical 1e-5 to 5e-5; `hpk/d10/j148` 2.63e-4, `hpk/d10/j149` 2.29e-4, `taser-80t` 1.26e-3
(79 ticks, stored start yaw 208 degrees, the base turn is the better one there). Every result replays byte-exact.

### Twelve human jumps, a Fast landing plus the stage, solve, Apply, solve again

Measured while the Fast graph still carried the stage. In the engine these results now come from Optimize or from
a Custom graph; the `problems/polish/` cases with `turns` in `engine` mode run this path with a Fast graph that
has the stage added.

| jump | before | with the turn | gain |
| --- | --- | --- | --- |
| j140 | -826.231873309 | -826.231856187 | 1.71e-5 |
| j345 | -660.197872806 | -660.197806168 | 6.66e-5 |
| j1099 | -2501.298719493 | -2501.298692362 | 2.71e-5 |
| j1149 | 4950.794602430 | 4950.794602430 | 0 |
| j1150 | -2805.297967220 | -2805.297958400 | 8.82e-6 |
| j155 | 4984.763186192 | 4984.763186192 | 0 |
| j716 (minimum) | -699.950269410 | -699.950269427 | 1.7e-8 |
| j718 | -1902.861611313 | -1902.861582285 | 2.90e-5 |
| j828 | 4978.013121953 | 4978.013121953 | 0 |
| j925 | 2809.336732908 | 2809.336757148 | 2.42e-5 |
| j154 (minimum) | -1599.700114039 | -1599.700117640 | 3.60e-6 |

j335 does not land in a Fast solve in either build. With the turn every jump returns the same objective on the second and
third solve after Apply. Before, j718 came back with -1902.861583318 on the second solve.

### Capture sweep

`LatticePolishSweep` over all 154 specs, standalone stage from the stored seed:

| | facing rows change | this change |
| --- | --- | --- |
| declined | 2 | 2 |
| seeded | 67 | 67 |
| improved | 62 | 64 |
| start facing one turn away | | 40 |
| violations | 0 | 0 |
| stage time over all seeded specs | 797 ms | 1538 ms |

40 specs are better, 27 the same, none worse, total gain 3.29e-3. The stage runs up to three times on a jump with
a free start turn, which is the added time.

## Limits and open work

- **Under an Optimize budget** the stage has a deadline (5 percent of the budget, at most 2 s). It searches the
  turn the jump is in first and gives the other turns the time that is left. `LatticePolishSweep` with the
  Optimize graph's parameters and the repeated grid search of the change stacked on this one: the turn is used on
  16 of 67 jumps at 150 ms, 20 at 500 ms and 30 at 2 s, against 40 without a deadline. That change runs a quick
  search over every turn first, which brings it to 39 at each of the three.
- **A jump inside the TAS** keeps its turn. Its start yaw is the facing the ticks before it produced, so moving it
  one turn means a row that turns 360 degrees. That needs a ruling first; it is not built.
- **One turn per jump.** Single ticks cannot leave the turn of their neighbours, for the same reason.
- **Playing the jump by hand.** After a reset the player's yaw is in the base turn. A human reaches the start
  facing of such a result only by turning once around before the jump. This is why the option is off by default.
- **`FacingStepNode`** still sorts the first facing of a free start into the base turn's cells when it lists its
  candidates. It only costs that stage some candidates when an incumbent sits in another turn.
- **Tied facings** as in the record before: ranking the grid steps of a tied facing by measured loss is open.
