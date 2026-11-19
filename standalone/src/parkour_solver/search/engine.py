import copy
from collections import Counter
import hashlib
import math
import time

from parkour_solver.constraints.contacts import floor_checks
from parkour_solver.search.repair import solve_contact_branches
from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.movement.program import compile_program, program_physics_key
from parkour_solver.movement.settings import movement_settings
from parkour_solver.scene.route import supports
from parkour_solver.schema import integer, number
from parkour_solver.spatial.checkpoints import checkpoint
from parkour_solver.search.compat.decision_windows import DecisionPolicy, plan_windows
from parkour_solver.spatial.cells import decompose
from parkour_solver.validation.scoped_scene import CorridorAdapter
from parkour_solver.bridge.counting import CountingCompiler; from parkour_solver.search.work import SearchWork
from parkour_solver.spatial.compat.corridors import corridor_for_points, landmarks
from parkour_solver.search.goals import goal_spec, landing_satisfied
from parkour_solver.search.identity import solve_scope, solve_identity, validation_prefix
from parkour_solver.reachability.intervals import motion_bound, reachable_envelopes
from parkour_solver.constraints.variants import ConstraintVariants


class ScopedWork:
    def __init__(self, parent, calls, deadline):
        self.parent = parent
        self.local = SearchWork(calls, deadline=deadline)

    @property
    def deadline(self):
        return min(self.local.deadline or math.inf, self.parent.deadline or math.inf)

    @property
    def termination(self):
        return self.parent.termination or self.local.termination

    def check(self):
        self.local.check()
        self.parent.check()

    def proposal(self, problem):
        self.parent.proposal(problem)
        self.local.proposal(problem)

    def before_solve(self, problem):
        self.check()
        self.parent.before_solve(problem)
        self.local.before_solve(problem)

    def accepted(self, best):
        self.local.accepted(best)


def segment_solids(scene, program, profile, through, gate):
    from parkour_solver.constraints.encounters import landing_segments
    from parkour_solver.search.anchors.policy import launch
    result = {}
    for begin, end in landing_segments(profile, through):
        found = launch(scene, program, begin, end)
        if found is None:
            continue
        table = gate.reachable_blocks(found[1])
        if end-found[0] <= table['ticks']:
            result[begin] = sorted(table['solids'])
    return result


