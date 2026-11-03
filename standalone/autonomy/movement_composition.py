import copy
import hashlib
import json

from standalone.ipopt.ladder import vertical_carry
from standalone.ipopt.model import integer, number
from .contact_combinations import merge_contacts
from .movement_units import compile_units, profile_program, shifted_unit
from .vertical_profiles import trace_ladder_schedule


def scalar_initial(initial):
    if isinstance(initial.get('position'), (list, tuple)):
        return dict(position=initial['position'][1], velocity=initial['velocity'][1],
                    onGround=initial.get('onGround', True),
                    previousHorizontalCollision=initial.get('previousHorizontalCollision', False))
    return dict(position=initial['position'], velocity=initial['velocity'],
                onGround=initial.get('onGround', False),
                previousHorizontalCollision=initial.get('previousHorizontalCollision', False))


def empty_program(initial, *, threshold=.005):
    if number(threshold, 'program threshold') < 0:
        raise ValueError('Program threshold must be nonnegative')
    initial = scalar_initial(initial)
    profile = dict(airtime=0, position=[initial['position']], velocity=[initial['velocity']],
        grounded=[initial['onGround']], jump=[], sneak=[], ladderTicks=[False], contacts=[],
        contactEvents=[], caseEvents=[], positionConstraints=[], generatedUnits=[],
        composition='movement_program', landingPhase='post')
    program = dict(initial=initial, profile=profile, ladderEvents=[], movementUnits=[],
                   terminal='open', terminalSupport=None, interactions=0, trace=None)
    if threshold != .005:
        program['threshold'] = threshold
    return program


def boundary_state(program, on_ladder=False):
    profile = program['profile']
    vy = profile['velocity'][-1]
    if profile['airtime'] and on_ladder != profile['ladderTicks'][-1]:
        vy = vertical_carry(program['trace']['movement'][-1], on_ladder, program['trace']['hit'][-1])
    return dict(position=profile['position'][-1], velocity=vy, onGround=profile['grounded'][-1],
                previousHorizontalCollision=(program['trace']['hit'][-1] if profile['airtime'] else
                                             program['initial']['previousHorizontalCollision']))


def profile_from_trace(trace, *, contacts=(), cases=(), constraints=(), units=None, landing_phase='post'):
    n = trace['ticks']
    profile = dict(airtime=n, position=trace['position'], velocity=trace['velocity'], grounded=trace['onGround'],
        jump=[v is not None for v in trace['vertical']['jumpVelocity']], sneak=trace['sneak'],
        ladderTicks=trace['ladderTicks'], contacts=trace['vertical']['contacts'],
        contactEvents=copy.deepcopy(list(contacts)), caseEvents=copy.deepcopy(list(cases)),
        positionConstraints=copy.deepcopy(list(constraints)), landingPhase=landing_phase,
        composition='movement_program')
    profile['generatedUnits'] = copy.deepcopy(units) if units is not None else profile_program(profile)
    if trace['vertical']['threshold'] != .005:
        profile['threshold'] = trace['vertical']['threshold']
    return profile


def movement_choice(kind, initial, trace, *, contacts=(), cases=(), constraints=(), units=None,
                    ladder_events=(), support=None, landing_phase='post'):
    if trace['status'] == 'invalid':
        return None
    profile = profile_from_trace(trace, contacts=contacts, cases=cases, constraints=constraints,
                                 units=units, landing_phase=landing_phase)
    events = list(contacts)+list(cases)
    owner = max((e.get('offset', e.get('input', -1))+1 for e in events), default=0)
    profile['constraintOwners'] = [owner]*len(profile['positionConstraints'])
    unit = next((u for u in profile['generatedUnits'] if u['type'] == kind), None)
    if unit is None:
        raise ValueError('A movement choice must contain its owning type')
    return dict(unit=unit, units=profile['generatedUnits'], initial=scalar_initial(initial), profile=profile,
                ladderEvents=copy.deepcopy(list(ladder_events)), terminalSupport=support,
                interaction=kind != 'normal')


