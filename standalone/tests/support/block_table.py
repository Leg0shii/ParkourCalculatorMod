"""Shared builders for the block table soundness checks in movement and constraint tests."""
import json
import os
from pathlib import Path


def random_box_request(rng):
    start = rng.randint(0, 10)
    cells = rng.sample([(x, z) for x in range(-18, 20, 3) for z in range(-18, 20, 3) if (x, z) != (0, 0)], 15)
    blocks = [dict(id='start', boxes=[[0, start-1, 0, 2, start, 2]])]
    for k, (x, z) in enumerate(cells):
        top, size = rng.randint(0, 10), rng.choice([1, 2])
        blocks.append(dict(id='b%d' % k, boxes=[[x, top-1, z, x+size, top, z+size]]))
    blocks[-1]['id'] = 'goal'
    return dict(version=1, sceneModel='collision_boxes', geometryBounds=[-30, -20, -30, 30, 30, 30],
                initial=dict(position=[1, start, 1], velocity=[0, -.0784000015258789, 0], yaw=-90),
                startBlock='start', goalBlock='goal', maxRunTicksPerJump=0, maxTotalRunTicks=0, maxFlightInputs=40,
                maxContactCombinations=0, maxWallHits=0, blocks=blocks)


def compiler():
    from parkour_solver.bridge.compiler import JavaCompiler
    return JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text()))


def generated(scene, program, gate, owner):
    from parkour_solver.movement.generation import generate_choices
    from parkour_solver.movement.program import boundary_state
    result = []
    for kind in ('normal', 'stepping', 'ceiling'):
        context = dict(mode='open', scene=scene, program=program, state=boundary_state(program), maxInputs=40,
                       threshold=0., interactionGate=gate)
        if owner is not None:
            context['launchOwner'] = owner
        result.append(list(generate_choices(kind, context)))
    return result


def nodes(scene, roots, gate):
    from parkour_solver.movement.program import append_choice, truncate_program
    result = []
    for root in roots:
        result.append(root)
        start = root['profile']['airtime']
        for kind in generated(scene, root, gate, None):
            for choice in kind[:3]:
                child = append_choice(root, choice)
                if child is None:
                    continue
                result.append(child)
                ticks = child['profile']['airtime']
                if ticks >= start+4:
                    result.append(truncate_program(child, start+(ticks-start)//2))
    return result


def real11_inputs():
    import gzip
    from tools.paths import STANDALONE_ROOT
    from tests.support.archived_events import current_events
    from tests.support.paths import AUTONOMY_DATA
    from parkour_solver.movement.program import empty_program, truncate_program
    from parkour_solver.scene.normalize import prepare_scene
    scene = prepare_scene(json.loads((STANDALONE_ROOT/'cases/real11/physical-anchors.json').read_text())['request'])
    fixture = json.loads((AUTONOMY_DATA/'experiments/linkcraft11-opening/fixtures/clock64-orchestrator.json').read_text())
    with gzip.open(AUTONOMY_DATA/'experiments/orchestrator-fixes/audit/candidate-ledger.json.gz', 'rt') as stream:
        saved = current_events(json.load(stream))
    original = next(p for p in saved['programs'] if p['label'] == 'P03')['record']['decision_program']
    prefix = truncate_program(original, 21)
    prefix['terminalSupport'] = 'b2'
    return [(scene, [empty_program(scene['initial'], threshold=0.)]), (prepare_scene(fixture['request']), [prefix])]
