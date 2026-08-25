# Agent B03 shard: correctness and determinism across the corpus

Territory: correctness, determinism, byte-exact-residual integrity, false-success and dropped-feasible
surfaces. NOT performance.

Files/commands actually run or inspected:
- `./gradlew :core:test -PslowTests` (the one permitted gradle invocation; 3m28s, BUILD SUCCESSFUL).
- Test result XMLs under `core/build/test-results/test/` (per-suite counts, per-capture SOLVE/DUAL/CLOSED
  stdout lines).
- Direct `java -cp` probes (classpath from `core/build/test-classpath.txt`, run on JDK 25):
  - `B03Verify` (scratch): full-engine solve then recompile `JumpConstraintCompiler.compile(spec)
    .maxViolation` on the produced yaws vs `SolveResult.isSuccess()`/met/total; objMatch validates the
    reconstruction reproduces the engine's own reported objective. Run on 18 solves.
  - `NondetProbe` (env `PKC_DIAG_FILE`,`PKC_RUNS`,`PKC_SECS`) on j021, j008b-2jump,
    trp-optimize-feasible-swap, loopmm-tight-t39.
  - `RazorColdT1` (env `PKC_RAZORT1=1`) the env-gated headline gate.
- Code read: `AngleSolverEngine` (runJob 1000-1051, buildResult/satisfied 1524-1603, runStagedRace
  886-943), `JumpConstraintCompiler` (compile/maxViolation 23-67), `ProblemsTest`, `Expect`,
  `OptimizeVsFastTest`, `RazorColdT1`; sidecars under `problems/{solve,closedform,dualrecovery}/`;
  `docs/research/angle-solver.md` 11.9 and S7; `solver-graph-learning-survey.md`.

---

## B03-1  Slow suite GREEN; all 39 skips are env-gated screens, not silent test loss

LOCATION: `./gradlew :core:test -PslowTests`; `core/build/test-results/test/*.xml`.

CLAIM: The full slow gate passes clean and nothing is skipped by accident; the 39 skips are all
Assume-gated diagnostic screens or the two env-gated WrapWindowIls replications.

EVIDENCE: grand totals across all suites: tests=703, skipped=39, failures=0, errors=0. ProblemsTest
120/120 in 164.7s (the dominant cost). Every skip is a `PKC_*`-gated screen (BlockSolveProbe,
NondetProbe, EngineFileScreen, StructureDump, MiqcpDump, RazorColdT1, ... ) or `WrapWindowIlsTest`
(6 tests, 2 skipped = the `PKC_WRAPILS_FULL` full replication). No production test class is skipped.

IMPACT: correctness, baseline. This is the trustworthy green reference for the campaign.

PROPOSAL: keep as the baseline; note (B03-8) that the headline cold gate is NOT inside this suite.

CONFIDENCE: 0.98.

DEPENDS-ON: none.

---

## B03-2  Determinism HOLDS on the coupled multi-jump class, including the improving anytime path

LOCATION: `NondetProbe` + `B03Verify` over j021, j008b-2jump, trp-optimize-feasible-swap,
loopmm-tight-t39.

CLAIM: Every coupled/multi-jump solve is byte-deterministic run-to-run: zero objective spread, identical
solver chain, even on the wall-clock-budgeted improving ILS/B&B path. The handoff's determinism claim is
re-verified and strengthened: the anytime search runs a fixed iteration schedule, not a wall-clock-driven
iteration count.

EVIDENCE (range = max-min objective across runs, byte compare):
- j021 (4-jump), 4 runs, first-feasible path: range = 0.000000 (obj 1067.684771 x4).
- j008b-2jump, 4 runs: range = 0.000000 (obj -0.215326 x4).
- trp-optimize-feasible-swap (THOROUGH seam-sweep path), 4 runs: range = 0.000000 (obj 4.237147 x4).
- loopmm-tight-t39, 3 runs: range = 0.000000 (obj -279.312301 x3; infeasible here, see B03-7 note, but
  still bit-identical).
- SHARPEST TEST: j021 forced THOROUGH, 3 runs exercising the full chain "receding horizon -> closed form
  -> SLP -> level set -> seam sweep (better objective) -> branch and bound (better objective) -> ILS
  (better objective)": obj = 1067.862397425 on all 3 runs (wall-clock 7683 / 7661 / 7641 ms). Identical
  objective under a 42 ms wall-clock spread proves the search is not time-sliced.

IMPACT: correctness/robustness, decisive. No nondeterminism found; the multi-threaded race + anytime
ladder is reproducible.

PROPOSAL: none required; record as the determinism baseline for the rework. Any future refactor that
introduces wall-clock-driven iteration counts would regress this and must be caught here.

