# Research tools

Probes, dumps, screens and benches that drive the solver or the renderer on demand. They are JUnit
classes so they can reuse the test harness (`anglesolver/harness/`, the captures under
`src/test/resources/`), but they are not tests: almost all of them skip unless an env variable or
`-Dpkc.*` property selects them, and none of them gate anything. They live in their own source set so
`:core:test` never compiles, schedules or shards them.

The `research` source set sees the main and test classes and resources. `:core:check` (and so every
build) compiles it, so a refactor that breaks a tool fails the build.

```bash
./gradlew :core:research --tests '*EngineFileScreen' -x tableStyleCheck   # PKC_* env on the daemon, -Dpkc.* passed through
./gradlew :core:printResearchClasspath -q                                 # for a direct java -cp run
java -cp "<research classpath>" org.junit.runner.JUnitCore de.legoshi.parkourcalc.anglesolver.FoldDriverProbe
```

PKC_* env variables reach the test JVM only from the environment of the Gradle daemon that runs it,
so either start a fresh one (`--no-daemon`) or use the direct `java -cp` form. `-PtestCpus` and
`-PpkcJfr` work as on `:core:test`.

```
anglesolver/
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
                           the trace filename on Windows). Run via direct java -cp <research classpath>
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
  RazorColdT1.java         THE HEADLINE GATE: cold 5.4375 from t1 (razor-proof-t1 capture, free
                           start, no yaw seeds) through the live engine; PKC_RAZORT1=1 +
                           PKC_RAZORT1_TAG to run; PASS = success + fresh-reparse viol <= 0;
                           report at build/reports/razort1-<tag>.txt
  CertBnbProbe.java        env-gated certified-B&B probe: PKC_CERTBNB_CAPTURES (comma list),
                           PKC_CERTBNB_OUT/NODES/MS/MODE; nodeCap=1 dumps root bounds for the COPT
                           root-bound validation (research/copt/certbnb_rootcheck.py)
  FoldDriverProbe.java     run the fold driver headlessly on any capture; PKC_FOLD_CAPTURE=<path or
                           pool name>, optional PKC_FOLD_OUT/PKC_FOLD_DEBUG/PKC_FOLD_SEED/
                           PKC_FOLD_REF (box-reference multi-start override); prints per-round
                           bound/byteObj/maxViol/pattern events; run via direct java -cp
  HomotopyProbe.java       ladder/margins/SLP/walk measurement probe; PKC_HOM_CAPTURE plus
                           PKC_HOM_WARM (witness replay), PKC_HOM_MARGINS, PKC_HOM_POLISH +
                           PKC_HOM_PERTURB (perturbed-anchor polish), PKC_HOM_POLISH2 (full
                           ladder polish), PKC_HOM_SEEDPOLISH (engine FAST incumbent ->
                           polishFromAnchor), PKC_HOM_ENGINE/DRIVER/LADDER/WALLS/DEBUG; run via
                           direct java -cp; run :core:processTestResources after fixture edits
  KernelDiagProbe.java     DiskSocpKernel failure diagnosis (base/folded/chord solves, jitter and
                           certificate paths); env-gated, run via direct java -cp
  NoTurnColdBench.java     no-turn stratfinder timing harness (skipped unless -Dpkc.bench.capture is
                           set): certifies the recorded keys as a no-turn structure (driver=human), a
                           given schedule (driver=keys), or runs the pool / in-game / beam / benders
                           drivers with per-certify timings; pkc.bench.{driver,graph,freeBox,startTick,
                           allowJa,playable,threads,searchSec,nearSearchMs,certifySec,totalSec,maxCertify,
                           extraCertify,extraSec,dumpPool,out}; keys/engage/repeat/cascade for
                           driver=keys, dfMarks to add dF=0 marks, contCap/contLead/contSec/parCont/
                           deepPairRepair for driver=benders; pair with -Dpkc.graphTrace=true (per-node
                           ms plus one [pool]/[whd]/[bm] line per certify), -Dpkc.solver.trace=<tag>,
                           -PpkcJfr=<file.jfr>; a ';' list of captures runs them in one JVM
  NoTurnFastCheckBench.java  FastCheck heuristic benchmark (skipped unless -Dpkc.fc.cases=<jsonl> is
                           set): runs one FastCheck implementation (-Dpkc.fc.impl=<FQCN>, default
                           SearchGraphCheck) over a case list, re-verifies every FEASIBLE witness
                           byte-exactly and reports HIT/MISS/FALSE_REJECT/BOGUS/REJECT/UNDECIDED with
                           wall and thread CPU ms; -Dpkc.fc.{budgetMs,only,repeat,parallel,out};
                           parallel=N runs the cases on N threads with one impl instance each (the
                           oversubscription gate for the thread-CPU budgets); -Dpkc.fc.audit=<FQCN>
                           runs a second FastCheck on every case (budget -Dpkc.fc.auditBudgetMs, 100)
                           and counts a CONTRADICTION whenever it says INFEASIBLE while the main impl
                           returned a byte-verified FEASIBLE witness (the real-pool soundness audit of
                           a rejection check: feed it pool dumps plus the in-game found lines)
  RunUpSweepProbe.java     free-start run-up sweep on one save; PKC_SOLVE_FILE=<save.json> plus
                           PKC_GRAPH_FILE=<graph.json> (docs/research/freestart-runup-sweep-2026-09-05.md)
  HpkStartBenchmark.java   cold-solve benchmark over the hpk_human corpus against reference
                           objectives; -Dpkc.bench.{only,skip,graph,effort,optsec,timeoutms,out},
                           -Dpkc.bench.gate fails the run when a jump misses its reference by more
                           than -Dpkc.bench.tol (default 1e-5); not env-gated, it runs whenever selected
  SmoothFileScreen.java    smoothing pass on a saved solve; PKC_SOLVE_FILE=<save.json>
  CaptureDriftScreen.java  solves a save, then steps its recorded debug states through ExactJumpModel
                           and prints every tick whose position or velocity drifts; PKC_DRIFT_FILE=<save.json>
  CoefDump.java            prints the per-tick friction, mMag and baseArg of a save's linear model;
                           PKC_CD_FILE=<save.json>
                           (nix campaign, docs/research/nix-solver-handoff.md)
  ReachGapScreen.java      single-takeoff reach gap per capture; PKC_SCREENS=1
  CellSetDump.java         razor rung 5.375 gate-flip cell-set dumps and verify; PKC_CELLSET_DUMP=1,
                           PKC_CELLSET_VERIFY=1 and siblings (docs/research/next-session-lever2-2026-07-10.md)
  Roc2CellSetDump.java     the same cell-set dump for roc2; PKC_ROC2_CELLSET=1
                           (docs/research/roc2-reach-handoff-2026-08-18.md)
  MiqcpDump.java           MIQCP export of the razor proof for the external solver; PKC_MIQCP_DUMP=1
  NormCellProbe.java       razor norm-cell probe; PKC_NCP=1
  StructureVariantDump.java  razor structure variant dump; PKC_SVAR=1 + PKC_SVAR_SPEC
  render/GizmoDragConstraintBench.java  gizmo drag constraint timing; PKC_BENCH=1
  render/PathRebuildBenchmark.java      path rebuild timing; PKC_BENCH=1
```
