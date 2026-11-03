import copy
import hashlib
import math
import time

from standalone.autonomy.block_contacts import floor_checks, solve_contact_branches
from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.movement_composition import compile_program, program_physics_key
from standalone.autonomy.movement_search import movement_settings
from standalone.autonomy.route import supports
from standalone.ipopt.model import integer, number
from .checkpoints import checkpoint
from .decision_windows import DecisionPolicy, plan_windows
from .geometry import decompose
from .local import CorridorAdapter, CountingCompiler, SearchWork
from .routes import corridor_for_points, enumerate_routes, landmarks
from .comparison_goals import goal_spec, landing_satisfied
from standalone.autonomy.solve_spec import solve_scope, solve_identity, validation_prefix
from standalone.autonomy.reachability import reachable_envelopes
from standalone.autonomy.constraint_variants import ConstraintVariants


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


def _motion_bound(scene, compiler, seconds):
    sample = compiler.compile(dict(scene, sharedMovement=True), dict(grounded=[True, True, False], jump=[False, True, False]), seconds)
    speed = max(math.hypot(scene['initial']['velocity'][0], scene['initial']['velocity'][2]),
                max(f*m/(1-f) for m, f in zip(sample['magnitude'], sample['friction'])))
    return dict(storedSpeedNorm=speed+1e-8, movementAfterSupportCheck=speed+max(sample['magnitude'])+1e-8,
                groundMagnitude=sample['magnitude'][0])


def evaluate_program(scene, program, compiler, adapter, work, motion_bound, *, checkpoint=None,
                     seconds=3, candidate_seconds=1, solver_calls=8, python_executable=None, cancel_event=None,
                     partial_corridor=False, required_landing=None, scope=None, on_progress=None, retry_unresolved=True, first_witness=False):
    complete = checkpoint is None
    virtual=program.get('terminalGoal')
    if virtual is not None:
        from standalone.autonomy.terminal_goals import prepare_goal, height_clock
        virtual=prepare_goal(virtual)
        if not height_clock(program,virtual):
            raise ValueError('Virtual finish program does not reach its height clock')
    compiled = compile_program(scene, program, require_complete=complete and virtual is None)
    route, profiles = compiled['route'], compiled['profiles']
    scope = solve_scope(scene, program, scope)
    scoped_adapter = adapter.scoped(scope) if hasattr(adapter, 'scoped') else adapter
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
            from standalone.autonomy.terminal_goals import satisfied
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

    from standalone.autonomy.route import schedule
    from standalone.autonomy.block_contacts import validate_vertical_schedule
    plan = schedule(route, [0])
    vertical = validate_vertical_schedule(route, profiles, plan)
    if not vertical['feasible']:
        return dict(status='unknown', termination='vertical_schedule', best=None, attempts=[], contactAttempts=[],
                    work=scoped.local.summary(), solveScope=scope, fullSceneValidated=False)
    plan['vertical'] = dict(vertical['vertical'], referencePosition=vertical['y'], referenceVelocity=vertical['vy'])
    coefficients = compiler.compile(route, plan, max(.001, min(seconds, candidate_seconds)))
    model = dict(coefficients, version=1, ticks=plan['ticks'], walls=plan['walls'], contacts=plan['contacts'],
        position=scene['initial']['position'][::2], velocity=scene['initial']['velocity'][::2],
        threshold=route.get('physicsThreshold', .005), vertical=vertical['vertical'], objective=dict(route.get('guideObjective', route['objective']), tick=plan['ticks']))
    envelopes = reachable_envelopes(model, vertical['y'])
    from standalone.autonomy.solve_spec import digest
    envelopes['modelKey'] = digest(coefficients)
    if hasattr(scoped_adapter, 'eligible'):
        scoped_adapter = scoped_adapter.eligible(envelopes, scope['through'])
        if checkpoint is not None:
            from dataclasses import replace
            ids = {g['id'] for g in scoped_adapter.gates}
            checkpoint = replace(checkpoint, required_signature=tuple(t for t in checkpoint.required_signature if t[0] in ids))
    from standalone.autonomy.search import initial_guess
    from standalone.ipopt import prepare, validate
    reference_yaw = initial_guess(route, plan, None)
    reference = validate(prepare(dict(model, yaw=reference_yaw)), reference_yaw)['positions']
    active = set(scope['solidIndices'])
    checks = [c for c in floor_checks(scene, [scene['startBlock'], scene['goalBlock']], profiles, envelopes=envelopes)
              if c['block']['solidIndex'] in active and c['offset'] < scope['through']]
    signature = checkpoint.required_signature if checkpoint is not None else (
        scoped_adapter.corridor.signature if hasattr(scoped_adapter, 'corridor') and not partial_corridor else ())
    identity = solve_identity(scene, program, route.get('guideObjective', route['objective']), scope,
        getattr(scoped_adapter, 'corridor', None).to_dict() if hasattr(scoped_adapter, 'corridor') else None, coefficients)
    variants = ConstraintVariants(route, profiles[0], plan, scene['collisionScene'], envelopes, scope,
        checks=checks, gates=scoped_adapter.gates, signature=signature,
        state=program.get('repairState', {}).get('constraintGeneration'), deadline=scoped.deadline, reference=reference)
    result = solve_contact_branches(route, profiles, checks,
        compiler, seconds=seconds, candidate_seconds=candidate_seconds, retries=0,
        max_branches=scene['maxContactBranches'], improve=True, rank_equal_cost=not first_witness, optimize_goal=True,
        python_executable=python_executable, cancel_event=cancel_event, geometry=scene['collisionScene'],
        motion_bound=motion_bound, max_blockages=scene['maxBlockagesPerObstacle'],
        max_horizontal=scene['maxHorizontalBlockagesPerObstacle'], max_vertical=scene['maxVerticalBlockagesPerObstacle'],
        max_passage_candidates=scene['maxPassageCandidates'], solid_blocks=owners, max_wall_hits=0,
        candidate_filter=candidate_filter, work=scoped, resume_state=program.get('repairState'),
        repair_scope=dict(solveIdentity=identity, scope=scope, assemblyVersion=6),
        validation_scope=scope, initial_variants=variants, on_progress=on_progress, retry_unresolved=retry_unresolved)
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
                  fullSceneValidated=result['best'] is not None and scope['purpose'] == 'complete',
                  reachability=dict(excludedSolids=variants.exclusions, interactionGroups=len(variants.items)))
    if result['best'] is not None:
        result['best']['validationScope'] = scope
    return result


