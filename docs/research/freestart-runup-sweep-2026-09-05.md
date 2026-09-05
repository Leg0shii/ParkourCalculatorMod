# Free-start solves vs the fixed-takeoff ILS solve (j716), 2026-09-05

Branch `feature/freestart-ils-ranking`. Question from the maintainer: free-start solves from T1 land well short of
the best known solve, while the fixed T32 solve with the `longer` preset (ilsPolish 0.0055 to 10 deg) beats it.
Hypothesis: rank free-start candidates by their ILS-polished objective instead of the raw one.

## Setup

- Jump: `hpk_human/d11/j716-1bm_Cobblewall_to_Cobblewall_Winged_Neo` (1.8.9). MIN X at T43, landing edge X = -699.95,
  offset = -699.95 - objective. Best known solve -699.950268670491 (offset 0.0002687), found from the fixed T32 state.
- Structure: rows 0..30 are a dF = 0 run-up (constant facing yaw0), row 31 takeoff, 11 free jump yaws, landing 42.
  Footprint range constraints at internal ticks 0, 4, 17, 30 (the player oscillates on the 0.5 x 0.5 post).
- Headless harness: `EngineFileScreen` (env `PKC_GRAPH_FILE`, `RECORD`/`RECNODE` dump), `RunUpSweepProbe`,
  `HpkStartBenchmark` (`-Dpkc.bench.graph`, `-Dpkc.bench.skip`). The benchmark reference for j716 was corrected from
  the stale -699.9501960734942 to the best known -699.950268670491.

## Diagnosis

| run | setup | objective | offset | chain |
| --- | --- | --- | --- | --- |
| RUN1 | fixed T32, 11 ticks, 20 s | -699.950268654 | 0.0002687 | closed form -> fold driver -> ILS |
| RUN2 | free start T1, 42 ticks, 60 s | -699.950187 | 0.000187 | horizon -> certified B&B -> translated start -> leaf snap |
| RUN3 | known-good T1 pinned, 42 ticks, 60 s | -699.950120 | 0.000120 | horizon -> certified B&B -> leaf snap |

Start selection is not the bottleneck: RUN2 already picks the known-good start to within 3e-4 blocks. On the
42-tick chain `foldDriver` never fires (`FoldDriverNode` refuses any spec with `Mode.F` constraints) and `ilsPolish`
reports UNCHANGED because it drew its kick ticks uniformly over all 43 ticks, so about three quarters of the kicks hit
dF-locked run-up ticks and were rejected as infeasible.

## Structure of the free-start problem

Under fixed keys and surfaces the run-up is a rigid translation of the start: the takeoff velocity depends on the
run-up facing yaw0 only, the takeoff position is start + offset(yaw0), and everything is bit-identical within one
sine bucket (0.0055 deg). The free-start problem is therefore a one-dimensional enumeration of facing buckets, each
with the 11-tick inner solve that RUN1 already solves well, plus a translated footprint box for the takeoff position:
B(yaw0) = intersection over footprint ticks t of (footprint_t + takeoff - pos_t), intersected with the free StartBox.

E1 (pinned start, 183 buckets): the captured bucket is best after ILS polish (reproduces 0.0002687 from T1), ranked
4th before polish; ILS gain per bucket ranged 3e-7 to 6.9e-5, Spearman(stage1, stage2) = 0.78. The raw objective is a
poor ranking proxy at the 1e-5 scale, which confirms the polish-then-rank hypothesis at bucket granularity.

E1b (free takeoff position): only buckets 36156..36158 are feasible (below: box top under the tick-31 Z wall; above:
B empty). Best: bucket 36156 at -699.950269137, beating the known solve by 4.7e-7 blocks, a practical tie.

## Provability

`CertifiedBnb` on the window with the free box, 20 s per bucket, sound but not closed to CERT_EPS: floor on X of
-699.950293314 (offset ceiling 0.000293, 0.0002945 after the FreeP0 smoothing slack of 1.2e-6). The achieved
0.000269 is within 2.4e-5 blocks of the optimum for this key pattern; a strictly better solve would need a different
run-up input pattern, which is outside the angle solver's scope. A pure "max speed and heading" argument only bounds
loosely; the costate dual with the sine-table support is the rigorous form of that argument. Prior COPT results on
the smooth clamp-free model (offset ceiling about 0.00039) are looser because that model misstates the recorded path
by about 1e-4.

## Changes

1. Chain-aware ILS: `IlsPolish.Config.freeTicks` (from `YawTies` singleton groups, set by `IlsPolishNode`), kicks and
   `BucketAscentPolish` scans restricted to free ticks, count clamped to the free set; null path bit-identical.
