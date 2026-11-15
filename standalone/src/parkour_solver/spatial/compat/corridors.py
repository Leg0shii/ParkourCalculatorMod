import heapq
import itertools
import math
import time
from dataclasses import asdict, dataclass, field, fields

from parkour_solver.spatial.cells import contains, intersection, merge_boxes, segment_intervals
from parkour_solver.scene.geometry import expanded_box
from parkour_solver.scene.route import center_bounds


def midpoint(box):
    return tuple((box[a] + box[a + 3]) / 2 for a in range(3))


def goal_point(scene):
    block = next(b for b in scene['blocks'] if b['id'] == scene['goalBlock'])
    return ((block['bounds'][0] + block['bounds'][1]) / 2, block['top'],
            (block['bounds'][2] + block['bounds'][3]) / 2)


def goal_region(scene):
    block = next(b for b in scene['blocks'] if b['id'] == scene['goalBlock'])
    b = center_bounds(block)
    margin = scene.get('supportClearance', 1e-6)
    return (b[0]+margin, block['top'], b[2]+margin, b[1]-margin, block['top'], b[3]-margin)


def distance_to_box(point, box):
    return math.sqrt(sum(max(box[a]-point[a], 0, point[a]-box[a+3])**2 for a in range(3)))


def landmarks(scene):
    start, goal = scene['initial']['position'], goal_point(scene)
    axis = 0 if abs(goal[0] - start[0]) >= abs(goal[2] - start[2]) else 2
    lateral = 2 if axis == 0 else 0
    terminal = goal_region(scene)
    result = []
    for box in merge_boxes(scene['collisionScene']['solids']):
        plane = (box[axis] + box[axis + 3]) / 2
        if min(start[axis], goal[axis]) < plane < max(start[axis], goal[axis]) and not terminal[axis] <= plane <= terminal[axis+3]:
            expanded = expanded_box(box)
            result.append(dict(id=len(result), axis=axis, lateral=lateral, plane=plane,
                lo=expanded[lateral], hi=expanded[lateral + 3], box=box))
    return result


def crossings(points, gates):
    events = []
    for gate in gates:
        axis, plane = gate['axis'], gate['plane']
        previous = 0
        for index, (a, b) in enumerate(zip(points, points[1:])):
            first = (a[axis] > plane) - (a[axis] < plane)
            second = (b[axis] > plane) - (b[axis] < plane)
            if first:
                previous = first
            if not second:
                continue
            if previous and second != previous:
                f = (plane-a[axis])/(b[axis]-a[axis])
                value = a[gate['lateral']] + f*(b[gate['lateral']]-a[gate['lateral']])
                side = -1 if value <= gate['lo'] + 1e-7 else 1 if value >= gate['hi'] - 1e-7 else 0
                token = (gate['id'], second, side)
                events.append((index, f, gate['id'], token))
            previous = second
    return [dict(token=token, segment=index) for index, _, _, token in sorted(events)]


def signature(points, gates):
    return tuple(e['token'] for e in crossings(points, gates))


def projected_boxes(decomposition, cell_ids):
    return merge_boxes([(decomposition.cells[i].bounds[0], 0, decomposition.cells[i].bounds[2],
                         decomposition.cells[i].bounds[3], 0, decomposition.cells[i].bounds[5]) for i in cell_ids])


def anchor_box(decomposition, point):
    patches = [p.bounds for p in decomposition.contacts if p.kind == 'support' and contains(p.bounds, point, 1e-7)]
    if not patches:
        return tuple(point) + tuple(point)
    return tuple(min(p[a] for p in patches) for a in range(3)) + tuple(max(p[a+3] for p in patches) for a in range(3))


