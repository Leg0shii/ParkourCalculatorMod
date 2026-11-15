import copy
import math

from parkour_solver.numerics.model import integer, number
from parkour_solver.scene.route import HALF_WIDTH, center_bounds


def validate_hits(route):
    hits = route.setdefault('hitEvents', [])
    if not isinstance(hits, list):
        raise ValueError('hitEvents must be a list')
    seen = set()
    for hit in hits:
        if not isinstance(hit, dict) or hit.get('axis') not in ('X', 'Z') or hit.get('direction') not in (-1, 1):
            raise ValueError('Invalid selected wall hit')
        leg = integer(hit.get('leg'), 'hit leg', 0, len(route['airtimes']) - 1)
        offset = integer(hit.get('offset'), 'hit input offset', 0, route['airtimes'][leg] - 2)
        number(hit.get('plane'), 'hit plane')
        box = hit.get('box')
        if not isinstance(box, list) or len(box) != 6:
            raise ValueError('A selected wall hit needs its finite box')
        for value in box:
            number(value, 'hit box')
        if any(box[i] >= box[i + 3] for i in range(3)):
            raise ValueError('Invalid hit box')
        a = 0 if hit['axis'] == 'X' else 2
        plane = box[a] - HALF_WIDTH if hit['direction'] > 0 else box[a + 3] + HALF_WIDTH
        if hit['plane'] != plane:
            raise ValueError('Hit plane must match the actual expanded face')
        key = leg, offset, hit['axis']
        if key in seen:
            raise ValueError('Duplicate selected contact axis')
        seen.add(key)
        for state in hit.get('avoidOffsets', range(route['airtimes'][leg] + 1)):
            integer(state, 'hit avoidance state', 0, route['airtimes'][leg])
    if hits and not route.get('sharedMovement'):
        raise ValueError('Selected wall hits require shared movement compilation')
    ceilings = route.setdefault('ceilingEvents', [])
    if not isinstance(ceilings, list) or any(not isinstance(contact, dict) for contact in ceilings):
        raise ValueError('ceilingEvents must be a list of objects')
    for contact in ceilings:
        leg = integer(contact.get('leg'), 'ceiling leg', 0, len(route['airtimes']) - 1)
        integer(contact.get('offset'), 'ceiling input offset', 0, route['airtimes'][leg] - 2)
        box = contact.get('box')
        if not isinstance(box, list) or len(box) != 6 or contact.get('plane') != box[1] - float_height():
            raise ValueError('Ceiling plane must match the actual underside')
        for value in box:
            number(value, 'ceiling box')
        if any(box[i] >= box[i + 3] for i in range(3)):
            raise ValueError('Invalid ceiling box')
        if not route.get('sharedMovement'):
            raise ValueError('Selected ceilings require shared movement compilation')


def compile_hits(route, events):
    contacts, scene_contacts, rows = [], [], []
    margin = max(route['supportClearance'], 2e-7)
    for hit in route.get('hitEvents', []):
        if hit['leg'] >= len(events):
            continue
        event = events[hit['leg']]
        offsets = hit.get('avoidOffsets', range(route['airtimes'][hit['leg']] + 1))
        states = {event['jump'] + offset for offset in offsets}
        if 0 in offsets:
            states.update(range(event['landed'], event['jump']))
        if event['landing'] is None:
            states = {t for t in states if t <= event['jump']}
        name = 'wall_hit:' + str(hit['leg']) + ':' + hit['axis']
        for tick in sorted(states):
            rows.append({'mode': hit['axis'], 't1': tick, 'cmp': 'LE' if hit['direction'] > 0 else 'GE',
                         'rhs': hit['plane'], 'name': name + ':avoid'})
        if event['landing'] is None:
            continue
        tick = event['jump'] + hit['offset']
        contact = {'state': tick + 1, 'axis': 0 if hit['axis'] == 'X' else 1,
                   'direction': hit['direction'], 'plane': hit['plane']}
        contacts.append(contact)
        scene_contacts.append(dict(contact, axis=0 if hit['axis'] == 'X' else 2, box=hit['box']))
        tangent = 'Z' if hit['axis'] == 'X' else 'X'
        index = 2 if tangent == 'Z' else 0
        at = tick if hit['axis'] == 'X' else tick + 1
        rows.extend([{'mode': tangent, 't1': at, 'cmp': 'GE', 'rhs': hit['box'][index] - HALF_WIDTH + margin,
                      'name': name + ':finite_lo'},
                     {'mode': tangent, 't1': at, 'cmp': 'LE', 'rhs': hit['box'][index + 3] + HALF_WIDTH - margin,
                      'name': name + ':finite_hi'},
                     {'mode': hit['axis'], 't1': tick + 2, 'cmp': 'LE' if hit['direction'] > 0 else 'GE',
                      'rhs': hit['plane'] - hit['direction'] * margin, 'name': name + ':reverse'}])
    for contact in route.get('ceilingEvents', []):
        if contact['leg'] >= len(events) or events[contact['leg']]['landing'] is None:
            continue
        tick = events[contact['leg']]['jump'] + contact['offset']
        scene_contacts.append({'state': tick + 1, 'axis': 1, 'plane': contact['plane'], 'direction': 1,
                               'box': contact['box']})
        for axis, index in [('X', 0), ('Z', 2)]:
            rows.extend([{'mode': axis, 't1': tick, 'cmp': 'GE',
                          'rhs': contact['box'][index] - HALF_WIDTH + margin, 'name': 'ceiling_hit:finite_lo'},
                         {'mode': axis, 't1': tick, 'cmp': 'LE',
                          'rhs': contact['box'][index + 3] + HALF_WIDTH - margin, 'name': 'ceiling_hit:finite_hi'}])
        for choice in contact.get('avoidBefore', []):
            rows.append(dict(choice, t1=events[contact['leg']]['jump'] + choice['t1']))
    return contacts, scene_contacts, rows


