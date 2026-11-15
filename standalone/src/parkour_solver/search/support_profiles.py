import copy
import heapq
import math
import time
from collections import deque

from parkour_solver.numerics.model import integer, number
from parkour_solver.search.repair import floor_checks, solve_contact_branches
from parkour_solver.movement.profiles import compile_open_air_route, normal_jump_height, stair_jump_profile
from parkour_solver.scene.geometry import normalize_geometry
from parkour_solver.scene.route import prepare_route
from parkour_solver.search.ranking import candidate_rank, objective_spec


def prepare_scene(request):
    scene = copy.deepcopy(request)
    if not isinstance(scene, dict):
        raise ValueError('Expected a block scene')
    allowed = {'version', 'sceneModel', 'initial', 'blocks', 'startBlock', 'goalBlock', 'inputs',
               'maxJumps', 'maxPrefixes', 'maxQueuedPrefixes', 'maxRunTicksPerJump', 'maxTotalRunTicks',
               'maxFlightInputs', 'maxContactBranches', 'stairBoost', 'supportClearance', 'objective',
               'geometryBounds', 'collisionScene', 'maxBlockagesPerObstacle', 'maxPassageCandidates',
               'maxHorizontalBlockagesPerObstacle', 'maxVerticalBlockagesPerObstacle',
               'maxWallHits', 'maxWallHitCandidates', 'ceilingHits', 'climbableCells', 'ladderSearch',
               'maxContactCombinations', 'maxCombinedEvents', 'maxSteppingProfiles', 'movementSearch'}
    extra = set(scene) - allowed
    if extra:
        raise ValueError('Unsupported block-scene fields: ' + ', '.join(sorted(extra)))
    if scene.get('sceneModel') not in ('coplanar_support_planes', 'support_planes', 'collision_boxes'):
        raise ValueError('Declare a support_planes, coplanar_support_planes or collision_boxes scene')
    scene['objective'] = objective_spec(scene.get('objective'))
    blocks = scene.get('blocks')
    if not isinstance(blocks, list) or not 2 <= len(blocks) <= 64:
        raise ValueError('Supply 2 through 64 candidate supports')
    solid_scene = scene['sceneModel'] == 'collision_boxes'
    if solid_scene:
        integer(scene.setdefault('maxWallHits', 2), 'maxWallHits', 0, 8)
        integer(scene.setdefault('maxWallHitCandidates', 24), 'maxWallHitCandidates', 1, 512)
        integer(scene.setdefault('maxContactCombinations', 64), 'maxContactCombinations', 0, 100000)
        integer(scene.setdefault('maxSteppingProfiles', 128), 'maxSteppingProfiles', 1, 100000)
        integer(scene.setdefault('maxCombinedEvents', 3), 'maxCombinedEvents', 1, 32)
        if type(scene.setdefault('ceilingHits', True)) is not bool:
            raise ValueError('ceilingHits must be boolean')
        scene = normalize_geometry(scene)
        blocks = scene['blocks']
        if len(blocks) > 64:
            raise ValueError('Supply at most 64 collision sub-boxes')
    elif 'collisionScene' in scene or 'geometryBounds' in scene:
        raise ValueError('Full geometry requires sceneModel=collision_boxes')
    ids = []
    for block in blocks:
        fields = {'id', 'bounds', 'top', 'sourceBlock', 'solidIndex'} if solid_scene else {'id', 'bounds', 'top'}
        if not isinstance(block, dict) or set(block) - fields:
            raise ValueError('Each candidate is one rectangular support with id, bounds and top')
        if not isinstance(block.get('id'), str) or not block['id']:
            raise ValueError('Each candidate support needs a nonempty ID')
        ids.append(block['id'])
    if len(set(ids)) != len(ids):
        raise ValueError('Candidate support IDs must be unique')
    if scene.get('startBlock') not in ids or scene.get('goalBlock') not in ids:
        raise ValueError('Start and goal must name candidate support IDs')
    if scene['startBlock'] == scene['goalBlock']:
        raise ValueError('This route search requires different start and goal supports')
    if type(scene.setdefault('stairBoost', True)) is not bool:
        raise ValueError('stairBoost must be boolean')
    if not isinstance(scene.get('inputs', {}), dict):
        raise ValueError('Input policy must be an object')
    scene['inputs'] = dict(scene.get('inputs', {}))
    if set(scene['inputs']) - {'forward', 'strafe', 'sprint', 'incomingSprint', 'force45'}:
        raise ValueError('Unsupported input-policy fields')
    scene['inputs'].setdefault('sprint', True)
    scene['inputs'].setdefault('force45', True)
    if scene['inputs']['sprint'] is not True or scene['inputs']['force45'] is not True:
        raise ValueError('Block search uses sprint jump and sprint 45')
    for key, default, lo, hi in [('maxJumps', 4, 1, 12), ('maxPrefixes', 200, 1, 100000),
                                 ('maxQueuedPrefixes', 4000, 1, 100000),
                                 ('maxRunTicksPerJump', 6, 0, 1000), ('maxTotalRunTicks', 12, 0, 1000),
                                 ('maxFlightInputs', 200, 2, 200), ('maxContactBranches', 16, 1, 256)]:
        integer(scene.setdefault(key, default), key, lo, hi)
    if solid_scene:
        integer(scene.setdefault('maxBlockagesPerObstacle', 3), 'maxBlockagesPerObstacle', 0, 3)
        integer(scene.setdefault('maxHorizontalBlockagesPerObstacle', 2), 'maxHorizontalBlockagesPerObstacle', 0, 2)
        integer(scene.setdefault('maxVerticalBlockagesPerObstacle', 2), 'maxVerticalBlockagesPerObstacle', 0, 2)
        integer(scene.setdefault('maxPassageCandidates', 128), 'maxPassageCandidates', 1, 4096)
    elif any(key in scene for key in ('maxBlockagesPerObstacle', 'maxPassageCandidates',
                                     'maxHorizontalBlockagesPerObstacle', 'maxVerticalBlockagesPerObstacle',
                                     'maxWallHits', 'maxWallHitCandidates', 'ceilingHits',
                                     'maxContactCombinations', 'maxCombinedEvents')):
        raise ValueError('Blockage limits require collision_boxes geometry')
    ordered = [blocks[ids.index(scene['startBlock'])]] + [b for b in blocks if b['id'] != scene['startBlock']]
    checked = prepare_route({'version': scene.get('version'), 'initial': scene.get('initial'),
                             'blocks': ordered, 'airtimes': [12] * (len(blocks) - 1),
                             'inputs': scene['inputs'], 'maxTotalRunTicks': scene['maxTotalRunTicks'],
                             'supportClearance': scene.get('supportClearance', 1e-6)})
    if scene['sceneModel'] == 'coplanar_support_planes' and any(b['top'] != ordered[0]['top'] for b in blocks):
        raise ValueError('Use sceneModel=support_planes for different support heights')
    scene['inputs'] = checked['inputs']
    scene['initial'] = checked['initial']
    if solid_scene and set(scene['initial']) - {'position', 'velocity', 'yaw'}:
        raise ValueError('Collision-box avoidance requires a clear initial input state; additional resume fields need shared movement compilation')
    scene['supportClearance'] = checked['supportClearance']
    if scene.get('climbableCells'):
        if not solid_scene:
            raise ValueError('Climbable cells require collision_boxes')
        from parkour_solver.search.climbable import ladder_settings
        scene['ladderSearch'] = ladder_settings(scene)
    elif 'ladderSearch' in scene:
        raise ValueError('Ladder search needs supplied climbable cells')
    return scene


