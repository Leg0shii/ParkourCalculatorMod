# Adjacent-face merging: implementation plan

2026-11-15. Status: **both parts implemented** in
[spatial/surfaces.py](../../src/parkour_solver/spatial/surfaces.py) and the optional
`tools.geometry_bounds --faces` report. Inspected planning
baseline `b535224c`. Scope remains exactly two parts: detecting adjacent faces
across all six orientations, and representing them for later decisions.

Owner: `src/parkour_solver/spatial/surfaces.py` and focused tests in
`tests/unit/spatial/test_surfaces.py`. The representation is geometric:
it does not classify blocks by anchor span or assign ceiling/wall/landing roles.
The two existing contact-dedup findings remain evidence, not additional work in
this plan. No movement-generator, numerical model or DFS rewrite is included.

## 1. Detect adjacent faces

### 1.1 Existing code to reuse and its limits

| Existing owner | Reuse / distinction |
| --- | --- |
| [scene.geometry.normalize_geometry](../../src/parkour_solver/scene/geometry.py) | Produces original solids plus one support entry per solid, retaining sourceBlock, derived part id and solidIndex. Use this input contract; do not invent another block parser. |
| [scene.geometry.box_bounds](../../src/parkour_solver/scene/geometry.py), [schema.number](../../src/parkour_solver/schema.py) | Existing positive-volume / finite-number validation. Reject ambiguous member-index mappings. |
| [spatial.regions.canonical_cover](../../src/parkour_solver/spatial/regions.py) | Exact planar rectangle union, currently encoded on an X/Z plane. Project all orientations into that encoding through one adapter. It preserves holes and seams correctly but does not choose the new adjacency policy. |
| [spatial.cells.subtract_open / intersection](../../src/parkour_solver/spatial/cells.py) | Existing subtraction/intersection primitives. Use a two-dimensional embedding for face clipping; see the outward-side rule below. |
| [spatial.cells.contact_patches / decompose](../../src/parkour_solver/spatial/cells.py) | Existing player-expanded, free-cell-clipped contacts. They are not raw physical faces, so they cannot be copied as this catalog's input. Do not run full 3D decomposition just to merge faces. |
| [spatial.event_routes.surface_groups](../../src/parkour_solver/spatial/event_routes.py) | Existing coplanar walkable-surface grouping. It requires positive-area overlap in player-space and is not an all-orientation physical-face adjacency implementation. Keep that behavior intact. |

The maintained collision scene allows at most64 sub-boxes in the solver, although
the geometry utility need not depend on that search cap. All six directions must
follow one implementation, using deterministic plane buckets and rectangle work.

### 1.2 Input, coordinates and orientation

Use `merge_faces(scene) -> FaceCatalog`, where `scene` already has normalized
`collisionScene.solids` and the matching `blocks` entries. Read geometry and
provenance only. No start state, goal, anchors, budgets or player dimensions are
needed. The geometry report can normalize a raw input with the existing helper
before calling this API. Avoid `prepare_scene`, which unnecessarily requires a
complete task with start/goal and at least two supports.

For each solid `[xmin,ymin,zmin,xmax,ymax,zmax]`, enumerate six SourceFace records.
Use world axes0/1/2 for X/Y/Z and outward `normalSign`−1/+1. The physical plane is
that axis's min for−1 or max for+1. Store a rectangle in tangent coordinates as
`[uMin,vMin,uMax,vMax]`:

| Normal axis | Tangent axes u,v |
| --- | --- |
| X | Y,Z |
| Y | X,Z |
| Z | X,Y |

A top is Y/+1; an underside is Y/−1. Existing contact-event `direction` describes
motion into a surface and has the opposite sign for an ordinary approaching
contact. Do not copy that field into `normalSign`. Existing avoidance face signs
use the outward convention. Neither convention changes in this feature.

Use exact stored numerical values for equality and adjacency; no tolerance may
bridge a real gap or join different planes. Normalize equivalent finite numeric
encodings for catalog identity, including integer versus equal float and signed
zero, without rounding distinct coordinates together. Reject a coordinate if
conversion to the chosen finite-double representation would change its value.
One-box and empty geometry utility cases return valid catalogs without task
requirements. No faces are created on geometry-coverage bounds.

