from parkour_solver.movement.ladder.settings import ladder_settings
import copy
import heapq
import json
import math
import time

from parkour_solver.physics.vertical import f32
from parkour_solver.schema import integer
from parkour_solver.search.repair import solve_contact_branches
from parkour_solver.constraints.contacts import floor_checks; from parkour_solver.validation.vertical import validate_vertical_schedule
from parkour_solver.scene.ladders import movement_contexts, exit_faces, ladder_regions, region_axis, schedule_constraints
from parkour_solver.search.ladder_ranking import rank_ladder_schedules
from parkour_solver.movement.ladder.schedules import generate_ladder_schedules
from parkour_solver.scene.route import center_bounds, schedule


def selected_edges(graph, edges):
    return [edge if isinstance(edge, dict) else graph['transitions'][edge] for edge in edges]


def ordinary_paths(graph, start, hops, limit, goal=None):
    pending, result, serial = [(0, 0, 0, (start,), ())], [], 0
    while pending and len(result) < limit:
        cost, contact_count, _, path, edges = heapq.heappop(pending)
        if goal is None or path[-1] == goal:
            result.append((path, edges))
        if len(edges) >= hops or goal is not None and path[-1] == goal:
            continue
        for target in graph['adjacency'].get(path[-1], []):
            for edge in graph['edgeOptions'][path[-1]][target]:
                serial += 1
                heapq.heappush(pending, (cost + graph['transitions'][edge]['profile']['airtime'],
                                        contact_count + len(graph['transitions'][edge]['profile'].get('contactEvents', [])), serial,
                                        path + (target,), edges + (edge,)))
    return result


def run_allocations(legs, per_leg, total, limit):
    def at_cost(left, count):
        if count == 0:
            if left == 0:
                yield ()
            return
        for value in range(min(per_leg, left) + 1):
            for tail in at_cost(left - value, count - 1):
                yield (value,) + tail
    emitted = 0
    for cost in range(min(total, legs * per_leg) + 1):
        for allocation in at_cost(cost, legs):
            yield list(allocation)
            emitted += 1
            if emitted >= limit:
                return


def _prefix(scene, graph, path, edges, target, allocation, compiler, seconds):
    from parkour_solver.search.support_profiles import compile_block_path
    source = next(b for b in scene['blocks'] if b['id'] == path[-1])
    dummy = {'airtime': 2, 'contacts': [], 'position': [source['top']] * 3,
             'velocity': [-(.08) * f32(.98)] * 3, 'grounded': [True, False, True], 'jump': [True, False]}
    selected = selected_edges(graph, edges) + [
        {'source': path[-1], 'target': target, 'profileKind': 'ordinary', 'profile': dummy, 'entryFace': None, 'exitFace': None}]
    compiled = compile_block_path(scene, list(path) + [target], transitions=selected)
    route = compiled['route']
    route['sharedMovement'] = True
    footprints = compiler.derive_footprints(route, seconds)
    for block, bounds in zip(route['blocks'], footprints):
        block['_derivedFootprint'] = bounds
    plan = schedule(route, allocation, 'run')
    if plan['ticks']:
        vertical = validate_vertical_schedule(route, compiled['profiles'], plan)['vertical']
    else:
        vertical = {'profile': 'minecraft-1.8.9', 'position': scene['initial']['position'][1],
                    'velocity': scene['initial']['velocity'][1], 'onGround': True,
                    'previousHorizontalCollision': False, 'threshold': .005,
                    'jumpVelocity': [], 'sneak': [], 'contacts': []}
    return route, plan, vertical


def entry_context(scene, graph, path, edges, target, allocation, compiler, seconds):
    route, base, vertical = _prefix(scene, graph, path, edges, target, allocation, compiler, seconds)
    return {'scene': scene, 'route': route, 'base': base, 'vertical': vertical}


def probe_entries(scene, graph, path, edges, target, allocation, column, wall, compiler, *,
                  deadline, candidate_seconds, max_entry, python_executable=None, cancel_event=None, context=None):
    from parkour_solver.search.entry import probe_region_entries
    context = context if context is not None else entry_context(scene, graph, path, edges, target, allocation, compiler,
                            max(.01, deadline - time.monotonic()))
    return probe_region_entries(context, column, wall, compiler, deadline=deadline,
        candidate_seconds=candidate_seconds, max_entry=max_entry,
        python_executable=python_executable, cancel_event=cancel_event)


