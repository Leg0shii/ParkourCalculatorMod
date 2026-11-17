"""Replay acceptance against the supplied finite collision scene."""
from parkour_solver.physics.vertical import clamp, vertical_move


def check_geometry(geometry, plan, candidate, vertical, compiler, seconds):
    positions = [[xz[0], y, xz[1]] for xz, y in zip(candidate['positions'], vertical['y'])]
    movement = []
    ladder = plan.get('ladderTicks', [False] * (plan['ticks'] + 1))
    for tick in range(plan['ticks']):
        raw = candidate.get('rawMovement')
        x = raw[tick][0] if raw is not None else positions[tick + 1][0] - positions[tick][0]
        z = raw[tick][1] if raw is not None else positions[tick + 1][2] - positions[tick][2]
        movement.append([clamp(x) if ladder[tick] else x,
                         vertical_move(vertical['vertical'], tick, vertical['vy'][tick], ladder[tick]),
                         clamp(z) if ladder[tick] else z])
    if plan.get('movementComposition') or geometry.get('ladders') or any(ladder) or any(c.get('movementCase') for c in plan.get('sceneContacts', [])):
        velocities = [[v[0], y, v[1]] for v, y in zip(candidate['velocity'], vertical['vy'])]
        checked = compiler.check_scene(geometry, positions, movement, vertical['onGround'], seconds,
            contacts=plan.get('sceneContacts', []), sneak=plan.get('sneak', [False] * plan['ticks']),
            ladderTicks=ladder, velocities=velocities, friction=candidate['friction'])
    elif plan.get('sceneContacts'):
        checked = compiler.check_scene(geometry, positions, movement, vertical['onGround'], seconds,
                                       contacts=plan['sceneContacts'])
    else:
        checked = compiler.check_scene(geometry, positions, movement, vertical['onGround'], seconds)
    if not checked['feasible'] and checked.get('clips'):
        clip = checked['clips'][0]
        checked['solidIndex'] = geometry['solids'].index(clip['box'])
        checked['axis'] = clip['axis']
    return checked
