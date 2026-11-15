# Standalone structure refactor: implementation ledger

User authorized the [reviewed layout](STANDALONE_STRUCTURE_REVIEW.md), with
intermittent working commits/tests. Starting HEAD `7def2875`. Work remains in
the session checkout; preserve unrelated AGENTS.md/CODE_MAP.md edits. Do not
change solver policy, equations, evidence bytes or declared test scope.

| Stage | Status / recovery evidence |
| --- | --- |
| 1. Extract runtime services from mixed drivers | Implemented: witness replay/iteration, report IO/summary, provenance and work accounting. Live callers no longer import experiment helpers. 56 focused native-enabled tests pass (6.12s). Commit before namespace relocation. |
| 2. Installable package and domain groups | Pending: mechanical import/path migration, minimal public facades, explicit worker/module launch. |
| 3. Separate tests, research, cases, native sources and local outputs | Pending: immutable artifact relocation map, fixture resolvers, current native runner and tests. |
| 4. Complete responsibility splits / test support | Pending: schema/physics, scene/validation/repair, plots, test builders and source provenance boundaries. |
| 5. Current docs, broad native/CLI/resume/replay/cold checks | Pending: preserve recorded 600-test scope, cold native smoke, artifact hashes and installed-package behavior outside checkout cwd. |

Changes are committed after each working stage; git history is the recovery
boundary. Local safety copies of the two unrelated edited files and original
tracked-file hashes were recorded before edits. Historical result manifests,
SQLite companions, archived instrumentation and fixture bytes stay immutable.
No policy/schema bump is justified by a file move alone.

Current native environment: `.venv-guidance/bin/python` at repository root,
`PKC_IPOPT_PYTHON` pointing to that executable, compiler config currently under
`standalone/autonomy/build/compiler.json`; BLAS/OpenMP threads1. Regenerate
native configuration deliberately when its source/output paths move.
