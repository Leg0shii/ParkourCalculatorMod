"""Scene surfaces: the physical face catalog, player-space surfaces and obstacle clusters.

One `SceneGeometry` per collision scene serves every interaction type. The
physical catalog merges exposed world faces across coplanar neighbours. Player
surfaces do the same for the faces the player centre touches. A cluster is a set
of expanded solids the player cannot pass between; at a given height its active
members' X/Z rectangles form a union, and the sides are every maximal empty
rectangle of the plane outside that union. Every free position lies in some side,
and a face two flush solids share is one side, not two.
"""
import functools
import itertools
import math
from collections.abc import Mapping
from dataclasses import dataclass, field
from types import MappingProxyType
from typing import ClassVar

from parkour_solver.scene.geometry import BODY_HEIGHT, MIN_FREE_WIDTH, box_bounds, expanded_box, thin
from parkour_solver.scene.route import HALF_WIDTH
from parkour_solver.schema import number
from parkour_solver.spatial.cells import subtract_open
from parkour_solver.spatial.regions import canonical_cover, geometry_key
VERSION = 1
COORDINATE_SPACE = 'physical_world_faces'
TANGENT_AXES = ((1, 2), (0, 2), (0, 1))


@dataclass(frozen=True)
class SourceSolid:
    solid_index: int
    block_id: str
    part_id: str
    box: tuple


@dataclass(frozen=True)
class SourceFace:
    key: tuple
    plane: float
    tangent_axes: tuple
    original_rect: tuple
    exposed_cover: tuple


@dataclass(frozen=True)
class FaceContribution:
    source_face_key: tuple
    cover: tuple


@dataclass(frozen=True)
class MergedFace:
    id: str
    axis: int
    normal_sign: int
    plane: float
    tangent_axes: tuple
    cover: tuple
    contributions: tuple