### 1.3 Implemented boundary rule: remove hidden portions before grouping

The earlier sketch merged all raw box faces. That would retain internal seams
and could connect groups through faces buried inside another solid.
MergedFace represents the **exposed physical boundary of the supplied solid
union**. Retain every raw SourceFace and its exposed cover for provenance.

Exposure is physical geometry, not a statement that the player's body can reach
or fit beside that face. A nearby overhang can obstruct the player without
covering the physical face itself; existing later checks remain necessary.

For a source face at axis a / plane p, another solid j covers its outward side
when the following exact normal-axis condition holds:

| Source normal | Other solid occupies the outward side if |
| --- | --- |
| +1 | min_j[a] ≤ p < max_j[a] |
| −1 | min_j[a] < p ≤ max_j[a] |

For qualifying solids, subtract their positive-area tangent-rectangle overlap
from the source rectangle. Apply every such blocker, then canonicalize the
remaining pieces. These asymmetric equalities handle boxes meeting exactly at
a face, without an arbitrary epsilon offset:

- Opposite faces at an internal seam are covered and disappear from exposed
  groups; their raw source records remain.
- A face partly covered by a taller/overlapping solid is clipped, possibly into
  several disconnected pieces.
- Coincident outward faces of duplicate/overlapping boxes retain all original
  contributors; do not pick one owner arbitrarily.
- A fully buried face has an empty exposed cover and no merged-face membership.

Do not directly call 3D `subtract_open` with the physical neighboring box at a
shared boundary plane: it treats that plane as outside the open box and would
miss a touching internal seam. First select blockers using the table, then use
one planar-subtraction adapter: embed a rectangle at synthetic Y0 and its cutter
with synthetic Y extent[−1,1], subtract, project back, and keep positive-area
pieces. These synthetic coordinates are only a 2D algorithm encoding.

Standalone lines/points are not face components. Retain the closures of surviving
area patches so their edges are represented. The shared `canonical_cover` drops
lines, points and slivers narrower than the minimum free width, and the cell
decomposition keeps only strata whose two largest extents are not that thin.

### 1.4 Adjacency, exact cover and deterministic groups

1. Bucket exposed rectangle contributions by exact `(axis, normalSign, plane)`.
2. For two rectangles, compute tangent overlap lengths du,dv. They connect iff
   `du ≥ 0 and dv ≥ 0 and (du > 0 or dv > 0)`: positive-area overlap or a shared
   edge segment of positive length. A single shared corner stays separate.
3. Find edge-connected components using these rectangles as graph vertices,
   **not whole SourceFace records**. One source face can be split by occlusion
   into pieces that belong to different merged faces.
4. Canonicalize the union inside each component with the shared planar adapter.
   A merged face can be nonconvex or have holes. `cover` is an exact rectangle
   union, not necessarily a minimal-size rectangle packing.
5. Intersect each contributing source's exposed pieces with the component and
   retain this component-specific contribution. Remove internal encoding seams
   from the geometric cover without erasing member boundaries from provenance.
6. Sort buckets, components, covers and member records before assigning IDs and
   building lookups. Isolated exposed faces remain singleton groups.

One shared edge of even very small positive length qualifies. Opposite normals,
nearby unequal planes, a true gap, or corner-only contact do not. A transitive
chain joins one group. Internal source boundaries need not survive as cuts in
that group's cover, since the source contribution map preserves them separately.

Start with pair comparisons inside each plane bucket and a standard component
walk/union-find. With F≤6N raw faces, initial occluder selection is O(FN); rectangle
fragmentation and adjacency determine the remaining work. Avoid allocating a full
map-wide grid or requiring a polygon library. Add an index only if measured need
justifies it. Do not silently truncate faces/fragments. A build budget interruption
raises before publishing the catalog; no partially complete catalog is usable.

### 1.5 Concrete Real11 and generic checks

Real11 boundary examples from direct geometry inspection:

- B9a/B9b/B10 undersides join atY9 with X[730,731], Z[−1093,−1090]. Their
  unequal top planes10.5/9.5/10 stay separate.
