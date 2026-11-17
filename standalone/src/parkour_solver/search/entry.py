import copy
import time

from parkour_solver.numerics import prepare, validate
from parkour_solver.numerics import solve
from parkour_solver.physics.vertical import f32
from parkour_solver.movement.contact_combinations import compile_contact_events, merge_contacts
from parkour_solver.scene.ladders import entry_channels, region_contains, schedule_constraints
from parkour_solver.numerics.builders import _raw_candidate, _vertical, ladder_waypoints, waypoint_seed
from parkour_solver.validation.scene import check_geometry


def build_entry_problem(context, column, wall, channel, entry, compiler, seconds, *,
                        prefix=None, extra_contacts=(), hit_on_entry=False):
    scene, route, base = context['scene'], context['route'], context['base']
    offset = base['ticks']
    cut = prefix['profile']['airtime'] if prefix is not None else 0
    local_n, n = cut + entry, offset + cut + entry
    if n == 0:
        raise ValueError('A zero-input entry uses initial membership')
    config = copy.deepcopy(context['vertical'])
    profile = prefix['profile'] if prefix is not None else {}
    jumps = list(profile.get('jump', [])) + ([prefix is None] + [False] * (entry - 1) if entry else [])
    sneaks = list(profile.get('sneak', [])) + [False] * entry
    config['jumpVelocity'].extend([f32(.42) if j else None for j in jumps])
    config['sneak'].extend(sneaks)
    local_ladder = list(profile.get('ladderTicks', [False]))[:-1] + [False] * (entry + 1)
    local_ladder[-1] = True
    ladder = base.get('ladderTicks', [False] * (offset + 1))[:-1] + local_ladder
    extra = list(profile.get('contactEvents', [])) + [dict(e, offset=cut + e['offset']) for e in extra_contacts]
    vertical = [dict(c, state=offset + c['state']) for c in profile.get('contacts', [])]
    vertical += [{'state': offset + cut + e['offset'] + 1, 'plane': e['plane'], 'direction': e['direction'],
                  **({'step': True} if e.get('role') == 'step' else {})} for e in extra_contacts if e['axis'] == 'Y']
    for c in vertical:
        if c not in config['contacts']:
            config['contacts'].append(c)
    hits = {c['state'] for c in base.get('contacts', [])}
    if prefix is not None:
        hits.update(offset + t + 1 for t, yes in enumerate(prefix['trace']['hit']) if yes)
    hits.update(offset + e['offset'] + 1 for e in extra if e['axis'] != 'Y')
    if hit_on_entry:
        hits.add(n)
    checked = _vertical(config, ladder, hits)
    if not checked['feasible'] or not column['minY'] <= checked['y'][-1] < column['maxY']:
        raise ValueError('Entry has no valid vertical state inside the region')
    selected = copy.deepcopy(prefix['ladderEvents']) if prefix is not None else []
    rows, contacts, scene_contacts = list(base['walls']), list(base.get('contacts', [])), list(base.get('sceneContacts', []))
    for event in selected:
        s = event['schedule']
        s.update(ticks=local_n, ladderTicks=[s['entryState'] <= t < s['exitState'] for t in range(local_n + 1)],
                 hit=(s['hit'] + [False] * local_n)[:local_n], trace={'position': checked['y'][offset:]})
    current = {'entryState': local_n, 'exitState': local_n + 1, 'exitKind': 'side',
               'ladderTicks': [False] * local_n + [True], 'hit': [False] * local_n,
               'trace': {'position': checked['y'][offset:]}}
    if hit_on_entry:
        current['hit'][-1] = True
    for event in selected + [{'schedule': current, 'column': column, 'wall': wall, 'entryFace': channel, 'exitFace': None}]:
        walls, h, sc = schedule_constraints(event['schedule'], event['column'], event['wall'],
                                            event['entryFace'], event['exitFace'], start=offset)
        rows.extend(walls)
        contacts.extend(h)
        scene_contacts.extend(sc)
    r, h, sc, _ = compile_contact_events({'contactEvents': extra, 'supportClearance': route['supportClearance']},
                                        [{'jump': offset, 'end': n}], n)
    rows.extend(r)
    contacts = merge_contacts(contacts + h)
    scene_contacts = merge_contacts(scene_contacts + sc, scene=True)
    ground = list(profile.get('grounded', [False]))[:-1] + ([prefix is None] + [False] * (entry - 1) if entry else [])
    plan = dict(base, ticks=n, grounded=base['grounded'] + ground, jump=base['jump'] + jumps,
        ladderTicks=ladder, sneak=config['sneak'], contacts=contacts, sceneContacts=scene_contacts,
        vertical=dict(config, referencePosition=checked['y'], referenceVelocity=checked['vy']))
    coefficient = compiler.compile(route, plan, seconds)
    problem = prepare(dict(coefficient, version=1, ticks=n, position=scene['initial']['position'][::2],
        velocity=scene['initial']['velocity'][::2], threshold=.005, yaw=[0.] * n,
        walls=rows, contacts=contacts, ladderTicks=ladder,
        objective={'axis': wall['axis'], 'sense': 'MAX' if wall['direction'] > 0 else 'MIN', 'tick': n}))
    point = [column['x'] + .5, column['z'] + .5]
    if hit_on_entry:
        point[0 if wall['axis'] == 'X' else 1] = wall['plane'] + wall['direction'] * .01
    selections = [(base['events'][e['leg']]['jump'], e) for e in route.get('ladderEvents', [])
                  if e['leg'] < len(base['events']) and base['events'][e['leg']]['landing'] is not None]
    selections.extend((offset, e) for e in selected)
    selections.append((offset, {'entryOnly': True, 'schedule': current}))
    problem['yaw'] = waypoint_seed(problem, ladder_waypoints(problem, selections) + [(n, point)])
    return problem, plan


