"""Allocate one existing movement DFS over lazy contact/guide requests."""

import copy
import time

from ..block_search import prepare_scene
from ..movement_search import movement_settings
from ..solve_spec import digest
from .comparison_goals import goal_spec
from .event_routes import RouteDomain, build_route_domain, build_event_graph
from .event_adapter import EventGuideAdapter
from .local import CountingCompiler, SearchWork
from .route_queue import collision_request


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


def search_event_routes(request, compiler, *, task_goal=None, discovery=None, **options):
    from .checkpoint_search import _motion_bound
    from .depth_first import search_goals
    started=time.monotonic(); deadline=started+options['seconds']
    limits=discovery_settings(discovery)
    if any(options.get(k) for k in ('checkpoints','opportunities','spatial_goals')) or options.get('terminal_checkpoint') is not None:
        raise ValueError('Automatic event discovery derives its own contact/finish goals')
    scene=prepare_scene(request)
    task=goal_spec(scene,task_goal if task_goal is not None else options.get('spatial_goal'))
    saved=options.get('resume_state')
    policy=options.get('program_filter')
    roots=options.get('initial_programs')
    if roots is None and saved is not None:
        roots=saved.get('initialRoots')
    cfg=movement_settings(scene)
    cfg['types']=[t for t in cfg['types'] if t in ('normal','ceiling','stepping')]
    scene['movementSearch']=cfg
    context=digest(dict(policy=3,constraintPolicy=7,scene={k:v for k,v in scene.items() if k!='movementSearch'},
        task=task,roots=roots,filter=getattr(policy,'checkpoint_identity',None),
        minimumRuns=options['minimum_run_pruning'],
        domain={k:v for k,v in cfg.items() if k not in
            ('maxPrefixes','maxQueuedPrefixes','maxChoicesPerType','expansionsPerProposal')}))
    if saved is not None and (saved.get('schema')!=1 or saved.get('kind')!='event_route_search' or saved.get('context')!=context):
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
        state=dict(schema=1,kind='event_route_search',context=context,domain=data,graph=build_event_graph(data),
            dfs=None,initialRoots=copy.deepcopy(roots),effectiveRequest=request,best=None,sessions=0,allocations=[])
    state['sessions']+=1
    motion=state.get('motion')
    if motion is None:
        motion=_motion_bound(scene,counted,max(.001,min(options['candidate_seconds'],deadline-time.monotonic())))
        state['motion']=motion
    adapter=EventGuideAdapter(state['domain'],state['graph'],motion=motion,compiler=counted,
                              candidate_seconds=options['candidate_seconds'])
    calls=0; expanded=0; allocations=[]
    used=dict(expansions=0,materializations=0)
    termination='exhausted'
    last=None
    def accepted_program(program):
        from ..solve_spec import motion_record
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
        local_deadline=min(deadline,time.monotonic()+options['score_seconds'])
        work=SearchWork(min(options['score_call_limit'],options['max_solver_calls']-calls),deadline=local_deadline)
        dfs=state['dfs']
        before_depth=max((f['index'] for f in dfs.get('stack',[])),default=-1) if dfs else -1
        before_frames=dfs['nextFrame'] if dfs else 0
        def publish(value):
            state['dfs']=value
            if options.get('on_progress'):
                options['on_progress'](state)
        last=search_goals(scene,counted,adapter,work,motion,
            cfg=dict(cfg,maxPrefixes=cfg['maxPrefixes']-expanded),plans=[],goals=None,default_goal=task,terminal_plan=None,
            accepted_program=accepted_program,program_filter=policy,roots=roots,
            resume_state=dfs,retry_unknown=options['retry_unknown'],deadline=local_deadline,started=started,
            candidate_seconds=options['candidate_seconds'],score_seconds=options['score_seconds'],
            score_calls=options['score_call_limit'],final_calls=options['final_call_limit'],batch_size=options['max_candidates'],
            python_executable=options.get('python_executable'),cancel_event=options.get('cancel_event'),
            on_progress=publish,on_window=options.get('on_window'),time_margin=options['time_margin'],
            progress_margin=options['progress_margin'] if options['progress_margin'] is not None else .25,
            route_report=None,window_plan=None,comparison_policy=options['comparison_policy'],
            minimum_run_pruning=options['minimum_run_pruning'])
        state['dfs']=last['searchState']; state['best']=last['best']
        dfs=state['dfs']
        for k in used:used[k]+=dfs['discoveryWork'][k]
        calls+=last['work'].get('solverCalls',0);expanded+=last['expandedPrefixes']
        after_depth=max((f['index'] for f in dfs['stack']),default=-1)
        advanced=after_depth>before_depth
        allocation=dict(calls=last['work'].get('solverCalls',0),expanded=last['expandedPrefixes'],
            termination=last['termination'],beforeDepth=before_depth,afterDepth=after_depth,advanced=advanced)
        allocations.append(allocation);state['allocations'].append(allocation)
        if not advanced and dfs['stack'] and dfs['goalQueue']:
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
        work=dict(solverCalls=calls,maxSolverCalls=options['max_solver_calls']),expandedPrefixes=expanded,
        elapsedSeconds=time.monotonic()-started,compiler=counted.summary(),backend='event_goals_v1',
        routeGeneration=dict(backend='event_goals_v1',events=len(state['graph']['events']),**used,
            coverage=state['domain']['coverage'],completeEnumeration=False),routeAttempts=allocations,
        routeWorkUnits=sum(len(v) for v in state['dfs']['goalOwners'].values()) if state['dfs'] else 0,
        coverageProven=False,objectiveOptimalityProven=False,minimumTicksProven=False,
        resume=dict(exact=saved is not None,session=state['sessions']))
    if options.get('on_progress'):options['on_progress'](state)
    return report
