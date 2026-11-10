import copy
import hashlib
import json
import math

if __package__:
    from .ladder import CAP, branch as cap_branch, clamp, vertical_carry, vertical_step
else:
    from ladder import CAP, branch as cap_branch, clamp, vertical_carry, vertical_step


TOLERANCE = 1e-7


def number(value, name):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise ValueError(name + ' must be a finite number')
    return value


def integer(value, name, lower, upper):
    if isinstance(value, bool) or not isinstance(value, int) or not lower <= value <= upper:
        raise ValueError(name + ' is out of range')
    return value


def prepare(request):
    problem = copy.deepcopy(request)
    if not isinstance(problem, dict) or type(problem.get('version')) is not int or problem['version'] != 1:
        raise ValueError('Expected a version 1 continuous problem')
    n = integer(problem.get('ticks'), 'ticks', 1, 100000)
    for key, length in [('position', 2), ('velocity', 2), ('magnitude', n),
                        ('friction', n), ('base', n), ('yaw', n)]:
        values = problem.get(key)
        if not isinstance(values, list) or len(values) != length:
            raise ValueError(key + ' has the wrong length')
        for value in values:
            number(value, key)
    if 'fallbackYaw' in problem:
        values = problem['fallbackYaw']
        if not isinstance(values, list) or len(values) != n:
            raise ValueError('fallbackYaw has the wrong length')
        for value in values:
            number(value, 'fallbackYaw')
    if any(m < 0 for m in problem['magnitude']):
        raise ValueError('Acceleration magnitudes must be nonnegative')
    if any(f < 0 or f > 1 for f in problem['friction']):
        raise ValueError('Friction must be between zero and one')
    problem.setdefault('threshold', 0.0)
    threshold = number(problem['threshold'], 'threshold')
    if threshold < 0 or 0 < threshold <= 1e-8:
        raise ValueError('Threshold must be zero or greater than the 1e-8 branch guard')
    problem.setdefault('startYaw', 0.0)
    number(problem['startYaw'], 'startYaw')
    problem.setdefault('seconds', 10.0)
    if number(problem['seconds'], 'seconds') <= 0:
        raise ValueError('seconds must be positive')
    problem.setdefault('stopOnFeasible', False)
    if not isinstance(problem['stopOnFeasible'], bool):
        raise ValueError('stopOnFeasible must be a boolean')
    problem.setdefault('objectiveMode', 'configured')
    if problem['objectiveMode'] not in ('configured','constant_zero'):
        raise ValueError('Unsupported numerical objective mode')
    if problem['objectiveMode']=='constant_zero' and not problem['stopOnFeasible']:
        raise ValueError('Constant-zero objective requires feasible stopping')
    problem.setdefault('maxIterations', 10000)
    integer(problem['maxIterations'], 'maxIterations', 1, 1000000)
    problem.setdefault('contacts', [])
    if not isinstance(problem['contacts'], list) or any(not isinstance(c, dict) for c in problem['contacts']):
        raise ValueError('contacts must be a list of objects')
    seen = set()
    for contact in problem['contacts']:
        state = integer(contact.get('state'), 'contact state', 1, n)
        axis = integer(contact.get('axis'), 'contact axis', 0, 1)
        direction = contact.get('direction')
        if isinstance(direction, bool) or direction not in (-1, 1) or (state, axis) in seen:
            raise ValueError('Invalid or duplicate contact')
        number(contact.get('plane'), 'contact plane')
        seen.add((state, axis))
    problem.setdefault('walls', [])
    if not isinstance(problem['walls'], list) or any(not isinstance(w, dict) for w in problem['walls']):
        raise ValueError('walls must be a list of objects')
    for wall in problem['walls']:
        mode = wall.get('mode')
        if mode not in ('X', 'Z', 'F', 'DXZ', 'DZX'):
            raise ValueError('Unsupported constraint mode: ' + str(mode))
        limit = n - 1 if mode == 'F' else n
        integer(wall.get('t1'), 'constraint t1', 0, limit)
        if wall.get('t2') is not None:
            integer(wall['t2'], 'constraint t2', 0, limit)
        elif mode in ('DXZ', 'DZX'):
            raise ValueError(mode + ' needs t2')
        wall.setdefault('op', 'PLUS')
        if wall['op'] not in ('PLUS', 'MINUS') or wall.get('cmp') not in ('GE', 'LE', 'EQ'):
            raise ValueError('Invalid constraint comparison or operation')
        number(wall.get('rhs'), 'constraint rhs')
    objective = problem.get('objective')
    if not isinstance(objective, dict):
        raise ValueError('Missing objective')
    integer(objective.get('tick'), 'objective tick', 0, n)
    if objective.get('axis') not in ('X', 'Z') or objective.get('sense') not in ('MAX', 'MIN'):
        raise ValueError('Invalid objective axis or sense')
    objective.setdefault('type', 'POSITION')
    objective.setdefault('smoothLambda', 0.0)
    if objective['type'] not in ('POSITION', 'MOTION') or objective['smoothLambda'] != 0:
        raise ValueError('Only position/motion objectives with zero smooth lambda are supported')
    if objective.get('customYaw') is not None:
        number(objective['customYaw'], 'customYaw')
    if objective['type'] == 'MOTION' and (objective.get('customYaw') is None or objective['tick'] == 0):
        raise ValueError('A motion objective needs customYaw and a tick greater than zero')
    if 'inertiaPattern' in problem:
        pattern = problem['inertiaPattern']
        if threshold == 0 or not isinstance(pattern, list) or len(pattern) != n:
            raise ValueError('A fixed inertia pattern needs one row per tick and a positive threshold')
        for row in pattern:
            if not isinstance(row, list) or len(row) != 2 or any(type(b) is not int or b not in (-1, 0, 1, 2) for b in row):
                raise ValueError('Inertia branches must be -1, 0, 1, or 2 for each axis')
    if 'ladderTicks' in problem:
        flags = problem['ladderTicks']
        if not isinstance(flags, list) or len(flags) != n + 1 or any(type(v) is not bool for v in flags):
            raise ValueError('ladderTicks needs one boolean per state, including the final state')
    if 'ladderPattern' in problem:
        if 'ladderTicks' not in problem:
            raise ValueError('ladderPattern requires ladderTicks')
        pattern = problem['ladderPattern']
        if not isinstance(pattern, list) or len(pattern) != n:
            raise ValueError('ladderPattern needs one row per input tick')
        for t, row in enumerate(pattern):
            if (not isinstance(row, list) or len(row) != 2
                    or any(type(b) is not int or b not in (-1, 0, 1) for b in row)
                    or not problem['ladderTicks'][t] and any(row)):
                raise ValueError('Ladder branches must be -1, 0 or 1, and zero outside ladder ticks')
    if 'vertical' in problem:
        config = problem['vertical']
        if not isinstance(config, dict) or config.get('profile') != 'minecraft-1.8.9':
            raise ValueError('vertical requires the minecraft-1.8.9 profile')
        for key in ('position', 'velocity'):
            number(config.get(key), 'vertical.' + key)
        for key in ('onGround', 'previousHorizontalCollision'):
            if type(config.get(key)) is not bool:
                raise ValueError('vertical.' + key + ' must be a boolean')
        config.setdefault('threshold', 0.005)
        if number(config['threshold'], 'vertical.threshold') < 0:
            raise ValueError('vertical.threshold must be nonnegative')
        for key in ('sneak', 'jumpVelocity'):
            if not isinstance(config.get(key), list) or len(config[key]) != n:
                raise ValueError('vertical.' + key + ' needs one value per input tick')
        if any(type(v) is not bool for v in config['sneak']):
            raise ValueError('vertical.sneak must contain booleans')
        for value in config['jumpVelocity']:
            if value is not None and number(value, 'vertical.jumpVelocity') < 0:
                raise ValueError('Jump velocities must be nonnegative or null')
        config.setdefault('contacts', [])
        if not isinstance(config['contacts'], list):
            raise ValueError('vertical.contacts must be a list')
        seen_y = set()
        for contact in config['contacts']:
            if not isinstance(contact, dict):
                raise ValueError('Malformed vertical contact')
            state = integer(contact.get('state'), 'vertical contact state', 1, n)
            number(contact.get('plane'), 'vertical contact plane')
            if type(contact.get('direction')) is not int or contact['direction'] not in (-1, 1) or state in seen_y:
                raise ValueError('Invalid or duplicate vertical contact')
            if type(contact.get('step', False)) is not bool or contact.get('step', False) and contact['direction'] != -1:
                raise ValueError('A prescribed step must be a lower contact')
            if contact.get('case'):
                if contact['direction'] != -1 or contact.get('step'):
                    raise ValueError('A movement case requires its own lower contact')
                if contact['case'] not in ('normal_step','slab_boost','blip','blip_up','corner_jump_cancel','corner_blip_up','grind'):
                    raise ValueError('Unsupported vertical movement case')
                for key in ('lowerHeight','higherHeight','clearance'):
                    if key in contact:
                        number(contact[key], 'vertical case ' + key)
            seen_y.add(state)
        for key in ('referencePosition', 'referenceVelocity'):
            if key in config:
                if not isinstance(config[key], list) or len(config[key]) != n + 1:
                    raise ValueError('vertical.' + key + ' needs one value per state')
                for value in config[key]:
                    number(value, 'vertical.' + key)
    if 'movement' in problem:
        movement = problem['movement']
        if (not isinstance(movement, dict) or movement.get('profile') != 'minecraft-1.8.9'
                or movement.get('compiler') != 'mod-input-state-v1'):
            raise ValueError('Unsupported compiled movement profile')
        for key, length in [('horizontalCollision', n + 1), ('ground', n), ('sprinting', n), ('airSprinting', n)]:
            values = movement.get(key)
            if not isinstance(values, list) or len(values) != length or any(type(v) is not bool for v in values):
                raise ValueError('movement.' + key + ' has the wrong length or type')
        for key in ('moveForward', 'moveStrafe'):
            values = movement.get(key)
            if not isinstance(values, list) or len(values) != n:
                raise ValueError('movement.' + key + ' needs one value per input tick')
            for value in values:
                number(value, 'movement.' + key)
        expected_hits = {c['state'] for c in problem['contacts']}
        if any(value != (t in expected_hits) for t, value in enumerate(movement['horizontalCollision']) if t > 0):
            raise ValueError('Compiled movement collision schedule disagrees with declared contacts')
        if 'vertical' in problem:
            if (movement['horizontalCollision'][0] != problem['vertical']['previousHorizontalCollision']
                    or movement['ground'][0] != problem['vertical']['onGround']):
                raise ValueError('Compiled movement initial state disagrees with the vertical state')
    json.dumps(problem, allow_nan=False)
    return problem


