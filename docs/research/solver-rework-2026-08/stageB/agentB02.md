# Stage B shard B02: where the solver iterates past progress (wasted work), per capture

Agent: B02. Territory: per-capture quantification of WASTED iteration/LP/anytime budget across three
populations (capped dual, SLP reject, ILS/anytime plateau). Builds on Stage A F1/F2 and
`docs/research/dual-newton-iteration-audit.md`; every number re-measured from current code.

Inspected: `00-context-pack.md`, `stageA/SYNTHESIS.md`, `stageB/orchestrator-timings.md`,
`docs/research/dual-newton-iteration-audit.md`; `CostateDualSolver.java`, `ClosedFormSolve.java`,
`SlpSolve.java`, `IlsPolishNode.java`, `SolverTrace.java`, `SolveCounters.java`, `EngineFileScreen.java`.

Method: (1) deterministic re-parse of the 109 canonical `build/reports/solver-trace-*.txt` (the same set
Stage A used) with awk. (2) fresh traces on the named captures via direct `java -cp "$(cat
core/build/test-classpath.txt)" org.junit.runner.JUnitCore EngineFileScreen` (JDK 25, Java-8 classes),
`PKC_SOLVE_FILE=<capture> PKC_SOLVER_TRACE=<tag> PKC_SOLVE_EFFORT=FAST|THOROUGH`; NO gradle. (3) a
temporary probe added to `CostateDualSolver.solve` (public `lastProgressIter`, `pgAt10/25/50/75`; last
1%-improvement iteration and fixed-iteration pgres samples) surfaced in the `CF rung` trace line, compiled
with `javac --release 8` into `core/build/classes`, run on the named captures, then REVERTED (`git
checkout` + recompile; working tree and the 109-file corpus restored, verified clean). Wall-clock
attributions are ESTIMATES from per-thread inter-line trace timestamps (each gap includes that solve plus
its recovery/violation check and log flush); iteration and LP COUNTS are exact. All wall-clocks are single
isolated runs and agree with `orchestrator-timings.md` (j021 270 vs 268 ms; j008b 193 vs 202 ms).

---

## B02-1: The three waste populations are DISJOINT across captures; no capture wastes on all three
LOCATION: whole solver chain, per capture.
CLAIM: each hard capture is dominated by exactly ONE of {capped-dual grind, SLP-reject thrash,
ILS-plateau}, so the "iterate past progress" problem is really three separate levers keyed to jump class,
not one knob.
EVIDENCE: fresh FAST traces, per-capture (counts exact; wall% estimated from trace timestamps):

| capture | n/jmp | CF rung | capped | cap% | SLP lp | rej% | LP/acc | dominant waste (est. wall%) |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| j005 | 9/1 | 2 | 0 | 0% | 0 | - | - | none (fast path) |
| j016-X2jmmp2p | 11/2 | 2 | 0 | 0% | 0 | - | - | none |
| j019-3jmmtruenix | 11/3 | 2 | 0 | 0% | 0 | - | - | none |
| j022-1bmhbfly | 11/1 | 3 | 0 | 0% | 746 | 99% | 82.9 | SLP-reject (~57%) |
| j008b-2jump | 25/2 | 34 | 16 | 47% | 48 | 79% | 4.8 | mixed, small (dual 5% + SLP 10%) |
| j021-rinav1-01 | 39/4 | 28 | 28 | 100% | 60 | 68% | 3.2 | capped-dual (~18%) |
| loopmm-3jump-lands | 33/3 | 29 | 1 | 3% | 791 | 87% | 7.5 | SLP-reject (~45%) |
| f2f-dfchain-multijump | -/dF | 232 | 0 | 0% | 0 | - | - | none (232 cheap solves) |
| df-chain-free-start | -/dF | 26 | 26 | 100% | 0 | - | - | capped-dual (~21%) |

