# Difficulty metric handoff (issue #237)

State as of 2026-08-06 (sixth session): combinedV4 is the tuned metric (0.957 / 0 inversions), adding a smoothness dimension measured on the smoothest FEASIBLE yaw line (see the sixth-session section at the bottom). Earlier state summary: as of 2026-07-28 (fourth session). Sessions covered: 2026-07-27 v1 metric; 2026-07-27 tick-shift windows + v2; 2026-07-27/28 simplification track (45 pairs + SimplifyLoop), ground-support guard, and the user's in-game review that STOPPED with the simplify outputs WITHDRAWN; 2026-07-28 fourth session: the review's rebuild plan items 1-3 plus the batched zero-demand fix are IMPLEMENTED (see "Rebuild session" section), combinedV3 is the tuned metric (0.954 / 0 inversions). Remaining before any new artifact handover: the loader-side real-sim verification gate (rebuild item 4) and full regeneration (item 5). Simplify outputs remain WITHDRAWN until then.

Gates at final state (2026-07-28 fourth session): full `:core:test` green incl. the new sprint/free-flag invariants; metric screen 32 measured, 2 skipped (j154, j030, both need user re-saves); ladder under the honest measurement: combinedV3 0.954 / 0 inversions, combinedV2 0.947 / 1, combinedV1 0.942 / 1.

## Working state (uncommitted files, all sessions)

Uncommitted (user handles all git; nothing was committed):

- `metriclab/MeasurementEngine.java`: input-edge tick-shift windows implemented (semantics below), plus a BadSample guard for enabled constraints outside the solve segment (catches the j030 defect).
- `metriclab/JumpMeasurements.java`: per-edge shift arrays + `shiftMinMomentumTicks`, `shiftGeoMomentumTicks`, `shiftMinJumpTicks`, `shiftGeoJumpTicks` (in measurements.csv) + `shiftMs`.
- `metriclab/Metrics.java`: seven metrics registered in `all()`; `combinedV2` is the shipped v2 and the screen's dHat calibration metric.
- `metriclab/MeasurementInvariantsTest.java`: extended (shift windows in [0,5], edge-count consistency with the input-edge counts, d1 momentum-shift leniency floor, shift determinism); the zero-shift feasibility check is enforced inside `measure()` itself (throws if the copy machinery is not an identity at shift 0).
- `HpkMetricScreen.java`: shMin/shGeo table columns, `input-shift-windows.csv` long-form dump, dHat calibration switched to `combinedV2`.
- Third session additions: `HpkPair45Screen.java`, `metriclab/Variant45.java`, `metriclab/HeadlessSolve.java`, `metriclab/SimplifyLoop.java`, `HpkSimplifyScreen.java` (see Simplification track section).
- Fourth session (rebuild): `MeasurementEngine` (sprint flag-chronology probes, jumpTicks cooldown gates, free flags, tap-shrink attribution, eff aggregates), `JumpMeasurements` (free arrays + eff fields, CSV columns), `Metrics` (combinedV3), `SimplifyLoop` (operator gating, keepify sample rewrite, baseline-name candidate measurement), `MeasurementInvariantsTest` (j001/j012 pins, free-flag determinism), all three screens on combinedV3.
- `TESTS.md` metric-lab entries updated; this doc.

Suggested fourth commit for the rebuild session:

```
fix(anglesolver): rebuild simplify measurement honesty per in-game review

Sprint timing probes via the recorded sprint flag chronology (engagement
ground-gated, j012 reads frame-exact, sprint releases read free); held-JUMP
probes respect the 10-tick jumpTicks cooldown; per-side free flags with
tap-shrink deletion attributed to the press; combinedV3 zeroes any edge
with a free side (phase-blind, no geo term, 0.954 Spearman / 0 inversions,
beats v2 on center, leave-one-out and perturbation on the same protocol).
SimplifyLoop: key operators gated on KEEP inputs, SPRINT untouchable,
holdJump needs 10-tick fire spacing, keepify rewrites debug samples from
the rows instead of stripping them, candidates measured under the baseline
name so jitter seeds cannot be harvested as fake gains.

Simplify outputs remain withdrawn until the real-sim SimulatorEntity
verification gate lands and everything is regenerated (issue #237 rebuild
plan items 4-5).
```

Suggested third commit for the simplify loop:

```
feat(anglesolver): greedy strat simplification loop over the 45 pairs

SimplifyLoop + HpkSimplifyScreen (PKC_SIMPLIFY=1): from the Force-45
solve (human save where 45 is unsolvable), greedily apply keepify,
jump-hold conversion, shift-window edge recentering, tap deletion and
dF=0 no-turn chains; a step is accepted only if the candidate replays
feasible against the full constraint set plus the accumulated
ground-support requirements of all accepted steps (or cold FAST
re-solves for constraint-adding operators) and combinedV2 drops; the
final strat must cold re-solve.

KNOWN DEFECTIVE per the 2026-07-28 in-game review (outputs withdrawn,
committing the harness as groundwork only): key edits under Force-45
are model-invisible, sprint-key walks corrupt the derive chronology,
keepify strips the live-tool sim context, and no real-sim verification
gate exists yet. See the handoff doc's review verdict and rebuild plan
before trusting or regenerating any output.
```

Suggested second commit for the pair work:

```
feat(anglesolver): paired Force-45 variant generation for the simplify track

HpkPair45Screen (PKC_PAIR45=1): per hpk_human capture, strip the F/dF
reconstruction pins, switch inputs to Force 45, cold-solve headless,
measure and score with combinedV2, and emit human-vs-45 pairs plus the
solved variants as loadable saves under build/hpk-metric/pairs45/.
28 of 34 solve; the 6 misses all need run-up speed control Force 45
cannot express. Positive human-minus-45 deltas flag j123/j716/j121
(and mildly j140/j135/j081/j073) as tighter-than-human
reconstructions, the triage list for re-reconstruction.
```

Gates at session end: full `:core:test` green; screen run green, 32 measured, 2 skipped (d12 j154 and d4 j030, both defective saves). Suggested commit message:

```
feat(anglesolver): input-edge tick-shift windows + v2 difficulty metric

MeasurementEngine measures per input edge how many ticks earlier/later
the key change can happen and still land (rows mutated on a JSON
round-trip copy, spec rebuilt through the stock buildSpec path, movement
samples synced, JUMP arcs shifted with the fire tick, recorded yaws held
fixed, Mode.F-filtered oracle, +-5 tick cap). Ticks a probe flips from
air to ground must be supported: their position must sit inside the
block checkpoint interval the reconstruction places one tick before
each jump (or the landing constraint), else the probe is infeasible.
New JumpMeasurements fields, both CSVs extended plus new
input-shift-windows.csv; invariants extended, never weakened.

combinedV2 replaces the inputEdgesMomentum proxy with log-scale timing
demand over the shift windows: 0.944 Spearman, 0 median inversions on
the 32 measured captures (combinedV1: 0.942/1); leave-one-out and
+-33 percent perturbation both beat v1 on the same protocol. Screen dHat
calibration switched to combinedV2; all 11 d-levels now separate.

j030 found defective (landing constraint at tick 37 outside landingTick
36, silently dropped on replay, so its 5.4375 line was never enforced):
now SKIPPED via an out-of-segment constraint guard; re-save needed
alongside the d12.
```

## Goal

A metric that rates how difficult a strat is for a human, scored as inputs x turn across momentum and jump phases, with tolerance windows as the measurable core. Full spec: issue #237. End use: the solver pipeline (find any solve, push to no-turn, simplify inputs, maximize turn leniency).

## Dataset

`core/src/test/resources/captures/hpk_human/d<level>/`: HPK jumps with human-reconstructed strats, 3 per d1-d11 plus 1 d12. Reconstruction protocol: HPK start position pinned, human key inputs entered and pinned via Inputs KEEP, dF=0 chains where the human plays no-turn, solver fills the yaws. The d-level is read from the folder name.

Status: 32 of 34 measure cleanly, 2 skipped as defective saves:

- `d12/j154_1bm_Head_Butterfly_Neo.json`: saved with a FAILED solve in its result block (success=false, met 35/41); the jump does solve, the save was a mistake. DEFERRED by user decision (2026-07-27). Until re-saved, d11 anchors the tail of the calibration.
- `d4/j030_6bm_5.4375-1.json` (found 2026-07-27, second session): its landing constraint (Z >= 431.1375, the 5.4375 line) sits at tick 37 while landingTick is 36, and `collectUiConstraints` silently drops constraints outside `[startTick, landingTick]`. The recorded solve therefore never enforced the jump's defining distance (recorded endZ 430.851, 0.287 short of 431.1375; its minMargin 1e-5 was a tick-23 mid-block lip graze, not the landing). Discovered because shift probes that delete the takeoff entirely still "landed". A new MeasurementEngine guard skips any capture with an enabled constraint outside the solve segment. Fix in-tool: move the landing constraint to tick 36 (or landingTick to 37), re-solve, re-save.

