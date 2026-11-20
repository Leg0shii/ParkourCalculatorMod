"""Backward finite guide projection and one observable binary approach edge."""

import math

from parkour_solver.search.identity import digest
from parkour_solver.spatial.checkpoints import Checkpoint


def nearest(cover, point):
    if not cover:
        raise ValueError('Anchor has no exposed landing region')
    choices = [tuple(min(max(point[a],b[a]),b[a+3]) for a in range(3)) for b in cover]
    return min(choices,key=lambda p:(math.dist(p,point),p))


def landing_cover(data, block):
    scene=data['scene']
    b=next(b for b in scene['blocks'] if b['id']==block)
    return [box for s in data['geometry']['supports'] if b['solidIndex'] in s['solids']
            and s['height']==b['top'] for box in s['cover']]


def terminal_point(data):
    scene=data['scene']; task=data['task']
    if task['kind']!='landing':
        raise ValueError('Anchor search requires a finite physical landing task')
    b=next(b for b in scene['blocks'] if b['id']==task['block'])
    boxes=[scene['collisionScene']['solids'][b['solidIndex']]]
    objective=scene['objective']
    axis=0 if objective['axis']=='X' else 2
    high=objective['sense']=='MAX'
    edge=(max if high else min)(box[axis+3 if high else axis] for box in boxes)
    other=2-axis
    point=[0.,b['top'],0.]
    point[axis]=edge
    point[other]=(min(v[other] for v in boxes)+max(v[other+3] for v in boxes))/2
    return point


def unit(x,z):
    length=math.hypot(x,z)
    return None if length<=1e-12 else (x/length,z/length)


def build_guides(data, policy):
    scene=data['scene']; names=[scene['startBlock']]+policy['anchors']+[data['task']['block']]
    points=[None]*len(names);points[-1]=terminal_point(data)
    covers={name:landing_cover(data,name) for name in names}
    for i in range(len(names)-2,0,-1):
        points[i]=list(nearest(covers[names[i]],points[i+1]))
    points[0]=list(scene['initial']['position'])
    footprints={name:[scene['collisionScene']['solids'][b['solidIndex']] for b in scene['blocks'] if b['id']==name]
                for name in names}
    result=dict(version=2,anchors=names,points=points,covers=covers,coverPhase='pre_or_post',physicalFootprints=footprints,turns=[])
    for i in range(1,len(names)-1):
        s,e,n=points[i-1:i+2]
        incoming=unit(e[0]-s[0],e[2]-s[2]); away=unit(e[0]-n[0],e[2]-n[2])
        if incoming is None or away is None:
            continue
        angle=math.degrees(math.acos(max(-1.,min(1.,-sum(a*b for a,b in zip(incoming,away))))))
        if angle<=policy['turnaround']['degrees']:
            continue
        normal=unit(incoming[0]+away[0],incoming[1]+away[1])
        if normal is None:
            continue
        b=next(b for b in scene['blocks'] if b['id']==names[i])
        box=scene['collisionScene']['solids'][b['solidIndex']]
        footprint=[[x,z] for x in (box[0],box[3]) for z in (box[2],box[5])]
        c=min(normal[0]*x+normal[1]*z for x,z in footprint)
        turn=dict(version=1,index=i,anchor=names[i],S=s,E=e,N=n,angleDegrees=angle,
                  normal=list(normal),offset=c,margin=policy['turnaround']['spatialMargin'],
                  units='blocks',footprint=footprint,spans=[i-1,i])
        turn['id']='turn:'+digest(turn)
        result['turns'].append(turn)
    result['id']='guides:'+digest(result)
    return result


def classify(guides, span, point):
    distances=[]
    for turn in guides['turns']:
        if span not in turn['spans']:
            continue
        raw=sum(a*b for a,b in zip(turn['normal'],point))-turn['offset']
        distances.append(dict(boundary=turn['id'],rawDistance=raw,
                              shiftedDistance=raw+turn['margin'],inside=raw+turn['margin']>=0))
    return dict(version=1,guide=guides['id'],span=span,point=list(point),
                isTurnaround=any(d['inside'] for d in distances),distances=distances)


def decision_objective(guides, reached, point, fallback):
    """Progress from `point` (where a decision's program ends) toward the next required guide point.

    `point` is [x, y, z]; `reached` anchors are visited, so the next required one is guide index reached+1
    (index 0 is the start, the last the task). From inside that anchor's turnaround the program aims at the
    one after it.
    """
    i=reached+1
    turn=next((t for t in guides['turns'] if t['index']==i),None)
    inside=classify(guides,reached,[point[0],point[2]])['distances']
    if turn is not None and any(d['boundary']==turn['id'] and d['inside'] for d in inside):
        i+=1
    if i>=len(guides['points']):
        return dict(fallback)
    target=guides['points'][i]
    direction=unit(target[0]-point[0],target[2]-point[2])
    if direction is None:
        return dict(fallback)
    return Checkpoint(1,-1,direction,tuple(point),tuple(target),()).objective


def onward_objective(guides, span, fallback):
    # At E aim onward to N, rather than back toward E's landing point.
    i=span+1
    if i+1>=len(guides['points']):
        return dict(fallback)
    e,n=guides['points'][i:i+2]
    direction=unit(n[0]-e[0],n[2]-e[2])
    if direction is None:
        return dict(fallback)
    return Checkpoint(1,-1,direction,tuple(e),tuple(n),()).objective
