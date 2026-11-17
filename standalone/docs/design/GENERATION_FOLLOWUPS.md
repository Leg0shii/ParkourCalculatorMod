# Generation follow-ups (proposed)

Work found while tracing Real11 solve reports. Status per item is in the
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

## 3. Use the exposed-face catalog everywhere

B7's +X side (X = 730) is offered as an avoidance side although B6 sits flush
against it: the gap between them is the same zero-width stratum as section 2. The
generator takes all six sides of each solid's own box (`faces(item['box'])` in
[`constraints/encounters.py`](../../src/parkour_solver/constraints/encounters.py))
and never consults neighbours; the exposed-face catalog
([`spatial/faces.py`](../../src/parkour_solver/spatial/faces.py)) is not used by any
generator. "B7 X+" plus B6's Z- rows names the same free region as "B7 Z-", so the
alternatives duplicate: on the Real11 cold run, frame 4 solved 18 routes with 5
distinct results and frame 8 solved 21 with 9.

- In player-centre space the expanded boxes overlap: B6 `X [729.7, 731.3] × Z [-1095.3, -1093.7]`,
  B7 `X [728.7, 730.3] × Z [-1095.3, -1092.7]`. Along B6's Z length, `X ≥ 730.3` lies inside
  B6; B7's X = 730.3 boundary is real only for Z in [-1093.7, -1092.7]. There the
  usable X side is B6's (`X ≤ 729.7` once over B7, or `X ≥ 731.3`).
- The usable geometry already exists: `merge_faces` / `FaceCatalog`
  ([`spatial/faces.py`](../../src/parkour_solver/spatial/faces.py)) subtracts flush
  neighbours from each face. On Real11 it reports B7's +X face (X = 730) exposed only
  for Z [-1094, -1093] and B6's -X face only above Y 7. Only `tools/geometry_bounds`
  and tests use it.
- Decision (user, 2026-11-17): precompute the catalog once per scene and use it
  everywhere geometry is turned into sides, contacts or landing regions. Collision
  checks stay per box: the union of boxes is already exact for collision.

### Consumers to move onto the catalog

| Area | Today builds from raw boxes in |
| --- | --- |
| Avoidance sides | `faces()` in [`constraints/avoidance.py`](../../src/parkour_solver/constraints/avoidance.py); `prepare_encounter`, `support_faces` in [`constraints/encounters.py`](../../src/parkour_solver/constraints/encounters.py) |
| Wall, ceiling and named-case contacts | `contact_opportunities` in [`movement/contact_profiles.py`](../../src/parkour_solver/movement/contact_profiles.py), [`movement/ceiling_generation.py`](../../src/parkour_solver/movement/ceiling_generation.py), `outside_row` / `landing_wall_options` in [`movement/cases.py`](../../src/parkour_solver/movement/cases.py), [`constraints/contacts.py`](../../src/parkour_solver/constraints/contacts.py), [`constraints/hit_events.py`](../../src/parkour_solver/constraints/hit_events.py) |
| Landing covers and support | `canonical_cover`, `support_components`, `free_transit_cover` in [`spatial/regions.py`](../../src/parkour_solver/spatial/regions.py); face features in [`spatial/event_routes.py`](../../src/parkour_solver/spatial/event_routes.py); [`spatial/cells.py`](../../src/parkour_solver/spatial/cells.py); [`spatial/anchor_guides.py`](../../src/parkour_solver/spatial/anchor_guides.py); [`spatial/guides.py`](../../src/parkour_solver/spatial/guides.py) |
| Stays per box (collision) | `block_interaction` regions in [`reachability/service.py`](../../src/parkour_solver/reachability/service.py), [`validation/scoped_scene.py`](../../src/parkour_solver/validation/scoped_scene.py) |

- Build the catalog in scene preparation (`prepare_scene`) next to the collision
  scene, with section 2's minimum width applied, and pass it to every consumer above
  instead of each recomputing from boxes. The legacy corridor path
  (`spatial/compat/corridors.py`) moves only if it stays in use.
- Changes generated sides, contacts and covers, so bump the constraint policy and the
  movement context identities.
- Estimated effort: one preparation step plus one consumer change per row above,
  each with a flush-block test; the avoidance row removes the duplicate IPOPT solves
  (13 of 18 in Real11 frame 4).

Related: [obstacle relevance](OBSTACLE_RELEVANCE.md), [avoidance passages](AVOIDANCE_PASSAGES.md).
