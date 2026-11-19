import copy
import math

from parkour_solver.physics.vertical import f32
from parkour_solver.schema import integer, number
from parkour_solver.scene.objectives import objective_spec


HALF_WIDTH = f32(0.6) / 2


def prepare_route(request):
    route = copy.deepcopy(request)
    if not isinstance(route, dict) or type(route.get('version')) is not int or route['version'] != 1:
        raise ValueError('Expected a version 1 block route')
    route['objective'] = objective_spec(route.get('objective'))
    if type(route.get('terminalSupportRequired', True)) is not bool:
        raise ValueError('terminalSupportRequired must be boolean')
    if number(route.get('physicsThreshold', .005), 'physicsThreshold') < 0:
        raise ValueError('physicsThreshold must be nonnegative')
    blocks = route.get('blocks')
    if not isinstance(blocks, list) or len(blocks) < 2:
        raise ValueError('Supply a start block and at least one landing block')
    for i, block in enumerate(blocks):
        if not isinstance(block, dict):
            raise ValueError('Invalid block')
        block.setdefault('id', str(i))
        if not isinstance(block['id'], str) or not block['id']:
            raise ValueError('Block IDs must be nonempty strings')
        if ('bounds' in block) == ('centerBounds' in block):
            raise ValueError('Supply either collision bounds or an explicit center region')
        bounds = block.get('bounds', block.get('centerBounds'))
        if not isinstance(bounds, list) or len(bounds) != 4:
            raise ValueError('Block bounds must be [minX, maxX, minZ, maxZ]')
        for value in bounds:
            number(value, 'block bound')
        if bounds[0] >= bounds[1] or bounds[2] >= bounds[3]:
            raise ValueError('Block bounds are empty')
        if 'standingBounds' in block:
            standing = block['standingBounds']
            if not isinstance(standing, list) or len(standing) != 4:
                raise ValueError('Standing support needs four bounds')
            for value in standing:
                number(value, 'standing bound')
            if standing[0] >= standing[1] or standing[2] >= standing[3]:
                raise ValueError('Standing support is empty')
        number(block.get('top'), 'block top')
    airtimes = route.get('airtimes')
    if not isinstance(airtimes, list) or len(airtimes) != len(blocks) - 1:
        raise ValueError('Supply one valid airtime per transition')
    kinds = route.setdefault('kinds', ['jump'] * len(airtimes))
    if not isinstance(kinds, list) or len(kinds) != len(airtimes) or any(k not in ('jump', 'walk_off', 'step', 'movement') for k in kinds):
        raise ValueError('Transition kinds must be jump, walk_off, step or movement')
    for value, kind in zip(airtimes, kinds):
        integer(value, 'airtime', 2 if kind == 'jump' else 1, 200)
        if kind == 'step' and value != 1:
            raise ValueError('A prescribed step takes one grounded movement input')
    phases = route.setdefault('landingPhases', ['post' if k == 'step' else 'pre' for k in kinds])
    if not isinstance(phases, list) or len(phases) != len(kinds) or any(p not in ('pre', 'post') for p in phases):
        raise ValueError('Landing phases must be pre or post')
    entries = route.setdefault('contactEntries', [None] * len(kinds))
    if not isinstance(entries, list) or len(entries) != len(kinds):
        raise ValueError('Supply one optional contact entry per transition')
    for entry, duration in zip(entries, airtimes):
        if entry is None:
            continue
        if not isinstance(entry, dict) or entry.get('axis') not in ('X', 'Z') or type(entry.get('direction')) is not int or entry['direction'] not in (-1, 1):
            raise ValueError('Invalid contact entry face')
        integer(entry.get('outsideFrom'), 'outsideFrom', 0, duration - 1)
        integer(entry.get('outsideThrough'), 'outsideThrough', entry['outsideFrom'], duration - 1)
        integer(entry.get('enter'), 'entry state', entry['outsideThrough'] + 1, duration)
    exits = route.setdefault('walkOffExits', [None] * len(airtimes))
    if not isinstance(exits, list) or len(exits) != len(airtimes):
        raise ValueError('Supply one optional run-off exit per transition')
    for i, exit_face in enumerate(exits):
        if exit_face is None:
            continue
        if kinds[i] != 'walk_off' or not isinstance(exit_face, dict) or exit_face.get('axis') not in ('X', 'Z'):
            raise ValueError('Invalid run-off exit')
        if type(exit_face.get('direction')) is not int or exit_face['direction'] not in (-1, 1):
            raise ValueError('Run-off direction must be -1 or 1')
    initial = route.get('initial')
    if not isinstance(initial, dict):
        raise ValueError('Supply an initial state')
    for key in ('position', 'velocity'):
        values = initial.get(key)
        if not isinstance(values, list) or len(values) != 3:
            raise ValueError('Initial ' + key + ' must be [X,Y,Z]')
        for value in values:
            number(value, 'initial ' + key)
    number(initial.setdefault('yaw', 0.0), 'initial yaw')
    if abs(initial['position'][1] - blocks[0]['top']) > 1e-7:
        raise ValueError('The initial feet height must equal the start block top')
    if not supports(blocks[0], initial['position'][0], initial['position'][2]):
        raise ValueError('The initial player must overlap the start block')
    inputs = route.setdefault('inputs', {})
    if not isinstance(inputs, dict):
        raise ValueError('Invalid input policy')
    for key, default in [('forward', f32(0.98)), ('strafe', 0.0)]:
        value = number(inputs.setdefault(key, default), key)
        if abs(value) > 1:
            raise ValueError('Movement inputs must be at the scaled moveFlying range')
    for key, default in [('sprint', True), ('incomingSprint', False), ('force45', False)]:
        if not isinstance(inputs.setdefault(key, default), bool):
            raise ValueError(key + ' must be boolean')
    if abs(inputs['forward'] - f32(0.98)) > 1e-7 or inputs['strafe'] != 0:
        raise ValueError('The first route family uses forward movement without strafe')
    integer(route.setdefault('maxTotalRunTicks', 40), 'maxTotalRunTicks', 0, 1000)
    run_bounds = route.setdefault('runBounds', [[0, route['maxTotalRunTicks']] for _ in airtimes])
    if not isinstance(run_bounds, list) or len(run_bounds) != len(airtimes):
        raise ValueError('Supply one run bound pair per transition')
    for bounds in run_bounds:
        if not isinstance(bounds, list) or len(bounds) != 2:
            raise ValueError('Run bounds must be [min,max]')
        integer(bounds[0], 'minimum run ticks', 0, 1000)
        integer(bounds[1], 'maximum run ticks', bounds[0], 1000)
    clearance = number(route.setdefault('supportClearance', 1e-6), 'supportClearance')
    if clearance < 0:
        raise ValueError('Support clearance must be nonnegative')
    if route.get('obstacles'):
        raise ValueError('Obstacle scenes require the integration replay adapter')
    for field in ('ladderTicks', 'ladderPattern', 'contacts', 'vertical'):
        if field in route:
            raise ValueError(field + ' needs a retiming adapter; it cannot be omitted from the compiled request')
    constraints = route.setdefault('constraints', [])
    if not isinstance(constraints, list):
        raise ValueError('Constraints must be a list')
    for row in constraints:
        if not isinstance(row, dict):
            raise ValueError('Invalid route constraint')
        integer(row.get('leg'), 'constraint leg', 0, len(airtimes) - 1)
        integer(row.setdefault('offset', 0), 'constraint offset', 0, 1000)
        if row.get('phase', 'departure') not in ('departure', 'run') or row.get('mode') not in ('X', 'Z'):
            raise ValueError('Only departure/run X/Z constraints are supported')
        if row.get('cmp') not in ('GE', 'LE', 'EQ'):
            raise ValueError('Invalid route comparison')
        number(row.get('rhs'), 'constraint rhs')
    from parkour_solver.constraints.avoidance import validate_passages
    validate_passages(route)
    from parkour_solver.constraints.hit_events import validate_hits
    validate_hits(route)
    from parkour_solver.movement.ladder.events import validate_ladder_events
    validate_ladder_events(route)
    from parkour_solver.movement.contact_combinations import validate_contact_events
    validate_contact_events(route)
    return route