def wall_hit_options(route, profiles, geometry, solid_blocks, motion_bound, limit):
    options = []
    speed = motion_bound['storedSpeedNorm'] + max(motion_bound.get('groundMagnitude', 0), .23)
    for leg, profile in enumerate(profiles):
        source, target = (center_bounds(route['blocks'][i]) for i in (leg, leg + 1))
        for index, box in enumerate(geometry['solids']):
            active = [t for t, y in enumerate(profile['position'][1:])
                      if y < box[4] and y + float_height() > box[1]]
            for axis, a in [('X', 0), ('Z', 2)]:
                for direction in (-1, 1):
                    plane = box[a] - HALF_WIDTH if direction > 0 else box[a + 3] + HALF_WIDTH
                    lo, hi = source[a:a + 2]
                    end_lo, end_hi = target[a:a + 2]
                    if direction * (lo if direction > 0 else hi) > direction * plane:
                        continue
                    if direction * (end_lo if direction > 0 else end_hi) > direction * plane:
                        continue
                    source_gap = max(0, lo - plane, plane - hi)
                    target_gap = max(0, end_lo - plane, plane - end_hi)
                    for offset in active:
                        if offset >= profile['airtime'] - 1:
                            continue
                        if source_gap > speed * (offset + 2) or target_gap > speed * (profile['airtime'] - offset):
                            continue
                        hit = {'leg': leg, 'offset': offset, 'axis': axis, 'direction': direction, 'plane': plane,
                               'box': copy.deepcopy(box), 'solidIndex': index,
                               'blockId': solid_blocks[index] if solid_blocks else 'solid:' + str(index),
                               'avoidOffsets': sorted({s for t in active for s in (t, t + 1)})}
                        score = source_gap + target_gap + .01 * offset
                        options.append((score, hit))
    options.sort(key=lambda item: item[0])
    return {'options': [hit for _, hit in options[:limit]], 'generated': len(options),
            'candidateDrops': max(0, len(options) - limit)}


def float_height():
    from parkour_solver.physics.vertical import f32
    return f32(1.8)


def hit_seed(problem, route, plan):
    from parkour_solver.numerics import validate
    waypoints = []
    for hit in route.get('hitEvents', []):
        if hit['leg'] >= len(plan['events']) or plan['events'][hit['leg']]['landing'] is None:
            continue
        event = plan['events'][hit['leg']]
        source, target = (center_bounds(route['blocks'][i]) for i in (hit['leg'], hit['leg'] + 1))
        point = [(source[a] + source[a + 1] + target[a] + target[a + 1]) / 4 for a in (0, 2)]
        normal, tangent = (0, 1) if hit['axis'] == 'X' else (1, 0)
        a = 2 if tangent else 0
        point[normal] = hit['plane'] + hit['direction'] * .01
        point[tangent] = max(hit['box'][a] - HALF_WIDTH + 1e-4,
                             min(hit['box'][a + 3] + HALF_WIDTH - 1e-4, point[tangent]))
        waypoints.append((event['jump'] + hit['offset'] + 1, point))
        waypoints.append((event['landing'] - 1, [(target[a] + target[a + 1]) / 2 for a in (0, 2)]))
    yaw = problem['yaw'][:]
    for tick in range(min(len(yaw), 128)):
        targets = sorted((at, p) for at, p in waypoints if at > tick)
        if not targets:
            break
        at, point = targets[0]
        state = validate(problem, yaw)
        coupling, drag = 0, 1
        for j in range(tick, at):
            coupling += drag
            drag *= problem['friction'][j]
        residual = [point[a] - state['positions'][tick][a] - coupling * state['velocity'][tick][a]
                    for a in range(2)]
        yaw[tick] = math.degrees(math.atan2(residual[1], residual[0]) - problem['base'][tick])
    return yaw
