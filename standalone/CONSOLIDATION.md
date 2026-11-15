# Standalone branch consolidation

> Historical consolidation provenance. Current branch and instructions: [AGENTS.md](AGENTS.md), [current state](docs/CURRENT_STATE.md). The canonical branch named below is the consolidation source, not the active research branch.

2026-10-30. The user requested one branch for all standalone IPOPT work after the integration agent stopped. The canonical branch is `feature/ipopt-consolidated`, in `/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-consolidated`. Existing source branches/worktrees were preserved.

## Sources and scope

| Source | Included work / treatment |
| --- | --- |
| `feature/ipopt-mod-integration` at `e09f2a7` | Consolidation base: clean standalone solver, input/sprint compiler, local replay, angle repair, TAS export and latest cumulative handoffs |
| `codex/run-ticks-planning` at `658490a` | Reapplied the orchestration series starting at `e2a7b22`: run search, block discovery, finite obstacles/contacts, ladders, stepping, ceilings, common open composition, tests, evidence and pruning research handoff |
| `feature/ipopt-standalone` at `5ef088b` | Verified standalone solver, ladder model/worker, capture attachment and fixtures against the consolidated package; later movement guards and named stepping cases are retained |
| `feature/ipopt-clean` at `498ecc1` | Already an ancestor of the integration base, including its standalone package |

The integration base uses the clean native-mod revision `485c0a8`, rather than the inherited native solver experiments on the old orchestration/archive branches. Only the actual orchestration commit series was reapplied. The unrelated inherited native solver experiments, benchmark tool trees and old traces were not merged into the new native baseline. They remain available on their original branches. `feature/ipopt-local` is the archived optional in-mod UI/backend experiment, not the standalone package, and remains unchanged.

The orchestration series produced 66 commits on the new branch before consolidation-specific changes. Its shared `StaticScene` import commit, `8cd2aaa`, was empty because the integration base already had that source and tests; it was skipped. An add/add conflict in the original autonomous handoff retained the newer cumulative integration document. No numerical code conflict was resolved by dropping a side.

## Reconciliation

Before the consolidation-specific build/documentation edits:

- `standalone/autonomy/` matched the orchestration branch exactly.
- `standalone/ipopt/*.py` and its numerical tests matched the orchestration branch exactly, preserving the named stepping and movement-assumption extensions.
- The optimizer, ladder worker and original package tests from the standalone archive were present, with their later extensions. Integration's ladder evidence fixture is the newer enriched version used by its replay checks.
- All integration replay/compiler sources remain present. The only core-source differences from the integration tip are the orchestrator's extended `StaticScene` and its tests.

The old orchestration helper compiled a historical Git snapshot of the integration classes into ignored build storage because its native base predated those APIs. The consolidated branch already contains those same shared classes. `compiler.gradle` now builds the helper against the current core/Forge outputs, using the current extended `StaticScene`; `materialize_movement.py` is removed. There is one checked-in implementation of movement compilation and collision checking for orchestration and local replay.

Both Gradle init scripts register their tasks after core configuration and resolve Forge outputs lazily, so they work together with configuration-on-demand. Building them compiles Java helpers; it does not run the mod's test suite or start Minecraft.

## Validation

Completed on the consolidated worktree:

- Both init scripts build together with configuration-on-demand: `prepareRunTicksCompiler` and `compileStandaloneIpoptExport` pass using the current shared core/Forge classes.
- All 51 standalone numerical tests pass in 24.539 seconds with the actual native worker enabled.
- All 65 focused orchestration tests pass in 56.037 seconds with the newly built shared compiler and native worker enabled. Coverage includes generic composition, five airborne ladders, stepping, mixed contacts, regenerated ceiling schedules, endpoint stops and saved evidence.
- Original-state Real11 spans of 200 and 400 inputs were recompiled from the original TAS with the shared input/sprint compiler, solved by the consolidated Python package, then passed executable-angle repair and fresh TAS reload/replay. Both reports have `status=validated` and `outputWritten=true`. These are declared-contact local checks, not actual-world or complete-scene validation.
- Source-tree comparison confirmed the orchestration code and numerical modules matched their source before the build/documentation cleanup. All tracked package paths from the standalone, clean and integration branches are present. The old source branches were left unchanged.

Local source audit, corrected requests, numerical results, exported TAS files and replay reports are in ignored `standalone/autonomy/build/consolidation/`. The replay smoke used the existing read-only `/Users/arvindmurty/Downloads/LinkcraftII_Competition_01/real/11.json` template and the tracked `real11-200-ladders.json.gz` / `real11-400-ladders.json.gz` requests. New worktrees can reproduce it through the compile/solve/replay commands in [RECENT_REPLAY_HANDOFF.md](docs/reference/RECENT_REPLAY_HANDOFF.md).

No search/pruning policy or movement arithmetic changed. No mod Java test suite or Minecraft client was run. The local `.venv-ipopt` currently links to the existing orchestration runtime for read-only reuse; create a fresh environment from the pinned requirements before removing that older worktree. No source/build helper depends on an older worktree or historical Git extraction.

For a full standalone-work diff, compare `485c0a8` to this branch. For the added orchestration/consolidation work over the integration tip, compare `e09f2a7` to this branch. Old commit IDs in design records remain provenance pointers; their reapplied commits have new IDs.

PR preparation update: at the user's request, `origin/main` on `arvind-murty/ParkourCalculatorMod` was fast-forwarded from `02844f4` to upstream `485c0a8` before the standalone PR was opened. The PR base is the user's fork `main`, not the upstream repository. Its diff now isolates the standalone implementation and shared Java support. The research agent should fetch the current `origin/feature/ipopt-consolidated`; see the updated [research entry point](docs/research/PRUNING_RESEARCH_HANDOFF.md#research-checkout-and-review-context).
