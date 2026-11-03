import itertools
import time
from dataclasses import asdict, dataclass, field

from ..obstacle_geometry import BODY_HEIGHT, box_bounds, expanded_box
from ..route import HALF_WIDTH


def dimension(box):
    return sum(box[a] < box[a + 3] for a in range(3))


def contains(box, point, tolerance=0):
    return all(box[a] - tolerance <= point[a] <= box[a + 3] + tolerance for a in range(3))


def intersection(a, b):
    result = tuple(max(a[i], b[i]) for i in range(3)) + tuple(min(a[i], b[i]) for i in range(3, 6))
    return result if all(result[i] <= result[i + 3] for i in range(3)) else None


def subtract_open(box, obstacle, *, boundaries=False):
    if any(box[a] == box[a + 3] and not obstacle[a] < box[a] < obstacle[a + 3] for a in range(3)):
        return [box]
    if any(box[a] < box[a + 3] and (box[a + 3] <= obstacle[a] or box[a] >= obstacle[a + 3]) for a in range(3)):
        return [box]
    middle, pieces = list(box), []
    for axis in range(3):
        if middle[axis] == middle[axis + 3]:
            continue
        lo, hi = max(middle[axis], obstacle[axis]), min(middle[axis + 3], obstacle[axis + 3])
        if middle[axis] < lo or boundaries and middle[axis] == lo == obstacle[axis]:
            piece = middle[:]
            piece[axis + 3] = lo
            pieces.append(tuple(piece))
        if hi < middle[axis + 3] or boundaries and hi == middle[axis + 3] == obstacle[axis + 3]:
            piece = middle[:]
            piece[axis] = hi
            pieces.append(tuple(piece))
        middle[axis], middle[axis + 3] = lo, hi
    return pieces


def merge_boxes(boxes):
    values = sorted(set(tuple(b) for b in boxes))
    changed = True
    while changed:
        changed = False
        for axis in range(3):
            groups = {}
            for b in values:
                key = tuple(v for i, v in enumerate(b) if i not in (axis, axis + 3))
                groups.setdefault(key, []).append(b)
            merged = []
            for group in groups.values():
                group.sort(key=lambda b: (b[axis], b[axis + 3]))
                current = list(group[0])
                for other in group[1:]:
                    if current[axis + 3] >= other[axis]:
                        current[axis + 3] = max(current[axis + 3], other[axis + 3])
                        changed = True
                    else:
                        merged.append(tuple(current))
                        current = list(other)
                merged.append(tuple(current))
            values = sorted(set(merged))
    return values


def union_subtract(boxes, solids, limit, *, boundaries=False, deadline=None):
    result = sorted(set(boxes))
    for obstacle in sorted(set(solids)):
        if deadline is not None and time.monotonic() >= deadline:
            raise TimeoutError('Cell decomposition deadline')
        result = sorted(set(piece for box in result for piece in subtract_open(box, obstacle, boundaries=boundaries)))
        if len(result) > limit:
            raise ValueError('Cell decomposition exceeded max_cells; geometry was not truncated')
    return result


@dataclass(frozen=True)
class Cell:
    id: int
    bounds: tuple
    dimension: int


@dataclass(frozen=True)
class Portal:
    id: int
    cells: tuple
    bounds: tuple
    dimension: int


@dataclass(frozen=True)
class ContactPatch:
    cell: int
    solid: int
    owner: str
    axis: int
    side: int
    kind: str
    bounds: tuple


@dataclass
class Decomposition:
    bounds: tuple
    cells: list
    portals: list
    contacts: list
    climbable: dict
    statistics: dict
    parent_cells: dict = field(default_factory=dict)

    def locate(self, point, tolerance=0):
        return [c.id for c in self.cells if contains(c.bounds, point, tolerance)]

    def neighbors(self):
        result = {c.id: [] for c in self.cells}
        for p in self.portals:
            a, b = p.cells
            result[a].append((b, p.id))
            result[b].append((a, p.id))
        return result

    def to_dict(self):
        return asdict(self)


