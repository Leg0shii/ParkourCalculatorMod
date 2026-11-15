# Standalone structure audit

Inventory/proposal only, source HEAD `7def2875`. No production files were moved,
no numeric solver was run, and no historical evidence was rewritten. The two
pre-existing modified paths were `standalone/AGENTS.md` and `standalone/docs/CODE_MAP.md`.

Read the [structure review](../../../docs/STANDALONE_STRUCTURE_REVIEW.md) for the
proposed tree, mixed-module extractions, migration sequence and constraints.

The `snapshot-7def2875-final/` bundle has an exhaustive `files.csv` (2,809 physical
files, including1,199 tracked files), `modules.csv` (275 tracked Python files),
`imports.csv`, `directories.csv` and `summary.json`. All118 modules currently in
library directories have reviewed roles/destinations:96 runtime-bearing/mixed
modules and22 research/diagnostic modules. This is not96 pure runtime-only files.

Counts exclude this audit directory and the review document to avoid counting
outputs recursively. Inventory bytes are logical file sizes. File contents were
not changed to inspect them, and large result payloads were not decompressed.
`files.csv` distinguishes tracked, ignored, local cache/run and native build data.
Destination columns are suggestions; semicolon-separated Python destinations
identify required extraction rather than a simple file move. Documentation
placements need authority/status review before archival.

Static AST imports include all functions/conditional branches and do not prove
execution on a given solve. Package initializer reachability is reported separately.
The required subprocess worker is explicitly marked because imports alone miss
it. Unsupported static edges/dynamic strings are not a deletion test. A zero
importer count does not mean an executable CLI or worker is unused.

Reproduce from `standalone/` with a fresh output directory:

```sh
python3 autonomy/experiments/structure-audit/inventory.py --output /tmp/standalone-layout-inventory-new
```

This uses standard-library Python, Git for tracking/ignore status, file stat and
AST parsing. It invokes no worker, compiler, tests, remote service or build.
The tracked/untracked totals reflect the source checkout at capture time; future
files/commits naturally change them. The script never applies proposed moves.