- B9a's +Z face atZ−1092 is covered by B9b over Y[9,9.5]; its exposed portion
  is Y[9.5,10.5]. B9b's corresponding −Z face is entirely internal.
- B10's −Z face atZ−1091 exposes only Y[9.5,10]; B9b covers its lower part.
- B13's two stair boxes share physical owner b13 but retain distinct part IDs.
  Hidden internal riser/base portions must not become candidate exposed faces.

[Planning probe](../../research/real11-extension/face-detection-plan-probe.json)
records the exploratory boundary calculation. It is not production implementation
or evidence that a player can execute a route on these faces.

Detection tests belong in `tests/unit/spatial/test_surfaces.py`:

| Case | Required result |
| --- | --- |
| All six orientations / axis permutations | Identical tangent-plane algorithm and correctly mapped normals/planes. |
| Full/partial edge adjacency, overlap, transitive chain | One exact connected cover, all contributions retained. |
| Corner-only contact, tiny real gap, unequal planes | Separate groups; use nextafter examples to catch accidental epsilon merging. |
| Opposite normals / boxes sharing a full face | No normal mixing; internal interfaces are hidden. |
| Partial overlap, containment, stacked boxes, duplicate boxes | Correct clipping, empty covered faces and preserved duplicate-owner provenance. |
| L-shape, ring/hole, one face split into two | No filled holes, no bounding-rectangle substitution, correct separate components. |
| Compound boxes / different block names | Geometry merges where appropriate while source/part identity remains queryable. |
| Reordered boxes, split equivalent geometry, numeric encoding | Same exposed geometry IDs; provenance/index records follow the actual input. |

Add a small independent oracle using original-box occupancy on the finite
coordinate arrangement: sample each open tangent cell and its inward/outward
normal intervals using exact rational arithmetic for input doubles. A returned
oriented patch must have solid inward and empty outward. Compare the complete
catalog cover to that oracle on small generated scenes. This checks boundary
coverage without duplicating the proposed subtraction/adjacency algorithm.

The first tested implementation checkpoint is `753cfd2a`: geometry builder and
immutable records,12 new detection tests and33 combined geometry/package checks.
The completed representation uses this same detector; no parallel path remains.

## 2. Represent merged faces for later decisions

### 2.1 Record layout and explicit source membership

Use frozen records with tuple-valued geometry in the same module. Read-only lookup
maps may be derived once internally; a frozen dataclass containing an exposed
mutable dict would not be an immutable catalog. Use explicit serialization rather
than generic copying of private lookup structures.

```text
SourceSolid:
    solidIndex                 # original scene-array index
    blockId                    # sourceBlock, e.g. b13
    partId                     # normalized support id, e.g. b13#0
    box                        # original physical AABB

SourceFace:
    key = (solidIndex, axis, normalSign)
    plane, tangentAxes
    originalRect               # full original physical face
    exposedCover               # all surviving pieces, possibly empty/disconnected

FaceContribution:
    sourceFaceKey
    cover                      # only this merged component's pieces

MergedFace:
    id                         # geometric identity below
    axis, normalSign, plane, tangentAxes
    cover                      # exact physical union of its contributions
    contributions              # component-specific source coverage

FaceCatalog:
    version, coordinateSpace = physical_world_faces
    sceneKey, catalogId
    solids, sourceFaces, faces
    byBlock, byPart, bySolid, bySourceFace   # derived reverse lookups
```

Both raw and exposed source extents matter. Keeping only owner names cannot
answer which block contributed a particular face region; keeping a source's
entire exposed cover in every component could introduce disconnected geometry
into the wrong face. The component-specific contribution prevents that error.

Catalog invariants:

- Each MergedFace cover equals the union of its contributions.
- Each contribution lies in its referenced SourceFace exposedCover and shares
  its orientation/plane.
- Each source exposedCover equals the union of that source's contributions over
  all merged faces. Fully hidden faces have an empty reverse lookup.
- Original physical boxes and source/part identities remain available exactly.
- A cover rectangle is geometry, not a new physical collision box.

### 2.2 Stable IDs, lifetime and numeric identity

Reuse [geometry_key](../../src/parkour_solver/spatial/regions.py) on a normalized,
canonical payload. Face ID includes representation version, axis, normal sign,
physical plane and exact canonical cover. It excludes original solid array order,
owner labels, anchor/task state and transient build timings.