When both are re-saved the screen must show 34 measured, 0 skipped.

Optional per-capture sidecars `<name>.meta.json` (fields: subTier, jumpClass, rung, notes) are supported by the loader but not yet created. Sub-tiers (+/=/-) are not yet recorded anywhere.

## Harness

Test-only, under `core/src/test/java/.../anglesolver/`:

- `metriclab/MeasurementEngine`: expensive, trusted measurement. Per capture: verifies the recorded solve replays byte-exact feasible (full constraint set), centers the yaw anchor, then measures per-tick yaw windows, uniform jitter radius, min landing margin, input edges (momentum/jump split at the last jump tick), turn ticks, Jump Angle, and input-edge tick-shift windows (semantics section below). Structure counts come from the RECORDED yaws; yaw tolerance from the CENTERED anchor; shift windows from the RECORDED yaws (held fixed).
- `metriclab/Metrics` + `ScoringMetric`: cheap scoring formulas over `JumpMeasurements`. This is the tuning surface; add a class, re-run the screen.
- `HpkMetricScreen` (`PKC_METRIC=1`, e.g. `$env:PKC_METRIC='1'; ./gradlew :core:test --tests "*.HpkMetricScreen"`): per-capture table, per-metric Spearman + per-d medians + inversion count, outputs `core/build/hpk-metric/report.txt`, `measurements.csv`, `yaw-windows.csv` (long form, per tick), `input-shift-windows.csv` (long form, per edge: row, phase, flipped keys, lo/hi ticks, censored flags). The CSVs support offline tuning in a spreadsheet or Python without re-measuring. The table shows `d` and `dHat` side by side: `dHat` is the combinedV2 score snapped to the nearest per-level median after pooled-adjacent-violators monotonization; under combinedV2 no adjacent levels invert, so all 11 centers are separate (v1 pooled d3-4). In-sample and relative to this set, a diagnostic, not an absolute rating. The `shMin`/`shGeo` columns are the momentum/jump shift aggregates.
- `metriclab/MeasurementInvariantsTest`: always-on in `:core:test`; do not weaken it while tuning. Now also pins: shift windows within [0,5], shift edge enumeration matches the input-edge counts, a d1 jump keeps `shiftGeoMomentumTicks > 1`, shift arrays deterministic. The zero-shift probe (full copy machinery at shift 0) is asserted feasible inside `measure()` itself.
- Captures with recording problems (success=false, missing result.yaws, enabled constraints outside the solve segment) are SKIPPED and listed, not fatal; any other failure fails the run.

## Traps already hit (do not rediscover)

1. Recorded solves are goal=MAX, so they graze the far constraint with ~1e-5 margin. Windows measured around the raw solver point are ~0 and carry no signal. Fixed: Gauss-Seidel centering (per-tick window midpoint, each move stays feasible) before measuring.
2. The dF=0/F-pin reconstruction constraints are saved in the captures; against the full constraint set any yaw perturbation is "infeasible". Fixed: the measurement oracle filters out `Mode.F` constraints (landing predicate only); the baseline replay still checks everything.
3. `result.yaws` holds solver-space wrapped-abs yaws at ticks `startTick + k + 1`; replay via `sc.toGameFacings(Angles.wrapAll(vec))`. `maxViolation` clamps at 0, so margins come from `JumpConstraintCompiler.evaluate` per ineq constraint.
4. `buildPhys` samples per-tick moveForward/moveStrafe/sprinting from the recorded TickStates (`boxes.getState(t + 1)`) whenever a movement sample exists, and ground/air from the per-tick slipperiness overrides; the rows only drive the jump mask. A row mutation alone is therefore INVISIBLE to the spec for WASD edges and inconsistent for JUMP edges. Shift probes must sync the debug movement samples on mutated rows and shift the slip-override arc together with a moved jump fire tick, all on a copied SaveFile fed through the stock buildSpec path. Never patch the schedule arrays in place.
5. `collectUiConstraints` silently drops constraints outside `[startTick, landingTick]` (`segTick > numTicks`). A capture can therefore carry a landing constraint the replay never sees (j030). The MeasurementEngine guard turns that into a SKIP; without it the landing oracle is toothless and every probe "lands".

## First results (33 captures)

- `toleranceOnly` (jitter + geo window): Spearman 0.824 vs d, 2 median inversions. Separates d8-d11 cleanly.
- `countsOnly` (input edges + turn ticks): Spearman 0.835 but 5 inversions and it saturates above d7.
- Jitter radius spans 17.7 deg (j001, d1) down to 0.002 deg (d9-d11 neos).
- Instructive residuals: `j030_6bm_5.4375-1` (d4) has winMin 49.6 deg and jitter 12.6, top-tier lenient on turn, because its difficulty is the momentum inputs and timing, not aim. `j081_6bm_7-5` (d7) similar shape. These are exactly the jumps a turn-only metric misrates; the input-side signal has to carry them.

## v1 combined metric (2026-07-27)

`combinedV1` in `metriclab/Metrics.java`: Spearman 0.942 vs d-level, 1 median inversion on the 33 captures. Both strawmen beaten (targets were rho > 0.85 and 2 or fewer inversions).

```
score = -log10(max(jitterDeg, 1e-4)) - 0.5 * log10(max(winGeoDeg, 1e-4))
      + 0.3  * inputEdgesMomentum
      + 0.06 * turnTicksJump
      + 1.5  * [jumpAngle]
      + 1.5  * [minMargin < 5e-5]
```

Rationale per term:

- Tolerance core, identical to `toleranceOnly`: total aim precision demand. `jitterDeg` is the all-ticks-simultaneously radius, `winGeoDeg` the average per-tick window.
- `inputEdgesMomentum` (0.3 per edge): timed key changes in the setup carry the run schedule; their timing windows are unmeasured (tick-shift windows, deferred step), so the count is the proxy. Jump-phase edges carry no signal (1-3 everywhere, mostly one release); weighting them only adds noise, which is why phase-blind `tolPlusCounts` (0.906, 4 inversions) loses to phase-aware `tolMomEdges` (0.923, 2).
- `turnTicksJump` (0.06 per tick): sustained aim control while airborne.
- `jumpAngle` flag (+1.5): a turn on the exact takeoff tick is tick-timing the yaw windows cannot see; mistiming by one tick fails. Carries j066 (d6, aim-lenient carpet 4.5) almost alone.
- Distance-limit flag (+1.5 when centered `minMargin` < 5e-5): the strat grazes its positional limit even after centering, so movement and timing must be near perfect even where yaw is free. This is the j030/j012 signal the handoff predicted the input side must carry. On this set any threshold in (4.7e-5, 8.9e-5] flags the same jumps: it must catch j066 (4.5e-5) and j1150 (4.7e-5) and must not catch j038 (8.9e-5, d3). Recalibrate the threshold when the dataset grows.

Robustness: leave-one-out rho stays in 0.937..0.953 (max 2 inversions); perturbing all four coefficients by +-33 percent (81 cells) never drops rho below 0.915. No coefficient is load-bearing to the third decimal.

Full comparison, all registered in `Metrics.all()` so every screen run reprints the ladder:

| metric | spearman | inversions | note |
| --- | --- | --- | --- |
| toleranceOnly | 0.824 | 2 | strawman: jitter + geo window |
| countsOnly | 0.835 | 5 | strawman: edges + turns, saturates above d7 |
| tightTicks | 0.892 | 2 | tolerance + count of ticks with window < 0.25 deg |
| tolPlusCounts | 0.906 | 4 | tolerance + phase-blind counts + jump angle |
| tolMomEdges | 0.923 | 2 | combinedV1 minus the distance-limit flag |
| combinedV1 | 0.942 | 1 | winner |

Remaining misranks (combinedV1):

