# HANDOFF: finish the ARCH-1 solver (cleanup + j003 fix + low-risk consolidation)

Paste this whole file into a fresh session. It is self-contained; read the pointer files it names before editing.

## Mission

Finish the current (ARCH-1) angle solver so it is ready for the in-game QA + benchmark re-run. Three tasks, in
order: (1) delete genuinely-dead code, (2) fix the one real benchmark regression (j003), (3) do the low-risk
STEP-7 consolidation (dedup only, NO solver deletion). Do NOT start the ARCH-2 global-solver rewrite; it is
parked in GitHub issue #422 with its design in `docs/research/global-solver-2026-08/DESIGN.md`.

## Where things stand (verified 2026-08-25; confirm by code, do not re-derive)

- Branch `feature/solver-rework-arch1-cutover`, base = pre-campaign `3d19c9ff`. The whole campaign is
  UNCOMMITTED on the working tree (the user handles all git). Read the campaign state in
  `docs/research/solver-rework-2026-08/BUILD-LOG.md` (newest entry first) and the plan in
  `NEXT-SESSION-PROMPT.md` (same folder). Read `AGENTS.md` first for repo rules + the solver code map.
- The cutover is DONE: ZERO capability flags on the solver path; the 4 ARCH-1 recovery components
  (ResidualRescue, DiskSocpKernel, GateMip, SphereDecodeSnap) are default-on and keep-better/byte-exact.
- STEP 6 is DONE (OI-06 gave the deadline-free FAST terminal polish a 2s bound; OI-11 warm-seeded GateMip's
  tree so BoundPrunedRecovery runs once). Slow suite `:core:test -PslowTests` GREEN (740 tests, 0 fail).
- STEP 9 benchmark is DONE: `docs/research/solver-rework-2026-08/BENCHMARK-STEP9.md` + re-runnable per-capture
  TSVs and `compare.py` under `benchmark/`. Headline: 0 FAST feasibility regressions of 126; multi-jump got
  STRONGER (41 wins / 5 regressions, +1.8 b of objective gains, incl. loopmm/j021/j345); single-jump held.
  FASTER is mixed (+48ms per-solve floor from the unconditional components, but better tail/aggregate).
- New this session (all UNCOMMITTED): `core/src/test/.../anglesolver/CorpusBench.java` (env-gated OLD-vs-NEW
  bench harness), `docs/research/solver-rework-2026-08/benchmark/` (reports + compare.py), the ARCH-2 design
  doc, and this file. There may be a leftover git worktree at `C:/pkcold` (OLD tree at 3d19c9ff) from the
  benchmark - reuse it or `git worktree remove --force C:/pkcold` when done.

## Task 1 - dead-code cleanup (safe; do first)

Now that the cutover is default-on and flag-free, some code has no live callers. Find and delete it.

- METHOD: for each class/public method/field in `core/src/main/java/.../anglesolver/` (esp. `solver/` and
  `graph/nodes/`), grep for references excluding the file itself. Classify: (a) NO live callers anywhere =
  delete; (b) referenced only by a test whose sole purpose is that dead code = delete both; (c) live = keep.
  Green-gate after each removal (fast suite), then `:core:test -PslowTests` at the end.
- LIKELY CANDIDATES to verify (confirm zero live callers before deleting): `LevelSetAscent` (BUILD-LOG notes it
  is no longer used by LongRunSolver - check ALL callers, it may still be used by a node); the `explore()`
  graph in `BuiltinGraphs` if nothing builds it; any default-off probe scaffolding or unused graph
  node/param/config left from the flag era; unused `ParamSpec`s in `NodeCatalog`.
- DO NOT DELETE the live ARCH-1 path: ClosedFormSolve, SlpSolve, CostateDualSolver, DiskSocpKernel, GateMip,
  SphereDecodeSnap, ResidualRescue, BoundPrunedRecovery, RelaxationRecovery, RecoveryLadder, TrendFilterSmooth,
  DeWiggle, SmoothFaceRecovery, LongRunSolver, the graph runner/nodes on the default path. These are used.
- Report the list of what you deleted (files, LOC) before/after; do not delete anything whose caller you have
  not actually inspected.

## Task 2 - fix j003 (the one real benchmark regression)

SYMPTOM (from `BENCHMARK-STEP9.md`): j003 (n=176, the LONGEST capture) at THOROUGH lands -30.27, a full block
short of OLD's -31.30, AND overruns its deadline (no result within 30s at a 10s budget). It is feasible at FAST
(-30.27, success) so it is NOT a feasibility regression; it is an optimize-quality + deadline-overrun bug. It
is ProblemsTest-green at its expect-config, so this only shows under the bench's saved-state + effort override.

ROOT CAUSE (traced this session; solver-trace lines):
- The receding-horizon solve found a feasible chain in ~1.24s. Then the OPTIMIZE polish ran: seam sweep, then
  a `BoundPrunedRecovery` ("BNB") that was given `budgetMs=1997` but ran `ms=5272` (2.6x over budget) and
  returned `incumbent=none`. Total 15s, exceeding the 10s deadline. Final obj stayed at the -30.27 seed.
