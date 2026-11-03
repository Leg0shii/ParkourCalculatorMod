"""Finite free-volume and physical support states for spatial routing.

The graph describes collision geometry, not dynamically feasible jumps. Open
links never reset flight. Contact links require an actual physical interaction;
occupancy of an incident volume alone cannot satisfy them.
"""

import hashlib
import json
from dataclasses import asdict, dataclass

from .geometry import contains, dimension, intersection


SCHEMA = 1


def geometry_key(value):
    # Exact JSON doubles, deliberately no coordinate rounding or raw cell IDs.
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':'),
                                    allow_nan=False).encode()).hexdigest()


def canonical_cover(boxes):
    """Canonical closed planar X/Z union, retaining uncovered lines/points.

    Sweep X strips, union their Z intervals, merge equal consecutive coverage.
    This removes encoding seams without filling holes or concavities.
    """
    boxes = sorted(set(tuple(b) for b in boxes))
    if not boxes:
        return ()
    height = boxes[0][1]
    if any(b[1] != height or b[4] != height for b in boxes):
        raise ValueError('A support cover must lie on one horizontal plane')
    areas = [b for b in boxes if b[0] < b[3] and b[2] < b[5]]
    edges = sorted({b[a] for b in areas for a in (0, 3)})
    strips = []
    for x, xx in zip(edges, edges[1:]):
        intervals = sorted((b[2], b[5]) for b in areas if b[0] <= x and xx <= b[3])
        merged = []
        for lo, hi in intervals:
            if merged and lo <= merged[-1][1]:
                merged[-1] = (merged[-1][0], max(hi, merged[-1][1]))
            else:
                merged.append((lo, hi))
        coverage = tuple(merged)
        if strips and strips[-1][1] == x and strips[-1][2] == coverage:
            strips[-1] = (strips[-1][0], xx, coverage)
        elif coverage:
            strips.append((x, xx, coverage))
    result = [(x, height, z, xx, height, zz) for x, xx, intervals in strips for z, zz in intervals]
    # Preserve lower-dimensional free strata not covered by the area union.
    # Sweep each line at all union endpoints before discarding covered pieces.
    lines = {}
    points = []
    for b in boxes:
        if b[0] < b[3] and b[2] < b[5]:
            continue
        axis = 0 if b[0] < b[3] else 2
        if b[axis] == b[axis + 3]:
            points.append(b)
        else:
            other = 2 if axis == 0 else 0
            lines.setdefault((axis, b[other]), []).append((b[axis], b[axis+3]))
    for (axis, fixed), intervals in sorted(lines.items()):
        other = 2 if axis == 0 else 0
        cuts = sorted({v for lohi in intervals for v in lohi} |
                      {b[a] for b in result if b[other] <= fixed <= b[other+3] for a in (axis, axis+3)})
        kept = []
        for lo, hi in zip(cuts, cuts[1:]):
            if not any(a <= lo and hi <= b for a, b in intervals):
                continue
            if any(b[other] <= fixed <= b[other+3] and b[axis] <= lo and hi <= b[axis+3] for b in result):
                continue
            if kept and kept[-1][1] == lo:
                kept[-1] = (kept[-1][0], hi)
            else:
                kept.append((lo, hi))
        for lo, hi in kept:
            b = [fixed, height, fixed, fixed, height, fixed]
            b[axis], b[axis+3] = lo, hi
            result.append(tuple(b))
    result.extend(b for b in points if not any(contains(a, b[:3]) for a in result))
    return tuple(sorted(set(result)))


@dataclass(frozen=True)
class SupportComponent:
    key: str
    owner: str
    height: float
    cover: tuple
    cells: tuple
    solids: tuple
    patches: tuple


def support_components(decomposition):
    grouped = {}
    for index, patch in enumerate(decomposition.contacts):
        if patch.kind == 'support':
            grouped.setdefault((patch.owner, patch.bounds[1]), []).append((index, patch))
    components = []
    for (owner, height), patches in sorted(grouped.items()):
        cover = canonical_cover(p.bounds for _, p in patches)
        pending = set(range(len(cover)))
        while pending:
            connected = {min(pending)}
            queue = list(connected)
            pending -= connected
            while queue:
                current = queue.pop()
                adjacent = {i for i in pending if intersection(cover[current], cover[i]) is not None}
                connected |= adjacent
                pending -= adjacent
                queue.extend(sorted(adjacent))
            pieces = tuple(cover[i] for i in sorted(connected))
            incident = [(i, p) for i, p in patches if any(intersection(p.bounds, b) is not None for b in pieces)]
            key = 'support:' + geometry_key((owner, height, pieces))
            components.append(SupportComponent(key, owner, height, pieces,
                tuple(sorted({p.cell for _, p in incident})), tuple(sorted({p.solid for _, p in incident})),
                tuple(i for i, _ in incident)))
    return sorted(components, key=lambda c: c.key)


@dataclass(frozen=True)
class RouteRegion:
    key: str
    kind: str
    cover: tuple
    cells: tuple
    owners: tuple = ()
    solids: tuple = ()
    predicate: object = None


