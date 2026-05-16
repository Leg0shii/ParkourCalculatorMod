# Vision

Parkour Calculator Mod is a Tool Assisted Speedrun (TAS) mod for Minecraft. The player defines movement inputs through an in-game GUI, and the mod simulates the exact resulting path using Minecraft's physics engine, rendering the predicted trajectory directly in the world.

This mod is the successor to the standalone desktop [ParkourCalculator](https://github.com/Leg0shii/ParkourCalculator) (JavaFX, 189 commits, 23 stars). The desktop app proved the core concept across MC 1.8.9 / 1.12 / 1.20 and includes working AI pathfinding, but required rebuilding worlds in an external 3D editor. The mod eliminates that friction by operating directly against the loaded Minecraft world.

The primary audience is the TAS and speedrun community. A secondary use case is parkour practice (visualizing jumps before attempting them), but this is not a priority.

The mod ships for Fabric 1.21.10, Forge 1.12.2, and Forge 1.8.9 in parallel. The Fabric loader tracks the newest Minecraft version; the two Forge loaders exist because the speedrun community is still anchored on those eras.

`docs/ROADMAP.md` is the live status of what's done and what's next. This file describes the longer arc, the algorithms inherited from the desktop predecessor, and the principles that shape day-to-day decisions.


## Phases

The project evolves in three phases. Each phase builds on the previous one and increases the level of automation.

### Phase 1: Manual Input Planning (current)

The player manually defines tick-by-tick inputs (W/A/S/D, Jump, Sprint, Sneak, yaw angle) in an ImGui table overlay. The mod feeds these inputs into a `SimulatorEntity` (a custom `PlayerEntity` or its 1.8.9 / 1.12.2 equivalent), records the resulting positions, and renders the predicted path as boxes in the 3D world. The start position can be dragged to test different setups.

This phase is about building a reliable, ergonomic manual TAS workflow: accurate simulation, fast visual feedback, and save/load so setups can be shared. See `docs/ROADMAP.md` for the remaining work.

### Phase 2: Input Optimization

Instead of the player manually searching for working inputs, they define a goal (land on this block, reach this position) and the mod searches the input space for solutions.

This phase reimplements the two-stage algorithm from the desktop ParkourCalculator:

**Stage 1, A\* block-level pathfinding.** Operates on the block grid. Given a start and end block position, A\* finds a coarse path through the world considering:
- Block types: solid, air, climbable (ladders/vines), swimmable (water/lava).
- Player hitbox clearance: whether the player can physically stand at each position without colliding with blocks above.
- Jump distance constraints: vertical tiers and maximum horizontal reach used to prune unreachable neighbors.
- The output is a list of block positions forming a "boundary corridor" that constrains the next stage.

**Stage 2, multi-threaded input bruteforcing within A\* boundaries.** Multiple bruteforcer threads run in parallel, each:
1. Generating random input tick sequences using an `InputGenerator` with configurable per-key probabilities (W/A/S/D/Jump/Sprint/Sneak/yaw change chance).
2. Simulating each sequence through the actual physics engine.
3. Building a `ticksMap`: a map from discretized positions to the shortest input sequence that reached them. This effectively grows a tree of reachable states.
4. Starting new trials from existing reachable positions (tree extension), not always from scratch.
5. Checking whether any simulated position lands on the target block.

Threads periodically sync their ticksMaps and fastest solutions. A windowed exploration mode focuses the search on specific tick-depth ranges, progressively advancing the search frontier.

The desktop implementation lives in `ParkourCalculator/src/main/java/de/legoshi/parkourcalculator/ai/`. Key classes: `AStarPathfinder`, `BlockNode`, `Bruteforcer`, `MultiThreadBruteforcer`, `SimpleBruteforcer`, `InputGenerator`, `BruteforceOptions`. The mod will reimplement this algorithm, not port the code directly, adapting it to each loader's world access patterns and the mod's existing `SimulatorEntity` simulation model.

**Planned features:**
- Position bruteforcing: define a target block and search for input sequences that land on it.
- Distance checking between player hitbox and block hitboxes as a fitness / validation function.
- Constraint-based input search: specify constraints beyond just "land here" (e.g., minimum speed on arrival, specific facing angle).
- UI for configuring bruteforce parameters (trial count, tick depth, key probabilities, thread count, sync interval).
- Progress visualization: show the expanding tree of explored states in real-time.

### Phase 3: Autonomous Pathfinding

Given a start and end position in the loaded world, the mod generates a complete movement sequence: not a single jump, but an entire multi-jump route across complex terrain.

This is a full rewrite of the desktop ParkourCalculator's pathfinding, not a port. Lessons from the desktop version:
- The A\* stage works well for coarse routing, but the block-level grid doesn't capture sub-block precision needed for tight jumps.
- The bruteforcer's random sampling is effective but slow for long routes. The rewrite should explore more directed search strategies.
- Operating against real loaded world geometry instead of a reconstructed editor environment simplifies block lookup and collision detection significantly.
- The desktop version's pathfinding achieves route quality comparable to world-record speedruns (demo: https://www.youtube.com/watch?v=-zVK3DKpgr4). The rewrite should match or exceed this.

**Planned features:**
- A-to-B route generation on loaded world geometry.
- Route visualization and editing: the player can inspect and modify individual segments of a generated route.
- Route export as a playable macro.


## Design Principles

- **Simulation accuracy over speed.** The mod uses a real `PlayerEntity` subclass (or its 1.8.9 / 1.12.2 equivalent) for simulation. Any shortcut that diverges from Minecraft's actual physics engine is a bug.
- **In-game first.** Everything happens inside Minecraft. No external tools, no alt-tabbing.
- **Minimal dependencies.** Fabric API on Fabric; nothing extra on Forge. imgui-java for the overlay. No heavyweight frameworks.
- **Client-side only.** The mod never touches the server. It reads world state but never modifies it.
- **Community interop.** Save/load uses mothball-string notation so setups roundtrip with Stratfinder and other Mothball tooling.
