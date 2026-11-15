import copy
import itertools
import math
import time

import numpy as np

from parkour_solver.validation.vertical import validate_vertical_schedule
from parkour_solver.validation.scene import check_geometry
from parkour_solver.scene.route import initial_guess, schedule, supports
from parkour_solver.numerics import prepare, validate
from parkour_solver.numerics import solve
from research.route_guidance.graph import assignments, edge_path


def rounded_assignment(family, relaxation):
    if relaxation.get('status') != 'solved':
        return None
    scores, previous = {family['start']: 0.}, {}
    for node in family['nodes']:
        if node not in scores:
            continue
        for i in family['outgoing'][node]:
            edge = family['edges'][i]
            candidate = scores[node]+math.log(max(1e-12, relaxation['masses'][i]))
            if edge['target'] not in scores or candidate > scores[edge['target']]:
                scores[edge['target']], previous[edge['target']] = candidate, i
    path, node = [], family['end']
    while node != family['start']:
        i = previous[node]
        path.append(i)
        node = family['edges'][i]['source']
    path.reverse()
    jumps = [family['edges'][i] for i in path if family['edges'][i]['kind'] == 'jump']
    options = [max(group, key=lambda i: relaxation['choices'][i]) for group in family['groups']]
    fixed = {'departure:'+str(e['leg']): e['tick'] for e in jumps}
    fixed.update({'passage:'+family['options'][i]['id']: family['options'][i]['gate'] for i in options})
    return dict(allocation=[e['source'][3] for e in jumps], options=options, fixed=fixed)


def make_problem(family, selection, compiler, *, seed=None, seconds=5):
    route = copy.deepcopy(family['route'])
    for index in selection['options']:
        option = family['options'][index]
        route['passages'].append(dict(leg=option['leg'], faces=option['faces'], gates=[option['gate']],
                                      blockId=option['id']))
    plan = schedule(route, selection['allocation'])
    if plan['ticks'] != family['ticks'] or plan['passageFailures']:
        raise ValueError('Selection is not a complete member of the family')
    vertical = validate_vertical_schedule(route, family['profiles'], plan)
    if not vertical['feasible']:
        raise ValueError('Ordinary profile did not match the physical vertical model')
    coefficients = compiler.compile(route, plan, seconds)
    walls = plan['walls'] + [dict(mode=w['axis'], t1=w['tick'], cmp=w['cmp'], rhs=w['rhs'])
                             for w in family['walls']]
    yaw = initial_guess(route, plan)
    if seed and seed.get('states'):
        path = edge_path(family, selection['allocation'])
        for t, index in enumerate(path):
            if seed.get('tickControls'):
                a = seed['tickControls'][t]
            elif seed.get('controls'):
                a = np.sum([seed['controls'][e['index']] for e in family['edges'] if e['tick'] == t], axis=0)
            else:
                continue
            if math.hypot(*a) > 1e-10:
                yaw[t] = math.degrees(math.atan2(a[1], a[0])-coefficients['base'][t])
    problem = prepare(dict(coefficients, version=1, ticks=plan['ticks'],
        position=[route['initial']['position'][a] for a in (0, 2)],
        velocity=[route['initial']['velocity'][a] for a in (0, 2)], yaw=yaw,
        threshold=family['threshold'], walls=walls, contacts=[], seconds=seconds,
        objective=dict(route['objective'], tick=plan['ticks']), vertical=vertical['vertical']))
    return route, plan, vertical, problem


