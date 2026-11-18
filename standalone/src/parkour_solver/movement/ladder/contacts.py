import copy
from parkour_solver.physics.vertical import f32, vertical_move, vertical_step, vertical_carry
from parkour_solver.movement.contact_combinations import contact_clearance, expanded_face, merge_contacts
from parkour_solver.movement.contact_profiles import contact_profile
from parkour_solver.movement.ladder.schedules import trace_ladder_schedule
from parkour_solver.scene.geometry import BODY_HEIGHT
from parkour_solver.movement.units import exit_matches, ladder_units
from parkour_solver.movement.ceiling_profiles import ceiling_sequence


def climb_contacts(schedule, wall):
    contacts = []
    for t, hit in enumerate(schedule['hit']):
        if not hit:
            continue
        y = schedule['trace']['position'][t + 1]
        part = next((p for p in wall['parts'] if y < p['box'][4] and y + BODY_HEIGHT > p['box'][1]), None)
        if part is None:
            raise ValueError('Climb hit has no finite face')
        contacts.append({'leg': 0, 'offset': t, 'axis': wall['axis'], 'plane': wall['plane'],
                         'direction': wall['direction'], 'boxes': [part['box']], 'surface': None,
                         'regions': [expanded_face(part['box'], 'XYZ'.index(wall['axis']), -wall['direction'],
                                                   contact_clearance({}))], 'role': 'climb'})
    return contacts


def with_contacts(schedule, extra, column, wall, target_top, *, max_inputs=200):
    s = copy.deepcopy(schedule)
    if target_top is None:
        max_inputs = s['exitState']
    try:
        base = climb_contacts(s, wall) + s.get('contactEvents', [])
        original = {(e['offset'], e['axis'], e['plane'], e['direction']) for e in base}
        if all((e['offset'], e['axis'], e['plane'], e['direction']) in original for e in extra):
            return None
        selected = merge_contacts([dict(e, state=e['offset'] + 1) for e in base + extra])
    except ValueError:
        return None
    profile = contact_profile(s['trace']['position'][0], target_top, selected, max_inputs=max_inputs,
        ladder=s['ladderTicks'], sneak=s['sneak'], initial_velocity=s['trace']['velocity'][0],
        initial_ground=s['trace']['onGround'][0], jump_at_start=s['trace']['vertical']['jumpVelocity'][0] is not None,
        min_landing_state=s['exitState'])
    if profile is None:
        return None
    n, ys = profile['airtime'], profile['position']
    if any(inside and not column['minY'] <= y < column['maxY'] for y, inside in zip(ys, profile['ladderTicks'])):
        return None
    if s['exitState'] > n or not exit_matches(s['exitKind'], ys[s['exitState']], column):
        return None
    actual = [any(e['axis'] != 'Y' and e['offset'] == t for e in selected) for t in range(n)]
    trace = trace_ladder_schedule({'position': ys[0], 'velocity': profile['velocity'][0], 'onGround': profile['grounded'][0]},
        profile['ladderTicks'], profile['sneak'], actual,
        jump=[f32(.42) if j else None for j in profile['jump']], contacts=profile['contacts'])
    if trace['status'] != ('simulated' if target_top is None else 'landed'):
        return None
    s.update(ticks=n, hit=(s['hit'] + [False] * n)[:n], sneak=profile['sneak'], ladderTicks=profile['ladderTicks'],
             trace=trace, postExitTicks=n - s['exitState'],
             terminal='airborne' if target_top is None else 'landed',
             contactEvents=[{k: v for k, v in e.items() if k != 'state'} for e in selected if e['role'] != 'climb'])
    try:
        s['movementUnits'] = ladder_units(s, column['id'])
        from parkour_solver.movement.ladder.ceiling import own_schedule_ceilings
        s = own_schedule_ceilings(s, column['id'])
    except ValueError:
        return None
    return s


def mixed_ladder_schedules(schedules, solids, column, wall, target_top, *, max_variants=16,
                           max_events=3, max_inputs=200, cancel_event=None):
    added, attempted, work = [], 0, max_variants * 100
    if not schedules:
        return {'schedules': [], 'attempted': 0, 'limit': max_variants}
    def variants(s):
        from parkour_solver.movement.contacts import contact_variants
        yield from contact_variants(solids, s['ticks'], schedules=[s], max_events=max_events,
            max_variants=min(256, max_variants * 8), cancel_event=cancel_event)['variants']
    streams = [(s, iter(variants(s))) for s in schedules[:max_variants]]
    signatures = set()
    while streams and len(added) < max_variants and attempted < work:
        for s, stream in streams[:]:
            if cancel_event is not None and cancel_event.is_set():
                streams = []
                break
            try:
                extra = next(stream)
            except StopIteration:
                streams.remove((s, stream))
                continue
            attempted += 1
            result = with_contacts(s, extra, column, wall, target_top, max_inputs=max_inputs)
            if result is None:
                continue
            key = (tuple(result['hit']), tuple(result['sneak']), tuple(result['ladderTicks']),
                   tuple((e['offset'], e['axis'], e['plane'], e['direction']) for e in result['contactEvents']))
            if key not in signatures:
                signatures.add(key)
                added.append(result)
            if len(added) >= max_variants or attempted >= work:
                break
    return {'schedules': added, 'attempted': attempted, 'limit': max_variants,
            'complete': not streams, 'routeSpecificPruning': False}