- The one median inversion is d3 to d4 (medians 1.15 vs 0.40). Pin: j032 (d4) scores mid-d3 while j158/j038 (d3) sit above it on momentum edges; the two levels interleave in every measured dimension and no inversion-free cell exists in any swept family. Separating them needs the input-edge tick-shift windows (deferred step below), not another reweighting.
- j123_3bm_Winged_Triple_Neo_to_Egg (d8) and j121_3jmm_Double_Winged_Neo__0.5 (d9) score in the d11 band: their reconstructions measure d11-tight (jitter <= 0.002, winMin <= 0.01). Either the strats truly are that tight or the reconstruction found a tighter line than the human plays; re-check these captures before trusting the tail.
- j073_1jmm_Pane_to_Pane_Short_Pane_Neo (d6) measures d9-tight (jitter 0.013), same suspicion.
- j018_X_3x3_4bm_Triple_Neo (d2) over-scores on its 5 momentum edges (score rank 12 of 33).

## Tick-shift windows: implemented semantics (2026-07-27, second session)

Per input edge (a keyset change between consecutive rows, same enumeration and momentum/jump split as `countInputEdges`): how many ticks earlier (`lo`) and later (`hi`) the key change can happen, everything else fixed, and still land. Probed outward 1..5 per side, stopping at the first failure; cap 5. A probe:

1. Deep-copies the SaveFile via a Gson JSON round-trip (one serialize per capture, one parse per probe).
2. Moves the flipped keys' change to row `t + s`: for `s > 0` rows `[t, t+s)` get the pre-edge state, for `s < 0` rows `[t+s, t)` get the post-edge state. `t + s` may go down to 0 (change at-or-before the first row) and up to the last counted row; beyond that the side stops WITHOUT counting a failure and the per-edge `loCensored`/`hiCensored` flag is set in the CSV (aggregates keep the achieved value, conservative).
3. Syncs the recorded movement samples on mutated rows (trap 4): moveForward/moveStrafe re-derived from the mutated keys at the 0.98 scale (0.3 sneak factor; exact for this no-sneak dataset), sprinting = the neighboring regime's recorded sprint gated by `W && !S` (the regime being extended: tick `t-1` for later shifts, tick `t` for earlier ones).
4. For a JUMP press edge, shifts the air arc with the fire tick. Fire = press tick when grounded. Physical rationale: air time from takeoff height to landing height is Y-only physics, invariant under takeoff timing, so the contiguous AIR window shifts rigidly by the fire-tick delta (slip overrides mutated on the copy; new ground ticks take the adjacent surface's slip). Guards: every tick flipped to AIR must currently be ground and vice versa, else the probe is infeasible (arcs merging = chain broken). Two deliberate special cases: a press shifted EARLIER into the previous arc's air is buffered (fire tick unchanged, no schedule change) and lands, which is exactly the hold-space auto-jump leniency; a press shifted PAST its release (1-tick taps) deletes the jump, keeps the original arc without the impulse, and lets the oracle judge (reads as frame-perfect on that side).
5. Rebuilds the spec through the stock buildSpec path (`Fixtures.buildBoxes` mandatory) and evaluates the RECORDED yaws against the Mode.F-filtered oracle. Spec build failure = infeasible. Zero-shift must replay feasible (enforced in `measure()`).

Aggregation into `JumpMeasurements`: per-edge width `w = lo + hi`; per phase, `shiftMin*Ticks = min(w)` and `shiftGeo*Ticks = expm1(mean(log1p(w)))` (the geometric mean of `1 + w`, minus 1: 0 = all frame-perfect, 10 = all free). Per-edge rows go to `input-shift-windows.csv`.

Cost: measured before optimizing, and no optimization was needed. Probes are ~1-3 ms each (JSON parse + spec rebuild + one forward), at most edges x 10 + 1 per capture; the whole 32-capture screen measures in a few seconds (per-capture totals 6-250 ms, `ms` column in the table; `JumpMeasurements.shiftMs` carries the shift share).

Known artifacts, stated so nobody re-derives them:

- From-rest first press: the first key event of a capture with no prior anchor (j264, j018, j108, j073 takeoff presses) measures (0,0) because shifting it is really a time-translation of the whole strat while the yaws stay pinned to absolute ticks. An anchored-only exclusion variant was tested offline and NOT adopted: the "first edge is a +JUMP press" criterion catches 9 captures including genuine run-up approaches, and the metric does not improve.
- Tap shrink: shifting one edge of a 1-tick tap deletes the tap; the real leniency of a held key shows on the press-earlier side only (buffered), so chained mid-jump presses read (5,0), which is the correct human asymmetry.
- Ground-support guard (added 2026-07-28 after user review): every tick a probe flips from air to ground must have its probe-trajectory position inside a block support region, else the probe is infeasible. The support regions come from the reconstruction protocol itself: every jump carries an absolute X/Z IN checkpoint one tick before its fire tick (the takeoff block expanded by the 0.3 player half-width, so "inside the interval" IS "footprint overlaps the block"), and the landing tick carries the same for the landing block. Takeoff-side extensions borrow the checkpoint at fire-1/fire (latest); landing-side extensions borrow the next checkpoint at/after the arc end, or the landing constraint for final arcs (earliest); no checkpoint found = infeasible. Original grounded ticks are grandfathered (the recording proves them). `selectedBlocks` boxes are NOT needed (only 8 of 34 captures carry them). Example of what it catches: j001's jump-1-early probe passed the tick-16 landing constraint (z 91.85) while its claimed landing tick stood at z 91.57, footprint 0.13 short of the block front at 92.0, a phantom landing tick on air; press lo is now correctly 0. The guard changed nothing in the metric ladder (combinedV2 still 0.944, 0 inversions), so no re-tune.
- Residual, second order only: a probe's schedule inherits previously accepted extensions as baseline when the simplify loop measures an already-mutated save, so measured windows on such saves treat verified extensions as ground truth (they were support-verified at acceptance, and the loop re-checks the ACCUMULATED support requirements of all accepted steps on every later candidate's trajectory).

## v2 combined metric (2026-07-27, second session)

`combinedV2` in `metriclab/Metrics.java`: Spearman 0.944 vs d-level, 0 median inversions on the 32 measured captures. Beats combinedV1 (0.942, 1 inversion) where it matters: no inversion-free cell existed in ANY v1-era family, and v2 has a whole inversion-free region.

```
score = -log10(max(jitterDeg, 1e-4)) - 0.5 * log10(max(winGeoDeg, 1e-4))
      + 0.5 * sum over momentum edges of log10(11 / (1 + lo_e + hi_e))
      + 0.5 * log10(11 / (1 + shiftGeoMomentumTicks))
      + 0.06 * turnTicksJump
      + 1.5  * [jumpAngle]
      + 1.5  * [minMargin < 5e-5]
```

Rationale per term (v1 terms carry over unchanged, their rationales stand):

- The momentum input-edge count proxy is REPLACED by log-scale timing demand over the measured shift windows, the input-side analog of the yaw tolerance core. `log10(11/(1+w))` is 0 for a fully free edge (w = 10) and ~1.04 for a frame-perfect one (w = 0), so the sum is a demand-weighted edge count: wide edges no longer cost anything, tight edges cost up to ~one frame-perfect unit each. This is what fixes j018 (its chained presses are wide, so 5 edges no longer read as 5 x 0.3).
- The geo term prices overall momentum timing tightness independent of edge count: j032's two frame-perfect edges (geo demand 1.04) now outweigh j158/j038's one-tight-of-many (0.4-0.45), which is precisely the d3/d4 pin. Deletion tests: removing the geo term costs the inversion (0.942/1), removing the sum term collapses to 0.902/3, removing turnJ/JA/flag costs 2/1/3 inversions.

Robustness (same protocols applied to v1 for a fair comparison):

| | center | leave-one-out worst | +-33 pct on all 4 coefficients, 81 cells, worst |
| --- | --- | --- | --- |
| combinedV2 | 0.944 / 0 inv | 0.939 / 1 inv | 0.927 / 3 inv |
| combinedV1 | 0.942 / 1 inv | 0.937 / 2 inv | 0.917 / 3 inv |

The 0.002 center rho difference is noise at n=32; the inversion count and the robustness margins are the claim. Losing families this round, do not re-try blind: pure sum-demand without the geo term (inv=0 only at the very edge of its coefficient range, a <= 0.25, perturbs to 4 inversions), min-demand instead of sum/geo (0.91/1), raw edge count re-added (helps rho by noise amounts only), jump-phase shift demand (no effect, c=0 optimal), anchored-only edge filtering (see artifacts above).

Full ladder on the 32 (all registered in `Metrics.all()`):

