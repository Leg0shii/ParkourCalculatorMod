"""Finite free-volume and physical support states for spatial routing.

The graph describes collision geometry, not dynamically feasible jumps. Open
links never reset flight. Contact links require an actual physical interaction;
occupancy of an incident volume alone cannot satisfy them.
"""

import hashlib
import json
from dataclasses import asdict, dataclass, field

from .geometry import Cell, cell_portals, contains, dimension, intersection, merge_boxes, subtract_open


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
    transit_key: str = ''


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
    transfers: dict = field(default_factory=dict)
    flight_proposals: dict = field(default_factory=dict)

    def to_dict(self):
        return asdict(self)

    def neighbors(self):
        result = {r.key: [] for r in self.regions}
        for transition in self.transitions:
            result[transition.source].append(transition)
        return result


def free_transit_cover(scene,decomposition,*,max_regions=20000,deadline=None):
    """Overlapping free cuboids remove arbitrary subtraction-plane extensions.

    Expand exact free seeds in all axis orders, also clipping one seed face at
    physical obstacle planes. Every result is checked with the same open-solid
    subtraction as decompose; original coverage is retained. This is a bounded
    convex cover construction, not a completeness claim about maximal cuboids.
    """
    import itertools
    import time
    from ..obstacle_geometry import expanded_box
    bounds=decomposition.bounds
    solids=sorted(set(tuple(expanded_box(b)) for b in scene['collisionScene']['solids']))
    coordinates=[sorted({bounds[a],bounds[a+3]} |
        {s[i] for s in solids for i in (a,a+3) if bounds[a]<=s[i]<=bounds[a+3]}) for a in range(3)]
    base=merge_boxes([c.bounds for c in decomposition.cells])
    seeds=set(base)
    for box in base:
        for axis in range(3):
            for edge in coordinates[axis]:
                if box[axis]<edge<box[axis+3]:
                    lo,hi=list(box),list(box)
                    lo[axis]=hi[axis+3]=edge
                    seeds.update((tuple(lo),tuple(hi)))
    if len(seeds)>max_regions:
        raise ValueError('Transit cover exceeded max_regions; seeds were not truncated')
    def free(box):
        return all(subtract_open(box,solid)==[box] for solid in solids)
    result=set(base)
    for seed in sorted(seeds):
        if deadline is not None and time.monotonic()>=deadline:
            raise TimeoutError('transit_cover_deadline')
        for order in itertools.permutations(range(3)):
            box=list(seed)
            for axis in order:
                if seed[axis]==seed[axis+3]:
                    continue
                for edge in coordinates[axis]:
                    if edge>=box[axis]:
                        break
                    candidate=box[:];candidate[axis]=edge
                    if free(tuple(candidate)):
                        box=candidate;break
                for edge in reversed(coordinates[axis]):
                    if edge<=box[axis+3]:
                        break
                    candidate=box[:];candidate[axis+3]=edge
                    if free(tuple(candidate)):
                        box=candidate;break
            result.add(tuple(box))
            if len(result)>max_regions:
                raise ValueError('Transit cover exceeded max_regions; geometry was not truncated')
    return sorted(b for b in result if not any(a!=b and intersection(a,b)==b for a in result))


def build_route_graph(scene, decomposition, task_goal=None, *, max_regions=20000, deadline=None):
    """Build geometry incidence; retain unknown movement feasibility explicitly."""
    supports = support_components(decomposition)
    # Reconstruct convex transit domains across local refinement seams. Surface
    # states remain explicit; the cuts above a footprint do not themselves
    # create independent overflight routes.
    transit=free_transit_cover(scene,decomposition,max_regions=max_regions,deadline=deadline)
    return _incidence_graph(scene, decomposition, supports, task_goal, transit, deadline=deadline)


def build_geometry_graph(scene, decomposition, supports=None, task_goal=None, *, deadline=None):
    """Exact raw-cell incidence without mandatory overlapping-box expansion.

    This is a covering substrate, not a selected route. Every raw cell/portal
    survives, including boundary strata. Contact attachments use the same
    positive-overlap convention as the legacy graph.
    """
    return _incidence_graph(scene, decomposition,
        support_components(decomposition) if supports is None else supports,
        task_goal, [c.bounds for c in decomposition.cells], deadline=deadline, raw=True)


