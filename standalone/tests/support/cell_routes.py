"""Shared test builders extracted from tests.unit.spatial.test_cell_routes."""
from parkour_solver.scene.normalize import prepare_scene

def side_scene():
    return prepare_scene(dict(version=1, sceneModel='collision_boxes', geometryBounds=[-4, -4, -5, 10, 9, 7],
        initial=dict(position=[.5, 1, .5], velocity=[0, -.0784000015258789, 0], yaw=-90),
        startBlock='start', goalBlock='goal', blocks=[
            dict(id='start', boxes=[[0, 0, 0, 1, 1, 1]]),
            dict(id='wall', boxes=[[2, 0, 0, 3, 5, 1]]),
            dict(id='goal', boxes=[[5, 0, 0, 6, 1, 1]])],
        maxWallHits=0, maxContactCombinations=0,
        movementSearch=dict(types=['normal'], maxInputs=30, maxPrefixes=24, maxChoicesPerType=12)))
