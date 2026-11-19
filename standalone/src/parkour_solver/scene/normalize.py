"""Normalize the supplied physical scene without choosing a search strategy."""
import copy
from parkour_solver.schema import integer, number
from parkour_solver.scene.geometry import normalize_geometry
from parkour_solver.scene.route import prepare_route
from parkour_solver.scene.objectives import objective_spec


def prepare_scene(request):
    scene = copy.deepcopy(request)
    if not isinstance(scene, dict):
        raise ValueError('Expected a block scene')
    allowed = {'version', 'sceneModel', 'initial', 'blocks', 'startBlock', 'goalBlock', 'inputs',
               'maxJumps', 'maxPrefixes', 'maxQueuedPrefixes', 'maxRunTicksPerJump', 'maxTotalRunTicks',
               'maxFlightInputs', 'maxContactBranches', 'stairBoost', 'supportClearance', 'objective',
               'geometryBounds', 'collisionScene', 'maxBlockagesPerObstacle', 'maxPassageCandidates',
               'maxHorizontalBlockagesPerObstacle', 'maxVerticalBlockagesPerObstacle',
               'maxWallHits', 'maxWallHitCandidates', 'ceilingHits', 'climbableCells', 'ladderSearch',
               'maxContactCombinations', 'maxCombinedEvents', 'maxSteppingProfiles', 'movementSearch'}
    extra = set(scene) - allowed
    if extra:
        raise ValueError('Unsupported block-scene fields: ' + ', '.join(sorted(extra)))
    if scene.get('sceneModel') not in ('coplanar_support_planes', 'support_planes', 'collision_boxes'):
        raise ValueError('Declare a support_planes, coplanar_support_planes or collision_boxes scene')
    scene['objective'] = objective_spec(scene.get('objective'))
    blocks = scene.get('blocks')
    if not isinstance(blocks, list) or not 2 <= len(blocks) <= 64:
        raise ValueError('Supply 2 through 64 candidate supports')
    solid_scene = scene['sceneModel'] == 'collision_boxes'
    if solid_scene:
        integer(scene.setdefault('maxWallHits', 2), 'maxWallHits', 0, 8)
        integer(scene.setdefault('maxWallHitCandidates', 24), 'maxWallHitCandidates', 1, 512)
        integer(scene.setdefault('maxContactCombinations', 64), 'maxContactCombinations', 0, 100000)
        integer(scene.setdefault('maxSteppingProfiles', 128), 'maxSteppingProfiles', 1, 100000)
        integer(scene.setdefault('maxCombinedEvents', 3), 'maxCombinedEvents', 1, 32)
        if type(scene.setdefault('ceilingHits', True)) is not bool:
            raise ValueError('ceilingHits must be boolean')
        scene = normalize_geometry(scene)
        blocks = scene['blocks']
        if len(blocks) > 64:
            raise ValueError('Supply at most 64 collision sub-boxes')
    elif 'collisionScene' in scene or 'geometryBounds' in scene:
        raise ValueError('Full geometry requires sceneModel=collision_boxes')
    ids = []
    for block in blocks:
        fields = {'id', 'bounds', 'top', 'sourceBlock', 'solidIndex'} if solid_scene else {'id', 'bounds', 'top'}
        if not isinstance(block, dict) or set(block) - fields:
            raise ValueError('Each candidate is one rectangular support with id, bounds and top')
        if not isinstance(block.get('id'), str) or not block['id']:
            raise ValueError('Each candidate support needs a nonempty ID')
        ids.append(block['id'])
    if len(set(ids)) != len(ids):
        raise ValueError('Candidate support IDs must be unique')
    if scene.get('startBlock') not in ids or scene.get('goalBlock') not in ids:
        raise ValueError('Start and goal must name candidate support IDs')
    if scene['startBlock'] == scene['goalBlock']:
        raise ValueError('This route search requires different start and goal supports')
    if type(scene.setdefault('stairBoost', True)) is not bool:
        raise ValueError('stairBoost must be boolean')
    if not isinstance(scene.get('inputs', {}), dict):
        raise ValueError('Input policy must be an object')
    scene['inputs'] = dict(scene.get('inputs', {}))
    if set(scene['inputs']) - {'forward', 'strafe', 'sprint', 'incomingSprint', 'force45'}:
        raise ValueError('Unsupported input-policy fields')
    scene['inputs'].setdefault('sprint', True)
    scene['inputs'].setdefault('force45', True)
    if scene['inputs']['sprint'] is not True or scene['inputs']['force45'] is not True:
        raise ValueError('Block search uses sprint jump and sprint 45')
    for key, default, lo, hi in [('maxJumps', 4, 1, 12), ('maxPrefixes', 200, 1, 100000),
                                 ('maxQueuedPrefixes', 4000, 1, 100000),
                                 ('maxRunTicksPerJump', 6, 0, 1000), ('maxTotalRunTicks', 12, 0, 1000),
                                 ('maxFlightInputs', 200, 2, 200)]:
        integer(scene.setdefault(key, default), key, lo, hi)
    # Accepted only to read source-era scenes; these removed local caps have
    # no normalized/runtime policy. New cases should omit them.
    for key in ('maxContactBranches', 'maxBlockagesPerObstacle', 'maxPassageCandidates',
                'maxHorizontalBlockagesPerObstacle', 'maxVerticalBlockagesPerObstacle'):
        scene.pop(key, None)
    ordered = [blocks[ids.index(scene['startBlock'])]] + [b for b in blocks if b['id'] != scene['startBlock']]
    checked = prepare_route({'version': scene.get('version'), 'initial': scene.get('initial'),
                             'blocks': ordered, 'airtimes': [12] * (len(blocks) - 1),
                             'inputs': scene['inputs'], 'maxTotalRunTicks': scene['maxTotalRunTicks'],
                             'supportClearance': scene.get('supportClearance', 1e-6)})
    if scene['sceneModel'] == 'coplanar_support_planes' and any(b['top'] != ordered[0]['top'] for b in blocks):
        raise ValueError('Use sceneModel=support_planes for different support heights')
    scene['inputs'] = checked['inputs']
    scene['initial'] = checked['initial']
    if solid_scene and set(scene['initial']) - {'position', 'velocity', 'yaw'}:
        raise ValueError('Collision-box avoidance requires a clear initial input state; additional resume fields need shared movement compilation')
    scene['supportClearance'] = checked['supportClearance']
    if scene.get('climbableCells'):
        if not solid_scene:
            raise ValueError('Climbable cells require collision_boxes')
        from parkour_solver.movement.ladder.settings import ladder_settings
        scene['ladderSearch'] = ladder_settings(scene)
    elif 'ladderSearch' in scene:
        raise ValueError('Ladder search needs supplied climbable cells')
    return scene