def _incidence_graph(scene, decomposition, supports, task_goal, transit, *, deadline=None, raw=False):
    regions=[RouteRegion('air:'+geometry_key(box),'air_volume',(box,),
        tuple(c.id for c in decomposition.cells if intersection(c.bounds,box) is not None),
        transit_key='transit:'+geometry_key(box)) for box in transit]
    air_regions={r.key:r for r in regions}
    regions += [RouteRegion(s.key, 'support_surface', s.cover, s.cells, (s.owner,), s.solids) for s in supports]
    transitions = []

    def link(source, target, kind, cover, portals=(), patches=()):
        key = 'transition:' + geometry_key((source, target, kind, cover))
        families = () if kind == 'open_portal' else ('normal', 'stepping', 'ceiling')
        transitions.append(RouteTransition(key, source, target, kind, cover, portals, patches, families))

    transit_cells=[Cell(i,b,dimension(b)) for i,b in enumerate(transit)]
    for p in (decomposition.portals if raw else cell_portals(transit_cells,deadline=deadline)):
        a,b=(regions[i].key for i in p.cells)
        # Both transit regions are free convex cuboids. Their exact shared
        # patch is connected and free; the raw subdivisions only encode it.
        cover=(p.bounds,)
        portals=tuple(raw.id for raw in decomposition.portals if intersection(raw.bounds,p.bounds) is not None)
        link(a,b,'open_portal',cover,tuple(portals))
        link(b,a,'open_portal',cover,tuple(portals))
    for support in supports:
        from ..obstacle_geometry import expanded_box
        for region,volume in sorted(air_regions.items()):
            incident=[]
            for i in support.patches:
                patch=decomposition.contacts[i]
                overlap=intersection(volume.cover[0],patch.bounds)
                if overlap is None:
                    continue
                box=expanded_box(scene['collisionScene']['solids'][patch.solid])
                if all(overlap[a+3]>box[a] and overlap[a]<box[a+3] for a in (0,2)):
                    incident.append((i,overlap))
            if not incident:
                continue
            patches=tuple(i for i,_ in incident)
            cover = canonical_cover(b for _,b in incident)
            link(region, support.key, 'support_arrival', cover, patches=patches)
            link(support.key, region, 'support_departure', cover, patches=patches)

    start = scene['initial']['position']
    start_owner = next(b.get('sourceBlock', b['id']) for b in scene['blocks'] if b['id'] == scene['startBlock'])
    starts = tuple(s.key for s in supports if scene['initial'].get('onGround', True) and
                   s.owner == start_owner and any(contains(b, start) for b in s.cover))
    # Do not fabricate grounded contact if the actual initial state is airborne.
    if not starts:
        starts = tuple(sorted(r.key for r in air_regions.values() if any(contains(b,start) for b in r.cover)))
    if task_goal is not None and task_goal.get('kind') == 'landing_height_region':
        from ..terminal_goals import prepare_goal
        task_goal = prepare_goal(task_goal)
        terminal = 'terminal:' + geometry_key(task_goal)
        regions.append(RouteRegion(terminal, 'terminal_predicate', (), (), predicate=task_goal))
        # Coverage bounds are only collision-query limits, never finish walls.
        # A finish link is unresolved until the descending height clock and rows
        # hold; it is not permission to leave this volume through a solid.
        for region in list(regions):
            if region.kind in ('air_volume', 'support_surface'):
                possible=False
                for box in region.cover:
                    if box[1] > task_goal['height']+1e-7:
                        continue
                    if region.kind=='air_volume' and box[1]>=task_goal['height']:
                        # A free descending crossing ends below this height.
                        # Actual clipping at that plane finishes through the
                        # explicit support state, preserving that alternative.
                        continue
                    clipped=list(box)
                    for row in task_goal['rows']:
                        axis=0 if row['mode']=='X' else 2
                        if row['cmp']=='GE':
                            clipped[axis]=max(clipped[axis],row['rhs'])
                        else:
                            clipped[axis+3]=min(clipped[axis+3],row['rhs'])
                    possible |= all(clipped[a] <= clipped[a+3] for a in (0,2))
                if possible:
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