@dataclass(frozen=True)
class FaceCatalog:
    """Immutable physical geometry with exact source-contribution lookups."""
    version: ClassVar[int] = VERSION
    coordinate_space: ClassVar[str] = COORDINATE_SPACE
    scene_key: str
    solids: tuple
    source_faces: tuple
    faces: tuple
    catalog_id: str = field(init=False)
    by_block: Mapping = field(init=False, repr=False, compare=False)
    by_part: Mapping = field(init=False, repr=False, compare=False)
    by_solid: Mapping = field(init=False, repr=False, compare=False)
    by_source_face: Mapping = field(init=False, repr=False, compare=False)
    _faces_by_id: Mapping = field(init=False, repr=False, compare=False)

    def __post_init__(self):
        indexes = [dict((s.block_id, set()) for s in self.solids),
                   dict((s.part_id, set()) for s in self.solids),
                   dict((s.solid_index, set()) for s in self.solids),
                   dict((s.key, set()) for s in self.source_faces)]
        for face in self.faces:
            for contribution in face.contributions:
                key = contribution.source_face_key
                source = self.solids[key[0]]
                for index, name in zip(indexes, (source.block_id, source.part_id, source.solid_index, key)):
                    index[name].add(face.id)
        for name, index in zip(('by_block', 'by_part', 'by_solid', 'by_source_face'), indexes):
            object.__setattr__(self, name, MappingProxyType({k: tuple(sorted(v)) for k, v in sorted(index.items())}))
        object.__setattr__(self, '_faces_by_id', MappingProxyType({f.id: f for f in self.faces}))
        object.__setattr__(self, 'catalog_id', 'face-catalog:' + geometry_key(self._payload()))

    def __deepcopy__(self, memo):
        return self

    def face(self, face_id):
        return self._faces_by_id[face_id]

    def faces_for_block(self, block_id):
        return self.by_block[block_id]

    def faces_for_solid(self, solid_index):
        if type(solid_index) is not int:
            raise ValueError('Solid index must be an integer')
        return self.by_solid[solid_index]

    def contributors_at(self, face_id, uv, *, boundary):
        """Return source-face keys at a physical tangent point (open or closed)."""
        if boundary not in ('open', 'closed'):
            raise ValueError('Boundary must be open or closed')
        point = _coordinates(uv, 2)
        return tuple(c.source_face_key for c in self.face(face_id).contributions
                     if _contains_point(c.cover, point, boundary))

    def contributors_over(self, face_id, rect, *, positive_area=True):
        """Query the actual member cover; a bounding rectangle cannot fill holes."""
        if type(positive_area) is not bool:
            raise ValueError('positive_area must be boolean')
        rect = _coordinates(rect, 4)
        if rect[0] > rect[2] or rect[1] > rect[3]:
            raise ValueError('Query rectangle has reversed bounds')
        return tuple(c.source_face_key for c in self.face(face_id).contributions
                     if any(_overlap(r, rect, positive_area=positive_area) is not None for r in c.cover))

    def cover_for_sources(self, face_id, source_face_keys):
        """Select contributions in this scene; do not reveal hidden neighbor faces."""
        selected = set()
        for value in source_face_keys:
            if (not isinstance(value, (list, tuple)) or len(value) != 3
                    or any(type(v) is not int for v in value)):
                raise ValueError('Source-face key must contain three integers')
            key = tuple(value)
            if key not in self.by_source_face:
                raise KeyError(key)
            selected.add(key)
        return _cover(r for c in self.face(face_id).contributions
                      if c.source_face_key in selected for r in c.cover)

    @property
    def statistics(self):
        return dict(solids=len(self.solids), rawFaces=len(self.source_faces),
            fullyHiddenSourceFaces=sum(not f.exposed_cover for f in self.source_faces),
            partiallyHiddenSourceFaces=sum(bool(f.exposed_cover) and f.exposed_cover != (f.original_rect,)
                                          for f in self.source_faces),
            exposedRectangleContributions=sum(len(f.exposed_cover) for f in self.source_faces),
            mergedFaces=len(self.faces), coverRectangles=sum(len(f.cover) for f in self.faces))

    def _payload(self):
        return dict(version=VERSION, coordinateSpace=COORDINATE_SPACE, sceneKey=self.scene_key,
            solids=[dict(solidIndex=s.solid_index, blockId=s.block_id, partId=s.part_id, box=list(s.box))
                    for s in self.solids],
            sourceFaces=[dict(key=list(s.key), plane=s.plane, tangentAxes=list(s.tangent_axes),
                originalRect=list(s.original_rect), exposedCover=[list(r) for r in s.exposed_cover])
                for s in self.source_faces],
            faces=[dict(id=f.id, axis=f.axis, normalSign=f.normal_sign, plane=f.plane,
                tangentAxes=list(f.tangent_axes), cover=[list(r) for r in f.cover],
                contributions=[dict(sourceFaceKey=list(c.source_face_key), cover=[list(r) for r in c.cover])
                               for c in f.contributions]) for f in self.faces])

    def to_dict(self):
        """Export an independent JSON value; lookup maps are derived, not serialized."""
        return dict(self._payload(), catalogId=self.catalog_id)

    @classmethod
    def from_dict(cls, value, *, expected_scene_key=None):
        """Validate content and rebuild once, including physical exposure invariants."""
        if (not isinstance(value, dict) or type(value.get('version')) is not int
                or value['version'] != VERSION or value.get('coordinateSpace') != COORDINATE_SPACE):
            raise ValueError('Unsupported physical face catalog')
        try:
            payload = {k: v for k, v in value.items() if k != 'catalogId'}
            if value.get('catalogId') != 'face-catalog:' + geometry_key(payload):
                raise ValueError('Face catalog content hash does not match')
            solids = value['solids']
            if not isinstance(solids, list) or any(not isinstance(s, dict) for s in solids):
                raise ValueError('Invalid face catalog sources')
            scene = dict(collisionScene=dict(solids=[s['box'] for s in solids]),
                blocks=[dict(id=s['partId'], sourceBlock=s['blockId'], solidIndex=s['solidIndex']) for s in solids])
            rebuilt = merge_faces(scene)
            if expected_scene_key is not None and rebuilt.scene_key != expected_scene_key:
                raise ValueError('Face catalog belongs to a different scene')
            if rebuilt.to_dict() != value:
                raise ValueError('Face catalog geometry or provenance does not match its sources')
            return rebuilt
        except (KeyError, TypeError, OverflowError) as error:
            raise ValueError('Malformed face catalog') from error


