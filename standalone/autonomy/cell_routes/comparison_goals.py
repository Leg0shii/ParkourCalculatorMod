import copy
import json
import math
import time

from standalone.autonomy.movement_composition import program_search_key, truncate_program
from standalone.autonomy.movement_search import iter_movement_programs, movement_settings, screen_program
from standalone.autonomy.ranking import candidate_rank
from standalone.ipopt.model import integer, number
from .checkpoints import checkpoint
from .time_progress import generation_exhausted


def goal_spec(scene, spec=None):
    spec=copy.deepcopy(spec or dict(kind='landing',block=scene['goalBlock']))
    if spec.get('kind')=='landing_height_region':
        from standalone.autonomy.terminal_goals import prepare_goal
        return prepare_goal(spec)
    if spec.get('kind')=='landing':
        if set(spec)-{'kind','block'} or spec.get('block') not in {b['id'] for b in scene['blocks']}:
            raise ValueError('Landing comparison requires a scene block')
    elif spec.get('kind')=='region':
        if set(spec)-{'kind','region','segment','first_tick','y'}:
            raise ValueError('Unsupported region comparison fields')
        bounds=spec.get('region')
        if not isinstance(bounds,list) or len(bounds)!=4:
            raise ValueError('Region comparison requires minX,maxX,minZ,maxZ')
        for value in bounds:number(value,'comparison region')
        if bounds[0]>=bounds[1] or bounds[2]>=bounds[3]:raise ValueError('Comparison region is empty')
        if 'segment' in spec:integer(spec['segment'],'comparison segment',0,200)
        spec.setdefault('first_tick',1)
        integer(spec['first_tick'],'first comparison tick',1,200)
        if 'y' in spec:
            if not isinstance(spec['y'],list) or len(spec['y'])!=2 or spec['y'][0]>spec['y'][1]:
                raise ValueError('Comparison Y requires a closed range')
            for value in spec['y']:number(value,'comparison Y')
    else:raise ValueError('Spatial comparison requires landing or region')
    return spec


def goal_key(scene,spec,corridor=None):
    return json.dumps(dict(goal=spec,route=corridor.to_dict() if corridor is not None else None,
                           initial=scene['initial']),sort_keys=True)



def region_arrival(candidate,spec,adapter):
    for tick in range(spec['first_tick'],len(candidate['positions'])):
        x,z=candidate['positions'][tick]
        if not spec['region'][0]<=x<=spec['region'][1] or not spec['region'][2]<=z<=spec['region'][3]:continue
        if 'y' in spec and not spec['y'][0]<=candidate['y'][tick]<=spec['y'][1]:continue
        kwargs=dict(tick=tick,region=spec['region'])
        if 'segment' in spec:kwargs['segment']=spec['segment']
        prefix=dict(candidate,positions=candidate['positions'][:tick+1],y=candidate['y'][:tick+1])
        if checkpoint(adapter.corridor,adapter.gates,kwargs).stage(prefix,adapter.gates)['accepted']:
            return tick
    return None



def landing_satisfied(scene,block_id,vertical):
    block=next(b for b in scene['blocks'] if b['id']==block_id)
    return bool(vertical['onGround'][-1] and abs(vertical['y'][-1]-block['top'])<=1e-7)


def _landing_prefixes(scene,program,block_id):
    block=next(b for b in scene['blocks'] if b['id']==block_id)
    box=scene['collisionScene']['solids'][block['solidIndex']]
    p=program['profile']
    events=p.get('contactEvents',[])+p.get('caseEvents',[])
    times={e.get('offset',e.get('input',-1))+1 for e in events if e.get('box')==box
           and (e.get('axis')=='Y' and e.get('direction')==-1 or e.get('case') in ('normal_step','blip'))}
    if program['terminalSupport']==block_id:times.add(p['airtime'])
    for tick in sorted(times):
        if not 0<tick<=p['airtime'] or not p['grounded'][tick] or abs(p['position'][tick]-block['top'])>1e-7:
            continue
        candidate=truncate_program(program,tick)
        candidate['terminalSupport']=block_id
        yield candidate


