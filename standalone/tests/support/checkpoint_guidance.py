"""Shared test builders extracted from tests.unit.spatial.test_checkpoint_guidance."""
import itertools

import time

from parkour_solver.scene.normalize import prepare_scene

from parkour_solver.search.movement import iter_movement_programs

def flat_scene():
    return prepare_scene(dict(version=1, sceneModel='collision_boxes', geometryBounds=[-10,-5,-10,20,12,10],
        initial=dict(position=[.5,1,.5], velocity=[.25,-.0784000015258789,0], yaw=-90),
        startBlock='start', goalBlock='goal', maxWallHits=0, maxContactCombinations=0,
        blocks=[dict(id='start', boxes=[[-2,0,-2,3,1,2]]), dict(id='goal', boxes=[[5,0,-1,6,1,1]])],
        movementSearch=dict(types=['normal'], maxInputs=30, maxPrefixes=96, maxQueuedPrefixes=128, maxChoicesPerType=16)))

def programs(scene, tick):
    return list(itertools.islice(iter_movement_programs(scene, checkpoint_tick=tick, threshold=0.,
                    deadline=time.monotonic()+3), 64))
