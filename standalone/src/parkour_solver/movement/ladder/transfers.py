import copy
import time
import heapq
from parkour_solver.schema import integer

from parkour_solver.movement.ladder.episodes import compose_ladder_episodes
from parkour_solver.scene.ladders import entry_channels, exit_faces
from parkour_solver.movement.ladder.schedules import generate_ladder_schedules
from parkour_solver.movement.contacts import contact_signature, contact_variants, interleave_families, mixed_schedule_pool, program_seed
from parkour_solver.movement.units import shifted_unit


def episode_from_event(event, offset=0):
    s = event['schedule']
    episode = {'column': copy.deepcopy(event['column']), 'wall': copy.deepcopy(event['wall']),
        'entryState': offset + s['entryState'], 'exitState': offset + s['exitState'],
        'hitInputs': [offset + t for t, hit in enumerate(s['hit']) if hit],
        'entryFace': copy.deepcopy(event['entryFace']), 'exitFace': copy.deepcopy(event['exitFace']),
        'exitKind': s['exitKind']}
    if s.get('climbWindow') is not None:
        episode['climbWindow'] = [offset + t for t in s['climbWindow']]
    if s.get('generatedUnits') is not None or s.get('movementUnits') is not None:
        units = s.get('generatedUnits', s.get('movementUnits'))
        episode['generatedUnits'] = [dict(shifted_unit(u, offset), region=event['column']['id'])
                                     for u in units if u['type'].startswith('ladder_')]
    return episode


def open_program(event):
    s = event['schedule']
    return compose_ladder_episodes(s['trace']['position'][0], None, [episode_from_event(event)],
        max_inputs=s['ticks'], sneak=s['sneak'][:s['exitState']],
        contacts=[e for e in s.get('contactEvents', []) if e['offset'] < s['exitState']],
        initial_velocity=s['trace']['velocity'][0], initial_ground=s['trace']['onGround'][0],
        jump_at_start=s['trace']['vertical']['jumpVelocity'][0] is not None)


def recompose(prefix, episodes, target_top, max_inputs, sneak=None, contacts=None):
    trace = prefix['trace']
    return compose_ladder_episodes(trace['position'][0], target_top, episodes, max_inputs=max_inputs,
        sneak=prefix['profile']['sneak'] if sneak is None else sneak,
        contacts=prefix['profile']['contactEvents'] if contacts is None else contacts,
        initial_velocity=trace['velocity'][0], initial_ground=trace['onGround'][0],
        jump_at_start=trace['vertical']['jumpVelocity'][0] is not None)


