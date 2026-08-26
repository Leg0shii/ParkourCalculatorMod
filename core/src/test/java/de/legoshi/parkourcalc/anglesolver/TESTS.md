# Angle-solver tests: the map

A saved jump (capture) lives in a check folder, and `ProblemsTest` validates it for that check. To add
coverage you drop a capture (or a tiny sidecar) into a folder. No Java change, no capture name in any test.

`ProblemsTest` and the other engine-driving suites are tagged `@Category(SlowSolverTests.class)` and
excluded from the default `:core:test` run; pass `-PslowTests` to run them (CI always does). See
AGENTS.md "Tests" for the full slow list and when a full run is required.

```
anglesolver/
  ProblemsTest.java        every capture under resources/problems/<check>/ is validated for that check
  OptimizeVsFastTest.java  gh-398 invariant on captures/gh398-optimize-2jump: Optimize's answer is never
                           worse than Fast's, and the run publishes at least two incumbents so the live
                           panel moves and Cancel keeps the best found so far
  GraphPathObjectiveGateTest.java  objective gates that solve THROUGH the full Optimize graph (not dualChain),
                           asserting the ENGINE's shipped objective (getObjectiveValue), which is computed with the
                           post-solve scenario and so honors yaw-lock. j021-rinav1-01 must reach ResidualRescue's
                           optimum (engineObj >= 1067.8637, div 0 = not yaw-locked, so the recompute is valid); goes
                           RED if ResidualRescue is unwired from DualChainNode. loopmm-3jump-lands LANDS per the
                           engine (engineObj -279.299868 >= -279.3 block edge). NOTE: sphereSnap adopts loopmm as a
                           fully YAW-LOCKED stage, so the harness recompute (toGameFacings via the non-yaw-locked
                           lastSpecDebug scenario) re-rounds to -279.300084 (div 2.16e-4); the test bounds that
                           divergence and flags that landing is yaw-lock-dependent - VERIFY IN-GAME. Also a
                           determinism guard (j021 solved twice, engineObj+recompute bit-identical)
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
  CorpusBench.java         manual OLD-vs-NEW full-corpus bench (STEP 9): all captures at FAST/THOROUGH,
                           external wall-clock + engine shipped objective + independent recompute (yaw-lock
                           flag). PKC_CORPUS=1 to run; PKC_CORPUS_TIER/RUNS/OPT_SEC/FILTER/TAG/TIMEOUT_MS
                           tune it. Drop into a git worktree at the OLD commit to bench both trees; compare
                           with docs/research/solver-rework-2026-08/benchmark/compare.py
  FreeStartSweepBench.java manual free-start sweep over captures/hpk/ (synthesized tick-0 box, base +
                           (+2.3,+1.7)-shifted seed variants, FAST, per-node timing from the run
                           record); -Dpkc.sweep=1 to run, -Dpkc.sweep.{tag,variants,filter,timeoutMs,
                           trace} tune it (or PKC_SWEEP-style env); report at build/reports/sweep-<tag>.txt.
                           A frac~fx~fz variant places the seed at those box fractions (in-box
                           seed-parity screening for the gh-386 class; '~' not ':', a colon breaks
                           the trace filename on Windows). Run via direct java -cp <test classpath>
                           (PKC_* env does not reach a warm gradle daemon's test JVM)
  SingleProblemProbe.java  run ONE ProblemsTest capture headlessly: -Dpkc.probe=<category>/<name>
                           (e.g. solve/gh386-4x2-seedshift) via JUnitCore; prints success/met/ms/obj
  EngineFileScreen.java    drive the live engine on any save file headlessly; PKC_SOLVE_FILE=<path>,
                           optional PKC_SOLVE_EFFORT and PKC_SOLVE_TIMEOUT_MS
  StructureDump.java       export a capture's compiled linear structure (walls, objective vectors,
                           per-tick mMag/baseArg/friction, start box, warm replay) as json for the
                           research/copt harness; PKC_STRUCT_FILE=<capture>, PKC_STRUCT_OUT=<json>,
                           optional PKC_STRUCT_ZERO=<pattern json> folds an inertia-gate zeroing
                           pattern (pattern-aware JumpLinearModel + velocityWalls); run --no-daemon
  NoTurnReplay.java        byte-exact replay of a no-turn decode (keys + yaws + sprint + start) from
                           research/copt through ExactJumpModel against a capture's compiled spec;
                           PKC_NOTURN_CAPTURE, PKC_NOTURN_FILE, PKC_NOTURN_OUT; reports objective,
                           per-constraint violations, positions and carry velocities (gate patterns)
  SolveNodeStatsScreen.java  per-node timing dump over problems/solve at expect efforts;
                           -Dpkc.nodestats=1 to run, -Dpkc.nodestats.{tag,timeoutMs} tune it;
                           TSV at build/reports/nodestats-<tag>.tsv (RUN + NODE rows); run via
                           direct java -cp like FreeStartSweepBench
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
                           (engine path via setSmoothLambda), and a bare entry with no params
                           reuses the static preset of that exact id
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
                           disabled pad wall ENABLED; the tight Z@71 >= -279.3 pad is reachable
                           only while the Z inertia gate is held open across the momentum
                           reversal, so this pins the pattern B&B's keep-alive branch,
                           THOROUGH 45 s)
                           (solve/loopmm-tight-t39-fast: the SAME capture under FAST effort,
                           same keep-alive pin on the shorter chain, budget 90 s)
                           (solve/gh313-j121-dfneo: gh-313; hpk j121 neo with dF <= 0 walls. The
                           dF inequality class is retired by ruling (issue 372, dF = 0 only) and
                           CMA-ES is removed (issue 374), so shouldSolve is false by design)
                           (solve/nix-full-t1: long multi-jump free start; accepted as not solving
                           after the CMA-ES removal (issue 374) until the multi-jump seam work lands)
                           (solve/gh283-j925-farseed: gh-283; hpk j925 momentum+neo with the start
                           dragged ~2 blocks outside its tick-0 footprint box, cold rows; pins the
                           seed-position-independent free-start solve, FAST 20 s: bestTranslate's
                           conflict fallback must place conflicted candidates at the min-violation
                           translation, never at the seed)
                           (solve/gh283-j990-cold: gh-283; hpk j990 dF=0 momentum chain with the
                           seed ~2 blocks outside the start box, cold rows; pins the exact
                           prefix-arc theta enumeration, the recoverStart-scored ladder fractions,
                           and the theta micro-polish certify in the joint free-start dual,
                           FAST 20 s)
                           (solve/inertia-1tick-neo: 1.8.9 neo whose landing is only feasible via
                           a single-tick X inertia zeroing (vX dips under the 0.005 threshold
                           mid-flight and re-accelerates after); the free relaxation is truly
                           infeasible, so this pins the B&B rescue's single-tick zx1@k patterns
                           and its dF=0 entry through the FacingPrefold-gated facing-wall check,
                           FAST 20 s)
                           (solve/inertia-1tick-neo-t31: the SAME capture with the window started
                           ON the zeroing tick (startTick 30), so the seed vX 0.00498 is below the
                           threshold and dead on arrival; pins the seed-velocity normalization
                           (ExactJumpModel.zeroSubThresholdVelocity at buildPhys), without which
                           every relaxation carries the phantom carry and no k>=1 pattern can
                           represent a tick-0 zeroing, FAST 20 s)
  LevelSetAscentTest.java  level-set objective ascent (gh-290): on keep-out-wall captures where the
                           chosen Solve For degenerates the dual recovery (j003 X/MIN, j012 Z/MAX,
                           j008-bfneo Z/MIN, taser-80t X/MIN), the goal-wall bisection strictly beats
                           the plain SLP hug the reseeded path produces and reaches near the dual
                           bound (j003 X/MIN: -27 hug -> -31.3 optimum). The dF gate (facing wall =>
                           skip + info notice) is unit-tested fast in
                           core/anglesolver/DfDirectionGateTest
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