2. `facingStep` node (`FacingStepNode`, registered in `NodeCatalog`, help in `NodeHelp`), a loop-driven redesign of
   the rejected monolithic `runUpSweep` that no longer re-orchestrates polish or translation internally. On its first
   visit it enumerates yaw0 buckets in `windowDeg` around the incumbent facing, computes the run-up state and takeoff
   box B(yaw0) per bucket, seeds each tail with a short pinned window solve (`LongRunSolver.suffixSpec`,
   `ClosedFormSolve`, `FoldReplayDriver`, about 300 ms), and keeps the top-K seeds as a work list. That seeding is the
   only solver call in the node. It then walks the work list one bucket per visit: it sets the scenario start for the
   bucket (`Scoring.adoptPinnedStart` when the start is free, StartBox-clipped into B and the free box) and emits the
   seeded path on the TRUE branch so the graph's own `ilsPolish` (`ils2`, chain-aware, tail ticks only) and
   `translatedStart` (`translate2`, best feasible translation inside the free box) refine it. On the next visit it
   re-reads the refined candidate, re-verifies it byte-exact at the current start with an explicit free-box
   containment check, and keeps the best. When the work list is exhausted or the budget is spent it restores the best
   start and returns IMPROVED (chain suffix "facing sweep"), else restores the original start and returns
   UNCHANGED/NONE. Params: budgetSec, windowDeg, maxBuckets, topK. Graph wiring in `BuiltinGraphs.build` for all tiers
   inserts sweep, ils2, translate2 after `freeImprove`: freeImprove to sweep; sweep TRUE to ils2; sweep
   FOUND/IMPROVED/UNCHANGED/NONE to fold; ils2 to translate2; translate2 back to sweep in non-fast tiers (the loop,
   guarded because ils2 carries a positive budget), or translate2 to fold in the Fast tier where sweep has budget 0,
   returns immediately and never enters the loop.
3. Tests: `IlsPolishFreeTicksTest`, `FacingStepNodeTest` (catalog/params, no-chain UNCHANGED, budget-0 never TRUE,
   enumeration window/max, StartBox containment from j828), `BuiltinGraphsTest` (per-branch loop edges per tier),
   `PipelineShapeTest` (collapses the sweep/ils2/translate2 cycle to one pass before comparing).

## Benchmark (hpk_human d10, d11, d12 without j155; `longer` preset; 60 s per jump; gap > 0 = short of reference)

| jump | before | ILS fix only | ILS fix + runUpSweep |
| --- | --- | --- | --- |
| j140 | +2.87e-3 | +2.77e-3 | +1.78e-4 |
| j335 | unsolved 16/28 | unsolved | unsolved |
| j345 | +7.96e-3 | +1.89e-3 | +1.89e-3 |
| j1099 | +0.658 | +0.657 | +0.657 |
| j1149 | +1.85e-3 | +1.68e-3 | +1.74e-3 |
| j1150 | +4.30e-4 | +2.2e-6 | -9.8e-5 |
| j716 | +7.0e-5 | +3.7e-5 | -5e-7 (offset 0.0002692 from T1) |
| j718 | -5.5e-4 | -6.4e-4 | -6.4e-4 |
| j828 | -7.3e-6 | -7.3e-6 | -1.9e-5 |
| j925 | +6.4e-5 | -9.9e-5 | -3.3e-4 |
| j154 | +8.8e-5 | +6.4e-5 | +8e-7 |

Ten of eleven improve, one unchanged, none regress. The first "after" run committed a start outside the footprint on
j828 (41/42 met): the tick-0 ranges live in the StartBox, not in `spec.constraints`, and `Scoring.verifiedObjectiveAt`
does not check the box. Fixed by intersecting the translated StartBox into the takeoff box and gating the verify.

## Open items

- Builtin Fast/Optimize graphs now include the `facingStep` loop after `freeStartImprove`; `PipelineShapeTest`
  collapses the loop to a single pass so both tiers share one canonical shape.
- `SolveResult` detail "Dual bound gap" is empty whenever the terminal node is ILS (the candidate's dual gap is not
  carried through `ilsPolish`); pre-existing display gap.
- Bucket enumeration uses the 1.8.9 legacy deg-to-rad chain; on other MC versions this only affects dedup
  efficiency, never correctness (the full-spec verify is byte-exact per model).

## Loop body A/B and the live tracker (2026-09-05, later)

`topK` was removed: the loop walks every full-spec-feasible seed in rank order until its own deadline
(`budgetSec` from the first visit, capped by the run deadline). Two loop bodies were measured under builtin Optimize
at 60 s on the 11 jumps: body A = `ils2 -> translate2`, body B = `cert2 -> bnb2 -> ils2 -> translate2` with 3 s
budgets each. Gap > 0 = short of reference.

| jump | body A | body B |
| --- | --- | --- |
| j140 | +4.44e-4 | +4.41e-4 |
| j345 | +2.56e-3 | +2.56e-3 |
| j1099 | +0.657 | +0.657 |
| j1149 | +2.10e-3 | +2.02e-3 |
| j1150 | -3.75e-5 | -3.52e-5 |
| j716 | +6e-7 | +6e-7 |
| j718 | -6.32e-4 | -6.32e-4 |
| j828 | -1.54e-5 | -7.3e-6 |
| j925 | -1.66e-4 | -1.66e-4 |
| j154 | +2e-7 | +4e-7 |

Two wins each above 1e-6, identical j716 and j154, identical total time: body A stays the builtin default, body B
remains expressible in a custom preset.

Live tracker: a loop candidate was displayed with an impossible objective (j716 -699.950307, 42/44 met) because the
live result was rebuilt at poll time against whatever start the scenario held by then. `SolveProgress` now snapshots
the start together with the best candidate and the engine builds the live result at that start.