| metric | spearman | inversions | note |
| --- | --- | --- | --- |
| toleranceOnly | 0.831 | 2 | strawman: jitter + geo window |
| countsOnly | 0.830 | 4 | strawman: edges + turns |
| tightTicks | 0.901 | 2 | tolerance + tight-tick count |
| tolPlusCounts | 0.913 | 4 | tolerance + phase-blind counts + jump angle |
| tolMomEdges | 0.927 | 2 | v1 minus the distance-limit flag |
| combinedV1 | 0.942 | 1 | prior winner (d3/d4 inversion) |
| combinedV2 | 0.944 | 0 | winner; shift-window demand replaces the edge-count proxy |

Remaining misranks (combinedV2):

- j018 (d2) rank 9 of 32 (was 11 under v1 on this set): improved but still top-of-d3 band; what remains is its tolerance core plus the from-rest artifact demand on its first press.
- j123 (d8) rank 31 and j121 (d9) rank 30: unchanged, still measure d11-tight in YAW (jitter <= 0.002); this is the suspected too-tight reconstruction issue (next steps), not an input-timing question.
- j073 (d6) rank 20, still d8-band for the same reason.
- j925 (d11) rank 26, up from the v1 under-score: now above every d9 except the suspect j121; its tight momentum shift windows (geo 0.32) carried it as predicted.
- j030 vs j018 separation: moot until j030 is re-saved (defective, skipped).

## Offline tuning recipe (how v1 and v2 were tuned; use this loop, not repeated screen runs)

1. Run the screen once to refresh `core/build/hpk-metric/measurements.csv` (plus `yaw-windows.csv` and `input-shift-windows.csv` for per-tick/per-edge work).
2. Replicate the screen's exact evaluation in a scratch Python script: tie-averaged-rank Spearman plus per-d medians plus adjacent-median inversion count. Copy the math from `HpkMetricScreen`; before trusting anything, verify parity by reproducing the CURRENT report's full ladder to three decimals (on the 32-capture set: toleranceOnly 0.831/2, countsOnly 0.830/4, ..., combinedV2 0.944/0; the old 0.824/2 and 0.835/5 targets were the 33-capture set including the defective j030).
3. Sweep formula families, sort by (inversions, rho). Before adopting a winner, check leave-one-out rho and a +-33 percent coefficient perturbation grid, and run the same protocols on the incumbent for a fair comparison; with ~32 points a 0.02 rho difference is noise.
4. Port winners to `Metrics.java`, run the screen once; the numbers must match the offline ones exactly (they did everywhere for v1 and v2).

Families that lost, do not re-try blind: sqrt-compressed counts, margin log-excess instead of the flag, phase-blind counts, tightTicks as the core, plus the v2-round losers listed in the v2 section.

## Next steps, in order (updated end of second session 2026-07-27)

1. DONE 2026-07-27: v1 combined metric, see section above.
2. Re-solve and re-save the two defective captures (user, in-tool, minutes): the d12 j154 (success=false) and the d4 j030 (landing constraint at tick 37 outside landingTick 36, see Dataset). Screen must then show 34 measured, 0 skipped, giving the calibration a d12 anchor and restoring the d4 median to 3 samples.
3. Fill subTier (+/=/-) sidecars (user, knowledge entry, no new captures): the loader already reads them; then extend the screen calibration to per-subtier buckets. Cheapest way to firm up the medians, and it tests whether j123/j121 are really top-of-tier. rung and jumpClass later for residual attribution.
4. Re-reconstruct the suspect captures (user + agent): j073, j123, j121 measure 2-3 levels too tight in yaw; the 45-pair deltas (Simplification track section) independently confirm them and add j716 and possibly j140 to the list. If a looser human-plausible line exists, the tail residuals shrink with zero formula changes. Do this before any further tuning; do not fit coefficients against measurement artifacts. (j925 no longer needs this: the shift windows resolved its under-score.)
5. DONE 2026-07-27 second session: input-edge tick-shift windows + combinedV2, see sections above. Deferred refinements if ever needed: yaw re-centering per probe (expensive), block-box-aware ground extension (the jam press-later blindness), whole-tap shifting as a separate probe family.
6. Named-timings validation set: synthetic captures for jam, hh, pessi hh, fmm, c4.5, bwmm, loop mm; the metric must reproduce that ordering (issue acceptance criterion). Now unblocked: the input-timing dimension is measured. Note the jam caveat from the artifacts list (press-later optimism) before reading its result.
7. Wire the metric into solver pipeline stages 2-4 (push to no-turn, simplify inputs, maximize leniency), with the Force-45 paired variants (see Future below) as the data spine for the simplification search.

## Simplification track (stages 2-3): 45-pair generation DONE (2026-07-27, third session)

User decisions (2026-07-27): metric v2 is frozen for now; simplification starts from the 45 solves (pipeline-true: stage 1 finds the strong solve, the simplifier walks it toward human-playable); the simplify loop may WARM-start its candidate re-solves but the final simplified strat must re-solve COLD before it counts (the seedless-cold ruling stays intact for solver capability claims).

Phase 1 shipped: `HpkPair45Screen` (`PKC_PAIR45=1`) + `metriclab/Variant45` + `metriclab/HeadlessSolve`. Per capture: strip the F/dF reconstruction pins, clear per-tick input overrides, set Force 45, keep rows/slip/seed/goal, cold-solve with the live engine (60 s cap), attach the fresh result, measure with MeasurementEngine, score with combinedV2. Outputs: `pairs45-report.txt`, `pairs45.csv`, and the solved variants as loadable saves under `core/build/hpk-metric/pairs45/<name>.json` (regenerate any time; build output, not resources).

Results (2026-07-27):

- 28 of 34 solve as 45 variants; 26 clean human-vs-45 pairs. The d12 j154 solves at 45 (score 6.92) even though its human save is still defective, so the simplify loop has a d12 starting point already. j030 solves its degenerate problem and is correctly flagged defective on both sides.
- The 6 non-solvers cluster on run-up speed control: j158 and j066 (backwalled), j925 (sidewalled), j065 (gapped butterfly), j068, j101. Force 45 holds W+A every tick and cannot brake, so back/side wall constraints kill it. Consequence for the simplifier: momentum key shaping is an ESSENTIAL operator, and for these jumps the loop must start from the human strat (or a KEEP-mode stage-1 solve) instead of a 45 solve.
- Typical delta for honest reconstructions (d1-d6): the 45 variant scores 1.3 to 2.4 HARDER than the human strat (bot-precision aim, tighter yaw windows). This is the leniency budget the simplifier has to buy back.
- Positive deltas, meaning the "human" reconstruction scores HARDER than the bot line, are a quantitative red flag for reconstruction tightness: j123 +5.2, j716 +5.2, j121 +3.4, j140 +1.4, j135 +0.9, j081 +0.8, j073 +0.5. This independently confirms the j073/j123/j121 suspicion from the yaw side, and newly implicates j716 and possibly j140. Use this delta as the triage list for step 4 re-reconstruction.

Phase 2 shipped: `metriclab/SimplifyLoop` + `HpkSimplifyScreen` (`PKC_SIMPLIFY=1`; `PKC_SIMPLIFY_ONLY` substring and `PKC_SIMPLIFY_D` d-level filters). Greedy best-of-batch: per iteration generate operator candidates, keep a candidate only if it stays feasible, accept the best combinedV2 improvement, stop when none improves (cap 15 steps). Feasibility policy as agreed: row-operator candidates are checked by replaying the CURRENT yaws against the mutated spec's FULL compiled constraint set PLUS the ground-support requirements of the candidate and of every previously accepted step (accumulated, so later steps cannot drift the trajectory off verified extended ground); constraint-adding operators cold-solve FAST (15 s cap) and then pass the same replay+support check; the final strat must cold re-solve (60 s) to count, and its own yaws replay byte-exact by construction. Start = the pairs45 save (regenerated inline if the build dir is empty), falling back to the human save where 45 is unsolvable. Outputs: `simplify-report.txt` (with the per-step operator log), `simplify.csv`, simplified saves under `build/hpk-metric/simplified/`.

Operator set v0 and two design findings:

