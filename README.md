# Parkour Calculator

A TAS input planning mod for Minecraft. Simulate and visualize parkour movements before executing them.

## Features

- Plan movement inputs tick-by-tick
- Visualize predicted path in the world
- Drag start position to test different setups
- Pin windows for quick access

## Installation

Releases ship one jar per loader. Grab the matching file from the [latest release](https://github.com/Leg0shii/ParkourCalculatorMod/releases/latest) and follow the section for your loader. `<version>` below is the release tag without the `v` prefix (e.g. `1.0.0`).

### Fabric 1.21.10

1. Install the [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 1.21.10.
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) into your `mods` folder.
3. Download `pkc-fabric-1.21.10-<version>.jar` and drop it into the same `mods` folder.
4. Launch the 1.21.10 Fabric profile.

### Forge 1.8.9

1. Install [MinecraftForge for 1.8.9](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.8.9.html) (no additional APIs required).
2. Download `pkc-forge-1.8.9-<version>.jar` and drop it into your `mods` folder.
3. Launch the 1.8.9 Forge profile.

### Forge 1.12.2

1. Install [MinecraftForge for 1.12.2](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html) (no additional APIs required).
2. Download `pkc-forge-1.12.2-<version>.jar` and drop it into your `mods` folder.
3. Launch the 1.12.2 Forge profile.

After launch, open the in-game **Mods** menu to confirm Parkour Calculator is listed.

## Usage

Press `K` to open the calculator. The toggle key is rebindable in Minecraft's **Controls** menu (Fabric and Forge both register it under the `Parkour Calculator` category).

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
| `K` | Toggle UI (rebindable) |
| `ESC` | Close UI |
| `Ctrl+Click` | Toggle selection |
| `Shift+Click` | Range select |
| `Right-Click` | Context menu |

## Building from Source

Requires JDK 21.

```bash
./gradlew :loader-fabric-1.21.10:build
```

The output JAR lands in `loader-fabric-1.21.10/build/libs/`.

The repo is a Gradle multi-module project: `core/` holds Minecraft-free UI code (Java 8 compatible), and `loader-fabric-1.21.10/` is the Fabric mod itself. See `CLAUDE.md` for architecture details.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the full workflow. Quick summary: feature branches off `main`, PR titles use [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `feat!:`), squash-merge only. Versioning, tagging, CHANGELOG entries, and publication of the three loader jars are automated via [release-please](https://github.com/googleapis/release-please).