def _contacts(events):
    merged = merge_contacts([dict(e, state=e['offset'] + 1) for e in events])
    return [{k: v for k, v in event.items() if k != 'state'} for event in merged]


def _shift_event(event, offset):
    event = copy.deepcopy(event)
    event['schedule']['constraintStart'] = offset
    event['schedule']['entryState'] += offset
    event['schedule']['exitState'] += offset
    event['schedule']['hit'] = [False] * offset + event['schedule']['hit']
    for key in ('climbWindow', 'stopInputs'):
        if event['schedule'].get(key) is not None:
            event['schedule'][key] = [t + offset for t in event['schedule'][key]]
    for key in ('contactEvents', 'ladderCeilingEvents'):
        if key in event['schedule']:
            event['schedule'][key] = [dict(c, offset=c['offset'] + offset) for c in event['schedule'][key]]
    event['schedule'].pop('generatedUnits', None)
    event['schedule'].pop('movementUnits', None)
    return event


def _events(events, trace):
    n = trace['ticks']
    result = copy.deepcopy(events)
    for index, event in enumerate(result):
        s = event['schedule']
        next_context = next((other['schedule']['constraintStart'] for other in result[index + 1:]
                             if other['column']['id'] == event['column']['id']), n + 1)
        s['constraintEnd'] = next_context
        entry, exit = s['entryState'], s['exitState']
        s.update(ticks=n, ladderTicks=[entry <= t < exit for t in range(n + 1)],
                 hit=(s['hit'] + [False] * n)[:n], sneak=trace['sneak'][:], trace=copy.deepcopy(trace),
                 postExitTicks=n - exit)
        from .ladder_geometry import region_contains
        from .movement_units import exit_matches, ladder_units
        if any(inside and not region_contains(event['column'], [event['column']['x'], y, event['column']['z']])
               for inside, y in zip(s['ladderTicks'], trace['position'])):
            return None
        if not exit_matches(s['exitKind'], trace['position'][exit], event['column']):
            return None
        try:
            ladder_units(s, event['column']['id'])
        except ValueError:
            return None
    return result


