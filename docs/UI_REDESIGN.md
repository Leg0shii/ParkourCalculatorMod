# UI Redesign (v1.3.0)

Design reference for the v1.3.0 release. Information architecture, user flows,
visual tokens, defaults. Implementation follows the phase plan in
`C:\Users\benja\.claude\plans\what-is-the-best-twinkly-wozniak.md`.

This doc is the contract between design and code. If the running app contradicts
it, fix the app or change the doc. Do not let them drift.


## Why this release

The current UI has five independent overlays (Parkour TAS, Settings, Files, Tick
Info, Perf) plus a control-panel toggler. Each was added when its feature
landed, none share a theme, defaults are whatever was hardcoded that day, and
the file browser is a free-floating window. World join auto-seeds 10 empty
ticks, which is a leftover from early prototyping and surprises new users.

V1.3.0 is structural cleanup. No new simulation features.


## Information architecture

One root window. Top of the window is a menu bar; below it is the body.

```
+--------------------------------------------------------+
| File   Edit   View   Settings   Help                   |  <- menu bar
+--------------------------------------------------------+
|                                                        |
|  [body: input table, or empty-state CTA]               |
|                                                        |
+--------------------------------------------------------+
```

Sub-panels (Tick Info, Perf) toggle from the `View` menu. When visible they
dock to the right side of the main window, not as separate OS-level windows.
The control panel and per-overlay pinning system are removed.

### Menu contents

```
File
  New TAS              Ctrl+N
  Open...              Ctrl+O
  Open Recent          >  (submenu, last 5)
  -----
  Save                 Ctrl+S
  Save As...           Ctrl+Shift+S
  -----
  Import .tas...
  -----
  Delete current TAS

Edit
  Undo                 Ctrl+Z
  Redo                 Ctrl+Y
  -----
  Add row              Insert
  Delete selected      Delete
  Duplicate selected   Ctrl+D
  -----
  Clear all rows

View
  Tick Info            (checkable)
  Performance          (checkable)
  -----
  Show potion columns  (checkable)
  Show yaw arrows      (checkable)
  Show hitbox          (checkable)
  Show full hitbox     (checkable)
  Subtick visualization (checkable)

Settings
  Preferences...       (opens modal)

Help
  Open save folder
  Report bug
  -----
  About
```

Rationale: the View menu surfaces the four most-toggled visualization booleans
so users do not need to open the Settings modal for them. Color and slider
settings stay inside the modal.


## User flows

### New TAS

1. `File > New TAS` (or `Ctrl+N`).
2. If current TAS is dirty, prompt: `Discard unsaved changes? [Discard] [Cancel]`.
3. Name modal: text input, Save / Cancel buttons. Empty name is rejected with
   inline error. Duplicate name prompts overwrite confirmation.
4. On Save: clear input data, reset start position to player, set `currentName`,
   show input table.

### Open

1. `File > Open` (or `Ctrl+O`).
2. Modal lists saved files (name, date, MC version, world label), sortable.
3. Single-click selects, double-click loads. Dirty prompt applies.
4. On load failure: inline error in modal with the validator's reason.

### Open Recent

1. `File > Open Recent` opens a submenu of the last 5 files (most recent first).
2. Click a name to load. Dirty prompt applies.
3. The recent list persists across sessions in the settings file. New saves
   push to the front; loads also push to the front. Cap at 5.

### Save / Save As

- `Save`: writes to `currentName`. If no `currentName` (TAS was created without
  naming), behaves as `Save As`.
- `Save As`: name modal; same rules as New TAS.

### Import .tas

1. `File > Import .tas`.
2. OS file picker opens (Java `JFileChooser` via `FilePickerPort`). Filter:
   `*.tas`.
3. On selection: parse with `SaveIO.load(...)` against an ephemeral path.
4. Success: copy file into save dir, push to recent list, load into editor,
   show toast `Imported: <filename>`.
5. Failure: dialog `Could not import: <validator reason>`. No state change.

### Delete

