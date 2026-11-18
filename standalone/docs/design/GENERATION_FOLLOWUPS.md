# Generation follow-ups: implementation plan

Goal: fewer avoidance alternatives and IPOPT solves without losing routes. Written
so an agent with no other context can implement it step by step. Status per item is
in the [deferred-work register](../DEFERRED_WORK.md) (P1-P7).

## Baseline (Real11 cold run, `runs/solve-report-check`, source `ed5e9c95`)

| | Frame 4 (B5 t57 → B7 t66) | Frame 8 (B5 t54 → B7 t63) |
|---|---|---|
| Alternatives solved | 21 (18 feasible, 2 IPOPT unknown, 1 blocked) | 21 (21 feasible) |
| Options rejected before solving | 76 | 76 |
| Options dropped as duplicate or dominated | 47 | 47 |

IPOPT dominates: 53 calls, 9.77 s of a 26.6 s search (`profile.json`,
`solver.invoke`). Real11 geometry: B5 `[730,0,-1096,731,6,-1095]`, B6
`[730,0,-1095,731,20,-1094]` (flush on B5's +Z side), B7 `[729,0,-1095,730,7,-1093]`
(flush on B6's -X side; the goal is a landing on B7).

## How the code works today (read before changing it)

- **Alternatives.** `ConstraintVariants` (`constraints/variants.py`) builds one item
  per (solid in `scope['solidIndices']`, landing segment) in `__init__`, plus route
  guards, required landing regions and contact checks. `_next_variant` walks items
  depth first: `choose_item` prepares every remaining item under the current
  conditioned envelopes and picks the earliest possible phase, then the fewest
  options; `option` asks that item's walk for its next option; the child's rows
  are screened with `analyze` (`condition_envelopes`, `reachability/screen.py`);
  a node with no remaining items is emitted once per effective row key (`seen`).
- **Per-solid walk.** `prepare_encounter` (`constraints/encounters.py`) takes the
  six faces of the solid's expanded box (`faces`, `constraints/avoidance.py`) and,
  for every tick and Y/X/Z sweep in the segment, records the phase as covered
  (ruled out by `block_interaction`), uncertain (named case probe), vertically clear
  (a Y face separates), or possible with one option per surviving face (rows at
  `phase_states(tick, phase, axis)` from `face_row`). Phase code `at = 3*tick +
  index('YXZ')`. `encounter_option` enumerates face walks over the possible phases
  (`_continuation`: simple walk; no direct switch between opposite faces of one
  axis without a vertical gap), skipping phases the rows already entail, and records
  `spans = [[first_at, last_at, face_index], ...]`; `describe` turns them into a
  passage (`faces`, `switchPhases`, `phaseSpans`, `blockId`, `solidIndex`, kind and
  blockage counts).
- **Solving.** `solve_contact_branches` (`search/repair.py`) pulls variants
  (`initial_variants`, or `ConstraintVariants.for_plan` sources created when scene
  validation names a colliding solid), queues them by score, solves each with
  `find_route` (`search/ordered_route.py`) and audits the result. Without
  `rank_equal_cost` it returns at the first accepted alternative; with it, it solves
  every alternative and keeps the best by `candidate_rank` (`search/ranking.py`:
  ticks, then objective). Every accepted alternative is saved in
  `acceptedVariants`.
- **Frames.** `search/dfs.py` `score` sets, for anchor routes,
  `stopOnFeasible = first_witness = not final_frame(f)` and
  `rankEqualCost = final_frame(f)`: intermediate frames stop at the first feasible
  alternative and the first feasible numerical seed (objective = the guide
  objective, `route['guideObjective']`); goal frames optimize and rank every
  alternative. Each saved accepted variant becomes a sibling child route (`score`,
  the `acceptedVariants` loop). Frames with unsolved alternatives are suspended and
  revisited later (`close_frame`, the `suspended`/`refinements` loop).
- **Landing covers.** `decompose` (`spatial/cells.py`) builds player-space free cells
  and per-solid support/ceiling/wall `contact_patches`; `support_components`
  (`spatial/regions.py`) unions support patches with `canonical_cover`;
  `event_routes` publishes them as `surface['cover']`; `contact_regions`
  (`search/events/adapter.py`) turns each cover rectangle into one landing option.
- **Unused:** the physical face catalog `merge_faces` (`spatial/faces.py`), used only
  by `tools/geometry_bounds` and tests.

## Rules every step keeps

- Avoidance options must together cover every free position at every phase. A side
  limited to a face's exposed part is unsound (east of B6 at Z -1094.5 below Y 7 is
  free and only "beyond B7's +X plane" separates it from B7). Maximal free
  rectangles around a union of obstacles (step 1) are exact.
- Minimum free width (user, 2026-11-17): physical gaps between blocks are 0 (flush),
  under 0.6 (the player cannot pass) or at least 0.612, so in player space every
  real free piece is at least 0.012 wide (`MIN_FREE_WIDTH`). Narrower pieces are
  numerical artifacts. Width is extent along a tangent axis: support, ceiling and
  wall patches have zero thickness by construction and stay. Code compares against
  `MIN_FREE_WIDTH/2` (`thin(width)` in `scene/geometry.py`): a real 0.012 gap can
  round to 0.0119999, artifacts are 0 or about 1e-12, so the half cut separates
  them; a strict 0.012 cut would drop real gaps.
- IPOPT unknown counts as infeasible when comparing switch ticks (user, 2026-11-17).
  It stays on the existing unresolved/retry path.
- Commit once per step with its tests and docs. After each step run the solve
  report on Real11 (below) into a new `runs/` directory, compare frames 4 and 8 with
  the baseline, and require the final result (T64, Z -1093.2381026602418, 47/47
  replays) unchanged or better. Run the native gate before any push.

Commands (from `standalone/`):

```sh
export PKC_IPOPT_PYTHON=$PWD/../.venv-guidance/bin/python
export PKC_RUN_TICKS_COMPILER=$PWD/build/native/compiler.json
export OPENBLAS_NUM_THREADS=1 OMP_NUM_THREADS=1
"$PKC_IPOPT_PYTHON" -B -m tools.profiling.run --input cases/real11/physical-anchors.json --output runs/<new> --seconds 300 --profile-mode bookkeeping
PYTHONPATH=src:. "$PKC_IPOPT_PYTHON" -B -m unittest tests.unit.constraints.test_encounters tests.integration.test_profiling
"$PKC_IPOPT_PYTHON" -B tools/native_gate.py --output /Users/arvindmurty/CS/pkc-archive/gates/<new>
python -m tools.docs_index && python -m tools.validate_layout
```

## Implementation order

| Step | Section | Changes frames 4/8 |
|---|---|---|
| 0 | §0 per-request solve seconds | no (measurement) |
| 1 | §3 obstacle footprints builder, plus `MIN_FREE_WIDTH`/`thin` | no (new module) |
| 2 | §4 avoidance on obstacle clusters | yes: 21 → 6 and 7 alternatives expected |
| 3 | §2 minimum width in the landing-cover path | yes: one fewer B5 landing option |
| 4 | §7 switch-tick hill-climb (after the user confirms §7's open points) | yes: about 3 solves per family expected |
| later | §1, §3 stages B-C, §4b, §5, §6, §7b | deferred |

## 0. Per-request solve seconds

- `tools/profiling/runtime.py` `invoke` and `tools/profiling/bookkeeping.py` `solve`:
  add `seconds=result.get('elapsedSeconds')` to `row.update(...)` (the solver
  returns `elapsedSeconds`, `numerics/solver.py`).
- `tools/profiling/trace.py`: `request_owners` already copies request rows; show
  `seconds` in the alternatives table's Solves column ("request 7 feasible, IPOPT
  Solve_Succeeded, 0.18 s") and a per-movement total line after the table.
- Test: extend `test_route_trace_links_alternatives_solves_and_inherited_rows`
  (`tests/integration/test_profiling.py`) with a `seconds` field and its rendering.
- Docs: [solve reports](../RESULTS_AND_DEBUGGING.md#solve-reports).

## 1. Encounter prescreen (deferred)

Above 256 encounters per repair run `choose_item` thrashes its 256-entry
`preparation_cache` (250 encounters 0.045 s, 500 10.9 s, 1000 61.7 s; synthetic
route). Real11's B7 runs build 140, and clusters (step 2) reduce that.

- 1a: prepare each item once under the unconditioned envelopes and drop those with
  no possible or uncertain phase. Sound only if `condition_envelopes` never widens a
  bound; prove that first with a property test over Real11 envelopes and random row
  subsets.
- 1b: a per-scene spatial index to discard far solids; worth it at thousands of
  blocks.

## 2. Minimum free width in the landing-cover path

Real11's B5 landing offers a second cover rectangle `X [731.3, 731.3] × Z
[-1095.3, -1094.7]`: the line where the player's box touches B5's top with zero
overlap beside the B5/B6 east wall. `support_components` joins it to the main
rectangle (they touch at a corner), so `contact_regions` offers two landing options
and the line costs a decision and a screen rejection in every B5 frame.

- `MIN_FREE_WIDTH` and `thin` already exist (step 1).
- `canonical_cover` (`spatial/regions.py`): delete the block after the comment
  "Preserve lower-dimensional free strata" (lines, points) and drop output rectangles
  whose X or Z extent is `thin`. Inherited by `support_components`,
  `free_transit_cover`, `surface_groups`, `refine_support_cells` and the face
  catalog's `_cover`.
- `contact_patches` (`spatial/cells.py`): skip a patch whose extent along either axis
  other than its own `axis` is `thin`.
- `decompose` (`spatial/cells.py`): keep only strata whose two largest extents are
  not `thin` (drops lines and points; keeps face strata, e.g. an exact 1.8 vertical
  slot).
- Tests: replace `test_boundary_lines_points_and_exact_doubles_are_retained`
  (`tests/unit/spatial/test_route_regions.py`) with one asserting lines, points and
  a 1e-15-wide sliver rectangle are dropped. Its last assertion (a box ending at 1
  versus 1.000000000000001 gives different covers) is about exact doubles, not
  slivers, and stays as is. Add a B5/B6-shaped scene whose B5 support cover is one
  rectangle. Keep
  `test_buried_block_has_no_support_from_merely_tangent_strata`
  (`tests/integration/test_geometry_bounds.py`); rerun `tests.unit.spatial` and
  `tests.integration.test_geometry_bounds` and update counts that change.
- Identities: covers enter route, guide and anchor identities through content
  digests (`geometry_key`); there is no version constant to bump. Check a saved run
  directory resumes by rebuilding, not by reusing the old covers.
- Docs: strata wording in [obstacle relevance](OBSTACLE_RELEVANCE.md),
  [layout](../LAYOUT.md) and `docs/REAL11_EXTENDED_GEOMETRY.md`.

## 3. Obstacle footprints: one player-space representation

### What exists

| Code | Space | Builds | Solver use |
|---|---|---|---|
| `decompose`, `contact_patches` (`spatial/cells.py`) | player | free cells, strata, per-solid patches | landing covers |
| `canonical_cover` (`spatial/regions.py`) | X/Z plane | exact rectangle union | under the row above |
| `merge_faces` (`spatial/faces.py`) | physical | exposed faces merged across touching solids | none |
| `faces` (`constraints/avoidance.py`) | player | six faces of one box | avoidance |
| `contact_opportunities` (`movement/contact_profiles.py`), `wall_hit_options` (`constraints/hit_events.py`), `movement/ceiling_generation.py`, `movement/cases.py`, `constraints/contacts.py` | player | contacts from raw faces | movement programs |

### Stage A (step 1): `spatial/footprints.py`

New module; pure geometry, standard library only. Step 1 also adds to
`scene/geometry.py`: `MIN_FREE_WIDTH = 0.012` and `thin(width)` (`width <
MIN_FREE_WIDTH/2`, see the rules above). API:

```python
Footprints(geometry, solid_indices)                     # built per ConstraintVariants; O(n²) clustering, n ≤ 64 solids
Footprints.clusters -> tuple[Cluster]                   # Cluster(id=min(members), members=tuple(solid indices), solids=raw boxes, boxes=expanded boxes)
Footprints.cluster(cluster_id) -> Cluster
Footprints.active(cluster, y_lo, y_hi) -> tuple[int]    # positions k into cluster.members
Footprints.sides(cluster, active) -> tuple[Side]        # cached per (cluster id, active)
Side(key, bounds, owners, region)                       # frozen dataclass
#   bounds: tuple of (axis 'X'|'Z', direction ±1, plane), 1 to 4 entries, sorted
#   owners: solid indices whose expanded edges lie on the bounds' planes
#   region: (x_lo, z_lo, x_hi, z_hi) with ±inf
#   key: bounds (the region is determined by them)
nested(a, b) -> bool                                    # one side's region contains the other's
```

- Boxes are `expanded_box(geometry['solids'][i])` for `i` in `solid_indices` (scoped
  validation passes a subset; out-of-scope solids are ignored, as today). Cluster ids
  are solid indices, so the serialized generator items stay valid when the object is
  rebuilt on resume.
- Clusters: union-find over pairs whose expanded boxes overlap or whose gap is
  `thin` on every axis. Grouping only affects how many alternatives are produced:
  each cluster's sides cover the free space around that cluster, so any grouping is
  sound. Real11: B5, B6 and B7 are one cluster.
- Active members over `[y_lo, y_hi]`: `box[1] < y_hi and y_lo < box[4]` (open,
  matching collision and `block_interaction(..., interior=True)`; the same formula
  covers a point range).
- Sides: every maximal empty axis-aligned rectangle (unbounded allowed) of the plane
  minus the union of the active members' open X/Z rectangles. Algorithm: X
  candidates = all active member X edges plus ±inf, same for Z; for each pair of X
  candidates `xa < xb`, the free Z intervals of the open strip `(xa, xb)` are the
  complement of the open Z intervals of members overlapping it in X (closed, with ±inf
  ends); each free interval `[za, zb]` gives a candidate rectangle. Keep it when it
  is maximal (extending it to the neighbouring X candidate on either side hits an
  active member; Z is maximal by construction) and no finite width is `thin`.
  Bounds: finite `xa` gives `X ≥ xa`, finite `xb` gives `X ≤ xb`, same for Z.
  O(m²·n) for m edges and n members; Real11 clusters have 3 members.
- Every free sweep segment lies in some side: grow the segment to a maximal empty
  rectangle; it is not `thin` because real gaps are at least 0.012.
- Owners of a bound: active members with an expanded edge on that plane whose
  extent along the other axis overlaps the side's region.
- Real11 expected sides. B6 ∪ B7 below Y 7 (B5 inactive above its top Y 6):
  `Z ≤ -1095.3` (owners B6, B7), `X ≥ 731.3` (B6), `X ≤ 728.7` (B7),
  `Z ≥ -1092.7` (B7), `X ≥ 730.3 ∧ Z ≥ -1093.7` (B7, B6). B6 alone above Y 7:
  `Z ≤ -1095.3`, `X ≥ 731.3`, `X ≤ 729.7`, `Z ≥ -1093.7`.
- Tests (`tests/unit/spatial/test_footprints.py`): the Real11 sides above; a ring of
  four boxes with an enclosed pocket (one bounded side); two boxes 0.7 apart
  physically (two clusters); random box sets checked by sampling points: every point
  outside the cross-section lies in some side, no side point lies inside a member's
  open box, every side is maximal.
- Docs: add the module to [architecture](../ARCHITECTURE.md),
  [code map](../CODE_MAP.md) and the [layout](../LAYOUT.md) package table; regenerate
  indexes.

### Stage B (later): contacts from footprint boundaries

Move `contact_opportunities`, `wall_hit_options`, `ceiling_generation`, `cases` and
`constraints/contacts.py` from raw faces to the clusters' exposed boundary edges
(hidden faces yield no contact; coplanar touching faces yield one, mapped back to
their owners for block-named goals). Write its own plan section, with each
consumer's current input and replacement, before starting.

### Stage C (later)

Rebuild `decompose`'s free volume from height bands (the height-sweep construction
in [spatial route research](../research/SPATIAL_ROUTE_RESEARCH.md)); retire
`spatial/faces.py` and move `tools/geometry_bounds --faces` onto footprints, as its
own dead-code commit.

## 4. Avoidance on obstacle clusters

Replace per-solid encounters with per-cluster encounters whose options are the
cluster's sides.

### `constraints/variants.py`

- `__init__`: `self.footprints = Footprints(geometry, scope['solidIndices'])` (not
  serialized; rebuilt from geometry). Items become `dict(clusterId=c.id,
  members=list(c.members), begin, end, owner='+'.join(sorted unique owners[i] for i
  in members))` per cluster and landing segment (owner falls back to `solid:i`),
  replacing the per-solid `dict(solidIndex=i, box=...)` items.
- Everything that tests `'solidIndex' in item` (`prepare_item`, `choose_item`)
  tests `'clusterId' in item`.
- `GENERATION_POLICY = 9`, `STATE_VERSION = 4` (and the error text); in
  `search/repair.py` `repair_context(..., avoidancePolicy=9)`. Old checkpoints do
  not resume across the bump, as with earlier bumps.

### `constraints/encounters.py`

A side dict (the walk's face): `dict(key=<list of [axis, direction, plane]>,
bounds=[dict(axis, direction, plane), ...], owners=[solid indices], region=[x_lo,
z_lo, x_hi, z_hi], axis=<the single bound's axis, 'XZ' for a corner, 'Y' for a
vertical pseudo-side>, direction=<single bound's direction, ±1 for a pseudo-side,
else 0>, plane=<single bound's plane, the pseudo-side's plane, else None>)`. JSON
safe: infinite region ends are stored as `None`. `face_row(bound, t)` already
accepts a bound dict.

- `prepare_encounter(owner, item, envelopes)`:
  - `cluster = owner.footprints.cluster(item['clusterId'])`; `result['sides']` is a
    list of side dicts, unique by key (owners unioned when a key recurs in another
    active set); every index below is into it.
  - Per tick and phase, `bounds = envelopes['phases'][tick][phase]`, `active =
    footprints.active(cluster, *bounds['Y'])`.
  - None active: record `verticalClear` with a pseudo-side: direction +1 and plane
    = max member top when `bounds['Y'][0]` ≥ every member's top, else direction -1
    and plane = min member expanded bottom. The phase is also covered (`certain` =
    [pseudo index]) and gets no options.
  - Covered when `block_interaction(envelopes, cluster.solids[k], tick, (phase,),
    interior=True)['ruledOut']` holds for every active `k`; `certain` = indices of
    the active set's sides whose every bound the phase bounds satisfy (today's
    per-face test).
  - Uncertain ticks unchanged.
  - Options: for each side of the active set, rows = `owner.face_row(bound, t)` for
    each bound and each `t` in `phase_states(tick, phase, bound['axis'])`; exclude
    with `interaction_reachability(envelopes, rows)` as today; cost sums over all
    rows. Options stay keyed by `str(index)`, so cursor keys keep their format.
  - After the loop, entry/exit sides (replacing `support_faces`/`boundary_faces`):
    the active set at the support's top (support event) or at
    `envelopes['states'][tick]['Y']`; its sides whose every bound passes today's
    per-bound test against the support region or state bounds; the pseudo-side
    when nothing is active there.
- `same(item, a, b)`: `a == b` or the two sides' regions are nested
  (`footprints.nested`). Sides of different active sets describe the same place when
  nested: west of B7 below Y 7 (`X ≤ 728.7`) lies inside west of B6 above it
  (`X ≤ 729.7`). Removing members only grows empty rectangles and adding members
  only shrinks them, so every side of one active set is nested with some side of
  the next.
- `_continuation(history, face, same)`: today's rules with equality replaced by
  `same`; a `same` step replaces the last entry instead of appending, so staying
  west while rising over B7 and falling back is one side, not a revisit.
- `encounter_option`: the bridge test (`f != history[-1]`) uses `same`; the
  opposite-face check ("same axis and no gap") becomes "the two sides' regions do
  not intersect and no vertical-clear phase lies between them" (for one box this is
  exactly the opposite-face rule; a corner shared at a point counts as
  intersecting).
- `describe`: collapse consecutive `same` sides into the first one; the entry insert
  compares with `same`. Passage fields: `clusterId`, `members`,
  `blockId=item['owner']`, `faces=[side dicts]`; `phaseSpans` keep exact side keys
  (`[first_at, last_at, key]`); `entryFaces`/`exitFaces` are side dicts. Drop
  `solidIndex`.
- `encounterLog` records `clusterId`, `members`, `owner`, `sides` (instead of
  `solidIndex`, `faces`); `droppedOptions` and `uncertainInteractions` records carry
  `clusterId` and `members` instead of `solidIndex`.

### Passage consumers

- `constraints/avoidance.py`: `gate_timeline` uses, for a corner side (`axis ==
  'XZ'`), the max (first side) or min (second side) of `phase_states` over X and Z,
  and drops `solidIndex` from its records; `blockage_counts` is unchanged (`axis !=
  'Y'`); `block_key` uses `blockId`. `compile_passages`, `passage_y_valid` and
  `validate_passages` only see authored route passages and stay.
- `search/repair.py` (`enrich`, `blockage_usage`, `gate_timeline`, `phaseReferences`)
  needs no other change; run its tests.
- `spatial/event_routes.py` `observed_route`: match features per bound of each side
  (`f['solidIndex'] in passage['members']`, then axis, side, plane as today; a
  pseudo-side matches the members' Y faces with its direction and plane).
- `tools/audit_candidates.py` `interactions`: name passages by `blockId`.
- `tools/profiling/enumeration.py` (and `enumeration_calibrate.py`): key rows and
  captures by `clusterId` instead of `solidIndex`/`box`.
- `tools/profiling/trace.py`: `encounter_trace` labels sides by bounds (`Z-`, `X+`,
  `X+Z+` for a corner, `over`/`under`) from `entry['sides']`; `item_label` and
  `repair_trace` read `clusterId`/`members` instead of `box`; `faceOwners` lists
  each side bound with its owners' block names; `droppedOptions` owner lookup by
  `clusterId`; `side` renders side dicts (corner `X+Z+`); `row_owners` matches by
  bound. Rewrite the legend bullet on sides: sides now belong to an obstacle cluster
  (touching blocks) and never cross a neighbour. Report example: "B5+B6+B7: Z- then
  X- (t58-67)".

### Tests and docs

- `tests/unit/constraints/test_encounters.py`: a flush B6/B7-shaped pair is one item
  and yields no alternative containing a redundant B7 row; extend
  `test_small_exact_paths_are_covered...` with an L-shaped two-box cluster (every
  sampled path clear of both boxes satisfies some variant); a path that stays west
  of the lower box while rising over it and falling back is covered; update the
  existing tests that read `item['faces']` and `solidIndex`.
- `tests/integration/test_profiling.py`: fixtures with cluster passages.
- Docs: [avoidance passages](AVOIDANCE_PASSAGES.md) (encounters are clusters; sides
  are maximal free rectangles; walk rule), [results and debugging](../RESULTS_AND_DEBUGGING.md#solve-reports).
- Expected on Real11 (not measured): frame 4 keeps south then west with the switch
  at t61-66 (6 alternatives); frame 8 keeps those plus south, west, north (7).

### 4b. Reduce across clusters (deferred insurance)

Separate clusters can still emit rows another cluster's rows make redundant. If a
report shows it: when a node completes, drop a cluster's rows when the other rows,
under their conditioned envelopes, still entail every possible phase of that
cluster's item (the walk's `rows_entailed` test), then dedupe on `seen`.

## 5. Propagating screen inside the walk (deferred)

The walk checks rows only against their own tick (`interaction_reachability`); the
full screen runs per finished option. Running `condition_envelopes` as a walk starts
a new side gives the same verdicts earlier and prunes longer spans. Generator time
only (frame 4: 76 options already rejected before solving), about 6.4 ms per call.
Measure before adopting.

## 6. Obstacle order from the free-space graph (deferred)

Choose the free cells a route passes through first (`decompose` cells and portals),
order clusters along them and fix sides once per sequence. Return only if §4 leaves
waste, e.g. long windows where obstacle-blind envelopes make a far side look
reachable.

## 7. Switch-tick hill-climb with optimize calls

Decision (user, 2026-11-17): the objective is unimodal in a switch tick. Group
alternatives into families by how they pass each cluster, and climb each family's
switch ticks with optimized solves instead of solving every tick.

The earlier draft built neighbours inside the generator (shift a span, rebuild its
rows, re-check every item's coverage). That is wrong: rows of items placed after the
shifted cluster were chosen under the old conditioning, so the re-check can reject
the real neighbour, which the walk later emits with adjusted rows and the climb then
skips. The climb below uses only variants the walk emits, so every solved
alternative is one the generator produces today.

### Generator side (`constraints/variants.py`)

- Each child node records `choices = parent choices + [choice]`, where `choice =
  dict(item=chosenItem, sides=<the passage's side keys after describe's collapse>,
  switches=<passage switchPhases // 3>)` for a cluster option and `dict(item,
  key=effective_key(option rows))` for any other item (landing region, route guard,
  contact check), so a different landing rectangle is a different family.
- Emitted variants add `family = digest(sorted [(item, sides or key)])` and
  `switches = tuple(ticks of every cluster choice in item order)`. Nothing else
  changes; the walk, dedupe and screening stay as they are.

### Repair side (`search/repair.py`, `solve_contact_branches`)

- A point is `(family, switches)`. State `families = {family: dict(points={switches:
  dict(rank, held=[saved branches], visited)}, done)}`, saved in `snapshot`
  (version 3; resume accepts only 3). Variants from `repair_sources` get families
  prefixed by their source record's branch key.
- Arrival of a variant: if its point is visited, offer it (as today). Otherwise hold
  it in its point. A family's first point is visited at once, except in
  first-witness mode while another family is climbing: then it is held, and the next
  family starts only after the current one is done (on a later call when the frame
  is revisited).
- Every family solve passes `stop_on_feasible=False` to `find_route` (optimize
  calls, with the guide objective in intermediate frames).
- After a solve, the point's `rank` = best `candidate_rank` of its accepted members,
  `None` for infeasible, unknown, blocked or rejected (unknown also stays on the
  unresolved retry path). If the family has no feasible point, visit the next held
  point in arrival order (today's behaviour). Otherwise let `best` be the best
  feasible point and visit its neighbours: points equal to `best` except one switch
  ±1 tick. A neighbour not held yet is waited for: the loop keeps pulling from the
  generator (it does so whenever the queue is empty). The family is done when every
  neighbour of `best` is visited, or missing with the generator exhausted.
- `acceptedVariants`: a family adds only its best member, when it is done (one
  sibling route per family, `search/dfs.py`).
- First-witness mode (`rank_equal_cost` false): return the family's best when the
  family is done, instead of the first accepted alternative. With
  `rank_equal_cost`: climb every family; the best across families wins as today.

### Open points (user)

1. Decision-node solves (`evaluate_node`, purpose `node`) are first-witness too, and
   their rows are inherited (`commit_arrival` keeps selected walls). Recommendation:
   do not climb there; they gate a decision, and anchor and goal frames climb.
2. Waiting for a missing neighbour can drain the generator in an intermediate frame,
   where today it stops at the first feasible alternative. Measure generation
   seconds per frame after step 2 before deciding whether that matters.

### Report and tests

- `tools/profiling/trace.py`: per movement, a families table: family sides, switch
  ticks tried with objective or "infeasible", chosen ticks.
- Tests: variants carry `family`/`switches` (same family for two switch ticks,
  different families for two landing rectangles); repair loop with a stub generator
  and a stub solver whose objective is unimodal in the switch tick (climbs to the
  peak, never solves the far ticks, treats unknown as infeasible, keeps only the best
  in `acceptedVariants`, waits for a late neighbour).
- Docs: [avoidance passages](AVOIDANCE_PASSAGES.md), [execution reference](../ANCHOR_SEARCH_EXECUTION_REFERENCE.md)
  (intermediate frames optimize within a family), [performance](../PERFORMANCE.md).
- Expected on Real11 (not measured): frame 4 solves t62 (start), t61 (unknown, so
  infeasible) and t63 (worse) for the south/west family instead of six ticks.

### 7b. Keep interactions open until downstream (deferred)

Keep up to two similarly performing families (one if far ahead) and two or three
switch ticks per cluster unresolved into later frames. Deferred: the guide path
usually makes the intermediate objective accurate enough.

Related: [obstacle relevance](OBSTACLE_RELEVANCE.md), [avoidance passages](AVOIDANCE_PASSAGES.md),
[face merging plan](FACE_MERGING_IMPLEMENTATION_PLAN.md).
