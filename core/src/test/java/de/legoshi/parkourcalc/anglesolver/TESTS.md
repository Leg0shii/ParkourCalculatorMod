# Angle-solver tests: the map

A saved jump (capture) lives in a check folder, and `ProblemsTest` validates it for that check. To add
coverage you drop a capture (or a tiny sidecar) into a folder. No Java change, no capture name in any test.

```
anglesolver/
  ProblemsTest.java        every capture under resources/problems/<check>/ is validated for that check
  SolveBenchmark.java      manual timing (@Ignore); iterates problems/solve/
  harness/                 shared plumbing; no test lives here
resources/
  problems/<check>/        one folder per check; holds captures or .expect.json sidecars
  captures/                the shared capture library (one copy of each saved jump)
```

## The two checks (folder = check)

| Folder        | Validates that the capture... |
|---------------|-------------------------------|
| `solve/`      | still solves through the live engine (optionally for every Solve-For direction), within a time budget |
| `closedform/` | closed-form-solves byte-exact feasible, on objective, and fast |

## How to add a capture

1. Put `<name>.json` in the check folder (e.g. `problems/closedform/`), or drop it in `resources/captures/`
   and put a `<name>.expect.json` sidecar in the check folder.
2. Done. `ProblemsTest` discovers it and runs the folder's check. Tune with the sidecar
   (see `resources/problems/README.md`).

## Plumbing: `harness/`

| File | Role |
|------|------|
| `Fixtures` | read a capture off the classpath; turn a recorded tick into a `TickState` |
| `ProblemFixture` | load a capture + drive the engine (solve / directed); times it |
| `Expect` | parse `<name>.expect.json`; supply defaults |
| `ProblemCatalog` | discover check folders and the captures in them |
