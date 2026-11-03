# Standalone route search and IPOPT

Start at [AGENTS.md](AGENTS.md). Current work is on `research/ipopt-cell-routes` in the isolated research worktree, continuing the older IPOPT consolidation. The continuous solver, movement composition, collision validation, local replay and TAS export already exist.

[Goal-aware DFS and early interaction eligibility](autonomy/DFS_INTERACTION_IMPLEMENTATION_PLAN.md) is implemented at `498c054c`. See [results and validation](autonomy/experiments/dfs-interactions/README.md) and the current-state guide for limits.

| Task | Entry |
| --- | --- |
| Understand current behavior and limitations | [Current state](docs/CURRENT_STATE.md) |
| Answer Linkcraft Real11 audit questions | [Real11 guide](docs/REAL11_GUIDE.md) |
| Understand modules/functions | [Architecture](docs/ARCHITECTURE.md), [code map](docs/CODE_MAP.md) |
| Execute a test, solve or resume | [Runbook](docs/RUNBOOK.md) |
| Navigate research, plans and evidence | [Documentation index](docs/DOC_INDEX.md) |

The [optimizer contract](ipopt/README.md), [constraint generation runbook](autonomy/CONSTRAINT_GENERATION_RUNBOOK.md) and [replay contract](ipopt/REPLAY.md) supply detailed formats. [CONSOLIDATION.md](CONSOLIDATION.md) records historical integration provenance; its old branch designation is not a current instruction.
