import math
import time

from parkour_solver.numerics.model import integer, number


def objective_spec(value=None):
    spec = dict(value) if isinstance(value, dict) else {'axis': 'X', 'sense': 'MAX'} if value is None else None
    if spec is None or set(spec) - {'axis', 'sense'}:
        raise ValueError('Objective must contain only axis and sense')
    if spec.get('axis') not in ('X', 'Z') or spec.get('sense') not in ('MAX', 'MIN'):
        raise ValueError('Objective requires axis X/Z and sense MAX/MIN')
    return spec


def candidate_rank(best, objective):
    ticks = integer(best['plan']['ticks'], 'total ticks', 1, 100000)
    value = number(best['candidate']['objective'], 'candidate objective')
    return ticks, -value if objective['sense'] == 'MAX' else value


def objective_seed(problem):
    objective = problem['objective']
    target = math.radians(objective['customYaw'])+math.pi/2 if objective.get('customYaw') is not None else (
        0 if objective['axis'] == 'X' else math.pi / 2)
    if problem['objective']['sense'] == 'MIN':
        target += math.pi
    seed = []
    for yaw, base in zip(problem['yaw'], problem['base']):
        difference = target - base - math.radians(yaw)
        turn = math.degrees(math.atan2(math.sin(difference), math.cos(difference)))
        offset = max(-10, min(10, turn))
        seed.append(yaw + (offset if abs(offset) > 1e-3 else 5))
    return seed


def passage_seed(problem, route, plan, seconds=.05):
    from parkour_solver.numerics import validate
    from parkour_solver.scene.route import center_bounds
    from parkour_solver.constraints.avoidance import consecutive
    waypoints, affected = [], set()
    deadline = time.monotonic() + seconds
    for passage in route['passages']:
        if passage['leg'] >= len(plan['events']):
            continue
        event = plan['events'][passage['leg']]
        if event['landing'] is None:
            continue
        affected.update(range(event['landed'], event['landing']))
        source, target = center_bounds(route['blocks'][passage['leg']]), center_bounds(route['blocks'][passage['leg'] + 1])
        for i, offset in enumerate(passage['gates']):
            first, second = passage['faces'][i:i + 2]
            tick = event['jump'] + offset + int(consecutive(first, second))
            fraction = max(0, offset) / max(1, event['landing'] - event['jump'])
            point = [(source[a] + source[a + 1]) / 2 * (1 - fraction) + (target[a] + target[a + 1]) / 2 * fraction for a in (0, 2)]
            for face in (first, second):
                if face['axis'] != 'Y':
                    point[0 if face['axis'] == 'X' else 1] = face['plane'] + face['direction'] * max(route['supportClearance'], 2e-7)
            if tick > 0:
                waypoints.append((tick, point))
        waypoints.append((event['landing'] - 1, [(target[0] + target[1]) / 2, (target[2] + target[3]) / 2]))
    waypoints.sort(key=lambda point: point[0])
    seed = problem['yaw'][:]
    for tick in sorted(affected)[:128]:
        if time.monotonic() >= deadline:
            break
        targets = [(at, point) for at, point in waypoints if at > tick]
        if not targets:
            break
        at, target = targets[0]
        state = validate(problem, seed)
        distance, weight = 0.0, 1.0
        for j in range(tick, at):
            distance += weight
            weight *= problem['friction'][j]
        residual = [target[a] - state['positions'][tick][a] - distance *
                    (state['velocity'][tick][a] if abs(state['velocity'][tick][a]) >= problem['threshold'] else 0)
                    for a in range(2)]
        seed[tick] = math.degrees(math.atan2(residual[1], residual[0]) - problem['base'][tick])
    return seed
