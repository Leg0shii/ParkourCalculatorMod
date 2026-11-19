# Reusing the broader Parkour Calculator

Relative links below resolve inside the same worktree, so they show the mod source at the same revision as this standalone code.

## Orientation and rules

- [CONTEXT.md](../../CONTEXT.md): domain vocabulary, ticks, velocity versus displacement, jump/landing names, stepping cases.
- [Root AGENTS](../../AGENTS.md): module navigation, Java/toolchain and integration constraints. Left unchanged by this documentation pass. The user's standalone test scope governs ordinary work here.
- [Physics mirror](../../docs/reference/mcpk): authoritative legacy movement constants, collision order and community mechanics; [vision](../../docs/VISION.md), [coding guide](../../docs/CODING_GUIDE.md), [contributing](../../CONTRIBUTING.md): broader design and module conventions.
- [Research](../../docs/research): native angle-solver design and recovery investigations. Consult when lifting an existing method, not as an instruction to integrate this prototype into the UI.

## Existing sources to use

| Need | Source and purpose |
| --- | --- |
| Float movement, sine table, inertia | [ExactJumpModel.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/ExactJumpModel.java), [Constants.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/Constants.java), [McSineTable.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/McSineTable.java) |
| Input-to-coefficient compilation | [JumpPhysicsInputs.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/JumpPhysicsInputs.java), through [CompileRunTicksPhysics.java](../native/java/autonomy/CompileRunTicksPhysics.java); [generate_coefficient_tables.py](../tools/generate_coefficient_tables.py) records its rows for the in-process bridge and lists the sources that define them |
| Selected blocks to neighbor-aware constraint bounds | [ConstraintKeyController.java](../../core/src/main/java/de/legoshi/parkourcalc/core/ui/ConstraintKeyController.java), [ConstraintDeriver.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/ConstraintDeriver.java), [BlockSelection.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/BlockSelection.java), [SupportOverlap.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/SupportOverlap.java) |
| Finite-scene collision/stepping/sneak checks | [StaticScene.java](../../core/src/main/java/de/legoshi/parkourcalc/core/replay/StaticScene.java), ported to [static_collision.py](../src/parkour_solver/scene/static_collision.py) with the Java bridge as differential oracle |
| Executable input and sprint state | [PlayerSprintMachine.java](../../forge-core/src/main/java/de/legoshi/parkourcalc/forge/core/sim/PlayerSprintMachine.java), [LocalReplayOracle.java](../../forge-core/src/main/java/de/legoshi/parkourcalc/forge/core/replay/LocalReplayOracle.java) |
| Neighbor-angle repair, replay/export acceptance | [IpoptReplay.java](../../core/src/main/java/de/legoshi/parkourcalc/core/replay/IpoptReplay.java), [ReplayStandaloneIpopt.java](../native/java/ipopt/ReplayStandaloneIpopt.java) |
| Existing native continuous methods | [ClosedFormSolve.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/ClosedFormSolve.java), [SlpSolve.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/SlpSolve.java), [BoundPrunedRecovery.java](../../core/src/main/java/de/legoshi/parkourcalc/core/anglesolver/solver/BoundPrunedRecovery.java); root AGENTS maps related certified B&B stages |
| Mod wiring if explicitly integrating later | [Application.java](../../core/src/main/java/de/legoshi/parkourcalc/core/Application.java); input/start changes must retrigger simulation. Existing standalone work does not alter that wiring. |

## Constraint derivation runbook

Use [MOD_REFERENCES.md](MOD_REFERENCES.md) for exact conventions. The mod's key-selection flow supplies support boxes, nearby obstacle boxes, reference point, facing and collision mode to `deriveFootprint`. `SupportOverlap` includes precise player overhang; obstacle clipping handles body/corners. The standalone convenience footprint operation currently passes no obstacle list, so it is not a replacement for neighbor-aware derivation. [DeriveSelectedFootprints.java](../research/linkcraft11-opening/DeriveSelectedFootprints.java) demonstrates calling the real code with neighbors.

A GUI-selected footprint rectangle is one selected region, not the full nonconvex support domain. Preserve unions as multiple support regions/cells with the same physical owner; never create collision walls on artificial seams. The player's legacy half-width is float-promoted `0.30000001192092896`, height is float-promoted `1.8F`. Do not use hand-rounded `0.3` in tight production constraints or expand already-authored player-center rows twice.

Core and forge-core remain MC-free Java 8 code; loader code is MC-specific. Standalone Java helpers reuse compiled current sources. A local helper build is different from launching Minecraft or running mod-wide test suites. If changing shared Java, read its module rules and validate that change appropriately; Python/docs iteration does not justify rebuilding all loaders.