def post_exit_reachable(schedule, coefficients, column, target):
    reach, carry = 0., float(f32(.15))
    for t in range(schedule['exitState'], schedule['ticks']):
        move = carry + coefficients['magnitude'][t]
        reach += move
        carry = coefficients['friction'][t] * move
    bounds = center_bounds(target)
    for a, origin in [(0, column['x']), (2, column['z'])]:
        hi = region_axis(column, 'X' if a == 0 else 'Z')[1]
        if max(0., bounds[a] - (hi + f32(.15)), origin - f32(.15) - bounds[a + 1]) > reach + 1e-6:
            return False
    return True


def first_hit_bounds(scene, graph, source, prefix_edges, allocation, entries, column, wall, compiler, cfg, deadline):
    from parkour_solver.reachability.screen import contradiction
    from parkour_solver.scene.ladders import outside_row
    if wall.get('mode') == 'fall_only':
        return
    for entry in entries:
        if 'firstValidatedState' not in entry or 'firstHitLowerBounds' in entry:
            continue
        entry['firstHitLowerBounds'] = {}
        for state in range(entry['firstValidatedState'], min(cfg['maxInputs'], entry['firstValidatedState'] + cfg['entryDelay'] + 1)):
            if time.monotonic() + .05 >= deadline:
                return
            n = cfg['maxInputs']
            plan = {'grounded': [True] + [False] * (n - 1), 'jump': [True] + [False] * (n - 1)}
            coefficient = compiler.compile({'sharedMovement': True, 'inputs': scene['inputs']}, plan,
                                            max(.01, deadline - time.monotonic()))
            rows = [outside_row(entry, t, 1e-6, 'entry_bound') for t in range(state)]
            for axis, value in [('X', column['x']), ('Z', column['z'])]:
                rows.extend([{'mode': axis, 't1': state, 'cmp': 'GE', 'rhs': value},
                             {'mode': axis, 't1': state, 'cmp': 'LE', 'rhs': region_axis(column, axis)[1]}])
            lower = n
            for tick in range(max(0, state - 1), n):
                problem = dict(coefficient, ticks=tick + 1, position=scene['initial']['position'][::2],
                    velocity=scene['initial']['velocity'][::2], walls=rows, ladderTicks=[t >= state for t in range(n + 1)],
                    contacts=[{'state': tick + 1, 'axis': 0 if wall['axis'] == 'X' else 1,
                               'plane': wall['plane'], 'direction': wall['direction']}])
                if prefix_edges or allocation[len(prefix_edges)]:
                    bounds, pad, speed = center_bounds(source), graph['bound']['movementAfterSupportCheck'], graph['bound']['storedSpeedNorm']
                    problem['initialIntervals'] = ([bounds[0] - pad, bounds[2] - pad], [bounds[1] + pad, bounds[3] + pad],
                                                  [-speed] * 2, [speed] * 2)
                if contradiction(problem) is None:
                    lower = tick
                    break
            entry['firstHitLowerBounds'][str(state)] = lower


