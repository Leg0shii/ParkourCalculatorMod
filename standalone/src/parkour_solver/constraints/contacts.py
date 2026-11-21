"""Physical floor requirements and their alternative constraint rows."""
import copy
from parkour_solver.physics.vertical import f32, vertical_move, vertical_step
from parkour_solver.scene.route import center_bounds
from parkour_solver.spatial.surfaces import scene_geometry


def exposed_tops(scene, block):
    """The block's exact exposed top rectangles in player space (no clearance: a floor check must flag every overlap)."""
    if 'collisionScene' not in scene:
        bounds = center_bounds(block)
        return [[bounds[0], bounds[2], bounds[1], bounds[3]]]
    return [list(r) for option in scene_geometry(scene['collisionScene']).contact_options(
        1, 1, clearance=0., members=[block['solidIndex']]) for r in option.regions]


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
                                    and e['plane'] == top and scene['collisionScene']['solids'][block['solidIndex']] in e['boxes']
                                    for e in profile.get('contactEvents', []))
                if next_y <= top <= y and not planned_floor and not (offset + 1 == profile['airtime'] and top == target_top):
                    checks.extend({'leg': leg, 'offset': offset, 'block': copy.deepcopy(block), 'region': region,
                                   'reason': 'earlier_floor'} for region in exposed_tops(scene, block))
        for block in scene['blocks']:
            if envelopes is not None:
                from parkour_solver.reachability.service import block_interaction
                box = scene['collisionScene']['solids'][block['solidIndex']]
                if block_interaction(envelopes,box,0)['ruledOut']:
                    continue
            selected_step = profile.get('profileKind') in ('stair', 'step') and block['id'] == block_ids[leg + 1]
            selected_step |= any(e['input'] == 0 and e['case'] in ('normal_step', 'slab_boost')
                                 and scene['collisionScene']['solids'][block['solidIndex']] in e['boxes']
                                 for e in profile.get('caseEvents', []))
            step_config = dict(config, contacts=[{'state': 1, 'plane': block['top'], 'direction': -1, 'step': True}])
            _, _, _, failures = vertical_step(step_config, 0, profile['position'][0], profile['velocity'][0], False, True, 0)
            if not selected_step and not failures:
                checks.extend({'leg': leg, 'offset': 1, 'block': copy.deepcopy(block), 'region': region,
                               'reason': 'unplanned_stair'} for region in exposed_tops(scene, block))
    return checks


