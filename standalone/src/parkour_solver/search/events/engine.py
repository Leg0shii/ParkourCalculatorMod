"""Allocate one existing movement DFS over lazy contact/guide requests."""

import copy
import time

from parkour_solver.search.comparison import POLICY_VERSION as COMPARISON_POLICY_VERSION

from parkour_solver.scene.normalize import prepare_scene
from parkour_solver.movement.settings import movement_settings
from parkour_solver.search.identity import digest
from parkour_solver.search.goals import goal_spec
from parkour_solver.spatial.event_routes import RouteDomain, build_route_domain, build_event_graph
from parkour_solver.search.events.adapter import EventGuideAdapter
from parkour_solver.bridge.counting import CountingCompiler
from parkour_solver.search.work import SearchWork
from parkour_solver.search.compat.corridor_queue import collision_request


def discovery_settings(settings=None):
    defaults=dict(version=1,maxGraphExpansions=5000,maxGuideMaterializations=50)
    if settings is not None:
        if set(settings)-set(defaults):
            raise ValueError('Unsupported discoverySettings field')
        defaults.update(settings)
    if defaults['version'] != 1 or type(defaults['version']) is not int:
        raise ValueError('Unsupported discoverySettings version')
    if any(type(defaults[k]) is not int or defaults[k]<1 for k in defaults if k!='version'):
        raise ValueError('Discovery work limits must be positive integers')
    return defaults


