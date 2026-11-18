import copy
import dataclasses
import itertools

from parkour_solver.schema import integer, number
from parkour_solver.scene.route import HALF_WIDTH
from parkour_solver.scene.geometry import BODY_HEIGHT, expanded_box
from parkour_solver.spatial.surfaces import TANGENT_AXES


def merge_contacts(contacts, *, scene=False):
    merged = {}
    for contact in contacts:
        key = contact['state'], contact['axis']
        if key in merged:
            previous = merged[key]
            keys = ('plane', 'direction', 'step', 'case', 'lowerHeight', 'higherHeight', 'clearance', 'movementCase')
            if any(previous.get(k) != contact.get(k) for k in keys + (() if scene else ('surface',))):
                raise ValueError('Conflicting contacts on one input axis')
            if scene:
                for field in ('boxes', 'regions'):
                    union = previous.setdefault(field, [])
                    union.extend(copy.deepcopy(v) for v in contact.get(field, []) if v not in union)
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
        axis = 'XYZ'.index(event['axis'])
        if 'box' in event:
            box = event.pop('box')
            event.update(boxes=[box], surface=None, regions=[expanded_face(box, axis, -event['direction'],
                                                                           contact_clearance(route))])
        boxes = event.get('boxes')
        if not isinstance(boxes, list) or not boxes:
            raise ValueError('A contact needs its actual finite collision boxes')
        width = BODY_HEIGHT if axis == 1 else HALF_WIDTH
        for box in boxes:
            if not isinstance(box, list) or len(box) != 6:
                raise ValueError('A contact needs an actual finite collision box')
            for value in box:
                number(value, 'contact box')
            if any(box[a] >= box[a + 3] for a in range(3)):
                raise ValueError('Contact box must have positive volume')
            plane = box[axis] - width if event['direction'] > 0 else box[axis + 3] + (0 if axis == 1 else width)
            if event.get('plane') != plane:
                raise ValueError('Contact plane must match its actual finite face')
        regions = event.get('regions')
        if not isinstance(regions, list) or not regions:
            raise ValueError('A contact needs at least one finite region')
        for region in regions:
            if not isinstance(region, list) or len(region) != 4 or region[0] > region[2] or region[1] > region[3]:
                raise ValueError('A contact region must be a finite tangent rectangle')
            for value in region:
                number(value, 'contact region')
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


def contact_clearance(scene):
    return max(scene.get('supportClearance', 1e-6), 2e-7)


def expanded_face(box, axis, sign, clearance):
    """One box's expanded face as a tangent rectangle, shrunk by the clearance along horizontal tangents."""
    expanded, (u, v) = expanded_box(box), TANGENT_AXES[axis]
    shrink = [0 if a == 1 else clearance for a in (u, v)]
    return [expanded[u] + shrink[0], expanded[v] + shrink[1], expanded[u + 3] - shrink[0], expanded[v + 3] - shrink[1]]


def scene_contact_options(scene, axis, sign, members=None):
    """Contact options on the faces of the scene's offered blocks (or the given solids)."""
    from parkour_solver.spatial.surfaces import scene_geometry
    return scene_geometry(scene['collisionScene']).contact_options(
        axis, sign, clearance=contact_clearance(scene),
        members=[b['solidIndex'] for b in scene['blocks']] if members is None else members)


def contact_event(option, axis, direction, offset, role, **fields):
    return dict(leg=0, offset=offset, axis=axis, direction=direction, plane=option.plane,
                boxes=[list(b) for b in option.boxes], surface=option.surface,
                regions=[list(r) for r in option.regions], role=role, **fields)


def region_choices(context, event, owner):
    """The regionChoices entry for an event with several regions inside a DFS walk, which chooses the
    rectangle; elsewhere no generator walk is built from the program and the bounding rows stand."""
    if len(event['regions']) == 1 or not context.get('regionItems'):
        return []
    offset, axis = region_offset(event), 'XYZ'.index(event['axis'])
    return [dict(offset=offset, surface=event['surface'], owner=owner,
                 options=[finite_rows(axis, r, leg=0, offset=offset) for r in event['regions']])]


def reachable_option(context, option, tick, *, jumping):
    """The top or underside option keeping only the rectangles the approach can reach at `tick`, or None."""
    from parkour_solver.reachability.service import bounds_allowed
    regions = tuple(r for r in option.regions
                    if bounds_allowed(context, [r[0], 0, r[1], r[2], 0, r[3]], tick, jumping=jumping, phases=('Y',)))
    return dataclasses.replace(option, regions=regions) if regions else None


def bounding_region(regions):
    return [min(r[0] for r in regions), min(r[1] for r in regions), max(r[2] for r in regions), max(r[3] for r in regions)]


def wall_heights(region, axis):
    """The feet-height range of one region of a wall contact on `axis`."""
    return (region[0], region[2]) if axis == 'X' else (region[1], region[3])


def region_offset(event):
    """The input whose state the region rows constrain: Y contacts before X/Z, steps after it, Z after X."""
    return event['offset'] + (event['axis'] == 'Z' or event['axis'] == 'Y' and event.get('role') == 'step')


def finite_rows(axis, region, **at):
    """Rows keeping a contact's horizontal tangent inside one region rectangle."""
    return [dict(mode='XYZ'[a], cmp=cmp, rhs=region[k + 2 * (cmp == 'LE')], name='contact:region', **at)
            for k, a in enumerate(TANGENT_AXES[axis]) if a != 1 for cmp in ('GE', 'LE')]


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
        scene.append(dict(contact, boxes=event['boxes'], regions=event['regions'],
                          **({'step': True} if event['role'] == 'step' else {})))
        if axis == 1:
            vertical.append(dict(state=tick + 1, plane=event['plane'], direction=event['direction'],
                                 **({'step': True} if event['role'] == 'step' else {})))
        else:
            horizontal.append(dict(contact, axis=axis // 2))
        rows.extend(finite_rows(axis, bounding_region(event['regions']), t1=events[leg]['jump'] + region_offset(event)))
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
               event.get('surface'), tuple(map(tuple, event['regions'])), tuple(map(tuple, event['boxes'])))
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
