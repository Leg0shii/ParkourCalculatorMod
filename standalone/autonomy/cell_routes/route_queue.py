"""Allocate existing DFS work across automatically discovered spatial routes."""

import copy
import time

from ..candidate_comparison import POLICY_VERSION as COMPARISON_POLICY_VERSION

from ..block_search import prepare_scene
from ..movement_search import movement_settings
from ..ranking import candidate_rank
from ..solve_spec import digest
from .comparison_goals import goal_spec
from .geometry import decompose
from .regions import annotate_normal_proposals, build_route_graph
from .routes import Corridor, enumerate_routes
from .regions import geometry_key
from .minimum_runs import POLICY_VERSION


def collision_request(request, compiler, seconds):
    """Reuse the compiled horizon bound for finite virtual-finish scene queries."""
    scene=prepare_scene(request)
    from math import hypot
    sample=compiler.compile(dict(scene,sharedMovement=True),dict(grounded=[True,True,False],jump=[False,True,False]),seconds)
    speed=hypot(*scene['initial']['velocity'][::2]); radius=0.
    for _ in range(movement_settings(scene)['maxInputs']):
        movement=speed+max(sample['magnitude'])
        radius+=movement
        speed=movement*max(sample['friction'])
    result=copy.deepcopy(request)
    bounds=scene['collisionScene']['bounds'][:]
    for axis in (0,2):
        bounds[axis]=min(bounds[axis],scene['initial']['position'][axis]-radius-1.)
        bounds[axis+3]=max(bounds[axis+3],scene['initial']['position'][axis]+radius+1.)
    result['geometryBounds']=bounds
    if 'collisionScene' in result:
        result['collisionScene']['bounds']=bounds[:]
    return result


