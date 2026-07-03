# Angle-solver tests: the map

A saved jump (capture) lives in a check folder, and `ProblemsTest` validates it for that check. To add
coverage you drop a capture (or a tiny sidecar) into a folder. No Java change, no capture name in any test.

```
anglesolver/
  ProblemsTest.java        every capture under resources/problems/<check>/ is validated for that check
  HpkDualRecoveryScreen.java  dev miss-screen over captures/hpk/ (dual bound + full chain per capture);
                              skipped unless PKC_SCREENS is set; report at build/reports/hpk-screen.txt
  RelaxDiagScreen.java     dev per-capture recovery diagnostic (stall margins, recorded-path replay);
                           skipped unless PKC_SCREENS is set; report at build/reports/relax-diag.txt
  LoopmmReachScreen.java   dev reach diagnostic on loopmm (pattern-branched B&B loose/tight, engine
                           exhaustive); skipped unless PKC_SCREENS is set
  HpkMissTriageScreen.java dev triage of the dualrecovery frontier misses (blind B&B per capture,
                           hand-pattern probes); skipped unless PKC_SCREENS is set
  HpkEngineBench.java      manual engine bench over captures/hpk/; PKC_BENCH=1 to run, PKC_BENCH_EXH,
                           PKC_BENCH_FILTER, PKC_BENCH_TAG, PKC_BENCH_TIMEOUT_MS tune it
  EngineFileScreen.java    drive the live engine on any save file headlessly; PKC_SOLVE_FILE=<path>,
                           optional PKC_SOLVE_EFFORT and PKC_SOLVE_TIMEOUT_MS
  harness/                 shared plumbing; no test lives here
resources/
  problems/<check>/        one folder per check; holds captures or .expect.json sidecars
  captures/                the shared capture library (one copy of each saved jump)
```

## The checks (folder = check)

| Folder          | Validates that the capture... |
|-----------------|-------------------------------|
| `solve/`        | still solves through the live engine (optionally for every Solve-For direction), within a time budget |
| `closedform/`   | closed-form-solves byte-exact feasible, on objective, and fast |
| `dualrecovery/` | is byte-exact-solved by the deterministic dual chain (closed form, SLP, reseeded SLP, relaxation recovery), no CMA-ES, no warm start; a sidecar `bnbSeconds` adds a bounded blind pattern-B&B feasibility fallback on chain miss; the hpk capture library (gh-204) is wired here, with `shouldSolve: false` marking the known frontier misses |

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

- `loopmm-3jump-solver-misses.json`: the failing half of the #186 reach-failure witness pair. Its landing
  half, `loopmm-3jump-lands.json`, IS wired: a `dualrecovery` sidecar with `refObjective: -279.3` asserts
  the pattern-B&B lands the pad blind (docs/research/angle-solver.md sections 10.3 and 11).
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

See `docs/research/anvil-solver-quality-decision.md`.