def evaluate_program(scene, program, compiler, adapter, work, motion_bound, *, checkpoint=None,
                     seconds=3, candidate_seconds=1, solver_calls=8, python_executable=None, cancel_event=None,
                     partial_corridor=False, required_landing=None, scope=None, on_progress=None, retry_unresolved=True, first_witness=False,
                     guide_objective=None, solve_policy=None, climb_families=False, final_route=None):
    timings={}
    marked=time.monotonic()
    def mark(name):
        nonlocal marked
        now=time.monotonic();timings[name]=now-marked;marked=now
    complete = checkpoint is None and not getattr(adapter,'node_query',False)
    from parkour_solver.search.feasibility import policy_spec, outcome
    policy = policy_spec(solve_policy, first_witness=first_witness)
    virtual=program.get('terminalGoal')
    if virtual is not None:
        from parkour_solver.search.virtual_goals import prepare_goal, height_clock
        virtual=prepare_goal(virtual)
        if not height_clock(program,virtual):
            raise ValueError('Virtual finish program does not reach its height clock')
    from parkour_solver.reachability.service import InteractionGate
    service=getattr(adapter,'interaction_service',None) or InteractionGate(scene,compiler,seconds=max(.001,min(seconds,candidate_seconds)))
    prepared=service.prepare(program,scene=scene,require_complete=complete and virtual is None)
    if prepared is None:
        return dict(status='unknown',termination='vertical_schedule',best=None,attempts=[],contactAttempts=[],work=dict(solverCalls=0),fullSceneValidated=False)
    route,profiles=copy.copy(prepared['route']),prepared['profiles']
    route['constraints']=list(route.get('constraints',[]))
    mark('programCompilation')
    if guide_objective is not None:
        route['objective']=dict(axis='X',sense='MAX')
        route['guideObjective']=copy.deepcopy(guide_objective)
    scope = solve_scope(scene, program, scope)
    scoped_adapter = adapter.scoped(scope) if hasattr(adapter, 'scoped') else adapter
    if hasattr(scoped_adapter,'bind_program'):
        scoped_adapter = scoped_adapter.bind_program(program)
    if checkpoint is not None:
        if program['profile']['airtime'] != checkpoint.tick:
            raise ValueError('Every compared program must end at the shared absolute tick')
        route['objective'] = dict(axis='X', sense='MAX')
        route['guideObjective'] = checkpoint.objective
        route['constraints'].extend(checkpoint.rows())
    scoped = ScopedWork(work, solver_calls, min(work.deadline or math.inf, time.monotonic()+seconds))
    owners = [b['sourceBlock'] for b in scene['blocks']]
    def candidate_filter(plan, candidate, vertical):
        p, c, v = validation_prefix(plan, candidate, vertical, scope['through'])
        verdict = scoped_adapter.candidate(p, c, v, partial=not complete or partial_corridor or scope['purpose'] == 'preliminary')
        if verdict['accepted'] and virtual is not None:
            from parkour_solver.search.virtual_goals import satisfied
            if not satisfied(program,candidate,virtual,scene.get('supportClearance',1e-6)):
                return dict(verdict,accepted=False,reason='virtual_finish_not_satisfied')
        if verdict['accepted'] and required_landing is not None and not landing_satisfied(scene,required_landing,vertical):
            return dict(verdict,accepted=False,reason='spatial_goal_not_satisfied')
        if verdict['accepted'] and checkpoint is not None and scope['purpose'] == 'complete':
            staged = dict(candidate, **{key: vertical[key] for key in ('y', 'vy', 'onGround')})
            stage = checkpoint.stage(staged, scoped_adapter.gates)
            if not stage['accepted']:
                return dict(verdict, accepted=False, reason='checkpoint_not_after_turn', comparisonStage=stage)
        return verdict

    from parkour_solver.scene.route import schedule
    from parkour_solver.validation.vertical import validate_vertical_schedule
    plan = schedule(route, [0])
    vertical = prepared['vertical']
    if not vertical['feasible']:
        return dict(status='unknown', termination='vertical_schedule', best=None, attempts=[], contactAttempts=[],
                    work=scoped.local.summary(), solveScope=scope, fullSceneValidated=False)
    plan['vertical'] = dict(vertical['vertical'], referencePosition=vertical['y'], referenceVelocity=vertical['vy'])
    mark('scopeAndVerticalPreparation')
    analysis=service.analyze_model(route,plan,vertical,scope=scope)
    coefficients=analysis['coefficient']
    mark('coefficientCompilation')
    model = dict(coefficients, version=1, ticks=plan['ticks'], walls=plan['walls'], contacts=plan['contacts'],
        position=scene['initial']['position'][::2], velocity=scene['initial']['velocity'][::2],
        threshold=route.get('physicsThreshold', .005), vertical=vertical['vertical'], objective=dict(route.get('guideObjective', route['objective']), tick=plan['ticks']))
    envelopes=analysis['envelopes']
    mark('reachability')
    if hasattr(scoped_adapter, 'eligible'):
        scoped_adapter = scoped_adapter.eligible(envelopes, scope['through'])
        if checkpoint is not None:
            from dataclasses import replace
            ids = {g['id'] for g in scoped_adapter.gates}
            checkpoint = replace(checkpoint, required_signature=tuple(t for t in checkpoint.required_signature if t[0] in ids))
    from parkour_solver.search.ordered_route import initial_guess
    from parkour_solver.numerics import prepare, validate
    reference_yaw = initial_guess(route, plan, None)
    reference = validate(prepare(dict(model, yaw=reference_yaw)), reference_yaw)['positions']
    active = set(scope['solidIndices'])
    checks = [c for c in floor_checks(scene, [scene['startBlock'], scene['goalBlock']], profiles, envelopes=envelopes)
              if c['block']['solidIndex'] in active and c['offset'] < scope['through']]
    signature = checkpoint.required_signature if checkpoint is not None else (
        scoped_adapter.corridor.signature if hasattr(scoped_adapter, 'corridor') and not partial_corridor else ())
    adapter_identity=getattr(scoped_adapter,'corridor',None).to_dict() if hasattr(scoped_adapter,'corridor') else None
    identity = solve_identity(scene, program, route.get('guideObjective', route['objective']), scope,
        dict(adapter=adapter_identity,policy=policy,nodeQuery=getattr(scoped_adapter,'node_query',False)), coefficients)
    saved_repair=program.get('repairState',{})
    saved_policy=(saved_repair.get('context',{}).get('scope') or {}).get('scope',{}).get('solvePolicy')
    variants = ConstraintVariants(route, profiles[0], plan, scene['collisionScene'], envelopes, scope,
        segment_solids=segment_solids(scene, program, profiles[0], scope['through'], service), checks=checks, gates=scoped_adapter.gates, signature=signature,
        state=saved_repair.get('constraintGeneration') if saved_policy==policy else None, deadline=scoped.deadline, reference=reference,
        owners=owners, budget=scoped.check, cancel_event=cancel_event,
        objective=route.get('guideObjective', route['objective']),
        route_guards=scoped_adapter.constraint_guards(program['profile'],envelopes,scope,scoped.deadline)
        if hasattr(scoped_adapter,'constraint_guards') else None)
    mark('eligibilityAndVariantConstruction')
    dispatch = None
    if policy['numerical'] == 'relaxed':
        from parkour_solver.numerics.disk import RelaxedDispatch
        dispatch = RelaxedDispatch(promoted=program.get('numericalPromoted', False), gate=service)
    # Finalization re-solves a relaxed witness's own branch route with IPOPT.
    result = solve_contact_branches(route if final_route is None else final_route, profiles, checks,
        compiler, seconds=seconds, candidate_seconds=candidate_seconds, retries=0,
        improve=True, rank_equal_cost=policy['rankEqualCost'], optimize_goal=True,
        stop_on_feasible=policy['stopOnFeasible'], oracle_rejection=policy['oracleRejection'], climb_families=climb_families,
        python_executable=python_executable, cancel_event=cancel_event, geometry=scene['collisionScene'],
        motion_bound=motion_bound, solid_blocks=owners, max_wall_hits=0,
        candidate_filter=candidate_filter, work=scoped, resume_state=program.get('repairState'),
        repair_scope=dict(solveIdentity=identity, scope=scope, solvePolicy=policy, assemblyVersion=13),
        validation_scope=scope, initial_variants=variants if final_route is None else None, on_progress=on_progress,
        retry_unresolved=retry_unresolved, numerical=dispatch.solve if dispatch is not None else None)
    mark('physicalSearchAndValidation')
    if dispatch is not None and dispatch.promotions:
        result['numericalPromotion'] = dispatch.promotions
    diagnostics=[d for a in result.get('attempts',[]) for d in a.get('diagnostics',[])]
    parent_work=work
    while not hasattr(parent_work,'counts'):
        parent_work=parent_work.parent
    for counter in (parent_work.counts,scoped.local.counts):
        counter.update(numerical_counts(diagnostics))
    result['timings']=dict(timings,**variants.state['timings'])
    if result['best'] is not None:
        if checkpoint is not None and scope['purpose'] == 'complete':
            stage = checkpoint.stage(result['best']['candidate'], scoped_adapter.gates)
            result['comparisonStage'] = stage
            if not stage['accepted']:
                result['deferredCandidate'] = result['best']
                result['best'] = None
                result.update(status='unknown', termination='checkpoint_not_after_turn')
        elif checkpoint is None and scope['purpose'] == 'complete':
            work.accepted(result['best'])
    result['work'] = scoped.local.summary()
    result.update(solveScope=scope, solveIdentity=identity,
                  solvePolicy=policy,objectiveMode='configured', nodeFeasibility=outcome(result, identity, policy),
                  fullSceneValidated=result['best'] is not None and scope['purpose'] == 'complete',
                  reachability=dict(excludedSolids=variants.exclusions, interactionGroups=len(variants.items)))
    if result['best'] is not None:
        result['best']['validationScope'] = scope
    return result


