import copy
import itertools

from parkour_solver.movement.profiles import compile_open_air_route
from parkour_solver.constraints.avoidance import consecutive, intervals
from parkour_solver.scene.route import center_bounds
from parkour_solver.physics.vertical import REST_CARRY
from parkour_solver.schema import integer, number


def carry_vertices(lower, upper, threshold):
    points = []
    for lo, hi, survives in [(lower, min(upper, -threshold), True),
                             (max(lower, -threshold), min(upper, threshold), False),
                             (max(lower, threshold), upper, True)]:
        if lo <= hi:
            points.extend((v, v if survives else 0.) for v in (lo, hi))
    return sorted(set(points))


def _row(axis, cmp, rhs, where='in', option=None):
    return dict(axis=axis, cmp=cmp, rhs=rhs, where=where, option=option)


def _support_rows(block, kind):
    b = center_bounds(block, kind)
    return [_row(a, cmp, rhs) for a, lo, hi in [(0, b[0], b[1]), (1, b[2], b[3])]
            for cmp, rhs in [('GE', lo), ('LE', hi)]]


def build_family(request, compiler, *, seconds=10):
    if not isinstance(request, dict) or set(request) - {'route', 'ticks', 'avoidances', 'walls', 'name', 'collisionScene'}:
        raise ValueError('Guidance expects route, ticks, optional avoidances/walls/name')
    raw = copy.deepcopy(request.get('route'))
    if not isinstance(raw, dict):
        raise ValueError('Supply an ordinary open-air support route')
    derived = compile_open_air_route(raw)
    if derived['status'] != 'derived':
        raise ValueError('Ordinary jump profile unavailable: ' + str(derived))
    route, profiles = derived['route'], derived['profiles']
    if route['constraints']:
        raise ValueError('Use absolute guidance walls; relative route constraints are not yet modeled')
    if abs(route['initial']['velocity'][1] - REST_CARRY) > 1e-7:
        raise ValueError('Ordinary guidance starts grounded with the normal vertical rest carry')
    route['sharedMovement'] = True
    n = integer(request.get('ticks'), 'guidance ticks', 1, 200)
    extra = n - sum(route['airtimes'])
    if extra < 0 or extra > route['maxTotalRunTicks']:
        raise ValueError('Horizon is outside the declared total run domain')
    options, groups = [], []
    used_ids = set()
    for avoidance in request.get('avoidances', []):
        if not isinstance(avoidance, dict) or set(avoidance) != {'id', 'leg', 'faces', 'choices'}:
            raise ValueError('Avoidance needs id, leg, two faces and gate choices')
        name = avoidance['id']
        if not isinstance(name, str) or not name or name in used_ids:
            raise ValueError('Avoidance IDs must be unique nonempty strings')
        used_ids.add(name)
        leg = integer(avoidance['leg'], 'avoidance leg', 0, len(profiles)-1)
        faces = avoidance['faces']
        if not isinstance(faces, list) or len(faces) != 2:
            raise ValueError('The first guidance slice supports two-face horizontal passages')
        for face in faces:
            if (not isinstance(face, dict) or set(face) != {'axis', 'direction', 'plane'}
                    or face['axis'] not in ('X', 'Z') or type(face['direction']) is not int
                    or face['direction'] not in (-1, 1)):
                raise ValueError('Invalid horizontal passage face')
            number(face['plane'], 'passage plane')
        if faces[0]['axis'] == faces[1]['axis']:
            raise ValueError('Passage faces must have different axes')
        choices = avoidance['choices']
        if not isinstance(choices, list) or not choices or len(set(choices)) != len(choices):
            raise ValueError('Supply distinct passage gate choices')
        group = []
        for gate in choices:
            integer(gate, 'gate offset', 0, profiles[leg]['airtime'] - int(consecutive(*faces)))
            index = len(options)
            options.append(dict(id=name, leg=leg, gate=gate, faces=copy.deepcopy(faces)))
            group.append(index)
        groups.append(group)
    footprints = compiler.derive_footprints(route, seconds)
    standing = compiler.derive_standing_footprints(route, seconds)
    if len(footprints) != len(route['blocks']) or len(standing) != len(route['blocks']):
        raise ValueError('Footprint compiler returned the wrong support count')
    for block, footprint, run in zip(route['blocks'], footprints, standing):
        for bounds in (footprint, run):
            if len(bounds) != 4 or any(not isinstance(v, (int, float)) for v in bounds):
                raise ValueError('Footprint compiler returned invalid bounds')
            for value in bounds:
                number(value, 'compiled footprint bound')
            if bounds[0] >= bounds[1] or bounds[2] >= bounds[3]:
                raise ValueError('Footprint compiler returned empty bounds')
        block['_derivedFootprint'], block['_derivedStandingFootprint'] = footprint, run
    coefficient_cache = {}

    def coefficients(ground, jump, first):
        key = (ground, jump, first)
        if key not in coefficient_cache:
            g, j = ([ground], [jump]) if first else ([True, ground], [False, jump])
            table = compiler.compile(route, dict(grounded=g, jump=j), seconds)
            coefficient_cache[key] = {k: table[k][-1] for k in ('magnitude', 'friction', 'base')}
            for name, value in coefficient_cache[key].items():
                number(value, 'compiled '+name)
            if coefficient_cache[key]['magnitude'] < 0 or not 0 <= coefficient_cache[key]['friction'] <= 1:
                raise ValueError('Unsupported compiled movement constants')
        return coefficient_cache[key]

    start, end = (0, 0, 0, 0), (n, len(profiles), 0, 0)
    layers, edges = [{start}], []
    for t in range(n):
        following = set()
        for node in sorted(layers[-1]):
            _, leg, age, runs = node
            if leg == len(profiles):
                continue
            moves = []
            if age == 0:
                lo, hi = route['runBounds'][leg]
                if runs < hi:
                    moves.append(('run', (t+1, leg, 0, runs+1)))
                if runs >= lo:
                    moves.append(('jump', (t+1, leg, 1, 0)))
            else:
                moves.append(('air', (t+1, leg+1, 0, 0) if age+1 == profiles[leg]['airtime']
                              else (t+1, leg, age+1, 0)))
            for kind, target in moves:
                guards = _support_rows(route['blocks'][leg], 'run') if kind == 'run' else []
                if target[1] > leg:
                    guards += _support_rows(route['blocks'][leg+1], 'landing')
                for k, option in enumerate(options):
                    if option['leg'] != leg:
                        continue
                    ranges = intervals(option['faces'], [option['gate']], 0, profiles[leg]['airtime'])
                    for where, at in [('in', age), ('out', age+1)]:
                        if kind == 'run':
                            at = 0
                        for face, (lo, hi) in zip(option['faces'], ranges):
                            if lo <= at <= hi:
                                guards.append(_row(0 if face['axis'] == 'X' else 1,
                                    'GE' if face['direction'] > 0 else 'LE', face['plane'], where, k))
                edges.append(dict(source=node, target=target, tick=t, leg=leg, age=age, kind=kind,
                                  guards=guards, **coefficients(kind != 'air', kind == 'jump', t == 0)))
                following.add(target)
        layers.append(following)
    useful = {end}
    kept = []
    for edge in reversed(edges):
        if edge['target'] in useful:
            useful.add(edge['source'])
            kept.append(edge)
    edges = list(reversed(kept))
    if start not in useful:
        raise ValueError('No complete phase-flow path at the requested horizon')
    nodes = sorted(useful)
    incoming, outgoing = {s: [] for s in nodes}, {s: [] for s in nodes}
    for i, edge in enumerate(edges):
        edge['index'] = i
        outgoing[edge['source']].append(i)
        incoming[edge['target']].append(i)
    p0 = [route['initial']['position'][a] for a in (0, 2)]
    v0 = [route['initial']['velocity'][a] for a in (0, 2)]
    bounds = [(p0+v0, p0+v0)]
    for t in range(n):
        active = [e for e in edges if e['tick'] == t]
        magnitude, friction = max(e['magnitude'] for e in active), max(e['friction'] for e in active)
        lo, hi = bounds[-1]
        vmax = [max(abs(lo[a+2]), abs(hi[a+2])) for a in (0, 1)]
        movement = [v+magnitude for v in vmax]
        bounds.append(([lo[a]-movement[a] for a in (0, 1)] + [-friction*m for m in movement],
                       [hi[a]+movement[a] for a in (0, 1)] + [friction*m for m in movement]))
    walls = copy.deepcopy(request.get('walls', []))
    for wall in walls:
        if not isinstance(wall, dict) or set(wall) != {'tick', 'axis', 'cmp', 'rhs'}:
            raise ValueError('Walls need tick, axis, cmp and rhs')
        integer(wall['tick'], 'wall tick', 0, n)
        if wall['axis'] not in ('X', 'Z') or wall['cmp'] not in ('GE', 'LE', 'EQ'):
            raise ValueError('Invalid guidance wall')
        number(wall['rhs'], 'wall rhs')
    geometry = copy.deepcopy(request.get('collisionScene'))
    if geometry is None:
        solids = [[b['bounds'][0], b['top']-1, b['bounds'][2], b['bounds'][1], b['top'], b['bounds'][3]]
                  for b in route['blocks']]
        geometry = dict(version=1, profile='minecraft-1.8.9', stepHeight=.6, solids=solids,
                        bounds=[bounds[-1][0][0]-2, min(b['top'] for b in route['blocks'])-5,
                                bounds[-1][0][1]-2, bounds[-1][1][0]+2,
                                max(max(p['position']) for p in profiles)+5, bounds[-1][1][1]+2])
    from parkour_solver.scene.geometry import box_bounds
    if (not isinstance(geometry, dict) or geometry.get('version') != 1
            or geometry.get('profile') != 'minecraft-1.8.9' or geometry.get('ladders')
            or geometry.get('stepHeight', .6) != .6):
        raise ValueError('Expected a normal non-ladder collision scene')
    box_bounds(geometry.get('bounds'), 'guidance collision bounds')
    if not isinstance(geometry.get('solids'), list) or not geometry['solids']:
        raise ValueError('Supply nonempty collision solids')
    for box in geometry['solids']:
        box_bounds(box, 'guidance collision solid')
    return dict(name=request.get('name', 'route'), route=route, profiles=profiles, ticks=n, geometry=geometry,
                options=options, groups=groups, edges=edges, nodes=nodes, incoming=incoming,
                outgoing=outgoing, start=start, end=end, bounds=bounds, walls=walls, threshold=.005,
                scope='ordinary_jump_supplied_support_sequence_and_declared_passage_choices')