def _coordinate(value):
    try:
        number(value, 'face coordinate')
        result = float(value)
    except (OverflowError, TypeError) as error:
        raise ValueError('Face coordinate must be a finite exact double') from error
    if result != value:
        raise ValueError('Face coordinate cannot be converted to a double exactly')
    return result if result else 0.0


def _coordinates(values, size):
    if not isinstance(values, (list, tuple)) or len(values) != size:
        raise ValueError('Expected ' + str(size) + ' physical tangent coordinates')
    return tuple(_coordinate(v) for v in values)


def _contains_point(cover, point, boundary):
    u, v = point
    if boundary == 'closed':
        return any(r[0] <= u <= r[2] and r[1] <= v <= r[3] for r in cover)
    # Interior of the union, including internal rectangle-encoding seams.
    # Four covered local quadrants certify a neighborhood without an epsilon.
    return all(any((r[0] <= u < r[2] if du > 0 else r[0] < u <= r[2])
                   and (r[1] <= v < r[3] if dv > 0 else r[1] < v <= r[3]) for r in cover)
               for du in (-1, 1) for dv in (-1, 1))


def _sources(scene, check_budget):
    if not isinstance(scene, dict):
        raise ValueError('Face merging requires a normalized collision scene')
    geometry = scene.get('collisionScene')
    if not isinstance(geometry, dict) or not isinstance(geometry.get('solids'), list):
        raise ValueError('Face merging requires a normalized collision scene')
    blocks = scene.get('blocks')
    if not isinstance(blocks, list) or len(blocks) != len(geometry['solids']):
        raise ValueError('Every solid requires one source mapping')
    mappings, parts = {}, set()
    for block in blocks:
        _check(check_budget)
        if not isinstance(block, dict):
            raise ValueError('Invalid solid source mapping')
        index = block.get('solidIndex')
        owner, part = block.get('sourceBlock', block.get('id')), block.get('id')
        if (type(index) is not int or not 0 <= index < len(blocks) or index in mappings
                or not isinstance(owner, str) or not owner
                or not isinstance(part, str) or not part or part in parts):
            raise ValueError('Invalid or ambiguous solid source mapping')
        mappings[index] = owner, part
        parts.add(part)
    result = []
    for index, box in enumerate(geometry['solids']):
        _check(check_budget)
        if not isinstance(box, (list, tuple)):
            raise ValueError('A face source requires a collision box')
        values = [_coordinate(v) for v in box]
        box_bounds(values, 'face source box')
        result.append(SourceSolid(index, *mappings[index], tuple(values)))
    return tuple(result)


def _embed(rect):
    return rect[0], 0.0, rect[1], rect[2], 0.0, rect[3]


def _project(box):
    return box[0], box[2], box[3], box[5]


def _cover(rectangles):
    return tuple(_project(b) for b in canonical_cover(_embed(r) for r in rectangles))


def _overlap(a, b, *, positive_area=True):
    result = max(a[0], b[0]), max(a[1], b[1]), min(a[2], b[2]), min(a[3], b[3])
    if positive_area:
        return result if result[0] < result[2] and result[1] < result[3] else None
    return result if result[0] <= result[2] and result[1] <= result[3] else None


def _adjacent(a, b):
    overlap = _overlap(a, b, positive_area=False)
    return overlap is not None and (overlap[0] < overlap[2] or overlap[1] < overlap[3])


def _check(check_budget):
    if check_budget is not None:
        check_budget()


