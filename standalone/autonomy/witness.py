"""Independent witness replay and iteration, shared by runtime and tools."""

from .block_contacts import validate_vertical_schedule
from .obstacle_geometry import check_geometry
from .route import prepare_route, schedule, supports
from .solve_spec import full_witness
from standalone.ipopt import prepare, validate


def replay_witness(scene, best, compiler):
    import math
    route = prepare_route(best['route'])
    if route['initial'] != scene['initial']:
        raise ValueError('Replay changed the original initial state')
    plan = schedule(route, [0])
    vertical = validate_vertical_schedule(route, best['profiles'], plan)
    if not vertical['feasible']:
        return dict(passed=False, reason='vertical_replay')
    plan['vertical'] = vertical['vertical']
    coefficients = compiler.compile(route, plan, 5)
    problem = prepare(dict(coefficients, version=1, ticks=plan['ticks'],
        position=scene['initial']['position'][::2], velocity=scene['initial']['velocity'][::2],
        yaw=best['candidate']['yaw'], threshold=route.get('physicsThreshold', .005),
        walls=plan['walls'], contacts=plan['contacts'],
        objective=dict(route.get('guideObjective', route['objective']), tick=plan['ticks'])))
    candidate = validate(problem, problem['yaw'])
    support_ok = all(supports(route['blocks'][index], *candidate['positions'][tick], kind=kind)
                     for tick, index, kind in plan['supportChecks'])
    candidate['rawMovement'] = [[(v if abs(v) >= problem['threshold'] else 0) + problem['magnitude'][t]*q
        for v, q in zip(candidate['velocity'][t], (math.cos(problem['base'][t]+math.radians(yaw)),
                                                  math.sin(problem['base'][t]+math.radians(yaw))))]
        for t, yaw in enumerate(problem['yaw'])]
    candidate['friction'] = problem['friction']
    checked = check_geometry(scene['collisionScene'], plan, candidate, vertical, compiler, 5)
    same = candidate['positions'] == best['candidate']['positions'] and candidate['velocity'] == best['candidate']['velocity']
    return dict(passed=candidate['feasible'] and support_ok and checked['feasible'] and same,
                scalarFeasible=candidate['feasible'], supports=support_ok, geometry=checked,
                exactHorizontalReplay=same, ticks=plan['ticks'], threshold=problem['threshold'],
                objective=candidate['objective'], objectiveHasY=False)



def witnesses(result):
    if result.get('best') is not None and full_witness(result['best']):yield result['best']
    for window in result.get('windows',[]):
        for candidate in window['candidates']:
            if 'witness' in candidate and full_witness(candidate['witness']):yield candidate['witness']
            spatial=candidate.get('spatial_probe',{})
            if spatial.get('best') is not None and full_witness(spatial['best']):yield spatial['best']
    for rescue in result.get('rescues',[]):yield from witnesses(rescue['result'])

