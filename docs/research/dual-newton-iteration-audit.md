# Dual Newton iteration audit (issue #384, 2026-08-21)

Executed at dev 64dada4 on the branch for issue #384. This is the measurement record for the two fix lanes proposed in `dual-newton-hessian-handoff.md` section 2; it supersedes that section. Every probe below was temporary (the RungAudit pattern from issue #380: per-callsite counters wired into `CostateDualSolver.solve`, stage counters in `LongRunSolver.solveWindow` and the jointLadder, all behind system properties) and was reverted after measurement. No solver code ships from this session.

**Verdict: the capped-iteration population is load-bearing through its iterate values, and every exit heuristic tried breaks the corpus. Both lanes are measured dead. The numbers are below so nobody re-attempts this without new information.**

## 0. Method notes

- Bench via direct `java -cp` (printTestCp init script), sweep = `FreeStartSweepBench` over 104 hpk captures at FAST, second corpus = full `ProblemsTest` (111 checks) via JUnitCore.
- The machine drifted mid-session (plain baseline moved 24.9 s to 27.5 s), so all timing claims come from interleaved A/B/A/B rounds run back to back, three rounds per config. Solve and iteration COUNTS from the audit are deterministic and were the primary comparator.
- Baseline reproduced the handoff exactly: 104/104, over-2s = j346 base, j347 base, j347 shift; `ProblemsTest` 111 OK in ~145 s.

## 1. Where the capped solves live (per-callsite audit)

Sweep, 104 captures, ~25 s wall, 15.7 s of it inside `CostateDualSolver.solve`. 9,551 solves, 598k iterations, 5,053 solves (53%) capped at `MAX_ITER = 100`; the capped solves own 13.3 s of the 15.7 s (85%).

| caller | solves | capped | in-dual ms | capped ms |
| --- | --- | --- | --- | --- |
| LongRunSolver window CF ladders (`closedForm` via `runLadder`) | 5,826 | 2,874 | 8,710 | 7,546 |
| FreeStartSolve jointLadder (probe + bisect + rungs, mostly under `runHorizon` free retry) | 2,349 | 1,411 | 5,902 | 4,851 |
| SlpSolve dual seed | 588 | 279 | 502 | 459 |
| everything else (RelaxationRecovery, dualBound, bestEffortShape) | 788 | 489 | 588 | 482 |

ProblemsTest flips the profile: `BoundPrunedRecovery` B&B node bounds dominate (935k solves, 405k capped, ~449 s in-dual summed across worker threads). Those are deadline-budgeted searches where an unconverged dual is still a valid (looser) bound, so their "capped time" is not removable wall time; it trades directly against pruning quality and the dualrecovery pins.

The sweep contains ZERO theta chain scans; its jointLadder volume is direct prefold pattern rounds. The 140-theta x 6-round machine from the handoff exists only on the free-start ProblemsTest pins (farseed, j990), inside FAST budgets they currently meet with 2-3x margin.

## 2. Contribution audit: the populations are load-bearing

- Joint margin ladder: every rung index 0-10 either directly certifies (rungs 1, 3), certifies via recovered start (0, 3, 5, 6, 7, 8, 9, 10), or improves the pattern-round best (all indices, 129 improvements at rung 0 down to 12 at rung 10). Nothing to collapse.
- rHi probe already short-circuits the bisection on 87 of 149 ladders; the bisection that remains feeds tStar, whose fractions the j990 pin covers.
- Window CF alternates win 10/375 (last) and 25/159 (lead-in); SLP alternates 7/283; SLP primary 21/119 (last). All stages produce solutions somewhere; none deletable.
- The one genuinely dead population: stages run inside windows that are provably continuous-infeasible. See section 4.

## 3. Lane A (exit heuristics): three independent corpus breakages

All variants were prototyped behind temporary system properties and run against the sweep, with the survivor promoted to ProblemsTest.

