"""Shared test builders extracted from tests.unit.search.test_anchor_policy."""
from parkour_solver.scene.normalize import prepare_scene

from parkour_solver.movement.program import empty_program

def scene():
    return prepare_scene(dict(version=1,sceneModel='collision_boxes',maxWallHits=0,
        initial=dict(position=[.5,1,.5],velocity=[0,-.0784000015258789,0],yaw=0),
        geometryBounds=[-5,-5,-5,12,10,10],startBlock='start',goalBlock='goal',blocks=[
            dict(id='start',boxes=[[0,0,0,1,1,1]]),dict(id='mid',boxes=[[4,0,0,5,2,1]]),
            dict(id='optional',boxes=[[6,0,0,7,2,1]]),dict(id='goal',boxes=[[0,0,2,1,3,3]])],
        objective=dict(axis='Z',sense='MAX'),movementSearch=dict(types=['normal'],maxInputs=60)))

def history(s,names):
    p=empty_program(s['initial'],threshold=0.)
    n=len(names)
    p['profile'].update(airtime=n,position=[1.]*(n+1),velocity=[0.]*(n+1),
        grounded=[True]*(n+1),jump=[True]*n,sneak=[False]*n,ladderTicks=[False]*(n+1))
    p['profile']['contactEvents']=[dict(offset=i,axis='Y',direction=-1,
        boxes=[s['collisionScene']['solids'][next(b['solidIndex'] for b in s['blocks'] if b['id']==name)]])
        for i,name in enumerate(names)]
    p['terminalSupport']=names[-1] if names else s['startBlock']
    return p
