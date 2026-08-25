# Stage B synthesis: corpus + hot-loop benchmarking (measured)

Reducer: orchestrator, over stageB/agentB01.md..agentB06.md + orchestrator-timings.md. Every number is a
measured wall-clock (direct java -cp, clean serial JVM) or a deterministic count (contention-free). The
authoritative timing is the orchestrator's serial runs + B01's full-corpus distribution; deterministic
counts (iterations, LP calls, forward evals, model rebuilds) are from B02/B04/B05 probes reverted after
measurement. Shipped path GREEN throughout (B03: 703 tests, ProblemsTest 120/120).

## Top findings (ranked by impact x confidence)

### SB1 (B01) The perf envelope "0.1 ms to 800 ms" is wrong on both ends
FAST distribution over 89 succeeding captures: min 99 ms, MEDIAN 302 ms, p90 895 ms, p95 1218 ms, p99
4163 ms, MAX 4882 ms. 12/89 (13.5%) exceed 800 ms. The 0.1 ms figure is the internal warm-loop fast path,
NOT the engine wall-clock (floor ~100 ms from cold-JVM + worker spawn). 800 ms is a p88, not a maximum. The
hard TAIL is 24-47 s, mixing accepted-fails (razor-proof 46 s, nix-full-t1 41-47 s) and slow-SUCCESSES
(loopmm-tight-t39 31.5 s). Cause: FAST has NO global deadline, so a cold miss runs a fallback ladder (peel
12 s + freeImprove 20 s + bnbFF 72 s + nearBnb 60 s). Difficulty tier does NOT predict wall-clock (d11 max
1084 ms < d10 max 4882 ms); the predictor is chain escalation + constraint count. j001 (n=353) is ~2.0 s at
FAST, not ~100 ms. IMPACT: correctness of the SPEC invariant. CONFIDENCE 0.9 (distribution robust;
sub-second individual ms +-50% by B01-11 caveat).

### SB2 (B02, B04) The dual grinds a flat degenerate face; converge-faster is the WRONG lever
58.6% of ClosedFormSolve dual rungs cap at MAX_ITER=100, holding 88.8% of all 511k dual iterations. j021
and df-chain-free-start are 100% capped. The capped set splits: j008b CRAWLS (iterations load-bearing),
j021/df-chain PLATEAU FLAT (pgres flat from iter ~45-50, wasting 50-56 of every 100 iters). This
corroborates Stage 0: the bound is loose only from non-convergence (COPT SOCP tight in < 20 ms), and the
flat dual value is near-tight while the RECOVERY is degenerate. So the lever is recover-differently
(ARCH-1 residual), not converge-faster (bound) and not iterate-less (measured-dead lottery, dual-newton
audit). IMPACT: decisive, points straight at ARCH-1. CONFIDENCE 0.9.

### SB3 (B05) Capping buildHessian's inner loop is bit-identical and removes ~28% of solver leaf CPU
buildHessian's O(walls^2 x ticks) MAC loop runs the full tick range, but each wall's coef[] is nonzero
only on a causal prefix [0, lastCoupled]; capping at min(lastCoupled_i, lastCoupled_j)+1 drops only
trailing zeros (bit-identical, verified H sums equal). Op-weighted saving 64.8% of buildHessian (median
43%); timed 48-60%. buildHessian is 43% of solver leaf CPU, so this is ~28% of solver leaf CPU with ZERO
iterate change. This is the "bit-compatible on converged, strictly-better on capped" lever the dual-newton
audit believed did not exist. IMPACT: speed, the single largest zero-risk lever. CONFIDENCE 0.9.

### SB4 (B04, B05, F12) stepRange is dead at runtime; the polishers spend 94-98% on full O(n) forwards
stepRangePartial(from>0) fires 0 times on every solve; 100% of trajectory re-evals are full O(n) forwards.
SmoothingPolish did 141202/141260 forwards (99.96%) on a Smooth-OFF j001, ~3.5 s = 94% of the solve, EACH
a single-tick perturbation scored via a full forward. THOROUGH j008b: ilsPolish 2.5-3.1M forwards
(97-98%). Routing perturbation rescoring through the dead stepRange (plus incremental toGameFacings and
maxViolation) is ~2x on the dominant polisher cost. IMPACT: speed, large on the anytime path. CONFIDENCE
0.9. Also: SmoothingPolish runs even with Smooth OFF (94% of a Smooth-off j001 solve is a pass the user did
not ask for), a defect to fix independently (A09-5).