@dataclass(frozen=True)
class RouteTransition:
    key: str
    source: str
    target: str
    kind: str
    cover: tuple
    portals: tuple = ()
    patches: tuple = ()
    movement_families: tuple = ()
    capability: str = 'not_ruled_out'
    evidence: str = 'geometry_only'


@dataclass
class RouteGraph:
    schema: int
    context: str
    regions: list
    transitions: list
    supports: list
    starts: tuple
    goals: tuple

    def to_dict(self):
        return asdict(self)

    def neighbors(self):
        result = {r.key: [] for r in self.regions}
        for transition in self.transitions:
            result[transition.source].append(transition)
        return result


def build_route_graph(scene, decomposition, task_goal=None):
    """Build geometry incidence; retain unknown movement feasibility explicitly."""
    supports = support_components(decomposition)
    regions = [RouteRegion('air:' + geometry_key(c.bounds), 'air_volume', (c.bounds,), (c.id,))
               for c in decomposition.cells]
    air = {r.cells[0]: r.key for r in regions}
    regions += [RouteRegion(s.key, 'support_surface', s.cover, s.cells, (s.owner,), s.solids) for s in supports]
    transitions = []

    def link(source, target, kind, cover, portals=(), patches=()):
        key = 'transition:' + geometry_key((source, target, kind, cover))
        families = () if kind == 'open_portal' else ('normal', 'stepping', 'ceiling')
        transitions.append(RouteTransition(key, source, target, kind, cover, portals, patches, families))

    for p in decomposition.portals:
        a, b = (air[c] for c in p.cells)
        link(a, b, 'open_portal', (p.bounds,), (p.id,))
        link(b, a, 'open_portal', (p.bounds,), (p.id,))
    for support in supports:
        for cell in support.cells:
            patches = tuple(i for i in support.patches if decomposition.contacts[i].cell == cell)
            cover = canonical_cover(decomposition.contacts[i].bounds for i in patches)
            link(air[cell], support.key, 'support_arrival', cover, patches=patches)
            link(support.key, air[cell], 'support_departure', cover, patches=patches)

    start = scene['initial']['position']
    start_owner = next(b.get('sourceBlock', b['id']) for b in scene['blocks'] if b['id'] == scene['startBlock'])
    starts = tuple(s.key for s in supports if s.owner == start_owner and any(contains(b, start) for b in s.cover))
    # Do not fabricate grounded contact if the actual initial state is airborne.
    if not starts:
        starts = tuple(air[c] for c in decomposition.locate(start))
    if task_goal is not None and task_goal.get('kind') == 'landing_height_region':
        from ..terminal_goals import prepare_goal
        task_goal = prepare_goal(task_goal)
        terminal = 'terminal:' + geometry_key(task_goal)
        regions.append(RouteRegion(terminal, 'terminal_predicate', (), (), predicate=task_goal))
        # Coverage bounds are only collision-query limits, never finish walls.
        # A finish link is unresolved until the descending height clock and rows
        # hold; it is not permission to leave this volume through a solid.
        for region in list(regions):
            if region.kind == 'air_volume':
                link(region.key, terminal, 'finish', ())
        goals = (terminal,)
    elif task_goal is None or task_goal.get('kind') == 'landing':
        goal_id = scene['goalBlock'] if task_goal is None else task_goal.get('block', scene['goalBlock'])
        goal_block = next(b for b in scene['blocks'] if b['id'] == goal_id)
        goal_owner = goal_block.get('sourceBlock', goal_id)
        goals = tuple(s.key for s in supports if s.owner == goal_owner and s.height == goal_block['top']
                      and goal_block['solidIndex'] in s.solids)
    else:
        raise ValueError('Route graph requires a physical landing or virtual height-region task goal')
    context = geometry_key(dict(schema=SCHEMA, bounds=decomposition.bounds,
        regions=[asdict(r) for r in regions], transitions=[asdict(t) for t in transitions],
        starts=starts, goals=goals, taskGoal=task_goal))
    return RouteGraph(SCHEMA, context, regions, transitions, supports, starts, goals)


def diagnostic_svg(graph, *, y):
    """Draw actual finite region rectangles, with physical support outlines."""
    boxes = [b for r in graph.regions for b in r.cover]
    if not boxes:
        raise ValueError('No finite route regions to draw')
    x, z = min(b[0] for b in boxes), min(b[2] for b in boxes)
    width, height = max(b[3] for b in boxes)-x, max(b[5] for b in boxes)-z
    stroke = max(width, height)/600
    lines = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{x} {z} {width} {height}">']
    for i, region in enumerate(graph.regions):
        for b in region.cover:
            if b[1] <= y <= b[4]:
                support = region.kind == 'support_surface'
                fill = 'none' if support else f'hsl({i*137%360},50%,85%)'
                color = 'red' if support else 'black'
                lines.append(f'<rect x="{b[0]}" y="{b[2]}" width="{b[3]-b[0]}" height="{b[5]-b[2]}" '
                             f'fill="{fill}" stroke="{color}" stroke-width="{stroke*(3 if support else 1)}"/>')
    return '\n'.join(lines + ['</svg>'])