def walk_projection(decomposition, cells, portals, start, goal):
    boxes = []
    for i, cell in enumerate(cells):
        before = decomposition.portals[portals[i-1]].bounds if i else anchor_box(decomposition, start)
        after = decomposition.portals[portals[i]].bounds if i < len(portals) else anchor_box(decomposition, goal)
        envelope = tuple(min(before[a], after[a]) for a in range(3)) + tuple(max(before[a+3], after[a+3]) for a in range(3))
        clip = intersection(envelope, decomposition.cells[cell].bounds)
        if clip is not None:
            boxes.append((clip[0], 0, clip[2], clip[3], 0, clip[5]))
    return merge_boxes(boxes)


def prescribed_projection(decomposition, cells, points):
    return projected_boxes(decomposition, [c.id for c in decomposition.cells])


@dataclass
class Corridor:
    id: int
    cells: list
    portals: list
    points: list
    signature: tuple
    projection: list
    geometric_length: float
    variants: int = 1
    source: str = 'automatic'
    stage_paths: list = field(default_factory=list)
    schema: int = 1
    graph_context: str = ''
    semantic_key: tuple = ()
    stages: list = field(default_factory=list)
    route_variants: list = field(default_factory=list)
    region_graph: dict = field(default_factory=dict)

    def to_dict(self, *, include_graph=True):
        if include_graph:
            return asdict(self)
        import copy
        result={f.name:copy.deepcopy(getattr(self,f.name)) for f in fields(self) if f.name!='region_graph'}
        return dict(result,region_graph={})


def corridor_for_points(decomposition, points, gates, *, route_id=0, source='prescribed'):
    if len(points) < 2 or any(len(p) != 3 or any(not math.isfinite(v) for v in p) for p in points):
        raise ValueError('A prescribed spatial route requires finite 3D points')
    cell_ids, ordered_cells = set(), []
    for a, b in zip(points, points[1:]):
        intervals = segment_intervals(decomposition, a, b)
        reach = 0.
        if not intervals:
            raise ValueError('Prescribed spatial route leaves free space')
        for lo, hi, cell in intervals:
            if lo > reach:
                break
            reach = max(reach, hi)
            if hi > lo or a == b:
                cell_ids.add(cell)
        if reach < 1:
            raise ValueError('Prescribed spatial route crosses an obstacle or leaves coverage')
        covered = 0.
        while covered < 1:
            available = [(hi, decomposition.cells[cell].dimension, -cell, cell) for lo, hi, cell in intervals if lo <= covered and hi > covered]
            if not available:
                raise ValueError('Prescribed itinerary has a coverage gap')
            hi, _, _, cell = max(available)
            if not ordered_cells or ordered_cells[-1] != cell:
                ordered_cells.append(cell)
            covered = hi
    cells = sorted(cell_ids)
    return Corridor(route_id, cells, [], [list(p) for p in points], signature(points, gates),
        prescribed_projection(decomposition, cells, points), sum(math.dist(a, b) for a, b in zip(points, points[1:])), source=source)


