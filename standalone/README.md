# Standalone parkour solver

The installable Python solver lives in [`src/parkour_solver`](src/parkour_solver).
Tests, research, native bridge sources and developer tools have separate roots.
The [layout guide](docs/LAYOUT.md) explains ownership, public entrypoints and
compatibility; the [implementation ledger](docs/LAYOUT.md)
records the tested recovery commits.

From `standalone/`, install into the existing numerical environment:

```sh
../.venv-guidance/bin/python -m pip install --editable . --no-deps
../.venv-guidance/bin/python -m parkour_solver --help
```

For a fresh environment, pinned extras are available; see the [runbook](docs/RUNBOOK.md).
The parent API uses the standard library. Numerical/LP/plot dependencies load
only when needed. Install the package in both interpreters if the worker uses a
separate Python environment.

| Need | Read |
| --- | --- |
| Current search behavior and limitations | [Current state](docs/CURRENT_STATE.md) |
| Generated combinations, pruning and IPOPT dispatch | [Execution reference](docs/ANCHOR_SEARCH_EXECUTION_REFERENCE.md) |
| Real11 evidence and questions | [Real11 guide](docs/REAL11_GUIDE.md) |
| Module ownership and execution | [Architecture](docs/ARCHITECTURE.md), [code map](docs/CODE_MAP.md) |
| Test, solve, resume, replay/export | [Runbook](docs/RUNBOOK.md), [testing](docs/TESTING.md) |
| Plans, deferred work and preserved research | [Decisions](docs/DECISIONS_AND_PLANS.md), [deferred-work register](docs/DEFERRED_WORK.md), [documentation index](docs/DOC_INDEX.md), [research guide](research/README.md) |

The folder refactor preserves numerical equations, comparison/reachability policy,
checkpoint schemas and historical evidence. Its detailed contracts remain in the
[numerical reference](docs/reference/NUMERICAL_SOLVER.md),
[constraint runbook](docs/MOD_REFERENCES.md) and
[replay reference](docs/reference/REPLAY.md). Start agent work at [AGENTS.md](AGENTS.md).