IMPACT: simplicity/speed. Frames every downstream lever: single tight jumps (j022) and momentum (loopmm)
are SLP-bound; coupled multi-jump (j021) and free-start dF-chains (df-chain-free-start) are dual-bound;
dF-chains without a max objective (f2f) waste nothing.
CONFIDENCE: 0.9. DEPENDS-ON: F1, F2.

## B02-2: CostateDualSolver caps at 100 iters on 58.6% of ClosedFormSolve rungs; capped solves own 88.8% of all dual iterations
LOCATION: `CostateDualSolver.java:40` (`MAX_ITER=100`), `:209-253`; logged at `ClosedFormSolve.java:402`.
CLAIM: over the corpus, the majority of dual solves grind the full cap, and the capped population holds
almost all iteration budget, re-confirming the audit's 53%/85% at the CF-ladder callsite.
EVIDENCE: awk over the 109 canonical traces (`/ CF   .*rung .* iters=/`): 7752 CF-visible dual solves,
sumIters=511102, avg 65.9/solve; 4541 capped at iters=100 = 58.6%; capped hold 4541x100=454100 iters =
88.8% of all dual iterations. (58.6% > the audit's 53% because CF-trace sees only the ClosedFormSolve
ladder, not the FreeStartSolve jointLadder / SlpSolve dual-seed callsites the audit counter also covered.)
Per-capture cap rate ranges 0% (single jumps, f2f) to 100% (j021, df-chain-free-start). Corpus tail:
j346-base 968/1030 rungs capped (94%), j716-base 345/451, j347-base 213/315. 88/106 corpus files have >=1
capped CF rung.
IMPACT: speed, large in iteration budget; but see B02-8, dual truncation is measured-dead.
CONFIDENCE: 0.92. DEPENDS-ON: B02-3, F1.

## B02-3: The capped population splits into slow-crawl vs FLAT-plateau; on j021 / df-chain-free-start ~50-56 of every 100 dual iterations are past the last progress
LOCATION: `CostateDualSolver.java:206-231` (pgres/du convergence tests; DIVERGE at :221).
CLAIM: capped solves are not uniform. j008b crawls (pgres still falling at the cap, iterations
load-bearing); j021 and df-chain-free-start PLATEAU (pgres flat/oscillating from ~iter 45-50 to 99), so
half the iterations produce no new best residual.
EVIDENCE: temporary probe (reverted) recording `progIt` = last iteration with a >=1% drop below the
running-min pgres, and pgres at fixed iters. Over capped CF rungs, FAST:

| capture | capped | total past-progress iters | avg/solve | avg progIt | pg@50 | pg@final | shape |
| --- | --- | --- | --- | --- | --- | --- | --- |
| j008b-2jump | 16 | 377 | 23.6 | 75 | 6.5e-1 | 2.5e-1 | crawl (still falling) |
| j021-rinav1-01 | 28 | 1424 | 50.9 | 48 | 8.2e-1 | 8.1e-1 | FLAT plateau |
| df-chain-free-start | 26 | 1449 | 55.7 | 43 | 2.7e-1 | 2.9e-1 | FLAT plateau |
| loopmm-3jump-lands | 1 | 24 | 24.0 | 75 | 2.4e-1 | 9.8e-1 | (single, oscillating) |

Example j021 rung: `iters=100 pg=5.101e-01 progIt=75 pg50=1.171e+00 pg75=1.867e-01` (pgres reaches 0.187
at it75 then RISES to 0.51 at it99, dual value stable at 10.216 across the whole run). Corpus pgres-at-cap
distribution (109 files): 74% of capped rungs end in [1e-3, 1] (crawl), 22% end >=1 (plateau), 1.3% >=4.
IMPACT: speed/correctness. The FLAT population (j021, df-chain, ~22% of corpus caps) is genuine dead
iteration; the crawl population (j008b, 74%) is slow convergence, not flatness. Distinguishing them is the
prerequisite for any safe stopping rule.
CONFIDENCE: 0.85 (progIt uses a 1% threshold; the pg50-vs-final ratio is the robust discriminator).
DEPENDS-ON: B02-2, F1.

