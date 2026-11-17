import argparse
import copy
import gzip
import json
import sys
import time
from pathlib import Path

from standalone.autonomy.block_search import prepare_scene
from standalone.autonomy.compiler import JavaCompiler
from standalone.autonomy.prefix_viability import prefix_viability
from standalone.autonomy.cell_routes.checkpoints import Checkpoint
from standalone.autonomy.cell_routes.checkpoint_search import evaluate_program, _motion_bound
from standalone.autonomy.cell_routes.checkpoint_experiment import replay_witness
from standalone.autonomy.cell_routes.geometry import decompose
from standalone.autonomy.cell_routes.local import CorridorAdapter, SearchWork
from standalone.autonomy.cell_routes.routes import corridor_for_points, landmarks
from standalone.autonomy.cell_routes.support_order import SupportOrder
from standalone.autonomy.cell_routes.window_experiment import save


ROOT=Path(__file__).resolve().parent
REFERENCE=ROOT.parent/'linkcraft11-opening'


def read(path):
    with gzip.open(path,'rt') as handle:
        return json.load(handle)


def fallen_prefixes():
    found={}
    for filename in ('real11-clock64-first','real11-clock64-near-goals'):
        source=read(REFERENCE/'evidence'/(filename+'.json.gz'))
        scene=prepare_scene(source['fixture']['request'])
        policy=SupportOrder(scene,source['fixture']['requiredSupports'],64)
        def visit(value):
            if isinstance(value,list):
                for item in value:
                    visit(item)
            elif isinstance(value,dict):
                p=value.get('profile',{})
                if not isinstance(p,dict):
                    p={}
                if p.get('airtime') in (28,44) and p['position'][-1]<5 and p['velocity'][-1]<0:
                    key=(p['airtime'],round(p['position'][-1],6))
                    if key not in found:
                        proof=prefix_viability(scene,value,required_contacts=policy.remaining(value),terminal_tick=64,
                                               enabled_types=['normal','stepping'])
                        found[key]=dict(source=filename,program=value,proof=proof)
                else:
                    for child in value.values():
                        visit(child)
        visit(source)
    return list(found.values())


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,default=ROOT/'evidence/fixed-reference-resume.json.gz')
    parser.add_argument('--compiler',type=Path,default=Path('standalone/autonomy/build/compiler.json'))
    args=parser.parse_args()
    saved=read(REFERENCE/'solution.json.gz')
    fixture=saved['fixture'];scene=prepare_scene(fixture['request'])
    gates=landmarks(scene);decomposition=decompose(scene)
    corridor=corridor_for_points(decomposition,fixture['spatialPoints'],gates)
    adapter=CorridorAdapter(scene,corridor,gates,decomposition=decomposition)
    program=copy.deepcopy(saved['movementProgram']);program.pop('repairState',None)
    program['guideSeedYaw']=saved['best']['candidate']['yaw']
    target=Checkpoint(64,-1,(0.,1.),tuple(scene['initial']['position']),tuple(corridor.points[-1]),corridor.signature)
    report=dict(kind='fixed_reference_control_with_recorded_schedule_and_yaw_not_cold_discovery',
                fixture=fixture,stages=[],fallenPrefixes=fallen_prefixes())
    with JavaCompiler(json.loads(args.compiler.read_text())) as compiler:
        motion=_motion_bound(scene,compiler,3)
        for calls in (2,8):
            work=SearchWork(calls,deadline=time.monotonic()+25)
            result=evaluate_program(scene,program,compiler,adapter,work,motion,checkpoint=target,
                seconds=25,candidate_seconds=2,solver_calls=calls,python_executable=sys.executable,
                retry_unresolved=False)
            replay=replay_witness(scene,result['best'],compiler) if result['best'] else None
            if replay is not None and not replay['passed']:
                raise RuntimeError('Reference failed independent replay')
            report['stages'].append(dict(result=result,replay=replay))
            program['repairState']=json.loads(json.dumps(result['repairState'],allow_nan=False))
            save(args.output,report)
    first,second=[stage['result'] for stage in report['stages']]
    before=first['repairState']['completed'];after=second['repairState']['completed']
    old={item['branch'] for item in before}
    report['summary']=dict(exactResume=second['repairResume']['exact'],
        firstCalls=first['work'].get('solverCalls',0),resumedCalls=second['work'].get('solverCalls',0),
        repeatedCompletedBranches=sum(item['branch'] in old for item in after[len(before):]),
        b2AvoidExcluded=any(item['solidIndex']==1 for item in second['reachability']['excludedSolids']),
        objective=second['best']['candidate']['objective'] if second['best'] else None,
        referenceObjective=saved['best']['candidate']['objective'],
        deadFallExclusions=sum(item['proof']['dead'] for item in report['fallenPrefixes']),
        hasPending=bool(second['repairState']['pending']) or not second['repairState']['constraintGeneration']['exhausted'])
    if report['summary']['repeatedCompletedBranches'] or not report['summary']['exactResume']:
        raise RuntimeError('Completed work was restarted')
    save(args.output,report)
    print(json.dumps(report['summary']),flush=True)


if __name__=='__main__':
    main()