def walk_off_faces(scene, program, cut, envelopes, landing_boxes):
    """The faces of its launch support a run-off can leave by on its walk-off tick `cut`: the Y sweep of input
    `cut` starts from the state-`cut` X/Z, so the centre must be off the support's exposed top there.

    The support L is the touching component of feet-height exposed tops holding a launch solid (what input
    cut-1 landed on, or the initial floor); other feet-height blocks keep their generic floor checks. Faces are
    ordered by where `landing_boxes` lie on raw footprints (player-space regions of flush blocks overlap):
    beyond one face, that face then the two side faces (toward B first), the opposite face only when all three
    are blocked; diagonal, the two facing faces, the two opposite only when both are blocked; overlapping, all
    four, nearest first. A face is blocked when no free side of the support's cluster at the feet height is
    bounded by it (`SceneGeometry.sides`), and dropped when `envelopes` cannot reach it at state `cut`.

    None leaves the generic floor checks alone: not grounded, a ladder tick, no reachable support, or L's
    regions and the solids beside them at the feet height not covering L's bounding box (leaving the box
    would be stronger than leaving every piece). Else dict(solids, region, options, diagonal); no options
    means the support cannot be left.
    """
    from parkour_solver.reachability.intervals import interaction_reachability
    from parkour_solver.reachability.service import block_interaction
    from parkour_solver.scene.route import supports
    from parkour_solver.spatial.surfaces import free_rectangles
    profile = program['profile']
    if not profile['grounded'][cut] or profile['ladderTicks'][cut]:
        return None
    y0, solids = profile['position'][cut], scene['collisionScene']['solids']
    feet = [b for b in scene['blocks'] if abs(b['top']-y0) < 1e-7
            and not block_interaction(envelopes, solids[b['solidIndex']], cut, ('Y',))['ruledOut']]
    regions = [(b, r) for b in feet for r in exposed_tops(scene, b)]
    parent = list(range(len(regions)))
    def root(i):
        while parent[i] != i:
            i = parent[i]
        return i
    for i, (_, a) in enumerate(regions):
        for j, (_, b) in enumerate(regions[:i]):
            dx, dz = min(a[2], b[2])-max(a[0], b[0]), min(a[3], b[3])-max(a[1], b[1])
            if dx >= -1e-9 and dz >= -1e-9 and max(dx, dz) > 1e-9:
                parent[root(i)] = root(j)
    groups = {}
    for i in range(len(regions)):
        groups.setdefault(root(i), []).append(i)
    if cut:
        launch = {i for e in profile['contactEvents'] if e['offset'] == cut-1 and e.get('role') in ('landing', 'step')
                  for i, box in enumerate(solids) if box in e.get('boxes', ())}
    else:
        x, _, z = scene['initial']['position']
        launch = {b['solidIndex'] for b in feet if supports(b, x, z)}
    # An unidentified launch can only be the one reachable component; an unreachable one is already left.
    held = ([g for g in groups.values() if any(regions[i][0]['solidIndex'] in launch for i in g)] if launch
            else list(groups.values()))
    if len(held) != 1:
        return None
    pieces = [regions[i][1] for i in held[0]]
    blocks = {regions[i][0]['id']: regions[i][0] for i in held[0]}
    region = [min(r[0] for r in pieces), min(r[1] for r in pieces), max(r[2] for r in pieces), max(r[3] for r in pieces)]
    geometry = scene_geometry(scene['collisionScene'])
    # Space solids fill at the feet height (a wall clipping the top) may lie in the box: no one stands there.
    filled = [geometry.boxes[i] for i in geometry.near(region) if geometry.boxes[i][1] < y0 < geometry.boxes[i][4]+1e-6]
    if any(min(f[2], region[2])-max(f[0], region[0]) > 1e-9 and min(f[3], region[3])-max(f[1], region[1]) > 1e-9
           for f in free_rectangles([(r[0], 0., r[1], r[2], 1., r[3]) for r in pieces]+filled)):
        return None
    members = {b['solidIndex'] for b in blocks.values()}
    midpoint = [sum(envelopes['states'][cut][axis])/2 for axis in ('X', 'Z')]
    rows = avoid_contact_rows(dict(leg=0, offset=cut, reason='walk_off', block=dict(id='+'.join(sorted(blocks))),
                                   region=region, position=midpoint), scene['supportClearance'])
    face = {(row['mode'], 1 if row['cmp'] == 'GE' else -1): row for row in rows}
    raw = [solids[i] for i in members]
    low = [min(box[0] for box in raw), min(box[2] for box in raw)]
    high = [max(box[3] for box in raw), max(box[5] for box in raw)]
    beyond, displacement = [0, 0], [0., 0.]
    for k in (0, 1):
        if landing_boxes:
            lo, hi = min(box[2*k] for box in landing_boxes), max(box[2*k+3] for box in landing_boxes)
            beyond[k] = 1 if lo >= high[k] else -1 if hi <= low[k] else 0
            displacement[k] = (lo+hi)/2-(low[k]+high[k])/2
    if beyond == [0, 0]:
        sets = [list(face)]
    elif 0 in beyond:
        k = 0 if beyond[0] else 1
        toward = 1 if displacement[1-k] > 0 else -1
        sets = [[('XZ'[k], beyond[k]), ('XZ'[1-k], toward), ('XZ'[1-k], -toward)], [('XZ'[k], -beyond[k])]]
    else:
        order = (0, 1) if abs(displacement[0]) >= abs(displacement[1]) else (1, 0)
        sets = [[('XZ'[k], beyond[k]) for k in order], [('XZ'[k], -beyond[k]) for k in order]]
    cluster = next(c for c in geometry.clusters(range(len(solids))) if members & set(c.members))
    edge = {('X', -1): region[0], ('X', 1): region[2], ('Z', -1): region[1], ('Z', 1): region[3]}
    open_faces = {(axis, direction) for side in geometry.sides(cluster, geometry.active(cluster, y0-1e-6, y0))
                  for (axis, direction, plane), owners in zip(side.bounds, side.owners)
                  if abs(plane-edge[axis, direction]) < 1e-9 and members & set(owners)}
    chosen = next(([f for f in faces if f in open_faces] for faces in sets if any(f in open_faces for f in faces)), [])
    return dict(solids=sorted(members), region=region, diagonal=0 not in beyond,
                options=[face[f] for f in chosen if not interaction_reachability(envelopes, [face[f]])['ruledOut']])


def contact_conflicts(checks, plan, candidate):
    conflicts = []
    for check in checks:
        tick = plan['events'][check['leg']]['jump'] + check['offset']
        x, z = candidate['positions'][tick]
        region = check['region']
        if region[0] < x < region[2] and region[1] < z < region[3]:
            conflicts.append(dict(check, state=tick, position=[x, z]))
    return conflicts


def avoid_contact_rows(conflict, clearance):
    region = conflict['region']
    x, z = conflict['position']
    rows = []
    for axis, coordinate, lo, hi in [('X', x, region[0], region[2]), ('Z', z, region[1], region[3])]:
        for cmp, edge in [('LE', lo - clearance), ('GE', hi + clearance)]:
            rows.append((abs(coordinate - edge), {'leg': conflict['leg'], 'offset': conflict['offset'],
                                                 'mode': axis, 'cmp': cmp, 'rhs': edge,
                                                 'name': conflict['reason'] + ':' + conflict['block']['id']}))
    return [row for _, row in sorted(rows, key=lambda value: value[0])]