## B02-4: SLP rejects 87.5% of LP steps corpus-wide; on j022 it is 99% reject at 82.9 LP/accept
LOCATION: `SlpSolve.java:286` (trace), trust-region halve/double logic.
CLAIM: re-verified the Stage A/audit reject rate and localized the extreme to level-set-ascent captures
that re-invoke SLP many times, each burning a long reject tail.
EVIDENCE: awk over 109 traces: 51907 lp# lines, 45430 reject = 87.52%, 8.01 LP/accept; phase-1 87.12%
(6168 acc / 41713 rej), phase-2 92.32% (309 acc / 3717 rej). Per named capture (fresh FAST):

| capture | SLP invocations | lp# | reject% | LP/accept | est. SLP wall% | est. rejected-LP wall% |
| --- | --- | --- | --- | --- | --- | --- |
| j022-1bmhbfly | 14 | 746 | 99% | 82.9 | 58% | 57% |
| loopmm-3jump-lands | (multi) | 791 | 87% | 7.5 | 54% | 45% |
| j008b-2jump | (multi) | 48 | 79% | 4.8 | 14% | 10% |
| j021-rinav1-01 | (multi) | 60 | 68% | 3.2 | 17% | 11% |

j022's 746 LPs come from 14 SLP starts (avg 53.3 LP/invocation, only 9 accepts total): the "closed form ->
SLP -> level set" chain re-solves SLP once per level-set rung while hugging the T50 wall to push the T52
objective. Corpus SLP tail: j335-shift 2903 rej / 3559 lp, j346-base 1734/1914. 71/106 files have >=1 SLP
reject.
IMPACT: speed. On single tight jumps (j022) and momentum (loopmm) the rejected LPs are the single largest
wall-clock slice (~45-57%).
CONFIDENCE: 0.9 (counts exact; wall% estimated). DEPENDS-ON: F2.

## B02-5: The SLP waste is a trust-region halving to the float lattice, not LP-kernel cost
LOCATION: `SlpSolve.java` phase-2 trust-region update; `TrustRegionLp`.
CLAIM: after the last accepted step, the trust region halves monotonically across a long run of full LP
solves that are all rejected, down to the float ULP, confirming F2's "trust-region management, not kernel"
diagnosis.
EVIDENCE: j022 FAST, one SLP invocation's tail (each line a full LP solve, all rejected):
`lp#41 tr=0.00488 reject ... lp#56 tr=1.49e-07 reject` (16 consecutive halvings 0.00488 -> 1.49e-7).
Accepted steps only ever survive at tr in {0.234, 0.117, 0.0586, 0.0293, ...}; everything below ~2e-2 is
thrown away. Same pattern inside B&B: j008b THOROUGH `lp#28..31 tr=1.19e-6..1.49e-7 pred=-1.4e-7
obj=-0.215313982 (unchanged) reject`.
IMPACT: speed, ~45-57% of wall on j022/loopmm; a curvature-aware trust region or an earlier "tr below
step-floor -> stop" gate removes it with no objective change (measure-gated per the audit's knife-edge
warning).
CONFIDENCE: 0.9. DEPENDS-ON: B02-4, F2.

## B02-6: The THOROUGH anytime tail runs ~36-39% of wall producing sub-certify-floor gains
LOCATION: `IlsPolishNode.java:32-37` (ILS start log; no per-round trace), graph deadline.
CLAIM: on coupled captures the deterministic chain (through B&B) converges near ~6 s, then ILS spins to
the deadline with gains three orders of magnitude below the ~1e-4 b certify floor.
EVIDENCE: fresh THOROUGH 12 s traces + EngineFileScreen incumbent log (temporary `objup` marker, reverted):