def append_choice(program, choice, *, max_inputs=200):
    old, tail = program['profile'], choice['profile']
    cut, size = old['airtime'], tail['airtime']
    integer(size, 'movement choice duration', 1, 200)
    if cut + size > min(max_inputs, 200):
        return None
    expected = boundary_state(program, tail['ladderTicks'][0])
    if choice.get('initial') != expected:
        raise ValueError('Movement choice consumes a different scalar boundary state')
    jump = old['jump'] + tail['jump']
    sneak = old['sneak'] + tail['sneak']
    ladder = old['ladderTicks'][:-1] + [old['ladderTicks'][-1] or tail['ladderTicks'][0]] + tail['ladderTicks'][1:]
    vertical = [{k: v for k, v in c.items() if k != 'axis'} for c in merge_contacts(
        [dict(c, axis=1) for c in old['contacts']] + [dict(c, state=cut + c['state'], axis=1) for c in tail['contacts']])]
    contacts = _contacts(old['contactEvents'] + [dict(c, offset=cut + c['offset']) for c in tail['contactEvents']])
    hit = [any(c['axis'] != 'Y' and c['offset'] == t for c in contacts) for t in range(cut + size)]
    from standalone.ipopt.ladder import f32
    trace = trace_ladder_schedule(program['initial'], ladder, sneak, hit,
        jump=[f32(.42) if value else None for value in jump], contacts=vertical, stop_on_landing=False,
        threshold=program.get('threshold', .005))
    if trace['status'] == 'invalid' or trace['ticks'] != cut + size:
        return None
    if trace['position'][:cut + 1] != old['position'] or trace['onGround'][:cut + 1] != old['grounded']:
        raise ValueError('Appending a choice rewrote a prior position or ground state')
    if trace['velocity'][:cut] != old['velocity'][:cut]:
        raise ValueError('Appending a choice rewrote a prior velocity')
    if trace['position'][cut:] != tail['position'] or trace['velocity'][cut:] != tail['velocity'] or trace['onGround'][cut:] != tail['grounded']:
        return None
    cases = copy.deepcopy(old['caseEvents'])
    for event in tail['caseEvents']:
        event = copy.deepcopy(event)
        event['input'] += cut
        event['anchors'] = {k: v + cut for k, v in event.get('anchors', {}).items()}
        cases.append(event)
    constraints = old['positionConstraints'] + [dict(row, t1=cut + row['t1']) for row in tail['positionConstraints']]
    units = copy.deepcopy(old['generatedUnits']) + [shifted_unit(unit, cut) for unit in tail['generatedUnits']]
    events = _events(program['ladderEvents'] + [_shift_event(e, cut) for e in choice.get('ladderEvents', [])], trace)
    if events is None:
        return None
    if events:
        from .movement_units import movement_program, overlay_program
        templates = units
        units = overlay_program(movement_program(trace['ticks'], events),
                                [u for u in templates if not u['type'].startswith('ladder_')])
        for unit in units:
            if unit['type'].startswith('ladder_'):
                effects = [copy.deepcopy(u) for u in templates if u['type'] not in ('normal', 'ladder_climb', 'ladder_fall')
                           and u['start'] < unit['end'] and u['end'] > unit['start']]
                if effects:
                    unit['effects'] = effects
            unit['jumpInputs'] = [t for t in range(unit['start'], unit['end']) if jump[t]]
            unit['groundedInputs'] = [t for t in range(unit['start'], unit['end']) if trace['onGround'][t]]
            unit['sneakInputs'] = [t for t in range(unit['start'], unit['end']) if sneak[t]]
    from .ladder_ceiling import own_ceiling_contacts
    try:
        contacts, units = own_ceiling_contacts(contacts, units)
    except ValueError:
        return None
    for event in events:
        s = event['schedule']
        s['ladderCeilingEvents'] = [copy.deepcopy(c) for c in contacts if c.get('effect') == 'ladder_ceiling'
            and c.get('ownerRegion') == event['column']['id'] and max(0, s['entryState'] - 1) <= c['offset'] < s['exitState']]
    profile = profile_from_trace(trace, contacts=contacts, cases=cases, constraints=constraints,
                                 units=units, landing_phase=tail['landingPhase'])
    profile['constraintOwners'] = old.get('constraintOwners', [0]*len(old['positionConstraints'])) + [
        cut+owner if owner else 0 for owner in tail.get('constraintOwners', [0]*len(tail['positionConstraints']))]
    masks = compile_units(profile['airtime'], units)
    if events and masks['ladderTicks'] != ladder:
        raise ValueError('Movement units disagree with composed ladder membership')
    result = dict(initial=copy.deepcopy(program['initial']), profile=profile, ladderEvents=events,
                  movementUnits=units, terminal='open', terminalSupport=choice.get('terminalSupport'),
                  interactions=program['interactions'] + int(choice.get('interaction', False)), trace=trace)
    for key in ('threshold', 'guideSeedYaw', 'repairState', 'solveScope', 'committedThrough'):
        if key in program:
            if key == 'repairState' and (program[key].get('context', {}).get('scope') or {}).get('solveIdentity'):
                continue
            result[key] = copy.deepcopy(program[key])
    return result