- So TWO defects: (A) DEADLINE OVERRUN - `BoundPrunedRecovery` does not respect its budget tightly (it grinds
  its pattern enumeration / search past the clock), and `LongRunSolver`'s outer loops (the `commitLadder` loop
  in `solve()` and the `windowLadder` loop in `runHorizon()`) only check `cancel`, not the deadline; lead-in
  windows use cancel-only `ClosedFormSolve.optimizeRobust`. Enforcement leans entirely on the graph watchdog
  firing `cancel`, which a long inner search outruns. (B) SEARCH QUALITY - on j003's long gate-critical tail
  (~68 gate ticks, patterns zxz1@98..166) the byte-exact search finds nothing at the -31.3 bound (which the
  disk relaxation says IS reachable), so it stays at -30.27; OLD's search reaches -31.30.

FIX DIRECTION:
- (A) Make `BoundPrunedRecovery` check its deadline inside the pattern-enumeration and search loops (return the
  best incumbent when the clock passes), and add an overall-deadline check to `LongRunSolver`'s `commitLadder`
  and `windowLadder` loops so long runs stop at the deadline instead of relying only on the watchdog.
- (B) Get OLD's j003 trace to see why OLD's search reaches -31.30 where NEW's does not: run the SAME trace on a
  worktree at 3d19c9ff (copy CorpusBench.java into it) and diff the LRS/BNB flow. Then decide if the regression
  is a config/seed change from the cutover (fixable) or the search genuinely not scaling (in which case record
  it honestly and note ARCH-2 is the real fix).
- Do NOT paper over it by widening the harness timeout; the deadline overrun is a real defect.

REPRODUCE / MEASURE (trace one capture; SolverTrace writes to core/build/reports/solver-trace-<tag>.txt):
```
PKC_CORPUS=1 PKC_CORPUS_TIER=THOROUGH PKC_CORPUS_OPT_SEC=10 PKC_CORPUS_FILTER=j003 \
  PKC_CORPUS_TAG=j003 PKC_SOLVER_TRACE=j003 \
  ./gradlew :core:cleanTest :core:test --tests "de.legoshi.parkourcalc.anglesolver.CorpusBench" -q
# then read core/build/reports/solver-trace-j003.txt (grep LRS / BNB / ENGINE lines) and corpus-j003.tsv
```
GREEN-GATE: fast suite after changes, `:core:test -PslowTests` GREEN, and re-run the CorpusBench THOROUGH-10s on
j003 to confirm it now reaches ~-31.30 WITHIN its deadline (obj improves, wall <= ~11s, viol 0).

## Task 3 - STEP-7 low-risk consolidation (dedup ONLY; no behavior change)

Pure refactors, each byte-identical and green-gated. Detail: BUILD-LOG P7 entry + `IMPLEMENTATION-GUIDE.md`
sections 3.8 (P5) and 3.10 (P7). Items:
- F14: merge the TWO dF-pin mechanisms into one (`FacingPrefold` 1e-9 vs `YawTies` 1e-6).
- F11: close the FAST/OPTIMIZE seed-path capability parity gap (free-start + capCertify missing on FAST-explore;
  legal-push OPTIMIZE-only) in `BuiltinGraphs`.
- Translation dedup: one shared free-start/translation support term (currently mirrored ~5 sites).
- (Optional, perf-only) F12: cross-window dual warm-start in `LongRunSolver` (carry CostateDualSolver lambda
  across a window seam). Only if cheap; it is a speed lever, not correctness.
Each must be byte-neutral (prove via the green slow suite); if any cannot be made byte-neutral, STOP and record
it honestly rather than shipping a behavior change.

DO NOT do the risky STEP-7 subsumption (OI-13: "make the residual the SOLE recovery, delete BoundPrunedRecovery
/ RelaxationRecovery"). The STEP-9 benchmark shows `BoundPrunedRecovery` is load-bearing on the hard momentum
cases; deleting it regresses. That deletion belongs to ARCH-2 (issue #422), which replaces the whole recovery
layer with a certified global solver.

## Hard rules (unchanged; from AGENTS.md + user prefs)

- Never git commit / push / branch / stage - the user does all git. Do not create branches.
- No code comments (no javadocs, no inline). No em dashes anywhere in repo writing (docs/commits/code).
- `core/` stays Minecraft-free; do not break `Application.runSimulation()` wiring. No shipped numeric-solver dep.
- FEAS_TOL = 0, byte-exact. Green `./gradlew :core:test -PslowTests` at every handoff; fast `:core:test` after
  each change; run `:core:tableStyleCheck`. Tag any new corpus-driving test `@Category(SlowSolverTests.class)`.
- When a change cannot be verified safely, STOP and record it honestly. Prefer the simpler option; do not
  over-engineer or add features beyond these three tasks.

## After (each session): append a dated `BUILD-LOG.md` entry (what closed, measured before/after, next),
update the stage table, and STOP without committing. When tasks 1-3 are done and slow-green, the remaining
ARCH-1 work is STEP 8 (in-game QA on 26.2 Fabric + both Forge, user-only) and STEP 9 (re-run the benchmark via
CorpusBench for the conditional-ship verdict).
