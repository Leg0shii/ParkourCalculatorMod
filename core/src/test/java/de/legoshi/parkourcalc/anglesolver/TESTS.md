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
  HpkMetricScreen.java     difficulty-metric lab over captures/hpk_human/ (one folder per HPK d-level,
                           3 human-reconstructed strats each): measures yaw tolerance per capture
                           (per-tick windows, uniform jitter radius, both around a window-centered
                           anchor with the F/dF reconstruction constraints filtered out of the
                           oracle) plus input-edge tick-shift windows (per key-change event, how many
                           ticks earlier/later it can happen and still land) plus the
                           smoothest-feasible-line kinematics (coordinate descent on the sum of
                           squared second yaw differences, dual-seeded from the centered anchor and
                           the recorded line, every move feasibility-clamped; residual jerk, velocity
                           sd, max turn speed and reversal count of that line measure the intrinsic
                           camera-motion demand independent of how jagged the recorded solver line
                           is), then scores every metric
                           in metriclab/Metrics and prints per-d-level medians + Spearman vs d;
                           PKC_METRIC=1 to run; report at core/build/hpk-metric/report.txt, raw data
                           at measurements.csv, yaw-windows.csv (incl. the smoothed line per tick)
                           and input-shift-windows.csv for
                           offline metric tuning; captures with recording problems (result
                           success=false, missing result.yaws, enabled constraints outside the solve
                           segment) are listed as SKIPPED without failing the run
  HpkPair45Screen.java     paired 45-variant generation over captures/hpk_human/: per capture, strips
                           the F/dF reconstruction pins, switches inputs to Force 45, cold-solves the
                           variant with the live engine, measures it with MeasurementEngine and prints
                           human-vs-45 combinedV4 pairs; PKC_PAIR45=1 to run; report at
                           core/build/hpk-metric/pairs45-report.txt, data at pairs45.csv, solved
                           variant saves at pairs45/<name>.json (the simplify loop's starting points);
                           45-unsolvable jumps are reported, not failures
  HpkSimplifyScreen.java   greedy strat simplification over captures/hpk_human/ (issue #237 pipeline
                           stages 2-3): starts from the pairs45 variant (regenerates it if absent,
                           falls back to the human save where 45 is unsolvable), applies operators
                           (keepify, jump-hold conversion, edge recentering into measured shift
                           windows, WASD tap deletion, dF=0 no-turn chains), accepts a step only if
                           the candidate stays feasible (yaw replay against the full constraint set,
                           or a cold FAST re-solve for constraint-adding operators) AND combinedV4
                           drops; key-editing operators require KEEP inputs (never Force-45), SPRINT
                           keys are untouchable, jump-hold needs >= 10-tick fire spacing (jumpTicks
                           cooldown), keepify rewrites the debug movement samples from the rows
                           (stateful sprint derive) instead of stripping them; candidates are
                           measured under the baseline capture name so jitter seeds match; final
                           strat must cold re-solve; benchmarked against the human variant's
                           combinedV4; final saves are self-contained handover artifacts (solved
                           yaws baked into yaw-locked rows after the cold verify, Force-45 ticks
                           realized into W/A/SPRINT keys with S/SNEAK cleared) so a loaded save
                           runs its solved line in the real sim; the in-game SimVerifyBatch gate
                           (Forge 1.8.9, key V, PKC_SIMVERIFY dir) is the final word per artifact
                           (2026-07-28 review verdict); PKC_SIMPLIFY=1 to
                           run, PKC_SIMPLIFY_ONLY=<substr> and PKC_SIMPLIFY_D=<levels> filter;
                           outputs simplify-report.txt, simplify.csv, simplified saves under
                           simplified/<name>.json
  HpkTemplateScreen.java   named-strat template benchmark over captures/hpk_human/ (the stratfinder
                           spike): per capture, StratTemplates generates the human strat vocabulary
                           as parameterized instances (run(d)+jam, pessi(k), fmm(k), Mark(side,k),
                           bwmm prefix arcs with jam/fmm/pessi follow-ups; each in a no-turn dF=0
                           variant and a free-yaw variant), realizes each as a KEEP-mode save
                           (template rows, derived debug samples, fresh slip schedule, runway
                           X/Z range constraints on every generated ground tick, free start over
                           the runway, human jump-phase rows and constraints time-translated to
                           the template's fire tick), cold-solves each at 250 ms and scores
                           feasible ones with combinedV4 against the human baseline;
                           PKC_TEMPLATE=1 to run, PKC_TEMPLATE_ONLY / PKC_TEMPLATE_D /
                           PKC_TEMPLATE_MS tune it; outputs template-report.txt, template.csv and
                           template-instances.csv (per-instance success, elapsed ms, solver chain)
  HpkReachScreen.java      soundness and prune-rate check for the ReachBound necessary-condition
                           pre-screen: screens every template instance per capture, cross-checks
                           pruned instances against recorded solver outcomes from the
                           template-instances CSVs (any pruned-but-solved instance is a soundness
                           violation), and reports prune rate on known-failed instances plus
                           per-instance screen time; PKC_REACH=1 to run; outputs reach-report.txt;
                           background: docs/research/stratfinder-levers-2026-08.md
  HpkLadderScreen.java     screening-ladder benchmark over template instances: ReachBound prune,
                           rung-1 solve at PKC_LADDER_R1_MS, met/total-ranked promotion
                           (PKC_LADDER_PROMOTE=all|third|none) to PKC_LADDER_TOP_MS, recall and
                           wall-time compared against the flat template-instances-timing250ms.csv
                           baseline; PKC_LADDER=1 to run, PKC_LADDER_ONLY / PKC_LADDER_D filter,
                           PKC_TEMPLATE_WIDE=1 widens the plan grid; outputs ladder-report.txt
  HpkRelaxExportScreen.java per-instance disk-relaxation export (RelaxExport): tick schedule
                           (accel magnitude, friction, jump boost) plus constraint gates as JSONL
                           under build/hpk-metric/relax/, consumed by an out-of-tree LP solver for
                           sound infeasibility certificates; PKC_RELAX_EXPORT=1 to run,
                           PKC_RELAX_ONLY / PKC_RELAX_D filter
  HpkSubstScreen.java      strat-substitution screen (StratSubstitutions): in-place key-timing
                           edits over the recorded rows (post-jump onsets and releases shifted one
                           tick, W/SPRINT/A/D/S), constraints and tick structure untouched, debug
                           inputs re-derived via SimplifyLoop.deriveDebugSamples, free start within
                           the save's own t0 constraint; the unmodified "self" variant is a canary
                           (its failure means the capture or budget is broken, printed as CANARY
                           FAIL); PKC_SUBST=1 to run, PKC_SUBST_ONLY / PKC_SUBST_D / PKC_SUBST_MS
                           (default 2000); outputs subst-report.txt, subst.csv
  HpkBakeScreen.java       bakes V-gate handover saves: re-solves the post-fix at-or-below-human
                           winners (from template-postfix250ms.csv, delta <= 0) and the assault's
                           tail-crack labels, scores with combinedV4, picks the best per capture,
                           bakes yaws into rows (SimplifyLoop.bakeYawRows) and writes loadable
                           saves under build/hpk-metric/templates/ plus bake-report.txt;
                           PKC_BAKE=1 and PKC_TEMPLATE_WIDE=1 to run
  metriclab/               measurement vs scoring split for the jump difficulty metric (issue #237):
                           MeasurementEngine (perturb-and-resim tolerance measurement, expensive,
                           trusted; shift probes mutate a JSON-round-trip copy of the rows and
                           rebuild the spec through the stock buildSpec path; SPRINT timing is
                           probed via the recorded sprint flag chronology, never by key mutation,
                           with engagement gated on grounded ticks; held-JUMP extensions onto
                           grounded ticks respect the 10-tick jumpTicks cooldown; each shift side
                           carries a free flag: never-failed = unconstrained, and a pure release's
                           full tap-deletion failure is the press's constraint, not the release's),
                           ScoringMetric + Metrics (cheap formulas over JumpMeasurements, tweak
                           freely; combinedV3 zeroes the demand of any edge with a free side;
                           combinedV4 = v3 + 0.15 * log1p(smoothJerkDeg), pricing the intrinsic
                           camera-kinematics demand of the smoothest feasible line; the shared
                           tolerance core floors jitter/winGeo at SIG_ANGLE_DEG = 360/65536,
                           the significant-angle bucket, so sub-bucket precision is never priced),
                           HpkHumanSet (loads captures/hpk_human with optional <name>.meta.json
                           sidecars: subTier, jumpClass, rung, notes), Variant45 (strip pins +
                           Force 45 + attach a fresh solve result), HeadlessSolve (cold engine
                           drive) and SimplifyLoop (the greedy operator loop) for the 45-pair and
                           simplification work, MeasurementInvariantsTest (always-on:
                           baseline replays feasible, zero-shift probe feasible, yaw windows within
                           [0,180], shift windows within [0,5], shift edge counts match the
                           input-edge counts, jitter bounded by the narrowest one-tick window,
                           measurement deterministic incl. free flags and smoothness stats, j001's
                           sprint release reads free on both sides, j012's delayed sprint start
                           reads (0,0) frame-exact, j001's smoothest line is near-flat and
                           reversal-free, j014's flat recorded line keeps a flat smoothest line)
  harness/                 shared plumbing; no test lives here
resources/
  problems/<check>/        one folder per check; holds captures or .expect.json sidecars
  captures/                the shared capture library (one copy of each saved jump)
  captures/hpk_human/      the difficulty-metric calibration set: d<level>/ folders of solved strat
                           captures (KEEP inputs, recorded human strat + solver yaws); d-level is
                           read from the folder name
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
