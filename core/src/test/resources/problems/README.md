# Capture-driven checks

`ProblemsTest` runs every file under `problems/<check>/`. The **folder name is the check**; each capture's
optional `<name>.expect.json` tunes it. Drop a file in a folder and it runs, no Java change.

| Folder        | Validates that the capture... |
|---------------|-------------------------------|
| `solve/`      | still solves through the live engine, within an optional time budget |
| `closedform/` | closed-form-solves byte-exact feasible, on objective, and fast |

## Adding a capture

A capture is named by its stem (e.g. `j154`). Resolved from a co-located `<name>.json` in the check folder if
present, else from the shared `../captures/` library. The optional `<name>.expect.json` holds the constraints;
with no sidecar, defaults come from the capture's own result. A capture may appear in both folders (e.g.
`j154` is in `solve/` and `closedform/`), which is why captures live in one shared library.

### Sidecar fields

```jsonc
{
  // solve/
  "shouldSolve":     true,     // default: the capture's recorded angleSolver.result.success
  "effort":          "FAST",   // FAST | BALANCED | THOROUGH; default FAST
  "maxSolveMs":      4000,      // wall-clock budget; omit for no timing assertion
  "minMet":          12,        // require >= this many constraints met; omit to require full success
  "allDirections":   false,     // require every Solve-For (axis x goal) to solve, not just the saved one

  // closedform/
  "maxObjectiveGap": 0.01,      // max objective shortfall vs the recorded run (default 0.01)
  "maxMicros":       2000        // max us per single solve (default 2000)
}
```

`maxSolveMs` / `maxMicros` are the "must not regress timewise" guards. Keep them generous: they exist to catch
gross regressions, not CI jitter. An empty `{}` sidecar just runs the folder's check with defaults.
