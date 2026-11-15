"""Shared test builders extracted from tests.unit.spatial.test_route_regions."""
from tests.support.cell_geometry import scene_for

def task_scene(solids, owners=None, **kwargs):
    scene = scene_for(solids, **kwargs)
    if owners is not None:
        for block, owner in zip(scene['blocks'], owners):
            block['sourceBlock'] = owner
    for block, box in zip(scene['blocks'], solids):
        block.update(top=box[4], bounds=[box[0], box[3], box[2], box[5]])
    scene.update(initial=dict(position=[.5, 1, .5]), startBlock='0', goalBlock=str(len(solids)-1))
    return scene
