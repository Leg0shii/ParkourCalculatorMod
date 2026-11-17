import copy

from parkour_solver.scene.ladders import entry_channels, exit_faces, ladder_regions, movement_contexts
from parkour_solver.movement.ladder.schedules import generate_ladder_schedules
from parkour_solver.movement.ladder.contacts import climb_contacts
from parkour_solver.movement.program import boundary_state, movement_choice, scalar_initial


def ladder_choices(context, kind):
    scene = context['scene']
    solids = scene['collisionScene']['solids']
    regions = ladder_regions(scene.get('climbableCells', scene['collisionScene'].get('ladders', [])))
    settings = scene.get('ladderSearch', {})
    horizon = min(context.get('maxInputs', 200), settings.get('maxInputs', 200))
    if horizon < 1:
        return
    if kind == 'ladder_fall' and settings.get('minClimbInputs', 0):
        return
    pool_cache = context.setdefault('ladderPools', {})
    goal = next(b for b in scene['blocks'] if b['id'] == scene['goalBlock'])
    toward = [(goal['bounds'][0] + goal['bounds'][1]) / 2, (goal['bounds'][2] + goal['bounds'][3]) / 2]
    for region in regions:
        if context.get('regionAllowed') is not None and not context['regionAllowed'](region):
            continue
        for wall in movement_contexts(region, solids):
            if context.get('checkBudget'):
                context['checkBudget']()
            faces = entry_channels(region, wall)
            max_entry = min(context.get('maxEntryInputs', settings.get('maxEntryInputs', 14)), horizon - 1)
            for inside in (False, True):
                state = (boundary_state(context['program'], inside) if 'program' in context else scalar_initial(context['state']))
                if inside and not region['minY'] <= state['position'] < region['maxY']:
                    continue
                entries = [dict(family=f['family'], channel=f['channel'] + '@' + str(t), firstCandidateState=t)
                           for f in faces for t in ([0] if inside else range(1, max_entry + 1))]
                if not entries:
                    continue
                channels = {f['channel'] + '@' + str(t): f for f in faces
                            for t in ([0] if inside else range(1, max_entry + 1))}
                for jumping in ([True, False] if state['onGround'] else [False]):
                    key = (region['id'], wall['axis'], wall['direction'], wall['plane'], inside, jumping)
                    if key not in pool_cache:
                        request = dict(version=1, initial=state, region={k: region[k] for k in ('minY', 'maxY')},
                            entries=entries[:64], finishAtExit=True, jumpAtStart=jumping,
                            allowPrimaryClimb=wall.get('mode') != 'fall_only', maxInputs=horizon,
                            entryDelay=0, maxSkeletons=settings.get('maxSkeletons', 2048),
                            maxSchedules=settings.get('maxSchedules', 2000), maxMaskWork=settings.get('maxMaskWork', 30000))
                        request.update(context.get('ladderRequest', {}))
                        pool_cache[key] = generate_ladder_schedules(request, deadline=context.get('deadline'),
                                                                  cancel_event=context.get('cancelEvent'))
                        if settings.get('maxContactVariants', 16):
                            from parkour_solver.movement.contacts import mixed_schedule_pool
                            mixed = mixed_schedule_pool(request, solids, schedules=pool_cache[key]['schedules'],
                                max_variants=settings.get('maxContactVariants', 16), max_events=settings.get('maxMixedContacts', 3),
                                deadline=context.get('deadline'), cancel_event=context.get('cancelEvent'),
                                box_allowed=context.get('boxAllowed'))
                            pool_cache[key]['schedules'].extend(mixed['schedules'])
                            pool_cache[key]['mixedContacts'] = {k: v for k, v in mixed.items() if k != 'schedules'}
                        context.setdefault('generationRecords', []).append(dict(type='ladder', region=region['id'],
                            **{k: v for k, v in pool_cache[key].items() if k not in ('schedules', 'timingGroups')}))
                    pool = pool_cache[key]
                    from parkour_solver.movement.contacts import interleave_families
                    schedules = sorted(pool['schedules'], key=lambda s: (s['ticks'], s['entryState'], sum(s['sneak'])))
                    schedules = interleave_families(schedules, lambda s: (s['entryState'], s['exitKind']),
                                                   within=lambda s: s['entryChannel'])
                    for schedule in schedules:
                        if sum(schedule['hit']) < settings.get('minClimbInputs', 0):
                            continue
                        actual_kind = 'ladder_climb' if any(schedule['trace']['climbRefreshed']) else 'ladder_fall'
                        if actual_kind != kind:
                            continue
                        if schedule['entryChannel'] not in channels:
                            continue
                        try:
                            finite = climb_contacts(schedule, wall) + schedule.get('contactEvents', [])
                        except ValueError:
                            continue
                        for face in exit_faces(region, toward):
                            event = dict(schedule=copy.deepcopy(schedule), column=copy.deepcopy(region), wall=copy.deepcopy(wall),
                                         entryFace=copy.deepcopy(channels[schedule['entryChannel']]), exitFace=face)
                            choice = ladder_event_choice(event)
                            if choice:
                                yield choice


