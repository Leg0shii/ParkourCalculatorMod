"""Independent validation of the selected scalar vertical/contact schedule."""
from parkour_solver.numerics import prepare, validate
from parkour_solver.physics.vertical import f32


def validate_vertical_schedule(route, profiles, plan):
    n = plan['ticks']
    contacts = []
    for event, profile in zip(plan['events'], profiles):
        if event['landing'] is not None:
            for contact in profile['contacts']:
                contacts.append(dict(contact, state=event['jump'] + contact['state']))
        for tick in range(event['landed'], event['jump']):
            contacts.append({'state': tick + 1, 'plane': profile['position'][0], 'direction': -1})
    from parkour_solver.movement.contact_combinations import merge_contacts
    contacts = merge_contacts([dict(c, axis=1) for c in contacts] + plan.get('verticalContacts', []))
    vertical = {'profile': 'minecraft-1.8.9', 'position': route['initial']['position'][1],
                'velocity': route['initial']['velocity'][1], 'onGround': True,
                'previousHorizontalCollision': False, 'threshold': route.get('physicsThreshold', .005),
                'jumpVelocity': [f32(.42) if jump else None for jump in plan['jump']],
                'sneak': plan.get('sneak', [False] * n),
                'contacts': [{k: v for k, v in c.items() if k != 'axis'} for c in contacts]}
    x, synthetic_hits = 0., []
    hit_states = {c['state'] for c in plan.get('contacts', [])}
    for t in range(n):
        x += .05 if t + 1 in hit_states else .1
        if t + 1 in hit_states:
            synthetic_hits.append({'state': t + 1, 'axis': 0, 'direction': 1, 'plane': x})
    problem = prepare({'version': 1, 'ticks': n, 'position': [0, 0], 'velocity': [0, 0],
                       'magnitude': [.1] * n, 'friction': [0] * n, 'base': [0] * n, 'yaw': [0] * n,
                       'contacts': synthetic_hits, 'ladderTicks': plan.get('ladderTicks', [False] * (n + 1)),
                       'objective': {'axis': 'X', 'sense': 'MAX', 'tick': n}, 'vertical': vertical})
    checked = validate(problem, problem['yaw'])
    last_profile = profiles[len(plan['events']) - 1] if profiles else {}
    terminal_ground = last_profile['grounded'][-1] if plan['stage'] == 'jump' and last_profile.get('composition') == 'movement_program' else True
    matched = True
    for event, profile in zip(plan['events'], profiles):
        if profile.get('composition') == 'movement_program' and event['landing'] is not None:
            first, end = event['jump'], event['landing'] + 1
            matched &= checked['y'][first:end] == profile['position'] and checked['vy'][first:end] == profile['velocity']
    return {'feasible': matched and checked['feasible'] and checked['onGround'] == plan['grounded'] + [terminal_ground],
            'vertical': vertical, 'y': checked['y'], 'vy': checked['vy'], 'onGround': checked['onGround']}