def supports(block, x, z, kind='landing'):
    if kind == 'run' and 'standingBounds' in block:
        a, b, c, d = block['standingBounds']
        return x + HALF_WIDTH > a and x - HALF_WIDTH < b and z + HALF_WIDTH > c and z - HALF_WIDTH < d
    if 'centerBounds' in block:
        a, b, c, d = block['centerBounds']
        return a < x < b and c < z < d
    a, b, c, d = block['bounds']
    return x + HALF_WIDTH > a and x - HALF_WIDTH < b and z + HALF_WIDTH > c and z - HALF_WIDTH < d


def center_bounds(block, kind='landing'):
    if kind == 'run' and '_derivedStandingFootprint' in block:
        return block['_derivedStandingFootprint'][:]
    if kind == 'run' and 'standingBounds' in block:
        a, b, c, d = block['standingBounds']
        return [a - HALF_WIDTH, b + HALF_WIDTH, c - HALF_WIDTH, d + HALF_WIDTH]
    if '_derivedFootprint' in block:
        return block['_derivedFootprint'][:]
    if 'centerBounds' in block:
        return block['centerBounds'][:]
    a, b, c, d = block['bounds']
    return [a - HALF_WIDTH, b + HALF_WIDTH, c - HALF_WIDTH, d + HALF_WIDTH]