def ladder_event_choice(event, *, start=0):
    from parkour_solver.movement.units import movement_program
    from parkour_solver.movement.vertical_profiles import trace_ladder_schedule
    schedule = event['schedule']
    stop = schedule['exitState']
    n = stop - start
    if n < 1 or schedule['entryState'] < start:
        raise ValueError('A ladder choice begins before its entry and ends at its exit')
    original = schedule['trace']
    initial = dict(position=original['position'][start], velocity=original['velocity'][start],
        onGround=original['onGround'][start],
        previousHorizontalCollision=original['hit'][start - 1] if start else
            original['vertical'].get('previousHorizontalCollision', False))
    finite = [dict(c, offset=c['offset'] - start) for c in climb_contacts(schedule, event['wall']) + schedule.get('contactEvents', [])
              if start <= c['offset'] < stop]
    ladder = [schedule['entryState'] <= t < stop for t in range(start, stop + 1)]
    hits = [any(c['axis'] != 'Y' and c['offset'] == t for c in finite) for t in range(n)]
    trace = trace_ladder_schedule(initial, ladder, schedule['sneak'][start:stop], hits,
        jump=original['vertical']['jumpVelocity'][start:stop],
        contacts=[dict(c, state=c['state'] - start) for c in original['vertical']['contacts'] if start < c['state'] <= stop],
        stop_on_landing=False)
    if trace['status'] == 'invalid':
        return None
    local = copy.deepcopy(event)
    s = local['schedule']
    s.update(ticks=n, entryState=schedule['entryState'] - start, exitState=n, ladderTicks=ladder,
             sneak=trace['sneak'], trace=trace, hit=schedule['hit'][start:stop], postExitTicks=0)
    for key in ('generatedUnits', 'movementUnits'):
        s.pop(key, None)
    for key in ('climbWindow', 'stopInputs'):
        if s.get(key) is not None:
            s[key] = [t - start for t in s[key]]
    from parkour_solver.movement.program import profile_from_trace
    from parkour_solver.movement.units import profile_program, overlay_program
    profile = profile_from_trace(trace, contacts=finite)
    units = overlay_program(movement_program(n, [local]), profile_program(profile))
    from parkour_solver.movement.ladder.ceiling import own_ceiling_contacts
    try:
        finite, units = own_ceiling_contacts(finite, units)
    except ValueError:
        return None
    s['contactEvents'] = [copy.deepcopy(e) for e in finite if e.get('role') != 'climb']
    s['ladderCeilingEvents'] = [copy.deepcopy(e) for e in finite if e.get('effect') == 'ladder_ceiling']
    kind = 'ladder_climb' if any(trace['climbRefreshed']) else 'ladder_fall'
    return movement_choice(kind, initial, trace, contacts=finite, units=units, ladder_events=[local])
