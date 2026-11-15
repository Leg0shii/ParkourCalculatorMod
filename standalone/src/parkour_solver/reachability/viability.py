import math

from parkour_solver.movement.program import boundary_state
from parkour_solver.movement.normal import replay
from parkour_solver.reachability.intervals import interaction_reachability


def prefix_viability(scene, program, *, required_contacts, terminal_tick, enabled_types=None):
    if not required_contacts:
        return dict(dead=False, reason='no_outstanding_contact_requirement')
    state = boundary_state(program)
    remaining = terminal_tick-program['profile']['airtime']
    if remaining <= 0:
        return dict(dead=True, reason='mandatory_contacts_after_horizon', remaining=list(required_contacts))
    kinds = set(enabled_types or ('normal', 'stepping', 'ceiling'))
    if kinds - {'normal', 'stepping', 'ceiling'} or scene.get('climbableCells') or scene.get('maxWallHits'):
        return dict(dead=False, reason='unsupported_first_event_scope')
    if state['onGround'] or state['velocity'] >= 0:
        return dict(dead=False, reason='grounded_or_rising_continuation')
    trace = replay(state, remaining, threshold=program.get('threshold', .005))
    if trace['status'] == 'invalid':
        return dict(dead=False, reason='unknown_scalar_continuation')
    states = [dict(X=[-math.inf, math.inf], Z=[-math.inf, math.inf], Y=[y, y]) for y in trace['position']]
    phases = [dict(Y=dict(a, Y=[min(a['Y'][0], b['Y'][0]), max(a['Y'][1], b['Y'][1])]))
              for a, b in zip(states, states[1:])]
    envelopes = dict(states=states, phases=phases, contradiction=None)
    excluded = []
    for block in scene['blocks']:
        x0, x1, z0, z1 = block['bounds']
        checks = []
        for t in range(remaining):
            checked = interaction_reachability(envelopes, dict(regions=[dict(tick=t, phase='Y',
                bounds=[x0, block['top'], z0, x1, block['top'], z1])]))
            if not checked['ruledOut']:
                return dict(dead=False, reason='possible_first_floor_contact', block=block['id'], input=t)
            checks.append(checked)
        excluded.append(dict(block=block['id'], top=block['top'], checkedInputs=remaining,
                             maxFeetY=max(trace['position']), firstExclusion=checks[0]))
    return dict(dead=True, reason='descending_without_any_first_floor_contact', remaining=list(required_contacts),
        through=terminal_tick, enabledTypes=sorted(kinds), floorExclusions=excluded,
        justification='airborne descent cannot jump or hit a ceiling upward; supported steps require a first floor contact')