def _source_faces(solids, check_budget):
    boxes = sorted({s.box for s in solids})
    result = []
    for source in solids:
        box = source.box
        for axis, (u, v) in enumerate(TANGENT_AXES):
            rect = box[u], box[v], box[u+3], box[v+3]
            for sign in (-1, 1):
                _check(check_budget)
                plane = box[axis if sign < 0 else axis+3]
                pieces = [_embed(rect)]
                for other in boxes:
                    _check(check_budget)
                    covered = (other[axis] <= plane < other[axis+3] if sign > 0
                               else other[axis] < plane <= other[axis+3])
                    cut = other[u], other[v], other[u+3], other[v+3]
                    if not covered or _overlap(rect, cut) is None:
                        continue
                    cutter = cut[0], -1.0, cut[1], cut[2], 1.0, cut[3]
                    remaining = []
                    for piece in pieces:
                        _check(check_budget)
                        remaining.extend(b for b in subtract_open(piece, cutter)
                                         if b[0] < b[3] and b[2] < b[5])
                    pieces = remaining
                    if not pieces:
                        break
                exposed = _cover(_project(b) for b in pieces)
                result.append(SourceFace((source.solid_index, axis, sign), plane,
                                         (u, v), rect, exposed))
    return tuple(result)


def _components(fragments, check_budget=None):
    """Edge-connected groups of (owner, rect) fragments on one plane, as owner -> rects."""
    pending, result = set(range(len(fragments))), []
    while pending:
        _check(check_budget)
        first = min(pending)
        pending.remove(first)
        work, component = [first], []
        while work:
            at = work.pop()
            component.append(at)
            for other in sorted(pending):
                _check(check_budget)
                if _adjacent(fragments[at][1], fragments[other][1]):
                    pending.remove(other)
                    work.append(other)
        members = {}
        for index in component:
            key, rect = fragments[index]
            members.setdefault(key, []).append(rect)
        result.append((_cover(fragments[i][1] for i in component), members))
    return result


def _merged_faces(sources, check_budget):
    buckets = {}
    for source in sources:
        _, axis, sign = source.key
        buckets.setdefault((axis, sign, source.plane), []).extend(
            (source.key, rect) for rect in source.exposed_cover)
    result = []
    for (axis, sign, plane), fragments in sorted(buckets.items()):
        for cover, members in _components(fragments, check_budget):
            contributions = tuple(FaceContribution(key, _cover(rects))
                                  for key, rects in sorted(members.items()))
            identity = 'merged-face:' + geometry_key([VERSION, axis, sign, plane, cover])
            result.append(MergedFace(identity, axis, sign, plane, TANGENT_AXES[axis],
                                     cover, contributions))
    return tuple(sorted(result, key=lambda face: face.id))


def merge_faces(scene, *, check_budget=None):
    """Build exposed physical geometry from normalized solids and source mappings.

    Positive-length shared edges connect; isolated shared corners do not. An
    interrupted build never publishes a partial catalog. Input values are read
    only, and no player dimensions or movement decisions enter the geometry.
    """
    _check(check_budget)
    solids = _sources(scene, check_budget)
    sources = _source_faces(solids, check_budget)
    faces = _merged_faces(sources, check_budget)
    scene_key = 'face-scene:' + geometry_key([
        (s.solid_index, s.block_id, s.part_id, s.box) for s in solids])
    _check(check_budget)
    catalog = FaceCatalog(scene_key, solids, sources, faces)
    _check(check_budget)
    return catalog


@dataclass(frozen=True)
class Cluster:
    id: int
    members: tuple
    solids: tuple
    boxes: tuple


@dataclass(frozen=True)
class Side:
    bounds: tuple
    owners: tuple  # per bound: the solid indices whose expanded edge lies on its plane
    region: tuple

    @property
    def key(self):
        return self.bounds


def nested(a, b):
    """Whether one (x_lo, z_lo, x_hi, z_hi) region contains the other."""
    def contains(outer, inner):
        return outer[0] <= inner[0] and outer[1] <= inner[1] and inner[2] <= outer[2] and inner[3] <= outer[3]
    return contains(a, b) or contains(b, a)


def intersects(a, b):
    return all(max(a[i], b[i]) <= min(a[i+2], b[i+2]) for i in (0, 1))