1. `File > Delete current TAS`.
2. Confirm modal: `Move <name> to recycle? [Move] [Cancel]`.
3. On confirm: move file to `<save dir>/.trash/`, clear `currentName`, fall to
   empty state.

### Empty state

When `currentName == null` AND `inputData.size() == 0`, the body renders an
empty-state panel instead of the input table.

```
+--------------------------------------------------------+
| File   Edit   View   Settings   Help                   |
+--------------------------------------------------------+
|                                                        |
|                  Parkour Calculator                    |
|                                                        |
|              [   + New TAS (Ctrl+N)   ]                |
|              [    Open... (Ctrl+O)    ]                |
|                                                        |
|              Open Recent:                              |
|                * my-run-1            (2 days ago)      |
|                * shortcut-route      (5 days ago)      |
|                * ...                                   |
|                                                        |
|        Tip: input rows seed only after you create      |
|        or open a TAS. No auto-load on world join.      |
|                                                        |
+--------------------------------------------------------+
```

Empty state is reachable from: world join, `File > New TAS > Cancel` after
discard, `File > Delete current TAS`, and `Edit > Clear all rows` followed by
closing the current TAS.

### World join

`Application.onWorldChange()`:
- Clears `inputData` (no auto 10 rows).
- Sets `currentName = null`.
- Resets start position to player.
- The body renders the empty-state CTA on next frame.

No modal pops on world join. The CTA is the prompt.


## Design tokens

Centralized in `ThemeManager`. Every overlay reads from these. No
literal RGBA arrays anywhere else in `core/ui/`.

### Palette: Catppuccin Mocha

Source: https://catppuccin.com/palette/. Picked for subtle alt-row banding
(~6% lift between base and surface0), legible muted text, and pastel accents
that don't fight the data.

| Token           | Hex     | Use                                                     |
|-----------------|---------|---------------------------------------------------------|
| `bg`            | `#1e1e2e` | Window background, default table row                  |
| `bg_dark`       | `#181825` | Title bar, popup background, table header             |
| `panel`         | `#313244` | Frame fill (inputs/buttons), alt-row banding          |
| `panel_hover`   | `#45475a` | Row/widget hover background                           |
| `panel_active`  | `#585b70` | Pressed/active fills (replaces blue tints on click)   |
| `border`        | `#45475a` | Borders, separators, frame outline at rest            |
| `text`          | `#cdd6f4` | Primary body text                                     |
| `text_muted`    | `#a6adc8` | Secondary, disabled labels                            |
| `text_dim`      | `#6c7086` | Very-low-emphasis text                                |
| `accent`        | `#89b4fa` | Primary buttons, slider grab, focus ring              |
| `accent_dim`    | `#89b4fa @ 0.30` | Drag-source row tint                           |
| `selected`      | `#cba6f7` | Selected row tint (mauve, distinct from warning)      |
| `warning`       | `#f9e2af` | Dirty marker, drop indicator, playback tick           |
| `danger`        | `#f38ba8` | Destructive actions, error text                       |
| `ok`            | `#a6e3a1` | Success toast / status text                           |
| `focus`         | `#89b4fa @ 0.30` | 2px outline on focused inputs                  |

Render-color tokens (in-world boxes, gizmos) remain user-customizable in the
Settings modal and keep their current defaults from `Settings.java`. They are
*not* part of the UI palette above; they are render data.

### Spacing scale

```
xxs  2px
xs   4px
sm   8px
md  12px
lg  16px
xl  24px
```

`ImGuiStyle` mapping:
- `WindowPadding`        = `lg, lg`
- `FramePadding`         = `md, sm`  (bumped from `sm, xs`; buttons/inputs need vertical breathing room)
- `ItemSpacing`          = `sm, sm`  (vertical was `xs`; rows were squashed against each other)
- `ItemInnerSpacing`     = `xs, xs`
- `CellPadding`          = `sm, xs`  (was `xs, xxs`; input table cells were cramped)
- `ScrollbarSize`        = `18px` (raised above `md`; see Component sanity rules)
- `GrabMinSize`          = `md`

### Borders and radii

