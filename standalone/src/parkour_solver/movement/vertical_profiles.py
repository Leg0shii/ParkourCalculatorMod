import copy
import math

from parkour_solver.physics.vertical import f32, vertical_carry, vertical_move, vertical_step
from parkour_solver.numerics.model import integer, number


def _flags(values, name, length=None):
    if not isinstance(values, (list, tuple)) or any(type(v) is not bool for v in values):
        raise ValueError(name + ' must contain booleans')
    if length is not None and len(values) != length:
        raise ValueError(name + ' has the wrong length')
    return list(values)


def trace_ladder_schedule(initial, ladder, sneak, hit, *, jump=None, target_top=None, min_landing_state=0, contacts=None,
                          occupied_y_bounds=None, stop_on_landing=True, threshold=.005):
    hit = _flags(hit, 'hit')
    n = integer(len(hit), 'ticks', 1, 200)
    ladder = _flags(ladder, 'ladder', n + 1)
    sneak = _flags(sneak, 'Sneak', n)
    if not isinstance(initial, dict) or set(initial) - {'position', 'velocity', 'onGround', 'previousHorizontalCollision'}:
        raise ValueError('Supply a scalar initial Y state')
    y, vy = number(initial.get('position'), 'initial Y'), number(initial.get('velocity'), 'initial VY')
    ground = initial.get('onGround', False)
    previous = initial.get('previousHorizontalCollision', False)
    if type(ground) is not bool or type(previous) is not bool:
        raise ValueError('Initial ground/collision flags must be boolean')
    impulses = list(jump) if jump is not None else [None] * n
    if len(impulses) != n:
        raise ValueError('Jump impulses have the wrong length')
    for value in impulses:
        if value is not None:
            number(value, 'jump impulse')
    if target_top is not None:
        number(target_top, 'target top')
    integer(min_landing_state, 'minimum landing state', 0, n)
    if occupied_y_bounds is not None:
        if not isinstance(occupied_y_bounds, (list, tuple)) or len(occupied_y_bounds) != 2:
            raise ValueError('Occupied Y bounds require a lower and upper limit')
        lower, upper = [number(v, 'occupied Y bound') for v in occupied_y_bounds]
        if lower >= upper:
            raise ValueError('Occupied Y bounds must be nonempty')
    from parkour_solver.movement.contact_combinations import merge_contacts
    selected = copy.deepcopy(contacts or [])
    if not isinstance(selected, list):
        raise ValueError('Vertical contacts must be a list')
    for contact in selected:
        integer(contact.get('state'), 'vertical contact state', 1, n)
        number(contact.get('plane'), 'vertical contact plane')
        if type(contact.get('direction')) is not int or contact['direction'] not in (-1, 1):
            raise ValueError('Vertical contact direction must be -1 or 1')
    selected = [{k: v for k, v in c.items() if k != 'axis'}
                for c in merge_contacts([dict(c, axis=1) for c in selected])]
    if number(threshold, 'velocity threshold') < 0:
        raise ValueError('Velocity threshold must be nonnegative')
    config = {'profile': 'minecraft-1.8.9', 'position': y, 'velocity': vy, 'onGround': ground,
              'previousHorizontalCollision': previous, 'threshold': threshold,
              'jumpVelocity': impulses, 'sneak': sneak, 'contacts': selected}
    positions, velocities, grounds, moves, refreshes, failures = [y], [vy], [ground], [], [], []
    landed = False
    for t in range(n):
        if occupied_y_bounds is not None and ladder[t] and not lower <= y < upper:
            failures.append({'state': t, 'reason': 'occupied_y_outside_region'})
            break
        if target_top is not None:
            movement = vertical_move(config, t, vy, ladder[t])
            if t + 1 >= min_landing_state and movement < 0 and y >= target_top and y + movement < target_top:
                if not any(c['state'] == t + 1 for c in config['contacts']):
                    config['contacts'].append({'state': t + 1, 'plane': target_top, 'direction': -1})
        y, movement, ground, errors = vertical_step(config, t, y, vy, ladder[t], ground, 0)
        failures.extend(errors)
        vy = vertical_carry(movement, ladder[t + 1], hit[t])
        positions.append(y)
        velocities.append(vy)
        grounds.append(ground)
        moves.append(movement)
        refreshes.append(ladder[t + 1] and hit[t])
        if occupied_y_bounds is not None and ladder[t + 1] and not lower <= y < upper:
            failures.append({'state': t + 1, 'reason': 'occupied_y_outside_region'})
            break
        if ground and any(c['state'] == t + 1 and c['direction'] < 0 and not c.get('case') for c in config['contacts']):
            landed = True
            if stop_on_landing:
                break
    count = len(moves)
    config['jumpVelocity'] = impulses[:count]
    config['sneak'] = sneak[:count]
    config['contacts'] = [c for c in config['contacts'] if c['state'] <= count]
    return {'status': 'invalid' if failures else 'landed' if landed else 'simulated', 'ticks': count,
            'position': positions, 'velocity': velocities, 'onGround': grounds, 'movement': moves,
            'climbRefreshed': refreshes, 'failures': failures, 'vertical': config,
            'ladderTicks': ladder[:count + 1], 'sneak': sneak[:count], 'hit': hit[:count],
            'resume': {'position': positions[-1], 'velocity': velocities[-1], 'onGround': grounds[-1],
                       'previousHorizontalCollision': hit[count - 1] if count else previous, 'onLadder': ladder[count]}}