def search_event_routes(request, compiler, *, task_goal=None, discovery=None, anchor_search=None, **options):
    from parkour_solver.search.engine import motion_bound
    from parkour_solver.search.dfs import search_goals
    started=time.monotonic(); deadline=started+options['seconds']
    limits=discovery_settings(discovery)
    if any(options.get(k) for k in ('checkpoints','opportunities','spatial_goals')) or options.get('terminal_checkpoint') is not None:
        raise ValueError('Automatic event discovery derives its own contact/finish goals')
    scene=prepare_scene(request)
    task=goal_spec(scene,task_goal if task_goal is not None else options.get('spatial_goal'))
    anchor_policy=None
    if task['kind']=='landing' or anchor_search is not None:
        from parkour_solver.search.anchors.policy import settings, semantic_settings
        anchor_policy=settings(anchor_search or {},scene)
        if task['kind']!='landing':
            raise ValueError('Anchor search requires a finite physical landing task')
    saved=options.get('resume_state')
    policy=options.get('program_filter')
    roots=options.get('initial_programs')
    if roots is None and saved is not None:
        roots=saved.get('initialRoots')
    cfg=movement_settings(scene)
    cfg['types']=[t for t in cfg['types'] if t in ('normal','ceiling','stepping')]
    scene['movementSearch']=cfg
    context=digest(dict(policy=4 if anchor_policy else 3,comparisonPolicy=COMPARISON_POLICY_VERSION,
        **(dict(anchorPolicy=semantic_settings(anchor_policy)) if anchor_policy else {}),
        constraintPolicy=14,scene={k:v for k,v in scene.items() if k!='movementSearch'},
        task=task,roots=roots,filter=getattr(policy,'checkpoint_identity',None),
        minimumRuns=options['minimum_run_pruning'],
        domain={k:v for k,v in cfg.items() if k not in
            ('maxPrefixes','maxQueuedPrefixes','maxChoicesPerType','expansionsPerProposal')}))
    schema=2 if anchor_policy else 1
    if saved is not None and (saved.get('schema')!=schema or saved.get('kind')!='event_route_search' or saved.get('context')!=context):
        raise ValueError('Incompatible automatic event continuation; cell-walk/schema6 states require their source-era runner')
    counted=CountingCompiler(compiler)
    if saved is not None:
        state=copy.deepcopy(saved)
        RouteDomain.from_dict(state['domain'])
        request=state['effectiveRequest']; scene=prepare_scene(request)
        scene['movementSearch']=cfg
    else:
        if task['kind']=='landing_height_region':
            request=collision_request(request,counted,min(options['candidate_seconds'],max(.001,deadline-time.monotonic())))
            scene=prepare_scene(request)
            scene['movementSearch']=cfg
        domain=build_route_domain(scene,task,deadline=deadline)
        data=domain.to_dict()
        state=dict(schema=schema,kind='event_route_search',context=context,domain=data,graph=build_event_graph(data),
            dfs=None,initialRoots=copy.deepcopy(roots),effectiveRequest=request,best=None,sessions=0,allocations=[])
    state['sessions']+=1
    saved_best=(state.get('dfs') or {}).get('best') or state.get('best')
    if saved is not None and saved_best is not None:
        from parkour_solver.validation.witness import replay_witness
        if not replay_witness(scene,saved_best,counted)['passed']:
            raise ValueError('Saved automatic incumbent no longer passes independent replay')
        state['best']=saved_best
    motion=state.get('motion')
    if motion is None:
        motion=motion_bound(scene,counted,max(.001,min(options['candidate_seconds'],deadline-time.monotonic())))
        state['motion']=motion
    adapter=EventGuideAdapter(state['domain'],state['graph'],motion=motion,compiler=counted,
                              candidate_seconds=options['candidate_seconds'])
    if anchor_policy is not None:
        from parkour_solver.search.anchors.adapter import AnchorAdapter
        adapter=AnchorAdapter(state['domain'],state['graph'],anchor_policy,motion=motion,compiler=counted,
                              candidate_seconds=options['candidate_seconds'])
        state['anchorPolicy']=anchor_policy
        state['guidePlan']=adapter.guide_plan
    calls=0; expanded=0; allocations=[]; worker_calls=0; seed_returns=0
    used=dict(expansions=0,materializations=0)
    termination='exhausted'
    last=None
    def accepted_program(program):
        from parkour_solver.search.identity import motion_record
        if anchor_policy is not None:
            from parkour_solver.search.anchors.policy import within_caps
            if not within_caps(request,program):return False
        movement=motion_record(program)
        if movement is not None and any(name in request and movement[field]>request[name]
                for name,field in (('maxTotalRunTicks','runs'),('maxJumps','jumps'))):
            return False
        return policy is None or policy(program)
    while time.monotonic()<deadline and calls<options['max_solver_calls'] and expanded<cfg['maxPrefixes']:
        if options.get('cancel_event') is not None and options['cancel_event'].is_set():
            termination='cancelled';break
        adapter.discovery_settings=dict(limits,
            maxGraphExpansions=max(0,limits['maxGraphExpansions']-used['expansions']),
            maxGuideMaterializations=max(0,limits['maxGuideMaterializations']-used['materializations']))
        # Anchor DFS owns its traversal until the run budget ends. It does not
        # rotate stalled branches, so service slices only interrupt that work.
        local_deadline=deadline if anchor_policy is not None else min(deadline,time.monotonic()+options['score_seconds'])
        remaining_calls=options['max_solver_calls']-calls
        work=SearchWork(remaining_calls if anchor_policy is not None else min(options['score_call_limit'],remaining_calls),
                        deadline=local_deadline)
        dfs=state['dfs']
        before_depth=max((f['index'] for f in dfs.get('stack',[])),default=-1) if dfs else -1
        before_frames=dfs['nextFrame'] if dfs else 0
        def publish(value):
            state['dfs']=value
            if options.get('on_progress'):
                options['on_progress'](state)
        def begin_repair(value,program,extra_targets=()):
            state['dfs']=value
            callback=getattr(options.get('on_progress'),'begin_repair',None)
            if callback is not None:callback(state,program,extra_targets)
            else:publish(value)
        def repair_progress(value):
            state['dfs']=value
            callback=getattr(options.get('on_progress'),'repair_progress',None)
            if callback is not None:callback(state)
            else:publish(value)
        publish.begin_repair=begin_repair
        publish.repair_progress=repair_progress
        last=search_goals(scene,counted,adapter,work,motion,
            cfg=dict(cfg,maxPrefixes=cfg['maxPrefixes']-expanded),plans=[],goals=None,default_goal=task,terminal_plan=None,
            accepted_program=accepted_program,program_filter=policy,roots=roots,
            resume_state=dfs,retry_unknown=options['retry_unknown'],deadline=local_deadline,started=started,
            candidate_seconds=options['candidate_seconds'],
            score_seconds=options['seconds'] if anchor_policy is not None else options['score_seconds'],
            score_calls=None if anchor_policy is not None else options['score_call_limit'],
            final_calls=None if anchor_policy is not None else options['final_call_limit'],batch_size=options['max_candidates'],
            python_executable=options.get('python_executable'),cancel_event=options.get('cancel_event'),
            on_progress=publish,on_window=options.get('on_window'),time_margin=options['time_margin'],
            progress_margin=options['progress_margin'] if options['progress_margin'] is not None else .25,
            route_report=None,window_plan=None,comparison_policy=options['comparison_policy'],
            minimum_run_pruning=options['minimum_run_pruning'],_owned_state=True)
        state['dfs']=last['searchState']; state['best']=last['best']
        dfs=state['dfs']
        for k in used:used[k]+=dfs['discoveryWork'][k]
        calls+=last['work'].get('solverCalls',0);expanded+=last['expandedPrefixes']
        worker_calls+=last['work'].get('workerLaunches',0);seed_returns+=last['work'].get('feasibleSeedReturns',0)
        after_depth=max((f['index'] for f in dfs['stack']),default=-1)
        advanced=after_depth>before_depth
        allocation=dict(calls=last['work'].get('solverCalls',0),expanded=last['expandedPrefixes'],
            termination=last['termination'],beforeDepth=before_depth,afterDepth=after_depth,advanced=advanced)
        allocations.append(allocation);state['allocations'].append(allocation)
        if anchor_policy is None and not advanced and dfs['stack'] and dfs['goalQueue']:
            # Each frame contains its actual parent/program/cursor. Parking a
            # stack changes service order only, never adopts a sibling child.
            dfs['goalQueue'].extend(reversed(dfs['stack']))
            dfs['stack']=[dfs['goalQueue'].pop(0)]
        if options.get('on_progress'):options['on_progress'](state)
        termination=last['termination']
        if termination in ('error','cancelled'):
            break
        active=bool(dfs['stack'] or dfs['goalQueue'] or dfs['refinements'])
        discoverable=any(not d['exhausted'] for d in dfs['discovery'].values()) and (
            used['expansions']<limits['maxGraphExpansions'] and used['materializations']<limits['maxGuideMaterializations'])
        if not active and not discoverable:
            break
        if not last['work'].get('solverCalls',0) and not last['expandedPrefixes'] and dfs['nextFrame']==before_frames and not active:
            break
    if time.monotonic()>=deadline:termination='deadline'
    elif calls>=options['max_solver_calls']:termination='numerical_call_limit'
    elif expanded>=cfg['maxPrefixes']:termination='prefix_limit'
    report=dict(last or {})
    report.update(status='feasible' if state['best'] else 'cancelled' if termination=='cancelled' else 'unknown',
        termination=termination,best=state['best'],searchState=state,effectiveRequest=request,taskGoal=task,
        work=dict(solverCalls=calls,workerLaunches=worker_calls,feasibleSeedReturns=seed_returns,
                  maxSolverCalls=options['max_solver_calls']),expandedPrefixes=expanded,
        elapsedSeconds=time.monotonic()-started,compiler=counted.summary(),backend='event_goals_v1',
        routeGeneration=dict(backend='event_goals_v1',events=len(state['graph']['events']),**used,
            coverage=state['domain']['coverage'],completeEnumeration=False),routeAttempts=allocations,
        routeWorkUnits=sum(len(v) for v in state['dfs']['goalOwners'].values()) if state['dfs'] else 0,
        coverageProven=False,objectiveOptimalityProven=False,minimumTicksProven=False,
        resume=dict(exact=saved is not None,session=state['sessions']))
    if anchor_policy is not None and state['dfs']:
        from parkour_solver.observability.turns import summary
        report['anchorSearch']=dict(policy=anchor_policy,guidePlan=adapter.guide_plan,
            hardLatches=state['dfs']['hardLatches'],latchRevision=state['dfs']['latchRevision'],
            transferTimeBounds=state['dfs'].get('transferTimeBounds',{}),
            comparisonWork=state['dfs'].get('comparisonWork',{}),comparisonPolicy=COMPARISON_POLICY_VERSION,
            nodes=state['dfs']['nodeResults'],observations=summary(state['dfs']['observations']),
            runDeferrals=len(state['dfs']['minimumRunExclusions']),
            runRestorations=sum(r.get('restored',False) for r in state['dfs']['minimumRunExclusions']))
        owners={f['id']:f for group in ('frames','stack','suspended','refinements','goalQueue')
                for f in state['dfs'].get(group,[])}
        report['anchorSearch']['deferredNodes']=sum(len(f['generation'].get('continuation',{}).get('deferredNodes',[]))
                                                   for f in owners.values())
        report['backend']='anchor_goals_v1'
        report['routeGeneration']['backend']='anchor_goals_v1'
    if options.get('on_progress'):options['on_progress'](state)
    return report