def _key(program):
    return hashlib.sha256(program_physics_key(program).encode()).hexdigest()


def find_checkpoint_route(request, compiler, *, checkpoints=None, corridor=None, spatial_points=None, seconds=30,
                          candidate_seconds=1, score_seconds=3, max_solver_calls=128,
                          max_candidates=24, beam_width=4, unknown_slots=1, score_call_limit=8,
                          final_call_limit=24, order='guided', python_executable=None, cancel_event=None,
                          opportunities=None, cluster_gap=2, objective_gap=None, fair_parents=False,
                          initial_programs=None, comparison_policy='time_progress', spatial_goal=None,
                          spatial_seconds=1.5, spatial_calls=4, spatial_candidates=4, spatial_prefixes=64,
                          time_margin=1, progress_margin=None, rescue_passes=1, terminal_checkpoint=None, program_filter=None,
                          on_window=None, spatial_goals=None, program_priority=None, resume_state=None, on_progress=None,
                          retry_unknown=False, minimum_run_pruning=True):
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
    if type(fair_parents) is not bool:
        raise ValueError('fair_parents must be boolean')
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
    try:
        d, gates = decompose(scene, deadline=deadline), landmarks(scene)
    except TimeoutError:
        return unfinished('deadline')
    route_report = None
    if corridor is None:
        if spatial_points is not None:
            goal = next(b for b in scene['blocks'] if b['id'] == scene['goalBlock'])
            if list(spatial_points[0]) != list(scene['initial']['position']):
                raise ValueError('The spatial guide must start at the original initial position')
            last = spatial_points[-1]
            if terminal_checkpoint is None and not (spatial_goals and spatial_goals[-1]['kind']=='landing_height_region') and (abs(last[1]-goal['top']) > 1e-7 or not supports(goal,last[0],last[2])):
                raise ValueError('The spatial guide must end in the goal landing region')
            corridor = corridor_for_points(d, spatial_points, gates)
        else:
            routes, route_report = enumerate_routes(scene, d, max_routes=1, max_expansions=1000, deadline=deadline)
            if not routes:
                result = unfinished('no_spatial_route')
                result['routeGeneration'] = route_report
                return result
            corridor = routes[0]
    window_plan = plan_windows(opportunities,gap=cluster_gap,max_inputs=cfg['maxInputs']) if opportunities is not None else None
    policy = DecisionPolicy(window_plan,scene) if window_plan is not None else None
    def accepted_program(program):
        from standalone.autonomy.solve_spec import motion_record
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
    adapter = CorridorAdapter(scene, corridor, gates, decomposition=d)
    try:
        work.check()
        motion = _motion_bound(scene, counted, max(.001,min(score_seconds,deadline-time.monotonic())))
    except TimeoutError:
        return unfinished('deadline')
    from .depth_first import search_goals
    return search_goals(scene, counted, adapter, work, motion, cfg=cfg, plans=plans,
        goals=spatial_goals, default_goal=spatial_goal, terminal_plan=terminal_plan, minimum_run_pruning=minimum_run_pruning,
        accepted_program=accepted_program, program_filter=program_filter, roots=initial_programs,
        resume_state=resume_state, retry_unknown=retry_unknown, deadline=deadline, started=started,
        candidate_seconds=candidate_seconds, score_seconds=score_seconds, score_calls=score_call_limit,
        final_calls=final_call_limit, batch_size=max_candidates, python_executable=python_executable,
        cancel_event=cancel_event, on_progress=on_progress, on_window=on_window,
        time_margin=time_margin, progress_margin=progress_margin, route_report=route_report,
        window_plan=window_plan, comparison_policy=comparison_policy)
