# Standalone layout refactor acceptance

Runtime source is commit `9c79e741` (folder/refactoring feature), preserving solver
behavior from `dd738c98`. Subsequent documentation/tooling changes do not change
that runtime. Source hashes in these records identify all131 installed Python
modules. See [implementation ledger](../../LAYOUT.md)
and [runbook](../../RUNBOOK.md) for recovery commits and reproduction.

| Check | Evidence / result |
| --- | --- |
| Full selected native gate | [report](report.json), [test log](tests.log):610 tests,0 failures/errors/skips;236.32s total, source unchanged. Exact previous600-test scope plus10 package/profiler checks. Dedicated ladder/climbable/vine/movement-entry suites remain outside this declared gate. |
| Protected files / boundary check | [layout integrity](layout.json): all1,180 relocation targets present,902 original evidence/fixture SHA256s preserved; no production imports from research/tools/tests/old checkout namespace. Four Java sources also retain original bytes. |
| Cold finite Real11 initial0 | [summary](real11-summary.json), [provenance](real11-provenance.json), [invocations](real11-requests.json):T64, final Z−1093.2381026602416; first full witness8.38s,90.74s search,26 numerical invocations/22 workers,22 retained-witness replays passed. |
| Extracted cold control | [control](real11-control.json.gz): cold best witness, effective scene, guide/motion inputs and full independent replay results. This is a compact witness/bound control, **not** a resumable search checkpoint. |
| Bound controls | [archived controls](witness-bound-controls.json):131 prefixes from old T67/T64 completions pass. [Cold control](cold-witness-bounds.json):64/64 prefixes pass. No NLP calls or discovery seeds. |
| Distributable wheel | [wheel check](wheel.json):131 production modules, no research/tools/tests; API imports directly from wheel in isolated Python outside checkout cwd. |
| Native helper rebuild | [Gradle log](native-build.log): moved compiler/export adapters build successfully; [fresh bridge replay](fresh-bridge-replay.json) passes exact scalar/support/scene replay for the cold T64 witness. |
| Installed/compatibility boundaries | The610-test gate includes isolated API, real worker from empty cwd, source-manifest and dependency checks, plus profiler restoration. All131 production modules import;5 supported old CLI aliases pass help. |
| Maintained docs | [link check](links.json): maintained local links resolve. Frozen bundle Markdown is intentionally excluded and retains recorded source-era paths. |

The cold arm uses the original archived initial0 fixture, with only the requested
90-second profiling budget. It does not seed saved programs/facings. The full
local run remains at `standalone/runs/layout-final-real11-initial0` (ignored);
its manifest and SQLite companion must stay together. The compact committed
control preserves the final witness and acceptance records without adding the
entire mutable DFS checkpoint to Git.

The budget ended with termination `deadline`. This is a refactor integration
control, not an optimality, global infeasibility, speedup or actual-world replay
claim. The declared scene still has finite extended B7 and stored B6. Compiler
configuration is machine-specific; its hash is in cold provenance and a fresh
post-build bridge replay checks the regenerated descriptor.