def append_ladder_programs(prefix, column, wall, *, toward, max_inputs=200, max_entry=14,
                           max_programs=16, solids=(), max_contact_variants=16, max_events=3,
                           min_climb_inputs=0, deadline=None, cancel_event=None, entry_probe=None, entry_delay=5):
    integer(max_inputs, 'maximum chain inputs', 1, 200)
    integer(max_entry, 'maximum entry inputs', 0, 199)
    integer(max_programs, 'maximum extension programs', 1, 200000)
    integer(min_climb_inputs, 'minimum climb inputs', 0, 200)
    cut = prefix['profile']['airtime']
    used = {e['column']['id'] for e in prefix['episodes']}
    if cut + 2 >= max_inputs or column['id'] in used:
        return {'programs': [], 'generated': 0}
    previous = prefix['episodes'][-1]
    preferred = exit_faces(previous['column'], [(column['x'] + column.get('maxX', column['x'] + 1)) / 2,
                                               (column['z'] + column.get('maxZ', column['z'] + 1)) / 2])[0]
    faces = sorted(entry_channels(column, wall), key=lambda f:
                   (f['axis'] != preferred['axis'], f['direction'] == preferred['direction']))
    horizon = min(max_entry, max_inputs - cut - 2)
    entries, channels, probes = [], {}, []
    if entry_probe is None:
        for face in faces:
            for state in range(horizon + 1):
                channel = face['channel'] + '@' + str(state)
                channels[channel] = face
                entries.append({'family': face['family'], 'channel': channel, 'firstCandidateState': state})
    else:
        probe_prefix = copy.deepcopy(prefix)
        probe_prefix['episodes'][-1]['exitFace'] = preferred
        probe_prefix['ladderEvents'][-1]['exitFace'] = preferred
        now = time.monotonic()
        probe_deadline = min(deadline, now + max(.05, min(3., (deadline - now) * .35))) if deadline is not None else now + 3.
        probed, probes = entry_probe(probe_prefix, column, wall, max_entry=horizon, channels=faces, deadline=probe_deadline)
        active = [e for e in probed if 'firstValidatedState' in e] or probed
        deferred = [e for e in probed if e not in active]
        for entry in active:
            channels[entry['channel']] = entry
            entries.append({k: entry[k] for k in ('family','channel','firstValidatedState','firstCandidateState',
                                                'unresolvedStates','contactEvents') if k in entry})
    dropped_entries = max(0, len(entries) - 64)
    entries = entries[:64]
    if not entries:
        return {'programs': [], 'generated': 0, 'entryProbes': probes, 'unprobedEntries': entry_probe is None}
    request = {'version': 1,
        'initial': {'position': prefix['trace']['position'][-1], 'velocity': prefix['trace']['velocity'][-1],
                    'onGround': False, 'previousHorizontalCollision': prefix['trace']['hit'][-1]},
        'region': {k: column[k] for k in ('minY', 'maxY')}, 'entries': entries,
        'allowPrimaryClimb': wall.get('mode') != 'fall_only',
        'finishAtExit': True, 'jumpAtStart': False, 'maxInputs': max_inputs - cut,
        'entryDelay': entry_delay if entry_probe is not None else 0, 'maxSkeletons': 16000, 'maxSchedules': min(200000, max_programs * 32),
        'maxMaskWork': 200000}
    pool = generate_ladder_schedules(request, cancel_event=cancel_event, deadline=deadline)
    mixed = mixed_schedule_pool(request, solids, schedules=pool['schedules'], max_variants=max_contact_variants,
                                max_events=max_events, deadline=deadline, cancel_event=cancel_event)
    pool['schedules'].extend(mixed['schedules'])
    groups = {}
    for s in sorted((s for s in pool['schedules'] if sum(s['hit']) >= min_climb_inputs), key=lambda s: (s['entryChannel'].split('@')[0] != faces[0]['channel'],
                                                       sum(s['sneak']), s['ticks'] if s['exitKind'] == 'top' else -sum(s['hit']))):
        groups.setdefault((s['entryState'], s['exitKind'], bool(any(s['hit'])),
                           contact_signature(s.get('contactEvents', []))), []).append(s)
    ordered = [group[0] for _, group in sorted(groups.items(), key=lambda pair:
               (pair[0][0], pair[0][1] != 'top', not pair[0][2]))]
    ordered.extend(s for _, group in sorted(groups.items()) for s in group[1:])
    ordered = interleave_families(ordered, lambda s: contact_signature(s.get('contactEvents', [])),
                                  within=lambda s: s['entryState'])
    programs = []
    for s in ordered:
        if len(programs) >= max_programs or deadline is not None and time.monotonic() >= deadline \
                or cancel_event is not None and cancel_event.is_set():
            break
        episodes = copy.deepcopy(prefix['episodes'])
        episodes[-1]['exitFace'] = preferred
        event = {'schedule': s, 'column': column, 'wall': wall,
                 'entryFace': channels[s['entryChannel']], 'exitFace': exit_faces(column, toward)[0]}
        episodes.append(episode_from_event(event, cut))
        contacts = prefix['profile']['contactEvents'] + [dict(e, offset=cut + e['offset']) for e in s.get('contactEvents', [])]
        program = recompose(prefix, episodes, None, max_inputs,
                            prefix['profile']['sneak'] + s['sneak'], contacts)
        if program is not None:
            programs.append(program)
    return {'programs': programs, 'generated': len(pool['schedules']),
            'generation': {k: v for k, v in pool.items() if k not in ('schedules', 'timingGroups')},
            'mixedContacts': {k: v for k, v in mixed.items() if k != 'schedules'},
            'unprobedEntries': any('firstCandidateState' in e for e in entries) or entry_probe is not None and entry_delay > 0,
            'entryProbeMode': 'numerical' if entry_probe is not None else 'unprobed',
            'entryProbes': probes, 'entryWindows': entries, 'deferredEntryWindows': deferred if entry_probe is not None else [], 'entryWindowDrops': dropped_entries, 'entryHorizon': horizon,
            'entryHorizonClipped': max_entry > horizon, 'maxPrograms': max_programs}


def finish_programs(prefix, target_top, solids=(), *, max_inputs=200, max_contact_variants=16, max_events=3,
                    deadline=None, cancel_event=None, report=None):
    finish = recompose(prefix, prefix['episodes'], target_top, max_inputs)
    if finish is not None:
        yield finish
    cut = prefix['profile']['airtime']
    if cut >= max_inputs or not max_contact_variants:
        return
    seed = {'ticks': finish['profile']['airtime'], 'trace': finish['trace'],
            'ladderTicks': finish['profile']['ladderTicks']} if finish is not None else program_seed(prefix, max_inputs)
    if cut >= seed['ticks']:
        return
    variants = contact_variants(solids, seed['ticks'], schedules=[seed], start=cut,
        existing=prefix['profile']['contactEvents'], max_variants=min(256, max_contact_variants),
        max_events=max_events, deadline=deadline, cancel_event=cancel_event)
    if report is not None:
        report.update(phase='final_air', **{k: v for k, v in variants.items() if k != 'variants'})
    for contacts in variants['variants']:
        if deadline is not None and time.monotonic() >= deadline or cancel_event is not None and cancel_event.is_set():
            return
        changed = recompose(prefix, prefix['episodes'], target_top, max_inputs, contacts=contacts)
        if changed is not None:
            yield changed