def free_rectangles(boxes):
    """Maximal closed rectangles disjoint from the open X/Z rectangles of boxes."""
    xs = [-math.inf]+sorted({b[a] for b in boxes for a in (0, 3)})+[math.inf]

    def empty(x0, x1, z0, z1):
        return not any(b[0] < x1 and x0 < b[3] and b[2] < z1 and z0 < b[5] for b in boxes)

    result = []
    for i, j in itertools.combinations(range(len(xs)), 2):
        xa, xb = xs[i], xs[j]
        if thin(xb-xa):
            continue
        start, free = -math.inf, []
        for lo, hi in sorted((b[2], b[5]) for b in boxes if b[0] < xb and xa < b[3]):
            if lo > start:
                free.append((start, lo))
            start = max(start, hi)
        free.append((start, math.inf))
        for za, zb in free:
            if thin(zb-za):
                continue
            if i > 0 and empty(xs[i-1], xb, za, zb) or j < len(xs)-1 and empty(xa, xs[j+1], za, zb):
                continue
            result.append((xa, za, xb, zb))
    return result


@dataclass(frozen=True)
class Surface:
    key: str
    axis: int
    sign: int
    plane: float
    tangent_axes: tuple
    cover: tuple
    contributions: tuple  # (solid index, cover) per member
    members: tuple
    cluster: int