def search_routes(request, compiler, *, task_goal=None, spatial_settings=None, **options):
    from .checkpoint_search import find_checkpoint_route
    started=time.monotonic(); deadline=started+options['seconds']
    if options.get('checkpoints') or options.get('opportunities') is not None or options.get('terminal_checkpoint') is not None or options.get('spatial_goals'):
        raise ValueError('Automatic routes derive their own stages; use an explicit corridor for authored clocks/goals')
    spatial=dict(max_routes=50,max_expansions=5000,max_steps=64,max_visits=2)
    if spatial_settings:
        if set(spatial_settings)-set(spatial):
            raise ValueError('Unsupported automatic spatial settings')
        spatial.update(spatial_settings)
    scene=prepare_scene(request)
    task_goal=goal_spec(scene,task_goal if task_goal is not None else options.get('spatial_goal'))
    if task_goal['kind']=='landing_height_region':
        request=collision_request(request,compiler,min(options['candidate_seconds'],max(.001,deadline-time.monotonic())))
        scene=prepare_scene(request)
    cfg=movement_settings(scene)
    d=decompose(scene,deadline=deadline)
    graph=build_route_graph(scene,d,task_goal,deadline=deadline)
    graph=annotate_normal_proposals(scene,graph,compiler,seconds=max(.001,min(options['score_seconds'],deadline-time.monotonic())))
    policy=options.get('program_filter')
    saved=options.pop('resume_state',None)
    if saved is not None and options.get('initial_programs') is None:
        options['initial_programs']=saved.get('initialRoots')
    context=digest(dict(version=1,comparisonPolicy=COMPARISON_POLICY_VERSION,allocationPolicy=2,routeStagePolicy=2,routeCommitPolicy=1,routeProposalPolicy=1,movementGenerationPolicy=3,constraintGenerationPolicy=7,
        graph=graph.context,task=task_goal,spatial=spatial,
        scene={k:v for k,v in scene.items() if k!='movementSearch'},
        domain={k:v for k,v in cfg.items() if k not in ('maxPrefixes','maxQueuedPrefixes','maxChoicesPerType','expansionsPerProposal')},
        roots=options.get('initial_programs'),filter=getattr(policy,'checkpoint_identity',None),
        minimumRuns=POLICY_VERSION if options['minimum_run_pruning'] else None))
    if saved is not None and (saved.get('version')!=1 or saved.get('kind')!='semantic_route_queue' or saved.get('context')!=context):
        raise ValueError('Incompatible automatic route continuation')
    if saved is not None:
        state=copy.deepcopy(saved)
        if state['best'] is not None:
            from ..witness import replay_witness
            if not replay_witness(scene,state['best'],compiler)['passed']:
                raise ValueError('Saved automatic incumbent no longer passes independent replay')
        if len(state['catalogueVariants']) < spatial['max_routes'] and state['routeGeneration']['termination'] in ('expansion_limit','deadline'):
            routes,report=enumerate_routes(scene,d,route_graph=graph,deadline=deadline,
                cancel_event=options.get('cancel_event'),continuation=state['routeGeneration']['continuation'],**spatial)
            for route in routes:
                family=geometry_key(route.semantic_key)
                previous=set(state['catalogueVariants'].get(family,[]))
                variants=[v for v in route.route_variants if geometry_key(v) not in previous]
                if not variants:
                    continue
                # Expanding the language of a running DFS would invalidate its
                # exact context. Give newly discovered variants a separate work
                # owner under the same semantic family, retaining the old DFS.
                route=copy.deepcopy(route)
                route.id=len(state['routes']);route.route_variants=variants;route.variants=len(variants)
                state['routes'].append(route.to_dict(include_graph=False));state['queue'].append(route.id)
                state['catalogueVariants'].setdefault(family,[]).extend(geometry_key(v) for v in variants)
            state['routeGeneration']=report
        if options.get('retry_unknown'):
            state['queue'].extend(i for i in state.get('unknownRoutes',[]) if i not in state['queue'])
            state['unknownRoutes']=[]
    else:
        routes,report=enumerate_routes(scene,d,route_graph=graph,deadline=deadline,
                                      cancel_event=options.get('cancel_event'),**spatial)
        state=dict(version=1,kind='semantic_route_queue',context=context,routes=[r.to_dict(include_graph=False) for r in routes],
            routeGeneration=report,queue=list(range(len(routes))),active=None,children={},best=None,sessions=0,
            graph=graph.to_dict(),
            initialRoots=copy.deepcopy(options.get('initial_programs')),
            catalogueVariants={geometry_key(r.semantic_key):[geometry_key(v) for v in r.route_variants] for r in routes})
    state['sessions']+=1
    if state['active'] is not None:
        # An interrupted DFS has one execution owner, ahead of untouched routes.
        if state['active'] not in state['queue']:
            state['queue'].insert(0,state['active'])
        state['active']=None
    calls=0; expanded=0; attempts=[]; termination='exhausted'
    on_progress=options.pop('on_progress',None)
    maximum_calls=options['max_solver_calls']
    while state['queue']:
        if time.monotonic() >= deadline:
            termination='deadline'; break
        if options.get('cancel_event') is not None and options['cancel_event'].is_set():
            termination='cancelled'; break
        if calls>=maximum_calls:
            termination='numerical_call_limit'; break
        if expanded>=cfg['maxPrefixes']:
            termination='prefix_limit'; break
        index=state['queue'].pop(0)
        state['active']=index
        child=state['children'].get(str(index))
        before_depth=max((f.get('index',0) for f in child.get('stack',[])),default=0) if child else 0
        prior_calls=child.get('cumulativeCalls',0) if child else 0
        prior_expanded=child.get('expanded',0) if child else 0
        def progress(value):
            state['children'][str(index)]=copy.deepcopy(value)
            if on_progress:
                on_progress(copy.deepcopy(state))
        local=dict(options)
        local.update(seconds=max(.001,min(options['score_seconds'],deadline-time.monotonic())),
            max_solver_calls=min(options['score_call_limit'],maximum_calls-calls),resume_state=child,on_progress=progress)
        local_request=copy.deepcopy(request)
        # Give the persistent generator a full configured choice batch. A
        # proposal-yield interval can end before any allowed setup continuation
        # becomes a landing proposal; it is not a per-route catalogue cap.
        prefix_slice=max(cfg['expansionsPerProposal'],cfg['maxChoicesPerType'])
        local_request['movementSearch']=dict(cfg,maxPrefixes=min(prefix_slice,cfg['maxPrefixes']-expanded))
        corridor=Corridor(**dict(state['routes'][index],region_graph=state['graph']))
        result=find_checkpoint_route(local_request,compiler,corridor=corridor,**local)
        attempts.append(dict(route=index,status=result['status'],termination=result['termination'],work=result['work']))
        if 'searchState' not in result:
            state['active']=None
            state['queue'].append(index)
            termination=result['termination']
            break
        state['children'][str(index)]=result['searchState']
        calls+=result['searchState']['cumulativeCalls']-prior_calls
        used=result['searchState']['expanded']-prior_expanded
        expanded+=used
        best=result['best']
        if best is not None and (state['best'] is None or candidate_rank(best,scene['objective'])<candidate_rank(state['best'],scene['objective'])):
            state['best']=copy.deepcopy(best)
            state['best']['spatialRoute']=index
        state['active']=None
        if result['searchState']['stack'] or result['searchState']['refinements']:
            after_depth=max((f.get('index',0) for f in result['searchState']['stack']),default=0)
            advanced=after_depth>before_depth
            state['queue'].insert(0,index) if advanced else state['queue'].append(index)
            state.setdefault('allocations',[]).append(dict(route=index,beforeDepth=before_depth,afterDepth=after_depth,
                continuation='committed_child' if advanced else 'yield_to_other_routes'))
        elif result['searchState']['suspended']:
            state.setdefault('unknownRoutes',[])
            if index not in state['unknownRoutes']:
                state['unknownRoutes'].append(index)
        if on_progress:
            on_progress(copy.deepcopy(state))
        if result['termination'] in ('error','cancelled'):
            termination=result['termination']; break
    if not state['queue'] and state.get('unknownRoutes'):
        termination='numerical_unknown'
    if on_progress:
        on_progress(copy.deepcopy(state))
    return dict(status='feasible' if state['best'] is not None else 'cancelled' if termination=='cancelled' else 'error' if termination=='error' else 'unknown',
        termination=termination,best=state['best'],searchState=state,routeGeneration=state['routeGeneration'],
        routeAttempts=attempts,windows=[],finalAttempts=[],corridor=None,expandedPrefixes=expanded,
        work=dict(solverCalls=calls,maxSolverCalls=maximum_calls),elapsedSeconds=time.monotonic()-started,
        effectiveRequest=request,taskGoal=task_goal,comparisonPolicy='automatic_route_stage_dfs',
        resume=dict(exact=saved is not None,session=state['sessions']),objectiveOptimalityProven=False,
        minimumTicksProven=False,coverageProven=False,routeWorkUnits=len(state['routes']))
