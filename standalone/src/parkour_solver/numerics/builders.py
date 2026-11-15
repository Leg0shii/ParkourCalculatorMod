import math

from parkour_solver.numerics import prepare, validate


def waypoint_seed(problem, waypoints):
    yaw = problem['yaw'][:]
    for tick in range(min(problem['ticks'], 160)):
        targets = [(at, point) for at, point in waypoints if at > tick]
        if not targets:
            break
        at, point = min(targets, key=lambda item: item[0])
        state = validate(problem, yaw)
        coupling, drag = 0., 1.
        for t in range(tick, at):
            coupling += drag
            drag *= problem['friction'][t]
        delta = [point[a] - state['positions'][tick][a] - coupling * state['velocity'][tick][a] for a in (0, 1)]
        yaw[tick] = math.degrees(math.atan2(delta[1], delta[0]) - problem['base'][tick])
    return yaw


def _raw_candidate(problem, candidate):
    candidate['rawMovement'] = []
    for t, yaw in enumerate(candidate['yaw']):
        angle = problem['base'][t] + math.radians(yaw)
        candidate['rawMovement'].append([(v if abs(v) >= problem['threshold'] else 0) + problem['magnitude'][t] * q
            for v, q in zip(candidate['velocity'][t], (math.cos(angle), math.sin(angle)))])
    candidate['friction'] = problem['friction']
    candidate['movement'] = problem['movement']
    return candidate


def _vertical(config, ladder, hits):
    n, x, contacts = len(ladder) - 1, 0., []
    for t in range(n):
        x += .05 if t + 1 in hits else .1
        if t + 1 in hits:
            contacts.append({'state': t + 1, 'axis': 0, 'plane': x, 'direction': 1})
    return validate(prepare({'version': 1, 'ticks': n, 'position': [0, 0], 'velocity': [0, 0],
        'magnitude': [.1] * n, 'friction': [0] * n, 'base': [0] * n, 'yaw': [0] * n,
        'contacts': contacts, 'vertical': config, 'ladderTicks': ladder,
        'objective': {'axis': 'X', 'sense': 'MAX', 'tick': n}}), [0] * n)


def ladder_waypoints(problem, selections):
    waypoints = []
    for offset, selected in selections:
        if selected.get('entryOnly'):
            continue
        s, column, wall = selected['schedule'], selected['column'], selected['wall']
        point = [column['x'] + .5, column['z'] + .5]
        point[0 if wall['axis'] == 'X' else 1] = wall['plane']
        if s['entryState'] == 0 and offset == 0:
            tangent = 1 if wall['axis'] == 'X' else 0
            point[tangent] = problem['position'][tangent]
        if any(at == offset and e['schedule']['exitState'] <= s['entryState']
               for at, e in selections):
            entry_face = selected['entryFace']
            if entry_face['axis'] != wall['axis']:
                point[0 if entry_face['axis'] == 'X' else 1] = entry_face['plane'] - entry_face['direction'] * .03
        waypoints.append((offset + s['entryState'], point[:]))
        following = [e for at, e in selections if at == offset
                     and e['schedule']['entryState'] >= s['exitState']]
        transfer_target = None
        if following:
            transfer_target = point[:]
            face = selected['exitFace']
            transfer_target[0 if face['axis'] == 'X' else 1] = face['plane'] + face['direction'] * .13
        for t, hit in enumerate(s['hit']):
            if hit:
                pressing = point[:]
                if transfer_target is not None:
                    duration = max(1, s['exitState'] - s['entryState'] - 1)
                    progress = max(0., min(1., (t + 1 - s['entryState']) / duration))
                    tangent = 1 if wall['axis'] == 'X' else 0
                    inside_target = transfer_target[tangent]
                    if selected['exitFace']['axis'] != wall['axis']:
                        inside_target -= selected['exitFace']['direction'] * .14
                    if duration >= 8 and abs(inside_target - pressing[tangent]) / duration < .09:
                        progress *= progress
                    pressing[tangent] += progress * (inside_target - pressing[tangent])
                pressing[0 if wall['axis'] == 'X' else 1] += wall['direction'] * .01
                waypoints.append((offset + t + 1, pressing))
        face = selected['exitFace']
        if s['exitKind'] == 'top':
            point[0 if wall['axis'] == 'X' else 1] = wall['plane'] + wall['direction'] * .03
            if transfer_target is not None:
                point = transfer_target
        elif transfer_target is not None:
            point = transfer_target
        elif s['exitKind'] == 'side':
            point[0 if face['axis'] == 'X' else 1] = face['plane'] + face['direction'] * .03
        waypoints.append((offset + s['exitState'], point[:]))
    return waypoints