- Window border          = 1px, `border`
- Frame border           = 0px (flat)
- Frame rounding         = 3px
- Window rounding        = 4px
- Tab rounding           = 3px
- Scrollbar rounding     = 3px

### Font

Keep ImGui default font. UI scale slider already exists (presets:
0.75 / 1.0 / 1.25 / 1.5 / 2.0 / 2.5). Default stays at 1.5x (`scaleIndex = 3`).


## Component conventions

### Buttons

Three styles. Implementation is `ThemeManager.pushButton(Style)` /
`popButton()`.

- **Primary** (`accent`): main action in a modal. One per modal max.
- **Secondary** (default): everything non-destructive.
- **Danger** (`danger`): Delete, Discard, Move-to-recycle. Always paired with
  a secondary Cancel.

Modal button strip: right-aligned, primary/danger rightmost, Cancel left of it.
Spacing between buttons: `sm`.

### Modals

Standard layout:

```
+--------------------------------------+
| Title                            [X] |
+--------------------------------------+
|                                      |
| body content                         |
|                                      |
+--------------------------------------+
|                  [Cancel] [Primary]  |
+--------------------------------------+
```

- Open with `ImGui.openPopup(id)`, render with `ImGui.beginPopupModal(id, ImGuiWindowFlags.AlwaysAutoResize)`.
- Esc closes (Cancel semantics) unless the modal owns destructive state.
- Helper: `ModalUtil.buttonStrip(primaryLabel, primaryStyle, onPrimary, onCancel)`.

### Window sizing

Canonical patterns. Pick one per surface, do not mix.

- **Top-level windows** (MainWindow, future Open TAS list): seed a default with `setNextWindowSize(W, H, FirstUseEver)` and pin a floor with `setNextWindowSizeConstraints(minW, minH, MAX, MAX)`. Never combine these with `AlwaysAutoResize`. The user resizes; ImGui remembers via the .ini.
- **Modals and transient popups** (Preferences, confirm dialogs, name-input, About): use `AlwaysAutoResize` and let content drive the size. No default size, no constraints. Switching tabs inside a modal grows/shrinks the window to fit the active tab.
- **Inner widget widths** inside layout tables: prefer `setNextItemWidth(-1)` (fill the cell) over hardcoded pixel widths. Use `getContentRegionAvail().x` when the available width needs to drive layout math.

Current sizes (update here when changed):
- MainWindow: default 960x640, min 720x420.
- Preferences modal: content-driven via `AlwaysAutoResize`.

### Tooltips

All tooltips go through `TooltipUtil.wrappedTooltip(String text)`. The helper:

1. Calls `ImGui.beginTooltip()`.
2. Sets text wrap pos to `350px` from the cursor.
3. Renders the text.
4. Calls `ImGui.endTooltip()`.

ImGui's native tooltip positioning already clamps to viewport on modern builds,
but the wrap is the part that is missing today. Wrap width `350px` is large
enough for two readable sentences on a 1080p screen at 1.5x UI scale.

Call site replaces every `ImGui.setTooltip(...)` with `TooltipUtil.wrappedTooltip(...)`.

### Inline status messages

Below the relevant input, one line, colored by token:
- `ok` for success ("Saved").
- `danger` for error ("Name cannot be empty").
- `text_muted` for hints ("Press Enter to save").

Toasts (top-right floating) only for transient confirmations: Import success,
Save success after auto-prompted name. Auto-dismiss after 2.5s.

### Tables

Use `ImGuiTableFlags.SizingFixedFit | RowBg | ScrollY | BordersInnerH`. Alt row
banding via `panel_alt`. Selected row fill via `accent_dim`. No bespoke column
headers; the input table's transparent header is the existing pattern and
stays.

### Header / collapsible sections

When a body has more than one section (Settings modal), use tabs
(`ImGui.beginTabBar`) at the top of the modal, not collapsing headers. Tabs are
discoverable; collapsing headers hide content.


## Defaults

Every default value the app ships with, in one table. If the running app
contradicts this, the app is wrong.

### General

