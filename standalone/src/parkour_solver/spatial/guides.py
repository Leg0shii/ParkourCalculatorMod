"""Finite local guide witnesses, never waypoint or cell-containment rows."""

import heapq
import math

from parkour_solver.spatial.checkpoints import Checkpoint, horizontal_segments
from parkour_solver.spatial.cells import box_segment_interval
from parkour_solver.spatial.regions import geometry_key
from parkour_solver.spatial.compat.corridors import conditioned_anchor


def task_reference(data, origin):
    task = data['task'] or dict(kind='landing', block=data['scene']['goalBlock'])
    if task['kind'] == 'landing':
        block = next(b for b in data['scene']['blocks'] if b['id'] == task['block'])
        groups = [s for s in data['surfaces'] if block['solidIndex'] in s['solids'] and s['height'] == block['top']]
        return conditioned_anchor([b for s in groups for b in s['cover']], origin, origin) if groups else tuple(origin)
    point = list(origin); point[1] = task['height']
    for row in task['rows']:
        axis = 0 if row['mode'] == 'X' else 2
        point[axis] = (max if row['cmp'] == 'GE' else min)(point[axis], row['rhs'])
    return tuple(point)


def segment_in_cover(boxes, a, b):
    intervals = sorted(i for box in boxes if (i := box_segment_interval(box, a, b)) is not None)
    through = 0.
    for lo, hi in intervals:
        if lo > through+1e-9:
            return False
        through = max(through, hi)
    return bool(intervals) and through >= 1.-1e-9


def guide_for(data, graph, request, entry, *, max_expansions=5000, continuation=None, check=None):
    """Dijkstra witness with local anchors and a resumable residual frontier.

    One chosen path orders/warm-starts work. The request's connector still
    references every backing alternative. No contact event is traversed en route
    to another contact, so a witness cannot silently insert a landing.
    """
    import copy
    event = next(e for e in graph['events'] if e['id'] == request['event'])
    target = (conditioned_anchor(event['cover'], entry, task_reference(data, entry)) if event['cover'] else
              task_reference(data, entry))
    regions = {r['key']: r for r in data['geometry']['regions']}
    edges = {}
    for edge in data['geometry']['transitions']:
        edges.setdefault(edge['source'], []).append(edge)
    goal = set(request['connector']['exit'])
    anchors = {k: conditioned_anchor(r['cover'], entry, target) if r['cover'] else target for k,r in regions.items()}
    key = geometry_key((request['id'], entry, target))
    if continuation is not None and continuation['context'] != key:
        raise ValueError('Incompatible guide witness continuation')
    state = copy.deepcopy(continuation) if continuation is not None else dict(schema=1, context=key,
        frontier=[[0.,k,[],[]] for k in request['connector']['entry']], seen=[], witness=None)
    heapq.heapify(state['frontier'])
    seen = set(state['seen'])
    expanded = 0
    while state['frontier'] and state['witness'] is None and expanded < max_expansions:
        if check:
            try:
                check()
            except TimeoutError:
                break
        cost, node, path, links = heapq.heappop(state['frontier'])
        if node in seen:
            continue
        seen.add(node); expanded += 1
        path = path+[node]
        if node in goal:
            state['witness'] = dict(regions=path, transitions=links)
            break
        for edge in edges.get(node, []):
            if edge['kind'] not in ('open_portal','support_departure') and edge['target'] not in goal:
                continue
            if edge['target'] in seen:
                continue
            weight = math.dist(anchors[node], anchors[edge['target']])
            heapq.heappush(state['frontier'], [cost+weight, edge['target'], path, links+[edge['key']]])
    state['seen'] = sorted(seen)
    points = [tuple(entry)]
    witness = state['witness']
    if witness is not None:
        by_key = {e['key']:e for e in data['geometry']['transitions']}
        for key in witness['transitions']:
            edge = by_key[key]
            if edge['cover']:
                points.append(conditioned_anchor(edge['cover'], entry, target))
        points.append(tuple(target))
        boxes = [b for k in witness['regions'] for b in regions[k]['cover']]
        # There are no internal semantic events in this witness. The required
        # next event remains the endpoint, never an optional removed waypoint.
        simplified = [points[0]]
        while len(points) > 1:
            end = next((i for i in range(len(points)-1, 0, -1)
                        if segment_in_cover(boxes, points[0], points[i])), 1)
            simplified.append(points[end]); points = points[end:]
        points = simplified
    else:
        # An unchecked direct line is not presented as a geometric witness.
        points = [tuple(entry)]
    return dict(schema=1, id='guide:'+geometry_key((request['id'],points)), request=request['id'],
        points=[list(p) for p in points], target=list(target), witness=witness,
        status='witness' if witness else 'unresolved', continuation=state,
        hardConstraints=False, expanded=expanded)


def outgoing_objective(data, graph, request, guide):
    """One fixed scoring interpretation per next-goal request.

    Future refinements remain latent; changing this objective later requires a
    different score context rather than overwriting a repair continuation.
    """
    if request['terminal']:
        return dict(data['scene']['objective'])
    start = guide['target']
    final = next(e for e in graph['events'] if e['terminal'])
    current = next(e for e in graph['events'] if e['id']==request['event'])
    onward = dict(request, id='onward:'+geometry_key((request['id'],start)), event=final['id'],
        terminal=True, connector=dict(entry=current['attachments'],exit=final['attachments']))
    witness = guide_for(data,graph,onward,start)
    if witness['witness']:
        from types import SimpleNamespace
        segments=horizontal_segments(SimpleNamespace(points=witness['points']))
        if segments:
            first=segments[0]
            return Checkpoint(1,-1,first['direction'],tuple(first['start']),tuple(first['end']),()).objective
    end = task_reference(data, start)
    dx, dz = end[0]-start[0], end[2]-start[2]
    length = math.hypot(dx, dz)
    if length <= 1e-9:
        return dict(data['scene']['objective'])
    return Checkpoint(1,-1,(dx/length,dz/length),tuple(start),tuple(end),()).objective