CONFIDENCE: 0.9 (5 captures, up to the full improving path; not every corpus capture forced to THOROUGH).

DEPENDS-ON: none.

---

## B03-3  Byte-exact residual audit: zero false-successes observed across 18 engine solves

LOCATION: `B03Verify` over j001, j005, j016, j019, j021 (FAST+THOROUGH x3), j008b, nix-t25-setup-tick,
j023-eqlanding, j024-goal, df-openchain-free-start, gh386-4x2-luckyseed, j318, j716, j828, trp-swap,
nix-full-t1.

CLAIM: Every engine solve that reports `isSuccess()==true` is genuinely byte-exact feasible when its
produced yaws are replayed through `ExactJumpModel` and re-scored by `JumpConstraintCompiler.maxViolation`;
no shipped success is a false success on this spread.

EVIDENCE: for all 17 success results, recompiled `maxViolation == 0.000e+00` (both raw and wrapAll
realizations), and `objMatch=raw` (my reconstruction reproduced the engine's reported objective to
< 1e-6, so I scored the same path the engine reported). Includes the two structural risk classes:
EQ-landing (j023-1bmhbfly-eqlanding, viol 0) and dF seam corridors (df-openchain-free-start, viol 0).
The single not-success in the set, nix-full-t1, correctly reports success=false with maxViolation
1.85e-4 (a real infeasibility, not masked). Coupled cases that leave headroom are still feasible:
j021 FAST obj 1067.684771 vs THOROUGH 1067.862397 (both viol 0); j008b obj -0.215326 (viol 0).

IMPACT: correctness, high. The A09-8 "false-success surface" is a code possibility (B03-4) but does NOT
manifest on the measured corpus; shipped `isSuccess=true` results are trustworthy here.

PROPOSAL: keep this replay-through-ExactJumpModel check as a standing invariant; wire it into ProblemsTest
`solve/` (currently only `dualrecovery/`/`closedform/` re-verify byte-exact; `solve/` trusts isSuccess).

CONFIDENCE: 0.9.

DEPENDS-ON: B03-4.

---

## B03-4  False-success MECHANISM confirmed in code: EQ/range judged at MET_TOL=1e-4 but compiled EQ has zero tolerance

LOCATION: `AngleSolverEngine.satisfied` (1583-1603), `buildResult` feasible hardcoded true (1524-1548 via
buildResultWithObjective default 1138-1148), `JumpConstraintCompiler.maxViolation`/`slack`/`evaluate`
(23-67, EQ = `Math.abs(evaluate)`), `finalViolation` (1043) recorded but not gating isSuccess.

CLAIM: `isSuccess()` = `met==total` over UI constraints, where walls use FEAS_TOL=0 (identical to the
compiled wall) but EQ and range use MET_TOL=1e-4; the compiled `maxViolation` scores EQ with zero
tolerance and includes solver-derived corridors. So an EQ/range constraint left with a byte-exact
residual in (0, 1e-4] passes the UI (isSuccess true) while `maxViolation > 0`. This is a latent
false-success confined to EQ/range, not inequality walls.

EVIDENCE: `satisfied` for GT/GE/LT/LE returns at `v +- FEAS_TOL` (FEAS_TOL=0.0, line 63); for EQ returns
`abs(f-v) <= MET_TOL` (MET_TOL=1.0e-4, line 65,1600); for range both bounds carry `+- MET_TOL`
(1585-1586). `maxViolation` iterates ineq slack then `eq: Math.abs(evaluate)` with no band (compiler
lines 26-27, 46). `finalViolation = compile(spec).maxViolation(...)` is computed (1043) and fed only to
the run-record `feasible` flag (1047-1048), never back into `isSuccess`. Not observed on the spread
(B03-3, 0/17), because the solvers drove every EQ/range to exact 0 on those captures; the dangerous
surface is a `solve/` capture whose EQ/range target is not reachable on the 65536-bucket yaw grid, which
would pass ProblemsTest (isSuccess-based) while byte-exact infeasible.

IMPACT: correctness, moderate (latent). A single divergence would show "Solved" in the panel and
`feasible=false` in the run record simultaneously.

