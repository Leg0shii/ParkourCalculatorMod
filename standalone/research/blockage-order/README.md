# Directed blockage timing checked against Minecraft

2026-10-26. This investigation uses the cached Minecraft 1.8.9 MCP runtime's own `AxisAlignedBB.calculateYOffset`, `calculateXOffset` and `calculateZOffset` methods, called in the order used by `Entity.moveEntity`. It does not port another collision engine.

`BlockageOrderProbe.java` tries the six ordered axis pairs and all four combinations of signed faces, for 24 cases. Each case starts outside the first face and ends outside the second, with no outside-both endpoint. The remaining axis overlaps the solid. Both full-tick endpoints are collision-free. It also checks a two-movement control that visits an outside-both corner state before switching sides.

| Transition | Direct corner handoff | With outside-both corner control |
| --- | --- | --- |
| X -> Z | X clips | Clear |
| Y -> X | Y clips | Clear |
| Y -> Z | Y clips | Clear |
| Z -> X | Clear | Clear |
| X -> Y | Clear | Clear |
| Z -> Y | Clear | Clear |

All 24 cases and 48 control movements pass their expected outcome. `results.json` retains the signed cases, actual widened player dimensions, start/end positions and runtime class hashes. These are collision-order probes with arbitrary intended displacements, not complete parkour trajectories, timing minima or step-assist tests.

The mod's `SimulatorEntity` delegates to Minecraft movement. The inspected runtime's `Entity.moveEntity` performs Y, X and Z clipping sequentially and moves the box between each axis. Its step-assist path separately compares two step candidates with the ordinary clipped path. Those branches require the shared scene implementation; this probe intentionally exercises the ordinary axis sequence only.

The integration agent's `StaticScene` and `StaticSceneTest` have been imported unchanged from `f057c0c` into this branch. All seven shared scene tests pass. The full native fast suite ran 798 tests with the same three already recorded failures, `CrossJvmDeterminismTest`, `InertiaFoldingTest` and `CostateSmoothingScaleTest`; none is a new scene test. `integration-reference.json` records the reviewed handoff/helper identities. Isolated reference copies and temporary decompilations live under the ignored `standalone/autonomy/build/obstacle-research` directory, not in the integration agent's worktree.

To reproduce with the version-correct cached MCP jar:

```sh
mc_jar=/path/to/forge-1.8.9-MinecraftForge-FG2-merged+MinecraftForge-FG2+forge-1.8.9-11.15.1.2318-1.8.9-mcp.jar
probe_out=standalone/autonomy/build/obstacle-research/probe
mkdir -p "$probe_out"
javac -cp "$mc_jar" -d "$probe_out" standalone/autonomy/experiments/blockage-order/BlockageOrderProbe.java
java -cp "$probe_out:$mc_jar" BlockageOrderProbe
```

Use a local JDK and the actual MCP jar from the loader cache. The probe fails with an assertion if the expected edge timing or any outside-both control disagrees. It prints the case results as JSON; saved metadata additionally identifies the inspected runtime classes.

The resulting [obstacle/blockage plan](../../OBSTACLES_AND_BLOCKAGES.md) separates finite collision geometry, profile-dependent landing/contact roles, directed edge timing and total-input route search. Shared movement compilation is adopted from the integration work before adding intentional wall-hit profiles; no alternate sprint state machine is proposed.
