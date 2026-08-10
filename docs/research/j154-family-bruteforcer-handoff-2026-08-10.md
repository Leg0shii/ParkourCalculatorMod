# j154 cold: the objective-B&B is dead, the family brute-forcer works, the probe is broken, and certify is the wall (2026-08-10 night, fresh-session handoff)

## UPDATE 2026-08-10 (Path A executed): j154 SOLVED COLD BYTE-EXACT in 37.5s

Path A ran to completion and solved j154. Only `ColdCycleBeamScreen` (test) changed: added `PKC_COLD_BEAM_BUCKET_BUDGET` (sets `ColdSearch.BUCKET_SLICE_BUDGET`, restored in try/finally so pins keep 30) and `PKC_COLD_BEAM_THREADS` (default = availableProcessors), and replaced the sequential streaming probe->certify loop with a **parallel** one: a dynamic `AtomicInteger` work-queue over the sig array, each worker owning its own `Sweep[]` cscan/pscan (new `buildScan` helper), first byte-exact solve wins via `AtomicReference.compareAndSet` then `cancel.set(true)`; worker exceptions are surfaced to the log via try/catch. `gate>=100.0` => `certifyAll` (skips the broken probe entirely; `benchSig` already probes internally to seed certify). No production certify code was touched; the path is thread-safe because `ExactJumpModel`/`ColdProblem` are read-only and the only mutable statics are `lastDirectDebug` (benign) and the `BUCKET_*` tunables (set once before threads start).

Run: the exact section-2 config with `PKC_COLD_BEAM_BUCKET_BUDGET=3`, `PKC_COLD_BEAM_THREADS=10`. Beam built **58,831** candidates (tailCut 213,857); the parallel certify SOLVED at **idx 5617 / certified 5622 in 37,487 ms** (~150 certifies/sec across 10 threads, no worker failures, EXITCODE=0). Found sig = `4.8.8.8.8.8.8.8.8.8.8.8.8.8.6.2+4.2+2+2+2+2+2+2+2+2+2+2+1+` (the human line, independently rediscovered from the sanctioned family alphabet; solver never read the human sig/yaws/debug). This sig is **already pinned** in `ColdSearchRegressionTest.j154KnifeEdgeLineCertifiesByteExact`; all 7 pins re-ran GREEN after the change.

Ops lesson: the first launch died to an external gradle daemon "stop command received" (shared repo, concurrent sessions). Fix = **`--no-daemon`** for all detached coldsearch runs (self-contained, not in the daemon registry, immune to `--stop`).