def cell_portals(cells, *, deadline=None):
    portals = []
    for i, a in enumerate(cells):
        if deadline is not None and time.monotonic() >= deadline:
            raise TimeoutError('Cell adjacency deadline')
        for b in cells[i + 1:]:
            overlap = intersection(a.bounds, b.bounds)
            if overlap is not None and dimension(overlap) >= min(a.dimension, b.dimension) - 1:
                portals.append(Portal(len(portals), (a.id, b.id), overlap, dimension(overlap)))
    return portals


def contact_patches(cells, solids, owners):
    # Cells are already outside every open expanded solid. Intersecting their
    # closures with a face therefore exposes only the collision-free face cover.
    contacts = []
    for cell in cells:
        for i, solid in enumerate(solids):
            for axis, side in itertools.product(range(3), (-1, 1)):
                face = list(solid)
                face[axis] = face[axis + 3] = solid[axis if side < 0 else axis + 3]
                overlap = intersection(cell.bounds, face)
                if overlap is not None:
                    kind = 'support' if axis == 1 and side == 1 else 'ceiling' if axis == 1 else 'wall'
                    contacts.append(ContactPatch(cell.id, i, owners[i], axis, side, kind, overlap))
    return contacts


def refine_support_cells(cells, contacts, *, max_cells, deadline=None):
    """Split only cells incident above a finite support; keep residual air/strata."""
    by_cell = {}
    for patch in contacts:
        if patch.kind == 'support' and cells[patch.cell].bounds[1] == patch.bounds[1]:
            by_cell.setdefault(patch.cell, set()).add(patch.bounds)
    refined = {}
    for cell in cells:
        if deadline is not None and time.monotonic() >= deadline:
            raise TimeoutError('Support refinement deadline')
        pieces = [cell.bounds]
        for patch in sorted(by_cell.get(cell.id, ())):
            # Cutting a single cell at its own support edges never extends a
            # grid across neighboring empty space. No merge may erase the cuts.
            for axis in (0, 2):
                for edge in (patch[axis], patch[axis + 3]):
                    next_pieces = []
                    for box in pieces:
                        if box[axis] < edge < box[axis + 3]:
                            lo, hi = list(box), list(box)
                            lo[axis + 3], hi[axis] = edge, edge
                            next_pieces.extend((tuple(lo), tuple(hi)))
                        else:
                            next_pieces.append(box)
                    pieces = next_pieces
                    if len(pieces) + len(refined) > max_cells:
                        raise ValueError('Support refinement exceeded max_cells; geometry was not truncated')
        for box in pieces:
            refined.setdefault(box, []).append(cell.id)
    boxes = sorted(refined)
    return ([Cell(i, box, dimension(box)) for i, box in enumerate(boxes)],
            {i: tuple(refined[box]) for i, box in enumerate(boxes)})