def annotate_normal_proposals(scene, graph, compiler, *, seconds):
    """Order transfers using existing zero-threshold normal movement builders.

    Missing normal profiles do not exclude stepping/ceiling/unknown methods.
    This is an optimistic proposal screen, never a feasibility certificate.
    """
    import math
    import time
    from standalone.ipopt.ladder import REST_CARRY
    from ..block_search import minimum_distance
    from ..movement_generation import generate_choices
    from ..movement_search import movement_settings
    from ..open_movement import approaches, first_descent
    deadline=time.monotonic()+seconds
    def remaining():
        if time.monotonic()>=deadline:
            raise TimeoutError('spatial_proposal_deadline')
        return deadline-time.monotonic()
    sample=compiler.compile(dict(scene,sharedMovement=True),dict(grounded=[True,True,False],jump=[False,True,False]),remaining())
    speed=max(math.hypot(*scene['initial']['velocity'][::2]),
              max(f*m/(1-f) for m,f in zip(sample['magnitude'],sample['friction'])))+1e-8
    horizon=movement_settings(scene)['maxInputs']
    bounds={s.key:[(b[0],b[3],b[2],b[5]) for b in s.cover] for s in graph.supports}
    flights={}
    by_solid={}
    for s in graph.supports:
        for solid in s.solids:
            by_solid.setdefault(solid,[]).append(s)
    transfers={}
    flight_proposals={}
    virtual=next((r.predicate for r in graph.regions if r.kind=='terminal_predicate'),None)
    def radius_for(grounded,jump):
        key=(tuple(grounded),tuple(jump))
        if key not in flights:
            flight=compiler.compile(dict(scene,sharedMovement=True),dict(grounded=grounded,jump=jump),remaining())
            radius=0.;carry=speed
            for magnitude,friction in zip(flight['magnitude'],flight['friction']):
                move=carry+magnitude;radius+=move;carry=move*friction
            flights[key]=radius+1e-7*(1+radius)
        return flights[key]
    for source in graph.supports:
        state=dict(position=source.height,velocity=REST_CARRY,onGround=True,previousHorizontalCollision=False)
        ctx=dict(mode='open',scene=scene,state=state,maxInputs=horizon,threshold=0.,checkBudget=remaining)
        air=dict(maxY=source.height,normalFinish=False,evidence='normal_proposal_only')
        for _,jumping,trace in approaches(ctx):
            air['maxY']=max(air['maxY'],max(trace['position']))
            if virtual is not None:
                tick=first_descent(trace,virtual['height'])
                if tick is not None:
                    n=tick+1
                    radius=radius_for([True]+[False]*(n-1),[jumping]+[False]*(n-1))
                    target=[-math.inf,math.inf,-math.inf,math.inf]
                    for row in virtual['rows']:
                        axis=0 if row['mode']=='X' else 2
                        index=axis if row['cmp']=='GE' else axis+1
                        target[index]=(max if row['cmp']=='GE' else min)(target[index],row['rhs'])
                    gap=min(minimum_distance(b,target) for b in bounds[source.key])
                    air['normalFinish'] |= gap<=radius
        flight_proposals[source.key]=air
        for choice in generate_choices('normal',ctx):
            profile=choice['profile']
            block=next(b for b in scene['blocks'] if b['id']==choice['terminalSupport'])
            targets=by_solid.get(block['solidIndex'],[])
            n=profile['airtime']
            radius=radius_for(profile['grounded'][:-1],profile['jump'])
            for target in targets:
                gap=min(minimum_distance(a,b) for a in bounds[source.key] for b in bounds[target.key])
                if gap<=radius:
                    transfers.setdefault(geometry_key((source.key,target.key)),dict(source=source.key,target=target.key,
                        capability='not_ruled_out',evidence='normal_proposal_optimistic_radius',threshold=0.,profiles=[]))['profiles'].append(
                            dict(airInputs=n,radius=radius,gap=gap))
    graph.transfers=transfers
    graph.flight_proposals=flight_proposals
    graph.context=geometry_key((graph.context,transfers,flight_proposals))
    return graph


def diagnostic_svg(graph, *, y, bounds=None, support_all_heights=False):
    """Draw actual finite region rectangles, with physical support outlines."""
    boxes = [b for r in graph.regions for b in r.cover]
    if not boxes:
        raise ValueError('No finite route regions to draw')
    view=tuple(bounds) if bounds is not None else (
        min(b[0] for b in boxes),0,min(b[2] for b in boxes),max(b[3] for b in boxes),0,max(b[5] for b in boxes))
    x,z=view[0],view[2]
    width,height=view[3]-x,view[5]-z
    stroke = max(width, height)/600
    lines = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{x} {z} {width} {height}">']
    for i, region in enumerate(graph.regions):
        for b in region.cover:
            if b[1] <= y <= b[4] or support_all_heights and region.kind=='support_surface':
                support = region.kind == 'support_surface'
                fill = 'none' if support else f'hsl({i*137%360},50%,85%)'
                color = 'red' if support else 'black'
                lines.append(f'<rect x="{b[0]}" y="{b[2]}" width="{b[3]-b[0]}" height="{b[5]-b[2]}" '
                             f'fill="{fill}" fill-opacity="0.25" stroke="{color}" stroke-width="{stroke*(3 if support else 1)}"/>')
    return '\n'.join(lines + ['</svg>'])
