import hashlib
import json
import math

if __package__:
    from parkour_solver.physics.vertical import CLIMB_CARRY, f32, vertical_carry, vertical_move
    from parkour_solver.schema import TOLERANCE, number
    from parkour_solver.numerics.model import prepare
else:
    from parkour_solver.physics.vertical import CLIMB_CARRY, f32, vertical_carry, vertical_move
    from parkour_solver.schema import TOLERANCE, number
    from parkour_solver.numerics.model import prepare


def cell(state):
    return tuple(math.floor(value) for value in state['pos'])


def attach_capture(request, capture, start=None):
    if not isinstance(capture, dict) or not str(capture.get('mcVersion', '')).startswith('1.8.'):
        raise ValueError('Captured ladder physics currently requires Minecraft 1.8.x')
    result = prepare(request)
    n = result['ticks']
    if not isinstance(result.get('source', {}), dict):
        raise ValueError('Captured state attachment requires object source metadata')
    start = result.get('source', {}).get('startTick', 0) if start is None else start
    states, rows = capture.get('debug', []), capture.get('rows', [])
    if not isinstance(states, list) or not isinstance(rows, list):
        raise ValueError('Captured debug states and rows must be lists')
    if type(start) is not int or start < 0 or start + n >= len(states) or start + n > len(rows):
        raise ValueError('Capture does not cover every requested state and input')
    for state in states:
        if not isinstance(state, dict) or type(state.get('onGround')) is not bool:
            raise ValueError('Captured states require an onGround flag')
        for key in ('pos', 'vel'):
            if not isinstance(state.get(key), list) or len(state[key]) != 3:
                raise ValueError('Captured states require three-dimensional position/velocity')
            for value in state[key]:
                number(value, 'captured ' + key)
        if type(state.get('wallCollision', False)) is not bool:
            raise ValueError('Captured horizontal collision must be a boolean')
    for row in rows[:start + n]:
        if not isinstance(row, dict) or not isinstance(row.get('keys', []), list):
            raise ValueError('Captured input keys must be a list')
    initial = states[start]
    for axis, source_axis in enumerate((0, 2)):
        if (abs(result['position'][axis] - initial['pos'][source_axis]) > TOLERANCE
                or abs(result['velocity'][axis] - initial['vel'][source_axis]) > TOLERANCE):
            raise ValueError('Capture and compiled initial position/velocity disagree')
    climbs = [t for t, state in enumerate(states)
              if state.get('wallCollision') and abs(state['vel'][1] - CLIMB_CARRY) <= 1e-9]
    cells = {cell(states[t]) for t in climbs}
    explicit = set()
    authored = capture.get('angleSolver', {})
    if not isinstance(authored, dict) or not isinstance(authored.get('ticks', []), list):
        raise ValueError('Captured angleSolver ticks must be a list in an object')
    for row in authored.get('ticks', []):
        if not isinstance(row, dict) or not isinstance(row.get('override', {}), dict):
            raise ValueError('Captured solver ticks and overrides must be objects')
        if row.get('override', {}).get('medium') == 'LADDER':
            tick = row.get('tick')
            if type(tick) is not int:
                raise ValueError('Captured ladder tick must be an integer')
            if 0 <= tick < len(states):
                explicit.add(tick)
                cells.add(cell(states[tick]))
    ladder = [cell(state) in cells or start + t in explicit
              for t, state in enumerate(states[start:start + n + 1])]
    config = {'profile': 'minecraft-1.8.9', 'position': initial['pos'][1], 'velocity': initial['vel'][1],
              'onGround': bool(initial['onGround']),
              'previousHorizontalCollision': bool(initial.get('wallCollision', False)),
              'threshold': 0.005, 'jumpVelocity': [], 'sneak': [], 'contacts': [],
              'referencePosition': [s['pos'][1] for s in states[start:start + n + 1]],
              'referenceVelocity': [s['vel'][1] for s in states[start:start + n + 1]]}
    for t in range(n):
        row, before, after = rows[start + t], states[start + t], states[start + t + 1]
        keys = row.get('keys', [])
        boost = row.get('jumpBoostAmplifier', 0)
        if type(boost) is not int or boost < 0:
            raise ValueError('Captured jump boost must be a nonnegative integer')
        jump = f32(0.42) + f32(f32(boost) * f32(0.1)) if before['onGround'] and 'JUMP' in keys else None
        config['jumpVelocity'].append(jump)
        config['sneak'].append('SNEAK' in keys)
        raw = vertical_move(config, t, before['vel'][1], ladder[t])
        actual = after['pos'][1] - before['pos'][1]
        if abs(raw - actual) > TOLERANCE:
            reset = vertical_carry(0.0, ladder[t + 1], after.get('wallCollision', False))
            if abs(after['vel'][1] - reset) > TOLERANCE:
                raise ValueError('Unresolved vertical event at captured movement ' + str(start + t))
            step = ((before['onGround'] or raw < 0) and actual > max(raw, 0)
                    and actual <= f32(0.6) + TOLERANCE)
            contact = {'state': t + 1, 'plane': after['pos'][1],
                       'direction': -1 if step or raw < actual else 1}
            if step:
                contact['step'] = True
            config['contacts'].append(contact)
    result['ladderTicks'], result['vertical'] = ladder, config
    result.setdefault('source', {})['ladderCapture'] = {
        'method': 'recorded climb carry and climb-proven voxel membership',
        'climbStates': [t for t in climbs if start <= t <= start + n],
        'knownClimbableCells': [list(c) for c in sorted(cells)],
        'recordedHorizontalCollision': [bool(s.get('wallCollision', False)) for s in states[start:start + n + 1]],
        'prescribedVerticalContacts': len(config['contacts']),
        'captureDataSha256': hashlib.sha256(json.dumps(capture, sort_keys=True, allow_nan=False,
                                                      separators=(',', ':')).encode()).hexdigest(),
        'model': 'prescribed ladder, horizontal and vertical contacts; fixed recorded Y reference'}
    return prepare(result)
