import copy
from functools import lru_cache

from parkour_solver.physics.vertical import REST_CARRY, f32, vertical_carry, vertical_step
from parkour_solver.numerics.model import integer, number
from parkour_solver.scene.route import prepare_route


@lru_cache(maxsize=1)
def normal_jump_height():
    return max(ordinary_jump_profile(0, 0)['position'])


def ordinary_jump_profile(source_top, target_top, *, max_inputs=200, threshold=.005):
    return _flight_profile(source_top, target_top, True, max_inputs, threshold)


def runoff_profile(source_top, target_top, *, max_inputs=200, threshold=.005):
    source_top = number(source_top, 'source top')
    target_top = number(target_top, 'target top')
    integer(max_inputs, 'maximum flight inputs', 1, 200)
    if target_top >= source_top:
        return None
    return _flight_profile(source_top, target_top, False, max_inputs, threshold)


def _flight_profile(source_top, target_top, jumping, max_inputs, threshold=.005):
    source_top = number(source_top, 'source top')
    target_top = number(target_top, 'target top')
    integer(max_inputs, 'maximum flight inputs', 2 if jumping else 1, 200)
    threshold=number(threshold,'physics threshold')
    if threshold<0:raise ValueError('Physics threshold must be nonnegative')
    config = {'threshold': threshold, 'jumpVelocity': [f32(.42) if jumping else None] + [None] * (max_inputs - 1),
              'sneak': [False] * max_inputs, 'contacts': []}
    y, vy, ground = source_top, REST_CARRY, True
    positions, velocities, grounded = [y], [vy], [ground]
    for tick in range(max_inputs):
        next_y, movement, next_ground, failures = vertical_step(config, tick, y, vy, False, ground, 0)
        if movement < 0 and y >= target_top and next_y <= target_top:
            config['contacts'] = [{'state': tick + 1, 'plane': target_top, 'direction': -1}]
            next_y, movement, next_ground, failures = vertical_step(config, tick, y, vy, False, ground, 0)
        if failures:
            raise ValueError('The existing vertical model rejected the derived profile: ' + str(failures))
        vy = vertical_carry(movement, False, False)
        y, ground = next_y, next_ground
        positions.append(y)
        velocities.append(vy)
        grounded.append(ground)
        if ground:
            return {'airtime': tick + 1, 'landingPhase': 'pre', 'position': positions,
                    'velocity': velocities, 'grounded': grounded, 'contacts': config['contacts'],
                    'jump': [jumping] + [False] * tick}
    return None


def stair_jump_profile(source_top, target_top):
    source_top = number(source_top, 'source top')
    target_top = number(target_top, 'target top')
    if not f32(.42) < target_top - source_top <= f32(.6):
        return None
    config = {'threshold': .005, 'jumpVelocity': [f32(.42), None], 'sneak': [False, False],
              'contacts': [{'state': 1, 'plane': target_top, 'direction': -1, 'step': True},
                           {'state': 2, 'plane': target_top, 'direction': -1}]}
    y, vy, ground = source_top, REST_CARRY, True
    positions, velocities, grounded = [y], [vy], [ground]
    for tick in range(2):
        y, movement, ground, failures = vertical_step(config, tick, y, vy, False, ground, 0)
        if failures:
            return None
        vy = vertical_carry(movement, False, False)
        positions.append(y)
        velocities.append(vy)
        grounded.append(ground)
    return {'airtime': 2, 'landingPhase': 'pre', 'position': positions, 'velocity': velocities,
            'grounded': grounded, 'contacts': config['contacts'], 'jump': [True, False]}


def compile_open_air_route(request, *, max_inputs=200):
    if not isinstance(request, dict):
        raise ValueError('Expected a block-route request')
    allowed = {'version', 'sceneModel', 'initial', 'blocks', 'inputs', 'runBounds', 'objective',
               'maxTotalRunTicks', 'supportClearance', 'constraints'}
    extra = set(request) - allowed
    if extra:
        raise ValueError('Unsupported open-air block-route fields: ' + ', '.join(sorted(extra)))
    if request.get('sceneModel') != 'open_air_supports':
        raise ValueError('Declare sceneModel=open_air_supports; solid side faces and surroundings are not modeled')
    integer(max_inputs, 'maximum flight inputs', 2, 200)
    blocks = request.get('blocks')
    if not isinstance(blocks, list) or len(blocks) < 2:
        raise ValueError('Supply a start support and at least one landing support')
    for block in blocks:
        if not isinstance(block, dict) or 'bounds' not in block or set(block) - {'id', 'bounds', 'top', 'standingBounds'}:
            raise ValueError('Supply one rectangular support per route entry, with bounds and top')
    route = copy.deepcopy(request)
    del route['sceneModel']
    route['airtimes'] = [2] * (len(blocks) - 1)
    route = prepare_route(route)
    ids = [block['id'] for block in route['blocks']]
    if len(set(ids)) != len(ids):
        raise ValueError('Route support IDs must be unique')
    profiles = []
    for index, (source, target) in enumerate(zip(route['blocks'], route['blocks'][1:])):
        profile = ordinary_jump_profile(source['top'], target['top'], max_inputs=max_inputs)
        if profile is None:
            return {'status': 'unknown', 'reason': 'no_ordinary_profile_within_limit',
                    'leg': index, 'sourceBlock': source['id'], 'targetBlock': target['id'],
                    'maxInputs': max_inputs, 'profiles': profiles}
        profile.update(sourceBlock=source['id'], targetBlock=target['id'])
        profiles.append(profile)
    route['airtimes'] = [profile['airtime'] for profile in profiles]
    route['profileDerivation'] = {'kind': 'ordinary_open_air', 'sceneModel': request['sceneModel'],
                                 'verticalModel': 'parkour_solver.physics.vertical',
                                 'suppliedAirtimes': False, 'solidSideFaces': False,
                                 'otherSupportContacts': False}
    return {'status': 'derived', 'route': prepare_route(route), 'profiles': profiles}
