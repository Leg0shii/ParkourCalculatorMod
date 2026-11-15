import copy
import math
import time
from collections import deque

from parkour_solver.physics.vertical import f32
from parkour_solver.numerics.model import integer
from parkour_solver.movement.contact_combinations import contact_combinations, merge_contacts
from parkour_solver.movement.contact_profiles import contact_opportunities
from parkour_solver.movement.ladder.schedules import generate_ladder_schedules
from parkour_solver.movement.vertical_profiles import trace_ladder_schedule
from parkour_solver.scene.geometry import BODY_HEIGHT


def contact_signature(events):
    return tuple(sorted((e['offset'], e['axis'], e['direction'], e['plane'], tuple(e['box']),
                         e.get('role', 'contact'), e.get('step', False)) for e in events))


def interleave_families(values, key, *, within=None):
    families = {}
    for value in values:
        families.setdefault(key(value), deque()).append(value)
    active = deque(iter(interleave_families(family, within)) if within is not None else iter(family)
                   for family in families.values())
    while active:
        family = active.popleft()
        try:
            value = next(family)
        except StopIteration:
            continue
        yield value
        active.append(family)


def contact_variants(solids, horizon, *, schedules=(), start=0, end=None, existing=(),
                     max_variants=16, max_events=3, deadline=None, cancel_event=None, box_allowed=None):
    integer(horizon, 'contact horizon', 1, 200)
    integer(start, 'contact start', 0, horizon - 1)
    end = integer(horizon if end is None else end, 'contact end', start + 1, horizon)
    integer(max_variants, 'contact variants', 0, 256)
    integer(max_events, 'combined contact events', 1, 200)
    if not max_variants:
        return {'variants': [], 'attempted': 0, 'termination': 'disabled', 'complete': False}
    if deadline is not None and time.monotonic() >= deadline or cancel_event is not None and cancel_event.is_set():
        return {'variants': [], 'attempted': 0,
                'termination': 'cancelled' if cancel_event is not None and cancel_event.is_set() else 'deadline',
                'complete': False}
    from parkour_solver.movement.ceiling_profiles import ceiling_opportunities
    from parkour_solver.movement.ladder.ceiling import predict_ceiling_sequences
    schedules = list(schedules)
    predictions = []
    for s in schedules:
        for box in solids:
            if box_allowed is not None and not box_allowed(box):
                continue
            predictions.extend(predict_ceiling_sequences(s, box, start=start, end=end))
    opportunities = [e for e in contact_opportunities(solids, horizon, ceilings=False) if start <= e['offset'] < end]
    opportunities.extend(e for e in ceiling_opportunities(solids, horizon) if start <= e['offset'] < end)
    if box_allowed is not None:
        opportunities = [e for e in opportunities if box_allowed(e['box'])]
    def distance(event):
        tick, box = event['offset'], event['box']
        scores = []
        for s in schedules:
            trace = s['trace']
            if tick >= trace['ticks']:
                continue
            y = trace['position'][tick]
            after = y + trace['movement'][tick]
            if event['axis'] == 'Y':
                scores.append(0 if y <= event['plane'] < after else 1 + abs(event['plane'] - max(y, after)))
            else:
                scores.append(max(0, after - box[4], box[1] - after - BODY_HEIGHT))
        return min(scores, default=0)
    opportunities.sort(key=distance)
    opportunities = list(interleave_families(opportunities, lambda e: e['axis']))
    work = max_variants * 100
    predictions = list({contact_signature(sequence): sequence for sequence in predictions}.values())
    singles = list({contact_signature([sequence[0]]): [sequence[0]] for sequence in predictions}.values())
    streams = deque([iter(singles), iter(predictions), iter(contact_combinations(opportunities, max_events=max_events,
        limit=work, independent_events=True, cancel_event=cancel_event))])
    variants, seen, attempted, termination = [], set(), 0, 'exhausted'
    old = contact_signature(existing)
    while streams:
        if deadline is not None and time.monotonic() >= deadline:
            termination = 'deadline'
            break
        if cancel_event is not None and cancel_event.is_set():
            termination = 'cancelled'
            break
        if attempted >= work or len(variants) >= max_variants:
            termination = 'variant_limit' if len(variants) >= max_variants else 'work_limit'
            break
        stream = streams.popleft()
        try:
            extra = next(stream)
        except StopIteration:
            continue
        streams.append(stream)
        attempted += 1
        try:
            selected = merge_contacts([dict(e, state=e['offset'] + 1) for e in list(existing) + extra])
        except ValueError:
            continue
        signature = contact_signature(selected)
        if signature == old or signature in seen:
            continue
        seen.add(signature)
        variants.append([{k: v for k, v in e.items() if k != 'state'} for e in selected])
    combinations = sum(math.comb(len(opportunities), k) for k in range(1, min(max_events, len(opportunities)) + 1))
    return {'variants': variants, 'attempted': attempted, 'termination': termination,
            'complete': termination == 'exhausted' and combinations <= work,
            'scope': [start, end], 'routeSpecificPruning': False}


