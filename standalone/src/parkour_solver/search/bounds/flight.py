"""Conditional flight minima: hard transfers exclude stepping by declaration.

Ordinary vertical clocks are useful only when shortening ceilings are absent.
Unknown ceilings return zero; no extra numerical solve or witness seam is used.
"""
import math

from parkour_solver.physics.vertical import REST_CARRY
from parkour_solver.movement.profiles import ordinary_jump_profile
from parkour_solver.reachability.service import query_interaction
from parkour_solver.movement.program import empty_program, append_choice
from parkour_solver.movement.normal import event_choice
from parkour_solver.scene.geometry import BODY_HEIGHT
from parkour_solver.search.identity import digest


POLICY_VERSION = 1
ASSUMPTION = 'hard_transfer_without_stepping'


def state_covers(guides, name, motion):
    """Enclose post-landing position, including a pre-overlap input's motion."""
    covers=guides['covers'].get(name,[])
    if not covers:return []
    pad=0.
    if guides.get('coverPhase','post')!='post':
        pad=motion.get('movementAfterSupportCheck',0.)
        if not math.isfinite(pad) or pad<=0:return []
    return [[b[0]-pad,b[1],b[2]-pad,b[3]+pad,b[4],b[5]+pad] for b in covers]


def transfer_bound(scene, guides, source, target, motion, service, *, threshold=0., check=None):
    by_id={b['id']:b for b in scene['blocks']}
    a,b=by_id[source],by_id[target]
    record=dict(version=POLICY_VERSION,source=source,target=target,sourceTop=a['top'],
                sourceBox=scene['collisionScene']['solids'][a['solidIndex']],threshold=threshold,
                assumption=ASSUMPTION,ticks=0,status='unknown',proofIds=[],ceilings=[])
    profile=ordinary_jump_profile(a['top'],b['top'],threshold=threshold)
    if profile is None:return dict(record,reason='no_ordinary_profile')
    duration=profile['airtime'];record['ordinaryTicks']=duration
    # All relevant first ceiling changes must happen before the ordinary arrival.
    possible=[]
    for i,box in enumerate(scene['collisionScene']['solids']):
        if check:check()
        plane=box[1]-BODY_HEIGHT
        for t in range(duration-1):
            if profile['position'][t]<=plane<profile['position'][t+1]:
                possible.append((i,box,t));break
    types=scene.get('movementSearch',{}).get('types',('normal','ceiling','stepping'))
    if 'ceiling' not in types or not scene.get('ceilingHits',True):
        possible=[];record['ceilingDomain']='disabled'
    if not possible:
        return dict(record,ticks=duration,status='bounded',reason='no_shortening_ceiling_height_window')
    covers=state_covers(guides,source,motion)
    speed=motion.get('storedSpeedNorm')
    if not covers or speed is None or not math.isfinite(speed):
        return dict(record,reason='unknown_launch_enclosure')
    if not callable(getattr(service.compiler,'compile',None)):
        return dict(record,reason='unknown_compiler')
    for cover in covers:
        if check:check()
        initial=dict(scene['initial'],position=[(a['bounds'][0]+a['bounds'][1])/2,a['top'],
                                               (a['bounds'][2]+a['bounds'][3])/2],
                     velocity=[0.,REST_CARRY,0.])
        family_scene=dict(scene,initial=initial,startBlock=source,goalBlock=target)
        prefix=empty_program(initial,threshold=threshold);prefix['terminalSupport']=source
        context=dict(scene=family_scene,program=prefix,maxInputs=duration,threshold=threshold)
        choice=event_choice(context,'normal',initial,duration,jumping=True)
        if choice is None:return dict(record,reason='unknown_scalar_approach')
        program=append_choice(prefix,choice)
        prepared=service.prepare(program,scene=family_scene)
        if prepared is None:return dict(record,reason='unknown_scalar_approach')
        intervals=[[cover[0],cover[2]],[cover[3],cover[5]],[-speed,-speed],[speed,speed]]
        analysis=service.analyze_model(prepared['route'],prepared['plan'],prepared['vertical'],
                                      open_terminal=True,initial_intervals=intervals)
        for solid,box,t in possible:
            if check:check()
            proof=query_interaction(dict(box=box,role='approach_prerequisite',interior=False,
                owner=solid,windows=[dict(tick=t,phase='Y')]),dict(envelopes=analysis['envelopes']))
            record['ceilings'].append(dict(solid=solid,input=t,status=proof['status'],proof=proof['id']))
            record['proofIds'].append(proof['id'])
            if proof['status']!='absent':return dict(record,reason='shortening_ceiling_not_excluded')
    record.update(ticks=duration,status='bounded',reason='shortening_ceilings_unreachable')
    return record


def transfer_bounds(scene, guides, motion, service, *, saved=None, threshold=0., check=None):
    """Commit each completed pair separately, so a timeout retains earlier work."""
    saved={} if saved is None else saved
    for source,target in zip(guides['anchors'],guides['anchors'][1:]):
        key=source+'→'+target
        if key not in saved:
            if check:check()
            result=transfer_bound(scene,guides,source,target,motion,service,threshold=threshold,check=check)
            result['id']='transfer-time:'+digest(result)
            saved[key]=result
    return saved