class SceneGeometry:
    """Player-space geometry of one collision scene, for any modeled subset of its solids."""

    def __init__(self, solids, bounds=None):
        self.solids = solids
        self.boxes = tuple(tuple(expanded_box(s)) for s in solids)
        self.bounds = None if bounds is None else (
            bounds[0] + HALF_WIDTH, bounds[1], bounds[2] + HALF_WIDTH,
            bounds[3] - HALF_WIDTH, bounds[4] - BODY_HEIGHT, bounds[5] - HALF_WIDTH)
        self._clusters, self._sides, self._surfaces, self._options = {}, {}, {}, {}
        self._grid = None

    GRID_CELL = 4.

    def near(self, rectangle):
        if self._grid is None:
            self._grid = {}
            for i, b in enumerate(self.boxes):
                for cell in self._cells(b[0], b[2], b[3], b[5]):
                    self._grid.setdefault(cell, []).append(i)
        x0, z0, x1, z1 = rectangle
        found = {i for cell in self._cells(x0, z0, x1, z1) for i in self._grid.get(cell, ())}
        return sorted(i for i in found if self.boxes[i][0] <= x1 and x0 <= self.boxes[i][3]
                      and self.boxes[i][2] <= z1 and z0 <= self.boxes[i][5])

    def _cells(self, x0, z0, x1, z1):
        size = self.GRID_CELL
        return itertools.product(range(math.floor(x0 / size), math.floor(x1 / size) + 1),
                                 range(math.floor(z0 / size), math.floor(z1 / size) + 1))

    def clusters(self, indices):
        return tuple(self._cluster_map(indices).values())

    def cluster(self, cluster_id, indices):
        return self._cluster_map(indices)[cluster_id]

    def touching(self, index, indices):
        """The solids in `indices` (a set or mapping) whose expanded box touches solid `index`'s: no
        passable gap on any axis."""
        box, boxes, reach = self.boxes[index], self.boxes, MIN_FREE_WIDTH/2
        return [b for b in self.near([box[0]-reach, box[2]-reach, box[3]+reach, box[5]+reach])
                if b != index and b in indices and all(
                    thin(max(box[k], boxes[b][k])-min(box[k+3], boxes[b][k+3])) for k in range(3))]

    def _cluster_map(self, indices):
        indices = tuple(sorted(indices))
        if indices not in self._clusters:
            parent = {i: i for i in indices}

            def root(i):
                while parent[i] != i:
                    parent[i] = parent[parent[i]]
                    i = parent[i]
                return i
            for a in indices:
                for b in self.touching(a, parent):
                    if b > a:
                        parent[root(b)] = root(a)
            groups = {}
            for i in indices:
                groups.setdefault(root(i), []).append(i)
            self._clusters[indices] = {
                min(m): Cluster(min(m), tuple(m), tuple(self.solids[i] for i in m), tuple(self.boxes[i] for i in m))
                for m in sorted(groups.values())}
        return self._clusters[indices]

    def active(self, cluster, y_lo, y_hi):
        return tuple(k for k, b in enumerate(cluster.boxes) if b[1] < y_hi and y_lo < b[4])

    def sides(self, cluster, active):
        key = (cluster.members, tuple(active))
        if key not in self._sides:
            boxes = [cluster.boxes[k] for k in active]
            sides = []
            for region in free_rectangles(boxes):
                bounds = []
                for axis, edge, direction, plane in (('X', 3, 1, region[0]), ('X', 0, -1, region[2]),
                                                     ('Z', 5, 1, region[1]), ('Z', 2, -1, region[3])):
                    if math.isinf(plane):
                        continue
                    other = (2, 5, 1, 3) if axis == 'X' else (0, 3, 0, 2)
                    owners = tuple(cluster.members[k] for k, b in zip(active, boxes) if b[edge] == plane
                                   and b[other[0]] <= region[other[3]] and region[other[2]] <= b[other[1]])
                    bounds.append(((axis, direction, plane), owners))
                bounds.sort()
                sides.append(Side(tuple(b for b, _ in bounds), tuple(o for _, o in bounds), region))
            self._sides[key] = tuple(sorted(sides, key=lambda s: s.bounds))
        return self._sides[key]

    def player_surfaces(self, indices=None):
        """Exposed expanded faces of the modeled solids, merged per plane into connected surfaces.

        A face loses the tangent interior of every other modeled expanded box
        whose open interior contains its plane, so an exact-height slot stays.
        """
        indices = tuple(range(len(self.solids))) if indices is None else tuple(sorted(indices))
        if indices not in self._surfaces:
            boxes, bounds, buckets = self.boxes, self.bounds, {}
            for i in indices:
                box = boxes[i]
                for axis, (u, v) in enumerate(TANGENT_AXES):
                    rect = box[u], box[v], box[u+3], box[v+3]
                    if bounds is not None:
                        rect = _overlap(rect, (bounds[u], bounds[v], bounds[u+3], bounds[v+3]))
                        if rect is None:
                            continue
                    for sign in (-1, 1):
                        plane = box[axis if sign < 0 else axis+3]
                        if bounds is not None and not bounds[axis] <= plane <= bounds[axis+3]:
                            continue
                        pieces = [_embed(rect)]
                        for j in indices:
                            other = boxes[j]
                            cut = other[u], other[v], other[u+3], other[v+3]
                            if not other[axis] < plane < other[axis+3] or _overlap(rect, cut) is None:
                                continue
                            cutter = cut[0], -1.0, cut[1], cut[2], 1.0, cut[3]
                            pieces = [b for piece in pieces for b in subtract_open(piece, cutter)
                                      if b[0] < b[3] and b[2] < b[5]]
                        buckets.setdefault((axis, sign, plane), []).extend(
                            (i, r) for r in _cover(_project(b) for b in pieces))
            clusters = self._cluster_map(indices)
            owner = {i: c.id for c in clusters.values() for i in c.members}
            result = []
            for (axis, sign, plane), fragments in sorted(buckets.items()):
                for cover, members in _components(fragments):
                    result.append(Surface('surface:' + geometry_key([axis, sign, plane, cover]), axis, sign, plane,
                        TANGENT_AXES[axis], cover, tuple((i, _cover(r)) for i, r in sorted(members.items())),
                        tuple(sorted(members)), owner[min(members)]))
            self._surfaces[indices] = tuple(result)
        return self._surfaces[indices]


    def contact_options(self, axis, sign, *, clearance, members):
        """Finite contact domains on the faces of the member solids, one option per connected surface.

        `sign` is the expanded face's outward normal (a landing uses +1 on Y, a
        ceiling hit -1). Every solid of the scene clips; only members contribute.
        The members' own union is eroded by the clearance along its horizontal
        tangent axes (collision needs strict overlap there, and a seam between
        touching members stays inside one region); a clipping neighbour's edge stays
        exact, since touching it is allowed.
        """
        key = axis, sign, clearance, tuple(sorted(members))
        if key not in self._options:
            boxes, (u, v), buckets, faces = self.boxes, TANGENT_AXES[axis], {}, {}
            for i in sorted(members):
                box = boxes[i]
                rect = box[u], box[v], box[u+3], box[v+3]
                plane = box[axis if sign < 0 else axis+3]
                faces.setdefault(plane, []).append(rect)
                pieces = [_embed(rect)]
                for other in boxes:
                    cut = other[u], other[v], other[u+3], other[v+3]
                    if not other[axis] < plane < other[axis+3] or _overlap(rect, cut) is None:
                        continue
                    cutter = cut[0], -1.0, cut[1], cut[2], 1.0, cut[3]
                    pieces = [b for piece in pieces for b in subtract_open(piece, cutter) if b[0] < b[3] and b[2] < b[5]]
                buckets.setdefault(plane, []).extend((i, r) for r in _cover(_project(b) for b in pieces))
            shrink = tuple(0 if a == 1 else clearance for a in (u, v))
            result = []
            for plane, fragments in sorted(buckets.items()):
                inner = _erode(_cover(faces[plane]), shrink)
                for cover, contributors in _components(fragments):
                    regions = _cover(o for a in inner for b in cover for o in [_overlap(a, b)] if o is not None)
                    if regions:
                        members = tuple(sorted(contributors))
                        result.append(ContactOption('surface:' + geometry_key([axis, sign, plane, cover]), plane,
                                                    members, tuple(self.solids[i] for i in members), regions))
            self._options[key] = tuple(result)
        return self._options[key]