PROPOSAL: derive `isSuccess` from the same `finalViolation <= FEAS_TOL` used for the record; keep the
per-UI MET_TOL margins as presentation only (matches A09-8's proposal). Cheap and removes the whole class.

CONFIDENCE: 0.85 (mechanism proven in code; magnitude bounded to 1e-4 b; 0 occurrences measured).

DEPENDS-ON: none.

---

## B03-5  The success flag also diverges from met==total in the safe direction (met==total yet success=false)

LOCATION: DF decline path (`hasUnsupportedDf` 1092-1095, `DF_UNSUPPORTED_NOTICE`); observed on
`gh313-j121-dfneo`.

CLAIM: `isSuccess` is gated beyond `met==total`: a dF-unsupported (non-zero dF) capture returns
success=false even though all mappable UI constraints are met, confirming the flag is not a pure
`met==total` function and that the two judgments (B03-4) are independently computed.

EVIDENCE: ProblemsTest line `SOLVE gh313-j121-dfneo success=false met=12/12 20ms` (met equals total,
success false, by design: dF inequality retired, `shouldSolve:false`). This is the inverse of B03-4 and
is correct behavior, but it demonstrates the same structural point: the success flag and the UI met
count are decoupled.

IMPACT: correctness, low (informational). Confirms A09-8's structural decoupling from the other side.

PROPOSAL: fold into B03-4's single-source-of-truth change; the DF decline should set success=false via an
explicit feasibility/eligibility gate, not via a special outcome that happens to keep met==total.

CONFIDENCE: 0.9 (measured line).

DEPENDS-ON: B03-4.

---

## B03-6  The "Optimize dropped-feasible" bug is FIXED and guarded; trp-swap solves feasible and deterministically

LOCATION: `AngleSolverEngine.runStagedRace`/`runArm` (886-943); `docs/research/angle-solver.md` 11.9;
regression capture `captures/trp-optimize-feasible-swap.json` + `problems/solve/` sidecar; A09-7 surface.

CLAIM: The historical class where an infeasible higher-reach race result replaced a feasible incumbent is
fixed by a feasibility-first race guard, and the regression capture solves feasible and deterministically
on this build. The A09-7 architectural surface (shared mutable scenario, decentralized per-node
re-verify) remains a latent recurrence risk but is currently closed.

EVIDENCE: `runArm` computes `out.feasible = maxViolation <= FEAS_TOL` per arm byte-exact (930-935);
`exploreWon = explore.cand != null && (!primaryOk || explore.feasible)` (902-903), so a feasible primary
is never traded for an infeasible explore (matches 11.9 verbatim). Measured: trp-optimize-feasible-swap
solves success=true met=20/20 viol 0 (B03Verify obj 4.237147, ProblemsTest 7567 ms), and NondetProbe
range=0 across 4 runs on the seam-sweep "(better objective)" path. `OptimizeVsFastTest` (gh398 invariant:
optimize never worse than fast, publishes >= 2 incumbents) passes (7.6 s). No dropped-feasible reproduced.

IMPACT: correctness/robustness. The specific bug is dead; the recurrence surface is A09-7's to close by
making candidates carry an immutable (start, yaws) pair.

PROPOSAL: adopt A09-7's central best-feasible selector so the guard is by construction, not by convention;
the race guard here is one of >= 6 independent feasibility-first re-implementations.

CONFIDENCE: 0.85.

DEPENDS-ON: none.

---

## B03-7  Frontier-miss catalog: only nix-full-t1 and razor-proof-t1 are genuine; j318 and j716 solve byte-exact

LOCATION: `problems/dualrecovery/{j318,j716,j828}.expect.json`, `problems/solve/nix-full-t1.expect.json`,
`captures/razor-proof-t1.json`; `solver-graph-learning-survey.md:162`.

CLAIM: Of the four captures named as hard (j716, j318, nix-full-t1, razor-proof-t1), two solve byte-exact
now and two are genuine misses of two DIFFERENT classes: nix-full-t1 is a precision hair's-breadth miss
(1.85e-4 b), razor-proof-t1 is a basin-discovery miss (~1.8e-1 b).

EVIDENCE (measured this build, commit 3d19c9ff):
- j318 (d9, shouldSolve:true): DUAL solved via "closed form -> relaxation recovery" 5 ms viol<=0
  obj -1886.297290; engine viol 0 obj -1886.296718. SOLVES. Tight validity corridor, but cleared.
- j716 (d11, shouldSolve:FALSE): the sidecar's false is a stale dual-chain-only label. DUAL actually
  solved via "closed form -> relaxation recovery" 115 ms viol<=0 obj -699.950168; engine viol 0
  obj -699.950168, 512 ms. survey doc:162 states the hpk "misses" (j155,j335,j716,j717,j828) are
  dual-chain misses only and the full fast pipeline solves all five in <= 30 s. NOT a genuine miss.
- nix-full-t1 (shouldSolve:false, GENUINE): engine THOROUGH success=false met=7/15, worst-wall
  byte-exact `maxViolation = 1.850e-4 b`, obj 8.699884 (recorded in-game attempt itself failed at
  8.698693). The 8 unmet UI walls are each clipped by <= 1.85e-4 b (~1.2 sine buckets); this is a
  near-feasible precision miss, not a gross one. Long multi-jump free start; awaits multi-jump seam work.
- razor-proof-t1 (GENUINE, headline gate): RazorColdT1 success=false met=9/14 obj 212.6998 in 93.7 s
  (see B03-8). angle-solver.md S7 documents this as a basin-discovery miss (cold searches plateau at
  viol ~1.8e-1, factor ~18 from the 1e-2 wrap-ILS trigger); the CMA race that produced the doc's
  212.8533/finalStart is now removed, so the exact obj/start differ but the class is unchanged.

IMPACT: correctness, high for scoping. The genuine frontier is 2 captures of 2 classes; the "misses"
j318/j716 are solved. Do not spend rework budget treating j318/j716 as open.

PROPOSAL: refresh the j716 sidecar (its shouldSolve:false is misleading; either flip to true with a
byte-exact assertion or annotate "dual-chain miss only, pipeline solves"). Treat nix-full-t1
(precision) and razor-proof-t1 (basin) as the two distinct remaining problems.

CONFIDENCE: 0.9 (all four measured; razor class cited from doc + my met=9/14).

DEPENDS-ON: none.

---

## B03-8  The headline cold gate (RazorColdT1) is env-gated, excluded from CI, and currently red

LOCATION: `RazorColdT1.java` (Assume-gated on `PKC_RAZORT1`); appears in the 39-skip list of the slow
suite; `captures/razor-proof-t1.json`.

CLAIM: THE HEADLINE GATE ("cold 5.4375 from t1", PASS = success + fresh-reparse viol <= 0) is not part of
the CI slow suite (it is `Assume`-skipped without `PKC_RAZORT1`), and it currently FAILS; so a red
headline gate is invisible to the normal green gate. The failure is consistent with the documented open
frontier (angle-solver.md S7), not a fresh regression.

EVIDENCE: run with `PKC_RAZORT1=1 PKC_RAZORT1_OPT_S=180 PKC_RAZORT1_S=300`:
`RESULT success=false met=9/14 ms=93726 obj=212.6998445 finalStart=(215.0820,-0.0276)` then
`AssertionError: headline gate: cold 5.4375 from t1 must solve`. In the slow-suite XML the class is
`tests=1 skipped=1`. The sidecar is deliberately NOT pinned into `problems/solve` (the plan pins only
after two consecutive passes), so ProblemsTest never runs it.

IMPACT: correctness/visibility, moderate. Fine as a deliberately-parked frontier, but there is no CI
signal if it regresses further or if a fix lands; it must be run by hand.

PROPOSAL: keep it parked, but add a lightweight CI-visible tripwire (e.g. record the current met=9/14 /
obj as an expected-miss baseline in a non-blocking screen) so a change in the miss is noticed; do not
pin the pass sidecar until it actually passes twice.

CONFIDENCE: 0.9 (measured miss; "expected not a regression" from S7 doc).

DEPENDS-ON: B03-7.

---

## B03-9  Marginal-but-passing catalog: closedform j019 near its objective budget; loopmm/DUAL near-uncapped runtimes

LOCATION: ProblemsTest stdout lines; `Expect` defaults (maxObjectiveGap 1e-2, maxMicros 2000).

CLAIM: A handful of assertions pass with little headroom and should be watched by the rework (they are the
first things a solver change would break).

EVIDENCE (measured this run):
- Closedform objective gap vs recorded ref (budget 1e-2): j019 gap 8.56e-3 (86% of budget, closest to
  failing), j005 6.80e-3, j016 2.27e-3, j020 1.95e-3, j015 1.53e-3. Timing headroom is large (max
  124.5 us vs 2000 us cap). So the closedform risk is objective drift, not speed.
- Runtimes with no or loose caps: `loopmm-3jump-lands` DUAL 26178 ms (dualrecovery has NO time
  assertion); `loopmm-tight-t39` solve 42051 ms (cap 120000), `loopmm-tight-t39-fast` 33548 ms (cap
  90000); j021 7575 ms, j022-noland 7194 ms, trp-swap 7567 ms (cap 40000). All pass, but loopmm's
  dualrecovery 26 s and the two loopmm solves are the slow tail.
- nix-t25-setup-tick and j828 exceeded a naive 60 s harness deadline only because the SAVE carries a long
  optimizeSeconds; bounded THOROUGH both solve byte-exact (viol 0) in ~7.6 s, so this is a save-budget
  artifact, not a hang.

IMPACT: robustness, low-moderate. These are the assertions with the least slack; a rework that shifts
closedform objective by > ~1.4e-3 b would start failing j019.

PROPOSAL: when touching ClosedFormSolve/recovery, re-check j019/j005 gaps first; consider a soft runtime
cap on the dualrecovery check (loopmm 26 s is the outlier).

CONFIDENCE: 0.85 (all numbers from this run's XML).

DEPENDS-ON: none.