def iter_chain_programs(first, regions, solids, target, *, max_inputs=200, max_entry=14, max_interactions=12,
                        max_programs=16, max_prefixes=64, max_pending=256, max_contact_variants=16, max_events=3,
                        min_climb_inputs=0, deadline=None, cancel_event=None, report=None, entry_probe=None, entry_delay=5):
    from parkour_solver.scene.ladders import movement_contexts
    integer(max_interactions, 'maximum ladder interactions', 1, 200)
    integer(max_prefixes, 'maximum chain prefixes', 1, 200000)
    integer(max_pending, 'maximum pending chain prefixes', 1, 200000)
    initial = open_program(first)
    report = report if report is not None else {}
    if initial is None:
        report.update(generated=0, termination='invalid_initial_prefix')
        return
    pending, serial, expanded, generated, records, drops = [(initial['profile']['airtime'], 0, initial)], 0, 0, 0, [], 0
    point = [(target['bounds'][a] + target['bounds'][a + 1]) / 2 for a in (0, 2)]
    termination = 'exhausted'
    while pending:
        if deadline is not None and time.monotonic() >= deadline:
            termination = 'deadline'
            break
        if cancel_event is not None and cancel_event.is_set():
            termination = 'cancelled'
            break
        if expanded >= max_prefixes:
            termination = 'prefix_limit'
            break
        _, _, prefix = heapq.heappop(pending)
        expanded += 1
        report.update(generated=generated, expandedPrefixes=expanded, remainingPrefixes=len(pending),
                      termination='in_progress', queueDrops=drops, generation=records,
                      unprobedEntries=entry_probe is None, maxInteractions=max_interactions, minimumTicksProven=False)
        if len(prefix['episodes']) > 1:
            final_report = {}
            records.append(final_report)
            yield from finish_programs(prefix, target['top'], solids, max_inputs=max_inputs,
                max_contact_variants=max_contact_variants, max_events=max_events,
                deadline=deadline, cancel_event=cancel_event, report=final_report)
        if len(prefix['episodes']) >= max_interactions:
            continue
        used = {e['column']['id'] for e in prefix['episodes']}
        for region in regions:
            if region['id'] in used:
                continue
            for wall in movement_contexts(region, solids, include_fall=min_climb_inputs == 0):
                extension = append_ladder_programs(prefix, region, wall, toward=point, max_inputs=max_inputs,
                    max_entry=max_entry, max_programs=max_programs, solids=solids,
                    max_contact_variants=max_contact_variants, max_events=max_events,
                    min_climb_inputs=min_climb_inputs, entry_probe=entry_probe, entry_delay=entry_delay,
                    deadline=deadline, cancel_event=cancel_event)
                generated += extension['generated']
                records.append({k: v for k, v in extension.items() if k != 'programs'})
                for program in extension['programs']:
                    serial += 1
                    heapq.heappush(pending, (program['profile']['airtime'], serial, program))
                if len(pending) > max_pending:
                    drops += len(pending) - max_pending
                    pending = heapq.nsmallest(max_pending, pending)
                    heapq.heapify(pending)
    report.update(generated=generated, expandedPrefixes=expanded, remainingPrefixes=len(pending),
        termination=termination, generation=records,
        unprobedEntries=entry_probe is None or any(r.get('unprobedEntries', False) for r in records),
        maxInteractions=max_interactions, minimumTicksProven=False, queueDrops=drops, maxPending=max_pending)


def chain_programs(*args, **kwargs):
    report = {}
    results = list(iter_chain_programs(*args, **kwargs, report=report))
    results.sort(key=lambda p: p['profile']['airtime'])
    return dict(report, programs=results)


def transfer_programs(first, second_column, second_wall, target, *, max_inputs=64, max_entry=14,
                      max_programs=16, solids=(), max_contact_variants=16, max_events=3,
                      deadline=None, cancel_event=None):
    prefix = open_program(first)
    if prefix is None:
        return {'programs': [], 'generated': 0}
    point = [(target['bounds'][a] + target['bounds'][a + 1]) / 2 for a in (0, 2)]
    result = append_ladder_programs(prefix, second_column, second_wall, toward=point,
        max_inputs=max_inputs, max_entry=max_entry, max_programs=max_programs,
        solids=solids, max_contact_variants=max_contact_variants, max_events=max_events,
        deadline=deadline, cancel_event=cancel_event)
    complete = [recompose(p, p['episodes'], target['top'], max_inputs) for p in result['programs']]
    result['programs'] = sorted([p for p in complete if p is not None], key=lambda p: p['profile']['airtime'])
    return result
