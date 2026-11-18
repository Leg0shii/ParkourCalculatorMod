import copy
import itertools

from parkour_solver.schema import integer, number
from parkour_solver.scene.route import HALF_WIDTH
from parkour_solver.scene.geometry import BODY_HEIGHT


def merge_contacts(contacts, *, scene=False):
    merged = {}
    for contact in contacts:
        key = contact['state'], contact['axis']
        if key in merged:
            previous = merged[key]
            if any(previous.get(k) != contact.get(k) for k in ('plane', 'direction', 'step', 'case', 'lowerHeight', 'higherHeight', 'clearance', 'movementCase')):
                raise ValueError('Conflicting contacts on one input axis')
            if scene and (previous.get('box') != contact.get('box') or previous.get('boxes') != contact.get('boxes')):
                choices = copy.deepcopy(previous.get('boxes', [previous['box']] if 'box' in previous else []))
                for box in contact.get('boxes', [contact['box']] if 'box' in contact else []):
                    if box not in choices:
                        choices.append(copy.deepcopy(box))
                previous.pop('box', None)
                previous['boxes'] = choices
        else:
            merged[key] = copy.deepcopy(contact)
    return [merged[key] for key in sorted(merged)]


def validate_contact_events(route):
    events = route.setdefault('contactEvents', [])
    if not isinstance(events, list):
        raise ValueError('contactEvents must be a list')
    for event in events:
        if not isinstance(event, dict) or event.get('axis') not in ('X', 'Y', 'Z'):
            raise ValueError('A contact event requires an X/Y/Z face')
        leg = integer(event.get('leg'), 'contact leg', 0, len(route['airtimes']) - 1)
        integer(event.get('offset'), 'contact input', 0, route['airtimes'][leg] - 1)
        if type(event.get('direction')) is not int or event['direction'] not in (-1, 1):
            raise ValueError('Contact direction must be -1 or 1')
        box = event.get('box')
        if not isinstance(box, list) or len(box) != 6:
            raise ValueError('A contact needs an actual finite collision box')
        for value in box:
            number(value, 'contact box')
        if any(box[a] >= box[a + 3] for a in range(3)):
            raise ValueError('Contact box must have positive volume')
        axis = 'XYZ'.index(event['axis'])
        width = BODY_HEIGHT if axis == 1 else HALF_WIDTH
        plane = box[axis] - width if event['direction'] > 0 else box[axis + 3] + (0 if axis == 1 else width)
        if event.get('plane') != plane:
            raise ValueError('Contact plane must match its actual finite face')
        role = event.setdefault('role', 'contact')
        if role not in ('contact', 'reversal', 'climb', 'ceiling', 'landing', 'step'):
            raise ValueError('Unsupported contact role')
        if role in ('reversal', 'climb') and axis == 1 or role in ('ceiling', 'landing', 'step') and axis != 1:
            raise ValueError('Contact role does not match its axis')
        if role == 'ceiling' and event['direction'] != 1 or role in ('landing', 'step') and event['direction'] != -1:
            raise ValueError('Contact role does not match its direction')
        for offset in event.get('avoidOffsets', []):
            integer(offset, 'contact avoidance input', 0, route['airtimes'][leg])
        for row in event.get('avoidBefore', []) + event.get('avoidAfter', []):
            if row.get('mode') not in ('X', 'Z') or row.get('cmp') not in ('GE', 'LE', 'EQ'):
                raise ValueError('Contact avoidance requires positional rows')
            integer(row.get('t1'), 'avoidance state', 0, route['airtimes'][leg])
            number(row.get('rhs'), 'avoidance boundary')
    if events and not route.get('sharedMovement'):
        raise ValueError('Contact combinations require shared movement compilation')
    merge_contacts([dict(e, state=e['offset'] + 1, axis=(e['leg'], e['axis'])) for e in events])