| Setting                 | Default |
|-------------------------|---------|
| UI scale                | 1.5x    |
| Pinned overlays         | [] (none) |
| Recent files cap        | 5       |

### Visualization toggles

| Setting                  | Default |
|--------------------------|---------|
| Show yaw arrows          | on      |
| Show hitbox              | off     |
| Show full hitbox         | off     |
| Subtick visualization    | on      |
| Show potion columns      | off     |

### Playback

| Setting                          | Default     | Min  | Max   |
|----------------------------------|-------------|------|-------|
| Max yaw turn rate (deg/s)        | 720         | 30   | 7200  |
| Path render distance (blocks)    | 128         | 16   | 512   |
| Unlimited path render distance   | off         | -    | -     |

### Render colors

Keep existing defaults from `core/.../ui/Settings.java`. Re-document here only
if a value changes during v1.3.0. They are not changing in this release.

### Input data

| Property                          | Default |
|-----------------------------------|---------|
| Rows on `InputData` construction  | 0       |
| Rows on world join                | 0       |
| Rows on `File > New TAS`          | 0       |

Row addition is always explicit, via the toolbar or `Edit > Add row`.

### Window placement

First-run window position: top-left, offset `lg` from screen edges. Width:
800px. Height: 600px. Saved across sessions in settings file.


## Component conventions: input editor

The input table moves into `MainWindowOverlay` body as the primary panel. The
existing widgets (key cells, yaw input, drag-and-drop, drag-to-fill, list
clipper) all remain functionally identical. Only chrome around the table
changes:

```
+--------------------------------------------------------+
| my-route.tas *                          12 rows        |  <- header strip
+--------------------------------------------------------+
| [+ Add N: 1 ]  [Add]  [Duplicate]  [Delete]  [Clear]   |  <- toolbar
+--------------------------------------------------------+
| #  W  A  S  D  Spc  Snk  Sprt  Yaw      ...            |
| 0  X  .  .  .  .    .    X     -42.0                   |
| 1  X  .  .  .  .    .    X      0.0                    |
| ...                                                    |
+--------------------------------------------------------+
```

- Header strip: filename with `warning`-colored dot if dirty, row count.
- Toolbar: numeric stepper + Add button (default N=1, persists across the
  session). Duplicate / Delete operate on selection; greyed when no selection.
  Clear has a confirm modal.
- The right-click context menu still works for power users, but it is no
  longer the only path to adding rows.
- Row drag-and-drop reorder, key-cell drag-to-fill, Del-key shortcut: kept
  verbatim.

## Visual quality contract

This section is non-negotiable. Every screen must satisfy these before merging.

### Token quality rules

- Adjacent bands in the row-banding scale must differ by ≥ 6% lightness. If
  `panel` is L=13%, `panel_alt` is L≥19%. Verify by eye at default UI scale on
  a typical 1080p monitor in normal room lighting. If you have to squint, the
  values are wrong, not your eyes.
- No token serves two semantic states. `panel_alt` is alt-row banding ONLY. Add
  separate tokens for the hover and focus states it currently double-books:

      hover     0.20, 0.20, 0.23, 1.00   : row/button hover background
      focus     accent (existing) at 0.30 alpha as outline, never fill

- Text on background must hit ≥ 4.5:1 contrast for body text, ≥ 3:1 for
  secondary text, ≥ 3:1 for "disabled" (still legible, never invisible).
  `text_muted` at 0.60 on `bg` at 0.10 is right at the limit; do not push it
  darker.

### State matrix (applies to every interactive element)

Each interactive control must visually distinguish five states:

| State    | Treatment                                                      |
|----------|----------------------------------------------------------------|
| Default  | Visible at rest. Frame, border, or fill must be present.       |
| Hover    | `hover` background OR brighter border. Not the same as default.|
| Focus    | 2px `accent` outline (keyboard focus ring). Distinct from hover.|
| Selected | `accent_dim` fill. Distinct from hover.                        |
| Disabled | `text_muted` text, frame still visible but desaturated.        |

