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

    goal_list = [goal_spec(scene, g) for g in goals] if goals else ([] if terminal_plan is not None else [default_goal])
    # Repeated clock observations of the same goal are not separate decisions.
    goal_list = [g for i, g in enumerate(goal_list) if i == 0 or g != goal_list[i-1]]
    seed = empty_program(scene['initial'], threshold=0.)
    seed['terminalSupport'] = scene['startBlock']
    if not goals and terminal_plan is not None and hasattr(program_filter,'remaining'):
        goal_list = [goal_spec(scene,dict(kind='landing',block=b)) for b in program_filter.remaining(seed)]
    final_goal = dict(kind='landing',block=scene['goalBlock'])
    if terminal_plan is None and (not goal_list or goal_list[-1] != final_goal and goal_list[-1]['kind']!=KIND):
        goal_list.append(final_goal)
    roots = copy.deepcopy(roots if roots is not None else
        resume_state.get('initialRoots', [seed]) if resume_state else [seed])
    if not roots or any(p['initial'] != seed['initial'] or p.get('threshold', .005) != 0. for p in roots):
        raise ValueError('DFS roots must preserve original state and zero inertia thresholds')
    if any(not accepted_program(p) for p in roots):
        raise ValueError('DFS roots violate the decision domain')
    context = digest(dict(version=5, kind='ordered_goal_dfs', scene={k:v for k,v in scene.items() if k!='movementSearch'},
        domain={k:v for k,v in cfg.items() if k not in ('maxPrefixes','maxQueuedPrefixes','maxChoicesPerType','expansionsPerProposal')},
        roots=[program_search_key(p) for p in roots], goals=goal_list,
        terminal=terminal_plan.to_dict() if terminal_plan else None, corridor=adapter.corridor.to_dict(),
        windows=window_plan, filter=getattr(program_filter, 'checkpoint_identity', None),
        margins=[time_margin,progress_margin], physics=motion,
        minimumRunPolicy=POLICY_VERSION if minimum_run_pruning else None))
    if resume_state is not None and (resume_state.get('version') != 5 or
            resume_state.get('kind') != 'ordered_goal_dfs' or resume_state.get('context') != context):
        raise ValueError('Incompatible DFS continuation; old checkpoint queues require explicit reconstruction')
    state = copy.deepcopy(resume_state) if resume_state is not None else dict(version=5, kind='ordered_goal_dfs',
        context=context, initialRoots=roots, stack=[], suspended=[], refinements=[], frames=[], transitions=[],
        windows=[], finalAttempts=[], best=None, cumulativeCalls=0, session=0, expanded=0, nextFrame=0)
    state['session'] += 1
    prior_calls, initial_expanded = state['cumulativeCalls'], state['expanded']
    if resume_state and state['best'] is not None and not replay_witness(scene,state['best'],compiler)['passed']:
        raise ValueError('Saved DFS incumbent no longer passes independent replay')
    gate = InteractionGate(scene, compiler, seconds=candidate_seconds)
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
        publish()

    def frame(index, parents, parent=None):
        f = dict(id=state['nextFrame'], index=index, parents=copy.deepcopy(parents), parent=parent,
            goal=goal_list[index] if index<len(goal_list) else None, phase='generate', generation={},
            ready=[], records=[], inFlight=None, sourceExhausted=False, batches=0)
        state['nextFrame'] += 1
        state['stack'].append(f)
        transition('open', f, goal=f['goal'])
        return f

    def configure(ctx):
        adapter.context(ctx)
        gate.context(ctx)

    def time_bound(program):
        if terminal_plan is None and state['best'] is not None and program['profile']['airtime']>state['best']['plan']['ticks']:
            return dict(reason='elapsed_inputs_exceed_incumbent_finish',elapsedInputs=program['profile']['airtime'],
                incumbentTick=state['best']['plan']['ticks'])
        return None

    def run_bound(program, f):
        if not minimum_run_pruning or f is None:return None
        proof=exclusion(f.get('minimumRuns',{}),latest_jump(program,scene))
        if proof is not None:
            identity=candidate_key(program);key=(f['id'],identity)
            if key not in excluded_runs:
                excluded_runs.add(key)
                saved={k:copy.deepcopy(v) for k,v in program.items() if k!='repairState'}
                state['minimumRunExclusions'].append(dict(frame=f['id'],candidate=identity,proof=proof,program=saved))
        return proof

    def eligible(program, f=None):
        proof=time_bound(program)
        if proof is not None:
            state['timeBoundExclusions'].append(dict(id=candidate_key(program),proof=proof))
            return False
        return run_bound(program,f) is None and accepted_program(program)

    def screen(program, f=None):
        bound=time_bound(program) or run_bound(program,f)
        if bound is not None:
            return bound
        required = program_filter.remaining(program) if hasattr(program_filter,'remaining') else (
            [] if terminal_plan is not None else [scene['goalBlock']])
        proof = prefix_viability(scene, program, required_contacts=required,
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
        return f['index'] == len(goal_list)

    def final_frame(f):
        return terminal(f) or terminal_plan is None and f['index']==len(goal_list)-1

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
                    configure(ctx)
                    source=iter(generate_choices('normal',ctx))
                    try:
                        for _ in range(f['directCursor']):next(source)
                        for choice in source:
                            child=append_choice(parent,choice,max_inputs=cfg['maxInputs'])
                            if child is not None:
                                child=bind_goal(child,f['goal'],scene.get('supportClearance',1e-6))
                                if child.get('terminalGoal')==f['goal'] and eligible(child,f) and screen(child,f) is None:
                                    identity=candidate_key(child)
                                    if identity not in {candidate_key(p) for p in f['ready']} | {r['id'] for r in f['records']}:
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
                configure(ctx)
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
                                if identity not in {r['id'] for r in f['records']}:
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
        settings = dict(cfg,maxPrefixes=max(1,cfg['maxPrefixes']-(state['expanded']-initial_expanded)))
        source = iter_movement_programs(target_scene,settings=settings,report=report,initial_programs=f['parents'],
            continuation=report.get('continuation'), persistent=True, checkpoint_tick=cp.tick if cp else None,
            threshold=0., deadline=deadline,cancel_event=cancel_event,work=work,screen=lambda p:screen(p,f),
            context_adapter=configure,program_filter=lambda p:eligible(p,f),stop_on_goal=cp is None,
            terminal_goal=f['goal'] if f['goal'] and f['goal']['kind']==KIND else None)
        seen = {r['id'] for r in f['records']} | {candidate_key(p) for p in f['ready']}
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
        finally:
            source.close()
            state['expanded'] += report.get('continuation',{}).get('expanded',0)-before
        f['ready'].sort(key=lambda p:processing_key(p,digest(program_physics_key(p))))
        f['batches'] += 1
        if not f['ready'] and not f['sourceExhausted']:
            raise TimeoutError(report.get('termination','generation_suspended'))
        f['phase'] = 'score'
        transition('generated_batch',f,count=len(f['ready']),exhausted=f['sourceExhausted'])
        return cp

    def score(f, program, old=None):
        source_program = program
        identity = old['id'] if old else f['inFlight']['id'] if f['inFlight'] is not None else candidate_key(program)
        work.counts['terminalEvaluations' if final_frame(f) else 'spatialGoalEvaluations'] += 1
        f['inFlight'] = dict(id=identity,program=program)
        transition('dispatch',f,candidate=identity,goal=f['goal'],inputs=program['profile']['airtime'])
        target_scene = dict(scene)
        cp = terminal_plan if terminal(f) else None
        landing = None
        if f['goal'] and f['goal']['kind']=='landing':
            landing = f['goal']['block']
            target_scene['goalBlock'] = landing
        elif f['goal'] and f['goal']['kind']=='region':
            spec = f['goal']
            cp = Checkpoint(program['profile']['airtime'],-1,(1.,0.),tuple(scene['initial']['position']),
                tuple(scene['initial']['position']),(),tuple(spec['region']))
        def progress(repair):
            f['inFlight']['program']['repairState'] = repair
            publish()
        result = evaluate_program(target_scene,program,compiler,adapter,work,motion,checkpoint=cp,
            seconds=max(.001,min(score_seconds,deadline-time.monotonic())),candidate_seconds=candidate_seconds,
            solver_calls=final_calls if final_frame(f) else score_calls,python_executable=python_executable,
            cancel_event=cancel_event,partial_corridor=not terminal(f),required_landing=landing,
            on_progress=progress,retry_unresolved=retry_unknown,first_witness=not final_frame(f))
        p = copy.deepcopy(program)
        if result.get('repairState') is not None:
            p['repairState'] = result['repairState']
        record = dict(id=identity,program=p,status=result['status'],termination=result['termination'],
            arrival=program['profile']['airtime'] if result['best'] is not None else None,
            value=result['best']['candidate']['objective'] if result['best'] else None,
            family=decision_family(program,f['parents']),work=result['work'],goal=f['goal'],goalKey=goal_key(scene,f['goal'],adapter.corridor) if f['goal'] else None,
            evaluations=(old.get('evaluations',[]) if old else [])+[dict(session=state['session'],work=result['work'])],
            tried=old.get('tried',False) if old else False,solveScope=result.get('solveScope'),solveIdentity=result.get('solveIdentity'),
            numericalAttempts=result.get('repairState',{}).get('attemptLog',result.get('attempts',[])),
            contactAttempts=result.get('repairState',{}).get('contactLog',result.get('contactAttempts',[])))
        record['ticks'] = program['profile']['airtime']
        record['complete'] = terminal(f) or terminal_plan is None and f['index']==len(goal_list)-1
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
        if minimum_run_pruning and record.get('witness') and final_validated:
            remember(f.setdefault('minimumRuns',{}),latest_jump(program,scene),identity,record['arrival'])
        if record.get('witness') and final_validated and (terminal(f) or terminal_plan is None and f['index']==len(goal_list)-1):
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

    if not state['stack'] and state['nextFrame']==0:
        index = 0
        while index<len(goal_list) and goal_list[index]['kind']=='landing' and all(
                list(_landing_prefixes(scene,p,goal_list[index]['block'])) for p in roots):
            index += 1
        frame(index,roots)
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
        while state['stack'] or state['refinements'] or resumable_ids:
            work.check()
            if not state['stack']:
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
                    work.check()
                    if f['inFlight'] is not None:
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
                        if measured and compared_roots and processing_key(program)[:3] > arrival_key(min(measured,key=arrival_key))[:3]:
                            break
                        f['ready'].remove(program)
                        previous = next((r for r in f['records'] if r['id']==candidate_key(program)),None)
                    if not eligible(program,f):
                        f['inFlight']=None
                        transition('candidate_exclusion',f,candidate=candidate_key(program))
                        continue
                    score(f,program,previous)
                f['phase'] = 'select'
            options = [r for r in f['records'] if r.get('witness') and not r['tried'] and run_bound(r['program'],f) is None]
            if options:
                chosen = min(options,key=arrival_key)
                chosen['tried'] = True
                transition('choose',f,candidate=chosen['id'],arrival=chosen['arrival'],key=list(arrival_key(chosen)))
                if terminal(f) or terminal_plan is None and f['index']==len(goal_list)-1:
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
                if on_window:
                    on_window(window)
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
            close_frame(f)
        if not state['stack'] and state['suspended']:
            termination = 'numerical_unknown'
    except TimeoutError as error:
        termination = work.termination or str(error)
    publish()
    generations = [f['generation'] for f in state['frames']+state['stack']]
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
        goalType='fixed_absolute_tick_objective' if terminal_plan else 'earliest_virtual_landing_height' if goal_list[-1]['kind']==KIND else 'landing_time')
