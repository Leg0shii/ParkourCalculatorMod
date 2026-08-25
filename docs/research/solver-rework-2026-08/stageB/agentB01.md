# Stage B shard B01: wall-clock timing distribution and the tail

- Agent: B01
- Territory: whole-corpus FAST wall-clock distribution; biggest time sinks per capture class; the tail (which captures blow 800 ms and why).
- Method: live engine headless via direct `java -cp "$(cat core/build/test-classpath.txt)" org.junit.runner.JUnitCore de.legoshi.parkourcalc.anglesolver.EngineFileScreen`, JDK 25 (Temurin 25.0.3, same as the orchestrator), env `PKC_SOLVE_FILE`/`PKC_SOLVE_EFFORT`/`PKC_OPTIMIZE_SECONDS`/`PKC_SOLVE_TIMEOUT_MS`. One clean serial cold JVM per capture, machine otherwise idle, never concurrent. `ms` is the harness value measured inside the JVM from just before `engine.solve()` to completion (excludes JVM startup, class-load, file parse; includes worker spawn), so it is directly comparable to `orchestrator-timings.md`.
- Files/commands inspected: `EngineFileScreen.java`, `SolveNodeStatsScreen.java`, `SolverTrace.java`, `SolveRunRecord.java`, `graph/BuiltinGraphs.java:55-185`, `AngleSolverState.java:52-73`. Captures: all 24 top-level `j0*`, hpk `d9`(30)/`d10`(20)/`d11`(8), `loopmm-*`(3), `razor-*`(6 of 12), `taser-*`(2), `nix-*`(2), `f2f-dfchain-multijump`, `df-chain-free-start`. Traces written to `build/reports/solver-trace-{j346,j330,loopmm,nixfull}.txt`.
- Convention below: FAST ms is a single cold-JVM run unless a median of repeats is stated; see B01-11 for the run-to-run variance this carries.

---

## B01-1: The FAST wall-clock FLOOR is ~100 ms, not 0.1 ms; the 0.1 ms figure is internal-only

- LOCATION: `AngleSolverEngine.solve()` worker spawn + cold-JIT of solver classes; `EngineFileScreen.java:372-405` timing window.
- CLAIM: No engine solve returns in under ~99 ms of measured wall-clock, however trivial the jump; the "0.1 ms" in the pack's "0.1 ms to 800 ms" is the internal closed-form tight-loop time, not the engine wall-clock.
- EVIDENCE: minimum over 89 succeeding captures = 99 ms (`hpk/d9/j126`). Closed-form single jumps cluster at the floor: j004 135, j005 131, j016 median 136 (131/136/142 over 3 cold JVMs), j010 149, j015 149, j757 138. j016's true algorithm cost is microseconds (pack), so ~130 ms of its wall-clock is fixed cold-JVM + worker-spawn overhead. Matches the orchestrator's own note.
- IMPACT: correctness of the envelope statement (the lower bound is off by ~1000x). No speed lever: this floor is JVM/class-load, not solver work; irrelevant to the warm in-app case where the same solve is sub-ms.
- PROPOSAL: restate the envelope lower bound as ~0.1 ms internal / ~100 ms engine-wall-cold. Do not chase it.
- CONFIDENCE: 0.95
- DEPENDS-ON: none

## B01-2: FAST distribution over 89 succeeding captures: median 302 ms, p90 895 ms, p99 4163 ms

- LOCATION: whole corpus.
- CLAIM: typical FAST solves are a few hundred ms, but the succeeding distribution has a heavy right tail into multiple seconds.
- EVIDENCE: over the 89 captures that succeed under FAST (single cold run each, isolated value preferred where I have repeats):
  `N=89  min=99  p10=149  p25=177  median=302  p75=533  p90=895  p95=1218  p99=4163  max=4882  mean=504` (ms).
  Bucketing: 44/89 at <=300 ms, 33/89 in 300-800 ms, 12/89 over 800 ms. Computed by `sort -n | awk` on the measured ms list.
