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

## Library-only captures (no check yet)

Some captures in `resources/captures/` are committed as data for upcoming work and are not yet wired to a
check (no sidecar, so `ProblemsTest` does not run them):

- `loopmm-3jump-lands.json` / `loopmm-3jump-solver-misses.json`: the optimizer reach-failure witness pair
  for #186 (a hand route that lands at Z@71 = -279.29973 vs the solver's -279.30585, 0.0058 short).
- `deserthard-sine262.json` / `deserthard-planrealization.json`: two recorded runs of the same 26.2
  desert-hard solve; near-duplicates in content, but each pins what the other cannot.
  `deserthard-sine262` contains the one jump (t=243, yaw 93.587) where the pre-26 and 26.x sine
  chains pick different buckets, so `CaptureReplayRegressionTest` goes red if the Mth.sin/cos
  rewrite port regresses (`loopmm-3jump-lands` is the legacy-chain control; the planrealization
  run replays byte-exact under BOTH chains and cannot catch that). Its tail rows predate its
  stored solve, so it cannot validate plan realization. `deserthard-planrealization` has rows in
  sync with its result, so `PlanRealizationRegressionTest` rebuilds the plan path (spec +
  toGameFacings) and requires the recorded resim to sit on it byte-exact; that pinned the 26.x
  square-movement input rewrite. `ApplyYawRowsTest` feeds on the sine262 run's yaw sequence.

See `docs/research/anvil-solver-quality-decision.md`. When #186 / #178 land, give these a `solve` sidecar
with a `refObjective` to turn them into real regression checks.
