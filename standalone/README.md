# Standalone route search and IPOPT

Start at [AGENTS.md](AGENTS.md). Current work is on `research/ipopt-cell-routes` in the isolated research worktree, continuing the older IPOPT consolidation. The continuous solver, movement composition, collision validation, local replay and TAS export already exist.

Current physical anchor search is implemented through `dd738c98`. The [execution reference](docs/ANCHOR_SEARCH_EXECUTION_REFERENCE.md) gives the combination/pruning/IPOPT tables, decisions and rationale; [current evidence](autonomy/experiments/hard-jump-comparisons/README.md) records600 native tests and the cold T64 result. Earlier DFS/corridor plans are source-era design/evidence.

| Task | Entry |
| --- | --- |
| Understand current behavior and limitations | [Current state](docs/CURRENT_STATE.md) |
| See generated combinations, pruning and IPOPT dispatch | [Execution reference](docs/ANCHOR_SEARCH_EXECUTION_REFERENCE.md) |
| Answer Linkcraft Real11 audit questions | [Real11 guide](docs/REAL11_GUIDE.md) |
| Understand modules/functions | [Architecture](docs/ARCHITECTURE.md), [code map](docs/CODE_MAP.md) |
| Execute a test, solve or resume | [Runbook](docs/RUNBOOK.md) |
| Navigate research, plans and evidence | [Documentation index](docs/DOC_INDEX.md) |

The [optimizer contract](ipopt/README.md), [constraint generation runbook](autonomy/CONSTRAINT_GENERATION_RUNBOOK.md) and [replay contract](ipopt/REPLAY.md) supply detailed formats. [CONSOLIDATION.md](CONSOLIDATION.md) records historical integration provenance; its old branch designation is not a current instruction.
