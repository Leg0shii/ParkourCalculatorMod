# Encounter prescreen (proposed)

The avoidance generator builds one encounter per landing segment per solid, from
tick 0, for every repair run (`ConstraintVariants.__init__`,
[`constraints/variants.py`](../../src/parkour_solver/constraints/variants.py);
`landing_segments`, [`constraints/encounters.py`](../../src/parkour_solver/constraints/encounters.py)).
Each ground tick is its own segment. Below 256 encounters the cost is linear; above
it `choose_item` re-ranks every remaining item through the 256-entry
`preparation_cache` and thrashes. Measured per repair run on a synthetic 60-tick,
10-segment route with unreachable solids: 250 encounters 0.045 s, 260 0.125 s,
500 10.9 s, 1000 61.7 s. Real11's B7 runs build 140.

## 1. Drop unreachable encounters once

Prepare each encounter once under the route's unconditioned envelopes and drop
those with no possible phase before the generator loop; keep them in `encounterLog`.

- Precondition: verify `condition_envelopes` never widens a base envelope
  ([`reachability/screen.py`](../../src/parkour_solver/reachability/screen.py)).
  Named step/grind probes stay uncertain and are never dropped.
- Changes persisted generator items, so bump constraint state3.
- Estimated effort: one function in `ConstraintVariants`, the state bump and one test.
- Estimated runtime: one preparation per encounter, about 5.6 µs per phase check
  (Real11), removing the quadratic term. Still `3 × ticks × solids` checks per run,
  e.g. 3.4 s at 200 ticks and 1000 solids.

## 2. Cheap spatial prefilter before 1

Discard solids that cannot meet the route's reach box (per segment, X/Z envelope
union plus the exact Y range), using a per-scene spatial index built once. A static
block-to-block reach table is the coarser alternative.

- Estimated runtime: index build linear in solids once per scene; per run, one
  lookup per segment. Leaves only nearby solids for option 1.
- Estimated effort: an index on the scene plus a filter before item construction, one test.
- Worth it only once scenes have thousands of blocks.

Related: [obstacle relevance](OBSTACLE_RELEVANCE.md), [avoidance passages](AVOIDANCE_PASSAGES.md).