def accept_candidate(family, selection, problem, route, plan, vertical, compiler, yaw, seconds=5):
    candidate = validate(problem, yaw)
    support_failures = [dict(tick=t, block=b, kind=kind) for t, b, kind in plan['supportChecks']
                        if not supports(route['blocks'][b], *candidate['positions'][t], kind=kind)]
    if not candidate['feasible'] or support_failures:
        return dict(status='unknown', scalarFeasible=candidate['feasible'], supportFailures=support_failures)
    candidate['rawMovement'] = []
    for t, facing in enumerate(yaw):
        angle = problem['base'][t]+math.radians(facing)
        candidate['rawMovement'].append([(v if abs(v) >= problem['threshold'] else 0.) + problem['magnitude'][t]*q
            for v, q in zip(candidate['velocity'][t], (math.cos(angle), math.sin(angle)))])
    candidate['friction'] = problem['friction']
    checked = check_geometry(family['geometry'], plan, candidate, vertical, compiler, seconds)
    if not checked['feasible']:
        return dict(status='unknown', scalarFeasible=True, geometryCheck=checked)
    sign = 1 if route['objective']['sense'] == 'MAX' else -1
    return dict(status='feasible', value=sign*candidate['objective'], candidate=candidate,
                selection=selection, geometryCheck=checked, scalarFeasible=True, supportFailures=[])


def refine(family, selection, compiler, *, seed=None, extra_seeds=(), seconds=2):
    started = time.monotonic()
    seeds = [seed, *extra_seeds, None] if seed else [None]
    best, attempts = None, []
    for index, initial in enumerate(seeds):
        remaining = seconds-(time.monotonic()-started)
        if remaining <= .02:
            break
        budget = remaining/(len(seeds)-index)
        route, plan, vertical, problem = make_problem(family, selection, compiler, seed=initial, seconds=budget)
        result = solve(problem, seconds=budget, stop_on_feasible=False)
        accepted = accept_candidate(family, selection, problem, route, plan, vertical, compiler,
                    result['candidate']['yaw'], max(.05, budget)) if result['feasible'] else dict(status='unknown')
        attempts.append(dict(seed='default' if initial is None else 'relaxed', status=accepted['status'],
                             value=accepted.get('value'), solverStatus=result.get('termination'),
                             geometryCheck=accepted.get('geometryCheck')))
        if accepted['status'] == 'feasible' and (best is None or accepted['value'] > best['value']):
            best = accepted
    result = best or dict(status='unknown')
    result.update(selection=selection, elapsedSeconds=time.monotonic()-started,
                  numericalCalls=len(attempts), attempts=attempts)
    return result


def _hull_weights(vertices, point):
    target = np.array([1., *point])
    for size in range(1, min(3, len(vertices))+1):
        for indices in itertools.combinations(range(len(vertices)), size):
            matrix = np.array([[1., *vertices[i]] for i in indices]).T
            values = np.linalg.lstsq(matrix, target, rcond=None)[0]
            if np.max(np.abs(matrix@values-target)) < 1e-9 and min(values) >= -1e-9:
                weights = np.zeros(len(vertices))
                weights[list(indices)] = values
                return weights
    raise ValueError('Real carry point is outside the declared graph hull')


def lift_witness(family, model, selection, candidate):
    p = model['program']
    values = np.zeros(len(p.names))
    for t, indices in enumerate(model['states']):
        values[indices] = candidate['positions'][t]+candidate['velocity'][t]
    for i in selection['options']:
        values[model['choices'][i]] = 1
    for t, i in enumerate(edge_path(family, selection['allocation'])):
        edge = family['edges'][i]
        values[model['masses'][i]] = 1
        values[model['inputs'][i]] = candidate['positions'][t]+candidate['velocity'][t]
        values[model['outputs'][i]] = candidate['positions'][t+1]+candidate['velocity'][t+1]
        angle = edge['base']+math.radians(candidate['yaw'][t])
        values[model['controls'][i]] = [edge['magnitude']*math.cos(angle), edge['magnitude']*math.sin(angle)]
        for axis in (0, 1):
            velocity = candidate['velocity'][t][axis]
            carry = velocity if abs(velocity) >= family['threshold'] else 0.
            values[model['carries'][i][axis]] = carry
            vertices = model['carryPoints'][i][axis]
            values[model['weights'][i][axis]] = _hull_weights(vertices, (velocity, carry))
    return dict(violation=float(p.violation(values)), initialState=values[model['states'][0]].tolist())