`sceneKey` fingerprints the original geometry/provenance snapshot used by the
catalog. `catalogId` fingerprints the serialized versioned catalog content.
Reordering input can preserve geometric face IDs while changing source indices,
derived part IDs or catalog provenance. That is expected; a geometric face ID
alone never establishes that two complete scene/member mappings are identical.
Adding a different block with an identical face changes provenance even if the
face geometry is unchanged.

Build once for a static geometry snapshot and share the immutable catalog.
Changing coordinates, physical membership or source mappings requires rebuilding
it. No per-anchor recomputation, player-size-specific catalog, mutable-object-ID
cache or global cache framework is needed.

### 2.3 Minimal API for later decisions

Provide these operations on the catalog; require explicit coordinate/boundary
conventions and return immutable records/tuples:

```text
merge_faces(normalized_scene, *, check_budget=None) -> FaceCatalog
catalog.face(face_id) -> MergedFace
catalog.faces_for_block(block_id) -> tuple[face_id]
catalog.faces_for_solid(solid_index) -> tuple[face_id]
catalog.contributors_at(face_id, uv, *, boundary) -> tuple[sourceFaceKey]
catalog.contributors_over(face_id, rect, *, positive_area=True) -> tuple[sourceFaceKey]
catalog.cover_for_sources(face_id, sourceFaceKeys) -> rectangle cover
catalog.to_dict() -> independent JSON-compatible value
FaceCatalog.from_dict(value, *, expected_scene_key=None) -> validated catalog
```

- `uv` and `rect` are physical tangent coordinates, not player-center bounds.
- A point on a shared source boundary may report both contributors under closed
  membership. That is not by itself proof of positive-area player support.
- `contributors_over` uses each contribution's exact cover, not only a merged
  bounding rectangle. A query in a hole returns no contributor.
- `cover_for_sources` can split a face or produce an empty result. It selects
  contributions in the **same physical scene**; it does not remove other blocks
  from the world or reveal their previously hidden neighbor faces.
- If a later caller actually changes the modeled physical-solid set, it needs
  a catalog built for that geometry subset. Filtering membership from a full-scene
  catalog is not equivalent to recomputing exposure after removing a blocker.

The module returns geometry and membership. It does not choose a movement type,
assign a contact tick, create a constraint alternative, or decide that landing
on any contributor satisfies landing on one named contributor.

### 2.4 Physical faces versus player/contact geometry

The catalog is independent of player width/height. Current
[expanded_box](../../src/parkour_solver/scene/geometry.py) and contact builders
already transform physical boxes into player-feet collision coordinates. Keep
that transformation with those consumers rather than embedding a second physics
implementation in the physical catalog.

A point-player physical surface and its player-expanded contact domain are not
interchangeable. Body expansion can bridge a physical gap between two catalog
faces; that does not merge their physical IDs. Clearance applied to a source
patch before union need not equal clearance applied to an entire merged cover.
Member contributions preserve the information a later consumer needs to apply
its actual rule. Nearby solids may further clip the player-valid region.

Movement events and full-scene checks carry the member `boxes` of a contact; a
merged face is never passed as a fictitious AABB or through an arbitrary first
member. Plane-only dedup does not preserve coverage, so combination keys include
the surface and regions.
Likewise, geometric merging does not assert equal friction, contact behavior or
block-specific obligations if later APIs introduce such properties.

### 2.4.1 Player surfaces

`scene_geometry(collision_scene)` returns the memoized `SceneGeometry` of
`scene['collisionScene']` (solids and optional bounds); it never rides inside the
scene dict. Besides obstacle clusters and sides, it serves
`player_surfaces(indices=None)`: for each modeled solid, the six faces of its
expanded box, clipped to the player-space coverage bounds when present, minus the
open tangent interior of every other modeled expanded box whose open interior
contains the plane. The pieces are unioned per plane with `canonical_cover` and
split into edge-connected `Surface`s (`key`, `axis`, `sign`, `plane`,
`tangent_axes`, `cover`, per-solid `contributions`, `members`, `cluster`). Thin
pieces are dropped; a floor and an underside on one exact-height plane both
survive; holes and concavities are never filled. Over the whole scene the covers
equal the cell decomposition's contact patches per plane and per solid.