def minimum_distance(a, b):
    dx = max(0.0, a[0] - b[1], b[0] - a[1])
    dz = max(0.0, a[2] - b[3], b[2] - a[3])
    return math.hypot(dx, dz)


def build_transition_graph(scene, compiler, *, seconds=5):
    scene = prepare_scene(scene)
    if number(seconds, 'graph seconds') <= 0:
        raise ValueError('Graph seconds must be positive')
    deadline = time.monotonic() + seconds

    def remaining():
        value = deadline - time.monotonic()
        if value <= 0:
            raise TimeoutError('Transition graph compilation exceeded its budget')
        return value

    ids = [b['id'] for b in scene['blocks']]
    adjacency = {block: [] for block in ids}
    movement_scene = dict(scene, sharedMovement=True) if scene['sceneModel'] == 'collision_boxes' else scene
    sample = compiler.compile(movement_scene, {'grounded': [True, True, False], 'jump': [False, True, False]},
                              remaining())
    magnitude, friction = sample['magnitude'], sample['friction']
    if len(magnitude) != 3 or len(friction) != 3:
        raise ValueError('Invalid movement-bound coefficients')
    pairs = list(zip(magnitude, friction))
    if any(not math.isfinite(m) or not math.isfinite(f) or m < 0 or not 0 <= f < 1 for m, f in pairs):
        raise ValueError('Graph bounds require finite nonnegative magnitude and drag below one')
    speed = max(math.hypot(scene['initial']['velocity'][0], scene['initial']['velocity'][2]),
                max(f * m / (1 - f) for m, f in pairs))
    speed += 1e-9 * (1 + speed)
    movement_after_support_check = speed + max(m for m, _ in pairs)
    footprints = compiler.derive_footprints(scene, remaining())
    if len(footprints) != len(ids):
        raise ValueError('Invalid support footprints')
    bounds = {}
    for block, footprint in zip(ids, footprints):
        if not isinstance(footprint, list) or len(footprint) != 4:
            raise ValueError('Invalid support footprint')
        for value in footprint:
            number(value, 'support footprint')
        if footprint[0] >= footprint[1] or footprint[2] >= footprint[3]:
            raise ValueError('Empty support footprint')
        bounds[block] = footprint
    coordinate_scale = max(abs(value) for footprint in footprints for value in footprint)
    rejected, profile_cache, flight_bounds, transitions = [], {}, {}, {}
    stepping_generation = []
    profiles = {block: {} for block in ids}
    edge_options = {block: {} for block in ids}
    solid_owners = {b['solidIndex']: b['sourceBlock'] for b in scene['blocks']} \
        if scene['sceneModel'] == 'collision_boxes' else {}
    for source in scene['blocks']:
        for target in scene['blocks']:
            remaining()
            key = (source['top'], target['top'])
            if scene['sceneModel'] != 'collision_boxes' and key[1] - key[0] > normal_jump_height() + 1e-9 * (1 + abs(key[0]) + abs(key[1])):
                rejected.append({'source': source['id'], 'target': target['id'], 'reason': 'jump_height'})
                continue
            if key not in profile_cache:
                from parkour_solver.movement.generation import generate_choices
                normal = list(generate_choices('normal', {'mode': 'support', 'sourceTop': key[0],
                    'targetTop': key[1], 'maxInputs': scene['maxFlightInputs']}))
                ordinary = next((c['profile'] for c in normal if c['profileKind'] == 'ordinary'), None)
                stair = stair_jump_profile(*key) if scene['stairBoost'] and scene['sceneModel'] != 'collision_boxes' else None
                runoff = next((c['profile'] for c in normal if c['profileKind'] == 'run_off'), None)
                profile_cache[key] = [('ordinary', ordinary, None)] if ordinary is not None else []
                if stair is not None:
                    profile_cache[key].extend(('stair', stair, {'axis': axis, 'direction': direction})
                                              for axis in ('X', 'Z') for direction in (-1, 1))
                if runoff is not None:
                    profile_cache[key].extend(('run_off', runoff, {'axis': axis, 'direction': direction})
                                              for axis in ('X', 'Z') for direction in (-1, 1))
            delta = {'X': (target['bounds'][0] + target['bounds'][1] - source['bounds'][0] - source['bounds'][1]) / 2,
                     'Z': (target['bounds'][2] + target['bounds'][3] - source['bounds'][2] - source['bounds'][3]) / 2}
            available_profiles = list(profile_cache[key])
            if scene['sceneModel'] == 'collision_boxes':
                from parkour_solver.movement.generation import generate_choices
                context = dict(scene=scene, source=source, target=target, maxInputs=scene['maxFlightInputs'],
                               checkBudget=remaining, limit=scene['maxSteppingProfiles'])
                available_profiles.extend((c['profileKind'], c['profile'], c['entryFace'])
                                          for c in generate_choices('stepping', context))
                stepping_generation.append(dict(context['generation'], source=source['id'], target=target['id']))
                if scene['ceilingHits']:
                    available_profiles.extend((c['profileKind'], c['profile'], None)
                                              for c in generate_choices('ceiling', context))
            if scene['sceneModel'] == 'collision_boxes' and scene['maxContactCombinations']:
                combination_key = key + ('combinations',)
                if combination_key not in profile_cache:
                    from parkour_solver.movement.contact_profiles import contact_opportunities, generate_contact_profiles
                    horizon = max((p['airtime'] for _, p, _ in profile_cache[key]), default=0)
                    opportunities = [e for e in contact_opportunities(scene['collisionScene']['solids'], horizon,
                        block_ids=[solid_owners[i] for i in range(len(solid_owners))], ceilings=scene['ceilingHits'],
                        horizontal=scene['maxWallHits'] > 0)
                        if e['axis'] == 'Y' or e['box'][4] > min(key)]
                    profile_cache[combination_key] = generate_contact_profiles(*key, opportunities,
                        max_inputs=scene['maxFlightInputs'], max_events=scene['maxCombinedEvents'],
                        limit=scene['maxContactCombinations'], max_horizontal=scene['maxWallHits'])['profiles']
                available_profiles.extend(('contacts', p, None) for p in profile_cache[combination_key])
            options = sorted(available_profiles, key=lambda option:
                             (option[0], (1 if option[0] == 'stair' else -1) *
                              option[2]['direction'] * delta[option[2]['axis']] if option[2] else 0))
            if not options:
                rejected.append({'source': source['id'], 'target': target['id'],
                                 'reason': 'profile_not_generated'})
                continue
            for kind, profile, face in options:
                if 'generatedUnits' not in profile:
                    from parkour_solver.movement.units import profile_program
                    profile = copy.deepcopy(profile)
                    profile['generatedUnits'] = profile_program(profile)
                from parkour_solver.movement.cases import entry_possible
                if not entry_possible(profile, bounds[source['id']]):
                    continue
                n = profile['airtime']
                classic = profile['grounded'][0] and not any(profile['grounded'][1:-1]) and not any(profile['jump'][1:])
                motion_key = (('jump:' if profile['jump'][0] else 'run_off:') + str(n) if classic else
                              ''.join('J' if j else 'G' if g else 'A'
                                      for g, j in zip(profile['grounded'][:-1], profile['jump'])))
                if motion_key not in flight_bounds:
                    flight = compiler.compile(movement_scene, {'grounded': profile['grounded'][:-1],
                                                       'jump': profile['jump']}, remaining())
                    if len(flight['magnitude']) != n or len(flight['friction']) != n:
                        raise ValueError('Invalid flight-bound coefficients')
                    if any(not math.isfinite(m) or not math.isfinite(f) or m < 0 or not 0 <= f < 1
                           or f * m > (1 - f) * speed
                           for m, f in zip(flight['magnitude'], flight['friction'])):
                        raise ValueError('Flight coefficients exceed the movement bound')
                    travel, carry = 0.0, speed
                    for m, f in zip(flight['magnitude'][:-1], flight['friction'][:-1]):
                        move = carry + m
                        travel += move
                        carry = move * f
                    radius = travel + movement_after_support_check
                    padding = 1e-8 * (1 + radius) + 1e-12 * (1 + coordinate_scale) * (n + 1)
                    number(radius + padding, 'horizontal reach bound')
                    flight_bounds[motion_key] = {'travel': travel, 'radius': radius + padding,
                                                 'magnitude': flight['magnitude'], 'friction': flight['friction']}
                radius = flight_bounds[motion_key]['radius']
                gap = minimum_distance(bounds[source['id']], bounds[target['id']])
                if gap > radius:
                    rejected.append({'source': source['id'], 'target': target['id'],
                                     'reason': 'optimistic_horizontal_reach', 'gap': gap,
                                     'radius': radius, 'airtime': n, 'profileKind': kind})
                    continue
                if target['id'] not in edge_options[source['id']]:
                    adjacency[source['id']].append(target['id'])
                    edge_options[source['id']][target['id']] = []
                    profiles[source['id']][target['id']] = profile
                edge_id = 'e' + str(len(transitions))
                transitions[edge_id] = {'source': source['id'], 'target': target['id'], 'profileKind': kind,
                                        'profile': profile, 'entryFace': face if kind == 'stair' else None,
                                        'exitFace': face if kind == 'run_off' else None, 'motionKey': motion_key}
                edge_options[source['id']][target['id']].append(edge_id)
    reverse = {block: [] for block in ids}
    for source, targets in adjacency.items():
        for target in targets:
            reverse[target].append(source)
    distances = {scene['goalBlock']: 0}
    pending = deque([scene['goalBlock']])
    while pending:
        target = pending.popleft()
        for source in reverse[target]:
            if source not in distances:
                distances[source] = distances[target] + 1
                pending.append(source)
    flight_to_goal, pending = {scene['goalBlock']: 0}, [(0, scene['goalBlock'])]
    while pending:
        cost, target = heapq.heappop(pending)
        if cost != flight_to_goal[target]:
            continue
        for source in reverse[target]:
            next_cost = cost + min(transitions[e]['profile']['airtime'] for e in edge_options[source][target])
            if next_cost < flight_to_goal.get(source, math.inf):
                flight_to_goal[source] = next_cost
                heapq.heappush(pending, (next_cost, source))
    return {'adjacency': adjacency, 'distanceToGoal': distances, 'profiles': profiles,
            'transitions': transitions, 'edgeOptions': edge_options,
            'flightInputsToGoal': flight_to_goal, 'flightBounds': flight_bounds,
            'rejectedEdges': rejected,
            'steppingGeneration': stepping_generation,
            'profileLimitReached': any(not g['complete'] for g in stepping_generation) or
                                   any(r['reason'] == 'profile_not_generated' for r in rejected),
            'bound': {'storedSpeedNorm': speed, 'movementAfterSupportCheck': movement_after_support_check,
                      'groundMagnitude': magnitude[0], 'groundFriction': friction[0],
                      'flightTravelToLandingCheck': max((b['travel'] for b in flight_bounds.values()), default=0),
                      'radius': max((b['radius'] for b in flight_bounds.values()), default=0)}}


