import copy
import math
import struct
import sys

from parkour_solver.physics.vertical import clamp, f32, vertical_move
from parkour_solver.schema import number
from parkour_solver.scene.route import HALF_WIDTH


BODY_HEIGHT = f32(1.8)
# Physical gaps are 0, under 0.6 or at least 0.612, so real player-space free pieces
# are at least 0.012 wide and narrower ones are numerical artifacts.
MIN_FREE_WIDTH = .012


def thin(width):
    # Half the minimum: a real 0.012 gap can round below it, artifacts are about 0.
    return width < MIN_FREE_WIDTH/2


def box_bounds(values, name):
    if not isinstance(values, list) or len(values) != 6:
        raise ValueError(name + ' requires minX,minY,minZ,maxX,maxY,maxZ')
    for value in values:
        number(value, name)
    if any(values[i] >= values[i + 3] for i in range(3)):
        raise ValueError(name + ' must have positive volume')
    return values


def normalize_geometry(scene):
    if 'collisionScene' in scene:
        geometry = scene['collisionScene']
        if not isinstance(geometry, dict) or set(geometry) - {'version', 'profile', 'bounds', 'solids', 'stepHeight', 'ladders'}:
            raise ValueError('Unsupported collision scene')
        if type(geometry.get('version')) is not int or geometry['version'] != 1 or geometry.get('profile') != 'minecraft-1.8.9':
            raise ValueError('Expected a minecraft-1.8.9 collision scene')
        solids = geometry.get('solids')
        bounds = geometry.get('bounds')
        if not isinstance(solids, list) or not solids:
            raise ValueError('Supply collision solids')
        for block in scene['blocks']:
            if not isinstance(block, dict) or set(block) - {'id', 'bounds', 'top', 'solidIndex', 'sourceBlock'}:
                raise ValueError('Invalid derived collision support')
            index = block.get('solidIndex')
            block.setdefault('sourceBlock', block.get('id'))
            if not isinstance(block['sourceBlock'], str) or not block['sourceBlock']:
                raise ValueError('Collision supports require physical block provenance')
            if type(index) is not int or not 0 <= index < len(solids):
                raise ValueError('Invalid support solid index')
            b = solids[index]
            box_bounds(b, 'collision solid')
            if block.get('bounds') != [b[0], b[3], b[2], b[5]] or block.get('top') != b[4]:
                raise ValueError('Support must match its actual collision box')
        if sorted(b['solidIndex'] for b in scene['blocks']) != list(range(len(solids))):
            raise ValueError('Every collision solid must have its derived support entry')
    else:
        bounds = scene.get('geometryBounds')
        solids, supports = [], []
        for block in scene['blocks']:
            if not isinstance(block, dict) or set(block) != {'id', 'boxes'}:
                raise ValueError('Collision blocks require id and actual boxes')
            if not isinstance(block['id'], str) or not block['id'] or not isinstance(block['boxes'], list) or not block['boxes']:
                raise ValueError('Collision blocks require an ID and nonempty boxes')
            for index, box in enumerate(block['boxes']):
                b = box_bounds(box, 'collision box')
                name = block['id'] if len(block['boxes']) == 1 else block['id'] + '#' + str(index)
                supports.append({'id': name, 'sourceBlock': block['id'], 'solidIndex': len(solids),
                                 'bounds': [b[0], b[3], b[2], b[5]], 'top': b[4]})
                solids.append(copy.deepcopy(b))
        scene['blocks'] = supports
        geometry = {'version': 1, 'profile': 'minecraft-1.8.9', 'bounds': bounds,
                    'solids': solids, 'stepHeight': .6}
        scene['collisionScene'] = geometry
    box_bounds(bounds, 'geometryBounds')
    if geometry.get('stepHeight', .6) != .6:
        raise ValueError('This route family uses normal step height .6')
    if scene.get('geometryBounds', bounds) != bounds:
        raise ValueError('Inconsistent geometry coverage bounds')
    for b in solids:
        box_bounds(b, 'collision solid')
        if any(b[i] < bounds[i] or b[i + 3] > bounds[i + 3] for i in range(3)):
            raise ValueError('Collision solid outside supplied geometry bounds')
    cells = scene.get('climbableCells', geometry.get('ladders', []))
    from parkour_solver.scene.ladders import ladder_columns
    ladder_columns(cells)
    if 'ladders' in geometry:
        ladder_columns(geometry['ladders'])
    if scene.get('climbableCells') is not None and geometry.get('ladders') is not None \
            and {tuple(c) for c in scene['climbableCells']} != {tuple(c) for c in geometry['ladders']}:
        raise ValueError('Conflicting climbable cells and scene ladder declarations')
    if cells:
        geometry['ladders'] = copy.deepcopy(cells)
        scene['climbableCells'] = copy.deepcopy(cells)
    return scene


def _ordered(value):
    bits = struct.unpack('<q', struct.pack('<d', value))[0]
    return bits if bits >= 0 else -(bits & 0x7fffffffffffffff)


def _unordered(key):
    return struct.unpack('<d', struct.pack('<q', key if key >= 0 else -key | -0x8000000000000000))[0]


def _first(predicate, guess):
    """Least finite double satisfying a predicate monotone in its argument, or None."""
    if predicate(guess) and not predicate(math.nextafter(guess, -math.inf)):
        return guess
    if not predicate(guess) and predicate(math.nextafter(guess, math.inf)):
        return math.nextafter(guess, math.inf)
    lo, hi = _ordered(-sys.float_info.max), _ordered(sys.float_info.max)
    if not predicate(_unordered(hi)):
        return None
    while lo < hi:
        middle = (lo + hi) // 2
        if predicate(_unordered(middle)):
            hi = middle
        else:
            lo = middle + 1
    return _unordered(lo)


def support_center_interval(lo, hi):
    """First and last player center (doubles) strictly overlapping [lo, hi] on one axis.

    This is the bridge's legacy footprint edge (`SupportOverlap.minCenter`/`maxCenter`).
    Ordered search replaces its step-by-step walk, which never ends when the
    analytic edge cancels to zero and the walk enters subnormal values.
    """
    lo, hi = float(lo), float(hi)
    first = _first(lambda center: center + HALF_WIDTH > lo, lo - HALF_WIDTH)
    after = _first(lambda center: not center - HALF_WIDTH < hi, hi + HALF_WIDTH)
    last = math.nextafter(after, -math.inf) if after is not None else sys.float_info.max
    if first is None or first > last:
        raise ValueError('No player center overlaps the support interval')
    return first, last


def route_footprints(blocks, standing=False):
    """Bridge footprints `[minX,maxX,minZ,maxZ]`: explicit centers, else exact support edges."""
    if not isinstance(blocks, list) or len(blocks) < 2:
        raise ValueError('Supply a route of support blocks')
    footprints = []
    for block in blocks:
        bounds = block.get('standingBounds') if standing else None
        if bounds is None and block.get('centerBounds') is not None:
            footprints.append([float(value) for value in block['centerBounds']])
            continue
        x1, x2, z1, z2 = block['bounds'] if bounds is None else bounds
        footprints.append([*support_center_interval(x1, x2), *support_center_interval(z1, z2)])
    return footprints


def expanded_box(box):
    return [box[0] - HALF_WIDTH, box[1] - BODY_HEIGHT, box[2] - HALF_WIDTH,
            box[3] + HALF_WIDTH, box[4], box[5] + HALF_WIDTH]