def mixed_schedule_pool(request, solids, *, schedules=(), max_variants=16, max_events=3,
                        deadline=None, cancel_event=None, box_allowed=None):
    ordered = sorted(schedules, key=lambda s: (s['exitKind'] != 'top', not any(s['hit']), s['ticks']))
    seeds = list(interleave_families(ordered, lambda s: s['entryState']))[:max(1, max_variants)]
    n = request['maxInputs']
    raw = trace_ladder_schedule(request['initial'], [False] * (n + 1), [False] * n, [False] * n,
        jump=[f32(.42) if request.get('jumpAtStart') else None] + [None] * (n - 1))
    seeds.append({'ticks': n, 'trace': raw, 'ladderTicks': [False] * (n + 1)})
    proposals = contact_variants(solids, n, schedules=seeds, max_variants=min(256, max_variants),
                                max_events=max_events, deadline=deadline, cancel_event=cancel_event, box_allowed=box_allowed)
    if not proposals['variants']:
        return {'schedules': [], 'contacts': proposals, 'generation': None}
    config = copy.deepcopy(request)
    selected = []
    for extra in proposals['variants']:
        for previous in config.get('contactVariants', [[]]):
            try:
                merged = merge_contacts([dict(e, state=e['offset'] + 1) for e in previous + extra])
            except ValueError:
                continue
            selected.append([{k: v for k, v in e.items() if k != 'state'} for e in merged])
    config['contactVariants'] = selected[:256]
    if not config['contactVariants']:
        return {'schedules': [], 'contacts': proposals, 'generation': None}
    pools, records = [], []
    remaining_work = config.get('maxMaskWork', 200000)
    remaining_schedules = config.get('maxSchedules', 10000)
    for index, variant in enumerate(config['contactVariants']):
        if not remaining_work or not remaining_schedules or deadline is not None and time.monotonic() >= deadline \
                or cancel_event is not None and cancel_event.is_set():
            break
        one = copy.deepcopy(config)
        one['contactVariants'] = [variant]
        left = len(config['contactVariants']) - index
        one['maxMaskWork'] = max(1, remaining_work // left)
        one['maxSkeletons'] = min(one.get('maxSkeletons', 2048), max(1, one['maxMaskWork'] // 2))
        one['maxSchedules'] = max(1, remaining_schedules // left)
        generated = generate_ladder_schedules(one, deadline=deadline, cancel_event=cancel_event)
        pools.extend(generated['schedules'])
        remaining_work -= generated['stats']['maskWork']
        remaining_schedules -= len(generated['schedules'])
        records.append({k: v for k, v in generated.items() if k not in ('schedules', 'timingGroups')})
    return {'schedules': list(interleave_families(pools, lambda s: contact_signature(s.get('contactEvents', [])))),
            'contacts': {k: v for k, v in proposals.items() if k != 'variants'},
            'contactVariants': config['contactVariants'],
            'generation': {'variants': records, 'maskWork': config.get('maxMaskWork', 200000) - remaining_work,
                           'generated': len(pools), 'unvisitedVariants': len(config['contactVariants']) - len(records)}}


def program_seed(program, horizon):
    profile, initial = program['profile'], program['trace']
    ladder = (profile['ladderTicks'] + [False] * (horizon + 1))[:horizon + 1]
    sneak = (profile['sneak'] + [False] * horizon)[:horizon]
    hits = (initial['hit'] + [False] * horizon)[:horizon]
    jump = (profile['jump'] + [False] * horizon)[:horizon]
    trace = trace_ladder_schedule({'position': initial['position'][0], 'velocity': initial['velocity'][0],
        'onGround': initial['onGround'][0],
        'previousHorizontalCollision': initial['vertical']['previousHorizontalCollision']}, ladder, sneak, hits,
        jump=[f32(.42) if yes else None for yes in jump], contacts=profile['contacts'])
    return {'ticks': trace['ticks'], 'trace': trace, 'ladderTicks': ladder[:trace['ticks'] + 1]}