### SB5 (B02, B05) SLP rejects 87.5% of its LP steps; the waste is trust-region management
Re-verified 87.52% reject / 8.01 LP per accepted step over 109-137 traces (phase-2 worst 92%; j022 hits 99%
reject at 82.9 LP/accept via 14 level-set SLP re-invocations each halving the trust region to the float
lattice). ~11% of solver CPU, ~5% recoverable with a curvature-aware trust region. IMPACT: speed, medium,
knife-edge-risk so measure-gated. CONFIDENCE 0.9. (This whole layer is subsumed if ARCH-1 replaces the
full-n SLP fallback with the residual solve.)

### SB6 (B04) No caching anywhere; cross-window warm-start unbuilt; model rebuilt 19-44x per dualChain
Deterministic: JumpLinearModel rebuilt 20x (j021) / 43x (j008b) per dualChain, 248/436 compileWall calls
(19-44x per constraint), no cache. j001 solves 8 windows = exactly 8 COLD dual solves (cross-window
lambda warm-start unbuilt; angle-solver.md 5.1 estimates 2.5-5x). bnb/seamSweep are model+dual rebuild
storms (1352-1986 model builds, fresh cold dual per pattern node). WindowCache is a result-memo only.
IMPACT: speed + simplicity. CONFIDENCE 0.9. (Absolute cost of the model rebuild is low per A02-5; the
polisher full-forward and cross-window warm-start are the real levers.)

### SB7 (B06) Real headroom is only on coupled/degenerate captures; single jumps OVER-reach
Shipped THOROUGH vs COPT: real unreached headroom on j008b (+1.84e-2 b), j021 (+1.6e-3), j144 (+4.5e-4),
loopmm (+7.6e-4 vs human). OVER-reach (shipped byte-exact ABOVE COPT continuous, half-angle norm>1) on
j005 (+3.4e-3), j016 (+1.7e-3), j019 (+1.0e-2), and j345 (+1.97e-2, the largest measured). 6/13 captures:
FAST already AT the COPT optimum yet THOROUGH still burns the full 7.6 s (no bound-matched early-exit, ~7 s
reclaimable per easy capture). IMPACT: sets the ARCH-1 target precisely (coupled multi-jump), and flags a
free THOROUGH early-exit. CONFIDENCE 0.9.

### SB8 (B03) Correctness: green, deterministic, zero false-successes on the corpus, but a latent EQ risk
Slow suite GREEN (703 tests, 3m28s). Determinism HOLDS (byte-identical across runs on all coupled
captures). ZERO false-successes in 18 audited solves (isSuccess always matched recompiled maxViolation=0).
BUT the mechanism is real in code: compiled EQ uses zero-tolerance while UI satisfied() accepts EQ/range
within MET_TOL=1e-4, so a solve/ capture with a grid-unreachable EQ could pass ProblemsTest while
byte-exact infeasible (latent, not active). Fix: derive isSuccess from the same maxViolation<=FEAS_TOL as
the record (B03-4/5). Frontier misses reduced to two genuine (nix-full-t1 precision 1.85e-4, razor-proof-t1
basin); j318/j716 now SOLVE (stale labels). RazorColdT1 headline cold gate is RED (9/14), env-gated /
CI-excluded (invisible to the green suite). IMPACT: correctness. CONFIDENCE 0.9.

## Ranked perf levers (independent of the ARCH-1 rework; ship-able now)

1. Cap buildHessian inner loop at each wall-pair's last coupled tick: bit-identical, -28% solver leaf CPU
   (SB3). Zero corpus risk. Highest value, lowest risk.
2. stepRange-backed incremental rescoring in the polishers (+ incremental toGameFacings/maxViolation): ~2x
   on the dominant anytime cost (SB4).
3. Do not run SmoothingPolish when Smooth is OFF: reclaims up to 94% of a Smooth-off solve (SB4).
4. THOROUGH bound-matched early-exit: 6/13 captures reach the optimum at FAST but burn the full THOROUGH
   budget; a converged-bound early-exit reclaims ~7 s each (SB7).
5. Cross-window dual warm-start: 2.5-5x on long runs (SB6), when long runs become an inner loop.
6. Curvature-aware SLP trust region: ~5% (SB5), measure-gated, subsumed if ARCH-1 lands.

## Corrections routed to Stage C (SPEC) and the FINAL-REPORT

- The perf envelope in the SPEC must be corrected to the MEASURED distribution (SB1): FAST median 302 ms,
  p90 895 ms, p99 4163 ms, max 4882 ms, tail 24-47 s; 13.5% exceed 800 ms. Any ARCH-1 replacement is
  measured against THIS, and specifically must beat the shipped THOROUGH on the coupled class without
  regressing the 100-900 ms typical band.
- The known-hard issue is corpus-resident (SB2): j021, j008b, df-chain reproduce the flat-degenerate-face
  recovery breakdown; these are the ARCH-1 targets.
- The ARCH-1 win is bounded to the coupled/degenerate class (SB7); single/half-angle jumps are already
  handled by the shipped byte-exact search and are NOT ARCH-1 targets.
