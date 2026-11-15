import copy

from parkour_solver.physics.vertical import REST_CARRY, f32
from parkour_solver.movement.contact_combinations import contact_combinations
from parkour_solver.movement.vertical_profiles import trace_ladder_schedule
from parkour_solver.scene.geometry import BODY_HEIGHT
from parkour_solver.scene.route import HALF_WIDTH


def contact_profile(source_top, target_top, events, *, max_inputs=200, ladder=None, sneak=None,
                    initial_velocity=REST_CARRY, initial_ground=True, jump_at_start=True,
                    min_landing_state=1):
    n = max_inputs
    ladder = list(ladder) if ladder is not None else [False] * (n + 1)
    sneak = list(sneak) if sneak is not None else [False] * n
    if len(ladder) < n + 1:
        ladder.extend([False] * (n + 1 - len(ladder)))
    if len(sneak) < n:
        sneak.extend([False] * (n - len(sneak)))
    hit, vertical = [False] * n, []
    for event in events:
        tick = event['offset']
        if not 0 <= tick < n:
            return None
        if event['axis'] == 'Y':
            vertical.append(dict(state=tick + 1, plane=event['plane'], direction=event['direction'],
                                 **({'step': True} if event.get('role') == 'step' else {})))
        else:
            hit[tick] = True
    try:
        trace = trace_ladder_schedule({'position': source_top, 'velocity': initial_velocity, 'onGround': initial_ground},
            ladder[:n + 1], sneak[:n], hit,
            jump=[f32(.42) if jump_at_start else None] + [None] * (n - 1),
            target_top=target_top, min_landing_state=min_landing_state, contacts=vertical)
    except ValueError:
        return None
    if trace['status'] != ('simulated' if target_top is None else 'landed') or any(e['offset'] >= trace['ticks'] for e in events):
        return None
    for event in events:
        if event['axis'] != 'Y':
            y, box = trace['position'][event['offset'] + 1], event['box']
            if y >= box[4] or y + BODY_HEIGHT <= box[1]:
                return None
    return {'airtime': trace['ticks'], 'landingPhase': 'pre', 'position': trace['position'],
            'velocity': trace['velocity'], 'grounded': trace['onGround'], 'contacts': trace['vertical']['contacts'],
            'jump': [v is not None for v in trace['vertical']['jumpVelocity']],
            'sneak': trace['sneak'], 'ladderTicks': trace['ladderTicks'],
            'contactEvents': copy.deepcopy(events)}


def contact_opportunities(solids, horizon, *, block_ids=None, ceilings=True, horizontal=True):
    for tick in range(horizon):
        for index, box in enumerate(solids):
            for axis, direction in [(a, d) for d in (1, -1) for a in ('X', 'Z', 'Y') if a != 'Y' or ceilings and d > 0]:
                if axis != 'Y' and not horizontal:
                    continue
                if axis == 'Y':
                    from parkour_solver.movement.ceiling_profiles import ceiling_contact
                    yield ceiling_contact(box, tick, solid_index=index, block_id=block_ids[index] if block_ids else None)
                    continue
                a = 'XYZ'.index(axis)
                plane = box[a] - (BODY_HEIGHT if axis == 'Y' else HALF_WIDTH) if direction > 0 else box[a + 3] + HALF_WIDTH
                yield {'leg': 0, 'offset': tick, 'axis': axis, 'direction': direction, 'plane': plane,
                       'box': copy.deepcopy(box), 'solidIndex': index,
                       'blockId': block_ids[index] if block_ids else 'solid:' + str(index),
                       'role': 'ceiling' if axis == 'Y' else 'contact'}


def generate_contact_profiles(source_top, target_top, opportunities, *, max_inputs=200,
                              max_events=3, limit=1000, ladder=None, sneak=None, ladder_windows=(), cancel_event=None,
                              max_horizontal=None):
    profiles, seen, attempts = [], set(), 0
    for chosen in contact_combinations(opportunities, max_events=max_events, limit=limit,
                                        ladder_windows=ladder_windows, cancel_event=cancel_event):
        attempts += 1
        if max_horizontal is not None and sum(e['axis'] != 'Y' for e in chosen) > max_horizontal:
            continue
        profile = contact_profile(source_top, target_top, chosen, max_inputs=max_inputs, ladder=ladder, sneak=sneak)
        if profile is None:
            continue
        key = tuple((e['offset'], e['axis'], e['direction'], e['plane']) for e in chosen)
        if key not in seen:
            seen.add(key)
            profiles.append(profile)
    return {'profiles': profiles, 'attempts': attempts, 'workLimit': limit,
            'complete': False, 'scope': 'bounded_event_combinations_without_route_specific_pruning'}