def compile_contact_events(route, events, ticks):
    rows, horizontal, scene, vertical = [], [], [], []
    margin = max(route['supportClearance'], 2e-7)
    for event in route.get('contactEvents', []):
        leg = event['leg']
        if leg >= len(events) or events[leg].get('landing') is None and events[leg].get('end') is None:
            continue
        tick = events[leg]['jump'] + event['offset']
        axis = 'XYZ'.index(event['axis'])
        contact = {'state': tick + 1, 'axis': axis, 'plane': event['plane'], 'direction': event['direction']}
        scene.append(dict(contact, box=event['box'], **({'step': True} if event['role'] == 'step' else {})))
        if axis == 1:
            vertical.append(dict(state=tick + 1, plane=event['plane'], direction=event['direction'],
                                 **({'step': True} if event['role'] == 'step' else {})))
            at = tick + 1 if event['role'] == 'step' else tick
            tangents = [('X', 0, at), ('Z', 2, at)]
        else:
            horizontal.append(dict(contact, axis=axis // 2))
            tangents = [('Z', 2, tick)] if axis == 0 else [('X', 0, tick + 1)]
        for tangent, a, state in tangents:
            rows.extend([{'mode': tangent, 't1': state, 'cmp': 'GE', 'rhs': event['box'][a] - HALF_WIDTH + margin,
                          'name': 'contact:finite_lo'},
                         {'mode': tangent, 't1': state, 'cmp': 'LE', 'rhs': event['box'][a + 3] + HALF_WIDTH - margin,
                          'name': 'contact:finite_hi'}])
        for offset in event.get('avoidOffsets', []):
            if axis != 1:
                rows.append({'mode': event['axis'], 't1': events[leg]['jump'] + offset,
                             'cmp': 'LE' if event['direction'] > 0 else 'GE', 'rhs': event['plane'],
                             'name': 'contact:avoid'})
        for row in event.get('avoidBefore', []) + event.get('avoidAfter', []):
            rows.append(dict(row, t1=events[leg]['jump'] + row['t1'], name='contact:avoidance'))
        if event['role'] == 'reversal' and tick + 2 <= ticks:
            rows.append({'mode': event['axis'], 't1': tick + 2, 'cmp': 'LE' if event['direction'] > 0 else 'GE',
                         'rhs': event['plane'] - event['direction'] * margin, 'name': 'contact:reverse'})
    return rows, horizontal, scene, vertical


def contact_combinations(opportunities, *, max_events=3, limit=1000, ladder_windows=(), cancel_event=None,
                         independent_events=False):
    integer(max_events, 'maximum combined contacts', 1, 200)
    integer(limit, 'combination work limit', 1, 2000000)
    unique = {}
    for event in opportunities:
        key = (event['leg'], event['offset'], event['axis'], event['plane'], event['direction'], event.get('role', 'contact'),
               tuple(event['box']))
        unique.setdefault(key, event)
    pool, inspected = list(unique.values()), 0
    streams = [iter(itertools.combinations(pool, size)) for size in range(1, min(max_events, len(pool)) + 1)]
    while streams:
        for stream in streams[:]:
            try:
                chosen = next(stream)
            except StopIteration:
                streams.remove(stream)
                continue
            if inspected >= limit or cancel_event is not None and cancel_event.is_set():
                return
            inspected += 1
            legs = {e['leg'] for e in chosen}
            offsets = [e['offset'] for e in chosen]
            if len(legs) != 1 or len({(e['offset'], e['axis']) for e in chosen}) != len(chosen):
                continue
            leg = chosen[0]['leg']
            if not independent_events and max(offsets) - min(offsets) > 1 and not any(
                    window['leg'] == leg and window['start'] <= min(offsets) <= max(offsets) <= window['end']
                    for window in ladder_windows):
                continue
            try:
                merge_contacts([dict(e, state=e['offset'] + 1) for e in chosen])
            except ValueError:
                continue
            yield copy.deepcopy(list(chosen))