If a state collapses onto another (e.g. hover == selected), the design is
broken. Fix the design, not the screenshot.

### Form control specs

**Text / numeric inputs**
- At rest: `panel` background, 1px `border` outline, `text` color.
- Empty inputs MUST render their frame. Placeholder text in `text_muted`.
- Focused: 2px `accent` outline replaces the 1px `border`.
- Min height: row.height (matches table rows).
- Numeric inputs: right-aligned digits, monospace.

**Sliders**
- Track: visible at rest. 6px tall, `panel` fill, 1px `border` outline,
  full width of the control.
- Filled portion (left of handle): `accent`.
- Handle: 16x20 px rectangle, `accent` fill, 1px darker border. Hover: brighten.
- The numeric value appears to the right of the slider as a focusable input
  using the input spec above, so users can type exact values.

**Checkboxes**
- Box: 16x16 px, `panel` background, 1px `border` outline, ALWAYS visible
  (even when unchecked).
- Checked: `accent` fill with a contrasting checkmark glyph.
- Label is clickable; click target covers box and label.

**Dropdowns / combos**: same frame as inputs, with a chevron glyph on the right.

### Density and layout

- Numeric input cells in the row table: min-width 90px (fits "-180.00000" at
  default font size with breathing room).
- Column groups in the row table separated by 14px gap; columns within a group
  separated by 6px. The current "all columns equal gap" reads as squashed.
- Empty area below the last row uses `bg`, never alt-banded into nothing.

### Component sanity rules

- "Off" state for a key cell in the row table renders as an empty cell. No
  placeholder glyph (previous v1.3.0 used a middle dot which read as noise).
  The key cell remains clickable when empty.
- Numeric steppers always render their current value. No placeholder
  characters standing in for the number.
- `ScrollbarSize = 18px` (raised from `md`/12px so the grab is easier to hit).

### Input table row tint state matrix

The row background uses three distinct tints so the user can tell the states
apart at a glance:

- **Selected row** = `selected` at 0.45 alpha (Mocha mauve).
- **Drag source row** (mid-drag, before drop) = `accent_dim` (blue 0.45).
- **Active-playback tick** = `warning` at 0.25 alpha (faint orange).
- **Drag-drop indicator line** (insertion target) = `warning` solid, 2px.

`selected` and `warning` are separate tokens with different hues (amber vs
warmer orange) so the selected row and the playback tick read as distinct
states even though both are warm-colored. The drag source uses accent
specifically so it reads as a different *kind* of state from selection.

### Verification

Before claiming a screen is done, the implementer:
1. Takes a screenshot at default UI scale.
2. Looks at it from arm's length on a normal monitor.
3. For each interactive element, names the state it appears to be in
   (default/hover/focus/selected/disabled). If any state is ambiguous, the
   styling is wrong.
4. Verifies that empty inputs, unchecked checkboxes, and slider tracks are
   all visible without interaction.

If any check fails, fix it before merging. The previous v1.3.0 visual pass
shipped without these checks and produced the regression that prompted this
section.


## Non-goals for v1.3.0

- ImGui docking branch (multi-window rearrangement, OS-level child windows).
- Theme presets, light mode, per-user palette.
- Localization / i18n.
- New simulation features, new visualization modes.
- Mothball-string clipboard import (deferred; native `.tas` file import only).
- Replacing right-click context menus on the table (kept for power users).
- Mobile / touch input.


## Done definition

V1.3.0 ships when:

1. This doc is approved.
2. All four phases (foundation, input editor, file flows, polish) merge to
   main.
3. The five legacy overlay classes (`InputOverlay`, `FileBrowserOverlay`,
   `SettingsOverlay`, `TickInfoPanel`, `PerfOverlay`) are either deleted or
   demoted to sub-panel renderers under `MainWindowOverlay`.
4. `OverlayManager` is removed or reduced to a no-op shim.
5. Every default in the table above matches the running app on all three
   loaders.
6. Tooltips wrap. No tooltip extends past the viewport edge.
7. Empty state renders on a fresh world join.
8. Release-please cuts the version on merge of phase 4.
