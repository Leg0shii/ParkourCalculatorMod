"""Exact exposed physical faces, merged across coplanar neighboring solids."""
from collections.abc import Mapping
from dataclasses import dataclass, field
from types import MappingProxyType
from typing import ClassVar

from parkour_solver.scene.geometry import box_bounds
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


def _merged_faces(sources, check_budget):
    buckets = {}
    for source in sources:
        _, axis, sign = source.key
        buckets.setdefault((axis, sign, source.plane), []).extend(
            (source.key, rect) for rect in source.exposed_cover)
    result = []
    for (axis, sign, plane), fragments in sorted(buckets.items()):
        pending = set(range(len(fragments)))
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
            cover = _cover(fragments[i][1] for i in component)
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