| variant | sweep | verdict |
| --- | --- | --- |
| relative-stall bail exactly as the handoff spec (no `lastStalled`, gate `pgBest > 1e4 x GRAD_TOL`, stall window 12) | 52-56 s, **103/104: j717 shift LOST** (0/19, 35 s timeout through bnbFF) | dead |
| `MAX_ITER` 100 -> 25 | 33 s: everything faster except **j716 base+shift explode 0.5 s -> 6.9 s each** (wrap-close burn re-engages) | dead |
| `MAX_ITER` 100 -> 50 | 18.5-19.0 s stable (-31%), 104/104, per-capture stable | **ProblemsTest: gh283-j990-cold LOST (0/39)**; stop-and-ask per the standing ruling |
| `MAX_ITER` 50 + stall bail | 17.3-21.4 s, 104/104 sweep | not pursued past the sweep: same family, marginal over cap50, second exit mechanism |

The decisive experiment: bisecting `MAX_ITER` on gh283-j990-cold alone. cap100 solves (489 ms), cap95 fails (12.6 s, 0/39), cap90 SOLVES (494 ms, objective differing from cap100 by 1.3e-6), cap75 fails, cap60 fails, cap50 fails. All repeatable run to run. **The outcome is deterministic but non-monotone in the cap.** There is no threshold to tune toward; every truncation just redraws a different downstream trajectory lottery, and the shipped corpus sits on the cap100 draw. The same chaos showed as round-to-round instability when two otherwise-stable changes were combined (j346 flipping 2.3 s / 5.7 s between runs via deadline-adjacent paths).

Why the failure mode exists: capped solves do not return garbage. Their iterates are still crawling monotonically toward the optimum (that is why the `U_TOL` input-space exit does not fire), and downstream consumers (rung candidates, warm starts, pattern evolution, recovered-start scoring) are sensitive to exactly those tail digits. Iterations 51-100 of a capped solve are not waste; they are slow convergence that some captures need and others merely tolerate.

The -31% cap50 sweep number stays on the table ONLY as a user ruling: it costs the j990 free-start pin outright, and the non-monotone cliff means any cap that happens to pass today's corpus is overfit to it. Recommendation: do not.

## 4. Lane B (fewer solves): one clean consolidation found, and it is wall-neutral

Prototype: after the primary window CF fails in `LongRunSolver.solveWindow`, run one cold margin-0 dual on the constraints-only wall set (byte-identical to the certificate `SlpSolve` already computes at its seed step); if unbounded, the window is continuous-infeasible for EVERY objective, so skip the 3 CF alternates and up to 4 SLP runs.

Measured on the sweep: fires on 49/129 failed last windows and 23/65 failed lead-in windows, costs 128 ms of certificates, removes ~1,900 dual solves and 30-50% of alternate/SLP calls, and every per-stage WIN count stays identical (it only ever kills losers). But wall time is unchanged (27.7 s vs 27.5 s reference): the solves it removes are unbounded duals that already exit early through the `LAMBDA_CAP` check, so they were cheap. Skipped per the complexity rule: +1 method and an extra solve per failed-primary window for a structural but not measurable win. If solveWindow is ever restructured anyway, this is the shape to fold in.

Handoff lane B items not pursued, with reasons: coarse-to-fine theta scanning and cross-round viability carry touch only the theta machine, which the sweep never enters and whose exact enumeration the j990 pin exists to protect, to shave ~3.6 s inside a 20 s budget currently met at ~7 s; weak-duality screening at 1-2 iterations is the jointLadder margin-0 probe that already exists (its skip path fired 0 times on the sweep, so there is nothing cheaper to screen with that would not re-enter section 3's lottery).

## 5. What a future attempt would need

- A speedup of the CONVERGENCE itself that is bit-compatible on converged solves and strictly-better on capped ones. Nothing of that shape was found; `buildHessian`/`choleskySolve` are already in the bit-identical-only zone.
- Or callers restructured so capped iterates are never consumed (certify differently, not from dual recovery). That is solver architecture work, not a perf patch.
- Any iterate-perturbing shortcut must clear: sweep 104/104 with per-capture stability across repeated runs (the deadline knife edges make single runs lie), full ProblemsTest, farseed/j990 FAST budgets, and the #383 pins. Three different variants each failed a different one of these.