def entry_contact_options(context, prefix=None, max_entry=14):
    from parkour_solver.movement.ceiling_profiles import ceiling_profile
    scene = context['scene']
    if not scene.get('ceilingHits', False):
        return []
    if prefix is not None:
        from parkour_solver.movement.contacts import program_seed
        from parkour_solver.movement.generation import generate_choices
        cut = prefix['profile']['airtime']
        seed = program_seed(prefix, cut + max_entry)
        return [[dict(c, offset=c['offset'] - cut) for c in choice['contactEvents']]
                for choice in generate_choices('ceiling', dict(mode='timeline', schedule=seed,
                    solids=scene['collisionScene']['solids']), start=cut)]
    route = context['route']
    top = route['blocks'][-2]['top']
    options = []
    for index, box in enumerate(scene['collisionScene']['solids']):
        profile = ceiling_profile(top, top, box, index, 'entry_ceiling')
        if profile:
            c = profile['ceilingEvent']
            options.append([dict(leg=0, offset=c['offset'], axis='Y', direction=1, plane=c['plane'], box=box, role='ceiling')])
    return options


def probe_region_entries(context, column, wall, compiler, *, deadline, candidate_seconds, max_entry,
                         prefix=None, channels=None, python_executable=None, cancel_event=None):
    records, entries = [], []
    ceiling_options = entry_contact_options(context, prefix, max_entry)
    faces = list(channels if channels is not None else entry_channels(column, wall))
    for channel in faces:
        unresolved, found = [], False
        for entry in range(max_entry + 1):
            if time.monotonic() >= deadline or cancel_event is not None and cancel_event.is_set():
                unresolved.extend(range(entry, max_entry + 1))
                break
            if entry == 0 and prefix is None and context['base']['ticks'] == 0:
                inside = region_contains(column, context['scene']['initial']['position'])
                records.append({'channel': channel['channel'], 'entryState': 0,
                                'status': 'feasible' if inside else 'blocked', 'reason': 'initial_membership'})
                if inside:
                    entries.append(dict(channel, firstValidatedState=0, unresolvedStates=[]))
                    found = True
                    break
                continue
            hit_choices = [False, True] if entry and wall.get('mode') != 'fall_only' else [False]
            variants = [([], hit) for hit in hit_choices]
            variants += [(cs, hit) for cs in ceiling_options if all(c['offset'] < entry for c in cs) for hit in hit_choices]
            for extra, hit in variants:
                if time.monotonic() >= deadline:
                    break
                geometry, reason = None, None
                try:
                    problem, plan = build_entry_problem(context, column, wall, channel, entry, compiler,
                        max(.01, deadline - time.monotonic()), prefix=prefix, extra_contacts=extra, hit_on_entry=hit)
                    solved = solve(problem, seconds=min(candidate_seconds, max(.001, deadline - time.monotonic())),
                        stop_on_feasible=True, python_executable=python_executable, cancel_event=cancel_event)
                    if solved['feasible']:
                        candidate = validate(problem, solved['candidate']['yaw'])
                        if candidate['feasible']:
                            _raw_candidate(problem, candidate)
                            vertical = {'vertical': problem['vertical'], 'y': candidate['y'], 'vy': candidate['vy'], 'onGround': candidate['onGround']}
                            geometry = check_geometry(context['scene']['collisionScene'], plan, candidate, vertical, compiler,
                                                      max(.01, deadline - time.monotonic()))
                            found = geometry['feasible']
                        else:
                            reason = 'Entry failed independent scalar validation'
                except (TimeoutError, ValueError, RuntimeError) as error:
                    reason = str(error)
                records.append({'channel': channel['channel'], 'entryState': entry, 'hitOnEntry': hit,
                    'status': 'feasible' if found else 'unknown', 'geometryCheck': geometry,
                    'reason': reason, 'contactEvents': extra})
                if found:
                    entries.append(dict(channel, firstValidatedState=entry, unresolvedStates=unresolved[:],
                                        contactEvents=copy.deepcopy(extra)))
                    break
            if found:
                break
            unresolved.append(entry)
        for state in ([] if found else unresolved):
            entries.append(dict(channel, channel=channel['channel'] + '@unresolved:' + str(state),
                                firstCandidateState=state, unresolvedStates=[]))
    return entries, records