- IMPACT: speed baseline for the whole campaign; this is the reference distribution to not regress. Median 302 ms is the honest "typical" number.
- PROPOSAL: adopt median 302 / p90 895 / p99 4163 (succeeding-only) as the Stage B headline distribution; carry the fail/slow-success tail separately (B01-6).
- CONFIDENCE: 0.85 (shape robust; individual ms carry the B01-11 variance)
- DEPENDS-ON: B01-11

## B01-3: The "0.1 ms to 800 ms" ceiling is REFUTED as a bound; 800 ms is a p88, not a max

- LOCATION: pack section 4 PERFORMANCE ENVELOPE; `orchestrator-timings.md` "Key reads".
- CLAIM: 800 ms holds for ~86% of succeeding captures and for none of the hard/fail class; it is a rough p88, not an envelope ceiling.
- EVIDENCE: 77/89 succeeding captures are <=800 ms (86.5%); p90 = 895 ms already exceeds 800. The 12 that exceed 800 ms (FAST ms):
  | capture | ms | class |
  | --- | --- | --- |
  | hpk/d10/j335 | 827 | recv-horizon -> CF -> relax recovery |
  | hpk/d11/j716 | 835 | recv-horizon -> CF -> relax recovery |
  | j002 | 895 | receding horizon (multi) |
  | nix-t25-setup-tick | 1074 | recv-horizon -> CF -> relax recovery |
  | hpk/d9/j246 | 1084 | receding horizon |
  | hpk/d11/j1150 | 1084 | recv-horizon -> CF -> relax recovery |
  | hpk/d10/j144 | 1218 | recv-horizon -> CF -> relax recovery |
  | taser-100t | 1221 | receding horizon (100 ticks) |
  | j003 | 1652 | receding horizon (multi) |
  | j001 | 2085 | receding horizon (353t/30 jumps) |
  | hpk/d10/j347 | 4163 | recv-horizon -> CF -> relax recovery -> level set |
  | hpk/d10/j346 | 4882 | recv-horizon -> CF -> relax recovery |
- IMPACT: correctness of the envelope claim. The pack's "800 ms and that is GOOD" is true for the common case but must not be quoted as a ceiling; ~1 in 7 succeeding captures exceeds it, and the hard class is 5x-60x over.
- PROPOSAL: reword the envelope to "typical (median) ~300 ms, p90 ~900 ms, worst succeeding ~5 s, hard-class fails/slow-successes 24-47 s"; hold p90 as the no-regress target.
- CONFIDENCE: 0.9
- DEPENDS-ON: B01-2, B01-6

## B01-4: j001 is ~2.05 s at FAST, NOT ~100 ms; dominated by receding-horizon window count, not the objective

- LOCATION: `graph/nodes/RecedingHorizonNode.java`; j001.json (353 ticks, 30 jumps, 81 constraints).
- CLAIM: the task-prompt figure "j001 is ~100 ms" is wrong by ~20x; j001 FAST is ~2.05 s and the cost is the number of committed windows across 353 ticks, not objective polishing.
- EVIDENCE: 3 cold JVMs = 2086 / 2235 / 2035 ms (~5% spread, median 2085). Live trace within each run: all 81 constraints feasible (`liveMet=81/81`) by ~200 ms, then the solve continues ~1.85 s more while the objective creeps 12.225675 -> 12.225687 (a 1.2e-5 b gain) as the receding horizon commits the remaining windows; final solver label is "receding horizon" (no "(first feasible)" suffix, i.e. it runs the full commit ladder).
- IMPACT: speed attribution; corrects a load-bearing hypothesis in the task. j001 is the second-largest natural single-solve tail after j346/j347.
- PROPOSAL: use 2.05 s as the j001 FAST reference; the receding-horizon window/commit ladder (not the objective) is the lever for large-tick runs.
- CONFIDENCE: 0.9
- DEPENDS-ON: none

## B01-5: The p99/max succeeding tail is the d10 momentum-butterfly-neo pair (j346 4.9 s, j347 4.2 s); time splits CF ~= SLP