def numerical_counts(diagnostics):
    """Work counters for numerical requests; a Clarabel answer is never a worker or a seed."""
    counts = Counter()
    for d in diagnostics:
        ipopt = d.get('backend', 'ipopt') == 'ipopt'
        counts['workerLaunches'] += d.get('workerLaunched') is True
        counts['feasibleSeedReturns'] += ipopt and d.get('workerLaunched') is False and d.get('status') == 'feasible'
        counts['conicCandidates'] += not ipopt and d.get('status') == 'feasible'
        counts['conicUndecided'] += not ipopt and d.get('termination') == 'disk_undecided'
        if d.get('fallback'):
            counts['relaxedFallbacks'] += 1
            counts['relaxedFallback:'+d['fallback']] += 1
    return counts


def _key(program):
    return hashlib.sha256(program_physics_key(program).encode()).hexdigest()


def find_checkpoint_route(request, compiler, *, checkpoints=None, corridor=None, spatial_points=None, seconds=30,
                          candidate_seconds=1, score_seconds=3, max_solver_calls=128,
                          max_candidates=24, beam_width=4, unknown_slots=1, score_call_limit=8,
                          final_call_limit=24, order='guided', python_executable=None, cancel_event=None,
                          opportunities=None, cluster_gap=2, objective_gap=None,
                          initial_programs=None, comparison_policy='time_progress', spatial_goal=None,
                          spatial_seconds=1.5, spatial_calls=4, spatial_candidates=4, spatial_prefixes=64,
                          time_margin=1, progress_margin=None, rescue_passes=1, terminal_checkpoint=None, program_filter=None,
                          on_window=None, spatial_goals=None, program_priority=None, resume_state=None, on_progress=None,
                          retry_unknown=False, minimum_run_pruning=True, task_goal=None, spatial_settings=None,
                          discovery_settings=None, anchor_search=None):
    entry_options = locals().copy()
    if type(minimum_run_pruning) is not bool:raise ValueError('minimum_run_pruning must be boolean')
    integer(rescue_passes,'rescue passes',0,100)
    if comparison_policy not in ('time_progress','objective_band','unpruned'):
        raise ValueError('Unknown comparison policy')
    if number(spatial_seconds,'spatial comparison seconds') <= 0:
        raise ValueError('Spatial comparison seconds must be positive')
    for name,value in [('spatial_calls',spatial_calls),('spatial_candidates',spatial_candidates),('spatial_prefixes',spatial_prefixes)]:
        integer(value,name,1,100000)
    if number(time_margin,'time margin')<0:
        raise ValueError('Time margin must be nonnegative')
    progress_margin = progress_margin if progress_margin is not None else objective_gap if objective_gap is not None else .25
    if number(progress_margin,'progress margin')<0:
        raise ValueError('Progress margin must be nonnegative')
    started = time.monotonic()
    for name, value in [('seconds', seconds), ('candidate_seconds', candidate_seconds), ('score_seconds', score_seconds)]:
        if number(value, name) <= 0:
            raise ValueError(name+' must be positive')
    for name, value in [('max_candidates', max_candidates), ('beam_width', beam_width),
                        ('score_call_limit', score_call_limit), ('final_call_limit', final_call_limit), ('max_solver_calls', max_solver_calls)]:
        integer(value, name, 1, 100000)
    integer(unknown_slots, 'unknown_slots', 0, beam_width)
    if order not in ('guided', 'generation'):
        raise ValueError('Ordering must be guided or generation')
    if objective_gap is not None and number(objective_gap,'objective_gap') < 0:
        raise ValueError('objective_gap must be nonnegative')
    if opportunities is not None and checkpoints is not None:
        raise ValueError('Supply opportunities or checkpoint ticks, not both')
    if request.get('maxWallHits', 0):
        raise ValueError('Intentional wall-hit experiments are excluded')
    scene = prepare_scene(dict(request, maxWallHits=0))
    if scene['sceneModel'] != 'collision_boxes' or scene.get('climbableCells') or scene['maxWallHits']:
        raise ValueError('Checkpoint search requires non-ladder collision geometry with maxWallHits=0')
    spatial_goal = goal_spec(scene,spatial_goal)
    if spatial_goals is not None:
        spatial_goals = [goal_spec(scene,g) for g in spatial_goals]
    cfg = movement_settings(scene)
    if any(k not in ('normal', 'ceiling', 'stepping') for k in request.get('movementSearch', {}).get('types', [])):
        raise ValueError('Select only normal, ceiling and stepping movement types')
    cfg['types'] = [k for k in cfg['types'] if k in ('normal', 'ceiling', 'stepping')]
    deadline = started+seconds
    work, counted = SearchWork(max_solver_calls, deadline=deadline), CountingCompiler(compiler)
    def unfinished(reason):
        return dict(status='cancelled' if reason == 'cancelled' else 'unknown', termination=reason, best=None,
            windows=[], finalAttempts=[], routeGeneration=None, corridor=None, expandedPrefixes=0,
            movementGeneration=[], work=work.summary(), compiler=counted.summary(),
            elapsedSeconds=time.monotonic()-started, order=order, threshold=0., checkpoints=[],
            objectiveOptimalityProven=False, minimumTicksProven=False, comparisonPolicy=comparison_policy)
    if cancel_event is not None and cancel_event.is_set():
        return unfinished('cancelled')
    if corridor is None and spatial_points is None:
        if spatial_settings is not None:
            raise ValueError('Legacy spatialSettings catalogue limits require their source-era runner; migrate to discoverySettings version1')
        from parkour_solver.search.events.engine import search_event_routes
        for name in ('request','compiler','corridor','spatial_points','task_goal','spatial_settings','discovery_settings'):
            entry_options.pop(name)
        try:
            return search_event_routes(request,compiler,task_goal=task_goal,discovery=discovery_settings,**entry_options)
        except TimeoutError as error:
            return unfinished(str(error))
    if task_goal is not None or spatial_settings is not None or discovery_settings is not None or anchor_search is not None:
        raise ValueError('task_goal/discovery_settings apply to automatic discovery; a supplied typed corridor embeds its task')
    try:
        d, gates = decompose(scene, deadline=deadline), landmarks(scene)
    except TimeoutError:
        return unfinished('deadline')
    route_report = None
    if corridor is None:
        corridor = corridor_for_points(d, spatial_points, gates)
        goal = next(b for b in scene['blocks'] if b['id'] == scene['goalBlock'])
        if list(spatial_points[0]) != list(scene['initial']['position']):
            raise ValueError('The spatial guide must start at the original initial position')
        last = spatial_points[-1]
        if terminal_checkpoint is None and not (spatial_goals and spatial_goals[-1]['kind']=='landing_height_region') and (abs(last[1]-goal['top']) > 1e-7 or not supports(goal,last[0],last[2])):
            raise ValueError('The spatial guide must end in the goal landing region')
    window_plan = plan_windows(opportunities,gap=cluster_gap,max_inputs=cfg['maxInputs']) if opportunities is not None else None
    policy = DecisionPolicy(window_plan,scene) if window_plan is not None else None
    def accepted_program(program):
        from parkour_solver.search.identity import motion_record
        movement = motion_record(program)
        if movement is not None and any(name in request and movement[field] > request[name]
                for name, field in [('maxTotalRunTicks', 'runs'), ('maxJumps', 'jumps')]):
            return False
        return (policy is None or policy(program)) and (program_filter is None or program_filter(program))
    specs = window_plan['checkpoints'] if window_plan is not None else checkpoints or []
    plans = [checkpoint(corridor, gates, spec) for spec in specs]
    terminal_plan=checkpoint(corridor,gates,terminal_checkpoint) if terminal_checkpoint is not None else None
    if terminal_plan is not None and (terminal_plan.tick>cfg['maxInputs'] or any(p.tick>=terminal_plan.tick for p in plans)):
        raise ValueError('Terminal clock must follow every comparison checkpoint and fit the movement horizon')
    if any(a.tick >= b.tick or a.segment > b.segment for a, b in zip(plans, plans[1:])):
        raise ValueError('Checkpoints must increase in absolute tick and ordered route segment')
    if plans and plans[-1].tick > cfg['maxInputs']:
        raise ValueError('Checkpoint exceeds movement horizon')
    if corridor.schema == 2:
        from parkour_solver.search.compat.semantic_adapter import SemanticCorridorAdapter
        adapter = SemanticCorridorAdapter(scene,corridor,decomposition=d)
    else:
        adapter = CorridorAdapter(scene, corridor, gates, decomposition=d)
    try:
        work.check()
        motion = motion_bound(scene, counted, max(.001,min(score_seconds,deadline-time.monotonic())))
    except TimeoutError:
        return unfinished('deadline')
    from parkour_solver.search.dfs import search_goals
    return search_goals(scene, counted, adapter, work, motion, cfg=cfg, plans=plans,
        goals=spatial_goals, default_goal=spatial_goal, terminal_plan=terminal_plan, minimum_run_pruning=minimum_run_pruning,
        accepted_program=accepted_program, program_filter=program_filter, roots=initial_programs,
        resume_state=resume_state, retry_unknown=retry_unknown, deadline=deadline, started=started,
        candidate_seconds=candidate_seconds, score_seconds=score_seconds, score_calls=score_call_limit,
        final_calls=final_call_limit, batch_size=max_candidates, python_executable=python_executable,
        cancel_event=cancel_event, on_progress=on_progress, on_window=on_window,
        time_margin=time_margin, progress_margin=progress_margin, route_report=route_report,
        window_plan=window_plan, comparison_policy=comparison_policy)
