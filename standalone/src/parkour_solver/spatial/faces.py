"""Exact exposed physical faces, merged across coplanar neighboring solids."""
from dataclasses import dataclass

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
    scene_key: str
    solids: tuple
    source_faces: tuple
    faces: tuple


def _coordinate(value):
    try:
        number(value, 'face coordinate')
        result = float(value)
    except (OverflowError, TypeError) as error:
        raise ValueError('Face coordinate must be a finite exact double') from error
    if result != value:
        raise ValueError('Face coordinate cannot be converted to a double exactly')
    return result if result else 0.0


def _sources(scene):
    geometry = scene.get('collisionScene')
    if not isinstance(geometry, dict) or not isinstance(geometry.get('solids'), list):
        raise ValueError('Face merging requires a normalized collision scene')
    blocks = scene.get('blocks')
    if not isinstance(blocks, list) or len(blocks) != len(geometry['solids']):
        raise ValueError('Every solid requires one source mapping')
    mappings, parts = {}, set()
    for block in blocks:
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
    solids = _sources(scene)
    sources = _source_faces(solids, check_budget)
    faces = _merged_faces(sources, check_budget)
    scene_key = 'face-scene:' + geometry_key([
        (s.solid_index, s.block_id, s.part_id, s.box) for s in solids])
    _check(check_budget)
    return FaceCatalog(scene_key, solids, sources, faces)
