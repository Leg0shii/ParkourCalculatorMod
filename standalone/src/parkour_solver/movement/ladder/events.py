from parkour_solver.scene.ladders import schedule_constraints, region_rectangles
from parkour_solver.movement.units import movement_program, compile_units, profile_program, overlay_program


def validate_ladder_events(route):
    entries = route.setdefault('ladderEvents', [])
    if not isinstance(entries, list):
        raise ValueError('ladderEvents must be a list')
    seen = {}
    for event in entries:
        leg = event.get('leg')
        if type(leg) is not int or not 0 <= leg < len(route['airtimes']):
            raise ValueError('Ladder interaction has an invalid transition')
        if event['schedule']['ticks'] != route['airtimes'][leg] or not route.get('sharedMovement'):
            raise ValueError('Ladder interaction requires matching duration and shared movement')
        s = event['schedule']
        if len(s['ladderTicks']) != s['ticks'] + 1 or len(s['sneak']) != s['ticks']:
            raise ValueError('Ladder schedule arrays have inconsistent lengths')
        for previous in seen.setdefault(leg, []):
            if any(a and b for a, b in zip(previous['ladderTicks'], s['ladderTicks'])):
                raise ValueError('Ladder episodes may not overlap occupied states')
            if previous['sneak'] != s['sneak'] or previous['trace']['position'] != s['trace']['position']:
                raise ValueError('Episodes in one flight must share the complete input and Y timeline')
        seen[leg].append(s)
    route['movementUnits'] = []
    for leg, ticks in enumerate(route['airtimes']):
        events = [e for e in entries if e['leg'] == leg]
        profiles = route.get('verticalProfiles', [])
        profile = profiles[leg] if leg < len(profiles) else {}
        templates = profile.get('generatedUnits', profile.get('movementUnits'))
        if templates is None and (profile.get('caseEvents') or profile.get('contactEvents')):
            templates = profile_program(profile)
        if profile.get('composition') == 'movement_program':
            from parkour_solver.movement.units import ladder_units
            for event in events:
                ladder_units(event['schedule'], event['column']['id'])
            units = templates
        else:
            units = movement_program(ticks, events)
            if templates is not None:
                compile_units(ticks, templates)
                units = overlay_program(units, [u for u in templates if not u['type'].startswith('ladder_')])
        compiled = compile_units(ticks, units)
        for unit in units:
            for field, mask in [('jumpInputs', 'jump'), ('groundedInputs', 'grounded'), ('sneakInputs', 'sneak')]:
                if field in unit and mask in profile:
                    expected_inputs = [t for t in range(unit['start'], unit['end']) if profile[mask][t]]
                    if unit[field] != expected_inputs:
                        raise ValueError('Movement unit controls disagree with scalar profile')
        expected = [any(e['schedule']['ladderTicks'][t] for e in events) for t in range(ticks + 1)]
        if compiled['ladderTicks'] != expected:
            raise ValueError('Movement units disagree with ladder occupancy')
        route['movementUnits'].append(units)


def compile_ladder_events(route, events, ticks):
    ladder, sneak = [False] * (ticks + 1), [False] * ticks
    rows, contacts, scene_contacts = [], [], []
    for selected in route.get('ladderEvents', []):
        if selected['leg'] >= len(events):
            continue
        event = events[selected['leg']]
        s = selected['schedule']
        if event['landing'] is None:
            continue
        offset = event['jump']
        profiles = route.get('verticalProfiles', [])
        if selected['leg'] < len(profiles) and profiles[selected['leg']].get('composition') == 'movement_program':
            occupied = s['ladderTicks']
        else:
            units = movement_program(s['ticks'], [selected])
            occupied = compile_units(s['ticks'], units)['ladderTicks']
        ladder[offset:offset + s['ticks'] + 1] = [a or b for a, b in zip(ladder[offset:offset + s['ticks'] + 1], occupied)]
        sneak[offset:offset + s['ticks']] = s['sneak']
        walls, hits, scene_hits = schedule_constraints(s, selected['column'], selected['wall'],
            selected['entryFace'], selected['exitFace'], start=offset, margin=max(route['supportClearance'], 2e-7))
        rows.extend(walls)
        contacts.extend(hits)
        scene_contacts.extend(scene_hits)
        if s['entryState'] == 0:
            for state in range(event['landed'], event['jump']):
                ladder[state] = True
                bounds = region_rectangles(selected['column'], s['trace']['position'][0])[0]
                for axis, lo, hi in [('X', bounds[0], bounds[1]), ('Z', bounds[2], bounds[3])]:
                    rows.extend([{'mode': axis, 't1': state, 'cmp': 'GE', 'rhs': lo + 1e-6, 'name': 'ladder:run_inside'},
                                 {'mode': axis, 't1': state, 'cmp': 'LE', 'rhs': hi - 1e-6, 'name': 'ladder:run_inside'}])
    return rows, contacts, scene_contacts, ladder, sneak
