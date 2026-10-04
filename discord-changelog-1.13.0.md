# Parkour Calculator - Version 1.13.0

### Minecraft 26.3

- The Fabric jar now targets Minecraft 26.3 (Fabric Loader 0.19.5+, Fabric API, Java 25). 26.3 swapped GLFW for SDL3 and moved the renderer to a new GPU layer, so the whole ImGui input layer, the in-world path rendering and the file picker were rebuilt on top of the new APIs. Everything you know from 26.2 works the same: overlay, hotkeys, clipboard, save dialogs, paired sim, replays.
- The 26.2 jar is not built anymore. If you stay on 26.2, keep 1.12.0. Fabric 1.21.3, Forge 1.8.9 and Forge 1.12.2 ship as before.

### Stratfinder: no-turn strats from a jump structure

- New window under **View > Stratfinder**. Give it a jump STRUCTURE instead of a finished setup: mark the no-turn ticks with `dF = 0`, add the landing constraints (`B` on the landing block), set the jump rows and a free-start box. It searches the key schedule (WASD presses and releases, sprint toggles) and the free start for a byte-exact no-turn strat: one settable facing across the whole run-up, then the turn, pure or with a jump-angle. Every strat is verified on the exact physics with zero tolerance, nothing is approximated.
- Strats appear in the list while the search runs. Click one to apply it to the TAS. Columns: number of input changes, sprint state, landing offset past the goal wall, and the key string (`SA x16, WD x22, W`).
- Two rankings: click **Inputs** to sort easiest first (no flick before the jump, then fewest presses and releases, then fewest backward ticks, then smallest turn), click **Offset** to sort furthest first.
- **Optimize** polishes every strat in the list for a per-strat time budget (slider, 1 to 120 s), in parallel, and re-sorts as strats finish. The selected strat is re-applied when it improves.
- **Human yaws**: solve the yaws for a human instead of a TAS. Aims for clearance rather than the last fraction of distance and straightens the turn so it never flicks out and back. Strats stay byte-exact and still land.
- Early development: the Stratfinder is a first version. It handles pure no-turns and single jump-angle strats on one jump with a free start, and it does not find complex strats yet (multi-jump routes, several turns, momentum chains). Expect no result on those for now and report what it should have found.
- Under the hood this is the cold solver: an enumeration front end, a wall-homotopy continuation that widens the landing walls and tracks the strat back down to the real walls, and a combinatorial Benders master that proposes schedules in fewest-edges order. It cracks the j1150 pure no-turn cold (`SA x16, WD x22, W`, 2 edges) and rediscovers the j154 jump-angle strat V6 (`SD x6, S x8, SD, WA, A, WA x11, W`) with no seed and no hardcoded family.

### Angle Solver

- **Fast (multi-start)**: new preset under Custom. Runs Fast once per start seed spread over the free-start box, in parallel on two low-priority threads so the game stays responsive, and keeps the best. Plain Fast is unchanged. On `cross2-good` the sweep reaches the same basin as the best in-game result (X -1909.58011) in about 6 s at the default two threads, where a single Fast landed at -1909.57861.
- Optimize now starts with that sweep and adopts your previous successful solve as its incumbent. It can no longer end worse than the Fast you already had.
- An exact facing target (`F = 33.3`, or a `dF = 0` on the first tick) now solves to exactly the value you typed, and "met" means the realized float facing lands anywhere in the same sine-table cell, which is byte-identical movement. No more "misses by 0.000001" on cell-edge values like 161.9.
- Over-constrained no-turn chains report plainly. When the `dF = 0` chain leaves zero or one free angle, the notice says so ("1 free angle: ... misses by 0.05422") instead of a silent "closest attempt".
- Legal mode on stop-on-feasible tiers keeps the goal wall hard. Before, "feasible" could mean 0.92 blocks short of the wall.
- Solves are bit-identical on Java 8 (Forge) and Java 21 (Fabric). The search path uses StrictMath everywhere; CI now runs the suite on Java 8 too.
- Node graph: every parameter is read and labelled correctly, orphan help entries are gone, `homotopyLadder.cap = 0` means unlimited like the other tick caps.

### More Features

- New `Inv` column (Settings > Visible columns): closes an open inventory or container screen on that tick during playback, on all four loaders. Saved as `CLOSE_INVENTORY`.
- Hit distance line: uses the game's own look vector and the game mode's block reach (5.0 in creative), so the line ends on the block the crosshair actually targets. Before it stopped at 4.5.
- Alt+B merged footprint is clipped by the walls around the union, so merging two landing blocks no longer produces a footprint that runs through a third block. If no clean cut exists the plain union is kept and the HUD says so.

### Bug Fixes

- With the path hidden (`Y`), box drag, the yaw gizmo, box select and the click suppression are off, so item right-clicks reach the server again.
- The start/goal inset bar and the teleport tint no longer draw past the input table border when the range is scrolled partly out of view.
- Solves starting on a `dF = 0` tick take the certified fold path again, and the pool screen accepts run-up-length key arrays.

## Download

- **GitHub release:** https://github.com/Leg0shii/ParkourCalculatorMod/releases/tag/v1.13.0
- **Modrinth:** https://modrinth.com/mod/parkourcalculator

If you find a problem or have an idea for a new function, make a new issue on GitHub.
