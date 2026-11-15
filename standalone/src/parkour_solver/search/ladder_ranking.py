import copy
import math
import time
from collections import deque

from parkour_solver.numerics import prepare, validate
from parkour_solver.schema import integer
from parkour_solver.movement.contacts import contact_signature, interleave_families


def schedule_plan(schedule):
    trace = schedule['trace']
    return {'ticks': schedule['ticks'], 'grounded': trace['onGround'][:-1],
            'jump': [v is not None for v in trace['vertical']['jumpVelocity']],
            'sneak': schedule['sneak'], 'ladderTicks': schedule['ladderTicks'],
            'vertical': trace['vertical'],
            'sceneContacts': [{'state': t + 1, 'axis': 0} for t, hit in enumerate(schedule['hit']) if hit]
                + [{'state': e['offset'] + 1, 'axis': 'XYZ'.index(e['axis'])}
                   for e in schedule.get('contactEvents', [])]}


def straight_line_score(coefficients, ladder, exit_state):
    n = len(coefficients['magnitude'])
    yaw = [-math.degrees(angle) for angle in coefficients['base']]
    problem = prepare({'version': 1, 'ticks': n, 'position': [0, 0], 'velocity': [0, 0],
                       'magnitude': coefficients['magnitude'], 'friction': coefficients['friction'],
                       'base': coefficients['base'], 'yaw': yaw, 'threshold': .005,
                       'ladderTicks': ladder, 'objective': {'axis': 'X', 'sense': 'MAX', 'tick': n}})
    candidate = validate(problem, yaw)
    return {'distance': candidate['positions'][-1][0], 'averageSpeed': candidate['positions'][-1][0] / n,
            'exitSpeed': candidate['velocity'][exit_state][0], 'finalSpeed': candidate['velocity'][-1][0],
            'kind': 'zero_momentum_geometry_free_axis', 'initialVelocity': [0, 0]}


def rank_ladder_schedules(schedules, compiler, *, seconds=10, max_ranked=2048, representatives=4,
                          inputs=None, cancel_event=None, screen=None):
    integer(max_ranked, 'max ranked schedules', 1, 200000)
    integer(representatives, 'representatives', 1, 10000)
    deadline = time.monotonic() + seconds
    route = {'sharedMovement': True, 'inputs': inputs or {'forward': .98, 'strafe': 0, 'sprint': True,
                                                          'incomingSprint': False, 'force45': True}}
    cache, ranked = {}, []
    termination = 'exhausted'
    screened, evaluated = 0, 0
    buckets = {}
    for item in sorted(enumerate(schedules), key=lambda item: (item[1]['ticks'], item[1]['postExitTicks'])):
        buckets.setdefault((item[1]['ticks'], item[1]['postExitTicks']), deque()).append(item)
    active = deque(deque(interleave_families(bucket, lambda row: contact_signature(row[1].get('contactEvents', []))))
                   for bucket in buckets.values())
    ordered = []
    while active:
        bucket = active.popleft()
        ordered.append(bucket.popleft())
        if bucket:
            active.append(bucket)
    for original, schedule in ordered:
        if cancel_event is not None and cancel_event.is_set():
            termination = 'cancelled'
            break
        if len(ranked) >= max_ranked:
            termination = 'ranking_limit'
            break
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            termination = 'deadline'
            break
        plan = schedule_plan(schedule)
        key = (tuple(plan['grounded']), tuple(plan['jump']), tuple(plan['sneak']),
               tuple(plan['ladderTicks']), tuple(schedule['trace']['hit']),
               tuple((c['state'], c['plane'], c['direction'], c.get('step', False))
                     for c in plan['vertical']['contacts']))
        if key not in cache:
            try:
                cache[key] = compiler.compile(route, plan, remaining)
            except TimeoutError:
                termination = 'deadline'
                break
        evaluated += 1
        if screen is not None and screen(schedule, cache[key]) is not None:
            screened += 1
            continue
        score = straight_line_score(cache[key], schedule['ladderTicks'], schedule['exitState'])
        ranked.append({'schedule': copy.deepcopy(schedule), 'score': score, 'sourceIndex': original,
                       'coefficients': cache[key]})
    ranked.sort(key=lambda r: (r['schedule']['ticks'], r['schedule']['postExitTicks'], -r['score']['distance'],
                              -r['score']['exitSpeed'], -r['score']['finalSpeed'], r['sourceIndex']))
    counts, diversity, preferred, backups = {}, set(), [], []
    for row in ranked:
        s = row['schedule']
        bucket = s['ticks'], s['postExitTicks']
        channel = bucket + (s['entryFamily'], s['entryChannel'], s['exitKind'], contact_signature(s.get('contactEvents', [])))
        if counts.get(bucket, 0) < representatives or channel not in diversity:
            preferred.append(row)
            counts[bucket] = counts.get(bucket, 0) + 1
            diversity.add(channel)
        else:
            backups.append(row)
    return {'ranked': preferred, 'backups': backups, 'termination': termination,
            'rankedCount': len(ranked), 'compilationCount': len(cache),
            'screenedOut': screened, 'evaluatedCount': evaluated,
            'unrankedCount': len(schedules) - evaluated, 'acceptance': 'ranking_proxy_only'}