def enumerate_routes(scene, decomposition, *, max_routes=50, max_expansions=5000, max_steps=64,
                     max_visits=2, deadline=None, cancel_event=None, route_graph=None, continuation=None):
    if route_graph is not None:
        return enumerate_semantic_routes(scene, decomposition, route_graph, max_routes=max_routes,
            max_expansions=max_expansions, max_steps=max_steps, max_visits=max_visits,
            deadline=deadline, cancel_event=cancel_event, continuation=continuation)
    if continuation is not None:
        raise ValueError('Horizontal signature enumeration has no exact continuation; use a region graph')
    for key, value in dict(max_routes=max_routes, max_expansions=max_expansions, max_steps=max_steps, max_visits=max_visits).items():
        if type(value) is not int or value < 1:
            raise ValueError(key + ' must be a positive integer')
    start, goal = tuple(scene['initial']['position']), goal_point(scene)
    terminal = goal_region(scene)
    starts = decomposition.locate(start)
    goals = {c.id: overlap for c in decomposition.cells if (overlap := intersection(c.bounds, terminal)) is not None}
    gates, adjacent = landmarks(scene), decomposition.neighbors()
    serial = itertools.count()
    queue = [(distance_to_box(start, terminal), next(serial), 0., c, (c,), (), (start,)) for c in starts]
    heapq.heapify(queue)
    representatives, grouped = {}, {}
    expanded, dominated, visit_drops, depth_drops, duplicates = 0, 0, 0, 0, 0
    termination = 'exhausted'
    while queue:
        if cancel_event is not None and cancel_event.is_set():
            termination = 'cancelled'
            break
        if deadline is not None and time.monotonic() >= deadline:
            termination = 'deadline'
            break
        if expanded >= max_expansions:
            termination = 'expansion_limit'
            break
        _, _, length, cell, cells, portals, points = heapq.heappop(queue)
        sig = signature(points, gates)
        repeats = tuple(sorted(c for c in set(cells) if cells.count(c) > 1))
        key = (cell, sig, repeats)
        if representatives.get(key, math.inf) <= length:
            dominated += 1
            continue
        representatives[key] = length
        expanded += 1
        if cell in goals:
            endpoint = midpoint(goals[cell])
            finished = points + (endpoint,)
            full_sig = signature(finished, gates)
            if full_sig in grouped:
                corridor = grouped[full_sig]
                corridor.cells = sorted(set(corridor.cells) | set(cells))
                corridor.projection = projected_boxes(decomposition, [c.id for c in decomposition.cells])
                corridor.variants += 1
                duplicates += 1
            else:
                grouped[full_sig] = Corridor(len(grouped), list(cells), list(portals), [list(p) for p in finished],
                    full_sig, projected_boxes(decomposition, [c.id for c in decomposition.cells]), length + math.dist(points[-1], endpoint))
                if len(grouped) >= max_routes:
                    termination = 'route_limit'
                    break
            continue
        if len(cells) >= max_steps:
            depth_drops += 1
            continue
        for neighbor, portal_id in adjacent[cell]:
            if cells.count(neighbor) >= max_visits:
                visit_drops += 1
                continue
            p = midpoint(decomposition.portals[portal_id].bounds)
            distance = length + math.dist(points[-1], p)
            next_points = points + (p,)
            next_signature = signature(next_points, gates)
            reverse_count = sum(token[1] != (1 if goal[gates[token[0]]['axis']] > start[gates[token[0]]['axis']] else -1) for token in next_signature)
            revisit_count = len(cells) + 1 - len(set(cells + (neighbor,)))
            ordering_penalty = (reverse_count + revisit_count) * math.dist(decomposition.bounds[:3], decomposition.bounds[3:])
            heapq.heappush(queue, (distance + distance_to_box(p, terminal) + ordering_penalty, next(serial), distance,
                neighbor, cells + (neighbor,), portals + (portal_id,), points + (p,)))
    return list(grouped.values()), dict(termination=termination, expanded=expanded, remaining=len(queue),
        dominanceDrops=dominated, visitDrops=visit_drops, depthDrops=depth_drops, groupedDuplicates=duplicates,
        routes=len(grouped), maxRoutes=max_routes, maxExpansions=max_expansions, maxSteps=max_steps,
        maxVisits=max_visits, coverageProven=False, missingStart=not starts, missingGoal=not goals)


def conditioned_anchor(cover, before, after):
    """Project a local reference into a finite opening; never create a waypoint row."""
    reference = tuple((a+b)/2 for a, b in zip(before, after))
    points = [tuple(min(max(reference[a], b[a]), b[a+3]) for a in range(3)) for b in cover]
    return min(points, key=lambda p: (math.dist(before, p)+math.dist(p, after), p))


