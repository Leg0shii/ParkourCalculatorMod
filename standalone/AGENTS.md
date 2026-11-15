# Standalone agent entry point

This file is the entry point for work in `standalone/`. Start with the user's task and read only the supporting documentation needed for it. Current tasks, branch names, implementation plans and experiment status belong in the linked documentation, not here.

## Scope and checkout

Standalone work uses this file's guidance. Read the repository-root `AGENTS.md` and the affected module's guidance only when the task actually touches the mod: inspecting or changing its source, reusing its physics or constraint APIs, or integrating with its UI, simulation or loaders. Ordinary standalone Python, documentation and experiment work does not require the root instructions or mod-wide build/test gates.

Work in the checkout where the session started. If the path is symlinked or a command needs the repository root, use `pwd -P` and `git rev-parse --show-toplevel` to identify the physical worktree. Resolve parent-repository references there; do not switch to another checkout based on an old path in a document. Preserve unrelated edits.

## Orientation

Use [current state](docs/CURRENT_STATE.md) for ongoing work and [decisions and plans](docs/DECISIONS_AND_PLANS.md) to distinguish implemented behavior, approved changes, deferred ideas and historical proposals. A linked plan is context, not an instruction to begin that work. Verify relevant claims against the current code.

| Need | Read |
| --- | --- |
| Current capabilities and active plans | [Current state](docs/CURRENT_STATE.md), [decisions and plans](docs/DECISIONS_AND_PLANS.md) |
| Immediate Real11 questions | [Real11 guide](docs/REAL11_GUIDE.md), then linked candidate/question/call ledgers |
| Code structure and function flow | [Architecture](docs/ARCHITECTURE.md), [code map](docs/CODE_MAP.md), [function index](docs/FUNCTION_INDEX.md) |
| Run, resume, build helpers, replay/export | [Runbook](docs/RUNBOOK.md); detailed [constraint runbook](docs/design/CONSTRAINT_GENERATION_RUNBOOK.md) |
| Tests and fixtures beyond Real11 | [Testing](docs/TESTING.md) |
| Interpret output or debug failure | [Results and debugging](docs/RESULTS_AND_DEBUGGING.md) |
| Write a fix or add a case | [Development](docs/DEVELOPMENT.md) |
| Reuse mod physics/constraints | [Mod references](docs/MOD_REFERENCES.md) |
| Find any document or tracked file | [Documentation index](docs/DOC_INDEX.md), [file index](docs/FILE_INDEX.md) |

## Working practices

- Reuse existing models, movement/constraint builders and validation paths. Read the affected module's conventions before changing behavior; keep fixture-specific data out of generic algorithms.
- Follow the [feature workflow](docs/DEVELOPMENT.md#keeping-the-codebase-clean) for every implementation: identify the existing owner and callers, keep shared rules in one implementation, and use the [layout](docs/LAYOUT.md) to place production code, tools, research and data.
- Finish features with a cleanup review: remove superseded paths and temporary instrumentation, check dependency boundaries, run appropriate tests, and update affected documentation. Commit coherent working features with their related tests/docs/fixtures; documentation-only work does not require a commit.
- Run focused standalone checks and widen according to the affected scope. See the runbook and testing guide for commands and native dependencies. Rebuild helpers only when needed; launch Minecraft or run mod checks only when the task calls for them.
- Distinguish numerical feasibility, scene validation and executable replay. A timeout, cap or local solver failure is not an infeasibility or optimality certificate.
- Preserve historical evidence and write new experiment runs to new output paths. Record source revision, settings and validation scope when reporting measurements.
- Update relevant current documentation when behavior, commands or measured facts change. Keep task history and detailed design records out of this entry point.