def compile_block_path(scene, block_ids, transitions=None):
    by_id = {b['id']: b for b in scene['blocks']}
    if not isinstance(block_ids, (list, tuple)) or len(block_ids) < 2 or block_ids[0] != scene['startBlock']:
        raise ValueError('A candidate path starts at the start support and contains at least one jump')
    blocks = []
    for index, block_id in enumerate(block_ids):
        if block_id not in by_id:
            raise ValueError('Unknown support in candidate path')
        block = copy.deepcopy(by_id[block_id])
        block = {key: block[key] for key in ('id', 'bounds', 'top')}
        block['id'] = block_id + '@' + str(index)
        blocks.append(block)
    if transitions is not None:
        if len(transitions) != len(block_ids) - 1:
            raise ValueError('One transition is required per selected edge')
        selected, entries, exits, kinds = [], [], [], []
        for leg, transition in enumerate(transitions):
            if transition['source'] != block_ids[leg] or transition['target'] != block_ids[leg + 1]:
                raise ValueError('Selected transition does not match the support path')
            profile = copy.deepcopy(transition['profile'])
            if scene['sceneModel'] == 'collision_boxes' and transition['profileKind'] in ('stair', 'step') and not profile.get('caseEvents'):
                from parkour_solver.movement.units import profile_program
                profile['caseEvents'] = [dict(type='stepping', case='slab_boost' if transition['profileKind'] == 'stair' else 'normal_step',
                    input=0, plane=blocks[leg + 1]['top'], box=scene['collisionScene']['solids'][by_id[block_ids[leg + 1]]['solidIndex']],
                    anchors={'hit': 1})]
                profile['generatedUnits'] = profile_program(profile)
            profile.update(sourceBlock=blocks[leg]['id'], targetBlock=blocks[leg + 1]['id'],
                           profileKind=transition['profileKind'])
            selected.append(profile)
            entries.append(dict(transition['entryFace'], outsideFrom=0, outsideThrough=0, enter=1)
                           if transition['entryFace'] else None)
            exits.append(transition.get('exitFace'))
            kinds.append('movement' if transition['profileKind'] == 'movement' else 'walk_off' if transition['profileKind'] == 'run_off' else 'step' if transition['profileKind'] == 'step' else 'jump')
        route = prepare_route({'version': 1, 'initial': scene['initial'], 'blocks': blocks,
                               'inputs': scene['inputs'], 'objective': scene['objective'],
                               'airtimes': [p['airtime'] for p in selected],
                               'kinds': kinds, 'landingPhases': ['post' if k == 'step' else p.get('landingPhase', 'pre')
                                                               for p, k in zip(selected, kinds)],
                               'contactEntries': entries, 'walkOffExits': exits,
                               'maxTotalRunTicks': scene['maxTotalRunTicks'],
                               'runBounds': [[0, scene['maxRunTicksPerJump']] for _ in selected],
                               'supportClearance': scene['supportClearance']})
        route['profileDerivation'] = {'kind': 'selected_height_profiles', 'suppliedAirtimes': False,
                                     'verticalModel': 'parkour_solver.physics.vertical', 'solidSideFaces': False}
        if scene['sceneModel'] == 'collision_boxes':
            route['sharedMovement'] = True
            route['ceilingEvents'] = [dict(p['ceilingEvent'], leg=leg) for leg, p in enumerate(selected)
                                      if 'ceilingEvent' in p]
            route['ladderEvents'] = [dict(edge['ladderEvent'], leg=leg) for leg, edge in enumerate(transitions)
                                     if 'ladderEvent' in edge]
            route['ladderEvents'].extend(dict(event, leg=leg) for leg, edge in enumerate(transitions)
                                          for event in edge.get('ladderEvents', []))
            route['verticalProfiles'] = selected
            route['contactEvents'] = [dict(event, leg=leg) for leg, profile in enumerate(selected)
                                      for event in profile.get('contactEvents', [])]
        return {'status': 'derived', 'route': route, 'profiles': selected}
    compiled = compile_open_air_route({'version': 1, 'sceneModel': 'open_air_supports',
                                      'initial': scene['initial'], 'blocks': blocks, 'inputs': scene['inputs'],
                                      'objective': scene['objective'],
                                      'maxTotalRunTicks': scene['maxTotalRunTicks'],
                                      'runBounds': [[0, scene['maxRunTicksPerJump']] for _ in block_ids[1:]],
                                      'supportClearance': scene['supportClearance']},
                                     max_inputs=scene['maxFlightInputs'])
    return compiled


