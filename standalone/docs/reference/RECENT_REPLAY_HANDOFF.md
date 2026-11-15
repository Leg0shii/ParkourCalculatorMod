# Replay and movement handoff, updated 2026-10-28

Consolidation update, 2026-10-30: the user requested one branch after the integration agent stopped. Current development is on `feature/ipopt-consolidated`; solver, orchestration, shared movement compilation and local replay are combined. See [standalone setup](../README.md) and [the consolidation record](../CONSOLIDATION.md). Branch/import instructions later in this document are historical; no cross-branch import is required on the consolidated branch. Physics/replay limitations below remain applicable.

This is the cumulative update for the autonomous-solve agent, including the local replay implementation, movement compilation fix and latest user decisions. It supersedes the deployment/planning paragraphs in the older handoff.

## Ownership and delivery boundary

You own input/run-tick/route search, candidate retention and retry policy. This integration agent owns movement compilation, executable angle realization, local replay and TAS export. The intended pipeline is:

**choose rows/timing/contact schedule → compile movement → Python solve → exact local angle repair/replay → validated TAS JSON → user imports into the mod for final actual-world validation.**

No running Minecraft client or mod is needed for the standalone steps. A complete scene export is optional, not a prerequisite. Final validation against the actual world's blocks belongs to the mod. Local accuracy is conditional on the declared contract covering the supported movement: swept player clearance, contact reach/approach and face extents, support/ceiling/step transitions, ladder occupancy, friction and carried input state. Endpoint X/Z bounds alone do not establish that coverage. Current declared-contact mode does not verify arbitrary clearance or finite face extents; improving those contract checks remains integration work.

## What landed since the original handoff

- `133b48c`: local Java replay, optional static-scene collision discovery, declared-contact replay, nearby executable-angle repair, strict original constraints/objective give-back and SaveIO JSON export followed by fresh reload/replay. The existing `ExactJumpModel` gained an additive collision hook; its normal native-solver path remains unchanged. No live Application/loader integration was added.
- `af12801`: fixed movement compilation and replay to share the mod's input state. Added `FixedMovementCompiler`, `ReplayInputState` and `ReplayRows`; reused production `PlayerSprintMachine`, existing exact movement kernel and float-friction helper. Added optional `movement` assumptions to the Python validator and a recompilation command. The 1.8.9 exporter now uses this compiler automatically.
- `264f6b9`: source map and explicit supported/unported mechanics in [MOVEMENT_PORT.md](MOVEMENT_PORT.md).
- `3554994`: first short replay handoff. `f057c0c` records the latest user decision assigning final world validation to the mod; this supersedes the scene-capture-first plan in `8fcb011`.

**Important correction:** required wall hits cancel sprint. Legacy air acceleration carries the previous tick's sprint factor, so its decrease/recovery lags one tick. The old real11 benchmark arrays assumed sprint through these hits. This caused the previous 400-input candidate to miss the state-190 wall, then diverge in ladder/Y behavior. It was a coefficient/input-state mismatch, not proof that IPOPT could not solve the route. Corrected compilation resolves that failure.

Legacy numerical fixtures remain unchanged for benchmark provenance. Recompile before game-accurate solving/replay. Old yaw can be a warm start; old feasibility flags, hashes and objective targets do not transfer to the changed physics.

## How orchestration should use it

Recompile after changing keys, inserted run ticks, ground/contact timing, effects or resume state. Reindex every tick-based constraint/event/reference when inserting rows. Prefer `KEEP` when you already chose exact keys; FORCE_45 deliberately edits them. Avoid copying sprint, cooldown or float input arithmetic into the planner.

The compiled `movement` metadata records horizontal-collision, ground, sprint/air-factor and processed-input assumptions. Python independently checks collision timing, plus ground when vertical replay is supplied. A changed event schedule is feedback for recompilation/re-solving, never an infeasibility certificate. The optional metadata is backward compatible; generic requests without it still run but do not gain those checks.

Key code:

| Location | Purpose |
| --- | --- |
| `forge-core/.../replay/FixedMovementCompiler.java` | Compile effective rows with fixed ground/contact assumptions. |
| `forge-core/.../replay/ReplayInputState.java` | Shared input/sprint/cooldown/air-factor state. |
| `forge-core/.../replay/LocalReplayOracle.java` | Full local exact replay from saved start/resume. |
| `core/.../replay/ReplayRows.java` | Shared effective row policy. |
| `core/.../replay/IpoptReplay.java` | Angle repair, original-row validation and accepted TAS realization. |
| `core/.../replay/DeclaredContacts.java`, `StaticScene.java` | Prescribed contacts or supplied-scene collision discovery. |
| `standalone/ipopt/model.py` | Independent continuous acceptance and movement-assumption guards. |

Commands from your worktree root:

```sh
./gradlew -I standalone/ipopt/export.gradle :core:compileStandaloneIpoptPhysics \
  -PipoptCapture=/path/to/template.json \
  -PipoptInput=/path/to/problem.json \
  -PipoptOutput=/tmp/corrected-problem.json

python3 -m standalone.ipopt.solve \
  --input /tmp/corrected-problem.json --output /tmp/result.json \
  --python /path/to/your/ipopt/python --seconds 10

./gradlew -I standalone/ipopt/export.gradle :core:replayStandaloneIpopt \
  -PipoptCapture=/path/to/template.json \
  -PipoptInput=/tmp/corrected-problem.json \
  -PipoptResult=/tmp/result.json \
  -PipoptOutput=/tmp/realized-tas.json \
  -PipoptReport=/tmp/replay-report.json \
  -PipoptReplaySeconds=5 -PipoptGiveBack=0.0001
```

On this machine add `-Dorg.gradle.java.home=/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home` to Gradle commands to override the Windows path in repository settings. An existing worker interpreter can be used read-only at `/Users/arvindmurty/CS/ParkourCalculatorMod/tools/angle-model-comparison/.venv/bin/python`; do not install into another agent's environment.

Add `-PipoptScene=/path/to/scene.json` for optional discovery. See [REPLAY.md](REPLAY.md) for the schema/API. Use the same corrected request throughout solving and replay. These are separate commands today; a single pipeline command is not implemented yet.

Only a current report with `status=validated` establishes accepted export. Failed replay leaves an older output untouched. The output preserves the original prefix, truncates the unvalidated suffix and uses absolute float yaw locks. SaveIO serialization is loaded and replayed again before acceptance. Continuous `feasible` alone does not establish executable replay. Repair `unknown` means the bounded local search did not find an accepted witness, not infeasibility.

## Results and limits

Corrected real11 results with all original scalar rows:

| Inputs | Continuous solve | Exact repair/export | Continuous objective | Replay objective |
| --- | --- | --- | --- | --- |
| 200 | ~0.94 s | ~0.25 s | 737.5049097217485 | 737.5049071316392 |
| 400 | ~2.42 s | ~3.80 s | 691.5261822673284 | 691.5261153484987 |

Both had final row violation zero and passed fresh JSON-round-trip replay. The 400-input give-back is ~6.69e-5, within 1e-4. These are local measurements, not runtime/global-optimum guarantees. The raw recorded first 400 inputs also match all 401 recorded position/velocity states bit-for-bit without per-tick reseeding, including 34 wall states and 35 ladder states. Real11 has no complete scene snapshot, so these route results use declared contacts; discovery has separate supplied-geometry regressions.

Supported scope is the 1.8.9 static inert-block/ladder profile, with exact float movement, inertia, blocked-axis resets, step/sneak-edge handling in scene mode, sprint/cooldown and Speed/Slowness/Jump Boost/Blindness. Fluids, webs, soulsand/slime callbacks, damage, dynamic entities/blocks, interactions and other versions are not ported end to end. See the full ledger before representing new block types as inert boxes.

Declared-contact replay currently requires `source.startTick=0`. Nonzero compiler starts need recorded prefix state. General portable replay checkpoints/window state handoff are not finished: do not splice using only optimizer X/Z/VX/VZ. Complete carry includes Y/VY, ground/collision, yaw, sprint/input timers, previous air factor, jump cooldown and bounding box. Preserve the original prefix until an exact handoff API exists. Integration's next priorities are explicit contract checks, reliable one-command export/reporting and exact state carry; your orchestration search can continue independently.

Latest code verification: core fast JDK 21 and Java 8 each passed 868 tests (two skips); forge-core passed 125; Python passed 51 with the actual IPOPT worker. The earlier additive-kernel implementation also passed the full JDK 21 slow suite, 1122 tests (two skips). The slow suite was not rerun for the later compiler-only changes. No Minecraft acceptance is claimed.

## Getting the changes into your worktree

Implementation checkout: `/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-mod-integration`, branch `feature/ipopt-mod-integration`. The user has now authorized publishing this accumulated work to `origin/feature/ipopt-mod-integration`. Inspect the published tip before importing; do not edit this checkout or reset your own work.

Your `codex/run-ticks-planning` checkout has an older upstream base and its own standalone/orchestration changes. A blanket merge would also bring unrelated upstream changes. Review the focused implementation commits `133b48c` and `af12801` and their dependencies against your base, then port/cherry-pick in your own worktree with conflict review and tests. In particular reconcile `standalone/ipopt/model.py`, shared fixture evidence and Gradle/core API differences rather than overwriting your work. The older baseline import recipe is not a recipe for this update. Documentation can be read directly from the integration checkout or fetched branch.

Details: [MOVEMENT_PORT.md](MOVEMENT_PORT.md), [REPLAY.md](REPLAY.md), [FINDINGS.md](FINDINGS.md). No other worktree was modified for this handoff.
