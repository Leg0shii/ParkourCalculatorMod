# Standalone documentation guide

A fresh agent should start at [standalone/AGENTS.md](../AGENTS.md) and [current state](CURRENT_STATE.md). The current worktree is `research/ipopt-cell-routes`; older consolidation branch instructions are historical. Documentation consolidation was followed by the DFS/early-interaction implementation at `498c054c`; see the current-state guide for behavior and validation.

| Task | Reading path |
| --- | --- |
| Integrate changes since the original AGENTS snapshot | [Corridor-agent handoff](CORRIDOR_AGENT_HANDOFF.md) |
| Answer a Real11 question now | [Real11 guide](REAL11_GUIDE.md) → exact audit/question/call artifact |
| Understand execution and file ownership | [Architecture](ARCHITECTURE.md) → [code map](CODE_MAP.md) → [function index](FUNCTION_INDEX.md) |
| Run/debug a case | [Runbook](RUNBOOK.md) → [testing](TESTING.md) → [results and debugging](RESULTS_AND_DEBUGGING.md) |
| Implement the approved changes | [Decisions/plans](DECISIONS_AND_PLANS.md) → [current plan](../autonomy/DFS_INTERACTION_IMPLEMENTATION_PLAN.md) → [development](DEVELOPMENT.md) |
| Reuse prior mod work | [Mod references](MOD_REFERENCES.md) → existing compiler/constraint/scene classes |
| Find older evidence or any file | [Document index](DOC_INDEX.md) → [complete file inventory](FILE_INDEX.md) |

## Consolidation and maintenance

The former accumulated pruning handoff is preserved verbatim after a historical banner in [PRUNING_RESEARCH_HISTORY.md](../autonomy/PRUNING_RESEARCH_HISTORY.md). [The replacement handoff](../autonomy/PRUNING_RESEARCH_HANDOFF.md) is concise and current. Old experiment pages carry source-era facts; newer status/navigation pages resolve their conflicting pending/shipped/policy language. Root AGENTS files were left unchanged.

`ParkourCalculatorMod/standalone` on this machine is a local symlink to the research worktree's standalone directory. Resolve the physical directory before finding the repository root. A separate clone should use its own actual research checkout; the symlink is not required by the code.

Inventories cover `git ls-files --cached --others --exclude-standard standalone`, excluding ignored build output/caches/virtualenvs. Code-map purposes are editorial. The function index uses Python AST for top-level definitions and class methods; nested callbacks are documented at their enclosing entry. The file index includes tests, fixtures, artifacts and documentation. Update these snapshots when files/functions change; source line numbers are hints, not permanent API IDs.

Documentation validation checks relative-link existence, inventory coverage, root AGENTS hashes, preservation of the original handoff and consistency of the existing audit ledger. Check docs with `git diff --check`; run `python3 -m unittest standalone.autonomy.tests.test_candidate_audit -v` for the small offline ledger gate. No native solve, mod test suite or game launch is required to validate a documentation pass. The source-era broad numerical baseline remains separately labelled in [testing](TESTING.md).
