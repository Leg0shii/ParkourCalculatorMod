import gzip
import json
import math
from pathlib import Path

from standalone.autonomy.block_contacts import validate_vertical_schedule
from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.movement_composition import compile_program
from standalone.autonomy.route import prepare_route, schedule
from standalone.autonomy.obstacle_geometry import expanded_box


ROOT = Path(__file__).resolve().parent


def read(path):
    with gzip.open(path, 'rt') as handle:
        return json.load(handle)


def main():
    winner = read(ROOT / 'solution.json.gz')
    original = read(ROOT / 'evidence/real11-clock64-event-priority.json.gz')
    scene = prepare_scene(original['fixture']['request'])
    box = scene['collisionScene']['solids'][1]
    expanded = expanded_box(box)
    initial = scene['initial']['position']
    report = dict(model='fixed compiled coefficients, normal momentum, zero thresholds',
        block='b2', box=box, expandedBox=expanded, initial=scene['initial'], runs=[])
    programs = [('immediate0', winner['movementProgram']),
                ('slow5', original['result']['finalAttempts'][1]['decision_program'])]
    with JavaCompiler(json.loads(Path('standalone/autonomy/build/compiler.json').read_text())) as compiler:
        for name, program in programs:
            compiled = compile_program(scene, program, require_complete=False)
            route = prepare_route(compiled['route'])
            plan = schedule(route, [0])
            vertical = validate_vertical_schedule(route, compiled['profiles'], plan)
            plan['vertical'] = dict(vertical['vertical'], referencePosition=vertical['y'],
                                   referenceVelocity=vertical['vy'])
            coefficients = compiler.compile(route, plan, 3)
            speed = math.hypot(scene['initial']['velocity'][0], scene['initial']['velocity'][2])
            radius = 0.
            rows = []
            for tick, (magnitude, friction) in enumerate(zip(coefficients['magnitude'], coefficients['friction'])):
                movement = speed + magnitude
                radius += movement
                speed = friction * movement
                padding = 1e-8 * (1+radius) + 1e-12 * (1+max(abs(v) for v in initial)) * (tick+1)
                lo, hi = min(vertical['y'][tick:tick+2]), max(vertical['y'][tick:tick+2])
                overlap_y = lo < expanded[4] and hi > expanded[1]
                min_x = initial[0] - radius - padding
                max_x = initial[0] + radius + padding
                min_z = initial[2] - radius - padding
                max_z = initial[2] + radius + padding
                disjoint = (min_x > expanded[3] or max_x < expanded[0]
                            or min_z > expanded[5] or max_z < expanded[2])
                rows.append(dict(input=tick, yStart=vertical['y'][tick], yEnd=vertical['y'][tick+1],
                    possibleVerticalOverlap=overlap_y, radiusThroughInput=radius+padding,
                    minX=min_x, minZ=min_z, xClearance=min_x-expanded[3],
                    horizontalEnvelopeDisjoint=disjoint, potentiallyRelevant=overlap_y and not disjoint))
            possible = [r['input'] for r in rows if r['potentiallyRelevant']]
            report['runs'].append(dict(name=name, jumpInputs=[i for i,j in enumerate(plan['jump']) if j],
                magnitude=coefficients['magnitude'], friction=coefficients['friction'], inputs=rows,
                remainingPotentialInputs=possible,
                conclusion='no additional B2 avoidance needed' if not possible else 'retain possible B2 interactions'))
    path = ROOT / 'audit/b2-reachability.json'
    path.write_text(json.dumps(report, indent=2, allow_nan=False)+'\n')
    for run in report['runs']:
        relevant_y = [r for r in run['inputs'] if r['possibleVerticalOverlap']]
        print(json.dumps(dict(name=run['name'], verticallyRelevantInputs=[r['input'] for r in relevant_y],
            remainingPotentialInputs=run['remainingPotentialInputs'],
            worstXClearance=min(r['xClearance'] for r in relevant_y), conclusion=run['conclusion'])))


if __name__ == '__main__':
    main()