def decompose(scene, *, max_cells=20000, deadline=None, refine_support=True):
    started = time.monotonic()
    geometry = scene['collisionScene']
    source_bounds = box_bounds(list(geometry['bounds']), 'decomposition bounds')
    bounds = (source_bounds[0] + HALF_WIDTH, source_bounds[1], source_bounds[2] + HALF_WIDTH,
              source_bounds[3] - HALF_WIDTH, source_bounds[4] - BODY_HEIGHT, source_bounds[5] - HALF_WIDTH)
    if any(bounds[a] > bounds[a + 3] for a in range(3)):
        raise ValueError('Scene coverage is smaller than the player body')
    solids = [tuple(expanded_box(b)) for b in geometry['solids']]
    raw = union_subtract([bounds], solids, max_cells, deadline=deadline)
    volumes = merge_boxes(raw)
    faces = []
    for solid in solids:
        for axis, side in itertools.product(range(3), (-1, 1)):
            b = list(solid)
            b[axis] = b[axis + 3] = solid[axis if side < 0 else axis + 3]
            clipped = intersection(bounds, b)
            if clipped is not None:
                faces.append(clipped)
    strata = union_subtract(faces, solids, max_cells, boundaries=True, deadline=deadline)
    strata = merge_boxes(strata)
    strata = [b for b in strata if not any(intersection(b, v) == b for v in volumes)]
    boxes = sorted(set(volumes + strata))
    if len(boxes) > max_cells:
        raise ValueError('Cell decomposition exceeded max_cells')
    cells = [Cell(i, b, dimension(b)) for i, b in enumerate(boxes)]
    owners = {b['solidIndex']: b.get('sourceBlock', b['id']) for b in scene['blocks']}
    contacts = contact_patches(cells, solids, owners)
    base_count = len(cells)
    parents = {c.id: (c.id,) for c in cells}
    if refine_support:
        cells, parents = refine_support_cells(cells, contacts, max_cells=max_cells, deadline=deadline)
        contacts = contact_patches(cells, solids, owners)
    portals = cell_portals(cells, deadline=deadline)
    climbable = {}
    for c in cells:
        memberships = []
        for ladder in geometry.get('ladders', []):
            region = (ladder[0], ladder[1], ladder[2], ladder[0] + 1, ladder[1] + 1, ladder[2] + 1)
            if intersection(c.bounds, region) is not None:
                memberships.append(ladder)
        if memberships:
            climbable[c.id] = memberships
    return Decomposition(bounds, cells, portals, contacts, climbable,
        dict(rawCells=len(raw), baseCells=base_count, volumeCells=sum(c.dimension == 3 for c in cells),
             boundaryCells=sum(c.dimension < 3 for c in cells), cells=len(cells), supportRefinement=refine_support,
             portals=len(portals), contactPatches=len(contacts), elapsedSeconds=time.monotonic() - started), parents)


def segment_intervals(decomposition, start, end, cell_ids=None, tolerance=1e-9):
    allowed = set(cell_ids) if cell_ids is not None else None
    result = []
    for cell in decomposition.cells:
        if allowed is not None and cell.id not in allowed:
            continue
        lo, hi = 0., 1.
        for axis in range(3):
            delta = end[axis] - start[axis]
            if delta == 0:
                if not cell.bounds[axis] - tolerance <= start[axis] <= cell.bounds[axis + 3] + tolerance:
                    hi = -1
                    break
            else:
                a = (cell.bounds[axis] - tolerance - start[axis]) / delta
                b = (cell.bounds[axis + 3] + tolerance - start[axis]) / delta
                lo, hi = max(lo, min(a, b)), min(hi, max(a, b))
        if lo <= hi:
            result.append((max(0., lo), min(1., hi), cell.id))
    return sorted(result)


def segment_covered(decomposition, start, end, cell_ids=None, tolerance=1e-9):
    reach = 0.
    intervals = segment_intervals(decomposition, start, end, cell_ids, tolerance)
    if not intervals or intervals[0][0] > 0:
        return False
    for lo, hi, _ in intervals:
        if lo > reach:
            return False
        reach = max(reach, hi)
        if reach >= 1:
            return True
    return False


def diagnostic_svg(decomposition, *, y):
    b = decomposition.bounds
    width, height = b[3] - b[0], b[5] - b[2]
    lines = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{b[0]} {b[2]} {width} {height}">']
    stroke = max(width, height) / 600
    for c in decomposition.cells:
        if c.bounds[1] <= y <= c.bounds[4]:
            x, _, z, xx, _, zz = c.bounds
            lines.append(f'<rect x="{x}" y="{z}" width="{xx-x}" height="{zz-z}" fill="hsl({c.id*137%360},50%,85%)" stroke="black" stroke-width="{stroke}"/>')
    lines.append('</svg>')
    return '\n'.join(lines)