def search_block_paths(scene, graph, evaluate, *, seconds=60, improve=False, cancel_event=None, on_attempt=None):
    scene = prepare_scene(scene)
    if number(seconds, 'search seconds') <= 0:
        raise ValueError('Search seconds must be positive')
    started = time.monotonic()
    deadline = started + seconds
    distances = graph['distanceToGoal']
    by_id = {b['id']: b for b in scene['blocks']}
    goal = by_id[scene['goalBlock']]['bounds']
    pending, attempts, unresolved = [], [], []
    best, termination = None, 'search_exhausted'
    serial = 0
    horizon_prunes, disconnected_prunes, pool_drops = 0, 0, 0
    tick_prunes = 0

    def offer(path, parent_status, parent_cost, selected=()):
        nonlocal serial, horizon_prunes, disconnected_prunes, tick_prunes
        current = path[-1]
        if current not in distances:
            disconnected_prunes += 1
            return
        lower_hops = len(path) - 1 + distances[current]
        if lower_hops > scene['maxJumps']:
            horizon_prunes += 1
            return
        serial += 1
        prefix_inputs = (sum(graph['transitions'][e]['profile']['airtime'] for e in selected) if selected else
                         sum(graph.get('profiles', {}).get(a, {}).get(b, {'airtime': 12})['airtime']
                             for a, b in zip(path, path[1:])))
        lower_inputs = prefix_inputs + graph.get('flightInputsToGoal', {}).get(current, 12 * distances[current])
        if best is not None and lower_inputs > best['plan']['ticks']:
            tick_prunes += 1
            return
        priority = (lower_inputs, lower_hops, parent_status != 'feasible', parent_cost,
                    minimum_distance(by_id[current]['bounds'], goal), serial)
        heapq.heappush(pending, (priority, tuple(path), tuple(selected)))

    offer([scene['startBlock']], 'feasible', 0)
    while pending:
        if cancel_event is not None and cancel_event.is_set():
            termination = 'cancelled'
            break
        if time.monotonic() >= deadline:
            termination = 'deadline'
            break
        if len(attempts) >= scene['maxPrefixes']:
            termination = 'candidate_limit'
            break
        priority, path, selected = heapq.heappop(pending)
        if best is not None and priority[0] > best['plan']['ticks']:
            tick_prunes += 1 + len(pending)
            pending.clear()
            termination = 'tick_bound_exhausted'
            break
        status, cost = 'feasible', 0
        if len(path) > 1:
            available = max(.001, deadline - time.monotonic())
            if selected:
                remaining_flight = graph.get('flightInputsToGoal', {}).get(path[-1], 12 * distances[path[-1]])
                cap = best['plan']['ticks'] - remaining_flight if best is not None else None
                result = evaluate(list(path), available, transition_ids=selected, max_ticks=cap)
            else:
                result = evaluate(list(path), available)
            if not isinstance(result, dict) or result.get('status') not in ('feasible', 'unknown', 'error', 'cancelled'):
                raise ValueError('Invalid path evaluator result')
            status = result['status']
            if status == 'feasible':
                if not isinstance(result.get('best'), dict):
                    raise ValueError('A feasible path needs an accepted candidate')
                cost = integer(result['best'].get('cost'), 'run cost', 0, scene['maxTotalRunTicks'])
            record = {'blockIds': list(path), 'status': status, 'cost': cost if status == 'feasible' else None,
                      'transitionIds': list(selected),
                      'lowerBoundTicks': priority[0],
                      'ticks': result['best']['plan']['ticks'] if status == 'feasible' else None,
                      'objective': result['best']['candidate'].get('objective') if status == 'feasible' else None,
                      'termination': result.get('termination'), 'elapsedSeconds': time.monotonic() - started,
                      'innerProbes': len(result.get('attempts', [])),
                      'contactBranches': len(result.get('contactAttempts', [])), 'error': result.get('error')}
            record['unresolvedAllocations'] = len(result.get('unresolved', [])) + len(result.get('unresolvedEqualCost', []))
            if scene['sceneModel'] == 'collision_boxes':
                contacts = result.get('contactAttempts', [])
                generations = [a['passageGeneration'] for a in contacts if 'passageGeneration' in a]
                record['obstacleSearch'] = {key: sum(a.get(key, 0) for a in generations)
                                           for key in ('generated', 'candidateDrops', 'timingPoolDrops', 'reachPruned', 'verticalFacesPruned', 'budgetPruned')}
                record['obstacleSearch']['generationIncomplete'] = any(a.get('generationIncomplete') for a in generations)
                record['obstacleSearch']['acceptedKinds'] = sorted({kind for a in contacts if a.get('geometryCheck', {}).get('feasible')
                                                                    for kind in a['passageKinds']})
                record['wallHitSearch'] = {'generated': max((a.get('wallHitGeneration', {}).get('generated', 0) for a in contacts), default=0),
                                           'candidateDrops': max((a.get('wallHitGeneration', {}).get('candidateDrops', 0) for a in contacts), default=0),
                                           'testedBranches': sum(a.get('wallHitCount', 0) > 0 for a in contacts)}
            attempts.append(record)
            if on_attempt is not None:
                on_attempt(copy.deepcopy(record))
            if status == 'unknown':
                unresolved.append(record)
            if status in ('error', 'cancelled'):
                termination = status
                break
            if status == 'feasible' and path[-1] == scene['goalBlock']:
                if best is None or candidate_rank(result['best'], scene['objective']) < candidate_rank(best, scene['objective']):
                    best = copy.deepcopy(result['best'])
                    best['blockIds'] = list(path)
                    best['transitionIds'] = list(selected)
        if path[-1] == scene['goalBlock'] or len(path) - 1 >= scene['maxJumps']:
            continue
        for target in graph['adjacency'][path[-1]]:
            options = graph.get('edgeOptions', {}).get(path[-1], {}).get(target)
            if options:
                for edge in options:
                    offer(path + (target,), status, cost, selected + (edge,))
            else:
                offer(path + (target,), status, cost)
        if len(pending) > scene['maxQueuedPrefixes']:
            pool_drops += len(pending) - scene['maxQueuedPrefixes']
            pending = heapq.nsmallest(scene['maxQueuedPrefixes'], pending)
            heapq.heapify(pending)
    initial_tick_bound = graph.get('flightInputsToGoal', {}).get(scene['startBlock'],
                        12 * distances[scene['startBlock']] if scene['startBlock'] in distances else None)
    return {'status': 'feasible' if best is not None else
            ('cancelled' if termination == 'cancelled' else 'error' if termination == 'error' else 'unknown'),
            'best': best, 'termination': termination, 'attempts': attempts, 'unresolvedPrefixes': unresolved,
            'elapsedSeconds': time.monotonic() - started,
            'ticksLowerBound': initial_tick_bound if all(g['complete'] for g in graph.get('steppingGeneration', [])) else None,
            'generatedTicksLowerBound': initial_tick_bound,
            'minimumTicksProven': best is not None and best['plan']['ticks'] == initial_tick_bound and
                                  all(g['complete'] for g in graph.get('steppingGeneration', [])),
            'objectiveOptimalityProven': False, 'objective': scene['objective'],
            'pruning': {'horizontalEdges': sum(r.get('reason') == 'optimistic_horizontal_reach' for r in graph['rejectedEdges']),
                        'heightEdges': sum(r.get('reason') == 'jump_height' for r in graph['rejectedEdges']),
                        'profileEdges': sum(r.get('reason') == 'profile_not_generated' for r in graph['rejectedEdges']),
                        'horizonPrefixes': horizon_prunes, 'tickBoundPrefixes': tick_prunes,
                        'disconnectedPrefixes': disconnected_prunes, 'heuristicPoolDrops': pool_drops},
            'remainingPrefixes': len(pending), 'searchPolicy': 'total_ticks_then_objective'}