- LOCATION: `ClosedFormSolve` (CF stage) + `SlpSolve`/`TrustRegionLp` (SLP stage) inside the receding-horizon terminal window; trace `build/reports/solver-trace-j346.txt`.
- CLAIM: the slowest succeeding captures spend their seconds ~evenly in the closed-form margin ladder and the trust-region SLP, as death-by-iteration, not one blocking call.
- EVIDENCE: j346 FAST traced 4802 ms, single-threaded (all 2699 trace lines on thread t26), per-stage attributed by summing inter-line deltas: CF 2168 ms (45%), SLP 2104 ms (44%), relaxation-recovery RXT 434 ms (9%); largest single inter-line gap only 97 ms. SLP emits 1986 of 2699 lines (heavy LP iteration, corroborating Stage A F2's 87.5% LP reject). j330 (745 ms iso, single-thread): SLP 497 ms (67%), CF 118 ms. j347 isolated 4163 ms (batch 4437), same chain plus level set.
- IMPACT: speed; localizes the worst succeeding wall-clock to CF+SLP grind on the "recv-horizon -> closed form -> relaxation recovery" chain (momentum butterfly-neo geometry). Cutting SLP's rejected trust-region steps (Stage A F2) or reusing the CF margin-ladder warm state would attack ~89% of j346's time.
- PROPOSAL: profile CF margin-ladder rung count and SLP LP-call count on j346/j347; these two captures are the Stage B/E speed benchmark for the hard-single class.
- CONFIDENCE: 0.85
- DEPENDS-ON: B01-11

## B01-6: The real tail is 24-47 s and mixes accepted-fails AND slow-successes, all on the FAST fallback ladder

- LOCATION: FAST graph cold-miss path; `graph/BuiltinGraphs.java:109-147` (rescueBnb, coldBnb, nearBnb, bnbFF).
- CLAIM: when FAST does not find a quick first-feasible, it drops into a fallback ladder that runs tens of seconds; the tail is not only accepted-fails, it also contains slow-successes.
- EVIDENCE (each run to natural termination, harness cap high enough that the printed line is a real finish, not a cap):
  | capture | FAST ms | result | solver label |
  | --- | --- | --- | --- |
  | loopmm-tight-t39 | 31514 | SUCCESS 6/6 | pattern B&B -> ILS (better objective) (explore) |
  | razor-uncorrected | 24438 | FAIL 7/13 | receding horizon |
  | nix-full-t1 | 41469 / 47089 (2 runs) | FAIL 8/15, 9/15 | receding horizon |
  | razor-proof | 46052 | FAIL 7/14 | receding horizon |
  loopmm-tight-t39 reached feasible only at ~21 s and finished at 31.5 s via the FAST-explore race arm's pattern-B&B; a 20 s cap had shown it "still solving, 0 met", which is pre-feasibility, not failure. nix-full-t1 reproduces the orchestrator's ~40 s / 8-15 (my 41.5-47 s / 8-9 of 15; threaded nondeterminism). razor-proof-improved / razor-proof-t1 / razor-rung-attempt / razor-weirdpane / razor-weirdpane-attempt were still solving at a 25 s cap (0 met); their natural-stop time is UNMEASURED beyond 25 s but by analogy to razor-proof is ~24-46 s.
- IMPACT: correctness/robustness of the envelope tail; these are 30x-60x over 800 ms. Distinguishing slow-success (loopmm-tight) from accepted-fail (razor-proof, nix-full-t1) matters: the fallback ladder is doing useful work on the former.
- PROPOSAL: treat 24-47 s as the measured FAST tail band; the open question (Stage C) is whether the fallback node budgets can be cut without dropping loopmm-tight-class slow-successes.
- CONFIDENCE: 0.85 (loopmm-tight and razor-proof/nix natural stops measured; the other 5 razor stops UNMEASURED beyond 25 s)
- DEPENDS-ON: B01-7

## B01-7: FAST has no global deadline; the tail is the sum of per-node fallback budgets (peel 12 s, freeImprove 20 s, bnbFF/coldBnb 72 s, nearBnb 60 s)

- LOCATION: `graph/BuiltinGraphs.java:55-66,109-147,167-172`; `AngleSolverEngine` FAST `deadlineNanosFor=0`.
- CLAIM: with `t=0` (FAST), the graph falls back to `tp=120` and hands each fallback node a multi-second-to-minute budget, so a FAST cold miss can legitimately burn tens of seconds up to ~2 min; there is no single stopping deadline.
- EVIDENCE: code with `t=0`: `tp=120`, `peelSec=12`, `freeSec=20`, `rescueSec=3`, `stageSec=120`, `sweepSec=min(60,120/5)=24`, `bnbSec=(120-24)*3/4=72`, `nearBnbSec=min(60,120/2)=60`. Nodes `bnbFF`(:30) and `coldBnb`(:117) are `bnb` FIRST_FEASIBLE with `budgetSec=bnbSec=72`; `rescueBnb`=3 s, `nearBnb`=60 s, `freeImprove`=20 s, `setupPeel`=12 s. Measured natural stops (B01-6) land at 24-47 s = peel(12) + freeImprove(20) + partial BnB, well short of the 72 s per-node caps because FIRST_FEASIBLE BnB exits on feasible or pattern exhaustion. Corroborates Stage A F15 ("FAST deadline=0, bounded only by internal iteration caps + fixed-second node budgets").
- IMPACT: robustness; the FAST latency ceiling is policy-unbounded and set by summed node budgets, not by an SLA. This is the mechanism behind the entire 24-47 s tail.
- PROPOSAL: give FAST a single wall-deadline (or scale the fallback node budgets down) so a cold miss cannot silently run tens of seconds; A/B against the loopmm-tight slow-success to size it.
- CONFIDENCE: 0.9
- DEPENDS-ON: B01-6

## B01-8: nix-tail wall-clock is parallel BnB + SLP across ~10 worker threads; BnB dominates CPU

- LOCATION: `BoundPrunedRecovery` (BNB) + `SlpSolve` inside the receding-horizon worker pool; trace `build/reports/solver-trace-nixfull.txt` (82618 lines).
- CLAIM: the nix-full-t1 ~41.5 s wall is a multithreaded parallel BnB+SLP search, BnB-dominated in CPU-time, not a single serial stage.
- EVIDENCE: trace spans >=10 worker threads (t35 with 21853 lines, t51-t60 with 3000-8600 each). Single-timeline delta attribution overcounts under interleave (it sums to ~42 s of CPU across concurrent threads and even yields a spurious negative FREE bucket), but the CPU-time ranking is unambiguous: BNB 22519 ms, SLP 15911 ms, CF 2923 ms, RXT 788 ms. Largest gaps are BNB->BNB (971 ms, 762 ms), i.e. long uninterrupted BnB node expansions. So the nix-tail class = receding-horizon windows failing and escalating to parallel `BoundPrunedRecovery` + SLP.
- IMPACT: speed/robustness; the nix-tail is the most expensive class and BnB is its dominant consumer. Any BnB budget or completeness change (Stage A F5/F10) directly moves this tail.
- PROPOSAL: for multithreaded traces use per-thread bucketing or `SolveRunRecord.NodeRun.elapsedNanos` (SolveNodeStatsScreen) rather than single-timeline attribution; the CPU-dominance conclusion (BnB > SLP) stands regardless.
- CONFIDENCE: 0.8 (thread interleave makes the exact per-stage wall share approximate; the BnB-dominant ordering is solid)
- DEPENDS-ON: B01-7

## B01-9: Difficulty tier does NOT predict wall-clock; constraint count + relaxation-recovery chain does

- LOCATION: hpk d9/d10/d11 corpus.
- CLAIM: the hpk tier label (d9 easy .. d11 hard) is uncorrelated with FAST wall-clock; the predictor is whether the chain escalates to "closed form -> relaxation recovery" (and the constraint count).
- EVIDENCE: per-tier FAST ms range (single cold run): d9 99-1465 (max hpk/d9/j330), d10 138-4882 (max hpk/d10/j346), d11 189-1084 (max hpk/d11/j1150). The hardest tier d11 tops out at 1084 ms, well under d10's 4882 ms. The two slowest hpk captures (j346 57/57 constraints, j347 54/54) are d10, and both run "recv-horizon -> closed form -> relaxation recovery"; the fastest d10 (j757 138 ms, j342 156 ms) stay on "receding horizon (first feasible)". j828 (d11, the known degenerate-recovery case) is only 613 ms at FAST first-feasible.
- IMPACT: correctness of any tier-based budgeting assumption; do not budget by hpk tier. Budget/route by chain escalation and constraint count.
- PROPOSAL: key the speed benchmark on chain class (first-feasible vs relaxation-recovery-escalated) not tier; j346/j347 are the hard-single benchmark irrespective of their d10 label.
- CONFIDENCE: 0.85
- DEPENDS-ON: B01-5

## B01-10: THOROUGH converges ~7.6-8.2 s at a 10 s budget and, unlike FAST, is bounded on accepted-fails

- LOCATION: `AngleSolverState.Effort.THOROUGH` (default optimizeSeconds=10, `AngleSolverState.java:57,74`); OPTIMIZE graph deadline.
- CLAIM: THOROUGH runs to roughly its budget and stops even when infeasible, whereas FAST on the same capture runs unbounded (B01-7).
- EVIDENCE (PKC_OPTIMIZE_SECONDS=10, one cold run):
  | capture | THOROUGH ms | FAST ms | obj change | note |
  | --- | --- | --- | --- | --- |
  | hpk/d10/j346 | 8193 | 4882 | none (-740.294311) | FAST already optimal |
  | loopmm-3jump-lands | 7616 | 350 | -279.354398 -> -279.300490 (+0.054 b) | seam sweep -> B&B -> ILS |
  | nix-t25-setup-tick | 7612 | 1074 | 8.696132 -> 8.698201 | B&B -> ILS |
  | razor-proof | 7726 | 46052 (fail) | fail 9/14 | BOUNDED at budget, vs 46 s FAST-fail |
  | j001 | 1785 | 2085 | none | finished under budget |
  Orchestrator's 12 s-budget THOROUGH (j005 9087, j016 9118, j019 9108, j022 9120, j008b 9102, j021 9126) is consistent (budget minus reserve).
- IMPACT: speed/robustness; THOROUGH is the well-behaved effort (bounded latency ~= budget); FAST is the one whose tail is policy-unbounded. razor-proof shows the contrast starkly: THOROUGH 7.7 s vs FAST 46 s for the same infeasible capture.
- PROPOSAL: the FAST fallback ladder should borrow THOROUGH's deadline discipline (B01-7).
- CONFIDENCE: 0.85
- DEPENDS-ON: B01-7

## B01-11: Single cold-JVM timing carries up to ~2x run-to-run variance on sub-second solves; <5% on multi-second solves

- LOCATION: measurement methodology (one cold JVM per capture, back-to-back serial loop).
- CLAIM: individual FAST ms for short solves are noisy (JIT warmup + class-load + thermal in a back-to-back loop dominate fixed overhead); multi-second solves are stable.
- EVIDENCE: same-capture repeats. Sub-second: j330 1465 ms (in the 30-capture d9 loop) vs 745 ms (isolated); loopmm-3jump-lands 1355 ms (first in a batch) vs 350/380 ms (isolated/traced) vs orchestrator 308 ms. Multi-second: j346 5070 (batch) vs 4882 (traced), 4% ; j347 4437 (batch) vs 4163 (iso), 6% ; j001 2086/2235/2035 over 3 cold JVMs, ~5%. j016 131/136/142 over 3, ~5% (but this is near the fixed floor, small absolute).
- IMPACT: correctness of the per-capture numbers; the distribution shape (B01-2) and the tail band (B01-6) are robust, but any single sub-second ms should be read +-50%. Batch position matters (later captures in a long serial loop run slower).
- PROPOSAL: for any capture where the exact ms is load-bearing, take the isolated median of >=3 cold runs; the values in B01-2/B01-3 prefer isolated where available and are otherwise batch single-runs (upper-biased for the sub-second entries).
- CONFIDENCE: 0.9
- DEPENDS-ON: none

## B01-12: coldBnb and bnbFF are the named accepted-fail budget-burner nodes; both FIRST_FEASIBLE BnB with a 72 s fallback budget

- LOCATION: `graph/BuiltinGraphs.java:30-31` (bnbFF), `:117-121` (coldBnb).
- CLAIM: the task's "coldBnb, bnbFF" are real FAST-graph `bnb` nodes in FIRST_FEASIBLE mode that fire on a cold/near-miss and consume the fallback BnB budget; they are the budget the accepted-fail pins burn.
- EVIDENCE: `g.add("bnbFF","bnb").set("bnbFF","mode","FIRST_FEASIBLE").set("bnbFF","budgetSec",bnbSec)` (:30-31) and `g.add("coldBnb","bnb").set("coldBnb","mode","FIRST_FEASIBLE").set("coldBnb","budgetSec",bnbSec).set("coldBnb","labelSuffix"," (cold)")` (:117-121), with `bnbSec=72` at FAST `t=0` (B01-7). The accepted-fail captures that route here (measured B01-6): razor-proof (46 s fail), nix-full-t1 (41-47 s fail), razor-uncorrected (24 s fail), and the 5 unmeasured razor variants. Stage A F10 notes the OPTIMIZE graph routes a coldBnb null straight to emit (surfaces a solvable jump as "no solution"); B01 confirms the FAST-side cost of that same node family.
- IMPACT: correctness/robustness; ties the empirical 24-47 s fail tail to a specific node family, so a budget or completeness fix has a clear target.
- PROPOSAL: cap bnbFF/coldBnb budgets under a FAST global deadline (B01-7) and fix the coldBnb-null false-negative (Stage A F10) together.
- CONFIDENCE: 0.85
- DEPENDS-ON: B01-6, B01-7

---

## Appendix: full FAST per-capture table (single cold run unless noted; ms)

Single jumps (top-level j0*): j001 2085(median/3) | j002 895 | j003 1652 | j004 135 | j005 131 | j006 176 | j007 294 | j008-bfneo 169 | j008-hyper-fp 171 | j008b-2jump 258 | j009 202 | j010 149 | j011 160 | j012 164 | j013 155 | j014 209 | j015 149 | j016 136(median/3) | j017 151 | j018 194 | j019 178 | j020 163 | j021 376 | j022 177

hpk d9: J143 418 | j108 185 | j111 133 | j119 232 | j120 124 | j121 381 | j122 336 | j126 99 | j127 210 | j128 395 | j129 533 | j132 250 | j133 302 | j134 527 | j135 437 | j138 390 | j146 260 | j147 623 | j150 302 | j187 448 | j246 1084 | j248 545 | j298 731 | j315 344 | j318 457 | j319 147 | j320 194 | j321 385 | j328 388 | j330 745(iso; 1465 in batch)

hpk d10: j140 401 | j144 1218 | j148 460 | j149 573 | j152 262 | j153 564 | j335 827 | j342 156 | j343 740 | j344 188 | j345 776 | j346 4882(traced; 5070 batch) | j347 4163(iso; 4437 batch) | j422 269 | j424 288 | j425 150 | j703 302 | j717 253 | j757 138 | j816 164

hpk d11: j1099 200 | j1149 550 | j1150 1084 | j155 486 | j716 835 | j718 277 | j828 613 | j925 189

families: loopmm-3jump-lands 350(iso; orch 308) | loopmm-3jump-solver-misses 336 | loopmm-tight-t39 31514 SLOW-SUCCESS 6/6 | taser-80t 304 | taser-100t 1221 | nix-t25-setup-tick 1074 | f2f-dfchain-multijump 246 | df-chain-free-start 157

tail (fail / slow-success, run to natural termination): nix-full-t1 41469 & 47089 FAIL 8-9/15 | razor-proof 46052 FAIL 7/14 | razor-uncorrected 24438 FAIL 7/13 | razor-proof-improved / razor-proof-t1 / razor-rung-attempt / razor-weirdpane / razor-weirdpane-attempt: still solving at 25 s cap, natural stop UNMEASURED (>=25 s)