@dataclass(frozen=True)
class ContactOption:
    surface: str
    plane: float
    members: tuple
    boxes: tuple
    regions: tuple


def _maximal_rectangles(cover):
    """Every maximal axis-aligned rectangle inside a closed union of rectangles."""
    us = sorted({r[0] for r in cover} | {r[2] for r in cover})
    vs = sorted({r[1] for r in cover} | {r[3] for r in cover})
    filled = [[any(r[0] <= (a+b)/2 <= r[2] and r[1] <= (c+d)/2 <= r[3] for r in cover)
               for c, d in zip(vs, vs[1:])] for a, b in zip(us, us[1:])]

    def runs(i0, i1):
        rows, start = [], None
        for j in range(len(vs) - 1):
            if all(filled[i][j] for i in range(i0, i1)):
                start = j if start is None else start
            elif start is not None:
                rows.append((start, j))
                start = None
        return rows + ([(start, len(vs) - 1)] if start is not None else [])

    result = []
    for i0, i1 in itertools.combinations(range(len(us)), 2):
        for j0, j1 in runs(i0, i1):
            wider = [i for i in (i0 - 1, i1) if 0 <= i < len(us) - 1]
            if not any(all(filled[i][j] for j in range(j0, j1)) for i in wider):
                result.append((us[i0], vs[j0], us[i1], vs[j1]))
    return result


def _erode(cover, shrink):
    """The points whose shrink-sized neighbourhood stays inside the cover, as canonical rectangles."""
    pieces = [(r[0]+shrink[0], r[1]+shrink[1], r[2]-shrink[0], r[3]-shrink[1]) for r in _maximal_rectangles(cover)]
    return _cover(r for r in pieces if r[0] < r[2] and r[1] < r[3])


@functools.lru_cache(maxsize=8)
def _scene_geometry(solids, bounds):
    return SceneGeometry(solids, bounds)


def scene_geometry(collision_scene):
    """The shared geometry of `scene['collisionScene']`, memoized; it never rides inside the scene dict."""
    bounds = collision_scene.get('bounds')
    return _scene_geometry(tuple(tuple(s) for s in collision_scene['solids']),
                           None if bounds is None else tuple(bounds))
