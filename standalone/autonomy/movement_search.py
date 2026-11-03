import copy
import heapq
import json
import math
import time

from standalone.ipopt.model import integer, number
from .case_profiles import entry_possible
from .movement_composition import append_choice, boundary_state, compile_program, empty_program, truncate_program, program_search_key
from .movement_generation import GENERATORS, generate_choices


def movement_settings(scene):
    settings = dict(scene.get('movementSearch', {}))
    defaults = dict(maxInputs=200, maxPrefixes=256, maxQueuedPrefixes=512, maxChoicesPerType=64,
                    maxInteractions=200, maxEntryInputs=14, expansionsPerProposal=8,
                    types=list(GENERATORS))
    if set(settings) - set(defaults):
        raise ValueError('Unsupported movement search settings')
    for key, value in defaults.items():
        settings.setdefault(key, value)
        if key != 'types':
            integer(settings[key], key, 0 if key == 'maxEntryInputs' else 1, 200 if key in ('maxInputs', 'maxInteractions') else 200000)
    if not isinstance(settings['types'], list) or not settings['types'] or len(set(settings['types'])) != len(settings['types']) \
            or any(kind not in GENERATORS for kind in settings['types']):
        raise ValueError('Select registered movement types')
    return settings


def iter_movement_programs(scene, *, settings=None, deadline=None, cancel_event=None, report=None, screen=None,
                           context_adapter=None, work=None, checkpoint_tick=None, initial_programs=None, threshold=.005,
                           program_filter=None, queue_policy='balanced', stop_on_goal=True, program_priority=None,
                           persistent=False, continuation=None, terminal_goal=None):
    cfg = movement_settings(scene) if settings is None else settings
    if persistent:
        from .movement_frontier import persistent_programs
        yield from persistent_programs(scene, settings=cfg, report=report if report is not None else {},
            continuation=continuation, deadline=deadline, cancel_event=cancel_event, work=work,
            initial_programs=initial_programs, checkpoint_tick=checkpoint_tick, threshold=threshold, screen=screen,
            context_adapter=context_adapter, program_filter=program_filter, stop_on_goal=stop_on_goal, terminal_goal=terminal_goal)
        return
    if terminal_goal is not None:
        raise ValueError('Virtual terminal discovery requires the persistent movement source')
    if queue_policy not in ('balanced', 'legacy'):
        raise ValueError('Unknown movement queue policy')
    report = report if report is not None else {}
    if checkpoint_tick is not None:
        integer(checkpoint_tick, 'checkpoint tick', 1, cfg['maxInputs'])
    initial = empty_program(scene['initial'], threshold=threshold)
    initial['terminalSupport'] = scene['startBlock']
    seeds = [initial] if initial_programs is None else list(initial_programs)
    if any(p['initial'] != initial['initial'] or p.get('threshold', .005) != threshold for p in seeds):
        raise ValueError('Checkpoint seeds must preserve the original vertical state and physics threshold')
    pending = [(p['profile']['airtime'], i, p) for i, p in enumerate(seeds)]
    if checkpoint_tick is not None and any(t >= checkpoint_tick for t, _, _ in pending):
        raise ValueError('Checkpoint seeds must end before the comparison tick')
    heapq.heapify(pending)
    serial, expanded, generated, screened, drops, seen = len(seeds), 0, 0, 0, 0, set()
    records, termination, policy_rejections = [], 'exhausted', 0
    ready, since_proposal, cursor, complete_drops, proposals = [], 0, -1, 0, 0

    def complete(program):
        ticks = program['profile']['airtime']
        return checkpoint_tick is not None and ticks == checkpoint_tick or (
            stop_on_goal and ticks and program['terminalSupport'] == scene['goalBlock'] and program['profile']['grounded'][-1]
            and (not scene.get('ladderSearch', {}).get('requireLadder') or program['ladderEvents']))

    def fair_subset(items, count):
        buckets = {}
        for item in sorted(items, key=lambda item: item[1]):
            buckets.setdefault(item[0], []).append(item)
        result = []
        while buckets and len(result) < count:
            for tick in sorted(buckets):
                result.append(buckets[tick].pop(0))
                if not buckets[tick]:
                    del buckets[tick]
                if len(result) == count:
                    break
        return result

    initial_bounds = [scene['initial']['position'][0]] * 2 + [scene['initial']['position'][2]] * 2
    def budget():
        if work is not None:
            work.check()
        if deadline is not None and time.monotonic() >= deadline:
            raise TimeoutError('Movement composition exceeded its budget')
    def ranked(item):return program_priority(item[2]),item[0],item[1]
    def take(items):
        item=min(items,key=ranked)
        items.remove(item)
        heapq.heapify(items)
        return item
    try:
        while pending or ready:
            budget()
            if cancel_event is not None and cancel_event.is_set():
                termination = 'cancelled'
                break
            if ready and (not pending or expanded >= cfg['maxPrefixes'] or proposals == 0
                          or since_proposal >= cfg.get('expansionsPerProposal', 8)) and (
                    program_priority is None or not pending or expanded>=cfg['maxPrefixes']
                    or ranked(min(ready,key=ranked))[0]<=ranked(min(pending,key=ranked))[0]):
                ticks, _, program = take(ready) if program_priority is not None else heapq.heappop(ready)
                since_proposal = 0
                if ticks > report.get('maxAcceptedTicks', cfg['maxInputs']):
                    continue
                if checkpoint_tick is not None and ticks == checkpoint_tick:
                    report.update(checkpointTick=checkpoint_tick, checkpointPrefixes=report.get('checkpointPrefixes', 0)+1)
                else:
                    program['terminal'] = 'landed'
                proposals += 1
                report.update(expandedPrefixes=expanded, generated=generated, remainingPrefixes=len(pending)+len(ready))
                yield program
                continue
            if expanded >= cfg['maxPrefixes']:
                termination = 'prefix_limit'
                break
            if program_priority is not None:
                ticks,_,program=take(pending)
            elif queue_policy == 'balanced' and expanded % 2:
                times = sorted({item[0] for item in pending})
                cursor = next((tick for tick in times if tick > cursor), times[0])
                item = min((item for item in pending if item[0] == cursor), key=lambda item: item[1])
                pending.remove(item)
                heapq.heapify(pending)
                ticks, _, program = item
            else:
                ticks, _, program = heapq.heappop(pending)
            if ticks > report.get('maxAcceptedTicks', cfg['maxInputs']):
                continue
            expanded += 1
            since_proposal += 1
            report.update(expandedPrefixes=expanded, generated=generated, remainingPrefixes=len(pending)+len(ready),
                          screenedPrefixes=screened, queueDrops=drops, generation=records, minimumTicksProven=False)
            if complete(program):
                if checkpoint_tick is not None and ticks == checkpoint_tick:
                    report.update(checkpointTick=checkpoint_tick, checkpointPrefixes=report.get('checkpointPrefixes', 0)+1)
                else:
                    program['terminal'] = 'landed'
                proposals += 1
                yield program
                continue
            remaining = cfg['maxInputs'] - ticks
            if not remaining or program['interactions'] >= cfg['maxInteractions']:
                continue
            context = dict(mode='open', scene=scene, program=program, state=boundary_state(program),
                maxInputs=remaining, maxEntryInputs=cfg['maxEntryInputs'], checkBudget=budget,
                deadline=deadline, cancelEvent=cancel_event, normalGap=checkpoint_tick is not None, generationRecords=records)
            if program.get('threshold', .005) != .005:
                context['threshold'] = program['threshold']
            if checkpoint_tick is not None:
                context['normalGapInputs'] = checkpoint_tick-ticks
            if context_adapter is not None:
                context_adapter(context)
            for kind in cfg['types']:
                choices = 0
                for choice in generate_choices(kind, context):
                    budget()
                    if choices >= cfg['maxChoicesPerType']:
                        records.append(dict(type=kind, prefixTicks=ticks, termination='choice_limit', emitted=choices))
                        break
                    choices += 1
                    generated += 1
                    child = append_choice(program, choice, max_inputs=cfg['maxInputs'])
                    if child is not None and screen is not None and screen(child) is not None:
                        screened += 1
                        continue
                    if child is not None and checkpoint_tick is not None and child['profile']['airtime'] > checkpoint_tick:
                        child = truncate_program(child, checkpoint_tick)
                    if child is not None and checkpoint_tick is not None and child['profile']['grounded'][-1]:
                        goal = next(b for b in scene['blocks'] if b['id'] == scene['goalBlock'])
                        if abs(child['profile']['position'][-1]-goal['top']) < 1e-7:
                            box = scene['collisionScene']['solids'][goal['solidIndex']]
                            at = child['profile']['airtime']-1
                            events = child['profile']['contactEvents']+child['profile']['caseEvents']
                            if any(e.get('offset', e.get('input')) == at and e.get('box') == box for e in events):
                                child['terminalSupport'] = scene['goalBlock']
                    if child is None or not entry_possible(child['profile'], initial_bounds):
                        continue
                    if program_filter is not None and not program_filter(child):
                        policy_rejections += 1
                        continue
                    if context.get('prefixFilter') is not None and not context['prefixFilter'](child):
                        continue
                    key = program_search_key(child) if checkpoint_tick is not None or child.get('repairState') is not None else json.dumps(
                        [child['profile'], [(e['column']['id'], e['wall'], e['entryFace'], e['exitFace'],
                        e['schedule']['entryState'], e['schedule']['exitState']) for e in child['ladderEvents']]], sort_keys=True)
                    if key in seen:
                        continue
                    seen.add(key)
                    if screen is not None and screen(child) is not None:
                        screened += 1
                        continue
                    serial += 1
                    item = (child['profile']['airtime'], serial, child)
                    if queue_policy == 'balanced' and complete(child):
                        heapq.heappush(ready, item)
                        if len(ready) > cfg['maxQueuedPrefixes']:
                            complete_drops += len(ready)-cfg['maxQueuedPrefixes']
                            ready = sorted(ready,key=ranked)[:cfg['maxQueuedPrefixes']] if program_priority is not None else heapq.nsmallest(cfg['maxQueuedPrefixes'], ready)
                            heapq.heapify(ready)
                    else:
                        heapq.heappush(pending, item)
                        if len(pending) > cfg['maxQueuedPrefixes']:
                            drops += len(pending) - cfg['maxQueuedPrefixes']
                            pending = sorted(pending,key=ranked)[:cfg['maxQueuedPrefixes']] if program_priority is not None else fair_subset(pending, cfg['maxQueuedPrefixes']) if queue_policy == 'balanced' else heapq.nsmallest(cfg['maxQueuedPrefixes'], pending)
                            heapq.heapify(pending)
                else:
                    records.append(dict(type=kind, prefixTicks=ticks, termination='exhausted', emitted=choices))
    except TimeoutError:
        termination = work.termination if work is not None and work.termination else 'deadline'
    except GeneratorExit:
        termination = 'consumer_closed'
        raise
    finally:
        report.update(termination=termination, expandedPrefixes=expanded, generated=generated,
            remainingPrefixes=len(pending)+len(ready), screenedPrefixes=screened, queueDrops=drops+complete_drops, generation=records,
            partialQueueDrops=drops, completeQueueDrops=complete_drops, queuedCompletePrograms=len(ready), emittedPrograms=proposals, queuePolicy=queue_policy,
            policyRejections=policy_rejections, minimumTicksProven=False, types=cfg['types'],
            explicitProgramPriority=program_priority is not None)


