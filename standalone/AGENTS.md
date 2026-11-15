# Standalone agent entry point

Read this first, then [docs/CURRENT_STATE.md](docs/CURRENT_STATE.md) and task-specific links below. This is the standalone workspace inside Parkour Calculator, not a separate physics project. Read the parent [domain glossary](../CONTEXT.md) when movement terminology is unfamiliar. The root `AGENTS.md` still applies; the user explicitly scopes ordinary iteration to standalone tests rather than the mod's full Gradle test gate.

## Find the right checkout

Current research branch: `research/ipopt-cell-routes`. Physical worktree on this machine: `/Users/arvindmurty/CS/ParkourCalculatorMod-ipopt-cell-routes`. Older instructions naming `feature/ipopt-consolidated` describe consolidation ancestry, not the current working branch. Continue in the isolated worktree; do not implement in the main checkout.

The main checkout's `standalone` may be a local symlink here. Start with `pwd -P`, then `git rev-parse --show-toplevel` from the resolved directory. Run repository-root commands from that physical worktree. Do not use the symlink's logical parent as the repository root. The link is local setup, not a tracked portable checkout layout.

## Current task and authority

Documentation consolidation precedes implementation. The current **approved, unimplemented** runtime change is [DFS_INTERACTION_IMPLEMENTATION_PLAN.md](docs/design/DFS_INTERACTION_IMPLEMENTATION_PLAN.md): goal-aware ordered depth-first completion, one early block/tick/phase interaction gate, effective-model deduplication and constraint ownership, with persistent backtracking. Current runtime still uses checkpoint-window traversal and shortest-prefix priority. Do not describe the plan as shipped.

Use [decisions and plans](docs/DECISIONS_AND_PLANS.md) to distinguish current requirements, implemented behavior, deferred ideas and history. The long research history contains superseded proposals. User corrections and current plans take priority. Real11 motivates regressions; never special-case its block IDs, ticks or coordinates in generic algorithms. No other-agent communication is needed. Commit at coherent boundaries, not after every turn. Update current docs when decisions or measured facts change.

## Modeling rules

- The orchestrator chooses **all discrete inputs, events and hard constraints before IPOPT**. Reuse existing movement/constraint builders. Facings remain variables.
- Every re-solve starts at the full original position and stored velocity; commit decisions, never intermediate optimized X/Z/velocity or earlier facings.
- Current decision research disables **inertia thresholds**, not momentum. Carry normal velocity. Focus on normal/run/jump/landing/stepping/ceiling/blockage; water, ladder expansion and intentional wall-hit search are deferred. Existing ladder/replay implementations remain available.
- State `t` is before input `t`; input `t` produces state `t+1`. AABBs are `[minX,minY,minZ,maxX,maxY,maxZ]`. Raw collision boxes, player-center support bounds, cells and guide points differ.
- Unknown/timeouts/caps/local NLP failure are not infeasibility or optimality certificates. Numerical feasibility, vertical validity, scene acceptance, corridor acceptance and executable replay are separate claims.
- Compare time at the same spatial goal, objective at the same absolute tick with compatible schedules. Preserve agreed position/velocity/schedule protections, close alternatives and unfinished work. Margins remain 0.25 blocks and 1 tick.

## Read only what the task needs

| Need | Read |
| --- | --- |
| Immediate Real11 questions | [Real11 guide](docs/REAL11_GUIDE.md), then linked candidate/question/call ledgers |
| Code structure and function flow | [Architecture](docs/ARCHITECTURE.md), [code map](docs/CODE_MAP.md), [function index](docs/FUNCTION_INDEX.md) |
| Run, resume, build helpers, replay/export | [Runbook](docs/RUNBOOK.md); detailed [constraint runbook](docs/design/CONSTRAINT_GENERATION_RUNBOOK.md) |
| Tests and fixtures beyond Real11 | [Testing](docs/TESTING.md) |
| Interpret output or debug failure | [Results and debugging](docs/RESULTS_AND_DEBUGGING.md) |
| Write a fix or add a case | [Development](docs/DEVELOPMENT.md) |
| Reuse mod physics/constraints | [Mod references](docs/MOD_REFERENCES.md) |
| Find any document or tracked file | [Documentation index](docs/DOC_INDEX.md), [file index](docs/FILE_INDEX.md) |

Use focused standalone tests and widen according to affected scope. Do not launch Minecraft, rebuild helpers unnecessarily or run mod test suites for documentation/Python-only iteration. See the runbook for native-worker dependencies and exact commands. Preserve historical evidence; produce new runs in new output paths.
