"""Shared test builders extracted from tests.unit.spatial.test_semantic_routes."""
from tests.support.cell_routes import side_scene

def support_scene():
    scene=side_scene()
    scene['blocks'][1]['top']=1
    scene['collisionScene']['solids'][1][4]=1
    return scene