def find_movement_blocks(request, compiler, *, seconds=60, candidate_seconds=2, retries=1,
                         improve=False, python_executable=None, cancel_event=None, on_attempt=None,
                         route_seconds=8, context_adapter=None, candidate_filter=None, work=None):
    from .block_search import prepare_scene
    from .block_contacts import floor_checks, solve_contact_branches
    from .ranking import candidate_rank
    for name, value in [('seconds', seconds), ('candidate_seconds', candidate_seconds), ('route_seconds', route_seconds)]:
        if number(value, name) <= 0:
            raise ValueError(name + ' must be positive')
    integer(retries, 'retries', 0, 10)
    scene = prepare_scene(request)
    if scene['sceneModel'] != 'collision_boxes':
        raise ValueError('Generic movement composition requires collision_boxes geometry')
    cfg = movement_settings(scene)
    started = time.monotonic()
    deadline = started + seconds
    sample = compiler.compile(dict(scene, sharedMovement=True), dict(grounded=[True, True, False], jump=[False, True, False]), seconds)
    speed = max(math.hypot(scene['initial']['velocity'][0], scene['initial']['velocity'][2]),
                max(f * m / (1 - f) for m, f in zip(sample['magnitude'], sample['friction'])))
    motion_bound = dict(storedSpeedNorm=speed + 1e-8, movementAfterSupportCheck=speed + max(sample['magnitude']) + 1e-8,
                        groundMagnitude=sample['magnitude'][0])
    owners = {b['solidIndex']: b['sourceBlock'] for b in scene['blocks']}
    report, attempts, best = {}, [], None
    from .interactions import InteractionGate
    gate = InteractionGate(scene, compiler, seconds=candidate_seconds)
    def configure(context):
        if context_adapter is not None:
            context_adapter(context)
        gate.context(context)
    def screen(program):
        return gate.required(program) or screen_program(scene, program, compiler, max(.001, deadline - time.monotonic()))
    for program in iter_movement_programs(scene, settings=cfg, deadline=deadline, cancel_event=cancel_event,
                                          report=report, screen=screen, context_adapter=configure, work=work):
        if time.monotonic() >= deadline:
            break
        compiled = compile_program(scene, program)
        if work is not None:
            work.program(program)
        result = solve_contact_branches(compiled['route'], compiled['profiles'],
            floor_checks(scene, [scene['startBlock'], scene['goalBlock']], compiled['profiles']), compiler,
            seconds=min(route_seconds, max(.001, deadline - time.monotonic())), candidate_seconds=candidate_seconds,
            retries=retries, max_branches=scene['maxContactBranches'], improve=improve,
            rank_equal_cost=True, optimize_goal=True, python_executable=python_executable,
            cancel_event=cancel_event, geometry=scene['collisionScene'],
            motion_bound=motion_bound,
            max_blockages=scene['maxBlockagesPerObstacle'], max_horizontal=scene['maxHorizontalBlockagesPerObstacle'],
            max_vertical=scene['maxVerticalBlockagesPerObstacle'], max_passage_candidates=scene['maxPassageCandidates'],
            solid_blocks=[owners[i] for i in range(len(owners))], max_wall_hits=scene['maxWallHits'],
            max_wall_candidates=scene['maxWallHitCandidates'], candidate_filter=candidate_filter, work=work)
        attempt = dict(ticks=program['profile']['airtime'], status=result['status'], termination=result['termination'],
                       units=[u['type'] for u in program['movementUnits']])
        if result.get('error'):
            attempt['error'] = result['error']
        attempts.append(attempt)
        if on_attempt is not None:
            on_attempt(attempt)
        if result['status'] == 'error':
            report['termination'] = 'numerical_error'
            break
        if result['best'] is not None and (best is None or candidate_rank(result['best'], scene['objective']) < candidate_rank(best, scene['objective'])):
            best = result['best']
            best['movementProgram'] = program
            best['blockIds'] = [scene['startBlock'], scene['goalBlock']]
            report['maxAcceptedTicks'] = best['plan']['ticks']
    return dict(status='feasible' if best is not None else 'cancelled' if report.get('termination') == 'cancelled' else 'error' if report.get('termination') == 'numerical_error' else 'unknown',
        best=best, termination=report.get('termination', 'deadline'), attempts=attempts, movementGeneration=report,
        elapsedSeconds=time.monotonic() - started, minimumTicksProven=False, objectiveOptimalityProven=False,
        validationLevel='continuous_movement_program_with_shared_scene_collision_checks')


