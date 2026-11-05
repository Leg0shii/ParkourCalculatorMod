import copy
import math
import time

from standalone.autonomy.interactions import InteractionGate
from standalone.autonomy.movement_composition import append_choice, boundary_state, empty_program, program_physics_key, program_search_key, truncate_program
from standalone.autonomy.movement_generation import generate_choices
from standalone.autonomy.movement_search import iter_movement_programs, screen_program
from standalone.autonomy.prefix_viability import prefix_viability
from standalone.autonomy.solve_spec import digest, motion_record, processing_key
from .checkpoints import Checkpoint, retain_prefix
from .comparison_goals import goal_spec, goal_key, region_arrival, _landing_prefixes
from standalone.autonomy.terminal_goals import KIND, bind_goal, satisfied
from .minimum_runs import POLICY_VERSION, latest_jump, remember, exclusion
from .anchor_policy import semantic_settings


def candidate_key(program):
    physical = {k:v for k,v in program.items() if k != 'repairState'}
    return digest(program_search_key(physical))


def arrival_key(record):
    motion = motion_record(record['program']) or dict(jumps=0, runs=0)
    return (record['arrival'], -motion['jumps'], motion['runs'], record['id'])


def decision_family(program, parents):
    start = min(p['profile']['airtime'] for p in parents)
    p = program['profile']
    jump = next((t for t in range(start,p['airtime']) if p['jump'][t] and p['grounded'][t]),None)
    events = [(e['offset'],e['axis'],e.get('role'),e.get('box')) for e in p.get('contactEvents',[])
        if e['offset']>=start and not (e['axis']=='Y' and p['grounded'][e['offset']] and not p['jump'][e['offset']])]
    events += [(e['input'],e['case'],e.get('box')) for e in p.get('caseEvents',[]) if e['input']>=start]
    first = min(events,key=lambda e:e[0]) if events else None
    return digest([jump,first])


def commit_arrival(program, best, *, source_program=None):
    best = copy.deepcopy(best)
    n = program['profile']['airtime']
    source = source_program if source_program is not None else program
    def row_key(row):
        return tuple(row.get(k) for k in ('mode','t1','t2','op','cmp','rhs'))
    dropped = {row_key(r) for r, owner in zip(source['profile']['positionConstraints'],
        source['profile'].get('constraintOwners',[0]*len(source['profile']['positionConstraints'])))
        if owner>n and source.get('committedThrough',0)<owner}
    best['plan']['walls'] = [r for r in best['plan']['walls'] if r['t1']<=n and
        (r.get('t2') is None or r['t2']<=n) and row_key(r) not in dropped]
    result = retain_prefix(program, best)
    result['profile']['constraintOwners'] = program['profile'].get(
        'constraintOwners', [0]*len(program['profile']['positionConstraints'])) + [0]*(
            len(result['profile']['positionConstraints'])-len(program['profile']['positionConstraints']))
    result.pop('repairState', None)
    result['guideSeedYaw'] = result['guideSeedYaw'][:n]
    return result