| capture | feasible@ | last obj-improve@ | run end@ | dead tail | ILS window | ILS obj gain | still short of COPT |
| --- | --- | --- | --- | --- | --- | --- | --- |
| j008b-2jump | 210 ms | 5616 ms | 9040 ms | 3424 ms (38%) | 3478 ms | 1.48e-7 b | 1.8e-2 b |
| j021-rinav1-01 | 470 ms | 5915 ms | 9092 ms | 3177 ms (35%) | 3240 ms | 9.1e-8 b | 1.5e-3 b |

Budget ladder confirms budget-insensitivity: j008b obj is BYTE-IDENTICAL at 4 s and 8 s budgets
(-0.215314 both); j021 shows a fully dead 1 s->2 s window (1067.845184 both) before B&B engages at 4 s.
The B&B-internal SLP has already collapsed (j021 `lp#29 tr=1.49e-7 obj=1067.862278684 reject`; j008b same)
when ILS starts, so ILS begins from a converged incumbent and cannot move it above the floor.
IMPACT: speed, ~35-38% of THOROUGH wall recoverable by terminating anytime nodes after K rounds with
sub-floor improvement; smoothness/correctness unaffected (the gain being discarded is below the certify
floor). This lever looks SAFE (unlike dual truncation) because the discarded improvement is provably
sub-floor, but it needs the standard corpus + pin re-run before shipping.
CONFIDENCE: 0.8 (measured on 2 captures; the FAST corpus never reaches ILS so no 109-file cross-check).
DEPENDS-ON: F15.

## B02-7: Total wasted budget per solve, and where the biggest lever is per capture
LOCATION: aggregate of B02-3/4/5/6.
CLAIM: the removable fraction of a single hard solve is large (35-57% of wall) and the lever differs by
class; the dominant SAFE lever corpus-wide is SLP-reject + anytime-plateau termination, NOT dual speedup.
EVIDENCE: per-capture est. wall attribution (single instrumented FAST run; THOROUGH for the anytime row):

| capture | span ms | CF dual (cf%) | wasted flat-dual (%) | SLP (%) | rejected-LP (%) | biggest single lever |
| --- | --- | --- | --- | --- | --- | --- |
| j022-1bmhbfly | 150 | 34 (23%) | ~0 | 87 (58%) | 85 (57%) | SLP trust-region |
| loopmm-3jump-lands | 365 | 68 (19%) | ~0 | 197 (54%) | 163 (45%) | SLP trust-region |
| j008b-2jump | 193 | 87 (45%) | 10 (5%) | 28 (14%) | 20 (10%) | mixed, none large |
| j021-rinav1-01 | 270 | 118 (44%) | 50 (18%) | 45 (17%) | 29 (11%) | flat-dual bail + SLP |
| df-chain-free-start | 103 | 44 (42%) | 22 (21%) | 0 | 0 | flat-dual bail |
| f2f-dfchain-multijump | 141 | 69 (49%) | 0 | 0 | 0 | none (232 cheap solves) |

Verdict on the three levers: (a) CONVERGE-FASTER the dual is the WRONG lever, F1 shows converging tightens
the BOUND but not the degenerate RECOVERY, and Stage 0 shows COPT already solves the SOCP in <20 ms, so the
grind is a bespoke-kernel non-convergence, not intrinsic hardness. (b) ITERATE-LESS on the dual is
measured-dead (B02-8). (c) The SAFE, largest levers are ITERATE-LESS on SLP (B02-5, ~45-57% of wall on
j022/loopmm) and on the anytime tail (B02-6, ~35% of THOROUGH wall), plus RECOVER-DIFFERENTLY for the flat
dual face (F1) rather than grinding it.
IMPACT: speed, up to ~half of a hard solve's wall clock, split across two safe levers and one recovery
redesign. CONFIDENCE: 0.8 (wall% estimated from trace gaps). DEPENDS-ON: B02-3, B02-5, B02-6, B02-8, F1.