Remaining (NOT done): (B) the form-based certify rewrite (~1ms, drop the glide prior); (3) GENERALIZE via `ColdHumanDiagScreen` then the brute-forcer on j716 (mirror handedness {SA,WD}: mirror j154's alphabet swapping A<->D). The rest of this doc is the pre-solve state.

## UPDATE 2026-08-10 (Path B profiling): the "form-intersection replaces the grid" premise does NOT hold; the real lever is per-eval construction reuse

Profiled `certifyLine` on j154 via `ColdCertifyProfileScreen` (env `PKC_COLD_PROF_FILE`/`PKC_COLD_PROF_SIG`; instrumentation counters `ColdSearch.profEntryEvals`/`profProbeSolves`/`profFeasEntries`, logic-neutral, pins green):
- **SOLVE** (human sig): entryEvals=**36**, probeSolves=**68**, ~**117ms** warm. The 3 continuous seed `sliceSolve`s all MISS (`recoverStartViol=2.69e-2` @ theta=-78); the solve happens in `bucketSweepCertify` at a discrete LUT bucket where the 3x3 entry grid finds feasEntries=1 on pass 0 (no fine 9x9, no entry descent). So the entry grid is CHEAP for a solve; it is not the 0.0002-sliver search the pre-solve notes imagined.
- **MISS** (near-miss sig, momentum intact): entryEvals=**899**, probeSolves=**983**, ~**1700ms** at BUCKET_SLICE_BUDGET=30. Cost = the bucket sweep doing up to `budget` `sliceSolve`s, each 3x3 grid + up to 80-eval entry descent (fires because bucket bestMiss ~8e-3 < ENTRY_DESCENT_TRIGGER 2e-2) + a dual-null `solveJoint`. At budget=3 (what Path A's beam uses) this is ~10x cheaper, and most beam candidates miss even cheaper (empty rect / quickBest>0.15 skips the bucket sweep entirely).

Two conclusions that reframe Path B:
1. **The knife-edge lives in discrete FACING-bucket space (SESSION-3), not entry-position space, and the linear/closed-form signals do NOT discriminate a feasible bucket from an infeasible one** (the free-start/probe violation is ~2.7e-2 at BOTH the solve bucket and miss buckets: the "probe is broken / rank-inverted" finding). So there is no closed-form "wall-form intersection" that can REPLACE the discrete byte-exact bucket sweep, because nothing but the byte-exact evaluation separates the feasible bucket. Path B point 2 (form-intersection replaces the entry grid) is mis-scoped.
2. **The valid Path B lever is point 1: eliminate the per-eval spec/linear-model REBUILD.** Each `sliceEntryEval` -> `buildSliceSpec` (rebuilds constraints + scenario) -> `sliceProbeSolve` -> `ClosedFormSolve.optimizeRobustGraded` (rebuilds `JumpLinearModel` + `FacingPrefold` + `CostateDualSolver`) at ~1.9ms. But `JumpLinearModel`/`FacingPrefold` are translation-invariant (identical across all entries at a fixed theta; only the constraint TARGETS shift with the entry). Hoisting that invariant construction out of the per-entry (and ideally per-bucket) loop makes each byte-exact check ~physics-floor (1.8us) instead of ~1.9ms: a real ~10-100x on BOTH solve and miss. This is a deep, risky refactor threading through `ClosedFormSolve`'s internals with 7 byte-exact pins to protect, NOT the shallow form-intersection the pre-solve notes described.

Net: Path A (37.5s) already meets the solve goal and, with bucketBudget=3, already runs in the sped-up regime; Path B's real form is a deep perf-only refactor. Kept: `ColdCertifyProfileScreen` + the `prof*` counters as reusable diagnostics.

### What was implemented (SLP + descent gating) and the HONEST result

Two data-backed, pin-validated gating changes in `ColdSearch` (both now tunable non-final statics), each justified by measuring decisiveness across all 7 pins via `ColdSlpDecisiveScreen`:
- `SLP_RESCUE_TRIGGER` 5.0e-2 -> **5.0e-3**. Measured: SLP is decisive (turns infeasible->feasible) ONLY at ClosedFormSolve violation <= **9.004e-04** (j154) and never when ClosedFormSolve returns null, across all 7 pins. The old 5e-2 fired SLP (~1ms, ~2x ClosedFormSolve's cost) on every ~2.7e-2 miss bucket for nothing.
- `ENTRY_DESCENT_TRIGGER` 2.0e-2 -> **3.0e-3** (= `SLICE_GRID_FINE_TRIGGER`). Measured: the entry descent is **decisive=0 across all 7 pins** (it never turned a miss into a solve), yet it burned ~2/3 of a miss's evals on close-but-infeasible buckets in the [3e-3, 2e-2] band.

Micro-benchmark on the j154 expensive near-miss (`certifyLine`, BUCKET_SLICE_BUDGET=30 default): **1664ms -> 401ms (~4.1x)**; SLP 1011ms->3ms; evals 899->297. All 7 pins stay byte-exact green.

**BUT the end-to-end j154 beam gain is only ~6% (37.5s -> 35.1s, ~150 -> ~160 certifies/sec).** Honest reason: the j154 beam is dominated by (a) CHEAP misses that fail the quick screen and skip the bucket sweep, and (b) the FIXED per-candidate `probeSig` full-circle 720-sweep scan. The SLP/descent gating only speeds the EXPENSIVE near-miss tail (candidates that reach the bucket sweep), a minority here. The 4.1x is real but lands on the minority path.

Where the win DOES matter: near-miss-heavy runs (e.g. certifying the full ~1.0M without the glide prior, where far more candidates reach the bucket sweep) and worst-case tail latency (the handoff's ~6s max misses shrink ~4x). It is genuine groundwork toward "drop the prior, certify 1M", just not a big number on j154's own beam.

The remaining, deeper levers for a large end-to-end win (NOT done, higher risk, shared-solver code): the per-candidate `probeSig` 720-sweep scan, and the ClosedFormSolve graded inner solve (~0.9ms/eval, the now-dominant certify cost). `buildSliceSpec` is NOT a lever (~0.06%). The pre-solve notes' "form-intersection replaces the grid" remains blocked (no closed-form discriminates the byte-exact bucket).

---


Self-contained continuation doc. Read this FIRST, then `j154-objective-bnb-nogo-2026-08-10.md` (the NO-GO proof), `modular-cycle-bruteforcer-vision.md` (the product direction), and memory `project_momentum_exact_search.md`. Everything is in worktree `.claude/worktrees/coldsearch`, branch `worktree/coldsearch`, uncommitted (the user commits). Pins: `ColdSearchRegressionTest` (7), full `-PslowTests` green (re-verify after picking this up).

## 0. TL;DR — where we are

- **j154 is NOT solved cold yet, but the pipeline that will solve it is built and correct.** It is a **per-cycle family brute-forcer** (`ColdCycleBeamScreen`), not the objective-B&B (that is proven dead, section 1).
- **Three discoveries this session, in order of importance:**
  1. **The probe screen is BROKEN for j154.** The human line (a real, byte-exact solution) scores probe **0.21** while junk scores **0.0** — rank-inverted. No probe gate can select the answer. This is why every streaming-probe run "certified 159, all missed". The probe must be dropped as a gate; certify directly.
  2. **A sound tail-reachability gate** (`Sweep.lineTailReachable`) prunes hard AND keeps the human line (validated). It replaced the useless probe as the pre-certify filter.
  3. **Certify is the wall: ~107ms avg (up to 6s), vs 1.8µs raw physics.** The cost is the certify's **seed loop** (entry-grid slice-solves finding the 0.0002-wide feasible start + air-yaws), NOT the bucket sweep. Reducing bucket radius 40->8 barely helped; budget 30->1 is a free ~2x.
- **The current bounded run**: per-cycle glide ranges `1-2,1-3,8-12` (the user's "setup short, build long" prior) cut 1.0M -> **58,831 candidates**, all containing the human line. Certify-all (probe removed) = ~1.75h sequential. **Not yet launched to completion.**
- **Two speed paths**: (a) quick wins — bucket budget=3 (free 2x) + parallelize across cores (8x) -> j154 in **~10min**, zero rewrite risk; (b) the big one — **form-based certify rewrite** (reuse ArcSweep's linear (sinθ,cosθ) forms; replace the entry-grid search with the closed-form wall-form intersection) -> **~1ms certify, ~100-800x**, which lets us drop the glide prior and certify the full 1.0M in ~2-17min. Section 5.

## 1. The objective-bounded B&B is dead (do not revive it)

Full proof in `j154-objective-bnb-nogo-2026-08-10.md`. Summary: the objective (landing X) is real-arithmetic, floored by the shared landing box (`X>=-1600.137`, BELOW the human optimum -1599.700 so every node LB prunes nothing), and realized in the free-yaw tail. Measured: wall-free bound frac@half 0.34, wall-aware 0.30. It neither prunes nor guides. `ColdBound` (the LP lower-bound primitive) + `ColdBoundProbeScreen`/`ColdBoundWallScreen`/`ColdEntryRegionScreen` remain as diagnostics but are not on the solve path.

## 2. The working pipeline: per-cycle family brute-forcer (`ColdCycleBeamScreen`)

The momentum splits into **press-cycles** (j154: [0-2],[3-15],[16-28], from presses 2/15/28). Each cycle is enumerated as `coast x (L-1-j) -> glide x j -> press`, drawing combos from a restricted, user-configurable alphabet. Cross-product the cycles; filter; certify.

**The generalizing strat structure (from decoding the 4 known lines):** every long momentum run is a **diagonal** `{WA,WD,SA,SD}`; the launch is always `W+`; each jump uses 1-2 specific diagonals. **Mirror symmetry**: solves come in handedness pairs `{SA,WD}` (j1150,j716) and `{SD,WA}` (j154). See the search-space inspector artifact (built this session).

**Pipeline stages, per candidate:**
1. **Momentum feasibility** (`traceLineTo` width >= -slack at some facing) — loose boxes -> ~73% pass.
2. **Tail reachability** (`Sweep.lineTailReachable`, section 3) — SOUND interval reachability. This is the real filter.
3. **Streaming certify** — the probe is BROKEN (section 4), so run with gate=999 to certify ALL survivors in beam order, stop at first byte-exact solve.

**j154 config that yields 58,831 candidates (all contain the human line, verified):**
```
PKC_COLD_BEAM_FILE=<j154.json>
PKC_COLD_BEAM_GLIDES=SD,S,WA   PKC_COLD_BEAM_COASTS=A,SD   PKC_COLD_BEAM_PRESSES=SD,WA,W   PKC_COLD_BEAM_ENGAGES=W,WA
PKC_COLD_BEAM_GLIDE=13   PKC_COLD_BEAM_GLIDE_RANGES=1-2,1-3,8-12
PKC_COLD_BEAM_CAP=5000000   PKC_COLD_BEAM_CERTCAP=100000
PKC_COLD_BEAM_PROBE_GATE=999   (certify all; probe is only computed for logging)
PKC_COLD_BEAM_PROBE_STEP=4.0   PKC_COLD_BEAM_FSTEP=1.0
PKC_COLD_BEAM_BUDGET_MS=9000000   PKC_COLD_BEAM_LOG=<log>
```
Human line = `4.8.8.8.8.8.8.8.8.8.8.8.8.8.6.2+4.2+2+2+2+2+2+2+2+2+2+2+1+` (cycle0 A/SD/SD j=1, cycle1 SD*11/S/WA j=1, cycle2 A/WA*11/W j=11). It threads, passes the tail gate, and `benchSig` certifies it (j=11 in 87ms).

## 3. The tail-reachability gate (sound, validated) — `Sweep.lineTailReachable`

Forward interval reachability over the tail (ticks last..nT-1) from the momentum exit. **Critical: propagate from the feasible start RECT, not a point** — the exit posX interval is `[txLo+dx, txHi+dx]` from `traceLine`; using a single start point false-prunes the human line (this bug was caught by validating on the human line FIRST, per the user's insistence — do this for any new gate). Each tick: widen velocity by +-a (a = 0.98*airAccel, +0.2 boost at a grounded press), move, apply friction, intersect X/Z with each tail wall; return false only if an interval empties. Treats X/Z accel independently => over-approximates => a feasible line is NEVER pruned. Cuts j154 1.0M -> ~530k (forward-only). The two-sided version (add a backward pass from the landing box, intersect) would tighten further but is unbuilt.

`Sweep.lineTailMargin` (the earlier crude omnidirectional max-distance version) is superseded by `lineTailReachable` in the beam's `feasWithTail`.

## 4. The probe is broken for j154 (the key diagnostic) — `ColdHumanDiagScreen`

`ColdHumanDiagScreen` (PKC_COLD_DIAG_FILE + PKC_COLD_DIAG_SIG) runs the human line through every stage. Result for j154:
- STAGE1 survivor filter (momentum + lineTailReachable): **PASS** (it is in the set).
- STAGE2 probe: **fine(0.5deg)=0.21, coarse(1deg)=0.66** — vs junk ~0.0. **Rank-inverted.**
- STAGE3 certify: **SOLVED** byte-exact both paths (benchSig + certifyLine, viol 0).
- STAGE4 two-sided tail gate: **PASSES** (after the start-rect fix).
- STAGE5 certify-cost sample (cycle-2 glide sweep j=2..12): avg 107ms, 12-452ms, j=11 SOLVED 87ms.

Why the probe fails: the probe's QUICK slice solve does not find the knife-edge tail yaws (gives 0.21); only the thorough certify (bucket sweep + entry grid) finds viol 0. So the probe cannot screen j154. **Any cheap screen built on the quick slice solve will fail the same way.** The only reliable test is the full certify.

## 5. Certify is the wall — benchmark + the two speed paths (`ColdBenchScreen`)

`ColdBenchScreen` (PKC_COLD_BENCH_FILE + PKC_COLD_BENCH_SIG) measured on j154:
```
RAW verify (forward+constraint, fixed inputs+yaws) = 1.8 us
SPEC build (LineSpec.build -> new AngleSolverEngine)= 20 us   (per candidate, ~11,000x the physics)
SCREEN (probe) = ~5 ms
CERTIFY LAND  = ~80 ms
CERTIFY MISS  = ~107-407 ms avg, MAX ~6000 ms
```
Radius x budget sweep (bucket sweep tuning): reducing `BUCKET_SWEEP_RADIUS` 40->8 barely moved the miss (177->172ms); radius<8 breaks the land (feasible bucket sits at +-5..8). `BUCKET_SLICE_BUDGET` 30->1 is a free ~2x (363->177ms) and the land still solves at budget=1. **So the ~172ms floor is the certify SEED LOOP** (`certifyDirect`: `DIRECT_FULL_SEEDS` entry-grid `sliceSolve`s over facing windows), not the bucket sweep. `BUCKET_SWEEP_RADIUS/SLICE_BUDGET/LP_MAX` are now non-final `static` in `ColdSearch` (tunable; defaults unchanged at 40/30/5e-2, pins rely on the defaults).

**Path A — quick wins (no rewrite, solve j154 tonight):**
- Set `BUCKET_SLICE_BUDGET=3` for the run (free ~2x; add `PKC_COLD_BEAM_BUCKET_BUDGET` env to `ColdCycleBeamScreen`, set `ColdSearch.BUCKET_SLICE_BUDGET` before streaming). Keep the default 30 elsewhere so pins are unaffected.
- **Parallelize the streaming certify** across cores (each thread its own `Sweep[]` scan since `Sweep.nodes` is mutable; `p.model` is stateless; `lastDirectDebug` is a benign static race). 8x.
- Combined ~16x: 58,831 candidates from ~1.75h -> **~7-10min**.

**Path B — the form-based certify rewrite (the real unlock, ~1ms, ~100-800x):**
- The 107ms is two SEARCHES that become closed-form on ArcSweep's linear forms:
  1. Per-bucket `AngleSolverEngine`/spec rebuild + generic LP -> replaced by `evalRange` on the carried `A*sin+B*cos` forms (ArcSweep.Form). No rebuild.
  2. The **entry grid** (3x3->9x9 `FreeStartSolve` search for the 0.0002-wide feasible start) -> replaced by the **closed-form intersection of the wall forms** (`lowerX`/`upperX`), which ArcSweep already computes. This removes the dominant term. (Prior notes called the entry grid "inherent" only because a lean one-shot FreeStartSolve stepped over the sliver; the FORM intersection is exact, not a search.)
- Estimated ~1ms/certify (0.3-3ms; physics floor 1.8us so huge headroom). Then 58,831 -> ~1min, and **the full 1.0M tail-gate set -> ~17min (2min parallel), so the glide prior can be dropped** and the solver becomes a true mass brute-forcer. This is a few-hours build + re-validate all 7 pins byte-exact.

## 6. All files built / changed this session

Main (`core/.../anglesolver/coldsearch/`):
- `ColdBound.java` (NEW): LP lower-bound primitive + gains + `formMin` + `lineTailReachable`-style helpers. Diagnostic only (objective-B&B dead).
- `ColdSearch.java`: added `Sweep.lineTailMargin` + `Sweep.lineTailReachable` (the sound tail gate); made `BUCKET_SWEEP_RADIUS/SLICE_BUDGET/LP_MAX` non-final static (tunable).

Test (`core/src/test/.../coldsearch/`):
- `ColdCycleBeamScreen.java`: env-configurable alphabets (`PKC_COLD_BEAM_COASTS/GLIDES/PRESSES/ENGAGES`), per-cycle glide ranges (`PKC_COLD_BEAM_GLIDE_RANGES`), tail gate in `feasWithTail`, streaming probe->certify with gate + coarse probe scan (`PKC_COLD_BEAM_PROBE_GATE/STEP`), file logging (`PKC_COLD_BEAM_LOG`), tailCut reporting.
- `ColdHumanDiagScreen.java` (NEW): human-line-through-pipeline diagnostic (the probe-broken proof) + two-sided tail gate validation + certify-cost sample. **Run this on any capture before trusting the search.**
- `ColdBenchScreen.java`: added the radius x budget sweep.
- `ColdBoundProbeScreen.java`, `ColdBoundWallScreen.java`, `ColdEntryRegionScreen.java` (NEW): objective-bound + entry-region diagnostics.

Docs: `j154-objective-bnb-nogo-2026-08-10.md`, `modular-cycle-bruteforcer-vision.md`, this file. Artifact: j154 search-space inspector (https://claude.ai/code/artifact/1963ea87-9411-4315-a1e4-010bbbdbb192).

## 7. Recommended next steps (in order)

1. **Solve j154 now via Path A**: add `PKC_COLD_BEAM_BUCKET_BUDGET` + parallel certify to `ColdCycleBeamScreen`, launch the section-2 config, monitor for `SOLVED`. Expected ~10min. Pin the found sig in `ColdSearchRegressionTest`.
2. **Build Path B** (form-based certify) — the durable unlock. Re-validate all 7 pins byte-exact.
3. **Generalize**: run `ColdHumanDiagScreen` then the brute-forcer on j716 (and more hpk_human captures) with their own diagonal handedness (j716 = SA/WD). Drop the glide prior once certify is ~1ms.
4. **Two-sided tail gate** (backward pass from landing) if the tail filter needs to be tighter.
5. **The modular UI** (per-cycle config, 3D cycle picker) per `modular-cycle-bruteforcer-vision.md`.

## 8. Operating rules (unchanged)

Cold inputs only (human rows/yaws/debug/result NEVER feed the solver; the user-provided per-cycle alphabet/family restriction IS a sanctioned general prior, cf. the grammar-prior ruling; validation oracles are fine). Byte-exact through `ExactJumpModel` is the only judge. Validate any new prune on the human line FIRST (the tail-gate start-rect bug is why). Never git commit/push/branch (ask, ready message, no attribution). Long runs: detached cmd scripts in `C:\Users\benja\AppData\Local\Temp\claude\coldlogs\` with the FULL gradlew path (cmd does NOT search cwd for gradlew.bat), done-markers, Monitor tails, verify the run started; PKC_* env in the same command as gradle plus `--rerun`; `-PtestHeap=3g`. Pins: `.\gradlew --configure-on-demand :core:test --tests "*ColdSearchRegressionTest" "-PslowTests" "-PtestHeap=3g" --rerun`.
