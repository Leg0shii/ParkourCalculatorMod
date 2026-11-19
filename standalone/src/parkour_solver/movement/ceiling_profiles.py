import copy

from parkour_solver.physics.vertical import REST_CARRY, f32, vertical_move, vertical_step, vertical_carry
from parkour_solver.schema import integer, number
from parkour_solver.movement.profiles import ordinary_jump_profile


def ceiling_options(solids, members=None, *, clearance=None):
    """Merged undersides of the member solids (all by default), clipped by every solid."""
    from parkour_solver.movement.contact_combinations import contact_clearance
    from parkour_solver.spatial.surfaces import scene_geometry
    return scene_geometry(dict(solids=list(solids))).contact_options(
        1, -1, clearance=contact_clearance({}) if clearance is None else clearance,
        members=range(len(solids)) if members is None else members)


def ceiling_contact(option, tick):
    from parkour_solver.movement.contact_combinations import contact_event
    return contact_event(option, 'Y', 1, tick, 'ceiling')


def ceiling_opportunities(solids, horizon):
    options = ceiling_options(solids)
    for tick in range(horizon):
        for option in options:
            yield dict(ceiling_contact(option, tick), blockId='+'.join(sorted('solid:' + str(i) for i in option.members)))


def ceiling_sequence(schedule, option, *, start=0):
    n = schedule['ticks']
    config = copy.deepcopy(schedule['trace']['vertical'])
    config['contacts'] = [c for c in config['contacts'] if c['direction'] > 0 or c.get('step') or c.get('case')]
    selected = []
    y, vy, ground = schedule['trace']['position'][0], schedule['trace']['velocity'][0], schedule['trace']['onGround'][0]
    plane = option.plane
    for tick in range(n):
        move = vertical_move(config, tick, vy, schedule['ladderTicks'][tick])
        if tick >= start and move > 0 and y <= plane < y + move:
            existing = next((c for c in config['contacts'] if c['state'] == tick + 1), None)
            if existing is not None and (existing['plane'] != plane or existing['direction'] != 1):
                return []
            if existing is None:
                config['contacts'].append({'state': tick + 1, 'plane': plane, 'direction': 1})
            selected.append(ceiling_contact(option, tick))
        y, move, ground, errors = vertical_step(config, tick, y, vy, schedule['ladderTicks'][tick], ground, 0)
        if errors:
            return []
        vy = vertical_carry(move, schedule['ladderTicks'][tick + 1], schedule['trace']['hit'][tick])
    return selected


def ceiling_profile(source_top, target_top, option, block_id, *, max_inputs=200):
    source_top, target_top = number(source_top, 'source top'), number(target_top, 'target top')
    integer(max_inputs, 'maximum flight inputs', 2, 200)
    ordinary = ordinary_jump_profile(source_top, source_top)
    plane = option.plane
    hit = next((t for t in range(ordinary['airtime'])
                if ordinary['position'][t] <= plane < ordinary['position'][t + 1]), None)
    if hit is None:
        return None
    contact = {'state': hit + 1, 'plane': plane, 'direction': 1}
    config = {'threshold': .005, 'jumpVelocity': [f32(.42)] + [None] * (max_inputs - 1),
              'sneak': [False] * max_inputs, 'contacts': [contact]}
    y, vy, ground = source_top, REST_CARRY, True
    positions, velocities, grounded = [y], [vy], [ground]
    for tick in range(max_inputs):
        raw = vertical_move(config, tick, vy, False)
        if tick > hit and raw < 0 and y >= target_top and y + raw <= target_top:
            config['contacts'].append({'state': tick + 1, 'plane': target_top, 'direction': -1})
        y, move, ground, failures = vertical_step(config, tick, y, vy, False, ground, 0)
        if failures:
            return None
        vy = vertical_carry(move, False, False)
        positions.append(y)
        velocities.append(vy)
        grounded.append(ground)
        if ground:
            if tick <= hit:
                return None
            return {'airtime': tick + 1, 'landingPhase': 'pre', 'position': positions, 'velocity': velocities,
                    'grounded': grounded, 'contacts': config['contacts'], 'jump': [True] + [False] * tick,
                    'ceilingEvent': {'offset': hit, 'plane': plane, 'boxes': [list(b) for b in option.boxes],
                                     'surface': option.surface, 'regions': [list(r) for r in option.regions],
                                     'blockId': block_id}}
    return None