def semantic_events(graph, region_path, transition_path):
    """Conservative ordered itinerary, compressing only unambiguous air chains.

    Keep uncertain passage equivalence separate. In particular no X/Z side
    signatures, unordered region unions, or visited-support sets define identity.
    """
    regions = {r.key: r for r in graph.regions}
    transitions = {t.key: t for t in graph.transitions}
    initial=regions[region_path[0]]
    events = [('start', initial.transit_key or initial.key)]
    for key in transition_path:
        t = transitions[key]
        if t.kind == 'support_arrival':
            events.append(('support', t.target))
        elif t.kind == 'support_departure':
            events.append(('departure',t.source,regions[t.target].transit_key or t.target))
        elif t.kind == 'finish':
            events.append(('finish', t.target))
        elif t.kind == 'open_portal':
            before = regions[t.source].transit_key or t.source
            after = regions[t.target].transit_key or t.target
            if before == after:
                continue
            events.append(('passage', before, after))
    return tuple(events)


def corridor_from_walk(scene, decomposition, graph, region_path, transition_path, *, route_id=0):
    regions = {r.key: r for r in graph.regions}
    transitions = {t.key: t for t in graph.transitions}
    start = tuple(scene['initial']['position'])
    destination = regions[region_path[-1]]
    endpoint = conditioned_anchor(destination.cover, start, start) if destination.cover else start
    if destination.kind == 'terminal_predicate':
        endpoint = list(start)
        endpoint[1] = destination.predicate['height']
        for row in destination.predicate['rows']:
            axis = 0 if row['mode'] == 'X' else 2
            endpoint[axis] = (min if row['cmp'] == 'LE' else max)(endpoint[axis], row['rhs'])
        endpoint = tuple(endpoint)
    # Guide references use the neighboring selected support boundaries. Distant
    # query-envelope corners and whole-air midpoints have no role in this guide.
    references = {0: start, len(transition_path): endpoint}
    for i, key in enumerate(transition_path):
        t = transitions[key]
        if t.kind == 'support_arrival':
            references[i+1] = conditioned_anchor(t.cover, start, endpoint)
    points = [start]
    stages = []
    for i, key in enumerate(transition_path):
        t = transitions[key]
        before = references[max(k for k in references if k <= i)]
        after = references[min(k for k in references if k >= i+1)]
        if t.cover:
            points.append(conditioned_anchor(t.cover, before, after))
        if t.kind != 'support_departure':
            stages.append(dict(occurrence=len(stages), kind=t.kind, source=t.source, target=t.target,
                transition=t.key, cover=t.cover, cells=regions[t.source].cells,
                portals=t.portals, predicate=regions[t.target].predicate,guide_point=points[-1]))
    if points[-1] != endpoint:
        points.append(endpoint)
    cells = sorted({c for key in region_path for c in regions[key].cells if regions[key].kind == 'air_volume'})
    portals = [p for key in transition_path for p in transitions[key].portals]
    variant = dict(regions=list(region_path), transitions=list(transition_path))
    return Corridor(route_id, cells, portals, [list(p) for p in points], (),
        projected_boxes(decomposition, cells), sum(math.dist(a,b) for a,b in zip(points, points[1:])),
        source='automatic', stage_paths=[], schema=2, graph_context=graph.context,
        semantic_key=semantic_events(graph, region_path, transition_path), stages=stages,
        route_variants=[variant], region_graph=graph.to_dict())