def fingerprint(problem):
    physical = {k: v for k, v in problem.items() if k not in ('seconds', 'yaw', 'fallbackYaw', 'stopOnFeasible', 'objectiveMode', 'source', 'maxIterations')}
    return hashlib.sha256(json.dumps(physical, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()).hexdigest()


def wrap(value):
    value = math.fmod(value, 360)
    if value > 180:
        value -= 360
    if value <= -180:
        value += 360
    return value


def validate(problem, yaw):
    n = problem['ticks']
    if not isinstance(yaw, list) or len(yaw) != n:
        raise ValueError('Wrong returned facing count')
    for value in yaw:
        number(value, 'returned facing')
    positions, velocities = [problem['position'][:]], [problem['velocity'][:]]
    contacts = {(c['state'], c['axis']): c for c in problem['contacts']}
    threshold = problem['threshold']
    missed, wrong_side, mismatches = [], [], []
    ladder = problem.get('ladderTicks', [False] * (n + 1))
    caps, cap_mismatches = [], []
    vertical = problem.get('vertical')
    movement = problem.get('movement')
    movement_mismatches = []
    previous_hits = [vertical['previousHorizontalCollision'] if vertical else movement['horizontalCollision'][0] if movement else False]
    y, vy = ([vertical['position']], [vertical['velocity']]) if vertical else ([], [])
    grounded = [vertical['onGround']] if vertical else []
    vertical_failures = []
    for t in range(n):
        angle = problem['base'][t] + math.radians(yaw[t])
        move, next_velocity, tick_caps = [], [], []
        hit = False
        for axis in range(2):
            incoming = velocities[t][axis]
            zero = abs(incoming) < threshold
            if 'inertiaPattern' in problem:
                branch = problem['inertiaPattern'][t][axis]
                if (branch == 0 and not zero) or (branch in (-1, 1) and branch * incoming < threshold):
                    mismatches.append({'tick': t, 'axis': axis, 'incoming': incoming, 'branch': branch})
            direction = math.cos(angle) if axis == 0 else math.sin(angle)
            displacement = (0 if zero else incoming) + problem['magnitude'][t] * direction
            tick_caps.append(cap_branch(displacement) if ladder[t] else 0)
            if 'ladderPattern' in problem and ladder[t]:
                b = problem['ladderPattern'][t][axis]
                valid = (displacement <= -CAP + TOLERANCE if b == -1
                         else displacement >= CAP - TOLERANCE if b == 1
                         else -CAP - TOLERANCE <= displacement <= CAP + TOLERANCE)
                if not valid:
                    cap_mismatches.append({'tick': t, 'axis': axis, 'raw': displacement, 'branch': b})
            if ladder[t]:
                displacement = clamp(displacement)
            proposed = positions[t][axis] + displacement
            carry = problem['friction'][t] * displacement
            contact = contacts.get((t + 1, axis))
            if contact:
                approach = contact['direction'] * (positions[t][axis] - contact['plane'])
                reach = contact['direction'] * (proposed - contact['plane'])
                if approach > TOLERANCE:
                    wrong_side.append({'state': t + 1, 'axis': axis, 'violation': approach})
                if reach < 0:
                    missed.append({'state': t + 1, 'axis': axis, 'shortBy': -reach})
                if approach <= TOLERANCE and reach >= 0:
                    hit |= reach > 0
                    proposed, carry = contact['plane'], 0.0
            number(proposed, 'replayed position')
            number(carry, 'replayed velocity')
            move.append(proposed)
            next_velocity.append(carry)
        positions.append(move)
        velocities.append(next_velocity)
        caps.append(tick_caps)
        previous_hits.append(hit)
        if movement and hit != movement['horizontalCollision'][t + 1]:
            movement_mismatches.append({'state': t + 1, 'reason': 'horizontal_collision_schedule'})
        if vertical:
            yn, ymove, ground, failures = vertical_step(vertical, t, y[t], vy[t], ladder[t],
                                                       grounded[t], TOLERANCE)
            y.append(yn)
            vy.append(vertical_carry(ymove, ladder[t + 1], previous_hits[t + 1]))
            grounded.append(ground)
            vertical_failures.extend(failures)
            if movement and grounded[t] != movement['ground'][t]:
                movement_mismatches.append({'state': t, 'reason': 'ground_schedule'})
    worst, worst_name = 0.0, None
    if vertical:
        for key, values, label in [('referencePosition', y, 'Y'), ('referenceVelocity', vy, 'VY')]:
            for state, expected in enumerate(vertical.get(key, [])):
                violation = abs(values[state] - expected)
                if violation > worst:
                    worst, worst_name = violation, label + '@' + str(state)
    for wall in problem['walls']:
        mode, t1, t2 = wall['mode'], wall['t1'], wall.get('t2')
        if mode in ('X', 'Z'):
            axis = 0 if mode == 'X' else 1
            lhs = positions[t1][axis]
            if t2 is not None:
                lhs += (1 if wall['op'] == 'PLUS' else -1) * positions[t2][axis]
        elif mode == 'F':
            lhs = yaw[t1]
            if t2 is not None:
                lhs += (1 if wall['op'] == 'PLUS' else -1) * yaw[t2]
        else:
            dx = abs(positions[t1][0] - positions[t2][0])
            dz = abs(positions[t1][1] - positions[t2][1])
            lhs = dx - dz if mode == 'DXZ' else dz - dx
        residual = lhs - wall['rhs']
        if mode == 'F':
            residual = wrap(residual)
        number(residual, 'constraint residual')
        violation = abs(residual) if wall['cmp'] == 'EQ' else max(0.0, residual if wall['cmp'] == 'LE' else -residual)
        if violation > worst:
            worst, worst_name = violation, wall.get('name', mode + '@' + str(t1))
    objective = problem['objective']
    tick = objective['tick']
    if objective.get('customYaw') is None:
        value = positions[tick][0 if objective['axis'] == 'X' else 1]
    else:
        angle = math.radians(objective['customYaw'])
        point = positions[tick][:]
        if objective['type'] == 'MOTION' and tick > 0:
            point = [point[a] - positions[tick - 1][a] for a in range(2)]
        value = -math.sin(angle) * point[0] + math.cos(angle) * point[1]
    number(value, 'objective')
    result = {'feasible': worst <= TOLERANCE and not missed and not wrong_side and not mismatches
              and not cap_mismatches and not vertical_failures and not movement_mismatches,
            'violation': worst, 'worstConstraint': worst_name, 'objective': value,
            'missedContacts': missed, 'wrongSideContacts': wrong_side, 'patternMismatches': mismatches,
            'yaw': yaw[:], 'positions': positions, 'velocity': velocities}
    if movement:
        result.update(movementMismatches=movement_mismatches, horizontalCollision=previous_hits)
    if 'ladderTicks' in problem:
        result.update(ladderPattern=caps, ladderPatternMismatches=cap_mismatches,
                      horizontalCollision=previous_hits)
    if vertical:
        result.update(y=y, vy=vy, onGround=grounded, horizontalCollision=previous_hits,
                      verticalFailures=vertical_failures,
                      resume={'y': y[-1], 'vy': vy[-1], 'onGround': grounded[-1],
                              'previousHorizontalCollision': previous_hits[-1]})
    return result
