"""Shared test builders extracted from tests.unit.spatial.test_cell_geometry."""
def scene_for(solids, bounds=(-5, -5, -5, 6, 6, 6)):
    return dict(collisionScene=dict(bounds=list(bounds), solids=solids),
                blocks=[dict(id=str(i), sourceBlock=str(i), solidIndex=i) for i in range(len(solids))])
