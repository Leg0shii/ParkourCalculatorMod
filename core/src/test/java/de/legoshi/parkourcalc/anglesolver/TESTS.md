# Angle-solver tests: the map

A saved jump (capture) lives in a check folder, and `ProblemsTest` validates it for that check. To add
coverage you drop a capture (or a tiny sidecar) into a folder. No Java change, no capture name in any test.

`ProblemsTest` and the other engine-driving suites are tagged `@Category(SlowSolverTests.class)` and
excluded from the default `:core:test` run; pass `-PslowTests` to run them (CI always does). See
AGENTS.md "Tests" for the full slow list and when a full run is required.

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
  RunMatrixScreen.java     (preset x problem) run matrix over problems/solve + problems/closedform,
                           cold starts, one SolveRunRecord JSONL line per run to
                           build/reports/matrix-<tag>/runs.jsonl, resumable (recorded pairs skipped);
                           PKC_MATRIX=1 to run, PKC_MATRIX_TAG, PKC_MATRIX_TIMEOUT_MS (default 120000,
                           cap = censored CANCELLED record), PKC_MATRIX_LIMIT (problems per category),
                           PKC_MATRIX_FILTER, PKC_MATRIX_PRESETS, PKC_MATRIX_BAND (path to a band.txt
                           allow-list of category/name keys); taser60-l* presets = optimize60 shape
                           with a nonzero smoothLambda (the TASer band lives at
                           docs/research/data/matrix-taser-pin1/band.txt); PKC_MATRIX_SWEEP (A18)
                           replaces the preset list with generated ones, `|`-separated entries of
                           base:key=v1,v2;key2=... cross-producted per entry: taser<sec> takes l
                           (engine path via setSmoothLambda), alm<sec> runs AlmSnapStage directly
                           (keys l, seeds, topk, cooking, gate; free startBox becomes the translation
                           domain) recording raw objective + smoothness stats per run, and a bare
                           entry with no params reuses the static preset of that exact id
                           (parse coverage: RunMatrixSweepTest)
  MatrixAnalysisScreen.java  per-preset aggregates + SBS/VBS feasibility and objective-regret gap over
                           a matrix runs.jsonl; PKC_MATRIX_ANALYZE=1 + PKC_MATRIX_TAG; writes
                           build/reports/matrix-<tag>/analysis.md
  FreeStartTranslationTest.java  translation-aware free-start scoring: zero-width translated score
                           byte-equals the pinned score (razor-proof, j004, j318); the
                           free-translate-edge synthetic solves and adopts its known +X box-edge
                           optimal translation end to end (terminal adoption step)
  WrapWindowIlsTest.java   wrap-window lattice ILS stage (|gf| <= 360 hard cap): candidate sets are
                           distinct/capped/deterministic; bounded span-16 descent from the rung snap
                           point (resources/points/) reaches translated viol <= 3.5e-5 at a fixed
                           eval cap; wrap-class results survive locked-rows JSON round trip
                           byte-exact; PKC_WRAPILS_FULL=1 + PKC_WRAPILS_TAG runs the env-gated full
                           replication with kicks (1.5e-5 bar on rung)
  LegalModeTest.java       legal/record objective mode: goal-wall selection (X@49lo on the proof
                           spec; velocity/EQ/cap/off-tick/off-axis never selected; ties refuse);
                           deterministic legal solve on the synth-legal-shortfall fixture with the
                           reported shortfall and every hard wall met
  RazorLegalReplayTest.java  byte-exact replay pins for the three delivered rung legal attempts
                           (legal / wrap720 / turn360): locked RAW rows realized without wrapping,
                           hard walls feasible under the rung patch, shortfall within 1e-9 of the
                           recorded value (model-drift tripwire)
  RazorColdT1.java         THE HEADLINE GATE: cold 5.4375 from t1 (razor-proof-t1 capture, free
                           start, no yaw seeds) through the live engine; PKC_RAZORT1=1 +
                           PKC_RAZORT1_TAG to run; PASS = success + fresh-reparse viol <= 0;
                           report at build/reports/razort1-<tag>.txt
                           (solve/loopmm-tight-t39: the loopmm misses capture with its shipped-
                           disabled pad wall ENABLED; pins the near-miss B&B rescue landing the
                           tight spec at Z@71 >= -279.3 through the live engine, THOROUGH 45 s)
                           (solve/loopmm-tight-t39-fast: the SAME capture under FAST effort; pins
                           the staged late-race: primary fast starves, the explore arm spawns at
                           the 20 s checkpoint and lands it, budget 90 s; the redirect-class gate)
                           (solve/gh313-j121-dfneo: gh-313; hpk j121 neo with dF <= 0 walls, a
                           facing-wall spec every deterministic recovery bails on; pins the
                           near-miss seeded feasibility rescue in SolveCore under FAST effort)
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
