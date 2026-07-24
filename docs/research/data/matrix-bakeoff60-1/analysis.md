# matrix-bakeoff60-1: bake-off shakedown leg on the TASer band (2026-07-20)

The first measured leg of checklist step 6 (grill brief section 10): incumbent chain at the pinned
lambda vs AlmSnapStage with native lambda, on the standing TASer band
(docs/research/data/matrix-taser-pin1/band.txt, all six 50+-tick corpus problems). This is a
SHAKEDOWN, not the bake-off verdict: ALM ran at stage defaults with no tuning sweep and no
80/100-tick rungs exist yet (corpus tick gap runs 66 to 176).

## Method

- RunMatrixScreen with PKC_MATRIX_SWEEP="taser60-l1e3|alm60:l=1e-3",
  PKC_MATRIX_BAND=matrix-taser-pin1/band.txt, timeout 120 s, cold starts.
- taser60-l1e3: the standing incumbent preset (THOROUGH, optimizeSeconds=60, smoothLambda=1e-3).
- alm60-l1e-3: AlmSnapStage driven directly (new A18 driver), 60 s budget, lambda 1e-3 native in
  the ALM smooth objective (this session's build), defaults seeds=16 cooking topK=32 gate=1.0,
  no warm seeds, free startBox becomes the translation domain.

## Results

| problem | ticks | sense | incumbent feas/obj/travel/rev | ALM feas/obj/travel/rev | obj delta (ALM-inc, +=ALM better) |
| --- | --- | --- | --- | --- | --- |
| solve/j001 | 353 | MAX | yes / 12.225830 / 3441 / 59 | NO / - / - / - | - |
| solve/j002 | 189 | MIN | yes / -33.672892 / 1840 / 31 | yes / -33.589203 / 1032 / 62 | -0.083689 |
| solve/j003 | 176 | MIN | yes / -31.299998 / 1787 / 28 | yes / -31.299772 / 887 / 62 | -0.000225 |
| solve/nix-full-t1 | 54 | MAX | yes / 8.700000 / 403 / 14 | NO / 8.699417 / 321 / 23 | -0.000583 |
| solve/trp-optimize-feasible-swap | 62 | MAX | yes / 4.230684 / 556 / 4 | NO / 4.209358 / 496 / 15 | -0.021326 |
| frontier/j155-4jmm_3bcmm_4.9375b | 66 | MAX | yes / 4984.763263 / 457 / 11 | yes / 4984.762502 / 349 / 15 | -0.000761 |

Incumbent nix-full-t1 row is a censored-at-cap record (feasible incumbent held at the 120 s
timeout); all other incumbent rows finished inside their budget.

## Reading

1. Feasibility: incumbent 6/6, ALM 3/6. ALM misses exactly where the incumbent's feasibility
   machinery earns its keep: nix-full-t1 and trp need momentum assembly / closer / peel-class
   work ALM does not have, and on j001 (353 ticks) ALM produced nothing snapped in 60 s.
2. Objective: incumbent wins every comparable row. Costs range from sub-milliblock (j003, j155)
   to 0.084 blocks (j002).
3. Smoothness signature: on its three feasible rows ALM always travels less (1032 vs 1840, 887 vs
   1787, 349 vs 457 deg) but reverses direction MORE (62 vs 31, 62 vs 28, 15 vs 11):
   low-travel, high-frequency micro-jitter vs the incumbent's fewer, larger swings. If reversal
   count is the stat the TASer persona actually optimizes for, ALM's travel edge is not
   automatically a win; the pin ruling (A7/A8) scored travel, so this is worth revisiting when
   picking the persona metric readout.
4. The >64t lambda-inertness limit is visible in the data: incumbent j001/j002/j003 rows are
   horizon-path solves (instant, wiggly, travel 1787-3441 deg) that never consulted lambda.

## Standing status for step 6

Still open before a verdict: 80/100-tick rungs (user is producing captures), an ALM tuning sweep
over the A18 grid (seeds/topk/cooking/gate at fixed lambda), and a decision whether the production
persona may hand ALM warm seeds (the matrix leg is cold-start by the seedless rule; the persona
race in the engine would legitimately have incumbents to warm from).