- `keepify`: FORCE_45 to KEEP with the debug samples STRIPPED and a cold re-solve. Stripping matters: the pairs45 saves retain the human capture's debug TickStates, and under KEEP `buildPhys` samples movement from them, so without stripping the candidate silently inherits the human's per-tick inputs and recorded sprint seeding. Stripped, the candidate is a clean rows-derived always-sprint problem. This operator carries almost the whole human-vs-45 gap (j001: 0.800 to -1.704 in one step).
- `holdJump[..]`: set JUMP held across chained presses (physics-identical when the inter-arc ground gaps are exactly the press ticks; the model fires only on grounded ticks).
- `recenter[rowN+s]`: move an input edge into its measured shift window (half-imbalance and full-imbalance candidates). Also walks releases into longer holds one window at a time.
- `deleteTap[..]`: remove a WASD press+release pair entirely.
- `noTurn[a..b]`: add dF=0 chains over the longest span of ticks with yaw window width >= 10 deg (min span 4), cold FAST re-solve (FacingPrefold keeps these on closed form).
- FINDING, do not re-try: a "comfort" operator (re-solve against landing ranges tightened toward center, transplant the interior yaws back) is a NO-OP by construction: the measurement Gauss-Seidel-centers the yaw anchor before measuring (trap 1), so where the recorded solve grazes is already invisible to the score. The human-vs-45 gap lives in the input mode and line, not in the graze; keepify is the lever.
- Known optimism carried from the shift windows: recentering a JUMP press later assumes the runway block extends (collision-free X/Z model); flagged for in-game verify of simplified strats.

Full-set benchmark (2026-07-27, chunked runs `PKC_SIMPLIFY_D=...`, reports `simplify-d*-report.txt`; NOTE Gradle treats env vars as non-inputs, re-runs need `--rerun` or a code change or the test silently no-ops):

