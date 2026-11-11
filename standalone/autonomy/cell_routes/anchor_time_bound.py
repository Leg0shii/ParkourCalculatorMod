"""Optimistic horizontal travel between mandatory anchor landing regions.

Guide points are estimates only. Pruning uses their full covers and the shared
movement-speed upper bound; no witness endpoint or velocity is frozen.
"""
import math


def cover_distance(left, right):
    return min(math.hypot(*(max(0.,b[a]-c[a+3],c[a]-b[a+3]) for a in (0,2)))
               for b in left for c in right)


def _vertical_remaining(guides, span, program, transfers, hard_latches):
    from standalone.ipopt.ladder import REST_CARRY
    names=guides['anchors'];p=program['profile'];n=p['airtime']
    reached=program.get('terminalSupport')==names[span+1] and p['grounded'][-1]
    terms=[]
    for i in range(span+int(reached),len(names)-1):
        key=names[i]+'→'+names[i+1];r=transfers.get(key,{})
        if key not in hard_latches or not r.get('ticks'):continue
        ticks=r['ticks'];reason='future_transfer'
        if i==span and not reached:
            positions,velocities=p.get('position',[]),p.get('velocity',[])
            if not positions or not velocities:continue
            if p['grounded'][-1] and program.get('terminalSupport')==names[i]:
                if abs(positions[-1]-r['sourceTop'])>1e-7 or abs(velocities[-1]-REST_CARRY)>1e-7:continue
                reason='grounded_source'
            elif not p['grounded'][-1]:
                source_contacts=[e['offset']+1 for e in p.get('contactEvents',[]) if
                    e.get('axis')=='Y' and e.get('direction')==-1 and e.get('box')==r.get('sourceBox')]
                boundary=max(source_contacts,default=0)
                launches=[t for t in range(boundary,n) if p['grounded'][t] and p['jump'][t]]
                if len(launches)!=1:continue
                launch=launches[0]
                if abs(positions[launch]-r['sourceTop'])>1e-7:continue
                if any(e['input']>=launch for e in p.get('caseEvents',[])):continue
                if any(e['offset']>=launch and e.get('axis')=='Y' and e.get('direction')==1
                       for e in p.get('contactEvents',[])):continue
                ticks=max(0,ticks-(n-launch));reason='paid_transfer_inputs'
            else:continue
        terms.append(dict(source=names[i],target=names[i+1],ticks=ticks,reason=reason,proof=r.get('id')))
    return sum(t['ticks'] for t in terms),terms


def remaining_bound(guides, span, program, motion, *, transfers=None, hard_latches=None):
    from .transfer_time_bounds import state_covers
    names=guides['anchors'];p=program['profile']
    target=span+1
    reached=program.get('terminalSupport')==names[target] and p['grounded'][-1]
    first=target if reached else target-1
    if not reached and (not p['grounded'][-1] or program.get('terminalSupport')!=names[first]):
        first=target
    segments=[];unknown=False
    for i in range(first,len(names)-1):
        a,b=names[i:i+2]
        left,right=state_covers(guides,a,motion),state_covers(guides,b,motion)
        if not left or not right:
            unknown=True;continue
        segments.append(dict(source=a,target=b,distance=cover_distance(left,right)))
    gap=sum(s['distance'] for s in segments)
    speed=motion.get('movementAfterSupportCheck',0.)
    horizontal=max(0,math.ceil((gap-1e-7)/speed)) if math.isfinite(speed) and speed>0 else 0
    vertical,terms=_vertical_remaining(guides,span,program,transfers or {},hard_latches or {})
    return dict(remainingTicks=max(horizontal,vertical),horizontalTicks=horizontal,verticalTicks=vertical,
                transferTerms=terms,proofIds=[t['proof'] for t in terms if t.get('proof')],
                reason='partial_unknown_landing_enclosure' if unknown else 'relaxed_remaining_time',
                distanceLowerBound=gap,movementUpperBound=speed,segments=segments,
                guideDistanceEstimate=sum(math.hypot(guides['points'][i+1][0]-guides['points'][i][0],
                    guides['points'][i+1][2]-guides['points'][i][2]) for i in range(first,len(names)-1)))