def enumerate_semantic_routes(scene, decomposition, graph, *, max_routes=50, max_expansions=5000,
                              max_steps=64, max_visits=2, deadline=None, cancel_event=None, continuation=None):
    import copy
    from parkour_solver.spatial.regions import geometry_key
    for key, value in dict(max_routes=max_routes, max_expansions=max_expansions,
                           max_steps=max_steps, max_visits=max_visits).items():
        if type(value) is not int or value < 1:
            raise ValueError(key + ' must be a positive integer')
    context = geometry_key((graph.context, max_steps, max_visits, scene['initial']))
    if continuation is not None and (continuation.get('schema') != 1 or continuation.get('context') != context):
        raise ValueError('Incompatible spatial continuation; graph or route domain changed')
    state = copy.deepcopy(continuation) if continuation is not None else dict(schema=1, context=context,
        frontier=[dict(regions=[s], transitions=[]) for s in graph.starts], emitted=[], expanded=0,
        duplicates=0, depthDrops=0, visitDrops=0)
    regions = {r.key: r for r in graph.regions}
    adjacent = graph.neighbors()
    targets = [b for key in graph.goals for b in regions[key].cover]
    start = tuple(scene['initial']['position'])

    def priority(walk):
        cover = regions[walk['regions'][-1]].cover
        # Minimum region distance is an ordering hint, not a transit-time bound.
        distance = min((distance_to_box(conditioned_anchor(cover, start, start), b)
                        for b in targets), default=0.) if cover else 0.
        supports=[key for key in walk['regions'] if regions[key].kind=='support_surface']
        unknown=sum(geometry_key((a,b)) not in graph.transfers for a,b in zip(supports,supports[1:])) if graph.transfers else 0
        last=None;episode_unknown=False
        for key in walk['regions']:
            region=regions[key]
            if region.kind=='support_surface':
                last=key;episode_unknown=False
            elif last in graph.flight_proposals:
                proposal=graph.flight_proposals[last]
                if region.kind=='air_volume' and not episode_unknown and all(b[1]>proposal['maxY']+1e-7 for b in region.cover):
                    unknown+=1;episode_unknown=True
                if region.kind=='terminal_predicate' and not proposal['normalFinish']:
                    unknown+=1
        if len(walk['regions'])>1 and regions[walk['regions'][0]].kind=='support_surface':
            first=regions[walk['regions'][1]]
            unknown+=int(not any(contains(b,start) for b in first.cover))
        return (unknown,len(walk['transitions']), distance, tuple(walk['transitions']))

    # Give newly discovered contact itineraries service before spending the
    # entire route budget on raw passage encodings of one support sequence.
    state.setdefault('served',{})
    frontier = {}
    serial = itertools.count()
    def enqueue(walk):
        family = next((key for key in reversed(walk['regions']) if regions[key].kind=='support_surface'),walk['regions'][0])
        if walk['regions'][-1] in graph.goals:
            family=(family,'terminal',walk['regions'][-2] if len(walk['regions'])>1 else walk['regions'][-1])
        else:
            family=(family,walk['regions'][-1])
        if len(walk['regions'])>1:
            family=geometry_key((walk['regions'][1],family))
        else:
            family=geometry_key(family)
        heapq.heappush(frontier.setdefault(family,[]),(priority(walk),next(serial),walk))
    for walk in state['frontier']:
        enqueue(walk)
    graph_data=graph.to_dict()
    grouped = {geometry_key(c['semantic_key']): Corridor(**dict(c,region_graph=graph_data)) for c in state['emitted']}
    expanded = 0
    termination = 'exhausted'
    while frontier:
        if cancel_event is not None and cancel_event.is_set():
            termination = 'cancelled'
            break
        if deadline is not None and time.monotonic() >= deadline:
            termination = 'deadline'
            break
        if len(grouped) >= max_routes:
            termination = 'route_limit'
            break
        if expanded >= max_expansions:
            termination = 'expansion_limit'
            break
        family = min(frontier,key=lambda k:(frontier[k][0][0][0],state['served'].get(k,0),frontier[k][0][0][1:],k))
        _, _, walk = heapq.heappop(frontier[family])
        if not frontier[family]:
            del frontier[family]
        state['served'][family] = state['served'].get(family,0)+1
        path, edges = walk['regions'], walk['transitions']
        current = path[-1]
        expanded += 1
        if current in graph.goals:
            corridor = corridor_from_walk(scene, decomposition, graph, path, edges, route_id=len(grouped))
            corridor.region_graph=graph_data
            key = geometry_key(corridor.semantic_key)
            if key in grouped:
                existing = grouped[key]
                if corridor.route_variants[0] not in existing.route_variants:
                    existing.route_variants.extend(corridor.route_variants)
                    existing.variants = len(existing.route_variants)
                state['duplicates'] += 1
            else:
                grouped[key] = corridor
            continue
        if len(path) >= max_steps:
            state['depthDrops'] += 1
            continue
        for t in adjacent[current]:
            if t.capability == 'ruled_out':
                continue
            if len(path) > 1 and t.target == path[-2] and t.kind != 'support_departure':
                # Immediate face touch-and-return and same-neighborhood setup
                # self-hops are local alternatives. Wider returns remain walks.
                continue
            last_contact = max((i for i,key in enumerate(path) if regions[key].kind=='support_surface'),default=0)
            visits = path if regions[t.target].kind=='support_surface' else path[last_contact:]
            if visits.count(t.target) >= max_visits:
                state['visitDrops'] += 1
                continue
            next_walk = dict(regions=path+[t.target], transitions=edges+[t.key])
            enqueue(next_walk)
    state['expanded'] += expanded
    state['frontier'] = [w for _, _, w in sorted(item for queue in frontier.values() for item in queue)]
    state['emitted'] = [c.to_dict(include_graph=False) for c in grouped.values()]
    return list(grouped.values()), dict(termination=termination, expanded=state['expanded'],
        sessionExpanded=expanded, remaining=len(state['frontier']), routes=len(grouped),
        rawWalks=state['expanded'], groupedDuplicates=state['duplicates'], depthDrops=state['depthDrops'],
        visitDrops=state['visitDrops'], maxRoutes=max_routes, maxExpansions=max_expansions,
        maxSteps=max_steps, maxVisits=max_visits, coverageProven=False, movementFeasibility='unresolved',
        missingStart=not graph.starts, missingGoal=not graph.goals, continuation=state)


