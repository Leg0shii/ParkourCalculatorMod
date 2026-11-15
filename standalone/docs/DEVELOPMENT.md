# Writing code and fixes

The current runtime baseline is `dd738c98`. Before changing candidate behavior, read the [combination/pruning/IPOPT execution reference](ANCHOR_SEARCH_EXECUTION_REFERENCE.md). Reuse `search.comparison` for intermediate predicates and `InteractionGate` for exact-model preparation/reachability. Do not add another local count rule, queue-wide bound scan, constant-zero default, future-count comparison for open prefixes, or source-hop ban. The hard-transfer no-stepping assumption and its ceiling-only conditional duration bounds are explicit domain policy, not a general theorem.

Read [AGENTS](../AGENTS.md), [current state](CURRENT_STATE.md), [architecture](ARCHITECTURE.md) and the affected module in [code map](CODE_MAP.md). The DFS/early-interaction implementation and later virtual-finish/local-run fixes imported through `61355976` are the baseline for further work. Continue in the isolated worktree and preserve unrelated edits. Only commit after a feature implementation, bundling its related documentation, tests and fixtures; investigation/planning/documentation-only turns do not require a commit. No agent delegation or external communication is required.

## Where new behavior belongs

- Shared movement/contact/constraint rules: extend existing `src/parkour_solver` builders and shared Java compiler/scene helpers. Avoid a second derivation of stepping, signed blockage faces or player footprint geometry.
- Generic reachability: `reachability.intervals.py` and callers, using actual model/scope context. Eligibility must occur before generation/derived rows/checks across all families. `reachability.linear.py` is an optional numerical relaxation screen, not a change to IPOPT physics.
- Discrete branch traversal, comparison, persistence: `search.dfs`, `search.frontier`, `search.comparison`, `search.deferrals`, `search.engine`, `comparison_goals` and existing repair/schema helpers. `time_progress` is the legacy common-clock record adapter, not the active anchor pruning owner. Do not embed fixture IDs in these modules.
- Fixed continuous equations, scalar replay, native process lifecycle: `src/parkour_solver/numerics`. Do not place new discrete decision ownership inside the worker.
- One-off reproduction/measurement: a clearly named script in an experiment directory, with settings, source/provenance and a result note. Extract shared logic into production modules when genuinely reused.
- Mod physics inspiration and lifting: [mod references](MOD_REFERENCES.md); compile/reuse current checked-in classes rather than materializing historical copies or importing MC loader classes into core.

## Fix workflow

1. Trace root cause with the [debugging workflow](RESULTS_AND_DEBUGGING.md); distinguish generation, assembly, numerical solve, validation, retention and continuation failures.
2. State a generic invariant. Examples: impossible interactions never reach any consumer; a projected prefix cannot retain an uncommitted removed event's accidental approach rows; completed exact-context work is not redispatched after resume.
3. Reproduce minimally, then add a meaningful generic regression plus any useful Real11 regression. Include a feasible counterexample to each new rejection rule, required contacts, uncertainty and phase boundaries. Translations/reflections/changed schedules expose hidden coordinate assumptions.
4. Reuse current constraints/coefficient and scene checks. Leave all original-state facings free. A scored arrival child must carry its actual discrete program; comparison-only rows cannot become task constraints.
5. Preserve all unfinished work and accepted incumbents. Version persistence when semantics change. Separate unknown, proven exclusion, exhausted catalogue and heuristic drop. Do not silently deserialize old raw queues into a new traversal as exact continuation.
6. Run focused checks, then affected broad standalone suites. Report skipped/native tests and known baselines. A cold run and a seeded control answer different questions. Complete false-pruning audits when exclusions are changed.
7. Update current decisions/plan status, commands/schema notes and evidence manifests at the implementation boundary. Do not claim a plan is shipped before tests and call-site coverage exist.

## Style and formatting

Packaging and pinned optional dependency groups live in `pyproject.toml`; there is no standalone Black/Ruff configuration. Follow the surrounding Python style, four-space indentation and existing imports/data records; avoid broad cosmetic rewrites. Keep dependency versions pinned in the package extras; research requirements remain separate and optional at the established API boundaries. Use the standard `unittest` runner and current environment flags.

Prefer pure serializable search state, stable keys and deterministic ordering. Canonicalization must include physics, contact ownership, scope and remaining resource/cap capabilities when relevant. Equal trajectories or equal jump/run counts alone do not establish equivalence. Do not use floating-point string rounding to merge distinct physical models. When saving evidence use `json.dumps(..., allow_nan=False)` and deterministic gzip `mtime=0`, preserve exact doubles, atomic progress/output writes and stable source hashes.

Markdown uses descriptive relative links and small comparable tables. Put blank lines before lists. Long inventories and source-era narrative stay in supporting docs, not AGENTS. New public files need a purpose in the code/file map, new modules/functions need navigable pointers, and commands must be verified against actual argparse/API signatures. Generated indexes are snapshots; refresh them when files/functions move.

## Boundaries on claims

Conservative interval/phase exclusion differs from a numerical LP infeasibility result with tolerances. IPOPT is a local continuous solver, not a global discrete certificate. A guide is not a reach envelope. A valid supplied-scene trajectory is not validated against undeclared world blocks. Keep exact local replay and actual-world validation as separate claims. These distinctions should appear in output and audit records, not as implementation jargon in user-facing product flows.


For a new named contact case, make its vertical contact metadata explicit through the existing profile/compiler path. Reachability must cover intended movement and collision probes, not just realized endpoints. Current named step/grind phases deliberately keep unknown Y and outer intended X/Z bounds. Tightening that uncertainty requires a sound probe model and feasible-control regressions (including the five-input grind/blip route), not an endpoint-height shortcut.
