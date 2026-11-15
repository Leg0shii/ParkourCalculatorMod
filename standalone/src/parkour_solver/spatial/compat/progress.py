"""Ordered 3D route membership, independent of representative guide points.

Each possible cursor belongs to one complete raw variant. Shared regions do
not permit switching to a different variant halfway through a movement.
"""

from parkour_solver.spatial.cells import box_segment_interval, contains


def initial_cursor(route):
    if route.schema != 2:
        raise ValueError('Typed route progress requires corridor schema2')
    regions = {r['key']: r for r in route.region_graph['regions']}
    return dict(context=route.graph_context, possible=[dict(variant=i, occurrence=0,
        lastSupport=v['regions'][0] if regions[v['regions'][0]]['kind'] == 'support_surface' else None,
        passages=0, confirmed=0) for i, v in enumerate(route.route_variants)], unknown=False)


def phase_trace(candidate):
    from parkour_solver.spatial.compat.corridors import horizontal_trace
    points, _ = horizontal_trace(candidate)
    return [dict(start=a, end=b, input=i//3, axis=('Y','X','Z')[i%3], phase=i)
            for i, (a,b) in enumerate(zip(points, points[1:]))]


def _phase(route, position, segment, regions, transitions, tolerance):
    variant = route.route_variants[position['variant']]
    path, edges = variant['regions'], variant['transitions']
    start, end = segment['start'], segment['end']
    pending = [(position, 0.)]
    seen, result = set(), []
    while pending:
        current, at = pending.pop()
        index = current['occurrence']
        identity = (index, at, current['lastSupport'], current['passages'], current['confirmed'])
        if identity in seen:
            continue
        seen.add(identity)
        region = regions[path[index]]
        intervals = [box_segment_interval(b, start, end, tolerance) for b in region['cover']]
        intervals = sorted(i for i in intervals if i is not None)
        reach = at
        occupied = False
        for lo, hi in intervals:
            if lo <= reach+tolerance and hi >= at-tolerance:
                occupied = True
                reach = max(reach, hi)
        if not occupied:
            continue
        if index > current['confirmed'] and region['kind'] == 'air_volume':
            incoming = transitions[edges[index-1]]
            entered = incoming['kind'] == 'support_departure'
            for patch in incoming['cover']:
                for box in region['cover']:
                    for axis in range(3):
                        if patch[axis] != patch[axis+3]:
                            continue
                        plane = patch[axis]
                        entered |= (box[axis] == plane < box[axis+3] and end[axis] > plane+tolerance
                                    or box[axis] < plane == box[axis+3] and end[axis] < plane-tolerance)
            if entered:
                current = dict(current, confirmed=index)
        if reach >= 1.-tolerance:
            result.append(current)
        if index >= len(edges):
            continue
        edge = transitions[edges[index]]
        if edge['kind'] not in ('open_portal', 'support_departure'):
            continue
        for patch in edge['cover']:
            interval = box_segment_interval(patch, start, end, tolerance)
            if interval is None:
                continue
            lo, hi = interval
            enter = max(at, lo)
            if enter > min(hi, reach)+tolerance:
                continue
            target=regions[edge['target']]
            significant=edge['kind']=='open_portal' and (
                (region.get('transit_key') or region['key']) != (target.get('transit_key') or target['key']))
            next_state = dict(current, occurrence=index+1,
                passages=current['passages'] + int(significant))
            pending.append((next_state, enter))
    return result


def _contact(route, position, event, point, regions, transitions, tolerance):
    variant = route.route_variants[position['variant']]
    index = position['occurrence']
    region = regions[variant['regions'][index]]
    if event.get('kind') == 'finish':
        if index < len(variant['transitions']):
            edge = transitions[variant['transitions'][index]]
            if edge['kind'] == 'finish' and event.get('validated'):
                return [dict(position, occurrence=index+1, confirmed=index+1)]
        return []
    matches = lambda r: (r['kind'] == 'support_surface' and
        (event.get('support') == r['key'] or event.get('owner') in r['owners']) and
        any(contains(b, point, tolerance) for b in r['cover']))
    result = []
    if matches(region):
        result.append(dict(position, lastSupport=region['key'], passages=0))
    if index < len(variant['transitions']):
        edge = transitions[variant['transitions'][index]]
        target = regions[edge['target']]
        if edge['kind'] == 'support_arrival' and matches(target):
            result.append(dict(position, occurrence=index+1, confirmed=index+1, lastSupport=target['key'], passages=0))
    previous = regions.get(position['lastSupport'])
    if previous is not None and not position['passages'] and matches(previous):
        # Repeated standing/setup contacts in the same neighborhood are local.
        result.append(position)
    return result


def advance(route, cursor, phase_segments, contact_events=(), scope=None, *, tolerance=1e-7):
    """Return compatible/ruled_out/unknown with all possible variant occurrences.

    Contacts are physical witnesses at the end of a phase, with `phase` and
    `support` or physical `owner`. Named internal probes must be marked unknown
    unless their complete owned envelope has been checked by the shared model.
    Scope may limit required completion, never silently add omitted future rows.
    """
    if cursor.get('context') != route.graph_context:
        raise ValueError('Route progress context changed')
    regions = {r['key']:r for r in route.region_graph['regions']}
    transitions = {t['key']:t for t in route.region_graph['transitions']}
    possible = list(cursor['possible'])
    unknown = cursor.get('unknown', False)
    achieved = []
    contacts = {}
    for event in contact_events:
        contacts.setdefault(event['phase'], []).append(event)
    for index, segment in enumerate(phase_segments):
        phase = segment.get('phase', index)
        if segment.get('uncertainProbes'):
            # Realized endpoints omit internal step/grind probes. Preserve the
            # pre-phase possibilities, and never certify further progress from
            # an incomplete trace. Full physical replay is a separate check.
            return dict(status='unknown', cursor=dict(cursor, possible=possible, unknown=True),
                        achieved=achieved, reason='unresolved_interaction_probes', phase=phase)
        possible = [p for old in possible for p in _phase(route, old, segment, regions, transitions, tolerance)]
        for event in contacts.get(phase, ()):
            possible = [p for old in possible for p in _contact(route, old, event, segment['end'],
                                                                regions, transitions, tolerance)]
            achieved.append(dict(kind='support_arrival', phase=phase, event=event))
        unique = {tuple(sorted(p.items())):p for p in possible}
        possible = list(unique.values())
        if not possible:
            return dict(status='ruled_out', cursor=dict(cursor, possible=[], unknown=unknown),
                        achieved=achieved, reason='route_phase_or_contact', phase=phase)
    complete = []
    for p in possible:
        variant = route.route_variants[p['variant']]
        complete.append(p['confirmed'] >= variant.get('required',len(variant['regions'])-1))
    require_complete = bool(scope and scope.get('complete'))
    status = 'unknown' if unknown else 'compatible'
    if require_complete and not any(complete):
        status = 'unknown' if unknown else 'ruled_out'
    return dict(status=status, cursor=dict(context=route.graph_context, possible=possible, unknown=unknown),
                achieved=achieved, complete=any(complete), reason=None if status == 'compatible' else 'route_incomplete')
