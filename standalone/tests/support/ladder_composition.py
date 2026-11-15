"""Shared test builders extracted from tests.unit.movement.test_ladder_composition."""
from parkour_solver.scene.ladders import ladder_columns, ladder_regions, climb_walls, entry_channels, exit_faces

from parkour_solver.movement.ladder.episodes import compose_ladder_episodes

from parkour_solver.scene.normalize import prepare_scene

from parkour_solver.physics.vertical import REST_CARRY, CLIMB_CARRY, f32

def airborne_case():
    request = {'version': 1, 'sceneModel': 'collision_boxes', 'geometryBounds': [-8,-5,-8,8,12,8],
        'initial': {'position': [.6,1,-.2], 'velocity': [.1,REST_CARRY,.3]}, 'blocks': [
            {'id': 'start', 'boxes': [[-1,0,-1,1,1,0]]}, {'id': 'a', 'boxes': [[1,0,0,2,2,1]]},
            {'id': 'b', 'boxes': [[1,0,2,2,3,3]]}], 'startBlock': 'start', 'goalBlock': 'b',
        'climbableCells': [[0,y,0] for y in range(2)] + [[0,y,2] for y in range(3)],
        'maxJumps': 1, 'maxRunTicksPerJump': 0, 'maxTotalRunTicks': 0, 'maxContactCombinations': 0}
    scene = prepare_scene(request)
    columns = ladder_columns(request['climbableCells'])
    walls = [next(w for w in climb_walls(c, scene['collisionScene']['solids']) if w['axis'] == 'X' and w['direction'] == 1) for c in columns]
    episodes = []
    for col, wall, entry, leave, hits in [(columns[0], walls[0], 1, 6, range(5)),
                                         (columns[1], walls[1], 11, 25, range(10, 24))]:
        face = next(f for f in entry_channels(col, wall) if f['family'] == 'side' and f['direction'] == -1)
        exit_face = next(f for f in exit_faces(col, [.7, 2.5]) if f['axis'] == 'Z' and f['direction'] == 1)
        episodes.append({'column': col, 'wall': wall, 'entryState': entry, 'exitState': leave,
                         'hitInputs': list(hits), 'entryFace': face, 'exitFace': exit_face, 'exitKind': 'top'})
    program = compose_ladder_episodes(1, 3, episodes, max_inputs=60)
    return scene, episodes, program
