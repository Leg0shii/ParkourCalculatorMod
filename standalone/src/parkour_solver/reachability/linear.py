import collections
import json
import math
import time

STATS = dict(withObjective=collections.Counter(), zeroCost=collections.Counter())
_DECIDED = {}
_BUDGET = 1.


def linear_exclusion(problem, extra_rows=()):
    if problem.get('threshold',0.) != 0 or any(problem.get('ladderTicks', [])):
        return None
    rows = []
    for r in list(problem.get('walls', []))+list(extra_rows):
        if r.get('mode') in ('X','Z'):
            t,t2=r.get('t1',r.get('offset')),r.get('t2')
            if type(t) is int and 0 <= t <= problem['ticks'] and (t2 is None or type(t2) is int and 0 <= t2 <= problem['ticks']):
                rows.append((r['mode'],t,t2,r.get('op','PLUS'),r['cmp'],r['rhs']))
    if not rows and not problem.get('contacts'):
        return None
    spec = {k:problem[k] for k in ('ticks','position','velocity','magnitude','friction')}
    spec.update(rows=sorted(set(rows),key=str),contacts=problem.get('contacts',[]),initialIntervals=problem.get('initialIntervals'))
    if problem.get('objective') is not None and problem.get('objectiveMode') != 'constant_zero':
        spec['objective'] = {k:problem['objective'].get(k) for k in ('tick','axis','sense','customYaw','type')}
    encoded = json.dumps(spec,sort_keys=True)
    stats = STATS['withObjective' if 'objective' in spec else 'zeroCost']
    if encoded in _DECIDED:
        stats['cacheHits'] += 1
        return _DECIDED[encoded]
    stats['solved'] += 1
    try:
        result = _check(encoded)
    except (ImportError,ValueError,RuntimeError) as error:
        result = dict(outcome='undecided',status=None,message=repr(error))
    stats[result['outcome']] += 1
    if result.pop('retried', False):
        stats['ipmRetries'] += 1
        if result['outcome'] != 'undecided':
            stats['ipmResolved'] += 1
    if result['outcome'] != 'undecided':
        _DECIDED[encoded] = result
        while len(_DECIDED) > 1024:
            _DECIDED.pop(next(iter(_DECIDED)))
    return result


def _cost(p, width, pos):
    import numpy as np
    cost = np.zeros(2*width)
    objective = p.get('objective')
    if objective is None or not 0 <= objective['tick'] <= p['ticks']:
        return cost
    sign = -1. if objective['sense'] == 'MAX' else 1.
    tick = objective['tick']
    if objective.get('customYaw') is not None:
        angle = math.radians(objective['customYaw'])
        for axis, weight in enumerate((-math.sin(angle), math.cos(angle))):
            cost[pos(axis,tick)] += sign*weight
            if objective.get('type') == 'MOTION' and tick > 0:
                cost[pos(axis,tick-1)] -= sign*weight
    else:
        cost[pos(0 if objective['axis'] == 'X' else 1,tick)] = sign
    return cost


def _check(encoded):
    import numpy as np
    from scipy.optimize import linprog
    from scipy.sparse import coo_matrix
    p=json.loads(encoded);n=p['ticks'];width=3*n+2
    indices,columns,values,rhs=[],[],[],[]
    bounds=[]
    def pos(axis,t):return axis*width+t
    def vel(axis,t):return axis*width+n+1+t
    def control(axis,t):return axis*width+2*(n+1)+t
    def row(entries,b):
        index=len(rhs)
        for column,value in entries.items():
            indices.append(index);columns.append(column);values.append(value)
        rhs.append(b)
    def band(entries,value,margin=2e-8):
        row(entries,value+margin)
        row({k:-v for k,v in entries.items()},-value+margin)
    contacts={(c['state'],c['axis']):c for c in p['contacts']}
    for axis in range(2):
        initial=p['initialIntervals']
        start=(initial[0][axis]-p['position'][axis],initial[1][axis]-p['position'][axis]) if initial else (0.,0.)
        speed=(initial[2][axis],initial[3][axis]) if initial else (p['velocity'][axis],p['velocity'][axis])
        bounds.extend([start]+[(None,None)]*n+[speed]+[(None,None)]*n+
                      [(-m-1e-9,m+1e-9) for m in p['magnitude']])
        for t in range(n):
            c=contacts.get((t+1,axis))
            if c is None:
                band({pos(axis,t+1):1.,pos(axis,t):-1.,vel(axis,t):-1.,control(axis,t):-1.},0.)
                f=p['friction'][t]
                band({vel(axis,t+1):1.,vel(axis,t):-f,control(axis,t):-f},0.)
            else:
                plane=c['plane']-p['position'][axis];direction=c['direction']
                band({pos(axis,t+1):1.},plane)
                band({vel(axis,t+1):1.},0.)
                row({pos(axis,t):float(direction)},direction*plane+1e-7)
                row({pos(axis,t):-float(direction),vel(axis,t):-float(direction),control(axis,t):-float(direction)},
                    -direction*plane+1e-7)
    for t,magnitude in enumerate(p['magnitude']):
        for direction in range(16):
            angle=2*math.pi*direction/16
            row({control(0,t):math.cos(angle),control(1,t):math.sin(angle)},magnitude+1e-9)
    for mode,t,t2,op,cmp,value in p['rows']:
        axis=0 if mode=='X' else 1
        entries={pos(axis,t):1.};offset=p['position'][axis]
        if t2 is not None:
            sign=-1. if op=='MINUS' else 1.
            entries[pos(axis,t2)]=entries.get(pos(axis,t2),0.)+sign
            offset+=sign*p['position'][axis]
        if cmp in ('LE','EQ'):
            row(entries,value-offset+2e-7)
        if cmp in ('GE','EQ'):
            row({k:-v for k,v in entries.items()},offset-value+2e-7)
    matrix=coo_matrix((values,(indices,columns)),shape=(len(rhs),2*width)).tocsr()
    options=dict(time_limit=_BUDGET,primal_feasibility_tolerance=1e-7,dual_feasibility_tolerance=1e-7)
    problem=dict(c=_cost(p,width,pos),A_ub=matrix,b_ub=np.asarray(rhs),bounds=bounds)
    started=time.perf_counter()
    result=linprog(**problem,method='highs',options=options)
    solver,retried='simplex',False
    remaining=_BUDGET-(time.perf_counter()-started)
    if result.status==4 and remaining>0:
        result=linprog(**problem,method='highs-ipm',options=dict(options,time_limit=remaining))
        solver,retried='ipm',True
    if result.status==2 and '(HiGHS Status 8:' in result.message:
        return dict(outcome='infeasible',retried=retried,proof=dict(reason='linear_control_relaxation_infeasible',
                    relaxation='circumscribed_16_face_acceleration_polygon',dynamicsMargin=2e-8,constraintMargin=2e-7,
                    solver=solver))
    if result.status==0:
        return dict(outcome='feasible',retried=retried)
    return dict(outcome='undecided',retried=retried,status=int(result.status),message=result.message)
