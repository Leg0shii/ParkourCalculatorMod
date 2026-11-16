# Adjacent-face merging: implementation plan

2026-11-15. User-directed scope: merge adjacent coplanar faces across all six
orientations. Implementation has not begun. This replaces the former ceiling-only
plan. The work has exactly two parts: detecting mergeable faces and representing
the result for later decisions.

## 1. Detect adjacent faces

**Owner:** add `src/parkour_solver/spatial/faces.py`, alongside the existing
[planar union implementation](../../src/parkour_solver/spatial/regions.py).

1. Enumerate the six physical rectangular faces of each supplied collision box.
   Record the original block ID, solid index, axis, outward normal sign, plane
   coordinate and rectangle on the two tangent axes.
2. Bucket faces by exact `(axis, normal sign, plane coordinate)`. All six
   orientations use this same algorithm. Faces with different planes or opposite
   normals stay separate.
3. Within each bucket, detect overlapping faces or faces sharing an edge segment
   of positive length. A single shared corner does not connect two groups.
   Partial shared edges qualify. Compare physical face geometry, before player
   expansion or numerical clearances; there is no distance tolerance that bridges
   a real gap.
4. Find the connected components of that adjacency graph. Each component is one
   merged face; an isolated face remains a one-member component. Contributions
   can come from distinct blocks or compound-box parts.
5. Compute each component's exact union as a canonical rectangle cover. Reuse
   `canonical_cover` by projecting each orientation to its two tangent axes,
   then mapping its rectangles back to the face plane. Preserve concavities and
   holes. The cover may need several rectangles even though it is one face.

Use this tangent-axis mapping consistently:

| Face normal axis | Tangent axes |
| --- | --- |
| X | Y, Z |
| Y | X, Z |
| Z | X, Y |

Sort buckets, members and rectangle covers for deterministic results. Input box
order must not change the merged geometry. Retain original member references;
merging faces does not merge or resize the physical collision boxes.

**Checks for detection:** exercise all six orientations, complete and partial
edge adjacency, overlapping rectangles, chains of neighbors, gaps, corner-only
contact, opposite normals, different planes, L-shapes and holes. Confirm that
canonical cover equals the union of input faces and is invariant to input order.
For Real11, the B9a/B9b/B10 undersides merge atY9 while their unequal top planes
remain distinct. Other matching side/top faces use the identical algorithm.

## 2. Represent merged faces for later decisions

Use immutable records in the same module:

```text
SourceFace:
    blockId
    solidIndex
    axis, normalSign, plane
    rectangle                  # original face extent on its tangent axes

MergedFace:
    id                         # axis/sign/plane + canonical exact cover
    axis, normalSign, plane
    cover                      # exact union as tangent-axis rectangles
    members                    # SourceFace contributions with original extents

FaceCatalog:
    faces                      # merged faces indexed by ID
    byBlock                    # block ID -> merged-face IDs
    bySolid                    # original solid index -> merged-face IDs
```

Keep each member's original extent, not only a list of block names. A later
query can then determine which blocks contribute to a particular part of a
merged face. That preserves distinctions such as landing on a named block even
when its coplanar face joins a neighbor's face.

A disconnected or holed region is never represented by its bounding rectangle.
`cover` is the geometry; a bounding box, if provided for lookup, is only an index.
Face geometry/ID is independent of source array ordering; original indices and
block IDs remain explicit provenance.

Provide one entry point, `merge_faces(blocks) -> FaceCatalog`, accepting existing
block IDs and actual collision boxes. Later decisions consume the catalog's
orientation, plane, cover and source membership. The representation assigns no
ceiling/wall/landing role; those meanings follow from the decision using a face.
Player expansion and clearances remain properties of those later consumers.

**Checks for representation:** round-trip the catalog without losing exact
coordinates, orientation or membership; verify reverse block/solid lookups;
query points/rectangles belonging only to each source member; and confirm that
original boxes are preserved. Add these checks alongside detection tests in
`tests/unit/spatial/test_faces.py`.