def horizontal_trace(candidate):
    positions = candidate['positions']
    ys = candidate.get('y', [0.] * len(positions))
    if len(ys) != len(positions):
        raise ValueError('Trajectory Y/XZ lengths disagree')
    points, references = [], []
    for t, (a, b) in enumerate(zip(positions, positions[1:])):
        phases = [(a[0], ys[t], a[1]), (a[0], ys[t+1], a[1]),
                  (b[0], ys[t+1], a[1]), (b[0], ys[t+1], b[1])]
        if not points:
            points.append(phases[0])
        points.extend(phases[1:])
        references.extend([(t, t), (t+1, t), (t+1, t+1)])
    return points, references


def projected_segment_covered(projection, a, b, tolerance=1e-7):
    intervals = []
    for box in projection:
        lo, hi = 0., 1.
        for axis in (0, 2):
            delta = b[axis] - a[axis]
            if delta == 0:
                if not box[axis]-tolerance <= a[axis] <= box[axis+3]+tolerance:
                    hi = -1
                    break
            else:
                low, high = sorted(((box[axis]-tolerance-a[axis])/delta, (box[axis+3]+tolerance-a[axis])/delta))
                lo, hi = max(lo, low), min(hi, high)
        if lo <= hi:
            intervals.append((lo, hi))
    reach = 0.
    if not intervals:
        return False
    for lo, hi in sorted(intervals):
        if lo > reach:
            return False
        reach = max(reach, hi)
    return reach >= 1


def audit_candidate(corridor, candidate, gates, *, terminal_pre=False, partial=False):
    points, references = horizontal_trace(candidate)
    if terminal_pre and len(points) >= 4:
        points, references = points[:-2], references[:-2]
    for i, (a, b) in enumerate(zip(points, points[1:])):
        if not projected_segment_covered(corridor.projection, a, b):
            return dict(accepted=False, reason='corridor_projection', segment=i, references=references[i])
    events = crossings(points, gates)
    actual = tuple(e['token'] for e in events)
    expected = corridor.signature[:len(actual)] if partial else corridor.signature
    if actual != expected:
        mismatch = next((i for i, pair in enumerate(zip(actual, corridor.signature)) if pair[0] != pair[1]), min(len(actual), len(corridor.signature)))
        return dict(accepted=False, reason='corridor_signature', mismatch=mismatch, actual=actual,
                    events=events, references=references)
    return dict(accepted=True, signature=actual, partial=partial, validation='horizontal_corridor_and_ordered_gate_crossings')
