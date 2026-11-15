"""Read-only controls for the hard-jump follow-up plan; no NLP dispatch."""

import argparse
import gzip
import hashlib
import json
from pathlib import Path
import subprocess

from standalone.autonomy.block_profiles import ordinary_jump_profile
from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.cell_routes.anchor_guides import build_guides
from standalone.autonomy.cell_routes.anchor_policy import transfer_structure
from standalone.autonomy.cell_routes.anchor_time_bound import cover_distance
from standalone.autonomy.movement_composition import append_choice, boundary_state, empty_program
from standalone.autonomy.obstacle_geometry import BODY_HEIGHT, expanded_box
from standalone.autonomy.open_movement import normal_choices
from standalone.autonomy.stepping_generation import open_stepping_choices


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--contexts', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise ValueError('Preserve previous evidence; choose a new output path')
    data = json.loads(gzip.decompress(args.contexts.read_bytes()))
    scene = prepare_scene(data['request'])
    guides = build_guides(data['domain'], data['policy'])
    blocks = {b['id']: b for b in scene['blocks']}
    spans = []
    for source, target in zip(guides['anchors'], guides['anchors'][1:]):
        profile = ordinary_jump_profile(blocks[source]['top'], blocks[target]['top'])
        # Geometry-only broad phase: an ascending head cannot clip an underside
        # below its launch head. This is not a general ceiling-free certificate.
        planes = [dict(solid=i, plane=box[1]-BODY_HEIGHT)
                  for i, box in enumerate(scene['collisionScene']['solids'])
                  if blocks[source]['top'] <= box[1]-BODY_HEIGHT <= max(profile['position'])]
        launch_cases = []
        for higher in scene['blocks']:
            rise = higher['top']-blocks[source]['top']
            if not .42 < rise <= .6:
                continue
            box = expanded_box(scene['collisionScene']['solids'][higher['solidIndex']])
            gap = cover_distance(guides['covers'][source], [box])
            launch_cases.append(dict(higher=higher['id'], horizontalGap=gap,
                                     oneInputMovementBound=data['motion']['movementAfterSupportCheck'],
                                     excludedByDistance=gap > data['motion']['movementAfterSupportCheck']+1e-7))
        spans.append(dict(source=source, target=target, ordinaryDuration=profile['airtime'],
                          possibleAscendingUndersides=planes, launchStepOrCancelGeometry=launch_cases))
    controls = []
    fixtures = Path(__file__).resolve().parents[2]/'fixtures'/'stepping'
    for name in ('corner_jump_cancel', 'slab_boost'):
        path = fixtures/(name+'.json')
        fixture = json.loads(path.read_text())
        sample = prepare_scene(fixture['request'])
        initial = empty_program(sample['initial'], threshold=0.)
        initial['terminalSupport'] = sample['startBlock']
        context = dict(mode='open', scene=sample, program=initial, state=boundary_state(initial),
                       maxInputs=40, threshold=0., cases=[name])
        result = None
        for choice in open_stepping_choices(context):
            child = append_choice(initial, choice)
            for landing in normal_choices(dict(context, program=child, state=boundary_state(child), maxInputs=39)):
                if landing.get('terminalSupport') != sample['goalBlock']:
                    continue
                program = append_choice(child, landing)
                structure = transfer_structure(sample, program, 0, sample['startBlock'], sample['goalBlock'], complete=True)
                result = dict(case=name, inputs=program['profile']['airtime'], transfer=structure,
                              fixture=str(path.relative_to(Path.cwd())),
                              fixtureSHA256=hashlib.sha256(path.read_bytes()).hexdigest(),
                              ordinaryDuration=ordinary_jump_profile(sample['initial']['position'][1],
                                  next(b['top'] for b in sample['blocks'] if b['id']==sample['goalBlock']))['airtime'])
                if not structure['valid']:
                    break
            if result and not result['transfer']['valid']:
                break
        assert result and not result['transfer']['valid'] and result['inputs']==2, name
        assert result['transfer']['reason']=='stepping_during_hard_transfer', name
        controls.append(result)
    output = dict(sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'], text=True).strip(),
                  scope='Existing scalar/generator/structure helpers only; no new NLP solve, scene replay or automatic duration certificate.',
                  contextSHA256=hashlib.sha256(args.contexts.read_bytes()).hexdigest(),
                  real11Spans=spans, genericSteppingControlsOutsideHardDomain=controls,
                  localArrivalTradeoff=dict(initial8B2=17, initial0B2=21, initial8Finish=67,
                      initial0Finish=64, evidence='../anchor-transfer-preparation/README.md'))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, indent=2, allow_nan=False)+'\n')
    print(json.dumps(output, indent=2, allow_nan=False))


if __name__ == '__main__':
    main()