def screen_program(scene, program, compiler, seconds):
    from .block_contacts import validate_vertical_schedule
    from .route import prepare_route, schedule
    from .screen import contradiction
    profile = program['profile']
    if profile['position'][1] < scene['initial']['position'][1]-1e-7:
        from .route import supports
        if any(abs(b['top']-scene['initial']['position'][1])<1e-7 and supports(
                b,scene['initial']['position'][0],scene['initial']['position'][2]) for b in scene['blocks']):
            return dict(reason='unselected_initial_floor')
    compiled = compile_program(scene, program, require_complete=False)
    route = prepare_route(compiled['route'])
    plan = schedule(route, [0])
    vertical = validate_vertical_schedule(route, compiled['profiles'], plan)
    if not vertical['feasible']:
        return dict(reason='vertical_schedule')
    if scene.get('maxWallHits', 0):
        return None
    plan['vertical'] = dict(vertical['vertical'], referencePosition=vertical['y'], referenceVelocity=vertical['vy'])
    coefficient = compiler.compile(route, plan, seconds)
    rows = [row for row in plan['walls'] if not row.get('name', '').startswith('landing:1:')]
    return contradiction(dict(coefficient, ticks=plan['ticks'], position=scene['initial']['position'][::2],
        velocity=scene['initial']['velocity'][::2], threshold=route.get('physicsThreshold', .005), walls=rows, contacts=plan['contacts'],
        ladderTicks=plan.get('ladderTicks', [False] * (plan['ticks'] + 1))))