def _find_blocks_normal(request, compiler, *, seconds=60, route_seconds=8, candidate_seconds=2, retries=1,
                improve=False, python_executable=None, cancel_event=None, on_attempt=None):
    scene = prepare_scene(request)
    for value, name in [(seconds, 'search seconds'), (route_seconds, 'route seconds'),
                         (candidate_seconds, 'candidate seconds')]:
        if number(value, name) <= 0:
            raise ValueError(name + ' must be positive')
    integer(retries, 'retries', 0, 10)
    started = time.monotonic()
    if cancel_event is not None and cancel_event.is_set():
        return {'status': 'cancelled', 'termination': 'cancelled', 'best': None, 'attempts': [],
                'elapsedSeconds': time.monotonic() - started}
    try:
        graph = build_transition_graph(scene, compiler, seconds=min(seconds, 3 * candidate_seconds))
    except TimeoutError as error:
        return {'status': 'unknown', 'termination': 'graph_timeout', 'error': str(error), 'best': None,
                'attempts': [], 'elapsedSeconds': time.monotonic() - started}
    except (ValueError, RuntimeError, OSError) as error:
        return {'status': 'error', 'termination': 'graph_compilation', 'error': str(error), 'best': None,
                'attempts': [], 'elapsedSeconds': time.monotonic() - started}

    def evaluate(block_ids, available, transition_ids=None, max_ticks=None):
        selected = [graph['transitions'][e] for e in transition_ids] if transition_ids else None
        compiled = compile_block_path(scene, block_ids, transitions=selected)
        if compiled['status'] != 'derived':
            return {'status': 'unknown', 'termination': 'profile_limit'}
        if scene['sceneModel'] == 'collision_boxes':
            compiled['route']['sharedMovement'] = True
            compiled['route']['passageCaps'] = {'horizontal': scene['maxHorizontalBlockagesPerObstacle'],
                                                'vertical': scene['maxVerticalBlockagesPerObstacle'],
                                                'total': scene['maxBlockagesPerObstacle']}
            selected_hits = sum(e['axis'] != 'Y' for e in compiled['route'].get('contactEvents', []))
            if selected_hits > scene['maxWallHits']:
                return {'status': 'unknown', 'termination': 'wall_hit_limit'}
        else:
            selected_hits = 0
        if max_ticks is not None:
            run_cap = max_ticks - sum(compiled['route']['airtimes'])
            if run_cap < 0:
                return {'status': 'unknown', 'termination': 'tick_bound'}
            compiled['route']['maxTotalRunTicks'] = min(compiled['route']['maxTotalRunTicks'], run_cap)
        checks = floor_checks(scene, block_ids, compiled['profiles'])
        goal = block_ids[-1] == scene['goalBlock']
        return solve_contact_branches(compiled['route'], compiled['profiles'], checks, compiler,
                                      seconds=min(route_seconds, available), candidate_seconds=candidate_seconds,
                                      retries=retries, max_branches=scene['maxContactBranches'], improve=goal,
                                      rank_equal_cost=goal, optimize_goal=goal,
                                      python_executable=python_executable, cancel_event=cancel_event,
                                      geometry=scene.get('collisionScene'),
                                      motion_bound=dict(graph['bound'], flightBounds=graph['flightBounds']),
                                      max_blockages=scene.get('maxBlockagesPerObstacle', 3),
                                      max_horizontal=scene.get('maxHorizontalBlockagesPerObstacle', 2),
                                      max_vertical=scene.get('maxVerticalBlockagesPerObstacle', 2),
                                      solid_blocks=[b['sourceBlock'] for b in sorted(scene['blocks'], key=lambda b: b['solidIndex'])]
                                      if scene['sceneModel'] == 'collision_boxes' else None,
                                      max_passage_candidates=scene.get('maxPassageCandidates', 128),
                                      max_wall_hits=max(0, scene.get('maxWallHits', 0) - selected_hits),
                                      max_wall_candidates=scene.get('maxWallHitCandidates', 24))

    remaining = seconds - (time.monotonic() - started)
    result = search_block_paths(scene, graph, evaluate, seconds=max(.001, remaining), improve=improve,
                                cancel_event=cancel_event, on_attempt=on_attempt)
    result.update(elapsedSeconds=time.monotonic() - started, graph=graph, scene=scene,
                  validationLevel='continuous_support_planes_with_floor_checks',
                  assumptions={'solidSideFaces': False, 'ceilings': False, 'stairBoost': scene['stairBoost'], 'runOff': True,
                               'runOffSourceObstacle': 'departure_exit_only_pending_y_obstacles',
                               'inputPolicy': 'sprint_jump_sprint_45', 'facings': 'free',
                               'routeOrderSupplied': False, 'airtimesSupplied': False,
                               'otherFloorContacts': 'checked_at_generated_crossings'})
    if scene['sceneModel'] == 'collision_boxes':
        result['validationLevel'] = 'continuous_profile_with_shared_scene_collision_checks'
        result['assumptions'].update(solidSideFaces=True, runOffSourceObstacle='checked_during_y_overlap',
                                     ceilings=True, ceilingContactProfiles=scene['ceilingHits'],
                                     intentionalWallHits=True, maxBlockagesPerObstacle=scene['maxBlockagesPerObstacle'])
        result['assumptions'].update(maxHorizontalBlockagesPerObstacle=scene['maxHorizontalBlockagesPerObstacle'],
                                     maxVerticalBlockagesPerObstacle=scene['maxVerticalBlockagesPerObstacle'],
                                     blockageCapScope='physical_block_across_selected_route')
    return result


def find_blocks(request, compiler, **options):
    scene = prepare_scene(request)
    if 'movementSearch' in scene:
        from parkour_solver.search.movement import find_movement_blocks
        return find_movement_blocks(scene, compiler, **options)
    for key, default in [('seconds', 60), ('route_seconds', 8), ('candidate_seconds', 2)]:
        if number(options.get(key, default), key) <= 0:
            raise ValueError(key + ' must be positive')
    integer(options.get('retries', 1), 'retries', 0, 10)
    if scene.get('climbableCells'):
        from parkour_solver.search.climbable import find_climbable_blocks
        return find_climbable_blocks(scene, compiler, _find_blocks_normal, **options)
    return _find_blocks_normal(scene, compiler, **options)
