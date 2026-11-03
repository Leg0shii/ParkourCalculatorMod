import copy

from standalone.ipopt.ladder import f32, vertical_move
from standalone.ipopt.model import number


KIND = 'landing_height_region'


def prepare_goal(spec):
    if not isinstance(spec,dict) or set(spec)-{'kind','height','rows'} or spec.get('kind')!=KIND:
        raise ValueError('Virtual finish requires landing_height_region, height and horizontal rows')
    result=copy.deepcopy(spec)
    number(result.get('height'),'finish height')
    rows=result.get('rows')
    if not isinstance(rows,list) or not rows:
        raise ValueError('Virtual finish requires nonempty one-sided horizontal rows')
    for row in rows:
        if not isinstance(row,dict) or set(row)-{'mode','cmp','rhs','strict'} or row.get('mode') not in ('X','Z') or row.get('cmp') not in ('LE','GE'):
            raise ValueError('Finish rows require X/Z, LE/GE and rhs')
        number(row.get('rhs'),'finish bound')
        if 'strict' in row and type(row['strict']) is not bool:
            raise ValueError('Finish strict must be boolean')
    return result


def height_clock(program, spec):
    p=program['profile'];n=p['airtime']
    if not n:
        return False
    t=n-1
    config=dict(threshold=program.get('threshold',.005),
        jumpVelocity=[f32(.42) if j else None for j in p['jump']],sneak=p['sneak'])
    movement=vertical_move(config,t,p['velocity'][t],p['ladderTicks'][t])
    return (movement<0 and p['position'][t]>=spec['height']-1e-7 and
        p['position'][t]+movement<spec['height'] and p['position'][n]<=spec['height']+1e-7)


def goal_rows(spec, tick, clearance=1e-6):
    return [dict(mode=r['mode'],t1=tick,cmp=r['cmp'],
        rhs=r['rhs']+((clearance if r['cmp']=='GE' else -clearance) if r.get('strict') else 0.),
        name='virtual_finish:'+str(i)) for i,r in enumerate(spec['rows'])]


def bind_goal(program, spec, clearance=1e-6):
    if not height_clock(program,spec):
        return program
    if program.get('terminalGoal')==spec:
        return program
    result=copy.deepcopy(program);p=result['profile'];n=p['airtime']
    p.setdefault('constraintOwners',[0]*len(p['positionConstraints']))
    rows=goal_rows(spec,n,clearance)
    p['positionConstraints'].extend(rows)
    p['constraintOwners'].extend([n]*len(rows))
    result['terminalGoal']=copy.deepcopy(spec)
    return result


def satisfied(program, candidate, spec, clearance=1e-6):
    if not height_clock(program,spec):
        return False
    n=program['profile']['airtime']
    if len(candidate['positions'])<=n:
        return False
    return all((candidate['positions'][n][0 if r['mode']=='X' else 1]-r['rhs'])*
        (1 if r['cmp']=='GE' else -1)>=-1e-7 for r in goal_rows(spec,n,clearance))


def finish_choices(context):
    from .open_movement import approaches, event_choice, first_descent
    spec=context['terminalGoal']
    for initial,jumping,trace in approaches(context):
        tick=first_descent(trace,spec['height'])
        if tick is not None:
            choice=event_choice(context,'normal',initial,tick+1,jumping=jumping)
            if choice is not None:
                yield choice
