# UI preview

Runs the real PKC ImGui UI outside Minecraft: the same `core` windows, `ThemeManager` theme, JetBrains Mono fonts and preset scales the loaders use, on a plain GLFW window. It is a dev tool for checking a UI change or a mockup against the real UI, on a desktop or headless in a cloud container, and it is not shipped in any jar.

The game is stubbed: `PreviewGame` answers the `MinecraftAccess` port (no world, the cursor and modifier keys from the window or the script) and `PreviewSimulator` moves the player on flat ground with simplified vanilla movement. Positions and velocities in the table are plausible numbers, not Minecraft's; the solver itself is the real one and runs when Solve is pressed. Replays, block placing and anything else that needs the game do nothing.

## Run it

```bash
ui-preview/preview                                   # Linux or macOS, or a cloud container
ui-preview\preview.cmd                               # Windows
ui-preview/preview --view AngleSolver --shot build/ui-preview/solver.png
```

The launcher builds the module with `-PcoreOnly` (only `core`, `forge-core`, `gpu` and `ui-preview` are configured, so no Fabric, Forge or Minecraft download is needed) and starts it with every argument passed through. `./gradlew -PcoreOnly :ui-preview:run --args="..."` works too.

When Gradle cannot start (a cloud container that cannot reach the Gradle Plugin Portal fails on the Stonecutter plugin), `ui-preview/preview` falls back to plain `javac`: it downloads the ten jars it needs from Maven Central into `~/.cache/pkc-ui-preview/libs` (or the folder in `PKC_PREVIEW_LIBS`), compiles `core` and `ui-preview` into `ui-preview/build/nogradle` and runs from there. It recompiles only when a source file changed. `PKC_PREVIEW_NO_GRADLE=1` skips the Gradle attempt.

On Linux without a `DISPLAY` the preview restarts itself under `xvfb-run` with Mesa's software renderer and runs headless, so the same command works in a cloud container. `--headless` forces that mode anywhere: no visible window, the script runs, then the program exits. Without a script a headless run saves `build/ui-preview/preview.png`.

By default it loads a bundled TAS (HPK j315 as captured, with its solver result), opens the main window and keeps all data (settings, saves, undo history) in a fresh temp folder, so nothing on the machine is touched. `--settings` starts from a copy of a real `parkourcalculator.json`, `--tas` loads any TAS save, `--ini` starts from an ImGui layout such as the game's `parkourcalculator.ini`, so windows sit where they sit in game. Each of these is copied first; the original files are never written.

## Options

| Option | Effect |
| --- | --- |
| `--size WxH` | Window size in pixels, default 1920x1080 |
| `--scale S` | `auto` or a preset scale: 0.75, 1, 1.25, 1.5, 2, 2.5 |
| `--view A,B` | Open windows by their `Settings.view*` flag, `-NAME` closes one (`--list-views` prints them) |
| `--tas FILE` | TAS save to load, `none` for an empty TAS |
| `--mc VERSION` | Game version the UI acts as, default the TAS's version, else 26.3 |
| `--settings FILE` | Start from a copy of a `parkourcalculator.json` |
| `--saves DIR` | TAS folder the file browser lists; its JSON files are copied into the temp folder, the folder itself is never written |
| `--ini FILE` | ImGui layout to start from |
| `--background V` | `#RRGGBB` or an image shown behind the UI, for example an in-game screenshot |
| `--do "CMD; CMD"` | Script commands separated by `;` |
| `--script FILE` | Script commands, one per line, `#` starts a comment |
| `--shot FILE` | Screenshot at the end of the script |
| `--headless` | No visible window, exit after the script |

## Script

Coordinates are window pixels from the top left. Every step renders at least one frame, so a click is one frame with the button down and one with it up, exactly as ImGui sees a real click. A visible window hands the mouse and keyboard back to the user when the script ends.

| Command | Effect |
| --- | --- |
| `view NAME`, `view -NAME` | Open or close a window |
| `set FIELD VALUE` | Set any `Settings` field (boolean, number, string, enum) |
| `scale S` | Change the UI scale |
| `ui open`, `ui closed` | Full UI or only the pinned panels |
| `select A[-B]` | Select TAS rows A to B (1-based) |
| `move X Y`, `click X Y [left\|right\|middle]`, `rightclick X Y`, `doubleclick X Y` | Mouse |
| `press [BUTTON]`, `release [BUTTON]`, `drag X1 Y1 X2 Y2`, `scroll DY` | Mouse |
| `hold ctrl\|shift\|alt`, `release ctrl\|shift\|alt` | Modifiers |
| `key NAME` | One key press, with modifiers as `ctrl+z`; letters, digits, f1 to f12, enter, escape, tab, backspace, delete, insert, space, arrows, home, end, pageup, pagedown |
| `type TEXT` | Types into the focused text field |
| `frames N`, `wait MS` | Keep rendering (`wait` lets hover tooltips appear) |
| `windows` | Print every window's name, position and size |
| `place NAME X Y [W H]`, `focus NAME` | Move, resize or raise a window |
| `shot FILE [window NAME \| X Y W H]` | Save a PNG of the screen, one window or a rectangle |
| `exit` | Stop |

A window `NAME` is matched against the names `windows` prints, ignoring case, spaces and `#`, so `Angle Solver` finds `###angle_solver`. A window keeps its last rectangle after it is closed, so crop only open windows.

Open the Smooth mode menu of the Angle Solver and save that corner:

```bash
ui-preview/preview --view AngleSolver,-TickInfo \
  --do "place 'Angle Solver' 660 16; click 910 413; shot build/ui-preview/smooth-menu.png 640 0 800 700"
```

To find coordinates, take a full screenshot first and read them off the image, or print `windows`.