def search_goals(scene, compiler, adapter, work, motion, *, cfg, plans, goals, default_goal, terminal_plan,
                 accepted_program, program_filter, roots, resume_state, retry_unknown, deadline, started,
                 candidate_seconds, score_seconds, score_calls, final_calls, batch_size, python_executable,
                 cancel_event, on_progress, on_window, time_margin, progress_margin, route_report,
                 window_plan, comparison_policy, minimum_run_pruning):
    from .checkpoint_search import evaluate_program
    from .checkpoint_experiment import replay_witness

    anchor_route = hasattr(adapter, 'anchor_policy')
    event_route = getattr(adapter, 'search_contract', None) == 'event_goals_v1'
    typed_route = event_route or hasattr(adapter,'for_goal')
    goal_list = [] if event_route else [goal_spec(scene,g) for g in adapter.goals()] if typed_route else (
        [goal_spec(scene, g) for g in goals] if goals else ([] if terminal_plan is not None else [default_goal]))
    # Repeated clock observations of the same goal are not separate decisions.
    if not typed_route:
        goal_list = [g for i, g in enumerate(goal_list) if i == 0 or g != goal_list[i-1]]
    seed = empty_program(scene['initial'], threshold=0.)
    seed['terminalSupport'] = scene['startBlock']
    if not typed_route and not goals and terminal_plan is not None and hasattr(program_filter,'remaining'):
        goal_list = [goal_spec(scene,dict(kind='landing',block=b)) for b in program_filter.remaining(seed)]
    final_goal = dict(kind='landing',block=scene['goalBlock'])
    if not typed_route and terminal_plan is None and (not goal_list or goal_list[-1] != final_goal and goal_list[-1]['kind']!=KIND):
        goal_list.append(final_goal)
    roots = copy.deepcopy(roots if roots is not None else
        resume_state.get('initialRoots', [seed]) if resume_state else [seed])
    if not roots or any(p['initial'] != seed['initial'] or p.get('threshold', .005) != 0. for p in roots):
        raise ValueError('DFS roots must preserve original state and zero inertia thresholds')
    if any(not accepted_program(p) for p in roots):
        raise ValueError('DFS roots violate the decision domain')
    schema = 8 if anchor_route else 7 if event_route else 6 if typed_route else 5
    context = digest(dict(version=schema, kind='ordered_goal_dfs', movementGenerationPolicy=3,constraintGenerationPolicy=7,
        scene={k:v for k,v in scene.items() if k!='movementSearch'},
        domain={k:v for k,v in cfg.items() if k not in ('maxPrefixes','maxQueuedPrefixes','maxChoicesPerType','expansionsPerProposal')},
        roots=[program_search_key(p) for p in roots], goals=goal_list,
        terminal=terminal_plan.to_dict() if terminal_plan else None, corridor=adapter.corridor.to_dict(),
        windows=window_plan, filter=getattr(program_filter, 'checkpoint_identity', None),
        margins=[time_margin,progress_margin], physics=motion,
        **({'eventStagePolicy':4 if anchor_route else 3} if event_route else {}),
        **({'anchorPolicy':semantic_settings(adapter.anchor_policy),'guidePlan':adapter.guide_plan['id']} if anchor_route else {}),
        minimumRunPolicy=POLICY_VERSION if minimum_run_pruning else None,
        **({'routeStagePolicy':2,'routeCommitPolicy':1,'routeProposalPolicy':1} if typed_route else {})))
    if resume_state is not None and (resume_state.get('version') != schema or
            resume_state.get('kind') != 'ordered_goal_dfs' or resume_state.get('context') != context):
        raise ValueError('Incompatible DFS continuation; old checkpoint queues require explicit reconstruction')
    state = copy.deepcopy(resume_state) if resume_state is not None else dict(version=schema, kind='ordered_goal_dfs',
        context=context, initialRoots=roots, stack=[], suspended=[], refinements=[], frames=[], transitions=[],
        windows=[], finalAttempts=[], best=None, cumulativeCalls=0, session=0, expanded=0, nextFrame=0)
    if event_route:
        state.setdefault('goalQueue', [])
        state.setdefault('goalOwners', {})
        state.setdefault('discovery', {})
    if anchor_route:
        state.setdefault('hardLatches', {})
        state.setdefault('latchRevision', 0)
        state.setdefault('nodeResults', {})
        state.setdefault('observations', dict(enabled=adapter.anchor_policy['observability'], decisions={}))
        state['observations']['enabled']=adapter.anchor_policy['observability']
    state['session'] += 1
    prior_calls, initial_expanded = state['cumulativeCalls'], state['expanded']
    if resume_state and state['best'] is not None and not replay_witness(scene,state['best'],compiler)['passed']:
        raise ValueError('Saved DFS incumbent no longer passes independent replay')
    gate = InteractionGate(scene, compiler, seconds=candidate_seconds)
    if event_route:
        gate.cache = adapter.interaction_cache
    state.setdefault('timeBoundExclusions',[])
    state.setdefault('minimumRunExclusions',[])
    excluded_runs={(r['frame'],r['candidate']) for r in state['minimumRunExclusions']}
    viability_counts = dict(checked=0, excluded=0)
    termination = 'exhausted'

    def publish():
        state['cumulativeCalls'] = prior_calls+work.counts['solverCalls']
        if on_progress:
            on_progress(state)

    def transition(kind, frame, **fields):
        state['transitions'].append(dict(kind=kind, frame=frame['id'], depth=frame['index'], session=state['session'], elapsedSeconds=time.monotonic()-started, **fields))
        # A typed arrival's tried flag and child execution owner are one
        # checkpoint transaction. The child's open/backtrack publishes both.
        if not typed_route or kind!='choose':
            publish()

    stage_views = {}

    def frame(index, parents, parent=None, cursor=None):
        if event_route:
            cursor = cursor if cursor is not None else adapter.initial_cursor(parents)
            owner = digest(dict(parents=[candidate_key(p) for p in parents], cursor=cursor,
                physics=motion, domain=adapter.data['domainId']))
            if owner in state['goalOwners']:
                return None
            discovery = dict(owner=owner,index=index,parents=copy.deepcopy(parents),parent=parent,
                cursor=cursor,continuation=None,exhausted=False)
            state['discovery'][owner] = discovery
            state['goalOwners'][owner] = []
            if parent is not None:
                state['pendingGoalOwner'] = owner
            expand_discovery(discovery)
            ready = next((f for f in state['goalQueue'] if f['owner']==owner),None)
            if ready is not None:
                f = ready
                state['goalQueue'].remove(f)
                state['stack'].append(f)
                if state.get('pendingGoalOwner') == owner:
                    state.pop('pendingGoalOwner')
                transition('open',f,goal=f['goal'])
                return f
            publish()
            return None
        f = make_frame(index,parents,parent,goal_list[index] if index<len(goal_list) else None)
        state['stack'].append(f)
        transition('open',f,goal=f['goal'])
        return f

    def make_frame(index, parents, parent, goal, **fields):
        f = dict(id=state['nextFrame'], index=index, parents=copy.deepcopy(parents), parent=parent,
            goal=goal, phase='generate', generation={}, ready=[], records=[], inFlight=None,
            sourceExhausted=False, batches=0, **fields)
        state['nextFrame'] += 1
        return f

    def expand_discovery(discovery):
        settings=getattr(adapter,'discovery_settings',{})
        used=state.setdefault('discoveryWork',dict(expansions=0,materializations=0))
        requests, continuation, report=adapter.next_goals(discovery['cursor'],discovery['parents'],
            discovery['continuation'],budget=dict(check=work.check,
                expansions=max(0,settings.get('maxGraphExpansions',5000)-used['expansions']),
                materializations=max(0,settings.get('maxGuideMaterializations',50)-used['materializations'])))
        used['expansions']+=report['expansions'];used['materializations']+=report['materializations']
        discovery.update(continuation=continuation,exhausted=continuation['exhausted'])
        for request in requests:
            if request.get('localSetupAlias'):
                # Current-support hops/runs remain in the movement source for
                # every next goal. They do not need an extra comparison frame.
                state.setdefault('localSetupAliases',[]).append(dict(owner=discovery['owner'],request=request['id']))
                continue
            f=make_frame(discovery['index'],discovery['parents'],discovery['parent'],request['goal'],
                goalRequest=request,semanticCursor=copy.deepcopy(discovery['cursor']),owner=discovery['owner'])
            state['goalOwners'][discovery['owner']].append(f['id'])
            state['goalQueue'].append(f)

    def stage_adapter(f):
        if not typed_route:
            return adapter
        key=f['id'] if event_route else f['index']
        if key not in stage_views:
            stage_views[key] = (adapter.for_goal(f['goalRequest'],f['semanticCursor']) if event_route else adapter.for_goal(f['index']))
            if event_route:
                f['guide'] = stage_views[key].guide
                f['scoreContextKey'] = stage_views[key].score_context
        return stage_views[key]

    def span_info(f):
        view=stage_adapter(f)
        return view.cursor['anchorIndex'],view.cursor['spanStart']

    def priority(program,f,identity=''):
        if not anchor_route:return processing_key(program,identity)
        from .anchor_policy import priority as span_priority
        return span_priority(program,span_info(f)[1],program.get('priorityTurnaround',False),identity)

    def record_priority(record,f):
        return priority(record['program'],f,record['id']) if anchor_route else arrival_key(record)

    def latch_key(f):
        span,_=span_info(f)
        names=adapter.guide_plan['anchors']
        return names[span]+'→'+names[span+1]

    def hard_bound(program,f):
        if not anchor_route or latch_key(f) not in state['hardLatches']:return None
        from .anchor_policy import transfer_structure
        _,start=span_info(f)
        names=adapter.guide_plan['anchors'];i=span_info(f)[0]
        completed=program.get('terminalSupport') in f['goal'].get('blocks',[f['goal']['block']])
        transfer=transfer_structure(scene,program,start,names[i],names[i+1],complete=completed)
        if not transfer['valid']:
            return dict(reason='global_single_jump_latch',span=latch_key(f),revision=state['latchRevision'],
                        transfer=transfer)
        return None

    def score_key(program,f):
        return digest([candidate_key(program),stage_adapter(f).score_context]) if event_route else candidate_key(program)

    def configure(ctx, f):
        stage_adapter(f).context(ctx)
        gate.context(ctx)

    def time_bound(program):
        if terminal_plan is None and state['best'] is not None and program['profile']['airtime']>state['best']['plan']['ticks']:
            return dict(reason='elapsed_inputs_exceed_incumbent_finish',elapsedInputs=program['profile']['airtime'],
                incumbentTick=state['best']['plan']['ticks'])
        return None

    def run_bound(program, f):
        if not minimum_run_pruning or f is None:return None
        if anchor_route and (latch_key(f) not in state['hardLatches'] or program.get('priorityTurnaround',False) or
                (program.get('restoredRun') or {}).get('frame')==f['id']):return None
        proof=exclusion(f.get('minimumRuns',{}),latest_jump(program,scene))
        if proof is not None:
            identity=candidate_key(program);key=(f['id'],identity)
            if key not in excluded_runs:
                excluded_runs.add(key)
                saved={k:copy.deepcopy(v) for k,v in program.items() if anchor_route or k!='repairState'}
                state['minimumRunExclusions'].append(dict(frame=f['id'],candidate=identity,proof=proof,program=saved,
                    **(dict(span=span_info(f)[0],decision=program.get('decisionNode'),isTurnaround=False,
                            latchRevision=state['latchRevision']) if anchor_route else {})))
        return proof

    def eligible(program, f=None):
        proof=time_bound(program)
        if proof is not None:
            state['timeBoundExclusions'].append(dict(id=candidate_key(program),proof=proof))
            return False
        return (not (anchor_route and hard_bound(program,f)) and run_bound(program,f) is None and accepted_program(program) and
                (not typed_route or stage_adapter(f).program_allowed(program)))

    def screen(program, f=None):
        bounded=program
        if anchor_route and f is not None:
            arrivals=[p for b in f['goal'].get('blocks',[f['goal']['block']])
                      for p in _landing_prefixes(scene,program,b)]
            if arrivals:bounded=min(arrivals,key=lambda p:p['profile']['airtime'])
        bound=time_bound(bounded) or (hard_bound(bounded,f) if anchor_route else None) or run_bound(bounded,f)
        if bound is not None:
            return bound
        if event_route:
            g=f['goal']
            required=[g['block']] if g['kind']=='landing' and bounded['terminalSupport'] not in g.get('blocks',[g['block']]) else []
        elif typed_route or goal_list and goal_list[-1]['kind']==KIND and not hasattr(program_filter,'remaining'):
            first = f['index'] if f is not None else 0
            if first<len(goal_list) and goal_list[first]['kind']=='landing' and program['terminalSupport'] in goal_list[first].get('blocks',[goal_list[first]['block']]):
                first += 1
            required = [g['block'] for g in goal_list[first:] if g['kind']=='landing']
        elif hasattr(program_filter,'remaining'):
            required = program_filter.remaining(program)
        else:
            required = [] if terminal_plan is not None else [scene['goalBlock']]
        proof = prefix_viability(scene, bounded, required_contacts=required,
            terminal_tick=terminal_plan.tick if terminal_plan else cfg['maxInputs'], enabled_types=cfg['types'])
        viability_counts['checked'] += 1
        if proof['dead']:
            viability_counts['excluded'] += 1
            return proof
        # Check the full proposal, including events beyond a diagnostic projection.
        return gate.required(program) or screen_program(scene, program, compiler,
            max(.001,min(candidate_seconds,deadline-time.monotonic())))

    def numerical_pending(record):
        repair = record.get('program', {}).get('repairState', {})
        return bool(repair.get('pending') or repair.get('inFlight') or
            repair.get('constraintGeneration') and not repair['constraintGeneration'].get('exhausted'))

    def terminal(f):
        return not event_route and f['index'] == len(goal_list)

    def final_frame(f):
        return f['goalRequest']['terminal'] if event_route else terminal(f) or terminal_plan is None and f['index']==len(goal_list)-1

    def evaluate_node(program, nodes, unused, f):
        from .node_feasibility import solve_due
        from .anchor_guides import classify
        from .turn_observability import record as observe, replay
        active=stage_adapter(f)
        span,start=span_info(f)
        identity=program['decisionNode']
        depth=program['decisionDepth']
        if not solve_due(program,adapter.anchor_policy['solveEveryDecisionLevels']):
            return dict(status='untested',program=program)
        saved=nodes.get(identity)
        if saved and saved['status']=='oracle_rejected':
            return dict(status='oracle_rejected',program=program)
        if saved and saved['status']=='feasible':
            # The saved program carries physical rows and a free-yaw seed.
            return dict(status='feasible',program=copy.deepcopy(saved['program']))
        if saved and saved.get('repairState') is not None:
            program['repairState']=saved['repairState']
        policy=dict(purpose='node',stopOnFeasible=True,rankEqualCost=False,oracleRejection=True)
        t=time.monotonic()
        def progress(repair):
            program['repairState']=repair
            nodes[identity]=dict(status='suspended',parent=program.get('decisionParent'),repairState=repair)
            publish()
        result=evaluate_program(scene,program,compiler,active.for_node(),work,motion,
            seconds=max(.001,min(score_seconds,deadline-time.monotonic())),candidate_seconds=candidate_seconds,
            solver_calls=score_calls,python_executable=python_executable,cancel_event=cancel_event,
            partial_corridor=True,guide_objective=active.objective,first_witness=True,
            solve_policy=policy,on_progress=progress,retry_unresolved=False)
        query=result.get('nodeFeasibility',dict(status='feasible' if result['best'] else 'suspended',model=identity))
        status=query['status']
        observe(state['observations'],adapter.guide_plan,program,span=span,start=start,
                model=query['model'],status=status,elapsed=time.monotonic()-t,metrics=result['work'],
                service=dict(latchRevision=state['latchRevision'],hardLatched=latch_key(f) in state['hardLatches'],
                    runDeferralEnabled=minimum_run_pruning and not program.get('priorityTurnaround',False) and latch_key(f) in state['hardLatches']))
        if result['best'] is not None:
            # Retain selected physical walls; never endpoint/velocity/yaw equalities.
            updated=commit_arrival(program,result['best'])
            for k in ('decisionNode','decisionParent','decisionDepth','priorityTurnaround','restoredRun'):
                if k in program:updated[k]=program[k]
            updated['lastSolvedDepth']=depth
            point=result['best']['candidate']['positions'][-1]
            updated.update(decisionPoint=point,decisionPointTick=program['profile']['airtime'],
                decisionPointSource='validated_node',decisionTurnaround=classify(adapter.guide_plan,span,point)['isTurnaround'])
            ancestor=program.get('decisionParent')
            while ancestor:
                event=state['observations']['decisions'].get(ancestor)
                if event is None:break
                tick=event['pointTick']
                replay(state['observations'],adapter.guide_plan,ancestor,event['span'],
                       result['best']['candidate']['positions'][tick],query['model'])
                ancestor=event['parent']
            program=updated
        elif result.get('repairState') is not None:
            program['repairState']=result['repairState']
        nodes[identity]=dict(status=status,parent=program.get('decisionParent'),query=query,
            depth=depth,lastSolvedDepth=program.get('lastSolvedDepth',0),program=copy.deepcopy(program))
        state['nodeResults'][identity]=dict(query,parent=program.get('decisionParent'),depth=depth)
        publish()
        return dict(status=status,program=program)

    def generate(f):
        if f['goal'] and f['goal']['kind']==KIND and not f.get('directDone'):
            f.setdefault('directRoot',0)
            f.setdefault('directCursor',0)
            while f['directRoot']<len(f['parents']):
                parent=f['parents'][f['directRoot']]
                existing=bind_goal(parent,f['goal'],scene.get('supportClearance',1e-6))
                if existing.get('terminalGoal')==f['goal'] and eligible(existing,f):
                    f['ready'].append(existing)
                remaining=cfg['maxInputs']-parent['profile']['airtime']
                if remaining>0:
                    ctx=dict(mode='open',scene=scene,program=parent,state=boundary_state(parent),maxInputs=remaining,
                        terminalGoal=f['goal'],threshold=0.,checkBudget=work.check)
                    configure(ctx,f)
                    source=iter(generate_choices('normal',ctx))
                    try:
                        for _ in range(f['directCursor']):next(source)
                        for choice in source:
                            child=append_choice(parent,choice,max_inputs=cfg['maxInputs'])
                            if child is not None:
                                child=bind_goal(child,f['goal'],scene.get('supportClearance',1e-6))
                                if child.get('terminalGoal')==f['goal'] and eligible(child,f) and screen(child,f) is None:
                                    identity=candidate_key(child)
                                    if identity not in {candidate_key(p) for p in f['ready']} | {r.get('candidateKey',r['id']) for r in f['records']}:
                                        f['ready'].append(child)
                            f['directCursor']+=1
                    finally:source.close()
                f['directRoot']+=1
                f['directCursor']=0
            f['directDone']=True
            if f['ready']:
                f['ready'].sort(key=lambda p:processing_key(p,candidate_key(p)))
                f['phase']='score'
                transition('direct_virtual_finish_batch',f,count=len(f['ready']))
                return None
        # Test an existing normal free-air/run-off continuation at the terminal
        # clock before enumerating optional intervening contacts and timings.
        # Keep the symbolic source intact for subsequent backtracking.
        if terminal(f) and terminal_plan is not None and not f.get('directDone'):
            f.setdefault('directRoot',0)
            f.setdefault('directCursor',0)
            while f['directRoot']<len(f['parents']):
                p = f['parents'][f['directRoot']]
                remaining = terminal_plan.tick-p['profile']['airtime']
                if remaining<=0:
                    f['directRoot'] += 1
                    continue
                ctx = dict(mode='open',scene=scene,program=p,state=boundary_state(p),maxInputs=remaining,
                    normalGap=True,normalGapInputs=remaining,threshold=0.,checkBudget=work.check)
                configure(ctx,f)
                source = iter(generate_choices('normal',ctx))
                try:
                    for _ in range(f['directCursor']):
                        next(source)
                    for choice in source:
                        f['directChoice'] = choice
                        if choice['profile']['airtime']==remaining:
                            child = append_choice(p,choice,max_inputs=cfg['maxInputs'])
                            if child is not None and accepted_program(child) and screen(child,f) is None:
                                identity = candidate_key(child)
                                if identity not in {r.get('candidateKey',r['id']) for r in f['records']}:
                                    f['ready'].append(child)
                        f['directCursor'] += 1
                        f.pop('directChoice',None)
                finally:
                    source.close()
                f['directRoot'] += 1
                f['directCursor'] = 0
            f['directDone'] = True
            if f['ready']:
                f['phase'] = 'score'
                transition('direct_terminal_batch',f,count=len(f['ready']))
                return terminal_plan
        target_scene = dict(scene)
        cp = terminal_plan if terminal(f) else None
        if f['goal'] and f['goal']['kind']=='landing':
            target_scene['goalBlock'] = f['goal']['block']
        elif f['goal'] and f['goal']['kind']=='region':
            spec = f['goal']
            at = plans[min(f['index'],len(plans)-1)].tick if plans else spec['first_tick']
            cp = Checkpoint(at,-1,(1.,0.),tuple(scene['initial']['position']),tuple(scene['initial']['position']),
                (),tuple(spec['region']))
        report = f['generation']
        before = report.get('continuation',{}).get('expanded',0)
        settings = dict(cfg,maxPrefixes=max(0 if typed_route else 1,
            cfg['maxPrefixes']-(state['expanded']-initial_expanded)))
        if anchor_route:
            from .anchor_guides import classify
            span,_=span_info(f)
            for root in f['parents']:
                point=root.get('decisionPoint',scene['initial']['position'][::2])
                root.setdefault('decisionPoint',point)
                root.setdefault('decisionPointTick',root['profile']['airtime'])
                root.setdefault('decisionPointSource','original_initial' if not root['profile']['airtime'] else 'validated_anchor')
                root['decisionTurnaround']=classify(adapter.guide_plan,span,point)['isTurnaround']
            node_options=dict(node_evaluator=lambda p,n,x:evaluate_node(p,n,x,f),
                priority_key=lambda p:priority(p,f,candidate_key(p)),
                policy_context=dict(anchor=semantic_settings(adapter.anchor_policy),span=span,guide=adapter.guide_plan['id'],score=stage_adapter(f).score_context))
        else:node_options={}
        source = iter_movement_programs(target_scene,settings=settings,report=report,initial_programs=f['parents'],
            **node_options,
            continuation=report.get('continuation'), persistent=True, checkpoint_tick=cp.tick if cp else None,
            threshold=0., deadline=deadline,cancel_event=cancel_event,work=work,screen=lambda p:screen(p,f),
            context_adapter=lambda ctx:configure(ctx,f),program_filter=lambda p:eligible(p,f),stop_on_goal=cp is None,
            stop_supports=f['goal'].get('blocks') if f['goal'] and f['goal']['kind']=='landing' else None,
            terminal_goal=f['goal'] if f['goal'] and f['goal']['kind']==KIND else None)
        seen = {r.get('candidateKey',r['id']) for r in f['records']} | {candidate_key(p) for p in f['ready']}
        try:
            while len(f['ready']) < batch_size:
                try:
                    p = next(source)
                except StopIteration:
                    f['sourceExhausted'] = report.get('continuation',{}).get('exhausted',False)
                    break
                identity = candidate_key(p)
                if identity not in seen:
                    f['ready'].append(p)
                    seen.add(identity)
                    if typed_route:
                        # Score the iterator's first ready arrival immediately.
                        # Remaining proposals stay owned by its saved cursor.
                        break
        finally:
            source.close()
            state['expanded'] += report.get('continuation',{}).get('expanded',0)-before
        f['ready'].sort(key=lambda p:priority(p,f,digest(program_physics_key(p))))
        f['batches'] += 1
        if not f['ready'] and not f['sourceExhausted']:
            raise TimeoutError(report.get('termination','generation_suspended'))
        f['phase'] = 'score'
        transition('generated_batch',f,count=len(f['ready']),exhausted=f['sourceExhausted'])
        return cp

    def score(f, program, old=None):
        source_program = program
        identity = old['id'] if old else f['inFlight']['id'] if f['inFlight'] is not None else score_key(program,f)
        work.counts['terminalEvaluations' if final_frame(f) else 'spatialGoalEvaluations'] += 1
        f['inFlight'] = dict(id=identity,program=program)
        transition('dispatch',f,candidate=identity,goal=f['goal'],inputs=program['profile']['airtime'])
        target_scene = dict(scene)
        cp = terminal_plan if terminal(f) else None
        landing = None
        if f['goal'] and f['goal']['kind']=='landing':
            landing = program['terminalSupport'] if program['terminalSupport'] in f['goal'].get('blocks',[]) else f['goal']['block']
            target_scene['goalBlock'] = landing
        elif f['goal'] and f['goal']['kind']=='region':
            spec = f['goal']
            cp = Checkpoint(program['profile']['airtime'],-1,(1.,0.),tuple(scene['initial']['position']),
                tuple(scene['initial']['position']),(),tuple(spec['region']))
        def progress(repair):
            f['inFlight']['program']['repairState'] = repair
            publish()
        active_adapter = stage_adapter(f)
        guidance=active_adapter.objective if event_route and not final_frame(f) else adapter.outgoing_objective(f['index']) if typed_route and not final_frame(f) else None
        result = evaluate_program(target_scene,program,compiler,active_adapter,work,motion,checkpoint=cp,
            seconds=max(.001,min(score_seconds,deadline-time.monotonic())),candidate_seconds=candidate_seconds,
            solver_calls=final_calls if final_frame(f) else score_calls,python_executable=python_executable,
            cancel_event=cancel_event,partial_corridor=not terminal(f),required_landing=landing,
            on_progress=progress,retry_unresolved=retry_unknown,first_witness=not final_frame(f),guide_objective=guidance,
            **(dict(solve_policy=dict(purpose='terminal' if final_frame(f) else 'anchor',
                stopOnFeasible=not final_frame(f),rankEqualCost=final_frame(f),oracleRejection=True)) if anchor_route else {}))
        if anchor_route:
            from .turn_observability import record as observe, replay
            span,start=span_info(f)
            observe(state['observations'],adapter.guide_plan,program,span=span,start=start,
                model=result.get('solveIdentity',identity),status=result.get('nodeFeasibility',{}).get('status',result['status']),kind='anchor',
                metrics=result['work'],elapsed=sum(result.get('timings',{}).values()),
                service=dict(latchRevision=state['latchRevision'],hardLatched=latch_key(f) in state['hardLatches'],
                    runDeferralEnabled=minimum_run_pruning and not program.get('priorityTurnaround',False) and latch_key(f) in state['hardLatches']))
            if result['best'] is not None:
                ancestor=program.get('decisionParent')
                while ancestor:
                    event=state['observations']['decisions'].get(ancestor)
                    if event is None:break
                    replay(state['observations'],adapter.guide_plan,ancestor,event['span'],
                        result['best']['candidate']['positions'][event['pointTick']],result.get('solveIdentity',identity))
                    ancestor=event['parent']
            node_id=program.get('decisionNode') or identity
            state['nodeResults'][node_id]=dict(result.get('nodeFeasibility',{}),
                parent=program.get('decisionParent'),depth=program.get('decisionDepth',0),kind='terminal' if final_frame(f) else 'anchor')
        p = copy.deepcopy(program)
        if result.get('repairState') is not None:
            p['repairState'] = result['repairState']
        record = dict(id=identity,program=p,status=result['status'],termination=result['termination'],
            arrival=program['profile']['airtime'] if result['best'] is not None else None,
            value=result['best']['candidate']['objective'] if result['best'] else None,
            family=decision_family(program,f['parents']),work=result['work'],goal=f['goal'],goalKey=goal_key(scene,f['goal'],active_adapter.corridor) if f['goal'] else None,
            evaluations=(old.get('evaluations',[]) if old else [])+[dict(session=state['session'],work=result['work'])],
            tried=old.get('tried',False) if old else False,solveScope=result.get('solveScope'),solveIdentity=result.get('solveIdentity'),
            numericalAttempts=result.get('repairState',{}).get('attemptLog',result.get('attempts',[])),
            contactAttempts=result.get('repairState',{}).get('contactLog',result.get('contactAttempts',[])))
        record['candidateKey'] = candidate_key(program)
        if event_route:
            record['scoreContextKey'] = active_adapter.score_context
            record['guideId'] = active_adapter.guide['id']
        record['ticks'] = program['profile']['airtime']
        record['complete'] = final_frame(f)
        if window_plan is not None:
            from .decision_windows import DecisionPolicy
            record['decisionAudit'] = DecisionPolicy(window_plan,scene).audit(program)
        if result['best'] is not None:
            best = result['best']
            if f['goal'] and f['goal']['kind']=='region':
                record['arrival'] = region_arrival(best['candidate'],f['goal'],adapter)
                if record['arrival'] is not None:
                    program = truncate_program(program,record['arrival'])
            if f['goal'] and f['goal']['kind']==KIND and not satisfied(program,best['candidate'],f['goal'],scene.get('supportClearance',1e-6)):
                record['arrival']=None
            if record['arrival'] is None:
                record.update(status='unknown',termination='spatial_goal_not_satisfied',guidanceWitness=best)
            else:
                record['witness'] = best
                record['child'] = commit_arrival(program,best,source_program=source_program)
                record['child']['searchParent'] = identity
                if anchor_route:
                    for k in ('decisionNode','decisionParent','decisionDepth','restoredRun'):
                        if k in program:record['child'][k]=program[k]
                    record['child'].update(decisionPoint=best['candidate']['positions'][-1],
                        decisionPointTick=program['profile']['airtime'],decisionPointSource='validated_anchor',
                        lastSolvedDepth=program.get('decisionDepth',0))
                record['child'].pop('solveScope',None)
        elif old and old.get('witness'):
            for key in ('witness','child','arrival','value'):
                record[key] = old[key]
        if old and old.get('child') is not None and record.get('child') is not None and candidate_key(old['child'])!=candidate_key(record['child']):
            record['tried'] = False
        if old:
            record['refinementSession'] = old.get('refinementSession')
            f['records'][f['records'].index(old)] = record
        else:
            f['records'].append(record)
        known_children = {candidate_key(r['child']) for r in f['records'] if r.get('child')}
        for variant in (result.get('repairState',{}).get('acceptedVariants',[]) if not final_frame(f) or result.get('fullSceneValidated',True) else []):
            best = copy.deepcopy(variant['best'])
            if result.get('solveScope') is not None:
                best['validationScope'] = result['solveScope']
            at = region_arrival(best['candidate'],f['goal'],adapter) if f['goal'] and f['goal']['kind']=='region' else source_program['profile']['airtime']
            if f['goal'] and f['goal']['kind']==KIND and not satisfied(source_program,best['candidate'],f['goal'],scene.get('supportClearance',1e-6)):
                at=None
            if at is None:
                continue
            variant_program = truncate_program(source_program,at) if at<source_program['profile']['airtime'] else source_program
            child = commit_arrival(variant_program,best,source_program=source_program)
            if anchor_route:
                child.update(decisionPoint=best['candidate']['positions'][at],decisionPointTick=at,
                    decisionPointSource='validated_anchor',lastSolvedDepth=source_program.get('decisionDepth',0))
            key = candidate_key(child)
            if key in known_children:
                continue
            known_children.add(key)
            alternate = dict(record,id=digest([identity,key]),program=copy.deepcopy(child),child=child,
                witness=best,tried=False,variantOf=identity,arrival=at,
                value=best['candidate']['objective'])
            f['records'].append(alternate)
        final_validated = result.get('fullSceneValidated',True)
        if final_frame(f) and not final_validated and record.get('witness'):
            record['guidanceWitness'] = record.pop('witness')
            record.pop('child',None)
            record.update(status='unknown',termination='preliminary_scope_not_a_terminal_witness')
        if anchor_route and record.get('witness') and final_validated:
            from .anchor_policy import transfer_structure
            span,start=span_info(f);names=adapter.guide_plan['anchors']
            transfer=transfer_structure(scene,program,start,names[span],names[span+1],complete=True)
            record['transferStructure']=transfer
            if transfer['valid']:
                key=latch_key(f)
                if key not in state['hardLatches']:
                    state['latchRevision']+=1
                    state['hardLatches'][key]=dict(version=transfer['version'],revision=state['latchRevision'],candidate=identity,
                        frame=f['id'],model=result.get('solveIdentity'),inputs=program['profile']['airtime'],
                        transferStructure=transfer)
                    transition('hard_jump_latched',f,span=key,revision=state['latchRevision'])
        if minimum_run_pruning and record.get('witness') and final_validated:
            remember(f.setdefault('minimumRuns',{}),latest_jump(program,scene),identity,record['arrival'])
        if record.get('witness') and final_validated and final_frame(f):
            from standalone.autonomy.ranking import candidate_rank
            candidate = copy.deepcopy(record['witness'])
            candidate.update(movementProgram=record['child'],blockIds=[scene['startBlock'],scene['goalBlock']])
            if state['best'] is None or candidate_rank(candidate,scene['objective']) < candidate_rank(state['best'],scene['objective']):
                state['best'] = candidate
                state.setdefault('firstFullSolutionSeconds',time.monotonic()-started)
                work.accepted(candidate)
                transition('incumbent',f,candidate=identity,objective=candidate['candidate']['objective'])
            existing = next((r for r in state['finalAttempts'] if r['id']==identity),None)
            if existing is not None:
                state['finalAttempts'][state['finalAttempts'].index(existing)] = record
            else:
                state['finalAttempts'].append(record)
        f['inFlight'] = None
        transition('scored',f,candidate=identity,status=record['status'],arrival=record['arrival'])
        if result['status'] in ('error','cancelled'):
            raise TimeoutError(result['status'])
        return record

    def close_frame(f, *, deferred=False):
        state['stack'].pop()
        old = next((v for v in state['frames'] if v['id']==f['id']),None)
        if old is not None:
            state['frames'].remove(old)
        state['frames'].append(f)
        if not deferred and any(numerical_pending(r) or r.get('program',{}).get('repairState',{}).get('unresolved') for r in f['records']):
            state['suspended'].append(f)
        transition('backtrack',f,parent=f['parent'])

    def support_episodes_for_child(record, frame):
        from .event_routes import observed_route
        return observed_route(adapter.data,record['child']['profile'],record['witness'],
                              after=stage_adapter(frame).request['afterTick'])

    if event_route:
        state['discoveryWork'] = dict(expansions=0,materializations=0)
    if not state['stack'] and state['nextFrame']==0 and not state.get('goalOwners'):
        index = 0
        while not typed_route and index<len(goal_list) and goal_list[index]['kind']=='landing' and all(
                list(_landing_prefixes(scene,p,goal_list[index]['block'])) for p in roots):
            index += 1
        frame(index,roots)
    if event_route:
        for discovery in state['discovery'].values():
            if not discovery['exhausted']:
                expand_discovery(discovery)
        ready = next((f for f in state['goalQueue'] if f['owner']==state.get('pendingGoalOwner')),None)
        if ready is not None:
            state['goalQueue'].remove(ready)
            state['stack'].append(ready)
            state.pop('pendingGoalOwner')
    resumable = next((f for f in reversed(state['suspended']) if retry_unknown or any(numerical_pending(r) for r in f['records'])),None)
    if not state['stack'] and resumable is not None:
        f = resumable
        state['suspended'].remove(f)
        f['retrySession'] = state['session']
        for r in f['records']:
            if retry_unknown and not r.get('witness'):
                f['ready'].append(r['program'])
        f['phase'] = 'score'
        state['stack'].append(f)
    resumable_ids = {f['id'] for f in state['suspended'] if retry_unknown or any(numerical_pending(r) for r in f['records'])}
    try:
        while state['stack'] or state['refinements'] or resumable_ids or event_route and state['goalQueue']:
            work.check()
            if not state['stack']:
                if event_route and state['goalQueue']:
                    state['stack'].append(state['goalQueue'].pop(0))
                    continue
                if state['refinements']:
                    f = state['refinements'].pop(0)
                else:
                    f = next(f for f in state['suspended'] if f['id'] in resumable_ids)
                    resumable_ids.remove(f['id'])
                    state['suspended'].remove(f)
                    if retry_unknown:
                        known = {candidate_key(p) for p in f['ready']}
                        for r in f['records']:
                            if not r.get('witness') and r['id'] not in known:
                                f['ready'].append(r['program'])
                f['phase'] = 'score' if f['ready'] else 'generate' if not f['sourceExhausted'] else 'select'
                state['stack'].append(f)
            if cancel_event is not None and cancel_event.is_set():
                raise TimeoutError('cancelled')
            f = state['stack'][-1]
            if f['phase']=='generate':
                generate(f)
            if f['phase']=='score':
                while f['ready'] or f['inFlight'] is not None:
                    if not typed_route:
                        work.check()
                    if f['inFlight'] is not None:
                        if typed_route:
                            work.check()
                        item = f['inFlight']
                        program = item['program']
                        previous = next((r for r in f['records'] if r['id']==item['id']),None)
                    else:
                        covered = {r['family'] for r in f['records'] if r.get('witness') and not r['tried']}
                        program = next((p for p in f['ready'] if decision_family(p,f['parents']) not in covered),None)
                        if program is None:
                            break
                        measured = [r for r in f['records'] if r.get('witness') and not r['tried']]
                        initial_domain = getattr(program_filter,'initial_jump_inputs',None) if f['index']==0 else None
                        observed = {next((t for t,j in enumerate(r['program']['profile']['jump'])
                            if j and r['program']['profile']['grounded'][t]),None) for r in measured}
                        compared_roots = initial_domain is None or set(initial_domain)<=observed
                        if measured and compared_roots and priority(program,f)[:3] > record_priority(min(measured,key=lambda r:record_priority(r,f)),f)[:3]:
                            break
                        if typed_route:
                            work.check()
                        f['ready'].remove(program)
                        previous = next((r for r in f['records'] if r.get('candidateKey',r['id'])==candidate_key(program)),None)
                    if not eligible(program,f):
                        f['inFlight']=None
                        transition('candidate_exclusion',f,candidate=candidate_key(program))
                        continue
                    score(f,program,previous)
                f['phase'] = 'select'
            options = [r for r in f['records'] if r.get('witness') and not r['tried'] and run_bound(r['program'],f) is None and (not anchor_route or hard_bound(r['program'],f) is None)]
            if options:
                chosen = min(options,key=lambda r:record_priority(r,f))
                chosen['tried'] = True
                if anchor_route:
                    event=state['observations']['decisions'].get(chosen['program'].get('decisionNode'))
                    if event is not None:event['selected']=event.get('selected',0)+1
                transition('choose',f,candidate=chosen['id'],arrival=chosen['arrival'],key=list(record_priority(chosen,f)))
                if final_frame(f):
                    candidate = copy.deepcopy(chosen['witness'])
                    candidate.update(movementProgram=chosen['child'],blockIds=[scene['startBlock'],scene['goalBlock']])
                    from standalone.autonomy.ranking import candidate_rank
                    if state['best'] is None or candidate_rank(candidate,scene['objective']) < candidate_rank(state['best'],scene['objective']):
                        state['best'] = candidate
                        work.accepted(candidate)
                        transition('incumbent',f,candidate=chosen['id'],objective=candidate['candidate']['objective'])
                    # Save local refinements, but return to actual siblings first.
                    if final_frame(f):
                        deferred = bool(f['ready'] or not f['sourceExhausted'] or any(numerical_pending(r) for r in f['records']))
                        if deferred:
                            state['refinements'].append(f)
                        close_frame(f,deferred=deferred)
                    continue
                window = dict(checkpoint=dict(tick=chosen['arrival']),goal=f['goal'],frame=f['id'],
                    candidates=copy.deepcopy(f['records']),retained=[r['id'] for r in options],
                    deferred=[candidate_key(p) for p in f['ready']],
                    scored=len(options),unknown=sum(not r.get('witness') for r in f['records']),heuristicDrops=0,
                    generation=copy.deepcopy(f['generation']),retention=dict(policy='saved_dfs_siblings_no_cap_eviction'))
                state['windows'].append(window)
                if event_route:
                    observed=support_episodes_for_child(chosen,f)
                    cursor=adapter.advance(f['semanticCursor'],chosen['child'],observed,f['goalRequest'])
                    frame(f['index']+1,[chosen['child']],parent=dict(frame=f['id'],candidate=chosen['id']),cursor=cursor)
                elif typed_route:
                    frame(f['index']+1,[chosen['child']],parent=dict(frame=f['id'],candidate=chosen['id']))
                if on_window:
                    on_window(window)
                if not typed_route:
                    frame(f['index']+1,[chosen['child']],parent=dict(frame=f['id'],candidate=chosen['id']))
                continue
            if f['ready']:
                f['phase'] = 'score'
                continue
            pending = next((r for r in f['records'] if run_bound(r['program'],f) is None and numerical_pending(r) and
                r.get('refinementSession')!=state['session']),None)
            if pending is not None:
                pending['refinementSession'] = state['session']
                score(f,pending['program'],pending)
                continue
            if not f['sourceExhausted']:
                f['phase'] = 'generate'
                continue
            if anchor_route:
                deferred=[r for r in state['minimumRunExclusions'] if r['frame']==f['id'] and not r.get('restored')]
                for saved in deferred:
                    saved['restored']=True
                    p=copy.deepcopy(saved['program']);p['restoredRun']=dict(frame=f['id'],candidate=saved['candidate'])
                    if hard_bound(p,f) is not None:continue
                    if p['terminalSupport'] in f['goal'].get('blocks',[f['goal']['block']]):
                        f['ready'].append(p)
                    else:
                        continuation=f['generation'].get('continuation')
                        if continuation is not None:
                            continuation['pending'].append(p);continuation['exhausted']=False
                            f['sourceExhausted']=False
                    transition('restore_run',f,candidate=saved['candidate'])
                if f['ready'] or not f['sourceExhausted']:
                    f['phase']='score' if f['ready'] else 'generate'
                    continue
            close_frame(f)
        if not state['stack'] and state['suspended']:
            termination = 'numerical_unknown'
    except TimeoutError as error:
        termination = work.termination or str(error)
    publish()
    if event_route and any(not d['exhausted'] for d in state['discovery'].values()) and termination=='exhausted':
        termination='discovery_work_limit'
    generations = [f['generation'] for f in state['frames']+state['stack']+state.get('goalQueue',[])]
    return dict(status='feasible' if state['best'] else 'cancelled' if termination=='cancelled' else
        'error' if termination=='error' else 'unknown',termination=termination,best=state['best'],
        windows=state['windows'],finalAttempts=state['finalAttempts'],corridor=adapter.corridor.to_dict(),
        routeGeneration=route_report,adapter=adapter.summary(),movementGeneration=generations,
        expandedPrefixes=state['expanded']-initial_expanded,totalExpandedPrefixes=state['expanded'],spatialExpandedPrefixes=0,
        work=work.summary(),compiler=compiler.summary(),elapsedSeconds=time.monotonic()-started,threshold=0.,
        order='goal_arrival_depth_first',checkpoints=[p.to_dict() for p in plans],decisionPlan=window_plan,
        comparisonPolicy='goal_arrival_dfs',requestedComparisonPolicy=comparison_policy,spatialGoal=default_goal,fairParents=False,
        parkedCandidates=[],unevaluatedCandidates=[],rescues=[],comparisonMargins=dict(time=time_margin,progress=progress_margin),
        objectiveOptimalityProven=False,minimumTicksProven=False,searchState=state,
        resume=dict(exact=state['session']>1,session=state['session']),interactionGate=gate.summary(),viability=viability_counts,
        minimumRunPruning=dict(enabled=minimum_run_pruning,version=POLICY_VERSION,excluded=len(state['minimumRunExclusions']),
            scope='ordinary_ground_hold_before_latest_jump',certifiedDominance=False),
        terminalCheckpoint=terminal_plan.to_dict() if terminal_plan else None,
        goalType='fixed_absolute_tick_objective' if terminal_plan else 'earliest_virtual_landing_height' if (adapter.data['task'] if event_route else goal_list[-1])['kind']==KIND else 'landing_time')