def truncate_program(program, ticks):
    n = program['profile']['airtime']
    integer(ticks, 'prefix ticks', 1, n)
    if ticks == n:
        return copy.deepcopy(program)
    if program['ladderEvents'] or any(program['profile']['ladderTicks']):
        raise ValueError('Checkpoint truncation currently supports non-ladder programs')
    result = copy.deepcopy(program)
    profile = result['profile']
    profile['airtime'] = ticks
    for key in ('position', 'velocity', 'grounded', 'ladderTicks'):
        profile[key] = profile[key][:ticks+1]
    for key in ('jump', 'sneak'):
        profile[key] = profile[key][:ticks]
    profile['contacts'] = [c for c in profile['contacts'] if c['state'] <= ticks]
    profile['contactEvents'] = [c for c in profile['contactEvents'] if c['offset'] < ticks]
    profile['caseEvents'] = [c for c in profile['caseEvents'] if c['input'] < ticks]
    for event in profile['caseEvents']:
        event['anchors'] = {k: v for k, v in event.get('anchors', {}).items() if v <= ticks}
    rows = [(r, owner) for r, owner in zip(profile['positionConstraints'],
        profile.get('constraintOwners', [0]*len(profile['positionConstraints'])))
        if r['t1'] <= ticks and (r.get('t2') is None or r['t2'] <= ticks)
        and (not owner or owner <= ticks or program.get('committedThrough', 0) >= owner)]
    profile['positionConstraints'] = [r for r, owner in rows]
    profile['constraintOwners'] = [owner for r, owner in rows]
    from .movement_units import profile_program
    profile['generatedUnits'] = profile_program(profile)
    profile['landingPhase'] = 'post'
    result.update(movementUnits=copy.deepcopy(profile['generatedUnits']), terminal='open', terminalSupport=None,
                  interactions=sum(u['type'] != 'normal' for u in profile['generatedUnits']))
    trace = result['trace']
    for key in ('position', 'velocity', 'onGround', 'ladderTicks'):
        trace[key] = trace[key][:ticks+1]
    for key in ('movement', 'climbRefreshed', 'sneak', 'hit'):
        trace[key] = trace[key][:ticks]
    trace['ticks'] = ticks
    trace['vertical']['jumpVelocity'] = trace['vertical']['jumpVelocity'][:ticks]
    trace['vertical']['sneak'] = trace['vertical']['sneak'][:ticks]
    trace['vertical']['contacts'] = [c for c in trace['vertical']['contacts'] if c['state'] <= ticks]
    trace['resume'] = dict(position=profile['position'][-1], velocity=profile['velocity'][-1],
                           onGround=profile['grounded'][-1], previousHorizontalCollision=trace['hit'][-1], onLadder=False)
    if 'guideSeedYaw' in result:
        result['guideSeedYaw'] = result['guideSeedYaw'][:ticks]
    return result


def program_physics_key(program):
    profile = {k: v for k, v in program['profile'].items() if k not in ('generatedUnits', 'constraintOwners')}
    profile['positionConstraints'] = sorted(
        [{k: v for k, v in row.items() if k != 'name'} for row in profile['positionConstraints']],
        key=lambda row: json.dumps(row, sort_keys=True))
    return json.dumps([profile, program['terminalSupport'], program.get('threshold', .005),
                       program['ladderEvents']], sort_keys=True)



def program_search_key(program):
    key = program_physics_key(program)
    key += '|owners=' + json.dumps([program['profile'].get('constraintOwners', []), program.get('committedThrough', 0), program.get('interactions', 0), program.get('solveScope')])
    if program.get('repairState') is not None:
        key += '|repair=' + hashlib.sha256(json.dumps(program['repairState'],sort_keys=True).encode()).hexdigest()
    return key


def compile_program(scene, program, *, require_complete=True):
    from .block_search import compile_block_path
    if not program['profile']['airtime']:
        raise ValueError('An empty prefix is not a complete movement route')
    if require_complete and (not program['profile']['grounded'][-1] or program['terminalSupport'] != scene['goalBlock']):
        raise ValueError('An open prefix is not a completed goal route')
    edge = dict(source=scene['startBlock'], target=scene['goalBlock'], profileKind='movement',
                profile=program['profile'], ladderEvents=program['ladderEvents'], entryFace=None, exitFace=None)
    compiled = compile_block_path(scene, [scene['startBlock'], scene['goalBlock']], [edge])
    compiled['route']['maxTotalRunTicks'] = 0
    compiled['route']['runBounds'] = [[0, 0]]
    if not require_complete:
        compiled['route']['terminalSupportRequired'] = False
    if 'threshold' in program:
        compiled['route']['physicsThreshold'] = program['threshold']
    if 'guideSeedYaw' in program:
        compiled['route']['warmYaw'] = program['guideSeedYaw'][:]
    return compiled