def find_climbable_blocks(scene, compiler, normal_solver, *, seconds=60, route_seconds=8, candidate_seconds=2,
                         retries=1, improve=False, python_executable=None, cancel_event=None, on_attempt=None):
    from parkour_solver.search.support_profiles import build_transition_graph, compile_block_path
    started, cfg = time.monotonic(), ladder_settings(scene)
    deadline = started + seconds
    base = normal_solver(scene, compiler, seconds=min(seconds * .2, route_seconds), route_seconds=route_seconds,
        candidate_seconds=candidate_seconds, retries=retries, python_executable=python_executable,
        cancel_event=cancel_event) if not cfg['requireLadder'] else None
    try:
        graph = base['graph'] if base is not None and 'graph' in base else build_transition_graph(scene, compiler, seconds=max(.01, deadline - time.monotonic()))
    except TimeoutError as error:
        return {'status': 'unknown', 'best': None, 'termination': 'graph_timeout', 'error': str(error), 'attempts': []}
    best, attempts, cache, contexts = base.get('best') if base else None, [], {}, 0
    blocks = {b['id']: b for b in scene['blocks']}
    prefixes = ordinary_paths(graph, scene['startBlock'], scene['maxJumps'] - 1, cfg['maxOrdinaryPaths'])
    pending, prefix_serial, seen_prefixes, prefix_drops = [], 0, set(), 0
    for path, edges in prefixes:
        cost = sum(e['profile']['airtime'] for e in selected_edges(graph, edges))
        heapq.heappush(pending, (cost, prefix_serial, path, edges, None))
        prefix_serial += 1
    termination = 'search_exhausted'
    while pending and len(seen_prefixes) < scene['maxPrefixes']:
        _, _, path, prefix_edges, fixed_runs = heapq.heappop(pending)
        signature = json.dumps([path, selected_edges(graph, prefix_edges), fixed_runs], sort_keys=True)
        if signature in seen_prefixes:
            continue
        seen_prefixes.add(signature)
        used_columns = {event['column']['id'] for edge in selected_edges(graph, prefix_edges)
                        for event in ([edge['ladderEvent']] if 'ladderEvent' in edge else edge.get('ladderEvents', []))}
        node_contexts = 0
        for column in ladder_regions(scene['climbableCells']):
            if column['id'] in used_columns or len(used_columns) >= cfg['maxInteractions']:
                continue
            for wall in sorted(movement_contexts(column, scene['collisionScene']['solids'], include_fall=cfg['minClimbInputs'] == 0),
                               key=lambda w: (-max((p['box'][4] for p in w['parts']), default=-float('inf')), w['direction'] * w['plane'])):
                owners = {b['id'] for b in scene['blocks'] if any(b['solidIndex'] == p['solidIndex'] for p in wall['parts'])}
                targets = sorted(blocks, key=lambda name: (name != scene['goalBlock'] if wall.get('mode') == 'fall_only' else name not in owners, math.hypot((blocks[name]['bounds'][0] + blocks[name]['bounds'][1]) / 2 - column['x'],
                                                                    (blocks[name]['bounds'][2] + blocks[name]['bounds'][3]) / 2 - column['z'])))
                for target in targets:
                    from parkour_solver.movement.profiles import normal_jump_height
                    source_top = blocks[path[-1]]['top']
                    if wall['parts'] and max(p['box'][4] for p in wall['parts']) <= source_top \
                            and blocks[target]['top'] > source_top + normal_jump_height():
                        continue
                    suffixes = ordinary_paths(graph, target, scene['maxJumps'] - len(prefix_edges) - 1,
                                               cfg['maxSuffixPaths'], scene['goalBlock'])
                    if target != scene['goalBlock'] and len(used_columns) + 1 < cfg['maxInteractions'] \
                            and (len(prefix_edges) + 1 < scene['maxJumps'] or cfg['maxTransferPrograms'] and len(used_columns) + 2 <= cfg['maxInteractions']) and any(
                                c['id'] != column['id'] and c['id'] not in used_columns for c in ladder_regions(scene['climbableCells'])):
                        suffixes = [((target,), ())] + suffixes
                    for suffix_path, suffix_edges in suffixes:
                        allocations = run_allocations(len(prefix_edges) + 1 + len(suffix_edges), scene['maxRunTicksPerJump'],
                                                      scene['maxTotalRunTicks'], cfg['maxRunAllocations']) if fixed_runs is None else (
                            fixed_runs + tail for tail in run_allocations(1 + len(suffix_edges), scene['maxRunTicksPerJump'],
                                scene['maxTotalRunTicks'] - sum(fixed_runs), cfg['maxRunAllocations']))
                        for allocation in allocations:
                            if time.monotonic() >= deadline or node_contexts >= cfg['maxRouteContexts'] \
                                    or cancel_event is not None and cancel_event.is_set():
                                termination = 'deadline' if time.monotonic() >= deadline else 'cancelled' if cancel_event is not None and cancel_event.is_set() else 'context_limit'
                                break
                            contexts += 1
                            node_contexts += 1
                            probe_key = (json.dumps(selected_edges(graph, prefix_edges), sort_keys=True), tuple(allocation[:len(prefix_edges) + 1]), column['id'],
                                         wall['axis'], wall['direction'], wall['plane'],
                                         tuple(tuple(p['box']) for p in wall['parts']))
                            if probe_key not in cache:
                                try:
                                    root_context = entry_context(scene, graph, path, prefix_edges, target,
                                        allocation[:len(prefix_edges) + 1], compiler, max(.01, deadline - time.monotonic()))
                                    cache[probe_key] = (root_context, probe_entries(scene, graph, path, prefix_edges, target,
                                        allocation[:len(prefix_edges) + 1], column, wall, compiler, deadline=deadline,
                                        candidate_seconds=min(candidate_seconds, .5), max_entry=min(cfg['maxEntryInputs'], cfg['maxInputs'] - 1),
                                        python_executable=python_executable, cancel_event=cancel_event, context=root_context))
                                except TimeoutError:
                                    termination = 'deadline'
                                    break
                            root_context, (all_entries, probes) = cache[probe_key]
                            active_entries = [e for e in all_entries if 'firstValidatedState' in e] or all_entries
                            entries = active_entries[:64]
                            record = {'prefix': list(path), 'target': target, 'suffix': list(suffix_path), 'allocation': allocation,
                                      'column': column['id'], 'entryProbes': probes, 'deferredEntryWindows': [e for e in all_entries if e not in entries], 'entryWindowDrops': max(0, len(active_entries) - 64), 'status': 'unknown', 'numericalAttempts': []}
                            attempts.append(record)
                            if time.monotonic() + .05 >= deadline:
                                termination = 'deadline'
                                break
                            if not entries:
                                continue
                            source = blocks[path[-1]]
                            first_hit_bounds(scene, graph, source, prefix_edges, allocation, entries, column, wall, compiler, cfg, deadline)
                            if time.monotonic() + .05 >= deadline:
                                termination = 'deadline'
                                break
                            pool = generate_ladder_schedules({'version': 1,
                                'initial': {'position': source['top'], 'velocity': -.08 * f32(.98), 'onGround': True},
                                'region': {k: column[k] for k in ('minY', 'maxY')}, 'allowPrimaryClimb': wall.get('mode') != 'fall_only',
                                'entries': [{k: e[k] for k in ('family', 'channel', 'firstValidatedState', 'firstCandidateState', 'unresolvedStates', 'firstHitLowerBounds', 'contactEvents') if k in e} for e in entries],
                                'targetTop': blocks[target]['top'], 'jumpAtStart': True,
                                'maxInputs': min(cfg['maxInputs'], 200), 'entryDelay': cfg['entryDelay'],
                                'maxSkeletons': cfg['maxSkeletons'], 'maxSchedules': cfg['maxSchedules'], 'maxMaskWork': cfg['maxMaskWork']},
                                cancel_event=cancel_event, deadline=deadline)
                            record['generation'] = {k: v for k, v in pool.items() if k not in ('schedules', 'timingGroups')}
                            from parkour_solver.movement.ladder.contacts import mixed_ladder_schedules
                            extensions = mixed_ladder_schedules([s for s in pool['schedules'] if sum(s['hit']) >= cfg['minClimbInputs']], scene['collisionScene']['solids'],
                                column, wall, blocks[target]['top'], max_variants=cfg['maxContactVariants'],
                                max_events=cfg['maxMixedContacts'], max_inputs=cfg['maxInputs'], cancel_event=cancel_event)
                            record['mixedContacts'] = {k: v for k, v in extensions.items() if k != 'schedules'}
                            pool['schedules'].extend(extensions['schedules'])
                            if cfg['maxTransferPrograms'] and len(used_columns) + 2 <= cfg['maxInteractions'] and not suffix_edges:
                                request = {'version': 1,
                                    'initial': {'position': source['top'], 'velocity': -.08 * f32(.98), 'onGround': True},
                                    'region': {k: column[k] for k in ('minY', 'maxY')}, 'allowPrimaryClimb': wall.get('mode') != 'fall_only',
                                    'entries': [{k: e[k] for k in ('family', 'channel', 'firstValidatedState', 'firstCandidateState', 'unresolvedStates', 'firstHitLowerBounds', 'contactEvents') if k in e} for e in entries],
                                    'finishAtExit': True, 'jumpAtStart': True, 'maxInputs': min(cfg['maxInputs'], 200),
                                    'entryDelay': cfg['entryDelay'], 'maxSkeletons': cfg['maxSkeletons'],
                                    'maxSchedules': cfg['maxSchedules'], 'maxMaskWork': cfg['maxMaskWork']}
                                airborne = generate_ladder_schedules(request, cancel_event=cancel_event, deadline=deadline)
                                record['airborneGeneration'] = {k: v for k, v in airborne.items() if k not in ('schedules', 'timingGroups')}
                                from parkour_solver.movement.contacts import mixed_schedule_pool
                                mixed_airborne = mixed_schedule_pool(request, scene['collisionScene']['solids'],
                                    schedules=airborne['schedules'], max_variants=cfg['maxContactVariants'],
                                    max_events=cfg['maxMixedContacts'], deadline=deadline, cancel_event=cancel_event)
                                record['airborneMixedContacts'] = {k: v for k, v in mixed_airborne.items() if k != 'schedules'}
                                pool['schedules'].extend(airborne['schedules'])
                                pool['schedules'].extend(mixed_airborne['schedules'])
                            candidates = [s for s in pool['schedules'] if sum(s['hit']) >= cfg['minClimbInputs']]
                            def screen_candidate(s, coefficients):
                                from parkour_solver.reachability.screen import contradiction
                                channel = next(e for e in entries if e['channel'] == s['entryChannel'])
                                try:
                                    rows, contacts, _ = schedule_constraints(s, column, wall, channel, None)
                                except ValueError:
                                    return {'reason': 'finite_climb_wall'}
                                if s.get('terminal') != 'airborne' and not post_exit_reachable(s, coefficients, column, blocks[target]):
                                    return {'reason': 'post_exit_reach'}
                                problem = dict(coefficients, ticks=s['ticks'], position=scene['initial']['position'][::2],
                                    velocity=scene['initial']['velocity'][::2], walls=rows, contacts=contacts,
                                    ladderTicks=s['ladderTicks'])
                                if prefix_edges or allocation[len(prefix_edges)]:
                                    bounds, pad = center_bounds(source), graph['bound']['movementAfterSupportCheck']
                                    speed = graph['bound']['storedSpeedNorm']
                                    problem['initialIntervals'] = ([bounds[0] - pad, bounds[2] - pad],
                                        [bounds[1] + pad, bounds[3] + pad], [-speed] * 2, [speed] * 2)
                                return contradiction(problem)
                            ranking = rank_ladder_schedules(candidates, compiler, seconds=max(.01, min(5, deadline - time.monotonic())),
                                max_ranked=cfg['maxRanked'], representatives=cfg['representatives'], inputs=scene['inputs'],
                                cancel_event=cancel_event, screen=screen_candidate)
                            record['ranking'] = {k: v for k, v in ranking.items() if k not in ('ranked', 'backups')}
                            context_deadline = min(deadline, time.monotonic() + route_seconds)
                            for ranked in (ranking['ranked'] + ranking['backups'])[:cfg['maxNumericalSchedules']]:
                                s = ranked['schedule']
                                prefix_cost = sum(e['profile']['airtime'] for e in selected_edges(graph, prefix_edges + suffix_edges)) + sum(allocation)
                                minimum_duration = s['exitState'] + 1 if cfg['maxTransferPrograms'] and len(used_columns) + 2 <= cfg['maxInteractions'] else s['ticks']
                                if best is not None and minimum_duration + prefix_cost > best['plan']['ticks']:
                                    continue
                                if time.monotonic() >= context_deadline:
                                    break
                                channel = next(e for e in entries if e['channel'] == s['entryChannel'])
                                coefficient = ranked['coefficients']
                                if s.get('terminal') != 'airborne' and not post_exit_reachable(s, coefficient, column, blocks[target]):
                                    record.setdefault('postExitPruned', 0)
                                    record['postExitPruned'] += 1
                                    continue
                                target_point = [(blocks[target]['bounds'][a] + blocks[target]['bounds'][a + 1]) / 2 for a in (0, 2)]
                                faces = exit_faces(column, target_point)
                                if s['exitKind'] == 'top' and all(y >= column['maxY'] for y in s['trace']['position'][s['exitState']:]):
                                    faces = faces[:1]
                                for face in faces:
                                    if time.monotonic() >= context_deadline:
                                        break
                                    profile = {'airtime': s['ticks'], 'landingPhase': 'pre', 'position': s['trace']['position'],
                                        'velocity': s['trace']['velocity'], 'grounded': s['trace']['onGround'],
                                        'contacts': s['trace']['vertical']['contacts'], 'jump': [v is not None for v in s['trace']['vertical']['jumpVelocity']],
                                        'sneak': s['sneak'], 'ladderTicks': s['ladderTicks'],
                                        'contactEvents': s.get('contactEvents', [])}
                                    selected = {'source': path[-1], 'target': target, 'profileKind': 'ladder', 'profile': profile,
                                        'entryFace': None, 'exitFace': None,
                                        'ladderEvent': {'schedule': s, 'column': column, 'wall': wall, 'entryFace': channel, 'exitFace': face}}
                                    def variations():
                                        if s.get('terminal') != 'airborne':
                                            yield selected
                                        from parkour_solver.movement.contacts import contact_signature
                                        transfer_key = [s['exitState'], contact_signature(s.get('contactEvents', []))]
                                        exits = {key[0] for key in record.get('transferWindows', [])}
                                        if not cfg['maxTransferPrograms'] or len(used_columns) + 2 > cfg['maxInteractions'] \
                                                or suffix_edges or transfer_key in record.get('transferWindows', []) \
                                                or s['exitState'] not in exits and len(exits) >= cfg['maxTransferWindows']:
                                            return
                                        record.setdefault('transferWindows', []).append(transfer_key)
                                        from parkour_solver.movement.ladder.transfers import iter_chain_programs
                                        report = {}
                                        record.setdefault('transferGeneration', []).append(report)
                                        regions = [c for c in ladder_regions(scene['climbableCells']) if c['id'] not in used_columns]
                                        def entry_probe(prefix, column, wall, **options):
                                            from parkour_solver.search.entry import probe_region_entries
                                            return probe_region_entries(root_context, column, wall, compiler, prefix=prefix,
                                                candidate_seconds=min(candidate_seconds, .5),
                                                python_executable=python_executable, cancel_event=cancel_event, **options)
                                        for p in iter_chain_programs(selected['ladderEvent'], regions, scene['collisionScene']['solids'],
                                                blocks[scene['goalBlock']], max_inputs=cfg['maxInputs'], max_entry=cfg['maxEntryInputs'],
                                                max_programs=cfg['maxTransferPrograms'], max_prefixes=cfg['maxTransferPrefixes'],
                                                max_pending=cfg['maxTransferQueuedPrefixes'],
                                                max_contact_variants=cfg['maxContactVariants'], max_events=cfg['maxMixedContacts'],
                                                min_climb_inputs=cfg['minClimbInputs'],
                                                max_interactions=cfg['maxInteractions'] - len(used_columns),
                                                entry_probe=entry_probe, entry_delay=cfg['entryDelay'],
                                                deadline=context_deadline, cancel_event=cancel_event, report=report):
                                            yield {'source': path[-1], 'target': scene['goalBlock'],
                                                   'profileKind': 'ladder', 'profile': p['profile'], 'ladderEvents': p['ladderEvents'],
                                                   'entryFace': None, 'exitFace': None}
                                    for variation in variations():
                                        if time.monotonic() >= context_deadline:
                                            break
                                        edges = selected_edges(graph, prefix_edges) + [variation] + selected_edges(graph, suffix_edges)
                                        full_path = list(path) + [variation['target']] + list(suffix_path[1:])
                                        compiled = compile_block_path(scene, full_path, transitions=edges)
                                        route = compiled['route']
                                        route['runBounds'] = [[v, v] for v in allocation]
                                        route['maxTotalRunTicks'] = sum(allocation)
                                        try:
                                            solved = solve_contact_branches(route, compiled['profiles'], floor_checks(scene, full_path, compiled['profiles']),
                                                compiler, seconds=max(.001, context_deadline - time.monotonic()), candidate_seconds=candidate_seconds,
                                                retries=retries, rank_equal_cost=True, optimize_goal=True,
                                                python_executable=python_executable, cancel_event=cancel_event, geometry=scene['collisionScene'],
                                                motion_bound=graph['bound'], solid_blocks=[b['sourceBlock'] for b in sorted(scene['blocks'], key=lambda b: b['solidIndex'])])
                                        except (ValueError, TimeoutError) as error:
                                            record['numericalAttempts'].append({'status': 'unknown', 'reason': str(error)})
                                            continue
                                        record['numericalAttempts'].append({'status': solved['status'], 'ticks': sum(route['airtimes']) + sum(allocation),
                                            'ladderInteractions': len(route['ladderEvents']),
                                            'entryState': s['entryState'], 'entryChannel': s['entryChannel'], 'exitKind': s['exitKind'],
                                            'exitFace': face, 'score': ranked['score'], 'contactAttempts': solved.get('contactAttempts', []),
                                            'solverAttempts': solved.get('attempts', [])})
                                        if solved.get('best') is not None:
                                            candidate = solved['best']
                                            candidate.update(blockIds=full_path, ladderSchedule=variation['ladderEvent']['schedule'] if 'ladderEvent' in variation else variation['ladderEvents'][-1]['schedule'], ladderColumn=column)
                                            sense = -1 if scene['objective']['sense'] == 'MAX' else 1
                                            candidate['ladderSchedules'] = [copy.deepcopy(e['schedule']) for e in candidate['route']['ladderEvents']]
                                            if full_path[-1] == scene['goalBlock']:
                                                if best is None or (candidate['plan']['ticks'], sense * candidate['candidate']['objective']) < (best['plan']['ticks'], sense * best['candidate']['objective']):
                                                    best = candidate
                                            elif len(prefix_edges) + 1 < scene['maxJumps']:
                                                for ordinary_path, ordinary_edges in ordinary_paths(graph, target,
                                                        scene['maxJumps'] - len(edges) - 1, cfg['maxOrdinaryPaths']):
                                                    next_path = tuple(full_path) + ordinary_path[1:]
                                                    next_edges = tuple(edges) + ordinary_edges
                                                    runs = list(allocation) + [0] * len(ordinary_edges)
                                                    cost = sum(e['profile']['airtime'] for e in selected_edges(graph, next_edges)) + sum(runs)
                                                    heapq.heappush(pending, (cost, prefix_serial, next_path, next_edges, runs))
                                                    prefix_serial += 1
                                                record['prefixOffers'] = record.get('prefixOffers', 0) + 1
                                            record['status'] = 'feasible'
                            if on_attempt is not None:
                                on_attempt(copy.deepcopy(record))
                        if termination != 'search_exhausted':
                            break
                    if termination != 'search_exhausted':
                        break
                if termination != 'search_exhausted':
                    break
            if termination != 'search_exhausted':
                break
        if termination == 'context_limit':
            prefix_drops += 1
            termination = 'search_exhausted'
        elif termination != 'search_exhausted':
            break
    if cancel_event is not None and cancel_event.is_set():
        termination = 'cancelled'
    elif pending and len(seen_prefixes) >= scene['maxPrefixes']:
        termination = 'prefix_limit'
    elif prefix_drops and termination == 'search_exhausted':
        termination = 'context_limit'
    return {'status': 'feasible' if best is not None else 'cancelled' if termination == 'cancelled' else 'unknown',
            'best': best, 'termination': termination, 'attempts': attempts, 'normalResult': base,
            'scene': scene, 'graph': graph, 'elapsedSeconds': time.monotonic() - started,
            'ticksLowerBound': 1, 'minimumTicksProven': False, 'objectiveOptimalityProven': False,
            'searchPolicy': 'total_ticks_then_objective', 'validationLevel': 'continuous_ladder_with_shared_scene',
            'compositionSearch': {'prefixes': len(seen_prefixes), 'remaining': len(pending), 'contextLimitedPrefixes': prefix_drops},
            'assumptions': {'maxLadderInteractions': cfg['maxInteractions'], 'climbableCellsSupplied': True,
                            'initialStateReoptimized': False, 'wholePrefixFacingsReoptimized': True}}
