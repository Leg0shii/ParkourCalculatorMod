# Generation follow-ups (proposed)

Work found while tracing Real11 solve reports. Not approved; see the
[deferred-work register](../DEFERRED_WORK.md) (P1, P2, P3).

## 1. Encounter prescreen

The avoidance generator builds one encounter per landing segment per solid, from
tick 0, for every repair run (`ConstraintVariants.__init__`,
[`constraints/variants.py`](../../src/parkour_solver/constraints/variants.py);
`landing_segments`, [`constraints/encounters.py`](../../src/parkour_solver/constraints/encounters.py)).
Each ground tick is its own segment. Below 256 encounters the cost is linear; above
it `choose_item` re-ranks every remaining item through the 256-entry
`preparation_cache` and thrashes. Measured per repair run on a synthetic 60-tick,
10-segment route with unreachable solids: 250 encounters 0.045 s, 260 0.125 s,
500 10.9 s, 1000 61.7 s. Real11's B7 runs build 140.

### 1a. Drop unreachable encounters once

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

### 1b. Cheap spatial prefilter before 1a

Discard solids that cannot meet the route's reach box (per segment, X/Z envelope
union plus the exact Y range), using a per-scene spatial index built once. A static
block-to-block reach table is the coarser alternative.

- Estimated runtime: index build linear in solids once per scene; per run, one
  lookup per segment. Leaves only nearby solids for 1a.
- Estimated effort: an index on the scene plus a filter before item construction, one test.
- Worth it only once scenes have thousands of blocks.


## 2. Zero-width landing-cover strips

Real11's B5 landing offers a second landing-cover rectangle,
`X [731.3, 731.3] × Z [-1095.3, -1094.7]`: the line where the player stands on B5
exactly touching B6's +X face. It costs a decision branch and a screen rejection
in every B5 frame. `canonical_cover`
([`spatial/regions.py`](../../src/parkour_solver/spatial/regions.py), "Preserve
lower-dimensional free strata", from commit `f9aa0d96`) keeps zero-width pieces,
and `contact_regions`
([`search/events/adapter.py`](../../src/parkour_solver/search/events/adapter.py))
turns every cover piece into a landing option.

Decision (user, 2026-11-17): no free piece narrower than 0.012, the minimum gap
between blocks in this Minecraft version, can exist. Do not keep zero-width or
sub-0.012 strata.

- Remove them everywhere they appear: `canonical_cover` and its consumers (support
  components, surface groups, route guards, guides), landing covers and the
  avoidance sides of section 3.
- Update the tests that pin the old strata behaviour to the new rule.
- Estimated effort: a width filter plus one test; runtime cost negligible.

## 3. Sides covered by an adjacent block

B7's +X side (X = 730) is offered as an avoidance side although B6 sits flush
against it: the gap between them is the same zero-width stratum as section 2. The
generator takes all six sides of each solid's own box (`faces(item['box'])` in
[`constraints/encounters.py`](../../src/parkour_solver/constraints/encounters.py))
and never consults neighbours; the exposed-face catalog
([`spatial/faces.py`](../../src/parkour_solver/spatial/faces.py)) is not used by any
generator. "B7 X+" plus B6's Z- rows names the same free region as "B7 Z-", so the
alternatives duplicate: on the Real11 cold run, frame 4 solved 18 routes with 5
distinct results and frame 8 solved 21 with 9.

- Proposed: offer only sides with an exposed face of positive width, after
  section 2 removes zero-width strata.
- Estimated effort: map each solid's sides to the face catalog in
  `prepare_encounter`, plus one test with two flush blocks; saves the duplicate
  IPOPT solves (13 of 18 in frame 4).

Related: [obstacle relevance](OBSTACLE_RELEVANCE.md), [avoidance passages](AVOIDANCE_PASSAGES.md).
