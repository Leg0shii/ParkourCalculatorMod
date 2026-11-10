"""Optimistic horizontal travel between mandatory anchor landing regions.

Guide points are estimates only. Pruning uses their full covers and the shared
movement-speed upper bound; no witness endpoint or velocity is frozen.
"""
import math


def cover_distance(left, right):
    return min(math.hypot(*(max(0.,b[a]-c[a+3],c[a]-b[a+3]) for a in (0,2)))
               for b in left for c in right)


def remaining_bound(guides, span, program, motion):
    names=guides['anchors'];p=program['profile']
    target=span+1
    reached=program.get('terminalSupport')==names[target] and p['grounded'][-1]
    first=target if reached else target-1
    # Airborne boundaries are not at the last support's guide/region. Omit that
    # segment rather than pinning a feasible witness or overestimating travel.
    if not reached and (not p['grounded'][-1] or program.get('terminalSupport')!=names[first]):
        first=target
    segments=[]
    for i in range(first,len(names)-1):
        a,b=names[i:i+2]
        if not guides['covers'].get(a) or not guides['covers'].get(b):
            return dict(remainingTicks=0,reason='unknown_landing_cover')
        segments.append(dict(source=a,target=b,distance=cover_distance(guides['covers'][a],guides['covers'][b])))
    gap=sum(s['distance'] for s in segments)
    speed=motion.get('movementAfterSupportCheck',0.)
    ticks=max(0,math.ceil((gap-1e-7)/speed)) if math.isfinite(speed) and speed>0 else 0
    return dict(remainingTicks=ticks,distanceLowerBound=gap,movementUpperBound=speed,segments=segments,
                guideDistanceEstimate=sum(math.hypot(guides['points'][i+1][0]-guides['points'][i][0],
                    guides['points'][i+1][2]-guides['points'][i][2]) for i in range(first,len(names)-1)))
