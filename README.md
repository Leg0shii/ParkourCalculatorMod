# Parkour Calculator

[![build](https://github.com/Leg0shii/ParkourCalculatorMod/actions/workflows/build.yml/badge.svg)](https://github.com/Leg0shii/ParkourCalculatorMod/actions/workflows/build.yml)
[![release](https://img.shields.io/github/v/release/Leg0shii/ParkourCalculatorMod)](https://github.com/Leg0shii/ParkourCalculatorMod/releases/latest)
[![downloads](https://img.shields.io/github/downloads/Leg0shii/ParkourCalculatorMod/total)](https://github.com/Leg0shii/ParkourCalculatorMod/releases)
[![license](https://img.shields.io/github/license/Leg0shii/ParkourCalculatorMod)](LICENSE)

A TAS input planning mod for Minecraft. Simulate and visualize parkour movements before executing them.

![Angle solver TAS replayed client-sided on a server](docs/media/jump_showcase.gif)

▶ [Creating a TAS Tutorial](https://youtu.be/y6Zqht6fyes)

## TASes created with the tool
- Juku Section (Top 1 Segmented): https://www.youtube.com/watch?v=DuOuvKRXtfw
- Drool City (Top 6 Rankup): https://www.youtube.com/watch?v=hz71P3R8YBo
- Utopica: https://www.youtube.com/watch?v=efh8SUA_13U
- Jumpcraft X: https://www.youtube.com/watch?v=OYNSGP5gSJI
- Jumpcraft XI: https://www.youtube.com/watch?v=Fu1xlAXp9UM
- Bedwars Lobby Parkour: https://www.youtube.com/watch?v=9A_4NfM1F4I

## Features

- **Angle Solver**: give it constraints and it finds the yaw inputs that land the jump
- **Free start position**: the solver picks the best starting spot within the start block's footprint
- **Constraints**: absolute X/Z/F, relative X/Z against any reference tick, per-tick dX/dZ/dF deltas, and a dX vs dZ axis comparison
- **Constraint hotkeys**: `B` adds wall and footprint constraints for the block you are looking at, `Ctrl+B` adds ladder/slime/ice cell constraints
- **Solver hotkeys**: `V` solves and applies, `I`/`O` set the solver start/goal tick to the selected tick, `H` fills per-tick slipperiness and medium from the recorded path; a HUD status line shows solve progress and outcome while the UI is closed
- **Node-graph solver pipelines** with per-stage time budgets (Custom effort)
- Plan movement inputs tick-by-tick (WASD, jump, sneak, sprint, yaw, pitch)
- Visualize the predicted path as boxes in the world, with per-tick movement info (motion, speed, combined XZ distance) and hit distance lines showing block reach
- Drag the start box to test different setups
- Replay planned inputs in-game in singleplayer, with the TAS list following the active tick
- **Client-sided multiplayer playback**: watch a replay of your TAS while on a server (visual only, see below)
- Experimental block capture: pick blocks from the world with their real collision shapes
- Save and load input plans
- Pin windows for quick access
- Supports Fabric on the latest Minecraft (currently 26.2), Forge 1.8.9, and Forge 1.12.2

## Multiplayer

Playback on a server is a client-sided replay: only you can see it, your own player never moves, and nothing is sent to the server. Playback that moves your player is deliberately restricted to singleplayer; on a server that would be macroing, and it will not be added.

## Usage

Press `G` to open the calculator. The toggle key is rebindable in Minecraft's **Controls** menu (Fabric and Forge both register it under the `Parkour Calculator` category).

### Adding Inputs

1. Click key columns to toggle inputs
2. Drag across columns to set multiple keys
3. Enter YAW values for rotation

### Managing Rows

- Right-click to add rows
- Click to select, Ctrl+Click to multi-select
- Shift+Click for range selection
- Delete key to remove selected rows
- Drag rows to reorder

### Moving Start Position

Click and drag the first box in the world to reposition.

## Controls

| Key | Action |
|-----|--------|
| `G` | Toggle UI (rebindable) |
| `ESC` | Close UI |
| `B` | Add wall/footprint constraints for the targeted block |
| `Ctrl+B` | Add ladder/slime/ice cell constraints |
| `Ctrl+Click` | Toggle selection |
| `Shift+Click` | Range select |
| `Right-Click` | Context menu |

## Building from Source

Requires JDK 21 (the Fabric module's JDK 25 toolchain is auto-provisioned).

```bash
./gradlew :loader-fabric:build
```

The output JAR lands in `loader-fabric/build/libs/`.

The repo is a Gradle multi-module project: `core/` holds Minecraft-free UI code (Java 8 compatible), and `loader-fabric/` is the Fabric mod itself. See `CLAUDE.md` for architecture details.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the full workflow. Quick summary: feature branches off `main`, PR titles use [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `feat!:`), squash-merge only. Versioning, tagging, CHANGELOG entries, and publication of the three loader jars are automated via [release-please](https://github.com/googleapis/release-please).

## Installation

Releases ship one jar per loader. Grab the matching file from the [latest release](https://github.com/Leg0shii/ParkourCalculatorMod/releases/latest) and follow the section for your loader. `<version>` below is the release tag without the `v` prefix (e.g. `1.0.0`).

### Fabric (latest Minecraft, currently 26.2)

1. Install the [Fabric Loader](https://fabricmc.net/use/installer/) for the Minecraft version named in the release notes.
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) into your `mods` folder.
3. Download `pkc-fabric-<version>.jar` and drop it into the same `mods` folder.
4. Launch the Fabric profile for that Minecraft version.

### Forge 1.8.9

1. Install [MinecraftForge for 1.8.9](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.8.9.html) (no additional APIs required).
2. Download `pkc-forge-1.8.9-<version>.jar` and drop it into your `mods` folder.
3. Launch the 1.8.9 Forge profile.

### Forge 1.12.2

1. Install [MinecraftForge for 1.12.2](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html) (no additional APIs required).
2. Download `pkc-forge-1.12.2-<version>.jar` and drop it into your `mods` folder.
3. Launch the 1.12.2 Forge profile.

After launch, open the in-game **Mods** menu to confirm Parkour Calculator is listed.
