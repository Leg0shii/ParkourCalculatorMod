# Changelog

## [1.12.0](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.11.0...v1.12.0) (2026-09-06)


### Features

* add a hotkey to toggle path rendering ([#467](https://github.com/Leg0shii/ParkourCalculatorMod/issues/467)) ([b276c61](https://github.com/Leg0shii/ParkourCalculatorMod/commit/b276c61d37fd833d94c37d9b194524c4f7120c01))
* **anglesolver:** chain-aware ILS and run-up facing sweep node ([#469](https://github.com/Leg0shii/ParkourCalculatorMod/issues/469)) ([2d25661](https://github.com/Leg0shii/ParkourCalculatorMod/commit/2d25661572b4d02eddc6184be78a541cff0d38e7))
* copy a tick as a teleport command ([#466](https://github.com/Leg0shii/ParkourCalculatorMod/issues/466)) ([ce28612](https://github.com/Leg0shii/ParkourCalculatorMod/commit/ce286126483138c04770d0635100d22286df09e3))


### Bug Fixes

* **anglesolver:** let a dF = 0 on Tick 1 solve the start facing ([b69b8c7](https://github.com/Leg0shii/ParkourCalculatorMod/commit/b69b8c711a9d04f4af632371b423428246e7f6ce))
* **anglesolver:** let a dF = 0 on Tick 1 solve the start facing ([b69b8c7](https://github.com/Leg0shii/ParkourCalculatorMod/commit/b69b8c711a9d04f4af632371b423428246e7f6ce))
* **anglesolver:** let a dF = 0 on Tick 1 solve the start facing ([c8f297a](https://github.com/Leg0shii/ParkourCalculatorMod/commit/c8f297a1c5e451d93186e166286714c33185c06a)), closes [#468](https://github.com/Leg0shii/ParkourCalculatorMod/issues/468)
* use 1.12.2 folded deg-to-rad in ExactJumpModel moveFlying ([c981ba5](https://github.com/Leg0shii/ParkourCalculatorMod/commit/c981ba5c666eb10a9c68e52f47dd59af6c191561))
* use 1.12.2 folded deg-to-rad in ExactJumpModel moveFlying ([15b73ac](https://github.com/Leg0shii/ParkourCalculatorMod/commit/15b73ac221001e16f5455e025f0d49993c03ecb3))


### Performance Improvements

* **anglesolver:** run the deterministic seed first in Fast with a 500 ms miss cap ([#470](https://github.com/Leg0shii/ParkourCalculatorMod/issues/470)) ([8db1ded](https://github.com/Leg0shii/ParkourCalculatorMod/commit/8db1ded369d0d70eddd52506ab479854a2ebba5f))


### Miscellaneous Chores

* weekly release train ([5f35a22](https://github.com/Leg0shii/ParkourCalculatorMod/commit/5f35a226a7935a76dbe62dc59964c90cadf7de4a))

## [1.11.0](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.10.0...v1.11.0) (2026-08-30)


### Features

* add a configurable replay start delay ([#445](https://github.com/Leg0shii/ParkourCalculatorMod/issues/445)) ([a3cd1e0](https://github.com/Leg0shii/ParkourCalculatorMod/commit/a3cd1e09b318ae9fe3507027008806f64940d195))
* add custom target angle objective to angle solver ([#370](https://github.com/Leg0shii/ParkourCalculatorMod/issues/370)) ([121d20f](https://github.com/Leg0shii/ParkourCalculatorMod/commit/121d20f18067f8a265d0239762ab56190040aa3e))
* add per-tick teleport destinations ([#443](https://github.com/Leg0shii/ParkourCalculatorMod/issues/443)) ([cf6d402](https://github.com/Leg0shii/ParkourCalculatorMod/commit/cf6d402bfc64de6c32d6d4ced03fd6df17349cfd)), closes [#438](https://github.com/Leg0shii/ParkourCalculatorMod/issues/438)
* add pressure plate hitbox constraint (Ctrl+B) ([#442](https://github.com/Leg0shii/ParkourCalculatorMod/issues/442)) ([4c5226f](https://github.com/Leg0shii/ParkourCalculatorMod/commit/4c5226fd9e6f197420c63c76926f046bdee6b402)), closes [#437](https://github.com/Leg0shii/ParkourCalculatorMod/issues/437)
* **anglesolver:** cold gate-pattern start solver + lattice-exact certified core ([#447](https://github.com/Leg0shii/ParkourCalculatorMod/issues/447)) ([96e15d0](https://github.com/Leg0shii/ParkourCalculatorMod/commit/96e15d080c86b8119b9d42f96d5de19647cab45d))
* list Fast and Optimize as built-in graph presets in Custom ([#444](https://github.com/Leg0shii/ParkourCalculatorMod/issues/444)) ([109cc17](https://github.com/Leg0shii/ParkourCalculatorMod/commit/109cc1780f507bbf87511b00b1dd9d62a246ce18)), closes [#336](https://github.com/Leg0shii/ParkourCalculatorMod/issues/336)
* remove the slow polish stage from the run-ticks fast solve ([#456](https://github.com/Leg0shii/ParkourCalculatorMod/issues/456)) ([e4dbf98](https://github.com/Leg0shii/ParkourCalculatorMod/commit/e4dbf9838b51d431e46ee2851ec717887a7f9377))
* **runticks:** refine UI layout and improve min mode search logic ([#426](https://github.com/Leg0shii/ParkourCalculatorMod/issues/426)) ([2ba0745](https://github.com/Leg0shii/ParkourCalculatorMod/commit/2ba0745976ac4bd7ec8c28ed466f5a9d50c2dd4d))
* show the in-game yaw in tick info ([#453](https://github.com/Leg0shii/ParkourCalculatorMod/issues/453)) ([6295709](https://github.com/Leg0shii/ParkourCalculatorMod/commit/62957096151ad749ef9429474b6e96eb0e3a6815))
* **solver:** certified B&B, one-path pipeline, cleanup, free-start seed-independence ([#427](https://github.com/Leg0shii/ParkourCalculatorMod/issues/427)) ([be584ed](https://github.com/Leg0shii/ParkourCalculatorMod/commit/be584eda23c56ed23b6d1138a68e0449580e3f38))
* **solver:** one authoritative time budget per node ([#431](https://github.com/Leg0shii/ParkourCalculatorMod/issues/431)) ([06c0573](https://github.com/Leg0shii/ParkourCalculatorMod/commit/06c0573d8005e5cef458ac4562733c8954ff22c6))
* **ui:** allow Speed and Jump Boost amplifiers up to 255 ([#433](https://github.com/Leg0shii/ParkourCalculatorMod/issues/433)) ([575e82c](https://github.com/Leg0shii/ParkourCalculatorMod/commit/575e82cfa37a72ba48eef0b029fc2e076b41fb72))
* **ui:** per-node and per-param help in the graph editor ([#430](https://github.com/Leg0shii/ParkourCalculatorMod/issues/430)) ([#432](https://github.com/Leg0shii/ParkourCalculatorMod/issues/432)) ([6d8fdc8](https://github.com/Leg0shii/ParkourCalculatorMod/commit/6d8fdc86f3cdfc978bd6f02a5f44f5ee9f56a683))


### Bug Fixes

* **anglesolver:** Custom defaults to the Optimize graph and budget ([#452](https://github.com/Leg0shii/ParkourCalculatorMod/issues/452)) ([f5a80b7](https://github.com/Leg0shii/ParkourCalculatorMod/commit/f5a80b763d486d6a0dff36929ccd19d3b5a90356))
* **anglesolver:** support cross-axis dX-vs-dZ constraints in closed-form and SLP solvers ([#425](https://github.com/Leg0shii/ParkourCalculatorMod/issues/425)) ([320509e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/320509ee90797b8edb6dfed288eb2aad0b3292b0))
* **sim:** apply per-tick pitch so water trajectory matches replay ([#441](https://github.com/Leg0shii/ParkourCalculatorMod/issues/441)) ([b8a888c](https://github.com/Leg0shii/ParkourCalculatorMod/commit/b8a888c7743f14d50d5511614d0452847130e36d)), closes [#423](https://github.com/Leg0shii/ParkourCalculatorMod/issues/423)


### Code Refactoring

* **anglesolver:** remove copy-to-tick and move-to-tick from constraint menu ([#440](https://github.com/Leg0shii/ParkourCalculatorMod/issues/440)) ([40d15a3](https://github.com/Leg0shii/ParkourCalculatorMod/commit/40d15a3b208260f0a086db5a800118bbf2365bc4)), closes [#420](https://github.com/Leg0shii/ParkourCalculatorMod/issues/420)
* **solver:** remove unreachable CountingForwardModel ([#436](https://github.com/Leg0shii/ParkourCalculatorMod/issues/436)) ([20f32b7](https://github.com/Leg0shii/ParkourCalculatorMod/commit/20f32b73b7f412b4e614120cc1574e4c2b438cae))


### Miscellaneous Chores

* weekly release train ([583011f](https://github.com/Leg0shii/ParkourCalculatorMod/commit/583011f8dd19c5476ffc2eaf24b0268283a40924))

## [1.10.0](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.9.0...v1.10.0) (2026-08-23)


### Features

* add a run-ticks search that adds run-up ticks before each jump ([#405](https://github.com/Leg0shii/ParkourCalculatorMod/issues/405)) ([b3f25e6](https://github.com/Leg0shii/ParkourCalculatorMod/commit/b3f25e6440e55430d2a705b61f09659f44b3d824))
* add extend path & solve to block keybind ([#333](https://github.com/Leg0shii/ParkourCalculatorMod/issues/333)) ([2d63a5e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/2d63a5e6d937519b36433d810a75a8dc4be2e6d8))
* add hotkey to re-run the simulation from the first tick ([#340](https://github.com/Leg0shii/ParkourCalculatorMod/issues/340)) ([1cb060b](https://github.com/Leg0shii/ParkourCalculatorMod/commit/1cb060b5836c407b32188bf4c2e74ced4e371e08))
* add middle-click to delete constraints and state overrides ([#331](https://github.com/Leg0shii/ParkourCalculatorMod/issues/331)) ([8057712](https://github.com/Leg0shii/ParkourCalculatorMod/commit/8057712534eea6acf7e4848b76e6ff9a1e07dc4a))
* align the intersection footprint with the player facing ([#343](https://github.com/Leg0shii/ParkourCalculatorMod/issues/343)) ([544e248](https://github.com/Leg0shii/ParkourCalculatorMod/commit/544e248a9b68ed064f8b5a2d4492c0b13d25aed0))
* level-set objective ascent for degenerate solve directions ([#339](https://github.com/Leg0shii/ParkourCalculatorMod/issues/339)) ([d88bc36](https://github.com/Leg0shii/ParkourCalculatorMod/commit/d88bc36892e3c668d225effe1f5807ac1c09ad5d))
* merge constraint bounding boxes with alt and fix saving keybind on fabric ([#332](https://github.com/Leg0shii/ParkourCalculatorMod/issues/332)) ([df9839e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/df9839e2260b1e8862d6a4e225c83b36cf27ea7c))
* reserve a wrap slice in the solver graph and make the wrap angle cap a node setting ([3433c77](https://github.com/Leg0shii/ParkourCalculatorMod/commit/3433c77e9e6bc8bfa7d55d85bc766831cdafb97f))
* **solver:** branch the pattern B&B on keeping the inertia gate open ([#397](https://github.com/Leg0shii/ParkourCalculatorMod/issues/397)) ([436d12c](https://github.com/Leg0shii/ParkourCalculatorMod/commit/436d12c6c53d0f60e028b0ac8eb309418265b495))
* **solver:** cost the seam turn in smooth scoring and accept dF on the first solve tick ([#306](https://github.com/Leg0shii/ParkourCalculatorMod/issues/306)) ([aecb61c](https://github.com/Leg0shii/ParkourCalculatorMod/commit/aecb61ccd417fa1b2cf5e9b98d907dd9e9b38fb5))
* **solver:** remove the CMA-ES machinery and simplify the solver ([#373](https://github.com/Leg0shii/ParkourCalculatorMod/issues/373), [#375](https://github.com/Leg0shii/ParkourCalculatorMod/issues/375), [#377](https://github.com/Leg0shii/ParkourCalculatorMod/issues/377), [#379](https://github.com/Leg0shii/ParkourCalculatorMod/issues/379), [#382](https://github.com/Leg0shii/ParkourCalculatorMod/issues/382)) ([538c92b](https://github.com/Leg0shii/ParkourCalculatorMod/commit/538c92bf6116a57d31731404f8e6296bd68ba58e))
* **solver:** teach SlpSolve facing constraints via tie folding and exact yaw rows ([#368](https://github.com/Leg0shii/ParkourCalculatorMod/issues/368)) ([b477231](https://github.com/Leg0shii/ParkourCalculatorMod/commit/b47723195f04379d16987f40e74829af00f6887c))
* **ui:** move angle solver action buttons above results and persist section expansion ([#381](https://github.com/Leg0shii/ParkourCalculatorMod/issues/381)) ([456b0ea](https://github.com/Leg0shii/ParkourCalculatorMod/commit/456b0ea65956ff9b89cf1ece59a00a1bdfd2fea3))


### Bug Fixes

* add dX comparison checkbox to dZ constraints ([#357](https://github.com/Leg0shii/ParkourCalculatorMod/issues/357)) ([9af2452](https://github.com/Leg0shii/ParkourCalculatorMod/commit/9af2452c66650ba37b15159f45ce38a79e242d68))
* **fabric:** suppress pause on lost focus while the UI is open ([#353](https://github.com/Leg0shii/ParkourCalculatorMod/issues/353)) ([ed7bc8a](https://github.com/Leg0shii/ParkourCalculatorMod/commit/ed7bc8a69bc95b2e1512326c53c00f077e22fe98))
* **fabric:** sync player attribute base values into the simulation ([#355](https://github.com/Leg0shii/ParkourCalculatorMod/issues/355)) ([730247c](https://github.com/Leg0shii/ParkourCalculatorMod/commit/730247c451dc09486a5056883f9345ab8097950a))
* fast and custom effort failing to solve jumps with dF constraints ([#335](https://github.com/Leg0shii/ParkourCalculatorMod/issues/335)) ([0dd6172](https://github.com/Leg0shii/ParkourCalculatorMod/commit/0dd617228bf76189e4cb31b83d029a08b4161cbd))
* **forge:** reuse path bake buffers instead of allocating direct memory per rebake ([#334](https://github.com/Leg0shii/ParkourCalculatorMod/issues/334)) ([63fef38](https://github.com/Leg0shii/ParkourCalculatorMod/commit/63fef384e8a7280828a29d2b8a0720d51c08137b))
* keep constraint ticks inside the route so none land past the last row ([#354](https://github.com/Leg0shii/ParkourCalculatorMod/issues/354)) ([d9eef0e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/d9eef0ef20baa9232f7ab61184a5e34d19e5d233))
* no start box or unsaved state before a TAS is opened or created ([#352](https://github.com/Leg0shii/ParkourCalculatorMod/issues/352)) ([bab5858](https://github.com/Leg0shii/ParkourCalculatorMod/commit/bab58588c558127c34ed60dd0754bfb8c5f3a497))
* **playback:** keep replay in sync when restarting near a fall-damage ruling ([#400](https://github.com/Leg0shii/ParkourCalculatorMod/issues/400)) ([bc746eb](https://github.com/Leg0shii/ParkourCalculatorMod/commit/bc746ebfbf4bb2ee4d8faab9e9f8a9ceb6701ae9))
* report which constraints a failed solve missed ([#410](https://github.com/Leg0shii/ParkourCalculatorMod/issues/410)) ([3ba6d3c](https://github.com/Leg0shii/ParkourCalculatorMod/commit/3ba6d3c44407c0f1adf21e1081b76431b53940b3))
* select the first tick when tapping the start box ([#341](https://github.com/Leg0shii/ParkourCalculatorMod/issues/341)) ([b3bb34f](https://github.com/Leg0shii/ParkourCalculatorMod/commit/b3bb34fd49476740821239ccf63a0f8e189600ac))
* **solver:** close free-start joint near misses with the wrap-window ILS ([#371](https://github.com/Leg0shii/ParkourCalculatorMod/issues/371)) ([89ce985](https://github.com/Leg0shii/ParkourCalculatorMod/commit/89ce98587496cda672472d9e54dff0aa59af4dfb))
* **solver:** make free-start solves independent of the seed start position ([#358](https://github.com/Leg0shii/ParkourCalculatorMod/issues/358), [#363](https://github.com/Leg0shii/ParkourCalculatorMod/issues/363), [#365](https://github.com/Leg0shii/ParkourCalculatorMod/issues/365), [#367](https://github.com/Leg0shii/ParkourCalculatorMod/issues/367)) ([197b70f](https://github.com/Leg0shii/ParkourCalculatorMod/commit/197b70f8c905f945a3a47364e115ff8926ba3003))
* **solver:** publish Optimize's incumbents and stop starving its polish stage ([#399](https://github.com/Leg0shii/ParkourCalculatorMod/issues/399)) ([1ff8c8b](https://github.com/Leg0shii/ParkourCalculatorMod/commit/1ff8c8bc0c712c5c8bc91cbf123f85e245099d66))
* **solver:** reach single-tick inertia zeroings in the pattern B&B ([#393](https://github.com/Leg0shii/ParkourCalculatorMod/issues/393)) ([f93f657](https://github.com/Leg0shii/ParkourCalculatorMod/commit/f93f657443098fa57b2131f97d9ebd4fe75a5e1e))
* **solver:** score Smooth (TAS) on turn-direction changes, not jerk ([#417](https://github.com/Leg0shii/ParkourCalculatorMod/issues/417)) ([49def65](https://github.com/Leg0shii/ParkourCalculatorMod/commit/49def653fb70cfbcffa51c92b8db266f328fce71))
* **solver:** seed-independent free-start solves for dF=0 chains ([#387](https://github.com/Leg0shii/ParkourCalculatorMod/issues/387), [#388](https://github.com/Leg0shii/ParkourCalculatorMod/issues/388), [#389](https://github.com/Leg0shii/ParkourCalculatorMod/issues/389), [#390](https://github.com/Leg0shii/ParkourCalculatorMod/issues/390)) ([e171cc5](https://github.com/Leg0shii/ParkourCalculatorMod/commit/e171cc52078fc2e4267dbf0f7901f5d417ed4fd3))
* **solver:** stop the feasibility repair from flicking the smoothed path ([#415](https://github.com/Leg0shii/ParkourCalculatorMod/issues/415)) ([bbd9b33](https://github.com/Leg0shii/ParkourCalculatorMod/commit/bbd9b335a42bbe57a715db2b3d8dbd10c50df50b))
* **ui:** make Tick Info settings rows as tall as the widgets they contain ([#356](https://github.com/Leg0shii/ParkourCalculatorMod/issues/356)) ([fd2058e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/fd2058e2e7692b551368e7f65a6bcca50bf77aec))


### Documentation

* **research:** consolidate and update the research docs, drop dead benchmark data ([#391](https://github.com/Leg0shii/ParkourCalculatorMod/issues/391)) ([d2575bc](https://github.com/Leg0shii/ParkourCalculatorMod/commit/d2575bc7f742df8c7db8af5292a469ff1843a0d4))
* **research:** issue 384 dual Newton iteration audit, both lanes measured dead ([#385](https://github.com/Leg0shii/ParkourCalculatorMod/issues/385)) ([60c8675](https://github.com/Leg0shii/ParkourCalculatorMod/commit/60c8675e569c883b35a983c2a8063c4a3cf9a8b1))


### Continuous Integration

* publish the fabric 1.21.3 jar to modrinth on release ([#329](https://github.com/Leg0shii/ParkourCalculatorMod/issues/329)) ([52e9115](https://github.com/Leg0shii/ParkourCalculatorMod/commit/52e91154395a1c9de99c4f1e5fa2bdd720eed683))


### Miscellaneous Chores

* drop the bundled commons-math3 and explain the unsupported-dF failure ([#396](https://github.com/Leg0shii/ParkourCalculatorMod/issues/396)) ([b8ff1e8](https://github.com/Leg0shii/ParkourCalculatorMod/commit/b8ff1e8d12a94070d3681399d38a5de5624463d4)), closes [#394](https://github.com/Leg0shii/ParkourCalculatorMod/issues/394) [#395](https://github.com/Leg0shii/ParkourCalculatorMod/issues/395)
* weekly release train ([b17234e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/b17234edd3acfbde7524045032fb2da6a7432af3))

## [1.9.0](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.8.0...v1.9.0) (2026-08-16)


### Features

* add native Fabric loader for Minecraft 1.21.3 ([#236](https://github.com/Leg0shii/ParkourCalculatorMod/issues/236)) ([#300](https://github.com/Leg0shii/ParkourCalculatorMod/issues/300)) ([1d6e52d](https://github.com/Leg0shii/ParkourCalculatorMod/commit/1d6e52d6ea470ee694df55839be41f012bea718a))
* copy ticks to a new TAS and copy/paste table rows ([#294](https://github.com/Leg0shii/ParkourCalculatorMod/issues/294)) ([93a06e4](https://github.com/Leg0shii/ParkourCalculatorMod/commit/93a06e4a15ba8e0c007a91836e36a36198f460e2)), closes [#291](https://github.com/Leg0shii/ParkourCalculatorMod/issues/291)
* paired client-server simulation and lockstep replay ([#322](https://github.com/Leg0shii/ParkourCalculatorMod/issues/322)) ([993884a](https://github.com/Leg0shii/ParkourCalculatorMod/commit/993884ab9e2de1b3e6292d0f7dda75af325e6440))
* right click teleport to the selected tick, singleplayer only ([#305](https://github.com/Leg0shii/ParkourCalculatorMod/issues/305)) ([ac7786b](https://github.com/Leg0shii/ParkourCalculatorMod/commit/ac7786b39348dddeb941cb12e55f987895e438e5))
* **ui:** add Edit menu with Undo/Redo items ([#295](https://github.com/Leg0shii/ParkourCalculatorMod/issues/295)) ([c70029d](https://github.com/Leg0shii/ParkourCalculatorMod/commit/c70029d2367b9184d8d5f887369ac071650397d2))
* **ui:** add per-tick hotbar slot input column ([#297](https://github.com/Leg0shii/ParkourCalculatorMod/issues/297)) ([3a41006](https://github.com/Leg0shii/ParkourCalculatorMod/commit/3a410061d478a69a517b8540f8f0e05ba4b64b51))


### Bug Fixes

* derive B-key constraints from the looked-at block's own hitbox ([#309](https://github.com/Leg0shii/ParkourCalculatorMod/issues/309)) ([4f91245](https://github.com/Leg0shii/ParkourCalculatorMod/commit/4f91245ab4e7a3f3df99c00ecad79b199a83636d))
* keep the gizmo tail patch alive while per-tick hitboxes are shown ([#304](https://github.com/Leg0shii/ParkourCalculatorMod/issues/304)) ([458fd73](https://github.com/Leg0shii/ParkourCalculatorMod/commit/458fd73d1eae76b46926fbe066efabfe7da0056f))
* keep the simulator's player size consistent across resumed runs ([#310](https://github.com/Leg0shii/ParkourCalculatorMod/issues/310)) ([df1f67f](https://github.com/Leg0shii/ParkourCalculatorMod/commit/df1f67f8b4617401fa413150ac51e9644d41ea62))
* **playback:** pin first-person arm sway to the driven rotation ([#316](https://github.com/Leg0shii/ParkourCalculatorMod/issues/316)) ([620b4f0](https://github.com/Leg0shii/ParkourCalculatorMod/commit/620b4f07559e5ce13d2569c524f45a84eedf503d))
* **playback:** reset fire, fall and damage state across resims and replay restarts ([#327](https://github.com/Leg0shii/ParkourCalculatorMod/issues/327)) ([ce5e539](https://github.com/Leg0shii/ParkourCalculatorMod/commit/ce5e539993a854ee15e5cbae7960dc35a3f43081))
* remap the pkc-fabric-1.21.3 jar to intermediary so it loads on production installs ([#324](https://github.com/Leg0shii/ParkourCalculatorMod/issues/324)) ([34e73b8](https://github.com/Leg0shii/ParkourCalculatorMod/commit/34e73b822759d938989f35f3e11aaff13733d1a2))
* **solver:** stop dumping a full save snapshot per solve ([#312](https://github.com/Leg0shii/ParkourCalculatorMod/issues/312)) ([8849bcb](https://github.com/Leg0shii/ParkourCalculatorMod/commit/8849bcb42d23bf2504bcac023b32a2d2da338120))


### Miscellaneous Chores

* weekly release train ([2d452a5](https://github.com/Leg0shii/ParkourCalculatorMod/commit/2d452a5e30e64fca0a903b896e3ce80c4ffa00cf))

## [1.8.0](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.7.1...v1.8.0) (2026-08-09)


### Features

* add an option to disable creative flight during singleplayer playback ([#261](https://github.com/Leg0shii/ParkourCalculatorMod/issues/261)) ([514725e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/514725e5283634dccbdd5f0631209936ecda535a))
* add ladder, soulsand, water, lava and cobweb surface regimes to the solver ([#254](https://github.com/Leg0shii/ParkourCalculatorMod/issues/254)) ([0fd0696](https://github.com/Leg0shii/ParkourCalculatorMod/commit/0fd0696ae556489e398cf146932c3a1cc09ef885)), closes [#91](https://github.com/Leg0shii/ParkourCalculatorMod/issues/91) [#202](https://github.com/Leg0shii/ParkourCalculatorMod/issues/202)
* add solver hotkeys with auto-applied solves and simulation-captured surface state ([#255](https://github.com/Leg0shii/ParkourCalculatorMod/issues/255)) ([#263](https://github.com/Leg0shii/ParkourCalculatorMod/issues/263)) ([a5335e8](https://github.com/Leg0shii/ParkourCalculatorMod/commit/a5335e83abc0a7ed0ee33191b93a06d286387230))
* add undo/redo with persistent per-save history  ([#257](https://github.com/Leg0shii/ParkourCalculatorMod/issues/257)) ([2c8fd90](https://github.com/Leg0shii/ParkourCalculatorMod/commit/2c8fd905847ce6055aaa781a936db684d45ba1bd))
* click a Tick Info row to copy its values ([#268](https://github.com/Leg0shii/ParkourCalculatorMod/issues/268)) ([23b5bac](https://github.com/Leg0shii/ParkourCalculatorMod/commit/23b5bacb790fcbb155e773069ecf31ec396e6438))


### Bug Fixes

* constraint chip delete crash and live best-path render crash ([#260](https://github.com/Leg0shii/ParkourCalculatorMod/issues/260)) ([6f1b2a6](https://github.com/Leg0shii/ParkourCalculatorMod/commit/6f1b2a6faac5072785baa1b9fb828586ecdf61a9))
* **constraints:** generalize hotkey B over multi-box block shapes ([#274](https://github.com/Leg0shii/ParkourCalculatorMod/issues/274)) ([1cd6d84](https://github.com/Leg0shii/ParkourCalculatorMod/commit/1cd6d84e1b837ea08f1338edcc384a8707b4fa48))
* **forge:** survive transient Windows clipboard contention on ImGui copy ([#275](https://github.com/Leg0shii/ParkourCalculatorMod/issues/275)) ([addb086](https://github.com/Leg0shii/ParkourCalculatorMod/commit/addb08618da12229af3682a58de03e88945d1061))
* keep the gizmo tail patch alive while constraint plates are shown ([#276](https://github.com/Leg0shii/ParkourCalculatorMod/issues/276)) ([c2b46dc](https://github.com/Leg0shii/ParkourCalculatorMod/commit/c2b46dc85802eec5d48e430d8aa95fa65313e5f8))
* recognize vines for ctrl+B cell constraints ([#266](https://github.com/Leg0shii/ParkourCalculatorMod/issues/266)) ([1ca9d3e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/1ca9d3efd34926077e4d6ce6e35cfb029bed6c95))
* route ImGuiKey constants through getKeyIndex so Del removes rows ([#262](https://github.com/Leg0shii/ParkourCalculatorMod/issues/262)) ([c705068](https://github.com/Leg0shii/ParkourCalculatorMod/commit/c7050682257b3c3f89456dfe0bd77c137e285c0c))
* send playback move packets before the client tick end packet ([#246](https://github.com/Leg0shii/ParkourCalculatorMod/issues/246)) ([bf78c04](https://github.com/Leg0shii/ParkourCalculatorMod/commit/bf78c04e96a6b6c4cae252c7868ea3c7d3e4db71))
* **solver:** accept range constraint bounds in either order ([#267](https://github.com/Leg0shii/ParkourCalculatorMod/issues/267)) ([756e412](https://github.com/Leg0shii/ParkourCalculatorMod/commit/756e412b10da05457a53d3fcb23d539dee19b356))
* **solver:** ladder the receding-horizon SLP fallback over all Solve-For directions ([#273](https://github.com/Leg0shii/ParkourCalculatorMod/issues/273)) ([48ae717](https://github.com/Leg0shii/ParkourCalculatorMod/commit/48ae717a727ca8808418f81f7fa83824ec3b6560))
* **solver:** read the modern air sprint factor live so Derive catches tick-0 engagement ([#264](https://github.com/Leg0shii/ParkourCalculatorMod/issues/264)) ([c6a473e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/c6a473ecdae106a2974a02438df122503b75cd7f))
* Toggle UI key no longer closes the overlay while typing in a text field ([#280](https://github.com/Leg0shii/ParkourCalculatorMod/issues/280)) ([b9649a9](https://github.com/Leg0shii/ParkourCalculatorMod/commit/b9649a9784e1cf335ecdfa39c9e64292962fc242))


### Documentation

* update stale 1.21.10 references to 26.2 in agent guidance and docs ([b9d760c](https://github.com/Leg0shii/ParkourCalculatorMod/commit/b9d760c30c4230d2e6d4de773d8e1bc60f6e4fd0))


### Continuous Integration

* publish Modrinth versions with plain semver version numbers ([4a7587c](https://github.com/Leg0shii/ParkourCalculatorMod/commit/4a7587c32881879de319ed12218e926e714f064b))


### Miscellaneous Chores

* weekly release train ([6c9db80](https://github.com/Leg0shii/ParkourCalculatorMod/commit/6c9db80e4126e6d1b4342c62648e6524649e18ff))

## [1.7.1](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.7.0...v1.7.1) (2026-08-03)


### Bug Fixes

* derive support footprint edges through the game's collision predicate ([538d554](https://github.com/Leg0shii/ParkourCalculatorMod/commit/538d5543cef853f4b4084a9d7018946adc65ff0d))
* **solver:** keep the scenario seed on a window anchored at tick 0 ([#242](https://github.com/Leg0shii/ParkourCalculatorMod/issues/242)) ([646918e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/646918eb3c2fdf8f9bfb8b5de5e7967d7d146592))


### Continuous Integration

* add dev release train and automated Modrinth publishing ([749430e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/749430ed9c1af6ac7d4efc11654525cc256b0258))
* include refactor, docs, ci and chore commits in the release changelog ([a615685](https://github.com/Leg0shii/ParkourCalculatorMod/commit/a61568597b8dfded85e2fedb68b56c35c09f3e55))


### Miscellaneous Chores

* cleanup ([8299988](https://github.com/Leg0shii/ParkourCalculatorMod/commit/829998852891ca8a6520a8188c2d6ad9bb722a35))
* update README.md ([c32b8e3](https://github.com/Leg0shii/ParkourCalculatorMod/commit/c32b8e3946ec3b0693955a798a6fecf949e8c791))

## [1.7.0](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.6.1...v1.7.0) (2026-07-26)


### Features

* angle solver node editor ([#228](https://github.com/Leg0shii/ParkourCalculatorMod/issues/228)) ([bafa94e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/bafa94e9d4fd05472ccb6363937156486c53675e)), closes [#221](https://github.com/Leg0shii/ParkourCalculatorMod/issues/221)
* client-side ghost playback for multiplayer on all loaders ([ff64a44](https://github.com/Leg0shii/ParkourCalculatorMod/commit/ff64a446fdeba2d03fe8f7e1b212e810fb53066d))
* constraint hotkeys ([#208](https://github.com/Leg0shii/ParkourCalculatorMod/issues/208)) ([069e2e2](https://github.com/Leg0shii/ParkourCalculatorMod/commit/069e2e2277ce6e393e5b18bff3bc1240495450a6))
* ctrl-drag pitch gizmo and pitch facing arrows ([0a9adbb](https://github.com/Leg0shii/ParkourCalculatorMod/commit/0a9adbbe2963ebd057e5bd8213778b3bcf323219))
* ctrl+B adds ladder, slime, and ice cell constraints ([786e1dc](https://github.com/Leg0shii/ParkourCalculatorMod/commit/786e1dc9e8e98b9f348023f6bc8a9ba87f358bba))
* dF constraint with closed-form and free-start solving ([d80d3e2](https://github.com/Leg0shii/ParkourCalculatorMod/commit/d80d3e2477d1b5d760ac5b64703306b53e844844))
* **fabric-1.21.10:** update modern loader to 26.2 ([#161](https://github.com/Leg0shii/ParkourCalculatorMod/issues/161)) ([01b63c5](https://github.com/Leg0shii/ParkourCalculatorMod/commit/01b63c51c16347485cfaf5bf059efab129f68640))
* hit distance lines showing block reach per tick ([4ee965d](https://github.com/Leg0shii/ParkourCalculatorMod/commit/4ee965da4cc6817a4dddd39b8f9ae8b0bbe53581))
* relative X/Z and dX-vs-dZ constraints ([88da70a](https://github.com/Leg0shii/ParkourCalculatorMod/commit/88da70a5db9bb84d7843f093d262c81c21be03dd))


### Bug Fixes

* allow relative pitch turns up to 180 degrees ([78f8987](https://github.com/Leg0shii/ParkourCalculatorMod/commit/78f89876a3cc9845a62b0d565334e2b47d6a9b2c))
* bump imgui dep for mac support ([#206](https://github.com/Leg0shii/ParkourCalculatorMod/issues/206)) ([e6e8fbc](https://github.com/Leg0shii/ParkourCalculatorMod/commit/e6e8fbc067a0760687a3c05d68bf84109c4563e8))
* click on tick is at correct position in input table ([2720c46](https://github.com/Leg0shii/ParkourCalculatorMod/commit/2720c46acaecfd09cc461fb39398006005a690b3))
* constraint hotkeys use own raycast instead of vanilla crosshair pick ([03f7247](https://github.com/Leg0shii/ParkourCalculatorMod/commit/03f7247e2a3f91eea4f10b8ed49f4bf8059eede9))
* disable 'Save as...' button ([#231](https://github.com/Leg0shii/ParkourCalculatorMod/issues/231)) ([d3593ab](https://github.com/Leg0shii/ParkourCalculatorMod/commit/d3593abc94c3c00e1e1c505ba51552f17f76c921))
* land tight-spec near-misses via a budgeted pattern-B&B rescue at Optimize/Custom ([467dd0e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/467dd0e3d6b9c573e1afe306321b29bdf3c60b6d))
* patch path geometry tail in place instead of full rebake on partial resim ([3c2fb9f](https://github.com/Leg0shii/ParkourCalculatorMod/commit/3c2fb9f55019d426f66603185f3f24b6f2e2eae0))
* re-reference free-start solves into the footprint box when the start is dragged outside it ([101fd81](https://github.com/Leg0shii/ParkourCalculatorMod/commit/101fd8167b064daa819b390f0fd9c44c403b3530))
* remove all process-spawning bytecode to pass Modrinth AutoMod ([08603ea](https://github.com/Leg0shii/ParkourCalculatorMod/commit/08603ea8033afe9bc313d7d5c7e1d9da80c06a41))
* simplify solver ([8be49ac](https://github.com/Leg0shii/ParkourCalculatorMod/commit/8be49aca01984e0e5193472bf8e91bb35a73ee1b))
* sprint lag v2 ([4462340](https://github.com/Leg0shii/ParkourCalculatorMod/commit/4462340a1869303517e4e3d66067cc8132d67faf))
* stop the tick-table context menu from replacing open modals on right-click ([9ace8a0](https://github.com/Leg0shii/ParkourCalculatorMod/commit/9ace8a0af42da729b751b5f68a7f7d33cba37910))

## [1.6.1](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.6.0...v1.6.1) (2026-06-24)


### Bug Fixes

* velocity finder constraints ([8ba84bf](https://github.com/Leg0shii/ParkourCalculatorMod/commit/8ba84bf6f4548078befe85341b82eca41dc90b16))

## [1.6.0](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.5.1...v1.6.0) (2026-06-24)


### Features

* add ILS polish for short multijump sequences ([be18592](https://github.com/Leg0shii/ParkourCalculatorMod/commit/be1859232b96199996d6546ef39904546be2272c))
* **anglesolver:** in-world visualization of landing constraints ([#145](https://github.com/Leg0shii/ParkourCalculatorMod/issues/145)) ([#169](https://github.com/Leg0shii/ParkourCalculatorMod/issues/169)) ([19a435c](https://github.com/Leg0shii/ParkourCalculatorMod/commit/19a435c805fe462c5ada803dcf149f6d59d7aea3))
* **anglesolver:** keybind to add a block's landing constraints to the selected tick ([#115](https://github.com/Leg0shii/ParkourCalculatorMod/issues/115)) ([#153](https://github.com/Leg0shii/ParkourCalculatorMod/issues/153)) ([def54d3](https://github.com/Leg0shii/ParkourCalculatorMod/commit/def54d3b05a39813e947a336ef34cc0936618c5d))
* **anglesolver:** user-tunable solve budget ([#173](https://github.com/Leg0shii/ParkourCalculatorMod/issues/173)) ([fa6d17d](https://github.com/Leg0shii/ParkourCalculatorMod/commit/fa6d17d388101d30849c2fb79e3db267dbe8fa4d))
* better UX for solver result updates ([3608f0f](https://github.com/Leg0shii/ParkourCalculatorMod/commit/3608f0f32261c467910d670f3d6117ff255b8e30))
* edit start position, velocity, and attributes from a dedicated Start state table ([#166](https://github.com/Leg0shii/ParkourCalculatorMod/issues/166)) ([a748414](https://github.com/Leg0shii/ParkourCalculatorMod/commit/a748414086ecd7e96400a56970e9ec63853625be))
* **input-pane:** configurable columns, per-tick pitch, and mouse buttons ([#100](https://github.com/Leg0shii/ParkourCalculatorMod/issues/100), [#101](https://github.com/Leg0shii/ParkourCalculatorMod/issues/101)) ([#154](https://github.com/Leg0shii/ParkourCalculatorMod/issues/154)) ([1898e0d](https://github.com/Leg0shii/ParkourCalculatorMod/commit/1898e0d37533d283a2350df01f1286c30f725b9a))
* move a tick to target coordinates by shifting the start ([#132](https://github.com/Leg0shii/ParkourCalculatorMod/issues/132)) ([#149](https://github.com/Leg0shii/ParkourCalculatorMod/issues/149)) ([8f1eac1](https://github.com/Leg0shii/ParkourCalculatorMod/commit/8f1eac15f7c675ffda2fbd1fa9576d3d00c07dfe))
* **playback:** replay from the selected tick or range ([#129](https://github.com/Leg0shii/ParkourCalculatorMod/issues/129)) ([#148](https://github.com/Leg0shii/ParkourCalculatorMod/issues/148)) ([2a10768](https://github.com/Leg0shii/ParkourCalculatorMod/commit/2a107681bf86c141698a00ca333fb2939549b084))
* ranges show differences now as well ([78cf061](https://github.com/Leg0shii/ParkourCalculatorMod/commit/78cf0618051d97ee8ecaaaf9a420bea3b53c1ed8))
* **ui:** browse sub-folders in the Open dialog ([#108](https://github.com/Leg0shii/ParkourCalculatorMod/issues/108)) ([#147](https://github.com/Leg0shii/ParkourCalculatorMod/issues/147)) ([34be334](https://github.com/Leg0shii/ParkourCalculatorMod/commit/34be334c81d35b7cc57c96f91606da833dc081d5))
* **ui:** configurable Tick Info window ([#143](https://github.com/Leg0shii/ParkourCalculatorMod/issues/143)) ([#168](https://github.com/Leg0shii/ParkourCalculatorMod/issues/168)) ([b006af6](https://github.com/Leg0shii/ParkourCalculatorMod/commit/b006af6c30b28e69af76d5c408b0596e459e681d))
* **ui:** reorder input tick right-click context menu into segments ([#140](https://github.com/Leg0shii/ParkourCalculatorMod/issues/140)) ([#167](https://github.com/Leg0shii/ParkourCalculatorMod/issues/167)) ([cbefee1](https://github.com/Leg0shii/ParkourCalculatorMod/commit/cbefee1716fbe6dce8556c2c24ac3c3c6378ce54))
* velocity finder: sweep launch velocities against a landing pad and explore the results in a 2D/3D velocity map ([#181](https://github.com/Leg0shii/ParkourCalculatorMod/issues/181)) ([0bfb8f1](https://github.com/Leg0shii/ParkourCalculatorMod/commit/0bfb8f1da25a9369f880833d236fea784aa72f2f))


### Bug Fixes

* align macro launch onGround with the sim for airborne starts ([#105](https://github.com/Leg0shii/ParkourCalculatorMod/issues/105)) ([#150](https://github.com/Leg0shii/ParkourCalculatorMod/issues/150)) ([1ea675d](https://github.com/Leg0shii/ParkourCalculatorMod/commit/1ea675dca2fed7d3f41288e3992c1db726cb9630))
* **anglesolver:** full-height tick-row hitbox and aligned start/goal accent ([#135](https://github.com/Leg0shii/ParkourCalculatorMod/issues/135), [#136](https://github.com/Leg0shii/ParkourCalculatorMod/issues/136)) ([#146](https://github.com/Leg0shii/ParkourCalculatorMod/issues/146)) ([e93ad4e](https://github.com/Leg0shii/ParkourCalculatorMod/commit/e93ad4ed51b4d5c18529d6f7a99cef75abe14a6c))
* cancel in-flight velocity sweep when the view changes ([#184](https://github.com/Leg0shii/ParkourCalculatorMod/issues/184)) ([290deea](https://github.com/Leg0shii/ParkourCalculatorMod/commit/290deea89a23eb681567c0533736f13d4d467a1f))
* disable sound for simulator entity ([85f0e5b](https://github.com/Leg0shii/ParkourCalculatorMod/commit/85f0e5b0a927e58a4f65f493873a012f48ce5d90))
* **forge-1.8.9:** let vanilla animate body yaw during playback ([#117](https://github.com/Leg0shii/ParkourCalculatorMod/issues/117)) ([#152](https://github.com/Leg0shii/ParkourCalculatorMod/issues/152)) ([eb7fee3](https://github.com/Leg0shii/ParkourCalculatorMod/commit/eb7fee32932c782e203b0f921ed435c059c91d85))
* **forge-1.8.9:** reset glColor4f after world/HUD overlay so hotbar stays opaque in F5 ([#97](https://github.com/Leg0shii/ParkourCalculatorMod/issues/97)) ([#151](https://github.com/Leg0shii/ParkourCalculatorMod/issues/151)) ([39922bf](https://github.com/Leg0shii/ParkourCalculatorMod/commit/39922bf9f794a7fd549bc3f72242fe3245b117be))
* input overlay rendering over window ([e812a1b](https://github.com/Leg0shii/ParkourCalculatorMod/commit/e812a1bd4b37fdb20d9739f4a1a373eb901bf9f7))
* read pre-tick (k-1) state for tick rows and align pitch handling ([1ef4d45](https://github.com/Leg0shii/ParkourCalculatorMod/commit/1ef4d45399e535d120bb9c396c1d7c5b46c4788c))
* remove enableLighting as it dropped the alpha value of the hotbar ([79e0c58](https://github.com/Leg0shii/ParkourCalculatorMod/commit/79e0c58bc13f6b7fbb0bf25b6615c5acfbf0a3f2))
* rendering depth fighting ([c749b11](https://github.com/Leg0shii/ParkourCalculatorMod/commit/c749b111996f179395a4942338bb9d6ca1a3ac22))
* resimulate from current tick after solve ([7ba3c63](https://github.com/Leg0shii/ParkourCalculatorMod/commit/7ba3c63215782f759d96dab3365c937d4a2a587c))
* tick player hitboxes, add Sprint for ticks, warning on solver ([21763e3](https://github.com/Leg0shii/ParkourCalculatorMod/commit/21763e3f63b5623c9cff31181eaccd705de4ae60))
* tick table inconsistencies ([4d8581f](https://github.com/Leg0shii/ParkourCalculatorMod/commit/4d8581f3ed3d78ba8b0426b8e48fe33b49d3b73b))
* yaw gizmo circle rendered in wrong place ([5bff20b](https://github.com/Leg0shii/ParkourCalculatorMod/commit/5bff20bf1e541969aa361a488889b72ecffe2e8d))


### Code Refactoring

* replace MC-internal reflection with access transformers in Forge loaders ([6957109](https://github.com/Leg0shii/ParkourCalculatorMod/commit/695710951ab289781760ceeae4461c09fe66959a))

## [1.5.1](https://github.com/Leg0shii/ParkourCalculatorMod/compare/v1.5.0...v1.5.1) (2026-06-13)


### Bug Fixes

* tick hitboxes ([9e0f3cd](https://github.com/Leg0shii/ParkourCalculatorMod/commit/9e0f3cddddfe10921d6433417936f223b6068905))

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
