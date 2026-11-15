import copy

from parkour_solver.physics.vertical import REST_CARRY, f32
from parkour_solver.numerics.model import integer
from parkour_solver.movement.contact_profiles import contact_profile
from parkour_solver.movement.ladder.schedules import trace_ladder_schedule
from parkour_solver.movement.ladder.contacts import climb_contacts
from parkour_solver.movement.units import exit_matches, movement_program


def compose_ladder_episodes(source_top, target_top, episodes, *, max_inputs=200, sneak=None,
                            contacts=(), initial_velocity=REST_CARRY, initial_ground=True, jump_at_start=True):
    integer(max_inputs, 'maximum inputs', 1, 200)
    if not isinstance(episodes, list) or not episodes:
        raise ValueError('Supply ladder episodes')
    if target_top is None:
        max_inputs = max(e['exitState'] for e in episodes)
    ladder, climbing, selected, templates, region_ids = [False] * (max_inputs + 1), [False] * max_inputs, list(contacts), [], set()
    for episode in episodes:
        e, x, column, wall = episode['entryState'], episode['exitState'], episode['column'], episode['wall']
        integer(e, 'entry state', 0, max_inputs - 1)
        integer(x, 'exit state', e + 1, max_inputs)
        if column['id'] in region_ids:
            raise ValueError('Same-region re-entry remains outside this family')
        region_ids.add(column['id'])
        if any(ladder[e:x]):
            raise ValueError('Ladder episodes overlap')
        ladder[e:x] = [True] * (x - e)
        local = [False] * max_inputs
        for tick in episode['hitInputs']:
            integer(tick, 'climb input', max(0, e - 1), x - 2)
            local[tick], climbing[tick] = True, True
        templates.append((episode, local))
    flags = [climbing[t] or any(e['axis'] != 'Y' and e['offset'] == t for e in contacts) for t in range(max_inputs)]
    vertical = [{'state': e['offset'] + 1, 'plane': e['plane'], 'direction': e['direction'],
                 **({'step': True} if e.get('role') == 'step' else {})} for e in contacts if e['axis'] == 'Y']
    sneak = list(sneak[:max_inputs]) if sneak is not None else []
    sneak.extend([False] * (max_inputs - len(sneak)))
    preview = trace_ladder_schedule({'position': source_top, 'velocity': initial_velocity, 'onGround': initial_ground},
        ladder, sneak, flags,
        jump=[f32(.42) if jump_at_start else None] + [None] * (max_inputs - 1),
        target_top=target_top, min_landing_state=max(e['exitState'] for e in episodes), contacts=vertical)
    if preview['status'] != ('simulated' if target_top is None else 'landed'):
        return None
    for episode, local in templates:
        try:
            selected.extend(climb_contacts({'hit': local[:preview['ticks']], 'trace': preview}, episode['wall']))
        except ValueError:
            return None
    profile = contact_profile(source_top, target_top, selected, max_inputs=max_inputs, ladder=ladder, sneak=sneak,
        initial_velocity=initial_velocity, initial_ground=initial_ground, jump_at_start=jump_at_start,
        min_landing_state=max(e['exitState'] for e in episodes))
    if profile is None:
        return None
    n = profile['airtime']
    hits = [climbing[t] or any(e['axis'] != 'Y' and e['offset'] == t for e in contacts) for t in range(n)]
    trace = trace_ladder_schedule({'position': source_top, 'velocity': initial_velocity, 'onGround': initial_ground},
        profile['ladderTicks'], profile['sneak'], hits,
        jump=[f32(.42) if j else None for j in profile['jump']], contacts=profile['contacts'])
    events = []
    for episode, local in templates:
        e, x, column = episode['entryState'], episode['exitState'], episode['column']
        inside = [e <= t < x for t in range(n + 1)]
        if any(yes and not column['minY'] <= y < column['maxY'] for y, yes in zip(trace['position'], inside)):
            return None
        kind = episode.get('exitKind', 'side')
        if not exit_matches(kind, trace['position'][x], column):
            return None
        s = {'ticks': n, 'entryState': e, 'exitState': x, 'exitKind': kind,
             'entryFamily': episode['entryFace']['family'], 'entryChannel': episode['entryFace']['channel'],
             'entryDelay': 0, 'hit': local[:n], 'sneak': profile['sneak'][:], 'ladderTicks': inside,
             'trace': copy.deepcopy(trace), 'postExitTicks': n - x, 'firstClampedInput': e,
             'exitInput': x - 1, 'lastClimbEligibleInput': x - 2, 'contactEvents': []}
        if episode.get('climbWindow') is not None:
            s['climbWindow'] = list(episode['climbWindow'])
        if 'generatedUnits' in episode:
            s['generatedUnits'] = copy.deepcopy(episode['generatedUnits'])
        try:
            climb_contacts(s, episode['wall'])
        except ValueError:
            return None
        events.append({'schedule': s, 'column': copy.deepcopy(column), 'wall': copy.deepcopy(episode['wall']),
                       'entryFace': copy.deepcopy(episode['entryFace']), 'exitFace': copy.deepcopy(episode['exitFace'])})
    profile['contactEvents'] = list(contacts)
    try:
        units = movement_program(n, events)
    except ValueError:
        return None
    profile['movementUnits'] = copy.deepcopy(units)
    return {'profile': profile, 'ladderEvents': events, 'trace': trace,
            'movementUnits': units, 'terminal': 'airborne' if target_top is None else 'landed',
            'episodes': copy.deepcopy(episodes)}
