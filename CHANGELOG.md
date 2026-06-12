# Changelog

## [1.5.0](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.4.0...v1.5.0) (2026-06-12)


### Features

* add decimal format settings for tick info and angle solver ([5a0dfe8](https://github.com/Leg0shii/ParkourCalculatorMod/commit/5a0dfe8c4312f66f19968ef842defadc9d6f49db))
* allow sprint to be derived + better error messages ([724dc0a](https://github.com/Leg0shii/ParkourCalculatorMod/commit/724dc0ac69a5d895ca21205f0c4c231671b7d339))
* angle optimizer ([#122](https://github.com/Leg0shii/ParkourCalculatorMod/issues/122)) ([9a22bdb](https://github.com/Leg0shii/ParkourCalculatorMod/commit/9a22bdbe340f4b788aedc6263a71b756e10170a7))


### Bug Fixes

* absolute yaw ([1866e9e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/1866e9e037f07abb6e3bee37caf5672baa38f1f9))
* arrow centering ([d8f3517](https://github.com/Leg0shii/ParkourCalculatorMod/commit/d8f351700827802e0dc946e15ea9a5f5aa859dcf))
* auto save default true ([fa4f2e2](https://github.com/Leg0shii/ParkourCalculatorMod/commit/fa4f2e257f79b465df6c69cc3ba2040d8916414d))
* better long solver ([6d5b3b7](https://github.com/Leg0shii/ParkourCalculatorMod/commit/6d5b3b7586177e6f69fa99ae571eb3e1dc47a21b))
* box centering ([f3d0fb2](https://github.com/Leg0shii/ParkourCalculatorMod/commit/f3d0fb25472f9d77e5d08828abbcd28e6a7e0077))
* byte exact 1.21.10 ([c64af67](https://github.com/Leg0shii/ParkourCalculatorMod/commit/c64af676c4dddf93cb9b3a49afcd4a82b0cab8eb))
* cancel in-flight solve when a save is loaded or session reset ([812c245](https://github.com/Leg0shii/ParkourCalculatorMod/commit/812c2454c37ca0201fbeec251d359004c5f9d584))
* cancel stale solve on load, restore gl state on non-vao imgui path ([812c245](https://github.com/Leg0shii/ParkourCalculatorMod/commit/812c2454c37ca0201fbeec251d359004c5f9d584))
* client startup on mac ([#130](https://github.com/Leg0shii/ParkourCalculatorMod/issues/130)) ([c4fedad](https://github.com/Leg0shii/ParkourCalculatorMod/commit/c4fedada3f47559b0f27f0f652168e9181c8f3fb))
* crash on color drag ([9f0652a](https://github.com/Leg0shii/ParkourCalculatorMod/commit/9f0652a09788c3795b371492be77ee5a70704a52))
* delay sneak slowdown by one tick to match vanilla ([#125](https://github.com/Leg0shii/ParkourCalculatorMod/issues/125)) ([f2dc23e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/f2dc23e4d9e2776a9b33d6e9c097e32905261ea8))
* locale-independent constraint value fields in angle solver drawer ([#126](https://github.com/Leg0shii/ParkourCalculatorMod/issues/126)) ([0052dba](https://github.com/Leg0shii/ParkourCalculatorMod/commit/0052dba58b635c77a85025c9f4cd92ab330695ad))
* objective in solved values, collapse-aware pane width, numbered solver section ([9b95ed9](https://github.com/Leg0shii/ParkourCalculatorMod/commit/9b95ed9777dd3d8bbbe82bb8f9e2e5727e6ccd0e))
* player sneak on vertical collision ([058fbac](https://github.com/Leg0shii/ParkourCalculatorMod/commit/058fbac78c635c57edf21cc8e57520879ac57bca))
* proper buttons for right click pane ([ab6ef3e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/ab6ef3e229e6fb7f8f655cae4b7e36f06cee056c))
* qol ([962e229](https://github.com/Leg0shii/ParkourCalculatorMod/commit/962e229f4bd23f7d96d67802d1602a1e5b9267a9))
* restore element buffer and attrib state after imgui draw on the non-vao gl path ([812c245](https://github.com/Leg0shii/ParkourCalculatorMod/commit/812c2454c37ca0201fbeec251d359004c5f9d584))
* solve every Solve-For direction on its own objective ([b13bce0](https://github.com/Leg0shii/ParkourCalculatorMod/commit/b13bce07fbdcb59ea1bb81793f1e6c887bc48488))

## [1.4.0](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.3.1...v1.4.0) (2026-06-03)


### Features

* add absolute yaw ([ae9bab3](https://github.com/Leg0shii/ParkourCalculatorMod/commit/ae9bab394f2ea72f7f84aaf39f6e0f476aff0530))
* add path toggleable ([8988653](https://github.com/Leg0shii/ParkourCalculatorMod/commit/898865374daaa5b2b0a593768841a157fc35382c))
* add scrollbar configuration ([37dce9c](https://github.com/Leg0shii/ParkourCalculatorMod/commit/37dce9c751afb11783dca00524192e6f035122cb))
* add toggleable window ([93bfac7](https://github.com/Leg0shii/ParkourCalculatorMod/commit/93bfac7cd2da4882c5597843a25c5b43e68c387b))
* improve performance ([#86](https://github.com/Leg0shii/ParkourCalculatorMod/issues/86)) ([43b7fa4](https://github.com/Leg0shii/ParkourCalculatorMod/commit/43b7fa4740e9ac2504a2d0f92d1805c520a8d3e6))
* rename labeling to match mpk ([1a08421](https://github.com/Leg0shii/ParkourCalculatorMod/commit/1a0842178f86a2e0bbd1dcf30defeba06d23e631))
* Unified visual style across the whole UI: custom styled title bar, menu bar with full-height hover highlights, shared modal chrome (bold titles, header close glyph, footer rules), accent-colored hyperlinks, and consistently padded buttons, inputs, and tabs.
* Add a persistent "Start" anchor row at the top of the input table, draggable in-world to reposition the start position.
* Freeze the Tick column and enable horizontal scroll so W/A/S/D stay visible on narrow windows.
* Auto-select a UI scale on first launch based on display height, so large/4K displays no longer start as a sliver.
* Navigate yaw cells with Tab, Shift+Tab, Up, and Down while editing.
* Add "Apply tick 1 Speed/Jump to all rows" actions to the context menu.
* Close the overlay with Escape when no popup or text field is capturing it.
* Support mouse-button keybinds for toggle, deselect, and playback on Forge.
* Cap the main window width to 60% of the display and enforce a sensible minimum usable width.


### Bug Fixes

* add test.txt with initial content ([eaa0c86](https://github.com/Leg0shii/ParkourCalculatorMod/commit/eaa0c8657ee3ef43e337d579c3a67d28e6a7e91f)), closes [#83](https://github.com/Leg0shii/ParkourCalculatorMod/issues/83)
* apply yaw of player when set to player position ([05d96d1](https://github.com/Leg0shii/ParkourCalculatorMod/commit/05d96d17fcfcefdc44d497c08f66ef30027c05f7))
* disable fall damage on simulation ([4e16207](https://github.com/Leg0shii/ParkourCalculatorMod/commit/4e162074a7301a491234417b5ed3765022e75bb0))
* file ending from .tas to .json ([e9b5904](https://github.com/Leg0shii/ParkourCalculatorMod/commit/e9b59040fb3c39da8677c3cf93a30df9a0516a8b))
* have input tick window scroll while replaying ([f6a501a](https://github.com/Leg0shii/ParkourCalculatorMod/commit/f6a501ac3f461de88ac293e704608f2621faff69))
* replay show shortest yaw ([f91d88d](https://github.com/Leg0shii/ParkourCalculatorMod/commit/f91d88de181bef623ff0ea3bae635ec1e092d74a))
* sprint is unset after 600 ticks ([8da5272](https://github.com/Leg0shii/ParkourCalculatorMod/commit/8da5272c357283befc0295eeafb972c5737bae2e))
* stop particles rendering of simulator entity ([a3f6be5](https://github.com/Leg0shii/ParkourCalculatorMod/commit/a3f6be5349f688a1913ff3cb8c48d5679b2b9736))
* ui display when pressing esc, inventory or chat ([5032335](https://github.com/Leg0shii/ParkourCalculatorMod/commit/50323350174c08d0d4507eaeaefa4d93b5c043e3))
* untoggle ui on modals or settings screen ([f03c4e9](https://github.com/Leg0shii/ParkourCalculatorMod/commit/f03c4e9f7b6fff05f2fc97df6571ca69e1410821))
* water and glass visibility ([a17502b](https://github.com/Leg0shii/ParkourCalculatorMod/commit/a17502b5daa1116e7ec8f1409ab6f52aa498110e))
* Rework the default render color palette for brighter, higher-contrast tick boxes.
* Show 1-based tick numbers in Tick Info and the yaw actually applied during the tick, matching the outgoing yaw arrow.
* Draw each box's yaw arrow with its outgoing facing; the final box no longer shows a stray arrow.
* Use the correct tick state for the on-ground row highlight.
* Fit the input table to its rows so column borders stop at the last row instead of running through empty space.
* Remap default keybinds: G toggle, L deselect, P playback.
* Use the .json extension and filter in the import file dialogs.
* Forward Ctrl/Shift/Alt modifiers to ImGui correctly on both Fabric and Forge.
* Collapse the yaw input selection to a cursor on focus instead of selecting all text.
* Default subtick visualization and on-ground highlight to off, and Tick Info to on.
* Remove the unused ImGui docking config flag.


### Miscellaneous Chores

* Centralize spacing, separators, modal, and chrome helpers in ThemeManager, Controls, and a new Modal class.
* Remove the obsolete docs/UI_REDESIGN.md.

## [1.3.1](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.3.0...v1.3.1) (2026-05-25)


### Bug Fixes

* anticheat flagging speed 2 replay ([313b2bd](https://github.com/Leg0shii/ParkourCalculatorMod/commit/313b2bdd4c403dce1f0d4bc53acda8bdf6ce424d))
* inconsistent replay state on server/client desync ([d655981](https://github.com/Leg0shii/ParkourCalculatorMod/commit/d6559811fb2936b1507cc7917e38b69c03a287bd))

## [1.3.0](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.2.0...v1.3.0) (2026-05-24)


### Features

* add row tint ([#80](https://github.com/Leg0shii/ParkourCalculatorMod/issues/80)) ([633dcd5](https://github.com/Leg0shii/ParkourCalculatorMod/commit/633dcd57a9cf867833865ef0e369a3e2953ed795)), closes [#55](https://github.com/Leg0shii/ParkourCalculatorMod/issues/55)
* Features/UI overhaul ([#79](https://github.com/Leg0shii/ParkourCalculatorMod/issues/79)) ([0a6a369](https://github.com/Leg0shii/ParkourCalculatorMod/commit/0a6a369a4f486ebc5c027b4de29575647bfdc562))


### Bug Fixes

* mouse button clickable when ui open ([#81](https://github.com/Leg0shii/ParkourCalculatorMod/issues/81)) ([c750ce2](https://github.com/Leg0shii/ParkourCalculatorMod/commit/c750ce22b78b43430a577ac21762425cd4d049d4)), closes [#74](https://github.com/Leg0shii/ParkourCalculatorMod/issues/74)
* overlay and box-render lag at high tick counts ([#76](https://github.com/Leg0shii/ParkourCalculatorMod/issues/76)) ([98a2405](https://github.com/Leg0shii/ParkourCalculatorMod/commit/98a24051fa886cb49756f4478f705f6eae6d17b2)), closes [#75](https://github.com/Leg0shii/ParkourCalculatorMod/issues/75)

## [1.2.0](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.1.0...v1.2.0) (2026-05-23)


### Features

* add collision angle info ([#71](https://github.com/Leg0shii/ParkourCalculatorMod/issues/71)) ([e3bfbb3](https://github.com/Leg0shii/ParkourCalculatorMod/commit/e3bfbb3ed771d4d1e08469b5f071c8d94e921e8c)), closes [#58](https://github.com/Leg0shii/ParkourCalculatorMod/issues/58)
* add keybinds for playback ([#70](https://github.com/Leg0shii/ParkourCalculatorMod/issues/70)) ([e9a3c06](https://github.com/Leg0shii/ParkourCalculatorMod/commit/e9a3c0693fb7217b7072c63d7cd21f99ee3ce7bb)), closes [#59](https://github.com/Leg0shii/ParkourCalculatorMod/issues/59)
* add potion effects ([#68](https://github.com/Leg0shii/ParkourCalculatorMod/issues/68)) ([72a0b4c](https://github.com/Leg0shii/ParkourCalculatorMod/commit/72a0b4cdddb1ecfaa2754d8b43895fb8a8994d38))
* add smooth turn ([#46](https://github.com/Leg0shii/ParkourCalculatorMod/issues/46)) ([69fe304](https://github.com/Leg0shii/ParkourCalculatorMod/commit/69fe30402fa4137c0e4916b5c5ea59fdc59c3713))
* add tick info ([#40](https://github.com/Leg0shii/ParkourCalculatorMod/issues/40)) ([96aab74](https://github.com/Leg0shii/ParkourCalculatorMod/commit/96aab742613619324909593b2dd06b6fdfa49a9e))
* add tick path selection ([#69](https://github.com/Leg0shii/ParkourCalculatorMod/issues/69)) ([3c62a1c](https://github.com/Leg0shii/ParkourCalculatorMod/commit/3c62a1ca82d74dc959bdff704f4572d28aa98412)), closes [#37](https://github.com/Leg0shii/ParkourCalculatorMod/issues/37)
* add version into header ([#73](https://github.com/Leg0shii/ParkourCalculatorMod/issues/73)) ([d4ea2d2](https://github.com/Leg0shii/ParkourCalculatorMod/commit/d4ea2d2448a4bf712771076f5716dc5a616284ec))


### Bug Fixes

* add 1.21.10 jump cool down ([9a2ccf3](https://github.com/Leg0shii/ParkourCalculatorMod/commit/9a2ccf399ccd253c46f31f77c33609e3868f3760))
* allow right click on tas inputs ([28643ee](https://github.com/Leg0shii/ParkourCalculatorMod/commit/28643eedc5493c74fe602f2e4a2fbc0c0fdea028))
* allow sp teleports and proper chunk loading ([ff3a68a](https://github.com/Leg0shii/ParkourCalculatorMod/commit/ff3a68a24d008177b086c929a32f600fe4082c53))
* correct forge version ([ffd85d1](https://github.com/Leg0shii/ParkourCalculatorMod/commit/ffd85d1279922554586bd34b8f552ce440c8879e))
* desync replay on teleport ([c722c5f](https://github.com/Leg0shii/ParkourCalculatorMod/commit/c722c5fb3c0ac6404b67a4156a443e6f881270fa))
* desync when jumping ([f553ed8](https://github.com/Leg0shii/ParkourCalculatorMod/commit/f553ed867f912326ce0d57e854da135ca38ff0e7))
* increase rows add ([#61](https://github.com/Leg0shii/ParkourCalculatorMod/issues/61)) ([d95671e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/d95671e4fed04bee70526726ae5a7d6e51ebf555)), closes [#54](https://github.com/Leg0shii/ParkourCalculatorMod/issues/54)
* invalidate entity on world change ([#66](https://github.com/Leg0shii/ParkourCalculatorMod/issues/66)) ([241f1b4](https://github.com/Leg0shii/ParkourCalculatorMod/commit/241f1b42edc3c884cedda8bc5a4a7c34be5527dd)), closes [#64](https://github.com/Leg0shii/ParkourCalculatorMod/issues/64)
* make inputs scrollable and resizeable ([e87a9a1](https://github.com/Leg0shii/ParkourCalculatorMod/commit/e87a9a18c737e632d4f8cb5a75cde5b12ac14af0))
* optimize tick generation ([#63](https://github.com/Leg0shii/ParkourCalculatorMod/issues/63)) ([87396aa](https://github.com/Leg0shii/ParkourCalculatorMod/commit/87396aaf4eef9f8b113675447ea79878a50066ba)), closes [#53](https://github.com/Leg0shii/ParkourCalculatorMod/issues/53)
* pausing ([#43](https://github.com/Leg0shii/ParkourCalculatorMod/issues/43)) ([85c14f2](https://github.com/Leg0shii/ParkourCalculatorMod/commit/85c14f2d8b77a7a4482909fd7da142e4d97cd467))
* remove damage impact ([7ea010f](https://github.com/Leg0shii/ParkourCalculatorMod/commit/7ea010fb2ac856b5980ec270fcf962f8fc76f3f6))
* soft collision ([#52](https://github.com/Leg0shii/ParkourCalculatorMod/issues/52)) ([ccf5b43](https://github.com/Leg0shii/ParkourCalculatorMod/commit/ccf5b43a0cdfae86e0d2ffd51992af0894e76c44)), closes [#51](https://github.com/Leg0shii/ParkourCalculatorMod/issues/51)
* yaw gizmo ([#62](https://github.com/Leg0shii/ParkourCalculatorMod/issues/62)) ([bc4e920](https://github.com/Leg0shii/ParkourCalculatorMod/commit/bc4e920e4220d146dae47a0eb5e46b1c8cd2259b)), closes [#48](https://github.com/Leg0shii/ParkourCalculatorMod/issues/48)

## [1.1.0](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.0.1...v1.1.0) (2026-05-18)


### Features

* add box and tick selection ([#33](https://github.com/Leg0shii/ParkourCalculatorMod/issues/33)) ([57027cd](https://github.com/Leg0shii/ParkourCalculatorMod/commit/57027cd070149ae3bfdda9a162a347e509ce8347))
* add colorful states ([#30](https://github.com/Leg0shii/ParkourCalculatorMod/issues/30)) ([44c68f4](https://github.com/Leg0shii/ParkourCalculatorMod/commit/44c68f4e07045d47f76a3bfe5d9cb5fc2c6ccddc))
* add hitbox rendering and subticks ([#31](https://github.com/Leg0shii/ParkourCalculatorMod/issues/31)) ([025c2d8](https://github.com/Leg0shii/ParkourCalculatorMod/commit/025c2d8331b4fcc4b534d0ca2982179ef66fac94))
* add macro playback ([#34](https://github.com/Leg0shii/ParkourCalculatorMod/issues/34)) ([5f0524f](https://github.com/Leg0shii/ParkourCalculatorMod/commit/5f0524ff46c0c6371e3e7692eca04764de98da4a))
* add yaw arrows ([#32](https://github.com/Leg0shii/ParkourCalculatorMod/issues/32)) ([7c6a2f3](https://github.com/Leg0shii/ParkourCalculatorMod/commit/7c6a2f3159f4f18a0e0ad17b15f00aa28cf7ca0f))
* add yaw gizmo ([#35](https://github.com/Leg0shii/ParkourCalculatorMod/issues/35)) ([6b09de6](https://github.com/Leg0shii/ParkourCalculatorMod/commit/6b09de66c110d77d43e82cfe29a3f01db5672368))
* file saving and loading ([#29](https://github.com/Leg0shii/ParkourCalculatorMod/issues/29)) ([722e4b2](https://github.com/Leg0shii/ParkourCalculatorMod/commit/722e4b2361a1fe9bf3c46f235c4caccd09688d4a))
* settings window ([#27](https://github.com/Leg0shii/ParkourCalculatorMod/issues/27)) ([fff9b8b](https://github.com/Leg0shii/ParkourCalculatorMod/commit/fff9b8b1518481f08719fc312df7c1d686ee9ac0))

## [1.0.1](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.0.0...v1.0.1) (2026-05-16)


### Bug Fixes

* add release please token ([78e4e59](https://github.com/Leg0shii/ParkourCalculatorMod/commit/78e4e5932193fa383dd748710a33f06ef62815fe))
* bundle dependencies in Forge jars ([91dfdbb](https://github.com/Leg0shii/ParkourCalculatorMod/commit/91dfdbb20d566f4101db8f36e09b04f358db358e))

## [1.0.0](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v0.1.0...v1.0.0) (2026-05-16)


### Features

* add draggable boxes ([#12](https://github.com/Leg0shii/ParkourCalculatorMod/issues/12)) ([1619f83](https://github.com/Leg0shii/ParkourCalculatorMod/commit/1619f839e4786f60d6b721da819163520c6e1aa9)), closes [#7](https://github.com/Leg0shii/ParkourCalculatorMod/issues/7)


### Bug Fixes

* align input handling ([#11](https://github.com/Leg0shii/ParkourCalculatorMod/issues/11)) ([47aab97](https://github.com/Leg0shii/ParkourCalculatorMod/commit/47aab978ffe01728729f3ef0346fd36457e179b3)), closes [#6](https://github.com/Leg0shii/ParkourCalculatorMod/issues/6)
* align key toggle to k ([#9](https://github.com/Leg0shii/ParkourCalculatorMod/issues/9)) ([17a4763](https://github.com/Leg0shii/ParkourCalculatorMod/commit/17a4763a9c9d606d320fbcf8ee0ade8ad4604c87)), closes [#5](https://github.com/Leg0shii/ParkourCalculatorMod/issues/5)
* align simulation behaviour across all three loaders ([385d706](https://github.com/Leg0shii/ParkourCalculatorMod/commit/385d706e822cd828da6b7bed195dc53b7f8fec0f))
* fetch correct version ([a379554](https://github.com/Leg0shii/ParkourCalculatorMod/commit/a3795547959318d470003cd2561becf4cdf76b6f))


### Miscellaneous Chores

* release 1.0.0 ([ee941ff](https://github.com/Leg0shii/ParkourCalculatorMod/commit/ee941ff2058c46baf5f810e06cce853652c796e4))

## Changelog
