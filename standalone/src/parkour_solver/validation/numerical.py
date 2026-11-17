"""Independent scalar replay/validation of a fixed continuous request."""
import math
from parkour_solver.schema import number, TOLERANCE
from parkour_solver.physics.angles import wrap
from parkour_solver.physics.vertical import CAP, branch as cap_branch, clamp, vertical_carry, vertical_step


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
