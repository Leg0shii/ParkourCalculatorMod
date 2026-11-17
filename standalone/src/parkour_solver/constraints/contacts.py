"""Physical floor requirements and their alternative constraint rows."""
import copy
from parkour_solver.physics.vertical import f32, vertical_move, vertical_step
from parkour_solver.scene.route import center_bounds, supports


def floor_checks(scene, block_ids, profiles, *, envelopes=None):
    checks = []
    by_id = {b['id']: b for b in scene['blocks']}
    for leg, profile in enumerate(profiles):
        target_top = by_id[block_ids[leg + 1]]['top']
        jumps = profile.get('jump', [True] + [False] * (profile['airtime'] - 1))
        config = {'threshold': profile.get('threshold', .005), 'jumpVelocity': [f32(.42) if j else None for j in jumps],
                  'sneak': profile.get('sneak', [False] * profile['airtime'])}
        for offset in range(profile['airtime']):
            y = profile['position'][offset]
            next_y = profile['position'][offset + 1]
            movement = vertical_move(config, offset, profile['velocity'][offset], profile.get('ladderTicks', [False] * (profile['airtime'] + 1))[offset])
            if movement >= 0:
                continue
            for block in scene['blocks']:
                if envelopes is not None:
                    from parkour_solver.reachability.service import block_interaction
                    box = scene['collisionScene']['solids'][block['solidIndex']]
                    if block_interaction(envelopes,box,offset,('Y',))['ruledOut']:
                        continue
                top = block['top']
                planned_floor = any(e['offset'] == offset and e.get('role') in ('landing', 'step')
                                    and e['plane'] == top and e['box'] == scene['collisionScene']['solids'][block['solidIndex']]
                                    for e in profile.get('contactEvents', []))
                if next_y <= top <= y and not planned_floor and not (offset + 1 == profile['airtime'] and top == target_top):
                    checks.append({'leg': leg, 'offset': offset, 'block': copy.deepcopy(block), 'reason': 'earlier_floor'})
        for block in scene['blocks']:
            if envelopes is not None:
                from parkour_solver.reachability.service import block_interaction
                box = scene['collisionScene']['solids'][block['solidIndex']]
                if block_interaction(envelopes,box,0)['ruledOut']:
                    continue
            selected_step = profile.get('profileKind') in ('stair', 'step') and block['id'] == block_ids[leg + 1]
            selected_step |= any(e['input'] == 0 and e['case'] in ('normal_step', 'slab_boost')
                                 and e['box'] == scene['collisionScene']['solids'][block['solidIndex']]
                                 for e in profile.get('caseEvents', []))
            step_config = dict(config, contacts=[{'state': 1, 'plane': block['top'], 'direction': -1, 'step': True}])
            _, _, _, failures = vertical_step(step_config, 0, profile['position'][0], profile['velocity'][0], False, True, 0)
            if not selected_step and not failures:
                checks.append({'leg': leg, 'offset': 1, 'block': copy.deepcopy(block), 'reason': 'unplanned_stair'})
    return checks


def contact_conflicts(checks, plan, candidate):
    conflicts = []
    for check in checks:
        tick = plan['events'][check['leg']]['jump'] + check['offset']
        x, z = candidate['positions'][tick]
        if supports(check['block'], x, z):
            conflicts.append(dict(check, state=tick, position=[x, z]))
    return conflicts


def avoid_contact_rows(conflict, clearance):
    bounds = center_bounds(conflict['block'])
    x, z = conflict['position']
    rows = []
    for axis, coordinate, lo, hi in [('X', x, bounds[0], bounds[1]), ('Z', z, bounds[2], bounds[3])]:
        for cmp, edge in [('LE', lo - clearance), ('GE', hi + clearance)]:
            rows.append((abs(coordinate - edge), {'leg': conflict['leg'], 'offset': conflict['offset'],
                                                 'mode': axis, 'cmp': cmp, 'rhs': edge,
                                                 'name': conflict['reason'] + ':' + conflict['block']['id']}))
    return [row for _, row in sorted(rows, key=lambda value: value[0])]
