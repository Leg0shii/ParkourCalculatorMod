import copy
import json

from parkour_solver.movement.program import truncate_program
from parkour_solver.schema import integer, number
from parkour_solver.spatial.checkpoints import checkpoint


def goal_spec(scene, spec=None):
    spec=copy.deepcopy(spec or dict(kind='landing',block=scene['goalBlock']))
    if spec.get('kind')=='landing_height_region':
        from parkour_solver.search.virtual_goals import prepare_goal
        return prepare_goal(spec)
    if spec.get('kind')=='landing':
        if set(spec)-{'kind','block','blocks','surface'} or spec.get('block') not in {b['id'] for b in scene['blocks']}:
            raise ValueError('Landing comparison requires a scene block')
        if 'blocks' in spec and (not isinstance(spec['blocks'],list) or not spec['blocks'] or
                spec['block'] not in spec['blocks'] or any(b not in {v['id'] for v in scene['blocks']} for b in spec['blocks'])):
            raise ValueError('Landing surface requires its physical support alternatives')
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


def _landing_prefixes(scene,program,block_id, *, reuse_full=False):
    block=next(b for b in scene['blocks'] if b['id']==block_id)
    box=scene['collisionScene']['solids'][block['solidIndex']]
    p=program['profile']
    events=p.get('contactEvents',[])+p.get('caseEvents',[])
    times={e.get('offset',e.get('input',-1))+1 for e in events if box in e.get('boxes',())
           and (e.get('axis')=='Y' and e.get('direction')==-1 or e.get('case') in ('normal_step','blip'))}
    if program['terminalSupport']==block_id:times.add(p['airtime'])
    for tick in sorted(times):
        if not 0<tick<=p['airtime'] or not p['grounded'][tick] or abs(p['position'][tick]-block['top'])>1e-7:
            continue
        candidate=program if reuse_full and tick==p['airtime'] and program['terminalSupport']==block_id else truncate_program(program,tick)
        candidate['terminalSupport']=block_id
        at=[e for e in events if box in e.get('boxes',()) and e.get('offset',e.get('input',-1))==tick-1]
        if any(e.get('case') in ('normal_step','blip') for e in at):
            candidate['profile']['landingPhase']='post'
        elif any(e.get('axis')=='Y' and e.get('direction')==-1 for e in at):
            candidate['profile']['landingPhase']='pre'
        yield candidate
