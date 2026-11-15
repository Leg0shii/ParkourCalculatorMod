import functools
import json
import math


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
    try:
        return _check(json.dumps(spec,sort_keys=True))
    except (ImportError,ValueError,RuntimeError):
        return None


@functools.lru_cache(maxsize=1024)
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
    result=linprog(np.zeros(2*width),A_ub=matrix,b_ub=np.asarray(rhs),bounds=bounds,method='highs',
                   options=dict(time_limit=1.,primal_feasibility_tolerance=1e-7,dual_feasibility_tolerance=1e-7))
    if result.status==2:
        return dict(reason='linear_control_relaxation_infeasible',relaxation='circumscribed_16_face_acceleration_polygon',
                    dynamicsMargin=2e-8,constraintMargin=2e-7)
    return None
