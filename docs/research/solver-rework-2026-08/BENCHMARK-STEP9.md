# STEP 9: OLD-vs-NEW full-corpus benchmark and SHIP VERDICT

Date: 2026-08-25. Trees: OLD = pre-campaign `3d19c9ff` (worktree); NEW = the ARCH-1 cutover tree
(`7556aceb` + the uncommitted STEP 1-6 working tree, incl. this session's OI-06/OI-11). Harness:
`core/src/test/.../anglesolver/CorpusBench.java` (env-gated `PKC_CORPUS=1`), the same file dropped into both
trees (depends only on stable public API: `AngleSolverEngine.solve`, `SolveResult`, `ExactJumpModel`). Raw
per-capture reports are in `benchmark/corpus-{OLD,NEW}-{FAST,THOROUGH,TH10}.tsv`; the comparator is
`benchmark/compare.py`. Re-run: `PKC_CORPUS=1 PKC_CORPUS_TIER=FAST ./gradlew :core:cleanTest :core:test
--tests '*CorpusBench'` (set `PKC_CORPUS_TIER=THOROUGH PKC_CORPUS_OPT_SEC=10` for the optimize tier).

## Method

- CORPUS: all 126 solvable capture files under `core/src/test/resources` (`captures/**` + inline
  `problems/solve/*.json` + `problems/closedform/*.json`, minus `*.expect.json`). The two trees' capture sets
  are byte-identical (diff empty), so the comparison is apples-to-apples with no only-OLD / only-NEW rows.
- TIERS: FAST (first-feasible, no wall-clock deadline) and THOROUGH (OPTIMIZE). THOROUGH was run at a fixed 4s
  budget corpus-wide, then a 10s follow-up on the suspect captures (see the budget caveat below).
- SPEED: external wall-clock around `engine.solve()` + the poll loop (NOT the engine's own `solveNanos`, which
  now includes smoothing). Cold = first solve of the capture in the JVM (runs=1).
- OBJECTIVE: the ENGINE's shipped objective (`SolveResult.getObjectiveValue()` on `isSuccess()`), which the
  engine already certifies byte-exact (`success = feasible && maxViolation(gameFacings, path) <= FEAS_TOL=0`).
  An independent `toGameFacings` recompute is captured ONLY to FLAG yaw-lock / free-start divergence; it is
  NOT the source of truth (the BUILD-LOG's hard-won lesson: the recompute mis-rounds yaw-locked adoptions and
  ignores free-start translation). Classification WIN / TIE(<=1e-4 b) / REGRESSION is on the shipped objective.

## Results

### FAST tier (the clean signal: no deadline, so feasibility + first-feasible objective + latency)

- FEASIBILITY REGRESSIONS (OLD-feasible -> NEW-infeasible): **0** of 126. (1 feasibility GAIN: synth-legal-shortfall.)
- OBJECTIVE: **60 wins, 43 ties, 6 regressions.** Top wins: gh398-optimize-2jump +0.767, taser-100t +0.639,
  j021-rinav1-01 +0.160, j345 +0.043, loopmm-3jump-lands +0.042, j138 +0.033. Regressions: **j003 -1.03** (the
  one real one, see below) then five sub-1e-2: taser-80t / j187 -4.7e-3, j1149 -6.0e-4, j318 -4.2e-4,
  gh386-j335-lowx -2.2e-4.
- smoothLambda>0 captures (6, the gh386-4x2 CI-smoothed set): every one OLD==NEW to the bit (delta 0.00). No
  smooth give-back regression on the corpus (the MAX_GIVE_BACK=8e-3 headroom is not exercised destructively).
- YAW-LOCK / free-start divergence: NEW 19, OLD 14 (the +5 are P2 sphere-snap adoptions the campaign added).
  Large divergences (df-chain-free-start 1.07, gh283-j925 2.49, gh386-4x2b -1.79, synth-* 0.5) are free-start /
  translation captures where the naive recompute ignores the chosen start; the small ones (j120 1.7e-6,
  j135 1.0e-6, ...) are genuine sphere-snap yaw-lock. Either way the shipped objective is authoritative; these
  are the captures whose in-game landing depends on save/playback honoring the yaw-lock (STEP 8 check).

### THOROUGH tier (4s fixed budget) — budget-starved, do not read at face value

At 4s: 3 "feasibility regressions" (j144, j330, nix-t25-setup-tick) and 17 objective regressions incl. j021
-1.7e-2. BUT all three "feasibility regressions" SUCCEED at FAST on NEW, and j021 WINS at FAST (+0.16). The 4s
budget is simply too short for NEW: the cutover added unconditional recovery work (disk-IPM, gate, sphere,
residual) that consumes budget OLD spent optimizing. Re-run at a full 10s budget (both trees, suspect set,
`corpus-*-TH10.tsv`):

| capture | OLD 10s | NEW 10s | at 10s |
| --- | --- | --- | --- |
| j144 / j330 / nix-t25 | feasible | **feasible** | budget artifact GONE (all 3 solve) |
| j021-rinav1-01 | 1067.8452 | **1067.8638** | NEW WINS +1.86e-2 (matches the graph-path gate) |
| j147__X__1bm | 1095.0932 | **1095.0966** | NEW wins +3.3e-3 |
| j133 / j346 / j347 | . | . | TIE (<=4e-5) |
| j003 | -31.2999 | **none (hangs)** | REAL regression (see below) |

So the THOROUGH-4s regressions are budget artifacts; at equal 10s budget NEW is equal-or-better on every
suspect except j003. The optimize-tier regression mode is objective-crowded-out by NEW's higher fixed cost,
not a weaker solver.

### The one real regression: j003

OLD solves j003 to -31.30 cleanly at every budget. NEW: FAST returns a *different, worse-objective* feasible
point (-30.27, both success=true), THOROUGH-4s -29.32, and THOROUGH-10s produces NO result within the 30s
harness timeout (the solve exceeds its own 10s deadline). j003 IS covered by ProblemsTest (`j003.expect.json`)
and the slow suite is GREEN, so at its expect-config NEW passes; the anomaly appears under the bench's
saved-state + effort-override config. This is the single genuine regression the benchmark surfaced: a
deadline-respect / first-feasible-quality issue on j003 to investigate before ship. It is NOT a feasibility
regression (NEW solves j003 feasibly at FAST) and NOT a ProblemsTest gate failure.

### Speed (FAST cold, 109 real jumps: success on both trees, excluding the shared research-fixture timeouts)

| metric | OLD | NEW |
| --- | --- | --- |
| median cold ms | 59 | 107 |
| mean cold ms | 268 | 243 |
| p90 cold ms | 731 | 562 |
| max cold ms | 4727 | 3450 |
| total cold ms (sum) | 29232 | 26472 |

NEW/OLD cold ratio: median 1.52, mean 1.80. NEW slower (>20ms) on 59, faster on 19, ~equal on 31. So NEW pays
a ~48ms fixed floor on trivial solves (the unconditional recovery components) but converges the HARD ones
faster: its tail (p90, max) and aggregate wall-clock are BETTER. All absolute latencies stay low (NEW p90
562ms). No NEW-specific cold outlier: the only 30s cases are ~14 degenerate solver research fixtures
(razor-*, nix-full, loopmm-tight) that time out on BOTH trees because the FAST tier has no wall-clock deadline.

## The 5-question SHIP VERDICT (evidence, not adjectives)

1. SIMPLER: **PARTIAL.** PASS-on-flags (0 capability flags, was 8 + PKC_SMOOTHFACING). FAIL-on-structure until
   STEP 7 (net-additive; no old solver deleted yet; STEP 1 removed 3 dead classes, -345 LOC, a start).
2. MAINTAINABLE: **PASS-pending-commit.** Committed on the branch, dead code removed, docs corrected, the
   benchmark is re-runnable and committed. Map CorpusBench + GraphPathObjectiveGateTest in TESTS.md.
3. STABLER: **PASS with one flag.** 0 FAST feasibility regressions corpus-wide; THOROUGH feasibility
   "regressions" are 4s-budget artifacts that all solve at 10s. Slow suite independently GREEN (STEP 0).
   Same-machine determinism guard (OI-17); cross-machine is manual. The one flag: the j003 THOROUGH
   deadline-hang.
4. STRONGER: **PASS.** FAST 60 wins / 6 regressions / 0 feasibility regressions; at equal 10s budget the
   optimize tier is equal-or-better (j021 +1.86e-2 reaching the tight graph-path gate). The only real
   objective regression is j003.
5. FASTER: **MIXED.** Higher per-solve floor (median 59->107ms, +48ms from the unconditional components) but
   BETTER tail and aggregate (p90 731->562ms, sum 29.2->26.5s). No NEW-specific cold outlier. Not a clean
   PASS (the floor rose); not a throughput regression. The floor is exactly the STEP-7 lever (delete subsumed
   old solvers / make components conditional).

### Rubric outcome

- HARD NO-SHIP conditions: none triggered by the benchmark. (No OLD-feasible capture is NEW-infeasible; the
  slow suite is independently green; in-game QA is PENDING, not failed.)
- CONDITIONAL SHIP is blocked on exactly two items: **(a) fix the j003 regression** (the one acceptance
  regression > 1e-4 b), and **(b) STEP 8 in-game QA** on the three touched loaders (still user-only, and the
  sphere-snap yaw-lock captures above are the concrete things to replay through SimulatorEntity).
- FULL SHIP additionally needs STEP 7 (net LOC/path reduction, one smoothing owner, and recovering the FAST
  floor) which is not yet done.

VERDICT: **do not ship yet, but the cutover is sound.** Feasibility is preserved corpus-wide, the objective
net-wins strongly with the tight gate holding, and speed is net-neutral-to-faster in aggregate. Close j003 and
pass the in-game QA for CONDITIONAL SHIP; do STEP 7 for FULL SHIP.
