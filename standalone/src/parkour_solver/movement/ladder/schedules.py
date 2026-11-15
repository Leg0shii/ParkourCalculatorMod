import copy
import time
from collections import defaultdict, deque
from itertools import combinations

from parkour_solver.physics.vertical import f32, vertical_carry, vertical_move, vertical_step
from parkour_solver.numerics.model import integer, number
from parkour_solver.movement.units import exit_matches
from parkour_solver.movement.vertical_profiles import trace_ladder_schedule
from parkour_solver.movement.generation import generate_unit_programs, materialize_generated_units, sneak_domain


def climb_variants(first, end, ticks):
    integer(ticks, 'ticks', 1, 200)
    integer(first, 'first climb input', 0, ticks - 1)
    integer(end, 'exclusive climb end', first + 1, ticks)
    variants = []
    for beginning, ending in [(False, False), (True, False), (False, True), (True, True)]:
        if beginning and ending and end - first < 3:
            continue
        omitted = ({first} if beginning else set()) | ({end - 1} if ending else set())
        variants.append({'beginningStop': beginning, 'endStop': ending,
                         'stopInputs': sorted(omitted), 'climbWindow': [first, end],
                         'hit': [first <= t < end and t not in omitted for t in range(ticks)]})
    return variants


def sneak_patterns(ticks, eligible, first_clamped=None):
    integer(ticks, 'ticks', 1, 200)
    eligible = list(eligible)
    for tick in eligible:
        integer(tick, 'Sneak input', 0, ticks - 1)
    if len(set(eligible)) != len(eligible):
        raise ValueError('Duplicate Sneak input')
    if first_clamped is not None:
        integer(first_clamped, 'first clamped input', 0, ticks - 1)
    ordered = sorted(eligible, reverse=True)
    if first_clamped in ordered:
        ordered.remove(first_clamped)
        ordered.insert(0, first_clamped)
    size = len(ordered)
    seeds = [0]
    if first_clamped in eligible:
        seeds.append(1 << ordered.index(first_clamped))
    seeds.extend([(1 << size) - 1,
        sum(1 << i for i, t in enumerate(ordered) if t % 2 == 0),
        sum(1 << i for i, t in enumerate(ordered) if t % 2 != 0),
        sum(1 << i for i, t in enumerate(ordered) if t in sorted(eligible)[size // 2:])])
    seen = set()
    for bits in seeds:
        if bits not in seen:
            seen.add(bits)
            selected = {ordered[i] for i in range(size) if bits & (1 << i)}
            yield [t in selected for t in range(ticks)]
    for count in range(size + 1):
        for chosen in combinations(range(size), count):
            bits = sum(1 << i for i in chosen)
            if bits in seen:
                continue
            selected = {ordered[i] for i in chosen}
            yield [t in selected for t in range(ticks)]


def prepare_ladder_request(request):
    if not isinstance(request, dict):
        raise ValueError('Expected a ladder schedule request')
    config = copy.deepcopy(request)
    allowed = {'version', 'initial', 'region', 'entries', 'targetTop', 'exitKinds', 'exitStates',
               'maxInputs', 'entryDelay', 'jumpAtStart', 'allowPrimaryClimb', 'maxSkeletons', 'maxMaskWork', 'maxSchedules', 'contactVariants', 'finishAtExit'}
    if set(config) - allowed or type(config.get('version')) is not int or config['version'] != 1:
        raise ValueError('Expected a version 1 ladder schedule request with supported fields')
    for key, default, lo, hi in [('maxInputs', 40, 1, 200), ('entryDelay', 5, 0, 5),
                                 ('maxSkeletons', 2048, 1, 200000), ('maxMaskWork', 200000, 1, 2000000),
                                 ('maxSchedules', 10000, 1, 200000)]:
        integer(config.setdefault(key, default), key, lo, hi)
    n = config['maxInputs']
    if type(config.setdefault('finishAtExit', False)) is not bool:
        raise ValueError('finishAtExit must be boolean')
    if not config['finishAtExit']:
        number(config.get('targetTop'), 'target top')
    region = config.get('region')
    if not isinstance(region, dict) or set(region) != {'minY', 'maxY'}:
        raise ValueError('Supply one contiguous vertical ladder region')
    integer(region['minY'], 'region minY', -30000000, 30000000)
    integer(region['maxY'], 'region maxY', region['minY'] + 1, 30000001)
    initial = config.get('initial')
    if not isinstance(initial, dict):
        raise ValueError('Supply an initial scalar Y state')
    trace_ladder_schedule(initial, [False, False], [False], [False])
    if type(config.setdefault('allowPrimaryClimb', True)) is not bool:
        raise ValueError('allowPrimaryClimb must be boolean')
    if type(config.setdefault('jumpAtStart', False)) is not bool:
        raise ValueError('jumpAtStart must be boolean')
    if config['jumpAtStart'] and not initial.get('onGround', False):
        raise ValueError('A launch jump requires initial ground')
    entries = config.get('entries')
    if not isinstance(entries, list) or not 1 <= len(entries) <= 64:
        raise ValueError('Supply 1 through 64 validated entry windows')
    seen = set()
    for entry in entries:
        if not isinstance(entry, dict) or set(entry) - {'family', 'channel', 'firstValidatedState', 'firstCandidateState', 'unresolvedStates', 'firstHitLowerBounds', 'contactEvents'}:
            raise ValueError('Invalid entry window')
        if entry.get('family') not in ('front', 'side'):
            raise ValueError('Entry family must be front or side')
        channel = entry.setdefault('channel', entry['family'])
        if not isinstance(channel, str) or not channel or (entry['family'], channel) in seen:
            raise ValueError('Entry channels must be unique and nonempty')
        seen.add((entry['family'], channel))
        if ('firstValidatedState' in entry) == ('firstCandidateState' in entry):
            raise ValueError('Supply one validated or unprobed candidate entry state')
        state = integer(entry.get('firstValidatedState', entry.get('firstCandidateState')), 'first entry state', 0, n - 1)
        unresolved = entry.setdefault('unresolvedStates', [])
        if not isinstance(unresolved, list) or len(set(unresolved)) != len(unresolved):
            raise ValueError('Invalid unresolved entry states')
        for earlier in unresolved:
            integer(earlier, 'unresolved earlier entry', 0, state - 1)
        bounds = entry.get('firstHitLowerBounds', {})
        if not isinstance(bounds, dict):
            raise ValueError('First hit lower bounds require an entry-state mapping')
        for key, value in bounds.items():
            if not isinstance(key, str) or not key.isdecimal():
                raise ValueError('First hit lower bound keys require decimal entry states')
            integer(int(key), 'bounded entry state', state, n - 1)
            integer(value, 'first hit lower bound', 0, n)
    from parkour_solver.movement.contact_combinations import validate_contact_events
    variants = config.setdefault('contactVariants', [[]])
    if not isinstance(variants, list) or not 1 <= len(variants) <= 256:
        raise ValueError('Supply 1 through 256 contact variants')
    for selected in variants + [e.get('contactEvents', []) for e in entries]:
        validate_contact_events({'contactEvents': selected, 'airtimes': [n], 'sharedMovement': True})
    kinds = config.setdefault('exitKinds', ['side', 'top', 'bottom'])
    if not isinstance(kinds, list) or not kinds or any(k not in ('side', 'top', 'bottom') for k in kinds) or len(set(kinds)) != len(kinds):
        raise ValueError('Exit kinds must be unique side/top/bottom choices')
    states = config.setdefault('exitStates', list(range(1, n + 1)))
    if not isinstance(states, list) or not states or len(set(states)) != len(states):
        raise ValueError('Supply unique exit states')
    for state in states:
        integer(state, 'exit state', 1, n)
    return config


def _round_robin(streams):
    queue = deque(iter(stream) for stream in streams)
    while queue:
        stream = queue.popleft()
        try:
            value = next(stream)
        except StopIteration:
            continue
        queue.append(stream)
        yield value


def _context_skeletons(context, stats):
    seen = set()
    for variant in generate_unit_programs(context):
        stats['skeletonOrigins'] += 1
        key = tuple(variant['hit'])
        if key in seen:
            stats['duplicateSkeletons'] += 1
            continue
        seen.add(key)
        yield dict(context, **variant)


def _mask_work(skeleton, config):
    n, entry, exit_state = skeleton['ticks'], skeleton['entryState'], skeleton['exitState']
    ladder = [entry <= t < exit_state for t in range(n + 1)]
    first_clamped = entry
    from parkour_solver.movement.contact_combinations import merge_contacts
    extra = skeleton.get('contactEvents', [])
    actual_hits = [hit or any(e['axis'] != 'Y' and e['offset'] == t for e in extra)
                   for t, hit in enumerate(skeleton['hit'])]
    vertical = [{'state': e['offset'] + 1, 'plane': e['plane'], 'direction': e['direction'],
                 **({'step': True} if e['role'] == 'step' else {})} for e in extra if e['axis'] == 'Y']
    primary = {t for t, hit in enumerate(skeleton['hit']) if hit}
    eligible = [t for t in sneak_domain(skeleton['units'], skeleton, primary) if t < exit_state]
    prefixes = iter(sneak_patterns(n, eligible, first_clamped))
    tails = deque()
    exhausted = False
    while not exhausted or tails:
        if not exhausted:
            try:
                sneak = next(prefixes)
            except StopIteration:
                exhausted = True
            else:
                trace = trace_ladder_schedule(config['initial'], ladder, sneak, actual_hits,
                    jump=[f32(.42) if config['jumpAtStart'] else None] + [None] * (n - 1),
                    target_top=None if config['finishAtExit'] else config['targetTop'], min_landing_state=max(exit_state, max((e['offset'] + 1 for e in extra), default=0)),
                    contacts=vertical, occupied_y_bounds=(config['region']['minY'], config['region']['maxY']))
                region = config['region']
                reason = None
                if any(f['reason'] == 'occupied_y_outside_region' for f in trace['failures']):
                    reason = 'inside_y'
                elif trace['status'] == 'invalid':
                    reason = 'vertical_model'
                elif trace['ticks'] < exit_state:
                    reason = 'early_landing'
                elif config['finishAtExit'] and trace['status'] != 'simulated':
                    reason = 'airborne_terminal_required'
                elif any(ladder[t] and not region['minY'] <= y < region['maxY']
                         for t, y in enumerate(trace['position'])):
                    reason = 'inside_y'
                elif not exit_matches(skeleton['exitKind'], trace['position'][exit_state], region):
                    reason = skeleton['exitKind'] + '_exit_y'
                elif not config['finishAtExit'] and trace['status'] != 'landed':
                    reason = 'landing_horizon'
                if reason is None:
                    tails.append((trace, iter(sneak_patterns(trace['ticks'], range(exit_state, trace['ticks'])))))
                yield 'trace', reason
        if tails:
            trace, masks = tails.popleft()
            try:
                suffix = next(masks)
            except StopIteration:
                continue
            tails.append((trace, masks))
            candidate = copy.deepcopy({k: v for k, v in skeleton.items() if k != 'units'})
            candidate['ticks'] = trace['ticks']
            candidate['hit'] = skeleton['hit'][:trace['ticks']]
            candidate['sneak'] = [a or b for a, b in zip(trace['sneak'], suffix)]
            candidate['ladderTicks'] = trace['ladderTicks'][:]
            candidate['trace'] = copy.deepcopy(trace)
            candidate['trace']['sneak'] = candidate['sneak'][:]
            candidate['trace']['vertical']['sneak'] = candidate['sneak'][:]
            candidate['postExitTicks'] = trace['ticks'] - exit_state
            candidate['firstClampedInput'] = first_clamped
            candidate['exitInput'] = exit_state - 1
            candidate['lastClimbEligibleInput'] = exit_state - 2 if exit_state >= 2 else None
            candidate['movementUnits'] = materialize_generated_units(skeleton['units'], trace['ticks'], candidate['sneak'])
            candidate['terminal'] = 'airborne' if config['finishAtExit'] else 'landed'
            yield 'schedule', candidate


def generate_ladder_schedules(request, *, cancel_event=None, deadline=None):
    config = prepare_ladder_request(request)
    stats = {'skeletonOrigins': 0, 'duplicateSkeletons': 0, 'skeletons': 0, 'maskWork': 0,
             'verticalTraces': 0, 'generated': 0, 'rejected': defaultdict(int), 'entryDelayClipped': 0}
    contexts, windows = [], []
    for entry in config['entries']:
        first = entry.get('firstValidatedState', entry.get('firstCandidateState'))
        states = [first + delay for delay in range(config['entryDelay'] + 1)
                  if first + delay < config['maxInputs']]
        stats['entryDelayClipped'] += config['entryDelay'] + 1 - len(states)
        windows.append(dict(copy.deepcopy(entry), entryStates=states))
        for state in states:
            for exit_state in sorted(config['exitStates']):
                if exit_state <= state:
                    continue
                for kind in config['exitKinds']:
                    for variant in config['contactVariants']:
                        combined = entry.get('contactEvents', []) + variant
                        if config['finishAtExit'] and any(e['offset'] >= exit_state for e in combined):
                            continue
                        try:
                            from parkour_solver.movement.contact_combinations import merge_contacts
                            combined = merge_contacts([dict(e, state=e['offset'] + 1) for e in combined])
                        except ValueError:
                            continue
                        contexts.append({'entryFamily': entry['family'], 'entryChannel': entry['channel'],
                                     'entryState': state, 'entryDelay': state - first, 'allowPrimaryClimb': config['allowPrimaryClimb'],
                                     'firstHitLowerBound': entry.get('firstHitLowerBounds', {}).get(str(state), 0),
                                     'exitState': exit_state, 'exitKind': kind, 'ticks': exit_state if config['finishAtExit'] else config['maxInputs'],
                                     'contactEvents': combined})
    stream = _round_robin(_context_skeletons(c, stats) for c in contexts)
    skeletons, skeleton_limited = [], False
    for skeleton in stream:
        if len(skeletons) >= config['maxSkeletons']:
            skeleton_limited = True
            break
        skeletons.append(skeleton)
    stats['skeletons'] = len(skeletons)
    schedules, termination, work_limited = [], 'exhausted', False
    groups = defaultdict(list)
    for kind, value in _round_robin(_mask_work(s, config) for s in skeletons):
        if deadline is not None and time.monotonic() >= deadline:
            termination, work_limited = 'deadline', True
            break
        if cancel_event is not None and cancel_event.is_set():
            termination = 'cancelled'
            break
        if stats['maskWork'] >= config['maxMaskWork']:
            termination, work_limited = 'mask_work_limit', True
            break
        stats['maskWork'] += 1
        if kind == 'trace':
            stats['verticalTraces'] += 1
            if value is not None:
                stats['rejected'][value] += 1
            continue
        if len(schedules) >= config['maxSchedules']:
            termination, work_limited = 'schedule_limit', True
            break
        from parkour_solver.movement.ladder.ceiling import own_schedule_ceilings
        try:
            value = own_schedule_ceilings(value)
        except ValueError:
            stats['rejected']['ladder_ceiling_successor'] += 1
            continue
        groups[(value['ticks'], value['postExitTicks'])].append(len(schedules))
        schedules.append(value)
    if termination == 'exhausted' and skeleton_limited:
        termination = 'skeleton_limit'
    stats['generated'] = len(schedules)
    stats['rejected'] = dict(stats['rejected'])
    return {'status': 'generated', 'schedules': schedules, 'entryWindows': windows, 'stats': stats,
            'termination': termination, 'generationComplete': termination == 'exhausted',
            'heuristicDrops': skeleton_limited or work_limited,
            'timingGroups': [{'airtime': t, 'postExitTicks': a, 'scheduleIndices': indices}
                             for (t, a), indices in sorted(groups.items())],
            'validationLevel': 'vertical_schedule_only', 'horizontalSolve': 'not_run',
            'horizontalRanking': 'not_run'}