Numbers below are from the 2026-07-28 re-run WITH the ground-support guard (every accepted step's extended ground ticks are verified against the block checkpoints, accumulated across steps):

- 33 captures processed (j030 skipped as defective), ALL 33 final strats cold-verified and ground-verified.
- 24 of 32 benchmarked jumps end at or below the human combinedV2 (both sides measured with the guard): 21 strict improvements plus 3 zero-step ties from human starts (j068, j066, j925: the human strat is already a local optimum for this operator set). j154 (d12) has no human benchmark but improved 6.83 to 6.49 from its 45 start.
- The guard's visible effect vs the pre-guard run: j001 lost its jump-later walk (the unsupported takeoff extension the user caught; final -1.61 vs human -1.45, still a win), j276 lost two support-invalid steps and now misses by 0.06, j335's numbers shifted with its guarded measurement. Everything else was already support-sound.
- Showcases: j053 3.54 to 0.50 (holdJump[all] over the 4-jump chain), j123 3.59 to 2.41 vs "human" 8.81, j716 6.53 vs 12.17, j121 4.01 vs 8.50, j345 True_Nix_Neo 3.41 vs 4.88. The huge wins over the flagged reconstructions are further evidence those captures are tighter than human play, not simplifier magic.
- The 8 misses (final above human): j264, j018, j276, j038, j032, j055, j105, j335. Common shape: the loop stalls ON the 45 line because keepify is never accepted (KEEP re-solve fails within FAST 15 s or scores worse), and no row operator can cross the input-mode gap. Next levers, in order: keepify under a bigger budget (OPTIMIZE or longer FAST), a dual-start strategy (also run the loop from the human save and keep the better endpoint; j158's human start already works this way), partial de-45 (span-wise), and S-tap/W-release insertion operators for the run-up (the same capability gap that makes those jumps 45-unsolvable or KEEP-hard).

Benchmark gate: per jump, the simplifier's output must score at or below the human variant's combinedV2 (on the honest pairs; the flagged reconstructions are not targets). Caveat to carry: the from-rest artifact slightly overprices first presses in the objective; none of the v0 operators can game it directly, but recentering can harvest the boundary-censoring conservatism (visible in the step log, and confirmed in-game on j001's sprint edge, see review observations below). The simplified saves under `build/hpk-metric/simplified/` load in-tool; jump-timing steps are ground-verified against the block checkpoints since 2026-07-28, in-game replay remains the final word.

## In-game review observations (user, 2026-07-28, ongoing)

The user is replaying the simplified strats in-game and logging observations here. Do NOT change measurement or loop semantics mid-review (it would invalidate the artifacts under test); batch the changes when the review is done.

1. j001: plays correctly in-game. Observation: the simplified strat releases SPRINT after 4 ticks; that is the loop walking the boundary-censored row-1 sprint edge to harvest artificial demand (recenter[row1+2] + recenter[row3+1] in the step log), not a real decision. User ruling: on this jump (and many others) sprint hold vs release is physically inert, so the SPRINT key must not add complexity; pressing sprint on tick 1 or holding it the whole run should score identically. On harder jumps sprint timing IS real complexity (deliberately delayed sprint starts).
2. j014: plays correctly in-game. j264: holds SPACE ticks 2-7 while airborne; pressing only on t2 is identical (release while airborne is inert). Same harvest family: recenter[row2+5] walked the JUMP release edge for capped-window slack.

3. User: "not all jumps allow you to just hold space, some require running ticks after landing." Two-part answer. (a) The probes already respect this: on multi-tick ground gaps an early press is NOT buffered, the fire tick moves earlier, the probe fails, the edge keeps its demand; the zero-demand rule below is gated on measured failures. (b) BUT the observation exposed a real model gap: real MC has a 10-tick auto-jump cooldown while space is HELD (jumpTicks = 10 on fire, decrements held, resets on release; docs/reference/mcpk/01-movement-formulas.md:247), which is exactly why humans re-tap short chains. ExactJumpModel does not model jumpTicks (the tap-only reconstructions never exercised it), so holdJump's physics-identical claim only holds for fires >= 10 ticks apart. Accepted holdJump steps bridging closer fires are UNSOUND in-game: j053 (fires 3 apart) and j140 (fires 8 apart) are predicted to desync at their second hop; j018, j081, j121, j123, j345 hold over 12-tick arcs and are sound. Batched fixes: restrict holdJump (and any held-span reasoning) to >= 10-tick fire spacing short-term; properly add jumpTicks to ExactJumpModel later (model change: ModernStepRegressionTest gate + in-game validation first).

4. j012 FAILS in-game: the simplified strat starts sprint at t7; the jump requires sprint held from exactly t2 (t1 or t3 both fail, the frame-exact delayed-sprint case predicted in observation 1). ROOT CAUSE, a real probe defect: sprint is stateful and under Sprint: Derive the physics runs off the recorded flag chronology; the probe syncs flags only inside the mutated region and downstream ticks keep their recorded sprinting=true, so the model kept the old sprint schedule while the real game engages from the moved key. SPRINT-key row edges are therefore NOT probeable by key mutation at all. Same defect class in the shipped strats: j716 (recenter[row19+5] walked its +SPRINT re-press, predicted broken) and possibly j335 (moved a -SPRINT release). j012's, j716's and j335's simplified saves are INVALID until regenerated. Batched fix: probe sprint timing by shifting the recorded sprint FLAG transition itself (the whole chronology moves, downstream included), which measures delayed-sprint tolerance exactly (j012 would read (0,0)); until then, sprint edges are unmeasured in the shift windows and the simplify loop must not touch SPRINT keys.

5. j018 FAILS in-game: A held from t32, needed from exactly t27 (one tick either way fails; a small camera turn would be an easy human alternative to the A-wing here, note for a future turn-for-strafe operator). j276 FAILS: A pressed at t12, needed held t7-or-t8 through t12. REVIEW STOPPED by user: "the constraints are NOT fulfilled at all."

VERDICT (2026-07-28): the simplify pipeline's outputs are WITHDRAWN except user-verified j001 and j014; the 24-of-32 benchmark claim is VOID. Root cause chain: (a) the loop edited A/WASD edges while saves were still in Force-45 mode, where the model ignores keys, so frame-exact wing presses (j018's row-26 edge that the measurement itself scored (0,0)!) were walked freely; (b) keepify then baked the corrupted key schedule in and re-solved the headless reconstruction; (c) keepify's debug-stripping means the saved file does not rebuild the same physics in the live tool (sprint seeding, per-tick sampling), so even "successful" solves do not hold in the real sim. "Cold-verified" only ever meant the headless model agreed with itself; the real SimulatorEntity was never in the loop, violating the repo's first rule (simulation is the single source of truth).

Rebuild plan, in order, before ANY new artifact is handed over: (1) operator hygiene: no key edits under Force-45 (keepify first, or key operators gated on KEEP), SPRINT keys untouchable, holdJump gated by the 10-tick cooldown; (2) sprint timing probed via the recorded flag chronology (observation 4); (3) keepify redesigned to keep the live-tool contract (no context stripping); (4) a mandatory real-sim verification gate: every output must replay through SimulatorEntity with all constraints fulfilled (loader-side batch harness, since core stays headless); (5) regenerate and re-benchmark everything.

Batched fix distilled from observations 1-2, apply after the review: a shift-window side that never fails (cap reached or boundary-censored) is UNCONSTRAINED, and an edge with any unconstrained side is collapsible (press from the start / hold forever), so it carries ZERO timing demand. A release's tap-shrink failure (lo=0) is the press's constraint, not the release's. Consequence to validate: chained mid-jump presses measuring (5,0) also zero out (press-early is free via hold-space auto-jump, consistent with holdJump[all]); the momentum demand terms shrink across the set, so this needs the FULL offline re-tune loop (parity, sweep, LOO, perturbation vs combinedV2), not a coefficient tweak, plus rerunning pairs and the simplify chunks. The loop then loses all incentive for cosmetic edge walks.

## Rebuild session (2026-07-28, fourth session): plan items 1-3 + zero-demand re-tune DONE

Everything below is implemented, `:core:test` green, and pinned by new always-on invariants. Simplify outputs stay WITHDRAWN; nothing was regenerated for handover.

Measurement changes (MeasurementEngine):

1. Sprint timing is probed via the recorded sprint FLAG chronology, never by key-mutation carry. Any edge whose flips include SPRINT shifts its coinciding flag transition (state index row+1) with the edge: regime-extension on the flag array, downstream intact. Gates: a state set true needs W && !S && !SNEAK on its (mutated) row; a moved ENGAGEMENT (false-to-true transition) must land on a grounded tick (1.8.9 semantics: no mid-air sprint start; conservative for modern versions). Dataset facts that shaped this: all 34 captures are Sprint DERIVE; every sprint PRESS co-occurs with other key flips (10 mixed edges); all 5 pure SPRINT edges are releases, whose flag chronology never moves (sprint persists while W is held), so they probe as no-ops and read free. j012's delayed-sprint start reads exactly (0,0) with both sides real failures (earlier deletes the takeoff jump and engages at t0; later needs a mid-air engagement, gated). Invariant-pinned for j001 (release free both sides) and j012 ((0,0), neither side free).
2. Held-JUMP reasoning respects the 10-tick jumpTicks cooldown (docs/reference/mcpk/01-movement-formulas.md:247): a release-later probe that extends a held span onto a grounded tick, and a press-earlier probe that merges into the previous tap (region reaches the previous JUMP release), are infeasible when the resulting fire spacing is under 10 ticks. Long-arc buffering (>= 10) stays free, which keeps the hold-space leniency on standard 12-tick arcs and prices hh-class re-tap chains honestly.
3. Per-side FREE flags (shiftLoFree/shiftHiFree in JumpMeasurements + input-shift-windows.csv): a side that never observed a real failure (cap exhausted or boundary-censored) is unconstrained. A pure-release edge whose lo-side first failure is the step that fully deletes the tap (dest at or before the released span's press) is recoded not-failed: that failure belongs to the press edge, which measures it on its own sides. Raw lo/hi counts and the old aggregates are unchanged (v1/v2 stay computable); new eff aggregates treat free edges as full-width.

Metric re-tune (offline loop per the recipe, parity verified to three decimals before sweeping):

- combinedV3 = toleranceCore + 0.8 * sum over ALL edges of [0 if edge has a free side else log10(11/(1+lo+hi))] + 0.06 * turnTicksJump + 1.5 * [jumpAngle] + 1.5 * [minMargin < 5e-5]. Spearman 0.954, 0 inversions on the 32; LOO worst 0.950/1; +-33 pct perturbation worst 0.936/2. Incumbent combinedV2 on the SAME (new) measurements: 0.947/1, LOO 0.944/2, perturbation 0.929/3. v3 wins every column.
- The metric is PHASE-BLIND and has NO geo term: with free edges zeroed, jump-phase demand became real signal (the noise was collapsible releases and buffered presses, now zero), and the geo term stopped paying (every b > 0 costs the inversion in the sumEff family). A sumRaw+geoEff family also reached 0.947/0 but is disqualified on principle: the user ruling requires free edges to cost exactly zero or the loop keeps its walk incentive. Term deletions: -sumEff 0.909/1, -turnJ 0.940/1, -JA 0.939/2, -marginFlag 0.950/2, -tolerance 0.888/2.
- Coefficient plateau a in 0.7..1.0 all 0/0.953+; a=0.8 chosen mid-plateau. Screens (metric, pair45, simplify) all score and calibrate with combinedV3 now; dHat separates all 11 levels (d3=0.84 d4=1.45 resolved without any geo machinery).

SimplifyLoop changes (operator hygiene + keepify contract):

- Key-editing operators (holdJump, recenter, deleteTap) require KEEP inputs: gated off under FORCE_45 default or any non-KEEP per-tick input override. Under a 45 start the loop can only keepify or noTurn first.
- SPRINT keys untouchable: recenter skips SPRINT-containing edges, deleteTap skips SPRINT pairs.
- holdJump candidates only bridge fires >= 10 ticks apart (holdJump[all] needs every consecutive spacing >= 10).
- keepify no longer strips debug: it rewrites the segment's debug movement samples from the rows (moveForward/moveStrafe at the 0.98/0.3 scale; sprinting via stateful derive: engages on a grounded tick with SPRINT+W held, persists while W && !S && !SNEAK, seeded from the recorded state at startTick). The saved candidate's headless physics is the derive-from-rows schedule the live tool reproduces; run-up samples before startTick stay recorded. Wall-hit sprint drops on candidate lines remain the real-sim gate's job.
- Candidates are measured under the baseline capture name: the jitter RNG is seeded by name, and per-candidate names let the greedy loop harvest RNG noise as fake score gains (caught on j001: two physically-inert JUMP-release walks worth 0.006 noise each; gone after the fix).
- Smoke runs: j001 = keepify only, final equals the human combinedV3 exactly (-1.541), cold-verified, sprint edge untouched. j264 still stalls on the 45 line (keepify not accepted within FAST 15 s), the known 8-miss shape; levers unchanged (bigger keepify budget, dual-start, partial de-45).

Real-sim verification gate BUILT (2026-07-28, same session, user directive: gate before hand-testing):

- Artifacts are now self-contained. `SimplifyLoop.bakeYawRows` (after the cold verify, so the verify stays unbaked) writes the solved line into the rows: every segment row gets yawLocked=true with the entity game facing from `sc.toGameFacings(wrapAll(result.yaws))` (float-exact, the same sequence the sim must run), and every FORCE_45 tick is realized into keys like the tool's Apply: W+SPRINT held, A/D per `sc.strafeAt` and `sc.strafeSign` (always +1 today, the solver never flips it), PLUS S and SNEAK cleared, a deliberate deviation from `InputRow.applyForce45` because the forced-input model ignored them and leaving them guarantees sim drift. Without baking, a loaded artifact ran its ORIGINAL row yaws, not the solve; that latent defect also contaminated the withdrawn review round.
- `core/SimVerifyBatch` (core, MC-free, driven through the ports): swaps a temporary FileSystemSaveStore onto the given directory, loads every save through the stock `SaveController.load` path (real `SimulatorEntity` sim runs), then checks (a) the full compiled constraint set (no Mode.F filtering; met/total with 1e-9 tolerance, worst violation from `Compiled.maxViolation`) against the SIM trajectory and yaws, and (b) the per-tick X/Z displacement match between sim and `ExactJumpModel.forward` under the sampled spec (1e-9, the `checkApplyDeviation` formula; first drift tick + max drift reported). PASS = all constraints met AND no drift. Unbaked or unrealized saves FAIL with immediate drift, which is the honest verdict. Report to `<dir>/simverify-report.txt`, summary returned. Entry point `Application.runSimVerifyBatch(Path)`.
- Trigger: Forge 1.8.9 only for now (all 34 captures are mcVersion 1.8.9): keybind V (`key.parkourcalculator.run_sim_verify`), directory from `PKC_SIMVERIFY` env var, default `<mcdata>/parkourcalculator/simverify`. Summary goes to log + chat.
- Staged for the user's early-catch hand-test: the d1+d2 simplified saves (regenerated with baking) copied to `loader-forge-1.8.9/run/client/parkourcalculator/simverify/`. NOTE the FAST-budget nondeterminism: j012's second d1-2 run accepted 0 steps where the first accepted 2 noTurn chains (candidate cold-solves are 15 s anytime runs); the staged artifact is the 0-step realized 45 solve.
- Full-set headless benchmark under the rebuilt loop (pre-bake run, all 33 cold-verified): keepify is now rarely accepted within FAST 15 s (j001, j335 only), so most d1-d5 jumps stall on the 45 line and miss the human benchmark; wins come from noTurn chains and the flagged too-tight reconstructions. Next levers unchanged: keepify under a bigger budget, dual-start, partial de-45.

Still open: regenerate pairs45 + ALL simplify chunks with baking and re-benchmark under combinedV3, with the in-game gate (user runs V) as the final word per capture. The stale pairs45 saves on disk are legitimate loop INPUTS (rows pristine, human debug retained, 45 result attached) but regenerate them anyway for the re-benchmark.

## Loop rework under user directives (2026-07-28, fifth session): 5 of 6 d1-d2 at or below human

User verdicts driving this session: the in-game gate re-proved a long-established invariant (model==sim, "tested hundreds of times prior"; the real defect class was artifact corruption, checkable headless), and the shipped d1-d2 outputs were bot-45 lines HARDER than the human strats. Directives: 250 ms per solve attempt (a trivial jump that needs more is a failed candidate), free-start every candidate solve, and violation-guided input adjustment instead of blind operator enumeration.

Rework, all in SimplifyLoop (plus one MeasurementEngine correction), `:core:test` green, metric ladder unchanged (combinedV3 0.954/0, identical medians):

- Budgets: CANDIDATE_SOLVE_MS 250, COLD_VERIFY_MS 1000. The engine is DETERMINISTIC for a fixed problem (identical scores across reruns), so repeated identical attempts are waste; diversity comes from seeds and operators, not retries.
- Free start: `freeStartify` adds the start block's footprint (cell of the seed pos, +-0.3, matching the checkpoint convention) as tick-0 X/Z ranges when startTick==0 and none exist; the engine's `deriveFreeStartBox` consumes them. The solved start comes back via `engine.apply()` + `setOnStartMoved` in HeadlessSolve (SolveResult does not carry it); `applyMovedStart` rewrites seed+start so the artifact stays coherent. Full-block assumption; fence/pane starts need block capture data (d5+ caveat).
- SPRINT AIR-ENGAGEMENT CORRECTION: the recorded flags prove real 1.8.9 engages sprint from the key mid-air (j012 engages at state 2 while airborne; also j065, j018). The grounded-engagement gate (observation-4 implementation detail) was WRONG and silently corrupted keepify's derived chronology (j012 got sprint from t12 instead of t2, making its KEEP problem unsolvable). Gate removed from both `deriveDebugSamples` and `shiftSprintFlag`; the j012 (0,0) invariant still holds on pure physics (earlier deletes the takeoff, later lands short). Ladder unchanged.
- Violation-guided repair (`repairYaws`): coordinate descent on max-violation over the yaw vector, single-tick moves plus suffix-block moves (rotate the whole tail; essential for chained arcs), 250 ms, seeded from the loop's OWN current line (never the human answer key; benchmark integrity). `deepenYaws` is the feasible twin (maximize min ineq margin, eq within 1e-9), mostly redundant with measurement centering (noise-scale gains).
- Seed families for crossing (keepify): `~repair` (current yaws), `~aim` (piecewise aim-at-waypoint line from the block checkpoint centers, the human template; yaw = atan2(-dx, dz) degrees), `~rot` (de-45 rotation: -45*strafeSign on strafe ticks reproduces the 45 trajectory under W-only keys), plus one 250 ms solver attempt.
- Arc-wise crossing (`keepifyArc`): partial de-45 via per-tick input overrides; cross [start, fire2) first, then shrink the FORCE_45 override region one arc per step, rot/aim-spliced seeds per range. This is how multi-jump chains cross (j018 crossed arcs 1-2 this way).
- Branching: three greedy branches from a 45 start: plain, deferred-keepify (noTurn first; j001 proved order matters), cross-first (keepify-family steps accepted UNCONDITIONALLY while any FORCE_45 remains, judged only on the branch's final; j276's winning path). Best final wins, cold-verified preferred.
- New ops: `resolve` (fresh 250 ms re-solve of the current KEEP problem, accept on improvement), `aimLine` (aim-seeded repair on KEEP saves), `deleteHold` (remove an A/D/S held span >= 2 ticks and let repair turn the camera instead: the user's turn-for-strafe idea; no wins yet).
- Candidate measurement seeds: candidates measure under the baseline capture name (jitter RNG); keepify rejection notes accumulate per iteration in the report.

d1-d2 scoreboard (final vs human combinedV3, all cold-verified, artifacts baked+realized and restaged in `loader-forge-1.8.9/run/client/parkourcalculator/simverify/`):

| jump | final | human | verdict |
| --- | --- | --- | --- |
| j001 | -1.541 | -1.541 | tie (aim-seeded keepify, one step) |
| j014 | -0.218 | 0.011 | BEAT (rot crossing + recenters + noTurn) |
| j264 | -1.011 | -0.405 | BEAT |
| j012 | -1.916..-2.148 | 0.435 | BEAT (no-turn 45 line: W+A+SPRINT, fixed camera; easier than the frame-exact delayed-sprint human strat) |
| j276 | -0.455 | -0.317 | BEAT (cross-first paid the keepify toll, then noTurn+recenter) |
| j018 | 2.474 | 1.431 | MISS: arcs 1-2 cross, the wing arc [25..38) stays FORCE_45 in the winning branch (aim-spliced crossing converges at 3.057 but no post-cross op digs below 2.474). Next levers: key ops on the KEEP prefix of partially-crossed saves, score-directed line search on the crossed line, aim-seed variety on the wing arc. |

NOTE the j012 result generalizes: for straight momentum jumps the metric correctly prefers turnless Force-45 lines (two keys, fixed camera) over human key-juggling; "simplified" does not always mean "the human line, cheaper". Whether that is the desired output style for handover is a user call.

## Smoothness dimension (2026-08-06, sixth session): combinedV4

User insight driving this session: the metric measured aim PRECISION (window widths, jitter) but not motor EXECUTABILITY. A smooth accelerate-decelerate camera turn is one hand gesture; a 45-0-45 tick-alternation demands velocity reversals every 50 ms. Two strats with identical windows can differ enormously in that dimension, and the simplify loop, which optimizes the metric, was blind to it: it would happily accept a jagged solver line with wide windows and call it simple (same Goodhart family as the edge-walk harvest).

Measurable: the RAW recorded line's sd/jerk is the WRONG measurable, it conflates the solver's jagged line with the jump (the same conflation as the too-tight reconstruction suspects). The honest measurable is a property of the feasible set: how smooth is the smoothest feasible line through the tube. Implementation in MeasurementEngine: coordinate descent minimizing the sum of squared second yaw differences over the startYaw-anchored extended sequence (each per-tick move goes to the closed-form quadratic target, feasibility-clamped by bisection against the Mode.F-filtered oracle; SMOOTH_PASSES 8, stop 0.01 deg; monotone objective descent, deterministic, a few hundred forwards per capture, ms-scale). DUAL-SEEDED from the centered anchor AND the recorded line, keeping the lower-objective result: single anchor seeding measured j014 at jerk 51 deg although its recorded line is flat and feasible (the 91 deg centering drift walked it into a local valley); with the recorded seed the result is a tight upper bound (recorded feasible implies smoothed jerk <= recorded jerk). New JumpMeasurements fields (all on the extended sequence including startYaw): smoothTravelDeg, smoothReversals, smoothJerkDeg (sum |second diff|), smoothVelSdDeg, smoothMaxTurnDeg, smoothYaw (per tick, dumped into yaw-windows.csv), plus yawVelSdDeg for the recorded line and startYawDeg; smoothMs timing. Invariants added: smoothness stats deterministic, j001 smoothest line near-flat (< 0.5 deg jerk) and reversal-free, j014 flat-recorded stays flat (< 1.0 deg), final smoothed line asserted feasible inside measure().

Result on the 32: no-turn-playable strats collapse to ~0 jerk regardless of how jagged the recorded solver line is (j001/j012 0.0, j014 0.12, j018 0.13, j029/j038/j158/j032 ~0), true camera-technique jumps keep intrinsic jerk (j066 carpet 148, j081 227, j335 248, j140 172, j925 125). Raw smoothJerkDeg alone: Spearman 0.796 vs d.

Re-tune (offline recipe, parity reproduced to three decimals on all 8 registered metrics before sweeping):

- combinedV4 = combinedV3 + 0.15 * log1p(smoothJerkDeg). Center 0.957/0, LOO worst 0.954/1, +-33 pct perturbation (5 coefficients, 243 cells) worst 0.939/2. Incumbent v3 on the same measurements: 0.954/0, LOO 0.950/1, perturbation 0.936/2. v4 wins every column. Coefficient plateau e in 0.05..0.4 all 0 inversions (peak 0.15).
- Families that lost, do not re-try blind: smoothReversals terms score higher centers (0.959-0.961/0) but the count is floor-brittle (per-capture counts swing 9-to-0 as the reversal floor moves 0.01..2.0 deg, i.e. partly micro-wiggle residue at the descent tolerance) and a discrete count is a bad optimization surface for the simplify loop; smoothMaxTurnDeg(sqrt) 0.958/0 but perturbation only ties v3; smoothVelSd families 0.956 max; linear transforms worse everywhere; replacing the turnTicksJump term with any smoothness term loses (0.945-0.952); jerk+reversals combo 0.955/0, worse than jerk alone. Term deletions on v4: every existing term still earns its place (-sumEff 0.923/1, -turnJ 0.949/1, -JA 0.945/2, -marginFlag 0.956/2, -tolerance 0.897/2); the jerk term itself is the cheapest deletion (back to 0.954/0), so on THIS human-reconstruction calibration set it adds modest ranking signal; its main value is pricing the executability dimension the simplify loop optimizes against.
- All three screens (metric, pair45, simplify) score and calibrate with combinedV4; dHat separates all 11 levels (d1=0.03 d2=0.44 d3=0.85 d4=1.71 d5=3.62 d6=4.50 d7=4.80 d8=5.77 d9=7.05 d10=7.70 d11=9.07).

Consequences for the pipeline: (a) the simplify loop's objective now rewards candidates whose tube admits a smooth line, closing the jagged-line blind spot; a dedicated smoothLine operator (replace current yaws with the smoothest feasible line when it scores better) is the natural next operator and a candidate lever for the j018-class misses; (b) the suspect reconstructions get a new diagnostic: j123 (jerk 40) and j716 (jerk 32, 0 reversals) measure yaw-tight but kinematically tame, consistent with a bot line in a thin smooth tube rather than a humanly-jerky strat. Benchmarks under v4 need the pairs45 + simplify chunks regenerated (still pending from the fifth session anyway).

Significant-angle floor (same session, user-caught): the tolerance core floored jitter/winGeo at 1e-4 deg and therefore priced SUB-BUCKET differences: a significant angle spans ~0.0055 deg (360/65536, docs/reference/mcpk/02-angles-and-mouse.md), yaws within one sine bucket produce identical trig, so log10(0.002) vs log10(0.0055) was 0.44 score points of physically meaningless distinction. Measured jitter below one bucket is REAL in the model and in-game (it is the distance to a fatal bucket seam; the tightest captures' winMin 0.0039-0.0098 shows their tubes are one to two buckets wide, i.e. TAS-aim territory, independent confirmation of the too-tight reconstruction flags), but it is not human aim demand. Fix: toleranceCore floors at Metrics.SIG_ANGLE_DEG = 360/65536. Protocols (vs the same metric on the 1e-4 floor): v4 center 0.960/0 (was 0.957/0), LOO 0.957/1 (0.954/1), perturbation 0.943/2 (0.939/2); v3 0.958/0, 0.955/1, 0.938/2; wins every column for both, jerk coefficient plateau 0.05..0.4 stays inversion-free. The floor is shared by all registered metrics, so the reprinted strawman ladder shifts a few thousandths (v1/v2 centers unchanged at 0.942/1 and 0.947/1). Jitter 0.0000 in the table means below the 0.001 deg bisection resolution, not literally zero.

Final ladder (2026-08-06): combinedV4 0.960/0 (medians d1=0.03 d2=0.44 d3=0.85 d4=1.71 d5=3.62 d6=4.50 d7=4.80 d8=5.77 d9=7.05 d10=7.70 d11=8.92), combinedV3 0.958/0, combinedV2 0.947/1, combinedV1 0.942/1.

## Stratfinder template spike (2026-08-07, seventh session)

User direction: search over the ESTABLISHED human strat vocabulary instead of free-form generation. Spec (user's messages are the spec, cross-checked against docs/reference/mcpk/06-timings-momentum-glitches.md): pessi(k) = SPACE, W+SPRINT k ticks later; fmm(k) = W+SPACE, SPRINT k later; Mark(side,k) = SPACE+strafe, W+SPRINT k later; run(d)+jam = d W+SPRINT run ticks then jam; bwmm = a real backward ARC (S+SPACE jump past the back hitbox edge, ~1 walk tick, then any forward timing), loop mm = repeated bwmm; WAD/WAWD jump-tick idioms; all within 45-quantized no-turn headings. bwmm is BACKWARD momentum, not "backwalled" (near-opposites: backwalled is the constraint, bwmm the technique; no wall collision involved, so everything is native to the collision-free model). Difficulty lives in how tight the jump forces the parameter window (shift windows already measure this); the k value itself is only marginally harder mid-cycle (user ruling), used as a ranking tie-break, not a metric term.

Implementation (test-side): metriclab/StratTemplates (plans = 70 instances: run0-8+jam, fmm1-6, pessi1-8, markA/D 1-4, bwmm+{jam,fmm1,fmm2,pessi1}, each in a /nt dF=0-chained no-turn variant and a free-yaw variant) + HpkTemplateScreen (PKC_TEMPLATE=1, PKC_TEMPLATE_ONLY/_D/_MS). Realization per instance: KEEP-mode save, template rows to the fire tick, human jump-phase rows and constraints TIME-TRANSLATED by (templateFire - humanFirstFire) with F/DF dropped, timing patches into the early arc (delayed W/SPRINT onsets), fresh slip schedule, debug samples derived from rows (keepify convention), runway rectangle from the human's recorded ground positions as X/Z ranges on every generated ground tick (t0 range doubles as the free-start domain), 250 ms cold solves, combinedV4 scored under the baseline capture name. HeadlessSolve now CANCELS the engine at deadline (before this fix abandoned 250 ms solves kept solving on the FAST budget in background threads; the first full run OOMed after 16 min).

Results (template-report.txt, template.csv):

- 26 of 33 benchmarked jumps get at least one feasible template strat; 14 land at or below the human combinedV4 (j001 -1.10, j012 -2.23, j014, j264, j018, j276, j028 -1.60, j053 -1.09, j055, j108 -0.49, j135 -5.47, j188, plus exact ties j158 pessi4/nt, j032 pessi1/nt, j066 pessi1: the ties mean the vocabulary reproduces the actual human strat).
- ALL SIX 45-unsolvable jumps (j158, j066, j925, j065, j068, j101) are template-feasible; the run-up speed control gap that killed Force-45 is closed.
- NONE on 7: j081 (d7 heavy-turn 7-5), j121, j140, j335, j345, j1150, j716, j154 (the d9-d11/12 tail): multi-arc translated constraint sets under 250 ms FAST; unknown whether budget or vocabulary is the binding limit (PKC_TEMPLATE_MS knob exists).
- CAVEATS, do not trust blindly: (a) constraints at ticks <= the human first fire are DROPPED by construction, so jumps whose defining difficulty is PRE-fire lose teeth; j135's -5.47 (d9 waza at near-d2 score) is the suspect case, and j030's template results are meaningless until its re-save (its 5.4375 line at t37 is out-of-segment and dropped). (b) Mid-arc obstacle constraints are translated rigidly; different approach speeds may make the fixed tick wrong in either direction. (c) Nothing is baked/realized or in-game verified yet; the screen emits no loadable artifacts. Real-sim V-gate before any handover.

Next steps: audit the outsized wins (j135, j053) for dropped-constraint artifacts; retry the NONE tail with a bigger budget; then the integration decision (standalone stratfinder vs simplify-loop operator family) with the user; then bake+realize outputs and run the in-game gate.

Suggested fifth commit for the gate:

```
feat(anglesolver): real-sim verification gate + self-contained artifacts

SimVerifyBatch loads every save in a directory through the stock save
path, runs the real SimulatorEntity over its rows, and checks the sim
trajectory against the save's full constraint set plus the model's
predicted per-tick path (1e-9); report to simverify-report.txt. Forge
1.8.9 binds V to sweep PKC_SIMVERIFY (default
<mcdata>/parkourcalculator/simverify) and prints the summary in chat.
Simplify finals are now self-contained: solved yaws baked into
yaw-locked rows and Force-45 ticks realized into W/A/SPRINT keys
(S/SNEAK cleared to match the forced-input model assumption).
```
