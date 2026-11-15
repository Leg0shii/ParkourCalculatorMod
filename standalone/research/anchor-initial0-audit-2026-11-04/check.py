"""Explain the cold initial-0 miss and replay an archived yaw on physical B7.

This is a seeded diagnostic, never cold-discovery evidence. It runs no IPOPT.
"""

import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import subprocess

from standalone.autonomy.block_contacts import validate_vertical_schedule
from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.movement_composition import append_choice, boundary_state, compile_program, truncate_program
from standalone.autonomy.open_movement import normal_choices
from standalone.autonomy.route import prepare_route, schedule
from standalone.autonomy.cell_routes.anchor_policy import HARD_JUMP_POLICY_VERSION, single_jump_structure, visits
from standalone.autonomy.cell_routes.automatic_experiment import source_hashes
from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
from standalone.autonomy.cell_routes.window_experiment import save
from standalone.ipopt import prepare, validate


def jumps(p):
    return [t for t in range(p['profile']['airtime']) if p['profile']['grounded'][t] and p['profile']['jump'][t]]


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--output',required=True,type=Path)
    args=parser.parse_args()
    if args.output.exists():parser.error('Preserve existing audit output')
    here=Path(__file__).resolve().parent;experiments=here.parent;root=here.parents[3]
    fixture=experiments/'anchor-feasibility/real11-anchors.json'
    scene=prepare_scene(json.loads(fixture.read_text())['request'])
    archive=experiments/'virtual-finish/evidence/initial0-cold.json.gz'
    old=json.loads(gzip.decompress(archive.read_bytes()))['result']['best']
    prefix=truncate_program(old['movementProgram'],55);prefix['terminalSupport']='b5'
    choice=next(c for c in normal_choices(dict(scene=scene,state=boundary_state(prefix),maxInputs=20,threshold=0.))
                if c['profile']['jump'][0] and c['terminalSupport']=='b7')
    program=append_choice(prefix,choice,max_inputs=200)
    compiled=compile_program(scene,program);route=prepare_route(compiled['route']);plan=schedule(route,[0])
    vertical=validate_vertical_schedule(route,compiled['profiles'],plan)
    assert vertical['feasible'];plan['vertical']=vertical['vertical']
    with JavaCompiler(json.loads(Path(os.environ['PKC_RUN_TICKS_COMPILER']).read_text())) as compiler:
        coefficients=compiler.compile(route,plan,5)
        model=prepare(dict(coefficients,version=1,ticks=plan['ticks'],position=scene['initial']['position'][::2],
            velocity=scene['initial']['velocity'][::2],yaw=old['candidate']['yaw'],threshold=0.,
            walls=plan['walls'],contacts=plan['contacts'],objective=dict(route['objective'],tick=plan['ticks'])))
        candidate=validate(model,model['yaw']);candidate.update({k:vertical[k] for k in ('y','vy','onGround')})
        best=dict(route=route,profiles=compiled['profiles'],plan=plan,candidate=candidate,movementProgram=program)
        checked=replay_witness(scene,best,compiler)
    assert checked['passed']
    physical=dict(T=plan['ticks'],jumps=jumps(program),visits=visits(scene,program),
        finalPosition=candidate['positions'][-1],Y=candidate['y'][-1],onGround=candidate['onGround'][-1],
        sameHorizontalTrajectory=candidate['positions']==old['candidate']['positions'],nativeReplay=checked,
        b1b2SingleJumpEligible=single_jump_structure(scene,truncate_program(program,21),0,'b1','b2',complete=True))
    cold_path=experiments/'anchor-feasibility/matched-2026-11-04/guided_early_stop.json.gz'
    cold=json.loads(gzip.decompress(cold_path.read_bytes()))['result']['searchState']['dfs']
    frames={f['id']:f for group in ('frames','stack','suspended','refinements','goalQueue') for f in cold.get(group,[])}
    roots=[f for f in frames.values() if f['index']==0]
    root_records=[dict(frame=f['id'],T=r['arrival'],jumps=jumps(r['program']),status=r['status'])
                  for f in roots for r in f['records']]
    pending=[]
    for frame in roots:
        source=frame['generation'].get('continuation',{})
        for p in source.get('pending',[]):
            pending.append(dict(T=p['profile']['airtime'],jumps=jumps(p),support=p['terminalSupport'],
                eligibleAfterLatch=single_jump_structure(scene,p,0,'b1','b2')))
    report=dict(sourceRevision=subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
        sourceHashes=source_hashes(),kind='archived_yaw_diagnostic_not_cold_search',numericalSolves=0,
        provenance=dict(archive=str(archive.relative_to(root)),archiveSha256=hashlib.sha256(archive.read_bytes()).hexdigest(),
                        fixture=str(fixture.relative_to(root)),fixtureSha256=hashlib.sha256(fixture.read_bytes()).hexdigest()),
        physical64=physical,currentEligibilityPolicyVersion=HARD_JUMP_POLICY_VERSION,
        coldRootRecords=root_records,coldRootPending=pending,
        coldInitial0Evaluations=sum(bool(jumps(r['program'])) and jumps(r['program'])[0]==0
            for f in frames.values() for r in f['records']),
        latch=cold['hardLatches']['b1→b2'],
        conclusion='The recorded 0d340c03 cold run left initial-0 unvisited and its policy1 latch excluded setup. Current-policy eligibility is reported separately; policy2 preserves source setup on every anchor.',
        validationScope='Archived facings with physical B7 contact substituted at T64; independent scalar/support/native-scene replay; no cold search or optimality proof')
    save(args.output,report)
    print(json.dumps(dict(physical64=physical,currentEligibilityPolicyVersion=HARD_JUMP_POLICY_VERSION,
        coldRootRecords=root_records,coldRootPending=pending,
                          coldInitial0Evaluations=report['coldInitial0Evaluations'])),flush=True)


if __name__=='__main__':main()