### 2.4.2 Contact options

`contact_options(axis, sign, *, clearance, members)` gives finite contact domains,
one `ContactOption(surface, plane, members, boxes, regions)` per connected surface.
`sign` is the expanded face's outward normal, minus the motion direction (a landing
uses the top, +1; a ceiling hit the bottom, -1). Only `members` contribute, every
solid of the scene clips (coverage bounds do not), and the union is eroded by the
clearance along its horizontal tangent axes (the union of the shrunk maximal
rectangles), so the seam between touching members stays inside one region. Ceiling
generators pass the offered blocks' solids and merge across them.

### 2.5 Serialization, inspectability and acceptance

Use version1 for this new catalog only. Preserve exact coordinates in JSON with
`allow_nan=False`; serialize records canonically and rebuild reverse lookup maps
on load. Validate versions, source references, face/contribution orientation and
coverage invariants, hashes and an expected scene key when supplied. Returned
JSON values and loaded catalogs must not share mutable state with the live one.
No DFS, repair or SQLite checkpoint schema change is needed just to expose this
standalone geometry representation.

Extend the existing [geometry_bounds tool](../../tools/geometry_bounds.py) with
an optional `--faces` report. It calls `merge_faces` on the already normalized
geometry and includes orientation, plane, exact cover and source/part membership.
Keep the current bounds-only output contract when that option is absent. No
new Real11-only implementation or new standalone script is required.

Include simple counts: original solids/raw faces, fully/partially hidden source
faces, exposed fragments, merged faces and final cover rectangles. They help
explain the result; a lower rectangle count is not itself proof of correctness
or a measured reduction in numerical calls. Record face geometry for all six
orientations, rather than only a ceiling diagram.

Representation tests cover:

1. JSON round-trip, content/scene validation and independent mutable exports.
2. Reverse lookups for source blocks, compound parts, solids and hidden faces.
3. Shared-face subregions unique to each owner, overlapping contributions, holes
   and member selection that disconnects a previously connected cover.
4. A partially hidden source split across two merged faces, with no contribution
   leaking into the other component.
5. Reordered input preserving geometry IDs while provenance remains correct;
   changed geometry/source mappings invalidating the appropriate catalog identity.
6. `--faces` report agreement with the library catalog, current bounds-only report
   compatibility, and unchanged original geometry input.

Finish part2 with focused geometry/report tests plus the existing cell/region
and immutable-domain suites, package/layout checks and documentation links.
This work is standard-library geometry; native/long numerical gates are needed
only if implementation actually changes those consumers. Commit the complete
representation/report feature with its tests/docs and remove temporary probes.
The result is a tested catalog available for later decision work, not a claim
that the current solver has started consuming merged faces.

Implemented usage (normalized collision geometry only):

```python
from parkour_solver.spatial.surfaces import FaceCatalog, merge_faces

catalog = merge_faces(scene)
for face_id in catalog.faces_for_block('b9b'):
    face = catalog.face(face_id)
    print(face.axis, face.normal_sign, face.plane, face.cover)
saved = catalog.to_dict()
restored = FaceCatalog.from_dict(saved, expected_scene_key=catalog.scene_key)
```

Loading validates the hash and rebuilds once from the stored original solids to
check exposure, canonical geometry and membership, including self-consistent
but incorrect edited catalogs. Lookups use prebuilt read-only indexes; they do
not rehash or rebuild the scene. Open point membership handles internal cover
encoding seams as interior. Immutable catalog deep copies share the catalog;
JSON exports are independent mutable values.

Completion validation:20 face tests,6 bounds/report tests and the existing
cell/region/event-domain/package checks,54 tests total with no skips. The
[Real11 validation record](../../research/real11-extension/face-catalog-validation.json)
records exact input/source hashes and static checks. Its24 solids/144 raw faces
produce102 merged faces with147 cover rectangles;15 source faces are fully hidden
and16 partially hidden. The old bounds report is unchanged after removing the
two optional face fields. These counts do not imply fewer numerical calls.