def row_tick(event, row):
    return event['landed'] + row['offset'] if row.get('phase', 'departure') == 'run' else event['jump'] + row['offset']


def schedule(route, allocation, stage='jump'):
    if stage not in ('jump', 'run') or not allocation or len(allocation) > len(route['airtimes']):
        raise ValueError('Invalid allocation or stage')
    grounded, jump, events, supports_at, exit_checks = [], [], [], [], []
    state = 0
    supports_at.append((0, 0, 'initial'))
    for i, extra in enumerate(allocation):
        lo, hi = route['runBounds'][i]
        integer(extra, 'run ticks', lo, hi)
        landing = state
        for _ in range(extra):
            supports_at.append((state, i, 'run'))
            grounded.append(True)
            jump.append(False)
            state += 1
        fire = state
        if stage == 'run' and i == len(allocation) - 1:
            events.append({'leg': i, 'landed': landing, 'jump': fire, 'landing': None, 'runTicks': extra,
                           'kind': route['kinds'][i]})
            break
        airtime = route['airtimes'][i]
        profiles = route.get('verticalProfiles', [])
        profile = profiles[i] if i < len(profiles) else {}
        grounded.extend(profile.get('grounded', [True] + [False]*airtime)[:-1])
        jump.extend(profile.get('jump', [route['kinds'][i] == 'jump'] + [False]*(airtime-1)))
        if route['kinds'][i] == 'walk_off':
            if fire > 0:
                supports_at.append((fire - 1, i, 'run'))
            exit_checks.append((fire, i))
        state += airtime
        if route.get('terminalSupportRequired', True) or i+1 < len(route['airtimes']):
            supports_at.append((state if route['landingPhases'][i] == 'post' else state - 1, i + 1, 'landing'))
        events.append({'leg': i, 'landed': landing, 'jump': fire, 'landing': state, 'runTicks': extra,
                       'kind': route['kinds'][i]})
    if sum(allocation) > route['maxTotalRunTicks']:
        raise ValueError('Allocation exceeds the total run-tick limit')
    walls = []
    clearance = route['supportClearance']
    for tick, index, kind in sorted(set(supports_at)):
        block = route['blocks'][index]
        bounds = center_bounds(block, kind)
        for axis, lo, hi in [('X', bounds[0], bounds[1]), ('Z', bounds[2], bounds[3])]:
            lower = math.nextafter(lo, math.inf) + clearance
            upper = math.nextafter(hi, -math.inf) - clearance
            walls.extend([{'mode': axis, 't1': tick, 'cmp': 'GE', 'rhs': lower,
                           'name': kind + ':' + str(index) + ':' + axis + ':lo'},
                          {'mode': axis, 't1': tick, 'cmp': 'LE', 'rhs': upper,
                           'name': kind + ':' + str(index) + ':' + axis + ':hi'}])
    for tick, index in exit_checks:
        source = center_bounds(route['blocks'][index], 'run')
        target = center_bounds(route['blocks'][index + 1])
        exit_face = route['walkOffExits'][index]
        if exit_face is None:
            dx = target[0] + target[1] - source[0] - source[1]
            dz = target[2] + target[3] - source[2] - source[3]
            axis, delta = ('X', dx) if abs(dx) > abs(dz) else ('Z', dz)
            exit_face = {'axis': axis, 'direction': 1 if delta >= 0 else -1}
        axis, direction = exit_face['axis'], exit_face['direction']
        k = 0 if axis == 'X' else 2
        edge = source[k + 1] if direction > 0 else source[k]
        walls.append({'mode': axis, 't1': tick, 'cmp': 'GE' if direction > 0 else 'LE',
                      'rhs': edge + direction * clearance, 'name': 'walk_off:' + str(index)})
    for event in events:
        if event['landing'] is None:
            continue
        entry = route['contactEntries'][event['leg']]
        if entry is None:
            continue
        target = center_bounds(route['blocks'][event['leg'] + 1])
        axis, direction = entry['axis'], entry['direction']
        k = 0 if axis == 'X' else 2
        edge = target[k + 1] if direction > 0 else target[k]
        for offset in range(entry['outsideFrom'], entry['outsideThrough'] + 1):
            walls.append({'mode': axis, 't1': event['jump'] + offset,
                          'cmp': 'GE' if direction > 0 else 'LE', 'rhs': edge + direction * clearance,
                          'name': 'contact_outside:' + str(event['leg'])})
        tick = event['jump'] + entry['enter']
        supports_at.append((tick, event['leg'] + 1, 'contact'))
        for axis, lo, hi in [('X', target[0], target[1]), ('Z', target[2], target[3])]:
            walls.extend([{'mode': axis, 't1': tick, 'cmp': 'GE', 'rhs': lo + clearance,
                           'name': 'contact_enter:' + str(event['leg'])},
                          {'mode': axis, 't1': tick, 'cmp': 'LE', 'rhs': hi - clearance,
                           'name': 'contact_enter:' + str(event['leg'])}])
    for row in route['constraints']:
        if row['leg'] >= len(events):
            continue
        event = events[row['leg']]
        if row.get('phase', 'departure') == 'run':
            if row['offset'] >= event['runTicks']:
                continue
        elif event['landing'] is None:
            continue
        tick = row_tick(event, row)
        if row.get('phase', 'departure') != 'run' and tick > event['landing']:
            raise ValueError('Constraint lies beyond its transition')
        walls.append({'mode': row['mode'], 't1': tick, 'cmp': row['cmp'], 'rhs': row['rhs'],
                      'name': row.get('name', 'route:' + str(row['leg']))})
    from parkour_solver.constraints.avoidance import compile_passages
    passage_rows, passage_failures = compile_passages(route, events)
    walls.extend(passage_rows)
    from parkour_solver.constraints.hit_events import compile_hits
    contacts, scene_contacts, hit_rows = compile_hits(route, events)
    walls.extend(hit_rows)
    extra = {}
    if route.get('ladderEvents'):
        from parkour_solver.movement.ladder.events import compile_ladder_events
        ladder_rows, ladder_contacts, ladder_scene, ladder, sneak = compile_ladder_events(route, events, state)
        walls.extend(ladder_rows)
        contacts.extend(ladder_contacts)
        scene_contacts.extend(ladder_scene)
        extra = {'ladderTicks': ladder, 'sneak': sneak}
    for event, profile in zip(events, route.get('verticalProfiles', [])):
        if event['landing'] is None:
            continue
        offset, duration = event['jump'], route['airtimes'][event['leg']]
        walls.extend(dict(row, t1=offset + row['t1']) for row in profile.get('positionConstraints', []))
        for case in profile.get('caseEvents', []):
            if case['case'] in ('normal_step', 'slab_boost') and any(
                    c['leg'] == event['leg'] and c.get('role') == 'step' and c['offset'] == case['input']
                    and c['plane'] == case['plane'] and any(b in c['boxes'] for b in case['boxes'])
                    for c in route.get('contactEvents', [])):
                continue
            scene_contacts.append(dict(state=offset + case['input'] + 1, axis=1, direction=-1,
                                       plane=case['plane'], boxes=case['boxes'], movementCase=case['case']))
        occupied = profile.get('ladderTicks', [False] * (duration + 1))
        selected_sneak = profile.get('sneak', [False] * duration)
        if len(occupied) != duration + 1 or len(selected_sneak) != duration:
            raise ValueError('Profile medium/input arrays have inconsistent lengths')
        if any(occupied) or any(selected_sneak):
            extra.setdefault('ladderTicks', [False] * (state + 1))
            extra.setdefault('sneak', [False] * state)
            for tick, inside in enumerate(occupied):
                extra['ladderTicks'][offset + tick] |= inside
            for tick, selected in enumerate(selected_sneak):
                extra['sneak'][offset + tick] |= selected
    from parkour_solver.movement.contact_combinations import compile_contact_events, merge_contacts
    contact_rows, additional, additional_scene, vertical = compile_contact_events(route, events, state)
    walls.extend(contact_rows)
    contacts = merge_contacts(contacts + additional)
    scene_contacts = merge_contacts(scene_contacts + additional_scene, scene=True)
    if vertical:
        extra['verticalContacts'] = merge_contacts([dict(c, axis=1) for c in vertical])
    from parkour_solver.movement.units import shifted_unit, movement_program
    relative_units = route.get('movementUnits')
    if relative_units is None:
        relative_units = [movement_program(duration, [e for e in route.get('ladderEvents', []) if e['leg'] == leg])
                          for leg, duration in enumerate(route['airtimes'])]
    units = []
    for event in events:
        if event['runTicks']:
            units.append({'type': 'normal', 'start': event['landed'], 'end': event['jump'], 'grounded': True})
        if event['landing'] is not None:
            units.extend(shifted_unit(unit, event['jump']) for unit in relative_units[event['leg']])
    extra['movementUnits'] = units
    if any(p.get('composition') == 'movement_program' for p in route.get('verticalProfiles', [])):
        extra['movementComposition'] = True
    return {'ticks': state, 'grounded': grounded, 'jump': jump, 'events': events,
            'supportChecks': supports_at, 'exitChecks': exit_checks, 'walls': walls,
            'allocation': list(allocation), 'stage': stage, 'passageFailures': passage_failures,
            'contacts': contacts, 'sceneContacts': scene_contacts, **extra}


def initial_guess(route, plan, parent=None):
    yaw = route['initial']['yaw']
    result = [yaw] * plan['ticks']
    if parent:
        prior = parent['candidate']['yaw']
        result[:min(len(prior), len(result))] = prior[:len(result)]
    for event in plan['events']:
        target = center_bounds(route['blocks'][event['leg'] + 1])
        source = center_bounds(route['blocks'][event['leg']])
        dx = (target[0] + target[1] - source[0] - source[1]) / 2
        dz = (target[2] + target[3] - source[2] - source[3]) / 2
        facing = math.degrees(math.atan2(-dx, dz)) if dx or dz else yaw
        start = event['landed']
        end = event['landing'] if event['landing'] is not None else event['jump']
        if parent:
            start = max(start, len(parent['candidate']['yaw']))
        result[start:end] = [facing] * (end - start)
        yaw = facing
    if route['inputs']['force45']:
        for t in range(len(result)):
            if (not parent or t >= len(parent['candidate']['yaw'])) and not plan['jump'][t]:
                result[t] += 45
    if parent is None and route.get('warmYaw'):
        prior = route['warmYaw']
        result[:min(len(prior), len(result))] = prior[:len(result)]
    return result