def edge_path(family, allocation):
    node, path = family['start'], []
    while node != family['end']:
        _, leg, age, runs = node
        kind = 'air' if age else 'run' if runs < allocation[leg] else 'jump'
        choices = [i for i in family['outgoing'][node] if family['edges'][i]['kind'] == kind]
        if len(choices) != 1:
            raise ValueError('Allocation does not belong to the phase graph')
        path.append(choices[0])
        node = family['edges'][choices[0]]['target']
    return path


def decisions(family):
    result = {}
    for edge in family['edges']:
        if edge['kind'] == 'jump':
            result.setdefault('departure:' + str(edge['leg']), set()).add(edge['tick'])
    for group in family['groups']:
        result['passage:' + family['options'][group[0]]['id']] = {family['options'][i]['gate'] for i in group}
    return {key: sorted(values) for key, values in result.items()}


def fixed_values(family, fixed):
    domains = decisions(family)
    for key, value in fixed.items():
        if key not in domains or type(value) is not int or value not in domains[key]:
            raise ValueError('Decision outside the declared family: ' + str((key, value)))
    edge_zeros = [e['index'] for e in family['edges'] if e['kind'] == 'jump'
                  and 'departure:'+str(e['leg']) in fixed
                  and fixed['departure:'+str(e['leg'])] != e['tick']]
    option_zeros = [i for i, o in enumerate(family['options']) if 'passage:'+o['id'] in fixed
                    and fixed['passage:'+o['id']] != o['gate']]
    return edge_zeros, option_zeros


def assignments(family):
    def paths(node, departures, allocation):
        if node == family['end']:
            yield allocation, departures
            return
        for i in family['outgoing'][node]:
            edge = family['edges'][i]
            if edge['kind'] == 'jump':
                yield from paths(edge['target'], departures+[edge['tick']], allocation+[node[3]])
            else:
                yield from paths(edge['target'], departures, allocation)
    for allocation, departures in paths(family['start'], [], []):
        for options in itertools.product(*family['groups']):
            fixed = {'departure:'+str(i): t for i, t in enumerate(departures)}
            fixed.update({'passage:'+family['options'][i]['id']: family['options'][i]['gate'] for i in options})
            yield dict(allocation=allocation, options=list(options), fixed=fixed)