def probe_spatial(scene,program,compiler,adapter,work,motion,*,spec=None,seconds=2,candidate_seconds=.7,
                  solver_calls=4,max_candidates=4,max_prefixes=64,python_executable=None,cancel_event=None,program_filter=None):
    from .checkpoint_search import ScopedWork,evaluate_program
    if number(seconds,'spatial comparison seconds')<=0 or number(candidate_seconds,'candidate seconds')<=0:
        raise ValueError('Comparison budgets must be positive')
    for name,value in [('solver_calls',solver_calls),('max_candidates',max_candidates),('max_prefixes',max_prefixes)]:
        integer(value,name,1,200000)
    spec=goal_spec(scene,spec)
    if spec['kind']=='region' and not hasattr(adapter,'corridor'):
        raise ValueError('Region comparisons require a spatial corridor')
    key=goal_key(scene,spec,getattr(adapter,'corridor',None))
    deadline=min(work.deadline or math.inf,time.monotonic()+seconds)
    scoped=ScopedWork(work,solver_calls,deadline)
    report,attempts,best,best_program={},[],None,None
    proposed=[]
    incomplete=False
    if spec['kind']=='landing':
        target_scene=copy.deepcopy(scene)
        target_scene['goalBlock']=spec['block']
        proposed.extend(_landing_prefixes(scene,program,spec['block']))
        cfg=dict(movement_settings(scene),maxPrefixes=max_prefixes)
        def screen(p):
            return screen_program(target_scene,p,compiler,max(.001,min(candidate_seconds,deadline-time.monotonic())))
        generator=None
        if not proposed:
            generator=iter_movement_programs(target_scene,settings=cfg,initial_programs=[program],threshold=0.,
                deadline=deadline,cancel_event=cancel_event,report=report,work=scoped,screen=screen,
                context_adapter=adapter.context if hasattr(adapter,'context') else None,program_filter=program_filter)
            try:
                for p in generator:
                    proposed.append(p)
                    if len(proposed)>=max_candidates:
                        incomplete=True
                        break
            finally:generator.close()
        proposed.sort(key=lambda p:p['profile']['airtime'])
        complete=spec['block']==scene['goalBlock']
        def evaluate(p):
            return evaluate_program(target_scene,p,compiler,adapter,scoped,motion,seconds=max(.001,deadline-time.monotonic()),
                candidate_seconds=candidate_seconds,solver_calls=max(1,solver_calls-scoped.local.counts['solverCalls']),
                python_executable=python_executable,cancel_event=cancel_event,partial_corridor=not complete,
                required_landing=spec['block'])
    else:
        target_scene=scene
        for tick in range(spec['first_tick'],program['profile']['airtime']+1):
            if 'y' in spec and not spec['y'][0]-1e-7<=program['profile']['position'][tick]<=spec['y'][1]+1e-7:
                continue
            proposed.append(truncate_program(program,tick))
        def evaluate(p):
            kwargs=dict(tick=p['profile']['airtime'],region=spec['region'])
            if 'segment' in spec:kwargs['segment']=spec['segment']
            cp=checkpoint(adapter.corridor,adapter.gates,kwargs)
            return evaluate_program(scene,p,compiler,adapter,scoped,motion,checkpoint=cp,
                seconds=max(.001,deadline-time.monotonic()),candidate_seconds=candidate_seconds,
                solver_calls=max(1,solver_calls-scoped.local.counts['solverCalls']),
                python_executable=python_executable,cancel_event=cancel_event)
    seen=set()
    for p in proposed:
        identity=program_search_key(p)
        if identity in seen:continue
        seen.add(identity)
        try:scoped.check()
        except TimeoutError:
            incomplete=True
            break
        if cancel_event is not None and cancel_event.is_set():
            incomplete=True
            break
        result=evaluate(p)
        attempts.append(dict(ticks=p['profile']['airtime'],status=result['status'],termination=result['termination'],
                             work=result['work']))
        if result['best'] is not None:
            candidate=result['best']
            if best is None or candidate_rank(candidate,scene['objective'])<candidate_rank(best,scene['objective']):
                best,best_program=candidate,p
            if spec['kind']=='region':break
        if result['status'] in ('error','cancelled'):
            incomplete=True
            break
    if best is not None and spec['kind']=='region':
        arrival=region_arrival(best['candidate'],spec,adapter)
        if arrival is None:raise RuntimeError('Validated region witness has no matching spatial achievement')
    else:arrival=best['plan']['ticks'] if best is not None else None
    generation_complete=not incomplete and (not report or generation_exhausted(report))
    measurement=dict(goal=spec,status='feasible' if best is not None else 'unknown',ticks=arrival,
        interpretation='elapsed_time_to_shared_spatial_goal_not_clock_objective',minimumProven=False,
        generationComplete=generation_complete,unresolvedAttempts=sum(a['status']=='unknown' for a in attempts),
        attempts=attempts,generation=report,work=scoped.local.summary())
    return dict(key=key,measurement=measurement,best=best,program=best_program,
                finalGoal=spec['kind']=='landing' and spec['block']==scene['goalBlock'])