## B02-8: The flat-dual iterations are real waste but NOT safely truncatable; only the bound is loose, per Stage 0
LOCATION: `CostateDualSolver.java:221` (DIVERGE gate does not fire on the flat cases).
CLAIM: cutting MAX_ITER to reclaim the flat tail (B02-3) is measured-dead; the flat cases pass under the
DIVERGE detector, and downstream consumers use the exact tail iterates.
EVIDENCE: (1) DIVERGE requires `pgBest>4.0 AND stall>=12`; j021 oscillates pg 0.18-1.2 (resets stall) and
df-chain-free-start plateaus at pg~0.28 (below 4.0), so neither trips, both grind all 100. (2) The audit
(`dual-newton-iteration-audit.md` sec 3, 5) measured MAX_ITER 100->50 loses gh283-j990-cold (0/39), cap95
fails / cap90 solves / cap75 fails on that pin (deterministic but NON-MONOTONE), and cap1000 leaves 2896
solves still capped while costing 3.9x. (3) Stage 0 frames it: COPT solves the same SOCP disk relaxation in
<20 ms and the CostateDualSolver bound is loose ONLY from non-convergence (F1), i.e. the dual VALUE is
already near-tight (j021 dual stable at ~10.216 while pgres thrashes), so more iterations would neither
tighten meaningfully nor fix the degenerate recovery. The correct move for this class is recover-differently
(a null-space min-slack projection / a proper SOCP kernel), not iterate-more or iterate-less.
IMPACT: correctness/speed. Redirects the dual lever away from the two dead directions the audit already
burned. CONFIDENCE: 0.85. DEPENDS-ON: B02-3, F1.

## B02-9: `SolveCounters` already provides a shippable, contention-free way to reproduce these counts
LOCATION: `SolveCounters.java` (`dualSolve`, `dualIters`, `dualStalled`, per-node `recordNode` nanos),
`CostateDualSolver.java:96`.
CLAIM: the per-callsite dual/iteration/stall counters and per-node time buckets used by the audit are still
in the tree (gated by `SolveCounters.ENABLED=false`); a future Stage E harness can dump exact
capped/iteration/LP totals per node without the trace-timestamp estimation this shard used for wall%.
EVIDENCE: read `SolveCounters.java`; `dualCtor` increments at `CostateDualSolver.java:96`;
`recordNode(id, dForward, dModel, dDual, dIters, nanos)` exists but is unused unless enabled;
`SolveNodeStatsScreen` is the existing dumper. The wall% figures here are the only estimated numbers; wiring
`ENABLED=true` + a dump in EngineFileScreen would replace them with exact per-node dual-vs-SLP-vs-forward
nanos.
IMPACT: measurement infrastructure for Stage E, removes the estimation caveat on B02-7.
CONFIDENCE: 0.9. DEPENDS-ON: none.

---

## Reproduction commands

```
# corpus aggregate (run BEFORE generating any new solver-trace-* files)
cd build/reports
awk '/ SLP  .*lp#/{t++; if($0~/accept$/)a++; else if($0~/reject$/)r++} END{print r, t, 100.0*r/(a+r), (a+r)/a}' solver-trace-*.txt
awk '/ CF   .*rung .* iters=/{t++; match($0,/iters=[0-9]+/); if(substr($0,RSTART+6,RLENGTH-6)+0>=100)c++} END{print c, t, 100.0*c/t}' solver-trace-*.txt

# fresh per-capture trace (repeat per capture / effort)
CP=$(cat core/build/test-classpath.txt)
PKC_SOLVE_FILE=core/src/test/resources/captures/j021-rinav1-01.json PKC_SOLVE_EFFORT=FAST \
  PKC_SOLVER_TRACE=j021-fast PKC_SOLVE_TIMEOUT_MS=60000 \
  java -cp "$CP" org.junit.runner.JUnitCore de.legoshi.parkourcalc.anglesolver.EngineFileScreen

# THOROUGH budget ladder for the anytime plateau
PKC_SOLVE_EFFORT=THOROUGH PKC_OPTIMIZE_SECONDS=8 PKC_SOLVE_TIMEOUT_MS=40000 ...

# past-progress iters require the reverted probe (lastProgressIter / pgAt50 on the CF rung line)
```
