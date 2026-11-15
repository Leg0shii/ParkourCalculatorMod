# Standalone structure refactor: implementation ledger

User authorized the [reviewed layout](STANDALONE_STRUCTURE_REVIEW.md), with
intermittent working commits/tests. Starting HEAD `7def2875`. Work remains in
the session checkout; preserve unrelated AGENTS.md/CODE_MAP.md edits. Do not
change solver policy, equations, evidence bytes or declared test scope.

| Stage | Status / recovery evidence |
| --- | --- |
| 1. Extract runtime services from mixed drivers | Implemented: witness replay/iteration, report IO/summary, provenance and work accounting. Live callers no longer import experiment helpers. 56 focused native-enabled tests pass (6.12s). Committed as `90135e5b` before namespace relocation. |
| 2. Installable package and domain groups | Implemented: installed `src/parkour_solver`, domain groups, explicit module worker launch and narrow public aliases. 77 focused checks pass; installed API+real worker works from `/tmp` without research/test imports. Full native gate600 passes/no skips,235.03s, source stable; [report](validation/layout-stage2/report.json). |
| 3. Separate tests, research, cases, native sources and local outputs | Implemented alongside package move: research/test/case/native roots, explicit fixture resolvers, current native-suite driver and relocation catalog. All902 protected evidence/fixture files retain original hashes. Local scratch preserved under ignored runs; native classes/config moved under build/native. |
| 4. Complete responsibility splits / test support | Implemented: schema/angle helpers, scene normalization/objectives, vertical/numerical/scene validation, physical rows, compiler accounting, application service, compatibility-only policy and developer plots have explicit owners. Shared builders and persistence harnesses now live in tests/support; current profiler/audit tools are separate from frozen evidence. 104 focused native checks, 47 package/profiler/persistence/DFS checks pass; no test→test imports. Full 610-test gate next. |
| 5. Current docs, broad native/CLI/resume/replay/cold checks | Pending: preserve recorded 600-test scope, cold native smoke, artifact hashes and installed-package behavior outside checkout cwd. |

Changes are committed after each working stage; git history is the recovery
boundary. Local safety copies of the two unrelated edited files and original
tracked-file hashes were recorded before edits. Historical result manifests,
SQLite companions, archived instrumentation and fixture bytes stay immutable.
No policy/schema bump is justified by a file move alone.

Current native environment: `.venv-guidance/bin/python` at repository root,
`PKC_IPOPT_PYTHON` pointing to that executable, compiler config currently under
`standalone/build/native/compiler.json`; BLAS/OpenMP threads1. Regenerate
native configuration deliberately when its source/output paths move.
